// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.maschine.maschine1;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
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
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Can handle Maschine 1 MSND format files.
 *
 * @author Jürgen Moßgraber
 */
public class Maschine1Format implements IMaschineFormat
{
    private static final byte [] MASCHINE_TEMPLATE_V1;

    static
    {
        try
        {
            MASCHINE_TEMPLATE_V1 = Functions.rawFileFor ("de/mossgrabers/convertwithmoss/templates/maschine/MaschineV1Template.msnd");
        }
        catch (final IOException ex)
        {
            throw new RuntimeException (ex);
        }
    }

    /** The 4 start bytes of the format signaling little-endian. */
    public static final String                    START_TAG_LITTLE_ENDIAN = "-in-";
    /** The 4 start bytes of the format signaling big-endian. */
    public static final String                    START_TAG_BIG_ENDIAN    = "-ni-";

    private static final String                   V1_SOUND_TAG            = "#NI#CS#Document##NI#SoundShell#Sound#";
    private static final String                   V1_DATA_TAG             = "data";
    private static final Set<String>              NO_CLOSING_TAG          = new HashSet<> ();
    private static final Map<Integer, FilterType> FILTER_TYPES            = new HashMap<> ();
    private static final Map<FilterType, Integer> INV_FILTER_TYPES        = new HashMap<> ();

    static
    {
        NO_CLOSING_TAG.add ("vt  ");
        NO_CLOSING_TAG.add ("vri ");
        NO_CLOSING_TAG.add ("osid");

        FILTER_TYPES.put (Integer.valueOf (2), FilterType.LOW_PASS);
        FILTER_TYPES.put (Integer.valueOf (3), FilterType.BAND_PASS);
        FILTER_TYPES.put (Integer.valueOf (4), FilterType.HIGH_PASS);

        INV_FILTER_TYPES.put (FilterType.LOW_PASS, Integer.valueOf (2));
        INV_FILTER_TYPES.put (FilterType.BAND_PASS, Integer.valueOf (3));
        INV_FILTER_TYPES.put (FilterType.HIGH_PASS, Integer.valueOf (4));
    }

    private final INotifier                   notifier;
    private long                              version;
    private DataSection                       topSection                  = new DataSection ();

    private final Map<Integer, DataParameter> globalParametersVriTags     = new TreeMap<> ();
    private boolean                           isBigEndian;
    private DataSection                       zoneDataSection             = null;
    private List<DataTag>                     zoneParametersTopDataTags   = null;
    private DataSection                       presetDataSection           = null;
    private List<DataTag>                     presetParametersTopDataTags = null;
    private SoundinfoDocument                 soundinfoDocument           = null;


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
    public String getFileEnding ()
    {
        return "msnd";
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readSound (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess, final IMetadataConfig metadataConfig) throws IOException
    {
        try (final FileChannel channel = fileAccess.getChannel (); final InputStream inputStream = Channels.newInputStream (channel))
        {
            if (!this.readSoundData (inputStream))
                return Collections.emptyList ();
        }

        // Create the multi-sample with 1 group
        final String name = FileUtils.getNameWithoutType (sourceFile);
        final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), sourceFolder, name);
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, name, AudioFileUtils.subtractPaths (sourceFolder, sourceFile));
        final IGroup group = new DefaultGroup ();
        multisampleSource.setGroups (Collections.singletonList (group));

        // Update metadata from SoundInfo XML document if present
        this.applyMetadata (multisampleSource);

        // Read all zones
        final List<String> filePaths = new ArrayList<> ();
        for (final DataTag zoneTag: this.zoneParametersTopDataTags)
        {
            // gsl/pfd pairs represent 1 zone but there can also be more like "osid"
            final DataTag gslParameterTag = getDataTag (zoneTag, "gzn ", "gsl ");
            final DataTag dfpParameterTag = getDataTag (zoneTag, "gzn ", "dfp ");
            final Map<String, DataParameter> params = collectZoneParameters (gslParameterTag, dfpParameterTag);
            group.addSampleZone (createSampleZone (params, filePaths));
        }

        // Find all samples
        MaschinePresetAccessor.assignSampleFile (sourceFile, group, filePaths, this.notifier);

        // Correct play-back end and loop end
        for (final ISampleZone sampleZone: group.getSampleZones ())
        {
            final int length = sampleZone.getSampleData ().getAudioMetadata ().getNumberOfSamples ();
            sampleZone.setStop (sampleZone.getStop () + length);
            final List<ISampleLoop> loops = sampleZone.getLoops ();
            if (!loops.isEmpty ())
            {
                final ISampleLoop sampleLoop = loops.get (0);
                sampleLoop.setEnd (sampleLoop.getEnd () + length);
            }
        }

        this.applyGlobalParameters (multisampleSource);

        return Collections.singletonList (multisampleSource);
    }


    /** {@inheritDoc} */
    @Override
    public void writeSound (final OutputStream out, final String safeSampleFolderName, final IMultisampleSource multisampleSource, final int version) throws IOException
    {
        if (!this.readSoundData (new ByteArrayInputStream (MASCHINE_TEMPLATE_V1)))
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_COULD_NOT_READ_TEMPLATE"));

        this.updateMetadata (multisampleSource);

        // There are no groups, therefore, collect all sample zones
        final List<ISampleZone> sampleZones = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            sampleZones.addAll (group.getSampleZones ());
        this.updateZones (sampleZones, safeSampleFolderName);

        this.updateGlobalParameters (multisampleSource, sampleZones.get (0));

        this.updateParameterSections ();

        // Write the header
        StreamUtils.writeASCII (out, START_TAG_LITTLE_ENDIAN, START_TAG_LITTLE_ENDIAN.length ());
        StreamUtils.writeUnsigned32 (out, 2, false);
        StreamUtils.writeASCII (out, V1_SOUND_TAG, V1_SOUND_TAG.length ());
        StreamUtils.padBytes (out, 11, 0);

        // So far we can only reflect it
        StreamUtils.writeUnsigned32 (out, this.version, false);

        this.writeDataSections (out);
    }


    /**
     * Reads a sound into the class fields.
     *
     * @param inputStream The stream to read from
     * @return True if success
     * @throws IOException Could not read the sound
     */
    private boolean readSoundData (final InputStream inputStream) throws IOException
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

        this.version = StreamUtils.readUnsigned32 (inputStream, this.isBigEndian);
        this.topSection = this.readDataSections (inputStream, this.isBigEndian);

        final DataSection metaDataSection = getData (this.topSection.children, "gtr ", "info");
        if (metaDataSection != null && metaDataSection.data != null)
            this.soundinfoDocument = readMetadata (metaDataSection.data);

        // Read all zones - mostly in: "gtr " -> "gtfs" -> "psg " -> "gznc"
        this.zoneDataSection = findDataSection (this.topSection, "gznc");
        if (this.zoneDataSection == null || this.zoneDataSection.data == null)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_FILE"));
        this.zoneParametersTopDataTags = readDataSectionParameters (new ByteArrayInputStream (this.zoneDataSection.data), this.isBigEndian);
        if (this.zoneParametersTopDataTags == null)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_FILE"));

        // Read global sampler parameters
        this.presetDataSection = getData (this.zoneDataSection.parent.children, "gemo", "prst");
        if (this.presetDataSection == null || this.presetDataSection.data == null)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_FILE"));
        this.presetParametersTopDataTags = readDataSectionParameters (new ByteArrayInputStream (this.presetDataSection.data), this.isBigEndian);
        final DataTag paramsTag = getDataTag (this.presetParametersTopDataTags, "dfp ", "pac ");
        this.globalParametersVriTags.clear ();
        for (final DataTag childTag: paramsTag.children)
        {
            final DataTag vriTag = getDataTag (childTag, "par ", "osid", "vr  ", "vri ");
            final int paramIndex = vriTag.parameter.name.getBytes ()[3];
            this.globalParametersVriTags.put (Integer.valueOf (paramIndex), vriTag.parameter);
        }

        return true;
    }


    /**
     * Apply all read global parameters to the zones of the sound.
     *
     * @param multisampleSource The source to which to apply to global parameters
     * @throws IOException Could not apply the parameters
     */
    private void applyGlobalParameters (final IMultisampleSource multisampleSource) throws IOException
    {
        int size = this.globalParametersVriTags.size ();
        if (size != 43 && size != 54)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNKNOWN_NUMBER_OF_GLOBAL_PARAMS", Integer.toString (size)));

        int envelopeType = this.globalParametersVriTags.get (Integer.valueOf (10)).integerValue;
        int pitchbend = 2;
        if (size > 43)
        {
            final float normalizedPitchbend = this.globalParametersVriTags.get (Integer.valueOf (48)).floatValue;
            pitchbend = Math.round (1200f * normalizedPitchbend * normalizedPitchbend);
        }
        else
            envelopeType++;

        final float modEnvAttack = MaschinePresetAccessor.mapToAttackMillis (this.globalParametersVriTags.get (Integer.valueOf (26)).floatValue) / 1000f;
        final float modEnvHold = MaschinePresetAccessor.mapToAttackMillis (this.globalParametersVriTags.get (Integer.valueOf (27)).floatValue) / 1000f;
        final float modEnvDecay = MaschinePresetAccessor.mapToDecayAndRelease (this.globalParametersVriTags.get (Integer.valueOf (28)).floatValue) / 1000f;
        final float modEnvSustain = this.globalParametersVriTags.get (Integer.valueOf (29)).floatValue;
        final float modEnvRelease = MaschinePresetAccessor.mapToDecayAndRelease (this.globalParametersVriTags.get (Integer.valueOf (30)).floatValue) / 1000f;

        final FilterType filterType = FILTER_TYPES.get (Integer.valueOf (this.globalParametersVriTags.get (Integer.valueOf (20)).integerValue));
        if (filterType != null)
        {
            final float cutoffValue = this.globalParametersVriTags.get (Integer.valueOf (21)).floatValue;
            final double cutoff = MaschinePresetAccessor.mapToFrequency (cutoffValue);
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
                if (envelopeType == 1)
                {
                    envelope.setAttackTime (modEnvAttack);
                    envelope.setHoldTime (modEnvHold);
                    envelope.setDecayTime (modEnvDecay);
                }
                // ADSR
                else if (envelopeType == 2)
                {
                    envelope.setAttackTime (modEnvAttack);
                    envelope.setDecayTime (modEnvDecay);
                    envelope.setSustainLevel (modEnvSustain);
                    envelope.setReleaseTime (modEnvRelease);
                }
            }
        }

        // Apply zone parameters
        final float ampEnvAttack = MaschinePresetAccessor.mapToAttackMillis (this.globalParametersVriTags.get (Integer.valueOf (11)).floatValue) / 1000f;
        final float ampEnvHold = MaschinePresetAccessor.mapToAttackMillis (this.globalParametersVriTags.get (Integer.valueOf (12)).floatValue) / 1000f;
        final float ampEnvDecay = MaschinePresetAccessor.mapToDecayAndRelease (this.globalParametersVriTags.get (Integer.valueOf (13)).floatValue) / 1000f;
        final float ampEnvSustain = this.globalParametersVriTags.get (Integer.valueOf (14)).floatValue;
        final float ampEnvRelease = MaschinePresetAccessor.mapToDecayAndRelease (this.globalParametersVriTags.get (Integer.valueOf (15)).floatValue) / 1000f;

        final float velocityAmpModulation = this.globalParametersVriTags.get (Integer.valueOf (6)).floatValue;
        final double tuning = this.globalParametersVriTags.get (Integer.valueOf (7)).floatValue * 72.0 - 36.0;
        final boolean isReverse = this.globalParametersVriTags.get (Integer.valueOf (9)).integerValue > 0;
        final float pitchEnvelopeModulation = this.globalParametersVriTags.get (Integer.valueOf (31)).floatValue;

        for (final ISampleZone zone: multisampleSource.getGroups ().get (0).getSampleZones ())
        {
            // Tuning
            zone.setTuning (tuning + zone.getTuning ());
            zone.setBendUp (pitchbend);
            zone.setBendDown (-pitchbend);

            // Reverse play-back
            zone.setReversed (isReverse);

            // Pitch Envelope Modulation
            if (pitchEnvelopeModulation > 0)
            {
                final IEnvelopeModulator pitchEnvelopeModulator = zone.getPitchModulator ();
                pitchEnvelopeModulator.setDepth (pitchEnvelopeModulation);
                final IEnvelope envelope = pitchEnvelopeModulator.getSource ();
                // AHD
                if (envelopeType == 1)
                {
                    envelope.setAttackTime (modEnvAttack);
                    envelope.setHoldTime (modEnvHold);
                    envelope.setDecayTime (modEnvDecay);
                }
                // ADSR
                else if (envelopeType == 2)
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
            if (envelopeType == 1)
            {
                ampEnvelope.setAttackTime (ampEnvAttack);
                ampEnvelope.setHoldTime (ampEnvHold);
                ampEnvelope.setDecayTime (ampEnvDecay);
            }
            // ADSR
            else if (envelopeType == 2)
            {
                ampEnvelope.setAttackTime (ampEnvAttack);
                ampEnvelope.setDecayTime (ampEnvDecay);
                ampEnvelope.setSustainLevel (ampEnvSustain);
                ampEnvelope.setReleaseTime (ampEnvRelease);
            }
        }
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

        zone.setStart (params.get ("zsst").integerValue);
        // This negative number will be fixed by adding the sample length in readSound!
        zone.setStop (params.get ("zsed").integerValue);

        if (params.get ("zslm").integerValue > 0)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setStart (params.get ("zsls").integerValue);
            // This negative number will be fixed by adding the sample length in readSound!
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
        zone.setTuning (tuneToPitch (params.get ("ztun").floatValue));
        return zone;
    }


    /**
     * Parse the sound info XML document.
     *
     * @param metaData The raw bytes of the XML document
     * @return The sound info document
     */
    private static SoundinfoDocument readMetadata (final byte [] metaData)
    {
        try
        {
            // Note: There is a prefix of 03 00 00 00 00 which is removed by the trim!
            final String xml = StreamUtils.readUTF8 (new ByteArrayInputStream (metaData)).trim ();
            return new SoundinfoDocument (xml);
        }
        catch (final SAXException | IOException ex)
        {
            // Ignore
            return null;
        }
    }


    /**
     * Update the metadata of the multi-sample from the sound info XML document.
     *
     * @param multisampleSource The multi-sample
     */
    private void applyMetadata (final DefaultMultisampleSource multisampleSource)
    {
        if (this.soundinfoDocument == null)
            return;

        final IMetadata metadata = multisampleSource.getMetadata ();
        final String author = this.soundinfoDocument.getAuthor ();
        if (author != null)
            metadata.setCreator (author);
        final Set<String> categories = this.soundinfoDocument.getCategories ();
        if (!categories.isEmpty ())
            metadata.setCategory (categories.iterator ().next ());
    }


    /**
     * Collect the parameters of a zone.
     *
     * @param gslParameterTag The GSL data tag which contains zone parameters
     * @param dfpParameterTag The DFP data tag which contains more zone parameters
     * @return The parameters of the zone mapped by their names
     * @throws IOException Could not collect the parameters
     */
    private static Map<String, DataParameter> collectZoneParameters (final DataTag gslParameterTag, DataTag dfpParameterTag) throws IOException
    {
        final Map<String, DataParameter> params = new HashMap<> ();
        if (gslParameterTag == null || dfpParameterTag == null)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_V1_UNSOUND_FILE"));

        final DataTag crpTag = getDataTag (gslParameterTag, "gsl ", "dfp ", "prc ");
        for (final DataTag childTag: crpTag.children)
        {
            final DataTag vriTag = getDataTag (childTag, "prp ", "vr  ", "vri ");
            params.put (vriTag.parameter.name, vriTag.parameter);
        }

        final DataTag prcTag2 = getDataTag (dfpParameterTag, "dfp ", "prc ");
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
        if (StringUtils.isLowerCase (section.name))
        {
            section.parent = currentDataSection;
            currentDataSection.children.add (section);
            return section;
        }

        // End tag -> move upwards
        return currentDataSection.parent == null ? currentDataSection : currentDataSection.parent;
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
    private static DataSection findDataSection (final DataSection section, final String tagName)
    {
        if (tagName.equals (section.name))
            return section;

        for (final DataSection childSection: section.children)
        {
            final DataSection subSection = findDataSection (childSection, tagName);
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
        return getDataTag (Collections.singletonList (tag), path);
    }


    /**
     * Get a data tag.
     *
     * @param tags The tags to start searching for the tag
     * @param path The path to follow to find the tag
     * @return The data tag or null if not found
     */
    private static DataTag getDataTag (final List<DataTag> tags, final String... path)
    {
        List<DataTag> children = tags;
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
     * @return The top nodes of the tree (there is no single one!)
     * @throws IOException Could not read the tree
     */
    private static List<DataTag> readDataSectionParameters (final InputStream in, final boolean isBigEndian) throws IOException
    {
        // 0
        StreamUtils.readUnsigned32 (in, isBigEndian);

        final DataTag topTag = new DataTag ();
        DataTag dataTag = topTag;

        while (in.available () >= 4)
        {
            final String tag1 = StreamUtils.readASCII (in, 4, !isBigEndian);
            if (StringUtils.isLowerCase (tag1))
            {
                final String tag2 = StreamUtils.readASCII (in, 4, !isBigEndian);
                if (tag1.equals (tag2))
                {
                    // Version 0-3 for GZN
                    StreamUtils.readUnsigned32 (in, isBigEndian);

                    final DataTag childTag = new DataTag ();
                    dataTag.children.add (childTag);
                    childTag.parent = dataTag;
                    dataTag = childTag;
                    dataTag.name = tag1;

                    // Overall numbering of parameters, starts with 1
                    if ("osid".equals (tag1))
                        dataTag.osid = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
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
                }
                else if (!StringUtils.isLowerCase (tag2))
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

        return topTag.children;
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
        // No idea about this but it is always 1
        if ((parameterVersion != 0) || ((in.read () & 0xFF) != 1))
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


    /**
     * Updates the XML sound-info document.
     *
     * @param multisampleSource The metadata from which to update the XML document
     * @throws IOException Could not update the XML document
     */
    private void updateMetadata (final IMultisampleSource multisampleSource) throws IOException
    {
        final IMetadata metadata = multisampleSource.getMetadata ();
        this.soundinfoDocument = new SoundinfoDocument (metadata.getCreator (), metadata.getCategory ());
        final String xmlCode = this.soundinfoDocument.createDocument (multisampleSource.getName ());

        final DataSection metaDataSection = getData (this.topSection.children, "gtr ", "info");
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        out.write (new byte []
        {
            0x03,
            0x00,
            0x00,
            0x00,
            0x00
        });
        out.write (xmlCode.getBytes (StandardCharsets.UTF_8));
        metaDataSection.data = out.toByteArray ();
    }


    /**
     * Create GZN tag trees for all zones.
     *
     * @param sampleZones The sample zones from which to create GZN tags
     * @param safeSampleFolderName The folder which contains the samples
     * @throws IOException Could not update the zones
     */
    private void updateZones (final List<ISampleZone> sampleZones, final String safeSampleFolderName) throws IOException
    {
        final DataTag zoneTemplate = this.zoneParametersTopDataTags.get (0);
        this.zoneParametersTopDataTags.clear ();

        for (final ISampleZone zone: sampleZones)
        {
            final DataTag newZoneTag = deepCloneDataTag (zoneTemplate);
            this.zoneParametersTopDataTags.add (newZoneTag);

            // User Library
            findParameterByName (newZoneTag, "bsca").integerValue = 3;
            findParameterByName (newZoneTag, "bsid").textValue = safeSampleFolderName;
            findParameterByName (newZoneTag, "bsur").textValue = "";

            final String samplePath = zone.getName () + ".wav";
            findParameterByName (newZoneTag, "rlur").textValue = samplePath;

            final int sampleLength = zone.getSampleData ().getAudioMetadata ().getNumberOfSamples ();
            findParameterByName (newZoneTag, "zsst").integerValue = zone.getStart ();
            findParameterByName (newZoneTag, "zsed").integerValue = zone.getStop () - sampleLength;

            final List<ISampleLoop> loops = zone.getLoops ();
            final boolean hasLoop = !loops.isEmpty ();
            findParameterByName (newZoneTag, "zslm").integerValue = hasLoop ? 2 : 0;
            int loopStart = zone.getStart ();
            int loopEnd = 0;
            int crossfadeInSamples = 0;
            if (hasLoop)
            {
                final ISampleLoop loop = loops.get (0);
                loopStart = loop.getStart ();
                loopEnd = loop.getEnd () - sampleLength;
                crossfadeInSamples = loop.getCrossfadeInSamples ();
            }

            findParameterByName (newZoneTag, "zsls").integerValue = loopStart;
            findParameterByName (newZoneTag, "zsle").integerValue = loopEnd;
            findParameterByName (newZoneTag, "zslx").integerValue = crossfadeInSamples;

            findParameterByName (newZoneTag, "zrky").integerValue = zone.getKeyRoot ();
            findParameterByName (newZoneTag, "zlky").integerValue = zone.getKeyLow ();
            findParameterByName (newZoneTag, "zhky").integerValue = zone.getKeyHigh ();
            findParameterByName (newZoneTag, "zlvl").integerValue = zone.getVelocityLow ();
            findParameterByName (newZoneTag, "zhvl").integerValue = zone.getVelocityHigh ();

            findParameterByName (newZoneTag, "zvol").floatValue = MaschinePresetAccessor.dbToInput (zone.getGain ());
            findParameterByName (newZoneTag, "zpan").floatValue = (float) zone.getPanning ();
            findParameterByName (newZoneTag, "ztun").floatValue = (float) pitchToTune (zone.getTuning ());
        }
    }


    /**
     * Fill in the global parameters.
     * 
     * @param multisampleSource The multi-sample source
     * @param firstSampleZone The first sample zone
     */
    private void updateGlobalParameters (final IMultisampleSource multisampleSource, final ISampleZone firstSampleZone)
    {
        // The template is from 1.0 and does not contain pitch-bend

        // Envelope type is already set to ADSR in template
        final Optional<IEnvelopeModulator> globalAmplitudeModulator = multisampleSource.getGlobalAmplitudeModulator ();
        if (globalAmplitudeModulator.isPresent ())
        {
            final IEnvelopeModulator envelopeModulator = globalAmplitudeModulator.get ();
            this.globalParametersVriTags.get (Integer.valueOf (6)).floatValue = (float) envelopeModulator.getDepth ();
            final IEnvelope ampEnvelope = envelopeModulator.getSource ();
            this.globalParametersVriTags.get (Integer.valueOf (11)).floatValue = MaschinePresetAccessor.attackMillisToInput ((float) Math.max (0, ampEnvelope.getAttackTime ()) * 1000f);
            this.globalParametersVriTags.get (Integer.valueOf (12)).floatValue = 0f;
            final double decayTime = (Math.max (0, ampEnvelope.getHoldTime ()) + Math.max (0, ampEnvelope.getDecayTime ())) * 1000.0;
            this.globalParametersVriTags.get (Integer.valueOf (13)).floatValue = MaschinePresetAccessor.decayAndReleaseMillisToInput ((float) decayTime);
            this.globalParametersVriTags.get (Integer.valueOf (14)).floatValue = (float) ampEnvelope.getSustainLevel ();
            this.globalParametersVriTags.get (Integer.valueOf (15)).floatValue = MaschinePresetAccessor.decayAndReleaseMillisToInput ((float) Math.max (0, ampEnvelope.getReleaseTime ()) * 1000f);
        }

        this.globalParametersVriTags.get (Integer.valueOf (9)).integerValue = firstSampleZone.isReversed () ? 1 : 0;

        // There is only 1 modulation envelope, prefer the filter envelope if present
        IEnvelope modEnvelope = null;
        final Optional<IFilter> globalFilter = multisampleSource.getGlobalFilter ();
        if (globalFilter.isPresent ())
        {
            final IFilter filter = globalFilter.get ();
            final Integer filterType = INV_FILTER_TYPES.get (filter.getType ());
            if (filterType != null)
            {
                this.globalParametersVriTags.get (Integer.valueOf (20)).integerValue = filterType.intValue ();
                this.globalParametersVriTags.get (Integer.valueOf (21)).floatValue = MaschinePresetAccessor.frequencyToNorm (filter.getCutoff ());
                this.globalParametersVriTags.get (Integer.valueOf (22)).floatValue = (float) filter.getResonance ();

                final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
                final double cutoffModulationIntensity = cutoffEnvelopeModulator.getDepth ();
                if (cutoffModulationIntensity > 0)
                {
                    this.globalParametersVriTags.get (Integer.valueOf (32)).floatValue = (float) cutoffModulationIntensity;
                    modEnvelope = cutoffEnvelopeModulator.getSource ();
                }
                this.globalParametersVriTags.get (Integer.valueOf (5)).floatValue = (float) filter.getCutoffVelocityModulator ().getDepth ();
            }
        }

        // If the modulation envelope was not used for the filter use it for pitch
        if (modEnvelope == null)
        {
            final IEnvelopeModulator pitchModulator = firstSampleZone.getPitchModulator ();
            final double pitchModulationIntensity = pitchModulator.getDepth ();
            if (pitchModulationIntensity > 0)
            {
                modEnvelope = pitchModulator.getSource ();
                this.globalParametersVriTags.get (Integer.valueOf (31)).floatValue = (float) pitchModulationIntensity;
            }
        }

        if (modEnvelope != null)
        {
            this.globalParametersVriTags.get (Integer.valueOf (26)).floatValue = MaschinePresetAccessor.attackMillisToInput ((float) Math.max (0, modEnvelope.getAttackTime ()) * 1000f);
            this.globalParametersVriTags.get (Integer.valueOf (27)).floatValue = 0f;
            final double decayTime = (Math.max (0, modEnvelope.getHoldTime ()) + Math.max (0, modEnvelope.getDecayTime ())) * 1000.0;
            this.globalParametersVriTags.get (Integer.valueOf (28)).floatValue = MaschinePresetAccessor.decayAndReleaseMillisToInput ((float) decayTime);
            this.globalParametersVriTags.get (Integer.valueOf (29)).floatValue = (float) modEnvelope.getSustainLevel ();
            this.globalParametersVriTags.get (Integer.valueOf (30)).floatValue = MaschinePresetAccessor.decayAndReleaseMillisToInput ((float) Math.max (0, modEnvelope.getReleaseTime ()) * 1000f);
        }
    }


    /**
     * Update the data byte arrays of the parameter sections from the field variables.
     *
     * @throws IOException Could not create the binary data
     */
    private void updateParameterSections () throws IOException
    {
        this.updateDataSectionParameters (this.presetDataSection, this.presetParametersTopDataTags);
        this.updateDataSectionParameters (this.zoneDataSection, this.zoneParametersTopDataTags);
    }


    /**
     * Writes all data sections to the given output stream.
     *
     * @param out The output stream
     * @throws IOException Could not write the data sections
     */
    private void writeDataSections (final OutputStream out) throws IOException
    {
        this.writeDataSections (out, this.topSection.children);
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
            this.writeDataSections (out, child.children);
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


    private void updateDataSectionParameters (final DataSection dataSection, final List<DataTag> dataTags) throws IOException
    {
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream ())
        {
            StreamUtils.writeUnsigned32 (out, 0, false);
            this.writeChildTags (out, dataTags);
            dataSection.data = out.toByteArray ();
        }
    }


    private void writeChildTags (final ByteArrayOutputStream out, final List<DataTag> childTags) throws IOException
    {
        final int size = childTags.size ();
        for (int i = 0; i < size; i++)
        {
            final DataTag child = childTags.get (i);

            StreamUtils.writeASCII (out, child.name, 4, true);
            StreamUtils.writeASCII (out, child.name, 4, true);

            // Version depends on the main version
            int subVersion = 0;
            if ("gzn ".equals (child.name))
                if (this.version == 411)
                    subVersion = 2;
                else if (this.version == 470)
                    subVersion = 3;
            StreamUtils.writeUnsigned32 (out, subVersion, false);

            if ("osid".equals (child.name))
                StreamUtils.writeUnsigned32 (out, child.osid, false);

            if (child.parameter != null)
                writeParameter (out, child.parameter);
            else
                this.writeChildTags (out, child.children);

            if (!NO_CLOSING_TAG.contains (child.name))
                StreamUtils.writeASCII (out, child.name.toUpperCase (), 4, true);
        }
    }


    /**
     * Write one parameter.
     *
     * @param out The output stream to write to
     * @param parameter The parameter to write
     * @throws IOException Could not write the parameter
     */
    private static void writeParameter (final OutputStream out, final DataParameter parameter) throws IOException
    {
        StreamUtils.writeASCII (out, parameter.name, 4, true);
        StreamUtils.writeASCII (out, "vt  ", 4, true);
        StreamUtils.writeASCII (out, "vt  ", 4, true);
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
        int                 osid      = 0;
    }


    /** Data parameters are the leaves of the data tag tree. */
    private static class DataParameter
    {
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


    private static DataTag deepCloneDataTag (final DataTag src)
    {
        final DataTag copy = new DataTag ();
        copy.name = src.name;
        copy.osid = src.osid;
        copy.parameter = cloneParameter (src.parameter);

        // parent is null and needs to be fixed outside if it is not a top tag
        copy.parent = null;

        for (final DataTag child: src.children)
        {
            DataTag childCopy = deepCloneDataTag (child);
            childCopy.parent = copy;
            copy.children.add (childCopy);
        }
        return copy;
    }


    private static DataParameter cloneParameter (final DataParameter p)
    {
        if (p == null)
            return null;
        final DataParameter q = new DataParameter ();
        q.name = p.name;
        q.type = p.type;
        q.integerValue = p.integerValue;
        q.floatValue = p.floatValue;
        q.textValue = p.textValue;
        return q;
    }


    private static DataParameter findParameterByName (final DataTag dataTag, final String name)
    {
        if (dataTag.parameter != null && Objects.equals (dataTag.parameter.name, name))
            return dataTag.parameter;
        for (final DataTag child: dataTag.children)
        {
            final DataParameter result = findParameterByName (child, name);
            if (result != null)
                return result;
        }
        return null;
    }


    // From Maschine
    private static double tuneToPitch (final double pitch)
    {
        final double v = Math.clamp (pitch, 0.125, 8);
        // y = 12 * log2(x)
        return 12.0 * (Math.log (v) / Math.log (2.0));
    }


    // To Maschine
    private static double pitchToTune (final double tune)
    {
        final double v = Math.clamp (tune, -36.0, 36.0);
        // x = 2^(y/12)
        return Math.pow (2.0, v / 12.0);
    }
}
