package game;

import game.Instruments.Voice;
import game.Song.Layer;
import game.Song.Mood;
import game.Song.Note;
import java.util.ArrayList;
import java.util.List;

/**
 * The live orchestra. It plays a {@link Song} in real time, note by note, on the instruments in {@link Instruments}.
 * The song's layers fade in and out as the {@link Mood} changes, so fights swell in without the tune stopping, and
 * switching to a different song is a crossfade. A recorded song ({@link Track}) is played back as it is, looping, and
 * crossfades the same way. It only ever runs on the audio thread (or, in tests, on the thread
 * that calls {@link #render}); the game talks to it through {@link AudioEngine}.
 */
final class Music {
    private static final int MAX_BLOCK = 1024;

    private SongPlayer current;
    private final List<SongPlayer> fading = new ArrayList<>();
    private Mood mood = Mood.CALM;
    private boolean paused;
    private double pauseAmount;                                   // 0 = normal, 1 = fully ducked and muffled
    private boolean hushed;
    private double hush;                                          // 0 = playing, 1 = silent and holding its place
    /** Seconds the music takes to fade out when hushed, and to come back. */
    static final double HUSH_OUT = 0.5, HUSH_IN = 0.9;
    private final Dsp.Biquad muffleL = new Dsp.Biquad(), muffleR = new Dsp.Biquad();
    private final Dsp.HighPass dcL = new Dsp.HighPass(20), dcR = new Dsp.HighPass(20);
    private final float[] playerL = new float[MAX_BLOCK], playerR = new float[MAX_BLOCK];

    /** Starts (or keeps) a song. {@code null} fades the music out. */
    void play(Song song, Mood mood, double fadeOutSeconds) {
        this.mood = mood;
        if (current != null && current.song == song) {
            current.setMood(mood);
            return;
        }
        if (current != null) {
            current.fadeTo(0, fadeOutSeconds);
            fading.add(current);
            current = null;
        }
        if (song != null) {
            current = new SongPlayer(song, mood);
            current.fadeTo(1, song.fadeIn);
        }
    }

    void setMood(Mood mood) {
        this.mood = mood;
        if (current != null) current.setMood(mood);
    }

    void setPaused(boolean paused) { this.paused = paused; }

    /** Hushed, the music fades to silence and stops where it is; lifted, it fades back in from there. */
    void setHushed(boolean hushed) { this.hushed = hushed; }

    boolean silent() { return hush >= 1; }

    Mood mood() { return mood; }

    /** True when a song is playing (or fading in). */
    boolean playing() { return current != null; }

    /** Adds the next {@code n} frames of music to the outputs. */
    void render(float[] l, float[] r, int n) {
        for (int done = 0; done < n; ) {
            int chunk = Math.min(MAX_BLOCK, n - done);
            java.util.Arrays.fill(playerL, 0, chunk, 0f);
            java.util.Arrays.fill(playerR, 0, chunk, 0f);
            if (current != null && hush < 1) current.render(playerL, playerR, chunk);   // fully hushed, the song holds its place
            for (int i = fading.size() - 1; i >= 0; i--) {
                SongPlayer p = fading.get(i);
                p.render(playerL, playerR, chunk);
                if (p.finished()) fading.remove(i);
            }
            for (int i = 0; i < chunk; i++) {                       // no DC offset or sub-bass rumble ever reaches the speakers
                playerL[i] = dcL.process(playerL[i]);
                playerR[i] = dcR.process(playerR[i]);
            }
            // pausing ducks the music and closes a low-pass over it, like a hand over your ears
            double target = paused ? 1 : 0;
            pauseAmount += (target - pauseAmount) * (1 - Math.pow(0.5, chunk / (SRD * 0.12)));
            if (pauseAmount > 0.002) {
                double fc = 20000 * Math.pow(650.0 / 20000, pauseAmount);
                muffleL.lowpass(fc, 0.7);
                muffleR.lowpass(fc, 0.7);
                float duck = (float) (1 - 0.45 * pauseAmount);
                for (int i = 0; i < chunk; i++) {
                    playerL[i] = muffleL.process(playerL[i]) * duck;
                    playerR[i] = muffleR.process(playerR[i]) * duck;
                }
            } else {
                muffleL.reset();
                muffleR.reset();
            }
            double hush0 = hush;
            hush = hushed ? Math.min(1, hush + chunk / (SRD * HUSH_OUT)) : Math.max(0, hush - chunk / (SRD * HUSH_IN));
            if (hush0 > 0 || hush > 0) {                            // eased, sample by sample, so the fade never clicks
                for (int i = 0; i < chunk; i++) {
                    double h = 1 - (hush0 + (hush - hush0) * i / chunk);
                    float gain = (float) (h * h * (3 - 2 * h));
                    playerL[i] *= gain;
                    playerR[i] *= gain;
                }
            }
            for (int i = 0; i < chunk; i++) {
                l[done + i] += playerL[i];
                r[done + i] += playerR[i];
            }
            done += chunk;
        }
    }

    private static final double SRD = Dsp.SR;

    // ------------------------------------------------------------------ one song being played

    private static final class Active {
        final Voice voice;
        final long offTick;
        final float pl, pr;
        boolean released;

        Active(Voice voice, long offTick, float pl, float pr) {
            this.voice = voice;
            this.offTick = offTick;
            this.pl = pl;
            this.pr = pr;
        }
    }

    private static final class LayerState {
        final Layer layer;
        final Note[][] byTick;
        final List<Active> voices = new ArrayList<>();
        final float[] gains = new float[MAX_BLOCK];
        double gain, target;
        final int index;

        LayerState(Layer layer, int ticks, int index) {
            this.layer = layer;
            this.index = index;
            List<Note>[] lists = newLists(ticks);
            for (Note n : layer.notes) lists[n.tick() % ticks].add(n);
            byTick = new Note[ticks][];
            for (int t = 0; t < ticks; t++) byTick[t] = lists[t].toArray(new Note[0]);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static List<Note>[] newLists(int n) {
            List<Note>[] a = new List[n];
            for (int i = 0; i < n; i++) a[i] = new ArrayList<>();
            return a;
        }

        boolean silent() { return target < 0.001 && gain < 0.001 && voices.isEmpty(); }
    }

    private static final class SongPlayer {
        final Song song;
        private final List<LayerState> layers = new ArrayList<>();
        private final Dsp.Reverb reverb;
        private final Dsp.Echo echo;
        private final Dsp.HighPass revCut = new Dsp.HighPass(160), echoCut = new Dsp.HighPass(160);   // sends keep the mud out of the tails
        private final float[] dryL = new float[MAX_BLOCK], dryR = new float[MAX_BLOCK], revIn = new float[MAX_BLOCK],
            echoIn = new float[MAX_BLOCK], wetL = new float[MAX_BLOCK], wetR = new float[MAX_BLOCK];
        private final double tickLen;
        private long samplePos, tickCount;
        private int trackPos;
        private double nextTickAt;
        private double fade, fadeTarget, fadeStep;
        private Mood mood;

        SongPlayer(Song song, Mood mood) {
            this.song = song;
            this.mood = mood;
            this.tickLen = Dsp.SR * 60.0 / song.bpm / 4;
            this.reverb = new Dsp.Reverb(song.reverbRoom, song.reverbDamp);
            this.echo = new Dsp.Echo(song.echoBeats * 60.0 / song.bpm, 0.38);
            int i = 0;
            for (Layer l : song.layers) {
                LayerState ls = new LayerState(l, song.ticks(), i++);
                ls.target = l.gain[mood.ordinal()] * l.trim;
                ls.gain = ls.target;                       // a song starts with its parts already at the right level; only later changes glide
                layers.add(ls);
            }
        }

        void setMood(Mood m) {
            mood = m;
            for (LayerState ls : layers) ls.target = ls.layer.gain[m.ordinal()] * ls.layer.trim;
        }

        void fadeTo(double target, double seconds) {
            fadeTarget = target;
            fadeStep = seconds <= 0 ? 1 : 1.0 / (seconds * Dsp.SR);
            if (seconds <= 0) fade = target;
        }

        boolean finished() { return fadeTarget == 0 && fade <= 0; }

        void render(float[] outL, float[] outR, int n) {
            if (song.track != null) {
                renderTrack(outL, outR, n);
                return;
            }
            int done = 0;
            while (done < n) {
                if (samplePos >= nextTickAt) {
                    tick();
                    tickCount++;
                    nextTickAt = tickCount * tickLen;
                    continue;
                }
                int chunk = (int) Math.min(n - done, Math.max(1, Math.ceil(nextTickAt - samplePos)));
                chunk(outL, outR, done, chunk);
                samplePos += chunk;
                done += chunk;
            }
        }

        /** A recording: played from the top as soon as it has loaded (silent until then), and round again when it ends. */
        private void renderTrack(float[] outL, float[] outR, int n) {
            Track t = song.track;
            int frames = t.frames();
            for (int i = 0; i < n; i++) {
                float f = nextFade();
                if (frames == 0) continue;
                f *= t.gain;
                outL[i] += t.left(trackPos) * f;
                outR[i] += t.right(trackPos) * f;
                if (++trackPos >= frames) trackPos = 0;
            }
        }

        /** Moves the fade on by one sample; the eased gain to play it at. */
        private float nextFade() {
            if (fade < fadeTarget) fade = Math.min(fadeTarget, fade + fadeStep);
            else if (fade > fadeTarget) fade = Math.max(fadeTarget, fade - fadeStep);
            return (float) (fade * fade * (3 - 2 * fade));
        }

        /** Everything that happens on one sixteenth: notes end, notes begin. */
        private void tick() {
            int loopTick = (int) (tickCount % song.ticks());
            long loop = tickCount / song.ticks();
            for (LayerState ls : layers) {
                for (Active a : ls.voices) {
                    if (!a.released && a.offTick <= tickCount) {
                        a.voice.release();
                        a.released = true;
                    }
                }
                if (ls.target < 0.001 && ls.gain < 0.001) continue;      // this part isn't playing right now
                for (Note note : ls.byTick[loopTick]) {
                    long seed = note.tick() * 131L + note.midi() * 7L + ls.index * 1013L + loop * 17L;
                    Voice v = Instruments.make(ls.layer.inst, note.midi(), note.vel(), seed);
                    double wobble = ((seed * 2654435761L >>> 8) % 1000) / 1000.0 - 0.5;           // a touch of stereo width
                    double pan = Dsp.clamp(ls.layer.pan + wobble * 0.22, -1, 1);
                    double a = (pan + 1) * Math.PI / 4;
                    ls.voices.add(new Active(v, tickCount + note.len(), (float) Math.cos(a), (float) Math.sin(a)));
                }
            }
        }

        private void chunk(float[] outL, float[] outR, int off, int n) {
            java.util.Arrays.fill(dryL, 0, n, 0f);
            java.util.Arrays.fill(dryR, 0, n, 0f);
            java.util.Arrays.fill(revIn, 0, n, 0f);
            java.util.Arrays.fill(echoIn, 0, n, 0f);
            for (LayerState ls : layers) {
                if (ls.silent()) continue;
                double coef = ls.target > ls.gain ? Dsp.tau(0.28) : Dsp.tau(0.42);
                for (int i = 0; i < n; i++) {
                    ls.gain += (ls.target - ls.gain) * coef;
                    ls.gains[i] = (float) ls.gain;
                }
                float send = ls.layer.reverb, sendEcho = ls.layer.echo;
                for (int k = ls.voices.size() - 1; k >= 0; k--) {
                    Active a = ls.voices.get(k);
                    for (int i = 0; i < n; i++) {
                        float s = a.voice.next() * ls.gains[i];
                        dryL[i] += s * a.pl;
                        dryR[i] += s * a.pr;
                        revIn[i] += s * send;
                        echoIn[i] += s * sendEcho;
                    }
                    if (a.voice.done()) ls.voices.remove(k);
                }
            }
            for (int i = 0; i < n; i++) {
                revIn[i] = revCut.process(revIn[i]);
                echoIn[i] = echoCut.process(echoIn[i]);
            }
            java.util.Arrays.fill(wetL, 0, n, 0f);
            java.util.Arrays.fill(wetR, 0, n, 0f);
            reverb.process(revIn, wetL, wetR, n, 3.0f);
            echo.process(echoIn, wetL, wetR, n, 0.9f);
            for (int i = 0; i < n; i++) {
                float f = nextFade();
                outL[off + i] += (dryL[i] + wetL[i]) * f;
                outR[off + i] += (dryR[i] + wetR[i]) * f;
            }
        }
    }
}
