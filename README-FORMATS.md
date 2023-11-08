# Source formats

The following multisample formats can be the source of a conversion.

## Akai MPC Keygroups / Drum

A MPC Keygroup or MPC Drum setup is stored in a folder. It contains a description file (.xpm) and the sample files (.WAV).
Both keygroup and drum types are supported.

There are currently no metadata fields (category, creator, etc.) specified in the format. Therefore, the same guessing logic is applied as with plain WAV files (see the metadata parameters of WAV above for an explanation).

## Bitwig Studio multisample

The parser can read all information from Bitwig Studio multi-samples except the group color, select and parameter 1 to 3, which are not mappable.

A Bitwig multisample file is a zip archive which contains all samples in WAV format and a metadata file in XML format.
This converter supports (split) stereo uncompressed and IEEE float 32 bit formats for the WAV files.

## DecentSampler

The Decent Sampler plugin is a free (but closed source) sample player plugin that allows you to play sample libraries in the DecentSampler format (files with extensions: dspreset and dslibrary). See https://www.decentsamples.com/product/decent-sampler-plugin/
The format specification is available here: https://www.decentsamples.com/wp-content/uploads/2020/06/format-documentation.html#the-sample-element

A preset file contains a single preset. A dspreset file contains only the description of the multisample. The related samples are normally kept in a separate folder. Only WAV files are supported.
A dslibrary file contains several dspreset files incl. the samples compressed in ZIP format.

There are currently no metadata fields (category, creator, etc.) specified in the format. Therefore, the same guessing logic is applied as with plain WAV files (see the metadata parameters of WAV above for an explanation).

## Korg KMP/KSF

The KMP/KSF format (*.KMP) was first introduced in the Korg Trinity workstation (1995) and since then supported in many Korg workstations and entertainment keyboards up to the latest Korg Nautilus (2020). The following keyboards are known to support the format:

* Trinity
* Triton
* OASYS
* M3
* Kronos
* KROSS (only for pads)
* PA1X/PA800/PA2X/PA3X/PA4X
* Nautilus

The format is documented in detail in the appendix of the respective parameter guides. The KMP format contains only 1 group of a multisample, which means there are only key splits but no groups. The file references several KSF files which contain the sample data for each key region.

## Korg wavestate/modwave

The korgmultisample format is currently used by the Korg wavestate and modwave keyboards. Files in that format (*.korgmultisample) can be opened with the Korg Sample Builder software and transferred to the keyboard.

Since the format is pretty simple all data stored in the file is available for the conversion.

## Native Instruments Kontakt NKI/NKM

Kontakt is a sampler from Native Instruments which uses a plethora of file formats which all are sadly proprietary
and therefore no documentation is publicly available. Nevertheless, several people analyzed the format and by now
sufficient information is available to provide the support as the source.

However, the format changed many times across the different Kontakt versions. So far, the following formats are known and supported:

| Kontakt Version |
|:----------------|
| 1               |
| 1.5             |
| 2 - 4.1.x       |
| 4.2.2+          |
| 5 - 7           |

A NKI file contains one instrument which is a multi-sample with many parameters. Currently, the multi-sample parameters are supported incl. loops. Furthermore, metadata information, the amplitude, pitch and filter cutoff envelope, filter parameters as well as pitchbend (except metadata currently only for Kontakt 1, 2).
(Most) NCW encoded sample files can be read as well.
A NKM file contains up to 64 instruments.

Encrypted files are not supported.

## SFZ

The SFZ format is a file format to define how a collection of samples are arranged for performance. The goal behind the SFZ format is to provide a free, simple, minimalistic and expandable format to arrange, distribute and use audio samples with the highest possible quality and the highest possible performance flexibility (cited from https://sfzformat.com/).

The SFZ file contains only the description of the multisample. The related samples are normally kept in a separate folder. The converter supports only samples in WAV format encoded as (split) stereo uncompressed and IEEE float 32 bit format.

There are currently no metadata fields (category, creator, etc.) specified in the format. Therefore, the same guessing logic is applied as with plain WAV files (see the metadata parameters of WAV above for an explanation).

## SoundFont 2

The original SoundFont file format was developed in the early 1990s by E-mu Systems and Creative Labs. It was first used on the Sound Blaster AWE32 sound card for its General MIDI support.

A SoundFont can contain several presets grouped into banks. Presets refer to one or more instruments which are distributed over a keyboard by key and velocity ranges.
The sample data contained in the file is in mono or split stereo with 16 or 24 bit.

The conversion process creates one destination file for each preset found in a SoundFont file. The mono files are combined into stereo files. If the left and right channel mono samples contain different loops, the loop of the left channel is used.

## TAL Sampler

TAL-Sampler is an analog modeled synthesizer with a sampler engine as the sound source, including a modulation matrix and self-oscillating filters. Most of the presets in it's library store the sample files in an encrypted format (*.wavsmpl), this format is not supported. Only presets using plain WAV or AIFF files are supported.

## WAV files

This is not a multisample format but a clever algorithm tries to detect the necessary information from each multisample file. It uses metadata found int the WAV file or from its' name.

WAV file can contain different sample formats. This converter supports (split) stereo uncompressed and IEEE float 32 bit formats.

All WAV files located in the same folder are considered as a part of one multisample. You can also select a top folder. If you do so, all sub-folders are checked for potential multisample folders.
The algorithm tries to detect as much metadata as possible from the WAV files:

* Notes are first detected from the sample chunk in the wave file (if present). If this is not set different parser settings are tried on the file name to detect a note name (or MIDI note value).
* The category is tried to be extracted from the file name. If this fails it tries with the folder names (e.g. you might have sorted your lead sounds in a folder called *Lead*). Furthermore, several synonyms and abbreviations are considered (e.g. Solo as a synonym for Lead).
* Characterizations like *hard* are tried to be extracted with a similar algorithm as for the category.

### Groups

Detected groups will be equally distributed across the velocity range. E.g. if 2 groups are detected the first will be mapped to the velocity range of 0-63 and the second to 64-127.

* Detection pattern: Comma separated list of patterns to detect groups. The pattern must contain a star character ("*"), which indicates the position which contains the group number.
* Order of group numbering: Enable to map groups inversed. This means that the highest number will be mapped to the lowest velocity range.

### Mono Splits

Stereo samples might be split up into 2 mono files (the left and right channel). This tool will combine them into a stereo file.

* Left channel detection pattern: Comma separated list of patterns to detect the left channel from the filename. E.g. "_L".
* Only WAV files in Mono or Stereo are supported.

### Options

* Prefer folder name: If enabled the name of the multisample will be extracted from the folder instead of the sample names.
* Default creator: The name which is set as the creator of the multisamples, if no creator tag could be found.
* Creator tag(s): Here you can set a number of creator names, which need to be separated by comma. You can also use this to look up other things. For example, I set the names of the synthesizers which I sampled. My string looks like: "01W,FM8,Pro-53,Virus B,XV" (without the quotes).
* Crossfade notes: You can automatically create crossfades between the different note ranges. This makes especially sense if you only sampled a couple of notes. Set the number of notes, which should be cross-faded between two samples (0-127). If you set a too high number the crossfade is automatically limited to the maximum number of notes between the two neighboring samples.
* Crossfade velocities: You can automatically create crossfades between the different groups. This makes especially sense if you sampled several sample groups with different velocity values. Set the number of velocity steps (0-127), which should be crossfaded between two samples. If you set a too high number the crossfade is automatically limited to the maximum number of velocity steps between the two neighbouring samples.
* Post-fix text to remove: The algorithm automatically removes the note information to extract the name of the multisample but there might be further text at the end of the name, which you might want to remove. For example the multisamples I created with SampleRobot have a group information like "_ms0_0". You can set a comma separated list of such postfix texts in that field.

## Destination formats

The following multisample formats can be the destination of a conversion.

### Akai MPC Keygroups

A MPC Keygroup is stored in a folder. It contains a description file (.xpm) and the sample files (.WAV).
This format has some restrictions:

* A round robin keygroup can only contain up to 4 layers (groups). An error is displayed in this case but the file is converted anyway.
* Only 128 keygroups are allowed. An error is displayed in this case but the file is written anyway but might not be loadable.

### Bitwig Studio multisample

This format can be loaded in the Bitwig Sampler device. It supports multiple groups, key and velocity crossfades as well as several metadata information: creator, sound category and keywords.

### DecentSampler

Writes a dspreset or dslibrary file (see above) depending out the setting. Samples are stored in a sub-folder with the same name.

Further options:

* Make monophonic: Restricts the sound to 1 note, use e.g. for lead sounds.
* Add envelope: Create 4 knobs to edit the amplitude envelope.
* Add filter: Adds a low pass filter and creates a cutoff and resonance knob for it.
* Add reverb: Adds a reverb effect and  creates two parameter knobs for it.

### Korg KMP/KSF

Since the KMP format can only contain 1 group of a multisample, sources with multiple groups are split up into several KMP files. Due to limitations of the format only uncompressed 8 or 16 bit samples up to 48kHz are supported.

### Korg wavestate/modwave (*.korgmultisample)

The korgmultisample format is currently used by the Korg wavestate and modwave keyboards. Files in that format can be opened with the Korg Sample Builder software and transferred to the keyboard.

Since the format supports only one group of a multisample, multiple files are created for each group available in the source. If there is more than one group in the source the name of the created file has the velocity range of the group added. Using that information a multisample with up to 4 groups can be created as Performance in the device.

## Native Instruments Kontakt

Writes a NKI file (see above) and puts all samples in a sub-folder with the same name. Currently, only the Kontakt 1 format is supported which does not contain any metadata information.

### SFZ

Writes a SFZ file (see above) and puts all samples in a sub-folder with the same name.

## TAL Sampler

Writes a TAL Sampler file (see above) and puts all samples in a sub-folder with the same name.

The implementation distributes the samples of the source groups across the 4 layers of TAL Sampler in such a way that the key and velocity splits do not overlap. This is a workaround for the fact that TAL Sampler does not support overlapping samples. Since groups have only the name and trigger type as attributes, which are not supported in TAL Sampler anyway, this should work in most cases. If there are still overlapping samples a warning is displayed.

### WAV files

Only stores the WAV files from the source format in a sub-folder. Use e.g. to extract the audio files from a SF2 file.
