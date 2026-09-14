#!/usr/bin/env python3
"""Catches a symbol that is used but never imported.

## Why this exists

v0.9.4 failed CI on two compiler errors that were one mistake:

    WatermarkStrip.kt:161 Unresolved reference 'VidsizeTheme'
    WatermarkStrip.kt:162 @Composable invocations can only happen from the
                          context of a @Composable function

A new file used `VidsizeTheme` in its @Preview and did not import it. The second
error was the first one's shadow - an unresolved symbol makes the lambda stop
being a composable context.

Every check the repo had passed: the file parses, the XML is valid, every grep
gate matched. Parsing is not resolution, and nothing here resolved anything.
The failure could only surface inside kotlinc, 46 seconds into a Gradle run
that cannot be run in this environment at all.

## What it checks

This codebase imports every external symbol explicitly - no wildcard imports -
so a capitalised identifier in code must come from exactly one of:

  * an explicit import in the same file,
  * a declaration in the same file,
  * a top-level declaration elsewhere in the same package,
  * the Kotlin/Java builtins listed below.

Anything else is unresolved, and this says so in under a second.

## What it deliberately does not check

Types, signatures, arity, nullability, overload resolution. This is not a
compiler and must not be described as one - it closes ONE failure class, the
one that has actually cost round trips.
"""
import re
import sys
from pathlib import Path

ROOT = Path("app/src/main/java")

# Kotlin/Java names available without an import. Not exhaustive by design:
# anything missing here surfaces as a false positive once, and is added then -
# which is cheaper than a list nobody trusts.
BUILTINS = {
    # Core types
    "Any", "Nothing", "Unit", "Boolean", "Byte", "Short", "Int", "Long", "Float",
    "Double", "Char", "String", "CharSequence", "Number", "Comparable", "Enum",
    "Throwable", "Exception", "RuntimeException", "IllegalStateException",
    "IllegalArgumentException", "UnsupportedOperationException", "Error",
    "NullPointerException", "ClassCastException", "NumberFormatException",
    "ArithmeticException", "IndexOutOfBoundsException",
    # Collections and friends
    "Array", "IntArray", "LongArray", "FloatArray", "DoubleArray", "ByteArray",
    "BooleanArray", "CharArray", "ShortArray",
    "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap",
    "Collection", "MutableCollection", "Iterable", "MutableIterable",
    "Iterator", "MutableIterator", "Sequence", "Pair", "Triple", "Result",
    "Lazy", "Regex", "MatchResult", "StringBuilder",
    # Annotations and modifiers used without import
    "Suppress", "Deprecated", "JvmStatic", "JvmField", "JvmOverloads", "JvmName",
    "JvmInline", "Throws", "SafeVarargs", "Volatile", "Synchronized",
    "Transient", "Strictfp", "PublishedApi", "RequiresOptIn", "Target",
    "Retention", "MustBeDocumented", "DslMarker", "ExperimentalStdlibApi",
    "OptIn",
    # Common java.lang names visible without an import in Kotlin/JVM
    "Math", "System", "Thread", "Runnable", "Class", "Object", "Void",
    "Character", "Integer", "Comparator", "Cloneable", "AutoCloseable",
    # kotlin.collections typealiases for the java.util classes
    "ArrayList", "HashMap", "HashSet", "LinkedHashMap", "LinkedHashSet",
    "ArrayDeque",
}

# Generated at build time, so present to the compiler and absent from the tree.
GENERATED = {"BuildConfig", "R"}

DECL = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public |private |internal |protected |abstract |final |open |sealed |data |value |inline |expect |actual |annotation |companion |enum |inner |external |const |lateinit |override |suspend |operator |infix |tailrec |vararg )*"
    r"(?:class|object|interface|fun|val|var|typealias)\s+"
    r"(?:<[^>]*>\s*)?"
    r"([A-Za-z_][A-Za-z0-9_]*)",
    re.M,
)

IMPORT = re.compile(r"^import\s+([A-Za-z0-9_.]+)(?:\s+as\s+([A-Za-z0-9_]+))?", re.M)
PACKAGE = re.compile(r"^package\s+([A-Za-z0-9_.]+)", re.M)

# A capitalised identifier NOT preceded by a dot (that would be member access)
# and not followed by one of the characters that mean it is being declared.
USE = re.compile(r"(?<![.\w@])([A-Z][A-Za-z0-9_]*)")


def strip_noise(text: str) -> str:
    """Blanks comments and string literals, preserving every newline.

    Written as a left-to-right scanner rather than a sequence of regexes,
    because the regex version had a real bug: it removed block comments FIRST,
    so a `/*` appearing inside a `//` line comment opened a comment that ran to
    the next `*/` and swallowed 73 lines of live code - including the very
    declaration whose absence it then reported as an error.

    Newlines survive so reported line numbers are the file's own. Kotlin allows
    nested block comments, so the depth is counted rather than assumed to be one.
    """
    out = []
    i, n = 0, len(text)
    depth = 0          # block-comment nesting
    line_comment = False
    quote = None       # None | '"' | "'" | '\u0022\u0022\u0022'
    while i < n:
        ch = text[i]
        two = text[i:i + 2]
        three = text[i:i + 3]

        if ch == "\n":
            line_comment = False
            out.append("\n")
            i += 1
            continue

        if line_comment or depth:
            if depth and two == "/*":
                depth += 1
                out.append("  ")
                i += 2
                continue
            if depth and two == "*/":
                depth -= 1
                out.append("  ")
                i += 2
                continue
            out.append(" ")
            i += 1
            continue

        if quote:
            if quote == '"""':
                if three == '"""':
                    quote = None
                    out.append("   ")
                    i += 3
                    continue
            else:
                if ch == "\\":
                    out.append("  ")
                    i += 2
                    continue
                if ch == quote:
                    quote = None
                    out.append(" ")
                    i += 1
                    continue
            out.append(" ")
            i += 1
            continue

        if two == "//":
            line_comment = True
            out.append("  ")
            i += 2
            continue
        if two == "/*":
            depth = 1
            out.append("  ")
            i += 2
            continue
        if three == '"""':
            quote = '"""'
            out.append("   ")
            i += 3
            continue
        if ch in ('"', "'"):
            quote = ch
            out.append(" ")
            i += 1
            continue

        out.append(ch)
        i += 1
    return "".join(out)


def main() -> int:
    files = sorted(ROOT.rglob("*.kt"))
    if not files:
        print(f"No Kotlin sources under {ROOT}", file=sys.stderr)
        return 1

    raw = {f: f.read_text(encoding="utf-8") for f in files}
    pkg_of = {f: (PACKAGE.search(t).group(1) if PACKAGE.search(t) else "") for f, t in raw.items()}

    # Top-level and nested declarations, by package. Nested ones are included
    # because a sibling file referring to `Foo.Bar` only needs `Foo`, and the
    # use-scan already drops anything after a dot.
    by_package: dict[str, set[str]] = {}
    declared: dict[Path, set[str]] = {}
    for f, text in raw.items():
        code = strip_noise(text)
        names = {m.group(1) for m in DECL.finditer(code)}
        # Enum entries, in both spellings this codebase uses: OUT_OF_SPACE on
        # its own line, and `enum class NoticeTone { Info, Blocking, Error }`
        # all on one.
        names |= set(re.findall(r"^\s*([A-Z][A-Za-z0-9_]*)\s*[,;(]", code, re.M))
        for body in re.findall(r"\benum class\s+\w+[^{]*\{([^}]*)\}", code):
            names |= set(re.findall(r"\b([A-Z][A-Za-z0-9_]*)\b", body))
        declared[f] = names
        by_package.setdefault(pkg_of[f], set()).update(names)

    problems: list[str] = []
    for f, text in raw.items():
        code = strip_noise(text)
        imported = set()
        for m in IMPORT.finditer(text):
            imported.add(m.group(2) or m.group(1).rsplit(".", 1)[-1])

        allowed = (
            imported
            | declared[f]
            | by_package.get(pkg_of[f], set())
            | BUILTINS
            | GENERATED
        )
        # Generic type parameters declared in this file (T, R, K, V...).
        allowed |= set(re.findall(r"<\s*([A-Z])\s*[,>]", code))

        seen: set[str] = set()
        for line_no, line in enumerate(code.split("\n"), start=1):
            if line.lstrip().startswith(("package ", "import ")):
                continue
            for m in USE.finditer(line):
                name = m.group(1)
                if name in allowed or name in seen:
                    continue
                # A bare SCREAMING_CASE name is a constant, and the ones that
                # appear bare are inherited from a superclass (Service's
                # START_NOT_STICKY, for one). Resolving those would mean
                # walking the class hierarchy into the SDK, which is the line
                # between this and a compiler.
                if name.isupper() or (name.upper() == name and "_" in name):
                    continue
                seen.add(name)
                problems.append(f"{f}:{line_no}: unresolved reference '{name}'")

    if problems:
        print("Kotlin reference errors (used but never imported):")
        for p in problems:
            print("  " + p)
        return 1

    print(f"Kotlin reference check: OK ({len(files)} files)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
