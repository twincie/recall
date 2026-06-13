package dev.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Command(name = "config", description = "View or set configuration")
public class ConfigCmd implements Runnable {
    @Option(names = {"--set"}, description = "Set a config key=value (e.g. storage.path=/path)")
    private String set;

    @Override
    public void run() {
        try {
            Path configFile = Paths.get(System.getProperty("user.home"), ".recall", "config.properties");
            if (set != null && !set.isBlank()) {
                int eq = set.indexOf('=');
                if (eq < 1) {
                    System.err.println("Usage: --set key=value");
                    return;
                }
                String key = set.substring(0, eq).trim();
                String value = set.substring(eq + 1).trim();

                Properties props = new Properties();
                if (Files.exists(configFile)) {
                    props.load(Files.newBufferedReader(configFile));
                }
                props.setProperty(key, value);

                if (!Files.exists(configFile.getParent())) {
                    Files.createDirectories(configFile.getParent());
                }
                props.store(Files.newBufferedWriter(configFile), "recall config");
                System.out.println("Set " + key + " = " + value);
            } else {
                if (!Files.exists(configFile)) {
                    System.out.println("No config set. Use --set key=value");
                    return;
                }
                Properties props = new Properties();
                props.load(Files.newBufferedReader(configFile));
                props.forEach((k, v) -> System.out.println(k + " = " + v));
            }
        } catch (IOException e) {
            System.err.println("Config failed: " + e.getMessage());
        }
    }
}