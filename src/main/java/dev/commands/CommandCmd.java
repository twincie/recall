package dev.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "command",
    aliases = {"-c", "--command"},
    mixinStandardHelpOptions = true,
    subcommands = {
        CommandSaveCmd.class,
        CommandRunCmd.class,
        CommandEditCmd.class,
        CommandListCmd.class
    },
    description = "Manage the command vault")
public class CommandCmd implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}