"""Bounded source-adapter checks, entirely offline."""
import io
import unittest

from PIL import Image

from tools.hatchery_sprites import NameTableParser, RosterParser, idle_frames, regional_name_pairs
from tools.wikimon_sprites import color_entry


class HatcherySourceTest(unittest.TestCase):
    def test_roster_accepts_named_color_dot_and_excludes_cutins_and_vb_art(self):
        parser = RosterParser()
        parser.feed('''<table><tr><td><img src="//humulos.com/digimon/images/dot/dmxw/cutin/shout.gif">
            <img src="//humulos.com/digimon/images/dot/dmxw/shout.gif"></td>
            <td class="list_name">Shoutmon &amp; test</td><td class="list_level">III</td></tr>
            <tr><td><img src="//humulos.com/digimon/images/dot/vbdm/agu.gif"></td>
            <td class="list_name">Agumon</td></tr></table>''')
        self.assertEqual(1, len(parser.rows))
        self.assertEqual("Shoutmon & test", parser.rows[0]["list_name"])
        self.assertEqual("https://humulos.com/digimon/images/dot/dmxw/shout.gif", parser.rows[0]["url"])

    def test_regional_table_retains_forms_alternatives_and_conflicting_evidence(self):
        pairs = regional_name_pairs('''<table><tr><th>Name</th><th>Alternative</th><th>Japanese</th></tr>
            <tr><td><a>Monmon</a></td><td></td><td>Koemon</td></tr>
            <tr><td>RedVegiemon</td><td>Red Veggiemon<br>Red Vagimon</td><td>Red Vegimon</td></tr>
            <tr><td>Omnimon X</td><td></td><td>Omegamon (X-Antibody)</td></tr>
            <tr><td colspan="3">Another name → see Othermon</td></tr></table>''')
        self.assertIn({"name": "Monmon", "target": "Koemon"}, pairs)
        self.assertIn({"name": "Red Veggiemon", "target": "Red Vegimon"}, pairs)
        self.assertIn({"name": "Omnimon X", "target": "Omegamon (X-Antibody)"}, pairs)
        self.assertEqual(5, len(pairs))

    def test_idle_adapter_preserves_transparency_and_observed_pose_order(self):
        first = Image.new("RGBA", (2, 2), (0, 0, 0, 0))
        second = first.copy()
        first.putpixel((0, 0), (255, 0, 0, 255))
        second.putpixel((1, 0), (0, 0, 255, 255))
        stream = io.BytesIO()
        first.save(stream, format="GIF", save_all=True, append_images=[second], duration=100, loop=0, disposal=2)
        frames = idle_frames(stream.getvalue())
        self.assertEqual(2, len(frames))
        self.assertEqual((255, 0, 0, 255), frames[0].getpixel((0, 0)))
        self.assertEqual(0, frames[0].getpixel((1, 1))[3])
        self.assertEqual((0, 0, 255, 255), frames[1].getpixel((1, 0)))

    def test_opaque_or_wrong_format_sources_are_not_silently_treated_as_dot_art(self):
        for fmt in ("GIF", "PNG"):
            stream = io.BytesIO()
            Image.new("RGB", (2, 2), "red").save(stream, format=fmt)
            with self.assertRaises(ValueError):
                idle_frames(stream.getvalue())

    def test_wikimon_color_adapter_uses_exact_file_identity_and_keeps_uploader_credit(self):
        image = {"url": "https://wikimon.net/images/6/67/Azhdarmon_vpet_dmc.gif", "user": "Contributor",
                 "timestamp": "2026-09-28T18:03:45Z", "size": 2000, "width": 48, "height": 48,
                 "descriptionurl": "https://wikimon.net/File:Azhdarmon_vpet_dmc.gif", "sha1": "a" * 40}
        entry = color_entry({"title": "File:Azhdarmon vpet dmc.gif", "imageinfo": [image]})
        self.assertEqual("Azhdarmon", entry["name"])
        self.assertEqual("Contributor", entry["uploader"])
        self.assertIsNone(color_entry({"title": "File:Azhdarmon vpet vb.gif", "imageinfo": [image]}))
        self.assertIsNone(color_entry({"title": "File:Azhdarmon vpet dmc cutin.gif", "imageinfo": [image]}))


if __name__ == "__main__":
    unittest.main()
