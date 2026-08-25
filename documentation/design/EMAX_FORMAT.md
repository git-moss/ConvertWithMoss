# E-mu Emax and Emax II bank format

The Emax is E-mu's 1986 eight voice sampler and the Emax II its 1989 successor. Everything they hold
- presets, voices, samples and sequences - lives in one *bank*, which is a straight dump of the
sampler's memory. A bank is what a floppy disk, a hard disk slot or an `.EM1`/`.EB2` file contains.

Both samplers use the same structures. They differ in their audio - the Emax stores one companded
byte per frame in 512 KB, the Emax II 16 bit frames in 2 MB or more - and in nothing else that this
document describes; where a section does not say otherwise, it holds for both.

This document describes what was established from a corpus of 45 Emax factory banks and 147 Emax II
banks of the "Elements of Sound" library CD-ROMs, from the Emax owner's manual (1986), from the
manual of EMXP - the DOS/Windows utility which reads and writes these disks - and, for the Emax II,
from a set of SoundFont conversions of the same CD-ROMs which serve as an external reference for the
audio. Everything below is verified against those unless it is marked as an assumption.

## Files

| Extension        | Content                                                                      |
|------------------|------------------------------------------------------------------------------|
| `.EM1`, `.EM2`   | A bank, preceded by a fixed 39 byte ASCII signature (see below). A bank of the Emax II can be larger than a floppy disk, in which case each file holds one part of it |
| `.EB1`, `.EB2`   | A bank whose unused sample memory has been cut off, so the file is shorter. EMXP states that the `.EB2` layout is also what an Emax II hard disk or CD-ROM holds |
| `.EMX`           | The operating system, not a bank - but the name is also used for banks in the wild |
| `.EM1FD`, `.EM2FD`, `.IMG` | An image of a whole floppy disk: operating system followed by one bank |
| `.EZ1`, `.EZ2`, `.ISO`     | An image of a hard disk or CD-ROM, holding up to 35 banks           |

The signature which starts an `.EM1` file is the constant string

```
emaxutil v1.1 Fri Mar 19 13:31:05 1993\n
```

It is not a time stamp: EMXP writes exactly these 39 bytes into every `.EM1` it creates, and the
whole E-mu sound library on the net carries them. The bank follows immediately.

Because a bank has a strong and self-checking header, a reader does not need to know where a bank
starts in a container. Searching a file for the header finds the bank in an `.EM1`, in a raw bank, in
a floppy image and in every slot of a hard disk image alike.

The Emax formats a 3.5" DS/DD disk with 80 cylinders of 10 sectors of 512 bytes on both sides, which
gives 819,200 bytes - the "80/2/10*512 E-mu Emax 800 kB" format of OmniFlop. The track format is
standard MFM, unlike the FM format of the Emulator II disks, so a PC floppy controller and a HxC
floppy emulator both read these disks. On the disks which were available the operating system comes
first and the bank starts at offset `0x2E000`, but a reader which searches for the header does not
have to rely on that.

## The bank

A bank is 552,960 bytes:

| Offset               | Size    | Content                                                       |
|----------------------|---------|---------------------------------------------------------------|
| `0x00000` - `0x06FFF`| 28,672  | Parameter memory: header, presets, sequences, sample directory |
| `0x07000` - `0x86FFF`| 524,288 | Sample memory: the audio and the sequencer data                |

Both sizes are confirmed by EMXP, which states that "the maximum memory size available for presets,
voice and sample parameters is only 28672 bytes", and by the owner's manual, whose table of maximum
sampling times is exactly 524,288 bytes divided by each sample rate.

The parameter memory is mapped at CPU address `0x8000`, so every pointer inside it is a 16 bit
little-endian address from which `0x8000` has to be subtracted. All multi-byte values in a bank are
little-endian.

The parameter memory is a heap. Presets are allocated upwards from `0x01AC`, the sample directory
grows downwards from `0x7000`, and the free space is in between. Presets are *not* stored in the
order of the preset table, and erasing a preset leaves a hole, so records have to be reached through
the table and never by walking them in sequence.

### Bank header

| Offset  | Size | Content                                                                        |
|---------|------|--------------------------------------------------------------------------------|
| `0x000` | 200  | 100 preset pointers, 16 bit each (CPU addresses)                                |
| `0x0C8` | 2    | Heap allocation pointer: the first free byte of the parameter memory            |
| `0x0CA` | 2    | Unknown, always 0                                                               |
| `0x0CC` | 4    | Unknown, always 1                                                               |
| `0x0D0` | 4    | The index of the last used preset slot, so the number of presets minus one      |
| `0x0D4` | 204  | 51 sequence pointers, 32 bit each, into sample memory; `0x80000` means empty     |
| `0x1A0` | 4    | CPU address of the *lowest* sample directory entry                              |
| `0x1A4` | 4    | CPU address of the *highest* sample directory entry, always `0xEFE0`             |
| `0x1A8` | 4    | Number of sample memory bytes in use                                            |
| `0x1AC` | ...  | The first preset record                                                         |

The number of samples follows from `0x1A0`: `(0xF000 - address) / 32`.

The number of *presets* is nowhere in the bank. The value at `0x0D0` looks like it - in the first
banks that were examined it was exactly the number of presets minus one - but it is the preset which
happened to be selected when the bank was saved: 24 of the 45 Emax banks and all 147 Emax II banks of
the corpus hold something else there, and reading it as a count loses 245 of the 775 presets of the
Emax corpus. All 100 slots have to be checked instead. A bank may hold a preset in any of them; the
E-mu library banks put their sequence demos into the last ones, which is why "PianoSequens" sits in
slot 99 of ZD700 while its other 26 presets occupy slots 0 to 25.

Unused preset slots do not hold a defined value; they point just past the last preset record and
each one is one higher than the one before, which keeps them unique. A slot counts as used when the
record it points at passes the checks listed under *Damaged slots* below.

The value which the unused sequence slots hold is the size of the sample memory in frames, and it is
what tells the two samplers apart: `0x80000` in all 45 Emax banks - the 512 KB which every Emax has,
one byte per frame - against `0x100000` in all 147 Emax II banks, the 1M frames of two bytes which a
2 MB Emax II holds.

### Preset record

A preset record is `124 + 4 * keyAreas + 32 * voices` bytes long.

| Offset  | Size            | Content                                                          |
|---------|-----------------|------------------------------------------------------------------|
| `0x00`  | 12              | Name, ASCII, space padded                                         |
| `0x0C`  | 23              | Preset parameters (see *What is not decoded*)                     |
| `0x23`  | 1               | Number of key areas, which is the length of the voice table       |
| `0x24`  | 88              | Key map: one byte per key, `0xFF` where the key is silent          |
| `0x7C`  | 4 per key area  | Voice table                                                       |
| ...     | 32 per voice    | Voice records                                                     |

The number of *voices* is not stored - the voice records simply follow the voice table, and how many
there are follows from the highest voice which the table references. A preset in which every key
area plays a single voice has as many voices as key areas, which is the common case, but a preset
which doubles its voices has more: the factory preset "Octave Piano" has 14 key areas and 22 voices,
where the key areas play the voices 8 to 21 and double them with the voices 0 to 7.

The key map covers 88 keys and key 0 is MIDI note 21, the same convention the Emulator III uses
later. The 61 keys of the Emax keyboard are map entries 15 to 75, MIDI notes 36 to 96.

A key map entry is *not* a voice number, it is an index into the voice table. Consecutive keys with
the same entry form one key area.

A voice table entry is four bytes:

| Offset | Content                                                            |
|--------|--------------------------------------------------------------------|
| 0      | Mode flags: `0x10` is set when a secondary voice is present         |
| 1      | Unknown, part of the velocity crossfade/switch settings             |
| 2      | The primary voice of this key area                                  |
| 3      | The secondary voice of this key area, `0xFF` when there is none      |

The secondary voice is how the Emax doubles a sound - the manual calls it Dual Voice - and how it
velocity-switches or velocity-crossfades between two samples. Factory presets such as "Octave Piano"
use it to stack a second voice an octave apart on every key area.

### Voice record

A voice is 32 bytes, which is what EMXP means when it says that "a single voice only requires 32
bytes of parameter data as opposed to 256 bytes on the Emulator-II". The 48 parameters the sampler
offers per voice do not fit into 32 bytes as separate values, so they are packed into a
**little-endian bit stream**: bit *N* of the stream is bit *N* modulo 8 of byte *N* divided by 8.
Two parameters sit outside the stream on byte boundaries.

| Bits    | Width | Parameter                                                          |
|---------|-------|--------------------------------------------------------------------|
| 0-4     | 5     | Amplitude envelope attack                                           |
| 5-9     | 5     | Amplitude envelope hold                                             |
| 10-14   | 5     | Amplitude envelope decay                                            |
| 15-19   | 5     | Amplitude envelope sustain                                          |
| 20-24   | 5     | Amplitude envelope release                                          |
| 25-31   | 7     | LFO rate                                                            |
| 32-37   | 6     | LFO delay                                                           |
| 38-42   | 5     | LFO variation (not converted, the model has no equivalent)          |
| 43-46   | 4     | LFO to pitch, the vibrato depth, 13 cents per step                  |
| 47-51   | 5     | Voice tuning, signed, 3 cents per step (-48 to +45 cents)           |
| 52-55   | 4     | Velocity to filter cutoff                                           |
| 56-59   | 4     | Velocity to filter attack                                           |
| 64-67   | 4     | LFO to volume, the tremolo depth, 1.6 dB per step                   |
| 68-71   | 4     | Velocity to level                                                   |
| 76-79   | 4     | Velocity to amplitude attack                                        |
| 88-92   | 5     | Chorus amount                                                       |
| 96-103  | 8     | Original key, in key map numbering, so MIDI note 21 + value (byte 12) |
| 104-111 | 8     | The sample this voice plays, counted from the top of the sample directory (byte 13) |
| 128-134 | 7     | Filter cutoff, 0 to 120                                             |
| 136-142 | 7     | Filter Q                                                            |
| 144-150 | 7     | Filter envelope amount, signed, 240 cents per step                  |
| 160-164 | 5     | Filter envelope attack                                              |
| 165-169 | 5     | Filter envelope hold                                                |
| 170-174 | 5     | Filter envelope decay                                               |
| 175-179 | 5     | Filter envelope sustain                                             |
| 180-184 | 5     | Filter envelope release                                             |
| 185-188 | 4     | Velocity to filter Q                                                |
| 191     | 1     | Non-transpose: the voice plays at its recorded pitch on every key    |
| 192-195 | 4     | Filter keyboard tracking                                            |
| 196-199 | 4     | Panning: 1 is fully right, 8 is centred, 15 is fully left            |
| 204-207 | 4     | LFO to filter cutoff, 340 cents per step                            |
| 208-213 | 6     | Voice delay                                                         |
| 214-218 | 5     | Voice attenuation, 1.5 dB per step (0 to 46.5 dB)                    |
| 219     | 1     | Chorus on/off                                                       |

The bit positions were established by aligning **37,965 voices** of the Emax II library CD-ROMs with
the SoundFont files which EMXP produced from the same banks, and then searching every field of the
bit stream for the one which predicts each SoundFont value. Every field above predicts its parameter
for 96% to 99.97% of those 37,965 voices; the non-transpose flag reaches 100.00%. The ranges which
fall out of the fields match the manual exactly where the manual gives one: the tuning is a signed
5 bit value of 3 cents, which is the "+45 to -48 cents" of Analog Processing 11; the attenuation is
5 bits of 1.5 dB, which is its "up to 46 dB"; the voice delay is 6 bits, which is its "0 to 63"; and
every envelope stage is 5 bits, which is its "two-digit numeral between 01 and 32".

The two envelopes have the same layout, five 5 bit stages one after the other, which is what makes
the packing readable: bits 0-24 are the amplitude envelope and bits 160-184 the filter envelope.

### The parameter laws

The SoundFonts also give the physical value behind each setting, since EMXP writes seconds, Hertz
and decibels into them. The tables below are what the converter uses; they are EMXP's mapping, not a
measurement of this project's own, and EMXP had the hardware.

* **Envelope attack** rises from 1 ms at 0 through 0.1 s at 9 and 1.2 s at 19 to 10.5 s at 31.
* **Envelope hold** is nearly linear from 1 ms at 0 to 2.0 s at 31.
* **Envelope decay and release** share one table, from 1 ms at 0 through 2.1 s at 9 and 8.3 s at 20
  to 100 s at 29.
* **Envelope sustain** runs from silence at 0 to full level at 31, but not linearly in decibels: it
  is -144 dB up to 3, -108 dB at 12, -44 dB at 20 and -5 dB at 29.
* **Filter cutoff** runs from 20 Hz at 0 through 145 Hz at 36, 350 Hz at 60 and 1.3 kHz at 80 to
  19 kHz at 100, and is fully open from there. This is a far cry from a straight line over the
  range: a setting of 80 is 1.3 kHz and not the 2 kHz which an even spread would give.
* **Filter Q** is about half a decibel per step up to 20 dB at 50, then steepens.
* **LFO rate** runs from 0.13 Hz at 1 through 1 Hz at 48 and 5 Hz at 85 to 20 Hz at 120.
* **LFO delay** is linear at about 64 ms per step.
* **Velocity to level** spans 0 dB at 0, 7 dB at 5, 16 dB at 10 and 32 dB at 15.

### Sample directory

The directory grows downwards from the end of the parameter memory. Entry *n* - the sample which
voices reference as number *n* - sits at `0x7000 - 32 * (n + 1)`, so entry 0 is the sample which
starts at address 0 of the sample memory and the entries follow the samples through memory.

| Offset | Size | Content                                                                       |
|--------|------|-------------------------------------------------------------------------------|
| 0      | 4    | Unused; holds whatever the operating system left there                         |
| 4      | 4    | Start address in sample memory                                                 |
| 8      | 4    | End address                                                                    |
| 12     | 4    | Sustain loop start                                                             |
| 16     | 4    | Sustain loop end                                                               |
| 20     | 4    | Release loop start                                                             |
| 24     | 4    | Release loop end                                                               |
| 28     | 1    | Flags                                                                          |
| 29     | 1    | Sample rate index                                                              |
| 30     | 2    | Unused                                                                         |

Flags:

| Bit  | Content                                                                                 |
|------|-----------------------------------------------------------------------------------------|
| 0    | Loop on. When it is clear the loop addresses are meaningless leftovers                   |
| 1    | Loop in release. When it is clear the release loop is a second, different loop           |
| 3, 5 | Unknown. Backwards playback and crossfade looping are the candidates                     |

Bit 0 was confirmed by loop sanity: over the corpus every sample with the bit set has a loop inside
its own audio, while a third of the samples without it do not. Bit 1 was confirmed the same way -
with bit 1 set the release loop always equals the sustain loop, and with bit 0 set but bit 1 clear
it never does.

The samples follow each other through the sample memory in the order of the directory, but neither
does the first one have to start at address 0 - in a third of the corpus the audio starts at address
44 - nor do they have to be adjacent, because erasing a sample leaves a gap behind. They never
overlap, which is what a reader can check an entry against.

The sample rate index selects from:

| Index | 0     | 1     | 2     | 3     | 4     | 5     | 6     | 7     |
|-------|-------|-------|-------|-------|-------|-------|-------|-------|
| Hertz | 10000 | 15625 | 20000 | 22050 | 27778 | 31250 | 41667 | 44100 |

This is the list EMXP gives for the Emax, and the sampler's own six rates - the manual calls them
10, 16, 20, 28, 31 and 42 kHz - are the indices 0, 1, 2, 4, 5 and 6. Indices 4, 5, 6 and 2 were
confirmed by measuring the pitch of factory samples against the original key of the voice which
plays them; the measured rates land within 1.5% of the table.

The Emax cannot transpose a sample far upwards, and how far depends on its rate. EMXP lists the
limit, which is worth honouring when writing a bank:

| Hertz     | 10000 | 15625 | 20000 | 22050 | 27778 | 31250 | 41667 | 44100 |
|-----------|-------|-------|-------|-------|-------|-------|-------|-------|
| Semitones | 25    | 18    | 13    | 12    | 8     | 6     | 1     | 0     |

## The audio

The Emax stores one byte per frame. EMXP puts it plainly: "the EMAX-I stores its samples as 8-bit
compressed data, while the EMAX-II uses 16-bit linear data", and elsewhere that "the EMAX-I sound
data is compressed (~12..14 bit)". That the audio is one byte per frame also follows from the
manual's table of maximum sampling times, which is 524,288 divided by each rate.

The compression is the companding transfer function of the AM6072 DAC, the same one the Emulator II
uses: bit 7 is the sign, bits 6 to 4 are the chord and bits 3 to 0 are the step, and the value is

```
magnitude = (((step * 2) + 33) << chord) - 33
```

This was established by decoding the corpus with every plausible transfer function and measuring how
tonal the result is. Over eight banks the companded expansion gives a spectral flatness of 0.007
against 0.028 for plain sign-and-magnitude, 0.13 for two's complement and 0.20 for 16 bit samples of
either endianness - a wrong law smears a harmonic spectrum into noise, so the margin is decisive.
The exact additive constant cannot be pinned down this way; 33 is what the Emulator II uses and it
sits inside the flat optimum of the measurement.

## Damaged slots

Library banks that were edited on the machine contain preset slots whose pointer survived but whose
record was partially overwritten - 13 of the 548 preset slots of the corpus, in 3 of the 45 banks. A
reader has to validate a slot instead of trusting it:

* the pointer lies between `0x1AC` and the sample directory,
* the whole record fits below the sample directory,
* the name starts with a printable character,
* the number of key areas is at least 1 and the voice table references at least one voice,
* every key map entry other than `0xFF` is smaller than the number of key areas,
* every voice's sample number is smaller than the number of samples and every original key is
  smaller than 88.

Of the 4500 preset slots of the Emax corpus 775 pass; the rest are empty slots and the 13 slots
whose record was overwritten, which are what the checks are for.

## What is not decoded

Of the 48 voice parameters which EMXP's error list names, three are not converted: the LFO
variation, which randomises the LFO rate per key and has no equivalent in the multi-sample model,
and the two output channel assignments, which route a voice to one of the output pairs of the
sampler. The chorus, the voice delay and the velocity routings to the filter attack, the filter Q
and the amplitude attack are read but only carried where the model has a place for them.

The 23 bytes of preset parameters at offset `0x0C` of a preset record are not decoded either. They
hold the settings of the Preset Definition module - the keyboard mode, the arpeggiator, the MIDI
setup, the pitch bend range and the real-time control assignments - which apply to a whole preset
rather than to its zones.

The sequencer is not decoded. The sequence pointers at `0x0D4` reach into the top of the sample
memory and the sequence names live in the parameter heap behind the presets.

## Assumptions

The parameter laws come from EMXP rather than from a measurement of this project's own. EMXP writes
seconds, Hertz and decibels into the SoundFonts it produces, and its author had the hardware, so
these are the values that tool decided on; where the manual gives a range, they agree with it. They
have not been checked against an Emax here.

## The Emax II

The Emax II holds the same structures - the same preset table with the `0x8000` base and the "one
higher than the one before" convention for empty slots, the same header fields, the same preset
records with their 12 byte name, key area count, 88 key map and voice table, the same 32 byte voice
records and the same sample directory with the same rate indices. Its differences are:

* The audio is 16 bit little-endian PCM instead of companded bytes, so a frame is two bytes and the
  audio of a sample starts at `0x7000 + 2 * address`.
* The sample memory is 2 MB or more instead of 512 KB, which the unused sequence slots report as
  `0x100000` frames or more.

Both were established against a set of SoundFont files which EMXP produced from the same library
CD-ROMs. Of the 147 CD-ROM banks, 146 hold the audio of their SoundFont at exactly the position this
layout predicts, and comparing the converted audio with the SoundFont audio frame for frame gives
byte-identical results. The SoundFonts also confirm the sample rate table for the Emax II - each of
its eight indices appears, and the number of samples per index matches the number of SoundFont
samples with that rate exactly (1488 at index 4/27778 Hz, 497 at index 5/31250 Hz, 84 at index
2/20000 Hz, 65 at index 6/41667 Hz, 17 at index 3/22050 Hz, 2 at index 1/15625 Hz and 1 at index
7/44100 Hz), which settles the indices the Emax corpus alone could not reach.

EMXP trims a few frames from the ends of a sample when it exports a SoundFont - in 123 of the banks
exactly two at the start - so the SoundFont samples are a little shorter than the bank says. The
bank values are the ones the sampler uses and are what this converter reads.

The banks of a hard disk or CD-ROM sit one after the other, each of them a parameter block followed
by its audio, exactly like a bank in a file. The image begins with a 61,440 byte header whose
directory of 32 byte entries starts at `0x1200`: a 14 byte name, then at offset `0x12` the number of
the first 64 KB block of the bank and at `0x14` how many blocks it occupies, both 16 bit. Block
numbers are one based, so block *n* begins at `0xF000 + (n - 1) * 65536`. A reader does not need any
of this - searching the image for the bank header finds every bank - but it is what the directory
means.
