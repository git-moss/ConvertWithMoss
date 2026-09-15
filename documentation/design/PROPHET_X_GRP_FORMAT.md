# Sequential Prophet X / XL sample instrument (`.grp`) format — reverse-engineered

Reverse-engineered 2026-09 from the **Prophet X OS 2.2.2.0.0 update image** (`PxUpdate_V2.2.2.0.0.bin`,
3 099 064 bytes, December 2021). The image is a 13-byte vendor header followed by an unstripped
x86-64 QNX 7 ELF executable, so all class, method and global names are available: the reader of the
instrument file is `GroupFileParse` with its tokenizer `CGroupVelocityManifestFileReader`, the column
names are the table `gGroupFileParmNames`, the WAV reader is `CWavReader::Open`/`Read`, the zone
lookup `CSampleGroup::FindHandler`, the USB importer `LibraryCopy_DoLibraryUpdate` and the pitch law
sits in `CSampleOsc::PlayNote`. Sequential does not document the format; the official "Prophet X
Series Mapping Utility" (8Dio) is the only public writer and states 16 bit / 48 kHz WAV, at most 128
files and 1.5 GB per instrument.

No factory content was available, so the format below comes from the code paths which read and
import the files; statements about what the device *does* with a value are taken from the code.
Two other writers served as the reference for what actually plays: the free **PXToolkit** 1.3.2
(ThinkerSnacks, an Electron app whose writer `ProphetWriter.js` is plain JavaScript) and the free
third-party pack *Goldbaby GBPX Free1* (six instruments in the USB layout, made with PXToolkit),
which ConvertWithMoss reads. The written output of ConvertWithMoss follows their conventions but
has **not** been verified on hardware yet.

## What kind of instrument this is

The Prophet X is a hybrid synthesizer whose two sample oscillators play **multi-sampled
instruments**: key and velocity zones with optional round robins, one WAV file per zone. Envelopes,
filters, LFOs, pitch bend, loop cross-fade and reverse/alternating loop playback are parameters of
the *program* which plays the instrument, not of the instrument file, so a `.grp` carries only the
mapping.

**ConvertWithMoss mapping.** One instrument = one multi-sample source. Each line of the group file
is one zone; the lines which share a round robin number form one group ("Round Robin N") which the
model plays in turn, the lines without a number form the first group. The category comes from the
category folder of the instrument, the name from the `Instrument Name` column (the device itself
lists the name of the group file). Written instruments carry every zone with its key and velocity
range, loop, frequency (root key plus fine tuning) and round robin number, and the velocity response
of the zones as `Volume.txt` when it is not the default of the model.

## Storage layout on the instrument

Instruments live on the internal drive:

    /hd/<group>/<NN Category>/<Instrument>/<Instrument>.grp
                                          /<sample>.wav ...
                                          /Volume.txt            (optional)

* `<group>` is one of 65 fixed folder names (`gpGroupNames`): `f00` (factory content),
  `u00`..`u31` (user banks) and `p00`..`p31` (purchased expansion packs).
* `<NN Category>` is one of 17 fixed folders (`gpFullCategoryNames`): `01 Ambience`, `02 Bass`,
  `03 Brass`, `04 Choir`, `05 Cinematic`, `06 Drums`, `07 Effects`, `08 Ethnic`, `09 Guitar`,
  `10 Keyboard`, `11 Percussion`, `12 Tonal Perc`, `13 Piano`, `14 Strings`, `15 Synth`, `16 Vox`,
  `17 Winds`. The indexer (`CSampleGroups_IndexerCallback`) skips the two digits and the space and
  compares the rest with `gpCategoryNames`; any other folder is ignored.
* The instrument folder is found by walking a category folder (`DSI_FTW`, depth 4) for files whose
  name ends with `.grp`. The file name without the ending is the name shown on the instrument;
  names which start with a dot are skipped. Instruments are sorted with a numeric-prefix-aware
  string compare (`firstnum_strcmp`).
* `/hd/<group>/GroupInfo.txt` only caches the size of the group (`Group Size: <bytes>`); it is not
  part of an instrument.

The instrument dictionary (`CInstrumentDictionary::Find`) identifies an instrument either by its
group, category and index or by its 16-byte UUID with a fallback to its name (the programs
themselves store category and index, see the end of this document). ConvertWithMoss writes a UUID
which is derived (version 3, name-based) from the user bank, the category and the name, so
converting a source again yields an instrument with the same identity.

## Import from USB

The importer (`LibraryCopy_DoLibraryUpdate`) walks

    /fs/usb0/px/<group>/<NN Category>/<Instrument>.zip

and installs every archive whose two parent folders are a valid group and category name
(`LibraryCopy_ValidateFolder`). Each archive is extracted into `/hd/<group>/<NN Category>/` with
`unzip -qq -P asdf` (a second, obfuscated password is tried for the encrypted expansion packs; an
unencrypted archive is accepted since `-P` only applies to encrypted entries). After extraction
`<Instrument>/<Instrument>.grp` must exist, otherwise the folder is deleted again. So the archive
must contain the instrument folder itself, named like the archive, with the group file named like
the folder. The drive is formatted by the device (`mkdosfs -L ProphetX`, FAT32).

## The group file

A plain text table read by `CGroupVelocityManifestFileReader`:

* Lines end with CR, LF or CR LF; empty lines are skipped. The line buffer holds 512 bytes.
* Values are separated by TAB. Only `\t`, `\n`, `\r` and NUL end a value, so values may contain
  spaces; leading and consecutive separators are skipped, so an empty value shifts the following
  ones to the left.
* The first line is the header which names the columns, in any order. The 15 known names are
  compared with `strcmp` against `gGroupFileParmNames`; unknown columns are ignored. Every
  following line describes one sample (`CWavHandler`). The file must not start with a byte order
  mark, since that would corrupt the first column name.

| Column               | Type   | Default | Meaning                                                                                                 |
|----------------------|--------|---------|---------------------------------------------------------------------------------------------------------|
| `File Path`          | text   |         | Appended to the instrument folder path; normally just the WAV file name.                               |
| `Low Midi Note`      | int    | 0       | First key of the zone, inclusive.                                                                       |
| `High Midi Note`     | int    | 127     | Last key of the zone, inclusive.                                                                        |
| `Low Velocity`       | int    | 0       | Lowest velocity, inclusive.                                                                             |
| `High Velocity`      | int    | 127     | Highest velocity, inclusive.                                                                            |
| `Loop Start`         | int    | -1      | First frame of the loop; -1 = none.                                                                     |
| `Loop End`           | int    | -1      | Last frame of the loop, inclusive (see below); -1 = none.                                               |
| `Round Robin Number` | int    | -1      | 1-based number inside a set of samples which share key and velocity range; 0 is read as 1; -1 = no set. |
| `Pitch in Hertz`     | float  | 440     | Frequency at which the file sounds.                                                                     |
| `Mono Pitch`         | Y/N    | N       | `Y`: the sample plays at its own pitch on every key (no key tracking).                                  |
| `Stereo/Mono File`   | S/M    | S       | `S` = stereo file, anything else = mono. Informational; the channel count comes from the WAV.           |
| `Category`           | text   |         | Category name. Stored per instrument (first line wins); the folder decides the real category.           |
| `Instrument Name`    | text   |         | Display name. Stored per instrument (first line wins); the device lists the name of the group file.     |
| `Mono Collapse`      | L/R/B  | B       | Channel used when a stereo sample is collapsed to mono: left, right or both.                            |
| `UUID`               | hex    | zeros   | 32 hex digits without separators (`StrToGUID` reads exactly 32 nibbles, no dashes).                     |

Integers are read with `atoi` and the frequency with `atof`. The category and instrument name
buffers hold 64 bytes, so a name is limited to 63 characters; the name is also passed through a
shell (`unzip` is run with `system`), which is why the characters a shell expands are avoided.

After reading, the samples are kept in a list sorted by low note, then low velocity, then round
robin number. Consecutive entries with identical low note and low velocity form the round robin set
of a zone; the velocity layers of a key zone are counted from its distinct low velocities.

### Zone lookup (`CSampleGroup::FindHandler`)

For a played note the zone with the smallest distance to `[Low Midi Note, High Midi Note]` is used,
so gaps in the key map are filled by the nearest zone. Among the candidates of that key zone the one
closest to `[Low Velocity, High Velocity]` wins. If the winner has a round robin number other than
-1, all following entries with the same low note and low velocity are counted and one of them is
chosen at random (`rand () % count`), or by an index which the caller passes.

### Pitch

`CSampleOsc::PlayNote` computes the playback rate as `frequency (note) / Pitch in Hertz`. The
sample rate of the WAV file is never read (`CWavReader::GetSampleRate` has no caller), so files
must be at the 48 kHz of the audio engine to sound at the intended pitch. A fractional root pitch
is expressed directly in the frequency:

    Pitch in Hertz = 440 * 2 ^ ((root - 69 - tuning) / 12)

for a root key and a tuning offset in semitones (positive = the sample is played sharper), and
reading inverts this: the root key is the closest key to the frequency and the rest is the tuning.

With `Mono Pitch = Y` the disk scheduler is opened with the truncated integer frequency and the
sample plays untransposed on every key.

### Loops

`Loop Start` / `Loop End` are handed unchanged to `CWavReader::Open`; a negative start or end
disables the loop, a start beyond the end or an end beyond the frame count rejects it and the file
plays without a loop. `CWavReader::Read` keeps reading while the frame position is <= `Loop End`
and wraps to `Loop Start` when it exceeds it, so both values are inclusive frame indices. A missing
start loops from the first frame, a missing end to the last one. Loop cross-fade and
forward/backward playback are voice parameters of the program, not part of the file.

## `Volume.txt`

Optional file next to the group file (`VelocityVolumeFileParse`): up to 128 decimal numbers, one
gain factor per MIDI velocity (index 0 = velocity 0), separated by anything which is not a digit or
a dot - the parser reads digits and `.` only, so neither a sign nor an exponent is possible.
Entries after the last number stay at 1.0.

Without the file the instrument uses the built-in table `cVELOCITY_TO_VOLUME_TABLE` (128 floats):
0.126 (-18 dB) at velocity 0, 0.251 at 32, 0.504 at 64, 0.756 at 96 and 1.0 at 127, roughly a
linear velocity with an 18 dB floor. The file *replaces* this table, therefore it is the velocity
response of the instrument. ConvertWithMoss fits it to the amplitude velocity modulator of the
model, whose response is `1 - depth + depth * (velocity / 127) ^ (3 ^ curve)`: the depth is the
swing from velocity 0 to 127 and the curve the power law which fits the shape in between. It writes
the file from that modulator only when all zones share a response which differs from the default of
the model (depth 1, linear), so that an unspecified source keeps the built-in table of the device.

## WAV files (`CWavReader::Open`)

* `RIFF`/`WAVE` container; the chunks are scanned for `fmt ` and `data`, other chunks are skipped.
* 16-bit PCM only (`Only support 16-bit mono or stereo sample files`), one or two channels.
* Any sample rate is accepted but ignored, see above: use 48 kHz.
* The frame count is `data size / 2 / channels`.

## What the other writers do

PXToolkit (`ProphetWriter.js`) and the Goldbaby pack show the conventions which are known to work:

* The group file has only ten columns - `File Path`, `Low Midi Note`, `High Midi Note`,
  `Low Velocity`, `High Velocity`, `Loop Start`, `Loop End`, `Round Robin Number`,
  `Pitch in Hertz`, `Mono Collapse` - with LF line ends; the other five columns of the firmware are
  never written. Every row carries a round robin number (1 for a single sample), the lowest
  velocity is 1 and the pitch is written with full double precision.
* Keys which no zone covers are mapped to `silence.wav`, a 44-byte WAV with a `data` chunk of
  zero bytes, so that the device plays nothing on them instead of the nearest zone. The zones of
  the Goldbaby pack are instead stretched to 0 and 127.
* Loop points come from the `smpl` chunk of the source file and are passed on unchanged: the
  looped instruments of the Goldbaby pack have `Loop End` equal to their frame count, i.e. one
  frame beyond the file, which the device tolerates (it plays up to and including the frame at
  `Loop End`). ConvertWithMoss clamps a loop end to the last frame when it writes.
* The WAV files are stripped to the `fmt ` and `data` chunks, 16 bit, and their header always
  claims 48 kHz even when the audio is not converted: a file at another rate keeps its samples and
  gets `Pitch in Hertz * 48000 / rate` instead, so the engine plays it slower and in tune.
  ConvertWithMoss converts the audio to 48 kHz instead, which keeps the durations.
* An instrument name is `NN. First|Second`: a two-digit index which orders the instruments on the
  device (`firstnum_strcmp`), then the two lines of the display name separated by `|`. The tool
  rejects characters outside printable ASCII and `/ ? < > \ : * "`. Since `|` is not allowed in
  a file name on Windows, the archive itself may be named with `_` in its place; macOS stores a
  `|` on a FAT32 drive as U+F027, which the importer (`CorrectPipeInString`) maps back to `|`.
* The pack's guide states the import path on the device: Global menu, *34. Update Library* set to
  *User*, then *Update Now*; the folder `u07` fills the user bank 8, so `u00` is bank 1. It also
  warns that firmware before 2.1.0.0.0 can brick the device when samples are installed.
* The tool limits an instrument to 1.5 GB of samples and reads 16 or 24 bit WAV or AIFF.

## What the instrument cannot carry: modulation

Envelopes, the filter, the LFOs, the modulation matrix, glide, unison, pitch bend and the loop
playback modes are parameters of the *program* which plays an instrument, so a converted
multi-sample loses them unless a program is written as well. What is known about programs, from
the User's Guide 1.2 (Appendix E) and the factory bank `PX_Programs_v2.0.syx`:

* A program is transmitted as `F0 01 30 02 <bank 0-9> <program 0-99> <data> F7` (edit buffer:
  `F0 01 30 03 <data> F7`), the data being 4096 bytes in the DSI "packed MS bit" format (8 MIDI
  bytes per 7 data bytes, 4683 MIDI bytes).
* The NRPN number of a parameter is its byte offset in the data: layer A at 0-2047, layer B at
  2048-4095, parameters with a range above 255 (`Inst1Start` 0-999 etc.) take two bytes, little
  endian. The program name is 20 characters at offset 418 (2466 for layer B).
* A program references its two instruments per layer by `InstNCategory` (0-16) and `InstNSelect`
  (0-99), i.e. by the index of the instrument inside its category folder - which is the alphabetical
  position among whatever the user has installed there. No UUID or name is stored in the program;
  the group (user bank) of a user instrument is an OS 2.x addition whose offset the 1.2 manual does
  not list (all factory programs reference group `f00`).
* The programs are stored and imported by the panel processor; the OS image analysed here only
  forwards `PxPrograms.prg` from the USB drive to it and reports `.syx` files found in
  `px/<group>/`. Loading a program file therefore goes through a SysEx librarian (or that folder),
  not through the instrument import.

Writing such a program per converted instrument is possible (amplitude envelope, filter, two LFOs
as vibrato and tremolo, velocity to amplifier, bend range, glide, mono mode, program name), but
the value laws (envelope times, LFO rates, cutoff) still need calibration and the instrument index
only holds as long as nothing else is installed in front of it in that category. It is not
implemented.
