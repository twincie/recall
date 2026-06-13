package dev.commands;

import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Command(name = "runbook", description = "Save and retrieve incident response steps by service")
public class RunbookCmd implements Runnable {
    @Parameters(description = "Service name", arity = "1")
    private String serviceName;

    @CommandLine.Option(names = {"--add"}, description = "Step to add")
    private String addStep;

    @CommandLine.Option(names = {"--list"}, description = "List all runbooks")
    private boolean listAll;

    private final StorageService storageService;

    public RunbookCmd() {
        try {
            this.storageService = new StorageService();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            String slug = StorageService.makeSlug(serviceName);

            if (addStep != null) {
                String filename = storageService.append(
                    "runbooks.md", slug, "runbook", slug, "- " + addStep);
                String msg = CommandLine.Help.Ansi.AUTO.string(
                    "@|green \u2713 Added step to |@@|bold,yellow " + slug + "|@@|green  runbook|@");
                System.out.println(msg);
                return;
            }

            if (listAll) {
                List<Map<String, String>> all = storageService.allEntries();
                System.out.println("Runbooks:\n");
                for (Map<String, String> e : all) {
                    if ("runbook".equals(e.get("type"))) {
                        System.out.println(CommandLine.Help.Ansi.AUTO.string(
                            String.format("  @|bold,green %s|@  @|cyan %d steps|@",
                                e.get("tags"), e.get("content").split("\n- ").length - 1)));
                    }
                }
                return;
            }

            List<Map<String, String>> all = storageService.allEntries();
            List<Map<String, String>> steps = all.stream()
                .filter(e -> "runbook".equals(e.get("type")) && e.get("tags").equals(slug))
                .collect(Collectors.toList());

            if (steps.isEmpty()) {
                System.out.println("No runbook for '" + serviceName + "'. Create one: recall runbook " + serviceName + " --add \"<step>\"");
                return;
            }

            String output = CommandLine.Help.Ansi.AUTO.string(
                String.format("@|bold,cyan === %s runbook ===|@\n",
                    slug));
            System.out.println(output);
            for (Map<String, String> step : steps) {
                System.out.println(step.get("content"));
            }
            System.out.println();
            System.out.println("Add more: recall runbook " + serviceName + " --add \"<step>\"");
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }
}