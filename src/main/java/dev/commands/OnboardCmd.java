package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Command(name = "onboard", aliases = {"-o", "--onboard"}, mixinStandardHelpOptions = true, description = "Package your notes for a new teammate")
public class OnboardCmd implements Runnable {
    @Parameters(description = "Teammate name (optional, defaults to markdown output)", arity = "0..1")
    private String teammate;

    @Option(names = {"--topic"}, description = "Filter by tag/topic")
    private String topic;

    @Option(names = {"--limit"}, description = "Maximum entries (default: 100)")
    private int limit = 100;

    @Option(names = {"--output", "-o"}, description = "Write to file instead of stdout")
    private String output;

    private final StorageService storageService;

    public OnboardCmd() {
        try {
            this.storageService = new StorageService();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            List<Map<String, String>> all = storageService.list(limit, topic, null);

            if (all.isEmpty()) {
                System.out.println("No notes to package" + (topic != null ? " for topic: " + topic : "") + ".");
                if (topic != null) {
                    System.out.println("Try without --topic to see all entries.");
                }
                return;
            }

            boolean limited = false;
            int total = storageService.allEntries().size();
            if (total > limit) {
                limited = true;
            }

            StringBuilder doc = new StringBuilder();
            doc.append("# Onboarding Notes\n\n");
            if (teammate != null && !teammate.isBlank()) {
                doc.append("For: **").append(teammate).append("**\n\n");
            }
            if (topic != null && !topic.isBlank()) {
                doc.append("Topic: **").append(topic).append("**\n\n");
            }
            if (limited) {
                doc.append("> Showing ").append(all.size()).append(" of ").append(total)
                    .append(" total entries. Use --limit to adjust.\n\n");
            }
            doc.append("---\n\n");

            // Table of contents
            String currentType = "";
            doc.append("## Table of Contents\n\n");
            for (Map<String, String> e : all) {
                String type = e.get("type");
                if (!type.equals(currentType)) {
                    String sectionName = Character.toUpperCase(type.charAt(0)) + type.substring(1) + "s";
                    doc.append("- ").append(sectionName).append("\n");
                    currentType = type;
                }
            }
            doc.append("\n---\n\n");

            currentType = "";
            for (Map<String, String> e : all) {
                String type = e.get("type");
                if (!type.equals(currentType)) {
                    String sectionName = Character.toUpperCase(type.charAt(0)) + type.substring(1) + "s";
                    doc.append("## ").append(sectionName).append("\n\n");
                    currentType = type;
                }
                doc.append("### ").append(e.get("slug")).append("\n");
                if (!e.get("tags").isBlank()) {
                    doc.append("*Tags: ").append(e.get("tags")).append("*  \n");
                }
                doc.append("*Saved: ").append(e.get("date")).append("*  \n\n");
                doc.append(e.get("content")).append("\n\n");
            }

            String result = doc.toString();
            if (output != null) {
                Files.writeString(Paths.get(output), result);
                System.out.println("Written to " + output);
            } else {
                System.out.println(result);
            }
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }
}
