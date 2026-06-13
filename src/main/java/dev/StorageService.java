package dev;

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

    public String append(String filename, String slug, String type, String tags, String content) throws IOException {
        Path file = dataDir.resolve(filename);
        String today = LocalDate.now().format(DATE_FORMAT);
        String tagsLine = (tags == null || tags.isBlank()) ? "" : "**tags:** " + tags + "\n";
        String entry = String.format("## %s | %s\n%s**type:** %s\n\n%s\n\n---\n\n",
            today, slug, tagsLine, type, content);

        Files.writeString(file, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        gitBackup("recall: auto-backup");
        return file.getFileName().toString();
    }

    public List<Map<String, String>> search(String query, String tag) throws IOException {
        List<Map<String, String>> results = new ArrayList<>();
        String[] terms = query.toLowerCase().split("\\s+");
        StringBuilder regex = new StringBuilder();
        for (String term : terms) {
            if (regex.length() > 0) regex.append("|");
            regex.append(Pattern.quote(term));
        }
        Pattern pattern = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);

        try (Stream<Path> paths = Files.list(dataDir)) {
            paths.filter(path -> path.toString().endsWith(".md"))
                .forEach(file -> {
                    try {
                        parseBlocks(file).forEach(block -> {
                            boolean matchesQuery = pattern.matcher(block.get("content")).find()
                                || pattern.matcher(block.get("slug")).find()
                                || pattern.matcher(block.get("tags")).find();
                            boolean matchesTag = tag == null || tag.isBlank()
                                || block.get("tags").toLowerCase().contains(tag.toLowerCase());
                            if (matchesQuery && matchesTag) {
                                block.put("file", file.getFileName().toString());
                                results.add(block);
                            }
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
            "(?:\\*\\*tags:\\*\\*\\s*([^\\n]*)\\n)?" +
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
                block.put("tags", matcher.group(3) != null ? matcher.group(3).trim() : "");
                block.put("type", matcher.group(4).trim());
                block.put("content", matcher.group(5).trim());
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

    public static String makeSlug(String text) {
        return text.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .trim()
            .replaceAll("\\s+", "-")
            .replaceAll("-{2,}", "-");
    }
}