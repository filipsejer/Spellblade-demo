package game;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws the game world with sprites: the level, its people, chests and gates, the player and every enemy sorted by
 * depth (so whoever is lower on the screen is in front), each with a shadow under its feet, plus attacks and other effects.
 * Menus and the HUD are drawn afterwards by {@link Renderer}.
 */
final class WorldRenderer {
    private final LevelView levelView = new LevelView();
    /** Shadows and the aura, baked once (drawn afresh, every one of them was a smooth shape worked out on the CPU each frame). */
    private final Baked baked = new Baked();
    private final Font f12 = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private final Font f14b = new Font(Font.SANS_SERIF, Font.BOLD, 14);

    private record Item(double sortY, Runnable draw) {}

    LevelView levelView() { return levelView; }

    /** {@code g} must already be translated by the camera; {@code view} is the visible part of the world. */
    void draw(Graphics2D g, World w, Rectangle2D view) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        Level level = w.level;
        levelView.draw(g, level, view, w.time);
        for (Zone z : w.zones) drawZone(g, w, z);
        for (Blast b : w.blasts) drawBlast(g, w, b);
        Run run = w.run;
        if (run != null) {
            if (run.ringActive) drawBossRing(g, w, run);
            drawAura(g, w, w.player);
            for (Pickup pk : run.pickups) {
                if (big(pk)) continue;
                if (view.intersects(pk.x - 30, pk.y - 40, 60, 60)) drawPickup(g, w, pk);
            }
        }
        for (Enemy e : w.enemies) drawTelegraph(g, e, w.player);

        // shadows first, then everyone in order of depth
        List<Item> items = new ArrayList<>();
        if (run != null) {
            for (Pickup pk : run.pickups) {
                if (!big(pk)) continue;
                if (!view.intersects(pk.x - 80, pk.y - 140, 160, 180)) continue;
                shadow(g, pk.x, pk.y, pk.kind == Pickup.Kind.PORTAL ? 38 : 28, 8);
                items.add(new Item(pk.y, () -> drawBigPickup(g, w, run, pk)));
            }
        }
        for (Level.Npc npc : level.npcs) {
            boolean stall = npc.role() != Level.Role.TALK;
            items.add(new Item(npc.y() + (stall ? 36 : 20), () -> drawNpc(g, w, npc)));
            if (stall) shadow(g, npc.x(), npc.y() + 36, 46, 12);
            else shadow(g, npc.x(), npc.y() + 22, 16, 6);
        }
        for (Level.Treasure t : level.treasures) {
            if (w.adventure == null || !view.intersects(t.x() - 60, t.y() - 90, 120, 120)) continue;
            boolean opened = w.adventure.opened.contains(t.id());
            shadow(g, t.x(), t.y(), 26, 8);
            items.add(new Item(t.y(), () -> drawTreasure(g, w, t, opened)));
        }
        for (Level.Gate gate : level.gates) {
            if (!view.intersects(gate.x() - 140, gate.y() - 200, 280, 280)) continue;
            items.add(new Item(gate.y(), () -> drawGate(g, w, gate)));
        }
        for (Level.Road road : level.roads) {
            if (!view.intersects(road.x() - 160, road.y() - 220, 320, 300)) continue;
            drawRoadGlow(g, w, road);
            shadow(g, road.x(), road.y() - 2, 22, 6);
            items.add(new Item(road.y(), () -> drawRoad(g, w, road)));
        }
        if (run != null) {
            for (Relay r : run.relays) {
                if (!view.intersects(r.x - 260, r.y - 260, 520, 520)) continue;
                drawRelayRing(g, w, r);
                shadow(g, r.x, r.y - 2, 40, 10);
                items.add(new Item(r.y, () -> drawRelay(g, w, r)));
            }
            Ward ward = run.ward;
            if (ward != null) {
                drawWardGround(g, w, run, ward);
                for (int i = ward.nextStop; i < ward.stops.length; i++) {             // the walls of vines still in his way
                    Util.Vec v = ward.pointAt(ward.stops[i]);
                    if (!view.intersects(v.x() - 120, v.y() - 140, 240, 200)) continue;
                    boolean cutting = i == ward.nextStop && ward.work > 0;
                    double vx = v.x() + (ward.faceLeft ? -34 : 34) * (i == ward.nextStop && ward.work > 0 ? 1 : 0);
                    items.add(new Item(v.y() + 6, () -> drawVines(g, w, vx, v.y() + 6, cutting ? ward.work / Ward.WORK_TIME : 1)));
                }
                if (view.intersects(ward.x - 160, ward.y - 200, 320, 260)) {
                    shadow(g, ward.x, ward.y - 2, ward.radius * 1.3, ward.radius * 0.4);
                    items.add(new Item(ward.y, () -> drawWard(g, w, ward)));
                }
            }
        }
        for (LevelView.Standing st : levelView.standing(level, view)) {     // the trees and rocks around the walls
            items.add(new Item(st.y(), () -> st.sprite().draw(g, st.x(), st.y(), Art.SCALE, st.flip())));
        }
        for (Level.Landmark l : level.landmarks) {
            if (!view.intersects(l.x() - 140, l.y() - 240, 280, 300)) continue;
            Sprite sprite = landmark(l.kind());
            items.add(new Item(l.y(), () -> sprite.draw(g, l.x(), l.y(), Art.SCALE, false)));
            shadow(g, l.x(), l.y() - 2, sprite.w * sprite.k * Art.SCALE * (l.kind().equals("oak") ? 0.3 : 0.4), sprite.w * sprite.k * Art.SCALE * 0.1);
        }
        for (Breakable b : level.breakables) {
            if (b.broken || !view.intersects(b.x - 60, b.y - 90, 120, 120)) continue;
            Sprite sprite = Art.frames(BreakableArt.name(b.kind, level.theme))[0];
            items.add(new Item(b.y, () -> sprite.draw(g, b.x, b.y, Art.SCALE, false)));
            shadow(g, b.x, b.y - 3, b.radius * 1.15, b.radius * 0.38);
        }
        for (Enemy e : w.enemies) {
            if (!view.intersects(e.x - 90, e.y - 140, 180, 220)) continue;
            double sh = e.type == Enemy.Type.BOSS ? 1.5 : e.type.small() ? 1.05 : 1.2;
            if (!e.intangible()) shadow(g, e.x, e.y + e.radius * 0.85, e.radius * sh, e.radius * 0.36);
            items.add(new Item(e.y + e.radius * 0.85, () -> drawEnemy(g, w, e)));
        }
        Player p = w.player;
        shadow(g, p.x, p.y + 14, 17, 6);
        items.add(new Item(p.y + 14, () -> drawPlayer(g, w, p)));
        items.sort(Comparator.comparingDouble(Item::sortY));
        for (Item it : items) it.draw.run();

        // the night / evening wash over the characters and ground; attacks stay bright on top of it
        ThemeArt art = levelView.art();
        if (art != null && (art.ambient >>> 24) > 0) {
            g.setColor(new Color(art.ambient, true));
            g.fill(view);
            levelView.drawGlows(g, view);
            for (Level.Landmark l : level.landmarks) {                       // lamps light the ground round them (and the lab's machines glow)
                int glow = landmarkGlow(l.kind());
                if (glow != 0 && view.intersects(l.x() - 220, l.y() - 320, 440, 440)) levelView.drawGlow(g, l.x(), l.y(), glow);
            }
            if (run != null && run.ward != null && !run.ward.broken) {         // Copper's lamp, the engine's cold light
                Ward wd = run.ward;
                if (view.intersects(wd.x - 220, wd.y - 320, 440, 440)) levelView.drawGlow(g, wd.x, wd.y + (wd.kind == Ward.Kind.ROBOT ? 20 : 40), wd.kind == Ward.Kind.ROBOT ? 0x02FFC070 : 0x0190D0FF);
            }
            if (run != null) for (Relay r : run.relays) {
                if (r.done && view.intersects(r.x - 220, r.y - 320, 440, 440)) levelView.drawGlow(g, r.x, r.y + 30, 0x01FFE7A0);
            }
        }

        for (Projectile pr : w.projectiles) drawProjectile(g, w, pr);
        if (run != null) drawOrbitBlades(g, w, p);
        for (Effect e : w.effects) e.render(g);
        Enemy lock = w.lockedTarget();
        if (lock != null) drawLockOn(g, lock);
        if (w.titleScene) return;                                             // the menus' backdrop: just the scene, no combat furniture
        for (Enemy e : w.enemies) drawEnemyBar(g, w, e);
        for (Enemy e : w.enemies) if (e.stun > 0.05 && e.spawnIn <= 0 && !e.type.armored && e.hp > 0) drawDizzy(g, w, e);
        if (run != null) drawComboDots(g, p);
        if (p.rollUnlocked) drawRollCharge(g, p);
        if (run != null) drawPlayerBar(g, p);
        if (run != null) drawCachePrompt(g, w, run);
        drawPrompt(g, w);
    }

    /** The light a piece of scenery gives off at night (see {@link LevelView#drawGlow}), or 0 for none. */
    private static int landmarkGlow(String kind) {
        return switch (kind) {
            case "lamp" -> 0x01FFE7A0;
            case "coil" -> 0x02B98CFF;
            case "tank" -> 0x0250FF90;
            case "planter.star" -> 0x02FFD070;
            case "orrery" -> 0x01FFD890;
            default -> 0;
        };
    }

    private static boolean big(Pickup pk) {
        return pk.kind == Pickup.Kind.PORTAL || pk.kind == Pickup.Kind.ELITE_CHEST || pk.kind == Pickup.Kind.BOSS_CHEST || pk.kind == Pickup.Kind.CACHE;
    }

    /** How far through the attack chain the player is: a row of pips under their feet, lit up hit by hit. */
    private void drawComboDots(Graphics2D g, Player p) {
        int progress = p.comboProgress();
        double spacing = 11, y = p.y + 32;
        double x0 = p.x - (p.comboMax - 1) * spacing / 2;
        for (int k = 0; k < p.comboMax; k++) {
            double dx = x0 + k * spacing;
            Ellipse2D dot = new Ellipse2D.Double(dx - 4, y - 4, 8, 8);
            g.setColor(k < progress ? new Color(255, 215, 90) : new Color(10, 10, 14, 210));
            g.fill(dot);
            g.setColor(new Color(0, 0, 0, 150));
            g.setStroke(new BasicStroke(1f));
            g.draw(dot);
        }
    }

    /**
     * The roll's cooldown: a ring beside the player that sweeps shut as it charges, then vanishes once the roll is
     * ready again — there's nothing to show once it's off cooldown.
     */
    private void drawRollCharge(Graphics2D g, Player p) {
        if (p.dodgeCd <= 0) return;
        double frac = 1 - Util.clamp(p.dodgeCd / p.dodgeCooldown, 0, 1);
        double r = 11, cx = p.x + p.radius + 16, cy = p.y;
        g.setColor(new Color(0, 0, 0, 130));
        g.setStroke(new BasicStroke(4f));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(new Color(215, 218, 228, 235));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, 90, -360 * frac, Arc2D.OPEN));
    }

    private final java.util.Map<String, Sprite> landmarks = new java.util.HashMap<>();

    /** The sprite of a placed piece of scenery (see {@link Level.Landmark}). */
    private Sprite landmark(String kind) {
        return landmarks.computeIfAbsent(kind, k -> {
            if (Art.has("landmark." + k)) return Art.frames("landmark." + k)[0];        // drawn art, when there is some
            ThemeArt a = ThemeArt.of(Theme.FOREST);
            return switch (k) {
                case "oak" -> a.tall[0];
                case "bush" -> a.low[0];
                case "boulder" -> a.low[1];
                case "stump" -> a.low[2];
                default -> a.low[3];
            };
        });
    }

    private void shadow(Graphics2D g, double x, double y, double rx, double ry) {
        double hx = Math.round(rx * 2) / 2.0, hy = Math.round(ry * 2) / 2.0;      // to the half pixel, so there are only a few to bake
        baked.draw(g, List.of("shadow", hx, hy), x - hx, y - hy, hx * 2, hy * 2, bg -> {
            bg.setColor(new Color(0, 0, 0, 62));
            bg.fill(new Ellipse2D.Double(0, 0, hx * 2, hy * 2));
        });
    }

    // ------------------------------------------------------------------ the hero

    private void drawPlayer(Graphics2D g, World w, Player p) {
        String dir = PeopleArt.heroDir(p.facing);
        boolean flip = dir.equals("side") && PeopleArt.heroFlip(p.facing);
        if (p.dodging()) {                                             // rolling: a spinning ball
            Sprite[] roll = Art.frames("hero.roll");
            roll[(int) (p.dodgeProgress() * 6) % roll.length].draw(g, p.x, p.y + 4, Art.SCALE, false, 0, 0.95f);
            return;
        }
        Sprite s;
        if (p.swinging()) s = Art.frames("hero." + dir + ".attack")[p.swingPhase() < 0.4 ? 0 : 1];
        else if (p.moving) s = Art.frame("hero." + dir + ".walk", w.time, 9);
        else s = Art.frame("hero." + dir + ".idle", w.time, 2);
        float alpha = p.hurtTimer > 0 && ((int) (p.hurtTimer * 20) % 2 == 0) ? 0.45f : 1f;
        SwordArt.Kind sword = SwordArt.forItem(w.profile == null ? null : w.profile.equipped.get(game.Item.Slot.WEAPON));   // the sword he wears
        PeopleArt.drawWithSword(g, s, p.x, p.y + 14, flip, alpha, sword, p.hurtTimer > 0.5 ? 0.65f : 0);   // (white flash when hit)
    }

    // ------------------------------------------------------------------ the explorable world

    /** Someone to talk to (the trainer and the merchant stand behind their stalls), their name, and a "!" if they've news. */
    private void drawNpc(Graphics2D g, World w, Level.Npc npc) {
        boolean stall = npc.role() != Level.Role.TALK;
        double feet = npc.y() + (stall ? 36 : 20);
        Sprite s = Art.frame(npc.portrait(), w.time + npc.x() * 0.01, npc.id().equals("hermit") ? 2.2 : 1.8);
        double hover = npc.id().equals("hermit") ? 7 + 3 * Math.sin(w.time * 3) : 0;      // the hermit floats a little
        s.draw(g, npc.x(), feet - hover, Art.SCALE, false);
        double top = feet - s.above(Art.SCALE) - hover;
        g.setFont(f12);
        Color c = stall ? new Color(255, 214, 110) : new Color(170, 220, 255);
        centered(g, npc.name(), npc.x(), top - 10, c);
        if (w.adventure != null && Story.hasNews(w.adventure, npc.id())) {               // something new to say: a bobbing "!"
            double by = top - 34 + 4 * Math.sin(w.time * 5);
            g.setColor(new Color(255, 214, 90));
            g.fill(new RoundRectangle2D.Double(npc.x() - 9, by - 18, 18, 24, 8, 8));
            g.setColor(new Color(60, 40, 10));
            g.setFont(f14b);
            FontMetrics fm = g.getFontMetrics();
            g.drawString("!", (float) (npc.x() - fm.stringWidth("!") / 2.0), (float) (by));
        }
    }

    /** A chest in the world: glowing until it's opened, then left standing open (dimmed). */
    private void drawTreasure(Graphics2D g, World w, Level.Treasure t, boolean opened) {
        if (!opened) {
            double r = 36 + 4 * Math.sin(w.time * 4 + t.x() * 0.01);
            g.setColor(Util.alpha(t.big() ? new Color(255, 150, 60) : new Color(255, 214, 90), 0.4));
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new Ellipse2D.Double(t.x() - r, t.y() - r * 0.35, r * 2, r * 0.7));
        }
        Art.frames(t.big() ? "run.chest.boss" : "run.chest.elite")[0].draw(g, t.x(), t.y(), Art.SCALE, false, 0, opened ? 0.4f : 1f);
    }

    /** A challenge's gate: a swirl of light, with its name; knotted shut with thorns until someone has asked you in. */
    private void drawGate(Graphics2D g, World w, Level.Gate gate) {
        Challenge c = gate.challenge();
        boolean open = w.gateOpen(c);
        double x = gate.x(), y = gate.y();
        double r = 64 + 6 * Math.sin(w.time * 2.4);
        Color tint = open ? new Color(200, 140, 255) : new Color(120, 90, 70);
        g.setColor(Util.alpha(tint, open ? 0.22 : 0.15));
        g.fill(new Ellipse2D.Double(x - r, y - r * 0.38, r * 2, r * 0.76));
        g.setColor(Util.alpha(tint, 0.6));
        g.setStroke(new BasicStroke(3f));
        g.draw(new Ellipse2D.Double(x - r, y - r * 0.38, r * 2, r * 0.76));
        Art.frame("run.portal", w.time, open ? 10 : 2).draw(g, x, y, Art.SCALE, false, 0, open ? 1f : 0.35f);
        boolean city = c.theme != Theme.FOREST;                                          // (the city's and the laboratory's are locked, not overgrown)
        if (!open && !city) {                                                             // thorns across it
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 5; i++) {
                double a = -0.9 + i * 0.45;
                g.setColor(new Color(60, 40, 34, 230));
                g.draw(new Line2D.Double(x - 46, y - 70 + i * 14, x + 46, y - 50 - i * 12 + Math.sin(a) * 10));
            }
        } else if (!open) {                                                               // the city's: iron bars and a padlock
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g.setColor(new Color(40, 42, 54, 235));
            for (int i = -2; i <= 2; i++) g.draw(new Line2D.Double(x + i * 18, y - 104, x + i * 18, y - 6));
            g.draw(new Line2D.Double(x - 46, y - 80, x + 46, y - 80));
            g.draw(new Line2D.Double(x - 46, y - 30, x + 46, y - 30));
            g.setColor(new Color(200, 160, 70));
            g.fill(new RoundRectangle2D.Double(x - 9, y - 62, 18, 16, 4, 4));
            g.setStroke(new BasicStroke(3f));
            g.draw(new Arc2D.Double(x - 6, y - 72, 12, 16, 0, 180, Arc2D.OPEN));
        }
        g.setFont(f14b);
        centered(g, c.title, x, y - 122, open ? new Color(225, 195, 255) : new Color(190, 170, 160));
        g.setFont(f12);
        int clears = w.adventure == null ? 0 : w.adventure.clears(c);
        String sub = !open ? (city ? "Locked" : "Sealed by thorns") : clears == 0 ? "From " + c.giver : "Cleared " + clears + (clears == 1 ? " time" : " times") + MenuStyle.DOT + "it's stronger now";
        centered(g, sub, x, y - 104, new Color(215, 210, 225));
    }

    /** The light on the ground where a road leaves the world: brighter once the road is open. */
    private void drawRoadGlow(Graphics2D g, World w, Level.Road road) {
        boolean open = w.adventure != null && Story.worldOpen(w.adventure, road.to());
        double r = 70 + 5 * Math.sin(w.time * 2), x = road.x(), y = road.y();
        Color c = open ? new Color(255, 214, 120) : new Color(150, 140, 130);
        g.setColor(Util.alpha(c, open ? 0.16 : 0.08));
        g.fill(new Ellipse2D.Double(x - r, y - r * 0.36, r * 2, r * 0.72));
        g.setColor(Util.alpha(c, open ? 0.55 : 0.25));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{10, 8}, (float) (w.time * 12)));
        g.draw(new Ellipse2D.Double(x - r, y - r * 0.36, r * 2, r * 0.72));
    }

    /** A road out of the world: a signpost with the road's name, and where it goes. */
    private void drawRoad(Graphics2D g, World w, Level.Road road) {
        Art.frames("landmark.signpost")[0].draw(g, road.x(), road.y(), Art.SCALE, false);
        boolean open = w.adventure != null && Story.worldOpen(w.adventure, road.to());
        g.setFont(f14b);
        centered(g, road.name(), road.x(), road.y() - 124, open ? new Color(255, 226, 160) : new Color(190, 170, 160));
        g.setFont(f12);
        centered(g, open ? "To " + Worlds.title(road.to()) : "Not yet", road.x(), road.y() - 106, new Color(215, 210, 225));
    }

    /** A relay's circle on the ground: dashed while it waits, filling round as it powers up, solid once it's on. */
    private void drawRelayRing(Graphics2D g, World w, Relay r) {
        double rx = Relay.RADIUS, ry = Relay.RADIUS * Relay.FLAT;
        Ellipse2D ring = new Ellipse2D.Double(r.x - rx, r.y - ry, rx * 2, ry * 2);
        Color c = r.done ? new Color(255, 222, 120) : r.started ? new Color(255, 200, 90) : new Color(170, 160, 200);
        g.setColor(Util.alpha(c, r.done ? 0.12 : r.held ? 0.2 + 0.06 * Math.sin(w.time * 8) : 0.08));
        g.fill(ring);
        g.setStroke(r.started ? new BasicStroke(3f) : new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{14, 10}, (float) (w.time * 10)));
        g.setColor(Util.alpha(c, r.started ? 0.45 : 0.6));
        g.draw(ring);
        if (r.charging()) {                                                   // how far it has powered up, round the circle
            g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 236, 160, r.held ? 235 : 140));
            g.draw(new Arc2D.Double(r.x - rx, r.y - ry, rx * 2, ry * 2, 90, -360 * r.charge, Arc2D.OPEN));
        }
    }

    private void drawRelay(Graphics2D g, World w, Relay r) {
        boolean lit = r.done || r.charging() && (r.held || (int) (w.time * 6) % 2 == 0);
        Art.frame(lit ? "relay.on" : "relay.off", w.time, r.charging() ? 12 : 3).draw(g, r.x, r.y, Art.SCALE, false);
        if (r.started) return;
        g.setFont(f14b);
        centered(g, "RELAY", r.x, r.y - 140, new Color(255, 226, 160));
    }

    /** A key cap and a word over whatever E would use right now: "E  Talk", "E  Open", "E  Enter". */
    private void drawPrompt(Graphics2D g, World w) {
        if (w.run != null || w.state != World.State.PLAYING || w.dialogue.stopsWorld()) return;
        double x, y;
        String what;
        Level.Npc npc = w.npcNearby();
        Level.Treasure t = npc == null ? w.treasureNearby() : null;
        Level.Gate gate = npc == null && t == null ? w.gateNearby() : null;
        if (npc != null) {
            x = npc.x();
            y = npc.y() + (npc.role() == Level.Role.TALK ? 52 : 74);
            boolean met = w.adventure.has("met." + npc.id());
            what = npc.role() == Level.Role.TRAINER && met ? "Train" : npc.role() == Level.Role.MERCHANT && met ? "Shop" : "Talk";
        } else if (t != null) {
            x = t.x();
            y = t.y() + 34;
            what = "Open";
        } else if (gate != null) {
            x = gate.x();
            y = gate.y() + 46;
            what = w.gateOpen(gate.challenge()) ? "Enter" : "Look";
        } else if (w.roadNearby() != null) {
            Level.Road road = w.roadNearby();
            x = road.x();
            y = road.y() + 40;
            what = Story.worldOpen(w.adventure, road.to()) ? "Travel" : "Look";
        } else {
            return;
        }
        keyPrompt(g, x, y, what);
    }

    /** A cache in a fight you're standing at: what it costs, and E to open it (or a relay, to switch on). */
    private void drawCachePrompt(Graphics2D g, World w, Run run) {
        Relay relay = run.relayNearby(w);
        if (relay != null && w.state == World.State.PLAYING) {
            keyPrompt(g, relay.x, relay.y + 40, run.chargingRelay() == null ? "Switch on" : "Finish the other relay first");
            return;
        }
        Pickup cache = run.cacheNearby(w);
        if (cache == null || w.state != World.State.PLAYING) return;
        keyPrompt(g, cache.x, cache.y + 34, run.gold >= cache.value ? "Open  (" + cache.value + " gold)" : "Needs " + cache.value + " gold");
    }

    private void keyPrompt(Graphics2D g, double x, double y, String what) {
        g.setFont(f14b);
        FontMetrics fm = g.getFontMetrics();
        double tw = fm.stringWidth(what), total = 26 + 8 + tw;
        double x0 = x - total / 2;
        RoundRectangle2D cap = new RoundRectangle2D.Double(x0, y - 18, 26, 26, 7, 7);
        g.setColor(new Color(20, 18, 30, 230));
        g.fill(cap);
        g.setColor(new Color(255, 214, 120));
        g.setStroke(new BasicStroke(1.8f));
        g.draw(cap);
        g.setColor(Color.WHITE);
        g.drawString("E", (float) (x0 + 13 - fm.stringWidth("E") / 2.0), (float) (y));
        centered(g, what, x0 + 34 + tw / 2, y, Color.WHITE);
    }

    // ------------------------------------------------------------------ enemies

    private static String name(Enemy.Type t) {
        return switch (t) { case GRUNT -> "grunt"; case RUNNER -> "runner"; case SHOOTER -> "shooter"; case BRUTE -> "brute"; case SHADE -> "shade"; case BOSS -> "boss"; case NEST -> "nest"; };
    }

    /** The sprite an enemy shows right now (its walk cycle, wind-up pose, boss attack pose...). */
    Sprite spriteFor(World w, Enemy e) {
        String theme = w.level.theme.key;
        boolean moving = e.state == Enemy.State.CHASE && e.stun <= 0 && e.spawnIn <= 0;
        double t = w.time + e.animOffset;
        if (e.rooted()) {
            String key = Art.has(theme + ".nest.idle") ? theme + ".nest.idle" : "forest.nest.idle";
            return Art.frame(key, t, e.awake ? 5 : 2);
        }
        if (e.type == Enemy.Type.BOSS) {
            String base = theme + (e.phase2 ? ".boss2." : ".boss.");
            if (e.state == Enemy.State.WINDUP) return Art.frames(base + (e.attack == Enemy.Attack.SLAM ? "slam" : "burst"))[0];
            return Art.frame(base + (moving ? "walk" : "idle"), t, moving ? 6 : 2.5);
        }
        String base = e.type == Enemy.Type.SHADE ? "shade" : theme + "." + name(e.type);
        if (e.state == Enemy.State.WINDUP) return Art.frames(base + ".windup")[0];
        return Art.frame(base + ".walk", t, moving ? 7 : 2.2);
    }

    /** How high above the ground it floats: drones and shades hover. */
    private double hover(World w, Enemy e) {
        boolean drone = e.type == Enemy.Type.SHOOTER && w.level.theme == Theme.CITY;
        if (e.type != Enemy.Type.SHADE && !drone) return 0;
        return 9 + 3 * Math.sin((w.time + e.animOffset) * 3);
    }

    /** How big an enemy is drawn: elites a size up, specimens bigger still (drawn art keeps its pixels even). */
    private static double scaleOf(Enemy e, Sprite s) {
        if (e.specimen) return s.k < 1 ? Art.SCALE * 1.5 : Art.SCALE + 2;
        if (e.elite) return s.k < 1 ? Art.SCALE * 1.5 : Art.SCALE + 1;
        return Art.SCALE;
    }

    private void drawEnemy(Graphics2D g, World w, Enemy e) {
        Sprite s = spriteFor(w, e);
        double x = e.x, fy = e.y + e.radius * 0.85 - hover(w, e) - e.z;   // airborne: floats up off its shadow, which stays on the ground
        double sc = scaleOf(e, s);
        if (e.intangible()) {                                             // shadow mode: a dark see-through silhouette with a violet edge
            float a = 0.6f;
            if (e.spawnIn <= 0 && e.shadowTimer < 1.0) a *= (float) (0.55 + 0.45 * Math.abs(Math.sin(e.shadowTimer * 16)));
            for (int[] o : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) {
                s.drawSilhouette(g, x + o[0], fy + o[1], sc, e.faceLeft, 0xA07AE0, a * 0.7f);
            }
            s.drawSilhouette(g, x, fy, sc, e.faceLeft, 0x14101E, a);
            drawClockArc(g, e, true);
            return;
        }
        float alpha = e.spawnIn > 0 ? 0.35f : 1f;
        if (e.type == Enemy.Type.SHADE && e.spawnIn <= 0 && e.shadowTimer < 1.0) alpha *= (float) (0.6 + 0.4 * Math.abs(Math.sin(e.shadowTimer * 16)));
        if (e.specimen) {                                                       // a specimen: bigger still, glowing violet (asleep, dimmer)
            double k = 0.5 + 0.5 * Math.sin(w.time * (e.awake ? 6 : 1.5));
            for (int[] o : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) {
                s.drawSilhouette(g, x + o[0], fy + o[1], sc, e.faceLeft, 0xE65AFF, (float) ((e.awake ? 0.35 : 0.15) + 0.25 * k) * alpha);
            }
            if (!e.awake) {                                                      // asleep: a drift of z's
                g.setFont(f14b);
                for (int i = 0; i < 3; i++) {
                    double ph = (w.time * 0.6 + i / 3.0) % 1;
                    g.setColor(new Color(230, 200, 255, (int) (200 * (1 - ph))));
                    g.drawString("z", (float) (x + 30 + ph * 26 + i * 4), (float) (fy - s.above(sc) - ph * 40));
                }
            }
        } else if (e.elite) {                                                   // an elite: bigger, with a gold glow round it
            double k = 0.5 + 0.5 * Math.sin(w.time * 5);
            for (int[] o : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) {
                s.drawSilhouette(g, x + o[0], fy + o[1], sc, e.faceLeft, 0xFFC840, (float) (0.35 + 0.3 * k) * alpha);
            }
        }
        s.draw(g, x, fy, sc, e.faceLeft, 0, alpha);
        if (e.flash > 0) s.drawSilhouette(g, x, fy, sc, e.faceLeft, 0xFFFFFF, 0.85f);
        if (e.stageTimer > 0) {                                                                          // changing stage: untouchable, glowing
            s.drawSilhouette(g, x, fy, sc, e.faceLeft, 0xA0FFC8, (float) (0.35 + 0.3 * Math.sin(w.time * 24)));
            double ring = e.radius * 2 + 18 * Math.abs(Math.sin(w.time * 6));
            g.setColor(Util.alpha(new Color(150, 255, 190), 0.6));
            g.setStroke(new BasicStroke(3f));
            g.draw(new Ellipse2D.Double(x - ring, e.y - ring, ring * 2, ring * 2));
        }
        if (e.burnTimer > 0) s.drawSilhouette(g, x, fy, sc, e.faceLeft, 0xFF8A20, (float) (0.28 + 0.16 * Math.sin(w.time * 20)));
        if (e.slowTimer > 0) s.drawSilhouette(g, x, fy, sc, e.faceLeft, 0x7CC8FF, 0.32f);
        if (e.type.shadowy) drawShadowClock(g, e);
    }

    /** The danger area of an attack that's winding up (readable through the sprites). */
    private void drawTelegraph(Graphics2D g, Enemy e, Player p) {
        if (e.state != Enemy.State.WINDUP) return;
        double total = e.windupTotal > 0 ? e.windupTotal : e.type.windup;
        double progress = Util.clamp(1 - e.stateTimer / total, 0, 1);
        double r = e.radius;
        if (e.type == Enemy.Type.BOSS && e.attack == Enemy.Attack.BOMBS) {                 // winding up to lob flasks: a green pulse
            double ring = r + 14 + 46 * progress;
            Ellipse2D pulse = new Ellipse2D.Double(e.x - ring, e.y - ring, ring * 2, ring * 2);
            g.setColor(Util.alpha(new Color(90, 255, 140), 0.10 + 0.25 * progress));
            g.fill(pulse);
            g.setColor(Util.alpha(new Color(170, 255, 190), 0.5 + 0.4 * progress));
            g.setStroke(new BasicStroke(3f));
            g.draw(pulse);
        } else if (e.type == Enemy.Type.BOSS && e.attack == Enemy.Attack.BURST) {
            double ring = r + 14 + 46 * progress;
            Ellipse2D pulse = new Ellipse2D.Double(e.x - ring, e.y - ring, ring * 2, ring * 2);
            g.setColor(Util.alpha(new Color(255, 70, 120), 0.10 + 0.25 * progress));
            g.fill(pulse);
            g.setColor(Util.alpha(new Color(255, 130, 160), 0.5 + 0.4 * progress));
            g.setStroke(new BasicStroke(3f));
            g.draw(pulse);
        } else if (e.type == Enemy.Type.BOSS && e.attack == Enemy.Attack.CHARGE) {          // winding up to charge: a line down the lane he'll take
            double ang = Util.angleTo(e.x, e.y, p.x, p.y);
            g.setColor(Util.alpha(new Color(150, 255, 120), 0.15 + 0.55 * progress));
            g.setStroke(new BasicStroke(5f));
            g.draw(new Line2D.Double(e.x, e.y, e.x + Math.cos(ang) * 500, e.y + Math.sin(ang) * 500));
        } else if (e.type == Enemy.Type.SHOOTER) {
            double ang = Util.angleTo(e.x, e.y, p.x, p.y);
            g.setColor(Util.alpha(new Color(255, 80, 80), 0.15 + 0.5 * progress));
            g.setStroke(new BasicStroke(2f));
            g.draw(new Line2D.Double(e.x, e.y, e.x + Math.cos(ang) * 420, e.y + Math.sin(ang) * 420));
        } else {
            double reach = r + p.radius + e.strikeReach();
            Ellipse2D danger = new Ellipse2D.Double(e.x - reach, e.y - reach, reach * 2, reach * 2);
            g.setColor(Util.alpha(new Color(255, 60, 60), 0.10 + 0.22 * progress));
            g.fill(danger);
            g.setColor(Util.alpha(new Color(255, 120, 100), 0.5 + 0.4 * progress));
            g.setStroke(new BasicStroke(2f));
            g.draw(danger);
        }
    }

    /**
     * A shade's clock: an arc around it that drains to show how long is left in the current mode (5 seconds each).
     * Pink = solid, lilac = shadow.
     */
    private void drawShadowClock(Graphics2D g, Enemy e) {
        drawClockArc(g, e, false);
    }

    private void drawClockArc(Graphics2D g, Enemy e, boolean ghost) {
        double rr = e.radius + 12;
        double frac = Util.clamp(e.shadowTimer / Enemy.SHADOW_PERIOD, 0, 1);
        g.setColor(ghost ? new Color(200, 165, 255, 230) : new Color(255, 120, 150, 230));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(e.x - rr, e.y - rr, rr * 2, rr * 2, 90, -360 * frac, Arc2D.OPEN));
    }

    private void drawDizzy(Graphics2D g, World w, Enemy e) {
        Sprite[] star = Art.frames("fx.star");
        Sprite s = spriteFor(w, e);
        double top = e.y + e.radius * 0.85 - hover(w, e) - e.z - s.above(scaleOf(e, s));
        for (int i = 0; i < 3; i++) {
            double a = w.time * 6 + i * Math.PI * 2 / 3;
            star[(int) (w.time * 8 + i) % star.length].draw(g, e.x + Math.cos(a) * 16, top + 6 + Math.sin(a) * 4, 2, false);
        }
    }

    /** The target-lock marker: a plain white ring (dark edge so it reads on any background) around the enemy. */
    private void drawLockOn(Graphics2D g, Enemy e) {
        double r = e.radius + 12, ey = e.y - e.z;
        Ellipse2D ring = new Ellipse2D.Double(e.x - r, ey - r, r * 2, r * 2);
        g.setColor(new Color(0, 0, 0, 170));
        g.setStroke(new BasicStroke(5.5f));
        g.draw(ring);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(ring);
    }

    private void drawEnemyBar(Graphics2D g, World w, Enemy e) {
        if (e.spawnIn > 0 || e.type == Enemy.Type.BOSS || e.intangible() || e.hp <= 0) return;   // the boss has its own big bar
        Sprite s = spriteFor(w, e);
        double top = e.y + e.radius * 0.85 - hover(w, e) - e.z - s.above(Art.SCALE);
        double bw = Math.max(30, e.radius * 2);
        double x = e.x - bw / 2, y = top - 10;
        g.setColor(new Color(20, 20, 22, 210));
        g.fill(new Rectangle2D.Double(x - 1.5, y - 1.5, bw + 3, 8));
        g.setColor(new Color(70, 200, 90));
        g.fill(new Rectangle2D.Double(x, y, bw * Util.clamp(e.hp / e.maxHp, 0, 1), 5));
    }

    // ------------------------------------------------------------------ attacks in flight

    private void drawProjectile(Graphics2D g, World w, Projectile p) {
        if (p.wave) {
            drawWave(g, p);
            return;
        }
        if (p.friendly) {
            Art.frame("fx.fireball", w.time, 14).draw(g, p.x, p.y, Art.SCALE * p.scale, false, Math.atan2(p.vy, p.vx), 1f);
            return;
        }
        boolean big = p.radius >= 8;
        String name = switch (w.level.theme) {
            case FOREST -> big ? "proj.spore" : "proj.seed";
            case CITY -> big ? "proj.boltball" : "proj.plasma";
            case LAB -> big ? "proj.acidball" : "proj.acid";
        };
        Art.frame(name, w.time, 8).draw(g, p.x, p.y, Art.SCALE, false, 0, 1f);
    }

    // ------------------------------------------------------------------ fights: skills, pickups, the ring

    /** Crescent Wave: a pale blue crescent of light, fading as it flies. */
    private void drawWave(Graphics2D g, Projectile p) {
        double ang = Math.atan2(p.vy, p.vx);
        double r = 30 * p.scale;
        float a = (float) Util.clamp(p.life / 0.2, 0, 1);
        java.awt.geom.AffineTransform saved = g.getTransform();
        g.translate(p.x, p.y);
        g.rotate(ang);
        Arc2D arc = new Arc2D.Double(-r * 1.2, -r, r * 2, r * 2, -62, 124, Arc2D.OPEN);
        g.setStroke(new BasicStroke((float) (11 * p.scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(120, 170, 255, (int) (110 * a)));
        g.draw(arc);
        g.setStroke(new BasicStroke((float) (5 * p.scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(235, 245, 255, (int) (235 * a)));
        g.draw(arc);
        g.setTransform(saved);
    }

    /** Soft round glows in a pickup's colour, painted once: drawn under gems and coins so they stand out on any floor. */
    private static final java.awt.image.BufferedImage[] GLOWS = {
        glow(new Color(80, 225, 255)), glow(new Color(180, 255, 100)), glow(new Color(255, 80, 130)), glow(new Color(255, 215, 80))};

    private static java.awt.image.BufferedImage glow(Color c) {
        int size = 48;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D gg = img.createGraphics();
        gg.setPaint(new java.awt.RadialGradientPaint(size / 2f, size / 2f, size / 2f, new float[]{0f, 0.35f, 1f},
            new Color[]{Util.alpha(Color.WHITE, 0.75), Util.alpha(c, 0.55), Util.alpha(c, 0)}));
        gg.fillRect(0, 0, size, size);
        gg.dispose();
        return img;
    }

    /** Gems, coins and crate pickups lying on the ground, bobbing a little. */
    private void drawPickup(Graphics2D g, World w, Pickup pk) {
        double bob = pk.attracted ? 0 : 3 * Math.sin(w.time * 4 + pk.x * 0.05);
        shadow(g, pk.x, pk.y + 2, pk.kind == Pickup.Kind.GEM ? 8 : 10, 3);
        if (pk.kind == Pickup.Kind.GEM || pk.kind == Pickup.Kind.COIN) {       // a pulsing glow behind it
            java.awt.image.BufferedImage halo = GLOWS[pk.kind == Pickup.Kind.COIN ? 3 : pk.gemTier()];
            double pulse = 0.75 + 0.25 * Math.sin(w.time * 5 + pk.x * 0.07 + pk.y * 0.03);
            double size = (pk.kind == Pickup.Kind.GEM ? 44 + 8 * pk.gemTier() : 40) * pulse;
            java.awt.Composite saved = g.getComposite();
            g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) Util.clamp(0.55 + 0.45 * pulse, 0, 1)));
            g.drawImage(halo, (int) (pk.x - size / 2), (int) (pk.y - 19 + bob - size / 2), (int) size, (int) size, null);
            g.setComposite(saved);
        }
        String name = switch (pk.kind) {
            case GEM -> "run.gem" + pk.gemTier();
            case COIN -> "run.coin";
            case HEART -> "run.heart";
            case MAGNET -> "run.magnet";
            default -> "run.bomb";
        };
        double fps = pk.kind == Pickup.Kind.COIN ? 8 : pk.kind == Pickup.Kind.GEM ? 5 : 3;
        Art.frame(name, w.time + pk.x * 0.01, fps).draw(g, pk.x, pk.y - 4 + bob, Art.SCALE, false);
    }

    /** A chest or the portal: sorted by depth with the characters, since they stand up off the ground. */
    private void drawBigPickup(Graphics2D g, World w, Run run, Pickup pk) {
        if (pk.kind == Pickup.Kind.PORTAL) {
            boolean ready = run.find(Pickup.Kind.BOSS_CHEST) == null;
            double r = 54 + 5 * Math.sin(w.time * 3);
            g.setColor(Util.alpha(new Color(190, 140, 255), ready ? 0.5 : 0.2));
            g.setStroke(new BasicStroke(3f));
            g.draw(new Ellipse2D.Double(pk.x - r, pk.y - r * 0.35, r * 2, r * 0.7));
            Art.frame("run.portal", w.time, 10).draw(g, pk.x, pk.y, Art.SCALE, false, 0, ready ? 1f : 0.45f);
            g.setFont(f14b);
            centered(g, ready ? "BACK TO THE FOREST" : "Open the chest first", pk.x, pk.y - 116,
                ready ? new Color(220, 190, 255) : new Color(200, 200, 210));
            return;
        }
        if (pk.kind == Pickup.Kind.CACHE) {                                     // a cache: a plain chest with its price over it
            Art.frames("run.chest.elite")[0].draw(g, pk.x, pk.y, Art.SCALE, false, 0, 0.9f);
            g.setFont(f14b);
            centered(g, pk.value + " G", pk.x, pk.y - 70, run.gold >= pk.value ? new Color(255, 214, 90) : new Color(200, 160, 150));
            return;
        }
        boolean boss = pk.kind == Pickup.Kind.BOSS_CHEST;
        double r = 40 + 4 * Math.sin(w.time * 4);
        Color glow = boss ? new Color(255, 150, 60) : new Color(255, 214, 90);
        g.setColor(Util.alpha(glow, 0.45));
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new Ellipse2D.Double(pk.x - r, pk.y - r * 0.35, r * 2, r * 0.7));
        double hop = Math.max(0, Math.sin(w.time * 3)) * 5;
        Art.frame(boss ? "run.chest.boss" : "run.chest.elite", w.time, 2).draw(g, pk.x, pk.y - hop, Art.SCALE, false);
    }

    /** The boss fight's ring: a wall of red light you can't cross until the boss is down. */
    private void drawBossRing(Graphics2D g, World w, Run run) {
        double r = Run.RING_RADIUS;
        Ellipse2D ring = new Ellipse2D.Double(run.ringX - r, run.ringY - r, r * 2, r * 2);
        double k = 0.5 + 0.5 * Math.sin(w.time * 4);
        g.setColor(new Color(255, 60, 60, (int) (50 + 40 * k)));
        g.setStroke(new BasicStroke(18f));
        g.draw(ring);
        g.setColor(new Color(255, 170, 150, 220));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{22, 14}, (float) (w.time * 40)));
        g.draw(ring);
    }

    /** Holy Aura: a soft golden disc round the player, pulsing with each burn tick. */
    private void drawAura(Graphics2D g, World w, Player p) {
        double r = Arsenal.auraRadius(p);
        if (r <= 0) return;
        double pulse = Util.clamp(p.auraTick / Arsenal.AURA_TICK, 0, 1);
        boolean evolved = Arsenal.rank(p, Perk.HOLY_AURA) >= Perk.EVOLVED;
        Color c = evolved ? new Color(255, 245, 170) : new Color(255, 220, 110);
        // baked at its brightest and faded as it pulses (the glow inside, and the ring round it, pulse by different amounts)
        double pad = 2, x = p.x - r - pad, y = p.y - r * 0.8 + 6 - pad, bw = r * 2 + 2 * pad, bh = r * 1.6 + 2 * pad;
        java.awt.Composite saved = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) ((0.10 + 0.10 * pulse) / 0.20)));
        baked.draw(g, List.of("aura", r, evolved), x, y, bw, bh, bg -> {
            bg.setColor(Util.alpha(c, 0.20));
            bg.fill(new Ellipse2D.Double(pad, pad, r * 2, r * 1.6));
        });
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) ((0.45 + 0.3 * pulse) / 0.75)));
        baked.draw(g, List.of("aura ring", r, evolved), x, y, bw, bh, bg -> {
            bg.setColor(Util.alpha(c, 0.75));
            bg.setStroke(new BasicStroke(2.5f));
            bg.draw(new Ellipse2D.Double(pad, pad, r * 2, r * 1.6));
        });
        g.setComposite(saved);
    }

    /** Orbit Blades: spectral swords circling the player, each pointing along its path. */
    private void drawOrbitBlades(Graphics2D g, World w, Player p) {
        int r = Arsenal.rank(p, Perk.ORBIT_BLADES);
        if (r == 0) return;
        int n = Arsenal.ORBIT_COUNT[r];
        Sprite blade = Art.frames("run.blade")[0];
        for (int i = 0; i < n; i++) {
            Util.Vec b = Arsenal.bladeAt(p, i, n, Arsenal.ORBIT_RADIUS[r]);
            double a = p.orbitAngle + i * Math.PI * 2 / n;
            blade.draw(g, b.x(), b.y() + 4, Art.SCALE, false, a + Math.PI, 0.95f);
        }
    }

    /** A small health bar under the hero's feet (a run's HUD keeps your eyes on the middle of the screen). */
    private void drawPlayerBar(Graphics2D g, Player p) {
        double bw = 46, x = p.x - bw / 2, y = p.y + 40;
        g.setColor(new Color(20, 20, 22, 210));
        g.fill(new Rectangle2D.Double(x - 1.5, y - 1.5, bw + 3, 8));
        g.setColor(p.hp < p.maxHp * 0.3 ? new Color(235, 70, 70) : new Color(70, 200, 90));
        g.fill(new Rectangle2D.Double(x, y, bw * Util.clamp(p.hp / p.maxHp, 0, 1), 5));
    }

    /** A flask bomb: the spot is marked, a flask falls onto it, then it bursts in a green flash. */
    private void drawBlast(Graphics2D g, World w, Blast b) {
        if (b.lightning) { drawStrike(g, w, b); return; }
        if (b.burst) {
            double a = Util.clamp(b.after / Blast.LINGER, 0, 1);
            Ellipse2D flash = new Ellipse2D.Double(b.x - b.radius, b.y - b.radius, b.radius * 2, b.radius * 2);
            g.setColor(Util.alpha(new Color(150, 255, 170), 0.35 * a));
            g.fill(flash);
            g.setColor(Util.alpha(new Color(220, 255, 230), 0.8 * a));
            g.setStroke(new BasicStroke(3f));
            g.draw(flash);
            return;
        }
        double p = Util.clamp(1 - b.delay / b.total, 0, 1);
        Ellipse2D mark = new Ellipse2D.Double(b.x - b.radius, b.y - b.radius, b.radius * 2, b.radius * 2);
        g.setColor(Util.alpha(new Color(90, 255, 140), 0.08 + 0.2 * p));
        g.fill(mark);
        g.setColor(Util.alpha(new Color(170, 255, 190), 0.45 + 0.45 * p));
        g.setStroke(new BasicStroke(2f));
        g.draw(mark);
        double inner = b.radius * (1 - p);
        g.draw(new Ellipse2D.Double(b.x - inner, b.y - inner, inner * 2, inner * 2));
        g.draw(new Line2D.Double(b.x - 9, b.y, b.x + 9, b.y));
        g.draw(new Line2D.Double(b.x, b.y - 9, b.x, b.y + 9));
        double drop = 420 * (1 - p) * (1 - p);                                                         // the flask falls faster as it nears
        Art.frame("proj.acidball", w.time, 8).draw(g, b.x, b.y - drop, 4, false, w.time * 5, 1f);
    }

    /**
     * A lightning strike: first the spot, marked in pale blue with a ring closing in and the air crackling over it;
     * then the bolt itself, jagged down out of the sky, fading.
     */
    private void drawStrike(Graphics2D g, World w, Blast b) {
        Color pale = new Color(200, 228, 255);
        if (b.burst) {
            double a = Util.clamp(b.after / Blast.LINGER, 0, 1);
            if (b.boltX != null) {
                Path2D bolt = new Path2D.Double();
                bolt.moveTo(b.boltX[0], b.boltY[0]);
                for (int i = 1; i < b.boltX.length; i++) bolt.lineTo(b.boltX[i], b.boltY[i]);
                g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(Util.alpha(new Color(140, 180, 255), 0.35 * a));
                g.draw(bolt);
                g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(Util.alpha(Color.WHITE, 0.95 * a));
                g.draw(bolt);
            }
            Ellipse2D flash = new Ellipse2D.Double(b.x - b.radius, b.y - b.radius * 0.6, b.radius * 2, b.radius * 1.2);
            g.setColor(Util.alpha(pale, 0.4 * a));
            g.fill(flash);
            return;
        }
        double p = Util.clamp(1 - b.delay / b.total, 0, 1);
        Ellipse2D mark = new Ellipse2D.Double(b.x - b.radius, b.y - b.radius * 0.6, b.radius * 2, b.radius * 1.2);
        g.setColor(Util.alpha(new Color(120, 170, 255), 0.06 + 0.22 * p));
        g.fill(mark);
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(Util.alpha(pale, 0.4 + 0.5 * p));
        g.draw(mark);
        double inner = 1 - p;
        g.draw(new Ellipse2D.Double(b.x - b.radius * inner, b.y - b.radius * 0.6 * inner, b.radius * 2 * inner, b.radius * 1.2 * inner));
        if (p > 0.5 && (int) (w.time * 30) % 3 == 0) {                        // the air crackling, just before
            double ax = b.x + (FxArt.hash((int) (w.time * 30), (int) b.x, 1) % 60 - 30), ay = b.y - 30 - FxArt.hash((int) (w.time * 30), (int) b.y, 2) % 40;
            g.setStroke(new BasicStroke(2f));
            g.setColor(Util.alpha(Color.WHITE, 0.8));
            g.draw(new Line2D.Double(ax, ay, ax + 8, ay + 10));
            g.draw(new Line2D.Double(ax + 8, ay + 10, ax + 2, ay + 18));
        }
    }

    // ------------------------------------------------------------------ what you protect: Copper, the engine

    /**
     * The ground round the ward: the engine's frost (and how far its cold reaches); for Copper, the next stretch of his
     * route, dashed ahead of him; and either one's repair ring while it's broken.
     */
    private void drawWardGround(Graphics2D g, World w, Run run, Ward wd) {
        if (wd.kind == Ward.Kind.ENGINE && !wd.broken && run.nestsLeft > 0) {
            double r = Ward.CHILL;
            Ellipse2D chill = new Ellipse2D.Double(wd.x - r, wd.y - r * 0.6, r * 2, r * 1.2);
            g.setColor(Util.alpha(Ward.FROST, 0.07 + 0.03 * Math.sin(w.time * 2)));
            g.fill(chill);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{10, 10}, (float) (w.time * 8)));
            g.setColor(Util.alpha(Ward.FROST, 0.35));
            g.draw(chill);
        }
        if (wd.kind == Ward.Kind.ROBOT && run.nestsLeft > 0) {                 // the way ahead: a dashed line along his route
            Path2D ahead = new Path2D.Double();
            ahead.moveTo(wd.x, wd.y);
            double step = 40;
            for (double d = wd.along + step; d < Math.min(wd.length, wd.along + 900); d += step) {
                Util.Vec v = wd.pointAt(d);
                ahead.lineTo(v.x(), v.y());
            }
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{2, 16}, (float) (18 - (w.time * 30) % 18)));
            g.setColor(Util.alpha(Ward.COPPER_LIGHT, 0.75));
            g.draw(ahead);
        }
        if (wd.broken) {                                                       // the repair ring: fills as you stand by it
            double r = Ward.REPAIR_RANGE + wd.radius;
            Ellipse2D ring = new Ellipse2D.Double(wd.x - r, wd.y - r * 0.6, r * 2, r * 1.2);
            g.setColor(Util.alpha(new Color(255, 120, 90), 0.1 + 0.05 * Math.sin(w.time * 6)));
            g.fill(ring);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{12, 9}, (float) (w.time * 10)));
            g.setColor(Util.alpha(new Color(255, 150, 110), 0.6));
            g.draw(ring);
            g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 220, 160, 230));
            g.draw(new Arc2D.Double(wd.x - r, wd.y - r * 0.6, r * 2, r * 1.2, 90, -360 * Util.clamp(wd.repair, 0, 1), Arc2D.OPEN));
        }
    }

    /** Copper (rolling, cutting, waiting for you, or broken down) or the engine, with its health bar over it. */
    private void drawWard(Graphics2D g, World w, Ward wd) {
        Sprite s;
        if (wd.kind == Ward.Kind.ROBOT) {
            String key = wd.broken ? "ward.copper.broken" : wd.work > 0 ? "ward.copper.work" : wd.moving ? "ward.copper.roll" : "ward.copper";
            s = Art.frame(key, w.time, wd.work > 0 ? 14 : wd.moving ? 8 : 2);
        } else {
            s = wd.broken ? Art.frames("ward.engine.broken")[0] : Art.frame("ward.engine", w.time, 6);
        }
        s.draw(g, wd.x, wd.y, Art.SCALE, wd.faceLeft);
        if (wd.flash > 0) s.drawSilhouette(g, wd.x, wd.y, Art.SCALE, wd.faceLeft, 0xFF8A70, 0.3f);
        double top = wd.y - s.above(Art.SCALE);
        double bw = wd.kind == Ward.Kind.ROBOT ? 70 : 110, bx = wd.x - bw / 2, by = top - 16;
        g.setColor(new Color(20, 20, 22, 220));
        g.fill(new Rectangle2D.Double(bx - 2, by - 2, bw + 4, 10));
        double frac = wd.broken ? Util.clamp(wd.repair, 0, 1) : Util.clamp(wd.hp / wd.maxHp, 0, 1);
        g.setColor(wd.broken ? new Color(255, 160, 110) : frac < 0.3 ? new Color(235, 90, 70) : wd.kind == Ward.Kind.ROBOT ? new Color(255, 190, 100) : new Color(140, 210, 255));
        g.fill(new Rectangle2D.Double(bx, by, bw * frac, 6));
        g.setFont(f12);
        String name = wd.kind == Ward.Kind.ROBOT ? "COPPER" : "STASIS ENGINE";
        boolean done = w.run == null || w.run.nestsLeft <= 0;
        String state = done ? "" : wd.broken ? "BROKEN - STAND BY TO REPAIR" : wd.kind == Ward.Kind.ROBOT && wd.work > 0 ? "CUTTING THROUGH" : wd.kind == Ward.Kind.ROBOT && !wd.moving ? "WAITING FOR YOU" : "";
        centered(g, name, wd.x, by - 6, wd.kind == Ward.Kind.ROBOT ? new Color(255, 214, 150) : new Color(190, 230, 255));
        if (!state.isEmpty()) centered(g, state, wd.x, by - 22, wd.broken ? new Color(255, 150, 120) : new Color(235, 235, 245));
    }

    /** A wall of vines across Copper's way (thinning as he cuts through it, {@code left} 1..0). */
    private void drawVines(Graphics2D g, World w, double x, double y, double left) {
        Sprite s = Art.frame("ward.vines", w.time, 2);
        s.draw(g, x, y, Art.SCALE, false, 0, (float) (0.35 + 0.65 * left));
    }

    /** An Ice Storm: frost on the ground, snow drifting down and ice shards circling. */
    private void drawZone(Graphics2D g, World w, Zone z) {
        float fade = (float) Math.min(1, z.life / 0.5);
        Sprite[] frost = FxArt.frost(z.radius);
        frost[(int) (w.time * 2.5) % frost.length].draw(g, z.x, z.y, Art.SCALE, false, 0, fade);
        Sprite[] shard = Art.frames("fx.shard");
        for (int i = 0; i < 6; i++) {
            double a = w.time * 1.6 + i * Math.PI / 3;
            double rad = z.radius * 0.6;
            shard[i % shard.length].draw(g, z.x + Math.cos(a) * rad, z.y + Math.sin(a) * rad * 0.65 - 8, Art.SCALE, false, a + Math.PI / 2, fade);
        }
        Sprite[] snow = Art.frames("fx.snow");
        for (int i = 0; i < 14; i++) {
            double a = (FxArt.hash(i, 7, 1) % 628) / 100.0, d = Math.sqrt((FxArt.hash(i, 8, 2) % 100) / 100.0) * z.radius * 0.95;
            double fall = ((w.time * 46 + i * 37) % 70);
            snow[(int) (w.time * 3 + i) % snow.length].draw(g, z.x + Math.cos(a) * d + Math.sin(w.time * 2 + i) * 4,
                z.y + Math.sin(a) * d * 0.7 - 66 + fall, 2, false, 0, fade * (float) (1 - fall / 90));
        }
    }

    private void centered(Graphics2D g, String s, double cx, double baseline, Color c) {
        FontMetrics fm = g.getFontMetrics();
        float x = (float) (cx - fm.stringWidth(s) / 2.0);
        g.setColor(new Color(0, 0, 0, Math.min(200, c.getAlpha())));
        g.drawString(s, x + 1.5f, (float) baseline + 1.5f);
        g.setColor(c);
        g.drawString(s, x, (float) baseline);
    }
}
