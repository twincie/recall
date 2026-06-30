package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Command(name = "edit", description = "Edit a saved command")
public class CommandEditCmd implements Runnable {
    @Parameters(index = "0", description = "Name of the command")
    private String name;

    private final StorageService storageService;

    public CommandEditCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            Path file = storageService.getDataDir().resolve("commands.md");
            if (!Files.exists(file)) {
                System.err.println("No commands saved yet");
                return;
            }

            List<Map<String, String>> commands = storageService.parseBlocks(file);
            String slug = StorageService.makeSlug(name);

            Map<String, String> target = null;
            for (Map<String, String> cmd : commands) {
                if (cmd.get("slug").equals(slug)) {
                    target = cmd;
                    break;
                }
            }

            if (target == null) {
                System.err.println("Command not found: " + slug);
                return;
            }

            String content = target.get("content");

            Path tmp = Files.createTempFile("recall-cmd-edit-", ".sh");
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

            boolean updated = storageService.updateEntry("commands.md", slug, newContent);
            if (updated) {
                String msg = CommandLine.Help.Ansi.AUTO.string(
                    "@|green \u2713 Updated command  [|@@|bold,yellow " + slug + "|@@|green ]|@");
                System.out.println(msg);
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to edit command: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
