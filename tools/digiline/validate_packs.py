#!/usr/bin/env python3
"""DigiLine v2 — content-pack validator (workstream A).

Validates the shipped JSON against `DIGILINE_IMPLEMENTATION_CONTRACT.md` §1:
  * manifest <-> file layout (§1, §1.7)
  * species_questions  (§1.1: coverage vs species chats, ids, options, shift, tier)
  * generic_questions  (§1.2: every personality 1..16 has >= 8 questions in the
                             shipped contentVersion (contract floor 2; plan §6.4
                             target 8), core field, shifts, moodDelta)
  * quiz               (§1.3: >= 100 catalog-fact questions, unique prompts,
                             correctIndex valid, loreRefs/app-catalog verification)
  * negotiation        (§1.4: 16 personalities x 4 tiers, node counts, tags,
                             reachability, demands scale by tier, tricks T2+ with
                             tells; contentVersion 3 SMT-style: node/option
                             topics, authored reaction on every answer, topic
                             continuity (option.topic == next node.topic), 2-3
                             branches per question, authored depth per tier,
                             JOIN + REFUSE/LEAVE reachable, no legacy generic
                             meta options, opening branches into >= 2 nodes;
                             contentVersion 4: exactly one in-world `counter`
                             option in the price phase -> one counter_deal node
                             with a reduced bits demand (55-75% of primary,
                             tier-specific counter band), pay/refuse/walk only,
                             no second counter offer)
  * persuasion         (§1.5: 16 personalities x 4 stages, >= 2 authored choice
                             nodes each, terminal outcomes NEXT_STAGE / QUEST_OFFER /
                             REFUSE / DEPART)
  * event_lines        (§1.6: all required events, >= 2 lines each, moodDelta range)
  * lore_species       (§1.7: one fact per referenced species, values match the app catalog)
  * unmatched_species  (§1.8: consistent coverage report)

Also runs the originality/language spot-checks promised by the contract:
  * no shipped line duplicates (even loosely) a line from the bundled
    `species_chat_examples.json` or `digimon_chat_export/` (D1: rewrite everything);
  * no gendered third-person pronouns in authored prose;
  * no SMT terminology (`Demon`, `Macca`, `Netherworld`, `Negotiation`) in authored
    prose (catalog `type` facts such as "Demon Lord" are catalog data, not prose,
    and are excluded from the term lint by design).

Exit code is 0 only when there are no errors; warnings do not fail the run.

Run:  python tools/digiline/validate_packs.py
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, OrderedDict
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parents[1]
ASSET_DIR = REPO_ROOT / "app" / "src" / "main" / "assets"
CATALOG_PATH = ASSET_DIR / "species_catalog.json"
SPECIES_CHATS_PATH = ASSET_DIR / "species_chat_examples.json"
CONTENT_ROOT = ASSET_DIR / "digiline" / "v1"
LOCALE = "en"
UNMATCHED_REPORT_PATH = HERE / "unmatched_species.json"
EXPORT_DIR = REPO_ROOT / "digimon_chat_export"

sys.path.insert(0, str(HERE))
import build_packs as generator  # noqa: E402  (shared coverage/matching helpers)
import content_banks as banks  # noqa: E402

EXPECTED_FILES = {
    "species": "species_questions.json",
    "generic": "generic_questions.json",
    "quiz": "quiz.json",
    "negotiation": "negotiation.json",
    "persuasion": "persuasion.json",
    "events": "event_lines.json",
    "lore": "lore_species.json",
}

NODE_TAGS = {
    "greeting", "probe", "demand", "trick", "insight", "resolve",
    "join", "refuse", "depart",
}
NEGOTIATION_OUTCOMES = {"JOIN", "REFUSE", "LEAVE", "FIGHT", "PAY_AND_LEAVE", "DEPART"}
PERSUASION_OUTCOMES = {"NEXT_STAGE", "QUEST_OFFER", "REFUSE", "DEPART"}
EFFECT_KEYS = {"bits", "itemId", "itemQuantity", "persuasionFlag", "depart"}
REQUIRED_NEGOTIATION_TAGS = {"greeting", "probe", "demand", "join", "refuse", "depart"}
# contentVersion 4 adds one counter_deal node + four options per flow, so the
# contracted node-count ranges are widened (T1 +1 .. T4 +1 from their old bounds).
TIER_NODE_RANGES = {1: (5, 9), 2: (7, 11), 3: (9, 14), 4: (12, 17)}
# Tier-specific reduced-price bands for the one-time counter offer. These are
# validator bands for the counter_deal amount; the per-flow check additionally
# enforces counterDeal < primaryDemand and a 55-75 % ratio (stronger than these
# coarse bands, and the real intent of "reduced but meaningful").
COUNTER_DEMAND_RANGES = {1: (18, 60), 2: (45, 95), 3: (85, 165), 4: (135, 230)}
SHIFT_TYPES = {str(number) for number in range(1, 17)}
# Expansion pass (contentVersion 2): plan §6.4 requires >= 8 generic questions
# per personality. The contract floor is >= 2; this validator enforces the
# raised shipped-content expectation.
GENERIC_MIN_PER_TYPE = 8
# Authored question exchanges before the price, per stage tier (contentVersion 3
# negotiation rewrite: T1=2, T2/T3=3, T4=4).
NEGOTIATION_TIER_DEPTH = {1: 2, 2: 3, 3: 3, 4: 4}
# Old contentVersion 1-2 generic player meta-lines. The negotiation rewrite must
# not reuse them: options have to be in-world speech that answers the prompt.
LEGACY_META_OPTION_TEXTS = [
    "I'm listening. What do you want?",
    "Say your piece. Why did you stop me?",
    "Go on, then. What is this about?",
    "That's more than small talk. Tell me what you're really after.",
    "You're testing me. Why?",
    "Skip the riddles. What's the offer?",
    "Then state your price. I'll decide after that.",
    "Name your terms, clearly.",
    "You've made your point. What's the toll?",
    "Fine. Take the bits. Let's settle it.",
    "Here's the payment. Now we're square.",
    "This should cover it. What happens now?",
    "No deal. I'm not paying for a conversation.",
    "Keep the bits. I'll find another way.",
    "That price is too high for words alone.",
    "I'm done talking.",
    "We're finished here. Safe travels.",
    "Forget it. I'll find another road.",
    "Then we do this the hard way.",
    "If words won't work, let's settle it in battle.",
    "Enough. Let's fight.",
    "That flicker in your voice gave you away.",
    "You're adding to the price. Try a cleaner trick.",
    "Nice try. The first price was the offer.",
    "Fine. One more payment. This is the last of it.",
    "You're pushing it, but here's the extra.",
    "I see what you did there. The demand dropped, didn't it?",
    "You respect straight talk. Then here's mine.",
    "You've been lying since the first word. We're done.",
    "Keep the bits. I'd rather walk away than be played.",
    "That's enough. I don't bargain with liars.",
    "Then we understand each other. Let's move.",
    "Good. No more games, we have a deal.",
    "The price is fair now. I'm satisfied.",
]
LEGACY_META_OPTION_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in (
        r"what'?s the toll",
        r"name your terms",
        r"let'?s settle it",
        r"what happens now",
        r"you'?ve made your point",
        r"try a cleaner trick",
        r"walk away than be played",
    )
]
GENDERED_RE = re.compile(r"\b(he|she|him|her|his|hers|himself|herself)\b", re.IGNORECASE)
SMT_TERM_RE = re.compile(r"\b(demon|demons|macca|netherworld|negotiation|compendium)\b", re.IGNORECASE)
PLACEHOLDER_RE = re.compile(r"[{}]|TODO|TBD|Lorem ipsum", re.IGNORECASE)
LORE_REF_RE = re.compile(r"^species_catalog:([a-z0-9]+)$")


def is_int(value) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def normalized_exact(text: str) -> str:
    return re.sub(r"\s+", " ", str(text)).strip().casefold()


def normalized_loose(text: str) -> str:
    return re.sub(r"[^a-z0-9]", "", str(text).casefold())


# Function words ignored by the reaction-references-answer heuristic. Only
# grammatical glue lives here: content words (even short ones like "bits",
# "road", "keep") must not be filtered or the heuristic becomes meaningless.
STOPWORDS = {
    "about", "after", "again", "against", "almost", "along", "already", "also",
    "although", "always", "among", "another", "anyone", "anything", "around",
    "because", "before", "behind", "being", "below", "beside", "between",
    "cannot", "could", "does", "doing", "down", "during", "each",
    "either", "else", "even", "ever", "every", "from", "further", "have",
    "having", "however", "into", "just", "less", "many", "maybe", "might",
    "most", "must", "neither", "never", "next", "once",
    "only", "other", "ours", "quite", "rather", "really", "same",
    "shall", "should", "since", "some", "still",
    "such", "than", "that", "their", "them", "then", "there", "these",
    "they", "this", "those", "though", "through", "thus", "together", "under",
    "until", "upon", "very", "well", "were", "what", "when", "where",
    "whether", "which", "while", "with", "within", "without", "would",
    "your", "yours", "yourself",
}


def significant_tokens(text: str) -> set[str]:
    return {
        token
        for token in re.findall(r"[a-z0-9]+", str(text).casefold())
        if len(token) >= 4 and token not in STOPWORDS
    }


def tokens_echo(left: str, right: str) -> bool:
    """True when two tokens share a >=4-char prefix (covers inflections)."""
    if left == right:
        return True
    prefix = 0
    for left_char, right_char in zip(left, right):
        if left_char != right_char:
            break
        prefix += 1
    return prefix >= 4


def reaction_references_answer(answer: str, reaction: str) -> bool:
    answer_tokens = significant_tokens(answer)
    reaction_tokens = significant_tokens(reaction)
    return any(
        tokens_echo(answer_token, reaction_token)
        for answer_token in answer_tokens
        for reaction_token in reaction_tokens
    )


class Validator:
    def __init__(self, locale: str = LOCALE, content_root: Path = CONTENT_ROOT) -> None:
        self.locale = locale
        self.content_root = content_root
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.notes: list[str] = []
        self.counts: "OrderedDict[str, object]" = OrderedDict()
        self.catalog = generator.load_json(CATALOG_PATH)
        self.catalog_by_key = {
            generator.normalize_key(entry["name"]): entry for entry in self.catalog["species"]
        }
        self.chat_entries = generator.load_json(SPECIES_CHATS_PATH)["entries"]
        self.packs: dict[str, dict] = {}
        # Catalog species whose NAME contains a term the SMT lint looks for
        # (e.g. the Digimon called "Demon"). Names are catalog facts, so they are
        # masked out before the term lint; only authored prose is judged.
        self._smt_species_names = sorted(
            {
                str(entry["name"])
                for entry in self.catalog["species"]
                if SMT_TERM_RE.search(str(entry["name"]))
            },
            key=len,
            reverse=True,
        )
        # (label, text) for every authored prose string that must obey the
        # originality/neutrality policy.
        self.authored_texts: list[tuple[str, str]] = []

    # -- reporting helpers --------------------------------------------------
    def error(self, message: str) -> None:
        self.errors.append(message)

    def warn(self, message: str) -> None:
        self.warnings.append(message)

    def note(self, message: str) -> None:
        self.notes.append(message)

    def require(self, condition: bool, message: str) -> bool:
        if not condition:
            self.error(message)
        return condition

    # -- generic shape helpers ---------------------------------------------
    def load_pack(self, name: str, path: Path) -> dict | None:
        if not path.exists():
            self.error(f"{name}: missing file {self.relative(path)}")
            return None
        try:
            data = json.loads(path.read_text(encoding="utf-8-sig"))
        except Exception as exc:  # noqa: BLE001 - report any parse failure
            self.error(f"{name}: malformed JSON in {self.relative(path)}: {exc}")
            return None
        if not isinstance(data, dict):
            self.error(f"{name}: root must be a JSON object")
            return None
        if data.get("schemaVersion") != 1:
            self.error(f"{name}: schemaVersion must be 1, got {data.get('schemaVersion')!r}")
        self.packs[name] = data
        return data

    @staticmethod
    def relative(path: Path) -> str:
        try:
            return str(path.relative_to(REPO_ROOT)).replace("\\", "/")
        except ValueError:
            return str(path)

    def collect_text(self, label: str, text) -> None:
        if isinstance(text, str) and text.strip():
            self.authored_texts.append((label, text))

    # -- option/shift validation -------------------------------------------
    def check_options(
        self,
        where: str,
        options,
        *,
        min_options: int,
        max_options: int,
        require_reaction: bool,
    ) -> list[dict]:
        if not isinstance(options, list) or not options:
            self.error(f"{where}: options must be a non-empty list")
            return []
        if not (min_options <= len(options) <= max_options):
            self.error(f"{where}: expected {min_options}..{max_options} options, got {len(options)}")
        seen_ids: set[str] = set()
        valid: list[dict] = []
        for option in options:
            if not isinstance(option, dict):
                self.error(f"{where}: option must be an object")
                continue
            option_id = option.get("id")
            if not isinstance(option_id, str) or not option_id:
                self.error(f"{where}: option id must be a non-empty string")
                continue
            if option_id in seen_ids:
                self.error(f"{where}: duplicate option id {option_id!r}")
            seen_ids.add(option_id)
            text = option.get("text")
            if not isinstance(text, str) or not text.strip():
                self.error(f"{where}.{option_id}: empty option text")
                continue
            self.collect_text(f"{where}.{option_id}.text", text)
            if require_reaction:
                reaction = option.get("reaction")
                if not isinstance(reaction, str) or not reaction.strip():
                    self.error(f"{where}.{option_id}: empty reaction")
                else:
                    self.collect_text(f"{where}.{option_id}.reaction", reaction)
            mood_delta = option.get("moodDelta", 0)
            if not is_int(mood_delta) or not (-1 <= mood_delta <= 2):
                self.error(f"{where}.{option_id}: moodDelta must be int in -1..2, got {mood_delta!r}")
            shift = option.get("shift")
            if not isinstance(shift, dict) or not shift:
                self.error(f"{where}.{option_id}: shift must be a non-empty object")
            else:
                positive = False
                for type_key, points in shift.items():
                    if not isinstance(type_key, str) or type_key not in SHIFT_TYPES:
                        self.error(f"{where}.{option_id}: shift key {type_key!r} not a type number 1..16")
                        continue
                    if not is_int(points) or points == 0 or not (-5 <= points <= 5):
                        self.error(
                            f"{where}.{option_id}: shift[{type_key}] must be a non-zero int in -5..5, got {points!r}"
                        )
                    elif points > 0:
                        positive = True
                if not positive:
                    self.error(f"{where}.{option_id}: shift must contain at least one positive points entry")
            tone = option.get("tone")
            if tone is not None and not isinstance(tone, str):
                self.error(f"{where}.{option_id}: tone must be a string when present")
            valid.append(option)
        return valid

    def check_question(self, where: str, question: dict, option_range: tuple[int, int], require_tier: bool) -> None:
        if not isinstance(question, dict):
            self.error(f"{where}: question must be an object")
            return
        prompt = question.get("prompt")
        if not isinstance(prompt, str) or not prompt.strip():
            self.error(f"{where}: empty prompt")
        else:
            self.collect_text(f"{where}.prompt", prompt)
            if PLACEHOLDER_RE.search(prompt):
                self.error(f"{where}: prompt contains an unresolved placeholder: {prompt!r}")
        if require_tier:
            stage_tier = question.get("stageTier")
            if not is_int(stage_tier) or not (1 <= stage_tier <= 4):
                self.error(f"{where}: stageTier must be int 1..4, got {stage_tier!r}")
        self.check_options(
            where,
            question.get("options"),
            min_options=option_range[0],
            max_options=option_range[1],
            require_reaction=True,
        )

    def check_shift_coverage(self, where: str, questions: list[dict]) -> None:
        """Plan §6.3/§6.4 reachability: every personality type must be reachable
        by some option, and every card must offer movement into >= 2 cores."""
        type_points: Counter = Counter()
        cards_without_spread = 0
        for question in questions:
            if not isinstance(question, dict):
                continue
            card_cores: set[str] = set()
            for option in question.get("options", []) or []:
                if not isinstance(option, dict) or not isinstance(option.get("shift"), dict):
                    continue
                for type_key, points in option["shift"].items():
                    if isinstance(type_key, str) and type_key in SHIFT_TYPES and is_int(points) and points > 0:
                        type_number = int(type_key)
                        type_points[type_number] += points
                        card_cores.add(banks.PERSONALITIES[type_number]["core"])
            if len(card_cores) < 2:
                cards_without_spread += 1
        missing = [type_number for type_number in range(1, 17) if type_points[type_number] == 0]
        if missing:
            self.error(f"{where}: no positive shift toward personality types {missing}")
        if cards_without_spread:
            self.error(f"{where}: {cards_without_spread} question cards offer movement into fewer than 2 cores")
        return None

    # -- manifest -----------------------------------------------------------
    def validate_manifest(self) -> None:
        manifest = self.load_pack("manifest", self.content_root / "manifest.json")
        if manifest is None:
            return
        if not is_int(manifest.get("contentVersion")) or manifest["contentVersion"] < 1:
            self.error("manifest: contentVersion must be a positive int")
        if manifest.get("locale") != self.locale:
            self.error(f"manifest: locale must be {self.locale!r}")
        files = manifest.get("files")
        if not isinstance(files, dict):
            self.error("manifest: files must be an object")
            return
        for key, filename in EXPECTED_FILES.items():
            if files.get(key) != filename:
                self.error(f"manifest: files[{key!r}] must be {filename!r}, got {files.get(key)!r}")
        extra = set(files) - set(EXPECTED_FILES)
        if extra:
            self.warn(f"manifest: unknown file keys {sorted(extra)}")
        self.counts["manifest files"] = len(files)
        self.counts["manifest contentVersion"] = manifest.get("contentVersion")

    def load_all_packs(self) -> None:
        locale_root = self.content_root / self.locale
        for key, filename in EXPECTED_FILES.items():
            self.load_pack(key, locale_root / filename)

    # -- species_questions --------------------------------------------------
    def validate_species(self) -> None:
        pack = self.packs.get("species")
        if pack is None:
            return
        questions = pack.get("questions")
        if not isinstance(questions, list):
            self.error("species: questions must be a list")
            return
        matched, unmatched_chats, duplicates = generator.match_species_chats(self.chat_entries, self.catalog)
        expected_keys = {generator.normalize_key(species["name"]) for _chat, species in matched}
        seen_keys: set[str] = set()
        for index, question in enumerate(questions):
            where = f"species[{index}]"
            self.check_question(where, question, (3, 4), require_tier=True)
            if not isinstance(question, dict):
                continue
            species_key = question.get("speciesKey")
            if not isinstance(species_key, str) or not re.fullmatch(r"[a-z0-9]+", species_key):
                self.error(f"{where}: speciesKey must be lowercase letters/digits")
                continue
            if species_key in seen_keys:
                self.error(f"{where}: duplicate speciesKey {species_key!r}")
            seen_keys.add(species_key)
            if question.get("id") != f"sp.{species_key}.1":
                self.error(f"{where}: id must be sp.{species_key}.1, got {question.get('id')!r}")
            species = self.catalog_by_key.get(species_key)
            if species is None:
                self.error(f"{where}: speciesKey {species_key!r} not found in species_catalog.json")
                continue
            if question.get("speciesName") != species["name"]:
                self.error(
                    f"{where}: speciesName must match catalog name {species['name']!r}, "
                    f"got {question.get('speciesName')!r}"
                )
            expected_tier = generator.stage_tier_for_level(species.get("level"))
            if question.get("stageTier") != expected_tier:
                self.error(
                    f"{where}: stageTier {question.get('stageTier')!r} does not match catalog level "
                    f"{species.get('level')!r} -> {expected_tier}"
                )
            tags = question.get("tags")
            if not isinstance(tags, list) or "species" not in tags:
                self.error(f"{where}: tags must contain 'species'")
            authoring_status = question.get("authoringStatus")
            if authoring_status not in (None, "template", "authored"):
                self.error(f"{where}: authoringStatus must be 'template'/'authored' when present")
        missing = expected_keys - seen_keys
        extra = seen_keys - expected_keys
        if missing:
            self.error(f"species: missing questions for {len(missing)} matchable species: {sorted(missing)[:10]}")
        if extra:
            self.error(f"species: questions for non-matchable species: {sorted(extra)[:10]}")
        if duplicates:
            self.error(f"species: duplicate chat->species mappings in matching: {duplicates[:3]}")
        self.counts["species questions"] = len(questions)
        self.counts["species expected (matchable)"] = len(expected_keys)
        self.counts["species unmatched (report)"] = len(unmatched_chats)
        self.check_shift_coverage("species", questions)

    # -- generic_questions --------------------------------------------------
    def validate_generic(self) -> None:
        pack = self.packs.get("generic")
        if pack is None:
            return
        questions = pack.get("questions")
        if not isinstance(questions, list):
            self.error("generic: questions must be a list")
            return
        seen_ids: set[str] = set()
        prompts: set[str] = set()
        per_type: Counter = Counter()
        for index, question in enumerate(questions):
            where = f"generic[{index}]"
            self.check_question(where, question, (2, 4), require_tier=True)
            if not isinstance(question, dict):
                continue
            question_id = question.get("id")
            if not isinstance(question_id, str) or not re.fullmatch(r"gen\.\d+\.\d+", question_id):
                self.error(f"{where}: id must be gen.<type>.<n>, got {question_id!r}")
            if question_id in seen_ids:
                self.error(f"{where}: duplicate id {question_id!r}")
            seen_ids.add(question_id)
            prompt = question.get("prompt")
            if isinstance(prompt, str):
                if prompt.casefold() in prompts:
                    self.warn(f"{where}: duplicate generic prompt")
                prompts.add(prompt.casefold())
            asker_types = question.get("askerTypes")
            if (
                not isinstance(asker_types, list)
                or not (1 <= len(asker_types) <= 2)
                or not all(is_int(value) and 1 <= value <= 16 for value in asker_types)
                or len(set(asker_types)) != len(asker_types)
            ):
                self.error(f"{where}: askerTypes must be 1..2 distinct type numbers 1..16")
                continue
            for type_number in asker_types:
                per_type[type_number] += 1
            expected_core = banks.PERSONALITIES[asker_types[0]]["core"]
            if question.get("core") != expected_core:
                self.error(
                    f"{where}: core must be {expected_core!r} for asker type {asker_types[0]}, "
                    f"got {question.get('core')!r}"
                )
        for type_number in range(1, 17):
            if per_type[type_number] < GENERIC_MIN_PER_TYPE:
                self.error(
                    f"generic: personality {type_number} has {per_type[type_number]} questions "
                    f"(< {GENERIC_MIN_PER_TYPE})"
                )
        if per_type:
            self.note(
                f"generic: {len(questions)} questions; every personality 1..16 has "
                f"{min(per_type[type] for type in range(1, 17))}.."
                f"{max(per_type[type] for type in range(1, 17))} "
                f"(contract floor 2, plan §6.4 target {GENERIC_MIN_PER_TYPE})"
            )
        self.counts["generic questions"] = len(questions)
        self.counts["generic min per personality"] = min(per_type[type] for type in range(1, 17))
        self.check_shift_coverage("generic", questions)

    # -- quiz ---------------------------------------------------------------
    def validate_quiz(self) -> None:
        pack = self.packs.get("quiz")
        if pack is None:
            return
        questions = pack.get("questions")
        if not isinstance(questions, list):
            self.error("quiz: questions must be a list")
            return
        if len(questions) < 100:
            self.error(f"quiz: needs >= 100 questions, got {len(questions)}")
        elif len(questions) < 300:
            self.warn(f"quiz: {len(questions)} questions (plan launch target >= 300)")
        seen_ids: set[str] = set()
        seen_prompts: dict[str, int] = {}
        level_pool = set(generator.QUIZ_LEVEL_OPTIONS)
        type_pool = {
            str(entry["type"]).strip()
            for entry in self.catalog["species"]
            if generator.is_meaningful_fact(entry.get("type"))
        }
        referenced: set[str] = set()
        for index, question in enumerate(questions):
            where = f"quiz[{index}]"
            if not isinstance(question, dict):
                self.error(f"{where}: question must be an object")
                continue
            question_id = question.get("id")
            if not isinstance(question_id, str) or not re.fullmatch(r"quiz\.\d+", question_id):
                self.error(f"{where}: id must be quiz.<n>, got {question_id!r}")
            if question_id in seen_ids:
                self.error(f"{where}: duplicate id {question_id!r}")
            seen_ids.add(question_id)
            stage_tier = question.get("stageTier")
            if not is_int(stage_tier) or not (1 <= stage_tier <= 4):
                self.error(f"{where}: stageTier must be int 1..4")
            prompt = question.get("prompt")
            if not isinstance(prompt, str) or not prompt.strip():
                self.error(f"{where}: empty prompt")
                continue
            self.collect_text(f"{where}.prompt", prompt)
            if prompt.casefold() in seen_prompts:
                self.error(f"{where}: duplicate prompt (also quiz[{seen_prompts[prompt.casefold()]}])")
            seen_prompts[prompt.casefold()] = index
            options = question.get("options")
            if not isinstance(options, list) or not (3 <= len(options) <= 4):
                self.error(f"{where}: options must be 3 or 4 strings")
                options = []
            if options:
                if len(set(options)) != len(options):
                    self.error(f"{where}: option strings must be unique")
                if not all(isinstance(option, str) and option.strip() for option in options):
                    self.error(f"{where}: option strings must be non-empty")
            correct_index = question.get("correctIndex")
            if not is_int(correct_index) or not options or not (0 <= correct_index < len(options)):
                self.error(f"{where}: correctIndex must be a valid option index")
                correct_index = None
            explanation = question.get("explanation")
            if not isinstance(explanation, str) or not explanation.strip():
                self.error(f"{where}: empty explanation")
            elif "catalog" not in explanation.casefold():
                self.error(f"{where}: explanation must mention the species catalog")
            lore_refs = question.get("loreRefs")
            if not isinstance(lore_refs, list) or not lore_refs:
                self.error(f"{where}: loreRefs must be a non-empty list")
                continue
            ref_keys: list[str] = []
            for reference in lore_refs:
                match = LORE_REF_RE.fullmatch(str(reference))
                if not match:
                    self.error(f"{where}: loreRef {reference!r} must match species_catalog:<normalized name>")
                    continue
                ref_keys.append(match.group(1))
                referenced.add(match.group(1))
            species = None
            for ref_key in ref_keys:
                species = self.catalog_by_key.get(ref_key)
                if species is None:
                    self.error(f"{where}: loreRef species {ref_key!r} not in species_catalog.json")
            if species is None or correct_index is None:
                continue
            species_name = species["name"]
            if species_name not in prompt:
                self.error(f"{where}: prompt must name {species_name!r}")
            lower_prompt = prompt.casefold()
            if "level" in lower_prompt:
                expected_kind = "level"
                expected_value = str(species.get("level", "")).strip()
                if expected_value not in level_pool:
                    self.error(f"{where}: species level {expected_value!r} is not a quiz level value")
                if correct_index is not None and options and options[correct_index] != expected_value:
                    self.error(
                        f"{where}: correct option must be catalog level {expected_value!r}, "
                        f"got {options[correct_index]!r}"
                    )
                allowed = level_pool
            elif "type" in lower_prompt:
                expected_kind = "type"
                expected_value = str(species.get("type", "")).strip()
                if correct_index is not None and options and options[correct_index] != expected_value:
                    self.error(
                        f"{where}: correct option must be catalog species type {expected_value!r}, "
                        f"got {options[correct_index]!r}"
                    )
                allowed = type_pool
            else:
                self.error(f"{where}: prompt must ask about the catalog 'level' or 'type'")
                continue
            if options and any(option not in allowed for option in options):
                self.error(f"{where}: options contain a value that is not a catalog {expected_kind} value")
            expected_tier = generator.stage_tier_for_level(species.get("level"))
            if question.get("stageTier") != expected_tier:
                self.error(
                    f"{where}: stageTier {question.get('stageTier')!r} does not match catalog level "
                    f"{species.get('level')!r} -> {expected_tier}"
                )
            if isinstance(explanation, str) and expected_value and expected_value not in explanation:
                self.error(f"{where}: explanation must contain the catalog fact {expected_value!r}")
        self.counts["quiz questions"] = len(questions)
        self.counts["quiz unique prompts"] = len(seen_prompts)
        self.counts["quiz referenced species"] = len(referenced)
        self._quiz_referenced_keys = referenced

    # -- negotiation --------------------------------------------------------
    def check_flow(
        self,
        where: str,
        flow,
        *,
        allowed_outcomes: set[str],
        expected_start: str | None = None,
        require_choice_nodes: int = 0,
        allows_demand: bool = True,
        smt_style: bool = False,
    ) -> dict | None:
        if not isinstance(flow, dict):
            self.error(f"{where}: flow must be an object")
            return None
        nodes = flow.get("nodes")
        if not isinstance(nodes, dict) or not nodes:
            self.error(f"{where}: nodes must be a non-empty object")
            return None
        start = flow.get("start")
        if not isinstance(start, str) or start not in nodes:
            self.error(f"{where}: start must reference an existing node, got {start!r}")
            return None
        if expected_start and start != expected_start:
            self.error(f"{where}: start must be {expected_start!r}")
        choice_nodes = 0
        for node_id, node in nodes.items():
            node_where = f"{where}.{node_id}"
            if not isinstance(node, dict):
                self.error(f"{node_where}: node must be an object")
                continue
            if node.get("id") != node_id:
                self.error(f"{node_where}: node id must equal its map key")
            text = node.get("text")
            if not isinstance(text, str) or not text.strip():
                self.error(f"{node_where}: empty text")
            else:
                self.collect_text(f"{node_where}.text", text)
                if PLACEHOLDER_RE.search(text):
                    self.error(f"{node_where}: text contains an unresolved placeholder")
            if node.get("speaker", "wild") != "wild":
                self.error(f"{node_where}: speaker must be 'wild'")
            tag = node.get("tag")
            if tag is not None and tag not in NODE_TAGS:
                self.error(f"{node_where}: invalid tag {tag!r}")
            if smt_style:
                topic = node.get("topic")
                if not isinstance(topic, str) or not topic.strip():
                    self.error(f"{node_where}: SMT-style node needs a non-empty topic")
            outcome = node.get("outcome")
            raw_options = node.get("options", [])
            if not isinstance(raw_options, list):
                self.error(f"{node_where}: options must be a list")
                raw_options = []
            if outcome is not None:
                if outcome not in allowed_outcomes:
                    self.error(f"{node_where}: invalid outcome {outcome!r}")
                if raw_options:
                    self.error(f"{node_where}: terminal node must not have options")
            else:
                if not raw_options:
                    self.error(f"{node_where}: non-terminal node needs options")
                else:
                    choice_nodes += 1
                    # The price phase allows a fourth branch (the one-time counter);
                    # all other question nodes stay at 2..3 authored branches.
                    max_branches = 4 if node.get("demand") else 3
                    if smt_style and not (2 <= len(raw_options) <= max_branches):
                        self.error(
                            f"{node_where}: SMT-style question node needs 2..{max_branches} "
                            f"answer branches, got {len(raw_options)}"
                        )
            option_ids: set[str] = set()
            for option in raw_options:
                option_where = f"{node_where}.option"
                if not isinstance(option, dict):
                    self.error(f"{option_where}: option must be an object")
                    continue
                option_id = option.get("id")
                if not isinstance(option_id, str) or not option_id:
                    self.error(f"{option_where}: id must be a non-empty string")
                    continue
                option_where = f"{node_where}.{option_id}"
                if option_id in option_ids:
                    self.error(f"{option_where}: duplicate option id")
                option_ids.add(option_id)
                option_text = option.get("text")
                if not isinstance(option_text, str) or not option_text.strip():
                    self.error(f"{option_where}: empty text")
                else:
                    self.collect_text(f"{option_where}.text", option_text)
                    if PLACEHOLDER_RE.search(option_text):
                        self.error(f"{option_where}: text contains an unresolved placeholder")
                next_node = option.get("next")
                if next_node is not None:
                    if not isinstance(next_node, str) or next_node not in nodes:
                        self.error(f"{option_where}: next {next_node!r} does not exist in flow")
                elif outcome is None:
                    self.error(f"{option_where}: non-terminal option needs a next node")
                if smt_style:
                    reaction = option.get("reaction")
                    if not isinstance(reaction, str) or not reaction.strip():
                        self.error(f"{option_where}: option needs the Digimon's authored reaction")
                    else:
                        self.collect_text(f"{option_where}.reaction", reaction)
                        if PLACEHOLDER_RE.search(reaction):
                            self.error(f"{option_where}: reaction contains an unresolved placeholder")
                        if isinstance(option_text, str) and not reaction_references_answer(
                            option_text, reaction
                        ):
                            self.error(
                                f"{option_where}: reaction does not reference the chosen answer "
                                f"({option_text!r} -> {reaction!r})"
                            )
                    option_topic = option.get("topic")
                    if not isinstance(option_topic, str) or not option_topic.strip():
                        self.error(f"{option_where}: option needs a non-empty topic")
                    elif isinstance(next_node, str) and isinstance(nodes.get(next_node), dict):
                        target_topic = nodes[next_node].get("topic")
                        if option_topic != target_topic:
                            self.error(
                                f"{option_where}: topic {option_topic!r} must equal the target node's "
                                f"topic {target_topic!r} (topic continuity)"
                            )
                requires = option.get("requires")
                if requires is not None and not isinstance(requires, str):
                    self.error(f"{option_where}: requires must be a string when present")
                effects = option.get("effects")
                if effects is None:
                    continue
                if not isinstance(effects, dict):
                    self.error(f"{option_where}: effects must be an object")
                    continue
                unknown = set(effects) - EFFECT_KEYS
                if unknown:
                    self.error(f"{option_where}: unsupported effect keys {sorted(unknown)}")
                if "bits" in effects and not is_int(effects["bits"]):
                    self.error(f"{option_where}: effects.bits must be an int")
                if "itemQuantity" in effects and not is_int(effects["itemQuantity"]):
                    self.error(f"{option_where}: effects.itemQuantity must be an int")
                if "itemId" in effects and effects["itemId"] is not None and not isinstance(effects["itemId"], str):
                    self.error(f"{option_where}: effects.itemId must be a string or null")
                if "persuasionFlag" in effects and not isinstance(effects["persuasionFlag"], str):
                    self.error(f"{option_where}: effects.persuasionFlag must be a string")
                if "depart" in effects and not isinstance(effects["depart"], bool):
                    self.error(f"{option_where}: effects.depart must be a bool")
            # demand object checks
            demand = node.get("demand")
            if demand is not None:
                if not allows_demand:
                    self.error(f"{node_where}: demand is not allowed in this pack")
                elif not isinstance(demand, dict):
                    self.error(f"{node_where}: demand must be an object")
                else:
                    if demand.get("kind") != "bits":
                        self.error(f"{node_where}: demand.kind must be 'bits', got {demand.get('kind')!r}")
                    amount = demand.get("amount")
                    if not is_int(amount) or amount <= 0:
                        self.error(f"{node_where}: demand.amount must be a positive int")
                    elif not any(
                        isinstance(option, dict)
                        and isinstance(option.get("effects"), dict)
                        and option["effects"].get("bits") == -amount
                        for option in raw_options
                    ):
                        self.error(f"{node_where}: no option pays the demanded amount ({-amount} bits)")
        # reachability from start
        reachable: set[str] = set()
        stack = [start]
        while stack:
            node_id = stack.pop()
            if node_id in reachable:
                continue
            reachable.add(node_id)
            node = nodes.get(node_id)
            if not isinstance(node, dict):
                continue
            for option in node.get("options", []) or []:
                if isinstance(option, dict) and isinstance(option.get("next"), str):
                    stack.append(option["next"])
        unreachable = set(nodes) - reachable
        if unreachable:
            self.error(f"{where}: unreachable nodes {sorted(unreachable)[:8]}")
        if require_choice_nodes and choice_nodes < require_choice_nodes:
            self.error(f"{where}: needs >= {require_choice_nodes} authored choice nodes, got {choice_nodes}")
        return flow

    def validate_negotiation(self) -> None:
        pack = self.packs.get("negotiation")
        if pack is None:
            return
        personalities = pack.get("personalities")
        if not isinstance(personalities, dict):
            self.error("negotiation: personalities must be an object")
            return
        expected = {str(number) for number in range(1, 17)}
        if set(personalities) != expected:
            self.error(f"negotiation: personalities must be exactly 1..16, got {sorted(personalities)[:20]}")
        flow_count = 0
        node_count = 0
        option_count = 0
        reaction_count = 0
        nodes_by_tier: dict[int, list[int]] = {1: [], 2: [], 3: [], 4: []}
        depths_by_tier: dict[int, list[int]] = {1: [], 2: [], 3: [], 4: []}
        legacy_meta = {normalized_exact(line): line for line in LEGACY_META_OPTION_TEXTS}
        outcomes: Counter = Counter()
        demands_by_tier: dict[int, list[int]] = {1: [], 2: [], 3: [], 4: []}
        counters_by_tier: dict[int, list[int]] = {1: [], 2: [], 3: [], 4: []}
        depart_tiers: set[int] = set()
        for personality in range(1, 17):
            tiers = personalities.get(str(personality))
            if not isinstance(tiers, dict):
                self.error(f"negotiation[{personality}]: must be an object keyed by tier 1..4")
                continue
            if set(tiers) != {"1", "2", "3", "4"}:
                self.error(f"negotiation[{personality}]: tiers must be exactly 1..4, got {sorted(tiers)}")
            for tier in range(1, 5):
                flow = tiers.get(str(tier))
                if flow is None:
                    continue
                flow_count += 1
                where = f"negotiation[{personality}][{tier}]"
                self.check_flow(
                    where,
                    flow,
                    allowed_outcomes=NEGOTIATION_OUTCOMES,
                    expected_start=f"p{personality}.t{tier}.start",
                    smt_style=True,
                )
                if not isinstance(flow, dict) or not isinstance(flow.get("nodes"), dict):
                    continue
                nodes = flow["nodes"]
                node_count += len(nodes)
                nodes_by_tier[tier].append(len(nodes))
                low, high = TIER_NODE_RANGES[tier]
                if not (low <= len(nodes) <= high):
                    self.error(f"{where}: node count {len(nodes)} outside contract range {low}..{high}")
                tags = {node.get("tag") for node in nodes.values() if isinstance(node, dict)}
                missing_tags = REQUIRED_NEGOTIATION_TAGS - tags
                if missing_tags:
                    self.error(f"{where}: missing required node tags {sorted(missing_tags)}")
                if tier == 1 and "trick" in tags:
                    self.error(f"{where}: T1 must not contain trick nodes")
                if tier >= 2 and "trick" not in tags:
                    self.error(f"{where}: T{tier} must contain a trick node")
                for node_id, node in nodes.items():
                    if not isinstance(node, dict):
                        continue
                    if node.get("tag") == "trick":
                        if not (node.get("text", "").rstrip().endswith(")") and "(" in node.get("text", "")):
                            self.error(f"{where}.{node_id}: trick node needs an authored tell in parentheses")
                    if node.get("demand"):
                        amount = node["demand"].get("amount")
                        if is_int(amount):
                            if node_id.endswith(".counter_deal"):
                                counters_by_tier[tier].append(amount)
                            else:
                                demands_by_tier[tier].append(amount)
                    outcome = node.get("outcome")
                    if outcome:
                        outcomes[outcome] += 1
                        if outcome == "DEPART":
                            depart_tiers.add(tier)
                    for option in node.get("options", []) or []:
                        if not isinstance(option, dict):
                            continue
                        effects = option.get("effects") or {}
                        if effects.get("depart") is True:
                            next_node = nodes.get(option.get("next"))
                            if not isinstance(next_node, dict) or next_node.get("outcome") != "DEPART":
                                self.error(
                                    f"{where}.{node_id}.{option.get('id')}: depart effect must lead to a DEPART node"
                                )
                        option_text = option.get("text")
                        if isinstance(option_text, str):
                            option_count += 1
                            if normalized_exact(option_text) in legacy_meta:
                                self.error(
                                    f"{where}.{node_id}.{option.get('id')}: legacy generic meta option reused "
                                    f"({option_text!r}) in an in-world negotiation"
                                )
                            for pattern in LEGACY_META_OPTION_PATTERNS:
                                if pattern.search(option_text):
                                    self.error(
                                        f"{where}.{node_id}.{option.get('id')}: interface-style meta wording "
                                        f"in option ({option_text!r})"
                                    )
                        reaction = option.get("reaction")
                        if isinstance(reaction, str) and reaction.strip():
                            reaction_count += 1
                # --- one-time counter offer (contentVersion 4) -----------------
                primary_demand_nodes = [
                    (node_key, node)
                    for node_key, node in nodes.items()
                    if isinstance(node, dict)
                    and node.get("demand")
                    and node_key.endswith(".demand")
                ]
                counter_nodes = [
                    (node_key, node)
                    for node_key, node in nodes.items()
                    if isinstance(node, dict) and node_key.endswith(".counter_deal")
                ]
                if len(primary_demand_nodes) != 1:
                    self.error(
                        f"{where}: expected exactly one primary demand node, got {len(primary_demand_nodes)}"
                    )
                if len(counter_nodes) != 1:
                    self.error(f"{where}: expected exactly one counter_deal node, got {len(counter_nodes)}")
                if len(primary_demand_nodes) == 1 and len(counter_nodes) == 1:
                    primary_id, primary_node = primary_demand_nodes[0]
                    counter_id, counter_node = counter_nodes[0]
                    primary_amount = (primary_node.get("demand") or {}).get("amount")
                    counter_amount_value = (counter_node.get("demand") or {}).get("amount")
                    counter_options = [
                        option
                        for option in primary_node.get("options", []) or []
                        if isinstance(option, dict) and option.get("id") == "counter"
                    ]
                    if len(counter_options) != 1:
                        self.error(
                            f"{where}.{primary_id}: expected exactly one 'counter' option, "
                            f"got {len(counter_options)}"
                        )
                    else:
                        counter_option = counter_options[0]
                        if counter_option.get("topic") != "demand":
                            self.error(
                                f"{where}.{primary_id}.counter: counter option topic must be 'demand'"
                            )
                        if counter_option.get("next") != counter_id:
                            self.error(
                                f"{where}.{primary_id}.counter: counter option must lead to {counter_id}"
                            )
                        if counter_option.get("effects"):
                            self.error(
                                f"{where}.{primary_id}.counter: counter ask must not pay anything"
                            )
                    all_counter_options = [
                        option
                        for node in nodes.values()
                        if isinstance(node, dict)
                        for option in node.get("options", []) or []
                        if isinstance(option, dict) and option.get("id") == "counter"
                    ]
                    if len(all_counter_options) != 1:
                        self.error(
                            f"{where}: expected exactly one counter option in the flow, "
                            f"got {len(all_counter_options)}"
                        )
                    if counter_node.get("tag") != "demand":
                        self.error(f"{where}.{counter_id}: counter_deal tag must be 'demand'")
                    if counter_node.get("topic") != "demand":
                        self.error(f"{where}.{counter_id}: counter_deal topic must be 'demand'")
                    counter_node_options = counter_node.get("options", []) or []
                    if not (2 <= len(counter_node_options) <= 3):
                        self.error(
                            f"{where}.{counter_id}: counter_deal needs 2..3 options, "
                            f"got {len(counter_node_options)}"
                        )
                    if any(
                        isinstance(option, dict) and option.get("id") == "counter"
                        for option in counter_node_options
                    ):
                        self.error(f"{where}.{counter_id}: counter_deal must not offer a second counter")
                    if is_int(primary_amount) and is_int(counter_amount_value):
                        low_counter, high_counter = COUNTER_DEMAND_RANGES[tier]
                        if not (low_counter <= counter_amount_value <= high_counter):
                            self.error(
                                f"{where}.{counter_id}: counter demand {counter_amount_value} outside "
                                f"tier counter band {low_counter}..{high_counter}"
                            )
                        if counter_amount_value >= primary_amount:
                            self.error(
                                f"{where}.{counter_id}: counter demand must be below primary "
                                f"({counter_amount_value} >= {primary_amount})"
                            )
                        if not (
                            counter_amount_value * 100 >= primary_amount * 55
                            and counter_amount_value * 100 <= primary_amount * 75
                        ):
                            self.error(
                                f"{where}.{counter_id}: counter demand must be 55-75% of primary "
                                f"({counter_amount_value} vs {primary_amount})"
                            )
                    counter_option_ids = {
                        option.get("id")
                        for option in counter_node_options
                        if isinstance(option, dict)
                    }
                    if not {"pay", "no", "walk"} <= counter_option_ids:
                        self.error(
                            f"{where}.{counter_id}: counter_deal needs pay/no/walk options, "
                            f"got {sorted(counter_option_ids)}"
                        )
                    walk_targets = [
                        option.get("next", "")
                        for option in counter_node_options
                        if isinstance(option, dict) and option.get("id") == "walk"
                    ]
                    if walk_targets and not any(
                        str(target).endswith((".depart", ".paid_leave")) for target in walk_targets
                    ):
                        self.error(
                            f"{where}.{counter_id}: walk option must lead to depart/paid_leave"
                        )
                # --- SMT-style authored-tree checks (contentVersion 3) ---------
                prompt_nodes = {
                    nid: node
                    for nid, node in nodes.items()
                    if isinstance(node, dict) and node.get("options")
                }
                prompt_texts = [node.get("text") for node in prompt_nodes.values()]
                if len(prompt_texts) != len(set(prompt_texts)):
                    self.error(f"{where}: repeated follow-up prompt text within one flow")
                start_node = nodes.get(flow.get("start"))
                if isinstance(start_node, dict):
                    start_targets = {
                        option.get("next")
                        for option in start_node.get("options", []) or []
                        if isinstance(option, dict) and option.get("next")
                    }
                    if len(start_targets) < 2:
                        self.error(f"{where}: opening question must branch into >= 2 follow-up nodes")
                demand_nodes = [
                    (node_key, node)
                    for node_key, node in nodes.items()
                    if isinstance(node, dict) and node.get("demand")
                ]
                if not demand_nodes:
                    self.error(f"{where}: missing demand (price) node")
                for demand_id, demand_node in demand_nodes:
                    if demand_node.get("topic") != "demand":
                        self.error(f"{where}.{demand_id}: demand node topic must be 'demand'")
                reachable_outcomes: set[str] = set()
                visit_stack = [flow["start"]]
                visited: set[str] = set()
                while visit_stack:
                    current = visit_stack.pop()
                    if current in visited:
                        continue
                    visited.add(current)
                    current_node = nodes.get(current)
                    if not isinstance(current_node, dict):
                        continue
                    if current_node.get("outcome"):
                        reachable_outcomes.add(current_node["outcome"])
                    for option in current_node.get("options", []) or []:
                        if isinstance(option, dict) and isinstance(option.get("next"), str):
                            visit_stack.append(option["next"])
                if "JOIN" not in reachable_outcomes:
                    self.error(f"{where}: JOIN is not reachable from start")
                if not ({"REFUSE", "LEAVE"} & reachable_outcomes):
                    self.error(f"{where}: neither REFUSE nor LEAVE is reachable from start")
                # authored depth = number of question exchanges before the price
                demand_ids = {node_key for node_key, _node in demand_nodes}
                depth_seen: set[tuple[str, int]] = set()
                depth_frontier = [(flow["start"], 0)]
                best_depth: int | None = None
                while depth_frontier:
                    current, exchanged = depth_frontier.pop(0)
                    if current in demand_ids:
                        best_depth = exchanged if best_depth is None else min(best_depth, exchanged)
                        continue
                    if (current, exchanged) in depth_seen:
                        continue
                    depth_seen.add((current, exchanged))
                    current_node = nodes.get(current)
                    if not isinstance(current_node, dict):
                        continue
                    increment = 1 if current in prompt_nodes else 0
                    for option in current_node.get("options", []) or []:
                        if isinstance(option, dict) and isinstance(option.get("next"), str):
                            depth_frontier.append((option["next"], exchanged + increment))
                expected_depth = NEGOTIATION_TIER_DEPTH[tier]
                if best_depth != expected_depth:
                    self.error(
                        f"{where}: authored depth {best_depth!r} question exchanges, "
                        f"expected {expected_depth} for tier {tier}"
                    )
                else:
                    depths_by_tier[tier].append(best_depth)
        for outcome in sorted(NEGOTIATION_OUTCOMES):
            if outcomes[outcome] == 0:
                self.error(f"negotiation: outcome {outcome} never appears in the pack")
        # demand scaling: ranges must not overlap across tiers
        for tier in (2, 3, 4):
            previous = demands_by_tier[tier - 1]
            current = demands_by_tier[tier]
            if not previous or not current:
                self.error(f"negotiation: tier {tier} demand coverage missing")
                continue
            if max(previous) >= min(current):
                self.error(
                    f"negotiation: tier {tier - 1} demands (max {max(previous)}) do not scale below "
                    f"tier {tier} demands (min {min(current)})"
                )
        if depart_tiers:
            self.note(
                f"negotiation: DEPART (authored loss) appears only in tier(s) {sorted(depart_tiers)}; "
                "engine must still gate first conversation (R6)"
            )
        else:
            self.warn("negotiation: no DEPART outcome authored (allowed, but D11 content is missing)")
        self.counts["negotiation flows"] = flow_count
        self.counts["negotiation nodes"] = node_count
        self.counts["negotiation options"] = option_count
        self.counts["negotiation reactions"] = reaction_count
        self.counts["negotiation depth per tier"] = dict(NEGOTIATION_TIER_DEPTH)
        self.counts["negotiation nodes per tier"] = {
            tier: (min(values) if min(values) == max(values) else (min(values), max(values)))
            for tier, values in nodes_by_tier.items()
            if values
        }
        self.counts["negotiation demand ranges"] = {
            tier: (min(values), max(values)) for tier, values in demands_by_tier.items() if values
        }
        self.counts["negotiation counters per tier"] = {
            tier: len(values) for tier, values in counters_by_tier.items()
        }
        self.counts["negotiation counter ranges"] = {
            tier: (min(values), max(values)) for tier, values in counters_by_tier.items() if values
        }

    # -- persuasion ---------------------------------------------------------
    def validate_persuasion(self) -> None:
        pack = self.packs.get("persuasion")
        if pack is None:
            return
        personalities = pack.get("personalities")
        if not isinstance(personalities, dict):
            self.error("persuasion: personalities must be an object")
            return
        if set(personalities) != {str(number) for number in range(1, 17)}:
            self.error("persuasion: personalities must be exactly 1..16")
        flow_count = 0
        node_count = 0
        outcomes: Counter = Counter()
        for personality in range(1, 17):
            stages = personalities.get(str(personality))
            if not isinstance(stages, dict):
                self.error(f"persuasion[{personality}]: must be an object keyed by stage 0..3")
                continue
            if set(stages) != {"0", "1", "2", "3"}:
                self.error(f"persuasion[{personality}]: stages must be exactly 0..3, got {sorted(stages)}")
            for stage in range(4):
                flow = stages.get(str(stage))
                if flow is None:
                    continue
                flow_count += 1
                where = f"persuasion[{personality}][{stage}]"
                self.check_flow(
                    where,
                    flow,
                    allowed_outcomes=PERSUASION_OUTCOMES,
                    expected_start=f"p{personality}.s{stage}.start",
                    require_choice_nodes=2,
                    allows_demand=False,
                )
                if not isinstance(flow, dict) or not isinstance(flow.get("nodes"), dict):
                    continue
                nodes = flow["nodes"]
                node_count += len(nodes)
                stage_outcomes = {
                    node.get("outcome") for node in nodes.values() if isinstance(node, dict) and node.get("outcome")
                }
                if stage == 3:
                    if "QUEST_OFFER" not in stage_outcomes:
                        self.error(f"{where}: stage 3 must reach QUEST_OFFER")
                elif "NEXT_STAGE" not in stage_outcomes:
                    self.error(f"{where}: stages 0..2 must reach NEXT_STAGE")
                if "REFUSE" not in stage_outcomes:
                    self.error(f"{where}: missing REFUSE terminal")
                if "DEPART" not in stage_outcomes:
                    self.error(f"{where}: missing DEPART terminal")
                for node_id, node in nodes.items():
                    if not isinstance(node, dict):
                        continue
                    if node.get("demand") is not None:
                        self.error(f"{where}.{node_id}: persuasion must not demand bits/items")
                    for option in node.get("options", []) or []:
                        if not isinstance(option, dict):
                            continue
                        effects = option.get("effects") or {}
                        if "bits" in effects:
                            self.warn(f"{where}.{node_id}.{option.get('id')}: persuasion costs bits (unexpected)")
                    for outcome in stage_outcomes:
                        outcomes[outcome] += 1
        for outcome in sorted(PERSUASION_OUTCOMES):
            if outcomes[outcome] == 0:
                self.error(f"persuasion: outcome {outcome} never appears in the pack")
        self.counts["persuasion flows"] = flow_count
        self.counts["persuasion nodes"] = node_count

    # -- event lines --------------------------------------------------------
    def validate_events(self) -> None:
        pack = self.packs.get("events")
        if pack is None:
            return
        lines = pack.get("lines")
        if not isinstance(lines, list):
            self.error("events: lines must be a list")
            return
        required = set(banks.EVENT_ORDER)
        per_event: Counter = Counter()
        seen_ids: set[str] = set()
        for index, line in enumerate(lines):
            where = f"events[{index}]"
            if not isinstance(line, dict):
                self.error(f"{where}: line must be an object")
                continue
            event = line.get("event")
            if event not in required:
                self.error(f"{where}: unknown event {event!r}")
            else:
                per_event[event] += 1
            line_id = line.get("id")
            if not isinstance(line_id, str) or not line_id:
                self.error(f"{where}: id must be a non-empty string")
            else:
                if line_id in seen_ids:
                    self.error(f"{where}: duplicate id {line_id!r}")
                seen_ids.add(line_id)
                if isinstance(event, str) and not line_id.startswith(f"ev.{event}."):
                    self.error(f"{where}: id must start with ev.{event}.")
            text = line.get("text")
            if not isinstance(text, str) or not text.strip():
                self.error(f"{where}: empty text")
            else:
                self.collect_text(f"{where}.text", text)
                if PLACEHOLDER_RE.search(text):
                    self.error(f"{where}: text contains an unresolved placeholder")
            mood_delta = line.get("moodDelta", 0)
            if not is_int(mood_delta) or not (-2 <= mood_delta <= 2):
                self.error(f"{where}: moodDelta must be int in -2..2, got {mood_delta!r}")
            types = line.get("types")
            if types is not None:
                if (
                    not isinstance(types, list)
                    or not types
                    or not all(is_int(value) and 1 <= value <= 16 for value in types)
                ):
                    self.error(f"{where}: types must be a non-empty list of type numbers 1..16")
        for event in banks.EVENT_ORDER:
            if per_event[event] < 2:
                self.error(f"events: event {event!r} has {per_event[event]} lines (< 2)")
        self.counts["event lines"] = len(lines)
        self.counts["events covered"] = f"{len([e for e in required if per_event[e] >= 2])}/{len(required)}"

    # -- lore ---------------------------------------------------------------
    def validate_lore(self) -> None:
        pack = self.packs.get("lore")
        if pack is None:
            return
        facts = pack.get("facts")
        if not isinstance(facts, list):
            self.error("lore: facts must be a list")
            return
        seen: set[str] = set()
        fact_facts: dict[str, dict] = {}
        for index, fact in enumerate(facts):
            where = f"lore[{index}]"
            if not isinstance(fact, dict):
                self.error(f"{where}: fact must be an object")
                continue
            species_key = fact.get("speciesKey")
            if not isinstance(species_key, str) or not re.fullmatch(r"[a-z0-9]+", species_key):
                self.error(f"{where}: speciesKey must be lowercase letters/digits")
                continue
            if species_key in seen:
                self.error(f"{where}: duplicate speciesKey {species_key!r}")
            seen.add(species_key)
            fact_facts[species_key] = fact
            species = self.catalog_by_key.get(species_key)
            if species is None:
                self.error(f"{where}: speciesKey {species_key!r} not found in species_catalog.json")
                continue
            if fact.get("speciesName") != species["name"]:
                self.error(f"{where}: speciesName must match catalog name {species['name']!r}")
            if generator.normalize_key(species["name"]) != species_key:
                self.error(f"{where}: speciesKey must equal normalize(name) for {species['name']!r}")
            if fact.get("level") != species.get("level"):
                self.error(
                    f"{where}: level {fact.get('level')!r} does not match catalog value {species.get('level')!r}"
                )
            if fact.get("type") != species.get("type"):
                self.error(
                    f"{where}: type {fact.get('type')!r} does not match catalog value {species.get('type')!r}"
                )
            if fact.get("attribute") is not None:
                self.error(f"{where}: attribute must be null (species_catalog.json has no attribute field)")
            if fact.get("source") != "app_species_catalog":
                self.error(f"{where}: source must be 'app_species_catalog'")
            if fact.get("sourceUrl") is not None:
                self.warn(f"{where}: sourceUrl should be null for app-catalog facts")
        required_keys: set[str] = set()
        species_pack = self.packs.get("species")
        if isinstance(species_pack, dict):
            for question in species_pack.get("questions", []) or []:
                if isinstance(question, dict) and isinstance(question.get("speciesKey"), str):
                    required_keys.add(question["speciesKey"])
        required_keys |= getattr(self, "_quiz_referenced_keys", set())
        missing = required_keys - seen
        if missing:
            self.error(f"lore: missing facts for {len(missing)} referenced species: {sorted(missing)[:10]}")
        extra = seen - required_keys
        if extra:
            self.warn(f"lore: {len(extra)} facts for species not referenced by quiz/species packs")
        self.counts["lore facts"] = len(facts)
        self.counts["lore referenced species"] = len(required_keys)

    # -- unmatched report ---------------------------------------------------
    def validate_unmatched_report(self) -> None:
        if not UNMATCHED_REPORT_PATH.exists():
            self.error("unmatched report: tools/digiline/unmatched_species.json is missing")
            return
        try:
            report = json.loads(UNMATCHED_REPORT_PATH.read_text(encoding="utf-8-sig"))
        except Exception as exc:  # noqa: BLE001
            self.error(f"unmatched report: malformed JSON: {exc}")
            return
        if not isinstance(report, dict):
            self.error("unmatched report: root must be an object")
            return
        matched, unmatched, _duplicates = generator.match_species_chats(self.chat_entries, self.catalog)
        expected_unmatched = {entry["characterId"] for entry in unmatched}
        reported_unmatched = {
            entry.get("characterId") for entry in report.get("unmatched", []) if isinstance(entry, dict)
        }
        if report.get("totalChatEntries") != len(self.chat_entries):
            self.error(
                f"unmatched report: totalChatEntries must be {len(self.chat_entries)}, "
                f"got {report.get('totalChatEntries')!r}"
            )
        if report.get("matchedCount") != len(matched):
            self.error(f"unmatched report: matchedCount must be {len(matched)}, got {report.get('matchedCount')!r}")
        if report.get("unmatchedCount") != len(unmatched):
            self.error(
                f"unmatched report: unmatchedCount must be {len(unmatched)}, got {report.get('unmatchedCount')!r}"
            )
        if expected_unmatched != reported_unmatched:
            self.error(
                "unmatched report: unmatched set does not match generator matching "
                f"(missing={sorted(expected_unmatched - reported_unmatched)[:5]}, "
                f"extra={sorted(reported_unmatched - expected_unmatched)[:5]})"
            )
        for entry in report.get("unmatched", []) or []:
            if not isinstance(entry, dict):
                self.error("unmatched report: each unmatched entry must be an object")
                continue
            if not isinstance(entry.get("characterId"), str) or not entry["characterId"]:
                self.error("unmatched report: unmatched entry needs characterId")
            if not isinstance(entry.get("speciesName"), str) or not entry["speciesName"]:
                self.error("unmatched report: unmatched entry needs speciesName")
            if not isinstance(entry.get("reason"), str) or not entry["reason"]:
                self.error("unmatched report: unmatched entry needs a reason")
        aliases = report.get("manualAliases") or {}
        if not isinstance(aliases, dict):
            self.error("unmatched report: manualAliases must be an object")
        else:
            catalog_ids = {entry["id"].split(".")[-1] for entry in self.catalog["species"]}
            for alias, target in aliases.items():
                if not isinstance(alias, str) or not re.fullmatch(r"[a-z0-9]+", alias):
                    self.error(f"unmatched report: manualAlias key {alias!r} is not normalized")
                if target not in catalog_ids:
                    self.error(f"unmatched report: manualAlias {alias!r} target {target!r} is not a catalog id")
        self.counts["unmatched species reported"] = len(unmatched)
        self.counts["manual aliases"] = len(aliases)

    # -- policy lint --------------------------------------------------------
    def validate_language_lint(self) -> None:
        for _index, (label, text) in enumerate(self.authored_texts):
            if GENDERED_RE.search(text):
                self.error(f"language lint: gendered third-person pronoun in {label}: {text!r}")
            sanitized = text
            if self._smt_species_names and SMT_TERM_RE.search(text):
                # Mask catalog species names ("Demon", "Demon (X-Antibody)", ...)
                # before the SMT-terminology check: those are app-catalog facts.
                for species_name in self._smt_species_names:
                    if species_name.casefold() in sanitized.casefold():
                        sanitized = re.sub(re.escape(species_name), " ", sanitized, flags=re.IGNORECASE)
            if SMT_TERM_RE.search(sanitized):
                self.error(f"language lint: SMT term in {label}: {text!r}")
        self._gathered_authored_text_count = len(self.authored_texts)

    def build_reference_corpus(self) -> tuple[set[str], set[str], int]:
        exact: set[str] = set()
        loose: set[str] = set()

        def add_line(value) -> None:
            if not isinstance(value, str) or not value.strip():
                return
            exact.add(normalized_exact(value))
            if len(value.strip()) >= 25:
                loose.add(normalized_loose(value))

        chats = generator.load_json(SPECIES_CHATS_PATH)
        for entry in chats.get("entries", []):
            add_line(entry.get("opening"))
            for exchange in entry.get("exchanges", []) or []:
                if isinstance(exchange, dict):
                    add_line(exchange.get("tamer"))
                    add_line(exchange.get("digimon"))
        reference_count = len(exact)
        digimon_chat = EXPORT_DIR / "digimon_chat.json"
        if digimon_chat.exists():
            try:
                data = generator.load_json(digimon_chat)
                for entry in data.get("entries", []) or []:
                    if isinstance(entry, dict):
                        add_line(entry.get("text"))
                reference_count = len(exact)
            except Exception as exc:  # noqa: BLE001
                self.warn(f"originality: could not read digimon_chat export ({exc})")
        smt = EXPORT_DIR / "demon_negotiation_dialogue_smt5.json"
        if smt.exists():
            try:
                data = generator.load_json(smt)

                def walk(value) -> None:
                    if isinstance(value, dict):
                        for item in value.values():
                            walk(item)
                    elif isinstance(value, list):
                        for item in value:
                            walk(item)
                    else:
                        add_line(value)

                walk(data)
                reference_count = len(exact)
            except Exception as exc:  # noqa: BLE001
                self.warn(f"originality: could not read SMT negotiation export ({exc})")
        return exact, loose, reference_count

    def validate_originality(self) -> None:
        exact, loose, reference_count = self.build_reference_corpus()
        if reference_count == 0:
            self.warn("originality: no reference corpus available; skipped copy check")
            return
        copied = 0
        for label, text in self.authored_texts:
            if normalized_exact(text) in exact:
                copied += 1
                self.error(f"originality: shipped line exactly matches a reference line in {label}: {text!r}")
            elif len(text.strip()) >= 25 and normalized_loose(text) in loose:
                copied += 1
                self.error(f"originality: shipped line matches a reference line after punctuation stripping in {label}: {text!r}")
        self.counts["originality reference lines"] = reference_count
        self.counts["originality flags"] = copied

    # -- driver -------------------------------------------------------------
    def run(self) -> None:
        self.validate_manifest()
        self.load_all_packs()
        self.validate_species()
        self.validate_generic()
        self.validate_quiz()
        self.validate_negotiation()
        self.validate_persuasion()
        self.validate_events()
        self.validate_lore()
        self.validate_unmatched_report()
        self.validate_language_lint()
        self.validate_originality()

    def report(self) -> str:
        lines: list[str] = []
        lines.append("=== DigiLine v2 content pack validation ===")
        lines.append(f"content root : {self.relative(self.content_root)}")
        lines.append(f"locale       : {self.locale}")
        status = "PASS" if not self.errors else "FAIL"
        lines.append(f"status       : {status} ({len(self.errors)} errors, {len(self.warnings)} warnings)")
        lines.append("")
        lines.append("-- counts --")
        for key, value in self.counts.items():
            lines.append(f"  {key}: {value}")
        lines.append(f"  authored strings checked: {getattr(self, '_gathered_authored_text_count', 0)}")
        if self.notes:
            lines.append("")
            lines.append("-- notes --")
            for note in self.notes:
                lines.append(f"  * {note}")
        if self.warnings:
            lines.append("")
            lines.append("-- warnings --")
            for warning in self.warnings:
                lines.append(f"  ! {warning}")
        if self.errors:
            lines.append("")
            lines.append("-- errors --")
            for error in self.errors:
                lines.append(f"  x {error}")
        lines.append("")
        lines.append("OK" if not self.errors else "VALIDATION FAILED")
        return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Validate DigiLine v2 content packs (contract §1 schemas + coverage + policy lint)."
    )
    parser.add_argument(
        "--content-root",
        type=Path,
        default=CONTENT_ROOT,
        help=f"pack root containing manifest.json (default: {CONTENT_ROOT.relative_to(REPO_ROOT)})",
    )
    parser.add_argument(
        "--locale",
        default=LOCALE,
        help="locale directory to validate (default: en)",
    )
    args = parser.parse_args(argv)
    validator = Validator(locale=args.locale, content_root=args.content_root)
    validator.run()
    print(validator.report())
    return 0 if not validator.errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
