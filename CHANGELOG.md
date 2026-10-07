# Changelog

## 1.2.0 (unreleased)

More than one card, a reorganised Settings with new options, backup and restore, a light theme, a
home-screen widget, and Tilde in Portuguese. Installing it over 1.1 keeps everything: your profile,
photo, links and choices become your first card, "My card".

### More than one card

- **Cards:** keep several, each a different you to share (work, personal, a project, an event).
  Each has its own name, title, company, photo, handle, phone numbers, links, WhatsApp greeting,
  what a tap shares, quick-switch row and cover colour. Labels such as "Work" are only for you.
- **Switching:** tap `~/` at the top of the Share screen (it shows the card's label once you have
  more than one) or the card itself, or swipe the card sideways to step through your cards.
- **New card:** copy the current card (photo and links included; then change what differs in Edit
  card) or start blank with just a name.
- **Manage cards:** rename, recolour, duplicate, reorder and delete. The last card can't be
  deleted, and stickers you've already written keep what's on them.
- **Choose what each card gives away:** untick details a card's contact card should leave out, such
  as your phone number on a card for events. Taps, the QR code, Send and the link iPhones open all
  follow it. **Preview** in Edit card shows the contact card as it will be saved.
- **Add a link** suggests links from your other cards, so you don't have to type them again.
- **Met** shows which card was shared, and the export has a `card` column.
- The Quick Settings tile shows the card as well as what it shares ("Work · LinkedIn").

### Settings, reorganised

- Settings is a short list in three groups: **This card** (tap your card to edit it, Your cards),
  **All cards** (Sharing, Guest Wi-Fi, Met, Write a sticker) and **App** (Backup and restore,
  Theme, Language, Quick Settings tile, About). Each row shows its current setting.
- **Edit card** has everything on a card in sections: Card (photo, name, title, company, handle),
  Contact (emails and phone numbers), Links (website, social profiles, saved links) and WhatsApp.
  Open it from Settings, with the pencil on your card on the Share screen, or by long-pressing the
  card.
- **Links are checked as you type,** here and in the welcome: anything that isn't a link says
  "That doesn't look like a link". A bare address gets `https://`, and "@janedoe" in a LinkedIn,
  GitHub, Instagram or X field becomes your profile link.
- **Phone numbers** in Edit card use the country picker from the welcome, with up to three
  numbers. Numbers saved before keep their country, and labels you typed yourself stay.
- **WhatsApp** has its own country picker and **Same as my mobile**, and its default greeting is
  in the app's language.
- The keyboard no longer covers the field you're typing in, in Edit card or any Settings page.
- **Set up** on Guest Wi-Fi or the contact card opens the right page directly.
- **About** has the version, a privacy note, links to the website, the privacy page, the source
  code and **Report a problem**, and the open-source licences.

### New settings

- **Tilde in Portuguese** (European Portuguese). Settings → Language chooses the phone's language,
  English or Português; Android 13 and later also list Tilde in the phone's own language settings.
  The translation still needs a native speaker's review.
- **Light theme:** Settings → Theme: Dark (as before, the default), Light, or follow the phone's
  setting.
- **Backup and restore:** save everything (cards, photos, Met and settings) to a file you keep, and
  restore it on a new phone, from Settings or with **Restore a backup** on the first welcome screen.
  The guest Wi-Fi password is only included if you tick it. The file isn't encrypted, so keep it
  somewhere private. A damaged file changes nothing. Tilde still has no cloud backup and no storage
  permission: you choose where the file goes.
- **Answer taps when Tilde is closed** (Settings → Sharing, off by default). Off, taps only share
  while Tilde is on screen, so nothing goes out from a pocket by accident; before, Android could
  route a tap to Tilde with the screen on even when it was closed. On, taps also work with Tilde
  closed while the phone is unlocked, never from the lock screen.
- **Share by tap** is the new name for Share over NFC. While it's off, the status badge says
  **Taps off**.
- **The event tag is now the event name,** and it clears itself at the end of the day you set it,
  so yesterday's event doesn't end up in today's links. You can switch that off.
- **Your photo in a sent contact card,** if you switch it on (Settings → Sharing). A tap or the QR
  code never includes it.
- **Share screen switches** (Settings → Sharing): full brightness, keeping the screen on, and
  vibrating when a tap is read. All on, as before, until you change them.
- **Met** (Settings → Met): ask for a note after each tap, and delete entries older than 3, 6 or 12
  months. Choosing a shorter time says how many people that deletes, and asks first.
- **Delete all data** (Settings → About) removes everything, as uninstalling would.

### Share screen

- What a tap shares and the quick-switch chips stay above the bottom bar, and whatever is being
  shared always has a chip. The QR code is a little smaller so everything fits on one screen, and
  Send and Copy link are icons beside the "Opens …" line.
- Tap the status badge (Ready, NFC off, Taps off, No NFC) to see what it means, with the fix one
  tap away: turn on NFC, or turn Share by tap on or off. On a phone without NFC, it says to share
  with the code instead.
- **Guest Wi-Fi is shared once:** after a tap, or when you leave the Share screen, your card goes
  back to what it shared before. Its Settings page says plainly that the Wi-Fi code contains the
  password.
- A long handle or label is shortened so the card switcher's ▾ always shows, and a code too long
  for a QR code says "Too long for a QR code. Shorten your link or details."

### Write a sticker

- **Write a card** is now **Write a sticker** (Settings → Write a sticker), now that "card" means
  one of your cards. It puts your link on an NFC sticker or a Tilde card, from the active card,
  and says which card when you have several.
- **Lock it after writing** (off unless ticked and confirmed), so nobody can overwrite the sticker.
  Tilde says so if a sticker can't be locked.
- It says what anyone who taps the sticker gets ("…gets your phone number and email", "…can join
  your Wi-Fi"), and says it again before locking.

### Home-screen widget

- **Tilde QR code:** a card's QR code on your home screen. Choose the card when you add it, or "the
  active card" to follow whichever is active; tap it to open Tilde on that card. It updates
  whenever you change something in Tilde, and drops the event name just after midnight. Choosing a
  card whose code is your contact card or guest Wi-Fi reminds you that anyone who sees your home
  screen can scan it.

### Met and Receive

- **Met keeps everyone:** the 500-entry limit is gone.
- **One entry per tap:** a phone that reads several times in one tap counts once, and two people
  who tap one after the other are both listed. **Sent** shows once per tap too, and About's count is
  now "Shared by tap … times".
- **Export sends a CSV file** (`tilde-met-YYYY-MM-DD.csv`) that opens in a spreadsheet app.
- **Receive** says what to do on a phone without NFC (scan their code instead) or with NFC off
  (with **Turn on**), and empty Met and Receive lists say how to fill them.

### Welcome

- The first screen says Tilde is free, with no account and no internet, and what a tap gives
  Android phones and iPhones. It also offers **Restore a backup** for moving from another phone.
- The handle shows as it will appear ("Shown as ~/janedoe at the top of your card"), and the
  last step says "NFC sticker" and "Tilde card", with **No Tilde card yet? Print your own**.

### Polish

- **Logos for saved links on known sites:** Bluesky, Threads, Facebook, YouTube, TikTok, Mastodon,
  Medium, Substack, Dribbble, Behance, Calendly, Telegram, Discord, Product Hunt, Stack Overflow,
  GitLab and Figma (and LinkedIn, GitHub, Instagram, X and WhatsApp links), instead of the first
  letter of the link's name.
- The cover colours are easier to tap and are read aloud by name, and the pencil on your card is
  bigger and easier to see.
- A card always keeps a name: clearing it in Edit card keeps the last one, with a note.
- Name, title and company take up to 100 characters, and links and Met notes up to 500.

## 1.2.0-beta.3 (7 October 2026)

A third 1.2 beta with the changes from a product review of 1.2.0-beta.2; everything in the earlier
betas below still applies. Like them, it's a pre-release: Obtainium offers it only with **Include
prereleases** turned on. It's for testing on a real phone before 1.2.0: taps with an iPhone and an
Android phone, Met entries, answering taps with Tilde closed, one-off Guest Wi-Fi, Write a sticker
and locking, the widget, and the Met export.

Changes:

- **Share screen:** what a tap shares and the quick-switch chips stay above the bottom bar, and
  whatever is being shared always has a chip. The QR code is a little smaller so everything fits on
  one screen, and Send and Copy link are icons beside the "Opens …" line. A long handle or label is
  shortened so ▾ always shows.
- **Share by tap** is the new name for Share over NFC (Settings → Sharing). While it's off, the
  status badge says **Taps off**. On a phone without NFC, the status dialog says to share with the
  code instead.
- **Answer taps when Tilde is closed** now also works when Tilde was last left on Receive.
- **The event tag is now the event name.**
- **Guest Wi-Fi is shared once:** after a tap, or when you leave the Share screen, your card goes
  back to what it shared before. Its Settings page says plainly that the Wi-Fi code contains the
  password.
- **Links are checked as you type,** in Edit card and the welcome: anything that isn't a link says
  "That doesn't look like a link". A bare address gets `https://`, and "@janedoe" in a LinkedIn,
  GitHub, Instagram or X field becomes your profile link.
- **WhatsApp** has a country picker and **Same as my mobile**, and its default greeting is in the
  app's language.
- **The keyboard no longer covers the field you're typing in,** in Edit card or any Settings page.
- **Preview** in Edit card shows your contact card as it will be saved.
- **Met keeps everyone** (the 500-entry limit is gone) and lists one entry per tap, so two people
  who tap one after the other are both listed. **Sent** shows once per tap too, and About's count
  is now "Shared by tap … times".
- **Met's export sends a CSV file** (`tilde-met-YYYY-MM-DD.csv`). Choosing a shorter "Delete
  entries older than" says how many people that deletes, and asks first.
- **Write a sticker** says what anyone who taps the sticker gets ("…gets your phone number and
  email", "…can join your Wi-Fi"), and says it again before locking. It says "NFC sticker" and
  "Tilde card" throughout, as does the rest of Tilde.
- **The widget is called Tilde QR code.** Choosing a card whose code is your contact card or guest
  Wi-Fi reminds you that anyone who sees your home screen can scan it.
- **Receive** says what to do on a phone without NFC (scan their code instead) or with NFC off
  (with **Turn on**), and empty Met and Receive lists say how to fill them.
- **Welcome:** the first screen says Tilde is free, with no account and no internet, and offers
  **Restore a backup** for moving from another phone. The handle shows as it will appear on your
  card.
- **About** links to the website, the privacy page and **Report a problem**, and lists the
  open-source licences. A backup that was cut short says it's damaged, and nothing changes.
- **Cards:** the cover colours are easier to tap and are read aloud by name, and the pencil on your
  card is bigger and easier to see. Name, title and company take up to 100 characters, and links
  and Met notes up to 500. A code too long for a QR code says so instead of crashing.

## 1.2.0-beta.2 (7 October 2026)

A second 1.2 beta with fixes from a review of 1.2.0-beta.1; everything in 1.2.0-beta.1 below still
applies. Like it, it's a pre-release: Obtainium offers it with **Include prereleases** turned on.

Fixes:

- **Restoring a backup is safer:** a damaged or edited file can't store settings the app can't
  read, and if restoring fails nothing changes (your photos stay). A restored theme applies
  straight away, an event tag keeps the day it was set, and a backup without the Wi-Fi password
  no longer pairs another network with this phone's password. Problems are explained in Portuguese
  too.
- **Taps never answer from the lock screen,** even without a PIN or while Smart Lock keeps the
  phone unlocked.
- **Taps, the tile and the widget share the same thing as the Share screen** when a card's chosen
  option isn't set up (they fall back to its first ready one).
- **Long-press your card on the Share screen** to edit it, as described below (it didn't work).
- **The event tag** set before 1.2 now clears itself too, isn't cleared early after flying west, and
  the widget drops it just after midnight.
- **A card always keeps a name:** clearing it in Edit card keeps the last one, with a note.
- **Delete all data** also removes contact cards made for Send and resets the language.
- A sticker that took your link but couldn't be locked says so, instead of "failed".
- No crash when the "who was it?" note opens just as the screen rotates or closes; reopening Tilde
  from Recents no longer switches back to a widget's card.

## 1.2.0-beta.1 (7 October 2026)

A beta of 1.2: more than one card, a reorganised Settings with new options, a light theme, a
home-screen widget, and Tilde in Portuguese. It's a pre-release, so Obtainium only offers it with
**Include prereleases** turned on, and the download link on tbutman.com/tilde stays on 1.1.0.
Installing it over 1.1 turns your profile into your first card, "My card", with everything kept;
this beta is for testing that, and the features that need a real phone (answering taps with Tilde
closed, locking stickers, the widget).

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

### Settings, reorganised

- **Settings is now a short list.** Your card (tap to edit it), then Your cards, Sharing, Guest Wi-Fi,
  Write a sticker, the Quick Settings tile and About. Each row shows what it's set to now, such as
  "Taps on · no event tag" or your Wi-Fi network's name, and opens its own page.
- **Edit card** has everything on a card in sections: Card (photo, name, title, handle), Contact
  (emails and phone numbers), Links (website, social profiles, saved links) and WhatsApp. Open it
  from Settings, with the pencil on your card on the Share screen, or by long-pressing the card.
- **Phone numbers** in Edit card use the country picker from the welcome, with up to three
  numbers. Numbers saved before keep their country, and labels you typed yourself stay.
- **About** has the version, a short privacy note and a link to the source code.
- **Set up** on Guest Wi-Fi or the contact card opens the right page directly.

### New settings

- **Tilde in Portuguese** (European Portuguese). Settings → Language chooses the phone's language,
  English or Português; Android 13 and later also list Tilde in the phone's own language settings.
  The translation still needs a native speaker's review.
- **Company:** a field next to your job title, shown on your card ("Product designer · Acme") and on
  the contact card.
- **What each card's contact card includes:** untick details a card should leave out, such as your
  phone number on a card for events. Taps, the QR code, Send and the link iPhones open all follow it.
- **Backup and restore:** save everything (cards, photos, Met and settings) to a file you keep, and
  restore it on a new phone. The guest Wi-Fi password is only included if you tick it. Tilde still
  has no cloud backup and no storage permission: you choose where the file goes.
- **The event tag clears itself** at the end of the day you set it, so yesterday's event doesn't
  tag today's links. You can switch that off.
- **Delete all data** in About, which removes everything as uninstalling would.
- **Share screen switches** (Settings → Sharing): full brightness, keeping the screen on, and
  vibrating when a tap is read. All on, as before, until you change them.
- **Met** (Settings → Met): ask for a note after each tap, and delete entries older than 3, 6 or 12
  months.

### Polish

- **Logos for saved links on known sites:** Bluesky, Threads, Facebook, YouTube, TikTok, Mastodon,
  Medium, Substack, Dribbble, Behance, Calendly, Telegram, Discord, Product Hunt, Stack Overflow,
  GitLab and Figma (and LinkedIn, GitHub, Instagram, X and WhatsApp links), instead of the first
  letter of the link's name.

- **Your photo in a sent contact card,** if you switch it on (Settings → Sharing). A tap never
  includes it, so it stays quick to read.

- **Light theme:** Settings → Theme: Dark (as before, the default), Light, or follow the phone's
  setting.

- **Answer taps when Tilde is closed** (Settings → Sharing, off by default). Off, taps only share
  while Tilde is open, so nothing goes out from a pocket by accident; before, Android could route a
  tap to Tilde with the screen on even when it was closed. On, taps also work with Tilde closed while
  the phone is unlocked (never from the lock screen).

- **Lock a sticker after writing it** (Write a sticker, off unless ticked and confirmed), so nobody
  can overwrite it. Tilde says so if a sticker can't be locked.

- **Home-screen widget:** a card's QR code on your home screen. Choose the card when you add it, or
  "the active card" to follow whichever is active; tap it to open Tilde on that card. It updates
  whenever you change something in Tilde.

### Share screen

- Tap the status badge (Ready, NFC off, Paused, No NFC) to see what it means, with the fix one tap
  away: turn on NFC, resume or pause taps.

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
