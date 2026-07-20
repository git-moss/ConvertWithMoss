# Roland ZEN-Core file formats

Reverse-engineered for ConvertWithMoss. Covers the Roland **FANTOM-0** (FANTOM-06/07/08, firmware
`mi078`, ARM64) and the **FANTOM 6/7/8** / **6/7/8 EX** (firmware `ky019`, ARM32). All of these run
the same **ZEN-Core** sound engine and share the same on-disk formats and internal platform tag
`KY19`/`KY019`; differences are capacity/feature only and are called out per section.

The ZEN-Core lineage is wider than the FANTOM: the same container and voice engine are used by the
MC-707/MC-101, JUNO-X and JUPITER-X/Xm (all `RPG68`/`MI078`-class parts, verified from their firmware
— see §10). A `.svz` written for the FANTOM therefore imports across that whole sample-capable range.
The GAIA-2 shares the engine too but has no sampler, so it cannot use a user-sample `.svz` (§10).

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

> **Name padding matters.** The `PATa` tone name is **space**-padded, but every *sample* name field
> (`USPa` 0x00, `SMPd` 0x10, `MSPa` 0x00) is **zero**-padded in device-written files — and the
> distinction is functional, not cosmetic: a sample whose name field is space-padded imports without
> an error and shows up correctly mapped in the multisample editor, but the device never binds its
> wave data (empty waveform display, silent on every key).

The tone has **four partials**, each with its own oscillator (wave number, pan), keyboard/velocity
window, TVF filter and three envelopes. The per-partial fields live in several **parallel tables**,
each with its own base offset and stride (all offsets are Partial 1; add the stride per partial):

| Per-partial table | Base | Stride | Partials 1–4 |
|-------------------|------|--------|--------------|
| Keyboard / range  | 0x0A0 | 0x0C | 0x0A0 / 0x0AC / 0x0B8 / 0x0C4 |
| Oscillator / filter | 0x0CE | 0x7C | 0x0CE / 0x14A / 0x1C6 / 0x242 |
| Pitch envelope    | 0x2B8 | 0x18 | 0x2B8 / 0x2D0 / 0x2E8 / 0x300 |
| Filter (TVF) envelope | 0x318 | 0x18 | 0x318 / 0x330 / 0x348 / 0x360 |
| TVA envelope      | 0x37A | 0x10 | 0x37A / 0x38A / 0x39A / 0x3AA |

```
0x000  16  tone name (ASCII, space padded)

Keyboard/range table (stride 0x0C):
0x0A0   1  Velocity Range Lower (1–127)
0x0A1   1  Velocity Range Upper (1–127)
0x0A4   1  Partial-switch display gate for the NEXT partial (see "Partial switching" below)
0x0A8   1  Key Range Lower (0 = C-1) ─┐ probable, not written by CWM; Partial 4's entry
0x0A9   1  Key Range Upper (127 = G9)─┘ overlaps Partial 1's pan at 0x0CE, so it is truncated

Oscillator/filter table (stride 0x7C):
0x0CE   1  Partial pan (signed, −64 = hard left … 0 = center … +63 = hard right)
0x0DF   1  Wave Group: 0 = ROM/off, 2 = user "Kbd" single sample, 3 = user multisample (active)
0x0E2   2  Wave Number L = 1-based multisample number
0x0E4   2  Wave Number R = L (mono tone, both sides); 0 = play L only (a hard-panned mono voice)
0x0EC   2  TVF filter type × 0x100:  0=OFF, 0x100=LPF, 0x200=BPF, 0x300=HPF
0x0F0   2  TVF cutoff 0–1023   (≈ 150 units per octave = 8 cents/unit, measured on a FANTOM-0)
0x0F6   2  TVF resonance 0–1023

Pitch-envelope table (stride 0x18):
0x2B8   1  Pitch env depth (signed byte; ≈ 0.42 semitone per unit, capped ±63 on a PCM partial)
0x2BC   8  Times T1–T4 (u16 each)
0x2C4  10  Levels L0–L4 (signed s16 — the bipolar shape scaled by depth)

Filter-envelope table (stride 0x18):
0x318   1  Filter (TVF) env depth (signed byte; ≈ 20 cutoff units per unit = 160 cents/unit)
0x31E   8  Times T1–T4 (u16 each)
0x326  10  Levels L0–L4 (signed s16)

TVA-envelope table (stride 0x10):
0x37A   8  Times:  attack, hold, decay, release  (default 0, 400, 400, 150)
0x382   8  Levels: peak, hold, sustain, end      (default 1023, 1023, 1023, 0)
```

- **Stereo / mono.** User samples are stored **mono** (SMPd channel count 1, USPa channel count
  still 2). The voice engine **mis-plays an interleaved-stereo sample**: the two channels
  alternate into the output as a buzz, degenerating to a per-loop-pass tick when they are
  identical, and it does this at **any** loop length (measured on a FANTOM-0). **True stereo is
  built the factory way** — verified in the FANTOM firmware, where every stereo sound is stored
  as separate "… L"/"… R" mono waves at different ROM addresses. A stereo source is therefore
  split into two mono samples (left, right) in two multisamples, played by a **two-partial tone**:
  Partial 1 plays the left multisample panned hard left (pan −64), Partial 2 the right multisample
  panned hard right (pan +63). Each channel is then a mono loop, so neither has the interleaved
  wrap click — hardware-verified click-free, wide, with matched left/right amplitude. A mono
  instrument uses one partial and plays its single multisample on both sides (Wave R = Wave L;
  R = 0 would drop the right channel — the device's own "To Multisample" import leaves R = 0, so
  its exported tones look correct but play mono; do not copy that default).
- **The last sample chunk's wave data does not reliably bind.** The last sample in the
  `USPa`/`USDa` pool can import without an error yet never load its wave data — the multisample
  maps and displays correctly, but the sample shows an empty waveform and plays silent on every
  key (the tone does not even register on the *note active* meter). Hardware-verified on a
  FANTOM-0 twice: a pool holding exactly **one** sample (whose only sample *is* the last) never
  loads it, and a probe bank's **six**-sample pool imported with its last sample dead on every
  attempt, while byte-identical content with an inert trailing sample appended played fine. (A
  four-sample pool once imported completely, so the exact trigger beyond "last slot" is not
  pinned down.) The writer therefore always appends an inert, unmapped 128-byte silence "Spacer"
  sample, so no real sample ever sits in the last slot.
- **Partial switching (two independent things: sound and display).** A partial *sounds* when its
  **Wave Group (0x0DF+) = 3** with a non-zero **Wave Number**. To make an unused partial silent,
  set **Wave Number L/R = 0** (a group-0 partial that keeps a non-zero wave number rings a ROM wave
  — hardware-verified). Separately, the *partial page's ON/OFF display* is driven by the keyboard
  byte at **0x0A4 + p·0x0C**, which — counter-intuitively — **gates the NEXT partial (p+1)**, not
  its own: **1** = the next partial shows ON, **0** = it shows OFF; Partial 4 (no next partial) reads
  **127**. So for `count` active partials the row of switch bytes is
  `switch[p] = 127 if p==3 else (1 if p+1<count else (0 if p<count else 127))` — e.g. one active
  partial → `[0,127,127,127]`, two → `[1,0,127,127]`, three → `[1,1,0,127]`, four → `[1,1,1,127]`.
  Hardware-verified against device exports at one, two and four active partials (the writer's
  two-active output is byte-identical to a device tone with Partial 3 switched off). This is why the
  mono template (its Partial 1 byte is 0) shows its unused partials OFF while the stereo template
  needs the byte corrected. The writer sets Wave 0 and this gate for every unused partial.
- **Velocity layers.** The flat `MSPa` map has no velocity axis, so velocity layering uses the
  partials instead: each distinct source velocity range is laid onto its own partial — **one partial
  per mono layer** (centre pan), **two partials per stereo layer** (hard L/R). This is done
  automatically (as the other formats map velocity layers). The engine's four partials cap it at
  **four mono or two stereo layers**; any excess layers merge into the top one (with a warning). Each
  active partial carries its own multisample, its velocity window (0x0A0/0x0A1), pan, and the shared
  filter/envelope shaping; Partials 3–4 are cloned from a verified active partial before being
  switched on so their opaque oscillator bytes stay device-valid.
- **Pitch and filter envelopes (hardware-calibrated).** The pitch (0x2B8+) and TVF-filter (0x318+)
  envelopes are **dedicated per-partial blocks** — a signed depth, four u16 times and five signed
  levels — not mod-matrix routings (confirmed in the FANTOM Parameter guide, which lists PITCH ENV /
  TVF ENV as their own sections and PIT-ENV/FLT-ENV/AMP-ENV as separate matrix *sources*). The writer
  emits the standard attack-to-peak / decay-to-sustain / release-to-centre shape scaled by the depth.
  Depths are calibrated on a FANTOM-0: the pitch envelope shifts **≈ 0.42 semitone per depth unit**
  (source semitones × ~2.4, clamped to the PCM partial's ±63; the audible shift itself saturates near
  +22 st), and the filter envelope moves the cutoff **≈ 20 units = 160 cents per depth unit** (source
  cents ÷ 160). Written only when the source actually modulates; a zero depth leaves the default.
- **A TVA decay time (Time 3) of 0 renders the tone silent** on a FANTOM-0 — the voice never sounds,
  even with all levels at maximum (which is also why the factory default keeps its middle stage
  times at 400 despite flat levels). **A near-instant TVA attack produces a transient burst at
  every note-on, and it is pure engine behavior, independent of the sample content.** Calibrated
  on a FANTOM-0 with a bank of tones whose samples start at levels from 100 to 30000 of full
  scale, at exact attack values (patched into `PATa`): the recorded onsets are byte-alike across
  all start levels — the engine fades the sample data start itself — and only the attack value
  shapes the residual burst: **~91 at attack 1** (an audible tick in quiet material), **~25 at
  attack 8** (below the ~46 audibility line with margin), ~13 at 16, ~5 at 32; attack 0 is worst
  (onset slope kinks of 26–98× the local slope). Whether attack 1 is audible depends on the
  material: high-edge content — saws, plucks, drum hits, whose onset edge (largest frame step in
  the first ~25 ms over the sample peak) exceeds ~0.5 — hides the burst under its own onset,
  hardware-verified masked at every pitch from 55 to 330 Hz; smooth material (string pads, onset
  edge a few percent) exposes it. Zero hold and release times are fine. The writer floors
  accordingly: a near-instant attack becomes 1 on high-edge material (keeping the full transient)
  and 8 otherwise (whose ~11 ms ramp is faster than half a cycle of a bass fundamental);
  decay ≥ 8.
- **Modulation (not mapped).** LFO1/2 and the mod-matrix exist in the record but are not written —
  CWM's model has no LFO/matrix abstraction. The TVF filter, the TVA envelope and the pitch/filter
  envelopes round-trip; the rest stays at the template default.
- The writer starts from a real **device-authored** tone as the 1632-byte template — a one-partial
  multisample tone for mono, a two-partial hard-panned tone for stereo — and patches only the name,
  per-partial wave/pan/velocity/switch and the filter/envelope fields; everything else (structure,
  OSC defaults, mod routings) stays device-valid for maximum import acceptance.

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
0x2C   1  channels — always 2 in device-written files and conforming packs, even for mono-stored
          samples (the SMPd channel count carries the actual storage layout)
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
0x004   4  u32 f04 = 2 × end  (the declared play extent; see below — an invariant of every
           conforming file, independent of the stored frame count and of the channel count)
0x008   1  channels (2)
0x009   1  bits (16)
0x00C   4  u32 sample rate  (48000 — the FANTOM's native rate; see §8)
0x010  16  sample name
0x060 364  per-sample preview: 182 × i16 signed peak-extreme envelope of the left channel
0x1CC  ..  interleaved 16-bit LITTLE-endian PCM   ← note: opposite endianness to RFWV
```

**The `f04` law.** Every conforming file writes `f04 = 2 × end` exactly — every FANTOM export
(whose stored frames run up to 144 frames *short* of `end`) as well as all 425 looped samples
written by **Roland's own SF2→SVZ converter** (in the commercial third-party *SOURCE* pack by
Vulture Culture, which stores 15 natural frames *past* `end`, i.e. beyond `f04/2`; the engine
tolerates both). It is a play extent, not a storage size. The firmware
(`mi078`, FANTOM-0 v1.07) confirms the division is hard-coded: the user-sample RAM allocator
computes `frames = f04 >> 1` regardless of the channel count, then allocates
`roundUp512bytes((frames + 64) × 2)` **per channel** — i.e. samples are stored channel-planar
in 512-byte blocks with a fixed 64-frame allocation margin past the declared extent for the
voice engine's read-ahead.

**Roland SF2→SVZ converter `SMPd`** (reference only; not written by CWM): the output format of
Roland's own SF2→SVZ converter, as shipped in commercial third-party packs (e.g. Vulture
Culture's *SOURCE*): a `"SMPd"` tag with a u16 header size `0x20` at +4 and u8 version `1` at
+6 (the device firmware validates exactly these fields), followed by a complete embedded
`RIFF`/`WAVE` file at 0x20 (44100 Hz), and the `USDa` directory CRCs are 0. The `USPa` record
is byte-identical to the device's. CWM decodes such packs and re-emits device-native raw-PCM
`SMPd`.

**Compact `SMPd`** (file version `02 01`; seen in third-party sample-pool exports, e.g. a pool
tagged `Nemly`): follows the same `f04 = 2 × end` law at +4, the same bits at `0x009`, rate at
`0x00C` and name at `0x010` as the device-native encoding, but uses a shorter **158-byte**
(`0x9E`) header with a shorter preview, and stores the **channel count as a u16 at `0x00A`** — the
byte at `0x008` (channels in the device encoding) holds an unrelated `0x32`. Interleaved 16-bit
LITTLE-endian PCM follows at `0x9E`. CWM reads it; it is not written.

```
0x000   4  "SMPd"
0x004   4  u32 f04 = 2 × end
0x008   1  0x32  (NOT the channel count in this encoding)
0x009   1  bits (16)
0x00A   2  u16 channels (2)
0x00C   4  u32 sample rate  (44100 / 48000)
0x010  16  sample name
0x060 ..   shorter preview
0x09E  ..  interleaved 16-bit LITTLE-endian PCM
```

**Distinguishing the encodings on read.** The device-native (460) and compact (158) header sizes
differ by 302 (`2 mod 4`), so a stereo chunk's PCM byte count is a whole number of 4-byte frames
for exactly one of them. The reader (`ZenCoreSvz.resolveSmpdLayout`) picks the header whose
channel-count field — the byte at `0x008` for the 460 header, the u16 at `0x00A` for the 158
header — is 1 or 2 and whose PCM region past the header divides evenly into frames; the declared
`end` is deliberately not used to size the PCM, since the stored frame count runs a little past or
short of it.

---

## 4. Sample preparation for click-free playback

The FANTOM voice engine is a fixed-point **phase-accumulator interpolator** (`Phase Inc 0x%04x_0000`,
`Start Address 0x%08X`, verified in the sound-engine firmware). Pitch-shifted playback therefore
interpolates *across* every playback boundary, and the engine exposes **no tone-side de-click or
loop-crossfade** parameter (confirmed against the Parameter Guide and the MC-707/FANTOM-0 firmware —
`De-Click` is the MC-707 *audio-looper's* control, `PreLoop LPF` is a reverb parameter, and the only
tone crossfade is the partial velocity-crossfade curve). Clean playback is therefore entirely a
**sample-preparation** job, and well-mastered content shows the target: every looped sample in
the third-party *SOURCE* pack (SF2 sources converted with Roland's own SF2→SVZ converter) —
pads included — is value-matched at the **exclusive** seam
`L[end-1] == L[ls-1]` (median deviation 1 of 32768), the loop plays `[loopStart, end)`, and ~15
frames of natural post-loop audio are stored after `end`. (The factory ROM waves appear to go
further: their wave table — `wpf_*.bin` inside `QSPIImage.BIN`, 36-byte records with three
ascending ROM positions and a per-articulation fine-tune field; mapping preliminary — suggests
mostly short loops, with long evolving textures built by modulation rather than long sample
loops.) CWM prepares samples accordingly:

1. **48 kHz / 16-bit.** Resample every sample to the FANTOM's native rate (removes an entire
   rate-conversion click layer that non-native rates add).
2. **Period-aligned loop end.** Move the loop *end* (never the audio) to the nearby point whose
   wrap deviates least from the waveform's own step into the loop start — the exclusive seam
   invariant that hardware-verified click-free playback requires (and that well-mastered packs
   satisfy).
3. **Source loop cross-fades are baked.** A source-specified loop cross-fade (a Renoise or
   Tonverk patch, or the *Set fixed loop-crossfade* processing option) is blended into the audio
   with its full source length - source formats apply it live at playback, but the ZEN-Core
   engine has no loop cross-fade of its own, so without baking, a loop whose sound evolves across
   the loop region jumps audibly at every wrap even though the waveform seam is perfect. The
   baked wrap then *defines* the loop: steps 2 and 4 are skipped for it, since re-seating the
   loop end afterwards would move the wrap away from the junction the fade constructed. Three
   guards keep the fade faithful. A loop that already wraps cleanly - seam step within tolerance
   *and* RMS-level-matched across the junction - is left byte-identical: baking a fade into
   material that needs no help would only soften it. When a fade is needed and the source stores
   audio past the loop end, it is applied at the loop *start*, blending from the tail's natural
   continuation - the wrap then follows the source's own motion across the boundary, which also
   heals a level step sitting at the loop start itself (the tail-side fade cannot: it targets
   the lead-in). And that continuation is first verified to flow out of the tail without a step,
   because an assembled sample bank may store *unrelated* data past a loop end (hardware corpus:
   a bass whose tail ends at +9411 with post-end data resuming at -4539); when it does not, the
   classic tail-side fade into the loop-start lead-in is used instead.
4. **In-phase loop cross-fade.** When no single end point is seamless (an evolving pad whose
   timbre drifts across the loop), blend the loop tail into the loop-start lead-in. The
   period-alignment in step 2 keeps the blend in phase, so bright content does not cancel.
   Both step 2 and this cross-fade measure the waveform's own step *into* the loop start, so they need a
   lead-in: a loop starting at the very first frames — a whole-file loop, or a start the
   zero-crossing snap processing option moved there — first has its start advanced into the sample
   (the skipped frames still play once before the first wrap), and only when its wrap is not
   already seamless; a period-aligned whole-file loop, like the device's own exports, is left
   byte-identical. The cross-fade can also only be as long as the lead-in, and it closes the wrap
   mismatch by roughly 1/(fade length) — a loop starting only a few dozen frames in leaves an
   audible residual (hardware-heard: a loop from frame 70 capped the fade at 70 frames, and its
   ~4400 mismatch left a ~63 residual that ticked every pass), so a bad-wrapped loop whose short
   lead-in limits the fade likewise has its start advanced before fading.
5. **Guard frames.** Store the loop-start continuation after the loop end (within the play
   extent under the old `f04` convention; the engine's own allocator reserves a 64-frame margin
   past `f04/2` regardless, see §3.4).
6. **Mono storage; stereo as two mono partials.** Hardware-measured: a looped user sample stored
   as **interleaved stereo** (SMPd channel count 2) is mis-played at any loop length — the two
   channels alternate into the output as a buzz, degenerating to a once-per-wrap tick when they
   are identical, **even when the wrap is mathematically seamless**. Byte-identical audio stored
   **mono** plays clean. So every sample is stored mono, and a stereo source is split into a left
   and a right mono sample played by the tone's two hard-panned partials (§3.1) — the factory's
   own way (its stereo sounds are separate "… L"/"… R" mono waves), hardware-verified click-free,
   wide, and with matched left/right amplitude.

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

The MC-707/MC-101 (`RPG68`), JUNO-X and JUPITER-X/Xm run the same ZEN-Core voice engine as the
FANTOM-0 — the sound-engine firmware (`C1A` partition) carries the identical
`Phase Inc`/`DSP_LOOP`/`FadeOut` debug strings. Their application code is Roland-LZS-compressed
(`init.lzs`, a non-Okumura scheme) but the SVZ/tone format is shared. **Each of these has a sampler**
(firmware strings `User Sample Info`, `Multisample Name0..16`, `SAMPLING`), so user-sample SVZs are
usable across that line, not only on the FANTOM.

**The GAIA-2 and the ZENOLOGY plug-in run the same engine but cannot load user samples**, so a
multi-sample `.svz` is useless on them and the creator does not target them. The GAIA-2 has no sampler
at all: its reference manual's `IMPORT` loads only tones (`.SVZ`/`.SDZ`) and Model-Expansion tones, no
user-sample or sampling feature appears anywhere in the manual, and its oscillators are virtual-analog
plus a *fixed* wavetable — it cannot even load custom wavetables, let alone user PCM. ZENOLOGY imports
only the tone (see below). Both would play a multi-sample silent.

**Model tag by device** (mined from firmware; the SVZ header's 5-byte `modelTag` selects the target).
`KY019` is the shared ZEN-Core interchange tag and appears in the FANTOM, Juno-X, both Jupiters and
the MC grooveboxes — every sample-capable device accepts the FANTOM `.svz` as written, so the creator
always writes `KY019`. The GAIA-2's `MI085` and the ZENOLOGY plug-in's `RC001` are documented here as
reverse-engineering reference only, never written.

| Device | Firmware model codes | Likely SVZ tag |
|--------|----------------------|----------------|
| FANTOM-0 (`MI078`) | KY019, KY022, MI073, MI078 | `KY019` (device-confirmed) |
| FANTOM 6/7/8 EX | KY010, KY019, KY022, MI073 | `KY019` |
| Juno-X | KY019, KY023 | `KY019` |
| Jupiter-X (`MI077`) | KY019, KY023, MI077 | `KY019` |
| Jupiter-Xm (`KY023`) | KY019, KY023 | `KY019` |
| MC-707 / MC-101 (`RPG68`) | RPG68, KY019, KY022 — the voice partition's content registry lists "RolandKY019 ROM0" and KY019 `EXP` banks | `KY019` |
| GAIA-2 (`MI085`) | MI085 | not a creator target — no user-sample import (VA + fixed wavetable, no sampler) |
| ZENOLOGY plug-in | — | `RC001` (header device-confirmed; **tone import only** — not offered as a creator target, see below) |

The creator always writes the 16-byte `KY019` header — magic `SVZa`, version `05 04`, tag `KY019`,
flag `24`, four reserved bytes — stored as the `svz_header.bin` resource. There is no target-device
option: `KY019` is the one tag every sample-capable ZEN-Core device accepts. The GAIA-2 (`MI085`) and
ZENOLOGY (`RC001`) headers are recorded above only as reverse-engineering reference, since neither
device can use a user-sample `.svz` (their version/flag bytes differ too: ZENOLOGY uses `03 03`/`01`
where the FANTOM family uses `05 04`/`24`).

**ZENOLOGY plug-in limitation (verified 2026-07-12).** ZENOLOGY Pro imports only the *tone* from a
`.svz`: the `USPa`/`MSPa`/`USDa` user-sample sections are ignored — the imported tone shows its PCM
wave as *No Assign* with wave bank `---` and plays silent. This is not an encoding matter: the
FANTOM's **own** exported user-sample tone behaves identically in the plug-in, and the samples-only
`.svz` flavor (Roland's SF2→SVZ converter output) is rejected outright ("the file cannot be read").
The plug-in's user samples exist only in its local library (ZENOLOGY Pro's *Sample Import*), its
sound banks are a zlib-compressed `SVDx` image (the "for Plugin" export), and its "for Hardware/ZC1"
export writes `DIFa`+`PATa`+`MDLa` (tones + model records, no user samples). Multisample `.svz`
files are therefore hardware-only; the creator does not offer ZENOLOGY as a target (the `RC001`
header itself is correct and accepted, documented here as reference).

---

## 11. ConvertWithMoss implementation

All classes live in `format/roland/zencore` and are named `ZenCore*` (capital C, per Roland's
marketing). CWM handles `.SVZ` only.

- **Detector** (`ZenCoreDetector`): reads `.svz` — the shared `USPa`/`USDa` user-sample pool plus, when
  present, the `MSPa` multisample key map (§3.3), turning it into key-ranged zones. A file written by
  the creator round-trips back through this detector. The `USDa` directory is read exactly as §3.4
  documents it (16-byte header, then a 16-byte entry per sample, chunk offsets relative to the block
  start); the `MSPa` sample number is 1-based (0 = unassigned). The first tone's shaping is read
  back as well — the TVF filter (type, cutoff, resonance), the TVA amplitude envelope, and the
  pitch/TVF modulation envelopes — with the same hardware-calibrated time law and depth scales the
  writer uses, so ZEN-Core sources convert to other formats with their real envelopes instead of
  pipeline defaults (measured round-trip error below one percent; a zero modulation depth is the
  template default and reads as no modulation).
- **Creator** (`ZenCoreCreator`, extends `AbstractCreator`): `createPreset` → one-tone `.svz`;
  `createPresetLibrary` → multi-tone bank `.svz` (`supportsPresetLibraries`). Per sample it converts
  to 48 kHz/16-bit (`AudioFileUtils.convertToWav` + `recalculateSamplePositions`), applies the §4
  sample-prep and stores everything mono. A **mono instrument** has one multisample played by a
  one-partial tone. A **stereo instrument** (any zone stereo) splits each zone into a left and a
  right mono sample (sharing one loop end), builds two multisamples, and writes a **two-partial
  tone** — Partial 1 = left panned hard left, Partial 2 = right panned hard right (§3.1). A pool
  always ends with the inert spacer, since the last chunk's wave data does not reliably bind
  (§3.1); samples and multisamples are named
  with a content hash (the device re-uses same-named imports, so equal names must imply equal
  bytes). The source's TVF filter and TVA amplitude envelope are carried into every partial so the
  channels share the same shaping. Tone names are capped at the PATa field's 16 characters (what
  the device displays); names in one bank that would truncate identically get part of the shared
  head elided with a `~` and keep their distinctive tail (`082_RTW2_106_BASS_SAW` / `..._SQR` →
  `082_RTW2_106~SAW` / `082_RTW2_106~SQR`; identical source names fall back to `~2`, `~3`, …). A
  source without a single convertible sample is
  skipped with an error (it would import as a silent husk tone), one broken source does not lose
  the rest of a library, and a library name typed with the `.svz` ending does not double up.
- **Header**: the creator has no options; it always stamps the fixed 16-byte `KY019` header (§2),
  read from the `svz_header.bin` resource — the one tag every sample-capable ZEN-Core device accepts.
  The GAIA-2 (`MI085`) and the ZENOLOGY plug-in (`RC001`) are not targets, as neither can load user
  samples (§10).
- **Assembler** (`ZenCoreSvz` + `ZenCoreContainer`): builds the CRC32 block framing, the
  DIFa/PATa/USPa/MSPa/USDa blocks and the `SMPd` chunks. The constant/opaque byte templates
  (`svz_header.bin`, `difa.bin`, `pata_multisample.bin` mono tone, `pata_stereo.bin` two-partial
  hard-panned stereo tone, `smpd_header.bin`, `uspa.bin`) are resources next to the class.
- **Envelope-time law (hardware-calibrated):** Roland's exact time table is not published, so it
  was measured on a FANTOM-0 with a release-ladder bank (exact values patched into `PATa`, the
  recorded exponential fades fitted). The stage span (time to −40 dB) per value:
  `0→10 ms, 8→20 ms, 32→60 ms, 75→120 ms, 129→200 ms, 256→390 ms, 512→1.24 s, 800→6.19 s`
  (extrapolated to ~21 s at 1023; the separately measured attack-rise anchors agree within ~25%).
  The writer interpolates log-linearly between these anchors for all four TVA stages and the
  pitch/filter envelope times. The device's release at value 0 is a clean ~10–20 ms engine fade —
  an instant source release never clicks at note-off, so no release floor is needed. (The earlier
  `log2` approximation overstated times several-fold below ~value 800: a 0.5 s release was written
  as 129, which really plays ~0.2 s, and everything below ~0.3 s collapsed to 0.)

---

## 12. Not decoded / limitations

- ZEN-Core **LFO1/LFO2** and the **mod-matrix** — no CWM abstraction, not translated.
- `PRFa` scene internals, `RHYa`/`INSa` drum structures — read as inventory only.
- The `.SMP`/`PADCONF` remaining per-record fields beyond sample length / key / loop are sufficient
  to rebuild a set but not exhaustively labeled.
