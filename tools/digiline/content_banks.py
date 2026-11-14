"""Authored original text banks for the DigiLine v2 content packs.

ORIGINALITY POLICY (hard rule, plan decision D1, 2026-11-13)
------------------------------------------------------------
Every string in this module was written specifically for this project ("TamersLab" /
DigiLine v2) by the content workstream. No line is copied, lightly edited or
translated from:
  * `app/src/main/assets/species_chat_examples.json` (Data-shape reference ONLY)
  * `digimon_chat_export/` (SMT V / Cyber Sleuth / DCO exports)
  * any wiki, fan translation, or commercial game script.

The bundled `species_chat_examples.json` was inspected only to learn:
  1. how many species-chat entries exist (268), and
  2. the field *shape* (`opening`, `exchanges`, `characterId`, `speciesName`)
so that the generated species-question pack can cover the same species set.
Its prose is never reused; `validate_packs.py` additionally enforces that no
shipped line exactly matches a reference line.

Species **facts** (level / species type) are not authored: they are read from
`app/src/main/assets/species_catalog.json` and every factual line carries a
`species_catalog:<normalized name>` reference where the schema allows it.

Everything here is a hand-authored *bank*: the deterministic generator
(`build_packs.py`) composes cards/nodes from these banks, so the same input
always produces byte-identical packs.
"""

# ---------------------------------------------------------------------------
# Personality reference (mirrors domain/personality/DigimonPersonalityTraits.kt)
# ---------------------------------------------------------------------------
CORE_TYPES = {
    "PHILANTHROPY": [1, 2, 3, 4],
    "VALOR": [5, 6, 7, 8],
    "WISDOM": [9, 10, 11, 12],
    "AMICABILITY": [13, 14, 15, 16],
}

PERSONALITIES = {
    1: {"name": "ADORING", "core": "PHILANTHROPY"},
    2: {"name": "DEVOTED", "core": "PHILANTHROPY"},
    3: {"name": "TOLERANT", "core": "PHILANTHROPY"},
    4: {"name": "OVERPROTECTIVE", "core": "PHILANTHROPY"},
    5: {"name": "ZEALOUS", "core": "VALOR"},
    6: {"name": "BRAVE", "core": "VALOR"},
    7: {"name": "RECKLESS", "core": "VALOR"},
    8: {"name": "DARING", "core": "VALOR"},
    9: {"name": "ENLIGHTENED", "core": "WISDOM"},
    10: {"name": "SLY", "core": "WISDOM"},
    11: {"name": "ASTUTE", "core": "WISDOM"},
    12: {"name": "STRATEGIC", "core": "WISDOM"},
    13: {"name": "OPPORTUNISTIC", "core": "AMICABILITY"},
    14: {"name": "FRIENDLY", "core": "AMICABILITY"},
    15: {"name": "SOCIABLE", "core": "AMICABILITY"},
    16: {"name": "COMPASSIONATE", "core": "AMICABILITY"},
}

CORE_TONE = {
    "PHILANTHROPY": "compassion",
    "VALOR": "courage",
    "WISDOM": "insight",
    "AMICABILITY": "friendship",
}

CORE_ORDER = ["PHILANTHROPY", "VALOR", "WISDOM", "AMICABILITY"]

# ---------------------------------------------------------------------------
# Species questions — prompt templates, authored per stage tier.
# `needs` lists the catalog facts a template may mention; the generator only
# picks a template whose needs are satisfied by the species record.
# ---------------------------------------------------------------------------
SPECIES_PROMPTS = {
    1: [
        {"template": "{name}. That's the name I answer to. What kind of partner do you think I could become?",
         "needs": ("name",)},
        {"template": "They keep saying I'm at the {level} level. What should I work on while I'm still learning?",
         "needs": ("level",)},
        {"template": "I want to be useful before I'm strong. What should I practice first?",
         "needs": ()},
        {"template": "Everything out here is louder than I expected. How should I handle it?",
         "needs": ()},
        {"template": "Do you think I'm growing fast enough? Tell me what you actually want from me.",
         "needs": ()},
    ],
    2: [
        {"template": "The record says I'm at the {level} level. What do you actually need me to be?",
         "needs": ("level",)},
        {"template": "Other Digimon put me in a category and expect me to match it. What should I do with that?",
         "needs": ()},
        {"template": "You say {name} like it means something. What kind of Digimon do you want that name to stand for?",
         "needs": ("name",)},
        {"template": "I feel steadier than before. Should I stay close or start taking the lead?",
         "needs": ()},
        {"template": "When a fight starts, how should I act?",
         "needs": ()},
    ],
    3: [
        {"template": "{name} is a heavy name to carry into the next fight. What should it mean from now on?",
         "needs": ("name",)},
        {"template": "Others expect me to behave a certain way because of what I am. What matters most when those expectations collide?",
         "needs": ()},
        {"template": "I've grown enough to see my own limits. Which one should I work on first?",
         "needs": ()},
        {"template": "If I had to choose between power and trust, which would you have me choose?",
         "needs": ()},
        {"template": "I want the next step to mean something. What would you ask of me?",
         "needs": ()},
    ],
    4: [
        {"template": "They say I've reached the {level} level. What should this strength be for?",
         "needs": ("level",)},
        {"template": "Others look at what I've become and decide who I am. What should I show them instead?",
         "needs": ()},
        {"template": "Every choice I make echoes further now. What kind of example should I set?",
         "needs": ()},
        {"template": "I can carry more weight than before. What should I carry for you?",
         "needs": ()},
        {"template": "Some Digimon fear what I've become. How should I answer that fear?",
         "needs": ()},
    ],
}

# Authored answer banks per core. Each entry is an original player line + the
# authored reaction the Digimon gives, plus an explicit personality shift.
SPECIES_CORE_OPTIONS = {
    "PHILANTHROPY": [
        {"text": "Take the kindest path you can. Strength that frightens people isn't strength I want.",
         "reaction": "Then I'll measure my strength by how safe others feel.",
         "shift": {1: 2, 2: 1}, "moodDelta": 2},
        {"text": "Protect the small things first. The rest can wait while someone needs you.",
         "reaction": "I can do that. Small things are easy to forget and hard to lose.",
         "shift": {4: 2, 3: 1}, "moodDelta": 2},
        {"text": "Stay patient with yourself. I'd rather see you steady than fast.",
         "reaction": "Steady it is. I'll stop counting how quickly I grow.",
         "shift": {3: 2, 1: 1}, "moodDelta": 1},
        {"text": "Listen to the people around you. That matters more than any title.",
         "reaction": "Then I'll listen first and decide after.",
         "shift": {2: 2, 3: 1}, "moodDelta": 1},
        {"text": "Be gentle where you can afford to be. You can always be firm later.",
         "reaction": "Gentle first. I can live with that rule.",
         "shift": {1: 2, 4: 1}, "moodDelta": 2},
        {"text": "Keep your promises small enough to keep, and keep them all.",
         "reaction": "Small promises, kept. That sounds like a life I could be proud of.",
         "shift": {2: 2, 4: 1}, "moodDelta": 1},
        {"text": "You're already enough as you are. Grow because you want to, not because you're behind.",
         "reaction": "Enough as I am. I didn't know how much I needed to hear that.",
         "shift": {1: 2, 3: 1}, "moodDelta": 2},
        {"text": "Put the ones you care about ahead of the fight, every time.",
         "reaction": "Then they come first. The fight can find us after.",
         "shift": {4: 2, 2: 1}, "moodDelta": 1},
    ],
    "VALOR": [
        {"text": "Choose the action you'd still respect tomorrow.",
         "reaction": "The one I'd respect tomorrow. Got it.",
         "shift": {6: 2, 5: 1}, "moodDelta": 2},
        {"text": "You don't have to be fearless. Just don't let fear make the choice for you.",
         "reaction": "That's the kind of courage I can actually learn.",
         "shift": {6: 2, 8: 1}, "moodDelta": 2},
        {"text": "Face the thing that scares you before it grows. That's how courage works.",
         "reaction": "Then I'll go first, and I'll go while it's still small.",
         "shift": {5: 2, 7: 1}, "moodDelta": 1},
        {"text": "Stand where the line is, and don't step back from it.",
         "reaction": "Then I'll plant my feet and make it clear I'm not moving.",
         "shift": {6: 2, 5: 1}, "moodDelta": 1},
        {"text": "Be the one who moves when everyone else freezes. We'll follow.",
         "reaction": "Then I'll move first. Just don't let me move alone.",
         "shift": {8: 2, 7: 1}, "moodDelta": 2},
        {"text": "Whatever happens next, stay loyal to the ones who trust you, even when it costs.",
         "reaction": "Then I'll be someone worth trusting, not just someone strong.",
         "shift": {5: 2, 6: 1}, "moodDelta": 2},
        {"text": "Do the hard thing because it's right, not because it's loud.",
         "reaction": "Right and quiet. I can hold that line.",
         "shift": {7: 2, 6: 1}, "moodDelta": 1},
        {"text": "If you fall, get up. If you can't, call for me and we'll get up together.",
         "reaction": "Together, then. I won't pretend I can always stand alone.",
         "shift": {8: 2, 5: 1}, "moodDelta": 2},
    ],
    "WISDOM": [
        {"text": "Look at the whole road, not just the next fight. Choose what keeps you moving.",
         "reaction": "The whole road. I'll stop staring at my feet.",
         "shift": {9: 2, 12: 1}, "moodDelta": 2},
        {"text": "Ask better questions before you pick a side.",
         "reaction": "Better questions first. I can do that.",
         "shift": {11: 2, 10: 1}, "moodDelta": 1},
        {"text": "Learn from what went wrong, then let it go.",
         "reaction": "Take the lesson, drop the weight. Fair.",
         "shift": {9: 2, 11: 1}, "moodDelta": 1},
        {"text": "Measure twice, act once. I'd rather be late than sorry.",
         "reaction": "Twice measured. I'll remember that.",
         "shift": {12: 2, 11: 1}, "moodDelta": 2},
        {"text": "There's more than one answer. Find the one that keeps everyone standing.",
         "reaction": "Everyone standing. That's a better goal than winning.",
         "shift": {9: 2, 12: 1}, "moodDelta": 2},
        {"text": "Trust what you can see, and stay honest about what you can't.",
         "reaction": "Honest about the gaps. Yes.",
         "shift": {10: 2, 11: 1}, "moodDelta": 1},
        {"text": "A quiet mind wins more fights than a loud one.",
         "reaction": "Then I'll keep my voice low and my eyes open.",
         "shift": {10: 2, 9: 1}, "moodDelta": 1},
        {"text": "Think about what happens after the victory, not just the victory.",
         "reaction": "After. Nobody ever asks me about after.",
         "shift": {12: 2, 9: 1}, "moodDelta": 2},
    ],
    "AMICABILITY": [
        {"text": "Whatever we do next, I'd like us to do it together.",
         "reaction": "Together it is. That already feels lighter.",
         "shift": {14: 2, 15: 1}, "moodDelta": 2},
        {"text": "Make room for the people who are slower or quieter. They matter too.",
         "reaction": "Then I'll save them a seat and a story.",
         "shift": {16: 2, 14: 1}, "moodDelta": 2},
        {"text": "Keep the people who make you feel like yourself close.",
         "reaction": "You're one of those people. Don't forget it.",
         "shift": {13: 2, 14: 1}, "moodDelta": 1},
        {"text": "A good day is one where nobody was left behind.",
         "reaction": "Then today can be a good day.",
         "shift": {15: 2, 16: 1}, "moodDelta": 2},
        {"text": "Say the kind thing out loud. It's worth the courage.",
         "reaction": "Out loud, then. Even if my voice wobbles.",
         "shift": {14: 2, 16: 1}, "moodDelta": 1},
        {"text": "Take the detour if it means better company.",
         "reaction": "Then let's take the long way home now and then.",
         "shift": {15: 2, 13: 1}, "moodDelta": 1},
        {"text": "You don't have to fill every silence. Some of my favorites were quiet.",
         "reaction": "Then let's sit a moment. No words needed.",
         "shift": {16: 2, 15: 1}, "moodDelta": 1},
        {"text": "Let your friends see the real you. That's the only way trust grows.",
         "reaction": "The real me. That's a scary thought, but I'll try.",
         "shift": {13: 2, 14: 1}, "moodDelta": 2},
    ],
}

# Three-core rotations so every species card offers movement into three cores.
SPECIES_CORE_COMBOS = [
    ["PHILANTHROPY", "VALOR", "WISDOM"],
    ["VALOR", "WISDOM", "AMICABILITY"],
    ["WISDOM", "AMICABILITY", "PHILANTHROPY"],
    ["AMICABILITY", "PHILANTHROPY", "VALOR"],
]

# ---------------------------------------------------------------------------
# Generic (lore-neutral) questions — every personality 1..16 is assigned at
# least four questions. `GENERIC_TYPE_PAIRS[i]` drives question i: the asker
# types, and the intended shift lanes (option A -> first type, option B ->
# second type, option C -> a third core chosen by the generator).
# ---------------------------------------------------------------------------
GENERIC_TYPE_PAIRS = [
    # round 1: paired within a core
    (1, 2), (3, 4), (5, 6), (7, 8), (9, 10), (11, 12), (13, 14), (15, 16),
    # round 2: paired across opposite cores
    (1, 16), (2, 15), (3, 14), (4, 13), (5, 12), (6, 11), (7, 10), (8, 9),
    # round 3: paired across adjacent cores
    (1, 9), (2, 10), (3, 11), (4, 12), (5, 13), (6, 14), (7, 15), (8, 16),
    # round 4: paired across the remaining adjacencies
    (1, 8), (2, 7), (3, 6), (4, 5), (9, 16), (10, 15), (11, 14), (12, 13),
]

GENERIC_QUESTIONS = [
    # --- round 1 -----------------------------------------------------------
    {
        "prompt": "When I do something kind, do you want me to say it out loud or let it show quietly?",
        "options": [
            {"text": "Tell me out loud. I like knowing what you feel.",
             "reaction": "Then I'll say it, even when my voice wobbles."},
            {"text": "Actions are enough. I'll remember what you did.",
             "reaction": "Good. My promises are quieter than my words."},
            {"text": "Do whatever feels natural. I'll notice either way.",
             "reaction": "That takes the pressure off. Thanks."},
        ],
    },
    {
        "prompt": "If I saw you about to try something risky, should I speak up straight away or wait until you ask?",
        "options": [
            {"text": "Wait a little. Let me learn from my own mistakes.",
             "reaction": "I'll try to be worth the room you give me."},
            {"text": "Speak up. I'd rather hear a warning too early than too late.",
             "reaction": "Then I'll keep my warnings honest and quick."},
            {"text": "Depends on the risk. Use your judgment.",
             "reaction": "I'll judge the danger, not the person."},
        ],
    },
    {
        "prompt": "What should I do with my energy when a plan falls apart?",
        "options": [
            {"text": "Throw it into the next plan. Don't let the fire go out.",
             "reaction": "Right. The fire is mine to keep, not the plan's."},
            {"text": "Steady yourself first, then pick the action that matters most.",
             "reaction": "Steady, then decisive. I can do that."},
            {"text": "Take a breath and see what's actually left to save.",
             "reaction": "Counting what remains sounds better than counting what's lost."},
        ],
    },
    {
        "prompt": "There's a shortcut through the ruins. Everyone says it's risky. What do you think?",
        "options": [
            {"text": "Take it. A risk we choose is better than a delay we regret.",
             "reaction": "Then let's move before the hesitation catches up."},
            {"text": "Let's scout it first. I want to know what we're betting on.",
             "reaction": "Smart. I'd rather win the bet than survive it."},
            {"text": "The long road is fine if the shortcut is a guess.",
             "reaction": "Then I'll keep my pace easy and my eyes open."},
        ],
    },
    {
        "prompt": "When I'm hiding something, do you want the truth right away or a chance to work it out?",
        "options": [
            {"text": "Give me a moment. I'd rather understand than pry.",
             "reaction": "You always give me room to find my own words."},
            {"text": "Let me guess for a while. Half the fun is the puzzle.",
             "reaction": "Then I'll drop a hint that's almost fair."},
            {"text": "Just tell me the part I need to know.",
             "reaction": "Plain and useful. I can live with that."},
        ],
    },
    {
        "prompt": "We have one afternoon and two places we could be. How should we decide?",
        "options": [
            {"text": "List what we'd gain from each. The right choice usually shows in the details.",
             "reaction": "Details it is. I'll bring the list."},
            {"text": "Pick the one that helps the bigger plan, even if it's less fun.",
             "reaction": "The bigger plan thanks you, even if my feet don't."},
            {"text": "Flip a coin and see which result disappoints me.",
             "reaction": "Clever. Even a coin can reveal what we want."},
        ],
    },
    {
        "prompt": "A stranger offers us a good deal at a fair price. Do we take it?",
        "options": [
            {"text": "Take it. Good timing is worth as much as good intentions.",
             "reaction": "Then I'll shake on it before the moment cools."},
            {"text": "Take it, but ask how they're doing first. Good deals can be friendly.",
             "reaction": "A fair deal between friends is the best kind."},
            {"text": "Only if we understand what they get out of it.",
             "reaction": "No invisible hooks. Agreed."},
        ],
    },
    {
        "prompt": "Nobody's talking on the way home. Should we fill the silence?",
        "options": [
            {"text": "Yes. Words keep the long road short.",
             "reaction": "Then I'll start with the smallest story I know."},
            {"text": "No. Sometimes quiet is the kindest company.",
             "reaction": "Then I'll walk beside you and let the road talk."},
            {"text": "Ask one small question and see what happens.",
             "reaction": "One question, no pressure. I like the odds."},
        ],
    },
    # --- round 2 -----------------------------------------------------------
    {
        "prompt": "I helped someone today, but I'm not sure I did the right thing. Do you want to hear the details?",
        "options": [
            {"text": "Yes. Tell me the small parts too. They matter.",
             "reaction": "The small parts are the ones I kept replaying."},
            {"text": "Only if it's still sitting heavy on you. I can listen.",
             "reaction": "It is. Thanks for noticing without making me say it."},
            {"text": "Wait until you've rested. The story will be clearer then.",
             "reaction": "Good idea. Tired memories lie a little."},
        ],
    },
    {
        "prompt": "I promised two people things I can't do at the same time. What now?",
        "options": [
            {"text": "Keep the promise to whoever truly needs you, and explain it honestly to the other.",
             "reaction": "Honest and hard. I'll do it before the day runs out."},
            {"text": "Talk to both together. Most problems shrink when everyone's in the room.",
             "reaction": "Everyone in the room. Yes, that's better than whispering."},
            {"text": "Ask for a day. Promises made in a rush are still promises.",
             "reaction": "One day to make it right. That's fair."},
        ],
    },
    {
        "prompt": "Someone in our group keeps making the same mistake. How should I bring it up?",
        "options": [
            {"text": "Wait for a calm moment and assume they can learn.",
             "reaction": "Calm and patient. You make correction sound survivable."},
            {"text": "Bring it up gently and offer to help fix it.",
             "reaction": "Offer help first. Then the mistake is small."},
            {"text": "Say it plainly, but once. Repeating it just makes people guard their backs.",
             "reaction": "Once, clearly, and then move on. Agreed."},
        ],
    },
    {
        "prompt": "There's a chance to gain something, but it means splitting the group for a while.",
        "options": [
            {"text": "Don't split. Whatever we gain isn't worth a gap in our watch.",
             "reaction": "Then the watch stays whole. I'd rather be careful than lucky."},
            {"text": "Take the chance. A short split is a small price for a real gain.",
             "reaction": "I'll take the chance, and I'll bring back more than I cost."},
            {"text": "Only split if both halves can reach each other quickly.",
             "reaction": "Short leash, long gain. That's a trade I can make."},
        ],
    },
    {
        "prompt": "We could finish a task tonight or do it properly tomorrow.",
        "options": [
            {"text": "Tonight. Momentum is worth more than a perfect plan.",
             "reaction": "Then I'll keep the momentum warm until the last step."},
            {"text": "Tomorrow. A rushed finish usually costs more than it saves.",
             "reaction": "Tomorrow, properly. I can be patient for one night."},
            {"text": "Tonight, if we fix the goal before we start.",
             "reaction": "A fixed goal makes the long night worth it."},
        ],
    },
    {
        "prompt": "I noticed a lie in what our contact said. Should I call it out?",
        "options": [
            {"text": "Yes. Say it openly and let them answer for it.",
             "reaction": "Then I'll say it to their face, not behind their back."},
            {"text": "Not yet. Note the details first and catch the whole pattern.",
             "reaction": "Pattern first. One lie is a thread, not a net."},
            {"text": "Ask one careful question and watch whether the story holds.",
             "reaction": "One question, then listen. I can do that."},
        ],
    },
    {
        "prompt": "A puzzle has three solutions. One is fast, one is clever, one is safe.",
        "options": [
            {"text": "Fast. If we hesitate, the puzzle wins.",
             "reaction": "Fast it is. I'll be the one who moves first."},
            {"text": "Clever. Winning without sweating is still winning.",
             "reaction": "Then watch me win this one with a smile."},
            {"text": "Safe first. We can always come back for the other two.",
             "reaction": "Safe now, clever later. Fine by me."},
        ],
    },
    {
        "prompt": "I want to try a new way of fighting. It could fail badly.",
        "options": [
            {"text": "Try it in a real fight. A new way only counts once it's tested.",
             "reaction": "Then I'll test it where it counts and take the bruises."},
            {"text": "Try it slowly first. Understanding beats surprise.",
             "reaction": "Slow and certain. I'll learn it before I trust it."},
            {"text": "Try it, but tell me the first sign of failure.",
             "reaction": "I'll call the warning before the mistake spreads."},
        ],
    },
    # --- round 3 -----------------------------------------------------------
    {
        "prompt": "When I feel strongly about something, I can't always explain why. Do you want the reason or the feeling first?",
        "options": [
            {"text": "The feeling first. Reasons can wait.",
             "reaction": "Then here's the feeling: I trust you with it."},
            {"text": "The reason, slowly. Then the feeling will make sense.",
             "reaction": "Slowly, then. I'll untangle it for you."},
            {"text": "Both, in whatever order comes out.",
             "reaction": "Fair. Strong feelings make their own order."},
        ],
    },
    {
        "prompt": "I know where the missing piece is. I could tell you now or lead you there myself.",
        "options": [
            {"text": "Lead. But tell me if I can help carry it.",
             "reaction": "Lead the way? I like that you'll help carry it."},
            {"text": "Tell me a riddle and let me find it. That's more fun.",
             "reaction": "A riddle it is. You'll hate how easy it is."},
            {"text": "Just the direction. I'd rather find the rest.",
             "reaction": "Direction only. Then the finding is yours."},
        ],
    },
    {
        "prompt": "Two of our friends are arguing. One is wrong and doesn't know it.",
        "options": [
            {"text": "Let them finish first. Being wrong out loud is part of learning.",
             "reaction": "You'd let someone finish being wrong. That's rarer than it should be."},
            {"text": "Point out the exact contradiction. Vague corrections help nobody.",
             "reaction": "Exact contradiction, no sarcasm. I can do that."},
            {"text": "Ask them what they actually want. Arguments hide that.",
             "reaction": "The want under the argument. That's the real target."},
        ],
    },
    {
        "prompt": "We can leave the camp early or finish one last useful thing.",
        "options": [
            {"text": "Leave early. A safe walk beats a useful delay.",
             "reaction": "Safe walk. I'll carry the useful thing tomorrow."},
            {"text": "Finish it. Timing is part of the plan, not an accident.",
             "reaction": "Finish it, then move. Understood."},
            {"text": "Leave early only if the thing can wait for us.",
             "reaction": "Then we check whether it can wait. Practical."},
        ],
    },
    {
        "prompt": "A deal is fair, but only slightly. Do we shake hands?",
        "options": [
            {"text": "No. I'd rather earn something worth being proud of.",
             "reaction": "Then we earn it properly. I'll take the longer road."},
            {"text": "Yes. Slightly fair is still fair, and the door is open.",
             "reaction": "The door is open. Let's not pretend fair is a trap."},
            {"text": "Ask for one better term. It costs nothing to try.",
             "reaction": "One better term. If they refuse, we still know them better."},
        ],
    },
    {
        "prompt": "I'm about to admit a mistake in front of everyone. Say something first?",
        "options": [
            {"text": "Go ahead. Owning it is braver than hiding it.",
             "reaction": "Then I'll own it with my chin up."},
            {"text": "I'll stand beside you while you say it.",
             "reaction": "You standing there will make it half as loud."},
            {"text": "Say it once, clearly, and don't apologize twice.",
             "reaction": "Once, clearly. Then we move on."},
        ],
    },
    {
        "prompt": "There's a strange noise past the ridge. Do we investigate?",
        "options": [
            {"text": "Yes. Standing here guessing is worse.",
             "reaction": "Then let's stop guessing and start walking."},
            {"text": "Yes, but let's bring the others. Stories are better shared.",
             "reaction": "The others make everything less strange."},
            {"text": "Check from a distance first. Curiosity can wait a minute.",
             "reaction": "A minute of watching before a step. Sensible."},
        ],
    },
    {
        "prompt": "A frightened wild Digimon is blocking the path. How do we get through?",
        "options": [
            {"text": "Walk forward calmly. Most threats fold when they meet resolve.",
             "reaction": "Calm and forward. I won't rush the fear."},
            {"text": "Give it room and speak softly. Fear needs space to settle.",
             "reaction": "Space, soft words, time. I can wait."},
            {"text": "Find another way around and leave it be.",
             "reaction": "Another way is fine. No need to win a pointless fight."},
        ],
    },
    # --- round 4 -----------------------------------------------------------
    {
        "prompt": "I want to do something bold tomorrow. Would you come with me?",
        "options": [
            {"text": "Of course. I'd follow you anywhere, you know that.",
             "reaction": "Then I'll make sure bold turns out to be worth it."},
            {"text": "Only if we plan the bold part together.",
             "reaction": "Together, then. Bold is better with a plan."},
            {"text": "Tell me the plan first. I'll decide after.",
             "reaction": "Fair. I'd rather earn the yes than assume it."},
        ],
    },
    {
        "prompt": "I could rush ahead to warn them, or stay and finish the work here.",
        "options": [
            {"text": "Stay. Finish what you promised, then we'll go together.",
             "reaction": "Then I'll finish it and keep my promise honest."},
            {"text": "Rush ahead. Warnings lose value with every minute.",
             "reaction": "Then I'll move fast and make every minute count."},
            {"text": "Send word somehow and keep working.",
             "reaction": "A message and steady hands. I like both."},
        ],
    },
    {
        "prompt": "Someone insulted our group. Do I answer with words or silence?",
        "options": [
            {"text": "Silence, then work. Results answer louder than insults.",
             "reaction": "Then I'll let the work do the talking."},
            {"text": "Words, clearly and once. Let them know where the line is.",
             "reaction": "Once, clearly. They'll remember the line."},
            {"text": "Ask what they hope to gain. Insults usually have a reason.",
             "reaction": "The reason under the insult. I'll look there."},
        ],
    },
    {
        "prompt": "The weather looks bad. Do we risk the pass or turn back?",
        "options": [
            {"text": "Turn back. There's no shame in surviving a storm.",
             "reaction": "Then we live to cross it another day. Good."},
            {"text": "Push through. If we turn back now, we'll doubt ourselves for a week.",
             "reaction": "Then I'll push first and doubt later."},
            {"text": "Watch the sky for an hour, then choose.",
             "reaction": "An hour of watching beats a day of regret."},
        ],
    },
    {
        "prompt": "I think I understand why the village is anxious. Do you want to hear my guess?",
        "options": [
            {"text": "Yes. Say it plainly and we'll test it together.",
             "reaction": "Plainly, then. And we test it before we trust it."},
            {"text": "Yes, but let's ask them first. Guesses can bruise.",
             "reaction": "Ask first. A guess can be gentle when it's curious."},
            {"text": "Hold it until we have one more fact.",
             "reaction": "One more fact. Patience makes better answers."},
        ],
    },
    {
        "prompt": "I could tell the story of how we met, or keep it for a quieter night.",
        "options": [
            {"text": "Keep it. A story gains flavor when it's rare.",
             "reaction": "Then I'll keep it safe and warm for later."},
            {"text": "Tell it now. Everyone's here and listening.",
             "reaction": "Then gather close. I'll tell it properly."},
            {"text": "Tell the short version and save the rest.",
             "reaction": "Short now, long later. A fair bargain."},
        ],
    },
    {
        "prompt": "I noticed our host is nervous. Should I ask about it?",
        "options": [
            {"text": "Ask directly, but name the detail that gave it away.",
             "reaction": "The exact detail. Then they'll know I paid attention."},
            {"text": "Ask gently, without making it a test.",
             "reaction": "Gently, then. Nerves don't deserve an interrogation."},
            {"text": "Wait. Nervousness often solves itself.",
             "reaction": "Waiting costs nothing. I'll keep an eye out."},
        ],
    },
    {
        "prompt": "There's one reward left and two ways to earn it.",
        "options": [
            {"text": "Take the way that also helps later. Rewards should compound.",
             "reaction": "Compound it. I like rewards that grow."},
            {"text": "Take the faster way. A reward in hand beats a story about one.",
             "reaction": "In hand, then. Stories can wait."},
            {"text": "Split the effort and see which way is honest.",
             "reaction": "Split and watch. Honest paths usually show themselves."},
        ],
    },
]

# ---------------------------------------------------------------------------
# Generic question expansion (contentVersion 2).
#
# The 32 cards above are hand-authored one by one. This bank adds 96 more
# authored cards as 12 themes x 8 prompts: each prompt is a lore-neutral
# dilemma the owned Digimon asks the Tamer, and each card composes three of the
# theme's authored per-core stance banks (one lane per core, so every card
# offers movement into three quadrants). `build_packs.py` pairs personalities
# cross-quadrant for 12 rounds, one theme per round, so each of the 16
# personalities receives 16 generic questions combined (plan §6.4 target: >= 8).
# ---------------------------------------------------------------------------
GENERIC_THEMES = [
    {
        "theme": "risk_and_courage",
        "prompts": [
            "There's a storm rolling in and a shortcut that saves us half a day. Should we take it?",
            "I froze during the last fight. How do I make sure it doesn't happen again?",
            "Everyone else is retreating. Do we hold the line or fall back with them?",
            "I want to try something I've never done before. How do I know if I'm ready?",
            "A stranger needs help across a flooded road. How much risk is too much?",
            "I keep hesitating before the first step. What would you have me do?",
            "The plan says wait, but my instincts say move. Which should I trust?",
            "If the fight looks unwinnable, should I still stand my ground?",
        ],
        "lanes": {
            "PHILANTHROPY": [
                {"text": "Only take the risk that lets everyone walk home after. I can wait.",
                 "reaction": "Then I'll count the people before I count the danger."},
                {"text": "Ask who pays if it goes wrong. If it's only us, go; if not, stay.",
                 "reaction": "That's a fair test. I'll use it every time."},
            ],
            "VALOR": [
                {"text": "Go. A choice you keep avoiding becomes a fear you keep carrying.",
                 "reaction": "Then I'll go, and carry the fear behind me."},
                {"text": "Stand where it matters and don't apologize for it.",
                 "reaction": "Then I'll plant my feet and make the reason clear."},
            ],
            "WISDOM": [
                {"text": "Check the odds first. Courage without a plan is just noise.",
                 "reaction": "Odds first, then the leap. Got it."},
                {"text": "Watch, learn, and move when the pattern is clear.",
                 "reaction": "I'll watch until the moment shows itself."},
            ],
            "AMICABILITY": [
                {"text": "Whatever you choose, don't choose it alone. We'll share the risk.",
                 "reaction": "Then it's half as heavy already."},
                {"text": "Ask the others what they can carry before you decide.",
                 "reaction": "Everyone carries something. That's how we get through."},
            ],
        },
    },
    {
        "theme": "care_and_comfort",
        "prompts": [
            "One of our friends is pretending not to be hurt. Should I say something?",
            "When I'm upset, do you want me to talk about it or sit quietly?",
            "Someone needs comfort and I don't know the right words. What should I do?",
            "I want to help, but I might be in the way. How do I tell?",
            "Is it better to cheer someone up or let them be sad for a while?",
            "I forgot to ask how you were doing today. Does that matter?",
            "A stranger looks lost and scared. Do I approach or keep my distance?",
            "How do I comfort someone without making it about me?",
        ],
        "lanes": {
            "PHILANTHROPY": [
                {"text": "Sit with them first. Comfort isn't words, it's presence.",
                 "reaction": "Presence. I can do that better than words."},
                {"text": "Be gentle and patient. The right moment usually arrives on its own.",
                 "reaction": "Then I'll be patient for both of us."},
            ],
            "VALOR": [
                {"text": "Speak up. Silence lets hurt grow roots.",
                 "reaction": "Then I'll say something, even if it shakes."},
                {"text": "Stand in front of them if they need shielding.",
                 "reaction": "Shielding is simple. I understand simple."},
            ],
            "WISDOM": [
                {"text": "Watch first. Hurt has a shape, and the shape tells you what's needed.",
                 "reaction": "I'll look before I speak. That feels fair."},
                {"text": "Ask one careful question instead of guessing.",
                 "reaction": "One good question. That I can manage."},
            ],
            "AMICABILITY": [
                {"text": "Say the plain thing: I'm here, and you don't have to be okay.",
                 "reaction": "I'd want someone to say that to me."},
                {"text": "Bring the others close. Nobody should be alone with that.",
                 "reaction": "No one alone. I like that rule."},
            ],
        },
    },
    {
        "theme": "planning_and_timing",
        "prompts": [
            "We could start the long job today or rest first and begin tomorrow. Which is better?",
            "I keep changing the plan every time something goes wrong. How do I stop?",
            "How do I decide which task actually matters most?",
            "A good chance appears early, but we're not ready. Do we take it?",
            "What should I do when the plan needs more time than we have?",
            "Is it better to finish one thing well or start three things at once?",
            "How far ahead should I be looking when the road keeps changing?",
            "The others want to move now, but the timing feels wrong. What do I say?",
        ],
        "lanes": {
            "PHILANTHROPY": [
                {"text": "Rest matters too. A tired road is a longer road.",
                 "reaction": "Then I'll rest without feeling guilty."},
                {"text": "Take care of the people doing the work before the work itself.",
                 "reaction": "People before the task. Understood."},
            ],
            "VALOR": [
                {"text": "Pick the choice you'd defend tomorrow, and start moving.",
                 "reaction": "Then I'll move, and answer for it tomorrow."},
                {"text": "Don't wait for perfect conditions. They never arrive.",
                 "reaction": "Then I'll go with good enough and make it better."},
            ],
            "WISDOM": [
                {"text": "Write the steps down. A plan you can see is easier to fix.",
                 "reaction": "On paper it can't hide from me."},
                {"text": "Find the one decision that makes the rest easier.",
                 "reaction": "One decision. That's a relief."},
            ],
            "AMICABILITY": [
                {"text": "Tell the others the plan and let them help hold it.",
                 "reaction": "Shared plans don't slip as easily."},
                {"text": "Ask what everyone needs, then time it around that.",
                 "reaction": "Timing around people. That's kinder."},
            ],
        },
    },
    {
        "theme": "truth_and_honesty",
        "prompts": [
            "I broke something and nobody knows. Should I say it was me?",
            "A friend asks if their work is good. It isn't. What do I say?",
            "I don't know the answer. Is it better to guess or admit it?",
            "Someone told me a secret I shouldn't have. Do I pass it on?",
            "How do I tell you the truth when I'm afraid it'll disappoint you?",
            "Is a kind lie ever better than a hard truth?",
            "I made a promise I can't keep. What now?",
            "The others believe something wrong. Do I correct them?",
        ],
        "lanes": {
            "PHILANTHROPY": [
                {"text": "Tell the truth gently. Hard words can still have soft edges.",
                 "reaction": "Gentle and honest. I'll try both."},
                {"text": "Think about who it protects before you speak.",
                 "reaction": "Then I'll check the harm first."},
            ],
            "VALOR": [
                {"text": "Say it plainly. The truth is worth the discomfort.",
                 "reaction": "Then I'll say it and take the sting."},
                {"text": "Own the mistake before anyone has to ask.",
                 "reaction": "Before they ask. Yes."},
            ],
            "WISDOM": [
                {"text": "Be exact. A vague truth just moves the confusion.",
                 "reaction": "Exact, not cruel. Got it."},
                {"text": "Wait for the right moment, but don't bury it.",
                 "reaction": "Timing, not hiding. Fair."},
            ],
            "AMICABILITY": [
                {"text": "Say it while you're standing next to them, not across the room.",
                 "reaction": "Beside them, not above them."},
                {"text": "Ask what they need to hear before you decide how to say it.",
                 "reaction": "Their need first. That's a good habit."},
            ],
        },
    },
    {
        "theme": "friendship_and_belonging",
        "prompts": [
            "The others have known each other longer than they've known me. How do I fit in?",
            "I like being alone sometimes. Does that make me a bad friend?",
            "A friend is drifting away. Should I chase them or let them go?",
            "How do I join a group without making it awkward?",
            "Someone in the group keeps excluding me. What should I say?",
            "I don't know what to give a friend. What matters most?",
            "How do you apologize so it lands honestly?",
            "Is it better to have a few close friends or many easy ones?",
        ],
        "lanes": {
            "PHILANTHROPY": [
                {"text": "Be patient. Belonging grows slower than it arrives.",
                 "reaction": "Then I'll stop rushing it."},
                {"text": "Give the care first and the seat will follow.",
                 "reaction": "Care first. I can do that."},
            ],
            "VALOR": [
                {"text": "Say the awkward thing out loud. Silence keeps the gap open.",
                 "reaction": "Then I'll speak and survive the awkward."},
                {"text": "Stand beside them even when it's inconvenient.",
                 "reaction": "Inconvenient is when it counts."},
            ],
            "WISDOM": [
                {"text": "Watch how the group works before you work out your place.",
                 "reaction": "Watch, then step in. Good."},
                {"text": "Honesty about the drift is better than guessing.",
                 "reaction": "Ask instead of guessing. Fair."},
            ],
            "AMICABILITY": [
                {"text": "Talk to them directly. Friendship can survive one hard conversation.",
                 "reaction": "One conversation. I can manage that."},
                {"text": "Ask them to include the next person, and to be included yourself.",
                 "reaction": "Ask to be included. That's not weakness."},
            ],
        },
    },
    {
        "theme": "growth_and_change",
        "prompts": [
            "I'm becoming someone I don't recognize. Is that bad?",
            "How do I know if I'm growing or just getting older?",
            "I want to change something about myself. Where do I start?",
            "What should I keep from who I used to be?",
            "Others expect me to stay the same. Can I disappoint them?",
            "How do I change without losing what you like about me?",
            "A change would make life harder for a while. Is it worth it?",
            "What if I grow in a direction you didn't expect?",
        ],
        "lanes": {
            "PHILANTHROPY": [
                {"text": "Grow at your own speed. I'd rather have you whole than fast.",
                 "reaction": "Whole over fast. That's a promise I can keep."},
                {"text": "Keep the gentle parts. They're worth carrying forward.",
                 "reaction": "The gentle parts stay. Good."},
            ],
            "VALOR": [
                {"text": "Take the first step before you can talk yourself out of it.",
                 "reaction": "First step first. I'll go."},
                {"text": "Don't shrink to fit what others expected.",
                 "reaction": "Then I'll stand taller, not smaller."},
            ],
            "WISDOM": [
                {"text": "Notice what caused the change. Understanding keeps it from controlling you.",
                 "reaction": "Cause first, fear later. Got it."},
                {"text": "Compare who you were to who you chose to become.",
                 "reaction": "Chosen, not drifted. That matters."},
            ],
            "AMICABILITY": [
                {"text": "Talk it through with us. Change is lighter when it's shared.",
                 "reaction": "Shared change. I like that better."},
                {"text": "Let the people who love you meet the new version.",
                 "reaction": "Then I'll introduce them properly."},
            ],
        },
    },
    {
        "theme": "conflict_and_boundaries",
        "prompts": [
            "Someone keeps pushing my patience. How do I push back?",
            "Two friends are arguing and both want me on their side. What do I do?",
            "When should I compromise and when should I hold firm?",
            "Someone insulted you in front of me. How should I respond?",
            "A boundary I set is being ignored. Do I repeat it or leave?",
            "How do I disagree without turning it into a fight?",
            "Is walking away from an argument weak?",
            "What do I do when I'm the one who was wrong in the fight?",
        ],
        "lanes": {
            "PHILANTHROPY": [
                {"text": "Stay calm and assume they can learn. Only raise your voice for a real line.",
                 "reaction": "Calm first. Loud only for what matters."},
                {"text": "Leave room for them to be wrong without being humiliated.",
                 "reaction": "Dignity for them too. Fair."},
            ],
            "VALOR": [
                {"text": "Say it once, clearly, and don't back away from it.",
                 "reaction": "Once, clearly. Done."},
                {"text": "Defend your side, but only where the line actually is.",
                 "reaction": "Then I'll stand at the line, not past it."},
            ],
            "WISDOM": [
                {"text": "Find what the argument is actually about before choosing a weapon.",
                 "reaction": "The real issue first. Good."},
                {"text": "Name the facts, not the person.",
                 "reaction": "Facts, not names. I can do that."},
            ],
            "AMICABILITY": [
                {"text": "Keep the relationship bigger than the argument.",
                 "reaction": "Then I'll fight for the friendship, not the win."},
                {"text": "Ask what both sides need, then see if one answer covers it.",
                 "reaction": "One answer for both. Worth looking for."},
            ],
        },
    },
    {
        "theme": "curiosity_and_learning",
        "prompts": [
            "What should I try to learn first about this world?",
            "I hate getting things wrong. How do I practice without dreading it?",
            "Should I ask more questions or trust what I already know?",
            "There's something I don't understand about you. Is it okay to ask?",
            "How do I learn faster without skipping the basics?",
            "What do I do when I discover I've been wrong for a long time?",
            "Do I need to understand something before I try it?",
            "Which is better: a wide look or a deep one?",
        ],
        "lanes": {
            "PHILANTHROPY": [
                {"text": "Learn about what you care for. Care makes curiosity last.",
                 "reaction": "Care behind the curiosity. Got it."},
                {"text": "Be patient with mistakes. They're the tuition, not the failure.",
                 "reaction": "Tuition, not failure. I like that."},
            ],
            "VALOR": [
                {"text": "Try the thing. You learn more from one attempt than ten warnings.",
                 "reaction": "Then I'll attempt it, and learn from the bruises."},
                {"text": "Ask the brave question, not the safe one.",
                 "reaction": "The brave question. I'll ask it."},
            ],
            "WISDOM": [
                {"text": "Start with the basics and let them stack. Speed comes later.",
                 "reaction": "Basics first, speed later. Fine."},
                {"text": "Test what you think you know. That's how wrong turns into right.",
                 "reaction": "Test it. That makes the knowing real."},
            ],
            "AMICABILITY": [
                {"text": "Learn beside someone. It's easier to stay curious together.",
                 "reaction": "Then let's learn it side by side."},
                {"text": "Swap what you know with me. That's a fair trade.",
                 "reaction": "A fair trade. I'll go first."},
            ],
        },
    },
    {
        "theme": "work_and_duty",
        "prompts": [
            "How do I keep going when the job is boring and long?",
            "What's the difference between duty and habit?",
            "Should I do what's needed or what I'm good at?",
            "I'm tired of being reliable. Is that okay?",
            "How do I refuse extra work without letting the others down?",
            "What does it mean to do a job properly?",
            "When is it right to put down the work and be still?",
            "If nobody notices me doing the work, why do it?",
        ],
        "lanes": {
            "PHILANTHROPY": [
                {"text": "Do the part that helps a person, not just the part that finishes the job.",
                 "reaction": "People in the work. I'll remember that."},
                {"text": "Take the breaks you need. Unrest makes bad work.",
                 "reaction": "Breaks without guilt. Got it."},
            ],
            "VALOR": [
                {"text": "Finish what you started, even when it stopped being exciting.",
                 "reaction": "Then I'll finish it, boring or not."},
                {"text": "Say no when the yes would break you. That's a kind of courage too.",
                 "reaction": "Refusing takes nerve. I'll practice it."},
            ],
            "WISDOM": [
                {"text": "Do the necessary thing first, and the impressive thing after.",
                 "reaction": "Necessary first. Clear."},
                {"text": "Measure the job by what it's for, not by who notices.",
                 "reaction": "The purpose, not the praise."},
            ],
            "AMICABILITY": [
                {"text": "Share the burden out loud. Silent duty builds resentments.",
                 "reaction": "Then I'll say it before it builds."},
                {"text": "Do it together, and it stops feeling like a duty.",
                 "reaction": "Together makes it lighter."},
            ],
        },
    },
    {
        "theme": "rest_and_recovery",
        "prompts": [
            "I don't know how to rest without feeling lazy. Help?",
            "How do I tell if I need rest or if I'm giving up?",
            "The others are still working. Can I stop for a while?",
            "What's the best way to recover after a hard fight?",
            "I can't sleep. What should I do with a night like this?",
            "Is it weak to ask for help when I'm worn out?",
            "How do I make sure a rest actually refills me?",
            "What do I do with the guilt of resting while others wait?",
        ],
        "lanes": {
            "PHILANTHROPY": [
                {"text": "Rest is part of caring for yourself. You don't have to earn it.",
                 "reaction": "Then I'll rest without earning it."},
                {"text": "Let me look after something while you recover. That's fair.",
                 "reaction": "Then I'll let you, this once."},
            ],
            "VALOR": [
                {"text": "Stop before you fall down. That's not giving up, that's reloading.",
                 "reaction": "Reloading. I can call it that."},
                {"text": "Ask for help. It's braver than pretending.",
                 "reaction": "Then I'll ask, even if my voice cracks."},
            ],
            "WISDOM": [
                {"text": "Sleep, food, quiet. Recovery is a routine, not a mood.",
                 "reaction": "A routine. I can follow a routine."},
                {"text": "Notice what drained you. Rest works better when it targets the cause.",
                 "reaction": "Target the cause. Yes."},
            ],
            "AMICABILITY": [
                {"text": "Let the others carry the watch. That's what a group is for.",
                 "reaction": "Then I'll let them carry it tonight."},
                {"text": "Rest near us. You don't have to be alone to recover.",
                 "reaction": "Near you. That helps more than sleep."},
            ],
        },
    },
    {
        "theme": "loss_and_letting_go",
        "prompts": [
            "Something I cared about is gone. What do I do with the empty space?",
            "How do I say goodbye properly?",
            "Should I keep holding on to what I've lost?",
            "I failed people who counted on me. How do I carry that?",
            "When is it time to let a grudge go?",
            "What do I tell someone who is about to lose something?",
            "Is it okay to be sad for a long time?",
            "How do I remember without letting the memory weigh me down?",
        ],
        "lanes": {
            "PHILANTHROPY": [
                {"text": "Be sad as long as you need. I'll sit with you through it.",
                 "reaction": "Then I won't hurry my grief."},
                {"text": "Take the soft memories first. The hard ones can wait.",
                 "reaction": "Soft memories first. That's kind."},
            ],
            "VALOR": [
                {"text": "Face the loss. Running from it just makes it longer.",
                 "reaction": "Then I'll turn and face it."},
                {"text": "Carry what you must, then put it down when you can.",
                 "reaction": "Carry, then put down. Understood."},
            ],
            "WISDOM": [
                {"text": "Name what's gone, then name what's left.",
                 "reaction": "The left side of the list. I'll look there."},
                {"text": "A grudge is a thing you carry. Ask whether it's still useful.",
                 "reaction": "Useful or heavy. Good question."},
            ],
            "AMICABILITY": [
                {"text": "Tell the people still here what you lost. Memory shared is lighter.",
                 "reaction": "Then I'll tell you about it."},
                {"text": "Say goodbye together. It's easier than doing it alone.",
                 "reaction": "Together, then. Yes."},
            ],
        },
    },
    {
        "theme": "celebration_and_gratitude",
        "prompts": [
            "We did something good today. Should we celebrate or keep moving?",
            "How do I thank you properly for everything?",
            "I don't know how to enjoy things. Can you teach me?",
            "Other people cheer louder than me. Am I doing it wrong?",
            "What should we do to mark a milestone?",
            "How do I show gratitude without making it awkward?",
            "Is it selfish to be happy when others are still hurting?",
            "Should I celebrate small wins or save it for big ones?",
        ],
        "lanes": {
            "PHILANTHROPY": [
                {"text": "Celebrate by giving something back. Joy grows when it's shared.",
                 "reaction": "Then I'll share the day."},
                {"text": "Be kind to whoever helped you. Gratitude is a debt worth paying.",
                 "reaction": "Then I'll pay it properly."},
            ],
            "VALOR": [
                {"text": "Celebrate loud and honest. You earned it.",
                 "reaction": "Then I'll cheer with my whole chest."},
                {"text": "Use the win as fuel. A good day should carry you into the next.",
                 "reaction": "Fuel for the next road. Got it."},
            ],
            "WISDOM": [
                {"text": "Notice exactly what worked, and thank the reason.",
                 "reaction": "The reason, not just the result."},
                {"text": "Small and large wins both count. Marking them keeps you honest.",
                 "reaction": "Mark them all. Fine by me."},
            ],
            "AMICABILITY": [
                {"text": "Gather everyone who helped and say their names out loud.",
                 "reaction": "Names out loud. They'll like that."},
                {"text": "Make it about everyone, not just the result.",
                 "reaction": "Then the celebration belongs to all of us."},
            ],
        },
    },
]

# ---------------------------------------------------------------------------
# Negotiation — authored voice banks per personality.
# Tags: greeting, probe, demand, trick, insight, join, refuse, depart,
#       pay_leave, fight. Trick lines ALWAYS end with an authored tell in
#       parentheses (validator enforces this for tiers 2..4).
# ---------------------------------------------------------------------------
NEGOTIATION_VOICE = {
    1: {
        "greeting": ["Oh! You actually stopped. I was hoping someone would.",
                     "Come closer, if you like. I don't bite unless you give me a reason."],
        "probe": ["What are you looking for out here? Maybe I could help.",
                  "Do you always stop for strangers, or am I special?"],
        "demand": ["Could you spare a few bits? Just enough to show you mean it.",
                   "I don't ask for much. A small payment, and I'll trust your word."],
        "trick": ["One tiny extra for the trouble, please. (A smile that arrives a little too fast.)",
                  "Wait, the price was for my time, not my company. A little more? (The voice turns sweet and practiced.)"],
        "insight": ["You saw through that, didn't you? All right, the first price stands.",
                    "Hmph. You're paying attention. That makes trusting you easier."],
        "join": ["Really? Then I'll come along. Stay close, okay?",
                 "I was hoping you'd ask. I'll be good company, I promise."],
        "refuse": ["I don't think so. You're kind, but not yet careful with me.",
                   "Maybe another day. I want to be sure you mean it."],
        "depart": ["I'll go, then. Safe travels, truly.",
                   "Off I go. Don't forget me too quickly."],
        "pay_leave": ["You paid, and then you went. That's honest enough."],
        "fight": ["So that's how it is? Fine. I'll show you what I'm worth."],
    },
    2: {
        "greeting": ["Stop there. I don't talk to everyone who wanders past.",
                     "You look like someone who keeps promises. Prove me right."],
        "probe": ["What would you have me do, exactly? Say it plainly.",
                  "If I follow you, what do I owe and what do you owe me?"],
        "demand": ["A promise first, payment second: bits for the road ahead.",
                   "I work for those who pay honestly. Show me bits, then words."],
        "trick": ["The price changed. Call it a loyalty test. (The eyes narrow, measuring the reaction.)",
                  "One more payment, and my word is yours. (The promise is made too smoothly to be whole.)"],
        "insight": ["You caught it. Good. I'd rather serve someone who checks the small print.",
                    "You're careful. That's worth more than the extra bits."],
        "join": ["Then I'm with you. My word holds until you break yours.",
                 "Agreed. I'll follow your lead, and I'll tell you when it's wrong."],
        "refuse": ["No. A bad oath is worse than none.",
                   "Not yet. I need to see a promise kept before I make one."],
        "depart": ["I'm leaving. Keep your word to whoever comes next.",
                   "That's the end of it. I'll remember how this went."],
        "pay_leave": ["Payment taken. I'll consider the matter closed."],
        "fight": ["If that's the only way to test your word, so be it."],
    },
    3: {
        "greeting": ["No need to hurry. I'm not going anywhere.",
                     "Take your time. I'd rather wait than watch someone stumble."],
        "probe": ["What would make this worth doing for both of us?",
                  "You're holding something back. That's fine. Say what you can."],
        "demand": ["Some bits would settle the matter. Only what you can spare.",
                   "A small toll, and we can talk without counting every word."],
        "trick": ["Just a little more, it's only fair. (The voice stays calm, but the offer keeps changing.)",
                  "One more tiny addition, and then we're even. (A mild pause before the word even.)"],
        "insight": ["Ah. You were listening. Then the first price was the real one.",
                    "You noticed the shifting. Good, patience doesn't mean blindness."],
        "join": ["If you'll have me, I'll walk along. No rush.",
                 "Yes, let's try it. Company makes long roads shorter."],
        "refuse": ["Not this time. No harm done.",
                   "I'll pass. We can still part on good terms."],
        "depart": ["I'll head off now. Walk carefully.",
                   "Stay well. Perhaps our roads cross again."],
        "pay_leave": ["The toll is paid. Go in peace."],
        "fight": ["You'd rather fight than wait? Very well, but I won't enjoy it."],
    },
    4: {
        "greeting": ["You shouldn't be out here alone. Where's your guard?",
                     "Stand where I can see you. The roads aren't safe, whatever they look like."],
        "probe": ["What do you want here? And no, nothing isn't an answer.",
                  "Who else is with you? I don't like surprises behind me."],
        "demand": ["Payment first: bits for my protection. You'll want it.",
                   "A toll, and I'll tell you where the real dangers are."],
        "trick": ["That's not enough if you want me to watch your back. (The warning sharpens into a bargain.)",
                  "Pay a little more and I'll keep the dangerous ones away. (The eyes flick to a shadow and stay there too long.)"],
        "insight": ["You caught the pressure. Fine. The original toll was fair.",
                    "Good. Someone who notices danger should notice tricks too."],
        "join": ["Then stay close. I'll take the front and you take the rear.",
                 "All right. I'll travel with you, someone has to keep watch."],
        "refuse": ["No. I won't weaken my post for a stranger.",
                   "Not with you. I don't know what you'd lead us into."],
        "depart": ["I'm leaving. Watch your own back now.",
                   "Go. And keep out of the shadows."],
        "pay_leave": ["Paid. Now go before the road gets worse."],
        "fight": ["Threatening me? Then I'm done being polite."],
    },
    5: {
        "greeting": ["Ah, finally, someone with purpose in their stride!",
                     "Stop and hear me, Tamer. This meeting should mean something!"],
        "probe": ["What are you willing to do for what you want? Tell me plainly.",
                  "Do you believe in what you're chasing, or are you just walking?"],
        "demand": ["Show your conviction. Bits, and I'll believe you're serious!",
                   "A price, and it's the price of my faith in you."],
        "trick": ["One more offering proves the first wasn't a whim. (The voice rises too eagerly for a simple sale.)",
                  "Add to the price. Conviction is never measured once! (The argument arrives before the ask.)"],
        "insight": ["Hah! You heard the false note in that. Good.",
                    "You're not blinded by my energy. I respect that more than I show."],
        "join": ["Then we march together, and the world will hear us!",
                 "Yes! Let the others doubt, we'll prove them wrong."],
        "refuse": ["No. Your flame is too dim for me to follow.",
                   "Not yet. I need to see you burn for something."],
        "depart": ["I leave you to your lukewarm road. Sparks like mine aren't wasted here.",
                   "We're done. Find me again when you believe in something."],
        "pay_leave": ["The offering is taken. Now the matter is finished."],
        "fight": ["If words can't move you, perhaps a good clash can!"],
    },
    6: {
        "greeting": ["You stopped. Good. Most just walk past and hope I'm not the problem.",
                     "Stand where I can see you. Then we'll talk like two who aren't afraid."],
        "probe": ["What are you after out here? Say it straight.",
                  "I want to know if you'd stand your ground when it costs you."],
        "demand": ["Payment first: bits for my time. Then we talk about joining.",
                   "Nothing's free, not even courage. Pay the toll and I'll listen."],
        "trick": ["One more thing before we're square, a little extra for my trouble. (The grin is practiced rather than warm.)",
                  "The toll's gone up. That's just how the road works. (The eyes don't blink at the word just.)"],
        "insight": ["You noticed the price moved. Fine, straight talk earns straight answers.",
                    "You're awake. Good. Battle-hardened people usually are."],
        "join": ["All right. I'll walk with you a while. Don't make me regret it.",
                 "You've got spine enough for me. I'm in."],
        "refuse": ["No. Not today, and not for that price.",
                   "You're not ready to lead anyone yet. Come back when you are."],
        "depart": ["We're done. Don't follow.",
                   "I don't waste words on someone who won't listen. Goodbye."],
        "pay_leave": ["Take your bits and go. Consider that money spent on a lesson."],
        "fight": ["Then we settle it the old way. No hard feelings after."],
    },
    7: {
        "greeting": ["Well? Say something before I get bored and leave!",
                     "You look like trouble. I like trouble. What do you want?"],
        "probe": ["Cut to it, are we doing something or just standing here?",
                  "What's the fastest way this ends with me following you?"],
        "demand": ["Bits! Now! Then maybe I'll listen.",
                   "Pay up quick, I don't like long talks. Long talks. Ugh."],
        "trick": ["Extra bits, and we call it done. (The foot taps twice, twice more, and the grin is a little too wide.)",
                  "One more payment and I'm yours, hurry, hurry! (The words tumble out too fast to check.)"],
        "insight": ["Huh. You caught that. Nobody catches that.",
                    "You're sharper than you look. Fine, original price."],
        "join": ["Yes! Let's go before I change my mind!",
                 "Finally, something interesting. I'm in!"],
        "refuse": ["Ugh, no. Boring. I'm out.",
                   "Not a chance. You'd slow me down."],
        "depart": ["Gone! Don't wait up!",
                   "Bye. Try to be more interesting next time!"],
        "pay_leave": ["Paid and done. Off I go!"],
        "fight": ["Finally, something fun! Come on!"],
    },
    8: {
        "greeting": ["I took a bet with myself that you'd stop. I won.",
                     "What's the bravest thing you've done this week? Answer well."],
        "probe": ["What's the plan, and is it worth the risk?",
                  "I'll follow interesting odds. What are you offering?"],
        "demand": ["A stake first. Bits, and we both put something on the table.",
                   "Fair trade: my company for your bits. Simple odds."],
        "trick": ["There's a hidden fee for the adventure. (The smile says the fee is invented.)",
                  "One more stake, and the odds tilt our way. (The eyes glance away at the word odds.)"],
        "insight": ["Clever. You spotted the rigged odds.",
                    "Good eye. I'd rather travel with someone who checks the table."],
        "join": ["Deal. This should be fun.",
                 "I'm in. Let's see what we can get away with."],
        "refuse": ["No deal. The odds aren't worth it.",
                   "I'll pass. Bad bet."],
        "depart": ["I'll find my own adventure. Safe roads to you.",
                   "We're done. Try to keep up with your own plans."],
        "pay_leave": ["Stake paid. Walking away with my winnings, none this time."],
        "fight": ["Oh, a fight? That's a bet I like."],
    },
    9: {
        "greeting": ["You came with questions. I can see it in how you stand.",
                     "Sit if you like. The road will wait a moment."],
        "probe": ["What do you truly seek? The honest answer, not the polite one.",
                  "Before we speak, tell me what you already know."],
        "demand": ["A small offering of bits keeps the world turning. Then we speak.",
                   "Payment is a custom here. A few bits, and the custom is honored."],
        "trick": ["The custom asks a little more. (The words are calm, but the amount moved.)",
                  "One more offering, and understanding follows. (A pause where there should be none.)"],
        "insight": ["You noticed the change in the custom. Understanding returns to you.",
                    "You listened beneath the words. That is rare."],
        "join": ["Then we walk together, and I'll share what I see.",
                 "Yes. I would enjoy the journey beside you."],
        "refuse": ["Not now. The path is yours to walk alone a while.",
                   "I think not. Some lessons still belong to you alone."],
        "depart": ["I'll wander on. Safe roads, and a clear mind.",
                   "Farewell. Listen more than you speak."],
        "pay_leave": ["The offering is made. Go with a lighter heart."],
        "fight": ["If conflict is the lesson you seek, then let it be a short one."],
    },
    10: {
        "greeting": ["Well, well. Someone followed the shiny thing. Hello.",
                     "You came looking for answers. I might have a few. Or not."],
        "probe": ["Ask a better question. That one's too easy.",
                  "What's it worth to you to hear something true?"],
        "demand": ["A few bits for the privilege of my honesty. Funny, I know.",
                   "Payment first, honest words are expensive."],
        "trick": ["Oh, and a small finder's fee. (The smile is a little too clean around the edges.)",
                  "The price wiggled. Prices do that. (A shrug that arrives a beat early.)"],
        "insight": ["You're sharper than my good mood assumed. The first price, then.",
                    "Caught. You'd make a decent liar yourself."],
        "join": ["Fine, I'll come along. Try to keep up with the surprises.",
                 "All right, all right. I'm curious what comes next too."],
        "refuse": ["No thanks. The game's not worth the candle.",
                   "Pass. I like to keep my options open."],
        "depart": ["I'm off. Don't believe everything you remember about me.",
                   "Goodbye. Probably."],
        "pay_leave": ["Paid. I'll consider the debt imaginary."],
        "fight": ["Rude. But fine, I fight dirty."],
    },
    11: {
        "greeting": ["Your steps are uneven. You're worried about something. Sit.",
                     "You were about to walk past. Why did you change your mind?"],
        "probe": ["What exactly are you proposing? Be precise.",
                  "You've left something out. Say it, and we'll get somewhere."],
        "demand": ["The toll is bits. The exact amount matters; I dislike rounding.",
                   "A payment. I've already calculated what's reasonable."],
        "trick": ["The toll is higher. The difference is the cost of my attention. (The words are precise, and the arithmetic isn't.)",
                  "One more payment, and the numbers add up. (A small detail that doesn't.)"],
        "insight": ["I saw your eyes move to the amount. Good, the original figure stands.",
                    "You checked my arithmetic. Excellent."],
        "join": ["Agreed. Your reasoning is consistent. I'll come.",
                 "I'll follow you. The plan is sound enough to test."],
        "refuse": ["No. The proposal has a gap I won't overlook.",
                   "Not yet. One of your claims is still unverified."],
        "depart": ["Leaving. Review the conversation and you'll see what you missed.",
                   "We're done. Take care of the small things; they decide the big ones."],
        "pay_leave": ["Transaction closed. Be precise on the road."],
        "fight": ["You've miscalculated. But let's test it."],
    },
    12: {
        "greeting": ["Good. You stopped. That's the first correct move.",
                     "Let's establish what we both want before anyone postures."],
        "probe": ["What's your plan after me? If you don't have one, say so.",
                  "Where does this conversation fit in your larger road?"],
        "demand": ["A toll now, because later costs more. Bits, please.",
                   "Investment first: my price, then my cooperation."],
        "trick": ["Adjust the budget slightly. Call it risk forecasting. (A neat pause where a plan would be.)",
                  "The price has an added line item. (The explanation is ready before the question.)"],
        "insight": ["You saw the line item. The original budget stands.",
                    "You audit as you listen. Useful habit."],
        "join": ["Fine. Your plan survives contact with reality. I'm in.",
                 "Agreed. I'll follow the plan until the plan stops working."],
        "refuse": ["No. The plan has a hole I could fall through.",
                   "Pass. Return with a better plan."],
        "depart": ["I'm done. Rerun the plan; the flaw will show.",
                   "Goodbye. Plan for the worst while you hope for better."],
        "pay_leave": ["Budget closed. Walk carefully, direction matters."],
        "fight": ["So the plan was force. Fine, it's a plan."],
    },
    13: {
        "greeting": ["You look like the kind of person with somewhere to be. I can help.",
                     "Quick question: what's in it for both of us?"],
        "probe": ["What's your offer? Skip the speeches, they cost time.",
                  "How do I profit by following you for a week?"],
        "demand": ["Bits first, conversation second. Simple as that.",
                   "My time has a price. Pay it and we'll both profit."],
        "trick": ["One extra fee for the privilege of my foresight. (The greed shows for a heartbeat.)",
                  "I'm adding a handling charge. Standard practice. (The eyes are counting while the mouth talks.)"],
        "insight": ["Fine, you checked the bill. No handling charge.",
                    "No fooling you. That's a good quality in a partner."],
        "join": ["Deal. Let's go make something happen.",
                 "I'm in. As long as the odds stay good."],
        "refuse": ["Not worth it. Walk on.",
                   "No profit here. Pass."],
        "depart": ["Wasted enough time. I'm off.",
                   "Goodbye. If you find something valuable, I might turn up."],
        "pay_leave": ["Bits collected. I'll be around. Maybe."],
        "fight": ["Fine, but fights are a poor investment."],
    },
    14: {
        "greeting": ["Hey! You look like you could use a conversation. Me too.",
                     "Don't be shy. Come over where I can hear you."],
        "probe": ["So what are you up to? And no stories, just the truth.",
                  "What would make this a good day for both of us?"],
        "demand": ["A few bits for the road? No pressure, whatever you can spare.",
                   "Payment's the custom, so a small toll. Then we're friends."],
        "trick": ["Oh, and a little extra for the trouble, friends help friends. (The smile bends into a sales pitch.)",
                  "Just one more payment and we're square. (The friendly voice skips a beat.)"],
        "insight": ["Ha, you caught me pushing. First price, promise.",
                    "Sharp! I like people who pay attention."],
        "join": ["Great! Let's travel together. I know a good road.",
                 "I'm in! This is going to be fun."],
        "refuse": ["Aw, not this time. Maybe our paths cross again.",
                   "No hard feelings, I'll stay here."],
        "depart": ["See you around, Tamer. Stay warm.",
                   "Off I go. Take care of yourself, okay?"],
        "pay_leave": ["Thanks for the bits. Go carefully!"],
        "fight": ["I really didn't want this. But I won't just take it."],
    },
    15: {
        "greeting": ["Finally, someone who talks! What's the news from the road?",
                     "Come on, tell me something interesting before we get to business."],
        "probe": ["Who are you traveling with? And what are they like?",
                  "What's the story? There's always a story."],
        "demand": ["Let's handle the fee, then the stories. Bits, please.",
                   "I don't mind a toll, it's just part of the custom. A few bits?"],
        "trick": ["One more bit for the short version, another for the long one. (The rhythm of the bargain is too practiced.)",
                  "The price moved. It'll make a good story later. (A wink that's already been rehearsed.)"],
        "insight": ["Caught the plot twist! Fine, the original toll.",
                    "You're a good listener. That's rarer than a lucky strike."],
        "join": ["Yes! The more the better. Let's go!",
                 "I'm coming. You won't get bored, I promise."],
        "refuse": ["Nah. I'll stay here where the company's good.",
                   "Not today. But I'll keep the story."],
        "depart": ["I'll wander back. Tell everyone you saw me.",
                   "Goodbye! I'll talk about you, and only the good parts."],
        "pay_leave": ["Paid up. Now I've got a story and a few bits."],
        "fight": ["Oh, so we're doing the loud part? Alright."],
    },
    16: {
        "greeting": ["You look tired. Sit a moment before we talk.",
                     "There's kindness in your step. I hope I'm reading that right."],
        "probe": ["What's weighing on you? Not the business part, the real part.",
                  "Who else is waiting for you back home?"],
        "demand": ["A small toll of bits, only what you can spare. No need to overpay.",
                   "The custom asks for payment. Smaller is fine; the gesture matters."],
        "trick": ["A little extra, so my care doesn't go unrewarded. (The words lean on the word care a little too hard.)",
                  "One more payment, and I'll look after you properly. (A promise that arrives too early.)"],
        "insight": ["You noticed the sharp edge under the soft words. Good. The first toll.",
                    "You listen well. I'd trust you with a burden."],
        "join": ["Then I'll come along, and I'll help where I can.",
                 "Yes. You shouldn't carry all of this alone."],
        "refuse": ["I don't think I can follow you yet. I'm sorry.",
                   "Not this time. Take care of yourself on the road."],
        "depart": ["I'll go. Please look after yourself better than you do.",
                   "Farewell. Rest when your body tells you."],
        "pay_leave": ["The toll is paid. Rest at the next camp."],
        "fight": ["So be it, but I'll hold back what I can."],
    },
}

# Demand scales by tier; ranges never overlap between tiers (validator checks).
NEGOTIATION_DEMAND_RANGES = {1: (35, 75), 2: (90, 150), 3: (160, 245), 4: (250, 345)}
NEGOTIATION_EXTRA_PAYMENT = {2: 30, 3: 35, 4: 45}

# ---------------------------------------------------------------------------
# Persuasion (post-join recruitment arc) — authored banks per personality.
# One flow per (personality, stage 0..3); each flow has three authored choice
# nodes (start + two topics) and one terminal resolve node.
# ---------------------------------------------------------------------------
PERSUASION_VOICE = {
    1: {
        "open": ["You came back. I was starting to wonder if you would.",
                 "I kept a little space for you here. Sit with me?"],
        "topic": ["When you're away, I notice the quiet more than I expected.",
                  "Do you think about us when the road gets long?",
                  "I want to be someone you're glad to come back to."],
        "next": ["Then let's do this again soon. I'll count the days.",
                 "I'll try to be braver next time. For you."],
        "refuse": ["I don't think I'm ready for that yet. Please understand."],
        "depart": ["I'll say goodbye now. Thank you for the time we had."],
        "offer": ["There's something I want to ask you. Will you hear it?",
                  "I've been thinking about traveling with you. Properly, I mean."],
    },
    2: {
        "open": ["You returned. That's one promise kept.",
                 "I said I'd wait, and here I am."],
        "topic": ["Whatever you're building, I want to be part of it.",
                  "Tell me what loyalty means to you, and I'll match it.",
                  "A promise between us should be tested before it's trusted."],
        "next": ["Then I'll hold to my word. Again.",
                 "That's another step. I'm still with you."],
        "refuse": ["No. I won't promise something I can't keep."],
        "depart": ["Then this is where my road parts from yours. Keep your word."],
        "offer": ["I've kept my word. Now let me make a bigger one.",
                  "I want to travel with you, and see it through to the end."],
    },
    3: {
        "open": ["Back already? I don't mind. Sit a while.",
                 "No hurry. I'm glad to see you."],
        "topic": ["You don't have to agree with me to stay welcome here.",
                  "We can take the long way through this conversation.",
                  "What would you change about how we talk?"],
        "next": ["Then we'll keep talking. That's enough progress for today.",
                 "Good. Patience is starting to look like trust."],
        "refuse": ["Not yet. I'd rather wait than force it."],
        "depart": ["I'll leave you to your road. We part without anger."],
        "offer": ["Before you go, there's something worth asking.",
                  "I've been patient long enough to ask this plainly."],
    },
    4: {
        "open": ["You're back. Did anything follow you?",
                 "Let me look at you first. Then we talk."],
        "topic": ["You take too many risks. I'd like to hear your reasoning.",
                  "Let me watch the road while you explain the plan.",
                  "If something goes wrong, who covers whom?"],
        "next": ["Alright. You'll listen. That's half of what I needed.",
                 "We'll keep this arrangement, then. Carefully."],
        "refuse": ["No. I won't follow someone who ignores warnings."],
        "depart": ["I can't watch over you from here. I'm leaving."],
        "offer": ["There's a danger worth sharing. Hear me out.",
                  "I'll ask you something, but only because I trust your answer."],
    },
    5: {
        "open": ["You came back! I knew you would.",
                 "Good, I have more to say than time to say it."],
        "topic": ["What are we fighting for? I need to hear you say it.",
                  "Give me a cause worth burning for.",
                  "Do you believe this will matter when it's done?"],
        "next": ["Yes! That's what I needed to hear.",
                 "I'll follow that with everything I have."],
        "refuse": ["No. Your cause isn't worth my fire."],
        "depart": ["I'm leaving to find something worth the flame."],
        "offer": ["There's one thing I've wanted to ask since we met.",
                  "I'll go all the way with you. But ask me first."],
    },
    6: {
        "open": ["Back for another talk. Good.",
                 "You keep coming back. That says something."],
        "topic": ["What's the hardest thing you're carrying right now?",
                  "I want to know where you stand when it costs you.",
                  "If the road ends badly, what do you want me to do?"],
        "next": ["Then I'm still standing with you.",
                 "Good. I'll keep my word and my guard up."],
        "refuse": ["No. I won't follow a road I can't stand behind."],
        "depart": ["I'll walk this out alone. No hard feelings."],
        "offer": ["I've got one question left, and it matters.",
                  "I'll ask it plainly: are we partners?"],
    },
    7: {
        "open": ["Bored. Talk to me. Tell me something exciting.",
                 "You came back, did something fun chase you?"],
        "topic": ["What's the wildest thing you've ever done?",
                  "If we tried something stupid tomorrow, what would it be?",
                  "Why do you keep coming back to me?"],
        "next": ["Ha! Yes. Same time tomorrow?",
                 "Alright, alright, I'll stick around."],
        "refuse": ["Nah. Too boring. I'm out."],
        "depart": ["Gone. Try to make the next month interesting."],
        "offer": ["Okay, big question. Don't laugh.",
                  "I've been thinking. Dangerous, right? Ask me."],
    },
    8: {
        "open": ["There you are. I've got an idea.",
                 "Come on, let's plan something bold."],
        "topic": ["What's the biggest risk you've taken for someone else?",
                  "Convince me the next adventure is worth the fall.",
                  "What would you do if you weren't being careful?"],
        "next": ["Then the adventure continues. Good.",
                 "I'm in. Keep the odds interesting."],
        "refuse": ["No. Bad odds, bad idea.", "Pass. I'll find a better bet."],
        "depart": ["I'll go find my own adventure. Keep up, if you can."],
        "offer": ["Question for you, and it's a big one.",
                  "I've decided something. Want to hear it?"],
    },
    9: {
        "open": ["You've returned with a different look about you.",
                 "I wondered when I'd see you again."],
        "topic": ["What have you learned since we last spoke?",
                  "Which of your choices would you make again?",
                  "Do you understand why you came back?"],
        "next": ["Then the path continues. That's enough.",
                 "Good. You're listening to yourself more."],
        "refuse": ["No. You're still looking for someone else's answer."],
        "depart": ["Farewell. Walk carefully, and notice what returns to you."],
        "offer": ["There is a question I've kept for a quiet moment.",
                  "I have something to ask. Listen with both ears."],
    },
    10: {
        "open": ["You found me again. Interesting.",
                 "Back for another batch of half-truths?"],
        "topic": ["What's the truth you're avoiding?",
                  "Try lying to me. I'll tell you if it's any good.",
                  "What would you trade for one honest answer?"],
        "next": ["Hm. You're learning. Keep this up and I'll trust you.",
                 "Alright, one more round. This one was fun."],
        "refuse": ["No. You're boring when you're sincere."],
        "depart": ["I'll slip away now. You'll never quite be sure why."],
        "offer": ["Alright, an honest question. Don't get used to it.",
                  "I've got something to ask. No tricks this time."],
    },
    11: {
        "open": ["You're back. Your posture is different today.",
                 "I expected you yesterday. Why the delay?"],
        "topic": ["Name the detail you noticed but didn't mention.",
                  "What's the one thing about me you keep getting wrong?",
                  "Explain the choice you regret most."],
        "next": ["Good. That detail matches what I saw.",
                 "Then we understand each other a little better."],
        "refuse": ["No. You're still guessing where you should be looking."],
        "depart": ["Leaving. Watch the small details; they were always the tell."],
        "offer": ["One question. I already know the answer, but hear it from you.",
                  "I've found something worth asking precisely."],
    },
    12: {
        "open": ["You're back on schedule. Good.",
                 "Have you thought about the next step, or are we improvising?"],
        "topic": ["What's the plan if this goes wrong?",
                  "Which of our resources are we wasting?",
                  "Where do you see us in a month?"],
        "next": ["Good. Keep the plan flexible.",
                 "That's progress. Next decision point, then."],
        "refuse": ["No. The plan isn't ready."],
        "depart": ["I'm closing this line. Return with a better plan."],
        "offer": ["This is the decision point I mentioned.",
                  "I have a proposal. Listen to the terms before you answer."],
    },
    13: {
        "open": ["Look who's back. Did you find something valuable?",
                 "I knew you'd return. There's usually profit in it."],
        "topic": ["What's the best deal you've turned down?",
                  "Tell me how this partnership pays off for both of us.",
                  "What's the most useful thing you know about the road ahead?"],
        "next": ["Good. We both profit from this. Keep it going.",
                 "That's a fair trade. Let's continue."],
        "refuse": ["No. The return isn't worth the risk."],
        "depart": ["I'm moving on. Opportunities don't wait."],
        "offer": ["I've got an offer. It's a good one.",
                  "There's an opening here. Want to hear it?"],
    },
    14: {
        "open": ["Hey, you're back! How was the road?",
                 "I saved you a spot by the fire."],
        "topic": ["What's been on your mind lately?",
                  "Tell me something good that happened this week.",
                  "What should we try together next?"],
        "next": ["This is nice. Let's keep doing this.",
                 "Glad we talked. Same time next stop?"],
        "refuse": ["Not yet. I like how things are."],
        "depart": ["I'll head back. Come find me when you can."],
        "offer": ["Okay, I've been meaning to ask you something.",
                  "I've got a question. A friendly one."],
    },
    15: {
        "open": ["There you are! I have so much to tell you.",
                 "Come sit. Everyone's been talking and I need to catch up."],
        "topic": ["What's the best rumor you've heard this week?",
                  "Tell me about the people you've met.",
                  "What's the story behind that look you're giving me?"],
        "next": ["Great talk! Let's do it again soon.",
                 "Now I have more stories to work with."],
        "refuse": ["No, I'd rather keep it light for now."],
        "depart": ["I'll let you get back to it. I've got people to see."],
        "offer": ["Before you go, I've got news.",
                  "I saved a question for you. Ask me back."],
    },
    16: {
        "open": ["You're back. You look like you've been carrying something.",
                 "Sit. Let's talk properly."],
        "topic": ["Who are you worried about right now?",
                  "What's the burden you haven't set down?",
                  "Would you tell me if the road was hurting you?"],
        "next": ["Thank you for telling me. That was brave.",
                 "I'm here for that, too."],
        "refuse": ["Not yet. I'm not sure you'd listen to all of it."],
        "depart": ["I'll go. Take better care of yourself."],
        "offer": ["There's something I want to ask you, kindly.",
                  "I have a question. Take your time with it."],
    },
}

PERSUASION_STAGE_FLAVOR = {
    0: "(You are still learning how to read each other.)",
    1: "(This conversation starts easier than the first.)",
    2: "(They bring up the old question before you do.)",
    3: "(They have been waiting for this conversation.)",
}

PERSUASION_OPTIONS = {
    "engage": ["Let's talk about it. I've got time.",
               "Go on. I want to hear your side.",
               "All right. Start from the beginning."],
    "challenge": ["That sounds like an excuse. Prove it.",
                  "You keep saying that. Show me what it costs you.",
                  "I don't buy it yet. Try again."],
    "empathize": ["I hear you. That sounds heavy to carry alone.",
                  "That's fair. I'd feel the same in your place.",
                  "Thank you for telling me that straight."],
    "commit": ["Then let's make it a real promise.",
               "Deal. I'll hold up my end.",
               "I'm in, for as long as it takes."],
    "decline": ["Not this time. I need to think.",
                "I can't promise that yet.",
                "We're not there yet. Give it time."],
    "leave": ["I should get going.",
              "We'll talk again later.",
              "That's enough for today."],
}

# ---------------------------------------------------------------------------
# Event lines — 5 authored variants per required event (one shared + one per
# core), so every owned Digimon always has a matching line.
# ---------------------------------------------------------------------------
EVENT_ORDER = [
    "battle_win", "battle_loss", "injury", "heal", "evolution", "degeneration",
    "milestone", "quest_offer", "quest_complete", "item_found", "diary",
    "wild_depart", "recruit_join",
]

EVENT_LINES = {
    "battle_win": {
        "shared": [("That was a good fight. You kept your head.", 1)],
        "PHILANTHROPY": [("You checked on me before you cheered. That matters more than the win.", 2)],
        "VALOR": [("We won because you didn't flinch. I'll remember that.", 2)],
        "WISDOM": [("Good call on the opening. The battle turned there.", 1)],
        "AMICABILITY": [("We make a good team. I'm glad it was us.", 2)],
    },
    "battle_loss": {
        "shared": [("We lost. Let's get somewhere safe and think.", -1)],
        "PHILANTHROPY": [("You're hurt. Forget the fight and let me see.", -2)],
        "VALOR": [("We lost the round, not the war. Next time we go again.", -1)],
        "WISDOM": [("We lost the data, not the lesson. I know what went wrong.", -1)],
        "AMICABILITY": [("It's alright. We're still here, and that's enough for tonight.", 0)],
    },
    "injury": {
        "shared": [("I'm still standing. Barely.", -1)],
        "PHILANTHROPY": [("It hurts worse when you look worried. I'll be fine, really.", -1)],
        "VALOR": [("A scratch. Next time I'll block it.", -1)],
        "WISDOM": [("I'll heal slower than I'd like. Don't rush me.", -2)],
        "AMICABILITY": [("Don't fuss. I just need a quiet minute.", -1)],
    },
    "heal": {
        "shared": [("The pain's gone. That's a strange feeling.", 1)],
        "PHILANTHROPY": [("You stayed while I recovered. Thank you.", 2)],
        "VALOR": [("Good as new. Let's move before something else happens.", 1)],
        "WISDOM": [("Recovery is its own lesson. I'll remember how this felt.", 1)],
        "AMICABILITY": [("Feeling better already. Mostly because you're here.", 2)],
    },
    "evolution": {
        "shared": [("I changed. It feels bigger than I expected.", 2)],
        "PHILANTHROPY": [("I grew, and you were the first one I wanted to show.", 2)],
        "VALOR": [("Look at me now! Let's test this new strength.", 2)],
        "WISDOM": [("A new shape, a new set of limits. I'll learn them.", 1)],
        "AMICABILITY": [("I'm different, but I'm still me. You'll help me remember that?", 2)],
    },
    "degeneration": {
        "shared": [("I'm smaller again. It's strange, being back here.", -1)],
        "PHILANTHROPY": [("Don't look so sad. I'm still me, just fewer pieces.", -1)],
        "VALOR": [("One step back. I'll climb it again.", -1)],
        "WISDOM": [("Everything I learned is still here. The shape doesn't matter.", 0)],
        "AMICABILITY": [("I'll need help with small things for a while. Is that okay?", -1)],
    },
    "milestone": {
        "shared": [("We've come a long way. I can feel it.", 2)],
        "PHILANTHROPY": [("Look how far we've come together. I'm proud of us.", 2)],
        "VALOR": [("That's a mark worth making. Let's make another.", 2)],
        "WISDOM": [("A milestone is only a marker. The road continues.", 1)],
        "AMICABILITY": [("We should celebrate. Properly, with everyone.", 2)],
    },
    "quest_offer": {
        "shared": [("There's work worth doing, if you're willing.", 0)],
        "PHILANTHROPY": [("Someone needs help. That's reason enough, isn't it?", 1)],
        "VALOR": [("A challenge. Say the words and I'll be beside you.", 1)],
        "WISDOM": [("The details matter on this one. Let me read them again.", 0)],
        "AMICABILITY": [("If people are counting on us, we should go. Together.", 1)],
    },
    "quest_complete": {
        "shared": [("It's done. Let's take a moment before the next road.", 2)],
        "PHILANTHROPY": [("We helped someone today. I won't forget their face.", 2)],
        "VALOR": [("A clean finish. That's what I call a good fight.", 2)],
        "WISDOM": [("Every step worked. Good planning, Tamer.", 1)],
        "AMICABILITY": [("I'm proud of what we did. And I'm glad we did it as a team.", 2)],
    },
    "item_found": {
        "shared": [("Look at this. The road keeps giving.", 1)],
        "PHILANTHROPY": [("Someone must have lost this. Should we try to return it?", 1)],
        "VALOR": [("Useful. We'll put it to work.", 1)],
        "WISDOM": [("Worth more than it looks. I'd keep it close.", 0)],
        "AMICABILITY": [("Finders keepers, unless the others need it more.", 1)],
    },
    "diary": {
        "shared": [("I wrote today down so we don't forget it.", 1)],
        "PHILANTHROPY": [("I wrote about the kind thing you did. Just in case you forget.", 2)],
        "VALOR": [("I wrote about the fight. It was a good one.", 1)],
        "WISDOM": [("I wrote down what we learned. Future us will thank us.", 1)],
        "AMICABILITY": [("I wrote about the quiet parts. Those are my favorite.", 1)],
    },
    "wild_depart": {
        "shared": [("That wild one left. Some roads aren't meant to cross.", -1)],
        "PHILANTHROPY": [("They looked frightened when they left. I hope they're okay.", -1)],
        "VALOR": [("They made their choice. I won't chase it.", -1)],
        "WISDOM": [("We'll mark their trail and see if it loops back.", 0)],
        "AMICABILITY": [("It's quieter now. I don't like it.", -2)],
    },
    "recruit_join": {
        "shared": [("A new contact in the Wild tab. The circle grows.", 2)],
        "PHILANTHROPY": [("They trusted us. Let's be worthy of it.", 2)],
        "VALOR": [("A new ally! I like our odds better already.", 2)],
        "WISDOM": [("A useful friendship. Let's not waste it.", 1)],
        "AMICABILITY": [("The more, the warmer. Let's welcome them properly.", 2)],
    },
}

# ---------------------------------------------------------------------------
# Quiz phrasing (facts themselves always come from species_catalog.json).
# ---------------------------------------------------------------------------
QUIZ_LEVEL_PROMPTS = [
    "Which level is {name} listed at in the species catalog?",
    "The species catalog records which level for {name}?",
    "What level does the species catalog give for {name}?",
]
QUIZ_TYPE_PROMPTS = [
    "Which species type is {name} listed as in the species catalog?",
    "The species catalog records which species type for {name}?",
    "What species type does the species catalog give for {name}?",
]
QUIZ_LEVEL_EXPLANATIONS = [
    "The species catalog lists {name} as {a} {level}-level Digimon.",
    "According to the species catalog, {name} is {a} {level}-level Digimon.",
]
QUIZ_TYPE_EXPLANATIONS = [
    "The species catalog lists {name} as {a} {type}-type Digimon.",
    "According to the species catalog, {name} is {a} {type}-type Digimon.",
]

# ---------------------------------------------------------------------------
# Negotiation trees (contentVersion 3) — SMT-style authored dialogue.
#
# Each personality owns one authored tree; `build_packs.py` exposes a different
# slice of it per stage tier (T1 depth 2 ... T4 depth 4) and adds the required
# demand/trick/insight branch. Rules encoded in these banks:
#   * node texts are the wild Digimon's prompts ONLY (never player meta-lines);
#   * every answer carries the Digimon's authored `reaction` that echoes the
#     chosen answer, so the next prompt never arrives cold;
#   * `follow` branches split the opening answer into a warm path and a colder,
#     more guarded path, and each path continues the same topic;
#   * `escalate`/`escalate2` raise the stakes on the same topic before any price;
#   * `demand`/`trick`/`insight` answers are ordered in-world (pay/refuse/walk,
#     call-out/pay-extra/walk, settle/refuse/walk) and map to fork kinds read by
#     the generator; no line here is interface wording;
#   * terminal outcome lines reuse the authored NEGOTIATION_VOICE banks above;
#   * no line is copied from any source (SMT V, exports, wikis); all authored
#     for this project and checked by validate_packs.py.
# ---------------------------------------------------------------------------
NEGOTIATION_TREES: dict[int, dict] = {}

NEGOTIATION_TREES[1] = {
    "opening": {
        "text": "You stopped for me? No one stops for me. Why did you?",
        "answers": [
            {"text": "You looked like you needed someone to talk to.",
             "reaction": "Needed someone? I did, actually. (A small, surprised smile.)",
             "branch": "warm"},
            {"text": "I wanted to meet you properly, not just pass by.",
             "reaction": "Properly! I like the sound of properly. (It steps closer.)",
             "branch": "warm"},
            {"text": "Do not flatter yourself. I was just passing through.",
             "reaction": "Passing through... (The smile folds away.) Everyone passes through.",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "Then stay a little longer. Everyone leaves so quickly. What makes you different?",
            "answers": [
                {"text": "I keep small promises. That is what makes me different.",
                 "reaction": "Small promises are the ones I remember. (It nods carefully.)"},
                {"text": "I do not know yet. Give me time to show you.",
                 "reaction": "Time. I can give time. I have a lot of it."},
            ],
        },
        "cold": {
            "text": "Passing through people do not usually look back. Why are you still looking at me?",
            "answers": [
                {"text": "Because my road can wait a moment.",
                 "reaction": "You would wait for me? That is a dangerous thing to say."},
                {"text": "I am deciding whether the stop is worth it.",
                 "reaction": "Worth the stop... Let me try to be worth it."},
            ],
        },
    },
    "escalate": {
        "text": "Then tell me plainly what you are after. Companionship? A partner? Say it, even if it sounds foolish.",
        "answers": [
            {"text": "A partner. Someone I can rely on when the road turns.",
             "reaction": "Rely on. I want to be relied on so badly. (Its hands tighten.)"},
            {"text": "Company for a while. Nothing more.",
             "reaction": "Company for a while... I can live with a while."},
            {"text": "Neither. I came to take what I need.",
             "reaction": "Take? Then we should speak of price, not feelings. (The warmth goes cold.)",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "Partners share burdens, not just hours. What burden would you give me?",
        "answers": [
            {"text": "The long road. I do not want to walk it alone.",
             "reaction": "The long road... Then I will learn to keep pace."},
            {"text": "Whatever you can carry. I will not ask for more.",
             "reaction": "Then I will carry until you tell me to stop."},
        ],
    },
    "demand": {
        "text": "I will come with you, but I am not free. Pay me enough to believe I am wanted. (It holds out a hand.)",
        "answers": [
            {"text": "Here. Count it twice, because I want this to be fair.",
             "reaction": "Fair... You counted twice. That matters to me.",
             "kind": "pay"},
            {"text": "I do not buy company. It should be given, not sold.",
             "reaction": "Given... Nobody ever gives me anything.",
             "kind": "refuse"},
            {"text": "This is too much like a price. I am leaving.",
             "reaction": "Of course you are. They always leave. (It does not look up.)",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "One more thing, a small extra for my feelings. (A sad smile that knows exactly what it is doing.)",
        "answers": [
            {"text": "That smile is a trick, and I can see it.",
             "reaction": "Caught. You watched me, not just my words. (The smile turns honest.)",
             "kind": "call_out"},
            {"text": "Fine. Keep a little more, since the road is lonely.",
             "reaction": "Lonely... You paid for that word. That was not kind.",
             "kind": "pay_extra"},
            {"text": "Keep the bits. I would rather be alone than cheated.",
             "reaction": "Alone. That word is a door closing. I will open it for you.",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You have been watching me, not just my price. Why?",
        "answers": [
            {"text": "Because I think you are worth understanding.",
             "reaction": "Worth understanding. Say that again when we are not bargaining.",
             "kind": "settle"},
            {"text": "Do not read too much into it.",
             "reaction": "Then I will read nothing. It is safer.",
             "kind": "refuse"},
            {"text": "I have heard enough. I am going.",
             "reaction": "Going, then. I will keep a light on for the next one.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_TREES[2] = {
    "opening": {
        "text": "You approached without a weapon drawn. That is either trust or foolishness. Which is it?",
        "answers": [
            {"text": "Trust. I am offering it first.",
             "reaction": "Trust first? Then I will try not to make you regret it.",
             "branch": "warm"},
            {"text": "Foolishness. But a useful kind.",
             "reaction": "Useful foolishness. I know that flavor.",
             "branch": "warm"},
            {"text": "Neither. I want something from you.",
             "reaction": "Something you want from me? Then say it straight. I do not trade in hints.",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "Trust is a promise, and promises have weight. What do you promise me?",
            "answers": [
                {"text": "That I will tell you when you are wrong.",
                 "reaction": "Telling me when I am wrong is blunt honesty. I respect that."},
                {"text": "That I will not abandon you the first time it gets hard.",
                 "reaction": "The first time... That is when most people leave."},
            ],
        },
        "cold": {
            "text": "You want something, yet you will not name it. A poor beginning. Try again.",
            "answers": [
                {"text": "Fair. I want a partner with your reputation.",
                 "reaction": "Reputation... I would rather be wanted for my word than my name."},
                {"text": "I want your strength, not your friendship.",
                 "reaction": "Strength can be hired. My word cannot be."},
            ],
        },
    },
    "escalate": {
        "text": "Then hear my terms before words get expensive: I keep my oaths, and I expect mine kept. Do you swear the same?",
        "answers": [
            {"text": "I swear it. Test me and see.",
             "reaction": "Tested you will be. First you will keep a small oath."},
            {"text": "I will promise what I can keep. No more.",
             "reaction": "A kept promise with limits. Better than a loud vow."},
            {"text": "Words are cheap. Let us fight and settle it.",
             "reaction": "So a fight will settle cheap words? Then be it. I keep my oath to win.",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "One oath remains unspoken: if I fall, what becomes of the word I gave you?",
        "answers": [
            {"text": "I will carry it to the end of the road.",
             "reaction": "Then it will not die with me. That is enough."},
            {"text": "I will honor it until I cannot, and tell the truth either way.",
             "reaction": "Truth at the end. That is a hard promise to make."},
        ],
    },
    "demand": {
        "text": "My oath has a toll, and the toll is bits. Pay it, and I will take your road as mine.",
        "answers": [
            {"text": "Take it. I would rather earn your oath than bargain for it.",
             "reaction": "Earned or paid, an oath is an oath. Count it once more.",
             "kind": "pay"},
            {"text": "No. Oaths should not have a price on them.",
             "reaction": "No price? Then how would I know you value my word?",
             "kind": "refuse"},
            {"text": "Then keep your oath. I will find a partner who does not charge for loyalty.",
             "reaction": "Find one. I will be here, keeping mine without you.",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "Wait. Before you count that, add a little more. Call it the price of my silence. (The eyes flick, measuring whether you are watching.)",
        "answers": [
            {"text": "You are charging for silence now? I saw the flick.",
             "reaction": "The flick, you saw it? Good. Then the first price stands.",
             "kind": "call_out"},
            {"text": "Fine. Take it, and keep your silence.",
             "reaction": "Paid for silence. You will wonder what I did not say.",
             "kind": "pay_extra"},
            {"text": "No. I do not buy loyalty twice.",
             "reaction": "Then loyalty stays unbought. We part as clean as two liars can.",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You caught me measuring you. So tell me: what would make my oath worth keeping to you?",
        "answers": [
            {"text": "Keeping it when it costs you. That is all.",
             "reaction": "When it costs me. Then let me pay my first price.",
             "kind": "settle"},
            {"text": "I do not need to justify that to you.",
             "reaction": "No justification, then. You never understood vows.",
             "kind": "refuse"},
            {"text": "This has gone far enough. I am done.",
             "reaction": "Done. I will keep my word to the empty road.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_TREES[3] = {
    "opening": {
        "text": "No hurry. Tell me why you crossed the whole plain just to stand near me.",
        "answers": [
            {"text": "I wanted to understand what lives out here.",
             "reaction": "Understanding. A good reason to get dusty.",
             "branch": "warm"},
            {"text": "I wanted a talk with someone who does not attack first.",
             "reaction": "A talk before an attack? That is why I sit where the grass is tall.",
             "branch": "warm"},
            {"text": "I need something. Do not waste my time.",
             "reaction": "Something you need, said like that? Rushed words start fights, but I will hear it.",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "Then let us understand each other. What do you believe strongest when the day turns bad?",
            "answers": [
                {"text": "That people can change if they are given room.",
                 "reaction": "Room to change. I have given a lot of room."},
                {"text": "That patience outlasts anger.",
                 "reaction": "Patience outlasts. You have been listening to the wind."},
            ],
        },
        "cold": {
            "text": "Rushed words, sharp edges. What happened to make you carry them?",
            "answers": [
                {"text": "Nothing. I am just tired of games.",
                 "reaction": "Tired. That I understand."},
                {"text": "That is my business, not yours.",
                 "reaction": "Business, then. Keep it. But keep your voice low."},
            ],
        },
    },
    "escalate": {
        "text": "Before we go further: out here, what would you not do for what you want?",
        "answers": [
            {"text": "I would not hurt someone who trusted me.",
             "reaction": "You would not hurt someone who trusted you? Then your line is where I draw mine."},
            {"text": "I would do what the road requires, and regret it later.",
             "reaction": "Regret later is a companion, not a compass."},
            {"text": "Let us settle this with claws instead of words.",
             "reaction": "Claws, then. I will not enjoy it.",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "You have been patient longer than most. Is it patience, or are you waiting for an opening?",
        "answers": [
            {"text": "Patience. If I wanted an opening, I would have taken one.",
             "reaction": "Then patience it is. Mine matches yours."},
            {"text": "Both. But I would rather take the gentle road.",
             "reaction": "Both is honest. Honest is enough."},
        ],
    },
    "demand": {
        "text": "A talk this long earns a little. Bits, only what you can spare, and then we continue.",
        "answers": [
            {"text": "Here. What you can spare, freely given.",
             "reaction": "Freely given. That is the only way paying feels right.",
             "kind": "pay"},
            {"text": "I will keep my bits. They were not part of this.",
             "reaction": "Then the bits stay yours. The talk was still worth it.",
             "kind": "refuse"},
            {"text": "Enough. I am not paying for patience.",
             "reaction": "Go. Patience does not charge for leaving.",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "One more small thing, and I would not usually ask. (A long, calm blink that lasts a fraction too long.)",
        "answers": [
            {"text": "That blink was a lie settling in. What is the extra for?",
             "reaction": "Caught. Good. No extra, then.",
             "kind": "call_out"},
            {"text": "All right. One more, for the calm.",
             "reaction": "For the calm. Hm.",
             "kind": "pay_extra"},
            {"text": "No. That was one request too many.",
             "reaction": "A request too many? Then I will ask the wind instead.",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You watched my face the way I watch the grass. What did you learn?",
        "answers": [
            {"text": "That you are kinder than the price you named.",
             "reaction": "Kinder than my price. Then let the price be small.",
             "kind": "settle"},
            {"text": "That is for me to keep.",
             "reaction": "Keep it. Some lessons should stay private.",
             "kind": "refuse"},
            {"text": "Nothing worth the walk. I am leaving.",
             "reaction": "Leaving... Walk gently. The grass will not mind.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_TREES[4] = {
    "opening": {
        "text": "Stand there. You are alone out here, which is a bad idea. Why are you?",
        "answers": [
            {"text": "I can defend myself. I do not need a guard.",
             "reaction": "Defend yourself, can you? I will decide after I watch you.",
             "branch": "warm"},
            {"text": "I am looking for a companion who watches the road.",
             "reaction": "A companion who watches. Good. I watch everything.",
             "branch": "warm"},
            {"text": "That is not your concern.",
             "reaction": "Not my concern. Then I will keep my distance.",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "If I travel with you, I need to know your blind spots. Name one.",
            "answers": [
                {"text": "I do not watch behind me enough.",
                 "reaction": "You do not watch behind you, and behind is where the danger lives. Noted."},
                {"text": "I trust the wrong people too easily.",
                 "reaction": "Trust too easily. That is how I lose partners."},
            ],
        },
        "cold": {
            "text": "Keeping distance is what I do. Yet you are still standing there. Why?",
            "answers": [
                {"text": "Maybe I want to learn why you are so careful.",
                 "reaction": "Careful people are careful for reasons."},
                {"text": "Because standing far away is lonely.",
                 "reaction": "Lonely is safer than sorry."},
            ],
        },
    },
    "escalate": {
        "text": "Then answer this: if I am hurt, do you run or do you carry me?",
        "answers": [
            {"text": "I carry you. No questions asked.",
             "reaction": "No questions. I will try to believe that."},
            {"text": "I carry you, but afterward I will ask why we were careless.",
             "reaction": "Careless is the word I was waiting for. The asking can wait until we are safe."},
            {"text": "Let us find out in a fight. Come at me.",
             "reaction": "A fight to test me? Then keep your guard up.",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "One more thing. If I tell you a place is dangerous, will you listen?",
        "answers": [
            {"text": "Yes. Even if I disagree with you.",
             "reaction": "Even if you disagree. Then we can travel."},
            {"text": "I will listen, and then decide.",
             "reaction": "Decide carefully. That is half of listening."},
        ],
    },
    "demand": {
        "text": "Traveling costs. Bits for the road, and I will watch the roads you do not.",
        "answers": [
            {"text": "Take it. Watch the rear, and I will watch ahead.",
             "reaction": "Ahead and rear. That is a formation.",
             "kind": "pay"},
            {"text": "No. Protection is not something I buy.",
             "reaction": "No protection bought, then. Travel blind. I will be behind you.",
             "kind": "refuse"},
            {"text": "I will take my chances alone.",
             "reaction": "Alone is how people disappear. But it is your road.",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "And there is a small travel fee I forgot. (The eyes slide to the side, counting your purse.)",
        "answers": [
            {"text": "That was no fee. Your eyes counted my purse.",
             "reaction": "Counted it. Fine. No fee.",
             "kind": "call_out"},
            {"text": "Fine. Here. Do not forget it again.",
             "reaction": "I will not forget it again. Neither should you.",
             "kind": "pay_extra"},
            {"text": "Forget the fee and the road. I am done.",
             "reaction": "Then walk alone, without the fee or the road.",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You did not flinch when I looked at your purse. Why?",
        "answers": [
            {"text": "Because I would rather you count my bits than lose the road.",
             "reaction": "You would count bits before losing the road? Trust with money is rare.",
             "kind": "settle"},
            {"text": "That is mine to wonder about.",
             "reaction": "Then wonder in silence. I do not need to know everything.",
             "kind": "refuse"},
            {"text": "I should go. This was enough watching.",
             "reaction": "Watching enough, was it? Go. Keep your back to the wall.",
             "kind": "walk"},
        ],
    },
}

NEGOTIATION_TREES[5] = {
    "opening": {
        "text": "You crossed this place with purpose in your step. Tell me what burns in you!",
        "answers": [
            {"text": "I want to matter to someone before the road ends.",
             "reaction": "Matter! Yes! A cause worth the fire!",
             "branch": "warm"},
            {"text": "I want to be strong enough to protect my road.",
             "reaction": "Protect! Then your road already has a banner.",
             "branch": "warm"},
            {"text": "None of your business, loud one. Step aside.",
             "reaction": "None of my business?! Then mind your tongue.",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "A cause, then! How far would you go for it, past the edge?",
            "answers": [
                {"text": "Past the edge, if the cause is worth it.",
                 "reaction": "Past the edge! Now we speak the same language!"},
                {"text": "To the edge, but I will set a limit there.",
                 "reaction": "A limit. Limits are where resolve is tested."},
            ],
        },
        "cold": {
            "text": "Loud one, am I? Fine, speak plainly or not at all. Why are you here?",
            "answers": [
                {"text": "Because I heard the fire before I saw it.",
                 "reaction": "You heard the fire. Then you are not deaf after all."},
                {"text": "For strength, not for sermons.",
                 "reaction": "Strength without fire is a cold blade."},
            ],
        },
    },
    "escalate": {
        "text": "Then swear your cause to me. What will you burn for?",
        "answers": [
            {"text": "For the people who travel beside me.",
             "reaction": "People! That is a flame I can follow."},
            {"text": "For the truth, even when it costs me.",
             "reaction": "Truth at a cost. That is rare fuel."},
            {"text": "For nothing. I will fight you for the road.",
             "reaction": "Then let the road decide which fire wins!",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "One flame left to test: if your cause betrays you, who do you burn first?",
        "answers": [
            {"text": "No one. I look for the reason first.",
             "reaction": "Reason before rage. Slower, but it burns cleaner."},
            {"text": "I burn the betrayal, not the people.",
             "reaction": "Hah! You burn the betrayal. Sharp answer!"},
        ],
    },
    "demand": {
        "text": "Zeal is not free! Feed the fire: bits for the cause!",
        "answers": [
            {"text": "Take them, and let us burn this road together.",
             "reaction": "Burned together! Now we are an army!",
             "kind": "pay"},
            {"text": "No. Causes are not taxed.",
             "reaction": "A cause that will not pay will not last.",
             "kind": "refuse"},
            {"text": "I will find another fire.",
             "reaction": "There are fires everywhere. This one waits.",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "One more offering, friend, the fire always needs a little more! (The enthusiasm curves too sharp.)",
        "answers": [
            {"text": "That is not the fire asking. That is greed wearing its armor.",
             "reaction": "Greed wearing armor. Sharp eyes.",
             "kind": "call_out"},
            {"text": "Fine! Take more, then, and let the fire roar.",
             "reaction": "More! The fire roars!",
             "kind": "pay_extra"},
            {"text": "The fire is too hungry. I am leaving.",
             "reaction": "Then leave before it eats the road.",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You saw through the roar. Tell me: what is the true part of what I have said?",
        "answers": [
            {"text": "That you want a partner for the climb, not a payment.",
             "reaction": "Partner for the climb. Say it once more when we march.",
             "kind": "settle"},
            {"text": "I will not sort your truth from your fire.",
             "reaction": "Truth from fire is your problem, then. Stand aside. The march is mine.",
             "kind": "refuse"},
            {"text": "Goodbye, fire. March without me.",
             "reaction": "Goodbye, then. The fire remembers.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_TREES[6] = {
    "opening": {
        "text": "You stopped. Most walk past and hope I am not the problem. Why did you stop?",
        "answers": [
            {"text": "Because you looked like someone worth hearing out.",
             "reaction": "Worth hearing. I will try to earn that.",
             "branch": "warm"},
            {"text": "Because running from you felt worse than stopping.",
             "reaction": "Worse than what? That is a start.",
             "branch": "warm"},
            {"text": "Stop wasting time. I have questions.",
             "reaction": "Questions, then. Ask before I lose patience.",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "Then hear my question: when the fight turns bad, what do you do?",
            "answers": [
                {"text": "I hold the line until the others get clear.",
                 "reaction": "Hold the line. Good. That is the answer I hoped for."},
                {"text": "I find the way out that takes everyone home.",
                 "reaction": "Everyone home. That is strategy, not cowardice."},
            ],
        },
        "cold": {
            "text": "Questions without manners. Ask one, then. What do you want?",
            "answers": [
                {"text": "Truth. I want to know who you are.",
                 "reaction": "Then the truth is earned. Watch me and decide."},
                {"text": "Strength. Yours, to borrow for a while.",
                 "reaction": "Borrowed strength is still strength."},
            ],
        },
    },
    "escalate": {
        "text": "Then tell me what you would risk to earn my company, and do not lie about it.",
        "answers": [
            {"text": "I would risk the plan and stand with you.",
             "reaction": "The plan. That is the size of my risk too."},
            {"text": "I would risk everything except the people who trust me.",
             "reaction": "Everything except trust. Then we can talk."},
            {"text": "Risk? Let us find out in a real fight.",
             "reaction": "A real fight, then. Stand ready.",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "One last thing: if I break my word, what do you do?",
        "answers": [
            {"text": "Tell you plainly, and give you one chance.",
             "reaction": "One chance. Fair."},
            {"text": "Hold my own word anyway.",
             "reaction": "Hold your word anyway. That is a heavy answer."},
        ],
    },
    "demand": {
        "text": "Nothing is free, not even courage. Pay the toll, and I will listen past it.",
        "answers": [
            {"text": "Fine. Take the bits. Let us settle this.",
             "reaction": "Settled. Now we talk like two who are not afraid.",
             "kind": "pay"},
            {"text": "No deal. I will not buy your time.",
             "reaction": "Then do not. My time stays mine.",
             "kind": "refuse"},
            {"text": "We are done. I will find another partner.",
             "reaction": "Find one. The road is long.",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "One more thing before we are square, a little extra for my trouble. (The grin is practiced rather than warm.)",
        "answers": [
            {"text": "That grin arrived before the words. It was practiced.",
             "reaction": "Caught. A practiced grin is a cheap trick. The first price stands.",
             "kind": "call_out"},
            {"text": "Fine. One more, and we are square.",
             "reaction": "Square. Remember it.",
             "kind": "pay_extra"},
            {"text": "I do not pay twice. Goodbye.",
             "reaction": "Goodbye. It is cheaper to be brave elsewhere.",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You caught my grin. What convinced you?",
        "answers": [
            {"text": "It arrived before your words did.",
             "reaction": "The grin arrived first, you mean. Awake and studying. Good.",
             "kind": "settle"},
            {"text": "Instinct. Nothing more.",
             "reaction": "Instinct can be wrong. But keep it.",
             "kind": "refuse"},
            {"text": "No more tests. I am done.",
             "reaction": "Go. I will test the next one.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_TREES[7] = {
    "opening": {
        "text": "Well? Say something before I get bored and leave! Why are you here?",
        "answers": [
            {"text": "For the excitement. The road was too quiet.",
             "reaction": "Excitement! Finally, someone honest!",
             "branch": "warm"},
            {"text": "To see if the stories about you are true.",
             "reaction": "Stories! Then I will be the loudest one.",
             "branch": "warm"},
            {"text": "Because I need something fast.",
             "reaction": "Fast? Fine, hurry up.",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "Excitement! Then answer fast: what is the dumbest thing you have ever done?",
            "answers": [
                {"text": "Chased a rumor across a dead plain.",
                 "reaction": "Dead plain! I love it already."},
                {"text": "Trusted the wrong person and bolted.",
                 "reaction": "Bolted? That is a skill too."},
            ],
        },
        "cold": {
            "text": "Needing something fast is boring. Make it interesting, or I am gone.",
            "answers": [
                {"text": "Fine: I want a companion who moves fast.",
                 "reaction": "Fast! That is better."},
                {"text": "I need someone who will not slow me down.",
                 "reaction": "Slow? I have never been slow."},
            ],
        },
    },
    "escalate": {
        "text": "Then let us settle the important part: how much trouble do you want?",
        "answers": [
            {"text": "As much as the road offers.",
             "reaction": "The road offers plenty!"},
            {"text": "Enough to matter, not enough to lose everyone.",
             "reaction": "Matter, not lose. Ugh, smart."},
            {"text": "None. I will fight you instead.",
             "reaction": "A fight! Finally, something honest!",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "You are still here, which means either brave or bored. Which is it?",
        "answers": [
            {"text": "Bored. Let us make this happen.",
             "reaction": "Bored! Then let us move!"},
            {"text": "Brave. I will keep the speed down for you.",
             "reaction": "Speed down? All right, all right."},
        ],
    },
    "demand": {
        "text": "Bits! Pay up quick, then maybe I will listen!",
        "answers": [
            {"text": "Take them. Just keep moving.",
             "reaction": "Moving! Finally!",
             "kind": "pay"},
            {"text": "No. I am not paying for a conversation.",
             "reaction": "No paying? Fine, then no conversation.",
             "kind": "refuse"},
            {"text": "I am done. Boring.",
             "reaction": "Boring?! Then go be boring somewhere else!",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "Extra bits, and we call it done. (The foot taps twice, twice more, and the grin is a little too wide.)",
        "answers": [
            {"text": "The grin is too wide, and the foot taps. Was it a trick?",
             "reaction": "Caught the trick! Fine, original price.",
             "kind": "call_out"},
            {"text": "Fine! Take it, and stop tapping.",
             "reaction": "Tapping? I do not tap.",
             "kind": "pay_extra"},
            {"text": "No. I am leaving.",
             "reaction": "Leaving? Fine! Bye!",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You caught my trick. Nobody catches my trick. How?",
        "answers": [
            {"text": "You looked too happy about the extra.",
             "reaction": "Happy? That is just my face. But fine.",
             "kind": "settle"},
            {"text": "Sheer luck. Do not read into it.",
             "reaction": "Luck. Sure.",
             "kind": "refuse"},
            {"text": "This is taking too long. Done.",
             "reaction": "Done! Done! I am done too.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_TREES[8] = {
    "opening": {
        "text": "I took a bet with myself that you would stop. I won. Why did you?",
        "answers": [
            {"text": "Because the odds looked interesting.",
             "reaction": "Interesting odds! A kindred spirit.",
             "branch": "warm"},
            {"text": "Because I heard you are the best at what you do.",
             "reaction": "The best! Flattery earns a conversation.",
             "branch": "warm"},
            {"text": "To see what you are hiding.",
             "reaction": "Hiding! Straight to the point.",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "Then tell me: what is the biggest risk you have taken for someone else?",
            "answers": [
                {"text": "I bet a whole plan on a friend's word.",
                 "reaction": "A whole plan! Good odds."},
                {"text": "I went back for someone who slowed me down.",
                 "reaction": "Went back! That is a rare bet."},
            ],
        },
        "cold": {
            "text": "So you want what I am hiding. Ask directly, then.",
            "answers": [
                {"text": "What do you want in return for traveling with me?",
                 "reaction": "In return! See, that I understand."},
                {"text": "Your secrets. All of them.",
                 "reaction": "My secrets, all of them? A bold ask."},
            ],
        },
    },
    "escalate": {
        "text": "Then let us set the stakes. I will travel with the one who makes the braver choice. Which are you?",
        "answers": [
            {"text": "The one who finishes what the others start.",
             "reaction": "Finishes! Now that is a bet I like."},
            {"text": "The one who knows when to fold.",
             "reaction": "Fold with grace. That is rarer than courage."},
            {"text": "The one who takes your secrets by force.",
             "reaction": "Force! Then let us see!",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "Before we shake on it, what happens when the road stops being fun?",
        "answers": [
            {"text": "Then we find a better road.",
             "reaction": "Better road! Deal."},
            {"text": "Then we finish it anyway, bored or not.",
             "reaction": "Finish it. Hm. Reliable."},
        ],
    },
    "demand": {
        "text": "Deal or no deal. Bits first, a stake against a boring road.",
        "answers": [
            {"text": "Take it. Let me have a share of the fun.",
             "reaction": "A share of the fun? Cut in! Deal!",
             "kind": "pay"},
            {"text": "No. The road is not a game to me.",
             "reaction": "A shame. Games pay better.",
             "kind": "refuse"},
            {"text": "I will find my own adventure.",
             "reaction": "Adventure, then. May the odds favor you.",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "There is a hidden fee for the adventure. (The smile says the fee is invented.)",
        "answers": [
            {"text": "Invented fees make bad bets. What is the real price?",
             "reaction": "Real price! You spotted it.",
             "kind": "call_out"},
            {"text": "Fine. Take the fee, and let the adventure start.",
             "reaction": "Adventure fee paid! Let us go.",
             "kind": "pay_extra"},
            {"text": "Keep the fee. I keep my bits.",
             "reaction": "Both of us keep something. Fine.",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You spotted my rigged odds. That is not luck. What is it?",
        "answers": [
            {"text": "Experience. I have played this table before.",
             "reaction": "Played before! Then let us play honestly.",
             "kind": "settle"},
            {"text": "Does it matter? The deal is over.",
             "reaction": "Over? I suppose.",
             "kind": "refuse"},
            {"text": "No more table talk. Goodbye.",
             "reaction": "Goodbye, player.",
             "kind": "walk"},
        ],
    },
}

NEGOTIATION_TREES[9] = {
    "opening": {
        "text": "You came with questions. I can see it in how you stand. What is it you truly seek?",
        "answers": [
            {"text": "Understanding of the road ahead.",
             "reaction": "Understanding. Then walk slowly with me.",
             "branch": "warm"},
            {"text": "A companion who sees clearly.",
             "reaction": "Clearly seen. Such eyes are rare; mine water often.",
             "branch": "warm"},
            {"text": "I do not know yet. I only know I had to come.",
             "reaction": "Do not know? That is honest. Sit with it.",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "Then tell me: which of your beliefs would you leave behind first?",
            "answers": [
                {"text": "The ones that only made me feel safe.",
                 "reaction": "Feeling safe is a warm lie. You see it."},
                {"text": "None yet. I want to understand them first.",
                 "reaction": "Understand before leaving. That is patience."},
            ],
        },
        "cold": {
            "text": "Not knowing can be a mask. What are you carrying that you will not name?",
            "answers": [
                {"text": "A debt I do not know how to pay.",
                 "reaction": "A debt unnamed. That one follows closely."},
                {"text": "That is mine to carry unseen.",
                 "reaction": "Unseen burdens still bend the back."},
            ],
        },
    },
    "escalate": {
        "text": "Then let us look together. What do you value more: knowing, or not being wrong?",
        "answers": [
            {"text": "Knowing. Even when it hurts.",
             "reaction": "Knowing that hurts is the only kind that changes us."},
            {"text": "Not being wrong, but I am learning to let go.",
             "reaction": "Learning. That is the honest answer."},
            {"text": "Neither. I will take what I want by force.",
             "reaction": "Force is a loud teacher. Let us see what it teaches.",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "One question remains. When you understand a thing, what do you owe it?",
        "answers": [
            {"text": "I owe it truth, even when it is ugly.",
             "reaction": "Truth returning truth. That is a fair covenant."},
            {"text": "Nothing. Understanding does not bind me.",
             "reaction": "Then understanding may leave you."},
        ],
    },
    "demand": {
        "text": "A small offering keeps the world turning. Then we speak without counting words.",
        "answers": [
            {"text": "Here. An offering, not a fee.",
             "reaction": "Offering, not fee. The words will be better.",
             "kind": "pay"},
            {"text": "No offering. Understanding is not a toll road.",
             "reaction": "No offering, then. We speak as equals in the empty air.",
             "kind": "refuse"},
            {"text": "I will go now. The road calls.",
             "reaction": "The road always calls. Listen to it.",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "The custom asks a little more. (The words are calm, but the amount moved.)",
        "answers": [
            {"text": "The amount moved, though your voice did not. Why?",
             "reaction": "You heard the amount move. Then the custom returns to its first number.",
             "kind": "call_out"},
            {"text": "Fine. Keep the calm and take the extra.",
             "reaction": "Calm is cheap today.",
             "kind": "pay_extra"},
            {"text": "No more customs. I am leaving.",
             "reaction": "Customs end. Farewell.",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You felt the shift beneath my calm. What do you make of it?",
        "answers": [
            {"text": "That you want respect more than bits.",
             "reaction": "Respect. Yes. Let the offering reflect it.",
             "kind": "settle"},
            {"text": "I think you test people for sport.",
             "reaction": "Maybe. A test is how I learn.",
             "kind": "refuse"},
            {"text": "I have understood enough.",
             "reaction": "Enough is a fine place to stop.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_TREES[10] = {
    "opening": {
        "text": "Well, well. Someone followed the shiny thing. Hello. Why are you here?",
        "answers": [
            {"text": "Curiosity. The shiny thing worked.",
             "reaction": "Curiosity! A cheap trick, but it pays.",
             "branch": "warm"},
            {"text": "I like clever people. Let us talk.",
             "reaction": "Clever! Flattery and truth in the same breath.",
             "branch": "warm"},
            {"text": "I am here for answers. Drop the games.",
             "reaction": "Games dropped. How dull.",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "Curiosity likes a puzzle. Solve this: why would I talk to a stranger?",
            "answers": [
                {"text": "Because strangers say true things.",
                 "reaction": "True things! Not always."},
                {"text": "Because you are bored of the usual liars.",
                 "reaction": "Bored of liars? You have been watching."},
            ],
        },
        "cold": {
            "text": "Games dropped, answers demanded. Very well: what is the answer you want?",
            "answers": [
                {"text": "Who you are, and what you charge for honesty.",
                 "reaction": "Honesty about who I am? That changes with the wind."},
                {"text": "The truth, cheaply.",
                 "reaction": "Cheap truth is the most expensive kind."},
            ],
        },
    },
    "escalate": {
        "text": "Then let us play one honest round. What do you pretend not to know?",
        "answers": [
            {"text": "That I am afraid of being alone.",
             "reaction": "Alone! A good answer, a costly one."},
            {"text": "Nothing. I am a poor liar.",
             "reaction": "Poor liars are my favorite audience."},
            {"text": "Enough. Give me what I came for.",
             "reaction": "Enough? Give? How refreshingly rude.",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "One more round, honest. What would you trade for a true answer?",
        "answers": [
            {"text": "A secret of my own.",
             "reaction": "A secret for a secret. The oldest trade."},
            {"text": "Nothing. Truth should not be traded.",
             "reaction": "Then you get the version that is not traded."},
        ],
    },
    "demand": {
        "text": "A few bits for the privilege of my honesty. Funny, I know.",
        "answers": [
            {"text": "Take them. Now be honest about the price.",
             "reaction": "Honest about the price! That is new.",
             "kind": "pay"},
            {"text": "No. Honesty should not need a toll.",
             "reaction": "No toll? Then nobody is honest.",
             "kind": "refuse"},
            {"text": "Keep your honesty. I am going.",
             "reaction": "Going! Everyone is going, eventually.",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "Oh, and a small finder's fee. (The smile is a little too clean around the edges.)",
        "answers": [
            {"text": "Too clean a smile. The fee is invented, is it not?",
             "reaction": "Invented! You would make a decent liar.",
             "kind": "call_out"},
            {"text": "Fine. Take the finder's fee.",
             "reaction": "Finder's fee paid! The honest version of a lie.",
             "kind": "pay_extra"},
            {"text": "No more fees. Goodbye.",
             "reaction": "Goodbye, probably.",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You caught the clean smile. What gave it away?",
        "answers": [
            {"text": "It was too perfect for a real one.",
             "reaction": "Too perfect. Then here is a rough one, free.",
             "kind": "settle"},
            {"text": "I do not share my tricks.",
             "reaction": "A magician keeps tricks. Fair.",
             "kind": "refuse"},
            {"text": "No more tricks. Done.",
             "reaction": "Done! The show closes.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_TREES[11] = {
    "opening": {
        "text": "Your steps are uneven. You are worried. Say why you are here, and do not round it off.",
        "answers": [
            {"text": "Because I need a companion with sharp eyes.",
             "reaction": "Sharp eyes. You checked mine before you spoke.",
             "branch": "warm"},
            {"text": "Because I saw you watching the road, and I need that.",
             "reaction": "Watching the road. You noticed.",
             "branch": "warm"},
            {"text": "Because someone told me not to trust you.",
             "reaction": "Told you... And you came anyway. Interesting.",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "Then note this: I count details. What detail of me did you notice first?",
            "answers": [
                {"text": "The worn clasp on your left side.",
                 "reaction": "Left side, worn clasp. One detail, correctly."},
                {"text": "That you keep your hands where they can move.",
                 "reaction": "Ready hands. You read posture."},
            ],
        },
        "cold": {
            "text": "You came despite a warning. Bad instincts or good information?",
            "answers": [
                {"text": "Bad instincts, honestly.",
                 "reaction": "Honest. That is information too."},
                {"text": "Good information: the one who warned me is a liar.",
                 "reaction": "Ah. Then the details about that liar line up."},
            ],
        },
    },
    "escalate": {
        "text": "Precisely, then. Tell me the weakest thing about your own plan.",
        "answers": [
            {"text": "I have no fallback if you refuse.",
             "reaction": "No fallback. Accurate and costly."},
            {"text": "I trust my allies too quickly.",
             "reaction": "Too quickly. I have seen that flaw."},
            {"text": "None. I will take what I want by force.",
             "reaction": "Force. Then let me judge your plan in the fight.",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "One detail remains. What did you leave out of everything you have told me?",
        "answers": [
            {"text": "That I need this to work more than I show.",
             "reaction": "Need showing. I appreciate the admission."},
            {"text": "Nothing. I do not leave things out.",
             "reaction": "Nothing? Everyone leaves something out."},
        ],
    },
    "demand": {
        "text": "The toll is bits, exact. I dislike rounding, and I dislike pretending it is not a price.",
        "answers": [
            {"text": "Here. Now walk me through your reasoning.",
             "reaction": "You paid and asked for reasoning. Unusual.",
             "kind": "pay"},
            {"text": "No. And I dislike pretending this is a bargain.",
             "reaction": "Then we share a dislike.",
             "kind": "refuse"},
            {"text": "Enough. I will find sharper eyes.",
             "reaction": "Sharper eyes rarely come cheaper.",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "The toll is higher. The difference is the cost of my attention. (The words are precise; the arithmetic is not.)",
        "answers": [
            {"text": "Your arithmetic does not add up. What is the missing number?",
             "reaction": "Missing number spotted. Very good.",
             "kind": "call_out"},
            {"text": "Fine. Take it. The arithmetic can lie too.",
             "reaction": "The arithmetic lying, and you paid anyway. Noticed the lie, at least.",
             "kind": "pay_extra"},
            {"text": "No. I will not pay for bad math.",
             "reaction": "Then the math stays ours.",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You checked my arithmetic. Tell me exactly where it failed.",
        "answers": [
            {"text": "The difference should be half what you named.",
             "reaction": "Half? So it should. Here is my real price.",
             "kind": "settle"},
            {"text": "I do not need to teach you.",
             "reaction": "No. Then I will not teach it either.",
             "kind": "refuse"},
            {"text": "The lesson is over. Goodbye.",
             "reaction": "Goodbye. Count your bits.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_TREES[12] = {
    "opening": {
        "text": "Good. You stopped. That is the first correct move. State your objective in one sentence.",
        "answers": [
            {"text": "To find a partner who plans as far as I do.",
             "reaction": "A partner who plans as far as you? Ambitious.",
             "branch": "warm"},
            {"text": "To learn whether this road has a future.",
             "reaction": "A future. You think in horizons.",
             "branch": "warm"},
            {"text": "To get through this talk quickly.",
             "reaction": "Quickly. Every shortcut has a cost.",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "Plans need constraints. What is the one thing you cannot sacrifice?",
            "answers": [
                {"text": "The people traveling with me.",
                 "reaction": "People as a constraint. Noted."},
                {"text": "Our supply of bits.",
                 "reaction": "Bits as a constraint. Cautious."},
            ],
        },
        "cold": {
            "text": "Quickly. Then here is the fast question: what do you offer me?",
            "answers": [
                {"text": "A plan that does not waste your time.",
                 "reaction": "A plan. I will test it."},
                {"text": "Nothing yet. That is why we talk.",
                 "reaction": "Nothing yet. Honest."},
            ],
        },
    },
    "escalate": {
        "text": "Then let us price the risk. What would you give to guarantee this works?",
        "answers": [
            {"text": "A part of the plan I care about.",
             "reaction": "Giving up what you care for. That is real cost."},
            {"text": "Nothing guaranteed. I would rather adapt.",
             "reaction": "Adapt. Good answer for a bad plan."},
            {"text": "Force. Force guarantees most things.",
             "reaction": "Most things. Not this. Let us test it.",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "One variable left. If I fail you, what is your contingency?",
        "answers": [
            {"text": "I carry the plan without you.",
             "reaction": "Carrying the plan alone, then. Adequate."},
            {"text": "I question why, then decide.",
             "reaction": "Questioning first. Slower, better."},
        ],
    },
    "demand": {
        "text": "Investment first: my price, then my cooperation. Bits, calculated.",
        "answers": [
            {"text": "Here. Show me the calculation after.",
             "reaction": "After. Yes, you will want the calculation.",
             "kind": "pay"},
            {"text": "No. Cooperation is not an investment.",
             "reaction": "Then cooperation stays a cost, and you are declining.",
             "kind": "refuse"},
            {"text": "I will find another collaborator.",
             "reaction": "Find one. Bring a better plan.",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "Adjust the budget slightly. Call it risk forecasting. (A neat pause where a plan would be.)",
        "answers": [
            {"text": "That pause was a hole in the plan. What is the forecast?",
             "reaction": "Hole spotted. The forecast was greed.",
             "kind": "call_out"},
            {"text": "Fine. Adjust the budget. Show me the line item.",
             "reaction": "Line item approved.",
             "kind": "pay_extra"},
            {"text": "I do not fund gaps. Goodbye.",
             "reaction": "Gaps find their own funding.",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You saw the gap. What is the correct line item now?",
        "answers": [
            {"text": "My price, honestly stated. Nothing extra.",
             "reaction": "Honestly stated. The plan survives.",
             "kind": "settle"},
            {"text": "I will keep my line items to myself.",
             "reaction": "Then the line stays mine.",
             "kind": "refuse"},
            {"text": "The plan ends here.",
             "reaction": "It was a short plan.",
             "kind": "walk"},
        ],
    },
}

NEGOTIATION_TREES[13] = {
    "opening": {
        "text": "You look like someone with somewhere to be. I can help. What is in it for me?",
        "answers": [
            {"text": "A fair share of whatever we find.",
             "reaction": "Fair share! Now that is a language I speak.",
             "branch": "warm"},
            {"text": "A useful ally who does not waste your time.",
             "reaction": "Useful. Better than friendly.",
             "branch": "warm"},
            {"text": "Nothing. I am just passing through.",
             "reaction": "Nothing? Then why stop?",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "A fair share. Then tell me: what is the best deal you have ever turned down?",
            "answers": [
                {"text": "One that would have cost me my allies.",
                 "reaction": "Allies as an asset. Clever."},
                {"text": "One that was too good. Those always hide something.",
                 "reaction": "Too good to be true, and hiding something? You have been burned."},
            ],
        },
        "cold": {
            "text": "Passing through people do not stop for nothing. What do you actually want?",
            "answers": [
                {"text": "A partner who knows where things are.",
                 "reaction": "Where things are. Practical."},
                {"text": "A share of your profit.",
                 "reaction": "My profit! At least you are direct."},
            ],
        },
    },
    "escalate": {
        "text": "Then let us talk profit. What are you willing to give up for a bigger cut?",
        "answers": [
            {"text": "The easy road. I will take the harder one for more.",
             "reaction": "Harder road, better margin."},
            {"text": "Nothing. I like the cut I have.",
             "reaction": "Nothing? Then the cut stays small."},
            {"text": "I will take the cut by force.",
             "reaction": "Force! But force has overhead.",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "One last term: when the profit dries up, what do you do?",
        "answers": [
            {"text": "Find the next road and bring my allies.",
             "reaction": "Bring allies. Loyalty with a return."},
            {"text": "Leave quietly and cut losses.",
             "reaction": "Quietly, then. Quiet exits are underrated."},
        ],
    },
    "demand": {
        "text": "Bits first, conversation second. My time has a price; pay it and we both profit.",
        "answers": [
            {"text": "Here. Profitable for both of us, I hope.",
             "reaction": "Hope is not a contract. But it is a start.",
             "kind": "pay"},
            {"text": "No. I will keep my bits and find my own road.",
             "reaction": "Then the road does not split the profit.",
             "kind": "refuse"},
            {"text": "This is a waste of time. I am off.",
             "reaction": "Time is money. Go spend it.",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "One extra fee for the privilege of my foresight. (The greed shows for a heartbeat.)",
        "answers": [
            {"text": "The greed showed. What is the honest fee?",
             "reaction": "Honest fee! You are learning my trade.",
             "kind": "call_out"},
            {"text": "Fine. Take the foresight fee.",
             "reaction": "Paid! I foresee a short alliance.",
             "kind": "pay_extra"},
            {"text": "No more fees. Goodbye.",
             "reaction": "Goodbye. If you find value, I will turn up.",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You caught the greed. So what is my honest price, then?",
        "answers": [
            {"text": "A fair share, stated up front.",
             "reaction": "Up front and fair. Deal.",
             "kind": "settle"},
            {"text": "I do not bargain with your greed.",
             "reaction": "Then my greed and I will manage.",
             "kind": "refuse"},
            {"text": "The deal is off.",
             "reaction": "The deal is off, then. Fine.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_TREES[14] = {
    "opening": {
        "text": "Hey! You look like you could use a conversation. Me too. What brings you here?",
        "answers": [
            {"text": "Company, honestly. The road is quiet.",
             "reaction": "Company! Then sit down, friend.",
             "branch": "warm"},
            {"text": "Curiosity. You looked interesting.",
             "reaction": "Interesting! I like that.",
             "branch": "warm"},
            {"text": "Just passing. Do not make it a thing.",
             "reaction": "A thing? Okay, okay.",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "Then tell me something true: what makes a good day for you?",
            "answers": [
                {"text": "One where nobody is in a hurry.",
                 "reaction": "Nobody in a hurry! Yes!"},
                {"text": "One where I learn something new.",
                 "reaction": "Something new! What did you learn today?"},
            ],
        },
        "cold": {
            "text": "Passing through can still be friendly! What is your favorite road?",
            "answers": [
                {"text": "The one with good company.",
                 "reaction": "Good company! Then you found some."},
                {"text": "The shortest one.",
                 "reaction": "Shortest... efficient and boring."},
            ],
        },
    },
    "escalate": {
        "text": "Then let us make this easy: what kind of partner do you actually want?",
        "answers": [
            {"text": "Someone who makes the quiet parts less quiet.",
             "reaction": "Quiet parts! I am good at those."},
            {"text": "Someone who works as hard as I do.",
             "reaction": "Work! I can work."},
            {"text": "Someone who stays out of my path.",
             "reaction": "Out of your path? That is not friendly. Let us fight, then.",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "Okay, real question: when the road gets boring, what do we do?",
        "answers": [
            {"text": "Tell stories until it is not boring.",
             "reaction": "Stories! I will start!"},
            {"text": "Walk faster.",
             "reaction": "Faster! Right, okay."},
        ],
    },
    "demand": {
        "text": "Friendship is free, but the road costs. A few bits, whatever you can spare?",
        "answers": [
            {"text": "Here. Take it and tell me the next story.",
             "reaction": "Story and bits! Best deal I have had.",
             "kind": "pay"},
            {"text": "No. I would rather owe you a story than bits.",
             "reaction": "A story owed! Even better.",
             "kind": "refuse"},
            {"text": "No hard feelings, but I should go.",
             "reaction": "No hard feelings! Come back later.",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "Oh, and a little extra for the trouble, friends help friends. (The smile bends into a sales pitch.)",
        "answers": [
            {"text": "That is a sales pitch, not friendship. What is the real ask?",
             "reaction": "Caught the sales pitch! First price, promise.",
             "kind": "call_out"},
            {"text": "Fine. Friends help friends. Here is more.",
             "reaction": "Thanks! I will help you more.",
             "kind": "pay_extra"},
            {"text": "I do not like where this is going.",
             "reaction": "Where is it going? Okay, okay, come back!",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You caught my pitch. So tell me, what do you actually want from me?",
        "answers": [
            {"text": "A friend on the road. That is all.",
             "reaction": "A friend! That I can do.",
             "kind": "settle"},
            {"text": "I am not sure yet.",
             "reaction": "Not sure? Take your time.",
             "kind": "refuse"},
            {"text": "I should go find the others.",
             "reaction": "Find the others, then. They will be happy to see you.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_TREES[15] = {
    "opening": {
        "text": "Finally, someone who talks! What is the news from the road?",
        "answers": [
            {"text": "The biggest news is you. What do you know?",
             "reaction": "Flattering! I know lots.",
             "branch": "warm"},
            {"text": "The road is long and the company is scarce.",
             "reaction": "Scarce company! Then I am valuable.",
             "branch": "warm"},
            {"text": "I did not come to gossip. What is your price?",
             "reaction": "Straight to price! No fun.",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "Hah! Then share one story before I share mine.",
            "answers": [
                {"text": "I once crossed a storm to reach a friend.",
                 "reaction": "Crossed a storm for a friend? Great story!"},
                {"text": "I have no stories. Only plans.",
                 "reaction": "Plans? Boring, but useful."},
            ],
        },
        "cold": {
            "text": "No fun, then. What is the price of my time, do you think?",
            "answers": [
                {"text": "Tell me a story and I will tell you mine.",
                 "reaction": "A story for a story! Now we are talking."},
                {"text": "Whatever gets me the answer fastest.",
                 "reaction": "Fastest! That is a story all its own."},
            ],
        },
    },
    "escalate": {
        "text": "Then let us settle it like this: what is the most interesting thing about you?",
        "answers": [
            {"text": "I listen more than I talk.",
             "reaction": "You listen more than you talk? A listener! Rare."},
            {"text": "I keep promises nobody expects me to keep.",
             "reaction": "Unexpected promises! Tell me one!"},
            {"text": "Nothing. Now stop wasting time.",
             "reaction": "Wasting time? You are at the wrong table. Let us fight.",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "Before we trade tall tales: who is waiting for you back home?",
        "answers": [
            {"text": "Friends who will want the story of this.",
             "reaction": "They will want the whole story!"},
            {"text": "No one. That is why I am here.",
             "reaction": "Here with no one waiting? Then you need a story louder than the quiet."},
        ],
    },
    "demand": {
        "text": "Let us handle the fee, then the stories. Bits, please.",
        "answers": [
            {"text": "Here. Now tell me the long version.",
             "reaction": "The long version! Grab a seat.",
             "kind": "pay"},
            {"text": "No fee. Stories should be free.",
             "reaction": "Free! Then you will get the short one.",
             "kind": "refuse"},
            {"text": "I will find better company.",
             "reaction": "Better company? Nobody tells it better!",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "One more bit for the short version, another for the long one. (The rhythm of the bargain is too practiced.)",
        "answers": [
            {"text": "That rhythm is practiced. Is the story even real?",
             "reaction": "Caught the rhythm! Fine, one price.",
             "kind": "call_out"},
            {"text": "Fine. Take both prices. Tell it well.",
             "reaction": "Both prices! It is a great story.",
             "kind": "pay_extra"},
            {"text": "I do not pay per version.",
             "reaction": "No pay per version? Then someone else hears it!",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You caught my rhythm. What gave it away?",
        "answers": [
            {"text": "The practiced part. Real stories stumble.",
             "reaction": "Practiced, was it? Then here is one that is not.",
             "kind": "settle"},
            {"text": "I will not teach you your trade.",
             "reaction": "Fair. I will not trade my tricks.",
             "kind": "refuse"},
            {"text": "I am off. Good luck with the audience.",
             "reaction": "Off you go. I will find another audience.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_TREES[16] = {
    "opening": {
        "text": "You look tired. Sit a moment before we talk. What is weighing on you?",
        "answers": [
            {"text": "The road has been long and I have been carrying too much.",
             "reaction": "Too much carried alone. That is a heavy way to travel.",
             "branch": "warm"},
            {"text": "I am worried about the people waiting for me.",
             "reaction": "Waiting people. You think of them first.",
             "branch": "warm"},
            {"text": "Nothing. I do not need your pity.",
             "reaction": "Not pity. Just a seat.",
             "branch": "cold"},
        ],
    },
    "follow": {
        "warm": {
            "text": "Then set it down here, just for a moment. What would you set down first?",
            "answers": [
                {"text": "The fear that I will fail them.",
                 "reaction": "Fear of failing. That one rarely sets itself down."},
                {"text": "The need to do everything myself.",
                 "reaction": "Everything yourself... That one is heavier than it looks."},
            ],
        },
        "cold": {
            "text": "No pity, then. But you are still here. What do you need, plainly?",
            "answers": [
                {"text": "Honestly? A moment that is not hard.",
                 "reaction": "A soft moment. Those are allowed."},
                {"text": "Nothing from you. Only silence.",
                 "reaction": "Silence I can give."},
            ],
        },
    },
    "escalate": {
        "text": "Then answer me this: if I asked you to carry my grief, would you?",
        "answers": [
            {"text": "Yes. I would carry it until it is lighter.",
             "reaction": "Until it is lighter... That is a kind answer."},
            {"text": "I would sit with it, but carry my own first.",
             "reaction": "Sitting with it is more than most offer. Carry your own first."},
            {"text": "No. I have enough grief of my own.",
             "reaction": "Enough of your own. Then do not take mine. Let us fight.",
             "kind": "fight"},
        ],
    },
    "escalate2": {
        "text": "One more: when you help someone and they do not thank you, what do you do?",
        "answers": [
            {"text": "Help them again. Thanks was never the point.",
             "reaction": "Thanks never the point. That is the whole lesson."},
            {"text": "Notice it, then help anyway.",
             "reaction": "Notice and help anyway. Honest."},
        ],
    },
    "demand": {
        "text": "The custom asks payment, but smaller is fine. The gesture matters more.",
        "answers": [
            {"text": "Here. It is what I can spare, gladly.",
             "reaction": "Gladly given. That is worth more.",
             "kind": "pay"},
            {"text": "No. Kindness should not be a custom.",
             "reaction": "No custom, then. Let it be a gift instead.",
             "kind": "refuse"},
            {"text": "I cannot stay. I am sorry.",
             "reaction": "Do not be sorry. Rest when you can.",
             "kind": "walk"},
        ],
    },
    "trick": {
        "text": "A little extra, so my care does not go unrewarded. (The words lean on 'care' a little too hard.)",
        "answers": [
            {"text": "That word care bent under the weight. What is the extra for?",
             "reaction": "Bent, was it? Then nothing extra.",
             "kind": "call_out"},
            {"text": "Fine. For the care, then.",
             "reaction": "For the care. I will remember.",
             "kind": "pay_extra"},
            {"text": "I do not pay to be cared for.",
             "reaction": "No. Care is not a purchase.",
             "kind": "walk"},
        ],
    },
    "insight": {
        "text": "You heard the weight in my words. What do you think I want?",
        "answers": [
            {"text": "To be needed, not paid.",
             "reaction": "Needed, not paid. Yes.",
             "kind": "settle"},
            {"text": "I would rather not guess.",
             "reaction": "No guessing, then. That is safer.",
             "kind": "refuse"},
            {"text": "I should rest. Goodbye.",
             "reaction": "Rest, then. You have earned it.",
             "kind": "walk"},
        ],
    },
}

# ---------------------------------------------------------------------------
# One-time counter-offer banks (contentVersion 4).
#
# Every SMT-style negotiation flow gains exactly one haggle attempt in its price
# phase: an authored `counter` option on the primary demand node, leading to one
# `<prefix>.counter_deal` node that demands a reduced price and offers
# pay/refuse/walk. No second counter offer exists anywhere in the graph.
# Per personality: the haggle line, the Digimon's reaction to being haggled, the
# reduced-price speech, and authored reactions for the three reduced-price
# answers. No numbers are hard-coded in prose: the amount lives in the demand
# object so engines stay authoritative.
# ---------------------------------------------------------------------------
NEGOTIATION_COUNTER_BANKS: dict[int, dict] = {}

NEGOTIATION_COUNTER_BANKS[1] = {
    "haggle": {
        "text": "That is a lot to ask of someone who just met you. Meet me halfway?",
        "reaction": "Halfway... You want to meet halfway. (It bites its lip, torn between pride and wanting you to stay.)",
    },
    "counter_deal": {
        "text": "Halfway, then. A smaller price, and I still get to feel wanted. Does that suit you?",
        "answers": [
            {"text": "Here is the smaller price, gladly.",
             "reaction": "Gladly given again. You keep surprising me.",
             "kind": "pay"},
            {"text": "Even smaller, or I am only paying to be liked.",
             "reaction": "Then keep it. Smaller or not, I would rather be liked than paid.",
             "kind": "refuse"},
            {"text": "This has turned into bargaining, not company.",
             "reaction": "Bargaining, then, not company. Go. I will remember that you listened first.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_COUNTER_BANKS[2] = {
    "haggle": {
        "text": "Your oath is worth more than that number. Take it down.",
        "reaction": "My oath, haggled like a fish? (It sighs.) You have a point, and I dislike that.",
    },
    "counter_deal": {
        "text": "The number comes down; the oath stays exactly the same. Serve that and we are square.",
        "answers": [
            {"text": "Agreed. The same oath, and the better number.",
             "reaction": "Better number, same oath. Then it is a fair bargain.",
             "kind": "pay"},
            {"text": "No. An oath should be neither cheap nor expensive.",
             "reaction": "No oath, then. Just words.",
             "kind": "refuse"},
            {"text": "This is not worth my word.",
             "reaction": "Then keep your word for someone who wants it.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_COUNTER_BANKS[3] = {
    "haggle": {
        "text": "That price is too high for a talk this honest. Lower it a little.",
        "reaction": "Too high for honesty? Hm. That is a gentle argument, and a fair one.",
    },
    "counter_deal": {
        "text": "A gentler number, then. Patience has a price, but it need not be steep.",
        "answers": [
            {"text": "Here. A gentler number for a gentler talk.",
             "reaction": "Then we keep talking. Gently.",
             "kind": "pay"},
            {"text": "Still too high. I will sit with that.",
             "reaction": "Sit with it. The number will not grow by waiting.",
             "kind": "refuse"},
            {"text": "I will take my patience elsewhere.",
             "reaction": "Then take it. Patience travels well.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_COUNTER_BANKS[4] = {
    "haggle": {
        "text": "You are charging me for protection I cannot check. Lower it.",
        "reaction": "Cannot check? Then watch me lower it. Protection should be seen to be trusted.",
    },
    "counter_deal": {
        "text": "Less for the road, and I still watch the rear. That is the offer, and it is final.",
        "answers": [
            {"text": "Fine. Less for the road, and watch the rear.",
             "reaction": "Rear watched. Now walk where I can see you.",
             "kind": "pay"},
            {"text": "No. Cheap and safe at once, or nothing.",
             "reaction": "Cheap and safe at once is expensive to be stubborn.",
             "kind": "refuse"},
            {"text": "I will risk the road alone.",
             "reaction": "Alone is exactly what I warned you about.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_COUNTER_BANKS[5] = {
    "haggle": {
        "text": "You are charging for fire like it is firewood. Cut the price.",
        "reaction": "Firewood! You would bargain a flame down? (It laughs, delighted and offended.) Bold.",
    },
    "counter_deal": {
        "text": "Half the price, double the marching. That is the deal I can shout about.",
        "answers": [
            {"text": "Take it. Half the price, and we march.",
             "reaction": "Half the price, and we march! Now we have a bargain worth shouting about.",
             "kind": "pay"},
            {"text": "No. You cannot discount a fire and keep the heat.",
             "reaction": "Then the heat stays mine, and the fire stays mine.",
             "kind": "refuse"},
            {"text": "Keep the fire. I will find my own.",
             "reaction": "Then go cold. The fire will wait for a louder one.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_COUNTER_BANKS[6] = {
    "haggle": {
        "text": "That toll is too high for an honest offer. Bring it down.",
        "reaction": "Down? From a toll? You negotiate like a soldier. Fine, the toll comes down.",
    },
    "counter_deal": {
        "text": "The toll drops. The danger does not. Pay the lower price and stand beside me.",
        "answers": [
            {"text": "Fine. The lower toll, and I stand beside you.",
             "reaction": "The lower toll, and you stand beside me. Then we are square, soldier.",
             "kind": "pay"},
            {"text": "No. I will not pay to be tested in a fight.",
             "reaction": "Then the fight can find us both.",
             "kind": "refuse"},
            {"text": "Forget it. I will find a partner who is not a toll gate.",
             "reaction": "Find one. Mind the road.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_COUNTER_BANKS[7] = {
    "haggle": {
        "text": "Too high! Too slow! Cut the price and let us move.",
        "reaction": "Cut it? Fine, but then we move fast. No more talking about numbers.",
    },
    "counter_deal": {
        "text": "Price slashed. Now we move. No more words about bits.",
        "answers": [
            {"text": "Take it. Price slashed, and let us go.",
             "reaction": "Price slashed and moving! Finally!",
             "kind": "pay"},
            {"text": "No. You slashed the price, not my patience.",
             "reaction": "Slashed the price and my patience is gone? Ugh. Boring.",
             "kind": "refuse"},
            {"text": "I am done. Too much talk about bits.",
             "reaction": "Too much talk about bits? Then go be slow somewhere else!",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_COUNTER_BANKS[8] = {
    "haggle": {
        "text": "The odds are not worth that price. Make a better offer.",
        "reaction": "Better odds? Then offer a better bet, and I will lower the stake.",
    },
    "counter_deal": {
        "text": "New stake, same adventure. That is the bet now. Take it or leave the table.",
        "answers": [
            {"text": "Deal. New stake, same adventure.",
             "reaction": "A new stake, a new bet. Now that is worth taking.",
             "kind": "pay"},
            {"text": "No. The adventure is not worth the new stake either.",
             "reaction": "Not worth the new stake? Then you are not the player I thought.",
             "kind": "refuse"},
            {"text": "Keep the stake. I will find a bigger game.",
             "reaction": "Bigger games have bigger losses.",
             "kind": "walk"},
        ],
    },
}

NEGOTIATION_COUNTER_BANKS[9] = {
    "haggle": {
        "text": "That price does not match what this conversation is worth. Bring it closer.",
        "reaction": "Closer... You think understanding has a fair number. Interesting.",
    },
    "counter_deal": {
        "text": "A smaller offering, then. The custom bends; the meaning does not.",
        "answers": [
            {"text": "Here. A smaller offering, freely made.",
             "reaction": "Freely made. Then the custom is satisfied.",
             "kind": "pay"},
            {"text": "No offering. I will not be charged for understanding.",
             "reaction": "Then understanding sits between us, unpaid.",
             "kind": "refuse"},
            {"text": "I will leave this custom to someone else.",
             "reaction": "Go. The road keeps customs for those who stay.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_COUNTER_BANKS[10] = {
    "haggle": {
        "text": "That price is a trick, and I have seen it. Lower it.",
        "reaction": "Seen it? Then you have earned a lower trick.",
    },
    "counter_deal": {
        "text": "A smaller number, and I keep my secrets. Does the new trick amuse you?",
        "answers": [
            {"text": "It does. Here is the smaller number.",
             "reaction": "Smaller number, and amused. The trick worked on both of us.",
             "kind": "pay"},
            {"text": "No. Smaller numbers do not make old tricks new.",
             "reaction": "Then the old trick stays expensive.",
             "kind": "refuse"},
            {"text": "Keep your tricks. I have seen enough.",
             "reaction": "Enough is a fine place to leave a magician.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_COUNTER_BANKS[11] = {
    "haggle": {
        "text": "Your number is too round. Make it match the actual value.",
        "reaction": "Too round... You inspected the number itself. Bold.",
    },
    "counter_deal": {
        "text": "Recalculated: a smaller number, and the terms untouched.",
        "answers": [
            {"text": "Here. The recalculated number.",
             "reaction": "The recalculated number. The math holds.",
             "kind": "pay"},
            {"text": "No. I do not trust a number that moves when asked.",
             "reaction": "Then the untrusting number stands.",
             "kind": "refuse"},
            {"text": "This talk has stopped being precise.",
             "reaction": "Precision left before you did.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_COUNTER_BANKS[12] = {
    "haggle": {
        "text": "The price is out of budget. Revise the line item.",
        "reaction": "Out of budget? Then let me revise the line item, strategically.",
    },
    "counter_deal": {
        "text": "Revised budget: smaller number, same cooperation, same duration.",
        "answers": [
            {"text": "Approved. Smaller number, same cooperation.",
             "reaction": "Approved. The plan survives the revision.",
             "kind": "pay"},
            {"text": "Denied. The revised number still hurts the plan.",
             "reaction": "Denied. We proceed as budgeted.",
             "kind": "refuse"},
            {"text": "I will take the budget to someone who respects it.",
             "reaction": "Then the budget travels alone.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_COUNTER_BANKS[13] = {
    "haggle": {
        "text": "That number is too greedy even for me. Cut it.",
        "reaction": "Too greedy? I like you. Here comes a smaller number.",
    },
    "counter_deal": {
        "text": "Smaller number, faster deal. Both of us profit sooner.",
        "answers": [
            {"text": "Done. Smaller number, faster deal.",
             "reaction": "Done! This is why I bargain with you.",
             "kind": "pay"},
            {"text": "No. Faster does not mean worth it.",
             "reaction": "No faster deal, then. Slower and poorer for both of us.",
             "kind": "refuse"},
            {"text": "I will find profit elsewhere.",
             "reaction": "It will find you first.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_COUNTER_BANKS[14] = {
    "haggle": {
        "text": "That is too much between friends. Knock a little off?",
        "reaction": "Between friends? Fine, knock a little off. (It grins.)",
    },
    "counter_deal": {
        "text": "A friendlier price, then. We both still win.",
        "answers": [
            {"text": "Here is the friendlier price, friend.",
             "reaction": "Friendlier price, friendlier walk. Let us go!",
             "kind": "pay"},
            {"text": "No. Friends still do not overpay.",
             "reaction": "Then we stay friends in the old way.",
             "kind": "refuse"},
            {"text": "I will give this road to someone kinder.",
             "reaction": "Go find kinder roads. I will still wave.",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_COUNTER_BANKS[15] = {
    "haggle": {
        "text": "That price ruins the story. Lower it so we have a better ending.",
        "reaction": "A better ending? Then lower the number and tell the story right.",
    },
    "counter_deal": {
        "text": "Smaller number, better ending. Tell it exactly as it happened.",
        "answers": [
            {"text": "Take it. And I will tell them you bargained well.",
             "reaction": "Bargained well! Now that is a story ending!",
             "kind": "pay"},
            {"text": "No. A good story does not need a discount.",
             "reaction": "Then the story keeps its expensive middle.",
             "kind": "refuse"},
            {"text": "I will find a cheaper audience.",
             "reaction": "Cheaper audiences tell worse stories!",
             "kind": "walk"},
        ],
    },
}
NEGOTIATION_COUNTER_BANKS[16] = {
    "haggle": {
        "text": "That price is more than I can carry comfortably. Lower it, please.",
        "reaction": "More than you can carry? Then lower it. I do not want you breaking.",
    },
    "counter_deal": {
        "text": "A lighter number, then. I will not make you carry the heavy one.",
        "answers": [
            {"text": "Here. The lighter number, gladly.",
             "reaction": "Gladly. Good. The road is lighter now.",
             "kind": "pay"},
            {"text": "No. I cannot carry even the lighter number right now.",
             "reaction": "Then the number can wait, and so can I.",
             "kind": "refuse"},
            {"text": "I am sorry. I have to go.",
             "reaction": "No apologies. Sorry is not needed. Rest first, bargain later.",
             "kind": "walk"},
        ],
    },
}


