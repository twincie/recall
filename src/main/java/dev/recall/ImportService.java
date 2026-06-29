package dev.recall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.StorageService;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ImportService {
    private final StorageService storageService;
    private final Map<String, ImportStats> stats;
    private int totalImported;
    private int globalLimit;
    private final Scanner scanner;
    private String targetType;
    private String targetFile;
    private boolean confirm;
    private boolean dryRun;

    private static final Map<String, String> TYPE_TO_FILE = new LinkedHashMap<>();
    static {
        TYPE_TO_FILE.put("note", "notes.md");
        TYPE_TO_FILE.put("command", "commands.md");
        TYPE_TO_FILE.put("snippet", "snippets.md");
        TYPE_TO_FILE.put("ticket", "tickets.md");
        TYPE_TO_FILE.put("retro", "retros.md");
        TYPE_TO_FILE.put("runbook", "runbooks.md");
        TYPE_TO_FILE.put("knowledge", "knowledge.md");
    }

    private static final Set<String> VALID_TYPES = TYPE_TO_FILE.keySet();

    static class ImportStats {
        int notes;
        int commands;
        int snippets;
    }

    public ImportService() throws IOException {
        this.storageService = new StorageService();
        this.stats = new LinkedHashMap<>();
        this.totalImported = 0;
        this.globalLimit = Integer.MAX_VALUE;
        this.scanner = new Scanner(System.in);
    }

    public static boolean isValidType(String type) {
        return VALID_TYPES.contains(type);
    }

    public static String typeToFile(String type) {
        return TYPE_TO_FILE.getOrDefault(type, "notes.md");
    }

    public void setLimit(int limit) {
        this.globalLimit = limit;
    }

    public void setTargetType(String type) {
        if (!isValidType(type)) {
            throw new IllegalArgumentException("Invalid type: " + type + ". Valid types: " + String.join(", ", VALID_TYPES));
        }
        this.targetType = type;
    }

    public void setTargetFile(String file) {
        this.targetFile = file;
    }

    public void setConfirm(boolean confirm) {
        this.confirm = confirm;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public int getTotalImported() {
        return totalImported;
    }

    private String resolveFile(String detectedType) {
        if (targetFile != null) return targetFile;
        if (targetType != null) return typeToFile(targetType);
        return typeToFile(detectedType);
    }

    private String resolveType(String detectedType) {
        if (targetType != null) return targetType;
        return detectedType;
    }

    public void importFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File not found: " + filePath);
        }
        String ext = extensionOf(filePath);
        String source = path.getFileName().toString();

        switch (ext) {
            case "md" -> importMarkdown(path, source);
            case "txt" -> importText(path, source);
            case "sh" -> importShell(path, source);
            case "json" -> importJsonSnippets(path, source);
            default -> throw new IllegalArgumentException("Unsupported extension: " + ext);
        }
    }

    public void importHistory() throws IOException {
        String home = System.getProperty("user.home");
        Path zsh = Paths.get(home, ".zsh_history");
        Path bash = Paths.get(home, ".bash_history");

        Path historyFile = null;
        if (Files.exists(zsh)) historyFile = zsh;
        else if (Files.exists(bash)) historyFile = bash;

        if (historyFile == null) {
            throw new FileNotFoundException("No shell history found (~/.zsh_history or ~/.bash_history)");
        }

        List<String> commands = parseHistory(historyFile);
        commands = commands.stream()
            .filter(c -> c.length() >= 20)
            .distinct()
            .collect(Collectors.toList());

        if (commands.isEmpty()) {
            System.out.println("No commands found (minimum 20 chars).");
            return;
        }

        String file = resolveFile("command");
        String type = resolveType("command");

        int showCount = Math.min(commands.size(), 50);
        System.out.println(showCount + " commands found. Pick which to import (space-separated numbers, or 'all'):");
        System.out.println();
        for (int i = 0; i < showCount; i++) {
            String display = commands.get(i).length() > 80 ? commands.get(i).substring(0, 77) + "..." : commands.get(i);
            System.out.printf("%2d. %s%n", i + 1, display);
        }
        System.out.println();
        System.out.print("Enter selection: ");

        String input = scanner.nextLine().trim();
        Set<Integer> selected = new HashSet<>();
        if (input.equalsIgnoreCase("all")) {
            for (int i = 0; i < commands.size(); i++) selected.add(i);
        } else {
            for (String part : input.split("[,\\s]+")) {
                try {
                    int idx = Integer.parseInt(part) - 1;
                    if (idx >= 0 && idx < commands.size()) selected.add(idx);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (selected.isEmpty()) {
            System.out.println("No commands selected.");
            return;
        }

        ImportStats s = getStats(historyFile.getFileName().toString());
        for (int idx : selected) {
            if (totalImported >= globalLimit) break;
            String cmd = commands.get(idx);
            String firstLine = cmd.lines().findFirst().orElse(cmd);
            String slug = SlugUtil.makeSlug(firstLine.length() > 40 ? firstLine.substring(0, 40) : firstLine);
            slug = handleDuplicate(slug, type, "");
            if (slug != null) {
                saveToFile(file, type, slug, "shell", cmd, s);
                s.commands++;
            }
        }
    }

    public void importVSCode() throws IOException {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        Path snippetsDir;

        if (os.contains("mac")) {
            snippetsDir = Paths.get(home, "Library", "Application Support", "Code", "User", "snippets");
        } else if (os.contains("linux")) {
            snippetsDir = Paths.get(home, ".config", "Code", "User", "snippets");
        } else {
            throw new UnsupportedOperationException("VS Code snippets not supported on this OS");
        }

        if (!Files.isDirectory(snippetsDir)) {
            throw new FileNotFoundException("VS Code snippets folder not found: " + snippetsDir);
        }

        String file = resolveFile("snippet");
        String type = resolveType("snippet");
        ObjectMapper mapper = new ObjectMapper();
        try (Stream<Path> files = Files.list(snippetsDir)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    String lang = p.getFileName().toString().replaceAll("\\.json$", "");
                    JsonNode root = mapper.readTree(p.toFile());
                    Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                    while (fields.hasNext()) {
                        if (totalImported >= globalLimit) return;
                        Map.Entry<String, JsonNode> entry = fields.next();
                        String name = entry.getKey();
                        JsonNode snip = entry.getValue();

                        JsonNode bodyNode = snip.get("body");
                        if (bodyNode == null) continue;
                        String body;
                        if (bodyNode.isArray()) {
                            StringBuilder sb = new StringBuilder();
                            for (JsonNode n : bodyNode) {
                                if (sb.length() > 0) sb.append("\n");
                                sb.append(n.asText());
                            }
                            body = sb.toString();
                        } else {
                            body = bodyNode.asText();
                        }

                        String slug = SlugUtil.makeSlug(name);
                        slug = handleDuplicate(slug, type, lang);
                        if (slug != null) {
                            saveToFile(file, type, slug, lang, body, getStats(p.getFileName().toString()));
                            ImportStats s = getStats(p.getFileName().toString());
                            s.snippets++;
                            totalImported++;
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error reading " + p.getFileName() + ": " + e.getMessage());
                }
            });
        }
    }

    public void importDir(String dirPath) throws IOException {
        Path dir = Paths.get(dirPath);
        if (!Files.isDirectory(dir)) {
            throw new FileNotFoundException("Directory not found: " + dirPath);
        }

        boolean perFilePrompt = (targetType == null && targetFile == null);

        try (Stream<Path> files = Files.list(dir)) {
            List<Path> matched = files
                .filter(p -> !Files.isDirectory(p))
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".sh");
                })
                .collect(Collectors.toList());

            if (matched.isEmpty()) {
                System.out.println("No .md, .txt, or .sh files found in: " + dirPath);
                return;
            }

            for (Path p : matched) {
                if (totalImported >= globalLimit) break;

                if (perFilePrompt) {
                    String fileName = p.getFileName().toString();
                    String ext = extensionOf(fileName);
                    String detectedType = switch (ext) {
                        case "sh" -> "command";
                        default -> "note";
                    };
                    System.out.println();
                    System.out.print(fileName + " [" + detectedType + "]: ");
                    String input = scanner.nextLine().trim().toLowerCase();

                    if (input.isEmpty() || input.equals("y") || input.equals("yes")) {
                        // use detected type
                    } else if (input.equals("s") || input.equals("skip")) {
                        System.out.println("  skipped");
                        continue;
                    } else if (isValidType(input)) {
                        targetType = input;
                    } else {
                        System.out.println("  skipped (invalid type)");
                        continue;
                    }
                }

                try {
                    importFile(p.toString());
                } catch (Exception e) {
                    System.err.println("Error importing " + p.getFileName() + ": " + e.getMessage());
                }

                if (perFilePrompt) {
                    targetType = null;
                }
            }
        }
    }

    public void printStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("\u2713 Import complete\n");
        for (Map.Entry<String, ImportStats> entry : stats.entrySet()) {
            ImportStats s = entry.getValue();
            if (s.notes > 0) {
                sb.append(String.format("  %2d %-10s from %s%n", s.notes, s.notes == 1 ? "note" : "notes", entry.getKey()));
            }
            if (s.commands > 0) {
                sb.append(String.format("  %2d %-10s from %s%n", s.commands, s.commands == 1 ? "command" : "commands", entry.getKey()));
            }
            if (s.snippets > 0) {
                sb.append(String.format("  %2d %-10s from %s%n", s.snippets, s.snippets == 1 ? "snippet" : "snippets", entry.getKey()));
            }
        }
        System.out.print(sb.toString());
    }

    private void importMarkdown(Path path, String source) throws IOException {
        String content = Files.readString(path);
        String[] sections = content.split("(?m)^##\\s+");
        ImportStats s = getStats(source);
        String file = resolveFile("note");
        String type = resolveType("note");
        for (int i = 0; i < sections.length; i++) {
            if (totalImported >= globalLimit) break;
            String section = sections[i].trim();
            if (section.isBlank()) continue;

            String[] lines = section.split("\n", 2);
            String heading = lines[0].trim();
            String body = lines.length > 1 ? lines[1].trim() : "";
            if (body.isBlank()) body = heading;

            String slug = SlugUtil.makeSlug(heading);
            slug = handleDuplicate(slug, type, "");
            if (slug != null) {
                saveToFile(file, type, slug, "", body, s);
                s.notes++;
            }
        }
    }

    private void importText(Path path, String source) throws IOException {
        String content = Files.readString(path);
        String[] blocks = content.split("\\n\\n+");
        ImportStats s = getStats(source);
        String file = resolveFile("note");
        String type = resolveType("note");
        for (String block : blocks) {
            if (totalImported >= globalLimit) break;
            block = block.trim();
            if (block.isBlank()) continue;
            String firstLine = block.lines().findFirst().orElse("");
            String slug = SlugUtil.makeSlug(firstLine.length() > 40 ? firstLine.substring(0, 40) : firstLine);
            slug = handleDuplicate(slug, type, "");
            if (slug != null) {
                saveToFile(file, type, slug, "", block, s);
                s.notes++;
            }
        }
    }

    private void importShell(Path path, String source) throws IOException {
        String content = Files.readString(path);
        String[] blocks = content.split("\\n\\n+");
        ImportStats s = getStats(source);
        String file = resolveFile("command");
        String type = resolveType("command");
        for (String block : blocks) {
            if (totalImported >= globalLimit) break;
            block = block.trim();
            if (block.isBlank()) continue;
            String firstLine = block.lines().findFirst().orElse("");
            String slug = SlugUtil.makeSlug(firstLine.length() > 40 ? firstLine.substring(0, 40) : firstLine);
            slug = handleDuplicate(slug, type, "");
            if (slug != null) {
                saveToFile(file, type, slug, "shell", block, s);
                s.commands++;
            }
        }
    }

    private void importJsonSnippets(Path path, String source) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(path.toFile());
        ImportStats s = getStats(source);
        String file = resolveFile("snippet");
        String type = resolveType("snippet");
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            if (totalImported >= globalLimit) break;
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = entry.getKey();
            JsonNode snip = entry.getValue();

            JsonNode bodyNode = snip.get("body");
            if (bodyNode == null) continue;
            String body;
            if (bodyNode.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode n : bodyNode) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(n.asText());
                }
                body = sb.toString();
            } else {
                body = bodyNode.asText();
            }

            String slug = SlugUtil.makeSlug(name);
            slug = handleDuplicate(slug, type, "");
            if (slug != null) {
                saveToFile(file, type, slug, "", body, s);
                s.snippets++;
            }
        }
    }

    private void saveToFile(String file, String type, String slug, String tags, String content, ImportStats s) throws IOException {
        if (confirm) {
            System.out.println();
            System.out.println("Import: " + slug);
            String preview = content.replaceAll("\n", " ").trim();
            if (preview.length() > 80) preview = preview.substring(0, 77) + "...";
            System.out.println("  " + preview);
            System.out.print("Save? [Y/n] ");
            String input = scanner.nextLine().trim().toLowerCase();
            if (!input.isEmpty() && !input.equals("y") && !input.equals("yes")) {
                System.out.println("  skipped");
                return;
            }
        }

        if (dryRun) {
            System.out.println("  [dry-run] " + slug + " \u2192 " + file + " (type: " + type + ")");
            totalImported++;
            return;
        }

        storageService.append(file, slug, type, tags, content);
        totalImported++;
    }

    private List<String> parseHistory(Path historyFile) throws IOException {
        List<String> commands = new ArrayList<>();
        for (String line : Files.readAllLines(historyFile)) {
            line = line.trim();
            if (line.isBlank()) continue;
            if (line.startsWith(": ")) {
                int semicolon = line.indexOf(";", 2);
                if (semicolon > 0) {
                    line = line.substring(semicolon + 1).trim();
                }
            }
            if (!line.isBlank()) {
                commands.add(line);
            }
        }
        return commands;
    }

    private String handleDuplicate(String slug, String type, String tags) throws IOException {
        if (dryRun) return slug;

        Map<String, String> existing = storageService.findBySlug(slug);
        if (existing == null) return slug;

        System.out.print("\u26A0 '" + slug + "' already exists. Skip / Overwrite / Save as new? [S/o/n] ");
        String input = scanner.nextLine().trim().toLowerCase();

        if (input.equals("o")) {
            return slug;
        } else if (input.equals("n")) {
            int counter = 1;
            String newSlug;
            do {
                newSlug = slug + "-" + counter;
                counter++;
            } while (storageService.findBySlug(newSlug) != null);
            return newSlug;
        } else {
            return null;
        }
    }

    private ImportStats getStats(String source) {
        return stats.computeIfAbsent(source, k -> new ImportStats());
    }

    private String extensionOf(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase();
    }
}
