package com.frameblend;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;

public final class FrameBlendConfig {
    public static final String CATEGORY = Configuration.CATEGORY_GENERAL;

    public static Configuration config;

    public static boolean enabled = true;
    /** 0-95. How much of the previous frames is kept. Higher = longer trails. */
    public static int strength = 60;
    /** Keep the blur length the same regardless of FPS (strength is measured at 60 FPS). */
    public static boolean fpsIndependent = true;
    /** Also blend the HUD (hotbar, chat, scoreboard). Off keeps the HUD sharp. */
    public static boolean blendHud = false;

    private static Property pEnabled, pStrength, pFpsIndependent, pBlendHud;

    private FrameBlendConfig() {}

    public static void load(File file) {
        config = new Configuration(file);
        config.load();
        pEnabled = config.get(CATEGORY, "enabled", true, "Turn frame blending on or off.");
        pStrength = config.get(CATEGORY, "strength", 60,
                "Blur strength in percent (0-95). Higher values leave longer trails.", 0, 95);
        pFpsIndependent = config.get(CATEGORY, "fpsIndependent", true,
                "Keep the trail length the same at any FPS (strength is tuned for 60 FPS). "
                        + "If false, strength is applied per frame, so higher FPS = shorter trails.");
        pBlendHud = config.get(CATEGORY, "blendHud", false,
                "Also blend the HUD (hotbar, chat, scoreboard). Off keeps the HUD sharp.");
        syncAndSave();
    }

    public static void syncAndSave() {
        enabled = pEnabled.getBoolean();
        strength = Math.max(0, Math.min(95, pStrength.getInt()));
        fpsIndependent = pFpsIndependent.getBoolean();
        blendHud = pBlendHud.getBoolean();
        if (config.hasChanged()) config.save();
    }

    public static void setEnabled(boolean v) {
        pEnabled.set(v);
        syncAndSave();
    }

    public static void setStrength(int v) {
        pStrength.set(Math.max(0, Math.min(95, v)));
        syncAndSave();
    }

    public static void setBlendHud(boolean v) {
        pBlendHud.set(v);
        syncAndSave();
    }

    public static void setFpsIndependent(boolean v) {
        pFpsIndependent.set(v);
        syncAndSave();
    }
}
