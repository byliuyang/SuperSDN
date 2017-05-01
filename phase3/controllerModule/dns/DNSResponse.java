package net.floodlightcontroller.gatewaycontroller.dns;

import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import org.apache.commons.lang.StringUtils;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.ArrayList;

/**
 * This class implements DNSResponse
 *
 * @author Harry Liu
 * @author Can Alper
 * @version April 22, 2017
 */
public class DNSResponse {
    private  int                                         transactionId;
    private int                                         questionRecordCount;
    private int                                         answerRecordCount;
    private  ArrayList<QuestionEntry>  questions;
    private ArrayList<ResourceRecord> records;
    
    public DNSResponse() {
        questions = new ArrayList<>();
        records = new ArrayList<>();
    }
    
    public static DNSResponse makeDNSResponse(byte[] bytes) {
        DNSResponse dnsResponse = new DNSResponse();
        dnsResponse.transactionId = (bytes[0] << 8) + bytes[1];
        dnsResponse.questionRecordCount = ((bytes[4]) << 8) + bytes[5];
        dnsResponse.answerRecordCount = ((bytes[6]) << 8) + bytes[7];
        int currIndex = 12;
        
        // Read question
        currIndex = QuestionEntry.readQuestion(bytes, currIndex, dnsResponse.questionRecordCount, dnsResponse.questions);
        ResourceRecord.readRecord(bytes, currIndex, dnsResponse.answerRecordCount, dnsResponse.records);
        
        return dnsResponse;
    }
    
    public static DNSResponse modifyDNSReponse(UDP udp, IPv4 iPv4, Ethernet ethernet, String domainName,IPv4Address serverAddr) {
        byte[] responseBytes = udp.getPayload().serialize();
        DNSResponse dnsResponse = makeDNSResponse(responseBytes);
        byte[] address = serverAddr.getBytes();
        byte[] modifiedResponseBytes = ResourceRecord.modifyRecordData(responseBytes, dnsResponse, domainName, address);
        Data newData = new Data(modifiedResponseBytes);
        udp.setPayload(newData);
        udp.resetChecksum();
        iPv4.setPayload(udp);
        ethernet.setPayload(iPv4);
        return makeDNSResponse(modifiedResponseBytes);
    }
    
    @Override
    public String toString() {
        return String.format("DNS Response\n\n;;Transaction ID\n%x\n\n;;QUERY: %d, ANSWER: %d\n\n;; QUESTION SECTION:\n%s\n\n;; ANSWER SECTION:\n%s\n", transactionId & 0xffff,
                             questionRecordCount, answerRecordCount, StringUtils.join(questions, "\n"), StringUtils.join(records, "\n"));
    }
    
    public ArrayList<ResourceRecord> getRecords() {
        return records;
    }
}