# DirectWave DWP Format

Reverse-engineered structure of FL Studio DirectWave program files (`.dwp`), as written by
DirectWave on FL Studio Desktop when a program is saved with the *Monolithic file* option
disabled. The same file is what FL Studio Mobile imports (packaged in one folder together
with its WAV samples, optionally zipped).

Sources of this specification:

* A real DirectWave export (`Instrument.dwp`, 47307 bytes, 48 chromatic stereo samples,
  FL Studio Desktop, published as a test fixture of the
  [Dwp-Creator](https://github.com/jvksdigitalstudio/Dwp-Creator) project) which was
  re-analyzed byte by byte for this implementation. The block-stream walk accounts for
  every byte of the file (no gaps, no trailing data).
* The block envelope layout documented by the Dwp-Creator project (verified against the
  file). Note: that project documents the key-range bytes as low/root/high — the specimen
  disproves this, see below.
* The DirectWave manual (Automap file-name token syntax, see the detector fall-back).
* The six factory programs of FL Studio Mobile (`FL Studio Mobile Factory Data/DirectWave
  Samples`, version byte 0x25 instead of the 0x26 of the specimen above): all of them
  tokenize completely, the preamble size rule and the zone mapping layout hold, and they
  add data points marked below. Differences of the older version: 100 parameter slot
  blocks instead of 99, and the bytes-per-frame field holds other values (see below).
* 85 monolithic programs of a commercial library (version 0x26, 288 sample containers),
  which pin down the embedded audio (see below).
* The 26 DirectWave programs of the legacy patch pack of FL Studio
  (`Data/Patches/Packs/Legacy`, version byte **0x24**). They are the only known files of
  that version; its preamble is 4 bytes longer, so the block stream starts at 0x5E instead
  of 0x5A. They also contain the only zones with an engaged filter found anywhere: 111
  zones with a low-pass, which confirm the filter type encoding described below. Their
  samples are WAV files with the Ogg Vorbis codec (format tag 0x674F), which the sample
  loader of ConvertWithMoss cannot decode, so their audio is not converted.

Everything below is little-endian.

## Overall layout

```
offset 0x00: 'DwPr' magic (4 bytes ASCII)
offset 0x04: fixed header/preamble (up to 0x5A; up to 0x5E in the version 0x24)
after it:    flat, gapless stream of blocks until end of file
```

### Block envelope

Every block, both at top level and nested inside a sample container:

```
u32  tag
u32  payloadLength
u32  reserved        (always 0 in the specimen)
u8   payload[payloadLength]
```

There are no absolute offsets anywhere; the format is purely sequential.

## Preamble (0x00 - 0x59)

Mostly opaque global settings, copied verbatim by the creator. Known/derived fields:

| offset | type | value in specimen | meaning |
|-------:|------|------------------:|---------|
| 0x00 | char[4] | `DwPr` | magic |
| 0x04 | u32 | 38 | version (38 = FL Studio 20.x specimen, 37 = FL Studio Mobile factory files) |
| 0x08 | u32 | 6 | unknown |
| 0x0C | u32 | 16 | unknown |
| 0x24 | u32 | 1 | unknown (program count?) |
| 0x28 | u32 | 47259 | **file size − 48** (verified in all seven specimens); patched by the creator |
| 0x30 | u32 | 100 | unknown (a volume?) |
| 0x34 | u32 | 30 | unknown |
| 0x42 | f32 | 1.0 | unknown, probably the master volume (1.0 in five factory files, 0.83/0.45 elsewhere) |
| 0x4A | u32 | 48 | **the number of sample containers** - verified in all seven specimens. DirectWave trusts this count: writing a wrong value crashes FL Studio with an access violation when the program is loaded (observed with FL Studio 2026 on macOS), so it must always match the number of 0x0003 blocks. |

The remaining bytes are unknown and kept as-is from the factory files.

## Top-level blocks (in file order)

| tag | count | length | content |
|----:|------:|-------:|---------|
| 0x0066 | 1 | n | instrument name (ASCII, no terminator) |
| 0x0067 | 1 | n | path of the .dwp itself, single backslashes: `G:\5\Instrument.dwp` |
| 0x0068 | 1 | 10 | zeroed (fixed size - it matching the name length in the first specimen was a coincidence) |
| 0x0069 | 1 | 18 | `u16 0` and the four floats `0.75, 0.75, 0.5, 0.75` |
| 0x006A | 1 | 17 | `u8 0` and the four floats `0.25, 0.5, 0.75, 0.25` |
| 0x006B | 1 | 17 | `u8 0` and the four floats `0.25, 0.5, 0.25, 0.0` |
| 0x006C | 2 | 20 | `u32 0` and the floats `0.1, 1.0, 0.0, 0.0` |
| 0x006D | 4 | 4 | zeroed |
| 0x006E | 100 | 13 | parameter slot: `u32 id, u8 0, f32 1.0, u32 0` with the ids 0..99 |

The blocks 0x0069 to 0x006C are **program parameters, not metadata** - all 117 available
programs of all three format versions carry exactly the payloads above (the version 0x24
programs of the legacy pack are the only ones which deviate, and only where the program
actually uses the parameter). Writing them zeroed produces a program which loads and plays
but does not sound like its source; that was how the values were found.
| 0x0003 | N | var | one nested sample container per sample zone (N = the count at preamble 0x4A) |
| 0x0002 | 1 | 0 | terminator |

## Sample container (tag 0x0003)

The payload is a nested block stream with the same envelope. Blocks in order:

| tag | length | content |
|----:|-------:|---------|
| 0x01F4 | 25 | zone mapping, see below |
| 0x01F5 | n | sample name without extension, e.g. `Instrument_C3_127` |
| 0x01F6 | n | sample path, single backslashes: `D:\Instrument\Instrument_C3_127.wav` |
| 0x01F7 | 40 | audio format, see below |
| 0x01F8 | 8 | `f32 0.5, u8 0, u8 0, u8 100, u8 0` (identical in all seven specimens) |
| 0x01F9 | 14 | opaque parameters (zeroed in the 0x26 specimen; `0, 0.5, 0.5, 1.0` floats in the factory files) |
| 0x01FA | 48 | opaque parameters (factory: `0.5, 0.5, 0.5, 1.0` floats then zeros; the 0x26 specimen instead holds zeros, 1.0 at +12 and what looks like uninitialized heap noise) |
| 0x01FB | 20 | **filter 1 / filter 2** (twice), see below |
| 0x01FC | 2 | zeroed |
| 0x01FD | 16 | **the amplitude envelope**: 4 floats attack, decay, sustain, release (see below) |
| 0x01FE-0x0201 | 9 | opaque parameters (one each) |
| 0x0202 | 16 | opaque parameters (twice) |
| 0x0203 | 20 | opaque, varies per program (twice) |
| 0x0204 | 8 | 16 of them: `u16, u16, f32` triples, opaque parameters |
| 0x0004 | 0 | container terminator |

The creator copies all opaque blocks verbatim from the first sample container of the
factory 'Nylon Guitar' program, so written files stay inside the byte patterns of the six
known-good Image-Line files.

### Zone mapping (0x01F4, 25 bytes)

```
u8  rootKey
u8  lowKey
u8  highKey
u8  lowVelocity      (0 in all specimens)
u8  highVelocity     (127 in all specimens)
u8  zero[4]
f32 zone gain at offset 9  (linear amplitude, 1.0 = 0 dB; a factory zone holds 0.675 = ca. -3.4 dB)
f32 zone panning at offset 13 (0.5 = center)
u8  flag at offset 17      (0x01 in the specimen, 0x00 in the factory files)
u8  zero[4]
u8  0x02 at offset 22
u8  zero[2]
```

**Byte order proof for root/low/high:** the specimen is chromatic from C3 to B6. The first
zone (`Instrument_C3_127`) holds `24 00 24` and the last (`Instrument_B6_127`) holds
`53 53 7F` — i.e. the first zone is root 36, low 0 (extended to the bottom), high 36 and
the last is root 83, low 83, high 127 (extended to the top). Only root/low/high fits both
edge zones; the low/root/high order published by Dwp-Creator fits neither.

This also pins the note-name convention to Image-Line's: `C3` ↔ MIDI 36, i.e.
**MIDI = 12 × octave + semitone** (C5 = middle C = 60, no negative octaves, top note G10 =
127). This matters for parsing/writing sample names and Automap tokens.

### Audio format (0x01F7, 40 bytes)

```
offset 0:  u32 frameCount   (verified: WAV data-chunk size / bytes per frame)
offset 4:  u32 0
offset 8:  u32 channelCount (1 and 2 verified)
offset 12: u32 bytesPerFrame (4 = stereo 16 bit in the 0x26 specimen - but NOT reliable, see below)
offset 16: f32 sampleRate   (44100.0 — the rate is stored as a float!)
offset 20: u32 loopMode     (see the loop mode enum below)
offset 24: u32 loopStart    (frames)
offset 28: u32 loopEnd      (frames)
offset 32: u32 0
offset 36: u32 sourceBitDepth (16; 32 for the two programs sampled as 32-bit float)
```

**The loop lives here** (not in a separate block): all sustained factory programs are
looped — the Strings Section loops 66224..132300, the Rhodes from roughly half, the Nylon
Guitar in a short tail — and all percussive programs (Picked Bass, Club Pluck) have
mode/start/end 0/0/0. Two trimmed Rhodes zones have a loop end slightly beyond their frame
count, so readers should tolerate that. Note the factory WAV files carry no `smpl` chunks;
the DWP is the only loop source there.

The field at offset 12 holds the bytes per frame only in the version 0x26 specimen. In the
version 0x25 factory files it holds varying powers of two (4 to 128, differing between the
samples of one program, loosely following the frame count — possibly a waveform display
cache stride). It must therefore not be used to derive the sample resolution when reading.

## Sample resolution

The stored paths are absolute paths of the machine that saved the program (the factory
files use environment-variable prefixes like `%ILSharedData%\...` and `%USERPROFILE%\...`)
and must be ignored. DirectWave desktop saves the program as `<Name>.dwp` next to a `<Name>` folder
containing the WAV files; the FL Studio Mobile import layout (as used by Dwp-Creator's
export and FLM zip imports) is a single `<Name>` folder containing both `<Name>.dwp` and
the WAV files. The detector therefore looks for each sample (last path component of tag
0x01F6) next to the .dwp file, then in a `<dwp base name>` sub-folder, then in a sub-folder
named like the second-to-last component of the stored path.

## Monolithic files

A program saved with the *Monolithic file* option keeps exactly the same preamble and block
structure; the samples are added as one extra block per sample container:

| tag | position | content |
|----:|----------|---------|
| 0x0206 | the last block of a sample container, after the sixteen 0x0204 blocks and before the 0x0004 terminator | `u32 length, u32 0, FLAC stream` |

The audio is therefore **FLAC compressed** - the block payload carries the length of the
FLAC data, four unused bytes and then a complete FLAC stream starting with its `fLaC`
magic. The sample path block (0x01F6) is still present and still holds the path of the
machine which saved the program, which is why the
[dwsanitizer](https://github.com/kachine/dwsanitizer) project can mask paths in monolithic
files; it is ignored when the embedded audio is present.

This was confirmed on 85 monolithic programs (2040 sample containers): in every one of them
the length field equals the payload size minus 8, the field at offset 4 is 0, and the block
sits between the last 0x0204 block and the terminator. Both reading and writing use it. Every
one of the 2040 embedded samples is 16 bit, stereo and 44.1 kHz, but the resolution is not a
constraint - a written program with 24 bit audio loads and plays.

### The audio is stored in blocks of 512 frames

The frame count of every one of the 2040 embedded samples is a **multiple of 512**, and the
FLAC stream always holds exactly 512 frames *more* than the frame count in the audio format
block. Samples in the non-monolithic programs have arbitrary lengths, so the rule belongs to
the embedded-audio path. A program whose samples do not fill their last block crashes the
plug-in while loading (see the crash section below), therefore written audio is padded with
silence up to a full block and the encoded stream gets one block more, which reproduces what
DirectWave itself writes.

The tag was found before a specimen was available by dumping the block writer of the
plug-in binary (see below): the payload length is a constant 8 bytes before the tag for
every block of fixed size, so the blocks *without* such a constant are the variable-length
ones - the sample name, the sample path and the two otherwise unused tags 0x0205 and
0x0206.

## Amplitude envelope (0x01FD)

Four floats: attack, decay, sustain, release as knob positions 0..1. Identified by
differential analysis of the factory programs:

* Attack is 0 in all seven specimens (sampled content plays instantly).
* Sustain is 1.0 everywhere except the 'Electric' piano, where it is **0.0** — and the
  Electric samples are looped, so sustain 0 with a long decay is exactly how a sampler
  makes a looped electric piano die away.
* Decay is 0.5 by default; Electric raises it to 0.81 (the long e-piano decay); the
  version 0x26 default is 1.0.
* Release is 0.25 by default and is nudged up on precisely the two sustained factory
  instruments (Rhodes 0.32, Strings 0.325); the version 0x26 default is 0.18.

The defaults differ per version: 0x25 = `0, 0.5, 1.0, 0.25`, 0x26 = `0, 1.0, 1.0, 0.18`.
The detector skips the envelope when the block equals the version defaults.

The knob-to-time law was measured the same way as the filter cutoff: a program was written
whose six zones differ only in the decay knob (0.20 … 0.95, sustain 0, so the note dies
while the key is held) next to five reference zones whose fade-out is baked into their
audio at exactly 0.5, 1, 2, 4 and 8 seconds. A recording of all of them gives

| decay knob | 0.20 | 0.35 | 0.50 | 0.65 | 0.80 | 0.95 |
|---|---|---|---|---|---|---|
| measured | 0.016 s | 0.14 s | 0.57 s | 1.65 s | 3.73 s | 7.49 s |

(measured to -40 dB and corrected by the 3 % which the reference zones show that metric to
be short - the 0.5 s reference measures 0.49 s and the 1 s reference 0.96 s). A
least-squares fit gives

```
seconds = 9.07 * position ^ 3.96
```

which reproduces every point within 3 %; the nearby round law `9 * position^4` is within
6.5 %. The previously assumed `10 * position^3` was wrong by up to a factor of three at
short times. The sustain is a level and therefore exact.

## Enumerations from the plugin binary

The DirectWave plug-in binary (`DirectWave_x64.dylib` of FL Studio 2026) contains its
enumerations as fixed-width (10 byte, NUL terminated) string tables, which makes them
authoritative rather than inferred. Search the binary for `Off      ` to find them; the
three tables follow each other:

| index | filter type | LFO waveform | loop mode |
|------:|-------------|--------------|-----------|
| 0 | Off | Sine | Disabled |
| 1 | Lowpass | Abs Sine | One-Shot |
| 2 | Highpass | Triangle | Forward |
| 3 | Bandpass | Square | Sustained |
| 4 | Notch | Saw | Bounce |
| 5 | Allpass | Inv Saw | |
| 6 | Minisynth | Random | |
| 7 | Vox | LP Random | |

The filter list is confirmed a second time by the editor library (`libeditor.dylib`), which
carries the plug-in's menus as Delphi VCL form data: the *FilterTypeMenu* holds one
`TQuickMenuItem` per type whose `Tag` property is the enum value (`Off` has no Tag, i.e.
0, then Lowpass = 1 … Vox = 7). That extraction is trustworthy because the *LoopTypeMenu*
right next to it yields exactly the loop values which the real files prove
(Disabled = 0, Forward = 2).

The loop mode table confirms the decoding of the loop mode field: the 0 and 2 of the
specimens are *Disabled* and *Forward*, and it adds *One-Shot* (plays to the end and
ignores a note-off), *Sustained* (loop while the key is held, then play the remainder =
the model's loop-until-release) and *Bounce* (= alternating/ping-pong). All five are
converted in both directions.

A fourth table right after them holds the modulation **sources** and **targets** in one
list of 14 byte entries. The first 24 entries are the sources:

```
 1 Note Key      5 Program Lfo 1   9 Zone Env 1    13 Amp Follower
 2 Note Velocity 6 Program Lfo 2  10 Zone Env 2    14 Sample Pos
 3 Mod Wheel     7 <Reserved>     11 Zone Lfo 1    15 Random Value
 4 Pitch Bend    8 <Reserved>     12 Zone Lfo 2    16-19 Mod Val 1-4
```

Entry 24 is a `---` separator and the rest are the 47 modulation targets, which double as
the names of the automatable parameters:

```
 0 Voice Pitch      12 Amp Env Att      24 Delay Send
 1 Voice Gain       13 Amp Env Dec      25 Chorus Send
 2 Voice Pan        14 Amp Env Sus      26 Reverb Send
 3 Sample Start     15 Amp Env Rel      27 Dry Amount
 4 Loop Start       16 Ring Mod Rate    28 Ts. Time
 5 Loop End         17 Ring Mod Mix     29 Ts. Grain
 6 Fl1 Cutoff       18 Decimator Stp    30 Ts. Smooth
 7 Fl1 Resonance    19 Decimator Mix    31-46 Mod Amt P1-1 .. P4-4
 8 Fl1 Shape        20 Reducer Bits
 9 Fl2 Cutoff       21 Reducer Mix
10 Fl2 Resonance    22 Phaser Freq
11 Fl2 Shape        23 Phaser Mix
```

This confirms the structure the manual describes - two filters with cutoff/resonance/shape
per zone, an ADSR amplitude envelope, and a 4x4 modulation matrix - and matches the block
inventory below (two 0x01FB filter blocks, the 0x01FD envelope, sixteen 0x0204 matrix
slots). In a matrix slot the source is the source index above and the **target is the index
in this table plus one** (the separator is target 0 = no target).

## Filter blocks (0x01FB, twice - filter 1 and filter 2)

```
offset 0: u32 type   (the filter type enum above - an INTEGER, not a float)
offset 4: f32 cutoff (knob position 0-1)
offset 8: f32 resonance (knob position 0-1)
offset 12: f32 unknown (0.0 in all specimens; the 'Shape' of the parameter table?)
offset 16: f32 unknown (0.0 in all specimens)
```

Every specimen has both filters Off, so the layout was settled by experiment: three probe
programs were written which put a Lowpass into the candidate fields, and only the variant
with the **type as a 32-bit integer at offset 0 combined with a low cutoff at offset 4**
silenced the zone (a low-pass at a near-minimum cutoff mutes a 220 Hz saw wave), while the
control zone in the same program stayed audible. The variant which put the type into the
2-byte block 0x01FC changed nothing.

The cutoff knob is mapped exponentially **from 45 Hz to 20 kHz**. Since the knob laws are
not stored in the binary (see below), the range was measured by listening: a program with
ten zones was written which play the same 220 Hz saw wave through a low-pass filter with
the cutoff knob at 0.10, 0.18, 0.26 … 1.0. The fundamental starts to pass at the zone with
the knob at **0.26** - which puts the cutoff at 220 Hz there - and the wave is fully open
at **0.66**. A range of 45 Hz to 20 kHz reproduces the first (219.7 Hz) almost exactly and
puts the second at ca. 2.5 kHz, i.e. about the 11th harmonic, which is consistent with
'fully open'; it also explains the two quiet zones below (83 Hz and 135 Hz cutoff against a
220 Hz fundamental). The remaining uncertainty is roughly ±10 %. Only the first filter is converted since the model has one filter per
zone; the second block is left at its template default (Off).

## Parameter block hypotheses (single tweaked specimen)

The DirectWave manual gives the structure that matches the remaining blocks: two LFOs and
a 4x4 modulation matrix.
* 0x0204 (16 of them, 8 bytes) = **the 4x4 modulation matrix**: `u16 source, u16 target,
  f32 amount`. Both indices address the source/target table of the plug-in binary (see the
  enumeration section), the target with an offset of one. That decoding makes the two
  defaults of the desktop programs read correctly: `(2,2,0.5)` = Note Velocity to Voice Gain
  and `(4,1,0.5714)` = Pitch Bend to Voice Pitch. The FL Studio Mobile programs default to
  `(2,2,1.0)`, `(3,34,0.75)` = Mod Wheel to Mod Amt P1-3 and `(12,1,0.5)` = Zone LFO 2 to
  Voice Pitch instead, which is why their containers must not be used as a template for
  programs written for the desktop plug-in.
* 0x0202 (twice, 16 bytes) and 0x0203 (twice, 20 bytes) = **LFO 1 / LFO 2** parameter
  candidates ('Electric' raises 0x0203#1 field 1 from 0.1 to 0.355 — plausibly an LFO
  rate for its tremolo, routed via its extra matrix slots).

## Two crashes, one lesson

Both crashes seen while developing this support came from writing a value into a field
where the real files only ever show a different value population:

* The sample count at preamble 0x4A kept the template's value instead of the real number
  of containers - DirectWave trusts the count and reads past the end of the container
  list, which crashes the plug-in loader with an access violation.
* The field at offset 12 of the audio format block was written as 2 (bytes per frame),
  while real files hold powers of two from 4 to 128 (the DirectWave zone list shows this
  field in its 'Ticks' column). The loader accepted it, but the editor crashed when it
  drew the zone.
* A sample whose frame count is not a multiple of 512 crashes the loader with an access
  violation a few bytes past the end of a heap block. The lengths of the samples of a
  converted instrument are whatever the source has - resampling 48 kHz material to 44.1 kHz
  produced 25 ragged lengths out of 63 and crashed; the same instrument converted without
  resampling happened to land on 128 frame boundaries and loaded. See the block rule above.

Therefore: **never write a value outside the population observed in real files** unless
the meaning is confirmed from a table inside the plug-in binary (as the loop modes above
are).

A quieter version of the same mistake costs sound instead of a crash: the first version of
the creator built its programs from the container of an FL Studio Mobile factory program and
zeroed the program parameter blocks 0x0069 to 0x006C. Every written program loaded, but the
percussive attack of the source instrument was gone. The templates therefore come from a real
**monolithic desktop** program; a written file now equals a real program byte for byte except
for the six blocks which carry the converted content (zone mapping, sample name, sample path,
audio format, amplitude envelope, embedded audio).

## The knob laws are not in the binary (searched)

The cutoff and envelope-time laws were searched for in the plug-in binary and are **not
stored as data**:

* No lookup tables: a scan for monotonically rising float arrays of 48 entries or more in
  an audio-plausible range returns nothing.
* No range pairs: no adjacent (minimum, maximum) frequency constants in single or double
  precision anywhere in either library.
* The parameters do have ranges - the binary contains the source file name
  `DirectWaveParameters.cpp` and the assertion text `RangeMin < RangeMax` - but no data
  table of parameter descriptors references the parameter name strings, so the ranges are
  passed as immediates when the parameter list is built in code. Extracting them would
  require disassembling that constructor.

What the search did yield is the display formatting of the parameters, which corroborates
several decodings: `%.1f dB` and `-oo dB` for gains (so the gain really is a level with a
silent bottom, as the -96 dB parked layers of converted E-mu presets assume), `%.2d L` /
`Center` / `+%.2d R` for panning (0.5 is the center), `%d Ticks` for the field at offset
12 of the audio format block (which the zone list shows in its *Ticks* column), and
`%.1f Hz` / `%.2f kHz` / `%.2f sec` / `%.2f ms` / `%d Cents` for the remaining units.

Both the cutoff and the envelope time law were therefore measured by listening instead
(see the filter and envelope sections above): a program whose zones differ in one knob
only, played from the on-screen keyboard of the plug-in, with reference zones whose
behavior is baked into their audio to calibrate the measurement. The plug-in wrapper's
own parameter list is *not* a way to read the values: it shows the channel settings of FL
Studio, not the 47 DirectWave parameters.

## Not yet decoded

* The LFO block layout and the source/target enums of the modulation matrix.
* Trigger groups (round-robin/random cycles) and their location in the opaque bytes.
* The purpose of the tag 0x0205, which the block writer of the plug-in knows but which
  appears in none of the available specimens.
* The structure of .dwb banks — no specimens. The file-name fall-back of the detector
  (see below) covers non-monolithic .dwb exports.

## Detector fall-back: sampled/Automap file names

When the preset file is not a parseable `DwPr` stream (e.g. a .dwb bank), the detector
falls back to parsing the names of the WAV files in the associated sample folder:

* Auto-sampling names (written by FL Studio's *Create DirectWave instrument*):
  `<program> Layers_<note>_<velocity>.wav` for a single cycle or
  `<program> <N>xCycles_<note>_<velocity>_<cycle>.wav` for N round-robin cycles.
  Key and velocity ranges are reconstructed by mid-point interpolation between the
  sampled root notes/velocities (the algorithm of the DirectWave-to-Sampler project).
* Automap token names (documented in the DirectWave manual): everything after the last
  `_` is a list of `+`-separated tokens: a bare note = root key, `K<low>-<high>` = key
  range, `V<low>-<high>` = velocity range, `TG<n>` = trigger group, `TY<n>` = trigger
  type (0 normal, 1 cycle, 2 random, 3 avoid previous), `TF<n>`/`TO<n>` frequency/overlap.
  Example from the manual: `samplename_C4+KA3-F#4+V64-95+TG1+TY3+TF100+TO15.wav`.

All note names use the Image-Line octave convention described above.
