# Groove Synthesis 3rd Wave sample slot formats

The oscillators of the 3rd Wave play wavetables, analog waveforms and *sample slots*
(S00, S01, ...). A slot holds up to eight mono samples; each sample has its own key range around an
*assigned note*, fine tuning, volume, play range and loop. On its USB drive the device stores:

* a slot as a **multi-sample file** (`.bin`, folder *Audio*), which *Export Sample Library*
  writes for every slot and *Import multisample* / *Bulk multisample import* read, and
* a program together with the slots and wavetables it uses as a **unified program file**
  (`.pgdata`, folder *Programs*), see *Import/Export unified program data file*.

ConvertWithMoss reads the slots of both file types and writes both file types; a written program is
the init program of the instrument whose parts play the slots. The settings of a program are not
read. Classes: `format/groovesynthesis/ThirdWaveFile` (layouts), `ThirdWaveProgram` (program
parameters), `ThirdWaveDetector`, `ThirdWaveCreator` and `ThirdWaveCreatorUI`.

Public references: the *3rd Wave Keyboard User Manual v1.9* (sampling chapter), the *3rd Wave
MIDI CC + SysEx Spec v2.0* (section *Using MIDI SysEx to manage multisample (S) slots*, which
lists the same sample parameters and their ranges) and the example files on the support page of
Groove Synthesis.

## Conventions

* All numbers are little-endian and packed without padding; floats are IEEE-754 binary32.
* A name has 32 bytes: ASCII, terminated and padded with zeros. ConvertWithMoss writes at most 31
  characters so that the terminating zero is always present.
* A file starts with a 16-byte signature: the text, padded with zeros.
* Sample positions are zero-based and **inclusive**: a recording of `N` frames plays from `0` to
  `N - 1`. This matches the play range and loop end of the ConvertWithMoss model.
* The audio is always **signed 16-bit mono**, two bytes per frame. The bit depth of a sample
  (8/12/16) is a playback setting which "reduces fidelity but uses the same amount of memory"; it
  does not change the stored audio.

## Multi-sample file (`.bin`)

| Offset | Size | Content |
| --- | --- | --- |
| 0 | 16 | `W3_MSAMPLE_1`, `W3_MSAMPLE_4` or `W3_MSAMPLE_5` |
| 16 | 1 | Number of samples, 0-8 |
| 17 | 32 | Name of the slot |
| 49 | ... | The samples, each directly followed by its audio (`2 * frames` bytes) |

The file ends with the audio of the last sample; there is no checksum. Samples which use the same
recording each carry a copy of it.

### Sample record

| Offset v1 | Offset v4/v5 | Size | Content |
| --- | --- | --- | --- |
| 0 | 0 | 4 | Number of frames of the recording (uint32) |
| - | 4 | 4 | Sample rate of the recording (float, Hz) |
| - | 8 | 1 | Bit depth: 0 = 8 bit, 1 = 12 bit, 2 = 16 bit |
| 4 | 9 | 32 | Name of the recording |
| 36 | 41 | 4 | Play start |
| 40 | 45 | 4 | Play end (inclusive) |
| 44 | 49 | 4 | Loop start |
| 48 | 53 | 4 | Loop end (inclusive) |
| 52 | 57 | 1 | Assigned note, which plays the recording in its original pitch |
| 53 | 58 | 1 | Lowest note |
| 54 | 59 | 1 | Highest note |
| 55 | 60 | 1 | Loop cross-fade: 0 = off, 1 = equal volume (linear), 2 = equal power |
| 56 | 61 | 4 | Loop cross-fade length in frames, at most the length of the loop |
| 60 | 65 | 1 | Loop: 0 = off, 1 = on while the note is held, 2 = always on |
| - | 66 | 4 | Fine tuning (float, semi-tones, -1 to 1 = -100 to +100 cents) |
| - | 70 | 4 | Second sample rate (float, Hz), see below |
| - | 74 | 4 | **v5 only:** volume (float, linear factor, 0 to 2) |
| 61 | 74 / 78 | `2 * frames` | Audio |

* **Version 1** has no sample rate (48 kHz), no bit depth (16 bit), no tuning and no volume.
* **Version 4** adds sample rate, bit depth, fine tuning and the second sample rate.
* The samples in unified program files add the volume; they are read as **version 5**, which is
  assumed to be the multi-sample file of the same OS versions (the unified program file embeds a
  slot exactly as the body of a multi-sample file: count, name, samples). No version 5 multi-sample
  file was available for testing; since the reader checks the ranges of all fields and that the
  samples end exactly at the end of the file, a different layout is rejected instead of being
  misread. Other versions are rejected.
* *Looping off* does not make a one-shot: the amplitude envelope of the program still stops the
  note. *On while the note is held* ends the loop at the release of the note and plays the rest
  of the sample (a sustain loop, `ISampleLoop#isLoopUntilRelease`), *always on* keeps looping.
* In 9 of the 378 stock samples the cross-fade is one frame longer than `loopEnd - loopStart`,
  i.e. it covers the whole loop including its last frame.
* The **second sample rate** equals the sample rate in 365 of 378 stock samples. In the others it
  differs, always for the same recordings: `as vocal 3` and `as texture 1` have 48000 / 22050 Hz in
  five programs and 22050 / 22050 Hz in another one with the identical audio, three drums of
  `8 Bit Drum Tour` have 44100 / 48000 Hz. Which one the device uses for the pitch is not known;
  ConvertWithMoss uses the first and reports the difference. Both are written with the same rate.

## Unified program file (`.pgdata`)

| Offset | Size | Content |
| --- | --- | --- |
| 0 | 16 | `W3_UNIPROG_1` |
| 16 | 1 | Number of resources `R`, at most 12 (4 parts with 3 oscillators) |
| 17 | `4 * R` | Resource directory, per entry: ID (uint8), type (uint8), number (uint16) |
| `17 + 4 * R` | ... | The `R` resources |
| ... | ... | The program |

Resource types: 0 = P wavetable, 1 = U wavetable, 2 = analog waveform, 4 = sample slot - the
numbering of the wavetable types in the MIDI specification, where 3 is the WaveMaker wavetable. The
number of a sample slot is `4000 + slot index`. The directory lists the resources which the
oscillators of the program reference, whether they are audible or not.

Each resource starts with type (uint8), ID (uint8) - the reverse order of the directory - and a
flag (uint8) whether its data is included. Without data the resource is just these 3 bytes. With
data:

| Type | Data |
| --- | --- |
| 0, P wavetable | 32-byte name + 65,536 bytes |
| 1, U wavetable | 32-byte name + 524,288 bytes |
| 4, sample slot | Number of samples (1), name of the slot (32), samples as version 5 |

The program follows: its name (32 bytes) and four counts (uint32) `F`, `I`, `S`, `E` (observed:
181, 136, 179 or 181, number of sequencer events), then `4 * (4 * (F + I) + S) + 24 * E` bytes
of parameters and sequencer events, which end the file. The reader checks this size but does not
interpret the parameters.

## Program parameters

A program stores, in this order:

| Count | Type | Content |
| --- | --- | --- |
| 4 * 181 (`F`) | int32 | The integer parameters of part 1, then of part 2, 3 and 4 |
| 4 * 136 (`I`) | float32 | The float parameters of part 1, then of part 2, 3 and 4 |
| 181 (`S`) | int32 | The parameters of the whole program |
| 6 * `E` | int32 | The sequencer events |

The stand-alone program file of the instrument (`.pro`, header `W3_PROG_5`) is a text file with
one value per line: header, name, `F`, `I`, `S`, `E` and the same values in the same order. The
parameters of a unified program file are the ones of its `.pro` except for the 12 oscillator
references: a `.pro` holds the *global* number of a wavetable, analog waveform or sample slot
(the numbers of the resource directory above), a unified program file its *ID* in the resource
directory. Compared with the 1,000 programs of the factory banks (24-voice and 8M), the
parameters follow the groups and ranges of the NRPN list of the MIDI specification: the integers
hold the discrete settings and the 0-127 controls, the floats the continuous ones (levels 0 to 1,
tuning in semi-tones, amounts -1 to 1). The positions which ConvertWithMoss uses, confirmed by
programs whose purpose is evident from their names (split, stereo and single-part programs):

| Position | Content |
| --- | --- |
| Part integer 0-2 | Wave of oscillator 1-3 (reference, see above) |
| Part integer 22, 26, 30 | Amplifier envelope attack, decay, release (0-127) |
| Part float 6-8 | Level of oscillator 1-3 (0 to 1) |
| Part float 99 | Amplifier envelope sustain (0 to 1) |
| Part float 112 | Part volume (0 to 1.7) |
| Part float 124 | Part pan (0 = left, 0.5 = center, 1 = right) |
| Program integer 18 | Number of split points of the keyboard (0-3) |
| Program integer 21-23 | Notes of the split points 1-3 |
| Program integer 24-27 | Parts which the keyboard sections 1-4 play (bit mask, part 1 = 1) |

Stereo sample programs play the left and the right slot with parts 1 and 2 panned hard left and
right, both in keyboard section 1 (mask 3). Split programs set the number of split points and
the split notes, section 1 plays part 1, section 2 part 2 and so on. The manual describes a split
note as "the end of Split point 1 and the beginning of Split point 2"; to which section the note
itself belongs is not established.

The global numbers of the analog waveforms are 41-47 for the analog waveforms 0-6 of the MIDI
specification: sawtooth, square, supersaw, sine, noise 1, noise 2 and triangle.

### The init program

The factory banks of the 8M (bank 5, programs 52-100) contain the init program of the instrument
('init prog' on the home screen, "one PPG legacy wavetable ... and one modern User wavetable, and
filter and amplifier envelopes set to useful values"): all four parts are the same, oscillator 1
plays P02 and oscillator 2 U35 at level 0.5, oscillator 3 the analog waveform 42 at level 0; the
amplifier envelope has attack 0, decay 80, sustain 1 and release 82; one keyboard section plays
part 1. Its values are the defaults which the stock sample programs keep for everything they do
not use. Seven parameters differ between the 24-voice and the 8M versions of the same factory
programs (program integers 0, 75, 142 and 177, part floats 102, 115 and 116); unified program
files with either variant are part of the libraries for both.

## Limits

From the manual unless noted:

* At most 8 samples per slot.
* 3.3 MB of sample memory for all slots: 1,683,456 frames (35 seconds at 48 kHz).
* Sample rates from 10 kHz to 48 kHz.
* The key range of a sample reaches at most two octaves below and above its assigned note. One
  stock sample exceeds it (`RandroidG6`: note 91, range 91-120).
* Samples are ordered by their assigned note (the MIDI specification requires this order when
  sending the samples of a slot). All stock samples have different assigned notes which lie in
  their own key range; the key ranges of 7 stock slots overlap at their edges.

## ConvertWithMoss mapping

**Reading.** Each slot with samples becomes a multi-sample with one group. The slots of a unified
program file are named `<program> - <slot>`; a program with only one slot is named after the
program. Each sample becomes a zone: key range, assigned note as root, fine tuning, volume as gain
(dB), play range and loop with its cross-fade. A bit depth below 16 bit is reported, the 16-bit
audio is kept.

**Writing.** Either a unified program file (the default) or version 4 multi-sample files, since
that version is available as a reference from the manufacturer. The samples of all groups are
first distributed to *layers*: samples are layered if they have the same assigned note or the
assigned note of one lies in the key range of the other, e.g. velocity layers and round robins.
Key ranges which only overlap at their edges are kept.

* *Unified program file:* the first layer is split in the order of the assigned notes into slots
  of at most 8 samples, up to 4 slots. The program is the init program (a resource in the `.pro`
  format) in which part N plays slot N with oscillator 1 at level 1 and oscillators 2 and 3 at
  level 0; these and the oscillators of the parts which are not used play the analog waveform 42,
  which the file lists without data. With more than one slot the keyboard is split: the split
  note lies on a note which neither neighbouring slot plays, or the lowest sample of the upper
  slot is extended by the split note, so that either reading of the split note plays a sample.
  All samples of the program need to fit into the sample memory, otherwise the program is not
  written. The samples store the gain as volume, up to +6 dB.
* *Multi-sample files:* every layer is split in the order of the assigned notes into slots of at
  most 8 samples which fit into the sample memory, each written as a file.

A multi-sample which needs more than one slot gets numbered slot names (`Name 1`, `Name 2`, ...)
and, for multi-sample files, numbered files; the slot name is shortened so that the number is
kept. Further:

* Audio: 16 bit mono, stereo is mixed down. Sample rates outside 10-48 kHz are converted; a looped
  sample is converted together with its loop so that it stays seamless
  (`AbstractCreator#recalculateSamplePositions(IMultisampleSource, int, int)`).
* Coarse tuning moves the assigned note, the remaining fine tuning (at most +/-0.5 semi-tone) is
  written as the tuning. A sample which does not follow the keyboard (key tracking off) gets the
  key in the middle of its range as its assigned note.
* The key range is limited to two octaves around the assigned note.
* The multi-sample files have no volume: the gain is applied to the audio, as is the part of it
  above +6 dB in a program file, but above 0 dB only up to the peak level of the sample.
* Only the first loop is written, forwards and within the play range. A cross-fade is written as
  *equal volume*. Reversed samples are written reversed.
* Samples which play on the release of a note are skipped.
* If all slots of a multi-sample need more than the sample memory, this is reported.

## Validation

* **Unified program files:** the 45 programs of the manufacturer's sample library
  (*3rdWaveSampleLibraryPrograms1*) and the 19 unified programs of bank 5 of the factory banks
  (v2b; the 8M factory banks v1 contain the same files) parse exactly to their end: 88 slots with
  378 samples, resources of the types 0, 1, 2 and 4, parameter counts 181/136/179 and 181/136/181.
* **Multi-sample files:** the six files of the manufacturer's OS 1.8 sample presets (five version
  1, one version 4) parse exactly to their end. Written back by ConvertWithMoss, the version 4 file
  `S002_C15.bin` is byte-identical; the version 1 files give identical samples and audio.
* **Round trip:** all 65 slots of the sample library written as multi-sample files: all 270
  samples keep audio (with the applied gain), play range, loop, cross-fade length, key range and
  tuning, except for 31 equal-power cross-fades (written as equal volume), 5 bit depths of 12 bit
  (written as 16 bit) and the key range of `RandroidG6` (limited to 91-115). Written as unified
  program files, all 270 samples keep their audio unchanged and their volume as well.
* **Programs:** the written programs differ from the init program only in the oscillator
  references and levels and the keyboard split, checked by reading them back.
* **Other sources:** e.g. the 66 SoundFonts of a stereo, multi-layered library, and synthetic SFZ
  files for 96 kHz and 8 kHz loops (seamless after the conversion), key tracking off, release
  samples, velocity layers, coarse tuning, reversed samples and gain.

**Not verified on hardware**: loading and playing the written files on a 3rd Wave.

## Open questions

* The meaning of the second sample rate, see above.
* The version and layout of the multi-sample files which current OS versions export (version 5
  is assumed to match the samples of the unified program files).
* The remaining parameters of the program and the time law of the envelopes (0-127 to seconds),
  which are needed to convert envelopes and filters, and to read programs as a whole, e.g. stereo
  pairs of slots.
* Whether a split note belongs to the lower or the upper keyboard section.
* How the device quantizes 8- and 12-bit playback.
* The size of an included WaveMaker wavetable (type 3); no file has one, a program which includes
  one is rejected since the resources behind it cannot be found.
