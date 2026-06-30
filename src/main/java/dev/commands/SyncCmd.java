package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Command(name = "sync", aliases = {"-sy", "--sync"}, mixinStandardHelpOptions = true, description = "Sync ~/.recall/ to a git remote")
public class SyncCmd implements Runnable {
    private static final Path CONFIG_FILE = Paths.get(System.getProperty("user.home"), ".recall", "config.properties");

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
            Properties config = loadConfig();
            String remote = config.getProperty("sync.remote", "");
            String branch = config.getProperty("sync.branch", "");

            if (remote.isBlank()) {
                System.err.println("\u2717 No sync remote configured. Run: recall config --set sync.remote=<git-url>");
                return;
            }

            Path dir = storageService.getDataDir();
            String branchFlag = branch.isBlank() ? "" : " origin " + branch;

            String pullCmd = "cd " + dir + " && git pull" + branchFlag + " 2>&1";
            Process pullP = Runtime.getRuntime().exec(new String[]{"bash", "-c", pullCmd});
            int pullExit = pullP.waitFor();
            String pullOut = new String(pullP.getInputStream().readAllBytes()).trim();
            String pullErr = new String(pullP.getErrorStream().readAllBytes()).trim();

            if (pullExit != 0) {
                String msg = pullErr.isEmpty() ? pullOut : pullErr;
                System.err.println(CommandLine.Help.Ansi.AUTO.string(
                    "@|red \u2717 Pull failed: " + msg + "|@"));
                return;
            }
            System.out.println("\u2713 Pulled latest" + (branch.isBlank() ? "" : " (" + branch + ")"));

            String pushCmd = "cd " + dir + " && git add . && git commit --allow-empty -m \"recall: manual sync\" && git push" + branchFlag;

            Process p = Runtime.getRuntime().exec(new String[]{"bash", "-c", pushCmd});
            int exit = p.waitFor();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            String err = new String(p.getErrorStream().readAllBytes()).trim();

            if (exit == 0) {
                System.out.println(CommandLine.Help.Ansi.AUTO.string(
                    "@|green \u2713 Synced to " + remote + "|@"));
                if (!out.isBlank()) System.out.println(out);
            } else {
                System.err.println(CommandLine.Help.Ansi.AUTO.string(
                    "@|red \u2717 Sync failed: " + (err.isEmpty() ? "exit code " + exit : err) + "|@"));
            }
        } catch (Exception e) {
            System.err.println(CommandLine.Help.Ansi.AUTO.string(
                "@|red \u2717 Sync failed: " + e.getMessage() + "|@"));
        }
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
}
