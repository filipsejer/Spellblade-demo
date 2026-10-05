package game;

import static game.MenuStyle.DIM;
import static game.MenuStyle.DOT;
import static game.MenuStyle.GOLD;
import static game.MenuStyle.TEXT;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * The main menu, on the title backdrop ({@link World#showTitle}: the hero in a forest clearing with the horde
 * circling). The scene is graded to dusk with a pool of warm light on the hero and fireflies drifting up; the left side
 * darkens to hold the lettering and the rows (see {@link MenuStyle} for the shared look), and a card in the corner
 * shows your saved game. The save slots screen uses the same backdrop.
 */
final class TitleScreen {
    private final MenuStyle.Glide mainGlide = new MenuStyle.Glide();
    private BufferedImage lighting;
    private int lightX, lightY;

    // ------------------------------------------------------------------ the backdrop's lighting

    /**
     * Grades the scene to dusk: everything darkened and cooled, a pool of warm light where the hero stands, the left
     * side sunk into shadow for the menu, and fireflies. Painted once (it never changes) and stamped each frame.
     */
    void drawBackdrop(Graphics2D g, World w, int width, int height) {
        Util.Vec hero = Renderer.toScreen(w, w.player.x, w.player.y, width, height);
        int hx = (int) Math.round(hero.x()), hy = (int) Math.round(hero.y() - 20 * Renderer.zoom(w));
        if (lighting == null || lighting.getWidth() != width || lighting.getHeight() != height || lightX != hx || lightY != hy) {
            lighting = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
            Graphics2D lg = lighting.createGraphics();
            paintLighting(lg, hx, hy, width, height);
            lg.dispose();
            lightX = hx;
            lightY = hy;
        }
        g.drawImage(lighting, 0, 0, null);
        fireflies(g, w.time, width, height);
    }

    private static void paintLighting(Graphics2D g, double hx, double hy, int width, int height) {
        g.setColor(new Color(14, 16, 48, 120));                                        // dusk
        g.fillRect(0, 0, width, height);
        float reach = (float) (Math.max(width, height) * 0.8);
        g.setPaint(new RadialGradientPaint((float) hx, (float) (hy - 20), reach, new float[]{0f, 0.18f, 0.5f, 1f},
            new Color[]{new Color(0, 0, 0, 0), new Color(4, 4, 16, 30), new Color(4, 4, 18, 140), new Color(2, 2, 10, 215)}));
        g.fillRect(0, 0, width, height);
        Composite saved = g.getComposite();                                            // warm light on the hero
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g.setPaint(new RadialGradientPaint((float) hx, (float) hy, 260f, new float[]{0f, 1f},
            new Color[]{new Color(255, 190, 110, 90), new Color(255, 170, 80, 0)}));
        g.fill(new Ellipse2D.Double(hx - 260, hy - 260, 520, 520));
        g.setComposite(saved);
        float side = (float) (width * 0.62);                                           // the menu's side, sunk into shadow
        g.setPaint(new LinearGradientPaint(0, 0, side, 0, new float[]{0f, 0.5f, 1f},
            new Color[]{new Color(6, 6, 16, 240), new Color(6, 6, 16, 185), new Color(6, 6, 16, 0)}));
        g.fillRect(0, 0, (int) side + 1, height);
    }

    /** Each firefly's glow, baked once (drawn afresh, 88 soft circles a frame kept the menu surprisingly busy). */
    private static final Baked FIREFLY = new Baked();

    /** Small lights drifting up and twinkling, the same ones every time (placed by index, moved by the clock). */
    private static void fireflies(Graphics2D g, double t, int width, int height) {
        Composite saved = g.getComposite();
        for (int i = 0; i < 44; i++) {
            double fx = frac(Math.sin(i * 12.9898) * 43758.5453), fy = frac(Math.sin(i * 78.233) * 12345.678), fz = frac(Math.sin(i * 3.17) * 999.13);
            double x = fx * width + Math.sin(t * (0.3 + fy * 0.5) + i) * 28;
            double y = height + 30 - ((t * (10 + fy * 22) + fz * (height + 60)) % (height + 60));
            double a = 0.25 + 0.75 * Math.abs(Math.sin(t * (0.8 + fy * 1.7) + i * 1.3));
            Color c = i % 5 == 0 ? new Color(170, 255, 190) : new Color(255, 214, 130);
            double halo = 5 + fz * 4;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));   // it pulses
            FIREFLY.draw(g, i, x - halo, y - halo, halo * 2, halo * 2, bg -> {
                bg.setColor(Util.alpha(c, 0.13));
                bg.fill(new Ellipse2D.Double(0, 0, halo * 2, halo * 2));
                bg.setColor(Util.alpha(c, 0.9));
                bg.fill(new Ellipse2D.Double(halo - 1.4, halo - 1.4, 2.8, 2.8));
            });
        }
        g.setComposite(saved);
    }

    private static double frac(double v) { return v - Math.floor(v); }

    /** A page's heading: gold lettering, spaced capitals between rules under it, and an italic line under that. Returns where the rows start. */
    private static double title(Graphics2D g, String heading, double size, String sub, String tagline, double x0, int height, double s, double t, boolean glint) {
        double base = height * 0.2 + 30 * s;
        Shape shape = MenuStyle.heading(g, heading, x0, base, size * s, s);
        if (glint) MenuStyle.glint(g, shape, t, 5.5);
        Rectangle2D b = shape.getBounds2D();
        double sy = base + (size >= 80 ? 40 : 34) * s;
        // the lines under the heading are centred on it, unless one is wider than it: then they all start at its left edge
        g.setFont(MenuStyle.serif(Font.BOLD, 21 * s, 0.62));
        double subW = g.getFontMetrics().stringWidth(sub);
        g.setFont(MenuStyle.serif(Font.ITALIC, 17 * s, 0));
        FontMetrics tm = g.getFontMetrics();
        double tagW = tm.stringWidth(tagline);
        boolean centred = subW <= b.getWidth() && tagW <= b.getWidth() + 40 * s;
        double cx = b.getCenterX();
        if (centred) MenuStyle.ruled(g, sub, cx, sy, b.getX(), b.getMaxX(), 21 * s, s);
        else MenuStyle.ruled(g, sub, b.getX() + subW / 2, sy, b.getX(), b.getX(), 21 * s, s);        // (no room for the rules)
        g.setFont(MenuStyle.serif(Font.ITALIC, 17 * s, 0));
        MenuStyle.shadowed(g, tagline, centred ? cx - tagW / 2.0 : b.getX(), sy + 32 * s, new Color(214, 206, 228, 220));
        return base + 118 * s;
    }

    // ------------------------------------------------------------------ the main menu

    void draw(Graphics2D g, World w, int width, int height) {
        MenuStyle.antialias(g);
        drawBackdrop(g, w, width, height);
        double s = MenuStyle.scale(height), x0 = MenuStyle.left(width);
        double y0 = title(g, "SPELLBLADE", 92, "THE BLIGHT", "Explore the forest.  Answer the call.  Burn out the Blight.", x0, height, s, w.time, true);

        int sel = w.menuCursor;
        String[] details = new String[World.MAIN_MENU.length];
        boolean[] disabled = new boolean[World.MAIN_MENU.length];
        disabled[1] = disabled[2] = w.savedGame == null;
        if (w.savedGame != null) details[1] = "save " + w.savedGame.slot();
        double rowH = 50 * s;
        MenuStyle.rows(g, mainGlide, World.MAIN_MENU, details, disabled, sel, false, x0, y0, rowH, 28 * s, 470 * s, s, w.time);

        String[] info = {
            "Begin the story in one of " + Saves.COUNT + " save slots: a wandering swordsman, a camp in the woods, and a sickness in the trees.",
            w.savedGame != null ? "Pick up the game you played last, right where you saved it." : "No saved game yet. Start a new one.",
            w.savedGame != null ? "Choose any of your saved games to pick up." : "No saved games yet. Start a new one.",
            "Leave the clearing. The forest will wait.",
        };
        MenuStyle.infoLine(g, info[sel], x0, y0 + World.MAIN_MENU.length * rowH + 12 * s, 380 * s, s, false);
        if (w.savedGame != null) drawLegend(g, w, width, height, s);
        MenuStyle.keys(g, x0, height - 30 * s, s, new String[][]{{"W", "S"}, {"ENTER"}, {"M"}}, new String[]{"Navigate", "Select", "Mute"});
    }

    /** The corner card: the saved game's gold, what's worn (six slot icons in their rarity colours), and how far along it is. */
    private void drawLegend(Graphics2D g, World w, int width, int height, double s) {
        Profile p = w.profile;
        Saves.Summary sv = w.savedGame;
        String line1 = sv.world() + DOT + sv.done() + " / " + sv.open() + " challenges" + DOT + sv.skillPoints() + " SP";
        String line2 = p.runs + (p.runs == 1 ? " fight" : " fights") + DOT + p.victories + " won" + DOT + p.items.size() + " items";
        g.setFont(MenuStyle.sans(Font.PLAIN, 13 * s));
        FontMetrics lm = g.getFontMetrics();
        double cw = Math.max(318 * s, Math.max(lm.stringWidth(line1), lm.stringWidth(line2)) + 38 * s), ch = 144 * s;   // wide enough for its text
        double x = width - cw - 28 * s, y = height - ch - 26 * s;
        MenuStyle.card(g, x, y, cw, ch, null);

        double px = x + 18 * s, py = y + 26 * s;
        MenuStyle.label(g, "SAVE " + sv.slot() + DOT + sv.when().toUpperCase(), px, py, s, new Color(255, 214, 120, 220));
        Art.frame("run.coin", w.time, 6).draw(g, x + cw - 92 * s, py + 1 * s, 2.5 * s, false);
        g.setFont(MenuStyle.sans(Font.BOLD, 15 * s));
        MenuStyle.shadowed(g, String.valueOf(p.gold), x + cw - 78 * s, py + 1 * s, GOLD);

        double slot = 38 * s, sx = px, sy = py + 14 * s;                                 // what's worn
        for (Item.Slot sl : Item.Slot.values()) {
            slotBox(g, p.equipped.get(sl), sl, sx, sy, slot, s);
            sx += slot + 9 * s;
        }
        g.setFont(MenuStyle.sans(Font.PLAIN, 13 * s));
        double ly = sy + slot + 24 * s;
        MenuStyle.shadowed(g, line1, px, ly, new Color(210, 205, 222));
        MenuStyle.shadowed(g, line2, px, ly + 20 * s, DIM);
    }

    // ------------------------------------------------------------------ the save slots

    private final MenuStyle.Glide slotGlide = new MenuStyle.Glide();

    /**
     * The save slots, for a new game or to load one: a card each, with where that game is, what's next, how far along
     * it is, its gold, skill points and worn gear, and when it was saved. The picked one glows gold (red while an
     * overwrite or a delete waits for its second press).
     */
    void drawSlots(Graphics2D g, World w, int width, int height) {
        MenuStyle.antialias(g);
        drawBackdrop(g, w, width, height);
        double s = MenuStyle.scale(height), x0 = MenuStyle.left(width);
        boolean fresh = w.slotsForNew;
        double y0 = title(g, fresh ? "NEW GAME" : "LOAD GAME", 64, "CHOOSE A SAVE SLOT",
            fresh ? "Every slot is a game of its own: its story, its gold, its gear." : "Pick up any of your games where you left it.", x0, height, s, w.time, false) - 44 * s;
        int n = Saves.COUNT, sel = Math.min(w.slotCursor, n - 1);
        slotGlide.step(sel, n, 0);                                                     // (the picked card slides out a little)
        double cw = Math.min(640 * s, width - x0 - 40 * s), rowH = 66 * s, gap = 8 * s;
        for (int i = 0; i < n; i++) {
            Saves.Summary sv = w.slots[i];
            double y = y0 + i * (rowH + gap), x = x0 + slotGlide.slide(i) * 0.5;
            boolean picked = i == sel;
            MenuStyle.card(g, x, y, cw, rowH, null);
            if (picked) {
                Color edge = w.confirmSlot ? MenuStyle.WARN : GOLD;
                g.setColor(Util.alpha(edge, 0.9));
                g.setStroke(new java.awt.BasicStroke((float) (2 * s)));
                g.draw(new RoundRectangle2D.Double(x, y, cw, rowH, 14 * s, 14 * s));
            }
            g.setFont(MenuStyle.serif(Font.BOLD, 30 * s, 0));                                 // the slot's number
            MenuStyle.shadowed(g, String.valueOf(i + 1), x + 22 * s, y + rowH * 0.68, picked ? GOLD : DIM);
            double tx = x + 66 * s, right = x + cw - 20 * s;
            if (sv == null) {
                g.setFont(MenuStyle.serif(Font.ITALIC, 19 * s, 0));
                MenuStyle.shadowed(g, "Empty", tx, y + 29 * s, picked ? TEXT : DIM);
                g.setFont(MenuStyle.sans(Font.PLAIN, 13 * s));
                MenuStyle.shadowed(g, fresh ? "A new adventure can start here." : "Nothing saved here.", tx, y + 50 * s, DIM);
                continue;
            }
            g.setFont(MenuStyle.caps(10 * s));
            FontMetrics cm = g.getFontMetrics();
            String when = sv.when().toUpperCase();
            MenuStyle.shadowed(g, when, right - cm.stringWidth(when), y + 20 * s, DIM);
            g.setFont(MenuStyle.serif(Font.BOLD, 17 * s, 0.03));
            MenuStyle.shadowed(g, sv.world(), tx, y + 22 * s, picked ? GOLD : TEXT);
            g.setFont(MenuStyle.serif(Font.ITALIC, 13 * s, 0));
            MenuStyle.shadowed(g, fit(g, sv.objective(), right - tx - 4 * s), tx, y + 40 * s, new Color(255, 226, 160, picked ? 235 : 170));
            g.setFont(MenuStyle.sans(Font.PLAIN, 12.5 * s));                                  // how far along, and what it owns
            double ly = y + 57 * s;
            Art.frame("run.coin", w.time, 6).draw(g, tx + 6 * s, ly - 1 * s, 2 * s, false);
            String stats = sv.profile().gold + DOT + sv.skillPoints() + " SP" + DOT + sv.done() + " / " + sv.open() + " challenges cleared";
            MenuStyle.shadowed(g, stats, tx + 16 * s, ly, DIM);
            double box = 18 * s, bx = right - Item.Slot.values().length * (box + 4 * s) + 4 * s;
            for (Item.Slot sl : Item.Slot.values()) {
                slotBox(g, sv.profile().equipped.get(sl), sl, bx, ly - box + 4 * s, box, s);
                bx += box + 4 * s;
            }
        }
        Saves.Summary here = w.slots[sel];
        String info;
        if (fresh) info = here == null ? "Start a new adventure in slot " + (sel + 1) + "."
            : w.confirmSlot ? "Press ENTER again to throw this game away, its gold, gear and story, and start over." : "This slot holds a game. Starting here throws it away.";
        else info = here == null ? "Nothing saved in this slot."
            : w.confirmSlot ? "Press X again to delete this game for good." : "Pick this game up where you saved it.";
        MenuStyle.infoLine(g, info, x0, y0 + n * (rowH + gap) + 10 * s, cw, s, w.confirmSlot);
        String[][] groups = fresh ? new String[][]{{"W", "S"}, {"ENTER"}, {"ESC"}} : new String[][]{{"W", "S"}, {"ENTER"}, {"X"}, {"ESC"}};
        String[] labels = fresh ? new String[]{"Choose", here != null ? "Overwrite" : "Start", "Back"} : new String[]{"Choose", "Load", "Delete", "Back"};
        MenuStyle.keys(g, x0, height - 30 * s, s, groups, labels);
    }

    /** The text cut short with an ellipsis if it's wider than {@code max}. */
    private static String fit(Graphics2D g, String text, double max) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= max) return text;
        String t = text;
        while (t.length() > 1 && fm.stringWidth(t + "...") > max) t = t.substring(0, t.length() - 1);
        return t.trim() + "...";
    }

    /** A square holding an equipment slot's icon, framed in the worn item's rarity colour (dim when empty). */
    static void slotBox(Graphics2D g, Item it, Item.Slot sl, double x, double y, double size, double s) {
        RoundRectangle2D box = new RoundRectangle2D.Double(x, y, size, size, 8, 8);
        g.setColor(new Color(24, 24, 38, 230));
        g.fill(box);
        g.setColor(it == null ? new Color(70, 70, 90) : it.rarity.color);
        g.setStroke(new java.awt.BasicStroke(it == null ? 1f : 1.8f));
        g.draw(box);
        Art.frames("item." + sl.name().toLowerCase())[0].draw(g, x + size / 2, y + size / 2, 2 * size / 38, false, 0, it == null ? 0.22f : 1f);
    }
}
