package dev.commands;

import dev.DevCLI;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "generate-completion", description = "Generate shell completion script")
public class GenerateCompletionCmd implements Runnable {
    @Override
    public void run() {
        String script = AutoComplete.bash("recall", new CommandLine(new DevCLI()));
        System.out.println(script);
    }
}