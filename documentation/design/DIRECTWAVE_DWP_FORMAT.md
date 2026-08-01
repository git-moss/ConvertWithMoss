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

Everything below is little-endian.

## Overall layout

```
offset 0x00: 'DwPr' magic (4 bytes ASCII)
offset 0x04: fixed header/preamble up to 0x5A (see below)
offset 0x5A: flat, gapless stream of blocks until end of file
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
| 0x28 | u32 | 47259 | **file size − 48** (verified: 47307 − 48); patched by the creator |
| 0x30 | u32 | 100 | unknown (a volume?) |
| 0x34 | u32 | 30 | unknown |

The remaining bytes are unaligned/unknown and kept as-is from the specimen.

## Top-level blocks (in file order)

| tag | count | length | content |
|----:|------:|-------:|---------|
| 0x0066 | 1 | n | instrument name (ASCII, no terminator) |
| 0x0067 | 1 | n | path of the .dwp itself, backslashes doubled: `D:\\Instrument.dwp` |
| 0x0068 | 1 | len(0x0066) | zeroed shadow of the name (length tracks the name!) |
| 0x0069 | 1 | len(0x0067) | zeroed shadow of the path (length tracks the path!) |
| 0x006A | 1 | 17 | zeroed (metadata slot, e.g. author) |
| 0x006B | 1 | 17 | zeroed (metadata slot) |
| 0x006C | 2 | 20 | zeroed (metadata slots) |
| 0x006D | 4 | 4 | zeroed |
| 0x006E | 99 | 13 | parameter slot: `u32 id (1..99), u8 0, f32 1.0, u32 0` |
| 0x0003 | N | var | one nested sample container per sample zone |
| 0x0002 | 1 | 0 | terminator |

## Sample container (tag 0x0003)

The payload is a nested block stream with the same envelope. Blocks in order:

| tag | length | content |
|----:|-------:|---------|
| 0x01F4 | 25 | zone mapping, see below |
| 0x01F5 | n | sample name without extension, e.g. `Instrument_C3_127` |
| 0x01F6 | n | sample path, single backslashes: `D:\Instrument\Instrument_C3_127.wav` |
| 0x01F7 | 40 | audio format, see below |
| 0x01F8 | 8 | `f32 0.5, u8 0, u8 0, u8 100, u8 0` (defaults, opaque) |
| 0x01F9 | 14 | zeroed |
| 0x01FA | 48 | opaque; contains f32 1.0 at +12 and u32 1 at +44, plus what looks like uninitialized heap noise at +20/+28/+36 in the specimen (suspected loop/edit state) |
| 0x01FB | 20 | zeroed (twice) |
| 0x01FC | 2 | zeroed |
| 0x01FD | 16 | `f32 0.0, f32 1.0, f32 1.0, f32 0.18` (defaults, opaque) |
| 0x01FE-0x0201 | 9 | zeroed (one each) |
| 0x0202 | 16 | zeroed (twice) |
| 0x0203 | 20 | zeroed (twice) |
| 0x0204 | 8 | 16 of them; the first is `u16 2, u16 2, u32 0`, the rest zeroed |
| 0x0004 | 0 | container terminator |

### Zone mapping (0x01F4, 25 bytes)

```
u8  rootKey
u8  lowKey
u8  highKey
u8  lowVelocity      (0 in all specimens)
u8  highVelocity     (127 in all specimens)
u8  zero[4]
f32 zone gain at offset 9  (1.0 in most zones; a factory zone holds 0.675)
f32 0.5 at offset 13       (suspected pan, center)
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
u32 frameCount        (verified: WAV data-chunk size / bytes per frame)
u32 0
u32 channelCount      (1 and 2 verified)
u32 bytesPerFrame     (4 = stereo 16 bit in the 0x26 specimen - but NOT reliable, see below)
f32 sampleRate        (44100.0 — the rate is stored as a float!)
u8  zero[16]
u32 32                (unknown, kept verbatim)
```

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

## Monolithic files (structural inference, no specimen)

No monolithic specimen was available, but two structural facts are known: the
[dwsanitizer](https://github.com/kachine/dwsanitizer) project (which rewrites path strings
inside monolithic files) hardcodes the offsets 0x5E for the program name length and 0x66
for the program name — exactly the length field and payload of the first block of the
stream starting at 0x5A — and scans monolithic files for the byte pattern
`F6 01 00 00 [len] 00 00 00 00 00 00 00`, which is precisely the envelope of a 0x01F6
sample path block. Monolithic files therefore keep the same preamble and block structure,
and the embedded audio has to live in additional blocks.

The detector exploits that the audio format block describes the audio: when the external
sample file of a container cannot be found, any block with an unknown tag whose payload
size is exactly `frameCount * channelCount * bytesPerSample` for a bytes-per-sample of 2,
3 or 4 is taken as the embedded audio (a check that cannot match by accident; the
unreliable bytes-per-frame field is not used). 2 or 3 bytes per sample are integer PCM; 4
bytes per sample are interpreted as the 32-bit float format which the DirectWave sampling
dialog offers (16 or 32-bit float) and converted to 24 bit. This path is verified against
synthesized monolithic files only — a real monolithic file has not been available yet. If
no block matches, the detector reports that the samples were not found.

## Not yet decoded (single specimen limits)

* Loop points, envelopes, filters, effects: all candidate blocks are zero/default in the
  specimen (it is a plain unlooped auto-sampling export). Loops still travel in the
  standard WAV `smpl` chunks of the sample files themselves, which DirectWave reads.
* Trigger groups (round-robin/random cycles) and their location in the opaque bytes.
* The tag and placement of the embedded audio block of monolithic files (see above) and
  the structure of .dwb banks — no specimens. The file-name fall-back of the detector
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
