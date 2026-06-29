package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

@Command(name = "list", aliases = {"-l", "--list"}, mixinStandardHelpOptions = true, description = "Browse recent entries")
public class ListCmd implements Runnable {
    @Option(names = {"-n", "--limit"}, description = "Number of entries (default: 10)")
    private int count = 10;

    @Option(names = {"--tag", "-t"}, description = "Filter by tag")
    private String tag;

    @Option(names = {"--type", "-f"}, description = "Filter by type (note, command, script, ticket, etc.)")
    private String type;

    @Option(names = {"--plain", "-p"}, description = "Disable interactive mode (show results without prompt)")
    private boolean plain;

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
            List<Map<String, String>> entries = storageService.list(count, tag, type);

            if (entries.isEmpty()) {
                System.out.println("Nothing saved yet. Use 'recall remember' to store something.");
                return;
            }

            if (!plain && System.console() != null) {
                interactiveBrowse(entries);
            } else {
                for (Map<String, String> entry : entries) {
                    printEntry(entry);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to list entries: " + e.getMessage());
        }
    }

    private void printEntry(Map<String, String> entry) {
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
            String.format("  @|bold,green %s|@@|cyan %s|@  @|bold,cyan %s|@\n    @|italic %s|@\n",
                slug, tagStr, date, preview)
        );
        System.out.println(output);
    }

    private void interactiveBrowse(List<Map<String, String>> entries) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            for (int i = 0; i < entries.size(); i++) {
                Map<String, String> e = entries.get(i);
                String preview = e.get("content").replaceAll("(?s)```.*?```", "```")
                    .replaceAll("\n", " ").trim();
                if (preview.length() > 50) preview = preview.substring(0, 47) + "...";
                String tagStr = (e.get("tags") == null || e.get("tags").isBlank()) ? "" : "  " + e.get("tags");

                System.out.println(CommandLine.Help.Ansi.AUTO.string(
                    String.format("%2d. @|bold,green %s|@@|cyan %s|@  @|bold,cyan %s|@\n    @|italic %s|@\n",
                        i + 1, e.get("slug"), tagStr, e.get("date"), preview)
                ));
            }

            System.out.print("Open which? [1-" + entries.size() + ", Enter to quit]: ");
            String input = reader.readLine();
            if (input == null || input.isBlank()) break;

            int choice;
            try {
                choice = Integer.parseInt(input.trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (choice < 1 || choice > entries.size()) continue;

            Map<String, String> entry = entries.get(choice - 1);
            String header = CommandLine.Help.Ansi.AUTO.string(
                String.format("@|bold,green %s|@  @|cyan %s|@  @|cyan %s|@",
                    entry.get("slug"), entry.get("date"), entry.get("type")));
            System.out.println("\n" + header);

            String tags = entry.get("tags");
            if (tags != null && !tags.isBlank()) {
                System.out.println("tags: " + tags);
            }

            System.out.println();
            String divider = CommandLine.Help.Ansi.AUTO.string(
                "@|bold,green " + "\u2500".repeat(55) + "|@");
            System.out.println(divider);
            System.out.println(entry.get("content"));
            System.out.println();
        }
    }
}
