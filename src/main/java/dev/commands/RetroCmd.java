package dev.commands;

import dev.LLMService;
import dev.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Map;

@Command(name = "retro", description = "Log and review weekly retros")
public class RetroCmd implements Runnable {
    @Option(names = {"--week"}, description = "ISO week number (default: current)")
    private Integer week;

    @Option(names = {"--year"}, description = "Year (default: current)")
    private Integer year;

    @Option(names = {"--went-well"}, description = "Log what went well this week")
    private String wentWell;

    @Option(names = {"--went-badly"}, description = "Log what went badly this week")
    private String wentBadly;

    @Option(names = {"--review"}, description = "Review last quarter's retros")
    private boolean review;

    private final StorageService storageService;

    public RetroCmd() {
        try {
            this.storageService = new StorageService();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            int y = year != null ? year : LocalDate.now().getYear();
            int w = week != null ? week : LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

            if (review) {
                reviewQuarter(y, w);
                return;
            }

            if (wentWell != null || wentBadly != null) {
                log(y, w);
                return;
            }

            show(y, w);
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }

    private void log(int y, int w) throws Exception {
        String slug = "retro-w" + w + "-" + y;
        if (wentWell != null) {
            storageService.append("retros.md", slug + "-well", "retro", "well", wentWell);
            System.out.println(CommandLine.Help.Ansi.AUTO.string("@|green \u2713 Logged what went well|@"));
        }
        if (wentBadly != null) {
            storageService.append("retros.md", slug + "-badly", "retro", "badly", wentBadly);
            System.out.println(CommandLine.Help.Ansi.AUTO.string("@|green \u2713 Logged what went badly|@"));
        }
    }

    private void show(int y, int w) throws Exception {
        System.out.println("Retro for " + y + " week " + w + "\n");

        String prefix = "retro-w" + w + "-" + y;
        List<Map<String, String>> all = storageService.allEntries();
        boolean found = false;
        for (Map<String, String> e : all) {
            if (e.get("slug").startsWith(prefix)) {
                if (!found) found = true;
                String tag = e.get("tags").equals("well") ? "Went well" : "Went badly";
                System.out.println("  " + tag + ": " + e.get("content").replaceAll("\n", " "));
            }
        }

        if (!found) {
            System.out.println("  Nothing logged yet.\n");
            System.out.println("  recall retro --went-well \"<what went well>\"");
            System.out.println("  recall retro --went-badly \"<what went badly>\"");
        }
    }

    private void reviewQuarter(int y, int w) throws Exception {
        int quarter = (w - 1) / 13 + 1;
        System.out.println("Q" + quarter + " " + y + " Review\n");

        List<Map<String, String>> all = storageService.allEntries();
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map<String, String> e : all) {
            if (e.get("slug").startsWith("retro-") && e.get("slug").contains("-" + y)) {
                try {
                    String numPart = e.get("slug").replaceAll("retro-w(\\d+)-.*", "$1");
                    int ew = Integer.parseInt(numPart);
                    int eq = (ew - 1) / 13 + 1;
                    if (eq == quarter) {
                        sb.append("- [").append(e.get("tags")).append("] ")
                            .append(e.get("content").replaceAll("\n", " ")).append("\n");
                        count++;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (count == 0) {
            System.out.println("No retros for Q" + quarter + ".");
            return;
        }

        System.out.println(count + " entries from Q" + quarter + "\n");
        System.out.println(sb);

        try {
            LLMService llm = new LLMService();
            String summary = llm.query(
                "Summarize these quarterly retro entries:\n\n" + sb,
                "Identify 3-5 key themes and trends. Be concise.", 1024);
            System.out.println("--- Themes ---\n" + summary);
        } catch (Exception e) {
            System.out.println("Set DEVOS_API_KEY for an AI-generated theme analysis.");
        }
    }
}