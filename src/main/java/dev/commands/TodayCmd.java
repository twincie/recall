package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Command(name = "today", description = "Show everything saved today")
public class TodayCmd implements Runnable {
    private final StorageService storageService;

    public TodayCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            List<Map<String, String>> entries = storageService.today();

            if (entries.isEmpty()) {
                System.out.println("Nothing saved today.");
                return;
            }

            Map<String, Long> counts = entries.stream()
                .collect(Collectors.groupingBy(e -> e.get("type"), Collectors.counting()));

            String summary = counts.entrySet().stream()
                .map(e -> e.getValue() + " " + e.getKey() + (e.getValue() > 1 ? "s" : ""))
                .collect(Collectors.joining(", "));

            System.out.println(summary + " saved today");
            System.out.println();

            for (Map<String, String> entry : entries) {
                String slug = entry.get("slug");
                String type = entry.get("type");
                String tags = entry.get("tags");
                String file = entry.get("file");
                String content = entry.get("content");

                String preview = content.replaceAll("(?s)```.*?```", "```")
                    .replaceAll("\n", " ")
                    .trim();
                if (preview.length() > 60) {
                    preview = preview.substring(0, 57) + "...";
                }

                String tagInfo = (tags == null || tags.isBlank()) ? "" : "  " + tags;

                String output = CommandLine.Help.Ansi.AUTO.string(
                    String.format("@|bold,green %s|@@|cyan %s|@  @|bold,cyan %s|@\n  %s\n",
                        slug, tagInfo, file, preview)
                );
                System.out.println(output);
            }
        } catch (IOException e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }
}