package dev.commands;

import dev.LLMService;
import dev.StorageService;
import dev.recall.SlugUtil;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.List;

@Command(name = "ingest",
    aliases = {"-g", "--ingest"},
    mixinStandardHelpOptions = true,
    description = "Auto-classify and file unstructured text using LLM")
public class IngestCmd implements Runnable {
    private static final List<String> VALID_TYPES = List.of("note", "command", "snippet", "ticket", "runbook", "retro", "knowledge");

    @Parameters(description = "The text to classify and store", arity = "1..*")
    private String[] parts;

    @Option(names = {"--tags"}, description = "Comma-separated tags")
    private String tags = "";

    final StorageService storageService;
    private final LLMService llmService;

    public IngestCmd() {
        try {
            this.storageService = new StorageService();
            this.llmService = new LLMService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize", e);
        }
    }

    IngestCmd(StorageService storageService, LLMService llmService) {
        this.storageService = storageService;
        this.llmService = llmService;
    }

    @Override
    public void run() {
        try {
            String text = String.join(" ", parts);
            if (text.isBlank()) {
                System.err.println("Nothing to ingest.");
                return;
            }

            String type = classify(text);
            String firstLine = text.lines().findFirst().orElse(text);
            String slug = SlugUtil.makeSlug(firstLine.length() > 60 ? firstLine.substring(0, 60) : firstLine);

            String filename = storageService.append(typeToFile(type), slug, type, tags, text);

            String message = CommandLine.Help.Ansi.AUTO.string(
                "@|green \u2713 Ingested  [|@@|bold,yellow " + slug + "|@@|green ]|@  (" + type + ")  " + filename);
            System.out.println(message);
        } catch (Exception e) {
            System.err.println("Ingest failed: " + e.getMessage());
        }
    }

    String classify(String text) {
        try {
            String prompt = "Classify the following content into exactly one category: note, command, snippet, ticket, runbook, retro, knowledge. " +
                "Respond with only the single category word and nothing else.\n\n---\n" + text + "\n---";
            String result = llmService.query(prompt, "You classify text into categories.").trim().toLowerCase();
            if (VALID_TYPES.contains(result)) return result;
        } catch (Exception ignored) {
        }
        return "note";
    }

    static String typeToFile(String type) {
        return switch (type) {
            case "command" -> "commands.md";
            case "snippet" -> "snippets.md";
            case "ticket" -> "tickets.md";
            case "runbook" -> "runbooks.md";
            case "retro" -> "retros.md";
            case "knowledge" -> "knowledge.md";
            default -> "notes.md";
        };
    }
}
