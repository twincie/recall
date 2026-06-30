package dev.recall.commands;

import dev.recall.ImportService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.FileNotFoundException;
import java.util.concurrent.Callable;

@Command(name = "import",
    aliases = {"-i", "--import"},
    mixinStandardHelpOptions = true,
    description = "Import notes, commands, or snippets from external sources")
public class ImportCmd implements Callable<Integer> {
    @Parameters(index = "0", description = "File to import", arity = "0..1")
    private String file;

    @Option(names = {"--history"}, description = "Import commands from shell history")
    private boolean history;

    @Option(names = {"--vscode"}, description = "Import snippets from VS Code")
    private boolean vscode;

    @Option(names = {"--dir"}, description = "Import all files from a directory")
    private String dir;

    @Option(names = {"--limit"}, description = "Maximum number of entries to import")
    private Integer limit;

    @Option(names = {"--type"}, description = "Target type: note, command, snippet, ticket, retro, runbook, knowledge")
    private String type;

    @Option(names = {"--file"}, description = "Target file (e.g. notes.md, commands.md)")
    private String targetFile;

    @Option(names = {"--confirm"}, description = "Prompt before importing each entry")
    private boolean confirm;

    @Option(names = {"--dry-run"}, description = "Show what would be imported without writing")
    private boolean dryRun;

    @Override
    public Integer call() {
        try {
            int modes = 0;
            if (file != null) modes++;
            if (history) modes++;
            if (vscode) modes++;
            if (dir != null) modes++;

            if (modes == 0) {
                System.err.println(CommandLine.Help.Ansi.AUTO.string(
                    "@|red Specify a file, --history, --vscode, or --dir|@"));
                return 1;
            }
            if (modes > 1) {
                System.err.println(CommandLine.Help.Ansi.AUTO.string(
                    "@|red Specify only one of: file, --history, --vscode, --dir|@"));
                return 1;
            }

            ImportService service = new ImportService();
            if (limit != null && limit > 0) {
                service.setLimit(limit);
            }
            if (type != null) {
                service.setTargetType(type);
            }
            if (targetFile != null) {
                service.setTargetFile(targetFile);
            }
            service.setConfirm(confirm);
            service.setDryRun(dryRun);

            if (file != null) {
                service.importFile(file);
            } else if (history) {
                service.importHistory();
            } else if (vscode) {
                service.importVSCode();
            } else if (dir != null) {
                service.importDir(dir);
            }

            if (service.getTotalImported() > 0) {
                service.printStats();
                System.out.println("\nRun: recall list");
            } else if (!dryRun) {
                System.out.println("Nothing imported.");
            }

            return 0;

        } catch (FileNotFoundException e) {
            System.err.println(CommandLine.Help.Ansi.AUTO.string(
                "@|red " + e.getMessage() + "|@"));
            return 1;
        } catch (IllegalArgumentException e) {
            System.err.println(CommandLine.Help.Ansi.AUTO.string(
                "@|red " + e.getMessage() + "|@"));
            return 1;
        } catch (UnsupportedOperationException e) {
            System.err.println(CommandLine.Help.Ansi.AUTO.string(
                "@|red " + e.getMessage() + "|@"));
            return 1;
        } catch (Exception e) {
            System.err.println(CommandLine.Help.Ansi.AUTO.string(
                "@|red Error: " + e.getMessage() + "|@"));
            return 1;
        }
    }
}
