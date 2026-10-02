package net.kdt.pojavlaunch.cloud;

import org.junit.Test;
import static org.junit.Assert.*;

public class GoogleIdentityClaimsTest {
    private static final String PAYLOAD = "{\"iss\":\"https://accounts.google.com\",\"aud\":\"client\",\"exp\":200,\"sub\":\"12345\"}";

    @Test public void readsProviderSubject() {
        assertEquals("12345", GoogleIdentityClaims.subject(PAYLOAD, "client", 100));
    }

    @Test public void rejectsWrongAudienceIssuerExpiredAndMissingSubject() {
        assertThrows(IllegalArgumentException.class, () -> GoogleIdentityClaims.subject(PAYLOAD, "other", 100));
        assertThrows(IllegalArgumentException.class, () -> GoogleIdentityClaims.subject(PAYLOAD, "client", 200));
        assertThrows(IllegalArgumentException.class, () -> GoogleIdentityClaims.subject(PAYLOAD.replace("accounts.google.com", "example.com"), "client", 100));
        assertThrows(IllegalArgumentException.class, () -> GoogleIdentityClaims.subject("{}", "client", 100));
    }

    @Test public void malformedProviderResponseCannotLeakIntoErrors() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> GoogleIdentityClaims.subject("not-json SECRET_TOKEN", "client", 100));
        assertFalse(error.toString().contains("SECRET_TOKEN"));
        assertNull(error.getCause());
    }
}
