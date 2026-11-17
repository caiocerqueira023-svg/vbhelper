# Código recuperado do DBT-Link v18.9 (REA)

Tudo aqui veio do APK `DBT-Link-v18.9.apk`
(SHA-256 `b96aebdb5b0a7c110ac2475ea04b8c26b980986bb555ad15abceab4aabe47030`), analisado
estaticamente com REA/jadx; nada foi executado. É o **código antigo do próprio autor do
projeto** (o `lector.js` traz o comentário “(2026-11-12, pedido del usuario)”).

## vpet-webapp.zip + lector.js

O webapp VPet completo extraído de `assets/vpet/` do APK (11.031 arquivos, ~8 MB) está no
arquivo **`vpet-webapp.zip`** (byte a byte). Para inspecionar:

```powershell
Expand-Archive analysis/vpet-rea/recovered/vpet-webapp.zip analysis/vpet-rea/recovered/webapp
```

O núcleo do protocolo continua fora do zip para leitura e para o gerador:
`recovered/lector.js` (33 KB, autossuficiente; tabelas `LISTAS` embutidas; exporta
`window.LECTOR` e `window.vpetLinea`) é a cópia verbatim usada por
`tools/vpet/extract-lists.mjs`. `recovered/reader.html` é a página mínima de glúp
criada para o Tamers Lab (referência; o app usa o port nativo, não WebView).

O zip guarda também `escaner.html`, `escaner-datos.js`, `datos.js`, `inicio.html`,
`pantalla.html`, `peleas.html`, `puente-huevos.js`, `ingles.js`, `quests.json`,
sprites/UI/fontes — as telas de ovos/jogo do DBT-Link não são necessárias para transferir.
## decompiled/

Saída do JADX (via REA) para as classes Kotlin do recurso: `VpetActivity` (onCreate,
linea, estado, abrir, conectarBt, listaBt, cuadrosDmc, pedirPermisoUsb…), `AcomUsb`
(abrir/recibir/enviar/avisarListo), `BtCom` (conectar/recibir/enviar/siguiente/parar,
`<clinit>` com UUIDs), `BtSerie`, `VpetLink`, `VpetActivity$Puente` (membros do JS bridge),
`$Disp`, `VpetKt`. Métodos aparecem como Java; `*.members.json` traz os inventários de
membros. Nomes de arquivos com `<init>`/`<clinit>` tiveram os caracteres <> removidos.

## kotlin/

Reconstrução para reutilização no Tamers Lab (não compilada/testada em hardware):

- `VpetLink.kt` — interface de transporte + `VpetReadResult` + enum de etapas.
- `VpetTransports.kt` — `AcomUsbLink` (USB CDC/ACM 9600 8N1 + permissão),
  `BtComLink` (BLE NUS: UUIDs, fila de chunks, retry), `BtSerieLink` (SPP).
- `VpetReaderWebView.kt` — WebView + `@JavascriptInterface` para rodar `lector.js` e
  devolver `VpetReadResult`.

Observações de fidelidade: `AcomUsb`/`lecTor` seguem o comportamento do APK; `BtCom` (BLE)
foi reconstruído a partir do java decompilado (fila, `writeType=2`, retry 60 ms) e precisa
de teste real; `BtSerie` é o caminho SPP clássico com fallback citado no original.
Só **leitura** — o DBT-Link não escreve no VPet.
