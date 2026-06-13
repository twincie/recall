package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Command(name = "search", description = "Search across all notes")
public class SearchCmd implements Runnable {
    @Parameters(description = "Search query", arity = "1..*")
    private String[] parts;

    @Option(names = {"--tag", "-t"}, description = "Filter by tag")
    private String tag;

    private final StorageService storageService;

    public SearchCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            String query = String.join(" ", parts);
            List<Map<String, String>> results = storageService.search(query, tag);

            if (results.isEmpty()) {
                System.out.println("No results for: " + query);
                return;
            }

            for (Map<String, String> result : results) {
                String slug = result.get("slug");
                String type = result.get("type");
                String date = result.get("date");
                String tags = result.get("tags");
                String content = result.get("content");

                String highlighted = content.replaceAll(
                    "(?i)(" + Pattern.quote(query) + ")", "@|yellow $1|@");

                String tagInfo = (tags == null || tags.isBlank()) ? "" : "  " + tags;
                String firstLine = highlighted.contains("\n")
                    ? highlighted.substring(0, highlighted.indexOf('\n'))
                    : highlighted;

                String output = CommandLine.Help.Ansi.AUTO.string(
                    String.format(
                        "@|bold,cyan \u250c\u2500 |@@|bold,green %s|@@|cyan %s |@@|bold,cyan %s|@\n" +
                        "@|bold,cyan \u2502|@  %s\n" +
                        "@|bold,cyan \u2514\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500|@\n",
                        slug, tagInfo, date,
                        firstLine
                    )
                );
                System.out.println(output);
            }
        } catch (IOException e) {
            System.err.println("Search failed: " + e.getMessage());
        }
    }
}