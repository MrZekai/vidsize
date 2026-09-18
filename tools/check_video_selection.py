#!/usr/bin/env python3
"""Regression gate for Vidsize's SAF video-selection contract.

The Android Photo Picker is intentionally not sufficient here: browser
downloads frequently live in the Downloads document provider rather than the
picker's visual-media collection. This checker locks the user-visible fix and
the three URI consumers that must remain SAF-compatible.
"""

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
ROOT_UI = ROOT / "app/src/main/java/com/vidsize/compressor/ui/VidsizeRoot.kt"
VIDEO_PROBE = ROOT / "app/src/main/java/com/vidsize/compressor/media/VideoProbe.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"


def require(text: str, pattern: str, explanation: str) -> None:
    if re.search(pattern, text, flags=re.MULTILINE | re.DOTALL) is None:
        raise AssertionError(explanation)


def forbid(text: str, pattern: str, explanation: str) -> None:
    if re.search(pattern, text, flags=re.MULTILINE | re.DOTALL) is not None:
        raise AssertionError(explanation)


def main() -> int:
    root_ui = ROOT_UI.read_text(encoding="utf-8")
    probe = VIDEO_PROBE.read_text(encoding="utf-8")
    manifest = MANIFEST.read_text(encoding="utf-8")

    checks = (
        lambda: require(
            root_ui,
            r"ActivityResultContracts\.OpenDocument\s*\(\s*\)",
            "VİDEO SEÇ must use SAF OpenDocument so Download/ is reachable.",
        ),
        lambda: require(
            root_ui,
            r"takePersistableUriPermission\s*\(.*?FLAG_GRANT_READ_URI_PERMISSION",
            "The SAF read grant must be persisted for foreground-service work.",
        ),
        lambda: require(
            root_ui,
            r"suppressNextForeground\s*\(\s*\).*?fileBrowser\.launch\s*\(\s*arrayOf\(\"video/\*\"\)\s*\)",
            "App-open suppression must cover the SAF round trip.",
        ),
        lambda: forbid(
            root_ui,
            r"\bPickVisualMedia(?:Request)?\b",
            "Photo Picker was reintroduced; downloaded videos can disappear again.",
        ),
        lambda: forbid(
            root_ui,
            r"ActivityResultContracts\.GetContent\s*\(",
            "GetContent cannot provide the persistable grant this workflow needs.",
        ),
        lambda: require(
            probe,
            r"retriever\.setDataSource\s*\(\s*context\s*,\s*uri\s*\)",
            "MediaMetadataRetriever must keep its Context + content Uri path.",
        ),
        lambda: require(
            probe,
            r"extractor\.setDataSource\s*\(\s*context\s*,\s*uri\s*,\s*null\s*\)",
            "MediaExtractor must keep its Context + content Uri path.",
        ),
        lambda: require(
            probe,
            r"openFileDescriptor\s*\(\s*uri\s*,\s*\"r\"\s*\).*?statSize",
            "File-size probing must keep the SAF file-descriptor path.",
        ),
        lambda: forbid(
            manifest,
            r"android\.permission\.(?:READ_EXTERNAL_STORAGE|READ_MEDIA_VIDEO|MANAGE_EXTERNAL_STORAGE)",
            "SAF selection must not regress into broad storage permissions.",
        ),
    )

    try:
        for check in checks:
            check()
    except AssertionError as error:
        print(f"SAF VIDEO SELECTION GATE FAILED: {error}", file=sys.stderr)
        return 1

    print("SAF video selection check: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
