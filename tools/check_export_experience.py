#!/usr/bin/env python3
"""Static gate for exact-gallery, pre-export reward choice, and ad pacing."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        errors.append(f"missing file: {relative}")
        return ""
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        errors.append(f"missing {label}: {needle}")


def reject(text: str, needle: str, label: str) -> None:
    if needle in text:
        errors.append(f"forbidden {label}: {needle}")


compression = read(
    "app/src/main/java/com/vidsize/compressor/ui/screens/CompressionScreen.kt"
)
result = read("app/src/main/java/com/vidsize/compressor/ui/screens/ResultScreen.kt")
choice = read(
    "app/src/main/java/com/vidsize/compressor/ui/components/OutputChoiceDialog.kt"
)
rewarded = read("app/src/main/java/com/vidsize/compressor/ads/RewardedAds.kt")
interstitial = read("app/src/main/java/com/vidsize/compressor/ads/InterstitialAds.kt")
gate = read("app/src/main/java/com/vidsize/compressor/ads/AdGate.kt")
diagnostics = read("app/src/main/java/com/vidsize/compressor/ads/AdDiagnostics.kt")
pacing = read(
    "app/src/main/java/com/vidsize/compressor/ads/FullScreenPacingPolicy.kt"
)

# Exact output identity must reach the external gallery/media viewer.
require(result, "showInGallery(context, result.outputUri)", "output URI gallery call")
require(
    result,
    "private fun showInGallery(context: Context, uri: Uri)",
    "URI-taking gallery helper",
)
require(result, "Intent(MediaStore.ACTION_REVIEW)", "platform gallery review action")
require(result, 'setDataAndType(uri, "video/*")', "exact-item media intent")
require(result, "Intent.FLAG_GRANT_READ_URI_PERMISSION", "read permission grant")
reject(
    result,
    "MediaStore.Video.Media.EXTERNAL_CONTENT_URI",
    "generic gallery collection",
)

# One explicit decision before encoding; no result-screen second pass.
for needle, label in (
    ("showOutputChoice = true", "CTA opens output chooser"),
    ("OutputChoiceDialog(", "output chooser call"),
    ("onWithMark = { startCompression(watermark = true) }", "marked route"),
    ("onWithoutMark = { startWithoutWatermark() }", "rewarded route"),
    ("RewardGrantedDialog()", "reward confirmation"),
    ("startCompression(watermark = false)", "single clean encode"),
    ("RewardedChoiceMessage.NOT_EARNED", "early-close feedback"),
):
    require(compression, needle, label)

for needle, label in (
    ("watermark_with_mark_title", "marked choice copy"),
    ("watermark_free_card_title", "mark-free choice copy"),
    ("watermark_free_card_badge", "reward price disclosure"),
):
    require(choice, needle, label)

reject(result, "CompressionService", "result-screen re-encode")
reject(result, "WatermarkStrip", "post-export rewarded offer")
for old in ("WatermarkFreeCard.kt", "WatermarkStrip.kt"):
    if (ROOT / "app/src/main/java/com/vidsize/compressor/ui/components" / old).exists():
        errors.append(f"removed component returned: {old}")

# SDK reward is recorded while playing, but the product callback waits until
# dismissal so confirmation/compression cannot appear behind the ad.
dismiss = re.search(
    r"override fun onAdDismissedFullScreenContent\(\) \{(.*?)"
    r"override fun onAdFailedToShowFullScreenContent",
    rewarded,
    re.DOTALL,
)
if dismiss is None or "onRewardGranted()" not in dismiss.group(1):
    errors.append("reward completion is not dispatched after ad dismissal")
show_callback = re.search(r"creative\.show\(activity\) \{(.*?)\n\s*}\n", rewarded, re.DOTALL)
if show_callback is None:
    errors.append("rewarded SDK callback not found")
else:
    require(show_callback.group(1), "WatermarkOffer.grant()", "SDK-earned grant")
    reject(show_callback.group(1), "onRewardGranted()", "compression behind rewarded ad")

# One monotonic clock and a humane three-minute full-screen interval.
require(pacing, "3L * 60L * 1000L", "three-minute pacing constant")
for text, label in ((gate, "AdGate"), (diagnostics, "AdDiagnostics")):
    require(text, "AdPacing.now()", f"monotonic clock in {label}")
    reject(text, "System.currentTimeMillis()", f"wall clock in {label}")

require(interstitial, "lastShownOutputToken", "one-interstitial-per-output state")
require(
    interstitial,
    "outputToken == lastShownOutputToken",
    "duplicate output interstitial guard",
)

open_video = re.search(r"private fun openVideo\(.*?\n}\n", result, re.DOTALL)
if open_video is None:
    errors.append("in-app player helper not found")
else:
    reject(open_video.group(0), "deferInterstitialOnReturn", "ad after in-app player")

# Every locale must describe the same two-way exchange and must not mention the
# removed result-screen re-encode offer.
required_strings = {
    "watermark_choice_title",
    "watermark_choice_body",
    "watermark_with_mark_title",
    "watermark_with_mark_body",
    "watermark_with_mark_badge",
    "watermark_free_card_title",
    "watermark_free_card_body",
    "watermark_free_card_badge",
    "watermark_free_unavailable",
    "watermark_reward_not_earned",
    "watermark_reward_granted_title",
    "watermark_reward_granted_body",
}
resource_files = sorted((ROOT / "app/src/main/res").glob("values*/strings.xml"))
if len(resource_files) != 8:
    errors.append(f"expected 8 string locales, found {len(resource_files)}")
for path in resource_files:
    text = path.read_text(encoding="utf-8")
    names = set(re.findall(r'<string name="([^"]+)"', text))
    for name in sorted(required_strings - names):
        errors.append(f"{path.parent.name} missing {name}")
    for old in ("watermark_offer_title", "watermark_offer_body", "watermark_offer_action"):
        if old in names:
            errors.append(f"{path.parent.name} still defines {old}")

if errors:
    print("Export experience check: FAILED", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Export experience check: OK")
