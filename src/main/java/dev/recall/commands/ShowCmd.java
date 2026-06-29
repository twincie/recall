package dev.recall.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "show",
    aliases = {"-sw", "--show"},
    mixinStandardHelpOptions = true,
    description = "Show the full content of an entry by slug")
public class ShowCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "Slug of the entry to show")
    private String slug;

    @Option(names = {"--plain", "-p"}, description = "Plain output without colors")
    private boolean plain;

    private final StorageService storageService;

    public ShowCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public Integer call() {
        try {
            Map<String, String> entry = storageService.findBySlug(slug);
            if (entry == null) {
                System.err.println(CommandLine.Help.Ansi.AUTO.string(
                    "@|red Entry not found: " + slug + "|@"));
                return 1;
            }

            if (plain) {
                System.out.println(entry.get("slug") + "  " + entry.get("date") + "  " + entry.get("type"));
            } else {
                String header = CommandLine.Help.Ansi.AUTO.string(
                    String.format("@|bold,green %s|@  @|cyan %s|@  @|cyan %s|@",
                        entry.get("slug"), entry.get("date"), entry.get("type")));
                System.out.println(header);
            }

            String tags = entry.get("tags");
            if (tags != null && !tags.isBlank()) {
                System.out.println("tags: " + tags);
            }

            String content = entry.get("content");

            System.out.println();
            if (plain) {
                System.out.println("---");
            } else {
                System.out.println(CommandLine.Help.Ansi.AUTO.string(
                    "@|bold,green " + "\u2500".repeat(55) + "|@"));
            }
            System.out.println(content);
            return 0;
        } catch (IOException e) {
            System.err.println("Failed: " + e.getMessage());
            return 1;
        }
    }
}
