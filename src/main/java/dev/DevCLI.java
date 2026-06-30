package dev;

import dev.commands.*;
import dev.recall.commands.BrowseCmd;
import dev.recall.commands.ImportCmd;
import dev.recall.commands.RecentCmd;
import dev.recall.commands.ShowCmd;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.UnmatchedArgumentException;

import java.util.List;

@Command(name = "recall",
    version = "1.0.0",
    description = "Personal engineering memory manager",
    mixinStandardHelpOptions = true,
    subcommands = {
        RememberCmd.class,
        SearchCmd.class,
        ScriptCmd.class,
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
        OnboardCmd.class,
        ImportCmd.class,
        IngestCmd.class,
        KnowledgeCmd.class,
        RecentCmd.class,
        BrowseCmd.class,
        ShowCmd.class,
        EditCmd.class,
        CleanCmd.class
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
        cmd.setExecutionStrategy(parseResult -> {
            CommandLine.ParseResult deepest = parseResult;
            while (deepest.subcommand() != null) {
                deepest = deepest.subcommand();
            }
            String name = deepest.commandSpec().name();
            if (!"recall".equals(name) && !FeatureFlags.isEnabled(name)) {
                System.err.println(Help.Ansi.AUTO.string(
                    "@|red Command '" + name + "' is disabled.|@ " +
                    "@|yellow Enable it in FeatureFlags.java|@"));
                return 0;
            }
            return new CommandLine.RunLast().execute(parseResult);
        });
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}