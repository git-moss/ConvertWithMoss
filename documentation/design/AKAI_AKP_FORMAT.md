# Akai S5000/S6000/Z4/Z8 Program Format (.akp)

Based on *"Akai S5000 / S6000 Sampler AKP file specification"* by Seb Francis (worked out on an
S6000 with OS 1.11). Additions to the original specification are marked with *(obs.)*.

## General

* RIFF file with the form type `APRG`. All values are single bytes, signed values are two's
  complement; the only multi-byte value is *Velocity->Start* (16 bit, little-endian).
* The chunk order is fixed. Chunk IDs are not unique (2 x `lfo `, 3 x `env `, 4 x `zone`), so
  chunks are identified by their position.
* The RIFF size field is 0 in all files written by the S5000/S6000. Files written by the Z4/Z8
  and by PC software (WorldSounds, Roland S-50 library) contain the real size. Readers must
  ignore it.
* *(obs.)* The first data byte of every chunk is the **version of the chunk structure**. Newer
  versions extend some chunks by 2 bytes (see [Generations](#generations)).
* The program contains neither the root note, the sample start/end nor the loops. These are
  read from the WAV file (`smpl` chunk) which must be in the same folder and is referenced by
  its name without the `.wav` extension (max. 20 characters).
* Cells marked `–` are undocumented. Writers use the listed default value.

## Generations

The chunk versions found in the analysed files form 3 generations. Since there is no OS version
in the file, the file dates are the only time reference.

| Generation | prg | tune   | lfo    | kloc | filt | zone   | kgrp size | File dates  |
|------------|-----|--------|--------|------|------|--------|-----------|-------------|
| 1          | 1   | 1 (22) | 1 (12) | 1    | 1    | 1 (46) | 336       | 1998        |
| 2          | 1   | 1 (22) | 2 (14) | 2    | 2    | 2 (46) | 336       | 1999        |
| 3          | 2   | 2 (24) | 2 (14) | 2    | 2    | 3 (48) | 344       | 2000 - 2002 |
| mixed      | 1   | 1 (22) | 2 (14) | 1    | 1    | 1 (46) | 336       | 2002        |

Chunk sizes in brackets. `out`, `mods` and `env` are version 1 in all files.

### Detecting the model

* **Z4/Z8**: a `z48 ` chunk precedes the `prg ` chunk. It contains the Z-native program as
  nested chunks (`prg ` 10, `midi` 6, `mods` 193, `tune` 17, `pich` 10, `kgrp` 490 per
  keygroup; not decoded). The S5000/S6000 chunks of generation 3 follow for compatibility. The
  Z8 also loads files without the `z48 ` chunk (one factory file has none).
* **S5000/S6000**: no `z48 ` chunk. The generation indicates the OS age.

## File layout

| Chunk      | Offset Gen. 1 | Offset Gen. 3 | Data size Gen. 1 | Data size Gen. 3 |
|------------|---------------|---------------|------------------|------------------|
| `RIFF`     | 0x0000        | 0x0000        | 4 (`APRG`)       | 4 (`APRG`)       |
| `prg `     | 0x000C        | 0x000C        | 6                | 6                |
| `out `     | 0x001A        | 0x001A        | 8                | 8                |
| `tune`     | 0x002A        | 0x002A        | 22               | 24               |
| `lfo ` (1) | 0x0048        | 0x004A        | 12               | 14               |
| `lfo ` (2) | 0x005C        | 0x0060        | 12               | 14               |
| `mods`     | 0x0070        | 0x0076        | 38               | 38               |
| `kgrp` (1) | 0x009E        | 0x00A4        | 336              | 344              |
| `kgrp` (n) | + 344 each    | + 352 each    | 336              | 344              |

File size: Gen. 1 = 158 + 344 x keygroups, Gen. 3 = 164 + 352 x keygroups.

The `kgrp` chunk contains 9 sub-chunks (standard RIFF nesting). Offsets relative to the start
of the `kgrp` data:

| Sub-chunk     | Offset | Data size        |
|---------------|--------|------------------|
| `kloc`        | 0x00   | 16               |
| `env ` Amp    | 0x18   | 18               |
| `env ` Filter | 0x32   | 18               |
| `env ` Aux    | 0x4C   | 18               |
| `filt`        | 0x66   | 10               |
| `zone` 1 - 4  | 0x78   | 46 / 48 (Gen. 3) |

## Chunks

All offsets are relative to the start of the chunk data (after ID and size).

### prg - Program (6 bytes)

| Offset | Parameter           | Range         | Default |
|--------|---------------------|---------------|---------|
| 0x00   | Chunk version       | 1, 2          | 1 / 2   |
| 0x01   | MIDI program number | 0 = OFF, 1..  | 0       |
| 0x02   | Number of keygroups | 1 - 99        | 1       |
| 0x03   | –                   |               | 0       |
| 0x04   | –                   |               | 2       |
| 0x05   | –                   | *(obs.)* 0, 1 | 0       |

### out - Output (8 bytes)

| Offset | Parameter            | Range               | Default |
|--------|----------------------|---------------------|---------|
| 0x00   | Chunk version        | 1                   | 1       |
| 0x01   | Loudness             | 0 - 100             | 85      |
| 0x02   | Amp Mod 1            | -100 - 100 *(obs.)* | 0       |
| 0x03   | Amp Mod 2            | -100 - 100 *(obs.)* | 0       |
| 0x04   | Pan Mod 1            | -100 - 100 *(obs.)* | 0       |
| 0x05   | Pan Mod 2            | -100 - 100 *(obs.)* | 0       |
| 0x06   | Pan Mod 3            | -100 - 100 *(obs.)* | 0       |
| 0x07   | Velocity Sensitivity | -100 - 100          | 25      |

The specification lists 0 - 100 for the modulation amounts but negative values occur.

### tune - Tuning (22 / 24 bytes)

| Offset      | Parameter      | Range                | Default |
|-------------|----------------|----------------------|---------|
| 0x00        | Chunk version  | 1, 2                 | 1 / 2   |
| 0x01        | Semitone Tune  | -36 - 36             | 0       |
| 0x02        | Fine Tune      | -50 - 50             | 0       |
| 0x03 - 0x0E | Detune C ... B | -50 - 50 each        | 0       |
| 0x0F        | Pitchbend Up   | 0 - 24 semitones     | 2       |
| 0x10        | Pitchbend Down | 0 - 24 semitones     | 2       |
| 0x11        | Bend Mode      | 0 = NORMAL, 1 = HELD | 0       |
| 0x12        | Aftertouch     | -12 - 12             | 0       |
| 0x13        | –              | *(obs.)* 0, 1        | 0       |
| 0x14        | –              | *(obs.)* 0, 1        | 0 / 1   |
| 0x15        | –              | *(obs.)* 0, 1, 2, 4  | 0       |
| 0x16        | – (Gen. 3)     | *(obs.)* 0           | 0       |
| 0x17        | – (Gen. 3)     | *(obs.)* 0, 91, 121  | 0       |

### lfo - LFO 1 (12 / 14 bytes)

| Offset | Parameter     | Range                             | Default |
|--------|---------------|-----------------------------------|---------|
| 0x00   | Chunk version | 1, 2                              | 1 / 2   |
| 0x01   | Waveform      | see [LFO waveform](#lfo-waveform) | 1       |
| 0x02   | Rate          | 0 - 100                           | 43      |
| 0x03   | Delay         | 0 - 100                           | 0       |
| 0x04   | Depth         | 0 - 100                           | 0       |
| 0x05   | LFO Sync      | 0 = OFF, 1 = ON                   | 0       |
| 0x06   | –             |                                   | 1       |
| 0x07   | Modwheel      | 0 - 100                           | 15      |
| 0x08   | Aftertouch    | 0 - 100                           | 0       |
| 0x09   | Rate Mod      | -100 - 100                        | 0       |
| 0x0A   | Delay Mod     | -100 - 100                        | 0       |
| 0x0B   | Depth Mod     | -100 - 100                        | 0       |
| 0x0C   | – (Gen. 3)    |                                   | 0       |
| 0x0D   | – (Gen. 3)    |                                   | 0       |

### lfo - LFO 2 (12 / 14 bytes)

| Offset | Parameter      | Range                             | Default |
|--------|----------------|-----------------------------------|---------|
| 0x00   | Chunk version  | 1, 2                              | 1 / 2   |
| 0x01   | Waveform       | see [LFO waveform](#lfo-waveform) | 0       |
| 0x02   | Rate           | 0 - 100                           | 0       |
| 0x03   | Delay          | 0 - 100                           | 0       |
| 0x04   | Depth          | 0 - 100                           | 0       |
| 0x05   | –              |                                   | 1       |
| 0x06   | LFO Re-trigger | 0 = OFF, 1 = ON                   | 0       |
| 0x07   | –              |                                   | 0       |
| 0x08   | –              |                                   | 0       |
| 0x09   | Rate Mod       | -100 - 100                        | 0       |
| 0x0A   | Delay Mod      | -100 - 100                        | 0       |
| 0x0B   | Depth Mod      | -100 - 100                        | 0       |
| 0x0C   | – (Gen. 3)     |                                   | 0       |
| 0x0D   | – (Gen. 3)     |                                   | 5       |

#### LFO waveform

| Value | Waveform | Value | Waveform | Value | Waveform |
|-------|----------|-------|----------|-------|----------|
| 0     | SINE     | 3     | SQUARE+  | 6     | SAW UP   |
| 1     | TRIANGLE | 4     | SQUARE-  | 7     | SAW DOWN |
| 2     | SQUARE   | 5     | SAW BI   | 8     | RANDOM   |

### mods - Modulation sources (38 bytes)

*(obs.)* After a 4 byte header the chunk holds 17 pairs of *destination* and *source*. The
destination bytes are identical in all files and are written as listed. The specification only
names the sources.

| Offset | Destination (fixed)  | Source parameter      | Default source |
|--------|----------------------|-----------------------|----------------|
| 0x00   | Chunk version = 1    |                       |                |
| 0x01   | – = 0                |                       |                |
| 0x02   | Number of pairs = 17 |                       |                |
| 0x03   | – = 0                |                       |                |
| 0x04   | 2 (Amp)              | 0x05 Amp Mod 1 Src    | 6 (KEYBOARD)   |
| 0x06   | 2 (Amp)              | 0x07 Amp Mod 2 Src    | 3 (AFT'TOUCH)  |
| 0x08   | 1 (Pan)              | 0x09 Pan Mod 1 Src    | 8 (LFO2)       |
| 0x0A   | 1 (Pan)              | 0x0B Pan Mod 2 Src    | 6 (KEYBOARD)   |
| 0x0C   | 1 (Pan)              | 0x0D Pan Mod 3 Src    | 1 (MODWHEEL)   |
| 0x0E   | 4 (LFO 1 Rate)       | 0x0F LFO 1 Rate Src   | 6 (KEYBOARD)   |
| 0x10   | 5 (LFO 1 Delay)      | 0x11 LFO 1 Delay Src  | 6 (KEYBOARD)   |
| 0x12   | 3 (LFO 1 Depth)      | 0x13 LFO 1 Depth Src  | 6 (KEYBOARD)   |
| 0x14   | 7 (LFO 2 Rate)       | 0x15 LFO 2 Rate Src   | 0 (NO SOURCE)  |
| 0x16   | 8 (LFO 2 Delay)      | 0x17 LFO 2 Delay Src  | 0 (NO SOURCE)  |
| 0x18   | 6 (LFO 2 Depth)      | 0x19 LFO 2 Depth Src  | 0 (NO SOURCE)  |
| 0x1A   | 0 (Pitch)            | 0x1B Pitch Mod 1 Src  | 7 (LFO1)       |
| 0x1C   | 0 (Pitch)            | 0x1D Pitch Mod 2 Src  | 11 (AUX ENV)   |
| 0x1E   | 2 (Amp)              | 0x1F Amp Mod Src      | 5 (VELOCITY)   |
| 0x20   | 9 (Filter)           | 0x21 Filter Mod 1 Src | 5 (VELOCITY)   |
| 0x22   | 9 (Filter)           | 0x23 Filter Mod 2 Src | 8 (LFO2)       |
| 0x24   | 9 (Filter)           | 0x25 Filter Mod 3 Src | 10 (FILT ENV)  |

The program level modulation amounts are in the `out` and `lfo ` chunks, the keygroup level
amounts (pitch, amp, filter) in `kloc` and `filt`.

*(obs.)* The specification lists 9 (AMP ENV) as the default source of Filter Mod 3 but all
analysed files contain 10 (FILT ENV). Its amount (`filt` Mod Input 3) is the depth of the filter
envelope: the Z4/Z8 writes the same value to `filt` 0x07 and to the Filt Env Depth of the filter
envelope (`env` 0x09), the S5000/S6000 leaves `filt` 0x07 at 0. Readers must use the depth of the
filter envelope.

#### Modulation source

| Value | Source    | Value | Source   | Value | Source    |
|-------|-----------|-------|----------|-------|-----------|
| 0     | NO SOURCE | 5     | VELOCITY | 10    | FILT ENV  |
| 1     | MODWHEEL  | 6     | KEYBOARD | 11    | AUX ENV   |
| 2     | BEND      | 7     | LFO1     | 12    | dMODWHEEL |
| 3     | AFT'TOUCH | 8     | LFO2     | 13    | dBEND     |
| 4     | EXTERNAL  | 9     | AMP ENV  | 14    | dEXTERNAL |

### kloc - Keygroup location (16 bytes)

| Offset | Parameter     | Range                                       | Default |
|--------|---------------|---------------------------------------------|---------|
| 0x00   | Chunk version | 1, 2                                        | 1 / 2   |
| 0x01   | –             |                                             | 3       |
| 0x02   | –             |                                             | 1       |
| 0x03   | –             |                                             | 4       |
| 0x04   | Low Note      | 21 - 127                                    | 21      |
| 0x05   | High Note     | 21 - 127                                    | 127     |
| 0x06   | Semitone Tune | -36 - 36                                    | 0       |
| 0x07   | Fine Tune     | -50 - 50                                    | 0       |
| 0x08   | Override FX   | 0 = OFF, 1 = FX1, 2 = FX2, 3 = RV3, 4 = RV4 | 0       |
| 0x09   | FX Send Level | 0 - 100                                     | 0       |
| 0x0A   | Pitch Mod 1   | -100 - 100                                  | 100     |
| 0x0B   | Pitch Mod 2   | -100 - 100                                  | 0       |
| 0x0C   | Amp Mod       | -100 - 100                                  | 0       |
| 0x0D   | Zone Xfade    | 0 = OFF, 1 = ON                             | 0       |
| 0x0E   | Mute Group    | 0 = OFF, 1.. *(obs.)* 0 - 3                 | 0       |
| 0x0F   | –             | *(obs.)* Gen. 2/3: 0, 15, 128, 239          | 0       |

### env - Amp envelope (18 bytes)

| Offset | Parameter     | Range                  | Default |
|--------|---------------|------------------------|---------|
| 0x00   | Chunk version | 1                      | 1       |
| 0x01   | Attack        | 0 - 100                | 0       |
| 0x02   | –             |                        | 0       |
| 0x03   | Decay         | 0 - 100                | 50      |
| 0x04   | Release       | 0 - 100                | 15      |
| 0x05   | –             |                        | 0       |
| 0x06   | –             |                        | 0       |
| 0x07   | Sustain       | 0 - 100                | 100     |
| 0x08   | –             |                        | 0       |
| 0x09   | –             |                        | 0       |
| 0x0A   | Velo->Attack  | -100 - 100             | 0       |
| 0x0B   | –             |                        | 0       |
| 0x0C   | Keyscale      | -100 - 100             | 0       |
| 0x0D   | –             |                        | 0       |
| 0x0E   | On Vel->Rel   | -100 - 100             | 0       |
| 0x0F   | Off Vel->Rel  | -100 - 100             | 0       |
| 0x10   | –             |                        | 0       |
| 0x11   | –             | *(obs.)* random values | 0       |

### env - Filter envelope (18 bytes)

| Offset | Parameter      | Range      | Default |
|--------|----------------|------------|---------|
| 0x00   | Chunk version  | 1          | 1       |
| 0x01   | Attack         | 0 - 100    | 0       |
| 0x02   | –              |            | 0       |
| 0x03   | Decay          | 0 - 100    | 50      |
| 0x04   | Release        | 0 - 100    | 15      |
| 0x05   | –              |            | 0       |
| 0x06   | –              |            | 0       |
| 0x07   | Sustain        | 0 - 100    | 100     |
| 0x08   | –              |            | 0       |
| 0x09   | Filt Env Depth | -100 - 100 | 0       |
| 0x0A   | Velo->Attack   | -100 - 100 | 0       |
| 0x0B   | –              |            | 0       |
| 0x0C   | Keyscale       | -100 - 100 | 0       |
| 0x0D   | –              |            | 0       |
| 0x0E   | On Vel->Rel    | -100 - 100 | 0       |
| 0x0F   | Off Vel->Rel   | -100 - 100 | 0       |
| 0x10   | –              |            | 0       |
| 0x11   | –              |            | 0       |

### env - Aux envelope (18 bytes)

| Offset | Parameter       | Range                              | Default |
|--------|-----------------|------------------------------------|---------|
| 0x00   | Chunk version   | 1                                  | 1       |
| 0x01   | Rate 1          | 0 - 100                            | 0       |
| 0x02   | Rate 2          | 0 - 100                            | 50      |
| 0x03   | Rate 3          | 0 - 100                            | 50      |
| 0x04   | Rate 4          | 0 - 100                            | 15      |
| 0x05   | Level 1         | 0 - 100                            | 100     |
| 0x06   | Level 2         | 0 - 100                            | 100     |
| 0x07   | Level 3         | 0 - 100                            | 100     |
| 0x08   | Level 4         | 0 - 100                            | 0       |
| 0x09   | –               |                                    | 0       |
| 0x0A   | Vel->Rate 1     | -100 - 100                         | 0       |
| 0x0B   | –               |                                    | 0       |
| 0x0C   | Keyboard->R2/R4 | -100 - 100                         | 0       |
| 0x0D   | –               |                                    | 0       |
| 0x0E   | Vel->Rate 4     | -100 - 100                         | 0       |
| 0x0F   | Off Vel->Rate 4 | -100 - 100                         | 0       |
| 0x10   | Vel->Out Level  | -100 - 100                         | 0       |
| 0x11   | –               | spec: 0x85, *(obs.)* random values | 0       |

### filt - Filter (10 bytes)

| Offset | Parameter      | Range                             | Default |
|--------|----------------|-----------------------------------|---------|
| 0x00   | Chunk version  | 1, 2                              | 1 / 2   |
| 0x01   | Filter Mode    | see [Filter mode](#filter-mode)   | 0       |
| 0x02   | Cutoff Freq    | 0 - 100                           | 100     |
| 0x03   | Resonance      | 0 - 12                            | 0       |
| 0x04   | Keyboard Track | -36 - 36                          | 0       |
| 0x05   | Mod Input 1    | -100 - 100                        | 0       |
| 0x06   | Mod Input 2    | -100 - 100                        | 0       |
| 0x07   | Mod Input 3    | -100 - 100                        | 0       |
| 0x08   | Headroom       | 0 = 0 dB, 1 = 6 dB, ... 5 = 30 dB | 0       |
| 0x09   | –              | *(obs.)* Gen. 1: 0, Gen. 2/3: 5   | 0 / 5   |

#### Filter mode

| Value | Mode       | Value | Mode       | Value | Mode      |
|-------|------------|-------|------------|-------|-----------|
| 0     | 2-POLE LP  | 9     | LO<>HI     | 18    | PEAK 2    |
| 1     | 4-POLE LP  | 10    | LO<>BAND   | 19    | PEAK 3    |
| 2     | 2-POLE LP+ | 11    | BAND<>HI   | 20    | WIDE PEAK |
| 3     | 2-POLE BP  | 12    | NOTCH 1    | 21    | BI-PEAK   |
| 4     | 4-POLE BP  | 13    | NOTCH 2    | 22    | PHASER 1  |
| 5     | 2-POLE BP+ | 14    | NOTCH 3    | 23    | PHASER 2  |
| 6     | 1-POLE HP  | 15    | WIDE NOTCH | 24    | BI-PHASE  |
| 7     | 2-POLE HP  | 16    | BI-NOTCH   | 25    | VOWELISER |
| 8     | 1-POLE HP+ | 17    | PEAK 1     |       |           |

### zone - Velocity zone (46 / 48 bytes), 4 per keygroup

| Offset      | Parameter          | Range                                | Default |
|-------------|--------------------|--------------------------------------|---------|
| 0x00        | Chunk version      | 1, 2, 3                              | 1 / 3   |
| 0x01        | Sample name length | 0 - 20, 0 = no sample assigned       | 0       |
| 0x02 - 0x15 | Sample name        | ASCII, padded with 0x00              | 0       |
| 0x16 - 0x21 | –                  |                                      | 0       |
| 0x22        | Low Velocity       | 0 - 127                              | 0       |
| 0x23        | High Velocity      | 0 - 127                              | 127     |
| 0x24        | Fine Tune          | -50 - 50                             | 0       |
| 0x25        | Semitone Tune      | -36 - 36                             | 0       |
| 0x26        | Filter             | -100 - 100                           | 0       |
| 0x27        | Pan/Balance        | -50 - 50 = L50 - R50                 | 0       |
| 0x28        | Playback           | see [Playback](#playback)            | 4 / 6   |
| 0x29        | Output             | see [Output](#output)                | 0       |
| 0x2A        | Zone Level         | -100 - 100                           | 0       |
| 0x2B        | Keyboard Track     | 0 = OFF, 1 = ON                      | 1       |
| 0x2C - 0x2D | Velocity->Start    | -9999 - 9999 (16 bit, little-endian) | 0       |
| 0x2E        | – (Gen. 3)         | *(obs.)* 0                           | 0       |
| 0x2F        | – (Gen. 3)         | *(obs.)* 0, 113, 130                 | 0       |

Unused zones have a sample name length of 0. Generation 1 writes them with 0 apart from
version, Playback = 4 and Keyboard Track = 1. Generation 3 writes version 3, Playback = 6,
Keyboard Track = 1 and High Velocity = 0, but leaves memory garbage in the unused bytes
(e.g. 0x02 - 0x21). Readers must only rely on the name length.

#### Playback

| Value | Zone version 1     | Zone version 2/3 *(obs.)* |
|-------|--------------------|---------------------------|
| 0     | NO LOOPING         | NO LOOPING                |
| 1     | ONE SHOT           | ONE SHOT                  |
| 2     | LOOP IN REL        | LOOP IN REL               |
| 3     | LOOP UNTIL REL     | LOOP UNTIL REL            |
| 4     | AS SAMPLE          | unknown                   |
| 5     | –                  | unknown                   |
| 6     | *(obs.)* AS SAMPLE | AS SAMPLE                 |

Since zone version 2 the value 6 is used by all unused zones and most used zones (like 4 in
version 1), hence it is AS SAMPLE. 4 and 5 were not found.

*(obs.)* Files created by PC software (Roland XV library) contain 6 in version 1 zones as well.
Readers treat it as AS SAMPLE in all versions.

#### Output

| Value  | Output                           |
|--------|----------------------------------|
| 0      | MULTI                            |
| 1 - 8  | stereo pairs 1/2, 3/4, ... 15/16 |
| 9 - 24 | mono outputs 1 - 16              |

## AKM - Multi (.akm)

Not required for writing programs; documented as far as known. RIFF form type `AMUL`, RIFF
size 0 (S6000) or the real size (Z8).

| Chunk  | Size | Version | Content                                                    |
|--------|------|---------|------------------------------------------------------------|
| `z48 ` | var. | –       | Z4/Z8 only: Z-native multi (nested `mult`, `qlnk`, ...)    |
| `mpc ` | 114  | –       | Found in 1 Z8 file, not decoded                            |
| `mver` | 4    | 1       | Always `01 20 xx 00`; xx = 0 - 28, not the number of parts |
| `fx  ` | 530  | 2       | Effects, not decoded                                       |
| `part` | 48   | 3       | Part, always 32                                            |

Order S6000: `fx  `, 32 x `part`, `mver`. Order Z8: `z48 `, (`mpc `), `mver`, `fx  `, 32 x `part`.
`mver` does not identify model or OS: S6000 and Z8 files contain the same `01 20`.

### part - Part (48 bytes)

| Offset      | Parameter           | Range                                           |
|-------------|---------------------|-------------------------------------------------|
| 0x00        | Chunk version       | 3                                               |
| 0x01        | Program name length | 0 - 32, 0 = unused part                         |
| 0x02 - 0x21 | Program name        | ASCII, padded with 0x00                         |
| 0x22        | MIDI channel        | 0 - 31 = 1A - 16A, 1B - 16B                     |
| 0x23        | Pan                 | -50 - 50                                        |
| 0x24        | Low Key             | 0 - 127                                         |
| 0x25        | High Key            | 0 - 127                                         |
| 0x26        | Volume              | 0 - 100 (-60 - +6 dB)                           |
| 0x27 - 0x2F | –                   | not decoded, *(obs.)* 0x2A: 0 - 4, 0x2B: 0 - 60 |
