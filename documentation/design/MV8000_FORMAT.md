# Roland MV-8000 patch format (.MV0) — reverse-engineered

Reverse-engineered 2026-07 from the 103 MV-8000 factory patches (OS-era files dated
2003-03-06, container version 0x75) and confirmed against the parameter descriptor
tables extracted from the MV-8000 OS 3.54 / MV-8800 OS 1.01 firmware (see "Firmware
notes" at the end). Field positions marked "(fw)" are taken from the firmware tables;
everything else was derived empirically with corpus-wide statistical validation.

## Container (IFF-like, all values big-endian)

```
offset  size  content
0       4     "MVFF" magic
4       4     u32 size of everything after this field (fileSize - 8)
8       4     u32 version (0x75 in all factory files)
12      4     form type: "PAT " = patch. Other forms exist: "MFXA" = MFX effect
              preset (e.g. MV-8800 bass-synth presets, no sample data) — not a patch.
16      ...   chunks: 4-char id + u32 size + payload, no padding
```

Chunks in a patch file, in order:

| id     | size      | content |
|--------|-----------|---------|
| `FMT ` | 12        | u32 0, u32 1 (patch count?), u32 sample count |
| `PRM ` | 15862     | patch parameters (fixed size, bit-packed, see below) |
| `SMPL` | variable  | u32 0, then per sample: `PRM ` (38 bytes) + `WAVE` (PCM) |

## Sample sub-chunks inside SMPL

Per-sample `PRM ` (38 bytes):

```
offset  size  content
0       4     u32 = 2 (constant; format/type tag)
4       4     u32 sample ID — referenced by SMT slots (unique, arbitrary base)
8       12    name, ASCII. Stereo pairs are stored as two mono samples whose names
              end in 0x7F 'L' / 0x7F 'R' (consecutive IDs, L first)
20      4     u32 start point (frames)
24      4     u32 loop start (frames)
28      4     u32 end point (frames; wave data may extend past it)
32      1     0x27 (constant tag, same value appears in partial records)
33      1     root key (MIDI note; 36 or 60 for unpitched material)
34      4     u32 BPM × 100 (12000 = 120.00 in all factory samples)
```

`WAVE` payload: headerless 16-bit big-endian signed mono PCM. The device sample rate
is fixed 44.1 kHz (no rate field exists).

## Patch PRM chunk (15862 bytes)

Bit offsets are MSB-first within the whole chunk unless marked otherwise.

```
bytes 0-7      u32 3 (constant), u32 flags (0; 0x0F seen in 5 factory kits)
bits 64-148    patch name, 12 × 7-bit ASCII
bits 148-155   category, 7-bit; values = Roland XV-5080 category enum
               (verified: PNO=1 EP=2 KEY=3 BEL=4 MLT=5 ORG=6 AGT=9 EGT=10 BS=12
                SBS=13 STR=14 BRS=19 SBR=20 SAX=21 HLD=22 SYN=27 BPD=28 SPD=29
                VOX=30 PRC=34 DRM=37)
bits 155-416   patch common parameters (mostly undecoded; patch-level defaults)
bytes 52-147   note→partial table, 96 entries: entry k = MIDI note (k + 21);
               value 0x7F = unassigned, else 0x80 + partial index.
               (K=21 anchored by the factory Grand Piano: table starts at the
               88-key bottom A0=21 and its top zone ends exactly at its top
               sample A6=93.)
bytes 148-15795  96 partial records × 163 bytes (byte-aligned)
bytes 15796-15861  66-byte tail, byte-identical in all factory files
```

### Partial record (163 bytes = 1304 bits, bit offsets relative to record start)

All positions below are confirmed by the firmware descriptor tables (fw).

```
bits 0-84      partial name, 12 × 7-bit ASCII ("Init Partial" = unused default)
bits 84-91     7-bit field, default 0; the firmware stamps 0x27 on used partials
bits 91-98     partial level (0-127, default 127)
bits 98-105    partial pan (1-127, default 64)
bits 105-110   mute group (5 bits, 1-17 = Off + group 1-16, default 1=Off)
bits 110-117   unknown (0-127, default 127)
bits 117-124   unknown (0-127, default 100)
bits 124-131   unknown (0-127, default 100)
bits 131-138   partial coarse tune (16-112, 64 = 0, ±48 semitones)
bits 138-145   partial fine tune (14-114, 64 = 0, ±50 cent)
bit  145       unknown switch, default 1
bits 146+210k  SMT slot k (k = 0..3), 210 bits each — see below
bits 986-993   unknown (14-114, 64 center; mostly 64 in factory content)
bits 993-1298  TVF / TVA / LFO block — see below
bits 1298-1304 padding
```

### SMT slot (210 bits, offsets relative to slot start = record bit 146 + 210k)

All positions (fw). Slot 1 has three small fields at [0..7) (w1/w5/w1, unknown); on
slots 2-4 the firmware lists [0..7) as a 14..114 center-64 field (unknown, 64 in all
factory files).

| bits      | field | notes |
|-----------|-------|-------|
| [7..23)   | sample ID: 16-bit region, value 0..9999 right-aligned (read [9..23)) | 0 = slot unused |
| 23, 24    | switches; 23 is set on used slots | exact meaning unknown |
| [25..31)  | **pitch key-follow**, 6 bits: -200%..+200% in 12.5% steps; 32 = 0% (fixed pitch), 40 = +100% (chromatic) | factory content uses only 32 (684 slots) and 40 (1234 slots) |
| [31..38)  | SMT level 0-127 (default 127) | |
| [38..45)  | SMT pan: 32 = left, 64 = center, 96 = right | stereo pair halves are hard-panned 32/96 |
| [45..52)  | SMT coarse tune (16-112, 64 = 0, ±48 semitones) | |
| [52..59)  | SMT fine tune (14-114, 64 = 0, ±50 cent) | |
| [59..66)  | velocity range lower (1-126, default 1) | complementary splits verified |
| [66..73)  | velocity fade lower (0-125, default 0) | |
| [73..80)  | velocity range upper (2-127, default 127) | |
| [80..87)  | velocity fade upper (0-125, default 0) | |
| [87..119) | 32-bit value + [119..127) 8-bit value, defaults 0 | unknown (sample offsets?) |
| [127..159)| 32-bit value + [159..167) 8-bit value, defaults 0 | unknown |
| [167..199)| 32-bit value + [199..207) 8-bit value, defaults 0 | unknown; varies in beat kits |
| [207..210)| **play mode**, 3 bits (0-4, default 1): 0 = loop [loopStart..endPoint], 1 = one-shot (ignores loop, plays to sample end); 2/4 = rare alternative loop modes (alternating/reverse?), 3 = rare one-shot variant. Uneven values do not loop | factory census: mode 1 ×1006, mode 0 ×899, mode 2 ×9, mode 3 ×4 |

### TVF / TVA / LFO block (record bits 993-1298, all (fw))

Envelopes are 4-segment Roland style: rise to L1 in T1, to L2 in T2, to L3 (sustain)
in T3; T4 = release. Defaults from the firmware match the factory "Init Partial"
exactly. Envelope times are 0-127 (hardware time curve unknown; the S-7xx formula
`20 * 2^((v-127)/21)` seconds is a reasonable approximation, with 0 = instant).

| bits        | field | default/range |
|-------------|-------|---------------|
| [993..997)  | TVF filter type (4-bit): 0 Off, 1 LPF, 2 BPF, 3 HPF | 0 |
| [997..1004) | TVF cutoff | 127, 0-127 (firmware label: "TVF Filter Cutoff Frequency [0-127]") |
| [1004..1011)| TVF resonance | 0, 0-127 |
| [1011..1013)| TVF velocity curve (2-bit) | 1, 0-3 |
| [1013..1041)| four ±63 params (64 = 0): velocity/keyfollow sensitivities; **[1034..1041) = TVF envelope depth** (verified: filter-sweep basses use 127 = +63) | 64 |
| [1041..1069)| TVF envelope levels L1-L4 | 127, 127, 127, 0 |
| [1069..1101)| TVF envelope times T1-T4 (8-bit cells) | 0, 10, 10, 0 |
| [1101..1115)| two ±63 params (keyfollow-related) | 64 |
| [1115..1122)| TVF keyfollow point (note 21-116) | 60 |
| [1122..1136)| two ±63 params | 64 |
| [1136..1138)| TVA velocity curve (2-bit) | 1, 0-3 |
| [1138..1159)| three ±63 params (level velocity sens etc.) | 64 |
| [1159..1180)| TVA envelope levels L1-L3 (L3 = sustain) | 127, 127, 127 |
| [1180..1212)| TVA envelope times T1-T4 (8-bit cells) | 0, 10, 10, 10 |
| [1212..1219)| TVA keyfollow point (note 21-116) | 60 |
| [1219..1233)| two ±63 params | 64 |
| [1233..1236)| LFO waveform (3-bit, 0-7) | 0 |
| [1236..1244)| LFO rate (8-bit cell, 0-149: 128+ = tempo-sync) | 102 |
| [1244..1251)| LFO offset/depth-related | 0, 0-127 |
| [1251..1252)| LFO key sync switch | 0 |
| [1252..1259)| LFO delay/fade time | 0, 0-127 |
| [1259..1294)| five LFO depths ±63 (pitch, TVF, TVA, pan, ...) | 64 |
| [1294..1298)| four 1-bit switches | 0 |

Notes:

- Stereo: an SMT pair (k, k+1) whose samples are a `...0x7F L` / `...0x7F R` name pair
  forms one stereo layer; the two halves are hard-panned 32/96.
- Key-follow and play mode are independent: the factory '@' basses (e.g. 'CompJBass @')
  are keytracked but unlooped (play through); pizzicato/vibes are keytracked one-shots;
  drum kits and menu patches are fixed-pitch one-shots; pianos and pads are keytracked
  and looped.
- Sound-design layers may place roots outside the zone (shimmer layers etc.);
  the root is authoritative for tracked slots.

## Firmware notes

The `.PRG` OS updater files are SH-4 flash images: a boot loader, then at file offset
0x20000 a "Roland ECS05 application program" header with an `LZ98L016` tag, the
uncompressed size (u32 BE at header+0x1c) and an LZSS stream (header+0x20): standard
Okumura LZSS — LSB-first flag bytes, 1 = literal, 0 = copy pair (12-bit ring offset =
low byte | high nibble << 4, length = low nibble + 3, 4 KB ring).

The decompressed image contains per-object **parameter descriptor tables** (patch,
partial, sample, MFX, ...) as 20-byte big-endian records:
`u16 byteOffset, u8 bitWidth, u8 bitOffset(0-7), u16 default16, u16 pad, u32 default,
u32 min, u32 max` — the (byteOffset*8 + bitOffset, bitWidth) pairs directly describe
the packed patch data. Special width codes: 0xF0 = 16-bit region (14-bit value,
sample IDs), 0xE0 = 32-bit region, 0xF8 = value descriptor of an 8-bit cell,
0xFC = filler/reserved marker. In MV-8000 OS 3.54 the partial table is at decompressed
offset 0x5372C4 (patch table at 0x536E00). The "Init Partial" record of the factory
patches matches the table defaults exactly.

## Not decoded / open

- The hardware curve for envelope times (0-127 → seconds) and the LFO rate table;
  they likely live in the sound-engine program (`MIAMI.PRG`), not in the main OS.
- The exact roles of the ±63 sensitivity params around the TVF/TVA envelopes and of
  the three 32+8-bit values per SMT slot.
- Patch common bits 155-416 beyond category/level/pan/mute-group/tuning (contains at
  least one per-patch varying param around bits 317-325).
- MV-8800: the partial descriptor table in the MV-8800 OS 1.01 firmware (decompressed
  offset 0x575148) is byte-identical to the MV-8000's (all 262 records) — the patch
  format is the same on both machines. MV-8800 "bass synth" .MVF files are form MFXA
  (effect presets), not patches.
