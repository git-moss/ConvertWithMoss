// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Repairs the zone sample indices of the banks of the E-mu library CD-ROMs whose mastering wrote
 * the 16 bit index through an 8 bit tool chain. The low byte of every such index is the true sample
 * slot modulo 256 while the high byte is unreliable: it is zero in most of the affected banks and
 * stale garbage in a few others. A preset whose samples sit above slot 256 therefore plays
 * completely unrelated material - the Formula 4000 and General MIDI CD-ROMs are full of examples
 * like a string preset which sounds basketball bounces.
 * <p>
 * The damage is per preset: presets written correctly (for example the drum kits of the 8 MB
 * General MIDI bank, which reference slots up to 531) sit in the same banks as truncated ones. The
 * evidence for the mechanism is spread over the whole library: the General MIDI drum kits of the
 * reduced drum banks reference holes whose low bytes are exactly the GM percussion sounds, hundreds
 * of presets resolve to samples which carry the preset's own name once the high byte is restored
 * ('Oct 3 All' to 'Oct 3 All E4', 'P5 Tablura' to 'P5TabluraE3'), and the pitch series named in the
 * sample names match the zones' original keys at the repaired slots.
 * <p>
 * Since the high byte is lost, the repair infers for each preset the page k so that its zones
 * resolve to slot low + k * 256. The stored interpretation always wins unless a page candidate is
 * decisively better; the evidence used is deliberately narrow: the note names which the E-mu sample
 * names carry ('OBXStringD2', 'puls98fx2') checked against the zones' original keys, the preset
 * name occurring in the sample names, and the feasibility of each page against the sample table (a
 * page is impossible when one of its slots does not exist). Presets whose evidence is ambiguous
 * keep their stored indices.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator3SampleIndexRepair
{
    /** A note name at the end of a sample name, e.g. 'OBXStringD2' or 'DRhodes F#4 Hard'. */
    private static final Pattern     NOTE_UPPER     = Pattern.compile ("([A-G])\\s?([#bx])?\\s?(-?\\d)\\s*$");
    /** A lower case note name; only trusted when letter, accidental and digit are contiguous. */
    private static final Pattern     NOTE_LOWER     = Pattern.compile ("([a-g])([#bx])?(-?\\d)$");
    /** Words which are too common in sample names to tie a preset to its samples. */
    private static final Set<String> GENERIC_TOKENS = Set.of ("loop", "wave", "the", "and", "link", "new", "old", "big", "low", "high");
    /** The pitch classes of the note letters A to G. */
    private static final int []      PITCH_CLASSES  =
    {
        9,
        11,
        0,
        2,
        4,
        5,
        7
    };


    /**
     * A note parsed from a sample name.
     * 
     * @param pitchClass The index of the note in the scale
     * @param octave The octave offset
     * @param hasAccidental Flat or sharp symbol
     */
    private record Note (int pitchClass, int octave, boolean hasAccidental)
    {
        // Intentionally empty
    }


    /**
     * One way to interpret the stored indices of a preset: as they are or moved to a page.
     * 
     * @param asIs Use the candidate as is otherwise use the candidate on the given page
     * @param page The page on which the alternative candidate is located
     */
    private record Candidate (boolean asIs, int page)
    {
        // Intentionally empty
    }


    /**
     * The evidence collected for one candidate.
     * 
     * @param hits The number of reference hits
     * @param parseable The overall number of references
     * @param distinctRoots The number of roots
     * @param affinity The affinity of the names
     */
    private record Score (int hits, int parseable, int distinctRoots, double affinity)
    {
        // Intentionally empty
    }


    /**
     * Private constructor since this is a utility class.
     */
    private Emulator3SampleIndexRepair ()
    {
        // Intentionally empty
    }


    /**
     * Resolve the zone sample indices of all presets of a bank.
     *
     * @param data The content of the bank
     * @param presetOffsets The offsets of all presets of the bank
     * @param sampleNames The names of the samples of the bank by their 1-based slot
     * @return For each preset offset which needs repairs, the mapping from the stored (masked) zone
     *         sample index to the resolved slot; presets whose indices are kept are absent
     */
    public static Map<Integer, Map<Integer, Integer>> resolveBank (final byte [] data, final List<Integer> presetOffsets, final Map<Integer, String> sampleNames)
    {
        final Map<Integer, Map<Integer, Integer>> repairs = new HashMap<> ();
        if (sampleNames.isEmpty ())
            return repairs;

        int maxSlot = 0;
        for (final Integer slot: sampleNames.keySet ())
            maxSlot = Math.max (maxSlot, slot.intValue ());
        final Map<Integer, Note> parsedNotes = new HashMap<> ();
        for (final Map.Entry<Integer, String> entry: sampleNames.entrySet ())
        {
            final Note note = parseNote (entry.getValue ());
            if (note != null)
                parsedNotes.put (entry.getKey (), note);
        }

        for (final Integer presetOffset: presetOffsets)
        {
            final Map<Integer, Integer> mapping = resolvePreset (data, presetOffset.intValue (), sampleNames, parsedNotes, maxSlot);
            if (!mapping.isEmpty ())
                repairs.put (presetOffset, mapping);
        }
        return repairs;
    }


    /**
     * Resolve the zone sample indices of one preset.
     *
     * @param data The content of the bank
     * @param presetOffset The offset of the preset
     * @param sampleNames The names of the samples of the bank by their 1-based slot
     * @param parsedNotes The notes parsed from the sample names, where a name carries one
     * @param maxSlot The highest occupied sample slot of the bank
     * @return The mapping from the stored index to the resolved slot; empty to keep all indices
     */
    private static Map<Integer, Integer> resolvePreset (final byte [] data, final int presetOffset, final Map<Integer, String> sampleNames, final Map<Integer, Note> parsedNotes, final int maxSlot)
    {
        // The distinct (original key, stored index) pairs of the preset - a pair which repeats
        // across note zones is one piece of evidence, not many
        final Set<Long> pairSet = collectZonePairs (data, presetOffset);
        if (pairSet.isEmpty ())
            return Map.of ();
        final int numPairs = pairSet.size ();
        final int [] roots = new int [numPairs];
        final int [] stored = new int [numPairs];
        final int [] lows = new int [numPairs];
        int n = 0;
        boolean asIsFeasible = true;
        for (final Long pair: new TreeSet<> (pairSet))
        {
            roots[n] = (int) (pair.longValue () >> 16);
            stored[n] = (int) (pair.longValue () & 0xFFFF);
            lows[n] = (stored[n] - 1) % 256 + 1;
            if (!sampleNames.containsKey (Integer.valueOf (stored[n])))
                asIsFeasible = false;
            n++;
        }

        // The candidates: the indices as they are plus every page on which all zones exist
        final List<Candidate> candidates = new ArrayList<> ();
        if (asIsFeasible)
            candidates.add (new Candidate (true, 0));
        final int maxPage = (maxSlot - 1) / 256;
        for (int page = 0; page <= maxPage; page++)
        {
            boolean feasible = true;
            boolean identical = asIsFeasible;
            for (int i = 0; i < numPairs; i++)
            {
                final int slot = lows[i] + page * 256;
                if (!sampleNames.containsKey (Integer.valueOf (slot)))
                    feasible = false;
                if (slot != stored[i])
                    identical = false;
            }
            if (feasible && !identical)
                candidates.add (new Candidate (false, page));
        }

        if (candidates.isEmpty ())
            return resolvePerZone (stored, lows, sampleNames, maxPage);

        final String presetName = Emulator3Constants.decodeName (data, presetOffset);
        final Score [] scores = new Score [candidates.size ()];
        for (int i = 0; i < candidates.size (); i++)
            scores[i] = score (candidates.get (i), roots, stored, lows, sampleNames, parsedNotes, presetName);

        final int chosen = choose (candidates, scores, numPairs);
        final Candidate winner = candidates.get (chosen);
        if (winner.asIs ())
            return Map.of ();
        final Map<Integer, Integer> mapping = new HashMap<> ();
        for (int i = 0; i < numPairs; i++)
        {
            final int slot = lows[i] + winner.page () * 256;
            if (slot != stored[i])
                mapping.put (Integer.valueOf (stored[i]), Integer.valueOf (slot));
        }
        return mapping;
    }


    /**
     * Choose the winning candidate. The stored interpretation is the baseline and only a decisively
     * better page replaces it.
     *
     * @param candidates The candidates; the first one is the baseline
     * @param scores The evidence of each candidate
     * @param numPairs The number of distinct zone pairs of the preset
     * @return The index of the winning candidate
     */
    private static int choose (final List<Candidate> candidates, final Score [] scores, final int numPairs)
    {
        final Score baseline = scores[0];

        // The strongest candidate by pitch evidence; ties prefer the stored interpretation and
        // then the lowest page
        int best = 0;
        for (int i = 1; i < scores.length; i++)
            if (scores[i].hits () > scores[best].hits ())
                best = i;

        // When every pitch score is weak and the stored names are pitched but all mismatch, a
        // candidate which carries the preset's own name in most zones outranks a lone chance hit
        if (scores[best].hits () < 3 && baseline.hits () == 0 && baseline.parseable () >= 2)
        {
            int affine = 0;
            boolean unique = true;
            for (int i = 1; i < scores.length; i++)
            {
                if (scores[i].affinity () > scores[affine].affinity ())
                {
                    affine = i;
                    unique = true;
                }
                else if (i != affine && scores[i].affinity () == scores[affine].affinity ())
                    unique = false;
            }
            if (scores[affine].affinity () >= 0.5 && (unique || candidates.size () == 1))
                best = affine;
        }

        if (best == 0)
            return 0;

        final Score bestScore = scores[best];
        final int hits = bestScore.hits ();
        final int parseable = bestScore.parseable ();
        final int baselineHits = baseline.hits ();
        final double affinityBest = bestScore.affinity ();
        final double affinityBase = baseline.affinity ();

        // A baseline whose sample names carry the preset's own name is never overridden
        if (affinityBase > affinityBest)
            return 0;

        // Hits must span 3+ distinct root keys - a single repeated root matching a chromatic
        // ladder by chance (drum zones rooted on C against a C ladder) is one hit, not many -
        // unless the preset name itself vouches for the target family
        final boolean decisive = hits >= 3 && (bestScore.distinctRoots () >= 3 || affinityBest > affinityBase) && hits >= baselineHits + 2 && hits * 10 >= 6 * Math.max (parseable, 1) && hits >= 2 * Math.max (baselineHits, 1);

        // Tiny presets: a perfect score is decisive only when the stored names are pitched but
        // mismatch - an unpitched stored target (percussion) may simply be correct and beyond
        // the reach of the pitch test
        final boolean perfectSmall = numPairs <= 2 && hits == numPairs && parseable == numPairs && baselineHits == 0 && baseline.parseable () >= 1;

        // The preset name vouching for a candidate whose pitch is unreadable, while the stored
        // names are pitched and all mismatch, is decisive too
        final boolean affinityDecisive = baselineHits == 0 && baseline.parseable () >= 2 && affinityBest >= 0.5 && affinityBase == 0;

        return decisive || perfectSmall || affinityDecisive ? best : 0;
    }


    /**
     * Collect the distinct (original key, stored sample index) pairs of all zones of a preset.
     *
     * @param data The content of the bank
     * @param presetOffset The offset of the preset
     * @return The pairs, encoded as (key << 16) | index
     */
    private static Set<Long> collectZonePairs (final byte [] data, final int presetOffset)
    {
        final Set<Long> pairs = new HashSet<> ();
        if (presetOffset + Emulator3Constants.PRESET_SIZE > data.length)
            return pairs;
        final int numNoteZones = data[presetOffset + Emulator3Constants.PRESET_NUM_NOTE_ZONES] & 0xFF;
        final int noteZoneOffset = presetOffset + Emulator3Constants.PRESET_SIZE;
        final int zonesOffset = noteZoneOffset + numNoteZones * Emulator3Constants.NOTE_ZONE_SIZE;

        int maxZone = -1;
        for (int noteZoneIndex = 0; noteZoneIndex < numNoteZones; noteZoneIndex++)
        {
            final int noteZone = noteZoneOffset + noteZoneIndex * Emulator3Constants.NOTE_ZONE_SIZE;
            if (noteZone + Emulator3Constants.NOTE_ZONE_SIZE > data.length)
                break;
            for (final int field: new int []
            {
                Emulator3Constants.NOTE_ZONE_PRIMARY,
                Emulator3Constants.NOTE_ZONE_SECONDARY
            })
            {
                final int zoneIndex = data[noteZone + field] & 0xFF;
                if (zoneIndex != Emulator3Constants.UNUSED)
                    maxZone = Math.max (maxZone, zoneIndex);
            }
        }

        for (int zoneIndex = 0; zoneIndex <= maxZone; zoneIndex++)
        {
            final int zone = zonesOffset + zoneIndex * Emulator3Constants.ZONE_SIZE;
            if (zone + Emulator3Constants.ZONE_SIZE > data.length)
                break;
            final int stored = Emulator3Constants.getU16 (data, zone + Emulator3Constants.ZONE_SAMPLE_INDEX) & Emulator3Constants.ZONE_SAMPLE_INDEX_MASK;
            if (stored == 0)
                continue;
            final int root = (data[zone + Emulator3Constants.ZONE_ORIGINAL_KEY] & 0xFF) + Emulator3Constants.KEY_OFFSET;
            pairs.add (Long.valueOf ((long) root << 16 | stored));
        }
        return pairs;
    }


    /**
     * Resolve zones one by one when no interpretation fits all of them: a stored index which exists
     * is kept and one which does not is moved to the lowest page on which it exists.
     *
     * @param stored The stored indices of the zones
     * @param lows The low bytes of the stored indices
     * @param sampleNames The names of the samples of the bank by their 1-based slot
     * @param maxPage The highest page of the bank
     * @return The mapping from the stored index to the resolved slot
     */
    private static Map<Integer, Integer> resolvePerZone (final int [] stored, final int [] lows, final Map<Integer, String> sampleNames, final int maxPage)
    {
        final Map<Integer, Integer> mapping = new HashMap<> ();
        for (int i = 0; i < stored.length; i++)
        {
            if (sampleNames.containsKey (Integer.valueOf (stored[i])))
                continue;
            for (int page = 0; page <= maxPage; page++)
            {
                final int slot = lows[i] + page * 256;
                if (sampleNames.containsKey (Integer.valueOf (slot)))
                {
                    mapping.put (Integer.valueOf (stored[i]), Integer.valueOf (slot));
                    break;
                }
            }
        }
        return mapping;
    }


    /**
     * Collect the evidence for one candidate: how many zones' original keys match the note named in
     * the sample name at the candidate's slot, over how many distinct root keys, how many of the
     * slots carry a parseable note at all, and how many carry the preset's own name.
     *
     * @param candidate The candidate to score
     * @param roots The original keys of the zones
     * @param stored The stored indices of the zones
     * @param lows The low bytes of the stored indices
     * @param sampleNames The names of the samples of the bank by their 1-based slot
     * @param parsedNotes The notes parsed from the sample names, where a name carries one
     * @param presetName The name of the preset
     * @return The evidence
     */
    private static Score score (final Candidate candidate, final int [] roots, final int [] stored, final int [] lows, final Map<Integer, String> sampleNames, final Map<Integer, Note> parsedNotes, final String presetName)
    {
        int hits = 0;
        int parseable = 0;
        final Set<Integer> hitRoots = new HashSet<> ();
        final List<String> targetNames = new ArrayList<> ();
        for (int i = 0; i < roots.length; i++)
        {
            final Integer slot = Integer.valueOf (candidate.asIs () ? stored[i] : lows[i] + candidate.page () * 256);
            final String name = sampleNames.get (slot);
            if (name != null)
                targetNames.add (name);
            final Note note = parsedNotes.get (slot);
            if (note == null)
                continue;
            parseable++;
            if (matches (roots[i], note))
            {
                hits++;
                hitRoots.add (Integer.valueOf (roots[i]));
            }
        }
        return new Score (hits, parseable, hitRoots.size (), affinity (presetName, targetNames));
    }


    /**
     * Check whether the note named in a sample name plausibly is the given key. E-mu names are
     * sloppy: the accidental is sometimes dropped ('OBXStringF4' for F#4) and the octave numbers
     * follow both the C3=60 and the C4=60 convention, so the pitch class must match exactly (or one
     * semitone flat when no accidental was written) and the octave within roughly one.
     *
     * @param key The MIDI key of the zone
     * @param note The note parsed from the sample name
     * @return True if they match
     */
    private static boolean matches (final int key, final Note note)
    {
        final int keyPitchClass = key % 12;
        if (note.pitchClass () != keyPitchClass && (note.hasAccidental () || (note.pitchClass () + 1) % 12 != keyPitchClass))
            return false;
        for (int base = 1; base <= 2; base++)
            if (Math.abs (note.pitchClass () + (note.octave () + base) * 12 - key) <= 13)
                return true;
        return false;
    }


    /**
     * Parse the note at the end of a sample name.
     *
     * @param name The sample name
     * @return The note or null if the name does not end with one
     */
    private static Note parseNote (final String name)
    {
        final String stripped = name.stripTrailing ();
        Matcher matcher = NOTE_UPPER.matcher (stripped);
        if (!matcher.find ())
        {
            matcher = NOTE_LOWER.matcher (stripped);
            if (!matcher.find ())
                return null;
        }
        final char letter = Character.toUpperCase (matcher.group (1).charAt (0));
        int pitchClass = PITCH_CLASSES[letter - 'A'];
        final String accidental = matcher.group (2);
        if (accidental != null)
            pitchClass = accidental.equals ("b") ? pitchClass - 1 : pitchClass + 1;
        return new Note ((pitchClass + 12) % 12, Integer.parseInt (matcher.group (3)), accidental != null);
    }


    /**
     * Calculate which fraction of the target sample names carries one of the distinctive words of
     * the preset name, e.g. 'KeyBass Sft' and 'SoftKeyBassA0'.
     *
     * @param presetName The name of the preset
     * @param targetNames The names of the samples the candidate resolves to
     * @return The fraction 0..1
     */
    private static double affinity (final String presetName, final List<String> targetNames)
    {
        if (targetNames.isEmpty ())
            return 0;
        final Set<String> tokens = new HashSet<> ();
        for (final String word: presetName.replace (':', ' ').replace ('/', ' ').replace ('-', ' ').split (" "))
        {
            final String token = normalize (word);
            if (token.length () >= 3 && !GENERIC_TOKENS.contains (token) && !token.chars ().allMatch (Character::isDigit))
                tokens.add (token);
        }
        if (tokens.isEmpty ())
            return 0;
        int matched = 0;
        for (final String name: targetNames)
        {
            final String normalized = normalize (name);
            for (final String token: tokens)
                if (normalized.contains (token))
                {
                    matched++;
                    break;
                }
        }
        return matched / (double) targetNames.size ();
    }


    /**
     * Reduce a text to its lower case letters and digits.
     *
     * @param text The text
     * @return The normalized text
     */
    private static String normalize (final String text)
    {
        final StringBuilder sb = new StringBuilder (text.length ());
        for (int i = 0; i < text.length (); i++)
        {
            final char c = text.charAt (i);
            if (Character.isLetterOrDigit (c))
                sb.append (Character.toLowerCase (c));
        }
        return sb.toString ();
    }
}
