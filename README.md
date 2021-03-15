# Sample Converter

Converts multi-samples in a specific source format to a different destination format. Furthermore, it can create multi-sample files from plain WAV files.

## Installation

Run the matching installer for your operating system. After that you can start the application SampleConverter.

## Usage

1. Select a source folder, which contains one or multiple folders with multi-samples. The files can also be located in sub-folders.
2. Select the output folder where you want to create the multisamples. This folder must be empty. You can add a non-existing folder to the name, which then is automatically created. E.g. you could select the Desktop and then add a folder *Conversions*. If *Create folder structure* is enabled, sub-folders from the source folder are created as well in the output folder. For example if I select my whole "Sounds" folder, there are sub-folders like "Sounds\07 Synth\Lead\01W Emerson'70 Samples". In that case the output folder would contain e.g. "07 Synth\Lead\01W Emerson'70.multisample" if Bitwig multisample is selected as the destination format.
3. Press the *Convert* button to start the conversion. The progress is shown with notification messages in the log area, which you should check for potential errors like defect source files, skipped folder, etc. Alternatively, press *Analyse* to execute the same process as with *Convert* but does not write any files. Use this to check for errors before really running the conversion.

The following multi-sample formats are supported as the source format:

1. WAV files

The following multi-sample formats are supported as the destination format:

1. Bitwig Studio multisample
2. SFZ (see https://sfzformat.com/)

## Source formats

The following multi-sample formats can be the source of a conversion.

### Plain WAV files

This is not a multi-sample format but a clever algorithm tries to detect the necessary information for a multi-sample file from the information present in the WAV files or their names.

All WAV files located in the same folder are considered as a multisample. You can also select a top folder. If you do so, all sub-folders are checked for potential multi-sample folders.
The algorithm tries to detect as much metadata as possible from the WAV files:

* Notes are first detected from the sample chunk in the wave file (if present). If this is not set different parser settings are tried on the file name to detect a note name (or MIDI note value).
* The category is tried to be extracted from the file name. If this fails it tries with the folder names (e.g. you might have sorted your lead sounds in a folder called *Lead*). Furthermore, several synonyms and abbreviations are considered (e.g. Solo as a synonym for Lead).
* Characterizations like *hard* are tried to be extracted with a similar algorithm as for the category.

#### Velocity layers

Detected velocity layers will be equally distributed accross the velocity range. E.g. if 2 layers are detected the first will be mapped to the velocity range of 0-63 and the second to 64-127.

* Detection pattern: Comma separated list of patterns to detect velocity layers. The pattern must contain a star character ("*"), which indicates the position which contains the layer number.
* Order of layer numbering: Enable to map velocity layers inversed. This means that the highest number will be mapped to the lowest velocity range.

#### Options

* Prefer folder name: If enabled the name of the multisample will be extracted from the folder instead of the sample names.
* Default creator: The name which is set as the creator of the multisamples, if no creator tag could be found.
* Creator tag(s): Here you can set a number of creator names, which need to separated by comma. You can also use this to look up other things. For example I set the names of the synthesizers which I sampled. My string looks like: "01W,FM8,Pro-53,Virus B,XV" (without the quotes).
* Crossfade: You can automatically create crossfades between the different note ranges. This makes especially sense if you only sampled a couple of notes. Set the number of notes, which should be crossfaded between two samples. If you set a too high number the crossfade is automatically limited to the maximum number of notes between the two neighbouring samples.
* Postfix text to remove: The algorithm automatically removes the note information to extract the name of the multisample but there might be further text at the end of the name, which you might to remove. For example the multi-samples I created with SampleRobot have a layer information like "_ms0_0". You can set a comma separated list of such postfix texts in that field.

#### Mono Splits

Stereo samples might be split up into 2 mono files (the left and right channel). This tool will combine them into a stereo file.

* Left channel detection pattern: Comma separated list of patterns to detect the left channel from the filename. E.g. "_L".
* Only WAV files in Mono or Stereo are supported.
