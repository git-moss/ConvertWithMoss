// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.bliss;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipOutputStream;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.XMLUtils;


/**
 * Creator for discoDSP Bliss multi-sample files. Such a file is a renamed ZIP file with the ending
 * ".zbp" and contains all samples (in FLAC format) and a metadata description file
 * (program.xml/bank.xml).
 *
 * @author Jürgen Moßgraber
 */
public class BlissCreator extends AbstractCreator<EmptySettingsUI>
{
    // The version number to use: 3.8.0
    private static final int                      BLISS_VERSION            = 0x30800;

    private final static DestinationAudioFormat   DESTINATION_AUDIO_FORMAT = new DestinationAudioFormat (new int []
    {
        16,
        24
    }, 96000, true);

    private static final Map<FilterType, Integer> FILTER_TYPE_MAP          = new EnumMap<> (FilterType.class);
    static
    {
        FILTER_TYPE_MAP.put (FilterType.LOW_PASS, Integer.valueOf (1));
        FILTER_TYPE_MAP.put (FilterType.HIGH_PASS, Integer.valueOf (2));
        FILTER_TYPE_MAP.put (FilterType.BAND_PASS, Integer.valueOf (3));
        FILTER_TYPE_MAP.put (FilterType.BAND_REJECTION, Integer.valueOf (5));
    }

    private int programIndex = -1;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public BlissCreator (final INotifier notifier)
    {
        super ("Bliss Preset/Bank", "ZBP", notifier, EmptySettingsUI.INSTANCE);
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return;
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);
        final Element topElement = document.createElement (BlissTag.PROGRAM);
        document.appendChild (topElement);

        createPresetXML (document, topElement, multisampleSource);
        final Optional<String> xml = this.createXMLString (document);
        if (xml.isEmpty ())
            return;

        final File multiFile = this.createUniqueFilename (destinationFolder, createSafeFilename (multisampleSource.getName ()), "zbp");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        try (final ZipOutputStream zos = new ZipOutputStream (new FileOutputStream (multiFile)))
        {
            zos.setMethod (ZipOutputStream.STORED);
            AbstractCreator.storeTextFile (zos, "program.xml", xml.get (), multisampleSource.getMetadata ().getCreationDateTime ());
            this.programIndex = 0;
            this.storeSampleFiles (zos, null, multisampleSource);
        }
        this.progress.notifyDone ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPresetLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return;
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);
        final Element bankElement = document.createElement (BlissTag.BANK);
        document.appendChild (bankElement);
        final Element programsElement = XMLUtils.addElement (document, bankElement, BlissTag.PROGRAMS);

        final File bankFile = this.createUniqueFilename (destinationFolder, createSafeFilename (libraryName), "zbb");
        this.notifier.log ("IDS_NOTIFY_STORING", bankFile.getAbsolutePath ());

        List<IMultisampleSource> sources = multisampleSources;
        if (multisampleSources.size () > 128)
        {
            sources = multisampleSources.subList (0, 128);
            this.notifier.log ("IDS_BLISS_LIMITED_PROGRAM_TO_128");
        }

        try (final ZipOutputStream zos = new ZipOutputStream (new FileOutputStream (bankFile)))
        {
            zos.setMethod (ZipOutputStream.STORED);
            for (int i = 0; i < 128; i++)
            {
                final Element programElement = XMLUtils.addElement (document, programsElement, BlissTag.PROGRAM);
                final IMultisampleSource multisampleSource = i < sources.size () ? sources.get (i) : null;
                createPresetXML (document, programElement, multisampleSource);
                this.programIndex = i;
                if (multisampleSource != null)
                    this.storeSampleFiles (zos, null, multisampleSource);
            }

            final Optional<String> xml = this.createXMLString (document);
            AbstractCreator.storeTextFile (zos, "bank.xml", xml.get (), null);
        }
        this.progress.notifyDone ();
    }


    /**
     * Create the text of the description file.
     *
     * @param document The XML document
     * @param topElement The top element to which to add the children
     * @param multisampleSource The multi-sample
     * @throws IOException Could not access a sample
     */
    private static void createPresetXML (final Document document, final Element topElement, final IMultisampleSource multisampleSource) throws IOException
    {
        XMLUtils.setIntegerAttribute (topElement, "version", BLISS_VERSION);
        topElement.setAttribute ("name", multisampleSource == null ? "..." : multisampleSource.getName ());
        final Element zonesElement = XMLUtils.addElement (document, topElement, BlissTag.ZONES);

        int zoneIndex = 0;
        int roundRobinGroup = 0;
        if (multisampleSource != null)
        {
            final Map<IGroup, Integer> roundRobinGroups = multisampleSource.getRoundRobinGroups ();
            for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            {
                final List<ISampleZone> zones = group.getSampleZones ();

                // Round robin is only supported on a group level, not on program level with
                // multiple groups
                int sequenceLength = -1;
                if (!roundRobinGroups.containsKey (group))
                {
                    for (final ISampleZone zone: zones)
                        if (zone.getPlayLogic () == PlayLogic.ROUND_ROBIN)
                            sequenceLength = Math.max (sequenceLength, zone.getSequencePosition ());
                    if (sequenceLength > 1)
                        roundRobinGroup++;
                }

                for (final ISampleZone zone: zones)
                {
                    // A program may contain up to 128 zones
                    if (zoneIndex == 128)
                        break;
                    createSampleZone (document, roundRobinGroup, sequenceLength, zonesElement, zoneIndex, zone);
                    zoneIndex++;
                }
            }
        }
        XMLUtils.setIntegerAttribute (topElement, "num_zones", zoneIndex);
    }


    /**
     * Creates the metadata for one sample zone.
     *
     * @param document The XML document
     * @param roundRobinGroup The index of the group () if it is used for round-robin
     * @param sequenceLength The sequence length for round-robin
     * @param multisampleElement The element where to add the sample zone information
     * @param zoneIndex The index of the zone
     * @param zone Where to get the sample zone info from
     * @throws IOException Could not access the sample
     */
    private static void createSampleZone (final Document document, final int roundRobinGroup, final int sequenceLength, final Element multisampleElement, final int zoneIndex, final ISampleZone zone) throws IOException
    {
        final Element zoneElement = XMLUtils.addElement (document, multisampleElement, BlissTag.ZONE);
        zoneElement.setAttribute ("name", zone.getName () + ".wav");
        zoneElement.setAttribute ("path", "");

        XMLUtils.setIntegerAttribute (zoneElement, "num_samples", zone.getSampleData ().getAudioMetadata ().getNumberOfSamples ());
        XMLUtils.setIntegerAttribute (zoneElement, "mp_gain", (int) Math.round (zone.getGain ()));
        XMLUtils.setDoubleAttribute (zoneElement, "vel_amp", zone.getAmplitudeVelocityModulator ().getDepth (), 2);
        XMLUtils.setIntegerAttribute (zoneElement, "mp_pan", (int) Math.round (zone.getPanning () * 100.0));
        XMLUtils.setIntegerAttribute (zoneElement, "midi_trigger", zone.getTrigger () == TriggerType.RELEASE ? 1 : 0);
        XMLUtils.setIntegerAttribute (zoneElement, "midi_root_key", zone.getKeyRoot ());

        if (sequenceLength > 1)
        {
            XMLUtils.setIntegerAttribute (zoneElement, "seq_length", sequenceLength);
            XMLUtils.setIntegerAttribute (zoneElement, "seq_position", zone.getSequencePosition ());
            XMLUtils.setIntegerAttribute (zoneElement, "res_group", roundRobinGroup);
        }

        final Element lowElement = XMLUtils.addElement (document, zoneElement, BlissTag.LOW_INPUT_RANGE);
        XMLUtils.setIntegerAttribute (lowElement, "midi_key", zone.getKeyLow ());
        XMLUtils.setIntegerAttribute (lowElement, "midi_vel", zone.getVelocityLow ());
        final Element highElement = XMLUtils.addElement (document, zoneElement, BlissTag.HIGH_INPUT_RANGE);
        XMLUtils.setIntegerAttribute (highElement, "midi_key", zone.getKeyHigh ());
        XMLUtils.setIntegerAttribute (highElement, "midi_vel", zone.getVelocityHigh ());

        final double tune = zone.getTuning ();
        final int coarseTuning = (int) Math.round (tune);
        XMLUtils.setIntegerAttribute (zoneElement, "midi_coarse_tune", coarseTuning);
        XMLUtils.setIntegerAttribute (zoneElement, "midi_fine_tune", (int) Math.round ((tune - coarseTuning) * 100.0));
        XMLUtils.setIntegerAttribute (zoneElement, "midi_keycents", (int) Math.round (zone.getKeyTracking () * 100.0));

        // Create loop
        final List<ISampleLoop> loops = zone.getLoops ();
        if (!loops.isEmpty ())
        {
            int loopMode = 1;
            final ISampleLoop loop = loops.get (0);
            switch (loop.getType ())
            {
                case FORWARDS:
                    loopMode = 1;
                    break;
                case ALTERNATING:
                    loopMode = 2;
                    break;
                case BACKWARDS:
                    loopMode = 3;
                    break;
                default:
                    loopMode = 1;
                    break;
            }
            XMLUtils.setIntegerAttribute (zoneElement, "loop_mode", loopMode);
            XMLUtils.setIntegerAttribute (zoneElement, "loop_start", loop.getStart ());
            XMLUtils.setIntegerAttribute (zoneElement, "loop_end", loop.getEnd ());
            XMLUtils.setDoubleAttribute (zoneElement, "loop_crossfade_len", loop.getCrossfade (), 2);
        }

        // Set envelopes
        setEnvelopeModulator (document, "amp", zoneElement, zone.getAmplitudeEnvelopeModulator ());

        boolean hasModEnvelope = false;
        final IEnvelopeModulator pitchEnvelopeModulator = zone.getPitchEnvelopeModulator ();
        final double pitchDepth = pitchEnvelopeModulator.getDepth ();
        if (pitchDepth != 0)
        {
            // Must be at max. Depth is set via mod_env_dest1amt below!
            pitchEnvelopeModulator.setDepth (1);
            setEnvelopeModulator (document, "mod", zoneElement, pitchEnvelopeModulator);
            hasModEnvelope = true;
            XMLUtils.setDoubleAttribute (zoneElement, "mod_env_dest1", fromDestinationIndex (2), 8);
            setDoubleValueAttribute (document, zoneElement, "mod_env_dest1amt", pitchDepth / 2.0 + 0.5);
        }

        // Set filter
        final Optional<IFilter> filterOpt = zone.getFilter ();
        if (filterOpt.isPresent ())
        {
            final IFilter filter = filterOpt.get ();
            final Integer filterIndex = FILTER_TYPE_MAP.get (filter.getType ());
            if (filterIndex != null)
            {
                XMLUtils.setIntegerAttribute (zoneElement, "flt1_type", filterIndex.intValue ());

                final Element cutoffElement = XMLUtils.addElement (document, zoneElement, "flt1_cut_frq");
                final Element resonanceElement = XMLUtils.addElement (document, zoneElement, "flt1_res_amt");

                // The [0..1] value is first squared (x²) to give it a perceptual curve, then
                // linearly mapped to 20 Hz – 22050 Hz
                final double normalizedCutoff = Math.sqrt ((filter.getCutoff () - 20) / (22050.0 - 20.0));
                XMLUtils.setDoubleAttribute (cutoffElement, "value", normalizedCutoff, 8);
                XMLUtils.setDoubleAttribute (resonanceElement, "value", filter.getResonance (), 8);

                final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
                final double cutoffDepth = cutoffEnvelopeModulator.getDepth ();
                if (cutoffDepth != 0)
                {
                    if (!hasModEnvelope)
                    {
                        // Must be at max. Depth is set via mod_env_dest1amt below!
                        cutoffEnvelopeModulator.setDepth (1);
                        setEnvelopeModulator (document, "mod", zoneElement, cutoffEnvelopeModulator);
                    }

                    XMLUtils.setDoubleAttribute (zoneElement, "mod_env_dest2", fromDestinationIndex (3), 8);
                    setDoubleValueAttribute (document, zoneElement, "mod_env_dest2amt", cutoffDepth / 2.0 + 0.5);
                }
                XMLUtils.setDoubleAttribute (zoneElement, "flt1_vel_trk", filter.getCutoffVelocityModulator ().getDepth (), 2);
            }
        }

    }


    /** {@inheritDoc} */
    @Override
    protected void rewriteFile (final IMultisampleSource multisampleSource, final ISampleZone zone, final OutputStream outputStream, final DestinationAudioFormat destinationFormat, final boolean trim) throws IOException
    {
        ISampleData sampleData = zone.getSampleData ();
        if (sampleData == null)
            return;

        // Trim and convert to FLAC
        final Path tempFile = Files.createTempFile ("CWM-", ".flac");
        try
        {
            // Trim sample from zone start to end
            if (zone.getStart () > 0)
            {
                final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData, DESTINATION_AUDIO_FORMAT);
                trimStartToEnd (waveFile, zone);
                sampleData = new WavFileSampleData (waveFile);
            }

            // It is important to write to a file otherwise the FLAC header is broken!
            AudioFileUtils.compressToFLAC (sampleData, FLAC_TARGET_FORMAT, tempFile.toFile ());

            Files.copy (tempFile, outputStream);
        }
        catch (final UnsupportedAudioFileException ex)
        {
            throw new IOException (ex);
        }
        finally
        {
            Files.deleteIfExists (tempFile);
        }
    }


    /** {@inheritDoc} */
    @Override
    protected String createFileName (final int zoneIndex, final ISampleZone zone)
    {
        return String.format ("program_%03d/zone_%03d.flac", Integer.valueOf (this.programIndex), Integer.valueOf (zoneIndex));
    }


    private static void setEnvelopeModulator (final Document document, final String prefix, final Element zoneElement, final IEnvelopeModulator envelopeModulator)
    {
        setDoubleValueAttribute (document, zoneElement, prefix + "_env_amt", envelopeModulator.getDepth ());
        final IEnvelope envelope = envelopeModulator.getSource ();
        setDoubleValueAttribute (document, zoneElement, prefix + "_env_att", normalizeTime (envelope.getAttackTime ()));
        setDoubleValueAttribute (document, zoneElement, prefix + "_env_att_shp", normalizeSlope (envelope.getAttackSlope ()));
        setDoubleValueAttribute (document, zoneElement, prefix + "_env_dec", normalizeTime (Math.max (0, envelope.getHoldTime ()) + Math.max (0, envelope.getDecayTime ())));
        setDoubleValueAttribute (document, zoneElement, prefix + "_env_dec_shp", normalizeSlope (envelope.getDecaySlope ()));
        final double sustainLevel = envelope.getSustainLevel ();
        setDoubleValueAttribute (document, zoneElement, prefix + "_env_sus", sustainLevel < 0 ? 1 : sustainLevel);
        setDoubleValueAttribute (document, zoneElement, prefix + "_env_rel", normalizeTime (envelope.getReleaseTime ()));
        setDoubleValueAttribute (document, zoneElement, prefix + "_env_rel_shp", normalizeSlope (envelope.getReleaseSlope ()));
        return;
    }


    private static double normalizeTime (final double value)
    {
        if (value < 0)
            return 0;
        return Math.pow (value / 16.0, 0.25);
    }


    // -1..1 (logarithmic..exponential) ->
    // 0..1 (0.0: concave (slow start, fast end), 0.5: linear, 1.0: convex (fast start, slow end))
    private static double normalizeSlope (final double value)
    {
        return -value / 2.0 + 0.5;
    }


    private static void setDoubleValueAttribute (final Document document, final Element parentElement, final String childElementName, final double value)
    {
        final Element childElement = XMLUtils.addElement (document, parentElement, childElementName);
        XMLUtils.setDoubleAttribute (childElement, "value", value, 2);
    }


    private static double fromDestinationIndex (final int value)
    {
        return (value + 0.5) / 14.0;
    }
}
