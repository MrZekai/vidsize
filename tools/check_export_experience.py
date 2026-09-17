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
gate = read("app/src/main/java/com/vidsize/compressor/ads/AdGate.kt")
diagnostics = read("app/src/main/java/com/vidsize/compressor/ads/AdDiagnostics.kt")
pacing = read(
    "app/src/main/java/com/vidsize/compressor/ads/FullScreenPacingPolicy.kt"
)
main_activity = read("app/src/main/java/com/vidsize/compressor/MainActivity.kt")
application = read("app/src/main/java/com/vidsize/compressor/VidsizeApplication.kt")
external_navigation = read(
    "app/src/main/java/com/vidsize/compressor/ads/ExternalNavigation.kt"
)
home = read("app/src/main/java/com/vidsize/compressor/ui/screens/HomeScreen.kt")

# The gallery action must open Movies/Vidsize as a folder. Passing the exact
# media item to ACTION_REVIEW/ACTION_VIEW can open a player or an unrelated OEM
# preview instead of showing where the output was saved.
require(result, "showInGallery(context, result.outputUri)", "output URI gallery call")
require(
    result,
    "private fun showInGallery(context: Context, uri: Uri)",
    "URI-taking gallery helper",
)
require(result, "DocumentsContract.buildDocumentUri", "output folder document URI")
require(result, '"primary:Movies/Vidsize"', "Movies/Vidsize document id")
require(result, "DocumentsContract.Document.MIME_TYPE_DIR", "folder MIME type")
require(result, "DocumentsContract.EXTRA_INITIAL_URI", "folder browser fallback")
require(result, "Intent.FLAG_GRANT_READ_URI_PERMISSION", "read permission grant")
reject(
    result,
    "MediaStore.ACTION_REVIEW",
    "exact-item gallery review",
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

require(choice, "CompressionBannerAd(", "banner inside output chooser")
require(choice, "AdSlots.bannerVisible", "consent-gated chooser banner")

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

# Interstitial delivery is intentionally disabled. The implementation remains
# available for a future product decision, but no lifecycle or user-flow call
# site may initialise, preload, queue or show it.
for text, label in (
    (compression, "compression screen"),
    (result, "result screen"),
    (main_activity, "main activity"),
    (application, "application"),
    (external_navigation, "external navigation"),
):
    reject(text, "InterstitialAds", f"interstitial call in {label}")
reject(result, "deferInterstitialOnReturn", "deferred interstitial on external exit")

# Home must compose only visible scroll items. A single eager Column also
# inflated the NativeAd AndroidView and every history/statistics surface during
# initial layout, which made long swipes visibly hitch on the test device.
require(home, "LazyColumn(", "lazy home content")
require(home, 'item(key = "native-ad")', "lazy native-ad item")
reject(home, ".verticalScroll(", "eager home scroll column")

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
