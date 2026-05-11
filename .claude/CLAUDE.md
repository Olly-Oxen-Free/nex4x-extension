# CLAUDE.md

Auto-loaded by Claude Code in this project. Keep terse — every line costs context.

## Knowledge layers (read on demand, do not preload)

- **Narrative wiki** — `brain/wiki/index.md` — human-curated themes (the "why").
- **Raw memory** — `brain/raw/index.md` — primary-source notes that survive compaction (`sessions/`, `sources/`). Anchors the wiki. Write here via `save-finding`.
- **Code topology** — `brain/_brain/GRAPH_REPORT.md` — graphify communities + cross-refs (the "what calls what"). Full graph at `brain/_brain/graph.json` (query via `graphify query "..."`).
- **State** — `brain/_brain/brain_state.json` — orchestrator metadata. Don't read unless debugging brain.

When a task spans multiple files, check `brain/_brain/GRAPH_REPORT.md` first to pick the right entry point. When intent is unclear, check `brain/wiki/index.md`. After a compaction or fresh session, skim `brain/raw/index.md` for durable findings the wiki may not yet reflect.

## Project rules

Follow superpowers TDD discipline. Write failing test, then implement. No skipping.

Run `maintain-brain` after substantive commits to keep wiki in sync.

<!-- Add project-specific rules below. Keep additions tight. -->
