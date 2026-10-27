package it.unibo.hermes.e2e.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Utility class for creating forged JWTs.
 */
public final class ForgedJwt {

    private ForgedJwt() {}

    /**
     * Builds a HS256 JWT with the given subject and expiration time,
     * signed with the given key.
     *
     * @param subject   the {@code sub} claim
     * @param expiresAt the {@code exp} claim
     * @param key       the raw HMAC key bytes
     */
    public static String hs256(String subject, Instant expiresAt, byte[] key) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(("{\"sub\":\"" + subject + "\",\"iat\":"
                + (expiresAt.getEpochSecond() - 3600) + ",\"exp\":" + expiresAt.getEpochSecond() + "}")
                .getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return signingInput + "." + enc.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign test JWT", e);
        }
    }
}
