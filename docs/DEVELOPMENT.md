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
  greeting, label and color, stored as JSON. `Prefs.profile`, `links`, `share`, `pinned` and
  `whatsappGreeting` read and write the active card, so most code doesn't know about cards. Version
  1.1's single profile (and photo) becomes card 1 the first time cards are read. See
  `docs/specs/multiple-cards.md`.
- **Answering taps** (`TapGate`). Android routes readers to the service whenever the screen is on,
  even with Tilde closed, so `NdefHceService` asks `TapGate` first: Share by tap must be on, Receive
  must not be on screen, and either a Tilde screen is open (`TildeApp.open`) or the owner switched
  on "Answer taps when Tilde is closed", in which case the phone must also be unlocked. Otherwise
  it answers "file not found", and the reader sees nothing.
- **Write a sticker** (`WriteActivity`, `TagWriter`; called Write a card before 1.2) uses reader
  mode too, and writes the active card's chosen option's message without the event name (a Tilde
  card outlives the event). It formats blank tags, refuses locked ones and tags that are too
  small, and only makes a tag read-only when the owner ticks "Lock it after writing" (confirmed,
  since it's permanent).
- **Met.** `MetLog` logs one entry per tap: the first complete read of each card-emulation session
  (a phone often reads more than once per tap). A new session within 4 s (`SAME_TAP_MS`) that
  shares the same thing is the same phone coming back into range, not the next person. Met has no
  size limit; only "Delete entries older than" or the owner removes entries.
- **Event name.** Stored once for all cards (`Prefs.event`), added as `?event=` only to links to
  the card's own website, and cleared at the end of the day it was set unless the owner switches
  that off (`eventAutoClear`, with the day in `event_day`).
- **Backup** (`Backup.kt`). One JSON file (`"kind": "tilde-backup"`, `"version": 1`) with the
  cards, each card's photo as base64 JPEG, Met, Share by tap, the event name and its day, guest
  Wi-Fi (the password only when ticked) and an allow-list of settings with their types. It isn't
  encrypted. Restoring refuses other files, newer versions, files without cards and damaged ones
  before changing anything, ignores unknown settings, and only accepts plain card ids (they
  become photo file names). Receive history and the read count aren't included. Written and read
  through the system file picker, so there's no storage permission.
- **Widget** (`TildeWidget`, `WidgetConfigActivity`). The **Tilde QR code** widget draws one
  card's QR code, or the active card's; the choice is stored per widget id. It redraws whenever a
  Tilde screen closes (`TildeApp`), and, while a self-clearing event name is set, just after
  midnight with an inexact alarm (no permission needed).
- **Languages.** English (`values`) and European Portuguese (`values-pt`, informal "tu").
  `res/xml/locales_config.xml` lists them for Android 13+'s per-app language setting; Settings →
  Language sets it through `AppCompatDelegate.setApplicationLocales`, and
  `AppLocalesMetadataHolderService` keeps the choice on Android 12 and earlier. Strings that must
  not be translated are `translatable="false"`.
- **WhatsApp** sends a `https://wa.me/<number>?text=...` click-to-chat link; the greeting is typed
  into the chat but not sent.
- **Profile.** Everything personal is edited in the app and stored in SharedPreferences. Nothing
  personal is in the source.
- **Photo.** Picked with the system Photo Picker (no storage permission), cropped in `CropView`
  and kept in the app's private files, one per card (`photo-<cardId>.jpg`). It is never in a tap
  or the QR code; it's in a sent contact card only when the owner switches that on (Settings →
  Sharing, `sendPhoto`).
- **Logos** come from Simple Icons (CC0; LinkedIn from 13.21.0, the last version to include it; the
  sites saved links are matched to, in `Sites`, from 16.34.0), with their paths rewritten so
  Android's vector parser accepts them (see `VectorPathTest`). Brand colors are kept unless they're
  too dark for the dark theme (black logos are drawn in the text color).

Permissions: `NFC` and `VIBRATE`. No `INTERNET` permission, no analytics, and cloud backup is off.
The `android.hardware.nfc.hce` feature is optional (`required="false"`): without NFC the Share
screen says "No NFC" and the code still works, so stores shouldn't hide Tilde from those phones.
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

The screenshots in `docs/screenshots` (450×1000 PNG) use the made-up Jane Doe, with two cards
("Work" and "Web Summit"), and nothing of anyone real. The README shows `share.png`, `picker.png`
and `met.png`, and under More screenshots `cards.png`, `settings.png`, `write.png`, `widget.png`,
the light theme (`share-light.png`, `met-light.png`) and Portuguese (`share-pt.png`,
`picker-pt.png`, `met-pt.png`). They're taken at 1080×2400 with the text size at 100%, then
scaled down.

For a clean status bar, turn on the system UI demo mode before taking them, and exit it after:

```bash
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@"; }
demo enter
demo clock -e hhmm 0941
demo network -e wifi show -e level 4
demo network -e mobile hide
demo battery -e level 100 -e plugged false
demo notifications -e visible false
# ... take the screenshots ...
demo exit
```

## Tests

`./gradlew testDebugUnitTest` runs 13 classes on the JVM:

| Class | Covers |
| --- | --- |
| `Type4TagTest` | The full reader exchange: select the NDEF application, read the capability container, the NDEF length and the message (long ones in chunks); the URI record's exact bytes; the refusals (no file selected, writes, unknown commands). |
| `NdefTest` | The contact message (a long vCard record, then the link), labeled numbers, the shorter QR code card, a reader getting it in chunks. |
| `ProfileTest` | Profiles and their vCards: the seed file, escaping and labels, the iPhone link, handles, company, what a card leaves off, the photo in a sent card, link checking and @handles in Edit card. |
| `FeaturesTest` | Event names on links, Wi-Fi records and `WIFI:` codes, Receive decoding what Share sends and other tags, vCard parsing, WhatsApp links. |
| `LinksTest` | Saved links: options, readiness, JSON, the 1.0 custom link, link checking and @handles, known sites. |
| `CardsTest` | Cards: JSON, the migration from one profile, copying, deleting, moving, swiping, new colors and labels. |
| `CountriesTest` | The country table, flags, search, labels and saved numbers. |
| `MetLogTest` | One entry per tap (repeated reads, two taps 10 s apart, another card), no size limit, notes, the CSV. |
| `BackupTest` | The backup round trip, the Wi-Fi password, refused, damaged and hand-edited files, a missing active card, the event name expiring, dropping old Met entries. |
| `TapGateTest` | When a tap is answered: open, closed, locked, paused, on Receive. |
| `TagWriterTest` | Writing stickers: fit, locked tags and phones, locking only when asked, missing room. |
| `QrCodeTest` | Content over capacity gives no code instead of a crash; a contact card at the field limits fits. |
| `VectorPathTest` | The logo paths parse. |

These check the protocol, not the radio. Whether a given phone reads it is only known by tapping.

## Releases

Releases are built by GitHub Actions when a tag like `v1.0.0` is pushed
(`.github/workflows/release.yml`). The workflow runs the tests, builds a signed, shrunk APK and
attaches it to a GitHub release, which is also what Obtainium follows.

1. Update `versionCode` (always up by one) and `versionName` in `app/build.gradle.kts`, and add the
   version to `CHANGELOG.md`. A section written ahead of time as `## 1.2.0 (unreleased)` only
   needs its date: `## 1.2.0 (7 October 2026)`.
2. Commit, then tag and push: `git tag v1.0.0 && git push origin main v1.0.0`.

The tag must match `versionName` (`v` + version), or the workflow stops. A version with a hyphen,
such as `1.0.0-beta.1`, is published as a pre-release: GitHub doesn't show it as the latest
release, and Obtainium only offers it to people who turn on pre-releases. The release notes are
that version's section of `CHANGELOG.md` (the text under `## <version>`, up to the next `## `),
after a line for newcomers: "New to Tilde?" with the install steps on a stable release, a link to
the latest stable release on a pre-release. Each release has exactly one `.apk`
(`Tilde-<version>.apk`): the site's download link and Obtainium both look for it.

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
