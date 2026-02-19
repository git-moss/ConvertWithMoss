package de.mossgrabers.convertwithmoss.core.algorithm;

import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;


/**
 * This multi-sample reducer decreases the number of ISampleZones across multiple groups while
 * preserving the exact original key×velocity coverage union.
 *
 * Requirements:
 * <ol>
 * <li>Groups keep their original velocity span (unless fully removed).</li>
 * <li>Zones may be reduced in both key and velocity dimensions.</li>
 * <li>If a zone is removed, neighboring zones expand to fill the gap.</li>
 * <li>For every originally covered key, at least one zone still covers it.</li>
 * <li>Prefer zones centered near velocity 100.</li>
 * <li>Reduction is global across all groups.</li>
 * <li>Original ISampleZone objects are reused and mutated via setters.</li>
 * <li>Code clarity preferred over performance.</li>
 * <li>The exact same key×velocity coverage union must be preserved.</li>
 * <li>Zones may overlap.</li>
 * <ol>
 *
 * Strategy:
 * <ol>
 * <li>Build original 128×128 coverage mask.</li>
 * <li>While total zones > maxSamples:
 * <ul>
 * <li>Generate all possible merge candidates (same group only).</li>
 * <li>For each candidate:
 * <ul>
 * <li>Simulate merge.</li>
 * <li>Rebuild coverage mask.</li>
 * <li>Accept only if mask equals original mask.</li>
 * <li>Select best scoring valid merge.</li>
 * <li>Apply merge.</li>
 * </ul>
 * </li>
 * </ul>
 * </li>
 * </ol>
 */
public class MultiSampleReducer
{
    /**
     * Reduces the number of samples in the groups to a maximum number.
     * 
     * @param groups The groups from which to reduce
     * @param maxSamples The maximum number of samples
     * @return The number of reduced samples
     */
    public static int reduce (final List<IGroup> groups, final int maxSamples)
    {
        int initialTotalZones = totalZones (groups);
        if (initialTotalZones <= maxSamples)
            return initialTotalZones;

        final boolean [] [] originalMask = buildCoverageMask (groups);
        int totalZones;
        while ((totalZones = totalZones (groups)) > maxSamples)
        {
            final MergeCandidate best = findBestValidMerge (groups, originalMask);
            // No valid merges possible without violating coverage
            if (best == null)
                break;

            applyMerge (best);
        }

        return initialTotalZones - totalZones;
    }


    private static MergeCandidate findBestValidMerge (final List<IGroup> groups, final boolean [] [] originalMask)
    {
        MergeCandidate best = null;
        for (final IGroup group: groups)
        {
            final List<ISampleZone> zones = group.getSampleZones ();
            for (int i = 0; i < zones.size (); i++)
                for (int j = i + 1; j < zones.size (); j++)
                {
                    final MergeCandidate candidate = tryCreateMerge (group, zones.get (i), zones.get (j));
                    if (candidate == null || !isCoveragePreserved (groups, candidate, originalMask))
                        continue;

                    if (best == null || candidate.score < best.score)
                        best = candidate;
                }
        }
        return best;
    }


    private static MergeCandidate tryCreateMerge (final IGroup group, final ISampleZone a, final ISampleZone b)
    {
        // Horizontal merge (adjacent in key)
        if (sameVelocityRange (a, b) && areKeyAdjacent (a, b))
        {
            final int newLow = Math.min (a.getKeyLow (), b.getKeyLow ());
            final int newHigh = Math.max (a.getKeyHigh (), b.getKeyHigh ());
            return createCandidate (group, a, b, newLow, newHigh, a.getVelocityLow (), a.getVelocityHigh ());
        }

        // Vertical merge (adjacent in velocity)
        if (sameKeyRange (a, b) && areVelocityAdjacent (a, b))
        {
            final int newVelLow = Math.min (a.getVelocityLow (), b.getVelocityLow ());
            final int newVelHigh = Math.max (a.getVelocityHigh (), b.getVelocityHigh ());
            return createCandidate (group, a, b, a.getKeyLow (), a.getKeyHigh (), newVelLow, newVelHigh);
        }

        return null;
    }


    private static MergeCandidate createCandidate (final IGroup group, final ISampleZone a, final ISampleZone b, final int newKeyLow, final int newKeyHigh, final int newVelLow, final int newVelHigh)
    {
        final MergeCandidate c = new MergeCandidate ();
        c.group = group;
        c.keep = a;
        c.remove = b;
        c.newKeyLow = newKeyLow;
        c.newKeyHigh = newKeyHigh;
        c.newVelLow = newVelLow;
        c.newVelHigh = newVelHigh;
        c.score = computeScore (newKeyLow, newKeyHigh, newVelLow, newVelHigh);
        return c;
    }


    private static boolean isCoveragePreserved (final List<IGroup> groups, final MergeCandidate candidate, final boolean [] [] originalMask)
    {
        // Temporarily apply merge
        final int oldKL = candidate.keep.getKeyLow ();
        final int oldKH = candidate.keep.getKeyHigh ();
        final int oldVL = candidate.keep.getVelocityLow ();
        final int oldVH = candidate.keep.getVelocityHigh ();

        candidate.keep.setKeyLow (candidate.newKeyLow);
        candidate.keep.setKeyHigh (candidate.newKeyHigh);
        candidate.keep.setVelocityLow (candidate.newVelLow);
        candidate.keep.setVelocityHigh (candidate.newVelHigh);

        candidate.group.getSampleZones ().remove (candidate.remove);

        final boolean [] [] newMask = buildCoverageMask (groups);

        // Revert changes
        candidate.keep.setKeyLow (oldKL);
        candidate.keep.setKeyHigh (oldKH);
        candidate.keep.setVelocityLow (oldVL);
        candidate.keep.setVelocityHigh (oldVH);

        candidate.group.getSampleZones ().add (candidate.remove);

        return masksEqual (originalMask, newMask);
    }


    private static boolean [] [] buildCoverageMask (final List<IGroup> groups)
    {
        final boolean [] [] mask = new boolean [128] [128];
        for (final IGroup group: groups)
            for (final ISampleZone z: group.getSampleZones ())
                for (int k = z.getKeyLow (); k <= z.getKeyHigh (); k++)
                    for (int v = z.getVelocityLow (); v <= z.getVelocityHigh (); v++)
                        mask[k][v] = true;
        return mask;
    }


    private static boolean masksEqual (final boolean [] [] a, final boolean [] [] b)
    {
        for (int k = 0; k < 128; k++)
            for (int v = 0; v < 128; v++)
                if (a[k][v] != b[k][v])
                    return false;
        return true;
    }


    private static void applyMerge (final MergeCandidate c)
    {
        c.keep.setKeyLow (c.newKeyLow);
        c.keep.setKeyHigh (c.newKeyHigh);
        c.keep.setVelocityLow (c.newVelLow);
        c.keep.setVelocityHigh (c.newVelHigh);

        c.group.getSampleZones ().remove (c.remove);
    }


    private static double computeScore (final int keyLow, final int keyHigh, final int velLow, final int velHigh)
    {
        // Scoring Heuristic

        final int width = keyHigh - keyLow + 1;
        final int height = velHigh - velLow + 1;
        final double area = width * height;

        final double velocityCenter = (velLow + velHigh) / 2.0;
        final double velocityPenalty = Math.abs (velocityCenter - 100);

        final double keyCenter = (keyLow + keyHigh) / 2.0;
        final double edgeBonus = Math.abs (keyCenter - 63.5);

        return 2.0 * velocityPenalty + 0.5 * area - 0.3 * edgeBonus;
    }


    private static boolean sameVelocityRange (final ISampleZone a, final ISampleZone b)
    {
        return a.getVelocityLow () == b.getVelocityLow () && a.getVelocityHigh () == b.getVelocityHigh ();
    }


    private static boolean sameKeyRange (final ISampleZone a, final ISampleZone b)
    {
        return a.getKeyLow () == b.getKeyLow () && a.getKeyHigh () == b.getKeyHigh ();
    }


    private static boolean areKeyAdjacent (final ISampleZone a, final ISampleZone b)
    {
        return a.getKeyHigh () + 1 == b.getKeyLow () || b.getKeyHigh () + 1 == a.getKeyLow ();
    }


    private static boolean areVelocityAdjacent (final ISampleZone a, final ISampleZone b)
    {
        return a.getVelocityHigh () + 1 == b.getVelocityLow () || b.getVelocityHigh () + 1 == a.getVelocityLow ();
    }


    private static int totalZones (final List<IGroup> groups)
    {
        return groups.stream ().mapToInt (g -> g.getSampleZones ().size ()).sum ();
    }


    private static class MergeCandidate
    {
        IGroup      group;
        ISampleZone keep;
        ISampleZone remove;

        int         newKeyLow;
        int         newKeyHigh;
        int         newVelLow;
        int         newVelHigh;

        double      score;
    }
}
