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
        val logo = logoFor(preset.id)
        row.findViewById<ImageView>(R.id.logo).apply {
            visibility = if (logo != null) View.VISIBLE else View.GONE
            logo?.let { setImageResource(it) }
        }
        row.findViewById<TextView>(R.id.monogram).apply {
            visibility = if (logo == null) View.VISIBLE else View.GONE
            text = preset.monogram
        }
        row.findViewById<TextView>(R.id.title).apply {
            text = preset.label
            setTextColor(context.getColor(if (selected) R.color.accent else R.color.text))
        }
        row.findViewById<TextView>(R.id.subtitle).text = detail(preset)
        // Everything works by tap or scan everywhere, so only the exceptions get a note.
        row.findViewById<TextView>(R.id.note).apply {
            visibility = if (preset.iphoneTap) View.GONE else View.VISIBLE
            val contact = preset.id == Presets.CONTACT
            setText(
                when {
                    writing -> if (contact) R.string.write_iphone_contact else R.string.write_iphone_wifi
                    contact && prefs.urlFor(Presets.CONTACT, event = event).isNotEmpty() -> R.string.iphone_contact_note
                    else -> R.string.iphone_scan_only
                },
            )
        }
        row.findViewById<ImageView>(R.id.end).apply {
            setImageResource(if (selected) R.drawable.ic_check else R.drawable.ic_chevron)
            imageTintList = context.getColorStateList(if (selected) R.color.accent else R.color.muted)
        }
    }

    /** A sheet listing what this profile can share; `onPick` gets the chosen option. */
    fun showPicker(title: Int, selectedId: String, onPick: (Presets.Preset) -> Unit) {
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
        for (preset in Presets.available(prefs.profile)) {
            val row = LayoutInflater.from(context).inflate(R.layout.row_preset, list, false)
            bind(row, preset, selected = preset.id == selectedId)
            row.setOnClickListener {
                sheet.dismiss()
                onPick(preset)
            }
            list.addView(row)
        }
        sheet.setContentView(list)
        sheet.show()
    }

    /** What an option opens, in a few words. Never a phone number or the Wi-Fi password. */
    private fun detail(preset: Presets.Preset): String = when (preset.id) {
        Presets.CONTACT -> context.getString(R.string.detail_contact)
        Presets.WHATSAPP -> context.getString(R.string.detail_whatsapp)
        Presets.WIFI -> prefs.wifiSsid.ifBlank { context.getString(R.string.detail_wifi_missing) }
        Presets.CUSTOM -> bare(prefs.customUrl)
        else -> bare(prefs.urlFor(preset.id, event = event))
    }

    private fun bare(url: String) = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").removeSuffix("/")

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        fun logoFor(id: String): Int? = when (id) {
            Presets.WHATSAPP -> R.drawable.ic_brand_whatsapp
            Presets.LINKEDIN -> R.drawable.ic_brand_linkedin
            Presets.GITHUB -> R.drawable.ic_brand_github
            Presets.INSTAGRAM -> R.drawable.ic_brand_instagram
            Presets.X -> R.drawable.ic_brand_x
            Presets.CONTACT -> R.drawable.ic_opt_contact
            Presets.CUSTOM -> R.drawable.ic_opt_link
            Presets.WIFI -> R.drawable.ic_opt_wifi
            else -> null
        }
    }
}
