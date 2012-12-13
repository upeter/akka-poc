package vcm.msg;

import static org.junit.Assert.*;

import org.junit.Test;

public class VCMLogLineTest {
    static String logLine =
            "{UNKNOWN} Mon Apr 02 10:53:02 CEST 2012 INFO 1 2012-04-02T10:53:02.660+02:00 RB090498 SAM - messageType [sam_requestresponse@9211 timestamp=\"2012-04-02 10:53:02.441 +0200\" url=\" action=\"\" sessionid=\"201108041346451234\" functionname=\"100\" user=\"X123456789012\" s_timestamp=\"2012-04-02 10:53:02.645 +0200\" s_responsestatus=\"200\" s_user=\"X12345678912354678=cool\\\" \"]";

    private static String xml =
            "<LogonInfo xmlns=\\\"https://bankservices.rabobank.nl/auth/logoninfo/v1/request\\\"><RequestedService>https://testhost-services/service/anything</RequestedService><EntranceCode>201202211353</EntranceCode></LogonInfo>";

    private static String logLine2 =
            "{UNKNOWN} Thu Apr 05 10:51:59 CEST 2012 INFO 1 2012-04-05T10:51:59.032+02:00 RB090498 SAM - samRequestResponse [sam_requestresponse@9211 timestamp=\"2012-04-05T10:51:58.845Z\" url=\"http://localhost:9080/auth/logoninfo/v1/\" action=\"POST\" sessionId=\"1234567890123456789\" functionName=\"110\" user=\"\" ipAddress=\"1.1.1.1\" reqLevel=\"\" currentLevel=\"\" distrChannel=\"MBK\" host=\"testhost-services\" signInd=\"\" reverseProxy=\"\" cookieString=\"SAM=3d5fe949a5a8456c9567780163bb7067|||||||||||, sessionCode=0, TE3=N0:C N1:C N2:C N3:E N4:E N5:C N6:C N7:C N8:E N9:C N10:C N11:C N12:C N13:C N14:C N15:C N16:C N17:C N18:E N19:C N20:C N21:C N22:C N23:C N24:C N25:C N26:C N27:C N28:C N29:C N30:C N31:C, RaboTS=3c5bHM+H1DzpeWrd0V3A2Q==\" userAgent=\"Mozilla/5.0 (Windows NT 5.1; rv:2.0) Gecko/20100101 Firefox/4.0\" referer=\"\" postData=\""
                    + xml
                    + "\" s_timestamp=\"2012-04-05T10:51:59.001Z\" s_responseStatus=\"500\" s_user=\"\" authResult=\"\" s_authLevel=\"\" s_secProfile=\"\" s_prevSession=\"\" s_registerResult=\"\" s_signInd=\"\" s_endSession=\"1\" s_cookieString=\"KLANTINFO=\"\"; Domain=.rabobank.nl; Expires=Thu, 01-Jan-1970 00:00:10 GMT; Path=/; Secure; HttpOnly\"]";

    @Test public void shouldCreateVCMLogLine() throws InterruptedException {
        final VCMLogLine vcmLogLine = VCMLogLine.valueOf(logLine);
        assertNotNull(vcmLogLine);
        assertEquals("14", vcmLogLine.getPri());
        assertEquals("1", vcmLogLine.getVersion());
        assertEquals("2012-04-02T10:53:02.660+02:00", vcmLogLine.getTimestamp());
        assertEquals("SAM", vcmLogLine.getAppName());
        assertEquals("-", vcmLogLine.getProcessId());
        assertEquals("messageType", vcmLogLine.getMessageId());
        assertEquals("sam_requestresponse@9211", vcmLogLine.getRequestResponseSdId());
        assertEquals("201108041346451234", vcmLogLine.getFields().get("sessionid"));
        assertEquals("X12345678912354678=cool\\\" ", vcmLogLine.getFields().get("s_user"));
    }

    @Test public void shouldCreateVCMLogLineWithDoubleQuotes() throws InterruptedException {
        final VCMLogLine vcmLogLine = VCMLogLine.valueOf(logLine2);
        assertNotNull(vcmLogLine);
        assertEquals(xml, vcmLogLine.getFields().get("postData"));
    }

    @Test(expected = IllegalArgumentException.class) public void shouldThrowExceptionOnInvalidVCMLogLine()
            throws InterruptedException
    {
        VCMLogLine.valueOf("bla");
    }
}
