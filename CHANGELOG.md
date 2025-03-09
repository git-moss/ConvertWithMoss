# Changes

## 12.2.0

* Fixed: Sample files with problematic characters in their name got updated accordingly but not the references to them.
* Ableton ADV
  * Fixed: Reading: Velocity range settings did overwrite the key range settings.
* Kontakt 1/2
  * Fixed: Improved detection of metadata like name, category and description.
* Kontakt 5-7
  * New: Reading amplitude and pitch envelopes is now supported.
  * New: Added automatically finding samples with wrong absolute or relative paths.
  * Fixed: Monolith NKIs which referenced a NCM file more than once could not be converted.
  * Fixed: AIFF files could be treated as WAV files.
* WAV - Read
  * Fixed: Split stereo files were not combined to stereo file instead only the left side was included.
  * Fixed: Samples could (still) have doubled file endings (.wav.wav).
* Waldorf QPat
  * Fixed: Split stereo files were not combined to a stereo file instead only the left side was included.
  * Fixed: Fine tune was applied in the wrong direction.

## 12.1.0

* Fixed: Most of the created files had two dots before the file extension.
* Fixed: Output folder was not checked for existence when only Analyze was executed but it is required now for the log file.
* Fixed: Crash when left/right WAV files should be combined to stereo.
* DecentSampler
  * Fixed: Added workaround for absolute sample paths in dslibrary files.
* Kontakt
  * New: Improved category detection, especially for Instruments in NKM files.
* Kontakt 1/2
  * Fixed: Improved lookup of sample files which are referenced by absolute paths.
  * Fixed: Added support for file paths which include encoded UTF-8 characters in the format of %xxxx.
* Kontakt 5+
  * Fixed: File could not be read if a sound description was set.

## 12.0.0

* New: Implemented a new logging component. Much faster and does not crash anymore.
* Fixed: Sample files with illegal file system characters could not be created.
* Yamaha YSFC
  * New: Performances can be created in destination libraries for Montage and MODX/MODX+ (optional).
  * New: Performance data of Montage and MODX/MODX+ can now be read and applied.
  * New: Waveform data of Montage M (*.Y2U) can now be read as well.
  * New: Added progress logging when extracting samples from a library.
  * Fixed: Library files of pre-Montage models were not read correctly.
  * Fixed: Waveform panorama was not always correct.

## 11.7.0

* New: Writing of samples can now be cancelled as well.
* Fixed: Logger text is now cleared regularly to prevent a crash. To have the log still available, all messages are now logged into a file ConvertWithMoss.log which is created in the output directory.
* Kontakt
  * Fixed: Regression: Reading Kontakt 5-7 file lists were broken.
  * Fixed: NCW files are now only read when needed for writing and the memory is freed up directly afterwards to support NKIs which reference a very large amounts of NCW files.
* Korg KMP
  * New: KSF files which reference another KSF file are now read properly.
  * New: Reading: Applied +12dB option.
  * New: KMP/KSF files which contain SKIPPEDSAMPLE as a filename are now ignored (conversion was canceled previously).
* Sample Files
  * New: Notify about the number of sample files found in a folder before the mapping starts.

## 11.6.0

* EXS24
  * New: Added support for round-robin. Files are larger now since this info is in an additional block.
  * Fixed: Reading: group indices were off by 1.
* SFZ
  * New: Added support for round-robin on group-level (not only zone-level).
* Kontakt
  * New: Added support reading for Kontakt 8
  * New: Added support for reading new file lists in 7.10+.
* Korgmultisample
  * Fixed: Files created with Sample Builder 1.2.7 could not be read.
  * Fixed: If a korgmultisample file was located in a subfolder, its samples could not be found.

## 11.5.0

* Added support for Waldorf Quantum MkI/MkII, Iridium, Iridium Core sample format.
* Checking if destination folder is empty ignores now OS thumbnail files like .DS_Store on MAC and Thumbs.db on Windows.
* Decent Sampler
  * New: Added logging of line/column numbers with the error if the dspreset file cannot be parsed.
* EXS24
  * Fixed: Parameters were not correctly read/written. Already created files should be created again.

## 11.4.0

* Decent Sampler
  * New: Added support dspresets using AIFF files.
  * New: Added option to (not) log unused XML elements and attributes. This is off by default since the warnings confused many users.
  * New: Tweaked envelope times a bit.
  * New: Removed groups which are disabled (since there is no way to translate the modulated activation to other formats).
  * New: Filters on group level are now read as well.
  * New: Improved mapping of round-robin
  * Fixed: Added support for note numbers with a prefixed 0 (e.g. '060').
  * Fixed: Global filter was not read.
* EXS24
  * New: Removed excessive logging when searching for a sample.
  * Fixed: Data chunk offset was mostly not correctly written to EXS.
* Sample Files
  * New: Implemented workaround for reading WAV files with a non-standard chunk at the end.
* Sf2
  * New: Added option to (not) log unused SF2 generators. This is off by default since the warnings confused many users.

## 11.3.0

* Ableton ADV
  * Fixed: Date of last sample change was in milli-seconds but needs to be seconds. Ableton 12 refused to load the file.
* EXS24
  * Fixed: Reading failed due to a not-removed log-output.
* MPC Keygroups
  * Fixed: The loop crossfade was not converted correctly in both directions.
* Sample Files
  * New: Implemented workaround for reading broken WAV files which have the wave data after the data chunk.
  * Fixed: Sample detection stopped already when no files were found for one sample format.
  * Fixed: Do not stop detection when no common name could be found among the input samples but use the name of the first sample.

## 11.2.0

* New: Source and destination path stores now the last 20 selections.
* New: Implemented loading of AIFF files since some crashed the Java Sound API.
* DecentSampler
  * New: Added option to create a dsbundle as output format.
  * New: Added option to combine all detected multi-sample sources into one library or bundle.
* Korg KMP
  * New: Proper support for stereo files. Turns out these workstations cannot play back real stereo files, therefore, a stereo file needs to be split into 2 KMP files.
  * New: Additionally, a KSC file is created to ease loading of stereo files.
  * New: Added 2 options to increase the volume.
  * New: Added option to split source groups into individual KMPs.
  * New: Increased sample rate limit to 48kHz (was 44.1kHz).
  * New: Improved creating unique folder names for KMP files.
  * Fixed: Zones needed to be ordered by their upper key-limit otherwise the file did not work and could even crash the workstation.
  * Fixed: Reverse playback state was not read correctly.
  * Fixed: Prevent several characters in file names which could crash the workstation.
* MPC Keygroups
  * New: Added option to create up to 8 layers which is now supported with MPC Firmware 3.4.
* SFZ
  * New: Added support for reading SFZ files which reference other SFZ files with #include statements.
  * New: Added option to (not) log unsupported SFZ opcodes. This is off by default since the warnings confused many users.
* Soundfont 2
  * New: Added options to add the filename and the preset number to the resulting destination file names.

## 11.1.0

* New: AIFF/WAV files are now lazy loaded which keeps the memory usage down.
* EXS24
  * New: Increased the directories upwards search option to 6.
* KMP
  * Fixed: Creation did crash.
* Sample Files
  * New: Aggregated AIFF and WAV sources into 'Sample Files' source. Added AIFF, FLAC, NCW and OGG files as well. All types can be selected and detected at once.
  * Fixed: Note detection from file names could be wrong when flat notes were part of it (e.g. Eb2 was detected as B2).
  * Fixed: Category detection on sample file names did not always work

## 11.0.0

* Added support for Yamaha YSFC format (read/write: Montage, MODX/MODX+, read: Motif XS, Motif XF, MOXF).
* Bitwig Writing
  * New: Support for RIFF chunk updates (fixes issues with certain MPC WAV files as source).

## 10.6.0

* All formats
  * New: If multi-samples with the same name are created during a conversion process, unique postfixes are now appended.
  * Fixed: Average bytes per second was not stored correctly in WAV files.
* Kontakt - Reading
  * New: Support for NCW files with 32-bit float samples.

## 10.5.0

* Several accessibility improvements and fixes: 
  * Button mnemonics were partially broken.
  * Improved order of tabulator traversal.
  * Added more tooltip info
  * Set default button states, can be execute by pressing *Return*.
* Fixed: Switching off dark mode required a restart.
* All formats
  * Fixed: Fixed a crash when envelope was not set.
* AIFF/WAV
  * Fixed: Velocity layer information was removed from file names which lead to duplicate filenames.
* Reason NN-XT
  * Fixed: Reading/Writing negative tunings was broken.

## 10.2.0

* Kontakt 1-4, MPC Keygroups, Soundfont 2, TAL Sampler, TX16Wx
  * New: Added support for amplitude and filter velocity modulation.
* Kontakt - Writing
  * New: Improved pitch envelope.
* Kontakt 4.2-7 - Reading
  * Fixed: Group volume, panorama and key-tracking was not applied.
* EXS, SXT, TX16Wx - Reading
  * New: Speed up finding samples.
  * Fixed: If levels to search upwards was set to 0, it did not search downwards.
* WAV
  * Fixed: Reading/writing the pitch fraction field of the sample chunk was not always correct.

## 10.1.0

* All formats
  * Fixed: Increased the heap memory to 64GB to support larger source files.
  * Fixed: WAV files in 32-bit float can now be converted to 16-bit PCM (workaround for bug in Java AudioSystem).
* 1010music format - Writing
  * New: Added an option to trim samples with a delayed start.
* disting EX - Writing
  * New: Added an option to trim samples with a delayed start.
  * Fixed: The MIDI note for the switch (SW) was off by 1 octave (disting assumes C3 as MIDI note 48 instead of 60). This caused playback issues.
  * Fixed: Release trigger groups are now removed from the output since the distingEX does not support release triggers.
* SFZ
  * Fixed: Pitch bend was by factor 100 too small (semi-tones instead of cents).

## 10.0.0

* Added support for disting EX multi-sample preset format.
* All formats
  * New: Added support for amplitude and filter velocity modulation (1010music, Ableton ADV, SFZ). Only amplitude: DecentSampler, EXS24.
  * Fixed: Improved handling of missing root note information.
* 1010music format - Reading
  * Fixed: Samples could not always be found.
* EXS - Writing
  * Fixed: Filter cutoff was calculated incorrectly and could lead to silent patches.
  * Fixed: Envelope parts which were not set were handled incorrectly.
* SFZ - Reading
  * Fixed: Attributes of previous converted SFZ did leak into next conversion.
  * Fixed: Only create a filter when there is at least a cutoff or filter type attribute present.

## 9.5.0

* Added support to write Soundfont 2.
* All formats
  * Fixed: In rare cases key-ranges could be stored incorrectly if not fully present in the source file
* 1010music format - Writing
  * New: Set samtrigtype to zero if one-shot.
  * Fixed: Writing sample start, length and reverse were missing.
* DecentSampler - Read
  * Fixed: The sub-folder which contains the library/preset was added to the name which could cause issues in the destination format.
* Sf2 - Reading
  * Fixed: Pitch envelope was only set when a filter was present as well.
* TX16Wx - Read
  * Fixed: samples could sometimes not be found on Macos/Linux
* WAV - Read
  * New: Metadata is now read from info sub-chunks and stored in the Comment metadata field.
* WAV - Write
  * Fixed: Update of Broadcast Audio chunk did fail if no date/time metadata was set.
  * Fixed: Destination file name could be empty if 'prefer folder name' was selected.

## 9.0.1

* Ableton - Read/Write
  * Fixed: The template contained and error and resulting ADV files could not be loaded in Ableton.
  * Fixed: Names from ADG files were not unique.

## 9.0.0

* New: Added support for Ableton ADV (read/write) and ADG (only read) files.
* New: Added support for creating multi-samples from AIFF files and the contained metadata.
* New: Envelope improvements
  * SFZ: Added attack, decay and release slope attributes to amplitude, filter and pitch envelopes.
* DecentSampler - Read
  * New: Read/write amplitude attackCurve, decayCurve and releaseCurve attributes.
  * Fixed: When processing a dslibrary file the name of the library file was always used as the destination preset name instead of the dspreset name. Therefore, only one preset from the library was created.
* Kontakt 1-2 - Read/Write
  * New: Added attack curve to amplitude, filter and pitch envelopes.
* MPC Keygroups - Read/Write
  * New: Added attack, decay and release slope attributes to amplitude, filter and pitch envelopes.
* TX16Wx Read/Write
  * New: Added attack, decay and release slope attributes to amplitude, filter and pitch envelopes. Added all envelope levels.
* WAV - Read
  * New: If the name ends with a dash it is removed.
  * Fixed: Samples could have doubled file endings (.wav.wav).
  * Fixed: If Instrument chunks were present in the files, the conversion did not work (there was an error shown that the MIDI note could not be detected which was misleading as well).

## 8.5.1

* Kontakt - Reading
  * Fixed: In Kontakt 4.2 to 7 the loop data was not read correctly. This could create loops of length 0 for One-Shots.
* Multisample - Write
  * Fixed: Bitwig could not process the ZIP compressed samples due to an added info field. Additional info is removed again.

## 8.5.0

* Added support for reading and writing CWITEC TX16Wx (*.txprog) files.
* Added support for reading and writing Propellerhead Reason NN-XT (*.sxt) files.
* All formats
  * New: Added chunk update settings to all output formats that reference WAV files.
  * Fixed: Fixed some issues with conversion of filter and pitch envelope modulation depth.
* Decent Sampler
  * New: Minimum version is now set to "1.11".
  * New: Added support for new filter types: lowpass, lowpass_1pl, bandpass, highpass, peak and notch.
  * New: Added filter envelope.
  * New: Added support for panorama.
  * New: Removed all knobs except reverb settings to be able to set these parameters on the samples level.
* Kontakt - Reading
  * New: Use category detection when category is set to 'New'.
* SFZ - Writing
  * Fixed: The length of the loop crossfade was calculated incorrectly.
* MPC - Writing
  * New: Set filter on groups from 1st zone of the group instead of the 1st zone of the 1st group.

## 8.0.0

* Added support for reading and writing Logic EXS24 files.
* Fixed: Font color of logger in light mode was wrong.
* NKI - Read
  * Fixed: A proper error message will be output if a sample file is missing.

## 7.5.0

* All formats
  * New: Implemented workaround to accept AIFF files with an ending of 'aiff' (instead of only 'aif').
* 1010music format - Writing
  * New: Added option to convert samples to 24bit/48 kHz which saves a bit on processor power on the 1010music devices.
* Korg KMP - Writing
  * Fixed: Loop points were not correct when the source sample was not 44.1kHz.
* SFZ - Reading
  * New: Added support for SFZ files which use sample files in OGG or FLAC format.
* SFZ - Writing
  * New: Added option to create FLAC samples.
  * New: Added options to write instrument, sample and broadcast audio chunks.
* TAL Sampler - Reading
  * Fixed: Metadata configuration widgets were missing.

## 7.4.0

* Added support for 1010music format (blackbox, tangerine, bitbox).
* All formats
  * New: Support for creation date/time in formats which support it.
  * New: Unsupported WAV file metadata chunks are kept when read/written.
  * New: Samples in ZIP files get the modification date of the multi-sample source.
  * New: Added 'Hammond' as organ synomym and 'Ambient' and 'Atmo' as pad synonyms in category detector.
  * Fixed: Tab labels were not visible on Linux.
* WAV - Reading
  * New: Reads metadata (originator, description, creation date/time) from the broadcast audio chunk (if present) of the 1st WAV file.
* WAV - Writing
  * New: Added options to write instrument, sample and broadcast audio chunks.
  * Fixed: WAV file chunks were not aligned to multiples of 2.
* SFZ, DecentSampler, MPC Keygroup, TAL Sampler - Reading
  * New: Reads metadata (originator, description, creation date/time) from the broadcast audio chunk (if present) of the 1st WAV file.
* SFZ, DecentSampler, MPC Keygroup, TAL Sampler - Writing
  * New: Writes metadata (originator, description, creation date/time) to the broadcast audio chunk of all WAV files.
* MPC - Writing
  * Fixed: The sample chunk of a MPC destination WAV file was missing the number of loops value.

## 7.3.0

* Added support for TAL Sampler format (reading + writing).
* Improved user interface.
* Sf2 - Reading
  * Fixed: 24 and 16 bit detection were flipped and produces an exception.
* SFZ - Reading
  * New: AIFF files can be used as input.
* Kontakt - Reading
  * Fixed: Zone tuning was not set correctly.
  * Fixed: If a file was referenced more than once in a monolith, all of them had the same zone settings.
* Korg KMP - Reading
  * Fixed: Pitch tracking was inverted.

## 7.2.1

* DecentSampler - Writing
  * Fixed: Tuning was not set correctly
* Kontakt - Reading
  * New: Support for Kontakt 7.6.
  * Fixed: Kontakt 5-7: Sample zones from monolith files did miss all settings.
  * Fixed: Kontakt 5-7: Pitch was not handled correctly.

## 7.2.0

* Kontakt - Reading
  * New: Support for Kontakt 4.2 and 5-7 NKMs.
  * Improved: Detection of encryption.
  * Fixed: Improved Kontakt 5-7 file path reading and handling.

## 7.1.1

* Kontakt - Reading
  * Fixed: Regression from 7.1.0 - Kontakt 5-7 files could not be read at all.
  * Fixed: Kontakt 5-7 relative paths can contain redirections to parent directories which were not added.
  * Fixed: Support for Kontakt 2 files which contain an XML document with a leading UTF-BOM.

## 7.1.0

* Fixed: Loops could be incorrect if sample rate was not 44.1kHz and audio file metadata could be wrong as well in that case.
* Korg KMP/KSF
  * New: Convert source samples to support bit resolutions (8, 16) and maximum sample rate of 48kHz.
  * Fixed: Improved check for duplicated DOS file names and unique ones are now created.
* Kontakt - Reading
  * New: Kontakt 2-4 monoliths in big-endian encoding are now supported.
  * New: Added support for alternative Kontakt 1 file-ex sample path reference.
  * New: Added support for Kontakt 1.5 files.
  * Improved: Finding samples when absolute sample file paths are used.
  * Fixed: Fixed several issues with Kontakt 2-4 monoliths.
  * Fixed: NCW files with mid/side encoding were not handled correctly.

## 7.0.0

* '(Velocity) Layers' have been renamed to 'Groups' in the user interface.
* Fixed: Some issues with reading WAV files.
* MPC keygroups
  * Improved: Loop information is written to the WAV file which seems to be used by the MPC.
* Native Instruments NKI files - Reading
  * New: Conversion of Kontakt 4.2 - 7 files: metadata, zones, loops, NCW and monoliths files work but no support for envelopes and filters.
* Native Instruments NKI files - Writing
  * Fixed: Created Kontakt 1 files could be opened with Kontakt but not saved again due to the use of forward slashes for sample paths. Backward slashes are used now.
* Sf2 - Reading
  * New: Use filename (without ending) for instruments named 'NewInstr'.
  * Fixed: Panorama setting was not corrected when mono files were combined to stereo.
  * Fixed: If left and right sample had different lengths, the shorter sample had data from the following sample added.

## 6.3.0

* Default volume envelopes are applied based on the detected category if none is present.
* Decent Sampler
  * Fixed: Read: Wrong velocity range (0-0) when velocity settings were missing.
* MPC keygroups
  * Fixed: Read/Write: Improved mapping of envelopes.
  * Fixed: Write: Pitch was not correct.
* SFZ
  * Fixed: Increased allowed range of pitch values.
  * Fixed: Panorama was not read / written.

## 6.2.1

* Decent Sampler - Reading
  * New: Implemented workaround for invalid XML document (contains comments before XML header).
  * New: Added support for notes which are formatted as text instead of MIDI numbers.
  * Fixed: Groups were not detected.

## 6.2.0

* Added support for reading Native Instruments NKM files (Kontakt Multis) in Kontakt version 1-4.
* Native Instruments NKI files - Reading
  * For Kontakt 5+ NKI files the exact version number is displayed (but reading is still not supported).
* Native Instruments NKI files - Writing
  * New: Intensity of default envelopes is now set to 1 (was 0).
  * New: The default pitch envelope has now 0 for all parameters.
  * Fixed: Envelope hold and decay were flipped.

## 6.1.0

* Tabs are now ordered alphabetically.
* Bitwig Multisample
  * Fixed: If a loop was set to Off it was still applied.
* Native Instruments NKI files
  * New: Added support to write NKI files in Kontakt 1 format.
  * New: Added support for AIFF files (will be converted to WAV).
  * New: Added support for reading Kontakt NKI files stored in big-endian format. But could not test with any monolith file, therefore an error is shown.
  * New: Added support for pitch envelopes.
  * New: Added support for filter settings and cutoff envelope.
  * Fixed: High velocity crossover value did overwrite low velocity crossover.
* Korg KMP
  * Fixed: Extracting groups into single KMP files did overwrite the KSF sample files.

## 6.0.0

* New: Added option to rename multi-samples (thanks to Philip Stolz).
* New: Improved mapping of envelopes to MPC keygroups (thanks to Philip Stolz).
* New: Added support for reading Kontakt NKI files (only the format of the versions before Kontakt 4.2 are supported, thanks to Philip Stolz).
* Fixed: Added missing reading of panorama value.

## 5.2.1

* Fixed: Bitwig Multisample files with old layer formatting had duplicated layers as output.
* Fixed: Missing trigger types in Decent Sampler files did show an unnecessary error.

## 5.2

* New: Added support for trigger type (attack, release, first, legato) for SFZ, Decent Sampler, MPC Keygroups (only attack, release on instrument).

## 5.1

* New: WAV files are added as destination format e.g. in case you only want to extract WAV files from SF2 files.
* New: Store WAV ending in lower-case when converted from MPC Keygroups.
* Fixed: (Bitwig) Multisample files must not be compressed for faster access. Bitwig can also handle compressed files but other hosts supporting the format might fail. If you created Multisample files with this converter, simply run a new conversion on them with Multisample as source and destination to fix the issue.
* Fixed: Created (Bitwig) Multisample metadata file contained wrong group indices (off by 1).

## 5.0

* New: Added reading/writing of Korg KMP/KSF files.
* New: Added icons to the buttons.

## 4.7.1

* Fixed: Name detection was broken (if 'Prefer folder name' was off).
* Fixed: Akai XPM: Velocity range was not read correctly.

## 4.7

* New: WAV: Layer detection pattern fields are now checked to contain a '*'.
* Fixed: WAV: Having the layer detection pattern field empty led to undetectable MIDI notes.
* Fixed: WAV: The order of potential note names in file names could have been wrong and therefore a detection could fail.

## 4.6

* New: SF2, SFZ, MPC: Support for Pitch bend range settings.
* New: SF2, SFZ, Decent Sampler, MPC: Support for filter settings (incl. filter envelope).
* New: SF2, SFZ, MPC: Support for Pitch envelope settings.
* Fixed: SFZ: Logging of unsupported opcodes did add up.
* Fixed: SFZ: Sample paths in metadata now always use forward slash.
* Fixed: Decent Sampler: Sample files from dslibrary could not be written.
* Fixed: Decent Sampler: Tuning was not read correctly (off by factor 100).
* Fixed: Decent Sampler: Round-robin was not read and not written correctly.

## 4.5

* New: Support for amplitude envelope: Decent Sampler, MPC Keygroups, SFZ: read/write; SF2: read
* New: Decent Sampler: Support 'tuning' and 'groupTuning' on group tags as well as 'globalTuning' on the groups tag.
* New: SF2: Support initialAttenuation generator.
* Fixed: SF2: Sample files extracted from Sf2 were always set as 44.1kHz.
* Fixed: SFZ: Presets with illegal characters were corrected for the sample folder name but not in the SFZ file reference.
* Fixed: SFZ: Loop attributes were not read when loop_type was missing.
* Fixed: SFZ: Loop attribute alternative names loopstart, loopend were not read.
* Fixed: SFZ: Loop was not set to off when no loop was present.
* Fixed: MPC Keygroups: Loop end was not set correctly if different from sample end.
* Fixed: Decent Sampler: group name was wrongly reported as not supported.
* Fixed: WAV: Check of sample chunks when combining mono to stereo does now only require to have the same pitch.
* Fixed: Error message for left/right mono samples with different pitch was missing.

## 4.0

* New: Added reading/writing of Korg Wavestate (.korgmultisample) files.
* New: Added reading of Akai MPC Keygroup files.
* New: Added the WAV creator detector parameters to SFZ, Decent Sampler and MPC Keygroups as well.
* New: Added a dark mode.
* Fixed: WAV: Detection of root note from sample names could be wrong when multiple options apply and the last one was wrong.
* Fixed: SFZ: Ignore illegal characters in SFZ files.
* Fixed: Bitwig multisample: Key tune parameter was not stored correctly.

## 3.2

* New: Support WAV files in extensible format.
* New: SFZ: Create names for groups without a name.
* New: SFZ: Check for trigger opcode but only 'attack' is supported.
* Fixed: SFZ: Key values which did not use MIDI note numbers were not read (e.g. c#3).
* Fixed: Improved handling of large chunks in WAV files.
* Fixed: Fixed issues with sample paths created on different OS.
* Fixed: Fixed some issues with error message formatting.
* Fixed: Do not create the top source folder in the output folder (only the sub-folders).

## 3.1

* New: Akai MPC Keygroup - round-robin groups are now converted (up to 4).
* New: Akai MPC Keygroup - more than 4 groups can now be converted; this creates multiple keygroups.
* Fixed: Akai MPC Keygroup - root notes of samples were off by 1.

## 3.0

* New: Added writing of Akai MPC Keygroup files.

## 2.2.0

* New: DecentSampler creator got some options to choose which controls to create and to make the sound monophonic.
* Fixed: WAV detector: Upper group was not always 127.

## 2.1.1

* Fixed: WAV detector did not read loops from WAV files.

## 2.1

* Fixed: WAV detector did also deliver results for empty folders.
* Fixed: Setup for created DecentSampler Filter and Reverb is working now.

## 2.0

* New: Added reading and writing of DecentSampler preset and library files.
* New: Improved note detection from file names.
* Fixed: SFZ detector - global_label was not read.
* Fixed: SFZ parser - Comments at line end were not removed which conflicted with attribute values.
* Fixed: WAV detector - Crash if left and right mono sample had different lengths.
* Fixed: Creating folders for SFZ could raise an exception.
* Fixed: Source and destination tabs could be removed.
