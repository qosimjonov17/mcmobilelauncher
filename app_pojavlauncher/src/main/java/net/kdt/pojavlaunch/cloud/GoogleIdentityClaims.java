package net.kdt.pojavlaunch.cloud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reads local identity metadata from a token returned directly by Credential Manager.
 * This does not verify a JWT signature and must never authorize a backend or game service.
 * No token is retained. A future backend must verify the original ID token itself.
 */
public final class GoogleIdentityClaims {
    private GoogleIdentityClaims() {}

    public static String subject(String payload, String clientId, long nowSeconds) {
        try {
            JsonObject claims = JsonParser.parseString(payload).getAsJsonObject();
            String issuer = claims.get("iss").getAsString();
            String subject = claims.get("sub").getAsString();
            if (!("https://accounts.google.com".equals(issuer) || "accounts.google.com".equals(issuer))
                    || !clientId.equals(claims.get("aud").getAsString())
                    || claims.get("exp").getAsLong() <= nowSeconds
                    || !subject.matches("[A-Za-z0-9_-]{1,255}")) {
                throw new IllegalArgumentException();
            }
            return subject;
        } catch (RuntimeException error) {
            // Do not echo token contents through a parse error or its cause.
            throw new IllegalArgumentException("Invalid Google identity response");
        }
    }
}
