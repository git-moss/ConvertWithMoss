# E-mu Emulator X bank format (EXB / EBL)

This document describes the file format of the E-mu Emulator X software sampler (Emulator X,
Emulator X2 and Emulator X3, 2004-2008). It was reverse-engineered from 14 banks - the E-mu factory
banks *PROcussion*, *SP-1200*, *Vintage Keys*, *Proteus 1*, *Orbit 8080* and *Production Set* as
well as the third party libraries *Halion Synth Kit*, *Saint Thomas Strings* and the six banks of
*Wizoo Electric Pianos*, together 1704 presets, 18670 voices and 2988 samples - and from the
`ebl2wav` tool of the [e-mu-soundbanks](https://github.com/mattetti/e-mu-soundbanks) project, which
decodes the sample files. E-mu never published the layout.

The meaning and the value ranges of the parameters come from the *Emulator X* and *Emulator X3*
reference manuals, which document the user interface but not the file format. Where this document
says that the manual confirms something, the manual states the range or the default and the factory
banks agree with it.

A sound bank consists of

* one **`*.exb`** file which holds all presets of the bank and a list of the samples it uses, and
* a sibling folder named **`SamplePool`** which holds one **`*.ebl`** file per sample.

The sample files are named `<bank name>SL<index>.ebl` with a three digit, 1-based, zero padded
index, e.g. `PROcussionSL018.ebl`. This naming convention is the only link between a bank and its
samples; the bank stores no path. Sample indices may have gaps.

Numbers are **big-endian** unless noted otherwise. Only the numeric fields of the EBL sample header
and the EBL trailer are little-endian. Strings are UTF-16LE in a fixed size field, padded with
zeros; a field of 64 bytes therefore holds up to 32 characters.

## Container

Both file types use the same IFF-like container:

| Offset | Size | Content                                                            |
|--------|------|--------------------------------------------------------------------|
| 0      | 4    | `FORM`                                                             |
| 4      | 4    | Size of everything that follows (file size - 8)                    |
| 8      | 8    | `E5B0TOC2`                                                         |
| 16     | 4    | Size of the table of contents                                      |
| 20     | *n*  | Table of contents, 78 bytes per entry                              |
| ...    |      | The chunks the table of contents points to                         |

A table of contents entry is:

| Offset | Size | Content                                                            |
|--------|------|--------------------------------------------------------------------|
| 0      | 4    | Chunk tag, one of `E5P1` (preset), `E5SL` (sample link), `E5S1` (sample) |
| 4      | 4    | Size of the chunk payload                                          |
| 8      | 4    | Offset of the chunk from the start of the file                     |
| 12     | 2    | Index of the chunk (0-based for presets, 1-based for samples)      |
| 14     | 64   | Name                                                               |

## Format generations

The chunks are versioned and grew over the lifetime of the product, so a reader must take their
size from the chunk header instead of assuming it. The observed generations are:

| Chunk                | Old                         | New                                   |
|----------------------|-----------------------------|---------------------------------------|
| `Phdr` preset header | version 1, 142 bytes        | version 2, 148 bytes; version 3, 154  |
| `Zhdr` zone header   | version 1, 16 bytes         | version 2, 28 bytes                   |
| `E5Vs` voice settings| 12 bytes                    | 14 bytes                              |
| `E5Oc` oscillator    | version 2, 40 bytes         | version 3, 50 bytes                   |
| `LIST FuGL`          | absent                      | 1279 bytes, three function generators |
| Sample header        | version 1, audio at 184     | version 2, audio at 188, `EXLZ` trailer |

Fields were appended rather than inserted: the chorus float at offset 27 of `E5Oc` sits at the same
place in both the 40 and the 50 byte version, and the sample index and original key of `Zhdr` are at
the same offsets in both of its versions.

## Chunks

A chunk the table of contents points to starts with its tag, a big-endian size, and a 2 byte index
which repeats the index of the table of contents entry. The size covers the index and the payload,
so a chunk occupies `payload size + 10` bytes and the entries follow each other without padding:

```
tag(4) size(4) index(2) payload(size - 2)
```

Nested chunks inside a payload have no index; they are plain `tag(4) size(4) payload(size)`. A
`LIST` chunk holds a 4 byte list type followed by its child chunks, as in RIFF.

An `*.exb` file lists all presets first and then all samples. After the last chunk the factory banks
contain slack space filled with `0x01` bytes, which is included in the `FORM` size. The slack is not
required.

## Sample link chunk (`E5SL`)

The payload is a single big-endian 32-bit number, the 1-based index of the sample. It is always
identical to the chunk index and to the number in the file name of the `*.ebl` file. The name of the
table of contents entry is the sample name and matches the name stored inside the `*.ebl` file.

## Sample file (`*.ebl`)

An `*.ebl` file is a container with a table of contents of exactly one `E5S1` entry. The payload of
that chunk starts at offset 108 of the file. All offsets below are relative to the start of that
payload; `108 + offset` is the position in the file.

| Offset | Size | Endian | Content                                                        |
|--------|------|--------|----------------------------------------------------------------|
| 0      | 2    |        | Zero                                                           |
| 2      | 2    | LE     | Header version, 1 or 2                                         |
| 4      | 64   |        | Name                                                           |
| 68     | 4    | BE     | Always 301, a format marker                                    |
| 72     | 4    | LE     | Start of the left channel data                                 |
| 76     | 4    | LE     | Start of the right channel data                                |
| 80     | 4    | LE     | End of the left channel data                                   |
| 84     | 4    | LE     | End of the right channel data                                  |
| 88     | 4    | LE     | Loop start of the left channel                                 |
| 92     | 4    | LE     | Loop start of the right channel                                |
| 96     | 4    | LE     | Loop end of the left channel (the last sample of the loop)     |
| 100    | 4    | LE     | Loop end of the right channel                                  |
| 104    | 4    | LE     | Sample rate in Hz                                              |
| 108    | 2    | LE     | Unknown, 94 or 95 in some banks and 0 in the others            |
| 110    | 2    | LE     | 1 if the sample is looped, otherwise 0                         |
| 112    | 1    |        | Always 1                                                       |
| 113    | 1    |        | Number of channels minus one: 0 for mono, 1 for stereo         |
| 114    | 1    |        | Mask of the used channels: 1 for mono, 3 for stereo            |
| 115    | 1    |        | Always 2                                                       |
| 116    | 64   |        | Comment                                                        |
| 180    | 1    |        | Zero                                                           |
| 181    | 4    | LE     | Offset of the trailer, 0 if there is none (version 2 only)     |
| 185    | 3    |        | Zero (version 2 only, version 1 pads with 3 bytes to offset 184)|

The audio data follows at the offset given by the start of the left channel, which is 184 for
version 1 and 188 for version 2. It is 16-bit signed little-endian PCM, that is, the raw content of
a WAV data chunk.

The two channels are stored one after the other, not interleaved. A sample is mono if the right
channel is empty, which the factory banks express in two ways: version 1 sets the right channel
start behind its end, version 2 gives both channels the same start and end. A stereo sample stores
the right channel behind the left one, starting at the next 4 byte boundary *behind* the end of the
left channel - so the gap between the two is 4 bytes if the left channel already ends on such a
boundary and 2 bytes if it does not. This holds for all 153 stereo samples of the corpus.

Two zero bytes follow the audio data. Version 2 files may then carry a trailer, an `EXLZ` chunk with
a little-endian size which contains further little-endian chunks:

* `INFO`, 8 bytes, meaning unknown, always `1, 1`.
* `MARK`, 8 bytes, the loop start and the loop end as sample frame numbers. The loop start is the
  first frame of the loop, the loop end is the frame behind the loop, so it repeats the offsets of
  the header.

## Preset chunk (`E5P1`)

The payload is a sequence of chunks:

| Tag         | Size | Content                                                           |
|-------------|------|-------------------------------------------------------------------|
| `Phdr`      | 154  | Preset header                                                     |
| `E5IC`      | 20   | Initial controller values, `-1` for 'unset'                       |
| `E5CL`      | 516  | Preset wide modulation cords, unused in the factory banks         |
| `E5MP`      | 12   | MIDI parameters                                                   |
| `EXPs`      | 12   | Preset settings, byte 5 is the preset volume (100)                |
| `LIST AEL ` | 44   | Arpeggiator, two `E5E1` chunks                                    |
| `LIST RmpL` | 48   | Ramp generators, two `ERmp` chunks                                |
| `LIST CrdL` | 292  | Preset modulation cords, 16 `E5Cd` chunks                         |
| `LIST E5VL` | *n*  | The voices, one `E5V1` chunk each                                 |
| `EXEd`      | 92   | Editor state, optional                                            |

`Phdr` is:

| Offset | Size | Content                                                            |
|--------|------|--------------------------------------------------------------------|
| 0      | 4    | Version, 3                                                         |
| 4      | 64   | Preset name                                                        |
| 68     | 64   | Second name field, always empty                                    |
| 132    | 4    | Number of voices                                                   |
| 136    | 8    | Zero                                                               |
| 144    | 4    | `0xFFFFFFFF`                                                       |
| 148    | 6    | Zero                                                               |

## Voice chunk (`E5V1`)

A voice is one layer of a preset. It has a key range, a velocity range and a full set of synthesis
parameters, and it references its samples through a zone list. All factory presets use exactly one
zone per voice, so a voice is effectively one sample zone; a keyboard map is built from many voices.

| Tag         | Size | Content                                                           |
|-------------|------|-------------------------------------------------------------------|
| `Vhdr`      | 16   | Voice header                                                      |
| `E5Vs`      | 14   | Voice settings, byte 5 and byte 13 are 100                        |
| `E5MP`      | 12   | MIDI parameters                                                   |
| `LIST TWL ` | 52   | Three `ETW ` windows: key, velocity and real-time                 |
| `LIST CCWL` | 94   | Five `ECCw` continuous controller windows                         |
| `E5Oc`      | 50   | Oscillator                                                        |
| `E5Am`      | 12   | Amplifier                                                         |
| `E5Fl`      | 62   | Filter                                                            |
| `LIST EvL ` | 220  | Three `E5Ev` envelopes: amplitude, filter and auxiliary           |
| `LIST LFOL` | 64   | Two `E5LF` LFOs                                                   |
| `LIST FuGL` | 1279 | Three `EFGn` function generators                                  |
| `LIST CrdL` | 652  | 36 `E5Cd` modulation cords                                        |
| `LIST E5ZL` | *n*  | The zones                                                         |

A window chunk `ETW ` is `version(4) low(1) lowFade(1) highFade(1) high(1)`. The first window of the
`TWL ` list is the key range, the second the velocity range and the third the real-time range. The
fade values are the crossfade widths, which the manual confirms are given in number of keys
respectively velocity steps.

The `CrdL` list holds 36 modulation cords, which matches the 'Cord 1-36 Amount' modulation
destination of the manual. A cord is `version(4) source(1) destination(1) amount(float, 4)` with the
amount in percent. The numeric codes of the sources and destinations are not documented; the cord
which routes velocity (source 0x0C) to the amplifier volume (destination 0x40) is the first cord of
every factory voice and carries the velocity sensitivity.

`E5Oc` carries the tuning: byte 14 is the transpose and byte 15 the coarse tuning, both signed and
in semitones, and the float at offset 16 is the fine tuning in cents. The manual gives the ranges as
-36..+36 semitones, -72..+24 semitones and ±100 cents, which the factory banks stay inside; the fine
tuning is always a multiple of 100/64 cents because the E-mu tunes in 1/64 semitone steps. Transpose
shifts the keyboard position of the voice while coarse tuning stretches its samples - for a voice
with a single zone, which is what the factory banks use throughout, both simply offset the playback
pitch. The float at offset 27 is a chorus parameter.

`E5Am` is `version(4) volume(float, 4) pan(1) reserved(3)`. The volume is the value in decibels, the
range the manual gives is -96 dB to +10 dB with a default of 0 dB. Across all 18670 voices no value
ever exceeds 10.0, which is that maximum; the third party banks, whose authors never touched the
control, store 0.0 throughout, which is the default; and the E-mu factory banks store 10.0
throughout, which is E-mu turning their ROM conversions up to the maximum. Values in between are
hand set levels such as 8.981 or -20.577.

The pan is a signed byte, which the manual confirms as -64 for hard left to +63 for hard right. The
E-mu banks stay inside that range but banks written by third party converters use up to ±127, so a
reader has to clamp.

`E5Fl` is `version(4) type(1) cutoff(float, 4) reserved(53)`. The cutoff is normalized to 0..1;
resonance is zero in every factory voice and its position in the chunk is unknown. Type 127 is the
'No Filter' setting which bypasses the filter section - it cannot be an index into the filter list,
which has only 55 entries. The other two types which occur are 0 (always with a fully open cutoff)
and 1. The numeric codes of the remaining types are *not* documented and are *not* the position in
the list the manual prints: the Emulator X3 inserted the 'Morph Designer' filter in the middle of
that list, which would have renumbered every filter behind it and broken older banks. The codes are
therefore assumed to be the ones the EOS hardware samplers use, which the Emulator X inherited
together with the filter set itself:

| Code | Filter                                                                   |
|------|--------------------------------------------------------------------------|
| 0    | 4-pole low-pass, the standard filter                                     |
| 1    | 2-pole low-pass                                                          |
| 2    | 6-pole low-pass                                                          |
| 8    | 2-pole high-pass                                                         |
| 9    | 4-pole high-pass                                                         |
| 16   | 2-pole band-pass                                                         |
| 17   | 4-pole band-pass                                                         |
| 18   | Contrary band-pass, the closest type to a notch                          |
| 127  | No Filter                                                                |

The remaining 47 types are swept EQs, phasers, flangers, vowel formant filters, distortions and the
programmable morph filters.

An envelope chunk `E5Ev` is `version(4) reserved(6)` followed by six stages of
`time(float, 4) level(float, 4) curve(1)`. The time is in seconds, the level is a percentage. The
manual describes the stages as attack 1, attack 2, decay 1, decay 2, release 1 and release 2: the
envelope runs from zero towards the level of each stage in turn, holds at the level of decay 2 until
the note is released - that level is therefore the sustain level - and then runs through the two
release stages. Every voice has three of them, the amplitude, the filter and the auxiliary envelope;
the modulation cords give the latter two no depth in any factory bank.

## Zone list (`LIST E5ZL`)

Per zone a `Zhdr` chunk followed by a `LIST TWL ` with two `ETW ` windows, the key range and the
velocity range of the zone. They are relative to the ranges of the voice. A voice with a single zone
leaves them fully open and puts the mapping into the voice; a multisample voice, of which the corpus
has 91 with up to 33 zones each, puts the mapping into the zones instead.

Velocity layers are built with the zone windows, not with the voice window: the voice velocity
window is fully open in all 18670 voices of the corpus, while the zones of the electric piano
libraries split the velocity range into three or four layers such as 0..65, 66..115 and 116..127.

`Zhdr` is:

| Offset | Size | Content                                                            |
|--------|------|--------------------------------------------------------------------|
| 0      | 4    | Version, 2                                                         |
| 4      | 2    | Index of the sample, matching an `E5SL` entry                      |
| 6      | 4    | Zero                                                               |
| 10     | 1    | Original key of the sample as a MIDI note number                   |
| 11     | 5    | Zero                                                               |
| 16     | 8    | `0xFFFFFFFF` twice, the loop override, unused in the factory banks |
| 24     | 4    | Zero                                                               |
