package dev.commands;

import dev.LLMService;
import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.Scanner;

@Command(name = "ticket", aliases = {"-tk", "--ticket"}, mixinStandardHelpOptions = true, description = "Generate a Jira ticket (describe the task or auto-detect from git diff)")
public class TicketCmd implements Runnable {
    @Parameters(description = "Describe what you did (omit to use git diff HEAD)", arity = "0..*")
    private String[] parts;

    private final StorageService storageService;

    public TicketCmd() {
        try {
            this.storageService = new StorageService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            String taskDesc;
            String branch = exec("git rev-parse --abbrev-ref HEAD");
            String slug;

            if (parts != null && parts.length > 0) {
                taskDesc = String.join(" ", parts);
                slug = "ticket-" + StorageService.makeSlug(taskDesc.length() > 40
                    ? taskDesc.substring(0, 40) : taskDesc);
            } else {
                String diff = exec("git diff HEAD");
                if (diff == null || diff.isBlank()) {
                    System.out.println("No uncommitted changes found. Describe your task instead.");
                    return;
                }
                String log = exec("git log --oneline -5");
                taskDesc = String.format(
                    "Branch: %s\n\nRecent commits:\n%s\n\nDiff:\n%s",
                    branch != null ? branch : "unknown",
                    log != null ? log : "none",
                    diff.length() > 15000 ? diff.substring(0, 15000) + "\n... (truncated)" : diff);
                slug = "ticket-" + (branch != null ? StorageService.makeSlug(branch) : "unknown");
            }

            String prompt = "Generate a structured Jira ticket from this:\n\n" + taskDesc + "\n\n" +
                "Format:\n**Summary:** one-line description\n**Description:** what and why\n" +
                "**Changes:** key files changed\n**Testing:** how to verify\n**Risk:** low/medium/high";

            System.out.println("Analyzing...");
            LLMService llm = new LLMService();
            String ticket = llm.query(prompt,
                "You are a senior engineer writing concise Jira tickets. Be direct, no fluff.",
                2048);

            storageService.append("tickets.md", slug, "ticket", branch != null ? branch : "", ticket);

            String msg = CommandLine.Help.Ansi.AUTO.string(
                "@|green \u2713 Saved ticket  [|@@|bold,yellow " + slug + "|@@|green ]|@  tickets.md");
            System.out.println(msg);
            System.out.println();
            System.out.println(ticket);
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }

    private String exec(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"bash", "-c", cmd});
            try (Scanner s = new Scanner(p.getInputStream()).useDelimiter("\\A")) {
                String result = s.hasNext() ? s.next().strip() : null;
                p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                return result;
            }
        } catch (Exception e) {
            return null;
        }
    }
}