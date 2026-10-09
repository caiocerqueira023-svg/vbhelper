package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.domain.personality.DigimonSocialProfile
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType

data class SocialOpening(val key: String, val text: String)

/** Localized, motive-specific speech available immediately, including when the provider is unavailable. */
object SocialOpenings {
    private data class Line(val en: String, val pt: String, val ja: String) {
        fun inLanguage(language: String) = when { language.startsWith("pt", true) -> pt; language.startsWith("ja", true) -> ja; else -> en }
    }

    fun create(choice: SocialEncounterDecision, profile: DigimonSocialProfile, language: String,
        recentKeys: Set<String> = emptySet(), responding: Boolean = false): SocialOpening {
        val motive = if (responding && choice.listenerDeclines) SocialMotive.REFUSAL else choice.motive
        val start = Math.floorMod(choice.variation, 16)
        val variant = (0 until 16).map { (start + it) % 16 }.firstOrNull {
            key(motive, profile, it, responding) !in recentKeys
        } ?: start
        val lines = if (responding) responses(motive) else if (motive == SocialMotive.HOSTILE_ATTACK && choice.targetId != "trainer") {
            listOf(
                Line("I'm picking this fight. Defend yourself!", "Vou iniciar esta luta. Defenda-se!", "こちらから勝負を仕掛ける。身を守って！"),
                Line("Stand your ground. I'm coming at you!", "Fique firme. Vou atacar você!", "構えて。攻撃するよ！"),
                Line("I'm challenging you here and now!", "Vou desafiar você aqui e agora!", "ここで今、君に勝負を挑む！"),
                Line("This is a confrontation, not a practice invitation.", "Isto é um confronto, não um convite de treino.", "これは練習の誘いではなく、対決だ。"))
        } else openings(motive)
        val base = lines[variant % lines.size].inLanguage(language)
        val suffix = if (variant / 4 == 0 || motive in listOf(SocialMotive.HOSTILE_ATTACK, SocialMotive.TERRITORIAL_WARNING, SocialMotive.REFUSAL)) ""
            else " " + characterLine(profile.personality, variant / 4).inLanguage(language)
        return SocialOpening(key(motive, profile, variant, responding), (base + suffix).take(500))
    }

    private fun key(motive: SocialMotive, profile: DigimonSocialProfile, variant: Int, response: Boolean) =
        "social-v1:${motive.name}:${profile.personality.number}:$variant:${if (response) "reply" else "opening"}"

    private fun openings(motive: SocialMotive): List<Line> = when (motive) {
        SocialMotive.CURIOUS_APPROACH -> listOf(
            Line("What brought you this way?", "O que trouxe você por aqui?", "どうしてここへ来たの？"),
            Line("I was wondering who would come by next.", "Eu estava pensando em quem apareceria por aqui.", "次は誰が来るのか気になっていたんだ。"),
            Line("Wait a moment. I'd like to get to know you.", "Espere um pouco. Quero conhecer você.", "ちょっと待って。君のことを知りたいな。"),
            Line("You caught my attention. Have a moment?", "Você chamou minha atenção. Tem um momento?", "君が気になったんだ。少し時間はある？"))
        SocialMotive.RECOGNITION -> listOf(
            Line("It's you again. How have you been?", "Você voltou. Como tem passado?", "また君だね。元気だった？"),
            Line("We keep crossing paths, don't we?", "Nós vivemos nos encontrando, não é?", "よく会うね。"),
            Line("I remember you. Want to catch up?", "Lembro de você. Vamos colocar a conversa em dia?", "君のことを覚えているよ。少し話そうか？"),
            Line("Back for another visit?", "Veio fazer outra visita?", "また会いに来てくれたの？"))
        SocialMotive.COMPANY -> listOf(
            Line("Would you keep me company for a moment?", "Pode me fazer companhia um pouco?", "少しそばにいてくれる？"),
            Line("I feel like talking with someone.", "Estou com vontade de conversar com alguém.", "誰かと話したい気分なんだ。"),
            Line("No need to rush off. Stay a little?", "Não precisa ir tão depressa. Fica um pouco?", "急がなくてもいいよ。少し一緒にいよう？"),
            Line("A quiet moment together sounds good.", "Um momento tranquilo juntos parece uma boa ideia.", "一緒にのんびりするのもよさそうだね。"))
        SocialMotive.PLAYFUL_TEASING -> listOf(
            Line("You look ready for an adventure—or a little mischief.", "Você parece pronto para uma aventura... ou uma travessura.", "冒険か、いたずらの準備はできている？"),
            Line("Careful, I might challenge you to something silly.", "Cuidado, posso desafiar você para alguma bobagem.", "気をつけて。変な勝負に誘うかもしれないよ。"),
            Line("Can you take a little teasing?", "Você aguenta uma provocaçãozinha?", "ちょっとからかってもいい？"),
            Line("Let's see which of us comes up with the better joke.", "Vamos ver quem inventa a melhor piada.", "どっちが面白い冗談を言えるかな？"))
        SocialMotive.CHALLENGE_SPARRING -> listOf(
            Line("Interested in a friendly practice match?", "Quer uma luta amistosa de treino?", "友好的な練習試合をしない？"),
            Line("I'd like to measure our skills. Only if you want to.", "Quero comparar nossas habilidades. Só se você quiser.", "腕を比べてみたい。君もよければね。"),
            Line("How about training together?", "Que tal treinarmos juntos?", "一緒に訓練するのはどう？"),
            Line("A little sparring could teach us both something.", "Um treino de combate pode ensinar algo a nós dois.", "練習試合なら二人とも何か学べそうだね。"))
        SocialMotive.TERRITORIAL_WARNING -> listOf(
            Line("Give me some space. This is my patch.", "Me dê um pouco de espaço. Este é meu canto.", "少し離れて。ここは私の場所だ。"),
            Line("Stop there. I want to know why you're so close.", "Pare aí. Quero saber por que chegou tão perto.", "そこで止まって。どうして近づくの？"),
            Line("I'm not looking for company right now. Keep your distance.", "Não quero companhia agora. Mantenha distância.", "今は一人でいたい。距離を取って。"),
            Line("Easy. Don't crowd me.", "Calma. Não me cerque.", "落ち着いて。そんなに近寄らないで。"))
        SocialMotive.HOSTILE_ATTACK -> listOf(
            Line("Enough. Get your partner ready—I'm attacking!", "Chega. Prepare seu parceiro: vou atacar!", "もういい。パートナーを構えて。攻撃する！"),
            Line("I'm not sharing this space. Defend yourself!", "Não vou dividir este espaço. Defenda-se!", "この場所は譲らない。身を守って！"),
            Line("I'm picking this fight. Here I come!", "Vou iniciar esta luta. Lá vou eu!", "こちらから勝負を仕掛ける。行くよ！"),
            Line("Your partner and I are settling this now!", "Seu parceiro e eu vamos resolver isso agora!", "君のパートナーと今決着をつける！"))
        SocialMotive.CHECK_IN -> listOf(
            Line("Are you doing all right? We can take a moment.", "Está tudo bem? Podemos fazer uma pausa.", "大丈夫？少し休んでもいいよ。"),
            Line("I wanted to check on you.", "Queria saber como você está.", "君の様子が気になったんだ。"),
            Line("Need company, or would you prefer some space?", "Quer companhia ou prefere um pouco de espaço?", "一緒にいたい？それとも一人がいい？"),
            Line("You can talk if you want. I'll listen.", "Pode falar, se quiser. Vou ouvir.", "話したければ聞くよ。"))
        SocialMotive.RECONCILE -> listOf(
            Line("I'd rather clear the air than keep arguing.", "Prefiro esclarecer as coisas a continuar discutindo.", "言い争うより、きちんと話したい。"),
            Line("Can we start this conversation again?", "Podemos recomeçar esta conversa?", "もう一度話し直せるかな？"),
            Line("We haven't been getting along. I'd like to understand why.", "Não estamos nos dando bem. Quero entender por quê.", "うまくいっていないね。理由を知りたい。"),
            Line("Let's talk before this becomes another dispute.", "Vamos conversar antes que isso vire outra briga.", "また争いになる前に話そう。"))
        SocialMotive.AVOIDANCE -> listOf(
            Line("I'm keeping to myself for now.", "Vou ficar na minha por enquanto.", "今は一人で過ごしたい。"),
            Line("Another time. I need some room.", "Outra hora. Preciso de espaço.", "また今度。少し距離がほしい。"),
            Line("I'm not in the mood for a conversation.", "Não estou com vontade de conversar.", "今は話す気分じゃないんだ。"),
            Line("I'll stay over here. You go ahead.", "Vou ficar por aqui. Pode seguir.", "ここにいるよ。君は先に行って。"))
        SocialMotive.REFUSAL -> listOf(
            Line("No, thanks. I'd rather do something else.", "Não, obrigado. Prefiro fazer outra coisa.", "遠慮しておく。別のことをしたい。"),
            Line("Not this time. Please respect that.", "Desta vez não. Respeite isso, por favor.", "今回はやめておく。それを尊重してほしい。"),
            Line("I'm not joining in, but you can go ahead.", "Não vou participar, mas pode ir em frente.", "私は参加しないけど、君は楽しんで。"),
            Line("I'll pass. Let's leave it there.", "Vou passar. Vamos deixar assim.", "やめておく。この話はここまでにしよう。"))
        SocialMotive.OBSERVATION -> listOf(
            Line("Sometimes it's worth stopping and looking around.", "Às vezes vale parar e observar ao redor.", "立ち止まって周りを見るのも大切だね。"),
            Line("I'd rather watch for a moment before deciding what to do.", "Prefiro observar um pouco antes de decidir o que fazer.", "何をするか決める前に少し様子を見たい。"),
            Line("What catches your attention around here?", "O que chama sua atenção por aqui?", "この辺りで何が気になる？"),
            Line("Let's slow down. We might notice something interesting.", "Vamos com calma. Podemos perceber algo interessante.", "ゆっくり行こう。面白いことに気づくかもしれない。"))
        SocialMotive.SHARED_ACTIVITY -> listOf(
            Line("Want to explore nearby together?", "Quer explorar aqui perto comigo?", "近くを一緒に探検しない？"),
            Line("Let's find something enjoyable to do here.", "Vamos achar algo divertido para fazer por aqui.", "ここで楽しいことを探そう。"),
            Line("We could spend a little time together. What do you think?", "Podemos passar um tempo juntos. O que acha?", "少し一緒に過ごすのはどう？"),
            Line("I'm inviting you, not dragging you along.", "Estou convidando, não arrastando você junto.", "誘っているだけだよ。無理に連れてはいかない。"))
    }

    private fun responses(motive: SocialMotive): List<Line> = when (motive) {
        SocialMotive.REFUSAL, SocialMotive.AVOIDANCE -> openings(SocialMotive.REFUSAL)
        SocialMotive.TERRITORIAL_WARNING -> listOf(Line("I heard you. I'll give you space.", "Entendi. Vou dar espaço.", "分かった。距離を取るよ。"),
            Line("We can talk without crowding each other.", "Podemos conversar sem cercar um ao outro.", "近づきすぎずに話せるよ。"))
        SocialMotive.RECONCILE -> listOf(Line("We can try. Start with what actually bothered you.", "Podemos tentar. Comece pelo que realmente incomodou você.", "やってみよう。何が気になったのか話して。"),
            Line("I'll listen. We don't have to agree on everything.", "Vou ouvir. Não precisamos concordar em tudo.", "聞くよ。全部同じ考えでなくてもいい。"))
        SocialMotive.CHALLENGE_SPARRING -> listOf(Line("I'm listening. What sort of practice do you have in mind?", "Estou ouvindo. Que tipo de treino você tem em mente?", "どんな練習を考えているの？"),
            Line("Let's discuss it first. A friendly match should suit us both.", "Vamos conversar primeiro. Uma luta amistosa deve servir aos dois.", "まず話そう。二人に合う練習試合がいいね。"))
        SocialMotive.SHARED_ACTIVITY -> listOf(
            Line("I'd like that. Let's spend a little time together.", "Gostaria disso. Vamos passar um tempo juntos.", "いいね。少し一緒に過ごそう。"),
            Line("I'm joining you. Let's stay nearby.", "Vou com você. Vamos ficar por perto.", "一緒に行くよ。近くで過ごそう。"),
            Line("Yes, I'm interested. Let's try it together.", "Sim, tenho interesse. Vamos tentar juntos.", "うん、興味がある。一緒にやってみよう。"),
            Line("I accept your invitation. We can start nearby.", "Aceito seu convite. Podemos começar aqui perto.", "誘いを受けるよ。近くから始めよう。"))
        SocialMotive.PLAYFUL_TEASING -> listOf(Line("Oh? I'll see whether you can take a joke back.", "É mesmo? Quero ver se você também aguenta uma piada.", "そう？君も冗談を受け止められるかな。"),
            Line("Keep it friendly and I'm listening.", "Mantenha a brincadeira amigável e eu escuto.", "友好的なら聞くよ。"))
        else -> listOf(Line("I'm listening. Tell me what you had in mind.", "Estou ouvindo. Diga o que você tem em mente.", "聞いているよ。何を考えていたの？"),
            Line("A little conversation could be worthwhile.", "Uma conversa pode valer a pena.", "少し話すのもよさそうだね。"),
            Line("We can take our time with this.", "Podemos conversar sem pressa.", "ゆっくりでいいよ。"),
            Line("I'll share my side, too.", "Vou contar meu lado também.", "私の考えも話すね。"))
    }

    private fun characterLine(type: DigimonPersonalityType, variation: Int): Line {
        val preference = when (type) {
            DigimonPersonalityType.ADORING -> Line("Little moments together matter to me.", "Esses pequenos momentos juntos importam para mim.", "一緒にいる小さな時間を大事にしているんだ。")
            DigimonPersonalityType.DEVOTED -> Line("I prefer getting to know someone properly.", "Prefiro conhecer alguém de verdade.", "相手をきちんと知りたいんだ。")
            DigimonPersonalityType.TOLERANT -> Line("There's no rush to agree with me.", "Não precisa concordar comigo depressa.", "急いで同意しなくてもいいよ。")
            DigimonPersonalityType.OVERPROTECTIVE -> Line("I like knowing everyone has room to feel comfortable.", "Gosto de saber que todos têm espaço para ficar à vontade.", "みんなが安心できる距離を大事にしたい。")
            DigimonPersonalityType.ZEALOUS -> Line("I put my heart into what I choose to do.", "Coloco meu coração no que escolho fazer.", "選んだことには心を込めるよ。")
            DigimonPersonalityType.BRAVE -> Line("I'd rather give something worthwhile a try.", "Prefiro tentar algo que valha a pena.", "価値のあることなら挑戦したい。")
            DigimonPersonalityType.RECKLESS -> Line("I tend to act before I finish planning.", "Costumo agir antes de terminar de planejar.", "考え終える前に動いてしまうんだ。")
            DigimonPersonalityType.DARING -> Line("A new possibility usually catches my interest.", "Uma possibilidade nova costuma me interessar.", "新しい可能性には興味が湧くよ。")
            DigimonPersonalityType.ENLIGHTENED -> Line("Sometimes a quiet moment tells us more.", "Às vezes um momento tranquilo diz mais.", "静かな時間から分かることもあるね。")
            DigimonPersonalityType.SLY -> Line("I enjoy a conversation with a little mystery.", "Gosto de uma conversa com um pouco de mistério.", "少し謎のある会話が好きなんだ。")
            DigimonPersonalityType.ASTUTE -> Line("Small details are often the interesting part.", "Os pequenos detalhes costumam ser a parte interessante.", "小さな違いこそ面白いことがあるよ。")
            DigimonPersonalityType.STRATEGIC -> Line("I like understanding the options before choosing.", "Gosto de entender as opções antes de escolher.", "選ぶ前に選択肢を理解したいんだ。")
            DigimonPersonalityType.OPPORTUNISTIC -> Line("A useful opportunity is worth noticing.", "Vale perceber uma oportunidade útil.", "役に立つ機会は見逃したくないね。")
            DigimonPersonalityType.FRIENDLY -> Line("I like making it easy for people to talk.", "Gosto de deixar as pessoas à vontade para conversar.", "気軽に話せる雰囲気が好きなんだ。")
            DigimonPersonalityType.SOCIABLE -> Line("I enjoy finding out what others think.", "Gosto de descobrir o que os outros pensam.", "みんなの考えを聞くのが好きだよ。")
            DigimonPersonalityType.COMPASSIONATE -> Line("Making room for someone else matters to me.", "Dar espaço a alguém importa para mim.", "相手のために余裕を持ちたいんだ。")
        }
        return if (variation != 2) preference else when {
            type in listOf(DigimonPersonalityType.SOCIABLE, DigimonPersonalityType.FRIENDLY, DigimonPersonalityType.ADORING) ->
                Line("I'd like to hear your side as well.", "Também quero ouvir seu lado.", "君の考えも聞きたいな。")
            type in listOf(DigimonPersonalityType.RECKLESS, DigimonPersonalityType.DARING, DigimonPersonalityType.BRAVE, DigimonPersonalityType.ZEALOUS) ->
                Line("I have a few ideas already.", "Já tenho algumas ideias.", "もういくつか案があるよ。")
            else -> Line("I'll think it over for a moment.", "Vou pensar um pouco nisso.", "少し考えてみるよ。")
        }
    }
}
