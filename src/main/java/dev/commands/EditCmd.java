package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Command(name = "edit", aliases = {"-ed"}, mixinStandardHelpOptions = true, description = "Edit a saved entry by slug")
public class EditCmd implements Runnable {

    @Parameters(description = "Slug of the entry to edit")
    private String slug;

    private final StorageService storageService;

    public EditCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            List<Map<String, String>> entries = storageService.findAllBySlug(slug);
            if (entries.isEmpty()) {
                System.err.println("No entry found with slug: " + slug);
                return;
            }

            Map<String, String> entry;
            if (entries.size() == 1) {
                entry = entries.get(0);
            } else {
                System.out.println("Multiple entries with slug '" + slug + "':");
                for (int i = 0; i < entries.size(); i++) {
                    Map<String, String> e = entries.get(i);
                    System.out.println("  " + (i + 1) + ". " + e.get("date") + "  (" + e.get("file") + ")");
                }
                System.out.print("Which one? [1-" + entries.size() + "]: ");
                String input = new BufferedReader(new InputStreamReader(System.in)).readLine();
                if (input == null) return;
                int choice;
                try {
                    choice = Integer.parseInt(input.trim());
                } catch (NumberFormatException e) {
                    System.err.println("Invalid choice.");
                    return;
                }
                if (choice < 1 || choice > entries.size()) {
                    System.err.println("Invalid choice.");
                    return;
                }
                entry = entries.get(choice - 1);
            }

            String filename = entry.get("file");
            String content = entry.get("content");

            Path tmp = Files.createTempFile("recall-edit-", ".md");
            Files.writeString(tmp, content);
            String editor = System.getenv("EDITOR");
            if (editor == null || editor.isBlank()) editor = "vim";

            Process p = new ProcessBuilder("bash", "-c", editor + " " + tmp.toAbsolutePath())
                .inheritIO().start();
            p.waitFor();

            String newContent = Files.readString(tmp).trim();
            Files.deleteIfExists(tmp);

            if (newContent.equals(content.trim())) {
                System.out.println("No changes made.");
                return;
            }

            boolean updated = storageService.updateEntry(filename, entry.get("slug"), newContent);
            if (updated) {
                String message = CommandLine.Help.Ansi.AUTO.string(
                    "@|green \u2713 Updated  [|@@|bold,yellow " + slug + "|@@|green ]|@");
                System.out.println(message);
            } else {
                System.err.println("Failed to update entry.");
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to edit entry: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
