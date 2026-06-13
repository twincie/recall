# recall

**Personal engineering memory manager** — a CLI tool that stores everything you learn as markdown files in `~/.recall/`. No database, no server, no dependencies beyond Java 17.

```bash
recall remember "use VirtualThreads for IO tasks" --tags java,concurrency
recall search virtual threads
recall today
recall standup
```

## Install

### Prerequisites
- Java 17+
- Maven (to build)

### Build

```bash
git clone <repo> && cd recall
mvn package
```

This produces a fat JAR at `target/recall-1.0-SNAPSHOT.jar`.

### Run

```bash
# Via wrapper script
./recall.sh --help

# Or directly
java -jar target/recall-1.0-SNAPSHOT.jar --help
```

### Shell completion (optional)

```bash
java -jar target/recall-1.0-SNAPSHOT.jar generate-completion >> ~/.zshrc
# or for bash:
java -jar target/recall-1.0-SNAPSHOT.jar generate-completion >> ~/.bashrc
```

Tab-completes command names, flags, and saved slugs.

## Commands

### Core

| Command | Description |
|---------|-------------|
| `remember <text>` | Store a note. No quotes needed — `recall remember git stash` works |
| `search <query>` | Search all notes. Multi-word, intelligent matching |
| `list` | Browse recent entries. `-n 5`, `--tag spring` |
| `today` | Show everything saved today with counts |
| `snippet save/get` | Save and retrieve code snippets from files |
| `command save/run` | Vault for frequently used shell commands |

### LLM-powered (requires `DEVOS_API_KEY`)

| Command | Description |
|---------|-------------|
| `explain [file]` | Analyze a stack trace or error — pipe it in or pass a file |
| `ticket [description]` | Generate a Jira ticket from `git diff HEAD` or a task description |
| `ask <question>` | Searches your notes first, escalates to Claude if nothing found |
| `standup` | Summarizes the last 24h entries into a standup report |
| `retro --review` | Quarterly theme analysis of retro entries |

### Knowledge management

| Command | Description |
|---------|-------------|
| `similar <text>` | Find related notes by meaning (LLM semantic + local word-similarity fallback) |
| `review` | Spaced repetition — surface notes from 7, 30, 90 days ago |
| `share <slug>` | Output an entry as clean markdown or create a GitHub Gist |
| `sync [--setup <remote>]` | Push to a private git remote. One-time setup: `recall sync --setup git@github.com:user/repo.git` |

### Workflow

| Command | Description |
|---------|-------------|
| `retro --went-well/badly` | Weekly retro logging. `--review` for quarterly analysis |
| `runbook <service> --add <step>` | Incident response steps by service name |
| `onboard [name] [--topic]` | Package your notes into an onboarding doc for teammates |

### Configuration

| Command | Description |
|---------|-------------|
| `config [--set key=value]` | View or set config in `~/.recall/config.properties` |
| `generate-completion` | Output shell completion script |

## Configuration

All config lives in `~/.recall/config.properties`. Set via `recall config --set key=value`:

| Key | Default | Description |
|-----|---------|-------------|
| `storage.path` | `~/.recall/` | Custom data directory (e.g., Dropbox for cross-machine sync) |
| `llm.provider` | `claude` | `claude`, `openai`, or `openai-compatible` |
| `llm.model` | per-provider | `claude-sonnet-4-6`, `gpt-4o`, `llama3`, etc. |
| `llm.api-key` | `DEVOS_API_KEY` env | Falls back to environment variable |
| `llm.api-url` | per-provider | Custom endpoint for OpenAI-compatible APIs (Ollama, vLLM) |

### Switching LLM providers

```bash
# OpenAI
recall config --set llm.provider=openai
recall config --set llm.model=gpt-4o

# Local model (Ollama)
recall config --set llm.provider=openai-compatible
recall config --set llm.api-url=http://localhost:11434/v1/chat/completions
recall config --set llm.model=llama3
```

### Cross-machine sync

```bash
recall config --set storage.path=/Users/johnson/Dropbox/recall
```

Now all your data lives in a synced folder. Zero infrastructure.

## Storage format

Everything is plain markdown in `~/.recall/`:

```markdown
## 2026-06-12 | use-virtualthreads-for-io-tasks
**tags:** java,concurrency
**type:** note

use VirtualThreads for IO tasks

---
```

Each file (`notes.md`, `snippets.md`, `tickets.md`, `commands.md`, `runbooks.md`, `retros.md`) is append-only. You can edit, grep, or version-control them with any tool.

## Architecture

```
recall/
  src/main/java/dev/
    DevCLI.java              # Picocli entry point
    LLMService.java          # Multi-provider LLM (Claude, OpenAI, local)
    StorageService.java      # Markdown file operations
    commands/
      RememberCmd.java       # 19 commands, one file each
      SearchCmd.java
      ...
```

- **Picocli** — CLI framework (ANSI colors, auto-complete, typo correction)
- **Jackson** — JSON serialization for LLM API calls
- **Markdown** — storage format (no database)
- **Maven shade plugin** — fat JAR packaging

## Data directory

`~/.recall/` contains:

| File | Purpose |
|------|---------|
| `notes.md` | General notes from `remember` |
| `snippets.md` | Code snippets from `snippet save` |
| `tickets.md` | Generated tickets from `ticket` |
| `commands.md` | Saved commands from `command save` |
| `runbooks.md` | Incident steps from `runbook` |
| `retros.md` | Retro entries from `retro` |
| `config.properties` | Persistent configuration |

If `~/.recall/` is a git repository, every write auto-commits and pushes.

## Motivation

Most engineering knowledge lives in three places: your head (volatile), Slack threads (unsearchable), and PR comments (hidden). `recall` is a zero-friction alternative — a CLI that stores what you learn as plain markdown, finds it instantly, and optionally enriches it with LLM summaries.

Designed for daily use:
- `recall remember "fixed BeanCreationException — missing @Bean in config" --tags spring`
- `recall explain` (paste stack trace, get root cause + fix)
- `recall ticket` (after finishing a feature, generate the Jira ticket from git diff)
- `recall standup` (end of day, generate your standup from today's notes)
- `recall review` (see what you learned 7, 30, 90 days ago)

## License

MIT
