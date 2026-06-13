package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Command(name = "run", description = "Run a saved command")
public class CommandRunCmd implements Runnable {
    @Parameters(index = "0", description = "Name of the command to run")
    private String name;

    private final StorageService storageService;

    public CommandRunCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            Path file = Paths.get(System.getProperty("user.home"), ".recall", "commands.md");
            if (!Files.exists(file)) {
                System.err.println("No commands saved yet");
                return;
            }

            List<Map<String, String>> commands = storageService.parseBlocks(file);
            String slug = StorageService.makeSlug(name);

            for (Map<String, String> cmd : commands) {
                if (cmd.get("slug").equals(slug)) {
                    String command = cmd.get("content");
                    System.out.println("$ " + command);
                    ProcessBuilder pb = new ProcessBuilder("bash", "-c", command)
                        .inheritIO();
                    Process process = pb.start();
                    int exit = process.waitFor();
                    System.exit(exit);
                    return;
                }
            }

            System.err.println("Command not found: " + slug);
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to run command: " + e.getMessage());
        }
    }
}