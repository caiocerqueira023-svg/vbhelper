# Integração VPet na aba Transferência do Tamers Lab

Baseado na engenharia reversa do DBT-Link (ver [README.md](README.md)), a leitura de
Virtual Pet é: **transportes Kotlin (USB serial / BLE NUS / SPP) + `lector.js` em WebView**.
O código antigo de protocolo é recuperado verbatim; o que precisa ser novo é o invólucro
Android/Compose e o mapeamento do resultado para o armazenamento do Tamers Lab.

## Onde encaixa na UI atual

Fluxo atual de dispositivos (`app/src/main/java/com/tamerslab/transfer/ui/`):

```
TransferHubScreen (page = "devices")
  └─ TransferDevicesPane              ← escolhe o “parceiro” que vai sair
       └─ ScanScreen
            └─ ChooseConnectOption    ← HOJE: “VB → app” e “app → VB” (NFC)
                 └─ ReadingScreen / ReadingScreen/Write... (NFC)
```

Proposta: manter o fluxo NFC intacto e **acrescentar uma terceira origem de dispositivo**
“Virtual Pet (DM20 / DMX / Pendulum / Color)”:

1. `ChooseConnectOption` ganha um botão/ação “Ler Virtual Pet” (ou um card novo
   “Outros dispositivos”) → abre `VpetReadScreen`.
2. `VpetReadScreen` (Compose + `AndroidView(WebView)`):
   - abas/segmented: **A-Com (USB)** · **BTCom (Bluetooth)**;
   - botões Conectar / Ler / Parar; barra de progresso (o `lector.js` expõe `progreso`);
   - log de linhas recebidas (opcional, como o DBT-Link mostrava);
   - ao terminar: card do Digimon (nome, etapa, atributo, poder, versão do aparelho,
     tipo do VPet) + botão “Importar para o Tamers Lab”.
3. Importar → resolve espécie na base canônica do app e grava um indivíduo no Storage
   (mesmo destino que a leitura NFC usa hoje, ver abaixo).

Alternativa mais simples: um item no menu “···” do `TransferHubScreen` → `vpet` page,
sem tocar no fluxo NFC.

## Peças de código

Novas (sugestão de pacote `com.tamerslab.vpet`):

| Arquivo | Papel |
|---|---|
| `vpet/VpetLink.kt` | interface `enviar(String)` / `cerrar()` + `VpetReadResult` |
| `vpet/AcomUsbLink.kt` | USB serial 9600 8N1 (`usb-serial-for-android`) |
| `vpet/BtComLink.kt` | BLE NUS (UUIDs do DBT-Link) com fila de escrita |
| `vpet/BtSerieLink.kt` | SPP clássico |
| `vpet/VpetReaderSession.kt` | orquestra transporte + WebView, trata permissões e eventos USB |
| `transfer/ui/vpet/VpetReadScreen.kt` | UI Compose (conectar/ler/progresso/resultado) |
| `assets/vpet/lector.js` + `assets/vpet/reader.html` | protocolo (verbatim) + glue mínimo |

Versões de referência já prontas em [recovered/kotlin/](recovered/kotlin/) e
[recovered/vpet-webapp.zip/](recovered/vpet-webapp.zip/).

`reader.html` mínimo (não precisa de `escaner.html`/`datos.js`/`escaner-datos.js`, que só
servem à vitrine de ovos do DBT-Link):

```html
<script src="lector.js"></script>
<script>
window.vpetLinea = line => {};           // Kotlin→JS: entrega linhas recebidas
window.vpetEstado = e => {};             // Kotlin→JS: {ok,msg}
window.LECTOR_CTX = null;
window.vpetComecar = (tipo) => LECTOR.empezar(tipo, {
  monitor: () => {},
  progreso: p => Android.progreso(p),
  falla: m => Android.falla(m),
  listo: d => Android.listo(JSON.stringify(d)),
});
window.vpetParar = () => LECTOR.parar();
</script>
```

Kotlin→JS: `window.vpetLinea(<JSON string>)` e `window.vpetEstado({ok,msg})` 
(iguais ao DBT-Link: `VpetActivity.linea/estado`).
JS→Kotlin (`@JavascriptInterface`): `conectar(via)`, `desconectar()`, `enviar(texto)`,
`cerrar()`, `progreso(p)`, `falla(msg)`, `listo(json)` (estes dois últimos são a adição
necessária para receber o resultado; o DBT-Link usava a própria página para o resto).

## Permissões / manifesto / dependências

`app/src/main/AndroidManifest.xml` precisa de:

```xml
<uses-feature android:name="android.hardware.usb.host" android:required="false" />
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

- NFC/manifest atual já tem `ACCESS_FINE_LOCATION`.
- Não é preciso serviço em background: conectar/ler acontece com a tela aberta
  (o DBT-Link também faz `registerReceiver` para `USB_DEVICE_ATTACHED/DETACHED`).
- Dependência USB: `com.github.mik3y:usb-serial-for-android:3.8.1`
  (jitpack já está em `settings.gradle.kts`). Adicionar em `gradle/libs.versions.toml` +
  `app/build.gradle.kts`.
- WebView: `android.webkit.WebView` (nativo) + `AndroidView` do Compose — sem dependência nova.

## Ligação com o Storage (reuso do caminho NFC)

Hoje a leitura NFC termina em `ScanScreenController.flushCharacter(cardId)` →
`VitalWearCharacterImporter.importCharacter(Character)` (app/src/main/java/com/tamerslab/
transfer/data/). `Character` é o modelo de hardware (`com.tamerslab.hardware...`) com
dados de espécie/estágio/atributo/vitais e `cardId/nome do card`.

Duas opções para o VPet (recomendo a **A**, reusa tudo):

- **A) Adapter para `Character`**: criar `VpetCharacterAdapter` que monta um `Character`
  sintético a partir de `VpetReadResult`:
  - `speciesName` = nome decodificado (`LISTAS[...]`), `speciesId` resolvido no catálogo
    canônico do app (o nome varia: "Agumon (Ver.20th)", "War Greymon", "Omegamon Alter-S"…);
  - estágio/atributo/poder mapeados para os campos equivalentes (`NfcCharacter` tem
    `Attribute`, `InjuryStatus`, etc.); vitais default;
  - `cardName = "VPet <tipo> <versão>"`, `cardId = null` (ou um card virtual por tipo);
  - chama `VitalWearCharacterImporter.importCharacter(...)` ou, se o importer exigir
    card real, um `VpetCharacterImporter` mínimo que grava `UserCharacter` +
    `IndividualIdentity` do mesmo jeito.
- **B) Importador próprio**: gravar direto via `AppDatabase.userCharacterDao()` +
  `IndividualIdentity`/policies. Mais controle, mais código duplicado.

Guardar procedência no indivíduo (ex.: `nativeIdentity = "vpet:<tipo>:<versão>:<índice>"`,
`characterSource = "vpet"` na `CharacterTransferPolicy`) para deduplicar/“já importado”.

## Mapeamento do resultado da leitura

`VpetReadResult` (JSON vindo do JS / `dec*`):

```kotlin
data class VpetReadResult(
    val tipo: String,        // "dm20" | "dmx" | "pen20" | "penz" | "dmc" | "penc"
    val version: Int,
    val indice: Int,
    val nombre: String?,     // pode ser null se índice 0/desconhecido
    val etapa: Int?,         // 0=Baby I … 5=Super Ultimate (tabela do lector.js)
    val attr: Int?,          // 0=Vaccine, 1=Data, 2=Virus, 3=Free
    val poder: Int?,         // dm20/pen20/penz/dmc/penc
    val hex: String,
    val valido: Boolean,
)
```

Resolução da espécie: normalizar (`BattleSpeciesIdentity.normalize` já existe no projeto)
e casar com `CanonicalSpeciesDto` (nome canônico/aliases). Para jogress/color, o `hex` e
`version` ficam guardados para auditoria.

## Fases sugeridas

1. **Protocolo + USB (A-Com)** — `lector.js` + WebView + `AcomUsbLink` + tela Compose;
   resultado exibido e logado. Teste em DM20/DMX real.
2. **Bluetooth (BTCom BLE NUS + SPP)** — portar `BtCom`/`BtSerie` do java recuperado,
   incluir permissões BLUETOOTH_SCAN/CONNECT e seletor de dispositivo.
3. **Importação para o Storage** — adapter `Character`/importador + procedência +
   deduplicação; botão “Importar para o Tamers Lab”.
4. **Integração na Transfer** — opção “Virtual Pet” no `TransferHubScreen` (`devices`),
   envio do indivíduo importado para jogo/relógio já existente.

## Riscos / cuidados

- WebView com `addJavascriptInterface` → só expor métodos mínimos, `setAllowFileAccess`
  apenas do asset, e o JS é executado do próprio APK (não há rede).
- USB: precisa `UsbManager.requestPermission` + receiver; o adaptador A-Com é CDC/ACM a
  9600 baud (comportamento observado no código, não testado em hardware).
- BLE: `writeCharacteristic` em chunks com `writeType=2` e retry de 60 ms; no Android 13+
  `writeCharacteristic(char, bytes, 2)`; validar callback `onCharacteristicWrite`.
- Espécies fora do catálogo canônico (formas “X”, Ver.20th, jogress) precisam fallback
  (manter nome + `nativeIdentity` “unknown-species” em vez de falhar a importação).
- `lector.js` é autossuficiente (tabelas LISTAS embutidas) — não é preciso copiar
  `datos.js`/`escaner-datos.js` nem o resto do jogo.
- Só **leitura**: o DBT-Link não escreve no VPet; o Tamers Lab também não deve tentar.
