# Synclavier Regen library / timbre / SFLC format

This document describes the file format used by the **Synclavier Regen** (2023), reverse-engineered
from the device firmware (`SynclavierRegen.update`, a squashfs image containing the ARM64 application
`SynclavierIIDesktopRelease`) and the freely available libraries on <https://www.synclavier.com/regen-downloads/>.
It is the basis of the `de.mossgrabers.convertwithmoss.format.synclavier` detector and creator.

The format is not officially documented. All of the following was recovered by analysis; where a value
could not be pinned down with certainty it is called out.

## 1. Library folder

Sounds are organized in **libraries**. A library is a folder placed under `Libraries/` on the device SD
card and contains:

* one or more **timbres**, each a UTF-8 text file named `NN-Entry.txt` (see §3),
* the referenced **samples** (`.sflc`, or plain `.wav`/`.flac` in user libraries),
* `_<LibraryName>.tsv` — the sample index (§5),
* `_TimbreIndex.tsv` — the timbre index (§5),
* optional `Description.txt`, `Tier.txt`, cover image, and a `Session/` / `Studios/` / `Reverbs/` tree.

The `NN` in the timbre file name is derived from the zero-based bank entry index `be`:

```
NN = 10 * (be / 8 + 1) + be % 8 + 1        (integer arithmetic)
```

so entry 0 → `11-Entry.txt`, entry 7 → `18-Entry.txt`, entry 8 → `21-Entry.txt`, … This matches every
library with zero exceptions.

## 2. SFLC sample obfuscation

A `.sflc` file is a **standard FLAC file whose bytes are XORed with a key-stream**. The key-stream is
produced by a full-period (2^64) linear congruential generator (LCG) seeded from the file's *base name*
(the file name without its extension). Because the transformation is a plain XOR, the same routine both
obfuscates and de-obfuscates. The whole file is high-entropy with no header — there is no `fLaC` magic
until it is de-obfuscated.

```
A    = 0x3357C6A7C5DCC7F5     # LCG multiplier (A % 4 == 1)
C    = 0x8D14B6503262BD01     # LCG increment (odd)  -> full period 2^64
SEED = 0xAEAEE9B5E0E46745     # seed base
name = base file name without extension, as UTF-8 bytes   # e.g. "Prodigy Long Sync"

# seed derivation
state = SEED
for b in name:
    state = ((b ^ state) * A + C) mod 2^64

# key-stream and XOR (encrypt == decrypt)
for i, byte in enumerate(file_bytes):
    state = (state * A + C) mod 2^64
    plain[i] = byte ^ (state & 0xFF)
```

In the firmware the three 64-bit constants are obfuscated as arrays of eight 32-bit floats, each a
perfect square; `floor(sqrt(f))` of the eight floats gives the eight little-endian bytes of the constant
(transform function at file offset `0x8e7cf0`; seed derivation and read/XOR loop in the
`SFLCFileInputStream` constructor at `0x37c1f0` and its read method at `0x388220`). All 71 sample files
from the firmware libraries and the *Starsky's Prodigy* bank de-obfuscate to a FLAC stream that passes
`flac -t`.

**Consequence for writing:** the key depends on the output file's base name, so renaming a `.sflc` file
corrupts it. The creator therefore obfuscates with the exact name it writes the file under.

User libraries may reference plain, un-obfuscated `.wav` or `.flac` files instead (e.g. the
`ExampleUserCardContents` download). Both are read.

## 3. Timbre text file

UTF-8 with LF line endings:

```
Line 1: title (display name)
Line 2: comment (may be empty; the paragraph separator is "¶" U+00B6; "#tags" are searchable)
Line 3: SynclavierVirtualInstrumentTimbreVersion000 <gen> 12 24 <mask> 99 <n>
Line 4+: one self-contained parameter line each
```

The version header fields: `<gen>` is the writer generation (5/6/7), `12` the number of partial slots,
`24` the maximum number of harmonics, `<mask>` a capability mask of the form `(2^k - 1) & ~32`, `99` the
maximum timbre-frame index and `<n>` a writer counter. Only `<gen>` is informative for parsing; the
creator writes a safe modern header `7 12 24 4063 99 14`.

### 3.1 Line grammar

```
Synclavier<Keyword> <a> <b> <c> <payload...>
```

The three leading integers are index fields whose meaning depends on the keyword prefix:

* `TBPI*` — timbre-global, `a=b=c=0`.
* `PTPI*` — per partial, `a` = partial index (0..11).
* `EMPI*` — per modulation slot, `a` = slot (0..11).
* `MIDICCValue` — `c` = MIDI CC number.

Parameter lines are written only when *touched* in the editor (a dirty flag, not "non-default"), so a
robust reader must not rely on any particular line being present.

### 3.2 Partials

A timbre has up to twelve partials (layers). Each partial has a synth mode (`SynclavierPTPISynthMode`):
`0` additive/FM, `1` subtractive, `2` sample playback, `4` frame-resynthesis of a sample. A partial is
marked *active* by the presence of a `SynclavierPTPIVolume` line; the `timbrePartials` bit-mask in
`_TimbreIndex.tsv` is exactly the set of partials that carry a volume line.

### 3.3 Patch list entry (the sample zones)

Each `SynclavierPTPatchListEntry` maps one sample into one partial. **All zone data must be read from
these lines only** — the sibling `SynclavierPTPIFile*` scalar lines merely mirror the patch line that is
currently selected in the editor and are frequently stale.

```
SynclavierPTPatchListEntry <partial> <entry> <lowKey> <highKey> <rootKey> \
    <volFrac> <tuneFrac> <start> <end> <loopStart> <loopEnd> <loopBits> <0> \
    <channels> <frames> <rateHz> <path...>
```

| Field       | Meaning                                                                    |
|-------------|----------------------------------------------------------------------------|
| partial     | partial (layer) index                                                      |
| entry       | entry index within the partial                                             |
| lowKey/highKey/rootKey | MIDI key range and the sample's original key                    |
| volFrac     | volume as a fraction (0.5 neutral): `gain[dB] = 36 * volFrac - 12`          |
| tuneFrac    | tuning as a fraction (0.5 centered): `tune[cents] = 250 * tuneFrac - 125`   |
| start / end | play start/end as a fraction of `frames`                                   |
| loopStart / loopEnd | loop start/end as a fraction of `frames`                           |
| loopBits    | 0 = no loop, 1 = loop, 3 = loop + cross-fade, 4 = one-shot, 5 = one-shot + loop |
| channels    | 1 = mono, 2 = stereo                                                        |
| frames      | number of sample frames (used to turn the fractions into frame positions)  |
| rateHz      | sample rate                                                                |
| path        | referenced sample (see §6); may carry a virtual folder prefix and a placeholder extension |

The patch entry itself has no panning; panning is a **per-partial** setting (`PTPIPan`, see §4). Older
(`gen` 5/6) files write the integer fields as floats. Empty-path placeholder entries occur and are ignored.

## 4. Envelopes, filter and velocity layers

**Amplitude envelope** (per partial): `PTPIVEnvDelay / VEnvAttack / VEnvPeak / VEnvIDecay / VEnvSust /
VEnvFDecay` — a Synclavier Delay / Attack-to-Peak / Initial-Decay-to-Sustain / Final-Decay (= release)
envelope; times are in seconds (0..30), levels in percent. Converted to and from the zone amplitude
envelope.

**Panning** (per partial): `PTPIPan <partial> 0 0 <value>` where `value` is an integer in `[-63..63]`, `0`
centered. Present only when non-centered (the format omits default values). Mapped to the panning of all
the partial's zones (`panning = value / 63`); on writing, the average pan of a partial's zones is written
back as one `PTPIPan` line, and centered partials emit none.

**Per-partial gain and pitch**: on top of the per-patch-entry gain/tuning, a partial has its own gain and
a three-level pitch offset, all confirmed from the firmware parameter descriptor table and pitch engine
(`frequency = 440 · 2^(semitones / 12)`):
* `PTPIVolume` — a dB **attenuation** in `[-50..0]` (`0` = neutral, the common case). Added to the gain of
  every zone of the partial. On writing, because the patch-entry gain field only reaches down to `-12` dB,
  a deeper attenuation is split off into `PTPIVolume` and the per-zone remainder stays in the entry.
* `PTPITune` — a fine tuning in **cents** (`[-125..125]`).
* `PTPITran` — a coarse transpose in **semitones** (`[-24..24]`).
* `PTPIOctave` — a coarse octave transpose stored as a **reference frequency** in Hz where `440` (A4) is
  neutral; its contribution is `12 · log2(Hz / 440)` semitones (always whole octaves, `-72..+24`).
  Omitted ⇒ neutral (`440`).

  The three pitch terms add up: `extraSemitones = Tran + Tune/100 + 12·log2(Octave/440)`, applied to the
  tuning of every zone of the partial. On writing, whole semitones beyond the entry tuning field's `±125`
  cents are split off into `PTPITran` and the fine remainder stays in the entry (octave-sized offsets are
  written as `PTPITran` too, within its `±24` range).

**Note filter** (timbre-global): `TBPINoteFilterType` is a multi-mode selector encoded as `index / 255`,
where `index` is `1 = LP12, 2 = HP12, 3 = BP12, 4 = LP24, 5 = HP24, 6 = BP24` — the low three are 2-pole,
the high three 4-pole, and `1/4` = low-pass, `2/5` = high-pass, `3/6` = band-pass (verified against the
production 1.18 firmware). `TBPINoteFilterCutoff` (0..1 fraction), `TBPINoteFilterResonance` (0..1), the
filter envelope `TBPINoteFilterAttack / Decay / Release` (seconds) with the depth from
`TBPINoteFilterPeakDelta` (octaves; 10 octaves = full model depth), and `TBPINoteFilterPitchTrack`
(keyboard tracking). Converted as a low-pass, high-pass or band-pass filter (with the matching 2- or
4-pole slope) with its cutoff envelope. The filter is set both per zone and as the global filter of the
multi-sample.

**Velocity layers**: the Synclavier has no per-zone velocity field. Instead each partial has a crossfade
window `PTPIXFStart / XFIn / XFOut / XFEnd` along a *dynamic axis* whose source is `TBPIDynEnvSrc` (`10` =
velocity, `16` = mod-wheel, `7` = …). When the source is velocity, each partial is a velocity layer:
`velLow = XFStart`, `velHigh = XFEnd`, with `XFIn`/`XFOut` the cross-fade edges. On writing, a source with
velocity layers sets `TBPIDynEnvSrc 0 0 0 10` and one partial per layer with the window from the layer's
velocity range. A timbre has 12 partials, hence at most 12 velocity layers.

Not converted: the `IEnv*` FM/index envelope, the `PTCarrier`/`ModulatorCoeffs`/`Phases` harmonic data,
the frame partials and the `EMPISource`/`EMPIDest` modulation matrix (whose numeric destination ids shift
between firmware generations — the trailing symbolic name such as `SynclavierEDFilterCutoff` is the stable
identifier). These are additive/FM-synthesis specifics that do not map to a generic multi-sample.

## 5. Index files

`_<LibraryName>.tsv` (sample index), tab-separated, one header row then one row per sample:

```
mFilenameNoExt  mTitle  mComment  mSampleRate  mFrameLength  mChannels  mMIDIKey  mFileHz \
    mPitchTrack  mMarkStart  mTotalLen  mLoopLen  mLoopXfade  mLoopBits  mMedia  mExtension  mDLMLSB
```

`_TimbreIndex.tsv` (timbre index):

```
bankEntry  timbreName  timbreInfo  timbrePartials  timbreDLMLSB
```

`bankEntry` is the zero-based entry index (see §1 for the file-name mapping), `timbreInfo` is the comment,
`timbrePartials` the active-partial bit-mask (§3.2).

## 6. Sample resolution

The `path` in a patch entry may contain a virtual folder prefix and a placeholder extension. To find the
actual file the reader uses the *base name* of the path and looks in the timbre's folder for, in order:
the exact referenced name (plain user samples), then `<base>.sflc`, `<base>.flac`, `<base>.wav`,
`<base>.aif(f)`. Names truncated with a trailing `~` and alternative extensions are resolved through the
`_<LibraryName>.tsv` sample index.

## 7. Mapping to the ConvertWithMoss model

* library folder → a set of multi-samples (each timbre is one `IMultisampleSource`);
* partial → `IGroup` (layer); patch entry → `ISampleZone`;
* `lowKey`/`highKey`/`rootKey` → zone key range and root key;
* `volFrac` → zone gain (dB, via the law above); `tuneFrac` → zone tuning (semitones);
* per-partial `PTPIVolume` (dB) adds to the zone gain; per-partial `PTPITune` (cents) + `PTPITran`
  (semitones) + `PTPIOctave` (`12·log2(Hz/440)` semitones) add to the zone tuning (§4); on writing these
  carry the part of the gain/tuning that does not fit the per-zone entry fields;
* `start`/`end` fraction × `frames` → zone start/stop; `loopBits` 1/3/5 → a forward loop with
  `loopStart`/`loopEnd` fraction × `frames`;
* per-partial `VEnv*` → the zone amplitude envelope; per-partial `PTPIPan` → the zones' panning
  (`panning = value / 63`); timbre-global `NoteFilter*` → a low-pass, high-pass or band-pass filter with
  a 2- or 4-pole slope (per zone and as the global filter) with its cutoff envelope (§4);
* when `TBPIDynEnvSrc == 10`, each partial's `XF*` window → the zones' velocity range (velocity layers);
  on writing, groups (velocity layers) → partials with `XF*` windows, capped at 12 partials;
* `#tags` in the comment ↔ category and keywords: on reading, the first tag becomes the category and
  the remaining tags the keywords; on writing, the category is emitted first as the Regen category tag
  (mapped to the canonical category list of the Regen manual §3.2, e.g. `Bass` → `#bass`,
  `Chromatic Percussion` → `#chime`; categories with no equivalent fall back to the sanitized name) and
  the keywords follow as property tags (all lower-cased, single-token, de-duplicated). The containing
  folder name → creator.

On writing, the laws are inverted (`volFrac = (dB + 12) / 36`, `tuneFrac = (cents + 125) / 250`), the full
sample is FLAC-encoded and then obfuscated into a `.sflc` file named after the (sanitized) sample name,
and the two index files are generated.
