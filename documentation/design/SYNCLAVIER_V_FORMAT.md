# Arturia Synclavier V preset / SYNX format

This document describes the preset format of **Arturia Synclavier V** (developed with Synclavier
Digital) and its **SYNX** export container, reverse-engineered from the 640 factory presets, a
genuine application export, the application resources (`Synclavier V.xml`) and the parameter
descriptor table of the engine library (`libCSynclavierMac.dylib`). It is the basis of the
`SynclavierV` detector and creator in `de.mossgrabers.convertwithmoss.format.synclavier`.

The format is not officially documented. All of the following was recovered by analysis and
verified by importing a written file into Synclavier V 2.13.4. See also
`SYNCLAVIER_REGEN_FORMAT.md`: the Synclavier Regen runs the same Synclavier engine and imports
these preset files after a simple rename, as described in the Synclavier Digital *"Documentation
Supplement: Importing Arturia Synclavier V Timbres to Synclavier Regen"*.

## 1. SYNX container

`*.synx` (the *Export Preset* / *Export Bank* output) is a **ZIP** archive:

```
Synclavier/User/<Library>/<Preset name>     one file per preset, no file extension
<Library>.png                               optional pack cover (wrapped, see §4)
```

A preset export contains one preset file, a bank export several. The preset files are
self-contained: the referenced sound files are embedded in a second archive appended to each
preset (§4). The cover image at the root is not a plain PNG either - it is wrapped in the same
name-to-bytes archive structure.

The application's import is picky about the ZIP container: every entry is **stored**
(uncompressed, method 0) with general purpose flags 0 and no extra fields, and there are no
directory entries. Archives written by common ZIP libraries (deflate compression, the UTF-8 name
flag 0x800, extended timestamp extra fields) are rejected with a generic import error, so a writer
has to emit the same minimal container.

The same preset files (without the appended sample archive) are what Synclavier V stores in its
browser database folder - one extensionless file per preset under
`/Library/Arturia/Presets/Synclavier V/<Pack>/<Bank>/` on macOS.

## 2. Preset file

A preset is a **Boost 1.55 text serialization archive**: a single long line of tokens separated by
single blanks. Numbers are plain tokens; a string is stored as

```
<byte length> <bytes>
```

with exactly one separating blank. The bytes may contain any character - including blanks and line
feeds, since the blob values embed binary data - so the file must be treated as bytes, not lines.
Floats are written with up to 8 significant digits (C++ `ostream` style).

### 2.1 Layout

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

Worked example (the start of the factory preset *Elec.Piano 1*):

```
22 serialization::archive 10 0 7 0 7 12 Elec.Piano 1 15 Vintage Factory 15 7 Arturia 4 Keys
0 0 13 0 3 80s 7 Classic 5 Clean ... 1 0 35 Synclavier original factory preset. 1709115121
12 2.13.0.65535 ...
```

* The `15` after the library is a schema version identifying the Synclavier V header layout.
  Other Arturia instruments use other values with different header fields (CMI V uses 26) - do not
  cross-parse.
* `<type>` is the browser instrument type (*Keys*, *Bass*, ...; empty in user presets).
* `<timestamp>` is Unix seconds, `<app version>` e.g. `2.13.0.65535`.
* An empty string is written as `0 ` (length zero), which produces the double blanks seen in real
  files.

### 2.2 The string map (browser metadata)

Key/value strings for the browser: `Type`, `Subtype`, `Characteristics` (the tags in grouped form,
`Characteristics,a|b;Genres,c;Styles,d;`), `OriginalPackName`, `OriginalPresetName`,
`OriginalFactory`, `ALVersionFirst`/`ALVersionLast`. A preset saved by the user from the default
state carries `OriginalPresetName=Default`, `OriginalPackName=Factory`, `OriginalFactory=1`,
`Type=Custom`, `Subtype=Default` and empty `Characteristics` groups.

### 2.3 The parameter map

All engine, effect and macro parameters, alphabetically, each mapped to one **normalized** value
`0..1` (§5). The factory presets carry a core set of 1526 parameters; newer application versions
write more (macros etc.) and tolerate missing ones.

### 2.4 The blob map

Raw byte strings, alphabetically:

* `AudioSampleObject` and `AudioSampleObject1..12` - the sound file reference of each partial
  (§3),
* `Partial <n> Frame <k>` - one 448 byte resynthesis frame (*Time Slice*), `k` counting from 0:
  eight header floats (enable, delay, splice time, splice shape, transpose, tuning, volume,
  modulation) followed by 24 carrier harmonic coefficients, 24 carrier phases, 24 modulator
  coefficients and 24 modulator phases (float32 little-endian; engine field names `FrameEnable`,
  `FrameSpliceTime`, `FrameCCoefficient`, `FrameMPhase`, ...),
* `__Mapped__<n>` / `__HW_Mapped__<n>` - 4 byte MIDI mapping entries.

## 3. AudioSampleObject

A 290 byte Boost *binary* archive with a fixed layout:

| Offset | Size | Content                                                            |
|-------:|-----:|--------------------------------------------------------------------|
| 0      | 26   | `01 16` + `serialization::archive` + `01 0A` (signature, version)  |
| 26     | 3    | `00 00 00`                                                          |
| 29     | 256  | Sound file path, UTF-8, NUL padded                                  |
| 285    | 5    | `00 FF 01 FF 01` (three Boost varints: 0, -1, -1)                   |

An empty path means the partial has no sound file. Factory presets reference the Arturia sample
pool (`/Library/Arturia/Samples/Synclavier V/` on macOS,
`C:\ProgramData\Arturia\Samples\Synclavier V` on Windows) with relative paths like
`Factory/Spoken Voice.wav` or `Arturia/CPU Sync High.wav`; user imports keep the absolute path of
the original file. Presets saved by old program versions use 289 byte objects with other trailer
values. The class is Arturia-wide (`JuceArturiaLib::ISamplesManager::AudioSample`) - the same
layout appears in CMI V, Emulator II V and Analog Lab presets.

## 4. The appended sample archive of an export

An exported preset file continues after the trailing line feed with a **second** archive which
embeds every referenced sound file as a verbatim copy:

```
22 serialization::archive 10 0 0 <count> 1 0 1 <path> <bytes> (<path> <bytes>)... \n
```

The single `1` before the first pair appears only once (a Boost class version); `<path>` is the
reference exactly as stored in the `AudioSampleObject` and `<bytes>` is the complete audio file as
a length-prefixed string. Presets in the browser database do not carry this section; exports
always do, and the import requires it - referenced files placed next to the preset inside the ZIP
are *not* picked up.

Note the second archive starts with the tokens `10 0 0` where a preset starts with `10 0 7`: that
is the way to tell the archive kinds apart. The wrapped cover image (§1) uses the same `10 0 0`
map structure with the image file name as its single key and the PNG bytes as the value.

## 5. Parameter scaling

Stored values are normalized `0..1`. The engine range of every parameter is defined in a
descriptor table inside the engine library (records of name pointer, minimum `f32`, maximum `f32`
and step count `i32`); the stored value maps **linearly** onto that range - with one exception,
the time parameters, which run through the quantized Synclavier time table (§5.1). Relevant
parameters (per partial `<n>` = 1..12; note that some names put the index in the middle,
`Partial <n> File Start`, and others at the end, `Partial File MIDI Key <n>` - exactly as listed):

| Parameter | Engine range | Notes |
|---|---|---|
| `Partial <n> Carrier Mode` | 0..2 | *Synthesis*, **Audition** (= plays the sound file), *Analysis* |
| `Partial Volume <n>` | -50..0 dB | 0 = *Off*; unused partials are parked here |
| `Partial File Volume <n>` | -12..+24 dB | 0 dB = 0.33333334; the ±12 dB list in `Synclavier V.xml` is GUI-only |
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
| `Partial <n> Intrinsic File Loop` | 0..1 | honor the loop stored in the WAV file (default on) |
| `Keyboard Envelope Start/In/Out/End <n>` | 0..127 | key window; cross-fade ramps Start→In and Out→End |
| `Crossfade Envelope Start/In/Out/End <n>` | 0..127 | dynamic window (velocity layers when the source is velocity) |
| `Dynamic Envelope Source` | 0..2 | *Keyboard* (default!), *Velocity*, *Mod Wheel* |
| `Volume Envelope Delay/Attack/Initial Decay/Final Decay <n>` | 0..30 s | via the time table (§5.1) |
| `Volume Envelope Peak/Sustain <n>` | 0..100 % | |
| `Vibrato`/`Tremolo` rate | 0..50 Hz | via a second table (`Sync50Herz`) generated in engine code - not recovered, therefore not converted |

### 5.1 The Synclavier time table (`Sync30_000`)

Time parameters are quantized to the classic Synclavier value list of 1251 positions; the stored
normalized value is the (fractional) position divided by 1250, interpolated linearly between
positions. The list is piece-wise linear:

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

The list is documented as a comment named `Sync30_000SecondsList` in the application's
`Synclavier V.xml` resource (`mapping="SynclavierTable(Sync30_000)"`). Examples: the default final
decay 0.3 is position 375 = 100 ms, the default attack 0.012 is position 15 = 3 ms.

## 6. Sample resolution

To find a referenced sound file the reader looks, in order:

1. in the sample archive embedded in the preset itself (§4), by the exact reference and by its
   base name,
2. under the reference as an absolute path (user imports keep the original path),
3. relative to the folder of the synx file,
4. in the Arturia sample pool (§3).

## 7. Mapping to the ConvertWithMoss model

* synx file → a set of multi-samples (each preset file is one `IMultisampleSource`; a bank export
  yields several);
* header name/author/type/tags/description → name, creator, category, keywords and description of
  the metadata;
* partial in **Audition** carrier mode with a non-empty, resolvable sound file → `IGroup` (layer)
  with one `ISampleZone`; partials in *Synthesis* mode are not convertible - the entire *Vintage
  Factory* and most of the factory content are synthesis presets (97 of the 640 factory presets
  play sound files), and a preset whose samples were only resynthesized into frames (a non-empty
  `AudioSampleObject` with carrier mode *Synthesis*, e.g. after a sample import) is skipped with a
  note as well;
* a partial parked at volume *Off* (normalized 0) is inaudible and skipped; on writing, unused
  partials are parked there (the factory convention);
* `Partial File MIDI Key` → root key; `Keyboard Envelope Start/End` → key range, `In`/`Out` → the
  note cross-fades; `Crossfade Envelope Start/In/Out/End` → velocity range and cross-fades, taken
  (and written) only with `Dynamic Envelope Source` = *Velocity*;
* `Partial Volume` + `Partial File Volume` → zone gain (dB, additive); on writing the gain is
  split: -12..+24 dB into the file volume, deeper attenuation into the partial volume;
* `Partial File Tuning` + `Partial Tuning` (cents) + `Partial Transpose` (semitones) +
  `Partial Octave` (`12 * log2(Hz / 440)` semitones) → zone tuning; on writing whole semitones go
  into the transpose and the remainder into the file tuning;
* `Partial Pan` → zone (and group) panning (`value / 63`);
* `File Start/End` fraction × sample frames → zone start/stop; `File Loop Mode` = *Loop* → a
  forward loop from the loop fractions; otherwise, with `Intrinsic File Loop` on, the loop stored
  in the WAV file applies;
* `Volume Envelope Delay/Attack/Initial Decay/Final Decay` (via §5.1) and `Peak`/`Sustain` → the
  zone amplitude envelope (delay, attack, decay, release, hold and sustain level);
* on writing, a preset starts from a neutral template (the majority value of every parameter over
  the factory corpus, effects off, one initial frame per partial), at most 12 zones become
  partials (more are dropped with a message), the samples are embedded (§4, referenced as
  `User/<Preset>/<zone>.wav`) and the whole is packed in the minimal stored-only ZIP (§1). A
  library becomes one synx file with all its presets, like a bank export.
