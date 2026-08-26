# E-mu Emulator II Disk Format

**Status: reading and writing are implemented.** `file/hfe/EmuFmDecoder` reads the disks,
`format/emu/emulator2/Emulator2Detector` turns every preset of a bank into a multi-sample and
`format/emu/emulator2/Emulator2Creator` writes a bank disk. Sections are marked *confirmed*,
*reported* or *unknown* so later work does not mistake a second-hand figure for a verified one.
Written disks have not been tried on a real Emulator II yet.

The Emulator II (1984) is the generation *below* the Emulator III whose bank format is described in
`EIII_FORMAT.md` and *above* the Emulator whose disks are described in `EMULATOR1_FORMAT.md`. It
shares the track format and the companding with the Emulator and nothing with the Emulator III,
which is a 68000 machine with an E-mu filesystem on standard MFM media - the Emulator II is a Z80
machine writing a proprietary FM track format that no PC floppy controller can read.

## The machine, as far as conversion is concerned

| Property | Value | Confidence |
|----------|-------|------------|
| Sample resolution | 8 bit, AM6072 companded (not linear PCM) | **confirmed** (service manual, audio) |
| Sample rate | 27,777 Hz, fixed | *reported*, consistent with the loop periods of the factory voices |
| Sample memory | a 512 KB address space (1 MB on the Emulator II+, as two banks), of which the sampler saves **485,888 bytes** of bank: from the bank address `0x9600` to the end of the 512 KB | **confirmed** (the bank region of every factory disk is blank behind that) |
| Voices per bank | up to 100 | **confirmed** (voice list of 100 entries) |
| Presets per bank | up to 100 | *reported* |
| Keyboard | 61 keys, C2 to C7 = MIDI 36 to 96 | **confirmed** (pitch of named factory voices) |
| Voice settings | filter (SSM2045 4-pole low-pass), VCA and VCF envelopes, LFO, level, chorus | *reported* - the bytes are located, their meaning is not decoded |

## Physical layout — *confirmed*

From *Disk layout of Emulator II floppy disks* v1.2 by ///Esynthesist (emxp.net), reverse-engineered
from oscilloscope captures on the Shugart interface, corroborated by E-mu's own service manual (the
debug monitor's `GETDISK`/`PUTDISK` read whole tracks of `E00` bytes, track numbers `0`-`9F`,
straight into linear RAM) and by the measured file sizes.

- DSDD soft-sectored 5.25" or 3.5" media, 300 RPM, **FM**, 2 sides × **80 tracks**, tracks addressed
  linearly `00`-`9F` as cylinder × 2 + side
- **1 sector per track, 3584 bytes (0xE00)** - a raw image is `2 × 80 × 3584` = **573,440 bytes**
- Track: gap `FF`, sync 4 × `00`, mark `FA 96`, track number, CRC, sync, gap, sync, mark `FA 96`,
  3584 data bytes, CRC, sync 2 × `00`, gap `FF` to the index hole

Bit level, derived from the OS 3.1 disk which EMXP publishes as both a HFE and a raw sector image of
the same disk: each FM bit cell occupies **four bits (slots)** of the HFE stream, the clock pulse in
slot 1 and the data pulse in slot 3, bits and bytes **least significant bit first**, so a track uses
only the byte values `AA`, `A2`, `2A` and `22`. `FA 96` are two ordinary data bytes - there are no
missing-clock address marks. The CRC is polynomial `8005` fed least significant bit first (reflected
form `A001`, initial value `0000`) over the payload only - the track number, or the 3584 data bytes -
stored least significant byte first.

### The data field sits at its own phase — *confirmed*

The header and the data field of a track are written separately, which is why the distance from
the header mark to the data mark is not what the published description says: the Emulator II - and
the images which the current HxC tools generate for it - puts **one sync byte, seven and a half
bytes of gap and four bytes of sync** between the header CRC and the data mark, i.e. the data mark
starts half a byte earlier than a byte-aligned reading expects. The older synthetic corpus of 1,437
disks has a whole byte there. A decoder which assembles bytes from the start of the track and looks
for `FA 96` on byte boundaries therefore read the synthetic corpus and **not a single track of the
90 factory disks**. `EmuFmDecoder` now works on the single flux pulses: it finds the 64-slot pulse
pattern of the mark at any phase, reads the header behind it, then finds the data mark again at any
phase behind the header CRC. With that all 160 tracks of every factory disk, the OS 3.1 disk and the
synthetic corpus decode with valid CRCs; re-encoding the decoded OS 3.1 disk with `EmuFmEncoder`
reproduces the HxC image **byte for byte**, which pins the field spacing exactly.

### Image containers — *confirmed*

| Extension | Meaning |
|-----------|---------|
| `.hfe` | HxC Floppy Emulator container, 2,540,544 bytes for an EII disk: 80 tracks, 2 sides, encoding `0x03` (`HfeFile.ENCODING_EMU_FM`), interface `0x0B` (`FLOPPYMODE_EMU_SHUGART`), 312 kbit/s, track list at block 1, track *i* at block 2 + 62·*i* with 31,250 bytes, padding `AA` |
| `.emuiifd` | HxC's extension for the **raw sector image**, 573,440 bytes |
| `.img` | the same raw bytes |
| `.E2O` | an operating system on its own, 72,704 bytes = the first 72,704 bytes of a disk (emxp.net publishes 2.1 / 2.3 / 3.0 / 3.1 / 2.6HD / 3.1HD) |
| `.eii` | a **bank file**: the bank memory alone, without the operating system, **485,887 bytes** = the 485,888 bytes the sampler saves less one. This is the *Sound Designer for EII* file of Digidesign's 1985 Macintosh software, which received the bank from the sampler over its RS-422 interface; EMXP's CDS3TOOL extracts them from the CDS3 CD-ROMs, EMXP writes them, and Arturia's *Emulator II V* imports them, which is why libraries in this form circulate. The bank starts at the first byte; the reader locates it from the self-pointers of the voice records anyway, so a file with a header in front of the bank is read too |

### OS extent — *confirmed*

`EMUIIOS31.E2O` is byte-identical to the first 72,704 bytes of the OS disk, so the OS occupies tracks
0-20 and the **bank starts at track 22, offset `0x13400`** (78,848); across the synthetic corpus the
first block that differs between disks is exactly there. The bank region is tracks 22-159 =
494,592 bytes, but the sampler writes only its **bank memory of `0x76A00` = 485,888 bytes** into
it - from the bank address `0x9600` to the end of its 512 KB address space - and the last 8,704
bytes of the region are zero on every one of the 90 factory disks, including those whose last
voice reaches the end (its audio stops at `0x769FF`). A disk without an operating system is
possible - the machine loads a bank from the disk once it has booted from another one - which is
what the creator writes when no system file is named.

## Bank layout — *confirmed*

The bank is the memory image of the operating system, so its layout is that of the OS data
structures. It is loaded at CPU address **`0x9600`** of the parameter memory: every 16-bit pointer
in the bank is `0x9600 + offset`, which is what identifies the voice records (see below). All
offsets here are relative to the bank start.

| Offset | Size | Content |
|--------|------|---------|
| `0x000` | 12 | blank (12 spaces on every disk) |
| `0x00C` | 11 × 61 | the expanded key maps of the selected preset, one byte per key of the 61-key keyboard (see *The selected preset*) |
| `0x2AB` | 35 | the header, name and parameters of the selected preset |
| `0x2CE` | 1 | the number of the selected preset |
| `0x2CF` | 100 | the **voice list**: the identifier of each voice number, `0x9B` + record index, 0 for an unused number |
| `0x333` | 2 × n | the preset directory: for each preset but the last the CPU address (big-endian) of the length word which ends its record |
| `0x500` | 256 × n | the **voice records** |
| behind them | | the **preset records** |
| further behind | | the **sample memory** |

### Voice records — *confirmed*

An array of **256-byte records from `0x500`**, each starting with the tag bytes `04 03`. The record
is the runtime structure of a voice, so most of it is the state of the sampler's playback engine;
the part which describes the sample is:

| Offset | Size | Content |
|--------|------|---------|
| `+0x00` | 2 | tag `04 03` |
| `+0x02` | 3 | the **counter block**: playback start − 1, 24-bit little-endian |
| `+0x06` | 3 | `0x500000` − the number of frames in front of the loop: the negative count the address counter is loaded with |
| `+0x0A` | 3 | loop start − 1 |
| `+0x0E` | 3 | `0x500000` − loop length (`0x600000` − loop length when flag bit 3 is set) |
| `+0x13` | 3 | the same |
| `+0x22`, `+0x26`, `+0x95`, `+0xA9` | 2-3 | pointers into the record itself: `0x9B00 + 0x100 × index + offset`, so the high byte names the record |
| `+0xBA` | 12 | name, ASCII, space padded |
| `+0xC6` | 1 | flags: **bit 2 (`0x04`) = loop on**, bit 4 (`0x10`) = the counter block is stale, bit 3 (`0x08`) changes the base of the loop counter; `0x24` (looped) and `0x20` (not looped) are the usual values, bits 1 and 5 are not decoded |
| `+0xC7` | 3 | **region start**, relative to the bank: the memory of the voice |
| `+0xCA` | 3 | **slot size** = the audio up to the end of the loop + 4 (+ 2 without a loop); the next voice's start on a contiguous bank |
| `+0xCD` | 3 | **loop start**, relative to the bank: the audio in front of it plays once |
| `+0xD0` | 3 | **loop length** in frames; 1 or 2 when the voice has no loop |
| `+0xD3` | 3 | **playback start**, relative to the bank: the region start on 3,707 of the 3,848 factory voices, later on the others |
| `+0xD6` | 3 | the number of frames from the playback start to the end of the loop + 4 (+ 2 without a loop) - the slot size for a voice which plays from its region start |
| `+0x1A`-`+0xB9` | | the settings of the voice: filter, envelopes, LFO, level (*unknown* layout) |

**The loop is the end of a voice.** Its memory region holds the audio in front of the loop, then
the loop, then the padding; the sampler plays the first part once and then repeats the loop - or
plays it once when the loop is switched off - and nothing lies behind it. `Piano A 2` of *Grand
Piano & Strings* shows it in the audio: the level of the 12,295 frames behind its "sample end"
continues the decay of the 33,657 frames in front of them, with the pitch of the note. The first
description read `+0xCD` as the sample end and `+0xD3` as the loop start, and it took bit 1 of the
flags for the loop switch, so it looped the beginning of 274 voices and cut the loop region off
all others.

The counter block proves the reading: it holds exactly what the channel is loaded with, and on
3,727 of the 3,768 factory voices whose block is valid the number of frames it plays and its loop
equal the ones computed from the fields above (the other 41 differ by 4 frames). The block is
stale on the 80 voices with flag bit 4 - copies whose counters were never computed - which is why
the fields above and not the counters are what the reader uses; the counters serve as the check.

Two kinds of voices play from elsewhere than their region start. The `*1` copies of the stacked
strings (*Stacked Strings*, *Grand Piano & Strings*, *High Strings*, ...) start a few frames in
front of the loop start and are the sustain-only layer of the stack, and the fifteen voices of
*Mallet Cymbals* are windows into one 17-second recording which they share - their loop fields are
those of the whole recording and only the playback start and the length field at `+0xD6` describe
the window. The reader therefore plays from `+0xD3` for the smallest of: the frames up to the end
of the region, the length field, and the frames up to the end of the loop.

On the factory library 3,311 of the 3,848 voice records have the loop bit set.

The old table position `0x5BA` of the first description was the name field of this record;
counting the records from `0x502` put the tag bytes at the end of the previous record, which is why
the presets seemed to start inside the last voice.

### The heap — *confirmed*

Voice records and preset records share the memory behind the key maps. Usually all voices come
first and the presets follow, but a bank saved with an empty preset selected starts with that
preset's record at `0x502` and continues with the voices in the next 256-byte slot (*Marcato
Strings*, *Grand Piano #2*, *Conga*). The reader therefore walks the heap: a slot which starts with
`04 03` is a voice record, otherwise a chain of preset records is expected, and after a chain the
next voice slot is the next multiple of 256 bytes.

### Preset records — *confirmed*

The two bytes in front of the first preset record hold its length, and **every record ends with
the length of the next one** (0 ends the chain). A record:

| Offset | Size | Content |
|--------|------|---------|
| `+0` | 9 | header `01 04 00 00 00 00 02 03 89` on the newer OS versions, `01 00 00 00 00 00 00 00 81` / `00 … 80` on older ones; the top bit of the last byte is always set |
| `+9` | 12 | name, ASCII, space padded |
| `+21` | 14 | parameters (*unknown*, `00 0B 00 00 00 00 00 00 00 00 00 00 50 04` is the most frequent) |
| `+35` | | the **key range entries** |
| | 4 | end marker `00 3D 00 00` (`0x3D` = 61 = the key behind the keyboard) |
| | 2 | length of the next record |

A key range entry is 5 bytes: `[mode << 6 | count] [00 or 08] [voice] [transposition] [level]`.
The count is the number of keys, the ranges follow each other from the lowest key and always sum
to 61. The mode is 1 for a silent range (voice 0), 2 for one voice and 3 for a range which plays
**two voices** - the second voice follows as 3 more bytes `[voice] [transposition] [level]`. The
voice is a 1-based *voice number*, resolved through the voice list at `0x2CF` to a record; a bank
without a voice list numbers its records in their order. The level is `0x70` on 361 of 375 factory
ranges. The second byte is 0 or 8 and not decoded.

### Key mapping — *confirmed*

The transposition is stored for the first key of a range and rises by one per key. **Key index 0 is
MIDI 36 and the value `0x10` plays a voice at its recorded pitch**, so

```
MIDI note = 36 + key index
root key  = MIDI note of the first key + 0x10 − transposition
```

This was settled with the factory disk *Grand Piano*: its six voices `piano A2`, `F#3`, `D4`,
`A#4`, `F#5` and `D6` measure 111.6, 187.7, 295.5, 470.8 and 750.7 Hz - their names in scientific
pitch - and the rule puts every one of them exactly on the key of its name (45, 54, 62, 70, 78, 86);
the Rhodes voices of the community disk *CLAVINET* (98 Hz = G2 on key 43, 196 Hz on 55) confirm it.
The first description used key 26 and the unity value `0x0E`, which is the same mapping shifted by
an octave: it was derived from a 12-string guitar disk whose preset is transposed down an octave in
the way guitar music is written, and it put every converted preset an octave too low.

### The selected preset

The eleven 61-byte tables at `0x00C` are the working copy of the selected preset which the OS keeps
expanded per key: voice number, voice identifier, transposition, two zero tables, the
transposition again, level (`0x70`), second level, a zero table, a table which is `0x0F` except at
the first key of each range, and a zero table. They only describe the selected preset, which is why
the first reader, which read them, saw one preset per disk - the factory disks hold up to 18.

### Sample memory — *confirmed*

The audio follows the records: on 88 of 91 factory disks the first sample starts exactly
**`0x95FE` bytes behind the end of the preset chain**, and the voices' slots follow each other
without gaps in the order in which the voices were recorded.

The sampler lets a voice be recorded into the whole of its address space but saves only the bank
memory below `0x80000` − `0x9600`, so the audio behind bank offset `0x76A00` is on no disk: 124
voices on 32 of the 90 factory disks reach beyond it - *Orchestra Tune* by 26,859 frames, *Grand
Piano* by 121 - and none of the OS 3.1 messages knows a bank on two disks (its "Insert Another
Disk" prompts belong to the disk copy and format functions). Such a voice is cut at the end of
the bank memory and noted; the first description took the whole 494,592-byte region for bank
memory and read the blank last 8,704 bytes as audio, and reported the bank as continued on another
disk.

### The sample encoding — *confirmed*

The service manual settles it: "Each output channel consists of an input latch (74HCT374), a DAC
(6072), a VCF/VCA (SSM2045) and a switched capacitor filter". The **AM6072 is a companding DAC**, so
the expansion is done in hardware and the stored byte is already the code of the converter: a sign
in bit 7, a chord in bits 6 to 4 and a step in bits 3 to 0, and

```
magnitude = ((step * 2 + 33) << chord) - 33          full scale 8031
```

The same code is confirmed one-to-one on the Emulator, whose EMXP SoundFonts are exactly this
expansion × 0.7 for all 256 byte values (see `EMULATOR1_FORMAT.md`). `EmuCompanding` holds the
transfer function in both directions for the Emulator and the Emulator II; the Emax keeps its
own copy in `EmaxConstants`.

## The converter

`Emulator2Detector` reads `.img`, `.emuiifd` and `.hfe` disk images and `.eii` bank files; every
preset of a bank becomes one multi-sample: a zone per key range with the key range, the root key,
the name, the loop and the expanded audio of its voice - from the playback start to the end of
the loop -, and the second voices of the ranges as a second group. Over the 90 factory disks this
gives **605 presets** with 5,214 zones, 4,254 of them looped (the first reader gave one preset per
disk with an octave error and loops on unlooped voices; 5 disks it could not read at all; the
second lost the loops of all but 274 voices); 26 disks of the older community corpus give 125. The
bank files which the tests used were carved out of the factory images; they decode to the same
zones as the disks, less the one frame their last byte would have held.

`Emulator2Creator` writes a bank disk from one multi-sample or from a library: each multi-sample
becomes a preset, the zones of its first group the voices of the key ranges, the zones of its second
group their second voices, identical audio is stored once. The audio is mixed to mono, re-sampled to
27,777 Hz and companded, and cut at the end of the loop, since the sampler plays the loop as the end
of a voice; the voice records take their settings from the voice `piano A2` of the disk *Grand
Piano* with the name, the addresses, the counter block, the loop flag and the pointers replaced,
the preset records and the key maps of the first preset are written as described, the sample memory
starts `0x95FE` behind the records and ends at the 485,888 bytes the sampler saves, and the
operating system is copied from the `.E2O` file or disk image named in the settings. The image is
written as a HFE with the field spacing of the HxC images or as a raw `.emuiifd`. Round trip: the
15 presets of *Grand Piano* converted to SFZ and back to a disk read back with identical key
ranges, roots, lengths and loops.

### Still to decode

The voice settings (filter cutoff and Q, VCA and VCF envelopes, LFO, level, chorus), bits 1, 3 and
5 of the voice flags (bit 1 may be *loop in release*), the preset parameters and the second byte of
a key range entry.

## Test corpus

| Material | Location | Purpose |
|----------|----------|---------|
| 90 factory disks (`.hfe`) + the OS 3.1 disk | `EmuSounds.zip` → `EII_Factory_HxC.7z` | dumps of real disks, 605 presets |
| 1,437 community disks (`.hfe`) | `EmulatorII-Library/` (untracked, 3.4 GB) | synthetic HxC images from `dblondin.com/samples/EII_HXC.zip` |
| `emuiios.emuiifd` + `emuiios31.hfe` | emxp.net | the same disk raw and as HFE - the byte-exact decoder and encoder validation |
| bank files (`.eii`) | Jürgen's test set; carved from the factory images for the tests here | Sound Designer for EII bank files, 485,887 bytes |

## Sources

- ///Esynthesist, *Disk layout of Emulator II floppy disks* v1.2 — https://emxp.net/Disk_layout_of_EmulatorII_floppy_disks_v4.pdf
- EMXP additional downloads (OS images, memory-test disk) — https://emxp.net/Additional_downloads.htm
- E-mu Emulator II Service Manual — https://archive.org/details/e-mu_Emulator_II_Service_Manual
- *Emulator II+ Owner's Manual* (OS 3.1), Voice Definition module: loop start and loop length, loop in release
- ChickenSys Translator, EII floppy image notes — http://www.chickensys.com/translator/documentation/floppyimageinfo/emue2.html
- EMXP CDS3TOOL — https://emxp.net/CDS3.htm ("Sound Designer for EII files (.EII)"), and the *Emulator II V* import of Arturia
