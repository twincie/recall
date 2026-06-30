package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Command(name = "review", aliases = {"-rv", "--review"}, mixinStandardHelpOptions = true, description = "Surface notes from past days (default: 7, 30, 90)")
public class ReviewCmd implements Runnable {
    private final StorageService storageService;

    @Option(names = {"--days"}, split = ",", description = "Days to review (comma-separated, default: 7,30,90)")
    private int[] days = {7, 30, 90};

    @Option(names = {"--plain", "-p"}, description = "Plain output without colors")
    private boolean plain;

    public ReviewCmd() {
        try {
            this.storageService = new StorageService();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            boolean found = false;

            for (int age : days) {
                LocalDate target = LocalDate.now().minusDays(age);
                List<Map<String, String>> entries = storageService.entriesOnDate(target);
                if (!entries.isEmpty()) {
                    found = true;
                    if (plain) {
                        System.out.println("=== " + age + " days ago (" + target + ") ===");
                    } else {
                        String heading = String.format("=== %d days ago (%s) ===", age, target);
                        System.out.println(CommandLine.Help.Ansi.AUTO.string(
                            "@|bold,cyan " + heading + "|@"));
                    }
                    System.out.println();
                    for (Map<String, String> e : entries) {
                        String preview = e.get("content").replaceAll("\n", " ").trim();
                        if (preview.length() > 80) preview = preview.substring(0, 77) + "...";
                        if (plain) {
                            System.out.println("  " + e.get("slug") + "  " + e.get("tags") + "  " + e.get("file"));
                            System.out.println("    " + preview);
                        } else {
                            String output = CommandLine.Help.Ansi.AUTO.string(
                                String.format("  @|bold,green %s|@@|cyan %s|@  @|bold %s|@\n    %s\n",
                                    e.get("slug"),
                                    e.get("tags").isBlank() ? "" : "  " + e.get("tags"),
                                    e.get("file"),
                                    preview));
                            System.out.println(output);
                        }
                    }
                    System.out.println();
                }
            }

            if (!found) {
                System.out.println("Nothing to review yet. Keep saving notes!");
            }
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }
}
