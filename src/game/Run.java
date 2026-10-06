package game;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * One fight: a {@link Challenge} on its battlefield ({@link Battlefield}), in the style of Survivor.io and Risk of Rain.
 * You start with only your sword (plus your gear and {@link Mastery} ranks). Every enemy drops an XP gem, and each
 * level-up offers a choice of {@link Perk}s; all of that is gone when the fight ends. The horde never stops coming,
 * and it gets tougher the longer you take (the danger clock).
 *
 * <p>The goal: destroy the Blight's nests, spread across the map (each one spits out guards when you come near); in
 * the city's substation, switch its relays back on and hold the ground round each one while it powers up
 * ({@link Relay}); at Stormcliff, hunt down the escaped specimens (they bolt when hurt, shedding more monsters), see
 * Copper safely across the map, or keep the stasis engine standing while it charges (both a {@link Ward}, which some
 * of the horde goes for instead of you). Stormcliff's fights also have lightning: marked spots on the ground that the
 * storm strikes a moment later, hurting you and flattening any monster standing there.
 * With that done, the challenge's guardian arrives inside a ring you can't leave, if it has one.
 * Then a chest and the way home. Along the way: elites with chests, crates with pickups, and caches you can open with the gold
 * you've picked up. Gold, items, and (for a win) the challenge's reward of skill points are yours to keep.
 * {@link World} runs the fight itself; this class is the director on top.
 */
final class Run {
    /** Seconds between elites, and between swarms. */
    static final double ELITE_EVERY = 80, SWARM_EVERY = 115;
    static final double BOSS_WARNING = 4;
    static final double RING_RADIUS = 600;
    static final int MAX_ENEMIES = 110;
    static final int CRATES = 30;
    /** How close a nest notices you (and starts sending guards). */
    static final double NEST_RANGE = 760;

    /** How a fight ended. */
    enum Outcome { VICTORY, DEFEAT, RETREAT }

    final Challenge challenge;
    final Random rng;
    /** How many times it had been cleared before (each one makes it more dangerous). */
    final int loops;
    /** Seconds into the fight. */
    double time;
    /** Gold picked up during the fight (not counting the reward). */
    int gold;
    /** Items found this fight: yours to keep when it ends, however it ends. */
    final List<Item> loot = new ArrayList<>();
    /** Items out of the chests opened since the last TREASURE screen: it shows them, so you see what you got. */
    final List<Item> unseen = new ArrayList<>();
    final List<Pickup> pickups = new ArrayList<>();

    /** The goal's count: nests, or relays in a relay fight (see {@link Challenge#goalWord}). */
    int nestsTotal, nestsLeft, elitesKilled, cachesOpened;
    /** A relay fight's relays. */
    final List<Relay> relays = new ArrayList<>();
    /** What you protect in an escort or a defence (null otherwise). */
    Ward ward;
    /** Seconds until the storm next strikes (Stormcliff's fights), and until Copper's cutting next throws sparks. */
    private double stormTimer = 5, sparkTimer, chillTimer;
    boolean bossSpawned, bossDead;
    double bossWarning;
    boolean ringActive;
    double ringX, ringY;
    private double spawnTimer, crateTimer, retuneTimer, eliteTimer = ELITE_EVERY, swarmTimer = SWARM_EVERY;
    private double xpCarry;

    // level-up / chest choices
    int pendingLevels, pendingChests;
    /**
     * Levels gained once the way home is open: with no fighting left there's nothing to choose, so each pays
     * {@link #LATE_LEVEL_GOLD} when the fight ends instead.
     */
    int lateLevels;
    static final int LATE_LEVEL_GOLD = 30;
    List<Perk.Choice> choices = List.of();
    int choiceCursor;
    String choiceTitle = "";
    int rerolls = 1;
    int cards = 3;
    /** Seconds before the cards on screen can be taken: a key already on its way down when they appeared can't pick one. */
    double choiceArm;
    /** How long the cards ignore their keys after appearing. */
    static final double CHOICE_ARM_TIME = 0.3;
    /** The keys that take card 1, 2, 3, 4 (the number row, or the numpad). */
    private static final int[][] CARD_KEYS = {{KeyEvent.VK_1, KeyEvent.VK_NUMPAD1}, {KeyEvent.VK_2, KeyEvent.VK_NUMPAD2},
        {KeyEvent.VK_3, KeyEvent.VK_NUMPAD3}, {KeyEvent.VK_4, KeyEvent.VK_NUMPAD4}};
    boolean reviveAvailable, reviveUsed;

    // the end
    boolean over;
    Outcome outcome;
    int rewardGold, rewardSkillPoints;
    /** Gold for the levels gained after the guardian fell (see {@link #lateLevels}), paid at the end. */
    int lateGold;
    boolean firstClear;

    private Run(Challenge c, long seed, int loops) {
        this.challenge = c;
        this.rng = new Random(seed);
        this.loops = loops;
    }

    // ------------------------------------------------------------------ starting

    /** A fight on the world's freshly made battlefield ({@link World#level}), with whatever the hero wears and has mastered. */
    static Run start(World w, Challenge c) {
        Adventure a = w.adventure;
        Run r = new Run(c, System.nanoTime(), a.clears(c));
        w.player = r.makePlayer(w, w.level);
        Player p = w.player;
        w.camX = p.x;
        w.camY = p.y;
        w.kills = 0;
        w.time = 0;
        w.fade = 1;
        r.spawnTimer = 2;
        r.retune(w);
        for (Util.Vec v : w.level.nestSpots) {
            switch (c.goal) {
                case RELAYS -> r.relays.add(new Relay(v.x(), v.y()));
                case HUNT -> {                                              // a specimen, dormant until something comes near
                    Enemy sp = new Enemy(Enemy.Type.BRUTE, v.x(), v.y(), r.hpMult(p, Enemy.Type.BRUTE) * SPECIMEN_HP, r.dmgMult(p) * 1.3, w.rng);
                    sp.specimen = true;
                    sp.spawnIn = 0;
                    w.enemies.add(sp);
                }
                default -> {
                    Enemy nest = new Enemy(Enemy.Type.NEST, v.x(), v.y(), r.hpMult(p, Enemy.Type.NEST), 1, w.rng);
                    nest.spawnIn = 0;
                    w.enemies.add(nest);
                }
            }
            r.nestsTotal++;
        }
        if (c.goal == Challenge.Goal.ESCORT) r.ward = Ward.robot(w.level.route, c.nests, ROBOT_HP * (1 + 0.15 * r.loops));
        if (c.goal == Challenge.Goal.DEFEND) r.ward = Ward.engine(w.level.route.get(0).x(), w.level.route.get(0).y(), ENGINE_HP * (1 + 0.15 * r.loops));
        if (r.ward != null) r.nestsTotal = 1;                              // (one goal: getting him there, or charging it)
        r.nestsLeft = r.nestsTotal;
        for (Util.Vec v : w.level.cacheSpots) r.pickups.add(new Pickup(Pickup.Kind.CACHE, v.x(), v.y(), 0));
        r.priceCaches();
        w.banner = c.title;
        w.bannerTimer = 3;
        w.notice = switch (c.goal) {
            case RELAYS -> "SWITCH ON THE " + r.nestsTotal + " RELAYS";
            case HUNT -> "HUNT DOWN THE " + r.nestsTotal + " SPECIMENS";
            case ESCORT -> "GET COPPER TO THE JUNCTION";
            case DEFEND -> "KEEP THE ENGINE STANDING";
            default -> "DESTROY THE " + r.nestsTotal + " NESTS";
        };
        w.noticeHint = (r.loops > 0 ? "Loop " + (r.loops + 1) + ": the Blight is stronger than last time.   " : "")
            + "Hold ENTER to swing your sword. The longer you take, the worse it gets.";
        w.noticeTimer = 6;
        r.pendingLevels = a.rank(Mastery.HEAD_START);
        return r;
    }

    /** A specimen's health, in brutes; Copper's and the engine's. */
    static final double SPECIMEN_HP = 9, ROBOT_HP = 900, ENGINE_HP = 1500;
    /** How much tougher Doctor Morrow is than the other guardians (each of his two stages). */
    static final double MORROW_HP = 3.5;
    /** The share of the walkers that go for the ward instead of you, and how hard they hit it. */
    static final double WARD_SHARE = 0.42, WARD_DAMAGE = 0.85;

    /** A fresh fighter: just the sword, plus whatever the gear and the masteries give. */
    private Player makePlayer(World w, Level level) {
        Player p = new Player(level.spawnX, level.spawnY);
        p.rollUnlocked = false;
        p.critChance = 0.05;
        for (Item it : w.profile.worn()) {
            for (Item.Stat s : it.stats.keySet()) applyStat(p, s, it.value(s));
            if (it.unique == null) continue;
            switch (it.unique) {
                case REROLL -> rerolls += 2;
                case REVIVE -> reviveAvailable = true;
                case FOURTH_CARD -> cards = 4;
                case WAVE_START -> Perk.CRESCENT_WAVE.take(p);
                case COMBO_START -> Perk.COMBO.take(p);
                case ROLL_START -> { if (p.perk[Perk.ROLL.ordinal()] == 0) Perk.ROLL.take(p); }
            }
        }
        Adventure a = w.adventure;
        Mastery.applyAll(p, a.mastery);
        if (a.rank(Mastery.TUMBLER) > 0 && p.perk[Perk.ROLL.ordinal()] == 0) Perk.ROLL.take(p);
        if (a.rank(Mastery.SECOND_WIND) > 0) reviveAvailable = true;
        rerolls += a.rank(Mastery.REROLLS);
        p.hp = p.maxHp;
        return p;
    }

    static void applyStat(Player p, Item.Stat s, double v) {
        switch (s) {
            case MELEE_DMG -> p.meleeMult += v / 100;
            case SPELL_DMG -> p.spellPower += v / 100;
            case MAX_HP -> p.maxHp += v;
            case ARMOR -> p.armor = Math.min(0.6, p.armor + v / 100);
            case ATTACK_SPEED -> p.attackSpeed += v / 100;
            case MOVE_SPEED -> p.moveSpeed *= 1 + v / 100;
            case CRIT -> p.critChance += v / 100;
            case REGEN -> p.regen += v;
            case MAGNET -> p.magnet *= 1 + v / 100;
            case XP -> p.xpMult += v / 100;
            case COOLDOWN -> p.cooldownMult *= Math.max(0.4, 1 - v / 100);
            case GOLD -> p.goldMult += v / 100;
        }
    }

    // ------------------------------------------------------------------ difficulty

    /** How dangerous it is right now: the challenge's own danger, each earlier clear, and a step for every minute you've taken. */
    double threat() { return challenge.danger + loops * Challenge.LOOP_DANGER + time / 60; }

    /** The danger clock's word for it, Risk of Rain style. */
    String dangerLabel() {
        double t = threat();
        return t < 2 ? "EASY" : t < 4 ? "MEDIUM" : t < 6 ? "HARD" : t < 8 ? "VERY HARD" : t < 10 ? "INSANE" : "IMPOSSIBLE";
    }

    double hpMult(Player p, Enemy.Type t) {
        double m = threat();
        double base = t == Enemy.Type.NEST ? 1 : t.small() ? 0.52 : 0.8;
        return base * (1 + 0.30 * m + 0.014 * m * m) * (1 + 0.03 * (p.level - 1));
    }

    double dmgMult(Player p) {
        return (0.8 + 0.11 * threat()) * (1 + 0.012 * (p.level - 1));
    }

    /** How many monsters the director keeps around you. */
    int population(Player p) {
        return (int) Math.min(MAX_ENEMIES, 12 + 5.0 * threat() + 0.4 * p.level);
    }

    /** Keeps the level's multipliers (used by summons) in step with the clock. */
    private void retune(World w) {
        Player p = w.player;
        w.level.smallEnemyHpMult = hpMult(p, Enemy.Type.GRUNT) * 0.6;
        w.level.enemyHpMult = hpMult(p, Enemy.Type.BRUTE);
        w.level.smallEnemyDamageMult = w.level.enemyDamageMult = dmgMult(p);
    }

    /** What the director sends at you: mostly the challenge's favourite, more variety the longer it goes. */
    private Enemy.Type pickType() {
        double m = threat();
        double[] wts = new double[Enemy.Type.values().length];
        wts[Enemy.Type.GRUNT.ordinal()] = 1.0;
        wts[Enemy.Type.RUNNER.ordinal()] = m >= 0.5 ? 0.6 : 0;
        wts[Enemy.Type.SHOOTER.ordinal()] = m >= 1.5 ? 0.3 : 0;
        wts[Enemy.Type.BRUTE.ordinal()] = m >= 2.5 ? 0.1 + 0.02 * m : 0;
        wts[Enemy.Type.SHADE.ordinal()] = m >= 5 ? 0.2 : 0;
        wts[challenge.favoured.ordinal()] += 1.2;
        double total = 0;
        for (double v : wts) total += v;
        double x = rng.nextDouble() * total;
        for (Enemy.Type t : Enemy.Type.values()) {
            x -= wts[t.ordinal()];
            if (x < 0) return t;
        }
        return Enemy.Type.GRUNT;
    }

    // ------------------------------------------------------------------ the frame

    void update(World w, double dt) {
        Player p = w.player;
        time += dt;
        if (p.regen > 0 && p.hp > 0) p.hp = Math.min(p.maxHp, p.hp + p.regen * dt);
        Arsenal.update(w, p, dt);

        retuneTimer -= dt;
        if (retuneTimer <= 0) { retuneTimer = 1; retune(w); }

        if (!bossSpawned && !bossDead) {
            direct(w, dt);
            eliteTimer -= dt;
            if (eliteTimer <= 0) { eliteTimer = ELITE_EVERY; spawnElite(w); }
            swarmTimer -= dt;
            if (swarmTimer <= 0) { swarmTimer = SWARM_EVERY; swarm(w, w.player.x, w.player.y); }
            updateNests(w, dt);
            updateRelays(w, dt);
            updateSpecimens(w);
            updateWard(w, dt);
        }
        updateStorm(w, dt);
        if (bossWarning > 0) {
            bossWarning -= dt;
            w.shake = Math.max(w.shake, 3);
            if (bossWarning <= 0) spawnBoss(w);
        }
        relocateStragglers(w);
        updateCrates(w, dt);
        updatePickups(w, dt);
    }

    /**
     * Keeps the battlefield topped up: a few more monsters every third of a second while there are fewer than the
     * target. While a relay is powering up, half as many again, coming quicker, out of the dark round it.
     */
    private void direct(World w, double dt) {
        spawnTimer -= dt;
        if (spawnTimer > 0) return;
        Relay surge = chargingRelay();
        boolean siege = ward != null && ward.targetable() && (ward.work > 0 || ward.kind == Ward.Kind.ENGINE);   // Copper cutting, the engine charging
        spawnTimer = surge != null || siege ? 0.22 : 0.33;
        int want = (int) Math.min(MAX_ENEMIES, population(w.player) * (surge != null || siege ? 1.3 : 1)) - w.enemies.size();
        int shooters = 0;
        for (Enemy e : w.enemies) if (e.type == Enemy.Type.SHOOTER) shooters++;
        for (int i = 0; i < Math.min(3, want); i++) {
            Enemy.Type t = pickType();
            if (t == Enemy.Type.SHOOTER && shooters >= 8) t = Enemy.Type.GRUNT;
            if (t == Enemy.Type.SHOOTER) shooters++;
            Util.Vec at = surge != null && rng.nextBoolean() ? around(w, surge.x, surge.y, Relay.RADIUS + 330, Relay.RADIUS + 520)
                : ward != null && ward.targetable() && rng.nextDouble() < 0.4 ? around(w, ward.x, ward.y, 650, 900)
                : spawnPoint(w, 700, 950);
            spawn(w, t, at.x(), at.y(), 1, 1);
        }
    }

    /** A spot {@code min}..{@code max} from (x, y), on walkable ground (for a relay's surges). */
    private Util.Vec around(World w, double x, double y, double min, double max) {
        for (int tries = 0; tries < 30; tries++) {
            double a = rng.nextDouble() * Math.PI * 2, d = min + rng.nextDouble() * (max - min);
            double sx = x + Math.cos(a) * d, sy = y + Math.sin(a) * d;
            if (w.level.contains(sx, sy) && w.level.contains(sx + 30, sy) && w.level.contains(sx - 30, sy)) return new Util.Vec(sx, sy);
        }
        return spawnPoint(w, 700, 950);
    }

    /** A spot {@code min}..{@code max} away from the player (just off-screen), on walkable ground. */
    Util.Vec spawnPoint(World w, double min, double max) {
        Player p = w.player;
        for (int tries = 0; tries < 30; tries++) {
            double a = rng.nextDouble() * Math.PI * 2, d = min + rng.nextDouble() * (max - min);
            double x = p.x + Math.cos(a) * d, y = p.y + Math.sin(a) * d;
            if (w.level.contains(x, y) && w.level.contains(x + 30, y) && w.level.contains(x - 30, y)) return new Util.Vec(x, y);
        }
        double a = rng.nextDouble() * Math.PI * 2;
        return w.level.clamp(p.x + Math.cos(a) * min, p.y + Math.sin(a) * min, 30);
    }

    Enemy spawn(World w, Enemy.Type t, double x, double y, double hpBoost, double dmgBoost) {
        Enemy e = new Enemy(t, x, y, hpMult(w.player, t) * hpBoost, dmgMult(w.player) * dmgBoost, w.rng);
        e.spawnIn = 0.35;
        boolean walker = t == Enemy.Type.GRUNT || t == Enemy.Type.RUNNER || t == Enemy.Type.BRUTE;
        e.forWard = walker && ward != null && ward.targetable() && rng.nextDouble() < WARD_SHARE;
        w.enemies.add(e);
        return e;
    }

    /** Monsters left far behind are brought back round in front of you, so the pressure never just trails off. */
    private void relocateStragglers(World w) {
        for (Enemy e : w.enemies) {
            if (e.tough() || e.summoned || e.forWard) continue;
            if (Util.dist(e.x, e.y, w.player.x, w.player.y) < 1500) continue;
            Util.Vec at = spawnPoint(w, 700, 900);
            e.x = e.lastX = at.x();
            e.y = e.lastY = at.y();
            e.kx = e.ky = 0;
        }
    }

    /** Nests guard themselves: when you come near, a burst of guards springs up, and more keep coming while you're close. */
    private void updateNests(World w, double dt) {
        Player p = w.player;
        for (Enemy nest : new ArrayList<>(w.enemies)) {                  // (its guards join the list as we go)
            if (!nest.rooted() || nest.hp <= 0) continue;
            double d = Util.dist(nest.x, nest.y, p.x, p.y);
            if (d > NEST_RANGE) continue;
            nest.summonCd -= dt;
            if (!nest.awake) {
                nest.awake = true;
                nest.summonCd = 0;
                w.notice = "A NEST!";
                w.noticeHint = "It spits out guards while you're near. Tear it down.";
                w.noticeTimer = 3;
                w.soundAt(Snd.BOSS_INTRO, nest.x, nest.y, 0, 0.6, 1.2);
            }
            if (nest.summonCd > 0) continue;
            int guards = nest.waves++ == 0 ? 5 : 2 + (threat() > 4 ? 1 : 0);
            nest.summonCd = 4.5;
            for (int i = 0; i < guards; i++) {
                double a = rng.nextDouble() * Math.PI * 2;
                Util.Vec at = w.level.clamp(nest.x + Math.cos(a) * 90, nest.y + Math.sin(a) * 90, 20);
                Enemy.Type t = i == 0 && nest.waves > 3 && threat() > 3 ? Enemy.Type.BRUTE : challenge.favoured;
                Enemy e = spawn(w, t, at.x(), at.y(), 0.9, 1);
                e.spawnIn = 0.5;
                w.effects.add(Effect.ring(at.x(), at.y(), 40, 10, 0.4, new Color(220, 90, 255), false));
            }
            w.soundAt(Snd.BOSS_SUMMON_LAB, nest.x, nest.y, 0, 0.7, 0.8);
        }
    }

    /** A ring of monsters closing in from every side at once, round (x, y). */
    private void swarm(World w, double x, double y) {
        int n = 14 + (int) (2 * threat());
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            Util.Vec at = w.level.clamp(x + Math.cos(a) * 560, y + Math.sin(a) * 560, 20);
            spawn(w, challenge.favoured == Enemy.Type.GRUNT ? Enemy.Type.RUNNER : challenge.favoured, at.x(), at.y(), 0.8, 1);
        }
        w.banner = "SWARM!";
        w.bannerTimer = 1.6;
        w.sound(w.themed(Snd.LOCK_FOREST, Snd.LOCK_CITY, Snd.LOCK_LAB));
    }

    private void spawnElite(World w) {
        Enemy.Type t = threat() > 5 && rng.nextBoolean() ? Enemy.Type.SHOOTER : Enemy.Type.BRUTE;
        Util.Vec at = spawnPoint(w, 520, 640);
        Enemy e = spawn(w, t, at.x(), at.y(), t.small() ? 22 : 12, 1.5);
        e.elite = true;
        e.forWard = false;
        e.spawnIn = 0.9;
        w.banner = "ELITE!";
        w.bannerTimer = 2;
        w.notice = "An elite " + creature(w.level.theme, t) + " has appeared";
        w.noticeHint = "It drops a treasure chest.";
        w.noticeTimer = 3.5;
        w.sound(Snd.BOSS_INTRO);
        w.effects.add(Effect.ring(at.x(), at.y(), 10, 120, 0.6, new Color(255, 210, 80), true));
    }

    /** What the people of a place call one of its monsters. */
    static String creature(Theme theme, Enemy.Type t) {
        return switch (theme) {
            case FOREST -> switch (t) { case BRUTE -> "stump golem"; case SHOOTER -> "snap-bloom"; case RUNNER -> "blight fox"; default -> "thornling"; };
            case CITY -> switch (t) { case BRUTE -> "living dumpster"; case SHOOTER -> "security drone"; case RUNNER -> "alley cat"; default -> "sewer rat"; };
            case LAB -> switch (t) { case BRUTE -> "mutant"; case SHOOTER -> "flask spitter"; case RUNNER -> "clockwork mouse"; default -> "ooze"; };
        };
    }

    // ------------------------------------------------------------------ relays

    /** The relay that's switched on and still powering up, if any (only one at a time). */
    Relay chargingRelay() {
        for (Relay r : relays) if (r.charging()) return r;
        return null;
    }

    /** The relay within reach that can be switched on, if any. */
    Relay relayNearby(World w) {
        for (Relay r : relays) if (!r.started && Util.dist(r.x, r.y, w.player.x, w.player.y) < 110) return r;
        return null;
    }

    /** E at a relay: switch it on (unless another one is still powering up). */
    private boolean switchOn(World w, Relay r) {
        if (chargingRelay() != null) {
            w.effects.add(Effect.text(r.x, r.y - 90, "Finish the relay you started first", new Color(230, 120, 120), false));
            w.sound(Snd.MENU_DENY);
            return true;
        }
        r.started = true;
        w.notice = "RELAY SWITCHED ON";
        w.noticeHint = "Stay inside its circle while it powers up. Here they come.";
        w.noticeTimer = 4;
        w.soundAt(Snd.BOSS_UNSEAL, r.x, r.y);
        w.shake = Math.max(w.shake, 5);
        w.effects.add(Effect.ring(r.x, r.y, 20, Relay.RADIUS, 0.5, RELAY_LIGHT, true));
        return true;
    }

    static final Color RELAY_LIGHT = new Color(255, 222, 120);

    /** The relay you switched on powers up while you're in its circle; its surges come at every quarter. */
    private void updateRelays(World w, double dt) {
        Relay r = chargingRelay();
        for (Relay o : relays) o.held = false;
        if (r == null) return;
        Player p = w.player;
        r.held = r.contains(p.x, p.y) && p.hp > 0;
        if (r.held) r.charge = Math.min(1, r.charge + dt / Relay.CHARGE_TIME);
        r.zap -= dt;
        if (r.held && r.zap <= 0) {                                       // the Blight hates the light: inside the circle it's slowed and scorched
            r.zap = Relay.ZAP_EVERY;
            boolean any = false;
            for (Enemy e : w.enemies) {
                if (!e.targetable() || !r.contains(e.x, e.y)) continue;
                double dmg = e.type.small() && !e.elite ? e.maxHp * 0.09 : e.maxHp * 0.02;
                e.hurt(w, dmg, 0, 0, 0, RELAY_LIGHT, false, false);
                e.slow(0.5, Relay.ZAP_EVERY + 0.2);
                if (any || rng.nextInt(3) == 0) w.effects.add(Effect.spark(e.x, e.y - 10, rng.nextDouble() * Math.PI * 2, 60, 4, 0.3, RELAY_LIGHT));
                any = true;
            }
            if (any) w.soundAt(Snd.ICE_TICK, r.x, r.y, 0, 0.5, 1.4);
        }
        int due = (int) (r.charge * 4);
        if (due > r.surges && r.surges < 3) {
            r.surges++;
            swarm(w, r.x, r.y);
            if (r.surges == 2) spawnElite(w);
            else {
                w.banner = "SURGE!";
                w.bannerTimer = 1.6;
            }
        }
        if (r.charge >= 1) relayDone(w, r);
    }

    /** Fully powered: it lights up for good, and its pulse flattens everything ordinary round it. */
    private void relayDone(World w, Relay r) {
        r.done = true;
        nestsLeft--;
        w.shake = Math.max(w.shake, 9);
        w.effects.add(Effect.ring(r.x, r.y, 30, 760, 0.6, RELAY_LIGHT, true));
        w.effects.add(Effect.ring(r.x, r.y, 20, Relay.RADIUS, 0.5, Color.WHITE, false));
        w.soundAt(Snd.FIRE_EXPLODE, r.x, r.y, 0, 1, 0.8);
        for (Enemy e : w.enemies) {
            if (!e.targetable() || Util.dist(r.x, r.y, e.x, e.y) > 760) continue;
            double big = e.tough() ? e.maxHp * 0.15 : e.hp + 1;
            e.hurt(w, big, 0, 0, 0.3, RELAY_LIGHT, true, false);
        }
        for (int i = 0; i < 5; i++) drop(w, new Pickup(Pickup.Kind.GEM, r.x, r.y, 14 + (int) (2 * threat())));
        drop(w, new Pickup(Pickup.Kind.HEART, r.x, r.y, 0));
        if (nestsLeft > 0) {
            w.banner = "RELAY ONLINE";
            w.bannerTimer = 2;
            w.notice = (nestsTotal - nestsLeft) + " / " + nestsTotal + " RELAYS";
            w.noticeHint = nestsLeft == 1 ? "One left." : nestsLeft + " left. Follow the arrows.";
            w.noticeTimer = 3.5;
            w.sound(Snd.ROOM_CLEAR);
            return;
        }
        goalMet(w);
    }

    // ------------------------------------------------------------------ specimens

    static final Color SPECIMEN_GLOW = new Color(230, 90, 255), VINES = new Color(190, 90, 210);

    /**
     * Specimens sleep until something comes near. Awake, they fight like any brute; at every quarter of their health
     * lost they shriek, shed a pack of oozes and mice, and bolt.
     */
    private void updateSpecimens(World w) {
        Player p = w.player;
        for (Enemy e : new ArrayList<>(w.enemies)) {                     // (the ones they shed join the list as we go)
            if (!e.specimen || e.hp <= 0) continue;
            if (!e.awake) {
                if (Util.dist(e.x, e.y, p.x, p.y) > NEST_RANGE * 0.9 && e.hp >= e.maxHp) continue;
                e.awake = true;
                w.notice = "A SPECIMEN!";
                w.noticeHint = "It bolts when it's hurt. Don't let it get away.";
                w.noticeTimer = 3;
                w.soundAt(Snd.BOSS_INTRO, e.x, e.y, 0, 0.6, 1.3);
                w.effects.add(Effect.ring(e.x, e.y, 20, 160, 0.5, SPECIMEN_GLOW, false));
            }
            int due = (int) ((1 - e.hp / e.maxHp) * 4);
            if (due <= e.molts || e.molts >= 3) continue;
            e.molts++;
            e.flee = 1.6;
            w.shake = Math.max(w.shake, 5);
            w.soundAt(Snd.BOSS_PHASE2_LAB, e.x, e.y, 0, 0.5, 1.4);
            w.effects.add(Effect.ring(e.x, e.y, 30, 200, 0.5, SPECIMEN_GLOW, true));
            for (int i = 0; i < 4 + (threat() > 6 ? 1 : 0); i++) {
                double a = rng.nextDouble() * Math.PI * 2;
                Util.Vec at = w.level.clamp(e.x + Math.cos(a) * 80, e.y + Math.sin(a) * 80, 16);
                Enemy m = spawn(w, i % 2 == 0 ? Enemy.Type.GRUNT : Enemy.Type.RUNNER, at.x(), at.y(), 0.8, 1);
                m.spawnIn = 0.4;
            }
        }
    }

    /** A specimen is down; with the last one, the way home opens. */
    private void specimenDown(World w, Enemy e) {
        nestsLeft--;
        w.shake = Math.max(w.shake, 8);
        w.effects.add(Effect.ring(e.x, e.y, 20, 240, 0.6, SPECIMEN_GLOW, true));
        if (nestsLeft > 0) {
            w.banner = "SPECIMEN DOWN";
            w.bannerTimer = 2;
            w.notice = (nestsTotal - nestsLeft) + " / " + nestsTotal + " SPECIMENS";
            w.noticeHint = nestsLeft == 1 ? "One left." : nestsLeft + " left. Follow the arrows.";
            w.noticeTimer = 3.5;
            w.sound(Snd.ROOM_CLEAR);
            return;
        }
        goalMet(w);
    }

    // ------------------------------------------------------------------ the ward: Copper, or the engine

    /** A monster's blow lands on the ward. */
    void hitWard(World w, double damage) {
        if (ward == null || !ward.hurt(w, damage * WARD_DAMAGE)) return;
        w.shake = Math.max(w.shake, 7);
        w.soundAt(Snd.BOSS_SLAM_LAB, ward.x, ward.y, 0, 0.7, 1.3);
        w.effects.add(Effect.ring(ward.x, ward.y, 10, 160, 0.5, new Color(255, 110, 80), true));
        w.banner = ward.kind == Ward.Kind.ROBOT ? "COPPER IS DOWN!" : "THE ENGINE IS DOWN!";
        w.bannerTimer = 2.2;
        w.notice = ward.kind == Ward.Kind.ROBOT ? "Stand by him to get him going again" : "Stand by it to restart it. The charge is draining!";
        w.noticeHint = "A few seconds right beside it. Clear them off first if you can.";
        w.noticeTimer = 5;
        if (ward.kind == Ward.Kind.ENGINE) ward.charge = Math.max(0, ward.charge - 0.08);
        for (Enemy e : w.enemies) e.forWard = false;                     // nothing left to go for: they all turn on you
    }

    private void updateWard(World w, double dt) {
        Ward wd = ward;
        if (wd == null || nestsLeft <= 0) return;
        Player p = w.player;
        wd.flash = Math.max(0, wd.flash - dt);
        wd.sinceHit += dt;
        if (wd.broken) {                                                  // stand by it to get it going again
            wd.moving = false;
            boolean by = p.hp > 0 && Util.dist(p.x, p.y, wd.x, wd.y) < Ward.REPAIR_RANGE + wd.radius;
            if (by) wd.repair += dt / Ward.REPAIR_TIME;
            sparkTimer -= dt;
            if (by && sparkTimer <= 0) {
                sparkTimer = 0.15;
                w.effects.add(Effect.spark(wd.x, wd.y - 30, rng.nextDouble() * Math.PI * 2, 120, 4, 0.3, Ward.COPPER_LIGHT));
                w.soundAt(Snd.ZAP_HIT, wd.x, wd.y, 0, 0.5, 1.2);
            }
            if (wd.repair < 1) return;
            wd.broken = false;
            wd.hp = wd.maxHp * 0.5;
            wd.sinceHit = 0;
            w.notice = wd.kind == Ward.Kind.ROBOT ? "COPPER'S BACK UP" : "THE ENGINE IS RUNNING AGAIN";
            w.noticeHint = wd.kind == Ward.Kind.ROBOT ? "Stay close and he'll roll on." : "Keep them off it.";
            w.noticeTimer = 3;
            w.sound(Snd.GUIDE_APPEAR);
            w.effects.add(Effect.ring(wd.x, wd.y, 10, 140, 0.5, wd.kind == Ward.Kind.ROBOT ? Ward.COPPER_LIGHT : Ward.FROST, true));
            return;
        }
        if (wd.sinceHit > Ward.MEND_AFTER) wd.hp = Math.min(wd.maxHp, wd.hp + wd.maxHp * Ward.MEND * dt);
        if (wd.kind == Ward.Kind.ROBOT) updateRobot(w, dt);
        else updateEngine(w, dt);
    }

    /** Copper rolls on while you're with him, stops to cut through each wall of vines, and makes for the junction. */
    private void updateRobot(World w, double dt) {
        Ward wd = ward;
        Player p = w.player;
        if (wd.work > 0) {
            wd.moving = false;
            wd.work -= dt;
            sparkTimer -= dt;
            if (sparkTimer <= 0) {
                sparkTimer = 0.12;
                double dir = wd.faceLeft ? -1 : 1;
                for (int i = 0; i < 2; i++) w.effects.add(Effect.spark(wd.x + dir * 30, wd.y - 24, (dir > 0 ? 0 : Math.PI) + rng.nextGaussian() * 0.8, 160, 4, 0.35, Ward.COPPER_LIGHT));
                if (rng.nextInt(3) == 0) w.soundAt(Snd.ZAP_HIT, wd.x, wd.y, 0, 0.45, 0.9);
            }
            if (wd.work > 0) return;
            wd.nextStop++;
            w.banner = "THROUGH!";
            w.bannerTimer = 1.6;
            w.notice = wd.nextStop < wd.stops.length ? "On to the next wall of vines" : "On to the junction";
            w.noticeHint = "Stay close and he'll roll on.";
            w.noticeTimer = 3;
            w.sound(Snd.ROOM_CLEAR);
            w.effects.add(Effect.ring(wd.x, wd.y, 20, 200, 0.5, VINES, true));
            for (int i = 0; i < 4; i++) drop(w, new Pickup(Pickup.Kind.GEM, wd.x, wd.y, 14 + (int) (2 * threat())));
            drop(w, new Pickup(Pickup.Kind.HEART, wd.x, wd.y, 0));
            return;
        }
        wd.moving = p.hp > 0 && Util.dist(p.x, p.y, wd.x, wd.y) < Ward.FOLLOW;
        if (!wd.moving) return;
        double step = Ward.SPEED * dt;
        if (wd.nextStop < wd.stops.length && wd.along + step >= wd.stops[wd.nextStop]) {
            wd.advance(wd.stops[wd.nextStop] - wd.along);
            wd.work = Ward.WORK_TIME;
            wd.moving = false;
            swarm(w, wd.x, wd.y);
            if (wd.nextStop == wd.stops.length - 1) spawnElite(w);
            w.banner = "VINES!";
            w.bannerTimer = 1.6;
            w.notice = "Copper's cutting through. Keep them off him!";
            w.noticeHint = "It takes him a few seconds. They know it.";
            w.noticeTimer = 4;
            return;
        }
        wd.advance(step);
        if (wd.along < wd.length - 0.5) return;
        wd.moving = false;
        nestsLeft = 0;
        w.effects.add(Effect.ring(wd.x, wd.y, 20, 760, 0.6, Ward.COPPER_LIGHT, true));
        goalMet(w);
    }

    /** The engine charges while it stands, chilling what comes near; its surges come at each third. */
    private void updateEngine(World w, double dt) {
        Ward wd = ward;
        wd.charge = Math.min(1, wd.charge + dt / Ward.CHARGE_TIME);
        chillTimer -= dt;
        if (chillTimer <= 0) {                                            // the cold round it slows whatever comes near
            chillTimer = 0.5;
            for (Enemy e : w.enemies) if (e.targetable() && Util.dist(e.x, e.y, wd.x, wd.y) < Ward.CHILL) e.slow(0.55, 0.7);
        }
        int due = (int) (wd.charge * 3);
        if (due > wd.surges && wd.surges < 2) {
            wd.surges++;
            swarm(w, wd.x, wd.y);
            if (wd.surges == 2) spawnElite(w);
            else {
                w.banner = "SURGE!";
                w.bannerTimer = 1.6;
            }
        }
        if (wd.charge < 1) return;
        nestsLeft = 0;
        w.shake = Math.max(w.shake, 10);
        w.effects.add(Effect.ring(wd.x, wd.y, 30, 900, 0.7, Ward.FROST, true));
        w.effects.add(Effect.ring(wd.x, wd.y, 20, 300, 0.5, Color.WHITE, false));
        w.soundAt(Snd.CAST_ICE, wd.x, wd.y);
        for (Enemy e : w.enemies) {
            if (!e.targetable() || Util.dist(wd.x, wd.y, e.x, e.y) > 900) continue;
            e.hurt(w, e.tough() ? e.maxHp * 0.15 : e.hp + 1, 0, 0, 0.3, Ward.FROST, true, false);
        }
        goalMet(w);
    }

    // ------------------------------------------------------------------ the storm (Stormcliff)

    /**
     * Every few seconds lightning picks out spots round you (one close by): marked on the ground, struck a moment later.
     * It hurts you if you're standing in one, and flattens any ordinary monster that is.
     */
    private void updateStorm(World w, double dt) {
        if (w.level.theme != Theme.LAB || bossDead || ringActive) return;
        stormTimer -= dt;
        if (stormTimer > 0) return;
        stormTimer = 7 + rng.nextDouble() * 4 - Math.min(2.5, threat() * 0.25);
        Player p = w.player;
        int n = 2 + (threat() > 5 ? 1 : 0);
        for (int i = 0; i < n; i++) {
            Util.Vec at = null;
            for (int tries = 0; tries < 12 && at == null; tries++) {
                double a = rng.nextDouble() * Math.PI * 2, d = i == 0 ? 60 + rng.nextDouble() * 180 : 200 + rng.nextDouble() * 450;
                double x = p.x + Math.cos(a) * d, y = p.y + Math.sin(a) * d;
                if (!w.level.contains(x, y)) continue;
                if (ward != null && Util.dist(x, y, ward.x, ward.y) < 200) continue;   // the storm spares what you protect
                at = new Util.Vec(x, y);
            }
            if (at != null) w.blasts.add(Blast.lightning(at.x(), at.y(), 1.3 + i * 0.3, p.maxHp * 0.12));
        }
    }

    /** The last nest is down: everything ordinary drops its gem and vanishes, and the guardian arrives in a ring. */
    private void spawnBoss(World w) {
        bossSpawned = true;
        Player p = w.player;
        for (Enemy e : new ArrayList<>(w.enemies)) {
            dropFor(w, e);
            w.effects.add(Effect.particle(e.x, e.y - e.radius * 0.3, 0, 0, 0.42, "fx.puff", -1, 1, 0, 3));
        }
        w.enemies.clear();
        w.projectiles.clear();
        w.lockTarget = null;
        ringX = p.x;
        ringY = p.y;
        ringActive = true;
        Util.Vec at = null;
        for (int tries = 0; tries < 24 && at == null; tries++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double bx = ringX + Math.cos(a) * 340, by = ringY + Math.sin(a) * 340;
            if (w.level.contains(bx, by)) at = new Util.Vec(bx, by);
        }
        if (at == null) at = w.level.clamp(ringX + 200, ringY, 50);
        double toughness = challenge.theme == Theme.LAB ? MORROW_HP : 1;      // Morrow, the chapter's last stand, is far tougher
        Enemy boss = new Enemy(Enemy.Type.BOSS, at.x(), at.y(), toughness * (0.85 + 0.35 * loops) * (1 + 0.05 * (p.level - 1)), 0.9 + 0.3 * loops, w.rng);
        w.enemies.add(boss);
        w.banner = w.level.bossName;
        w.bannerTimer = 3;
        w.notice = "BOSS FIGHT";
        w.noticeHint = "The ring holds you both in. Only one of you leaves.";
        w.noticeTimer = 3.5;
        w.sound(Snd.BOSS_INTRO);
        w.shake = Math.max(w.shake, 10);
        w.effects.add(Effect.ring(ringX, ringY, 40, RING_RADIUS, 0.7, new Color(255, 90, 90), false));
    }

    /**
     * The nearest spot to (x, y) where something {@code r} across can stand and be walked up to: clear of trees and
     * rocks, on walkable ground; crates in the way are simply swept aside.
     */
    Util.Vec clearSpot(World w, double x, double y, double r) {
        for (int pass = 0; pass < 4; pass++) {
            for (Level.Landmark l : w.level.landmarks) {
                if (l.radius() <= 0) continue;
                double d = Util.dist(x, y, l.x(), l.y() - 12), min = l.radius() + r;
                if (d >= min) continue;
                double a = d < 0.01 ? 0 : Math.atan2(y - (l.y() - 12), x - l.x());
                x = l.x() + Math.cos(a) * min;
                y = l.y() - 12 + Math.sin(a) * min;
            }
            Util.Vec v = w.level.clamp(x, y, r);
            x = v.x();
            y = v.y();
        }
        for (Breakable b : w.level.breakables) if (!b.broken && Util.dist(x, y, b.x, b.y) < r + b.radius + 20) b.broken = true;
        return new Util.Vec(x, y);
    }

    /** Inside the boss ring, nobody gets out. Called after every collision pass. */
    void confine(World w) {
        if (!ringActive) return;
        Player p = w.player;
        Util.Vec v = inRing(p.x, p.y, p.radius);
        p.x = v.x();
        p.y = v.y();
        for (Enemy e : w.enemies) {
            v = inRing(e.x, e.y, e.radius);
            e.x = v.x();
            e.y = v.y();
        }
    }

    private Util.Vec inRing(double x, double y, double r) {
        double dx = x - ringX, dy = y - ringY, d = Math.hypot(dx, dy), max = RING_RADIUS - r;
        if (d <= max) return new Util.Vec(x, y);
        return new Util.Vec(ringX + dx / d * max, ringY + dy / d * max);
    }

    /** Keeps the battlefield stocked with crates (they break, the director quietly replaces them out of sight). */
    private void updateCrates(World w, double dt) {
        crateTimer -= dt;
        if (crateTimer > 0) return;
        crateTimer = 12;
        int alive = 0;
        for (Breakable b : w.level.breakables) if (!b.broken) alive++;
        w.level.breakables.removeIf(b -> b.broken);
        for (int i = alive; i < CRATES && i < alive + 4; i++) {
            Util.Vec at = spawnPoint(w, 900, 1500);
            w.level.breakables.add(new Breakable(rng.nextBoolean() ? Breakable.Kind.CRATE : Breakable.Kind.BARREL, at.x(), at.y(), 1));
        }
    }

    // ------------------------------------------------------------------ drops

    /** An enemy died (for real — the final boss changing stage doesn't count). */
    void onKill(World w, Enemy e) {
        if (e.elite) elitesKilled++;
        dropFor(w, e);
        if (e.type == Enemy.Type.NEST) nestDown(w, e);
        if (e.specimen) specimenDown(w, e);
        if (e.type == Enemy.Type.BOSS) bossDown(w, e);
    }

    /** Its gem, maybe a coin or a heart, and a chest for an elite. A nest bursts into a shower of them. */
    private void dropFor(World w, Enemy e) {
        if (e.summoned) return;
        if (e.type == Enemy.Type.NEST || e.specimen) {
            for (int i = 0; i < 6; i++) drop(w, new Pickup(Pickup.Kind.GEM, e.x, e.y, 14 + (int) (2 * threat())));
            for (int i = 0; i < 4; i++) drop(w, new Pickup(Pickup.Kind.COIN, e.x, e.y, 3 + rng.nextInt(4)));
            drop(w, new Pickup(Pickup.Kind.HEART, e.x, e.y, 0));
            return;
        }
        int xp = (int) Math.round(e.type.xp * (1 + 0.1 * threat()) * (e.elite ? 6 : 1) * (e.type == Enemy.Type.BOSS ? 2 : 1));
        drop(w, new Pickup(Pickup.Kind.GEM, e.x, e.y, xp));
        if (rng.nextDouble() < 0.04 || e.elite) drop(w, new Pickup(Pickup.Kind.COIN, e.x, e.y, (1 + rng.nextInt(3)) * (e.elite ? 10 : 1)));
        if (rng.nextDouble() < 0.004) drop(w, new Pickup(Pickup.Kind.HEART, e.x, e.y, 0));
        if (e.elite) drop(w, new Pickup(Pickup.Kind.ELITE_CHEST, e.x, e.y, 0));
    }

    void drop(World w, Pickup pk) {
        if (pk.kind == Pickup.Kind.GEM) {
            int gems = 0;
            Pickup far = null;
            for (Pickup o : pickups) {
                if (o.kind != Pickup.Kind.GEM) continue;
                gems++;
                if (!o.attracted && Util.dist(o.x, o.y, w.player.x, w.player.y) > 900) far = o;
            }
            if (gems > 320 && far != null) { far.value += pk.value; return; }      // too many lying about: pool it into a distant one
        }
        if (pk.kind != Pickup.Kind.PORTAL && pk.kind != Pickup.Kind.BOSS_CHEST && pk.kind != Pickup.Kind.CACHE) {   // things pop out of what dropped them
            double a = rng.nextDouble() * Math.PI * 2, sp = pk.kind.magnetic() ? 60 + rng.nextDouble() * 90 : 120;
            pk.vx = Math.cos(a) * sp;
            pk.vy = Math.sin(a) * sp;
        }
        pickups.add(pk);
    }

    /** Crates hold the good stuff: gold, hearts, magnets and bombs. */
    void onSmash(World w, Breakable b) {
        double x = rng.nextDouble();
        Pickup.Kind k = x < 0.38 ? Pickup.Kind.COIN : x < 0.58 ? Pickup.Kind.HEART : x < 0.70 ? Pickup.Kind.MAGNET
            : x < 0.80 ? Pickup.Kind.BOMB : Pickup.Kind.GEM;
        int value = k == Pickup.Kind.COIN ? 3 + rng.nextInt(6) : k == Pickup.Kind.GEM ? 12 + (int) (3 * threat()) : 0;
        drop(w, new Pickup(k, b.x, b.y - 6, value));
    }

    /** A nest is down. With the last one gone, the guardian comes (or the way home opens). */
    private void nestDown(World w, Enemy nest) {
        nestsLeft--;
        w.shake = Math.max(w.shake, 8);
        w.effects.add(Effect.ring(nest.x, nest.y, 20, 220, 0.6, new Color(230, 110, 255), true));
        if (nestsLeft > 0) {
            w.banner = "NEST DESTROYED";
            w.bannerTimer = 2;
            w.notice = (nestsTotal - nestsLeft) + " / " + nestsTotal + " NESTS";
            w.noticeHint = nestsLeft == 1 ? "One left." : nestsLeft + " left. Follow the arrows.";
            w.noticeTimer = 3.5;
            w.sound(Snd.ROOM_CLEAR);
            return;
        }
        goalMet(w);
    }

    /** The last nest is down (or relay powered up): the guardian comes, or the way home opens. */
    private void goalMet(World w) {
        w.sound(Snd.BOSS_UNSEAL);
        if (challenge.boss) {
            bossWarning = BOSS_WARNING;
            w.banner = "THE GROUND SHAKES";
            w.bannerTimer = 3;
            w.notice = switch (challenge.goal) {
                case RELAYS -> "Every relay is on";
                case HUNT -> "Every specimen is down";
                case ESCORT -> "Copper made it";
                case DEFEND -> "The engine is charged";
                default -> "Every nest is down";
            } + "... and something is coming";
            w.noticeHint = "Get ready.";
            w.noticeTimer = BOSS_WARNING;
        } else {
            openWayHome(w, w.player.x, w.player.y - 60, false);
        }
    }

    /** The guardian is down: the ring falls, every gem flies to you, its chest drops and the way home opens. */
    private void bossDown(World w, Enemy boss) {
        ringActive = false;
        openWayHome(w, boss.x, boss.y, true);
    }

    /** The end of the fight: gems and gold fly to you, a chest drops, and a portal home opens beside it. */
    private void openWayHome(World w, double x, double y, boolean boss) {
        bossDead = true;
        lateLevels += pendingLevels;                           // anything still waiting to be chosen is moot now: levels turn to gold,
        pendingLevels = 0;                                     // and a chest's cards are dropped (its item is already yours)
        pendingChests = 0;
        unseen.clear();
        for (Pickup pk : pickups) if (pk.kind == Pickup.Kind.GEM || pk.kind == Pickup.Kind.COIN) pk.attracted = true;
        for (Enemy e : w.enemies) if (!e.rooted()) e.hp = 0;
        Util.Vec chest = clearSpot(w, x, y, 40);
        drop(w, new Pickup(Pickup.Kind.BOSS_CHEST, chest.x(), chest.y(), 0));
        Util.Vec portal = clearSpot(w, chest.x() + 30, chest.y() - 170, 60);
        if (Util.dist(portal.x(), portal.y(), chest.x(), chest.y()) < 140) portal = clearSpot(w, chest.x() + 200, chest.y(), 60);
        drop(w, new Pickup(Pickup.Kind.PORTAL, portal.x(), portal.y(), 0));
        w.banner = boss ? "VICTORY!" : "CLEARED!";
        w.bannerTimer = 3;
        w.notice = "Open the chest, then step into the light";
        w.noticeHint = "It leads back to " + (challenge.theme == Theme.CITY ? "the city." : "the forest.");
        w.noticeTimer = 6;
        w.sound(Snd.GUIDE_APPEAR, boss ? 2.4 : 0.6);
    }

    // ------------------------------------------------------------------ pickups and caches

    /** Sets each unopened cache's price: dearer the more you've opened and the longer the fight has run. */
    private void priceCaches() {
        int price = (int) Math.round(25 + 15 * cachesOpened + 4 * threat());
        for (Pickup pk : pickups) if (pk.kind == Pickup.Kind.CACHE) pk.value = price;
    }

    /** The cache within reach, if any. */
    Pickup cacheNearby(World w) {
        for (Pickup pk : pickups) {
            if (pk.kind == Pickup.Kind.CACHE && Util.dist(pk.x, pk.y, w.player.x, w.player.y) < 80) return pk;
        }
        return null;
    }

    /** E in a fight: switch on the relay you're standing at, or open the cache, if you can afford it. */
    void interact(World w) {
        Relay relay = relayNearby(w);
        if (relay != null && switchOn(w, relay)) return;
        Pickup cache = cacheNearby(w);
        if (cache == null) return;
        if (gold < cache.value) {
            w.effects.add(Effect.text(cache.x, cache.y - 60, "Needs " + cache.value + " gold", new Color(230, 120, 120), false));
            w.sound(Snd.MENU_DENY);
            return;
        }
        gold -= cache.value;
        pickups.remove(cache);
        cachesOpened++;
        w.sound(Snd.CHEST_OPEN);
        chestBurst(w, cache.x, cache.y, new Color(255, 214, 90));
        if (rng.nextDouble() < 0.35) findItem(w, Item.roll(rng, Item.rarity(rng, Item.odds(challenge.world, Item.Source.CACHE, loops)), tier()));
        pendingChests++;
    }

    /** How strong items found here are. */
    private int tier() { return (int) Math.min(3, (challenge.danger + loops * Challenge.LOOP_DANGER) / 2); }

    private void updatePickups(World w, double dt) {
        priceCaches();
        Player p = w.player;
        boolean chestLeft = false;
        for (Pickup pk : pickups) if (pk.kind == Pickup.Kind.BOSS_CHEST) chestLeft = true;
        List<Pickup> taken = new ArrayList<>();
        for (Pickup pk : pickups) {
            pk.age += dt;
            pk.x += pk.vx * dt;
            pk.y += pk.vy * dt;
            double drag = Math.exp(-5 * dt);
            pk.vx *= drag;
            pk.vy *= drag;
            double d = Util.dist(pk.x, pk.y, p.x, p.y);
            if (pk.kind.magnetic() && pk.age > 0.25) {
                if (!pk.attracted && d < p.magnet) pk.attracted = true;
                if (pk.attracted) {
                    pk.speed = Math.min(1300, Math.max(pk.speed, 260) + 1800 * dt);
                    double step = Math.min(d, pk.speed * dt);
                    pk.x += (p.x - pk.x) / Math.max(d, 1e-6) * step;
                    pk.y += (p.y - 6 - pk.y) / Math.max(d, 1e-6) * step;
                    d = Util.dist(pk.x, pk.y, p.x, p.y);
                }
                if (d < p.radius + 12) taken.add(pk);
            } else if (pk.kind == Pickup.Kind.PORTAL) {
                if (!chestLeft && d < pk.radius()) taken.add(pk);
            } else if (pk.kind == Pickup.Kind.CACHE) {
                // opened with E (see interact)
            } else if (d < p.radius + pk.radius() && pk.age > 0.3) {
                taken.add(pk);
            }
        }
        for (Pickup pk : taken) {
            if (!pickups.remove(pk)) continue;
            collect(w, pk);
            if (w.run != this || w.state != World.State.PLAYING && pk.kind == Pickup.Kind.PORTAL) return;   // the fight just ended
        }
    }

    private void collect(World w, Pickup pk) {
        Player p = w.player;
        switch (pk.kind) {
            case GEM -> {
                addXp(w, pk.value);
                w.soundAt(Snd.XP_PICKUP, pk.x, pk.y, 0, 0.8 + 0.2 * pk.gemTier(), 1);
            }
            case COIN -> {
                int g = (int) Math.round(pk.value * p.goldMult);
                gold += g;
                w.soundAt(Snd.COIN_PICKUP, pk.x, pk.y);
                w.effects.add(Effect.text(pk.x, pk.y - 20, "+" + g, new Color(255, 214, 80), false));
            }
            case HEART -> {
                p.heal(w, p.maxHp * 0.3);
                w.sound(Snd.POWERUP);
                w.effects.add(Effect.ring(p.x, p.y, 10, 60, 0.4, Perk.HEALING.color, true));
            }
            case MAGNET -> {
                for (Pickup o : pickups) if (o.kind.magnetic()) o.attracted = true;
                w.sound(Snd.POWERUP);
                w.effects.add(Effect.ring(p.x, p.y, 20, 900, 0.6, new Color(120, 170, 255), false));
                w.effects.add(Effect.text(p.x, p.y - 60, "MAGNET!", new Color(140, 190, 255), true));
            }
            case BOMB -> bomb(w);
            case ELITE_CHEST -> openEliteChest(w, pk);
            case BOSS_CHEST -> openBossChest(w, pk);
            case PORTAL -> { w.sound(Snd.TRAVEL); finish(w, Outcome.VICTORY); }
            case CACHE -> { }
        }
    }

    /** Wipes out every ordinary monster on screen (nests, elites and bosses just take a heavy hit). */
    private void bomb(World w) {
        Player p = w.player;
        w.sound(Snd.FIRE_EXPLODE);
        w.shake = Math.max(w.shake, 12);
        w.effects.add(Effect.explosion(p.x, p.y, 160));
        w.effects.add(Effect.ring(p.x, p.y, 30, 760, 0.5, new Color(255, 170, 70), true));
        for (Enemy e : w.enemies) {
            if (!e.targetable() || Util.dist(p.x, p.y, e.x, e.y) > 760) continue;
            double big = e.tough() ? e.maxHp * 0.08 : e.hp + 1;
            e.hurt(w, big, 0, 0, 0.3, new Color(255, 170, 70), true, false);
        }
    }

    private void openEliteChest(World w, Pickup pk) {
        int g = 20 + (int) (5 * threat());
        gold += g;
        w.sound(Snd.CHEST_OPEN);
        chestBurst(w, pk.x, pk.y, new Color(255, 214, 90));
        w.effects.add(Effect.text(pk.x, pk.y - 50, "+" + g + " GOLD", new Color(255, 214, 80), true));
        if (rng.nextDouble() < 0.4) findItem(w, Item.roll(rng, Item.rarity(rng, Item.odds(challenge.world, Item.Source.ELITE, loops)), tier()));
        pendingChests++;
    }

    private void openBossChest(World w, Pickup pk) {
        int g = 40 + (int) (10 * threat());
        gold += g;
        w.sound(Snd.CHEST_OPEN);
        chestBurst(w, pk.x, pk.y, new Color(255, 150, 60));
        w.effects.add(Effect.text(pk.x, pk.y - 50, "+" + g + " GOLD", new Color(255, 214, 80), true));
        double[] odds = Item.odds(challenge.world, challenge.boss ? Item.Source.GUARDIAN : Item.Source.CHEST, loops);
        Item it = Item.roll(rng, Item.rarity(rng, odds), tier());
        findItem(w, it);
        unseen.remove(it);                                     // just the item: the fighting's over, so no cards (the results screen shows it)
    }

    private void findItem(World w, Item it) {
        loot.add(it);
        unseen.add(it);
        w.notice = "FOUND: " + it.name;
        w.noticeHint = it.rarity.label + " " + it.slot.label.toLowerCase() + "  -  yours to keep";
        w.noticeTimer = 5;
        w.sound(Snd.GAME_CLEARED, 0.3);
    }

    private void chestBurst(World w, double x, double y, Color c) {
        w.effects.add(Effect.ring(x, y, 10, 110, 0.5, c, true));
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14;
            w.effects.add(Effect.particle(x, y - 20, Math.cos(a) * 170, Math.sin(a) * 170 - 60, 0.8, "fx.star", -1, 0.03, 30, 3));
        }
        w.shake = Math.max(w.shake, 4);
    }

    // ------------------------------------------------------------------ XP and level-ups

    /** XP needed to go from level {@code lv} to the next. */
    static int xpFor(int lv) {
        int n = lv - 1;
        return 30 + 16 * n + (int) (1.8 * n * n);
    }

    void addXp(World w, double amount) {
        Player p = w.player;
        xpCarry += amount * p.xpMult;
        int whole = (int) xpCarry;
        xpCarry -= whole;
        p.xp += whole;
        int levels = 0;
        while (p.xp >= p.xpNext) {
            p.xp -= p.xpNext;
            p.level++;
            p.xpNext = xpFor(p.level);
            levels++;
        }
        if (levels == 0) return;
        Color gold = new Color(255, 220, 90);
        if (bossDead) {                                        // the fight's won: no cards, gold at the end instead
            lateLevels += levels;
            w.effects.add(Effect.text(p.x, p.y - 60, "LEVEL UP!", gold, true));
            w.effects.add(Effect.text(p.x, p.y - 96, "+" + levels * LATE_LEVEL_GOLD + " GOLD", gold, false));
            w.sound(Snd.LEVEL_UP);
            return;
        }
        pendingLevels += levels;
        w.effects.add(Effect.text(p.x, p.y - 60, "LEVEL UP!", gold, true));
        w.effects.add(Effect.ring(p.x, p.y, 10, 80, 0.5, gold, true));
        w.sound(Snd.LEVEL_UP);
    }

    /** After the frame: if a level-up or a chest is waiting, stop the action and show the choices. */
    void maybeOpenChoices(World w) {
        if (w.state != World.State.PLAYING || w.player.hp <= 0 || bossDead) return;
        if (pendingLevels > 0) openChoices(w, "LEVEL UP!");
        else if (pendingChests > 0) openChoices(w, "TREASURE!");
    }

    private void openChoices(World w, String title) {
        choiceTitle = title;
        choices = Perk.offer(w.player, rng, cards, !title.startsWith("LEVEL"));
        if (choices.isEmpty()) choices = List.of(new Perk.Choice(null, -1), new Perk.Choice(null, -2));   // everything is maxed out
        choiceCursor = 0;
        choiceArm = CHOICE_ARM_TIME;
        w.state = World.State.LEVEL_UP;
        w.sound(Snd.MENU_OPEN);
    }

    /**
     * 1, 2, 3 (and 4, with the Ring of Fortune) take that card; R rerolls them. ENTER is the attack, so it does nothing
     * here. The cards can't be taken for the first {@value #CHOICE_ARM_TIME} seconds.
     */
    void updateChoices(World w, Input in, double dt) {
        choiceArm = Math.max(0, choiceArm - dt);
        if (in.pressed(KeyEvent.VK_R)) {
            if (rerolls > 0 && choices.get(0).perk() != null) {
                rerolls--;
                choices = Perk.offer(w.player, rng, cards, !choiceTitle.startsWith("LEVEL"));
                choiceCursor = 0;
                choiceArm = CHOICE_ARM_TIME;
                w.sound(Snd.MENU_OPEN);
            } else {
                w.sound(Snd.MENU_DENY);
            }
        }
        if (choiceArm > 0) return;
        for (int i = 0; i < choices.size() && i < CARD_KEYS.length; i++) {
            if (!in.pressed(CARD_KEYS[i][0]) && !in.pressed(CARD_KEYS[i][1])) continue;
            choiceCursor = i;
            take(w, choices.get(i));
            in.consume(CARD_KEYS[i]);
            return;
        }
    }

    void take(World w, Perk.Choice c) {
        Player p = w.player;
        if (c.perk() == null) {
            if (c.rank() == -1) gold += 40;
            else p.hp = Math.min(p.maxHp, p.hp + p.maxHp * 0.4);
        } else if (c.evolution()) {
            p.perk[c.perk().ordinal()] = Perk.EVOLVED;
            w.effects.add(Effect.text(p.x, p.y - 70, c.perk().evoName.toUpperCase() + "!", c.perk().color, true));
        } else {
            c.perk().take(p);
        }
        w.sound(Snd.MENU_SELECT);
        if (choiceTitle.equals("LEVEL UP!")) pendingLevels--;
        else {
            pendingChests--;
            unseen.clear();
        }
        p.invuln = Math.max(p.invuln, 0.4);
        w.state = World.State.PLAYING;
        maybeOpenChoices(w);
    }

    // ------------------------------------------------------------------ the end

    /**
     * The fight is over. Whatever happened, the gold picked up and the items found are yours; a victory also pays the
     * challenge's reward (gold and skill points, more for the first clear) and counts as a clear.
     */
    void finish(World w, Outcome how) {
        if (over) return;
        over = true;
        outcome = how;
        Adventure a = w.adventure;
        Profile prof = w.profile;
        if (how == Outcome.VICTORY) {
            firstClear = a.clears(challenge) == 0;
            rewardGold = firstClear ? challenge.gold : challenge.repeatGold + 20 * loops;
            rewardSkillPoints = (firstClear ? challenge.skillPoints : challenge.repeatSkillPoints) + (w.player.level >= 12 ? 1 : 0);
            a.clears.merge(challenge.id, 1, Integer::sum);
            a.skillPoints += rewardSkillPoints;
            a.wins++;
            a.restock(challenge.world);
        }
        lateGold = lateLevels * LATE_LEVEL_GOLD;
        prof.gold += gold + rewardGold + lateGold;
        prof.add(loot);
        prof.runs++;
        if (how == Outcome.VICTORY) prof.victories++;
        prof.bestKills = Math.max(prof.bestKills, w.kills);
        prof.bestLevel = Math.max(prof.bestLevel, w.player.level);
        w.saveGame();
        w.state = World.State.RESULTS;
        w.overTimer = World.OVER_GUARD;
        w.sound(how == Outcome.VICTORY ? Snd.GAME_CLEARED : Snd.GAME_OVER);
    }

    /** Legendary armour or Second Wind: the first death of the fight isn't the end. */
    boolean tryRevive(World w) {
        if (!reviveAvailable || reviveUsed) return false;
        reviveUsed = true;
        Player p = w.player;
        p.hp = p.maxHp * 0.5;
        p.invuln = 2.5;
        w.banner = "REVIVED!";
        w.bannerTimer = 2;
        w.sound(Snd.GUIDE_APPEAR);
        w.effects.add(Effect.ring(p.x, p.y, 10, 260, 0.6, new Color(255, 150, 60), true));
        for (Enemy e : w.enemies) {
            double d = Util.dist(p.x, p.y, e.x, e.y);
            if (d > 300 || e.type.armored) continue;
            double a = Util.angleTo(p.x, p.y, e.x, e.y);
            e.kx += Math.cos(a) * 900;
            e.ky += Math.sin(a) * 900;
            e.stun = Math.max(e.stun, 1);
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) { }
    }

    static double parse(Properties p, String key) {
        try {
            return Double.parseDouble(p.getProperty(key, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String clock(double seconds) {
        int s = (int) seconds;
        return s / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60);
    }

    /** How far the goal has got, as the HUD and the results put it: "2 / 3", or for a ward "42%". */
    String tally() {
        if (ward != null) return (nestsLeft <= 0 ? 100 : (int) Math.floor(ward.progress() * 100)) + "%";
        return (nestsTotal - nestsLeft) + " / " + nestsTotal;
    }

    /** The first pickup of a kind (for the HUD). */
    Pickup find(Pickup.Kind k) {
        for (Pickup pk : pickups) if (pk.kind == k) return pk;
        return null;
    }
}
