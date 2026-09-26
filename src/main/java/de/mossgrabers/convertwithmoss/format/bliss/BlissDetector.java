// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.bliss;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFileBasedSampleData;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.FlacFileSampleData;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.XMLUtils;


/**
 * Detects recursively discoDSP Bliss multi-sample files in folders. Files must end with <i>.zbp</i>
 * or <i>.zbb</i>.
 *
 * @author Jürgen Moßgraber
 */
public class BlissDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String                    MOD_LFO                              = "mod_lfo";

    private static final String                    VALUE                                = "value";

    private static final String                    IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND = "IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND";
    private static final String                    ERR_BAD_METADATA_FILE                = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";

    private static final Map<Integer, LfoWaveform> LFO_WAVE_FORMS                       = new HashMap<> ();
    private static final Map<Integer, FilterType>  FILTER_TYPE_MAP                      = new HashMap<> ();
    static
    {
        LFO_WAVE_FORMS.put (Integer.valueOf (0), LfoWaveform.SINE);
        LFO_WAVE_FORMS.put (Integer.valueOf (1), LfoWaveform.TRIANGLE);
        LFO_WAVE_FORMS.put (Integer.valueOf (2), LfoWaveform.SAWTOOTH_UP);
        LFO_WAVE_FORMS.put (Integer.valueOf (3), LfoWaveform.SQUARE);
        LFO_WAVE_FORMS.put (Integer.valueOf (4), LfoWaveform.RANDOM);

        FILTER_TYPE_MAP.put (Integer.valueOf (1), FilterType.LOW_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (2), FilterType.HIGH_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (3), FilterType.BAND_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (4), FilterType.BAND_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (5), FilterType.BAND_REJECTION);
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public BlissDetector (final INotifier notifier)
    {
        super ("Bliss Preset/Bank", "ZBP", notifier, new MetadataSettingsUI ("ZBP"), ".zbp", ".zbb");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        try (final ZipFile zipFile = new ZipFile (file))
        {
            final boolean isBank = file.getName ().endsWith (".zbb");
            final ZipEntry entry = zipFile.getEntry (isBank ? "bank.xml" : "program.xml");
            if (entry == null)
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_NO_METADATA_FILE");
                return Collections.emptyList ();
            }

            return this.parseXMLDocument (file, zipFile, entry, isBank);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Load and parse the XML description file.
     *
     * @param sourceFile The file
     * @param zipFile The ZIP file which contains the description file
     * @param entry The ZIP entry of the file
     * @param isBank True if this is a bank otherwise a single program
     * @return The result
     * @throws IOException Error reading the file
     */
    private List<IMultisampleSource> parseXMLDocument (final File sourceFile, final ZipFile zipFile, final ZipEntry entry, final boolean isBank) throws IOException
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final List<IMultisampleSource> results = new ArrayList<> ();
        try (final InputStream in = zipFile.getInputStream (entry))
        {
            final Document document = XMLUtils.parseDocument (new InputSource (in));

            final List<Element> programElements;
            if (isBank)
            {
                final Element top = document.getDocumentElement ();
                final Element programsElement = XMLUtils.getChildElementByName (top, BlissTag.PROGRAMS);
                programElements = XMLUtils.getChildElementsByName (programsElement, BlissTag.PROGRAM);
            }
            else
                programElements = Collections.singletonList (document.getDocumentElement ());

            for (int programIndex = 0; programIndex < programElements.size (); programIndex++)
            {
                final Element programElement = programElements.get (programIndex);
                final Optional<IMultisampleSource> program = this.parseProgram (sourceFile, programElement, programIndex);
                if (program.isPresent ())
                    results.add (program.get ());
            }

            return results;
        }
        catch (final SAXException | RuntimeException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Process a program file and the related FLAC files.
     *
     * @param sourceFile The program file
     * @param programElement The program element to parse
     * @param programIndex The index of the program to parse
     * @return The parsed multi-sample source
     * @throws IOException Could not parse the version attribute
     */
    private Optional<IMultisampleSource> parseProgram (final File sourceFile, final Element programElement, final int programIndex) throws IOException
    {
        if (!BlissTag.PROGRAM.equals (programElement.getNodeName ()))
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, "Unknown Root");
            return Optional.empty ();
        }

        // Empty bank program
        if (XMLUtils.getIntegerAttribute (programElement, "num_zones", 0) == 0)
            return Optional.empty ();

        final String name = programElement.getAttribute ("name");
        if (name.isBlank ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BAD_METADATA_NO_NAME");
            return Optional.empty ();
        }

        final int versionValue = XMLUtils.getIntegerAttribute (programElement, "version", -1);
        if (versionValue < 0)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, "Negative version attribute");
            return Optional.empty ();
        }
        final Version version = new Version (versionValue);
        this.notifier.log ("IDS_BLISS_DETECTED_PROGRAM", name, version.toString ());

        final Element zonesElement = XMLUtils.getChildElementByName (programElement, BlissTag.ZONES);
        if (zonesElement == null)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, "Missing Zones tag");
            return Optional.empty ();
        }

        // 'Only' fake groups in Bliss format to support round-robin inside of groups
        final Map<Integer, IGroup> groupsMap = new TreeMap<> ();

        // Parse all zones
        final List<Element> zoneElements = XMLUtils.getChildElementsByName (zonesElement, BlissTag.ZONE);
        for (int i = 0; i < zoneElements.size (); i++)
        {
            final Element zoneElement = zoneElements.get (i);
            final int resourceGroupIndex = XMLUtils.getIntegerAttribute (zoneElement, "res_group", 0);
            final IGroup group = groupsMap.computeIfAbsent (Integer.valueOf (resourceGroupIndex), index -> new DefaultGroup ("Group " + index));
            this.parseZone (sourceFile, group, zoneElement, programIndex, i, version);
        }

        return Optional.of (this.createMultisampleSource (sourceFile, name, new ArrayList<> (groupsMap.values ())));
    }


    /**
     * Parse the zone information.
     *
     * @param zipFile The main ZIP file
     * @param group The group
     * @param zoneElement The XML zone element
     * @param programIndex The index of the program to parse
     * @param zoneIndex The index of the zone
     * @param version The version of the Bliss document
     */
    private void parseZone (final File zipFile, final IGroup group, final Element zoneElement, final int programIndex, final int zoneIndex, final Version version)
    {
        final Optional<ISampleZone> zoneOpt = this.initZone (zipFile, zoneElement, programIndex, zoneIndex);
        if (zoneOpt.isEmpty ())
            return;

        final Element lowElement = XMLUtils.getChildElementByName (zoneElement, BlissTag.LOW_INPUT_RANGE);
        final Element highElement = XMLUtils.getChildElementByName (zoneElement, BlissTag.HIGH_INPUT_RANGE);
        if (lowElement == null || highElement == null)
            return;

        final ISampleZone zone = zoneOpt.get ();
        zone.setStart (0);
        zone.setStop (XMLUtils.getIntegerAttribute (zoneElement, "num_samples", 0));
        zone.setGain (XMLUtils.getIntegerAttribute (zoneElement, "mp_gain", 0));
        zone.getAmplitudeVelocityModulator ().setDepth (XMLUtils.getDoubleAttribute (zoneElement, "vel_amp", 1.0));
        zone.setPanning (XMLUtils.getIntegerAttribute (zoneElement, "mp_pan", 0) / 100.0);
        final int midiTrigger = XMLUtils.getIntegerAttribute (zoneElement, "midi_trigger", 0);
        if (midiTrigger == 2)
            zone.setOneShot (true);
        else
            zone.setTrigger (midiTrigger == 1 ? TriggerType.RELEASE : TriggerType.ATTACK);
        zone.setKeyRoot (XMLUtils.getIntegerAttribute (zoneElement, "midi_root_key", 60));
        zone.setKeyLow (XMLUtils.getIntegerAttribute (lowElement, "midi_key", 0));
        zone.setKeyHigh (XMLUtils.getIntegerAttribute (highElement, "midi_key", 127));
        zone.setVelocityLow (XMLUtils.getIntegerAttribute (lowElement, "midi_vel", 0));
        zone.setVelocityHigh (XMLUtils.getIntegerAttribute (highElement, "midi_vel", 127));
        zone.setTuning (XMLUtils.getIntegerAttribute (zoneElement, "midi_coarse_tune", 0) + XMLUtils.getIntegerAttribute (zoneElement, "midi_fine_tune", 0) / 100.0);
        zone.setKeyTracking (XMLUtils.getIntegerAttribute (zoneElement, "midi_keycents", 100) / 100.0);

        // Read loop
        final int loopMode = XMLUtils.getIntegerAttribute (zoneElement, "loop_mode", 1);
        if (loopMode > 0)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            switch (loopMode)
            {
                case 1 -> loop.setType (LoopType.FORWARDS);
                case 2 -> loop.setType (LoopType.ALTERNATING);
                case 3 -> loop.setType (LoopType.BACKWARDS);
                default -> loop.setType (LoopType.FORWARDS);
            }

            loop.setStart (XMLUtils.getIntegerAttribute (zoneElement, "loop_start", 0));
            loop.setEnd (XMLUtils.getIntegerAttribute (zoneElement, "loop_end", zone.getStop ()));
            loop.setCrossfade (XMLUtils.getDoubleAttribute (zoneElement, "loop_crossfade_len", 0));
            zone.addLoop (loop);
        }

        // Read envelopes
        final IEnvelopeModulator ampEnvelopeModulator = getEnvelopeModulator ("amp", zoneElement);
        final IEnvelopeModulator amplitudeEnvelopeModulator = zone.getAmplitudeEnvelopeModulator ();
        amplitudeEnvelopeModulator.setDepth (ampEnvelopeModulator.getDepth ());
        amplitudeEnvelopeModulator.setSource (ampEnvelopeModulator.getSource ());

        final IEnvelopeModulator modEnvelopeModulator = getEnvelopeModulator ("mod", zoneElement);
        final double ampEnvDest1 = toDestinationIndex (zoneElement.getAttribute ("amp_env_dest1"), version);
        final double ampEnvDest2 = toDestinationIndex (zoneElement.getAttribute ("amp_env_dest2"), version);
        final double modEnvDest1 = toDestinationIndex (zoneElement.getAttribute ("mod_env_dest1"), version);
        final double modEnvDest2 = toDestinationIndex (zoneElement.getAttribute ("mod_env_dest2"), version);

        IEnvelopeModulator pitchEnvelopeModulator = null;
        double pitchAmount = 0;
        if (ampEnvDest1 == 2 || ampEnvDest2 == 2)
        {
            pitchEnvelopeModulator = ampEnvelopeModulator;
            pitchAmount = XMLUtils.getDoubleAttribute (zoneElement, ampEnvDest1 == 2 ? "amp_env_dest1amt" : "amp_env_dest2amt", 0);
        }
        else if (modEnvDest1 == 2 || modEnvDest2 == 2)
        {
            pitchEnvelopeModulator = modEnvelopeModulator;
            pitchAmount = XMLUtils.getDoubleAttribute (zoneElement, modEnvDest1 == 2 ? "mod_env_dest1amt" : "mod_env_dest2amt", 0);
        }
        IEnvelopeModulator cutoffEnvelopeModulator = null;
        double cutoffAmount = 0;
        if (ampEnvDest1 == 3 || ampEnvDest2 == 3)
        {
            cutoffEnvelopeModulator = ampEnvelopeModulator;
            cutoffAmount = XMLUtils.getDoubleAttribute (zoneElement, ampEnvDest1 == 2 ? "amp_env_dest1amt" : "amp_env_dest2amt", 0);
        }
        else if (modEnvDest1 == 3 || modEnvDest2 == 3)
        {
            cutoffEnvelopeModulator = modEnvelopeModulator;
            cutoffAmount = XMLUtils.getDoubleAttribute (zoneElement, modEnvDest1 == 2 ? "mod_env_dest1amt" : "mod_env_dest2amt", 0);
        }

        // Read filter
        final FilterType filterType = FILTER_TYPE_MAP.get (Integer.valueOf (XMLUtils.getIntegerAttribute (zoneElement, "flt1_type", 0)));
        if (filterType != null)
        {
            final Element cutoffElement = XMLUtils.getChildElementByName (zoneElement, "flt1_cut_frq");
            final Element resonanceElement = XMLUtils.getChildElementByName (zoneElement, "flt1_res_amt");
            if (cutoffElement != null && resonanceElement != null)
            {
                final double normalizedCutoff = XMLUtils.getDoubleAttribute (cutoffElement, VALUE, 1.0);
                // The [0..1] value is first squared (x²) to give it a perceptual curve, then
                // linearly mapped to 20 Hz – 22050 Hz
                final double hertz = 20 + Math.pow (normalizedCutoff, 2) * (22050.0 - 20.0);
                final double resonance = XMLUtils.getDoubleAttribute (resonanceElement, VALUE, 0.0);
                final IFilter filter = new DefaultFilter (filterType, 4, hertz, resonance);
                if (cutoffEnvelopeModulator != null && cutoffAmount > 0)
                {
                    final IEnvelopeModulator cutoffEnvelopeMod = filter.getCutoffEnvelopeModulator ();
                    cutoffEnvelopeMod.setSource (cutoffEnvelopeModulator.getSource ());
                    cutoffEnvelopeMod.setDepth ((cutoffEnvelopeModulator.getDepth () - 0.5) * (cutoffAmount - 0.5) * 4.0);
                }
                filter.getCutoffVelocityModulator ().setDepth (XMLUtils.getDoubleAttribute (zoneElement, "flt1_vel_trk", 0));
                filter.setCutoffKeyTracking (XMLUtils.getDoubleAttribute (zoneElement, "flt1_kbd_trk", 0));

                zone.setFilter (filter);
            }
        }

        final int bendRange = (int) Math.round (XMLUtils.getDoubleAttribute (zoneElement, "sys_pit_rng", 0.083333335816860198975) * 2400.0);
        zone.setBendUp (bendRange);
        zone.setBendDown (-bendRange);

        if (pitchEnvelopeModulator != null && pitchAmount > 0)
        {
            final IEnvelopeModulator pitchEnvelopeMod = zone.getPitchEnvelopeModulator ();
            pitchEnvelopeMod.setSource (pitchEnvelopeModulator.getSource ());
            pitchEnvelopeMod.setDepth ((pitchEnvelopeModulator.getDepth () - 0.5) * (pitchAmount - 0.5) * 4.0);
        }

        if (version.supportsRoundRobins ())
        {
            final int sequenceLength = XMLUtils.getIntegerAttribute (zoneElement, "seq_length", 0);
            if (sequenceLength > 1)
            {
                zone.setSequencePosition (XMLUtils.getIntegerAttribute (zoneElement, "seq_position", 1));
                zone.setPlayLogic (PlayLogic.ROUND_ROBIN);
            }
        }

        if (version.supportsKeySwitches ())
        {
            // IMPROVE Add if key-switches are support in CWM
            // `sw_last`, -1, Key-switch note this zone responds to `sw_last`, -1 = disabled
            // `sw_lokey`, -1, Key-switch range low bound (-1 = not set)
            // `sw_hikey`, -1, Key-switch range high bound (-1 = not set)
        }

        applyLfo (zone, toDestinationIndex (zoneElement.getAttribute ("mod_lfo1_dest"), version), zoneElement, 1);
        applyLfo (zone, toDestinationIndex (zoneElement.getAttribute ("mod_lfo2_dest"), version), zoneElement, 2);

        group.addSampleZone (zone);
    }


    private static void applyLfo (final ISampleZone zone, final int destinationIndex, final Element zoneElement, final int lfoIndex)
    {
        final Optional<IFilter> filter = zone.getFilter ();
        final ILfoModulator modulator = switch (destinationIndex)
        {
            case 1 -> zone.getAmplitudeLfoModulator ();
            case 2 -> zone.getPitchLfoModulator ();
            case 3 -> filter.isPresent () ? filter.get ().getCutoffLfoModulator () : null;
            default -> null;
        };

        if (modulator == null)
            return;

        final double amount = getDoubleValueAttribute (zoneElement, MOD_LFO + lfoIndex + "_amt", 0);
        if (amount == 0)
            return;
        modulator.setDepth (amount / 2.0 + 0.5);

        final ILfo lfo = modulator.getSource ();
        final int lfoType = XMLUtils.getIntegerAttribute (zoneElement, MOD_LFO + lfoIndex + "_type", 0);
        lfo.setWaveform (LFO_WAVE_FORMS.getOrDefault (Integer.valueOf (lfoType), LfoWaveform.SINE));
        // IMPROVE In sync mode it's round(1 + x * 255) 16th notes

        lfo.setDelay (denormalizeLfoTime (getDoubleValueAttribute (zoneElement, MOD_LFO + lfoIndex + "_del", 0.0)));
        final double phase = getDoubleValueAttribute (zoneElement, MOD_LFO + lfoIndex + "_phs", 1.0);
        final boolean isKeySync = phase <= 0.99725;
        lfo.setKeySync (isKeySync);
        lfo.setStartPhase (isKeySync ? phase : 0.0);
        lfo.setRate (lfoRateToFrequency (getDoubleValueAttribute (zoneElement, MOD_LFO + lfoIndex + "_rat", 0.7)));

        // IMPROVE If clock sync for LFO is supported
        // XMLUtils.getIntegerAttribute (zoneElement, "mod_lfo" + lfoIndex + "_syn", 0) == 1
        // Rate is calculated in Sync mode like this: round(1 + (1 - x) * 255) 16th notes per cycle
    }


    private Optional<ISampleZone> initZone (final File zipFile, final Element zoneElement, final int programIndex, final int zoneIndex)
    {
        final String originalFilename = zoneElement.getAttribute ("name");
        if (originalFilename == null || originalFilename.isBlank ())
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, "Missing name attribute");
            return Optional.empty ();
        }

        // The name is like "MyName.wav" but reference is a fixed path:
        // program_000/zone_000.flac (three-digit zero-padded index)
        IFileBasedSampleData sampleData;
        try
        {
            final File flacFile = new File (String.format ("program_%03d/zone_%03d.flac", Integer.valueOf (programIndex), Integer.valueOf (zoneIndex)));
            sampleData = new FlacFileSampleData (zipFile, flacFile);
        }
        catch (final IOException ex)
        {
            // If a zone references an external sample file ('path' is set), the FLAC audio
            // entry for that zone may be omitted and the original file on disk is used (developer
            // fall-back option). The path is always absolute.
            final String filepath = zoneElement.getAttribute ("path");
            if (filepath == null || filepath.isBlank ())
            {
                this.notifier.logError (IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND, ex);
                return Optional.empty ();
            }

            final File sampleFile = new File (filepath);
            if (!sampleFile.exists ())
            {
                this.notifier.logError (IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND, sampleFile.getAbsolutePath ());
                return Optional.empty ();
            }
            try
            {
                sampleData = AbstractDetector.createSampleData (sampleFile, this.notifier);
            }
            catch (final IOException ex2)
            {
                this.notifier.logError (IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND, ex2);
                return Optional.empty ();
            }
        }

        return Optional.of (new DefaultSampleZone (FileUtils.getNameWithoutType (originalFilename), sampleData));
    }


    private static IEnvelopeModulator getEnvelopeModulator (final String prefix, final Element zoneElement)
    {
        final double amount = getDoubleValueAttribute (zoneElement, prefix + "_env_amt", 1.0);
        final IEnvelopeModulator envelopeModulator = new DefaultEnvelopeModulator (amount);
        final IEnvelope envelope = envelopeModulator.getSource ();

        envelope.setAttackTime (denormalizeTime (getDoubleValueAttribute (zoneElement, prefix + "_env_att", 0.0)));
        envelope.setAttackSlope (denormalizeSlope (getDoubleValueAttribute (zoneElement, prefix + "_env_att_shp", 0.5)));
        envelope.setDecayTime (denormalizeTime (getDoubleValueAttribute (zoneElement, prefix + "_env_dec", 0.0)));
        envelope.setDecaySlope (denormalizeSlope (getDoubleValueAttribute (zoneElement, prefix + "_env_dec_shp", 0.5)));
        envelope.setSustainLevel (getDoubleValueAttribute (zoneElement, prefix + "_env_sus", 1.0));
        envelope.setReleaseTime (denormalizeTime (getDoubleValueAttribute (zoneElement, prefix + "_env_rel", 0.0)));
        envelope.setReleaseSlope (denormalizeSlope (getDoubleValueAttribute (zoneElement, prefix + "_env_rel_shp", 0.5)));

        return envelopeModulator;
    }


    private static double denormalizeTime (final double normalizedValue)
    {
        return 0.001 + Math.pow (normalizedValue, 4) * (16.0 - 0.001);
    }


    private static double denormalizeLfoTime (final double normalizedValue)
    {
        return Math.pow (normalizedValue, 4) * 16.0;
    }


    // 0..1 (0.0: concave (slow start, fast end), 0.5: linear, 1.0: convex (fast start, slow end))
    // -> -1..1 (logarithmic..exponential)
    private static double denormalizeSlope (final double normalizedValue)
    {
        return (normalizedValue - 0.5) * -2.0;
    }


    private static double getDoubleValueAttribute (final Element parentElement, final String childElementName, final double defaultValue)
    {
        final Element childElement = XMLUtils.getChildElementByName (parentElement, childElementName);
        return childElement == null ? defaultValue : XMLUtils.getDoubleAttribute (childElement, VALUE, defaultValue);
    }


    private static int toDestinationIndex (final String value, final Version version)
    {
        if (value == null)
            return 0;
        if (version.supportsIntegerDestinations ())
            return Integer.parseInt (value);
        return (int) Math.floor (Float.parseFloat (value) * 14.0);
    }


    private static double lfoRateToFrequency (final double x)
    {
        return 220.5 * Math.pow (2, -14 * (1 - x));
    }


    private class Version
    {
        final int major;
        final int minor;
        final int patch;


        /**
         * Constructor.
         *
         * @param version The version as an integer
         * @throws IOException Could not parse the version
         */
        public Version (final int version) throws IOException
        {
            String hex = StringUtils.padLeftSpaces (StringUtils.formatHexStr (version), 6);
            hex = hex.substring (hex.length () - 6);
            if (hex.length () != 6)
                throw new IOException ();

            this.major = Integer.parseInt (hex.substring (0, 2).trim (), 16);
            this.minor = Integer.parseInt (hex.substring (2, 4), 16);
            this.patch = Integer.parseInt (hex.substring (4, 6), 16);
        }


        /**
         * Does this version support key-switches? Requires version 3.8+.
         *
         * @return True if it does
         */
        public boolean supportsKeySwitches ()
        {
            return this.major > 3 || (this.major == 3 && this.minor >= 8);
        }


        /**
         * Does this version support round-robins? Requires version 3.8+.
         *
         * @return True if it does
         */
        public boolean supportsRoundRobins ()
        {
            return this.major > 3 || (this.major == 3 && this.minor >= 8);
        }


        /**
         * Does this version support envelope/LFO destinations as plain integers? Requires version
         * 3.18+.
         *
         * @return True if it does
         */
        public boolean supportsIntegerDestinations ()
        {
            return this.major > 3 || (this.major == 3 && this.minor >= 18);
        }


        /** {@inheritDoc} */
        @Override
        public String toString ()
        {
            return this.major + "." + this.minor + "." + this.patch;
        }
    }
}
