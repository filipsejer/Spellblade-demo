package game;

import game.Instruments.Inst;
import game.Song.Layer;
import game.Song.Note;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The game's pieces of music, written out bar by bar.
 *
 * <ul>
 *   <li><b>Forest</b> (G major, 96 bpm): a folk tune on flute over a harp and a warm pad, for wandering the forest.</li>
 *   <li><b>Forest battle</b> (D minor, 152 bpm): the fights. A driving piano ostinato and staccato strings in 3+3+2,
 *       a soaring string melody, a heroic brass strain in the relative major, and orchestral drums.</li>
 *   <li><b>City</b> (A minor, 100 bpm): a lonely electric piano over a synth pad and echoing arpeggios; the fight adds a
 *       four-on-the-floor beat, a driving bass and a lead.</li>
 *   <li><b>City battle</b> (A minor, 148 bpm): the city's fights. Electric piano comping, a walking bass and jazzy
 *       stabs under a string melody that grows out of the city's own tune, a heroic brass strain in the relative
 *       major, and the city's drum machine with a swung hat.</li>
 *   <li><b>Stormcliff battle</b> (D minor, 160 bpm): the laboratory's fights. A toccata of synth sixteenths and organ
 *       stabs over a growling bass, the theremin carrying a melody that grows out of the laboratory's own tune, a brass
 *       strain over the storm, the drum machine, and war drums rolling like thunder at the end of each phrase.</li>
 *   <li><b>Forest boss</b> (E minor, 120 bpm) and <b>city boss</b> (D minor, 128 bpm): brass and lead melodies over war
 *       drums; below half health the boss music gets a second, more frantic layer of parts.</li>
 * </ul>
 *
 * Every melody note is a note of the chord underneath it on the strong beats, and every stinger and interface blip in
 * the game uses only the notes C D E G A, which belong to all four keys, so nothing ever clashes with the music.
 */
final class Songs {
    private Songs() {}

    enum Tune { FOREST, FOREST_BATTLE, CITY, CITY_BATTLE, FOREST_BOSS, CITY_BOSS, LAB, LAB_BOSS, LAB_BATTLE, MAIN_MENU, BATTLE }

    /**
     * The recordings (in {@code res/music}): the main menu's theme, and the battle theme every fight plays (in place of
     * the worlds' written ones). Each has a level correction in dB, so it's as loud as the rest (measured, see the sound tests).
     */
    static final String MAIN_MENU_FILE = "main_menu.wav", BATTLE_FILE = "battle.wav";
    private static final double MAIN_MENU_TRIM = -5.5, BATTLE_TRIM = -5.5;

    private static final Map<Tune, Song> CACHE = new EnumMap<>(Tune.class);

    static synchronized Song get(Tune t) {
        return CACHE.computeIfAbsent(t, k -> switch (k) {
            case FOREST -> forest();
            case FOREST_BATTLE -> forestBattle();
            case CITY -> city();
            case CITY_BATTLE -> cityBattle();
            case FOREST_BOSS -> forestBoss();
            case CITY_BOSS -> cityBoss();
            case LAB -> lab();
            case LAB_BOSS -> labBoss();
            case LAB_BATTLE -> labBattle();
            case MAIN_MENU -> Song.recorded("main-menu", new Track(MAIN_MENU_FILE, MAIN_MENU_TRIM));
            case BATTLE -> Song.recorded("battle", new Track(BATTLE_FILE, BATTLE_TRIM));
        });
    }

    private static List<Note> shift(List<Note> notes, int semitones) {
        List<Note> out = new ArrayList<>();
        for (Note n : notes) out.add(new Note(n.tick(), n.midi() + semitones, n.len(), n.vel()));
        return out;
    }

    /** Adds a part. {@code trimDb} balances it against the others (found by measuring it alone, see the sound tests). */
    private static Layer layer(Song s, String name, Inst inst, double pan, double reverb, double echo, double calm, double fight, double peak, List<Note> notes) {
        Layer l = s.addLayer(new Layer(name, inst, (float) pan, (float) reverb, (float) echo, (float) calm, (float) fight, (float) peak)).add(notes);
        l.trimDb(TRIM.getOrDefault(s.id + "/" + name, 0.0) + SONG_TRIM.getOrDefault(s.id, 0.0));
        return l;
    }

    /** Whole-song level corrections in dB, so that (by A-weighted loudness) every piece of music is equally loud. */
    private static final Map<String, Double> SONG_TRIM = Map.of("forest", 1.5, "forest-battle", -2.0, "city", -1.6, "city-battle", 0.8, "forest-boss", 0.0, "city-boss", 0.2, "lab", 1.6, "lab-boss", 0.0, "lab-battle", 1.0);

    /** Level corrections in dB per part, measured so every part sits where a mix engineer would put it. */
    private static final Map<String, Double> TRIM = new java.util.HashMap<>();

    static {
        TRIM.put("forest/pad", -12.0);
        TRIM.put("forest/harp", -14.0);
        TRIM.put("forest/flute", -8.5);
        TRIM.put("forest/bass", -7.5);
        TRIM.put("forest/drive", -9.5);
        TRIM.put("forest/fiddle", -5.0);
        TRIM.put("forest/descant", 2.0);
        TRIM.put("forest/drums", -12.5);
        TRIM.put("forest/shaker", -2.5);
        TRIM.put("forest-battle/pad", -8.5);
        TRIM.put("forest-battle/piano", -3.5);
        TRIM.put("forest-battle/left", 0.0);
        TRIM.put("forest-battle/bass", -3.5);
        TRIM.put("forest-battle/chop", -2.5);
        TRIM.put("forest-battle/strings", -4.5);
        TRIM.put("forest-battle/brass", -4.0);
        TRIM.put("forest-battle/horns", -9.0);
        TRIM.put("forest-battle/pulse", -5.0);
        TRIM.put("forest-battle/drums", -12.0);
        TRIM.put("forest-battle/hats", -5.0);
        TRIM.put("forest-battle/open", -10.0);
        TRIM.put("city/pad", -12.0);
        TRIM.put("city/arp", -5.0);
        TRIM.put("city/arp16", -8.5);
        TRIM.put("city/piano", -0.5);
        TRIM.put("city/lead", -3.5);
        TRIM.put("city/bass", -12.0);
        TRIM.put("city/drive", -11.5);
        TRIM.put("city/kickSoft", -9.0);
        TRIM.put("city/hatSoft", 1.5);
        TRIM.put("city/beat", -11.5);
        TRIM.put("city/hats", -2.0);
        TRIM.put("city-battle/pad", -10.0);
        TRIM.put("city-battle/keys", -8.5);
        TRIM.put("city-battle/bass", -10.0);
        TRIM.put("city-battle/stab", -7.0);
        TRIM.put("city-battle/strings", -6.5);
        TRIM.put("city-battle/brass", -6.5);
        TRIM.put("city-battle/horns", -12.0);
        TRIM.put("city-battle/arp", -7.5);
        TRIM.put("city-battle/pulse", -10.0);
        TRIM.put("city-battle/beat", -12.0);
        TRIM.put("city-battle/hats", -6.5);
        TRIM.put("city-battle/open", -11.0);
        TRIM.put("forest-boss/pad", -10.5);
        TRIM.put("forest-boss/bass", -8.0);
        TRIM.put("forest-boss/brass", -3.5);
        TRIM.put("forest-boss/strings", -4.5);
        TRIM.put("forest-boss/war", -16.5);
        TRIM.put("forest-boss/hats", -3.0);
        TRIM.put("forest-boss/harp", -17.0);
        TRIM.put("forest-boss/descant", -8.0);
        TRIM.put("city-boss/pad", -11.0);
        TRIM.put("city-boss/bass", -8.5);
        TRIM.put("city-boss/lead", -3.0);
        TRIM.put("city-boss/arp", -8.5);
        TRIM.put("city-boss/beat", -12.5);
        TRIM.put("city-boss/hats", -3.5);
        TRIM.put("city-boss/offbeat", -5.5);
        TRIM.put("city-boss/stab", -1.0);
        TRIM.put("city-boss/lead2", -3.5);
        TRIM.put("lab/pad", -12.5);
        TRIM.put("lab/arp", -5.5);
        TRIM.put("lab/arp16", -9.0);
        TRIM.put("lab/theremin", -8.5);
        TRIM.put("lab/lead", -3.5);
        TRIM.put("lab/bass", -11.0);
        TRIM.put("lab/drive", -12.0);
        TRIM.put("lab/tick", -2.5);
        TRIM.put("lab/beat", -12.0);
        TRIM.put("lab/hats", -2.5);
        TRIM.put("lab-battle/pad", -11.0);
        TRIM.put("lab-battle/toccata", -8.0);
        TRIM.put("lab-battle/bass", -9.5);
        TRIM.put("lab-battle/organ", -6.0);
        TRIM.put("lab-battle/theremin", -11.0);
        TRIM.put("lab-battle/brass", -6.0);
        TRIM.put("lab-battle/strings", -9.0);
        TRIM.put("lab-battle/pulse", -10.0);
        TRIM.put("lab-battle/beat", -12.0);
        TRIM.put("lab-battle/hats", -6.5);
        TRIM.put("lab-battle/thunder", -15.0);
        TRIM.put("lab-boss/pad", -10.0);
        TRIM.put("lab-boss/bass", -8.5);
        TRIM.put("lab-boss/organ", -1.0);
        TRIM.put("lab-boss/stab", -5.0);
        TRIM.put("lab-boss/beat", -13.0);
        TRIM.put("lab-boss/hats", -4.0);
        TRIM.put("lab-boss/arp", -9.0);
        TRIM.put("lab-boss/lead2", -4.5);
        TRIM.put("lab-boss/offbeat", -6.0);
    }


    // ------------------------------------------------------------------ forest

    private static Song forest() {
        Song s = new Song("forest", 96, 16, 0.78, 0.5, 0.75);
        List<String> chords = List.of("G", "D/F#", "Em", "Bm", "C", "G/B", "Am7", "D",
                                      "Em", "C", "G", "D", "Em", "Cmaj7", "D", "D");
        s.chords.addAll(chords);
        List<Note> tune = Song.melody("""
            B4:4 D5:4 G5:6 F#5:2 | A5:4 F#5:4 D5:8 | E5:4 G5:4 B5:6 A5:2 | F#5:4 D5:4 B4:8 |
            C5:4 E5:4 G5:6 E5:2 | D5:4 B4:4 G4:4 B4:4 | C5:4 E5:4 A4:4 C5:4 | A4:4 D5:4 F#5:8 |
            G5:2 F#5:2 E5:4 B4:4 E5:4 | G5:2 E5:2 C5:4 E5:4 G5:4 | B4:2 D5:2 G5:4 B5:4 A5:2 G5:2 | F#5:4 A5:4 F#5:4 D5:4 |
            E5:4 G5:2 A5:2 B5:4 G5:4 | E5:2 G5:2 C6:4 B5:2 G5:2 E5:4 | D5:2 F#5:2 A5:4 F#5:2 D5:2 A4:4 | F#5:4 D5:4 A4:8
            """, 0);
        layer(s, "pad", Inst.PAD_WARM, 0, 0.55, 0, 0.85, 0.55, 0.55, Song.pad(chords, 0.8f));
        layer(s, "harp", Inst.HARP, -0.3, 0.5, 0.22, 0.85, 0.5, 0.5, Song.arp(chords, new int[]{0, 1, 2, 3, 4, 3, 2, 1}, 2, 4, 0, 0.8f));
        layer(s, "flute", Inst.FLUTE, 0.12, 0.5, 0.2, 0.95, 0.8, 0.8, tune);
        layer(s, "bass", Inst.BASS_ROUND, 0, 0.05, 0, 0.75, 0, 0, Song.bass(chords, "0:0:6 8:1:6", 0.9f));
        layer(s, "drive", Inst.BASS_ROUND, 0, 0.05, 0, 0, 1.0, 1.0, Song.bass(chords, "0:0:2 2:0:2 4:0:2 6:1:2 8:0:2 10:0:2 12:0:2 14:1:2", 0.9f));
        layer(s, "fiddle", Inst.FIDDLE, 0.32, 0.25, 0.1, 0, 0.8, 0.8, Song.chug(chords, 2, 1, 0, 0.75f, "XoxoXoxo"));
        layer(s, "descant", Inst.FIDDLE, -0.2, 0.4, 0, 0, 0.55, 0.55, shift(tune, -12));
        Layer drums = layer(s, "drums", Inst.DRUMS_FOLK, 0, 0.12, 0, 0, 1.0, 1.0, new ArrayList<>());
        drums.add(Song.drums(36, "X.....x.X.x.....", 0, 16));
        drums.add(Song.drums(38, "....X.......X...", 0, 16));
        drums.add(Song.drums(76, "..x...x...x...x.", 0, 16));
        for (int bar : new int[]{3, 7, 11, 15}) {
            drums.add(Song.drums(45, "............x.x.", bar, bar + 1));
            drums.add(Song.drums(41, "..............xx", bar, bar + 1));
        }
        layer(s, "shaker", Inst.DRUMS_FOLK, 0.2, 0.1, 0, 0.3, 0.85, 0.85, Song.drums(70, "x.o.x.o.x.o.x.o.", 0, 16));
        return s;
    }

    // ------------------------------------------------------------------ forest battle

    /**
     * The forest's fights (D minor, 152 bpm, 32 bars). Calm, it's just the piano's ostinato over a pad and a distant
     * drum, waiting; in the fight proper the strings chop out a 3+3+2 rhythm under a soaring string melody, the brass
     * takes the tune for a heroic strain in the relative major, and the drums drive it on. At the peak (taking a nest
     * down) horns swell under everything and the cymbals open up.
     */
    private static Song forestBattle() {
        Song s = new Song("forest-battle", 152, 32, 0.6, 0.5, 0.75);
        s.fadeIn = 0.6;
        List<String> chords = List.of("Dm", "Dm", "Bb", "C", "Dm", "Dm", "Gm", "A",
                                      "Dm", "Bb", "F", "C", "Gm", "Dm", "Eb", "A",
                                      "Bb", "C", "Am", "Dm", "Gm", "C", "F", "A",
                                      "Bb", "Bb", "C", "C", "Dm", "Bb", "Gm", "A7");
        s.chords.addAll(chords);
        List<Note> strings = new ArrayList<>(Song.melody("""
            A4:6 D5:2 F5:4 A5:4 | A5:2 G5:2 F5:2 E5:2 D5:8 | D5:4 F5:4 Bb5:6 A5:2 | G5:4 E5:4 C5:4 E5:2 F5:2 |
            A5:6 F5:2 D6:6 C6:2 | A5:4 F5:4 D5:4 A4:4 | Bb4:4 D5:4 G5:6 F5:2 | E5:6 F5:2 E5:4 C#5:4 |
            D6:6 C6:2 A5:4 F5:4 | F5:2 G5:2 F5:2 D5:2 Bb4:8 | C5:4 F5:4 A5:6 G5:2 | G5:4 E5:4 C6:8 |
            Bb5:6 A5:2 G5:4 D5:4 | F5:4 A5:4 D6:4 A5:4 | G5:4 Bb5:4 Eb6:6 D6:2 | C#6:8 A5:4 E5:4
            """, 0));
        strings.addAll(Song.melody("""
            D6:8 F6:4 D6:4 | Bb5:4 F5:2 G5:2 F5:8 | E5:4 G5:4 C6:8 | C6:2 D6:2 E6:4 G6:4 E6:4 |
            F6:6 E6:2 D6:4 A5:4 | D6:6 C6:2 Bb5:4 F5:4 | G5:4 Bb5:4 D6:4 Bb5:4 | C#6:4 A5:2 G5:2 E5:4 C#5:4
            """, 24));
        List<Note> brass = Song.melody("""
            F4:4 Bb4:4 D5:6 C5:2 | E5:3 E5:1 G5:4 C6:6 Bb5:2 | A5:6 E5:2 C5:4 E5:4 | F5:4 D5:2 E5:2 A4:8 |
            G4:4 Bb4:4 D5:6 F5:2 | E5:4 G5:4 C6:6 D6:2 | C6:4 A5:4 F5:4 A5:4 | A5:6 G5:2 E5:4 C#5:4
            """, 16);
        layer(s, "pad", Inst.PAD_WARM, 0, 0.6, 0, 0.7, 0.55, 0.55, Song.pad(chords, 0.75f));
        layer(s, "piano", Inst.PIANO, -0.15, 0.35, 0.05, 0.95, 0.85, 0.85,
            Song.arp(chords, new int[]{0, 1, 2, 3, 1, 2, 3, 4, 2, 3, 4, 5, 4, 3, 2, 1}, 1, 2, 0, 0.8f));
        layer(s, "left", Inst.PIANO, -0.1, 0.3, 0, 0.85, 0.55, 0.55, Song.bass(chords, "0:0:3 3:2:3 6:0:2 8:0:3 11:2:3 14:0:2", 0.9f));
        layer(s, "bass", Inst.BASS_ROUND, 0, 0.05, 0, 0, 1.0, 1.0, Song.bass(chords, "0:0:2 3:0:2 6:0:2 8:0:2 11:0:2 14:1:2", 0.9f));
        layer(s, "chop", Inst.STAB, 0.3, 0.25, 0, 0, 0.8, 0.9, Song.chug(chords, 1, 1, 0, 0.8f, "Xo.xo.Xo.xo.Xo.x"));
        layer(s, "strings", Inst.STRINGS, 0.15, 0.5, 0.1, 0, 1.0, 1.0, strings);
        layer(s, "brass", Inst.BRASS, -0.1, 0.4, 0.05, 0, 1.0, 1.0, brass);
        layer(s, "horns", Inst.BRASS, -0.25, 0.5, 0, 0, 0, 0.6, Song.pad(chords, 0.55f));
        layer(s, "pulse", Inst.DRUMS_WAR, 0, 0.25, 0, 0.55, 0, 0, Song.drums(36, "X.......x.......", 0, 32));
        Layer drums = layer(s, "drums", Inst.DRUMS_WAR, 0, 0.2, 0, 0, 1.0, 1.0, new ArrayList<>());
        for (int bar = 0; bar < 32; bar++) {
            boolean phraseEnd = bar % 8 == 7;
            drums.add(Song.drums(36, "X..x..x.X..x..x.", bar, bar + 1));
            drums.add(Song.drums(38, phraseEnd ? "....X...xoxoXxXX" : "....X.......X...", bar, bar + 1));
            if (phraseEnd) drums.add(Song.drums(45, "........x.x.....", bar, bar + 1));
            if (phraseEnd) drums.add(Song.drums(41, "............x.x.", bar, bar + 1));
        }
        layer(s, "hats", Inst.DRUMS_WAR, 0.25, 0.1, 0, 0, 0.5, 0.85, Song.drums(42, "x.x.x.x.x.x.x.x.", 0, 32));
        layer(s, "open", Inst.DRUMS_WAR, -0.25, 0.2, 0, 0, 0, 0.7, Song.drums(46, "..x...x...x...x.", 0, 32));
        return s;
    }

    // ------------------------------------------------------------------ city

    private static Song city() {
        Song s = new Song("city", 100, 16, 0.6, 0.6, 0.75);
        List<String> chords = List.of("Am", "F", "C", "G", "Am", "F", "Dm", "E",
                                      "Dm", "G", "C", "Am", "Dm", "G", "E", "E7");
        s.chords.addAll(chords);
        List<Note> tune = Song.melody("""
            E5:4 r:2 C5:2 A4:4 C5:4 | C5:4 A4:2 C5:2 F5:6 E5:2 | E5:4 G5:4 C6:4 G5:4 | B4:4 D5:4 G5:4 D5:4 |
            E5:4 C5:2 E5:2 A5:6 G5:2 | A5:4 F5:4 C5:4 A4:4 | D5:4 F5:4 A5:4 F5:4 | B4:4 G#4:4 B4:4 E5:4 |
            A4:4 D5:4 F5:4 A5:4 | G5:4 D5:4 B4:4 D5:4 | E5:4 G5:4 C6:8 | C6:4 B5:2 A5:2 E5:8 |
            F5:4 A5:4 D6:4 A5:4 | B5:4 G5:4 D5:4 G5:4 | G#4:4 B4:4 E5:4 B4:4 | E5:8 D5:4 B4:4
            """, 0);
        layer(s, "pad", Inst.PAD_SYNTH, 0, 0.6, 0, 0.85, 0.65, 0.65, Song.pad(chords, 0.8f));
        layer(s, "arp", Inst.ARP_SYNTH, -0.25, 0.3, 0.45, 0.6, 0, 0, Song.arp(chords, new int[]{0, 1, 2, 3, 2, 1, 2, 1}, 2, 2, 0, 0.75f));
        layer(s, "arp16", Inst.ARP_SYNTH, -0.25, 0.3, 0.5, 0, 0.7, 0.7, Song.arp(chords, new int[]{0, 1, 2, 3, 2, 1, 2, 4}, 1, 1, 0, 0.75f));
        layer(s, "piano", Inst.EPIANO, 0.1, 0.5, 0.3, 0.95, 0.8, 0.8, tune);
        layer(s, "lead", Inst.LEAD_SYNTH, 0.2, 0.35, 0.3, 0, 0.5, 0.5, tune);
        layer(s, "bass", Inst.BASS_SYNTH, 0, 0.03, 0, 0.9, 0, 0, Song.bass(chords, "0:0:6 8:0:6 14:1:2", 0.9f));
        layer(s, "drive", Inst.BASS_SYNTH, 0, 0.03, 0, 0, 1.0, 1.0, Song.bass(chords, "0:0:2 2:0:2 4:0:2 6:2:2 8:0:2 10:0:2 12:0:2 14:1:2", 0.9f));
        layer(s, "kickSoft", Inst.DRUMS_ELECTRO, 0, 0.05, 0, 0.5, 0, 0, Song.drums(36, "x.......x.......", 0, 16));
        layer(s, "hatSoft", Inst.DRUMS_ELECTRO, 0.25, 0.1, 0.1, 0.4, 0, 0, Song.drums(42, "..x...x...x...x.", 0, 16));
        Layer beat = layer(s, "beat", Inst.DRUMS_ELECTRO, 0, 0.12, 0, 0, 1.0, 1.0, new ArrayList<>());
        beat.add(Song.drums(36, "X...X...X...X...", 0, 16));
        beat.add(Song.drums(38, "....X.......X...", 0, 16));
        beat.add(Song.drums(39, "....x.......x...", 0, 16));
        layer(s, "hats", Inst.DRUMS_ELECTRO, 0.25, 0.1, 0.1, 0, 0.8, 0.8, Song.drums(42, "x.xox.xox.xox.xo", 0, 16));
        return s;
    }

    private static Song cityBattle() {
        Song s = new Song("city-battle", 148, 32, 0.6, 0.55, 0.75);
        s.fadeIn = 0.6;
        List<String> chords = List.of("Am", "Am", "F", "G", "Am", "Am", "Dm", "E",
                                      "F", "G", "Em", "Am", "Dm", "E", "Am", "E7",
                                      "F", "G", "C", "Am", "Dm", "G", "C", "E",
                                      "F", "G", "Em", "Am", "Dm", "Bb", "E", "E7");
        s.chords.addAll(chords);
        List<Note> strings = Song.melody("""
            E5:3 E5:3 A5:2 C6:4 A5:2 B5:2 | E5:6 C5:2 A4:8 | F5:3 F5:3 A5:2 C6:4 A5:4 | B5:4 D6:4 G5:8 |
            A5:3 G5:3 E5:2 C5:4 E5:4 | A5:6 B5:2 C6:8 | D6:4 A5:2 C6:2 F5:4 A5:4 | G#5:8 B5:4 E6:4 |
            C6:6 A5:2 F5:4 A5:4 | B5:6 G5:2 D5:4 G5:4 | E5:4 G5:4 B5:6 A5:2 | A5:8 E5:4 C5:4 |
            D5:4 F5:4 A5:4 D6:4 | E6:4 B5:2 D6:2 G#5:4 E5:4 | A5:6 C6:2 E6:8 | D6:4 B5:4 G#5:4 E5:4
            """, 0);
        List<Note> brass = Song.melody("""
            A4:4 C5:4 F5:6 E5:2 | D5:4 G5:4 B5:6 A5:2 | G5:4 E5:4 C6:8 | C6:4 A5:2 B5:2 E5:8 |
            F5:4 A5:4 D6:6 C6:2 | B5:4 G5:4 D5:4 G5:4 | E5:4 G5:4 C6:4 E6:4 | E6:6 D6:2 B5:4 G#5:4 |
            A5:6 C6:2 F6:8 | D6:4 B5:4 G5:4 B5:4 | B5:6 A5:2 G5:4 E5:4 | A5:4 C6:4 E6:8 |
            F6:6 E6:2 D6:4 A5:4 | D6:4 Bb5:4 F5:4 Bb5:4 | G#5:6 B5:2 E6:8 | D6:4 B5:4 G#5:4 E5:4
            """, 16);
        layer(s, "pad", Inst.PAD_SYNTH, 0, 0.6, 0, 0.6, 0.5, 0.5, Song.pad(chords, 0.75f));
        layer(s, "keys", Inst.EPIANO, -0.15, 0.4, 0.2, 0.95, 0.8, 0.8, Song.chug(chords, 1, 2, 0, 0.8f, "X..x..x...X.x..."));
        layer(s, "bass", Inst.BASS_ROUND, 0, 0.05, 0, 0.6, 1.0, 1.0, Song.bass(chords, "0:0:3 4:1:3 8:2:3 12:1:2 14:0:2", 0.9f));
        layer(s, "stab", Inst.STAB, 0.3, 0.25, 0.1, 0, 0.75, 0.85, Song.chug(chords, 1, 1, 1, 0.8f, "..X...X..X....X."));
        layer(s, "strings", Inst.STRINGS, 0.15, 0.5, 0.1, 0, 1.0, 1.0, strings);
        layer(s, "brass", Inst.BRASS, -0.1, 0.4, 0.05, 0, 1.0, 1.0, brass);
        layer(s, "horns", Inst.BRASS, -0.25, 0.5, 0, 0, 0, 0.6, Song.pad(chords, 0.55f));
        layer(s, "arp", Inst.ARP_SYNTH, -0.3, 0.3, 0.45, 0, 0, 0.6, Song.arp(chords, new int[]{0, 1, 2, 3, 4, 3, 2, 1}, 1, 1, 0, 0.7f));
        layer(s, "pulse", Inst.DRUMS_ELECTRO, 0, 0.1, 0, 0.5, 0, 0, Song.drums(36, "X.......X.......", 0, 32));
        Layer beat = layer(s, "beat", Inst.DRUMS_ELECTRO, 0, 0.12, 0, 0, 1.0, 1.0, new ArrayList<>());
        for (int bar = 0; bar < 32; bar++) {
            boolean phraseEnd = bar % 8 == 7;
            beat.add(Song.drums(36, "X.....X...X.....", bar, bar + 1));
            beat.add(Song.drums(38, phraseEnd ? "....X.......XxXX" : "....X.......X...", bar, bar + 1));
            beat.add(Song.drums(39, "....x.......x...", bar, bar + 1));
        }
        layer(s, "hats", Inst.DRUMS_ELECTRO, 0.25, 0.1, 0.1, 0, 0.75, 0.85, Song.drums(42, "x.xxx.xxx.xxx.xx", 0, 32));
        layer(s, "open", Inst.DRUMS_ELECTRO, -0.2, 0.15, 0, 0, 0, 0.7, Song.drums(46, "..x...x...x...x.", 0, 32));
        return s;
    }

    // ------------------------------------------------------------------ bosses

    private static Song forestBoss() {
        Song s = new Song("forest-boss", 120, 16, 0.55, 0.55, 0.75);
        s.fadeIn = 0.25;
        List<String> chords = List.of("Em", "Em", "C", "D", "Em", "Em", "Am", "B7",
                                      "Em", "C", "D", "Em", "Am", "C", "D", "B7");
        s.chords.addAll(chords);
        List<Note> tune = Song.melody("""
            E5:4 B4:4 G4:4 E4:4 | G4:2 A4:2 B4:4 E5:8 | C5:4 E5:4 G5:4 E5:4 | F#5:4 A5:4 F#5:4 D5:4 |
            E5:6 G5:2 B5:8 | G5:4 B5:2 A5:2 G5:4 E5:4 | C5:4 E5:4 A5:8 | B5:4 A5:4 F#5:4 D#5:4 |
            E5:2 G5:2 B5:4 G5:2 E5:2 B4:4 | C5:2 E5:2 G5:4 E5:2 C5:2 G4:4 | D5:2 F#5:2 A5:4 F#5:2 D5:2 A4:4 | B4:2 E5:2 G5:4 B5:8 |
            E5:2 A5:2 C6:4 A5:4 E5:4 | G5:4 E5:4 C5:4 E5:4 | A5:4 F#5:4 D5:4 F#5:4 | D#5:4 F#5:4 A5:4 B5:4
            """, 0);
        layer(s, "pad", Inst.PAD_DARK, 0, 0.6, 0, 0.5, 0.75, 0.85, Song.pad(chords, 0.8f));
        layer(s, "bass", Inst.BASS_GROWL, 0, 0.03, 0, 0.7, 1.0, 1.0, Song.bass(chords, "0:0:2 2:0:2 4:0:2 6:0:2 8:0:2 10:0:2 12:0:2 14:1:2", 0.9f));
        layer(s, "brass", Inst.BRASS, 0.1, 0.4, 0.1, 0, 1.0, 1.0, tune);
        layer(s, "strings", Inst.STAB, 0.3, 0.25, 0, 0, 0.75, 0.85, Song.chug(chords, 2, 1, 0, 0.8f, "XoxoXoxo"));
        Layer war = layer(s, "war", Inst.DRUMS_WAR, 0, 0.2, 0, 0, 1.0, 1.0, new ArrayList<>());
        war.add(Song.drums(36, "X.....x.X.x...x.", 0, 16));
        war.add(Song.drums(38, "....X.......X...", 0, 16));
        war.add(Song.drums(41, "..x.......x.....", 0, 16));
        for (int bar : new int[]{3, 7, 11, 15}) {
            war.add(Song.drums(45, "............x.x.", bar, bar + 1));
            war.add(Song.drums(48, "..............xx", bar, bar + 1));
        }
        layer(s, "hats", Inst.DRUMS_WAR, 0.25, 0.1, 0, 0, 0.35, 0.8, Song.drums(42, "x.x.x.x.x.x.x.x.", 0, 16));
        layer(s, "harp", Inst.HARP, -0.3, 0.45, 0.2, 0, 0, 0.65, Song.arp(chords, new int[]{0, 1, 2, 3, 4, 3, 2, 1}, 1, 3, 0, 0.8f));
        layer(s, "descant", Inst.FLUTE, 0.25, 0.5, 0.2, 0, 0, 0.42, shift(tune, 12));
        return s;
    }

    private static Song cityBoss() {
        Song s = new Song("city-boss", 128, 16, 0.5, 0.6, 0.75);
        s.fadeIn = 0.25;
        List<String> chords = List.of("Dm", "Dm", "Bb", "C", "Dm", "Dm", "Gm", "A",
                                      "Bb", "C", "Dm", "F", "Bb", "C", "A", "A");
        s.chords.addAll(chords);
        List<Note> tune = Song.melody("""
            D5:3 D5:3 F5:2 A5:4 F5:4 | A5:4 G5:4 F5:4 D5:4 | Bb4:3 Bb4:3 D5:2 F5:4 D5:4 | C5:3 C5:3 E5:2 G5:4 E5:4 |
            D5:3 D5:3 F5:2 A5:4 C6:4 | A5:4 F5:4 D5:4 A4:4 | G5:4 Bb5:4 D6:4 Bb5:4 | C#6:4 A5:4 E5:4 C#5:4 |
            F5:4 D5:4 Bb4:4 D5:4 | G5:4 E5:4 C5:4 E5:4 | A5:4 F5:4 D5:4 F5:4 | A5:4 C6:4 F5:4 A5:4 |
            Bb5:4 D6:4 F5:4 Bb5:4 | C6:4 G5:4 E5:4 G5:4 | E5:4 A5:4 C#6:4 A5:4 | A5:4 G5:4 E5:4 C#5:4
            """, 0);
        layer(s, "pad", Inst.PAD_SYNTH, 0, 0.5, 0, 0.6, 0.75, 0.85, Song.pad(chords, 0.8f));
        layer(s, "bass", Inst.BASS_GROWL, 0, 0.03, 0, 0.7, 1.0, 1.0, Song.bass(chords, "0:0:2 2:0:1 3:0:1 4:0:2 6:0:2 8:0:2 10:0:1 11:0:1 12:0:2 14:2:2", 0.9f));
        layer(s, "lead", Inst.LEAD_SYNTH, 0.1, 0.35, 0.3, 0, 1.0, 1.0, tune);
        layer(s, "arp", Inst.ARP_SYNTH, -0.3, 0.3, 0.5, 0, 0.6, 0.8, Song.arp(chords, new int[]{0, 1, 2, 3, 2, 1, 2, 4}, 1, 1, 0, 0.75f));
        Layer beat = layer(s, "beat", Inst.DRUMS_ELECTRO, 0, 0.1, 0, 0, 1.0, 1.0, new ArrayList<>());
        beat.add(Song.drums(36, "X...X...X...X...", 0, 16));
        beat.add(Song.drums(38, "....X.......X...", 0, 16));
        beat.add(Song.drums(39, "....x.......x...", 0, 16));
        layer(s, "hats", Inst.DRUMS_ELECTRO, 0.25, 0.1, 0.1, 0, 0.7, 0.85, Song.drums(42, "x.xox.xox.xox.xo", 0, 16));
        layer(s, "offbeat", Inst.DRUMS_ELECTRO, -0.2, 0.15, 0, 0, 0, 0.7, Song.drums(46, "..x...x...x...x.", 0, 16));
        layer(s, "stab", Inst.STAB, 0.35, 0.25, 0.25, 0, 0, 0.75, Song.chug(chords, 2, 1, 0, 0.8f, ".x.o.x.o.x.o.x.o"));
        layer(s, "lead2", Inst.LEAD_SYNTH, 0.3, 0.4, 0.3, 0, 0, 0.42, shift(tune, 12));
        return s;
    }

    // ------------------------------------------------------------------ laboratory

    /** The laboratory (D dorian, 108 bpm): a theremin over a synth pad, a ticking clock and odd little arpeggios. */
    private static Song lab() {
        Song s = new Song("lab", 108, 16, 0.6, 0.55, 0.75);
        List<String> chords = List.of("Dm", "Dm", "G", "G", "F", "C", "Am", "Am",
                                      "Dm", "G", "C", "F", "Dm", "G", "Am", "Am");
        s.chords.addAll(chords);
        List<Note> tune = Song.melody("""
            D5:6 F5:2 A5:4 F5:4 | F5:2 E5:2 D5:4 A5:8 | G5:4 D5:4 B4:4 D5:4 | B4:2 D5:2 G5:4 D5:4 G5:4 |
            A5:4 C6:4 A5:4 F5:4 | G5:4 E5:4 C5:4 E5:4 | E5:4 A5:4 C6:4 A5:4 | A5:6 G5:2 E5:4 C5:4 |
            D5:4 F5:4 A5:4 D6:4 | D6:4 B5:4 G5:4 B5:4 | C6:4 G5:4 E5:4 G5:4 | A5:4 F5:4 C5:4 F5:4 |
            D5:2 E5:2 F5:4 A5:4 F5:4 | G5:4 B5:4 D6:4 B5:4 | C6:4 A5:4 E5:4 A5:4 | E5:8 C5:4 A4:4
            """, 0);
        layer(s, "pad", Inst.PAD_SYNTH, 0, 0.6, 0, 0.85, 0.65, 0.65, Song.pad(chords, 0.8f));
        layer(s, "arp", Inst.ARP_SYNTH, -0.25, 0.3, 0.45, 0.55, 0, 0, Song.arp(chords, new int[]{0, 2, 1, 3, 2, 1, 3, 2}, 2, 2, 0, 0.75f));
        layer(s, "arp16", Inst.ARP_SYNTH, -0.25, 0.3, 0.5, 0, 0.7, 0.7, Song.arp(chords, new int[]{0, 1, 2, 3, 2, 4, 3, 1}, 1, 1, 0, 0.75f));
        layer(s, "theremin", Inst.THEREMIN, 0.1, 0.55, 0.3, 0.95, 0.8, 0.8, tune);
        layer(s, "lead", Inst.LEAD_SYNTH, 0.2, 0.35, 0.3, 0, 0.5, 0.5, tune);
        layer(s, "bass", Inst.BASS_SYNTH, 0, 0.03, 0, 0.9, 0, 0, Song.bass(chords, "0:0:4 6:0:2 8:0:4 14:1:2", 0.9f));
        layer(s, "drive", Inst.BASS_SYNTH, 0, 0.03, 0, 0, 1.0, 1.0, Song.bass(chords, "0:0:2 2:0:2 4:0:2 6:2:2 8:0:2 10:0:2 12:0:2 14:1:2", 0.9f));
        layer(s, "tick", Inst.DRUMS_FOLK, 0.3, 0.1, 0.15, 0.6, 0.35, 0.35, Song.drums(76, "x...o...x...o...", 0, 16));
        Layer beat = layer(s, "beat", Inst.DRUMS_ELECTRO, 0, 0.12, 0, 0, 1.0, 1.0, new ArrayList<>());
        beat.add(Song.drums(36, "X...X...X...X...", 0, 16));
        beat.add(Song.drums(38, "....X.......X...", 0, 16));
        beat.add(Song.drums(39, "....x.......x...", 0, 16));
        layer(s, "hats", Inst.DRUMS_ELECTRO, 0.25, 0.1, 0.1, 0, 0.8, 0.8, Song.drums(42, "x.xox.xox.xox.xo", 0, 16));
        return s;
    }

    /**
     * Stormcliff's fights (D minor, 160 bpm): a synth toccata in sixteenths and organ stabs over a growling bass; the
     * theremin's melody grows out of the laboratory's own tune (D, F, A, up to the octave), then brass takes a strain
     * over the storm; war drums roll at the end of every phrase like thunder.
     */
    private static Song labBattle() {
        Song s = new Song("lab-battle", 160, 32, 0.6, 0.55, 0.75);
        s.fadeIn = 0.6;
        List<String> chords = List.of("Dm", "Dm", "Bb", "C", "Dm", "Dm", "Gm", "A",
                                      "Dm", "F", "C", "Gm", "Bb", "C", "A", "A7",
                                      "Bb", "C", "F", "Dm", "Gm", "A", "Dm", "A7",
                                      "Gm", "Dm", "Bb", "F", "Gm", "Bb", "A", "A7");
        s.chords.addAll(chords);
        List<Note> theremin = Song.melody("""
            D5:3 F5:3 A5:2 D6:4 A5:4 | A5:2 C6:2 A5:4 F5:8 | D5:3 F5:3 Bb5:2 D6:4 Bb5:4 | C6:4 G5:4 E5:4 G5:4 |
            A5:3 G5:3 F5:2 D5:4 F5:4 | A5:6 D6:2 F6:8 | G5:3 Bb5:3 D6:2 G6:4 D6:4 | E6:4 C#6:4 A5:4 E5:4 |
            D6:3 C6:3 A5:2 F5:4 A5:4 | C6:3 A5:3 F5:2 A5:4 C6:4 | G5:3 E5:3 C5:2 E5:4 G5:4 | Bb5:4 D6:4 G5:8 |
            F5:3 Bb5:3 D6:2 F6:4 D6:4 | E6:4 C6:4 G5:4 E5:4 | A5:3 C#6:3 E6:2 A6:8 | G6:4 E6:4 C#6:4 A5:4
            """, 0);
        List<Note> brass = Song.melody("""
            F5:4 Bb5:4 D6:6 C6:2 | E6:4 G5:4 C6:8 | A5:4 C6:4 F6:6 E6:2 | D6:4 A5:4 F5:8 |
            G5:3 Bb5:3 D6:2 G6:4 D6:2 Bb5:2 | E6:4 C#6:4 A5:8 | F6:6 E6:2 D6:8 | C#6:4 E6:4 G6:4 E6:4 |
            D6:3 Bb5:3 G5:2 Bb5:4 D6:4 | F6:3 D6:3 A5:2 D6:4 F6:4 | F6:4 D6:4 Bb5:8 | C6:4 A5:4 F5:4 A5:4 |
            G5:2 A5:2 Bb5:4 D6:4 G6:4 | F6:6 D6:2 Bb5:8 | A5:3 C#6:3 E6:2 A6:8 | G6:4 E6:4 C#6:4 A5:4
            """, 16);
        layer(s, "pad", Inst.PAD_DARK, 0, 0.6, 0, 0.7, 0.55, 0.55, Song.pad(chords, 0.8f));
        layer(s, "toccata", Inst.ARP_SYNTH, -0.25, 0.3, 0.4, 0.95, 0.75, 0.75, Song.arp(chords, new int[]{0, 2, 1, 2, 3, 2, 1, 2}, 1, 1, 0, 0.75f));
        layer(s, "bass", Inst.BASS_GROWL, 0, 0.03, 0, 0.5, 1.0, 1.0, Song.bass(chords, "0:0:2 2:0:2 4:0:2 6:2:2 8:0:2 10:0:2 12:1:2 14:2:2", 0.9f));
        layer(s, "organ", Inst.ORGAN, 0.2, 0.4, 0.1, 0, 0.7, 0.85, Song.chug(chords, 1, 2, 0, 0.8f, "X..X..X.X..X..X."));
        layer(s, "theremin", Inst.THEREMIN, 0.1, 0.5, 0.25, 0, 1.0, 1.0, theremin);
        layer(s, "brass", Inst.BRASS, -0.1, 0.4, 0.05, 0, 1.0, 1.0, brass);
        layer(s, "strings", Inst.STRINGS, 0.25, 0.5, 0, 0, 0, 0.6, Song.pad(chords, 0.6f));
        layer(s, "pulse", Inst.DRUMS_ELECTRO, 0, 0.1, 0, 0.55, 0, 0, Song.drums(36, "X.......X.......", 0, 32));
        Layer beat = layer(s, "beat", Inst.DRUMS_ELECTRO, 0, 0.12, 0, 0, 1.0, 1.0, new ArrayList<>());
        for (int bar = 0; bar < 32; bar++) {
            boolean phraseEnd = bar % 8 == 7;
            beat.add(Song.drums(36, "X..X..X...X..X..", bar, bar + 1));
            beat.add(Song.drums(38, phraseEnd ? "....X.......XXXX" : "....X.......X...", bar, bar + 1));
            beat.add(Song.drums(39, "....x.......x..x", bar, bar + 1));
        }
        layer(s, "hats", Inst.DRUMS_ELECTRO, 0.25, 0.1, 0.1, 0, 0.75, 0.85, Song.drums(42, "xxXxxxXxxxXxxxXx", 0, 32));
        Layer thunder = layer(s, "thunder", Inst.DRUMS_WAR, -0.15, 0.4, 0, 0, 0.6, 1.0, new ArrayList<>());
        for (int bar = 7; bar < 32; bar += 8) thunder.add(Song.drums(41, "........X.X.XXXX", bar, bar + 1));   // a roll of toms, like thunder, into each phrase
        for (int bar = 0; bar < 32; bar += 8) thunder.add(Song.drums(43, "X...............", bar, bar + 1));
        return s;
    }

    /** The mad scientist (A harmonic minor, 144 bpm): a frantic organ toccata over growling bass and an electronic beat. */
    private static Song labBoss() {
        Song s = new Song("lab-boss", 144, 16, 0.55, 0.55, 0.75);
        s.fadeIn = 0.25;
        List<String> chords = List.of("Am", "Am", "F", "E", "Am", "Dm", "E", "E",
                                      "Dm", "Am", "F", "E", "Dm", "Am", "E", "E");
        s.chords.addAll(chords);
        List<Note> tune = Song.melody("""
            A5:2 C6:2 E6:2 C6:2 A5:4 E5:4 | A5:2 B5:2 C6:4 E6:4 C6:2 B5:2 | C6:4 A5:4 F5:4 A5:4 | G#5:2 B5:2 E6:4 B5:4 G#5:4 |
            E6:2 C6:2 A5:2 C6:2 E6:8 | D6:2 A5:2 F5:2 A5:2 D6:4 A5:4 | B5:4 G#5:4 E5:4 G#5:4 | B5:2 D6:2 C6:2 B5:2 G#5:8 |
            F5:2 A5:2 D6:4 A5:4 F5:4 | E5:2 A5:2 C6:4 A5:4 E5:4 | C6:2 A5:2 F5:4 A5:4 C6:4 | E5:2 G#5:2 B5:4 E6:4 B5:4 |
            D6:4 C6:2 A5:2 F5:4 A5:4 | C6:2 B5:2 A5:4 C6:4 E6:4 | E6:4 B5:4 G#5:4 B5:4 | G#5:2 A5:2 B5:4 E6:8
            """, 0);
        layer(s, "pad", Inst.PAD_DARK, 0, 0.6, 0, 0.5, 0.75, 0.85, Song.pad(chords, 0.8f));
        layer(s, "bass", Inst.BASS_GROWL, 0, 0.03, 0, 0.7, 1.0, 1.0, Song.bass(chords, "0:0:2 2:0:2 4:0:2 6:0:2 8:0:2 10:0:2 12:0:2 14:1:2", 0.9f));
        layer(s, "organ", Inst.ORGAN, 0.1, 0.45, 0.1, 0, 1.0, 1.0, tune);
        layer(s, "stab", Inst.STAB, 0.3, 0.25, 0.2, 0, 0.75, 0.85, Song.chug(chords, 2, 1, 0, 0.8f, "XoxoXoxo"));
        Layer beat = layer(s, "beat", Inst.DRUMS_ELECTRO, 0, 0.1, 0, 0, 1.0, 1.0, new ArrayList<>());
        beat.add(Song.drums(36, "X...X...X...X...", 0, 16));
        beat.add(Song.drums(38, "....X.......X...", 0, 16));
        beat.add(Song.drums(39, "....x.......x...", 0, 16));
        layer(s, "hats", Inst.DRUMS_ELECTRO, 0.25, 0.1, 0.1, 0, 0.7, 0.85, Song.drums(42, "x.xox.xox.xox.xo", 0, 16));
        layer(s, "arp", Inst.ARP_SYNTH, -0.3, 0.3, 0.5, 0, 0, 0.75, Song.arp(chords, new int[]{0, 1, 2, 3, 2, 1, 2, 4}, 1, 1, 0, 0.75f));
        layer(s, "lead2", Inst.LEAD_SYNTH, 0.3, 0.4, 0.3, 0, 0, 0.42, shift(tune, -12));
        layer(s, "offbeat", Inst.DRUMS_ELECTRO, -0.2, 0.15, 0, 0, 0, 0.7, Song.drums(46, "..x...x...x...x.", 0, 16));
        return s;
    }
}
