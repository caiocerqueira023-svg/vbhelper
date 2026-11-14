# -*- coding: utf-8 -*-
"""
Build a JSON of the Digimon-only "Quiz time!" questions from Digimon Story:
Cyber Sleuth - Complete Edition (Cyber Sleuth + Hacker's Memory).

Source: GameFAQs "DigiLine Quiz Guide (PC)" by GStelar04
  https://gamefaqs.gamespot.com/pc/268807-digimon-story-cyber-sleuth-complete-edition/faqs/78530/cyber-sleuth-quiz-answers
  https://gamefaqs.gamespot.com/pc/268807-digimon-story-cyber-sleuth-complete-edition/faqs/78530/hackers-memory-quiz-answers

Only the "Digimon Questions" sections are used (Real Life / Game Questions excluded).
The guide lists the first in-game text variation of each question.

Raw rows below are transcribed from the guide's tables:
    number | question | A/B/C answers | real answer
"""
import json
import re
from pathlib import Path

OUT = Path(__file__).resolve().parent / "quiz_time_digimon_questions.json"

RAW_CS = r"""
01|Quiz time! Omnimon \_\_\_\_ can Digivolve <br>from BlackWarGreymon. What goes in \_\_\_\_?|A: Black! <br>B: Zwart! <br>C: Sloppy Mode!|B: Zwart!
02|Quiz time! How many Digimon in the <br>Royal Knights, guardians of the Digital World?|A: 13! <br>B: Should be 14. <br>C: 15, maybe?|A: 13!
03|Quiz time! How many virus type Digimon <br>are currently in the Royal Knights?|A: Two! <br>B: Five! <br>C: All of the Royal Knights!|A: Two!
04|Quiz time! What is Gallantmon's special <br>move where he lunges with a spear?|A: Royal jelly! <br>B: Lightning Joust, maybe? <br>C: Bifrost.|B: Lightning Joust, maybe?
05|Quiz time! What is the name of <br>Craniamon's spear?|A: Muspelheimr... <br>B: Gram! <br>C: Claiomh Solais.|C: Claíomh Solais.
06|Quiz time! How many feet does <br>Kentaurosmon have?|A: Four. <br>B: Six, I think. <br>C: Eight!|B: Six, I think.
07|Quiz time! What type is the <br>Royal Knight Examon?|A: Free type <br>B: Virus type? <br>C: Data type!|C: Data type!
08|Quiz time! What generation is the <br>Royal Knight Alphamon?|A: In-training, maybe? <br>B: Mega, I think? <br>C: Armor!|B: Mega, I think?
09|Quiz time! In Gankoomon's special move <br>"Quake! Blast! Fire! \_\_\_!," what is \_\_\_?|A: Father! <br>B: Mama! <br>C: G... Grandpa!?|A: Father!
10|Quiz time! Which Digimon Digivolves to <br>Magnamon: Dorumon, Lalamon or Veemon?|A: Dorumon! <br>B: Lalamon! <br>C: Veemon!|C: Veemon!
11|Quiz time! Which Royal Knight Digimon <br>can change to Leopard Mode?|A: Leopardmon, maybe? <br>B: Magnamon, I think. <br>C: Ophanimon!|A: Leopardmon, maybe?
12|Quiz time! Which Digimon uses Dragon's <br>Roar, an energy-shooting special move?|A: Craniamon, right? <br>B: Titamon, I think. <br>C: Dynasmon, I'm sure of it.|C: Dynasmon, I'm sure of it.
13|Quiz time! The vaccine type Royal Knights <br>wearing capes are Alphamon and who?|A: Gallantmon, perhaps? <br>B: Leopardmon, I think. <br>C: Omnimon, for sure|C: Omnimon, for sure
14|Quiz time! Which Digimon uses a 4-bladed <br>special move called Spiral Masquerade?|A: Crusadermon! <br>B: UlforceVeedramon? <br>C: Kentaurosmon, yeah!|A: Crusadermon!
15|Quiz time! Which swift Royal Knight <br>Digimon dons Blue Digizoid armor?|A: Should be Examon! <br>B: UlforceVeedramon, right? <br>C: Magnamon, maybe.|B: UlforceVeedramon, right?
16|Quiz time! Which Digimon is nicknamed <br>the "Demon of the Deep Forest"?|A: Impmon? <br>B: GranKuwagamon! <br>C: Woodmon!|B: GranKuwagamon!
17|Quiz time! Which Digimon transforms its <br>hands into axes called Bunny Blades?|A: Antylamon... right? <br>B: Probably Vikemon. <br>C: It's Silphymon!|A: Antylamon... right?
18|Quiz time! Which Digimon is bipedal: <br>Wormmon, ToyAgumon or Armadillomon?|A: Wormmon! <br>B: ToyAgumon! <br>C: Armadillomon!|B: ToyAgumon!
19|Quiz time! Which Digimon usually has a <br>Holy Ring attached to their tail?|A: Togemon! <br>B: Growlmon? <br>C: Gatomon, yeah!|C: Gatomon, yeah!
20|Quiz time! Which Digimon has a flower on <br>its head: Sukamon, Palmon or Woodmon?|A: Definitely Sukamon! <br>B: Palmon, of course! <br>C: No doubt it's Woodmon.|B: Palmon, of course!
21|Quiz time! Which looks like a teddy <br>bear: Lilamon, Monzaemon or Guilmon?|A: Lilamon! <br>B: Monzaemon! <br>C: Guilmon!|B: Monzaemon!
22|Quiz time! Which has wings on its back: <br>Infermon, Garudamon or Gotsumon?|A: Infermon, right? <br>B: Garudamon! <br>C: Maybe it's Gotsumon.|B: Garudamon!
23|Quiz time! Which in-training Digimon <br>has a black, fuzzy exterior?|A: Botamon <br>B: Motimon? <br>C: Kuramon, I bet!|A: Botamon
24|Quiz time! Which Digimon has an M <br>on its forehead?|A: Machinedramon <br>B: MarineAngemon <br>C: Wanyamon|C: Wanyamon
25|Quiz time! What do the characters on <br>MetalEtemon's chest mean?|A: "Invincible" <br>B: "Strongest" <br>C: "Instant Kill"|B: "Strongest"
26|Quiz time! Which Digimon uses <br>two shotguns as weapons?|A: MirageGaogamon <br>B: GroundLocomon! <br>C: Beelzemon?|C: Beelzemon?
27|Quiz time! Which one looks like a tree: <br>Sunflowmon, Cherrymon or Kuzuhamon?|A: Sunflowmon! <br>B: Cherrymon! <br>C: Kuzuhamon!|B: Cherrymon!
28|Quiz time! Which is an armor Digimon: <br>WarGreymon, Boltmon or Flamedramon?|A: WarGreymon! <br>B: Boltmon <br>C: Flamedramon?|C: Flamedramon?
29|Quiz time! Which Digimon uses Taoist <br>arts and Amethyst Mandala special move?|A: Taomon! <br>B: Leomon! <br>C: Sakuyamon!|C: Sakuyamon!
30|Quiz time! What special move cleaves <br>in two with Knightmon's longsword?|A: Strike Sword <br>B: Frozen Yogurt Sword <br>C: Berserk Sword|C: Berserk Sword
31|Quiz time! Which of these is Crowmon's <br>special move?|A: Savage Emperor? <br>B: Average Emperor? <br>C: Beverage Emperor?|A: Savage Emperor?
32|Quiz time! Which Digimon uses <br>Unidentified Flying Kiss special move?|A: Frigimon <br>B: Vademon! <br>C: LadyDevimon?|B: Vademon!
33|Quiz time! Which part of Reppamon's body <br>takes the shape of a blade?|A: Its right leg <br>B: Its tail? <br>C: Its beard!|B: Its tail?
34|Quiz time! Which Digimon uses the <br>special move Poop Dunk?|A: Nanimon! <br>B: Digitamamon? <br>C: Kuwagamon|A: Nanimon!
35|Quiz time! Which red in-training Digimon <br>uses Bubble Blow on its enemies?|A: Pabumon! <br>B: Punimon. <br>C: Meramon?|B: Punimon.
36|Quiz time! Which Digimon mutated from a <br>computer virus created on Halloween?|A: Yokomon? <br>B: Rapidmon? <br>C: Pumpkinmon?|C: Pumpkinmon?
37|Quiz time! What baby bird Digimon <br>enjoys pecking Tanemon on the head?|A: Bukamon! <br>B: Biyomon! <br>C: AeroVeedramon!|B: Biyomon!
38|Quiz time! What Mega puppet Digimon is <br>made from the body a cursed Cherrymon?|A: Clockmon! <br>B: Puppetmon? <br>C: GrapLeomon|B: Puppetmon?
39|Quiz time! What do you call a mind- <br>controlled Digimon with dark red eyes? \*1\*|A: Devimon! <br>B: LadyDevimon! <br>C: Creepymon|A: Devimon!
40|Quiz time! Which 6-winged Digimon <br>uses the special move Heaven's Knuckle?|A: Lucemon? <br>B: Angemon <br>C: Angewomon?|B: Angemon
41|Quiz time! What Diaboromon special <br>move shoots energy balls from his chest?|A: Spider Shooter! <br>B: Catastrophe Cannon! <br>C: Atomic Blaster!|B: Catastrophe Cannon!
42|Quiz time! What impish fairy Digimon <br>holds its beloved spear "Fairy Tale"?|A: Piximon! <br>B: Seraphimon! <br>C: Boltmon!|A: Piximon!
43|Quiz time! What Digimon wants to get <br>stronger out of esteem for Gankoomon?|A: Wizardmon? <br>B: Starmon... <br>C: Hackmon?|C: Hackmon?
44|Quiz time! Who has a silver body, white <br>wings, and the move Disaster Blaster?|A: Shakkoumon? <br>B: Hououmon! <br>C: Lotosmon|A: Shakkoumon?
45|Quiz time! Which Digimon has no sword: <br>Leopardmon, Minervamon or Veedramon?|A: Leopardmon... <br>B: Minervamon? <br>C: Veedramon!|C: Veedramon!
46|Quiz time! What does ore Digimon <br>Gotsumon's special move, Rock Fist, do?|A: Shoots crystals from its head! <br>B: Produces and hurls a massive crystal! <br>C: Furiously wails on a guitar!|A: Shoots crystals from its head!
47|Quiz time! What is lilac-themed <br>Lilamon's special move, Lila Shower?|A: Shoots flower-shaped energy bullets! <br>B: Heals allies with absorbed sunlight! <br>C: Shoots a thin beam from its hands!|C: Shoots a thin beam from its hands!
48|Quiz time! Which machine Digimon is seen <br>as strongest in the Digital World?|A: MetalGarurumon! <br>B: WarGrowlmon <br>C: Machinedramon|C: Machinedramon
49|Quiz time! What is the nickname for the <br>gigantic bird Digimon Aquilamon?|A: Gentleman of the Desert? <br>B: Great Eagle of the Desert... <br>C: Demon of the Desert?|B: Great Eagle of the Desert...
50|Quiz time! What is the name of the blade <br>held by cyborg Digimon Ravemon?|A: Raven King! <br>B: Goblin King! <br>C: Yaksha King!|A: Raven King!
51|Quiz time! Who is the beastman-like <br>Digimon wearing a long coat?|A: Gargomon? <br>B: Kyubimon! <br>C: BanchoLeomon?|C: BanchoLeomon?
52|Quiz time! What Terriermon special move <br>creates a small tornado with its ears?|A: Terrier Tornado <br>B: Meteor Wing? <br>C: Destruction Grenade!|A: Terrier Tornado
53|Quiz time! Togemon's special move is <br>"Needle \_\_\_." What goes in \_\_\_?|A: ...Ricochet? <br>B: ...Spray? <br>C: ...Shake?|B: ...Spray?
54|Quiz time! Who is the undead Digimon <br>king born from a nasty computer virus?|A: Paildramon <br>B: Myotismon? <br>C: Datamon?|B: Myotismon?
"""

RAW_HM = r"""
01|Quiz time! What kind of Digimon is Gabumon?|A: Beast <br>B: Reptile <br>C: Dinosaur|B: Reptile
02|Quiz time! Which of the following are not <br>in the Olympos XII?|A: Crescemon <br>B: Dianamon <br>C: Merukimon|A: Crescemon
03|Quiz time! Which has more horns, Lopmon <br>or Terriermon?|A: Lopmon <br>B: Terriermon <br>C: They both have the same number|A: Lopmon
04|Quiz time! What is the name of the lance <br>wielded by Gallantmon Crimson Mode?|A: Lightning Joust <br>B: Gungnir <br>C: Blutgang|B: Gungnir
05|Quiz time! What is the name of <br>Slayerdramon's sword?|A: Laevateinn <br>B: Balmung <br>C: Fragarach|C: Fragarach
06|Quiz time! How many drills does <br>Brakedramon have on its body?|A: 4 <br>B: 6 <br>C: 8|B: 6
07|Quiz time! What type is Arcadiamon <br>(In-Training)?|A: Data type <br>B: Virus Type <br>C: Free type|C: Free type
08|Quiz time! What is the name of <br>Valkyrimon's golden bird?|A: Freyr <br>B: Frey <br>C: Hououmon|A: Freyr
09|Quiz time! Which move capitalizes on <br>Turuiemon great leaping power?|A: Samurai Fist <br>B: Bunny Fist <br>C: Ninja Fist|C: Ninja Fist
10|Quiz time! Which Digimon does NOT <br>have wings?|A: GoldNumemon <br>B: PlatinumNumemon <br>C: BlackKingNumemon|C: BlackKingNumemon
11|Quiz time! Titamon's Zanjintou sword is <br>carved from the bones of which Digimon?|A: SkullGreymon <br>B: Coelamon <br>C: Meteormon|A: SkullGreymon
12|Quiz time! Which Digimon is also known <br>as the Depraved Monk of the Deep?|A: Neptunemon <br>B: Dragomon <br>C: ShogunGekomon|B: Dragomon
13|Quiz time! Which of the following walks on <br>two legs?|A: Monochromon <br>B: Triceramon <br>C: Groundramon|B: Triceramon
14|Quiz time! Which of the following has the <br>most wings?|A: Varodurumon <br>B: Goldramon <br>C: Magnadramon|C: Magnadramon
15|Quiz time! Which of the following does NOT <br>wear a scarf?|A: PileVolcamon <br>B: Lekismon <br>C: Pandamon|B: Lekismon
16|Quiz time! Which Puppet Digimon is <br>secretly jealous of Monzaemon?|A: Pumpkinmon <br>B: Pandamon <br>C: Etemon|B: Pandamon
17|Quiz time! Which Digimon has a heart <br>on its chest?|A: Monzaemon <br>B: MarineAngemon <br>C: Gabumon|B: MarineAngemon
18|Quiz time! What does Mushroomon <br>have on its gloves?|A: Skull and crossbones <br>B: Mushroom <br>C: X|A: Skull and crossbones
19|Quiz time! Which can fly, <br>Monodramon or Dracomon?|A: Monodramon <br>B: Dracomon <br>C: Neither|C: Neither
20|Quiz time! What color are <br>KingEtemon's gloves and boots?|A: Red <br>B: Blue <br>C: Yellow|B: Blue
21|Quiz time! What is the name of the <br>motorcycle-type machine Beelzemon rides?|A: Cerberus <br>B: Bahamut <br>C: Behemoth|C: Behemoth
22|Quiz time! Which of the following <br>is NOT an Undead?|A: Matadormon <br>B: Phantomon <br>C: Myotismon|B: Phantomon
23|Quiz time! Which of the following is <br>the lowest level?|A: Icemon <br>B: Gatomon <br>C: Renamon|C: Renamon
24|Quiz time! Which of the following <br>does NOT carry a spear?|A: Piximon <br>B: Sistermon Blanc <br>C: SkullSatamon|C: SkullSatamon
25|Quiz time! What is the name of the pair of swords wielded by Grademon.|A: Souken Transcendent Swords <br>B: Souken Gold Swords <br>C: Souken Gradalpha|C: Souken Gradalpha
26|Quiz time! What effect does GranDracmon's voice have?|A: Sleep <br>B: Charm <br>C: Curse|B: Charm
27|Quiz time! Which number does Duramon's <br>Blinded attack carve into the enemy?|A: 20 <br>B: 25 <br>C: 99|A: 20
28|Quiz time! Which Mega Digimon is known <br>as the "King of Bugs"?|A: MegaKabuterimon <br>B: HerculesKabuterimon <br>C: TyrantKabuterimon|C: TyrantKabuterimon
29|Quiz time! What color is the blade on <br>BaoHuckmon's tail?|A: Black <br>B: Red <br>C: White|B: Red
30|Quiz time! Clockmon can freely advance <br>and rewind time during which period?|A: 1000 and 2000 <br>B: 1900 and 1999 <br>C: 2001 and 2099|B: 1900 and 1999
31|Quiz time! Which weapon is stuck in <br>Pumpkinmon's head?|A: Axe <br>B: Knife <br>C: Ouryuken|A: Axe
32|Quiz time! Which of the following Digimon <br>uses Oriental martial arts?|A: Agunimon <br>B: Lobomon <br>C: Susanomon|A: Agunimon
33|Quiz time! Which Beast Humanoid Digimon <br>uses the Fist of the Beast King attack?|A: Leopardmon <br>B: WereGarurumon <br>C: Leomon|C: Leomon
34|Quiz time! Which Digimon looks almost <br>exactly the same as Frigimon?|A: MudFrigimon <br>B: Golemon <br>C: Gotsumon|A: MudFrigimon
35|Quiz time! Which Hybrid Digimon carries <br>the Ryugonken sword?|A: Gaiomon <br>B: Susanomon <br>C: KaiserGreymon|C: KaiserGreymon
36|Quiz time! Which Mythical Animal Digimon <br>Digivolves into Gryphonmon?|A: HippoGryphonmon <br>B: Unimon <br>C: Garudamon|A: HippoGryphonmon
37|Quiz time! What does DemiDevimon throw <br>during its Demi Darts special move?|A: Black arrow <br>B: Thin beam <br>C: Syringe|C: Syringe
38|Quiz time! Which Digimon was born when <br>lots of Diaboromon were fused?|A: Creepymon <br>B: Arcadiamon (Ultra) <br>C: Armageddemon|C: Armageddemon
39|Quiz time! What is the alias by which <br>Rapidmon is also known?|A: Light Speed Archer <br>B: Keen Hound <br>C: Swift Hunter|B: Keen Hound
40|Quiz time! What does Ankylomon have on <br>the end of its tail?|A: Iron ball <br>B: Axe <br>C: Drill|A: Iron ball
"""

CS_URL = ("https://gamefaqs.gamespot.com/pc/268807-digimon-story-cyber-sleuth-complete-edition/"
          "faqs/78530/cyber-sleuth-quiz-answers")
HM_URL = ("https://gamefaqs.gamespot.com/pc/268807-digimon-story-cyber-sleuth-complete-edition/"
          "faqs/78530/hackers-memory-quiz-answers")


def clean(text: str) -> str:
    """Join in-game line wraps, unescape '\\_', drop the guide's *1* note marker."""
    text = re.sub(r"-\s*<br>\s*", "-", text)        # hyphenated wrap: "mind-<br>controlled"
    text = re.sub(r"<br\s*/?>", " ", text)
    text = re.sub(r"\s*\\?\*\d+\\?\*\s*", " ", text)  # stray note marker in CS Digimon Q39
    text = text.replace("\\_", "_")
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def parse(raw: str, game: str, id_prefix: str):
    out = []
    for line in raw.strip().splitlines():
        line = line.strip()
        if not line:
            continue
        num, question, choices, answer = [part.strip() for part in line.split("|")]
        answers = [clean(part) for part in re.split(r"<br\s*/?>", choices)]
        answers = [re.sub(r"^[ABC]:\s*", "", a) for a in answers]
        m = re.match(r"^([ABC]):\s*(.*)$", clean(answer))
        correct_letter, correct_text = m.group(1), m.group(2).strip()
        out.append({
            "id": f"{id_prefix}_{int(num):02d}",
            "game": game,
            "number": int(num),
            "question": clean(question),
            "answers": answers,
            "correctLetter": correct_letter,
            "correctIndex": "ABC".index(correct_letter),
            "correctAnswerText": correct_text,
        })
    return out


questions = parse(RAW_CS, "Cyber Sleuth", "cs") + parse(RAW_HM, "Hacker's Memory", "hm")

# --- validation -------------------------------------------------------------
cs = [q for q in questions if q["game"] == "Cyber Sleuth"]
hm = [q for q in questions if q["game"] == "Hacker's Memory"]
assert [q["number"] for q in cs] == list(range(1, 55)), "CS numbering broken"
assert [q["number"] for q in hm] == list(range(1, 41)), "HM numbering broken"
for q in questions:
    assert len(q["answers"]) == 3, f"{q['id']} does not have 3 answers"
    assert all(q["answers"]), f"{q['id']} has an empty answer"
    assert 0 <= q["correctIndex"] <= 2, q["id"]
    assert q["correctAnswerText"], q["id"]
    assert q["question"].startswith(("Quiz time!", "Check it.")), q["id"]
    assert "<br>" not in json.dumps(q), f"{q['id']} still has a line break"
    assert "\\" not in q["question"] + "".join(q["answers"]) + q["correctAnswerText"], \
        f"{q['id']} still has a backslash"

# the guide's "Real Answer" should match the text of the chosen option (allow accents)
def norm(s: str) -> str:
    import unicodedata
    return unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower().strip()

mismatch = [q["id"] for q in questions
            if norm(q["correctAnswerText"]) != norm(q["answers"][q["correctIndex"]])]
assert not mismatch, f"answer/letter mismatch: {mismatch}"

payload = {
    "meta": {
        "name": "DigiLine quiz time — Digimon questions",
        "game": "Digimon Story: Cyber Sleuth - Complete Edition",
        "sourceGuide": "GameFAQs — DigiLine Quiz Guide (PC) by GStelar04, v1.1 (2023-10-30)",
        "sourceUrls": [CS_URL, HM_URL],
        "filter": "Only questions from the guide's 'Digimon Questions' sections; Real Life and Game Questions sections were excluded.",
        "questionCount": len(questions),
        "counts": {"cyberSleuth": len(cs), "hackersMemory": len(hm)},
        "schema": {
            "id": "cs_01..cs_54 | hm_01..hm_40",
            "number": "question number from in-game code order (first text variation)",
            "question": "question as shown in game, line wraps joined",
            "answers": "3 multiple-choice answers, in A/B/C order",
            "correctLetter": "A, B or C",
            "correctIndex": "0-based index into answers",
            "correctAnswerText": "answer text as listed in the guide's 'Real Answer' column",
        },
        "notes": [
            "The guide states all text was ripped from the game files and shows the first 'variation'; alternate in-game wordings are not covered.",
            "CS Digimon Q39 carries a stray '*1*' note marker in the guide (likely a bugged-question note) which was removed from the question text.",
            "This JSON is a data compilation of the quiz questions/answers for modding use; question text remains property of Bandai Namco.",
        ],
    },
    "questions": questions,
}

OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# --- report -----------------------------------------------------------------
print(f"wrote {OUT}")
print(f"questions: {len(questions)} (CS {len(cs)} + HM {len(hm)})")
print("sample:", json.dumps(questions[0], ensure_ascii=False))
print("sample HM:", json.dumps(hm[0], ensure_ascii=False))
print("CS Q39:", json.dumps(next(q for q in cs if q["number"] == 39), ensure_ascii=False))
print("longest question:", max(len(q["question"]) for q in questions), "chars")
print("filesize:", OUT.stat().st_size, "bytes")
