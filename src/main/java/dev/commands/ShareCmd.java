package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

@Command(name = "share", aliases = {"-sh", "--share"}, mixinStandardHelpOptions = true, description = "Share an entry by slug")
public class ShareCmd implements Runnable {
    @Parameters(description = "Slug of the entry to share")
    private String name;

    @Option(names = {"--clip", "-c"}, description = "Copy to clipboard")
    private boolean clip;

    @Option(names = {"--gist", "-g"}, description = "Create a GitHub Gist")
    private boolean gist;

    @Option(names = {"--private", "-p"}, description = "Create a private Gist (requires --gist)")
    private boolean priv;

    @Option(names = {"--output", "-o"}, description = "Save to file")
    private String output;

    private final StorageService storageService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ShareCmd() {
        try {
            this.storageService = new StorageService();
            this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
            this.objectMapper = new ObjectMapper();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            String slug = StorageService.makeSlug(name);
            Map<String, String> entry = storageService.findBySlug(slug);

            if (entry == null) {
                System.err.println(CommandLine.Help.Ansi.AUTO.string(
                    "@|red Entry not found: " + slug + "|@"));
                return;
            }

            String markdown = String.format("## %s\n**tags:** %s  \n**type:** %s  \n**date:** %s\n\n%s\n",
                entry.get("slug"), entry.get("tags"), entry.get("type"), entry.get("date"), entry.get("content"));

            if (gist) {
                shareAsGist(markdown, slug + ".md");
            } else if (clip) {
                copyToClipboard(markdown);
            } else if (output != null) {
                Files.writeString(Path.of(output), markdown);
                System.out.println("Saved to " + output);
            } else {
                System.out.println(markdown);
            }
        } catch (Exception e) {
            System.err.println(CommandLine.Help.Ansi.AUTO.string(
                "@|red " + e.getMessage() + "|@"));
        }
    }

    private void shareAsGist(String content, String filename) {
        try {
            String token = System.getenv("GITHUB_TOKEN");
            if (token == null || token.isBlank()) {
                if (System.console() == null) {
                    System.err.println("No GITHUB_TOKEN set and no terminal to prompt. Set GITHUB_TOKEN env var.");
                    return;
                }
                char[] tokenChars = System.console().readPassword("GitHub token: ");
                token = new String(tokenChars);
            }

            Map<String, Object> files = Map.of(filename, Map.of("content", content));
            Map<String, Object> body = Map.of("public", !priv, "files", files);
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/gists"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                String url = (String) result.get("html_url");
                if (url != null) {
                    System.out.println("Shared: " + url);
                } else {
                    System.err.println(CommandLine.Help.Ansi.AUTO.string(
                        "@|red Failed to parse Gist response|@"));
                }
            } else {
                String msg = "Gist creation failed (HTTP " + response.statusCode() + ")";
                try {
                    Map<String, Object> err = objectMapper.readValue(response.body(), Map.class);
                    Object errMsg = err.get("message");
                    if (errMsg != null) msg += ": " + errMsg;
                } catch (Exception ignored) {
                }
                System.err.println(CommandLine.Help.Ansi.AUTO.string("@|red " + msg + "|@"));
            }
        } catch (Exception e) {
            System.err.println(CommandLine.Help.Ansi.AUTO.string(
                "@|red " + e.getMessage() + "|@"));
        }
    }

    private void copyToClipboard(String content) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] cmd;
            if (os.contains("mac")) {
                cmd = new String[]{"pbcopy"};
            } else if (os.contains("linux")) {
                cmd = new String[]{"xclip", "-selection", "clipboard"};
            } else {
                System.err.println("Clipboard not supported on this OS");
                return;
            }
            Process p = Runtime.getRuntime().exec(cmd);
            p.getOutputStream().write(content.getBytes());
            p.getOutputStream().close();
            p.waitFor();
            System.out.println("Copied to clipboard");
        } catch (Exception e) {
            System.err.println("Failed to copy: " + e.getMessage());
        }
    }
}
