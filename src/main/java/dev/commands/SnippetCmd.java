package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Command(name = "snippet",
    subcommands = {
        SnippetCmd.Save.class,
        SnippetCmd.Get.class
    },
    description = "Manage code snippets")
public class SnippetCmd implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "save", description = "Save a code snippet from a file")
    static class Save implements Runnable {
        @Parameters(index = "0", description = "Name of the snippet")
        private String name;

        @Option(names = {"--file"}, description = "Source file path", required = true)
        private String filePath;

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
                Path source = Paths.get(filePath);
                if (!Files.exists(source)) {
                    System.err.println("File not found: " + filePath);
                    return;
                }
                String content = Files.readString(source);
                String ext = filePath.contains(".") ? filePath.substring(filePath.lastIndexOf('.') + 1) : "txt";
                String slug = StorageService.makeSlug(name);

                String filename = storageService.append(
                    "snippets.md", slug, "snippet", ext,
                    "```" + ext + "\n" + content + "\n```");

                String msg = CommandLine.Help.Ansi.AUTO.string(
                    "@|green \u2713 Saved snippet  [|@@|bold,yellow " + slug + "|@@|green ]|@  " + filename);
                System.out.println(msg);
            } catch (IOException e) {
                System.err.println("Failed to save snippet: " + e.getMessage());
            }
        }
    }

    @Command(name = "get", description = "Get a saved code snippet")
    static class Get implements Runnable {
        @Parameters(index = "0", description = "Name of the snippet")
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
                Path snippetsFile = Paths.get(System.getProperty("user.home"), ".recall", "snippets.md");
                if (!Files.exists(snippetsFile)) {
                    System.err.println("No snippets saved yet");
                    return;
                }

                List<Map<String, String>> snippets = storageService.parseBlocks(snippetsFile);
                String slug = StorageService.makeSlug(name);

                for (Map<String, String> snippet : snippets) {
                    if (snippet.get("slug").equals(slug)) {
                        String content = snippet.get("content");
                        String type = snippet.get("tags");
                        if (!type.isBlank()) {
                            System.out.println("```" + type);
                        }
                        System.out.println(content.replaceAll("^```[a-z]*\\n|\\n```$", ""));
                        if (!type.isBlank()) {
                            System.out.println("```");
                        }
                        return;
                    }
                }

                System.err.println("Snippet not found: " + slug);
            } catch (IOException e) {
                System.err.println("Failed to retrieve snippet: " + e.getMessage());
            }
        }
    }
}