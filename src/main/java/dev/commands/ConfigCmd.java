package dev.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Command(name = "config", aliases = {"-cf", "--config"}, mixinStandardHelpOptions = true, description = "View or set configuration")
public class ConfigCmd implements Runnable {
    @Option(names = {"--set"}, arity = "1..*", description = "Set config key=value (can be used multiple times)")
    private String[] set;

    @Option(names = {"--get"}, description = "Get a config value by key")
    private String get;

    @Option(names = {"--delete"}, description = "Delete a config key")
    private String delete;

    @Option(names = {"--list"}, description = "List all config keys and values")
    private boolean list;

    @Option(names = {"--edit", "-e"}, description = "Open config file in $EDITOR")
    private boolean edit;

    @Override
    public void run() {
        try {
            Path configFile = Paths.get(System.getProperty("user.home"), ".recall", "config.properties");

            if (set != null && set.length > 0) {
                for (String kv : set) {
                    handleSet(configFile, kv.trim());
                }
                return;
            }

            if (get != null && !get.isBlank()) {
                handleGet(configFile, get.trim());
                return;
            }

            if (delete != null && !delete.isBlank()) {
                handleDelete(configFile, delete.trim());
                return;
            }

            if (edit) {
                handleEdit(configFile);
                return;
            }

            handleList(configFile);
        } catch (Exception e) {
            System.err.println("Config failed: " + e.getMessage());
        }
    }

    private Properties loadConfig(Path configFile) throws IOException {
        Properties props = new Properties();
        if (Files.exists(configFile)) {
            props.load(Files.newBufferedReader(configFile));
        }
        return props;
    }

    private void saveConfig(Path configFile, Properties props) throws IOException {
        if (!Files.exists(configFile.getParent())) {
            Files.createDirectories(configFile.getParent());
        }
        props.store(Files.newBufferedWriter(configFile), "recall config");
    }

    private void handleSet(Path configFile, String kv) throws IOException {
        int eq = kv.indexOf('=');
        if (eq < 1) {
            System.err.println("Usage: --set key=value");
            return;
        }
        String key = kv.substring(0, eq).trim();
        String value = kv.substring(eq + 1).trim();

        Properties props = loadConfig(configFile);
        props.setProperty(key, value);
        saveConfig(configFile, props);
        System.out.println("Set " + key + " = " + value);
    }

    private void handleGet(Path configFile, String key) throws IOException {
        Properties props = loadConfig(configFile);
        String value = props.getProperty(key);
        if (value != null) {
            System.out.println(key + " = " + value);
        } else {
            System.out.println(key + " not set");
        }
    }

    private void handleDelete(Path configFile, String key) throws IOException {
        Properties props = loadConfig(configFile);
        if (props.remove(key) != null) {
            saveConfig(configFile, props);
            System.out.println("Deleted " + key);
        } else {
            System.out.println(key + " not set");
        }
    }

    private void handleList(Path configFile) throws IOException {
        Properties props = loadConfig(configFile);
        if (props.isEmpty()) {
            System.out.println("No config set. Use --set key=value");
            return;
        }
        for (String key : props.stringPropertyNames()) {
            System.out.println(key + " = " + props.getProperty(key));
        }
    }

    private void handleEdit(Path configFile) throws IOException {
        if (!Files.exists(configFile)) {
            if (!Files.exists(configFile.getParent())) {
                Files.createDirectories(configFile.getParent());
            }
            Files.createFile(configFile);
        }
        String editor = System.getenv("EDITOR");
        if (editor == null || editor.isBlank()) editor = "vim";
        try {
            new ProcessBuilder("bash", "-c", editor + " " + configFile.toAbsolutePath())
                .inheritIO().start().waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
