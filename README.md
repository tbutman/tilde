# Tap to share

An Android app that makes the phone act as an NFC tag. With the app open and the screen on,
another phone tapped against it gets your website, contact card, WhatsApp, a social profile or
guest Wi-Fi, just as it would from an NFC business card (like [this one](../business-card)). The
same thing is on screen as a QR code for phones that would rather scan.

It uses Host Card Emulation: Android routes readers that select the NFC Forum NDEF application
(AID `D2760000850101`) to `NdefHceService`, which answers as a read-only NFC Forum Type 4 Tag.

- **Your profile:** name, title, email, website, handle, LinkedIn, GitHub, Instagram and X links,
  up to two phone numbers and a WhatsApp number, edited under **Settings → Profile** and stored
  only on the phone. Nothing personal is in the source. A build can seed the profile on first
  launch from `profile.local.properties`, which git ignores (copy `profile.example.properties`);
  without it the app starts empty and the Share screen offers to set it up.
- **Layout:** four tabs in a bottom toolbar. **Share** is what the other person sees: your name,
  "Tap phones to share", a large QR code (at full screen brightness) and one line saying what a
  tap shares; tapping that line opens the picker. **Settings** holds everything else, so none of
  it is on show. A completed tap fills the screen with "Sent" and offers "Add a note".
- **What a tap shares:** only what the profile has: Website, Contact card, WhatsApp, LinkedIn,
  GitHub, Instagram, X, plus a custom link and guest Wi-Fi. An optional **event tag** adds
  `?event=<tag>` to links on your website only, so its access log shows which event a visit came
  from.
- **Works on every phone, with two iPhone exceptions.** iPhones only act on links from a tap. The
  **contact card** sends a vCard and then your website as a second record: Android offers to
  save the contact, an iPhone opens the website (tested 5 October 2026), and the on-screen QR
  code (a shorter card) saves the contact on either. **Guest Wi-Fi** sends the Wi-Fi Alliance's
  NFC credential, which Android offers to join; a tap does nothing on an iPhone, but the `WIFI:`
  QR code joins on both. The Share screen says so when one of these is selected.
- **WhatsApp** sends a `https://wa.me/<number>` click-to-chat link. "Hi" and your first name are
  typed into the chat but never sent, and stay as a draft if they leave; the text is editable,
  and blank opens an empty chat. WhatsApp has no "add contact" link (it reads the phone's
  contacts), so to be saved, share the contact card.
- **Met:** each completed tap is logged on the phone (time, event, what was shared), with notes
  and CSV export. **Receive** turns the phone into a reader for tags, NFC business cards and other
  Android phones running the app.
- **Quick Settings tile:** "Add to Quick Settings" asks Android to add a tile that opens the app.
- **Icons:** the WhatsApp, LinkedIn, GitHub, Instagram and X logos come from Simple Icons (CC0;
  LinkedIn from version 13.21.0, the last to include it), unaltered, and are used only to link to
  the profile's own accounts, as each brand's guidelines allow. They remain their owners'
  trademarks. The paths are rewritten so Android's parser accepts them (see `VectorPathTest`).
- **Permissions:** `NFC` and `VIBRATE`. No network access and no analytics. Libraries: Material
  Components (with AppCompat) for the interface, and ZXing core for the QR code.
- **While open**, the app keeps the screen on, asks Android to prefer its service for the AID,
  and on Android 15+ stops the phone polling as a reader, so two phones back to back don't both
  act as readers.
- **Keep the app open when sharing.** Other apps can claim the same NDEF application ID (on
  the author's phone: X and Meshtastic). Android only routes to this app first while it is in the
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
