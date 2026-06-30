package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Command(name = "run", description = "Run a saved command")
public class CommandRunCmd implements Runnable {
    @Parameters(index = "0", description = "Name of the command to run")
    private String name;

    @Parameters(index = "1..*", arity = "0..*", description = "Arguments to append to the command")
    private String[] extraArgs;

    @Option(names = {"--confirm", "-c"}, description = "Skip confirmation prompt")
    private boolean confirm;

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

            String command = target.get("content");
            if (extraArgs != null && extraArgs.length > 0) {
                command = command + " " + String.join(" ", extraArgs);
            }

            if (!confirm && System.console() != null) {
                System.out.println("$ " + command);
                System.out.print("Run? [Y/n]: ");
                String input = new BufferedReader(new InputStreamReader(System.in)).readLine();
                if (input != null) {
                    input = input.trim().toLowerCase();
                    if (!input.isBlank() && !input.equals("y") && !input.equals("yes")) {
                        System.out.println("Cancelled.");
                        return;
                    }
                }
            }

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command).inheritIO();
            Process process = pb.start();
            int exit = process.waitFor();
            System.out.println("Exit code: " + exit);

        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to run command: " + e.getMessage());
        }
    }
}
