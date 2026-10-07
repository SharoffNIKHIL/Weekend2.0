package com.weekend.assistant.adapter.llm;

import com.weekend.assistant.port.LlmProvider.ImagePart;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Offline stand-in for Claude vision: real facts from the pixels (format, size, average colour, brightness). */
final class LocalVision {

    private LocalVision() {}

    static String describe(ImagePart img) {
        byte[] bytes = Base64.getDecoder().decode(img.base64());
        String kind = img.mediaType().substring("image/".length()).toUpperCase(Locale.ROOT);
        BufferedImage bi;
        try {
            bi = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            bi = null;
        }
        if (bi == null) {
            return kind + ", " + bytes.length + " bytes (the offline model cannot decode this format; Claude can)";
        }
        long rs = 0;
        long gs = 0;
        long bs = 0;
        int step = Math.max(1, Math.max(bi.getWidth(), bi.getHeight()) / 200);
        int n = 0;
        for (int y = 0; y < bi.getHeight(); y += step) {
            for (int x = 0; x < bi.getWidth(); x += step) {
                int rgb = bi.getRGB(x, y);
                rs += (rgb >> 16) & 0xff;
                gs += (rgb >> 8) & 0xff;
                bs += rgb & 0xff;
                n++;
            }
        }
        int r = (int) (rs / n);
        int g = (int) (gs / n);
        int b = (int) (bs / n);
        int brightness = (int) Math.round((0.2126 * r + 0.7152 * g + 0.0722 * b) / 2.55);
        String tone = brightness > 70 ? "bright" : brightness > 35 ? "medium" : "dark";
        String orient = bi.getWidth() > bi.getHeight() ? "landscape" : bi.getWidth() < bi.getHeight() ? "portrait" : "square";
        return String.format(Locale.ROOT, "%s %d×%d (%s), average colour #%02x%02x%02x, %s (%d%% brightness)",
                kind, bi.getWidth(), bi.getHeight(), orient, r, g, b, tone, brightness);
    }
}
