# Backlog

Ideas worth doing later, roughly in order of value. Each says why, and what to watch for. When one
is picked up, it moves to a spec (docs/specs) or straight to a branch, and off this list.

## Polish

- **Logos for known sites in saved links.** A link to Bluesky, YouTube, Calendly, Threads and so on
  gets that site's icon in the options list and chips, instead of the first letter of its name.
  Needs: a small table of domains and icons (no network lookups).
- **Include the photo when sending the contact card.** A tap stays photo-free (a photo makes a tag
  slow to read), but a `.vcf` sent through another app can carry it (vCard `PHOTO`). Off by default;
  check the size stays reasonable (resize to about 400 px).
- **Theme: light, or follow the system.** Tilde is dark-only. A light theme helps outdoors and in
  bright venues. Needs: light values for every colour token; the QR card is light already.

## Bigger features

- **Answer taps without opening Tilde.** Android can let the tap service answer while Tilde is
  closed (screen on). Convenient, but sharing by accident becomes possible: needs a clear switch
  (off by default), real-phone testing on a few Android versions, and the Met log still working.
- **Lock a sticker after writing it.** Makes it read-only so nobody can overwrite it. Permanent, so
  it needs a strong warning and a second confirmation; never the default.
- **Home-screen widget** showing a card's QR code (choose the card per widget). Good for people who
  share by code more than by tap.
- **Portuguese interface**, using Android's per-app language setting. The welcome and Settings are
  the bulk of the text; have a native speaker review it.
- **Wear OS:** a QR code tile first; tapping from the watch only if it proves reliable.

## Done (moved out of the backlog)

- Multiple cards, Settings reorganised, contact card choices, company, backup and restore, event tag
  auto-clear, Delete all data, Share screen and Met settings: 1.2.
