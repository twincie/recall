package dev;

import dev.commands.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.UnmatchedArgumentException;

import java.util.List;

@Command(name = "recall",
    version = "1.0",
    description = "Personal engineering memory manager",
    mixinStandardHelpOptions = true,
    subcommands = {
        RememberCmd.class,
        SearchCmd.class,
        SnippetCmd.class,
        ListCmd.class,
        CommandCmd.class,
        ExplainCmd.class,
        TicketCmd.class,
        AskCmd.class,
        TodayCmd.class,
        ConfigCmd.class,
        GenerateCompletionCmd.class,
        SimilarCmd.class,
        SyncCmd.class,
        ShareCmd.class,
        StandupCmd.class,
        ReviewCmd.class,
        RetroCmd.class,
        RunbookCmd.class,
        OnboardCmd.class
    }
)
public class DevCLI implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new DevCLI());
        cmd.getSubcommands().get("remember").getCommandSpec().parser()
            .unmatchedOptionsArePositionalParams(true);
        cmd.setParameterExceptionHandler((ex, a) -> {
            if (ex instanceof UnmatchedArgumentException) {
                List<String> suggestions = ((UnmatchedArgumentException) ex).getSuggestions();
                if (!suggestions.isEmpty()) {
                    String[] corrected = new String[a.length];
                    corrected[0] = suggestions.get(0);
                    System.arraycopy(a, 1, corrected, 1, a.length - 1);
                    return new CommandLine(new DevCLI()).execute(corrected);
                }
            }
            System.err.println(ex.getMessage());
            return 2;
        });
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}