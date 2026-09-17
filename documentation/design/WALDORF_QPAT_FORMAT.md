# Waldorf Quantum / Iridium patch format (.qpat)

The patch file format of the Waldorf **Quantum** (MkI, MkII), **Iridium** and **Iridium Core**
synthesizers, written for anyone who wants to read or write sample-based patches for these
instruments. Waldorf does not document the format. Everything here was recovered while building the
reader and writer of ConvertWithMoss (`format/waldorf/qpat/` in its source tree) and is stated from
the viewpoint of an implementer, not of that program; a short appendix says how ConvertWithMoss
itself maps the format, for readers who want to compare against it.

**Verification status.** Statements marked **(hw)** were confirmed on an Iridium (MK2 hardware, OS
4.0.x) - they come from controlled tests whose outcome is recorded in the ConvertWithMoss commit
history. Statements marked **(fw)** were read out of the Iridium MK2 firmware 4.0.5 (the value laws
of the low frequency oscillators and the formatters of the other laws). Statements marked
**(manual)** come from the Iridium MK2 manual (English, OS 4). The layer model, the format versions
and the version-dependent header fields were checked against 6,357 patch files on hand - factory
sets and device exports of both generations, format versions 6 to 15 - and are marked **(corpus)**.
Everything else is how ConvertWithMoss interprets the file; its output loads and plays on the
device, so the interpretation is at least compatible, but it has not been proven to be what the
device does internally. The section "Open questions" lists what is known to be uncertain.

## 1. Overview

A `.qpat` file holds one **patch**. A patch has up to **two layers** on the first generation of the
instruments (Quantum MkI, Iridium, Iridium Core) and up to **four** on the MK2 generation (Iridium
MK2, Quantum MK2 and first-generation instruments upgraded to MK2 hardware). A layer is a complete
patch of its own, and a multi-layer file simply stores the single-layer patches one after the other
(section 2.6). Each layer has **three oscillators**, one **filter path**, an **amplifier envelope**,
three **free envelopes**, **six low frequency oscillators** and a **40 slot modulation matrix**.

An oscillator in *Particle* mode plays a **sample map**: a small text table which maps sample files
to key and velocity ranges, with a root note, level, panning, start/end and loop per entry. A patch
can therefore carry up to three multi-samples per layer, one per oscillator. The sample files are
**not** embedded - the map references WAV files which live next to the patch.

Everything apart from the sample maps is stored as named **parameters** (`Osc1Vol`, `Filter1CutOff`,
`AmpEnvAttack`, ...), each a 32-bit float. A file is essentially a fixed header, a dictionary of
such parameters and the sample map texts.

## 2. File layout

All integers are **little-endian**, floats are IEEE 754 single precision little-endian, strings are
**ASCII in fixed 32 byte fields, NUL padded** (a string of exactly 32 characters has no terminator).
The file is a fixed **512 byte header**, then the **parameter records**, then the **resource
data** - and, in a multi-layer patch, the next layer's header right after that (section 2.6):

```
offset  size  content
0       4     u32 magic 0x0033ECB4 (decimal 3402932; bytes B4 EC 33 00)
4       4     u32 format version. ConvertWithMoss writes 14, which an Iridium on OS 4 loads (hw)
8       32    patch name - what the device displays and files the patch under; the 32 bytes are
              the limit which the Iridium MK2 manual states for a name entered on the device
40      32    author
72      32    bank
104     128   four attributes of 32 bytes each (free tags, see section 6)
232     2     u16 number of parameter records
234     2     padding (0; some older files hold garbage)
236     192   resource table: 16 entries of 12 bytes (section 2.2)
428     2     u16 layer count code: 0 = single layer, 1 = two layers (offset at 432),
              2 = four layers (offsets at 432, 440 and 444; MK2, version 15) - section 2.6
430     2     u16 layer mode, a copy of the TimbreMode parameter: 0 = single, 1 = split,
              2 = layered. Only meaningful when 428 is non-zero; older files hold garbage here
432     4     u32 absolute file offset of layer 2 (the "alternate timbre"), a complete patch
              with its own 512 byte header, parameter records and resource data
436     1     u8  instrument the patch was last saved on: 0 = Quantum, 1 = Iridium,
              2 = Iridium Core, 3 = Iridium MK2 (observed, OS 4.0.x). Present from version 9
              on; version 8 and older hold garbage here
437     3     padding
440     4     u32 absolute file offset of layer 3 (MK2, version 15; otherwise 0)
444     4     u32 absolute file offset of layer 4 (MK2, version 15; otherwise 0)
448     64    padding (0 in recent files; version 8 and older hold uninitialized data)
512     68 n  n parameter records of 68 bytes (section 2.3)
512+68n ...   resource data (the sample maps), addressed by the resource table (section 2.4)
```

A file carries **no checksum** and no length fields other than the ones above, so the header strings
can be patched in place (this is how an already converted library was re-tagged with new attributes
without converting it again).

### 2.1 Header strings

Name, author, bank and the four attributes are plain 7-bit ASCII. Trim trailing NUL bytes and
whitespace when reading; truncate to 32 bytes when writing. The name field is the only one of them
which the device needs; author, bank and attributes may be empty.

### 2.2 Resource table (offset 236)

Sixteen entries of 12 bytes, directly after the parameter count:

```
offset  size  content
0       4     u32 type (table below)
4       4     u32 offset of the resource, relative to the START of the resource data,
              i.e. to the end of the parameter block at 512 + 68 n
8       4     u32 length in bytes
```

| type | meaning |
|-----:|---------|
| 0 | unused entry (all three fields 0) |
| 1-3 | user wavetable 1-3 (not covered by this document) |
| 4-6 | **user sample map 1-3** = the multi-sample of oscillator 1-3 |
| 7 | MK2 only (version 15), seen in patches which use the *Param Sequence*; a binary table of 9 byte records (u8, u32, f32) - not decoded, skip it (corpus) |

Skip entries whose type you do not know instead of rejecting the file - a reader which rejects type
7 fails on every MK2 patch that carries one. The offsets of consecutive resources must
**accumulate**: a file which wrote offset 0 for every map made the device read maps 2 and 3 on top
of map 1, so the samples of a multi-oscillator patch could not be located and the device showed its
*Find Sample Map* screen **(hw)**. Write the maps back to back and give map k the offset "sum of the
lengths of maps 1 .. k-1". Because the offsets are relative to the end of the parameter block, a
parameter record can be inserted or removed (and the count at 232 adjusted) without touching the
table - the resource data only moves as a whole.

The loader reads the resources **sequentially in the order of the table** (fw): for every entry
with a type it reads `length` bytes from the current file position into its area at `offset`, so
the data of the entries has to follow the parameter block in table order without gaps, which the
accumulated offsets give. An offset or an end beyond 20 MB (0x1400000, the size of the resource
area) is reported as "strange resource offset" and the entry is dropped. A file holds at most 4011
parameter records; more are skipped with a note.

### 2.3 Parameter records (offset 512)

```
offset  size  content
0       4     f32 value
4       32    parameter name, e.g. "Filter1CutOff", "Osc1Vol", "MatrixAmount4"
36      32    hint - the value as display text, e.g. "19912.2 Hz", "Active", "+100.00 %"
```

* The **name identifies the parameter**. The block is a flat dictionary: neither the order nor the
  number of records is fixed, and a reader should ignore names it does not know. The device's own
  files carry the complete parameter set of the instrument (files of about 260 KB); ConvertWithMoss
  writes only the parameters a patch needs (18 for a plain single-oscillator sample patch, see
  section 3) and the device loads those.
* The **hint is only a label**. Files written by the device leave it empty for continuous parameters
  and hold the option name for enumerations; ConvertWithMoss fills it with the value as the device
  would display it. Decode the float, never the text.
* **Value encoding.** A continuous parameter is a normalized `0..1` float which the device maps
  through a parameter-specific law (section 5.9). An enumeration stores its index as a float
  (`FilterState` 1.0 = *Bypass*). A few stepped parameters store an integer-valued float directly
  (`Osc1CoarsePitch` 0..48 = -24..+24 semitones).
* Whether a parameter which is absent from the file is reset to its default or keeps the value of
  the previously loaded patch has not been tested. The safe rule is to **write every parameter a
  feature depends on** - for an LFO you route somewhere, all of its parameters - so the result does
  not depend on what was loaded before (see "Open questions").

### 2.4 Resource data

Follows the last parameter record without padding. Each resource occupies the byte range given by
its table entry. A sample map is plain ASCII text (section 4) which ends with `\n` and a NUL byte,
both counted in its length: the device does not stop reading a map at that length (4.4) **(hw)**.

### 2.5 Format versions (corpus)

The version at offset 4 grew with the instrument firmware. What the corpus shows:

| version | files | instrument byte (436) | parameters per layer | notes |
|--------:|------:|-----------------------|---------------------:|-------|
| 6 | 12 | not yet present (garbage) | 1145 | oldest seen |
| 8 | 1666 | not yet present (garbage) | 1173-2006 | two-layer files exist (code 1); bytes 437-511 hold uninitialized data |
| 9 | 1274 | 0 (Quantum) | 2139-2163 | first version with the instrument byte |
| 10, 11 | 686 | 1 (Iridium) | 2192-2213 | |
| 14 | 2415 | 0 | 18-40 | written by ConvertWithMoss; loads on an Iridium MK2 (hw) |
| 15 | 304 | 3 (Iridium MK2) | 3810-3811 | four-layer fields, resource type 7, `LayerActive` and the other additions of section 2.6 |

The parameter block, the resource table and the resource data have the same layout in every version
(`512 + 68 x count + resources` ends exactly at the next layer or at the end of the file in all
6,357 files). What differs is which parameters exist and the meaning of some enumeration indices
(section 5.2), plus the garbage in the header fields named above.

The loader itself gates the header fields by the version (fw): up to version 4 it clears the
resource table, up to version 6 the layer fields at 428-435, up to version 8 the instrument byte
at 436 and up to version 14 the offsets of the layers 3 and 4 at 440 and 444 - which is why those
fields hold garbage in old files without harm, and why a file of version 14 can carry two layers
but never four.

### 2.6 Multi-layer patches

A multi-layer patch is a **concatenation of complete single-layer patches**: layer 2 starts exactly
where the resource data of layer 1 ends, layer 3 where layer 2 ends, and so on (corpus). Each layer
has its own magic, header (with the same name), parameter block and resources, and its resource
offsets are relative to the end of its own parameter block. The header of the **first** layer is the
authoritative one:

```
428  layer count code: 1 = two layers, 2 = four layers
430  layer mode = the TimbreMode parameter: 0 single, 1 split, 2 layered
432  absolute offset of layer 2 (the "alternate timbre")
440  absolute offset of layer 3 (code 2 only)
444  absolute offset of layer 4 (code 2 only)
```

The headers of the later layers repeat the code and the mode and hold the offsets "up to themselves"
(layer 2: its own offset; layer 3: layers 2 and 3; layer 4: all three) - a writer artefact; ignore
them. The firmware validates the offsets when it loads a patch ("Wrong offset for alternate
timbre!", "... for layer3 timbre!", "... for layer4 timbre!" (fw)), so every offset must point at a
magic.

* **First generation** (versions 8-11 in the corpus): code 1, two layers, `TimbreMode` 1 (split) or
  2 (layered).
* **MK2** (version 15): code 2 and **always four layer blobs**, even when only two are in use.
  Whether a layer sounds is stated by the parameter `LayerActive` inside that layer: in the "dual
  layer" patches of the corpus, layers 3 and 4 are present, `LayerActive` = 0 and everything else is
  at its init values. Code 1 has not been seen in a version-15 file, and a three-layer file has not
  been seen at all; treat an offset which is 0 or does not point at a magic as "no layer".

### How the layers are combined

`TimbreMode` selects it, and the header field at 430 mirrors it. The three modes **(manual)**:

| value | parameter label | the manual calls it | what it does |
|------:|-----------------|---------------------|--------------|
| 0 | *Single* | Single | the layers hold individual sounds and **cannot be played together**; the player switches between them |
| 1 | *Split* | Split | each layer plays a dedicated key range (`LayerMinNote`/`LayerMaxNote`), with `LayerVoices` dividing the polyphony; a layer can additionally be addressed on its own MIDI channel |
| 2 | *Layered* | Multi | all active layers sound simultaneously over the whole key range; `MultiAllocMode` then picks *Layered*, *Round Robin* or *Random Robin* |

**A layer cannot be selected by velocity.** The complete set of layer parameters in the firmware is
volume, pan, gain, active, voices, min/max note, split min/max key, pitch and controller - there is
no velocity field, and the manual offers only key ranges, MIDI channels and the two robin modes to
pick a layer **(manual, fw)**. A velocity split therefore has to live in the **sample maps**, whose
entries carry their own velocity window (columns 5 and 6 of section 4.1); the layers are for sounds
which are meant to be heard **together**.

Per-layer parameters, stored inside each layer's own parameter block:

| parameter | meaning |
|-----------|---------|
| `TimbreMode` | the mode above; the same value in every layer |
| `LayerActive` | version 15: 0 *Off*, 1 *Active*. Only meaningful in Split and Multi mode (manual). Earlier versions have no such parameter: every layer of a multi-layer patch sounds |
| `LayerVolume` | 0..1, 1.0 = full level; `dB = 40 log10(x)`, the law of `Osc{i}Vol` (fw) |
| `LayerPan` | 0..1, 0.5 = center (fw) |
| `LayerGain` | 0..1; an additional gain of `24 x` dB, 0 = none (fw) |
| `LayerVoices` | `value + 1` = the voices reserved for the layer (3 = *4*). The manual documents it for **Split mode** and warns that the sum over all active layers must not exceed the 16 voices of the instrument; in the corpus the sums are 14+2, 5+11 and 4x4 |
| `LayerMinNote`, `LayerMaxNote` | version 15: the *Min Key* / *Max Key* of the manual, MIDI notes 0..127 (*C-2* .. *G8*), used in Split mode |
| `LayerSplitMinKey`, `LayerSplitMaxKey` | the older key window, 0..60 with the hints *C1* .. *C6* - five octaves rather than the full range. The device names the MIDI note 60 *C3* (`LayerMinNote` 0 is *C-2*), so the 0 stands for C1 = **MIDI 36** and a value `v` is the note `36 + v` (fw). Prefer `LayerMinNote`/`LayerMaxNote` where they exist |
| `MultiAllocMode` | version 15: 0 = *Layered*, and the *Round Robin* / *Random Robin* cycling next to it (manual) |
| `MultiArpMode` | version 15: 0 = *Individual Layer*, or the arpeggiator of one layer driving all of them |

The per-layer MIDI channel of Split mode is **not** part of the patch - the manual puts it in Global
-> MIDI -> In.

ConvertWithMoss reads multi-layer files - each layer becomes a multi-sample of its own - and can
write them (section 7.5).

## 3. Worked example: a minimal two-sample patch

A patch with two short mono samples, 16-bit / 44.1 kHz, as ConvertWithMoss writes it from plain WAV
files. The whole file is 2005 bytes: 512 (header) + 18 x 68 (parameters) + 269 (one sample map).

```
Example Piano.qpat
samples/Example Piano/Example Piano A3.wav
samples/Example Piano/Example Piano C4.wav
```

Annotated header (only non-zero lines; the rest of the 512 bytes is zero):

```
00000000  b4 ec 33 00 0e 00 00 00  45 78 61 6d 70 6c 65 20  magic, version 14, name "Example
00000010  50 69 61 6e 6f 00 00 00  00 00 00 00 00 00 00 00  Piano" (NUL padded to 32 bytes)
                                                            author @40 and bank @72 are empty
00000060  00 00 00 00 00 00 00 00  50 69 61 6e 6f 00 00 00  attribute 1 @104 = "Piano"
000000e0  00 00 00 00 00 00 00 00  12 00 00 00 04 00 00 00  @232 count = 18, pad; @236 type 4
000000f0  00 00 00 00 0d 01 00 00  00 00 00 00 00 00 00 00  offset 0, length 269; entry 2 unused
                                                            @428 layer code 0, mode 0, offsets 0, synth 0
00000200  00 00 00 40 4f 73 63 31  54 79 70 65 00 00 00 00  @512 record 1: value 2.0f, "Osc1Type"
00000220  00 00 00 00 50 61 72 74  69 63 6c 65 00 00 00 00  @548 hint "Particle"
00000240  00 00 00 00 00 00 00 40  4f 73 63 31 50 61 72 74  @580 record 2: 2.0f, "Osc1Particle...
```

The 18 parameter records, in file order:

| name | value | hint | meaning |
|------|------:|------|---------|
| `Osc1Type` | 2 | Particle | the sample player |
| `Osc1ParticleSampleMode` | 2 | Normal | key-tracked multi-sample playback |
| `Osc1CoarsePitch` | 24 | +0 semi | 0 semitones |
| `Osc1FinePitch` | 0.5 | +0.0 cents | 0 cents |
| `Osc1PitchBendRange` | 26 | +2 | +2 semitones |
| `Osc1Keytrack` | 0.75 | +100.0 | 1:1 key tracking |
| `Osc1Vol` | 1 | +0.000 dB | full level |
| `Osc1Pan` | 0.5 | Center | |
| `FilterState` | 1 | Bypass | no filter |
| `AmpEnvDelay` | 0 | 0.00 secs | |
| `AmpEnvAttack` | 0 | 0.00 secs | instant |
| `AmpEnvDecay` | 0 | 0.00 secs | instant |
| `AmpEnvRelease` | 0.415718 | 1.00 secs | `0.06 * 1000^0.415718 - 0.06` = 1.0 s |
| `AmpEnvSustain` | 1 | 100.00 % | |
| `AmpEnvAttackCurve` | 2 | Lin | |
| `AmpEnvDecayCurve` | 2 | Lin | |
| `AmpEnvReleaseCurve` | 2 | Lin | |
| `AmpVeloAmount` | 1 | 100.00 % | velocity fully controls the level |

The sample map (269 bytes at file offset 1736; TABs shown as `<TAB>`, one entry per line, each line
ended by `\n` and the last one followed by a NUL byte):

```
"samples/Example Piano/Example Piano A3.wav"<TAB>69.00000000<TAB>0<TAB>70<TAB>1.00000000<TAB>0<TAB>127<TAB>0.50000000<TAB>0.00000000<TAB>1.00000000<TAB>0<TAB>0<TAB>1.00000000<TAB>0<TAB>0<TAB>1
"samples/Example Piano/Example Piano C4.wav"<TAB>72.00000000<TAB>71<TAB>127<TAB>1.00000000<TAB>0<TAB>127<TAB>0.50000000<TAB>0.00000000<TAB>1.00000000<TAB>0<TAB>0<TAB>1.00000000<TAB>0<TAB>0<TAB>1
```

That is: file, root 69 / 72, keys 0-70 / 71-127, gain 1.0, velocities 0-127, pan center, start 0,
end 1.0 (the whole file), no loop (mode 0, loop 0 .. 1.0), forward, no cross-fade, key-tracked.
Patches of exactly this shape - plus a filter and a matrix routing where the source has them - are
what the hardware tests referenced in this document were run with.

## 4. Sample map

### 4.1 Columns

A sample map is a **TAB separated text table**, one line per map entry, every line ended by `\n`,
no header line, and a NUL byte after the last line (4.4). A line has 16 columns (the device parses
them with `sscanf` and the format `"%[^"]" %f %d %d %f %s %s %f %f %f %d %f %f %d %f %d`, so any
white space separates the columns and an unquoted path may not contain a space) (fw):

| # | column | content | encoding |
|--:|--------|---------|----------|
| 0 | path | sample file, in double quotes | `"samples/<patch>/<file>.wav"`; optional drive prefix, see 4.3 |
| 1 | pitch | root note **with the fine tuning folded in** | float MIDI note. To play an entry `t` semitones sharper than its recording, write `root - t`: a lower root transposes the playback up. Read it back as `root = round(pitch)`, `tuning = root - pitch` |
| 2 | FromNote | lowest key | integer 0-127 |
| 3 | ToNote | highest key | integer 0-127, inclusive |
| 4 | Gain | level | **linear amplitude factor**: 1.0 = 0 dB, `10^(dB/20)` |
| 5 | FromVelo | lowest velocity | integer 0-127, or a fraction with a decimal point: `0.5` is read as `(int) (0.5 x 127 + 0.0001)` = 63 (fw) |
| 6 | ToVelo | highest velocity | integer 0-127, inclusive; a fraction as above. The velocity of a note is compared as an integer against both ends, so a window `0 0` never plays: a note-on with the velocity 0 is a note-off (fw) |
| 7 | Pan | panning | 0.0 = left, 0.5 = center, 1.0 = right; **the device ignores this column** (hw) - pan with `Osc{i}Pan` instead |
| 8 | Start | sample start | fraction 0..1 of the file length (4.2) |
| 9 | End | sample end | fraction 0..1 |
| 10 | LoopMode | 0 = off, 1 = forward, 2 = alternating (ping-pong) | integer |
| 11 | LoopStart | loop start | fraction 0..1 |
| 12 | LoopEnd | loop end | fraction 0..1 |
| 13 | Direction | 0 = forward, 1 = reverse | integer |
| 14 | CrossFade | loop cross-fade | 0..1; played by the device **(hw)**: a loop whose wrap jumps a quarter of a sine period clicks without it and does not click with 0.5 - but the blend is not constant-power, the level dips once per loop cycle (the same loop with an equal-power cross-fade rendered into the audio plays at a steady level). What length the fraction refers to is not verified; device-written maps fit a loop-relative reading (see "Open questions") |
| 15 | TrackPitch | 1 = the entry follows the keyboard, 0 = fixed pitch, 2 = plays at the pitch of the pitch column whatever the key is; any other value plays at a fixed pitch (fw) | integer |

Floating point columns are written with 8 decimal places (`%.8f`), integers plain. A line may be
up to 2000 bytes long and a path up to 1023 characters; a map holds at most **1024 entries** and a
key at most 128 of them, everything beyond is dropped with the error "Number of entries in sample
map exceeded" (fw). Missing trailing columns take the defaults pitch 60, keys 0-127, gain 1,
velocities 0-127, pan 0.5, start 0, end 1, no direction and cross-fade, key tracking on - and a
missing loop mode (fewer than 11 columns) makes the device take the loop of the `smpl` chunk of the
WAV (fw). A pitch of -1000 stands for the root note of the WAV.

**Entries which overlap alternate; they do not stack (hw).** Two entries of the *same* map whose key
*and* velocity ranges overlap are played one after the other on successive notes - a round robin -
and not together. Observed on an Iridium MK2 (2026-08-22) with one map holding two full-range
entries. That matters in both directions: the parts of a sound which are meant to be **heard
together** have to be spread over the oscillators or the layers and must not be folded into one map,
while a round robin - for which the map has no column of its own - is expressed exactly this way.

### 4.2 Positions are fractions of the file length

Start, end and loop points are **fractions of the sample's frame count**, not frame numbers. An
entry therefore cannot be interpreted without the WAV it points to: a reader has to open the file
(or at least its header) to turn the fractions back into frames, and a writer needs the frame count
to produce them.

**The scale is the last frame, not the frame count (fw).** The firmware (Iridium MK2 4.0.5) parses
a fraction as a float and turns it into a frame in single precision - the voice start,
`SampleMap::addSample` and the dozens of other places which use a position share one expression:

```
frame = (int) (0.001f + (float) (N - 1) * fraction)          N = frames of the sample
```

So 1.0 is the last frame, 0.5 of a 1001-frame sample is frame 500, and the value is truncated. The
device's own conversion is the inverse: a sample loop imported from a WAV `smpl` chunk (or AIFF
markers) is stored as `frame / (N - 1)`. The Iridium MK2 factory sets agree: 496 whole-file entries
end at exactly `1.0`, and 48 of 56 loops which the device took over from `smpl` chunks read back
as exactly those frames (the other 8 as the start one frame early - the single-precision product
lands marginally below the frame, which is what the device plays).

**Loops include their end frame (fw).** The looping renderer wraps once the position passes the
loop end and wraps by `end - start + 1` frames; the sample end is the last frame played, too.

Consequences for a writer and a reader:

* A writer which divides by `N` places nearly every position **one frame early** on the device
  (`(N - 1) * frame / N` truncates to `frame - 1` beyond the first 0.1 % of the sample), and a loop
  which starts at the very beginning of a sample becomes one frame shorter.
* Even `frame / (N - 1)` lands one frame early for some positions (7.5 % in a test over sample
  lengths up to 8 million frames), because of the single precision. Write `(frame + 0.5) / (N - 1)` - the middle of the frame - with 8 decimals,
  `0.0` for the first frame and `1.0` for the last: the formula above then gives exactly `frame` for
  every sample shorter than about 7.5 million frames.
* A reader should use the formula above, including the single precision, to get the frames the
  device plays. The fractions of a patch saved by the device are the float values it parsed, so an
  exported patch keeps whatever convention its original writer used.

Keep every fraction inside `0..1`. Source data can point beyond the audio (loop points authored for
a longer original, or a lossy file which decodes shorter than its loop points assume); written
unclamped, a negative or huge value makes the device show its *Locate Samples* screen **(hw)**.

**A loop plays where the map states it, whatever the start of the entry (hw).** An entry which
starts 22,050 frames into its sample and loops the audio behind that point plays exactly that
loop on an Iridium MK2. A reading of the voice set-up in the firmware, which seemed to subtract
the start frame twice from the loop points, predicted the loop to be pulled forward by the start
and was wrong: the device compensates it somewhere else in the render path.

### 4.3 Sample paths and drives

The path is resolved **relative to the folder the patch was loaded from**. An optional drive number
in front of the path (`"4:samples/..."`) makes it absolute on one of the device's drives:

| prefix | drive |
|-------:|-------|
| `2:` | SD card (hw, Iridium OS 4.0.5) |
| `3:` or no prefix | internal memory |
| `4:` | USB drive |

**Write no prefix.** An absolute path only loads from the one drive it names, and the device
prepends its own drive again on *Export -> With Samples*, which produced a doubled, invalid path
(`"3:2:samples/..."`) so the samples could not be backed up; a relative path loads and exports
cleanly from any drive **(hw)**. The device's own exports use `samples/<patch name>/<file>.wav`,
which is also the layout ConvertWithMoss writes.

The file name in the path must match the WAV on disk **exactly**, including any sanitizing a writer
applies to file names (the device cannot find the sample otherwise and shows *Find Sample Map*). The
same screen appears when the device's **internal sample memory is full** - loading a patch copies
its samples into that pool - which looks exactly like a broken file until the free memory is checked
**(hw)**. When reading, strip a drive prefix and resolve the rest against the patch's folder.

### 4.4 Maps written by the device

Maps written by the device end with `\n` and a **NUL byte**, both counted in the length of the
resource - all 563 maps of the MK2 factory sets (versions 8-11) and all 28 of the patches an
Iridium MK2 saved to a card (version 15) **(corpus)**. If a reader splits the text at `\n` it must
trim that NUL (and any other control characters) from the last line.

A writer has to end its maps the same way **(hw)**. The device does not stop reading a map at the
length in the resource table: ConvertWithMoss wrote its maps without the two bytes, so the last
map ended the file, and patches of that kind which an Iridium MK2 saved again carry TrackPitch
values of 10, 15, 19, 1000000 and 15839416 - the `1` which was written, followed by whatever digits
came next in the device's memory. 7 of 346 entries of those saved patches have such a value, each
one the last entry of the last map; every other entry kept its 0 or 1. An entry with such a value
plays every key with the same pitch: 6 of 12 converted single-sample patches of one pack did not
track the keyboard, while their parameters and maps differed from the other 6 in nothing that
concerns the pitch.
Which patches are hit depends on the memory of the device, not on the file. Device exports can also be
incomplete - samples referenced by the map but never written to the card, or written under a
slightly different name (a `#` dropped) - so a missing sample is not necessarily the reader's fault.
The positions follow the scale of section 4.2 (the last frame is `1.0`).

## 5. Parameters

`{i}` stands for the oscillator 1-3, `{n}` for an envelope or LFO index, `{k}` for a matrix slot
1-40. Only the parameters which matter for sample playback are listed; the device has several
hundred more (wavetable, kernel, resonator, effects, arpeggiator, ...).

### 5.1 Value encodings

| kind | stored value | examples |
|------|--------------|----------|
| continuous | normalized `0..1` float, mapped by a law per parameter (5.9) | `Filter1CutOff`, `AmpEnvAttack`, `Osc1Vol` |
| bipolar amount | `0..1` = -100 % .. +100 % (`2x - 1`), 0.5 = 0 | `AmpVeloAmount`, `Filter1EnvAmount`, `MatrixAmount{k}` |
| key tracking | `0..1` = -200 % .. +200 % (`4x - 2`), **0.75 = +100 %** | `Osc{i}Keytrack`, `Filter1Keytrack` |
| enumeration | the option index as a float | `FilterState`, `Filter12Type`, `Lfo{n}Shape`, curve types |
| stepped | an integer-valued float with an offset | `Osc{i}CoarsePitch` 0..48 = -24..+24 |

### 5.2 Oscillator

| parameter | encoding / meaning |
|-----------|--------------------|
| `Osc{i}Type` | enum: 0 *Wavetable*, 1 *Waveform*, 2 ***Particle*** (the sample player), 3 *Resonator*, 4 *Kernels*, 5 *Off* in versions 9-11 (corpus; version-8 files from before the Kernels engine have *Off* at 4). On the MK2 (version 15) a further engine sits at 5 and *Off* is **6**. The index of *Off* therefore depends on the version: to keep an oscillator silent, write nothing for it |
| `Osc{i}ParticleSampleMode` | enum; **2 = *Normal*** = key-tracked multi-sample playback. **Required**: without it a single sample mapped across the keyboard plays at a fixed pitch (hw) |
| `Osc{i}CoarsePitch` | 0..48 = -24..+24 semitones; 24 = 0 |
| `Osc{i}FinePitch` | 0..1 = -100..+100 cents; 0.5 = 0 |
| `Osc{i}PitchBendRange` | 0..48 = -24..+24 semitones; 24 = 0 |
| `Osc{i}Keytrack` | 0..1 = -200..+200 %; **0.75 = +100 %** (the 1:1 tracking of the manual); 0.5 = 0 % = fixed pitch |
| `Osc{i}Vol` | `dB = 40 log10(x)`: 1.0 = 0 dB, 0.5 = -12 dB, 0 = silent |
| `Osc{i}Pan` | 0..1 = left..right, 0.5 = center |
| `Osc{i}MinNote`, `Osc{i}MaxNote` | key window of the oscillator (0..127), used for splits; not needed for a full-range oscillator |

The oscillator's volume, panning and tuning are **offsets on top of the map entries**: an entry with
gain 0.5 under an oscillator at -6 dB plays at -12 dB. A reader combines the two; a writer can put a
common offset of all entries on the oscillator and only the differences into the map. Since the
device ignores the map's pan column, `Osc{i}Pan` is the only way to pan a sample map at all (hw).

A patch which specifies nothing for oscillators 2 and 3 plays only oscillator 1 **(hw - every
single-oscillator conversion)**.

### 5.3 Filter

One filter path per layer.

| parameter | encoding / meaning |
|-----------|--------------------|
| `FilterState` | 0 = *Active*, 1 = *Bypass*, 2 = *Off*. The filter only takes effect at 0 |
| `Filter12Type` | 0-17: `type = index / 6` (0 LP, 1 HP, 2 BP), `slope = index % 6` (0-2 = 12 dB, 3-5 = 24 dB; within each triple: plain, *sat.*, *dirty*). Plain types: LP 12 dB = 0, LP 24 dB = 3, HP 12 dB = 6, HP 24 dB = 9, BP 12 dB = 12, BP 24 dB = 15 (the hints of device-written files confirm the table, corpus) |
| `Filter1CutOff` | `f = 8.1758 Hz x 2^(11.25 x)`: 8.18 Hz .. 19912 Hz (fw, confirmed to 2 ppm) |
| `Filter1Reso` | 0..1 = 0..100 % |
| `Filter1VeloAmount` | bipolar, velocity to cut-off |
| `Filter1EnvAmount` | bipolar, filter envelope to cut-off |
| `Filter1Keytrack` | key tracking, `4x - 2`: **0.75 = +100 %** (hw). A writer which used the bipolar encoding `(kt + 1) / 2` put full tracking at +200 % on the device |
| `Filter1EnvDelay`, `Filter1EnvAttack`, `Filter1EnvDecay`, `Filter1EnvSustain`, `Filter1EnvRelease` | the cut-off envelope, laws in 5.5 |
| `Filter1AttackCurve`, `Filter1DecayCurve`, `Filter1ReleaseCurve` | its curve types - **note the prefix `Filter1`, not `Filter1Env`** |

### 5.4 Amplifier

| parameter | encoding / meaning |
|-----------|--------------------|
| `AmpEnvDelay`, `AmpEnvAttack`, `AmpEnvDecay`, `AmpEnvSustain`, `AmpEnvRelease` | the amplitude envelope, laws in 5.5 |
| `AmpEnvAttackCurve`, `AmpEnvDecayCurve`, `AmpEnvReleaseCurve` | its curve types (prefix `AmpEnv`) |
| `AmpVeloAmount` | bipolar, velocity to level; 1.0 = +100 % = full velocity sensitivity. There is no velocity curve table in the firmware (fw) |

### 5.5 Envelopes

All envelopes share one layout and the same laws:

| stage | law (x = stored value) | range |
|-------|------------------------|-------|
| Delay | `t = 2 x^2` s | 0 .. 2 s |
| Attack, Decay, Release | `t = 0.06 x 1000^x - 0.06` s; **x = 0 is instant** (hw) | 0 .. 59.94 s |
| Sustain | level `x` | 0..1 = 0..100 % |
| AttackCurve | enum 0 = *Exp*, 1 = *RC*, 2 = *Lin* | |
| DecayCurve, ReleaseCurve | enum 0 = *Exp*, 1 = *Exp alt*, 2 = *Lin* | |

Inverse for a writer: `x = log(1 + t / 0.06) / log(1000)`. The display of the device shows
`60 x 10^(3 (x - 1)) - 0.001` s **(fw)**, which is 59 ms more than the sound engine plays - an
earlier version of this document and of ConvertWithMoss used the display law, which made every stage
60 ms too short and wrote everything below 0.06 s as instant. Measured on an Iridium MK2 with OS 4.0.6
with linear pitch envelopes and `EnvelopeVar` at 0: values written for 0.07, 0.1, 0.25, 0.5, 1 and
2 s with the display law played 0.011, 0.041, 0.190, 0.441, 0.940 and 1.940 s, for the attack, the
decay and the release alike and within 2 ms on repeated notes **(hw)**.

The curve runs continuously from instant upwards. For the amplitude envelope a very short stage
opens or closes the amplifier so fast that it clicks unless the audio is at zero at that moment.
Measured on the device, **a stage of 10 ms renders without a click** **(hw)** - the test wrote it as
0.07 s with the display law. See section 7.2 for how to choose between 0 and 10 ms.

Curve types: *Exp* and *RC* (attack) / *Exp alt* (decay, release) are the two curved variants, *Lin*
is linear. Write the option index. The device rounds the value of an enumeration to the nearest
index when it shows the option name and when it saves the hint text (fw); earlier versions of
ConvertWithMoss wrote **0.5** for *Exp alt*, so a reader should accept any value other than 0 and 2
as *Exp alt*.

### 5.6 Free envelopes

`FreeEnv{n}Delay`, `FreeEnv{n}Attack`, `FreeEnv{n}Decay`, `FreeEnv{n}Sustain`, `FreeEnv{n}Release`
and `FreeEnv{n}AttackCurve`, `FreeEnv{n}DecayCurve`, `FreeEnv{n}ReleaseCurve` (prefix `FreeEnv{n}`
for both groups), `n` = 1..3. A free envelope does nothing by itself; it is routed through a matrix
slot, typically to the pitch of an oscillator (a pitch envelope).

### 5.7 Modulation matrix

Each of the 40 slots has four parameters:

| parameter | encoding / meaning |
|-----------|--------------------|
| `MatrixOnOff{k}` | 0 = *Disabled*, 1 = *Active* |
| `MatrixSrc{k}` | source index: 4-6 = *Free Env 1-3*, 7-12 = *LFO 1-6*, 14 = *Wheel* (the modulation wheel), the same in every format version (fw, corpus). The firmware 4.0.6 lists 57 sources - 0 *Off*, 1 *Amp Env*, 2 *Filter1 Env*, 3 *Filter2 Env*, 4-6 *Free Env1-3*, 7-12 *LFO 1-6*, 13 *Komplex*, 14 *Wheel*, 15 *Pitch Bend*, 16 *Per-Note Pitch Bend*, 17 *After Touch*, ... 32 *Keytrack*, 33 *Velocity*, 34 *Release Velocity*, ... 40 *Constant* (fw); version 15 inserted *Per-Note Pitch Bend* and *Release Velocity*, so only the indices up to 15 are stable across versions. The other sources are not covered |
| `MatrixDst{k}` | destination index, **which depends on the format version** (table below): 1 = *Pitch* (all three oscillators at once) and 2-4 = *Osc1-3 Pitch* in every version |
| `MatrixAmount{k}` | bipolar, `2x - 1` = -100..+100 % |

**The device resolves an enumeration by its name.** When it loads a patch it looks the hint of every
enumeration up among the option names of the parameter and takes the index of the option it finds;
only an empty hint, or one which names no option, leaves the stored value in place **(fw:
`PatchLib::migrateIOAreaToStagingArea` calls `Param::DiscreteNameToIndex`)**. The hint therefore wins
over the value - a writer which changes the value of an enumeration must change or clear its hint as
well. This is what keeps old patches working while the list of destinations grew:

| destination | version 9 | versions 10-13 | version 14 | version 15 |
|-------------|-----------|----------------|------------|------------|
| *Filter1 Cutoff* | 99 | 99 | 100 | 109 |
| *VCA* | 105 | 107 | 108 | 117 |

The indices of versions 9-14 come from the hints of device-written patches (corpus), those of
version 15 from the list of the firmware 4.0.6 (fw): version 14 added *DF Tilt* in front of the
filter destinations and version 15 the nine *SD1-3 Position/Detune/Spread* destinations of the Seeds
oscillators. A reader matches the hint first and falls back to the index of the file's version; a
writer writes the name and the index of the version it writes.

The matrix adds `source x amount` to a sum per destination, clamps the sum to +/-100 and the voice
applies it in the destination's own units **(fw: `ModMatrix::calc`)**. A destination can be
registered to **square the amount**, keeping its sign, before it is applied.

* **Pitch destinations: the amount is squared, +/-100 % = +/-24 semitones.** *Pitch* and
  *Osc1-3 Pitch* carry the squaring flag and the voice multiplies their sum with 24 semitones
  **(fw)**. An amount of +50 % with a source at full level raises the pitch by `0.5^2 x 24` = 6
  semitones; +14.4 % gives half a semitone. A pitch envelope is therefore a free envelope into
  *Osc{i} Pitch* with an amount of `sign x sqrt(|semitones| / 24)`. The factory patches agree: their
  vibratos on the modulation wheel use control amounts of 11-20 %, which is 29-96 cents squared and
  would be 2.6-4.8 semitones linear (corpus). Measured on an Iridium MK2 with OS 4.0.6: a free envelope
  held at full level into *Osc2 Pitch* at +50 % plays the second oscillator 6.00 semitones above the
  first, at two different keys, and a converted vibrato of 50 cents at 5 Hz swings +/-49.97 cents at
  4.999 Hz, where the linear amount of earlier versions swings +/-1.04 cents, and a converted pitch
  envelope of 12 semitones starts every note 12.02 semitones high **(hw)**. Earlier versions of this document and of ConvertWithMoss
  used `semitones / 24`, which writes every pitch modulation below 24 semitones too shallow: a
  50 cent vibrato became one of about 1 cent.
* **Filter cutoff (*Filter1 Cutoff*): linear, +/-100 % = +/-135 semitones.** The voice adds the sum
  to the cutoff in the units of `Filter1CutOff`, whose range of 0..1 covers 11.25 octaves; the key
  tracking of the filter is scaled into the same units with 1/135 per semitone **(fw)**. +8.9 % with a
  source at full level raises the cutoff by an octave. Measured on an Iridium MK2: a unipolar square
  LFO at +8.89 % switches a resonant 24 dB low-pass at 1000 Hz between 1000 Hz and 11.75 +/- 0.5
  semitones above it; a law of 120 semitones would give 10.7 **(hw)**.
* **VCA destination.** The amplifier already plays at full level, so a positive modulation is
  clamped and only a *negative* excursion is audible. The attenuation reached at the end of the
  swing follows the level law of `Osc{i}Vol`: `dB = 40 log10(1 - |amount|)`; silence (96 dB down) is
  an amount of -99.6 %, i.e. the end of the range. A tremolo is an LFO set to **unipolar** with a
  **negative** amount, so it only attenuates downwards from the full level; a bipolar LFO would
  press the first half of every cycle against the upper end of the amplifier and leave the rectified
  half of the waveform. The destination is not squared (fw); its dB scale is derived from the level
  law and not measured.

### 5.8 Low frequency oscillators

| parameter | encoding / meaning |
|-----------|--------------------|
| `Lfo{n}Speed` | `rate = 1/240 + (100 - 1/240) x^4` Hz: 240 s per cycle .. 100 Hz (fw). The midpoint gives 6.25 Hz, the default the manual documents |
| `Lfo{n}Sync` | 0 = *Off*, 1 = *On*: the rate is a note length instead of Hertz, which needs the song tempo |
| `Lfo{n}Global` | 0 = *Poly* (one LFO per voice, restarted with every note), 1 = *Global*, 2 = *Single Trig* (fw) |
| `Lfo{n}Shape` | 0 *Sine*, 1 *Triangle*, 2 *Square*, 3 *Saw (down)*, 4 *Saw (up)*, 5 *S&H* (fw) |
| `Lfo{n}Polarity` | 0 = *Bipolar*, 1 = *Unipolar* (fw) |
| `Lfo{n}Phase` | 0..1 = 0..360 degrees start phase; from **0.9986** upwards the LFO runs free |
| `Lfo{n}Delay` | `t = 20 x^2` s (fw) |
| `Lfo{n}Attack` | `t = 10 x^2` s, fade-in of the modulation (fw) |
| `Lfo{n}Decay` | `t = 10 x^2` s, fade-out; **1.0 = Off** (fw) |

### 5.9 Value laws at a glance

| quantity | stored value x -> unit | inverse |
|----------|------------------------|---------|
| envelope stage time | `0.06 x 1000^x - 0.06` s, 0 = instant | `log(1 + t / 0.06) / log(1000)` |
| envelope delay | `2 x^2` s | `sqrt(t / 2)` |
| level (`Osc{i}Vol`) | `40 log10(x)` dB | `10^(dB / 40)` |
| filter cut-off | `8.1758 x 2^(11.25 x)` Hz | `log2(f / 8.1758) / 11.25` |
| key tracking (oscillator and filter) | `400 x - 200` % | `(kt + 2) / 4` |
| bipolar amounts | `200 x - 100` % | `(a + 1) / 2` |
| unipolar amounts (sustain, resonance) | `100 x` % | `x` |
| coarse pitch, bend range | `x - 24` semitones | `semitones + 24` |
| fine pitch | `200 x - 100` cents | `(cents / 100 + 1) / 2` |
| LFO rate | `1/240 + (100 - 1/240) x^4` Hz | `((rate - 1/240) / (100 - 1/240))^(1/4)` |
| LFO delay / attack / decay | `20 x^2` / `10 x^2` / `10 x^2` s | `sqrt(t / max)` |
| map gain | linear factor, `20 log10(g)` dB | `10^(dB / 20)` |
| map positions | fraction of the frame count | `frames / count` |
| matrix amount on a pitch destination | `sign(a) x a^2 x 24` semitones | `sign x sqrt(|semitones| / 24)` |
| matrix amount on *Filter1 Cutoff* | `a x 135` semitones | `semitones / 135` |
| glide time (`GlideRate`) | `2 x^2` s | `sqrt(t / 2)` |

### 5.10 Voices and glide

| parameter | encoding / meaning |
|-----------|--------------------|
| `PolyMonoMode` | 0 = *Poly*, 1 = *Mono* |
| `LayerVoices` | the voices of a layer, option index + 1 (3 = 4 voices, the default). Only used in the split mode, which shares the 16 voices of the instrument among its layers (manual) - it does not limit the polyphony of a patch in the other modes |
| `GlideOnOff` | 0 = *Off*, 1 = *On* |
| `GlideRate` | `t = 2 x^2` s, 0..2 s; the default 0.15 is 45 ms. The glide takes this time for every interval: the step per block of 128 samples is `interval x 128 / (88200 x x^2)` (fw). Measured: 0.7071 glides for 1.000 s over 200 and over 400 cents, on a straight line of pitch (hw) |
| `GlideType` | 0 = *Onset*, glides to every new note; 1 = *Legato*, only to a note played while another one is held (manual) |
| `PitchVariation` | analog drift: every oscillator of every voice gets a random detune, scaled by this value; default 0.17 (manual) |
| `EnvelopeVariation` | the attack and decay of every note deviate randomly from their values, scaled by this value; default 0.35 (manual) |

Waldorf keeps `PitchVariation` and `EnvelopeVariation` at their defaults in 234 of the 242 sample-based
factory patches (corpus); a patch which does not store them gets the defaults (2.3).

In the *Mono* mode every new note retriggers the amplitude envelope - its level follows the velocity of
each note - unless `AmpEnvSingleTrig` is on (manual, hw), and a note which does not overlap the previous
one restarts the sample: the sounding waveform is cut within 2-3 samples and the sample starts again
from its first frame. With an instant attack and a sustained sample this is an audible click, while an
overlapping note glides on without one (hw).

## 6. Metadata: name, author, bank, attributes

* **Name** (offset 8): what the device displays and files the patch under. 32 ASCII characters;
  everything beyond is cut off. The file name on disk is independent of it (section 7.4).
* **Author** (40) and **Bank** (72): the device groups and filters its library by both, so they are
  worth filling. Because there is a field for the bank, a name of the form `Bank - Preset` does not
  need to repeat the bank - and in a 32 character field the bank would eat the room which tells the
  presets of one bank apart (`Full Arco String - Arco Strings Lo` and `... Hi` both end as
  `Full Arco String - Arco Strings`).
* **Attributes** (104, 136, 168, 200): up to four free tags. They are **not** a type followed by
  modifiers but four slots drawn from **one shared vocabulary**, used in any order. The device lists
  them next to the name and offers them as filters, so a writer should use the **spelling of the
  factory sound sets** - otherwise *Keyboard* ends up in the filter list next to the *Keys* of every
  factory patch and each finds half of the sounds. Words used by the factory content, most frequent
  first: *Synth, Pad, Atmo, Keys, FX, Percussive, Epic, PPG, Lead, Bass, Arp, Noise, Granular,
  Strings, Sequenced, Vocal, FM, Cinematic, Resonator, Organ, Loop, Bells, Kernel FM, Experimental,
  Mono, Piano, Drum, Kernels, Sample, World, Monophon, Wavetable, Pipe, Winds, Space, Chromatic
  Percussion, Drone, Pluck, Brass*. Leave unused slots empty rather than writing a placeholder such
  as *Unknown*, which would become a filter entry of its own. ConvertWithMoss maps every attribute -
  the category of the analysis, its keywords and the tags of a source pack - onto this vocabulary
  regardless of case (*KEYS* -> *Keys*, *DRUMS* -> *Drum*, *atmospheric* -> *Atmo*) and capitalizes
  a word the factory sets do not use (*VINTAGE* -> *Vintage*); the device shows *KEYS* and *Keys* as
  two filter entries otherwise.

## 7. Writing a patch

### 7.1 Minimal parameter set

The 18 parameters of section 3 are sufficient for a sample-based patch which loads and plays
**(hw)**. In words, per oscillator `{i}` which plays a map:

1. `Osc{i}Type = 2` (*Particle*) and `Osc{i}ParticleSampleMode = 2` (*Normal*).
2. `Osc{i}Keytrack = 0.75` (+100 %), `Osc{i}CoarsePitch = 24`, `Osc{i}FinePitch = 0.5` - the tuning
   belongs into the map's pitch column, not here.
3. `Osc{i}Vol`, `Osc{i}Pan`, `Osc{i}PitchBendRange` as wanted (1.0 / 0.5 / 26 for 0 dB, center, +2
   semitones).

Write nothing for the oscillators you do not use (section 5.2). Once per layer: `FilterState` (1 for
no filter, or 0 plus the `Filter1*` parameters), the eight `AmpEnv*` parameters and `AmpVeloAmount`.
Add `Matrix*`, `FreeEnv*` and `Lfo*` parameters only for the modulations you actually use - and then
all parameters of the LFO or envelope you use.

ConvertWithMoss never writes more than seven matrix slots and fills them from slot 1 on, in the
order a player looks for them, so a patch shows its modulations at the top of the matrix page
without gaps: first the modulation wheel into *Filter1 Cutoff* (*Wheel*, bipolar; the amount is the
share of the filter range which the wheel at its top adds - 291 of the 1,787 factory patches of an
Iridium MK2 carry such a slot), then the pitch envelopes of the oscillators 1-3 (*Free Env 1-3*
into *Osc1-3 Pitch*, the free envelope with the index of the oscillator), a vibrato (*LFO 1* into
*Pitch*, one slot for all three oscillators), a tremolo (*LFO 2* into *VCA*, unipolar, negative
amount) and a modulation of the filter cutoff (*LFO 3* into *Filter1 Cutoff*, bipolar). A patch
with only a wheel modulation therefore has it in slot 1. The remaining slots and LFOs 4-6 are left
untouched for the user; the reader (section 8) finds each modulation by its source and destination
in any slot, so it does not depend on this order.

### 7.2 Pitfalls seen on the device

All of these were found by loading written patches on an Iridium **(hw)**:

* **Resource offsets** must accumulate across maps (2.2), or multi-oscillator patches show *Find
  Sample Map*.
* **Sample paths**: relative, no drive prefix, file names identical to the files on disk (4.3).
  *Export -> With Samples* doubles a drive prefix.
* **Map values** outside `0..1` -> *Locate Samples*. Clamp positions; compute them from the real
  frame count of the written WAV (after any re-sampling).
* **End every map with `\n` and a NUL** and count both in its length (4.4). Without them the device
  reads beyond the map, and the TrackPitch flag of the last entry picks up digits from its memory,
  so the entry plays at a fixed pitch - in some patches and not in others.
* **Full sample memory** on the device shows the same screens as a broken file; check it first when
  a file that looks correct will not load.
* **`Osc{i}ParticleSampleMode = 2`** or single samples do not track the keyboard.
* **Key tracking is 0.75 for 1:1**, not 1.0 - for the oscillator and for `Filter1Keytrack` alike.
  1.0 is +200 % and opens the filter twice as far per octave.
* **Very short envelope stages click**; 0 is instant and clicks unless the audio is at zero at that
  moment:
  - *Release*: write at least 10 ms. A note-off lands at an arbitrary point of the waveform (for a
    looped entry at full level), so an instant release always clicks.
  - *Attack*: an instant attack is fine - and is what the device itself uses for percussive sounds
    (35 % of 141 patches written by an Iridium set it to 0) - **when the sample starts near zero**.
    Lifting every short attack erases the strike of a percussive recording. ConvertWithMoss lifts an
    attack below 10 ms to 10 ms only when the first frame at the entry's start is above 2 % of the
    sample's peak level; otherwise it writes the attack of the source.
  - *Attack 0 + Decay 0 + Sustain below 100 %* pops: the device snaps to the 100 % attack peak and
    drops instantly to the sustain level. Write such an envelope with sustain 1.0 and apply the
    sustain level to the map gains instead.
* **Write every parameter of a feature you use**, not only the ones which differ from some default
  (2.3).
* **Pan with `Osc{i}Pan`**; the map's pan column does nothing.
* **Filter**: `FilterState` must be 0 for the `Filter1*` parameters to matter.

And these were read from the firmware of the Iridium MK2 (4.0.6) **(fw)**:

* **The importer copies the samples of the first layer only** (7.5, confirmed on the device). Name
  every sample of a later layer in a map of the first layer as well.
* **A cross-fade of 50 % pumps** when the two ends of the loop do not match (4.1): the blend of
  the device is not constant-power. Keep cross-fades short, or render an equal-power cross-fade
  into the audio and write 0.
* **At most 1024 entries per map** (4.1); a larger multi-sample has to be split over the
  oscillators or the layers.
* **A missing parameter is set to its default**, not left at the value of the previously loaded
  patch: the loader fills every parameter which the file does not name with its default value
  before the patch is applied. Writing every parameter of a feature is still the safe rule, since
  the defaults are the ones of the firmware version which loads the file.

### 7.3 Sample files

Standard WAV files. Every hardware test used **16-bit PCM at 44.1 kHz**; the device also plays
higher resolutions, at a cost in performance (the ConvertWithMoss default is therefore to re-sample
to 16 bit / 44.1 kHz). 64-bit float WAVs and files with absurd sample rates (single-cycle waveforms
stored at 12 MHz) do not play **(hw)**. Mono and stereo files are referenced the same way. Loops
live in the map, not in a `smpl` chunk of the WAV.

### 7.4 File naming and import

* The device exports patches as `NNNNN-<name>.qpat` with a 5-digit import number (e.g.
  `05002-Name.qpat`) and, on import, assigns a patch to the number in its file name. The number is
  **only in the file name** - it appears nowhere in the binary and the sample paths use the patch
  name - so files can be renumbered freely. Keep the numbers unique across everything that is
  imported together.
* The import screen of an Iridium MK2 shows about **43 characters** of a file name and clips the
  rest **(hw)**. With the prefix and `.qpat` that leaves about 30 characters for the name part.
* The name field (32 characters) is what the browser shows; the file name is what the import list
  shows. Both are cut at the end, which is usually the part that tells similar presets apart.

### 7.5 Writing a multi-layer patch

A layer multiplies the capacity of a patch: three oscillators each, so **3 sample maps with one
layer, 6 with two and 12 with the four of the MK2**. It also gives each set of maps its own filter,
amplifier envelope and modulation, which the three oscillators of a single layer have to share.

What to put in a layer, and what not:

* **Sounds which are meant to be heard together** belong in separate layers (Multi/Layered mode) -
  that is what layers do.
* **A velocity split must not be spread over layers**: there is no layer velocity range, so every
  layer would answer every note. Keep velocity zones inside the sample maps of one layer.
* **A key split** can use either mechanism: the map entries carry key ranges, and Split mode adds a
  key window per layer.

The mechanics:

1. Build each layer as a complete patch - header, parameters, sample maps - and store them one after
   the other, the first one at offset 0.
2. In **every** header write the layer count (1 for two layers, 2 for four) and the mode; write the
   absolute file offsets of the layers 2, 3 and 4 at 432, 440 and 444. The offset of layer *k* is
   the sum of the sizes of the layers before it, a size being
   `512 + 68 x parameters + sample map bytes`.
3. For the layer count 2 store **four** layers even when fewer are used: that is what the device
   does, and its loader checks the offsets of the layers 3 and 4. Give an unused layer `LayerActive`
   = 0 and no resources.
4. Write `TimbreMode` (2 for Multi), `MultiAllocMode` = 0 and `LayerActive` = 1 into every sounding
   layer.

When the device **imports** a patch it copies the patch into its patch memory and the samples into
its sample memory - but it collects the samples from the sample maps of the **first layer only**:
the importer reads the resource table of the first header of the file, walks its maps and copies
each file they name (fw, `PatchLib::importPatch`). A sample which only a later layer names is never
copied, and when the patch is loaded that layer reports *loading samples/<patch>/<file>.wav failed*
and stays silent, while a later layer which only re-uses the samples of the first plays **(hw)**.
So name every sample of the later layers in a map of the first layer as well, in an entry which
never plays: ConvertWithMoss appends such entries to the last map of the first layer with the
velocity window `0 0` (a note-on with the velocity 0 is a note-off, and the device compares the
velocity as an integer against both ends, so the entry is never picked for a note and costs no
voice), a gain of 0 and the key range `0 0`, which keeps it out of the entry lists of the other
keys. Verified on an Iridium MK2 with OS 4.0.6 **(hw)**: a two-layer test patch whose second layer
brings a sample of its own plays both layers after the import, while the same patch without the
entry fails with *loading samples/.../L2_saw_octave.wav failed*.

A written two-layer patch loads and its second layer carries its own filter and amplitude envelope
**(hw)**; the samples of the second layer were the open problem described above. ConvertWithMoss
writes the four-layer layout of the MK2 - count 2, version 15, four stored layers, the unused ones
with `LayerActive` 0 and without oscillators - when a source needs three or four layers. Verified
on an Iridium MK2 with OS 4.0.6 **(hw)**: a written four-layer patch with one sample pitch per
layer plays all four pitches after the import, and a three-layer patch with its switched-off
fourth layer plays its three.

## 8. Reading a patch

* Check the magic at 0 and read the version at 4 (section 2.5).
* Read the header strings (section 2.1) and the four attributes.
* Walk the resource table; keep types 4-6 as the maps of oscillators 1-3 and **skip** every other
  type (wavetables 1-3, the MK2's type 7, anything newer).
* Read `count` records of 68 bytes from 512 into a name -> value dictionary. Ignore unknown names
  and the hint text; if a name repeats, the last occurrence wins in ConvertWithMoss.
* The resource data starts at 512 + 68 x count; slice each map by its table entry and decode it as
  ASCII. Split at `\n`, trim each line (this removes the NUL which device-written maps end with),
  stop at the first empty line, split at TAB. Do not insist on all 16 columns - treat missing
  trailing columns as their defaults (no loop, forward, key-tracked).
* Strip a drive prefix from the path, resolve it against the patch's folder, and use the WAV's frame
  count to turn the fractions into frames like the firmware does (section 4.2):
  `(int) (0.001f + (float) (N - 1) * fraction)`.
* Read a velocity with a decimal point as a fraction of the range (4.1), skip an entry whose
  velocity window ends at 0 (it only names a sample for the importer), and read only a TrackPitch
  of 1 as key tracking - a 2 and the garbage values behind an unterminated map play at a fixed
  pitch.
* A patch without any type 4-6 resource is not sample based (wavetable, virtual analog, ...).
* Apply `Osc{i}CoarsePitch`/`FinePitch`, `Osc{i}Vol` and `Osc{i}Pan` as offsets on top of every
  entry of the oscillator's map, and `LayerVolume`, `LayerGain` and `LayerPan` on top of the
  oscillators (2.6). An oscillator with `Osc{i}Vol` 0 is silent, whatever its map says.
* Take the filter only when `FilterState` is 0; the saturated and dirty variants of `Filter12Type`
  can be reduced to their plain type.
* Scan the 40 matrix slots: an active slot with a free envelope source and an oscillator's pitch as
  destination is that oscillator's pitch envelope; an LFO into *Pitch* or an oscillator's pitch is a
  vibrato, an LFO into *VCA* a tremolo, an LFO into *Filter1 Cutoff* a filter modulation and the
  *Wheel* into *Filter1 Cutoff* the modulation-wheel control of the cutoff (convert the amount with
  the laws of 5.7 - the pitch amount is squared, the cutoff amount is linear). Match a destination by its hint
  and only without one by the index of the file's version (5.7). Skip an LFO with
  `Lfo{n}Sync = 1` unless you know the tempo, and read a phase at or above 0.9986 as free running.
* Accept non-integral enumeration values (section 5.5).
* **Layers** (section 2.6): read the u16 at 428. 1 = one more layer at the offset in 432; 2 = up to
  three more at 432, 440 and 444. Accept an offset only if it is non-zero, inside the file and
  points at a magic, then parse that layer exactly like the first (its resource offsets are relative
  to its own parameter block). In version-15 files drop the layers whose `LayerActive` is 0. In
  split mode (`TimbreMode` 1) the key window of a layer is `LayerMinNote`/`LayerMaxNote`, which are
  MIDI notes; the older `LayerSplitMinKey`/`MaxKey` run 0..60 over five octaves from MIDI 36 (C1).
  Ignore the layer fields in the headers of the later layers.
* Read the layer mode at 430 only when 428 is non-zero, the instrument byte at 436 only from version
  9 on, and the bytes 437-511 only as the two offsets of a code-2 file: older files hold garbage in
  all of them (section 2.5).

## 9. Open questions

* **Enumeration values between two options.** The display and the hint text round to the nearest
  option (fw); what the sound engine makes of a value such as the 0.5 which earlier versions of
  ConvertWithMoss wrote for *Exp alt* has not been checked. Writing the index avoids the question.
* **`Expalt` curve value.** The enumeration index of *Exp alt* is 1; ConvertWithMoss writes 0.5 and
  the device accepts the file, but how it rounds a non-integral enumeration value has not been
  checked. Writing 1.0 matches the index.
* **VCA destination scale.** The dB per percent of the matrix destination *VCA* is derived from the
  level law and not measured on the device.
* **Loop cross-fade unit.** Column 14 is a fraction and the device plays it (hw); whether it
  relates to the loop length or to the sample length is not verified. Device-written maps fit a
  loop-relative reading (521 looped entries with a cross-fade on one card: 517 fit in front of the
  loop start relative to the loop length, 61 relative to the sample length).
* **Envelope delay.** The law of the delay stages (`2 x^2` s) is read from the display formatter and
  not measured; `EnvelopeVar` (default 0.35) randomizes attack and decay per note, so zero it before
  measuring envelope times on the device.
* **Layers.** Three-layer files and layer count codes other than 1 and 2 have not been observed;
  whether an MK2 can write code 1, and whether a first-generation instrument accepts code 2, is
  unknown. The loader accepts the codes 1, 2 and 3 as "more than one layer" and loads the layers 3
  and 4 for the codes 2 and 3 (fw). A written two-layer patch loads and, with the entries of
  section 7.5, plays its own samples in the second layer; a written four-layer patch (count 2,
  version 15) plays all four layers on an Iridium MK2 (hw). The first generation holds two layers -
  the Iridium and Iridium Core manuals switch "between the two layers by tapping on the Layer 1 or
  Layer 2 button", and Waldorf describes the MK2 upgrade as raising the layer count from 2 to 4 -
  so a count-2 file is for the MK2 generation only; how a first-generation OS reacts to one is not
  known (manual).
* **Oscillator type indices.** *Off* is 5 in versions 9-11 and 6 in version 15; how an instrument
  interprets the index of a file of another version (e.g. a version-14 file with `Osc2Type` = 5 on
  an MK2) is untested - hence the advice to write nothing for unused oscillators.
* **Resource type 7.** Seen only in MK2 patches which use the Param Sequence; the content (9 byte
  records of u8, u32, f32 - by their values a step, a parameter id and a value) is not decoded.
* **Where the start of an entry is compensated.** The voice set-up of the Normal mode subtracts
  the start frame twice from the loop points (4.2), yet the loop plays in place; the part of the
  render path which puts it back has not been found.
* **Header padding** (the u16 at 234, the 3 bytes at 437 and the 64 bytes at 448) is unknown; write
  zeros.
* **Not covered by this document**: wavetable resources (types 1-3) and the non-Particle oscillator
  types, the granular sample modes (`Osc{i}ParticleSampleMode` 0 *Granular* and 1 *Live Granular*),
  the selection rule for overlapping entries (`Osc{i}ParticleSelectionRule`: *Round Robin*,
  *Reverse Robin*, *Ping Pong*, *Random Robin*, *Random*, *None*), filter 2 and the saturated/dirty
  filter variants, the matrix sources and destinations other than those of 5.7, split key windows
  (`Osc{i}MinNote`/`MaxNote`), the *Global* and *Single Trig* LFO modes, tempo-synchronized LFOs.

## Appendix: how ConvertWithMoss maps the format

For readers who want to compare an implementation against ConvertWithMoss (`WaldorfQpatDetector`,
`WaldorfQpatCreator`):

* **Reading**: one layer -> one multi-sample, the layers after the first named `<name> 2`,
  `<name> 3`, `<name> 4`. Both layer counts are followed, a layer whose `LayerActive` is 0 is
  skipped, an unknown resource type is skipped and a layer without a sample map no longer discards
  the layers which have one. `LayerVolume`, `LayerGain` and `LayerPan` are applied to the zones of
  the layer, the key windows of a layer are not. One sample map -> one group named *Sample Map
  1/2/3*; filter, amplifier envelope, velocity amount, vibrato and tremolo of the layer are applied
  to every zone. An entry whose velocity window ends at 0 is skipped. The four attributes
  are fed through its keyword detector to derive a category and keywords; author -> creator, bank ->
  description. Matrix destinations are matched by their hint, then by the index of the version. Matrix
  pitch amounts are squared and scaled to its model's depth of 12000 cents
  (`depth = sign x amount^2 x 24 x 100 / 12000`), a cutoff amount with 135 semitones
  (`depth = amount x 135 x 100 / 12000`), the VCA amount to its 96 dB volume depth. *Mono* becomes a
  polyphony of 1 played legato, an active glide the portamento time (`2 x GlideRate^2` s).
* **Writing**: split-stereo groups are combined into stereo files; a group whose zones stack
  (overlap in key *and* velocity) is partitioned into layers of non-overlapping zones, largest
  first. The resulting groups fill the three oscillators of a layer and then, when the option allows
  it, those of a second layer - 3 groups with one layer, 6 with two; whatever does not fit is folded
  into the last map, as it always was - which changes what is heard, since those groups were
  separated because they sound at the same time and entries which overlap inside one map alternate
  instead (section 4.1). Everything beyond the first layer is written in the Multi/Layered mode. The
  common gain and panning of a group go to `Osc{i}Vol` / `Osc{i}Pan`, the remainder into the map.
  Filter, amplifier envelope, velocity and LFOs are taken from the first zone of the first group;
  the pitch envelope from the first zone of each group. A polyphony of 1 or a legato source is
  written with *Mono*, a portamento as the glide (*Legato* for a legato source, *Onset* otherwise), a
  key tracking which all key-tracked zones of a group share as `Osc{i}Keytrack`. Matrix destinations
  are written with their name and the index of the written version (5.7). The samples of the later layers are named
  in the last map of the first layer in entries which never play (7.5).
* **Policies**: the de-click and flat-envelope rules of section 7.2; hold + decay are added into the
  Decay stage; a pitch envelope which starts at a level above 0 is written as attack 0 and a decay of
  the source's attack time (an unset start level is written as a normal envelope); the preset name drops a leading bank because the bank field holds it (unless
  an explicit bank option replaces the source's bank, in which case the name keeps it as long as it
  fits 32 characters); categories are translated into the factory spelling (*Keyboard -> Keys, Bell
  -> Bells, Percussion/Hi-Hat/Kick/Snare/Clap -> Percussive, Loops -> Loop, Acoustic Drum -> Drum,
  Monosynth -> Monophon, Orchestral -> Cinematic, Ensemble -> Strings, Destruction ->
  Experimental*).
* **Options**: re-sample to 16 bit / 44.1 kHz (default on), *Author* and *Bank* overrides, the
  `NNNNN-` import number prefix with a configurable first number, short file names which keep the
  whole file name within 40 characters, and *Maximum number of layers* with 1 to 4 layers (1 by default,
  which writes exactly what earlier versions wrote). One or two layers use the layer count 1, which
  every instrument of the family has stored since the format version 8; three or four use the
  four-layer layout of section 7.5 with the format version 15.
