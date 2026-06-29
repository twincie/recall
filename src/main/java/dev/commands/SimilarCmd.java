package dev.commands;

import dev.LLMService;
import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.*;
import java.util.stream.Collectors;

@Command(name = "similar", aliases = {"-sm", "--similar"}, mixinStandardHelpOptions = true, description = "Find related notes by meaning (not exact keywords)")
public class SimilarCmd implements Runnable {
    @Parameters(description = "Text to find similar notes for", arity = "1..*")
    private String[] parts;

    private final StorageService storageService;

    public SimilarCmd() {
        try {
            this.storageService = new StorageService();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            String text = String.join(" ", parts);
            List<Map<String, String>> all = storageService.allEntries();
            if (all.isEmpty()) {
                System.out.println("No notes saved yet.");
                return;
            }

            List<Map<String, String>> ranked = tryLLMSimilarity(text, all);
            if (ranked == null) {
                ranked = localSimilarity(text, all);
            }

            if (ranked.isEmpty()) {
                System.out.println("No similar notes found.");
                return;
            }

            int limit = Math.min(5, ranked.size());
            System.out.println("Most similar to: " + text + "\n");
            for (int i = 0; i < limit; i++) {
                Map<String, String> r = ranked.get(i);
                String score = String.format("%.0f%%", Double.parseDouble(r.get("_score")) * 100);
                String output = CommandLine.Help.Ansi.AUTO.string(
                    String.format("  @|bold,green %s|@ @|cyan %s|@  @|bold,cyan %s|@\n  @|bold,yellow %s|@ — %s\n",
                        r.get("slug"), r.get("tags").isBlank() ? "" : "  " + r.get("tags"),
                        r.get("date"), score, r.get("file")));
                System.out.println(output);
            }
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }

    private List<Map<String, String>> tryLLMSimilarity(String text, List<Map<String, String>> entries) {
        try {
            LLMService llm = new LLMService();
            StringBuilder sb = new StringBuilder();
            sb.append("Query: ").append(text).append("\n\nNotes:\n");
            for (int i = 0; i < entries.size(); i++) {
                Map<String, String> e = entries.get(i);
                sb.append(i).append(". [").append(e.get("slug")).append("] ")
                    .append(e.get("content").replaceAll("\n", " "));
                if (sb.length() > 8000) { sb.append("..."); break; }
                sb.append("\n");
            }
            String result = llm.query(
                sb + "\n\nReturn only the line numbers of the 5 most similar notes, comma-separated, ordered by relevance. Example: 3,0,4,1,2",
                "You match notes to a query by meaning. Return only numbers.", 512);
            String[] nums = result.replaceAll("[^0-9,]", "").split(",");
            List<Map<String, String>> ranked = new ArrayList<>();
            for (String n : nums) {
                int idx = Integer.parseInt(n.trim());
                if (idx >= 0 && idx < entries.size()) {
                    Map<String, String> e = new HashMap<>(entries.get(idx));
                    e.put("_score", String.valueOf(1.0 - ranked.size() * 0.15));
                    ranked.add(e);
                }
            }
            return ranked.isEmpty() ? null : ranked;
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, String>> localSimilarity(String text, List<Map<String, String>> entries) {
        Set<String> queryTerms = tokenize(text);
        if (queryTerms.isEmpty()) return List.of();

        List<Map<String, String>> scored = new ArrayList<>();
        for (Map<String, String> entry : entries) {
            Set<String> entryTerms = tokenize(entry.get("content") + " " + entry.get("slug") + " " + entry.get("tags"));
            double intersection = 0;
            for (String t : queryTerms) {
                if (entryTerms.contains(t)) intersection++;
            }
            double score = intersection / Math.max(queryTerms.size(), 1);
            if (score > 0) {
                Map<String, String> e = new HashMap<>(entry);
                e.put("_score", String.valueOf(Math.min(score, 0.95)));
                scored.add(e);
            }
        }

        scored.sort((a, b) -> Double.compare(
            Double.parseDouble(b.get("_score")), Double.parseDouble(a.get("_score"))));
        return scored;
    }

    private Set<String> tokenize(String s) {
        return Arrays.stream(s.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+"))
            .filter(w -> w.length() > 2)
            .collect(Collectors.toSet());
    }
}