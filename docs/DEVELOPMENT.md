# Development

## How it works

Tilde makes the phone answer as an NFC tag using Android's Host Card Emulation. Android routes
readers that select the NFC Forum NDEF application (AID `D2760000850101`) to `NdefHceService`,
which answers as a read-only NFC Forum Type 4 Tag (`Type4Tag`) serving one NDEF message (`Ndef`).
To the other phone it looks like any NFC sticker or business card, so it needs no app.

- **What a tap sends.** Links are URI records. The contact card is a vCard (MIME record) followed
  by the website as a URI record: Android offers to save the contact, while an iPhone, which only
  acts on links, opens the website. Guest Wi-Fi is the Wi-Fi Alliance's NFC credential record
  (`application/vnd.wfa.wsc`), which Android offers to join. The QR code carries the same thing,
  except that the contact card is a shorter vCard and Wi-Fi is a `WIFI:` string.
- **Routing.** While open, the app asks Android to prefer its service for the AID and, on Android
  15+, stops the phone polling as a reader, so two phones back to back don't both act as readers.
  Other apps can register the same AID (on the author's phone, X and Meshtastic did), and Android
  only prefers Tilde while it is in the foreground.
- **Receive** uses reader mode to read NDEF from tags, NFC cards and other phones running Tilde.
- **Cards.** Each identity is a `Card` (`Cards.kt`): profile, saved links, share choice, stars,
  greeting, label and colour, stored as JSON. `Prefs.profile`, `links`, `share`, `pinned` and
  `whatsappGreeting` read and write the active card, so most code doesn't know about cards. Version
  1.1's single profile (and photo) becomes card 1 the first time cards are read. See
  `docs/specs/multiple-cards.md`.
- **Write a sticker** (`WriteActivity`, `TagWriter`; called Write a card before 1.2) uses reader
  mode too, and writes the active card's chosen option's message without the event tag (a printed card outlives the event). It formats blank
  tags, refuses locked ones and tags that are too small, and only makes a tag read-only when the
  owner ticks "Lock it after writing" (confirmed, since it's permanent).
- **Met.** `MetLog` turns the reads a tap produces (a phone often reads more than once) into one
  entry per person.
- **WhatsApp** sends a `https://wa.me/<number>?text=...` click-to-chat link; the greeting is typed
  into the chat but not sent.
- **Profile.** Everything personal is edited in the app and stored in SharedPreferences. Nothing
  personal is in the source.
- **Photo.** Picked with the system Photo Picker (no storage permission), cropped in `CropView`
  and kept in the app's private files. It is shown on screen only, never sent.
- **Logos** come from Simple Icons (CC0; LinkedIn from 13.21.0, the last version to include it; the
  sites saved links are matched to, in `Sites`, from 16.34.0), with their paths rewritten so
  Android's vector parser accepts them (see `VectorPathTest`). Brand colours are kept unless they're
  too dark for the dark theme (black logos are drawn in the text colour).

Permissions: `NFC` and `VIBRATE`. No `INTERNET` permission, no analytics, and cloud backup is off.
Dependencies: Material Components (with AppCompat), ExifInterface and ZXing core for the QR code.

## Build

Needs JDK 17 and the Android SDK with platform 36. Point Gradle at the SDK with `sdk.dir` in
`local.properties` or `ANDROID_HOME`.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

To start a debug build with your own details instead of the welcome screen, copy
`profile.example.properties` to `profile.local.properties` (git ignores it). It only seeds the
profile on first launch of a debug build. Release builds always start empty.
Debug builds are a separate app, **Tilde dev** (`com.tbutman.tilde.dev`), so one installs next to a
release of Tilde without touching it or its profile. Use it to try changes on a phone before
publishing anything. Its Settings has a **Show the welcome screens** button (debug builds only), which
clears the dev profile and starts the welcome again.

Only one app answers taps at a time: whichever is open, so open the one you're testing.

### Screenshots

The emulator has no NFC, so the Share screen would say "No NFC". Debug builds accept a `demo`
extra that draws it as on a phone with NFC switched on:

```bash
adb shell am start -n com.tbutman.tilde.dev/com.tbutman.tilde.MainActivity --ez demo true
```

The screenshots in `docs/screenshots` use the made-up profile Jane Doe and the system UI demo mode
for a clean status bar.

## Tests

`Type4TagTest` runs the full reader exchange on the JVM: select the NDEF application, read the
capability container, read the NDEF length and then the message, including long messages read in
chunks. It also checks the exact bytes of the URI record and the refusals (no file selected,
writes, unknown commands). `NdefTest`, `ProfileTest`, `FeaturesTest` and `MetLogTest` cover the
records, vCards, links and the Met log; `VectorPathTest` guards the logo paths.

These check the protocol, not the radio. Whether a given phone reads it is only known by tapping.

## Releases

Releases are built by GitHub Actions when a tag like `v1.0.0` is pushed
(`.github/workflows/release.yml`). The workflow runs the tests, builds a signed, shrunk APK and
attaches it to a GitHub release, which is also what Obtainium follows.

1. Update `versionCode` (always up by one) and `versionName` in `app/build.gradle.kts`, and add the
   version to `CHANGELOG.md`.
2. Commit, then tag and push: `git tag v1.0.0 && git push origin main v1.0.0`.

The tag must match `versionName` (`v` + version), or the workflow stops. A version with a hyphen,
such as `1.0.0-beta.1`, is published as a pre-release: GitHub doesn't show it as the latest
release, and Obtainium only offers it to people who turn on pre-releases. The release notes are
that version's section of `CHANGELOG.md`.

Signing uses one upload key, which must never be committed or lost: Android only installs an
update signed with the same key. It is read from `TILDE_*` environment variables (in CI, from
repository secrets) or from a git-ignored `keystore.properties`:

```properties
store_file=../tilde-release.jks
store_password=...
key_alias=tilde
key_password=...
```

| Secret | Value |
| --- | --- |
| `TILDE_KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `TILDE_STORE_PASSWORD` | keystore password |
| `TILDE_KEY_ALIAS` | key alias |
| `TILDE_KEY_PASSWORD` | key password |

Without signing settings, `./gradlew assembleRelease` still builds an unsigned APK.

## Tap test log

| Date | This phone | Other phone | Result |
| --- | --- | --- | --- |
| 2026-10-04 | Nothing A059, Android 16 | — | Installed. Launches with no errors; Android lists the service as the preferred foreground service for the AID, and polling turns off while the app is open (`updateDiscoveryTechnology: pollTech=0x0`). No tap test yet. |
| 2026-10-04 | Nothing A059, Android 16 | iPhone | Works: the iPhone offered to open the link. The app counted 6 complete reads. |
| 2026-10-05 | Nothing A059, Android 16 | iPhone | Contact card: the iPhone opened the website (the second record), as expected. |
