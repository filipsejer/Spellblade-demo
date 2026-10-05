package game;

import game.Instruments.Inst;
import java.util.ArrayList;
import java.util.List;

/**
 * A piece of music: a loop of bars, split into layers (pad, bass, melody, drums...). Every layer has a target volume
 * for each {@link Mood}, so the score can grow when a fight starts and swell again for a boss without ever
 * restarting or losing the beat.
 *
 * <p>Time is counted in ticks of a sixteenth note, 16 to a bar, 4/4 throughout.
 */
final class Song {
    /** How intense the music should be right now. */
    enum Mood { CALM, FIGHT, PEAK }

    record Note(int tick, int midi, int len, float vel) {}

    /** One part of the band. */
    static final class Layer {
        final String name;
        final Inst inst;
        final float pan, reverb, echo;
        final float[] gain;                 // by Mood ordinal
        /** A fixed level correction (linear) that balances this part against the others; measured, see the sound tests. */
        float trim = 1f;
        final List<Note> notes = new ArrayList<>();

        Layer(String name, Inst inst, float pan, float reverb, float echo, float calm, float fight, float peak) {
            this.name = name;
            this.inst = inst;
            this.pan = pan;
            this.reverb = reverb;
            this.echo = echo;
            this.gain = new float[]{calm, fight, peak};
        }

        Layer trimDb(double dB) {
            trim = (float) Dsp.lin(dB);
            return this;
        }

        Layer add(List<Note> more) {
            notes.addAll(more);
            return this;
        }
    }

    final String id;
    final double bpm;
    final int bars;
    final double reverbRoom, reverbDamp, echoBeats;
    final List<Layer> layers = new ArrayList<>();
    /** The chord of each bar, for analysis and tests. */
    final List<String> chords = new ArrayList<>();
    /** Seconds the song takes to fade in when it starts. */
    double fadeIn = 1.2;
    /** A recording played in place of the written parts (null for a written song); see {@link #recorded}. */
    Track track;

    Song(String id, double bpm, int bars, double reverbRoom, double reverbDamp, double echoBeats) {
        this.id = id;
        this.bpm = bpm;
        this.bars = bars;
        this.reverbRoom = reverbRoom;
        this.reverbDamp = reverbDamp;
        this.echoBeats = echoBeats;
    }

    /** A song that is a recording rather than written parts: it has no layers, and the mood doesn't change it. */
    static Song recorded(String id, Track track) {
        Song s = new Song(id, 120, 1, 0, 0, 0.5);
        s.track = track;
        track.preload();
        return s;
    }

    int ticks() { return bars * 16; }

    double seconds() { return bars * 4 * 60.0 / bpm; }

    Layer layer(String name) {
        for (Layer l : layers) if (l.name.equals(name)) return l;
        throw new IllegalArgumentException("no layer " + name);
    }

    Layer addLayer(Layer l) {
        layers.add(l);
        return l;
    }

    // ------------------------------------------------------------------ notation

    private static final int[] LETTER = {9, 11, 0, 2, 4, 5, 7};   // A B C D E F G as pitch classes

    /** "C4" = 60, "F#5", "Bb3". */
    static int midi(String name) {
        char c = name.charAt(0);
        int i = 1, pc = LETTER[c - 'A'];
        if (i < name.length() && name.charAt(i) == '#') { pc++; i++; }
        else if (i < name.length() && name.charAt(i) == 'b') { pc--; i++; }
        int octave = Integer.parseInt(name.substring(i));
        return 12 * (octave + 1) + pc;
    }

    /**
     * Reads a melody: notes as {@code E5:4} (name, length in sixteenths; a trailing {@code !} accents it) and rests as
     * {@code r:4}. Bars are separated by {@code |} and each must add up to exactly 16, so a slip in the score is
     * caught the moment the song is built.
     */
    static List<Note> melody(String text, int firstBar) {
        List<Note> out = new ArrayList<>();
        String[] bars = text.split("\\|");
        for (int b = 0; b < bars.length; b++) {
            int tick = 0;
            for (String tok : bars[b].trim().split("\\s+")) {
                if (tok.isEmpty()) continue;
                boolean accent = tok.endsWith("!");
                if (accent) tok = tok.substring(0, tok.length() - 1);
                String[] parts = tok.split(":");
                int len = Integer.parseInt(parts[1]);
                if (!parts[0].equals("r")) out.add(new Note((firstBar + b) * 16 + tick, midi(parts[0]), len, accent ? 1f : 0.82f));
                tick += len;
            }
            if (tick != 16) throw new IllegalStateException("bar " + (firstBar + b + 1) + " of \"" + text.trim() + "\" adds up to " + tick + " sixteenths, not 16");
        }
        return out;
    }

    /** A chord symbol: {@code G}, {@code Em}, {@code Am7}, {@code Cmaj7}, {@code B7}, {@code D/F#}. */
    record Chord(int root, int bass, int[] tones) {
        static Chord parse(String s) {
            String main = s, slash = null;
            int at = s.indexOf('/');
            if (at >= 0) { main = s.substring(0, at); slash = s.substring(at + 1); }
            int i = 1, root = LETTER[main.charAt(0) - 'A'];
            if (i < main.length() && main.charAt(i) == '#') { root++; i++; }
            else if (i < main.length() && main.charAt(i) == 'b') { root--; i++; }
            root = ((root % 12) + 12) % 12;
            int[] shape = switch (main.substring(i)) {
                case "" -> new int[]{0, 4, 7};
                case "m" -> new int[]{0, 3, 7};
                case "7" -> new int[]{0, 4, 7, 10};
                case "m7" -> new int[]{0, 3, 7, 10};
                case "maj7" -> new int[]{0, 4, 7, 11};
                default -> throw new IllegalArgumentException("chord quality in " + s);
            };
            int[] tones = new int[shape.length];
            for (int k = 0; k < shape.length; k++) tones[k] = (root + shape[k]) % 12;
            int bass = root;
            if (slash != null) bass = midi(slash + "4") % 12;
            return new Chord(root, bass, tones);
        }

        boolean has(int midi) {
            for (int t : tones) if (Math.floorMod(midi, 12) == t) return true;
            return false;
        }
    }

    /**
     * Pad voicings with good voice leading: for each chord, the closest set of notes to the previous chord, kept within
     * one octave and around the middle of the range, so the harmony glides instead of jumping.
     */
    static List<int[]> voicings(List<String> chords) {
        List<int[]> out = new ArrayList<>();
        int[] prev = null;
        for (String name : chords) {
            Chord c = Chord.parse(name);
            int n = c.tones.length;
            int[][] cand = new int[n][];
            for (int k = 0; k < n; k++) {
                List<Integer> opts = new ArrayList<>();
                for (int m = 52; m <= 71; m++) if (m % 12 == c.tones[k]) opts.add(m);
                cand[k] = opts.stream().mapToInt(Integer::intValue).toArray();
            }
            int[] best = null;
            double bestCost = 1e9;
            int[] pick = new int[n];
            int combos = 1;
            for (int[] a : cand) combos *= a.length;
            for (int code = 0; code < combos; code++) {
                int rem = code;
                for (int k = 0; k < n; k++) { pick[k] = cand[k][rem % cand[k].length]; rem /= cand[k].length; }
                int[] sorted = pick.clone();
                java.util.Arrays.sort(sorted);
                if (sorted[n - 1] - sorted[0] > 12) continue;
                double cost = 0;
                double mean = 0;
                for (int v : sorted) mean += v / (double) n;
                cost += Math.abs(mean - 61) * 0.5;
                if (prev != null) for (int k = 0; k < n; k++) cost += Math.abs(sorted[k] - prev[Math.min(k, prev.length - 1)]) * 1.0;
                if (cost < bestCost) { bestCost = cost; best = sorted; }
            }
            out.add(best);
            prev = best;
        }
        return out;
    }

    /** A pad: each bar holds its chord for the whole bar. */
    static List<Note> pad(List<String> chords, float vel) {
        List<Note> out = new ArrayList<>();
        List<int[]> v = voicings(chords);
        for (int bar = 0; bar < chords.size(); bar++) for (int m : v.get(bar)) out.add(new Note(bar * 16, m, 16, vel));
        return out;
    }

    /**
     * A bass line from a rhythm of {@code tick:kind:length} steps repeated in every bar (kind 0 = the chord's bass note,
     * 1 = its fifth, 2 = the octave above, 3 = the octave below).
     */
    static List<Note> bass(List<String> chords, String rhythm, float vel) {
        List<Note> out = new ArrayList<>();
        for (int bar = 0; bar < chords.size(); bar++) {
            Chord c = Chord.parse(chords.get(bar));
            int low = 36 + Math.floorMod(c.bass - 36, 12);          // C2..B2
            for (String tok : rhythm.trim().split("\\s+")) {
                String[] p = tok.split(":");
                int kind = Integer.parseInt(p[1]);
                int m = switch (kind) { case 1 -> low + 7; case 2 -> low + 12; case 3 -> low - 12; default -> low; };
                out.add(new Note(bar * 16 + Integer.parseInt(p[0]), m, Integer.parseInt(p[2]), Integer.parseInt(p[0]) % 8 == 0 ? vel : vel * 0.82f));
            }
        }
        return out;
    }

    /**
     * An arpeggio through each chord's notes: {@code steps} are indices into the ladder (the pad voicing followed by
     * the same notes an octave up), one per {@code stepTicks}, repeated to fill every bar. {@code octave} shifts it.
     */
    static List<Note> arp(List<String> chords, int[] steps, int stepTicks, int len, int octave, float vel) {
        List<Note> out = new ArrayList<>();
        List<int[]> v = voicings(chords);
        for (int bar = 0; bar < chords.size(); bar++) {
            int[] base = v.get(bar);
            int n = base.length;
            for (int s = 0; s < 16 / stepTicks; s++) {
                int idx = steps[s % steps.length];
                int m = base[idx % n] + 12 * (idx / n) + 12 * octave;
                out.add(new Note(bar * 16 + s * stepTicks, m, len, s % (8 / stepTicks) == 0 ? vel : vel * 0.78f));
            }
        }
        return out;
    }

    /** Ostinato: the chord's tones (root first) cycled in a rhythm, for choppy strings. {@code octave} shifts it. */
    static List<Note> chug(List<String> chords, int stepTicks, int len, int octave, float vel, String accents) {
        List<Note> out = new ArrayList<>();
        List<int[]> v = voicings(chords);
        for (int bar = 0; bar < chords.size(); bar++) {
            int[] base = v.get(bar);
            for (int s = 0; s < 16 / stepTicks; s++) {
                char a = accents.charAt(s % accents.length());
                if (a == '.') continue;
                int m = base[(a == 'X' || a == 'x') ? 0 : 1 + (s % Math.max(1, base.length - 1))] + 12 * octave;
                out.add(new Note(bar * 16 + s * stepTicks, m, len, a == 'X' ? 1f : 0.78f));
            }
        }
        return out;
    }

    /**
     * A drum pattern from a 16-step grid (one character per sixteenth), repeated for bars [from, to): {@code x} a hit,
     * {@code X} an accent, {@code o} a soft ghost note, {@code .} a rest.
     */
    static List<Note> drums(int drum, String grid, int fromBar, int toBar) {
        List<Note> out = new ArrayList<>();
        if (grid.length() != 16) throw new IllegalArgumentException("a drum grid has 16 steps: " + grid);
        for (int bar = fromBar; bar < toBar; bar++) {
            for (int s = 0; s < 16; s++) {
                char c = grid.charAt(s);
                if (c == '.') continue;
                out.add(new Note(bar * 16 + s, drum, 1, c == 'X' ? 1f : c == 'o' ? 0.45f : 0.8f));
            }
        }
        return out;
    }
}
