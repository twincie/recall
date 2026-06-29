package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Command(name = "list", aliases = {"ls"}, mixinStandardHelpOptions = true, description = "List saved commands")
public class CommandListCmd implements Runnable {
    @Option(names = {"--limit", "-l"}, description = "Max results (0 for all)")
    private int limit = 20;

    private final StorageService storageService;

    public CommandListCmd() {
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
                System.out.println("No commands saved yet.");
                return;
            }

            List<Map<String, String>> commands = storageService.parseBlocks(file);
            commands.sort((a, b) -> b.get("date").compareTo(a.get("date")));

            if (limit > 0 && commands.size() > limit) {
                commands = commands.subList(0, limit);
            }

            if (commands.isEmpty()) {
                System.out.println("No commands saved yet.");
                return;
            }

            for (Map<String, String> cmd : commands) {
                String content = cmd.get("content");
                String preview = content.length() > 60 ? content.substring(0, 60) + "..." : content;
                System.out.println(CommandLine.Help.Ansi.AUTO.string(
                    String.format("  @|bold,green %s|@  @|cyan %s|@",
                        cmd.get("slug"), preview)));
            }
        } catch (IOException e) {
            System.err.println("Failed to list commands: " + e.getMessage());
        }
    }
}
