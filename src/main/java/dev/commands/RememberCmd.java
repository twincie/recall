package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;

@Command(name = "remember", description = "Store a note in your engineering memory")
public class RememberCmd implements Runnable {
    @Parameters(description = "The note content", arity = "1..*")
    private String[] parts;

    @CommandLine.Option(names = {"--tags"}, description = "Comma-separated tags")
    private String tags = "";

    private final StorageService storageService;

    public RememberCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            String text = String.join(" ", parts);
            String slug = StorageService.makeSlug(
                text.length() > 60 ? text.substring(0, 60) : text);

            String filename = storageService.append("notes.md", slug, "note", tags, text);

            String message = CommandLine.Help.Ansi.AUTO.string(
                "@|green \u2713 Saved  [|@@|bold,yellow " + slug + "|@@|green ]|@  " + filename);
            System.out.println(message);
        } catch (IOException e) {
            System.err.println("Failed to store note: " + e.getMessage());
        }
    }
}