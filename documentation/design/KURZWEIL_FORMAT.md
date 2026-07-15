# Kurzweil K2000/K2500/K2600 File Format (.KRZ / .K25 / .K26)

Object files of the Kurzweil K2000, K2500 and K2600 samplers/synthesizers. All three devices share
one container format; the extension only signals the intended device family. A K2500 reads K2000
files, a K2600 reads both. Program objects may contain device specific segments (K2500 triple
modular processing, KDFX effects, KB3 organ mode) which older devices ignore or reject.

The layout was derived from the source code of the GPL tool *KurzFiler* by Marc Halbruegge
(https://sourceforge.net/projects/kurzfiler/), which reads and writes these files and whose output
loads on the hardware. All multi-byte values are **big-endian**. Sample positions are counted in
16-bit **words**, not bytes.

## File layout

```
offset size
0      4    magic "PRAM"
4      4    int32  objectSize - file offset where the raw sample data region starts
                   (= end of the object region)
8      24   6 x int32, mostly 0; the 3rd one (offset 16) holds the OS version,
                   e.g. 353 for 3.53 (written by KurzFiler, value is not essential)
32     ...  object blocks (see below) up to 'objectSize'
objectSize  raw sample data region: 16-bit big-endian PCM words, referenced by the
            sample headers via absolute word indices (byte pos = objectSize + 2 * index)
```

## Object blocks

The object region is a sequence of blocks:

```
int32 blockSize   negative: -(4 + paddedObjectLength); a value >= 0 terminates the list
                  (the terminator conventionally holds the file offset after itself,
                  i.e. 'objectSize', but readers only test for 'not negative')
object            see below, zero-padded to a 4-byte boundary
```

The next block starts at `blockPos + 4 + (-blockSize) - 4 = blockPos - blockSize`.

### Common object header

```
uint16 hash      object type and ID: for types <= 42: (type << 10) | id, id = 0..1023
                 (usable IDs on the device are 1..999)
uint16 size      total object size in bytes counted from the start of 'hash' (before padding)
uint16 nameOfs   offset from the start of this field to the object data
name             ASCII, 0-terminated, padded to even length (nameOfs = 2 + name field length;
                 max 16 characters on the device)
data             size - nameOfs - 4 bytes
```

Object types: 36 = program, 37 = keymap, 38 = sample. (Other types use an 8-bit type in the
hash: 111 = quick-access bank, 112 = song, 113 = effect; these are preserved verbatim but not
interpreted here.)

## Sample object (type 38)

```
int16  baseID       (KurzFiler writes 1)
int16  numHeaders   number of sample headers - 1
int16  headersOfs   offset from this field to the first header (always 8)
uint8  flags        1 = stereo: the headers form left/right pairs (even index = left)
uint8  unused1
int16  copyID
int16  unused2
numHeaders+1 x sample header (32 bytes each):
    uint8  rootKey        MIDI note of the recorded pitch
    uint8  flags          0x80 = loop OFF (inverted!), 0x40 = sample data present in this
                          file (otherwise the header references device ROM), KurzFiler
                          writes 0x70 for imported samples
    int8   volumeAdjust   (unit not confirmed, KurzFiler writes 0)
    int8   altVolumeAdjust
    int16  maxPitch       highest playable transposition in cents:
                          ceil(1200*log2(96000e-9*samplePeriod)) + 100*rootKey - 1200
    int16  offsetToName
    int32  sampleStart    absolute word index into the sample data region
    int32  altSampleStart alternative start (used when the Alt switch is on)
    int32  loopStart      absolute word index; the loop always ends at sampleEnd
    int32  sampleEnd      absolute word index, inclusive (length = end - start + 1)
    int16  offsetToEnvelope     byte offset from this field to the natural envelope
    int16  altOffsetToEnvelope  byte offset from this field to the alternative envelope
    int32  samplePeriod   sample period in nanoseconds (44100 Hz -> 22676)
natural envelope records, 12 bytes each (6 x int16); KurzFiler writes two default records
{-1, 1, 0, 0, -1600, 0} per sample object and points both envelope offsets of every header
at the first one
```

A sample object with several headers is a *multi-root* sample (one recording per root key).
For stereo samples every second header is the right channel of the preceding left one; a
keymap entry references the left header, the right one is implicit.

Non-looped samples: KurzFiler writes the loop flag as ON with `loopStart == sampleEnd`
(a degenerate 1-word loop that holds the last sample value); native one-shot samples have
flag bit 0x80 set instead. Readers should treat `loopStart >= sampleEnd` as "no loop".

## Keymap object (type 37)

```
int16  sampleID      sample object ID used for all entries when the method has no
                     per-entry sample ID (bit 0x02 clear, a "compacted" keymap)
int16  method        bit set describing the per-entry fields (in this order):
                     0x10 tuning as int16 (cents), else 0x08 tuning as int8
                     0x04 volume adjust as int8
                     0x02 sample ID as int16
                     0x01 sub-sample number as uint8
int16  basePitch     pitch of entry 0 in cents (0 = MIDI note 12)
int16  centsPerEntry key range covered per entry in cents (normally 100 = 1 semitone)
int16  entriesPerVel number of entries per velocity level - 1 (normally 127)
int16  entrySize     bytes per entry (must match the method bits)
int16  level[8]      for each of the 8 dynamic levels ppp..fff: byte offset from this
                     level field to the entry table of the velocity level to use;
                     writing: (8 - levelIndex) * 2 + tableIndex * tableSize
entry tables         numTables x (entriesPerVel+1) x entrySize
```

Entry fields (presence and order by the method bits): `tuning`, `volumeAdjust`, `sampleID`,
`subSampleNumber`. The sub-sample number is the 1-based index of the header inside the sample
object; 0 marks an unused entry. For stereo samples it references the left header (1, 3, 5, ...).

Key mapping: entry `i` responds to MIDI note `12 + (basePitch + i * centsPerEntry) / 100`.
With the standard values (basePitch 0, 100 cents, 128 entries) entry `i` = MIDI note `i + 12`
covering C-1..G9 of the keymap range C0..G10 in Kurzweil terms.

Playback pitch: `recordedPitch + (playedNote - rootKey) * 100 + tuning` cents, i.e. tuning 0
gives normal chromatic tracking of the header's root; a drum map cancels the tracking with
`tuning = 100 * (rootKey - playedNote)`.

The number of distinct entry tables is derived from the level offsets (adjacent equal values
share a table). The 8 dynamic levels map linearly onto MIDI velocity (level j covers
velocities j*16 .. j*16+15).

## Program object (type 36)

A sequence of segments, each a tag byte followed by a fixed-length data block. The object data
ends with a 0 word. Data length by tag: 8 (PGM) and 9 (LYR) = 15 bytes, 15 (FX) = 7 bytes,
otherwise by `tag & 0xF8`: 16/20 (ASR/LFO) = 7, 24 (FUN) = 3, 32 (ENC/ENV/IMP) = 15,
64 (CAL) = 31, 80 (output blocks) = 15, 104 (KDFX) = 7, 120 (KB3) = 31.

Segments used by KurzFiler generated programs (all other bytes 0):

* PGM (tag 8): `[0]` mode (2 = K2000, 3 = K2500, 4 = needs KDFX), `[1]` number of layers,
  `[3]` 0x37 bend range, `[4]` 64 portamento
* per layer:
  * LYR (tag 9): `[1]` 0x18, `[3]`/`[4]` low/high key, `[5]`/`[6]` 0/0x7F (velocity range),
    `[8]` enable flags: 0x04 mono, 0x24 stereo
  * ENC (tag 32): all 0 (envelope control, "not natural")
  * ENV (tag 33): amplitude envelope, `[1]` = 100, `[7]` = 100
  * CAL (tag 64): `[0]` 0x7F, `[1]` keymap transpose, `[3]` 0x2B, `[7..8]` keymap object ID
    (big-endian), `[11..12]` second keymap object ID (same as the first for generated
    programs), `[29]` 1
  * tags 0x50/0x51/0x52: `[0]` = 62/60/60 (output/pan blocks)
  * tag 0x53: `[0]` 1, `[2]` 0x70, `[13]` 4, `[14]` 0x90 for stereo panning, 0x00 for mono
```

K2500/K2600 only segments have `(tag & 96) == 96`; converting a program to K2000 strips them
and sets mode 2.

## Not interpreted / unknown

* The unit of the volume adjust fields (sample header and keymap entry) is not confirmed and
  therefore ignored.
* The natural envelope records and the program ENV/ASR/LFO/FUN segment contents beyond the
  values above are not decoded.
* Multi-floppy spanning files (.KR1/.K21 etc. part files) are not supported.
