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


def code_only(text: str) -> str:
    """Kotlin source with comment lines removed.

    A prohibition that fires on a comment is a false positive, and the one that
    prompted this helper was exactly that: a comment explaining WHY a call had
    been replaced tripped the gate forbidding the call. The shell gates already
    learned this lesson (tools/gate_helpers.sh has the same helper); the Python
    checkers had not.

    Line-oriented on purpose. A full Kotlin parser is not needed to decide
    whether a line is commentary, and every prohibition here is about a call
    that must not be written, which is always a whole line of code.
    """
    kept = []
    in_block = False
    for line in text.splitlines():
        stripped = line.strip()
        if in_block:
            if "*/" in stripped:
                in_block = False
            continue
        if stripped.startswith("/*"):
            if "*/" not in stripped:
                in_block = True
            continue
        if stripped.startswith("//") or stripped.startswith("*"):
            continue
        kept.append(line)
    return "\n".join(kept)


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

# Android exposes no portable "reveal in folder" contract. The old
# ACTION_OPEN_DOCUMENT fallback was a picker: selecting a video returned an
# ignored result to Vidsize. Open the exact output externally instead.
require(result, "showInGallery(context, result.outputUri)", "output URI gallery call")
require(
    result,
    "private fun showInGallery(context: Context, uri: Uri)",
    "URI-taking gallery helper",
)
require(result, "Intent(Intent.ACTION_VIEW)", "external output view intent")
require(result, 'setDataAndType(uri, "video/*")', "exact video MIME and URI")
require(result, "ClipData.newRawUri", "URI permission ClipData")
require(result, "Intent.FLAG_GRANT_READ_URI_PERMISSION", "read permission grant")
reject(
    result,
    "Intent.ACTION_OPEN_DOCUMENT",
    "picker used as gallery browser",
)
reject(result, "DocumentsContract", "non-portable folder document intent")

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

# Interstitial delivery, wired.
#
# This block used to REJECT every InterstitialAds reference, on the grounds that
# the format was "intentionally disabled". Two things were wrong with that.
# docs/DECISIONS.md names the revenue model as "Home/Result native +
# Interstitial + Rewarded", so the format was never a product decision to drop;
# and a prohibition cannot tell the difference between a format switched off on
# purpose and one that quietly lost its call sites - which is precisely what had
# happened. InterstitialAds had zero callers anywhere but AdDiagnostics, so the
# format earned nothing AND the exits it was paired with kept deleting the
# app-open ad on the way back, giving up impressions in both directions.
#
# The gate is now the other way round: each half of the chain is required, so it
# cannot fall apart again without the build saying so.
require(application, "InterstitialAds.init(", "interstitial preferences init")
require(compression, "InterstitialAds.preload(", "interstitial preload before the result screen")
require(result, "InterstitialAds.markPending(", "deferred interstitial on external exit")
require(result, "InterstitialAds.showNow(", "immediate interstitial on in-app exit")
require(main_activity, "InterstitialAds.showPendingIfAny(", "deferred interstitial consumed on return")

# markPending() calls suppressAppOpenOnReturn() itself, so the result screen must
# not also call it directly: a bare suppression with no ad behind it is the exact
# shape of the regression above - the app-open impression is spent and nothing
# replaces it.
reject(code_only(result), "suppressAppOpenOnReturn", "unpaired app-open suppression on the result screen")

# The product rules that stay prohibitions, from docs/DECISIONS.md:
# no full-screen ad during an active compression, and none in front of a result
# the user has not seen yet.
reject(code_only(compression), "InterstitialAds.showNow", "full-screen ad during compression")
reject(code_only(compression), "InterstitialAds.showPendingIfAny", "full-screen ad during compression")
# Vidsize's own player is a clean inspection path and never arms an ad.
reject(result, "PlayerActivity.intent(context, uri))\n        InterstitialAds", "interstitial armed by the in-app player")
# The name that never existed. Documented for two releases, implemented never.
reject(code_only(result), "deferInterstitialOnReturn", "call to a function that does not exist")

# Home has a strict three-entry cap, so composing its short content once avoids
# lazy-list measurement overhead. Monetisation is a banner anchored outside the
# scroll; NativeAdView/MediaView remains Result-only and cannot join a fling.
require(home, ".verticalScroll(rememberScrollState())", "bounded eager home scroll")
reject(home, "LazyColumn(", "lazy-list overhead on bounded home content")
reject(home, "NativeAdCard", "media-heavy native ad on home")
require(home, "HomeBannerAd", "anchored home banner")
reject(home, "combinedClickable", "hidden recent-row long-press action")
require(home, "R.string.result_share", "visible recent-row share action")
if home.count("elevation = 0.dp") < 3:
    errors.append("home cards must avoid GPU-heavy scrolling shadows")

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
