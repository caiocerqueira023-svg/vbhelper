# Diagnóstico — APK offline VB Lab (“o apk pede arquivos”)

Data: 2026-11-18 · Autor: apk-builder (teammate do Lead) · Tarefa: `task-9`

## Resumo executivo

1. **A v1 não estava corrompida.** O ZIP íntegro, as 3.184 entradas planejadas estão presentes
   (só o `classes.dex` foi recompilado, como esperado) e a assinatura v2/v3 confere. O rebuild
   limpo (`v2-baseline`) é **funcionalmente idêntico** à v1; as únicas diferenças de bytes são
   o layout do `classes.dex` (não determinístico do assembler smali, mesmas 173 classes) e os
   blocos de assinatura. Não há race/corrupção a corrigir.
2. **O APK é autocontido.** Todo o jogo está dentro dele: 1.056 bundles, 2.066 páginas,
   catálogo reescrito para caminho local, atualização de catálogo desligada e **permissão de
   INTERNET removida**. Confirmado: 1.011/1.011 bundles referenciados no catálogo existem no
   APK; 0 URLs remotas restantes. Não precisa de OBB, split, asset pack nem download.
3. **A causa provável do “pede arquivos” é a segunda tela inicial adicionada pelo patcher**
   (`RecoveryActivity`, rótulo chinês “VB 離線備份與復原”): ela é um utilitário de
   backup/diagnóstico que abre o seletor de arquivos do Android para **exportar/restaurar um ZIP**
   (`ACTION_CREATE_DOCUMENT`, `VB-offline-backup-*.zip`). Não é o jogo. A v1 tinha **dois
   ícones**; nada no jogo pede arquivos/download (varredura do `global-metadata.dat`: não existem
   as strings “ダウンロード/ファイル/インストール”).
4. **Correção na v2 final:** patch mínimo e verificado no `AndroidManifest.xml` — apenas **um
   ícone** (“VB Lab Offline”), a tela de backup fica oculta (acessível só por ADB), rótulos sem
   chinês e remoção do metadado `com.android.vending.derived.apk.id`. Tudo o mais é idêntico
   byte-a-byte ao rebuild limpo do patcher. Assinatura v2/v3, `zipalign` e `testzip` OK.

Entrega: `C:\Users\julye\Downloads\VB-Lab-Offline-Phase47-v2.apk`
(385.560.247 bytes, SHA-256 `5590cf9c8c063f1332504ef0a503fb5710480804d5fe11ef76d61695378b2374`).

---

## 1. Sintoma e material disponível

- Relato: “o apk pede arquivos e não sei o que lá; quero um apk pronto que eu possa abrir e já
  usar o app”. Sem screenshot e sem logcat.
- Entradas usadas (todas locais, SHA conferido): XAPK `net.digimon.digibb_1.4.6.xapk`
  (`27d9bdef…`), `vb_lab_aa.zip` (`59e5cf88…`), `vb_lab_static.zip` (`951eccc3…`), donor Arena
  2.1.0. Patcher oficial em `…\patcher\VB-Lab-Local-Patcher` (com `patch.py --check` OK,
  3.172 entradas verificadas).
- v1: `VB-Lab-Offline-Phase47.apk`, 385.560.252 bytes,
  SHA-256 `54d0ac1c1c4b42b1cc3f45c6f8406086dac535fccc5afe999124400d02dc0829`.

## 2. Verificação estrutural da v1 (evidência: `logs/audit-v1.txt`)

| Item | Resultado |
| --- | --- |
| Integridade ZIP (`testzip`, CRC de todas as entradas) | OK |
| Entradas | 3.187 = 3.184 do plano + 3 assinatura `META-INF` |
| Entradas faltantes vs. plano | 0 |
| Hashes por entrada vs. plano | iguais, exceto `classes.dex` (recompilado; ver §3) |
| `assets/aa/Android/offline/*.bundle` | 1.056 |
| `assets/vb-pages/*` | 2.066 |
| `lib/arm64-v8a/` | `libil2cpp.so`, `libmain.so`, `libunity.so`, `libvboffline.so` |
| DEX | `classes.dex` (222.272 B) + `classes2.dex` (4.260 B) |
| `isSplitRequired` | **false** |
| `splitTypes` / `requiredSplitTypes` / `com.android.vending.splits*` | ausentes no XML |
| `extractNativeLibs` | true |
| `resources.arsc` | armazenado sem compressão, pacote renomeado p/ `net.digimon.digibb.offline` |
| Assinatura | v2=true, v3=true, cert `CN=VB Lab Offline, O=TamersLab, C=BR` |
| minSdk / targetSdk | 26 / 30 |
| ABI | arm64-v8a |
| Espaço descomprimido | 455.609.286 B (434,5 MiB) |
| `zipalign -c 4` (stored) | OK (4 bytes) |

### Sem dependência externa (`logs/catalog-audit-v1.txt`)

- Catálogo Addressables: 3.584 internal ids; **1.011** eram `https://…` → reescritos para
  `{Addressables.RuntimePath}/Android/offline/<arquivo>.bundle`; **0** URLs remotas restantes.
- Os **1.011** bundles referenciados estão presentes no APK (0 faltando).
- `settings.json`: 1 único catalog location (local), `m_Dependencies=[]`,
  `m_DisableCatalogUpdateOnStart=true`.
- `assets/aa/catalog.bundle` legível por UnityPy; amostra de bundles carregada com objetos
  intactos (`logs/audit-v1.txt`, `unity_samples`).
- Permissões finais: `NFC` + `ACCESS_NETWORK_STATE` (a `INTERNET` foi removida pelo patch).
  **Sem INTERNET o app não consegue baixar nada** — não é tecnicamente possível ele exigir
  arquivos da rede.

## 3. Rebuild limpo vs. v1 (evidência: `logs/compare-v1-v2.json`, `logs/dex-semantic-diff-v1-v2.txt`)

O rebuild limpo em ambiente isolado (sem outros jobs concorrentes, ~8 min) gerou
`v2-baseline` com **exatamente o mesmo tamanho** (385.560.252 B) e mesmas 3.187 entradas.
Comparação entrada-a-entrada:

| Entrada | v1 vs rebuild |
| --- | --- |
| `classes.dex` | tamanho igual (222.272 B), conteúdo difere |
| `META-INF/MANIFEST.MF`, `.SF`, `.RSA` | derivados do `classes.dex` |
| **Todas as outras 3.183 entradas** | **idênticas (mesmo CRC)** |

O `classes.dex` foi comparado semanticamente via baksmali: **173 classes em ambas, 0 classes
diferentes**. `patch.py` valida o conjunto de classes contra `dex-class-hashes.json` em todo
build (“Reassembled DEX: verified 173 classes”), portanto as diferenças de bytes são layout do
assembler smali — não perda de patch nem corrupção. O hash do `classes.dex` do plano
(`5645edcf…`) não é reproduzido em nenhum dos dois builds, o que confirma
não-determinismo (nenhum build bate com o plano; ambos batem entre si semanticamente).

**Conclusão:** a hipótese de race/corrupção da v1 está descartada; o rebuild limpo valida a v1.

## 4. Por que o usuário viu “pede arquivos”

### 4.1 O patcher adiciona duas telas

`manifest_patch.py` (`patch_manifest` + `patch_reminder`) adiciona ao manifesto:

| Componente | Rótulo | Papel |
| --- | --- | --- |
| `…ReminderActivity` | “VB Lab Offline 測試版” | Aviso legal em inglês; botão **“acknowledged”** abre o `MainActivity` (jogo). |
| `…RecoveryActivity` | “VB 離線備份與復原” | **Tela de backup/restauração/diagnóstico** em chinês, com ícone **LAUNCHER** próprio na v1. |

Evidência no `smali/…/RecoveryActivity.smali`: título “VB 離線備份與復原”; botões
“匯出備份與診斷 ZIP” (exportar backup/diagnóstico ZIP), “重新整理”, “關閉 App…”; uso de
`android.intent.action.CREATE_DOCUMENT` com `application/zip` e nome
`VB-offline-backup-<data>.zip`; restauração lê ZIP via seletor de arquivos; mensagens
“無法讀取存檔。請先匯出備份與診斷…” (não foi possível ler o save; exporte o backup antes).
Ou seja: **é uma tela que abre o seletor de arquivos do Android e pede/escreve um ZIP**.

`aapt2 dump badging` da v1 confirma **duas** `launchable-activity`:
`RecoveryActivity` (“VB 離線備份與復原”) e `ReminderActivity` (“VB Lab Offline 測試版”).
É muito provável que o usuário tenha tocado no ícone chinês (ou no “abrir” do instalador, que
cai na primeira atividade MAIN/LAUNCHER — a de backup) e interpretado a tela/seletor de
arquivos como “o apk pede arquivos”.

### 4.2 O que o aviso do patcher diz (texto extraído de `payload/ReminderActivity.java` e do `classes2.dex`)

Título: “[Reminder from DigiKidult]”.
Corpo: “This modified offline version is intended for personal, non-commercial use only. It is an
unofficial fan modification and is not affiliated with or endorsed by Bandai. Digimon and all
related app assets are the property of Bandai and their respective rights holders. No ownership of
the original content is claimed.” Botão: “acknowledged”.
**Não há pedido de arquivo/download nesse aviso** — é apenas o aviso legal obrigatório do patch.

### 4.3 O jogo em si não pede download/arquivos

Varredura do `assets/bin/Data/Managed/Metadata/global-metadata.dat` (3,8 MB; onde ficam os
literais de string do IL2CPP): **não existem** “ダウンロード” (download), “ファイル” (arquivo),
“インストール” (instalar), “ネットワーク” (rede); as ocorrências de “Download” são apenas APIs
do Unity (`DownloadHandler`). O jogo não tem diálogo de baixar/instalar arquivos.

### 4.4 Outras hipóteses (menos prováveis, cobertas no INSTALL.md)

- Instalador/Play Protect/Auto Blocker (Samsung) bloqueando a instalação — não é “pedir
  arquivos”, mas é o problema de instalação mais comum; instruções no INSTALL.md.
- Corrupção na transferência (368 MB por WhatsApp/Drive) → “erro de análise do pacote”.
- Espaço livre < ~1,5 GB → “Aplicativo não instalado”.
- Instalar o APK base do XAPK por engano: o base original tem `isSplitRequired=true` + metadados
  `com.android.vending.splits(.required)` e `INTERNET` (ver `logs/orig-xapk-base-manifest.txt`);
  um base de bundle sem os splits não instala/abre. O APK do patch remove isso
  (`isSplitRequired=false`).

## 5. Correção aplicada na v2 final (patch de manifesto)

Base: `v2-baseline` (saída **pura** do patcher, SHA `33b611ed…`). Patch binário do
`AndroidManifest.xml` (sem tocar em DEX, recursos, assets ou libs), reempacotamento e
reassinatura com a **mesma keystore** (`vb-offline.p12`, alias `vb-offline`).

Edições (ver `logs/build-ux-v2.log`):

1. `RecoveryActivity`: removido o `<category android:name="android.intent.category.LAUNCHER">`
   → some o ícone chinês de backup da tela inicial, mas a atividade/intent-filter continuam
   (exportada; abre por ADB se precisar: comando no INSTALL.md).
2. Rótulos visíveis “VB Lab Offline 測試版” → **“VB Lab Offline”** (app/atividades).
3. Rótulo da `RecoveryActivity` → “VB Lab Offline Backup” (aparece só em contextos de sistema/ADB).
4. Removido `meta-data com.android.vending.derived.apk.id` (resíduo de APK derivado de bundle).

Obrigatórios preservados: aviso legal `ReminderActivity`, pacote separado
`net.digimon.digibb.offline`, versão 1.4.6/220041, `isSplitRequired=false`,
`extractNativeLibs=true`, ABI arm64-v8a, remoção da `INTERNET`, assinatura v2/v3.

Resultado (`logs/compare-v2baseline-v2final.json`): apenas 4 entradas diferem do baseline —
`AndroidManifest.xml` e as 3 da assinatura. **Nenhum outro byte do jogo mudou.**

## 6. Verificações finais da v2 (evidências em `logs/verify-v2-*`)

| Verificação | Resultado |
| --- | --- |
| SHA-256 | `5590cf9c8c063f1332504ef0a503fb5710480804d5fe11ef76d61695378b2374` |
| Tamanho | 385.560.247 bytes |
| `apksigner verify` (exit 0) | **Verifies**; v2=true, v3=true; 1 signer `CN=VB Lab Offline` (SHA-256 `c6a80551…`) |
| `aapt2 dump badging` | `net.digimon.digibb.offline` 220041/1.4.6, minSdk 26, target 30, label **“VB Lab Offline”**, 1 `launchable-activity` (`ReminderActivity`), `MainActivity` só leanback, native-code arm64-v8a |
| `aapt2 dump permissions` | `NFC`, `ACCESS_NETWORK_STATE` (sem INTERNET) |
| Manifesto | `isSplitRequired=false`, `extractNativeLibs=true`, sem `splitTypes`/`requiredSplitTypes`/vending splits |
| ZIP | 3.187 entradas, `testzip` OK, sem duplicatas |
| `zipalign -c 4` (exit 0) | Verification successful (1.701 entradas STORED alinhadas em 4 B) |
| Assets | 1.056 bundles offline + 2.066 páginas; catálogo 0 HTTP / 1.011 ids locais; todos os bundles referenciados presentes; amostras UnityPy legíveis |
| DEX | `classes.dex` recompilado (173 classes verificadas pelo patch); `classes2.dex` idêntico ao plano (aviso legal + `ReminderActivity`) |
| Instalação | APK único; atualiza por cima da v1 (mesma chave, mesmas versionCode/assinatura) mantendo dados |

## 7. Limitações e próximo teste recomendado

- **Não foi possível testar em aparelho**: o celular conectado por ADB
  (`RQCW808E6WF`) apareceu e desapareceu durante a sessão, e não há AVD arm64 (as imagens
  locais são x86_64 `android-37.x` e não executam APK arm64). A validação é estática + de
  empacotamento/assinatura (a mesma cadeia usada pelo patcher).
- **Teste recomendado (5 min)**: `adb install -r VB-Lab-Offline-Phase47-v2.apk`;
  abrir; conferir tela do aviso → “acknowledged” → título; conferir que só há 1 ícone; ligar
  modo avião e reabrir; opcional `adb logcat` se algo falhar.
- O jogo original é japonês/inglês; o patch não traduz a interface interna.
- A tela de backup/diagnóstico fica oculta (ADB): ver comando no INSTALL.md. Se o usuário quiser
  o atalho de volta, é só manter a v1 ou pedir uma variante com o ícone visível.

## 8. Artefatos

- `C:\Users\julye\Downloads\VB-Lab-Offline-Phase47-v2.apk` — **produto final** (instalar).
- `C:\Users\julye\Downloads\VB-Lab-Offline-Phase47-v2-baseline.apk` — saída pura do patcher
  (referência/verificação; **não** é a versão para o usuário). SHA `33b611ed…`.
- `C:\Users\julye\Downloads\VB-Lab-Offline-Phase47.apk` — v1, preservada.
- `analysis/vb-lab-apk/INSTALL.md` — guia de instalação pt-BR.
- `analysis/vb-lab-apk/logs/` — hashes, auditorias, comparações, dumps `aapt2`, assinatura.
- `analysis/vb-lab-apk/tools/` — scripts reprodutíveis:
  - `audit_apk.py` — auditoria estrutural/hashes de um APK contra o plano do patcher;
  - `catalog_audit.py` — resolução do catálogo Addressables contra as entradas do APK;
  - `compare_apk.py` — diff entrada-a-entrada entre dois APKs;
  - `build_ux_variant.py` — patch de manifesto + reempacotamento + reassinatura + verificação.
