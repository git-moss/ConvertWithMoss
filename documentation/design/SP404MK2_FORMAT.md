# Roland SP-404MK2 project / PADCONF.BIN format — reverse-engineered

Reverse-engineered 2026-07 and **validated against a real exported project** (one of the
public projects from `sp404.amirmeludah.com`, system program v5.x) plus the
**SP-404MKII system program v5.52** firmware image (`SP404MKII_APP1.bin`, an uncompressed
ARM Thumb image loaded at `0x80000000` — i.MX RT1060 SDRAM). Cross-checked against three
independent community efforts:

* `gsterlin/sp404mk2-tools` — a bit-level `PADCONF.BIN` diff log (firmware 5.01).
* `ciscoittech/sp404mk2-sample-agent` — a Python read/write reference implementation.
* `macdigi/compa` — protocol notes documenting the `RFPD` / `RFWV` magics.

Fields below are confirmed against the real file unless marked **(?)** = still unknown.

## What kind of instrument this is

The SP-404MK2 is a **pad sampler**, not a keyboard multi-sampler — there is no
keygroup / key-range / per-zone-root-key model in the firmware. A **project** owns
10 **banks** (A–J), each bank has 16 **pads** (160 pads/project), 16 projects on the
device. Each pad holds **one** sample plus per-pad playback parameters. CHROMATIC mode
pitches a *single* pad's sample across a keyboard.

**ConvertWithMoss mapping.** Same as CWM's other pad/drum devices (Tonverk Drum Kit,
MC-707 kits): **one populated bank → one multi-sample source**, **each pad → one
single-key zone**. A general keyboard multi-sample written *to* the MK2 is flattened to
one sample per pad.

## On-card / exported project layout (confirmed)

An exported project (`EXPORT PROJECT`, or the on-device `B:/ROLAND/SP-404MKII/PROJECT_%02d`)
is a self-contained folder:

```
PROJECT_XX/
    PADCONF.BIN                 pad configuration (RFPD, 31,488 bytes)   <-- parsed
    SMPL/
        BANK<b>-<p>.SMP         one sample per used pad (RFWV audio)     <-- parsed
    PTN/  PTN%05d.BIN, PATTERNCHAIN_%02d.CHN     sequencer data (ignored)
    PICTURE/  *.bmp             project artwork (ignored)
```

`SMPL/BANK<b>-<p>.SMP` naming: for global pad index `i` (0-based, 0..159),
`b = i/16 + 1` (1..10, bank A..J) and `p = i%16 + 1` (1..16). E.g. pad index 16
(bank B, pad 1) → `BANK2-01.SMP`. Only used pads have a file.

## `SMPL/*.SMP` — RFWV audio (confirmed)

Roland's own wave container. All integers **big-endian**; **PCM samples are also
big-endian** (verified: byte-swapped interpretation has ~27× the sample-to-sample
delta energy).

```
offset  size  content
0       4     "RFWV" magic
4       4     u32  file size - 8   (RIFF-style)
8       4     u32  sample rate     (48000)
12      4     u32  channel count   (1 = mono, 2 = stereo)
16      4     u32  bit depth       (16)
20      12    reserved (zero)
32      ...   PCM, interleaved, signed 16-bit BIG-endian
```

PCM frame count = `(fileSize - 32) / (channels * 2)`.

## `PADCONF.BIN` — pad configuration (confirmed)

Two forms exist and **both are read**; the **export** form is what CWM writes (it is the
importable one):

| form     | size   | version (@0x08) | header | pad-metadata | pad-names | trailer |
|----------|--------|-----------------|--------|--------------|-----------|---------|
| export   | 31,488 | 2               | 0x80   | @0x80        | @0x6C00   | —       |
| internal | 52,000 | 3               | 0xA0   | @0xA0        | @0x6C20   | 20,480-byte per-pad "marks" section |

The two differ only in the header size (0x80 vs 0xA0, which shifts the metadata and name
blocks) and the trailing marks section; the 160×172 metadata records and 160×24 names are
byte-for-byte identical between the forms (verified). The name-block offset is therefore
`headerSize + 160×172` and the metadata starts at `headerSize`. The description below uses
the export offsets.

### Header (0x00–0x7F)

```
0x00  4    "RFPD" magic
0x04  4    u32 pad count = 0x000000A0 = 160
0x08  4    u32 version   (0x02000000)
0x0C  4    u32 pad-data region size = 0x7A80 = 31360  (= 160*172 + 160*24)
0x10  4    u32 (?) 0x000124B8 in sample file
0x40  40   10 × u32 per-bank BPM, stored as BPM*200 (0x4650=18000 → 90.00 BPM)
0x68  ..   per-bank volumes (?) / global params
```
Project display name is taken from the folder name (the export header carries no name
at a confirmed offset).

### Pad metadata — 160 records of 172 bytes, starting at **0x80**

Record for pad index `i` at `0x80 + i*172`. All fields u32 big-endian unless noted.
Confirmed against real trimmed + looped + full pads:

```
+0x00  sample end   (byte offset into the .SMP incl. its 32-byte header; == file size
                     for an untrimmed pad)
+0x04  sample start (byte offset; 512 used as the "from the beginning" sentinel)
+0x08  sample end   (duplicate of +0x00 — orig/user end pair)
+0x0C  volume       (0..127)
+0x10  loop enable  (0 = off, 1 = on)
+0x18  (?) 1 when the pad is used
+0x24  pad BPM * 100 (9000 = 90.00)
+0x2C  loop start   (byte offset; == +0x04 for a full pad)
+0x30  pitch, semitones (signed)
+0x34  fine tune (signed)
+0x48  pan          (0x40 = center, <0x40 left, >0x40 right)
       remaining bytes: envelope / filter / routing — not yet pinned (?)
```

### Filenames — 160 records of 24 bytes, starting at **0x6C00**

Record `i` at `0x6C00 + i*24`: up to 23 chars, then a NUL, **space-padded** to 24
(an all-space/empty field means the pad is unused). This is the pad's display name
(e.g. `01-New Sample`); the *audio* is resolved by the `BANK<b>-<p>.SMP` convention
above, not by this string.

### Start/end trim + loop points — partially decoded

Analyzed against the real project's audio (all 47 pads):

* `+0x04` is the **play start**, `+0x2C` the **loop start** (only meaningful when
  `+0x10` loop = 1); `+0x00` = `+0x08` = the sample **data length** (= the `.SMP` file
  size, so `(end - 32) / bytesPerFrame` = total frames). All are **byte offsets**,
  `bytesPerFrame = channels * 2` (4 for stereo).
* An **untrimmed** pad stores **512** for start (and loop start). Trimmed pads store a
  larger offset and play a sub-window; e.g. `BANK2-03` stores start = 23032 and plays
  from ≈ frame 5630 to the end, which lines up with where its waveform's leading
  transient stops (the audio before that frame is trimmed off).
* First-order formula: `startFrame ≈ (start - 512) / bytesPerFrame` (clamped ≥ 0),
  `loopStartFrame ≈ (loopStart - 512) / bytesPerFrame`, `endFrame = total frames`
  (no end-trimmed pad appears in the one available project).

**Unresolved — the exact base (a 2.5 ms / 120-frame swing).** `(v - 512)` maps every
untrimmed pad to frame 0 (correct for a drum hit whose attack is on the first sample,
e.g. `BANK2-01`), but a few pads argue for `(v - 32)` instead — `BANK4-05`'s quiet
intro ends *exactly* at `(2048 - 32) / 4 = frame 504`, and some long/looped pads use
2048 / 6048 as their "start". The inconsistency implies a subtler rule (a minimum-start
floor, or an import auto-trim of leading silence) that one project cannot pin down, and
the end-trim base is untested (no end-trimmed pad in the corpus).

**Therefore v1 still extracts the full sample** (start = 0, end = total frames) — safe
and lossless. Applying the start-trim is a one-line change once the base is confirmed,
by either: an on-device **edit-diff** (set a known start/end/loop, export, diff the
`PADCONF.BIN` — the method used for the other Roland formats), or a firmware-loader
disassembly (image base `0x80000000`). Issue #215's author owns the hardware and is
reverse-engineering the same format — the fastest route to certainty.

## Firmware-confirmed folder tokens

`B:/ROLAND/SP-404MKII/PROJECT_%02d`, `<proj>/SMPL/`, `<proj>/PADCONF.BIN`,
`A:/EXPORT/PROJECT/PROJECT_%02d`, `A:/IMPORT/…`, plus `CHROMATIC` / `16 VELOCITY` /
`MULTIPAD` mode strings — none of which imply a keyboard multi-sample model.
