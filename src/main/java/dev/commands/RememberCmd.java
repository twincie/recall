package dev.commands;

import dev.StorageService;
import dev.recall.SlugUtil;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Command(name = "remember", aliases = {"-r", "--remember"}, mixinStandardHelpOptions = true, description = "Store a note in your engineering memory")
public class RememberCmd implements Runnable {

    @Parameters(description = "The note content (omit to pipe, --file, or --edit)", arity = "0..*")
    private String[] parts;

    @CommandLine.Option(names = {"--tags"}, description = "Comma-separated tags")
    private String tags = "";

    @CommandLine.Option(names = {"--file", "-f"}, description = "Read content from a file")
    private Path file;

    @CommandLine.Option(names = {"--edit", "-e"}, description = "Open $EDITOR to compose the note")
    private boolean edit;

    @CommandLine.Option(names = {"--name", "-n"}, description = "Title for the note (overrides auto-deriving from first line)")
    private String name;

    @CommandLine.Option(names = {"--confirm", "-c"}, description = "Skip confirmation prompt and save silently")
    private boolean confirm;

    private final StorageService storageService;

    public RememberCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            String raw = readContent();
            if (raw.isBlank()) {
                System.err.println("No input provided. Use: recall remember <text>, --file <path>, --edit, or pipe input.");
                return;
            }

            String title;
            String body;

            if (name != null) {
                title = name.trim();
                body = raw;
            } else {
                int nl = raw.indexOf('\n');
                title = nl == -1 ? raw.trim() : raw.substring(0, nl).trim();
                body = nl == -1 ? "" : raw.substring(nl + 1).trim();
            }

            while (true) {
                if (!confirm) {
                    String action = prompt(title, body);
                    if ("e".equals(action)) {
                        raw = editWithBody(body);
                        if (name != null) {
                            body = raw;
                        } else {
                            int nl = raw.indexOf('\n');
                            title = nl == -1 ? raw.trim() : raw.substring(0, nl).trim();
                            body = nl == -1 ? "" : raw.substring(nl + 1).trim();
                        }
                        continue;
                    }
                    if (!"y".equals(action)) {
                        System.out.println("Cancelled.");
                        return;
                    }
                }
                break;
            }

            String slug = SlugUtil.makeSlug(title);
            String text = body.isBlank() ? title : title + "\n" + body;

            Map<String, String> existing = storageService.findBySlug(slug);
            if (existing != null) {
                String deduped = slug;
                int counter = 2;
                while (storageService.findBySlug(deduped) != null) {
                    deduped = slug + "-" + counter++;
                }

                if (!confirm && System.console() != null) {
                    System.out.print("Slug '" + slug + "' already exists. [S]ave as '" + deduped + "', [U]pdate existing, or [C]ancel? [S/u/c]: ");
                    String input = new BufferedReader(new InputStreamReader(System.in)).readLine();
                    if (input != null) {
                        input = input.trim().toLowerCase();
                        if (input.equals("u") || input.equals("update")) {
                            storageService.updateEntry(existing.get("file"), slug, text);
                            String msg = CommandLine.Help.Ansi.AUTO.string(
                                "@|green \u2713 Updated  [|@@|bold,yellow " + slug + "|@@|green ]|@");
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

            String filename = storageService.append("notes.md", slug, "note", tags, text);

            String message = CommandLine.Help.Ansi.AUTO.string(
                "@|green \u2713 Saved  [|@@|bold,yellow " + slug + "|@@|green ]|@  " + filename);
            System.out.println(message);

        } catch (IOException e) {
            System.err.println("Failed to store note: " + e.getMessage());
        }
    }

    private String readContent() throws IOException {
        int count = 0;
        if (file != null) count++;
        if (edit) count++;
        if (parts != null && parts.length > 0) count++;

        if (count > 1) {
            System.err.println("Use only one input source: text args, --file, --edit, or stdin.");
            return "";
        }

        if (file != null) return Files.readString(file).trim();
        if (edit) return editWithBody("");
        if (parts != null && parts.length > 0) return String.join(" ", parts).trim();

        if (System.console() == null) {
            return new String(System.in.readAllBytes()).trim();
        }

        return "";
    }

    private String editWithBody(String currentBody) throws IOException {
        Path tmp = Files.createTempFile("recall-", ".md");
        if (!currentBody.isBlank()) {
            Files.writeString(tmp, currentBody);
        }
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

    private String prompt(String title, String body) {
        if (System.console() == null) return "y";
        System.out.println("─── Preview ───");
        System.out.println("Title: " + title);
        if (!body.isBlank()) {
            System.out.println("Body:");
            System.out.println(body);
        }
        System.out.println("───────────────");
        System.out.print("Save? [Y/n/e]: ");
        try {
            String input = new BufferedReader(new InputStreamReader(System.in)).readLine();
            if (input == null) return "y";
            input = input.trim().toLowerCase();
            if (input.isBlank() || input.equals("y") || input.equals("yes")) return "y";
            if (input.equals("e") || input.equals("edit")) return "e";
            return "n";
        } catch (IOException e) {
            return "y";
        }
    }
}
