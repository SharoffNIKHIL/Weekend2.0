package com.weekend.assistant.studio;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Draws one video frame: a cinematic background with drifting light, then the scene (title, points, photo, outro),
 * a progress bar, the part badge and burned-in captions. Everything is drawn here, so there are no licensed assets.
 */
public final class Painter {

    static final Color INK = new Color(0x0B1024);
    static final Color NAVY = new Color(0x16224F);
    static final Color BLUE = new Color(0x1D5BFF);
    static final Color SKY = new Color(0x6E9BFF);
    static final Color WHITE = Color.WHITE;
    static final Color SOFT = new Color(255, 255, 255, 200);

    private final int w;
    private final int h;
    private final boolean vertical;
    private final Timeline timeline;
    private final String badge;
    private final boolean captions;
    private final Map<Script.Scene, BufferedImage> images;
    private final Map<Script.Scene, String> credits;
    private final String nextTitle;

    public Painter(int w, int h, Timeline timeline, String badge, boolean captions, Map<Script.Scene, BufferedImage> images,
            Map<Script.Scene, String> credits, String nextTitle) {
        this.w = w;
        this.h = h;
        this.vertical = h > w;
        this.timeline = timeline;
        this.badge = badge;
        this.captions = captions;
        this.images = images;
        this.credits = credits;
        this.nextTitle = nextTitle;
    }

    public void paint(Graphics2D g, double t) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        background(g, t);
        Timeline.Slot slot = timeline.slotAt(t);
        double local = t - slot.start();
        float in = (float) Math.min(1, local / 0.35);
        float out = (float) Math.min(1, (slot.end() - t) / 0.25);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(in, out))));
        switch (slot.scene().kind()) {
            case TITLE -> title(g, slot, local);
            case POINTS -> points(g, slot, local);
            case IMAGE -> image(g, slot, local);
            case OUTRO -> outro(g, slot, local);
        }
        g.setComposite(AlphaComposite.SrcOver);
        chrome(g, t);
        if (captions) {
            caption(g, timeline.captionAt(t));
        }
    }

    private int u(double fraction) {
        return (int) Math.round(Math.min(w, h) * fraction);
    }

    private void background(Graphics2D g, double t) {
        g.setPaint(new GradientPaint(0, 0, INK, 0, h, NAVY));
        g.fillRect(0, 0, w, h);
        for (int i = 0; i < 3; i++) {
            double a = t * (0.15 + i * 0.07) + i * 2.1;
            float cx = (float) (w * (0.5 + 0.35 * Math.cos(a)));
            float cy = (float) (h * (0.45 + 0.3 * Math.sin(a * 1.3)));
            float r = (float) (Math.max(w, h) * (0.35 + 0.05 * i));
            Color c = i == 1 ? new Color(110, 155, 255, 70) : new Color(29, 91, 255, 85);
            g.setPaint(new RadialGradientPaint(cx, cy, r, new float[] {0f, 1f}, new Color[] {c, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
            g.fillRect(0, 0, w, h);
        }
    }

    private void title(Graphics2D g, Timeline.Slot slot, double local) {
        double rise = Math.max(0, 1 - local / 0.6);
        int size = u(vertical ? 0.095 : 0.085);
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, size);
        List<String> lines = wrap(g, f, slot.scene().heading(), (int) (w * 0.84));
        int lh = (int) (size * 1.12);
        int y0 = (int) (h * (vertical ? 0.40 : 0.38) - lines.size() * lh / 2.0 + rise * u(0.08));
        g.setFont(f);
        g.setColor(WHITE);
        for (int i = 0; i < lines.size(); i++) {
            center(g, lines.get(i), y0 + i * lh);
        }
        int lineW = (int) (w * 0.22 * Math.min(1, local / 0.8));
        g.setColor(BLUE);
        g.fillRoundRect((w - lineW) / 2, y0 + lines.size() * lh - lh / 3, lineW, u(0.012), u(0.012), u(0.012));
    }

    private void points(Graphics2D g, Timeline.Slot slot, double local) {
        Script.Scene s = slot.scene();
        heading(g, s.heading(), (int) (h * (vertical ? 0.22 : 0.2)));
        List<String> bullets = s.bullets().isEmpty() ? sentenceBullets(s.narration()) : s.bullets();
        int size = u(vertical ? 0.058 : 0.05);
        Font f = new Font(Font.SANS_SERIF, Font.PLAIN, size);
        int x = (int) (w * 0.1);
        int y = (int) (h * (vertical ? 0.34 : 0.36));
        int gap = (int) (size * (vertical ? 2.4 : 2.0));
        double each = Math.max(0.6, (slot.duration() - 0.6) / Math.max(1, bullets.size()));
        for (int i = 0; i < bullets.size(); i++) {
            double appear = 0.3 + i * each;
            if (local < appear) {
                break;
            }
            float p = (float) Math.min(1, (local - appear) / 0.35);
            int dx = (int) ((1 - p) * u(0.06));
            int box = (int) (size * 0.9);
            g.setColor(BLUE);
            g.fillRoundRect(x - dx, y + i * gap - box + size / 6, box, box, box / 3, box / 3);
            g.setColor(WHITE);
            g.setStroke(new BasicStroke(Math.max(2f, size / 9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int bx = x - dx;
            int by = y + i * gap - box + size / 6;
            g.drawPolyline(new int[] {bx + box / 4, bx + box / 2 - box / 12, bx + box * 3 / 4 + box / 12},
                    new int[] {by + box / 2, by + box * 3 / 4 - box / 12, by + box / 4}, 3);
            g.setFont(f);
            List<String> lines = wrap(g, f, bullets.get(i), (int) (w * 0.74));
            for (int k = 0; k < lines.size() && k < 2; k++) {
                g.drawString(lines.get(k), x - dx + box + size / 2, y + i * gap + k * (int) (size * 1.15));
            }
        }
    }

    private void image(Graphics2D g, Timeline.Slot slot, double local) {
        BufferedImage img = images.get(slot.scene());
        if (img == null) {
            points(g, slot, local);
            return;
        }
        double zoom = 1.04 + 0.10 * Math.min(1, local / Math.max(1, slot.duration()));
        double scale = Math.max((double) w / img.getWidth(), (double) h / img.getHeight()) * zoom;
        int iw = (int) (img.getWidth() * scale);
        int ih = (int) (img.getHeight() * scale);
        int ix = (w - iw) / 2 - (int) (u(0.02) * local / Math.max(1, slot.duration()));
        int iy = (h - ih) / 2;
        g.drawImage(img, ix, iy, iw, ih, null);
        g.setPaint(new GradientPaint(0, h * 0.45f, new Color(11, 16, 36, 0), 0, h, new Color(11, 16, 36, 230)));
        g.fillRect(0, 0, w, h);
        g.setPaint(new GradientPaint(0, 0, new Color(11, 16, 36, 180), 0, h * 0.25f, new Color(11, 16, 36, 0)));
        g.fillRect(0, 0, w, h);
        heading(g, slot.scene().heading(), (int) (h * (vertical ? 0.16 : 0.15)));
        String credit = credits.get(slot.scene());
        if (credit != null) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, u(0.02)));
            g.setColor(SOFT);
            String c = credit.length() > 80 ? credit.substring(0, 77) + "..." : credit;
            g.drawString("Photo: " + c, u(0.03), h - u(0.03));
        }
    }

    private void outro(Graphics2D g, Timeline.Slot slot, double local) {
        int size = u(vertical ? 0.085 : 0.075);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        g.setColor(WHITE);
        center(g, slot.scene().heading().isBlank() ? "Thanks for watching" : slot.scene().heading(), (int) (h * 0.4));
        if (nextTitle != null) {
            Font f = new Font(Font.SANS_SERIF, Font.PLAIN, u(0.042));
            g.setFont(f);
            List<String> lines = wrap(g, f, "Next: " + nextTitle, (int) (w * 0.78));
            int bw = (int) (w * 0.84);
            int lh = (int) (u(0.042) * 1.3);
            int bh = lines.size() * lh + u(0.05);
            int bx = (w - bw) / 2;
            int by = (int) (h * 0.48);
            g.setColor(new Color(255, 255, 255, 30));
            g.fill(new RoundRectangle2D.Double(bx, by, bw, bh, u(0.04), u(0.04)));
            g.setColor(WHITE);
            for (int i = 0; i < lines.size(); i++) {
                center(g, lines.get(i), by + u(0.025) + (i + 1) * lh - lh / 4);
            }
        }
        double pulse = 1 + 0.05 * Math.sin(local * 5);
        int pw = (int) (w * 0.42 * pulse);
        int ph = (int) (u(0.09) * pulse);
        int py = (int) (h * (nextTitle != null ? 0.66 : 0.52));
        g.setColor(BLUE);
        g.fillRoundRect((w - pw) / 2, py, pw, ph, ph, ph);
        g.setColor(WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, u(0.045)));
        center(g, "Subscribe", py + ph / 2 + u(0.016));
    }

    private void chrome(Graphics2D g, double t) {
        int barH = Math.max(3, u(0.008));
        g.setColor(new Color(255, 255, 255, 40));
        g.fillRect(0, 0, w, barH);
        g.setColor(BLUE);
        g.fillRect(0, 0, (int) (w * Math.min(1, t / timeline.total())), barH);
        if (badge != null) {
            Font f = new Font(Font.SANS_SERIF, Font.BOLD, u(0.03));
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            int pad = u(0.018);
            int bw = fm.stringWidth(badge) + pad * 2;
            int bh = fm.getHeight() + pad / 2;
            int x = u(0.04);
            int y = u(0.04) + barH;
            g.setColor(new Color(29, 91, 255, 220));
            g.fillRoundRect(x, y, bw, bh, bh, bh);
            g.setColor(WHITE);
            g.drawString(badge, x + pad, y + bh - fm.getDescent() - pad / 4);
        }
    }

    private void caption(Graphics2D g, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, u(vertical ? 0.052 : 0.048));
        g.setFont(f);
        List<String> lines = wrap(g, f, text, (int) (w * 0.82));
        FontMetrics fm = g.getFontMetrics();
        int lh = (int) (fm.getHeight() * 1.05);
        int bh = lines.size() * lh + u(0.03);
        int bw = lines.stream().mapToInt(fm::stringWidth).max().orElse(0) + u(0.06);
        int y = (int) (h * (vertical ? 0.70 : 0.82)); // Shorts: above YouTube's bottom controls
        g.setColor(new Color(0, 0, 0, 170));
        g.fill(new RoundRectangle2D.Double((w - bw) / 2.0, y, bw, bh, u(0.03), u(0.03)));
        g.setColor(WHITE);
        for (int i = 0; i < lines.size(); i++) {
            center(g, lines.get(i), y + u(0.015) + (i + 1) * lh - fm.getDescent());
        }
    }

    private void heading(Graphics2D g, String text, int y) {
        if (text == null || text.isBlank()) {
            return;
        }
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, u(vertical ? 0.07 : 0.062));
        g.setFont(f);
        g.setColor(SKY);
        List<String> lines = wrap(g, f, text, (int) (w * 0.86));
        for (int i = 0; i < lines.size() && i < 2; i++) {
            center(g, lines.get(i), y + i * (int) (f.getSize() * 1.1));
        }
        g.setColor(WHITE);
    }

    private void center(Graphics2D g, String s, int y) {
        g.drawString(s, (w - g.getFontMetrics().stringWidth(s)) / 2, y);
    }

    static List<String> wrap(Graphics2D g, Font f, String text, int max) {
        FontMetrics fm = g.getFontMetrics(f);
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : (text == null ? "" : text).split("\\s+")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(next) > max && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        return out;
    }

    static List<String> sentenceBullets(String narration) {
        List<String> out = new ArrayList<>();
        for (String s : narration.split("(?<=[.!?])\\s+")) {
            String t = s.strip().replaceAll("[.!?]$", "");
            if (!t.isEmpty()) {
                out.add(t.length() > 48 ? t.substring(0, 45) + "…" : t);
            }
            if (out.size() == 3) {
                break;
            }
        }
        return out;
    }
}
