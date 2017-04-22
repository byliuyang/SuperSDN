package net.floodlightcontroller.gatewaycontroller.dns;

import org.sdnplatform.sync.internal.util.Pair;

import java.util.ArrayList;

/**
 * This class implements QuestionEntry
 *
 * @author Harry Liu
 * @author Can Alper
 * @version April 22, 2017
 */
public class QuestionEntry {
    private String name;
    private int    type;
    private int    questionClass;
    
    private String serializeQuestionClass() {
        switch (questionClass) {
            case 0x01:
                return "IN";
        }
        return "";
    }
    
    public static int readQuestion(byte[] bytes, int currIndex, int recordCount, ArrayList<QuestionEntry> result) {
        for (int i = 0; i < recordCount; i++) {
            QuestionEntry questionEntry = new QuestionEntry();
            currIndex = makeQuestionEntry(bytes, currIndex, questionEntry);
            result.add(questionEntry);
        }
        return currIndex;
    }
    
    public static int makeQuestionEntry(byte[] bytes, int currIndex, QuestionEntry questionEntry) {
        Pair<String, Integer> result = DNSUtils.readDomainName(bytes, currIndex);
        questionEntry.name = result.getFirst();
        currIndex = result.getSecond();
        questionEntry.type = ((bytes[currIndex]) << 8) + bytes[currIndex + 1];
        currIndex += 2;
        questionEntry.questionClass = ((bytes[currIndex]) << 8) + bytes[currIndex + 1];
        return currIndex + 2;
    }
    
    @Override
    public String toString() {
        return String.format(";%s\t%s\t%s", name, serializeQuestionClass(), RecordType.getTypeStr(type));
    }
}