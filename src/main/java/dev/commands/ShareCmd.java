package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.Map;
import java.util.Scanner;

@Command(name = "share", description = "Share a note or snippet by slug")
public class ShareCmd implements Runnable {
    @Parameters(description = "Slug of the entry to share", arity = "1")
    private String name;

    private final StorageService storageService;

    public ShareCmd() {
        try {
            this.storageService = new StorageService();
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
                System.err.println("Not found: " + slug);
                return;
            }

            String markdown = String.format("## %s\n**tags:** %s  \n**type:** %s  \n**date:** %s\n\n%s\n",
                entry.get("slug"), entry.get("tags"), entry.get("type"), entry.get("date"), entry.get("content"));

            String gistUrl = createGist(entry.get("slug") + ".md", markdown);
            if (gistUrl != null) {
                System.out.println("Shared: " + gistUrl);
            } else {
                System.out.println("---");
                System.out.println(markdown);
                System.out.println("---");
                System.out.println("Install `gh` CLI and authenticate to share as a gist.");
            }
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }

    private String createGist(String filename, String content) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"bash", "-c",
                "echo " + escape(content) + " | gh gist create --filename \"" + filename + "\" --public -"});
            try (Scanner s = new Scanner(p.getInputStream()).useDelimiter("\\A")) {
                String url = s.hasNext() ? s.next().strip() : null;
                p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                return (url != null && url.startsWith("https")) ? url : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String escape(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}