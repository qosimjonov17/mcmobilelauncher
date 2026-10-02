package net.kdt.pojavlaunch.authenticator.microsoft;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class AuthDiagnosticsTest {
    @Test public void excludesMessagesAndNestedCredentials() {
        Exception error = new IOException("https://login.live.com/?code=SECRET_CODE",
                new RuntimeException("refresh_token=SECRET_REFRESH"));
        assertEquals("Microsoft authentication failed (IOException)", AuthDiagnostics.failure(error));
    }
}
