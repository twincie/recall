package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Command(name = "list", description = "Browse recent entries")
public class ListCmd implements Runnable {
    @Option(names = {"-n"}, description = "Number of entries (default: 10)")
    private int count = 10;

    @Option(names = {"--tag", "-t"}, description = "Filter by tag")
    private String tag;

    private final StorageService storageService;

    public ListCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            List<Map<String, String>> entries = storageService.list(count, tag, null);

            if (entries.isEmpty()) {
                System.out.println("Nothing saved yet. Use 'recall remember' to store something.");
                return;
            }

            for (int i = 0; i < entries.size(); i++) {
                Map<String, String> entry = entries.get(i);
                String slug = entry.get("slug");
                String type = entry.get("type");
                String date = entry.get("date");
                String tags = entry.get("tags");
                String content = entry.get("content");

                String preview = content.replaceAll("(?s)```.*?```", "```")
                    .replaceAll("\n", " ")
                    .trim();
                if (preview.length() > 50) {
                    preview = preview.substring(0, 47) + "...";
                }

                String tagStr = (tags == null || tags.isBlank()) ? "" : "  " + tags;

                String output = CommandLine.Help.Ansi.AUTO.string(
                    String.format("%2d. @|bold,green %s|@@|cyan %s|@  @|bold,cyan %s|@\n    @|italic %s|@\n",
                        i + 1, slug, tagStr, date, preview)
                );
                System.out.println(output);
            }
        } catch (IOException e) {
            System.err.println("Failed to list entries: " + e.getMessage());
        }
    }
}