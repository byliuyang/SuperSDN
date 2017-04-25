package net.floodlightcontroller.gatewaycontroller.dns;

import net.floodlightcontroller.packet.IPv4;
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
    
    public static TCPConnection makeTCPConnection(TCP tcp, IPv4 iPv4) {
        TCPConnection connection = new TCPConnection();
        connection.clientAddr = iPv4.getSourceAddress();
        connection.serverAddr = iPv4.getDestinationAddress();
        connection.clientPort = tcp.getSourcePort().getPort();
        connection.serverPort = tcp.getDestinationPort().getPort();
        return connection;
    }
    
    public static TCPConnection makeReverseTCPConnection(TCP tcp, IPv4 iPv4) {
        TCPConnection reverseConnection = new TCPConnection();
        reverseConnection.clientAddr = iPv4.getDestinationAddress();
        reverseConnection.serverAddr = iPv4.getSourceAddress();
        reverseConnection.clientPort = tcp.getDestinationPort().getPort();
        reverseConnection.serverPort = tcp.getSourcePort().getPort();
        return reverseConnection;
    }
    
    
    public static TCPConnection makeReverseTCPConnection(TCPConnection connection) {
        TCPConnection reverseConnection = new TCPConnection();
        connection.clientAddr = connection.serverAddr;
        connection.serverAddr = connection.clientAddr;
        connection.clientPort = connection.serverPort;
        connection.serverPort = connection.clientPort;
        return reverseConnection;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        TCPConnection that = (TCPConnection) o;
        
        if (clientPort != that.clientPort) return false;
        if (serverPort != that.serverPort) return false;
        if (!clientAddr.equals(that.clientAddr)) return false;
        return serverAddr.equals(that.serverAddr);
    }
    
    @Override
    public int hashCode() {
        int result = clientAddr.hashCode();
        result = 31 * result + serverAddr.hashCode();
        result = 31 * result + clientPort;
        result = 31 * result + serverPort;
        return result;
    }
    
    @Override
    public String toString() {
        return "TCPConnection{" + "clientAddr=" + clientAddr + ", serverAddr=" + serverAddr + ", clientPort=" + clientPort + ", serverPort=" + serverPort + '}';
    }
}
