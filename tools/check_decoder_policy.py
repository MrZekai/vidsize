#!/usr/bin/env python3
"""Regression gate for high-resolution decoder recovery.

Codec capability tables are advisory. The product contract is that a 2K/4K
source gets a real attempt, Media3 decoder fallback is enabled, one
software-first recovery is available, and every mode starts its output at
1080p or below. This checker keeps those four pieces connected.
"""

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
MEDIA = ROOT / "app/src/main/java/com/vidsize/compressor/media"
MODEL = ROOT / "app/src/main/java/com/vidsize/compressor/model/VideoInfo.kt"
SCREEN = ROOT / "app/src/main/java/com/vidsize/compressor/ui/screens/CompressionScreen.kt"
TESTS = ROOT / "app/src/test/java/com/vidsize/compressor/media"


def require(text: str, pattern: str, explanation: str) -> None:
    if re.search(pattern, text, flags=re.MULTILINE | re.DOTALL) is None:
        raise AssertionError(explanation)


def forbid(text: str, pattern: str, explanation: str) -> None:
    if re.search(pattern, text, flags=re.MULTILINE | re.DOTALL) is not None:
        raise AssertionError(explanation)


def main() -> int:
    engine = (MEDIA / "CompressionEngine.kt").read_text(encoding="utf-8")
    policy = (MEDIA / "DecoderRecoveryPolicy.kt").read_text(encoding="utf-8")
    planner = (MEDIA / "CompressionPlanner.kt").read_text(encoding="utf-8")
    model = MODEL.read_text(encoding="utf-8")
    screen = SCREEN.read_text(encoding="utf-8")
    policy_test = (TESTS / "DecoderRecoveryPolicyTest.kt").read_text(encoding="utf-8")
    planner_test = (TESTS / "CompressionPlannerTest.kt").read_text(encoding="utf-8")

    checks = (
        lambda: require(
            engine,
            r"DefaultDecoderFactory\.Builder\(context\).*?"
            r"setEnableDecoderFallback\(true\)",
            "Media3 decoder fallback must be enabled explicitly.",
        ),
        lambda: require(
            engine,
            r"DecoderRoute\.SOFTWARE_FIRST.*?"
            r"setMediaCodecSelector\(MediaCodecSelector\.PREFER_SOFTWARE\)",
            "A software-first decoder recovery route is required.",
        ),
        lambda: require(
            engine,
            r"setAssetLoaderFactory\(assetLoaderFactory\)",
            "The configured decoder factory must be attached to Transformer.",
        ),
        lambda: require(
            engine,
            r"DecoderRecoveryPolicy\.nextRoute\(decoderRoute, decoderFailed\).*?"
            r"if \(decoderFailed\).*?throw SourceUndecodableException",
            "Decoder failure must recover once, then stop the output ladder.",
        ),
        lambda: forbid(
            engine,
            r"if\s*\(\s*!\s*info\.(?:decoderPrecheckPassed|deviceCanDecode)\s*\)",
            "A codec-table result must never hard-block the engine again.",
        ),
        lambda: require(
            policy,
            r"PLATFORM_ORDER\s*->\s*DecoderRoute\.SOFTWARE_FIRST",
            "The normal route must get exactly one software-first recovery.",
        ),
        lambda: require(
            model,
            r"val decoderPrecheckPassed: Boolean = true",
            "The advisory decoder measurement must travel with VideoInfo.",
        ),
        lambda: require(
            screen,
            r"val blockedEntirely = probeFailed\s*$",
            "Only an unreadable file may block the whole compression screen.",
        ),
        lambda: forbid(
            screen,
            r"blockedEntirely\s*=\s*[^\n]*(?:decoderFallbackExpected|decoderPrecheckPassed)",
            "A negative decoder pre-check must not disable the CTA.",
        ),
        lambda: require(
            screen,
            r"decoderFallbackExpected.*?NoticeTone\.Info.*?"
            r"notice_decoder_fallback_title",
            "A negative codec report should be informational, not blocking.",
        ),
        lambda: require(
            planner,
            r"TARGET_SHORT_EDGES\s*=\s*intArrayOf\(1080, 720, 480, 360\)",
            "Target mode must start 2K/4K sources at 1080p or below.",
        ),
        lambda: require(
            policy_test,
            r"aDecoderFailureGetsOneSoftwareFirstRetry.*?"
            r"aSoftwareFirstDecoderFailureStopsImmediately.*?"
            r"anEncoderFailureNeverChangesTheDecoderRoute",
            "The decoder recovery state machine needs all three unit cases.",
        ),
        lambda: require(
            planner_test,
            r"twoKAndFourKSourcesStartAt1080pOrBelowInEveryMode",
            "The 2K/4K output cap needs a unit regression test.",
        ),
    )

    try:
        for check in checks:
            check()
        for strings_file in sorted(ROOT.glob("app/src/main/res/values*/strings.xml")):
            strings = strings_file.read_text(encoding="utf-8")
            require(
                strings,
                r'name="notice_decoder_fallback_title"',
                f"Decoder fallback title missing from {strings_file}.",
            )
            require(
                strings,
                r'name="notice_decoder_fallback_body"',
                f"Decoder fallback body missing from {strings_file}.",
            )
    except AssertionError as error:
        print(f"DECODER POLICY GATE FAILED: {error}", file=sys.stderr)
        return 1

    print("2K/4K decoder recovery policy: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
