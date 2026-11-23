# VB Lab 1.4.6 — análise com o conteúdo remoto completo

Pasta de análise criada depois de montar o APK offline (com `vb_lab_aa.zip` + `vb_lab_static.zip`), que
traz os bundles remotos que faltavam no APK. Conteúdo principal:

| Arquivo | Conteúdo |
|---|---|
| `aa_inventory.jsonl` | inventário dos 1.912 bundles Android 20190428 (nomes de GameObjects/Sprites/Clips interessantes) |
| `data/animations.csv` | 2.207 AnimationClips encontrados (nome, bundle, curvas, eventos) |
| `assets/rank_icons/` | 16 ícones originais de rank de Tamer (`tamerrankicon_0..15`) |
| `assets/rank_ui/`, `assets/rank_bg/` | sprites e prefabs da UI de rank/ranking (81 + 122 sprites, 150 clips) |
| `assets/footers/` | 2.311 sprites de skin/footer: os **ícones originais do menu** por skin (on/off) |
| `assets/nav_icons_white/` | recorte branco/alpha dos 5 ícones de nav (sem o texto embutido) |
| `assets/raid/` | 171 sprites de raid (bosses raid001…, tickets, UI) |
| `assets/title_gears/` | sprites originais `title_circle1/2/3` + 6 clips (`title_anime1/2/3`) |
| `contacts/` | contact sheets (rank, skins, raid, engrenagens, ícones) |
| `extraction_report.json` | inventário detalhado por bundle extraído |
| `RANK.md`, `RAID_AND_ANIMATIONS.md`, `ICONS.md` | relatórios por tema |

Fonte: `vb_lab_aa.zip` (1.2 GB, SHA-256 `59e5cf88…`) e `vb_lab_static.zip` (165 MB, `951eccc3…`),
baixados do Internet Archive e verificados; patcher local `VB-Lab-Local-Patcher` 0.1-RC.
Uso pessoal de preservação; material de IP de terceiros, não redistribuir.
