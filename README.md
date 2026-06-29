# recall

**Personal engineering memory manager** — all your commands, notes, snippets, runbooks, and knowledge saved in plain markdown files under `~/.recall/`.

## Quick install

### Homebrew (macOS)
```bash
brew install twincie/recall/recall
```

### Linux / macOS / Windows (Git Bash)
```bash
curl -fsSL https://raw.githubusercontent.com/twincie/recall/main/install.sh | bash
```

### Build from source
```bash
git clone https://github.com/twincie/recall.git
cd recall
mvn package
# JAR at target/recall-1.0.0.jar
./recall.sh --help
```

Requires **Java 17+**. Maven only needed to build.

### Shell completion
```bash
recall generate-completion >> ~/.zshrc   # or ~/.bashrc
```

---

## Core commands

### `recall remember [--name <title>] [--file <path>] [--edit] [--confirm] [--tags <tags>] [<text>]`

Store a note. First line becomes the title (slug). Supports stdin pipe, `--file`, and `--edit` for input.

```bash
recall remember "use VirtualThreads for IO-bound tasks" --tags java,concurrency
recall remember --name "kubectl pod exec" --tags kubernetes
echo "remember this" | recall remember
recall remember --edit
```

If the slug already exists you get a prompt: **[S]ave as new, [U]pdate existing, [C]ancel**.

### `recall edit <slug>`

Find an entry by slug across all files and open it in `$EDITOR`. If multiple entries share the same slug, you pick which one.

### `recall show <slug> [--plain|-p]`

Display the full content of a single entry.

```bash
recall show virtual-threads
recall show virtual-threads --plain
```

### `recall search <query> [--file <file>] [--limit|-n <n>] [--plain|-p]`

Full-text search across all entries. Default shows interactive browser with numbered results.

```bash
recall search "virtual threads"
recall search --tag kubernetes "pod exec"
recall search --file notes.md --limit 5
```

### `recall list [--limit|-n <count>] [--type|-t <type>] [--plain|-p]`

Browse entries. Default shows an interactive numbered list. Use `--plain` for non-interactive.

```bash
recall list -n 5 --type command
recall list --plain
```

### `recall recent [--limit|-n <count>] [--type|-t <type>] [--plain|-p]`

Compact list with relative dates (today, yesterday, 3 days ago, etc.).

```bash
recall recent
recall recent -n 20 --type note
```

### `recall today [--plain|-p]`

Show everything saved today, grouped by type.

### `recall review [--days <n,n,n>] [--plain|-p]`

Surface entries from past days. Default: 7, 30, and 90 days ago.

```bash
recall review
recall review --days 1,7,30 --plain
```

### `recall clean [--file|-f <name>] [--slug|-s <slug>] [--all|-a] [--confirm|-c]`

List storage files with entry counts, clear a file, remove a specific entry, or clear everything.

```bash
recall clean          # list files with counts
recall clean --file notes.md
recall clean --slug virtual-threads
recall clean --all --confirm
```

---

## Share

### `recall share <slug> [--clip|-c] [--gist|-g] [--private|-p] [--output|-o <file>]`

Share an entry as markdown. No flags = print to stdout.

```bash
recall share virtual-threads                    # print to stdout
recall share virtual-threads --clip             # copy to clipboard
recall share virtual-threads --gist             # create GitHub Gist
recall share virtual-threads --gist --private   # private Gist
recall share virtual-threads --output note.md   # save to file
```

Reads `GITHUB_TOKEN` env var for gist creation; prompts if not set.

---

## Scripts

### `recall script save <name> [--file <path>] [--edit] [--confirm]`
### `recall script get <name>`
### `recall script run <name> [-- <args>]`
### `recall script list` / `recall script ls`
### `recall script edit <name>`

```bash
recall script save deploy --file deploy.sh
recall script run deploy -- --env prod
recall script ls
```

---

## Commands

### `recall command save <name> [--file <path>] [--edit] [--confirm]`
### `recall command run <name>`
### `recall command list` / `recall command ls`
### `recall command edit <name>`

```bash
recall command save build "mvn package -q"
recall command run build
```

---

## Runbooks

### `recall runbook <service> [--add <step>] [--remove <n>] [--edit] [--delete <service>] [--list|-l] [--plain|-p]`

Steps are stored as a single numbered block per service.

```bash
recall runbook api-gateway --add "kubectl get pods -n istio-system"
recall runbook api-gateway --add "curl -I http://localhost:8080/health"
recall runbook api-gateway               # view all steps
recall runbook api-gateway --remove 1    # remove and renumber
recall runbook api-gateway --edit        # open in $EDITOR
recall runbook --list                    # list all services
recall runbook --delete api-gateway      # delete runbook
```

---

## Knowledge base

### `recall knowledge <text> [--name <title>] [--file <path>] [--edit] [--tags <tags>] [--get|-g <slug>] [--search|-s <query>] [--delete <slug>] [--plain|-p]`

Dedicated knowledge-base storage.

```bash
recall knowledge "Spring Boot auto-configuration works by..." --tags spring
recall knowledge --name "architecture" --file docs/arch.md --tags system-design
recall knowledge --get spring-boot-autoconfiguration
recall knowledge --search auto-configuration
recall knowledge --delete spring-boot-autoconfiguration
recall knowledge --edit --name "my-topic"
```

---

## Onboard

### `recall onboard [name] [--topic <tag>] [--limit <n>] [--output|-o <file>]`

Package entries as a markdown onboarding doc with table of contents.

```bash
recall onboard "new dev" --topic spring
recall onboard --limit 50 --output onboarding.md
```

---

## Import

### `recall import <file> [--type <type>] [--file <target>] [--confirm] [--dry-run]`
### `recall import --dir <path> [--type <type>] [--file <target>]`
### `recall import --history`
### `recall import --vscode`

```bash
recall import notes.md                     # auto-detect → notes.md
recall import commands.sh --type command   # force type
recall import data.txt --file snippets.md  # write to custom file
recall import --dir ~/notes/               # prompts per file if no --type
recall import --history                    # pick commands interactively
recall import --vscode                     # VS Code snippets
recall import --dry-run notes.md           # preview without writing
```

| Extension | Default file |
|-----------|-------------|
| `.md` | `notes.md` |
| `.txt` | `notes.md` |
| `.sh` | `commands.md` |
| `.json` | `snippets.md` |

---

## Ingest

### `recall ingest <text> [--tags <tags>]`

Auto-classify unstructured text with LLM and file into the right category.

```bash
recall ingest "kubectl get pods -n default shows CrashLoopBackOff"
```

---

## LLM-powered

Commands that use an LLM (Claude, OpenAI, Gemini, DeepSeek, or local via Ollama).

Requires `llm.api-key` in config or `DEVOS_API_KEY` env var.

| Command | Description |
|---------|-------------|
| `recall ask <question>` | Check notes first, then ask LLM |
| `recall explain [file]` | Explain a stack trace or error |
| `recall ticket [desc]` | Generate a Jira ticket from text or `git diff` |
| `recall standup` | Standup summary from last 24h |
| `recall retro --went-well/badly --review` | Log and review weekly retros |
| `recall similar <text>` | Find related notes by meaning |

---

## Config

### `recall config [--set key=value] [--get key] [--delete key] [--list] [--edit]`

```bash
recall config --list
recall config --set llm.provider=openai
recall config --set llm.model=gpt-4o
recall config --set storage.path=/Users/johnson/Dropbox/recall
```

| Key | Default | Description |
|-----|---------|-------------|
| `storage.path` | `~/.recall/` | Data directory |
| `llm.provider` | `claude` | `claude`, `openai`, `openai-compatible`, `deepseek`, `gemini` |
| `llm.model` | per-provider | Model name override |
| `llm.api-key` | `DEVOS_API_KEY` | API key (env var overrides config) |
| `llm.api-url` | per-provider | Custom endpoint (Ollama, vLLM, etc.) |

### `recall sync`

Push `~/.recall/` to a git remote (always pulls before push).

```bash
recall config --set sync.remote=git@github.com:user/notes.git
recall sync
```

### `recall generate-completion`

Print shell completion script.

---

## Storage format

Everything is append-only markdown in `~/.recall/`:

| File | Content |
|------|---------|
| `notes.md` | General engineering notes |
| `snippets.md` | Code snippets |
| `scripts.md` | Saved scripts |
| `tickets.md` | Generated tickets |
| `commands.md` | Saved shell commands |
| `runbooks.md` | Incident response steps |
| `retros.md` | Weekly retros |
| `knowledge.md` | Knowledge base entries |

Entry format:

```markdown
## 2026-06-12 | virtual-threads
**tags:** java,concurrency
**type:** note

use VirtualThreads for IO-bound tasks to reduce memory overhead

---
```

If `~/.recall/` is a git repository, every write auto-commits and pushes (non-blocking).

---

## License

MIT
