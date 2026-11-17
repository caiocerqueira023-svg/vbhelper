# VB Lab — ícones de navegação (brancos, PNG transparente, prontos para app)

Os cinco ícones da barra inferior do VB Lab (**Scan, Log, Digidex, Items, Battle**) convertidos
para **PNG branco puro com fundo transparente** a partir das imagens em alta resolução fornecidas
(`source/<icone>_source.png`). Os detalhes escuros (fundo, tela, olhos, boca, facetas, sulcos,
interior do medidor e o "!" do Log) ficaram **transparentes**. Nenhum texto faz parte dos arquivos.

## O que tem

- `master/<icone>_native.png` — recorte nativo em alta resolução (com alpha), a melhor qualidade.
- `png/<icone>_<tamanho>.png` — quadrados com o ícone centralizado (92%), tamanhos **24, 36, 48,
  72, 96, 192, 512 e 1024 px**.
- `android/drawable-<densidade>/ic_vblab_nav_<icone>.png` — prontos para copiar para
  `app/src/main/res/`: **mdpi=24, hdpi=36, xhdpi=48, xxhdpi=72, xxxhdpi=96 px**
  (para um slot de 24 dp).
- `preview/` — `white_on_dark.png` (fundo escuro), `white_on_checker.png` (transparência),
  `masters_preview.png`, `source_vs_result.png` (recebido × PNG branco).

## Especificação

- **Cor:** só branco — RGB `#FFFFFF` em todo pixel com alfa > 0 (validado).
- **Alfa:** fundo 100% transparente; bordas suavizadas por antialiasing; interior escuro vazado.
- **Log:** o badge vira branco e o "!" é um **furo transparente** (para aparecer em qualquer fundo);
  o medidor mantém as marcações e o ponteiro brancos.
- Master nativo por ícone: Scan ~876×396, Log ~1009×660, Digidex ~601×457, Items ~655×489,
  Battle ~688×553 px.

## Como usar

Copie as pastas `android/drawable-*` para `app/src/main/res/` ou use `png/<icone>_96.png` direto.
Em Compose:

```kotlin
Image(painterResource(R.drawable.ic_vblab_nav_scan), contentDescription = null,
      modifier = Modifier.size(24.dp))
```

## Observação

A imagem do Scan tinha o texto "Resultado do Vectorizer.AI" ao fundo; componentes fora do ícone
foram removidos. Preservação pessoal de material de IP de terceiros — não redistribuir.
