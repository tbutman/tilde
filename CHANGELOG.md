# Changelog

## Unreleased (1.2)

Not released yet. Tested on the emulator.

### More than one card

- **Cards:** keep several, each a different you to share (work, personal, a project, an event).
  Each has its own name, title, photo, handle, phone numbers, links, WhatsApp greeting, what a tap
  shares, quick-switch row, and a cover colour. Labels such as "Work" are only for you.
- **Switching:** tap `~/` at the top of the Share screen (it shows the card's label once you have
  more than one) or the card itself, or swipe the card sideways to step through your cards.
- **New card:** copy the current card (photo and links included, then change what differs) or
  start blank with just a name.
- **Manage cards:** rename, recolour, duplicate, reorder and delete. The last card can't be
  deleted.
- **Settings** is split into **This card** (with the card's label and a Switch button) and **All
  cards** (Share over NFC, the event tag, guest Wi-Fi, writing stickers, shortcuts, About).
- **Add a link** suggests links from your other cards, so you don't have to type them again.
- **Met** shows which card was shared, and the CSV export has a `card` column.
- The Quick Settings tile shows the card as well as what it shares ("Work · LinkedIn").
- Updating from 1.1 keeps everything: your profile, photo, links and choices become your first
  card, "My card".

### Write a sticker

- **Write a card** is now **Write a sticker** (Settings → Stickers and cards), now that "card"
  means one of your cards. It writes the active card, and says which one when you have several.
  Stickers already written keep what's on them.

## 1.1.0 (7 October 2026)

A new first launch, saved links and quicker switching. It installs over 1.0.0 and keeps your card
(tested with 1.1.0-beta.1, updating through Obtainium).

### First launch

- A new welcome in four short steps, with Back and Next always at the bottom and progress dots:
  what Tilde is (including the NFC card or sticker option), your card, how people reach
  you, and how you'll share.
- A live preview of your card as you type your name and title, with your photo.
- Clearer fields: **Full name**, **Job title** (with an example) and **Link to share** (your
  website, portfolio or LinkedIn; leave it empty and a tap shares your contact card), each with an
  icon. Android's autofill can now fill in your
  name, email and phone number.
- Phone numbers have a country picker (flag and dialling code, set from your SIM, searchable by
  name or code), format as you type, and are saved in international format. Add a second number
  on the spot; two numbers from different countries are labelled, for example "mobile (US)".
- Checks before moving on: a name is needed, and an email must look like one.
- The last step asks how you'll share: just your phone, or your phone and a card or sticker. If you
  don't have a card, a link shows how to print your own (the design is free).
- **Your card is ready:** a short panel when setup finishes, with the three things to know (hold
  phones back to back, change what you share, Receive and Met). If you chose a card or sticker, it
  offers **Write my card**, and Write a card has **Skip for now** in case it hasn't arrived yet
  (write it any time from Settings).
- **Tilde handle:** the name after `~/` at the top of your card, set up on the "Make your card"
  step and shown in the preview. It follows your name (`~/janedoe`) until you change it: lower-case
  letters, numbers, dots, dashes and underscores, up to 20. It used to start empty, showing a bare
  `~/`; an empty handle now shows `~/tilde`. Called **Tilde handle** in Settings too.

### Sharing

- **Saved links** replace the single custom link: save as many as you like, each with a name (for
  example "Tilde → tbutman.com/tilde"), and each becomes its own option. Add them from the options
  list (**Add a link**) or under Settings → Links, where you can also change or delete them. A
  custom link from 1.0 becomes your first saved link automatically.
- **Quick switch:** chips under the QR code for your favourite options, one tap each, or swipe the
  code to step through them. Star options in the options list (or tick saved links in Settings) to
  choose which appear; an unticked link stays saved without showing on the Share screen.
- The options list puts your starred options first and folds the rest under **More options**, so
  it stays short however many links you save.
- Options that aren't set up yet (Guest Wi-Fi, a social link, WhatsApp) are shown greyed out with
  **Set up**, which asks for the missing link or number on the spot, or opens the right part of
  Settings. Before, an empty custom link could be chosen and shared nothing.
- A line under the code says what it opens ("Opens tbutman.com/tilde"), for the person scanning.
- Tap the code to show it full screen, for scanning from further away.
- **Send** the current link, or your contact card as a file, through any app (WhatsApp, email,
  messages), and **Copy link**. Not offered for Guest Wi-Fi, so its password stays out of other apps.

### Profile and contact card

- A LinkedIn, GitHub, Instagram or X link given as the link to share now goes in its own field,
  and a tap shares it under its own name.
- Fixed: a LinkedIn (or other social) profile entered as the website went on the contact card as
  just `linkedin.com`, and event tags were added to its links. It now stays whole, without tags.
- iPhones can't save a contact card from a tap, so the card also carries a link for them to open.
  That was always the website; with no website, it's now your first social profile (LinkedIn,
  GitHub, Instagram or X), and every note about it says what an iPhone will open, or that a tap
  does nothing when there's no link at all.
- An optional second email in Settings, for example work and personal; both go on the contact card.
- Settings says **Full name** and **Job title**, to match.
- Fixed: on a card without a job title, the name sat against the card's bottom edge.

## 1.1.0-beta.1 (7 October 2026)

A pre-release of 1.1.0 with the same changes, published to test the update from 1.0.0 before
release.

## 1.0.0 (5 October 2026)

The first public release.

Installing over a test build of Tilde: uninstall that first, because a release is signed with a
different key and Android won't install it over the top. From here on, updates install over each
other as normal, including from 1.0.0-beta.1.

- Share your website, contact card, WhatsApp, LinkedIn, GitHub, Instagram, X, any link or guest
  Wi-Fi with a tap, or as a QR code, switching between them with one tap.
- A welcome screen sets up your card on first launch; everything is editable under Settings.
- Profile photo, picked with the system photo picker and cropped in the app.
- Met: a list of everyone you've shared with, with notes and CSV export.
- Event tags on links to your own website.
- Write a card: put your link, contact card or guest Wi-Fi on an NFC sticker or a printed card.
- Receive: read NFC tags, cards and other phones running Tilde.
- Quick Settings tile.
- Free and open source (MIT): no account, no internet permission, no analytics and no cloud backup.

## 1.0.0-beta.1 (5 October 2026)

A beta of the first public release. Sharing by tap and QR code, and the Met list, are tested on an
Android phone with an iPhone reading. Still to test on real hardware: **Write a card** with an NFC
sticker, tapping between two Android phones, and the welcome screen and profile photo (so far tried
on the Android emulator).

Installing over an earlier test build of Tilde: uninstall that first. A release is signed with a
different key, so Android won't install it over the top. From this beta on, updates install over
each other as normal.

- Share your website, contact card, WhatsApp, LinkedIn, GitHub, Instagram, X, any link or guest
  Wi-Fi with a tap, or as a QR code.
- A welcome screen sets up your card on first launch; everything is editable under Settings.
- Profile photo, picked with the system photo picker and cropped in the app.
- Met: a list of everyone you've shared with, with notes and CSV export.
- Event tags on links to your own website.
- Write a card: put your link, contact card or guest Wi-Fi on an NFC sticker or a printed card.
- Receive: read NFC tags, cards and other phones running Tilde.
- Quick Settings tile.
- No internet permission, no analytics and no cloud backup.
