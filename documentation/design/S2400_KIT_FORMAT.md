# ISLA Instruments S2400 kit (`.kit`) format — reverse-engineered

Reverse-engineered 2026-08 from the **official ISLA "S2400 Kit Builder" web application**
(`https://s2400kiteditor.web.app`, mirrored at `http://www.islaelectronics.com/s2400kiteditor`),
which is the only tool that writes kit files. The kit writer is the function `ck(project,
sampleLengths)` in its Vite bundle `/assets/index-*.js`; that function is the format oracle. Its
exact byte output was reproduced in Node to produce a golden reference kit, and the decode below is
**validated both directions**: a kit written by ConvertWithMoss round-trips, and ConvertWithMoss
reads the golden kit the real app produced byte-for-byte (gain, tuning, filter cutoff in Hz, and the
envelope times converted back from the device's percentage-of-length law all match).

The device firmware (`S2400_20260713.upd`, 1 082 136 bytes) is a **dead end**: it is encrypted -
uniform 7.9998 bits/byte entropy from byte 0, a flat byte histogram, no repeated 16-byte blocks (no
ECB), no repeating-key XOR autocorrelation, and no compression at any offset. The bootloader holds
the key; nothing is extractable from it. The manual documents the folder layout and the (text)
`.MAP` file, but not the binary kit file.

Fields below are confirmed from the app's writer unless marked **(reserved)** = the app always
writes a fixed value whose purpose is unknown, or **(approx)** = the encoding round-trips but its
absolute calibration is inferred.

## What kind of instrument this is

The S2400 is a **sampling drum machine** — a pad sampler, not a keyboard multi-sampler. It has 4
banks (A–D) of 8 pads = **32 pads (tracks)**. Each track holds **one** sample plus its playback
parameters (gain, tuning, filter, volume envelope, choke/trigger group, …) and, optionally, eight
**multi-slices** of that same sample. There is no key-range / per-zone-root model; a track is played
by its pad and, over MIDI, by a single note assigned in the `.MAP` file.

**ConvertWithMoss mapping.** Same as CWM's other pad/drum devices (SP-404MK2, MC-707 kits): the kit
is **one multi-sample source**, and **each track → one single-key zone**. The pads are mapped to
consecutive MIDI notes starting at **note 36 for pad A1** (`36 + trackIndex`), which matches the
device's default sample-tracks MIDI map (`A1=36` in the manual's appendix). A general keyboard
multi-sample written *to* the S2400 is flattened to one sample per pad (up to 32; extra zones are
dropped).

## Project / kit folder layout

A kit lives in its own folder. The device saves kits under `KITS/` and projects under `PROJECTS/`
on a FAT32 SD card, but reads them from anywhere.

```
MyKit/                       (a stand-alone kit)          MyProject/            (a project)
  MyKit.kit                  settings, named after folder   MyProject.S24        sequencer/pattern data
  A1_Kick.wav                one WAV per assigned pad        MyProject.kit        the kit (same base name)
  B3_Snare.wav               ...                             MyProject.map        MIDI note map (optional)
  ...                                                        <the kit's WAVs>
```

A project uses the same kit file for its sounds; the `.S24` is the pattern/song data and is **out of
scope** for this converter (CWM handles the kit, which is where the samples and their settings live).
The `.MAP` file is a text INI-style file documented in the user manual and is not handled here.

Samples are standard WAV. The device audio engine is **48 kHz / 16-bit** (the optimal format); it
also plays 44.1 kHz and 26 kHz, and 24/32/float which it converts on playback. **Loop points are
read from the WAV `smpl` chunk**, not from the kit file (per the manual: "It reads the `smpl` data
chunk … If a loop point exists in the file, the S2400 automatically enables looping").

## The kit file — a flat record stream

The kit file is a flat stream of **self-describing records**, no outer header or table of contents.
Every record starts with a one-byte type tag and a 16-bit little-endian identifier; the payload
depends on the type:

| Type | Name | Layout | Bytes |
|-----:|------|--------|------:|
| `1` | U32  | `u8 type` · `u16 id` · `u32 value` (LE) | 7 |
| `2` | I32  | `u8 type` · `u16 id` · `i32 value` (LE) | 7 |
| `3` | BLOB | `u8 type` · `u16 id` · `u32 len` (LE) · `len` bytes | 7 + len |

A BLOB is used for two things: a **string** (`len` = string length + 1, the bytes are ASCII followed
by a terminating `0x00`) and a **16-bit value** (`len` = 2, the two bytes are the value LE) used for
the envelope modulation amounts.

Because the type tag alone gives each record's length, the stream is fully self-describing: a reader
can skip records it does not know, and parsing simply stops at end-of-file or at the first byte that
is not a valid type (`1`–`3`). This makes the reader robust to firmware revisions that add records,
and tolerant of the trailing padding described at the end of this document.

### Overall structure

```
[header]   id 0  version   (U32 = 0x00020002)      ← file magic: 01 00 00 02 00 02 00
           id 1  track count (U32)
           id 58 marker    (U32 = 1)
[track 0]  id 2  track index (starts a track block)
           ...per-track fields...
           id 11 slice slot 0 …  (nine slots, 0..8; slot 8 carries the performance parameters)
           ...
[track 1]  id 2  track index
           ...
```

The first record is always `01 00 00 02 00 02 00` (type U32, id 0, value `0x00020002`), which serves
as the file's magic number.

### Per-track fields

Written in this order, once per assigned track, immediately after the `id 2` track-index record:

| id | Type | Field | Notes |
|---:|------|-------|-------|
| 2  | U32  | Track index | 0-based pad index (0 = A1 … 31 = D8); starts the block |
| 10 | BLOB | Track name | The WAV file name **without** its extension |
| 3  | U32  | Pad color | `0x00BBGGRR` (byte order in the value LE: `RR GG BB 00`) |
| 53 | I32  | *(reserved)* | Always `0` |
| 36 | U32  | *(reserved)* | Always `2` |
| 60 | I32  | Track gain | Signed **decibels, direct** (e.g. `-3` → `FD FF FF FF`) |
| 37 | U32  | Envelope style | `0` = Classic, `1` = HiFi |
| 4  | U32  | Output (first / left) | Output channel index |
| 5  | U32  | Output (second / right) | Stereo tracks: `min(7, channel+1)`; mono: same as id 4 |
| 59 | U32  | Choke group | `0` = none |
| 64 | U32  | Trigger group | `0` = none |
| 6  | U32  | Bit-depth reduction | Playback bit-crush |
| 7  | U32  | Resampler / audio engine | App default `1` |
| 8  | U32  | Mix-down | `0` MONO_L · `1` MONO_R · `2` MONO_LR · `3` STEREO |
| 49 | U32  | *(reserved)* | Always `0` |
| 57 | U32  | *(reserved)* | Always `0` |
| 55 | U32  | *(reserved)* | Always `0` |
| 52 | U32  | Gate mode | `0` = one-shot, `1` = gated (note-off stops) |
| 56 | U32  | Stop on mute | `0` / `1` |

### Slice slots

Each track ends with **nine** slots, each opened by an `id 11` (slot index) record with value `0`
to `8`. Slots `0`–`7` are the eight **multi-slices**; **slot 8 carries the main performance
parameters** (level, filter, pitch, envelopes). The device's MIDI CC table numbers the main slice as
`0` and the multi-slices `1`–`8`; the file uses the opposite convention (main = slot 8).

Fields inside a slot:

| id | Type | Field | Notes |
|---:|------|-------|-------|
| 12 | U32  | Level | `0`–`255`; `255` = unity. Playback gain = `level / 255` |
| 35 | U32  | Filter type | `0` = low-pass · `1` = band-pass · `2` = high-pass |
| 15 | U32  | Filter resonance | `0`–`255` |
| 54 | U32  | Filter cutoff | **Hertz** (20–20000) in the main slot; the app writes `1023` in slots 0–7 |
| 61 | I32  | Fine pitch | Signed **cents** (e.g. `1250` = +12.5 semitones) |
| 17 | U32  | Slice start | Sample frame |
| 18 | U32  | Slice end | Sample frame (whole sample = `frames - 1`) |
| 34 | U32  | *(reserved)* | Always `0` |
| 19 | U32  | Loop start | Sample frame (main slot: `0`) |
| 51 | U32  | Loop end | Sample frame (main slot: `frames - 1`) |
| 50 | U32  | *(reserved)* | Always `0` |

Then the volume envelope, which depends on the track's envelope style:

* **Classic** (`id 37` = 0): a single `id 13` (U32) **classic decay**, `0`–`31`. `31` disables the
  envelope (hold forever). The device's absolute decay times come from a firmware table which, being
  encrypted, is unknown.
* **HiFi** (`id 37` = 1): **two** envelopes written back to back. Envelope index `0` is the volume
  envelope; index `1` is a spare (the app writes it neutral). Each envelope is:

  | id | Type | Field | Notes |
  |---:|------|-------|-------|
  | 42 | U32  | Envelope index | `0` = volume, `1` = spare |
  | 43 | U32  | Attack | `0`–`1023` |
  | 44 | U32  | Attack hold | `0`–`1023` |
  | 45 | U32  | Decay | `0`–`1023` |
  | 46 | U32  | Sustain **level** | `0`–`1023` |
  | 47 | U32  | Sustain hold | `0`–`1023` |
  | 48 | U32  | Release | `0`–`1023` |
  | 40 | BLOB | Envelope→pitch amount | `i16`, clamped ±1000 |
  | 41 | BLOB | Envelope→filter amount | `i16`, clamped ±1000 |

  The five time stages (attack, attack-hold, decay, sustain-hold, release) are a **percentage of the
  total sample length**, not absolute seconds: `seconds = value / 1023 × sampleLengthSeconds`. Sustain
  is a level on the same `0`–`1023` scale.

In the multi-slices (slots 0–7) the app writes the level at `255`, the filter neutral (type 0,
resonance 0, cutoff `1023`), the slice/loop points at the slice region (or the whole sample when no
slices are set), and either a classic decay of `31` or two neutral HiFi envelopes
(`attack/attack-hold/decay/release = 0`, `sustain = sustain-hold = 1023`, mods `0`).

## Value encodings

* **Gain** (id 60): signed 32-bit **decibels**, stored directly. `-3 dB` is `0xFFFFFFFD`.
* **Fine pitch** (id 61): signed 32-bit **cents**. `+12.5` semitones is `1250`.
* **Color** (id 3): `0x00BBGGRR`. A `#RRGGBB` color is packed as blue<<16 | green<<8 | red.
* **Filter cutoff** (id 54, main slot): an actual **frequency in Hertz**. The web app derives it from
  a normalized 0–1 slider through a 1024-entry lookup table (20 Hz … 20 000 Hz); the value stored and
  read back is the Hz value itself, so a reader/writer works directly in Hz and clamps to
  `[20, 20000]`.
* **Filter resonance** (id 15): `0`–`255`. The app maps a Q value `0.1`–`20` **linearly**:
  `round((Q - 0.1) / (20 - 0.1) × 255)`. CWM treats it as a plain normalized `0`–`1` **(approx)** —
  it round-trips, but the absolute Q calibration is not pinned.
* **Envelope stages** (id 43–48): `0`–`1023`; times are a fraction of the sample length, sustain is a
  level (see above).
* **Classic decay** (id 13): `0`–`31`, `31` = off **(approx** absolute times unknown**)**.
* **Level** (id 12): `0`–`255`, `255` = unity.

## What the reference app over-allocates

The Kit Builder's size calculation reserves, per HiFi envelope, room for **8 fixed records + 2
16-bit blobs** (`8 × 7 + 2 × 9 = 74` bytes after the index) but actually writes **6 records + 2
blobs** (`60` bytes), over-allocating **14 bytes per envelope**. Those bytes are left as zeros and
**shipped in the file**: the golden reference is 3 331 bytes allocated versus 3 079 written = 252
trailing zeros (one HiFi track × 9 slots × 2 envelopes × 14). This is a benign uninitialized-buffer
artifact — the device stops reading at the first `0x00` type byte. ConvertWithMoss **writes a tight
stream** (no padding) and **tolerates trailing padding** on read.

## ConvertWithMoss implementation notes

* Package `format/isla/s2400/`: `S2400Constants` (the ids/types/enums above), `S2400Detector`,
  `S2400Creator`. The reader parses the record stream generically and only needs the main slot
  (slot 8) and the volume envelope (HiFi index 0); it stops on any non-`1..3` type byte.
* **Reading:** each track → one zone at MIDI note `36 + trackIndex`, with gain, tuning, choke group,
  filter (a low-pass parked at 20 kHz or a high-pass parked at 20 Hz is treated as no filter) and the
  volume envelope (HiFi converted from percent-of-length to seconds; classic approximated by its
  release). Loops come from the WAV `smpl` chunk via `addZoneData`. Sustain-hold has no model
  equivalent and is dropped.
* **Writing:** one kit folder per multi-sample — the `.kit` named after the folder plus one WAV per
  zone (48 kHz / 16-bit; a sample above 48 kHz is down-sampled and its loop positions rescaled, a
  44.1 kHz sample is kept). The volume envelope is written as HiFi index 0; the record order and the
  slot defaults mirror the reference app exactly.
* **Loops in the WAV.** Because the device reads loops from the `smpl` chunk, the creator always
  writes that chunk for a looped zone. Note that the shared `WavChunkSettingsUI` resets its chunk
  flags to off on the command line unless `-p<prefix>WriteSampleChunk=1` is passed, so
  `S2400Creator` overrides `additionalProcessing` to write the loop chunk regardless.

None of this has been verified on real S2400 hardware; it is validated against the official Kit
Builder's output, which is as authoritative a reference as exists short of the device itself.
