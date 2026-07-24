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
    int8   volumeAdjust   volume adjust in 0.5 dB steps (-64.0 to +63.5 dB, the range of
                          the Volume Adjust parameter on the MISC page of the sample
                          editor in the K2600 manual)
    int8   altVolumeAdjust volume adjust used when the alternative start is active
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

The parameter bytes of the layer segments below were reverse-engineered by the mpc2emu
project (https://github.com/mpc2emu) by editing single parameters of the K2000 ROM default
program on a K2000R, disk-saving each variant and diffing the files; the calibration points
mentioned below were confirmed against the device display and recordings. Segments and bytes
which are not listed are 0 in generated programs.

* PGM (tag 8): `[0]` mode (2 = K2000, 3 = K2500, 4 = needs KDFX), `[1]` number of layers,
  `[3]` 0x37 bend range, `[4]` 64 portamento
* per layer:
  * LYR (tag 9): `[1]` 0x18, `[3]`/`[4]` low/high key, `[5]` velocity window, `[6]` layer
    enable control source (0x7F = always on - this is *not* the high velocity), `[8]` enable
    flags: 0x04 mono, 0x24 stereo. The velocity window packs the low and high velocity as two
    0..7 dynamic marks (ppp..fff, mark j covers velocities j*16..j*16+15): the low mark in
    bits 3-5 and the high mark *inverted* (7 - mark) in bits 0-2, so a full range is 0.
  * ENC (tag 32): envelope control; `[1]` amplitude envelope mode: 1 = the 'natural' envelope
    of the samples is active (the ENV values are ignored by the device), 0 = user envelope
  * ENV (tag 33): the amplitude envelope; ENV2 (tag 34): the filter envelope; ENV3 (tag 35):
    the pitch envelope. All three hold 7 (time, level) byte pairs from byte 0 - 3 attack
    stages, 1 decay stage and 3 release stages, each with a duration and a target level -
    byte `[14]` is a loop flag. A time byte holds the number of steps on the non-linear
    editor time grid plus 3 (grid: 0-2s in 0.02s steps, 2-5s in 0.04s, 5-10s in 0.10s,
    10-15s in 0.5s, 15-25s in 1s, 25-60s in 5s); a level byte is a signed percentage.
  * CAL (tag 64): `[0]` 0x7F, `[1]` keymap transpose, `[3]` 0x2B, `[7..8]` a *second* keymap
    slot which must stay 0 - writing the keymap ID into both slots makes the layer claim two
    keymaps, which overflows the device at 4 or more layers and mutes the whole program -
    `[11..12]` keymap object ID (big-endian), `[21]`/`[22]` pitch modulation source/depth
    (control source 114 = LFO1 for vibrato), `[29]` DSP algorithm number
  * F1 page (tag 0x50): `[0]` DSP function type of the F1 slot (the filter, see below),
    `[1]` filter cutoff as signed semi-tones: Hz = 440 * 2^((byte - 9) / 12), device range
    -48 (16 Hz) to 79 (25088 Hz), `[5]` cutoff modulation source (121 = ENV2, the filter
    envelope), `[6]` its signed depth: piece-wise linear through the calibration points
    0 = 0 cents, 40 = 1200 cents, 127 = 10800 cents
  * F2 page (tag 0x51): `[0]` function type: 16 = resonance, 61 = none, `[1]` the resonance
    in 0.5 dB steps (0..48 = 0..24 dB); for the 2-pole bandpass the value is the bandwidth
    instead (64 is a typical medium width)
  * F3 page (tag 0x52): `[0]` function type: 18 = separation, 60 = none
  * F4/amplifier page (tag 0x53): `[0]` 1, `[2]` 0x70, `[13]` 4, `[14]` 0x90 for stereo
    panning, 0x00 for mono
  * LFO (tag 0x14): `[2]` rate = 26 + 10 * Hz, `[4]` shape (0 = sine, 2 = square,
    4 = triangle, 6 = rising saw, 8 = falling saw, 20 = 8-step), `[5]` phase = 1 + degrees/45
    (not converted, see below)

The filter is spread over the DSP algorithm (CAL `[29]`) and the function types of the F1-F3
pages:

| Filter                    | Algorithm | F1 `[0]` | F2 `[0]` | F3 `[0]` | Resonance |
|---------------------------|-----------|----------|----------|----------|-----------|
| 1-pole low-pass (6dB)     | 16        | 15       | 61       | 18       | fixed     |
| 2-pole low-pass (12dB)    | 5         | 2        | 16       | 60       | yes       |
| 2-pole bandpass           | 5         | 3        | 16       | 60       | width     |
| 4-pole low-pass (24dB)    | 1         | 50       | 16       | 18       | yes       |
| 4-pole high-pass          | 1         | 54       | 16       | 18       | yes       |
| 4-pole twin-peaks bandpass| 1         | 55       | 16       | 18       | yes       |
| 4-pole double notch       | 1         | 56       | 16       | 18       | yes       |
| none                      | 1         | 62       | -        | -        | -         |

K2500/K2600 only segments have `(tag & 96) == 96`; converting a program to K2000 strips them
and sets mode 2.

## Not interpreted / unknown

* The unit of the keymap entry volume adjust field is not confirmed and therefore ignored
  (the sample header volume adjust is confirmed to be 0.5 dB steps, see above).
* The LFO segments and the vibrato routing (CAL `[21]`/`[22]`) are decoded (see above) but
  not converted since the internal model has no LFO representation yet. The pitch envelope
  (ENV3) is not converted since the control source code which routes it to the pitch is not
  confirmed. The ASR and FUN segments and the natural envelope records are not decoded.
* The per-sample playback direction (Normal/Reverse/Bidirectional on the MISC page of the
  sample editor) is stored somewhere in the sample header but the bit positions are unknown.
* Multi-floppy spanning files (.KR1/.K21 etc. part files) are not supported.
