package net.floodlightcontroller.gatewaycontroller;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.gatewaycontroller.dns.DNSResponse;
import net.floodlightcontroller.gatewaycontroller.dns.ResourceRecord;
import net.floodlightcontroller.gatewaycontroller.dns.TCPConnection;
import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.floodlightcontroller.gatewaycontroller.dns.DNSResponse.modifyDNSReponse;
import static net.floodlightcontroller.gatewaycontroller.dns.TCPFlags.*;

/**
 * This class implements a gateway controller as a floodlight module
 *
 * @author Harry Liu
 * @author Can Alper
 * @version April 13, 2017
 */
public class GatewayController implements IFloodlightModule, IOFMessageListener {
    private static Pattern     switchIPPattern       = Pattern.compile("/([0-9.]+):[0-9]+");
    private final  String      DOMAIN_NAME           = "www.team2.4516.cs.wpi.edu.";
    private final  int         DNS_PORT              = 53;
    private final  int         SSH_TTL               = 3600;
    private final  IPv4Address clientOriginalIp      = IPv4Address.of("10.45.2.1");
    private final  String      DNS_SWITCH            = "10.45.2.2";
    private final  String      NAT_SWITCH            = "10.45.2.1";
    private final  String      SERVER_CLUSTER_SWITCH = "10.45.2.3";
    protected IFloodlightProviderService                 floodlightProvider;
    protected IOFSwitchService                           switchService;
    private   List<IPv4Address>                          adminAddresses;
    private   HashSet<IPv4Address>                       acceptedAddresses;
    private   HashMap<String, DatapathId>                switches;
    private   HashMap<String, String>                    switchNames;
    private   List<IPv4Address>                          serverAddresses;
    private   List<IPv4Address>                          clientAddresses;
    private   HashSet<IPv4Address>                       allocatedClientIp;
    private   HashMap<TCPConnection, TCPConnectionState> tcpConnectionStates;
    private   HashMap<TCPConnection, IPv4Address>        tcpConnections;
    
    @Override
    public String getName() {
        return GatewayController.class.getSimpleName();
    }
    
    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                return processPacket(sw, cntx);
            default:
                break;
        }
        return Command.CONTINUE;
    }
    
    private Command processPacket(IOFSwitch sw, FloodlightContext cntx) {
        String switchIp = getSwitchIp(sw);
        Ethernet ethernet = getEthernet(cntx);
        if (!switches.containsKey(switchIp)) {
            DatapathId switchId = sw.getId();
            switches.put(switchIp, switchId);
            enabledSSh(switchId, IPv4Address.of(switchIp));
        }
        
        switch (switchIp) {
            case DNS_SWITCH:
                return processDNSSwitchPacket(sw, ethernet);
            case NAT_SWITCH:
                return processNATSwitchPacket(sw, ethernet);
            case SERVER_CLUSTER_SWITCH:
                return Command.STOP;
        } return Command.STOP;
    }
    
    private Command processNATSwitchPacket(IOFSwitch sw, Ethernet ethernet) {
        IPacket packet = ethernet.getPayload();
        IPv4 iPv4;
        
        if (isNotIPv4Packet(ethernet, packet)) {
            sendOutPacket(sw, ethernet);
            return Command.STOP;
        }
    
        iPv4 = (IPv4) packet;
        if (iPv4.getDestinationAddress().equals(IPv4Address.of(DNS_SWITCH))) sendOutPacket(sw, ethernet);
        else if (iPv4.getSourceAddress().equals(IPv4Address.of(DNS_SWITCH))) sendOutPacket(sw, ethernet);
        if (iPv4.getProtocol() == IpProtocol.TCP) {
            TCP tcp = (TCP) iPv4.getPayload();
        
            short flags = tcp.getFlags();
            TCPConnection connection;
            TCPConnectionState connectionState;
            IPv4Address clientNewIp;
            IPv4Address serverIp;
            switch (flags) {
                case SYN:
                    if (!iPv4.getSourceAddress().equals(clientOriginalIp)) return Command.STOP;
                    clientNewIp = allocateClientIp();
                    connection = TCPConnection.makeTCPConnection(tcp, clientNewIp, iPv4.getDestinationAddress());
                    System.out.println();
                    System.out.println("Client initiates " + connection);
                    if (!tcpConnectionStates.containsKey(connection)) {
                        allocatedClientIp.add(clientNewIp);
                        connectionState = new TCPConnectionState();
                        connectionState.synforward();
                        tcpConnections.put(connection, clientNewIp);
                        tcpConnectionStates.put(connection, connectionState);
                        if (!switches.containsKey(NAT_SWITCH)) return Command.STOP;
                        serverIp = iPv4.getDestinationAddress();
                        DatapathId natSwitchId = switches.get(NAT_SWITCH);
                        System.out.println("Add NAT rules for " + connection);
                        addTunnel(natSwitchId.toString(), EthType.ARP, clientNewIp, 3600);
                        addTCPTunnel(natSwitchId, clientOriginalIp, clientNewIp, serverIp, tcp.getSourcePort(), tcp.getDestinationPort(), U16.ofRaw(SYN));
                        addTCPTunnel(natSwitchId, clientOriginalIp, clientNewIp, serverIp, tcp.getSourcePort(), tcp.getDestinationPort(), U16.ofRaw(SYN_ACK));
                        addTCPTunnel(natSwitchId, clientOriginalIp, clientNewIp, serverIp, tcp.getSourcePort(), tcp.getDestinationPort(), U16.ofRaw(ACK));
                        addTCPTunnel(natSwitchId, clientOriginalIp, clientNewIp, serverIp, tcp.getSourcePort(), tcp.getDestinationPort(), U16.ofRaw(PUSH_ACK));
                        OFFactory factory = sw.getOFFactory();
                        addNATOUTFlowEntry(makeTCPMatchBuilder(factory, OFPort.LOCAL, clientOriginalIp, serverIp, tcp.getSourcePort(), tcp.getDestinationPort(), U16.ofRaw(FIN_ACK)).build(),
                                           sw, factory, OFPort.of(1), clientNewIp);
                        iPv4.setSourceAddress(clientNewIp);
                        iPv4.resetChecksum();
                        tcp.resetChecksum();
                        ethernet.setPayload(iPv4);
                        ethernet.resetChecksum();
                        sendOutPacket(sw, ethernet);
                    }
                    break;
                case FIN_ACK:
                    if (allocatedClientIp.contains(iPv4.getDestinationAddress())) connection = TCPConnection.makeReverseTCPConnection(tcp, iPv4.getSourceAddress(), null);
                    else return Command.STOP;
                    System.out.println("Server terminate TCP connection");
                    clientNewIp = tcpConnections.get(connection);
                    connection.setClientAddr(clientNewIp);
                    connectionState = tcpConnectionStates.get(connection);
                    connectionState.finAckBackward();
                    DatapathId natSwitchId = switches.get(NAT_SWITCH);
                    serverIp = connection.getServerAddr();
                    System.out.println("Remove NAT rule for " + connection);
                    removeTCPTunnel(natSwitchId, clientOriginalIp, clientNewIp, serverIp, tcp.getDestinationPort(), tcp.getSourcePort(), U16.ofRaw(SYN));
                    removeTCPTunnel(natSwitchId, clientOriginalIp, clientNewIp, serverIp, tcp.getDestinationPort(), tcp.getSourcePort(), U16.ofRaw(SYN_ACK));
                    removeTCPTunnel(natSwitchId, clientOriginalIp, clientNewIp, serverIp, tcp.getDestinationPort(), tcp.getSourcePort(), U16.ofRaw(ACK));
                    removeTCPTunnel(natSwitchId, clientOriginalIp, clientNewIp, serverIp, tcp.getDestinationPort(), tcp.getSourcePort(), U16.ofRaw(PUSH_ACK));
                    OFFactory factory = sw.getOFFactory();
                    removeNATOUTFlowEntry(makeTCPMatchBuilder(factory, OFPort.LOCAL, clientOriginalIp, serverIp, tcp.getDestinationPort(), tcp.getSourcePort(), U16.ofRaw(FIN_ACK)).build(),
                                          sw, factory, OFPort.of(1), clientNewIp);
                    System.out.println("Release client IP " + clientNewIp);
                    allocatedClientIp.remove(clientNewIp);
                    clientAddresses.add(clientNewIp);
                    tcpConnections.remove(connection);
                    tcpConnectionStates.remove(connection);
                    System.out.println(connection + " terminated.");
                    iPv4.setDestinationAddress(clientOriginalIp);
                    iPv4.resetChecksum();
                    tcp.resetChecksum();
                    ethernet.setPayload(iPv4);
                    ethernet.resetChecksum();
                    sendOutPacket(sw, ethernet);
                    break;
                default:
                    break;
            }
        }
        return Command.STOP;
    }
    
    private void log(String switchIp, String msg) {
        System.out.printf("GATEWAY CONTROLLER [%s] %s\n", switchNames.getOrDefault(switchIp, String.format("SWITCH %s", switchIp)), msg);
    }
    
    private Command processDNSSwitchPacket(IOFSwitch sw, Ethernet ethernet) {
        IPacket packet = ethernet.getPayload();
        if (isNotIPv4Packet(ethernet, packet)) {
            sendOutPacket(sw, ethernet);
            return Command.CONTINUE;
        }
        
        IPv4 iPv4 = (IPv4) packet;
        if (iPv4.getProtocol().equals(IpProtocol.UDP)) {
            UDP udp = (UDP) iPv4.getPayload();
            if (udp.getSourcePort().getPort() == DNS_PORT) {
                IPv4Address serverAddr = getRandomServerAddr();
                DNSResponse modifiedDNSResponse = modifyDNSReponse(udp, iPv4, ethernet, DOMAIN_NAME, serverAddr);
                Optional<ResourceRecord> record = findRecordFor(DOMAIN_NAME, modifiedDNSResponse);
                if (record.isPresent() && switches.containsKey(SERVER_CLUSTER_SWITCH)) {
                    int ttl = record.get().getTtl();
                    addTunnel(switches.get(SERVER_CLUSTER_SWITCH).toString(), serverAddr, ttl);
                }
            }
        }
        sendOutPacket(sw, ethernet);
        return Command.STOP;
    }
    
    
    private String getSwitchIp(IOFSwitch sw) {
        Matcher switchIPMatcher = switchIPPattern.matcher(sw.getInetAddress().toString());
        switchIPMatcher.find();
        return switchIPMatcher.group(1);
    }
    
    private Optional<ResourceRecord> findRecordFor(String domainName, DNSResponse modifiedDNSReponse) {
        return modifiedDNSReponse.getRecords().stream().filter(resourceRecord -> resourceRecord.getName().equals(domainName)).findFirst();
    }
    
    private IPv4Address allocateClientIp() {
        Random random = new Random();
        int addrIndex = random.nextInt(clientAddresses.size());
        IPv4Address clientIp = clientAddresses.remove(addrIndex);
        return clientIp;
    }
    
    private IPv4Address getRandomServerAddr() {
        Random random = new Random();
        int addrIndex = random.nextInt(serverAddresses.size());
        return serverAddresses.get(addrIndex);
    }
    
    private void enabledSSh(DatapathId switchId, IPv4Address switchIp) {
        for (IPv4Address adminAddress : adminAddresses)
            addTunnel(switchId.toString(), adminAddress, switchIp, SSH_TTL);
        log(switchIp.toString(), String.format("enabled SSH for %s", adminAddresses));
    }
    
    private void addTunnel(String switchId, IPv4Address clientIp, IPv4Address serverIp, int ttl) {
        addTunnel(switchId, EthType.ARP, clientIp, serverIp, ttl);
        addTunnel(switchId, EthType.IPv4, clientIp, serverIp, ttl);
    }
    
    private void addTunnel(String switchId, IPv4Address serverIp, int ttl) {
        addTunnel(switchId, EthType.ARP, serverIp, ttl);
        addTunnel(switchId, EthType.IPv4, serverIp, ttl);
    }
    
    private void addTunnel(String switchId, EthType ethType, IPv4Address serverIp, int ttl) {
        IOFSwitch sw = switchService.getSwitch(DatapathId.of(switchId));
        OFFactory factory = sw.getOFFactory();
        addFlowEntry(makeMatchBuilderWithDst(ethType, factory, serverIp, OFPort.of(1)).build(), sw, factory, OFPort.LOCAL, ttl);
        addFlowEntry(makeMatchBuilderWithSrc(ethType, factory, serverIp, OFPort.LOCAL).build(), sw, factory, OFPort.of(1), ttl);
    }
    
    private void removeTCPTunnel(DatapathId switchId, IPv4Address privateClientIp, IPv4Address publicClientIp, IPv4Address serverIp, TransportPort clientPort, TransportPort serverPort, U16 flags) {
        IOFSwitch sw = switchService.getSwitch(switchId);
        OFFactory factory = sw.getOFFactory();
        removeNATINFlowEntry(makeTCPMatchBuilder(factory, OFPort.of(1), serverIp, publicClientIp, serverPort, clientPort, flags).build(), sw, factory, OFPort.LOCAL, privateClientIp);
        removeNATOUTFlowEntry(makeTCPMatchBuilder(factory, OFPort.LOCAL, privateClientIp, serverIp, clientPort, serverPort, flags).build(), sw, factory, OFPort.of(1), publicClientIp);
    }
    
    private void removeNATINFlowEntry(Match match, IOFSwitch sw, OFFactory factory, OFPort outPort, IPv4Address privateClientIp) {
        OFFlowAdd flowAdd = NATINFlowAdd(match, factory, outPort, privateClientIp);
        OFFlowDelete flowDelete = FlowModUtils.toFlowDelete(flowAdd);
        sw.write(flowDelete);
    }
    
    private void removeNATOUTFlowEntry(Match match, IOFSwitch sw, OFFactory factory, OFPort outPort, IPv4Address publicClientIp) {
        OFFlowAdd flowAdd = NATOUTFlowAdd(match, factory, outPort, publicClientIp);
        OFFlowDelete flowDelete = FlowModUtils.toFlowDelete(flowAdd);
        sw.write(flowDelete);
    }
    
    private void addTCPTunnel(DatapathId switchId, IPv4Address privateClientIp, IPv4Address publicClientIp, IPv4Address serverIp, TransportPort clientPort, TransportPort serverPort, U16 flags) {
        IOFSwitch sw = switchService.getSwitch(switchId);
        OFFactory factory = sw.getOFFactory();
        addNATINFlowEntry(makeTCPMatchBuilder(factory, OFPort.of(1), serverIp, publicClientIp, serverPort, clientPort, flags).build(), sw, factory, OFPort.LOCAL, privateClientIp);
        addNATOUTFlowEntry(makeTCPMatchBuilder(factory, OFPort.LOCAL, privateClientIp, serverIp, clientPort, serverPort, flags).build(), sw, factory, OFPort.of(1), publicClientIp);
    }
    
    private void addTunnel(String switchId, EthType ethType, IPv4Address clientIp, IPv4Address serverIp, int ttl) {
        IOFSwitch sw = switchService.getSwitch(DatapathId.of(switchId));
        addFlowEntry(sw, ethType, clientIp, serverIp, OFPort.of(1), OFPort.LOCAL, ttl);
        addFlowEntry(sw, ethType, serverIp, clientIp, OFPort.LOCAL, OFPort.of(1), ttl);
    }
    
    private void addFlowEntry(IOFSwitch sw, EthType ethType, IPv4Address srcIp, IPv4Address destIp, OFPort inPort, OFPort outPort, int ttl) {
        OFFactory factory = sw.getOFFactory();
        Match match = makeMatchBuilder(ethType, factory, srcIp, destIp, inPort).build();
        addFlowEntry(match, sw, factory, outPort, ttl);
    }
    
    private Match.Builder makeMatchBuilderWithSrc(EthType ethType, OFFactory factory, IPv4Address srcIp, OFPort inPort) {
        if (ethType.equals(EthType.ARP)) return factory.buildMatch().setExact(MatchField.ETH_TYPE, EthType.ARP).setExact(MatchField.ARP_SPA, srcIp).setExact(MatchField.IN_PORT, inPort);
        else if (ethType.equals(EthType.IPv4)) return factory.buildMatch().setExact(MatchField.ETH_TYPE, EthType.IPv4).setExact(MatchField.IPV4_SRC, srcIp).setExact(MatchField.IN_PORT, inPort);
        return factory.buildMatch();
    }
    
    private Match.Builder makeMatchBuilderWithDst(EthType ethType, OFFactory factory, IPv4Address destIp, OFPort inPort) {
        if (ethType.equals(EthType.ARP)) return factory.buildMatch().setExact(MatchField.ETH_TYPE, EthType.ARP).setExact(MatchField.ARP_TPA, destIp).setExact(MatchField.IN_PORT, inPort);
        else if (ethType.equals(EthType.IPv4)) return factory.buildMatch().setExact(MatchField.ETH_TYPE, EthType.IPv4).setExact(MatchField.IPV4_DST, destIp).setExact(MatchField.IN_PORT, inPort);
        return factory.buildMatch();
    }
    
    
    private Match.Builder makeMatchBuilder(EthType ethType, OFFactory factory, IPv4Address srcIp, IPv4Address destIp, OFPort inPort) {
        if (ethType.equals(EthType.ARP))
            return factory.buildMatch().setExact(MatchField.ETH_TYPE, EthType.ARP).setExact(MatchField.ARP_SPA, srcIp).setExact(MatchField.ARP_TPA, destIp).setExact(MatchField.IN_PORT, inPort);
        else if (ethType.equals(EthType.IPv4))
            return factory.buildMatch().setExact(MatchField.ETH_TYPE, EthType.IPv4).setExact(MatchField.IPV4_SRC, srcIp).setExact(MatchField.IPV4_DST, destIp).setExact(MatchField.IN_PORT, inPort);
        return factory.buildMatch();
    }
    
    private Match.Builder makeTCPMatchBuilder(OFFactory factory, OFPort inPort, IPv4Address srcIp, IPv4Address destIp, TransportPort srcPort, TransportPort dstPort, U16 flags) {
        return factory.buildMatch().setExact(MatchField.IN_PORT, inPort).setExact(MatchField.ETH_TYPE, EthType.IPv4).setExact(MatchField.IPV4_SRC, srcIp).setExact(MatchField.IPV4_DST, destIp)
                .setExact(MatchField.IP_PROTO, IpProtocol.TCP).setExact(MatchField.TCP_SRC, srcPort).setExact(MatchField.TCP_DST, dstPort).setExact(MatchField.OVS_TCP_FLAGS, flags);
    }
    
    private OFFlowAdd NATFlowAdd(Match match, OFFactory factory, OFPort outPort, OFActionSetField actionSetField) {
        OFActions actions = factory.actions();
        ArrayList<OFAction> actionList = new ArrayList<>();
        actionList.add(actionSetField);
        OFActionOutput output = actions.buildOutput().setPort(outPort).build();
        actionList.add(output);
        OFInstructions instructions = factory.instructions();
        OFInstructionApplyActions applyActions = instructions.buildApplyActions().setActions(actionList).build();
        List<OFInstruction> instList = new ArrayList<>();
        instList.add(applyActions);
        return factory.buildFlowAdd().setPriority(100).setMatch(match).setInstructions(instList).build();
    }
    
    private OFFlowAdd NATOUTFlowAdd(Match match, OFFactory factory, OFPort outPort, IPv4Address publicClientIp) {
        OFOxms oxms = factory.oxms();
        OFActions actions = factory.actions();
        OFActionSetField setNwSrc = actions.buildSetField().setField(oxms.buildIpv4Src().setValue(publicClientIp).build()).build();
        return NATFlowAdd(match, factory, outPort, setNwSrc);
    }
    
    private OFFlowAdd NATINFlowAdd(Match match, OFFactory factory, OFPort outPort, IPv4Address privateClientIp) {
        OFOxms oxms = factory.oxms();
        OFActions actions = factory.actions();
        OFActionSetField setNwDst = actions.buildSetField().setField(oxms.buildIpv4Dst().setValue(privateClientIp).build()).build();
        return NATFlowAdd(match, factory, outPort, setNwDst);
    }
    
    private void addNATINFlowEntry(Match match, IOFSwitch sw, OFFactory factory, OFPort outPort, IPv4Address privateClientIp) {
        sw.write(NATINFlowAdd(match, factory, outPort, privateClientIp));
    }
    
    private void addNATOUTFlowEntry(Match match, IOFSwitch sw, OFFactory factory, OFPort outPort, IPv4Address publicClientIp) {
        sw.write(NATOUTFlowAdd(match, factory, outPort, publicClientIp));
    }
    
    private void addFlowEntry(Match match, IOFSwitch sw, OFFactory factory, OFPort outPort, int ttl) {
        ArrayList<OFAction> actionList = new ArrayList<>();
        OFActionOutput output = factory.actions().buildOutput().setPort(outPort).build();
        actionList.add(output);
        OFInstructions instructions = factory.instructions();
        OFInstructionApplyActions applyActions = instructions.buildApplyActions().setActions(actionList).build();
        List<OFInstruction> instList = new ArrayList<>();
        instList.add(applyActions);
        OFFlowAdd flowAdd = factory.buildFlowAdd().setHardTimeout(ttl).setPriority(100).setMatch(match).setInstructions(instList).build();
        sw.write(flowAdd);
    }
    
    /**
     * Get the ethernet information with Floodlight context
     *
     * @param cntx The Floodlight context
     *
     * @return the ethernet with Floodlight context
     */
    private Ethernet getEthernet(FloodlightContext cntx) {
        return IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
    }
    
    /**
     * Send out a layer 2 packet from a given switch
     *
     * @param sw       The switch
     * @param ethernet The ethernet
     */
    private void sendOutPacket(IOFSwitch sw, Ethernet ethernet) {
        byte[] serializedData = ethernet.serialize();
        OFPacketOut po = sw.getOFFactory().buildPacketOut().setData(serializedData).setActions(Collections.singletonList(sw.getOFFactory().actions().output(OFPort.NORMAL, 1))).setInPort(OFPort.CONTROLLER).build();
        sw.write(po);
    }
    
    /**
     * Check whether a packet is a IPv4 packet
     *
     * @param ethernet The ethernet
     * @param packet   The packet
     *
     * @return true if the packet is not a IPv4 packet, false otherwise
     */
    private boolean isNotIPv4Packet(Ethernet ethernet, IPacket packet) {
        return ethernet.getEtherType() != EthType.IPv4 || !(packet instanceof IPv4);
    }
    
    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }
    
    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }
    
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return null;
    }
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<>();
        l.add(IFloodlightProviderService.class);
        return l;
    }
    
    private List<IPv4Address> generateServerAddresses() {
        return generateAddresses(129, 255);
    }
    
    private List<IPv4Address> generateClientAddresses() {
        return generateAddresses(48, 63);
    }
    
    private List<IPv4Address> generateAddresses(int from, int to) {
        List<IPv4Address> addresses = new ArrayList<>();
        for (int i = from; i < to; i++)
            addresses.add(IPv4Address.of(String.format("10.45.2.%d", i)));
        return addresses;
    }
    
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        acceptedAddresses = new HashSet<>();
        acceptedAddresses.add(IPv4Address.of("10.45.2.1"));
        acceptedAddresses.add(IPv4Address.of("10.45.2.2"));
        acceptedAddresses.add(IPv4Address.of("10.45.2.3"));
        acceptedAddresses.add(IPv4Address.of("10.45.2.4"));
        serverAddresses = generateServerAddresses();
        clientAddresses = generateClientAddresses();
        adminAddresses = new ArrayList<>();
        adminAddresses.add(IPv4Address.of("10.10.152.59"));
        adminAddresses.add(IPv4Address.of("10.10.152.151"));
        switches = new HashMap<>();
        switchNames = new HashMap<>();
        switchNames.put("10.45.2.1", "NAT SWITCH");
        switchNames.put("10.45.2.2", "DNS SWITCH");
        switchNames.put("10.45.2.3", "SERVER CLUSTER SWITCH");
        tcpConnections = new HashMap<>();
        tcpConnectionStates = new HashMap<>();
        allocatedClientIp = new HashSet<>();
    }
    
    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }
}
