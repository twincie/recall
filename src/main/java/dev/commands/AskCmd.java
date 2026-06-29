package dev.commands;

import dev.LLMService;
import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Command(name = "ask", aliases = {"-a", "--ask"}, mixinStandardHelpOptions = true, description = "Ask a question — checks your notes first, then Claude")
public class AskCmd implements Runnable {
    @Parameters(description = "Your question", arity = "1..*")
    private String[] parts;

    private final StorageService storageService;

    public AskCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            String question = String.join(" ", parts);

            List<Map<String, String>> notes = storageService.search(question, null);

            if (!notes.isEmpty()) {
                System.out.println("From your notes:\n");
                for (int i = 0; i < Math.min(3, notes.size()); i++) {
                    Map<String, String> note = notes.get(i);
                    String output = CommandLine.Help.Ansi.AUTO.string(
                        String.format("@|bold,green %s|@@|cyan %s|@  @|bold,cyan %s|@\n  %s\n",
                            note.get("slug"),
                            note.get("tags").isBlank() ? "" : "  " + note.get("tags"),
                            note.get("date"),
                            note.get("content").replaceAll("\n", " ").substring(
                                0, Math.min(120, note.get("content").length()))));
                    System.out.println(output);
                }

                if (notes.size() > 3) {
                    System.out.println("(+ " + (notes.size() - 3) + " more — narrow your search)");
                }
                return;
            }

            System.out.println("Nothing in your notes. Asking Claude...");
            LLMService llm = new LLMService();
            String answer = llm.query(question,
                "You are a senior software engineer. Answer concisely and directly.",
                1024);
            System.out.println(answer);
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }
}