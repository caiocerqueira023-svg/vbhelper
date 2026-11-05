from pathlib import Path

fixes = {
    "app/src/main/res/values-pt-rBR/strings.xml": {
        932: '    <string name="ui_battle_loadout_blast_title">EVOLU\u00c7\u00c3O BLAST</string>',
    },
    "app/src/main/java/com/tamerslab/battle/ui/offline/OfflineTrainingBattleScreen.kt": {
        838: '                                    Text("\u00daltima rejei\u00e7\u00e3o: $reason", color = BattleErrorHint,',
    },
    "app/src/main/java/com/tamerslab/farm/ui/home/FarmHomeHud.kt": {
        89: " * Top status zone of the farm home (PRESENTATION_REDESIGN_PLAN.md \u00a75.1).",
    },
}
for name, lines in fixes.items():
    path = Path(name)
    content = path.read_text(encoding="utf-8").splitlines(keepends=True)
    for number, replacement in lines.items():
        old = content[number - 1]
        ending = "\r\n" if old.endswith("\r\n") else ("\n" if old.endswith("\n") else "")
        content[number - 1] = replacement + ending
    path.write_text("".join(content), encoding="utf-8")
    print("fixed", name)
