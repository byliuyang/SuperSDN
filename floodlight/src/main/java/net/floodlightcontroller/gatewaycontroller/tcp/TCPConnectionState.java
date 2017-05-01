package net.floodlightcontroller.gatewaycontroller.tcp;

/**
 * This class keep track of the state of TCPConnection
 *
 * @author Harry Liu
 * @version Apr 25, 2017
 */
public class TCPConnectionState {
    private int synForwardCount;
    private int synAckBackwardCount;
    private int ackForwardCount;
    private  int ackBackwardCount;
    private  int finAckForwardCount;
    private  int finAckBackwardCount;
    
    public int getSynForwardCount() {
        return synForwardCount;
    }
    
    public int getAckForwardCount() {
        return ackForwardCount;
    }
    
    public int getAckBackwardCount() {
        return ackBackwardCount;
    }
    
    public int getFinAckForwardCount() {
        return finAckForwardCount;
    }
    
    public int getFinAckBackwardCount() {
        return finAckBackwardCount;
    }
    
    public int getSynAckBackwardCount() {
        return synAckBackwardCount;
    }
    
    public void synforward() {
        synForwardCount++;
    }
    
    public void synAckBackward() {
        synAckBackwardCount++;
    }
    
    public void ackFoward() {
        ackForwardCount++;
    }
    
    public void ackBackward() {
        ackBackwardCount++;
    }
    
    public void finAckForward() {
        finAckForwardCount++;
    }
    
    public void finAckBackward() {
        finAckBackwardCount++;
    }
    
    public boolean initiatedConnection() {
        return synForwardCount == 1 && synAckBackwardCount == 0 && finAckForwardCount == 0 && finAckBackwardCount == 0 && ackForwardCount == 0 && ackBackwardCount == 0;
    }
    
    public boolean handShaked() {
        return synForwardCount == 1 && synAckBackwardCount == 1;
    }
    
    public boolean terminating() {
        return finAckForwardCount == 1 && finAckBackwardCount == 0;
    }
    
    public boolean terminated() {
        return finAckForwardCount == 1 && finAckBackwardCount == 1;
    }
    
    public boolean isNewConnection() {
        return synForwardCount == 1 && synAckBackwardCount == 1 && finAckForwardCount == 0 && finAckBackwardCount == 0 && ackForwardCount == 0 && ackBackwardCount == 0;
    }
    
    @Override
    public String toString() {
        return "TCPConnectionState{" + "synForwardCount=" + synForwardCount + ", synAckBackwardCount=" + synAckBackwardCount + ", ackForwardCount=" + ackForwardCount + ", ackBackwardCount=" +
               ackBackwardCount + ", finAckForwardCount=" + finAckForwardCount + ", finAckBackwardCount=" + finAckBackwardCount + '}';
    }
}
