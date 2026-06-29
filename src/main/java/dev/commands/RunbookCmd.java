package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

@Command(name = "runbook", aliases = {"-rb", "--runbook"}, mixinStandardHelpOptions = true, description = "Save and retrieve incident response steps by service")
public class RunbookCmd implements Runnable {
    @Parameters(description = "Service name", arity = "0..1")
    private String serviceName;

    @Option(names = {"--add"}, description = "Step to add")
    private String addStep;

    @Option(names = {"--remove"}, description = "Remove step by number")
    private Integer removeStep;

    @Option(names = {"--list", "-l"}, description = "List all runbooks")
    private boolean listAll;

    @Option(names = {"--edit", "-e"}, description = "Edit runbook in editor")
    private boolean edit;

    @Option(names = {"--delete"}, description = "Delete a runbook entry by service name")
    private String deleteService;

    @Option(names = {"--plain", "-p"}, description = "Plain output without colors")
    private boolean plain;

    private final StorageService storageService;

    private static final String RUNBOOK_FILE = "runbooks.md";

    public RunbookCmd() {
        try {
            this.storageService = new StorageService();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            if (listAll) {
                list();
                return;
            }
            if (deleteService != null) {
                delete(deleteService);
                return;
            }
            if (serviceName == null || serviceName.isBlank()) {
                System.out.println("Usage:");
                System.out.println("  recall runbook <service>              Show runbook");
                System.out.println("  recall runbook <service> --add <step>  Add step");
                System.out.println("  recall runbook <service> --remove <n>  Remove step");
                System.out.println("  recall runbook <service> --edit        Edit in editor");
                System.out.println("  recall runbook --list                  List all runbooks");
                System.out.println("  recall runbook --delete <service>      Delete runbook");
                return;
            }

            String slug = StorageService.makeSlug(serviceName);
            if (edit) {
                editRunbook(slug);
                return;
            }
            if (addStep != null) {
                addStepToRunbook(slug);
                return;
            }
            if (removeStep != null) {
                removeStepFromRunbook(slug, removeStep);
                return;
            }
            showRunbook(slug);
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }

    private void addStepToRunbook(String slug) throws IOException {
        Map<String, String> existing = storageService.findBySlug(slug);
        if (existing != null && RUNBOOK_FILE.equals(existing.get("file"))) {
            String content = existing.get("content");
            List<String> steps = parseSteps(content);
            int num = steps.size() + 1;
            steps.add(num + ". " + addStep);
            String newContent = String.join("\n", steps);
            storageService.updateEntry(RUNBOOK_FILE, slug, newContent);
        } else {
            String content = "1. " + addStep;
            storageService.append(RUNBOOK_FILE, slug, "runbook", slug, content);
        }

        String msg = CommandLine.Help.Ansi.AUTO.string(
            "@|green \u2713 Added step to |@@|bold,yellow " + slug + "|@@|green  runbook|@");
        System.out.println(msg);
    }

    private void removeStepFromRunbook(String slug, int num) throws IOException {
        Map<String, String> existing = storageService.findBySlug(slug);
        if (existing == null || !RUNBOOK_FILE.equals(existing.get("file"))) {
            System.err.println("No runbook for: " + slug);
            return;
        }

        List<String> steps = parseSteps(existing.get("content"));
        if (num < 1 || num > steps.size()) {
            System.err.println("Step " + num + " not found (1-" + steps.size() + ")");
            return;
        }

        steps.remove(num - 1);
        // renumber
        List<String> renumbered = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            String s = steps.get(i);
            int dot = s.indexOf('.');
            if (dot > 0 && dot < 4) {
                renumbered.add((i + 1) + "." + s.substring(dot + 1));
            } else {
                renumbered.add(s);
            }
        }

        String newContent = String.join("\n", renumbered);
        storageService.updateEntry(RUNBOOK_FILE, slug, newContent);
        System.out.println("Removed step " + num);
    }

    private void editRunbook(String slug) throws IOException {
        Map<String, String> existing = storageService.findBySlug(slug);
        String content;
        if (existing != null && RUNBOOK_FILE.equals(existing.get("file"))) {
            content = existing.get("content");
        } else {
            content = "";
        }

        Path tmp = Files.createTempFile("recall-runbook-", ".md");
        Files.writeString(tmp, content);
        ProcessBuilder pb = new ProcessBuilder(
            System.getenv().getOrDefault("EDITOR", "nano"), tmp.toString());
        pb.inheritIO();
        int code;
        try {
            code = pb.start().waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.err.println("Editor interrupted");
            Files.deleteIfExists(tmp);
            return;
        }
        if (code != 0) {
            System.err.println("Editor exited with code " + code);
            Files.deleteIfExists(tmp);
            return;
        }
        String newContent = Files.readString(tmp).trim();
        Files.deleteIfExists(tmp);

        if (existing != null && RUNBOOK_FILE.equals(existing.get("file"))) {
            storageService.updateEntry(RUNBOOK_FILE, slug, newContent);
        } else {
            storageService.append(RUNBOOK_FILE, slug, "runbook", slug, newContent);
        }
        System.out.println("Saved runbook: " + slug);
    }

    private void showRunbook(String slug) throws IOException {
        Map<String, String> existing = storageService.findBySlug(slug);
        if (existing == null || !RUNBOOK_FILE.equals(existing.get("file"))) {
            System.out.println("No runbook for '" + serviceName + "'. Create one: recall runbook " + serviceName + " --add \"<step>\"");
            return;
        }

        String heading = String.format("=== %s runbook ===", slug);
        if (plain) {
            System.out.println(heading);
        } else {
            System.out.println(CommandLine.Help.Ansi.AUTO.string(
                "@|bold,cyan " + heading + "|@"));
        }

        String content = existing.get("content");
        if (content != null && !content.isBlank()) {
            System.out.println();
            System.out.println(content);
        }
        System.out.println();
        System.out.println("  recall runbook " + serviceName + " --add \"<step>\"");
        System.out.println("  recall runbook " + serviceName + " --remove <n>");
        System.out.println("  recall runbook " + serviceName + " --edit");
    }

    private void list() throws IOException {
        List<Map<String, String>> all = storageService.allEntries();
        List<Map<String, String>> runbooks = all.stream()
            .filter(e -> "runbook".equals(e.get("type")) && RUNBOOK_FILE.equals(e.get("file")))
            .collect(Collectors.toList());

        if (runbooks.isEmpty()) {
            System.out.println("No runbooks yet.");
            return;
        }

        System.out.println("Runbooks:\n");
        for (Map<String, String> e : runbooks) {
            List<String> steps = parseSteps(e.get("content"));
            if (plain) {
                System.out.println("  " + e.get("tags") + "  " + steps.size() + " steps");
            } else {
                System.out.println(CommandLine.Help.Ansi.AUTO.string(
                    String.format("  @|bold,green %s|@  @|cyan %d steps|@",
                        e.get("tags"), steps.size())));
            }
        }
    }

    private void delete(String slug) throws IOException {
        Map<String, String> existing = storageService.findBySlug(slug);
        if (existing == null || !RUNBOOK_FILE.equals(existing.get("file"))) {
            System.err.println("No runbook for: " + slug);
            return;
        }
        storageService.removeEntry(RUNBOOK_FILE, slug);
        System.out.println("Deleted runbook: " + slug);
    }

    private List<String> parseSteps(String content) {
        if (content == null || content.isBlank()) return new ArrayList<>();
        return content.lines()
            .filter(l -> l.matches("^\\d+\\..*"))
            .collect(Collectors.toList());
    }
}
