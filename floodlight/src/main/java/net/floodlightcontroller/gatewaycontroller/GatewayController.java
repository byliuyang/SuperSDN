package net.floodlightcontroller.gatewaycontroller;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.*;

/**
 * This class implement a gateway controller as a floodlight module
 *
 * @author Harry Liu
 * @version April 1, 2017
 */
public class GatewayController implements IFloodlightModule, IOFMessageListener {
    protected        IFloodlightProviderService floodlightProvider;
    private        HashMap<IPv4Address, MacAddress>    ipToMacAddresses;
    private final String SWITCH_IP_ADDR = "10.45.2.2";
    private final String GET_A_PACKET_MSG = "GET packet";
    private final String SEND_OUT_PACKET_MSG = "SEND OUT packet";
    
    
    @Override
    public String getName() {
        return GatewayController.class.getSimpleName();
    }
    
    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                Ethernet ethernet = getEthernet(cntx);
                IPacket packet = ethernet.getPayload();
                if (isNotIPv4Packet(ethernet, packet)) return Command.CONTINUE;
                IPv4 iPv4 = (IPv4) packet;
                IPv4Address switchIpAddr = IPv4Address.of(SWITCH_IP_ADDR);
                
                logPacket(GET_A_PACKET_MSG, ethernet, iPv4.getSourceAddress(), switchIpAddr);
                IPv4Address destIpAddress = iPv4.getDestinationAddress();
                if(hasNoForwardingRule(destIpAddress)) return Command.CONTINUE;

                MacAddress destMacAddr = getDestMacAddress(destIpAddress);
                changeMacAddrFromSwitchToDest(ethernet, destMacAddr);
                logPacket(SEND_OUT_PACKET_MSG, ethernet,switchIpAddr, iPv4.getDestinationAddress());
                sendOutPacket(sw, ethernet);
                break;
            default:
                break;
        }
        System.out.println();
        return Command.CONTINUE;
    }
    
    /**
     * Log a given packet to standard output
     *
     * @param message The additional message
     * @param ethernet The ethernet
     * @param from The source IP address of the packet
     * @param to The destination IP address of the packet
     */
    private void logPacket(String message, Ethernet ethernet, IPv4Address from, IPv4Address to) {
        System.out.printf("[%S] %s from %s(%s) to %s(%s)\n", getName(), message,
                          from, ethernet.getSourceMACAddress(),
                          to, ethernet.getDestinationMACAddress());
    }
    
    /**
     * Get the ethernet information with Floodlight context
     * @param cntx The Floodlight context
     *
     * @return the ethernet with Floodlight context
     */
    private Ethernet getEthernet(FloodlightContext cntx) {
       return  IFloodlightProviderService.bcStore.get(cntx,
                                               IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
    }
    
    /**
     * Send out a layer 2 packet from a given switch
     *
     * @param sw The switch
     * @param ethernet The ethernet
     */
    private void sendOutPacket(IOFSwitch sw, Ethernet ethernet) {
        byte[] serializedData = ethernet.serialize();
        OFPacketOut po = sw.getOFFactory().buildPacketOut().setData(serializedData)
                .setActions(Collections.singletonList(sw.getOFFactory().actions()
                                                              .output(OFPort.NORMAL,
                                                                      1))).setInPort
                        (OFPort.CONTROLLER).build();
        sw.write(po);
    }
    
    /**
     * Set the source mac address of the packet to mac address of the switch and its destination
     * mac address to that of target host
     *
     * @param ethernet The ethernet
     * @param destMacAddr The mac address of destination host
     */
    private void changeMacAddrFromSwitchToDest(Ethernet ethernet, MacAddress destMacAddr) {
        ethernet.setSourceMACAddress(ethernet.getDestinationMACAddress());
        ethernet.setDestinationMACAddress(destMacAddr);
    }
    
    /**
     * Get the destination mac address of the destination IP address
     *
     * @param destIpAddress The destination IP address
     *
     * @return the destination mac address of the destination IP address
     */
    private MacAddress getDestMacAddress(IPv4Address destIpAddress) {
        return ipToMacAddresses.get(destIpAddress);
    }
    
    /**
     * Check whether there is a forwarding for the given destination IP address
     *
     * @param destIpAddress The destination IP address
     *
     * @return true if there is not forwarding rule for the given IP address, false otherwise
     */
    private boolean hasNoForwardingRule(IPv4Address destIpAddress) {
        return !ipToMacAddresses.containsKey(destIpAddress);
    }
    
    /**
     * Check whether a packet is a IPv4 packet
     *
     * @param ethernet The ethernet
     * @param packet The packet
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
    
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        ipToMacAddresses = new HashMap<>();
        ipToMacAddresses.put(IPv4Address.of("10.45.2.128"), MacAddress.of("52:54:00:45:16:07"));
        ipToMacAddresses.put(IPv4Address.of("10.45.2.1"), MacAddress.of("52:54:00:45:16:05"));
    }
    
    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }
}
