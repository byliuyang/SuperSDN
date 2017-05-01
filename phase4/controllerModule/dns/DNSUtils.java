package net.floodlightcontroller.gatewaycontroller.dns;

import org.sdnplatform.sync.internal.util.Pair;

/**
 * This class implements organize DNS utilities
 *
 * @author Harry Liu
 * @author Can Alper
 * @version April 22, 2017
 */
public class DNSUtils {
    public static Pair<String, Integer> readDomainName(byte bytes[], int currIndex) {
        StringBuilder builder = new StringBuilder();
        int length = bytes[currIndex++];
        while (length > 0) {
            int end = currIndex + length;
            for (; currIndex < end; currIndex++) {
                builder.append((char) bytes[currIndex]);
            }
            builder.append('.');
            length = bytes[currIndex++];
        }
        return new Pair<>(builder.toString(), currIndex);
    }
}
