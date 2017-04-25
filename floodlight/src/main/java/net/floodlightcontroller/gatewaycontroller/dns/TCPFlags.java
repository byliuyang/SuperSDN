package net.floodlightcontroller.gatewaycontroller.dns;

/**
 * This class specifies list of TCP flags
 *
 * @author Harry Liu
 * @version Apr 25, 2017
 */
public class TCPFlags {
    public static final short SYN = 0x2;
    public static final short SYN_ACK = 0x12;
    public static final short ACK = 0x10;
    public static final short RESET = 0x4;
    public static final short FIN = 0x1;
    public static final short FIN_ACK = 0x11;
}
