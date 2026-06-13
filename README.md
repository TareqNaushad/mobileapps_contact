# Fuzzy Contacts (Android, plain Java)

A tiny Android app that fixes the "exact-match only" contact search on phones.
Type **tariq** and it still finds **Tareq**; type **onirban** and it finds **Anirban**.
Tap a result to open the dialer (pick your SIM there) and call.

- Language: **plain Java** (Android framework only — no Flutter, no AndroidX, no libraries)
- Matching: normalize + collapse-repeats + **Levenshtein** + **Soundex**
- Permission requested: only `READ_CONTACTS` (calling uses the dialer, no `CALL_PHONE`)

---

## Option A — Build in the cloud (recommended, nothing to install)

You do **not** need the Android SDK or Java on your laptop for this.

1. Create a free account at https://github.com (if you don't have one).
2. Create a new **empty** repository, e.g. `fuzzy-contacts`.
3. Upload this whole `fuzzy-contacts` folder to the repo:
   - Easiest: on the repo page click **Add file → Upload files**, then drag in
     everything inside this folder (keep the folder structure).
   - The `.github/workflows/build.yml` file must end up at the repo root path
     `.github/workflows/build.yml`.
4. GitHub automatically runs the build. Open the **Actions** tab and wait for the
   green check (≈2–3 min).
5. Click the finished run → scroll to **Artifacts** → download
   **`FuzzyContacts-debug-apk`** (a zip). Inside is `app-debug.apk`.

## Option B — Build locally (only if you want to)

Requires **JDK 17** and the Android command-line SDK (`platforms;android-34`,
`build-tools;34.0.0`). Then from this folder:

```
gradle wrapper          # once, to generate ./gradlew  (needs Gradle 8.x installed)
./gradlew assembleDebug
```

The APK appears at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Install on your Android phone

1. Copy `app-debug.apk` to the phone (USB, email it to yourself, or Google Drive).
2. Tap the file. Android will ask to allow **"Install unknown apps"** for whatever
   app you opened it from (Files / Chrome / Drive) — allow it.
3. Open **Fuzzy Contacts**, grant the contacts permission when asked, and search.

> This is a *debug* APK signed with the standard debug key — perfect for installing
> on your own phone. (For Play Store distribution you'd build a signed *release*
> APK instead, which we can add later.)

## Tuning the fuzziness

Open `app/src/main/java/com/example/fuzzycontacts/Phonetic.java` and adjust the
`allow` value in `tokenScore()` — higher tolerates more spelling differences (but
returns more results).
