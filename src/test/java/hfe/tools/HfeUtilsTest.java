package hfe.tools;

import org.jboss.security.Base64Encoder;
import org.jboss.security.auth.callback.RFC2617Digest;
import org.testng.annotations.Test;

public class HfeUtilsTest {

    @Test
    public void checkDigestEncryption() throws Exception {
        RFC2617Digest.main(new String[] {"feidner", "nop", "10Hendi!"});
        Base64Encoder.main(new String[] {"10Hendi!", "MD5"});
    }

}