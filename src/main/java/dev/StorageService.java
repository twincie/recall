package dev;

import dev.recall.SlugUtil;
import picocli.CommandLine;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StorageService {
    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".recall");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String append(String filename, String slug, String type, String tags, String content) throws IOException {
        return append(filename, slug, type, tags, content, false);
    }

    public String append(String filename, String slug, String type, String tags, String content, boolean sensitive) throws IOException {
        return append(filename, slug, type, tags, content, sensitive ? "true" : "false");
    }

    public String append(String filename, String slug, String type, String tags, String content, String sensitiveMode) throws IOException {
        return appendWithExt(filename, slug, type, tags, "", content, sensitiveMode);
    }

    public String appendWithExt(String filename, String slug, String type, String tags, String ext, String content) throws IOException {
        return appendWithExt(filename, slug, type, tags, ext, content, "false");
    }

    public String appendWithExt(String filename, String slug, String type, String tags, String ext, String content, String sensitiveMode) throws IOException {
        Path file = dataDir.resolve(filename);
        String today = LocalDate.now().format(DATE_FORMAT);
        String extLine = (ext == null || ext.isBlank()) ? "" : "**ext:** " + ext + "\n";
        String tagsLine = (tags == null || tags.isBlank()) ? "" : "**tags:** " + tags + "\n";
        String sensitiveLine = "true".equals(sensitiveMode) ? "**sensitive:** true\n" :
                               "auto".equals(sensitiveMode) ? "**sensitive:** auto\n" : "";
        String entry = String.format("## %s | %s\n%s%s%s**type:** %s\n\n%s\n\n---\n\n",
            today, slug, extLine, tagsLine, sensitiveLine, type, content);

        Files.writeString(file, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        gitBackup("recall: auto-backup");
        syncAuto();
        return file.getFileName().toString();
    }

    private final Path dataDir;

    public StorageService() throws IOException {
        if (!Files.exists(CONFIG_DIR)) {
            Files.createDirectories(CONFIG_DIR);
        }
        this.dataDir = loadDataDir();
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }
    }

    private Path loadDataDir() throws IOException {
        if (Files.exists(CONFIG_FILE)) {
            Properties props = new Properties();
            props.load(new StringReader(Files.readString(CONFIG_FILE)));
            String path = props.getProperty("storage.path");
            if (path != null && !path.isBlank()) {
                return Paths.get(path);
            }
        }
        return CONFIG_DIR;
    }

    public Path getDataDir() {
        return dataDir;
    }

    private Properties loadConfig() {
        Properties props = new Properties();
        try {
            if (Files.exists(CONFIG_FILE)) {
                props.load(new StringReader(Files.readString(CONFIG_FILE)));
            }
        } catch (IOException e) {
            // silent
        }
        return props;
    }

    public void syncAuto() {
        Properties config = loadConfig();
        if (!"true".equals(config.getProperty("sync.auto", "false"))) return;

        Path gitDir = dataDir.resolve(".git");
        if (!Files.exists(gitDir)) return;

        String remote = config.getProperty("sync.remote", "");
        String branch = config.getProperty("sync.branch", "");
        try {
            String pushCmd = "cd " + dataDir + " && git add . && git commit --allow-empty -m \"recall: auto-sync\"";
            if (!remote.isBlank()) {
                pushCmd += " && git push " + remote;
                if (!branch.isBlank()) pushCmd += " " + branch;
            } else {
                pushCmd += " && git push -q 2>/dev/null";
            }
            Process p = new ProcessBuilder("bash", "-c", pushCmd).start();
            boolean finished = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && p.exitValue() != 0) {
                String err = new String(p.getErrorStream().readAllBytes()).trim();
                if (!err.isBlank()) {
                    System.err.println(CommandLine.Help.Ansi.AUTO.string("@|red \u2717 Sync failed: " + err + "|@"));
                }
            }
        } catch (Exception e) {
            System.err.println(CommandLine.Help.Ansi.AUTO.string("@|red \u2717 Sync failed: " + e.getMessage() + "|@"));
        }
    }

    public void gitBackup(String message) {
        try {
            Path gitDir = dataDir.resolve(".git");
            if (!Files.exists(gitDir)) return;
            ProcessBuilder pb = new ProcessBuilder("bash", "-c",
                "cd " + dataDir + " && git add . && git commit -m " +
                "\"" + message + "\" && git push -q 2>/dev/null");
            Process p = pb.start();
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            // silent — git backup is optional
        }
    }

    public List<Map<String, String>> search(String query, String tag) throws IOException {
        List<Map<String, String>> results = new ArrayList<>();
        String[] terms = query.toLowerCase().split("\\s+");
        List<Pattern> patterns = new ArrayList<>();
        for (String term : terms) {
            if (term.length() < 2) continue;
            patterns.add(Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE));
        }
        if (patterns.isEmpty()) return results;

        try (Stream<Path> paths = Files.list(dataDir)) {
            paths.filter(path -> path.toString().endsWith(".md"))
                .forEach(file -> {
                    try {
                        parseBlocks(file).forEach(block -> {
                            boolean matchesTag = tag == null || tag.isBlank()
                                || block.get("tags").toLowerCase().contains(tag.toLowerCase());
                            if (!matchesTag) return;
                            for (Pattern p : patterns) {
                                if (!p.matcher(block.get("content")).find()
                                    && !p.matcher(block.get("slug")).find()
                                    && !p.matcher(block.get("tags")).find()) {
                                    return;
                                }
                            }
                            block.put("file", file.getFileName().toString());
                            results.add(block);
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        }
        return results;
    }

    public List<Map<String, String>> list(int count, String tag, String type) throws IOException {
        List<Map<String, String>> all = new ArrayList<>();

        try (Stream<Path> paths = Files.list(dataDir)) {
            paths.filter(path -> path.toString().endsWith(".md"))
                .forEach(file -> {
                    try {
                        parseBlocks(file).forEach(block -> {
                            boolean matchesTag = tag == null || tag.isBlank()
                                || block.get("tags").toLowerCase().contains(tag.toLowerCase());
                            boolean matchesType = type == null || type.isBlank()
                                || block.get("type").equalsIgnoreCase(type);
                            if (matchesTag && matchesType) {
                                block.put("file", file.getFileName().toString());
                                all.add(block);
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        }

        all.sort((a, b) -> b.get("date").compareTo(a.get("date")));
        return all.stream().limit(count).collect(Collectors.toList());
    }

    public List<Map<String, String>> today() throws IOException {
        String today = LocalDate.now().format(DATE_FORMAT);
        List<Map<String, String>> all = new ArrayList<>();

        try (Stream<Path> paths = Files.list(dataDir)) {
            paths.filter(path -> path.toString().endsWith(".md"))
                .forEach(file -> {
                    try {
                        parseBlocks(file).forEach(block -> {
                            if (block.get("date").equals(today)) {
                                block.put("file", file.getFileName().toString());
                                all.add(block);
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        }
        return all;
    }

    public List<Map<String, String>> parseBlocks(Path file) throws IOException {
        List<Map<String, String>> blocks = new ArrayList<>();
        String content = Files.readString(file);
        if (content.isBlank()) return blocks;

        String[] parts = content.split("\\n---\\n\\n");

        Pattern headerPattern = Pattern.compile(
            "##\\s+([^|]+)\\s*\\|\\s*([^\\n]+)\\n" +
            "(?:\\*\\*ext:\\*\\*\\s*([^\\n]*)\\n)?" +
            "(?:\\*\\*tags:\\*\\*\\s*([^\\n]*)\\n)?" +
            "(?:\\*\\*sensitive:\\*\\*\\s*([^\\n]*)\\n)?" +
            "\\*\\*type:\\*\\*\\s*([^\\n]+)\\n\\n" +
            "([\\s\\S]*)"
        );

        for (String part : parts) {
            if (part.trim().isEmpty()) continue;
            Matcher matcher = headerPattern.matcher(part);
            if (matcher.find()) {
                Map<String, String> block = new HashMap<>();
                block.put("date", matcher.group(1).trim());
                block.put("slug", matcher.group(2).trim());
                block.put("ext", matcher.group(3) != null ? matcher.group(3).trim() : "");
                block.put("tags", matcher.group(4) != null ? matcher.group(4).trim() : "");
                String sensitive = matcher.group(5);
                block.put("sensitive", sensitive != null ? sensitive.trim() : "false");
                block.put("type", matcher.group(6).trim());
                block.put("content", matcher.group(7).trim());
                blocks.add(block);
            }
        }
        return blocks;
    }

    public List<Map<String, String>> allEntries() throws IOException {
        List<Map<String, String>> all = new ArrayList<>();
        try (Stream<Path> paths = Files.list(dataDir)) {
            paths.filter(p -> p.toString().endsWith(".md")).forEach(file -> {
                try { parseBlocks(file).forEach(b -> { b.put("file", file.getFileName().toString()); all.add(b); });
                } catch (IOException e) { e.printStackTrace(); }
            });
        }
        return all;
    }

    public Map<String, String> findBySlug(String slug) throws IOException {
        for (Map<String, String> entry : allEntries()) {
            if (entry.get("slug").equals(slug)) return entry;
        }
        return null;
    }

    public List<Map<String, String>> findAllBySlug(String slug) throws IOException {
        List<Map<String, String>> matches = new ArrayList<>();
        for (Map<String, String> entry : allEntries()) {
            if (entry.get("slug").equals(slug)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    public List<Map<String, String>> entriesOnDate(LocalDate date) throws IOException {
        String target = date.format(DATE_FORMAT);
        List<Map<String, String>> all = new ArrayList<>();
        try (Stream<Path> paths = Files.list(dataDir)) {
            paths.filter(p -> p.toString().endsWith(".md")).forEach(file -> {
                try { parseBlocks(file).forEach(b -> {
                    if (b.get("date").equals(target)) {
                        b.put("file", file.getFileName().toString()); all.add(b);
                    }
                }); } catch (IOException e) { e.printStackTrace(); }
            });
        }
        return all;
    }

    public boolean updateEntry(String filename, String slug, String newContent) throws IOException {
        Path file = dataDir.resolve(filename);
        if (!Files.exists(file)) return false;

        List<Map<String, String>> blocks = parseBlocks(file);
        boolean found = false;

        StringBuilder sb = new StringBuilder();
        for (Map<String, String> block : blocks) {
            if (block.get("slug").equals(slug)) {
                block.put("content", newContent.trim());
                block.put("date", LocalDate.now().format(DATE_FORMAT));
                found = true;
            }
            sb.append(formatEntry(block));
        }

        if (!found) return false;

        Files.writeString(file, sb.toString());
        gitBackup("recall: update " + slug);
        syncAuto();
        return true;
    }

    private String formatEntry(Map<String, String> block) {
        String date = block.get("date");
        String slug = block.get("slug");
        String ext = block.get("ext");
        String tags = block.get("tags");
        String sensitive = block.get("sensitive");
        String type = block.get("type");
        String content = block.get("content");

        String extLine = (ext == null || ext.isBlank()) ? "" : "**ext:** " + ext + "\n";
        String tagsLine = (tags == null || tags.isBlank()) ? "" : "**tags:** " + tags + "\n";
        String sensitiveLine = "true".equals(sensitive) ? "**sensitive:** true\n" :
                               "auto".equals(sensitive) ? "**sensitive:** auto\n" : "";

        return String.format("## %s | %s\n%s%s%s**type:** %s\n\n%s\n\n---\n\n",
            date, slug, extLine, tagsLine, sensitiveLine, type, content);
    }

    public boolean clearFile(String filename) throws IOException {
        Path file = dataDir.resolve(filename);
        if (!Files.exists(file)) return false;
        Files.writeString(file, "");
        gitBackup("recall: cleared " + filename);
        syncAuto();
        return true;
    }

    public boolean removeEntry(String filename, String slug) throws IOException {
        Path file = dataDir.resolve(filename);
        if (!Files.exists(file)) return false;

        List<Map<String, String>> blocks = parseBlocks(file);
        List<Map<String, String>> remaining = new ArrayList<>();
        boolean found = false;

        for (Map<String, String> block : blocks) {
            if (block.get("slug").equals(slug)) {
                found = true;
            } else {
                remaining.add(block);
            }
        }

        if (!found) return false;

        StringBuilder sb = new StringBuilder();
        for (Map<String, String> block : remaining) {
            sb.append(formatEntry(block));
        }

        Files.writeString(file, sb.toString());
        gitBackup("recall: removed " + slug);
        syncAuto();
        return true;
    }

    public Map<String, Integer> fileEntryCounts() throws IOException {
        Map<String, Integer> counts = new HashMap<>();
        try (Stream<Path> paths = Files.list(dataDir)) {
            paths.filter(p -> p.toString().endsWith(".md")).forEach(file -> {
                try {
                    String name = file.getFileName().toString();
                    counts.put(name, parseBlocks(file).size());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
        return counts;
    }

    public static String makeSlug(String text) {
        return SlugUtil.makeSlug(text);
    }
}
