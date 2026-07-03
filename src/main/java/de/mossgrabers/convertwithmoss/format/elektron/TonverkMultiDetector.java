// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.elektron;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkMultiFile.TonverkKeyZone;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkMultiFile.TonverkSampleSlot;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkMultiFile.TonverkVelocityLayer;


/**
 * Detects recursively Elektron multi-sample files in folders. Files must end with <i>.elmulti</i>.
 *
 * @author Jürgen Moßgraber
 */
public class TonverkMultiDetector extends AbstractDetector<MetadataSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public TonverkMultiDetector (final INotifier notifier)
    {
        super ("Elektron Tonverk Multisample", "Elektron", notifier, new MetadataSettingsUI ("Elektron"), ".elmulti", ".eldrum");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final TonverkMultiFile elektronMultiFile = new TonverkMultiFile ();
            elektronMultiFile.parse (file.toPath ());

            for (final String error: elektronMultiFile.errors)
                this.notifier.logText (error);

            final String [] parts = AudioFileUtils.createPathParts (file.getParentFile (), this.sourceFolder, file.getName ());
            return Collections.singletonList (this.convertMultiFile (file, elektronMultiFile, parts));
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private IMultisampleSource convertMultiFile (final File sourceFile, final TonverkMultiFile elektronMultiFile, final String [] parts) throws IOException
    {
        final String multiSampleName = elektronMultiFile.name;
        final IMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, multiSampleName);

        // Create all sample zones and store them by their root note and velocity low value. From
        // these the key-/velocity ranges need to be calculated in the next step
        final TreeMap<Integer, TreeMap<Integer, List<ISampleZone>>> orderedKeyRanges = new TreeMap<> ();
        for (final TonverkKeyZone zone: elektronMultiFile.keyZones)
        {
            final Map<Integer, List<ISampleZone>> keyRangeMap = orderedKeyRanges.computeIfAbsent (Integer.valueOf (zone.pitch), _ -> new TreeMap<> ());
            for (final TonverkVelocityLayer velocityLayer: zone.velocityLayers)
            {
                final List<ISampleZone> sampleZones = this.createSampleZone (zone, velocityLayer, sourceFile.getParentFile ());
                final int velocity = (int) Math.clamp (velocityLayer.velocity * 127.0, 0, 127.0);
                keyRangeMap.put (Integer.valueOf (velocity), sampleZones);
            }
        }

        // Now calculate the key-/velocity ranges
        calculateRanges (orderedKeyRanges);

        // Collapse the maps into groups
        multisampleSource.setGroups (collapseToGroups (orderedKeyRanges));

        // Detect metadata
        final String [] tokens = Arrays.copyOf (parts, parts.length + 1);
        tokens[tokens.length - 1] = multiSampleName;
        multisampleSource.getMetadata ().detectMetadata (this.settingsConfiguration, tokens);

        return multisampleSource;
    }


    private List<ISampleZone> createSampleZone (final TonverkKeyZone zone, final TonverkVelocityLayer velocityLayer, final File parentFile) throws IOException
    {
        final List<ISampleZone> sampleZones = new ArrayList<> ();
        for (final TonverkSampleSlot sampleSlot: velocityLayer.sampleSlots)
        {
            final ISampleZone sampleZone = this.createSampleZone (new File (parentFile, sampleSlot.sample));

            sampleZone.setKeyRoot (zone.pitch);
            sampleZone.setTuning (zone.pitch - zone.keyCenter);

            // A slot without explicit trim points plays the whole sample; default to the full range
            // (0 .. number-of-frames) rather than leaving the model default of -1, which
            // destinations
            // such as the Waldorf QPAT would otherwise write out verbatim.
            final int frames = sampleZone.getSampleData ().getAudioMetadata ().getNumberOfSamples ();
            sampleZone.setStart (sampleSlot.trimStart != null && sampleSlot.trimStart.intValue () >= 0 ? sampleSlot.trimStart.intValue () : 0);
            sampleZone.setStop (sampleSlot.trimEnd != null && sampleSlot.trimEnd.intValue () >= 0 ? sampleSlot.trimEnd.intValue () : frames);

            if ("Forward".equals (sampleSlot.loopMode))
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                loop.setType (LoopType.FORWARDS);
                if (sampleSlot.loopStart != null && sampleSlot.loopStart.intValue () >= 0)
                    loop.setStart (sampleSlot.loopStart.intValue ());
                if (sampleSlot.loopEnd != null && sampleSlot.loopEnd.intValue () >= 0)
                    loop.setEnd (sampleSlot.loopEnd.intValue ());
                if (sampleSlot.loopCrossfade != null && sampleSlot.loopCrossfade.intValue () >= 0)
                    loop.setCrossfadeInSamples (sampleSlot.loopCrossfade.intValue ());
                sampleZone.getLoops ().add (loop);
            }

            sampleZones.add (sampleZone);
        }

        // If there is more than 1 sample, apply round-robin
        if (sampleZones.size () > 1)
            for (int i = 0; i < sampleZones.size (); i++)
                sampleZones.get (i).setSequencePosition (1 + i);

        return sampleZones;
    }


    static void calculateRanges (final TreeMap<Integer, TreeMap<Integer, List<ISampleZone>>> orderedKeyRanges)
    {
        if (orderedKeyRanges == null || orderedKeyRanges.isEmpty ())
            return;

        // TreeMap has ascending order!
        final List<Integer> pitches = new ArrayList<> (orderedKeyRanges.keySet ());

        for (int i = 0; i < pitches.size (); i++)
        {
            final int currentPitch = pitches.get (i).intValue ();

            // Calculate the key-range
            int keyLow;
            if (i == 0)
                keyLow = 0;
            else
            {
                final int previousPitch = pitches.get (i - 1).intValue ();
                keyLow = clamp (previousPitch + (currentPitch - previousPitch) / 2);
            }

            int keyHigh;
            if (i == pitches.size () - 1)
                keyHigh = 127;
            else
            {
                final int nextPitch = pitches.get (i + 1).intValue ();
                keyHigh = clamp (currentPitch + (nextPitch - currentPitch) / 2);
            }

            final Map<Integer, List<ISampleZone>> velocityMap = orderedKeyRanges.get (Integer.valueOf (currentPitch));
            final List<Integer> velocities = new ArrayList<> (velocityMap.keySet ());
            for (int v = 0; v < velocities.size (); v++)
            {
                final int velocityKey = velocities.get (v).intValue ();
                final int velocityLow = v == 0 ? 0 : velocityKey;
                int velocityHigh;
                if (v == velocities.size () - 1)
                    velocityHigh = 127;
                else
                    velocityHigh = clamp (velocities.get (v + 1).intValue () - 1);

                final List<ISampleZone> zones = velocityMap.get (Integer.valueOf (velocityKey));
                for (final ISampleZone zone: zones)
                {
                    zone.setKeyLow (keyLow);
                    zone.setKeyHigh (keyHigh);
                    zone.setVelocityLow (velocityLow);
                    zone.setVelocityHigh (velocityHigh);
                }
            }
        }
    }


    static List<IGroup> collapseToGroups (final TreeMap<Integer, TreeMap<Integer, List<ISampleZone>>> orderedKeyRanges)
    {
        final IGroup defaultGroup = new DefaultGroup ();
        final List<IGroup> groups = new ArrayList<> ();
        groups.add (defaultGroup);

        for (final TreeMap<Integer, List<ISampleZone>> velocityLayers: orderedKeyRanges.values ())
            for (final List<ISampleZone> sampleZones: velocityLayers.values ())
                if (sampleZones.size () == 1)
                    defaultGroup.addSampleZone (sampleZones.get (0));
                else
                {
                    final IGroup roundRobinGroup = new DefaultGroup ();
                    roundRobinGroup.setSampleZones (sampleZones);
                    groups.add (roundRobinGroup);
                }
        return groups;
    }


    private static int clamp (final int value)
    {
        return Math.clamp (value, 0, 127);
    }
}
