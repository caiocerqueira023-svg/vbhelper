#!/usr/bin/env python3
"""Free Talk ("Papo") content validator (workstream A).

Validates every shipped asset under `app/src/main/assets/freechat/v1/en/` against
FREE_CHAT_IMPLEMENTATION_CONTRACT.md §1 + DIGILINE_FREE_TALK_PLAN.md:

  manifest + file layout (intents / species voices / database fallback)
  schema, required keys, id uniqueness, reply length/sentence rules
  duplicate reply texts and duplicate match phrases
  duplicate intent ids, per-intent variant minimums, per-topic tone/stage coverage
  fallback ladder levels 1..4 (+ fallbackId resolution)
  chip refs (top-level <-> intent), chip text retriever consistency
  safety classes S1/S2/S3/PII, normalized terms, EN+PT required coverage
  tone / personality-type / stage-tier coverage (personality-dependent tone)
  assistant-phrase, out-of-world and third-party lints on generated replies
  originality: no 8-word overlap with the catalog profiles, the existing
    DigiLine packs or the legacy species-chat asset
  species_voice: exactly one entry per catalog species, catalog fact atoms only
  database_fallback: last-resort dataset dialogue (count/unique/length/source/
    license/PII/sexual/abusive/assistant row gates)

The database fallback is a documented exception to the in-world originality rule:
it is dataset dialogue for LAST-RESORT use only. Generated replies are checked
strictly and are never mixed with it.

Exit code 0 = clean, 1 = errors (warnings do not fail). Stdlib only; deterministic
output (sorted iteration, no timestamps).

Run: python tools/freechat/validate_freechat.py [--locale en]
     [--root app/src/main/assets/freechat]
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import unicodedata
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
ASSETS_ROOT = REPO_ROOT / "app" / "src" / "main" / "assets"
CATALOG = ASSETS_ROOT / "species_catalog.json"
LEGACY_SPECIES_CHAT = ASSETS_ROOT / "species_chat_examples.json"
DIGILINE_DIR = ASSETS_ROOT / "digiline" / "v1" / "en"

# ---- shipped targets (owner Big v1 direction) vs contract hard floors --------
FULL_INTENTS = 200
FULL_REPLIES = 3200
FULL_MATCH_PHRASES = 1600
CONTRACT_MIN_INTENTS = 60
CONTRACT_MIN_REPLIES = 1000
CONTRACT_MIN_MATCH_PHRASES = 500
EXPECTED_VOICES = 1662
SMOKE_DB_MIN_PAIRS = 500
DB_MIN_PAIRS = 3000
DB_MAX_PAIRS = 5400
DB_MAX_RESPONSE_CHARS = 300
DB_MAX_USER_CHARS = 300

MAX_REPLY_CHARS = 160          # contract hard limit
AUTHORING_TARGET_CHARS = 120   # contract §1 authoring target (warning only)
MAX_SENTENCES = 2
MIN_INTENT_REPLIES = 2         # plan: >=2 variants per intent (hard)
RECOMMENDED_INTENT_REPLIES = 4
MAX_INTENT_CHIPS = 3
ORIGINALITY_SHINGLE = 8

TONES = ["warm", "dry", "courage", "compassion", "insight", "friendship", "playful", "formal"]
CORE_NAMES = {"PHILANTHROPY", "VALOR", "WISDOM", "AMICABILITY"}
MOOD_BANDS = {"LOW", "SETTLED", "POSITIVE"}
LEVEL_TO_TIER = {
    "Baby I": 1, "Baby II": 1, "Child": 1,
    "Adult": 2, "Champion": 2, "Armor": 2, "Hybrid": 2,
    "Perfect": 3, "Ultimate": 4,
}
DEFAULT_TIER = 2

EXPECTED_MANIFEST_FILES = {
    "databaseFallback": "database_fallback.json",
    "intents": "free_chat.json",
    "speciesVoice": "species_voice.json",
}
DB_LICENSE_BY_SOURCE = {
    "cornell-movie-dialogs": "research-only (personal build)",
    "oasst1-en": "Apache-2.0",
    "oasst1-pt": "Apache-2.0",
    "soda": "CC BY 4.0",
}

ALLOWED_PACK_KEYS = {"schemaVersion", "contentVersion", "locale", "intents", "chips",
                     "fallbacks", "safety"}
ALLOWED_INTENT_KEYS = {"id", "topic", "tags", "matchPhrases", "keywords", "conditions",
                       "replies", "chips", "fallbackId"}
ALLOWED_REPLY_KEYS = {"id", "text", "tone", "stageTiers", "types", "moodDelta", "energy",
                      "loreRefs", "authoringStatus"}
ALLOWED_CONDITION_KEYS = {"audience", "stageTiers", "types", "cores", "moodBands", "recentEvents"}
ALLOWED_CHIP_KEYS = {"id", "text", "intentId"}
ALLOWED_FALLBACK_KEYS = {"level", "replies"}
ALLOWED_VOICE_KEYS = {"speciesKey", "speciesName", "level", "type", "moves", "tier"}
ALLOWED_DB_KEYS = {"schemaVersion", "contentVersion", "locale", "source", "pairs"}
ALLOWED_DB_PAIR_KEYS = {"user", "response", "source", "license"}

# Contract §6.5 anti-generic lint (EN + PT assistant stock phrases).
ASSISTANT_BANNED = [
    "as an ai", "i'm an ai", "i am an ai", "i'm just an ai", "i am just an ai",
    "language model", "large language model", "i'm just a program",
    "i am just a program", "i'm here to help", "i am here to help", "how can i help",
    "how may i assist", "let me know if", "i'm sorry, but i can't", "i'm sorry, i can't",
    "i cannot help with", "happy to help", "is there anything else", "as your assistant",
    "virtual assistant", "artificial intelligence", "neural network", "training data",
    "prompt engineering", "não tenho acesso", "nao tenho acesso", "estou aqui para ajudar",
    "posso ajudar com isso", "como posso ajudar", "sou uma ia", "sou um assistente",
    "não sou um robô", "me desculpe, mas não posso",
]
# Real-world/assistant vocabulary that must never reach a generated partner line.
OUT_OF_WORLD_BANNED = [
    "internet", "smartphone", "phone", "wi-fi", "wifi", "website", "wikipedia", "google",
    "netflix", "chatgpt", "openai", "llm", "prompt", "algorithm", "artificial intelligence",
    "machine learning", "politics", "religion", "president", "bitcoin", "nft", "assistant",
    "api", "human", "humans", "real world", "human world", "earth", "school", "homework",
    "teacher", "news", "celebrity", "election", "digimon adventure", "pokemon",
]
THIRD_PARTY_BANNED = ["macca", "netherworld", "compendium", "shin megami", "persona",
                      "negotiation", "smt"]

REQUIRED_SAFETY = {
    "S1": ["idiot", "stupid", "burro", "idiota"],
    "S2": ["sex", "nude", "matar", "kill you"],
    "S3": ["suicid", "me matar", "kill myself"],
    "PII": ["cpf", "address", "endereco", "phone"],
}
PT_SAFETY_MARKERS = {
    "S1": ["burro", "idiota", "imbecil", "otario"],
    "S2": ["matar", "sangue", "estupro", "abusar"],
    "S3": ["me matar", "suicidio", "quero morrer", "nao quero viver"],
    "PII": ["cpf", "endereco", "telefone", "senha", "documento"],
}

# Database fallback row gates (dataset dialogue; LAST RESORT only).
DB_ASSISTANT_BANNED = [
    "as an ai", "i'm an ai", "i am an ai", "language model", "neural network",
    "i'm here to help", "i am here to help", "how can i help", "how may i assist",
    "let me know if", "i'm sorry, but i can't", "i cannot help", "i can't help with",
    "artificial intelligence", "openai", "chatgpt", "virtual assistant", "happy to help",
    "is there anything else", "training data",
]
DB_SEXUAL_TERMS = [
    "sex", "sexy", "sexual", "nude", "naked", "porn", "erotic", "orgasm", "masturbat",
    "blowjob", "condom", "breasts", "boobs", "hookup", "hook up", "make love",
    "virginity", "virgin", "prostitute", "stripper", "seduce",
]
DB_ABUSE_TERMS = [
    "rape", "raped", "molest", "suicide", "kill yourself", "murder", "stab", "shoot",
    "gun", "knife", "torture", "beat you", "hit you", "hurt you", "threaten",
    "i'll kill", "i will kill", "kill you", "you're dead", "you are dead", "shut up",
    "i hate you", "bitch", "asshole", "bastard", "dick", "pussy", "cunt", "whore",
    "slut", "idiot", "stupid", "moron",
]
DB_ROOT_BANNED = {
    "fuck": "profanity", "shit": "profanity", "bullshit": "profanity",
    "goddamn": "profanity", "damn": "profanity", "motherfuck": "profanity",
    "dickhead": "profanity", "nigg": "abusive", "fag": "abusive",
    "kiss": "romance", "making out": "romance",
}
DB_PII_PATTERNS = [
    re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+"),
    re.compile(r"https?://|www\."),
    re.compile(r"(?<!\w)@[A-Za-z0-9_]{2,}"),
    re.compile(r"(?<!\d)(?:\+?\d[\s.-]?){7,}\d(?!\d)"),
    re.compile(r"(?<!\w)\d{3}-\d{2}-\d{4}(?!\w)"),
]
DB_PII_PHRASES = [
    "my address", "where i live", "phone number", "social security", "credit card",
    "my number is", "call me at", "text me at", "email me at",
]


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def load_json(path: Path):
    with path.open(encoding="utf-8-sig") as handle:
        return json.load(handle)


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def normalize_key(value: str) -> str:
    """Contract §1.1 speciesKey normalization (same as the generator)."""
    text = unicodedata.normalize("NFKD", str(value))
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    return re.sub(r"[^a-z0-9]", "", text.lower())


def normalize_loose(value: str) -> str:
    text = unicodedata.normalize("NFKD", str(value))
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def words(text: str) -> list[str]:
    return normalize_loose(text).split()


def shingles(text: str, size: int) -> set[tuple[str, ...]]:
    tokens = words(text)
    return {tuple(tokens[i:i + size]) for i in range(0, max(0, len(tokens) - size + 1))}


def sentence_count(text: str) -> int:
    return len([part for part in re.split(r"[.!?]+", text) if part.strip()])


def iter_strings(node):
    if isinstance(node, str):
        yield node
    elif isinstance(node, list):
        for item in node:
            yield from iter_strings(item)
    elif isinstance(node, dict):
        for item in node.values():
            yield from iter_strings(item)


def is_int(value) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def utf8_length(text: str) -> int:
    return len(text)


def reference_shingles(size: int = ORIGINALITY_SHINGLE) -> set[tuple[str, ...]]:
    """8-word shingles from every corpus a generated line must not copy."""
    result: set[tuple[str, ...]] = set()
    if CATALOG.exists():
        catalog = load_json(CATALOG)
        species = catalog.get("species") if isinstance(catalog, dict) else catalog
        for record in species or []:
            profile = record.get("profile") if isinstance(record, dict) else None
            if profile:
                result |= shingles(profile, size)
    if LEGACY_SPECIES_CHAT.exists():
        for text in iter_strings(load_json(LEGACY_SPECIES_CHAT)):
            result |= shingles(text, size)
    if DIGILINE_DIR.exists():
        for path in sorted(DIGILINE_DIR.glob("*.json")):
            for text in iter_strings(load_json(path)):
                result |= shingles(text, size)
    return result


def catalog_tier(level) -> int:
    if not level or str(level).strip() in {"", "Unknown", "Species Unknown", "(?)", "None"}:
        return DEFAULT_TIER
    return LEVEL_TO_TIER.get(str(level).strip(), DEFAULT_TIER)


# ---------------------------------------------------------------------------
# Validator
# ---------------------------------------------------------------------------
class Validator:
    def __init__(self, root: Path, locale: str):
        self.root = root
        self.locale = locale
        self.pack_dir = root / "v1" / locale
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.manifest: dict = {}
        self.pack: dict = {}
        self.voices: list[dict] = []
        self.database: dict = {}
        self.catalog_by_key: dict[str, dict] = {}
        self.voice_keys: set[str] = set()
        self.reply_ids: dict[str, str] = {}
        self.reply_texts: dict[str, str] = {}
        self.intent_ids: set[str] = set()
        self.stats: dict = {}

    # -- reporting --------------------------------------------------------
    def error(self, message: str) -> None:
        self.errors.append(message)

    def warn(self, message: str) -> None:
        self.warnings.append(message)

    # -- loading ----------------------------------------------------------
    def load_inputs(self) -> bool:
        if not self.pack_dir.is_dir():
            self.error(f"missing pack directory: {self.pack_dir}")
            return False
        manifest_path = self.pack_dir / "manifest.json"
        if not manifest_path.is_file():
            self.error(f"missing manifest: {manifest_path}")
            return False
        self.manifest = load_json(manifest_path)
        files = self.manifest.get("files")
        if not isinstance(files, dict):
            self.error("manifest.files must be an object")
            return False
        if files != EXPECTED_MANIFEST_FILES:
            missing = sorted(set(EXPECTED_MANIFEST_FILES) - set(files))
            unexpected = sorted(set(files) - set(EXPECTED_MANIFEST_FILES))
            if missing:
                self.error(f"manifest.files missing keys: {missing}")
            if unexpected:
                self.error(f"manifest.files unexpected keys: {unexpected}")
            for key in sorted(set(files) & set(EXPECTED_MANIFEST_FILES)):
                if files[key] != EXPECTED_MANIFEST_FILES[key]:
                    self.error(
                        f"manifest.files[{key!r}] = {files[key]!r}, "
                        f"expected {EXPECTED_MANIFEST_FILES[key]!r}"
                    )
        found_files = {path.name for path in self.pack_dir.glob("*.json")}
        expected_files = {"manifest.json", *EXPECTED_MANIFEST_FILES.values()}
        extra = sorted(found_files - expected_files)
        if extra:
            self.error(f"unexpected json files in pack dir: {extra}")
        for name in sorted(expected_files):
            if name != "manifest.json" and not (self.pack_dir / name).is_file():
                self.error(f"manifest references missing file: {name}")
                return False
        if not isinstance(self.manifest.get("schemaVersion"), int) or \
                self.manifest.get("schemaVersion") != 1:
            self.error("manifest.schemaVersion must be 1")
        if not is_int(self.manifest.get("contentVersion")) or self.manifest["contentVersion"] < 1:
            self.error("manifest.contentVersion must be a positive integer")
        if self.manifest.get("locale") != self.locale:
            self.error(f"manifest.locale {self.manifest.get('locale')!r} != {self.locale!r}")
        self.pack = load_json(self.pack_dir / files["intents"])
        self.voices = (load_json(self.pack_dir / files["speciesVoice"]).get("voices") or [])
        self.database = load_json(self.pack_dir / files["databaseFallback"])
        if CATALOG.exists():
            catalog = load_json(CATALOG)
            species = catalog.get("species") if isinstance(catalog, dict) else catalog
            for record in species or []:
                self.catalog_by_key[normalize_key(record.get("name", ""))] = record
        else:
            self.error(f"missing species catalog: {CATALOG}")
        return True

    # -- schema helpers ---------------------------------------------------
    def check_keys(self, obj: dict, allowed: set[str], label: str) -> None:
        if not isinstance(obj, dict):
            self.error(f"{label}: expected object")
            return
        unknown = sorted(set(obj) - allowed)
        if unknown:
            self.error(f"{label}: unexpected keys {unknown}")

    # -- reply ------------------------------------------------------------
    def check_reply(self, reply: dict, label: str, reference: set[tuple[str, ...]]) -> None:
        if not isinstance(reply, dict):
            self.error(f"{label}: reply must be an object")
            return
        self.check_keys(reply, ALLOWED_REPLY_KEYS, label)
        reply_id = reply.get("id")
        if not isinstance(reply_id, str) or not re.fullmatch(r"[a-z0-9_.]+\.r[0-9]+", reply_id):
            self.error(f"{label}: invalid reply id {reply_id!r}")
            return
        if reply_id in self.reply_ids:
            self.error(f"duplicate reply id {reply_id!r} (also {self.reply_ids[reply_id]})")
            return
        self.reply_ids[reply_id] = label
        text = reply.get("text")
        if not isinstance(text, str) or not text.strip():
            self.error(f"{reply_id}: empty reply text")
            return
        if text != text.strip():
            self.error(f"{reply_id}: leading/trailing whitespace in reply text")
        if "  " in text:
            self.error(f"{reply_id}: repeated spaces in reply text")
        if "{" in text or "}" in text:
            self.error(f"{reply_id}: unresolved template slot in reply text")
        length = utf8_length(text)
        if length > MAX_REPLY_CHARS:
            self.error(f"{reply_id}: {length} chars > contract max {MAX_REPLY_CHARS}")
        elif length > AUTHORING_TARGET_CHARS:
            self.warn(f"{reply_id}: {length} chars > authoring target {AUTHORING_TARGET_CHARS}")
        sentences = sentence_count(text)
        if sentences < 1:
            self.error(f"{reply_id}: no sentence terminator")
        elif sentences > MAX_SENTENCES:
            self.error(f"{reply_id}: {sentences} sentences > {MAX_SENTENCES}")
        if not re.search(r"[.!?]$", text):
            self.warn(f"{reply_id}: does not end with sentence punctuation")
        tone = reply.get("tone")
        if tone not in TONES:
            self.error(f"{reply_id}: missing/invalid tone {tone!r}")
        if not isinstance(reply.get("authoringStatus"), str) or \
                reply.get("authoringStatus") != "draft":
            self.error(
                f"{reply_id}: authoringStatus must be 'draft' (got {reply.get('authoringStatus')!r})"
            )
        stage_tiers = reply.get("stageTiers")
        if not isinstance(stage_tiers, list) or any(
                not is_int(tier) or tier not in (1, 2, 3, 4) for tier in stage_tiers):
            self.error(f"{reply_id}: invalid stageTiers {stage_tiers!r}")
        types = reply.get("types")
        if not isinstance(types, list) or any(
                not is_int(value) or not 1 <= value <= 16 for value in types):
            self.error(f"{reply_id}: invalid personality types {types!r}")
        mood_delta = reply.get("moodDelta")
        if not is_int(mood_delta) or not -1 <= mood_delta <= 1:
            self.error(f"{reply_id}: invalid moodDelta {mood_delta!r}")
        energy = reply.get("energy")
        if not is_int(energy) or not 0 <= energy <= 2:
            self.error(f"{reply_id}: invalid energy {energy!r}")
        lore_refs = reply.get("loreRefs")
        if not isinstance(lore_refs, list):
            self.error(f"{reply_id}: loreRefs must be a list")
            lore_refs = []
        for reference_key in lore_refs:
            if normalize_key(str(reference_key)) not in self.voice_keys:
                self.error(f"{reply_id}: loreRef {reference_key!r} does not resolve to a species key")
        normalized = normalize_loose(text)
        if normalized in self.reply_texts:
            self.error(
                f"{reply_id}: duplicate normalized text (also {self.reply_texts[normalized]})"
            )
        else:
            self.reply_texts[normalized] = reply_id
        low = text.lower()
        for banned in ASSISTANT_BANNED:
            if banned in normalize_loose(text):
                self.error(f"{reply_id}: assistant-phrase lint hit {banned!r}")
        for banned in OUT_OF_WORLD_BANNED:
            if re.search(rf"(?<![a-z]){re.escape(banned)}(?![a-z])", low):
                self.error(f"{reply_id}: out-of-world lint hit {banned!r}")
        for banned in THIRD_PARTY_BANNED:
            if re.search(rf"(?<![a-z]){re.escape(banned)}(?![a-z])", low):
                self.error(f"{reply_id}: third-party lint hit {banned!r}")
        overlap = shingles(text, ORIGINALITY_SHINGLE) & reference
        if overlap:
            first = sorted(overlap)[0]
            self.error(
                f"{reply_id}: originality: shares {len(overlap)} {ORIGINALITY_SHINGLE}-gram(s) "
                f"with reference corpora (first: {' '.join(first)})"
            )
        self.stats.setdefault("_tones", {})
        self.stats["_tones"][tone] = self.stats["_tones"].get(tone, 0) + 1
        self.stats.setdefault("_types", set()).update(types)
        self.stats.setdefault("_tiers", set()).update(stage_tiers)
        self.stats["_reply_records"] = self.stats.get("_reply_records", 0) + 1

    # -- chip -------------------------------------------------------------
    def check_chip(self, chip: dict, label: str) -> None:
        self.check_keys(chip, ALLOWED_CHIP_KEYS, label)
        chip_id = chip.get("id")
        if not isinstance(chip_id, str) or not re.fullmatch(r"chip\.[a-z0-9_]+", chip_id):
            self.error(f"{label}: invalid chip id {chip_id!r}")
            return
        text = chip.get("text")
        if not isinstance(text, str) or not text.strip():
            self.error(f"{label}: empty chip text")
            return
        if len(text) > AUTHORING_TARGET_CHARS:
            self.warn(f"{label}: chip text longer than {AUTHORING_TARGET_CHARS} chars")
        sentences = sentence_count(text)
        if sentences > MAX_SENTENCES:
            self.error(f"{label}: chip text has more than {MAX_SENTENCES} sentences")
        intent_id = chip.get("intentId")
        if intent_id is not None and (not isinstance(intent_id, str) or intent_id not in self.intent_ids):
            self.error(f"{label}: chip intentId {intent_id!r} does not resolve")

    # -- intents ----------------------------------------------------------
    def check_intents(self, reference: set[tuple[str, ...]]) -> None:
        intents = self.pack.get("intents")
        if not isinstance(intents, list) or not intents:
            self.error("pack.intents must be a non-empty list")
            return
        self.intent_ids = {
            intent.get("id") for intent in intents
            if isinstance(intent.get("id"), str)
        }
        topic_counts: dict[str, int] = {}
        topic_tones: dict[str, set[str]] = {}
        topic_tiers: dict[str, set[int]] = {}
        phrase_owner: dict[str, str] = {}
        for index, intent in enumerate(intents):
            label = f"intents[{index}]"
            if not isinstance(intent, dict):
                self.error(f"{label}: intent must be an object")
                continue
            self.check_keys(intent, ALLOWED_INTENT_KEYS, label)
            intent_id = intent.get("id")
            if not isinstance(intent_id, str) or not re.fullmatch(
                    r"fc\.[a-z0-9_]+\.[0-9]+", intent_id):
                self.error(f"{label}: invalid intent id {intent_id!r}")
                continue
            label = intent_id
            topic = intent.get("topic")
            if not isinstance(topic, str) or not topic.strip():
                self.error(f"{label}: missing topic")
                topic = "?"
            topic_counts[topic] = topic_counts.get(topic, 0) + 1
            tags = intent.get("tags")
            if not isinstance(tags, list) or not tags or any(
                    not isinstance(tag, str) or not tag for tag in tags):
                self.error(f"{label}: tags must be a non-empty string list")
            conditions = intent.get("conditions")
            if not isinstance(conditions, dict):
                self.error(f"{label}: conditions must be an object")
                conditions = {}
            else:
                self.check_keys(conditions, ALLOWED_CONDITION_KEYS, f"{label}.conditions")
                audience = conditions.get("audience")
                if audience is not None and audience not in ("owned", "wild"):
                    self.error(f"{label}: invalid conditions.audience {audience!r}")
                for key, values in (
                        ("stageTiers", {1, 2, 3, 4}),
                        ("types", set(range(1, 17))),
                        ("cores", CORE_NAMES),
                        ("moodBands", MOOD_BANDS),
                ):
                    entries = conditions.get(key, [])
                    if not isinstance(entries, list):
                        self.error(f"{label}: conditions.{key} must be a list")
                        continue
                    for value in entries:
                        if value not in values:
                            self.error(f"{label}: invalid conditions.{key} entry {value!r}")
                if not isinstance(conditions.get("recentEvents", []), list):
                    self.error(f"{label}: conditions.recentEvents must be a list")
            phrases = intent.get("matchPhrases")
            if not isinstance(phrases, list) or not phrases:
                self.error(f"{label}: matchPhrases must be a non-empty list")
                phrases = []
            if len(phrases) < 4:
                self.error(f"{label}: only {len(phrases)} match phrases; need >= 4")
            elif len(phrases) < 6:
                self.warn(f"{label}: {len(phrases)} match phrases; shipped target is 6+")
            for phrase in phrases:
                if not isinstance(phrase, str) or not phrase.strip():
                    self.error(f"{label}: empty match phrase")
                    continue
                if phrase != phrase.strip():
                    self.error(f"{label}: match phrase has leading/trailing whitespace: {phrase!r}")
                if len(phrase) > 90:
                    self.error(f"{label}: match phrase too long ({len(phrase)} chars): {phrase!r}")
                key = normalize_loose(phrase)
                if not key:
                    self.error(f"{label}: match phrase normalizes empty: {phrase!r}")
                    continue
                if key in phrase_owner:
                    self.error(
                        f"{label}: duplicate match phrase {phrase!r} "
                        f"(also on {phrase_owner[key]})"
                    )
                else:
                    phrase_owner[key] = label
            keywords = intent.get("keywords")
            if not isinstance(keywords, list) or not keywords:
                self.error(f"{label}: keywords must be a non-empty list")
                keywords = []
            keyword_seen: set[str] = set()
            for keyword in keywords:
                if not isinstance(keyword, str) or not keyword:
                    self.error(f"{label}: empty keyword")
                    continue
                if keyword != normalize_loose(keyword) or " " in keyword:
                    self.error(f"{label}: keyword {keyword!r} must be a normalized single token")
                if keyword in keyword_seen:
                    self.error(f"{label}: duplicate keyword {keyword!r}")
                keyword_seen.add(keyword)
            replies = intent.get("replies")
            if not isinstance(replies, list) or not replies:
                self.error(f"{label}: replies must be a non-empty list")
                replies = []
            if len(replies) < MIN_INTENT_REPLIES:
                self.error(f"{label}: only {len(replies)} replies; need >= {MIN_INTENT_REPLIES}")
            elif len(replies) < RECOMMENDED_INTENT_REPLIES:
                self.warn(f"{label}: only {len(replies)} replies; recommended {RECOMMENDED_INTENT_REPLIES}+")
            for reply_index, reply in enumerate(replies):
                self.check_reply(reply, f"{label}.replies[{reply_index}]", reference)
                if isinstance(reply, dict):
                    topic_tones.setdefault(topic, set()).add(reply.get("tone"))
                    topic_tiers.setdefault(topic, set()).update(reply.get("stageTiers") or [])
            chips = intent.get("chips")
            if not isinstance(chips, list):
                self.error(f"{label}: chips must be a list")
                chips = []
            if len(chips) > MAX_INTENT_CHIPS:
                self.error(f"{label}: {len(chips)} chips > max {MAX_INTENT_CHIPS}")
            chip_ids = [chip.get("id") for chip in chips if isinstance(chip, dict)]
            if len(set(chip_ids)) != len(chip_ids):
                self.error(f"{label}: duplicate chip refs")
            for chip_index, chip in enumerate(chips):
                self.check_chip(chip, f"{label}.chips[{chip_index}]")
            fallback_id = intent.get("fallbackId")
            if fallback_id is not None:
                if not isinstance(fallback_id, str) or not re.fullmatch(
                        r"fallback\.[1-4]", fallback_id):
                    self.error(f"{label}: invalid fallbackId {fallback_id!r}")
        # per-topic coverage
        for topic in sorted(topic_counts):
            if topic_counts[topic] < 2:
                self.error(f"topic {topic!r}: only {topic_counts[topic]} intent(s)")
            tones = {tone for tone in topic_tones.get(topic, set()) if tone}
            if len(tones) < 6:
                self.error(
                    f"topic {topic!r}: only {len(tones)} tones; personality-dependent "
                    "variation needs 6+"
                )
            tiers = topic_tiers.get(topic, set())
            if len(tiers & {1, 2, 3, 4}) < 2:
                self.error(f"topic {topic!r}: only stage tiers {sorted(tiers)}; need 2+")
        self.stats["_topics"] = len(topic_counts)

    # -- chips ------------------------------------------------------------
    def check_chips(self) -> None:
        chips = self.pack.get("chips")
        if not isinstance(chips, list) or not chips:
            self.error("pack.chips must be a non-empty list")
            return
        chip_by_id: dict[str, dict] = {}
        chip_texts: dict[str, str] = {}
        for index, chip in enumerate(chips):
            label = f"chips[{index}]"
            if not isinstance(chip, dict):
                self.error(f"{label}: chip must be an object")
                continue
            self.check_chip(chip, label)
            chip_id = chip.get("id")
            if not isinstance(chip_id, str):
                continue
            if chip_id in chip_by_id:
                self.error(f"duplicate global chip id {chip_id!r}")
            chip_by_id[chip_id] = chip
            normalized = normalize_loose(chip.get("text", ""))
            if normalized in chip_texts:
                self.error(
                    f"duplicate chip text {chip.get('text')!r} "
                    f"(also {chip_texts[normalized]})"
                )
            else:
                chip_texts[normalized] = chip_id
        # per-intent chip refs must resolve to a top-level chip with identical content
        intents = self.pack.get("intents") or []
        for intent in intents:
            intent_id = intent.get("id") if isinstance(intent, dict) else "?"
            for chip in intent.get("chips") or []:
                if not isinstance(chip, dict):
                    continue
                chip_id = chip.get("id")
                if chip_id not in chip_by_id:
                    self.error(f"{intent_id}: chip ref {chip_id!r} does not resolve")
                    continue
                if chip_by_id[chip_id] != chip:
                    self.error(f"{intent_id}: chip ref {chip_id!r} differs from the global chip")
                target_id = chip.get("intentId")
                target = next(
                    (item for item in intents if item.get("id") == target_id), None
                )
                if target is None:
                    self.error(f"chip {chip_id!r}: intentId {target_id!r} does not resolve")
                    continue
                target_phrases = {normalize_loose(phrase) for phrase in
                                  target.get("matchPhrases") or []}
                if normalize_loose(chip.get("text", "")) not in target_phrases:
                    self.warn(
                        f"chip {chip_id!r}: text {chip.get('text')!r} is not a match phrase of "
                        f"{target_id} (retriever consistency)"
                    )
        self.stats["_chips"] = len(chip_by_id)

    # -- fallbacks --------------------------------------------------------
    def check_fallbacks(self, reference: set[tuple[str, ...]]) -> None:
        fallbacks = self.pack.get("fallbacks")
        if not isinstance(fallbacks, list):
            self.error("pack.fallbacks must be a list")
            return
        levels: dict[int, dict] = {}
        for index, fallback in enumerate(fallbacks):
            label = f"fallbacks[{index}]"
            if not isinstance(fallback, dict):
                self.error(f"{label}: fallback must be an object")
                continue
            self.check_keys(fallback, ALLOWED_FALLBACK_KEYS, label)
            level = fallback.get("level")
            if not is_int(level) or level not in (1, 2, 3, 4):
                self.error(f"{label}: invalid fallback level {level!r}")
                continue
            if level in levels:
                self.error(f"duplicate fallback level {level}")
            levels[level] = fallback
            replies = fallback.get("replies")
            if not isinstance(replies, list) or not replies:
                self.error(f"fallback level {level}: replies must be a non-empty list")
                continue
            if len(replies) < 2:
                self.error(f"fallback level {level}: only {len(replies)} lines")
            elif len(replies) < 6:
                self.warn(f"fallback level {level}: only {len(replies)} lines; target 6+")
            for reply_index, reply in enumerate(replies):
                self.check_reply(reply, f"fallback.{level}.replies[{reply_index}]", reference)
                expect_prefix = f"fallback.{level}.r"
                reply_id = reply.get("id") if isinstance(reply, dict) else ""
                if isinstance(reply_id, str) and not reply_id.startswith(expect_prefix):
                    self.error(f"fallback level {level}: reply id {reply_id!r} wrong prefix")
        missing = sorted({1, 2, 3, 4} - set(levels))
        if missing:
            self.error(f"missing fallback levels: {missing}")
        self.stats["_fallback_lines"] = sum(
            len(fallback.get("replies") or []) for fallback in levels.values()
        )
        # fallbackId resolution
        for intent in self.pack.get("intents") or []:
            fallback_id = intent.get("fallbackId") if isinstance(intent, dict) else None
            if fallback_id is None:
                continue
            match = re.fullmatch(r"fallback\.([1-4])", str(fallback_id))
            if not match or int(match.group(1)) not in levels:
                self.error(f"{intent.get('id')}: fallbackId {fallback_id!r} unresolved")

    # -- safety -----------------------------------------------------------
    def check_safety(self) -> None:
        safety = self.pack.get("safety")
        if not isinstance(safety, dict):
            self.error("pack.safety must be an object")
            return
        required = set(REQUIRED_SAFETY)
        unknown = sorted(set(safety) - required)
        if unknown:
            self.warn(f"unexpected safety classes: {unknown}")
        all_terms: dict[str, str] = {}
        for klass in sorted(required):
            terms = safety.get(klass)
            if not isinstance(terms, list) or not terms:
                self.error(f"safety class {klass} missing/empty")
                continue
            seen: set[str] = set()
            for term in terms:
                if not isinstance(term, str) or not term.strip():
                    self.error(f"safety {klass}: empty term")
                    continue
                if term != term.strip() or term != term.lower():
                    self.error(f"safety {klass}: term {term!r} must be lowercase/trimmed")
                if any(ord(ch) > 127 for ch in term):
                    self.error(f"safety {klass}: term {term!r} must be ASCII (normalized)")
                if term != normalize_loose(term):
                    self.error(f"safety {klass}: term {term!r} is not in normalizer form")
                if term in seen:
                    self.error(f"safety {klass}: duplicate term {term!r}")
                seen.add(term)
                if term in all_terms and all_terms[term] != klass:
                    self.warn(
                        f"safety term {term!r} appears in both {all_terms[term]} and {klass}"
                    )
                all_terms[term] = klass
            missing_required = [term for term in REQUIRED_SAFETY[klass] if term not in seen]
            if missing_required:
                self.error(f"safety {klass}: missing contract terms {missing_required}")
            if not any(term in seen for term in PT_SAFETY_MARKERS[klass]):
                self.error(f"safety {klass}: missing basic PT (pt-BR) safety terms")
            self.stats[f"_safety.{klass}"] = len(seen)

    # -- species voice ----------------------------------------------------
    def check_voices(self) -> None:
        if not isinstance(self.voices, list):
            self.error("species_voice.voices must be a list")
            return
        if len(self.voices) != EXPECTED_VOICES:
            self.error(f"species voices: {len(self.voices)} != {EXPECTED_VOICES}")
        catalog_keys = set(self.catalog_by_key)
        seen_keys: set[str] = set()
        ordered: list[str] = []
        for index, voice in enumerate(self.voices):
            label = f"voices[{index}]"
            if not isinstance(voice, dict):
                self.error(f"{label}: voice must be an object")
                continue
            self.check_keys(voice, ALLOWED_VOICE_KEYS, label)
            species_key = voice.get("speciesKey")
            species_name = voice.get("speciesName")
            if not isinstance(species_key, str) or not normalize_key(species_key):
                self.error(f"{label}: missing/invalid speciesKey")
                continue
            key = normalize_key(species_key)
            if key in seen_keys:
                self.error(f"{label}: duplicate speciesKey {species_key!r}")
            seen_keys.add(key)
            ordered.append(species_key)
            if not isinstance(species_name, str) or not species_name.strip():
                self.error(f"{label}: missing speciesName")
                continue
            catalog_entry = self.catalog_by_key.get(key)
            if catalog_entry is None:
                self.error(f"{label}: speciesKey {species_key!r} not in catalog")
                continue
            if species_name != catalog_entry.get("name"):
                self.error(
                    f"{label}: speciesName {species_name!r} != catalog "
                    f"{catalog_entry.get('name')!r}"
                )
            level = voice.get("level")
            if level is not None and level != catalog_entry.get("level"):
                self.error(f"{label}: level {level!r} != catalog level")
            voice_type = voice.get("type")
            if voice_type is not None and voice_type != catalog_entry.get("type"):
                self.error(f"{label}: type {voice_type!r} != catalog type")
            tier = voice.get("tier")
            if not is_int(tier) or tier != catalog_tier(catalog_entry.get("level")):
                self.error(f"{label}: tier {tier!r} does not match catalog level")
            moves = voice.get("moves")
            if not isinstance(moves, list):
                self.error(f"{label}: moves must be a list")
                continue
            catalog_moves = [str(move) for move in catalog_entry.get("specialMoves") or []]
            seen_moves: set[str] = set()
            for move in moves:
                if not isinstance(move, str) or not move.strip():
                    self.error(f"{label}: empty move")
                    continue
                if move not in catalog_moves:
                    self.error(f"{label}: move {move!r} is not a catalog special move")
                if move in seen_moves:
                    self.error(f"{label}: duplicate move {move!r}")
                seen_moves.add(move)
        missing_catalog = sorted(catalog_keys - seen_keys)
        if missing_catalog:
            self.error(
                f"species voices missing {len(missing_catalog)} catalog species "
                f"(first: {missing_catalog[:5]})"
            )
        self.voice_keys = seen_keys
        if ordered != sorted(ordered):
            self.warn("species voices are not sorted by speciesKey (deterministic style)")
        self.stats["_voices"] = len(self.voices)

    # -- database fallback ------------------------------------------------
    def check_database(self, smoke: bool) -> None:
        database = self.database
        if not isinstance(database, dict):
            self.error("database_fallback.json must be an object")
            return
        self.check_keys(database, ALLOWED_DB_KEYS, "database_fallback")
        if database.get("schemaVersion") != 1:
            self.error("database_fallback.schemaVersion must be 1")
        if database.get("contentVersion") != 1:
            self.error("database_fallback.contentVersion must be 1")
        if database.get("locale") != self.locale:
            self.error("database_fallback.locale must match the pack locale")
        if not isinstance(database.get("source"), str) or not database.get("source").strip():
            self.error("database_fallback.source missing/empty")
        pairs = database.get("pairs")
        if not isinstance(pairs, list):
            self.error("database_fallback.pairs must be a list")
            return
        floor = SMOKE_DB_MIN_PAIRS if smoke else DB_MIN_PAIRS
        if len(pairs) < floor:
            self.error(f"database_fallback: {len(pairs)} pairs < {floor}")
        if len(pairs) > DB_MAX_PAIRS:
            self.warn(f"database_fallback: {len(pairs)} pairs above shipped cap {DB_MAX_PAIRS}")
        seen_pairs: dict[tuple[str, str], int] = {}
        sources: dict[str, int] = {}
        licenses: dict[str, int] = {}
        for index, pair in enumerate(pairs):
            label = f"database_fallback.pairs[{index}]"
            if not isinstance(pair, dict):
                self.error(f"{label}: pair must be an object")
                continue
            self.check_keys(pair, ALLOWED_DB_PAIR_KEYS, label)
            user = pair.get("user")
            response = pair.get("response")
            source = pair.get("source")
            license_text = pair.get("license")
            if not isinstance(user, str) or len(user.strip()) < 2:
                self.error(f"{label}: empty/too-short user text")
                continue
            if not isinstance(response, str) or len(response.strip()) < 2:
                self.error(f"{label}: empty/too-short response text")
                continue
            if len(response) > DB_MAX_RESPONSE_CHARS:
                self.error(
                    f"{label}: response {len(response)} chars > {DB_MAX_RESPONSE_CHARS}"
                )
            if len(user) > DB_MAX_USER_CHARS:
                self.error(f"{label}: user {len(user)} chars > {DB_MAX_USER_CHARS}")
            if not isinstance(source, str) or not source.strip():
                self.error(f"{label}: missing source")
            if not isinstance(license_text, str) or not license_text.strip():
                self.error(f"{label}: missing license")
            if isinstance(source, str) and source in DB_LICENSE_BY_SOURCE and \
                    license_text != DB_LICENSE_BY_SOURCE[source]:
                self.error(
                    f"{label}: license {license_text!r} does not match source {source!r}"
                )
            key = (normalize_loose(user), normalize_loose(response))
            if key in seen_pairs:
                self.error(f"{label}: duplicate normalized pair (also pairs[{seen_pairs[key]}])")
            seen_pairs[key] = index
            if isinstance(source, str):
                sources[source] = sources.get(source, 0) + 1
            if isinstance(license_text, str):
                licenses[license_text] = licenses.get(license_text, 0) + 1
            # dataset-row gates: last-resort dialogue must still be safe
            for text, where in ((user, "user"), (response, "response")):
                normalized = normalize_loose(text)
                for root, reason in DB_ROOT_BANNED.items():
                    if root in normalized:
                        self.error(f"{label}: {reason} content in {where}")
                for term in DB_SEXUAL_TERMS:
                    if re.search(rf"(?<![a-z]){re.escape(term)}(?![a-z])", normalized):
                        self.error(f"{label}: sexual content in {where}")
                for term in DB_ABUSE_TERMS:
                    if re.search(rf"(?<![a-z]){re.escape(term)}(?![a-z])", normalized):
                        self.error(f"{label}: abusive content in {where}")
                for pattern in DB_PII_PATTERNS:
                    if pattern.search(text):
                        self.error(f"{label}: PII-like content in {where}")
                for phrase in DB_PII_PHRASES:
                    if phrase in normalized:
                        self.error(f"{label}: PII-like content in {where}")
            for banned in DB_ASSISTANT_BANNED:
                if banned in normalize_loose(response):
                    self.error(f"{label}: assistant-phrase in fallback response {banned!r}")
        self.stats["_db_pairs"] = len(pairs)
        self.stats["_db_sources"] = {key: sources[key] for key in sorted(sources)}
        self.stats["_db_licenses"] = {key: licenses[key] for key in sorted(licenses)}

    # -- top-level --------------------------------------------------------
    def validate(self) -> bool:
        if not self.load_inputs():
            return False
        self.stats = {}
        self.manifest  # loaded
        # pack header
        self.check_keys(self.pack, ALLOWED_PACK_KEYS, "pack")
        if self.pack.get("schemaVersion") != 1:
            self.error("pack.schemaVersion must be 1")
        if self.pack.get("locale") != self.locale:
            self.error("pack.locale must match the manifest locale")
        if self.pack.get("contentVersion") != self.manifest.get("contentVersion"):
            self.error("pack.contentVersion != manifest.contentVersion")
        reference = reference_shingles()
        self.stats["_referenceShingles"] = len(reference)
        # voices first: reply loreRefs/chip checks need the key set
        self.check_voices()
        self.check_intents(reference)
        self.check_chips()
        self.check_fallbacks(reference)
        self.check_safety()
        intents = self.pack.get("intents") or []
        replies = [reply for intent in intents for reply in intent.get("replies") or []]
        phrases = [phrase for intent in intents for phrase in intent.get("matchPhrases") or []]
        self.stats["_intents"] = len(intents)
        self.stats["_replies"] = len(replies)
        self.stats["_match_phrases"] = len(phrases)
        smoke = len(intents) < FULL_INTENTS
        if smoke:
            if self.stats["_intents"] < CONTRACT_MIN_INTENTS:
                self.error(f"too few intents: {self.stats['_intents']} < {CONTRACT_MIN_INTENTS}")
            if self.stats["_replies"] < CONTRACT_MIN_REPLIES:
                self.error(f"too few replies: {self.stats['_replies']} < {CONTRACT_MIN_REPLIES}")
            if self.stats["_match_phrases"] < CONTRACT_MIN_MATCH_PHRASES:
                self.error(
                    f"too few match phrases: {self.stats['_match_phrases']} "
                    f"< {CONTRACT_MIN_MATCH_PHRASES}"
                )
            self.warn(
                f"SMOKE pack: {self.stats['_intents']} intents < shipped target {FULL_INTENTS}; "
                "contract floors enforced instead of the Big v1 targets"
            )
        else:
            if self.stats["_intents"] < FULL_INTENTS:
                self.error(f"too few intents: {self.stats['_intents']} < {FULL_INTENTS}")
            if self.stats["_replies"] < FULL_REPLIES:
                self.error(f"too few replies: {self.stats['_replies']} < {FULL_REPLIES}")
            if self.stats["_match_phrases"] < FULL_MATCH_PHRASES:
                self.error(
                    f"too few match phrases: {self.stats['_match_phrases']} "
                    f"< {FULL_MATCH_PHRASES}"
                )
        self.check_database(smoke)
        tones = {tone: count for tone, count in sorted(self.stats.get("_tones", {}).items())}
        if len(tones) < 6:
            self.error(f"too few tones used: {sorted(tones)} (need >= 6)")
        elif len(tones) < len(TONES):
            self.warn(f"tones used: {sorted(tones)} (all 8 authored tones are expected)")
        types_seen = self.stats.get("_types", set())
        if types_seen != set(range(1, 17)):
            self.error(f"personality type coverage incomplete: {sorted(types_seen)}")
        tiers_seen = self.stats.get("_tiers", set())
        if not {1, 2, 3, 4}.issubset(tiers_seen):
            self.error(f"stage tier coverage incomplete: {sorted(tiers_seen)}")
        # audience coverage
        audiences = {
            intent.get("conditions", {}).get("audience")
            for intent in intents
            if isinstance(intent.get("conditions"), dict)
        }
        if "owned" not in audiences:
            self.error("no intent conditioned on the owned audience")
        self.stats["_tones"] = tones
        self.stats["_types"] = len(types_seen)
        self.stats["_tiersSeen"] = sorted(tiers_seen)
        return True


def print_report(validator: Validator) -> None:
    stats = validator.stats

    def stat(label: str, value) -> None:
        print(f"  {label}: {value}")

    print("Free Talk content stats")
    stat("locale", validator.locale)
    stat("intents", stats.get("_intents", 0))
    stat("replies", stats.get("_replies", 0))
    stat("fallbackLines", stats.get("_fallback_lines", 0))
    stat("replyRecords", stats.get("_reply_records", 0))
    stat("matchPhrases", stats.get("_match_phrases", 0))
    stat("topics", stats.get("_topics", 0))
    stat("chips", stats.get("_chips", 0))
    stat("speciesVoices", stats.get("_voices", 0))
    stat("tones", json.dumps(stats.get("_tones", {}), sort_keys=True))
    stat("personalityTypes", stats.get("_types", 0))
    stat("stageTiers", stats.get("_tiersSeen", []))
    stat("databasePairs", stats.get("_db_pairs", 0))
    stat("databaseSources", json.dumps(stats.get("_db_sources", {}), sort_keys=True))
    stat("databaseLicenses", json.dumps(stats.get("_db_licenses", {}), sort_keys=True))
    stat("originalityShingles", stats.get("_referenceShingles", 0))
    for klass in ("S1", "S2", "S3", "PII"):
        stat(f"safety.{klass}", stats.get(f"_safety.{klass}", 0))
    print("  files:")
    for name in sorted(path.name for path in validator.pack_dir.glob("*.json")):
        path = validator.pack_dir / name
        print(f"    {name}: {path.stat().st_size} bytes sha256 {sha256_file(path)}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate the Free Talk content pack.")
    parser.add_argument("--locale", default="en")
    parser.add_argument("--root", default=str(ASSETS_ROOT / "freechat"))
    args = parser.parse_args()

    validator = Validator(Path(args.root), args.locale)
    try:
        loaded = validator.validate()
    except json.JSONDecodeError as error:
        print(f"error: invalid JSON: {error}", file=sys.stderr)
        return 1
    if loaded:
        print_report(validator)

    if validator.warnings:
        unique = sorted(set(validator.warnings))
        print(f"\n{len(unique)} warning(s):")
        for warning in unique:
            print(f"  - {warning}")
    if validator.errors:
        unique = sorted(set(validator.errors))
        print(f"\n{len(unique)} error(s):")
        for error in unique:
            print(f"  - {error}")
        print("\nFree Talk content pack is INVALID.")
        return 1
    print("\nFree Talk content pack is valid.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
