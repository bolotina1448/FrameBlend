package com.frameblend;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;

import java.util.Set;

/** Adds a "Config" button for this mod in the Forge mod list. */
public class FrameBlendGuiFactory implements IModGuiFactory {
    @Override
    public void initialize(Minecraft minecraftInstance) {}

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return ConfigScreen.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }

    public static class ConfigScreen extends GuiConfig {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public ConfigScreen(GuiScreen parent) {
            super(parent,
                    new ConfigElement(FrameBlendConfig.config.getCategory(FrameBlendConfig.CATEGORY)).getChildElements(),
                    FrameBlendMod.MODID, false, false, "Frame Blending");
        }
    }
}
