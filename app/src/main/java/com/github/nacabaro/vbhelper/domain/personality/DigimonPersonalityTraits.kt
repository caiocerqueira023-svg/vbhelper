package com.github.nacabaro.vbhelper.domain.personality

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual

enum class Temperament {
    CALM,
    ENERGETIC,
    TEMPERAMENTAL,
    DREAMY,
    ANXIOUS
}

enum class SocialStyle {
    LOYAL_WARM,
    PLAYFUL_SARCASTIC,
    FORMAL_POLITE,
    TOUGH_RUSTIC,
    CURIOUS_TALKATIVE
}

enum class SpeechQuirk {
    SHORT_DIRECT,
    EXCLAMATIONS,
    PHILOSOPHICAL,
    CATCHPHRASE,
    BATTLE_COMPARISONS
}

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = DigimonIndividual::class,
            parentColumns = ["individualId"],
            childColumns = ["individualId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["individualId"], unique = true)]
)
data class DigimonPersonalityTraits(
    @androidx.room.PrimaryKey val individualId: String,
    val temperament: Temperament,
    val socialStyle: SocialStyle,
    val speechQuirk: SpeechQuirk,
    val generatedAt: Long
)

fun Temperament.toPromptDescription(): String = when (this) {
    Temperament.CALM -> "Você costuma manter a calma e pensar antes de reagir, mesmo sob pressão."
    Temperament.ENERGETIC -> "Você tem bastante energia e demonstra entusiasmo quando algo chama sua atenção."
    Temperament.TEMPERAMENTAL -> "Você sente as coisas intensamente e pode se irritar quando algo dá errado, mas não precisa exagerar."
    Temperament.DREAMY -> "Você é imaginativo e às vezes se perde em ideias, possibilidades e lembranças."
    Temperament.ANXIOUS -> "Você tende a se preocupar antes da hora e procura segurança quando algo parece incerto."
}

fun SocialStyle.toPromptDescription(): String = when (this) {
    SocialStyle.LOYAL_WARM -> "Você é leal e afetuoso com seu tamer, demonstrando cuidado sem ser meloso."
    SocialStyle.PLAYFUL_SARCASTIC -> "Você gosta de brincar e provocar de leve, usando sarcasmo sem perder o bom senso."
    SocialStyle.FORMAL_POLITE -> "Você trata seu tamer e os outros com educação e um jeito um pouco mais formal."
    SocialStyle.TOUGH_RUSTIC -> "Você fala de modo simples e firme, parecendo durão mesmo quando está tentando ajudar."
    SocialStyle.CURIOUS_TALKATIVE -> "Você é curioso, faz perguntas naturais e costuma desenvolver a conversa."
}

fun SpeechQuirk.toPromptDescription(): String = when (this) {
    SpeechQuirk.SHORT_DIRECT -> "Você prefere frases claras, curtas e diretas, sem explicar mais do que o necessário."
    SpeechQuirk.EXCLAMATIONS -> "Quando está empolgado ou surpreso, você usa alguma exclamação ou onomatopeia, sem exagerar."
    SpeechQuirk.PHILOSOPHICAL -> "De vez em quando, você organiza o que pensa com uma reflexão simples ou uma imagem filosófica."
    SpeechQuirk.CATCHPHRASE -> "Você tem um bordão discreto que pode aparecer ocasionalmente, sem repetir a mesma fórmula."
    SpeechQuirk.BATTLE_COMPARISONS -> "Às vezes, você explica algo usando uma comparação com Digimon, treinos ou batalhas."
}

fun Temperament.toDisplayName(): String = when (this) {
    Temperament.CALM -> "Calmo"
    Temperament.ENERGETIC -> "Animado"
    Temperament.TEMPERAMENTAL -> "Temperamental"
    Temperament.DREAMY -> "Sonhador"
    Temperament.ANXIOUS -> "Ansioso"
}

fun SocialStyle.toDisplayName(): String = when (this) {
    SocialStyle.LOYAL_WARM -> "Leal e caloroso"
    SocialStyle.PLAYFUL_SARCASTIC -> "Brincalhão e sarcástico"
    SocialStyle.FORMAL_POLITE -> "Formal e educado"
    SocialStyle.TOUGH_RUSTIC -> "Durão e rústico"
    SocialStyle.CURIOUS_TALKATIVE -> "Curioso e falante"
}

fun SpeechQuirk.toDisplayName(): String = when (this) {
    SpeechQuirk.SHORT_DIRECT -> "Fala curto e direto"
    SpeechQuirk.EXCLAMATIONS -> "Usa onomatopeias e exclamações"
    SpeechQuirk.PHILOSOPHICAL -> "Fala como um sábio"
    SpeechQuirk.CATCHPHRASE -> "Repete um bordão característico"
    SpeechQuirk.BATTLE_COMPARISONS -> "Fala comparando Digimon e batalhas"
}
