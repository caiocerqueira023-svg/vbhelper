# VB Lab Offline — instalação do APK único (100% offline)

**Arquivo a instalar: `C:\Users\julye\Downloads\VB-Lab-Offline-Phase47-v2.apk`**

> **Você NÃO precisa instalar nenhum arquivo extra.**
> Não precisa de OBB, XAPK, asset pack, conta, login, internet, Wi-Fi, root nem Play Store.
> O APK contém **todo** o jogo: 1.056 bundles de assets + 2.066 páginas do catálogo/páginas web,
> catálogo reescrito para caminho local, atualização de catálogo desligada e **permissão de
> internet removida** — ou seja, o app não tem como baixar ou pedir arquivos.
> NFC é necessário **apenas** para transferir com a pulseira Vital Bracelet; para jogar offline, não.

---

## 1. Confira o arquivo (checklist)

| Item | Valor |
| --- | --- |
| Nome do arquivo | `VB-Lab-Offline-Phase47-v2.apk` |
| Tamanho exato | **385.560.247 bytes** (≈ 367,7 MB) |
| SHA-256 | `5590cf9c8c063f1332504ef0a503fb5710480804d5fe11ef76d61695378b2374` |
| Pacote Android | `net.digimon.digibb.offline` (convive com o VB Lab original da Bandai) |
| Versão | 1.4.6 (versionCode 220041) |
| Assinatura | APK Signature Scheme **v2 + v3** (mesma chave dos builds anteriores do projeto) |
| Android mínimo | **8.0 (API 26)** |
| Arquitetura | **arm64-v8a** (todos os celulares Android modernos) |
| Permissões | `NFC` e `ACCESS_NETWORK_STATE` — **sem** permissão de internet |

**Como conferir o SHA-256 no PC (PowerShell):**

```powershell
Get-FileHash "C:\Users\julye\Downloads\VB-Lab-Offline-Phase47-v2.apk" -Algorithm SHA256
```

Deve retornar exatamente `5590CF9C8C063F1332504EF0A503FB5710480804D5FE11EF76D61695378B2374`.
Se o hash ou o tamanho não baterem, o arquivo foi corrompido na transferência: copie de novo
(de preferência por cabo USB). **Não instale** um arquivo que não bata com o hash.

> Não instale os outros arquivos da pasta Downloads: `VB-Lab-Offline-Phase47.apk` (v1, antigo),
> `VB-Lab-Offline-Phase47-v2-baseline.apk` (artefato técnico de verificação) e o `.xapk` original.
> O único arquivo para o celular é o **`VB-Lab-Offline-Phase47-v2.apk`**.

---

## 2. Espaço necessário no celular

- APK: **368 MB**.
- Instalação: cópia do APK + bibliotecas nativas extraídas (~39 MB) + otimização ≈ **450–500 MB**.
- Durante a instalação o Android precisa de espaço temporário extra.

**Reserve pelo menos 1,5 GB livres (ideal: 2 GB).** Se faltar espaço o Android mostra
“Aplicativo não instalado” / “Não há espaço suficiente”.

---

## 3. Passo a passo da instalação

1. **Transferir por cabo USB** (recomendado para 368 MB):
   - Conecte o celular no PC e escolha “Transferir arquivos / MTP”.
   - Copie `VB-Lab-Offline-Phase47-v2.apk` para a pasta `Download` do celular.
   - Alternativas: Google Drive/OneDrive (deixe baixar por completo). **Evite WhatsApp** (limite de 100 MB).
2. No celular, abra o **Gerenciador de Arquivos / Files** e toque no APK.
3. O Android vai avisar que a instalação de apps desconhecidos está bloqueada para o gerenciador:
   toque em **Configurações** no próprio aviso → ative **“Permitir desta fonte / Instalar apps desconhecidos”**
   → volte → **Instalar**. (Você pode desativar essa permissão depois.)
4. Se aparecer verificação do **Play Protect** (“app não verificado”, “pode ser perigoso”):
   toque em **“Instalar mesmo assim” / “Continuar”**. Se bloquear de vez, abra a Play Store →
   foto do perfil → **Play Protect** → engrenagem → desative temporariamente
   “Verificar apps” → instale → **reative em seguida**.
5. Toque em **Instalar** e aguarde (pode levar de 30 s a 2 min).
6. Abra **“VB Lab Offline”** (ícone único).

### Primeira abertura (o que esperar)

1. Aparece uma tela de aviso legal em inglês: **“[Reminder from DigiKidult]”**.
   É o aviso do patch (uso pessoal/não comercial, projeto de fã não oficial).
   **Não pede arquivos nem download.**
2. Toque no botão azul **“acknowledged”** na parte de baixo da tela.
3. O jogo abre (tela de título). Na primeira execução pode levar até ~1 minuto; depois abre rápido.
4. Se o celular perguntar sobre **NFC**, é opcional: permita apenas se for usar a pulseira Vital
   Bracelet. Sem NFC o jogo funciona normalmente.
5. Dica de verificação: ligue o **modo avião** — o app continua abrindo e funcionando (ele é offline).

---

## 4. Por que a v1 “pedia arquivos” (e o que mudou na v2)

A v1 tinha **dois ícones** na tela inicial:

| Ícone (v1) | O que era |
| --- | --- |
| “VB Lab Offline 測試版” | O jogo (mostra o aviso legal e abre o jogo). |
| “VB 離線備份與復原” | Uma **tela de backup/diagnóstico em chinês** adicionada pelo patch: ela exporta um ZIP de backup e abre o seletor de arquivos do Android. Não é o jogo e **não é necessária para jogar**. |

Era provavelmente essa segunda tela (ou o seletor de arquivos que ela abre) que parecia
“o APK pedindo arquivos”.

**Na v2:**
- existe **um único ícone**: **“VB Lab Offline”** (o jogo);
- o ícone chinês de backup foi removido da tela inicial (a função continua existindo, mas só por ADB — avançado);
- o nome do app deixou de ter “測試版” (chinês);
- metadados de “split APK” da Play foram removidos;
- o APK foi **reconstruído do zero e re-verificado**, e continua **100% autocontido**.

---

## 5. Solução de problemas

| Mensagem / sintoma | Causa provável | Solução |
| --- | --- | --- |
| “Aplicativo não instalado” / “App not installed” | Espaço insuficiente | Libere ≥ 1,5 GB e tente de novo |
| “Aplicativo não instalado” mesmo com espaço | Arquivo corrompido na transferência | Confira tamanho e SHA-256 no PC; copie de novo por USB |
| “Aplicativo não instalado” / conflito de assinatura | Existe versão antiga instalada com **outra** chave (ex.: build de outra pessoa) | Desinstale a versão antiga (perde os saves dela) e instale a v2. Builds desta série usam a **mesma** chave → instalar por cima atualiza e mantém os dados |
| “App não é compatível” / instalação recusada | Celular 32-bit (raríssimo hoje) | Este APK é **arm64-v8a**; só instala em aparelhos 64-bit |
| Play Protect: “app não verificado” / “pode ser perigoso” | APK assinado fora da Play Store | “Instalar mesmo assim”; se bloquear: Play Store → Play Protect → desativar “Verificar apps” → instalar → reativar |
| Samsung: instalação bloqueada / “app bloqueado” | **Auto Blocker** (One UI 6.1+) bloqueia apps de fora | Configurações → **Segurança e privacidade** → **Bloqueio automático** → desligar → instalar → religar |
| Android 14+: “Configurações restritas” ao instalar | Proteção para APKs recebidos por apps de mensagem | Abra o APK pelo **Gerenciador de Arquivos** ou, em Configurações → Apps → (app que está abrindo) → menu ⋮ → **Permitir configurações restritas** |
| Android novo: aviso de **verificação de desenvolvedor** | Política nova do Google para apps fora da Play | Permita “apps não verificados” nas opções de segurança/desenvolvedor ou instale via **ADB** (seção 6). É política do Android, não defeito do APK |
| “Arquivo inválido” / “erro de análise do pacote” | Transferência corrompida | Verificar SHA-256 e reenviar |
| Abre um **seletor de arquivos** / pede ZIP | Era o ícone chinês de backup da **v1** | Instale a **v2** (ícone único “VB Lab Offline”). Se aparecer dentro do jogo, avise com print |
| Tela em inglês com botão “acknowledged” | Aviso legal do patch (normal) | Toque em “acknowledged” — não há nada a baixar/instalar |
| Pede permissão de **NFC** | Necessário só para a pulseira | Pode negar e jogar offline; permita se for transferir com a Vital Bracelet |
| App fecha sozinho ao abrir | Espaço/instalação incompleta | Confira espaço, reinstale; se persistir, capture `adb logcat` (seção 6) e envie |
| Aparece pedido de “baixar dados” | **Não é deste APK**: ele não tem permissão de internet e todos os assets estão embutidos | Confirme que abriu **VB Lab Offline** (e não o VB Lab original/da Play Store, que tenta baixar da rede) |

---

## 6. Instalação por ADB (avançado, opcional)

Se o instalador do celular insistir em bloquear, com depuração USB ativada:

```powershell
adb install -r "C:\Users\julye\Downloads\VB-Lab-Offline-Phase47-v2.apk"
adb shell am start -n net.digimon.digibb.offline/jp.co.chariot.nfc4digivb.ReminderActivity
```

- **Não** use `install-multiple`: é um APK único, não um conjunto de splits.
- A tela de backup/diagnóstico escondida (só por ADB; não é necessária para jogar):

```powershell
adb shell am start -n net.digimon.digibb.offline/jp.co.chariot.nfc4digivb.RecoveryActivity
```

---

## 7. Checklist pós-instalação

- [ ] O ícone **“VB Lab Offline”** aparece na tela inicial (apenas **um** ícone desta série).
- [ ] Abre na tela do aviso legal em inglês → “acknowledged” → tela de título do jogo.
- [ ] Com **modo avião ligado**, o app continua abrindo (prova de que é offline).
- [ ] (Opcional) Em Configurações → Apps → VB Lab Offline → Permissões, só aparece NFC.
- [ ] O VB Lab original da Bandai (se estiver instalado) continua separado e não é afetado.

---

## 8. Observações

- **Dados/saves:** o pacote é `net.digimon.digibb.offline` (renomeado), então é uma instalação
  separada do VB Lab original. Atualizar de uma build anterior **deste projeto** com a mesma
  chave instala por cima e mantém os dados.
- **Backup antes de atualizar:** a tela de backup (seção 6) exporta um ZIP com os dados do app;
  ela não é necessária para jogar.
- **Idioma:** o patch não traduz a interface do jogo (o jogo original é japonês/inglês).
- **Aviso legal:** modificação de fã, não oficial, para uso pessoal/não comercial; Digimon e os
  assets pertencem à Bandai e detentores de direitos. O aviso dentro do APK é mantido de propósito.

---

### Hashes de referência

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| `VB-Lab-Offline-Phase47-v2.apk` (**instalar este**) | 385.560.247 | `5590cf9c8c063f1332504ef0a503fb5710480804d5fe11ef76d61695378b2374` |
| `VB-Lab-Offline-Phase47.apk` (v1 antigo, não usar) | 385.560.252 | `54d0ac1c1c4b42b1cc3f45c6f8406086dac535fccc5afe999124400d02dc0829` |
| `VB-Lab-Offline-Phase47-v2-baseline.apk` (artefato técnico, não usar) | 385.560.252 | `33b611edabe08461010e246be2c0d93e694c9430a89b42cdfd1bca4fcadb09f2` |

Evidências técnicas da verificação: `analysis/vb-lab-apk/DIAGNOSTICO.md` e `analysis/vb-lab-apk/logs/`.
