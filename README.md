# ConvertWithMoss

Converts multisamples in a specific source format to a different destination format. Furthermore, it can create multisample files from plain WAV files.

Additionally, the conversion process reads and writes metadata (name, category, creator, description and keywords) as well as envelopes and filter settings, if supported by the format. If the source format does not support the information a guessing algorithm is applied based on the samples names.

Furthermore, samples can be grouped into groups and key ranges. Each sample can have 1 or no loop and parameters for pitch and different playback parameters.

## Supported formats

The following multisample formats are supported as the source and destination formats (except SF2 is currenlty only a source):

1. 1010music blackbox, tangerine, bitbox (preset.xml)
2. Akai MPC Keygroups (*.xpm)
3. Bitwig Studio multisample (*.multisample)
4. DecentSampler (*.dspreset, *.dslibrary)
5. Korg KMP/KSF (*.KMP)
6. Korg wavestate/modwave (*.korgmultisample)
7. Native Instruments Kontakt (*.nki) 1-4
8. SFZ (*.sfz)
9. SoundFont 2 (*.sf2)
10. WAV files (*.wav)

See [README-FORMATS.md][1] document for more details. Even more details about the specific converted parameters can be found here:

https://github.com/git-moss/ConvertWithMoss/blob/main/documentation/SupportedFeaturesSampleFormats.ods

## Installation

[Download][2] and run the matching installer for your operating system.
After that you can start the application ConvertWithMoss.

> **Note**
>
> macOS users should read [README-MACOS.md][3] document for important notices.

## Build from sources

Ensure to have the required `JVM`, `JRE`, `JavaFX` and `Maven` dependencies preinstalled and set the `JAVA_HOME` environment variable to specify which Java version to use; the minimum required version is `17`. Then use `mvn install` command to start the build.

See also the various build scripts in this directory as references on how to build the documentation and/or the application source files.

For Linux (BSD not tested) there is also a `Makefile` for build and install with the usual `make` and `make install` commands.

## Usage

1. Select a source folder, which contains one or multiple folders with multisamples in the selected source format. The files can also be located in sub-folders.
2. Select the output folder where you want to create the multisamples. This folder must be empty. You can add a non-existing folder to the name, which then is automatically created. E.g. you could select the Desktop and then add a folder *Conversions*.
3. Press the *Convert* button to start the conversion. The progress is shown with notification messages in the log area, which you should check for potential errors like defect source files, skipped folder, etc.

Alternatively, press *Analyse* to execute the same process as *Convert* but does not write any files. Use this to check for errors before finally running the conversion.

### Options

* **Renaming**: Allows to rename multi-samples. Enable the checkbox to use this feature. If enabled select the file which contains the mapped names. The file is a simple text file in UTF-8 format (important if non-ASCII characters are used!). Each row contains one mapping. A mapping consists of 2 names separated either by ';' or ','. E.g. a row which contains "AcPno;Acoustic Piano" would name a multi-sample with the name "AcPno" into "Acoustic Piano" as output.
* **Create folder structure**: If enabled, sub-folders from the source folder are created as well in the output folder. For example, if I select my whole "Sounds" folder, there are sub-folders like `Sounds\07 Synth\Lead\01W Emerson'70 Samples`. In that case the output folder would contain e.g. `07 Synth\Lead\01W Emerson'70.multisample` if Bitwig multisample is selected as the destination format.
* **Add new files**: Starts the conversion even if the output folder is not empty but only adds files which are not already present.
* **Dark Mode**: Toggles the user interface between a light and dark layout.


[1]: README-FORMATS.md
[2]: https://mossgrabers.de/Software/ConvertWithMoss/ConvertWithMoss.html
[3]: README-MACOS.md
