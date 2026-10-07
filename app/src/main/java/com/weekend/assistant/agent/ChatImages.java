package com.weekend.assistant.agent;

import com.weekend.assistant.port.LlmProvider.ImagePart;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Validates images attached to a chat turn: at most 4, each at most 5 MB, PNG/JPEG/WebP/GIF only, and the bytes must
 * really be that format (magic numbers). Accepts data URLs ("data:image/png;base64,…"). Images are sent to the model
 * for that turn only and are never stored (P5); 🔓 with Vertex AI they leave Weekend like the prompt (P7).
 */
public final class ChatImages {

    public static final int MAX_IMAGES = 4;
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    static final Map<String, byte[]> MAGIC = Map.of(
            "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'},
            "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "image/gif", new byte[] {'G', 'I', 'F', '8'},
            "image/webp", new byte[] {'R', 'I', 'F', 'F'});

    private ChatImages() {}

    public static List<ImagePart> parse(List<String> dataUrls) {
        if (dataUrls == null || dataUrls.isEmpty()) {
            return List.of();
        }
        if (dataUrls.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("attach at most " + MAX_IMAGES + " images");
        }
        List<ImagePart> out = new ArrayList<>();
        for (String url : dataUrls) {
            if (url == null || !url.startsWith("data:") || !url.contains(";base64,")) {
                throw new IllegalArgumentException("images must be data URLs (data:image/png;base64,…)");
            }
            String type = url.substring(5, url.indexOf(';')).toLowerCase(java.util.Locale.ROOT);
            if (!MAGIC.containsKey(type)) {
                throw new IllegalArgumentException("images must be PNG, JPEG, WebP or GIF");
            }
            String b64 = url.substring(url.indexOf(";base64,") + 8);
            if (b64.length() > MAX_BYTES / 3 * 4 + 8) {
                throw new IllegalArgumentException("each image must be at most 5 MB");
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(b64);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("an image is not valid base64");
            }
            if (bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("each image must be at most 5 MB");
            }
            if (!startsWith(bytes, MAGIC.get(type)) || (type.equals("image/webp") && !webp(bytes))) {
                throw new IllegalArgumentException("an image's content does not match its type");
            }
            out.add(new ImagePart(type, b64));
        }
        return out;
    }

    private static boolean startsWith(byte[] b, byte[] magic) {
        if (b.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (b[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean webp(byte[] b) {
        return b.length >= 12 && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
