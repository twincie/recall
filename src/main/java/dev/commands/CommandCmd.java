package dev.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "command",
    subcommands = {
        CommandSaveCmd.class,
        CommandRunCmd.class
    },
    description = "Manage the command vault")
public class CommandCmd implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}