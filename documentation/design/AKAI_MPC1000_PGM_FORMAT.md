# Akai MPC500/1000/2500 Program Format (.pgm)

## General

The MPC500, MPC1000 and MPC2500 share one drum program format. A program is a fixed size binary file
of **10756 bytes** (0x2A04) with 64 pads. Each pad holds up to 4 samples (layers) plus the pad
parameters (envelope, filters, LFO, mixer). The samples are stored as separate WAV files in the same
folder; the program only references them by name.

All values are little-endian. Offsets in the pad tables are relative to the start of the pad (see
[File layout](#file-layout)).

The format is a direct memory dump: the save routine of the firmware copies the magic string into the
program in RAM and writes the whole block (its length is taken from the first 2 bytes) to the file.
Therefore unknown bytes are not padding but fields of the program in memory, which are 0 if unused.

## Versions

| Device / OS    | Magic              | LFO |
|----------------|--------------------|-----|
| MPC1000 OS 1.x | `MPC1000 PGM 1.00` | No  |
| MPC1000 OS 2.x | `MPC1000 PGM 1.00` | Yes |
| MPC2500        | `MPC1000 PGM 1.01` | Yes |
| MPC500         | `MPC1000 PGM 1.00` | No  |

* The LFO was added with the MPC1000 OS 2.0 ("Added LFOs" in the release notes). The file format
  stayed the same; the LFO parameters are stored in 9 of the 14 pad bytes (0x81..0x8E) which were
  unused in OS 1.x and are therefore documented as padding by Norum. Files written by OS 1.x or by programs which do
  not know the LFO (e.g. Awave Studio) contain 0 in these bytes, which means that the LFO is off.
* The MPC2500 writes the magic `MPC1000 PGM 1.01`. The firmware contains no other version string
  (neither `1.00` nor any other PGM magic) and the LFO page uses the identical offsets, pad size
  (0xA4) and program layout. No further differences were found. Since the magic string is
  referenced only by the save routine, the loaders apparently do not compare it, which is why the
  devices can exchange their programs.
* The MPC500 firmware has no LFO page (no LFO waveform names, no LFO parameter labels) and writes
  `MPC1000 PGM 1.00`. It has only an amplitude envelope. Its filter supports the types Off,
  Low-pass, Band-pass and High-pass (Low-pass only in older OS versions).
* A reader should accept both magics. Programs of the JJOS operating systems (third party) are
  written in the same format; JJOS2XL uses the last byte of the sample record for the 'NDC Chop'
  setting.

## File layout

| Offset | Size     | Description                                  |
|--------|----------|----------------------------------------------|
| 0x0000 | 24       | Header                                       |
| 0x0018 | 64 x 164 | 64 pads, pad *p* starts at `0x18 + p * 0xA4` |
| 0x2918 | 64       | MIDI note of each pad                        |
| 0x2958 | 128      | Pad of each MIDI note                        |
| 0x29D8 | 1        | MIDI program change                          |
| 0x29D9 | 2 x 13   | 2 sliders                                    |
| 0x29F3 | 17       | Unused (0)                                   |

The firmware calculates the pointer to the current pad exactly like this: `program + 24 + pad * 0xA4`.

### Header (24 bytes)

| Offset | Size | Type   | Description | Values                                                              |
|--------|------|--------|-------------|---------------------------------------------------------------------|
| 0x00   | 2    | uint16 | File size   | 0x2A04 (10756)                                                      |
| 0x02   | 2    |        | Unused      | 0                                                                   |
| 0x04   | 16   | ASCII  | Magic       | `MPC1000 PGM 1.00` or `MPC1000 PGM 1.01`, see [Versions](#versions) |
| 0x14   | 4    |        | Unused      | 0                                                                   |

### Pad (164 bytes)

A pad starts with 4 sample records (layers) of 24 bytes each, followed by the pad parameters.

#### Sample (24 bytes), sample *s* at pad offset `s * 0x18`

| Offset | Size | Type  | Description                | Values                                                                                           |
|--------|------|-------|----------------------------|--------------------------------------------------------------------------------------------------|
| 0x00   | 16   | ASCII | Sample name                | Name of the WAV file without extension, padded with 0. Empty if the layer is not used            |
| 0x10   | 1    |       | Unused                     | 0                                                                                                |
| 0x11   | 1    | uint8 | Level                      | 0..100                                                                                           |
| 0x12   | 1    | uint8 | Velocity range low         | 0..127                                                                                           |
| 0x13   | 1    | uint8 | Velocity range high        | 0..127                                                                                           |
| 0x14   | 2    | int16 | Tuning                     | -3600..3600 cents                                                                                |
| 0x16   | 1    | uint8 | Play mode                  | 0 = One Shot, 1 = Note On                                                                        |
| 0x17   | 1    | uint8 | Velocity to pitch (likely) | 'V>Pitch' of the Pitch Modulation window, range unknown. 0 in all test files (JJOS2XL: NDC Chop) |

Note: the Python struct description in Norum's document lists 'Range Upper' before 'Range Lower',
but his table and all test files show that the lower value comes first.

#### Pad parameters (68 bytes)

| Offset | Size | Type   | Description                                                     | Values                                                                                                                                                 |
|--------|------|--------|-----------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0x60   | 2    | int16? | Unknown                                                         | 0 in most files; Akai factory programs contain signed values (e.g. -41, 12, -20). Not related to the LFO                                               |
| 0x62   | 1    | uint8  | Voice overlap                                                   | 0 = Poly, 1 = Mono                                                                                                                                     |
| 0x63   | 1    | uint8  | Mute group                                                      | 0 = Off, 1..32                                                                                                                                         |
| 0x64   | 1    |        | Unused                                                          | 0                                                                                                                                                      |
| 0x65   | 1    | uint8  | Overlap x-fade                                                  | 'Overlap x-fade' of the Amp Envelope window, 1 in all test files                                                                                       |
| 0x66   | 1    | uint8  | Attack                                                          | 0..100                                                                                                                                                 |
| 0x67   | 1    | uint8  | Decay                                                           | 0..100                                                                                                                                                 |
| 0x68   | 1    | uint8  | Decay mode                                                      | 0 = End, 1 = Start                                                                                                                                     |
| 0x69   | 1    | uint8  | Velocity to attack                                              | 'V>Attack': the larger the value, the slower the attack depending on the velocity, 0 = constant                                                        |
| 0x6A   | 1    | uint8  | Velocity to start                                               | 'V>Start': the larger the value, the later the sample start depending on the velocity, 0 = constant                                                    |
| 0x6B   | 1    | uint8  | Velocity to level                                               | 'V>Level': 0 = maximum level regardless of the velocity, 0..100                                                                                        |
| 0x6C   | 5    |        | Unused                                                          | 0                                                                                                                                                      |
| 0x71   | 1    | uint8  | Filter 1 type                                                   | 0 = Off, 1 = Low-pass, 2 = Band-pass, 3 = High-pass, 4 = Low-pass 2                                                                                    |
| 0x72   | 1    | uint8  | Filter 1 frequency                                              | 0..100                                                                                                                                                 |
| 0x73   | 1    | uint8  | Filter 1 resonance                                              | 0..100                                                                                                                                                 |
| 0x74   | 1    | uint8  | Filter 1 envelope time                                          | 0..100, see [Filter envelope](#filter-envelope)                                                                                                        |
| 0x75   | 1    | int8   | Filter 1 envelope amount                                        | -50..50                                                                                                                                                |
| 0x76   | 1    | int8   | Filter 1 envelope velocity to time                              | 'V>Time'                                                                                                                                               |
| 0x77   | 1    | int8   | Filter 1 envelope velocity to amount                            | 'V>Amount'                                                                                                                                             |
| 0x78   | 1    | uint8  | Filter 1 velocity to frequency                                  | 'V>Freq', 0..100                                                                                                                                       |
| 0x79   | 1    | uint8  | Filter 2 type                                                   | 0 = Off, 1 = Low-pass, 2 = Band-pass, 3 = High-pass, 4 = Link                                                                                          |
| 0x7A   | 1    | uint8  | Filter 2 frequency                                              | 0..100                                                                                                                                                 |
| 0x7B   | 1    | uint8  | Filter 2 resonance                                              | 0..100                                                                                                                                                 |
| 0x7C   | 1    | uint8  | Filter 2 envelope time                                          | 0..100                                                                                                                                                 |
| 0x7D   | 1    | int8   | Filter 2 envelope amount                                        | -50..50                                                                                                                                                |
| 0x7E   | 1    | int8   | Filter 2 envelope velocity to time                              | 'V>Time'                                                                                                                                               |
| 0x7F   | 1    | int8   | Filter 2 envelope velocity to amount                            | 'V>Amount'                                                                                                                                             |
| 0x80   | 1    | uint8  | Filter 2 velocity to frequency                                  | 'V>Freq', 0..100                                                                                                                                       |
| 0x81   | 1    |        | Unused                                                          | 0                                                                                                                                                      |
| 0x82   | 9    |        | **LFO**, see [LFO](#lfo-version-100-with-os-2x-and-version-101) | 0 if written by OS 1.x                                                                                                                                 |
| 0x8B   | 3    |        | Unused                                                          | 0                                                                                                                                                      |
| 0x8E   | 1    | uint8  | Unknown                                                         | 0 or 1 in some Akai factory programs                                                                                                                   |
| 0x8F   | 1    | uint8  | Mixer level                                                     | 0..100                                                                                                                                                 |
| 0x90   | 1    | uint8  | Mixer pan                                                       | 0..49 = left, 50 = center, 51..100 = right                                                                                                             |
| 0x91   | 1    | uint8  | Output                                                          | 0 = Stereo, 1 = 1-2, 2 = 3-4                                                                                                                           |
| 0x92   | 1    | uint8  | FX send                                                         | 0 = Off, 1 = FX 1, 2 = FX 2                                                                                                                            |
| 0x93   | 1    | uint8  | FX send level                                                   | 0..100                                                                                                                                                 |
| 0x94   | 1    | uint8  | Filter attenuation                                              | 0 = 0dB, 1 = -6dB, 2 = -12dB                                                                                                                           |
| 0x95   | 2    |        | Unused                                                          | 0                                                                                                                                                      |
| 0x97   | 4    | uint8  | Mute targets                                                    | Up to 4 pads which are stopped when this pad is played ('Mute target' of the PARAMS page). The exact encoding (pad number, value for 'OFF') is unknown |
| 0x9B   | 9    |        | Unused                                                          | 0                                                                                                                                                      |

#### Filter envelope

The MPC1000 and MPC2500 have a simple filter envelope for each filter, set in the Filter Envelope
window (press WINDOW on the Type, Freq or Reso field of the FILTER page). The bytes are 8 bytes per
filter starting at 0x71 (filter 1) and 0x79 (filter 2): type, frequency, resonance, envelope time,
envelope amount, velocity to time, velocity to amount and velocity to frequency.

* **Amount**: the cutoff starts at the cutoff frequency plus (positive values) or minus (negative
  values) the amount and goes back to the cutoff frequency within the envelope time.
* **Time**: the time from the start (cutoff plus amount) back to the cutoff frequency, 0..100.
* **V>Time**: positive values make the time closer to the set time with higher velocity, negative
  values the opposite, 0 = constant.
* **V>Amount**: positive values make the start frequency closer to the amount with higher velocity,
  negative values the opposite, 0 = constant.
* **V>Freq**: the higher the velocity, the higher the cutoff frequency.

The window shows the ranges 0..100 for the time and -50..50 for the amount. The MPC500 has no
filter envelope.

#### LFO (version 1.00 with OS 2.x and version 1.01)

Found in the LFO page of the MPC1000 OS 2.13 and MPC2500 OS 1.24 firmware: the edit and display
functions of the parameters 'Wave', 'Rate', 'Delay', 'Pitch', 'Filter1', 'Filter2' and 'Level' read
these offsets from the current pad pointer. Both firmware versions use identical offsets.

| Offset | Size | Type  | Description     | Values                                                                                                                                                                                                                |
|--------|------|-------|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0x82   | 2    | int16 | Rate            | Length of one LFO cycle in ticks (96 ticks = 1 beat). Displayed as beats.ticks (`value / 96`, `value % 96`), e.g. 96 = 01.00 (1 beat), 48 = 00.48 (1/8 note). Range 00.00..99.00. The rate follows the sequence tempo |
| 0x84   | 2    | int16 | Delay           | Time before the LFO starts in ticks, same encoding as the rate. Range 00.00..99.00                                                                                                                                    |
| 0x86   | 1    | uint8 | Waveform        | 0 = Triangle, 1 = Sine, 2 = Square, 3 = Saw, 4 = Saw Down, 5 = Random                                                                                                                                                 |
| 0x87   | 1    | uint8 | Pitch amount    | 0..100 (vibrato)                                                                                                                                                                                                      |
| 0x88   | 1    | uint8 | Filter 1 amount | 0..100 (cutoff of filter 1)                                                                                                                                                                                           |
| 0x89   | 1    | uint8 | Filter 2 amount | 0..100 (cutoff of filter 2)                                                                                                                                                                                           |
| 0x8A   | 1    | uint8 | Level amount    | 0..100 (tremolo)                                                                                                                                                                                                      |

Notes:

* The waveform order is taken from the string table of the firmware (entries of 11 bytes, indexed
  by the value): `TRIANGLE`, `SINE`, `SQUARE`, `SAW`, `SAW DOWN`, `RANDOM`.
* The rate is converted to a frequency with `Hz = BPM / 60 * 96 / rate`, e.g. 1.00 at 120 BPM =
  2 Hz. A rate of 0 does not create a modulation.
* The operator's manual also lists a 'Pan' field, but neither firmware has a parameter for it.
  The manual lists only one 'Filter' amount while the firmware has one for each filter.
* The JJOS manual states that only one of the LFO destinations works at the same time. It is
  unknown if this also applies to the original Akai OS.
* The depth of the amounts (e.g. how many semi-tones a pitch amount of 100 is) is not stored in
  the firmware tables and is unknown.

### MIDI

| Offset | Size | Type  | Description                            | Values                   |
|--------|------|-------|----------------------------------------|--------------------------|
| 0x2918 | 64   | uint8 | MIDI note of pad *p* (at `0x2918 + p`) | 0..127                   |
| 0x2958 | 128  | uint8 | Pad of MIDI note *n* (at `0x2958 + n`) | 0..63, 64 = not assigned |
| 0x29D8 | 1    | uint8 | MIDI program change                    | 0 = Off, 1..128          |

### Slider (13 bytes), slider *r* (0..1) at `0x29D9 + r * 0x0D`

| Offset | Size | Type  | Description | Values                                                 |
|--------|------|-------|-------------|--------------------------------------------------------|
| 0x00   | 1    | uint8 | Pad         | 0..63                                                  |
| 0x01   | 1    | uint8 | Unknown     | Always 1                                               |
| 0x02   | 1    | uint8 | Parameter   | 0 = Tune, 1 = Filter, 2 = Layer, 3 = Attack, 4 = Decay |
| 0x03   | 1    | int8  | Tune low    | -120..Tune high                                        |
| 0x04   | 1    | int8  | Tune high   | Tune low..120                                          |
| 0x05   | 1    | int8  | Filter low  | -50..Filter high                                       |
| 0x06   | 1    | int8  | Filter high | Filter low..50                                         |
| 0x07   | 1    | uint8 | Layer low   | 0..Layer high                                          |
| 0x08   | 1    | uint8 | Layer high  | Layer low..127                                         |
| 0x09   | 1    | uint8 | Attack low  | 0..Attack high                                         |
| 0x0A   | 1    | uint8 | Attack high | Attack low..100                                        |
| 0x0B   | 1    | uint8 | Decay low   | 0..Decay high                                          |
| 0x0C   | 1    | uint8 | Decay high  | Decay low..100                                         |
