from pathlib import Path

# Line-targeted repairs for characters lost to an earlier encoding rewrite (U+FFFD placeholders).
repairs = {
    "app/src/main/java/com/tamerslab/world/ui/RadarChrome.kt": {
        131: "/** Dense thin ticks: one every 3\u00b0; every 30th tick is a cardinal direction. */",
        153: " * The top cluster: a soft scrim carrying one 48dp back key, the place label (\"Radar \u00b7 NW\", the",
        206: "                    text = cardinal?.let { \"\u00b7 $it\" } ?: \"\u00b7 \u2014\",",
        244: " * One dense ring of thin cyan ticks turns with the phone heading (120 marks, every 3\u00b0); the main",
        247: " * the plate itself is the instrument face. A single red two-triangle needle \u2014 the reference digivice",
        248: " * arrow \u2014 rides the ring at the nearest wild Digimon's bearing and points outward at it.",
        393: "        // Nearest-Digimon needle: the reference digivice arrow is two stacked triangles \u2014 a larger",
    },
    "app/src/main/java/com/tamerslab/world/ui/RadarIsometricViewport.kt": {
        1145: "     * Projects each live wild billboard through view \u00d7 projection and reports the clamped screen",
        1403: "        /** Radar camera assistance is tighter than the farm's (\u00b115\u00b0) so cardboard reads front-facing. */",
        1407: "        /** Facing latch: wider than the farm's 0.16 so a 1\u00b0 compass step cannot chatter the mirror. */",
    },
    "app/src/main/java/com/tamerslab/world/ui/WorldInteractionOverlay.kt": {
        82: "            Text(wilds.joinToString(\" \u00b7 \") { names[it.individualId].orEmpty() },style=MaterialTheme.typography.bodyMedium)",
        117: "        text={Text(if(previewBoth) \"$activeName + ${owned.firstOrNull { it.first==secondOwned }?.second}  \u00b7  ${names.values.joinToString(\" + \")}\" else",
        118: "            \"$activeName + ${names[previewAlly]}  \u00b7  ${names.filterKeys { it!=previewAlly }.values.joinToString()}\")},",
    },
    "app/src/main/java/com/tamerslab/world/ui/WorldConversationScreen.kt": {
        150: "                if(group) Text(names.values.joinToString(\" \u00b7 \"),style=MaterialTheme.typography.labelLarge,color=TextSecondaryOnDark)",
    },
    "app/src/main/java/com/tamerslab/world/ui/WorldScreen.kt": {
        1187: "                // O jogador permanece sempre no centro da tela. O mundo \u00e9 que se desloca",
        1188: "                // em sentido contr\u00e1rio ao movimento do jogador.",
        1209: "                    // A grade representa o mundo, se desloca com o jogador e gira com a b\u00fassola.",
        1217: "                        // Desenha a grade em uma \u00e1rea maior que a tela para que a rota\u00e7\u00e3o",
        1894: "            text={Text((spawn?.speciesName ?: \"Digimon\")+\" \u00b7 1 \u00d7 1\\n\"+intent.reason)},",
    },
}
for name, lines in repairs.items():
    path = Path(name)
    content = path.read_text(encoding="utf-8").splitlines(keepends=True)
    for number, replacement in lines.items():
        index = number - 1
        ending = "\n" if content[index].endswith("\n") else ""
        if content[index].endswith("\r\n"):
            ending = "\r\n"
        content[index] = replacement + ending
    path.write_text("".join(content), encoding="utf-8")
    print("repaired", name, len(lines), "lines")
