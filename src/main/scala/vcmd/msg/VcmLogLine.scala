package vcmd.msg

case class VcmLogLine(version: String, timestamp: String, hostName: String, appName: String, processId: String, messgeId: String, requestResponseSdId: String, fields: Map[String, String]) {
// val VCM_LOGLINE_PATTERN = "([^\\[]+)\\[([^\\s]+)\\s(.*)\\]".r
//
//    // Using possessive matching (x|y)*+ to prevent StackOverflow
//    val NAME_VALUE_PAIR_PATTERN = "(\\w+)=\"((?:\\\\.|[^\"])*+)\"".r;
//  
//     def apply( logLine:String):VcmLogLine = {
//       val parseError = s"Logline $logLine does not match pattern ${VCM_LOGLINE_PATTERN.pattern}" 
//       val matcher = VCM_LOGLINE_PATTERN.pattern.matcher(logLine);
//        if (matcher.find()) {
//            val header = matcher.group(1);
//            val split = header.substring(header.indexOf("INFO")).split("\\s");
//            if (split == null || split.length != 7) {
//                throw new IllegalArgumentException(parseError);
//            }
//            val version = split(1);
//            val timestamp = split(2);
//            val hostName = split(3);
//            val appName = split(4);
//            val processId = split(5);
//            val messageId = split(6);
//            val requestResponseSdId = matcher.group(2);
//            final Map<String, String> fields = toNVPair(matcher.group(3));
//            return new VCMLogLine(PRI_INFO, version, timestamp, hostName, appName, processId, messageId,
//                requestResponseSdId, fields);
//        }
//        val parseError =
//                String.format();
//        throw new IllegalArgumentException(parseError);
//    }
//
//    private def toNVPair(nvPairs:String):Map[String, String] =  {
//        val matcher = NAME_VALUE_PAIR_PATTERN.pattern.matcher(nvPairs);
//        NAME_VALUE_PAIR_PATTERN.findAllIn(source)
//        while (matcher.find()) {
//            for (int i = 1; i < matcher.groupCount() + 1; i += 2) {
//                val name = matcher.group(i);
//                val value = matcher.group(i + 1);
//                nvMap.put(name, value);
//            }
//        }
//        Map();
//    }
}