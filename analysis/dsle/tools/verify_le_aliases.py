"""Verify the hand-curated alias candidates against the bundled catalog."""
import json
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
CATALOG = ROOT / "app/src/main/assets/species_catalog.json"

# LE name -> candidate catalog ids (first existing wins)
CANDIDATES = {
    "Kyuubimon": ["digimon.kyubimon", "digimon.kyukimon"],
    "WereGarurumonB": ["digimon.weregarurumonblack"],
    "MKabuterimonR": ["digimon.atlurkabuterimonred", "digimon.megakabuterimonred"],
    "Rapidmon": ["digimon.rapidmonperfect", "digimon.rapidmonarmor"],
    "Lucemon FM": ["digimon.lucemonfalldownmode"],
    "MameTyranomon": ["digimon.mametyranomon", "digimon.mametyramon"],
    "METyranomon": ["digimon.metaltyranomon"],
    "Argomon UT": ["digimon.argomonultimate", "digimon.argomonultimatemode"],
    "SkBaluchimon": ["digimon.skullbaluchimon"],
    "MTyrannomon": ["digimon.mastertyranomon"],
    "HKabuterimon": ["digimon.heraklekabuterimon", "digimon.herculeskabuterimon"],
    "VNMyotismon": ["digimon.venommyotismon"],
    "IPDramonDM": ["digimon.imperialdramondragonmode"],
    "Cherubimon(G)": ["digimon.cherubimonvirtue"],
    "Cherubimon(E)": ["digimon.cherubimonvice"],
    "MESeadramon": ["digimon.megaseadramon"],
    "Justimon": ["digimon.justimonaccelarm"],
    "BWarGreymon": ["digimon.blackwargreymon"],
    "GKuwagamon": ["digimon.grankuwagamon"],
    "SNGreymon": ["digimon.shinegreymon"],
    "MRGaogamon": ["digimon.miragegaogamon"],
    "Chronomon HM": ["digimon.chronomonholymode"],
    "DSGreymon": ["digimon.dotshinegreymon"],
    "DMGaogamon": ["digimon.dotmiragegaogamon"],
    "ArgomonM": ["digimon.argomonmega", "digimon.argomonultimatemode"],
    "AIrismon": ["digimon.ancientirismon"],
    "AGarurumon": ["digimon.ancientgarurumon"],
    "AMTheriumon": ["digimon.ancientmegatheriummon"],
    "AGreymon": ["digimon.ancientgreymon"],
    "AMermaidmon": ["digimon.ancientmermaidmon"],
    "AWisemon": ["digimon.ancientwisemon"],
    "ASphinxmon": ["digimon.ancientsphinxmon"],
    "ATroiamon": ["digimon.ancienttroiamon", "digimon.ancienttoropiamon"],
    "ABeatmon": ["digimon.ancientbeatmon"],
    "AVolcamon": ["digimon.ancientvolcamon"],
    "UFVeedramon": ["digimon.ulforcevdramon"],
    "TGVespamon": ["digimon.tigervespamon"],
    "IPDramon FM": ["digimon.imperialdramonfightermode"],
    "IPDramon PM": ["digimon.imperialdramonpaladinmode"],
    "Gallantmon CM": ["digimon.gallantmoncrimsonmode", "digimon.dukemoncrimsonmode"],
    "MMyotismon": ["digimon.malomyotismon"],
    "ZdMilleniummon": ["digimon.zeedmillenniumon"],
    "CGallantmon": ["digimon.chaosgallantmon", "digimon.chaosdukemon"],
    "MMillenniummon": ["digimon.moonmillenniumon"],
    "SGreymon BM": ["digimon.shinegreymonburstmode"],
    "SGreymon RM": ["digimon.shinegreymonruinmode"],
    "MGaogamon BM": ["digimon.miragegaogamonburstmode"],
    "Ravemon BM": ["digimon.ravemonburstmode"],
    "Beelzemon BM": ["digimon.beelzebumonblastmode"],
    "Rosemon BM": ["digimon.rosemonburstmode"],
}


def main():
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    ids = {species["id"]: species for species in catalog["species"]}
    resolved = {}
    missing = []
    for name, candidates in CANDIDATES.items():
        found = next((candidate for candidate in candidates if candidate in ids), None)
        if found is None:
            missing.append((name, candidates))
        else:
            resolved[name] = found
            print(f"{name!r:20s} -> {found:45s} {ids[found]['name']}")
    print(f"\nresolved {len(resolved)} / {len(CANDIDATES)}")
    for name, candidates in missing:
        print(f"MISSING {name!r}: tried {candidates}")
    Path(ROOT / "analysis/dsle/le_alias_map.json").write_text(
        json.dumps(resolved, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
