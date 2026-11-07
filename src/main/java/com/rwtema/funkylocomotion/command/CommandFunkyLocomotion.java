package com.rwtema.funkylocomotion.command;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import com.rwtema.funkylocomotion.FunkyLocomotion;

public class CommandFunkyLocomotion extends CommandBase {

    @Override
    public String getCommandName() {
        return "funkylocomotion";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/funkylocomotion reload";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("fl");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1 || !"reload".equalsIgnoreCase(args[0])) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }

        FunkyLocomotion.reloadConfig();
        sender.addChatMessage(new ChatComponentText("Funky Locomotion config reloaded."));
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return sender.canCommandSenderUseCommand(2, getCommandName());
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "reload") : null;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    // No compareTo override: CommandBase already implements it correctly by
    // comparing getCommandName() against any ICommand, not just instances of
    // this class. The previous override cast the argument to
    // CommandFunkyLocomotion, so sorting commands of different types
    // (e.g. HelpFixer validating/sorting all commands) threw
    // ClassCastException at server startup.
}
