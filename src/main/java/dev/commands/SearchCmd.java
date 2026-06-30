package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Command(name = "search", aliases = {"-s", "--search"}, mixinStandardHelpOptions = true, description = "Search across all notes")
public class SearchCmd implements Runnable {
    @Parameters(description = "Search query", arity = "1..*")
    private String[] parts;

    @Option(names = {"--tag", "-t"}, description = "Filter by tag")
    private String tag;

    @Option(names = {"--file", "-f"}, description = "Search only this file (notes.md, commands.md, etc.)")
    private String file;

    @Option(names = {"--limit", "-l"}, description = "Max results (default 20, 0 for unlimited)")
    private int limit = 20;

    @Option(names = {"--plain", "-p"}, description = "Disable interactive mode (show results without prompt)")
    private boolean plain;


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

            if (file != null) {
                results = results.stream()
                    .filter(r -> file.equals(r.get("file")))
                    .collect(Collectors.toList());
            }

            results.sort((a, b) -> b.get("date").compareTo(a.get("date")));

            if (limit > 0 && results.size() > limit) {
                results = results.subList(0, limit);
            }

            if (results.isEmpty()) {
                System.out.println("No results for: " + query);
                return;
            }

            if (!plain && System.console() != null) {
                interactiveBrowse(results, query);
            } else {
                for (Map<String, String> result : results) {
                    printResult(result, query);
                }
            }
        } catch (IOException e) {
            System.err.println("Search failed: " + e.getMessage());
        }
    }

    private void printResult(Map<String, String> result, String query) {
        String slug = result.get("slug");
        String tags = result.get("tags");
        String date = result.get("date");
        String content = result.get("content");

        String tagInfo = (tags == null || tags.isBlank()) ? "" : "  " + tags;
        String highlighted = highlight(content, query);

        int nl1 = highlighted.indexOf('\n');
        int nl2 = nl1 == -1 ? -1 : highlighted.indexOf('\n', nl1 + 1);
        String preview = nl2 == -1
            ? highlighted.substring(0, Math.min(highlighted.length(), 120))
            : highlighted.substring(0, nl2);

        String output = CommandLine.Help.Ansi.AUTO.string(
            String.format(
                "@|bold,cyan \u250c\u2500 |@@|bold,green %s|@@|cyan %s |@@|bold,cyan %s|@\n" +
                "@|bold,cyan \u2502|@  %s\n" +
                "@|bold,cyan \u2514\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500|@\n",
                slug, tagInfo, date, preview
            )
        );
        System.out.println(output);
    }

    private void interactiveBrowse(List<Map<String, String>> results, String query) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            for (int i = 0; i < results.size(); i++) {
                Map<String, String> r = results.get(i);
                System.out.printf("  %2d. ", i + 1);
                System.out.println(CommandLine.Help.Ansi.AUTO.string(
                    String.format("@|bold,green %s|@  @|cyan %s|@  @|cyan %s|@",
                        r.get("slug"), r.get("date"), r.get("type"))));
            }

            System.out.print("\nOpen which? [1-" + results.size() + ", Enter to quit]: ");
            String input = reader.readLine();
            if (input == null || input.isBlank()) break;

            int choice;
            try {
                choice = Integer.parseInt(input.trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (choice < 1 || choice > results.size()) continue;

            showEntry(results.get(choice - 1), query);
            System.out.println();
        }
    }

    private void showEntry(Map<String, String> entry, String query) {
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
        System.out.println(CommandLine.Help.Ansi.AUTO.string(highlight(entry.get("content"), query)));
        System.out.println();
    }

    private String highlight(String text, String query) {
        String result = text;
        for (String term : query.split("\\s+")) {
            result = result.replaceAll("(?i)(" + Pattern.quote(term) + ")", "@|yellow $1|@");
        }
        return result;
    }
}
