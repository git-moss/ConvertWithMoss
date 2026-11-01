// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;


/**
 * Measures how far the recordings of a multi-sample sound away from the root keys they are mapped
 * to. Sample libraries are often built from an instrument which was itself transposed - an
 * oscillator set to another footage, a frequency ratio of one half or two, a transposed patch - so
 * that the recording of the key C3 sounds one or two octaves higher or lower. The mapping is
 * faithful, the source plays exactly the same way, but on the destination device the preset is then
 * played in a different part of the keyboard than the player expects.
 * <p>
 * The pitch of a recording is the highest fundamental whose harmonics hold most of the power of the
 * spectrum. That rule cannot fall for an octave too low - such a candidate explains the same
 * partials but predicts harmonics between them which are not there - and it only reports an octave
 * too high if the odd harmonics of the sound are practically absent, in which case that is the
 * pitch which is heard; the noise in a sample does not change that decision. Material which is not
 * clearly pitched - drums, noise, sound effects - does not reach the required coverage and is not
 * measured at all, and a multi-sample only gets a result if its zones agree with each other, which
 * sorts out phrase and multi-instrument kits whose zones are each rooted differently.
 * <p>
 * A chord is the exception: its notes are harmonic series of their own, and the only fundamental
 * which explains all of them lies below its lowest note - an octave below a fifth, two octaves
 * below a major triad - and does not sound itself. A chord of two or three notes is recognised by
 * the harmonics of that fundamental which belong to none of its notes and stay empty, and it is
 * measured at its root, its note at the pitch class of the fundamental; a chord whose root cannot
 * be told is not measured.
 * <p>
 * A multi-sample of only one or two zones - typically one recording stretched across the keyboard -
 * has no majority which could outvote a misreading. It only gets a result if all of its zones are
 * measured and sound a whole number of octaves away from their root keys: a root key set in another
 * octave than the one of the recording is the common case, and moving it by octaves keeps the keys
 * in tune, while a chord or an inharmonic sound can produce any other interval.
 *
 * @author Jürgen Moßgraber
 */
public final class PitchDetector
{
    /**
     * Zones are measured spread over the multi-sample, so that one velocity layer or one corner of
     * the keyboard cannot decide the result alone.
     */
    private static final int    MAXIMUM_MEASURED_ZONES = 12;
    /**
     * At least this many zones must be measurable, otherwise a single odd sample could decide. A
     * multi-sample which has fewer zones must have all of them measured and is only trusted with a
     * whole number of octaves.
     */
    private static final int    MINIMUM_MEASURED_ZONES = 3;
    /** This part of the measured zones must agree with the median offset. */
    private static final double MINIMUM_AGREEMENT      = 0.8;
    /** Offsets which lie within this many semitones of the median count as agreeing. */
    private static final double AGREEMENT_RANGE        = 0.5;
    /** The harmonics of the fundamental must hold this part of the power of the analysed band. */
    private static final double MINIMUM_COVERAGE       = 0.85;
    /** Half the width of the window around a harmonic, in cents. */
    private static final double TOLERANCE_CENTS        = 30;
    /** The longest analysed part of a sample, in seconds. */
    private static final double MAXIMUM_SEGMENT        = 1.0;
    /** The shortest usable loop, in seconds. */
    private static final double MINIMUM_LOOP           = 0.05;
    /** The shortest analysed part of a sample, in frames. */
    private static final int    MINIMUM_SEGMENT_FRAMES = 2048;
    /**
     * Frequencies below this are not considered as a fundamental - mains hum and rumble live there.
     */
    private static final double MINIMUM_FREQUENCY      = 16;
    /** The spectrum is analysed up to this part of the sample rate. */
    private static final double BAND_LIMIT             = 0.45;
    /** Peaks more than this many decibel below the strongest one do not start a candidate. */
    private static final double PEAK_RANGE_DB          = 60;
    /** The number of peaks from which the candidates are built. */
    private static final int    CANDIDATE_PEAKS        = 10;
    /** Each peak may be the harmonic 1 to this number of the fundamental. */
    private static final int    CANDIDATE_DIVISORS     = 12;
    /** Candidates which lie this close together are the same one. */
    private static final double CANDIDATE_DISTANCE     = 5;
    /** The harmonics over which the found fundamental is measured exactly. */
    private static final int    REFINED_HARMONICS      = 8;
    /**
     * The harmonics of a fundamental which are examined for its octave and for the notes of a
     * chord.
     */
    private static final int    EXAMINED_HARMONICS     = 24;
    /**
     * The octave above a fundamental is the pitch if the odd harmonics, which it does not explain,
     * hold less than this part of the power on the harmonics.
     */
    private static final double MAXIMUM_ODD_HARMONICS  = 0.05;
    /** Chords of up to this many notes are recognised. */
    private static final int    MAXIMUM_CHORD_NOTES    = 3;
    /**
     * A harmonic is missing if it holds less than this part of the power of its neighbours - in
     * a chord only noise is left there.
     */
    private static final double MISSING_HARMONIC       = 0.03;
    /** A harmonic needs this part of the power of all harmonics to start the series of a note. */
    private static final double NOTE_HARMONIC          = 0.02;
    /**
     * Harmonics above the last one which holds this part of the power of all harmonics are only
     * noise.
     */
    private static final double AUDIBLE_HARMONIC       = 0.001;
    /** A chord leaves at least this many harmonics empty besides the fundamental. */
    private static final int    MINIMUM_CHORD_GAPS     = 2;
    /** The analysis window is padded to a power of two of at least this size. */
    private static final int    MINIMUM_FFT_SIZE       = 4096;
    /** ... and of at most this size, which keeps the cost of a long segment in hand. */
    private static final int    MAXIMUM_FFT_SIZE       = 1 << 17;
    /** The frequency of the note A4, from which all other notes are calculated. */
    private static final double CONCERT_PITCH          = 440;
    /** The MIDI note number of A4. */
    private static final int    CONCERT_PITCH_NOTE     = 69;


    /**
     * The result of a measurement over one multi-sample.
     *
     * @param semitones How many semitones the samples sound above (positive) or below (negative)
     *            their root keys
     * @param agreeingZones The number of measured zones which agree with this offset
     * @param measuredZones The number of zones which could be measured
     */
    public record Result (double semitones, int agreeingZones, int measuredZones)
    {
        // Intentionally empty
    }


    /**
     * Private due to helper class.
     */
    private PitchDetector ()
    {
        // Intentionally empty
    }


    /**
     * Measure how far the samples of the given groups sound away from their root keys. The audio is
     * read for up to twelve zones; zones which cannot be read or are not clearly pitched are
     * skipped, the measurement never fails.
     *
     * @param groups The groups whose zones to measure
     * @return The result, empty if too few zones could be measured or they do not agree, or if a
     *         multi-sample of fewer than three zones does not sound a whole number of octaves away
     */
    public static Optional<Result> detect (final List<IGroup> groups)
    {
        final List<ISampleZone> zones = selectZones (groups);
        final List<Double> offsets = new ArrayList<> ();
        for (final ISampleZone zone: zones)
        {
            final double offset = measure (zone);
            if (!Double.isNaN (offset))
                offsets.add (Double.valueOf (offset));
        }
        final boolean hasFewZones = zones.size () < MINIMUM_MEASURED_ZONES;
        if (offsets.isEmpty () || offsets.size () < (hasFewZones ? zones.size () : MINIMUM_MEASURED_ZONES))
            return Optional.empty ();

        final double median = median (offsets);
        int agreeingZones = 0;
        for (final Double offset: offsets)
            if (Math.abs (offset.doubleValue () - median) <= AGREEMENT_RANGE)
                agreeingZones++;
        if (agreeingZones < MINIMUM_AGREEMENT * offsets.size ())
            return Optional.empty ();
        // Rounded like the transposition which moves the root keys onto the pitch
        if (hasFewZones && Math.round (-median) % 12 != 0)
            return Optional.empty ();
        return Optional.of (new Result (median, agreeingZones, offsets.size ()));
    }


    /**
     * Measure the pitch of the sample of one zone, e.g. to find the root key of a sample whose file
     * does not contain one. Unlike {@link #detect(List)} the measurement does not depend on the
     * root key of the zone.
     *
     * @param zone The zone whose sample to measure, its sample data, start, stop and loops must be
     *            set
     * @param expectedNote The note around which the pitch is expected, e.g. the middle of the key
     *            range of the zone; the search is limited to three octaves around it
     * @return The pitch as a MIDI note including the fraction of a semitone, empty if the sample
     *         could not be read or is not clearly pitched
     */
    public static Optional<Double> measurePitch (final ISampleZone zone, final int expectedNote)
    {
        final double pitch = measureFrequency (zone, noteToFrequency (expectedNote));
        if (Double.isNaN (pitch))
            return Optional.empty ();
        return Optional.of (Double.valueOf (CONCERT_PITCH_NOTE + 12 * MathUtils.log2 (pitch / CONCERT_PITCH)));
    }


    /**
     * Get the zones to measure: those which follow the keyboard, spread over the multi-sample.
     *
     * @param groups The groups whose zones to select from
     * @return The selected zones
     */
    private static List<ISampleZone> selectZones (final List<IGroup> groups)
    {
        final List<ISampleZone> zones = new ArrayList<> ();
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final int keyRoot = zone.getKeyRoot ();
                if (zone.getKeyTracking () != 0 && keyRoot >= 0 && keyRoot < 128 && zone.getSampleData ().isPresent ())
                    zones.add (zone);
            }
        if (zones.size () <= MAXIMUM_MEASURED_ZONES)
            return zones;

        final List<ISampleZone> selection = new ArrayList<> (MAXIMUM_MEASURED_ZONES);
        for (int i = 0; i < MAXIMUM_MEASURED_ZONES; i++)
            selection.add (zones.get ((int) ((long) i * zones.size () / MAXIMUM_MEASURED_ZONES)));
        return selection;
    }


    /**
     * Measure one zone.
     *
     * @param zone The zone to measure
     * @return How many semitones the sample sounds above or below the root key of the zone, not a
     *         number if it could not be measured
     */
    private static double measure (final ISampleZone zone)
    {
        final double notatedFrequency = noteToFrequency (zone.getKeyRoot ());
        final double pitch = measureFrequency (zone, notatedFrequency);
        if (Double.isNaN (pitch))
            return Double.NaN;
        // The tuning of the zone shifts the playback, so it is part of what the root key sounds
        return 12 * MathUtils.log2 (pitch / notatedFrequency) + zone.getTuning ();
    }


    /**
     * Measure the frequency of the sample of one zone.
     *
     * @param zone The zone to measure
     * @param notatedFrequency The frequency around which the pitch is expected, which limits the
     *            search to three octaves around it
     * @return The pitch in Hertz, not a number if it could not be measured
     */
    private static double measureFrequency (final ISampleZone zone, final double notatedFrequency)
    {
        final int [] signal;
        final int sampleRate;
        try
        {
            signal = LoopZeroSnapper.readMonoSignal (zone);
            final Optional<ISampleData> sampleData = zone.getSampleData ();
            if (!sampleData.isPresent ())
                return Double.NaN;
            sampleRate = sampleData.get ().getAudioMetadata ().getSampleRate ();
        }
        catch (final Exception _)
        {
            return Double.NaN;
        }
        if (sampleRate <= 0)
            return Double.NaN;

        // The loop is what the destination plays while a key is held - if it has no clear pitch,
        // e.g. because it is noisy, the loudest part after the attack is measured instead
        final double [] loop = pickLoop (signal, sampleRate, zone);
        double pitch = loop == null ? -1 : estimatePitch (loop, sampleRate, notatedFrequency);
        if (pitch < 0)
        {
            final double [] segment = pickLoudestPart (signal, sampleRate, zone);
            if (segment == null)
                return Double.NaN;
            pitch = estimatePitch (segment, sampleRate, notatedFrequency);
        }
        return pitch <= 0 ? Double.NaN : pitch;
    }


    /**
     * Pick the loop of a sample, which is what the destination plays while a key is held. A loop
     * which is shorter than the analysed part is repeated, exactly as the device repeats it.
     *
     * @param signal The mono signal of the sample
     * @param sampleRate The sample rate
     * @param zone The zone of the sample
     * @return The segment, null if the zone has no loop which can be measured
     */
    private static double [] pickLoop (final int [] signal, final int sampleRate, final ISampleZone zone)
    {
        final int start = Math.clamp (zone.getStart (), 0, signal.length);
        final int stop = getStop (signal, zone, start);
        final int maximumLength = (int) (MAXIMUM_SEGMENT * sampleRate);
        for (final ISampleLoop loop: zone.getLoops ())
        {
            final int loopStart = loop.getStart ();
            final int loopLength = loop.getEnd () - loopStart + 1;
            if (loopStart < start || loopStart + loopLength > stop || loopLength < MINIMUM_LOOP * sampleRate)
                continue;
            final double [] segment = new double [Math.min (maximumLength, Math.max (loopLength, MINIMUM_SEGMENT_FRAMES))];
            for (int i = 0; i < segment.length; i++)
                segment[i] = signal[loopStart + i % loopLength];
            return segment;
        }
        return null;
    }


    /**
     * Pick the loudest part of a sample after the attack.
     *
     * @param signal The mono signal of the sample
     * @param sampleRate The sample rate
     * @param zone The zone of the sample
     * @return The segment, null if the sample is too short
     */
    private static double [] pickLoudestPart (final int [] signal, final int sampleRate, final ISampleZone zone)
    {
        final int start = Math.clamp (zone.getStart (), 0, signal.length);
        final int stop = getStop (signal, zone, start);
        final int maximumLength = (int) (MAXIMUM_SEGMENT * sampleRate);
        final int frameLength = Math.max (1, sampleRate / 50);
        final int frames = (stop - start) / frameLength;
        if (frames < 4)
            return stop - start >= MINIMUM_SEGMENT_FRAMES ? copy (signal, start, Math.min (stop - start, maximumLength)) : null;

        final double [] levels = new double [frames];
        int loudest = 0;
        for (int frame = 0; frame < frames; frame++)
        {
            double sum = 0;
            for (int i = start + frame * frameLength; i < start + (frame + 1) * frameLength; i++)
                sum += (double) signal[i] * signal[i];
            levels[frame] = sum / frameLength;
            if (levels[frame] > levels[loudest])
                loudest = frame;
        }
        // Start after the attack, which is noisy and often not yet at the pitch of the sound, and
        // end where the sound has fallen 40 dB - what follows is the release or silence
        int last = frames - 1;
        while (last > loudest && levels[last] < levels[loudest] / 10000)
            last--;
        final int segmentStart = Math.min (start + (loudest + 3) * frameLength, Math.max (start, stop - MINIMUM_SEGMENT_FRAMES));
        // A sound which is still loud in the last frame lasts until the end, also the rest which is
        // shorter than a frame
        final int segmentStop = Math.min (last == frames - 1 ? stop : Math.min (start + (last + 1) * frameLength, stop), segmentStart + maximumLength);
        return segmentStop - segmentStart >= MINIMUM_SEGMENT_FRAMES ? copy (signal, segmentStart, segmentStop - segmentStart) : null;
    }


    /**
     * Get the end of the play-back of a zone.
     *
     * @param signal The mono signal of the sample
     * @param zone The zone of the sample
     * @param start The start of the play-back
     * @return The frame behind the last one which is played
     */
    private static int getStop (final int [] signal, final ISampleZone zone, final int start)
    {
        // The stop of a zone is the last frame which is played
        return zone.getStop () <= start ? signal.length : Math.min (zone.getStop () + 1, signal.length);
    }


    /**
     * Estimate the pitch of a segment: the highest candidate fundamental whose harmonics hold at
     * least {@link #MINIMUM_COVERAGE} of the power of the analysed band, or an octave of it above
     * if its odd harmonics are practically absent.
     *
     * @param segment The audio to analyse
     * @param sampleRate The sample rate
     * @param notatedFrequency The frequency of the root key, which limits the search to three
     *            octaves around it
     * @return The pitch in Hertz, 0 for a chord whose root cannot be told, -1 if the segment is not
     *         clearly pitched
     */
    private static double estimatePitch (final double [] segment, final int sampleRate, final double notatedFrequency)
    {
        final int fftSize = fftSize (segment.length);
        final double [] power = powerSpectrum (segment, fftSize);
        final double binWidth = (double) sampleRate / fftSize;
        final int firstBin = Math.max (1, (int) Math.ceil (MINIMUM_FREQUENCY / binWidth));
        final int lastBin = Math.min (power.length - 2, (int) (BAND_LIMIT * sampleRate / binWidth));
        if (lastBin - firstBin < 16)
            return -1;

        double total = 0;
        for (int bin = firstBin; bin <= lastBin; bin++)
            total += power[bin];
        if (total <= 0)
            return -1;

        final double highest = Math.min (BAND_LIMIT * sampleRate, notatedFrequency * 8);
        for (final double candidate: candidates (power, binWidth, firstBin, lastBin, notatedFrequency, sampleRate))
            if (coverage (power, binWidth, firstBin, lastBin, total, candidate) >= MINIMUM_COVERAGE)
            {
                // Noise lowers the coverage of the octave above as well, which must not decide the
                // octave: it is the pitch if the odd harmonics, which it does not explain, are
                // practically absent
                double octave = candidate;
                while (2 * octave <= highest && getOddHarmonicsPart (power, binWidth, firstBin, lastBin, octave) < MAXIMUM_ODD_HARMONICS)
                    octave *= 2;
                final double fundamental = refine (power, binWidth, firstBin, lastBin, octave);
                final int root = findChordRoot (power, binWidth, firstBin, lastBin, fundamental);
                return root > 0 ? root * fundamental : 0;
            }
        return -1;
    }


    /**
     * Get the part of the power on the harmonics of a fundamental which lies on its odd harmonics.
     *
     * @param power The power spectrum
     * @param binWidth The frequency of one bin
     * @param firstBin The first bin of the analysed band
     * @param lastBin The last bin of the analysed band
     * @param fundamental The fundamental
     * @return The part in the range of [0..1], 1 if the harmonics carry no power
     */
    private static double getOddHarmonicsPart (final double [] power, final double binWidth, final int firstBin, final int lastBin, final double fundamental)
    {
        final double [] harmonics = getHarmonics (power, binWidth, firstBin, lastBin, fundamental);
        double odd = 0;
        double all = 0;
        for (int harmonic = 1; harmonic < harmonics.length; harmonic++)
        {
            all += harmonics[harmonic];
            if (harmonic % 2 == 1)
                odd += harmonics[harmonic];
        }
        return all > 0 ? odd / all : 1;
    }


    /**
     * Get the power of the first harmonics of a fundamental, as far as they lie in the analysed
     * band.
     *
     * @param power The power spectrum
     * @param binWidth The frequency of one bin
     * @param firstBin The first bin of the analysed band
     * @param lastBin The last bin of the analysed band
     * @param fundamental The fundamental
     * @return The power of each harmonic at the index of its number, followed by an empty one
     */
    private static double [] getHarmonics (final double [] power, final double binWidth, final int firstBin, final int lastBin, final double fundamental)
    {
        final int count = (int) Math.clamp (lastBin * binWidth / fundamental, 0, EXAMINED_HARMONICS);
        final double [] harmonics = new double [count + 2];
        final double toleranceFactor = Math.pow (2, TOLERANCE_CENTS / 1200) - 1;
        for (int bin = firstBin; bin <= lastBin; bin++)
        {
            final double frequency = bin * binWidth;
            final int harmonic = (int) Math.max (1, Math.round (frequency / fundamental));
            if (harmonic > count)
                break;
            if (Math.abs (frequency - harmonic * fundamental) <= Math.min (frequency * toleranceFactor, fundamental / 4))
                harmonics[harmonic] += power[bin];
        }
        return harmonics;
    }


    /**
     * Check if a fundamental is the common fundamental of a chord instead of the pitch of a single
     * note. Each note of a chord is a harmonic series of its own, so the only fundamental which
     * explains all of them lies below the lowest note and does not sound itself, and the harmonics
     * of it which belong to none of the notes stay empty - those of a fifth are the harmonics 2 and
     * 3 of the fundamental, so the harmonics 1, 5 and 7 are missing. A single note whose
     * fundamental is weak or filtered away still has these harmonics, which tells the two apart.
     * The root of the chord is its note at the pitch class of the fundamental, the harmonic 2, 4 or
     * 8 - which it is for a fifth, a fourth, a major third and a major triad in all of its
     * inversions.
     *
     * @param power The power spectrum
     * @param binWidth The frequency of one bin
     * @param firstBin The first bin of the analysed band
     * @param lastBin The last bin of the analysed band
     * @param fundamental The fundamental to check
     * @return 1 if the fundamental is the pitch of a single note, the harmonic of it which is the
     *         root of the chord, or 0 for a chord whose root cannot be told
     */
    private static int findChordRoot (final double [] power, final double binWidth, final int firstBin, final int lastBin, final double fundamental)
    {
        // The power of each harmonic, with an empty one above the last for its neighbour
        final double [] harmonics = getHarmonics (power, binWidth, firstBin, lastBin, fundamental);
        final int count = harmonics.length - 2;
        if (count < 4)
            return 1;
        double total = 0;
        for (int harmonic = 1; harmonic <= count; harmonic++)
            total += harmonics[harmonic];
        double strongest = 0;
        for (int harmonic = 2; harmonic <= count; harmonic++)
            strongest = Math.max (strongest, harmonics[harmonic]);
        // The fundamental sounds, it is a single note
        if (harmonics[1] >= MISSING_HARMONIC * strongest)
            return 1;

        // Above the last harmonic which carries power only noise is left, which tells nothing
        int last = count;
        while (last > 2 && harmonics[last] < AUDIBLE_HARMONIC * total)
            last--;

        // Each harmonic which is not part of the series of a note found so far is either missing or
        // starts the series of another note
        final List<Integer> notes = new ArrayList<> ();
        int missing = 0;
        for (int harmonic = 2; harmonic <= last; harmonic++)
        {
            if (isInSeries (harmonic, notes))
                continue;
            if (harmonics[harmonic] < AUDIBLE_HARMONIC * total || harmonics[harmonic] < MISSING_HARMONIC * (harmonics[harmonic - 1] + harmonics[harmonic + 1]) / 2)
                missing++;
            else if (harmonics[harmonic] >= NOTE_HARMONIC * total)
            {
                notes.add (Integer.valueOf (harmonic));
                // A whole harmonic series is a single note whose fundamental is missing
                if (notes.size () > MAXIMUM_CHORD_NOTES)
                    return 1;
            }
            else
                // A weak harmonic which belongs to none of the notes is part of a single note
                return 1;
        }
        if (notes.size () < 2 || missing < MINIMUM_CHORD_GAPS)
            return 1;
        for (final Integer note: notes)
            if (Integer.bitCount (note.intValue ()) == 1)
                return note.intValue ();
        return 0;
    }


    /**
     * Check if a harmonic is part of the harmonic series of one of the given notes.
     *
     * @param harmonic The harmonic of the common fundamental
     * @param notes The notes as harmonics of the common fundamental
     * @return True if it is a multiple of one of them
     */
    private static boolean isInSeries (final int harmonic, final List<Integer> notes)
    {
        for (final Integer note: notes)
            if (harmonic % note.intValue () == 0)
                return true;
        return false;
    }


    /**
     * Build the candidate fundamentals from the strongest peaks of the spectrum: each of them may
     * be any of the first harmonics of the fundamental. The candidates are returned with the
     * highest first, since that is the order in which they are tried.
     *
     * @param power The power spectrum
     * @param binWidth The frequency of one bin
     * @param firstBin The first bin of the analysed band
     * @param lastBin The last bin of the analysed band
     * @param notatedFrequency The frequency of the root key
     * @param sampleRate The sample rate
     * @return The candidates in Hertz, the highest first
     */
    private static double [] candidates (final double [] power, final double binWidth, final int firstBin, final int lastBin, final double notatedFrequency, final int sampleRate)
    {
        double maximum = 0;
        for (int bin = firstBin; bin <= lastBin; bin++)
            maximum = Math.max (maximum, power[bin]);
        final double threshold = maximum * Math.pow (10, -PEAK_RANGE_DB / 10);

        final double [] peakPower = new double [CANDIDATE_PEAKS];
        final double [] peakFrequency = new double [CANDIDATE_PEAKS];
        for (int bin = firstBin + 1; bin < lastBin; bin++)
        {
            if (power[bin] < threshold || power[bin] <= power[bin - 1] || power[bin] < power[bin + 1])
                continue;
            insertPeak (peakPower, peakFrequency, power[bin], interpolatePeak (power, bin) * binWidth);
        }

        final double lowest = Math.max (MINIMUM_FREQUENCY, notatedFrequency / 8);
        final double highest = Math.min (BAND_LIMIT * sampleRate, notatedFrequency * 8);
        final List<Double> candidates = new ArrayList<> ();
        for (int i = 0; i < CANDIDATE_PEAKS; i++)
        {
            if (peakPower[i] <= 0)
                continue;
            for (int divisor = 1; divisor <= CANDIDATE_DIVISORS; divisor++)
            {
                final double candidate = peakFrequency[i] / divisor;
                if (candidate >= lowest && candidate <= highest)
                    candidates.add (Double.valueOf (candidate));
            }
        }
        Collections.sort (candidates, Collections.reverseOrder ());

        final double [] result = new double [candidates.size ()];
        int size = 0;
        for (final Double candidate: candidates)
        {
            final double frequency = candidate.doubleValue ();
            if (size == 0 || 1200 * MathUtils.log2 (result[size - 1] / frequency) > CANDIDATE_DISTANCE)
                result[size++] = frequency;
        }
        final double [] trimmed = new double [size];
        System.arraycopy (result, 0, trimmed, 0, size);
        return trimmed;
    }


    /**
     * Get the part of the power of the analysed band which lies on the harmonics of a fundamental.
     *
     * @param power The power spectrum
     * @param binWidth The frequency of one bin
     * @param firstBin The first bin of the analysed band
     * @param lastBin The last bin of the analysed band
     * @param total The power of the whole analysed band
     * @param fundamental The fundamental to test
     * @return The part of the power which lies on its harmonics
     */
    private static double coverage (final double [] power, final double binWidth, final int firstBin, final int lastBin, final double total, final double fundamental)
    {
        final double toleranceFactor = Math.pow (2, TOLERANCE_CENTS / 1200) - 1;
        double covered = 0;
        for (int bin = firstBin; bin <= lastBin; bin++)
        {
            final double frequency = bin * binWidth;
            final double harmonic = Math.max (1, Math.round (frequency / fundamental));
            // The window may never grow beyond a quarter of the distance between two harmonics,
            // otherwise a very low candidate covers the whole spectrum by itself
            if (Math.abs (frequency - harmonic * fundamental) <= Math.min (frequency * toleranceFactor, fundamental / 4))
                covered += power[bin];
        }
        return covered / total;
    }


    /**
     * Measure a found fundamental exactly: the centre of its first harmonics, weighted by their
     * power. This is what makes a chorused or detuned sound - whose harmonics are split into a
     * cluster of partials - land on the pitch which is heard.
     *
     * @param power The power spectrum
     * @param binWidth The frequency of one bin
     * @param firstBin The first bin of the analysed band
     * @param lastBin The last bin of the analysed band
     * @param fundamental The fundamental to measure
     * @return The measured fundamental in Hertz
     */
    private static double refine (final double [] power, final double binWidth, final int firstBin, final int lastBin, final double fundamental)
    {
        final double toleranceFactor = Math.pow (2, TOLERANCE_CENTS / 1200) - 1;
        double weighted = 0;
        double weights = 0;
        final int stopBin = (int) Math.min (lastBin, (REFINED_HARMONICS + 0.5) * fundamental / binWidth);
        for (int bin = firstBin; bin <= stopBin; bin++)
        {
            final double frequency = bin * binWidth;
            final double harmonic = Math.max (1, Math.round (frequency / fundamental));
            if (Math.abs (frequency - harmonic * fundamental) > Math.min (frequency * toleranceFactor, fundamental / 4))
                continue;
            weighted += power[bin] * frequency / harmonic;
            weights += power[bin];
        }
        return weights > 0 ? weighted / weights : fundamental;
    }


    /**
     * Get the power spectrum of a segment, windowed and padded to the given size.
     *
     * @param segment The audio to transform
     * @param fftSize The size of the transform
     * @return The power of the bins up to half the sample rate
     */
    private static double [] powerSpectrum (final double [] segment, final int fftSize)
    {
        final double [] real = new double [fftSize];
        final double [] imaginary = new double [fftSize];
        final int length = Math.min (segment.length, fftSize);
        for (int i = 0; i < length; i++)
        {
            // Blackman-Harris window: its side-lobes stay 92 dB down, so the partials of a sample
            // are not drowned in the skirts of a stronger neighbour
            final double phase = 2 * Math.PI * i / (length - 1.0);
            real[i] = segment[i] * (0.35875 - 0.48829 * Math.cos (phase) + 0.14128 * Math.cos (2 * phase) - 0.01168 * Math.cos (3 * phase));
        }
        transform (real, imaginary);

        final double [] power = new double [fftSize / 2 + 1];
        for (int bin = 0; bin < power.length; bin++)
            power[bin] = real[bin] * real[bin] + imaginary[bin] * imaginary[bin];
        return power;
    }


    /**
     * Transform a complex signal in place (iterative radix-2, the length must be a power of two).
     *
     * @param real The real part, replaced by the real part of the spectrum
     * @param imaginary The imaginary part, replaced by the imaginary part of the spectrum
     */
    private static void transform (final double [] real, final double [] imaginary)
    {
        final int size = real.length;
        for (int i = 1, j = 0; i < size; i++)
        {
            int bit = size >> 1;
            for (; (j & bit) != 0; bit >>= 1)
                j ^= bit;
            j ^= bit;
            if (i < j)
            {
                double swap = real[i];
                real[i] = real[j];
                real[j] = swap;
                swap = imaginary[i];
                imaginary[i] = imaginary[j];
                imaginary[j] = swap;
            }
        }

        for (int length = 2; length <= size; length <<= 1)
        {
            final double angle = -2 * Math.PI / length;
            final double stepReal = Math.cos (angle);
            final double stepImaginary = Math.sin (angle);
            final int half = length / 2;
            for (int offset = 0; offset < size; offset += length)
            {
                double factorReal = 1;
                double factorImaginary = 0;
                for (int i = 0; i < half; i++)
                {
                    final int even = offset + i;
                    final int odd = even + half;
                    final double oddReal = real[odd] * factorReal - imaginary[odd] * factorImaginary;
                    final double oddImaginary = real[odd] * factorImaginary + imaginary[odd] * factorReal;
                    real[odd] = real[even] - oddReal;
                    imaginary[odd] = imaginary[even] - oddImaginary;
                    real[even] += oddReal;
                    imaginary[even] += oddImaginary;
                    final double nextReal = factorReal * stepReal - factorImaginary * stepImaginary;
                    factorImaginary = factorReal * stepImaginary + factorImaginary * stepReal;
                    factorReal = nextReal;
                }
            }
        }
    }


    /**
     * Get the size of the transform for a segment: a power of two which leaves room for padding,
     * which smooths the spectrum between the bins.
     *
     * @param length The number of frames of the segment
     * @return The size of the transform
     */
    private static int fftSize (final int length)
    {
        int size = MINIMUM_FFT_SIZE;
        while (size < length * 3 / 2 && size < MAXIMUM_FFT_SIZE)
            size <<= 1;
        return size;
    }


    /**
     * Get the exact position of a peak by fitting a parabola through its bin and the two
     * neighbours.
     *
     * @param power The power spectrum
     * @param bin The bin of the peak
     * @return The position of the peak in bins
     */
    private static double interpolatePeak (final double [] power, final int bin)
    {
        final double left = Math.log10 (Math.max (power[bin - 1], Double.MIN_NORMAL));
        final double centre = Math.log10 (Math.max (power[bin], Double.MIN_NORMAL));
        final double right = Math.log10 (Math.max (power[bin + 1], Double.MIN_NORMAL));
        final double curvature = left - 2 * centre + right;
        return curvature == 0 ? bin : bin + Math.clamp (0.5 * (left - right) / curvature, -0.5, 0.5);
    }


    /**
     * Keep a peak if it is stronger than the weakest one which is kept so far.
     *
     * @param peakPower The power of the kept peaks, the strongest first
     * @param peakFrequency The frequency of the kept peaks
     * @param power The power of the peak to insert
     * @param frequency The frequency of the peak to insert
     */
    private static void insertPeak (final double [] peakPower, final double [] peakFrequency, final double power, final double frequency)
    {
        if (power <= peakPower[peakPower.length - 1])
            return;
        int position = peakPower.length - 1;
        while (position > 0 && peakPower[position - 1] < power)
        {
            peakPower[position] = peakPower[position - 1];
            peakFrequency[position] = peakFrequency[position - 1];
            position--;
        }
        peakPower[position] = power;
        peakFrequency[position] = frequency;
    }


    private static double [] copy (final int [] signal, final int start, final int length)
    {
        final double [] segment = new double [length];
        for (int i = 0; i < length; i++)
            segment[i] = signal[start + i];
        return segment;
    }


    private static double median (final List<Double> values)
    {
        final List<Double> sorted = new ArrayList<> (values);
        Collections.sort (sorted);
        final int middle = sorted.size () / 2;
        if (sorted.size () % 2 == 1)
            return sorted.get (middle).doubleValue ();
        return (sorted.get (middle - 1).doubleValue () + sorted.get (middle).doubleValue ()) / 2;
    }


    private static double noteToFrequency (final int note)
    {
        return CONCERT_PITCH * Math.pow (2, (note - (double) CONCERT_PITCH_NOTE) / 12);
    }
}
