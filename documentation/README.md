---
title: The ConvertWithMoss Manual
author: 
- Jürgen Moßgraber
header-includes:
    \usepackage{fancyhdr}
    \pagestyle{fancy}
geometry: margin=2.5cm
papersize: a4paper
date:   \today
...

<div style="page-break-after: always; visibility: hidden"> 
\pagebreak 
</div>

# Introduction & Installation

This tool converts multisamples in a [specific source format to a different destination format](README-FORMATS.md#). Furthermore, it can create multisample files from plain sample files like AIFF and WAV.

Additionally, the conversion process reads and writes metadata (name, category, creator, description and keywords) as well as envelopes and filter settings, if supported by the format. If the source format does not support metadata a guessing algorithm is applied based on the samples names.

Details about the specific converted parameters can be found in a [spreadsheet][1].

## Reporting bugs and asking questions

If you run into an error or you are stuck somewhere, feel free to get in touch. But make sure that you have read this manual and also have a look at the tutorial videos on my Youtube channel. To report bugs and/or ask questions write either to the ConvertWithMoss thread on KVR or create an issue in the ConvertWithMoss GitHub projects. All links can be found on the ConvertWithMoss [Download][2] page.

## Installation

[Download][2] and run the matching installer for your operating system.
After that you can start the application ConvertWithMoss.

### macOS issues

Since the build depends on the GitHub build infrastructure, you need a recent macOS version.
This is currently macOS 13 for Intel and macOS 14 for ARM based Macs. It might work down to macOS 11 but I cannot test that.

After installation macos will complain about different things when you try to run the application, which vary depending on your macOS version:

1. The application is unsafe to run since it is downloaded from the internet.
2. The application is unsafe to run since it is not signed by Apple.
3. The application files are corrupted.

To fix this, do the following:

* Run the application again and click away the error.
* Open the system settings and go to *Privacy & Security Settings*.
* Scroll to the very end, there should now be a message saying something like 'publisher of ConvertWithMoss could not be identified'.
* Click on the allow anyway button
* When you start ConvertWithMoss again you need to confirm again that you want to run the application.

If this did not work for any reason, try this:

Open the Terminal app and enter the application folder:

```sh
cd /Applications/ConvertWithMoss.app
```

Then remove the evil flag (Requires your administrator password):

```sh
sudo xattr -rc .
```

Since this seems not to work for everybody, there is another solution:

Temporarily, disable the Gatekeeper with

```sh
sudo spctl --master-disable
```

Open the application (should work now). Close it and enable Gatekeeper again to feel safe...

```sh
sudo spctl --master-enable
```

The application should now run also with Gatekeeper enabled.

Finally, have fun.

## Build from sources

Ensure to have the required `JVM`, `JRE`, `JavaFX` and `Maven` dependencies preinstalled and set the `JAVA_HOME` environment variable to specify which Java version to use; the minimum required version is `24`. Then use `mvn install` command to start the build.

See also the various build scripts in this directory as references on how to build the documentation and/or the application source files.

For Linux (BSD not tested) there is also a `Makefile` for build and install with the usual `make` and `make install` commands.

# Usage via the user interface

1. Select the source format on the left.
2. Select the source folder, which contains one or more multisamples in the selected source format. The files can also be located in sub-folders.
3. Select the destination format on the right.
4. Select the output folder where you want to create the multisamples. You can add a non-existing folder to the name, which then is automatically created. E.g. you could select the Desktop and then add a folder *Conversions*.
5. Choose the type of the created output format: either single presets, a preset library which contains all found source files, a performance which contains several presets with settings or finally a library of performances. Only some destination formats support libraries and performances, all others are greyed out.
6. Press the *Convert* button to start the conversion. The progress is shown with notification messages in the log area, which you should check for potential errors like defect source files, skipped folder, etc. This log is also written to a file in the output folder.

Alternatively, press *Analyse* to analyse all potential source file but not to write any files. Use this to check for errors before finally running the conversion.

## Options

* **Renaming**: Allows to rename multi-samples. Enable the checkbox to use this feature. If enabled select the file which contains the mapped names. The file is a simple text file in UTF-8 format (important if non-ASCII characters are used!). Each row contains one mapping. A mapping consists of 2 names separated either by ';' or ','. E.g. a row which contains "AcPno;Acoustic Piano" would name a multi-sample with the name "AcPno" into "Acoustic Piano" as output.
* **Create folder structure**: If enabled, sub-folders from the source folder are created as well in the output folder. For example, if I select my whole "Sounds" folder, there are sub-folders like `Sounds\07 Synth\Lead\01W Emerson'70 Samples`. In that case the output folder would contain e.g. `07 Synth\Lead\01W Emerson'70.multisample` if Bitwig multisample is selected as the destination format.
* **Add new files**: Starts the conversion even if the output folder is not empty. Duplicates will get unique names by adding numbers.
* **Dark Mode**: Toggles the user interface between a light and dark layout.

[1]: https://github.com/git-moss/ConvertWithMoss/blob/main/documentation/SupportedFeaturesSampleFormats.ods
[2]: https://mossgrabers.de/Software/ConvertWithMoss/ConvertWithMoss.html

# Usage via the command line interface (CLI)

Locate the ConvertWithMoss executable on your system. On **Windows** use the application ConvertWithMossCLI.exe which is in the same folder as ConvertWithMoss.exe.
Open a console window. As soon as you add attributes after the application it will run in CLI mode instead of opening the ConvertWithMoss application window.

First display all of the available attributes by typing:

```./ConvertWithMoss -h```

The following output is displayed:

```
Usage: ConvertWithMoss [-afhV] -d=DESTINATION [-l=LIBRARY] [-r=RENAME]
                       -s=SOURCE [-t=TYPE] [-p[=KEY=VALUE...]]... SOURCE_FOLDER
                       DESTINATION_FOLDER
      SOURCE_FOLDER        The source folder to process.
      DESTINATION_FOLDER   The destination folder to write to.
  -a, --analyze            If present, only analyzes the potential source files.
  -d, --destination=DESTINATION
                           The destination format.
  -f, --flat               If present, the folder structure is not recreated in
                             the output folder.
  -h, --help               Show this help message and exit.
  -l, --library=LIBRARY    Name for the library. Set to create a library.
  -p=[KEY=VALUE...]        Key-value pairs in the form -pkey1=value1,
                             key2=value2,...
  -r, --rename=RENAME      Configuration file for automatic file renaming.
  -s, --source=SOURCE      The source format.
  -t, --type=TYPE          Set to either 'preset' (the default if absent) or
                             'performance' (without the quotes).
  -V, --version            Print version information and exit.
```

The parameters should be easy to understand since they are identical to what you can do with the user interface.
Here is an example for a conversion from Kontakt NKI files to 1010music format:

```./ConvertWithMoss -s nki -d 1010music D:\MySampler\Kontakt C:\ConversionOutput```

To get a list of the available detectors simply set a non-existing one like this (same for the creators):

```./ConvertWithMoss -s whatever -d 1010music D:\MySampler\Kontakt C:\ConversionOutput```

All configuration settings for the detector and the creator are available as well. These settings are can be applied with the -p attribute and then adding a list of key/value pairs. To get a list of the available settings for the selected detector and creator simply add an illegel one like this:

```./ConvertWithMoss -s nki -d 1010music -pKey=Value D:\MySampler\Kontakt C:\ConversionOutput```

You will get the following output:

```
Unknown parameter: 'key'.
Accepted source parameters: [NkiPreferFolderName, NkiDefaultCreator, NkiCreators]
Accepted destination parameters: [1010musicWriteBroadcastAudioChunk, 1010musicWriteInstrumentChunk, 1010musicWriteSampleChunk, 1010musicRemoveJunkChunk, 1010musicInterpolationQuality, 1010musicResampleTo2448, 1010musicTrimStartToEnd]
```

The names of the parameters should be easy to match when looking at the ConvertWithMoss user interface. Checkboxes are boolean value which can be set with 0 and 1, for example:

```./ConvertWithMoss -s nki -d 1010music -p1010musicResampleTo2448=1 D:\MySampler\Kontakt C:\ConversionOutput```

Multiple settings are concatenated by using a comma:

```./ConvertWithMoss -s nki -d 1010music -pNkiDefaultCreator="Klaus Meier",1010musicResampleTo2448=1 D:\MySampler\Kontakt C:\ConversionOutput```

Finally, an example for creating a library of performances:

```./ConvertWithMoss -s nki -d ysfc -l Pads -t performance D:\MySampler\Kontakt C:\ConversionOutput```
