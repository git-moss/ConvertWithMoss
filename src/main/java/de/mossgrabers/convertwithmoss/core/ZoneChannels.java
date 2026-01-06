// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.exception.CombinationNotPossibleException;
import de.mossgrabers.convertwithmoss.format.KeyMapping;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;


/**
 * Enumeration for different channel configurations of zones.
 *
 * @author Jürgen Moßgraber
 */
public enum ZoneChannels
{
    /** All zones reference mono samples. */
    MONO,
    /** All zones reference stereo samples. */
    STEREO,
    /** All zones reference mono samples which are either hard panned left or right. */
    SPLIT_STEREO,
    /** Mixed mono and stereo samples. */
    MIXED;


    private static final SampleZoneComparator SAMPLE_ZONE_COMPARATOR = new SampleZoneComparator ();


    /**
     * Checks for the channel setup across all zones of the given groups.
     *
     * @param groups The groups to check
     * @return The channel configuration
     * @throws IOException Could not detect the number of channels
     */
    public static ZoneChannels detectChannelConfiguration (final List<IGroup> groups) throws IOException
    {
        Boolean stereo = null;
        Boolean splitStereo = null;

        for (final IGroup group: groups)
            for (final ISampleZone sampleZone: group.getSampleZones ())
            {
                final ISampleData sampleData = sampleZone.getSampleData ();
                if (sampleData == null)
                    continue;
                final boolean isStereo = sampleData.getAudioMetadata ().getChannels () == 2;
                if (stereo == null)
                {
                    // First iteration, store if mono or stereo
                    stereo = Boolean.valueOf (isStereo);
                    // If it is already stereo, it cannot be split stereo
                    if (isStereo && splitStereo == null)
                        splitStereo = Boolean.FALSE;
                }
                else
                {
                    // Mixed mono/stereo
                    if (stereo.booleanValue () != isStereo)
                        return ZoneChannels.MIXED;

                    // Check for split stereo which needs to be hard panned left or right
                    if (!isStereo && (splitStereo == null || splitStereo.booleanValue ()))
                    {
                        final double panning = sampleZone.getTuning ();
                        splitStereo = Boolean.valueOf (panning <= -1 || panning >= 1);
                    }
                }
            }

        if (stereo != null && stereo.booleanValue ())
            return ZoneChannels.STEREO;

        return splitStereo != null && splitStereo.booleanValue () ? ZoneChannels.SPLIT_STEREO : ZoneChannels.MONO;
    }


    /**
     * Creates stereo sample zones (with stereo sample files) from split stereo sample zones.
     * Important: the input must have been checked to be split stereo with the
     * detectChannelConfiguration method.
     *
     * @param groups The groups containing the sample zones
     * @return The group containing all resulting stereo sample zones if combination is possible
     * @throws IOException Could not combine the files
     */
    public static Optional<IGroup> combineSplitStereo (final List<IGroup> groups) throws IOException
    {
        // First, separate hard panned left/right channels
        final List<ISampleZone> leftSampleZones = new ArrayList<> ();
        final List<ISampleZone> rightSampleZones = new ArrayList<> ();
        ZoneChannels.getSplitStereo (groups, leftSampleZones, rightSampleZones);
        return combineSplitStereo (leftSampleZones, rightSampleZones);
    }


    /**
     * Creates stereo sample zones (with stereo sample files) from split stereo sample zones.
     * Important: the input must have been checked to be split stereo with the
     * detectChannelConfiguration method.
     *
     * @param leftSampleZones All sample zones of the left channel
     * @param rightSampleZones All sample zones of the right channel
     * @return The group containing all resulting stereo sample zones if combination is possible
     * @throws IOException Could not combine the files
     */
    public static Optional<IGroup> combineSplitStereo (final List<ISampleZone> leftSampleZones, final List<ISampleZone> rightSampleZones) throws IOException
    {
        // Size of left/right must match
        final int size = leftSampleZones.size ();
        if (size != rightSampleZones.size ())
            return Optional.empty ();

        // Sort both arrays by all of their relevant attributes which means that the matching pairs
        // are at the same index
        Collections.sort (leftSampleZones, SAMPLE_ZONE_COMPARATOR);
        Collections.sort (rightSampleZones, SAMPLE_ZONE_COMPARATOR);

        // Audio metadata must match as well as the loops of the left/right pairs!
        for (int i = 0; i < size; i++)
            if (!compareSampleZones (leftSampleZones.get (i), rightSampleZones.get (i)))
                return Optional.empty ();

        // Finally, combine the left/right pairs
        final IGroup group = new DefaultGroup ();
        for (int i = 0; i < size; i++)
        {
            final ByteArrayOutputStream leftBuffer = new ByteArrayOutputStream ();
            final ByteArrayOutputStream rightBuffer = new ByteArrayOutputStream ();
            final ISampleZone leftSampleZone = leftSampleZones.get (i);
            final ISampleZone rightSampleZone = rightSampleZones.get (i);
            leftSampleZone.getSampleData ().writeSample (leftBuffer);
            rightSampleZone.getSampleData ().writeSample (rightBuffer);
            try (final InputStream inLeft = new ByteArrayInputStream (leftBuffer.toByteArray ()); final InputStream inRight = new ByteArrayInputStream (rightBuffer.toByteArray ()))
            {
                leftSampleZone.setSampleData (new WavFileSampleData (inLeft).combine (new WavFileSampleData (inRight)));
            }
            catch (final CombinationNotPossibleException ex)
            {
                throw new IOException (ex);
            }

            leftSampleZone.setPanning (0);
            String commonName = KeyMapping.findCommonPrefix (leftSampleZone.getName (), rightSampleZone.getName ());
            if (commonName.endsWith ("_") || commonName.endsWith ("-"))
                commonName = commonName.substring (0, commonName.length () - 1).trim ();
            leftSampleZone.setName (commonName);

            group.addSampleZone (leftSampleZone);
        }

        return Optional.of (group);
    }


    /**
     * Splits all sample zones from all groups into left/right arrays.
     *
     * @param groups All groups
     * @param leftSampleZones All samples zones which are panned hard left
     * @param rightSampleZones All samples zones which are panned hard right
     */
    private static void getSplitStereo (final List<IGroup> groups, final List<ISampleZone> leftSampleZones, final List<ISampleZone> rightSampleZones)
    {
        for (final IGroup group: groups)
            for (final ISampleZone sampleZone: group.getSampleZones ())
                if (sampleZone.getTuning () <= -1)
                    leftSampleZones.add (sampleZone);
                else
                    rightSampleZones.add (sampleZone);
    }


    private static boolean compareSampleZones (final ISampleZone leftSampleZone, final ISampleZone rightSampleZone) throws IOException
    {
        final List<ISampleLoop> loopsLeft = leftSampleZone.getLoops ();
        final List<ISampleLoop> loopsRight = rightSampleZone.getLoops ();
        if (loopsLeft.size () != loopsRight.size ())
            return false;
        if (!loopsLeft.isEmpty ())
        {
            final ISampleLoop loopLeft = loopsLeft.get (0);
            final ISampleLoop loopRight = loopsRight.get (0);
            if (loopLeft.getStart () != loopRight.getStart () || loopLeft.getEnd () != loopRight.getEnd () || loopLeft.getType () != loopRight.getType ())
                return false;
        }

        final IAudioMetadata metadataLeft = leftSampleZone.getSampleData ().getAudioMetadata ();
        final IAudioMetadata metadataRight = rightSampleZone.getSampleData ().getAudioMetadata ();
        return metadataLeft.getNumberOfSamples () == metadataRight.getNumberOfSamples () && metadataLeft.getBitResolution () == metadataRight.getBitResolution () && metadataLeft.getSampleRate () == metadataRight.getSampleRate ();
    }


    private static class SampleZoneComparator implements Comparator<ISampleZone>
    {
        /** {@inheritDoc} */
        @Override
        public int compare (final ISampleZone o1, final ISampleZone o2)
        {
            int result = Integer.compare (o1.getKeyRoot (), o2.getKeyRoot ());
            if (result != 0)
                return result;

            result = Integer.compare (o1.getKeyLow (), o2.getKeyLow ());
            if (result != 0)
                return result;

            result = Integer.compare (o1.getKeyHigh (), o2.getKeyHigh ());
            if (result != 0)
                return result;

            result = Integer.compare (o1.getNoteCrossfadeLow (), o2.getNoteCrossfadeLow ());
            if (result != 0)
                return result;

            result = Integer.compare (o1.getNoteCrossfadeHigh (), o2.getNoteCrossfadeHigh ());
            if (result != 0)
                return result;

            result = Integer.compare (o1.getVelocityLow (), o2.getVelocityLow ());
            if (result != 0)
                return result;

            result = Integer.compare (o1.getVelocityHigh (), o2.getVelocityHigh ());
            if (result != 0)
                return result;

            result = Integer.compare (o1.getVelocityCrossfadeLow (), o2.getVelocityCrossfadeLow ());
            if (result != 0)
                return result;

            result = Integer.compare (o1.getVelocityCrossfadeHigh (), o2.getVelocityCrossfadeHigh ());
            if (result != 0)
                return result;

            result = Integer.compare (o1.getStart (), o2.getStart ());
            if (result != 0)
                return result;

            result = Integer.compare (o1.getStop (), o2.getStop ());
            if (result != 0)
                return result;

            result = Double.compare (o1.getTuning (), o2.getTuning ());
            if (result != 0)
                return result;

            result = o1.getTrigger ().ordinal () - o2.getTrigger ().ordinal ();
            if (result != 0)
                return result;

            return o1.getName ().compareTo (o2.getName ());
        }
    }
}
