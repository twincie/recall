package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Scanner;

@Command(name = "sync", description = "Sync ~/.recall/ to a git remote")
public class SyncCmd implements Runnable {
    @Option(names = {"--setup"}, description = "Initialize and set remote (git@github.com:user/repo.git)")
    private String remote;

    private final StorageService storageService;

    public SyncCmd() {
        try {
            this.storageService = new StorageService();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            Path dir = storageService.getDataDir();

            if (remote != null && !remote.isBlank()) {
                String init = exec("cd " + dir + " && git init && git add . && git commit -m \"recall: init\"");
                String rem = exec("cd " + dir + " && git remote add origin " + remote);
                String push = exec("cd " + dir + " && git push -u origin main 2>&1 || git push -u origin master 2>&1");
                System.out.println("Setup complete. Remote: " + remote);
                if (push != null && !push.isBlank()) System.out.println(push);
                return;
            }

            String result = exec("cd " + dir + " && git push 2>&1");
            if (result == null || result.contains("fatal: not a git repository")) {
                System.out.println("Not a git repo. Use: recall sync --setup <remote>");
            } else {
                System.out.println("Pushed.");
                if (!result.isBlank()) System.out.println(result);
            }
        } catch (Exception e) {
            System.err.println("Sync failed: " + e.getMessage());
        }
    }

    private String exec(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"bash", "-c", cmd});
            try (Scanner s = new Scanner(p.getInputStream()).useDelimiter("\\A")) {
                String out = s.hasNext() ? s.next().strip() : null;
                p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                return out;
            }
        } catch (Exception e) {
            return null;
        }
    }
}