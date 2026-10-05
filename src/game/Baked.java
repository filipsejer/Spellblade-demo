package game;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Drawings that rarely change, baked once into an image and then reused, frame after frame: a level-up card, a skill
 * slot, a firefly. Java2D works out every smooth-edged shape and every line of text pixel by pixel on the CPU, and hands
 * it to the graphics card as a new picture, so a busy screen drawn afresh 60 times a second keeps a laptop working
 * hard. A baked image is handed over once and stays on the graphics card.
 *
 * <p>Images are baked at the screen's pixel density (twice the size on a Retina screen), so they're as sharp as
 * drawing live. The key must hold everything the drawing depends on; the oldest are dropped past {@link #MAX}.
 */
final class Baked {
    private static final int MAX = 96;

    private record Key(Object what, double w, double h, double scale) {}

    private final Map<Key, BufferedImage> images = new LinkedHashMap<>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, BufferedImage> e) { return size() > MAX; }
    };

    /**
     * Draws the {@code w} x {@code h} area whose top-left is at ({@code x}, {@code y}): baked by {@code paint} the
     * first time {@code what} is seen (it draws with (0, 0) as that corner), and from the image after that.
     */
    void draw(Graphics2D g, Object what, double x, double y, double w, double h, Consumer<Graphics2D> paint) {
        double ds = deviceScale(g);
        BufferedImage img = images.computeIfAbsent(new Key(what, w, h, ds), k -> {
            BufferedImage b = new BufferedImage(Math.max(1, (int) Math.ceil(w * ds)), Math.max(1, (int) Math.ceil(h * ds)), BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg = b.createGraphics();
            bg.setRenderingHints(g.getRenderingHints());
            bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            bg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            bg.scale(ds, ds);
            paint.accept(bg);
            bg.dispose();
            return b;
        });
        AffineTransform at = AffineTransform.getTranslateInstance(x, y);
        at.scale(1 / ds, 1 / ds);
        g.drawImage(img, at, null);
    }

    /** How many device pixels a drawing unit is (2 on a Retina screen). */
    static double deviceScale(Graphics2D g) {
        AffineTransform t = g.getTransform();
        double s = Math.hypot(t.getScaleX(), t.getShearY());
        return Math.max(1, Math.min(4, Math.round(s * 4) / 4.0));
    }
}
