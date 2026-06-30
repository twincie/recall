package dev.recall.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Command(name = "browse",
    aliases = {"-b", "--browse"},
    mixinStandardHelpOptions = true,
    description = "Interactive terminal browser for all entries")
public class BrowseCmd implements Runnable {
    private static final List<String> SECTION_TYPES = List.of(
        "", "command", "note", "snippet", "ticket", "runbook", "retro", "knowledge", "");
    private static final List<String> SECTION_LABELS = List.of(
        "All", "Commands", "Notes", "Snippets", "Tickets", "Runbooks", "Retros", "Knowledge", "Settings");
    private static final List<String> SETTINGS_KEYS = List.of(
        "llm.provider", "llm.model", "llm.api-key", "llm.api-url");
    private static final List<String> SETTINGS_LABELS = List.of(
        "Provider", "Model", "API Key", "API URL");

    private List<Map<String, String>> allEntries;
    private List<Map<String, String>> filteredEntries;
    private int selectedIndex;
    private int sectionIndex;
    private String filterText;
    private boolean filterMode;
    private int boxWidth;
    private int settingsIndex;
    private boolean editingField;
    private StringBuilder editBuffer;
    private Properties configProps;
    private final Path configFile;

    private final StorageService storageService;

    public BrowseCmd() {
        try {
            this.storageService = new StorageService();
            this.configFile = Paths.get(System.getProperty("user.home"), ".recall", "config.properties");
            this.configProps = new Properties();
            if (Files.exists(configFile)) {
                configProps.load(Files.newBufferedReader(configFile));
            }
            this.settingsIndex = 0;
            this.editingField = false;
            this.editBuffer = new StringBuilder();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public void run() {
        try {
            allEntries = storageService.allEntries();
            if (allEntries.isEmpty()) {
                System.out.println("No entries yet. Use 'recall remember' to store something.");
                return;
            }
            allEntries.sort((a, b) -> b.get("date").compareTo(a.get("date")));
            sectionIndex = 0;
            selectedIndex = 0;
            filterText = "";
            filterMode = false;
            boxWidth = getBoxWidth();
            settingsIndex = 0;
            editingField = false;
            editBuffer = new StringBuilder();
            configProps = new Properties();
            if (Files.exists(configFile)) {
                configProps.load(Files.newBufferedReader(configFile));
            }
            applySection();

            String savedState = terminal("stty -g < /dev/tty");
            terminal("stty raw -echo < /dev/tty");

            try {
                render();
                while (true) {
                    int ch = System.in.read();
                    if (ch == -1) break;

                    if (filterMode) {
                        if (!handleFilterInput(ch)) break;
                    } else {
                        if (!handleNavInput(ch)) break;
                    }
                }
            } finally {
                terminal("stty " + savedState + " < /dev/tty");
                System.out.print("\u001B[2J\u001B[H");
                System.out.flush();
            }
        } catch (Exception e) {
            System.err.println("Browse failed: " + e.getMessage());
        }
    }

    private boolean handleNavInput(int ch) throws Exception {
        if (ch == 'q' || ch == 'Q') return false;

        if (ch == 27) {
            try { Thread.sleep(15); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (System.in.available() >= 2) {
                int bracket = System.in.read();
                if (bracket == 91) {
                    int dir = System.in.read();
                    if (sectionIndex == 7) {
                        if (dir == 65) { if (settingsIndex > 0) { settingsIndex--; render(); } }
                        else if (dir == 66) { if (settingsIndex < SETTINGS_KEYS.size() - 1) { settingsIndex++; render(); } }
                        else if (dir == 67) moveRight();
                        else if (dir == 68) moveLeft();
                    } else {
                        if (dir == 65) moveUp();
                        else if (dir == 66) moveDown();
                        else if (dir == 67) moveRight();
                        else if (dir == 68) moveLeft();
                    }
                }
            } else if (editingField) {
                editingField = false;
                render();
            }
            return true;
        }

        if (editingField) {
            if (ch == '\r' || ch == '\n') {
                saveCurrentSetting();
                editingField = false;
                render();
                return true;
            }
            if (ch == 127 || ch == 8) {
                if (editBuffer.length() > 0) {
                    editBuffer.deleteCharAt(editBuffer.length() - 1);
                    render();
                }
                return true;
            }
            if (ch >= 32 && ch < 127) {
                editBuffer.append((char) ch);
                render();
            }
            return true;
        }

        if (ch == '\r' || ch == '\n') {
            if (sectionIndex == 7) {
                editingField = true;
                editBuffer = new StringBuilder(configProps.getProperty(SETTINGS_KEYS.get(settingsIndex), ""));
                render();
            } else if (!filteredEntries.isEmpty()) {
                showEntry(filteredEntries.get(selectedIndex));
            }
            return true;
        }

        if (ch == '/') {
            if (sectionIndex != 7) {
                filterText = "";
                filterMode = true;
                render();
            }
            return true;
        }

        if (ch >= '1' && ch <= '9') {
            int idx = ch - '1';
            if (idx != sectionIndex) {
                sectionIndex = idx;
                selectedIndex = 0;
                settingsIndex = 0;
                editingField = false;
                applySection();
                render();
            }
            return true;
        }

        return true;
    }

    private boolean handleFilterInput(int ch) throws Exception {
        if (ch == 27) {
            try { Thread.sleep(15); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (System.in.available() >= 2) {
                int bracket = System.in.read();
                if (bracket == 91) {
                    int dir = System.in.read();
                    if (dir == 65) moveUp();
                    else if (dir == 66) moveDown();
                    else if (dir == 67) moveRight();
                    else if (dir == 68) moveLeft();
                }
            } else {
                filterText = "";
                filterMode = false;
                applySection();
                selectedIndex = 0;
                render();
            }
            return true;
        }

        if (ch == '\r' || ch == '\n') {
            filterMode = false;
            render();
            return true;
        }

        if (ch == 127 || ch == 8) {
            if (!filterText.isEmpty()) {
                filterText = filterText.substring(0, filterText.length() - 1);
                applyFilter();
            }
            render();
            return true;
        }

        if (ch >= 32 && ch < 127) {
            filterText += (char) ch;
            applyFilter();
            render();
        }

        return true;
    }

    private void moveUp() {
        if (selectedIndex > 0) {
            selectedIndex--;
            render();
        }
    }

    private void moveDown() {
        if (selectedIndex < filteredEntries.size() - 1) {
            selectedIndex++;
            render();
        }
    }

    private void moveLeft() {
        if (sectionIndex > 0) {
            sectionIndex--;
            selectedIndex = 0;
            applySection();
            render();
        }
    }

    private void moveRight() {
        if (sectionIndex < SECTION_LABELS.size() - 1) {
            sectionIndex++;
            selectedIndex = 0;
            applySection();
            render();
        }
    }

    private void applySection() {
        String type = SECTION_TYPES.get(sectionIndex);
        if (type.isEmpty()) {
            filteredEntries = new ArrayList<>(allEntries);
        } else {
            filteredEntries = allEntries.stream()
                .filter(e -> type.equals(e.get("type")))
                .collect(Collectors.toList());
        }
        if (!filterText.isEmpty()) {
            applyFilter();
        }
    }

    private void applyFilter() {
        if (filterText.isEmpty()) return;
        String lower = filterText.toLowerCase(Locale.ROOT);
        String type = SECTION_TYPES.get(sectionIndex);
        List<Map<String, String>> base;
        if (type.isEmpty()) {
            base = new ArrayList<>(allEntries);
        } else {
            base = allEntries.stream()
                .filter(e -> type.equals(e.get("type")))
                .collect(Collectors.toList());
        }
        filteredEntries = base.stream()
            .filter(e -> e.get("slug").toLowerCase(Locale.ROOT).contains(lower)
                || e.get("content").toLowerCase(Locale.ROOT).contains(lower)
                || e.get("tags").toLowerCase(Locale.ROOT).contains(lower))
            .collect(Collectors.toList());
        if (selectedIndex >= filteredEntries.size()) {
            selectedIndex = Math.max(0, filteredEntries.size() - 1);
        }
    }

    private void showEntry(Map<String, String> entry) throws Exception {
        System.out.print("\u001B[2J\u001B[H");

        String slug = entry.get("slug");
        String date = entry.get("date");
        String type = entry.get("type");
        String tags = entry.get("tags");
        String content = entry.get("content");

        System.out.print(slug + "  " + date + "  " + type + "\r\n");
        if (tags != null && !tags.isBlank()) {
            System.out.print("tags: " + tags + "\r\n");
        }
        System.out.print("\r\n");
        for (int i = 0; i < boxWidth; i++) System.out.print('\u2500');
        System.out.print("\r\n");
        System.out.print(content.replace("\n", "\r\n") + "\r\n");
        System.out.print("\r\n");
        System.out.print("Press any key to return...");
        System.out.flush();

        try { Thread.sleep(30); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        while (System.in.available() > 0) System.in.read();
        System.in.read();

        render();
    }

    private void render() {
        StringBuilder sb = new StringBuilder();
        sb.append("\u001B[H");

        renderTopBorder(sb);

        sb.append('\u2502').append(" > ");
        if (filterMode) {
            sb.append(filterText).append('\u2588');
            repeat(sb, ' ', Math.max(0, boxWidth - 4 - filterText.length() - 1));
        } else {
            String sectionInfo = SECTION_LABELS.get(sectionIndex);
            String hint = sectionIndex == 7 ? " " + sectionInfo : " " + sectionInfo + " (" + filteredEntries.size() + ")";
            sb.append(filterText);
            repeat(sb, ' ', Math.max(0, boxWidth - 4 - filterText.length() - hint.length()));
            sb.append(hint);
        }
        sb.append('\u2502').append("\r\n");

        sb.append('\u251C');
        repeat(sb, '\u2500', boxWidth - 2);
        sb.append('\u2524').append("\r\n");

        if (sectionIndex == 7) {
            renderSettings(sb);
        } else if (filteredEntries.isEmpty()) {
            sb.append('\u2502');
            String msg = " No entries in this section";
            sb.append(msg);
            repeat(sb, ' ', Math.max(0, boxWidth - 2 - msg.length()));
            sb.append('\u2502').append("\r\n");
        } else {
            int listRows = Math.min(filteredEntries.size(), 20);
            int start = Math.max(0,
                Math.min(selectedIndex - listRows / 2, filteredEntries.size() - listRows));

            int contentWidth = boxWidth - 4;
            int typeWidth = 8;
            int dateWidth = 12;

            for (int i = start; i < start + listRows && i < filteredEntries.size(); i++) {
                Map<String, String> entry = filteredEntries.get(i);
                sb.append('\u2502');
                if (i == selectedIndex) sb.append("\u001B[7m");
                sb.append(' ');

                String slug = entry.get("slug");
                String type = entry.get("type");
                String relDate = relativeDate(entry.get("date"));
                int slugWidth = contentWidth - typeWidth - dateWidth - 3;

                String entryLine = String.format("%-" + slugWidth + "s%" + typeWidth + "s \u00B7 %-" + dateWidth + "s",
                    slug, type, relDate);
                sb.append(entryLine);

                if (i == selectedIndex) sb.append("\u001B[0m");
                repeat(sb, ' ', Math.max(0, boxWidth - 2 - 1 - entryLine.length()));
                sb.append('\u2502').append("\r\n");
            }

            int rendered = Math.min(listRows, filteredEntries.size());
            for (int i = rendered; i < 20; i++) {
                sb.append('\u2502');
                repeat(sb, ' ', boxWidth - 2);
                sb.append('\u2502').append("\r\n");
            }
        }

        if (sectionIndex != 7) {
            sb.append('\u2514');
            repeat(sb, '\u2500', boxWidth - 2);
            sb.append('\u2518').append("\r\n");

            sb.append("  \u2190\u2192 sections  \u2191\u2193 navigate  enter view  / filter  q quit");
            sb.append("\r\n");
        }

        System.out.print(sb.toString());
        System.out.flush();
    }

    private void renderTopBorder(StringBuilder sb) {
        boolean useFull = boxWidth >= 90;
        int totalTabWidth = 0;
        for (String l : SECTION_LABELS) {
            String label = useFull ? l : (l.length() > 6 ? l.substring(0, 4) : l);
            totalTabWidth += label.length() + 2;
        }
        int gaps = SECTION_LABELS.size() - 1;
        int fillerTotal = boxWidth - 2 - totalTabWidth;
        int perGap = gaps > 0 ? Math.max(1, fillerTotal / gaps) : 0;
        int extra = gaps > 0 ? fillerTotal - perGap * gaps : 0;

        sb.append('\u250C');
        int used = 1;
        for (int i = 0; i < SECTION_LABELS.size(); i++) {
            String label = useFull ? SECTION_LABELS.get(i) :
                (SECTION_LABELS.get(i).length() > 6 ? SECTION_LABELS.get(i).substring(0, 4) : SECTION_LABELS.get(i));
            String tab = " " + label + " ";

            if (i == sectionIndex) sb.append("\u001B[7m");
            sb.append(tab);
            if (i == sectionIndex) sb.append("\u001B[0m");
            used += tab.length();

            if (i < SECTION_LABELS.size() - 1) {
                int fill = perGap + (i < extra ? 1 : 0);
                for (int f = 0; f < fill; f++) {
                    sb.append('\u2500');
                    used++;
                }
            }
        }
        while (used < boxWidth - 1) {
            sb.append('\u2500');
            used++;
        }
        sb.append('\u2510').append("\r\n");
    }

    private void repeat(StringBuilder sb, char c, int count) {
        for (int i = 0; i < count; i++) sb.append(c);
    }

    private int getBoxWidth() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "stty size < /dev/tty"});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            if (line != null) {
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    int cols = Integer.parseInt(parts[1]);
                    if (cols >= 40) return Math.min(cols, 120);
                }
            }
        } catch (Exception ignored) {
        }
        return 72;
    }

    private String terminal(String cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).trim();
        p.waitFor();
        return out;
    }

    private void renderSettings(StringBuilder sb) {
        int contentWidth = boxWidth - 4;
        int labelWidth = 12;

        for (int i = 0; i < SETTINGS_KEYS.size(); i++) {
            sb.append('\u2502');
            boolean selected = !editingField && i == settingsIndex;
            if (selected) sb.append("\u001B[7m");
            sb.append(' ');

            String label = SETTINGS_LABELS.get(i);
            String value = configProps.getProperty(SETTINGS_KEYS.get(i), "");
            if (SETTINGS_KEYS.get(i).equals("llm.api-key") && !editingField) {
                value = maskApiKey(value);
            }

            String line = String.format("%-" + labelWidth + "s %s", label, value);
            if (line.length() > contentWidth) {
                line = line.substring(0, contentWidth - 3) + "...";
            }
            sb.append(line);
            repeat(sb, ' ', Math.max(0, contentWidth - line.length()));

            if (selected) sb.append("\u001B[0m");
            sb.append('\u2502').append("\r\n");
        }

        for (int i = SETTINGS_KEYS.size(); i < 20; i++) {
            sb.append('\u2502');
            repeat(sb, ' ', boxWidth - 2);
            sb.append('\u2502').append("\r\n");
        }

        sb.append('\u2514');
        repeat(sb, '\u2500', boxWidth - 2);
        sb.append('\u2518').append("\r\n");

        if (editingField) {
            String fieldLabel = SETTINGS_LABELS.get(settingsIndex);
            sb.append("  Editing ").append(fieldLabel).append(": ");
            sb.append(editBuffer.toString()).append('\u2588');
            sb.append("  \u23CE save  esc cancel");
        } else {
            sb.append("  \u2190\u2192 sections  \u2191\u2193 select field  \u23CE edit  q quit");
        }
        sb.append("\r\n");
    }

    private void saveCurrentSetting() {
        String key = SETTINGS_KEYS.get(settingsIndex);
        String value = editBuffer.toString();
        configProps.setProperty(key, value);
        try {
            if (!Files.exists(configFile.getParent())) {
                Files.createDirectories(configFile.getParent());
            }
            configProps.store(Files.newBufferedWriter(configFile), "recall config");
        } catch (IOException e) {
            // silent
        }
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() < 8) return "********";
        return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
    }

    static String relativeDate(String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        LocalDate today = LocalDate.now();
        long days = ChronoUnit.DAYS.between(date, today);
        if (days == 0) return "today";
        if (days == 1) return "yesterday";
        if (days < 7) return days + " days ago";
        return date.format(DateTimeFormatter.ofPattern("MMM dd"));
    }
}
