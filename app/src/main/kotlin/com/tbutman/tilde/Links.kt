package com.tbutman.tilde

import org.json.JSONArray
import org.json.JSONObject

/**
 * A link saved to share, with a name, such as "Tilde" → https://tbutman.com/tilde. Each one is its
 * own sharing option, `link:<id>`. Plain Kotlin apart from org.json, so it's unit-tested.
 */
data class SavedLink(val id: String, val name: String, val url: String) {
    val presetId: String get() = PREFIX + id

    companion object {
        const val PREFIX = "link:"

        /** The saved link's id in a preset id ("link:a1b2" → "a1b2"), or null for any other option. */
        fun idOf(presetId: String): String? = presetId.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)

        /** A name for a link nobody named: its address without https:// or www. ("labtrails.app/demo"). */
        fun defaultName(url: String): String =
            url.trim().substringAfter("://").removePrefix("www.").removeSuffix("/").ifEmpty { url.trim() }

        /**
         * The link as it will be shared ("labtrails.app" → "https://labtrails.app"), or null when it
         * isn't one: a web address needs a dot in its host; mailto:, tel: and similar links are kept.
         */
        fun validUrl(text: String): String? {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
            val other = Regex("^(mailto|tel|sms|geo):.+", RegexOption.IGNORE_CASE)
            if (other.matches(trimmed)) return trimmed
            val url = Profile.normalizeUrl(trimmed)
            val host = Regex("^https?://([^/?#:]+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1) ?: return null
            return url.takeIf { '.' in host && !host.startsWith('.') && !host.endsWith('.') }
        }

        /** A short id that isn't already taken. */
        fun newId(taken: Collection<String>, random: () -> Int = { (0..0xFFFFFF).random() }): String {
            while (true) {
                val id = random().toString(16).padStart(6, '0')
                if (id !in taken) return id
            }
        }

        fun toJson(links: List<SavedLink>): String = JSONArray().apply {
            links.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
        }.toString()

        fun fromJson(json: String?): List<SavedLink> = runCatching {
            val array = JSONArray(json ?: "[]")
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                SavedLink(o.getString("id"), o.optString("name"), o.getString("url"))
            }
        }.getOrDefault(emptyList())

        /**
         * Version 1.0 had one custom link instead of a list: it becomes the first saved link, and a
         * share setting pointing at it ("custom") points at the new link instead.
         */
        fun migrate(customUrl: String, share: String?, newId: String): Pair<List<SavedLink>, String?> {
            val url = customUrl.trim()
            if (url.isEmpty()) return emptyList<SavedLink>() to share?.takeUnless { it == Presets.CUSTOM }
            val link = SavedLink(newId, defaultName(url), url)
            return listOf(link) to (if (share == Presets.CUSTOM) link.presetId else share)
        }
    }
}
