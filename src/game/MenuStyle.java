package game;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The look every screen shares (the main menu, the Armory, the pause menus, the trainer's and merchant's screens, the
 * level-up cards, the results): gold serif headings with a dark edge and a warm glow, a subtitle between two rules, rows of serif text with
 * a gold bar gliding to the selected one, key caps, and dark glass cards with a thin gold border. Everything is sized
 * by {@link #scale} so it grows and shrinks with the window.
 */
final class MenuStyle {
    private MenuStyle() {}

    static final Color GOLD = new Color(255, 214, 120);
    static final Color GOLD_DEEP = new Color(255, 180, 70);
    static final Color TEXT = new Color(218, 212, 230);
    static final Color DIM = new Color(165, 160, 182);
    static final Color ROW = new Color(208, 206, 222, 215);
    static final Color ROW_SELECTED = new Color(255, 232, 170);
    static final Color WARN = new Color(255, 150, 120);
    /** A middle dot with room either side, between the parts of a stats line. */
    static final String DOT = "  \u00b7  ";

    /** Serif faces with some gravitas, best first; the first one installed wins. */
    private static final String[] SERIFS = {"Cinzel", "Trajan Pro", "Palatino", "Palatino Linotype", "Book Antiqua", "Georgia"};
    static final String SERIF = pickSerif();

    private static String pickSerif() {
        try {
            Set<String> have = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            for (String s : SERIFS) if (have.contains(s)) return s;
        } catch (RuntimeException ignored) {
            // no font list (an odd headless setup): the logical serif font will do
        }
        return Font.SERIF;
    }

    static Font serif(int style, double size, double tracking) {
        return new Font(SERIF, style, 1).deriveFont(Map.of(TextAttribute.TRACKING, tracking, TextAttribute.SIZE, (float) size));
    }

    static Font sans(int style, double size) { return new Font(Font.SANS_SERIF, style, 1).deriveFont((float) size); }

    static Font caps(double size) {
        return new Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(Map.of(TextAttribute.TRACKING, 0.25, TextAttribute.SIZE, (float) size));
    }

    /** How big to draw things for this window: 1 at 720 pixels tall. */
    static double scale(int height) { return Util.clamp(height / 720.0, 0.8, 1.35); }

    /** Where the left-hand column (the logo, headings, rows) starts. */
    static double left(int width) { return Math.max(56, width * 0.07); }

    static void antialias(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    // ------------------------------------------------------------------ headings

    /** A painted heading, ready to stamp: its image, where the image sits relative to the text's origin, and its outline there. */
    private record Heading(BufferedImage image, int dx, int dy, Shape outline) {}

    private static final Map<String, Heading> HEADINGS = new HashMap<>();

    /**
     * Gold lettering with a warm glow, a drop shadow and a dark edge, its baseline starting at (x, base). Painted once
     * per text and size, then stamped. Returns the lettering's outline where it was drawn (for a glint, or to line
     * things up under it).
     */
    static Shape heading(Graphics2D g, String text, double x, double base, double size, double s) {
        return heading(g, text, x, base, size, s, false);
    }

    /** As {@link #heading(Graphics2D, String, double, double, double, double)}, in blood red instead of gold when {@code red}. */
    static Shape heading(Graphics2D g, String text, double x, double base, double size, double s, boolean red) {
        Heading h = paintedHeading(text, size, s, red);
        g.drawImage(h.image, (int) Math.round(x) + h.dx, (int) Math.round(base) + h.dy, null);
        return AffineTransform.getTranslateInstance(Math.round(x), Math.round(base)).createTransformedShape(h.outline);
    }

    /** A heading centred on {@code cx}. */
    static Shape headingCentred(Graphics2D g, String text, double cx, double base, double size, double s, boolean red) {
        Rectangle2D b = paintedHeading(text, size, s, red).outline.getBounds2D();
        return heading(g, text, cx - b.getWidth() / 2 - b.getX(), base, size, s, red);
    }

    private static Heading paintedHeading(String text, double size, double s, boolean red) {
        String key = text + "|" + Math.round(size * 10) + "|" + Math.round(s * 100) + (red ? "|red" : "");
        Heading h = HEADINGS.get(key);
        if (h == null) {
            Font f = serif(Font.BOLD, size, 0.03);
            Shape outline = new TextLayout(text, f, new FontRenderContext(null, true, true)).getOutline(null);
            Rectangle2D b = outline.getBounds2D();
            int pad = (int) Math.ceil(44 * s);
            BufferedImage img = new BufferedImage((int) b.getWidth() + pad * 2, (int) b.getHeight() + pad * 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D ig = img.createGraphics();
            antialias(ig);
            ig.translate(pad - b.getX(), pad - b.getY());
            paintGold(ig, outline, size / 92.0, red);        // (size already includes the window scale)
            ig.dispose();
            h = new Heading(img, (int) Math.floor(b.getX()) - pad, (int) Math.floor(b.getY()) - pad, outline);
            HEADINGS.put(key, h);
        }
        return h;
    }

    /** The gold treatment itself; {@code k} scales the glow and the edge with the lettering's size. */
    private static void paintGold(Graphics2D g, Shape shape, double k, boolean red) {
        Rectangle2D b = shape.getBounds2D();
        for (int i = 5; i >= 1; i--) {
            g.setStroke(new BasicStroke((float) (i * 7 * k), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(red ? new Color(255, 60, 40, 12) : new Color(255, 150, 50, 11));
            g.draw(shape);
        }
        AffineTransform saved = g.getTransform();
        g.translate(4 * k, 6 * k);
        g.setColor(new Color(0, 0, 0, 170));
        g.fill(shape);
        g.setTransform(saved);
        g.setStroke(new BasicStroke((float) Math.max(2, 5 * k), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(red ? new Color(40, 8, 8) : new Color(48, 22, 8));
        g.draw(shape);
        Color[] metal = red
            ? new Color[]{new Color(255, 225, 215), new Color(245, 120, 100), new Color(200, 50, 45), new Color(110, 18, 22)}
            : new Color[]{new Color(255, 248, 215), new Color(255, 212, 110), new Color(240, 158, 48), new Color(176, 84, 26)};
        g.setPaint(new LinearGradientPaint((float) b.getX(), (float) b.getY(), (float) b.getX(), (float) b.getMaxY(),
            new float[]{0f, 0.46f, 0.54f, 1f}, metal));
        g.fill(shape);
    }

    /**
     * Darkens the game underneath an overlay (the pause, a level-up, the results): an even veil plus deeper shadow toward
     * the edges, so whatever's drawn on top reads while the fight stays visible behind it.
     */
    static void veil(Graphics2D g, int width, int height, int alpha) {
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);   // a screen-sized rectangle needs no smooth edges, and filling
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);   // it with them is slow
        g.setColor(new Color(6, 6, 16, alpha));
        g.fillRect(0, 0, width, height);
        float r = (float) (Math.hypot(width, height) / 2);
        g.setPaint(new java.awt.RadialGradientPaint(width / 2f, height / 2f, r, new float[]{0f, 0.55f, 1f},
            new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 40), new Color(0, 0, 0, 170)}));
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    /** A light sweeping across a heading every {@code cycle} seconds. */
    static void glint(Graphics2D g, Shape shape, double t, double cycle) {
        double p = (t % cycle) / 1.3;
        if (p >= 1) return;
        Rectangle2D b = shape.getBounds2D();
        Shape clip = g.getClip();
        g.clip(shape);
        double gx = b.getX() - 120 + p * (b.getWidth() + 240);
        g.setPaint(new GradientPaint((float) (gx - 60), 0, new Color(255, 255, 255, 0), (float) gx, 0, new Color(255, 255, 255, 150), true));
        AffineTransform tilt = g.getTransform();
        g.shear(-0.35, 0);
        g.fill(new Rectangle2D.Double(gx - 60 + b.getMaxY() * 0.35, b.getY() - 10, 120, b.getHeight() + 20));
        g.setTransform(tilt);
        g.setClip(clip);
    }

    /** Spaced-out capitals centred on {@code cx} between two gold rules (running from {@code from} to {@code to}), a diamond at each inner end. */
    static void ruled(Graphics2D g, String text, double cx, double baseline, double from, double to, double size, double s) {
        g.setFont(serif(Font.BOLD, size, 0.62));
        FontMetrics fm = g.getFontMetrics();
        double sw = fm.stringWidth(text);
        shadowed(g, text, cx - sw / 2, baseline, GOLD);
        double gap = 16 * s, mid = baseline - fm.getAscent() * 0.36;
        g.setStroke(new BasicStroke((float) (1.6 * s)));
        if (cx - sw / 2 - gap > from + 4) {
            g.setPaint(new GradientPaint((float) from, 0, new Color(255, 214, 120, 0), (float) (cx - sw / 2 - gap), 0, GOLD));
            g.draw(new Line2D.Double(from, mid, cx - sw / 2 - gap, mid));
            diamond(g, cx - sw / 2 - gap + 2, mid, 4.5 * s, GOLD);
        }
        if (to > cx + sw / 2 + gap + 4) {
            g.setPaint(new GradientPaint((float) (cx + sw / 2 + gap), 0, GOLD, (float) to, 0, new Color(255, 214, 120, 0)));
            g.draw(new Line2D.Double(cx + sw / 2 + gap, mid, to, mid));
            diamond(g, cx + sw / 2 + gap - 2, mid, 4.5 * s, GOLD);
        }
    }

    // ------------------------------------------------------------------ rows

    /** One menu's animation: the selection bar's height, easing to the selected row, and each row's slide to the right. */
    static final class Glide {
        private double y = Double.NaN;
        private double[] slide = new double[0];
        private long last;

        /** Moves everything a frame's worth toward row {@code sel} of {@code rows}; returns the bar's height now. */
        double step(int sel, int rows, double targetY) {
            long now = System.nanoTime();
            double dt = last == 0 ? 0 : Math.min(0.1, (now - last) / 1e9);
            last = now;
            if (slide.length != rows) slide = new double[rows];
            y = Double.isNaN(y) ? targetY : y + (targetY - y) * Math.min(1, dt * 16);
            for (int i = 0; i < rows; i++) slide[i] += ((i == sel ? 18 : 0) - slide[i]) * Math.min(1, dt * 14);
            return y;
        }

        double slide(int i) { return i < slide.length ? slide[i] : 0; }
    }

    /**
     * A column of menu rows starting at (x0, y0), one every {@code rowH}: the gold bar behind the selected one, each
     * label in serif capitals, and an optional small detail after it. {@code disabled} rows are greyed, and {@code warn}
     * turns the selected label red (a destructive choice waiting for its confirming press).
     */
    static void rows(Graphics2D g, Glide glide, String[] labels, String[] details, boolean[] disabled, int sel, boolean warn,
                     double x0, double y0, double rowH, double fontSize, double barWidth, double s, double t) {
        double barY = glide.step(sel, labels.length, y0 + sel * rowH);
        highlight(g, x0, barY, rowH, barWidth, s, t);
        Font rowFont = serif(Font.BOLD, fontSize, 0.08);
        Font detailFont = sans(Font.PLAIN, 13 * s);
        for (int i = 0; i < labels.length; i++) {
            boolean selected = i == sel, off = disabled != null && disabled[i];
            double ty = y0 + i * rowH + rowH * 0.18, tx = x0 + glide.slide(i);
            Color c = off ? new Color(120, 118, 136, selected ? 200 : 150) : selected ? (warn ? WARN : ROW_SELECTED) : ROW;
            g.setFont(rowFont);
            shadowed(g, labels[i], tx, ty, c);
            if (details != null && details[i] != null && !details[i].isEmpty()) {
                double w = g.getFontMetrics().stringWidth(labels[i]);
                g.setFont(detailFont);
                shadowed(g, details[i], tx + w + 18 * s, ty - 3 * s, new Color(190, 185, 205, selected ? 230 : 150));
            }
        }
    }

    /** The glowing bar behind the selected row, with a gold edge and a diamond pointing at it. */
    static void highlight(Graphics2D g, double x0, double y, double rowH, double width, double s, double t) {
        double top = y - rowH * 0.5, h = rowH * 0.84, left = x0 - 34 * s, w = width;
        g.setPaint(new GradientPaint((float) left, 0, new Color(255, 180, 70, 95), (float) (left + w), 0, new Color(255, 180, 70, 0)));
        g.fill(new Rectangle2D.Double(left, top, w, h));
        g.setPaint(new GradientPaint((float) left, 0, new Color(255, 230, 170, 60), (float) (left + w * 0.7), 0, new Color(255, 230, 170, 0)));
        g.fill(new Rectangle2D.Double(left, top, w, 1.5));
        g.fill(new Rectangle2D.Double(left, top + h - 1.5, w, 1.5));
        g.setColor(GOLD_DEEP);
        g.fill(new Rectangle2D.Double(left, top, 3.5 * s, h));
        double pulse = 0.6 + 0.4 * Math.sin(t * 4);
        double dx = x0 - 15 * s, dy = top + h / 2;
        g.setColor(new Color(255, 200, 90, (int) (70 * pulse)));
        g.fill(new Ellipse2D.Double(dx - 11 * s, dy - 11 * s, 22 * s, 22 * s));
        diamond(g, dx, dy, 6 * s, new Color(255, 236, 180));
    }

    /** A thin rule and, under it, a line of italic serif saying what the selected row does. */
    static void infoLine(Graphics2D g, String text, double x0, double y, double width, double s, boolean warn) {
        g.setColor(new Color(255, 214, 120, 70));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Line2D.Double(x0, y - 18 * s, x0 + width, y - 18 * s));
        g.setFont(serif(Font.ITALIC, 16 * s, 0));
        shadowed(g, text, x0, y + 6 * s, warn ? WARN : TEXT);
    }

    // ------------------------------------------------------------------ keys and cards

    /** One key cap with its label drawn in; returns how wide it was. */
    static double keyCap(Graphics2D g, String key, double x, double baseline, double s) {
        g.setFont(sans(Font.BOLD, 12 * s));
        FontMetrics fm = g.getFontMetrics();
        double cw = Math.max(24 * s, fm.stringWidth(key) + 14 * s), ch = 22 * s, top = baseline - ch + 5 * s;
        RoundRectangle2D cap = new RoundRectangle2D.Double(x, top, cw, ch, 7, 7);
        g.setColor(new Color(20, 20, 32, 220));
        g.fill(cap);
        g.setColor(new Color(255, 214, 120, 150));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(cap);
        g.setColor(new Color(245, 238, 225));
        g.drawString(key, (float) (x + (cw - fm.stringWidth(key)) / 2), (float) (baseline - 1 * s));
        return cw;
    }

    /** How wide {@link #keys} would draw these. */
    static double keysWidth(Graphics2D g, double s, String[][] groups, String[] labels) {
        double x = 0;
        for (int k = 0; k < groups.length; k++) {
            g.setFont(sans(Font.BOLD, 12 * s));
            for (String key : groups[k]) x += Math.max(24 * s, g.getFontMetrics().stringWidth(key) + 14 * s) + 5 * s;
            g.setFont(sans(Font.PLAIN, 13 * s));
            x += 3 * s + g.getFontMetrics().stringWidth(labels[k]) + (k < groups.length - 1 ? 26 * s : 0);
        }
        return x;
    }

    /** A row of key-cap groups, each followed by what it does: {{"W", "S"}, {"ENTER"}} with {"Navigate", "Select"}. */
    static void keys(Graphics2D g, double x, double baseline, double s, String[][] groups, String[] labels) {
        for (int k = 0; k < groups.length; k++) {
            for (String key : groups[k]) x += keyCap(g, key, x, baseline, s) + 5 * s;
            g.setFont(sans(Font.PLAIN, 13 * s));
            shadowed(g, labels[k], x + 3 * s, baseline - 1 * s, new Color(200, 196, 214));
            x += g.getFontMetrics().stringWidth(labels[k]) + 26 * s;
        }
    }

    /** Ten little bars, lit up to the volume, and the percentage. */
    static void slider(Graphics2D g, double x, double y, int value, boolean selected, double s) {
        double bw = 11 * s, bh = 14 * s, gap = 4 * s;
        for (int k = 0; k < AudioSettings.STEPS; k++) {
            g.setColor(k < value ? (selected ? GOLD : new Color(205, 190, 150)) : new Color(50, 48, 64, 220));
            g.fill(new java.awt.geom.RoundRectangle2D.Double(x + k * (bw + gap), y - bh / 2, bw, bh, 3, 3));
        }
        g.setFont(MenuStyle.sans(Font.BOLD, 13 * s));
        MenuStyle.shadowed(g, value * 10 + "%", x + AudioSettings.STEPS * (bw + gap) + 8 * s, y + 5 * s, selected ? GOLD : DIM);
    }

    /** A dark glass card with a thin gold border (and, given one, a soft glow of {@code tint} along its top). */
    static RoundRectangle2D card(Graphics2D g, double x, double y, double w, double h, Color tint) {
        RoundRectangle2D card = new RoundRectangle2D.Double(x, y, w, h, 14, 14);
        g.setColor(new Color(10, 10, 20, 205));
        g.fill(card);
        if (tint != null) {
            Shape clip = g.getClip();
            g.clip(card);
            g.setPaint(new GradientPaint(0, (float) y, Util.alpha(tint, 0.32), 0, (float) (y + Math.min(140, h * 0.5)), Util.alpha(tint, 0)));
            g.fill(new Rectangle2D.Double(x, y, w, Math.min(140, h * 0.5)));
            g.setClip(clip);
        }
        g.setColor(new Color(255, 214, 120, 110));
        g.setStroke(new BasicStroke(1.3f));
        g.draw(card);
        return card;
    }

    /** Text centred on {@code cx}, shadowed. */
    static void centred(Graphics2D g, String text, double cx, double baseline, Color c) {
        shadowed(g, text, cx - g.getFontMetrics().stringWidth(text) / 2.0, baseline, c);
    }

    /** A small tracked-out capital label, gold by default (a card's or a column's title). */
    static void label(Graphics2D g, String text, double x, double y, double s, Color c) {
        g.setFont(caps(11 * s));
        shadowed(g, text, x, y, c);
    }

    static void diamond(Graphics2D g, double cx, double cy, double r, Color c) {
        Path2D d = new Path2D.Double();
        d.moveTo(cx, cy - r);
        d.lineTo(cx + r, cy);
        d.lineTo(cx, cy + r);
        d.lineTo(cx - r, cy);
        d.closePath();
        g.setColor(c);
        g.fill(d);
    }

    /** Text with a soft dark shadow under it, so it reads over the busy scene. */
    static void shadowed(Graphics2D g, String text, double x, double y, Color c) {
        g.setColor(new Color(0, 0, 0, Math.min(190, c.getAlpha())));
        g.drawString(text, (float) (x + 1.5), (float) (y + 2));
        g.setColor(c);
        g.drawString(text, (float) x, (float) y);
    }

    /** Word-wraps {@code text} to {@code maxWidth}; returns the baseline of the line after the last one. */
    static double wrap(Graphics2D g, String text, double x, double y, double maxWidth, double lineHeight, Color c) {
        FontMetrics fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(test) > maxWidth && !line.isEmpty()) {
                shadowed(g, line.toString(), x, y, c);
                y += lineHeight;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) {
            shadowed(g, line.toString(), x, y, c);
            y += lineHeight;
        }
        return y;
    }

    /** "THE WARDEN" as "The Warden". */
    static String titleCase(String s) {
        StringBuilder b = new StringBuilder();
        for (String word : s.toLowerCase().split(" ")) {
            if (b.length() > 0) b.append(' ');
            if (!word.isEmpty()) b.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return b.toString();
    }
}
