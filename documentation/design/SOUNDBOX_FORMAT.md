# Audiomodern Soundbox format (.sbpack / .sbset / .sbgrp / .amx)

Reverse engineered from the factory packs *Cosmo*, *Kromium*, *Voxmotive*, *Starlit*, *Mechania*
and the imported library of the Soundbox plug-in (macOS, versions 1.0.0b11 … 1.2.1). Soundbox is a
JUCE based sample player with 4 layers; each layer plays one *group* which contains the mapped
samples (called *sounds*).

## Containers

### .sbpack — a sound pack

A plain ZIP archive (entries are STORED / uncompressed):

| Entry                | Content                                                          |
|----------------------|------------------------------------------------------------------|
| `pack.amx`           | XML: pack metadata, preset names, all groups, sample name list   |
| `_presets/p<N>.sbset`| XML: preset N (index into the `<Presets>` list of `pack.amx`)    |
| `_samples/s<N>`      | Sample file N *without file extension* (WAV, newer packs FLAC)   |
| `*.png`              | Optional icon/cover images referenced from `pack.amx`            |

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

| Offset | Type | Field                | Notes                                                   |
|--------|------|----------------------|---------------------------------------------------------|
| 0x00   | u32  | key low (copy)       | always identical to 0x26 (4093 of 4093 samples)         |
| 0x04   | u32  | root note            | MIDI note                                               |
| 0x08   | u8   | loop active          | 0/1                                                     |
| 0x09   | u8   | ping-pong loop       | 0/1 (loop type alternating)                             |
| 0x0A   | f32  | sample start         | fraction 0..1 of the sample length                      |
| 0x0E   | f32  | sample end           | fraction 0..1                                           |
| 0x12   | f32  | loop start           | fraction 0..1                                           |
| 0x16   | f32  | loop end             | fraction 0..1                                           |
| 0x1A   | f32  | fade in              | fraction 0..1                                           |
| 0x1E   | f32  | fade out             | fraction 0..1                                           |
| 0x22   | f32  | loop crossfade       | 0..0.5, assumed relative to the loop length             |
| 0x26   | u32  | key low              | MIDI note, inclusive                                    |
| 0x2A   | u32  | key high, exclusive  | first key *not* played (drum maps use low+1; max 127)   |
| 0x2E   | u8   | reverse              | 0/1                                                     |
| 0x2F   | f32  | pan                  | assumed; always 0 in the corpus                         |
| 0x33   | f32  | (speed?)             | always 1.0 (a single sample: 0.3136)                    |
| 0x37   | u32  | volume percent       | `setSoundVolume(int)`; 100 = full, corpus also has 80   |
| 0x3B   | u32  | ?                    | always 0                                                |
| 0x3F   | i32  | tune semi-tones      | `setSoundTune(int)`; corpus: 0 and -12                  |
| 0x43   | u32  | velocity low         | inclusive, 0..127 (paired splits like 20/21 in corpus)  |
| 0x47   | u32  | velocity high        | inclusive                                               |

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
  <Engines>
    <E state="13...." sequence="128...."/>  <!-- 4 elements, playback engine per layer -->
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
| 0x03   | f32  | pan    | -1..1                          |
| 0x07   | f32  | volume | 0..1, default 0.75             |

### Layer `settings` blob — 33 bytes (`AMSamplerEngineMPE` parameters)

| Offset | Type | Field      | Notes                                              |
|--------|------|------------|----------------------------------------------------|
| 0x00   | u8   | voice mode | 0 = mono, 1 = legato, 2 = poly (default)           |
| 0x01   | f32  | glide      | knob 0..1; time law not calibrated                 |
| 0x05   | f32  | transpose  | semi-tones, always 0 in corpus                     |
| 0x09   | f32  | fine tune  | cents (corpus: ±5, ±10)                            |
| 0x0D   | u32  | octave     | index with center 2 = ±0 octaves (assumed)         |
| 0x11   | f32  | attack     | knob 0..1; time = knob x 4.0 s (measured, linear)  |
| 0x15   | f32  | decay      | knob 0..1; assumed 20 s law like release           |
| 0x19   | f32  | sustain    | 0..1 (UI shows 0-100%)                             |
| 0x1D   | f32  | release    | knob 0..1; time = knob x 20 s (measured)           |

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
3.145) and `TYPE` is the filter type (0 = low-pass; only 0 has been observed, the other indices
are assumed to follow the usual high-pass/band-pass/band-rejection order). ConvertWithMoss reads
the first active filter slot of a layer - or of the master, if the layer has none - as the filter
of the layer's zones and writes a common zone filter back into the first slot. All other effects
are not converted.

The engine `state` (13 bytes) and `sequence` (128 bytes) blobs belong to the 4 LFO engines of
the Modulation tab (the 128 byte sequence holds the 32 steps of the LFO sequencer shape); they
are identical for all four engines in every corpus preset and ConvertWithMoss writes the
constant default as found in Cosmo (see `SoundboxCreator`).

## Mapping notes (ConvertWithMoss)

- One preset = one `IMultisampleSource`. Every active layer with a non-empty group becomes one
  `IGroup`; layer volume (relative to the 0.75 default), pan, fine tune/transpose/octave and ADSR
  are folded into the zones of that group.
- Key high and velocity ranges: key high is stored exclusive, velocity is inclusive.
- Loop/sample positions are fractions of the sample frame count.
- Writing always produces the Cosmo-era layout: `gv="1" pv="1.0.0b19"`, 75 byte sound blobs,
  numeric `f` attributes, WAV samples. One group per preset (all zones with their velocity
  ranges), assigned to layer 1.
