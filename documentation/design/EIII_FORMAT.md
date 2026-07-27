# E-mu EIII Bank Format (.E3B / .E3X / .ESI)

Bank files of the E-mu Emulator III sampler family. One bank holds the presets *and* the sample
data of a sound, which is why a bank file is the whole instrument and not just a reference to
external samples.

The structures were derived from the source code of the GPL tool *emu3bm* by David García Goñi
(https://github.com/dagargo/emu3bm), which reads and writes these banks, and were verified against
the E-mu EIIIX and ESI-4000 library CD-ROMs (22 banks, 3424 presets, 25596 zones, 8073 samples).
All multi-byte values are **little-endian**. Sample positions are byte offsets, not frame indices.

## Bank variants

The first 16 bytes of a bank are an identifier: 15 characters followed by a terminating zero byte.
All three variants use the same preset, zone and sample structures and only differ in the position
and the size of the two address tables which locate them.

| Identifier        | Devices                  | DOS extension | Preset table | Sample table | Preset area | Presets | Samples |
|-------------------|--------------------------|---------------|--------------|--------------|-------------|---------|---------|
| `EMULATOR THREE ` | Emulator III             | `.e3b`        | 0x006C       | 0x0204       | 0x074A      | 100     | 99      |
| `EMULATOR 3X   `  | Emulator IIIX, ESI-32    | `.e3x`        | 0x17CA       | 0x1BD2       | 0x2B72      | 256     | 999     |
| `EMU SI-32 v3  `  | ESI-32 / ESI-2000 / ESI-4000 | `.esi`    | 0x17CA       | 0x1BD2       | 0x2B72      | 256     | 999     |

The DOS extensions are the ones the E-mu tools use when a bank is written to a FAT disk instead of
an E-mu formatted one; on an E-mu disk the files carry no extension at all.

`EMULATOR THREE` additionally biases every entry of its preset address table by `0x1A6FE`; the
later variants use no bias. The `EMULATOR 3X` banks still carry a (empty) `EMULATOR THREE` preset
address table at 0x6C.

## Bank header

```
offset size
0      16   char   identifier (see above)
16     16   char   bank name, padded with spaces
32     4    uint32 number of objects - unreliable, empty banks hold 1 or 25
36     12   3 x uint32, always 1
48     4    uint32 position behind the last preset
52     4    uint32 position behind the last sample, relative to the sample area
56     4    uint32 unknown, always 0x00800000
60     4    uint32 number of 512 byte blocks which the presets occupy
64     4    uint32 number of 512 byte blocks which the samples occupy
68     4    uint32 unknown, always 0
72     4    uint32 number of 512 byte blocks of the whole bank
76     16   char   second copy of the bank name
92     4    uint32 index of the preset which is selected when the bank is loaded
96     12   3 x uint32 parameters
```

The block counts split the bank at the filler byte (see below): `presetBlocks + sampleBlocks ==
totalBlocks` holds in every bank of the library CD-ROMs.

## Address tables

Both tables hold one entry per slot plus one terminating entry.

**Preset table** - entry *i* is the offset of preset *i* relative to the preset area:

```
presetOffset(i) = presetArea + table[i] - bias
```

A slot whose entry equals the entry of its successor is **empty**, which is what deleting a preset
leaves behind. This is a per-slot test and not a terminator: the library CD-ROMs contain banks
(e.g. `Phatt Presets  X`) whose slot 0 is empty while 256 presets follow it, so the whole table has
to be walked. The terminating entry is the total size of the preset area.

**Sample table** - entry *i* is the address of sample *i+1* (sample numbers are 1-based) relative
to the sample area, biased by `0x400000`:

```
sampleAddress(i) = sampleArea + table[i] - 0x400000
sampleArea       = presetArea + 1 + presetTable[maxPresets] - bias
```

The single byte at `presetArea + presetTableSize` between the presets and the samples is a filler;
its value is 0x74 in EIIIX and 0xEE in ESI banks and does not appear to matter.

An entry of 0 marks an **empty slot**, again what deleting a sample leaves behind. As with the
presets this is not a terminator: 6 of the 24 examined banks have such holes and every one of the
2593 entries behind them holds a valid sample which the presets still reference. The terminating
entry points behind the last sample and therefore equals the file size.

## Preset

```
offset size
0      16   char   preset name, padded with spaces
16     12   int8   real-time controller assignments (10 controllers + 2 footswitches)
28     16   int8   unknown
44     1    int8   pitch bend range in semitones
45     1    uint8  lowest velocity of the primary layer
46     1    uint8  highest velocity of the primary layer
47     1    uint8  lowest velocity of the secondary layer
48     1    uint8  highest velocity of the secondary layer
49     1    uint8  1-based number of the preset which is layered on top of this one, 0 = none
50     1    uint8  unknown parameter
51     2    int8   unknown
53     1    uint8  number of note zones
54     88   uint8  one entry per key: the index of its note zone, 0xFF = unmapped
```

The preset is 142 bytes and is directly followed by its note zones and then by its zones. Key 0 is
MIDI note 21 (which the samplers display as A-1), key 87 is MIDI note 108.

A **velocity range** of 0 for the high value means that the layer is not restricted.

The **link** builds chains of presets which play together; combined with the per-layer velocity
ranges this is how the samplers stack more than the two layers a single preset provides. emu3bm
reads offsets 49-50 as one uint16, but the byte at 50 is a parameter of its own: about 1% of the
library presets set it together with a link (e.g. `Stick Combo III` of the `Chapman Stick` bank,
link 51 and parameter 61), which makes the 16 bit value look out of range and would lose the
layered preset.

### Note zone (4 bytes)

```
0  uint8  options, low byte  - crossfade/switch settings
1  uint8  options, high byte
2  uint8  index of the zone of the primary layer, 0xFF = none
3  uint8  index of the zone of the secondary layer, 0xFF = none
```

The key range of a note zone is not stored: it is given by the keys of the preset key map which
point at it. The zone array is only as long as the highest index any note zone references; slots
which no note zone references may hold stale data and must not be read.

### Zone (48 bytes)

```
0   uint8  original key (0..87), the key at which the sample plays at its recorded pitch
1   uint16 1-based sample number; the ESI samplers use bits 14 and 15 as flags (see below)
3   int8   unknown - the EIIIX writes 0x1F here, the ESI samplers 0x00
4   5      amplifier envelope: attack, hold, decay, sustain, release
9   uint8  LFO rate
10  uint8  LFO delay
11  uint8  LFO variation
12  uint8  filter cutoff
13  uint8  filter Q; bit 7 enables its real-time control (set by the ESI samplers)
14  int8   filter envelope amount
15  5      filter envelope
20  5      auxiliary envelope
25  int8   auxiliary envelope amount
26  uint8  auxiliary envelope destination: 0 off, 1 pitch, 2 pan, 3 LFO rate,
           4 LFO->pitch, 5 LFO->VCA, 6 LFO->VCF, 7 LFO->pan
27  int8   velocity to auxiliary envelope
28  int8   velocity to amplifier level
29  int8   velocity to amplifier attack
30  int8   velocity to pitch
31  int8   velocity to pan
32  int8   velocity to filter cutoff
33  int8   velocity to filter Q
34  int8   velocity to filter attack
35  int8   velocity to sample start
36  int8   LFO to pitch
37  int8   LFO to amplifier
38  int8   LFO to cutoff
39  int8   LFO to pan
40  int8   amplifier level (0..127)
41  int8   tuning, -64..64 for -100..100 cents
42  int8   filter key tracking, -127..127 for -2.0..2.0
43  uint8  note-on delay, 0x00..0xFF for 0.00..1.53 s
44  uint8  panorama, 0 fully left, 0x40 centered, 0x7F fully right
45  uint8  filter type (upper 5 bits) and LFO shape (lower 2 bits)
46  uint8  flags which enable the real-time controls, normally 0xFF
47  uint8  flags: 0x02 non-transpose, 0x04 envelope trigger mode, 0x08 chorus,
           0x10 solo, 0x20 disable loop, 0x40 disable left, 0x80 disable right
```

**Sample number flags.** The ESI banks set bit 14 or bit 15 of the sample number. The library
CD-ROM carries the same bank in both the EIIIX and the ESI-4000 variant (`Orbit Presets  X` and
`Orbit Presets 4k`); comparing them zone by zone shows 1279 zones whose sample number differs by
exactly 0x4000 (1192x) or 0x8000 (87x) while every other parameter is identical. The index is
therefore the low 14 bits; the meaning of the two flags is unknown.

**Filter type.** Only the ESI samplers store a filter type. Their 19 types are, in the order of the
upper 5 bits: 2/4/6 pole low-pass, 2nd/4th order high-pass, 2nd/4th order band-pass, contrary
band-pass, swept EQ 1 oct / 2->1 / 3->1, phaser 1, phaser 2, bat-phaser, flanger lite, vocal
Ah-Ay-Ee, vocal Oo-Ah, bottom feeder and the ESi/E3x low-pass (which is their most used type). The
EIII and the EIIIX have a single low-pass filter instead: scanning the 22 EIIIX banks shows that
their zones only ever hold 0x00, 0x40, 0x80 or 0xC0 in this byte, i.e. the three bits which select
one of the ESI types are never set.

**Conversion tables.** The envelope stages, the cutoff frequency and the panorama are table
indices, not linear values; the tables are in `Emulator3Constants`. Envelope times run from 0 to
163.69 s, cutoff from 26 Hz to 74040 Hz.

## Sample

```
offset size
0    16   char   sample name, padded with spaces
16   4    uint32 unknown
20   4    uint32 position of the first frame of the left channel (always 92)
24   4    uint32 position of the first frame of the right channel, 0 if mono
28   4    uint32 position of the last frame of the left channel
32   4    uint32 position of the last frame of the right channel, 0 if mono
36   4    uint32 loop start of the left channel
40   4    uint32 loop start of the right channel
44   4    uint32 loop end of the left channel
48   4    uint32 loop end of the right channel
52   4    uint32 sample rate in Hz
56   2    uint16 encoded playback rate, 0 for 44100 Hz
58   2    uint16 options
60   4    uint32 position of the left channel in the sample memory
64   4    uint32 position of the right channel in the sample memory
68   24   6 x uint32 parameters
92   ...  16 bit PCM data
```

All positions are byte offsets **relative to the start of this 92 byte header**, so the number of
frames is `(endLeft + 2 - 92) / 2` and a loop position in frames is `(loopStart - 92) / 2`.

The two channels of a stereo sample are stored one after the other, not interleaved: the left
channel occupies `92 .. 92 + frames * 2`, the right one follows it.

Option flags:

```
0x0001  looped
0x0008  the loop continues to play during the release phase; without this flag the
        loop stops as soon as the key is released
0x0020  the sample has a left channel
0x0040  the sample has a right channel
```

A sample which only carries its *right* channel (0x40 without 0x20) exists in the libraries; its
positions have to be read from the right hand set of fields. Bit 0x10 is set in almost every sample
of the libraries and its meaning is unknown.

Sample rates are arbitrary, not a fixed set - the libraries contain rates such as 7000, 12000,
15625, 27777 and 31396 Hz. The samplers always play back at 44.1 kHz and compensate with the
encoded playback rate `0xF800 | (int) (-9799 + 1108 * ln(rate))`.

## Floppy disk sets

The EIIIX and ESI samplers also save a bank onto one or more 1.44 MB floppy disks (raw images of
1474560 bytes). The layout below was derived from the EIIIX and ESI sets of the E-mu and
Sweetwater floppy libraries (10 sets, 789 presets, 806 samples) and verified by comparing shared
material with the library CD-ROMs. Sets of the original Emulator III were not available; their
layout is unknown.

Every disk starts with a 512 byte disk header whose values are **big-endian** - like everything
else on the floppies, which are a dump of the memory of the 68k CPU of the samplers:

```
offset size
0      16   char   bank identifier as in a bank file
16     16   char   bank name
36     4    uint32 always 1
40     4    uint32 1-based number of this disk
44     4    uint32 number of disks of the set
72     4    uint32 number of 512 byte content blocks of the whole set
```

The header holds further sizes and block counts (with the EIIIX and the ESI ordering them
differently) plus some per-disk fields, but none of them are needed - everything follows from the
payload itself. A bank file carries little-endian 1s at offsets 36-47, so sane big-endian disk
numbers tell a floppy disk from a bank file of the same size. The disks of a set share the
identifier and the bank name of their headers; the file names cannot be used to collect a set
since the collections in the wild number their disks in different ways (`Orchestral #002.img`,
`sw_classic_2.img`).

The payload - bytes 0x200 up to the end of every disk, concatenated in disk order - consists of
three regions, each padded to full 512 byte blocks. The EIIIX orders them record, sample data,
sample header table; the ESI samplers order them record, sample header table, sample data.

**The bank record** holds the bank name (16 bytes), 16 bytes of fields and then exactly what a
bank file stores from offset 0x6C on: bank file offset `B` maps to payload offset `B - 0x4C`. The
two address tables are big-endian and hold memory addresses instead of file offsets; subtracting
the first entry of the preset table (the address of the preset area) turns the preset entries into
the offsets of a bank file, including the empty-slot and terminator semantics. The region ends
behind the filler byte, i.e. its size is `presetAreaOffset - 0x4C + presetTableTerminator -
firstEntry + 1`.

**The sample header table** is 92000 bytes (180 blocks): 1000 slots of 92 bytes, one per sample
number, slot 0 unused. The slots use the field layout of a bank file sample header with these
differences:

* All values are big-endian and the positions are byte offsets relative to the start of the
  channel data: the start fields are 0, the end fields hold the size of the channel in bytes and
  the loop end points *behind* the last frame of the loop, where a bank file stores the frame
  before the last one (verified by sweeping the offset over the loop seams of all looped samples:
  the file parser's +1 yields the clean seam without any further correction).
* The option flags differ from a bank file: `0x02` = has a left channel, `0x04` = has a right
  channel, `0x08` = loop in release, `0x40` = looped. The position and data fields of a channel
  which is not flagged hold stale values of deleted allocations and must not be read - which is
  also why the table can hold more plausible-looking headers than the sample table has entries.
* The data offset fields at 0x3C/0x40 hold the memory address of the channel's data. Bit 26
  (0x4000000) is a memory bank flag and has to be masked off; the masked address is the position
  in the sample data region. The masked sample table terminator is the size of that region.

**The sample data region** holds one chunk per channel per sample, each preceded by an unused 92
byte slot (the layout of a bank file with the headers blanked out). The 16 bit sample values are
big-endian.

The presets of the record are byte-identical with a bank file with one exception: the flag byte of
a zone (offset 47) is stored with its bits mirrored - the flag a bank file keeps in bit 0 sits in
bit 7 and so on. This is obvious from the `Stereo Grand` library, whose zones all carry 0x80:
read as a file flag that would mute the right channel of every zone of a stereo piano, while the
mirrored value is the undocumented bit 0x01 which most zones of the library CD-ROMs set as well.
The bytes around it (filter Q with its real-time bit at 13, filter type at 45, real-time enables
at 46) are stored unmirrored.

## Device requirements when writing

* The first and the last two frames of every channel have to be silent, otherwise the samplers
  report a `Mono Start Zero!!!` error.
* A loop position has to keep a distance of 6 frames from the start and 7 frames from the end of
  the sample, and a loop must be at least 10 frames long.
* The sample memory of the samplers is limited to 128 MB.
