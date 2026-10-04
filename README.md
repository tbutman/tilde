# Tap to share

An Android app that makes the phone act as an NFC tag. With the app open and the screen on,
another phone tapped against it reads `https://tbutman.com/hello` and offers to open it, just as
it would from the [business card](../business-card).

It uses Host Card Emulation: Android routes readers that select the NFC Forum NDEF application
(AID `D2760000850101`) to `NdefHceService`, which answers as a read-only NFC Forum Type 4 Tag
holding one URI record.

- **Screen:** an on/off switch, the link (editable), a QR code fallback for phones with NFC off,
  and a count of how many times a reader has read the link from this phone.
- **Permissions:** only `NFC`. There is no network access, no analytics and no AndroidX; the one
  library is ZXing core, for the QR code.
- **While open**, the app keeps the screen on, asks Android to prefer its service for the AID,
  and on Android 15+ stops the phone polling as a reader, so two phones back to back don't both
  act as readers.
- **Keep the app open when sharing.** Other apps can claim the same NDEF application ID (on
  Thomas's phone: X and Meshtastic). Android only routes to this app first while it is in the
  foreground.

## Build and install

Needs JDK 17 and the Android SDK (platform 36). On this Mac they came from Homebrew
(`openjdk@17`, `android-commandlinetools`), with `sdk.dir` in `local.properties`.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`adb` is in `/opt/homebrew/share/android-commandlinetools/platform-tools/`. The phone needs
Developer options → USB debugging (or Wireless debugging) turned on.

## Tests

`Type4TagTest` runs the full reader exchange on the JVM: select the NDEF application, read the
capability container, read the NDEF length and then the message, including long messages read in
chunks. It also checks the exact bytes of the URI record and the refusals (no file selected,
writes, unknown commands).

These check the protocol, not the radio. Whether a given phone reads it is only known by tapping.

## Tap test log

| Date | This phone | Other phone | Result |
| --- | --- | --- | --- |
| 2026-10-04 | Nothing A059, Android 16 | — | Installed. Launches with no errors; Android lists the service as the preferred foreground service for the AID, and polling turns off while the app is open (`updateDiscoveryTechnology: pollTech=0x0`). No tap test yet. |
| 2026-10-04 | Nothing A059, Android 16 | iPhone | Works: the iPhone offered to open the link. The app counted 6 complete reads. |
