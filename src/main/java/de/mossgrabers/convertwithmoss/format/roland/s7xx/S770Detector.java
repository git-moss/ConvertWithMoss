// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFileBasedSampleData;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770Partial.SampleSection;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770Partial.TvaSection;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770Partial.TvfSection;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770Patch.BenderSection;


/**
 * Detects recursively Roland S-7xx sampler disk files in folders. Files must end with <i>.out</i>,
 * <i>.img</i> or <i>.iso</i>.
 *
 * @author Jürgen Moßgraber
 */
public class S770Detector extends AbstractDetector<MetadataSettingsUI>
{
    // The highest key
    private static final int                      TOP_KEY              = 108;

    private static final int []                   SAMPLING_FREQUENCIES = new int [6];
    private static final LoopType []              LOOP_MODES           = new LoopType [7];
    private static final Map<Integer, FilterType> FILTER_TYPES         = new HashMap<> ();
    static
    {
        SAMPLING_FREQUENCIES[0] = 48000;
        SAMPLING_FREQUENCIES[1] = 44100;
        SAMPLING_FREQUENCIES[2] = 24000;
        SAMPLING_FREQUENCIES[3] = 22050;
        SAMPLING_FREQUENCIES[4] = 30000;
        SAMPLING_FREQUENCIES[5] = 15000;

        LOOP_MODES[0] = LoopType.FORWARDS;
        LOOP_MODES[1] = LoopType.FORWARDS;
        LOOP_MODES[2] = null;
        LOOP_MODES[3] = LoopType.FORWARDS;
        LOOP_MODES[4] = LoopType.ALTERNATING;
        LOOP_MODES[5] = LoopType.BACKWARDS;
        LOOP_MODES[6] = LoopType.BACKWARDS;

        FILTER_TYPES.put (Integer.valueOf (0), FilterType.LOW_PASS);
        FILTER_TYPES.put (Integer.valueOf (1), FilterType.BAND_PASS);
        FILTER_TYPES.put (Integer.valueOf (2), FilterType.HIGH_PASS);
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public S770Detector (final INotifier notifier)
    {
        super ("Roland S-7xx", "S7xx", notifier, new MetadataSettingsUI ("S7xx"), ".out", ".img", ".iso");
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            try (final InputStream input = new BufferedInputStream (new FileInputStream (sourceFile)))
            {
                final S770Header header = new S770Header (input);
                final boolean isDiskette = header.getDiskFormat () == S770DiskFormat.DISKETTE;
                this.notifier.log ("IDS_S7XX_VERSION", header.getS70Str (), header.getVersionStr (), header.getDiskName (), isDiskette ? "diskette" : "CD-ROM/HD");

                final IS770Image image = isDiskette ? this.loadDiskette (input, header, sourceFile.getParentFile ()) : new S770Hd (input, header);
                if (image == null)
                    return Collections.emptyList ();

                return this.readPatches (sourceFile, image);
            }
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }
        return Collections.emptyList ();
    }


    private S770Diskette loadDiskette (final InputStream input, final S770Header header, final File parentPath) throws IOException
    {
        final int indexDiskette = header.getIndexDiskette ();
        final int numDiskettes = header.getNumDiskettes ();
        if (indexDiskette != 0)
        {
            this.notifier.logError ("IDS_S7XX_CONTINUATION_DISK_IGNORED", Integer.toString (indexDiskette + 1), Integer.toString (numDiskettes + 1));
            return null;
        }

        final String diskName = header.getDiskName ();
        final List<byte []> continuationData = new ArrayList<> ();
        for (int i = 1; i <= numDiskettes; i++)
        {
            final byte [] continuationDisk = S770Diskette.findContinuationDisk (diskName, i, numDiskettes, parentPath);
            if (continuationDisk == null)
            {
                this.notifier.logError ("IDS_S7XX_CONTINUATION_DISK_NOT_FOUND", Integer.toString (i + 1), Integer.toString (numDiskettes + 1));
                return null;
            }
            this.notifier.log ("IDS_S7XX_CONTINUATION_DISK_FOUND", Integer.toString (i + 1), Integer.toString (numDiskettes + 1));
            continuationData.add (continuationDisk);
        }

        return new S770Diskette (input, header, continuationData);
    }


    private List<IMultisampleSource> readPatches (final File sourceFile, final IS770Image image)
    {
        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        final String metadataDescription = createMetadataDescription (image.getHeader ());

        final List<S770Patch> patches = image.getPatches ();
        for (int i = 0; i < patches.size (); i++)
        {
            final S770Patch patch = patches.get (i);
            final String patchName = patch.getName ().trim ();
            if (patchName.isBlank ())
                continue;
            this.notifier.log ("IDS_S7XX_CONVERTING_PATCH", String.format ("%02d %s", Integer.valueOf (i + 1), patchName));

            final IMultisampleSource multisampleSource = this.readPatch (sourceFile, patch, patchName, metadataDescription, image);
            multisampleSources.add (multisampleSource);
        }

        return multisampleSources;
    }


    private IMultisampleSource readPatch (final File sourceFile, final S770Patch patch, final String patchName, final String metadataDescription, final IS770Image image)
    {
        final File parentFile = sourceFile.getParentFile ();
        final String [] parts = AudioFileUtils.createPathParts (parentFile, this.sourceFolder, patchName);
        final IMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, patchName);

        final List<IGroup> groups = new ArrayList<> ();
        for (int i = 0; i < 4; i++)
            groups.add (new DefaultGroup ("Layer " + (i + 1)));
        multisampleSource.setGroups (groups);

        final List<S770Partial> partials = image.getPartials ();
        final List<S770Sample> samples = image.getSamples ();

        int lowKey = 21;
        int highKey;
        int partialId = -1;

        final int [] keysPartialSelection = patch.getKeysPartialSelection ();
        // Key 21-108
        for (int p = 0; p < keysPartialSelection.length; p++)
        {
            final int key = 21 + p;

            final int nextPartialId = keysPartialSelection[p];
            if (partialId == -1)
                partialId = nextPartialId;
            else if (partialId != nextPartialId || key == TOP_KEY)
            {
                highKey = key == TOP_KEY ? TOP_KEY : key - 1;

                // Only create zone if tone is enabled
                if (partialId >= 0)
                {
                    final S770Partial partial = partials.get (partialId);
                    final SampleSection [] sampleSections = partial.getSamples ();
                    for (int i = 0; i < sampleSections.length; i++)
                    {
                        final SampleSection sampleSection = sampleSections[i];

                        final int sampleIndex = sampleSection.getSampleSelection ();
                        if (sampleIndex < 0 || sampleIndex >= samples.size ())
                            continue;

                        final S770Sample sample = samples.get (sampleIndex);
                        final String sampleName = sample.getSampleName ().trim ();
                        final ISampleZone sampleZone = new DefaultSampleZone (sampleName, lowKey, highKey);
                        applyParameters (sampleZone, patch, partial, sampleSection, sample);
                        groups.get (i).addSampleZone (sampleZone);
                    }
                }

                lowKey = key;
                partialId = nextPartialId;
            }
        }

        final int octaveShift = patch.getOctaveShift () * 12;
        if (octaveShift != 0)
            for (final IGroup group: multisampleSource.getGroups ())
                for (final ISampleZone sampleZone: group.getSampleZones ())
                {
                    sampleZone.setKeyLow (Math.max (0, sampleZone.getKeyLow () - octaveShift));
                    sampleZone.setKeyHigh (Math.min (127, sampleZone.getKeyHigh () - octaveShift));
                    sampleZone.setTuning (sampleZone.getTuning () + octaveShift);
                }

        final IMetadata metadata = multisampleSource.getMetadata ();
        metadata.setDescription (metadataDescription);
        this.createMetadata (metadata, (IFileBasedSampleData) null, parts);
        this.updateCreationDateTime (metadata, sourceFile);

        return multisampleSource;
    }


    private static void applyParameters (final ISampleZone sampleZone, final S770Patch patch, final S770Partial partial, final SampleSection sampleSection, final S770Sample sample)
    {
        sampleZone.setSampleData (createSampleData (sample));
        sampleZone.setVelocityLow (sampleSection.getSmtVelocityLower ());
        sampleZone.setVelocityCrossfadeLow (sampleSection.getSmtFadeWidthLower ());
        sampleZone.setVelocityHigh (sampleSection.getSmtVelocityUpper ());
        sampleZone.setVelocityCrossfadeHigh (sampleSection.getSmtFadeWidthUpper ());
        sampleZone.setKeyRoot (sample.getOriginalKey ());
        sampleZone.setStart ((int) sample.getStartSample ().getAddress ());

        // 0 = Forward, 1 = Fwd+R, 2 = Oneshot, 3 = Fwd+One, 4 = Alt, 5 = Rev One, 6 = Rev
        final int loopMode = sample.getLoopMode ();
        if (loopMode != 2)
        {
            final ISampleLoop sampleLoop = new DefaultSampleLoop ();
            sampleLoop.setType (LOOP_MODES[loopMode]);
            sampleLoop.setStart ((int) sample.getSustainLoopStart ().getAddress ());
            sampleLoop.setCrossfadeInSamples (sample.getSustainLoopStart ().getFine () + sample.getSustainLoopEnd ().getFine ());
            sampleLoop.setEnd ((int) sample.getSustainLoopEnd ().getAddress ());
            sampleLoop.setTuning (sample.getSustainLoopTune () / 100.0);
            sampleZone.getLoops ().add (sampleLoop);
        }

        // Pitch parameters

        // Can only map values in the range of 0..8 which relates to 0..100%
        final int pitchKeyFollow = sampleSection.getPitchKf ();
        if (pitchKeyFollow >= 0 && pitchKeyFollow <= 8)
            sampleZone.setKeyTracking (pitchKeyFollow / 8.0);

        final double fineTuning = (patch.getFineTune () + partial.getFineTune () + sampleSection.getFineTune ()) / 100.0;
        sampleZone.setTuning (patch.getCoarseTune () + partial.getCoarseTune () + sampleSection.getCoarseTune () + fineTuning);

        // Volume parameters
        final double level = patch.getPatchLevel () / 127.0 * (patch.getStereoMixLevel () / 127.0) * (partial.getPartialLevel () / 127.0) * (partial.getStereoMixLevel () / 127.0) * (sampleSection.getSampleLevel () / 127.0);
        sampleZone.setGain (MathUtils.valueToDb (level));

        double pan = patch.getTotalPan () / 32.0 * (partial.getPan () / 32.0);
        final int samplePan = sampleSection.getPan ();
        // Ignore unsupported panning options
        if (samplePan <= 32)
            pan *= samplePan / 32.0;
        sampleZone.setPanning (pan);

        final TvaSection tva = partial.getTva ();
        final IEnvelope ampEnvelope = createEnvelope (tva.getLevels (), tva.getTimes ());
        sampleZone.getAmplitudeEnvelopeModulator ().setSource (ampEnvelope);
        // Unclear how to apply tva.getVelocityCurveRatio ()
        sampleZone.getAmplitudeVelocityModulator ().setDepth (tva.getVelocityCurveType () == 0 ? 0 : 1);

        createFilter (sampleZone, partial.getTvf ());

        final BenderSection bender = patch.getBender ();
        sampleZone.setBendUp (bender.getPitchCtrlUp () * 100);
        sampleZone.setBendDown (-bender.getPitchCtrlDown () * 100);
    }


    private static ISampleData createSampleData (final S770Sample sample)
    {
        final byte [] waveData = sample.getWaveData ();
        final int sampleRate = SAMPLING_FREQUENCIES[sample.getSampleFrequency () & 0xF];
        return new InMemorySampleData (new DefaultAudioMetadata (1, sampleRate, 16, waveData.length / 2), waveData);
    }


    private static void createFilter (final ISampleZone sampleZone, final TvfSection tvf)
    {
        final IEnvelope envelope = createEnvelope (tvf.getLevels (), tvf.getTimes ());

        final int filterMode = tvf.getFilterMode ();
        if (filterMode >= 0 && filterMode <= 2)
        {
            final double cutoff = MathUtils.denormalizeCutoff (tvf.getCutoff () / 127.0);
            final IFilter filter = new DefaultFilter (FILTER_TYPES.get (Integer.valueOf (filterMode)), 4, cutoff, tvf.getResonance () / 127.0);

            final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
            cutoffEnvelopeModulator.setSource (envelope);
            cutoffEnvelopeModulator.setDepth (tvf.getEnvTvfDepth () / 63.0);

            filter.getCutoffVelocityModulator ().setDepth (tvf.getVelocityCurveType () == 0 ? 0 : tvf.getCutoffVelocitySens () / 63.0);

            sampleZone.setFilter (filter);
        }

        final int envPitchDepth = tvf.getEnvPitchDepth ();
        if (envPitchDepth != 0)
        {
            final IEnvelopeModulator pitchEnvelopeModulator = sampleZone.getPitchEnvelopeModulator ();
            pitchEnvelopeModulator.setSource (envelope);
            pitchEnvelopeModulator.setDepth (envPitchDepth / 63.0);
        }
    }


    private static String createMetadataDescription (final S770Header header)
    {
        final StringBuilder sb = new StringBuilder ();

        String str = header.getVersionStr ();
        if (str != null && !str.isBlank ())
            sb.append (str.trim ()).append ("\n");

        str = header.getCopyrightStr ();
        if (str != null && !str.isBlank ())
            sb.append (str.trim ()).append ("\n");

        str = header.getDiskName ();
        if (str != null && !str.isBlank ())
            sb.append (str.trim ()).append ("\n");

        return sb.toString ().trim ();
    }


    private static IEnvelope createEnvelope (final int [] levels, final int [] times)
    {
        final IEnvelope envelope = new DefaultEnvelope ();

        envelope.setStartLevel (levels[0] / 127.0);
        envelope.setHoldLevel (levels[1] / 127.0);
        envelope.setSustainLevel (levels[2] / 127.0);
        envelope.setEndLevel (levels[3] / 127.0);

        envelope.setDelayTime (calculateTime (times[0]));
        envelope.setAttackTime (calculateTime (times[1]));
        envelope.setDecayTime (calculateTime (times[2]));
        envelope.setReleaseTime (calculateTime (times[3]));

        return envelope;
    }


    private static double calculateTime (final int value)
    {
        return 20.0 * Math.pow (2.0, (value - 127.0) / 21.0);
    }
}
