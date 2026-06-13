package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;

@Command(name = "save", description = "Save a command to the vault")
public class CommandSaveCmd implements Runnable {
    @Parameters(index = "0", description = "Name for the command")
    private String name;

    @Parameters(index = "1", description = "The command to save")
    private String command;

    private final StorageService storageService;

    public CommandSaveCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            String slug = StorageService.makeSlug(name);
            String filename = storageService.append("commands.md", slug, "command", "", command);

            String msg = CommandLine.Help.Ansi.AUTO.string(
                "@|green \u2713 Saved command  [|@@|bold,yellow " + slug + "|@@|green ]|@  " + filename);
            System.out.println(msg);
        } catch (IOException e) {
            System.err.println("Failed to save command: " + e.getMessage());
        }
    }
}