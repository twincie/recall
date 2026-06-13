package dev.commands;

import dev.LLMService;
import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Command(name = "standup", description = "Generate a standup summary from the last 24h")
public class StandupCmd implements Runnable {
    private final StorageService storageService;

    public StandupCmd() {
        try {
            this.storageService = new StorageService();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);
            List<Map<String, String>> todayEntries = storageService.entriesOnDate(today);
            List<Map<String, String>> yesterdayEntries = storageService.entriesOnDate(yesterday);

            if (todayEntries.isEmpty() && yesterdayEntries.isEmpty()) {
                System.out.println("Nothing saved in the last 24h.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Entries from the last 24h:\n\n");
            for (Map<String, String> e : todayEntries) {
                sb.append("- [").append(e.get("type")).append("] ").append(e.get("slug"))
                    .append(": ").append(e.get("content").replaceAll("\n", " ")).append("\n");
            }
            for (Map<String, String> e : yesterdayEntries) {
                sb.append("- [").append(e.get("type")).append("] ").append(e.get("slug"))
                    .append(": ").append(e.get("content").replaceAll("\n", " ")).append("\n");
            }

            try {
                LLMService llm = new LLMService();
                String summary = llm.query(sb.toString(),
                    "Generate a concise standup summary from these entries. Format:\n" +
                    "Yesterday: bullet points\nToday: bullet points\nBlockers: none",
                    1024);
                System.out.println(summary);
            } catch (Exception e) {
                System.out.println("LLM unavailable. Raw entries:\n");
                System.out.println(sb.toString());
            }
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }
}