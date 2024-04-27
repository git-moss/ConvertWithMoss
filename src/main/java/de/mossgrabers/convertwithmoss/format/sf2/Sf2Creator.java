// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sf2;

import java.io.File;
import java.io.IOException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;


/**
 * Creator for SoundFont 2 files.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2Creator extends AbstractCreator
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Sf2Creator (final INotifier notifier)
    {
        super ("SoundFont 2", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());

        final File multiFile = new File (destinationFolder, sampleName + ".sf2");
        if (multiFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
            return;
        }

        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        storeMultisample (multisampleSource, multiFile);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    private static void storeMultisample (final IMultisampleSource multisampleSource, final File multiFile) throws IOException
    {
        // TODO Mono/stereo files need to be handled differently!

        // final boolean isBigEndian = true;
        //
        // final List<EXS24Zone> exsZones = new ArrayList<> ();
        // final List<EXS24Sample> exsSamples = new ArrayList<> ();
        // final List<EXS24Group> exsGroups = new ArrayList<> ();
        //
        // final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);
        // for (final IGroup group: groups)
        // {
        // final EXS24Group exsGroup = new EXS24Group ();
        // exsGroups.add (exsGroup);
        // exsGroup.name = group.getName ();
        // exsGroup.releaseTrigger = group.getTrigger () == TriggerType.RELEASE;
        //
        // for (final ISampleZone zone: group.getSampleZones ())
        // {
        // final EXS24Zone exs24Zone = new EXS24Zone ();
        // exsZones.add (exs24Zone);
        // final EXS24Sample exs24Sample = new EXS24Sample ();
        // exsSamples.add (exs24Sample);
        //
        // // Fill zone
        // exs24Zone.sampleIndex = exsSamples.size () - 1;
        // exs24Zone.groupIndex = exsGroups.size () - 1;
        // exs24Zone.name = zone.getName ();
        //
        // exs24Zone.key = zone.getKeyRoot ();
        // exs24Zone.keyLow = zone.getKeyLow ();
        // exs24Zone.keyHigh = zone.getKeyHigh ();
        // exs24Zone.velocityRangeOn = true;
        // exs24Zone.velocityLow = zone.getVelocityLow ();
        // exs24Zone.velocityHigh = zone.getVelocityHigh ();
        // exs24Zone.sampleStart = zone.getStart ();
        // exs24Zone.sampleEnd = zone.getStop ();
        // exs24Zone.reverse = zone.isReversed ();
        // exs24Zone.volumeAdjust = (int) zone.getGain ();
        // exs24Zone.pitch = true;
        // final double tune = zone.getTune ();
        // exs24Zone.coarseTuning = (int) (tune / 100);
        // exs24Zone.fineTuning = (int) (tune % 100);
        // exs24Zone.pan = (int) (zone.getPanorama () * 50);
        //
        // final List<ISampleLoop> loops = zone.getLoops ();
        // exs24Zone.loopOn = !loops.isEmpty ();
        // if (exs24Zone.loopOn)
        // {
        // final ISampleLoop loop = loops.get (0);
        // exs24Zone.loopStart = loop.getStart ();
        // exs24Zone.loopEnd = loop.getEnd ();
        // exs24Zone.loopCrossfade = loop.getCrossfadeInSamples ();
        // }
        //
        // // Fill sample
        // final ISampleData sampleData = zone.getSampleData ();
        // final IAudioMetadata audioMetadata = sampleData.getAudioMetadata ();
        // final String name = zone.getName () + ".wav";
        // final File sampleFile = writtenSamples.get (name);
        // exs24Sample.name = name;
        // exs24Sample.waveDataStart = 88;
        // exs24Sample.length = audioMetadata.getNumberOfSamples ();
        // exs24Sample.sampleRate = audioMetadata.getSampleRate ();
        // exs24Sample.bitDepth = audioMetadata.getBitResolution ();
        // exs24Sample.channels = audioMetadata.getChannels ();
        // exs24Sample.channels2 = audioMetadata.getChannels ();
        // exs24Sample.type = isBigEndian ? "WAVE" : "EVAW";
        // exs24Sample.size = (int) sampleFile.length ();
        // exs24Sample.filePath = "";
        // exs24Sample.fileName = name;
        // }
        // }
        //
        // final EXS24Instrument exsInstrument = new EXS24Instrument ();
        // exsInstrument.name = StringUtils.fixASCII (multisampleSource.getName ());
        // exsInstrument.numZoneBlocks = exsZones.size ();
        // exsInstrument.numGroupBlocks = exsGroups.size ();
        // exsInstrument.numSampleBlocks = exsSamples.size ();
        // exsInstrument.numParameterBlocks = 1;
        //
        // // Fill global parameters from zone 1
        // final EXS24Parameters exsParameters = new EXS24Parameters ();
        // if (!groups.isEmpty ())
        // {
        // final List<ISampleZone> sampleZones = groups.get (0).getSampleZones ();
        // if (!sampleZones.isEmpty ())
        // {
        // final ISampleZone zone = sampleZones.get (0);
        // // Pitch bend up/down
        // exsParameters.put (EXS24Parameters.PITCH_BEND_UP, zone.getBendUp () / 100);
        // exsParameters.put (EXS24Parameters.PITCH_BEND_DOWN, Math.abs (zone.getBendDown () /
        // 100));
        //
        // createEnvelope (exsParameters, 1, zone.getAmplitudeModulator ());
        // applyFilterParameters (exsParameters, multisampleSource.getGlobalFilter ());
        // }
        // }
        //
        // writeAllBlocks (multiFile, exsZones, exsSamples, exsGroups, exsInstrument, exsParameters,
        // isBigEndian);
    }
}