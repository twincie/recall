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
import java.util.Map;

@Command(name = "save", description = "Save a command to the vault")
public class CommandSaveCmd implements Runnable {
    @Parameters(index = "0", description = "Name for the command")
    private String name;

    @Parameters(index = "1..*", arity = "0..*", description = "The command to save (omit for stdin or --edit)")
    private String[] parts;

    @Option(names = {"--edit", "-e"}, description = "Open $EDITOR to compose the command")
    private boolean edit;

    @Option(names = {"--confirm", "-c"}, description = "Skip confirmation prompt")
    private boolean confirm;

    private final StorageService storageService;

    public CommandSaveCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            String command = readCommand();
            if (command.isBlank()) {
                System.err.println("No command provided.");
                return;
            }

            String slug = StorageService.makeSlug(name);
            Map<String, String> existing = storageService.findBySlug(slug);
            if (existing != null && "commands.md".equals(existing.get("file"))) {
                String deduped = slug;
                int counter = 2;
                while (storageService.findBySlug(deduped) != null) {
                    deduped = slug + "-" + counter++;
                }

                if (!confirm && System.console() != null) {
                    System.out.print("Command '" + slug + "' already exists. [S]ave as '" + deduped + "', [U]pdate existing, or [C]ancel? [S/u/c]: ");
                    String input = new BufferedReader(new InputStreamReader(System.in)).readLine();
                    if (input != null) {
                        input = input.trim().toLowerCase();
                        if (input.equals("u") || input.equals("update")) {
                            storageService.updateEntry("commands.md", slug, command);
                            String msg = CommandLine.Help.Ansi.AUTO.string(
                                "@|green \u2713 Updated command  [|@@|bold,yellow " + slug + "|@@|green ]|@");
                            System.out.println(msg);
                            return;
                        }
                        if (!input.isBlank() && !input.equals("s") && !input.equals("save") && !input.equals("y") && !input.equals("yes")) {
                            System.out.println("Cancelled.");
                            return;
                        }
                    }
                }
                slug = deduped;
            }

            String filename = storageService.append("commands.md", slug, "command", "", command);

            String msg = CommandLine.Help.Ansi.AUTO.string(
                "@|green \u2713 Saved command  [|@@|bold,yellow " + slug + "|@@|green ]|@  " + filename);
            System.out.println(msg);
        } catch (IOException e) {
            System.err.println("Failed to save command: " + e.getMessage());
        }
    }

    private String readCommand() throws IOException {
        if (parts != null && parts.length > 0) {
            return String.join(" ", parts).trim();
        }

        if (edit) {
            Path tmp = Files.createTempFile("recall-cmd-", ".sh");
            String editor = System.getenv("EDITOR");
            if (editor == null || editor.isBlank()) editor = "vim";
            try {
                new ProcessBuilder("bash", "-c", editor + " " + tmp.toAbsolutePath())
                    .inheritIO().start().waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String content = Files.readString(tmp).trim();
            Files.deleteIfExists(tmp);
            return content;
        }

        if (System.console() == null) {
            return new String(System.in.readAllBytes()).trim();
        }

        return "";
    }
}
