# recall — AGENTS.md

## Immediate-first

```bash
mvn package -q                  # quiet build
java -jar target/recall-1.0.0.jar --help
```

## Build & run

- `mvn clean package` — fat JAR at `target/recall-1.0.0.jar`
- `recall.sh` wrapper: `./recall.sh remember "..."` checks JAR exists first
- requires **Java 17+** (Adoptium); Maven only needed to build
- **no tests** — no `src/test/` directory exists

## Architecture

- **Packages**: `dev`, `dev.commands`, `dev.recall`, `dev.recall.commands`
- **Entrypoint** `dev.DevCLI` (`pom.xml` `mainClass`)
- **23 subcommands** in `dev.commands.*` / `dev.recall.commands.*`, registered as Picocli subcommands in `DevCLI`
- **Storage** `StorageService` — reads/writes `~/.recall/*.md` (or `storage.path` from config)
- **LLM** `LLMService` — multi-provider (Claude, OpenAI, openai-compatible), needs `DEVOS_API_KEY` env or `llm.api-key` config
- **SlugUtil** (`dev.recall.SlugUtil`) — shared slug cleaner used by `RememberCmd` and `ImportCmd`
- **Dependencies**: Picocli 4.7.5, Jackson 2.16.1 (JSON for LLM API)

## Storage & config

- Data dir: `~/.recall/` (set via `recall config --set storage.path=...`)
- Config file: `~/.recall/config.properties`
- If `~/.recall/` is a git repo, every write auto-commits + pushes (silent failure, 5s timeout)
- File format: `notes.md`, `snippets.md`, `tickets.md`, `commands.md`, `runbooks.md`, `retros.md`
- Entries are `## date | slug` blocks delimited by `---`

## Releasing

```bash
mvn clean package
gh release create v<version> target/recall-1.0.0.jar --title "v<version>" --notes "..."
```

## Known quirks

- `DevCLI.main` runs `System.exit(exitCode)` — not suitable for embedding
- `remember` subcommand has `unmatchedOptionsArePositionalParams(true)` set in main()
- Shell completion: `recall generate-completion >> ~/.zshrc`
- Every subcommand has short/long aliases (e.g. `-r`/`--remember`, `-t`/`--today`, `-s`/`--search`) — see `--help`
- No CI, no lint, no formatter config present in repo
- `.gitignore` includes `*.jar` but the built JAR in `target/` is covered by `target/`
