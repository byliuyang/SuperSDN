package net.floodlightcontroller.gatewaycontroller.dns;

import org.sdnplatform.sync.internal.util.Pair;

import java.util.ArrayList;

/**
 * This class implements ResourceRecord
 *
 * @author Harry Liu
 * @author Can Alper
 * @version April 22, 2017
 */
public class ResourceRecord {
    private String name;
    private int    type;
    private int    recordClass;
    private int    ttl;
    private int    dataPointer;
    private byte[] data;
    private static final int         POINTER             = 0x3;
    
    private String serializeData() {
        switch (type) {
            case 0x01:
                return String.format("%d.%d.%d.%d", data[0], data[1], data[2], data[3]);
        }
        return "";
    }
    
    public static int makeResourceRecord(byte[] bytes, int currIndex, ResourceRecord resourceRecord) {
        int address = currIndex;
        int nameType = (bytes[currIndex] & (0x3 << 6)) >> 6;
        if (nameType == POINTER) address = (((bytes[currIndex]) << 8) + bytes[currIndex + 1]) & 0x3fff;
        Pair<String, Integer> result = DNSUtils.readDomainName(bytes, address);
        resourceRecord.name = result.getFirst();
        currIndex = nameType == POINTER ? currIndex : result.getSecond();
        
        currIndex += 2;
        resourceRecord.type = ((bytes[currIndex]) << 8) + bytes[currIndex + 1];
        currIndex += 2;
        resourceRecord.recordClass = ((bytes[currIndex]) << 8) + bytes[currIndex + 1];
        currIndex += 2;
        
        resourceRecord.ttl = ((bytes[currIndex] & 0xff) << 24) + ((bytes[currIndex + 1] & 0xff) << 16) + ((bytes[currIndex + 2] & 0xff) << 8) + (bytes[currIndex + 3] & 0xff);
        currIndex += 4;
        int dataLength = ((bytes[currIndex]) << 8) + bytes[currIndex + 1];
        currIndex += 2;
        resourceRecord.dataPointer = currIndex;
        resourceRecord.data = new byte[dataLength];
        for (int i = 0; i < dataLength; i++)
            resourceRecord.data[i] = bytes[currIndex + i];
        return currIndex + dataLength;
    }
    
    public static byte[] modifyRecordData(byte[] bytes, DNSResponse dnsResponse, String domainName, byte[] data) {
        dnsResponse.getRecords().stream().filter(resourceRecord -> resourceRecord.name.equals(domainName)).forEach(resourceRecord -> {
            bytes[resourceRecord.dataPointer - 2] = (byte) ((data.length & (0xff << 8)) >> 8);
            bytes[resourceRecord.dataPointer - 1] = (byte) (data.length & 0xff);
            for (int i = 0; i < data.length; i++)
                bytes[resourceRecord.dataPointer + i] = data[i];
        });
        return bytes;
    }
    
    private String serializeRecordClass() {
        switch (recordClass) {
            case 0x01:
                return "IN";
        }
        return "";
    }
    
    public static int readRecord(byte[] bytes, int currIndex, int recordCount, ArrayList<ResourceRecord> result) {
        for (int i = 0; i < recordCount; i++) {
            ResourceRecord record = new ResourceRecord();
            currIndex = makeResourceRecord(bytes, currIndex, record);
            result.add(record);
        }
        return currIndex;
    }
    
    public String toString() {
        return String.format("%s %d %s\t%s\t%s", name, ttl, serializeRecordClass(), RecordType.getTypeStr(type), serializeData());
    }
    
    public String getName() {
        return name;
    }
    
    public int getTtl() {
        return ttl;
    }
}