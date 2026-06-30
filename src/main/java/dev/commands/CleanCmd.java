package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

@Command(name = "clean", aliases = {"-cl"}, mixinStandardHelpOptions = true, description = "List, clear, or remove entries from storage files")
public class CleanCmd implements Runnable {

    @Option(names = {"--file", "-f"}, description = "Target a specific file (notes.md, snippets.md, etc.)")
    private String file;

    @Option(names = {"--slug", "-s"}, description = "Remove a specific entry by slug")
    private String slug;

    @Option(names = {"--all", "-a"}, description = "Clear all storage files")
    private boolean all;

    @Option(names = {"--confirm", "-c"}, description = "Skip confirmation prompt")
    private boolean confirm;

    private final StorageService storageService;

    public CleanCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            if (slug != null) {
                removeBySlug();
                return;
            }

            if (file != null) {
                clearFile();
                return;
            }

            if (all) {
                clearAll();
                return;
            }

            listFiles();

        } catch (IOException e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }

    private void listFiles() throws IOException {
        Map<String, Integer> counts = storageService.fileEntryCounts();
        if (counts.isEmpty()) {
            System.out.println("No storage files found.");
            return;
        }
        int total = 0;
        System.out.println("── Storage files ──");
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            System.out.println("  " + e.getKey() + "  (" + e.getValue() + " entries)");
            total += e.getValue();
        }
        System.out.println("───────────────────");
        System.out.println("  Total: " + total + " entries across " + counts.size() + " files");
        System.out.println();
        System.out.println("Use: recall clean --file <name>  to clear a file");
        System.out.println("     recall clean --slug <slug>  to remove an entry");
        System.out.println("     recall clean --all          to clear everything");
    }

    private void clearFile() throws IOException {
        Map<String, Integer> counts = storageService.fileEntryCounts();
        if (!counts.containsKey(file)) {
            System.err.println("File '" + file + "' not found. Options: " + String.join(", ", counts.keySet()));
            return;
        }
        if (!confirmed("Clear all " + counts.get(file) + " entries from '" + file + "'?")) return;
        storageService.clearFile(file);
        System.out.println("Cleared " + file);
    }

    private void clearAll() throws IOException {
        Map<String, Integer> counts = storageService.fileEntryCounts();
        if (counts.isEmpty()) {
            System.out.println("No files to clear.");
            return;
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (!confirmed("Clear all " + total + " entries across " + counts.size() + " files?")) return;
        for (String name : counts.keySet()) {
            storageService.clearFile(name);
        }
        System.out.println("Cleared all files.");
    }

    private void removeBySlug() throws IOException {
        var entries = storageService.findAllBySlug(slug);
        if (entries.isEmpty()) {
            System.err.println("No entry found with slug: " + slug);
            return;
        }
        for (var entry : entries) {
            if (!confirmed("Remove '" + slug + "' from " + entry.get("file") + " (" + entry.get("date") + ")?")) {
                System.out.println("Skipped.");
                continue;
            }
            storageService.removeEntry(entry.get("file"), slug);
            System.out.println("Removed '" + slug + "' from " + entry.get("file"));
        }
    }

    private boolean confirmed(String prompt) throws IOException {
        if (confirm || System.console() == null) return true;
        System.out.print(prompt + " [y/N]: ");
        String input = new BufferedReader(new InputStreamReader(System.in)).readLine();
        if (input == null) return false;
        input = input.trim().toLowerCase();
        return input.equals("y") || input.equals("yes");
    }
}
