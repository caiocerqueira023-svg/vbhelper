#!/usr/bin/env python3
"""DigiLine v2 — deterministic content-pack generator (workstream A).

WHAT THIS BUILDS
----------------
    app/src/main/assets/digiline/v1/manifest.json
    app/src/main/assets/digiline/v1/en/species_questions.json
    app/src/main/assets/digiline/v1/en/generic_questions.json
    app/src/main/assets/digiline/v1/en/quiz.json
    app/src/main/assets/digiline/v1/en/negotiation.json
    app/src/main/assets/digiline/v1/en/persuasion.json
    app/src/main/assets/digiline/v1/en/event_lines.json
    app/src/main/assets/digiline/v1/en/lore_species.json
    tools/digiline/unmatched_species.json          (coverage report)

ORIGINALITY POLICY (hard rule; plan decision D1)
------------------------------------------------
All prose shipped by this generator is composed from hand-authored banks in
`tools/digiline/content_banks.py`, written for this project. No text is copied
from `app/src/main/assets/species_chat_examples.json`, `digimon_chat_export/`,
SMT V, Cyber Sleuth or any wiki. The bundled species-chat asset is used ONLY
as a *coverage/data-shape reference*: this generator reads its 268
`characterId`/`speciesName` values to know which species need a species
question, and never reads or reuses its `opening`/`exchanges` prose.
`validate_packs.py` re-checks every shipped line against the reference asset.

Species FACTS (level / species type) are read from
`app/src/main/assets/species_catalog.json`; the catalog is the single source
of truth for factual content (quiz, lore, stage tiers). Species questions that
mention a fact only do so through generated templates, and are marked
`"authoringStatus": "template"`.

DETERMINISM
-----------
No timestamps, no randomness, no locale-dependent sorting. Every choice uses
sha256-based stable hashes and explicit sort orders, so two runs over the same
inputs produce byte-identical files.

CONTENT VERSION
---------------
`CONTENT_VERSION` is written to `manifest.json` and documented in place:
  1 = initial pack (32 generic questions, 4 per personality)
  2 = generic bank expansion to 128 questions (16 per personality, plan §6.4)
  3 = negotiation rewrite: authored SMT-style trees (node/option `topic`,
      per-answer `reaction`, tier-deep authored exchanges)
  4 = negotiation counter-offer: one authored `counter` option per price phase
      leading to one reduced-price `counter_deal` node (one-time by graph)

Run:  python tools/digiline/build_packs.py
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parents[1]
ASSET_DIR = REPO_ROOT / "app" / "src" / "main" / "assets"
CATALOG_PATH = ASSET_DIR / "species_catalog.json"
SPECIES_CHATS_PATH = ASSET_DIR / "species_chat_examples.json"
IDENTITY_REGISTRY_PATH = REPO_ROOT / "tools" / "species_identity_registry.json"
OUT_ROOT = ASSET_DIR / "digiline" / "v1"
OUT_EN = OUT_ROOT / "en"
UNMATCHED_REPORT_PATH = HERE / "unmatched_species.json"

sys.path.insert(0, str(HERE))
import content_banks as banks  # noqa: E402  (authored original text banks)

CONTENT_VERSION = 4
# contentVersion history:
#   1 = initial pack (32 generic questions, 4 per personality)
#   2 = generic bank expansion: +96 themed cards, 128 total / 16 per personality
#   3 = negotiation rewrite: authored SMT-style trees with per-answer demon
#       reactions, node/option topics and tier-deep authored exchanges
#   4 = negotiation counter-offer branch: one authored haggle option per price
#       phase leading to one reduced-demand counter_deal node (no second counter)
SCHEMA_VERSION = 1
DEFAULT_LOCALE = "en"

# Contract §1.1 / §7.3 stage-tier mapping. "Champion" is a synonym of "Adult"
# found once in the catalog; it is mapped to T2 and documented in the report.
LEVEL_TO_TIER = {
    "Baby I": 1,
    "Baby II": 1,
    "Child": 1,
    "Adult": 2,
    "Champion": 2,
    "Armor": 2,
    "Hybrid": 2,
    "Perfect": 3,
    "Ultimate": 4,
}
# Levels usable as quiz options (the canonical ladder; excludes Unknown/(?)/None).
QUIZ_LEVEL_OPTIONS = ["Baby I", "Baby II", "Child", "Adult", "Armor", "Hybrid", "Perfect", "Ultimate"]
UNKNOWN_FACT_VALUES = {"", "Unknown", "Species Unknown", "(?)", "None"}

# Manual alias map for species-chat names whose romanization/variant differs
# from the app catalog. Keys are normalized chat keys (see normalize_key);
# values are catalog id suffixes. Everything here is a shape/name mapping, not
# content: no dialogue text is involved.
MANUAL_ALIASES = {
    "aegiochusmonholly": "aegiochusmonholy",
    "beelzemonbm": "beelzebumonblastmode",
    "rosemonbm": "rosemonburstmode",
    "lucemonfm": "lucemonfalldownmode",
    "lucemonsm": "lucemonsatanmode",
    "imperialdramonfm": "imperialdramonfightermode",
    "miragegaogamonbm": "miragegaogamonburstmode",
    "ravemonbm": "ravemonburstmode",
    "dukemoncm": "dukemoncrimsonmode",
    "duftmonlm": "duftmonleopardmode",
    "belphemonrm": "belphemonragemode",
    "belphemonsm": "belphemonsleepmode",
    "megagrowlmon": "megalogrowmon",
    "ultimatebrakimon": "ultimatebrachimon",
    "undeadplutomon": "zombieplutomon",
    "chronomon": "chronomonholymode",
    "chronomondestroy": "chronomondestroymode",
    "atlurkabuterimon": "atlurkabuterimonblue",
}

MANUAL_ALIAS_NOTES = {
    "aegiochusmonholly": "double-L spelling variant of catalog Aegiochusmon(Holy)",
    "beelzemonbm": "alternate romanization of Beelzebumon; BM = Blast Mode",
    "rosemonbm": "BM = Burst Mode",
    "lucemonfm": "FM = Falldown Mode",
    "lucemonsm": "SM = Satan Mode",
    "imperialdramonfm": "FM = Fighter Mode",
    "miragegaogamonbm": "BM = Burst Mode",
    "ravemonbm": "alternate romanization of Ravmon; BM = Burst Mode",
    "dukemoncm": "CM = Crimson Mode",
    "duftmonlm": "LM = Leopard Mode",
    "belphemonrm": "RM = Rage Mode",
    "belphemonsm": "SM = Sleep Mode",
    "megagrowlmon": "alternate romanization of catalog Megalo Growmon",
    "ultimatebrakimon": "alternate spelling of catalog Ultimate Brachimon",
    "undeadplutomon": "alternate form name for catalog Zombie Plutomon",
    "chronomon": "catalog has Holy Mode / Destroy Mode; base name mapped to Holy Mode",
    "chronomondestroy": "catalog Chronomon Destroy Mode",
    "atlurkabuterimon": "catalog splits Blue/Red; classic Blue variant used",
}

# Species-chat entries that are deliberately not matched (reported, not guessed).
UNMATCHED_REASONS = {
    "PICKLEMON": "no corresponding species exists in app species_catalog.json",
    "YUNIMON": "no corresponding species exists in app species_catalog.json",
    "RAPIDMON": "catalog only has Rapidmon Armor / Rapidmon Perfect / X-Antibody variants; base form is ambiguous",
    "JUSTIMON": "catalog only has Justimon(Accel Arm)/Blitz Arm/Critical Arm variants; base form is ambiguous",
    "MAGNAGARURUMON_SEPARATION": "catalog has no 'Separation' variant; base Magna Garurumon would misstate the form",
}


# ---------------------------------------------------------------------------
# Small deterministic helpers
# ---------------------------------------------------------------------------
def normalize_key(value: str) -> str:
    """Contract §1.1 speciesKey normalization: lowercase letters/digits only."""
    text = unicodedata.normalize("NFKD", str(value))
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    return re.sub(r"[^a-z0-9]", "", text.lower())


def stable_hash(*parts: object) -> int:
    """Stable, process-independent hash used for every deterministic choice."""
    payload = "|".join(str(part) for part in parts).encode("utf-8")
    return int.from_bytes(hashlib.sha256(payload).digest()[:8], "big")


def load_json(path: Path):
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def write_json(path: Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)


def article_for(word: str) -> str:
    return "an" if str(word).strip()[:1].lower() in "aeiou" else "a"


def stage_tier_for_level(level: str | None) -> int:
    if not level:
        return 2
    return LEVEL_TO_TIER.get(str(level).strip(), 2)


def is_meaningful_fact(value) -> bool:
    return bool(value) and str(value).strip() not in UNKNOWN_FACT_VALUES


# ---------------------------------------------------------------------------
# Species matching (species_chat_examples.json -> species_catalog.json)
# ---------------------------------------------------------------------------
def build_catalog_indexes(species: list[dict]) -> tuple[dict, dict]:
    by_key: dict[str, dict] = {}
    by_id_suffix: dict[str, dict] = {}
    for entry in species:
        suffix = entry["id"].split(".")[-1]
        by_id_suffix[suffix] = entry
        candidates = {normalize_key(entry.get("name", "")), normalize_key(suffix)}
        for alias in entry.get("aliases") or []:
            candidates.add(normalize_key(alias))
        for key in candidates:
            if key:
                by_key.setdefault(key, entry)
    return by_key, by_id_suffix


def match_species_chats(chat_entries: list[dict], catalog: dict) -> tuple[list[tuple[dict, dict]], list[dict], list[dict]]:
    by_key, by_id_suffix = build_catalog_indexes(catalog["species"])
    matched: list[tuple[dict, dict]] = []
    unmatched: list[dict] = []
    duplicates: list[dict] = []
    claimed_species_ids: dict[str, str] = {}
    for chat in chat_entries:
        chat_keys = [normalize_key(chat.get("characterId", "")), normalize_key(chat.get("speciesName", ""))]
        hit = None
        for key in chat_keys:
            if key and key in by_key:
                hit = by_key[key]
                break
            if key and key in MANUAL_ALIASES:
                hit = by_id_suffix[MANUAL_ALIASES[key]]
                break
        if hit is None:
            unmatched.append(
                {
                    "characterId": chat.get("characterId", ""),
                    "speciesName": chat.get("speciesName", ""),
                    "normalizedKey": chat_keys[0],
                    "reason": UNMATCHED_REASONS.get(
                        chat.get("characterId", ""),
                        "no app-catalog species matched the normalized name/alias",
                    ),
                }
            )
            continue
        species_id = hit["id"]
        if species_id in claimed_species_ids:
            duplicates.append(
                {
                    "characterId": chat.get("characterId", ""),
                    "speciesName": chat.get("speciesName", ""),
                    "matchedSpeciesId": species_id,
                    "alreadyMatchedBy": claimed_species_ids[species_id],
                    "note": "one species question per catalog species; duplicate chat entry skipped",
                }
            )
            continue
        claimed_species_ids[species_id] = chat.get("characterId", "")
        matched.append((chat, hit))
    return matched, unmatched, duplicates


# ---------------------------------------------------------------------------
# Pack: species_questions.json
# ---------------------------------------------------------------------------
def build_species_questions(matched: list[tuple[dict, dict]]) -> list[dict]:
    questions: list[dict] = []
    for _chat, species in matched:
        species_key = normalize_key(species["name"])
        tier = stage_tier_for_level(species.get("level"))
        prompt = choose_species_prompt(species, species_key, tier)
        combo = banks.SPECIES_CORE_COMBOS[stable_hash(species_key, "combo") % len(banks.SPECIES_CORE_COMBOS)]
        options = []
        for index, core in enumerate(combo):
            option_bank = banks.SPECIES_CORE_OPTIONS[core]
            authored = option_bank[stable_hash(species_key, core, "option") % len(option_bank)]
            options.append(
                {
                    "id": "abc"[index],
                    "text": authored["text"],
                    "reaction": authored["reaction"],
                    "moodDelta": authored["moodDelta"],
                    "shift": {str(type_number): points for type_number, points in authored["shift"].items()},
                    "tone": banks.CORE_TONE[core],
                }
            )
        questions.append(
            {
                "id": f"sp.{species_key}.1",
                "speciesKey": species_key,
                "speciesName": species["name"],
                "stageTier": tier,
                "prompt": prompt,
                "options": options,
                "tags": ["species"],
                "authoringStatus": "template",
            }
        )
    questions.sort(key=lambda item: item["speciesKey"])
    return questions


def choose_species_prompt(species: dict, species_key: str, tier: int) -> str:
    available_facts = {"name": species.get("name", "")}
    if is_meaningful_fact(species.get("level")):
        available_facts["level"] = str(species["level"]).strip()
    if is_meaningful_fact(species.get("type")):
        available_facts["type"] = str(species["type"]).strip()
    templates = [
        template
        for template in banks.SPECIES_PROMPTS[tier]
        if set(template["needs"]).issubset(available_facts.keys())
    ]
    chosen = templates[stable_hash(species_key, "prompt") % len(templates)]
    return chosen["template"].format(**available_facts)


# ---------------------------------------------------------------------------
# Pack: generic_questions.json
# ---------------------------------------------------------------------------
def core_secondary_type(type_number: int) -> int:
    core = banks.PERSONALITIES[type_number]["core"]
    core_members = banks.CORE_TYPES[core]
    return core_members[(core_members.index(type_number) + 1) % len(core_members)]


def third_core_for(pair: tuple[int, int]) -> str:
    core_a = banks.PERSONALITIES[pair[0]]["core"]
    core_b = banks.PERSONALITIES[pair[1]]["core"]
    candidates = [core for core in banks.CORE_ORDER if core not in (core_a, core_b)]
    if not candidates:
        candidates = [banks.CORE_ORDER[(banks.CORE_ORDER.index(core_a) + 2) % len(banks.CORE_ORDER)]]
    return candidates[stable_hash(pair[0], pair[1], "third-core") % len(candidates)]


def third_option_shift(pair: tuple[int, int]) -> dict[int, int]:
    core = third_core_for(pair)
    members = banks.CORE_TYPES[core]
    primary = members[stable_hash(pair[0], pair[1], "third-type") % len(members)]
    return {primary: 2, core_secondary_type(primary): 1}


GENERIC_EXTENSION_ROUNDS = 12


def generic_extension_pairing_rounds() -> list[list[tuple[int, int]]]:
    """Cross-quadrant asker pairings for the contentVersion 2 generic expansion.

    One theme per round; within a round every personality 1..16 appears exactly
    once, and every pair joins two different cores. 12 rounds x 8 pairs = 96
    additional cards, so combined with the 32 baseline cards each personality
    receives 16 generic questions (plan §6.4 target: >= 8).
    """
    core_members = [banks.CORE_TYPES[core] for core in banks.CORE_ORDER]
    delta_cycle = [1, 2, 3, 1]  # no round may pair two types of the same core
    rounds: list[list[tuple[int, int]]] = []
    for round_index in range(GENERIC_EXTENSION_ROUNDS):
        delta = delta_cycle[round_index % len(delta_cycle)]
        permutation = (round_index // len(delta_cycle)) % 4
        source_cores = [0, 2] if delta % 2 == 1 else [0, 1]
        pairs: list[tuple[int, int]] = []
        for source_core in source_cores:
            target_core = (source_core + delta) % len(core_members)
            for position in range(4):
                first = core_members[source_core][position]
                second = core_members[target_core][(position + permutation) % 4]
                pairs.append((first, second))
        rounds.append(pairs)
    return rounds


def build_generic_questions() -> list[dict]:
    questions: list[dict] = []
    seen_per_first_type: dict[int, int] = {}
    for index, pair in enumerate(banks.GENERIC_TYPE_PAIRS):
        authored = banks.GENERIC_QUESTIONS[index]
        first_type, second_type = pair
        core = banks.PERSONALITIES[first_type]["core"]
        shifts = [
            {first_type: 2, core_secondary_type(first_type): 1},
            {second_type: 2, core_secondary_type(second_type): 1},
            third_option_shift(pair),
        ]
        mood_deltas = [1, 1, 0]
        options = []
        for option_index, option in enumerate(authored["options"]):
            shift = shifts[option_index]
            primary_core = banks.PERSONALITIES[next(iter(shift))]["core"]
            options.append(
                {
                    "id": "abc"[option_index],
                    "text": option["text"],
                    "reaction": option["reaction"],
                    "moodDelta": mood_deltas[option_index],
                    "shift": {str(type_number): points for type_number, points in shift.items()},
                    "tone": banks.CORE_TONE[primary_core],
                }
            )
        seen_per_first_type[first_type] = seen_per_first_type.get(first_type, 0) + 1
        questions.append(
            {
                "id": f"gen.{first_type}.{seen_per_first_type[first_type]}",
                "askerTypes": [first_type, second_type],
                "core": core,
                "stageTier": 1,
                "prompt": authored["prompt"],
                "options": options,
                "tags": ["generic"],
            }
        )
    # --- contentVersion 2 expansion: 96 themed cards (12 themes x 8 pairings) ---
    for round_index, pairings in enumerate(generic_extension_pairing_rounds()):
        theme = banks.GENERIC_THEMES[round_index]
        for prompt_index, pair in enumerate(pairings):
            first_type, second_type = pair
            core_first = banks.PERSONALITIES[first_type]["core"]
            core_second = banks.PERSONALITIES[second_type]["core"]
            core_third = third_core_for(pair)
            shifts = [
                {first_type: 2, core_secondary_type(first_type): 1},
                {second_type: 2, core_secondary_type(second_type): 1},
                third_option_shift(pair),
            ]
            lane_cores = [core_first, core_second, core_third]
            mood_deltas = [1, 1, 0]
            options = []
            for option_index, lane_core in enumerate(lane_cores):
                variants = theme["lanes"][lane_core]
                variant_index = stable_hash(
                    round_index, first_type, second_type, lane_core
                ) % len(variants)
                authored = variants[variant_index]
                options.append(
                    {
                        "id": "abc"[option_index],
                        "text": authored["text"],
                        "reaction": authored["reaction"],
                        "moodDelta": mood_deltas[option_index],
                        "shift": {
                            str(type_number): points
                            for type_number, points in shifts[option_index].items()
                        },
                        "tone": banks.CORE_TONE[lane_core],
                    }
                )
            seen_per_first_type[first_type] = seen_per_first_type.get(first_type, 0) + 1
            questions.append(
                {
                    "id": f"gen.{first_type}.{seen_per_first_type[first_type]}",
                    "askerTypes": [first_type, second_type],
                    "core": core_first,
                    "stageTier": 1,
                    "prompt": theme["prompts"][prompt_index],
                    "options": options,
                    "tags": ["generic"],
                }
            )
    return questions


# ---------------------------------------------------------------------------
# Pack: quiz.json — facts come ONLY from species_catalog.json
# ---------------------------------------------------------------------------
def build_quiz(catalog: dict) -> tuple[list[dict], list[str]]:
    species = sorted(catalog["species"], key=lambda entry: normalize_key(entry["name"]))
    all_types = sorted(
        {str(entry["type"]).strip() for entry in species if is_meaningful_fact(entry.get("type"))}
    )
    questions: list[dict] = []
    referenced_keys: list[str] = []
    for entry in species:
        if entry.get("kind") != "DIGIMON":
            continue
        species_key = normalize_key(entry["name"])
        name = entry["name"]
        level = str(entry.get("level", "")).strip()
        species_type = str(entry.get("type", "")).strip()
        if level in QUIZ_LEVEL_OPTIONS:
            questions.append(
                make_quiz_question(
                    index=len(questions) + 1,
                    name=name,
                    species_key=species_key,
                    stage_tier=stage_tier_for_level(level),
                    prompt_variants=banks.QUIZ_LEVEL_PROMPTS,
                    options_pool=QUIZ_LEVEL_OPTIONS,
                    correct_value=level,
                    explanation_variants=banks.QUIZ_LEVEL_EXPLANATIONS,
                    explanation_values={"level": level},
                )
            )
            referenced_keys.append(species_key)
        if is_meaningful_fact(species_type):
            questions.append(
                make_quiz_question(
                    index=len(questions) + 1,
                    name=name,
                    species_key=species_key,
                    stage_tier=stage_tier_for_level(level),
                    prompt_variants=banks.QUIZ_TYPE_PROMPTS,
                    options_pool=all_types,
                    correct_value=species_type,
                    explanation_variants=banks.QUIZ_TYPE_EXPLANATIONS,
                    explanation_values={"type": species_type},
                )
            )
            referenced_keys.append(species_key)
    return questions, referenced_keys


def make_quiz_question(
    *,
    index: int,
    name: str,
    species_key: str,
    stage_tier: int,
    prompt_variants: list[str],
    options_pool: list[str],
    correct_value: str,
    explanation_variants: list[str],
    explanation_values: dict,
) -> dict:
    others = [value for value in options_pool if value != correct_value]
    others.sort(key=lambda value: stable_hash(species_key, "distractor", value))
    options = [correct_value] + others[:3]
    options.sort(key=lambda value: stable_hash(species_key, "option-position", value))
    prompt = prompt_variants[stable_hash(species_key, prompt_variants[0]) % len(prompt_variants)].format(name=name)
    explanation = explanation_variants[
        stable_hash(species_key, explanation_variants[0]) % len(explanation_variants)
    ].format(
        name=name,
        a=article_for(correct_value),
        **explanation_values,
    )
    return {
        "id": f"quiz.{index:04d}",
        "stageTier": stage_tier,
        "prompt": prompt,
        "options": options,
        "correctIndex": options.index(correct_value),
        "explanation": explanation,
        "loreRefs": [f"species_catalog:{species_key}"],
    }


# ---------------------------------------------------------------------------
# Pack: negotiation.json
# ---------------------------------------------------------------------------
# SMT-style authored trees (contentVersion 3). Every flow is composed from the
# personality's authored NEGOTIATION_TREES bank:
#   * node texts are the wild Digimon's prompts only;
#   * every option carries the Digimon's authored reaction to that specific
#     answer (option.reaction) and the topic it continues (option.topic);
#   * option.topic always equals the target node's topic, so a follow-up prompt
#     always continues/escalates the same topic;
#   * branches split at the opening (warm/cold follow-ups) and converge only
#     after the authored exchanges earn a bargain/trick/insight;
#   * tiers differ by authored depth: T1 = 2 exchanges, T2/T3 = 3, T4 = 4, with
#     trick branches from T2, insight from T3, and DEPART only at T4.
NEGOTIATION_TOPICS = {
    "start": "intent",
    "follow_warm": "intent",
    "follow_cold": "intent",
    "escalate": "stake",
    "escalate2": "stake",
    "demand": "demand",
    "counter_deal": "demand",
    "trick": "demand",
    "insight": "demand",
    "join": "demand",
    "refuse": "demand",
    "depart": "demand",
    "depart_final": "demand",
    "paid_leave": "demand",
    "fight": "stake",
}

# Identifies which authored line a repeated role should use inside one flow so
# the same node never repeats a line (persuasion topic_a/topic_b rely on it too).
TAG_OCCURRENCE_ORDER = {
    "first": 0,
    "start": 0,
    "second": 1,
    "third": 2,
    "final": 1,
}


def option(option_id: str, text: str, next_node: str, effects: dict | None = None) -> dict:
    payload = {"id": option_id, "text": text, "next": next_node}
    if effects:
        payload["effects"] = effects
    return payload


def demand_amounts(personality: int, tier: int) -> tuple[int, int]:
    low, high = banks.NEGOTIATION_DEMAND_RANGES[tier]
    extra = banks.NEGOTIATION_EXTRA_PAYMENT.get(tier, 0)
    span = (high - low + 1) - extra
    primary = low + (stable_hash(personality, tier, "demand") % span)
    return primary, primary + extra


def counter_amount(personality: int, tier: int, primary_demand: int) -> int:
    """Reduced one-time counter offer: deterministic 55-75% of the primary price.

    Uses ceil(55%) as the inclusive lower bound and floor(75%) as the inclusive
    upper bound so integer rounding can never leave the 55-75% contract band.
    """
    low = (primary_demand * 55 + 99) // 100
    high = (primary_demand * 75) // 100
    low = max(1, min(low, primary_demand - 1))
    high = max(low, min(high, primary_demand - 1))
    return low + (stable_hash(personality, tier, "counter-ratio") % (high - low + 1))


def build_negotiation_pack() -> dict:
    personalities: dict[str, dict[str, dict]] = {}
    for personality in range(1, 17):
        tiers: dict[str, dict] = {}
        for tier in range(1, 5):
            tiers[str(tier)] = build_negotiation_flow(personality, tier)
        personalities[str(personality)] = tiers
    return {"schemaVersion": SCHEMA_VERSION, "personalities": personalities}


def build_negotiation_flow(personality: int, tier: int) -> dict:
    tree = banks.NEGOTIATION_TREES[personality]
    voice = banks.NEGOTIATION_VOICE[personality]
    primary_demand, _secondary_demand = demand_amounts(personality, tier)
    extra_payment = banks.NEGOTIATION_EXTRA_PAYMENT.get(tier, 0)

    def node_id(suffix: str) -> str:
        return f"p{personality}.t{tier}.{suffix}"

    def topic_of(suffix: str) -> str:
        return NEGOTIATION_TOPICS[suffix]

    def answer_option(option_id: str, answer: dict, target_suffix: str, effects: dict | None = None) -> dict:
        payload = {
            "id": option_id,
            "text": answer["text"],
            "reaction": answer["reaction"],
            "topic": topic_of(target_suffix),
            "next": node_id(target_suffix),
        }
        if effects:
            payload["effects"] = effects
        return payload

    def prompt_node(suffix: str, tag: str, text: str, **extra) -> dict:
        node = {
            "id": node_id(suffix),
            "speaker": "wild",
            "tag": tag,
            "topic": topic_of(suffix),
            "text": text,
        }
        node.update(extra)
        return node

    def outcome_node(suffix: str, tag: str, text: str, outcome: str) -> dict:
        return {
            "id": node_id(suffix),
            "speaker": "wild",
            "tag": tag,
            "topic": topic_of(suffix),
            "text": text,
            "outcome": outcome,
        }

    nodes: dict[str, dict] = {}

    # --- opening: three in-world answers split into warm/cold follow-ups -----
    opening_options = []
    for index, answer in enumerate(tree["opening"]["answers"]):
        target = "follow_warm" if answer.get("branch") == "warm" else "follow_cold"
        opening_options.append(answer_option("abc"[index], answer, target))
    nodes[node_id("start")] = prompt_node(
        "start", "greeting", tree["opening"]["text"], options=opening_options
    )

    # --- follow-up exchange: same topic, then escalate (T2+) or price (T1) ---
    follow_target = "demand" if tier == 1 else "escalate"
    for branch in ("warm", "cold"):
        follow = tree["follow"][branch]
        options = [
            answer_option("abc"[index], answer, follow_target)
            for index, answer in enumerate(follow["answers"])
        ]
        nodes[node_id(f"follow_{branch}")] = prompt_node(
            f"follow_{branch}", "probe", follow["text"], options=options
        )

    # --- escalation exchange(s): depends on the contracted tier depth --------
    if tier >= 2:
        escalate_options = []
        for index, answer in enumerate(tree["escalate"]["answers"]):
            if answer.get("kind") == "fight":
                if tier >= 3:
                    escalate_options.append(answer_option("c", answer, "fight"))
                continue  # T2 stays at depth 3 and has no extra branch yet
            target = "escalate2" if tier >= 4 else "demand"
            escalate_options.append(answer_option("abc"[index], answer, target))
        nodes[node_id("escalate")] = prompt_node(
            "escalate", "probe", tree["escalate"]["text"], options=escalate_options
        )

    if tier >= 4:
        escalate2_options = [
            answer_option("abc"[index], answer, "demand")
            for index, answer in enumerate(tree["escalate2"]["answers"])
        ]
        nodes[node_id("escalate2")] = prompt_node(
            "escalate2", "probe", tree["escalate2"]["text"], options=escalate2_options
        )

    # --- bargain: the authored price demand ---------------------------------
    demand_answers = tree["demand"]["answers"]
    demand_pay_target = "trick" if tier >= 2 else "join"
    demand_options = []
    for answer in demand_answers:
        kind = answer["kind"]
        if kind == "pay":
            demand_options.append(
                answer_option("pay", answer, demand_pay_target, effects={"bits": -primary_demand})
            )
        elif kind == "refuse":
            demand_options.append(answer_option("no", answer, "refuse"))
        else:
            demand_options.append(answer_option("leave", answer, "depart"))
    # One-time counter offer: the haggle option itself never pays anything; the
    # reduced price lives on the single counter_deal node it opens. The option id
    # is an app-side contract (HUD counter chip): it MUST stay exactly "counter".
    counter_bank = banks.NEGOTIATION_COUNTER_BANKS[personality]
    demand_options.append(answer_option("counter", counter_bank["haggle"], "counter_deal"))
    nodes[node_id("demand")] = prompt_node(
        "demand",
        "demand",
        tree["demand"]["text"],
        demand={"kind": "bits", "amount": primary_demand},
        options=demand_options,
    )

    counter_demand = counter_amount(personality, tier, primary_demand)
    counter_options = []
    for answer in counter_bank["counter_deal"]["answers"]:
        kind = answer["kind"]
        if kind == "pay":
            counter_options.append(
                answer_option("pay", answer, "join", effects={"bits": -counter_demand})
            )
        elif kind == "refuse":
            counter_options.append(answer_option("no", answer, "refuse"))
        else:
            counter_options.append(answer_option("walk", answer, "depart"))
    nodes[node_id("counter_deal")] = prompt_node(
        "counter_deal",
        "demand",
        counter_bank["counter_deal"]["text"],
        demand={"kind": "bits", "amount": counter_demand},
        options=counter_options,
    )

    # --- trick branch (T2+) and insight branch (T3+) ------------------------
    if tier >= 2:
        trick_options = []
        for answer in tree["trick"]["answers"]:
            kind = answer["kind"]
            if kind == "call_out":
                trick_options.append(
                    answer_option("call_out", answer, "insight" if tier >= 3 else "join")
                )
            elif kind == "pay_extra":
                trick_options.append(
                    answer_option("pay_extra", answer, "refuse", effects={"bits": -extra_payment})
                )
            else:
                if tier >= 4:
                    trick_options.append(
                        answer_option("walk", answer, "depart_final", effects={"depart": True})
                    )
                else:
                    trick_options.append(answer_option("walk", answer, "depart"))
        nodes[node_id("trick")] = prompt_node(
            "trick", "trick", tree["trick"]["text"], options=trick_options
        )

    if tier >= 3:
        insight_options = []
        for answer in tree["insight"]["answers"]:
            kind = answer["kind"]
            if kind == "settle":
                insight_options.append(answer_option("settle", answer, "join"))
            elif kind == "refuse":
                insight_options.append(answer_option("no", answer, "refuse"))
            else:
                insight_options.append(answer_option("leave", answer, "paid_leave"))
        nodes[node_id("insight")] = prompt_node(
            "insight", "insight", tree["insight"]["text"], options=insight_options
        )

    # --- terminal outcomes: authored voice lines, topic is inherited --------
    nodes[node_id("join")] = outcome_node("join", "join", voice["join"][0], "JOIN")
    nodes[node_id("refuse")] = outcome_node("refuse", "refuse", voice["refuse"][0], "REFUSE")
    nodes[node_id("depart")] = outcome_node("depart", "depart", voice["depart"][0], "LEAVE")
    if tier >= 3:
        nodes[node_id("paid_leave")] = outcome_node(
            "paid_leave", "resolve", voice["pay_leave"][0], "PAY_AND_LEAVE"
        )
        nodes[node_id("fight")] = outcome_node("fight", "resolve", voice["fight"][0], "FIGHT")
    if tier >= 4:
        nodes[node_id("depart_final")] = outcome_node(
            "depart_final", "depart", voice["depart"][-1], "DEPART"
        )

    ordered: dict[str, dict] = {}
    for key in sorted(nodes.keys()):
        ordered[key] = nodes[key]
    return {"start": node_id("start"), "nodes": ordered}


# ---------------------------------------------------------------------------
# Pack: persuasion.json
# ---------------------------------------------------------------------------
def persuasion_option_text(tag: str, personality: int, stage: int, salt: str = "", offset: int = 0) -> str:
    bank = banks.PERSUASION_OPTIONS[tag]
    return bank[(stable_hash(personality, stage, tag, salt) + offset) % len(bank)]


def build_persuasion_pack() -> dict:
    personalities: dict[str, dict[str, dict]] = {}
    for personality in range(1, 17):
        stages: dict[str, dict] = {}
        for stage in range(4):
            stages[str(stage)] = build_persuasion_flow(personality, stage)
        personalities[str(personality)] = stages
    return {"schemaVersion": SCHEMA_VERSION, "personalities": personalities}


def build_persuasion_flow(personality: int, stage: int) -> dict:
    voice = banks.PERSUASION_VOICE[personality]

    def node_id(suffix: str) -> str:
        return f"p{personality}.s{stage}.{suffix}"

    def pick(bank_name: str, salt: str, offset: int = 0) -> str:
        bank = voice[bank_name]
        ordinal = TAG_OCCURRENCE_ORDER.get(salt, offset)
        base = stable_hash(personality, stage, bank_name) % len(bank)
        return bank[(base + ordinal) % len(bank)]

    def pos_effects(salt: str) -> dict:
        return {"persuasionFlag": f"persuade_positive_{salt}"}

    def neg_effects(salt: str) -> dict:
        return {"persuasionFlag": f"persuade_negative_{salt}"}

    start_node = node_id("start")
    topic_a_node = node_id("topic_a")
    topic_b_node = node_id("topic_b")
    resolve_node = node_id("quest_offer") if stage == 3 else node_id("next_stage")
    depart_node = node_id("depart")
    refuse_node = node_id("refuse")

    nodes: dict[str, dict] = {
        start_node: {
            "id": start_node,
            "speaker": "wild",
            "tag": "greeting",
            "text": f"{pick('open', 'open')} {banks.PERSUASION_STAGE_FLAVOR[stage]}",
            "options": [
                option("a", persuasion_option_text("engage", personality, stage, "a"), topic_a_node, pos_effects("engage")),
                option("b", persuasion_option_text("challenge", personality, stage, "b"), topic_b_node, neg_effects("challenge")),
                option("c", persuasion_option_text("leave", personality, stage, "c"), depart_node, None),
            ],
        },
        topic_a_node: {
            "id": topic_a_node,
            "speaker": "wild",
            "tag": "probe",
            "text": pick("topic", "topic_a"),
            "options": [
                option("a", persuasion_option_text("empathize", personality, stage, "a"), resolve_node, pos_effects("empathize")),
                option("b", persuasion_option_text("decline", personality, stage, "b"), refuse_node, neg_effects("decline")),
            ],
        },
        topic_b_node: {
            "id": topic_b_node,
            "speaker": "wild",
            "tag": "insight",
            "text": pick("topic", "topic_b", offset=1),
            "options": [
                option("a", persuasion_option_text("commit", personality, stage, "a"), resolve_node, pos_effects("commit")),
                option("b", persuasion_option_text("decline", personality, stage, "b"), refuse_node, neg_effects("decline_b")),
            ],
        },
        depart_node: {
            "id": depart_node,
            "speaker": "wild",
            "tag": "depart",
            "text": voice["depart"][0],
            "outcome": "DEPART",
        },
        refuse_node: {
            "id": refuse_node,
            "speaker": "wild",
            "tag": "refuse",
            "text": voice["refuse"][0],
            "outcome": "REFUSE",
        },
    }
    if stage == 3:
        nodes[resolve_node] = {
            "id": resolve_node,
            "speaker": "wild",
            "tag": "join",
            "text": pick("offer", "offer"),
            "outcome": "QUEST_OFFER",
        }
    else:
        nodes[resolve_node] = {
            "id": resolve_node,
            "speaker": "wild",
            "tag": "resolve",
            "text": pick("next", "next"),
            "outcome": "NEXT_STAGE",
        }

    ordered: dict[str, dict] = {}
    for key in sorted(nodes.keys()):
        ordered[key] = nodes[key]
    return {"start": start_node, "nodes": ordered}


# ---------------------------------------------------------------------------
# Pack: event_lines.json
# ---------------------------------------------------------------------------
def build_event_lines() -> list[dict]:
    lines: list[dict] = []
    for event in banks.EVENT_ORDER:
        groups = banks.EVENT_LINES[event]
        shared_lines = groups["shared"]
        for index, (text, mood_delta) in enumerate(shared_lines, start=1):
            lines.append(
                {
                    "id": f"ev.{event}.shared.{index}",
                    "event": event,
                    "text": text,
                    "moodDelta": int(mood_delta),
                }
            )
        for core in banks.CORE_ORDER:
            types = banks.CORE_TYPES[core]
            id_range = f"{types[0]}-{types[-1]}"
            for index, (text, mood_delta) in enumerate(groups[core], start=1):
                lines.append(
                    {
                        "id": f"ev.{event}.{id_range}.{index}",
                        "event": event,
                        "types": list(types),
                        "text": text,
                        "moodDelta": int(mood_delta),
                    }
                )
    return lines


# ---------------------------------------------------------------------------
# Pack: lore_species.json
# ---------------------------------------------------------------------------
def build_lore(catalog: dict, referenced_keys: set[str]) -> list[dict]:
    by_key = {normalize_key(entry["name"]): entry for entry in catalog["species"]}
    facts: list[dict] = []
    for species_key in sorted(referenced_keys):
        entry = by_key[species_key]
        facts.append(
            {
                "speciesKey": species_key,
                "speciesName": entry["name"],
                "level": entry.get("level") if is_meaningful_fact(entry.get("level")) else entry.get("level"),
                "type": entry.get("type"),
                "attribute": None,  # species_catalog.json has no attribute field
                "source": "app_species_catalog",
                "sourceUrl": None,
            }
        )
    return facts


# ---------------------------------------------------------------------------
# Orchestration
# ---------------------------------------------------------------------------
def main() -> int:
    catalog = load_json(CATALOG_PATH)
    species_chats = load_json(SPECIES_CHATS_PATH)
    chat_entries = species_chats["entries"]

    matched, unmatched, duplicates = match_species_chats(chat_entries, catalog)

    species_questions = build_species_questions(matched)
    generic_questions = build_generic_questions()
    quiz_questions, quiz_referenced_keys = build_quiz(catalog)
    negotiation_pack = build_negotiation_pack()
    persuasion_pack = build_persuasion_pack()
    event_lines = build_event_lines()

    referenced_keys = set(quiz_referenced_keys) | {question["speciesKey"] for question in species_questions}
    lore_facts = build_lore(catalog, referenced_keys)

    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "contentVersion": CONTENT_VERSION,
        "locale": DEFAULT_LOCALE,
        "files": {
            "species": "species_questions.json",
            "generic": "generic_questions.json",
            "quiz": "quiz.json",
            "negotiation": "negotiation.json",
            "persuasion": "persuasion.json",
            "events": "event_lines.json",
            "lore": "lore_species.json",
        },
    }

    write_json(OUT_ROOT / "manifest.json", manifest)
    write_json(OUT_EN / "species_questions.json", {"schemaVersion": SCHEMA_VERSION, "questions": species_questions})
    write_json(OUT_EN / "generic_questions.json", {"schemaVersion": SCHEMA_VERSION, "questions": generic_questions})
    write_json(OUT_EN / "quiz.json", {"schemaVersion": SCHEMA_VERSION, "questions": quiz_questions})
    write_json(OUT_EN / "negotiation.json", negotiation_pack)
    write_json(OUT_EN / "persuasion.json", persuasion_pack)
    write_json(OUT_EN / "event_lines.json", {"schemaVersion": SCHEMA_VERSION, "lines": event_lines})
    write_json(OUT_EN / "lore_species.json", {"schemaVersion": SCHEMA_VERSION, "facts": lore_facts})

    unmatched_report = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedBy": "tools/digiline/build_packs.py",
        "sourceFile": "app/src/main/assets/species_chat_examples.json",
        "catalogFile": "app/src/main/assets/species_catalog.json",
        "matchingRule": "normalized characterId or speciesName against catalog name/id/aliases; MANUAL_ALIASES for romanization variants",
        "totalChatEntries": len(chat_entries),
        "matchedCount": len(matched),
        "unmatchedCount": len(unmatched),
        "unmatched": unmatched,
        "duplicateMappings": duplicates,
        "manualAliases": {key: MANUAL_ALIASES[key] for key in sorted(MANUAL_ALIASES)},
        "manualAliasNotes": {key: MANUAL_ALIAS_NOTES[key] for key in sorted(MANUAL_ALIAS_NOTES)},
    }
    write_json(UNMATCHED_REPORT_PATH, unmatched_report)

    negotiation_nodes = sum(
        len(flow["nodes"])
        for tiers in negotiation_pack["personalities"].values()
        for flow in tiers.values()
    )
    persuasion_nodes = sum(
        len(flow["nodes"])
        for stages in persuasion_pack["personalities"].values()
        for flow in stages.values()
    )

    summary = {
        "chatEntries": len(chat_entries),
        "matchedSpecies": len(matched),
        "unmatchedSpecies": len(unmatched),
        "speciesQuestions": len(species_questions),
        "genericQuestions": len(generic_questions),
        "quizQuestions": len(quiz_questions),
        "negotiationFlows": sum(len(tiers) for tiers in negotiation_pack["personalities"].values()),
        "negotiationNodes": negotiation_nodes,
        "persuasionFlows": sum(len(stages) for stages in persuasion_pack["personalities"].values()),
        "persuasionNodes": persuasion_nodes,
        "eventLines": len(event_lines),
        "loreFacts": len(lore_facts),
        "outputRoot": str(OUT_ROOT.relative_to(REPO_ROOT)),
    }
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
