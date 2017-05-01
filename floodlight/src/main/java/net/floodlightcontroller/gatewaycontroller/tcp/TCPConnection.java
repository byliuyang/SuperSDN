package net.floodlightcontroller.gatewaycontroller.tcp;

import net.floodlightcontroller.packet.TCP;
import org.projectfloodlight.openflow.types.IPv4Address;

/**
 * This class models TCPConnection
 *
 * @author Harry Liu
 * @version Apr 25, 2017
 */
public class TCPConnection {
    private IPv4Address clientAddr;
    private IPv4Address serverAddr;
    private  int clientPort;
    private  int serverPort;
    
    public static TCPConnection makeTCPConnection(TCP tcp, IPv4Address serverIp) {
        TCPConnection connection = new TCPConnection();
        connection.serverAddr = serverIp;
        connection.clientPort = tcp.getSourcePort().getPort();
        connection.serverPort = tcp.getDestinationPort().getPort();
        return connection;
    }
    
    public static TCPConnection makeTCPConnection(TCP tcp, IPv4Address src, IPv4Address dst) {
        TCPConnection connection = new TCPConnection();
        connection.clientAddr = src;
        connection.serverAddr = dst;
        connection.clientPort = tcp.getSourcePort().getPort();
        connection.serverPort = tcp.getDestinationPort().getPort();
        return connection;
    }
    
    public static TCPConnection makeReverseTCPConnection(TCP tcp, IPv4Address src, IPv4Address dst) {
        TCPConnection connection = new TCPConnection();
        connection.clientAddr = dst;
        connection.serverAddr = src;
        connection.clientPort = tcp.getDestinationPort().getPort();
        connection.serverPort = tcp.getSourcePort().getPort();
        return connection;
    }
    
    public void setClientAddr(IPv4Address clientAddr) {
        this.clientAddr = clientAddr;
    }
    
    public IPv4Address getServerAddr() {
        return serverAddr;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        TCPConnection that = (TCPConnection) o;
        
        if (clientPort != that.clientPort) return false;
        if (serverPort != that.serverPort) return false;
        return serverAddr.equals(that.serverAddr);
    }
    
    @Override
    public int hashCode() {
        int result = serverAddr.hashCode();
        result = 31 * result + clientPort;
        result = 31 * result + serverPort;
        return result;
    }
    
    @Override
    public String toString() {
        return "[TCPConnection]" + " clientAddr:" + clientAddr + " serverAddr:" + serverAddr + " clientPort=" + clientPort + " serverPort=" + serverPort;
    }
}
