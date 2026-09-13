# Vidsize ad model — operating document

v0.9.0. This is the document you read before changing anything about ads, and
the one you hand a tester before a device pass.

The model is adapted from a reusable pattern that was first proven in a PDF
reader. Most of it transfers unchanged. The parts that do **not** transfer are
called out explicitly, because they are the parts that would quietly break here.

---

## 1. What is different about Vidsize

The source pattern assumes a job that completes in milliseconds while the user
is looking at the screen. Vidsize's job takes **two to five minutes**, runs in a
foreground service, and the user frequently leaves the app while it runs. Three
consequences:

**The trigger is "the user came back", not "the work finished."** Firing an ad
when the compression completes would present a full-screen ad while Vidsize is
in the background — an outright Play policy violation — or over the progress
overlay. The interstitial is therefore driven from `MainActivity.onResume` and
from an explicit in-app transition, never from the job's completion callback.

**Preloading is free.** The usual reason interstitials lose impressions is
requesting one at the moment of display. The request here goes out when the job
*starts*, so the creative has minutes to arrive. There is deliberately no load
timeout: the user is never waiting on it.

**Entry path C barely exists.** The source model's heaviest path — a file opened
from another app — does not really apply. Nobody opens a video *with* a
compressor. Vidsize declares `ACTION_SEND` only (share a video into the app),
and on that path the user is *starting* work, not finishing it, so the app-open
ad is suppressed and nothing is deferred. There is no `ACTION_VIEW` filter and
none should be added.

---

## 2. The formats and where they sit

| Format | Placement | Live | Respects ad-free window |
|---|---|---|---|
| Banner | Anchored bottom of Home; top of Compression; top of the progress panel | yes | yes |
| Native | End of the Home scroll; result screen's own labelled section | yes | yes |
| Interstitial | After a finished compression — see §4 | yes | yes |
| Rewarded | Offer strip on Home **and** on the Compression screen | yes | **no** (it is the thing being traded for) |
| App open | Launcher-icon foreground only | **off — no unit** | yes |

Only one compression banner is ever live: the top banner switches off for the
whole processing phase so it never coexists with the one in the progress panel.

### App Open is deliberately not enabled

No unit exists for it in this AdMob account, and on the evidence that is the
right call for Vidsize rather than an omission to fix:

- **The eligible audience is small.** A large share of Vidsize opens arrive
  through the share sheet, where the format is suppressed on policy grounds
  anyway. Only launcher-icon opens are eligible.
- **Sessions are episodic.** People compress a video when they happen to need
  one smaller, not daily. App-open earns per app open, so its ceiling here is
  structurally low.
- **It carries the highest complaint risk of the five.** "I opened the app and
  got a full-screen ad" is the single loudest one-star driver in utility apps and
  the placement Play's Better Ads Experiences names most directly.

Low ceiling, highest risk: the worst ratio in the portfolio. Revisit only with
data — if AdMob reports substantial launcher-open volume once the other four
formats are stable and not drawing complaints.

Turning it on later needs no code: create the unit, declare an "App-open ad" in
`docs/privacy.html` (`verifyProductionAdConfig` fails the build if you forget),
and set `VIDSIZE_APP_OPEN_AD_UNIT_ID`. The policy already carries that paragraph,
so today the app under-shows what it declares, which is the safe direction.

---

## 3. The two rules that live in code

Everything else is a panel setting. A condition stays in code **only** if it is
a promise the app made to the user in words:

1. **60 seconds between full-screen ads.** `AdPacing.FULL_SCREEN_GAP_MILLIS`.
   Shared by the app-open ad and the interstitial — one timestamp, not two, or
   the user meets both back to back while each format believes it behaved.
2. **The rewarded ad-free window: 10 minutes, every format.** `AdFreeWindow`.
   Enforced in exactly one place, `AdSlots.requestable`.

### What was deleted, and why

v0.8.9's app-open ad required four conditions simultaneously: a 3-day install
grace period, 3 sessions minimum, a 6-hour cooldown, and a 4-hour creative
expiry. On a real device that did not add up to "conservative", it added up to
**never** — and each condition declined silently, so the result was
indistinguishable from no fill, from a consent refusal, and from a build with
ads switched off.

The first three are gone. The fourth was never a pacing rule: four hours is
Google's staleness limit for a *loaded* creative. Confusing those two is how an
app-open ad stays dark for an afternoon while every counter looks healthy.

A CI gate (`v0.9.0 ad model regression gates`, gate 2) fails the build if any of
`INSTALL_GRACE`, `MIN_SESSIONS_BEFORE`, `COOLDOWN_MILLIS`, `DAILY_LIMIT`,
`SESSION_QUOTA`, `MAX_PER_SESSION`, `MAX_PER_DAY`, `FREE_USES` or
`MIN_COMPRESSIONS` reappears anywhere under `ads/`.

---

## 4. The deferred interstitial

The intuitive wiring is backwards. Given "Compress another" and
Share / Show in Gallery / Open Video, the obvious design shows an ad on the
first and cancels it on the other three, so nothing gets between the user and
their file. That reasoning is right about placement and wrong about outcome:
practically everyone who just compressed a video wants to *do something with
it*, so the cancelling branch is the common one and the format earns nothing.

Defer instead of cancel:

| User action | Behaviour |
|---|---|
| **Compress another** → Home | Show now. `reset()` runs first — ordering is load-bearing (§6). |
| **Share** | Defer. Show on return. |
| **Show in Gallery** | Defer. Show on return. |
| **Open Video** | Defer. Show on return. |
| **Back** → preset picker, same video | Show now. Still a completed job and a real transition. |
| Cancelled / failed job | Clean. No call site exists. |
| History row on Home | Clean. Maintenance flow. |

`Context.deferInterstitialOnReturn()` is one function rather than two calls on
purpose. It both marks the pending ad and suppresses the app-open ad. Doing only
the first lets the app-open ad land on re-entry, which then blocks the
interstitial for 60 seconds and reads to a tester as "the deferred ad is
broken".

The pending flag is process-level and **not** persisted. If the process died
while the user was away, the ad dies with it — resurrecting it on the next cold
start would show an ad to someone with no context for it, and a cold start
already has the app-open ad.

---

## 5. AdMob panel settings

These are the tempo knobs. Change them here, not in code — no release needed.

| Unit | Setting |
|---|---|
| Interstitial | Frequency cap: **3 per user per hour** |
| App open | Frequency cap: **4 per user per day** |
| Banner | Auto-refresh: **60 seconds** |
| Rewarded | **No cap** — let the user watch as often as they like |
| Native | No cap |

Conservative values suitable for a first production rollout. Raise them from the
panel as install volume grows.

### Gradle properties

Read from a `-P` flag, then an environment variable, then an untracked
`ads.properties` at the repository root. Copy `ads.properties.example` and fill
it in once; it is gitignored.

**Required — a blank value switches off EVERY format, banners included.**
`verifyProductionAdConfig` names the missing one rather than failing with a
single unhelpful boolean.

```
VIDSIZE_ADMOB_APP_ID
VIDSIZE_HOME_BANNER_AD_UNIT_ID
VIDSIZE_NATIVE_RESULT_AD_UNIT_ID
VIDSIZE_INTERSTITIAL_AD_UNIT_ID
VIDSIZE_REWARDED_AD_UNIT_ID
```

**Optional — absent disables exactly one thing and nothing else.**

```
VIDSIZE_APP_OPEN_AD_UNIT_ID            # absent -> app-open format off
VIDSIZE_COMPRESSION_BANNER_AD_UNIT_ID  # absent -> reuses the home banner unit
```

The required/optional split exists because all-or-nothing is right for a format
the product depends on and wrong for one it does not: pinning App Open into the
required set would take the banner, the native, the interstitial and the rewarded
ad down with it over a unit nobody created.

The identifiers stay out of the repository not because they are secret — an
AdMob unit ID ships inside the APK — but to preserve the audit guarantee: a
clone with no identifiers packages with ads off and still builds, so CI can
publish an unsigned audit AAB that nobody can arm. A CI gate fails the build if
any real `ca-app-pub-` identifier ever appears in tracked source.

---

## 6. Build variants

| Variant | Ads | Units | Signing | Purpose |
|---|---|---|---|---|
| `debug` | off unless `-PVIDSIZE_ENABLE_TEST_ADS=true` | test | QA (public) | day-to-day |
| `adsQa` | **on** | **test** | QA (public) | **the device matrix in §8** |
| `closedTest` | on when IDs supplied | real | Play upload | Play closed track |
| `release` | on, IDs mandatory | real | Play upload | production |

`adsQa` is new and it is the variant a tester installs. Two reasons it exists:

- v0.8.9 pinned `closedTest` to `ENABLE_ADS = false`, which meant the only build
  a tester ever received could not show a single ad. T1–T7 were literally
  impossible to run against the shipped artefact.
- Running the matrix against **real** units is worse than useless: tapping your
  own production ads is invalid traffic, and a tester working through T1–T7 taps
  every format several times by design. That gets AdMob accounts suspended.

`adsQa` sets `isDebuggable = true`, and that flag is load-bearing rather than a
convenience: `AdIds` refuses any Google sample identifier unless
`BuildConfig.DEBUG` is set, so it is the single thing that lets test creatives
render there and nowhere else.

---

## 7. Diagnostics — seven taps on the version row in Settings

Not optional. Six causes produce one symptom ("no ad appeared") and each has a
different fix. The screen reports build state, consent state, usage counters,
the pacing clock, the ad-free countdown, and what is loaded — and then, at the
bottom, **one sentence naming the binding condition and what to do about it.**

Read the last line first. Do not ask a tester to interpret a column of numbers.

It is reachable from signed builds on purpose: the build that most needs
explaining is the one on a real tester's phone, not a debug build nobody is
confused about.

---

## 8. Device test matrix

Install `adsQa`. Before each test: force-stop, clear app data. Between tests:
**wait 60 seconds** or the pacing rule will (correctly) suppress the ad.

| # | Steps | Expected |
|---|---|---|
| T1 | Compress a video → **Compress another** | Interstitial **shows** |
| T2 | Compress → **Share** → return to Vidsize | Interstitial **shows on return**, not before the share sheet |
| T3 | Compress → **Open Video** → back | Interstitial **shows on return** |
| T4 | Compress → **Show in Gallery** → back | Interstitial **shows on return** |
| T5 | Share a video **into** Vidsize from Gallery | **No** app-open ad, **no** interstitial before the compression screen |
| T6 | Start a compression, background the app, return while still running | **No** full-screen ad of any kind |
| T7 | Let a compression finish while backgrounded, then foreground | **No** app-open ad in front of the result |
| T8 | Tap the ad-free strip, watch the rewarded ad fully | Banner disappears, countdown starts, text states 10 minutes |
| T9 | Inside the window: compress → Compress another | **No** ad. Result screen shows **no** ad section at all |
| T10 | Close the rewarded ad early | No window granted, no penalty, strip returns to its offer |
| T11 | Close Vidsize, reopen from the launcher icon | App-open ad **shows** |
| T12 | Immediately after any full-screen ad, trigger another | **Suppressed** (60-second rule) |
| T13 | Reopen an old video from a history row on Home | **No** interstitial |
| T14 | Cancel a compression midway | **No** interstitial |
| T15 | Compress → **Back** (top-left arrow) from the result | Interstitial **shows** |
| T16 | Scroll Home to the bottom | Labelled native ad after "Storage saved"; no layout jump above it |
| T17 | Open the compression screen | Ad-free offer strip above the presets; gone while processing |
| T18 | Open Vidsize from the launcher icon | **No** app-open ad (format has no unit) |

If any test disagrees: open the diagnostics screen and read the **VERDICT** line
at the bottom. It names the condition.

---

## 9. Before production rollout

- [ ] Create the interstitial and rewarded units in AdMob; set the §5 caps
- [ ] Supply all seven Gradle properties to the signed workflow
- [ ] Publish the updated privacy policy (it now declares all five formats, the
      reward and its duration) — `docs/privacy.html` and the bundled copy must
      stay byte-identical; CI checks this
- [ ] Run the §8 matrix on `adsQa` on at least two devices
- [ ] Confirm CI's negative tests still fire (the "New gates must FAIL when
      broken" step)
- [ ] Staged rollout, then raise the panel caps from the panel
