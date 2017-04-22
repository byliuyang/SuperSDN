package net.floodlightcontroller.gatewaycontroller.dns;

/**
 * This class implements RecordType
 *
 * @author Harry Liu
 * @author Can Alper
 * @version April 22, 2017
 */
public class RecordType {
    public static String getTypeStr(int type) {
        switch (type) {
            case 0x01:
                return "A";
            case 0x02:
                return "NS";
            case 0x05:
                return "CNAME";
            case 0x0C:
                return "PTR";
            case 0x0F:
                return "MX";
            case 0x21:
                return "SRV";
            case 0xFB:
                return "IXFR";
            case 0xFC:
                return "AXFR";
            case 0xFF:
                return "All";
        }
        return "";
    }
}
