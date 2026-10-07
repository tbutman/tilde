package com.tbutman.tilde

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * The sharing options as rows, and the sheet to choose one. Used by the Share screen and by Write a
 * card (`writing`), which shows links without the event tag, because a written card outlives the
 * event, and iPhone notes without the on-screen code, which a sticker doesn't have.
 */
class PresetRows(private val context: Context, private val prefs: Prefs, private val writing: Boolean = false) {
    private val event: String
        get() = if (writing) "" else prefs.event

    fun bind(row: View, preset: Presets.Preset, selected: Boolean) {
        // A saved link on a known site shows that site's logo; others show their first letter.
        val logo = logoFor(preset.id) ?: SavedLink.idOf(preset.id)?.let { siteLogo(prefs.urlFor(preset.id, event = "")) }
        row.findViewById<ImageView>(R.id.logo).apply {
            visibility = if (logo != null) View.VISIBLE else View.GONE
            logo?.let { setImageResource(it) }
        }
        row.findViewById<TextView>(R.id.monogram).apply {
            visibility = if (logo == null) View.VISIBLE else View.GONE
            text = preset.monogram
        }
        row.findViewById<TextView>(R.id.title).apply {
            text = context.labelOf(preset)
            setTextColor(context.getColor(if (selected) R.color.accent else R.color.text))
        }
        row.findViewById<TextView>(R.id.subtitle).text = detail(preset)
        // Everything works by tap or scan everywhere, so only the exceptions get a note.
        row.findViewById<TextView>(R.id.note).apply {
            visibility = if (preset.iphoneTap) View.GONE else View.VISIBLE
            val contact = preset.id == Presets.CONTACT
            val opens = iphoneOpens(context, prefs.contactProfile)
            text = when {
                writing && !contact -> context.getString(R.string.write_iphone_wifi)
                writing && opens == null -> context.getString(R.string.write_iphone_contact_none)
                writing -> context.getString(R.string.write_iphone_contact, opens)
                contact && opens != null -> context.getString(R.string.iphone_contact_note, opens)
                else -> context.getString(R.string.iphone_scan_only)
            }
        }
        row.findViewById<ImageView>(R.id.end).apply {
            setImageResource(if (selected) R.drawable.ic_check else R.drawable.ic_chevron)
            imageTintList = context.getColorStateList(if (selected) R.color.accent else R.color.muted)
        }
    }

    /**
     * A sheet of sharing options; `onPick` gets the chosen one. With `onSetUp` (the Share screen),
     * starred options come first and the rest fold under "More options", including the ones that
     * aren't ready, greyed with "Set up" (which calls `onSetUp`); stars choose the quick-switch row.
     * Without it (Write a card), it's a plain list of the ready options. Either way it ends with
     * "Add a link", and a new link is picked straight away.
     */
    fun showPicker(title: Int, selectedId: String, onSetUp: ((Presets.Preset) -> Unit)? = null, onPick: (Presets.Preset) -> Unit) {
        val sheet = BottomSheetDialog(context)
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(24))
        }
        list.addView(TextView(context).apply {
            setText(title)
            setTextColor(context.getColor(R.color.text))
            textSize = 18f
            setPadding(dp(20), dp(8), dp(20), dp(12))
        })
        val pinned = prefs.pinned.toSet()
        val options = prefs.options().filter { onSetUp != null || prefs.isReady(it.id) }
        // On the Share screen: starred and ready first, then everything else behind "More options".
        val (first, rest) = if (onSetUp == null) options to emptyList() else
            options.partition { it.id in pinned && prefs.isReady(it.id) }
        val more = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        for (preset in first + rest) {
            val ready = prefs.isReady(preset.id)
            val target = if (preset in rest) more else list
            val row = LayoutInflater.from(context).inflate(R.layout.row_preset, target, false)
            bind(row, preset, selected = preset.id == selectedId)
            if (ready) {
                row.setOnClickListener {
                    sheet.dismiss()
                    onPick(preset)
                }
                if (onSetUp != null) bindStar(row.findViewById(R.id.star), preset.id)
            } else {
                // Greyed, with "Set up" in place of the end mark.
                for (id in listOf(R.id.logo, R.id.monogram, R.id.title)) row.findViewById<View>(id).alpha = 0.45f
                row.findViewById<TextView>(R.id.subtitle).text = context.getString(R.string.detail_missing)
                row.findViewById<View>(R.id.note).visibility = View.GONE
                row.findViewById<View>(R.id.end).visibility = View.GONE
                row.findViewById<View>(R.id.set_up).visibility = View.VISIBLE
                row.setOnClickListener {
                    sheet.dismiss()
                    onSetUp?.invoke(preset)
                }
            }
            target.addView(row)
        }
        if (rest.isNotEmpty()) {
            // Open from the start when what's being shared is in there, so it's never hidden.
            var open = rest.any { it.id == selectedId }
            val toggle = TextView(context).apply {
                setTextColor(context.getColor(R.color.accent))
                textSize = 15f
                setPadding(dp(20), dp(14), dp(20), dp(14))
                val ripple = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                setBackgroundResource(ripple.resourceId)
            }
            fun show() {
                more.visibility = if (open) View.VISIBLE else View.GONE
                toggle.text = if (open) context.getString(R.string.picker_less) else context.getString(R.string.picker_more, rest.size)
            }
            toggle.setOnClickListener { open = !open; show() }
            show()
            list.addView(toggle)
            list.addView(more)
        }
        // "Add a link": a row like the others, with a + badge.
        val add = LayoutInflater.from(context).inflate(R.layout.row_preset, list, false)
        add.findViewById<TextView>(R.id.monogram).apply { visibility = View.VISIBLE; text = "+" }
        add.findViewById<View>(R.id.logo).visibility = View.GONE
        add.findViewById<TextView>(R.id.title).apply {
            setText(R.string.picker_add_link)
            setTextColor(context.getColor(R.color.accent))
        }
        add.findViewById<View>(R.id.subtitle).visibility = View.GONE
        add.findViewById<View>(R.id.end).visibility = View.GONE
        add.setOnClickListener {
            sheet.dismiss()
            LinkDialogs.editLink(context, prefs, null) { link -> link?.let { onPick(prefs.find(it.presetId)) } }
        }
        list.addView(add)
        sheet.setContentView(androidx.core.widget.NestedScrollView(context).apply { addView(list) })
        sheet.show()
    }

    /** The star that puts an option in (or takes it out of) the quick-switch row. */
    private fun bindStar(star: ImageView, id: String) {
        fun show() {
            val on = id in prefs.pinned
            star.setImageResource(if (on) R.drawable.ic_star_filled else R.drawable.ic_star)
            star.imageTintList = context.getColorStateList(if (on) R.color.accent else R.color.muted)
            star.contentDescription = context.getString(if (on) R.string.picker_unstar else R.string.picker_star)
        }
        star.visibility = View.VISIBLE
        show()
        star.setOnClickListener {
            prefs.pinned = if (id in prefs.pinned) prefs.pinned - id else prefs.pinned + id
            show()
        }
    }

    /** What an option opens, in a few words. Never a phone number or the Wi-Fi password. */
    private fun detail(preset: Presets.Preset): String = when (preset.id) {
        Presets.CONTACT -> contactSummary(context, prefs.contactProfile)
        Presets.WHATSAPP -> context.getString(R.string.detail_whatsapp)
        Presets.WIFI -> prefs.wifiSsid.ifBlank { context.getString(R.string.detail_wifi_missing) }
        else -> bare(prefs.urlFor(preset.id, event = event))
    }

    private fun bare(url: String) = Companion.bare(url)

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        /** A link as people read it: no https:// or www., no trailing slash. */
        fun bare(url: String) = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").removeSuffix("/")

        /**
         * What's on a contact card, from what this card includes and has: "Name, title, email and
         * links", in the app's language. The name is always there.
         */
        fun contactSummary(context: Context, profile: Profile): String {
            val parts = listOfNotNull(
                R.string.contact_part_name,
                R.string.contact_part_title.takeIf { profile.title.isNotBlank() },
                R.string.contact_part_company.takeIf { profile.company.isNotBlank() },
                R.string.contact_part_email.takeIf { profile.email.isNotBlank() || profile.email2.isNotBlank() },
                R.string.contact_part_phones.takeIf { profile.phones.any { it.second.isNotBlank() } },
                R.string.contact_part_links.takeIf { profile.siteRoot != null || profile.website.isNotBlank() || profile.socials.isNotEmpty() },
            ).map(context::getString)
            if (parts.size == 1) return parts.first()
            return context.getString(R.string.list_and, parts.dropLast(1).joinToString(", "), parts.last())
        }

        /** What an iPhone opens when it taps a contact card ("your website", "LinkedIn"), or null for nothing. */
        fun iphoneOpens(context: Context, profile: Profile): String? =
            profile.contactLink?.let { (label, _) -> label ?: context.getString(R.string.iphone_your_website) }

        /** The logo for a link on a known site (see [Sites]), or null. */
        fun siteLogo(url: String): Int? = when (Sites.of(url)) {
            "bluesky" -> R.drawable.ic_brand_bluesky
            "threads" -> R.drawable.ic_brand_threads
            "facebook" -> R.drawable.ic_brand_facebook
            "youtube" -> R.drawable.ic_brand_youtube
            "tiktok" -> R.drawable.ic_brand_tiktok
            "mastodon" -> R.drawable.ic_brand_mastodon
            "medium" -> R.drawable.ic_brand_medium
            "substack" -> R.drawable.ic_brand_substack
            "dribbble" -> R.drawable.ic_brand_dribbble
            "behance" -> R.drawable.ic_brand_behance
            "calendly" -> R.drawable.ic_brand_calendly
            "telegram" -> R.drawable.ic_brand_telegram
            "discord" -> R.drawable.ic_brand_discord
            "producthunt" -> R.drawable.ic_brand_producthunt
            "stackoverflow" -> R.drawable.ic_brand_stackoverflow
            "gitlab" -> R.drawable.ic_brand_gitlab
            "figma" -> R.drawable.ic_brand_figma
            "linkedin" -> R.drawable.ic_brand_linkedin
            "github" -> R.drawable.ic_brand_github
            "instagram" -> R.drawable.ic_brand_instagram
            "x" -> R.drawable.ic_brand_x
            "whatsapp" -> R.drawable.ic_brand_whatsapp
            else -> null
        }

        fun logoFor(id: String): Int? = when (id) {
            Presets.WHATSAPP -> R.drawable.ic_brand_whatsapp
            Presets.LINKEDIN -> R.drawable.ic_brand_linkedin
            Presets.GITHUB -> R.drawable.ic_brand_github
            Presets.INSTAGRAM -> R.drawable.ic_brand_instagram
            Presets.X -> R.drawable.ic_brand_x
            Presets.CONTACT -> R.drawable.ic_opt_contact
            Presets.WIFI -> R.drawable.ic_opt_wifi
            else -> null
        }
    }
}
