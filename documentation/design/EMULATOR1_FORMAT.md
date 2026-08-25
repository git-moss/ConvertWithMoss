# E-mu Emulator Disk Format

**Status: reading and writing are implemented.** `file/hfe/EmuFmDecoder` reads the disks,
`format/emu/emulator1/Emulator1Detector` turns their two banks into a multi-sample and
`format/emu/emulator1/Emulator1Creator` writes a disk (as a HxC image or as a raw image) from a
multi-sample. Sections are marked *confirmed*, *reported* or *unknown* so later work does not mistake
a second-hand figure for a verified one. Nothing here has been tried on a real Emulator yet.

The Emulator (1981) is the first sampler of the line, the machine *below* the Emulator II whose
disks are described in `EMULATOR2_FORMAT.md`. The two share the floppy controller and the track
format, the companding DAC and the sample clock, and nothing else: the Emulator holds two banks of
one bare sample memory each, has no presets, no envelopes and no names, and stores its pitch as a
table of sample clock periods per key.

## The machine, as far as conversion is concerned

| Property | Value | Confidence |
|----------|-------|------------|
| Sample resolution | 8 bit, AM6072 companded (not linear PCM) | **confirmed** (see *Audio*) |
| Sample rate | 27,778 Hz at unity pitch, varied per key by the sample clock | **confirmed** (pitch law) |
| Sample memory | 57,088 bytes per half of the keyboard, ~2 s | **confirmed** |
| Keyboard | 49 keys, C2 to C6 = MIDI 36 to 84 | **confirmed** (EMXP references, pitch of named samples) |
| Zones | each half divided into 1, 2, 3, 4, 6 or 8 zones of equal width | **confirmed** for 1, 2, 3, 4 and 6; 8 fits the record space |
| Loop | one forward loop per sample | **confirmed** |
| Filter | one 3 bit setting per sample | **confirmed** field, *reported* meaning (EMXP) |
| Envelopes, velocity, names | none | **confirmed** by absence |
| Operating system | loaded from the first two tracks of every disk at power-on | *reported* (manual: "software is not stored in the machine but on each diskette") |

The manual excerpts that could be found say that all operating software from version 0303 onwards
plays pre-recorded multi-sample sounds, that the software is loaded during the power-on procedure
and that `GET UPPER` and `GET LOWER` load the sounds of one half of the keyboard. Whether `GET` reads
the two system tracks again is *unknown*; it decides whether a disk written without a system can be
loaded after booting from another disk.

## Physical layout — *confirmed*

From *Disk layout of Emulator I floppy disks* v0.8 by ///Esynthesist (emxp.net), captured with an
oscilloscope on the Shugart interface, and verified here on all 79 disks of the corpus.

- Shugart 400L drive, SSDD soft-sectored 5.25" media, 300 RPM, **one side, 35 tracks** (of the 40 the
  drive could format)
- **FM** encoding, 310 kbit/s including the clock pulses, 155 kbit/s of data
- **1 sector per track, 3584 bytes** (0xE00), the same sector as on the Emulator II
- Tracks 0-1 operating system, 2-17 lower bank, 18-33 upper bank, 34 sequencer memory

Track structure as written by the machine:

| Field | Size | Value |
|-------|------|-------|
| GAP | 20-24 bytes | `FF` (the machine writes 24, a capture that starts at the index pulse shows 20) |
| SYNC | 4 bytes | `00` |
| Mark | 2 bytes | `FA 96` |
| ID | 1 byte | track number `00`-`22` |
| CRC of ID | 2 bytes | see below |
| SYNC | 2 bytes | `00` |
| GAP | 7 bytes | `FF` |
| SYNC | 4 bytes | `00` |
| Mark | 2 bytes | `FA 96` |
| Data | 3584 bytes | sector payload |
| CRC of data | 2 bytes | see below |
| SYNC | 2 bytes | `00` |
| GAP | to the index | `FF` |

The bit level encoding is the one of the Emulator II: each FM bit cell is four bits (slots) of the
HFE stream with the clock pulse in slot 1 and the data pulse in slot 3, bits and bytes least
significant bit first, so a track uses only the byte values `AA`, `A2`, `2A` and `22`. The CRC is
polynomial `8005` fed least significant bit first - reflected form `A001`, initial value `0000` -
over the payload only (the track number, or the 3584 data bytes), stored least significant byte
first.

### The two fields of a track are written separately — *confirmed*

This is what broke the first decoder. The Emulator writes the header and the data field of a track
in two operations, so the data mark does not sit at a fixed distance from the header mark: across
the 2,765 tracks of the corpus it is found 0, +2, -2 or +4 slots away from the nominal position,
i.e. shifted by half or by a whole bit cell. A decoder that assembles bytes from the start of the
track and looks for `FA 96` on byte boundaries loses the data field on every track whose shift is
not a multiple of a byte - on the first corpus disk it read 10 of 35 tracks.

`EmuFmDecoder` therefore works on the single flux pulses: it searches the 64-slot pulse pattern of
the mark at any phase, reads the header behind it, then searches the data mark again at any phase
behind the header CRC and reads the data field from there. With that **all 2,765 tracks of the 79
corpus disks decode with valid CRCs** (35 per disk, none missing). The same decoder handles the
Emulator II disks written by the machine and the images the current HxC tools generate for it,
which put the data mark 28 sync cells (three and a half bytes) after the gap where the older
synthetic corpus and the published description have 32; the 90 factory disks and the OS 3.1 image
of the Emulator II corpus all decode as well.

### Image containers — *confirmed*

| Extension | Meaning |
|-----------|---------|
| `.hfe` | HxC Floppy Emulator container, 1,112,064 bytes for an Emulator disk |
| `.emufd` | HxC's extension for the **raw sector image**, 35 × 3584 = **125,440 bytes** |
| `.img` | the same raw bytes |
| `.E1O` | an operating system on its own: the first two tracks, 7,168 bytes (emxp.net publishes 3.07 and 3.11) |

The HFE header of every corpus disk: signature `HXCPICFE`, revision 0, **35 tracks, 1 side**, track
encoding **`0x03` = `HfeFile.ENCODING_EMU_FM`**, bit rate 312 kbit/s, floppy interface mode
**`0x0B` = `HfeFile.FLOPPYMODE_EMU_SHUGART`**, track list at block 1, track *i* at block 2 + 62·i with
a length of 31,250 bytes. The 256-byte half of each block which would hold side 1 is `00`, the
padding behind the track data is `AA` - the closing gap continued. `HfeFileWriter` and
`EmuFmEncoder` reproduce exactly this, and re-encoding a decoded corpus disk decodes back to the
identical raw image.

The raw `.emufd` dumps which circulate were made from other physical copies of the same titles:
disk `#1 Trombone - Trumpet` and `EmuI_00_002_103M2__00_001_101M4_Trombone_Trumpet.emufd` agree on
34 of 35 tracks and differ in 12 parameter bytes of the upper bank header, the way two copies of a
disk that was edited differ.

## Disk layout — *confirmed*

| Tracks | Offset | Content |
|--------|--------|---------|
| 0-1 | `0x00000` | operating system, 7,168 bytes of Z80 code |
| 2-17 | `0x01C00` | lower bank: header and sample memory of the lower half of the keyboard |
| 18-33 | `0x0FC00` | upper bank, same layout |
| 34 | `0x1DC00` | sequencer memory |

**Operating system.** The `.E1O` files EMUOS311 and EMUOS307 from emxp.net are exactly the first
two tracks of a disk; OS 3.11 is byte-identical to the system tracks of 20 of the 79 corpus disks,
the others carry 19 other versions. Bytes 3-4 of track 0 hold the serial number of the boot ROM for
the copy protection described in *Copy protection schema in Emulator I* v1.0 by ///Esynthesist
(the corpus disks carry `81 01`); later system versions reduced the protection to a check that is
satisfied by write-enabling the disk. The system is copyrighted E-mu code and is not part of the
converter: the creator copies it from a system file or a disk image which the user names, and
without one it writes the system tracks as zeroes.

**Sequencer track.** An empty sequencer holds the 12 settings bytes
`08 04 02 00 0A 04 02 00 00 FE 00 FE`, 52 zeroes and then alternating 64-byte blocks of `FF` and
`00` up to the end of the track; three of the fourteen reference disks carry this pattern, the
others hold recorded sequences or other leftovers. The creator writes the empty pattern.

## Bank layout — *confirmed*

A bank is **a 256-byte header followed by 57,088 bytes of sample memory**, together the 16 tracks =
57,344 bytes. The bank is loaded at RAM address **`0x2000`**, and every address in the header is a
16-bit absolute address of that space, so `bank offset = address - 0x2000`: the sample memory runs
from `0x2100` to `0xFFFF` (57,088 bytes), which is why the largest sample of the references is
57,084 frames.

Samples are stored **in blocks of length + 4 bytes at 4-byte aligned addresses, in the order in
which they were recorded**, which is not the order of the zones: on `TenorSax_Flute` the sample of
zone 4 is the last block at `0xD3EC`.

### Header records

The header is an array of 16-byte records:

| Offset | Content |
|--------|---------|
| `+0x00` | record 0: the selected sample |
| `+0x10` … | one record per zone, in key order |
| `+0xA0` | 32 bytes: `\0COPYRIGHT 1982 E-MU SYSTEMS INC` on disks written by the newer multi-sampling software, leftovers on older ones |
| `+0xC0` | the pitch table, 25 × 16 bit, on the newer disks (the records point at it) |

A record:

| Offset | Size | Content |
|--------|------|---------|
| `+0` | 1 | record 0: flags - `0x10` multi-sample bank with zone records, `0x04` loop off (`0x14` on the banks whose samples do not loop). Zone records: the number of zones in the first, 24 / zones = keys per zone in the second, 0 in the others |
| `+1` | 1 | filter setting in the upper 3 bits |
| `+2` | 2 | zone records: address of the pitch table, `0x20C0` on the newer disks, `0x2050` on the older ones (which then hold at most 4 zones). Record 0: `00 00` |
| `+4` | 2 | sample start address |
| `+6` | 2 | loop start relative to the sample start, less one |
| `+8` | 2 | loop start address |
| `+10` | 2 | loop length, less one |
| `+12` | 2 | loop end address |
| `+14` | 2 | bytes from the loop end to the end of the sample |

All 16-bit values are little-endian. The **sample length is `loopEnd + last - 3`** (relative to the
start), which is the convention EMXP uses and which reproduces the length of every reference sample
exactly; the allocated block is one byte longer than `loopEnd + last`, so the machine keeps 4 bytes
behind the audio. A sample which does not loop carries a **loop of length 2 right at its end**
(`loop start = length`, `last = 1`); a loop longer than 2 is a real loop. Record 0 mirrors the
descriptor of the sample which was selected when the disk was saved - zone 1 on most disks, zone 4
on `TenorSax_Flute`.

A bank made **before the multi-sampling software** existed - `Tympani_OchestraHit`, lower bank - has
no `0x10` flag, no zone records and no table: record 0 holds flags `0x04`, its sample starts at
`0x2010` directly behind the record and fills the half of the keyboard.

A single-zone bank has the record `19 00 00 …` behind its zone record; it is written as found.

### The filter — *reported*

The 3-bit setting is read from bits 7-5 of byte 1: 0 leaves the filter open. EMXP assigns the
cutoff frequencies 19,300 / 18,200 / 14,500 / 9,000 / 5,000 / (unobserved) / 800 Hz to the settings
1 to 7 - these are the frequencies its SoundFonts carry - and the converter uses them, with 2,200 Hz
interpolated for the setting 6 which none of the references uses. Which analogue filter sits behind
the setting is *unknown*.

## Keyboard and zones — *confirmed*

The keyboard has 49 keys, C2 to C6 = **MIDI 36 to 84**; the **lower half is MIDI 36-59** (24 keys),
the **upper half 60-84** (25 keys). Each half is divided into zones of equal width **24 / zones**,
and the 25th key of the upper half belongs to its top zone (`Trombone_Trumpet` upper: 60-65, 66-71,
72-77, 78-84). Observed zone counts are 1, 2, 3, 4 and 6; 8 zones of 3 keys still fit the record
space in front of the copyright text. A bank without a pitch table plays at its recorded pitch on
MIDI **48** (lower) and **72** (upper).

The pitch of every key is confirmed by the samples themselves: the loop of the trombone G#2 is 268
frames = one period of 103.6 Hz, the trumpet loops of 87, 62, 43 and 31 frames are one period of
D#4, A4, E5 and A5, all at 27,778 Hz and all on the keys the table puts them.

## Pitch table — *confirmed*

The zone records point at a table of **25 little-endian 16-bit entries**, one per key of the half
from its lowest key upwards plus one more; on the newer disks it sits at `0x20C0`. An entry holds a
**13-bit value *m* in the lower bits and a 3-bit code in the upper three**.

### The value — the sample clock

The value is the complement of the period of the sample clock of the key:

```
N = 3072 - m                                 (the period, in ticks)
sample clock = 27,778 Hz × 416 / N
cents above the recorded pitch = 1200 × log2 (416 / N)
unity: m = 2656, N = 416
```

This law is exact on every reference: -8 semitones is stored as 2412 (the law gives 2411.6), -3 as
2577 (2577.3), +5 as 2760 (2760.3), and the unity value 2656 appears at the root key of every zone
whose EMXP fine tune is 0. It also reproduces EMXP's roots and fine tunes throughout: the trumpet A
of `Trombone_Trumpet` has 2648 at key 69, i.e. N = 424 and -33 cents, and indeed its loop of 62
frames is one period of 448 Hz - the sample was recorded a third of a semitone sharp and the
operator tuned it on the machine. The step between two semitones therefore shrinks with rising
pitch (37 values at -8, 23 at unity, 18 at +5), which is what identifies the table as periods.

The **root key** of a zone is the key whose entry lies closest to unity and the **tuning** is its
residual in cents; EMXP does the same with a linear estimate of the step, which is why its fine
tunes differ by up to 3 cents from the exact law. A table whose entries do not rise by semitones
would mark a sample which does not track the keyboard; none of the corpus disks has one.

### The code — *unknown*

The upper 3 bits depend only on the position of the key in its zone: a 24-key zone runs
`5 5 5 5 4 4 4 4 3 3 3 3 2 2 2 2 1 1 1 1 0 0 0 0`, a 12-key zone `2 2 2 2 1 1 1 1 0 0 0 0`, an
8-key zone `1 1 1 1 0 0 0 0`, and 6- and 4-key zones are all 0 - i.e.
`max (0, (4 × (size / 4) - 1 - index) / 4)` in integer arithmetic. The same value with a different
code plays the same pitch, so the code is not part of the pitch; what it controls is not known. The
creator writes the pattern as found.

## Audio — *confirmed*

The sample bytes are the code of the **AM6072 companding DAC**, exactly as on the Emulator II: sign
in bit 7, chord in bits 6-4, step in bits 3-0, and

```
magnitude = ((step × 2 + 33) << chord) - 33          full scale 8031
```

Tabulating every byte of the trombone sample of `Trombone_Trumpet` against the sample EMXP wrote
into its SoundFont gives **one value per byte for all 256 byte values, and every one of them is this
expansion × 0.7** (`0x7F` → 22486 = 32124 × 0.7, `0x01` → 5 = 8 × 0.7), with the SoundFont sample
aligned at the first byte of the sample memory, disk offset `0x1D00`. `EmuCompanding` holds the
transfer function in both directions for the Emulator and the Emulator II; the Emax keeps its
own copy in `EmaxConstants`.

## Validation

- **Physical.** 79 of 79 HFE disks, 2,765 tracks, zero CRC failures and no missing track; the disk
  which is also available as a raw dump agrees track for track except for the 12 edited bytes named
  above.
- **Logical.** The corpus holds 14 raw dumps of the *Production Set* together with the SoundFonts
  EMXP made from them in July 2018 (`ISFT: EMXP by ESynthesist`). Converting the 14 disks and
  comparing zone by zone: **key ranges, root keys, sample lengths and loop points agree on every zone
  of every disk**, tunings agree within 3 cents, with a single disagreement of 21 cents on one zone of
  `TenorBariSax_AltoSax` where EMXP wrote 0 while the table holds 2661 = +21 cents at the root.
- **Writing.** A disk converted to SFZ and back to an Emulator HFE decodes CRC-valid on every track,
  the decoded raw image re-encodes to the identical HFE, and reading the written disk again gives the
  same zones, roots, loops and audio. Not tried on hardware.

## The converter

`Emulator1Detector` reads `.emufd`, `.img` (125,440 bytes) and `.hfe`; one disk becomes one
multi-sample with the zones of both halves: key range, root key, tuning, loop, filter and the
expanded audio at 27,778 Hz.

`Emulator1Creator` writes one disk per multi-sample: the zones which cover each half are mapped onto
the smallest available zone count, each zone plays the source zone which covers most of its keys,
the audio is mixed to mono, re-sampled to 27,778 Hz where the source is faster - a slower source
keeps its rate, which the pitch table compensates - and companded; the records, the pitch table
(with the code pattern above) and the empty sequencer track are written, the system tracks are
copied from the `.E1O` file or disk image named in the settings, and the image is written as a HFE
through `EmuFmEncoder` with the field spacing of the Emulator or as a raw `.emufd`. A sample which
does not fit into the 57,088 bytes of its half is shortened and reported. Two things are *unknown*
until a machine is available: whether the disk loads as written, and whether `GET UPPER` / `GET
LOWER` re-read the system tracks, which decides whether a disk without a system can be loaded after
booting from a factory disk.

## Sources

- ///Esynthesist, *Disk layout of Emulator I floppy disks* v0.8 (2010) — https://emxp.net/Disk_layout_of_EmulatorI_floppy_disks_v0_8.pdf
- ///Esynthesist, *Copy protection schema in Emulator I* v1.0 (2010) — https://emxp.net/Emulator_I_BootRom_CopyProtection_v1_0.pdf
- EMXP additional downloads (OS 3.07 and 3.11 as `.E1O`) — https://emxp.net/Additional_downloads.htm
- E-mu Systems, *Emulator Operating Instructions* — the excerpts on `GET UPPER` / `GET LOWER`, the multi-sampling software (0303 and later) and the loading of the software at power-on
- Corpus: *Emulator I all* — 79 `.hfe` of the factory library (emulatorarchive numbering) and the 14 `.emufd` dumps of the *Production Set* with the SoundFonts EMXP wrote from them
- `EMULATOR2_FORMAT.md` for the shared track encoding and the companding
