package dev.recall.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Command(name = "recent",
    aliases = {"-rc", "--recent"},
    mixinStandardHelpOptions = true,
    description = "Show recently saved entries")
public class RecentCmd implements Runnable {
    @Option(names = {"--limit", "-n"}, description = "Number of entries (default: 10)")
    private int limit = 10;

    @Option(names = {"--type", "-t"}, description = "Filter by type")
    private String typeFilter;

    @Option(names = {"--plain", "-p"}, description = "Plain output without colors")
    private boolean plain;

    private final StorageService storageService;

    public RecentCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            List<Map<String, String>> entries = storageService.allEntries();
            entries.sort(Comparator.comparing(e -> e.get("date"), Comparator.reverseOrder()));

            if (typeFilter != null) {
                entries = entries.stream()
                    .filter(e -> typeFilter.equals(e.get("type")))
                    .collect(Collectors.toList());
            }

            if (entries.isEmpty()) {
                System.out.println("No entries yet. Use 'recall remember' to store something.");
                return;
            }

            entries = entries.stream().limit(limit).collect(Collectors.toList());

            int maxSlugLen = entries.stream()
                .mapToInt(e -> e.get("slug").length())
                .max().orElse(20);

            for (Map<String, String> entry : entries) {
                String slug = entry.get("slug");
                String type = entry.get("type");
                String relative = relativeDate(entry.get("date"));

                String line = String.format("  %-" + maxSlugLen + "s %10s \u00B7 %s",
                    slug, type, relative);
                if (plain) {
                    System.out.println(line);
                } else {
                    String output = CommandLine.Help.Ansi.AUTO.string(
                        "@|bold,green " + line + "|@");
                    System.out.println(output);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }

    static String relativeDate(String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        LocalDate today = LocalDate.now();
        long days = ChronoUnit.DAYS.between(date, today);
        if (days == 0) return "today";
        if (days == 1) return "yesterday";
        if (days < 7) return days + " days ago";
        return date.format(DateTimeFormatter.ofPattern("MMM dd"));
    }
}
