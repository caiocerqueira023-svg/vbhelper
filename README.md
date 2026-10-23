# VBHelper

Application to interact with the Vital series, VB, VH, VBC and VBBE.

## Current state of the project

The project is still under development. The current implementation uses schema
**53**, with preserving migrations, a shared DIM-style Home for card-independent
partners, linked-save discovery, and a read-only local-network PC companion.
See [TESTING.md](TESTING.md) for verified checkpoints and native acceptance boundaries.

### Multi-game Transfer and local PC companion

**Current development focus:** actual World DS / first Story moves between a save and
the vault, followed by the other DS games. Time Stranger transfer development is paused.
The target Transfer flow uses PKForge's game-first organization, adapted into one
origin list with DS launcher icons, Vital Bracelet and other origins together.
Open a game, tap a sprite-matched individual, then use Move to Storage in its information
panel; the supported move must preserve nickname and progress without intermediate
review/mapping/materialization steps. This final flow is still being implemented.
DS format research uses
[DigimonNDSRomEditor](https://github.com/joaomlsantos/DigimonNDSRomEditor) as the
Dawn/Dusk reference; see [the DS notes](game-save-core/WORLD_DS_RESEARCH.md).

**Current prototype:** Home's top-left button opens a Games library. Open a game tile to see its
individuals, select Digimon in the grid, and use the selection action. **In game** and
**VBHelper Storage** switch the two sides on a phone; wider windows show both.
Selection is cleared when the observed save revision changes. Current imports are
explicitly labelled **Copy to Storage** because they leave the source intact.

Transfers go **game → VBHelper Storage** or **VBHelper Storage → game**. A stored
individual can go to any supported game that supports its species, independently of
its original game, once that game's conversion/writer capability is validated. Opening
a receiving game fixes the destination for its Storage actions; there is no redundant
destination chooser. Filename-based library grouping never enables an unsupported
reader or claims species compatibility. Game-cover credits are bundled in
`app/src/main/assets/transfer_game_art_credits.md`.

The latest origin-list, DS-launcher-icon and one-action move requirements supersede
this prototype's cover grid and copy-review UI; see sections 2, 4 and 20 of the
[implementation plan](MULTI_GAME_TRANSFER_PLAN.md).

The top-right menu keeps secondary workflows together:
- **Manage saves:** files/folders/PCs, access repair, copy review and saved import reviews.
- **Storage tools:** native mapping, app creation, source history, native-change review
  and destination previews. Stored tiles also open their individual actions directly.
- **Vital Bracelet:** outgoing-partner selection, bracelet preparation and NFC transfer.

Storage shortcuts open the Storage side with the relevant partner selected.

Transfer links local files/folders and paired PCs, refreshes persisted saves and
retains cached observations on failure. Start the Windows companion using
[these setup instructions](pc-save-companion/README.md), then choose **Transfer →
Connect local PC** on the phone. The distribution is built with
`:pc-save-companion:installDist` / `:pc-save-companion:distZip`.

Time Stranger supports inspection and **provisional party/bank snapshot copies**
into Storage. Copies receive app-owned permanent identities and receipts; the game
save stays intact. Possible matches to prior copies require identity review. Farm,
boundary and tail records remain review-only. This does not establish verified
game-wide native identity or enable game writes and managed moves. Game-specific
progression conversion still needs validation.

**Digimon World DS (USA), DraStic raw 64 KiB `.sav` files are now readable directly
on the phone.** In **Transfer → Game saves → Link save file**, select the regular `.sav` from an
accessible phone folder. The roster opens after reading and shows stored names,
level, native HP/MP/combat stats, aptitude and eight separate EXP channels. Linked
folders can discover these `.sav` files too. The reader was checked against the
user-supplied newly created DraStic save. Eligible **party Digimon can now be copied
into Storage provisionally**, then given a reviewed species/form and an APP
representation using the shared DIM-style Home. Choose the resulting app partner
to use Home. Native stats/EXP and original records remain separate from app starting
values; native personality stays absent and the app initializes its own personality
once. Native checksums, global identity and native writes remain unvalidated.

For saves with multiple populated copies, use **Review save copy** in Transfer.
That authority choice applies only to the exact source revision; a changed file
requires another review. Retrying an import does not create another individual;
later native observations require reviewed refresh. World DS IDs are source-local,
so equal IDs from independent linked saves are not automatically merged.

The companion also has an offline **Digimon World DS signature-research inspector** for raw
backup candidates and DeSmuME version-0 containers. It reports structural candidate
blocks and native roster-reference counts. The phone's observed DraStic profile does
not require that research signature; it uses the registered read-only reader.
See [the research notes](game-save-core/WORLD_DS_RESEARCH.md) for commands and evidence.

Transfer → Storage transfers → Game archives → **Preview game destination** evaluates a selected linked
save and Party/Bank placement using a fresh read. The planning report distinguishes
same-format native return from cross-format conversion, flags possible existing
records, and reports unvalidated/unmapped fields. It now also shows recorded app
progress in its own units, whether a retained native baseline matches the destination
format, pending native-change reviews and destination records that changed under the
same identity evidence. Linking either side to new progress or native reviews rebuilds
the revision-bound report on re-read. Up to 16 partners can be included for evaluation
against the same fresh destination read, with shared restrictions and one conflict
warning when selected partners share candidate identity evidence. It does not execute
a transfer. Farm transfer is outside scope and is not a prerequisite for party/bank
support.

The desktop inspector can rehearse the planned commit pipeline on copies only:
`--write-rehearsal <save-file> <output-dir>` verifies a fresh backup, rechecks the
source right before writing, refuses output inside the save folder, and replays the
commit journal through readback and re-parse verification. Encrypted Time Stranger
saves must additionally re-encode byte-identically (verified for the reference save).
`--edit-rename <save-file> <output-dir> <party:N|bank:N> <name-file>` builds one
stored-name candidate for the Time Stranger adapter with the exact semantics observed
in the game's own rename: the name bytes plus a terminator overwrite the record window
and nothing else changes. Its output was validated against the game's own rename sample
(identical except volatile time/live-state fields), and a candidate built from an
authorized slot loaded normally in the retail game (user-run swap, 2026-10-16).
`--edit-activate <save-file> <output-dir> <inactive:N>` is the staged experimental
counterpart: one stored flag byte on the first inactive bank-tail record, desk-verified
to differ by exactly that byte, testing whether the flag alone controls ownership
(in-game acceptance pending). The tool writes no game file and no writer capability is
enabled; see [the format-research notes](game-save-core/FORMAT_RESEARCH.md).

**Review native changes** compares archived native refreshes in their original units:
permanent parameter/EXP-channel changes, temporary HP/MP, personal edits, movement,
form/identity changes and unmapped bytes. This read-only review preserves pending
changes; it does not award growth or establish a game-to-game conversion policy.

The universal progression core retains native baselines and tracks actual changes
separately from app/DIM stat projections. Synthetic tests cover lossless zero-progress
A/B/C/A returns despite lossy intermediate scales; real-game round trips remain
subject to adapter/writer acceptance. See [the implementation plan](MULTI_GAME_TRANSFER_PLAN.md).

## Features

As of now, the project allows you to read characters, view characters stats, and send them back to your watch. 

You can also apply items to the characters read, such as special missions, or change timers, and store characters in the storage section.

You also earn new items every time an item such as a special mission (VB only) or a character completes an in-app adventure mission.

App also comes with a dex that will update every time a new character is added, and allows you to see evolution requirements and current adventure stage in the watch.

### Bundled Dot sprites

Open **Settings → Species and sprites**, select a species to edit, choose
**Choose sprite → Dot sprites**, select an appearance, then **Save** the species.
The alternatives work offline and include artist/device credits. The selected sprite
set is used within the chosen artwork priority, including portraits, Arena fighters
and result forms.

### Digimon graphics and universal participation

**Settings → Digimon graphics** offers **VB first** (loaded VB sprites, then Dot
fallback) and **Dot first** (Dot sprites, then loaded VB fallback). This choice is
cosmetic. There are no VB/Expanded gameplay modes, source restrictions or artwork
participation requirements. Missing art uses a neutral placeholder. Artwork changes
do not clear the active partner, pause activities, change stats or interrupt play.
Existing source-mode preferences migrate to the corresponding artwork priority;
legacy mode pauses resume without replaying paused activity time.

The current source includes catalog-backed encounters, quest targets, recruitment
and a high-trust first-partner World encounter. Scripted, AI-independent
onboarding and complete card-free gameplay acceptance remain pending. Sending a
catalog partner to a physical watch requires assigning an exact loaded DIM/BEM slot
of the same species and form. That association enforces normal card stat rules,
suppressing extra app/cross-product bonuses without deleting growth. Removing it
restores the balanced app stats. Known incoming device fields and outgoing local
representation state are durably archived with the permanent individual; transfer
receipt consumption does not delete those baselines. Physical conversion/round-trip
acceptance and product-specific growth equivalence remain adapter work.

Additional game/VPet readers, verified progression mappings, writers and managed
multi-game moves remain format-specific work. See
[EXPANDED_MODE_IMPLEMENTATION.md](EXPANDED_MODE_IMPLEMENTATION.md).

## How to set up

1. Download the latest version for VB Arena APK from a trustworthy source. If your download is a standalone APK, continue to step 2. Otherwise, if your download is an XAPK, do the following:

    1. Using your phone file manager, rename the XAPK file to ZIP, and extract its contents. You can also do this with any other device, such as Windows, macOS and Linux. 

    2. Once the files are extracted, look for the VB arena APK. Copy it somewhere else, you will need it.

2. Install an APK release for VB Helper. You will find the releases [here](http://github.com/nacabaro/vbhelper/releases). Download the latest release and install its APK.

    For an existing installation, upgrade in place to retain your collection.
    The current preserving migrations require no data reset; uninstalling or
    clearing application data discards local records.

3. Import secrets in the app. These secrets will allow the app to talk to the watch. On the main screen, click on the gear icon, then `Import secrets`.
    
    You will be prompted to choose a file. Choose the APK file that was previously obtained.

4. Import cards for bracelet interaction and optional exact card-stat association. App partners can participate without a card; the bundled Dot library is available offline. To import cards, do the following.

    1. Using your own DiM/BEm cards, dump the cards to your device. You can get an in-depth tutorial in [here](http://mrblinky.net/digimon/vb/dimcardtool/dimcardtool.html). You can download the dump tool from [here](http://mrblinky.net/digimon/vb/dimcardtool/)

    2. Once installed the tool and drivers, open the tool, connect your DiM/BEm reader hardware to yout computer and click on Read card.

    3. Transfer the resulting file to your mobile device. You can put them anywhere, as long as they are accessible. My recommendation is to put them under a folder called `Cards` in your `Internal storage` or `SD Card`

    4. In the app, click on import card. Next choose the BIN file corresponding to the card you want to import.

    **Note: if you do not import the card, whenever you attempt to read a character from th watch, the character you read will get deleted.** 

5. App will now be ready to be used.

## Planned features

- Online battles, undegoing development by `lightheel`.

- VitalWear compatibility, undergoing development by `cfogrady`.

- Support for multiple languages, not yet started.

- Database backup/restore.

## Credits

- `cyanic` for helping us understand more about the VB connection protocol.

- `cfogrady` for making both [`VB-DIM-Reader`](https://github.com/cfogrady/VB-DIM-Reader) and [`lib-vb-nfc`](https://github.com/cfogrady/lib-vb-nfc)

- `lightheel` for working on the online component in the application, both server and battle client.

- `shvstrz` for the app icon.
