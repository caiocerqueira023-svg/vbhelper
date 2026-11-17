// (2026-11-12, pedido del usuario) LECTOR DE VPETS para DBT Link (APK de prueba): lee un DM20, DMX, Pendulum Ver.20th (pen20), Pendulum Z (penz),
// Digital Monster Color (dmc) o Pendulum Color (penc) por el A-Com (USB) o el BTCom (Bluetooth).
// Es el mismo protocolo que usa el juego web (fuentes/juego/js/03-002 … 03-008): para los de paquetes de 16 bits el A-Com manda una DigiROM «V2-…»
// (espera a que el vpet inicie el combate) y el vpet contesta con paquetes «r:XXXX». La última verificación va mal a propósito: el vpet entrega sus
// datos y muestra «error» en vez de combatir. Los Color usan bloques de 16 bytes («C1-…»/«C2-…», respuestas «r:47444C43…» o «r:47444470…»).
// Kotlin (VpetLink) solo mueve el texto: window.vpetLinea(linea) y window.vpetEstado({ok,msg,modo}).
// Las listas de nombres van dentro (bloque LISTAS) y se regeneran con lector-gen/generar.js a partir de las tablas del juego.
window.LECTOR = (() => {
  const A = window.Android || null;
  const nib = p => p.reduce((t, x) => t + ((x >> 12) & 15) + ((x >> 8) & 15) + ((x >> 4) & 15) + (x & 15), 0);
  const hex4 = x => ("000" + x.toString(16).toUpperCase()).slice(-4);
  const bits = (n, a, l) => (n >> (16 - a - l)) & ((1 << l) - 1);
  /*LISTAS-INICIO (generado por lector-gen/generar.js desde fuentes/juego/js; no editar a mano)*/
  const LISTAS = {"dm20":"|Koromon~0~3|Agumon~1~0|Betamon~1~2|Greymon~2~0|Tyranomon~2~1|Devimon~2~2|Meramon~2~1|Airdramon~2~0|Seadramon~2~1|Numemon~2~2|Metal Greymon (Virus)~3~2|Mamemon~3~1|Monzaemon~3~0|Blitz Greymon~4~2|Bancho Mamemon~4~1||Tsunomon~0~3|Gabumon~1~1|Elecmon~1~1|Kabuterimon~2~0|Garurumon~2~0|Angemon~2~0|Yukidarumon~2~0|Birdramon~2~0|Whamon~3~0|Vegimon~2~2|Skull Greymon~3~2|Metal Mamemon~3~1|Vademon~3~2|Skull Mammon~4~0|Cres Garurumon~4~1||Tokomon~0~3|Patamon~1~1|Kunemon~1~2|Unimon~2~0|Centalmon~2~1|Ogremon~2~2|Bakemon~2~2|Shellmon~2~1|Drimogemon~2~1|Scumon~2~2|Andromon~3~0|Giromon~3~0|Etemon~3~2|Hi Andromon~4~0|King Etemon~4~2||Tanemon~0~3|Piyomon~1~0|Palmon~1~1|Monochromon~2~1|Cockatrimon~2~1|Leomon~2~0|Kuwagamon~2~2|Coelamon~2~1|Mojyamon~2~0|Nanimon~2~2|Megadramon~3~2|Piccolomon~3~1|Digitamamon~3~1|Aegisdramon~4~0|Titamon~4~2||Pagumon~0~3|Gazimon~1~2|Gizamon~1~2|Dark Tyranomon~2~2|Cyclomon~2~2|Devidramon~2~2|Tuskmon~2~2|Flymon~2~2|Deltamon~2~2|Raremon~2~2|Metal Tyranomon~3~2|Nanomon~3~2|Ex-Tyranomon~3~0|Mugendramon~4~2|Pinochimon~4~2||Sakuttomon~0~3|Zubamon~1~0|Zubaeagermon~2~0|Duramon~3~0|Durandamon~4~0|Hackmon~1~1|Bao Hackmon~2~1|Savior Hackmon~3~1|Jesmon~4~1||Babydmon~0~3|Dracomon~1~1|Coredramon (Blue)~2~0|Wingdramon~3~0|Slayerdramon~4~0|Coredramon (Green)~2~2|Groundramon~3~2|Breakdramon~4~2||Pukamon~0~3|Coronamon~1~0|Firamon~2~0|Flaremon~3~0|Apollomon~4~0|Lunamon~1~1|Lekismon~2~1|Crescemon~3~1|Dianamon~4~1|Agumon (Ver.20th)~1~0|Greymon (Ver.20th)~2~0|Metal Greymon (Vaccine)~3~0|War Greymon~4~0|Gabumon (Ver.20th)~1~1|Garurumon (Ver.20th)~2~0|Were Garurumon~3~0|Metal Garurumon~4~1||Dorimon~0~3|DORUmon~1~1|DORUgamon~2~1|DORUguremon~3~1|Alphamon~4~0||Nyaromon~0~3|Plotmon~1~0|Meicoomon~2~3|Meicrackmon~3~0|Rasielmon~4~0|Omegamon Alter-S~4~2|Rust Tyranomon~4~2|Examon~4~1|Grace Novamon~4~0|Omegamon~4~0","pen20":"||Mochimon~0~3|Tentomon~1~0|Gottsumon~1~1|Otamamon~1~2|Kabuterimon~2~0|Tortamon~2~0|Monochromon~2~1|Starmon~2~1|Gekomon~2~2|Kuwagamon~2~2|Tailmon~2~0|Atlur Kabuterimon~3~0|Jyagamon~3~0|Triceramon~3~1|Piccolomon~3~1|Tonosama Gekomon~3~2|Okuwamon~3~2|Angewomon~3~0|Herakle Kabuterimon~4~0|Saber Leomon~4~1|Holydramon~4~0|Metal Etemon~4~2||Pukamon~0~3|Gomamon~1~0|Ganimon~1~1|Shakomon~1~2|Rukamon~2~0|Ikkakumon~2~0|Seadramon~2~1|Coelamon~2~1|Octmon~2~2|Gesomon~2~2|Ebidramon~2~1|Whamon~3~0|Zudomon~3~0|Mega Seadramon~3~1|Anomalocarimon~3~1|Dagomon~3~2|Marin Devimon~3~2|Hangyomon~3~1|Marin Angemon~4~0|Metal Seadramon~4~1|Pukumon~4~2|Plesiomon~4~1||Peti Meramon~0~3|Bakumon~1~0|Candmon~1~1|Pico Devimon~1~2|Hanumon~2~0|Garurumon~2~0|Meramon~2~1|Wizarmon~2~1|Devimon~2~2|Bakemon~2~2|Dokugumon~2~2|Mammon~3~0|Were Garurumon~3~0|Death Meramon~3~1|Pumpmon~3~1|Vamdemon~3~2|Fantomon~3~2|Lady Devimon~3~2|Skull Mammon~4~0|Boltmon~4~1|Demon~4~2|Piemon~4~2||Pyocomon~0~3|Piyomon~1~0|Floramon~1~1|Mushmon~1~2|Palmon~1~1|V-dramon~2~0|Birdramon~2~0|Togemon~2~1|Kiwimon~2~1|Woodmon~2~2|Red Vegimon~2~2|Aero V-dramon~3~0|Garudamon~3~0|Blossomon~3~1|Delumon~3~1|Jyureimon~3~2|Gerbemon~3~2|Lilimon~3~1|Hououmon~4~0|Griffomon~4~1|Pinochimon~4~2|Rosemon~4~1||Caprimon~0~3|Toy Agumon~1~0|Kokuwamon~1~1|Hagurumon~1~2|Greymon~2~0|Revolmon~2~0|Tankmon~2~1|Clockmon~2~1|Guardromon~2~2|Mechanorimon~2~2|Thunderballmon~2~1|Metal Greymon (Vaccine)~3~0|Andromon~3~0|Knightmon~3~1|Big Mamemon~3~1|Megadramon~3~2|Waru Monzaemon~3~2|Cyberdramon~3~0|War Greymon~4~0|Metal Garurumon~4~1|Mugendramon~4~2|Venom Vamdemon~4~2||Nyaromon~0~3|Agumon~1~0|Gabumon~1~1|Plotmon~1~0|Leomon~2~0|Igamon~2~1|Angemon~2~0|Asuramon~3~0|Metal Mamemon~3~1|Holy Angemon~3~0|Seraphimon~4~0|Ofanimon~4~0|Phascomon~1~2|Impmon~1~2|Porcupamon~2~2|Mad Leomon~2~3|Shadramon~2~3|Troopmon~2~3|Astamon~3~2|Baalmon~3~3|Dark Knightmon~3~3|Belphemon: Rage Mode~4~2|Beelzebumon~4~2||Koromon~0~3|Guilmon~1~2|Bushi Agumon~1~0|Growmon~2~2|Reppamon~2~0|Arresterdramon~2~3|Manbomon~2~3|Megalo Growmon~3~2|Beowolfmon~3~3|Rize Greymon~3~0|Dukemon~4~2|Duftmon~4~1||Sakuttomon~0~3|Zubamon~1~0|Hackmon~1~1|Zubaeagermon~2~0|Bao Hackmon~2~1|Targetmon~2~2|Duramon~3~0|Savior Hackmon~3~1|Etemon~3~2|Durandamon~4~0|Jesmon~4~1|Jesmon X~5~1|Bancho Leomon~4~0||Babydmon~0~3|Dracomon~1~1|Agumon Hakase~1~0|Coredramon (Blue)~2~0|Coredramon (Green)~2~2|Devidramon~2~2|Wingdramon~3~0|Groundramon~3~2|Slayerdramon~4~0|Breakdramon~4~2|Darkdramon~4~2||Gummymon~0~3|Terriermon~1~0|Galgomon~2~0|Rapidmon~3~0|Saint Galgomon~4~0||Chocomon~0~3|Lopmon~1~1|Turuiemon~2~1|Andiramon~3~2|Cherubimon~4~0||Kakkinmon~0~3|Ludomon~1~1|Tia Ludomon~2~1|Raiji Ludomon~3~1|Bryweludramon~4~1|Vorvomon~1~2|Lavorvomon~2~2|Lavogaritamon~3~2|Volcanicdramon~4~2||Chibimon~0~3|V-mon~1~3|XV-mon~2~3|Ulforce V-dramon~4~0|Meicoomon~2~3|Meicrackmon: Vicious Mode~3~3|Raguelmon~4~3||Kyokyomon~0~3|Ryudamon~1~0|Ginryumon~2~0|Hisyaryumon~3~0|Ouryumon~4~0|Budmon~0~3|Lalamon~1~1|Sunflowmon~2~1|Lilamon~3~1|Lotusmon~4~1||Dorimon~0~3|DORUmon~1~1|DORUgamon~2~1|DORUguremon~3~1|DORUgoramon~4~1|Alphamon~4~0|Omegamon~5~0|Ogudomon~5~2|Ordinemon~5~3|Paildramon~3~3|Imperialdramon: Fighter Mode~5~3|Voltobautamon~5~2|Beelzebumon: Blast Mode~5~2|Ragna Lordmon~5~2|Alphamon: Ouryuken~5~0|Rafflesimon~5~1|Mastemon~5~0|Chaosmon~5~0|Examon~5~1|Imperialdramon: Paladin Mode~5~3","z1":"|Wanyamon~0~3|Gaomon~1~1|Morphomon~1~0|Sunarizamon~1~2|Plotmon~1~0|Baboongamon~2~0|Unimon~2~0|Hudiemon~2~3|Gaogamon~2~1|Golemon~2~2|Gokimon~2~2|Tailmon~2~0|Gogmamon~3~0|Jewelbeemon~3~0|Scorpiomon~3~1|Mach Gaogamon~3~1|Orochimon~3~2|Archnemon~3~2|Angewomon~3~0|Ancient Beatmon~4~0|Holydramon~4~0|Baihumon~4~1|Mirage Gaogamon~4~1|Nidhoggmon~4~2|Ancient Volcamon~4~2|Ofanimon~4~0||Upamon~0~3|Swimmon~1~0|Sangomon~1~1|Gizamon~1~2|Armadimon~1~3|Tobiumon~2~0|Yukidarumon~2~0|Shellmon~2~1|Submarimon~2~3|Hookmon~2~2|Karatuki Numemon~2~2|Ankylomon~2~3|Marin Chimairamon~3~0|Gusokumon~3~0|Sirenmon~3~1|Mermaimon~3~1|Waru Seadramon~3~2|Piranimon~3~2|Aegisdramon~4~0|Marin Angemon~4~0|Ancient Mermaimon~4~1|Ancient Megatheriumon~4~1|Regalecusmon~4~2|Leviamon~4~2||Algomon (Baby II)~0~3|Ghostmon~1~1|Labramon~1~0|Algomon (Child)~1~2|Wormmon~1~3|Witchmon~2~1|Dobermon~2~0|Algomon (Adult)~2~2|Devimon~2~2|Flare Lizamon~2~1|Mojyamon~2~0|Stingmon~2~3|Chimairamon~3~1|Cerberumon~3~0|Algomon (Perfect)~3~2|Neo Devimon~3~2|Ex-Tyranomon~3~0|Mummymon~3~2|Deathmon~4~1|Anubimon~4~0|Algomon (Ultimate)~4~2|Done Devimon~4~2|Ancient Sphinxmon~4~2|Megidramon~4~2|Apocalymon~5~3|Armagemon~5~3|Huanglongmon~5~3|Susanoomon~5~3|Omegamon~5~0|Imperialdramon: Paladin Mode~5~3|Shakkoumon~3~3|Slash Angemon~4~0|Silphymon~3~3|Valkyrimon~4~3|Paildramon~3~3|Imperialdramon: Fighter Mode~4~3|Dinobeemon~3~3|Gran Kuwagamon~4~3|Agumon~1~0|Agnimon~1~3|Greymon~2~0|Vritramon~2~3|Metal Greymon (Vaccine)~3~0|Aldamon~3~3|War Greymon~4~0|Kaiser Greymon~4~3|Agumon -Yuki no Kizuna-~5~3","z2":"|Poromon~0~3|Falcomon~1~0|Pomumon~1~1|Tinkermon~1~2|Hawkmon~1~3|Peckmon~2~0|Diatrymon~2~0|Petermon~2~1|Cockatrimon~2~1|Parasaurmon~2~2|Vegimon~2~2|Aquilamon~2~3|Yatagaramon (2006)~3~0|Parrotmon~3~0|Hippogriffomon~3~1|Lilamon~3~1|Toropiamon~3~2|Entmon~3~2|Ravmon~4~0|Ancient Irismon~4~0|Xuanwumon~4~0|Ancient Troiamon~4~1|Ornismon~4~2|Zhuqiaomon~4~2||Hopmon~0~3|Monodramon~1~0|Jazamon~1~1|Junkmon~1~2|V-mon~1~3|Strikedramon~2~0|Machmon~2~0|Eosmon (Adult)~2~3|Komondomon~2~1|Minotaurmon~2~2|Raremon~2~2|XV-mon~2~3|Cyberdramon~3~0|Loader Liomon~3~0|Eosmon (Perfect)~3~3|Locomon~3~1|Metal Tyranomon~3~2|Rebellimon~3~2|Justimon: Blitz Arm~4~0|Heavy Leomon~4~0|Eosmon (Ultimate)~4~3|Grand Locomon~4~1|Ancient Wisemon~4~2|Gundramon~4~2||Tokomon~0~3|Agumon (2006)~1~0|Shoutmon~1~1|Guilmon~1~2|Patamon~1~1|Geo Greymon~2~0|Darcmon~2~0|Shoutmon X3~2~1|Baluchimon~2~1|Mimicmon~2~2|Growmon~2~2|Angemon~2~0|Rize Greymon~3~0|Duramon~3~0|Raiji Ludomon~3~1|Shoutmon X5~3~1|Manticoremon~3~2|Megalo Growmon~3~2|Holy Angemon~3~0|Shine Greymon~4~0|Ancient Greymon~4~0|Goddramon~4~0|Ancient Garurumon~4~1|Qinglongmon~4~1|Dukemon~4~2|Seraphimon~4~0|Lucemon: Satan Mode~5~2||||||||||||||Gabumon~1~1|Wolfmon~1~3|Garurumon~2~0|Garummon~2~3|Were Garurumon~3~0|Beowolfmon~3~3|Metal Garurumon~4~1|Magna Garurumon~4~3|Gabumon -Yujo no Kizuna-~5~3","x":["|Tokomon X~0~3|Dracomon X~1~1|Gabumon X~1~1|Sistermon Blanc~1~0|Renamon X~1~1|Agumon X~1~0|Herissmon~1~1|Growmon X~2~2|Rhinomon X~2~0|Sistermon Ciel~2~1|Leomon X~2~0|Tyranomon X~2~1|Sangloupmon~2~2|Damemon~2~3|Megalo Growmon X~3~2|Were Garurumon X~3~1|Cho-Hakkaimon~3~1|Lilimon X~3~1|Cerberumon X~3~0|Metal Greymon X~3~0|Lucemon: Falldown Mode~3~2|Skull Baluchimon~3~1|Metal Tyranomon X~3~2|Yatagaramon~3~0|Craniummon X~4~0|Metal Garurumon X~4~1|Beel Starmon X~4~2|Megidramon X~4~2|Sleipmon X~4~0|War Greymon X~4~0|Sakuyamon X~4~1|Beelzebumon X~4~2|Jesmon X~4~0|Valdurmon~4~0|Belial Vamdemon~4~2|Rafflesimon~4~1|Platinum Numemon~4~2|Omegamon X~5~0|Diablomon X~5~3|Dukemon X~5~2|Alphamon~5~0|Minervamon X~5~2|Lord Knightmon X~5~2|Tobucatmon~2~1","|Yaamon~0~3|Agumon (Black) X~1~2|Gomamon X~1~0|Kokuwamon X~1~1|Impmon X~1~2|Plotmon X~1~0|Otamamon X~1~2|Herissmon~1~1|Duskmon~1~3|Dark Tyranomon X~2~2|Mantaraymon X~2~0|Kuwagamon X~2~2|Ogremon X~2~2|Nefertimon X~2~0|Seadramon X~2~1|Allomon X~2~1|Numemon X~2~2|Filmon~2~1|Velgrmon~2~3|Metal Greymon (Virus) X~3~2|Lady Devimon X~3~2|Okuwamon X~3~2|Triceramon X~3~1|Vamdemon X~3~2|Mephismon X~3~2|Mega Seadramon X~3~1|Metal Fantomon~3~1|Mammon X~3~0|Mametyramon~3~1|Stiffilmon~3~1|Kaiser Leomon~3~3|Black War Greymon X~4~2|Ofanimon: Falldown Mode X~4~0|Grandis Kuwagamon~4~2|Ultimate Brachiomon~4~1|Dark Knightmon X~4~2|Cherubimon (Vice) X~4~0|Giga Seadramon~4~1|Dinorexmon~4~1|Chaosdramon X~4~2|Rosemon X~4~1|Beel Starmon X~4~2|Prince Mamemon X~4~1|Skull Mammon X~4~0|Metal Piranimon~4~2|Rasenmon: Fury Mode~4~1|Raihimon~4~3|Bagramon~4~3|Demon X~5~2|Lilithmon X~5~2|Lucemon X~5~2|Belphemon X~5~2|Leviamon X~5~2|Barbamon X~5~2|Beelzebumon X~5~2|Diablomon X~5~3|Rasenmon~5~1|Ancient Sphinxmon~5~2|Darkness Bagramon~5~3","|||Gummymon~0~3|Chocomon~0~3|Tsumemon~0~3|Terriermon X~1~0|Palmon X~1~1|DORUmon~1~1|Lopmon X~1~1|Shakomon X~1~2|Ryudamon~1~0|Blucomon~1~1|Jazamon~1~1|Keramon X~1~3|Siesamon X~2~0|Meramon X~2~1|DORUgamon~2~1|Pteranomon X~2~1|Togemon X~2~1|Pegasmon X~2~0|Wizarmon X~2~1|Ginryumon~2~0|Tailmon X~2~0|Tylomon X~2~0|Omekamon~2~1|Paledramon~2~1|Jazardmon~2~1|Omega Shoutmon X~3~0|Cyberdramon X~3~0|DORUguremon~3~1|Garudamon X~3~0|Cannonbeemon~3~2|Monzaemon X~3~0|Rize Greymon X~3~0|Hisyaryumon~3~0|Angewomon X~3~0|Anomalocarimon X~3~1|Grademon~3~0|Mamemon X~3~1|Crys Paledramon~3~1|Jazarichmon~3~1|Pumpmon~3~1|Rapidmon X~4~0|Justimon X~4~0|Alphamon~4~0|Hououmon X~4~0|Tiger Vespamon~4~2|Cherubimon (Virtue) X~4~0|Goddramon X~4~0|Ouryumon~4~0|Ofanimon X~4~0|Plesiomon X~4~1|Gaioumon~4~2|Dinotigermon~4~1|Holydramon X~4~0|Ebemon X~4~2|Hexeblaumon~4~1|Metallicdramon~4~1|Noble Pumpmon~4~1|Magnamon X~5~0|Gankoomon X~5~1|Dynasmon X~5~1|Craniummon X~5~0|Lord Knightmon X~5~2|Dukemon X~5~2|Ulforce V-dramon X~5~0|Examon X~5~1|Duftmon X~5~1|Sleipmon X~5~0|Jesmon X~5~1|Omegamon X~5~0|Alphamon: Ouryuken~5~0|Diablomon X~5~3|Jesmon GX~5~1|Ogudomon X~5~2"],"dmc":"01-01=Koromon~0~3|01-02=Agumon~1~0|01-03=Betamon~1~2|01-04=Greymon~2~0|01-05=Tyranomon~2~1|01-06=Devimon~2~2|01-07=Meramon~2~1|01-08=Airdramon~2~0|01-09=Seadramon~2~1|01-10=Numemon~2~2|01-11=Metal Greymon (Virus)~3~2|01-12=Mamemon~3~1|01-13=Monzaemon~3~0|01-14=Blitz Greymon~4~2|01-15=Bancho Mamemon~4~1|01-16=Shin Monzaemon~4~0|02-01=Tsunomon~0~3|02-02=Gabumon~1~1|02-03=Elecmon~1~1|02-04=Kabuterimon~2~0|02-05=Garurumon~2~0|02-06=Angemon~2~0|02-07=Yukidarumon~2~0|02-08=Birdramon~2~0|02-09=Whamon~2~0|02-10=Vegimon~2~2|02-11=Skull Greymon~3~2|02-12=Metal Mamemon~3~1|02-13=Vademon~3~2|02-14=Skull Mammon~4~0|02-15=Cres Garurumon~4~1|02-16=Ebemon~4~2|03-01=Tokomon~0~3|03-02=Patamon~1~1|03-03=Kunemon~1~2|03-04=Unimon~2~0|03-05=Centaurmon~2~1|03-06=Ogremon~2~2|03-07=Bakemon~2~2|03-08=Shellmon~2~1|03-09=Drimogemon~2~1|03-10=Scumon~2~2|03-11=Andromon~3~0|03-12=Giromon~3~0|03-13=Etemon~3~2|03-14=Chimairamon~3~1|03-15=Hi Andromon~4~0|03-16=Gokumon~4~2|03-17=Bancho Leomon~4~0|04-01=Tanemon~0~3|04-02=Piyomon~1~0|04-03=Palmon~1~1|04-04=Monochromon~2~1|04-05=Cockatrimon~2~1|04-06=Leomon~2~0|04-07=Kuwagamon~2~2|04-08=Coelamon~2~1|04-09=Mojyamon~2~0|04-10=Nanimon~2~2|04-11=Megadramon~3~2|04-12=Piccolomon~3~1|04-13=Digitamamon~3~1|04-14=Darkdramon~4~2|04-15=Bloom Lordmon~4~0|04-16=Gankoomon~4~1|05-01=Pagumon~0~3|05-02=Gazimon~1~2|05-03=Gizamon~1~2|05-04=Dark Tyranomon~2~2|05-05=Cyclomon~2~2|05-06=Devidramon~2~2|05-07=Tuskmon~2~2|05-08=Flymon~2~2|05-09=Deltamon~2~2|05-10=Raremon~2~2|05-11=Metal Tyranomon~3~2|05-12=Nanomon~3~2|05-13=Ex-Tyranomon~3~0|05-14=Mugendramon~4~2|05-15=Raidenmon~4~2|05-16=Gaioumon~4~2|17-17=Omegamon Alter-S~5~2|17-18=Chaosdramon~5~2|18-18=Chaosmon~5~0|19-17=Millenniumon~5~2","penc":"1-01=Mochimon~0~3|1-02=Tentomon~1~0|1-03=Gottsumon~1~1|1-04=Otamamon~1~2|1-05=Kabuterimon~2~0|1-06=Tortamon~2~0|1-07=Monochromon~2~1|1-08=Starmon~2~1|1-09=Gekomon~2~2|1-10=Kuwagamon~2~2|1-11=Tailmon~2~0|1-12=Atlur Kabuterimon~3~0|1-13=Jyagamon~3~0|1-14=Triceramon~3~1|1-15=Piccolomon~3~1|1-16=Tonosama Gekomon~3~2|1-17=Okuwamon~3~2|1-18=Angewomon~3~0|1-19=Herakle Kabuterimon~4~0|1-20=Saber Leomon~4~1|1-21=Metal Etemon~4~2|1-22=Holydramon~4~0|1-23=Blastmon~4~0|1-24=El Doradimon~4~1|1-25=Gran Kuwagamon~4~3|1-26=Tlalocmon~5~1|1-27=Mastemon~5~0|1-28=Angoramon~1~0|1-29=Symbare Angoramon~2~0|1-30=Lamortmon~3~0|1-31=Diarbbitmon~4~0|2-01=Pukamon~0~3|2-02=Gomamon~1~0|2-03=Ganimon~1~1|2-04=Shakomon~1~2|2-05=Rukamon~2~0|2-06=Ikkakumon~2~0|2-07=Seadramon~2~1|2-08=Coelamon~2~1|2-09=Octmon~2~2|2-10=Gesomon~2~2|2-11=Ebidramon~2~1|2-12=Whamon~3~0|2-13=Zudomon~3~0|2-14=Mega Seadramon~3~1|2-15=Anomalocarimon~3~1|2-16=Dagomon~3~2|2-17=Marin Devimon~3~2|2-18=Hangyomon~3~1|2-19=Marin Angemon~4~0|2-20=Metal Seadramon~4~1|2-21=Pukumon~4~2|2-22=Plesiomon~4~1|2-23=Vikemon~4~3|2-24=Jumbo Gamemon~4~1|2-25=Cthyllamon~4~2|2-26=Mitamamon~5~0|2-27=Aegisdramon~5~0|2-28=Jellymon~1~1|2-29=Tesla Jellymon~2~1|2-30=Thetismon~3~1|2-31=Amphimon~4~1|3-01=Peti Meramon~0~3|3-02=Bakumon~1~0|3-03=Candmon~1~1|3-04=Pico Devimon~1~2|3-05=Hanumon~2~0|3-06=Garurumon~2~0|3-07=Meramon~2~1|3-08=Wizarmon~2~1|3-09=Devimon~2~2|3-10=Bakemon~2~2|3-11=Dokugumon~2~2|3-12=Mammon~3~0|3-13=Were Garurumon~3~0|3-14=Death Meramon~3~1|3-15=Pumpmon~3~1|3-16=Vamdemon~3~2|3-17=Fantomon~3~2|3-18=Lady Devimon~3~2|3-19=Skull Mammon~4~0|3-20=Boltmon~4~1|3-21=Demon~4~2|3-22=Piemon~4~2|3-23=Anubimon~4~0|3-24=Noble Pumpmon~4~1|3-25=Callismon~4~2|3-26=Voltobautamon~5~2|3-27=Mastemon~5~0|3-28=Loogamon~1~2|3-29=Loogarmon~2~2|3-30=Soloogarmon~3~2|3-31=Fenriloogamon~4~2|4-01=Pyocomon~0~3|4-02=Piyomon~1~0|4-03=Floramon~1~1|4-04=Mushmon~1~2|4-05=Palmon~1~1|4-06=V-dramon~2~0|4-07=Birdramon~2~0|4-08=Togemon~2~1|4-09=Kiwimon~2~1|4-10=Woodmon~2~2|4-11=Red Vegimon~2~2|4-12=Aero V-dramon~3~0|4-13=Garudamon~3~0|4-14=Blossomon~3~1|4-15=Delumon~3~1|4-16=Jyureimon~3~2|4-17=Gerbemon~3~2|4-18=Lilimon~3~1|4-19=Hououmon~4~0|4-20=Griffomon~4~1|4-21=Pinochimon~4~2|4-22=Rosemon~4~1|4-23=Ulforce V-dramon~4~0|4-24=Rafflesimon~4~1|4-25=Hydramon~4~2|4-26=Mitamamon~5~0|4-27=Cernumon~5~1|4-28=Pteromon~1~1|4-29=Galemon~2~1|4-30=Grand Galemon~3~1|4-31=Zephagamon~4~1|5-01=Caprimon~0~3|5-02=Toy Agumon~1~0|5-03=Kokuwamon~1~1|5-04=Hagurumon~1~2|5-05=Greymon~2~0|5-06=Revolmon~2~0|5-07=Tankmon~2~1|5-08=Clockmon~2~1|5-09=Guardromon~2~2|5-10=Mechanorimon~2~2|5-11=Thunderballmon~2~1|5-12=Metal Greymon (Vaccine)~3~0|5-13=Andromon~3~0|5-14=Knightmon~3~1|5-15=Big Mamemon~3~1|5-16=Megadramon~3~2|5-17=Waru Monzaemon~3~2|5-18=Cyberdramon~3~0|5-19=War Greymon~4~0|5-20=Metal Garurumon~4~1|5-21=Mugendramon~4~2|5-22=Venom Vamdemon~4~2|5-23=Hi Andromon~4~0|5-24=Ragnamon~4~3|5-25=Zeke Greymon~4~2|5-26=Omegamon~5~0|5-27=Chaosdramon~5~2|5-28=Commandramon~1~2|5-29=Hi-Commandramon~2~2|5-30=Cargodramon~3~2|5-31=Brigadramon~4~2|0-01=Nyaromon~0~3|0-02=Agumon~1~0|0-03=Gabumon~1~1|0-04=Plotmon~1~0|0-05=Greymon~2~0|0-06=Leomon~2~0|0-07=Garurumon~2~0|0-08=Igamon~2~1|0-09=Angemon~2~0|0-10=Tailmon~2~0|0-11=Metal Greymon (Vaccine)~3~0|0-12=Asuramon~3~0|0-13=Were Garurumon~3~0|0-14=Metal Mamemon~3~1|0-15=Holy Angemon~3~0|0-16=Angewomon~3~0|0-17=War Greymon~4~0|0-18=Metal Garurumon~4~1|0-19=Dominimon~4~0|0-20=Quantumon~4~1|0-21=Omegamon~5~0|0-22=Mastemon~5~0|0-23=Gammamon~1~2|0-24=Betel Gammamon~2~0|0-25=Kaus Gammamon~2~1|0-26=Wezen Gammamon~2~1|0-27=Gulus Gammamon~2~2|0-28=Canoweissmon~3~0|0-29=Regulusmon~3~2|0-30=Siriusmon~4~0|0-31=Arcturusmon~4~2|0-32=Proximamon~5~2|6-01=Mococomon~0~3|6-02=Kakamon~1~0|6-03=Kamemon~1~1|6-04=Fujamon~1~2|6-05=Takinmon~1~0|6-06=Hanumon~2~0|6-07=Xiquemon~2~0|6-08=Gawappamon~2~1|6-09=Lianpumon~2~1|6-10=Ginkakumon~2~2|6-11=Kinkakumon~2~2|6-12=Hakubamon~2~0|6-13=Turuiemon~2~1|6-14=Sanzomon~3~0|6-15=Huankunmon~3~0|6-16=Cho-Hakkaimon~3~1|6-17=Pandamon~3~1|6-18=Gokuwmon~3~2|6-19=Xingtianmon~3~2|6-20=Sagomon~3~2|6-21=Andiramon (Deva)~3~1|6-22=Erlangmon~4~0|6-23=Xiangpengmon~4~0|6-24=Shagaramon~4~1|6-25=Nezhamon~4~1|6-26=Seiten Gokuwmon~4~2|6-27=Dijiangmon~4~2|6-28=Takutoumon~4~0|6-29=Jougamon~4~0|6-30=Shakamon~5~0|7-01=Onibimon~0~3|7-02=Wankomon~1~0|7-03=Hanimon~1~1|7-04=Gasamon~1~2|7-05=Renamon~1~1|7-06=Siesamon~2~0|7-07=Peckmon~2~0|7-08=Kokeshimon~2~1|7-09=Shellmon~2~1|7-10=Musyamon~2~2|7-11=Fugamon~2~2|7-12=Manekimon~2~0|7-13=Tenkomon~2~2|7-14=Shishimamon~3~0|7-15=Yatagaramon (2006)~3~0|7-16=Karakurumon~3~1|7-17=Marin Bullmon~3~1|7-18=Oboromon~3~2|7-19=Karatenmon~3~2|7-20=Darumamon~3~0|7-21=Sekkamon~3~2|7-22=Amaterasumon~4~0|7-23=Ravmon~4~0|7-24=Kaguyamon~4~1|7-25=Ryugumon~4~1|7-26=Zanbamon~4~2|7-27=Tengumon~4~2|7-28=Ariemon~4~1|7-29=Yukinamon~4~2|7-30=Enmamon~5~2"};
  /*LISTAS-FIN*/
  // ---- listas: "nombre~etapa~atributo" separadas por «|» (la posición es el número que manda el vpet); dmc/penc: "clave=nombre~etapa~atributo" ----
  const CACHE = {};
  const fila = e => { if (!e) return null; const [n, et, at] = e.split("~"); return { nombre: n, etapa: et === "" ? null : +et, attr: at === "" ? null : +at } };
  function lista(k, i) { // k: dm20, pen20, z1, z2, x0, x1, x2
    if (!CACHE[k]) CACHE[k] = (k[0] === "x" ? (LISTAS.x || [])[+k.slice(1)] || "" : LISTAS[k] || "").split("|");
    return i > 0 ? fila(CACHE[k][i]) : null;
  }
  function porClave(k, c) { // k: dmc, penc
    if (!CACHE[k]) { CACHE[k] = {}; (LISTAS[k] || "").split("|").forEach(s => { const j = s.indexOf("="); if (j > 0) CACHE[k][s.slice(0, j)] = s.slice(j + 1) }) }
    return fila(CACHE[k][c]);
  }
  const PEN20_VER = { 6: "Silver Black", 7: "Silver Blue", 8: "Dukemon Color", 9: "Beelzebumon Color" };
  const PENZ_VER = { 6: "Nature Spirits Z", 7: "Deep Savers Z", 8: "Nightmare Soldiers Z", 9: "Wind Guardians Z", 10: "Metal Empire Z", 11: "Virus Busters Z" };
  // DM20: el A-Com se presenta como un Agumon de nombre «ACOM» (igual que el juego)
  function romDM20(ver) {
    const p = [0x0301, 0x0D0F, ((ver & 15) << 4) | 0xE, (2 << 6) | 0xE, (1 << 10) | (26 << 4) | 0xE, (18 << 4) | 0xE, 0xE, 0xE, 0xE, 0xE];
    p[9] |= (((16 - nib(p) % 16) % 16 + 1) & 15) << 12; return "V2-" + p.map(hex4).join("-");
  }
  // DMX: el de Dukemon X del juego, con la versión del vpet en el primer paquete
  const romDMX = ver => "V2-23" + (ver & 15).toString(16).toUpperCase() + "E-AA2E-560E-182E-0CAE-103E";
  // Pen20 (juego: 03-005 pen20Rom): formato propio de combate, el A-Com contesta como un Agumon (n.º 118); verificación mal a propósito (no da 12 mód 16)
  function romPen20(ver) {
    const p = [((ver & 15) << 4) | 0xE, (118 << 6) | (0 << 4) | 0xE, 0xE, 0xE, (16 << 4) | 0xE, 0xE, 0xE, 0xE, 0xE, 0xE];
    p[9] |= (((12 - nib(p) % 16 + 16) % 16 + 1) & 15) << 12; return "V2-" + p.map(hex4).join("-");
  }
  // PenZ (juego: 03-006 penzRom): contesta como un Sunarizamon; verificación del paquete 6 mal a propósito (no da 8 mód 16)
  function romPenZ(ver) {
    const p = [0x030E | ((ver & 15) << 4), 0x212E, 0x288E, 0x182E, 0x018E, 0x000E];
    p[5] |= (((16 - nib(p) % 16) % 16 + 1) & 15) << 12; return "V2-" + p.map(hex4).join("-");
  }
  // Color (juego: 03-007 DMC_RIVAL / PENC_RIVAL + 03-008 dmcDañar modo «roto2» = cierre dañado: lee todo y luego corta)
  const DMC_RIVAL = "C1-47444C4300000000000B007300019406-47444C4300020002000000000000938B";
  const PENC_RIVAL = "C1-4744447000000000000B007300018C33-47444470000200020000000000008BB8";
  const roto2 = c => { const m = /^C1-([0-9A-F]{32})-([0-9A-F]{32})$/i.exec(c); return "C1-" + m[1] + "-" + m[2].slice(0, 28) + (parseInt(m[2].slice(28), 16) ^ 0xFFFF).toString(16).toUpperCase().padStart(4, "0") };
  const romDMC = () => roto2(DMC_RIVAL);                         // C1: el A-Com lo reenvía cada 2,5 s y tras cada «t» (como el juego, velocidad «normal»)
  const romPenC = () => roto2(PENC_RIVAL).replace(/^C1-/, "C2-"); // C2 (orden «el vpet envía primero», el recomendado): se envía UNA vez, el A-Com lo repite solo
  const paquetes = (ls, soloE) => { const a = []; ls.forEach(l => { const re = /(?:^|\s)r:\s*([0-9A-Fa-f]{4})(?![0-9A-Fa-f])/g; let m; while ((m = re.exec(l))) { const v = parseInt(m[1], 16); if (!soloE || (v & 15) === 14) a.push(v) } }); return a };
  // DM20 (juego: 03-005 dm20Decodificar, formato DM20). Si la versión es 6-9 el juego busca en la lista del Pen20.
  function decDM20(ls) {
    const q = paquetes(ls, false).slice(0, 10); if (q.length < 4 || (q[2] & 15) !== 14 || (q[3] & 15) !== 14) return null;
    const d = { tipo: "dm20", version: (q[2] >> 4) & 15, indice: q[3] >> 6, attr: (q[3] >> 4) & 3, poder: q.length > 5 ? (q[5] >> 4) & 255 : null, n: q.length, hex: q.map(hex4).join(" ") };
    d.pen = !!PEN20_VER[d.version]; const f = lista(d.pen ? "pen20" : "dm20", d.indice);
    d.nombre = f ? f.nombre : null; d.etapa = f ? f.etapa : null; d.valido = q.length === 10 && nib(q) % 16 === 0; return d;
  }
  // Pen20 (juego: 03-005 dm20Decodificar con vp = pen20 → pen20Decodificar). Si no llega en formato Pen20, el juego cae al formato DM20.
  function decPen20(ls) {
    const q = paquetes(ls, false).slice(0, 10);
    if (q.length >= 2 && (q[0] & 15) === 14 && (q[1] & 15) === 14 && PEN20_VER[(q[0] >> 4) & 15]) {
      const d = { tipo: "pen20", version: (q[0] >> 4) & 15, indice: (q[1] >> 6) & 255, attr: (q[1] >> 4) & 3, poder: q.length > 4 ? (q[4] >> 4) & 255 : null,
        copia: q.length > 5 ? (q[5] >> 14) & 3 : 0, n: q.length, hex: q.map(hex4).join(" "), valido: q.length === 10 && nib(q) % 16 === 12 };
      // copia 2 = Digimon copiado del DM20 o con contraseña: no es del roster del Pen20 (el juego no lo reconoce)
      const f = d.copia === 2 ? null : lista("pen20", d.indice); d.pen = true;
      d.nombre = f ? f.nombre : null; d.etapa = f ? f.etapa : null; return d;
    }
    const d = decDM20(ls); if (d) d.tipo = "pen20"; return d;
  }
  function decDMX(ls) {
    const q = paquetes(ls, true).slice(0, 6); if (q.length < 2) return null;
    const v = bits(q[0], 8, 4);
    const d = { tipo: "dmx", version: v, set: Math.floor(v / 2), etapa: bits(q[1], 0, 3), indice: bits(q[1], 3, 7), attr: bits(q[1], 10, 2), n: q.length, hex: q.map(hex4).join(" ") };
    // juego: 03-002 decodificar (solo con número > 0; la etapa que manda el vpet se queda, el +VI+ llega como 5)
    const f = d.indice > 0 ? lista("x" + d.set, d.indice) : null; d.nombre = f ? f.nombre : null; return d;
  }
  // PenZ (juego: 03-006 penzDecodificar): versión 6-8 → lista Z1, 9-B → Z2; en Z2 los n.º 78-90 son Jogress compartidos con Z1
  function decPenZ(ls) {
    const q = paquetes(ls, false).slice(0, 6); if (q.length < 2 || (q[0] & 15) !== 14 || (q[1] & 15) !== 14) return null;
    const version = (q[0] >> 4) & 15; if (!PENZ_VER[version]) return null;
    const d = { tipo: "penz", version, lista: version <= 8 ? "Z1" : "Z2", indice: (q[1] >> 6) & 127, attr: (q[1] >> 4) & 3, poder: q.length > 4 ? (q[4] >> 4) & 255 : null,
      etapaVpet: (q[1] >> 13) & 7, n: q.length, hex: q.map(hex4).join(" "), valido: q.length === 6 && nib(q) % 16 === 8 };
    const f = lista(d.lista.toLowerCase(), d.indice) || (d.lista === "Z2" && d.indice >= 78 && d.indice <= 90 ? lista("z1", d.indice) : null);
    d.nombre = f ? f.nombre : null; d.etapa = f ? f.etapa : null; return d;
  }
  // ---- bloques Color: 16 bytes = 8 palabras de 16 bits ----
  const palabras = h => { const w = []; for (let i = 0; i < 32; i += 4) w.push(parseInt(h.substr(i, 4), 16)); return w };
  // DMC (juego: 03-008 dmcBloqueR con versión «Automática» + dmcMostrar). version = Ver. del DMC (1-5, 17-19), como en las claves «01-11».
  function decDMC(h) {
    const w = palabras(h), ver0 = w[3] <= 4 ? w[3] : Math.max(0, w[2] - 1), num = w[4];
    let clave = String(ver0 + 1).padStart(2, "0") + "-" + String(num).padStart(2, "0"), aviso = null;
    // números que existen en otra versión «VIP» (17-19): el juego elige la que encaja
    const VIP = { "17-17": [1, 2], "17-18": [4, 5], "18-18": [3, 4], "19-17": [3, 5] };
    if (!porClave("dmc", clave)) { const vn = ver0 + 1, cands = Object.keys(VIP).filter(k => +k.split("-")[0] === num), cc = cands.filter(k => VIP[k].indexOf(vn) >= 0), pick = (cc.length ? cc : cands)[0]; if (pick) { if (!cc.length) aviso = "n.º " + num + " no encaja con la versión " + vn; clave = pick } }
    const f = porClave("dmc", clave);
    return { tipo: "dmc", version: ver0 + 1, indice: num, clave, attr: f ? f.attr : null, nombre: f ? f.nombre : null, etapa: f ? f.etapa : null, poder: w[5], aviso, n: 1, hex: w.map(hex4).join(" ") };
  }
  // PenC (juego: 03-007 pencBloqueR + pencProcesar + pencBuscar, Pendulum Color normal, no Godzilla). Suma de control: palabras 0-6 = palabra 7.
  function decPenC(h) {
    const w = palabras(h); if (w[2] === 18) return { ignorar: true }; // bloque de resultado de combate: no trae un Digimon
    const sm = (w[0] + w[1] + w[2] + w[3] + w[4] + w[5] + w[6]) & 65535, vw = w[3], num = w[4];
    const clave = (vw <= 5 ? (vw + 1) % 6 : vw) + "-" + String(num).padStart(2, "0"), f = porClave("penc", clave);
    return { tipo: "penc", version: vw, indice: num, clave, attr: f ? f.attr : null, nombre: f ? f.nombre : null, etapa: f ? f.etapa : null, poder: w[5], valido: sm === w[7], n: 1, hex: w.map(hex4).join(" ") };
  }
  // C1-<bloque>-<bloque> completo que imprime el A-Com (juego: 03-008 dmcDecodificar, solo cabecera 47444C43)
  function decDMCcodigo(c) {
    const m = /C1-([0-9A-Fa-f]{32})-([0-9A-Fa-f]{32})/.exec(c); if (!m || m[1].slice(0, 8).toUpperCase() !== "47444C43") return null;
    const ver = parseInt(m[1].slice(8, 16), 16), num = parseInt(m[1].slice(16, 20), 16), clave = String(ver + 1).padStart(2, "0") + "-" + String(num).padStart(2, "0"), f = porClave("dmc", clave);
    return { tipo: "dmc", version: ver + 1, indice: num, clave, attr: f ? f.attr : null, nombre: f ? f.nombre : null, etapa: f ? f.etapa : null, n: 2, hex: m[1] + " " + m[2] };
  }
  // ---- la lectura: manda la DigiROM, junta paquetes y decodifica; si la versión no era la del vpet, la ajusta y vuelve a pedir ----
  const TIPOS = {
    dm20: { rom: romDM20, dec: decDM20, need: 10, soloE: false },
    dmx: { rom: romDMX, dec: decDMX, need: 6, soloE: true },
    pen20: { rom: romPen20, dec: decPen20, need: 10, soloE: false, verDef: 6, verOk: v => !!PEN20_VER[v] },
    penz: { rom: romPenZ, dec: decPenZ, need: 6, soloE: true, verDef: 6, verOk: v => !!PENZ_VER[v] },
    dmc: { color: true, rom: romDMC, cab: "47444C43", reenviar: true },
    penc: { color: true, rom: romPenC, cab: "47444470", reenviar: false }
  };
  let L = null; // lectura en curso
  const guardarVer = (t, v) => { try { localStorage.setItem("vpet-ver-" + t, v) } catch (e) { } };
  const verGuardada = t => { let v = 0; try { v = +localStorage.getItem("vpet-ver-" + t) || 0 } catch (e) { } return v || (TIPOS[t] && TIPOS[t].verDef) || 0 };
  function enviar(t) { if (A) A.enviar(t); if (L && L.monitor) L.monitor("> " + t) }
  function empezar(tipo, cb) { // cb: {monitor(txt), progreso(p), listo(d), falla(msg)}
    parar(); if (!TIPOS[tipo]) { cb && cb.falla && cb.falla("Tipo de vpet desconocido: " + tipo); return }
    L = Object.assign({ tipo, T: TIPOS[tipo], ver: verGuardada(tipo), lineas: [], t: null, intentos: 0, buf: "", cbuf: "", rvisto: false, ue: 0, iv: null, tt: null }, cb);
    if (L.T.color) { L.iv = setInterval(() => { if (L && L.T.reenviar && Date.now() - L.ue > 2500) mandarColor() }, 500) }
    pedir();
  }
  function pedir() { if (!L) return; L.lineas = []; L.intentos++; if (L.T.color) { mandarColor(); L.progreso && L.progreso(10) } else enviar(L.T.rom(L.ver)); armar(30000) }
  function mandarColor() { if (!L || Date.now() - L.ue < 400) return; L.ue = Date.now(); enviar(L.T.rom()) }
  function armar(ms) { clearTimeout(L.t); L.t = setTimeout(cerrar, ms) }
  function parar() { if (L) { clearTimeout(L.t); clearTimeout(L.tt); clearInterval(L.iv); L = null; enviar("0") } }
  function linea(l) {
    if (L && L.monitor) L.monitor(l); if (!L) return; L.lineas.push(l);
    if (L.T.color) return lineaColor(l);
    const n = paquetes(L.lineas, L.T.soloE).length, need = L.T.need;
    if (n > 0) { L.progreso && L.progreso(Math.min(95, Math.max(10, n / need * 100))); armar(1500) }
    if (n >= need) { clearTimeout(L.t); cerrar() }
  }
  // juego: 03-008 dmcEscaneo (se acumula el texto: un bloque puede llegar partido; solo cuenta la primera «r:» después de cada «s:»)
  function lineaColor(l) {
    const tx = l + "\n";
    if (/active too short|too short/i.test(tx)) L.monitor && L.monitor("[la señal del vpet llegó con error: mantén el vpet quieto y vuelve a pulsar B]");
    L.buf = (L.buf + tx).slice(-1500);
    const re = new RegExp("([sr]):\\s*(" + L.T.cab + "[0-9A-Fa-f]{24})", "gi"); let m, fin = 0, hubo = false;
    while ((m = re.exec(L.buf))) {
      fin = re.lastIndex;
      if (m[1].toLowerCase() === "s") L.rvisto = false;
      else { hubo = true; if (!L.rvisto) { L.rvisto = true; if (bloque(m[2])) return } }
    }
    const mt = /(?:^|\s)t(?=\s|$)/.exec(L.buf.slice(fin));
    if (mt) { fin += mt.index + mt[0].length; if (L.T.reenviar) { clearTimeout(L.tt); L.tt = setTimeout(() => { if (L && Date.now() - L.ue > 120) mandarColor() }, 300) } }
    if (fin) L.buf = L.buf.slice(fin);
    if (hubo) L.progreso && L.progreso(60);
    if (L.tipo === "dmc") { // código C1 completo que no sea el nuestro
      L.cbuf = (L.cbuf + tx).slice(-300); const rc = /C1-[0-9A-Fa-f]{32}-[0-9A-Fa-f]{32}/g; let c, u = null; while ((c = rc.exec(L.cbuf))) u = c[0];
      if (u) { L.cbuf = L.cbuf.slice(L.cbuf.lastIndexOf(u) + u.length); const U = u.toUpperCase(); if (U !== romDMC() && U !== DMC_RIVAL) { const d = decDMCcodigo(u); if (d) return terminar(d) } }
    }
  }
  function bloque(h) { // true si terminó la lectura
    const d = L.tipo === "penc" ? decPenC(h) : decDMC(h);
    if (d.ignorar) return false;
    if (L.tipo === "penc" && !d.valido) { L.monitor && L.monitor("[Pendulum Color: lectura descartada, la suma de control no coincide. Pulsa B otra vez]"); return false }
    return terminar(d);
  }
  function terminar(d) { const ok = L.listo; parar(); ok && ok(d); return true }
  function cerrar() {
    if (!L) return;
    if (L.T.color) { if (L.intentos >= 8) { const f = L.falla; parar(); f && f("No llegó nada del vpet. Revisa el A-Com y vuelve a intentarlo.") } else { L.monitor && L.monitor("[sin lectura todavía; vuelvo a enviar]"); L.ue = 0; pedir() } return }
    const d = L.T.dec(L.lineas);
    if (!d) { if (L.lineas.length) L.monitor && L.monitor("[no parece un " + L.tipo.toUpperCase() + "; vuelvo a escuchar]"); if (L.intentos >= 8) { const f = L.falla; parar(); f && f("No llegó nada del vpet. Revisa el A-Com y vuelve a intentarlo.") } else pedir(); return }
    // el vpet solo revela el número de su Digimon si la versión coincide: se ajusta y se vuelve a pedir
    if (d.version !== L.ver && (!L.T.verOk || L.T.verOk(d.version))) { L.ver = d.version; guardarVer(L.tipo, d.version); L.monitor && L.monitor("[versión " + d.version + ": ajusto y vuelvo a pedir]"); if (d.indice === 0) { L.progreso && L.progreso(5); pedir(); return } }
    const ok = L.listo; parar(); ok && ok(d);
  }
  window.vpetLinea = linea;
  const version = t => L && L.tipo === t ? L.ver : verGuardada(t);
  return { empezar, parar, version, tipos: Object.keys(TIPOS), romDM20, romDMX, romPen20, romPenZ, romDMC, romPenC, decDM20, decDMX, decPen20, decPenZ, decDMC, decPenC, PEN20_VER, PENZ_VER };
})();
