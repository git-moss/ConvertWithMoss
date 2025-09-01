// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.maschine.maschine2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
import de.mossgrabers.tools.StringUtils;
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

    private final static String                   BOOST_ARCHIVE_MAGIC     = "serialization::archive";
    private static final String                   NI_MASCHINE_DATA_TAG    = "NI::MASCHINE::DATA::";
    private final static String                   PLUGIN_MASCHINE_SAMPLER = "NI::MASCHINE::DATA::Sampler";

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
        // TODO remove
        // Files.write (new File ("C:/Users/mos/Desktop/Logs", "MaschinePreset-" +
        // sourceFile.getName () + ".bin").toPath (), data);

        final List<byte []> parameterArray;
        final byte [] version;
        try (final ByteArrayInputStream inputStream = new ByteArrayInputStream (data))
        {
            final long size = StreamUtils.readUnsigned32 (inputStream, false);
            if (size != inputStream.available ())
                throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Wrong data size"));

            inputStream.skipNBytes (1);
            final String magic = StreamUtils.readWith1ByteLengthAscii (inputStream);
            if (!BOOST_ARCHIVE_MAGIC.equals (magic))
                throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Magic boost bytes not found."));

            version = inputStream.readNBytes (7);
            inputStream.skipNBytes (6);

            parameterArray = readArray (inputStream);
        }

        final Optional<IMultisampleSource> source = this.readSounds (sourceFolder, sourceFile, parameterArray, version[1] < 0x0E);
        return source.isPresent () ? Collections.singletonList (source.get ()) : Collections.emptyList ();
    }


    private Optional<IMultisampleSource> readSounds (final File sourceFolder, final File sourceFile, final List<byte []> parameterArray, final boolean isOldFormat) throws IOException
    {
        // TODO remove
        dumpParameters (parameterArray, sourceFile);

        final boolean isGroup = sourceFile.getName ().toLowerCase (Locale.US).endsWith (".mxgrp");
        final int numSounds = isGroup ? 16 : 1;
        int groupOffset = isGroup ? 41 : 0;

        boolean finish = false;
        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        for (int i = 0; i < numSounds; i++)
        {
            // Sound starts at 91 / 642
            // 92 / 643 contains the name but it is taken from the SoundInfo
            final int offsetCountInfo = groupOffset + (isOldFormat ? 105 : 660);
            int offsetPluginInfo = groupOffset + (isOldFormat ? 109 : 665);
            int offsetNumberOfSamples = groupOffset + (isOldFormat ? 110 : 666);
            int offsetFirstZone = groupOffset + (isOldFormat ? 111 : 667);
            final int offsetZone = isOldFormat ? 59 : 80;

            final int numParameters = parameterArray.size ();
            if (numParameters < offsetFirstZone)
                return Optional.empty ();

            final int numberOfSamples;
            if (i == 0 || parameterArray.get (offsetCountInfo).length > 0)
            {
                // Check if there is at least 1 plug-in
                try
                {
                    final int [] values = readIntegerValues (parameterArray.get (offsetCountInfo), 12, isOldFormat);
                    if (values[11] < 1)
                    {
                        this.notifier.logError ("IDS_NI_MASCHINE_NO_SAMPLER_PLUGIN");
                        // We do not know the offset to the next sound...
                        return Optional.empty ();
                    }
                }
                catch (final IOException ex)
                {
                    // There are some which seem to have non-number content in there but also no
                    // sampler
                    this.notifier.logError ("IDS_NI_MASCHINE_NO_SAMPLER_PLUGIN");
                    // We do not know the offset to the next sound...
                    return Optional.empty ();
                }

                // 665 must contain "NI::MASCHINE::DATA::Sampler" at index 14 (14 = length)
                final byte [] pluginInfo = parameterArray.get (offsetPluginInfo);
                if (pluginInfo.length < 15)
                {
                    this.notifier.logError ("IDS_NI_MASCHINE_NO_SAMPLER", "Unknown");
                    // We do not know the offset to the next sound...
                    return Optional.empty ();
                }

                final ByteArrayInputStream pluginInfoIn = new ByteArrayInputStream (pluginInfo, isOldFormat ? 13 : 14, pluginInfo.length - (isOldFormat ? 13 : 14));
                String pluginID = StreamUtils.readWith1ByteLengthAscii (pluginInfoIn);
                if (!PLUGIN_MASCHINE_SAMPLER.equals (pluginID))
                {
                    if (pluginID.startsWith (NI_MASCHINE_DATA_TAG))
                        pluginID = pluginID.substring (NI_MASCHINE_DATA_TAG.length ());
                    this.notifier.logError ("IDS_NI_MASCHINE_NO_SAMPLER", pluginID);
                    // We do not know the offset to the next sound...
                    return Optional.empty ();
                }

                numberOfSamples = readIntegerValues (parameterArray.get (offsetNumberOfSamples), 6, isOldFormat)[5];
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
                final ByteArrayInputStream sampleInfoIn = new ByteArrayInputStream (parameterArray.get (offsetFirstZone + zoneOffset));

                if (i == 0 && sampleIndex == 0)
                {
                    final byte [] introBytes = sampleInfoIn.readNBytes (2);
                    if (introBytes.length == 0)
                    {
                        this.notifier.logError ("IDS_NI_MASCHINE_SAMPLER_HAS_NO_SAMPLES");
                        return Optional.empty ();
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
            int soundinfoIndex = findSoundinfo (parameterArray, offsetFirstZone + zoneOffset);
            if (soundinfoIndex == -1)
            {
                this.notifier.logError ("IDS_NI_MASCHINE_NO_SOUNDINFO");
                return Optional.empty ();
            }
            readSoundinfo (multisampleSource, parameterArray.get (soundinfoIndex), parts);
            // There is a 2nd one at the end
            soundinfoIndex = findSoundinfo (parameterArray, soundinfoIndex + 1);
            if (soundinfoIndex == -1)
            {
                this.notifier.logError ("IDS_NI_MASCHINE_NO_SOUNDINFO");
                return Optional.empty ();
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

        return Optional.of (multisampleSource);
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


    private static void readGlobalParameters (final IMultisampleSource multisampleSource, final List<byte []> parameterArray, final int offset, final boolean isOldFormat) throws IOException
    {
        // offset + 3 / + 9: Polyphony

        float [] floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 9 : 17)), 3, isOldFormat);

        // Translate the pitch-bend from normalized 0..1 to 0..1200 cents
        final float normalizedPitchend = Math.clamp (floatValues[0], 0, 1f);
        final int pitchbend = Math.round (100f * (12f * normalizedPitchend * normalizedPitchend));

        floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 23 : 35)), 3, isOldFormat);
        final float tuning = floatValues[0] * 36f;

        // Amp-Envelope: 0 = One-shot, 1 = AHD, 2 = AHDR
        final int envelopeType = readIntegerValue (parameterArray.get (offset + (isOldFormat ? 17 : 27)), isOldFormat);
        floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 84 : 116)), 3, isOldFormat);
        final double attackTime = mapToAttackMillis (floatValues[0]) / 1000.0;
        floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 87 : 120)), 3, isOldFormat);
        final double holdTime = mapToAttackMillis (floatValues[0]) / 1000.0;
        floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 90 : 124)), 3, isOldFormat);
        final double decayTime = mapToDecayAndRelease (floatValues[0]) / 1000.0;
        floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 93 : 128)), 3, isOldFormat);
        final double sustainLevel = floatValues[0];
        floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 96 : 132)), 3, isOldFormat);
        final double releaseTime = mapToDecayAndRelease (floatValues[0]) / 1000.0;

        final int reverse = readIntegerValue (parameterArray.get (offset + (isOldFormat ? 20 : 31)), isOldFormat);

        // Velocity modulation
        floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 66 : 92)), 3, isOldFormat);
        final double velocityToCutoff = floatValues[0];
        floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 69 : 96)), 3, isOldFormat);
        final double velocityToVolume = floatValues[0];

        // Modulation envelope
        floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 111 : 152)), 3, isOldFormat);
        final double modulationAttackTime = mapToAttackMillis (floatValues[0]);
        floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 117 : 160)), 3, isOldFormat);
        final double modulationDecayTime = mapToDecayAndRelease (floatValues[0]);
        floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 120 : 164)), 3, isOldFormat);
        final double modulationSustainLevel = floatValues[0];
        floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 123 : 168)), 3, isOldFormat);
        final double modulationReleaseTime = floatValues[0];

        // Modulation destinations
        floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 126 : 172)), 3, isOldFormat);
        final double pitchModulationIntensity = floatValues[0];
        floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 129 : 176)), 3, isOldFormat);
        final double cutoffModulationIntensity = floatValues[0];

        // Filter: 0 = Off, 1 = LP2, 2 = BP2, 3 = HP2, 4 = EQ
        final int filterType = readIntegerValue (parameterArray.get (offset + (isOldFormat ? 42 : 60)), isOldFormat);
        if (filterType > 0 && filterType < 4)
        {
            floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 45 : 64)), 3, isOldFormat);
            final double cutoff = mapToFrequency (floatValues[0]);
            floatValues = readFloatValues (parameterArray.get (offset + (isOldFormat ? 48 : 68)), 3, isOldFormat);
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
    private static void readZoneParameters (final ISampleZone zone, final int zoneOffset, final List<byte []> parameterArray, final boolean isOldFormat) throws IOException
    {
        zone.setStart (readIntegerValue (parameterArray.get (zoneOffset + (isOldFormat ? 114 : 671)), isOldFormat));
        zone.setStop (readIntegerValue (parameterArray.get (zoneOffset + (isOldFormat ? 116 : 674)), isOldFormat));

        if (readIntegerValue (parameterArray.get (zoneOffset + (isOldFormat ? 127 : 685)), isOldFormat) == 1)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setStart (readIntegerValue (parameterArray.get (zoneOffset + (isOldFormat ? 119 : 678)), isOldFormat));
            loop.setEnd (readIntegerValue (parameterArray.get (zoneOffset + (isOldFormat ? 121 : 681)), isOldFormat));
            loop.setCrossfadeInSamples (readIntegerValue (parameterArray.get (zoneOffset + (isOldFormat ? 127 : 689)), isOldFormat));
            zone.addLoop (loop);
        }

        zone.setKeyRoot (readIntegerValue (parameterArray.get (zoneOffset + (isOldFormat ? 136 : 701)), isOldFormat));
        zone.setKeyLow (readIntegerValue (parameterArray.get (zoneOffset + (isOldFormat ? 139 : 705)), isOldFormat));
        zone.setKeyHigh (readIntegerValue (parameterArray.get (zoneOffset + (isOldFormat ? 141 : 708)), isOldFormat));
        zone.setVelocityLow (readIntegerValue (parameterArray.get (zoneOffset + (isOldFormat ? 144 : 712)), isOldFormat));
        zone.setVelocityHigh (readIntegerValue (parameterArray.get (zoneOffset + (isOldFormat ? 146 : 715)), isOldFormat));

        zone.setGain (inputToDb (readFloatValue (parameterArray.get (zoneOffset + (isOldFormat ? 149 : 719)), isOldFormat)));
        zone.setPanning (readFloatValue (parameterArray.get (zoneOffset + (isOldFormat ? 152 : 723)), isOldFormat));
        zone.setTune (readFloatValue (parameterArray.get (zoneOffset + (isOldFormat ? 155 : 727)), isOldFormat));
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


    private static int readIntegerValue (final byte [] data, final boolean isOldFormat) throws IOException
    {
        // Ignores the first 00
        final ByteArrayInputStream in = new ByteArrayInputStream (data, isOldFormat ? 0 : 1, isOldFormat ? data.length : data.length - 1);
        return StreamUtils.readVariableLengthNumberLE (in);
    }


    private static int [] readIntegerValues (final byte [] data, final int numberCount, final boolean isOldFormat) throws IOException
    {
        final ByteArrayInputStream in = new ByteArrayInputStream (data, isOldFormat ? 0 : 1, isOldFormat ? data.length : data.length - 1);
        final int [] result = new int [numberCount];
        for (int i = 0; i < numberCount; i++)
            result[i] = StreamUtils.readVariableLengthNumberLE (in);
        return result;
    }


    private static float readFloatValue (final byte [] data, final boolean isOldFormat) throws IOException
    {
        final ByteArrayInputStream in = new ByteArrayInputStream (data, isOldFormat ? 0 : 1, isOldFormat ? data.length : data.length - 1);
        return StreamUtils.readFloatLE (in);
    }


    private static float [] readFloatValues (final byte [] data, final int numberOfFloats, final boolean isOldFormat) throws IOException
    {
        final ByteArrayInputStream in = new ByteArrayInputStream (data, isOldFormat ? 0 : 1, isOldFormat ? data.length : data.length - 1);
        float [] result = new float [numberOfFloats];
        for (int i = 0; i < numberOfFloats; i++)
            result[i] = StreamUtils.readFloatLE (in);
        return result;
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


    /**
     * Splits the data into an array of bytes. Each array entry starts with its index, stored in the
     * number representation (they start with 1 byte which contains the number of bytes of which the
     * number consists followed by the bytes in little-endian order).
     * 
     * @param inputStream The input stream to read from
     * @return The parameter array
     * @throws IOException Could not read/split the data
     */
    private static List<byte []> readArray (final InputStream inputStream) throws IOException
    {
        final List<byte []> chunks = new ArrayList<> ();
        int expectedIndex = 0;

        // Read the first index, must be 0
        int firstIndex = readStrictIndex (inputStream);
        if (firstIndex != expectedIndex)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Array index does not match"));

        int nextIndex;
        do
        {
            nextIndex = -1;
            expectedIndex++;

            final ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream ();
            while (true)
            {
                inputStream.mark (3);
                int header = inputStream.read ();
                if (header == -1)
                    break;

                // Is this a 1 byte index?
                if (header == 1)
                {
                    int b0 = inputStream.read ();
                    if (b0 == -1)
                    {
                        inputStream.reset ();
                        break;
                    }

                    int indexVal = b0;
                    if (indexVal == expectedIndex)
                    {
                        nextIndex = indexVal;
                        break;
                    }
                }
                // Is this a 2 byte index?
                else if (header == 2)
                {
                    int b0 = inputStream.read ();
                    int b1 = inputStream.read ();
                    if (b0 == -1 || b1 == -1)
                    {
                        inputStream.reset ();
                        break;
                    }
                    int indexVal = b1 << 8 | b0;
                    if (indexVal == expectedIndex)
                    {
                        nextIndex = indexVal;
                        break;
                    }
                }

                // No index byte - add to current data
                inputStream.reset ();
                dataBuffer.write (inputStream.read ());
            }

            chunks.add (dataBuffer.toByteArray ());

        } while (nextIndex != -1);

        return chunks;
    }


    /**
     * Reads the next index. An index starts with the number of index-bytes (1 or 2) followed by the
     * index bytes in little-endian order.
     * 
     * @param inputStream The input stream to read from
     * @return The read index
     * @throws IOException Could not read the index
     */
    private static int readStrictIndex (final InputStream inputStream) throws IOException
    {
        inputStream.mark (3);

        // Read the number of index bytes to follow
        int header = inputStream.read ();
        if (header == -1)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Unexpected end of stream when reading first index"));

        if (header > 2)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Invalid index header at stream start: " + header));

        int b0 = inputStream.read ();
        if (b0 == -1)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Incomplete index"));

        // There is one index byte
        if (header == 1)
            return b0;

        // There are two index bytes as little-endian
        int b1 = inputStream.read ();
        if (b1 == -1)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Incomplete index"));
        return b1 << 8 | b0;
    }


    // TODO remove
    private static void dumpParameters (final List<byte []> parameterArray, final File sourceFile) throws IOException
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("Read " + parameterArray.size () + " parameters.\n");
        for (int i = 0; i < parameterArray.size (); i++)
        {
            final byte [] data = parameterArray.get (i);
            if (data.length > 0)
                sb.append (i).append (": ").append (StringUtils.formatHexStr (data)).append (" - ").append (StringUtils.fixASCII (new String (data))).append ("\n");
        }

        Files.write (new File ("C:/Users/mos/Desktop/Logs", "MaschinePreset-" + sourceFile.getName () + ".txt").toPath (), sb.toString ().getBytes ());
    }
}
