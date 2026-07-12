# Roland ZEN-Core file formats

Reverse-engineered for ConvertWithMoss. Covers the Roland **FANTOM-0** (FANTOM-06/07/08, firmware
`mi078`, ARM64) and the **FANTOM 6/7/8** / **6/7/8 EX** (firmware `ky019`, ARM32). All of these run
the same **ZEN-Core** sound engine and share the same on-disk formats and internal platform tag
`KY19`/`KY019`; differences are capacity/feature only and are called out per section.

The ZEN-Core lineage is wider than the FANTOM: the same container and voice engine are used by the
MC-707/MC-101, GAIA-2, JUNO-X and JUPITER-X/Xm (all `RPG68`/`MI078`-class parts, verified from their
firmware — see §10). A `.svz` written for the FANTOM therefore imports across that whole range.

MIDI SysEx model ID: `00 00 00 5B`. This document is about the *file* formats on USB / internal
storage, not SysEx.

> **Scope.** `.SVZ` is ConvertWithMoss's **primary target format for the FANTOM** — the one
> self-contained, device-importable interchange (a keyboard multi-sample written as a single tone or
> a multi-tone bank). The `.SVD` backup, `RFWV` `.SMP` samples and `RFPD` pad maps are read-side
> (`RFWV`/`RFPD` are write-capable, but the creator's output is `.SVZ`). The paid Roland Cloud `.SDZ`
> expansion format is deliberately **not** supported (licensed, encrypted content).

---

## 1. Containers at a glance

| Extension | Magic | Field endian | Content | CWM use |
|-----------|-------|--------------|---------|---------|
| `.SVZ` | `SVZa` | little-endian | Self-contained ZEN-Core pack — importable tone/bank/sample. PCM is embedded. | **Read + Write — the only format CWM handles** |
| `.SVD` | `SVD5` | little-endian | Full/partial backup: scenes, tones, drum kits, user multisamples + sample metadata (no embedded PCM). | Reference only (not parsed) |
| `.SMP` | `RFWV` | **big-endian** | One on-device sample: 0x20 header + 16-bit **big-endian** PCM. | Reference only (not parsed) |
| `PADCONF.BIN` | `RFPD` | **big-endian** | Maps the 16×4 sample pads to samples + params. | Reference only (not parsed) |
| `.SDZ` | `VEXP` | little-endian | Roland Cloud paid expansion (encrypted). | **Not supported** |

> **CWM scope:** ConvertWithMoss reads and writes **`.SVZ` only**. `.SVD`, `RFWV` `.SMP` and `RFPD`
> `PADCONF.BIN` are documented here as reverse-engineering reference — the earlier read paths for them
> were removed to keep the format focused on the importable SVZ round-trip.

`.SVZ` and `.SVD` share one container engine (`NS_SVZ::CSvz`); they differ in one important way —
`.SVZ` blocks carry a **per-record CRC32 table**, `.SVD` blocks do not (§3). `RFWV`/`RFPD` are
Roland's own big-endian wave/pad formats with different framing (§6, §7).

All integers **inside** `SVZa`/`SVD5` are **little-endian**. `RFWV`/`RFPD` are big-endian, and their
audio is 16-bit big-endian PCM (byte-swap on read/write, as for the MV-8000).

---

## 2. Shared container framing

Both files begin with a 16-byte file header, then a table-of-contents (TOC) of 16-byte entries, then
the blocks in file order.

**TOC entry (16 bytes):**

```
0x00  4  tag        e.g. "DIFa","PATa","USPa","MSPa","USDa","PRFa","SMPa",...
0x04  4  platform   "ZCOR" in a .SVZ, "KY19" in a .SVD
0x08  4  u32 offset  absolute file offset of the block (LE)
0x0C  4  u32 size    block length in bytes (LE)
```

The TOC runs from 0x10 until the first block's offset.

**Block header (16 bytes)** — every block starts with:

```
0x00  4  u32 count       number of records
0x04  4  u32 unitSize    bytes per record (0 for the variable USDa block)
0x08  4  u32 dataOffset  where the records start, relative to the block
0x0C  4  u32 reserved    0
```

The **`dataOffset` is the tell** for the two container variants:

- **`.SVZ` fixed block** — `dataOffset = 16 + 4*count`. Between the header and the records is a
  **CRC32 table**: `count` entries of `u32 = zlib.crc32(record)` little-endian, one per record. This
  is byte-exact on every FANTOM device export **and** on a ZENOLOGY `RC001` export. The device
  validates it on import, so a written `.svz` must compute it (`java.util.zip.CRC32`).
- **`.SVD` fixed block** — `dataOffset = 16`, **no** CRC table; records follow immediately.
  Invariant `size == 16 + count*unitSize`.

**File header (16 bytes):**

```
.SVZ:  "SVZa" + version[2] + modelTag[5] + flag[1] + reserved[4]
       FANTOM device write: 53 56 5A 61  05 04  4B 59 30 31 39 ("KY019")  24  00 00 00 00
       ZENOLOGY plug-in write: version 03 03, modelTag "RC001", flag 01
.SVD:  u16 headerEnd (LE, offset of last TOC byte) + "SVD5\0" + padding
```

---

## 3. Importable keyboard-instrument `.SVZ`

This is the format ConvertWithMoss writes and the FANTOM loads through **UTILITY → IMPORT → IMPORT
TONE**. It is a self-contained instrument: one or more **tones** (each an oscillator pointing at a
**multisample**) over a shared pool of **samples**. Five blocks, in order:

```
DIFa  1 × 32   constant integrity record (copy a device template verbatim)
PATa  N × 1632 one ZEN-Core tone per instrument
USPa  M × 64   sample parameters, shared pool (M = total samples)
MSPa  N × 1040 one 128-key multisample map per instrument
USDa  M ×  var per-sample SMPd chunks, shared pool
```

A **single instrument** is `N = 1`; a **multi-tone bank** is `N > 1` instruments sharing one
`USPa`/`USDa` sample pool, each with its own `PATa` tone and `MSPa` map. Tone *i* references
multisample number *i* (1-based). Confirmed on-device: a 5-instrument bank imports as 5 selectable
tones.

### 3.1 `PATa` — ZEN-Core tone (1632 bytes)

The tone whose Partial-1 oscillator plays the user multisample. Field offsets below are validated
against the 2048 factory tones in `FANTOM.SVD` (§5) plus on-device edit-diffs; all values are u16 LE.

```
0x000  16  tone name (ASCII, space/zero padded)
0x0DF   1  Wave Group: 0 = ROM/preset wave, 2 = user "Kbd" single sample, 3 = user multisample
0x0E2   2  Wave Number L  = the 1-based multisample number
0x0E4   2  Wave Number R  = same as L for stereo; 0 = mono (plays L only)  ← the stereo switch
0x0EC   2  TVF filter type × 0x100:  0=OFF, 0x100=LPF, 0x200=BPF, 0x300=HPF  (+0x400/0x600 variants)
0x0F0   2  TVF cutoff     0–1023
0x0F6   2  TVF resonance  0–1023
0x37A   2  TVA env Time 1 (attack)   0–1023   default 0
0x37C   2  TVA env Time 2 (hold)     0–1023   default 400
0x37E   2  TVA env Time 3 (decay)    0–1023   default 400
0x380   2  TVA env Time 4 (release)  0–1023   default 150
0x382   2  TVA env Level 1 (peak)    0–1023   default 1023
0x384   2  TVA env Level 2           0–1023   default 1023
0x386   2  TVA env Level 3 (sustain) 0–1023   default 1023
0x388   2  TVA env Level 4 (end)     0–1023   default 0
```

- **Stereo.** A user multisample is stored interleaved-stereo in one sample record; the tone plays
  it in stereo only when **Wave R = Wave L**. The device's own "To Multisample" sampler import
  leaves Wave R = 0, so its exported tones look correct but are mono — do not copy that default.
- A second identical 4-time/4-level envelope block sits at `0x38A` (the TVF or pitch envelope); it is
  left at the template default.
- **Modulation.** Pitch/filter envelopes, LFO1/2 and the mod-matrix exist in the record but are not
  mapped — CWM's model has no LFO/matrix abstraction, so only the TVF filter and the TVA envelope
  round-trip.
- The writer starts from a real **device-authored** multisample tone (Wave Group 3, standard OSC
  bytes) as the 1632-byte template and patches only name, Wave L/R and the filter/envelope fields;
  everything else (structure, defaults, mod routings) stays device-valid for maximum acceptance.

### 3.2 `USPa` — sample parameters (64 bytes each)

One record per pooled sample. Copy the format bytes from a device sample; patch the per-sample
values:

```
0x00  16  sample name (ASCII)
0x14   1  loop mode: 0 = forward loop, 1 = one-shot
0x15   1  level 0–127
0x19   1  original (root) key 0–127
0x1C   4  start point   (frames, LE u32) — 0
0x20   4  loop start    (frames, LE u32)
0x24   4  end / loop end(frames, LE u32)
0x2C   1  channels (2 for the interleaved-stereo user samples)
0x2E-0x3F constant format descriptor: 32 32 00 01 e0 2e 00 00 10 00 01 10 00 00 00 00
          (copied verbatim from a device sample; determines the stereo sample format)
```

### 3.3 `MSPa` — multisample key map (1040 bytes)

A flat 128-slot per-key table (not ranged/velocity zones):

```
0x00  16   multisample name
0x10 1024  128 entries × 8 bytes, one per MIDI key 0..127:
             +0x00  u16 LE  sample number (1-based into the USPa/USDa pool; 0 = unassigned)
             +0x02  u8      per-key level (0x7F)
             +0x04  u8      0x80 constant flag
             +0x05  3       reserved
```

A CWM zone's key range is expanded into per-key entries; on read, keys sharing a sample number
collapse back into a zone.

### 3.4 `USDa` — sample data (variable block) + `SMPd` chunk

`USDa` has `unitSize = 0` and `dataOffset = 16 + 16*count`. Between the header and the concatenated
sample chunks is a 16-byte directory entry per sample:

```
+0x00  u32  index (0-based)
+0x04  u32  offset of the chunk, relative to the USDa block start
+0x08  u32  chunk size
+0x0C  u32  crc32 of the chunk  (the device tolerates 0 here; the CWM writer fills it in)
```

Each chunk is one **`SMPd`** sample. Two encodings exist and both import:

**Device-native `SMPd` (what the FANTOM exports, and what CWM writes):**

```
0x000   4  "SMPd"
0x004   4  u32 f04 = playedSampleCount = end × channels   (NOT frames × channels; frames may run past 'end')
0x008   1  channels (2)
0x009   1  bits (16)
0x00C   4  u32 sample rate  (48000 — the FANTOM's native rate; see §8)
0x010  16  sample name
0x060 364  per-sample preview: 182 × i16 signed peak-extreme envelope of the left channel
0x1CC  ..  interleaved 16-bit LITTLE-endian PCM   ← note: opposite endianness to RFWV
```

**Roland SF2→SVZ converter `SMPd`** (reference only; not written by CWM): a 0x20 header followed by a
complete embedded `RIFF`/`WAVE` file at 0x20 (44100 Hz), and the `USDa` directory CRCs are 0. The
`USPa` record is byte-identical to the device's. CWM decodes such packs and re-emits device-native
raw-PCM `SMPd`.

---

## 4. Sample preparation for click-free playback

The FANTOM voice engine is a fixed-point **phase-accumulator interpolator** (`Phase Inc 0x%04x_0000`,
`Start Address 0x%08X`, verified in the sound-engine firmware). Pitch-shifted playback therefore
interpolates *across* every playback boundary, and the engine exposes **no tone-side de-click or
loop-crossfade** parameter (confirmed against the Parameter Guide and the MC-707/FANTOM-0 firmware —
`De-Click` is the MC-707 *audio-looper's* control, `PreLoop LPF` is a reverb parameter, and the only
tone crossfade is the partial velocity-crossfade curve). The device's own sampler leaves the same
residue, so clean playback is entirely a **sample-preparation** job. CWM does what careful hardware
sampling does:

1. **48 kHz / 16-bit.** Resample every sample to the FANTOM's native rate (removes an entire
   rate-conversion click layer that non-native rates add).
2. **Zero-crossing loop snap.** Move `loop_start` and `loop_end` to the nearest rising zero crossing
   (the sample closest to 0 across channels), so the wrap `end → loop_start` is ≈0→0 and a one-shot
   stopping at `end` stops on ≈0.
3. **Tail taper.** Fade the post-loop tail to zero so release/one-shot never stops on a non-zero
   value.

This brings converted samples to the same click floor as the device's own sampler; residual pitch-
shift interpolation noise is a hardware DSP trait, not a format defect.

---

## 5. `SVD5` backup container (`.SVD`)

Whole-instrument backup (Utility → Backup) and the factory sound set (`FANTOM.SVD`). Metadata only —
no embedded PCM (user-sample audio lives in a sibling `KBD_SMPL/*.SMP`). Same framing as §2 with
`dataOffset = 16` and no CRC table.

Sections (record sizes identical across FANTOM-0/6/7/8/EX; counts differ):

| Tag  | Meaning | recSize | FANTOM-0/6/7/8 | EX |
|------|---------|--------:|---------------:|---:|
| `PRFa` | Scenes ("performances") | 3572 | 512 | 512 |
| `PATa` | Tones (ZEN-Core, §3.1) | 1632 | 2048 | 2048 |
| `RHYa` | Drum kits | 3328 | 128 | 128 |
| `INSa` | Per-scene instrument/zone data | 19008 | 128 | 128 |
| `DCWa`/`VTWa`/`SNAa`/`ZAPa`/`ZEPa` | wave/partial tables | — | 128–256 | same |
| `MDLa` | model/wave data | 2048 | 1024 | 1024 |
| `ACBa` | ACB model-expansion tones — **EX only** | 9984 | — | 1024 |
| `SMPa` | User-sample metadata | 84 | **2048** | **8000** |
| `MLSa` | User multisamples (= `MSPa`, §3.3) | 1040 | 128 | 128 |
| `SYSa` | System settings | 904 | 1 | 1 |
| `DIFa` | Integrity trailer | 32 | 1 | 1 |

The factory `FANTOM.SVD`'s 2048 `PATa` records validated the §3.1 tone layout (filter type `0xEC`
`×0x100`, cutoff `0xF0` 0–1023, the full TVA time/level block); the visible names are the blank-bank
`INITIAL TONE` (the playable factory presets are in ROM, not editable `PATa`).

---

## 6. `RFWV` sample (`.SMP`) — verified

```
0x00  4  "RFWV"
0x04  4  u32 payloadSize (BE) = fileLength - 8
0x08  4  u32 sampleRate  (BE)
0x0C  4  u32 channels    (BE) 1 or 2
0x10  4  u32 bitDepth    (BE) 16
0x14 12  reserved
0x20 ..  interleaved 16-bit BIG-endian PCM
```

`frameCount = (fileLength - 0x20) / (channels × 2)`. Byte-swap 16-bit samples on read/write (same as
MV-8000). Verified by decoding a factory pad sample to a correct 48 kHz/stereo WAV.

---

## 7. `RFPD` pad map (`PADCONF.BIN`) — verified framing

Maps a `PAD_SMPL/SMPL_SETxxx/` set (4 banks × 16 pads = 64) to its `BANKn-mm.SMP` files. Big-endian:
`"RFPD"` + `u32 padCount(BE)=64` + `u32 version(LE)=1` + `u32 bodySize(BE)` + 64 pad records (stride
76) + a trailing 24-bytes-per-pad block. Per-record fields include sample length (= the `.SMP` file
size), original key (`0x3C`=60), and loop markers. Enough is decoded to reconstruct a loadable set.

---

## 8. Import/export model (on-device)

- **UTILITY → IMPORT → IMPORT TONE** ← a tone/bank `.svz` (§3). **IMPORT SAMPLE** ← a sample-only
  `.svz`. **IMPORT SCENE** ← a scene `.svd`.
- **[SAMPLING] → IMPORT → To Keyboard / To Multisample** builds a user tone/multisample from
  WAV/AIFF/MP3 files (in an `EXPORT SAMPLE/` folder on the USB stick).
- **UTILITY → EXPORT → EXPORT TONE** writes a self-contained tone `.svz` (used as the RE ground
  truth). **EXPORT SAMPLE** writes samples; pad samples export to `EXPORT SAMPLE/` as bare
  `fmt`+`data` 48 kHz/16-bit WAVs.

Re-import reuses an existing same-named multisample/sample from device memory, so each written
instrument must use unique multisample **and** sample names to force a fresh load.

---

## 9. FANTOM-0 vs EX (firmware-confirmed)

| Aspect | FANTOM-0 (`mi078`) | FANTOM 6/7/8 EX (`ky019`) |
|--------|--------------------|---------------------------|
| CPU / firmware | ARM64, dual sound engine (`bmc0`/`bmc1`) | ARM32 |
| Container / tone layout | identical | identical (`PATa` byte-for-byte) |
| Platform tag | `KY019` | `KY019` |
| `SMPa` capacity | 2048 | 8000 |
| `ACBa` section | absent | present (1024 × 9984, ACB model tones) |

Confirmed by diffing the factory `FANTOM.SVD` section tables of `fantom060708_sys_v107` and
`fantom678ex_sys_v110`: only `SMPa` capacity and the EX-only `ACBa` differ. One detector/creator
covers all variants; a written `.svz` imports on every one.

---

## 10. Cross-device ZEN-Core

The MC-707/MC-101 (`RPG68`), GAIA-2, JUNO-X and JUPITER-X/Xm run the same ZEN-Core voice engine — the
sound-engine firmware (`C1A` partition) carries the identical `Phase Inc`/`DSP_LOOP`/`FadeOut` debug
strings as the FANTOM-0's. Their application code is Roland-LZS-compressed (`init.lzs`, a
non-Okumura scheme) but the SVZ/tone format is shared. **Every one of these has a sampler** (firmware
strings `User Sample Info`, `Multisample Name0..16`, `SAMPLING`), so user-sample SVZs are usable
across the line, not only on the FANTOM.

**Model tag by device** (mined from firmware; the SVZ header's 5-byte `modelTag` selects the target).
`KY019` is the shared ZEN-Core interchange tag and appears in the FANTOM, Juno-X and both Jupiters —
those accept the FANTOM `.svz` as written. **GAIA-2 is the outlier: only `MI085` is present**, so a
GAIA-2 `.svz` needs `modelTag = MI085`. Confirm each by exporting a sound from the device and reading
its header.

| Device | Firmware model codes | Likely SVZ tag |
|--------|----------------------|----------------|
| FANTOM-0 (`MI078`) | KY019, KY022, MI073, MI078 | `KY019` (device-confirmed) |
| FANTOM 6/7/8 EX | KY010, KY019, KY022, MI073 | `KY019` |
| Juno-X | KY019, KY023 | `KY019` |
| Jupiter-X (`MI077`) | KY019, KY023, MI077 | `KY019` |
| Jupiter-Xm (`KY023`) | KY019, KY023 | `KY019` |
| **GAIA-2 (`MI085`)** | **MI085** | **`MI085`** |
| ZENOLOGY plug-in | — | `RC001` (device-confirmed) |

The creator's `svz_header.bin` template carries `KY019`; a **selectable target-device model tag** (a
settings dropdown patching bytes 6–10 of the header) is the clean way to cover GAIA-2 and ZENOLOGY.

---

## 11. ConvertWithMoss implementation

All classes live in `format/roland/zencore` and are named `ZenCore*` (capital C, per Roland's
marketing). CWM handles `.SVZ` only.

- **Detector** (`ZenCoreDetector`): reads `.svz` — the shared `USPa`/`USDa` user-sample pool plus, when
  present, the `MSPa` multisample key map (§3.3), turning it into key-ranged zones. A file written by
  the creator round-trips back through this detector. The `USDa` directory is read exactly as §3.4
  documents it (16-byte header, then a 16-byte entry per sample, chunk offsets relative to the block
  start); the `MSPa` sample number is 1-based (0 = unassigned).
- **Creator** (`ZenCoreCreator`, extends `AbstractCreator`): `createPreset` → one-tone `.svz`;
  `createPresetLibrary` → multi-tone bank `.svz` (`supportsPresetLibraries`). Per sample it converts
  to 48 kHz/16-bit (`AudioFileUtils.convertToWav` + `recalculateSamplePositions`), duplicates mono to
  stereo, applies the §4 sample-prep, dedupes identical samples, and maps key ranges into the `MSPa`
  table. Per tone it writes Wave L=R (stereo) and carries the source's TVF filter (type/cutoff/
  resonance) and TVA amplitude envelope into the `PATa`.
- **Target device** (`ZenCoreCreatorUI`): a settings dropdown stamps the header's 5-byte `modelTag`
  (§2) — `KY019` (FANTOM/Juno-X/Jupiter-X, default), `MI085` (GAIA-2) or `RC001` (ZENOLOGY) — so the
  `.svz` imports on the chosen device (§10).
- **Assembler** (`ZenCoreSvz` + `ZenCoreContainer`): builds the CRC32 block framing, the
  DIFa/PATa/USPa/MSPa/USDa blocks and the `SMPd` chunks. The constant/opaque byte templates
  (`svz_header.bin`, `difa.bin`, `pata_multisample.bin`, `smpd_header.bin`, `uspa.bin`) are resources
  next to the class.
- **Envelope-time caveat:** Roland's exact envelope-time curve (seconds ↔ 0–1023) is not published,
  so the writer uses a calibrated `log2` approximation (near-instant → 0, ~20 s → full scale). Filter
  values and envelope *levels* are exact.

---

## 12. Not decoded / limitations

- ZEN-Core **LFO1/LFO2** and the **mod-matrix** — no CWM abstraction, not translated.
- **Pitch envelope** and **TVF (filter) envelope** offsets are located (the second env block at
  `0x38A`) but not currently written.
- `PRFa` scene internals, `RHYa`/`INSa` drum structures — read as inventory only.
- The `.SMP`/`PADCONF` remaining per-record fields beyond sample length / key / loop are sufficient
  to rebuild a set but not exhaustively labeled.
