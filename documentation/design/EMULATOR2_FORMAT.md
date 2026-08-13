# E-mu Emulator II Disk Format

**Status: reading is implemented.** `file/hfe/EmuFmDecoder` reads the disks and
`format/emu/emulator2/Emulator2Detector` turns their banks into multi-samples. There is no creator. Sections are marked
*confirmed*, *reported* or *unknown* so later work does not mistake a second-hand figure for a
verified one.

The Emulator II (1984) is the generation *below* the Emulator III whose bank format is described in
`EIII_FORMAT.md`. The two are unrelated: the EIII is a 68000 machine with an E-mu filesystem on
1.44 MB MFM floppies and SCSI media, while the EII is a Z80 machine writing a proprietary FM track
format that no PC floppy controller can read.

## The machine, as far as conversion is concerned

| Property | Value | Confidence |
|----------|-------|------------|
| Sample resolution | 8 bit, **µ-255 companded** (not linear PCM) | reported, consistently |
| Sample rate | 27,777 Hz, fixed — the EII cannot vary it | reported, consistently |
| Sampling time | ~17 s per bank | reported (ChickenSys) |
| Presets per bank | up to 100 | reported (ChickenSys) |
| Voices (sample objects) per bank | up to 100 | reported (ChickenSys) |
| Voice parameters | root key, loop, chorus, VCF cutoff, resonance (SSM2045 4-pole LP) | reported |
| Main RAM | 512 KB, sixteen 32 KB segments (0..F), address space `0 0000`–`7 FFFF` | **confirmed** (service manual) |

The companding matters for the decoder: raw sample bytes are **not** signed or unsigned linear 8-bit.
They are a µ-law (µ = 255) encoding of a roughly 12-bit signal, so quiet passages retain far more
resolution than 8 bits would suggest and peaks degrade to true 8-bit grit. Decoding must expand
through the µ-255 law before writing linear PCM; treating the bytes as linear will produce a
recognisable but badly distorted result. `mpc2emu/processors/resampler.py` (local clone) models the
same law in the forward direction and is a usable cross-check.

## Disk types

- **Library disks** — hold individually accessible samples.
- **Performance disks** — hold a complete sound bank and, optionally, the operating system. There is
  no FAT or directory of any kind; the disk holds effectively two files (OS, bank), so their extent
  is positional. This is by far the more common type.

Both use the same physical layout.

## Physical layout — *confirmed*

From *Disk layout of Emulator II floppy disks* v1.2 by ///Esynthesist (emxp.net), reverse-engineered
from oscilloscope captures on the Shugart interface and cross-checked against the Software
Preservation Society's KryoFlux work. The geometry is **independently corroborated by E-mu's own
service manual** (see below) and by measured file sizes.

- DSDD soft-sectored 5.25" or 3.5" media, drive spins at 300 RPM
- **FM** encoding; 310 kbit/s including clock pulses, 155 kbit/s of data
- 2 sides, **80 tracks per side** → tracks addressed linearly `00`–`9F`
- **1 sector per track, 3584 bytes (0xE00) per sector**
- Track sync starts after the falling edge of the negative index pulse

Track structure:

| Field | Size | Value |
|-------|------|-------|
| GAP | 20 bytes | `FF` |
| SYNC | 4 bytes | `00` |
| IDAM | 2 bytes | `FA 96` |
| ID | 1 byte | track number, = cylinder × 2 + side |
| CRC of ID | 2 bytes | CRC-16, poly `8005`, init `0000`, final XOR `0000`; covers **only the ID byte**, stored **least significant byte first** |
| SYNC | 1 byte | `00` |
| GAP | 8 bytes | `FF` |
| SYNC | 4 bytes | `00` |
| Mark | 2 bytes | `FA 96` |
| Data | **3584 bytes** | sector payload |
| CRC of data | 2 bytes | same CRC-16; covers **only the 3584 data bytes**, least significant byte first |
| SYNC | 2 bytes | `00` |
| GAP | 20 bytes | `FF` |

A complete raw image is therefore `2 × 80 × 3584` = **573,440 bytes**, which matches every raw image
obtained (`zm325.emuiifd`, `emuiios.emuiifd`).

The address mark ambiguity is **resolved**: `FA 96` are two consecutive *data* bytes, not the
data/clock pair that an IBM-style FM mark would be. The Emulator II does not use missing-clock
address marks at all — every cell carries its clock pulse and the mark is just an unusual byte pair.

### Bit level encoding — *confirmed*

Derived from the OS 3.1 disk, which EMXP publishes as both a HFE and a raw sector image of the same
disk, and verified against every one of its 160 tracks. Exactly one interpretation reproduces the
raw image, so none of this is inferred:

- Each FM bit cell occupies **four bits** of the HFE bit-stream. The clock pulse is always present;
  the data pulse - the **last** of the four bits - is set for a one bit. In the stream a cell is
  therefore `1010` for a one and `0010` for a zero, and a whole track uses only the four byte values
  `AA`, `A2`, `2A` and `22`.
- Both the bits of the HFE stream and the decoded bytes are ordered **least significant bit first**.
- 80 stream bytes of `AA` are consequently the opening 20-byte `FF` gap, and 16 bytes of `22` are the
  4-byte `00` sync - which is how the framing was recognised in the first place.

The CRC follows from the same LSB-first transmission order: the specification's "direct CRC-16 with
polynomial 8005" applied to the bit stream as it is sent is, once bytes have been assembled, the
reflected form `A001` with initial value `0000`. It covers the payload only - the single ID byte, or
the 3584 data bytes - and never the marks or gaps.

### Corroboration from the E-mu service manual — *confirmed*

The Emulator II Service Manual (archive.org, OCR text) documents the debug monitor's disk primitives
and independently confirms the geometry from E-mu's side:

```
PUTDISK  P [track#] [address] [#tracks]     each track holds E00 bytes
GETDISK  G [track#] [address] [#tracks]     each track holds E00 bytes
         track# can be from 0 to 9F
         address can be from 0 0000 to 7 FFFF
```

`0xE00` = 3584 bytes per track; `0x9F` = 159, i.e. 160 tracks; the address range is the full 512 KB
of DRAM. Manual specifications also give "5¼" floppy diskettes, soft sectored, double sided, double
density, storage capacity approx. 500k bytes per diskette".

Critically, `GETDISK` loads whole tracks **directly into a linear RAM address**. The disk is a flat
memory image, not a filesystem. This is confirmed by inspection: the memory-test disk `zm325.emuiifd`
begins with Z80 code (`CD 7B 2A` = `CALL 2A7B`, …) and contains its UI strings inline (`Testing
Memory`, `Bank=A  Segmnt=1`, `Err BnkA`). It follows that a bank's on-disk structure *is* the OS's
in-memory data structure, so the logical layout can be recovered by locating tables in the image.

### OS extent — *confirmed*

`EMUIIOS31.E2O` (72,704 bytes) is byte-identical to the **first 72,704 bytes** of the OS disk image
`emuiios.emuiifd`, at offset 0. So the OS payload occupies `0x00000`–`0x11BFF`, spanning tracks 0–20
with track 20 only partly used. The bank then starts at the next track boundary but one, 78,848
(= track 22), which is confirmed below. A secondary source's bank end of 564,734 is not track-aligned
and does not reconcile with the geometry — treat it as unreliable.

## Image containers — *confirmed*

| Extension | Meaning |
|-----------|---------|
| `.hfe` | HxC Floppy Emulator container, 2,540,544 bytes for an EII disk |
| `.emuiifd` | HxC's extension for an EII **raw sector image**, 573,440 bytes |
| `.img` | same raw bytes as `.emuiifd` |
| `.EII` | a bank on its own, without the disk wrapper; how libraries circulate |
| `.E2O` | an OS image on its own (emxp.net publishes 2.1 / 2.3 / 3.0 / 3.1 / 2.6HD / 3.1HD) |

The HFE header of an EII disk, read across all 1,437 corpus files without a single exception:

| Field | Value |
|-------|-------|
| signature | `HXCPICFE` |
| format revision | 0 |
| number of tracks | 80 |
| number of sides | 2 |
| **track encoding** | **`0x03` = `HfeFile.ENCODING_EMU_FM`** |
| bit rate | 312 kbit/s |
| **floppy interface mode** | **`0x0B` = `HfeFile.FLOPPYMODE_EMU_SHUGART`** |

## What the code base already provides

`file/hfe` is a genuine asset: it parses HFE containers and has a bit-stream reader plus FM and MFM
decoders, already used by the Ensoniq, Fairlight, Akai MPC60/MPC2000 and Casio FZ detectors.
`HfeFile` even declares the two E-mu constants the corpus turns out to use.

The existing `FmDecoder` however **cannot** read an EII track. It implements IBM System 34 FM
specifically, and every assumption breaks:

| `FmDecoder` assumption | Emulator II reality |
|------------------------|---------------------|
| IDAM bit pattern `0xF57E` (data `FE`, clock `C7`) | mark is `FA 96` |
| Header is cylinder, head, sector, size-code | header is a single track-number byte |
| Sector size `128 << sizeCode` | fixed 3584, which is not `128 << n` for any n |
| CRC-CCITT poly `0x1021`, init `0xFFFF` (`AbstractDecoder.calculateCrc`) | poly `0x8005`, init `0x0000`, non-reflected |
| Many sectors per track, addressed by sector number | exactly one sector per track |

`HfeFile.decodeSectors()` used to route `ENCODING_EMU_FM` into that decoder, so feeding it an EII HFE
yielded zero sectors rather than an error. That is now fixed:

- **`file/hfe/EmuFmDecoder`** (new) decodes the cell stream, finds the `FA 96` marks, parses the
  one-byte header and returns the single 3584-byte sector per track.
- **`HfeFile.decodeSectors()`** routes `ENCODING_EMU_FM` to it; `ENCODING_ISOIBM_FM` still goes to
  `FmDecoder`, so the Ensoniq, Fairlight, Akai and Casio paths are untouched.
- **`AbstractDecoder.calculateCrcLsbFirst()`** (new) implements the E-mu CRC beside the existing
  CCITT one, which is left alone.
- **`Sector`** gained `createWithSize()` because 3584 cannot be expressed as `128 << sizeCode`;
  `getSizeBytes()` now returns a stored size which the existing constructor still fills with
  `128 << sizeCode`, so IBM-format callers are unaffected.

`DiskImageBuilder.buildImage (sectors, 80, 2, 1, 3584, true)` reassembles a raw image from the
result, since its LBA of `(cylinder × heads + head) × sectorsPerTrack + sector` reduces to exactly
the disk's own linear track number.

For the detector to come, `Emulator3Detector` is the template — it registers image extensions
alongside bank extensions — and `Emulator3FloppySet` shows how raw floppy images are assembled
before parsing.

## Test corpus — *in hand*

| Material | Location | Purpose |
|----------|----------|---------|
| 1,437 EII disk images (`.hfe`) | `EmulatorII-Library/` (untracked, 3.4 GB) | the corpus; foldered by content (strings, brass, drums, mellotron, foley, …) |
| `emuiios.emuiifd` + `emuiios31.hfe` | scratchpad, from emxp.net | **same disk as raw and HFE** — byte-exact decoder validation |
| `zm325.emuiifd` | scratchpad, from emxp.net | second raw image (memory-test disk) |
| `EMUIIOS31.E2O`, `EMUIIOS31HD.E2O` | scratchpad, from emxp.net | Z80 OS binaries — the parser oracle of last resort |
| 11,604 reference WAVs | archive.org item `EIIwaves` (889 MB, not downloaded) | **decoded ground truth for this exact corpus** |

Every corpus file is exactly 2,540,544 bytes with an identical header profile, so the set is
homogeneous and no per-file special-casing is expected.

The archive.org `EIIwaves` set is the decoded output of *these* disks: its files are named
`<disk>_EII_hfe_S<nn>.WAV`, and **1,427 of the 1,429 distinct disk names match the corpus exactly**
(`X_Men_EII.hfe` ↔ `X_Men_EII_hfe_S01.WAV`). It gives per-disk sample counts and per-sample audio to
check a bank parse against, without having to trust the reverse engineering.

Sources are public: the corpus is `EII_HXC.zip` from `dblondin.com/samples/`, the OS and memory-test
disks are from emxp.net, the WAV set is archive.org item `EIIwaves`.

## Bank layout — *partial, reverse engineered here*

Nothing public documents this. The ///Esynthesist specification stops at the track level; EMXP,
ChickenSys Translator, Awave and Arturia's Emulator II V are all closed source;
`mattetti/e-mu-soundbanks` handles Emulator X `.ebl` only, `mpc2emu` models the EII's *sound* but
never parses its disks, and `emu3bm` is EIII-only. What follows was derived from the corpus. Enough
is known to locate presets, voices and sample audio; the per-voice synthesis parameters are not
decoded yet.

### Where the bank starts — *confirmed*

**Offset `0x13400` (78,848) = track 22.** Comparing whole disks, tracks 0–21 are byte-identical
across the corpus (the OS) and the first block that differs anywhere is exactly `0x13400`. This
confirms the previously unverified second-hand figure. The bank region is therefore tracks 22–159 =
494,592 bytes.

### The working copy — *confirmed by structure*

The bank opens with the state of the currently selected preset:

| Offset | Size | Content |
|--------|------|---------|
| `bank+0x000` | 12 | name, blank on the disks examined |
| `bank+0x00C` | 61 | voice index per key - the EII has a **61-note keyboard** |
| `bank+0x049` | 61 | the same map biased by `0x9A`, i.e. as voice *identifiers* |
| `bank+0x086` | 61 | a per-key index which ascends within each voice's key span |
| `bank+0x2AB` | var | a preset record (see below) holding the selected preset |

Voice **identifier = voice index + `0x9A`**, so voice 1 is `0x9B`. A preset's voice list uses these
identifiers: a one-voice disk lists `9B`, and a twelve-voice disk lists `9B 9C … A6`.

### Voice records — *confirmed*

An array of **256-byte records** beginning at **`bank+0x5BA`**, which holds on 198 of 200 disks
sampled; the voice number stored in the key map indexes it one-based.

| Offset | Size | Content |
|--------|------|---------|
| `+0x00` | 12 | name, ASCII, space padded (`12string E  `) |
| `+0x0D` | 3 | **sample start**, 24-bit little-endian, **relative to the bank start** |
| `+0x10` | 3 | **slot size** - the fixed stride at which sample slots are allocated; consecutive voices' start addresses differ by exactly this |
| `+0x13` | 3 | **sample end** |
| `+0x16` | 3 | **loop length** |
| `+0x19` | 3 | **loop start**; equals the sample start where no loop is set |

The loop length is confirmed by pitch: for the six voices of `12 STRING GUITAR 1` it is 1342, 1509,
1700, 1980, 562 and 336 frames against measured pitch periods of 337, 252, 188, 142, 112 and 84 -
exactly 4, 6, 9, 14, 5 and 4 whole periods. A loop length which lands on an integer number of periods
for every voice is not a coincidence.

`+0x0C` and the parameter blocks from `+0x46` are not decoded. Two voices may share a start address,
which is how a 12-string's paired courses reuse one recording with different end points.

### Key maps and the root key — *confirmed*

The three 61-byte tables are read together. For key *k*: `bank+0x049` gives the **voice identifier**,
`bank+0x00C` a voice number for the display and `bank+0x086` a **transposition index** which ascends
by one per semitone. The value **`0x0E` marks unity**, so

Identifiers start at **`0x9B`** and follow the order of the voice records, so the record of a key is
simply `identifier - 0x9B`; an identifier below that means the key is silent. The table at
`bank+0x00C` looks like the same thing biased by a constant and is **not** usable to find a record:
its base differs from bank to bank - 1 on `12 STRING GUITAR 1`, 6 on `SLEIGH BELLS`, 60 on `DRUMS` -
while the identifiers of all three start at `0x9B`. Indexing by it happens to work on banks whose
base is 1 and silently reads sample data as a voice record on the rest.


```
root key = k - (transposition[k] - 0x0E)          MIDI note = key index + 26
```

A zone is a run of consecutive keys with the same voice whose transposition index increases by one;
where the index restarts inside a voice's span, the voice is mapped twice with different roots, which
is what the sampler does.

Both halves of the rule are confirmed against measured pitch. On `12 STRING GUITAR 1` the six voices
resolve to key 14, 19, 24, 29, 33 and 38 - intervals of 5, 5, 5, 4 and 5 semitones, which is exactly
standard guitar tuning - and with a base of 26 those are MIDI 40, 45, 50, 55, 59 and 64, i.e.
E2 A2 D3 G3 B3 E4. Voices whose name states their pitch confirm the base independently:
`CS 816 G2` resolves to MIDI 43, `CLAVINET F2` to 41, `CLAVINET F3` to 53 and `Piano TineG3` to 55,
each matching both its name and its measured pitch. Measured pitches run about 0.1 semitone sharp of
the nominal note throughout, which is a property of the instrument, not of the rule.

### Preset records — *confirmed signature, variable length*

Each preset begins with the nine byte signature `01 04 00 00 00 00 02 03 89` followed by a 12-byte
ASCII name. Records are variable length and packed one after another. Across the corpus the counts
are plausible throughout - 1 to 65 presets per disk, always within the documented limit of 100 - and
on 1,040 of 1,437 disks every signature is followed by twelve printable characters. 43 disks contain
no signature at all and may be a different bank revision; that is not yet explained.

### Validation of the model

- **Sample count.** The archive.org `EIIwaves` set is an independent extraction of these very disks.
  Counting *distinct* sample start addresses per disk - not voices, since voices may share a sample -
  reproduces its per-disk file count on **110 of 120** disks tested. The residual is traced to the
  ad-hoc chain-of-names record finder used for the test, not to the record model.
- **The addresses really are audio.** Reading `bank+start … bank+end` for the six voices of
  `12 STRING GUITAR 1` and running autocorrelation over each gives 82.4, 110.2, 147.8, 98.2, 248.0
  and 330.7 Hz at correlation peaks of r = 0.85 to 0.91 - **E2, A2, D3, G2, B3, E4**, standard guitar
  tuning, matching the voice names, and only correct because the sample rate really is 27,777 Hz.
  The same bytes read image-relative instead of bank-relative are not signal-like at all.

### The sample encoding — *confirmed*

The service manual settles it: "Each output channel consists of an input latch, (74HCT374) a DAC,
(6072)". The **AM6072 is a companding DAC**, so the expansion is done in hardware and the stored byte
is already a µ-255 code - which is what "8-bit companded" means on this machine. The byte is a
**sign in bit 7, a chord in bits 6 to 4 and a step in bits 3 to 0**, and

```
magnitude = ((step * 2 + 33) << chord) - 33          full scale 8031
```

Two independent checks confirm the shape. Decoding a voice and measuring the ratio of harmonic to
total energy gives 0.82 for the sign-plus-magnitude reading against 0.67 for a plain linear reading
and 0.54 for G.711 with its inverted bits. And a histogram of the stored magnitudes over four disks
steps *up* at each multiple of 16 instead of decaying - the signature of a segmented code, where a
code in the next chord covers twice the amplitude range - with all eight chords in use.

Note that the published `EIIwaves` extraction of these disks is **not** a per-sample decode of the
stored bytes: tabulating one against the other yields no function, and cross-correlating them over
±4000 frames finds no alignment at all, only self-similarity at the pitch period. It carries the same
notes and the same slot length, so it is useful for counting samples, but it cannot calibrate the
expansion and was not used to.

The absolute polarity of the sign bit is not verified; it is inaudible in a mono sample.

### The detector

`format/emu/emulator2/Emulator2Detector` reads `.img`, `.emuiifd`, `.eii` and `.hfe` and turns one
disk into one multi-sample: a zone per run of keys which share a voice and a rising transposition,
with the key range, root key, name and loop of each, and the audio expanded through the AM6072 law.

Measured over the whole corpus of 1,437 disks: **1,414 produce a multi-sample and none produce an
error**. Of the rest, some are continuation disks of a bank which is larger than one floppy - the
Emulator II+ holds 1 MB of samples and a floppy holds 494,592 bytes of bank - and those are reported
rather than silently skipped, because a voice whose audio lies past the end of the disk is a bank
that continues elsewhere, not a broken file. 251 disks report at least one such voice and still yield
their complete voices.

Spot check of `12 STRING GUITAR 1` converted to SFZ: six voices at roots 40, 45, 50, 55, 59 and 64 -
E2 A2 D3 G3 B3 E4 - with the low E additionally mapped an octave down over the bottom keys, which is
the transposition restart described above, and loops of 1342, 1509, 1700, 1980, 562 and 336 frames.
The exported audio measures 82.4, 110.7, 147.8, 196, 248.0 and 330.7 Hz at 27,777 Hz.

### Still to decode

The sample encoding above; loop enable; per-voice tuning and level; velocity ranges; the envelope,
filter and chorus settings; and the preset record layout beyond its signature and name. 43 disks
carry no preset signature at all and may be a different bank revision.

## Plan

1. ~~Obtain a corpus.~~ **Done** — see above.
2. ~~Physical layer.~~ **Done** — `EmuFmDecoder`, verified below.
3. **Logical layer.** *In progress* — see *Bank layout* above. The bank start, the voice records with
   their names and sample addresses, and the preset signature are established; the per-voice
   synthesis parameters are not. The `.E2O` binaries are the oracle of last resort — Z80 disassembly
   should be a fallback, not the first move.
4. **Sample decoding.** Expand µ-255 companded bytes to linear PCM at 27,777 Hz.
5. **Detector.** `format/emu/emulator2/`, registered in the `ConverterBackend` constructor, file
   endings `.eii`, `.img`, `.emuiifd`, `.hfe`. Read-only first.

### Validation strategy

Each layer has an objective check, so nothing rests on judgement:

- **Physical** — **passed.** Decoding `emuiios31.hfe` and rebuilding the image yields all 160 sectors
  with zero CRC failures and **573,440 bytes identical to `emuiios.emuiifd`**. Sweeping the whole
  corpus gives **1,437 of 1,437 files fully decoded, 229,920 sectors, zero CRC failures** (~26 s).
  The `FA 96` ambiguity was resolved the same way — of the 128 candidate bit interpretations exactly
  one reproduces the raw image, and it is the one documented above.
- **Logical** — **partially passed.** Distinct sample start addresses per bank reproduce the
  `EIIwaves` per-disk file count on 110 of 120 disks tested; autocorrelation of the addressed bytes
  returns the pitches the voice names promise. Repeat over all 1,427 matched disks once the record
  finder reads the voice table properly instead of chaining ASCII names.
- **Sample decode** — compare decoded audio against the corresponding reference WAV. A linear
  misreading of companded data is obvious both on a spectrum and in a sample-by-sample diff.

A creator is a separate question. Writing EII disks is feasible in principle (HxC supports E-mu
write), but a 17-second, 8-bit, 100-voice target is a narrow destination, and it would need an FM
track encoder to pair with the decoder.

## Sources

- ///Esynthesist, *Disk layout of Emulator II floppy disks* v1.2 — https://emxp.net/Disk_layout_of_EmulatorII_floppy_disks_v4.pdf
- EMXP additional downloads (OS images, memory-test disk) — https://emxp.net/Additional_downloads.htm and https://emxp.net/HXC.htm
- E-mu Emulator II Service Manual — https://archive.org/details/e-mu_Emulator_II_Service_Manual
- *E-mu Emulator II waveforms* (decoded ground truth) — https://archive.org/details/EIIwaves
- EII HFE corpus — http://www.dblondin.com/samples/EII_HXC.zip
- ChickenSys Translator, EII floppy image notes — http://www.chickensys.com/translator/documentation/floppyimageinfo/emue2.html
- Software Preservation Society / KryoFlux announcement — http://www.softpres.org/news:2010-10-10
- `mpc2emu` µ-255 profile (local clone) — `mpc2emu/processors/resampler.py`
