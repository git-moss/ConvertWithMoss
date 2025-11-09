// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.maschine.maschine2;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.ui.Functions;


/**
 * Can read and write one Maschine sound to a Preset Chunk.
 *
 * @author Jürgen Moßgraber
 */
public class MaschinePresetAccessor
{
    private static final float                    MIN_DB                  = -82.3f;
    private static final float                    GAIN                    = 80.05f;
    /** Results to 0dB. */
    private static final float                    REFERENCE_DB            = 0.75f;
    private final static String                   PLUGIN_MASCHINE_SAMPLER = "Sampler";

    private final static Map<Integer, FilterType> FILTER_TYPE_LOOKUP      = new HashMap<> ();
    static
    {
        FILTER_TYPE_LOOKUP.put (Integer.valueOf (1), FilterType.LOW_PASS);
        FILTER_TYPE_LOOKUP.put (Integer.valueOf (2), FilterType.BAND_PASS);
        FILTER_TYPE_LOOKUP.put (Integer.valueOf (3), FilterType.HIGH_PASS);
    }

    private final INotifier notifier;


    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     */
    public MaschinePresetAccessor (final INotifier notifier)
    {
        this.notifier = notifier;
    }


    /**
     * Reads a Maschine 2+ preset chunk structure and the contained Maschine sounds.
     * 
     * @param sourceFolder The top source folder for the detection
     * @param sourceFile The source file to convert
     * @param data The preset data
     * @return The read multi-sample source
     * @throws IOException Could not parse the data
     */
    public List<IMultisampleSource> readMaschinePreset (final File sourceFolder, final File sourceFile, final byte [] data) throws IOException
    {
        final MaschinePresetParameterArray parameterArray = new MaschinePresetParameterArray (data);

        final boolean isMaschineGroupFile = sourceFile.getName ().toLowerCase (Locale.US).endsWith (".mxgrp");
        final int numSounds = isMaschineGroupFile ? 16 : 1;
        int groupOffset = isMaschineGroupFile ? 41 : 0;
        final boolean isOldFormat = parameterArray.isOldFormat ();
        final List<byte []> parameterArrayRaw = parameterArray.getRawData ();
        final int offsetZone = isOldFormat ? 59 : 80;

        boolean finish = false;
        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        for (int i = 0; i < numSounds; i++)
        {
            // 91 / 642: Start of Sound
            // 92 / 643: contains the name but it is taken from the SoundInfo
            int offsetCountInfo = groupOffset + (isOldFormat ? 105 : 660);
            int offsetPluginInfo = groupOffset + (isOldFormat ? 109 : 665);
            int offsetNumberOfSamples = groupOffset + (isOldFormat ? 110 : 666);
            int offsetFirstZone = groupOffset + (isOldFormat ? 111 : 667);
            if (offsetFirstZone > parameterArrayRaw.size ())
                return Collections.emptyList ();

            final int numberOfSamples;
            if (i == 0 || parameterArrayRaw.get (offsetCountInfo).length > 0)
            {
                // Check if there is at least 1 plug-in
                try
                {
                    final int [] values = parameterArray.readIntegerValues (offsetCountInfo, 12);
                    if (values[11] < 1)
                    {
                        this.notifier.logError ("IDS_NI_MASCHINE_NO_SAMPLER_PLUGIN");
                        // We do not know the offset to the next sound... execute search below
                    }
                }
                catch (final IOException ex)
                {
                    // There are some which seem to have non-number content in there but also no
                    // sampler
                    this.notifier.logError ("IDS_NI_MASCHINE_NO_SAMPLER_PLUGIN");
                    // We do not know the offset to the next sound... execute search below
                }

                boolean foundSampler = false;
                do
                {
                    // 665 must contain "NI::MASCHINE::DATA::Sampler" otherwise search is executed
                    final Pair<Integer, String> nextMaschineDevice = parameterArray.findNextMaschineDevice (offsetPluginInfo);
                    final int newOffsetPluginInfo = nextMaschineDevice.getKey ().intValue ();
                    if (newOffsetPluginInfo < 0)
                        return Collections.emptyList ();
                    if (newOffsetPluginInfo != offsetPluginInfo)
                    {
                        // Adjust all offsets
                        offsetPluginInfo = newOffsetPluginInfo;
                        groupOffset = offsetPluginInfo - (isOldFormat ? 109 : 665);
                        offsetCountInfo = groupOffset + (isOldFormat ? 105 : 660);
                        offsetNumberOfSamples = groupOffset + (isOldFormat ? 110 : 666);
                        offsetFirstZone = groupOffset + (isOldFormat ? 111 : 667);
                    }

                    foundSampler = PLUGIN_MASCHINE_SAMPLER.equals (nextMaschineDevice.getValue ());
                    if (!foundSampler)
                    {
                        this.notifier.logError ("IDS_NI_MASCHINE_NO_SAMPLER", nextMaschineDevice.getValue ());
                        offsetPluginInfo++;
                    }
                } while (!foundSampler);

                numberOfSamples = parameterArray.readIntegerValues (offsetNumberOfSamples, 6)[5];
            }
            else
            {
                // Group Kit: It seems there are more samplers but without being explicitly named
                offsetFirstZone -= 4;
                groupOffset -= 4;
                numberOfSamples = 1;
            }

            // Create the multi-sample with 1 group
            final String name = FileUtils.getNameWithoutType (sourceFile);
            final File parentFile = sourceFile.getParentFile ();
            final String [] parts = AudioFileUtils.createPathParts (parentFile, sourceFolder, name);
            final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, name, AudioFileUtils.subtractPaths (sourceFolder, sourceFile));
            final IGroup group = new DefaultGroup ();
            multisampleSource.setGroups (Collections.singletonList (group));

            int zoneOffset = 0;
            final List<String> filePaths = new ArrayList<> ();
            for (int sampleIndex = 0; sampleIndex < numberOfSamples; sampleIndex++)
            {
                // Sample info parameter: 4 bytes - 5 bytes for libraries; path with a max. of 256
                // characters; 3 more bytes (01 01 00)
                final ByteArrayInputStream sampleInfoIn = new ByteArrayInputStream (parameterArrayRaw.get (offsetFirstZone + zoneOffset));

                if (i == 0 && sampleIndex == 0)
                {
                    final byte [] introBytes = sampleInfoIn.readNBytes (2);
                    if (introBytes.length == 0)
                    {
                        this.notifier.logError ("IDS_NI_MASCHINE_SAMPLER_HAS_NO_SAMPLES");
                        return Collections.emptyList ();
                    }
                    if (introBytes[0] != 0 || introBytes[1] != 0)
                        throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Unknown Sampleinfo structure"));
                }

                if (sampleInfoIn.available () == 0)
                {
                    this.notifier.logError ("IDS_NI_MASCHINE_NO_MORE_SAMPLES");
                    finish = true;
                    break;
                }

                final String samplePath;
                // Library
                if (sampleInfoIn.read () > 0)
                {
                    sampleInfoIn.skip (2);
                    samplePath = StreamUtils.readWith1ByteLengthAscii (sampleInfoIn);
                }
                else
                {
                    sampleInfoIn.skip (1);
                    samplePath = StreamUtils.readWith1ByteLengthAscii (sampleInfoIn);
                }
                filePaths.add (samplePath);

                final ISampleZone zone = new DefaultSampleZone ();
                group.addSampleZone (zone);
                readZoneParameters (zone, groupOffset + zoneOffset, parameterArray, isOldFormat);

                zoneOffset += offsetZone;
            }

            if (finish)
                break;

            final List<File> files = this.lookupFiles (filePaths, sourceFile.getParent ());
            for (int fileIndex = 0; fileIndex < files.size (); fileIndex++)
            {
                final File file = files.get (fileIndex);
                final ISampleZone zone = group.getSampleZones ().get (fileIndex);
                zone.setName (FileUtils.getNameWithoutType (file));
                zone.setSampleData (AbstractDetector.createSampleData (file, this.notifier));
            }

            readGlobalParameters (multisampleSource, parameterArray, offsetFirstZone + zoneOffset, isOldFormat);
            int soundinfoIndex = findSoundinfo (parameterArrayRaw, offsetFirstZone + zoneOffset);
            if (soundinfoIndex == -1)
            {
                this.notifier.logError ("IDS_NI_MASCHINE_NO_SOUNDINFO");
                return Collections.emptyList ();
            }
            readSoundinfo (multisampleSource, parameterArrayRaw.get (soundinfoIndex), parts);
            // There is a 2nd one at the end
            soundinfoIndex = findSoundinfo (parameterArrayRaw, soundinfoIndex + 1);
            if (soundinfoIndex == -1)
            {
                this.notifier.logError ("IDS_NI_MASCHINE_NO_SOUNDINFO");
                return Collections.emptyList ();
            }

            groupOffset = soundinfoIndex + 1;

            multisampleSources.add (multisampleSource);
        }

        // Another crude hack to combine the 16 single drum sounds into 1 multi-sample
        final IMultisampleSource multisampleSource = multisampleSources.get (0);
        if (multisampleSources.size () > 1)
        {
            int note = 48;
            final List<ISampleZone> sampleZones = multisampleSource.getGroups ().get (0).getSampleZones ();
            final ISampleZone sampleZone = sampleZones.get (0);
            sampleZone.setKeyLow (note);
            sampleZone.setKeyHigh (note);
            for (int i = 1; i < multisampleSources.size (); i++)
            {
                note++;
                final ISampleZone sampleZone2 = multisampleSources.get (i).getGroups ().get (0).getSampleZones ().get (0);
                sampleZone2.setKeyLow (note);
                sampleZone2.setKeyHigh (note);
                sampleZones.add (sampleZone2);
            }
        }

        return Collections.singletonList (multisampleSource);
    }


    private static int findSoundinfo (final List<byte []> parameterArray, final int startIndex)
    {
        for (int i = startIndex; i < parameterArray.size (); i++)
        {
            final byte [] data = parameterArray.get (i);
            // Crude hack which assumes that the SoundInfo content is longer than all other ones
            if (data != null && data.length > 40 && data[0] != 0)
                return i;
        }
        return -1;
    }


    private static void readGlobalParameters (final IMultisampleSource multisampleSource, final MaschinePresetParameterArray parameterArray, final int offset, final boolean isOldFormat) throws IOException
    {
        // offset + 3 / + 9: Polyphony

        // Up to 3 but not always
        float [] floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 9 : 17), 1);

        // Translate the pitch-bend from normalized 0..1 to 0..1200 cents
        final float normalizedPitchend = Math.clamp (floatValues[0], 0, 1f);
        final int pitchbend = Math.round (100f * (12f * normalizedPitchend * normalizedPitchend));

        floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 23 : 35), 3);
        final float tuning = floatValues[0] * 36f;

        // Amp-Envelope: 0 = One-shot, 1 = AHD, 2 = AHDR
        final int envelopeType = parameterArray.readIntegerValue (offset + (isOldFormat ? 17 : 27));
        floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 84 : 116), 3);
        final double attackTime = mapToAttackMillis (floatValues[0]) / 1000.0;
        floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 87 : 120), 3);
        final double holdTime = mapToAttackMillis (floatValues[0]) / 1000.0;
        floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 90 : 124), 3);
        final double decayTime = mapToDecayAndRelease (floatValues[0]) / 1000.0;
        floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 93 : 128), 3);
        final double sustainLevel = floatValues[0];
        floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 96 : 132), 3);
        final double releaseTime = mapToDecayAndRelease (floatValues[0]) / 1000.0;

        final int reverse = parameterArray.readIntegerValue (offset + (isOldFormat ? 20 : 31));

        // Velocity modulation
        floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 66 : 92), 3);
        final double velocityToCutoff = floatValues[0];
        floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 69 : 96), 3);
        final double velocityToVolume = floatValues[0];

        // Modulation envelope
        floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 111 : 152), 3);
        final double modulationAttackTime = mapToAttackMillis (floatValues[0]);
        floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 117 : 160), 3);
        final double modulationDecayTime = mapToDecayAndRelease (floatValues[0]);
        floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 120 : 164), 3);
        final double modulationSustainLevel = floatValues[0];
        floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 123 : 168), 3);
        final double modulationReleaseTime = floatValues[0];

        // Modulation destinations
        floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 126 : 172), 3);
        final double pitchModulationIntensity = floatValues[0];
        floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 129 : 176), 3);
        final double cutoffModulationIntensity = floatValues[0];

        // Filter: 0 = Off, 1 = LP2, 2 = BP2, 3 = HP2, 4 = EQ
        final int filterType = parameterArray.readIntegerValue (offset + (isOldFormat ? 42 : 60));
        if (filterType > 0 && filterType < 4)
        {
            floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 45 : 64), 3);
            final double cutoff = mapToFrequency (floatValues[0]);
            floatValues = parameterArray.readFloatValues (offset + (isOldFormat ? 48 : 68), 3);
            final double resonance = floatValues[0];

            final IFilter filter = new DefaultFilter (FILTER_TYPE_LOOKUP.get (Integer.valueOf (filterType)), 2, cutoff, Math.clamp (resonance, 0, 1));
            if (cutoffModulationIntensity != 0)
            {
                final IEnvelope globalFilterEnvelope = new DefaultEnvelope ();
                globalFilterEnvelope.setAttackTime (modulationAttackTime);
                globalFilterEnvelope.setDecayTime (modulationDecayTime);
                globalFilterEnvelope.setSustainLevel (modulationSustainLevel);
                globalFilterEnvelope.setReleaseTime (modulationReleaseTime);

                final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
                cutoffModulator.setDepth (cutoffModulationIntensity);
                cutoffModulator.setSource (globalFilterEnvelope);

                filter.getCutoffVelocityModulator ().setDepth (velocityToCutoff);
            }

            multisampleSource.setGlobalFilter (filter);
        }

        for (final IGroup group: multisampleSource.getGroups ())
        {
            for (final ISampleZone zone: group.getSampleZones ())
            {
                zone.setReversed (reverse == 1);
                zone.setBendUp (pitchbend);
                zone.setBendDown (-pitchbend);
                zone.setTune (tuning + zone.getTune ());

                // Amplitude envelope
                final IEnvelopeModulator amplitudeEnvelopeModulator = zone.getAmplitudeEnvelopeModulator ();
                final IEnvelope ampEnvelope = amplitudeEnvelopeModulator.getSource ();
                // Since there is no one-shot setting, crank up the release
                if (envelopeType == 0)
                    ampEnvelope.setReleaseTime (10);
                else
                {
                    ampEnvelope.setAttackTime (attackTime);
                    if (envelopeType == 1)
                        ampEnvelope.setHoldTime (holdTime);
                    ampEnvelope.setDecayTime (decayTime);
                    if (envelopeType == 2)
                    {
                        ampEnvelope.setReleaseTime (releaseTime);
                        ampEnvelope.setSustainLevel (sustainLevel);
                    }
                }
                zone.getAmplitudeVelocityModulator ().setDepth (velocityToVolume);

                // Pitch envelope
                if (pitchModulationIntensity != 0)
                {
                    final IEnvelope pitchEnvelope = new DefaultEnvelope ();
                    pitchEnvelope.setAttackTime (modulationAttackTime);
                    pitchEnvelope.setDecayTime (modulationDecayTime);
                    pitchEnvelope.setSustainLevel (modulationSustainLevel);
                    pitchEnvelope.setReleaseTime (modulationReleaseTime);

                    final IEnvelopeModulator pitchModulator = zone.getPitchModulator ();
                    pitchModulator.setDepth (pitchModulationIntensity);
                    pitchModulator.setSource (pitchEnvelope);
                }
            }
        }
    }


    /**
     * Read the parameters for a sample zone.
     * 
     * @param zone The sample zone to fill
     * @param zoneOffset The offset to the zone
     * @param parameterArray The parameters
     * @param isOldFormat True if the file format version is older than 0x0E
     * @throws IOException Could not read the zone data
     */
    private static void readZoneParameters (final ISampleZone zone, final int zoneOffset, final MaschinePresetParameterArray parameterArray, final boolean isOldFormat) throws IOException
    {
        zone.setStart (parameterArray.readIntegerValue (zoneOffset + (isOldFormat ? 114 : 671)));
        zone.setStop (parameterArray.readIntegerValue (zoneOffset + (isOldFormat ? 116 : 674)));

        if (parameterArray.readIntegerValue (zoneOffset + (isOldFormat ? 127 : 685)) == 1)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setStart (parameterArray.readIntegerValue (zoneOffset + (isOldFormat ? 119 : 678)));
            loop.setEnd (parameterArray.readIntegerValue (zoneOffset + (isOldFormat ? 121 : 681)));
            loop.setCrossfadeInSamples (parameterArray.readIntegerValue (zoneOffset + (isOldFormat ? 127 : 689)));
            zone.addLoop (loop);
        }

        zone.setKeyRoot (parameterArray.readIntegerValue (zoneOffset + (isOldFormat ? 136 : 701)));
        zone.setKeyLow (parameterArray.readIntegerValue (zoneOffset + (isOldFormat ? 139 : 705)));
        zone.setKeyHigh (parameterArray.readIntegerValue (zoneOffset + (isOldFormat ? 141 : 708)));
        zone.setVelocityLow (parameterArray.readIntegerValue (zoneOffset + (isOldFormat ? 144 : 712)));
        zone.setVelocityHigh (parameterArray.readIntegerValue (zoneOffset + (isOldFormat ? 146 : 715)));

        zone.setGain (inputToDb (parameterArray.readFloatValue (zoneOffset + (isOldFormat ? 149 : 719))));
        zone.setPanning (parameterArray.readFloatValue (zoneOffset + (isOldFormat ? 152 : 723)));
        zone.setTune (parameterArray.readFloatValue (zoneOffset + (isOldFormat ? 155 : 727)));
    }


    /**
     * Reads the SoundInfo data and fills the metadata of the given multi-sample source with it.
     * 
     * @param multisampleSource The multi-sample source in which to store the read data
     * @param data The sound info data
     * @param parts The file parts for category detection if no categories are stored in the sound
     *            info
     * @throws IOException Could not read the sound info section
     */
    private static void readSoundinfo (final IMultisampleSource multisampleSource, final byte [] data, final String [] parts) throws IOException
    {
        final IMetadata metadata = multisampleSource.getMetadata ();

        final ByteArrayInputStream soundInfoIn = new ByteArrayInputStream (data);
        StreamUtils.readUnsigned16 (soundInfoIn, false);

        // Could be a version: 02 02
        StreamUtils.readUnsigned16 (soundInfoIn, false);

        // Unknown
        soundInfoIn.skipNBytes (11);

        final String soundName = StreamUtils.readWithLengthUTF16 (soundInfoIn);
        if (!soundName.isBlank ())
            multisampleSource.setName (soundName);
        final String soundAuthor = StreamUtils.readWithLengthUTF16 (soundInfoIn);
        if (!soundAuthor.isBlank ())
            metadata.setCreator (soundAuthor);
        final String soundCompany = StreamUtils.readWithLengthUTF16 (soundInfoIn);
        if (!soundCompany.isBlank ())
            metadata.setDescription (soundCompany);

        // 0, 0, 0, 0, 0, 0, 0, 0
        soundInfoIn.skipNBytes (8);
        // -1, -1, -1, -1, -1, -1, -1, -1
        soundInfoIn.skipNBytes (8);
        // 0, 0, 0, 0, 0, 0, 0, 0
        soundInfoIn.skipNBytes (8);
        // 0, 0, 0, 0, 0, 0, 0, 0
        soundInfoIn.skipNBytes (8);
        // 0, 0, 0, 0
        soundInfoIn.skipNBytes (4);
        // 1, 0, 0, 0
        soundInfoIn.skipNBytes (4);

        // Library info
        final int numLibraryInfo = (int) StreamUtils.readUnsigned32 (soundInfoIn, false);
        final List<String> libraryInfo = new ArrayList<> ();
        for (int i = 0; i < numLibraryInfo; i++)
            libraryInfo.add (StreamUtils.readWithLengthUTF16 (soundInfoIn));

        // Categories
        final int numCategoryPath = (int) StreamUtils.readUnsigned32 (soundInfoIn, false);
        final List<String> categoryPath = new ArrayList<> ();
        String categoryPart = "";
        // We only use the last one which contains the full sub-categories path
        for (int i = 0; i < numCategoryPath; i++)
            categoryPart = StreamUtils.readWithLengthUTF16 (soundInfoIn);
        final String [] categoryParts = categoryPart.split ("\\\\:");
        for (int i = categoryParts.length - 1; i >= 0; i--)
        {
            if (!categoryParts[i].isBlank ())
                categoryPath.add (categoryParts[i]);
        }
        String detectedCategory = TagDetector.detectCategory (categoryPath);
        if (TagDetector.CATEGORY_UNKNOWN.equals (detectedCategory))
            detectedCategory = TagDetector.detectCategory (parts);
        metadata.setCategory (detectedCategory);
        metadata.setKeywords (TagDetector.detectKeywords (categoryPath));

        // Padding
        StreamUtils.readUnsigned32 (soundInfoIn, false);

        final int numAttributes = (int) StreamUtils.readUnsigned32 (soundInfoIn, false);
        final Map<String, String> attributes = new HashMap<> ();
        for (int i = 0; i < numAttributes; i++)
        {
            final String name = StreamUtils.readWithLengthUTF16 (soundInfoIn);
            final String value = StreamUtils.readWithLengthUTF16 (soundInfoIn);
            attributes.put (name, value);
        }
    }


    /**
     * File paths are either absolute or relative to a library root (which is not known). Therefore,
     * the sample files need to be located.
     * 
     * @param filePaths The read paths
     * @param sourceFilePath The location of the Maschine file
     * @return The corrected file paths
     */
    private List<File> lookupFiles (final List<String> filePaths, final String sourceFilePath)
    {
        int missingFiles = 0;

        final List<File> files = new ArrayList<> ();
        for (final String filename: filePaths)
        {
            File sampleFile = new File (filename);
            if (sampleFile.isAbsolute () && !sampleFile.exists ())
                sampleFile = new File (sourceFilePath, sampleFile.getName ());
            else
            {
                sampleFile = new File (sourceFilePath, filename);
                if (!sampleFile.exists ())
                {
                    // This should work for libraries
                    sampleFile = new File (new File (sourceFilePath).getParentFile ().getParentFile (), sampleFile.getName ());
                    if (!sampleFile.exists ())
                        sampleFile = new File (sourceFilePath, sampleFile.getName ());
                }
            }

            if (!sampleFile.exists ())
                missingFiles++;

            files.add (sampleFile);
        }

        // Only search for missing files, if all of them are missing!
        if (missingFiles != files.size ())
            return files;

        final List<File> lookedupFiles = new ArrayList<> ();
        File previousFolder = null;

        for (final File sampleFile: files)
        {
            // Find the sample file starting 2 folders up, which should work for libraries
            final int height = 2;
            final File file = AbstractDetector.findSampleFile (this.notifier, sampleFile.getParentFile (), previousFolder, sampleFile.getName (), height);
            if (file != null && file.exists ())
            {
                lookedupFiles.add (file);
                previousFolder = file.getParentFile ();
            }
            else
                lookedupFiles.add (sampleFile);
        }
        return lookedupFiles;
    }


    /**
     * Converts gain in the range of [0..1] to the range [-82.3f..10.0f]. -82.3f is very close to
     * -Inf.
     * 
     * @param input The input value in the range of 0..1[]
     * @return The gain value in dB
     */
    public static float inputToDb (final float input)
    {
        if (input <= 0.0f)
            return MIN_DB;
        float db = GAIN * (float) Math.log10 (input / REFERENCE_DB);
        return Math.max (db, MIN_DB);
    }


    // Not perfect but close
    private static float mapToAttackMillis (final float v)
    {
        final float x = Math.clamp (v, 0, 1f);
        if (x <= 0.5f)
        {
            // from (0,0) to (0.5,171)
            return (171f / 0.5f) * x;
        }
        if (x <= 0.85f)
        {
            // from (0.5,171) to (0.85,2100)
            return 171f + ((2100f - 171f) / (0.85f - 0.5f)) * (x - 0.5f);
        }

        // from (0.85,2100) to (1,7700)
        return 2100f + ((7700f - 2100f) / (1f - 0.85f)) * (x - 0.85f);
    }


    // Not perfect but close
    private static float mapToDecayAndRelease (final float v)
    {
        final float x = Math.clamp (v, 0, 1f);
        if (x <= 0.29f)
        {
            // 0.00 → 2.9 ; 0.29 → 72.7
            return 2.9f + (72.7f - 2.9f) / 0.29f * x;
        }
        if (x <= 0.50f)
        {
            // 0.29 → 72.7 ; 0.50 → 217.0
            return 72.7f + (217f - 72.7f) / (0.50f - 0.29f) * (x - 0.29f);
        }
        if (x <= 0.77f)
        {
            // 0.50 → 217.0; 0.77 → 1500.0
            return 217f + (1500f - 217f) / (0.77f - 0.50f) * (x - 0.50f);
        }

        // 0.77 → 1500.0; 1.00 → 12300.0
        return 1500f + (12300f - 1500f) / (1.00f - 0.77f) * (x - 0.77f);
    }


    // Maps input x in [0,1] to frequency in Hz using exponential curve, does not match 100% but
    // very close
    private static double mapToFrequency (final double x)
    {
        return Math.clamp (51.0917 * Math.exp (5.9497 * x), 43.7, 19600);
    }
}
