package com.frameblend;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

@Mod(
        modid = FrameBlendMod.MODID,
        name = "Frame Blending",
        version = "1.0.0",
        acceptedMinecraftVersions = "[1.8.9]",
        guiFactory = "com.frameblend.FrameBlendGuiFactory"
)
public class FrameBlendMod {
    public static final String MODID = "frameblend";

    public static FrameBlender blender;
    private static KeyBinding toggleKey;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        FrameBlendConfig.load(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (!event.getSide().isClient()) return;

        blender = new FrameBlender();
        toggleKey = new KeyBinding("Toggle Frame Blending", Keyboard.KEY_NONE, "Frame Blending");
        ClientRegistry.registerKeyBinding(toggleKey);
        ClientCommandHandler.instance.registerCommand(new FrameBlendCommand());

        // In 1.8.9 both buses are usually the same object; registering twice is ignored.
        MinecraftForge.EVENT_BUS.register(blender);
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(blender);
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (toggleKey.isPressed()) {
            FrameBlendConfig.setEnabled(!FrameBlendConfig.enabled);
            chat("Frame blending " + (FrameBlendConfig.enabled
                    ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF"));
        }
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (MODID.equals(event.modID)) {
            FrameBlendConfig.syncAndSave();
            if (blender != null) blender.reset();
        }
    }

    public static void chat(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.AQUA + "[FrameBlend] " + EnumChatFormatting.RESET + msg));
        }
    }
}
