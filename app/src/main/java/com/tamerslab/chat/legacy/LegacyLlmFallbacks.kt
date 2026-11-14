package com.tamerslab.chat.legacy

/**
 * Deterministic, authored fallback lines for the legacy LLM chat plumbing.
 *
 * The new DigiLine engine owns the real content. These lines exist only so that surfaces that are
 * not migrated to authored packs in the same release still return readable, offline text instead of
 * calling a model. They are intentionally neutral (no personality claims, no lore facts, no gender
 * reference) and are never shown in the normal owned/wild conversation flows.
 */
object LegacyLlmFallbacks {

    enum class Kind { OWNED_REPLY, WILD_REPLY, EVENT, FARM, RADAR, RECAP }

    fun line(kind: Kind, languageTag: String = "en"): String = when {
        languageTag.startsWith("pt", ignoreCase = true) -> portuguese(kind)
        languageTag.startsWith("ja", ignoreCase = true) -> japanese(kind)
        else -> english(kind)
    }

    private fun english(kind: Kind): String = when (kind) {
        Kind.OWNED_REPLY -> "…(The Digimon looks at you, waiting to see what you do next.)"
        Kind.WILD_REPLY -> "…(The Digimon keeps its distance and watches you.)"
        Kind.EVENT -> "(The Digimon takes a moment to react.)"
        Kind.FARM -> "…(The Digimon quietly goes back to what it was doing.)"
        Kind.RADAR -> "…(The two Digimon regard each other for a moment.)"
        Kind.RECAP -> "Recorded facts only: no new events, dialogue or outcomes are added here."
    }

    private fun portuguese(kind: Kind): String = when (kind) {
        Kind.OWNED_REPLY -> "…(O Digimon olha para você, esperando o próximo passo.)"
        Kind.WILD_REPLY -> "…(O Digimon mantém distância e observa você.)"
        Kind.EVENT -> "(O Digimon leva um instante para reagir.)"
        Kind.FARM -> "…(O Digimon volta em silêncio ao que estava fazendo.)"
        Kind.RADAR -> "…(Os dois Digimon se observam por um momento.)"
        Kind.RECAP -> "Apenas fatos registrados: nenhum evento, fala ou resultado novo é acrescentado aqui."
    }

    private fun japanese(kind: Kind): String = when (kind) {
        Kind.OWNED_REPLY -> "…（デジモンはこちらを見て、次の行動を待っている。）"
        Kind.WILD_REPLY -> "…（デジモンは距離を保ち、こちらをうかがっている。）"
        Kind.EVENT -> "（デジモンは少し間を置いて反応した。）"
        Kind.FARM -> "…（デジモンは静かに元の作業へ戻った。）"
        Kind.RADAR -> "…（二体のデジモンはしばらく互いを見つめ合っている。）"
        Kind.RECAP -> "記録された事実のみ。新しい出来事や会話、結果はここに追加されない。"
    }
}
