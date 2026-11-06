// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synthstrom;

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;


/**
 * The samples which the two oscillators of a Synthstrom Deluge synth (sound) play. Each oscillator
 * plays its own key map - a list of sample ranges, each one ending on its top note - while both
 * share the envelopes and the filters of the sound. A Deluge key map selects its samples by the
 * note only, therefore an oscillator plays one zone on each note: velocity layers and round-robins
 * are reduced to the loudest zone of the note, as in the single oscillator mode.
 * <p>
 * In the dual oscillator mode the second oscillator plays the zones which sound together with
 * those of the first oscillator on the same note, e.g. a swell on top of a body. Only zones which
 * sound together count as layers: key ranges which follow each other, velocity layers,
 * round-robins and the two channels of a stereo recording all fit onto one oscillator, and an
 * overlapping copy of a zone - the same sample with the same root note, tuning and panning - is
 * not a layer either. A zone which is far quieter than the loudest zone of the multi-sample, often
 * a layer which the source switched off by its volume, is near-silent: it is neither counted nor
 * written. A multi-sample in which more than two layers sound together on a note does not fit
 * onto the two oscillators, and neither do two layers whose attack times are more than 2 seconds
 * apart, since the two oscillators share one amplitude envelope, see {@link #getSkipReason()}.
 * <p>
 * Since a Deluge key map has no gaps, an oscillator plays a silent sample on the notes on which
 * only the layer of the other oscillator sounds; otherwise the zone next to these notes would be
 * stretched onto them and add a layer which the source does not have there.
 *
 * @author Jürgen Moßgraber
 */
class DelugeOscillatorLayout
{
    /** Why a multi-sample does not fit onto the two oscillators. */
    enum SkipReason
    {
        /** It fits. */
        NONE,
        /** More than two layers sound together on a note. */
        TOO_MANY_LAYERS,
        /** The attack times of two layers which sound together are too far apart. */
        DIFFERENT_ATTACKS
    }


    /** One range of the key map of an oscillator: a zone and the highest note which plays it. */
    static final class SampleRange
    {
        private final ISampleZone zone;
        private final int         topNote;
        private final boolean     isSilent;


        /**
         * Constructor.
         *
         * @param zone The zone which the range plays
         * @param topNote The highest note of the range
         * @param isSilent True if the zone is the silent sample
         */
        SampleRange (final ISampleZone zone, final int topNote, final boolean isSilent)
        {
            this.zone = zone;
            this.topNote = topNote;
            this.isSilent = isSilent;
        }


        /**
         * Get the zone which the range plays.
         *
         * @return The zone
         */
        ISampleZone getZone ()
        {
            return this.zone;
        }


        /**
         * Get the highest note of the range. The range starts on the note above the top note of
         * the range below it.
         *
         * @return The top note
         */
        int getTopNote ()
        {
            return this.topNote;
        }


        /**
         * Does the range play the silent sample, which keeps the oscillator quiet on notes on
         * which only the layer of the other oscillator sounds?
         *
         * @return True if the range is silent
         */
        boolean isSilent ()
        {
            return this.isSilent;
        }
    }


    /**
     * A zone whose gain is this many dB or more below the loudest zone of the multi-sample is
     * near-silent.
     */
    static final double             NEAR_SILENT_DECIBELS = 40.0;

    private static final int        NUMBER_OF_NOTES      = 128;
    /** The largest number of zones on one note and velocity for an exact count of the layers. */
    private static final int        MAX_EXACT_CANDIDATES = 20;
    private static final String     SILENCE_NAME         = "Silence";
    private static final double     SILENCE_SECONDS      = 0.1;
    private static final int        DEFAULT_SAMPLE_RATE  = 44100;
    private static final double     TOLERANCE            = 0.0001;

    /** The largest difference of the attack times of two layers which sound together in seconds. */
    static final double             MAX_ATTACK_GAP       = 2.0;

    private final List<SampleRange> oscillator1;
    private final List<SampleRange> oscillator2;
    private final int               numberOfLayers;
    private final int               crowdedNoteLow;
    private final int               crowdedNoteHigh;
    private final List<ISampleZone> nearSilentZones;
    private final SkipReason        skipReason;


    /**
     * Constructor.
     *
     * @param oscillator1 The key map of the first oscillator
     * @param oscillator2 The key map of the second oscillator, empty if it is silent
     * @param numberOfLayers The largest number of layers which sound together on a note
     * @param crowdedNoteLow The lowest note on which the multi-sample does not fit onto the two
     *            oscillators, -1 if it fits
     * @param crowdedNoteHigh The highest note of these notes, -1 if it fits
     * @param nearSilentZones The near-silent zones which are left out
     * @param skipReason Why the multi-sample does not fit onto the two oscillators
     */
    private DelugeOscillatorLayout (final List<SampleRange> oscillator1, final List<SampleRange> oscillator2, final int numberOfLayers, final int crowdedNoteLow, final int crowdedNoteHigh, final List<ISampleZone> nearSilentZones, final SkipReason skipReason)
    {
        this.oscillator1 = oscillator1;
        this.oscillator2 = oscillator2;
        this.numberOfLayers = numberOfLayers;
        this.crowdedNoteLow = crowdedNoteLow;
        this.crowdedNoteHigh = crowdedNoteHigh;
        this.nearSilentZones = nearSilentZones;
        this.skipReason = skipReason;
    }


    /**
     * Create a layout in which only the first oscillator plays samples.
     *
     * @param zones The zones of the key map, one per range, ordered by their upper key
     * @return The layout
     */
    static DelugeOscillatorLayout createSingle (final List<ISampleZone> zones)
    {
        return createSingle (zones, Collections.emptyList ());
    }


    /**
     * Create a layout in which only the first oscillator plays samples.
     *
     * @param zones The zones of the key map, one per range, ordered by their upper key
     * @param nearSilentZones The near-silent zones which are left out
     * @return The layout
     */
    private static DelugeOscillatorLayout createSingle (final List<ISampleZone> zones, final List<ISampleZone> nearSilentZones)
    {
        final List<SampleRange> ranges = new ArrayList<> (zones.size ());
        for (final ISampleZone zone: zones)
            ranges.add (new SampleRange (zone, AbstractCreator.limitToDefault (zone.getKeyHigh (), 127), false));
        return new DelugeOscillatorLayout (ranges, Collections.emptyList (), 1, -1, -1, nearSilentZones, SkipReason.NONE);
    }


    /**
     * Create a layout without oscillators for a multi-sample which does not fit onto them.
     *
     * @param skipReason Why it does not fit
     * @param numberOfLayers The largest number of layers which sound together on a note
     * @param analysis The analysis which knows the notes on which it does not fit
     * @return The layout
     */
    private static DelugeOscillatorLayout createSkipped (final SkipReason skipReason, final int numberOfLayers, final LayerAnalysis analysis)
    {
        return new DelugeOscillatorLayout (Collections.emptyList (), Collections.emptyList (), numberOfLayers, analysis.crowdedNoteLow, analysis.crowdedNoteHigh, Collections.emptyList (), skipReason);
    }


    /**
     * Create a layout in which the second oscillator plays the zones which sound together with
     * those of the first one. Near-silent zones are left out. A multi-sample in which no zones
     * sound together gets the single oscillator layout of the given key map, or - if near-silent
     * zones were left out - of the key map of the other zones. A multi-sample which does not fit
     * onto the two oscillators gets a layout without oscillators which only tells why and on which
     * notes, see {@link #getSkipReason()}.
     *
     * @param multisampleSource The multi-sample source
     * @param mappedZones The key map which a single oscillator plays, one zone per range
     * @return The layout
     */
    static DelugeOscillatorLayout createDual (final IMultisampleSource multisampleSource, final List<ISampleZone> mappedZones)
    {
        // Only zones which start to play with the note can be played by an oscillator, a zone
        // which is triggered by the release of the note cannot
        final List<ISampleZone> allZones = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            for (final ISampleZone zone: group.getSampleZones ())
                if (zone.getTrigger () != TriggerType.RELEASE)
                    allZones.add (zone);
        if (allZones.isEmpty ())
            return createSingle (mappedZones);

        // A zone which is far quieter than the loudest one is hardly heard, often a layer which
        // the source switched off by its volume, and is neither counted nor written (the gain of
        // a group is part of the gain of its zones)
        double loudest = Double.NEGATIVE_INFINITY;
        for (final ISampleZone zone: allZones)
            loudest = Math.max (loudest, zone.getGain ());
        final List<ISampleZone> zones = new ArrayList<> ();
        final List<ISampleZone> nearSilentZones = new ArrayList<> ();
        for (final ISampleZone zone: allZones)
            if (zone.getGain () <= loudest - NEAR_SILENT_DECIBELS)
                nearSilentZones.add (zone);
            else
                zones.add (zone);

        final LayerAnalysis analysis = new LayerAnalysis (zones);
        final int numberOfLayers = analysis.countLayers ();
        if (numberOfLayers <= 1)
            return nearSilentZones.isEmpty () ? createSingle (mappedZones) : createSingle (DelugeCreator.getMappedZones (zones), nearSilentZones);
        if (numberOfLayers > 2)
            return createSkipped (SkipReason.TOO_MANY_LAYERS, numberOfLayers, analysis);

        final Optional<List<List<ISampleZone>>> distribution = analysis.distribute ();
        if (distribution.isEmpty ())
            return createSkipped (SkipReason.TOO_MANY_LAYERS, 3, analysis);

        // The two oscillators share the amplitude envelope of the sound
        if (analysis.findDifferentAttacks ())
            return createSkipped (SkipReason.DIFFERENT_ATTACKS, 2, analysis);

        final ISampleZone [] notes1 = selectNoteZones (distribution.get ().get (0));
        final ISampleZone [] notes2 = selectNoteZones (distribution.get ().get (1));
        ISampleZone silence = null;
        for (int note = 0; note < NUMBER_OF_NOTES; note++)
        {
            final boolean hasZone1 = notes1[note] != null;
            if (hasZone1 == (notes2[note] != null))
                continue;
            if (silence == null)
                silence = createSilence (zones);
            if (hasZone1)
                notes2[note] = silence;
            else
                notes1[note] = silence;
        }
        return new DelugeOscillatorLayout (createRanges (notes1, silence), createRanges (notes2, silence), 2, -1, -1, nearSilentZones, SkipReason.NONE);
    }


    /**
     * Get the near-silent zones which are left out.
     *
     * @return The zones, empty if none
     */
    List<ISampleZone> getNearSilentZones ()
    {
        return this.nearSilentZones;
    }


    /**
     * Get why the multi-sample does not fit onto the two oscillators: more layers sound together
     * on a note than the two oscillators can play, or the attack times of two layers which sound
     * together are more than 2 seconds apart, while the oscillators share one amplitude envelope.
     * Such a layout has no oscillators.
     *
     * @return The reason, NONE if it fits
     */
    SkipReason getSkipReason ()
    {
        return this.skipReason;
    }


    /**
     * Get the largest number of layers which sound together on a note.
     *
     * @return The number of layers, 1 if only the first oscillator plays samples
     */
    int getNumberOfLayers ()
    {
        return this.numberOfLayers;
    }


    /**
     * Get the lowest note on which the multi-sample does not fit onto the two oscillators: of the
     * first run of notes on which the most layers sound together, or of the notes on which two
     * layers with attack times which are too far apart sound together.
     *
     * @return The note, -1 if the multi-sample fits
     */
    int getCrowdedNoteLow ()
    {
        return this.crowdedNoteLow;
    }


    /**
     * Get the highest note on which the multi-sample does not fit onto the two oscillators, see
     * {@link #getCrowdedNoteLow()}.
     *
     * @return The note, -1 if the multi-sample fits
     */
    int getCrowdedNoteHigh ()
    {
        return this.crowdedNoteHigh;
    }


    /**
     * Get the key map of the first oscillator.
     *
     * @return The sample ranges, ordered by their top note
     */
    List<SampleRange> getOscillator1 ()
    {
        return this.oscillator1;
    }


    /**
     * Get the key map of the second oscillator.
     *
     * @return The sample ranges, ordered by their top note, empty if the second oscillator does not
     *         play samples
     */
    List<SampleRange> getOscillator2 ()
    {
        return this.oscillator2;
    }


    /**
     * Get the zone whose envelopes and filter the sound uses, which the two oscillators share: the
     * first zone which the first oscillator plays.
     *
     * @return The zone
     */
    ISampleZone getMainZone ()
    {
        for (final SampleRange range: this.oscillator1)
            if (!range.isSilent ())
                return range.getZone ();
        return this.oscillator1.get (0).getZone ();
    }


    /**
     * Get all zones which the oscillators play, each one once, including the silent sample.
     *
     * @return The zones in the order of the key maps of the first and the second oscillator
     */
    List<ISampleZone> getZones ()
    {
        final List<SampleRange> ranges = new ArrayList<> (this.oscillator1);
        ranges.addAll (this.oscillator2);
        return getDistinctZones (ranges, true);
    }


    /**
     * Calculate the average gain of the zones of a key map, without the silent sample.
     *
     * @param ranges The ranges of the key map
     * @return The average gain in dB
     */
    static double getAverageGain (final List<SampleRange> ranges)
    {
        final List<ISampleZone> zones = getDistinctZones (ranges, false);
        double sum = 0;
        for (final ISampleZone zone: zones)
            sum += zone.getGain ();
        return zones.isEmpty () ? 0 : sum / zones.size ();
    }


    /**
     * Get the zones of a key map, each one once.
     *
     * @param ranges The ranges of the key map
     * @param includeSilence True to include the silent sample
     * @return The zones in the order of the ranges
     */
    private static List<ISampleZone> getDistinctZones (final List<SampleRange> ranges, final boolean includeSilence)
    {
        final Set<ISampleZone> zones = Collections.newSetFromMap (new IdentityHashMap<> ());
        final List<ISampleZone> result = new ArrayList<> ();
        for (final SampleRange range: ranges)
            if ((includeSilence || !range.isSilent ()) && zones.add (range.getZone ()))
                result.add (range.getZone ());
        return result;
    }


    /**
     * Select the zone which an oscillator plays on each note: the one with the highest velocity
     * range of those which are mapped to the note, and if several have the same, the one whose key
     * range ends first - like in the key map of a single oscillator, where each zone plays the
     * notes up to its upper key. This also trims an overlapping copy of a zone.
     *
     * @param zones The zones of the oscillator
     * @return The zone of each note, null on notes to which no zone is mapped
     */
    private static ISampleZone [] selectNoteZones (final List<ISampleZone> zones)
    {
        final ISampleZone [] noteZones = new ISampleZone [NUMBER_OF_NOTES];
        for (final ISampleZone zone: zones)
        {
            final int keyLow = Math.clamp (getKeyLow (zone), 0, NUMBER_OF_NOTES - 1);
            final int keyHigh = Math.clamp (getKeyHigh (zone), 0, NUMBER_OF_NOTES - 1);
            for (int note = keyLow; note <= keyHigh; note++)
                if (noteZones[note] == null || isPreferred (zone, noteZones[note]))
                    noteZones[note] = zone;
        }
        return noteZones;
    }


    /**
     * Test if a zone is preferred over another zone which is mapped to the same note: the one with
     * the higher velocity range, and if both have the same, the one whose key range ends first.
     *
     * @param candidate The zone to test
     * @param current The zone which plays the note so far
     * @return True if the candidate is preferred
     */
    private static boolean isPreferred (final ISampleZone candidate, final ISampleZone current)
    {
        final int candidateHigh = getVelocityHigh (candidate);
        final int currentHigh = getVelocityHigh (current);
        if (candidateHigh != currentHigh)
            return candidateHigh > currentHigh;
        final int candidateLow = getVelocityLow (candidate);
        final int currentLow = getVelocityLow (current);
        if (candidateLow != currentLow)
            return candidateLow > currentLow;
        return getKeyHigh (candidate) < getKeyHigh (current);
    }


    /**
     * Merge neighboring notes which play the same zone into one range. Since a Deluge key map has
     * no gaps, a note which plays nothing is added to the range above it, and the highest range
     * plays all notes above it.
     *
     * @param noteZones The zone of each note, null if nothing is mapped to the note
     * @param silence The silent sample, null if there is none
     * @return The sample ranges, ordered by their top note
     */
    private static List<SampleRange> createRanges (final ISampleZone [] noteZones, final ISampleZone silence)
    {
        final List<SampleRange> ranges = new ArrayList<> ();
        ISampleZone current = null;
        int topNote = -1;
        for (int note = 0; note < NUMBER_OF_NOTES; note++)
        {
            final ISampleZone zone = noteZones[note];
            if (zone == null)
                continue;
            if (zone != current)
            {
                if (current != null)
                    ranges.add (new SampleRange (current, topNote, current == silence));
                current = zone;
            }
            topNote = note;
        }
        if (current != null)
            ranges.add (new SampleRange (current, topNote, current == silence));
        return ranges;
    }


    /**
     * Create the silent sample which an oscillator plays on the notes on which only the other
     * oscillator plays a layer. It has the sample rate of the first zone which has sample data.
     *
     * @param zones The zones of the multi-sample
     * @return The zone of the silent sample
     */
    private static ISampleZone createSilence (final List<ISampleZone> zones)
    {
        int sampleRate = DEFAULT_SAMPLE_RATE;
        for (final ISampleZone zone: zones)
        {
            final Optional<ISampleData> sampleData = zone.getSampleData ();
            if (sampleData.isEmpty ())
                continue;
            try
            {
                sampleRate = sampleData.get ().getAudioMetadata ().getSampleRate ();
                break;
            }
            catch (final IOException _)
            {
                // Try the next zone
            }
        }
        final int frames = (int) Math.round (sampleRate * SILENCE_SECONDS);
        final ISampleZone silence = new DefaultSampleZone (SILENCE_NAME, new InMemorySampleData (new DefaultAudioMetadata (1, sampleRate, 16, frames), new short [frames]));
        silence.setKeyRoot (DelugeValues.REFERENCE_NOTE);
        return silence;
    }


    private static int getKeyLow (final ISampleZone zone)
    {
        return AbstractCreator.limitToDefault (zone.getKeyLow (), 0);
    }


    private static int getKeyHigh (final ISampleZone zone)
    {
        return AbstractCreator.limitToDefault (zone.getKeyHigh (), 127);
    }


    private static int getVelocityLow (final ISampleZone zone)
    {
        return AbstractCreator.limitToDefault (zone.getVelocityLow (), 1);
    }


    private static int getVelocityHigh (final ISampleZone zone)
    {
        return AbstractCreator.limitToDefault (zone.getVelocityHigh (), 127);
    }


    /**
     * Finds out which zones of a multi-sample sound together on the same note, how many layers
     * sound together at most and how the zones can be distributed onto two oscillators.
     */
    private static final class LayerAnalysis
    {
        private final List<ISampleZone>         zones;
        private final int                       size;
        private final int []                    keyLow;
        private final int []                    keyHigh;
        private final int []                    velocityLow;
        private final int []                    velocityHigh;
        /** The key map of each zone, see {@link #findKeyMaps()}. */
        private final int []                    keyMap;
        /** The zones which are copies of each other share a unit, see {@link #findUnit(int)}. */
        private final int []                    unit;
        private final boolean [] []             together;
        private final Map<ISampleData, byte []> digests         = new IdentityHashMap<> ();
        private int                             crowdedNoteLow  = -1;
        private int                             crowdedNoteHigh = -1;


        /**
         * Constructor.
         *
         * @param zones The zones to analyze
         */
        LayerAnalysis (final List<ISampleZone> zones)
        {
            this.zones = zones;
            this.size = zones.size ();
            this.keyLow = new int [this.size];
            this.keyHigh = new int [this.size];
            this.velocityLow = new int [this.size];
            this.velocityHigh = new int [this.size];
            for (int i = 0; i < this.size; i++)
            {
                // A cross-fade only blends a zone with its neighbor, which is not a layer, so
                // only the part of a range outside of its cross-fades counts
                final ISampleZone zone = zones.get (i);
                this.keyLow[i] = getKeyLow (zone) + Math.max (0, zone.getNoteCrossfadeLow ());
                this.keyHigh[i] = getKeyHigh (zone) - Math.max (0, zone.getNoteCrossfadeHigh ());
                this.velocityLow[i] = getVelocityLow (zone) + Math.max (0, zone.getVelocityCrossfadeLow ());
                this.velocityHigh[i] = getVelocityHigh (zone) - Math.max (0, zone.getVelocityCrossfadeHigh ());
            }

            this.keyMap = this.findKeyMaps ();

            this.unit = new int [this.size];
            for (int i = 0; i < this.size; i++)
                this.unit[i] = i;
            for (int i = 0; i < this.size; i++)
                for (int j = i + 1; j < this.size; j++)
                    if (this.overlap (i, j) && this.isCopy (zones.get (i), zones.get (j)))
                        this.unit[this.findUnit (j)] = this.findUnit (i);

            this.together = new boolean [this.size] [this.size];
            for (int i = 0; i < this.size; i++)
                for (int j = i + 1; j < this.size; j++)
                {
                    final boolean isTogether = this.soundTogether (i, j);
                    this.together[i][j] = isTogether;
                    this.together[j][i] = isTogether;
                }
        }


        /**
         * Count the largest number of layers which sound together on one note and remember the
         * first run of notes on which that many sound together.
         *
         * @return The number of layers
         */
        int countLayers ()
        {
            int maximum = 0;
            boolean inRun = false;
            for (int note = 0; note < NUMBER_OF_NOTES; note++)
            {
                final int count = this.countLayers (note);
                if (count > maximum)
                {
                    maximum = count;
                    this.crowdedNoteLow = note;
                    this.crowdedNoteHigh = note;
                    inRun = true;
                }
                else if (count == maximum && inRun)
                    this.crowdedNoteHigh = note;
                else
                    inRun = false;
            }
            return maximum;
        }


        /**
         * Count the largest number of layers which sound together on a note at any velocity.
         *
         * @param note The note
         * @return The number of layers
         */
        private int countLayers (final int note)
        {
            final List<Integer> noteZones = new ArrayList<> ();
            final Set<Integer> velocities = new TreeSet<> ();
            for (int i = 0; i < this.size; i++)
                if (this.keyLow[i] <= note && note <= this.keyHigh[i])
                {
                    noteZones.add (Integer.valueOf (i));
                    velocities.add (Integer.valueOf (this.velocityLow[i]));
                }

            // The most zones sound together where the velocity range of a zone starts
            int maximum = 0;
            for (final Integer velocity: velocities)
            {
                final List<Integer> candidates = new ArrayList<> ();
                for (final Integer index: noteZones)
                {
                    final int i = index.intValue ();
                    if (this.velocityLow[i] <= velocity.intValue () && velocity.intValue () <= this.velocityHigh[i])
                        candidates.add (index);
                }
                maximum = Math.max (maximum, candidates.size () <= MAX_EXACT_CANDIDATES ? this.findLargestGroup (candidates) : this.findLargeGroup (candidates));
            }
            return maximum;
        }


        /**
         * Find the size of the largest group of zones which all sound together.
         *
         * @param candidates The indices of the zones
         * @return The size of the group
         */
        private int findLargestGroup (final List<Integer> candidates)
        {
            if (candidates.isEmpty ())
                return 0;
            final int first = candidates.get (0).intValue ();
            final List<Integer> rest = candidates.subList (1, candidates.size ());
            final List<Integer> partners = new ArrayList<> ();
            for (final Integer other: rest)
                if (this.together[first][other.intValue ()])
                    partners.add (other);
            final int withFirst = 1 + this.findLargestGroup (partners);
            if (withFirst > rest.size ())
                return withFirst;
            return Math.max (withFirst, this.findLargestGroup (rest));
        }


        /**
         * Find the size of a large group of zones which all sound together, fast but not
         * necessarily the largest one: starting from each zone, add every zone which sounds
         * together with all zones added so far. A count which is too low is caught when the zones
         * are distributed onto the two oscillators.
         *
         * @param candidates The indices of the zones
         * @return The size of the group
         */
        private int findLargeGroup (final List<Integer> candidates)
        {
            int maximum = 0;
            for (final Integer start: candidates)
            {
                final List<Integer> group = new ArrayList<> ();
                group.add (start);
                for (final Integer other: candidates)
                    if (other.intValue () != start.intValue () && group.stream ().allMatch (member -> this.together[member.intValue ()][other.intValue ()]))
                        group.add (other);
                maximum = Math.max (maximum, group.size ());
            }
            return maximum;
        }


        /**
         * Distribute the zones onto the two oscillators so that zones which sound together play on
         * different oscillators, while the copies of a zone stay together. Of the zones which are
         * connected through the zones they sound together with, the part which is heard more - by
         * the number of notes its zones cover times their amplitude - plays on the first
         * oscillator, like zones which do not sound together with any other zone.
         *
         * @return The zones of the first and of the second oscillator, empty if they cannot be
         *         distributed onto two oscillators
         */
        Optional<List<List<ISampleZone>>> distribute ()
        {
            final Map<Integer, List<Integer>> units = new LinkedHashMap<> ();
            for (int i = 0; i < this.size; i++)
                units.computeIfAbsent (Integer.valueOf (this.findUnit (i)), _ -> new ArrayList<> ()).add (Integer.valueOf (i));

            final Map<Integer, Set<Integer>> partners = new LinkedHashMap<> ();
            for (final Integer unitIndex: units.keySet ())
                partners.put (unitIndex, new TreeSet<> ());
            for (int i = 0; i < this.size; i++)
                for (int j = i + 1; j < this.size; j++)
                    if (this.together[i][j])
                    {
                        final Integer unitI = Integer.valueOf (this.findUnit (i));
                        final Integer unitJ = Integer.valueOf (this.findUnit (j));
                        partners.get (unitI).add (unitJ);
                        partners.get (unitJ).add (unitI);
                    }

            final Map<Integer, Integer> colors = new LinkedHashMap<> ();
            for (final Integer start: units.keySet ())
            {
                if (colors.containsKey (start))
                    continue;

                // The connected units alternate between the two oscillators
                final List<Integer> component = new ArrayList<> ();
                final Deque<Integer> queue = new ArrayDeque<> ();
                colors.put (start, Integer.valueOf (0));
                queue.add (start);
                while (!queue.isEmpty ())
                {
                    final Integer current = queue.poll ();
                    component.add (current);
                    final int color = colors.get (current).intValue ();
                    for (final Integer partner: partners.get (current))
                    {
                        final Integer partnerColor = colors.get (partner);
                        if (partnerColor == null)
                        {
                            colors.put (partner, Integer.valueOf (1 - color));
                            queue.add (partner);
                        }
                        else if (partnerColor.intValue () == color)
                        {
                            this.rememberConflict (units.get (current), units.get (partner));
                            return Optional.empty ();
                        }
                    }
                }

                // The part which is heard more plays on the first oscillator
                final double [] audibility = new double [2];
                for (final Integer unitIndex: component)
                    for (final Integer index: units.get (unitIndex))
                        audibility[colors.get (unitIndex).intValue ()] += getAudibility (this.zones.get (index.intValue ()));
                if (audibility[1] > audibility[0])
                    for (final Integer unitIndex: component)
                        colors.put (unitIndex, Integer.valueOf (1 - colors.get (unitIndex).intValue ()));
            }

            final List<ISampleZone> oscillator1 = new ArrayList<> ();
            final List<ISampleZone> oscillator2 = new ArrayList<> ();
            for (int i = 0; i < this.size; i++)
            {
                final ISampleZone zone = this.zones.get (i);
                if (colors.get (Integer.valueOf (this.findUnit (i))).intValue () == 0)
                    oscillator1.add (zone);
                else
                    oscillator2.add (zone);
            }
            final List<List<ISampleZone>> result = new ArrayList<> (2);
            result.add (oscillator1);
            result.add (oscillator2);
            return Optional.of (result);
        }


        /**
         * Remember the notes on which two zones sound together which cannot be distributed onto
         * two oscillators together with the zones they sound with.
         *
         * @param unit1 The indices of the zones of the first unit
         * @param unit2 The indices of the zones of the second unit
         */
        private void rememberConflict (final List<Integer> unit1, final List<Integer> unit2)
        {
            for (final Integer index1: unit1)
                for (final Integer index2: unit2)
                {
                    final int i = index1.intValue ();
                    final int j = index2.intValue ();
                    if (this.together[i][j])
                    {
                        this.crowdedNoteLow = Math.max (this.keyLow[i], this.keyLow[j]);
                        this.crowdedNoteHigh = Math.min (this.keyHigh[i], this.keyHigh[j]);
                        return;
                    }
                }
        }


        /**
         * Check if two zones which sound together - and therefore play on the two oscillators -
         * have attack times which are so far apart that the shared amplitude envelope of the
         * oscillators cannot play them: a layer which fades in over seconds would start with the
         * note, or a layer which starts with the note would fade in. A time which is not set is 0,
         * like it is written. The notes on which such zones sound together are remembered.
         * Different decay, sustain or release settings only change how long the second layer
         * plays, and different filters how bright it is, so they do not count.
         *
         * @return True if two such zones sound together
         */
        boolean findDifferentAttacks ()
        {
            int noteLow = Integer.MAX_VALUE;
            int noteHigh = -1;
            for (int i = 0; i < this.size; i++)
                for (int j = i + 1; j < this.size; j++)
                {
                    if (!this.together[i][j])
                        continue;
                    final IEnvelope envelopeA = this.zones.get (i).getAmplitudeEnvelopeModulator ().getSource ();
                    final IEnvelope envelopeB = this.zones.get (j).getAmplitudeEnvelopeModulator ().getSource ();
                    if (Math.abs (Math.max (0, envelopeA.getAttackTime ()) - Math.max (0, envelopeB.getAttackTime ())) <= MAX_ATTACK_GAP)
                        continue;
                    noteLow = Math.min (noteLow, Math.max (this.keyLow[i], this.keyLow[j]));
                    noteHigh = Math.max (noteHigh, Math.min (this.keyHigh[i], this.keyHigh[j]));
                }
            if (noteHigh < 0)
                return false;
            this.crowdedNoteLow = noteLow;
            this.crowdedNoteHigh = noteHigh;
            return true;
        }


        /**
         * Test if two zones sound together on the same note: their key and velocity ranges
         * overlap, they are not copies of each other, not two neighbors of one key map, they do not
         * alternate (round-robin or random) and their velocity ranges do not follow each other,
         * which makes them velocity layers.
         *
         * @param i The index of the first zone
         * @param j The index of the second zone
         * @return True if they sound together
         */
        private boolean soundTogether (final int i, final int j)
        {
            if (!this.overlap (i, j) || this.findUnit (i) == this.findUnit (j) || this.keyMap[i] == this.keyMap[j])
                return false;
            if (alternate (this.zones.get (i), this.zones.get (j)))
                return false;
            final boolean follow = this.velocityLow[i] < this.velocityLow[j] && this.velocityHigh[i] < this.velocityHigh[j] || this.velocityLow[j] < this.velocityLow[i] && this.velocityHigh[j] < this.velocityHigh[i];
            return !follow;
        }


        /**
         * Test if the key and the velocity ranges of two zones overlap, without their
         * cross-fades.
         *
         * @param i The index of the first zone
         * @param j The index of the second zone
         * @return True if they overlap
         */
        private boolean overlap (final int i, final int j)
        {
            return this.keyLow[i] <= this.keyHigh[j] && this.keyLow[j] <= this.keyHigh[i] && this.velocityLow[i] <= this.velocityHigh[j] && this.velocityLow[j] <= this.velocityHigh[i];
        }


        /**
         * Find the key maps: the zones with the same velocity range whose key ranges follow each
         * other up the keyboard. Sources sometimes give the zones of a key map ranges which overlap
         * a little, e.g. the lowest sample is mapped up to the key 54 while the next one starts at
         * 43, which are not two layers. A zone is added to the key map of its velocity range which
         * it overlaps least - preferably not at all - among those whose zones it overlaps by less
         * than half of the smaller of the two key ranges; a zone which overlaps more sounds
         * together with the other one, and if there is no such key map the zone starts another
         * one. The zones are taken from the lowest key up, of two which start on the same key the
         * wider one first, so that a layer which spans the whole keyboard does not scatter the
         * zones of another one.
         *
         * @return The index of the key map of each zone
         */
        private int [] findKeyMaps ()
        {
            final Map<String, List<Integer>> velocityRanges = new LinkedHashMap<> ();
            for (int i = 0; i < this.size; i++)
            {
                final ISampleZone zone = this.zones.get (i);
                velocityRanges.computeIfAbsent (getVelocityLow (zone) + "-" + getVelocityHigh (zone), _ -> new ArrayList<> ()).add (Integer.valueOf (i));
            }

            final int [] keyMaps = new int [this.size];
            Arrays.fill (keyMaps, -1);
            int keyMapCount = 0;
            for (final List<Integer> indices: velocityRanges.values ())
            {
                indices.sort (Comparator.comparingInt ((final Integer index) -> this.keyLow[index.intValue ()]).thenComparing (Comparator.comparingInt ((final Integer index) -> this.keyHigh[index.intValue ()]).reversed ()));
                final List<List<Integer>> rangeKeyMaps = new ArrayList<> ();
                for (final Integer index: indices)
                {
                    List<Integer> target = null;
                    int targetOverlap = Integer.MAX_VALUE;
                    for (final List<Integer> candidate: rangeKeyMaps)
                    {
                        if (candidate.stream ().anyMatch (member -> this.overlapsMuch (member.intValue (), index.intValue ())))
                            continue;
                        int overlap = 0;
                        for (final Integer member: candidate)
                            overlap += this.countOverlappingKeys (member.intValue (), index.intValue ());
                        if (overlap < targetOverlap)
                        {
                            target = candidate;
                            targetOverlap = overlap;
                        }
                    }
                    if (target == null)
                    {
                        target = new ArrayList<> ();
                        rangeKeyMaps.add (target);
                    }
                    target.add (index);
                }
                for (final List<Integer> rangeKeyMap: rangeKeyMaps)
                {
                    for (final Integer index: rangeKeyMap)
                        keyMaps[index.intValue ()] = keyMapCount;
                    keyMapCount++;
                }
            }
            return keyMaps;
        }


        /**
         * Test if the key ranges of two zones overlap by half of the smaller one or more.
         *
         * @param i The index of the first zone
         * @param j The index of the second zone
         * @return True if they overlap that much
         */
        private boolean overlapsMuch (final int i, final int j)
        {
            final int overlap = this.countOverlappingKeys (i, j);
            if (overlap == 0)
                return false;
            final int smaller = Math.min (this.keyHigh[i] - this.keyLow[i], this.keyHigh[j] - this.keyLow[j]) + 1;
            return 2 * overlap >= smaller;
        }


        /**
         * Count the keys on which the key ranges of two zones overlap.
         *
         * @param i The index of the first zone
         * @param j The index of the second zone
         * @return The number of keys, 0 if they do not overlap
         */
        private int countOverlappingKeys (final int i, final int j)
        {
            return Math.max (0, Math.min (this.keyHigh[i], this.keyHigh[j]) - Math.max (this.keyLow[i], this.keyLow[j]) + 1);
        }


        /**
         * Find the unit of a zone: the first of the zones which are copies of each other.
         *
         * @param index The index of the zone
         * @return The index of the unit
         */
        private int findUnit (final int index)
        {
            int root = index;
            while (this.unit[root] != root)
                root = this.unit[root];
            return root;
        }


        /**
         * Test if a zone is an overlapping copy of another zone, which is a mapping error rather
         * than a layer: both play the same sample with the same root note, tuning and panning, the
         * same play range and the same loops.
         *
         * @param a The first zone
         * @param b The second zone
         * @return True if they are copies
         */
        private boolean isCopy (final ISampleZone a, final ISampleZone b)
        {
            if (a.getKeyRoot () != b.getKeyRoot () || Math.abs (a.getTuning () - b.getTuning ()) > TOLERANCE || Math.abs (a.getPanning () - b.getPanning ()) > TOLERANCE)
                return false;
            if (a.getStart () != b.getStart () || a.getStop () != b.getStop () || a.isReversed () != b.isReversed () || !haveSameLoops (a, b))
                return false;
            return this.isSameSample (a, b);
        }


        /**
         * Test if two zones play the same sample: the same sample data or sample data with the same
         * format and the same content.
         *
         * @param a The first zone
         * @param b The second zone
         * @return True if they play the same sample
         */
        private boolean isSameSample (final ISampleZone a, final ISampleZone b)
        {
            final Optional<ISampleData> dataA = a.getSampleData ();
            final Optional<ISampleData> dataB = b.getSampleData ();
            if (dataA.isEmpty () || dataB.isEmpty ())
                return false;
            if (dataA.get () == dataB.get ())
                return true;
            try
            {
                final IAudioMetadata metadataA = dataA.get ().getAudioMetadata ();
                final IAudioMetadata metadataB = dataB.get ().getAudioMetadata ();
                if (metadataA.getChannels () != metadataB.getChannels () || metadataA.getSampleRate () != metadataB.getSampleRate () || metadataA.getBitResolution () != metadataB.getBitResolution () || metadataA.getNumberOfSamples () != metadataB.getNumberOfSamples ())
                    return false;
                return MessageDigest.isEqual (this.getDigest (dataA.get ()), this.getDigest (dataB.get ()));
            }
            catch (final IOException _)
            {
                return false;
            }
        }


        /**
         * Get the digest of the content of sample data.
         *
         * @param sampleData The sample data
         * @return The digest
         * @throws IOException Could not read the sample data
         */
        private byte [] getDigest (final ISampleData sampleData) throws IOException
        {
            byte [] digest = this.digests.get (sampleData);
            if (digest != null)
                return digest;
            try
            {
                final MessageDigest messageDigest = MessageDigest.getInstance ("SHA-256");
                try (final OutputStream out = new DigestOutputStream (OutputStream.nullOutputStream (), messageDigest))
                {
                    sampleData.writeSample (out);
                }
                digest = messageDigest.digest ();
            }
            catch (final NoSuchAlgorithmException ex)
            {
                throw new IOException (ex);
            }
            this.digests.put (sampleData, digest);
            return digest;
        }


        /**
         * Test if two zones have the same loops.
         *
         * @param a The first zone
         * @param b The second zone
         * @return True if all loops have the same type, start, end and cross-fade
         */
        private static boolean haveSameLoops (final ISampleZone a, final ISampleZone b)
        {
            final List<ISampleLoop> loopsA = a.getLoops ();
            final List<ISampleLoop> loopsB = b.getLoops ();
            if (loopsA.size () != loopsB.size ())
                return false;
            for (int i = 0; i < loopsA.size (); i++)
            {
                final ISampleLoop loopA = loopsA.get (i);
                final ISampleLoop loopB = loopsB.get (i);
                if (loopA.getType () != loopB.getType () || loopA.getStart () != loopB.getStart () || loopA.getEnd () != loopB.getEnd () || Math.abs (loopA.getCrossfade () - loopB.getCrossfade ()) > TOLERANCE)
                    return false;
            }
            return true;
        }


        /**
         * Test if two zones alternate (round-robin or random) instead of sounding together. Zones
         * which carry the same position in their sequence belong to two alternating sets which
         * sound together.
         *
         * @param a The first zone
         * @param b The second zone
         * @return True if they alternate
         */
        private static boolean alternate (final ISampleZone a, final ISampleZone b)
        {
            if (a.getPlayLogic () == PlayLogic.ALWAYS || b.getPlayLogic () == PlayLogic.ALWAYS)
                return false;
            final int positionA = a.getSequencePosition ();
            final int positionB = b.getSequencePosition ();
            return positionA < 1 || positionB < 1 || positionA != positionB;
        }


        /**
         * Estimate how much a zone is heard: the number of notes it covers times its amplitude.
         *
         * @param zone The zone
         * @return The audibility
         */
        private static double getAudibility (final ISampleZone zone)
        {
            return (getKeyHigh (zone) - getKeyLow (zone) + 1) * Math.pow (10.0, zone.getGain () / 20.0);
        }
    }
}
