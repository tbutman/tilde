# Tilde

**Your business card, on your phone.** Hold your Android phone against someone else's and they get
your website, contact details, WhatsApp or LinkedIn, just as if you'd handed them an NFC business
card. Or they scan the code on your screen.

**Free and open source** (MIT licence). Website: [tbutman.com/tilde](https://tbutman.com/tilde). No account, sign-up, subscription, payment or ads, and no
internet: Tilde can't go online, and your details stay on your phone until you share them.

<p>
  <img src="docs/screenshots/share.png" width="240" alt="The Share screen: Jane Doe's card, a QR code and what a tap shares">
  <img src="docs/screenshots/picker.png" width="240" alt="Choosing what a tap shares: website, contact card, WhatsApp, LinkedIn, GitHub, saved links or guest Wi-Fi">
  <img src="docs/screenshots/met.png" width="240" alt="The Met tab: people you've shared with, with notes">
</p>

## What it does

- **Tap to share.** The other phone doesn't need Tilde or any other app. Phones read it the way they
  read an NFC tag.
- **Or scan.** The same thing is on screen as a large QR code, for phones without NFC or people
  who'd rather scan.
- **Switch what you share in one tap,** to suit who you're talking to: tap one of the chips under
  the QR code (or swipe the code), or tap the line below them for every option:
  - **Contact card:** your name, title, phone numbers, email, website and social links all at once,
    ready to save to their contacts. (It's a vCard, the standard format every phone's contacts app
    understands.)
  - **A link:** your website, LinkedIn, GitHub, Instagram, X, or links you save yourself, such as
    your projects. Each saved link is its own option, with its own name.
  - **WhatsApp:** opens a chat with you, with a greeting typed and ready to send.
  - **Guest Wi-Fi:** joins your network without anyone typing the password.
- **Send it instead.** For someone who isn't in front of you, send the link or your contact card
  through WhatsApp, email or messages, or copy the link. Tilde stays offline: the app you choose
  does the sending.
- **Remember who you met.** Every tap is listed under **Met** with the time and what you shared.
  Add a note so you remember who they were, and export the list as a spreadsheet (CSV).
- **Events.** Add an event name and the links to your own website carry it, so you can see which
  event a visit came from.
- **Write a card.** Put your link or your whole contact card on an NFC sticker or a printed NFC
  business card, so it works even when your phone isn't there. Tilde never locks a tag, so you can
  change it later.
- **Receive.** Read other people's NFC cards, tags and phones running Tilde.

## Install

Tilde isn't in the Play Store yet, so you install it from this page. It takes about a minute and
you **don't** need developer mode or any special settings: Android just asks you twice to confirm.

Tilde needs Android 8.0 or newer. Tapping phones needs NFC; the QR code works on any phone.

1. **Download it.** On your Android phone, open the
   [latest release](https://github.com/tbutman/tilde/releases/latest) and, under **Assets**, tap
   the file ending in `.apk`. If your browser warns that this type of file can harm your device,
   tap **Download anyway**: it says that about every app downloaded outside the Play Store.
2. **Let your browser install apps (once).** Open the downloaded file. Android says your browser
   isn't allowed to install unknown apps: tap **Settings**, turn on **Allow from this source**, then
   go back.
3. **Install it.** Tap **Install**. Google Play Protect may say it doesn't recognise the developer,
   because Tilde isn't in the Play Store: choose to install anyway (on some phones that's under
   **More details**). If it offers to scan the app first, that's fine too.
4. **Open Tilde** and follow the welcome screens.

The exact wording of these prompts varies a little between phones and Android versions.

**Is it safe?** Every release is built from this code by GitHub Actions and signed with the same
key, and Android only installs an update over Tilde if it's signed with that key, so nobody else can
replace your copy. Tilde has no internet permission, so it can't send your details anywhere. To
check a download yourself (for example with [AppVerifier](https://github.com/soupslurpr/AppVerifier)),
the package is `com.tbutman.tilde` and the signing certificate's SHA-256 fingerprint is:

```
84:5F:25:41:BD:70:77:34:EC:97:76:23:90:BE:B1:0B:9D:0A:5C:64:AF:0D:B4:27:A6:94:99:86:73:A0:38:D9
```

### Updates

- **Automatically:** install [Obtainium](https://obtainium.imranr.dev), tap **Add app** and enter
  `https://github.com/tbutman/tilde`. It checks for new versions and offers to install them.
- **By hand:** repeat steps 1 and 3 with the new version. It installs over the old one and keeps
  your card and everything else.

## Using it

1. Open Tilde. The Share screen is what the other person sees.
2. Hold the backs of the two phones together for a second or two. Their phone shows your link or
   your contact card. Tilde buzzes and shows **Sent** once it's been read.
3. Tap the line under the QR code to change what a tap shares.

Things worth knowing:

- **Keep Tilde open while you share.** Some other apps also answer NFC taps, and Android only gives
  Tilde priority while it's on screen. A Quick Settings tile (in Settings) opens it in one swipe.
- **iPhones only act on links from a tap.** If you share your contact card, a tapping iPhone opens
  your website instead; the QR code saves the contact on any phone. Guest Wi-Fi is the same: an
  iPhone joins by scanning the code. The Share screen tells you when this applies.
- **The other phone needs NFC switched on.** Most Android phones have it in Quick Settings. iPhones
  read without any setting.

## Optional: a printed card

Tilde works on its own; you don't need a card. If you have a 3D printer and want something to hand
out too, there's a free, open-source business card designed to go with it: a QR code on the front,
and optionally an NFC tag sealed inside. Customise it with your name and colours, print it, then
use **Settings → Write a card** to put your link or contact card on the tag. See
[tbutman/tilde-card](https://github.com/tbutman/tilde-card).

## Privacy

Tilde only asks for NFC and vibration. It has no internet permission, so it can't send anything
anywhere: no analytics, no ads, no accounts. Your profile, photo and the Met list are stored only
on your phone, and Tilde opts out of Android's cloud backup, so they aren't copied to Google either.
Your photo is never shared; it only appears on your own screen. Uninstalling Tilde deletes
everything.

## Questions

**The other phone doesn't react.** Check that NFC is on for both phones, that Tilde is open on
screen, and try moving the phones slowly: the NFC antenna is usually near the camera or in the
middle of the back. Thick or metal cases can block it.

**Can I use it without NFC?** Yes. The QR code shows the same thing and works with any camera.

**Is there an iPhone version?** No. iPhones don't let apps act as an NFC tag, so Tilde is Android
only. iPhones can read from it, though.

## Development

Tilde is written in Kotlin with no network code and few dependencies. See
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for how it works, building, tests and releases, and
[CHANGELOG.md](CHANGELOG.md) for what's changed.

## Licence

[MIT](LICENSE) © Thomas Butman.

The WhatsApp, LinkedIn, GitHub, Instagram and X logos come from [Simple Icons](https://simpleicons.org)
(CC0) and are used only to link to your own accounts. They remain their owners' trademarks.
