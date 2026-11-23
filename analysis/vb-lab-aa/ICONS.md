# Ícones originais (menu/footer) — extraídos e aplicados

## Origem

O bundle de skins contém **todos os ícones do footer por skin**:
`skin_<id>_<scan|log|digidex|items|battle>_<on|off>.png` (56 skins, 2.311 sprites no total).
Contact sheets: `contacts/skin_00_nav.png`, `contacts/skin_002_nav.png` (cada par on/off).

## Aplicado no Tamers Lab

- Recortei **só o ícone** (sem o texto que vem embutido no sprite), deixei branco com alpha
  transparente e gerei 192 px quadrados em `assets/nav_icons_white/`:
  `scan`, `log`, `digidex`, `items`, `battle` (contact: `contacts/nav_icons_original.png`).
- Esses arquivos substituíram os ícones recriados no app:
  `app/src/main/res/drawable-nodpi/vb_icon_{scan,log,digidex,items,battle}.png`.
  O `vb_icon_battle` original não precisa mais do “+8%” óptico: agora é a arte real.
- O app continua tintando por tema (`Icon(tint=…)`), então os ícones seguem as paletas.

## Outros ícones úteis extraídos

- `assets/footers/` mantém todas as skins (útil para um seletor de skins no Tamers Lab).
- `assets/rank_icons/` (16 ranks), `assets/rank_ui/` (números/badges), `assets/raid/` (tickets/UI).
- Sprites de UI gerais (botões, popups) estão no inventário: `aa_inventory.jsonl`.

## Nota

Os sprites originais incluem o rótulo de texto gravado (ex.: “Battle”); o recorte em
`nav_icons_white/` remove o texto e mantém só o glifo. Os ícones de rank e raid não têm texto.
