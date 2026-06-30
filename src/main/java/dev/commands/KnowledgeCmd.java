package dev.commands;

import dev.StorageService;
import dev.recall.SlugUtil;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

@Command(name = "knowledge",
    aliases = {"-k", "--knowledge"},
    mixinStandardHelpOptions = true,
    description = "Store and retrieve knowledge base entries")
public class KnowledgeCmd implements Runnable {
    private static final String KNOWLEDGE_FILE = "knowledge.md";

    @Parameters(description = "Knowledge content to store", arity = "0..*")
    private String[] parts;

    @Option(names = {"--get", "-g"}, description = "Retrieve a knowledge entry by slug")
    private String getSlug;

    @Option(names = {"--search", "-s"}, description = "Search knowledge entries")
    private String searchQuery;

    @Option(names = {"--file", "-f"}, description = "Read content from a file")
    private String filePath;

    @Option(names = {"--edit", "-e"}, description = "Open editor to write content")
    private boolean edit;

    @Option(names = {"--delete"}, description = "Delete a knowledge entry by slug")
    private String deleteSlug;

    @Option(names = {"--tags"}, description = "Comma-separated tags")
    private String tags = "";

    @Option(names = {"--name"}, description = "Title for the entry (used for slug)")
    private String name;

    @Option(names = {"--plain", "-p"}, description = "Plain output without colors")
    private boolean plain;

    private final StorageService storageService;

    public KnowledgeCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            if (getSlug != null) {
                show(getSlug);
                return;
            }
            if (searchQuery != null) {
                search(searchQuery);
                return;
            }
            if (deleteSlug != null) {
                delete(deleteSlug);
                return;
            }
            if ((parts == null || parts.length == 0) && filePath == null && !edit) {
                System.out.println("Usage:");
                System.out.println("  recall knowledge <text>              Store a knowledge entry");
                System.out.println("  recall knowledge --file <path>       Store from a file");
                System.out.println("  recall knowledge --edit              Write content in editor");
                System.out.println("  recall knowledge --get <slug>        Retrieve an entry");
                System.out.println("  recall knowledge --search <query>    Search entries");
                System.out.println("  recall knowledge --delete <slug>     Delete an entry");
                return;
            }
            save();
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }

    private void save() throws IOException {
        String text;
        String slug;

        if (edit) {
            Path tmp = Files.createTempFile("recall-knowledge-", ".md");
            ProcessBuilder pb = new ProcessBuilder(
                System.getenv().getOrDefault("EDITOR", "nano"), tmp.toString());
            pb.inheritIO();
            int code;
            try {
                code = pb.start().waitFor();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                System.err.println("Editor interrupted");
                return;
            }
            if (code != 0) {
                System.err.println("Editor exited with code " + code);
                return;
            }
            text = Files.readString(tmp).trim();
            Files.deleteIfExists(tmp);
            if (text.isBlank()) {
                System.err.println("Nothing to save.");
                return;
            }
            if (name != null) {
                slug = SlugUtil.makeSlug(name);
            } else {
                String firstLine = text.lines().findFirst().orElse("");
                slug = SlugUtil.makeSlug(firstLine.length() > 60 ? firstLine.substring(0, 60) : firstLine);
            }
        } else if (filePath != null) {
            Path source = Paths.get(filePath);
            if (!Files.exists(source)) {
                System.err.println("File not found: " + filePath);
                return;
            }
            text = Files.readString(source);
            if (name != null) {
                slug = SlugUtil.makeSlug(name);
            } else {
                String fname = source.getFileName().toString();
                int dot = fname.lastIndexOf('.');
                slug = SlugUtil.makeSlug(dot > 0 ? fname.substring(0, dot) : fname);
            }
        } else if (parts != null && parts.length > 0) {
            if (name != null) {
                text = String.join(" ", parts);
                slug = SlugUtil.makeSlug(name);
            } else {
                String all = String.join(" ", parts);
                String firstLine = all.lines().findFirst().orElse(all);
                slug = SlugUtil.makeSlug(firstLine.length() > 60 ? firstLine.substring(0, 60) : firstLine);
                text = all;
            }
        } else {
            System.err.println("Nothing to save.");
            return;
        }

        slug = handleDuplicate(slug);

        String filename = storageService.append(KNOWLEDGE_FILE, slug, "knowledge", tags, text);

        String message = CommandLine.Help.Ansi.AUTO.string(
            "@|green \u2713 Saved to knowledge base  [|@@|bold,yellow " + slug + "|@@|green ]|@  " + filename);
        System.out.println(message);
    }

    private String handleDuplicate(String slug) throws IOException {
        Map<String, String> existing = storageService.findBySlug(slug);
        if (existing == null) return slug;

        System.out.print("\u26A0 '" + slug + "' already exists. [S]ave as new, [U]pdate existing, [C]ancel? [S/u/c] ");
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim().toLowerCase();

        if (input.equals("u")) {
            return slug;
        } else if (input.equals("c")) {
            return null;
        } else {
            int counter = 1;
            String newSlug;
            do {
                newSlug = slug + "-" + counter;
                counter++;
            } while (storageService.findBySlug(newSlug) != null);
            return newSlug;
        }
    }

    private void show(String slug) throws IOException {
        Map<String, String> entry = storageService.findBySlug(slug);
        if (entry == null || !"knowledge".equals(entry.get("type"))) {
            System.err.println("Knowledge entry not found: " + slug);
            return;
        }

        if (plain) {
            System.out.println(entry.get("slug") + "  " + entry.get("date"));
        } else {
            String header = CommandLine.Help.Ansi.AUTO.string(
                String.format("@|bold,green %s|@  @|cyan %s|@",
                    entry.get("slug"), entry.get("date")));
            System.out.println(header);
        }

        String tags = entry.get("tags");
        if (tags != null && !tags.isBlank()) {
            System.out.println("tags: " + tags);
        }

        String content = entry.get("content");

        System.out.println();
        if (plain) {
            System.out.println("---");
        } else {
            System.out.println(CommandLine.Help.Ansi.AUTO.string("@|bold,green " + "\u2500".repeat(55) + "|@"));
        }
        System.out.println(content);
    }

    private void search(String query) throws IOException {
        List<Map<String, String>> all = storageService.search(query, null);
        boolean found = false;
        for (Map<String, String> entry : all) {
            if (!"knowledge".equals(entry.get("type"))) continue;
            if (!found) {
                System.out.println("Knowledge base results:\n");
                found = true;
            }
            String slug = entry.get("slug");
            String date = entry.get("date");
            String raw = entry.get("content");
            String preview = raw.replaceAll("\n", " ").trim();
            if (preview.length() > 80) preview = preview.substring(0, 77) + "...";

            if (plain) {
                System.out.println("  " + slug + "  " + date);
                System.out.println("  " + preview);
            } else {
                String output = CommandLine.Help.Ansi.AUTO.string(
                    String.format("  @|bold,green %s|@  @|cyan %s|@\n  %s\n",
                        slug, date, preview));
                System.out.println(output);
            }
        }
        if (!found) {
            System.out.println("No knowledge entries match: " + query);
        }
    }

    private void delete(String slug) throws IOException {
        Map<String, String> entry = storageService.findBySlug(slug);
        if (entry == null || !"knowledge".equals(entry.get("type"))) {
            System.err.println("Knowledge entry not found: " + slug);
            return;
        }
        storageService.removeEntry(KNOWLEDGE_FILE, slug);
        System.out.println("Deleted: " + slug);
    }
}
