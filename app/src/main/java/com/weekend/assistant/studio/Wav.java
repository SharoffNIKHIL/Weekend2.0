package com.weekend.assistant.studio;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Minimal PCM WAV toolkit: 16-bit mono at a fixed rate. Enough for narration, pauses and the background pad. */
public final class Wav {

    public static final int RATE = 24_000;

    private Wav() {}

    /** Reads 16-bit PCM WAV (mono or stereo, any rate); returns mono samples resampled to {@link #RATE}. */
    public static short[] read(byte[] wav) {
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (wav.length < 44 || b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157) { // "RIFF" … "WAVE"
            throw new IllegalArgumentException("not a WAV file");
        }
        int channels = 1;
        int rate = RATE;
        int bits = 16;
        int pos = 12;
        while (pos + 8 <= wav.length) {
            int id = b.getInt(pos);
            int size = b.getInt(pos + 4);
            if (id == 0x20746d66) { // "fmt "
                channels = b.getShort(pos + 10);
                rate = b.getInt(pos + 12);
                bits = b.getShort(pos + 22);
            } else if (id == 0x61746164) { // "data"
                if (bits != 16) {
                    throw new IllegalArgumentException("only 16-bit PCM WAV is supported");
                }
                int len = Math.min(size, wav.length - pos - 8);
                int frames = len / (2 * channels);
                short[] mono = new short[frames];
                for (int i = 0; i < frames; i++) {
                    int sum = 0;
                    for (int c = 0; c < channels; c++) {
                        sum += b.getShort(pos + 8 + (i * channels + c) * 2);
                    }
                    mono[i] = (short) (sum / channels);
                }
                return rate == RATE ? mono : resample(mono, rate);
            }
            pos += 8 + size + (size & 1);
        }
        throw new IllegalArgumentException("WAV has no data chunk");
    }

    static short[] resample(short[] in, int from) {
        int n = (int) ((long) in.length * RATE / from);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double src = (double) i * from / RATE;
            int a = (int) src;
            int c = Math.min(in.length - 1, a + 1);
            double f = src - a;
            out[i] = (short) Math.round(in[Math.min(a, in.length - 1)] * (1 - f) + in[c] * f);
        }
        return out;
    }

    public static byte[] write(short[] samples) {
        ByteBuffer b = ByteBuffer.allocate(44 + samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x46464952).putInt(36 + samples.length * 2).putInt(0x45564157);
        b.putInt(0x20746d66).putInt(16).putShort((short) 1).putShort((short) 1).putInt(RATE).putInt(RATE * 2)
                .putShort((short) 2).putShort((short) 16);
        b.putInt(0x61746164).putInt(samples.length * 2);
        for (short s : samples) {
            b.putShort(s);
        }
        return b.array();
    }

    public static double seconds(short[] samples) {
        return samples.length / (double) RATE;
    }

    public static short[] silence(double seconds) {
        return new short[(int) Math.round(seconds * RATE)];
    }

    /** Joins clips, each placed at its start time (seconds); gaps are silence. */
    public static short[] place(java.util.List<short[]> clips, java.util.List<Double> starts, double total) {
        short[] out = new short[(int) Math.round(total * RATE)];
        for (int i = 0; i < clips.size(); i++) {
            int at = (int) Math.round(starts.get(i) * RATE);
            short[] c = clips.get(i);
            System.arraycopy(c, 0, out, Math.min(at, out.length), Math.max(0, Math.min(c.length, out.length - at)));
        }
        return out;
    }

    /**
     * A calm, royalty-free background pad made here from sine waves (no samples, nothing licensed): a slow
     * I–vi–IV–V progression with soft attack and release, quiet enough to sit under a voice.
     */
    public static short[] pad(double seconds) {
        double[][] chords = {{220.00, 277.18, 329.63}, {185.00, 220.00, 277.18}, {146.83, 185.00, 220.00}, {164.81, 207.65, 246.94}};
        int n = (int) Math.round(seconds * RATE);
        short[] out = new short[n];
        double chordLen = 4.0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double[] ch = chords[(int) (t / chordLen) % chords.length];
            double local = t % chordLen;
            double env = Math.min(1, local / 0.8) * Math.min(1, (chordLen - local) / 0.8);
            double v = 0;
            for (double f : ch) {
                v += Math.sin(2 * Math.PI * f * t) + 0.25 * Math.sin(2 * Math.PI * f * 2 * t);
            }
            double fade = Math.min(1, t / 1.5) * Math.min(1, (seconds - t) / 1.5);
            out[i] = (short) (v / (ch.length * 1.25) * env * fade * 0.22 * Short.MAX_VALUE);
        }
        return out;
    }

    static byte[] toBytes(short[] s) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(write(s));
        return o.toByteArray();
    }
}
