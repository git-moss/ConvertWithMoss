// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.elektron;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.DetectSettings;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.format.elektron.ElektronMultiFile.ElektronKeyZone;
import de.mossgrabers.convertwithmoss.format.elektron.ElektronMultiFile.ElektronSampleSlot;
import de.mossgrabers.convertwithmoss.format.elektron.ElektronMultiFile.ElektronVelocityLayer;


/**
 * Creator for preset files for the Elektron Tonverk (emulti). A preset has a description file and
 * the related samples are in the same folder.
 *
 * @author Jürgen Moßgraber
 */
public class ElektronMultiCreator extends AbstractWavCreator<ElektronMultiCreatorUI>
{
    private static final DestinationAudioFormat OPTIMIZED_AUDIO_FORMAT = new DestinationAudioFormat (new int []
    {
        24
    }, 48000, true);
    private static final DestinationAudioFormat DEFAULT_AUDIO_FORMAT   = new DestinationAudioFormat ();
    private static final Set<Integer>           SUPPORTED_BIT_DEPTHS   = new HashSet<> ();
    static
    {
        SUPPORTED_BIT_DEPTHS.add (Integer.valueOf (16));
        SUPPORTED_BIT_DEPTHS.add (Integer.valueOf (24));
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public ElektronMultiCreator (final INotifier notifier)
    {
        super ("Elektron Tonverk", "Emulti", notifier, new ElektronMultiCreatorUI ("Emulti"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final boolean resample = this.settingsConfiguration.resampleTo2448 ();

        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final File presetFolder = this.createUniqueFilename (destinationFolder, sampleName, "");
        if (!presetFolder.mkdir ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", presetFolder.getAbsolutePath ());
            return;
        }

        final ElektronMultiFile elektronMulti = this.createPreset (multisampleSource);
        final String presetFile = sampleName + ".emulti";
        this.notifier.log ("IDS_NOTIFY_STORING", presetFile);
        elektronMulti.write (new File (presetFolder, presetFile).toPath ());

        // Store all samples
        if (resample)
            recalculateSamplePositions (multisampleSource, 48000);
        this.writeSamples (presetFolder, multisampleSource, resample ? OPTIMIZED_AUDIO_FORMAT : DEFAULT_AUDIO_FORMAT, false);

        this.progress.notifyDone ();
    }


    private ElektronMultiFile createPreset (final IMultisampleSource multiSampleSource) throws IOException
    {
        final List<IGroup> groups = this.combineSplitStereo (multiSampleSource);
        multiSampleSource.setGroups (groups);

        final ElektronMultiFile elektronMulti = new ElektronMultiFile ();
        elektronMulti.name = multiSampleSource.getName ();

        for (final Entry<Integer, TreeMap<Integer, List<ISampleZone>>> velocityLayerMapEntry: multiSampleSource.getOrderedSampleZones (false).entrySet ())
        {
            final ElektronKeyZone keyZone = new ElektronKeyZone ();
            elektronMulti.keyZones.add (keyZone);

            final int keyRoot = velocityLayerMapEntry.getKey ().intValue ();
            keyZone.pitch = keyRoot;

            Double tuning = null;
            boolean tuningIsSame = true;

            for (final Entry<Integer, List<ISampleZone>> sampleZonesEntry: velocityLayerMapEntry.getValue ().entrySet ())
            {
                final ElektronVelocityLayer velocityLayer = new ElektronVelocityLayer ();
                keyZone.velocityLayers.add (velocityLayer);

                final List<ISampleZone> sampleZones = sampleZonesEntry.getValue ();
                velocityLayer.velocity = sampleZonesEntry.getKey ().intValue () / 127.0;
                // If there is only one velocity layer center the velocity
                if (sampleZones.size () == 1 && sampleZones.get (0).getVelocityLow () == 0)
                    velocityLayer.velocity = 0.49411765;

                for (final ISampleZone sampleZone: sampleZones)
                {
                    final ElektronSampleSlot sampleSlot = new ElektronSampleSlot ();
                    velocityLayer.sampleSlots.add (sampleSlot);

                    sampleSlot.sample = sampleZone.getName () + ".wav";
                    sampleSlot.trimStart = Integer.valueOf (sampleZone.getStart ());
                    sampleSlot.trimEnd = Integer.valueOf (sampleZone.getStop ());

                    final List<ISampleLoop> loops = sampleZone.getLoops ();
                    final boolean hasLoop = !loops.isEmpty ();
                    sampleSlot.loopMode = hasLoop ? "Forward" : "Off";
                    if (hasLoop)
                    {
                        final ISampleLoop sampleLoop = loops.get (0);
                        sampleSlot.loopStart = Integer.valueOf (sampleLoop.getStart ());
                        sampleSlot.loopEnd = Integer.valueOf (sampleLoop.getEnd ());
                        sampleSlot.loopCrossfade = Integer.valueOf (sampleLoop.getCrossfadeInSamples ());
                    }

                    if (tuningIsSame)
                        if (tuning == null)
                            tuning = Double.valueOf (sampleZone.getTuning ());
                        else if (tuning.doubleValue () != sampleZone.getTuning ())
                            tuningIsSame = false;
                }
            }

            keyZone.keyCenter = keyRoot + (tuningIsSame && tuning != null ? tuning.doubleValue () : 0);
        }

        return elektronMulti;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkProcessingCompatibility (final DetectSettings detectSettings)
    {
        if (detectSettings.reduceBitDepth <= 0 || SUPPORTED_BIT_DEPTHS.contains (Integer.valueOf (detectSettings.reduceBitDepth)))
            return true;
        this.notifier.log ("IDS_PROCESSING_REDUCE_BITE_DEPTH_NOT_SUPPORTED", Integer.toString (detectSettings.reduceBitDepth), "16, 24");
        return false;
    }
}