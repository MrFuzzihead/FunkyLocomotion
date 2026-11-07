package com.rwtema.funkylocomotion.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;

import org.junit.Test;

/**
 * Regression test for the HelpFixer ClassCastException:
 * {@code CommandFunkyLocomotion.compareTo} used to cast the argument to
 * {@code CommandFunkyLocomotion}, so comparing it against any other command
 * (as HelpFixer / command sorting does) threw ClassCastException.
 */
public class CommandFunkyLocomotionTest {

    /** Minimal stand-in for another mod's command, e.g. HelpFixer's own. */
    private static CommandBase namedCommand(final String name) {
        return new CommandBase() {
            @Override
            public String getCommandName() {
                return name;
            }

            @Override
            public String getCommandUsage(ICommandSender sender) {
                return "/" + name;
            }

            @Override
            public void processCommand(ICommandSender sender, String[] args) {}
        };
    }

    @Test
    public void compareToOtherCommandTypesDoesNotThrowClassCastException() {
        CommandFunkyLocomotion command = new CommandFunkyLocomotion();

        // These calls previously threw ClassCastException.
        assertTrue(command.compareTo(namedCommand("aaa")) > 0);
        assertTrue(command.compareTo(namedCommand("zzz")) < 0);
        assertEquals(0, command.compareTo(namedCommand("funkylocomotion")));
    }

    @Test
    public void compareToSelfIsZero() {
        CommandFunkyLocomotion command = new CommandFunkyLocomotion();
        assertEquals(0, command.compareTo(command));
        assertEquals(0, command.compareTo(new CommandFunkyLocomotion()));
    }

    @Test
    public void sortingMixedCommandTypesOrdersByName() {
        List<ICommand> commands = new ArrayList<ICommand>(
            Arrays.<ICommand>asList(
                namedCommand("zzz"),
                new CommandFunkyLocomotion(),
                namedCommand("aaa")));
        Collections.sort(commands);

        assertEquals("aaa", commands.get(0).getCommandName());
        assertEquals("funkylocomotion", commands.get(1).getCommandName());
        assertEquals("zzz", commands.get(2).getCommandName());
    }

    @Test
    public void commandNameAndAliasesUnchanged() {
        CommandFunkyLocomotion command = new CommandFunkyLocomotion();
        assertEquals("funkylocomotion", command.getCommandName());
        assertEquals(Arrays.asList("fl"), command.getCommandAliases());
    }
}
