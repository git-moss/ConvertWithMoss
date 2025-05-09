// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
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
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());

        // Create a sub-folder to have the EXS file together with the samples
        final File subFolder = this.createUniqueFilename (destinationFolder, sampleName, "");
        if (!subFolder.exists () && !subFolder.mkdirs ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", subFolder.getAbsolutePath ());
            return;
        }

        final File multiFile = this.createUniqueFilename (subFolder, sampleName, "exs");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        final Map<String, File> writtenSamples = new HashMap<> ();
        for (final File sampleFile: this.writeSamples (subFolder, multisampleSource))
            writtenSamples.put (sampleFile.getName (), sampleFile);
        storeMultisample (multisampleSource, multiFile, writtenSamples);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    private void storeMultisample (final IMultisampleSource multisampleSource, final File multiFile, final Map<String, File> writtenSamples) throws IOException
    {
        final boolean isBigEndian = true;

        final EXS24File exs24File = new EXS24File (this.notifier);

        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);
        final Map<IGroup, Integer> roundRobinGroups = multisampleSource.getRoundRobinGroups ();
        for (final IGroup group: groups)
        {
            final EXS24Group exsGroup = new EXS24Group ();
            exs24File.addGroup (exsGroup);
            exsGroup.name = group.getName ();
            exsGroup.releaseTrigger = group.getTrigger () == TriggerType.RELEASE;

            if (roundRobinGroups.containsKey (group))
            {
                final int sequencePosition = roundRobinGroups.get (group).intValue ();
                if (sequencePosition > 0)
                {
                    exsGroup.roundRobinGroupPos = sequencePosition - 2;
                    exsGroup.enableByRoundRobin = true;
                }
            }

            for (final ISampleZone zone: group.getSampleZones ())
            {
                final EXS24Zone exs24Zone = exs24File.createZone ();
                final EXS24Sample exs24Sample = exs24File.createSample (exs24Zone);

                // Fill zone
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
                exs24Zone.pan = (int) (zone.getPanning () * 50);

                final List<ISampleLoop> loops = zone.getLoops ();
                exs24Zone.loopOn = !loops.isEmpty ();
                if (exs24Zone.loopOn)
                {
                    final ISampleLoop loop = loops.get (0);
                    exs24Zone.loopStart = loop.getStart ();
                    exs24Zone.loopEnd = loop.getEnd () + 1;
                    exs24Zone.loopCrossfade = loop.getCrossfadeInSamples ();
                }

                // Fill sample
                final ISampleData sampleData = zone.getSampleData ();
                final IAudioMetadata audioMetadata = sampleData.getAudioMetadata ();
                final String name = zone.getName () + ".wav";
                final File sampleFile = writtenSamples.get (name);
                exs24Sample.name = name;
                exs24Sample.waveDataStart = (int) WaveFile.getPositionOfDataChunkData (sampleFile);
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

        exs24File.createInstrument (StringUtils.fixASCII (multisampleSource.getName ()));

        // Fill global parameters from zone 1
        if (!groups.isEmpty ())
        {
            final List<ISampleZone> sampleZones = groups.get (0).getSampleZones ();
            if (!sampleZones.isEmpty ())
            {
                final ISampleZone zone = sampleZones.get (0);
                // Pitch bend up/down
                exs24File.addParameter (EXS24Parameters.PITCH_BEND_UP, Math.clamp (Math.round (zone.getBendDown () / 100.0), 0, 24));
                exs24File.addParameter (EXS24Parameters.PITCH_BEND_DOWN, Math.clamp (Math.round (zone.getBendDown () / 100.0), -24, 0));

                final double velocityDepth = zone.getAmplitudeVelocityModulator ().getDepth ();
                final int velocityModulation = (int) Math.round (Math.clamp ((1 - velocityDepth) * -60.0, -60, 0));
                exs24File.addParameter (EXS24Parameters.ENV1_VEL_SENS, velocityModulation);

                createEnvelope (exs24File.getParameters (), 1, zone.getAmplitudeEnvelopeModulator ());
                applyFilterParameters (exs24File.getParameters (), multisampleSource.getGlobalFilter ());
            }
        }

        try (final OutputStream out = new FileOutputStream (multiFile))
        {
            exs24File.write (out, isBigEndian);
        }
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

        final double attackSlope = envelope.getAttackSlope ();
        if (attackSlope != 0)
        {
            int v = (int) Math.round (attackSlope * 99);
            if (v < 0)
                v += 0x100 + 0xFF00;
            parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_ATK_CURVE : EXS24Parameters.ENV2_ATK_CURVE, v);
        }
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