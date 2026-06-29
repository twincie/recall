package dev;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FeatureFlags {

    private static final Map<String, Boolean> flags = new ConcurrentHashMap<>();

    // ── AI-dependent commands (all disabled by default) ────────────────
    public static boolean ASK     = true;
    public static boolean EXPLAIN = true;
    public static boolean TICKET  = true;
    public static boolean SIMILAR = true;
    public static boolean STANDUP = true;
    public static boolean RETRO   = true;
    public static boolean INGEST  = true;

    // ── Non-AI commands (all enabled by default) ───────────────────────
    public static boolean REMEMBER            = true;
    public static boolean SEARCH              = true;
    public static boolean SCRIPT              = true;
    public static boolean LIST                = true;
    public static boolean COMMAND             = true;
    public static boolean TODAY               = true;
    public static boolean CONFIG              = true;
    public static boolean GENERATE_COMPLETION = true;
    public static boolean SYNC                = true;
    public static boolean SHARE               = true;
    public static boolean REVIEW              = true;
    public static boolean RUNBOOK             = true;
    public static boolean ONBOARD             = true;
    public static boolean IMPORT              = true;
    public static boolean KNOWLEDGE           = true;
    public static boolean RECENT              = true;
    public static boolean BROWSE              = true;
    public static boolean SHOW                = true;
    public static boolean EDIT                = true;
    public static boolean CLEAN               = true;

    private static final Map<String, Boolean> DEFAULTS = Map.ofEntries(
        Map.entry("ask", ASK), Map.entry("explain", EXPLAIN),
        Map.entry("ticket", TICKET), Map.entry("similar", SIMILAR),
        Map.entry("standup", STANDUP), Map.entry("retro", RETRO),
        Map.entry("ingest", INGEST),

        Map.entry("remember", REMEMBER), Map.entry("search", SEARCH),
        Map.entry("script", SCRIPT), Map.entry("list", LIST),
        Map.entry("command", COMMAND), Map.entry("today", TODAY),
        Map.entry("config", CONFIG),
        Map.entry("generate-completion", GENERATE_COMPLETION),
        Map.entry("sync", SYNC), Map.entry("share", SHARE),
        Map.entry("review", REVIEW), Map.entry("runbook", RUNBOOK),
        Map.entry("onboard", ONBOARD), Map.entry("import", IMPORT),
        Map.entry("knowledge", KNOWLEDGE), Map.entry("recent", RECENT),
        Map.entry("browse", BROWSE), Map.entry("show", SHOW),
        Map.entry("edit", EDIT),
        Map.entry("clean", CLEAN)
    );

    static {
        flags.putAll(DEFAULTS);
    }

    private FeatureFlags() {}

    public static boolean isEnabled(String commandName) {
        return flags.getOrDefault(commandName, true);
    }
}
