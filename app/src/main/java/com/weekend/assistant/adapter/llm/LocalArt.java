package com.weekend.assistant.adapter.llm;

import java.util.Locale;
import java.util.Random;

/**
 * Offline stand-in for Claude's SVG art: a layered, deterministic landscape (sky gradient, sun or moon, glow, stars,
 * three mountain ranges with atmospheric depth, water reflection) seeded from the prompt, at 3840×2160 (4K).
 */
final class LocalArt {

    private LocalArt() {}

    static String svg(String prompt) {
        String p = prompt.toLowerCase(Locale.ROOT);
        Random r = new Random(p.hashCode());
        boolean night = p.contains("night") || p.contains("moon") || p.contains("star") || p.contains("space");
        boolean sunset = p.contains("sunset") || p.contains("dusk") || p.contains("evening");
        String[] sky = night ? new String[] {"#060a1f", "#1b2259", "#3a2f6b"} : sunset ? new String[] {"#2b1055", "#d1436e", "#ffb36b"}
                : new String[] {"#1d5bff", "#6e9bff", "#e8f0ff"};
        String[] hills = night ? new String[] {"#2a2f5c", "#1c2048", "#0f1230"} : sunset ? new String[] {"#7a2a5b", "#4e1d4a", "#2a0f33"}
                : new String[] {"#6b8fd6", "#3c5fae", "#203a7a"};
        StringBuilder s = new StringBuilder();
        s.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 3840 2160\">");
        s.append("<defs><linearGradient id=\"sky\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">")
                .append("<stop offset=\"0\" stop-color=\"").append(sky[0]).append("\"/><stop offset=\".6\" stop-color=\"").append(sky[1])
                .append("\"/><stop offset=\"1\" stop-color=\"").append(sky[2]).append("\"/></linearGradient>")
                .append("<radialGradient id=\"glow\"><stop offset=\"0\" stop-color=\"#fff6d8\" stop-opacity=\".95\"/>")
                .append("<stop offset=\"1\" stop-color=\"#fff6d8\" stop-opacity=\"0\"/></radialGradient>")
                .append("<linearGradient id=\"water\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\"><stop offset=\"0\" stop-color=\"").append(hills[2])
                .append("\"/><stop offset=\"1\" stop-color=\"").append(sky[0]).append("\"/></linearGradient>")
                .append("<filter id=\"blur\"><feGaussianBlur stdDeviation=\"18\"/></filter></defs>");
        s.append("<rect width=\"3840\" height=\"2160\" fill=\"url(#sky)\"/>");
        if (night) {
            for (int i = 0; i < 260; i++) {
                s.append(String.format(Locale.ROOT, "<circle cx=\"%d\" cy=\"%d\" r=\"%.1f\" fill=\"#fff\" opacity=\"%.2f\"/>",
                        r.nextInt(3840), r.nextInt(1200), 1 + r.nextDouble() * 3.5, 0.3 + r.nextDouble() * 0.7));
            }
        }
        int sx = 900 + r.nextInt(2000);
        int sy = night ? 420 : sunset ? 1180 : 520;
        s.append(String.format(Locale.ROOT, "<circle cx=\"%d\" cy=\"%d\" r=\"620\" fill=\"url(#glow)\"/>", sx, sy));
        s.append(String.format(Locale.ROOT, "<circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\"/>", sx, sy, night ? 150 : 210,
                night ? "#f4f1e3" : "#fff2c9"));
        int[] base = {1250, 1420, 1580};
        for (int layer = 0; layer < 3; layer++) {
            StringBuilder d = new StringBuilder("M0 2160 L0 ").append(base[layer]);
            for (int x = 0; x <= 3840; x += 160) {
                int y = base[layer] - (int) (Math.abs(Math.sin(x / (380.0 + layer * 90) + r.nextDouble())) * (420 - layer * 110))
                        - r.nextInt(70);
                d.append(" L").append(x).append(' ').append(y);
            }
            d.append(" L3840 2160 Z");
            s.append("<path d=\"").append(d).append("\" fill=\"").append(hills[layer]).append("\" opacity=\"").append(0.75 + layer * 0.12)
                    .append("\"/>");
        }
        s.append("<rect y=\"1760\" width=\"3840\" height=\"400\" fill=\"url(#water)\" opacity=\".9\"/>");
        s.append(String.format(Locale.ROOT, "<ellipse cx=\"%d\" cy=\"1900\" rx=\"260\" ry=\"40\" fill=\"#fff6d8\" opacity=\".35\" filter=\"url(#blur)\"/>", sx));
        for (int i = 0; i < 14; i++) {
            s.append(String.format(Locale.ROOT, "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"6\" rx=\"3\" fill=\"#fff\" opacity=\".18\"/>",
                    sx - 200 + r.nextInt(400), 1800 + i * 24, 80 + r.nextInt(220)));
        }
        s.append("</svg>");
        return s.toString();
    }
}
