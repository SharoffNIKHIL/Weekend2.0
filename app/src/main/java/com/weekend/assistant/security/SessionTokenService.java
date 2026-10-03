package com.weekend.assistant.security;

import com.weekend.assistant.config.WeekendProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Owner session tokens: {@code base64url(owner:<expiryEpochSeconds>).base64url(HMAC-SHA256)}.
 * The key comes from Secret Manager ({@code <prefix>-app-session-signing-key}) via WEEKEND_SESSION_KEY.
 * Passkey (WebAuthn) login that issues these tokens is the D6 work in Phase 2.
 */
@Service
public class SessionTokenService {

    private static final String SUBJECT = "owner";
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final byte[] key;
    private final WeekendProperties.Security settings;
    private final Clock clock;

    public SessionTokenService(WeekendProperties props, Clock clock) {
        this.settings = props.security();
        this.clock = clock;
        String raw = settings.sessionKey();
        if (settings.requireSession() && (raw == null || raw.length() < 32)) {
            throw new IllegalStateException("weekend.security.session-key must be at least 32 characters when sessions are required");
        }
        this.key = (raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8);
    }

    public String issue() {
        long expiry = clock.instant().plus(settings.sessionTtl()).getEpochSecond();
        String payload = ENC.encodeToString((SUBJECT + ":" + expiry).getBytes(StandardCharsets.UTF_8));
        return payload + "." + ENC.encodeToString(sign(payload));
    }

    /** Returns the expiry when the token is authentic and unexpired. */
    public Optional<Instant> verify(String token) {
        if (token == null || key.length == 0) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        String payload = token.substring(0, dot);
        byte[] given;
        try {
            given = DEC.decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(sign(payload), given)) {
            return Optional.empty();
        }
        String[] parts = new String(DEC.decode(payload), StandardCharsets.UTF_8).split(":");
        if (parts.length != 2 || !SUBJECT.equals(parts[0])) {
            return Optional.empty();
        }
        Instant expiry;
        try {
            expiry = Instant.ofEpochSecond(Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return expiry.isAfter(clock.instant()) ? Optional.of(expiry) : Optional.empty();
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.length == 0 ? new byte[1] : key, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
