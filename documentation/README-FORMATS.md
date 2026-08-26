# Common options

The following settings are available in several source and destination formats and only explained once.

## Common Source Options

### Sample File Search

* Go this number of directories upwards to start file search: If a referenced sample (or other required file types of the format) cannot be found, this option configures how many folders upwards the search should be started. 0 means to start from the current directory where the source file is located and search downwards in the sub-folders as well.

### Automatic Metadata detection

If there are no metadata fields (category, creator, etc.) specified in the format, information is retrieved from Broadcast Audio Extension chunks in the WAV files. If no such chunks are present, ConvertWithMoss can detect from the name and path of the file. The following settings can be used to tweak the detection process:

* Prefer folder name: If enabled the name of the multi-sample will be extracted from the folder instead of the sample names.
* Category tag at name start declares the category: Many commercial libraries prefix each preset name with its category (e.g. 'PAD Solina', 'BASS Growler'). If enabled, such a tag at the very start of the name declares the category and takes precedence over keyword matches elsewhere in the name (e.g. 'BELL Vibrato Strings' is then detected as Bell instead of Strings). Common abbreviations are recognized as well: BRAS, DRM, FLUT, GRAN, ORG, PERC, PHYS, PLUK, POLY, REES, STRG, SWEP, VOC. Leave it off for libraries which do not follow this naming convention (a name like 'Syn Brass' would otherwise be detected as Synth instead of Brass).
* Default creator: The name which is set as the creator of the multi-samples, if no creator tag could be found.
* Creator tag(s): Here you can set a number of creator names, which need to be separated by comma. You can also use this to look up other things. For example, I set the names of the synthesizers which I sampled. My string looks like: "01W,FM8,Pro-53,Virus B,XV" (without the quotes).

## Common Destination Options

### WAV Chunk Information

If the format uses WAV files to store the samples, the following options to additionally write metadata information to the respective chunks are available:

* Broadcast Audio Metadata: This can contain a description text, the creator of the sample and the creation date and time.
* Instrument: Contains the root note, fine tuning, gain, the key range and the velocity range.
* Sample: Contains the root note, fine tuning and loop points.
* Remove JUNK, junk, FLLR and MD5 chunks: Enable this option to drop these chunks. Junk and filler chunks are only for aligning the following chunks to certain data positions. The MD5 chunk contains a checksum which is currently not updated and therefore should be dropped.

# Common parameters

Some parameters are supported by many formats but need a common reference point or cannot be expressed by every format. They are converted as follows, which is identical for all source and destination formats.

* One-shot playback (a note-off is ignored and the sample is always played back to its end) and the exclusive ('choke') group (all sounding notes of the same group are stopped when a note of that group is started, e.g. a closed hi-hat cutting off an open one) are stored for each sample zone. Formats which keep them on the group, oscillator or instrument level can only write them if all zones of that level agree on the value, otherwise nothing is written.
* Formats which cannot express a random selection of a zone fall back to round-robin and never to playing all zones at once. The variation is then a fixed cycle instead of a random one.
* The amplitude keyboard-tracking has no natural 1:1 reference like the pitch and the filter cutoff keyboard-tracking. Its unit is therefore fixed to 1 decibel per key relative to the root key of the sample zone, which means that the maximum raises the volume by 12 dB per octave above the root key. Formats whose own scaling is not documented approximate their range linearly.
* The envelope time keyboard- and velocity-scaling shortens or lengthens all times of an envelope depending on the played key and velocity (in contrast to the envelope slopes, which describe the curvature of a segment and not its duration). It has no effect at MIDI note 60 and at velocity 64; a positive value shortens the times towards higher keys and velocities, a negative value lengthens them.

# Supported Formats

The following multi-sample formats are supported:

* [1010music bento](#1010music-bento)
* [1010music blackbox, tangerine, bitbox](#1010music-blackbox-tangerine-bitbox)
* [Ableton Sampler](#ableton-sampler)
* [Akai AKP/AKM (S5000/S6000/Z4/Z8/MPC4000)](#akai-akpakm-s5000s6000z4z8mpc4000) - read only
* [Akai MESA](#akai-mesa) - read only
* [Akai MPC Modern](#akai-mpc-modern)
* [Akai MPC60](#akai-mpc60) - read only
* [Akai MPC500/MPC1000/MPC2500](#akai-mpc500mpc1000mpc2500) - read only
* [Akai MPC2000/MPC2000XL/MPC3000](#akai-mpc2000mpc2000xlmpc3000) - read only
* [Akai S900/S950 image](#akai-s900s950-series-disk-image) - read only
* [Akai S1000/S3000 image](#akai-s1000s3000-series-disk-image) - read only
* [Arturia Synclavier V](#arturia-synclavier-v)
* [Audiomodern Soundbox format](#audiomodern-soundbox)
* [Bitwig Multisample](#bitwig-multisample)
* [Casio FZ-1/FZ-10M/FZ-20M](#casio-fz-1fz-10mfz-20m)
* [CWITEC TX16Wx](#cwitec-tx16wx)
* [DecentSampler](#decentsampler)
* [discoDSP Bliss](#discodsp-bliss)
* [Downloadable Sounds (DLS)](#downloadable-sounds-dls) - read only
* [Elektron Tonverk Multisample & Preset](#elektron-tonverk)
* [E-mu Emulator](#e-mu-emulator)
* [E-mu Emulator II](#e-mu-emulator-ii)
* [E-mu Emulator III/IIIX/ESI](#e-mu-emulator-iiiiiixesi)
* [E-mu Emulator IV](#e-mu-emulator-iv)
* [E-mu Emulator X](#e-mu-emulator-x)
* [Ensoniq EPS/EPS16+/ASR-10](#ensoniq-epseps16asr-10) - read only
* [Ensoniq Mirage](#ensoniq-mirage) - read only
* [Expert Sleepers disting EX](#expert-sleepers-disting-ex)
* [Fairlight CMI Voice](#fairlight-cmi-voice)
* [FL Studio DirectWave](#fl-studio-directwave)
* [ISLA S2400](#isla-s2400)
* [ISO/IMG Files](#isoimg-files)
* [Korg KSC/KMP/KSF](#korg-ksckmpksf)
* [Korg wavestate/modwave](#korg-wavestatemodwave)
* [Kurzweil K2000/K2500/K2600](#kurzweil-k2000k2500k2600)
* [Logic EXS24](#logic-exs24)
* [Native Instruments Kontakt](#native-instruments-kontakt)
* [Native Instruments Maschine](#native-instruments-maschine)
* [Polyend Tracker](#polyend-tracker)
* [Propellerhead Reason NN-XT](#propellerhead-reason-nn-xt)
* [Renoise](#renoise)
* [Roland MC-707/MC-101](#roland-mc-707mc-101)
* [Roland MV-8000/MV-8800](#roland-mv-8000mv-8800)
* [Roland S-10 Series](#roland-s-10-series) - read only
* [Roland S-50 Series](#roland-s-50-series) - read only
* [Roland S-770 Series](#roland-s-770-series) - read only
* [Roland SP-404MK2](#roland-sp-404mk2)
* [Roland ZEN-Core](#roland-zen-core)
* [Sample files (AIFF, CAF, FLAC, NCW, OGG, WAV)](#sample-files-aiff-caf-flac-ncw-ogg-wav)
* [SFZ](#sfz)
* [SoundFont 2](#soundfont-2)
* [Spectrasonics Omnisphere 3](#spectrasonics-omnisphere-3)
* [Synclavier Regen](#synclavier-regen)
* [Synthstrom Deluge](#synthstrom-deluge)
* [TAL Sampler](#tal-sampler)
* [Teenage Engineering OP-XY](#teenage-engineering-op-xy)
* [Waldorf Quantum MkI, MkII / Iridium / Iridium Core](#waldorf-quantum-mki-mkii--iridium--iridium-core)
* [Yamaha YSFC](#yamaha-ysfc)

## 1010music bento

This format can contain either a single *patch* (1 track) or all 8 tracks of a *project*. Each track contains an instrument engine. ConvertWithMoss only supports the multi-sample engine. All user patches need to be placed in the *UserPatches/SampInst* folder on the SD-card. The factory patches reside in *Patches/SampInst*. The main information of a preset is stored in a file which is always called *patch.xml* or *project.xml* for a project file. This file is located in a folder with the name of the preset/project.

The related samples need to be in the same folder as the patch.xml file.

If the format is selected as the source, there are two things to consider:

* One or multiple tracks contain a multi-sample: for each of the multi-samples a file in the destination format is created.

### Destination Options

* Option to set the *Interpolation Quality*. Setting it to *High* requires a bit more processing power on the 1010music devices.
* Option to trim sample to range of zone start to end. Since the format does not support a sample start attribute for multi-sample, this fixes the issue.

If 'Performance' is selected as the destination type, some workarounds are applied:

* MIDI channel: There is no OMNI setting. They are currently set to Off.
* Key ranges: The 1010music devices do not support key-ranges which means a multi-sample is always sounding across the full note range. As a workaround a silent sample (a totally empty one-shot sample is used) is applied to the lower and upper range which should not sound.

## 1010music blackbox, tangerine, bitbox

This format is simply called a *preset*. A preset contains 16 slots and each slot can either contain a simple sample or a complex multi-sample. All presets need to be placed in the *Presets* folder on the SD-card. The main information of a preset is stored in a file which is always called *preset.xml*. This file is located in a folder with the name of the preset (eg. /Presets/MyLovelyPiano/preset.xml).

The related samples can be anywhere on the SD-card, if referenced accordingly in the preset.xml file. But to make the handling easier, the output of this tool puts all sample files in the same folder as the preset.xml file. Therefore, only one folder needs to be copied to the Presets folder on the SD-card.

If the format is selected as the source, there are two things to consider:

* One or multiple slots contain a multi-sample: for each of the multi-samples a file in the destination format is created.
* All slots contain only single samples: one file in the destination format is created which combines all 16 slots. The notes are from 36 upwards if not configured differently in the preset.

### Destination Options

* Option to set the *Interpolation Quality*. Setting it to *High* requires a bit more processing power on the 1010music devices.
* Option to trim sample to range of zone start to end. Since the format does not support a sample start attribute for multi-sample, this fixes the issue.

If 'Performance' is selected as the destination type, some workarounds are applied:

* MIDI channel: There is no explicit OMNI setting. Instead, if MIDI channel 1 is selected on the device it acts as the OMNI channel, which means it is always sounding and renders MIDI channel 1 to be unusable. As a solution all MIDI channels are increased by 1 (channel 1 is 2, channel 2 is 3, ...) and channel 16 is set to Off. All instruments which have OMNI configured are set to channel 1.
* Key ranges: The 1010music devices do not support key-ranges which means a multi-sample is always sounding across the full note range. As a workaround a silent sample (a totally empty one-shot sample is used) is applied to the lower and upper range which should not sound.

## Ableton Sampler

Ableton uses a generic preset format (*.adv) for all of their devices. For combined rack presets another format (*.adg) is used. All their formats are XML documents which are compressed with the open GZIP algorithm.

ConvertWithMoss can extract Sampler and Simpler presets from ADV files as well as all instances of Sampler or Simpler in ADG files when selected as a source. The presets from the Ableton libraries cannot be extracted since their AIFF files use a proprietary encryption algorithm. It writes ADV files as the destination.

ADV files and their samples need to be placed in the Ableton user library in the correct folders to allow Ableton to open it. Therefore, ConvertWithMoss creates the necessary folder structure which can be simply copied to the user library. If the source has sub-folders the global option *Create folder structure* should be deactivated otherwise it can be quite tedious to collect all the results files with their additional Ableton sub-folder structure.

Round-robin is supported in both representations: the native round-robin flag of Live 12 and the classic Sampler way of spreading the cycles across the sample-select (selector) ranges of otherwise identical zones. When writing, the selector representation is used whenever the native flag is not available (Ableton 11) or does not apply (only some of the groups alternate); at the default selector position only the first cycle is heard and the other cycles can be reached by modulating the sample selector.

### Destination Options

* Option to set the *Ableton Version*. Setting it to *12* will add additional Round-Robin information (but cannot be loaded in Ableton 11).

## Akai AKP/AKM (S5000/S6000/Z4/Z8/MPC4000)

This format uses a chunk based binary format with the ending AKP. It supports up to 99 key-groups. A key-group covers a note range with up to 4 velocity layers. AKM files are a multi configuration of up to 32 AKP preset files. The AKP files are only referenced from the AKM. Available parameters are the MIDI channel, panning, volume and key-range.
AKP files are used if destination is Preset or Preset Library. AKM files are used if destination is Performance or Performance Library. 
Only reading of the AKP/AKM formats is supported.

## Akai S900/S950 series disk image

The Akai S900 is a 12-bit sampler, with a variable sample rate from 7.5 kHz through to 40 kHz. Up to 32 samples can be created and stored to disk along with any edit settings. An expanded version, the Akai S950, was released in 1988 alongside the higher end S1000. The S950 soon followed the S900 and offered increased memory and sampling rates. The sample rate was now variable from 7.5 to 48kHz and it could hold up to 99 samples in memory. Memory could be expanded from 750KB to 2.25MB. Unlike the S1000 series, the S900 series allows a sample to loop alternating forwards and backwards.

**Help needed:**
* I am missing the info about the different velocity modulation settings. If you own one of these machines it would be great if you could provide me some examples with different velocity settings (keeping all other parameters identical).
* Furthermore, if you have a HFE file with valid data I would like to take a look.

## Akai S1000/S3000 series disk image

The Akai S1000 and S3000 series are landmark professional digital samplers first introduced by Akai in the late 1980s and early 1990s. The S1000 became widely adopted in studios and electronic music production for its 16-bit PCM sampling, extensive on-board editing, and reliable MIDI integration. The S3000 series built on that legacy with expanded memory, improved filtering, and more advanced modulation and layering capabilities, offering deeper sound design flexibility.

The CD format used with the S1000/S3000 series was a proprietary Akai CD-ROM structure built on standard ISO-9660 physical media but organized according to Akai’s own disk architecture. Data was stored as 16-bit linear PCM sample files along with separate program and keygroup parameter data, arranged in volumes that the sampler’s operating system could index via SCSI. Unlike generic audio CDs (Red Book), these discs were data CDs containing structured directories and allocation tables specific to Akai’s file system, enabling direct loading of samples, programs, and partitions into memory. While ConvertWithMoss cannot directly read the CDs, it can read images created from them with other tools (normally named *.iso). There is no write support.

Programs of a volume which share their MIDI program number (on the same MIDI channel) are combined into one multi-sample with one group per program: selecting that program number on the sampler activates all of them, so they always sound layered. Many CD-ROMs build their final patches this way from 2 or 3 component programs, e.g. 'DIGIJAZZ A' + 'DIGIJAZZ B' become the multi-sample 'DIGIJAZZ'. Zones with an upper velocity of 0 are skipped: a velocity of 0 is a note-off and can never trigger them - they are used to mute the layers which belong to the partner program and to carry copyright information.

## Akai MESA

The Akai MESA S3P format is a computer-side representation of Akai S-series program data used by the original MESA (Mac/PC Multi-Editor and Sample Accelerator) librarian/editor software for the [S-3000](#akai-s1000s3000-series-disk-image) family. In practice the .S3P extension contains a classic S3000-style Program (instrument) encoded in a format akin to MIDI SysEx dumps, with the sample waveforms stored externally as accompanying WAV files on a computer. Internally the Program’s structure—keygroups, sample references, mapping, filters and loop parameters—is essentially the same as an Akai S-series Program on disk; MESA simply encapsulates the Akai program data in its own file container for editing and transfer.

## Akai MPC Modern

### Keygroups / Drum

A MPC Keygroup or MPC Drum setup is stored in a folder. It contains a description file (.xpm) and the sample files (.WAV). Both keygroup and drum types are supported.

### Akai MPC Project/Track

A track file (*.xty) is a MPC v3 specific file that saves all settings, samples, macros, FX and MIDI data associated with a track. A track consists of two elements; the track file itself and a trackData folder containing the samples used within the track (ending with '_\[TrackData\]'). If the track contains a keygroup it is extracted as a multi-sample source.
A project file (*.xpj) contains all track and project settings. All tracks which contain a keygroup are extracted as a multi-sample source.

### Source Options

* Ignore Loops: There are XPM files which do not contain loops but the related WAV files do (seems to happen with the MPC Autosampler). ConvertWithMoss uses the loops from the WAV files in that case. This might not be what you intended if a multi-sample should be one-shot. Enable this option to ignore the loops.

### Destination Options

* Output Format: Writes either a MPC 2 keygroup - a folder with the description file (.xpm) and the samples - or a MPC 3 track file (.xty) with its sample folder (ending with '_\[TrackData\]'). The track files replicate the complete 'SerialisableTrackData' structure of files written by MPC firmware 3.7 - all 128 instrument slots with their layers, the key-group performance controls and the sample list - but have not been verified in the MPC 3 software itself yet; reports welcome.
* Limit layers to: MPC Firmware 3.4 increased the number of possible layers in a keygroup to 8. This option allows you to choose between 4 (for older firmware revisions) or 8.

### Destination Restrictions

* A round robin or random keygroup can only contain up to 4/8 layers (groups), since the MPC picks one of the layers of a keygroup. An error is displayed in this case but the file is converted anyway.
* Only 128 keygroups are allowed. An error is displayed in this case but the file is written anyway but might not be loadable.

## Akai MPC60

Reads Akai MPC60 Set files (*.SET). Such files are monoliths containing the samples as well. This format stores 32 'pads'. Each pad is assigned to 1 MIDI note but they must not and the default is that they are off. Therefore, the pads are simply mapped to MIDI keys in ascending order starting with MIDI note 36.   
A pad can reference another pad for a velocity split.

The sampling rate is fixed to 40kHz which is quite uncommon and some programs might not be able to play it back.

Floppy disk backups (ending with *.IMG or *.HFE) in MPC60 format can be read as well.

**Several parameters are still unknown. Currently, mainly the samples and names are converted. Please get in touch if you have a MPC60 and can spend some time in analysis by storing different parameter settings.**

## Akai MPC500/MPC1000/MPC2500

Reads Akai MPC500/MPC1000/MPC2500 programs (*.PGM) which reference WAV files. This format stores 64 'pads'. Each pad is assigned to 1 MIDI note and can contain up to four samples with different velocity settings.

**I am missing the info about the filter envelope. If you own one of these machines it would be great if you could provide me some examples with different filter envelope settings (keeping all other parameters identical).**

## Akai MPC2000/MPC2000XL/MPC3000

Reads Akai MPC2000/MPC2000XL/MPC3000 programs (*.PGM). This format stores 64 'pads'. Each pad is assigned to 1 MIDI note and can contain up to three samples with 2 velocity splits. All PGM files can only reference SND's in the same folder as the PGM.

CD-Rom/Harddisk backups (ending with *.ISO, *.IMG or *.HFE) in MPC2000 and MPC2000XL format can be read as well.

## Arturia Synclavier V

Arturia's Synclavier V (developed with Synclavier Digital) recreates the classic Synclavier synthesizer. Its *Export Preset* and *Export Bank* functions produce *.synx* files: ZIP archives which contain one preset file per exported preset. A preset holds up to twelve *partials* (layers); each partial can either synthesize its sound (FM plus resynthesis frames) or play a referenced sound file directly (its carrier is in *Audition* mode). Sound files are referenced by name and live in the Arturia sample pool (`/Library/Arturia/Samples/Synclavier V/` on macOS, `C:\ProgramData\Arturia\Samples\Synclavier V` on Windows) or, for imported samples, under their original absolute path.

Reading: every preset of a synx file (a bank export contains several) becomes one multi-sample. Partials which play a sound file become its groups/zones with their key window and cross-fades (the *keyboard envelope*), velocity window and cross-fades (the *crossfade envelope*, when the dynamic envelope source is velocity), root key, fine tuning (file tuning plus the partial tuning, transpose and octave), volume (partial plus file volume), panning, sample start/end, loop (either the explicit file loop or, with the *intrinsic* option, the loop stored in the WAV file itself) and the volume envelope (delay, attack, initial decay to the sustain level, final decay as release; the times run through the quantized Synclavier time table). Referenced sound files are first taken from the audio embedded in the preset itself (exports are self-contained), then searched under their absolute path, relative to the synx file and in the Arturia sample pool. Synthesis-only presets contain no audio to convert and are skipped with a note - the same applies to presets whose samples were only the source of a resynthesis (the classic *Time Slices*).

Writing: each sample zone becomes a partial in Audition mode (at most 12 zones - reduce the number of layers if necessary), with the same parameters as above; velocity layers set the dynamic envelope source to velocity. The samples are embedded in the written preset files, exactly as the application's own exports do, so a written synx file is self-contained. All other synthesis parameters come from a neutral template. To use a converted file, import it into Synclavier V (verified with version 2.13); alternatively the archive can be unzipped and its presets renamed to *NN-Entry.txt* for the Synclavier Regen, as described in the Synclavier Regen documentation supplement. Writing a library creates one synx file which contains all the presets, like a bank export. The format is not documented; it was reverse-engineered from the Synclavier V factory content, a genuine export and the application resources (see *documentation/design/SYNCLAVIER_V_FORMAT.md*).
### Destination Options

The *Write the samples into a sample pool folder* option additionally writes the samples as plain WAV files under `User/&lt;preset&gt;/` next to the synx files. This works around a flaw of the application's import: the embedded samples are loaded into the running instance but not copied into the sample pool - they are extracted relative to the working directory of the host process - so a preset loaded from the browser database in a later session reports its samples as missing at the pool path (and the standalone application, whose working directory is not writable, fails the import altogether; import inside a DAW). Merging the written *User* folder into the Arturia sample pool (see above for its location) makes the imported presets permanent.

## Audiomodern Soundbox

Soundbox is a sample player plug-in with 4 layers; each layer plays one *group* which contains the mapped samples. Sound packs (*.sbpack*) are ZIP archives with the pack description, all presets and all samples, they are installed with the *Import* function of the plug-in. The format is not documented; it was reverse-engineered from several factory packs (see *documentation/design/SOUNDBOX_FORMAT.md*).

Reading: every preset of a pack becomes one multi-sample. Each active layer becomes a group with its samples as zones: key range, root note, velocity range, sample start/end, loop with cross-fade (also ping-pong), reverse play-back, volume, panning and tuning. The layer volume, panning, fine tune, transpose and octave settings as well as the amplitude envelope (ADSR) of the layer engine are folded into its zones. A filter effect in one of the 4 effect slots of the layer (or of the master) becomes the filter of its zones with its cutoff and resonance; the voice mode (mono/legato/poly) and the glide of the first layer become the polyphony and portamento of the instrument. The other 9 effect types (reverb, delay, EQ, distortion, chorus, phaser, lofi, compressor, noise) have no counterpart in the multi-sample model and are not converted. The round robin layers which newer plug-in versions support are read as separate groups with round robin play logic. The fade-in/out of a sound and all the sequencer, arpeggiator and effects settings are not converted. The envelope values are the 0-100% knob positions of the plug-in. The time laws were measured with plug-in 1.2.1 and are converted accordingly: the attack knob maps linearly to 0-4 seconds, the release knob linearly to 0-20 seconds (the decay knob is assumed to follow the release law; it is inaudible at the 100% sustain which all factory presets use).

Writing: each multi-sample source becomes one preset of the written pack (writing a library creates one pack which contains all the presets). The round robin groups of the source become the round robin layers of one Soundbox group (verified with plug-in 1.2.1); all other groups are mapped to the up to 4 layers of the preset, which play simultaneously - since every zone keeps its velocity range this makes no audible difference. More groups than layers are merged into the first layer. Panning, gain and the fractional part of the tuning which is common to all zones of a layer is stored lossless in the layer parameters, the rest per sound (the tuning in full semi-tones, the gain as a percentage which cannot exceed 100%). The amplitude envelope of the first zone becomes the layer ADSR, and a filter which is common to all zones of a layer is written as a filter effect into its first effect slot. To use the written pack, import it into Soundbox with the *Import* button of the preset browser.

## Bitwig Multisample

This open format is currently supported by the stock sampler in Bitwig Studio and Presonus Studio One. A multisample file is a zip archive which contains all samples in WAV format and a metadata file in XML format.
It supports multiple groups, key and velocity crossfades as well as several metadata information: creator, sound category and keywords.

The parser supports all information from the format except the group color and select parameters 1 to 3, which are not mappable.

This converter supports (split) stereo uncompressed and IEEE float 32 bit formats for the WAV files.

## Casio FZ-1/FZ-10M/FZ-20M

The Casio FZ series are 16-bit samplers from 1987 with a built-in 3.5" floppy drive. The implementation follows the official 'FZ-1 Data Structures' documentation from Casio.

As a source, floppy disk images (*.img*, raw 1,310,720 bytes, or *.hfe* for HxC/Gotek floppy emulators) as well as bare dump files (*.fzf* full dump, *.fzv* voice, *.fzb* bank - both with a file head and in the head-less layout of the *fzdump* utility, in which the circulating libraries like the factory library come) are read. Each bank of a dump becomes one multi-sample: its areas define the key splits, velocity splits, area volumes and the root keys; the voices provide the 16-bit samples (36, 18 or 9 kHz) with their sustain loop, loop cross-fade, pitch correction, play mode (normal or reversed), the velocity to amplitude depth, the amplitude and filter envelopes and the filter with its resonance. Dumps without a bank become one multi-sample from the key ranges of the voices. The pitch bend range is read from the effect parameters. The envelope rates and the filter cutoff of the FZ have no documented unit, they are converted with an approximated law.

As the destination, a ready-to-use floppy disk image (*.img*) with one full dump file is written, which can be written to a disk or copied to a floppy emulator. The zones become the areas of a bank with one voice each (up to 64). Samples are converted to 16-bit mono; their sample rate is kept and the difference to the closest FZ hardware rate is compensated with the pitch parameter of the voice, so the samples are stored without any loss. If the samples do not fit onto the 1.25 MB of a floppy disk, zones are dropped from the end with an error message. Writing has not been verified on real hardware yet.

## CWITEC TX16Wx

TX16Wx is a free sampler plug-in available for Windows and MacOS. TX16Wx Professional is the commercial expansion of TX16Wx. It adds some advanced features like effects, signal routing or trigger switching. But the free version is already very powerful and covers all of the features that ConvertWithMoss supports.

The output formats use a XML format and keep the samples separate. There following file formats are supported:

* txprog: Single program files.
* txbank: A bank of program files, the program files are only referenced and individual txprog files are created as well.
* txperf: A performance which contains performance settings for several program files, the program files are only referenced and individual txprog files are created as well.

## DecentSampler

The Decent Sampler plugin is a free (but closed source) sample player plugin that allows you to play sample libraries in the DecentSampler format (files with extensions: dspreset and dslibrary). See https://www.decentsamples.com/product/decent-sampler-plugin/
The format specification is available here: https://www.decentsamples.com/wp-content/uploads/2020/06/format-documentation.html#the-sample-element

A dspreset file contains a single preset and the description of the multi-sample. The related samples are normally kept in a separate folder. WAV and FLAC files are supported. A dslibrary file contains several dspreset files including the samples compressed in ZIP format. A dsbundle is similar but uncompressed.

There are no metadata fields (category, creator, etc.) specified in the format. Therefore, information is stored and retrieved from Broadcast Audio Extension chunks in the WAV files. If no such chunks are present an [automatic detection](#automatic-metadata-detection) is applied.

A pitch LFO (vibrato) is converted as an `<lfo>` bound to the group tuning, carrying its rate (in Hertz), depth and delay. The oscillator's fade-in has no equivalent and is dropped, and the waveform is mapped to the nearest of sine, square and saw. A volume LFO (tremolo) is converted as an `<lfo>` bound to the group volume in the same way; since the volume parameter is linear, the depth in decibels is translated into the linear swing whose lowest point lies that many decibels below full volume.

### Source Options

* Create one multi-sample per group: Creates a separate multi-sample for each group instead of one multi-sample which contains all groups. Intended for presets which contain several alternative kits or articulations as groups and switch between them via their user interface (only one group is enabled at a time). Disabled groups are converted as well when this option is enabled; when it is off, only the enabled groups are converted.
* Log unused XML elements and attributes: If enabled the XML elements and attributes which are not used in the translation process are logged.

### Destination Options

* Output Format - Create Bundle: Choose to create a bundle (instead of single presets or a library).
* Make monophonic: Restricts the sound to 1 note, use e.g. for lead sounds. If it is disabled, the polyphony of the source is applied instead (if it has one).
* Add low-pass filter to all groups if none is present: This always adds a low-pass filter on a group level, if no filter is present yet in the source material. Enable it if you want to have controls for a filter envelope in your template.
* Template and resources folder: Allows to modify the UI and effects section of the presets (see below).
* Options to write/update [WAV Chunk Information](#wav-chunk-information).

If no 'Template and resources folder' is configured the default template is used which creates several controls for an amp envelope, a lowpass filter with envelope, a delay and reverb as well as pitch-modulation via mod-wheel.
To modify this template, first create an empty folder somewhere on your disc. Select this folder in the 'Template and resources folder' field. Then click on the button 'Create template in the selected folder'. This copies the template 'ui.xml' into this folder. You can copy additional images and documentation files to the folder. These resource files will be added to the output as well. This template will be applied to all created dspresets. But note that you can have multiple templates if you use several template folders which can then be switched for each conversion run.

The template can contain 1 effects, modulators, midi and ui tag. The content of the modulators-tag will be added to the existing modulators-tag which gets created by ConvertWithMoss. Make sure to use the correct indices!

There are two issues with amplitude envelopes:

1. If an envelope is applied to a certain level (sample, group or instrument) it does not work to change the values with a knob on a different level. Therefore, ConvertWithMoss tries to set the envelope on the highest possible level (the instrument) if all of the sample envelopes are identical.
2. If controls are assigned to an envelope value to change it, it will not pick up this value but will use the value set for the control-element instead. To work around this issue the following variables can be used in the template and will be replaced with the instrument amplitude values:

* %ENV_ATTACK_VALUE%
* %ENV_DECAY_VALUE%
* %ENV_SUSTAIN_VALUE%
* %ENV_RELEASE_VALUE%
* %ENV_VELOCITY_SENSITIVITY%

## discoDSP Bliss

Bliss is a multi-platform (Windows, MacOS & Linux) sampler by discoDSP (https://www.discodsp.com/bliss/).
It provides support for multi-samples and a bank system (containing up to 128 patches).
Both the program (.zbp) as well as the bank (.zbb) are stored as monoliths (zipped) with a XML description file and all samples. The samples are stored in FLAC format (16/24 bit). The full format specification is available here: https://github.com/reales/bliss-format.

## Downloadable Sounds (DLS)

The DLS format (*.dls) is a standardized file format developed for storing and distributing collections of digital musical instrument sounds, enabling their use in software synthesizers and hardware devices compatible with the MIDI protocol. It encapsulates audio samples, instrument definitions, articulations, and performance parameters into a single file. Developed in the 1990s initially by the Interactive Audio Special Interest Group (IASIG) and later standardized by the MIDI Manufacturers Association (MMA), with the first formal specification released in 1999.
There is no write support.

The amplitude and pitch envelopes, the sample loops and a pitch LFO (vibrato) are read. The vibrato's depth, its frequency (converted from absolute pitch cents to Hertz) and its start delay are carried over to the pitch LFO. Only a connection which is not modulated by a controller is read as the vibrato; the format normally contains a second one controlled by the modulation wheel, which is the amount the wheel can dial in and would sound permanently if it were converted. A volume LFO (tremolo) is read from an uncontrolled connection of the oscillator to the gain under the same rule; it shares the frequency and delay of the vibrato since the format has only one modulation oscillator.

## Elektron Tonverk

The Elektron Tonverk is a dedicated hardware sampler that marks an important milestone for Elektron as its first instrument to support multi-samples. This allows users to map multiple sampled sounds across keys or velocity ranges, creating more expressive and realistic instruments than single-sample playback alone.

ConvertWithMoss supports two Elektron Tonverk formats: the basic multi-sample mapping files (*.elmulti / *.eldrum) and the full preset (*.tvpst).

### Multi-Sample Mapping (.elmulti / .eldrum)

The elmulti format is very basic and limited. It only supports the basic multi-sample layout and does not contain any synthesizer parameters like envelopes or filter settings.
Furthermore, even this basic setup has some limitations:

* There are no key ranges, the Tonverk always plays the sample with the closest root note. This can lead to different key-ranges than in the source multi-sample.
* Velocity layers are fixed to the key-ranges (like on the modern Akai MPCs).
* Duplicated velocity layers always result in round-robin of these samples (they do not sound at the same time).
* Only 1 Pitch per key zone can be set which means you cannot tune individual samples.

#### Destination Options

* Re-sample to 24bit/48kHz: If enabled, samples will be resampled to 24bit and 48kHz. While the device can play other resolutions as well, there are reports of issues when you do so.

### Preset (.tvpst)

In contrast to the mapping files, a Tonverk preset is a full sound that also contains the synthesizer parameters. The sample-based generator machines are read:

* **Multi**: a multi-sample mapped to key- and velocity-ranges.
* **One-Shot**: a single sample mapped across the whole keyboard.
* **Drum**: a kit of eight drum voices, each on its own key with its own settings.
* **Grainer**: granular playback of a single sample. Since grains cannot be represented in the multi-sample model, the sample is converted like a One-Shot; the granular engine parameters are not converted.

The amplitude envelope (AHD or ADSR), the multi-mode filter together with its envelope, the sample loops, gain and panning are converted. The remaining, synthesizer-specific parameters (arpeggiator, effects, global LFOs and the modulation matrix) have no equivalent in the multi-sample model and are therefore not converted.

When writing, the samples are stored next to the preset and referenced by their relative file name, so the preset can be copied anywhere onto the SD card. The full parameter block is created from a neutral factory template (effects bypassed, LFOs, arpeggiator and modulation neutralized) and only the converted parameters above are filled in from the source.

Note: the Tonverk stores envelope times and the filter cut-off frequency as normalized values using internal, non-published curves. ConvertWithMoss uses documented approximations for these. A Tonverk-to-Tonverk conversion is therefore loss-less, while a conversion to or from a unit-based format (such as Waldorf Quantum/Iridium or the Synthstrom Deluge) is a close approximation.

#### Destination Options

* Output Engine: Selects which machine to write. *Multi-Sample* and *Drum Kit* force that machine; *Auto (from source)* writes a Drum machine when the source looks like a drum kit (a percussion category or up to eight single-key zones) and a Multi machine otherwise.
* Re-sample to 24bit/48kHz: If enabled, samples will be resampled to 24bit and 48kHz. While the device can play other resolutions as well, there are reports of issues when you do so.

## E-mu Emulator

The E-mu Emulator (1981) is the first sampler of the line: a 49 key keyboard whose lower and upper halves each play one *bank* of 8 bit companded samples - about 2 seconds of audio at 27,778 Hz per half - loaded from a single-sided 5.25" floppy disk. The multi-sampling software which followed the initial release divides a half into up to eight zones of equal width which each play one sample, and the disk also carries the operating system of the machine (the Emulator has no ROM), so every sound disk is a boot disk. The disks are written in a FM track format which no PC floppy controller can read, so a disk gets onto a computer as an image of a HxC/Gotek floppy emulator (*.hfe*) or as a raw sector image (*.emufd*, 125,440 bytes, or *.img*). The format is not documented by E-mu; the track format was described by the EMXP project and the bank layout was reverse-engineered here from the factory library and verified against the SoundFonts EMXP made of the *Production Set* disks (see *documentation/design/EMULATOR1_FORMAT.md*).

When reading, one disk becomes one multi-sample: the zones of the lower half (MIDI notes 36-59) and of the upper half (60-84) with their key ranges, and for each zone its root key and fine tuning - the disk stores the sample clock of every key in a pitch table, from which they follow exactly -, its loop, its filter setting (a three step low-pass, whose cutoff frequencies are the ones EMXP assigns) and its audio, which is expanded from the companded bytes the sampler feeds to its AM6072 DAC. A disk which was made before the multi-sampling software existed holds one sample per half, which fills the half. The sequencer memory on the last track is not read.

When writing, one multi-sample becomes one disk. The zones which cover a half of the keyboard are mapped onto the smallest number of zones the sampler offers (1, 2, 3, 4, 6 or 8, of equal width); each zone of the sampler plays the zone of the source which covers most of its keys, but every single key gets the sample clock which the root key and the tuning of its source zone ask for, so no key plays out of tune where the zone borders do not align. The audio is mixed to mono, re-sampled to 27,778 Hz where the source is faster - a slower source keeps its rate, which the pitch table compensates - and companded. A half holds 57,088 bytes of audio; what does not fit is shortened with a message, and a sample longer than a half is shortened to it. Velocity layers are not supported, the loudest layer of a key is used. The disk needs the operating system of the sampler on its first two tracks to boot; the system is copyrighted E-mu code and not part of this program, therefore it is copied from a system file (*.E1O*, as the EMXP project publishes them) or from any Emulator disk image (*.hfe*, *.emufd*) named in the settings. Without one the system tracks stay empty and the sampler cannot boot from the disk. Written disks have not been verified on hardware yet.

### Destination Options

* Output: Write a HxC floppy emulator image (*.hfe*), which is what a Gotek or HxC drive in the sampler reads, or a raw disk image (*.emufd*) for the EMXP and HxC tools. CLI: `EIOutputFormat=hfe|raw`.
* Operating system: The file from which the two system tracks are taken, optional. CLI: `EIOperatingSystem=<path>`.

## E-mu Emulator II

The E-mu Emulator II (1984) stores a *bank* - up to 100 voices, each a sample with its loop and its settings, and up to 100 presets which assign the voices to the 61 keys - on a double-sided floppy disk of 573,440 bytes, of which the first 22 tracks may hold the operating system and the rest the bank. Like the Emulator, it writes a FM track format of its own, so a disk gets onto a computer only as an image of a HxC/Gotek floppy emulator (*.hfe*) or as a raw sector image (*.emuiifd*, *.img*). The track format was described by the EMXP project; the bank layout was reverse-engineered here from the 90 disks of the factory library and 1,437 community disks and is described in *documentation/design/EMULATOR2_FORMAT.md*.

When reading, every preset of a bank becomes one multi-sample. A preset divides the keyboard (MIDI notes 36-96) into ranges which each play a voice at a transposition, and optionally a second voice, so the first voices become the first group of the multi-sample and the second voices the second group. From each voice the name, the loop (when it is switched on) and the audio are read, the latter expanded from the companded bytes which the sampler feeds to its AM6072 DAC; the root key of a range follows from its transposition. The settings of a voice - filter, envelopes, LFO, level - are located in the bank but not decoded yet and are therefore not converted. A bank which is larger than a floppy disk holds - the Emulator II+ has 1 MB of sample memory - is cut at the end of the disk and reported.

When writing, each multi-sample becomes one preset of the bank, several of them as a library on one disk: the zones of the first group become the voices of the key ranges, the zones of a second group their second voices, and identical audio is stored once. The audio is mixed to mono, re-sampled to the fixed 27,777 Hz of the sampler and companded; the settings of the voices are taken from a voice of the factory library (an unfiltered piano), since they are not decoded. The 494,592 bytes of a disk hold about 17 seconds of audio; a voice which does not fit is shortened or skipped with a message. The operating system is not part of this program; it is copied from a system file (*.E2O*, as the EMXP project publishes them) or from any Emulator II disk image named in the settings. Without one the disk holds the bank alone, which the sampler loads once it has booted from another disk. Written disks have not been verified on hardware yet.

### Destination Options

* Output: Write a HxC floppy emulator image (*.hfe*) or a raw disk image (*.emuiifd*). CLI: `EIIOutputFormat=hfe|raw`.
* Operating system: The file from which the system tracks are taken, optional. CLI: `EIIOperatingSystem=<path>`.
## E-mu Emax / Emax II

The E-mu Emax (1986) is the eight voice successor of the Emulator II, the Emax II (1989) its 16-bit successor. Everything they hold - up to 100 presets, their voices and the audio of all samples - lives in one *bank*, a straight dump of the memory of the sampler. Such a bank is what their floppy disks, their hard disks and the *.EM1* and *.EB2* files of the E-mu sound library contain. The format is not documented by E-mu; it was reverse-engineered from 45 Emax factory banks and the 147 banks of the four Emax II "Elements of Sound" library CD-ROMs, from the Emax owner's manual, from the manual of EMXP (the utility which reads and writes these disks) and, for the audio of the Emax II, from SoundFont conversions of the same CD-ROMs which serve as an external reference (see *documentation/design/EMAX_FORMAT.md*).

Both samplers use the same structures and are read from the same files; which of the two a bank belongs to is read from the bank itself. Banks are read from *.em1*, *.eb1*, *.em2* and *.eb2* files, from raw floppy disk images (*.em1fd*, *.em2fd*, *.img*, 800 KB: 80 cylinders of 10 sectors on two sides), from HxC floppy emulator images (*.hfe*) of such disks and from hard disk and CD-ROM images (*.ez1*, *.ez2*, *.iso*, *.img*), where every bank of the image is converted. A bank is found by its header rather than at a fixed position, so it does not matter whether a file holds the bank alone, an operating system in front of it, or several banks one after the other.

When reading, every preset of a bank becomes one multi-sample. A preset maps each of the 88 keys of the sampler (MIDI notes 21-108) to a key area, and every key area plays a primary voice and optionally a secondary one - the *Dual Voice* of the sampler - so the primary voices become the first group and the secondary voices the second. From each voice the root key, key range, tuning, attenuation, panning, delay, non-transpose flag, its amplitude envelope with the velocity amount on the level, its low-pass filter with cutoff, Q, keyboard tracking, velocity amount and its own envelope, and the LFO with its rate, delay and its depth on the pitch (vibrato) and on the level (tremolo) are read; from its sample come the loop and the sample rate. The audio of the Emax is expanded from the companded bytes which the sampler feeds to its AM6072 DAC, the same 8-bit-in, 13-bit-out transfer function the Emulator II uses; the audio of the Emax II is 16 bit and is taken as it is.

Note that a bank does not say how many presets it holds and may keep one in any of its 100 slots - the library banks put their sequence demos into the last ones - so every slot is examined. Library banks which were edited on the sampler also contain preset slots whose record was overwritten; those are detected and skipped with a note instead of producing broken multi-samples.

The Emax packs the 48 parameters of a voice into 32 bytes as a bit stream. That packing was resolved by aligning 37,965 voices of the Emax II library CD-ROMs with SoundFont files which EMXP produced from the same banks, so the physical value behind each setting - envelope times in seconds, cutoff frequencies in Hertz, attenuation in decibels - is the one that tool decided on; where the Emax manual gives a range, the two agree exactly. Three parameters are not converted: the LFO variation, which randomises the LFO rate per key, and the two output channel assignments of a voice.

When writing, each source becomes one preset and several sources can be collected into one bank as a library (at most 100 presets). Samples are mixed down to mono - the samplers play one channel per voice and place it in the stereo field with its panning - are encoded the way the target sampler stores them and are de-duplicated by their content, so audio which several zones share is only stored once. Each sample keeps its own sample rate, which the format supports. Every parameter which is read is written back as well, so a bank survives a conversion to and from this format unchanged. The handful of parameters which are not decoded are taken from a template which is the most frequent setting of the factory library, so that a written bank plays with the defaults of the sampler instead of with zeroes. An Emax bank is written as *.em1* with the whole 512 KB of its sample memory, an Emax II bank as *.eb2* with only the audio which is in use, which is the layout EMXP describes for the hard disks and CD-ROMs of the sampler.

Note that the sample memory is small - 512 KB on the Emax, 2 MB on the Emax II - and the parameter memory only 28 KB, which a converted library exceeds quickly; what does not fit is skipped with an error naming the preset or zone. Note also that written banks have not been verified on hardware yet - they round-trip through the reader byte for byte and match the structure of the E-mu library banks.

#### Destination Options

* Target device: Whether to write a bank for the *Emax* (512 KB of companded 8-bit audio) or for the *Emax II* (2 MB of 16-bit audio).
* Sample rate: The rate at which the samples are stored. Both samplers support 10000, 15625, 20000, 22050, 27778, 31250, 41667 and 44100 Hz, and the rate decides both how much audio fits into the sample memory and how far the sampler can transpose a sample upwards (25 semitones at 10000 Hz down to none at 44100 Hz). *Automatic* (the default) keeps each sample at the highest rate which is not above the rate of its source audio, so nothing is ever up-sampled. A source whose zones need more upward transposition than its rate allows is reported, since choosing a lower rate is the way to fix that.

## E-mu Emulator III/IIIX/ESI

The E-mu Emulator III (1987) and its successors - the Emulator IIIX and the ESI-32, ESI-2000 and ESI-4000 - store a sound as a *bank*, a single file which contains both the presets and their 16-bit sample data. The samplers keep such banks on their own disk format, so the files usually carry no extension; when they are written to a DOS/FAT disk the E-mu tools name them *.e3b* (Emulator III), *.e3x* (Emulator IIIX) and *.esi* (ESI series). All three variants use the same structures and differ only in where their address tables sit and how many objects they hold (100 presets and 99 samples for the Emulator III, 256 and 999 for the later ones). The layout was derived from the source code of the GPL tool emu3bm by David García Goñi and verified against the EIIIX and ESI-4000 library CD-ROMs (see *documentation/design/EIII_FORMAT.md*).

Banks are also read directly from CD-ROM and hard disk images of the samplers (*.iso*, *.img*, *.hda*), which use the proprietary E-mu disk filesystem; every bank found in such an image is converted.

Banks which the samplers saved onto floppy disks (*.img*, raw 1.44 MB images) are read as well. A bank which did not fit onto one disk spans a set of disks; the set is assembled automatically when its first disk is read and the other disks are skipped. All disks of a set need to be in the same folder - they are recognized by the bank name in their disk headers, not by their file names, since the collections in the wild number their files in different ways. Floppy sets of the original Emulator III are not supported (only IIIX and ESI sets were available for verification).

When reading, each preset becomes one multi-sample. A preset maps each of the 88 keys of the sampler (MIDI notes 21-108) to a note zone, and every note zone references up to two zones - the primary and the secondary layer - which become the groups of the multi-sample; the velocity ranges the preset assigns to its two layers are applied to them. Presets can be chained with the preset link, which is how the samplers build more than two velocity layers; such a chain is collected into a single multi-sample so that its layers stay together instead of appearing as separate presets. From each zone the root key, key range, tuning, level, panorama, amplitude envelope, velocity-to-level amount, filter (cutoff, resonance, key tracking and filter envelope) and the auxiliary envelope (when it modulates the pitch) are read, as well as the loop of its sample - forward and forward/backward ('fwd/bkwd') loops, the latter becoming alternating loops - and its reverse playback flag. Only the ESI samplers store a filter type; for the Emulator III and IIIX the single low-pass filter of the device is used. Filters which have no counterpart in other formats (phasers, flangers, vocal formant filters and the swept EQs of the ESI) are dropped. Banks whose presets were partly deleted are handled: an empty preset or sample slot is skipped instead of ending the search, so the objects behind it are still converted.

When writing, all groups of a source become presets which are chained with the preset link, one preset per group, and several sources can be collected into one bank as a library. The samples are stored in the bank as 16-bit mono or stereo and are de-duplicated by their content, so a sample which several zones share is only written once. The first and last two frames of every sample are silenced and loop positions are moved away from the sample ends, both of which the samplers require. Note that the key range of the devices is limited to MIDI notes 21-108 and that a bank cannot exceed the 128 MB sample memory of the samplers.

Banks can also be written as a CD-ROM image (*.iso*) with the proprietary E-mu disk filesystem, which is how a converted library reaches a sampler of this generation: these units read neither FAT hard disks nor any other file system of a computer, so their own filesystem is the only way in. The image holds one bank per source instead of merging all of them into one bank - a bank is loaded into the memory of the sampler as a whole, which even fully expanded is a fraction of a converted library. Its geometry, its file entries and its directory copy the Emulator IIIX library CD-ROMs, up to the number of banks a folder holds (112).

Note: written banks have not been verified on hardware yet. They round-trip through the reader and match the structure of the E-mu library CD-ROMs.

#### Source Options

* Prepend the bank name to the preset name: The presets of the E-mu libraries are often named after their articulation or variation ('Dark Tremolo') while the instrument they play is only given by the name of their bank. If enabled (the default), the bank name is put in front of the preset name unless the preset name already starts with it.

#### Destination Options

* Target Device: Selects the bank format to write: *Emulator IIIX (e3x)* or *ESI-32/2000/4000 (esi)*. The Emulator IIIX format is the more compatible one since the ESI samplers read it as well; the ESI format additionally enables the real-time control of the filter Q, which is what the ESI factory banks do.
* Create CD-ROM image (.iso) for SCSI CD-ROM emulators: The banks are written into a CD-ROM image (*.iso*) with the proprietary E-mu disk filesystem instead of into plain bank files. Copy the image to the SD card of a SCSI emulator (e.g. rename it to *CD1.iso* for a ZuluSCSI in CD-ROM mode) and load the banks on the sampler from the emulated CD-ROM drive, exactly like from a library CD-ROM. Each converted source becomes one bank of the image (at most 112) and the library name becomes the name of its folder; without a library name every source becomes an image of its own.

## E-mu Emulator IV

The E-mu Emulator IV series (Emulator 4, E4X, E4XT, E4K, e-Synth, e-6400 and the other EOS samplers, 1994-2002) stores its banks in single *.e4b* files which contain all presets, their parameters and the sample data. A preset layers several voices; each voice maps a set of zones (a key/velocity range referencing a sample) and carries the tuning, volume, filter, envelope and modulation settings for them. The format is not documented by E-mu, the layout was reverse-engineered by the mpc2emu project from hardware-saved E4XT banks and commercial EOS CD-ROMs (see *documentation/design/E4B_FORMAT.md*).

Banks can be read from single *.e4b* files and also directly from CD-ROM and hard disk images of the EOS samplers (*.iso*, *.img*, *.hda* - e.g. images for SCSI emulators like the ZuluSCSI or dumps of commercial E-mu CD-ROMs), which use the proprietary E-mu disk filesystem. All banks of an image are read; files of the older EIII samplers, which use the same filesystem for their banks, are skipped.

When reading, every preset of a bank becomes one multi-sample and every voice becomes a group. Names, key and velocity ranges, root keys, tuning, volume, panning, loops, the amplitude envelope with its velocity modulation, the filter (type, cutoff, resonance, key tracking and envelope with its depth) and the 16-bit sample data are read. The per-zone offsets for fine tuning, volume and panning are applied on top of the settings of their voice, and the pitch offset of a sample is applied as a fine tuning as far as it detunes the sample beyond compensating its sample rate. The EOS effect filter types (phasers, flangers, vocal formants, EQ morphs) have no model equivalent, such voices are converted without a filter.

The name of the bank is passed on as the description of the multi-sample, which the formats that have a field of their own for the bank take it from - the Waldorf Quantum/Iridium for example shows it next to the preset name.

The presets of the commercial EOS libraries are named after the articulation or the variation they provide (*Dark Tremolo*, *Long Release*, ...) while the instrument they actually play is only given by the name of their bank. The name of the bank is therefore prepended to the name of the preset as well, except when the preset name already starts with it - the *Dark Tremolo* preset of the *Greek Bazouki* bank becomes *Greek Bazouki - Dark Tremolo* while *Greek Bazouki XS* is kept unchanged. This also keeps the presets of different banks apart, which would otherwise overwrite each other since a name like *Natural Range* is used in many banks. If the folder structure of the source is created, each bank additionally gets a folder of its own.

A written bank does not repeat this in its preset names, since the file itself is that bank and its preset names hold only 16 characters - which the bank alone would fill. The file name of the bank keeps the full name.

#### Source Options

* Prepend the bank name to the preset name: Enabled by default, see above. Disable it to keep the preset names exactly as they are in the bank - the bank is then only carried in the description, and the written file names are the bare preset names.

When writing, each multi-sample becomes one preset; a library collects all multi-samples into a single bank (up to 1000 presets and 1000 samples). Every zone is written as its own voice, which keeps all per-zone settings; the panning, which only exists per zone, is written into the zone entry. Samples are stored as 16-bit mono PCM with their original sample rate (rates above 48kHz, the EOS maximum, are down-sampled); stereo samples are mixed down to mono. Identical samples mapped to multiple zones are stored only once. Since EOS only has a sample-level forward loop, alternating loops are written as forward loops and only the first loop of a zone is kept. Written banks validate against the reference parser of the mpc2emu project but have not been verified on real hardware yet.

#### Destination Options

* Create CD-ROM image (.iso) for SCSI CD-ROM emulators: The bank is wrapped into a CD-ROM image (*.iso*) with the proprietary E-mu disk filesystem. Copy the image to the SD card of a SCSI emulator (e.g. rename it to *CD1.iso* for a ZuluSCSI in CD-ROM mode) and load the bank on the sampler from the emulated CD-ROM drive. This works on all EOS versions and on units which cannot read FAT hard disks (EOS before 4.7); a plain bank file instead requires a FAT formatted hard disk and EOS 4.7 or later.

## E-mu Emulator X

The Emulator X (2004), Emulator X2 (2005) and Emulator X3 (2008) were the software samplers with which E-mu moved its Emulator line from hardware to the PC. They kept the Z-plane filters and the modulation cord architecture of the EOS hardware samplers and were sold both on their own and bundled with E-mu sound cards, together with a large library of sound banks - many of which are conversions of the classic E-mu ROM modules like Vintage Keys, PROcussion and the SP-1200 library.

A sound bank consists of a *.exb* file, which holds all presets of the bank, and a folder named *SamplePool* next to it, which holds one *.ebl* file per sample. The two are linked by the file names: a sample file is called *&lt;bank name&gt;SL&lt;index&gt;.ebl*, e.g. *PROcussionSL018.ebl*. Keep the bank and its sample pool together, otherwise the samples cannot be found.

A preset is a list of voices. Every voice has a key range and a velocity range (both with crossfades), references one or more samples through its zones and carries its own tuning, volume, panning, filter and envelopes. A voice usually maps exactly one sample, so a keyboard map is built from many voices, while velocity layers are built inside the voices. ConvertWithMoss collects all zones which cover the same velocity range into one group.

The file format is not documented by E-mu, the layout was reverse-engineered from 14 banks; the meaning and the ranges of the parameters come from the Emulator X and Emulator X3 reference manuals (see *documentation/design/EMULATORX_FORMAT.md*). Reading and writing are supported. Written sample files are byte-identical to the ones the Emulator X writes, but the banks themselves have not been verified with the Emulator X yet.

### Issues and Workarounds

1. Only the parameters which have a counterpart in the model are converted: key and velocity ranges with their crossfades, the original key, tuning, volume, panning, the loop, the amplitude envelope, the velocity to volume modulation, the filter type and cutoff, the filter envelope and the category of the preset. The LFOs, function generators, arpeggiator and the remaining modulation cords are written with the defaults of the factory banks.
2. The chunks of the format are versioned and several generations exist. All of them can be read; sample files are always written in the newer of the two layouts, which is what the Emulator X3 uses.
3. Of the 55 filter types, only the low-pass (2, 4 and 6 poles), high-pass (2 and 4), band-pass (2 and 4) and the contrary band-pass, which is the closest the format has to a notch, have a model equivalent; the swept EQs, phasers, flangers, vowel formant filters, distortions and morph filters are read as 'no filter'. A wide open low-pass, which is how the factory banks express an unfiltered voice, is read as 'no filter' as well and is written back as the 'No Filter' type. The filter resonance is not stored by the format.
4. Many presets park the filter cutoff at the bottom and open it again with a modulation cord. Two cases are converted: an assignable MIDI controller, whose initial value the preset stores and which therefore gives the cutoff the voice actually starts at, and the filter envelope. If the cutoff is parked but is opened by a modulation source which cannot be converted, no filter is written at all, because a filter at 57 Hz without its modulation would turn the voice into silence.
5. The E-mu factory banks set the volume of every voice to the maximum of +10 dB, so converting them applies that gain. Reduce it with the processing options if the result clips.
6. Sample files (*.ebl*) can also be read on their own, which gives one multi-sample per sample and is useful for a sample pool without its bank. Sample files which belong to a bank in the same folder are skipped, because they are already read through that bank.
7. The category which the sound designer gave a preset ('Synthesizer', 'Keyboard - GM') is read and written. Since the model has a fixed set of categories, the category is mapped onto the closest one of those, so a bank written back from an Emulator X source carries the mapped name and not the original wording.
8. A written bank is not byte-identical to the bank it was read from, even though its audio data is: two per-sample fields are not reproduced. One is a value which is a constant in some banks but differs per sample in others, where it looks like a check sum; the other is a comment which names the sound set a sample was originally taken from. Neither can be derived from the converted data. The sample names themselves are kept.

## Ensoniq EPS/EPS16+/ASR-10

The Ensoniq EPS, Ensoniq EPS16+, and Ensoniq ASR-10 were influential hardware samplers from the late 1980s and 1990s, known for their distinctive sound, practical workflow, and integrated sequencing features. The EPS made professional sampling more accessible, while the EPS16+ added effects processing and stereo audio routing. The ASR-10 extended the series with greater processing power, improved editing, and expanded performance capabilities.

The file format is more or less identical on the 3 models with the additions of the added parameters of the later models (all files can be exchanged between the models). However, there are plenty of different storage formats which contain the actual instrument files:

* HFE, GKH, EDE, EDA: These formats contain the data of a full diskette with some additional metadata.
* IMG: The raw data of a diskette without any addition information.
* ISO: FAT file system which can contain many EPS/ASR files.
* EFE: This format contains exactly 1 EPS/ASR file with additional metadata.

ConvertWithMoss can read them all but writing is not supported.

## Ensoniq Mirage

The Ensoniq Mirage, introduced in 1984, was a groundbreaking 8-bit digital sampler that democratized sampling technology for musicians. Priced at around $1,695, it was one of the first affordable samplers on the market—a fraction of the cost of competitors like the Fairlight CMI or E-mu Emulator. The Mirage featured 8-voice polyphony, a small 2-digit LED display, and used 3.5" floppy disks for storing samples and sounds. Despite its limited memory (just 128KB) and lo-fi character, it became hugely popular in the mid-1980s and found its way onto countless recordings across pop, hip-hop, and electronic music.The Mirage was available in both keyboard (DSK-1, DSK-8) and rack-mount (DSM-1) versions.

Its open architecture was fairly unusual for hardware of that era and gave the Mirage an active user community of developers and enthusiasts who continued to push the instrument's boundaries well beyond what Ensoniq originally intended which led to several alternative operating systems. One of them is Triton Soundprocess, it uses its own filesystem which is not supported (if someone has any knowledge about it, please get in touch).

This disk format is proprietary with a complex layout. Each disk contains 3 sounds. Each sound consists of a lower and upper layer. Each layer can have up to 8 samples. Each layer has 4 programs with different parameter settings. The programs of the lower and upper layer can be selected differently which gives 4x4=16 different configurations! To make things confusing these sounds are interleaved with OS and sequence data on the disk. ConvertWithMoss can read such disk files (*.hfe, *.img or *.edm).

### Issues and Workarounds

1. Since the format does not provide any naming, the name of the files are used. If a file contains exactly 2 dashes the name is split into 3 parts and they are used for the 3 sounds. E.g. label a file like 'Name1-Name2-Name3.img' to get a proper names for the multi-samples.
2. To keep things manageable only the matching programs are exported (e.g. Program 1 from the lower layer with Program 1 from the upper layer). This means each disk will result to 3x4=12 multi-samples.
3. Another issue is that the format does not store the root note of the samples. The pitch is only determined by the sample-rate and the tuning. The current sample-rate is extracted from the disk as well but in theory it could be totally wrong.
4. The filter cutoff value is not fully understood. Therefore, settings which produce no sound are ignored and noi filter is set in such a case.
5. There can be some very short loop lengths (like 256 samples) which might cause a playback issue with some multi-sample players.
6. It uses quite uncommon sample rate which might cause a playback issue with some multi-sample players.

## Expert Sleepers disting EX

The disting EX is a multi-function Eurorack module which provides many different algorithms. On of them is the SD Multisample algorithm which is an eight voice polyphonic, three part multi-timbral, sample playback instrument, playing WAV files from the MicroSD card. It can have up to 3 input CV/gate pairs, or can be played via MIDI or I2C. It supports both velocity switches and round robins per sample.
The basic multi-sample setup is encoded in the file-names of the samples. Further information like the amplitude envelope are stored in a preset (*.dexpreset). The preset references only the name of the folder which contains the related samples. All samples in the folder considered to be belonging to the multi-sample.

### Destination Options

* Re-sample to 16bit/44.1kHz: If enabled, samples will be resampled to 16bit and 44.1kHz. While the device can play higher resolutions as well it decrease the number of voices it can play.
* Trim sample to range of zone start to end: Since the format does not support a sample start attribute, this fixes the issue.

## Fairlight CMI Voice

The Fairlight CMI (**C**omputer **M**usical **I**nstrument) was an advanced digital synthesizer, sampler, and music workstation, often described as an "orchestra in a box." Series III, introduced in 1985, featured 16-bit sampling with rates up to 100 kHz (mono) / 50 kHz (stereo), for the time extensive memory (14-64MB), and sophisticated sequencing systems like CAPS (**C**omposer, **A**rranger, **P**erformer **S**equencer). This model improved upon its predecessors with better sound quality, MIDI compatibility, and user-friendly editing tools.

Voice Files (*.VC) store individual instrument subvoices (samples) and synthesis data. The file is split into headers/control parameters followed by raw linear 8-bit audio samples (or 16-bit for Series III). Fairlight CMI IIx used variable rates from 2.1 kHz to 32 kHz (default 14.08 kHz). Series III expanded up to 100 kHz at 16 bits. Early CMI memory was limited (e.g., 16KB per channel).

ConvertWithMoss can read/write 3 versions of the format: 

* the 16-bit Series III files with all of their sub-voices (key ranges, loops, tuning, gain and the amplitude envelope) and 
* the fixed-size 8-bit files of the CMI I/II/IIx (16384 samples with their loop segments), as well as 
* the QasarBeach recreation of the IIx.
 
When the control (CO) file referenced by an 8-bit voice is present next to it, its parameters win (as on the CMI itself): the loop, the attack and the damping, which becomes the release of the amplitude envelope. Voices saved by QasarBeach itself (its own 16-bit format marked with a 'QBV2' tag) are read as well, with their name and loop. The 8-bit dialect stores no sample rate: the CMI II reads one 128-sample segment per waveform period, so a voice plays at its original pitch on the key with the frequency of its sampling rate divided by 128. Such a voice is therefore read with the documented default rate of the IIx (14080 Hz) and its root on the matching 110 Hz A below the middle C.

Additionally, IMD, IMG and HFE files which contain several VC files can be read.

### Destination Options

* Target Format:
  * Series III: One voice file per multi-sample. Each sample zone becomes a sub-voice with its key range in the 128-key mapping table, with 16-bit mono or stereo audio, loop, tuning, gain and amplitude envelope. Since the format has no velocity dimension, the loudest velocity layer wins where zones overlap. Up to 127 sub-voices fit into a file.
  * IIx with CO control file: One fixed-size 8-bit voice file per sample zone, as read by the CMI itself and by the QasarBeach recreation. Note that the Arturia CMI V cannot load these files: it imports presets and banks only in its own *.cmix* format and its sample slots only take ordinary audio files. The audio is mixed to mono, re-sampled to the root frequency of the zone times 128 - which makes the voice play at its original pitch on its root key (verified against QasarBeach) - and cut to the 16384 samples of a voice. The loop is stored in segments of 128 samples. Each voice comes with the control (CO) file it references, which carries the loop, the attack and damping (release) of the amplitude envelope and the level - the CMI takes the control parameters from this file, therefore both files have to stay together. QasarBeach does not: two voices which differ only in the attack, damping and level of their control file play identically there, so for QasarBeach the native format below is the better target. Note that the fixed voice length limits a voice to 16384 samples, e.g. half a second at the rate of a middle C root.

  * QasarBeach (QBV2): One voice file per sample zone in the native 16-bit format of the QasarBeach recreation, which carries the loop, the release (damping) and the level inside the file - a voice loads into QasarBeach ready to play. The unknown parts of the format are filled from a template captured from a QasarBeach save; the time law of the damping is measured only roughly yet.

Written files have not been tested on real Fairlight hardware yet.

## FL Studio DirectWave

DirectWave is the software sampler of Image-Line's FL Studio; FL Studio Mobile plays its programs as well. A program is stored in a DWP file, a bank of programs in a DWB file. When a program is saved with the *Monolithic file* option disabled, the samples are stored as WAV files in a folder named like the program.

As a source, DWP programs are read from their binary structure (see DIRECTWAVE_DWP_FORMAT.md in the design documentation): name, key/velocity ranges, root, gain, panning, loop (all five loop modes including one-shot and loop-until-release), amplitude envelope, filter, pitch LFO (vibrato) and volume LFO (tremolo) of each zone (the sample chunks of the WAV files are used as the loop fall-back). The cutoff law of the filter was measured by listening (45 Hz to 20 kHz, exponential); the time law of the envelope knobs was measured the same way. The samples are searched next to the DWP file and in the sub-folder named like the program. Monolithic programs, which carry their samples inside the DWP file (FLAC compressed or as uncompressed PCM, which is what older versions of DirectWave wrote), are read as well. For files without the known binary structure (e.g. DWB banks) the mapping is reconstructed from the names of the sample files, which supports both the names written when sampling a plugin (e.g. *Piano 4xCycles_C5_100_2.wav*, round-robin cycles become groups) and the Automap tokens documented in the DirectWave manual (e.g. *sample_C4+KA3-F#4+V64-95.wav*). Note that DirectWave note names follow the FL Studio convention where the middle C (MIDI note 60) is called C5.

As a destination, one monolithic DWP file is written per instrument, i.e. a single self-contained file which carries all its samples as FLAC compressed audio. FL Studio Desktop loads it directly and for FL Studio Mobile it only needs to be copied into its user files. The DWP structure carries name, key/velocity ranges, root, gain, panning, loop, amplitude envelope, a low-pass/high-pass/band-pass/notch filter and the two zone LFOs (LFO 1 modulates the pitch, LFO 2 the volume) per zone. Only the first round-robin cycle is kept since trigger groups cannot be written.

To import a DWP file into FL Studio Mobile, copy it to the iOS device and open it with FL Studio Mobile. This will copy the file to the user instrument folder.

## ISLA S2400

The ISLA Instruments S2400 is a sampling drum machine. It stores a set of pads as a *kit*: a folder which contains a kit file (ending *.kit*) named after the folder plus one WAV file per pad. A project (an *.S24 file with its kit) uses the same kit file for its sounds. This converter handles the kit, which is where the samples and their settings live; the sequencer data of a project is not read or written.

As a source, each pad (track) of the kit becomes one zone. The pads are mapped to consecutive MIDI notes starting at note 36 for pad A1, which matches the default sample track MIDI map of the device. Each zone carries the name of its WAV file, the base gain in dB, the fine tuning in cents, the choke group, the filter (low-pass, band-pass or high-pass with its cutoff in Hertz and its resonance) and the volume envelope. A HiFi envelope stores its attack, hold, decay, sustain and release; since the device expresses the envelope times as a percentage of the sample length they are converted back to seconds using the length of the sample. A classic envelope is approximated by its release stage. The loop points are read from the *smpl* chunk of the WAV file, which is where the device stores them.

As a destination, one kit folder is written per multi-sample: the kit file named after the folder and one WAV file per zone (up to the 32 pads of the device, additional zones are ignored). The samples are written in the device native 48 kHz / 16-bit format - a sample above 48 kHz is down-sampled, a sample at 44.1 kHz is kept since the device supports it - and their loop is stored in the *smpl* chunk so that the device picks it up.

The kit file format was reverse-engineered from the official S2400 Kit Builder web application, since the device firmware is encrypted. The read and written kits have not yet been verified on real hardware.

## ISO/IMG Files

Searches for files ending with *.ISO, *.IMG or *.HDA. Currently, the following formats can be handled:

* [Akai MPC2000/MPC2000XL](#akai-mpc2000mpc2000xlmpc3000)
* [Akai S1000/3000](#akai-s1000s3000-series-disk-image)
* [E-mu Emulator](#e-mu-emulator) (only *.IMG)
* [E-mu Emulator II](#e-mu-emulator-ii) (only *.IMG)
* [E-mu Emulator III](#e-mu-emulator-iiiiiixesi)
* [E-mu Emulator IV](#e-mu-emulator-iv)
* [Ensoniq EPS/ASR](#ensoniq-epseps16asr-10) (only *.ISO)
* [Roland S-50 series](#roland-s-50-series)
* [Roland S-770 series](#roland-s-770-series)

## Korg KSC/KMP/KSF

The KSC/KMP/KSF format (*.KSC, *.KMP, *.KSF) was first introduced in the Korg Trinity workstation (1995) and since then supported in many Korg workstations and entertainment keyboards up to the latest Korg Nautilus (2020). The following devices are known to support the format:

* Trinity
* Triton
* OASYS
* M3
* Kronos
* KROSS (only for pads)
* PA1X/PA800/PA2X/PA3X/PA4X
* Nautilus

The format is documented in detail (more or less) in the appendix of the respective parameter guides. A multi-sample is distributed across 4 types of files which makes the handling a bit tricky:

* KSF: One KSF file contains one single mono sample. Even if the KSF files can store stereo files, they do not work. Therefore, they need to be split into 2 KSF files. The format only supports uncompressed 8 or 16 bit samples up to 48kHz. Files in other formats are automatically converted.
* KMP: The KMP format contains 1 layer of a multi-sample, which means there are only key splits but no groups and no velocity settings. The file references several KSF files which contain the sample data for each key region.
* KSC: A KSC contains a list of KMP files (and sometimes other files) which allows to load them in one go. It contains no other additional information.
* PCG: This contains a full program which combines several KMPs into a complete multi-sample (stereo positioning and velocity layers). Since these are different for each workstation and not publicly documented, they are currently not supported.

### PA series

The .PCM format belongs to the KORG Family of PA Models. It is used in combination with a KMP file which has a different format as the KMP/KSF combination. **These formats are not supported.** 

### Source Options

* Use KSC files as the input: By default ConvertWithMoss searches for KPM files. If enabled, KSC files are searched and the KMP files referenced in a KSC file are loaded (if found). Furthermore, it tries to combine stereo-split files into stereo files by the prefixes of the KMP names and the long names stored in the KSF files (they normally end with -L for the left and -R for the right channel).

### Destination Options

* Enable the +12dB option: Increases the volume of each sample by +12dB. Use for low volume samples.
* Set sample volume to +99: If enabled, sets all sample volumes to +99. Use for very low volume samples.
* Shorten the name for the device display: Keeps only the last separated part of the name (after " - ", " / ", " : " or " | "), e.g. *Greek Bazouki - Dark Tremolo* becomes *Dark Tremolo*. The multi-sample name of this format holds only 24 characters, so a source whose name is qualified with the bank it comes from otherwise loses exactly the part which tells its presets apart. The file and folder names are not affected. The dropped part is not written anywhere else, therefore this is disabled by default. CLI: `KMPShortenName=1`.

## Korg wavestate/modwave

The korgmultisample format is currently used by the Korg wavestate and modwave keyboards as well as their VST plugin siblings. Files in that format (*.korgmultisample) can be opened with the Korg Sample Builder software and transferred to the keyboard.

Since the format is pretty simple all data stored in the file is available for the conversion.

Since the format supports only one group of a multi-sample, multiple destination files are created for each group available in the source. If there is more than one group in the source the name of the created file has the velocity range of the group added. Using that information a multi-sample with up to 4 groups can be created as a Performance in the device.

## Kurzweil K2000/K2500/K2600

The Kurzweil K2000 (1991), K2500 and K2600 samplers/synthesizers share one object file format with the extensions *.krz*, *.k25* and *.k26*. A file is a bank of numbered objects: programs (presets), keymaps (the mapping of sample recordings across the keys and the 8 dynamic velocity levels) and samples, which may contain several recordings (multiple root keys, stereo pairs). The layout was derived from the source code of the GPL tool KurzFiler by Marc Halbruegge (see *documentation/design/KURZWEIL_FORMAT.md*).

When reading, each program becomes one multi-sample: its layers reference keymaps from which the key ranges, velocity levels, root keys, tunings, volume adjusts, loops and the 16-bit sample data are read. From the program layers the key and velocity windows, the amplitude envelope (unless the layer plays the 'natural' envelope of its samples), the filter (type, number of poles, cutoff, resonance) and the filter envelope or velocity modulation of the cutoff with its depth are read as well. Keymaps and samples which are not referenced by any program are converted as multi-samples of their own. Many factory and commercial K-series files map samples from the device ROM which is not present in the file; such zones cannot be converted and are skipped with a note.

When writing, a file is created which uses only K2000 features and therefore loads on all three device families (the current Kurzweil range - K2700, PC4, Forte - imports these files as well). Each multi-sample becomes a program with one layer and a keymap; the velocity layers of the source are quantized onto the 8 dynamic levels of the keymap. The layer carries the global amplitude envelope of the multi-sample as well as the global filter, which is mapped onto the nearest filter of the device (1/2/4-pole low-pass, 2/4-pole bandpass, high-pass, notch) together with its cutoff, resonance and filter envelope or cutoff velocity modulation (the device has one modulation slot on the filter page; the envelope has priority). One sample object is written per zone (16-bit; the sample rate is kept up to 96kHz, the maximum sample playback rate of the devices) with the zone gain in its volume adjust. Since the device plays a loop until the end of the sample, the data after the loop end is cut off. If any zone is stereo, all samples of the program are written as stereo pairs. The keymap covers MIDI notes 12-127 (C0-G9 in Kurzweil terms), keys below are dropped. Several multi-samples can be written into one file as a library; if the object IDs (200-999 per type) do not suffice, multiple files are created.

#### Destination Options

* Target Device: Selects the device family for which the file is named: *K2000 (krz)*, *K2500 (k25)* or *K2600 (k26)*. Since the written objects use only K2000 features, the selection only sets the file extension.
* Shorten the name for the device display: Keeps only the last separated part of the name (after " - ", " / ", " : " or " | "), e.g. *Greek Bazouki - Dark Tremolo* becomes *Dark Tremolo*. The name field of this format holds only 16 characters, so a source whose name is qualified with the bank it comes from otherwise loses exactly the part which tells its presets apart. The dropped part is not written anywhere else, therefore this is disabled by default. CLI: `KurzweilShortenName=1`.

## Logic EXS24

The Logic EXS24 format is a proprietary sample format used by Logic Pro, a digital audio workstation. It is primarily used for storing and playback of sampled instruments and sounds within Logic Pro. The format allows for comprehensive mapping and editing of samples, as well as providing various modulation and performance options.

The format only stores absolute paths to the sample files. Therefore, the easiest way to make the converter find the sample files is to place them in the same folder as the EXS file. If it cannot be found in this folder the sample file is searched recursively starting from a number of levels up from the source folder of the EXS. *The number of folders can be configured*.

## Native Instruments Kontakt

Kontakt is a sampler from Native Instruments which uses a plethora of file formats which all are sadly proprietary and therefore no documentation is publicly available. Nevertheless, several people analyzed the format and by now sufficient information is available to provide the support as the source.

However, the format changed many times across the different Kontakt versions. So far, the following formats (NKI/NKM) are known and supported as a source:

| Kontakt Version |
| :-------------- |
| 1               |
| 1.5             |
| 2 - 4.1.x       |
| 4.2.2+          |
| 5 - 7           |

A NKI file contains one instrument which is a multi-sample with many parameters. Currently, the usual multi-sample parameters are supported incl. loops. Furthermore, metadata information, the amplitude, pitch and filter cutoff envelope, filter parameters as well as pitchbend.
(Most) NCW encoded sample files can be read as well.
A NKM file contains up to 64 instruments and is supported as well as a source.

The velocity to volume modulator is read with its intensity and its response curve: Kontakt maps its normalized volume value to decibels with *60 · log10(x)*, so the amplitude follows the cube of the velocity. Destination formats which can express the curve reproduce this response, e.g. SFZ writes *amp_velcurve_N* points (see the SFZ section).

Encrypted files are not supported.

If selected as a destination, a NKI file is written and all samples are placed in a sub-folder with the same name.

If selected as a source and 'Performance' is selected as the destination type, only NKM files are used as sources.

### Destination Options

* Output Format: Currently, only the Kontakt 1 format is supported which sadly does not contain any metadata information.

## Native Instruments Maschine

### MSND

MSND is a binary format for Maschine 1. It got dropped completely in later versions and it cannot even be opened in Maschine 2/3. You can use ConvertWithMoss to convert it e.g. into Maschine 2 or 3 format (MXSND).

### MXSND

The MXSND format is a proprietary binary format used by Maschine 2 and 3. MXSND uses the same container wrapper format as Kontakt 5+. Maschine 1 used a different format with the ending MSND which is currently not supported.

Only MXSND files which contain an instance of a Maschine Sampler can be read. The Maschine Sampler supports basic features but has e.g. no groups or release layers (see the detailed parameter documentation).

Note that Maschine contains an auto-sampler with which you can sample plugins or external synths and writes MXSND as the output. This means that you can then convert it to other formats with ConvertWithMoss.

### Source Options

* Scan for Maschine 1 MSND files: Scans the source folder for files ending with *.msnd (Maschine 1 format) as well. If the source is a library which contains both version, deactivate this option to prevent duplicates.

### Destination Options

* Output Format: Select the Maschine output format. Selecting **Maschine 1** will create a MSND file, otherwise a MXSND file is created.

## Polyend Tracker

The Polyend Tracker (and the Tracker Mini / Tracker+) is a standalone hardware sampler, sequencer and tracker. Its instrument format (file ending *pti*) is a single binary file which holds exactly one 16-bit / 44.1kHz PCM sample (mono or stereo) together with the instrument parameters. Both reading and writing are supported.

When reading, the play mode, the playback start/end and loop points, the loop mode (forward / backward / ping-pong), the filter (low-, high- and band-pass with cutoff, resonance and the cutoff envelope), the amplitude and pitch envelopes as well as the volume, panning and tuning are converted. Instruments in one of the slice play modes are split into one sample zone per slice; each slice is trimmed to its own audio and mapped chromatically to a single key starting at MIDI note 60 (middle C).

When writing, the play mode (one-shot or one of the loop modes), the start/end and loop points, the filter, the amplitude/cutoff/pitch envelopes as well as the volume, panning and tuning are stored. The audio is converted to 16-bit / 44.1kHz; mono or stereo is preserved.

### Limitations

* A Polyend Tracker instrument holds only a single sample. When converting a multi-sample, the zone whose key range covers MIDI note 60 (middle C) is stored - otherwise the first zone - and all other zones are ignored (a note is logged).
* The wavetable and granular play modes are read as a plain one-shot sample. The wavetable/granular specific parameters, the LFOs and the delay/reverb sends and overdrive are not converted.
* The playback start/end, loop and slice positions are stored proportionally to the sample length (0 to 65535). Loop points can therefore differ by a tiny fraction of the sample length after a round-trip.
* The filter cutoff frequency mapping is an approximation since the Tracker's normalized cutoff to frequency curve is internal and not part of the format.
* This format has **not** been verified on physical Polyend Tracker hardware. It was validated against the official *tracker-lib* reference implementation and round-trip conversions.

## Propellerhead Reason NN-XT

The Propellerhead Reason NN-XT is a software sampler that is included in the Reason software package. Reason is a digital audio workstation (DAW) software developed by Propellerhead Software. It allows users to load and play back sampled sounds, such as instruments or drum hits. The file ending is *sxt*.

There are metadata fields for creator and a creator URL.

## Renoise

Renoise is a tracker-based digital audio workstation. Its instrument format (file ending *xrni*) is a ZIP archive which contains an *Instrument.xml* description file and all samples (in FLAC format) in a *SampleData* folder. Both reading and writing are supported. Files saved by Renoise 3.0 up to 3.5 (document version 24 - 34) can be read; created files use document version 33 (Renoise 3.3) so they load in newer Renoise versions as well as in the Renoise Redux plug-in.

The converter maps the key and velocity ranges, root note, tuning (transpose + fine-tune), volume, panning and the loop (off / forward / backward / ping-pong). The amplitude envelope is stored as the AHDSR volume modulation device. A per-sample filter (low-pass, high-pass, band-pass and band-stop) is stored as the native sampler filter inside the modulation set together with its cutoff, resonance and an optional cutoff envelope; a pitch envelope is stored as well. A pitch LFO (vibrato) and a volume LFO (tremolo) are stored as LFO modulation devices, carrying their waveform, rate, depth and onset time. The instrument comment is used as the description metadata. Samples whose velocity ranges are identical are combined into one velocity layer (group); if the keyzone overlapping mode is set to *Cycle* the overlapping samples are treated as round-robin and if it is set to *Random* as a random selection.

Renoise has no loop cross-fade parameter, so by default loops are written exactly as they are (faithful). If the loop cross-fade processing option is enabled, the cross-fade is baked into the sample audio so that looped samples wrap seamlessly - this is useful for source formats whose loops contain a discontinuity (e.g. some SoundFonts).

The following limitations apply:

* Renoise uses a 10 octave keyboard (notes 0 to 119), so notes above B-9 are clamped when writing.
* The keyzone overlapping mode is a setting of the whole instrument, therefore round-robin and random zones cannot be mixed: if at least one zone uses a random selection, all overlapping zones are played randomly.
* The filter cutoff frequency mapping is an approximation since Renoise's normalized cutoff to frequency curve is internal and not part of the format.
* There are no dedicated category, author or keyword fields in the format; only the name and a free-text comment are available.
* Samples stored as 32-bit FLAC inside a source instrument cannot be transcoded (a limitation of the bundled FLAC decoder) and are skipped with an error.

## Roland MC-707/MC-101

The Roland MC-707 and MC-101 GROOVEBOX run the ZEN-Core sound engine and keep all of their user content in a single project file (*.mpj*) on the SD card: the tracks with their clips, the project's user tone and drum-kit banks and the user samples with their audio. Both devices use the byte-identical format, so a written project loads on either one. The file format is not documented by Roland; it was reverse-engineered from Roland's own preset projects and the init-project image embedded in the device firmware (see *documentation/design/MC707_FORMAT.md*). Written projects have not been verified on hardware yet - feedback is welcome.

A written project is the device's own init project with the converted sounds placed in its user banks, so everything outside of the converted content is in the exact factory-default state. Copy the file to the `ROLAND/PROJECT` folder of the SD card and load it as a project; the sounds appear as the project's user tones and user drum kits, the samples in the project's sample list. A single-zone source becomes a **user tone** whose first partial plays the sample chromatically, carrying the source's filter (type, cutoff, resonance) and amplitude envelope - the exact record pattern of Roland's own user-sample preset tones. A multi-zone source becomes a **user drum kit** that maps each key of the kit range (A0-C8) to its zone's sample with the key transposition baked into the per-key pitch, the pattern of Roland's own user-sample preset kits. Samples are stored the way the device's sample import stores them: interleaved stereo 16-bit at the native 44.1 kHz (mono sources are duplicated to both channels), with level, original key, start, loop start and end in the project's sample-parameter table. When creating a library, all sources are packed into the user banks of one project file.

Reading extracts every tone and drum kit that plays user samples - from the user banks as well as from the per-clip sounds of the tracks (this includes Roland's downloadable preset projects). The audio of ROM-wave sounds is not contained in the file and cannot be converted. Runs of neighboring kit keys playing the same sample with an ascending chromatic pitch merge back into one key-ranged zone, so a written project round-trips.

The following options are supported:

* **Write as multisample tones instead of drum kits**: the project format also contains a multisample key-map table - the analog of the FANTOM's, whose tone record layout the MC shares. With this option a multi-zone source becomes one chromatic **multisample tone** (a key map plus a tone whose first partial plays it), which represents melodic multisamples faithfully. It is off by default because no Roland-authored project uses that table, so unlike the drum-kit pattern it could not be verified against real files; treat it as experimental until confirmed on hardware. Multisample tones written this way are read back.

The following limitations apply:

* A project holds up to 64 user tones, 64 user drum kits and 500 samples; sources beyond that are skipped with an error.
* A drum kit key (and a multisample map key) plays a single sample, so overlapping velocity layers are reduced to the loudest layer; drum kit zones outside of A0-C8 are dropped.
* The tone's chromatic keyboard tracking spans the full keyboard; per-partial key/velocity ranges are not written (their binary encoding is not fully decoded).
* The envelope time curve (seconds to the 0-1023 range) is an approximation, as with the other ZEN-Core formats.

## Roland MV-8000/MV-8800

The Roland MV-8000 Production Studio (2003) and its successor MV-8800 are pad-based sampling workstations. A patch consists of up to 96 partials which are assigned to the 96 pads (6 pad banks with 16 pads), each partial layers up to 4 samples (SMT slots) with velocity ranges and crossfades. Stereo samples are stored as 2 mono samples. Patches are stored in single *.mv0* files which contain all parameters and the sample data (16-bit/44.1kHz).

The file format is not documented by Roland, it was reverse-engineered from the factory patches and the parameter tables of the MV-8000/MV-8800 firmware (see *documentation/design/MV8000_FORMAT.md*). Names, the category, the note mapping, velocity ranges and crossfades, loops, play modes, pitch key-follow, SMT level/panning/coarse/fine tuning, the amplitude envelope, the filter (type, cutoff, resonance, envelope) and the sample data are read and written. The envelope times are converted with the time table of the sound engine, which the MV-8000 inherits from the S-7xx series: 0.01 to 21.8 seconds over the 0-127 setting. Note that MV-8800 *.mvf* files are effect presets (e.g. for the Analog Bass Synth) and not patches, they cannot be converted.

When writing patches, samples are converted to 16-bit/44.1kHz. Since the note range of a patch is limited to MIDI notes 21-116, zones outside of this range are clipped or skipped. Identical samples mapped to multiple key ranges are stored only once.

### Destination Options

* Shorten the name for the device display: Keeps only the last separated part of the name (after " - ", " / ", " : " or " | "), e.g. *Greek Bazouki - Dark Tremolo* becomes *Dark Tremolo*. The name field of this format holds only 12 characters, so a source whose name is qualified with the bank it comes from otherwise loses exactly the part which tells its presets apart. The dropped part is not written anywhere else, therefore this is disabled by default. CLI: `MV8000ShortenName=1`.

## Roland S-10 Series

### Roland S-10 family

The Roland S-10 family comprises the S-10 digital sampling keyboard and its rack-mounted derivatives, notably the MKS-100 and the subsequently enhanced S-220, which represent Roland’s early implementation of relatively compact, low-cost digital sampling technology. The S-10, introduced in the mid-1980s, employed linear 12-bit A/D conversion at selectable sampling rates of 30 or 15 kHz, with approximately 4.4 seconds of total memory at 30 kHz distributed across four sample banks; its architecture provided eight-voice polyphony. The S-220 added four-part multitimbrality.

Sample storage was based on Roland's 2.8-inch Quick Disk format, and the instruments supported MIDI-based sample transfer despite predating the later MIDI Sample Dump Standard.

* Currently, only the system exclusive format is supported. **If you can provide QD examples, please get in touch.**
* Only reading is supported.
* Files from the S-220 are not tested and might not work **If you can provide examples, please get in touch.**

## Roland S-50 Series

The Roland S-50 series (S-50, S-330, S-550, W-30), introduced in the mid-1980s, represented a significant development in digital sampling technology. Based on 12-bit pulse-code modulation (PCM) sampling, the system combined waveform acquisition, editing, and keyboard performance capabilities within a single instrument. The series was notable for its integration of video-based graphical editing, enabling detailed visualization and manipulation of sampled waveforms.

The format of the S-50 is slightly different to the one used on the other models. All of them store 12-bit samples with 15/30kHz sample rate. It is a good idea to up-sample them (with the Processing feature) to e.g. 16-bit/44.1kHz to prevent compatibility issues. Only reading is supported.

There was a specific CD-ROM drive for the S-550 (CD-5). This format can be read as well. The main difference to the diskette format is that it uses 16-bit samples.

## Roland S-770 Series

The Roland S-770 series comprises a family of digital PCM samplers introduced between 1989 and 1995, including the S-750, S-770, S-760, DJ-70, DJ-70 MkII, and SP-700. These instruments share a common sampling architecture based on high-resolution PCM playback, digital resonant Time Variant Filters (TVFs), and sophisticated modulation and envelope generators. The flagship S-770 expanded the platform with advanced multisampling capabilities, internal digital signal processing, and video-based graphical editing, while the later S-760 provided similar functionality in a more compact and cost-effective form. The DJ-70 and SP-700 adapted the technology for performance-oriented and phrase-sampling applications.

Only reading is supported. But it supports both HD/CD-Rom and diskette image files. Also files that span multiple diskettes are supported (all disk files need to be in the same folder).

## Roland SP-404MK2

The Roland SP-404MKII is a pad-based sampler and effector. A project holds 10 banks (A-J) of 16 pads each (160 pads); every pad plays a single sample with its own start/end, loop, level, pan, pitch and BPM. It is not a keyboard multi-sampler - there are no key-ranges - so, as with the other pad devices, ConvertWithMoss treats each **populated bank as one multi-sample** and each **pad as a single-key zone** (a drum-kit-shaped mapping).

The format is not documented by Roland; it was reverse-engineered and validated against real exported projects and the SP-404MKII v5.52 firmware (see *documentation/design/SP404MK2_FORMAT.md*). A project is a folder (`PROJECT_XX`) holding a `PADCONF.BIN` pad-configuration file and a `SMPL` folder of `BANK<bank>-<pad>.SMP` samples in Roland's own `RFWV` wave container (48 kHz, 16-bit, big-endian PCM). Both the exported (31,488-byte) and the device-internal (52,000-byte) `PADCONF.BIN` forms are read. Point ConvertWithMoss at a project folder (or a folder of projects, e.g. the `EXPORT/PROJECT` folder of an SD card) to read it. Writing produces the same `PROJECT_XX/PADCONF.BIN` + `SMPL/*.SMP` layout; copy it into the `IMPORT` folder of an SD card and load it with the device's *IMPORT PROJECT* function. Written projects have not been verified on hardware yet - feedback is welcome.

Reading extracts each pad's full sample together with its name, level, panning and loop on/off state; a multi-zone source written back becomes one pad per zone. The following limitations apply:

* Each pad plays one sample, so overlapping velocity layers are reduced to the loudest layer and a bank holds at most 16 zones; zones beyond a bank are carried into the next bank.
* The on-device start/end trim and loop points are preserved in the file but not applied - the full sample is always extracted (the byte-offset encoding of the trim window is not yet fully decoded).
* Patterns (`PTN`), pattern chains and project artwork are not converted.

## Roland ZEN-Core

The ZEN-Core sound engine powers Roland's FANTOM-0, FANTOM / FANTOM EX, Juno-X, Jupiter-X/Xm and the MC-707/MC-101 grooveboxes, which all share one *.svz* container - and one model tag (`KY019`) - holding a tone (or a bank of tones) together with its user samples and its keyboard mapping. ConvertWithMoss writes an importable *.svz* - a single tone for one multi-sample, or a multi-tone bank that shares one sample pool for several - which is loaded on the device through its *UTILITY -> IMPORT* function. The GAIA-2 and the ZENOLOGY plug-in run the same engine but are not supported, because neither can load the user samples a multi-sample needs: the GAIA-2 has no sampler at all (its *IMPORT* accepts only tones) and the ZENOLOGY plug-in imports only the tone from a *.svz*, so a multi-sample would play silent there. The user samples, multisample key map, filter and envelopes of a *.svz* are also read back (the envelope times through the same hardware-calibrated law the writer uses).

User samples are written at the device-native 48 kHz / 16-bit. As the ZEN-Core voice engine has no loop cross-fade or de-click of its own, click-free playback (hardware-verified on a FANTOM-0) is prepared into the samples: the loop end is re-seated so the wrap reproduces the waveform's own step into the loop start, and a loop with no seamless end point (an evolving pad) gets its tail cross-faded in phase into the loop-start lead-in.

**Smoothing audible loop jumps - the *Set fixed loop-crossfade* processing option.** A loop can wrap click-free and still jump audibly on every pass: when the sound evolves across the loop region (a swelling pad), the level just before the loop end differs from the level at the loop start. That jump sits in the source's own loop points and plays the same way on the source instrument, so it is faithfully kept by default. Since the ZEN-Core engine cannot cross-fade at playback, enabling the *Set fixed loop-crossfade* processing option (or a cross-fade specified by the source preset) bakes the cross-fade into the written sample audio instead. A loop that already wraps cleanly is left untouched - byte-identical to a conversion without the option - so it is safe to enable for a whole library; a moderate value such as 30 % is a good starting point (hardware-verified: the loop stays audible as a musical evolution but no longer jumps). All samples are stored mono, since the voice engine mis-plays an interleaved-stereo sample (the two channels alternate into the output as a buzz). A stereo source is written the factory way - split into a left and a right mono sample played by a two-partial tone, Partial 1 panned hard left and Partial 2 hard right - which is hardware-verified click-free true stereo. Velocity layers are mapped onto separate tone partials - each distinct source velocity range plays its own partial (in stereo, its own pair of hard-panned partials), up to four mono or two stereo layers, the voice engine's four-partial limit, with any excess layers merged into the top one; a single velocity range is written as one multi-sample. Samples and multisamples are named with a content hash, since the device silently re-uses already imported ones of the same name, an inert spacer sample always closes the sample pool (the device does not reliably load the wave data of the last sample in a pool - hardware-verified with pools of one and of six samples - so no real sample is written into the last slot), and sample name fields are zero-padded as the device requires. Each tone additionally carries the source's filter (type, cutoff, resonance) and its amplitude, pitch and TVF-filter envelopes - both the envelope time law (each stage's seconds-to-value curve was measured on a FANTOM-0 with a calibration bank; the previous approximation played all envelope stages several times too fast and collapsed times below ~0.3 s to instant) and the pitch and filter envelope depths are hardware-calibrated; a near-instant amplitude attack is floored to a small hardware-calibrated value, since the voice engine emits a transient at note-on when the amplifier opens too fast; percussive material whose own onset hides that transient (saws, plucks, drum hits - hardware-verified at every pitch) keeps the fastest attack and its full transient, while smooth material (string pads) gets a floor whose ~11 ms ramp is faster than half a cycle of a bass fundamental. Tone names are capped at the format's 16 characters - the name the device displays - and names in a bank that would truncate identically stay recognizable: part of the shared head is elided with a ~ and the distinctive tail is kept (e.g. *082_RTW2_106_BASS_SAW* and *..._SQR* become *082_RTW2_106~SAW* and *082_RTW2_106~SQR*). A source without any convertible sample is skipped with an error instead of being written as a silent tone, and a broken source does not lose the rest of a library. There are no destination options: the file always carries the shared `KY019` header of the sample-capable hardware.

### Destination Options

* Shorten the name for the device display: Keeps only the last separated part of the name (after " - ", " / ", " : " or " | "), e.g. *Greek Bazouki - Dark Tremolo* becomes *Dark Tremolo*. The name field of this format holds only 16 characters, so a source whose name is qualified with the bank it comes from otherwise loses exactly the part which tells its presets apart. The dropped part is not written anywhere else, therefore this is disabled by default. CLI: `ZenCoreShortenName=1`.

## Sample files (AIFF, CAF, FLAC, NCW, OGG, WAV)

This powerful algorithm allows to create multi-samples from single sample files incl. detection of metadata.

All files of the same type located in the same folder are considered as a part of one multi-sample. You can also select a top folder. If you do so, all sub-folders are checked for potential multi-sample folders.

WAV files can already contain metadata to configure a complete multi-sample (but sadly rarely used). Therefore, all WAV files of a folder are checked if they contain instrument chunks. If this is the case they are used to create the layout of the multi-sample (range and velocity splits as well as gain and pitch settings). The Instrument chunks of AIFF and CAF files are used in the same way. If no such information is available the same algorithm is applied as for the other supported formats: it tries to detect the necessary key range and velocity information from the names of the WAV files as well as metadata:

* Notes are first detected from the files metadata (if supported by the format). If none is present, different parser settings are applied on the file name to detect a note name (or MIDI note value).
* A category is extracted from the file name as well based on a list of several synonyms and abbreviations (e.g. Solo as a synonym for Lead). If this fails the same logic is applied to the folder names (e.g. you might have sorted your lead sounds in a folder called *Lead*).
* Characterizations like *hard* are extracted as well with a similar algorithm as for the category.

CAF (Apple Core Audio Format) files are read when they contain linear PCM (integer or float in either byte order), IMA4 (Apple's IMA 4:1 ADPCM), µLaw, aLaw, Apple Lossless (ALAC) or MPEG-4 AAC (the low complexity profile, i.e. plain AAC without the high efficiency extensions) audio data. This includes the sample files which the instruments of Logic Pro reference. Files with other codecs are reported with the name of their codec. Root note, key/velocity ranges, gain and tuning are read from the Instrument chunk, loops from the referenced regions or the sustain loop markers and metadata texts from the Information chunk. The ALAC and AAC codecs are ports of their reference implementations (Apple and FFmpeg) and work on all platforms.

As the destination, the audio file format of the written sample files can be selected: WAV, AIFF, CAF (with linear PCM), CAF-ALAC (CAF compressed with Apple Lossless), CAF-AAC (CAF compressed with MPEG-4 AAC, which is lossy - prefer CAF or CAF-ALAC when fidelity matters) or FLAC. This turns the format into a general audio file converter, e.g. from WAV to AIFF or from AIFF to FLAC. All conversions except CAF-AAC are lossless with one exception: FLAC supports at maximum 24-bit, therefore 32-bit samples are reduced. The AAC encoder chooses its scalefactors for a fixed quantization precision instead of using a psychoacoustic model, which results in a simple, high bit rate encoding. Float samples are converted to 16-bit integer for AIFF, CAF-ALAC and FLAC (WAV and CAF keep them as float). The WAV chunk options are applied to the other formats accordingly: the instrument and sample (loop) information is written to the Instrument and Marker chunks of AIFF files and to the Instrument and Region chunks of CAF files, the metadata texts to the Author/Annotation chunks of AIFF files and to the Information chunk of CAF files. FLAC files carry no instrument information.

### Source Options - Groups

Detected groups will be equally distributed across the velocity range. E.g. if 2 groups are detected the first will be mapped to the velocity range of 0-63 and the second to 64-127.

* Detection pattern: Comma separated list of patterns to detect groups. The pattern must contain a star character ("*"), which indicates the position which contains the group number.
* Order of group numbering: Enable to map groups inversed. This means that the highest number will be mapped to the lowest velocity range.

### Source Options - Mono Splits

WAV file can contain different sample formats. This converter supports (split) stereo uncompressed and IEEE float 32 bit formats. Only WAV files in Mono or Stereo are supported. Stereo samples might be split up into 2 mono files (the left and right channel). This tool will combine them into a stereo file.

* Left channel detection pattern: Comma separated list of patterns to detect the left channel from the filename. E.g. "_L".

### Source Options

* Crossfade notes: You can automatically create crossfades between the different note ranges. This makes especially sense if you only sampled a couple of notes. Set the number of notes, which should be cross-faded between two samples (0-127). If you set a too high number the crossfade is automatically limited to the maximum number of notes between the two neighboring samples.
* Crossfade velocities: You can automatically create crossfades between the different groups. This makes especially sense if you sampled several sample groups with different velocity values. Set the number of velocity steps (0-127), which should be crossfaded between two samples. If you set a too high number the crossfade is automatically limited to the maximum number of velocity steps between the two neighbouring samples.
* Post-fix text to remove: The algorithm automatically removes the note information to extract the name of the multi-sample but there might be further text at the end of the name, which you might want to remove. For example the multi-samples I created with SampleRobot have a group information like "_ms0_0". You can set a comma separated list of such post-fix texts in that field.
* Ignore loops: Sometimes the source files contain wrong loops. Especially helpful for one-shot samples.

### Destination Options

* Audio file format: The format of the written sample files: WAV, AIFF, CAF, CAF-ALAC (CAF compressed with Apple Lossless), CAF-AAC (lossy) or FLAC.

## SFZ

"The SFZ format is a file format to define how a collection of samples are arranged for performance. The goal behind the SFZ format is to provide a free, simple, minimalistic and expandable format to arrange, distribute and use audio samples with the highest possible quality and the highest possible performance flexibility" (cited from https://sfzformat.com/).

The SFZ file contains only the description of the multi-sample. The related samples are normally kept in a separate folder. The converter supports samples in WAV, OGG and FLAC format.

SFZ can only mark a sample as a one-shot (`loop_mode=one_shot`) if it has no loop. A looped zone therefore keeps its loop and is not written as a one-shot.

A pitch LFO (vibrato) is read and written via the `pitchlfo_freq` (Hertz), `pitchlfo_depth` (cent), `pitchlfo_delay` and `pitchlfo_fade` (seconds) opcodes. A volume LFO (tremolo) is read and written via the `amplfo_freq` (Hertz), `amplfo_depth` (decibels), `amplfo_delay` and `amplfo_fade` (seconds) opcodes.

The intensity of the velocity to volume modulation is read and written via the `amp_veltrack` opcode. If the source also describes the response curve of the modulation (e.g. the velocity modulation of Kontakt scales the amplitude with the cube of the velocity), the curve is written as `amp_velcurve_N` points between which the players interpolate linearly. When reading, `amp_velcurve_N` points are fitted to the closest power law and stored as the curve of the modulation, so the response survives a round trip.

### Source Options

* Log unsupported SFZ opcodes: If enabled, opcodes which are found in the source but are not used (not supported) as input for the conversion are logged.

### Destination Options

* Convert to FLAC format: If enabled, the sample files are converted to FLAC.

## SoundFont 2

The original SoundFont file format was developed in the early 1990s by E-mu Systems and Creative Labs. It was first used on the Sound Blaster AWE32 sound card for its General MIDI support.

A SoundFont can contain several presets grouped into banks. Presets refer to one or more instruments which are distributed over a keyboard by key and velocity ranges. The sample data contained in the file is in mono or split stereo with 16 or 24 bit.

The conversion process creates one destination file for each preset found in a SoundFont file. The mono files are combined into stereo files. If the left and right channel mono samples contain different loops, the loop of the left channel is used.

There are metadata fields for creator and some description specified in the format. However, additional information like a category is retrieved from Broadcast Audio Extension chunks in the WAV files. If no such chunks are present an [automatic detection](#automatic-metadata-detection) is applied.

The vibrato low frequency oscillator (generators `vibLfoToPitch`, `freqVibLFO` and `delayVibLFO`) is converted to and from the pitch LFO, carrying its depth, rate and delay. The volume modulation of the modulation low frequency oscillator (generators `modLfoToVolume`, `freqModLfo` and `delayModLfo`) is converted to and from the volume LFO (tremolo) in the same way. The waveform is always a triangle in this format and there is no fade-in.

### Source Options

* Log unused SF2 generators: If enabled, generators which are found in the source but are not used (not supported) as input for the conversion are logged.
* Keep mismatched stereo samples as mono: If enabled, two samples that the SoundFont links as a stereo pair but whose left and right halves differ in length are kept as separate mono samples instead of being combined into one stereo sample. Off by default. Some SoundFonts - notably commercial E-mu banks - carry unreliable stereo links that flag unrelated mono samples as a pair, so enabling this avoids welding two different sounds into a single stereo sample; leave it off if a bank contains genuine stereo pairs whose channels differ slightly in length. (A differing pitch or sample rate always keeps the samples separate.)
* Prefix with file name: If enabled, the name of the Soundfont file is added to all resulting destination files.
* Prefix with program number: If enabled, the preset number of the preset is added to the resulting destination file.

### Destination Options

* Re-sample 24-bit to 16-bit: If enabled, 24-bit source samples are converted to 16-bit files. Use this prevent issues with certain software which can only handle 16-bit samples.

## Spectrasonics Omnisphere 3

Spectrasonics Omnisphere 3 is a software synthesizer designed for music production, sound design, and film scoring. It combines sample-based sounds with multiple synthesis methods and includes a large library of presets, effects, and modulation tools.
Its user interface supports the import of single samples but not multi-samples. But ConvertWithMoss can create multi-samples as well. Factory files have a slightly different format and are not supported.

Several file types are relevant:

* .db: these files contain one or more samples.
* .zmap: these files contain the basic multi-sample layout and reference one or more db files.
* .prt_omn: these files represent one preset which reference one to four zmap files.

The db files need to be in the same folder as the zmap file. The presets need to be in the Omnisphere patches folder under User.

First locate the Omnisphere STEAM folder on your computer. On Windows it is normally:

> `C:\ProgramData\Spectrasonics\STEAM`

The db and zmap files are stored in:

> `<STEAM_FOLDER>\Omnisphere\Settings Library\Patches\User`

The preset files are stored in:

> `<STEAM_FOLDER>\Omnisphere\Soundsources\User`

You can create sub-folders in these folders as well.

### Reading preset files

When reading preset files the related db and zmap files must be in the sub-folder `Soundsources\User` which is either in the same directory as the zmap file or in an up-wards directory.

### Writing preset files

ConvertWithMoss creates a sub-folder for each source multi-sample. This folder contains all db files as well as the zmap and prt_omn files. Copy the whole folder to

> `<STEAM_FOLDER>\Omnisphere\Soundsources\User`

then move the prt_omn file to 

> `<STEAM_FOLDER>\Omnisphere\Settings Library\Patches\User`

**Note 1**: You can create a sub-folder with the name of a category, e.g. "Vox Humana" and put it there.
**Note 2**: When opening Omnisphere both the presets and soundsources need to be rescanned! If only the presets are scanned an error shows up that the soundsource cannot be located!

## Synclavier Regen

The Synclavier Regen (2023) is a desktop re-creation of the classic Synclavier synthesizer. Its sounds are organized in *libraries* - a folder placed under *Libraries/* on the device SD card that holds one or more *timbres* together with their samples and two index files. A timbre is a plain UTF-8 text file named *NN-Entry.txt* which starts with a title and a comment line followed by *SynclavierVirtualInstrumentTimbreVersion...* and the parameter lines. A timbre has up to twelve *partials* (layers); each partial maps its samples across the keyboard through *SynclavierPTPatchListEntry* lines (key range, root key, volume, tuning, start, loop). ConvertWithMoss reads a whole library folder (each timbre becomes one multi-sample) and writes a library folder that can be copied to the device SD card.

The format is not documented by Synclavier Digital, it was reverse-engineered from the device firmware and the freely available libraries (see *documentation/design/SYNCLAVIER_REGEN_FORMAT.md*). Names, the comment and its *#tags* (on writing, the category is written as the Regen's primary category tag - mapped to its canonical tag list, see the Regen manual - and the keywords as property tags; on reading, the first tag becomes the category and the rest the keywords), the partial/zone layout (a partial which is muted in the editor keeps its samples in the timbre but is not played, so it is skipped), key ranges, root keys, per-zone volume (`gain[dB] = 36 * f - 12`) and tuning (`tune[cents] = 250 * f - 125`), the per-partial volume (dB) and pitch (a fine tune in cents, a semitone transpose and an octave transpose), the sample start, the loop (start, end, cross-fade), velocity layers (each layer becomes a partial selected by velocity via its crossfade window - a timbre has 12 partials, so up to 12 velocity layers; groups which do not overlap in key and velocity are packed into shared partials first, since a partial holds a whole key map), the per-partial amplitude envelope, the per-partial panning and the timbre-global multi-mode filter (low-pass, high-pass or band-pass with a 2- or 4-pole slope; cutoff, resonance, the filter envelope with its start, peak and end levels, and keyboard tracking) are read and written. The additive/FM synthesis parameters (the index envelope, harmonic coefficients, frame partials, modulation matrix) are not converted. Mono and stereo samples in 16- or 24-bit at any sample rate are supported.

Commercial libraries store their samples as *.sflc* files: a normal FLAC file whose bytes are obfuscated with a key-stream that is keyed by the file's own base name. ConvertWithMoss reads these transparently and, on writing, produces the same obfuscated *.sflc* files (so renaming an *.sflc* file breaks it). User libraries may instead reference plain *.wav* or *.flac* samples, which are also read. Samples that are used by several partials or timbres are stored only once, as in the original libraries. When a source folder is converted as a whole (the library option), all timbres are written into a single library folder together with the shared sample pool and the *_&lt;name&gt;.tsv* and *_TimbreIndex.tsv* index files; otherwise each timbre is written as its own small library folder.

### Getting a converted library onto the Regen

The Regen loads sounds from *libraries* on a FAT32-formatted SD card. To convert your sounds and use them on the device:

1. Convert with *Synclavier Regen* as the destination. When converting a whole folder of sounds, enable the library option (the *Create Library* / library name field in the GUI, or `-l "<Name>"` on the command line) so they become one library you can browse on the device. Without it, every sound is written as its own separate one-timbre library, which quickly clutters the browser.
2. Copy the produced library folder(s) into the `Libraries/` folder at the top level of the SD card - create `Libraries/` first if the card is blank. Everything a library needs is inside its own folder: the `NN-Entry.txt` timbres, the samples and the `_<name>.tsv` / `_TimbreIndex.tsv` index files. Copy the whole folder, not its contents.
3. Insert the card into the Regen and choose *Load* on the *"Load SD Card?"* prompt.
4. On the device, open the library browser, switch to the *User* libraries, select your library and load its timbres (they are addressed as *bank-entry*, e.g. *2-4*, matching the `NN-Entry.txt` numbering).

A Regen library holds at most **64 timbres** (8 banks of 8). When a conversion produces more, it is automatically written as several numbered libraries (`<Name> 1`, `<Name> 2`, …), each within the limit.

To give a library a cover and description in the browser, add a `CDImage.png` (1:1 aspect ratio) and a `Description.txt` (up to 220 characters) to its folder - both names are case-sensitive. The `Description.txt` is written automatically from the descriptions of the converted sounds (the same metadata description carried by other formats; the unique descriptions of a library's timbres are combined, as the SoundFont creator does for its comment). When none of the sounds has a description, no `Description.txt` is written and the device shows its default. Either way you can add or replace it.

## Synthstrom Deluge

The Synthstrom Audible Deluge is a standalone hardware synthesizer, sampler and sequencer. Its sounds are stored as XML files (file ending *xml*) on the SD-card; a *sound* holds a (multi-)sample based synth voice and a *kit* holds a set of drums. The samples themselves are referenced by a path which is relative to the SD-card root (e.g. *SAMPLES/My Patch/C3.wav*). Both reading and writing are supported.

The Deluge has gone through several firmware generations which changed how the XML is written: the official firmware (up to v4) stores the parameters as child elements while the community firmware additionally writes them as attributes. When reading, **both** variants - and both *sound* and *kit* files - are understood, so patches authored on either firmware are translated without losing information. The sample mapping (key ranges), the original root note (from the *transpose* / *cents* offset, or the fixed root note of a drum), the sample playback start/end and loop points, the loop mode (one-shot or loop), the reversed flag, the low- and high-pass filter (with the various filter modes), the amplitude and filter envelopes, and the filter keyboard-tracking, filter velocity and velocity-to-volume modulations are converted.

When writing, a multi-sample can be stored either as a *sound* (synth) patch or as a drum *kit* - see the *Output Type* option below. A *sound* uses a single sample oscillator whose zones are written as a *sampleRanges* list. A *kit* writes one drum per note (a *sound* inside the kit's *soundSources*), laid out low to high. Since a Deluge drum is a single sample, velocity layers and round-robins (several zones on the same note) are consolidated to the loudest layer. Because a one-sample-per-note layout is not necessarily a kit (a per-note synth bass looks the same), the type is chosen explicitly rather than guessed. A kit holds at most one drum per note from 36 up (92 drums); a larger source is truncated with a logged warning. The file is written in the **element-based form of the official v4 firmware** (`firmwareVersion="4.1.0-alpha"`), so it loads on both the official v4 firmware and on the community firmware - no community-only features are used. The output mirrors the Deluge SD-card layout: the patch is written to a *SYNTHS* (or *KITS*) sub-folder and its samples to *SAMPLES/&lt;name&gt;/* next to it, and the *fileName* references are written relative to that card root.

The Deluge has no loop cross-fade parameter of its own, so - exactly like the Renoise format - a loop cross-fade is baked into the looped sample audio. This happens automatically (there is no extra option) whenever a forward loop has a cross-fade: set one with the *Set fixed loop-crossfade* processing option, or it is taken from the source. This removes clicks at the loop point of samples whose loop was not designed to be click-free without a cross-fade (many auto-sampled instruments rely on the host applying a cross-fade at play time). With no cross-fade set, the loop is written exactly as it is.

### Destination Options

* Output Type: Choose whether to write a *Synth (Sound)* preset (the default) or a *Drum Kit*. A kit writes one drum per sample; use it for drum/percussion sets. CLI: `DelugeOutputType=kit` (or `sound`).
* Consolidate kit (one drum per type): For a *Drum Kit*, reduce the kit to a single drum per recognized type (kick, snare, hi-hat, tom, ...) and order the drums by drum role - kick on the lowest row - following the factory TR-808 layout, so a beat can be programmed without switching rows. Several drums of the same type are reduced to the first; unrecognized drums are all kept and appended at the end. Each consolidated drum is labelled by its role (Kick, Snare, Hat Closed, ...) for a clean read-out on the device, while the sample files keep their original names. CLI: `DelugeConsolidateKit=1`.
* Shorten kit name: For a *Drum Kit*, give the kit a short name for the device display - the last separated segment of the name (separators " - ", " / ", " : ", " | "), prefixed with the zero-padded "Kit" number if present (e.g. "80s hits SSS043 - Kit 07 - Full Kit 2" becomes "007 Full Kit 2"), so it does not scroll as much on the OLED. A redundant trailing "Kit NN" segment is dropped in favour of the preceding one, and a trailing date or version suffix ("-20220718", "v2") is removed while model numbers ("TR-808", "R-50") are kept. CLI: `DelugeShortenKitName=1`.
* Options to write/update [WAV Chunk Information](#wav-chunk-information).

### Limitations

* A Deluge *sound* has a single sample oscillator with one sample per key, therefore only one velocity layer is written. If a source contains several velocity layers, the loudest zone of each key is kept and the others are ignored.
* The loop mode belongs to the sample oscillator and not to the individual samples. A one-shot is therefore only written if all zones of the sound are one-shots, otherwise the loop of the source is kept.
* The amplitude envelope attack, decay and release times are converted using the Deluge's internal rate tables (attack about 0.7 ms .. 3 s, decay/release about 6 ms .. 6 s); times outside that range are clamped. The filter cut-off / resonance are stored as the Deluge's internal 32-bit parameter values and are a musically faithful approximation rather than an exact match.
* Effects and device-specific modulation are not converted. The Deluge's reverb, delay, chorus / mod-FX, distortion, EQ, sidechain/compressor and arpeggiator are outside the multi-sample model, and modulation sources such as the LFOs are not carried because their parameters (rate ranges, shapes, sync, destinations) differ from one sampler to the next and have no portable representation. A patch that relies on them - for example a pad whose long tail comes from reverb and delay rather than a long amplitude release - therefore sounds drier or shorter after conversion, even though its oscillator, envelopes and filter are translated faithfully.

## TAL Sampler

TAL-Sampler is an analog modeled synthesizer with a sampler engine as the sound source, including a modulation matrix and self-oscillating filters. Most of the presets in it's library store the sample files in an encrypted format (*.wavsmpl), this format is not supported. Only presets using plain WAV or AIFF files are supported.

Choosing TAL Sampler as the destination format, creates a *talsmpl*
file and stores all samples in a sub-folder by the same name. The samples of the source groups are distributed across the 4 layers of TAL Sampler in such a way that the key and velocity splits do not overlap. This is a workaround for the fact that TAL Sampler does not support overlapping samples. Since groups have only the name and trigger type as attributes, which are not supported in TAL Sampler anyway, this should work in most cases. If there are still overlapping samples a warning is displayed.

## Teenage Engineering OP-XY

The OP-XY is a portable sampler and sequencer from Teenage Engineering. Its multi-sample presets are open: a preset is a folder with the ending *.preset* which contains all samples as WAV files and a *patch.json* file with the mapping and the settings of the synthesizer engine.

Reading a preset gives the key range, root key, tuning, gain, play range and loop (with its cross-fade and release behavior) of every region, plus the amplitude envelope, the velocity sensitivity and the pitch bend range which the device stores once for the whole preset.

Writing creates such a folder, which only needs to be copied to the device. Note the limits which the device manual states: a preset holds at most 24 zones, a sample may be at most 20 seconds long (longer ones are reported), and multiple velocities are not supported - of several zones which cover the same keys only the one which plays at the highest velocity is kept. The regions are written as the device expects them, i.e. covering the keyboard without gaps: a region ends at its own upper key and starts one key above the previous one. Samples are written as 16 bit WAV files with at most 44.1 kHz. The velocity sensitivity is filled from the source; if the source states nothing, the default of the device (displayed as 64) is used.

## Waldorf Quantum MkI, MkII / Iridium / Iridium Core

This family of Waldorf synthesizers supports the playback of multi-samples. One preset can contain several layers - 2 on the first generation of the instruments and 4 on the MK2 generation (Iridium MK2, Quantum MK2 and instruments upgraded to MK2 hardware). A layer is a complete preset in itself and the layers are simply concatenated. Each layer can have up to 3 oscillators of which each oscillator can contain its own multi-sample. The format is not documented by Waldorf; the file layout, the sample map and the value laws which are read and written are described in *documentation/design/WALDORF_QPAT_FORMAT.md*.

If this format is used as the source it produces one output preset per layer which plays samples; a layer which is switched off, or which plays a wave-table instead of samples, is reported and skipped. If used as the destination format, each group of the source multi-sample is applied to one of the 3 oscillators of a layer; with the *Use a second layer* option a preset holds up to 6 multi-samples instead of 3, and all groups which do not fit are added to the multi-sample of the last oscillator, as before. Note that the device cannot select a layer by velocity - it splits its layers by key, by MIDI channel or cycles them - so a velocity split is always written into the sample maps of one layer and only sounds which are meant to be heard together are spread across the layers.

The volume, panning and tuning of an oscillator are offsets on top of the values of its sample map entries, therefore the two are combined when reading instead of the oscillator replacing the sample map. When writing, the volume and panning of a group are stored on its oscillator and only the remainder in its sample map, so they survive a Quantum/Iridium round-trip - previously the oscillator was always written as 0 dB and Center.

A vibrato and a tremolo are carried through the modulation matrix. Of its 40 slots the first three are taken by the pitch envelopes of the three oscillators, so the vibrato uses the slot 4 with LFO 1 on the destination *Pitch* - which moves all three oscillators at once - and the tremolo the slot 5 with LFO 2 on the destination *VCA*; the slots 6-40 and the LFOs 3-6 are never written. The tremolo runs its oscillator unipolar with a negative amount, so it attenuates from the full level of the amplifier downwards, and its depth is scaled with the loudness law of the device, where the maximum depth of 96 dB is the swing down to silence. Both oscillators are written in the *Poly* mode, so that a vibrato and a tremolo belong to the played note. A low frequency oscillator of a read preset which is synchronized to the tempo of the song is not converted, since a multi-sample does not carry a tempo; a phase which the device runs freely is not converted either. The device has no fade-out for a modulation, therefore the *Decay* of a written oscillator is off.

Since the device has a field of its own for the bank, a source which carries its bank in front of its name is written with that bank removed from the preset name - the device shows it in the Bank field anyway and the display offers little room. The file name keeps the full name, so the files can still be told apart in a file browser. An explicit *Bank* from the destination options replaces the source's bank, which is then no longer written anywhere else, so in that case the preset name keeps it - but only as long as the qualified name fits into the name field, which holds 32 characters (the manual of the Iridium MK2 states the same limit for a name entered on the device). Everything beyond that is cut off, which removes exactly the part that tells the presets of one bank apart, so such a name is written without the bank of the source.

### Destination Options

* Re-sample to 16bit/44.1kHz: If enabled, samples will be resampled to 16bit and 44.1kHz. While the device can play higher resolutions as well it might impact the performance.
* Author: Written into the preset's Author field, which the device shows and can group presets by. When left empty, the creator from the source metadata is kept (e.g. the sound designer stored in a SoundFont).
* Bank: Written into the preset's Bank field. When left empty, the description from the source metadata is kept.
* Prefix file names with an import number: Prefixes each written preset file with a 5-digit number (e.g. *05002-Name.qpat*), which mirrors the naming of the device's own preset export; on import the device assigns the preset to that number. The *First import number* is used for the first written preset and each further preset increases the number by one; the source presets are converted in alphabetical order.
* Use a second layer for more than 3 groups: A preset normally holds one layer with up to 3 multi-samples, one on each oscillator, and every further group of the source is merged into the third of them. With this option a source with more than 3 groups is written as 2 layers with up to 6 multi-samples, which keeps the groups apart and gives the second set of them its own filter and amplitude envelope. Two layers are stored the same way by every instrument of the family, so such a preset plays on a Quantum, an Iridium and an Iridium Core as well as on the MK2 generation. Only groups which sound at the same time are put into a separate layer - the device cannot select a layer by velocity, so a velocity split stays inside the sample maps. Disabled by default. Verified on an Iridium MK2: the written preset loads and its second layer carries its own filter and amplitude envelope.
* Shorten file names to the displayed preset name: Names the written file after the preset name which the device displays instead of the full name of the source. A preset of a bank otherwise carries its bank in the file name (e.g. *05000-Full Arco String - Arco Strings Lo.qpat*, 45 characters), which is more than the import screen of the device shows - measured on an Iridium MK2, its file list displays about 43 characters and cuts off the rest, exactly the end that tells the presets apart. With this option the example becomes *05000-Arco Strings Lo.qpat*, and the whole file name - including the import number prefix and the file ending - is kept within 40 characters. Disabled by default, since the full name is what tells the presets of different banks apart in a file browser on the computer.
* Options to write/update [WAV Chunk Information](#wav-chunk-information)

## Yamaha YSFC

This format is used in many Yamaha Workstation. While the format is the same, the content is different.

### Using it as the source format

The following file formats are supported as a source:

* Motif XS: X0A, X0W
* Motif XF: X3A, X3W
* MOXF: X6A, X6W
* Montage: X7A, X7L, X7U
* MODX/MODX+: X8A, X8L, X8U
* Montage M: Y2U, Y2L

The wave files in professional Yamaha libraries are often compressed. Such files are not supported. Furthermore, only self-contained libraries (= libraries which do not reference samples in other libraries) are supported.

So far, reading of Performances is only supported for some formats. This means that for all other formats only the basic multi-sample data is converted (no filter and envelopes data is converted).

| Format                                          | Performance Data |
| :---------------------------------------------- | :--------------- |
| Motif XS: X0A, X0W                              |                  |
| Motif XF: X3A, X3W                              |                  |
| MOXF: X6A, X6W                                  |                  |
| Montage: X7A, X7L, X7U with Performance Data    | Yes              |
| MODX/MODX+: X8A, X8L, X8U with Performance Data | Yes              |
| Montage M: Y2U, Y2L                             |                  |

### Source Options

* Create multi-samples for: 
  * Waveforms: this reads only the raw-multi-sample(s) without additional Performance information. Use this option if the Performances do not reference all multi-samples in the library/user-bank.
  * Performances: Only supported for Montage and MODX/MODX+ files and when Performance data is present in the file. This sill create one multi-sample source for each Performance.

### Using it as the destination format

Currently, the user and library formats of the Montage (not Montage M!) and MODX/MODX+ are available as the destination format. The backup formats X7A and X8A are supported only as a source. The structure is as follows (bottom-up):

* A key-group references several samples which form a multi-sample setup (key-/velocity ranges)
* An element references 1 key-group (adds synthesis parameters)
* A part can contain up to 8 elements (this forms already a complex setup with layers and ranges)
* A performance can contain up to 16 parts (e.g. to perform a song by muting, soloing parts via scenes)
* A library contains several performances

**Destination Type: Preset or Preset Library**

When creating presets or libraries as the destination type, each multi-sample source creates one performance with one active part. Each group of the the multi-sample source is assigned to 1 element for which 1 key-group is created as well which contains the samples. If there are more groups than elements, the remaining groups are all added to the last element. If there are no groups all samples will be assigned to key-group/element one.

Note: There are no checks that the created libraries stay in the boundaries of the workstation specifications (e.g. the number of the maximum allowed samples or the required memory size)!

**Destination Type: Performance or Performance Library**

When creating (ConvertWithMoss) performances as the destination type, each performance source creates one (Yamaha) performance with one active part for each multi-sample (instrument) of the source. Since the parts 9-16 can only be addressed externally and even worse they have a fixed MIDI channel only the parts 1-8 are used.
MIDI channels of the instrument sources are mapped to scenes. Scene 1 represents first MIDI channel of the instrument sources, Scene 2 the second and so on until Scene 8. Each instrument source is assigned to 1 part. The keyboard is enabled for the scene with the respective MIDI channel. If the MIDI channel is set to OMNI it is active for all scenes.

If there are more than 8 instruments sources the following strategy is applied to reduce them to a maximum of 8:

1. All instrument sources are grouped by their MIDI channel. If there are more than 8 different MIDI channels, the highest of them are removed since they couldn't be mapped to scenes anyway.
2. If there are still more than 8 instrument sources, 2 instrument sources with the same MIDI channel and (if possible) the same key-range are aggregated into 1 instrument. Such aggregations are repeated until there are no more than 8 instrument sources.
3. Each of the up to 8 instrument sources is finally mapped to 1 performance part.

Other mappings are identical to creating presets/libraries.

### Destination Options

* File Format: Chooses the output format which is created.
* Create only Waveforms: No Performances will be written. Only Waveform data.
