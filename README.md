# Frame Blending (Forge 1.8.9)

Blends every frame with the previous ones (accumulation motion blur), the same effect
recording tools call "frame blending", but live in-game. Works with LabyMod on Forge 1.8.9.

## Build the jar

**Option A – on your PC (Windows)**
1. Install a JDK 17 (e.g. Adoptium Temurin 17).
2. Open a terminal in this folder and run: `gradlew.bat build`
   (first run downloads Minecraft/Forge, takes a few minutes; Java 8 for compiling is fetched automatically).
3. The mod is `build/libs/frameblend-1.0.0.jar`.

**Option B – no setup, via GitHub**
1. Create a new GitHub repo and upload this whole folder (including `.github`).
2. Open the **Actions** tab, wait for "Run Gradle Build" to finish.
3. Download the `mod-jar` artifact – the jar is inside.

Put the jar in `.minecraft/mods` and launch your LabyMod Forge 1.8.9 profile.

## Use

| | |
|---|---|
| `/frameblend` | show status |
| `/frameblend on` / `off` / `toggle` | enable / disable |
| `/frameblend 60` | strength 0–95 % (higher = longer trails) |
| `/frameblend hud on` | also blur the HUD (default off: hotbar/chat stay sharp) |
| `/frameblend fps off` | apply strength per frame instead of FPS-independent |
| Controls → *Frame Blending* | bind a toggle key (unbound by default) |
| Mods → Frame Blending → Config | same settings in a GUI |

Good starting values: 40–60 for a smooth look, 75+ for heavy trails.

## Notes
- With framebuffers on (default) the trail history is stored at 16 bits per channel, so trails fade out cleanly.
- If framebuffers are off (e.g. OptiFine *Fast Render*), a fallback path is used automatically; it can leave very faint residual ghosts at high strengths. Turning Fast Render off gives the best result.
- OptiFine shader packs were not tested.
