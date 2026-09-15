#!/usr/bin/env python3
"""Structural balance check for Kotlin sources.

## Why this exists

v0.9.7 removed a block from ProcessingOverlay.kt with a regex that matched
across a brace boundary. It deleted the closing `) {` of a `VidsizeCard(` call
along with the block, leaving:

    VidsizeCard(
        elevation = 24.dp,
        Column(

The file still "looked" like Kotlin. The reference checker passed - every
symbol used was imported - and the string and grep gates passed too. Nothing in
the repository could see that the file no longer parsed, so the first thing to
notice was kotlinc, forty seconds into the Gradle step:

    Mixing named and positional arguments is not allowed
    Unresolved reference 'Spacer'
    Syntax error: Expecting ')'

A real parser is the right tool and one exists (ktlint), but it needs a JVM and
a download, and CI already pays for a compiler later. What is missing is a
CHEAP check that runs first. Bracket balance is not parsing, but every
mechanical edit that cuts across a block boundary - which is how this class of
damage actually happens - unbalances something.

## What it checks

Per file, outside comments and string literals: (), [] and {} nest and close.
Reports the line where an unmatched opener was introduced, which is where the
edit went wrong, not where the compiler eventually noticed.

## What it does not check

Everything else. Arity, types, names, statement structure, and any corruption
that happens to stay balanced. It closes one class, quickly.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_kotlin_references import strip_noise  # noqa: E402

ROOT = Path("app/src/main/java")
PAIRS = {")": "(", "]": "[", "}": "{"}
OPENERS = set(PAIRS.values())


def check(path: Path) -> list[str]:
    code = strip_noise(path.read_text(encoding="utf-8"))
    stack: list[tuple[str, int]] = []
    problems: list[str] = []
    for line_no, line in enumerate(code.split("\n"), start=1):
        for ch in line:
            if ch in OPENERS:
                stack.append((ch, line_no))
            elif ch in PAIRS:
                if not stack:
                    problems.append(f"{path}:{line_no}: stray '{ch}'")
                    return problems
                opener, opened_at = stack.pop()
                if opener != PAIRS[ch]:
                    problems.append(
                        f"{path}:{line_no}: '{ch}' closes '{opener}' "
                        f"opened at line {opened_at}"
                    )
                    return problems
    for opener, opened_at in stack:
        problems.append(f"{path}:{opened_at}: '{opener}' is never closed")
    return problems


def main() -> int:
    files = sorted(ROOT.rglob("*.kt"))
    if not files:
        print(f"No Kotlin sources under {ROOT}", file=sys.stderr)
        return 1
    problems: list[str] = []
    for f in files:
        problems.extend(check(f))
    if problems:
        print("Kotlin structure errors (brackets do not balance):")
        for p in problems[:20]:
            print("  " + p)
        return 1
    print(f"Kotlin structure check: OK ({len(files)} files)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
