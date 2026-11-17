# Plano de implementação — VPet (Digital Monster / Pendulum) na transferência do Tamers Lab

Status: **implemented (all phases, 2026-11-16)** — see
[IMPLEMENTATION_RESULT.md](IMPLEMENTATION_RESULT.md) for the delivered files and the build/test
evidence. Base: engenharia reversa do
DBT-Link v18.9 — ver [README.md](README.md) e [INTEGRATION.md](INTEGRATION.md). As classes
recuperadas estão em `recovered/`; nada foi executado no APK nem testado em hardware.

## 0. Decisões tomadas (respostas do usuário)

| # | Pergunta | Decisão |
|---|---|---|
| D1 | Direção | **Leitura agora; arquitetura pronta para escrita futura** (sem pesquisar/implementar envio ao VPet nesta entrega) |
| D2 | Lugar na UI | **Nova seção “VPet” no hub de Transferência** (ao lado de Jogos/Storage/Dispositivos/Enviar) |
| D3 | Resultado | **Parceiro APP no Storage com `originKind = IMPORTED`** (sem card físico, sem migração Room) |
| D4 | Leituras repetidas | **Novo indivíduo a cada leitura** (sem dedupe; hex bruto só para exibição/log) |
| D5 | Motor do protocolo | **Port nativo em Kotlin + tabelas JSON geradas do `lector.js`** |
| D6 | Transportes da 1ª versão | **Só A-Com USB** (serial 9600 8N1); BLE/SPP ficam para fase posterior |
| D7 | Espécie desconhecida/bloqueada | **Importar como “espécie desconhecida” com nome + procedência** (não perder a leitura) |
| D8 | Hardware | **Nenhum por enquanto** → validar com transportes fake/script e deixar checklist manual para quando houver A-Com |

Consequências diretas:
- Como não há hardware, o plano inclui um **transporte scriptado** (replay de linhas) e um
  botão de “simular leitura” em debug para provar o fluxo fim-a-fim sem aparelho.
- Escrita futura (D1): o núcleo do protocolo fica separado em comandos (`VpetCommand`) e
  sessão (`VpetReadSession`), e o transporte já é genérico `enviar/cerrar` — adicionar
  escrita depois = novo `VpetWriteSession` + novos comandos, sem tocar USB/UI.
- Espécie desconhecida (D7): não há como criar parceiro jogável sem identidade canônica.
  Solução sem migração: leitura fica numa **lista de “leituras pendentes”** (JSON em
  DataStore) na seção VPet, com procedência completa; o usuário pode **mapear a espécie**
  (busca no catálogo) e aí sim importar, ou descartar. Se preferir criar um “parceiro
  placeholder” bloqueado em vez de lista pendente, é só dizer — a lista é a opção que não
  cria dados inutilizáveis no Storage.

## 1. Mapa do que existe hoje (para encaixar sem quebrar nada)

Transferência (`app/src/main/java/com/tamerslab/transfer/`, `navigation/`):

| Seção | Hoje |
|---|---|
| `SAVES` | biblioteca de jogos (`TransferGameLibraryScreen`) |
| `STORAGE` | parceiros e envio para save de jogo (`TransferWorkspaceScreen`) |
| `DEVICES` | relógio NFC (`TransferDevicesPane` → `ScanScreen` → `Reading/WritingScreen`) |
| `SEND` | destino: relógio ou jogo (`TransferSendScreen`) |

Modelo de dados:
- `DigimonIndividual` = identidade permanente (`individualId`, `speciesName`, `originKind`).
- `UserCharacter` = representação jogável (`DeviceType.VBDevice/BEDevice/APP`; sidecar
  `AppPartnerProgress` para APP).
- `CardCharacter` com `cardId == null` + `catalogSpeciesId` = definição de catálogo
  (parceiro sem card físico).
- `IndividualOrigin.IMPORTED` = **“Brought in from another game or V-Pet”** — já existe,
  evita migração Room.
- `ExpandedCatalogRepository.definitionFor(nameOrId)` cria a definição de catálogo;
  `AppPartnerFactory.createLocked(...)` cria indivíduo + representação + progresso +
  personalidade + histórico (tudo em transação).
- `SpeciesBindingRepository.canonicalIdentityFor(name)` / `bindNewIndividual(...)`.
- Gate de participação: `db.participation?.snapshot()?.requireCard(definition.id)`
  (`EligibilityReason.UNKNOWN_SPECIES` também existe para parceiros sem espécie).

Convenções (CONTRIBUTING.md): módulos de biblioteca não dependem de `app`; domínio sem
Android/Room; Koin em `di/AppModule.kt`; mudança de comportamento atrás de flag default-off;
cada fase com testes; migração Room só com bump + teste; gates
`:app:testIntegrityCheckUnitTest`, `:app:lintIntegrityCheck`. Locales: `values`,
`values-pt-rBR`, `values-ja`.

## 2. Conceito: VPet como origem de indivíduo

```
[VPet físico] --IR--> [A-Com USB] --linhas ASCII--> app
  escolhe modelo (dm20|dmx|pen20|penz|dmc|penc) → envia DigiROM/bloco
  → recebe r:XXXX → decodifica {nome, etapa, atributo, poder, versão, hex}
  → resolve espécie canônica
       ├─ conhecida → cria parceiro APP no Storage (origem IMPORTED) → fluxos existentes
       └─ desconhecida → leitura pendente (nome+procedência) → mapear depois
```

Não há escrita no VPet (o DBT-Link só lê). “VPet na aba de transferência” = nova origem de
dispositivo, ao lado de jogo e relógio.

## 3. Arquitetura (final)

### 3.1 Novo módulo `:hardware-vpet` (biblioteca Android, sem depender de `app`)

```
hardware-vpet/
  build.gradle.kts                     Android library, namespace com.tamerslab.hardware.vpet
  src/main/AndroidManifest.xml         (merge de permissões pode ficar no app; módulo mínimo)
  src/main/resources/vpet-lists.json   tabelas LISTAS geradas do lector.js (script único)
  src/main/java/com/tamerslab/hardware/vpet/
    protocol/VpetModel.kt              enum DM20, DMX, PEN20, PENZ, DMC, PENC (+ versões)
    protocol/VpetCommand.kt            sealed: DigiRom(prefixo/palavras), ColorBlock(C1/C2), Stop
    protocol/VpetDigiRom.kt            romDM20/DMX/Pen20/PenZ/DMC/PenC + nib/checksum + hex4
    protocol/VpetDecoder.kt            decDM20/decDMX/decPen20/decPenZ/decDMC/decPenC
    protocol/VpetTables.kt             carrega vpet-lists.json (nome/etapa/atributo por índice)
    protocol/VpetReadSession.kt        máquina de estados: enviar, timeouts (30 s / 1,5 s),
                                       progresso, aprendizado de versão, até 8 tentativas,
                                       callbacks monitor/progresso/falla/listo
    transport/VpetLink.kt              interface enviar(texto)/cerrar()
    transport/AcomUsbLink.kt           USB serial 9600 8N1 (usb-serial-for-android)
    transport/VpetDiscovery.kt         lista A-Com via prober + fallback CDC/ACM
    transport/ScriptedVpetLink.kt      (debug/test) replay de linhas para testes e simulação
  src/test/java/...                    ROM/decoder/sessão (JVM)
```

- `vpet-lists.json` é gerado por `tools/vpet/extract-lists.mjs` a partir do
  `recovered/lector.js` (o objeto `LISTAS` já é JSON válido). O script também
  emite **vetores dourados** (ROMs e decodificações) usados nos testes Kotlin, garantindo
  paridade com o código antigo.
- Escrita futura (D1): `VpetCommand` é extensível; `VpetLink` já envia qualquer texto;
  adicionar depois `VpetWriteSession`/comandos novos não altera transporte nem UI.

### 3.2 Camada app

```
app/src/main/java/com/tamerslab/
  domain/repository/VpetRepository.kt        interface (sem Android/Room) — leituras pendentes
  di/repository/RepositoryAdapters.kt        implementação Koin
  transfer/vpet/VpetPendingReadStore.kt      leituras não mapeadas em DataStore (JSON)
  transfer/vpet/VpetImportService.kt         resolve espécie + cria parceiro APP/IMPORTED
  transfer/vpet/VpetReadController.kt        orquestra sessão+transporte, estado de tela
  transfer/ui/vpet/VpetSectionScreen.kt      seção VPet (escolher modelo, conectar, ler)
  transfer/ui/vpet/VpetResultCard.kt         resultado + “Importar” / “Mapear espécie”
  transfer/ui/vpet/VpetPendingList.kt        leituras pendentes + mapeamento manual
  transfer/ui/vpet/VpetDevicePicker.kt       (USB: A-Com encontrado / instruções OTG)
  navigation/TransferNavigation.kt           + TransferSection.VPET
  transfer/ui/TransferHubScreen.kt           página "vpet"
  di/AppModule.kt                            bindings (factory de sessão, store, import)
  AndroidManifest.xml                        usb.host + permissões (só USB nesta fase)
  res/values*/strings.xml                    textos pt-BR + en (default)
```

Importação (sem migração Room):

```kotlin
// VpetImportService (transação)
val canonical = speciesBinding.canonicalIdentityFor(read.nombre)     // alias-aware
val definition = expandedCatalog.definitionFor(canonical.id)         // CardCharacter catálogo
db.participation?.snapshot()?.requireCard(definition.id)             // gate; falha → motivo
AppPartnerFactory.createLocked(
    db, definition, individualId = IndividualIdentity.generate(), now = clock(),
    nickname = read.nombre, activate = false, origin = IndividualOrigin.IMPORTED)
```

- D4 (novo indivíduo por leitura): nenhum índice de dedupe. `hex`/`modelo/versão/índice`
  entram apenas no card de resultado e no log (Timber), não no banco.
- D7: se `canonical == null` **ou** o gate falhar, salva `VpetPendingRead` (DataStore):
  `{id, readAt, model, version, index, name, stage, attribute, power, hex, blockReason}`.
  UI mostra “mapear espécie” (busca no catálogo canônico) → resolve → importa e remove da
  lista; ou “descartar”.

### 3.3 UX (seção VPet)

1. Seção VPet → escolha do modelo do VPet (lembrada; default DM20) + instruções
   (“ponha o vpet em BATTLE”).
2. Transporte A-Com (USB): “Conectar” → permissão USB (PendingIntent) → “Conectado”.
3. “Ler” → progresso por pacote (`progreso` do protocolo) → resultado:
   nome lido, espécie canônica, etapa, atributo, poder, versão do aparelho, hex (detalhe).
4. Ações: **Importar para o Storage** (cria parceiro APP) · **Mapear espécie** (se
   pendente) · **Ler de novo** · **Descartar leitura**.
5. Lista “Leituras pendentes” no rodapé da seção (nome lido, aparelho, data, motivo).
6. Debug builds: botão “Simular leitura” alimentando `ScriptedVpetLink` (sem hardware).

## 4. Fases, entregáveis e critérios de aceite

Flag `FeatureFlags.VPET_TRANSFER` (default **off**) esconde a seção VPet até o aceite;
o código entra pronto mas inerte (CONTRIBUTING).

### Fase 0 — Núcleo do protocolo (sem UI)
Entregáveis: módulo `:hardware-vpet`, `VpetCommand`, ROMs, decoders, tabelas JSON, sessão;
`tools/vpet/extract-lists.mjs`; testes JVM.
Aceite: ROMs batem com o `lector.js` (vetores dourados); decoders cobrem os 6 modelos
(DM20/Pen20/DMX/PenZ/DMC/PenC) incluindo checksums e casos inválidos; sessão com transporte
fake exercita 30 s de timeout, reenvio por versão errada, até 8 tentativas, progresso e
cancelamento. Gate: `.\gradlew.bat :hardware-vpet:test`.

### Fase 1 — A-Com USB + tela de leitura (simulável)
Entregáveis: `AcomUsbLink`, permissão USB, seção VPet (flag), picker/instruções, tela de
leitura com progresso, resultado (sem importar), transporte scriptado + botão debug.
Aceite: JVM/Compose tests com `ScriptedVpetLink`; app instala e a tela funciona em aparelho
físico com simulação; sem hardware real ainda. Gates: `:app:testIntegrityCheckUnitTest`,
`:app:lintIntegrityCheck`.

### Fase 2 — Importação + pendências
Entregáveis: `VpetImportService`, `VpetPendingReadStore` (DataStore), resultado com
“Importar”, lista de pendentes + “mapear espécie”, strings pt-BR/en.
Aceite: testes de importação (espécie conhecida, desconhecida, gate bloqueado, duas
leituras = dois indivíduos), parceiro aparece no Storage como APP/IMPORTED e participa dos
fluxos existentes; remoção/rollback ok. Gate idem Fase 1.

### Fase 3 — Bluetooth (quando priorizado)
Entregáveis: `BtComLink` (BLE NUS) + `BtSerieLink` (SPP) + scan/lista; aceite com hardware
quando disponível; mantém fakes nos testes.
Aceite: leitura por BT igual à USB com hardware; se sem hardware, fase pronta porém
desabilitada por flag.

### Fase 4 — Integração final
Entregáveis: entrada definitiva na seção, atalho “importar e enviar”, textos/locales,
docs (`analysis/vpet-rea/` atualizado), remoção de código de simulação da build release,
flag removida após aceite.
Aceite: checklist manual completo (modelo × USB) + regressões dos fluxos de transferência
existentes.

## 5. Testes e verificação

- **JVM (módulo)**: `VpetDigiRomTest` (vetores do JS), `VpetDecoderTest` (6 modelos,
  pacotes sintéticos construídos pelo teste, checksum inválido, índice 0), `VpetReadSessionTest`
  (fake transport/clock: sucesso, timeout, reenvio por versão, 8 tentativas, cancelamento).
- **App**: `VpetImportServiceTest` (Robolectric/DB em memória: conhecida/desconhecida/gate/
  duplicata), `VpetPendingReadStoreTest` (serialização/DataStore fake), Compose UI test com
  controller fake.
- **Manual (quando houver A-Com)**: por modelo DM20/DMX/Pen20/PenZ/DMC/PenC — conectar,
  ler, conferir nome/etapa/poder, importar, ver no Storage, enviar ao relógio/jogo.
- **Gates**: `.\gradlew.bat :hardware-vpet:test :app:testIntegrityCheckUnitTest` e
  `.\gradlew.bat :app:lintIntegrityCheck`; rodar `ArchitectureBoundaryTest` (módulo novo já
  entra no padrão: `app → hardware-vpet`, nunca o inverso).

## 6. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Sem hardware para validar USB | transporte scriptado + simulação debug; checklist manual documentado para a primeira sessão com A-Com |
| Transcrição do protocolo JS→Kotlin | vetores dourados gerados do próprio `lector.js`; testes por modelo |
| USB CDC/ACM variar por adaptador | prober padrão + fallback CDC/ACM (como o original), teste com dispositivo real |
| Espécies fora do catálogo / formas X / jogress | leitura pendente + mapeamento manual (D7) |
| Gate de participação bloquear import | mostrar motivo e manter em pendências (não perde leitura) |
| BLE desconhecido (fase futura) | adiada (D6); UUIDs/fila já documentados |
| Regressão nos fluxos de transferência | flag default-off + testes existentes + aceite Fase 4 |

## 7. Riscos legais/escopo

- Protocolo recuperado do **seu próprio** app antigo; nada de terceiros.
- Escopo: só leitura dos 6 modelos do `lector.js`; escrita, quests/ovos e outras telas do
  DBT-Link ficam fora.

## 8. Próximo passo

Aprovado este plano, começo pela **Fase 0** (protocolo + testes, sem tocar em UI) e sigo
para a **Fase 1** (USB + tela simulável) no mesmo ciclo, mantendo a flag desligada até você
conferir. Só preciso do seu “ok”.
