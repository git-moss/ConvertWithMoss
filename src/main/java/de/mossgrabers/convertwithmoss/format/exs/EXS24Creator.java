// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;


/**
 * Creator for Logic EXS24 files.
 *
 * @author Jürgen Moßgraber
 */
public class EXS24Creator extends AbstractCreator
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public EXS24Creator (final INotifier notifier)
    {
        super ("Logic EXS24", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        this.addWavChunkOptions (panel);
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.loadWavChunkSettings (config, "EXS24");
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        this.saveWavChunkSettings (config, "EXS24");
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());

        // Create a sub-folder to have the EXS file together with the samples
        final File subFolder = new File (destinationFolder, sampleName);
        if (subFolder.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", subFolder.getAbsolutePath ());
            return;
        }
        if (!subFolder.exists () && !subFolder.mkdirs ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", subFolder.getAbsolutePath ());
            return;
        }
        final File multiFile = new File (subFolder, sampleName + ".exs");
        if (multiFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
            return;
        }

        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        final Map<String, File> writtenSamples = new HashMap<> ();
        for (final File sampleFile: this.writeSamples (subFolder, multisampleSource))
            writtenSamples.put (sampleFile.getName (), sampleFile);
        storeMultisample (multisampleSource, multiFile, writtenSamples);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    private static void storeMultisample (final IMultisampleSource multisampleSource, final File multiFile, final Map<String, File> writtenSamples) throws IOException
    {
        final boolean isBigEndian = true;

        final List<EXS24Zone> exsZones = new ArrayList<> ();
        final List<EXS24Sample> exsSamples = new ArrayList<> ();
        final List<EXS24Group> exsGroups = new ArrayList<> ();

        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);
        for (final IGroup group: groups)
        {
            final EXS24Group exsGroup = new EXS24Group ();
            exsGroups.add (exsGroup);
            exsGroup.name = group.getName ();
            exsGroup.releaseTrigger = group.getTrigger () == TriggerType.RELEASE;

            for (final ISampleZone zone: group.getSampleZones ())
            {
                final EXS24Zone exs24Zone = new EXS24Zone ();
                exsZones.add (exs24Zone);
                final EXS24Sample exs24Sample = new EXS24Sample ();
                exsSamples.add (exs24Sample);

                // Fill zone
                exs24Zone.sampleIndex = exsSamples.size () - 1;
                exs24Zone.groupIndex = exsGroups.size () - 1;
                exs24Zone.name = zone.getName ();

                exs24Zone.keyLow = limitToDefault (zone.getKeyLow (), 0);
                exs24Zone.keyHigh = limitToDefault (zone.getKeyHigh (), 127);
                exs24Zone.key = limitToDefault (zone.getKeyRoot (), exs24Zone.keyLow);
                exs24Zone.velocityRangeOn = true;
                exs24Zone.velocityLow = limitToDefault (zone.getVelocityLow (), 1);
                exs24Zone.velocityHigh = limitToDefault (zone.getVelocityHigh (), 127);
                exs24Zone.sampleStart = zone.getStart ();
                exs24Zone.sampleEnd = zone.getStop ();
                exs24Zone.reverse = zone.isReversed ();
                exs24Zone.volumeAdjust = (int) zone.getGain ();
                exs24Zone.pitch = true;
                final double tune = zone.getTune ();
                exs24Zone.coarseTuning = (int) (tune / 100);
                exs24Zone.fineTuning = (int) (tune % 100);
                exs24Zone.pan = (int) (zone.getPanorama () * 50);

                final List<ISampleLoop> loops = zone.getLoops ();
                exs24Zone.loopOn = !loops.isEmpty ();
                if (exs24Zone.loopOn)
                {
                    final ISampleLoop loop = loops.get (0);
                    exs24Zone.loopStart = loop.getStart ();
                    exs24Zone.loopEnd = loop.getEnd ();
                    exs24Zone.loopCrossfade = loop.getCrossfadeInSamples ();
                }

                // Fill sample
                final ISampleData sampleData = zone.getSampleData ();
                final IAudioMetadata audioMetadata = sampleData.getAudioMetadata ();
                final String name = zone.getName () + ".wav";
                final File sampleFile = writtenSamples.get (name);
                exs24Sample.name = name;
                exs24Sample.waveDataStart = 88;
                exs24Sample.length = audioMetadata.getNumberOfSamples ();
                exs24Sample.sampleRate = audioMetadata.getSampleRate ();
                exs24Sample.bitDepth = audioMetadata.getBitResolution ();
                exs24Sample.channels = audioMetadata.getChannels ();
                exs24Sample.channels2 = audioMetadata.getChannels ();
                exs24Sample.type = isBigEndian ? "WAVE" : "EVAW";
                exs24Sample.size = (int) sampleFile.length ();
                exs24Sample.filePath = "";
                exs24Sample.fileName = name;
            }
        }

        final EXS24Instrument exsInstrument = new EXS24Instrument ();
        exsInstrument.name = StringUtils.fixASCII (multisampleSource.getName ());
        exsInstrument.numZoneBlocks = exsZones.size ();
        exsInstrument.numGroupBlocks = exsGroups.size ();
        exsInstrument.numSampleBlocks = exsSamples.size ();
        exsInstrument.numParameterBlocks = 1;

        // Fill global parameters from zone 1
        final EXS24Parameters exsParameters = new EXS24Parameters ();
        if (!groups.isEmpty ())
        {
            final List<ISampleZone> sampleZones = groups.get (0).getSampleZones ();
            if (!sampleZones.isEmpty ())
            {
                final ISampleZone zone = sampleZones.get (0);
                // Pitch bend up/down
                exsParameters.put (EXS24Parameters.PITCH_BEND_UP, zone.getBendUp () / 100);
                exsParameters.put (EXS24Parameters.PITCH_BEND_DOWN, Math.abs (zone.getBendDown () / 100));

                final double velocityDepth = zone.getAmplitudeVelocityModulator ().getDepth ();
                final int velocityModulation = (int) Math.round (Math.clamp ((1 - velocityDepth) * -60.0, -60, 0));
                exsParameters.put (EXS24Parameters.ENV1_VEL_SENS, velocityModulation);

                createEnvelope (exsParameters, 1, zone.getAmplitudeEnvelopeModulator ());
                applyFilterParameters (exsParameters, multisampleSource.getGlobalFilter ());
            }
        }

        writeAllBlocks (multiFile, exsZones, exsSamples, exsGroups, exsInstrument, exsParameters, isBigEndian);
    }


    private static void createEnvelope (final EXS24Parameters parameters, final int envelopeIndex, final IEnvelopeModulator modulator)
    {
        final IEnvelope envelope = modulator.getSource ();
        final double depth = modulator.getDepth ();
        if (depth == 0)
            return;

        final int delay = formatEnvTime (envelope.getDelayTime ());
        final int attack = formatEnvTime (envelope.getAttackTime ());
        final int hold = formatEnvTime (envelope.getHoldTime ());
        final int decay = formatEnvTime (envelope.getDecayTime ());
        final int sustain = formatEnvVolume (envelope.getSustainLevel (), depth);
        final int release = formatEnvTime (envelope.getReleaseTime ());
        parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_DELAY_START : EXS24Parameters.ENV2_DELAY_START, delay);
        parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_ATK_HI_VEL : EXS24Parameters.ENV2_ATK_HI_VEL, attack);
        parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_HOLD : EXS24Parameters.ENV2_HOLD, hold);
        parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_DECAY : EXS24Parameters.ENV2_DECAY, decay);
        parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_SUSTAIN : EXS24Parameters.ENV2_SUSTAIN, sustain);
        parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_RELEASE : EXS24Parameters.ENV2_RELEASE, release);
    }


    private static void applyFilterParameters (final EXS24Parameters parameters, final Optional<IFilter> filterOpt)
    {
        final boolean isEnabled = filterOpt.isPresent ();
        parameters.put (EXS24Parameters.FILTER1_TOGGLE, isEnabled ? 1 : 0);
        if (!isEnabled)
            return;

        final IFilter filter = filterOpt.get ();
        final int poles = filter.getPoles ();
        final int filterTypeIndex;
        switch (filter.getType ())
        {
            case LOW_PASS:
                switch (poles)
                {
                    default:
                    case 4:
                        filterTypeIndex = 0;
                        break;
                    case 3:
                        filterTypeIndex = 1;
                        break;
                    case 2:
                        filterTypeIndex = 2;
                        break;
                    case 1:
                        filterTypeIndex = 3;
                        break;
                }
                break;

            case HIGH_PASS:
                filterTypeIndex = 4;
                break;

            case BAND_PASS:
                filterTypeIndex = 5;
                break;

            case BAND_REJECTION:
            default:
                parameters.put (EXS24Parameters.FILTER1_TOGGLE, 0);
                return;
        }
        parameters.put (EXS24Parameters.FILTER1_TYPE, filterTypeIndex);

        createEnvelope (parameters, 2, filter.getCutoffEnvelopeModulator ());

        parameters.put (EXS24Parameters.FILTER1_CUTOFF, (int) Math.round (MathUtils.normalize (filter.getCutoff (), 0, IFilter.MAX_FREQUENCY) * 1000.0));
        parameters.put (EXS24Parameters.FILTER1_RESO, (int) (filter.getResonance () * 1000));
    }


    private static void writeAllBlocks (final File multiFile, final List<EXS24Zone> exsZones, final List<EXS24Sample> exsSamples, final List<EXS24Group> exsGroups, final EXS24Instrument exsInstrument, final EXS24Parameters exsParameters, final boolean isBigEndian) throws IOException
    {
        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            final EXS24Block instrumentBlock = exsInstrument.write (isBigEndian);
            instrumentBlock.write (out);

            // Write all group zones
            for (int i = 0; i < exsZones.size (); i++)
            {
                final EXS24Zone exsZone = exsZones.get (i);
                final EXS24Block zoneBlock = exsZone.write (isBigEndian);
                zoneBlock.index = i;
                zoneBlock.write (out);
            }

            // Write all group blocks
            for (int i = 0; i < exsGroups.size (); i++)
            {
                final EXS24Group exsGroup = exsGroups.get (i);
                final EXS24Block groupBlock = exsGroup.write (isBigEndian);
                groupBlock.index = i;
                groupBlock.write (out);
            }

            // Write all sample blocks
            for (int i = 0; i < exsSamples.size (); i++)
            {
                final EXS24Sample exsSample = exsSamples.get (i);
                final EXS24Block sampleBlock = exsSample.write (isBigEndian);
                sampleBlock.index = i;
                sampleBlock.write (out);
            }

            // Write parameters
            final EXS24Block parameterBlock = exsParameters.write (isBigEndian);
            parameterBlock.write (out);
        }
    }


    private static int formatEnvTime (final double time)
    {
        // Maximum time for each step are 10 seconds
        return time < 0 ? 0 : (int) Math.round (time / 10.0 * 127.0);
    }


    private static int formatEnvVolume (final double volume, final double depth)
    {
        return (int) Math.round ((volume < 0 ? 127 : volume * 127.0) * depth);
    }
}