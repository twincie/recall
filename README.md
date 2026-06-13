# recall

**Personal engineering memory manager** — a zero-infrastructure CLI tool that stores everything you learn as plain markdown files in `~/.recall/`. No database, no server, no cloud dependency.

```bash
recall remember "use VirtualThreads for IO tasks" --tags java,concurrency
recall search virtual threads
recall today
recall standup
```

## Quick install

### macOS (Homebrew)

```bash
brew install twincie/recall/recall
```

### Linux / macOS / Windows (Git Bash)

```bash
curl -fsSL https://raw.githubusercontent.com/twincie/recall/main/install.sh | bash
```

Or download the JAR and run directly:

```bash
curl -fsSLo /usr/local/lib/recall/recall-1.0.0.jar \
  https://github.com/twincie/recall/releases/download/v1.0.0/recall-1.0.0.jar
```

### Windows (PowerShell)

```powershell
# Download JAR
Invoke-WebRequest -Uri "https://github.com/twincie/recall/releases/download/v1.0.0/recall-1.0.0.jar" -OutFile "$env:USERPROFILE\recall.jar"

# Create alias (add to your $PROFILE)
function recall { java -jar "$env:USERPROFILE\recall.jar" $args }
```

### Build from source

```bash
git clone https://github.com/twincie/recall.git
cd recall
mvn package
# JAR at target/recall-1.0.0.jar
```

### Prerequisites

- **Java 17+** — [install from Adoptium](https://adoptium.net)
- **Maven** (only if building from source)

### Shell completion

```bash
recall generate-completion >> ~/.zshrc   # or ~/.bashrc
```

Tab-completes all 20+ commands, flags like `--tag`, and even saved slugs.

## All commands

### Store and retrieve

| Command | Example |
|---------|---------|
| `remember <text>` | `recall remember "fixed BeanCreationException" --tags spring` |
| `search <query>` | `recall search --tag spring connection pool` |
| `list` | `recall list -n 5 --tag kubernetes` |
| `snippet save <name> --file <path>` | `recall snippet save docker-compose --file docker-compose.yml` |
| `snippet get <name>` | `recall snippet get docker-compose` |
| `command save <name> <cmd>` | `recall command save build "mvn package -q"` |
| `command run <name>` | `recall command run build` |
| `today` | `recall today` |

### AI-powered (requires `DEVOS_API_KEY` or `llm.api-key` in config)

| Command | Example |
|---------|---------|
| `explain [file]` | `cat error.log \| recall explain` |
| `ticket [description]` | `recall ticket` (auto from git diff) |
| `ask <question>` | `recall ask "how to fix circular dependency in Spring"` |
| `standup` | `recall standup` |
| `retro --review` | `recall retro --review` |

### Knowledge management

| Command | Example |
|---------|---------|
| `similar <text>` | `recall similar "thread pool configuration"` |
| `review` | `recall review` |
| `share <slug>` | `recall share my-note` |
| `sync [--setup <remote>]` | `recall sync --setup git@github.com:user/memory.git` |

### Workflow

| Command | Example |
|---------|---------|
| `retro --went-well/badly` | `recall retro --went-well "fixed pipeline flakiness"` |
| `runbook <service> --add <step>` | `recall runbook api-gateway --add "kubectl get pods"` |
| `onboard [name] [--topic]` | `recall onboard "new dev" --topic spring` |

### Configuration

| Command | Example |
|---------|---------|
| `config [--set key=value]` | `recall config --set llm.provider=openai` |
| `generate-completion` | `recall generate-completion >> ~/.zshrc` |

## Configuration

File: `~/.recall/config.properties`

| Key | Default | Description |
|-----|---------|-------------|
| `storage.path` | `~/.recall/` | Custom data directory (for Dropbox/iCloud sync) |
| `llm.provider` | `claude` | `claude`, `openai`, or `openai-compatible` |
| `llm.model` | per-provider | Model name override |
| `llm.api-key` | `DEVOS_API_KEY` env | Falls back to environment variable |
| `llm.api-url` | per-provider | Custom endpoint for local models (Ollama, vLLM) |

```bash
# Use OpenAI instead of Claude
recall config --set llm.provider=openai
recall config --set llm.model=gpt-4o

# Use a local model via Ollama
recall config --set llm.provider=openai-compatible
recall config --set llm.api-url=http://localhost:11434/v1/chat/completions
recall config --set llm.model=llama3

# Sync data via Dropbox
recall config --set storage.path=/Users/johnson/Dropbox/recall
```

## Storage format

Everything is plain markdown. Each file is append-only. You can `grep`, `vim`, or version-control them with any tool.

```markdown
## 2026-06-12 | fixed-beancreationexception
**tags:** spring,config
**type:** note

fixed BeanCreationException by adding @Bean to VirtualAccountProperties config

---
```

Files in `~/.recall/`:

| File | Purpose |
|------|---------|
| `notes.md` | General engineering notes |
| `snippets.md` | Code snippets |
| `tickets.md` | Generated Jira tickets |
| `commands.md` | Saved shell commands |
| `runbooks.md` | Incident response steps |
| `retros.md` | Weekly retro entries |

If `~/.recall/` is a git repo, every write auto-commits and pushes.

## Architecture

```
recall/
  pom.xml                  # Maven build, shade plugin for fat JAR
  install.sh               # Cross-platform installer
  Formula/recall.rb        # Homebrew formula
  src/main/java/dev/
    DevCLI.java            # Picocli entry point, 19 subcommands
    LLMService.java        # Multi-provider LLM client
    StorageService.java    # Markdown file operations
    commands/              # One file per command
```

- **Picocli** — CLI framework (ANSI colors, tab-completion, typo auto-correction)
- **Jackson** — JSON serialization for LLM API calls
- **Markdown** — zero-dependency storage
- **Maven shade** — fat JAR with all dependencies

## Development

```bash
mvn package              # Build fat JAR
java -jar target/recall-1.0.0.jar --help  # Test
```

## Releasing

```bash
# Build and verify
mvn clean package
java -jar target/recall-1.0.0.jar --version

# Create GitHub release with the JAR attached
gh release create v1.0.0 target/recall-1.0.0.jar --title "v1.0.0" --notes "Release notes"
```

## License

MIT
