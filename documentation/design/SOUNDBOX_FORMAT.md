# Audiomodern Soundbox format (.sbpack / .sbset / .sbgrp / .amx)

## Containers

### .sbpack — a sound pack

A plain ZIP archive (entries are STORED / uncompressed):

| Entry                 | Content                                                        |
|-----------------------|----------------------------------------------------------------|
| `pack.amx`            | XML: pack metadata, preset names, all groups, sample name list |
| `_presets/p<N>.sbset` | XML: preset N (index into the `<Presets>` list of `pack.amx`)  |
| `_samples/s<N>`       | Sample file N *without file extension* (WAV, newer packs FLAC) |
| `*.png`               | Optional icon/cover images referenced from `pack.amx`          |

### .sbgrp / .sbset on disk (imported/user library)

After importing a pack, the plug-in stores each group as
`~/Library/Application Support/Audiomodern/Soundbox/Groups/<name>.sbgrp` (Windows analogous) —
the same group XML as in `pack.amx` but without the `name` attribute (the file name is the name,
with the `G` prefix of the pack group name stripped) and with the `f` attribute holding a sample
file path relative to the user sample folder instead of a `_samples` index. Presets go to
`Presets/Imported/<Pack>/<preset name>.sbset` and reference groups by their pack name (with `G`
prefix). Since resolving the user sample folder is a plug-in setting, ConvertWithMoss only reads
the self-contained `.sbpack`.

## pack.amx

```xml
<Audiomodern.Soundbox_Pack name="Cosmo" author="Audiomodern" desc="..." locked="0" sfolder=""
                           icon="Cosmo_Thumbnail.png" cover="Cosmo_Main_Cover.png"
                           c0="ffff6370" ... panel_alpha="1.0">   <!-- UI colors -->
  <Presets>
    <P name="CSM - Abstract Piano"/>          <!-- ordered: index N = _presets/pN.sbset -->
    ...
  </Presets>
  <Groups>
    <Audiomodern.Soundbox_Group gv="1" pv="1.0.0b19" name="GAbstract Piano 01">
      <S f="0">75.j.....C...</S>              <!-- f = index into _samples; text = sound blob -->
      ...
    </Audiomodern.Soundbox_Group>
    ...
  </Groups>
  <Sounds>
    <S>Abstract Piano Sustain 01_01.wav</S>   <!-- ordered: original file name of _samples/sN -->
    ...
  </Sounds>
</Audiomodern.Soundbox_Pack>
```

`pv` is the plug-in version that wrote the group and determines the sound blob size (see below).
Newer groups (pv ≥ 1.0.8) support round robins: the group gets `rrLayers="<count>" rrMode="0"`
and the `S` elements use `f0` instead of `f`, plus `bpm0="<int>"`, `rrMode="0"`,
`rrLayer="<int>"` attributes. `rrLayer` assigns the sound to one of the up to 8 round robin
layers of the group (the manual's *RR Layers*); `rrMode` selects the cycling: 0 = Sequential,
1 = Random non-rep, 2 = Random (the order of the plug-in's drop-down). ConvertWithMoss reads
each layer as one group with round robin play logic and writes the attributes for its round
robin groups. Verified with plug-in 1.2.1 (import test with two alternating layers): the
`rrLayers`/`rrLayer` attributes are honored both in old-style groups (`f`, 75 byte blobs,
pv 1.0.0b19) and new-style groups (`f0`/`bpm0`, 87 byte blobs, pv 1.2.0); ConvertWithMoss
writes the old-style variant. The factory packs contain no group with more than one round robin
layer. Independently of the RR layers, a single sound can carry *RR Sample Layers*: additional
sample files `f1`, `f2`, … on the same `S` element which alternate on that mapping only - not
read yet, no real-world example has been observed.

## Binary blobs — JUCE MemoryBlock Base64

All binary values in the XML files use `juce::MemoryBlock::toBase64Encoding`:
`<decimal byte count>.<encoded>` with the 64-character alphabet

```
.ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+
```

Six bits per character, filled little-endian: character *i* holds bits *i*·6 … *i*·6+5 of the
data, where bit *n* is bit (*n* & 7) of byte (*n* >> 3). All multi-byte fields inside the blobs
are little-endian; the structures are packed (no alignment).

## Sound blob (the `<S>` element text) — 75 bytes (pv ≤ 1.0.2), 87 bytes (pv ≥ 1.0.8)

Field names follow the plug-in's setter symbols (`AMSoundsManager::set…`).

| Offset | Type | Field               | Notes                                                  |
|--------|------|---------------------|--------------------------------------------------------|
| 0x00   | u32  | key low (copy)      | always identical to 0x26 (4093 of 4093 samples)        |
| 0x04   | u32  | root note           | MIDI note                                              |
| 0x08   | u8   | loop active         | 0/1                                                    |
| 0x09   | u8   | ping-pong loop      | 0/1 (loop type alternating)                            |
| 0x0A   | f32  | sample start        | fraction 0..1 of the sample length                     |
| 0x0E   | f32  | sample end          | fraction 0..1                                          |
| 0x12   | f32  | loop start          | fraction 0..1                                          |
| 0x16   | f32  | loop end            | fraction 0..1                                          |
| 0x1A   | f32  | fade in             | fraction 0..1                                          |
| 0x1E   | f32  | fade out            | fraction 0..1                                          |
| 0x22   | f32  | loop crossfade      | 0..0.5, assumed relative to the loop length            |
| 0x26   | u32  | key low             | MIDI note, inclusive                                   |
| 0x2A   | u32  | key high, exclusive | first key *not* played (drum maps use low+1; max 127)  |
| 0x2E   | u8   | reverse             | 0/1                                                    |
| 0x2F   | f32  | ?                   | always 0                                               |
| 0x33   | f32  | (speed?)            | always 1.0 (a single sample: 0.3136)                   |
| 0x37   | u32  | volume percent      | `setSoundVolume(int)`; 100 = full, corpus also has 80  |
| 0x3B   | f32  | pan                 | -1..1, left to right; editor shows L50..R50 (verified) |
| 0x3F   | i32  | tune semi-tones     | `setSoundTune(int)`; corpus: 0 and -12                 |
| 0x43   | u32  | velocity low        | inclusive, 0..127 (paired splits like 20/21 in corpus) |
| 0x47   | u32  | velocity high       | inclusive                                              |

The 87 byte variant appends 12 bytes: u8 flag at 0x4B (repitch/tempo-sync related, usually 1),
two zero bytes, f64 = 1.0 at 0x4E (stretch/speed factor), u8 = 0 at 0x56. These are ignored when
reading; ConvertWithMoss always writes the 75 byte variant, which current plug-in versions still
read (all Cosmo groups imported by plug-in 1.2.x keep their 75 byte blobs).

## Preset (.sbset)

```xml
<Audiomodern.Soundbox_Preset v="1" pv="1.0.0b19" g0="GAbstract Piano 01" g1="" g2="" g3=""
                             t0="134" t1="0" t2="0">  <!-- g0..g3: group per layer; t0: tempo -->
  <Layers>
    <L state="11...." settings="33....">    <!-- 4 layer elements -->
      <Arp active="0" .../>                 <!-- arpeggiator/sequencer, 32 step children -->
      <Effects active="1" .../>             <!-- 4 FX slots -->
    </L> ...
  </Layers>
  <Engines>                                <!-- the 4 LFO engines of the Modulation tab -->
    <E state="13...." sequence="128....">
      <P name="VOLUME LAYER 1" state="9...."/>  <!-- one assignment per modulated parameter -->
    </E> ...
  </Engines>
  <ArpsManager/>
  <Master volume="0.85" pan="0.0"> <Effects .../> </Master>
  <Midi> <ModWheel/> <Aftertouch/> <Timbre/> </Midi>
  <XYPAD active="0" .../>
</Audiomodern.Soundbox_Preset>
```

### Layer `state` blob — 11 bytes (`AMLayer` parameters)

| Offset | Type | Field  | Notes                          |
|--------|------|--------|--------------------------------|
| 0x00   | u8   | active | 0/1                            |
| 0x01   | u8   | solo   | always 0 in corpus             |
| 0x02   | u8   | link   | always 0 in corpus             |
| 0x03   | f32  | pan    | -1..1, not applied (see below) |
| 0x07   | f32  | volume | 0..1, default 0.75             |

### Layer `settings` blob — 33 bytes (`AMSamplerEngineMPE` parameters)

| Offset | Type | Field      | Notes                                             |
|--------|------|------------|---------------------------------------------------|
| 0x00   | u8   | voice mode | 0 = mono, 1 = legato, 2 = poly (default)          |
| 0x01   | f32  | glide      | knob 0..1; time law not calibrated                |
| 0x05   | f32  | transpose  | semi-tones, always 0 in corpus                    |
| 0x09   | f32  | fine tune  | cents (corpus: ±5, ±10)                           |
| 0x0D   | u32  | octave     | index with center 2 = ±0 octaves (assumed)        |
| 0x11   | f32  | attack     | knob 0..1; time = knob x 4.0 s (measured, linear) |
| 0x15   | f32  | decay      | knob 0..1; assumed 20 s law like release          |
| 0x19   | f32  | sustain    | 0..1 (UI shows 0-100%)                            |
| 0x1D   | f32  | release    | knob 0..1; time = knob x 20 s (measured)          |

The ADSR values are the normalized knob positions of the plug-in (the UI displays them as
0-100%, e.g. the defaults decay 0.2/sustain 1.0 show as 20%/100%). Neither the UI nor the user
manual documents the mapping of the percentage to a time; both laws were measured with plug-in
1.2.1 by recording a constant tone at known knob positions. Attack: the knob maps linearly to
the time with 100% = 4.0 seconds and the ramp itself is linear (25% = 1.0 s, 50% = 2.0 s,
100% = 4.0 s). Release: the knob maps linearly to the time until silence with 100% = 20 seconds
(10% = 2.0 s, 25% = 5.0 s, both complete tails within 1.5%; the two longer settings truncated
by the sample length confirm the slightly convex ramp shape). The decay knob is assumed to
follow the release law - it could not be measured directly since a decay is inaudible at the
100% sustain which all factory presets use.

### Effects

Each layer and the master carry an `<Effects>` element. Its `active` attribute switches the whole
section, `s0fx`..`s3fx` hold the effect type loaded into each of the 4 slots (0 = empty) and
`s0`..`s3` mark a bypassed slot with 1. One `<S SlotNum="n" type="t" .../>` child per slot holds
the parameters which differ from the defaults. The types follow the order of the plug-in's effect
menu: 1 = Reverb, 2 = Delay, 3 = EQ, 4 = Filter, 5 = Distortion, 6 = Chorus, 7 = Phaser,
8 = Lofi, 10 = Compressor/Limiter, 11 = Noise.

Only the **filter** (type 4) maps to the multi-sample model: `FREQUENCY` is the cutoff in Hertz
(15001 = fully open, the default), `RESONANCE` is the quality factor (default 0.707, up to about
3.145) and `TYPE` is the filter type: **0 = low-pass, 1 = band-pass, 2 = high-pass**, the order of
`juce::dsp::StateVariableTPTFilterType`. The plug-in binary holds the parameter as
`AMParameterEnum<juce::dsp::StateVariableTPTFilterType>` (mangled symbol
`15AMParameterEnumIN4juce3dsp26StateVariableTPTFilterTypeEE` in Soundbox 1.2.x), so the enumeration
is JUCE's and there is **no band-rejection**. The frequencies of the *Wired* pack confirm it: its
44 type 0 slots have a median cutoff of 5.4 kHz (low-pass), the 23 type 2 slots a median of 106 Hz
with a minimum of 50 Hz (high-pass, i.e. rumble filters) and the 10 type 1 slots a median of 543 Hz
(band-pass). *Cosmo*, *Kromium*, *Voxmotive*, *Starlit* and *Mechania* use type 0 only. A filter
type which is not one of the three is read as a low-pass; a band-rejection of the model is written
as the low-pass default, since the plug-in cannot store one. ConvertWithMoss reads the first active
filter slot of a layer - or of the master, if the layer has none - as the filter of the layer's
zones and writes a common zone filter back into the first slot. All other effects are not
converted.

### Modulation (LFO engines)

The `<Engines>` section holds the 4 LFO engines of the Modulation tab, one `<E>` element each. The
`state` attribute holds the oscillator settings, the `sequence` attribute the 32 steps of the
sequencer shape (32 f32 values, 0..1) and each `<P>` child assigns the LFO to one parameter of the
preset. An engine can modulate any number of parameters.

#### Engine `state` blob — 13 bytes

| Offset | Type | Field            | Notes                                                                        |
|--------|------|------------------|------------------------------------------------------------------------------|
| 0x00   | u8   | shape            | 0 = sine, 1 = triangle, 2 = square, 3 = saw, 4 = inverted saw, 5 = sequencer |
| 0x01   | u8   | retrigger        | 0/1, restart the LFO with each note                                          |
| 0x02   | f32  | rate             | Hertz, 0.1..80, used if sync is off                                          |
| 0x06   | u8   | note length      | used if sync is on: 0..8 = 1/16, 1/8, 1/4, 1/2, 1, 2, 4, 8, 16               |
| 0x07   | u8   | motion           | 0 = loop, 1 = ping-pong, 2 = reverse                                         |
| 0x08   | u8   | bypass           | 0/1, a bypassed engine modulates nothing                                     |
| 0x09   | u8   | infinity active  | 0/1                                                                          |
| 0x0A   | u8   | infinity counter |                                                                              |
| 0x0B   | u8   | steps            | number of sequencer steps, 1..32                                             |
| 0x0C   | u8   | sync             | 0/1, tempo sync                                                              |

The default engine (written by the plug-in for every unused engine) is a sine with retrigger and
sync on, rate 0.1 Hz, note length 4, loop, 1 infinity counter and 32 steps.

The note length is given in whole notes: one LFO cycle lasts *length* × 4 beats of the host
tempo, e.g. 1/4 at 120 BPM = 2 Hz. The tempo is not stored in the preset.

All shapes produce a value *x* in -1..1 over one cycle (phase *p* = 0..1): sine = sin(2π*p*),
triangle rising from -1, square = -1 for the first half and +1 for the second, saw rising from -1 to
+1, inverted saw falling from +1 to -1. The sequencer outputs step ⌊*p* × steps⌋, scaled from 0..1
to -1..1. Reverse plays the shape backwards, ping-pong plays it forwards and then backwards, so one
ping-pong cycle lasts two LFO cycles.

#### Assignment `<P>`

`name` is the name of the modulated parameter, `state` a 9 byte blob:

| Offset | Type | Field    | Notes                                        |
|--------|------|----------|----------------------------------------------|
| 0x00   | f32  | amount   | -1..1, a negative amount inverts the LFO     |
| 0x04   | f32  | position | -1..1, center of the modulation              |
| 0x08   | u8   | bypass   | 0/1, a bypassed assignment modulates nothing |

A modulated parameter does not keep its own value. With *min*/*max* being the range of the
parameter it becomes

```
value = min + (max - min) × (1 + position + clamp(amount × x, -1, 1)) / 2
```

limited to *min*..*max*. The position therefore replaces the static value of the parameter (0 =
the middle of the range) and the amount sets the swing (1 = the whole range).

The parameter names are formed from a parameter and a suffix for the layer: `" 1"` .. `" 4"` or
`" Master"`. Effect parameters additionally get the effect slot as `" (1)"` .. `" (4)"`, e.g.
`FILTER FREQUENCY 1 (2)` is the cutoff of the filter in the second effect slot of layer 1.

| Parameter                | Range       | Notes                            |
|--------------------------|-------------|----------------------------------|
| `VOLUME MASTER`          | 0..1        | master volume (no layer suffix)  |
| `PAN MASTER`             | -1..1       | master panning (no layer suffix) |
| `VOLUME LAYER n`         | 0..1        | layer volume                     |
| `PAN LAYER n`            | -1..1       | layer panning                    |
| `OCTAVE LAYER n`         | -2400..2400 | cents                            |
| `TRANSPOSE LAYER n`      | -1200..1200 | cents                            |
| `FINE LAYER n`           | -100..100   | cents                            |
| `FILTER FREQUENCY n (s)` | 50..15001   | Hertz, linear                    |
| `FILTER RESONANCE n (s)` | 1..15       |                                  |

Most other effect parameters can be modulated as well, they are named `<EFFECT> <PARAMETER>`
with the effect and parameter names used in the `<S>` elements of the effects, e.g.
`DELAY FEEDBACK 2 (1)` or `PHASER RATE Master (3)`.

## Mapping notes (ConvertWithMoss)

- One preset = one `IMultisampleSource`. Every active layer with a non-empty group becomes one
  `IGroup`; layer volume (relative to the 0.75 default), fine tune/transpose/octave and ADSR
  are folded into the zones of that group.
- Panning: only the pan of the sound (0x3B) is applied. Verified with plug-in 1.2.1: a preset
  whose two layers had the layer pan -1 and +1 and sounds with pan 0 played both layers in the
  center; panning the sounds in the sample editor (L20 = -0.4, R8 = 0.16, hard left/right = -1/+1)
  changed only the float at 0x3B. The layer pan is therefore ignored when reading and written as
  0, the pan of each zone is written into its sound.
- Key high and velocity ranges: key high is stored exclusive, velocity is inclusive.
- LFOs: the assignments to `VOLUME LAYER n`, to `FINE`/`TRANSPOSE`/`OCTAVE LAYER n` and to the
  `FILTER FREQUENCY` of the filter slot which is read as the zone filter become the amplitude,
  pitch and cutoff LFOs of the layer's zones; if several engines modulate the same destination,
  the strongest one is used. The position of such an assignment replaces the layer volume, the
  tuning or the cutoff. The volume and cutoff depths are half of the swing between the highest
  and the lowest value in dB respectively cents. The sequencer shape is read as a random wave,
  synced rates are calculated for 120 BPM. Writing creates free running engines (rate in Hertz),
  layers with identical oscillators share an engine. The pitch is written to `FINE LAYER n` if
  the modulation fits into ±100 cents, otherwise to `TRANSPOSE LAYER n`; the cutoff is written to
  the first effect slot which holds the filter.
- Loop/sample positions are fractions of the sample frame count, so a sound cannot be read
  without the length of its sample. The frame count has to come from the audio metadata:
  the packs of the original corpus hold WAV, but packs written by newer plug-in versions
  hold FLAC (the *Wired* pack does), and `ISampleData.addZoneData` fills the stop of a zone
  in only for WAV.
- Writing always produces the Cosmo-era layout: `gv="1" pv="1.0.0b19"`, 75 byte sound blobs,
  numeric `f` attributes, WAV samples. One group per preset (all zones with their velocity
  ranges), assigned to layer 1.
