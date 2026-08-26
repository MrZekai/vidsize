# Closed-test audit package

CI produces three artifacts from the same commit:

1. `vidsize-v080-qa-apk` — installable QA APK signed with the public QA debug key.
2. `vidsize-v080-closed-test-UNSIGNED-audit-aab` — release-like, R8/resource-shrunk
   AAB using Google demo ads. **Audit only. Never upload this unsigned file to Play.**
3. `vidsize-v080-source-audit` — exact source ZIP (`git archive`) for independent review.

The `closedTest` build type keeps the permanent package
`com.vidsize.compressor`, runs the release optimizer/shrinker, and explicitly
uses Google's test AdMob App ID/ad units.

The true `release` build never falls back to test ads. `bundleRelease` and
`assembleRelease` are blocked until all five production AdMob identifiers
(App ID + 4 ad-unit IDs) are supplied outside source control.

After independent Red-Team approval, create the private Play upload key and
produce the signed Closed Test AAB. The public QA debug keystore must never be
used for Google Play signing.
