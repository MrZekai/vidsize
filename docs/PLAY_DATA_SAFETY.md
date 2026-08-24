# Play Console — Data Safety guidance (Vidsize v0.6)

This file is a code-matched checklist, not legal advice. Re-check it whenever
Google Mobile Ads SDK, UMP, analytics, crash reporting, or any backend changes.

Vidsize itself keeps video processing and compression history on-device. The
Google Mobile Ads SDK 25.4.0 is the reason the Play Data Safety answer is **Yes**
for collection/sharing.

## Data collection and sharing

Google's disclosure for Mobile Ads SDK 25.4.0 says the SDK automatically
collects/shares IP address, user product interactions, diagnostic information,
and device/account identifiers for advertising, analytics, and fraud prevention.
Google Play also says location inferred from IP must be disclosed as approximate
location when that inference occurs.

Declare at least these categories after confirming the current Play form wording:

| Play data type | Collected | Shared | Purposes |
|---|---|---|---|
| Location → Approximate location | Yes | Yes | Advertising/marketing; analytics; fraud prevention/security as applicable |
| App activity → App interactions | Yes | Yes | Advertising/marketing; analytics; fraud prevention/security as applicable |
| App info and performance → Diagnostics | Yes | Yes | Analytics; fraud prevention/security as applicable |
| Device or other IDs → Device or other IDs | Yes | Yes | Advertising/marketing; analytics; fraud prevention/security as applicable |

Do **not** declare Photos and videos as collected solely because Vidsize reads a
user-selected video locally. Google Play does not treat on-device-only access as
collection when the data is not transmitted off device.

Vidsize does not have accounts, a backend, cloud video processing, analytics,
Crashlytics, contacts, messages, health, finance, or installed-app collection.

## Encryption in transit

For the off-device ad/consent traffic above, answer **Yes**: Google's SDK traffic
uses TLS.

## Account / deletion questions

- Vidsize does **not** allow account creation.
- `Clear` deletes Vidsize's local compression history; uninstalling removes
  app-private local data because backups are disabled.
- Do **not** claim that `Clear` deletes data processed by Google Ads.
- For any Play Console question specifically asking for deletion of off-device
  data, follow the wording shown in your current Console and Google's current
  policy. The app has no developer-operated server-side user record to delete.

## Advertising ID

The Mobile Ads SDK can collect Android advertising ID and other identifiers.
Complete the Play Console Advertising ID declaration consistently with the
merged manifest and current Google Mobile Ads documentation. Do not manually
remove SDK permissions without re-checking ad behavior and the declaration.

## Other Play Console declarations

| Section | Vidsize answer |
|---|---|
| Ads | Contains ads: **Yes** |
| Target audience | General utility; **not directed to children** |
| Accounts | **No account creation** |
| Video content | User-selected media is processed **on-device** |
| Foreground service | `mediaProcessing` for user-started video transcoding; see `PLAY_FOREGROUND_SERVICE.md` |

## Re-check before every release

1. Compare this file with Google's current Mobile Ads SDK data disclosure for the
   exact SDK version in `app/build.gradle.kts`.
2. Confirm the Privacy Policy says the same thing as the Play Data Safety form.
3. If analytics, crash reporting, a backend, sign-in, cloud processing or a new
   ad/measurement SDK is added, update both before uploading the AAB.
