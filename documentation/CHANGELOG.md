# Changes

## 20.2.0 (work-in-progress)

* New: Added support for the E-mu Emulator II (IMG, EMUIIFD, HFE). One disk becomes one multi-sample: the key map of the bank gives the zones with their key ranges and root keys and each voice its name, its loop and its audio, which is expanded from the companded bytes the sampler feeds to its AM6072 DAC. A bank which is larger than one floppy is reported as continuing on another disk. Read only.
* New: Added support for the Roland S-10/S-220/MKS-100 System Exclusive files (*.syx) - read only
* New: A note is logged when a loop of a converted preset audibly clicks at its wrap-around point (the step back to the loop start is many times the normal movement of the waveform and a substantial part of the level). Sample libraries ship such loops surprisingly often - 17 of 152 presets across three commercial Ensoniq libraries - and without the note the first hint is the converted preset ticking on the destination device. Nothing is changed: the loop is written as the source authored it, and the note points to the snap-to-zero-crossing and loop cross-fade processing options which remove such clicks. A loop which already has a cross-fade is not reported.
* New: The Analyse run can log for every found multi-sample what it contains (option "Log analysis details", on the command line `-ad`): the mapping of its zones with their sample format, loops with their cross-fades, envelopes, LFOs and the filter with its modulators. Only attributes which a source actually uses are logged, so searching the log finds the sources which use a specific feature - e.g. all presets of a library with a filter envelope, a loop cross-fade or a release trigger. The details describe the source as it was read, before any processing is applied.
* New: A note is logged when samples are re-sampled because the destination format does not support their sample rate or bit resolution - e.g. a 24 bit / 48 kHz sample which is written as a Waldorf Quantum/Iridium preset with its 'Re-sample to 16bit/44.1kHz' option enabled, or written for a device whose hardware dictates the format of its samples. So far this conversion happened silently and the log gave no hint that the created preset does not contain the audio of the source unchanged; the note states the source format and the format it becomes. Each distinct conversion is logged once per run.
* Fixed: The sample and program files which a preset references are now looked up ignoring the upper/lower case of their name. Sampler file systems are case-insensitive and their CD-ROMs store all names in upper case, but a preset might well reference them in lower case. On a case-sensitive file system, which is the normal case on Linux, none of those samples could be found (e.g. 'STR SEC.6 -L.WAV' referenced as 'STR SEC.6 -L.wav'). This affects the Akai S5000/S6000 (AKP/AKM), Akai MESA, Akai MPC and MPC1000, FL Studio DirectWave, Teenage Engineering OP-XY and Synclavier Regen formats as well as all formats which use the common sample search (Ableton, Deluge, EXS24, NI Kontakt, NI Maschine, 1010music, SXT and TX16Wx) - the latter did only try the file ending in all upper and all lower case, so a name which differs in its case anywhere else was still not found.
* Fixed: When a sample was not found next to its preset, the search jumped the configured number of folder levels up in one go and took whatever the recursive search found first. Two libraries below the same folder which name their samples alike - the normal case for the variants of a library - then fed a preset the sample of the other library, which sounds wrong and, since the preset keeps its own loop points, clicks at the loop. The search now goes up one level at a time and stops at the first level which contains the file, so a sample close to the preset always wins over one of the same name further away.
* Fixed: The category names 'Winds' and 'World' were not detected when they were read back from a preset which stores the category as text: 'Winds' was detected as FX - from the 'Wind' sound effect - and 'World' was not detected at all. The name of a category is now a keyword of its own, which is checked for all of them.
* User Interface
  * New: Contents dialog: The new 'Export List...' button writes the listed presets - not the presets themselves - as a CSV or JSON file with the name, category, number of zones, key range (as note names and MIDI note numbers), folder, file, containers, index inside of the file and the ticked state of each of them. This gives an inventory of a disk image, a bank or a preset folder which can be used in a spreadsheet or a script. A search filter applies to the written list as well.
  * New: Contents dialog: The new 'Import List...' button reads such a list back in and ticks exactly the presets which it selects, so the presets to convert can be picked in another application: export the list, tick the presets in e.g. a spreadsheet, save it as CSV again and import it. A row selects its preset when its 'Selected' field says so ('true', 'x', '1' or 'yes'); a list without that field selects every preset it contains, so deleting rows works as well. Presets are matched by their file and their index inside of it, which tells presets of the same name in different banks apart and still finds a library which was moved to another folder.
* Akai MPC
  * Fixed: The Version element was created but never added to a written XPM file. Since it is the element by which the format is recognized, such a file was rejected when it was read back ("Could not read metadata: Unknown Root"). The platform is now written as well, so that the version log line is complete.
* Bliss
  * Fixed: A zone with a trimmed sample start was re-sampled to exactly 96 kHz (the maximum rate of the format was applied as a fixed rate, up-sampling included). Worse, the trim positions count in frames of the source rate, so after the up-sampling they pointed at the wrong place and cut the later part of the sample audio away. The samples now keep their rate; only the actual limits of the format are enforced - a rate above 96 kHz or a bit resolution which the format does not store (e.g. 8 bit) is converted, and this now also happens for zones which are not trimmed, whose samples went into the file unchanged before.
* Fairlight CMI
  * Fixed: Reading a Voice file whose version is not recognized wrote a copy of that file into a hard-coded folder of the developer's machine which did crash the conversion.
* Waldorf Quantum/Iridium
  * Fixed: Reading a preset shifted the sample start/end and loop points of many zones one frame down. The positions are stored as a fraction of the sample length with 8 decimal places, which can land marginally below the exact frame boundary (frame 3977 of 5469 is written as 0.72718961, and 0.72718961 * 5469 = 3976.9999787); the fraction was truncated instead of rounded, which loses one frame for every such position - on a converted card of 165 presets, 759 of 2270 loop positions were affected. The device's own exported presets write fractions which land marginally below the frame the same way, so rounding to the nearest frame is also what the device does when it reads them.
  * Fixed: The attributes of a written preset - the device lists them next to the preset name and filters the patches by them - now use the wording of the factory sound sets: 'Keys' instead of 'Keyboard', 'Bells' instead of 'Bell', 'Percussive' instead of 'Percussion', 'Loop' instead of 'Loops' and so on. Both words ended up in the filter list of the device otherwise, each of them finding only a part of the sounds.
  * Fixed: A preset whose category could not be detected wrote the word 'Unknown' into the first attribute, which fills the filter list of the device with an entry that says nothing. The attribute is left empty now, like the factory sound sets do. A keyword which repeats the category is no longer written a second time.

## 20.1.0

* Many thanks to Douglas Carmichael for plenty of contributions and fixes!
* New: Added support for the FL Studio DirectWave format (reading of DWP programs with their key/velocity ranges, gain, panning, loops, amplitude envelope, filter and the pitch and volume LFOs, including monolithic programs which carry all their samples inside of the file; the mapping of DWB banks and of sampled plug-ins is reconstructed from the names of their sample files; written programs are always monolithic, i.e. one self-contained file per instrument which stores all of its samples as FLAC compressed audio).
* New: Added support for the Teenage Engineering OP-XY multi-sample preset format (reading and writing of the *.preset folders with their patch.json description file, including the loop with its release behavior and cross-fade and the velocity sensitivity of the engine).
* New: Added support for the Casio FZ-1/FZ-10M/FZ-20M format (reading of floppy disk images (IMG, HFE) and bare dump files (FZF, FZV, FZB - including the head-less layout of the 'fzdump' utility, in which the circulating libraries like the factory library come) with banks, voices, loops, envelopes and filters; writing creates a ready-to-use floppy disk image with a full dump, e.g. for a Gotek/HxC floppy emulator - not yet verified on real hardware).
* New: Added support for the Audiomodern Soundbox format (reading and writing of sound packs (SBPACK): every preset of a pack becomes one multi-sample with the key/velocity ranges, root notes, sample start/end, loops with cross-fade (also ping-pong), reverse flag, volume, panning and tuning of its sounds and the volume, panning, tuning and amplitude envelope of the layers folded in; round robin layers are read as round robin groups and round robin groups are written as round robin layers; a filter in one of the effect slots becomes the filter of the zones and the voice mode and glide become the polyphony and portamento; writing creates one pack with one preset per source and stores identical samples only once - import the written pack into the plug-in, verified with Soundbox 1.2.1).
* User Interface
  * New: The folder/file history does now remember the selected source format for the folder/file and restores it on selection.
  * New: Contents dialog: The filter field can now be cleared with 'X' and has the focus when the dialog is opened.
  * Fixed: Contents dialog: Using 'Select All' on filtered content did still select all presets not only the filtered ones.
  * Fixed: Tabbing in dialogs did not work.
  * Fixed: Processing dialog: the Enable option could not be reached with tab.
* Processing
  * New: Added support to handle 32-bit float samples as input.
  * Fixed: Upsampling the bit resolution from 8 bit resulted in silent samples.
  * Fixed: Upsampling from 8 to 24 bit set the result to PCM_UNSIGNED instead of PCM_SIGNED.
  * Fixed: Handling of 12‑ and 20‑bit samples was not correct when converting to mono.
  * Fixed: Truncating the end did only check the 1st loop.
* Command Line Interface
  * New: The new option '-P' additionally writes the progress of a conversion to the error output in a machine-readable form, so that an application which runs ConvertWithMoss as a child process can display it - the progress dots of the normal output cannot be turned into a percentage. The percentage moves with the finished source files and, inside of a source file, with its loaded samples, which keeps a single large instrument moving as well. Setting the environment variable CWM_MACHINE_PROGRESS to 1 has the same effect, for hosts which cannot add options to the command line. Without the option nothing is written and nothing is changed.
* 1010music bento
  * Fixed: On macOS and Linux the patches of a performance were written into a single folder whose name literally contains the backslashes of the device path (`UserPatches\SampInst\`) instead of the nested UserPatches/SampInst folders; such a folder cannot even be copied onto the FAT32/exFAT card of the device. The paths inside of the project file were and are correct.
* 1010music blackbox, bento
  * Fixed: The preset paths written into presets and performances used the multi-sample name as-is, but the actually created folders have illegal file name characters removed and a number appended when the name already exists. Such presets referenced non-existing folders (e.g. for sources with a ':' in their name, like Roland S-7xx patches). The written paths now always match the created folders.
  * Fixed: blackbox: The silence workaround samples of a performance were referenced in the folder of the instrument but the sample is stored in the performance folder.
* Akai MPC60, MPC2000/3000, S-9x0, S-1000, Ensoniq ASR/EPS, Roland S-5xx, S-7xx
  * New: Patches are now put in sub-folders with their image name. On the S-5xx, if it is a CD-ROM, the CD-ROM name is another sub-folder. S-1000 adds Volume names.
* Ableton
  * New: Round-robin cycles which are stored as sample-select (selector) ranges - the only round-robin representation the Sampler of Live 10/11 has - are now read as round-robin groups instead of zones which all play at once. When writing, round-robin groups are stored as selector ranges whenever the native round-robin flag of Live 12 is not available (Ableton 11) or does not apply because only some of the groups alternate.
  * Fixed: A preset whose samples were not found at the calculated root path failed with 'No presets were found in the source' although the samples were present. This happened e.g. for a preset saved inside of or next to a Live project folder which keeps its samples in a nested folder (the root path search only recognized a folder with a direct 'Samples' child). The root folder of a Live project (marked by its 'Ableton Project Info' folder) is now recognized as well, the absolute sample path stored in the preset is tried as a fall-back and finally the sample file is searched by its name starting from the folder of the preset. A sample which is still not found only logs an error and drops its zone instead of the whole preset.
  * Fixed: A written preset was malformed XML when the preset name contains one of the characters '&', '<', '>' or a quote (the name is now escaped).
* DecentSampler
  * Fixed: The folder name of a created DSBUNDLE kept characters which are illegal in file names (e.g. the ':' of Roland S-7xx patch names).
* E-mu Emulator III/IIIX/ESI
  * New: Banks can now be written as a ready-to-use CD-ROM image (ISO) for SCSI CD-ROM emulators (e.g. ZuluSCSI), which is the only way to get a converted library onto these samplers since they read no file system of a computer. Each converted source becomes one bank of the image (at most 112), whose geometry, file entries and directory copy the Emulator IIIX library CD-ROMs. Written images have not been tested on real hardware yet.
* Fairlight CMI
  * New: IMG, IMD and HFE files which contain several Voice files can be read.
  * New: Voice files can now be written: either as a Series III voice (16-bit mono or stereo, up to 127 sub-voices with their key ranges, loops, tuning, gain and amplitude envelope) or as the fixed-size 8-bit voice files of the CMI I/II/IIx with their loop segments (one file per sample zone, e.g. for the QasarBeach recreation - the Arturia CMI V does not read voice files; the audio is re-sampled to the root frequency times 128, which is the pitch law of the CMI II - verified against QasarBeach; each voice comes with the control (CO) file it references, which carries the loop, attack, damping and level). Written files have not been tested on real hardware yet.
  * New: Voices can also be written in the native 16-bit format of the QasarBeach recreation (QBV2), which carries the loop, release and level inside the file and therefore loads ready to play; such files are read as well.
  * New: Voice files of the 8-bit CMI I/II/IIx dialect are now read as well, both with their full header and as bare 16 KB audio-only files. A control (CO) file present next to a voice wins over the voice header (as on the CMI itself) and also provides the attack and release of the amplitude envelope.
* Korgmultisample
  * Fixed: The loop end was ignored when writing: the single end field of the format (which is the sample end and the loop end at once) was always set to the sample end. A loop which ends earlier now moves the end to the loop end - audio behind an active loop can never be heard anyway - so held notes no longer cycle across e.g. the faded-out tail of a pad sample.
* NI Kontakt
  * New: The velocity to volume modulator is now converted with its response curve: Kontakt maps its normalized volume to decibels with 60*log10(x), so the amplitude follows the cube of the velocity. Destinations which can express the curve reproduce this response, e.g. SFZ receives matching amp_velcurve_N points.
  * Fixed: Added a workaround for missing samples with Kontakt 2/3 Monolithic NKM files created with AWave Studio which falsely classify samples as NKI files.
  * Fixed: The group and the instrument tune of a Kontakt 2 file were read as a linear offset in octaves although they are frequency ratios, like the already correctly read zone tune (and like all tune values of Kontakt 1 and 4.2/5+). Both are 1 - the neutral ratio - in a program whose tuning was never touched, and each contributed a full octave, so every converted program played 24 semitones too high. A tune of 0, which cannot be a ratio, is now taken as neutral instead of producing an infinite value.
  * Fixed: The soloed groups of a Kontakt 4.2/5+ program were honored even when the program has group solo switched off. Kontakt keeps the solo flags of the groups when solo mode is left, so a program with such left-overs converted to those groups only and dropped every other group.
* Roland MC-707/MC-101
  * Fixed: Projects which were created on the device (rather than by Roland) reported 'No tones or drum kits using user samples found' although they contain user samples, e.g. a project with 386 samples in 90 sounds. A slot of the sample parameter table was only taken as being in use when the flag at offset 0x10 is set, which Roland's own projects do set but the sample import of the device does not; a slot is now recognized by its name, which matches the number of stored samples in every project examined.
  * Fixed: The audio of the read samples was taken from the wrong offset and with twice the length: the size field of a sample chunk counts the bytes of one channel (not 16 bit words) and the audio starts 64 zero bytes behind the chunk header. The samples are now also read with their real channel count, which bit 15 of the chunk tag selects - the sample import of the device writes mono, while Roland's projects are stereo throughout.
  * Fixed: The TVF filter and the TVA amplitude envelope of a tone and the TVA envelope of a drum kit key were not read at all, so every converted sound fell back to a default envelope and lost its filter (e.g. a low-pass at a seventh of its range with resonance).
  * Fixed: The level, coarse/fine tuning and panning of a tone partial were not read. A tone which layers the same sample on several partials - the usual way to build an octave, a chord or a wide detuned sound on the device - therefore converted into several identical zones which only doubled the volume (e.g. 33 of the 90 sounds of one project were affected). The tuning is now read as the tuning of the zone and the panning as its panning, and the partial level scales the level of the sample slot. Writing stores the tuning and panning of a source in its tone.
  * Fixed: The filter and the amplitude envelope of every zone were taken from the tone's first partial, although each partial has its own. A layer with a slow attack or a different filter played with the settings of the first layer. The filter types LPF2 and LPF3 are now read as a low-pass as well, instead of being dropped.
  * Fixed: Written sample chunks lacked the 64 zero bytes which precede the audio in device-written and Roland-written chunks alike, so the device would play them from 32 frames too early. The 'untrimmed' flag of a written sample is no longer cleared by a loop, which had made a looped sample read back as an unused slot.
* Roland S-7xx
  * New: The 3 character prefixes are now interpreted as a category. If no category could be matched they are added to the full name (without the ':').
  * Fixed: CD-ROM/hard-disk images: the audio data of the samples is now located through the file allocation table and the directory entries instead of assuming one contiguous block at the fixed start offset of S-760 formatted disks. Images formatted by an S-750/S-770 system (whose audio area starts 1 MB earlier) and disks which contain deleted or fragmented entries either failed with 'Referenced sample data is larger than the available data' (e.g. the SV-SP70-01 Library Preview Disc) or could silently assign wrong audio to the samples. Deleted entries between the entries in use are now skipped as well instead of shifting all following patch, partial and sample references.
  * Fixed: Continuation disks with postfix spaces in the disk name could not be detected.
* Sample Files
  * New: Added support for reading CAF (Apple Core Audio Format) files. Linear PCM (integer and float in both byte orders), IMA4, µLaw, aLaw, Apple Lossless and MPEG-4 AAC (low complexity profile) audio data is decoded; files with other codecs are reported with the name of their codec. The instrument info (root note, key/velocity ranges, gain, tuning), loops and metadata texts are read from the respective chunks. CAF files are also picked up by all formats which reference sample files, e.g. the sample files of Logic Pro EXS24 instruments.
  * New: The destination 'Sample Files (WAV)' is now called 'Sample Files' and got an option for the audio file format of the written samples: WAV, AIFF, CAF, CAF-ALAC (compressed with Apple Lossless), CAF-AAC (compressed with MPEG-4 AAC, lossy) or FLAC. This allows audio file conversions (e.g. WAV to AIFF or AIFF to FLAC); all paths except CAF-AAC are lossless. The instrument, loop and metadata information is written to the matching chunks of AIFF and CAF files. The Apple Lossless decoder and encoder are ports of the reference implementation published by Apple, the AAC decoder is a port of the FFmpeg AAC decoder and the AAC encoder follows the structure of the FFmpeg encoder with a fixed precision target instead of its psychoacoustic model. All of them work on all platforms.
* SFZ
  * New: The response curve of the velocity to volume modulation is now written as amp_velcurve_N points when the source describes one (e.g. Kontakt, whose velocity modulation scales the amplitude with the cube of the velocity). When reading, amp_velcurve_N points are fitted to the closest power law, so the response survives a round trip.
* Synclavier Regen
  * Fixed: A tab or line break in a preset name or description corrupted the line/column structure of the written timbre and index text files; such characters are now replaced.
* Yamaha YSFC
  * Fixed: The waveform entry names written with the 'Create only waveforms' option kept the multi-sample name as-is; it is now folded to ASCII and limited to 20 characters like the names written with performances.
  * Fixed: The 'Create only waveforms' option could not be enabled from the command line interface - passing YsfcCreateOnlyWaveforms failed with 'Unknown parameter'.
* Processing
  * Fixed: Changing the sample rate of the samples used a linear interpolation, which is not band-limited: reducing the rate folded all frequencies above the new Nyquist frequency back into the audible range (measured on a sweep which lies entirely above it: -9 dB instead of silence) and raising it left images of the source spectrum above the original Nyquist frequency. Both are now converted with a windowed sinc interpolation, which lowers the aliasing of that sweep by 92 dB and the images of a 32 kHz sample raised to 48 kHz from -41 dB to -84 dB.
* WAV
  * New: WAV files which contain a complete Ogg stream instead of PCM data (marked with one of the Ogg Vorbis WAVE format codes, e.g. the samples of the legacy DirectWave packs of FL Studio) are read by de-compressing the embedded stream. They were rejected as an unsupported source format before.
  * Fixed: The audio metadata of a WAV file inside of a ZIP file could not be read when the file contains a large padding chunk in front of the data chunk (e.g. the PAD chunks in Audiomodern Soundbox packs). Destinations which need the metadata quietly dropped such a preset with only 'Resetting to invalid mark' in the log, e.g. converting such sources to Waldorf Quantum/Iridium. The metadata is now read with the RIFF parser instead of javax.sound.

## 20.0.0

* Many thanks to Douglas Carmichael for plenty of contributions and fixes!
* Many thanks to David García Goñi for providing specifications of the E-mu formats and refined Kurzweil specifications!
* Many thanks to Linus Wileryd for the improved icon set!
* New: Added support for the Arturia Synclavier V format (reading and writing of SYNX preset and bank exports; partials which play a sound file become sample zones).
* New: Added support for the E-mu Emulator III/IIIX/ESI bank format (E3B, E3X, ESI).
* New: Added support for the E-mu Emulator IV bank format (E4B). Banks can also be read directly from CD-ROM and hard disk images of the EOS samplers (ISO, IMG, HDA), including via the ISO/IMG source format. New: Writing creates a bank as a ready-to-use CD-ROM image for SCSI CD-ROM emulators (e.g. ZuluSCSI), which is the only way to load banks on units running EOS versions before 4.7. Written banks have not been tested on real hardware yet.
* New: Added support for the E-mu Emulator X format (EXB banks with their EBL sample pool).
* New: Added support for the Roland S-550 CD-ROM format.
* New: Added support for the Roland SP-404MK2 format (reading and writing of projects; each bank of pads becomes a multi-sample).
* New: When a converted preset plays its samples an octave or more from the middle of the keyboard (common for vintage phrase and vocal presets, which were triggered from drum machines rather than played on keys), a log line names the root it plays at, so the faithful mapping is not mistaken for a conversion error.
* User Interface
  * New: There is now a toggle switch before the source field to switch between batch conversion (as it worked before) or only picking a single file to convert. Each mode keeps a history of its own, which is exchanged along with the entered path when the toggle is used.
  * New: A source format where one file contains several presets - a bank, a disk image or a library - has a *Contents...* button, which shows all presets found in the source as a tree of their file and their containers. Each entry shows its number of zones, its key range and its category, so that an unknown bank can be explored, and only the ticked presets are converted. One note of the preset highlighted in the *Contents...* dialog can be played with the *Play* button or a double-click. It is rendered from the preset as it was read - amplitude and filter envelopes, filter, pitch and volume LFOs, velocity and panning - so it tells what the conversion will produce, which allows to pick the presets of an unknown bank by ear. This works for every source format, since it renders the model and not the format.
  * New: The *Play* button plays the note of a sample root close to the keyboard middle, so that phrase and vocal presets are heard as they were recorded.
  * New: Added tooltips to format lists to be able to see the full text of an entry.
* Command Line Interface
  * New: One or more source files can be given instead of the source folder, which converts only these files (e.g. `ConvertWithMoss -s exs24 -d 1010music MySampler/Piano*.exs Output`). The last given path stays the destination folder.
* Backend
  * New: Added support for a LFO (low frequency oscillator) modulating pitch (vibrato) with its rate, depth and delay: DecentSampler, DLS, Renoise, SFZ, SoundFont 2. Other formats pending.
  * New: Added support for a LFO modulating the volume (tremolo) with its rate, depth and delay: DecentSampler, DLS, Renoise, SFZ, SoundFont 2. Other formats pending.
  * New: The Korg KSC/KMP/KSF, Kurzweil K2x00, Roland MV-8000 and Roland ZEN-Core destinations have an option to shorten a name to its last separated segment for the short name field of the device (e.g. 'Greek Bazouki - Dark Tremolo' becomes 'Dark Tremolo'), which otherwise cuts off exactly the part that tells the presets apart. Disabled by default.
  * New: Added more instrument names to the category detector.
  * Fixed: A destination which cannot write what was asked for wrote nothing at all: the sources were read, collected and then silently dropped, since creating a library or a performance is an empty operation for a format which does not support it. E.g. converting 175 Elektron Tonverk presets to Waldorf QPAT with '-l MyLib' finished with an empty output folder, and Kontakt multis to QPAT with '-t performance' did the same. The command line now fails with an error message before the detection starts. The user interface is not affected, since it only offers the output types which the destination supports.
  * Fixed: Detecting a source which contains many presets was dominated by a fixed 10 millisecond pause taken after every single detected preset. A CD-ROM image of an E-mu sampler with 812 presets needed 11 seconds, of which 8 were that pause; two of them with 2339 presets needed 36 seconds. The pause is now taken every 64 presets, which keeps a cancellation responsive but takes the two images down to 1.4 seconds.
* 1010music blackbox / bento
  * Fixed: The loop cross-fade of a blackbox pad was read from the loop-end attribute instead of the fade-amount attribute - virtually every looped pad got a cross-fade spanning the entire loop, which destinations that bake cross-fades into the audio smeared audibly.
  * Fixed: A performance instrument on OMNI was written into a bento project with MIDI input channel 'Off', so the track received no MIDI at all - the same defect was fixed for the blackbox in 19.1.0.
  * Fixed: The loop type and cross-fade of a bento cell were assigned from themselves instead of the cell's default loop - a ping-pong loop read as a forward loop.
  * Fixed: The sample length attribute of a bento cell was treated as an absolute end position although it is the play-back length (the blackbox reading is correct) - a cell with a moved sample start lost that many frames from its end.
  * Fixed: The root velocity written into a bento project was half of the velocity range's width instead of its mid-point, which for a layer of 80..127 lies outside of the layer.
* Ableton
  * Fixed: An unset envelope start or end level was written as 1.0 although Live's default is silence - a slow-attack pad converted with no swell at all (the envelope ramped from full to full), and unset pitch-envelope levels became a full-depth offset. Unset start/end levels are now written as 0, unset hold/sustain levels as 1.
  * Fixed: The sign of the filter and pitch (aux) envelope amounts was stripped when reading, so downward sweeps inverted - converting an ADV with a negative filter envelope back to ADV flipped it to positive.
  * Fixed: The detune of the sustain loop was read from the multi-sample part (which is the zone tuning, already applied) instead of the sustain loop element, so the zone detune was applied twice after a round trip.
* Akai AKP/AKM
  * Fixed: The pitch bend range was passed as semitones into the model's cents field, so the bend wheel effectively did nothing on converted programs.
  * Fixed: The volume of a multi part was multiplied onto the dB gain of the zones (a no-op for 0dB zones) instead of being added as a dB offset, and the part panning was scaled twice as wide as the field allows.
* Akai MPC
  * New: The destination can now write MPC 3 track files (*.xty) with their '_[TrackData]' sample folder as an alternative to MPC 2 keygroup folders (new 'Output Format' option, issue #117). The files replicate the complete track structure of MPC firmware 3.7 from a template - the firmware's own reader is strict about its serialized object tree - and only the multi-sample fields are patched in.
  * New: The volume of a MPC 3 layer (an object holding the linear gain coefficient) is now read.
  * Fixed: The pitch bend range of a MPC 3 track or project was read as cents but the file stores semitones, so the default of 2 semitones became 2 cents. The fraction of an octave stored next to it confirms the unit.
  * Fixed: The tuning of a MPC 3 layer was read from the coarse and fine tune of its instrument, which the caller had already applied - the instrument tuning was doubled and the layer's own tuning was lost.
* Akai MPC2000/3000
  * Fixed: The tune of a program pad and of a SND sample were read unsigned - every negatively tuned pad of e.g. the factory library CD came out drastically sharp (a -0.05 semitone pad read as more than +600 semitones and was clamped to +12).
* Akai MPC60
  * Fixed: The 12 bit sample data was unpacked with the low nibble at 1/16 of its weight and the result byte-swapped, which reduced the audio to roughly 8 bit quality with a signal-correlated error. The 12 bits are now left-justified as in the reference implementation.
* Akai S900/S950
  * Fixed: The fractional part of the nominal pitch was applied with an inverted sign: a sample recorded e.g. a quarter tone sharp was played another quarter tone sharp instead of being corrected. Also the loudness offset was read unsigned, so any attenuation became a boost.
* Akai S1000/S3000
  * Fixed: The pitch bend range was passed as semitones into the model's cents field - the bend wheel effectively did nothing.
  * Fixed: The program volume (a 0..1 value) was written directly as the dB gain of every zone, overwriting the per-layer loudness set before - the velocity-layer balance of every program was discarded.
  * Fixed: The fraction byte of the keygroup and keygroup-sample fixed point tunings was treated as signed, so negative fine tunes came out about a semitone flat (e.g. -0.05 semitones read as -1.05). The sample header tuning in literal cents is no longer rescaled.
* Arturia Synclavier V
  * New: Added an option to write the samples additionally as plain WAV files in the layout of the sample pool ('User/&lt;preset&gt;'): the application's import loads the embedded samples but does not copy them into its sample pool, so imported presets loaded in a later session report them as missing. Merging the written 'User' folder into the pool makes them permanent.
* CWITEC TX16Wx
  * Fixed: The wave IDs of a written program restarted at 0 for every group, although they must be unique across the program - in a program with several groups all regions resolved to the waves of the last group when read (also in the TX16Wx plug-in itself). Additionally the reader now creates an own zone object per region, since several regions may reference the same wave; before, such regions shared one object and overwrote each other's key and velocity ranges.
  * Fixed: The depth of a pitch envelope was written in cents of the full model depth (12000 cents) but read as a fraction of 4800 cents, inflating the depth 2.5 times in a round trip.
  * Fixed: Cutoff Key-Tracking is now adjusted to 24000ct maximum range (was only 1200ct maximum).
* DecentSampler
  * Fixed: A volume value without a dB unit is a linear amplitude but was scaled as 'value times 6dB' - volume="0.5" (-6dB) read as +3dB and an explicit volume="1.0" differed from an absent attribute.
* Disting EX
  * Fixed: The creator names round-robin samples with an '_RR<n>' suffix but the detector's file name pattern only accepted '_R<n>' - such file names did not match at all, so the note and velocity information of every round-robin sample was lost when reading. Both spellings are accepted now.
* DLS
  * Fixed: The pitch envelope was built from the times of EG1 (the amplitude envelope) instead of its own EG2 times.
  * Fixed: The fine tune of a region (stored in cents) was divided by 32768 as if it were a semi-tone fraction - a -50 cent detune shrank to -0.15 cents, erasing the detune of every region.
  * Fixed: The signed 16 bit accessor of the RIFF chunk framework read big-endian although RIFF is little-endian throughout; the region fine tune (its only user) additionally came out byte-swapped.
* Kurzweil K2x00
  * Fixed: An envelope stage with both a zero time and a zero level is unused on the device and keeps the level of the previous stage, but was read literally. A program which leaves its decay stage unused - like the reported FM basses, which sustain at the attack level and only fade out with a long release - was therefore converted to a silent preset. Additionally, the attack velocity is now converted as a cutoff modulation source of the F1 slot in both directions; the same programs open their nearly closed cutoff by velocity and converted very dull without it.
* Logic EXS24
  * Fixed: The velocity sensitivity of the volume envelope was read and written inverted: a file with full sensitivity (-60dB, which is also the format default) was read as no velocity response at all and vice versa. Since both directions were inverted, the defect was invisible in a round trip but e.g. a written EXS did not respond to velocity in Logic.
  * Fixed: The parameters with IDs above 255 (among them the envelope delay and attack slope) were written in a layout which neither the reader nor Logic understands and their padding leaked 4 additional zero bytes per unused slot into the old parameter block - the affected parameters were lost from every written file.
  * Fixed: The velocity modulation of the filter cutoff was formatted but never added to the written file.
  * Fixed: The panning of a group was decoded twice, so every left-panned group ended up hard left (the flattened per-zone panning was correct, only the group offset was affected).
* NI Kontakt
  * Fixed: The filter cutoff of a Kontakt 1 file is stored logarithmically normalized to [0..1] but was read as a frequency in Hertz - a mid-range filter became a low-pass below 1 Hz and the converted preset was practically silent. Writing was already correct, which is why round trips through ConvertWithMoss hid it.
  * Fixed: The upper key limit of an instrument in a Kontakt 1 multi (NKM) was stored as the lower clip key - the clip range became [high..127] and destinations which apply it dropped or mis-clipped most zones of a performance conversion.
  * Fixed: The upper velocity crossfade of a written Kontakt 1 file was calculated from the lower velocity crossfade value.
  * Fixed: The loop cross-fade of a written Kontakt 1 file was the cross-fade fraction cast to a whole number, so every cross-fade became 0 samples. The field is a length in samples.
  * Fixed: The lower velocity crossfade of a Kontakt 4.2/5+ zone was read from the lower key crossfade field.
  * Fixed: Muted and soloed groups were ignored when reading Kontakt 4.2/5+ files - the zones of muted groups (often alternate articulations parked by the sound designer) stacked onto the active ones. Zones of muted groups are now skipped, and if any group is soloed only the soloed groups are converted.
* NI Maschine
  * Fixed: The velocity to volume amount was written from the amplitude envelope modulation depth instead of the velocity modulation amount (both the Maschine 1 and 2 writers) - a source without velocity response became fully velocity sensitive.
* Reason NN-XT
  * Fixed: The velocity fade-out was read raw although it is stored inverted (0x80 = off, otherwise 127 minus the fade range, as the writer encodes it) - a zone without a fade read as a fade over the whole velocity range and an SXT round trip turned 'off' into a full-range fade.
* Roland S-5xx
  * Fixed: Removed Null-characters from description texts.
  * Fixed: The TVF key follow used an integer division, so any tracking below the maximum was rounded down to none; the value was also read unsigned, so negative tracking was impossible.
  * Fixed: The unison detune of a patch (documented -50..50 cents) was read unsigned, so a negative detune turned into more than 2 semitones upwards.
* Roland S-7xx
  * Fixed: The three panning stages (patch, partial and sample mix) were multiplied instead of added, so a centered stage - the default - erased the panning of all others: the stereo image of every factory patch built from hard-panned sample pairs collapsed to doubled mono, and two same-side pans flipped to the other side. On a library CD-ROM all 3765 panned zones were read dead center.
  * Fixed: Five signed parameters (the TVF envelope depth, the pitch envelope depth, the cutoff velocity sensitivity, the cutoff key follow and the TVA level key follow, all -63..63) were read unsigned - a negative value such as a downward filter sweep of -20 became +3.75 with the direction inverted.
  * Fixed: The envelope time law missed that 0 means instant, so every stage was at least 302 ms long - including a fabricated 302 ms delay before every note, which 3204 zones of the same CD-ROM carried into the converted files. Envelope times which are really programmed are unchanged.
  * Fixed: A loop cross-fade was fabricated from the fractional address bytes of the loop points; the S-7xx has no loop cross-fade parameter.
* Roland ZEN-Core
  * Fixed: A bank (a SVZ which holds one tone per preset, as written by the preset library destination) was read as one single multi-sample: all samples of the file were merged into it and only the shaping of its first tone was applied, so every preset but the first was lost. Each tone is now read as a multi-sample of its own, built from the multi-samples which its partials play. A SVZ with one tone is unchanged.
  * Fixed: The pan and the velocity window of the tone partials were ignored when reading, so a stereo instrument (two hard-panned partials over two mono multi-samples) collapsed into stacked centered zones and velocity layers all sounded at once. Each partial's zones now carry its pan and velocity range; a partial whose right wave differs from its left one is read as a stereo pair.
  * Fixed: The levels of the pitch and filter (TVF) envelopes are signed 16 bit values but were read unsigned, so e.g. a pitch envelope starting at -1023 (a classic pitch drop) read as starting at the positive maximum.
* Renoise
  * Fixed: A modulation set was created for every key zone. Now zones with identical modulation share one set, which gives the usual single instrument-wide modulation set; additional sets are only created for zones which genuinely modulate differently.
  * New: A pitch LFO (vibrato) is read from and written as a LFO modulation device (waveform, rate, depth and onset time; the value curves were calibrated against Renoise 3.5.4).
  * New: A volume LFO (tremolo) is read from and written as a LFO modulation device targeting the volume; since the volume chain is linear, the depth in decibels is expressed as the linear swing whose lowest point lies that many decibels below full volume.
  * Fixed: The depth of a pitch envelope was read as the model maximum of 120 semitones instead of the pitch modulation range of the modulation set (12 semitones by default) and was dropped entirely when writing - a 2 semitone pitch envelope e.g. inflated to 120 semitones in a round trip. The depth is now read from and written as the pitch modulation range of the mixer device, which also keeps the pitch LFO consistent.
* Sample Files
  * Fixed: When building a multi-sample from plain sample files, an embedded root note (e.g. from the WAV 'smpl' chunk) was ignored since the key mapping ran before the file metadata was loaded - file name digits won instead ("Marimba-01..03" mapped to the MIDI notes 1..3) or the detection failed although every file carries its root. The file's own root note now takes precedence over the name.
* SFZ
  * Fixed: The key and velocity crossfade ranges (xfin/xfout) were written outside of the zone's range, where the zone does not even play - and for zones at the edges the 0/127 clamps collapsed the fade range entirely (e.g. a fade at the top of a full-velocity zone was written as the empty range 127..127 and lost). The fades are now anchored inside the zone's range.
  * Fixed: A negative filter envelope depth (a downward sweep, e.g. of an 808-style kick) was silently dropped when writing; the fileg_depth opcode is now also written for negative depths.
* SoundFont 2
  * Fixed: Generators which only appear on the preset level were dropped entirely when the instrument level did not carry the same generator, although the specification defines them as offsets to the default value in that case. A preset whose global zone transposes a plain instrument down an octave was read an octave too high, preset-level attenuation and envelope offsets vanished, and a preset-level filter offset never created a filter. Such generators are routine in commercial and General MIDI SoundFonts.
  * Fixed: A preset-level generator offset was added without the signed conversion when the instrument level carried the generator and the value was looked up unsigned - a negative offset (e.g. lowering the filter cutoff) was added as a huge positive number.
  * Fixed: The filter resonance was read as centibels divided by 100 instead of 10, so every resonance came out ten times too small (20 dB read as 2 dB) and each SoundFont-to-SoundFont round trip divided it by ten again. Writing was already correct.
  * Fixed: Negative pitch and filter envelope modulation amounts (downward sweeps, e.g. the pitch drop of an 808-style kick) were dropped when writing; both generators are signed in the format and reading them was already correct.
* Synclavier Regen
  * Fixed: A source with more than 12 groups lost everything beyond the first 12 - many sources deliver one group per sample zone, so nearly the whole key map was dropped and large multi-samples converted silent. Groups whose zones share the same velocity range and do not overlap on the keyboard are now packed into shared partials (a partial holds a whole key map in its patch list), so such sources fit completely; only genuine layers beyond 12 are still dropped.
* Synthstrom Deluge
  * Fixed: The velocity sensitivity of the volume (the velocity to volume patch cable) was read into and written from the amplitude envelope modulation depth instead of the velocity modulation amount - a source without velocity response became fully velocity sensitive and vice versa.
  * Fixed: An instrument without loops which is not a one-shot was written with the loop mode ONCE, which ignores a note-off on the device (and was read back as a one-shot). Such instruments are now written with CUT, which stops the play-back on a note-off.
  * Fixed: The transpose and detune of a kit drum were discarded when reading a kit, so pitched kit drums lost their tuning even in a Deluge to Deluge conversion.
* TAL Sampler
  * Fixed: The creator omitted the volume attribute for 0dB zones but the reader decodes a missing attribute as about -21.8dB, so every zone of a read-back .talsmpl dropped by that amount. The volume is now always written and the reader's default is the raw value which represents 0dB.
* Waldorf Quantum/Iridium
  * New: A source which carries its bank in front of its name is written without it in the preset name, since the device shows the bank in a field of its own. The file name keeps the full name. An explicit Bank from the settings replaces the source's bank, in which case the name keeps it - but only as long as it fits into the 32 character name field of the device. Everything beyond that is cut off, which removes exactly the part that tells the presets of one bank apart: 'Full Arco String - Arco Strings Lo' and '... Hi' both ended up as 'Full Arco String - Arco Strings' on the display. Such a name is now written without the bank of the source, which the file name still carries.
  * New: A 'Shorten file names to the displayed preset name' option names the written file after the preset name which the device shows and keeps the whole file name within 40 characters (the import screen of an Iridium MK2 displays about 43 and cuts off the rest), e.g. '05000-Full Arco String - Arco Strings Lo.qpat' (45 characters, clipped on the device) becomes '05000-Arco Strings Lo.qpat'. Disabled by default.
  * Fixed: The amount of a matrix entry modulating the oscillator pitch (the pitch envelope) was read with the wrong offset: a positive amount was read as a negative one (e.g. +50% as -25%) and +100% was dropped entirely.
  * Fixed: The velocity modulation of the volume was written from the amplitude envelope modulation depth instead of the velocity modulation amount. E.g. a source with a velocity amount of 0% was written as +100%.
* WAV
  * Fixed: The sample chunk stores a note with a negative fine tuning as the next higher unity note with the complementary positive fraction, but the correction was applied in the wrong direction on both sides: reading added 1 to the unity note where 1 must be subtracted and writing subtracted where it must add. The two errors cancelled between ConvertWithMoss' own reader and writer, which is why round trips never showed it - but any WAV from another tool whose pitch fraction is above 50 cents was read two semitones off, and every written WAV with a negative fine tuning carried a unity note two semitones below what conforming samplers expect.
  * Fixed: A sample chunk whose pitch fraction is above 50 cents overwrote the root key which the preset format itself supplied (e.g. the root of a Bitwig multisample's XML), since the fine-tuning merge was not gated on the root being unset - combined with the direction error above, such a zone ended up more than two octaves off. The root key and fine tuning of the sample chunk are now only used when the format does not provide a root of its own.
* Yamaha YSFC
  * Fixed: Envelope times were quantized to whole seconds when writing, since the lookup into the time table compared against truncated table entries - every sub-second time became about 0.9 seconds (e.g. a release of 0.3s tripled) in all written amplitude, filter and pitch envelopes.
  * Fixed: The tuning of a zone was written to both the keybank and the element although the two stack on the device (and when reading), which doubled the detune of every written zone. The element tuning is now left neutral.
  * Fixed: When reading performances, a performance was added to the result once per part, so a multi-part performance was converted multiple times.

## 19.1.0

* Many thanks to Douglas Carmichael for plenty of contributions and fixes!
* New: Added support for the Kurzweil K2000/K2500/K2600 format (KRZ, K25, K26). Known issue: Envelopes are not always read correctly.
* User Interface
  * New: The Settings and Processing dialogs opened with a bright white frame and, on macOS, repainted whenever the main window was clicked. Since there is no nice fix for this, the dialogs have now been replaced with pseudo dialogs which are part of the main window.
  * New: Removed the hover highlighting from the titles in the Processing dialog.
* Backend
  * New: Added support for the one-shot playback mode (a note-off is ignored and the sample is always played back to its end): Ableton, Akai AKP, Akai MPC, Akai MPC1000, Akai S900, Akai S1000, Kontakt, Korg multisample, Logic EXS24, 1010music blackbox/bento, Polyend Tracker, Renoise, Roland MV-8000, Roland S-5xx, Roland S-7xx, Roland ZEN-Core, SFZ, Synclavier, Synthstrom Deluge, Tonverk, TX16Wx, Yamaha YSFC. Formats previously collapsed this into "has no loop" or guessed it from the envelope.
  * New: Added support for exclusive ('choke') groups, which stop all sounding notes of the same group when a note of that group starts, e.g. a closed hi-hat cutting off an open one: Akai MPC1000, Akai MPC60, DLS, Kontakt, Logic EXS24, 1010music blackbox/bento, Renoise, Roland MV-8000, SoundFont 2, TAL Sampler, Yamaha YSFC.
  * New: Added support for a random play logic next to round-robin: Ableton, Akai MPC, DecentSampler, Logic EXS24, Renoise, Yamaha YSFC. Formats which cannot express a random selection now fall back to round-robin instead of playing all layers at once.
  * New: Added support for amplitude keyboard-tracking, the counterpart of the filter cutoff keyboard-tracking: Akai S1000, DLS, Logic EXS24, Roland MV-8000, Roland S-7xx, SFZ, Synthstrom Deluge, Yamaha YSFC.
  * New: Added support for envelope time keyboard- and velocity-scaling, which scales the envelope times by the played key and velocity (as opposed to the already supported slopes, which describe the curvature of a segment): Akai S1000, Ensoniq EPS/ASR, Ensoniq Mirage, Logic EXS24, Reason NN-XT, Roland S-7xx, SoundFont 2, Yamaha YSFC.
  * New: Added support for per-instrument voice settings (polyphony and monophonic legato): Akai S1000, DecentSampler, Disting EX, Ensoniq Mirage, Logic EXS24, Reason NN-XT, Roland S-7xx, SFZ, Synthstrom Deluge, TAL Sampler.
  * New: Added support for group volume, panning and tuning offsets: Kontakt, DecentSampler, Logic EXS24, Synclavier, TX16Wx, Waldorf Quantum/Iridium.
  * New: Added support for a pitch low frequency oscillator (vibrato) with its rate, depth and delay: DecentSampler, DLS, SFZ, SoundFont 2. Previously any vibrato was dropped on conversion.
  * New: Source folders and files are now processed in a stable alphabetical order instead of the file-system enumeration order, so consecutive runs behave identically (and e.g. the QPAT import numbers are assigned in a predictable order).
  * New: Improved logging if WAV file could not be written.
  * New: Keep dots when making file names safe.
  * Fixed: Two filters which differed only in their cutoff envelope were treated as equal, so zones which are not identical could be combined into one; a filter with a cutoff envelope but without a cutoff velocity modulation could additionally throw an exception.
  * Fixed: Copying a sample zone did not copy the sequence position belonging to the play logic and did not copy the velocity modulator of the amplitude.
* 1010music bento
  * Fixed: With the "Trim start and end" processing option enabled the loop points were left at their untrimmed positions - the sample length was already corrected - so a trimmed sample with a non-zero start got a displaced loop.
* 1010music blackbox
  * Fixed: The choke group attribute of an unused template slot was written as "okegrp" instead of "chokegrp".
  * Fixed: The MIDI channel of a performance was written one channel too high and MIDI channel 16 was turned into "Off". The value is the MIDI channel 1-16 (where channel 1 doubles as the OMNI mode) and not an additionally offset one; OMNI is now written as channel 1 instead of switching MIDI input off.
* Ableton
  * Fixed: The warning that the round-robin configuration could not be translated was logged when it could be translated and not when it could not. The written file is not affected.
  * Fixed: No preset was written at all (the conversion failed with a "sample file not found" error) when a zone name contained one of the characters & . ' : / \ * ? " < > | - the sample file is written with those characters replaced by an underscore, but the preset looked it up and referenced it under the unchanged name.
  * Fixed: The upper velocity crossfade was calculated from the lower velocity crossfade value.
* AIFF
  * New: Added support for reading AIFC files with un-compressed PCM sound data, e.g. the little-endian ('sowt') files written by the Elektron Tonverk or the Teenage Engineering OP-1. Compressed AIFC files are still rejected with an error message.
  * Fixed: Converting an AIFF file with the file ending '.aiff' (instead of '.aif') deleted the source sample file: an internal workaround copies such files to a temporary file but the cleanup deleted the original instead of the temporary copy.
  * Fixed: Reading the sound data chunk of an AIFF file returned only the first few bytes (the bit resolution was mistaken for the data size), which corrupted the fallback conversion path for AIFF files that the Java sound system cannot read.
* Akai MPC500/1000/2000(XL)/2500/3000
  * New: Always create filter, even when cutoff is at maximum.
* Bliss
  * Fixed: The loop mode was only written when a zone had a loop, but a missing loop mode is read back as a forward loop, so zones without a loop turned into fully looped ones.
  * Fixed: The samples in a written preset or bank (.zbp/.zbb) were stored as raw source audio under a ".flac" name instead of being FLAC encoded, so neither Bliss nor ConvertWithMoss itself could read back a written file. The zone start/end trimming was skipped for the same reason.
* CWITEC TX16Wx
  * Fixed: Ignore modulation slots which are switched off.
* DecentSampler
  * New: Added a source option "Create one multi-sample per group": creates a separate multi-sample for each group (disabled groups included), e.g. for presets which contain several alternative kits as groups and switch between them via their user interface.
  * Fixed: Disabled groups were only skipped when written as enabled="0" but not as enabled="false". Presets that switch between several kits via a drop-down in their UI (each kit is a group and only one is enabled) were converted with all kits stacked on the same keys and playing at once.
  * Fixed: A loop which is explicitly disabled with loopEnabled="false" was still created when loop points were present; loops from the sample file chunks were imported as well in this case. Presets without the loopEnabled attribute are not affected.
  * Fixed: The panning of a group was not scaled, so any group with a panning moved all of its zones fully to one side.
* Elektron Tonverk Preset
  * New: Read the Grainer generator machine (granular playback of a single sample). Since grains cannot be represented in the multi-sample model, the sample is converted like a One-Shot and the granular engine parameters are not converted.
  * Fixed: An envelope hold time was dropped when writing a preset. It is now added to the decay time - the hold phase only exists in the device's AHD mode while the written envelope is always ADSR - the same way all other formats without a separate hold stage handle it.
  * Fixed: Writing a Tonverk preset failed in the packaged application with "Resource '.../tonverk/multi-template.tvpst' not found.
* Expert Sleepers Disting EX
  * Fixed: The amplitude envelope was only written when the internal hash code of the envelope value happened to be positive, so about half of all converted presets silently kept the factory default envelope instead.
  * Fixed: The envelope sustain level was never written and stayed at its maximum, so a percussive source (sustain 0) held at full level forever.
* Korg KMP
  * Fixed: Detector did hang on first found KMP file.
* Logic EXS24
  * New: Implemented Velocity -> Filter Cutoff Modulation (read/write)
  * Fixed: The panning of a group was read as an unsigned value and was not scaled, so a group panned fully left moved all of its zones fully right.
  * Fixed: The attack time at the lowest velocity was never written and stayed at 0, which gave all written presets an instant attack at low velocity.
* Polyend Tracker
  * Fixed: The filter cut-off envelope amount was written from - and read back into - the modulation depth as if that depth were a frequency in Hertz, but it is a normalized [-1..1] value. The written amount collapsed to nearly zero, so the filter sweep disappeared; both directions now use the normalized value.
* Reason NN-XT
  * Fixed: All loops were lost when reading a preset: the loop was created but never added to the zone. Writing loops was not affected.
  * Fixed: The pitch key tracking was never read, so zones with a reduced key tracking (e.g. fixed-pitch percussion) were imported as fully tracking.
  * Fixed: The depth of the filter envelope was written into the field of the pitch envelope, so the filter modulation was lost and the pitch modulation was overwritten with it.
* SFZ
  * Fixed: The velocity range was taken from the cross-fade opcodes, so it grew by the width of the cross-fade with every conversion (e.g. lovel/hivel 40/100 became 30/110 and then 20/120). The same was already fixed for the key range.
* Spectrasonics Omnisphere 3
  * Fixed: Envelope stages which are not set were written as "not a number" (attack, hold, decay and release) or as a negative sustain level. Unset times are now written as zero and an unset sustain level as the full level.
  * Fixed: The zone tuning was written and read as cents although the model value is in semi-tones, so the tuning was off by a factor of 100 against every other format (an Omnisphere-to-Omnisphere conversion was not affected since both directions had the same error).
* Synthstrom Deluge
  * New: Added an Output Type creator option (Synth/Kit, CLI DelugeOutputType) to write a drum kit instead of a synth (sound) preset. A kit writes one drum per note, consolidating velocity layers and round-robins to the loudest layer (a Deluge drum is a single sample). The type is chosen explicitly because a one-sample-per-note layout is not necessarily a kit (e.g. a per-note synth bass).
  * New: Added a "Consolidate kit" option (CLI DelugeConsolidateKit) which reduces a drum kit to one drum per type (kick, snare, hi-hat, ...) ordered by drum role following the factory TR-808 layout (kick on the lowest row), so a beat can be programmed without switching rows. The consolidated drums are labelled by their role for a clean read-out on the device.
  * New: Added a "Shorten kit name" option (CLI DelugeShortenKitName) which names a kit "NNN &lt;last name segment&gt;" (e.g. "80s hits SSS043 - Kit 07 - Full Kit 2" becomes "007 Full Kit 2") so it scrolls less on the device display; a trailing date/version suffix is removed while model numbers like TR-808 are kept.
  * Fixed: Kit drums were pitched by their keyboard mapping note, so a drum mapped to e.g. note 35 played 25 semitones too low (audible on toms). Kit drums now play at their natural pitch (transpose 0), like the factory kits; only an explicit detune is kept.
* TAL Sampler
  * Fixed: Read the pitch envelope depth from the correct matrix row.
  * Fixed: The sample "reverse" flag was never read as enabled: it is stored numerically (0/1) like all other TAL flags but was parsed as a true/false text boolean.
  * Fixed: Disabled groups were only skipped when written as enabled="0" but not as enabled="false". Presets that switch between several kits via a drop-down in their UI (each kit is a group and only one is enabled) were converted with all kits stacked on the same keys and playing at once.
* TX16Wx
  * Fixed: Envelope levels which are not set were written as -100%, which is a valid but completely different envelope. They now fall back to the neutral level.
* Waldorf Quantum/Iridium
  * New: Added a creator option to prefix written preset file names with a 5-digit import number (e.g. 05002-Name.qpat), mirroring the device's own export naming so the device assigns each preset to that number on import.
  * New: The oscillator volume and panning are now preserved instead of always being written as 0 dB / Center, so a Quantum/Iridium round-trip keeps them.
  * Fixed: The oscillator volume and panning overwrote the volume and panning of the sample map entries instead of being combined with them, which is what made a round-trip lose them.
  * Fixed: A preset without an oscillator panning panned all of its samples half right, because the converted [-1..1] panning was initialized with a value of the raw [0..1] range.
  * Fixed: The sample map referenced the samples with a different name sanitizing than the one used to write the sample files - an umlaut was transliterated in the map but kept in the file name, an '&' was kept in the map but replaced in the file name - so the device could not resolve the samples and showed the "Find Sample Map" screen.
  * Fixed: The sign of the fine tuning was inverted when reading a preset, so a Quantum/Iridium preset converted back to a Quantum/Iridium preset detuned its samples in the wrong direction.
* Roland MV-8000/MV-8800
  * Fixed: Reading a patch could hang with full CPU load and no output. The loop over the sample slots did not advance on an empty slot, and since a patch rarely uses all of its slots this affected almost every patch.
* Roland S-7xx
  * Fixed: The two envelope time key-follow fields were read as unsigned although they are signed.
* Roland ZEN-Core
  * Fixed: SVZ sample packs produced by Roland's own SF2-to-SVZ converter (e.g. the commercial "ARP Solina Strings" / Vulture Culture SOURCE packs) could not be read - their samples were detected as "50 channels" and the length calculation failed. These chunks embed a complete WAV file rather than raw PCM; the embedded WAV is now read directly.

## 19.0.0

* New: Added support for the Polyend Tracker (PTI) instrument format (thanks to Douglas Carmichael).
* New: Added support for the Renoise instrument (XRNI) format (thanks to Douglas Carmichael).
* New: Added support for the Synthstrom Deluge instrument format (thanks to Douglas Carmichael).
* New: Added support for the Elektron Tonverk preset (TVPST) (thanks to Douglas Carmichael).
* New: Added support for the Roland MV-8000/MV-8800 patch format (MV0) (thanks to Douglas Carmichael).
* New: Added support for the Roland ZEN-Core sound format (SVZ) (thanks to Douglas Carmichael).
* New: Added support for the Synclavier Regen timbre/library format (SFLC) (thanks to Douglas Carmichael).
* New: Added support for the Roland MC-707/MC-101 project format (MPJ) (thanks to Douglas Carmichael).
* New: Added support for the Fairlight CMI 3 - read only (thanks to PythonBlue).
* New: Added support for the Downloadable Sound format (DLS) - read only.
* User Interface
  * New: Improved user interface for long lists of formats.
  * New: Added menu item when right clicking a log message to open the mentioned folder (if there is one).
  * Fixed: The source format list showed a stray comma before the file extensions (thanks to Douglas Carmichael).
* Backend
  * New: Added support for sustain / 'loop until release' loop mode (the loop runs while the key is held and then plays the remainder of the sample on release, as opposed to a continuous loop) - Ableton, Ensoniq EPS/ASR, EXS24, NI Kontakt, Renoise, SoundFont 2, SFZ, SXT, Tonverk (thanks to Douglas Carmichael).
  * New: Added support for filter cutoff keyboard-tracking: Ableton Sampler, Akai AKP/AKM, Akai S1000, Bliss, Ensoniq, EXS, Omnisphere, SXT, Roland, SFZ, Synthstrom Deluge, TAL Sampler, TX16W, Waldorf, Yamaha YSFC.
  * New: Added several new tags for category detection.
  * New: Added an opt-in metadata option "Category tag at name start declares the category" (off by default): many commercial libraries prefix each preset name with its category (e.g. 'PAD Solina', 'BASS Growler'). When enabled, such a prefix takes precedence over keyword matches elsewhere in the name, which could otherwise win accidentally (e.g. 'BELL Vibrato Strings' was detected as Strings instead of Bell), and common abbreviations (BRAS, DRM, FLUT, GRAN, ORG, PERC, PHYS, PLUK, POLY, REES, STRG, SWEP, VOC) are recognized as well. Also added 'Reese' as a Bass category tag (thanks to Douglas Carmichael).
  * New: Added an opt-in *Snap loops to zero-crossings* processing option.
  * New: Added a *Transpose* processing option (-24 to 24 semitones, CLI -Zp): moves the root notes of all samples so the presets play higher or lower without changing which sample is mapped to which key (thanks to Douglas Carmichael).
  * Fixed: Ignores hidden files/folders and the known Windows system folders when checking for empty-folder (thanks to Douglas Carmichael).
  * Fixed: A library name typed with its file ending (e.g. "MyLibrary.xrni") produced a doubled-up file name ("MyLibrary_xrni.xrni") - the ending is now recognized for every destination format (thanks to Douglas Carmichael).
  * Fixed: The "Trim start and end" processing option cut the audio to the zone's start/end but left the loop points at their old positions, so a trimmed sample with a non-zero start got a displaced loop - the loop end could even point past the end of the trimmed audio. The loop points now move with the cut (thanks to Douglas Carmichael).
  * Fixed: Replaced the external FLAC encoder library with an own implementation: the library crashed on samples whose length modulo 4096 was 2, 3 or 4 (e.g. "The FLAC encoder failed for sample '...'" when writing Renoise files; SFZ with FLAC option, Bliss and Synclavier Regen were affected as well) (thanks to Douglas Carmichael).
  * Fixed: Fixed some potential NullPointerExceptions.
* 1010music (thanks to Douglas Carmichael)
  * Fixed: The amplitude decay and release times were written with a different time scale than the one used when reading them back (25 seconds instead of 38 seconds full-scale), so a converted blackbox/Bento preset played its decay and release noticeably shorter than the source. The write scale now matches the read scale.
* disting EX (thanks to Douglas Carmichael)
  * Fixed: A misplaced parenthesis in the amplitude decay conversion divided only the decay time (not hold plus decay) by the time constant, so any hold time was effectively dropped from the written decay.
* Elektron Tonverk Multisample (thanks to Douglas Carmichael)
  * New: Relabelled "Elektron Tonverk Multisample" to not confuse it with the new "Elektron Tonverk Preset".
  * Fixed: Loops were dropped when reading the multi-sample mapping (.elmulti/.eldrum) format - the loop was parsed but never attached to the sample zone, so converted instruments lost their loop.
  * Fixed: A mapping slot without explicit sample-trim points read a sample start and end of -1 instead of the whole sample (e.g. a converted Waldorf QPAT then showed a sample start and end of -1 on the device).
* EXS24
  * New: Read/write filter envelope depth from/to modulation matrix.
  * Fixed: Loop type was not applied.
  * Fixed: Envelope times were converted from the EXS24 parameter linearly, but the device applies a fourth-power curve, so short times were greatly overstated - and the attack stage additionally skipped even the linear scaling, coming out about 12.7 times too long on top of that. A quick attack (e.g. 7.5 ms) was read as over a second, so plucked and struck instruments faded in too slowly to be heard and appeared silent. Envelope times are now converted with the hardware-calibrated curve seconds = 10 * (parameter / 127)^4, matching Logic to within one percent (thanks to Douglas Carmichael).
* FLAC/OGG
  * Fixed: FLAC or OGG samples stored inside a ZIP archive (e.g. discoDSP Bliss or DecentSampler libraries) could fail to decompress.
  * Fixed: Stereo (multi-channel) samples stored in a compressed format were truncated to half their length when decompressed while writing to an uncompressed destination.
  * Fixed: Implemented workaround for converting 32-bit FLAC files (might not always work).
  * Fixed: The number of sample frames (and the bit resolution) of an OGG file was reported as -1, since Vorbis does not store them in its header. Every destination format that works with the sample length wrote corrupt values for OGG sources - e.g. a Waldorf Quantum/Iridium preset converted from an SFZ with OGG samples wrote its loop points as large negative numbers instead of [0..1] fractions, so the device could not resolve the sample maps and showed the "Locate Samples" screen. The frame count is now read from the granule position of the last Ogg page (thanks to Douglas Carmichael).
  * Fixed: Decoding an OGG file dropped the final Vorbis block (about 10ms for a 44.1kHz file): the Java Sound wrapper around the decoder stops draining it at the last whole block and ignores the end-of-stream length trim. Every converted sample came out slightly short and sample loops ending at (or near) the end of the file lost their loop end. OGG files are now decoded with a direct decoder that drives the same underlying engine but emits all sample frames - the output length matches the granule position of the last Ogg page, so sample positions and loop points are sample-exact (thanks to Douglas Carmichael).
* Maschine 1
  * Fixed: File version number was always written as 0.
* Maschine 2/3 (thanks to Douglas Carmichael)
  * Fixed: The modulation (filter/pitch) envelope times were converted incorrectly. When reading, the attack and decay were left in milliseconds instead of seconds (a thousand times too long) and the release skipped the time-curve mapping entirely; when writing, the release skipped that mapping as well. The modulation envelope now uses the same conversion as the amplitude envelope.
* MPC
  * Fixed: Program in XTY file was not read.
* Omnisphere
  * New: Added support for reading envelope slopes.
  * New: If the required folder structure is not found when reading an Omnisphere preset, the files are now also searched in the same folder as the preset file.
  * Fixed: Reading an Omnisphere preset with multiple sample voice elements did only return the samples of the last voice.
  * Fixed: Save formatting of ampersand character when writing.
* SoundFont 2 (thanks to Douglas Carmichael)
  * New: A note is logged when the analyzed sample pitches of a preset consistently contradict its root keys by whole octaves, so presets that will sound octaves off are visible before converting (several E-mu E4 Producer Series presets carry roots one octave above the samples' true pitch) - the new Transpose processing option can correct this.
  * New: Added a "Keep mismatched stereo samples as mono" source option (off by default). Some SoundFonts - notably commercial E-mu banks - carry unreliable stereo links that flag two unrelated mono samples as a stereo pair; if their left and right halves also differ in length they were welded into a single stereo sample. When the option is enabled a length mismatch keeps the two samples separate as mono (a pitch or sample-rate mismatch always does). It is off by default because some banks contain genuine stereo pairs whose channels differ slightly in length.
  * Fixed: "Marker" presets that reference no samples (commercial SoundFonts often include one or two named after the vendor or copyright, e.g. "E-mu Systems 2007") were converted into empty instruments. Presets without any samples are now skipped.
  * Fixed: Marker presets whose samples contain only digital silence are now skipped as well - the E-mu E4 Producer Series banks mark their vendor presets with half a second of dithered silence instead of no sample at all.
  * Fixed: Stereo pairs with one channel that simply carries extra frames at its start (its loop and its length are offset by the same amount, e.g. every right channel sample in the DigitalSoundFactory E-mu E4 banks is 1 frame longer and loops 1 frame later) are now re-aligned when the channels are combined, instead of writing a skewed stereo file and warning about a loop mismatch.
  * Fixed: The "left and right samples do not match" notices (differing pitch, sample rate or length) are now only shown when the "log unsupported attributes" option is enabled, so a normal conversion of a quirky bank is no longer flooded with warnings.
* TX16W
  * Fixed: First check if the referenced absolute sample file path exists before searching all local folders.
* Waldorf Quantum/Iridium (thanks to Douglas Carmichael)
  * New: The preset Author and Bank fields can now be set with the *Author* and *Bank* creator options (or the CLI parameters QPATAuthor / QPATBank); leaving them empty keeps the values from the source. This lets a converted library be tagged so its presets are grouped and browsable by author and bank on the device.
  * New: A layered preset - one that stacks several samples on the same note (e.g. a body plus a swell, common in rompler banks) - is now spread across the three oscillators instead of collapsing into one, so the sound keeps its full body. Each set of zones that would sound simultaneously gets its own oscillator (up to three).
  * Fixed: Sample Loop mode 2 was not set to alternating but backwards.
  * Fixed: Samples were referenced with a leading drive number (an absolute path such as `4:samples/...`). This caused two problems on the device: a preset placed on a drive other than the hard-coded one showed the "Find Sample Map" screen and the samples had to be located by hand, and the device doubled the prefix when using its own "Export -> With Samples" (e.g. `3:2:samples/...`), so the samples could not be backed up. Sample paths are now written relative to the preset, which the device resolves against the folder the preset was loaded from - the samples load automatically on any drive and export/back up cleanly (confirmed on Iridium OS 4).
  * Fixed: A very short envelope time (at or below 0.06 seconds - in particular a zero attack, decay or release) was written as an out-of-range parameter value; exactly zero produced negative infinity. The corrupt value could cause a click at the start of every note on the device. Such times are now clamped to the shortest representable value.
  * Fixed: A very short but non-zero envelope attack, decay or release (below the ~0.06 second device minimum) collapsed to parameter value 0, i.e. an instant stage, which still clicked on note-on and note-off for samples that do not start or end at a zero crossing. Non-zero times are now clamped up to the shortest audible value instead of to instant, while a genuine zero stays an instant stage (verified on Iridium hardware).
  * Fixed: A patch with more than one sample map (a multi-oscillator sample-based patch) wrote every map's resource offset as 0, so on the device maps 2 and 3 were read overlapping map 1 and their samples could not be located - the device showed the "Find Sample Map" screen. Each map's offset is now written as the running total of the preceding maps' lengths (verified on Iridium hardware).
  * Fixed: An amplitude envelope with no attack and no decay that sustains below full level popped at the start of every note - the device snapped to the 100% attack peak and instantly dropped to the sustain level. Such an envelope is now written flat (full sustain) with the sustain level folded into the sample gain, so the loudness is unchanged but the discontinuity is gone.
  * Fixed: A sample zone without an explicit start/end (e.g. converted from a format that stores only loop points) was written with a sample start and end of -1; the whole sample is now used.
  * Fixed: A preset created from a single sample played at a fixed pitch instead of following the keyboard, because the Particle oscillator was not switched to its "Normal" sample mode (thanks to Douglas Carmichael).
  * Fixed: Sample start/end and loop positions are now clamped to the [0..1] range expected by the device. Source formats may reference positions slightly beyond the length of the audio data, e.g. loop points authored for the original sample but shipped with a lossy-compressed file that decodes to a marginally shorter length.

## 18.1.1

* New: If the source does not contain pitch bend values, the default is now 2 semi-tones (instead of 0).
* Ableton
  * Fixed: Created files could not be opened if the source file did not contain a loop.
* Akai MPC
  * Fixed: Root note was not read from WAV file when missing in XML.
* Ensoniq EPS/EPS16+/ASR-10
  * Fixed: Samples had appended silence which doubled the length of the sample.
* Omnisphere 3
  * Fixed: Pitch-bend was scaled wrong.

## 18.1.0

* New: Added CLI parameters ProcessAlwaysResample and ProcessLoopCrossfade.
* New: Added processing option to set a fixed loop cross-fade.
* New: Redesign of processing dialog.
* Elektron Tonverk (thanks to Douglas Carmichael)
  * New: Sample chunks are only written when a loop is present and instrument/broadcast audio chunks are off by default since the Tonverk WAV parser is strict (factory files only contain 'fmt ', 'data' and 'smpl' chunks).
  * Fixed: The preset file is now written with the correct '.elmulti' extension (was '.emulti') which the Tonverk requires.
  * Fixed: Samples are now physically trimmed to the zone start/end instead of writing 'trim-start'/'trim-end' which the Tonverk only supports for single-file multi-samples and rejected the preset otherwise.
  * Fixed: Loop positions written to the preset file were not updated for re-sampling and trimming. Loops are also clamped into the sample boundaries and short single-cycle loops keep their exact length when re-sampling to prevent pitch drift.
  * Fixed: A velocity layer with velocity 0.0 made the Tonverk reject the whole preset and import the WAV files as loose samples. The factory default velocity is used instead.
  * Fixed: The key-center was written with an inverted tuning direction.
  * Fixed: Sample file references in the preset file could differ from the written WAV file names if a zone name contained characters which needed to be replaced. Samples are now named following the Elektron factory convention 'Name-VVV-NNN-note.wav'.
  * Fixed: 'keep-looping-on-release' is now written for looped samples (the Tonverk otherwise stops looping on key release).
  * Fixed: Preset names containing a single quote produced an invalid preset file.
* Ensoniq EPS/EPS16+/ASR-10
  * New: Added a 'P' in front of the Patch-number for better readability.
  * Fixed: EFE files which use "Instrument" instead of "Instr" as the file type identifier could not be loaded.
* Omnisphere 3
  * Fixed: Samples with a delayed play-back start were not written (empty db-file). 

## 18.0.0

* Added support for Elektron Tonverk elmulti.
* Added support for Omnisphere 3.
* Added support for reading Roland S-50, S-330, S-550, W-30.
* Added support for reading Roland S-750, S-770, S-760, DJ-70, DJ-70 MkII, and SP-700.
* Added support for loop tuning: Ableton ADV/ADG, EXS24, Korgmultisample, Kontakt, SFZ, YSFC (partially).
* New: Processing can now up-sample as well (option: 'Always re-sample').
* New: Removed renaming feature.
* New: Made settings and processing dialogs non-resizable.
* Fixed: Processing did not work when Normalize was not enabled.
* Fixed: Processing did not work for 12-bit samples.
* 1010music samplers
  * New: If there are overlapping sample zones which so far cannot be handled by the 1010music samplers, the overlapping ones are removed to create limited but working output files.
* Akai MPC
  * New: Combined "Akai MPC Keygroup" and "Akai MPC Project/Track" detectors to "Akai MPC Modern".
  * New: Added support to read JSON based .xpm files.
* Akai S1000/S3000
  * Fixed: Loops were not imported.
* ISO File
  * New: Added detection of Ensoniq EPS/ASR ISOs.
  * New: Added detection of Roland images.
* Kontakt 4
  * Fixed: Added some workarounds for malformed umlauts in author field.
* Korgmultisample
  * Fixed: Sample files are now already checked for existence during scanning the sources. If the sample file is not found, it is searched in the same folder as the korgmultisample file.
* NI
  * New: Renamed "Kontakt NKI" to "NI Kontakt".
  * New: Renamed "Maschine Sound" to "NI Maschine".
* SFZ
  * New: Improved layout of metadata header with long description texts.
* WAV
  * Fixed: When writing WAV files the padding byte was counted as content.
  * Fixed: When writing WAV files preserve the chunks 'meta', 'atem' and 'ID3 '.
  * Fixed: Don't overwrite WAV samples multiple times if they already exist
  * Fixed: Failed resolution conversions are now logged properly.
  * Fixed: Conversion from 32-bit float to 16-bit PCM did not always work.

## 17.1.0

* Added support for reading Ensoniq Mirage disks (*.hfe, *.img, *.edm).
* Added support for reading Ensoniq EPS/EPS16+/ASR-10 disks (*.hfe, *.img, *.gkh, *.ede, *.eda, *.efe).
* Ableton Sampler
  * New: Read/write of round-robin setting (requires Ableton 12).
  * New: Add a creator option to either write files for Ableton 11 or Ableton 12.
  * New: Constant Power XF is set now to true (instead of linear crossfade).
  * Fixed: Transposition was off by 1 octave when writing.
* EXS24
  * Fixed: Group volume was not decoded correctly.
* Yamaha YSFC
  * Fixed: Samples need to be fixed to 44.1kHz (includes up-sampling).

## 17.0.0

* Added support for reading Akai MPC60 programs.
* Added support for reading Akai MPC500/MPC1000/MPC2500 programs.
* Added support for reading Akai MPC2000/MPC2000XL/MPC3000 programs.
* Added support for reading Akai S900/S950 programs.
* Added specific entry for Akai S1000/S3000 (and not only generic ISO). Searches for IMG files as well.
* New: Source formats show their file endings with a tooltip.
* ISO File
  * New: Added support for MPC2000 format.
  * New: Shows an info text if it is a plain ISO 9660 file which can be accessed with OS functionality.  
* Korg KMP
  * Fixed: Velocity layers need to be stored in separate KMP files.
* Yamaha YSFC
  * Fixed: Libraries are now limited to a max. of 128 performances.
  * Fixed: The performance names are now limited to 20 characters.

## 16.5.1

* Fixed: Processing: Sample reduction did not always work and improved logging.

## 16.5.0

* Added support for discoDSP Bliss.
* Added option to maximize samples.
* Added several options to minimize the size of a multi-sample.
* New: Improved sample writing progress logging output.
* Fixed: Don't report WAV files with padded zeros at the end as broken.
* 1010music Samplers
  * New: If the source material contains layered samples, a warning will be displayed.
* DecentSampler
  * New: Write seqLength attribute for group as well.
* Kontakt 5+
  * Fixed: Envelope hold and decay times were reversed.

## 16.2.0

* Added support for reading Akai MESA (*.s3p).
* Added support for reading Akai S1000/S3000 series images (*.iso).
* Fixed: Gain could not be set below +0.125dB.
* Fixed: Reading broken WAV files could make ConvertWithMoss hang.
* Ableton ADV, Sf2, TX16W, Yamaha YSFC
  * Fixed: Negative fine tuning values could be off by 1 when written.
* Akai AKP, MPC XPJ/XTY, TAL Sampler, TX16W
  * Fixed: Reading: Pitch-bend down was inverted (pitching up instead of down).
* DecentSampler
  * Fixed: Creating presets did miss adding seqMode attribute for round_robin groups.
* EXS24
  * Fixed: Writing: Coarse and fine tuning was always set to 0.
* Sf2
  * Fixed: 24-bit samples were not extracted correctly when read.

## 16.1.1

* Fixed: Application could not be closed if it was installed for the first time.
* Kontakt
  * Fixed: Prevent a crash when InternalModulator cannot be read.

## 16.1.0

* Added support for reading Akai MPC XPJ and XTY files.
* Akai AKP/AKM
  * New: Renamed "Akai S5000/S6000" to "Akai AKP/AKM".
  * New: Added reading support for Akai Z4/Z8/MPC4000 AKP/AKM format.
  * New: Added version information to the log file.
  * New: Improved conversion of filter resonance.
  * New: The root note is now modified instead re-pitching it via tuning parameter.
* Akai XPM
  * Fixed: Never read loops from WAV files.
* SFZ
  * Fixed: Prevent creation of filter type with poles not supported by SFZ (2 poles will be set in such a case).

## 16.0.0

* Added reading support for Akai S5000/S6000 AKP/AKM format.
* User Interface
  * Moved several setting to a specific Settings dialog.
  * Updated button icons.
* Fixed: Tuning value was set for panning.
* Kontakt
  * Fixed: Tuning was not written correctly.
* MPC Keygroups
  * New: Added an option to ignore loops

## 15.5.1

* Fixed: Split-stereo files were not combined into stereo files for formats which require it (e.g. Bento).

## 15.5.0

* Added support for 1010music Bento
* Fixed: The header of written FLAC files did not contain the sample length, which is valid but many readers rely on that value and crash otherwise.
* 1010music blackbox
  * Fixed: Empty sample trick for silent ranges was applied to single presets as well but the sample was not added.
* DecentSampler
  * Fixed: If the *Template and resources folder* was not set, the current folder was copied completely.
* Maschine 2/3
  * Fixed: Added workaround for presets which have set *Sampler* in the sound info as their name.
* MPC
  * New: The file version and source platform is now logged.
  * New: Improved check for valid loops. If none is present it is loaded from the WAV file if present.
* TAL Sampler
  * New: Conversion does not stop after first missing sample. All missing samples are logged.
  * Fixed: Could not read file when the program element had more than 200 attributes.
  * Fixed: Version 11 of the format has now a double to indicate of a layer is enabled or not which led to empty results.
* Yamaha YSFC
  * Fixed: End of loop was always set to the end of the sample.

## 15.1.0

* New: Added support for Maschine 1 MSND files.
* Fixed: Application icons show up again.
* Maschine MXSND
  * Fixed: Older Maschine 2 files were not converted correctly or did show exceptions.
* MPC Keygroups
  * Fixed: Don't read loops from WAV files which can cause unwanted full loops.

## 15.0.0

* New: Added support for Maschine MXSND files.
* Fixed: Restoration of main window on startup ensures that it is at least 25% visible on the screen.
* Fixed: CLI: Some parameters could be falsely rejected.
* 1010music
  * Fixed: Improved lookup of samples when reading presets.
* EXS
  * Fixed: Read loop cross-fade was not calculated correctly (integer instead of double).
  * Fixed: Loop cross-fade was written as samples not as milliseconds.
* SF2
  * New: Use all metadata fields for category detection if none could be extracted from the path.
* SFZ
  * Fixed: Read loop cross-fade was not calculated correctly (integer instead of double).
  * Fixed: Writing loop cross-fade was not calculated correctly (was rounded to full seconds).

## 14.2.0

* Kontakt
  * Fixed: Reading: Fixed an issue reading internal modulators.
* Korg KSF
  * Fixed: Reading: The play-back end is now set to the length of the sample to prevent issues with output formats which require the end (e.g. Korg wavestate).
  * Fixed: The KSF loop end is exclusive and therefore was off by 1.
* Soundfont 2 (thanks to Douglas Carmichael)
  * New: Added option to resample 24bit to 16bit.
  * Fixed: Always writes a global chunk.

## 14.1.0

* Logging: Improved logging output of missing samples. Added ConvertWithMoss version number and source/destination-format to log.
* Sample search: Added support for finding samples with wrong upper/lower case in the extension of the samples name.
* Improved processing cancellation.
* DecentSampler
  * New: The value for the amplitude velocity sensitivity is now initialized in the template via the new variable %ENV_VELOCITY_SENSITIVITY%.
  * New: The delay Mix default value is now set to zero in the template.
* Kontakt
  * Fixed: Reading of Soundinfo could fail in rare cases with file version 4.2.
  * Fixed: File lists of version 4.2.4 and 5.0.x were not always read correctly.
* Yamaha YSFC
  * Fixed: Montage files were not written correctly.

## 14.0.0

* The application can now be run without the user interface for batch processing via the command line interface (CLI). See the manual for details.
* The destination type has now a new option which allows to create performance libraries. Currently, only the Yamaha YSFC format is supported.
* 1010music
  * New: Can be a source format for performances.
  * New: Accept sample cells which are set to granular as well as a source.
  * Fixed: Filter cutoff frequency was not read correctly.
* Kontakt
  * New: Can be a source format for destination types library and performance.
  * Fixed: MIDI channels for Kontakt 4.2 multis were not read.
* Korgmultisample
  * Fixed: Potential crash when source file has no creation date set.
* TX16Wx
  * New: Can be a source and destination format for performances.
* Yamaha YSFC
  * New: Can be a source and destination format for Performances.
  * Fixed: Pitch Key Follow Sensitivity was not read/written.
  * Fixed: Filter types were not always mapped correctly.

## 13.1.0

* DecentSampler
  * New: Amplitude envelope settings are now aggregated to group or instrument level if they are identical.
  * New: The template effects.xml is now integrated into the ui.xml for simplicity reasons and brings new features (see the manual!).
  * New: Added an option to always add a low-pass filter on a group level. Enable it if you want to have controls for a filter envelope in your template.
  * New: Added a more fancy UI template with a volume envelope, filter incl. envelope, delay, reverb effect and pitch-modulation via mod-wheel.
* Korg KSC/KMP/KSF
  * New: KSC files get now a DOS-safe filename as well. Check for duplicated names is now separate for folders and normal files.
* Sample Files
  * New: Added option to ignore the loops in the source sample files.
* SFZ
  * New: Amplitude envelope settings are now aggregated to group or global level if they are identical.

## 13.0.0

* New: Rearranged the destination area of the user interface. There is now a new section which allows to switch between creating single patches, libraries containing multiple patches and performances which contain a certain configuration of patches (e.g. different MIDI channels). Output formats are filtered to the ones which support these options.
* New: Improved maximum size of RIFF files that can be written.
* Fixed: Envelope could be wrong if the input envelope uses the hold-time instead of decay-time and the output format does not support a hold-time.
* Fixed: The logging does now always scroll fully to the end when the conversion or analysis process has finished.
* 1010music format
  * New: Can be a destination format for Performances (see the manual for details).
  * New: Stereo-split samples are now combined (if possible) to stereo samples since the format does not support panning on a sample level.
* Kontakt
  * New: Kontakt can be an input format for Performances (see the manual for details).
  * New: Kontakt 4.2-7: Pitchmodulation by Pitchbend and Amplitudemodulation by Velocity are now read.
  * Fixed: Fixed crash with reading envelopes from NI-container.
* Decent Sampler
  * New: Write: There are now templates for the UI and effect sections which can be modified as well as further resources can be added automatically. See the manual for more info.
  * New: Read: Filter and pitch envelopes are now read as well.
  * Fixed: Read: Loops were not read from wave files when loop info was missing in DecentSampler file.
  * Fixed: Read: seqMode and seqPosition were falsely reported as not used.
  * Fixed: Write: Curve settings were not applied for filter and pitch envelopes.
  * Fixed: Write: Pitch envelopes do work now.
* Korg KSC/KMP/KSF - Read
  * New: Added option to load KSC files (instead of only KMP). Combines related mono files into stereo files.
  * New: The long name stored in the KMP is now set as the multi-samples name instead of the short filename.
* SF2
  * New: Added support to write as library (adds all found source-multi-samples into 1 sf2 file).

## 12.2.2

* Fixed: Checking for empty output folder ignores now the ConvertWithMoss log file.
* EXS
  * Fixed: The loop end was off by 1.
* SF2
  * Fixed: Added support for reading missing generators: startAddrsOffset, endAddrsOffset, startloopAddrsOffset and endloopAddrsOffset.
* SFZ
  * Fixed: Loops were not read from wave files when loop info was missing in SFZ file.

## 12.2.1

* EXS24
  * Fixed: Creating EXS files was broken (0 > -24).
* Kontakt 2
  * New: Round-robin information is read.
* Reason NN-XT
  * Fixed: Sample indices were not written correctly (every group started again to count from zero).

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
  * Fixed: Waveform panning was not always correct.

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
  * Fixed: Group volume, panning and key-tracking was not applied.
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
  * New: Added support for panning.
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
  * Fixed: Panning setting was not corrected when mono files were combined to stereo.
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
  * Fixed: Panning was not read / written.

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
* Fixed: Added missing reading of panning value.

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
