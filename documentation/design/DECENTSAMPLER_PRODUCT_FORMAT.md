# DecentSampler Product Presets

The DecentSampler detector reads product presets (`.dsproduct`, version 001) in addition to plain presets (`.dspreset`). A product preset contains the same XML document as a plain preset, therefore both go through the same conversion and can be written by every destination format. Presets may be single files, files inside `.dsbundle` folders, or entries of `.dslibrary` ZIP archives. File extensions are matched case-insensitively.

## Container

| Field | Content |
| --- | --- |
| Header | 16 ASCII bytes: `DECENTSAMPLER001` |
| Payload | Blowfish in ECB mode, independent 8-byte blocks |
| Block layout | Two 32-bit words, each stored little-endian |
| Key | A constant of the format (see `DecentSamplerProduct`), 72 bytes |
| Padding | 1 to 8 bytes, each containing the number of padding bytes; a full block of padding if the document fills the last block |
| Decoded payload | UTF-8 XML document with a `DecentSampler` root element, identical to a `.dspreset` |

The container holds only the description of the instrument. The samples are separate files, whose paths are relative to the location of the preset, also inside a library where they are relative to the folder of the ZIP entry. Windows path separators are accepted. The preset is decoded in memory; no intermediate file is written and the source files are not modified.

The reader checks the header, that the payload consists of complete blocks, every padding byte, the UTF-8 encoding and the XML structure; a payload is limited to 64 MiB. The format has no checksum, therefore these checks cannot detect every damage. The XML parser does not access external DTDs or schemas. A damaged entry of a library is reported and skipped, the other presets of the library are still read. A preset without any sample which can be played is skipped with a message.

## Stored control values

DecentSampler sends the value of each control of the user interface through its bindings when it loads a preset (the default of the binding attribute `triggerOnLoad`). Therefore, the stored control values and not only the attributes of the groups, samples, tags, effects and modulators decide how a preset sounds. They are applied before the preset is converted:

* `control` and `labeled-knob`: the attribute `value` (0 if it is not set) limited to the range from `minValue` to `maxValue`.
* `menu`: `value` selects an option, numbered from 1; 0 or no value selects none. The bindings of the selected option are applied.
* `button`: `value` selects a state, numbered from 0. The bindings of the selected state are applied.
* A binding with `enabled="false"` or `triggerOnLoad="false"` is not applied. The value is multiplied by `factor` and then translated: `linear` maps the range of the control onto `translationOutputMin` to `translationOutputMax` (optionally reversed) and passes the value on unchanged if no output range is set, `table` interpolates between the pairs of `translationTable`, `fixed_value` sends `translationValue`.

| Binding type | Level | Parameters | Target |
| --- | --- | --- | --- |
| `amp`, `general` | `instrument`, `group` | `ENV_ATTACK`, `ENV_DECAY`, `ENV_SUSTAIN`, `ENV_RELEASE`, the three envelope curves, `GLOBAL_TUNING`, `GROUP_TUNING`, `PITCH_KEY_TRACK`, `AMP_VEL_TRACK`, `ENABLED`, `AMP_ENV_ENABLED`, `SILENCING_MODE`, `SILENCING_DECAY`, `OUTPUT_n_VOLUME`, `OUTPUT_n_TARGET` | The `groups` element (instrument) or the groups selected by `groupIndex`, `position`, `groupTags` or `tags` |
| `amp`, `general` | `instrument`, `group` | `AMP_VOLUME`, `PAN` | The volume and the pan offset of the instrument or of the selected groups (`globalVolume`, `groupVolume`, `globalPan`, `groupPan`) |
| `amp`, `general` | `tag` | `TAG_VOLUME`, `TAG_ENABLED`, `TAG_POLYPHONY` (also `AMP_VOLUME` and `ENABLED`) | The tag named by `identifier`; a tag which is not defined yet is added |
| `effect` | `instrument`, `group` | `ENABLED`, `FX_FILTER_FREQUENCY`, `FX_FILTER_RESONANCE`, `FX_MIX`, `FX_WET_LEVEL`, `FX_REVERB_WET_LEVEL`, `FX_IR_FILE`, `FX_PITCH_SHIFT`, `LEVEL` (a linear factor) | The effect selected by `effectIndex`, `position`, `effectTags` or `tags` of the instrument or of the selected groups |
| `modulator` | any | `MOD_AMOUNT`, `FREQUENCY` | The modulator selected by `position`, `modulatorTags` or `tags` |

Bindings to other controls, notes and key colors do not change the sound and are ignored, like bindings to the arpeggiator and note sequences, which are reported themselves if they are used. The other parameters of effects are not converted anyway, except that a pitch shift of 0 semitones is not reported as an effect. A binding whose target does not exist changes nothing, like in DecentSampler. A binding to any other parameter or level and a control or translation with an invalid value are reported with the name of the control, and the attributes of the preset are kept.

## Musical parameters

| Source | Conversion |
| --- | --- |
| Sample path, root note, key and velocity range | Sample zone which references the original audio file |
| Settings of the `groups` element and of a `group` | Defaults of their samples; the most specific setting wins |
| Sample start and end, loop start and end | Frame positions, the ends are inclusive and limited to the last frame; a loop which does not fit into its sample is dropped and reported |
| Missing loop points | Taken from the loop of the sample file, else the start or the end of the sample, if the loop is enabled |
| `loopEnabled="false"` | Also suppresses the loop of the sample file |
| Loop cross-fade | Length kept; the curve is the one of the destination |
| Volume | The volumes of the `groups` element, the group and the sample are separate levels, combined with the volumes of the tags of the sample, the gain effects of the instrument and the group and the volume of the main output (in dB) |
| Pan | The pan of the sample (else of its group, else of all groups) plus the pan offsets of the instrument and the group (`globalPan`, `groupPan`), limited to the valid range |
| Tuning | Sample tuning (inherited like the pan) plus the offsets of the instrument and the group (`globalTuning`, `groupTuning`), in semitones |
| Amplitude envelope | Attack, decay, sustain, release and their curves of the sample, else of its group, else of all groups. If none of them is set, the envelope stays unset and the default envelope of the category is applied. A curve which is not set gets the default of DecentSampler: -100 (logarithmic) for the attack, 100 (exponential) for the decay and the release |
| `ampEnvEnabled="false"` | One-shot without an envelope |
| Pitch and velocity tracking, trigger, round robin mode and position | Settings of the sample zone |
| Filters and modulators | The first low-pass, high-pass, band-pass, peak or notch filter of an effect chain, an envelope bound to the filter frequency or the group tuning, LFOs bound to the group tuning or volume |
| Disabled groups and tags (`enabled="false"`) | The groups and samples are skipped; they are kept with 'Create one multi-sample per group' |
| `silencedByTags` | An exclusive group, if the tag silences mutually: it is the single silencing tag of every sample which carries it, and every sample which it silences carries it. Other silencing is reported |
| Polyphony of the tags | The lowest polyphony of the tags of the groups |

The [DecentSampler Developer Guide](https://decentsampler-developers-guide.readthedocs.io/en/latest/) documents the XML elements and attributes, their inheritance and the bindings of the user interface.

## Compatibility limits

The user interface itself and MIDI controller bindings are not converted, the converted preset has the sound of the stored control values. The following features change the sound but have no equivalent in the conversion model. A message lists those which a preset uses:

* Effects other than the filter and the gain effect, e.g. convolution, reverb (only if its wet level is above 0) or delay
* More than one filter in an effect chain (the first filter is converted)
* Effect buses
* Outputs other than the main output (the main output is kept, a sample which is not sent to it stays audible)
* The arpeggiator and note sequences
* Silencing by tags which is not mutual, e.g. a closed hi-hat which stops the open one but not vice versa
* Fading out silenced samples (`silencingMode="normal"`, `silencingDecay`)
* The curve of loop cross-fades

A destination format can limit further what is kept, e.g. envelope curves, filters, round robins, one-shots and exclusive groups.

## Validation

* The five product presets of the Speak & Music library (175 sample zones: four looping instruments with two layers of 20 zones each and a one-shot instrument with 15 zones) decode to the same XML documents as with the Blowfish implementation of OpenSSL. All their samples are found, and the zones, loops, stored envelope values (attack 0 s, decay 5 s, sustain 75 %, release 2 s), the gain (-9 dB), the one-shots and the 15 exclusive groups match the presets. They convert to SFZ, SoundFont 2 and DecentSampler without errors, and the SoundFont keeps the exclusive groups.
* Synthetic presets and product files were checked for all padding lengths and non-ASCII text, unknown versions, truncated and damaged files, invalid XML, external DTDs, libraries with nested and damaged entries, missing samples, independent presets in one library, inherited settings, stored control values and their translations, tag volumes, disabled tags, gain, pan, tuning, main output levels, envelopes, loops, one-shots, exclusive groups and the reported features, and for their conversion to SFZ and SoundFont 2.
* The seven plain presets of Harry Zimm's 'Rare Akai AX73' (419 zones) read as before, except for the corrections described above: the volume of the groups element (-3 dB), the stored values of the attack and release knobs (0.01 s, and 1 s or 0.1 s, instead of the 0 s and 0.43 s of the attributes), the filter frequency of the stored 'Tone' knob and the default curves of DecentSampler.
* The converted presets were not compared with DecentSampler by rendering them.
