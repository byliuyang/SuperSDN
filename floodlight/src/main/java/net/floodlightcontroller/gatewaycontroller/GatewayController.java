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
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;

import java.util.*;

import static net.floodlightcontroller.gatewaycontroller.dns.DNSResponse.modifyDNSReponse;

/**
 * This class implements a gateway controller as a floodlight module
 *
 * @author Harry Liu
 * @author Can Alper
 * @version April 13, 2017
 */
public class GatewayController implements IFloodlightModule, IOFMessageListener {
    private final String      DOMAIN_NAME = "www.team2.4516.cs.wpi.edu.";
    private final String      switchId3   = "76:c1:48:b9:5a:4d";
    private final String      switchId2   = "ee:c6:65:ac:f6:4a";
    private final IPv4Address switch3Ip   = IPv4Address.of("10.45.2.3");
    private final IPv4Address switch2Ip   = IPv4Address.of("10.45.2.2");
    private final int         DNS_PORT    = 53;
    protected IFloodlightProviderService floodlightProvider;
    protected IOFSwitchService           switchService;
    private   List<IPv4Address>          adminAddresses;
    private   HashSet<IPv4Address>       acceptedAddresses;
    private   List<IPv4Address>          availableAddresses;
    private boolean switch2SSHEnabled = false;
    private boolean switch3SSHEnabled = false;
    
    
    @Override
    public String getName() {
        return GatewayController.class.getSimpleName();
    }
    
    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        Ethernet ethernet = getEthernet(cntx);
        IPacket packet = ethernet.getPayload();
        if (isNotIPv4Packet(ethernet, packet)) return Command.CONTINUE;
        IPv4 iPv4 = (IPv4) packet;
        switch (msg.getType()) {
            case PACKET_OUT:
                
                break;
            case PACKET_IN:
                if (!switch2SSHEnabled) enabledSwitch2SSh();
                if (!switch3SSHEnabled) enabledSwitch3SSh();
                
                if (acceptedAddresses.contains(iPv4.getDestinationAddress()) && acceptedAddresses.contains(iPv4.getSourceAddress())) {
                    if (iPv4.getProtocol().equals(IpProtocol.UDP)) {
                        UDP udp = (UDP) iPv4.getPayload();
                        if (udp.getSourcePort().getPort() == DNS_PORT) {
                            IPv4Address serverAddr = getRandomServerAddr();
                            DNSResponse modifiedDNSReponse = modifyDNSReponse(udp, iPv4, ethernet, DOMAIN_NAME, serverAddr);
                            Optional<ResourceRecord> record = findRecordFor(DOMAIN_NAME, modifiedDNSReponse);
                            if (record.isPresent()) {
                                int ttl = record.get().getTtl();
                                permitTrafficToServer(iPv4, serverAddr, ttl);
                            }
                        }
                    }
                    sendOutPacket(sw, ethernet);
                }
                
                break;
            default:
                break;
        }
        return Command.CONTINUE;
    }
    
    private Optional<ResourceRecord> findRecordFor(String domainName, DNSResponse modifiedDNSReponse) {
        return modifiedDNSReponse.getRecords().stream().filter(resourceRecord -> resourceRecord.getName().equals(domainName)).findFirst();
    }
    
    private void permitTrafficToServer(IPv4 iPv4, IPv4Address serverAddr, int ttl) {
        IOFSwitch sw3 = switchService.getSwitch(DatapathId.of(switchId3));
        if (sw3 == null) return;
        addTunnel(sw3.getId().toString(), iPv4.getDestinationAddress(), serverAddr, ttl);
    }
    
    private IPv4Address getRandomServerAddr() {
        Random random = new Random();
        int addrIndex = random.nextInt(availableAddresses.size());
        return availableAddresses.get(addrIndex);
    }
    
    private boolean enabledSSh(String switchId, IPv4Address switchIp) {
        IOFSwitch sw = switchService.getSwitch(DatapathId.of(switchId));
        if (sw == null) return false;
        for (IPv4Address adminAddress : adminAddresses)
            addTunnel(switchId, adminAddress, switchIp, 3600);
        
        return true;
    }
    
    private void enabledSwitch2SSh() {
        if (enabledSSh(switchId2, switch2Ip)) {
            switch2SSHEnabled = true;
            System.out.println("SSH enabled for switch 2");
        }
    }
    
    private void enabledSwitch3SSh() {
        if (enabledSSh(switchId3, switch3Ip)) {
            switch3SSHEnabled = true;
            System.out.println("SSH enabled for switch 3");
        }
    }
    
    private void addTunnel(String switchId, IPv4Address clientIp, IPv4Address serverIp, int ttl) {
        addTunnel(switchId, EthType.ARP, clientIp, serverIp, ttl);
        addTunnel(switchId, EthType.IPv4, clientIp, serverIp, ttl);
    }
    
    private void addTunnel(String switchId, EthType ethType, IPv4Address clientIp, IPv4Address serverIp, int ttl) {
        IOFSwitch sw = switchService.getSwitch(DatapathId.of(switchId));
        addFlowEntry(sw, ethType, clientIp, serverIp, OFPort.of(1), OFPort.LOCAL, ttl);
        addFlowEntry(sw, ethType, serverIp, clientIp, OFPort.LOCAL, OFPort.of(1), ttl);
    }
    
    private void addFlowEntry(IOFSwitch sw, EthType ethType, IPv4Address srcIp, IPv4Address destIp, OFPort inPort, OFPort outPort, int ttl) {
        OFFactory factory = sw.getOFFactory();
        Match match = makeBuilder(ethType, factory, srcIp, destIp, inPort).build();
        addFlowEntry(match, sw, factory, outPort, ttl);
    }
    
    
    private Match.Builder makeBuilder(EthType ethType, OFFactory factory, IPv4Address srcIp, IPv4Address destIp, OFPort inPort) {
        if (ethType.equals(EthType.ARP))
            return factory.buildMatch().setExact(MatchField.ETH_TYPE, EthType.ARP).setExact(MatchField.ARP_SPA, srcIp).setExact(MatchField.ARP_TPA, destIp).setExact(MatchField.IN_PORT, inPort);
        else if (ethType.equals(EthType.IPv4))
            return factory.buildMatch().setExact(MatchField.ETH_TYPE, EthType.IPv4).setExact(MatchField.IPV4_SRC, srcIp).setExact(MatchField.IPV4_DST, destIp).setExact(MatchField.IN_PORT, inPort);
        return factory.buildMatch();
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
     * Log a given packet to standard output
     *
     * @param message  The additional message
     * @param ethernet The ethernet
     * @param from     The source IP address of the packet
     * @param to       The destination IP address of the packet
     */
    private void logPacket(String message, Ethernet ethernet, IPv4Address from, IPv4Address to) {
        System.out.printf("[%S] %s from %s(%s) to %s(%s)\n", getName(), message, from, ethernet.getSourceMACAddress(), to, ethernet.getDestinationMACAddress());
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
    
    private List<IPv4Address> generateAllAvaAddresses() {
        List<IPv4Address> addresses = new ArrayList<>();
        for (int i = 129; i < 255; i++)
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
        availableAddresses = generateAllAvaAddresses();
        adminAddresses = new ArrayList<>();
        adminAddresses.add(IPv4Address.of("10.10.152.59"));
        adminAddresses.add(IPv4Address.of("10.10.152.151"));
    }
    
    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }
}
