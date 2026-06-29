package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Command(name = "script",
    aliases = {"-sc", "--script"},
    mixinStandardHelpOptions = true,
    subcommands = {
        ScriptCmd.Save.class,
        ScriptCmd.Get.class,
        ScriptCmd.Run.class,
        ScriptCmd.ListScripts.class,
        ScriptCmd.Edit.class
    },
    description = "Manage scripts (.sh / .bat)")
public class ScriptCmd implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    // ── Save ────────────────────────────────────────────────────────────

    @Command(name = "save", description = "Save a script from a file, stdin, or editor")
    static class Save implements Runnable {
        @Parameters(index = "0", description = "Name of the script")
        private String name;

        @Option(names = {"--file", "-f"}, description = "Source file path")
        private String filePath;

        @Option(names = {"--edit", "-e"}, description = "Open $EDITOR to compose the script")
        private boolean edit;

        @Option(names = {"--confirm", "-c"}, description = "Skip confirmation prompt")
        private boolean confirm;

        private final StorageService storageService;

        Save() {
            try {
                this.storageService = new StorageService();
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize storage", e);
            }
        }

        @Override
        public void run() {
            try {
                String content = readContent();
                if (content.isBlank()) {
                    System.err.println("No content provided.");
                    return;
                }

                String ext = "sh";
                if (filePath != null) {
                    ext = filePath.contains(".") ? filePath.substring(filePath.lastIndexOf('.') + 1) : "sh";
                }

                String slug = StorageService.makeSlug(name);
                Map<String, String> existing = storageService.findBySlug(slug);
                if (existing != null && "scripts.md".equals(existing.get("file"))) {
                    String deduped = slug;
                    int counter = 2;
                    while (storageService.findBySlug(deduped) != null) {
                        deduped = slug + "-" + counter++;
                    }

                    if (!confirm && System.console() != null) {
                        System.out.print("Script '" + slug + "' already exists. [S]ave as '" + deduped + "', [U]pdate existing, or [C]ancel? [S/u/c]: ");
                        String input = new BufferedReader(new InputStreamReader(System.in)).readLine();
                        if (input != null) {
                            input = input.trim().toLowerCase();
                            if (input.equals("u") || input.equals("update")) {
                                storageService.updateEntry("scripts.md", slug, content);
                                String msg = CommandLine.Help.Ansi.AUTO.string(
                                    "@|green \u2713 Updated script  [|@@|bold,yellow " + slug + "|@@|green ]|@");
                                System.out.println(msg);
                                return;
                            }
                            if (!input.isBlank() && !input.equals("s") && !input.equals("save") && !input.equals("y") && !input.equals("yes")) {
                                System.out.println("Cancelled.");
                                return;
                            }
                        }
                    }
                    slug = deduped;
                }

                String filename = storageService.appendWithExt(
                    "scripts.md", slug, "script", "", ext, content);

                String msg = CommandLine.Help.Ansi.AUTO.string(
                    "@|green \u2713 Saved script  [|@@|bold,yellow " + slug + "|@@|green ]|@  " + filename);
                System.out.println(msg);

            } catch (IOException e) {
                System.err.println("Failed to save script: " + e.getMessage());
            }
        }

        private String readContent() throws IOException {
            int count = 0;
            if (filePath != null) count++;
            if (edit) count++;

            if (count > 1) {
                System.err.println("Use only one input source: --file, --edit, or stdin.");
                return "";
            }

            if (filePath != null) {
                Path source = Path.of(filePath);
                if (!Files.exists(source)) {
                    System.err.println("File not found: " + filePath);
                    return "";
                }
                return Files.readString(source).trim();
            }

            if (edit) {
                Path tmp = Files.createTempFile("recall-script-", ".sh");
                String editor = System.getenv("EDITOR");
                if (editor == null || editor.isBlank()) editor = "vim";
                try {
                    new ProcessBuilder("bash", "-c", editor + " " + tmp.toAbsolutePath())
                        .inheritIO().start().waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                String content = Files.readString(tmp).trim();
                Files.deleteIfExists(tmp);
                return content;
            }

            if (System.console() == null) {
                return new String(System.in.readAllBytes()).trim();
            }

            return "";
        }
    }

    // ── Get ─────────────────────────────────────────────────────────────

    @Command(name = "get", description = "Print a saved script to stdout")
    static class Get implements Runnable {
        @Parameters(index = "0", description = "Name of the script")
        private String name;

        private final StorageService storageService;

        Get() {
            try {
                this.storageService = new StorageService();
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize storage", e);
            }
        }

        @Override
        public void run() {
            try {
                Path scriptsFile = storageService.getDataDir().resolve("scripts.md");
                if (!Files.exists(scriptsFile)) {
                    System.err.println("No scripts saved yet");
                    return;
                }

                List<Map<String, String>> scripts = storageService.parseBlocks(scriptsFile);
                String slug = StorageService.makeSlug(name);

                for (Map<String, String> script : scripts) {
                    if (script.get("slug").equals(slug)) {
                        System.out.println(script.get("content"));
                        return;
                    }
                }

                System.err.println("Script not found: " + slug);
            } catch (IOException e) {
                System.err.println("Failed to retrieve script: " + e.getMessage());
            }
        }
    }

    // ── List ────────────────────────────────────────────────────────────

    @Command(name = "list", aliases = {"ls"}, mixinStandardHelpOptions = true, description = "List saved scripts")
    static class ListScripts implements Runnable {
        @Option(names = {"--limit", "-l"}, description = "Max results (0 for all)")
        private int limit = 20;

        private final StorageService storageService;

        ListScripts() {
            try {
                this.storageService = new StorageService();
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize storage", e);
            }
        }

        @Override
        public void run() {
            try {
                Path scriptsFile = storageService.getDataDir().resolve("scripts.md");
                if (!Files.exists(scriptsFile)) {
                    System.out.println("No scripts saved yet.");
                    return;
                }

                List<Map<String, String>> scripts = storageService.parseBlocks(scriptsFile);
                scripts.sort((a, b) -> b.get("date").compareTo(a.get("date")));

                if (limit > 0 && scripts.size() > limit) {
                    scripts = scripts.subList(0, limit);
                }

                if (scripts.isEmpty()) {
                    System.out.println("No scripts saved yet.");
                    return;
                }

                for (Map<String, String> s : scripts) {
                    String ext = s.get("ext");
                    String label = (ext == null || ext.isBlank()) ? "" : "  ." + ext;
                    System.out.println(CommandLine.Help.Ansi.AUTO.string(
                        String.format("  @|bold,green %s|@@|cyan %s|@  @|cyan %s|@",
                            s.get("slug"), label, s.get("date"))));
                }
            } catch (IOException e) {
                System.err.println("Failed to list scripts: " + e.getMessage());
            }
        }
    }

    // ── Edit ────────────────────────────────────────────────────────────

    @Command(name = "edit", description = "Edit a saved script")
    static class Edit implements Runnable {
        @Parameters(index = "0", description = "Name of the script")
        private String name;

        private final StorageService storageService;

        Edit() {
            try {
                this.storageService = new StorageService();
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize storage", e);
            }
        }

        @Override
        public void run() {
            try {
                Path scriptsFile = storageService.getDataDir().resolve("scripts.md");
                if (!Files.exists(scriptsFile)) {
                    System.err.println("No scripts saved yet");
                    return;
                }

                List<Map<String, String>> scripts = storageService.parseBlocks(scriptsFile);
                String slug = StorageService.makeSlug(name);

                Map<String, String> target = null;
                for (Map<String, String> s : scripts) {
                    if (s.get("slug").equals(slug)) {
                        target = s;
                        break;
                    }
                }

                if (target == null) {
                    System.err.println("Script not found: " + slug);
                    return;
                }

                String content = target.get("content");
                String ext = target.get("ext");
                if (ext == null || ext.isBlank()) ext = "sh";

                Path tmp = Files.createTempFile("recall-script-edit-", "." + ext);
                Files.writeString(tmp, content);
                String editor = System.getenv("EDITOR");
                if (editor == null || editor.isBlank()) editor = "vim";

                Process p = new ProcessBuilder("bash", "-c", editor + " " + tmp.toAbsolutePath())
                    .inheritIO().start();
                p.waitFor();

                String newContent = Files.readString(tmp).trim();
                Files.deleteIfExists(tmp);

                if (newContent.equals(content.trim())) {
                    System.out.println("No changes made.");
                    return;
                }

                boolean updated = storageService.updateEntry("scripts.md", slug, newContent);
                if (updated) {
                    String msg = CommandLine.Help.Ansi.AUTO.string(
                        "@|green \u2713 Updated script  [|@@|bold,yellow " + slug + "|@@|green ]|@");
                    System.out.println(msg);
                }

            } catch (IOException | InterruptedException e) {
                System.err.println("Failed to edit script: " + e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    // ── Run ─────────────────────────────────────────────────────────────

    @Command(name = "run", description = "Run a saved script")
    static class Run implements Runnable {
        @Parameters(index = "0", description = "Name of the script")
        private String name;

        @Parameters(index = "1..*", arity = "0..*", description = "Arguments to pass to the script")
        private String[] scriptArgs;

        @Option(names = {"--confirm", "-c"}, description = "Skip confirmation prompt")
        private boolean confirm;

        private final StorageService storageService;

        Run() {
            try {
                this.storageService = new StorageService();
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize storage", e);
            }
        }

        @Override
        public void run() {
            try {
                Path scriptsFile = storageService.getDataDir().resolve("scripts.md");
                if (!Files.exists(scriptsFile)) {
                    System.err.println("No scripts saved yet");
                    return;
                }

                List<Map<String, String>> scripts = storageService.parseBlocks(scriptsFile);
                String slug = StorageService.makeSlug(name);

                Map<String, String> target = null;
                for (Map<String, String> s : scripts) {
                    if (s.get("slug").equals(slug)) {
                        target = s;
                        break;
                    }
                }

                if (target == null) {
                    System.err.println("Script not found: " + slug);
                    return;
                }

                String content = target.get("content");
                String ext = target.get("ext");
                if (ext == null || ext.isBlank()) ext = "sh";

                if (!confirm && System.console() != null) {
                    System.out.println("─── Script: " + slug + " ───");
                    System.out.println(content);
                    System.out.println("─────────────────────────");
                    System.out.print("Run? [Y/n]: ");
                    String input = new BufferedReader(new InputStreamReader(System.in)).readLine();
                    if (input != null) {
                        input = input.trim().toLowerCase();
                        if (!input.isBlank() && !input.equals("y") && !input.equals("yes")) {
                            System.out.println("Cancelled.");
                            return;
                        }
                    }
                }

                runScript(content, ext);

            } catch (IOException e) {
                System.err.println("Failed to run script: " + e.getMessage());
            }
        }

        private void runScript(String content, String ext) throws IOException {
            boolean isSh = ext.equalsIgnoreCase("sh");
            boolean isBat = ext.equalsIgnoreCase("bat");

            String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);

            if (isBat && !os.contains("windows")) {
                System.err.println("\u2717 Cannot run .bat scripts on this OS");
                return;
            }
            if (isSh && os.contains("windows")) {
                System.err.println("\u2717 Cannot run .sh scripts on this OS");
                return;
            }

            Path tempDir = Files.createTempDirectory("recall-script-");
            Path scriptFile = tempDir.resolve("script." + ext);
            Files.writeString(scriptFile, content);

            if (isSh) {
                scriptFile.toFile().setExecutable(true);
            }

            ProcessBuilder pb;
            if (isSh) {
                List<String> cmd = new java.util.ArrayList<>();
                cmd.add("sh");
                cmd.add(scriptFile.toString());
                if (scriptArgs != null) cmd.addAll(java.util.Arrays.asList(scriptArgs));
                pb = new ProcessBuilder(cmd);
            } else {
                List<String> cmd = new java.util.ArrayList<>();
                cmd.add("cmd");
                cmd.add("/c");
                cmd.add(scriptFile.toString());
                if (scriptArgs != null) cmd.addAll(java.util.Arrays.asList(scriptArgs));
                pb = new ProcessBuilder(cmd);
            }
            pb.inheritIO();

            try {
                Process p = pb.start();
                int exit = p.waitFor();
                System.out.println("Exit code: " + exit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Script execution interrupted");
            } finally {
                try {
                    Files.deleteIfExists(scriptFile);
                    Files.deleteIfExists(tempDir);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
