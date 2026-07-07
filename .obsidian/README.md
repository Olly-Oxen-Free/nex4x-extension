# Brain Vault

Obsidian-friendly knowledge base maintained by the init-brain and
maintain-brain skills.

## Open in Obsidian

1. Launch Obsidian
2. "Open folder as vault" → select this project root
3. Navigate to `brain/wiki/` for compiled articles

## Layout

- `brain/wiki/` — compiled markdown articles, organized by theme (committed)
- `brain/raw/` — session notes + sources that survive compaction (committed)
- `brain/_brain/brain_state.json` — source of truth for brain state (committed)
- `brain/compiled_articles/` — temporary compilations (gitignored, regenerated)
- `brain/_brain/cache/` — graphify scratch (gitignored)

The brain self-maintains. Edit the wiki only when correcting LLM output.
