package game;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Swing panel that runs the fixed-timestep game loop on the UI thread and paints the world. */
@SuppressWarnings("serial")   // never serialized
final class GamePanel extends JPanel {
    private static final double STEP = 1.0 / 60.0;

    private final World world = new World();
    private final Input input = new Input();
    private final Renderer renderer = new Renderer();
    private final AudioEngine engine = new AudioEngine();
    private final GameAudio audio = new GameAudio(engine);
    private final Timer timer;
    private long last;
    private double accumulator;

    GamePanel() {
        setPreferredSize(new Dimension(1280, 720));
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);   // so TAB reaches the game instead of moving focus
        world.audio = AudioSettings.load(AudioSettings.file());
        addKeyListener(input);
        addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { input.clear(); }
        });
        timer = new Timer(1, e -> tick());
        timer.setRepeats(false);                // each tick books the next for when the next step is due (see tick)
    }

    void start() {
        last = System.nanoTime();
        if (!"off".equals(System.getProperty("spellblade.audio"))) engine.start();   // ./run.sh silent turns sound off
        timer.start();
    }

    private void tick() {
        long now = System.nanoTime();
        accumulator += Math.min((now - last) / 1e9, 0.1);   // clamp so a stall doesn't cause a huge catch-up
        last = now;
        boolean stepped = false;
        while (accumulator >= STEP) {
            world.update(STEP, input);
            input.endFrame();
            accumulator -= STEP;
            stepped = true;
        }
        audio.update(world);
        if (world.audio.dirty) world.audio.save(AudioSettings.file());
        if (world.quitRequested) {
            timer.stop();
            System.exit(0);
        }
        if (stepped) repaint();
        // sleep until the next step is due, rather than checking every few milliseconds (that kept the CPU awake)
        timer.setInitialDelay(Math.max(1, (int) Math.ceil((STEP - accumulator) * 1000)));
        timer.restart();
    }

    /** The window is closing: the game is saved so CONTINUE can pick it up next time. */
    void onClose() {
        world.saveIfAny();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        renderer.render(world, (Graphics2D) g, getWidth(), getHeight());
    }
}
