# Shared helpers for the CI source gates. Sourced, never executed.
#
# ## Why this file exists
#
# Every "this must NOT be in the source" gate used to be written as:
#
#     ! grep -q PATTERN FILE
#
# under `set -euo pipefail`. That check never failed a build. POSIX exempts a
# command whose status is inverted with `!` from `set -e`, so when the forbidden
# pattern WAS present the line reported failure and the script carried on; the
# step's exit status came from whatever ran last. Nineteen gates in this
# workflow were written that way, including the ones guarding consent handling,
# the removed home banner, and the ad-free clock. All of them were decoration.
#
# `forbid` is the replacement, and it exits.
#
# The second half of the lesson: the first gate converted to a real check failed
# immediately - on a COMMENT that named the forbidden symbol while explaining
# why it had been removed. A gate that fires on its own documentation gets
# deleted rather than fixed, so these look at code only.

# Strips full-line comments in the four syntaxes this repo's sources use:
# Kotlin/Java (// and block-comment continuations), XML (<!--) and shell (#).
# A trailing comment on a line of code is deliberately NOT stripped: a forbidden
# symbol sitting after real code on the same line is still in the file.
code_only() {
  grep -vE '^[[:space:]]*(//|\*|/\*|<!--|#)' "$1"
}

# forbid <extended-regex> <file>
#
# Fails the step, loudly and with the pattern named, when the pattern appears in
# the file's code. Silent when clean.
forbid() {
  if code_only "$2" | grep -q "$1"; then
    echo "GATE FAILED (must not appear in code): $1 -> $2" >&2
    exit 1
  fi
}

# forbid_span <perl-regex> <file>
#
# The multi-line form, for "X must not appear within N characters of Y".
# Same code-only rule as `forbid`: stripping comment lines first is what keeps a
# note explaining a removed call from being read as the call itself.
#
# Note that stripping changes distances, so a span limit here is a limit in
# CODE, not in file text - which is the thing the gate actually means.
forbid_span() {
  if code_only "$2" | grep -Pzoq "$1"; then
    echo "GATE FAILED (must not appear in code): $1 -> $2" >&2
    exit 1
  fi
}
