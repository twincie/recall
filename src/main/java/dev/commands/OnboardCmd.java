package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Map;

@Command(name = "onboard", description = "Package your notes for a new teammate")
public class OnboardCmd implements Runnable {
    @Parameters(description = "Teammate name (optional, defaults to markdown output)", arity = "0..1")
    private String teammate;

    @CommandLine.Option(names = {"--topic"}, description = "Filter by tag/topic")
    private String topic;

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
            List<Map<String, String>> all = storageService.list(100, topic, null);

            if (all.isEmpty()) {
                System.out.println("No notes to package" + (topic != null ? " for topic: " + topic : "") + ".");
                return;
            }

            StringBuilder doc = new StringBuilder();
            doc.append("# Onboarding Notes\n\n");
            if (teammate != null && !teammate.isBlank()) {
                doc.append("For: **").
append(teammate).append("**\n\n");
            }
            if (topic != null && !topic.isBlank()) {
                doc.append("Topic: **").append(topic).append("**\n\n");
            }
            doc.append("---\n\n");

            String currentType = "";
            for (Map<String, String> e : all) {
                String type = e.get("type");
                if (!type.equals(currentType)) {
                    doc.append("## ").append(type.substring(0, 1).toUpperCase())
                        .append(type.substring(1)).append("s\n\n");
                    currentType = type;
                }
                doc.append("### ").append(e.get("slug")).append("\n");
                if (!e.get("tags").isBlank()) {
                    doc.append("*Tags: ").append(e.get("tags")).append("*  \n");
                }
                doc.append("*Saved: ").append(e.get("date")).append("*  \n\n");
                doc.append(e.get("content")).append("\n\n");
            }

            System.out.println(doc.toString());
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }
}