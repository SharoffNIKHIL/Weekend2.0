package com.weekend.assistant.studio;

import com.weekend.assistant.config.WeekendProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Short-lived signed links for rendered files, so a {@code <video>} element can stream them without the owner's
 * session token in the URL: HMAC-SHA256 over job, file and expiry. Key: the session key, or a random per-start key.
 */
@Component
public class MediaSigner {

    static final long TTL_SECONDS = 6 * 3600;

    private final byte[] key;
    private final Clock clock;

    public MediaSigner(WeekendProperties props, Clock clock) {
        String k = props.security().sessionKey();
        if (k != null && k.length() >= 32) {
            this.key = ("media:" + k).getBytes(StandardCharsets.UTF_8);
        } else {
            this.key = new byte[32];
            new SecureRandom().nextBytes(this.key);
        }
        this.clock = clock;
    }

    public String url(String jobId, String file) {
        long exp = clock.instant().getEpochSecond() + TTL_SECONDS;
        return "/media/" + jobId + "/" + file + "?exp=" + exp + "&sig=" + sig(jobId, file, exp);
    }

    public boolean valid(String jobId, String file, long exp, String sig) {
        if (sig == null || exp < clock.instant().getEpochSecond()) {
            return false;
        }
        return MessageDigest.isEqual(sig(jobId, file, exp).getBytes(StandardCharsets.US_ASCII), sig.getBytes(StandardCharsets.US_ASCII));
    }

    private String sig(String jobId, String file, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] d = mac.doFinal((jobId + "|" + file + "|" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
