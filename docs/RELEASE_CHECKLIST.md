# Release checklist

Ordered by when it blocks you. Items marked **BLOCKER** stop a Play release
outright; the rest cost you ratings rather than approval.

---

## A. Before you write another line of code

- [ ] **BLOCKER — Enable GitHub Pages from `main` → `/docs`** and verify the public legal URLs. The repository is already named `vidsize`.
- [ ] **BLOCKER — Verify the two legal URLs load** in a browser:
      `…/privacy.html` and `…/terms.html`. If your GitHub username's casing
      differs, fix `url_privacy` and `url_terms` in `values/strings.xml`.
- [ ] **Decide the support email.** The pages and the Play listing currently use
      `aitolianrock@gmail.com`. It becomes public on your store listing. A
      dedicated address (e.g. `support@…`) is worth ten minutes of setup.
- [ ] **Read the two legal pages and change anything that is not true of you.**
      They are drafted to match what this code actually does, but I am not a
      lawyer and they are not legal advice. Section 11 of the Terms leaves
      governing law tied to where you are established — confirm that is what you
      want before publishing.

## B. Before the first internal build goes to a tester

- [ ] Install on a **non-Pixel** device. At minimum one Samsung (Exynos) and one
      MediaTek phone (Xiaomi/Redmi/Realme). Pixel-only testing is how this
      category earns one-star reviews.
- [ ] Test the **share-sheet entry**: open Gallery → Share → Vidsize.
- [ ] Test with the phone in **dark mode** — the status bar icons must stay dark
      and readable over the white UI.
- [ ] Test with **font size set to maximum** in system settings. No clipped
      labels anywhere.
- [ ] Test on a phone with **almost no free storage** — the pre-flight notice
      should appear and the COMPRESS button should be disabled.
- [ ] Test a **long video** (15+ min or 1.5 GB+) — the "big video" notice appears
      and the job completes or fails gracefully.
- [ ] Test **cancel mid-compression** — no orphan file appears in the gallery.
- [ ] Test **leaving the app mid-compression**: switch to another app, confirm the
      notification keeps updating and the job finishes.
- [ ] Test **cancel from the notification** action.
- [ ] Test **denying the notification permission** on Android 13+ — compression
      must still complete; only the progress notification is missing.
- [ ] Test **Arabic** (RTL) and **Hindi** — layout mirrors correctly, nothing
      overlaps.
- [ ] Confirm a compressed file has **audio** and is **not rotated wrongly**.

## C. AdMob

- [ ] **BLOCKER — Create the real AdMob app** and provide its App ID as
      `VIDSIZE_ADMOB_APP_ID` only for the production build. Debug and `closedTest`
      deliberately use Google's demo App ID.
- [ ] **BLOCKER — Create four real ad units**: Home adaptive banner,
      Compression adaptive banner, Result Native Advanced, and App Open. Provide
      them as `VIDSIZE_HOME_BANNER_AD_UNIT_ID`,
      `VIDSIZE_COMPRESSION_BANNER_AD_UNIT_ID`,
      `VIDSIZE_NATIVE_RESULT_AD_UNIT_ID`, and `VIDSIZE_APP_OPEN_AD_UNIT_ID`.
      Production packaging hard-fails if any production identifier is missing.
- [ ] **BLOCKER — Configure the GDPR message in the AdMob console**
      (Privacy & messaging → European regulations). The `ConsentManager` code is
      already wired, but without a published message the form never appears.
- [ ] Add your test device's advertising ID as an AdMob **test device** so you
      never load a live ad on your own phone. Tapping your own live ad is the
      fastest route to an account suspension.
- [ ] Verify the consent form actually appears using a debug geography override,
      and that **no ad loads before consent resolves** (the ad slots stay empty).
- [ ] Verify **Settings → Ad privacy options** appears for an EEA user and
      re-opens the form.
- [ ] App Open QA: first 3 days suppressed, first 3 sessions suppressed, no ad over
      compression/result/share-sheet entry, no entry delay when an ad is unavailable.
- [ ] Confirm `closedTest` uses Google demo ads and the true `release`
      variant uses only the four real Vidsize ad units.

## D. Play Console

- [ ] **BLOCKER — Foreground service declaration** — declare `mediaProcessing`
      in App content and use [PLAY_FOREGROUND_SERVICE.md](PLAY_FOREGROUND_SERVICE.md)
      for the use-case explanation/demo video.
- [ ] **BLOCKER — Data Safety form** — use [PLAY_DATA_SAFETY.md](PLAY_DATA_SAFETY.md) as the code-matched checklist and confirm the wording against the current Console.
- [ ] **BLOCKER — Advertising ID declaration** = Yes.
- [ ] **BLOCKER — Privacy policy URL** entered in both App content and the
      store listing.
- [ ] **BLOCKER — Content rating questionnaire** completed.
- [ ] **BLOCKER — Target audience: not children.** Do not tick any under-13 band.
- [ ] **BLOCKER — EU trader status declared** if you distribute in the EU.
- [ ] **BLOCKER — Closed testing** — follow the exact tester count/duration shown for this developer account in Play Console. Recruit testers before the production gate.
- [ ] Target API 36 — already set. Re-check Play's current target-API deadline before production.
- [ ] The CI `closedTest` AAB is an **UNSIGNED Red-Team artifact only**.
      Do not upload it to Play.
- [ ] After final Red-Team approval, create/secure the private Play upload key and
      upload the resulting **signed AAB**, not an APK. Keep the upload keystore
      backed up somewhere you will still have in five years.
- [ ] Store listing: 25-char title, 70-char short description, 8 localised
      listings, screenshots with the real `1.02 GB → 612 MB` number.

## E. Rollout

- [ ] Staged rollout: **10% → 50% → 100%**, gated on crash-free rate ≥ 99%.
- [ ] Watch **Android vitals** for ANRs on the compression screen for the first
      72 hours.
- [ ] Read every 1–3 star review in the first two weeks and fix the top cause
      before adding any new feature.

---

## Known limitations shipped on purpose

| Item | Status | Why |
|---|---|---|
| App Open ads | **Implemented and enabled** | 3-day grace, 3-session warm-up, 6-hour cooldown, no blocking when an ad is unavailable. |
| Trim before compress | Not built | Highest-value V1.1 feature; needs a new screen and touches the export path. |
| Batch compression | Not built | V1.1. |
| Dark theme | Not built | V1 is Light Minimal by decision; the token system makes it a small change later. |
| Foreground service for background compression | **Shipped in v0.5** | `media/CompressionService.kt`. Compression survives leaving the app, shows live progress in a notification, and can be cancelled from there. |
| Maximum file size | Not advertised | No fixed limit exists; the app checks storage and warns instead. See `StorageGuard`. |
