package vcm.msg;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VCMLogLine {
    private static final String PRI_INFO = "14";
    private final static Pattern VCM_LOGLINE_PATTERN = Pattern.compile("([^\\[]+)\\[([^\\s]+)\\s(.*)\\]");

    // Using possessive matching (x|y)*+ to prevent StackOverflow
    private final static Pattern NAME_VALUE_PAIR_PATTERN = Pattern.compile("(\\w+)=\"((?:\\\\.|[^\"])*+)\"");

    final String pri;
    final String version;
    final String timestamp;
    final String hostName;
    final String appName;
    final String processId;
    final String messageId;
    final String requestResponseSdId;
    final Map<String, String> fields;

    public VCMLogLine(final String pri, final String version, final String timestamp, final String hostName,
                      final String appName, final String processId, final String messageId,
                      final String requestResponseSdId, final Map<String, String> fields)
    {
        super();
        this.pri = pri;
        this.version = version;
        this.timestamp = timestamp;
        this.hostName = hostName;
        this.appName = appName;
        this.processId = processId;
        this.messageId = messageId;
        this.requestResponseSdId = requestResponseSdId;
        this.fields = fields;
    }

    public String getPri() {
        return pri;
    }

    public String getVersion() {
        return version;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getHostName() {
        return hostName;
    }

    public String getAppName() {
        return appName;
    }

    public String getProcessId() {
        return processId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getRequestResponseSdId() {
        return requestResponseSdId;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public static VCMLogLine valueOf(final String logLine) {
        final String parseError =
                String.format("Logline %s does not match pattern %s", logLine, VCM_LOGLINE_PATTERN.pattern());
        final Matcher matcher = VCM_LOGLINE_PATTERN.matcher(logLine);
        if (matcher.find()) {
            final String header = matcher.group(1);
            final String[] split = header.substring(header.indexOf("INFO")).split("\\s");
            if (split == null || split.length != 7) {
                throw new IllegalArgumentException(parseError);
            }
            final String version = split[1];
            final String timestamp = split[2];
            final String hostName = split[3];
            final String appName = split[4];
            final String processId = split[5];
            final String messageId = split[6];
            final String requestResponseSdId = matcher.group(2);
            final Map<String, String> fields = toNVPair(matcher.group(3));
            return new VCMLogLine(PRI_INFO, version, timestamp, hostName, appName, processId, messageId,
                requestResponseSdId, fields);
        }
        throw new IllegalArgumentException(parseError);
    }

    private static Map<String, String> toNVPair(final String nvPairs) {
        final Map<String, String> nvMap = new HashMap<String, String>();
        final Matcher matcher = NAME_VALUE_PAIR_PATTERN.matcher(nvPairs);
        while (matcher.find()) {
            for (int i = 1; i < matcher.groupCount() + 1; i += 2) {
                final String name = matcher.group(i);
                final String value = matcher.group(i + 1);
                nvMap.put(name, value);
            }
        }
        return nvMap;
    }

}
