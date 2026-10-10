package com.github.nacabaro.vbhelper.battle.offline.tamers

import com.github.nacabaro.vbhelper.battle.offline.core.BattleSpeciesIdentity

data class CanonicalPartnerHistory(val stages: Map<Int, List<String>>, val sourceUri: String)
    data class CanonicalFusionLead(val stage: Int, val species: String, val components: Set<String>, val sourceUri: String,
        /** Fields the combined form only when fighting solo; with a partner the components field and equip it. */
        val soloOnly: Boolean = false)

/**
 * Character histories, not species evolution recommendations. Empty cells mean
 * "not documented here", never "the generic path is canonical". Mode changes,
 * borrowed partners, and armor/Hybrid arena stage adapters are separate policies.
 * Audited 2026-10-10 against the 223 character entries and their linked histories.
 */
object TamerCanonicalPartners {
    const val REVISION = 2

    // id | partner anchor | Baby I | Baby II | Child | Adult | Perfect | Ultimate | character-specific source
    // Semicolon-separated alternatives are documented branches, ordered by the representative story route.
    private val historyRows = """
        taichi-adventure|Agumon|Botamon|Koromon|Agumon|Greymon|Metal Greymon;Skull Greymon|War Greymon|Agumon_(Adventure)
        yamato|Gabumon|Punimon|Tunomon|Gabumon|Garurumon|Were Garurumon|Metal Garurumon|Gabumon_(Adventure)
        sora|Piyomon|Nyokimon|Pyocomon|Piyomon|Birdramon|Garudamon|Hououmon|Piyomon_(Adventure)
        koushiro|Tentomon|Pabumon|Mochimon|Tentomon|Kabuterimon|Atlur Kabuterimon (Red)|Herakle Kabuterimon|Tentomon_(Adventure)
        mimi|Palmon|Yuramon|Tanemon|Palmon|Togemon|Lilimon|Rosemon|Palmon_(Adventure)
        jo|Gomamon|Pitchmon|Pukamon|Gomamon|Ikkakumon|Zudomon|Vikemon;Plesiomon|Gomamon_(Adventure)
        takeru|Patamon|Poyomon|Tokomon|Patamon|Angemon;Pegasmon|Holy Angemon;Shakkoumon|Seraphimon|Patamon_(Adventure)
        hikari|Tailmon|Yukimi Botamon|Nyaromon|Plotmon|Tailmon;Nefertimon|Angewomon;Silphymon|Holydramon;Ofanimon|Tailmon_(Adventure)
        daisuke|V-mon|Chicomon|Chibimon|V-mon|XV-mon;Fladramon;Lighdramon|Paildramon|Imperialdramon Dragon Mode|V-mon_(Adventure)
        ken|Wormmon|Leafmon|Minomon|Wormmon|Stingmon|Paildramon;Dinobeemon|Imperialdramon Dragon Mode;Gran Kuwagamon|Wormmon_(Adventure)
        miyako|Hawkmon|Pururumon|Poromon|Hawkmon|Aquilamon;Holsmon;Shurimon|Silphymon|Valkyrimon|Hawkmon_(Adventure)
        iori|Armadimon|Tsubumon|Upamon|Armadimon|Ankylomon;Digmon;Submarimon|Shakkoumon|Vikemon|Armadimon_(Adventure)
        wallace|Terriermon||Gummymon|Terriermon|Galgomon;Rapidmon Armor|||Terriermon_(Adventure)
        wallace|Lopmon||Chocomon|Lopmon|Wendimon|Andiramon|Cherubimon (Virtue);Cherubimon (Vice)|Lopmon_(Adventure)
        meiko|Meicoomon||Meicoomon's Unnamed Baby Form|Meicoomon's Unnamed Child Form|Meicoomon|Meicrackmon Vicious Mode|Raguelmon|Meicoomon_(Adventure)
        maki|Bakumon|||Bakumon||Megadramon||Bakumon_(Adventure)
        daigo|Bearmon|||Bearmon||Loader Leomon|Baihumon|Bearmon_(Adventure)
        menoa|Morphomon|||Morphomon|Eosmon (Adult)|Eosmon (Perfect)|Eosmon (Ultimate)|Morphomon_(Adventure)
        lui|Ukkomon|||Ukkomon|||Big Ukkomon|Ukkomon_(Adventure)
        michael|Betamon|||Betamon|Seadramon|||Michael
        dingo|Ganimon|||Ganimon|Coelamon|||Dingo
        catherine-adventure|Floramon|||Floramon|Kiwimon|||Catherine_Deneuve
        takato|Guilmon|Jyarimon|Gigimon|Guilmon|Growmon|Megalo Growmon|Dukemon;Megidramon|Guilmon_(Tamers)
        jianliang|Terriermon|Zerimon|Gummymon|Terriermon|Galgomon|Rapidmon|Saint Galgomon|Terriermon_(Tamers)
        ruki|Renamon|Relemon|Pokomon|Renamon|Kyubimon|Taomon|Sakuyamon|Renamon_(Tamers)
        ryo-tamers|Cyberdramon|Ketomon|Hopmon|Monodramon|Strikedramon|Cyberdramon|Justimon|Cyberdramon_(Tamers)
        hirokazu|Guardromon||Caprimon|Kokuwamon|Guardromon|Andromon|Hi Andromon|Guardromon_(Tamers)
        kenta|Marin Angemon|||Penmon|Rukamon|Whamon|Marin Angemon|Marin_Angemon_(Tamers)
        juri|Leomon|||Elecmon|Leomon|Grappu Leomon|Saber Leomon|Leomon_(Tamers)
        juri|Impmon|Keemon|Yarmon|Impmon|Meramon|Death Meramon|Beelzebumon|Impmon_(Tamers)
        shaochung|Lopmon|Conomon|Chocomon|Lopmon|Turuiemon|Andiramon (Deva);Andiramon|Cherubimon (Virtue)|Lopmon_(Tamers)
        ai-makoto|Impmon|Keemon|Yarmon|Impmon|Meramon|Death Meramon|Beelzebumon|Impmon_(Tamers)
        masaru|Agumon (2006)||Koromon|Agumon (2006)|Geo Greymon|Rize Greymon|Shine Greymon|Agumon_(Savers)
        thoma|Gaomon||Wanyamon|Gaomon|Gaogamon|Mach Gaogamon|Mirage Gaogamon|Gaomon_(Savers)
        yoshino|Lalamon||Budmon|Lalamon|Sunflowmon|Lilamon|Rosemon|Lalamon_(Savers)
        ikuto|Falcomon (2006)||Pinamon|Falcomon (2006)|Peckmon|Yatagaramon (2006 Anime Version)|Ravemon|Falcomon_(Savers)
        miki|Pawn Chessmon (Black)|||Pawn Chessmon (Black)|Knight Chessmon (Black)|Rook Chessmon (Black)||Pawn_Chessmon_(Black)_(Savers)
        megumi|Pawn Chessmon (White)|||Pawn Chessmon (White)|Knight Chessmon (White)|Bishop Chessmon (White)||Pawn_Chessmon_(White)_(Savers)
        satsuma|Kudamon (2006)||Kyaromon|Kudamon (2006)|Reppamon|Tyilinmon|Sleipmon|Kudamon_(Savers)
        yushima|Kamemon|||Kamemon|Gawappamon|Shawujinmon|Jumbo Gamemon|Kamemon_(Savers)
        suguru|Bancho Leomon||||||Bancho Leomon|Bancho_Leomon_(Savers)
        hiro|Gammamon|Curimon|Gurimon|Gammamon|Betel Gammamon;Kaus Gammamon;Wezen Gammamon;Gulus Gammamon|Canoweissmon;Regulusmon|Siriusmon|Gammamon_(Ghost_Game)
        hiro|Espimon|||Espimon|Hover Espimon|||Espimon_(Ghost_Game)
        ruli|Angoramon|Pyonmon|Bosamon|Angoramon|Symbare Angoramon|Lamortmon|Diarbbitmon|Angoramon_(Ghost_Game)
        kiyoshiro|Jellymon|Puyomon|Puyoyomon|Jellymon|Tesla Jellymon|Thetismon|Amphimon|Jellymon_(Ghost_Game)
        hokuto|Terriermon Assistant|||Terriermon Assistant||||Terriermon_Assistant_(Ghost_Game)
        takuya|Agnimon|||Flamemon|Agnimon|Aldamon;Vritramon|Kaiser Greymon;Ancient Greymon|Kanbara_Takuya
        kouji|Wolfmon||||Wolfmon|Beowolfmon;Garummon|Magna Garurumon;Ancient Garurumon|Minamoto_Kouji
        tomoki|Chackmon||||Chackmon|Blizzarmon;Daipenmon|Ancient Megatheriumon|Himi_Tomoki
        izumi|Fairimon||||Fairimon|Shutumon;Jet Silphymon|Ancient Irismon|Orimoto_Izumi
        junpei|Blitzmon||||Blitzmon|Bolgmon;Rhino Kabuterimon|Ancient Beatmon|Shibayama_Junpei
        kouichi|Löwemon||||Löwemon;Duskmon|Kaiser Leomon;Velgrmon;Raihimon|Ancient Sphinxmon|Kimura_Kouichi
        taiki|Shoutmon|||Shoutmon|Shoutmon X2|Shoutmon X4|Omega Shoutmon|Shoutmon_(Xros_Wars)
        taiki|Ballistamon||||Ballistamon|||Ballistamon_(Xros_Wars)
        kiriha|Greymon (2010 Anime Version)||||Greymon (2010 Anime Version)|Metal Greymon (2010 Anime Version)|Zeke Greymon|Greymon_(Xros_Wars)
        kiriha|Mail Birdramon||||Mail Birdramon|||Mail_Birdramon_(Xros_Wars)
        nene|Sparrowmon|||Sparrowmon||||Sparrowmon_(Xros_Wars)
        nene|Mervamon||||||Mervamon|Mervamon_(Xros_Wars)
        yuu|Damemon||||Damemon|Tuwarmon||Damemon_(Xros_Wars)
        akari|Dorulumon||||Dorulumon||Yaeger Dorulumon|Dorulumon_(Xros_Wars)
        zenjirou|Ballistamon||||Ballistamon||Atlur Ballistamon|Ballistamon_(Xros_Wars)
        kotone|Sparrowmon|||Sparrowmon|Raptor Sparrowmon|||Sparrowmon_(Xros_Wars)
        tagiru|Gumdramon|||Gumdramon|Arresterdramon|Arresterdramon Superior Mode||Gumdramon_(Xros_Wars)
        ryouma|Psychemon|||Psychemon||Astamon||Psychemon_(Xros_Wars)
        ren|Dracumon|||Dracumon|Yaksamon|||Dracumon_(Xros_Wars)
        airu|Opossumon||||Opossumon|Cho Hakkaimon||Opossummon_(Xros_Wars)
        hideaki|Dobermon||||Dobermon|||Dobermon_(Xros_Wars)
        kiichi|Locomon|||||Locomon||Locomon_(Xros_Wars)
        mizuki|Submarimon||||Submarimon|||Submarimon_(Xros_Wars)
        clock-shop|Clockmon||||Clockmon|||Clockmon_(Xros_Wars)
        taichi-vtamer|V-dramon|Botamon|Koromon|Agumon|V-dramon|Aero V-dramon|Ulforce V-dramon|Zeromaru
        neo|Arkadimon Child||Arkadimon Baby|Arkadimon Child|Arkadimon Adult|Arkadimon Perfect|Arkadimon Ultimate|Arkadimon_(V-Tamer)
        hideto|War Greymon||||||War Greymon|Omegamon_(V-Tamer)
        hideto|Metal Garurumon||||||Metal Garurumon|Omegamon_(V-Tamer)
        mari|Rosemon||||||Rosemon|Rosemon_(V-Tamer)
        sigma|Piemon||||||Piemon|Piemon_(V-Tamer)
        tsurugi|Agumon (2006)|Botamon|Koromon|Agumon (2006);Agumon|Geo Greymon;Greymon|Rize Greymon|Victory Greymon|Agumon_(Next)
        yuu-next|Gaomon||Wanyamon|Gaomon|Gaogamon;Black Gaogamon|Mach Gaogamon|Z'd Garurumon|Gaomon_(Next)
        shou|Peckmon||||Peckmon|Yatagaramon (2006 Anime Version)|Ravemon|Peckmon_(Next)
        ami-next|Pitchmon|Pitchmon|||||Marin Angemon|Pitchmon_(Next)
        kouta-chronicle|DORUmon|Dodomon|Dorimon|DORUmon|DORUgamon;Raptordramon|DORUguremon;Grademon|DORUgoramon;Alphamon|DORUmon_(Chronicle)
        yuuji|Ryudamon|||Ryudamon|Ginryumon|Hisyaryumon|Ouryumon|Ryudamon_(Chronicle)
        long|DORUmon|Dodomon|Dorimon|DORUmon||Grademon;Death-X-DORUguremon|Alphamon;DORUgoramon;Death-X-DORUgoramon|DORUmon_(D-Cyber)
        fang|Ryudamon|||Ryudamon|Ginryumon;Death-X-DORUgamon|Hisyaryumon|Ouryumon;Gaioumon|Ryudamon_(D-Cyber)
        luo|Agumon (X-Antibody)|||Agumon (X-Antibody)|Greymon (X-Antibody);Omekamon|Metal Greymon (X-Antibody)||Agumon_X-Antibody_(D-Cyber)
        ritsu|Pulsemon||Bibimon|Pulsemon|Bulkmon|||Pulsemon_(Dreamers)
        jo-dreamers|Wingdramon|||||Wingdramon||Wingdramon_(Dreamers)
        eiji|Loogamon|Fusamon|Bowmon|Loogamon|Loogarmon|Soloogarmon;Helloogarmon|Fenriloogamon|Loogamon_(Seekers)
        leon|Pulsemon|Dokimon|Bibimon|Pulsemon|Bulkmon|Boutmon|Kazuchimon|Pulsemon_(Seekers)
        yulin|Ryudamon|Fufumon|Kyokyomon|Ryudamon|Ginryumon|Hisyaryumon|Ouryumon|Ryudamon_(Seekers)
        kosuke|DORUmon|Dodomon|Dorimon|DORUmon|DORUgamon;Death-X-DORUgamon|DORUguremon;Death-X-DORUguremon|DORUgoramon;Death-X-DORUgoramon|DORUmon_(Seekers)
        kosuke|Agumon (Black)|||Agumon (Black)||||Agumon_(Seekers)
        satsuki|Numemon||||Numemon|Black King Numemon|Platinum Numemon|Numemon_(Seekers)
        marvin|Airdramon||||Airdramon|Megadramon||Airdramon_(Seekers)
        shoto|Pteromon|Yolkmon|Fluffymon|Pteromon|Galemon|Grand Galemon|Zephagamon;Medieval Dukemon;Vortexdramon|Pteromon_(Liberator)
        shoto|Muchomon|||Muchomon|Cockatrimon|Delumon|Medieval Dukemon|Muchomon_(Liberator)
        arisa|Shoemon|Pafumon|Kyaromon|Shoemon|Shoeshoemon|Chaperomon|Cendrillmon;Nyabootmon|Shoemon_(Liberator)
        owen|Elizamon|Jyarimon|Gigimon|Elizamon|Dimetromon|Lamiamon|Medusamon;Styracomon|Elizamon_(Liberator)
        owen|Agumon||Gigimon|Agumon|Cyclomon|Cyberdramon|War Greymon|Agumon_(Liberator)
        violet|Ghostmon|Mokumon|Peti Meramon|Ghostmon|Bakemon;Soulmon|Fantomon|Necromon;Dullahamon|Ghostmon_(Liberator)
        qinglan|Sangomon|Puyomon|Puyoyomon|Sangomon|Shellmon|Marin Bullmon|Ryugumon;Ariemon|Sangomon_(Liberator)
        cool-boy|Omekamon||||Omekamon||Omegamon (X-Antibody)|Omekamon_(Liberator)
        close|Sunarizamon|Sunamon|Goromon|Sunarizamon|Landramon|Proganomon|Pyramidimon;Magneticdramon|Sunarizamon_(Liberator)
        zenith|BEMmon|Kuramon|Tsumemon|BEMmon|Snatchmon|Destromon|Ragnamon|BEMmon_(Liberator)
        yuuki|Impmon||Yarmon|Impmon|Punkmon|Loudmon|Heavy Metaldramon;Beelzebumon|Impmon_(Liberator)
        saikiyo|Funbeemon||Puroromon|Funbeemon|Forgebeemon|Vespamon|Queenbeemon|Funbeemon_(Liberator)
        ryutaro|Tyranomon|Botamon|Koromon|Agumon|Tyranomon|Master Tyranomon|Dinomon|Tyranomon_(Liberator)
        aruba|Cupimon||Cupimon|||||Aruba_Dokuson
        suzune|Yukidarumon||Hiyarimon|Penmon|Yukidarumon|Polar Bearmon|Skadimon|Yukidarumon_(Liberator)
        altea|Espimon||Caprimon|Espimon|Hover Espimon|Oblivimon;Valvemon|Invisimon;Raidenmon|Espimon_(Liberator)
        chitose-liberator|Gazimon|||Gazimon||Chimairamon||Gazimon_(Liberator)
        mayu-recollection|Agumon|Botamon|Koromon|Agumon|Geo Greymon|Rize Greymon|Shine Greymon|Agumon_(ReCollection)
        mayu-recollection|Terriermon||Gummymon|Terriermon|Galgomon;Rapidmon Armor|||Terriermon_(ReCollection)
        eve|Kuwagamon||||Kuwagamon|Metal Tyranomon|Mugendramon|Kuwagamon_(Paradox)
        hajime|Gankoomon||||||Gankoomon|Gankoomon_(Knuckles)
        haruka|Gankoomon||||||Gankoomon|Gankoomon_(Knuckles)
        mameo|Mamemon|Yuramon||Agumon;Gabumon;Palmon|Tyranomon;Garurumon;Vegimon;Scumon|Mamemon;Metal Greymon|Mugendramon|Mamemon_(World)
        koh|Coronamon||Sunmon|Coronamon|Firamon|Flaremon|Apollomon|Coronamon_(Moonlight)
        sayo|Lunamon||Moonmon|Lunamon|Lekismon|Crescemon|Dianamon|Lunamon_(Sunburst)
        glare|Ofanimon||||||Ofanimon|Ofanimon_(Sunburst)
        julia|Chaos Dukemon||||||Chaos Dukemon|Chaos_Dukemon_(Moonlight)
        taiga|Agumon|||Agumon|Greymon|Metal Greymon|War Greymon|Digimaru
        taiga|Plotmon (X-Antibody)|||Plotmon (X-Antibody);Plotmon||||Plotmon_(Re:Digitize)
        akiho|Piyomon||Pyocomon|Piyomon|Birdramon|Garudamon|Hououmon;Examon|Digitorin
        nicolai|Gaomon|||Gaomon|Gaogamon|Mach Gaogamon|Mirage Gaogamon;Magnamon|Sashenka
        nicolai|Agumon|||Agumon|Greymon|Metal Greymon|War Greymon|Digimaru
        mirei|Angewomon|||||Angewomon|Ofanimon;Mastemon|Angewomon_(Re:Digitize)
        mirei|Lady Devimon|||||Lady Devimon|Lilithmon;Mastemon|Lady_Devimon_(Re:Digitize)
        yuuya|Black War Greymon (X-Antibody)||||||Black War Greymon (X-Antibody);Imperialdramon Fighter Mode|Black
        yuuya|DORUgamon|||DORUmon|DORUgamon|DORUguremon|Alphamon|DORUmon_(X-evolution)
        petrov|Rize Greymon|||||Rize Greymon|Shine Greymon|Rize_Greymon_(Re:Digitize)
        rina|V-dramon|||V-mon|V-dramon|Aero V-dramon|Ulforce V-dramon|V.V.
        lili|Numemon||||Numemon;Scumon|Monzaemon|Lilithmon|Catherine_(Re:Digitize)
        sebastian|Angemon||||Angemon|Holy Angemon|Seraphimon|Angemon_(Re:Digitize)
        nokia|Agumon|||Agumon|Greymon||War Greymon|Agumon_(Cyber_Sleuth)
        nokia|Gabumon|||Gabumon|Garurumon||Metal Garurumon|Gabumon_(Cyber_Sleuth)
        arata|Keramon|Kuramon|Tsumemon|Keramon|Chrysalimon|Infermon|Diablomon|Keramon_(Cyber_Sleuth)
        yuuko|Rize Greymon||||Geo Greymon|Rize Greymon|Gaioumon|Rize_Greymon_(Cyber_Sleuth)
        fei|Tiger Vespamon||||||Tiger Vespamon|Tiger_Vespamon_(Cyber_Sleuth)
        erika|Wormmon|||Wormmon|Hudiemon|||Wormmon_(Cyber_Sleuth)
        ryuji|Cyberdramon|||Monodramon|Strikedramon|Cyberdramon|Justimon|Cyberdramon_(Cyber_Sleuth)
        ryuji|Arkadimon Child||Arkadimon Baby|Arkadimon Child|Arkadimon Adult|Arkadimon Perfect|Arkadimon Ultimate|Arkadimon_(Cyber_Sleuth)
        chitose|Ankylomon||||Ankylomon|Shakkoumon||Ankylomon_(Cyber_Sleuth)
        takuto|Agumon|||Agumon|||War Greymon|Digimon_World_-next_0rder-#Characters
        takuto|Gabumon|||Gabumon|||Metal Garurumon|Digimon_World_-next_0rder-#Characters
        shiki|Piyomon|||Piyomon||||Shiki
        shiki|Palmon|||Palmon||||Shiki
        kouta-next|Guilmon|Jyarimon|Gigimon|Guilmon|Growmon|Megalo Growmon;Skull Greymon|Dukemon|Yukimura
        himari|Plotmon|||Plotmon|Tailmon|Angewomon|Holydramon|Rikka
        rearise-player|Herissmon|Pusumon|Pusurimon|Herissmon|Filmon|Stiffilmon|Rasenmon|Herissmon_(ReArise)
        takumi-rearise|DORUmon|||DORUmon|DORUgamon|DORUguremon|Gaioumon|DORUmon_(ReArise)
        michi|Plotmon|||Plotmon|Tailmon|Angewomon|Lovely Angemon|Plotmon_(ReArise)
        keito|Elecmon|||Elecmon|Leomon|Grappu Leomon|Heavy Leomon|Elecmon_(ReArise)
        mayu-rearise|Kudamon|||Kudamon|Reppamon|Tyilinmon|Mitamamon|Kudamon_(ReArise)
        nozomi|Pumpmon|||||Pumpmon|Noble Pumpmon|Pumpmon_(ReArise)
        chihiro|Lopmon|||Lopmon||Andiramon (Deva)||Lopmon_(ReArise)
        mon|Hackmon|||Huckmon|Bao Huckmon|Savior Huckmon|Jesmon|Huckmon_(ReArise)
        kazuma|Bearmon|Botamon|Wanyamon|Bearmon|Gryzmon|Cerberumon||Bearmon_(ReArise)
        takuma|Agumon||Koromon|Agumon|Greymon;Tyranomon;Tuskmon|Metal Greymon;Triceramon;Megadramon|War Greymon;Dinorexmon;Mugendramon;Huanglongmon|Agumon_(Survive)
        minoru|Falcomon||Pinamon|Falcomon|Diatrymon|Yatagaramon|Valdurmon;Zhuqiaomon|Falcomon_(Survive)
        aoi|Labramon||Xiaomon|Labramon|Dobermon|Cerberumon|Anubimon;Plutomon;Baihumon|Labramon_(Survive)
        saki|Floramon||Pyocomon|Floramon|Vegimon|Blossomon|Ceresmon Medium;Xuanwumon|Floramon_(Survive)
        ryo-survive|Kunemon||Tokomon|Kunemon|Flymon|Jewelbeemon|Bancho Stingmon|Kunemon_(Survive)
        shuuji|Lopmon||Chocomon|Lopmon|Turuiemon;Wendimon|Andiramon (Deva)|Cherubimon (Virtue)|Lopmon_(Survive)
        kaito|Dracumon||Tsumemon|Dracumon|Sangloupmon|Vamdemon|Beelzebumon;Voltobautamon|Dracumon_(Survive)
        miu|Shakomon||Pukamon|Shakomon|Shellmon|Mermaimon|Marin Angemon;Qinglongmon|Shakomon_(Survive)
        inori|Aegiomon||Tunomon|Elecmon|Aegiomon|Aegiochusmon;Aegiochusmon (Blue);Aegiochusmon (Green);Aegiochusmon (Dark);Aegiochusmon (Holy)|Jupitermon|Aegiomon_(Time_Stranger)
        asuna|Black Tailmon||||Black Tailmon||Beel Starmon|Black_Tailmon_(Time_Stranger)
        tomoro|Gekkomon|Kekomon|Kekkomon|Gekkomon|Arma Lizamon|Monarch Lizamon|Atratusmon;Volvemon|Gekkomon_(Beatbreak)
        reina-beatbreak|Pristimon|Pafumon|Kyaromon|Pristimon|Wolvermon|Bearcatmon|Artiomon|Pristimon_(Beatbreak)
        makoto-beatbreak|Chiropmon|Zurumon|Pagumon|Chiropmon|Night Chiropmon|Scourge Chiropmon|Nosferamon|Chiropmon_(Beatbreak)
        kyo|Murasamemon|Popomon|Frimon|Liollmon|Cougarmon|Murasamemon|Habakirimon|Murasamemon_(Beatbreak)
        raito|Monodramon|Ketomon||Monodramon|Rhamphomon|Azhdarmon||Monodramon_(Beatbreak)
        hotaruko|Shakomon|Puyomon||Shakomon|Tylomon|||Shakomon_(Beatbreak)
        granit|Ludomon|Cotsucomon||Ludomon|Tia Ludomon|||Ludomon_(Beatbreak)
        haruomi|Commandramon|Bommon||Commandramon||||Sone_Haruomi
        haruomi|Elizamon|||Elizamon||||Sone_Haruomi
        ichinosuke|Vulturemon|Sunamon||||Vulturemon||Ichinosuke
        nichika|Sealsdramon|Bommon|||Sealsdramon|||Nichika
        sanpei|Hi-Commandramon|Bommon|||Hi-Commandramon|||Sanpei
        meto|Toropiamon|||Pomumon||Toropiamon||Toropiamon_(Beatbreak)
        kanon|Oleamon|||Alraumon||Oleamon||Oleamon_(Beatbreak)
        haruko|Shademon||||Shademon|||Yamada_Haruko
        handa|Starmon||||Starmon|||Handa
        miharu|Mephismon|Mokumon|||Wizarmon|Mephismon||Mephismon_(Beatbreak)
        hori|Jokermon|Mokumon||||Jokermon||Hori
        riku|Loogarmon|Fusamon|||Loogarmon|Helloogarmon||Hosho_Riku
        sana|Bombermon|Curimon||||Bombermon||Tsujimine_Sana
        mitsuo|Funbeemon|Pupumon||Funbeemon|Waspmon|||Minezaki_Mitsuo
        hiroichi|Greymon|Botamon|||Greymon|||Hiroichi
        manabu|Moosemon|Pafumon|||Moosemon|||Akasaka_Manabu
        yume|Tinkermon|||Tinkermon||||Ikuhara_Yume
        hitomi|Cutemon|Pabumon|Pyocomon|Cutemon||||Shinomiya_Hitomi
    """.trimIndent()

    val histories: Map<Pair<String, String>, CanonicalPartnerHistory> = historyRows.lines().associate { line ->
        val cells = line.trim().split('|')
        require(cells.size == 9) { "Invalid canonical history: ${cells.firstOrNull()}" }
        val stages = (0..5).mapNotNull { stage -> cells[stage + 2].takeIf(String::isNotBlank)?.let {
            stage to it.split(';').distinctBy(BattleSpeciesIdentity::normalize)
        } }.toMap()
        (cells[0] to BattleSpeciesIdentity.normalize(cells[1])) to
            CanonicalPartnerHistory(stages, "https://wikimon.net/${cells[8]}")
    }

    fun history(tamerId: String, anchor: String): CanonicalPartnerHistory? =
        histories[tamerId to BattleSpeciesIdentity.normalize(anchor)]

    private fun fusion(stage: Int, species: String, source: String, vararg components: String, soloOnly: Boolean = false) =
        CanonicalFusionLead(stage, species, components.toSet(), "https://wikimon.net/$source", soloOnly)

    /** Owned components disappear into one canonical partner; no slot may reuse any component. */
    val fusionLeads = mapOf(
        "mirei" to listOf(fusion(5, "Mastemon", "Angewomon_(Re:Digitize)", "mirei:primary", "mirei:secondary")),
        "nokia" to listOf(fusion(5, "Omegamon", "Agumon_(Cyber_Sleuth)", "nokia:primary", "nokia:secondary")),
        "hideto" to listOf(fusion(5, "Omegamon", "Omegamon_(V-Tamer)", "hideto:primary", "hideto:secondary")),
        "takuto" to listOf(fusion(5, "Omegamon", "Digimon_World_-next_0rder-#Characters", "takuto:primary", "takuto:secondary")),
        "shiki" to listOf(fusion(5, "Omegamon", "Digimon_World_-next_0rder-#Characters", "shiki:primary", "shiki:secondary")),
        "kiriha" to listOf(fusion(4, "Metal Greymon (2010 Anime Version)", "Greymon_(Xros_Wars)", "kiriha:primary", "kiriha:secondary"),
            fusion(5, "Zeke Greymon", "Greymon_(Xros_Wars)", "kiriha:primary", "kiriha:secondary")),
        "taiki" to listOf(fusion(3, "Shoutmon X2", "Shoutmon_(Xros_Wars)", "taiki:primary", "taiki:secondary"),
            fusion(4, "Shoutmon X4", "Shoutmon_(Xros_Wars)", "taiki:primary", "taiki:secondary", "taiki:other:dorulumon", "taiki:other:starmons")),
        "eve" to listOf(fusion(4, "Metal Tyranomon", "Kuwagamon_(Paradox)", "eve:primary", "eve:secondary"),
            fusion(5, "Mugendramon", "Kuwagamon_(Paradox)", "eve:primary", "eve:secondary")),
        // Shoma fights alone as Noir (Alter-B); beside Kuzuhamon he fields Gaioumon and they equip the Jogress.
        "shoma" to listOf(fusion(5, "Omegamon Alter-B", "Omegamon_Alter-B", "shoma:primary", "shoma:secondary", soloOnly = true)),
    )

    /** Fusions with another tamer's partner still consume that partner's identity. */
    fun extraComponents(tamerId: String, species: String): Set<String> {
        val name = BattleSpeciesIdentity.normalize(species)
        return when {
            tamerId in setOf("daisuke", "ken") && name in setOf("paildramon", "imperialdramondragonmode", "imperialdramonfightermode") -> setOf("daisuke:primary", "ken:primary")
            tamerId in setOf("miyako", "hikari") && name == "silphymon" -> setOf("miyako:primary", "hikari:primary")
            tamerId in setOf("iori", "takeru") && name == "shakkoumon" -> setOf("iori:primary", "takeru:primary")
            tamerId == "chitose" && name == "shakkoumon" -> setOf("chitose:primary", "chitose:other:angemon")
            else -> emptySet()
        }
    }

    /** This is one partner inherited by two humans, not two Gankoomon instances. */
    fun sharedPartnerId(tamerId: String, anchor: String): String? =
        if (tamerId in setOf("hajime", "haruka") && BattleSpeciesIdentity.normalize(anchor) == "gankoomon")
            "partner:gankoomon-knuckles" else null

    // Only documented companions, not arbitrary tamers who happen to share a species line.
    // Fusion partners are appended last: they only win the slot when they complete
    // an approved fusion with the lead at the requested stage (see resolve()).
    val guestAlternatives = mapOf(
        "mirei" to listOf("rina", "taiga", "akiho", "nicolai"),
        "nokia" to listOf("arata", "takumi-cs", "ami-cs", "yuuko"),
        "hideto" to listOf("taichi-vtamer", "mari", "sigma"),
        "daisuke" to listOf("miyako", "iori", "takeru", "hikari", "ken"),
        "ken" to listOf("miyako", "iori", "takeru", "hikari", "daisuke"),
        "miyako" to listOf("daisuke", "ken", "iori", "takeru", "hikari"),
        "hikari" to listOf("takeru", "daisuke", "ken", "iori", "miyako"),
        "iori" to listOf("daisuke", "ken", "miyako", "hikari", "takeru"),
        "takeru" to listOf("hikari", "daisuke", "ken", "miyako", "iori"),
        "wallace" to listOf("daisuke", "takeru", "hikari"),
        "kiriha" to listOf("taiki", "nene", "yuu"),
        "taiki" to listOf("yuu", "kiriha", "nene"),
        "nene" to listOf("taiki", "yuu", "kiriha"),
        "mameo" to listOf("takuto", "kouta-next", "himari"),
        "takuto" to listOf("kouta-next", "himari", "mameo"),
        "shiki" to listOf("himari", "kouta-next", "mameo"),
    )

    // id | independently documented owned/commanded partners. Used NPC teams are not fabricated evolution chains.
    private val companionRows = """
        yulin|Palmon
        kosuke|Tentomon
        taiki|Dorulumon;Starmons;Beelzebumon (2010 Anime Version);Cutemon;Monitamon;Jijimon
        kiriha|Cyberdramon (2010 Anime Version);Deckerdramon;Dracomon
        nene|Monitamon;Monimon
        yuu|Dark Knightmon
        akira|Agumon;Patamon;Pico Devimon
        vandar|Metal Mamemon;Mamemon;Prince Mamemon
        esmeralda|Tentomon;Piyomon;Bakumon;Pidmon;Unimon;Birdramon
        arc-schultz|Saberdramon;Gururumon;Shima Unimon;Angewomon;Holy Angemon;Garudamon
        blanc|Garudamon;Aero V-dramon;Mammon
        cecilia|Holydramon;Marin Angemon;Jijimon
        zudokan|Mega Seadramon;Lilimon;Triceramon;Blossomon;Delumon;Pumpmon
        vivi|Palmon;Mori Shellmon;Jungle Mojyamon;Togemon;Mamemon;Piccolomon;Lilimon;Blossomon
        yaruyaru|Elecmon;Gottsumon;Floramon;Centalmon;Monochromon;Tyranomon
        ben|Centalmon;Meramon;Metal Mamemon
        skull|Hagurumon;Pico Devimon;Pinochimon
        shina|Otamamon;Pico Devimon;Gazimon;Devimon;Gesomon;Guardromon
        klon|Bakemon;Fantomon;Megadramon;Soulmon;Vamdemon;Woodmon;Vademon
        kiruri|Megadramon;Tekkamon;Waru Monzaemon
        crimson|Vamdemon;Deltamon;Ex-Tyranomon;Skull Mammon;Venom Vamdemon;Pumpmon
        damedabose|Etemon;Orgemon;Platinum Scumon;Waru Monzaemon;Nanimon;Jyureimon;Atlur Kabuterimon (Blue);Pinochimon;Red Vegimon;Gerbemon;Gran Kuwagamon;Ex-Tyranomon
        ayumi|Wizarmon;Seadramon;Akatorimon
        atsushi|Guilmon;Patamon;Kotemon;Renamon;Koemon;Agumon;Bearmon;V-mon
        kazuya|Gabumon;Cherubimon (Vice);Venom Vamdemon
        kain|Atlur Kabuterimon (Red);Gran Kuwagamon;Zhuqiaomon
        yuji-story|Cyberdramon;Black Saint Galgomon;Imperialdramon Dragon Mode (Black)
        peter|Black Rapidmon;Infermon
        sueann|Mugendramon;Lady Devimon;Parasimon
        chan|Cannondramon;Cyberdramon
        chris|Agumon (2006);Greymon;Metal Greymon
        robert|Dukemon;Diablomon
        sarah|Demon;Gigadramon
        takumi-cs|Terriermon;Palmon;Hagurumon
        ami-cs|Palmon;Terriermon;Hagurumon
        keisuke|Betamon;Tentomon;Gottsumon
        chitose|Angemon;Shakomon;Seadramon;Whamon;Unimon;Hippogriffomon
        takuto|Agumon;Gabumon
        shiki|Piyomon;Palmon
        shoma|Gaioumon;Kuzuhamon;Titamon;Omegamon Zwart Defeat
        dan|Patamon;Gomamon;Pico Devimon
        kanan|Gomamon;Pico Devimon;Patamon
    """.trimIndent()
    val companions: Map<String, List<String>> = companionRows.lines().associate {
        val cells = it.trim().split('|')
        cells[0] to cells[1].split(';')
    }

    /** Mode changes follow the actual owner even when the partner is a guest. */
    private val modeRows = """
        takato|Dukemon|Dukemon Crimson Mode
        ruki|Sakuyamon|Sakuyamon Miko Mode
        ai-makoto|Beelzebumon|Beelzebumon Blast Mode
        juri|Beelzebumon|Beelzebumon Blast Mode
        masaru|Shine Greymon|Shine Greymon Burst Mode
        masaru|Agumon (2006)|Agumon Burst Mode
        thoma|Mirage Gaogamon|Mirage Gaogamon Burst Mode
        yoshino|Rosemon|Rosemon Burst Mode
        ikuto|Ravemon|Ravemon Burst Mode
        suguru|Bancho Leomon|Bancho Leomon Burst Mode
        daisuke|Imperialdramon Dragon Mode|Imperialdramon Fighter Mode
        ken|Imperialdramon Dragon Mode|Imperialdramon Fighter Mode
        taichi-vtamer|Ulforce V-dramon|Ulforce V-dramon Future Mode
        neo|Arkadimon Ultimate|Arkadimon Super Ultimate
        ryuji|Arkadimon Ultimate|Arkadimon Super Ultimate
        takumi-rearise|Gaioumon|Gaioumon Itto Mode
        mayu-recollection|Shine Greymon|Shine Greymon Burst Mode
        inori|Jupitermon|Jupitermon Wrath Mode
        nokia|Omegamon|Omegamon (X-Antibody)
    """.trimIndent()
    private val modes = modeRows.lines().associate {
        val cells = it.trim().split('|')
        (cells[0] to BattleSpeciesIdentity.normalize(cells[1])) to cells[2]
    }
    fun mode(tamerId: String, species: String): String? = modes[tamerId to BattleSpeciesIdentity.normalize(species)]

    /** Species compatibility alone is not evidence that an NPC tamer uses that fusion. */
    val permittedJogress = mapOf(
        "taichi-adventure" to setOf("Omegamon"), "yamato" to setOf("Omegamon"),
        "nokia" to setOf("Omegamon"), "hideto" to setOf("Omegamon"),
        "takuto" to setOf("Omegamon"), "shiki" to setOf("Omegamon"), "takuma" to setOf("Omegamon"),
        "daisuke" to setOf("Paildramon"), "ken" to setOf("Paildramon"),
        "miyako" to setOf("Silphymon"), "hikari" to setOf("Silphymon"),
        "iori" to setOf("Shakkoumon"), "takeru" to setOf("Shakkoumon"), "chitose" to setOf("Shakkoumon"),
        "mirei" to setOf("Mastemon"),
        "kouta-chronicle" to setOf("Alphamon Ouryuken"), "yuuji" to setOf("Alphamon Ouryuken"),
        "long" to setOf("Alphamon Ouryuken"), "fang" to setOf("Alphamon Ouryuken"),
        "eiji" to setOf("Fenriloogamon Takemikazuchi"), "leon" to setOf("Fenriloogamon Takemikazuchi"),
        "eve" to setOf("Metal Tyranomon"),
        "shoma" to setOf("Omegamon Alter-B"),
        "taiki" to setOf("Shoutmon X2"),
        "kiriha" to setOf("Metal Greymon (2010 Anime Version)"),
    )

    /** Documented two-component combinations, shared by team resolution and loadouts. */
    val jogressPairs = listOf(
        Triple("War Greymon", "Metal Garurumon", "Omegamon"),
        Triple("XV-mon", "Stingmon", "Paildramon"),
        Triple("Aquilamon", "Tailmon", "Silphymon"),
        Triple("Ankylomon", "Angemon", "Shakkoumon"),
        Triple("Angewomon", "Lady Devimon", "Mastemon"),
        Triple("Alphamon", "Ouryumon", "Alphamon Ouryuken"),
        Triple("Fenriloogamon", "Kazuchimon", "Fenriloogamon Takemikazuchi"),
        Triple("Kuwagamon", "Dark Tyranomon", "Metal Tyranomon"),
        Triple("Gaioumon", "Kuzuhamon", "Omegamon Alter-B"),
        Triple("Shoutmon", "Ballistamon", "Shoutmon X2"),
        Triple("Greymon (2010 Anime Version)", "Mail Birdramon", "Metal Greymon (2010 Anime Version)"),
    )

    /** Whether fielding [second] beside [first] enables an approved fusion for [tamerId]. */
    fun completesApprovedFusion(tamerId: String, first: String, second: String): Boolean {
        val pair = setOf(BattleSpeciesIdentity.normalize(first), BattleSpeciesIdentity.normalize(second))
        val approved = permittedJogress[tamerId].orEmpty().map { BattleSpeciesIdentity.normalize(it) }.toSet()
        return jogressPairs.any { entry ->
            setOf(BattleSpeciesIdentity.normalize(entry.first), BattleSpeciesIdentity.normalize(entry.second)) == pair &&
                BattleSpeciesIdentity.normalize(entry.third) in approved
        }
    }
}
