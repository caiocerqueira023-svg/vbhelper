"""Fast, offline checks for catalog identity, matching and pixel conversion."""
import io
import unittest

from PIL import Image

from tools.build_dot_sprites import (
    artwork_key, canonical_key, documented_aliases, match_sets, preferred_set, rgb565_frame, source_path, stable_sprite_id,
)


class DotSpritePipelineTest(unittest.TestCase):
    def set(self, name="Agumon", stage="2", author="Bandai", roles=None):
        return {"name": name, "stage": stage, "author": author, "source": "Device",
                "style": "Dot", "type": "character_sprite", "format": 1,
                "images": roles or ["https://tero0x.github.io/dmc-sprites/images/test/idle.png"]}

    def test_explicit_regional_alias_preserves_form_qualifiers(self):
        aliases = {"omnimon": "omegamon", "gallantmon": "dukemon"}
        self.assertEqual("omegamon", canonical_key("Omnimon", aliases))
        self.assertNotEqual(canonical_key("Omnimon X", aliases), canonical_key("Omegamon", aliases))
        self.assertNotEqual(canonical_key("Dukemon Crimson Mode", aliases), canonical_key("Dukemon", aliases))

    def test_matching_rejects_wrong_stage_and_high_resolution(self):
        entries = [self.set(), self.set(stage="5"), dict(self.set(), style="Hi-res")]
        self.assertEqual([entries[0]], match_sets("Agumon", "Child", entries, {}))

    def test_alias_match_requires_reviewed_alias(self):
        candidate = self.set("Gallantmon", "5")
        self.assertEqual([], match_sets("Dukemon", "Ultimate", [candidate], {}))
        self.assertEqual([candidate], match_sets("Dukemon", "Ultimate", [candidate], {"gallantmon": "dukemon"}))

    def test_artwork_aliases_keep_existing_identity_and_x_forms_distinct(self):
        aliases = {"diaboromon": "diablomon", "armageddemon": "armagemon"}
        self.assertEqual("diablomon", artwork_key("Diaboromon", aliases))
        self.assertEqual("diablomonxantibody", artwork_key("Diaboromon X", aliases))
        self.assertEqual(artwork_key("Diablomon (X-Antibody)", aliases), artwork_key("Diaboromon (X Antibody)", aliases))
        self.assertNotEqual(artwork_key("Diaboromon X", aliases), artwork_key("Diablomon", aliases))
        candidate = self.set("Diaboromon", "5")
        self.assertEqual([candidate], match_sets("Diablomon", "Ultimate", [candidate], aliases))
        self.assertEqual("diablomon", canonical_key("Diablomon", {}))

    def test_armor_hybrid_and_unknown_are_artwork_categories_not_conflicting_forms(self):
        for stage in ("7", "8", "9"):
            candidate = self.set("Allomon", stage)
            self.assertEqual([candidate], match_sets("Allomon", "Adult", [candidate], {}))
        self.assertEqual([], match_sets("Allomon X", "Adult", [self.set("Allomon", "7")], {}))

    def test_regional_bases_and_color_qualifiers_match_without_collapsing_forms(self):
        aliases = {"vegiemon": "vegimon", "monmon": "koemon"}
        self.assertEqual(artwork_key("Red Vegimon", aliases), artwork_key("RedVegiemon", aliases))
        self.assertEqual(artwork_key("RedVegiemon", aliases), artwork_key("Vegiemon (Red)", aliases))
        self.assertNotEqual(artwork_key("RedVegiemon", aliases), artwork_key("Vegiemon", aliases))
        self.assertEqual("redvegimonxantibody", artwork_key("Vegiemon (Red) X", aliases))
        self.assertEqual("koemon", artwork_key("Monmon", aliases))

    def test_documented_names_require_a_known_unambiguous_target_and_cannot_reassign_existing_names(self):
        groups = {"koemon": [{"name": "Koemon"}], "bearmon": [{"name": "Bearmon"}],
                  "chackmon": [{"name": "Chackmon"}], "other": [{"name": "Othermon"}]}
        pairs = [{"name": "Monmon", "target": "Koemon"}, {"name": "Kumamon", "target": "Bearmon"},
                 {"name": "Kumamon", "target": "Chackmon"}, {"name": "Othermon", "target": "Koemon"},
                 {"name": "Missingmon", "target": "Unknownmon"}]
        self.assertEqual({"Monmon": "koemon"}, documented_aliases(groups, {}, pairs))

    def test_complete_existing_art_is_preferred_over_new_idle_only_animation(self):
        complete = dict(self.set(author="Artist"), id="existing", frames={"idle_1": "a", "atk_2": "b"})
        idle = dict(self.set(), id="supplement", frames={"idle_1": "c", "idle_2": "d"})
        self.assertEqual(complete, preferred_set([idle, complete]))

    def test_ambiguous_source_name_uses_its_documented_stage_mapping(self):
        bear = dict(self.set("Kumamon", "2"), matchName="Bearmon")
        self.assertEqual([bear], match_sets("Bearmon", "Child", [bear], {"kumamon": "chackmon"}))
        self.assertEqual([], match_sets("Chackmon", "Hybrid", [bear], {"kumamon": "chackmon"}))

    def test_unknown_and_special_stages_do_not_invent_numeric_tiers(self):
        candidate = self.set("Fladramon", "3")
        self.assertEqual([candidate], match_sets("Fladramon", "Armor", [candidate], {}))
        self.assertEqual([], match_sets("Fladramon X", "Armor", [candidate], {}))

    def test_official_default_is_stable_independent_of_input_order(self):
        fan = dict(self.set(author="Artist"), id="b", frames={"idle_1": "a"})
        official = dict(self.set(), id="a", frames={"idle_1": "a"})
        self.assertEqual(official, preferred_set([fan, official]))
        self.assertEqual(official, preferred_set([official, fan]))

    def test_sprite_ids_are_stable_reserved_and_distinct(self):
        self.assertEqual(stable_sprite_id("a"), stable_sprite_id("a"))
        self.assertLess(stable_sprite_id("a"), -1)
        self.assertNotEqual(stable_sprite_id("a"), stable_sprite_id("b"))

    def png(self, pixels, width=2, height=1):
        image = Image.new("RGBA", (width, height))
        image.putdata(pixels)
        stream = io.BytesIO()
        image.save(stream, format="PNG")
        return stream.getvalue()

    def test_transparency_and_visible_black_use_native_little_endian_format(self):
        data = rgb565_frame(self.png([(255, 0, 0, 0), (0, 0, 0, 255)]), 2, 1)
        self.assertEqual(b"\xe0\x07\x00\x00", data)

    def test_visible_green_is_not_mistaken_for_transparency(self):
        data = rgb565_frame(self.png([(0, 255, 0, 255), (255, 255, 255, 255)]), 2, 1)
        self.assertNotEqual(b"\xe0\x07", data[:2])
        self.assertEqual(b"\xff\xff", data[2:])

    def test_bad_png_and_inconsistent_dimensions_are_rejected(self):
        with self.assertRaises(ValueError):
            rgb565_frame(b"not a PNG", 48, 48)
        with self.assertRaises(ValueError):
            rgb565_frame(self.png([(0, 0, 0, 255)] * 2), 48, 48)

    def test_image_urls_cannot_escape_the_pinned_source_archive(self):
        self.assertEqual("images/Artist/Baby I/a.png", source_path("https://tero0x.github.io/dmc-sprites/images/Artist/Baby%20I/a.png"))
        for url in ["https://example.com/a.png", "https://tero0x.github.io/dmc-sprites/images/../../secret", "file:///secret"]:
            with self.assertRaises(ValueError):
                source_path(url)


if __name__ == "__main__":
    unittest.main()
