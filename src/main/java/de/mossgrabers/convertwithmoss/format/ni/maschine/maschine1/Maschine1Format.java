// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.maschine.maschine1;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.IMetadataConfig;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.SoundinfoDocument;
import de.mossgrabers.convertwithmoss.format.ni.maschine.IMaschineFormat;
import de.mossgrabers.convertwithmoss.format.ni.maschine.maschine2.MaschinePresetAccessor;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Can handle Maschine 1 MSND format files.
 *
 * @author Jürgen Moßgraber
 */
public class Maschine1Format implements IMaschineFormat
{
    /** The 4 start bytes of the format signaling little-endian. */
    public static final String                    START_TAG_LITTLE_ENDIAN = "-in-";
    /** The 4 start bytes of the format signaling big-endian. */
    public static final String                    START_TAG_BIG_ENDIAN    = "-ni-";

    private static final String                   V1_SOUND_TAG            = "#NI#CS#Document##NI#SoundShell#Sound#";
    private static final String                   V1_DATA_TAG             = "data";
    private static final Set<String>              NO_CLOSING_TAG          = new HashSet<> ();
    private static final Map<Integer, FilterType> FILTER_TYPES            = new HashMap<> ();

    static
    {
        NO_CLOSING_TAG.add ("vt  ");
        NO_CLOSING_TAG.add ("vri ");
        NO_CLOSING_TAG.add ("osid");

        FILTER_TYPES.put (Integer.valueOf (2), FilterType.LOW_PASS);
        FILTER_TYPES.put (Integer.valueOf (3), FilterType.BAND_PASS);
        FILTER_TYPES.put (Integer.valueOf (4), FilterType.HIGH_PASS);
    }

    private final INotifier                   notifier;
    private long                              unknownSize;
    private DataSection                       topSection              = new DataSection ();

    private final Map<Integer, DataParameter> globalParametersVriTags = new TreeMap<> ();
    private boolean                           isBigEndian;
    private DataSection                       zoneDataSection;
    private DataTag                           zoneParametersDataTag;
    private DataSection                       presetData;
    private DataTag                           presetParametersDataTag;


    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     */
    public Maschine1Format (final INotifier notifier)
    {
        this.notifier = notifier;
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readSound (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess, final IMetadataConfig metadataConfig) throws IOException
    {
        try (final InputStream inputStream = Channels.newInputStream (fileAccess.getChannel ()))
        {
            if (!this.readHeader (inputStream))
                return Collections.emptyList ();

            // Create the multi-sample with 1 group
            final String name = FileUtils.getNameWithoutType (sourceFile);
            final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), sourceFolder, name);
            final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, name, AudioFileUtils.subtractPaths (sourceFolder, sourceFile));
            final IGroup group = new DefaultGroup ();
            multisampleSource.setGroups (Collections.singletonList (group));

            // Update metadata from SoundInfo XML document if present
            final DataSection metaData = getData (this.topSection.children, "gtr ", "info");
            if (metaData != null && metaData.data != null)
                updateMetadata (multisampleSource, metaData.data);

            // Normally but not always in: "gtr " -> "gtfs" -> "psg " -> "gznc"
            this.zoneDataSection = findData (this.topSection, "gznc");
            if (this.zoneDataSection != null && this.zoneDataSection.data != null)
            {
                // Read all zones

                this.zoneParametersDataTag = readDataSectionParameters (new ByteArrayInputStream (this.zoneDataSection.data), this.isBigEndian);

                // gzn has gsl/dfp pairs as children which represent 1 zone! But there can also be
                // more like "osid"!
                int zoneIndex = 0;
                final List<String> filePaths = new ArrayList<> ();
                while (true)
                {
                    final int lsgIndex = findNextTag ("gsl ", zoneIndex, this.zoneParametersDataTag.children);
                    if (lsgIndex == -1)
                        break;

                    final Map<String, DataParameter> params = collectZoneParameters (this.zoneParametersDataTag, lsgIndex);
                    final ISampleZone sampleZone = createSampleZone (params, filePaths);
                    group.addSampleZone (sampleZone);

                    zoneIndex = lsgIndex + 2;
                }

                MaschinePresetAccessor.assignSampleFile (sourceFile, group, filePaths, this.notifier);
                // Correct loop length
                for (final ISampleZone sampleZone: group.getSampleZones ())
                {
                    final List<ISampleLoop> loops = sampleZone.getLoops ();
                    if (!loops.isEmpty ())
                    {
                        final ISampleLoop sampleLoop = loops.get (0);
                        final int length = sampleZone.getSampleData ().getAudioMetadata ().getNumberOfSamples ();
                        sampleLoop.setEnd (sampleLoop.getEnd () + length);
                    }
                }

                this.applyGlobalParameters (multisampleSource);
            }

            return Collections.singletonList (multisampleSource);
        }
    }


    private void applyGlobalParameters (final IMultisampleSource multisampleSource) throws IOException
    {
        // Read global sampler parameters
        this.presetData = getData (this.zoneDataSection.parent.children, "gemo", "prst");
        if (this.presetData == null || this.presetData.data == null)
            return;

        this.presetParametersDataTag = readDataSectionParameters (new ByteArrayInputStream (this.presetData.data), this.isBigEndian);
        final DataTag paramsTag = getDataTag (this.presetParametersDataTag, "dfp ", "pac ");
        this.globalParametersVriTags.clear ();
        for (final DataTag childTag: paramsTag.children)
        {
            final DataTag vriTag = getDataTag (childTag, "par ", "osid", "vr  ", "vri ");
            final int paramIndex = vriTag.parameter.name.getBytes ()[3];
            this.globalParametersVriTags.put (Integer.valueOf (paramIndex), vriTag.parameter);
        }

        // Apply all parameters
        int size = this.globalParametersVriTags.size ();
        if (size == 54)
        {
            // TODO
        }
        else if (size == 43)
        {
            final int envelopeType = this.globalParametersVriTags.get (Integer.valueOf (10)).integerValue;

            final float modEnvAttack = MaschinePresetAccessor.mapToAttackMillis (this.globalParametersVriTags.get (Integer.valueOf (26)).floatValue);
            final float modEnvHold = MaschinePresetAccessor.mapToAttackMillis (this.globalParametersVriTags.get (Integer.valueOf (27)).floatValue);
            final float modEnvDecay = MaschinePresetAccessor.mapToDecayAndRelease (this.globalParametersVriTags.get (Integer.valueOf (28)).floatValue);
            final float modEnvSustain = this.globalParametersVriTags.get (Integer.valueOf (29)).floatValue;
            final float modEnvRelease = MaschinePresetAccessor.mapToDecayAndRelease (this.globalParametersVriTags.get (Integer.valueOf (30)).floatValue);

            final FilterType filterType = FILTER_TYPES.get (Integer.valueOf (this.globalParametersVriTags.get (Integer.valueOf (20)).integerValue));
            if (filterType != null)
            {
                final float cutoffValue = this.globalParametersVriTags.get (Integer.valueOf (21)).floatValue;
                final double cutoff = MathUtils.denormalize (cutoffValue, 43.6, 19600.0);
                final float resonance = this.globalParametersVriTags.get (Integer.valueOf (22)).floatValue;
                final IFilter filter = new DefaultFilter (filterType, 2, cutoff, resonance);
                multisampleSource.setGlobalFilter (filter);

                filter.getCutoffVelocityModulator ().setDepth (this.globalParametersVriTags.get (Integer.valueOf (5)).floatValue);

                final float envelopeModulation = this.globalParametersVriTags.get (Integer.valueOf (32)).floatValue;
                if (envelopeModulation > 0)
                {
                    final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
                    cutoffEnvelopeModulator.setDepth (envelopeModulation);
                    final IEnvelope envelope = cutoffEnvelopeModulator.getSource ();
                    // AHD
                    if (envelopeType == 0)
                    {
                        envelope.setAttackTime (modEnvAttack);
                        envelope.setHoldTime (modEnvHold);
                        envelope.setDecayTime (modEnvDecay);
                    }
                    // ADSR
                    else if (envelopeType == 1)
                    {
                        envelope.setAttackTime (modEnvAttack);
                        envelope.setDecayTime (modEnvDecay);
                        envelope.setSustainLevel (modEnvSustain);
                        envelope.setReleaseTime (modEnvRelease);
                    }
                }
            }

            // Apply zone parameters
            final float ampEnvAttack = MaschinePresetAccessor.mapToAttackMillis (this.globalParametersVriTags.get (Integer.valueOf (11)).floatValue);
            final float ampEnvHold = MaschinePresetAccessor.mapToAttackMillis (this.globalParametersVriTags.get (Integer.valueOf (12)).floatValue);
            final float ampEnvDecay = MaschinePresetAccessor.mapToDecayAndRelease (this.globalParametersVriTags.get (Integer.valueOf (13)).floatValue);
            final float ampEnvSustain = this.globalParametersVriTags.get (Integer.valueOf (14)).floatValue;
            final float ampEnvRelease = MaschinePresetAccessor.mapToDecayAndRelease (this.globalParametersVriTags.get (Integer.valueOf (15)).floatValue);

            final float velocityAmpModulation = this.globalParametersVriTags.get (Integer.valueOf (6)).floatValue;
            final double tuning = this.globalParametersVriTags.get (Integer.valueOf (7)).floatValue * 72.0 - 36.0;
            final boolean isReverse = this.globalParametersVriTags.get (Integer.valueOf (7)).integerValue > 0;
            final float pitchEnvelopeModulation = this.globalParametersVriTags.get (Integer.valueOf (31)).floatValue;

            for (final ISampleZone zone: multisampleSource.getGroups ().get (0).getSampleZones ())
            {
                // Tuning
                zone.setTune (tuning + zone.getTune ());

                // Reverse play-back
                zone.setReversed (isReverse);

                // Pitch Envelope Modulation
                if (pitchEnvelopeModulation > 0)
                {
                    final IEnvelopeModulator pitchEnvelopeModulator = zone.getPitchModulator ();
                    pitchEnvelopeModulator.setDepth (pitchEnvelopeModulation);
                    final IEnvelope envelope = pitchEnvelopeModulator.getSource ();
                    // AHD
                    if (envelopeType == 0)
                    {
                        envelope.setAttackTime (modEnvAttack);
                        envelope.setHoldTime (modEnvHold);
                        envelope.setDecayTime (modEnvDecay);
                    }
                    // ADSR
                    else if (envelopeType == 1)
                    {
                        envelope.setAttackTime (modEnvAttack);
                        envelope.setDecayTime (modEnvDecay);
                        envelope.setSustainLevel (modEnvSustain);
                        envelope.setReleaseTime (modEnvRelease);
                    }
                }

                // Amplitude
                zone.getAmplitudeVelocityModulator ().setDepth (velocityAmpModulation);
                final IEnvelopeModulator amplitudeEnvelopeModulator = zone.getAmplitudeEnvelopeModulator ();
                amplitudeEnvelopeModulator.setDepth (1.0);
                final IEnvelope ampEnvelope = amplitudeEnvelopeModulator.getSource ();
                // AHD
                if (envelopeType == 0)
                {
                    ampEnvelope.setAttackTime (ampEnvAttack);
                    ampEnvelope.setHoldTime (ampEnvHold);
                    ampEnvelope.setDecayTime (ampEnvDecay);
                }
                // ADSR
                else if (envelopeType == 1)
                {
                    ampEnvelope.setAttackTime (ampEnvAttack);
                    ampEnvelope.setDecayTime (ampEnvDecay);
                    ampEnvelope.setSustainLevel (ampEnvSustain);
                    ampEnvelope.setReleaseTime (ampEnvRelease);
                }
            }
        }
        else
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNKNOWN_NUMBER_OF_GLOBAL_PARAMS", Integer.toString (size)));
    }


    /** {@inheritDoc} */
    @Override
    public void writeSound (final OutputStream out, final String safeSampleFolderName, final IMultisampleSource multisampleSource, final int sizeOfSamples, final int version) throws IOException
    {
        this.updateGlobalParameterSection ();

        // Write the header
        StreamUtils.writeASCII (out, START_TAG_LITTLE_ENDIAN, START_TAG_LITTLE_ENDIAN.length ());
        StreamUtils.writeUnsigned32 (out, 2, false);
        StreamUtils.writeASCII (out, V1_SOUND_TAG, V1_SOUND_TAG.length ());
        StreamUtils.padBytes (out, 11, 0);

        // So far we can only reflect it
        StreamUtils.writeUnsigned32 (out, this.unknownSize, false);

        this.writeDataSections (out);
    }


    private boolean readHeader (final InputStream inputStream) throws IOException
    {
        final String startTag = StreamUtils.readASCII (inputStream, 4);
        this.isBigEndian = !Maschine1Format.START_TAG_LITTLE_ENDIAN.equals (startTag);
        if (this.isBigEndian && !Maschine1Format.START_TAG_BIG_ENDIAN.equals (startTag))
        {
            this.notifier.logError ("IDS_NI_MASCHINE_NOT_A_V1_FILE");
            return false;
        }

        final long version = StreamUtils.readUnsigned32 (inputStream, this.isBigEndian);
        if (version != 2)
        {
            this.notifier.logError ("IDS_NI_MASCHINE_V1_UNSUPPORTED_VERSION", Long.toString (version));
            return false;
        }

        if (!V1_SOUND_TAG.equals (StreamUtils.readASCII (inputStream, 48).trim ()))
        {
            this.notifier.logError ("IDS_NI_MASCHINE_NOT_A_V1_FILE");
            return false;
        }

        // Some kind of size
        this.unknownSize = StreamUtils.readUnsigned32 (inputStream, this.isBigEndian);

        this.topSection = readDataSections (inputStream, this.isBigEndian);
        return true;
    }


    private void updateGlobalParameterSection () throws IOException
    {
        writeDataSectionParameters (this.presetData, this.presetParametersDataTag);
    }


    /**
     * Create a sample zone from the given parameters.
     * 
     * @param params The parameters of the zone
     * @param filePaths The file path of the sample is added here
     * @return The created sample zone
     */
    private static ISampleZone createSampleZone (Map<String, DataParameter> params, final List<String> filePaths)
    {
        final ISampleZone zone = new DefaultSampleZone ();

        final String sampleName = URLDecoder.decode (params.get ("rlur").textValue, StandardCharsets.UTF_8).replace ("\\", File.separator);
        filePaths.add (sampleName);

        // TODO Where is this info?
        // zone.setStart ();
        // zone.setStop ();

        if (params.get ("zslm").integerValue > 0)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setStart (params.get ("zsls").integerValue);
            // This negative number will be fixed by adding the sample length later on!
            loop.setEnd (params.get ("zsle").integerValue);
            loop.setCrossfadeInSamples (params.get ("zslx").integerValue);
            zone.addLoop (loop);
        }

        zone.setKeyRoot (params.get ("zrky").integerValue);
        zone.setKeyLow (params.get ("zlky").integerValue);
        zone.setKeyHigh (params.get ("zhky").integerValue);
        zone.setVelocityLow (params.get ("zlvl").integerValue);
        zone.setVelocityHigh (params.get ("zhvl").integerValue);

        zone.setGain (MaschinePresetAccessor.inputToDb (params.get ("zvol").floatValue));
        zone.setPanning (params.get ("zpan").floatValue);
        zone.setTune (params.get ("ztun").floatValue);
        return zone;
    }


    /**
     * Update the metadata of the multi-sample from the sound info XML document.
     * 
     * @param multisampleSource The multi-sample
     * @param metaData The sound info XML document
     */
    private static void updateMetadata (final DefaultMultisampleSource multisampleSource, final byte [] metaData)
    {
        try
        {
            final String xml = StreamUtils.readUTF8 (new ByteArrayInputStream (metaData)).trim ();
            final SoundinfoDocument soundinfoDocument = new SoundinfoDocument (xml);
            final IMetadata metadata = multisampleSource.getMetadata ();
            final String author = soundinfoDocument.getAuthor ();
            if (author != null)
                metadata.setCreator (author);
            final Set<String> categories = soundinfoDocument.getCategories ();
            if (!categories.isEmpty ())
                metadata.setCategory (categories.iterator ().next ());
        }
        catch (final SAXException | IOException ex)
        {
            // Ignore
        }
    }


    /**
     * Searches the children for the next occurrence of the given tag.
     * 
     * @param tagName The name of the tag to look for
     * @param currentZoneIndex The index to start searching
     * @param children The children
     * @return The index of the next occurrence or -1 if none is found
     */
    private static int findNextTag (final String tagName, final int currentZoneIndex, final List<DataTag> children)
    {
        if (currentZoneIndex >= children.size ())
            return -1;

        int zoneIndex = currentZoneIndex;
        do
        {
            final DataTag lsgTag = children.get (zoneIndex);
            if (tagName.equals (lsgTag.name))
                return zoneIndex;
            zoneIndex++;
        } while (zoneIndex < children.size ());
        return -1;
    }


    /**
     * Collect the parameters of a zone.
     * 
     * @param zoneParameters The data tag which contains the parameters
     * @param zoneIndex The index at which the zone starts in the children of the data tag
     * @return The parameters of the zone mapped by their names
     * @throws IOException Could not collect the parameters
     */
    private static Map<String, DataParameter> collectZoneParameters (final DataTag zoneParameters, int zoneIndex) throws IOException
    {
        // gzn has gsl/pfd pairs as children which represent 1 zone! But there can also be more like
        // "osid"!
        final DataTag gslTag = zoneParameters.children.get (zoneIndex);
        final DataTag dfpTag = zoneParameters.children.get (zoneIndex + 1);
        if (!"gsl ".equals (gslTag.name) || !"dfp ".equals (dfpTag.name))
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_FILE"));

        final Map<String, DataParameter> params = new HashMap<> ();
        final DataTag crpTag = getDataTag (gslTag, "gsl ", "dfp ", "prc ");
        for (final DataTag childTag: crpTag.children)
        {
            final DataTag vriTag = getDataTag (childTag, "prp ", "vr  ", "vri ");
            params.put (vriTag.parameter.name, vriTag.parameter);
        }

        final DataTag prcTag2 = getDataTag (dfpTag, "dfp ", "prc ");
        for (final DataTag childTag: prcTag2.children)
        {
            final DataTag vriTag = getDataTag (childTag, "prp ", "vr  ", "vri ");
            params.put (vriTag.parameter.name, vriTag.parameter);
        }
        return params;
    }


    /**
     * Reads all data sections from the given input stream.
     * 
     * @param in The input stream
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian
     * @return The top section
     * @throws IOException Could not read the data sections
     */
    private DataSection readDataSections (final InputStream in, boolean isBigEndian) throws IOException
    {
        DataSection currentSection = this.topSection;
        while (in.available () >= 16)
        {
            final String id = StreamUtils.readASCII (in, 4, !isBigEndian);
            if (!V1_DATA_TAG.equals (id))
                throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_FILE"));

            final long dataVersion = StreamUtils.readUnsigned32 (in, isBigEndian);
            if (dataVersion != 2)
                throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNKNOWN_DATA_VERSION", Long.toString (dataVersion)));

            currentSection = readDataSection (in, currentSection, isBigEndian);
        }

        if (currentSection == null)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_FILE"));
        return currentSection;
    }


    /**
     * Reads one data section from the given input stream.
     * 
     * @param in The input stream
     * @param currentDataSection The currently read data section
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian
     * @return The new current section
     * @throws IOException Could not read the data section
     */
    private static DataSection readDataSection (final InputStream in, final DataSection currentDataSection, final boolean isBigEndian) throws IOException
    {
        if (in.available () < 16)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_FILE"));

        final DataSection section = new DataSection ();
        section.name = StreamUtils.readASCII (in, 4, !isBigEndian);
        // Always 'none'
        StreamUtils.readASCII (in, 4, !isBigEndian);
        int size1 = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        int size2 = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        if (size1 != size2 || size1 < 0 || size1 > in.available ())
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_FILE"));

        section.data = in.readNBytes (size1);
        // Remove the closing tag if it is not an XML tag
        if (!section.name.equals ("info") && !section.name.equals ("brqy"))
            section.data = Arrays.copyOf (section.data, section.data.length - 4);

        // Is it a start (all lower case) or end tag (all upper case)?

        // Start tag -> add as a child
        if (isLowerCase (section.name))
        {
            section.parent = currentDataSection;
            currentDataSection.children.add (section);
            return section;
        }

        // End tag -> move upwards
        return currentDataSection.parent == null ? currentDataSection : currentDataSection.parent;
    }


    /**
     * Writes all data sections to the given output stream.
     * 
     * @param out The output stream
     * @throws IOException Could not write the data sections
     */
    private void writeDataSections (final OutputStream out) throws IOException
    {
        writeDataSections (out, this.topSection.children);
    }


    /**
     * Writes all given child data sections to the given output stream.
     * 
     * @param out The output stream
     * @param childSections The sections to write
     * @throws IOException Could not write the data section
     */
    private void writeDataSections (final OutputStream out, final List<DataSection> childSections) throws IOException
    {
        final int numChildren = childSections.size ();
        for (int i = 0; i < numChildren; i++)
        {
            final DataSection child = childSections.get (i);
            int size = child.data.length;
            final boolean isXML = child.name.equals ("info") || child.name.equals ("brqy");
            if (!isXML)
                size += 4;
            writeDataHeader (out, child.name, size);
            out.write (child.data);
            if (!isXML)
                StreamUtils.writeASCII (out, child.name.toUpperCase (), 4, true);
            writeDataSections (out, child.children);
            writeClosingDataSection (out, child);
        }
    }


    private static void writeDataHeader (final OutputStream out, final String sectionName, final int size) throws IOException
    {
        StreamUtils.writeASCII (out, V1_DATA_TAG, 4, true);
        StreamUtils.writeUnsigned32 (out, 2, false);
        StreamUtils.writeASCII (out, sectionName, 4, true);
        StreamUtils.writeASCII (out, "none", 4, true);
        StreamUtils.writeUnsigned32 (out, size, false);
        StreamUtils.writeUnsigned32 (out, size, false);
    }


    private static void writeClosingDataSection (final OutputStream out, final DataSection section) throws IOException
    {
        writeDataHeader (out, section.name.toUpperCase (), 4);
        StreamUtils.writeUnsigned32 (out, 0, false);
    }


    /**
     * Get the data section which is at the end of the given path.
     * 
     * @param startChildren The children to start the search
     * @param path The path indicating the names of the children to walk 'down'
     * @return The data section or null if it does not exist
     */
    private static DataSection getData (final List<DataSection> startChildren, final String... path)
    {
        List<DataSection> children = startChildren;
        DataSection found = null;
        for (final String pathValue: path)
        {
            found = null;
            for (final DataSection ds: children)
                if (ds.name.equals (pathValue))
                {
                    found = ds;
                    break;
                }
            if (found == null)
                return null;
            children = found.children;
        }
        return found == null ? null : found;
    }


    /**
     * Find a data section with the given tag name and returns its data.
     * 
     * @param section The section to start searching
     * @param tagName The name of the section to look for
     * @return The data of the found section or null if it was not found
     */
    private static DataSection findData (final DataSection section, final String tagName)
    {
        if (tagName.equals (section.name))
            return section;

        for (final DataSection childSection: section.children)
        {
            final DataSection subSection = findData (childSection, tagName);
            if (subSection != null)
                return subSection;
        }

        return null;
    }


    /**
     * Get a data tag.
     * 
     * @param tag The tag to start searching for the tag
     * @param path The path to follow to find the tag
     * @return The data tag or null if not found
     */
    private static DataTag getDataTag (final DataTag tag, final String... path)
    {
        List<DataTag> children = Collections.singletonList (tag);
        DataTag found = null;
        for (final String pathValue: path)
        {
            found = null;
            for (final DataTag dt: children)
                if (dt.name.equals (pathValue))
                {
                    found = dt;
                    break;
                }
            if (found == null)
                return null;
            children = found.children;
        }
        return found;
    }


    /**
     * Reads the tree which contains all parameter/value pairs which are contained in a data
     * section.
     * 
     * @param in The input stream to read the data from
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian
     * @return The top node of the tree
     * @throws IOException Could not read the tree
     */
    private static DataTag readDataSectionParameters (final InputStream in, final boolean isBigEndian) throws IOException
    {
        // 0
        StreamUtils.readUnsigned32 (in, isBigEndian);

        final DataTag topTag = new DataTag ();
        DataTag dataTag = null;
        int lastOsID = -1;

        while (in.available () > 4)
        {
            final String tag1 = StreamUtils.readASCII (in, 4, !isBigEndian);
            if (isLowerCase (tag1))
            {
                final String tag2 = StreamUtils.readASCII (in, 4, !isBigEndian);
                if (tag1.equals (tag2))
                {
                    // Found 0 and 3...
                    StreamUtils.readUnsigned32 (in, isBigEndian);

                    if (dataTag == null)
                        dataTag = topTag;
                    else
                    {
                        final DataTag childTag = new DataTag ();
                        dataTag.children.add (childTag);
                        childTag.parent = dataTag;
                        dataTag = childTag;
                    }
                    dataTag.name = tag1;

                    // Overall numbering of parameters, starts with 1
                    if ("osid".equals (tag1))
                        lastOsID = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
                }
                else if ("vt  ".equals (tag2))
                {
                    if (dataTag == null)
                        throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_PARAMETER_SECTION"));

                    // This is a parameter
                    final String tag3 = StreamUtils.readASCII (in, 4, !isBigEndian);
                    if (!"vt  ".equals (tag3))
                        throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_PARAMETER_SECTION"));

                    dataTag.parameter = readParameter (in, isBigEndian);
                    dataTag.parameter.name = tag1;
                    dataTag.parameter.indexID = lastOsID;
                }
                else if (!isLowerCase (tag2))
                    dataTag = findMatchingStartTag (dataTag, tag2);
                else
                    throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_PARAMETER_SECTION"));
            }
            else
            {
                if (dataTag == null)
                    throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_PARAMETER_SECTION"));
                dataTag = findMatchingStartTag (dataTag, tag1);
            }
        }

        return topTag;
    }


    private static void writeDataSectionParameters (final DataSection dataSection, final DataTag dataTag) throws IOException
    {
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream ())
        {
            StreamUtils.writeUnsigned32 (out, 0, false);
            writeChildTags (out, Collections.singletonList (dataTag));
            dataSection.data = out.toByteArray ();
        }
    }


    private static void writeChildTags (final ByteArrayOutputStream out, final List<DataTag> childTags) throws IOException
    {
        final int size = childTags.size ();
        for (int i = 0; i < size; i++)
        {
            final DataTag child = childTags.get (i);

            StreamUtils.writeASCII (out, child.name, 4, true);

            StreamUtils.writeASCII (out, child.name, 4, true);
            // Also found 3...
            StreamUtils.writeUnsigned32 (out, 0, false);
            if ("osid".equals (child.name))
            {
                // vr -> vri -> tv
                final int indexID = child.children.get (0).children.get (0).parameter.indexID;
                StreamUtils.writeUnsigned32 (out, indexID, false);
            }

            if (child.parameter != null)
            {
                StreamUtils.writeASCII (out, child.parameter.name, 4, true);
                StreamUtils.writeASCII (out, "vt  ", 4, true);
                StreamUtils.writeASCII (out, "vt  ", 4, true);
                writeParameter (out, child.parameter);
            }
            else
                writeChildTags (out, child.children);

            if (!NO_CLOSING_TAG.contains (child.name))
                StreamUtils.writeASCII (out, child.name.toUpperCase (), 4, true);
        }
    }


    private static DataTag findMatchingStartTag (final DataTag dataTag, final String endTag) throws IOException
    {
        DataTag currentDataTag = dataTag;

        // This is an end-tag -> go upwards till we find it
        while (!currentDataTag.name.equalsIgnoreCase (endTag))
        {
            if (currentDataTag.parent == null)
                throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_PARAMETER_SECTION"));
            currentDataTag = currentDataTag.parent;
        }
        return currentDataTag.parent;
    }


    private static DataParameter readParameter (final InputStream in, final boolean isBigEndian) throws IOException
    {
        final int parameterVersion = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        if (parameterVersion != 0)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_PARAMETER_SECTION"));

        // No idea about this but it is always 1
        if ((in.read () & 0xFF) != 1)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_PARAMETER_SECTION"));

        final DataParameter parameter = new DataParameter ();
        parameter.type = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        switch (parameter.type)
        {
            // UTF-16LE string
            case 0:
                parameter.textValue = StreamUtils.readWithLengthUTF16 (in, isBigEndian);
                break;

            // float32
            case 1:
                parameter.floatValue = StreamUtils.readFloat (in, isBigEndian);
                break;

            // uint32
            case 2:
                // This should read a long but should not matter with the given data
                parameter.integerValue = StreamUtils.readSigned32 (in, isBigEndian);
                break;

            // int32
            case 3:
                parameter.integerValue = StreamUtils.readSigned32 (in, isBigEndian);
                break;

            // boolean/byte
            case 4:
                parameter.integerValue = in.read () & 0xFF;
                break;

            default:
                throw new IOException ("Unknown parameter type: " + parameter.type);
        }

        return parameter;
    }


    private static void writeParameter (final OutputStream out, final DataParameter parameter) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, 0, false);
        out.write (1);

        StreamUtils.writeUnsigned32 (out, parameter.type, false);

        switch (parameter.type)
        {
            // UTF-16LE string
            case 0:
                StreamUtils.writeWithLengthUTF16 (out, parameter.textValue);
                break;

            // float32
            case 1:
                StreamUtils.writeFloatLE (out, parameter.floatValue);
                break;

            // uint32
            case 2:
                // This should read a long but should not matter with the given data
                StreamUtils.writeSigned32 (out, parameter.integerValue, false);
                break;

            // int32
            case 3:
                StreamUtils.writeSigned32 (out, parameter.integerValue, false);
                break;

            // boolean/byte
            case 4:
                out.write (parameter.integerValue > 0 ? 1 : 0);
                break;

            default:
                throw new IOException ("Unknown parameter type: " + parameter.type);
        }
    }


    private static boolean isLowerCase (final String text)
    {
        return text.equals (text.toLowerCase ());
    }


    /** The content of a Maschine 1 file consists of data sections. */
    private static class DataSection
    {
        String                  name     = null;
        byte []                 data     = null;
        DataSection             parent   = null;
        final List<DataSection> children = new ArrayList<> ();
    }


    /** The content of a Maschine 1 data section consists of a tree hierarchy of data tags. */
    private static class DataTag
    {
        String              name;
        DataTag             parent    = null;
        final List<DataTag> children  = new ArrayList<> ();
        DataParameter       parameter = null;
    }


    /** Data parameters are the leaves of the data tag tree. */
    private static class DataParameter
    {
        int    indexID;
        String name;

        // 0 = String
        // 1 = Float
        // 2 = Uint32
        // 3 = Int32
        // 4 = Boolean/Byte
        int    type;

        int    integerValue;
        float  floatValue;
        String textValue;


        /** {@inheritDoc} */
        @Override
        public String toString ()
        {
            String value;
            switch (this.type)
            {
                case 0 -> value = "String(\"" + this.textValue + "\")";
                case 1 -> value = "Float(" + this.floatValue + ")";
                case 2 -> value = "Int32(" + this.integerValue + ")";
                case 3 -> value = "UInt32(" + this.integerValue + ")";
                case 4 -> value = "Boolean/Byte(" + this.integerValue + ")";
                default -> value = "Unknown";
            }
            return value;
        }
    }


    //////////////////////////////////////////////
    // TODO remove

    public static void main ()
    {
        File file = new File ("L:\\Native Instruments\\Maschine 1 Factory Library\\Sounds\\Bass\\Analog Classic A.msnd");
        try (RandomAccessFile inputStream = new RandomAccessFile (file, "r"); FileOutputStream out = new FileOutputStream ("C:\\Users\\mos\\Desktop\\TEST\\Copy.msnd");)
        {
            final Maschine1Format maschine1Format = new Maschine1Format (null);
            maschine1Format.readSound (file.getParentFile (), file, inputStream, null);
            maschine1Format.writeSound (out, "Samples", null, 0, 2);
        }
        catch (Exception ex)
        {
            ex.printStackTrace ();
        }
    }


    public static void mainOld (String [] args) throws Exception
    {
        // File [] listFiles = new File ("L:\\Native Instruments\\Vintage Heat\\Sounds").listFiles
        // (pathname -> pathname.getName ().endsWith (".msnd"));
        List<File> listFiles = new ArrayList<> ();
        collectFile (new File ("L:\\Native Instruments\\Maschine 1 Factory Library\\Sounds"), listFiles);

        for (int i = 0; i < listFiles.size (); i++)
        {
            File file = listFiles.get (i);

            // File file = new File ("L:\\Native Instruments\\Maschine 1 Factory
            // Library\\Sounds\\Bass\\Analog Classic A.msnd");
            // File file = new File ("L:\\Native Instruments\\Maschine 1 Factory
            // Library\\Sounds\\Guitar\\African Plucks.msnd");

            System.out.println (file);
            try (RandomAccessFile inputStream = new RandomAccessFile (file, "r"))
            {

                new Maschine1Format (null).readSound (file.getParentFile (), file, inputStream, null);

            }
            catch (Exception ex)
            {
                ex.printStackTrace ();
            }
        }
    }


    private static void collectFile (final File path, final List<File> fileList)
    {
        Collections.addAll (fileList, path.listFiles (pathname -> pathname.getName ().endsWith (".msnd")));

        for (File folder: path.listFiles (pathname -> pathname.isDirectory ()))
            collectFile (folder, fileList);
    }
}
