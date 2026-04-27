#!/usr/bin/env python3
"""Weekly README refresh.

Collects commits from the last 7 days on the current branch, asks Claude to
update the README to reflect those changes, and writes the result back to
README.md. The calling workflow is responsible for opening a PR if the file
changed.

Environment:
    ANTHROPIC_API_KEY  required.
    SINCE              optional override for the git --since window
                       (default: "7 days ago").
"""
from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

import anthropic

REPO_ROOT = Path(__file__).resolve().parents[2]
README_PATH = REPO_ROOT / "README.md"
SINCE = os.environ.get("SINCE", "7 days ago")

# Soft cap on the diff payload. Opus 4.7 has a 1M context window, but a runaway
# week (vendored deps, generated files) can blow past that — truncate with a
# visible marker rather than silently dropping bytes.
MAX_DIFF_CHARS = 400_000

SYSTEM_PROMPT = """\
You maintain the README.md of a software project. You will be given the current
README and a summary of code changes from the last week. Your job is to return
an UPDATED README that accurately reflects the current state of the project.

Rules:
- Preserve the existing structure, tone, headings, and formatting style.
- Update only sections that the changes actually affect (features, configuration,
  environment variables, tech stack, deployment instructions, etc.).
- Do NOT invent features, env vars, or behaviors that aren't backed by the diff.
- Do NOT remove sections unless the diff clearly removes the underlying feature.
- If the README is already accurate and no changes are warranted, output exactly
  the single token: NO_CHANGES_NEEDED
- Otherwise output ONLY the full new README content. No preamble, no commentary,
  no markdown code fences around the whole thing.
"""


def git(*args: str) -> str:
    return subprocess.run(
        ["git", *args],
        cwd=REPO_ROOT,
        check=True,
        capture_output=True,
        text=True,
    ).stdout


def collect_changes() -> str:
    log = git(
        "log",
        f"--since={SINCE}",
        "--no-merges",
        "--pretty=format:%h %s%n%b%n---",
    ).strip()
    if not log:
        return ""

    diff = git(
        "log",
        f"--since={SINCE}",
        "--no-merges",
        "-p",
        "--stat",
        "--",
        ".",
        ":(exclude)README.md",
        ":(exclude)*.lock",
        ":(exclude)**/*.lock",
        ":(exclude)*.png",
        ":(exclude)*.jpg",
        ":(exclude)*.jpeg",
        ":(exclude)*.gif",
        ":(exclude)*.svg",
    )

    truncated_note = ""
    if len(diff) > MAX_DIFF_CHARS:
        diff = diff[:MAX_DIFF_CHARS]
        truncated_note = (
            f"\n\n[diff truncated at {MAX_DIFF_CHARS} characters; "
            "rely on the commit log above for the full picture]\n"
        )

    return f"## Commit log\n\n{log}\n\n## Diff\n\n{diff}{truncated_note}"


def main() -> int:
    changes = collect_changes()
    if not changes:
        print(f"No commits since '{SINCE}' — skipping README refresh.")
        return 0

    readme = README_PATH.read_text()
    client = anthropic.Anthropic()

    user_content = (
        "# Current README.md\n\n"
        f"```markdown\n{readme}\n```\n\n"
        "# Changes from the last week\n\n"
        f"{changes}\n\n"
        "Return either NO_CHANGES_NEEDED or the full updated README content."
    )

    with client.messages.stream(
        model="claude-opus-4-7",
        max_tokens=64000,
        thinking={"type": "adaptive"},
        system=SYSTEM_PROMPT,
        messages=[{"role": "user", "content": user_content}],
    ) as stream:
        message = stream.get_final_message()

    text = "".join(b.text for b in message.content if b.type == "text").strip()

    if not text or text == "NO_CHANGES_NEEDED":
        print("Claude reports no README changes are needed.")
        return 0

    if not text.endswith("\n"):
        text += "\n"
    README_PATH.write_text(text)
    print("README updated.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
