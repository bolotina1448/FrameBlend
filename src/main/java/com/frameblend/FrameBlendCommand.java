package com.frameblend;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;

public class FrameBlendCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "frameblend";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/frameblend [on|off|toggle|<0-95>|hud <on|off>|fps <on|off>]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            status();
            FrameBlendMod.chat(EnumChatFormatting.GRAY + getCommandUsage(sender));
            return;
        }
        String a = args[0].toLowerCase();
        if (a.equals("on") || a.equals("off") || a.equals("toggle")) {
            FrameBlendConfig.setEnabled(a.equals("toggle") ? !FrameBlendConfig.enabled : a.equals("on"));
        } else if (a.equals("hud") && args.length >= 2) {
            FrameBlendConfig.setBlendHud(parseOnOff(args[1]));
        } else if (a.equals("fps") && args.length >= 2) {
            FrameBlendConfig.setFpsIndependent(parseOnOff(args[1]));
        } else {
            int value = parseInt(a, 0, 95);
            FrameBlendConfig.setStrength(value);
            if (value > 0 && !FrameBlendConfig.enabled) FrameBlendConfig.setEnabled(true);
        }
        if (FrameBlendMod.blender != null) FrameBlendMod.blender.reset();
        status();
    }

    private static boolean parseOnOff(String s) throws CommandException {
        s = s.toLowerCase();
        if (s.equals("on") || s.equals("true")) return true;
        if (s.equals("off") || s.equals("false")) return false;
        throw new CommandException("Expected on or off, got " + s);
    }

    private static void status() {
        FrameBlendMod.chat((FrameBlendConfig.enabled ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF")
                + EnumChatFormatting.RESET + " | strength " + FrameBlendConfig.strength + "%"
                + " | HUD blend " + (FrameBlendConfig.blendHud ? "on" : "off")
                + " | FPS-independent " + (FrameBlendConfig.fpsIndependent ? "on" : "off"));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "on", "off", "toggle", "hud", "fps", "30", "50", "60", "75");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("hud") || args[0].equalsIgnoreCase("fps"))) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        return null;
    }
}
