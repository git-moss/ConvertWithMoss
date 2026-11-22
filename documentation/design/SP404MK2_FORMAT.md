# Roland SP-404MK2 project / PADCONF.BIN format — reverse-engineered

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
32      480   opaque block (looks encrypted, see below)
512     ...   PCM, interleaved, signed 16-bit BIG-endian
```

PCM frame count = `(fileSize - 512) / (channels * 2)`. The audio starts at byte **512**
(verified with a sine of known phase written by the SP-404MKII App: the first frame of
the sine is at byte 512). Reading from byte 32 put 480 bytes of noise in front of every
sample (240 frames mono, 120 frames stereo).

**The 480-byte block.** 225 different byte values, no MD5/SHA-1/SHA-256/SHA-512/CRC32 of
the audio; it stays identical when the project is exported again. The SP-404MKII App
validates it on import:

| SMP written for the import test                        | imported |
|--------------------------------------------------------|----------|
| the app's own file                                     | yes      |
| the same, block zeroed                                 | no       |
| other audio of the **same length**, block copied       | yes      |
| other audio of a **different length**, block copied    | no       |

So the block depends on the sample length but not on the audio content. Its encoding is
unknown; ConvertWithMoss writes it zeroed, therefore the app does not import the samples
of written projects (whether the device accepts them is not tested yet).

## `PADCONF.BIN` — pad configuration (confirmed)

Two forms exist and **both are read**. The SP-404MKII App stores and exports the
**52,000-byte** form and only imports this one (the 31,488-byte form imports the bank
BPMs but no pads); CWM writes it:

| form     | size   | version (@0x08) | header | pad-metadata | pad-names | trailer |
|----------|--------|-----------------|--------|--------------|-----------|---------|
| export   | 31,488 | 2               | 0x80   | @0x80        | @0x6C00   | —       |
| internal | 52,000 | 3               | 0xA0   | @0xA0        | @0x6C20   | 20,480-byte per-pad "marks" section |

The internal header has the project name (ASCII, NUL-padded) at 0x80. The marks section
holds 160 records of 128 bytes: `00 00 00 00` followed by 124 × `FF` for every pad without
marks.

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

Record for pad index `i` at `0x80 + i*172` (export form) resp. `0xA0 + i*172` (internal
form). All fields u32 big-endian. **Decoded by edit-diffs with the SP-404MKII App**: one
parameter changed, project exported, `PADCONF.BIN` compared (✓ = verified this way).

```
+0x00  original sample end (byte offset, == .SMP file size)                        ✓
+0x04  sample start  (byte offset)                                                  ✓
+0x08  sample end    (byte offset, the trimmed end)                                 ✓
+0x0C  level         (0..127)
+0x10  GATE          (1 = plays while the pad is held, 0 = plays to the end)        ✓
+0x14  loop count    (0x7FFFFFFF with LOOP on, 0 otherwise)                         ✓
+0x18  (?) 1 for every record
+0x1C  mute group    (0 = Off, 1..10 = A..J)                                       ✓
+0x24  pad BPM * 100 (9000 = 90.00)
+0x28  flags         bit 0 = FIXED VELOCITY, bit 5 = ONE SHOT                       ✓
+0x2C  loop start    (byte offset)                                                  ✓
+0x30  LOOP          (0 = off, 1 = on)                                              ✓
+0x34  pitch coarse, semitones (signed)                                             ✓
+0x38  pitch fine, cents (signed)                                                   ✓
+0x3C  play mode     (0 Forward, 1 Reverse, 2 Fwd Ping-Pong, 3 Rev Ping-Pong)       ✓ (0/1)
+0x40  time stretch / speed (10000 = 100 %)
+0x44  VINYL         (1 = pitch follows the speed, the pitch fields are ignored)    ✓
+0x48  pan           (0x40 = center, <0x40 left, >0x40 right)
+0x50  (?) 1, +0x54 (?) 2 (Prms page: mute group / BUS FX assign / chromatic mode?)
+0x58  envelope ATTACK  (0..127, 127 = 3 s)                                         ✓
+0x5C  envelope HOLD    (1..100 % of the played range)                              ✓
+0x60  envelope RELEASE (0..127, 127 = 3 s)                                         ✓
```

The probes: mute group C (+0x1C 0→3); GATE off (+0x10 1→0); LOOP on (+0x30 0→1, +0x14 0→0x7FFFFFFF); ONE SHOT
on, which switches LOOP off (+0x28 0→0x20); FIXED VELOCITY on (+0x28 0x20→0x21); play mode
Reverse (+0x3C 0→1); ATTACK 5 / HOLD 97 / RELEASE 2 (+0x58/+0x5C/+0x60); VINYL off with
pitch +2 semi / +14 cents (+0x44 1→0, +0x34 2, +0x38 14); start 4800 / end 72000 / loop
start 24000 frames of a mono sample (+0x04 10112, +0x08 144512, +0x2C 48512). The 3 s of
ATTACK/RELEASE 127 are from the manual; a linear scale is assumed. The probe projects are
in *Testdateien/Roland/SP-404MK2*.

The current code before this decoding treated +0x10 as LOOP and +0x30/+0x34 as the pitch;
since every written record kept VINYL = 1, the written pitch was ignored anyway.

### Filenames — 160 records of 24 bytes, starting at **0x6C00**

Record `i` at `0x6C00 + i*24`: up to 23 chars, then a NUL, **space-padded** to 24
(an all-space/empty field means the pad is unused). This is the pad's display name
(e.g. `01-New Sample`); the *audio* is resolved by the `BANK<b>-<p>.SMP` convention
above, not by this string.

### Start/end trim + loop points — decoded

All positions are byte offsets into the `.SMP` file: **`512 + frame × channels × 2`**, i.e.
512 is the first frame since the audio starts behind the 512-byte header. Verified with
the app (mono: start frame 4800 → 10112, end 72000 → 144512, loop start 24000 → 48512).
The loop runs from the loop start to the (trimmed) end. Reading applies the trim, the loop
start and HOLD (which shortens the played range); writing sets them.

The earlier analysis of the real project, kept for reference:

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

**(Resolved: the base is 512, the 120-frame difference of the stereo pads is the 480-byte
block which was read as audio.)** v1 extracted the full sample (start = 0, end = total frames) — safe
and lossless. Applying the start-trim is a one-line change once the base is confirmed,
by either: an on-device **edit-diff** (set a known start/end/loop, export, diff the
`PADCONF.BIN` — the method used for the other Roland formats), or a firmware-loader
disassembly (image base `0x80000000`). Issue #215's author owns the hardware and is
reverse-engineering the same format — the fastest route to certainty.

## Firmware-confirmed folder tokens

`B:/ROLAND/SP-404MKII/PROJECT_%02d`, `<proj>/SMPL/`, `<proj>/PADCONF.BIN`,
`A:/EXPORT/PROJECT/PROJECT_%02d`, `A:/IMPORT/…`, plus `CHROMATIC` / `16 VELOCITY` /
`MULTIPAD` mode strings — none of which imply a keyboard multi-sample model.
