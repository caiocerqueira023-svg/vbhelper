# VB Lab — engrenagens do fundo (tech background)

Engrenagens de fundo do VB Lab, usadas no `TechBackground` do Tamers Lab.

## Evidência (APK original, análise estática)

- `apk/assets/bin/Data/data.unity3d` → cena local **`level1`** (tela de scan/transferência):
  GameObject **`bg`** com Animator (controller **`homebg_anime`**) e 3 filhos do tipo Image:
  | nó | sprite | RectTransform (unidades) |
  |---|---|---|
  | `c1` | `title_circle1` | 1480 × 1480 |
  | `c2` | `title_cricle2` | 1520 × 1520 |
  | `c3` | `title_cricle3` | 2000 × 2000 |
  - âncora (0,0), escala 1, rotação inicial 0; **cor branca com alfa 0,051**.
  - Canvas de referência: 1920 × 1920 unidades.
- Animação: clip **`homebg_anime`** (20,0 s, loop, 60 fps), 3 bindings genéricos de Transform
  (attribute 4 = rotação), curvas comprimidas no `m_MuscleClip` (não decodificáveis com UnityPy).
  Por isso a direção/velocidade exatas foram inferidas: loop linear de 20 s com anéis em sentidos
  alternados.
- A tela do print (Home/Log) é uma cena **remota** de Addressables, não incluída no APK. A posição
  dessa instância foi reconstruída a partir do print fornecido: **centro no canto inferior direito**,
  escala **1,37×** o layout da cena local e centro em (0,976·largura, 0,919·altura) — ajustado por template matching dos sprites contra o fundo realçado do print (`homebg_spritefit_overlay2.png`).

## Assets

- `title_circle1_sprite.png`, `title_cricle2_sprite.png`, `title_cricle3_sprite.png` — sprites
  originais (branco + alfa) exportados do bundle.
- `tech_background_home_layout_preview.png` — prévia do fundo reconstruído (1080 × 2340).
- `homebg_highpass.png` / `homebg_bottom_enhanced.png` — realce do print usado para medir.
- `overlay_br2.png`, `homebg_spritefit_overlay2.png`, `homebg_spritefit_zoom.png` — sobreposições/ajustes medidos no print.
- `tech_background_preview_v2.png` — prévia final (centro no canto inferior direito, escala 1,37×).

## No app (layout final, desenho em Canvas)

`app/src/main/res/drawable-nodpi/vb_bg_gear_c1.png` … `c3.png` e
`app/src/main/java/com/tamerslab/components/TechBackground.kt`:
- posição: centro no **canto inferior direito** da tela (largura, altura);
- diâmetros independentes por anel — **1,20× / 1,60× / 2,00× a largura** (enquadramento da prévia 4) — para os três anéis ficarem
  concêntricos **sem se sobrepor** (no layout local da cena de scan os anéis se cruzam; a Home usa
  raios separados);
- tint: `TextPrimaryOnDark` (respeita o tema; no tema escuro é branco, como no APK);
- desenho: `Canvas` de tela cheia com `translate`/`rotate`/`drawImage` explícitos — sem cadeia de
  `offset`/`requiredSize`, para o centro cair exatamente no canto;
- rotação: como o fundo antigo de dois semicírculos — linear, mesmo sentido (0→360), 18 s no anel
  interno, 32 s no médio e 24 s no externo; respeita `motionEnabled()`;
- prévia: `tech_background_preview_v4.png` (enquadramento atual).

Preservação pessoal de material de IP de terceiros — não redistribuir.
