# Kurzweil PC3 Series / Forte / PC4 / K2700 Object File Format (.PC3 / .P3K / .K2P / .P3A / .PLE / .FOR / .FSE / .PC4 / .P4S / .K27)

Object files of the Kurzweil PC3 series (PC3, PC3K, PC3A, PC3LE), the Forte and Forte SE, the PC4
and the K2700. All these devices share one container format, the successor of the
K2000/K2500/K2600 format (see `KURZWEIL_FORMAT.md`); the extension only signals the device which
wrote the file. Every device of the family loads the files of the others and converts the objects
on the fly. Only the PC3K, the Forte family, the PC4 and the K2700 have user sample memory; the
files of the other devices reference the ROM samples of the device only.

The layout of the container and of the objects follows the open-source object viewer *Kurzweil
Object View* by Brian Cowell (MIT license, https://github.com/cunka/Kurzweil-Object-View) and was
verified on the free sound packs which Kurzweil publishes for the PC3, PC3K, Forte and Forte SE
(e.g. `ACCORDN1.PC3`, `REAL32DW.P3K` of the *Take 6* sample library, `KCOMP100.K2P` - the K2661
ROM compatibility samples for the PC3K -, the 25 Forte *Legacy* / *Patch Kreator* packs, their
Forte SE variants and the K2700 packs including `NylonGuitar.K27` of the *Building Keymaps*
tutorial, which holds samples in the layout of the Forte generation). All multi-byte values are **big-endian**.
Sample positions are counted in **bytes** (the K2x00 family counts 16-bit words).

## File layout

```
offset size
0      4    magic "COOL"
4      4    0
8      4    uint32 objectRegionSize - file offset where the raw sample data region starts
                   (= 36 byte header + objects + 4 byte terminator); equals the file size
                   when the file holds no samples
12     20   0
32     4    int32  -(objectRegionSize - 36): the negative size of the object list
36     ...  objects up to 'objectRegionSize' - 4
            uint32 0 - terminates the object list
objectRegionSize  raw sample data region: 16-bit big-endian PCM, referenced by the sample
                  headers via absolute byte offsets
```

### Object header

```
uint32 type        object type (see below)
uint32 id          object ID; the user area of the devices is 1024..4095, lower IDs are ROM
uint32 0
uint32 size        total object size in bytes counted from 'type', a multiple of 4
uint16 0
uint16 nameOfs     length of the name field + 2
name               ASCII, 0-terminated, zero padded to a multiple of 4 bytes (nameOfs - 2
                   bytes; the names hold up to 16 characters)
data               size - 20 - (nameOfs - 2) bytes, zero padded to a multiple of 4
```

The next object starts at `objectOffset + size`.

Object types: 0x64 master table, 0x67 intonation table, 0x70 song, **0x85 keymap**, 0x88 quick
access bank, **0x8A program**, 0x95 algorithm, 0x9B setup (PC3) / multi (Forte), 0x9C effect
chain, **0x9E sample**, 0xA3 shift pattern, 0xA4 velocity pattern, 0xA7 duration pattern, 0xA8
arpeggiator template, 0xA9 beats pattern, **0xAA sample of the Forte generation** (Forte, PC4,
K2700), 0xAB velocity per key (Forte). Only keymaps, programs and the two sample types are
interpreted.

## Sample object (type 0x9E)

```
int16  baseID       (1)
int16  numHeaders   number of sample headers - 1
int16  headersOfs   offset from this field to the first header (always 8)
uint8  flags        1 = stereo: the headers form left/right pairs (even index = left)
uint8  unused1
int16  copyID
int16  unused2
numHeaders+1 x sample header (40 bytes each):
    uint8  rootKey        MIDI note of the recorded pitch
    uint8  flags          0x80 = loop OFF (inverted!), 0x40 = sample data present in this
                          file (otherwise the header references device ROM), 0x10 = RAM
                          sample; the PC3K writes 0x70 for imported samples
    int8   volumeAdjust   volume adjust in 0.5 dB steps (-64.0 to +63.5 dB)
    int8   altVolumeAdjust volume adjust used when the alternative start is active
    int16  maxPitch       highest playable pitch in cents (see below)
    int16  offsetToName   0
    int32  sampleStart    absolute byte offset into the sample data region
    int32  altSampleStart alternative start (used when the Alt switch is on)
    int32  loopStart      absolute byte offset; the loop always ends at sampleEnd
    int32  sampleEnd      absolute byte offset of the last frame (inclusive):
                          frames = (sampleEnd - sampleStart) / 2 + 1
    int16  offsetToEnvelope     byte offset from this field to the natural envelope
    int16  altOffsetToEnvelope  byte offset from this field to the alternative envelope
    int32  samplePeriod   sample period in nanoseconds (32000 Hz -> 31250)
    8 bytes               00 00 03 E0 00 00 00 00 in all samples written by a PC3K
natural envelope records, 12 bytes each (6 x int16); the PC3K writes two records
{-1, 1, 0, 0, -1600, 0} per sample object and points both envelope offsets of every header at
the first one (16 and 14 for a single header)
```

The first 32 bytes of a header are the K2x00 sample header, with byte instead of word
positions. The sample data of consecutive samples follows without padding: the next sample starts
at `sampleEnd + 2` of the previous one; the last `sampleEnd + 2` is the file size.

`maxPitch` is the pitch which the sample reaches at the maximum playback rate of the devices
(96 kHz): `floor(1200 * log2(96000 * samplePeriod / 1e9)) + 100 * rootKey - 1200` (the K2x00
family rounds up instead; the 8 untuned WAV imports of the K2700 tutorial and the 258 K2661
samples of `KCOMP100.K2P` truncate). The *Pitch Adjust* of the sample editor shifts this value
by the same number of cents, so the pitch adjust of a sample is `maxPitch` minus that formula
(the 258 samples of *Take 6* give values between -48 and +58 cents).

Non-looped samples are written with the loop flag ON and `loopStart == sampleEnd` (a
degenerate one-frame loop), like KurzFiler does for the K2x00 family: 147 of the 258 *Take 6*
samples are stored this way. Readers treat `loopStart >= sampleEnd` as "no loop".

## Sample object of the Forte generation (type 0xAA)

The Forte, PC4 and K2700 store their user samples in an object of their own type. It is the
PC3K sample object with a longer preamble and 64-bit positions:

```
uint32 baseID       (1)
uint16 numHeaders   number of sample headers (not minus 1)
uint16 flags        1 = stereo (assumed as for the PC3K object)
uint16 headersOfs   offset from this field to the first header (8)
6 bytes             0
numHeaders x sample header (56 bytes each): the fields of the PC3K header with the four
                    positions (sampleStart, altSampleStart, loopStart, sampleEnd) as int64
                    byte offsets; the envelope offsets are relative to their fields
                    (16 and 26 for a single header)
natural envelope records, 2 x 12 bytes
```

The K2700 tutorial file holds 8 mono samples of one header each (96 byte objects) whose data
follows contiguously in the sample data region like in a PC3K file. The devices import PC3K
files, so the creator writes the PC3K object (type 0x9E).

## Keymap object (type 0x85)

Identical to the K2x00 keymap object: sample ID, method bits (0x10 tuning as int16 / 0x08 as
int8, 0x04 volume adjust, 0x02 sample ID, 0x01 sub-sample number per entry), base pitch, cents
per entry, entries per velocity level - 1, entry size, the 8 level offsets and the entry tables.
Entry `i` responds to MIDI note `12 + (basePitch + i * centsPerEntry) / 100`. The volume adjust
of an entry is the *Volume Adjust* of the keymap editor (± 24 dB); it is read as dB.

## Program object (type 0x8A)

The object data consists of a fixed 229 byte header, one fixed 318 byte record per layer and a
trailing block.

### Header (229 bytes)

```
offset size
0      5    "PC3" + version major, minor of the program: 4.0 = PC3, 4.5 = PC3K, 4.7 = Forte
5      5    "PC3" + 3.3 (the version of the following block)
10     26   uint16, uint16, uint16, uint8 0x13 (19), 19 byte keyword string ("keyword1,
            keyword2" or zeros)
36     2    3.5 (the version of the program block)
38     27   program block: tag 0x08, uint8 5 (the mode), uint8 numLayers, uint8 mode flags
            (bit 1 = KB3 organ program), bend range 0x37, portamento 0x40, ...
65     20   the global ASR2 (6), FUN2 (4), LFO2 (6) and FUN4 (4) segments
85     36   3 effect blocks of 12 bytes (insert, aux 1, aux 2): 3.3 version, tag 0x51/0x52/
            0x53, flags, uint16 effect chain ID (0 = none), send levels, ...
121    16   "ProgramRFU012567"
137    92   the KB3 organ block
```

### Layer record (318 bytes)

The record is a fixed sequence of the segments known from the K2x00 programs, each opened by
its tag byte (the tag bytes are 0 in some programs which the devices converted from older
formats, so the positions and not the tags identify the segments):

```
offset size tag  segment
0      18   0x09 layer: [1] enable low, [2] low key, [3] high key, [4] velocity window,
                 [5] enable control source (0 = OFF: the layer never plays, 0x7F = ON),
                 [6] flags, [7] more flags: 0x04 always set, 0x20 = stereo (keymap 1 plays
                 on the left, keymap 2 on the right; the same keymap on both sides plays
                 the left/right headers of stereo samples), [8] trigger, [9] enable high,
                 [10..12] delay control/min/max, [13] cross-fade, [14..15] version 3.5,
                 [16..17] category
18     3    0x6A pan/exclusion map (PC88 map)
21     6    0x10 ASR1, 27 6 0x11 ASR2
33     4    0x18 FUN1, 37 4 0x19 FUN2
41     6    0x14 LFO1, 47 6 0x15 LFO2
53     4    0x1A FUN3, 57 4 0x1B FUN4
61     15   0x20 envelope control: [1] flags: bit 0 = the 'natural' envelope of the samples
                 is active (the AMPENV values are ignored), then the attack (adjust, key
                 tracking, velocity tracking, source, depth), decay and release (adjust, key
                 tracking, source, depth) controls
76     6    0x27 impact ('punch')
82     16   0x21 AMPENV, 98 16 0x22 ENV2, 114 16 0x23 ENV3 (see below)
130    32   0x40 keymap/pitch block: [1] 0x7F, [2] transpose (semitones), [3] detune,
                 [4] 0x2B, [8..9] keymap 2 (the right side of a stereo layer), [11] flags
                 (0x40 on RAM sample layers), [12..13] keymap, [22] pitch control source,
                 [23] its depth, [30] DSP algorithm number
162    5x22 0x50..0x54 the five DSP function pages (see below)
272    12   layer effect: 3.3 version, tag 0x51, flags, uint16 effect chain, ...
284    34   int16 screen transpose, int16 timbre shift, uint16 20 (string length), 20 byte
            layer name ("LayerNameHere0123456" or spaces), "LayerRFU"
```

The velocity window byte packs the low and high velocity as two 0..7 dynamic marks (ppp..fff,
mark j covers velocities j*16..j*16+15): the low mark in bits 3-5 and the high mark *inverted*
(7 - mark) in bits 0-2, so a full range is 0 (as on the K2x00).

#### Envelopes

An envelope segment holds the tag, a flags byte (the envelope loop) and 7 stages as (level,
time) byte pairs - **the level first** (the K2x00 stores time, level): 3 attack stages, 1 decay
stage and 3 release stages. The level is a signed percentage (the decay level is the sustain
level). The time byte indexes the time list of the devices: the codes 0, 1, 2 and 3 are 0,
0.002, 0.005 and 0.01 seconds, from code 4 = 0.02 s on the list follows the time grid of the
K2x00 envelopes (0.02 s steps up to 2 s, 0.04 s up to 5 s, 0.1 s up to 10 s, 0.5 s up to 15
s, 1 s up to 25 s, 5 s up to 60 s; code 255 = 60 s). Like on the K2x00 a stage with a zero
time and a zero level is unused and keeps the previous level; the release stages of most
factory programs are all zero when the program uses the natural envelope. In user envelopes
the sustain is written explicitly into the decay stage (e.g. level 100, time 0).

#### DSP function pages

Each of the five 22 byte records holds the parameters of one DSP block of the layer's algorithm:

```
[0] tag 0x50..0x54, [1] function, [2] coarse ('Adjust'), [3] fine, [4] key tracking,
[5] velocity tracking, [6] source 1, [7] depth of source 1, [8] depth control, [9] minimum
depth, [10] maximum depth, [11] source 2, [12..13] block type, [14..15] output, [16..17] gain,
[18..19] pan mode, [20] pan 1, [21] pan 2
```

The first 11 bytes match the K2x00 function pages. The records are positional (the tag bytes
are cosmetic; the PC3K writes them in the order 0x50, 0x51, 0x53, 0x52, 0x54): the third
record is always the amplifier (function 1 or 0/41 on the PC3K, output 0x2000; [2] = the level
adjust in dB, [5] = the velocity tracking in dB, 35 on a new layer of the Forte and K2700), the
first two records hold the blocks F1 and F2 of the algorithm and the last two the blocks F3 and
F4. A 2-block function occupies two records: the second record holds its second parameter - the
resonance of the 2-pole low-pass in 0.5 dB steps (-12 to 24 dB), the width of the 2-pole
bandpass - with a modulation of its own, and its function byte is 60 (the 'none' of a second
block) or the leftover of a previously assigned function, so only the function byte of the
first record of a block identifies the function. Algorithm 1 is [2-block F1/F2] [1-block F3]
[1-block F4] and algorithm 5 has no DSP blocks. The block type bytes [12..13] hold 0x0200 on
the first record of a 2-block function, 0x0300 on 1-block records and 0 on the second record
of a 2-block function.

The filter functions of the K2x00 family keep their numbers: 2 = 2-pole low-pass, 3 = 2-pole
bandpass, 15 = 1-pole low-pass (a 1-block function without a resonance), 50 = 4-pole low-pass,
54 = 4-pole high-pass, 55 = twin peaks bandpass, 56 = double notch (0 = none, 61 = none in a
2-block slot). For these the coarse value is the cutoff in semitones (440 Hz * 2^((coarse - 9)
/ 12), -48 = 16 Hz to 79 = 25088 Hz). Source 1 and its depth on the filter record route the
filter envelope (source 121 = ENV2) or the attack velocity (source 100) to the cutoff; the
depth law is taken over from the K2x00. The PC3 family adds many DSP functions with higher
numbers, which are not interpreted; the 4-pole functions of the K2x00 do not occur in the
factory programs of the PC3 family.

### Trailing block

The 64 bytes after the layers of a PC3K program hold the version 1.2 controller information
(empty), the virtual parameter table and the arpeggiator and drum pad blocks of the program; the
Forte stores its controller names here (variable length). The block is copied from a PC3K
program when writing.

## Writing

The creator writes a PC3K file (.p3k, program version 4.5), which the PC3K, Forte, Forte SE, PC4
and K2700 load. Each group of a multi-sample becomes a layer with its own keymap (32 layers at
most). The header, the layer record and the trailing block of a program are copies of a two
layer program of the *Take 6* library which plays RAM samples through algorithm 5 without DSP
functions; only the number of layers, the key and velocity window, the enable source (ON), the
flags (0x04, plus 0x20 for stereo), the envelope control (user envelope) with the AMPENV stages,
the keymap IDs and the velocity tracking of the amplifier record are set. A layer with a filter
switches to algorithm 1: the 2-pole low-pass or bandpass goes into the records F1/F2 with the
resonance or width on the second record, the 1-pole low-pass into the record F3, the other
records are set to 'none'; the filter record carries the filter envelope (ENV2, written into
the ENV2 segment) or the attack velocity as source 1 with the depth. Samples are written with
the flags 0x70, the natural envelope records above and consecutive byte offsets. Written files
are not yet verified on hardware.

## Not interpreted / unknown

* The sample object of the Forte generation was verified on one K2700 file with mono samples of
  one header each; multi-header and stereo objects of this type are assumed to follow the PC3K
  object.
* The layer level and the modulations of the amplifier record (other than its velocity
  tracking), the layer effects, the LFOs, ASRs, FUNs and the pitch envelope are not converted.
* The DSP function numbers which the PC3 family added (e.g. the 4-pole 'Mogue' low-pass) are
  not known; layers which use them are read without a filter. The function 67, which the PC3
  programs use in the place of a filter, is probably the 2-pole high-pass but is not verified.
* The modulation of the cutoff by source 2 with its depth control is not read, only source 1.
