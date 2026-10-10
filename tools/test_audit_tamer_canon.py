import unittest

from audit_tamer_canon import evolution, links, templates, transformations


class CanonAuditTest(unittest.TestCase):
    def test_commented_species_recommendations_are_not_character_forms(self):
        text = """{{Box Evo
|Perfect=[[Rize Greymon]]
|Ultimate=[[Victory Greymon]]
}}
<!--{{Box Evo
|Ultimate=[[Shine Greymon]]
}}-->"""
        self.assertEqual(1, len(evolution(text)))
        self.assertEqual("Victory Greymon", evolution(text)[0][1]["forms"][0]["name"])

    def test_references_do_not_become_partners_or_fusion_components(self):
        text = "[[Mastemon]] (with [[Lady Devimon]]){{ref|''[[Digimon Story: Cyber Sleuth]]''}}"
        self.assertEqual(["Mastemon", "Lady Devimon"], [item["name"] for item in links(text)])
        forms = evolution("{{Box Evo\n|Ultimate=" + text + "\n}}")
        self.assertEqual([{"page": "Lady Devimon", "name": "Lady Devimon"}], forms[0][0]["forms"][0]["companions"])

    def test_nested_templates_do_not_cut_off_the_evolution_box(self):
        text = "{{Box Evo\n|Child=[[DORUmon]]\n|Ultimate=[[Gaioumon]]{{ref|source}}\n|Ultimate2=[[Gaioumon: Itto Mode]]\n}}"
        self.assertEqual(1, len(templates(text, "Box Evo")))
        self.assertEqual(3, len(evolution(text)[0]))

    def test_hybrid_and_digixros_have_separate_nonstandard_stage_evidence(self):
        text = "{{Box Hybrid\n|Human=[[Agnimon]]\n|Transcendent=[[Kaiser Greymon]]\n}}\n{{Box Xros\n|Base=[[Shoutmon]]\n|Super=[[Omega Shoutmon]]\n}}"
        self.assertEqual({"Box Hybrid", "Box Xros"}, set(transformations(text)))
        self.assertEqual([], evolution(text))


if __name__ == "__main__":
    unittest.main()
