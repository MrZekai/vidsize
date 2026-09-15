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

# forbid <basic-regex> <file>
#
# Fails the step, loudly and with the pattern named, when the pattern appears in
# the file's code. Silent when clean.
#
# ## Why a malformed pattern is a failure and not a pass
#
# grep has THREE exit codes: 0 found, 1 not found, 2 the pattern or the file was
# bad. The first version of this treated everything non-zero as "clean", which
# turned a broken pattern into a silently passing gate - the exact failure this
# whole file was written to end, wearing a different hat.
#
# It cost a real gate. A v0.9.9 check was written `forbid 'WatermarkFreeCard\('`
# on the reflex that a parenthesis needs escaping. `grep` without -E is a BASIC
# regex, where `(` is already literal and `\(` opens a group - so the pattern was
# an unmatched group, grep exited 2, and the gate reported clean on a file that
# contained exactly what it was there to forbid. It was caught by deliberately
# breaking the invariant and noticing the gate did not fire.
#
# Patterns here are BASIC regexes. Write `foo(` and not `foo\(`.
#
# ## Why the status is captured with `&& ... || ...`
#
# The callers run under `set -euo pipefail`. A bare
# `count="$(... | grep -c ...)"` is a plain assignment, so when grep finds
# nothing it exits 1, pipefail propagates that, and `set -e` kills the whole
# step at the assignment - turning a CLEAN file into a failed build. The old
# `if code_only | grep -q` form was immune because a pipeline inside `if` is
# exempt from `set -e`; this form has to opt back into that exemption
# explicitly, which is what the `&&`/`||` list does.
forbid() {
  local count status
  count="$(code_only "$2" | grep -c "$1" 2>/dev/null)" && status=0 || status=$?
  if [ "$status" -ge 2 ]; then
    echo "GATE BROKEN: '$1' is not a valid basic regex (grep exit $status)." >&2
    echo "Note: grep here is BRE - write 'foo(' not 'foo\\('." >&2
    exit 1
  fi
  if [ "${count:-0}" -gt 0 ]; then
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
