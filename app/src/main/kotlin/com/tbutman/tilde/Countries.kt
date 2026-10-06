package com.tbutman.tilde

/**
 * Countries and their international dialling codes, for the phone number fields. Plain Kotlin and
 * unit-tested; names come from the phone's locale at runtime, so they're always in its language.
 * The table was generated from Google's libphonenumber metadata (version 9.0.40, 245 regions), to
 * avoid shipping the library itself for one lookup.
 */
data class Country(val iso: String, val dial: String) {
    /** The flag emoji: each letter of the ISO code as a regional-indicator symbol. */
    val flag: String get() = iso.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
}

object Countries {
    private const val TABLE =
        "AC247 AD376 AE971 AF93 AG1 AI1 AL355 AM374 AO244 AR54 AS1 AT43 AU61 AW297 AX358 AZ994 BA387 BB1 " +
        "BD880 BE32 BF226 BG359 BH973 BI257 BJ229 BL590 BM1 BN673 BO591 BQ599 BR55 BS1 BT975 BW267 BY375 " +
        "BZ501 CA1 CC61 CD243 CF236 CG242 CH41 CI225 CK682 CL56 CM237 CN86 CO57 CR506 CU53 CV238 CW599 " +
        "CX61 CY357 CZ420 DE49 DJ253 DK45 DM1 DO1 DZ213 EC593 EE372 EG20 EH212 ER291 ES34 ET251 FI358 " +
        "FJ679 FK500 FM691 FO298 FR33 GA241 GB44 GD1 GE995 GF594 GG44 GH233 GI350 GL299 GM220 GN224 GP590 " +
        "GQ240 GR30 GT502 GU1 GW245 GY592 HK852 HN504 HR385 HT509 HU36 ID62 IE353 IL972 IM44 IN91 IO246 " +
        "IQ964 IR98 IS354 IT39 JE44 JM1 JO962 JP81 KE254 KG996 KH855 KI686 KM269 KN1 KP850 KR82 KW965 KY1 " +
        "KZ7 LA856 LB961 LC1 LI423 LK94 LR231 LS266 LT370 LU352 LV371 LY218 MA212 MC377 MD373 ME382 MF590 " +
        "MG261 MH692 MK389 ML223 MM95 MN976 MO853 MP1 MQ596 MR222 MS1 MT356 MU230 MV960 MW265 MX52 MY60 " +
        "MZ258 NA264 NC687 NE227 NF672 NG234 NI505 NL31 NO47 NP977 NR674 NU683 NZ64 OM968 PA507 PE51 " +
        "PF689 PG675 PH63 PK92 PL48 PM508 PR1 PS970 PT351 PW680 PY595 QA974 RE262 RO40 RS381 RU7 RW250 " +
        "SA966 SB677 SC248 SD249 SE46 SG65 SH290 SI386 SJ47 SK421 SL232 SM378 SN221 SO252 SR597 SS211 " +
        "ST239 SV503 SX1 SY963 SZ268 TA290 TC1 TD235 TG228 TH66 TJ992 TK690 TL670 TM993 TN216 TO676 TR90 " +
        "TT1 TV688 TW886 TZ255 UA380 UG256 US1 UY598 UZ998 VA39 VC1 VE58 VG1 VI1 VN84 VU678 WF681 WS685 " +
        "XK383 YE967 YT262 ZA27 ZM260 ZW263"

    val all: List<Country> = TABLE.trim().split(' ').map { Country(it.take(2), it.drop(2)) }

    fun find(iso: String?): Country? = iso?.uppercase()?.let { code -> all.firstOrNull { it.iso == code } }

    /** The SIM's country, else the network's, else the phone's region, else the US. */
    fun default(vararg candidates: String?): Country =
        candidates.firstNotNullOfOrNull { find(it?.takeIf(String::isNotBlank)) } ?: find("US")!!

    /**
     * Matches a search typed into the country list against the name, the ISO code and the dialling
     * code ("port", "pt", "351" and "+351" all find Portugal).
     */
    fun matches(country: Country, name: String, query: String): Boolean {
        val q = query.trim().removePrefix("+").lowercase()
        if (q.isEmpty()) return true
        return name.lowercase().split(' ', '-', '(').any { it.startsWith(q) } ||
            name.lowercase().startsWith(q) || country.iso.lowercase() == q || country.dial.startsWith(q)
    }

    /**
     * Labels for numbers entered with these countries: plain "mobile" for one number, and the
     * country added when there are several, as in "mobile (US)" and "mobile (PT)".
     */
    fun phoneLabels(isos: List<String>): List<String> {
        val distinct = isos.distinct().size > 1
        return isos.map { if (distinct) "mobile (${it.uppercase()})" else "mobile" }
    }

    /**
     * A number in international form when Android can't format it: the dialling code plus what was
     * typed. A typed "+" means the person already gave a full international number.
     */
    fun international(country: Country, typed: String): String {
        val number = typed.trim()
        if (number.isEmpty()) return ""
        if (number.startsWith("+")) return number
        return "+${country.dial} $number"
    }
}
