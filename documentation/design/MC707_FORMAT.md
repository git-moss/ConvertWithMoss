# Roland MC-707 / MC-101 project format (`.mpj`)

Reverse-engineered for ConvertWithMoss from the Roland **MC-707** and **MC-101** GROOVEBOX
(firmware v1.82, model codes `RPG68`/`RPG69`) — the seven official Roland preset projects
(`MC707_PRESET_PROJECT`) and the init-project image embedded in both devices' firmware.
Everything below is validated against those Roland-authored files; **no hardware test has been
performed yet** (marked where it matters).

Both devices run the ZEN-Core engine (see `ZENCORE_FORMAT.md`); the project file embeds ZEN-Core
tone records in the same 1632-byte layout as the FANTOM's `PATa` records, so that document's tone
findings apply here and are extended by this one (partial strides, kit records).

> **Scope.** A project is the MC's only user-content container: it holds the 8×16 clip grid with
> sequences, the per-project **user tone bank (64)**, **user drum-kit bank (64)**, **user sample
> pool (500 slots + PCM)** and the looper-clip audio. ConvertWithMoss reads tones/kits that play
> *user samples* (with their PCM) and writes new projects containing converted sounds in the user
> banks. Sequences, looper clips and ROM-wave-only sounds are out of scope.

Projects live on the SD card as `ROLAND/PROJECT/<name>.mpj` (a sibling `startup.txt` holds the
file name of the project to auto-load). MC-707 and MC-101 use the **byte-identical** format and
platform tag `MC77` — one implementation covers both.

---

## 1. Container

All integers little-endian. **No CRC tables anywhere** (unlike `.SVZ`; this matches the `.SVD`
container variant).

**File header (16 bytes):** `u16 0x007E` (offset of the TOC's last byte, as in `SVD5`) + `"PRJ5"` +
10 zero bytes.

**TOC** at 0x10, seven 16-byte entries:

```
0x00  4  tag       "PRJa","STPa","SYSa","USRa","LPPa","LPDa","USDa"
0x04  4  platform  "MC77"
0x08  4  u32 offset (absolute)
0x0C  4  u32 size
```

**Section framing:** every section starts with a 16-byte block header
`u32 count, u32 unitSize, u32 dataOffset (16), u32 0`, then the payload. `PRJa`/`STPa`/`SYSa`/
`USRa`/`USDa` use `count=1, unitSize = size-16` (one opaque record). `LPPa` uses `count=128,
unitSize=16`; `LPDa` uses `count=128, dataOffset=32`.

**Fixed layout.** Section sizes and offsets are constant in every Roland-authored project —
only `LPDa` (looper audio) and `USDa` (user-sample PCM) vary; all following sections shift
accordingly:

| Section | Offset | Size | Content |
|---------|--------|------|---------|
| `PRJa` | 0x80 | 0x634090 | project name, 8×17 clip records (sound name + sequence), clip tone/kit banks (§4) |
| `STPa` | 0x634110 | 0x210 | setup |
| `SYSa` | 0x634320 | 0x210 | system |
| `USRa` | 0x634530 | 0x191420 | **user banks: tones, kits, sample params, multisample maps** (§2) |
| `LPPa` | 0x7C5950 | 0x810 | looper clip directory (128 × 16, `FFFFFFFF` = empty) |
| `LPDa` | 0x7C6160 | variable (empty: 0x820) | looper clip audio pool |
| `USDa` | after LPDa | variable (empty: 0x10) | **user sample PCM** (§3) |

---

## 2. `USRa` — user banks (payload offsets, i.e. section offset + 0x10)

```
0x000000  64 × 1632   user tone bank ("InitTone")                        §5
0x019800  64 × 3328   user drum-kit common records ("InitDrum")          §6
0x04D800  64 × 88 × 216  kit key records, keys 21(A0)..108(C8) per kit   §6
0x176800  500 × 84    sample parameter table                             §7
0x180C10  128 × 528   multisample key maps: name[16] + 128 × {u16 sample
                      (1-based table slot, 0 = unassigned), u8 level, u8 0}
                      — present but **unused in every Roland-authored
                      project**; written only by the opt-in multisample-tone
                      path (tone wave group 3, see §5/§9), hardware-unverified
```

(ends exactly at the payload size 0x191410)

## 3. `USDa` — user sample PCM

Payload: one `SMPh` header + one `SMPd` chunk per sample, concatenated. **Chunk order i =
sample-table slot i.**

```
SMPh (0x20): "SMPh" + u32 headerSize 0x20 + u32 version 2 + u32 sampleCount + 16 zero bytes
SMPd (0x30 header + data):
  0x00  4  "SMPd"
  0x04  2  u16 header size 0x30
  0x06  2  u16 ceil(sampleWords / 32768)
  0x08  4  u32 dataSize = roundUp1024(2*sampleWords + 256)
  0x0C  4  u32 sampleWords — total 16-bit samples across BOTH channels (frames = words/2)
  0x10 16  name, original file name incl. ".wav" (space padded)
  0x20  4  u32 0x8000 + chunkIndex
  0x24  4  u32 sample rate (44100 — the MC's native rate)
  0x28  4  garbage in Roland files (uninitialized pointer; two byte-identical duplicate
           imports carry different values here) — written as 0, evidently not validated
  0x2C  4  0
  0x30 ..  PCM, 16-bit LE interleaved stereo, zero-padded to dataSize
```

Samples are **always stored interleaved stereo at 44100 Hz** (mono material is imported by the
device as stereo; every duration in the preset projects only makes sense at 44100 stereo — e.g.
`pb_resoLoop2` = exactly 4.000 s, `Daedelus_Chord_2` = exactly 3.000 s).

## 4. `PRJa` — clip sound banks (read-side only)

Section-relative offsets (fixed):

```
0x000010  project name (16 chars)
0x0010F0  136 × 0x5C40  clip records: 8 tracks × (16 clips + 1 current);
                        +0x00 sound name copy, then sound-common + sequence data (opaque)
0x31EBB0  102 × 1632    clip TONE bank  (6 groups × 17 — slots 0-15 = clips, 16 = current)
0x3475F0  136 × 3328    clip KIT common bank (8 groups × 17)
0x3B5DF0  136 × 88 × 216 clip KIT key bank
0x630170  trailer (opaque)
```

The three banks butt-join exactly (tone bank end = kit bank start; kit commons end = key bank
start). Records use the same layouts as the `USRa` banks, so a detector can read the preset
projects' clip sounds too. (Why the tone bank has 6 groups where the kit banks have 8 is not
understood; the observed named records fit 6×17 exactly.)

## 5. Tone record (1632 bytes) — ZEN-Core `PATa`

Identical layout to the FANTOM `PATa` (`ZENCORE_FORMAT.md` §3.1); MC-707 findings extend it with
the partial strides:

```
0x000 16  name
0x098  1  1 in every Roland-authored (non-init) tone, 0 in InitTone — purpose unknown, write 1
Partial OSC block p = 0..3 at 0x0DF + p*0x7C:
  +0x00  1  wave group: 0 = ROM/preset wave, 2 = user sample, (3 = multisample — FANTOM;
            no MC project uses it, untested on MC)
  +0x01  1  0x08 for ROM waves, 0x00 for user samples
  +0x03  2  u16 wave number L — for group 2: 1-based sample-table slot
  +0x05  2  u16 wave number R (0; stereo lives inside the one sample slot on MC)
  +0x0D  2  u16 TVF filter type × 0x100 (P1: file offset 0x0EC)  0=OFF,1=LPF,2=BPF,3=HPF…
  +0x11  2  u16 TVF cutoff 0-1023   (P1: 0x0F0)
  +0x17  2  u16 TVF resonance 0-1023 (P1: 0x0F6)
TVA envelope p = 0..3 at 0x37A + p*0x10: 4 × u16 times + 4 × u16 levels, 0-1023
  (P1 = the documented FANTOM offsets 0x37A..0x388; defaults T=0/400/400/400·rel 150, L=1023)
Partial control blocks (36 bytes) at 0x5D0 + p*0x24: partial switch, key/velocity ranges —
  exact field semantics unresolved; ConvertWithMoss copies the complete block pattern of a
  Roland-authored single-partial user-sample tone verbatim (partial 1 active, 2-4 off).
```

**User-sample reference, device-verified:** Soaring's `Stack Chord 2D` plays sample slot 1
(`Daedelus_Chord_2`) with `group=2, waveL=1, waveR=0`; UK Nights has three more such tones.
Pitch tracks the keyboard chromatically relative to the sample's **original key** in the sample
table (§7). This is the pattern the creator writes for single-zone sources.

## 6. Drum kit records

**Kit common (3328 bytes):** name[16] + level/defaults (`+0x14` 0x7F, `+0x17` 0x64); rest left at
template defaults.

**Key record (216 bytes)** — one per key 21..108 (`record j` = key `21+j`), one wave per key:

```
0x00 16  wave name (display copy; 16 spaces = unassigned key)
0x11  1  key level (0-127; Roland kits use 0x78)
0x12  1  wave pitch key: 0x3C plays the sample at native pitch; write 0x3C + (key − zoneRoot)
         while keeping the sample-table original key at 0x3C — correct whether the engine
         interprets it absolutely or relative to the sample root
0x18  8  wave reference mode bytes:
           ROM inst:    00 00 01 00 01 00 08 00
           user sample: 00 01 01 00 01 02 08 00     (+0x1C on-switch, +0x1D wave group 2)
           unassigned:  00 01 01 00 00 00 00 00
0x20  4  u32 wave number — group 2: 1-based sample-table slot; ROM: inst index
0x24  4×28 velocity/WMT sub-blocks (defaults; not decoded)
0xCC  3×u16 TVA envelope times   (defaults 400,400,400; TR-909 kick: 26,200,100)
0xD2  3×u16 TVA envelope levels  (defaults 1023,1023,1023)
```

Device-verified: Mirio's `MOSS KIT` maps `Putt_Snare_1/Putt_Hat_1/…` onto keys via exactly this
pattern (and its duplicate sample import proves the wave number is the 1-based table slot: two
identical files at slots 0 and 3, keys reference 2 and 4). Multi-zone sources are written as kits —
one sample per key with per-key pitch, the same approach as the MV-8000 converter.

## 7. Sample parameter table (84 bytes per slot, 500 slots)

```
0x00 16  name without extension (space padded)
0x10  4  u32 1 = slot in use
0x40  1  1 when untrimmed & unlooped (device default), else 0
0x41  1  level (0x7F)
0x42  1  fine tune? (unclear; one preset sample has 0x7C here — written 0)
0x43  1  coarse tune, signed (one preset sample: 0xFE = −2 — written 0)
0x44  1  loop switch
0x45  1  original key (device import default 0x3C)
0x48  4  u32 start point   (stereo frames)
0x4C  4  u32 loop start    (stereo frames)
0x50  4  u32 end point     (stereo frame index; untrimmed = frameCount − 1)
```

All positions are in **stereo frames** (= `SMPd sampleWords / 2`).

## 8. The init-project template (from firmware)

The firmware update (`MC707_UPA_up.bin` / `MC101_UPA_up.bin`) is a plain **tar** archive of four
partitions. `RPG68_C0C` is a QSPI resource image (directory at 0x20: 32-byte entries, name[16] +
u32 offset + u32 size) containing the factory content banks (`tone_pcmx_cmn.bin`: 837 × 1432-byte
ROM tones — note: *not* the 1632-byte project record) and **`init.lzs`** — the compressed image of
the device's INIT PROJECT ("INIT PRJ").

`init.lzs` is textbook **Okumura LZSS**: 4096-byte ring buffer **pre-filled with zeros**, write
position starts at 0xFEE, flag bytes LSB-first (1 = literal, 0 = match), matches are
`u8 lo, u8 hi` → 12-bit *absolute* ring offset `lo | (hi & 0xF0) << 4`, length `(hi & 0x0F) + 3`.
The stream starts at file offset 0x20. It decompresses to 8,153,245 bytes — 243 bytes short of the
canonical 8,153,488-byte project; the missing tail is the self-repeating empty looper directory
pattern plus the 16-byte empty `USDa`, completed byte-identically from any preset project.

MC-707 and MC-101 embed **byte-identical** init projects. ConvertWithMoss ships this
(gzip-compressed, ~32 KB) as its write template: a Roland-authored, all-default canvas whose
`USRa`/`USDa` sections are patched with the converted content, the project name stamped at
`PRJa+0x10`, and the `USDa` TOC size updated.

## 9. ConvertWithMoss implementation

Classes in `format/roland/mc707`; read + write `.mpj`.

- **Creator** (`MC707Creator`): resamples to 44.1 kHz/16-bit stereo; single-zone sources become
  **user tones** (group-2 partial 1, template = a neutralized Roland user-sample tone record),
  multi-zone sources become **user drum kits** (per-key samples, baked per-key pitch); overlapping
  velocity layers are reduced to the loudest layer. Sample dedup by content, capacities 64
  tones / 64 kits / 500 samples per project. `createPresetLibrary` packs many sources into one
  project (the natural unit — one file = one SD-card project). An opt-in setting
  (`MC707CreatorUI`) writes multi-zone sources as **multisample tones** instead: a map-table
  record (one sample per key, loudest layer) plus a tone whose partial 1 uses wave group 3 with
  the wave-bank byte 0x08 and Wave L = Wave R = the 1-based map slot — the FANTOM's
  device-authored multisample-tone pattern on the shared record layout (on the FANTOM R = 0
  plays mono; the MC's own group-2 tones use R = 0 over their inherently-stereo sample slots,
  so each path copies its best evidence). Off by default: no Roland-authored MC
  project uses the map table, so the path cannot be validated against real files.
- **Detector** (`MC707Detector`): reads both the `USRa` user banks and the `PRJa` clip banks;
  extracts every tone/kit that references user samples, with filter/envelope (tones), per-key
  zones merged from kit keys, loop/root/level from the sample table, PCM from `USDa`.

## 10. Not decoded / open

- Tone partial control blocks (0x5D0): switch/range field offsets — written as verbatim pattern.
- The 128 × 528 multisample map table: no Roland-authored usage exists; wave group 3 on MC is
  therefore unverified (written only by the opt-in multisample-tone setting).
- Kit key WMT sub-blocks, sequence/clip records, looper `LPPa`/`LPDa` internals, `STPa`/`SYSa`.
- `SMPd+0x28` (garbage in Roland files, written 0) — flagged in case a future firmware validates it.
- **Hardware acceptance of written projects is untested** (no device available); the written files
  are byte-pattern-faithful to Roland's own, and unmodified projects round-trip byte-identically
  through the section writer.
