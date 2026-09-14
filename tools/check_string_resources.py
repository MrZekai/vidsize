#!/usr/bin/env python3
"""AAPT2 string-resource escaping validator.

XML well-formedness is NOT enough: an apostrophe is legal XML and illegal
in an unquoted Android string resource. This checks the rules aapt2 applies
AFTER the XML parser, which is exactly the gap that let the v0.9.2 build
through a green local check and into a red CI run.
"""
import sys, glob, re
from xml.etree import ElementTree as ET

VALID_ESCAPES = set("nt\\'\"@?ur")
errs = []

def check_text(path, name, text):
    i, n, in_quotes = 0, len(text), False
    while i < n:
        c = text[i]
        if c == '\\':
            if i + 1 >= n:
                errs.append(f"{path}: {name}: trailing backslash"); return
            e = text[i+1]
            if e not in VALID_ESCAPES:
                errs.append(f"{path}: {name}: invalid escape sequence '\\{e}'")
            elif e == 'u':
                hexpart = text[i+2:i+6]
                if len(hexpart) < 4 or not re.fullmatch(r'[0-9a-fA-F]{4}', hexpart):
                    errs.append(f"{path}: {name}: invalid unicode escape '\\u{hexpart}'")
                i += 6; continue
            i += 2; continue
        if c == '"':
            in_quotes = not in_quotes
        elif c == "'" and not in_quotes:
            errs.append(f"{path}: {name}: unescaped apostrophe (use \\' or wrap in \")")
        i += 1
    if text[:1] in ('@', '?'):
        errs.append(f"{path}: {name}: value starts with '{text[0]}' and must be escaped")

def walk(path, el, name):
    if el.text:
        check_text(path, name, el.text)
    for child in el:
        walk(path, child, name)
        if child.tail:
            check_text(path, name, child.tail)

for path in sorted(glob.glob('app/src/main/res/values*/strings.xml')):
    root = ET.parse(path).getroot()
    for el in root:
        if el.tag == 'string':
            walk(path, el, el.get('name'))
        elif el.tag in ('string-array', 'plurals'):
            for item in el:
                walk(path, item, f"{el.get('name')}[]")

if errs:
    print("AAPT string resource errors:")
    for e in errs:
        print("  " + e)
    sys.exit(1)
print("AAPT string resource check: OK")
