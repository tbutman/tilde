# Spec: multiple cards

Status: **agreed, to build for 1.2** (7 October 2026). All decisions are recorded below.

## Why

People present themselves differently depending on who they're meeting: a work card at a conference, a personal one with friends, a side project at a meetup, or a card for one event. Today Tilde holds one profile, so switching context means editing your name, title and links each time. Multiple cards make Tilde a set of identities you switch between with one tap, which is where products like Blinq and Popl put their value. Tilde keeps its difference: no account, no internet, everything on the phone.

## What a card is

A card is a complete identity to share:

- **What others see:** name, job title, photo, Tilde handle (`~/handle`) and a cover colour.
- **What it shares:** emails, phone numbers, website, social links, WhatsApp number and greeting.
- **How it shares:** which option a tap shares by default, and which options appear in its quick-switch row.
- **A private label** that only you see, such as "Work", "Personal" or "Web Summit".

Exactly one card is **active**. The Share screen, taps, the QR code, Send/Copy, the Quick Settings tile and Write a card all use the active card.

## What belongs to a card, and what's shared by all of them

| Setting | Where it lives | Why |
| --- | --- | --- |
| Name, title, emails, phones, website, socials, WhatsApp number, handle | **Card** | They're the identity. |
| Photo | **Card** | A headshot for work, something else for personal use. |
| Cover colour | **Card** | Tells cards apart at a glance; good for a project or company's colour. |
| WhatsApp greeting | **Card** | It uses the card's first name ("Hi Thomas"). |
| What a tap shares; quick-switch stars | **Card** | Work shows LinkedIn and LabTrails; Personal shows Instagram. |
| Saved links | **Card** | Like the LinkedIn and website fields beside them. Each card is self-contained: editing one never changes another (see "Saved links: per card" below). |
| Guest Wi-Fi | **Shared** | It's about the place, not the person. Each card can still star it. |
| Event tag | **Shared** | It's about where you are now, whichever card you're using. It still only tags links to that card's own website. |
| Share over NFC on/off, the tile, the current tab | **Shared** | Phone behaviour, not identity. |
| Met | **Shared, with a Card column** | One list of people met; each entry records which card was shared. CSV export gets a `card` column. |
| Receive history, the read count | **Shared** | Not about which card you are. |

## How it works

### Saved links: per card

Each card has its own saved links, as it has its own LinkedIn link and website.

- **Consistency:** social links and the website are already per card, so saved links follow them.
- **Self-contained cards:** everything about "Work" lives in Work. Editing one card never changes another, and "This card" in Settings holds all of it.
- **Separation:** cards keep contexts apart (work, personal, a client). A link added for one never appears on another by accident.
- **Simpler:** links are stored inside the card; deleting a card deletes its links; there's no clean-up across cards.

The cost is re-entering a link that two cards share. Two things soften it:

- **Copy card** copies the links, and most new cards start as a copy.
- **Add a link** suggests links from your other cards ("From Work: LabTrails · labtrails.app"), so reusing one is a tap. The copy is independent from then on.

The 1.1 saved links move into card 1 during the upgrade.

### Switching cards

- **The `~/handle` line on the Share screen becomes a button.** It opens a sheet listing the cards (label, name and colour) with a check on the active one, plus **New card** and **Manage cards**.
- **Swipe the profile card sideways** to go to the next or previous card, like flipping through a wallet. This mirrors swiping the QR code to change what's shared: the top card is *who*, the code is *what*. It uses the same touch handling as the QR swipe (keeps sideways touches from the scrolling page, counts by distance).
- Switching is instant and recorded as the active card. A tap that's already in progress finishes with the card it started with.
- **Quick Settings tile:** the subtitle shows "Work · LinkedIn". A long press opens the app, as now.

### Creating a card

**New card** asks:

1. **Start from:**
   - **This card (copy).** Everything is copied, including the photo, stars and greeting; you then change what differs. Usually the quickest, since most details (name, phone) stay the same.
   - **Blank.** A short form like the welcome's "Make your card" and "How people reach you" steps.
2. **Label:** prefilled ("Work", "Card 2"), editable later.
3. The new card becomes active, and the Share screen shows it.

### Managing cards

A **Cards** screen, reached from the switcher's **Manage cards** and from Settings:

- Reorder by dragging, rename, change the colour, duplicate, or delete.
- **Delete** asks for confirmation and removes that card's photo. The last card can't be deleted.
- Deleting the active card makes the first remaining card active.

### Settings

Settings splits in two:

- **This card** (top, with the card's label and colour and a **Switch** button): profile, photo, handle, phone numbers, WhatsApp greeting, and its saved links (with their Share screen checkboxes).
- **All cards** (below): Guest Wi-Fi, event tag, Share over NFC, Stickers and cards (Write a sticker), shortcuts and About.

That makes clear which edits affect only this card.

### Welcome

Unchanged: it creates the first card, labelled "My card", with the colour picked automatically. A short line on the "Your card is ready" sheet mentions that you can add more cards (for work, personal or an event) from the `~/handle` button.

### Write a sticker (renamed from Write a card)

"Card" now means one of your cards, so the writing feature is renamed **Write a sticker**: "Put your link on an NFC sticker, or on a printed card with one inside." The printable product keeps its name, the **Tilde card**. The screen writes the **active card's** option, with a line saying which card ("From your Work card"). The tag keeps what was written: switching or editing the card later doesn't change tags already written. This is said once, on the screen.

## Edge cases

| Case | Behaviour |
| --- | --- |
| The selected option isn't available on the card switched to (say, no LinkedIn) | Fall back to the card's first ready option, as the Share screen already does. Each card remembers its own choice. |
| A saved link is deleted | Only from that card; its stars and selection fall back there. |
| A saved link is edited | Only on that card. A link reused from another card is an independent copy. |
| Copying a card | Copies the photo file and the saved links, so later changes to either card don't affect the other. |
| A card without a name | Not allowed: like the welcome, a card needs a name (the contact card needs one). |
| Two cards with the same label or handle | Allowed. Labels are private, and handles aren't unique anywhere. |
| Switching during a tap | The tap service reads the active card when a phone starts reading. A switch mid-read affects the next tap. Taps take about a second, so it won't matter in practice. |
| Met entries from before cards | Shown with the first card's label. The CSV leaves `card` empty for them. |
| Upgrading from 1.0 or 1.1 | The single profile, photo, share option, stars, greeting and saved links become card 1, "My card". Nothing is lost; covered by a migration test. |
| Debug "Show the welcome screens" | Clears all cards. |
| Lots of cards | No limit. The switcher scrolls. Expect fewer than ten. |
| Accessibility | Swiping the card always has a button alternative (the switcher). The handle button is labelled "Switch card, current: Work". |

## Data model

- **`Card`** (plain Kotlin, unit-tested): `id`, `label`, `colour`, `profile: Profile`, `links: List<SavedLink>`, `share`, `pinned`, `whatsappGreeting`. Stored as a JSON list under `cards`, with `active_card` holding the id.
- **Photos:** `photo-<cardId>.jpg`. The existing `profile_photo.jpg` is renamed during migration.
- **`Prefs.profile`, `links`, `share`, `pinned` and `whatsappGreeting`** read and write the active card, so most screens and the tap service don't change. New: `cards`, `activeCard`, `switchTo(id)`, `newCard(from)`, `deleteCard(id)`.
- **`Meeting`** gains `card` (the label at the time).
- **Migration:** runs once, the first time cards are read, as the saved-links migration does.

## Out of scope (later)

- Switching automatically by time, place or event.
- Exporting or importing a card, or sharing one with someone as a file.
- Cards on a Wear OS watch, and a home-screen widget per card.
- Syncing between phones (Tilde stays offline).

## Decisions

Decided by Thomas on 7 October 2026 (links per card after a second look):

1. **Naming:** identities are **cards**. The writing feature is renamed **Write a sticker** ("an NFC sticker, or a printed card with one inside"); the printable product stays the **Tilde card**.
2. **Switching:** the `~/handle` button opens the switcher, **and** swiping the profile card steps through cards.
3. **Cover colour:** yes, per card, from six colours (Tilde amber plus five that suit the dark theme).
4. **Release:** ship `release/1.1.0` first (beta, then 1.1.0), then build cards on their own branch for **1.2**.

5. **Saved links per card**, with Copy card copying them and Add a link suggesting links from other cards (see "Saved links: per card"). Chosen over a shared library starred per card, which has one place to edit but behaves differently from the per-card social links, and where an edit can affect other cards.

## Build plan (once decided)

1. `Card` model, storage and migration, with unit tests: migration from 1.0 and 1.1 data (profile, photo, saved links), copy (including links and photo), delete, the last card.
2. `Prefs` redirects to the active card. The tap service, tile and Write a card use the active card.
3. The switcher sheet, New card (copy or blank), the Cards screen (rename, colour, reorder, delete), and link suggestions from other cards in Add a link.
4. Settings split into This card and All cards.
5. The profile-card swipe, the Met card column and CSV, the welcome mention.
6. Rename Write a card to Write a sticker (screen, Settings, README, site, the welcome's card step). Release notes, README and the site's Tilde page.

Testing: the unit tests above, then the emulator (two or three cards, switching, deleting the active card, copying with a photo, upgrading from a 1.1 settings file), then Tilde dev on the phone.
