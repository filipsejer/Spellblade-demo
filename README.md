# Spellblade

A top-down action adventure in plain Java (Swing / Java2D), with no dependencies. It's a mashup of three games:

- **Kingdom Hearts**: an explorable world between fights, with people to talk to, a story, hidden chests, and skill
  points you spend on permanent upgrades.
- **Survivor.io**: the fighting. Hordes pour in, every kill drops XP, and each level-up offers a choice of skills that
  fire on their own. Equipment with stats comes with you into every fight.
- **Risk of Rain**: each fight takes place on a battlefield generated fresh every time, with a danger clock that makes
  the monsters tougher the longer you take, caches to buy with the gold you pick up, and loops that make a cleared
  challenge harder (and better paid) every time you go back.

This is the demo: three chapters. Chapter 1 is **The Whispering Forest**; chapter 2 is **Lumen, the City of Lamps**,
down the road south; chapter 3 is **Stormcliff**, Doctor Morrow's laboratory up the coast road, in a storm that never
ends. The monsters, bosses, the forest's trees and rocks, the nests, the crates and the chests are drawn pixel art
made with [PixelLab](https://pixellab.ai) (the PNGs in `res/art`); everything else is pixel art painted by code at
startup (see **Graphics engine**), the forest's, the city's and Stormcliff's ground and scenery painted to match the
drawn art. The main menu's music and the battle music are recordings (`res/music`); all the other music and every
sound effect are synthesised by code (see **Sound engine**).

## Just want to play it?

Download [**Spellblade.jar**](https://github.com/filipsejer/Spellblade-demo/releases/latest/download/Spellblade.jar)
from the [latest release](https://github.com/filipsejer/Spellblade-demo/releases/latest) and double-click it (or run
`java -jar Spellblade.jar` in a terminal).
You need [Java](https://adoptium.net/) 17 or newer installed — no other setup, no install. See **Controls** below for how to play.

**On a Mac**, the first time you open it Gatekeeper will say it can't verify the jar is free of malware — that's just
because it isn't signed by a paid Apple Developer account, not a sign anything's wrong. Instead of double-clicking:
**Control-click `Spellblade.jar` → Open → Open** (a dialog with an actual Open button shows up this time). That only
needs doing once; after that it opens normally. If that doesn't work, **System Settings → Privacy & Security** has an
**Open Anyway** button for it near the bottom of the page.

## Run from source

```sh
./run.sh              # normal
./run.sh safe         # software rendering, memory capped, log written to game.log
./run.sh silent       # no sound at all (the sound engine is never started); can be combined: ./run.sh safe silent
```

Needs a JDK 17+. The drawn sprites are loaded from `res/` on the classpath (`run.sh` adds it). Without it, the game
still runs, with code-painted sprites in their place.

To build the jar yourself (compiled for Java 17, with the images inside), which writes `dist/Spellblade.jar`:

```sh
./build.sh
```

The jar isn't kept in the repo. For a new release, build it and attach `dist/Spellblade.jar` to a
[GitHub release](https://github.com/filipsejer/Spellblade-demo/releases); the README's download link always
follows the latest one.

## Controls

| Key | In the world | In a fight |
| --- | --- | --- |
| W A S D | Move | Move |
| E | Talk, open a chest, step up to a gate | Open a cache (if you have the gold) |
| Enter (or J) | Swing your sword (break crates for a few coins); confirm on screens | Attack. **Hold it** to keep swinging; you dash through the nearest enemy |
| Space | Roll | Roll, once you've picked **Evasive Roll** (or learned **Tumbler**) |
| 1 / 2 / 3, R | | On a level-up or chest: take that card (4 with the Ring of Fortune); R rerolls |
| Tab (hold) | The big map: the corner radar grows to show everywhere you've explored, with the areas' names | The big map of the battlefield (after a moment, since a tap locks on) |
| Tab (tap) / Q | | Lock on (cycle) / release |
| Esc | Pause: resume, **Equipment**, volumes, **Save & Quit** | Pause: resume, volumes, **Retreat** |
| M | Mute / unmute | Mute / unmute |

On the trainer's, the merchant's and the Armory's screens: **W / S** choose, **Enter** confirms, **Esc** leaves.

## The adventure

**New Game** starts the story in one of five save slots; **Continue** picks up the game you played last, where you
saved it; **Load Game** picks up any of them; **Quit** closes the game. Your gear is managed from **Equipment** in the
pause menu. You arrive at **Mossbrook**, a camp of forest folk, and walk around freely: no monsters live in the world
itself (`Worlds.forest`).

```
                       [ HOLLOW'S EDGE ]          (the gate to the Blighted Hollow)
                               |
   [ MUSHROOM RING ] -- [  OLD SHRINE  ]
                               |
   [ WEST THICKET ] -- [ MOSSBROOK CAMP ] -- [ SUNLIT PATH ] -- [ HUNTER'S CLEARING ]   (the gate to the fox dens)
                               |
                         [ RIVERBANK ]
```

- **People** (`Story.talk`): **Elder Rowan** (the story and the main quest), **Ranger Ash** (the trainer),
  **Bramble** (the merchant), **Wren** (a child with hints), **the Hermit** at the Old Shrine (lore) and **Hunter Fenn**
  (a side job). Walk up and press **E**. What they say changes as the story moves on; a gold **!** over someone means
  they have something new for you (or, over Ash, that you can afford an upgrade). Important conversations (the story moving on, or
  a quest being given) fade the screen to black and hush the music; when they're done it fades back, and the music
  carries on where it stopped. Small talk, greetings and reminders just put the box up.
- **Chests** are hidden around the forest: gold, items, and a glowing seed worth a skill point (`Story.treasure`).
  Crates and barrels break for a few coins.
- **Ranger Ash** turns skill points into **masteries** (`Mastery`): permanent upgrades that apply in every fight.
  Vitality, Might, Focus, Toughness, Swiftness, Gatherer (pickup range), Insight (XP), Fortune (gold), Tumbler (start
  every fight with the roll), Head Start (free level-up picks at the start), Second Wind (revive once per fight) and
  Second Thoughts (extra rerolls). Rank *r* costs *r* skill points.
- **Bramble** sells four items for gold, priced by rarity (`Adventure.price`); the stock changes every time you clear
  a challenge.
- The top of the screen names the area you're in and what to do next (`Story.objective`); the radar in the corner
  shows the places you've been, the people (blue; the trainer and merchant in gold), unopened chests (orange), the
  gates (purple) and the roads out (gold diamonds).
- **Roads** join the worlds (`Level.Road`). Walk up to a road's signpost and press **E** for the map of the worlds:
  each one a lit globe on a night sky, joined by a dotted road, with the ones still to come shown as "???". Pick one
  with **A** / **D** and **ENTER** to travel; you arrive where its road comes in. The south road from the Riverbank
  opens once chapter 1 is done, and the city's north road leads back, so you can go home to repeat challenges or
  train with Ash. Each world remembers where you last stood.

### Lumen, the City of Lamps (chapter 2)

A night-time city (`Worlds.city`), its lamps flickering:

```
                         [  NORTH GATE  ]                 [ CLOCKTOWER COURT ]
                                |                                  |
   [ CANAL WALK ] -- [  LANTERN SQUARE  ] ------------------- [ MARKET ROW ]
         |                      |
   [ ALLEY MOUTH ]       [ TINKER'S YARD ]      (the gates to the back alleys and the substation)
                                |
                         [ DYNAMO STEPS ]        (the gate to the Dynamo Tower)
```

- **People:** **Captain Vell** of the Watch (the story), **Tinker Juno** (the substation, and what she finds in the
  Warden), **Sable** the fencing master (the city's trainer: the same masteries as Ash), **Nix** the peddler (the
  city's merchant: the same table as Bramble's), **Pip** the newsboy (hints) and **Old Gus** the rat-catcher (a side job).
- **Chests:** six, including a spark of starlight worth a skill point in Clocktower Court.

### Stormcliff (chapter 3)

Doctor Morrow's laboratory (`Worlds.lab`), bolted onto the sea cliffs: rain driving across the screen, lightning
lighting everything up every so often (thunder rolling in after it), and the sea breaking far below. The coast road,
east of Lumen's Market Row, opens once chapter 2 is done.

```
                          [ OBSERVATORY LIFT ]          (the gate to the Observatory)
                                   |
   [ THE GREENHOUSE ] ------ [  THE ATRIUM  ] ------ [ EAST WING DOORS ]   (the gates to the greenhouse and the east wing)
                                   |
   [ LIGHTNING GARDEN ] -- [ THE GATEHOUSE ] -- [ THE SEA WALL ]
                                   |
                            [ THE CLIFF ROAD ]          (the road back to Lumen)
```

- **People:** **Doctor Ilse**, Morrow's old assistant (the story), **Copper** her surveyor robot (who you escort),
  **Fern** who keeps the greenhouse (a side job), **Brass** the sparring automaton (the trainer) and **Quill** the
  archivist (the merchant). The robots talk in beeps.
- **Chests:** five, including a sliver of starlight worth a skill point by the lift, and the grandest one lashed to
  the sea wall.

### The story so far (Chapter 1)

The stars fell, and three nights later a sickness crept out of the Hollow north of Mossbrook: the **Blight**. Thorns
that walk, flowers that bite, growing from nests. Elder Rowan asks you to go into the Hollow and tear the nests out.
Clear it and the camp thanks you with a gift (an uncommon ring, gold and skill points) and a warning: the Blight didn't
start in the forest. The road south leads to the city.

### Chapter 2

One of the stars came down through the roof of the **Dynamo**, the engine every lamp in Lumen burns on. Since then the
lamps flicker, the city's drones shoot at people, the bins bite, and the **Warden**, the iron guard built to watch the
Dynamo, has sealed its tower. Captain Vell sends you to Tinker Juno, who needs the substation's power back before the
tower's door will open. Bring the Warden down and the Watch gives you the **Lumen Saber** (an epic curved saber, the
first gift you'll see in the hero's hand), gold and skill points. Then Juno shows you what she found in the Warden's
core: new orders, *GUARD THE GARDEN*, in the hand of **Doctor Morrow**, who ran the Dynamo before her and left for a
laboratory on the cliffs. Someone is gathering the fallen stars, and planting them. The story continues there.

### Chapter 3

At Stormcliff, **Doctor Ilse** tells you the truth of it: Morrow didn't make the stars fall, but he's been collecting
the pieces and planting them, and the Blight is what grows. He's shut himself in the **Observatory** with the biggest
star of all. Its lift is dead, its power cut off in the overgrown East Wing, so you see **Copper** through it to
rewire the junction. Then Ilse's **stasis engine** has to be kept standing beside the star until it's charged enough
to freeze it, and then there's Morrow himself, in two stages. Beaten, he says he never made the stars fall: he only
caught them. Something up there is still throwing them down. Ilse gives you **Morrow's Goggles** (an epic helm), gold
and skill points, and that's the end of the demo. Every challenge can still be replayed, harder and better paid each
time.

## Challenges

A challenge (`Challenge`) is a fight you take on from the world: someone asks for help, and its **gate** (a swirl of
light, knotted with thorns until you've been asked) takes you to a battlefield. Before you go in, a card shows what's
waiting: how many nests, whether there's a guardian, the danger at the start, and the reward.

| Challenge | From | Nests | Guardian | Starting danger | First clear | After that |
| --- | --- | --- | --- | --- | --- | --- |
| **The Blighted Hollow** | Elder Rowan | 4 | the Blighted Guardian | Easy (a step above the dens) | 160 gold, 5 skill points | 70 gold, 1 skill point |
| **Fox Den Raid** | Hunter Fenn | 3 | none | Easy | 70 gold, 2 skill points | 45 gold |
| **The Back Alleys** | Old Gus | 3 | none | Easy | 110 gold, 3 skill points | 60 gold, 1 skill point |
| **The Substation** | Tinker Juno | 3 relays | none | Medium | 220 gold, 5 skill points | 90 gold, 1 skill point |
| **The Dynamo Tower** | Captain Vell | 5 | the Warden | Medium | 300 gold, 6 skill points | 110 gold, 1 skill point |
| **The Overgrown Greenhouse** | Fern | 4 specimens | none | Medium | 260 gold, 4 skill points | 110 gold, 1 skill point |
| **The East Wing** | Doctor Ilse | escort Copper (3 walls of vines) | none | Medium | 360 gold, 6 skill points | 130 gold, 1 skill point |
| **The Observatory** | Doctor Ilse | keep the stasis engine standing | Doctor Morrow | Hard | 460 gold, 8 skill points | 150 gold, 2 skill points |

**Relays** (`Relay`, the substation's goal) are the other kind of fight, Risk of Rain's teleporter three times over.
Walk up to a relay and press **E** to switch it on; it powers up (32 seconds) only while you stand inside its circle,
and only one can power up at a time. The horde comes thicker and faster out of the dark round it, in surges at a
quarter, a half (an elite) and three quarters, but the Blight hates the light: while you hold the circle, whatever
comes into it is slowed and scorched. Fully powered, a relay lights up for good and its pulse flattens the ordinary
monsters round it. A bar on the HUD shows how far it's got, and shouts when you've been pushed out.

Stormcliff's three fights each have a goal of their own:

- **Hunt** (the greenhouse): four of Morrow's **specimens**, huge glowing mutants, sleep in the far corners of the map
  until something comes near. Awake, they fight; at every quarter of their health lost they shriek, shed a pack of
  oozes and wind-up mice, and bolt. Arrows and the map show where they are.
- **Escort** (the east wing): **Copper** rolls along a route across the whole map (dashed on the ground ahead of him,
  and on the map), but only while you're beside him. Three times he stops at a wall of the Blight's vines and cuts
  through it for a few seconds while the horde surges in. Some of the horde goes for him instead of you (until you hit
  them); knocked down, he isn't lost, but he won't move until you stand by him for a few seconds, and the danger clock
  keeps ticking.
- **Defend** (the observatory): Ilse's **stasis engine** stands in the middle of the map and charges by itself (just
  under two minutes), its cold slowing whatever comes near. Surges come at each third. Knocked down, it loses some
  charge and stops until you restart it by standing next to it. Fully charged, it freezes everything near it, and
  **Doctor Morrow** arrives: the chapter's last stand, far tougher than the other guardians, in two stages.

All of Stormcliff's fights have **lightning** too: every few seconds the storm marks spots round you (one close by),
and a moment later strikes them. Standing in one hurts; any ordinary monster standing in one is flattened, so it's
worth luring them in. The storm never strikes what you're protecting.

Challenges can be fought again. Each clear is a **loop**: the next attempt starts more dangerous
(`Challenge.LOOP_DANGER`), its guardian is tougher, and it pays a little more.

## Fights

A fight (`Run`) happens on a **battlefield** made fresh every time (`Battlefield.make`): a big irregular stretch of
ground grown cell by cell on a 7x7 grid, so it has open stretches, narrow necks, loops and dead ends, with trees and
rocks to weave round. You start in a cell on its edge. The monsters know the way round it (`PathField`): one that
can see you comes straight at you, and one that can't follows the shortest route around the walls, trees, crates and
nests in between, so hiding behind a wall doesn't leave the horde stuck in a corner.

- **You start from nothing:** level 1, only your sword. Your **equipment** and Ash's **masteries** come with you.
  Every monster drops an XP gem; each level-up pauses the action and offers three cards (`Perk.offer`).
  Your level and cards are gone when the fight ends; the next one starts from nothing again.
- **The goal: destroy the nests.** The Blight's nests (`Enemy.Type.NEST`) sit in the cells furthest from where you
  start and from each other, so clearing them means crossing the map. Arrows at the screen's edge point to the ones
  you can't see. A nest doesn't fight itself, but when you come near it springs a pack of guards and keeps sending
  more while you're close. A destroyed nest bursts into gems, gold and a heart.
- **The danger clock.** The horde never stops coming, and it gets tougher the longer you take: the HUD shows the
  clock and the danger (Easy, Medium, Hard, Very Hard, Insane, Impossible). Monster health, damage and numbers grow with
  it (`Run.threat`), and with your level. Elites arrive every 80 seconds (they drop a chest), swarms every 115.
- **Caches** stand in a few cells: press **E** to open one with the gold you've picked up (a free card and maybe an
  item). Each one costs more than the last, Risk of Rain style.
- **The guardian.** With the last nest down, the ground shakes, everything ordinary vanishes (leaving its gems), and
  the challenge's guardian arrives inside a red **ring** you can't leave until it's beaten. A challenge without one
  just opens the way home.
- **The way home:** a chest (gold and an item, always, and no cards: the fighting's over) and a portal back to the
  gate you came in by. Levels gained from here on offer no cards either; each pays `Run.LATE_LEVEL_GOLD` (30 gold)
  when the fight ends.
- **The end.** Win, lose or **Retreat** (from the pause menu), you keep the gold you picked up and every item you
  found. A win also pays the challenge's reward of gold and skill points (one more skill point if you reached level 12).

**Skills** fire by themselves, up to 5 of them, 5 ranks each: **Crescent Wave** (your sword swings send piercing
waves), **Fireball**, **Lightning**, **Ice Storm**, **Orbit Blades**, **Holy Aura**, and **Healing**. **Passives**, up
to 6: **Evasive Roll**, **Combo Extension**, **Blade Mastery**, **Quick Strikes**, **Long Reach**, **Vampiric Strikes**,
**Arcane Power**, **Quick Casting**, **Vitality**, **Fleet Foot**, **Magnetism**, **Wisdom**, **Iron Skin**,
**Regeneration** and **Precision**. A skill at rank 5 plus its partner passive can **evolve**: Crescent Wave + Quick
Strikes = *Moonlit Crescent*, Fireball + Arcane Power = *Inferno Comet*, Lightning + Quick Casting = *Storm Lord*, Ice
Storm + Iron Skin = *Absolute Zero*, Orbit Blades + Blade Mastery = *Blade Tempest*, Holy Aura + Vitality = *Sanctuary*.
Chests always lead with an evolution when one is ready.

**Crates** in a fight hold gold, hearts (heal 30%), magnets (pull in every gem on the map) and bombs (wipe out
ordinary monsters on screen).

## Air combos

The game is still drawn top-down, but combat has a height axis: a combo's **finisher** (the last hit of a chain) launches
whatever it connects with into the air, and you can keep juggling it there.

- **Launching.** Only the finisher hit calls `Enemy.launch()`; the regular hits earlier in a combo never do. Heavier
  enemies (a higher `Type.resist`, the same stat that already softens knockback) go up proportionally less — a Brute
  barely leaves the ground. Armored enemies (**the guardian**, and the nests) can't be launched at all, so a boss fight is never trivialised
  into a juggle.
- **Falling.** Gravity (`Enemy.GRAVITY`) pulls it back down; the sprite draws offset above its shadow, which stays on
  the ground the whole time, so you can always see where it's about to land.
- **Juggling.** Any hit that lands on an airborne enemy — your sword or a skill, it doesn't matter — gives it a small
  upward nudge (`Enemy.JUGGLE_VZ`), so a follow-up combo (or a Fireball) keeps it up rather than letting it drop.
  Damage numbers and impact sparks float up with it.
- **While it's airborne** an enemy has no AI at all (it can't move, attack, or wind anything up), and it doesn't push
  or get pushed by anything solid (other enemies, the player, trees, crates) — it's simply out of the way until it
  comes back down. It's still a completely valid target: lock-on, the sword and skills all work on it as normal.
- **Landing** plays a thump and a puff of dust, and leaves it staggered (briefly unable to act) before its AI resumes.

Tune the height and weight of it all via `Player.LAUNCH_VZ` (how hard a finisher launches), `Enemy.GRAVITY` (how fast
it falls) and `Enemy.JUGGLE_VZ` (how much each juggle hit adds) in `Player.java` / `Enemy.java`.

## Items and the Armory

Items come from chests (the world's, elites', the guardian's, and caches), from Bramble's stall, and from Rowan's gift.

- **Six slots:** weapon, helm, armor, gloves, boots, ring. Each slot has a main stat (melee damage, skill damage, max HP,
  attack speed, move speed, crit chance) plus more random lines — damage taken, regeneration, pickup range, XP gain,
  skill cooldowns, gold found.
- **Rarity:** Common, Uncommon, Rare, Epic, Legendary — one, two, three, four and four stat lines, each rarity's
  numbers bigger than the last. Rolls only stray 10% either side of the middle, so of two items from fights of the same
  difficulty (and upgraded as far), the rarer is always the stronger. Items from harder fights (the Substation and the
  Tower, or a fight cleared before) are 25% stronger per step.
- **Rarity by world:** the forest only gives commons and uncommons. The city gives mostly uncommons and rares, epics now
  and then (more from a guardian), and its guardian the odd legendary. Clearing a fight again tilts its drops further
  toward the top. Bramble's and Nix's table follows the world of the last fight won. Each **Legendary** also has
  a power of its own: *Dawnbreaker* (fights start with Crescent Wave), *Crown of Insight* (+2 rerolls), *Phoenix Mail*
  (revive once per fight), *Tempest Gauntlets* (start with a 3-hit combo), *Windwalkers* (start with the roll), *Ring of
  Fortune* (4 cards per level-up).
- **The Armory** (**Equipment** on the pause menu, out in the world): **Enter** equips or takes off, **U** spends gold
  to upgrade an item (+12% of its stats per level, up to +10), **X** twice salvages it for gold. The bag holds 60.
- **Comparing:** in the Armory and the shops, the item you're looking at is set against what you wear in the same slot,
  stat by stat: a green arrow for a gain, a red one for a loss. Stats only the worn item has are struck through, and so
  is its legendary power, as what you'd give up.

## Saving

The game saves itself whenever something changes in the world (talking, chests, training, buying, travelling), when you go into a
fight and come back out, on **Save & Quit**, and when the window closes. A fight in progress isn't saved: closing the
window mid-fight puts you back at its gate next time.

There are five **save slots** (`Saves`), each a separate game with its own story, gold and gear. **New Game** and
**Load Game** show them as cards: where that game is, what's next, how many challenges it has cleared, its gold, skill
points and worn gear, and when it was saved. New Game starts on the first empty slot; starting over a slot that holds a
game asks for a second press first. In Load Game, **X** (twice) deletes a game. **Continue** loads the slot played last.

Each slot is a folder in `~/.spellblade` (`save1` ... `save5`) with two files: `adventure.properties` (the story, chests
opened, challenges cleared, skill points, masteries, the merchants' stock, which world you're in and where you stood in
each) and `profile.properties` (gold and equipment). `slots.properties` remembers which slot was played last. A save
from before there were slots is moved into slot 1 the first time the game starts. The volume settings are kept beside
them, in `audio.properties`. `-Dspellblade.home=<folder>` moves it all.

## Layout

| File | What it does |
| --- | --- |
| `Main.java` / `GamePanel.java` / `Input.java` | The window and its icon, the fixed-step game loop (60 updates a second), and the keyboard |
| `World.java` | Game state and rules: the main menu, walking around the world (talking, chests, gates), the trainer's and merchant's screens, starting and ending fights, collisions, the pause menu, the Armory, saving. `World` has no drawing code |
| `Saves.java` | The five save slots: which one is in use, which was played last, what's in each (for the slot screen), deleting one, and moving an old save into slot 1 |
| `Adventure.java` | The story so far (flags), chests opened, challenges cleared, skill points, masteries, Bramble's stock, where you stood; saving and loading it |
| `Story.java` | All the words: what everyone says as the story moves on, what the chests hold, the objective line, the intro and Rowan's gift |
| `Worlds.java` | The explorable worlds (`forest()`, `city()` and `lab()`: their areas, people, chests, gates and roads), their names, and the main menu's clearing |
| `Challenge.java` | The challenges: who asks, which world, the goal (nests, relays, a hunt, an escort or a defence), the guardian, the danger, the rewards |
| `Relay.java` | A substation relay's state: switched on, powering up, held, done |
| `Ward.java` | What you protect at Stormcliff: Copper and his route, or the stasis engine and its charge; broken and repaired |
| `Battlefield.java` | Generates a challenge's battlefield: the shape, the nests, the scenery, the caches |
| `Run.java` | One fight: the director (spawning, the danger clock, elites, swarms, the nests' guards), drops, pickups and caches, the guardian's ring, level-up choices, and the end of the fight |
| `Mastery.java` | Ranger Ash's permanent upgrades |
| `Perk.java` / `Arsenal.java` | Everything a level-up can offer (skills, passives, evolutions) and the self-firing skills' numbers per rank |
| `Pickup.java` | Gems, gold, hearts, magnets, bombs, chests, caches and the portal |
| `Item.java` / `Profile.java` | Equipment (slots, rarities, stats, legendaries, the odds of each rarity per world) and the profile (gold, the bag, what's worn) |
| `ItemCompare.java` | An item's stats set against what you wear in the same slot, for the Armory's and the shops' close-up |
| `Level.java` | A map: rooms (each one or more rectangles), corridors, people, chests, gates, scenery, and walkable-area collision |
| `Player.java` | Movement, the combo, dash-through attacks, the roll |
| `Enemy.java` | Enemy types (`Type` holds the stats), their AI, the guardian, and the nests |
| `Projectile.java` / `Blast.java` / `Zone.java` | Shots, the scientist's flask bombs and Stormcliff's lightning strikes, and lingering ground areas (Ice Storm) |
| `Breakable.java` | Crates and barrels: solid, broken in one hit, a little XP and gold |
| `Util.java` | Small helpers: vectors, distances and angles, clamping, colour fading and mixing |
| `PathField.java` | How monsters find you around walls, crates and nests: one shortest-route map (a flow field) for the whole horde, rebuilt as you move |
| `Renderer.java` | Draws a frame: the world, then the HUD and whichever screen is up, and the speech box |
| `WorldRenderer.java` | Draws the world itself: level, shadows, depth-sorted sprites, people, chests, gates, prompts, telegraphs, projectiles, effects, health bars |
| `LevelView.java` | The level's background: bakes floors, walls, props and scenery into cached image chunks, and glows |
| `WorldHud.java` | The world's HUD, pause menu, the trainer's and merchant's screens, and the card before a challenge |
| `RunHud.java` | A fight's HUD, the level-up cards, the fight's pause menu, the results screen, and the perk icons |
| `TitleScreen.java` / `ArmoryScreen.java` | The main menu (on its dusk backdrop), the save slots, and the Armory |
| `MenuStyle.java` | The look the screens share: gold headings, gliding selection rows, key caps, glass cards, the serif font |
| `Minimap.java` | The round radar in the top-right corner |
| `Dialogue.java` | The speech box's rules: typewriter text, lines that wait for a key or are called out, the voice's chirps |
| `PixelCanvas.java` / `Sprite.java` / `Art.java` | The graphics engine: a pixel painting canvas, an anchored sprite, and the sprite atlas |
| `Baked.java` | Drawings that rarely change (cards, skill slots, shadows, the radar's bezel) drawn once into an image and reused, so the CPU isn't redrawing smooth shapes 60 times a second |
| `ImportedArt.java` / `res/art/` | The drawn (PixelLab) sprites and the poses made from them |
| `PeopleArt.java` / `TownArt.java` / `CityFolk.java` / `LabFolk.java` / `Doll.java` | The hero, the forest's people and stalls, Lumen's (Vell, Juno, Gus, Pip, Sable's and Nix's counters) and Stormcliff's (Ilse, Fern, Brass's and Quill's counters), painted at the drawn art's pixel size and shaded like it by `Doll` |
| `SwordArt.java` | The hero's swords, drawn apart from him: one for each kind of weapon and rarity, painted at whatever angle a pose holds it |
| `CreatureArt.java` / `FxArt.java` / `RunArt.java` / `BreakableArt.java` | Painted enemies (the fallback for the drawn ones), effects, pickups and item icons, crates |
| `Effect.java` / `PixelFont.java` | Short-lived visuals (slashes, bursts, particles, damage numbers) and the tiny font the numbers are drawn in |
| `Theme.java` / `ThemeArt.java` / `ForestArt.java` / `CityArt.java` / `LabArt.java` | The forest, city and Stormcliff looks: floors, walls, colours, scenery, and Copper, the stasis engine and the vine walls (`LabArt`), all painted to match the drawn art |
| `Music*.java`, `Song*.java`, `Track.java`, `Instruments.java`, `Sfx*.java`, `Sx.java`, `Audio*.java`, `GameAudio.java`, `Dsp.java`, `Snd.java` | The sound engine (below) |

## Graphics engine

Apart from the drawn art (see **Drawn art** below), everything you see is painted by `game/*Art.java` code into small pixel images (`PixelCanvas`), scaled up 3x with
nearest-neighbour filtering so the pixels stay crisp (`Art.SCALE`). The images are built once when the game starts.

- **`PixelCanvas`** is a grid of ARGB pixels with drawing helpers: rectangles, ellipses, lines, polygons, `outline`
  (a dark border around a shape), `bevel` (light top-left, dark bottom-right shading), mirroring and colour mixing.
- **`Sprite`** is a frame plus an *anchor* (usually the feet). It draws scaled, mirrored, rotated or as a flat-colour
  silhouette (hit flashes, ghosts, status tints), and caches the silhouettes.
- **`Art`** is the atlas: name to animation frames, e.g. `hero.side.walk`, `forest.brute.windup`, `city.boss2.slam`.
  `Art.frame(name, seconds, fps)` picks the current frame; asking for a name that doesn't exist throws.
- **Characters** face right and are mirrored to face left. The hero has front, back and side views (idle, walk, attack)
  and a roll; enemies have a walk cycle and a wind-up pose that shows before an attack. Bosses have idle, walk, slam
  and burst poses, and a second, angrier look under half health.
- **Maps** are baked into 1024x1024 image chunks the first time you look at them, so drawing the map costs a few
  image copies per frame instead of thousands of tile draws. Only the props that animate, glows and everything that
  moves are drawn live.
- **Depth:** characters, people, chests and gates are sorted by their feet and each gets a ground shadow. So are the
  forest's trees and rocks around the clearings, so a tree hides whoever walks behind it.
- **Lighting:** a colour wash for the mood (warm dappled light in the forest, blue dusk in the city), glows for
  lamps / neon / fire / lightning, and a vignette at the edges.
- **Themes:** `Level.theme` picks the art set. The forest has grass, cobbles, dirt paths, hedges and tree canopy (see
  **The forest's look** below), the city cobbled streets lined with houses (see **The city's look**), and Stormcliff
  wet slate walks between the laboratory's buildings above the sea (see **Stormcliff's look**). Enemies change with the theme too: toadstools, foxes, snap-blooms, stump golems,
  the Blight's nests and the Treant in the forest; rats, cats, drones, dumpsters and the Warden robot in the city; green
  oozes, wind-up mice, acid flasks, hulking green mutants and the Mad Scientist in the lab. The shade is the same in all three.

### Drawn art

The monsters and the props you fight among are drawn pixel art, made with PixelLab and loaded by `ImportedArt` from
`res/art`. Each PNG is a horizontal strip of frames. `res/art/anchors.properties` gives each strip's anchor (the feet,
in the frame's pixels) and its frame count. A strip replaces the painted sprite of the same name, with `_` for `.`
(`crate_forest.png` becomes `crate.forest`). Any sprite without a PNG keeps its painted look.

- **Finer pixels.** One pixel of drawn art is 2 world units (`ImportedArt.PIXEL`), against 3 for the painted art.
  `Sprite.k` holds that ratio, so code keeps drawing everything at `Art.SCALE` and both kinds come out the right size.
  An elite is drawn 1.5x so its pixels stay even.
- **The monsters** have one drawn pose each. Their walk (a squash and a bob) and their wind-up (rearing back) are made
  from it in code.
- **The bosses** have one drawn pose each too. Idle, walk, slam and burst are made from it, and so is the second
  phase under half health: the Treant's leaves turn autumn orange, the Warden runs red, and the Mad Scientist's
  coat and hair go a sickly green. A `<theme>_boss2.png` would replace that recolour.
- **Scenery:** the forest's oak, pine, bush, boulder and stump (`landmark_*`), the Blight's nest (`forest_nest`, which
  pulses in code), the crates and barrels of all three themes, and the chests. The city and lab crates are recolours of the forest's.
- The images were cleaned up before they were added: baked-in drop shadows and stray specks removed, each cropped to
  its pixels, and the alley cat's all-black body lightened so it shows on the dark streets.

### The forest's look

`ForestArt` paints the forest's ground and small scenery to sit with the drawn art: the same 2-unit pixels, a palette
sampled from the sprites (yellow-green foliage over blue-green shadow, warm bark, olive moss, blue-grey stone), shading in
a few hard tones with ordered dithering between them, and near-black outlines on anything that stands up off the ground.

- **Floors** are large seamless textures rather than small tiles: grass (broad drifts of light and shade under hundreds
  of tufts of blades), packed-earth paths, the camp's cobbles (a seamless Voronoi pattern, each stone lit along its top,
  moss in the gaps), the leafy hedge that walls the clearings in, and the dark canopy beyond. Soft patches of lighter and
  darker grass and the odd bare spot are scattered over the grass to hide the repeat, and grass grows in over the paths' edges.
- **Small things on the floor:** flowers, clumps of grass, ferns, clover, fallen leaves, rocks and toadstools, painted at
  the fine size.
- **Around the clearings** stand the drawn oak and pine, the oak recoloured for autumn and for deep shade, and the drawn
  bush, boulder and stump, with a painted log and a ring of toadstools.

### The city's look

`CityArt` paints Lumen the same way: the drawn art's 2-unit pixels, hard tones with dithering between them, outlines on
anything standing up, and a blue night wash over it all with warm pools of lamplight.

- **Floors:** blue-grey cobbles with weeds in the cracks for the streets, big sandstone slabs for the squares, red brick
  for the lanes between, granite kerbs at the edges, and beyond them the city's rooftops seen from above (slate and
  terracotta, gutters, chimneys and lit skylights). The canal is dark water behind a stone kerb. Damp and grime
  patches, puddles, drains, manholes, litter, weeds and pigeons break up the streets.
- **House fronts** line the top edge of every street, side by side: timber-framed plaster, brick shops with striped
  awnings and lit windows full of goods, blue houses with balconies, narrow rose houses with shutters, a pitched roof
  and chimney on each, some windows lit and some dark. Where the gap behind a street is short, a one-storey shop goes
  there instead, so a house never hides ground you can walk on. The other edges get crates, barrels, planters and bins.
  The city's battlefields are built the same way, so a fight there runs through streets between houses.
- **In the streets:** iron lamps (each lights the ground round it), the square's fountain, planters with little trees,
  benches, hydrants, market stalls under striped canopies, a greengrocer's cart, the stopped clock tower, Juno's scrap
  and pylon, signposts at the roads, and the substation's relays (wrapped in the Blight's vines until they're powered),
  all shaded by a `Doll`.

### Stormcliff's look

`LabArt` paints the laboratory the same way, under a dark blue storm wash with rain and lightning drawn over it all
(`Renderer.drawWeather`).

- **Floors:** big wet slate flagstones outdoors, shining where the rain catches them, with puddles in the joints;
  cream tiles with little teal diamonds indoors (the atrium, the lift hall, the greenhouse); iron grating on the walks
  between. Beyond the walls is the storm-tossed sea far below, streaked with foam, and the walls are the cliff's dark
  basalt sea wall with moss in the joints. Under the sea wall, the breakers churn.
- **The laboratory's buildings** line the top edge of every walk: dark brick wings with tall arched windows lit green,
  stone towers wound with copper pipe under little verdigris domes, glass conservatories in white iron with plants
  pressed against the panes, and corrugated machine houses with riveted doors and pressure gauges. Every roof bristles
  with lightning rods. The other edges get tesla coils, valves, gas cylinders, specimen tanks, crates and wet rocks.
- **Standing about:** the atrium's great brass orrery, tesla coils, lightning rods, specimen tanks (something curled
  up in each, one eye open), workbenches of bubbling flasks, planters of glowing star-plants, telescopes and cables.
  The coils, tanks, star-plants and the orrery glow in the dark.
- **In the fights:** Copper (standing, rolling, cutting with his torch, broken down with his lamp dark), the stasis
  engine (cold blue light swirling in its glass column; dark and cracked when broken) and the Blight's walls of vines.

### The people

The hero and the forest's people are painted in code, but on the drawn art's finer grid (`PeopleArt.Fine`) and shaded
the way the drawn sprites are, by `Doll`. A doll is drawn in *materials* (skin, hair, cloth, leather, steel, gold), each
with a hand-picked five-tone ramp that cools into shadow and warms into light, and each part (a leg, an arm, the face)
is its own piece laid over the ones before. Rendering then lights every piece from the top left, puts a glint on round
things, darkens what lies just under a piece in front (the head's shadow on the collar), runs a dark line wherever a
piece overlaps another, and outlines the figure in near-black. Pieces that should read as one shape (a face over the
head, a fringe over the hair) are joined so no seam shows. Faces are shared: big two-tone eyes with a white glint,
blush and a small mouth, so the hero, Rowan, Fenn, Wren, the Hermit, Ash and Bramble all look like one cast.

**The sword is its own sprite.** The hero's frames are painted empty-handed, and each records where the sword goes
(`PeopleArt.Grip`: the middle of the grip, the way the blade points, and whether it's behind him, resting at his hip or
held out in front of him while we see his back, or in his fist). `SwordArt` paints swords from a description of their
parts (`SwordArt.Kind`: blade length, width and curve, guard, grip, pommel, their materials, and a colour for glowing
runes) at whatever angle a pose needs, with crisp pixel lines rather than by rotating a picture, and keeps each one
once painted. The hero carries the sword for the weapon he wears: its name's last word gives the shape (a plain
*Sword*, a short broad *Blade*, a curved *Saber*, a *Longsword* with a wide cross-guard) and its rarity the metal
(iron, steel, blue steel with a gold guard, violet steel with glowing runes, sunsteel with a ruby). With nothing worn
he carries the traveller's sword. To add a sword, add a `Kind`.

The effects and the pickups are still the older painted art.

To add a sprite: paint it in the matching `*Art` class, register it under a name, and ask the atlas for that name where
it's drawn. To add a theme: add it to the `Theme` enum, give it tiles and props in `ThemeArt`, and draw the enemies
in `CreatureArt`.

## Sound engine

Everything you hear except the two recorded themes is made by code, in real time or when the game starts, with `javax.sound` (part of the JDK) only used to
send the finished audio to the speakers. **Sound never slows the game down**: the game thread only drops requests in a queue,
and a separate audio thread mixes 44.1 kHz stereo in 512-frame blocks (about 11 ms). If there is no sound device (or the device
fails) the game just runs silent; if something goes wrong in one block the audio thread plays silence for it and carries on.
The sound effects are painted in about half a second on the audio thread, in parallel with opening the speakers, so the window
never waits (on a Mac the very first open of the speakers can take a few seconds; music and sounds start when it's ready).

- **Two recordings:** the main menu's theme (`res/music/main_menu.wav`, 96 s) and the battle theme every fight plays, in
  every world (`res/music/battle.wav`, 32 bars at 162 bpm). Each (`Track`) is read on a background thread the first time
  it's wanted, so nothing waits for it, then looped from the top, and crossfades like any other piece. A recording doesn't
  swell with the action the way the written pieces do. If a file is missing, the written piece it replaced plays instead:
  the forest's tune on the menu, or the world's own battle theme in a fight.
- **The written music** is nine pieces, of 16 or 32 bars, written by hand as notes over a chord progression, played live on the synthesised
  instruments. Every piece is split into *layers* (pad, bass, melody, drums...) and each layer has a volume for each mood, so the
  score changes with the action without ever restarting or losing the beat:
  - **Forest** (G major, 96 bpm): a flute tune over harp and a warm pad, for wandering the forest.
  - **Forest battle** (D minor, 152 bpm, 32 bars), in the spirit of Kingdom Hearts' battle music: a driving piano ostinato and
    staccato strings in 3+3+2, a soaring string melody, a heroic brass strain in the relative major, and orchestral drums with
    rolls at the end of each phrase. A fight opens on four bars of piano alone; while you're taking a nest down, horns swell
    under it and the cymbals open up. The piano is its own synthesised voice: stretched partials that each fade at their own
    rate, a felt hammer, and a damper.
  - **City** (A minor, 100 bpm): an electric-piano tune over a synth pad and echoing arpeggios, for walking Lumen's streets.
  - **City battle** (A minor, 148 bpm, 32 bars): Kingdom Hearts' town fights by way of a jazz club. Electric piano comping,
    a walking bass and syncopated stabs under a string melody that grows out of the city's own tune, then a heroic brass
    strain in the relative major, over the city's drum machine with a swung hat. It opens on the piano alone, and swells
    (horns, an arpeggio, open hats) while you hold a relay or take a nest down.
  - **Laboratory** (D dorian, 108 bpm): a theremin over a synth pad and a ticking clock, for walking Stormcliff.
  - **Stormcliff battle** (D minor, 160 bpm, 32 bars): a synth toccata in sixteenths and organ stabs over a growling
    bass; the theremin carries a melody grown out of the laboratory's own tune, then brass takes a strain over the
    storm, with war drums rolling like thunder into every phrase. It swells while a specimen is awake nearby, while
    Copper cuts through the vines, and while the engine charges.
  - **Forest boss** (E minor, 120 bpm, brass and war drums), **city boss** (D minor, 128 bpm, saw lead and electronic beat) and the **Mad Scientist** (A harmonic minor, 144 bpm, a frantic organ toccata over growling bass).
    Below half health the boss music brings in a second wave of parts (`Mood.PEAK`).
  - `MusicDirector` picks the piece and mood from the game state: calm while exploring, the world's battle theme in a fight,
    the boss piece for the guardian, the calm theme
    again once the way home is open, silence after a defeat; pausing (and the world's screens) muffles and lowers the music.
    An important conversation *hushes* it: the song fades out but keeps its place, and picks up from there when the talk ends.
- **Sound effects** (`Snd` lists them all): swings, hits, skills, enemy tells and attacks, deaths, boss events, nests,
  jingles, menu blips, footsteps (soft on forest grass, hard on the camp's paving and in the city). Each has several variants that are taken
  in turn, plus a little random pitch, so nothing ever repeats exactly. Enemy sounds are placed in the stereo field and get quieter
  with distance. Sounds have a minimum gap and a voice limit so a crowd can't machine-gun one sound, and a voice that is taken over
  fades out in 10 ms rather than being cut.
- **Ambience** is made live from filtered noise: wind and the odd bird in the forest, a low city hum and distant traffic and
  the odd horn in the city, and at Stormcliff rain hissing on stone, heavy drops pattering close by, the sea breaking far below in slow swells and now and then a beep from a machine (the thunder is the lightning's own). It ducks itself during fights.
- **Mixing:** music and effects are balanced by *A-weighted loudness* (how loud a sound seems to an ear, not how much energy it has),
  so a swish, a thump and a jingle of the same number seem equally loud. Big effects duck the music by up to about 3 dB; a
  look-ahead limiter keeps the output under 0.995 whatever happens; DC offset and sub-bass rumble are filtered out; deep booms
  get overtones added so they still work on laptop speakers.
- **Key rule:** every jingle and interface blip uses only the notes C D E G A, which belong to all four keys, so an effect can
  never clash with the music underneath it.

To change the sound: melodies and chords are in `Songs.java` (each bar of a melody must add up to 16 sixteenths, checked when the
song is built); a part's volume per mood is the three numbers after its name, and its balance is in the `TRIM` table (re-measure
with the sound tests after changing an instrument). Sound effect recipes are in `SfxSynth.java`; how loud each is meant to be is the
first number in `Snd.java`. To replace a recording, put the new file in its place in `res/music` (uncompressed PCM
at 44.1 kHz, 16- or 24-bit; 16-bit keeps the download smaller) and set `Songs.MAIN_MENU_TRIM` or `BATTLE_TRIM` so it's
as loud as the rest (by A-weighted loudness, like the other pieces). Default volumes are in `AudioSettings.java`; the saved settings are in `~/.spellblade/audio.properties`.

## Where to tune things

- **The worlds:** areas, people, chests, gates and roads are placed in `Worlds.forest()` and `Worlds.city()`; what
  everyone says, what the chests hold, the objectives and when each road opens are in `Story.java`.
- **Challenges:** nests, danger, rewards, the favoured monster and the battlefield's size are the arguments in
  `Challenge.java`; `Challenge.LOOP_DANGER` is what each clear adds.
- **Relays:** how long one takes to power up, its circle, and how often its light scorches what's in it are `Relay.CHARGE_TIME`,
  `RADIUS` and `ZAP_EVERY`; how much thicker the horde comes while one powers up is in `Run.direct`.
- **Battlefield shape:** `Battlefield.GRID` / `CELL`, the passage widths and the scenery counts in `Battlefield.make`.
- **Fight difficulty:** `Run.threat` (the danger clock), `Run.hpMult` / `dmgMult` / `population`, `Run.pickType`,
  `ELITE_EVERY` / `SWARM_EVERY`, the guardian's multipliers in `Run.spawnBoss`; nests: `Enemy.Type.NEST` (health),
  `Run.NEST_RANGE` and the guard counts in `Run.updateNests`.
- **Caches:** prices in `Run.priceCaches`.
- **Masteries:** `Mastery.java` (effects in `applyAll`; costs in `cost`).
- **Bramble:** stock odds in `Adventure.restock`, prices in `Adventure.price`.
- **Level-up curve:** `Run.xpFor`; gem values in `Run.dropFor`.
- **Skills:** the per-rank arrays at the top of `Arsenal.java` (keep the text in `Perk.java` in step); passives' effects
  in `Perk.apply`.
- **Loot:** the rarity weights in `Run.openEliteChest` / `openBossChest` / `interact`; stat ranges in `Item.Stat`;
  upgrade and salvage prices in `Item.upgradeCost` / `salvageValue`.
- **Enemy stats:** the `Type` enum in `Enemy.java`; the guardian's attacks are `updateBoss()` / `fireBurst()`.
- **Combo timing and damage:** constants at the top of `Player.java`, plus `startAttack()` / `doHit()`.
- **Air combos:** `Player.LAUNCH_VZ` / `Enemy.GRAVITY` / `Enemy.JUGGLE_VZ`.
