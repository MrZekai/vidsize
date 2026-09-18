#!/usr/bin/env python3
"""Runs every shell-only CI gate against the working tree, locally.

## Why this exists

Run 34903198496 went red in twenty seconds on this single line:

    grep -qx 'ads.properties' .gitignore

The rule it checks had not changed. Its line ending had. .gitignore carried
Windows CRLF, and `ads.properties` had been the file's LAST line with no
terminator at all - so grep saw exactly "ads.properties" and -x matched.
Appending new rules below it gave that line a "\\r" terminator, -x stopped
matching, and `set -e` killed the step with no output at all.

Nothing in the app was wrong, and nothing any checker in this directory looks
at was wrong. The gates are shell, and only shell can find a shell bug.

check_kotlin_syntax.py, check_kotlin_references.py and check_string_resources.py
each close a gap that cost a red CI run. This closes the last one: the gates
themselves.

## Why it builds a throwaway git repository

The first version of this file skipped any step containing a git command, and
that skipped "v0.9.0 ad model regression gates" - the exact step that had just
failed. A verifier that steps around the thing it was written to catch is worse
than no verifier, because it reports green.

So instead of skipping, it snapshots the working tree into a temporary
directory, runs `git init` + `git add -A` + `git commit` there, and executes the
steps inside that. Every git-dependent gate then runs for real: `git ls-files`,
`git status --porcelain`, `git archive`. Only Gradle steps are skipped, because
those need a JDK, the network and six minutes.

The snapshot honours .gitignore, so ads.properties stays untracked exactly as it
is on the runner - which is the condition half these gates are checking.

    python3 tools/run_shell_gates.py

Exit status is the number of failed steps, so it chains with &&.
"""

import re
import shutil
import os
import subprocess
import sys
import tempfile
from pathlib import Path

try:
    import yaml
except ImportError:
    sys.exit("PyYAML gerekli: pip install pyyaml")

ROOT = Path(__file__).resolve().parent.parent
WORKFLOW = ROOT / ".github" / "workflows" / "android-debug.yml"

# The only genuine blocker: a step that invokes Gradle needs a JDK, the network
# and minutes. Everything else runs, git included.
NEEDS_GRADLE = re.compile(r"(^|\n)\s*(gradle|\./gradlew)\s")

IGNORE = shutil.ignore_patterns(
    ".git", "build", ".gradle", ".idea", "__pycache__", "*.pyc", "*.zip",
)


def snapshot(destination: Path) -> None:
    """Copy the tree and make it a real git repository."""
    shutil.copytree(ROOT, destination, ignore=IGNORE, dirs_exist_ok=True)
    run = lambda *a: subprocess.run(a, cwd=destination, check=True,
                                    capture_output=True, text=True)
    run("git", "init", "-q")
    run("git", "config", "user.email", "gates@local")
    run("git", "config", "user.name", "gates")
    run("git", "add", "-A")
    run("git", "commit", "-qm", "snapshot")


def main() -> int:
    steps = yaml.safe_load(WORKFLOW.read_text(encoding="utf-8"))["jobs"]["build"]["steps"]

    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp) / "tree"
        try:
            snapshot(work)
        except subprocess.CalledProcessError as exc:
            print("Gecici depo kurulamadi:", exc.stderr)
            return 1

        failed = 0
        ran = 0
        for index, step in enumerate(steps):
            script = step.get("run")
            if not script:
                continue
            name = step.get("name", f"step {index}")

            if NEEDS_GRADLE.search(script):
                print(f"  --   {name[:60]:62s} (Gradle gerekli)")
                continue

            # The scratch script lives OUTSIDE the tree it is testing.
            #
            # It used to be written to `work/.gate-tmp.sh`, which meant that for
            # as long as a step ran, the tree it was inspecting contained an
            # untracked file this tool had put there. Any gate asserting a clean
            # working tree - and there is one, because an untracked file is
            # exactly what travels into a commit by accident - saw
            # `?? .gate-tmp.sh` and failed for a reason that exists nowhere in
            # CI. A test harness that fails the thing it is testing is worse
            # than no harness.
            with tempfile.NamedTemporaryFile(
                "w", suffix=".sh", encoding="utf-8", delete=False
            ) as handle:
                handle.write(script)
                script_path = handle.name
            try:
                result = subprocess.run(
                    ["bash", script_path], cwd=work, capture_output=True, text=True
                )
            finally:
                os.unlink(script_path)

            ran += 1
            if result.returncode == 0:
                print(f"  OK   {name[:60]:62s}")
                continue

            failed += 1
            print(f"  FAIL {name[:60]:62s} exit={result.returncode}")
            tail = (result.stderr.strip() or result.stdout.strip()).splitlines()
            if not tail:
                # The exact shape of the failure this tool was written for: a
                # bare `grep -q` under `set -e`, which says nothing on its way
                # out. Re-run it traced so the line is named rather than hunted.
                print("         (cikti yok - sessiz bir 'grep -q' basarisizligi)")
                # Outside the tree, for the same reason as the run above.
                with tempfile.NamedTemporaryFile(
                    "w", suffix=".sh", encoding="utf-8", delete=False
                ) as handle:
                    handle.write(script)
                    traced_path = handle.name
                try:
                    traced = subprocess.run(
                        ["bash", "-x", traced_path],
                        cwd=work,
                        capture_output=True,
                        text=True,
                    )
                finally:
                    os.unlink(traced_path)
                for line in traced.stderr.strip().splitlines()[-3:]:
                    print(f"         son komut: {line}")
                continue
            for line in tail[-6:]:
                print(f"         {line}")

    print()
    print(f"{ran} adim calisti, {failed} basarisiz.")
    return failed


if __name__ == "__main__":
    sys.exit(main())
