package game;

import static game.MenuStyle.DOT;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything drawn in screen space during a fight: the HUD (XP bar, the objective and the danger clock, skill slots,
 * arrows to nests and chests), the level-up cards, the fight's pause menu and the results screen. The world's own
 * screens are {@link WorldHud}'s; the main menu is {@link TitleScreen}.
 */
final class RunHud {
    private final Font f12 = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private final Font f12b = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private final Font f14 = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    private final Font f14b = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private final Font f16b = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    private final Font f22b = new Font(Font.SANS_SERIF, Font.BOLD, 22);
    private final Font f30b = new Font(Font.SANS_SERIF, Font.BOLD, 30);
    private final Font f54b = new Font(Font.SANS_SERIF, Font.BOLD, 54);
    private final Minimap minimap = new Minimap();
    private final Baked baked = new Baked();

    private static final Color GOLD = new Color(255, 214, 80);
    private static final Color XP = new Color(90, 190, 255);
    private static final Color DIM_TEXT = new Color(170, 172, 188);

    // ------------------------------------------------------------------ the HUD

    void drawHud(Graphics2D g, World w, int width, int height) {
        Run run = w.run;
        Player p = w.player;

        // XP: a bar across the whole top of the screen
        g.setColor(new Color(10, 12, 20, 220));
        g.fillRect(0, 0, width, 16);
        g.setColor(XP);
        g.fill(new Rectangle2D.Double(0, 2, width * Util.clamp((double) p.xp / p.xpNext, 0, 1), 12));
        g.setColor(new Color(255, 255, 255, 60));
        g.fill(new Rectangle2D.Double(0, 2, width * Util.clamp((double) p.xp / p.xpNext, 0, 1), 4));
        g.setFont(f12b);
        centered(g, "LV " + p.level, width / 2.0, 13, Color.WHITE);

        // health, gold, kills (top left)
        bar(g, 20, 26, 260, 20, p.hp / p.maxHp, p.hp < p.maxHp * 0.3 ? new Color(235, 70, 70) : new Color(70, 200, 90));
        g.setFont(f14b);
        g.setColor(Color.WHITE);
        g.drawString("HP  " + (int) Math.ceil(Math.max(0, p.hp)) + " / " + (int) p.maxHp, 28, 41);
        Art.frame("run.coin", w.time, 6).draw(g, 30, 70, 3, false);
        g.setFont(f16b);
        g.setColor(GOLD);
        g.drawString(String.valueOf(run.gold), 44, 70);
        g.setColor(new Color(235, 235, 245));
        g.drawString(w.kills + " kills", 120, 70);
        if (run.reviveAvailable && !run.reviveUsed) {
            g.setFont(f12b);
            g.setColor(new Color(255, 160, 70));
            g.drawString("PHOENIX READY", 210, 70);
        }

        // the objective and the danger clock (top centre)
        Enemy boss = w.boss();
        g.setFont(f30b);
        String goal = run.bossDead ? "CLEARED" : boss != null ? "GUARDIAN" : run.bossWarning > 0 ? "..." : run.challenge.goalWord() + "  " + run.tally();
        centered(g, goal, width / 2.0, 52, run.bossDead ? new Color(150, 240, 150) : boss != null ? new Color(255, 110, 110) : Color.WHITE);
        g.setFont(f12b);
        String danger = run.dangerLabel();
        Color dc = switch (danger) { case "EASY" -> new Color(140, 230, 140); case "MEDIUM" -> new Color(240, 220, 120); case "HARD" -> new Color(255, 160, 80); default -> new Color(255, 90, 90); };
        centered(g, run.challenge.title + "   -   " + Run.clock(run.time) + "   -   DANGER: " + danger, width / 2.0, 70, dc);
        if (run.bossWarning > 0) {
            g.setFont(f22b);
            double k = 0.5 + 0.5 * Math.sin(w.time * 10);
            centered(g, w.level.bossName + " AWAKENS", width / 2.0, 100, Util.alpha(new Color(255, 90, 90), 0.6 + 0.4 * k));
        }
        if (boss != null) {
            double bw = Math.min(560, width - 520), bx = (width - bw) / 2;
            bar(g, bx, 86, bw, 16, boss.hp / boss.maxHp, boss.phase2 ? new Color(255, 110, 60) : new Color(210, 45, 70));
            g.setFont(f12b);
            boolean lab = w.level.theme == Theme.LAB;
            centered(g, w.level.bossName + (lab ? "   -   STAGE " + (boss.phase2 ? 2 : 1) : ""), width / 2.0, 99, Color.WHITE);
        }

        Relay relay = run.chargingRelay();
        if (relay != null && boss == null) {                          // a relay powering up: how far, and whether you're holding it
            double bw = Math.min(460, width - 560), bx = (width - bw) / 2;
            bar(g, bx, 86, bw, 14, relay.charge, Run.RELAY_LIGHT);
            g.setFont(f12b);
            String label = "RELAY POWERING UP   " + (int) (relay.charge * 100) + "%";
            centered(g, label, width / 2.0 + 1, 99, new Color(0, 0, 0, 200));
            centered(g, label, width / 2.0, 98, Color.WHITE);
            if (!relay.held) {
                g.setFont(f22b);
                double k = 0.5 + 0.5 * Math.sin(w.time * 9);
                centered(g, "GET BACK IN THE CIRCLE!", width / 2.0, 130, Util.alpha(new Color(255, 120, 90), 0.6 + 0.4 * k));
            }
        }

        Ward ward = run.ward;
        if (ward != null && boss == null && run.nestsLeft > 0) drawWardBar(g, w, ward, width);

        if (w.mapZoom <= 0) minimap.draw(g, w, width, height);   // (held open, the Renderer draws it over everything else)
        drawSkillSlots(g, p, height);
        drawArrows(g, w, run, width, height);

        g.setFont(f12);
        g.setColor(new Color(200, 200, 212, 170));
        String hint = "ENTER  attack (hold)" + (p.rollUnlocked ? "    SPACE  roll" : "") + "    TAB  lock on (hold: map)    ESC  pause";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(hint, width - fm.stringWidth(hint) - 16, height - 14);

        if (w.noticeTimer > 0 && w.state == World.State.PLAYING) {
            double fade = Math.min(1, w.noticeTimer);
            g.setFont(f22b);
            centered(g, w.notice, width / 2.0, height - 150, Util.alpha(GOLD, fade));
            g.setFont(f14);
            if (!w.noticeHint.isEmpty()) centered(g, w.noticeHint, width / 2.0, height - 127, Util.alpha(Color.WHITE, fade));
        }
        if (w.bannerTimer > 0 && w.state == World.State.PLAYING) {
            g.setFont(f54b);
            centered(g, w.banner, width / 2.0, height * 0.32, Util.alpha(Color.WHITE, Math.min(1, w.bannerTimer)));
        }
    }

    /** Your skills (big boxes, with their recharge) and passives (small ones), bottom left. */
    private void drawSkillSlots(Graphics2D g, Player p, int height) {
        double x = 18, y = height - 104;
        for (Perk k : Perk.values()) {
            if (k.kind != Perk.Kind.SKILL || p.perk[k.ordinal()] == 0) continue;
            int r = p.perk[k.ordinal()];
            double cd = Arsenal.baseCooldown(p, k) * p.cooldownMult;
            double frac = cd > 0 ? Util.clamp(p.skillCd[k.ordinal()] / cd, 0, 1) : 0;
            slot(g, k, r, x, y, 46, frac);
            x += 52;
        }
        if (x == 18) {
            g.setFont(f12);
            g.setColor(DIM_TEXT);
            g.drawString("Only your sword for now: level up to learn skills", (float) x, (float) (y + 28));
        }
        x = 18;
        y += 56;
        for (Perk k : Perk.values()) {
            if (k.kind != Perk.Kind.PASSIVE || p.perk[k.ordinal()] == 0) continue;
            slot(g, k, p.perk[k.ordinal()], x, y, 34, 0);
            x += 39;
        }
    }

    /**
     * A skill's slot: its icon in a box, darkened by a sweep while it's {@code cooling} down (0..1), with its rank in the
     * corner. The box and the frame are baked (they only change when the rank does); only the sweep is drawn live.
     */
    private void slot(Graphics2D g, Perk k, int rank, double x, double y, double size, double cooling) {
        double pad = 3;
        baked.draw(g, List.of("slot", k, rank >= Perk.EVOLVED, size), x - pad, y - pad, size + 2 * pad, size + 2 * pad, bg -> {
            bg.setColor(new Color(16, 16, 24, 225));
            bg.fill(new RoundRectangle2D.Double(pad, pad, size, size, 9, 9));
            drawIcon(bg, k, pad + size / 2, pad + size / 2 - 2, size * 0.78, rank >= Perk.EVOLVED);
        });
        if (cooling > 0) {
            Shape saved = g.getClip();
            g.clip(new RoundRectangle2D.Double(x, y, size, size, 9, 9));
            g.setColor(new Color(0, 0, 0, 150));
            g.fill(new Arc2D.Double(x - size * 0.3, y - size * 0.3, size * 1.6, size * 1.6, 90, 360 * cooling, Arc2D.PIE));
            g.setClip(saved);
        }
        baked.draw(g, List.of("frame", k, rank, size), x - pad, y - pad, size + 2 * pad, size + 2 * pad, bg -> slotFrame(bg, k, rank, pad, pad, size));
    }

    /** A slot's border and its rank in the corner. */
    private void slotFrame(Graphics2D g, Perk k, int rank, double x, double y, double size) {
        RoundRectangle2D box = new RoundRectangle2D.Double(x, y, size, size, 9, 9);
        g.setColor(rank >= Perk.EVOLVED ? GOLD : Util.alpha(k.color, 0.8));
        g.setStroke(new BasicStroke(rank >= Perk.EVOLVED ? 2.6f : 1.6f));
        g.draw(box);
        g.setFont(f12b);
        String label = rank >= Perk.EVOLVED ? "MAX" : String.valueOf(rank);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(0, 0, 0, 200));
        g.drawString(label, (float) (x + size - fm.stringWidth(label) - 2), (float) (y + size - 1));
        g.setColor(rank >= Perk.EVOLVED ? GOLD : Color.WHITE);
        g.drawString(label, (float) (x + size - fm.stringWidth(label) - 3), (float) (y + size - 2));
    }

    /**
     * What you're protecting: how far Copper has got (or how charged the engine is), its health under that, and a
     * warning when it's down or waiting for you.
     */
    private void drawWardBar(Graphics2D g, World w, Ward ward, int width) {
        boolean robot = ward.kind == Ward.Kind.ROBOT;
        double bw = Math.min(460, width - 560), bx = (width - bw) / 2;
        Color c = robot ? Ward.COPPER_LIGHT : Ward.FROST;
        bar(g, bx, 86, bw, 14, ward.progress(), c);
        g.setFont(f12b);
        String label = robot ? "COPPER" + (ward.work > 0 ? " - CUTTING THROUGH THE VINES" : "") + "   " + (int) (ward.progress() * 100) + "% OF THE WAY"
            : "STASIS ENGINE CHARGING   " + (int) (ward.progress() * 100) + "%";
        centered(g, label, width / 2.0 + 1, 99, new Color(0, 0, 0, 200));
        centered(g, label, width / 2.0, 98, Color.WHITE);
        double hp = ward.broken ? 0 : ward.hp / ward.maxHp;                        // its health, a thin bar under
        bar(g, bx, 104, bw, 6, hp, hp < 0.3 ? new Color(235, 80, 70) : new Color(90, 210, 110));
        String warn = null;
        if (ward.broken) warn = robot ? "COPPER IS DOWN! STAND BY HIM TO REPAIR" : "THE ENGINE IS DOWN! STAND BY IT TO RESTART";
        else if (robot && !ward.moving && ward.work <= 0) warn = "COPPER IS WAITING FOR YOU";
        if (warn == null) return;
        g.setFont(f22b);
        double k = 0.5 + 0.5 * Math.sin(w.time * 9);
        centered(g, warn, width / 2.0, 138, Util.alpha(ward.broken ? new Color(255, 120, 90) : new Color(255, 214, 140), 0.6 + 0.4 * k));
    }

    private void drawArrows(Graphics2D g, World w, Run run, int width, int height) {
        double left = Util.clamp(w.camX - width / 2.0, 0, Math.max(0, w.level.width - width));
        double top = Util.clamp(w.camY - height / 2.0, 0, Math.max(0, w.level.height - height));
        for (Pickup pk : run.pickups) {
            Color c = switch (pk.kind) {
                case ELITE_CHEST -> GOLD;
                case BOSS_CHEST -> new Color(255, 150, 60);
                case PORTAL -> new Color(200, 150, 255);
                default -> null;
            };
            if (c == null) continue;
            arrow(g, pk.x - left, pk.y - top, c, width, height);
        }
        for (Enemy e : w.enemies) if (e.rooted() && e.hp > 0) arrow(g, e.x - left, e.y - top, new Color(230, 110, 255), width, height);
        for (Relay r : run.relays) if (!r.done) arrow(g, r.x - left, r.y - top, Run.RELAY_LIGHT, width, height);
        for (Enemy e : w.enemies) if (e.specimen && e.hp > 0) arrow(g, e.x - left, e.y - top, Run.SPECIMEN_GLOW, width, height);
        Ward ward = run.ward;
        if (ward != null && run.nestsLeft > 0) arrow(g, ward.x - left, ward.y - top, ward.kind == Ward.Kind.ROBOT ? Ward.COPPER_LIGHT : Ward.FROST, width, height);
    }

    /** An arrow at the screen's edge pointing at something off-screen, at (sx, sy) in screen terms. */
    private void arrow(Graphics2D g, double sx, double sy, Color c, int width, int height) {
        if (sx > 30 && sx < width - 30 && sy > 30 && sy < height - 30) return;
        double cx = width / 2.0, cy = height / 2.0;
        double ang = Math.atan2(sy - cy, sx - cx);
        double ex = Util.clamp(sx, 40, width - 40), ey = Util.clamp(sy, 110, height - 130);
        AffineTransform saved = g.getTransform();
        Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.translate(ex, ey);
        g.rotate(ang);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);   // baked pointing right, turned smoothly
        baked.draw(g, List.of("arrow", c), -20, -20, 44, 40, bg -> {           // (room for the outline's sharp corners)
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(36, 20);
            arrow.lineTo(10, 9);
            arrow.lineTo(15, 20);
            arrow.lineTo(10, 31);
            arrow.closePath();
            bg.setColor(new Color(0, 0, 0, 160));
            bg.setStroke(new BasicStroke(4f));
            bg.draw(arrow);
            bg.setColor(c);
            bg.fill(arrow);
        });
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation == null ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : interpolation);
        g.setTransform(saved);
    }

    // ------------------------------------------------------------------ level-up choices

    private final MenuStyle.Glide pauseGlide = new MenuStyle.Glide();

    /**
     * A level-up (or a chest): a gold heading, and the cards as dark glass, each tinted along the top in its perk's
     * colour, with the number key that takes it sitting on its top edge. The keys stay dim for the moment the cards
     * ignore them after appearing.
     */
    void drawChoices(Graphics2D g, World w, int width, int height) {
        MenuStyle.antialias(g);
        MenuStyle.veil(g, width, height, 150);
        Run run = w.run;
        Player p = w.player;
        double s = MenuStyle.scale(height), cx = width / 2.0;
        List<Perk.Choice> choices = run.choices;
        int n = choices.size();
        double cw = (n >= 4 ? 232 : 258) * s, ch = 336 * s, gap = 22 * s;
        double total = n * cw + (n - 1) * gap;
        boolean level = run.choiceTitle.startsWith("LEVEL");
        List<Item> found = level ? List.of() : run.unseen;
        double x0 = (width - total) / 2, y0 = Math.max(150 * s, (height - ch) / 2 + 6 * s) + (found.isEmpty() ? 0 : 64 * s);

        if (!found.isEmpty()) {                                                  // what came out of the chest, above it all
            int shown = Math.min(3, found.size());
            double chipW = 306 * s, chipH = 52 * s, chipY = y0 - 172 * s, fx0 = cx - shown * chipW / 2;
            g.setFont(MenuStyle.caps(11 * s));
            MenuStyle.centred(g, shown == 1 ? "IN THE CHEST" : "IN THE CHESTS", cx, chipY - 10 * s, GOLD);
            for (int i = 0; i < shown; i++) itemChip(g, found.get(i), fx0 + i * chipW + 5 * s, chipY, chipW - 10 * s, chipH, "NEW", s);
        }
        double base = y0 - 58 * s;
        java.awt.geom.Rectangle2D hb = MenuStyle.headingCentred(g, level ? "LEVEL UP" : "TREASURE", cx, base, 60 * s, s, false).getBounds2D();
        MenuStyle.ruled(g, level ? "LEVEL " + (p.level - run.pendingLevels + 1) + DOT + "CHOOSE ONE" : "A FREE UPGRADE" + DOT + "CHOOSE ONE",
            cx, base + 32 * s, hb.getX() - 60 * s, hb.getMaxX() + 60 * s, 15 * s, s);

        java.awt.Composite saved = g.getComposite();
        java.awt.Composite faded = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.4f);
        String[] numbers = new String[n];
        for (int i = 0; i < n; i++) {
            double x = x0 + i * (cw + gap);
            drawCard(g, w, choices.get(i), x, y0, cw, ch, p, s);
            numbers[i] = String.valueOf(i + 1);
            if (run.choiceArm > 0) g.setComposite(faded);
            g.setFont(MenuStyle.sans(Font.BOLD, 15 * s));                         // the key that takes this card, on its top edge
            double capW = 30 * s, capH = 28 * s, capX = x + cw / 2 - capW / 2, capY = y0 - capH / 2;
            RoundRectangle2D cap = new RoundRectangle2D.Double(capX, capY, capW, capH, 8, 8);
            g.setColor(new Color(20, 18, 30));
            g.fill(cap);
            g.setColor(MenuStyle.GOLD);
            g.setStroke(new BasicStroke((float) (1.8 * s)));
            g.draw(cap);
            MenuStyle.centred(g, numbers[i], capX + capW / 2, capY + capH / 2 + 5.5 * s, Color.WHITE);
            g.setComposite(saved);
        }

        String[][] groups = {numbers, {"R"}};
        String[] labels = {"Take that card", run.rerolls > 0 ? "Reroll (" + run.rerolls + " left)" : "No rerolls left"};
        double kw = MenuStyle.keysWidth(g, s, groups, labels), ky = y0 + ch + 44 * s;
        if (run.choiceArm > 0) g.setComposite(faded);
        MenuStyle.keys(g, cx - kw / 2, ky, s, groups, labels);
        g.setComposite(saved);
        g.setFont(MenuStyle.serif(Font.ITALIC, 14 * s, 0));
        MenuStyle.centred(g, "Skills " + Perk.owned(p, Perk.Kind.SKILL) + " / " + Perk.SKILL_SLOTS + DOT + "Passives " + Perk.owned(p, Perk.Kind.PASSIVE)
            + " / " + Perk.PASSIVE_SLOTS + DOT + "a maxed skill evolves once you also own its partner passive", cx, ky + 28 * s, MenuStyle.DIM);
        drawSkillSlots(g, p, height);
    }

    private void drawCard(Graphics2D g, World w, Perk.Choice c, double x, double y, double cw, double ch, Player p, double s) {
        Perk k = c.perk();
        if (c.evolution()) {                                                     // an evolution glows gold
            double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() / 1.6e8);
            for (int i = 3; i >= 1; i--) {
                g.setColor(Util.alpha(MenuStyle.GOLD_DEEP, (0.08 + 0.05 * pulse) * (4 - i) / 3));
                g.fill(new RoundRectangle2D.Double(x - i * 5 * s, y - i * 5 * s, cw + i * 10 * s, ch + i * 10 * s, 20 + i * 8, 20 + i * 8));
            }
        }
        int now = k == null ? 0 : p.perk[k.ordinal()];
        double pad = 8 * s;                                                      // the card's outline and the text's shadows reach a little past its edge
        baked.draw(g, List.of("card", c, now, cw, ch, s), x - pad, y - pad, cw + 2 * pad, ch + 2 * pad, bg -> paintCard(bg, c, now, pad, pad, cw, ch, s));
    }

    /** A card's face (everything but an evolution's pulsing glow); {@code now} is the rank the player holds. */
    private void paintCard(Graphics2D g, Perk.Choice c, int now, double x, double y, double cw, double ch, double s) {
        Perk k = c.perk();
        Color accent = k == null || c.evolution() ? GOLD : k.color;
        RoundRectangle2D card = MenuStyle.card(g, x, y, cw, ch, accent);
        g.setColor(Util.alpha(accent, c.evolution() ? 0.95 : 0.55));
        g.setStroke(new BasicStroke((float) ((c.evolution() ? 2.4 : 1.6) * s)));
        g.draw(card);

        String tag, name, text;
        if (k == null) {
            tag = "BONUS";
            name = c.rank() == -1 ? "Treasure" : "Rest";
            text = c.rank() == -1 ? "You've mastered everything. Take 40 gold." : "You've mastered everything. Heal 40% of your health.";
        } else {
            name = k.title(c.rank());
            text = c.rank() == 1 && k.kind == Perk.Kind.SKILL ? k.blurb + " " + k.text(1) : k.text(c.rank());
            if (c.evolution()) tag = "EVOLUTION";
            else if (now == 0) tag = k.kind == Perk.Kind.SKILL ? "NEW SKILL" : "NEW PASSIVE";
            else tag = "LEVEL " + now + "  >  " + c.rank();
        }
        g.setFont(MenuStyle.caps(11 * s));
        MenuStyle.centred(g, tag, x + cw / 2, y + 32 * s, c.evolution() ? GOLD : tag.startsWith("NEW") ? new Color(150, 255, 170) : new Color(230, 226, 240));

        double icx = x + cw / 2, icy = y + 92 * s, r = 40 * s;                   // the medallion
        g.setColor(new Color(10, 10, 18, 235));
        g.fill(new Ellipse2D.Double(icx - r, icy - r, r * 2, r * 2));
        g.setColor(Util.alpha(accent, 0.95));
        g.setStroke(new BasicStroke((float) (2.6 * s)));
        g.draw(new Ellipse2D.Double(icx - r, icy - r, r * 2, r * 2));
        g.setColor(new Color(255, 214, 120, c.evolution() ? 200 : 80));
        g.setStroke(new BasicStroke((float) (1.2 * s)));
        g.draw(new Ellipse2D.Double(icx - r - 6 * s, icy - r - 6 * s, (r + 6 * s) * 2, (r + 6 * s) * 2));
        if (k != null) drawIcon(g, k, icx, icy, 56 * s, c.evolution());
        else drawCoinIcon(g, icx, icy, c.rank() == -1);

        g.setFont(MenuStyle.serif(Font.BOLD, 23 * s, 0.03));
        MenuStyle.centred(g, name, x + cw / 2, y + 170 * s, c.evolution() ? GOLD : Color.WHITE);
        if (k != null) {
            g.setFont(MenuStyle.caps(9.5 * s));
            MenuStyle.centred(g, c.evolution() ? "EVOLVED " + k.label.toUpperCase() : k.kind == Perk.Kind.SKILL ? "SKILL" + DOT + "FIRES BY ITSELF" : "PASSIVE",
                x + cw / 2, y + 190 * s, MenuStyle.DIM);
        }
        g.setFont(MenuStyle.sans(Font.PLAIN, 14 * s));
        g.setColor(new Color(232, 230, 240));
        wrapped(g, text, x + 20 * s, y + 222 * s, cw - 40 * s, 19 * s, true);

        if (k != null && !c.evolution()) {                                       // rank diamonds: held, this one, still to come
            int max = k.maxRank;
            double step = 18 * s, px0 = x + cw / 2 - (max - 1) * step / 2, py = y + ch - 26 * s;
            for (int i = 0; i < max; i++) {
                Color pc = i < c.rank() - 1 ? accent : i == c.rank() - 1 ? Color.WHITE : new Color(62, 60, 78);
                MenuStyle.diamond(g, px0 + i * step, py, (i == c.rank() - 1 ? 6 : 5) * s, pc);
            }
            if (k.evolvable() && c.rank() == k.maxRank) {
                g.setFont(MenuStyle.serif(Font.ITALIC, 12 * s, 0));
                MenuStyle.centred(g, "evolves with " + k.partner().label, x + cw / 2, py - 14 * s, Util.alpha(GOLD, 0.9));
            }
        }
    }

    private void drawCoinIcon(Graphics2D g, double cx, double cy, boolean coin) {
        if (coin) Art.frames("run.coin")[0].draw(g, cx, cy + 18, 6, false);
        else Art.frames("run.heart")[0].draw(g, cx, cy + 22, 5, false);
    }

    // ------------------------------------------------------------------ a run's pause menu

    /**
     * A fight's pause menu, laid out like the title screens: the heading and how it's going on the left, the rows (the
     * volumes as sliders), and a card on the right with the build you've put together so far.
     */
    void drawRunPause(Graphics2D g, World w, int width, int height) {
        MenuStyle.antialias(g);
        MenuStyle.veil(g, width, height, 165);
        g.setPaint(new GradientPaint(0, 0, new Color(6, 6, 16, 150), (float) (width * 0.55), 0, new Color(6, 6, 16, 0)));
        g.fillRect(0, 0, (int) (width * 0.55) + 1, height);
        Run run = w.run;
        Player p = w.player;
        double s = MenuStyle.scale(height), x0 = MenuStyle.left(width);

        double base = height * 0.2 + 12 * s;
        java.awt.geom.Rectangle2D hb = MenuStyle.heading(g, "PAUSED", x0, base, 76 * s, s).getBounds2D();
        MenuStyle.ruled(g, run.challenge.title, hb.getCenterX(), base + 36 * s, hb.getX(), Math.max(hb.getMaxX(), hb.getX() + 10), 17 * s, s);
        g.setFont(MenuStyle.serif(Font.ITALIC, 16 * s, 0));
        String status = "Level " + p.level + DOT + run.challenge.goalWord().toLowerCase() + " " + run.tally() + DOT + run.gold + " gold" + DOT
            + run.loot.size() + " item" + (run.loot.size() == 1 ? "" : "s") + " found";
        MenuStyle.shadowed(g, status, x0, base + 66 * s, new Color(214, 206, 228, 220));

        String[] rows = World.FIGHT_PAUSE;
        double y0 = base + 128 * s, rowH = 52 * s, rowSize = 27 * s;
        int sel = Math.min(w.pauseCursor, rows.length - 1);
        boolean warn = rows[sel].equals("RETREAT") && w.confirmRetreat;
        MenuStyle.rows(g, pauseGlide, rows, null, null, sel, warn, x0, y0, rowH, rowSize, 470 * s, s, w.time);
        g.setFont(MenuStyle.serif(Font.BOLD, rowSize, 0.08));
        FontMetrics fm = g.getFontMetrics();
        int[] volumes = {w.audio.music, w.audio.sfx};
        for (int i = 1; i <= 2; i++) {
            double lx = x0 + pauseGlide.slide(i) + fm.stringWidth(rows[i]) + 22 * s, ly = y0 + i * rowH + rowH * 0.18 - 9 * s;
            MenuStyle.slider(g, lx, ly, volumes[i - 1], sel == i, s);
        }
        String[] info = {
            "Back to the fight.",
            "The music's volume. LEFT and RIGHT change it; M mutes everything.",
            "The volume of the sound effects. LEFT and RIGHT change it.",
            warn ? "Press ENTER again to leave. You keep the gold and items you've found, but the challenge isn't cleared."
                : "Leave the fight and go back to " + Worlds.home(run.challenge.world) + ". You keep the gold and items you've found.",
        };
        MenuStyle.infoLine(g, info[sel], x0, y0 + rows.length * rowH + 12 * s, 380 * s, s, warn);
        MenuStyle.keys(g, x0, height - 30 * s, s, new String[][]{{"W", "S"}, {"A", "D"}, {"ENTER"}, {"ESC"}}, new String[]{"Navigate", "Volume", "Select", "Resume"});
        drawBuildCard(g, p, width, height, s);
    }

    /** The pause menu's right-hand card: your skills and passives (with ranks), and what they add up to. */
    private void drawBuildCard(Graphics2D g, Player p, int width, int height, double s) {
        double cw = 372 * s, ch = 330 * s, x = width - cw - 40 * s, y = (height - ch) / 2 + 20 * s;     // (sized to what's in it)
        MenuStyle.card(g, x, y, cw, ch, null);
        double px = x + 20 * s, py = y + 28 * s;
        MenuStyle.label(g, "YOUR BUILD", px, py, s, GOLD);
        MenuStyle.label(g, "SKILLS", px, py + 30 * s, s, MenuStyle.DIM);
        double sx = px, sy = py + 42 * s;
        int skills = 0;
        for (Perk k : Perk.values()) {
            if (k.kind != Perk.Kind.SKILL || p.perk[k.ordinal()] == 0) continue;
            slot(g, k, p.perk[k.ordinal()], sx, sy, 50 * s, 0);
            sx += 56 * s;
            skills++;
        }
        if (skills == 0) {
            g.setFont(MenuStyle.serif(Font.ITALIC, 14 * s, 0));
            MenuStyle.shadowed(g, "Only your sword, so far.", px, sy + 30 * s, MenuStyle.DIM);
        }
        MenuStyle.label(g, "PASSIVES", px, sy + 76 * s, s, MenuStyle.DIM);
        sx = px;
        double sy2 = sy + 88 * s;
        int passives = 0;
        for (Perk k : Perk.values()) {
            if (k.kind != Perk.Kind.PASSIVE || p.perk[k.ordinal()] == 0) continue;
            slot(g, k, p.perk[k.ordinal()], sx, sy2, 44 * s, 0);
            sx += 50 * s;
            passives++;
        }
        if (passives == 0) {
            g.setFont(MenuStyle.serif(Font.ITALIC, 14 * s, 0));
            MenuStyle.shadowed(g, "None yet.", px, sy2 + 28 * s, MenuStyle.DIM);
        }

        double ty = sy2 + 82 * s;
        g.setColor(new Color(255, 214, 120, 60));
        g.setStroke(new BasicStroke(1f));
        g.draw(new java.awt.geom.Line2D.Double(px, ty - 18 * s, x + cw - 20 * s, ty - 18 * s));
        String[][] stats = {
            {"Melee damage", String.format(Locale.ROOT, "x%.2f", p.meleeMult)}, {"Skill power", String.format(Locale.ROOT, "x%.2f", p.spellPower)},
            {"Attack speed", String.format(Locale.ROOT, "x%.2f", p.attackSpeed)}, {"Combo", p.comboMax + " hits"},
            {"Crit chance", Math.round(p.critChance * 100) + "%"}, {"Damage taken", p.armor > 0 ? "-" + Math.round(p.armor * 100) + "%" : "normal"},
            {"Move speed", String.valueOf(Math.round(p.moveSpeed))}, {"Pickup range", String.valueOf(Math.round(p.magnet))},
        };
        double col = (cw - 40 * s) / 2;
        for (int i = 0; i < stats.length; i++) {
            double lx = px + (i % 2) * col, ly = ty + (i / 2) * 22 * s;
            g.setFont(MenuStyle.sans(Font.PLAIN, 13 * s));
            MenuStyle.shadowed(g, stats[i][0], lx, ly, MenuStyle.DIM);
            g.setFont(MenuStyle.sans(Font.BOLD, 13 * s));
            FontMetrics fm = g.getFontMetrics();
            MenuStyle.shadowed(g, stats[i][1], lx + col - 14 * s - fm.stringWidth(stats[i][1]), ly, MenuStyle.TEXT);
        }
    }

    // ------------------------------------------------------------------ the end of a run

    /**
     * The results: VICTORY in gold, or DEFEATED / RETREATED in blood red; a row of stat tiles; what you take home
     * (gold, skill points for a win) and every item found as a small card tinted in its rarity.
     */
    void drawRunEnd(Graphics2D g, World w, int width, int height) {
        MenuStyle.antialias(g);
        MenuStyle.veil(g, width, height, 190);
        Run run = w.run;
        Player p = w.player;
        double s = MenuStyle.scale(height), cx = width / 2.0;
        boolean won = run.outcome == Run.Outcome.VICTORY;

        double base = height * 0.17 + 8 * s;
        String head = won ? "VICTORY" : run.outcome == Run.Outcome.RETREAT ? "RETREATED" : "DEFEATED";
        java.awt.geom.Rectangle2D hb = MenuStyle.headingCentred(g, head, cx, base, 80 * s, s, !won).getBounds2D();
        MenuStyle.ruled(g, won ? run.challenge.title + (run.firstClear ? DOT + "CLEARED" : DOT + "CLEARED AGAIN") : run.challenge.title,
            cx, base + 38 * s, hb.getX() - 80 * s, hb.getMaxX() + 80 * s, 16 * s, s);
        g.setFont(MenuStyle.serif(Font.ITALIC, 17 * s, 0));
        String home = Worlds.home(run.challenge.world);
        MenuStyle.centred(g, won ? "The Blight withers. The light takes you back to " + home + ", with everything you found."
                : "You make it back to " + home + ". Everything you found is yours to keep.", cx, base + 68 * s, new Color(214, 206, 228, 225));

        String[][] tiles = {
            {Run.clock(run.time), "TIME"}, {String.valueOf(p.level), "LEVEL"}, {String.valueOf(w.kills), "KILLS"},
            {run.tally(), run.challenge.goalWord()}, {String.valueOf(run.elitesKilled), "ELITES"}, {run.dangerLabel(), "DANGER"},
        };
        double tw = 118 * s, ty = base + 104 * s, tx0 = cx - tiles.length * tw / 2;
        for (int i = 0; i < tiles.length; i++) {
            double tcx = tx0 + i * tw + tw / 2;
            g.setFont(MenuStyle.serif(Font.BOLD, tiles[i][0].length() > 6 ? 20 * s : 30 * s, 0.02));
            MenuStyle.centred(g, tiles[i][0], tcx, ty + 28 * s, Color.WHITE);
            g.setFont(MenuStyle.caps(10 * s));
            MenuStyle.centred(g, tiles[i][1], tcx, ty + 48 * s, MenuStyle.DIM);
            if (i > 0) {
                g.setColor(new Color(255, 214, 120, 70));
                g.fill(new Rectangle2D.Double(tx0 + i * tw, ty + 6 * s, 1, 44 * s));
            }
        }

        // what you take home
        double gy = ty + 94 * s;
        g.setFont(MenuStyle.serif(Font.BOLD, 26 * s, 0.03));
        String goldText = "+" + (run.gold + run.rewardGold) + " gold" + (run.rewardSkillPoints > 0 ? "      +" + run.rewardSkillPoints + " skill point" + (run.rewardSkillPoints == 1 ? "" : "s") : "");
        FontMetrics gm = g.getFontMetrics();
        double gw = gm.stringWidth(goldText) + 30 * s;
        Art.frame("run.coin", System.nanoTime() / 1e9, 6).draw(g, cx - gw / 2 + 8 * s, gy - 8 * s, 3.4 * s, false);
        MenuStyle.shadowed(g, goldText, cx - gw / 2 + 30 * s, gy, GOLD);
        if (won) {
            g.setFont(MenuStyle.sans(Font.PLAIN, 13 * s));
            MenuStyle.centred(g, run.gold + " picked up" + DOT + run.rewardGold + " reward" + (run.rewardSkillPoints > 0 ? DOT + "spend skill points with " + Worlds.trainer(run.challenge.world) : ""),
                cx, gy + 22 * s, MenuStyle.DIM);
        }

        List<Item> all = run.loot;
        double ly = gy + 46 * s;
        if (all.isEmpty()) {
            g.setFont(MenuStyle.serif(Font.ITALIC, 16 * s, 0));
            MenuStyle.centred(g, "No items this time. Chests, caches and the guardian carry them.", cx, ly + 20 * s, MenuStyle.DIM);
        } else {
            MenuStyle.ruled(g, "LOOT", cx, ly + 8 * s, cx - 300 * s, cx + 300 * s, 14 * s, s);
            ly += 24 * s;
            int cols = Math.min(3, all.size());
            double colW = 318 * s, rowH = 62 * s;
            int shown = Math.min(9, all.size());
            for (int i = 0; i < shown; i++) {
                Item it = all.get(i);
                double ix = cx - cols * colW / 2 + (i % cols) * colW, iy = ly + (i / cols) * rowH;
                itemChip(g, it, ix + 6 * s, iy, colW - 12 * s, rowH - 10 * s, null, s);
            }
        }
        String[][] groups = {{"ENTER"}};
        String[] labels = {"Back to " + Worlds.home(run.challenge.world)};
        java.awt.Composite saved = g.getComposite();
        if (w.overTimer > 0) g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.35f));   // not yet
        MenuStyle.keys(g, cx - MenuStyle.keysWidth(g, s, groups, labels) / 2, height - 30 * s, s, groups, labels);
        g.setComposite(saved);
    }

    /** A found item as a small glass card: its icon in a slot frame, its name in its rarity colour, what it is and its main stat. */
    private void itemChip(Graphics2D g, Item it, double x, double y, double w, double h, String note, double s) {
        MenuStyle.card(g, x, y, w, h, it.rarity.color);
        double box = h - 14 * s;
        TitleScreen.slotBox(g, it, it.slot, x + 7 * s, y + 7 * s, box, s);
        double tx = x + box + 18 * s;
        g.setFont(MenuStyle.sans(Font.BOLD, 14 * s));
        MenuStyle.shadowed(g, it.name, tx, y + h / 2 - 3 * s, it.rarity.color);
        g.setFont(MenuStyle.caps(9 * s));
        MenuStyle.shadowed(g, it.rarity.label.toUpperCase(), tx, y + h / 2 + 12 * s, MenuStyle.DIM);          // (the icon says which slot)
        if (note != null) {
            FontMetrics fm = g.getFontMetrics();
            MenuStyle.shadowed(g, note, x + w - 10 * s - fm.stringWidth(note), y + 16 * s, GOLD);
        }
        g.setFont(MenuStyle.sans(Font.PLAIN, 12 * s));
        FontMetrics fm = g.getFontMetrics();
        String stat = it.lines().get(0);
        MenuStyle.shadowed(g, stat, x + w - 10 * s - fm.stringWidth(stat), y + h / 2 + 12 * s, new Color(200, 228, 200));
    }

    // ------------------------------------------------------------------ perk icons

    /**
     * A small vector picture for a perk, centred on (cx, cy), about {@code size} across. Evolved skills get a gold ring.
     */
    void drawIcon(Graphics2D g, Perk k, double cx, double cy, double size, boolean evolved) {
        AffineTransform saved = g.getTransform();
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(cx, cy);
        g.scale(size / 32.0, size / 32.0);
        Color c = k.color;
        BasicStroke thick = new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        BasicStroke thin = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        if (evolved) {
            g.setColor(new Color(255, 214, 80, 70));
            g.fill(new Ellipse2D.Double(-16, -16, 32, 32));
        }
        switch (k) {
            case CRESCENT_WAVE -> {
                Path2D moon = new Path2D.Double();
                moon.append(new Arc2D.Double(-12, -13, 24, 26, -70, 140, Arc2D.OPEN), false);
                moon.append(new Arc2D.Double(-6, -9, 14, 18, 70, -140, Arc2D.OPEN), true);
                moon.closePath();
                g.setColor(c);
                g.fill(moon);
                g.setColor(Color.WHITE);
                g.setStroke(thin);
                g.draw(new Arc2D.Double(-12, -13, 24, 26, -60, 120, Arc2D.OPEN));
            }
            case FIREBALL -> {
                Path2D tail = new Path2D.Double();
                tail.moveTo(-14, -12);
                tail.quadTo(-4, -2, 2, -6);
                tail.lineTo(8, 6);
                tail.quadTo(-2, 2, -14, -12);
                g.setColor(new Color(220, 70, 30));
                g.fill(tail);
                g.setColor(c);
                g.fill(new Ellipse2D.Double(-5, -5, 18, 18));
                g.setColor(new Color(255, 230, 120));
                g.fill(new Ellipse2D.Double(0, 0, 9, 9));
            }
            case LIGHTNING -> {
                Path2D bolt = new Path2D.Double();
                bolt.moveTo(4, -15);
                bolt.lineTo(-8, 2);
                bolt.lineTo(-1, 2);
                bolt.lineTo(-5, 15);
                bolt.lineTo(9, -3);
                bolt.lineTo(2, -3);
                bolt.closePath();
                g.setColor(c);
                g.fill(bolt);
                g.setColor(new Color(120, 100, 20));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(bolt);
            }
            case ICE_STORM -> {
                g.setColor(c);
                g.setStroke(thick);
                for (int i = 0; i < 3; i++) {
                    double a = i * Math.PI / 3;
                    g.draw(new Line2D.Double(-Math.cos(a) * 13, -Math.sin(a) * 13, Math.cos(a) * 13, Math.sin(a) * 13));
                }
                g.setColor(Color.WHITE);
                g.setStroke(thin);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3, bx = Math.cos(a) * 8, by = Math.sin(a) * 8;
                    g.draw(new Line2D.Double(bx, by, bx + Math.cos(a + 0.8) * 4, by + Math.sin(a + 0.8) * 4));
                }
            }
            case ORBIT_BLADES -> {
                g.setColor(Util.alpha(c, 0.5));
                g.setStroke(thin);
                g.draw(new Ellipse2D.Double(-11, -11, 22, 22));
                g.setColor(c);
                g.setStroke(thick);
                for (int i = 0; i < 3; i++) {
                    double a = i * Math.PI * 2 / 3 - 0.3, bx = Math.cos(a) * 11, by = Math.sin(a) * 11;
                    g.draw(new Line2D.Double(bx, by, bx + Math.cos(a + Math.PI / 2) * 8, by + Math.sin(a + Math.PI / 2) * 8));
                }
                g.fill(new Ellipse2D.Double(-3, -3, 6, 6));
            }
            case HOLY_AURA -> {
                g.setColor(Util.alpha(c, 0.35));
                g.fill(new Ellipse2D.Double(-14, -14, 28, 28));
                g.setColor(c);
                g.setStroke(thin);
                g.draw(new Ellipse2D.Double(-14, -14, 28, 28));
                g.draw(new Ellipse2D.Double(-8, -8, 16, 16));
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(-3, -3, 6, 6));
            }
            case HEALING -> {
                g.setColor(c);
                g.fill(new RoundRectangle2D.Double(-4, -13, 8, 26, 3, 3));
                g.fill(new RoundRectangle2D.Double(-13, -4, 26, 8, 3, 3));
            }
            case ROLL -> {
                g.setColor(c);
                g.setStroke(thick);
                g.draw(new Arc2D.Double(-11, -11, 22, 22, 120, 260, Arc2D.OPEN));
                Path2D head = new Path2D.Double();
                head.moveTo(-11, -9);
                head.lineTo(-3, -13);
                head.lineTo(-5, -4);
                head.closePath();
                g.fill(head);
            }
            case COMBO -> {
                g.setColor(c);
                for (int i = 0; i < 3; i++) g.fill(new Ellipse2D.Double(-13 + i * 9, 5, 7, 7));
                g.setStroke(thick);
                g.draw(new Line2D.Double(-12, -2, 12, -12));
            }
            case BLADE -> sword(g, c, thick);
            case HASTE -> {
                g.setColor(c);
                g.setStroke(thick);
                for (int i = 0; i < 2; i++) {
                    Path2D chev = new Path2D.Double();
                    chev.moveTo(-11 + i * 9, -10);
                    chev.lineTo(-3 + i * 9, 0);
                    chev.lineTo(-11 + i * 9, 10);
                    g.draw(chev);
                }
            }
            case REACH -> {
                g.setColor(c);
                g.setStroke(thick);
                g.draw(new Line2D.Double(-13, 0, 13, 0));
                g.draw(new Line2D.Double(-13, 0, -7, -6));
                g.draw(new Line2D.Double(-13, 0, -7, 6));
                g.draw(new Line2D.Double(13, 0, 7, -6));
                g.draw(new Line2D.Double(13, 0, 7, 6));
            }
            case VAMPIRE -> {
                Path2D drop = new Path2D.Double();
                drop.moveTo(0, -14);
                drop.quadTo(12, 4, 0, 13);
                drop.quadTo(-12, 4, 0, -14);
                g.setColor(c);
                g.fill(drop);
                g.setColor(new Color(255, 190, 200));
                g.fill(new Ellipse2D.Double(-5, 0, 4, 6));
            }
            case POWER -> {
                g.setColor(c);
                g.fill(star(0, 0, 14, 5, 4));
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(-3, -3, 6, 6));
            }
            case CASTING -> {
                g.setColor(c);
                g.setStroke(thick);
                g.draw(new Ellipse2D.Double(-12, -12, 24, 24));
                g.draw(new Line2D.Double(0, 0, 0, -8));
                g.draw(new Line2D.Double(0, 0, 6, 3));
            }
            case VITALITY -> {
                Path2D heart = new Path2D.Double();
                heart.moveTo(0, 12);
                heart.curveTo(-18, 0, -8, -16, 0, -6);
                heart.curveTo(8, -16, 18, 0, 0, 12);
                g.setColor(c);
                g.fill(heart);
            }
            case SWIFT -> {
                g.setColor(c);
                g.setStroke(thick);
                for (int i = 0; i < 3; i++) g.draw(new Line2D.Double(-13 + i * 3, -8 + i * 8, 8 + i * 3, -8 + i * 8));
                g.fill(new Ellipse2D.Double(7, -12, 8, 8));
            }
            case MAGNET -> {
                g.setColor(c);
                g.setStroke(new BasicStroke(6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                g.draw(new Arc2D.Double(-10, -12, 20, 20, 180, 180, Arc2D.OPEN));
                g.draw(new Line2D.Double(-10, -2, -10, -12));
                g.draw(new Line2D.Double(10, -2, 10, -12));
                g.setColor(Color.WHITE);
                g.fill(new Rectangle2D.Double(-13, -15, 6, 4));
                g.fill(new Rectangle2D.Double(7, -15, 6, 4));
            }
            case WISDOM -> {
                g.setColor(c);
                Path2D book = new Path2D.Double();
                book.moveTo(0, -8);
                book.lineTo(-14, -12);
                book.lineTo(-14, 10);
                book.lineTo(0, 13);
                book.lineTo(14, 10);
                book.lineTo(14, -12);
                book.closePath();
                g.fill(book);
                g.setColor(new Color(20, 30, 60));
                g.setStroke(thin);
                g.draw(new Line2D.Double(0, -8, 0, 13));
            }
            case ARMOR -> {
                Path2D shield = new Path2D.Double();
                shield.moveTo(0, -14);
                shield.lineTo(12, -9);
                shield.quadTo(12, 7, 0, 14);
                shield.quadTo(-12, 7, -12, -9);
                shield.closePath();
                g.setColor(c);
                g.fill(shield);
                g.setColor(new Color(90, 100, 120));
                g.setStroke(thin);
                g.draw(new Line2D.Double(0, -10, 0, 10));
            }
            case REGEN -> {
                Path2D leaf = new Path2D.Double();
                leaf.moveTo(-10, 12);
                leaf.quadTo(-12, -12, 12, -12);
                leaf.quadTo(12, 10, -10, 12);
                g.setColor(c);
                g.fill(leaf);
                g.setColor(new Color(40, 110, 50));
                g.setStroke(thin);
                g.draw(new Line2D.Double(-10, 12, 6, -6));
            }
            case CRIT -> {
                g.setColor(c);
                g.setStroke(thick);
                g.draw(new Ellipse2D.Double(-10, -10, 20, 20));
                g.draw(new Line2D.Double(0, -15, 0, -5));
                g.draw(new Line2D.Double(0, 5, 0, 15));
                g.draw(new Line2D.Double(-15, 0, -5, 0));
                g.draw(new Line2D.Double(5, 0, 15, 0));
            }
        }
        g.setTransform(saved);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    private static void sword(Graphics2D g, Color c, BasicStroke thick) {
        g.setColor(c);
        g.setStroke(thick);
        g.draw(new Line2D.Double(-10, 10, 12, -12));
        g.setColor(new Color(255, 214, 80));
        g.draw(new Line2D.Double(-12, 2, -2, 12));
        g.setColor(new Color(140, 90, 50));
        g.draw(new Line2D.Double(-10, 10, -14, 14));
    }

    private static Shape star(double cx, double cy, double outer, double inner, int points) {
        Path2D s = new Path2D.Double();
        for (int i = 0; i < points * 2; i++) {
            double a = -Math.PI / 2 + i * Math.PI / points, r = i % 2 == 0 ? outer : inner;
            if (i == 0) s.moveTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
            else s.lineTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
        }
        s.closePath();
        return s;
    }

    // ------------------------------------------------------------------ helpers

    private void bar(Graphics2D g, double x, double y, double w, double h, double frac, Color fill) {
        g.setColor(new Color(20, 20, 22, 210));
        g.fill(new Rectangle2D.Double(x - 1.5, y - 1.5, w + 3, h + 3));
        g.setColor(fill);
        g.fill(new Rectangle2D.Double(x, y, w * Util.clamp(frac, 0, 1), h));
    }

    private void centered(Graphics2D g, String s, double cx, double baseline, Color c) {
        FontMetrics fm = g.getFontMetrics();
        float x = (float) (cx - fm.stringWidth(s) / 2.0);
        if (c.getRed() + c.getGreen() + c.getBlue() > 300) {
            g.setColor(new Color(0, 0, 0, Math.min(200, c.getAlpha())));
            g.drawString(s, x + 1.5f, (float) baseline + 1.5f);
        }
        g.setColor(c);
        g.drawString(s, x, (float) baseline);
    }

    /** Word-wraps {@code text} into lines at most {@code maxWidth} wide; returns the last line's baseline. */
    private double wrapped(Graphics2D g, String text, double x, double y, double maxWidth, double lineHeight, boolean centre) {
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(test) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            double lx = centre ? x + (maxWidth - fm.stringWidth(l)) / 2 : x;
            g.drawString(l, (float) lx, (float) (y + i * lineHeight));
        }
        return y + (lines.size() - 1) * lineHeight;
    }
}
