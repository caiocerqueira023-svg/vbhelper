# VPet leitura — engenharia reversa do DBT-Link v18.9 (REA)

Análise estática do APK antigo `DBT-Link-v18.9.apk` para recuperar a implementação de
**leitura de Digimon de Virtual Pets** (DM20, DMX, Pendulum Ver.20th, Pendulum Z,
Digital Monster Color, Pendulum Color) e reutilizá-la no Tamers Lab.

- Alvo: `C:\Users\julye\Downloads\DBT-Link-v18.9.apk`
- SHA-256: `b96aebdb5b0a7c110ac2475ea04b8c26b980986bb555ad15abceab4aabe47030`
- Pacote: `online.battleterminal.vital` · versão 18.9 (code 191) · minSdk 28 · targetSdk 34
- Ferramenta: REA 6.9.0 · provider `jadx` (jadx-headless-mcp 0.7.1, JDK 25) · evidências
  retidas na sessão REA. O APK **não foi executado**; tudo abaixo é observação estática
  (decompilação JADX + assets recuperados). Nada foi validado em hardware real.

## Onde o código realmente está

O recurso de VPet do DBT-Link é uma **WebView** com um webapp empacotado nos assets e um
conector Kotlin só para transportes/ponte:

```
VpetActivity (Kotlin, AppCompatActivity)
 ├─ WebView  →  file:///android_asset/vpet/inicio.html
 │    ├─ assets/vpet/lector.js   ← PROTOCOLO DE LEITURA (JS, recuperado 100% verbatim)
 │    ├─ assets/vpet/escaner.html ← UI do escâner (usa LECTOR)
 │    ├─ assets/vpet/escaner-datos.js, datos.js ← tabelas do jogo (huevos/evos/spr)
 │    └─ scripts irmãos (pantalla.html, peleas.html, puente-huevos.js…)
 ├─ addJavascriptInterface(Puente, "Android")
 │    └─ conectar(via) / desconectar() / enviar(texto) / cerrar() / leer(k) / guardar(k,v) / idioma() / vibrar(ms)
 └─ VpetLink (interface) ← AcomUsb | BtCom (BLE NUS) | BtSerie (SPP)
       ↓ linhas ASCII terminadas em "\n"
   A-Com (adaptador USB)  ou  BTCom (adaptador Bluetooth)  →  IR  →  VPet
```

Fluxo de dados:

1. Kotlin recebe bytes do adaptador, acumula em `StringBuilder`, quebra por `\n`,
   faz `trim('\r',' ')` e chama `VpetActivity.linea(linha)`.
   `linea()` → `webView.evaluateJavascript("window.vpetLinea(...)")`.
2. O JS (`lector.js`) monta a DigiROM/commando, chama `Android.enviar(texto)`;
   o Kotlin repassa ao adaptador.
3. Status de conexão: `VpetActivity.estado(ok, msg)` → `window.vpetEstado({ok,msg})`.
4. Quando o JS decodifica o Digimon, a página `escaner.html` monta o resultado
   (nome, etapa, atributo, poder, versão, índice, hex). No DBT-Link isso vira
   “ovo desbloqueado”; no Tamers Lab basta ler esse objeto.

## Transportes (Kotlin, recuperados)

| Transporte | Classe | Detalhes observados |
|---|---|---|
| USB serial | `AcomUsb` | `usb-serial-for-android` (`com.hoho.android.usbserial`), prober padrão e fallback CDC/ACM; `setParameters(9600, 8, 1, 0)` (9600 8N1), DTR/RTS true; leitura por `SerialInputOutputManager`, charset ISO-8859-1. “Listo” após 350 ms (CDC) ou 1900 ms (outros drivers). |
| BLE UART | `BtCom` | NUS Nordic UART: serviço `6e400001-b5a3-f393-e0a9-e50e24dcca9e`; RX (escrita no adaptador) `6e400002-…`; TX (notificação) `6e400003-…`; CCCD `00002902-0000-1000-8000-00805f9b34fb`. Fila de write com chunk e retry 60 ms, `writeType=2` (no response). Também faz scan BLE (`startScan`) para achar o BTCom. |
| BT clássico | `BtSerie` | SPP `00001101-0000-1000-8000-00805f9b34fb`; falha → tenta BLE (`conectarBt$lambda$24$lambda$23`). |

Seleção do transporte em `conectarBt(Disp)`: usa `BtCom` (BLE) quando `uart == true`,
tipo BT == 2 ou (ble && tipo == 0); senão `BtSerie`. Mensagens “A-Com conectado / BTCom
conectado. Toca «Leer»…” vêm daí.

## Protocolo de leitura (recuperado de `assets/vpet/lector.js`)

Comentário original do arquivo (tradução): *“Lê DM20, DMX, Pendulum Ver.20th (pen20),
Pendulum Z (penz), Digital Monster Color (dmc) ou Pendulum Color (penc) pelo A-Com (USB)
ou BTCom (Bluetooth). É o mesmo protocolo do jogo web (03-002…03-008): para os de pacotes
de 16 bits o A-Com manda uma DigiROM «V2-…» (espera o vpet iniciar o combate) e o vpet
responde com pacotes «r:XXXX». A última verificação vai mal de propósito: o vpet entrega
os dados e mostra «erro» em vez de combater. Os Color usam blocos de 16 bytes
(«C1-…»/«C2-…», respostas «r:47444C43…» ou «r:47444470…»).”*

### Comandos enviados ao adaptador

- 16 bits (DM20/DMX/Pen20/PenZ): `V2-<palavra16>-<palavra16>-…`
- Color (DMC): `C1-<32 hex>-<32 hex>` (o adaptador reenvia a cada 2,5 s e após cada `t`)
- Pendulum Color (PenC): `C2-<32 hex>-<32 hex>` (envia uma vez; o adaptador repete)
- Encerrar/parar: string `"0"`

### DigiROMs (fórmulas exatas de `lector.js`)

`nib(p)` = soma dos nibbles de todas as palavras; `hex4` = 4 dígitos hex maiúsculos.

- **DM20** (`romDM20(ver)`), 10 palavras:
  `[0x0301, 0x0D0F, ((ver&15)<<4)|0xE, (2<<6)|0xE, (1<<10)|(26<<4)|0xE, (18<<4)|0xE, 0xE,0xE,0xE,0xE]`
  e `p[9] |= (((16 - nib(p)%16)%16 + 1) & 15) << 12`.
- **DMX** (`romDMX(ver)`): `"V2-23" + (ver&15).toString(16).toUpperCase() + "E-AA2E-560E-182E-0CAE-103E"`.
- **Pen20** (`romPen20(ver)`), 10 palavras:
  `[((ver&15)<<4)|0xE, (118<<6)|(0<<4)|0xE, 0xE, 0xE, (16<<4)|0xE, 0xE,0xE,0xE,0xE,0xE]`
  e `p[9] |= (((12 - nib(p)%16 +16)%16 + 1) & 15) << 12`.
- **PenZ** (`romPenZ(ver)`), 6 palavras:
  `[0x030E|((ver&15)<<4), 0x212E, 0x288E, 0x182E, 0x018E, 0x000E]`
  e `p[5] |= (((16 - nib(p)%16)%16 + 1) & 15) << 12`.
- **DMC** (Digital Monster Color): `C1-` + bloco fixo
  `47444C4300000000000B007300019406-47444C4300020002000000000000938B`
  com os últimos 4 hex XOR `0xFFFF` (“roto2” proposital).
- **PenC** (Pendulum Color): o mesmo bloco com `47444470`, prefixo `C2-` e roto2.

### Respostas e decodificação

Pacotes de 16 bits chegam como `r:XXXX` (também `s:…` marca início; `t` marca fim no
modo Color). `dec*` espera contagens e nibble de verificação:

| Tipo | Pacotes | Campos |
|---|---|---|
| dm20 | 10 | `version=(q[2]>>4)&15`, `indice=q[3]>>6`, `attr=(q[3]>>4)&3`, `poder=(q[5]>>4)&255`; válido se `nib%16==0`; nome = `LISTAS.dm20[indice]` (ou lista `pen20` quando version 6–9) |
| pen20 | 10 | `version=(q[0]>>4)&15` (6…9), `indice=(q[1]>>6)&255`, `attr=(q[1]>>4)&3`, `poder=(q[4]>>4)&255`, `copia=(q[5]>>14)&3`; válido se `nib%16==12`; fallback DM20 |
| dmx | 6 (só `r:` com nibble E) | `version=bits(q0,8,4)`, `set=floor(version/2)`, `etapa=bits(q1,0,3)`, `indice=bits(q1,3,7)`, `attr=bits(q1,10,2)`; nome = `LISTAS["x"+set][indice]` se `indice>0` |
| penz | 6 | `version=(q[0]>>4)&15` (6…11), `indice=(q[1]>>6)&127`, `attr=(q[1]>>4)&3`, `poder=(q[4]>>4)&255`, `etapaVpet=(q[1]>>13)&7`; Z1 para ver≤8, Z2 para ver≥9 (jogress 78–90 compartilhados); válido `nib%16==8` |
| dmc | bloco 16 B | 8 palavras: `ver = (w[3]<=4?w[3]:max(0,w[2]-1))+1`, `num=w[4]`, chave `"VV-NN"` → nome/etapa/attr de `LISTAS.dmc`; `poder=w[5]`; mapa VIP 17–19 |
| penc | bloco 16 B | `w[2]==18` → ignora (resultado de combate); `ver = w[3]<=5?(w[3]+1)%6:w[3]`, `num=w[4]`, chave `"V-NN"` em `LISTAS.penc`; `poder=w[5]`; soma `w0..w6 & 0xFFFF == w[7]` |

O nome/etapa/atributo por índice vêm da tabela `LISTAS` embutida no próprio `lector.js`
(gerada no DBT-Link a partir das tabelas do jogo). Isso é autossuficiente: **não é preciso
recuperar `datos.js`/`escaner-datos.js` para a leitura**; eles só servem à vitrine de ovos.

### Máquina de estados da leitura (`lector.js`)

- `empezar(tipo, {monitor, progreso, falla, listo})`, `parar()`, `version(t)`.
- `pedir()` envia a DigiROM, zera linhas, `armar(30000)` (timeout 30 s).
- Cada `r:` recebido atualiza progresso e rearma timeout (1,5 s) até `need` pacotes.
- `cerrar()` decodifica; se a versão decodificada ≠ versão usada, ajusta
  (`localStorage["vpet-ver-"+tipo]`) e **pede de novo** (o VPet só revela o número do
  Digimon quando a versão bate); até 8 tentativas; erro final
  “No llegó nada del vpet…”.
- Color: intervalo reenviando o bloco a cada 2,5 s (DMC) / uma vez (PenC), bloquear
  duplicados via `s:`/`t`, descartar PenC com checksum errado, acumular texto
  (bloco pode chegar partido, buffer 1500 chars).
- Código completo: [recovered/lector.js](recovered/lector.js) (33 KB,
  verbatim do APK; header com a explicação original do autor).

## Artefatos recuperados

- `recovered/vpet-webapp.zip` — webapp VPet completo (11.031 arquivos, 8 MB) compactado;\n  `recovered/lector.js` é a cópia verbatim do protocolo usada pelo gerador:
  `lector.js`, `escaner.html`, `escaner-datos.js`, `datos.js`, `inicio.html`, `pantalla.html`,
  `peleas.html`, `puente-huevos.js`, `ingles.js`, `quests.json`, sprites/UI/fontes.
- `recovered/decompiled/` — métodos Kotlin→Java (JADX) de `VpetActivity`, `AcomUsb`,
  `BtCom`, `BtSerie`, `VpetLink`, `VpetActivity$Puente`, `$Disp`, `$enchufe$1`.
- `recovered/kotlin/` — reconstrução Kotlin (transportes + bridge WebView + página mínima
  `reader.html` usando só `lector.js`) pronta para adaptar ao Tamers Lab.
- `analysis/vpet-rea/INTEGRATION.md` — plano de integração na aba Transferência.

## Inventário de evidências REA (amostra)

| Operação | Evidence ID | Objeto |
|---|---|---|
| inspect_android_package | `ev_582f5fccd95d4b6766aa8681422cbb946707b503c69b3f1ec1c6882394fc41b7` | APK + manifesto + 2831 classes |
| search_android_classes | `ev_e3ea5e8f3b16ee93112beaf82ddca7c28a8bb9301a915a88fc02260d5bdcf853` | inventário de classes |
| inspect_android_class | `ev_71b60d94dbbc69263e3bb98dc25baa51cfba7fca6631c0392aa90c416b88d9bf` | `VpetActivity` (56 métodos) |
| inspect_android_class | `ev_9e91289afdca0cbd63f517259ae43900434f7aaa04e5bdd764d101de6b74ada5` | `AcomUsb` |
| inspect_android_class | `ev_faaec99c1dcadb2b871ba9f4c61b7dba95dfb857b66ef42237177e43e0b9e9fb` | `BtCom` |
| inspect_android_class | `ev_f281f39bba3ed1168328d44e166cb76c8aabf51ae0f9c42b22a1f69fc69b0d3c` | `BtSerie` |
| inspect_android_class | `ev_e3c7a56027c875e950749d7425f0b34576c5ed84a1a5da70c429c092b2b03f44` | `VpetActivity$Puente` (JS bridge) |
| inspect_android_method | `ev_9a185f1d5b5b11a7879ecae6b4a289c41d605cf9acd72b3353a6cf552bc28dee` | `VpetActivity#linea` |
| inspect_android_method | `ev_efd716b8de50db361cb48637702f1115e04f74d6cf2639af8a4b4b2ea7c01563` | `AcomUsb#abrir` (9600 8N1) |
| inspect_android_method | `ev_3dc8b6604e9acc019e97994d34e8cac9f7137a6fe225cccb60e0b89732b2d42b` | `BtCom#<clinit>` (UUIDs NUS) |
| inspect_android_method | `ev_5dd9b5f8dda485cdb21d45a93c59b1db4b528ff418967384de32ff66355c8630` | `BtSerie#<clinit>` (SPP) |

## Limitações / desconhecidos

- Análise **estática**: o protocolo foi recuperado do código, não observado em execução.
  O APK não foi instalado/executado e não houve hardware.
- Os comentários do `lector.js` citam o “jogo web” (`fuentes/juego/js/03-002…03-008`) que não
  está neste APK; as tabelas `LISTAS` já vêm embutidas, então isso não bloqueia a leitura.
- `BtCom` (BLE) foi decompilado parcialmente (fila/retry/UUIDs identificados); detalhes de
  callback GATT (`onCharacteristicChanged`, descoberta de serviço, scan) estão nos smali/java
  recuperados e devem ser portados com teste em hardware.
- `Puente` expõe também `guardar/leer/compa/espacios/nidoEstado/nidoCuidar/questPremio/poder`,
  usados pelo jogo/ovos do DBT-Link; para o Tamers Lab só interessam
  `conectar/desconectar/enviar/cerrar/idioma/vibrar`.
- Sem verificação de runtime, o resultado não garante comportamento em cada versão de
  firmware dos V-Pets; as versões suportadas são as do código (DM20, DMX, Pen20, PenZ, DMC, PenC).
