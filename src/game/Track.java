package game;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A recorded piece of music: a WAV file in {@code res/music}, played by {@link Music} in place of a written
 * {@link Song} (see {@link Song#recorded}). It loops from the top when it ends.
 *
 * <p>The file is read on a background thread the first time the track is wanted, so asking for it never stalls a
 * frame; until it's ready it plays as silence. It must be uncompressed PCM at 44.1 kHz, 16- or 24-bit, mono or stereo,
 * and is kept in memory as 16-bit stereo. A file that's missing or can't be read is reported once and stays silent.
 */
final class Track {
    final String file;
    /** A fixed level correction (linear), so the track sits as loud as the rest of the music; measured, see the sound tests. */
    final float gain;
    private volatile short[] data;              // interleaved left, right
    private boolean loading;

    Track(String file, double gainDb) {
        this.file = file;
        this.gain = (float) Dsp.lin(gainDb);
    }

    /** True if the file is in the game (the jar, or {@code res/} when run from source). */
    static boolean exists(String file) { return Track.class.getResource("/music/" + file) != null; }

    /** Starts reading the file in the background, if that hasn't started yet. */
    synchronized void preload() {
        if (loading) return;
        loading = true;
        Thread t = new Thread(() -> {
            try {
                data = read(file);
            } catch (IOException | RuntimeException e) {
                System.err.println("Could not load the music " + file + ": " + e.getMessage());
            }
        }, "music-" + file);
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    boolean ready() { return data != null; }

    /** Length in frames (0 until it's loaded). */
    int frames() {
        short[] d = data;
        return d == null ? 0 : d.length / 2;
    }

    float left(int frame) { return data[2 * frame] * (1f / 32768f); }

    float right(int frame) { return data[2 * frame + 1] * (1f / 32768f); }

    /** Reads a PCM WAV (16- or 24-bit, mono or stereo, 44.1 kHz) into 16-bit stereo, skipping any chunks it doesn't need. */
    private static short[] read(String file) throws IOException {
        byte[] bytes;
        try (InputStream in = Track.class.getResourceAsStream("/music/" + file)) {
            if (in == null) throw new IOException("not found");
            bytes = in.readAllBytes();
        }
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < 12 || b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157) throw new IOException("not a WAV file");   // "RIFF", "WAVE"
        int channels = 0, rate = 0, bits = 0, format = 0, dataAt = -1, dataLen = 0;
        for (int pos = 12; pos + 8 <= bytes.length; ) {
            int id = b.getInt(pos), len = b.getInt(pos + 4);
            if (id == 0x20746d66) {                                   // "fmt "
                format = b.getShort(pos + 8) & 0xFFFF;
                channels = b.getShort(pos + 10);
                rate = b.getInt(pos + 12);
                bits = b.getShort(pos + 22);
            } else if (id == 0x61746164) {                            // "data"
                dataAt = pos + 8;
                dataLen = Math.min(len, bytes.length - dataAt);
            }
            if (len < 0) break;
            pos += 8 + len + (len & 1);
        }
        if (dataAt < 0) throw new IOException("no audio in it");
        if ((format != 1 && format != 0xFFFE) || (bits != 16 && bits != 24) || (channels != 1 && channels != 2))
            throw new IOException("must be 16- or 24-bit PCM, mono or stereo (it's format " + format + ", " + bits + "-bit, " + channels + " channels)");
        if (rate != Dsp.SR) throw new IOException("must be " + Dsp.SR + " Hz (it's " + rate + " Hz)");

        int bytesPer = bits / 8, frames = dataLen / (bytesPer * channels);
        short[] out = new short[frames * 2];
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < 2; c++) {
                int at = dataAt + (f * channels + Math.min(c, channels - 1)) * bytesPer;
                out[2 * f + c] = bits == 16 ? b.getShort(at) : (short) (((bytes[at + 2] << 16) | ((bytes[at + 1] & 0xFF) << 8) | (bytes[at] & 0xFF)) >> 8);
            }
        }
        return out;
    }
}
