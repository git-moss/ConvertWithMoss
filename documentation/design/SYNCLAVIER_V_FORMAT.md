# Arturia Synclavier V preset format (SYNX)

Reverse-engineered 2026 from the Synclavier V factory content (640 presets), the application
resources and the engine library. Nothing in this document is official. See also
`SYNCLAVIER_REGEN_FORMAT.md` - the Regen runs the same Synclavier engine and imports these files
after a simple rename, as described in the Synclavier Digital "Documentation Supplement:
Importing Arturia Synclavier V Timbres to Synclavier Regen".

## SYNX container

`*.synx` (the *Export Preset* / *Export Bank* output of Synclavier V) is a plain **ZIP** archive:

```
Synclavier/User/<Library>/<Preset name>     one file per preset, no file extension
```

A preset export contains one file, a bank export several. ConvertWithMoss additionally bundles the
samples of written presets under `Synclavier/User/<Library>/Samples/<Preset>/*.wav` and references
them relative to the preset file.

The preset files are also what Synclavier V stores in its browser database folder
(`/Library/Arturia/Presets/Synclavier V/...` on macOS) - one extensionless file per preset.

## Preset file

A preset is a **Boost 1.55 text serialization archive**: a single long line of tokens separated by
single blanks. A string is stored as `<byte length> <bytes>` with exactly one separating blank; the
bytes may contain any character (including blanks and line feeds - the blob values embed binary
data, so the file as a whole must be treated as bytes, not lines).

```
22 serialization::archive 10 0 7 0 7
<name> <library> 15 <author> <type>
0 0 <tag count> 0 <tags...>
1 0 <description> <timestamp> <app version>
0 0 0 0 0 0 0 <empty string> 0 0
<meta count> 0 0 0 (<key> <value>)...
0 0 0 7 0 0 0 0 0 0
<parameter count> 0 0 0 (<name> <value>)...
<blob count> 0 (<key> <blob>)...
\n
```

* The `15` after the library is a schema version: it identifies the Synclavier V header layout
  (other Arturia instruments use other values with different header fields).
* `<type>` is the browser instrument type (*Keys*, *Bass*, ...; empty for user presets).
* `<timestamp>` is Unix seconds, `<app version>` e.g. `2.13.0.65535`.
* The meta map holds browser strings: `Type`, `Subtype`, `Characteristics` (the tags in grouped
  form), `OriginalPackName`, `OriginalPresetName`, `OriginalFactory`, `ALVersion*`.
* The parameter map (alphabetical) holds every engine and effect parameter as a **normalized**
  value `0..1` (written like a C++ `float` with up to 8 significant digits).
* The blob map (alphabetical) holds raw byte strings:
  * `AudioSampleObject` and `AudioSampleObject1..12` - the sound file reference per partial (see
    below),
  * `Partial <n> Frame <k>` - one 448 byte resynthesis frame (*Time Slice*): header floats
    (enable, delay, splice time/shape, transpose, tuning, volume, modulation) followed by 24
    carrier coefficients/phases and 24 modulator coefficients/phases,
  * `__Mapped__<n>` / `__HW_Mapped__<n>` - 4 byte MIDI mapping entries.

## AudioSampleObject

A 290 byte Boost *binary* archive with a fixed layout:

| Offset | Size | Content                                                            |
|-------:|-----:|--------------------------------------------------------------------|
| 0      | 26   | `01 16` + `serialization::archive` + `01 0A` (signature, version)  |
| 26     | 3    | `00 00 00`                                                          |
| 29     | 256  | Sound file path, UTF-8, NUL padded                                  |
| 285    | 5    | `00 FF 01 FF 01` (three Boost varints: 0, -1, -1)                   |

An empty path means the partial has no sound file. Factory presets reference the Arturia sample
pool (`/Library/Arturia/Samples/Synclavier V/` on macOS) with relative paths like
`Factory/Spoken Voice.wav` or `Arturia/CPU Sync High.wav`; user imports keep the absolute path of
the original file. Sample audio is never embedded in the preset. (Presets saved by old program
versions use 289 byte objects with other trailer values.)

## Parameter scaling

Stored values are normalized `0..1`. The engine range of every parameter sits in a descriptor
table inside the engine library (records of name, minimum, maximum and step count); the stored
value maps linearly onto that range - with one exception, the time parameters, which run through
the quantized Synclavier time table below. Relevant parameters (per partial `<n>` = 1..12):

| Parameter | Engine range | Notes |
|---|---|---|
| `Partial <n> Carrier Mode` | 0..2 | *Synthesis*, **Audition** (= plays the sound file), *Analysis* |
| `Partial Volume <n>` | -50..0 dB | 0 = *Off* (unused partials are parked here) |
| `Partial File Volume <n>` | -12..+24 dB | 0 dB = 0.33333334 |
| `Partial Pan <n>` | -63..+63 | |
| `Partial Tuning <n>` | -125..+125 cents | |
| `Partial Transpose <n>` | -24..+24 semitones | |
| `Partial Octave <n>` | 6.875..1760 Hz | geometric: `6.875 * 2^(8 * value)`, 440 Hz (0.75) = neutral |
| `Partial File MIDI Key <n>` | 0..127 | the root key (60 = 0.47244096) |
| `Partial File Tuning <n>` | -125..+125 cents | |
| `Partial <n> File Start/End` | 0..1 | fraction of the sample length |
| `Partial <n> File Loop Start/End` | 0..1 | fraction of the sample length |
| `Partial <n> File Loop Mode` | 0..1 | *No Loop* / *Loop* |
| `Partial <n> File Loop Decay` | 0..1 | loop cross-fade off/on |
| `Partial <n> Intrinsic File Loop` | 0..1 | honor the loop stored in the WAV file |
| `Keyboard Envelope Start/In/Out/End <n>` | 0..127 | key window with cross-fade ramps Start-In and Out-End |
| `Crossfade Envelope Start/In/Out/End <n>` | 0..127 | dynamic window (velocity when the source is velocity) |
| `Dynamic Envelope Source` | 0..2 | *Keyboard*, *Velocity*, *Mod Wheel* |
| `Volume Envelope Delay/Attack/Initial Decay/Final Decay <n>` | 0..30 s | via the time table |
| `Volume Envelope Peak/Sustain <n>` | 0..100 % | |
| `Vibrato`/`Tremolo` rate | 0..50 Hz | via a second table (`Sync50Herz`) which is generated in code - not recovered, therefore not converted |

## The Synclavier time table (`Sync30_000`)

Time parameters are quantized to the classic Synclavier value list of 1251 positions; the stored
normalized value is the (fractional) position divided by 1250. The list is piece-wise linear:

| Position | Seconds | Step |
|---:|---:|---:|
| 0..250 | 0..0.05 | 0.2 ms |
| 250..375 | 0.05..0.1 | 0.4 ms |
| 375..475 | 0.1..0.2 | 1 ms |
| 475..625 | 0.2..0.5 | 2 ms |
| 625..750 | 0.5..1 | 4 ms |
| 750..850 | 1..2 | 10 ms |
| 850..950 | 2..4 | 20 ms |
| 950..1100 | 4..10 | 40 ms |
| 1100..1200 | 10..20 | 100 ms |
| 1200..1250 | 20..30 | 200 ms |

(The list is documented as a comment named `Sync30_000SecondsList` in the application's
`Synclavier V.xml` resource; e.g. the default final decay 0.3 is position 375 = 100 ms.)

## Conversion notes

* Only partials in **Audition** carrier mode play their sound file and can be converted to sample
  zones. Synthesis presets (the entire *Vintage Factory* and most of the factory content) and
  presets whose sample was only resynthesized into frames (`Carrier Mode` *Synthesis* with a
  non-empty `AudioSampleObject`, e.g. after a sample import) contain no playable audio. Of the 640
  factory presets, 97 play sound files.
* The partial volume *Off* position (normalized 0) marks unused partials; a written preset parks
  every unused partial there (that is also the factory convention).
* Velocity layers exist as partials whose crossfade envelope splits the dynamic range, with the
  `Dynamic Envelope Source` set to *Velocity*.
* A preset written by ConvertWithMoss starts from a neutral template (the majority value of every
  parameter over the factory corpus, effects off, one initial frame per partial) so that all
  synthesis parameters hold their defaults.
