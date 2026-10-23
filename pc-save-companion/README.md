# VBHelper local PC save companion

This is a **read-only local-network** companion. The phone remains the main UI.
It exposes only supported candidate files under the save folders explicitly supplied
on the command line. The phone uses the shared decoder and refreshes persisted files;
neither emulator RAM nor save states are inspected. No game-save write endpoint exists.

## Windows setup

1. Install a Java 11+ **JDK** (including `keytool`).
2. Build `:pc-save-companion:installDist` or extract the generated distribution ZIP.
3. Run, for example:

   `.\bin\pc-save-companion.bat --save-dir "C:\Games\Digimon Story - Time Stranger\gamedata\savedata\YOUR_PROFILE"`

   From this repository the launcher is under
   `pc-save-companion\build\install\pc-save-companion\bin\`.
4. Keep the PC and phone on the same local network. If Windows asks, allow Java
   on that private network. The default TCP port is **7428** (`--port` overrides it).
5. In phone **Transfer → Connect local PC**, paste one of the printed pairing links.
   The link contains the local address, certificate fingerprint and read-access token.
6. Leave the companion running. Transfer refresh discovers new slots and rereads
   linked files. Stopping the companion makes sources unavailable while preserving
   their last successful phone observations and imported provenance.

Repeat `--save-dir` to authorize additional folders. Discovery is bounded to 32
candidate saves/2,000 entries and excludes `backups` directories and symlink files.
Names/sizes are discovery hints; the phone still validates actual contents.

## Pairing and persistence

The state directory defaults to `%USERPROFILE%\.vbhelper-companion`; override it
with `--state-dir`. Keep this private state directory if reinstalling the launcher:
it preserves the server identity and source IDs. HTTPS uses an exact certificate pin
from the pairing link, not a globally trusted/self-signed-certificate bypass.
Android encrypts the token with its app-specific Keystore key. Restoring a phone
backup without that key requires pairing again, not deleting collection data.

To revoke old pairing tokens, stop the companion, then restart it with
`--rotate-pairing` and re-pair authorized phones using the newly printed link.
The certificate/server identity and existing source IDs remain stable.

Only current format readers are supported. The PC transport remains read-only; the
phone supports provisional Time Stranger party/bank snapshot copies into Storage.
Farm/boundary/tail records remain review-only, later identity matches require review,
and game writes are unavailable. The phone's shared registry also reads the observed
Digimon World DS USA/DraStic 64 KiB raw `.sav` profile. These files can be linked locally
on the phone or discovered under authorized companion roots. The transport remains
read-only; the phone can copy eligible World DS party snapshots into Storage and use
the reviewed species/form/shared Home flow. Native checksums and game writing remain
unverified, and farm transfer is excluded.
The supplied fixture is now recorded in `game-save-core/WORLD_DS_RESEARCH.md`.

## Offline Digimon World DS research inspection

The distribution includes a structural inspector based on published USA (`ADNE`)
save-layout evidence:

```powershell
.\bin\pc-save-companion.bat --inspect-world-ds "C:\path\to\Digimon World DS.sav"
.\bin\pc-save-companion.bat --inspect-world-ds "C:\path\to\Digimon World DS.dsv"
```

It accepts raw candidates and validated DeSmuME version-0 containers, prints aggregate
counts for every complete signature candidate, then exits. It needs no phone or
pairing and creates no companion state. The original file remains intact. Farm
references are inspection-only; farm transfer is outside scope.

The report is research-only: region, active save block, checksum, ownership, text
encoding and native identity remain unverified. This signature-based tool is separate
from the phone's registered DraStic raw profile, which does not require that signature.
The phone's World DS party snapshot copies do not enable native EXP conversion or
writers. no$GBA containers require a raw backup
export first. Full offsets, pinned sources and boundaries are in
[`game-save-core/WORLD_DS_RESEARCH.md`](../game-save-core/WORLD_DS_RESEARCH.md).

## Controlled Time Stranger evidence

Capture and compare modes work offline and exit without starting the network server.
They copy the exact game-written encrypted document into private state; they never
edit the original, stamp native IDs, or promote candidate hashes to owned identities.
Use a private state directory **outside the active save folder and outside the repo**.
Labels are immutable: an existing capture is never overwritten.

From the extracted distribution root, before changing one known individual:

```powershell
.\bin\pc-save-companion.bat --save-dir "C:\path\to\profile" --capture 0001.bin --session time-stranger --label before-rename --expected baseline
```

Rename only that individual in the game, flush the normal save, then:

```powershell
.\bin\pc-save-companion.bat --save-dir "C:\path\to\profile" --capture 0001.bin --session time-stranger --label after-rename --expected rename
.\bin\pc-save-companion.bat --compare-captures before-rename after-rename --session time-stranger
```

Use the same `--state-dir` on every command if overriding the default. Samples live
under its `captures\SESSION\` directory as `capture-LABEL.bin` plus a small manifest.
The manifest and console report contain counts/revisions, not roster names, account
directory paths or native candidate IDs. Captured save files themselves are private
native data and must not be committed as public test fixtures.

Repeat one controlled change per before/after pair:

- `resave`: save twice without intentional changes.
- `cross-slot`: save that state into another normal slot and capture its file.
- `rename`, `personality`, `reorder`, `party-bank`, `farm`.
- `evolution`: evolve/de-evolve in separate pairs.
- `progression`: one known level/EXP/training change.
- `duplicates`: retain two same-species individuals with distinguishable names.
- `restore-branch`: compare an older game-written state with its continuation.

Reports separate normal active-candidate comparison from **research-only** persistence
across farm/review rows. They expose collisions, missing identity evidence, raw-record
changes and regressions. An `expectedChangeObserved` flag means the declared pattern
was seen; `establishesNativeIdentity` remains **false**. Together with in-game roster
counts and the known single change, these captures provide evidence for adapter
validation; the tool does not automatically enable imports or writers.

Original capture manifests remain immutable when the decoder improves. Comparisons
verify the original document hash/size, analyze both files with the current compatible
reader, and flag `analysisDiffersFromCapture` if its profile/version differs from the
capture metadata. This allows reanalysis without editing native evidence or repeating
the in-game experiment merely because the decoder was upgraded.
