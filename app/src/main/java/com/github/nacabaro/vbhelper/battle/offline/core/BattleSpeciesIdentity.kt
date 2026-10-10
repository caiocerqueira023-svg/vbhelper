package com.github.nacabaro.vbhelper.battle.offline.core

import java.util.Locale

/** Shared comparison only; qualifiers remain part of the species identity. */
object BattleSpeciesIdentity {
    private fun compact(value: String) = value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    private val aliases = mapOf(
        "gallantmon" to "dukemon", "gallantmoncrimsonmode" to "dukemoncrimsonmode",
        "beelzemon" to "beelzebumon", "beelzemonblastmode" to "beelzebumonblastmode",
        "megagargomon" to "saintgalgomon", "gargomon" to "galgomon",
        "growlmon" to "growmon", "wargrowlmon" to "megalogrowmon",
        "veemon" to "vmon", "exveemon" to "xvmon", "veedramon" to "vdramon",
        "aeroveedramon" to "aerovdramon", "ulforceveedramon" to "ulforcevdramon",
        "gatomon" to "tailmon", "blackgatomon" to "blacktailmon", "salamon" to "plotmon",
        "biyomon" to "piyomon", "armadillomon" to "armadimon", "hawkmon" to "hawkmon",
        "machinedramon" to "mugendramon", "omnimon" to "omegamon",
        "ophanimon" to "ofanimon", "ravmon" to "ravemon", "ravmonburstmode" to "ravemonburstmode",
        "agumon2006animeversion" to "agumon2006", "falcomon2006animeversion" to "falcomon2006",
        "kudamon2006animeversion" to "kudamon2006", "greymon2010animeversion" to "greymon2010",
        "cyberdramon2010animeversion" to "cyberdramon2010", "beelzebumon2010animeversion" to "beelzebumon2010",
        "agunimon" to "agnimon", "lobomon" to "wolfmon", "kazemon" to "fairimon",
        "kumamon" to "chackmon", "beetlemon" to "blitzmon", "loweemon" to "löwemon",
        "leoweemon" to "löwemon", "burninggreymon" to "vritramon", "kendogarurumon" to "garummon",
        "piedmon" to "piemon", "puppetmon" to "pinochimon", "myotismon" to "vamdemon",
        "venommyotismon" to "venomvamdemon", "demidevimon" to "picodevimon",
        "frigimon" to "yukidarumon",
        "flamemon" to "flamon", "penguinmon" to "penmon", "dolphmon" to "rukamon",
        "grapleomon" to "grappuleomon", "grappleomon" to "grappuleomon", "grizzlymon" to "gryzmon", "stefilmon" to "stiffilmon",
        "hackmon" to "huckmon", "baohackmon" to "baohuckmon", "saviorhackmon" to "saviorhuckmon",
        "cocomon" to "conomon", "bubbmon" to "pabumon", "pufumon" to "pafumon",
        "yatagaramon2006animeversion" to "yatagaramon2006", "meicrackmonviciousmode" to "meicrackmonviciousmode",
        "omegamonzwartd" to "omegamonzwartdefeat", "justimonaccelarm" to "justimon",
        "opossummon" to "opossumon", "metalgreymon2010animeversion" to "metalgreymon2010",
        "valvemon" to "valvemon", "bombmon" to "bommon",
        "blackimperialdramon" to "imperialdramondragonmodeblack", "puffmon" to "pafumon",
        "ogremon" to "orgemon", "centaurmon" to "centalmon", "elecmon" to "elecmon",
        "whamonperfect" to "whamon", "marineangemon" to "marinangemon", "kimeramon" to "chimairamon",
        "chimeramon" to "chimairamon",
        "paildramon" to "paildramon", "imperialdramondm" to "imperialdramondragonmode",
        "imperialdramon" to "imperialdramondragonmode", "imperialdramonfm" to "imperialdramonfightermode",
        "cherubimonvirtue" to "cherubimonvirtue", "cherubimonvice" to "cherubimonvice",
    ).mapKeys { compact(it.key) }

    fun normalize(value: String): String = compact(value).let { aliases[it] ?: it }
}
