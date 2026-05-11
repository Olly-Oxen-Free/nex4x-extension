# Raw Layer Index

The `raw/` layer holds primary-source notes that **survive context compaction**.
While the wiki is regenerable LLM synthesis, raw/ is durable evidence — the
anchors that wiki articles cite and that future maintain-brain runs reread.

## Layout

- `sessions/YYYY-MM-DD-<slug>.md` — dated session notes, decisions, findings
- `sources/<slug>.md` — external references: papers, specs, transcripts

Subdirs are lazy — created on first write by the **save-finding** skill.
Don't pre-seed empty dirs.

## How to write here

Use the `save-finding` skill. It enforces filename conventions, stamps
YAML frontmatter (`created`, `source_files`), and appends a bullet under
this file's `## Findings` section. maintain-brain discovers raw files
by globbing brain/raw/**/*.md — no state file to update.

Manual edits are fine; just keep the date prefix on session files so the
chronology stays scannable in Obsidian's file pane.
