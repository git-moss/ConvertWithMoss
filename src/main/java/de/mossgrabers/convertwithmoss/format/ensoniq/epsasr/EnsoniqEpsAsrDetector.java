// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ensoniq.epsasr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.NoteParser;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.TagDetector;


/**
 * Detects Ensoniq EPS/ASR disk files. Files must end with <i>.iso</i>, <i>.hfe</i>, <i>.gkh</i>,
 * <i>.img</i>, <i>.ede</i> or <i>.eda</i>.
 *
 * @author Jürgen Moßgraber
 */
public class EnsoniqEpsAsrDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final int                      LOWEST_NOTE  = 21;

    private static final Map<Integer, String>     HARDWARE_IDS = new HashMap<> (3);
    private static final Map<Integer, FilterType> FILTER_TYPES = new HashMap<> ();
    private static final Map<Integer, Integer>    FILTER_POLES = new HashMap<> ();
    private static final double []                TIMES        = new double [100];
    static
    {
        HARDWARE_IDS.put (Integer.valueOf (0x0000), "EPS");
        HARDWARE_IDS.put (Integer.valueOf (0xFFFF), "EPS-16 PLUS");
        HARDWARE_IDS.put (Integer.valueOf (0xFFFE), "ASR-10");

        FILTER_TYPES.put (Integer.valueOf (0), FilterType.LOW_PASS);
        FILTER_TYPES.put (Integer.valueOf (1), FilterType.LOW_PASS);
        FILTER_TYPES.put (Integer.valueOf (2), FilterType.LOW_PASS);
        FILTER_TYPES.put (Integer.valueOf (3), FilterType.LOW_PASS);

        FILTER_POLES.put (Integer.valueOf (0), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (1), Integer.valueOf (3));
        FILTER_POLES.put (Integer.valueOf (2), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (3), Integer.valueOf (3));

        TIMES[0] = 0;
        TIMES[1] = 0.01;
        TIMES[2] = 0.02;
        TIMES[3] = 0.03;
        TIMES[4] = 0.04;
        TIMES[5] = 0.05;
        TIMES[6] = 0.06;
        TIMES[7] = 0.07;
        TIMES[8] = 0.08;
        TIMES[9] = 0.09;
        TIMES[10] = 0.10;
        TIMES[11] = 0.11;
        TIMES[12] = 0.11;
        TIMES[13] = 0.12;
        TIMES[14] = 0.13;
        TIMES[15] = 0.14;
        TIMES[16] = 0.15;
        TIMES[17] = 0.16;
        TIMES[18] = 0.17;
        TIMES[19] = 0.19;
        TIMES[20] = 0.20;
        TIMES[21] = 0.22;
        TIMES[22] = 0.23;
        TIMES[23] = 0.25;
        TIMES[24] = 0.27;
        TIMES[25] = 0.29;
        TIMES[26] = 0.31;
        TIMES[27] = 0.33;
        TIMES[28] = 0.35;
        TIMES[29] = 0.38;
        TIMES[30] = 0.41;
        TIMES[31] = 0.44;
        TIMES[32] = 0.47;
        TIMES[33] = 0.50;
        TIMES[34] = 0.54;
        TIMES[35] = 0.58;
        TIMES[36] = 0.62;
        TIMES[37] = 0.66;
        TIMES[38] = 0.71;
        TIMES[39] = 0.76;
        TIMES[40] = 0.82;
        TIMES[41] = 0.88;
        TIMES[42] = 0.94;
        TIMES[43] = 1.0;
        TIMES[44] = 1.0;
        TIMES[45] = 1.1;
        TIMES[46] = 1.2;
        TIMES[47] = 1.3;
        TIMES[48] = 1.4;
        TIMES[49] = 1.5;
        TIMES[50] = 1.6;
        TIMES[51] = 1.7;
        TIMES[52] = 1.8;
        TIMES[53] = 2.0;
        TIMES[54] = 2.1;
        TIMES[55] = 2.3;
        TIMES[56] = 2.4;
        TIMES[57] = 2.6;
        TIMES[58] = 2.8;
        TIMES[59] = 3.0;
        TIMES[60] = 3.2;
        TIMES[61] = 3.5;
        TIMES[62] = 3.7;
        TIMES[63] = 4.0;
        TIMES[64] = 4.3;
        TIMES[65] = 4.6;
        TIMES[66] = 4.9;
        TIMES[67] = 5.3;
        TIMES[68] = 5.7;
        TIMES[69] = 6.1;
        TIMES[70] = 6.5;
        TIMES[71] = 7.0;
        TIMES[72] = 7.5;
        TIMES[73] = 8.1;
        TIMES[74] = 8.6;
        TIMES[75] = 9.3;
        TIMES[76] = 9.9;
        TIMES[77] = 10;
        TIMES[78] = 11;
        TIMES[79] = 12;
        TIMES[80] = 13;
        TIMES[81] = 14;
        TIMES[82] = 15;
        TIMES[83] = 16;
        TIMES[84] = 17;
        TIMES[85] = 18;
        TIMES[86] = 19;
        TIMES[87] = 21;
        TIMES[88] = 22;
        TIMES[89] = 24;
        TIMES[90] = 26;
        TIMES[91] = 28;
        TIMES[92] = 30;
        TIMES[93] = 32;
        TIMES[94] = 34;
        TIMES[95] = 37;
        TIMES[96] = 39;
        TIMES[97] = 42;
        TIMES[98] = 45;
        TIMES[99] = 49;
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public EnsoniqEpsAsrDetector (final INotifier notifier)
    {
        super ("Ensoniq EPS/ASR", "Ensoniq", notifier, new MetadataSettingsUI ("Ensoniq"), ".iso", ".hfe", ".gkh", ".img", ".efe", ".ede", ".eda");
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            return this.createMultiSamples (new EnsoniqDisk (sourceFile));
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private List<IMultisampleSource> createMultiSamples (final EnsoniqDisk ensoniqDisk)
    {
        final StringBuilder sb = new StringBuilder ();
        final String diskID = ensoniqDisk.getDiskID ();
        final String diskLabel = ensoniqDisk.getDiskLabel ();
        final String description = ensoniqDisk.getDescription ();
        final String minimumRomVersion = ensoniqDisk.getMinimumRomVersion ();
        final String osVersion = ensoniqDisk.getOsVersion ();
        if (diskID != null && !diskID.isBlank ())
            sb.append (diskID).append (" ");
        if (diskLabel != null && !diskLabel.isBlank ())
            sb.append (diskLabel).append (" ");
        if (description != null && !description.isBlank ())
            sb.append (description);
        if (minimumRomVersion != null && !minimumRomVersion.isBlank ())
            sb.append (" Minimum ROM version: ").append (minimumRomVersion);
        if (osVersion != null && !osVersion.isBlank ())
            sb.append (" OS version: ").append (osVersion);
        this.notifier.log ("IDS_EPS_FOUND_DISK", ensoniqDisk.getEncodingType ().toString (), sb.toString ());

        final List<EnsoniqFile> instrumentFiles = ensoniqDisk.listInstruments ();
        if (instrumentFiles.isEmpty ())
        {
            this.notifier.logError ("IDS_EPS_FILE_DOES_NOT_CONTAIN_AN_INSTRUMENT");
            return Collections.emptyList ();
        }

        final File sourceFile = ensoniqDisk.getSourceFile ();
        final File parentFolder = sourceFile.getParentFile ();
        final List<IMultisampleSource> multiSampleSources = new ArrayList<> ();
        for (final EnsoniqFile instrumentFile: instrumentFiles)
            try
            {
                final EnsoniqInstrument instrument = new EnsoniqInstrument (instrumentFile);
                final String instrumentName = instrument.getName ();
                this.notifier.log ("IDS_EPS_FOUND_VERSION", HARDWARE_IDS.getOrDefault (Integer.valueOf (instrument.getInstrumentID ()), "EPS"), instrumentName);

                final String [] parts = AudioFileUtils.createPathParts (parentFolder, this.sourceFolder, instrumentName);
                multiSampleSources.addAll (this.createMultiSample (sourceFile, parts, instrumentName, instrument));
            }
            catch (final IOException ex)
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            }

        return multiSampleSources;
    }


    /**
     * Convert an Instrument to a multi-sample source.
     *
     * @param sourceFile The source file
     * @param parts The folder parts for metadata lookup
     * @param instrument The lower program
     * @param multiSampleName The name of the multi-sample
     * @return The converted multi-sample sources
     */
    private List<IMultisampleSource> createMultiSample (final File sourceFile, final String [] parts, final String multiSampleName, final EnsoniqInstrument instrument)
    {
        // Detect metadata
        final String [] tokens = java.util.Arrays.copyOf (parts, parts.length + 1);
        tokens[tokens.length - 1] = multiSampleName;
        final List<IGroup> sampleGroups = this.createSampleZones (multiSampleName, instrument);

        final List<IMultisampleSource> multiSampleSources = new ArrayList<> ();
        final int [] patches = instrument.getPatches ();
        // Create 4 patches with different activated layers
        int lastPatch = -1;
        final int numLayers = sampleGroups.size ();
        for (int i = 0; i < patches.length; i++)
        {
            // Prevent duplicates - needs to be limited to available layers!
            final int patch = MathUtils.clearBitsFrom (patches[i], numLayers);
            if (lastPatch == patch || patch == 0)
                continue;
            lastPatch = patch;

            final String name = TagDetector.toCamelCase (multiSampleName) + " " + (i + 1);
            final IMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, name, name);
            final List<IGroup> patchGroups = new ArrayList<> ();
            for (int p = 0; p < numLayers; p++)
            {
                if (MathUtils.isBitSet (patch, p))
                    patchGroups.add (sampleGroups.get (p));
            }
            if (patchGroups.isEmpty ())
                continue;

            multisampleSource.setGroups (patchGroups);
            if (multisampleSource.getNonEmptyGroups (true).isEmpty ())
                continue;
            multisampleSource.getMetadata ().detectMetadata (this.settingsConfiguration, tokens);
            multiSampleSources.add (multisampleSource);
        }

        // If there is only 1 preset, remove the index number for cleaner output
        if (multiSampleSources.size () == 1)
            multiSampleSources.get (0).setName (TagDetector.toCamelCase (multiSampleName));

        return multiSampleSources;
    }


    private List<IGroup> createSampleZones (final String multiSampleName, final EnsoniqInstrument instrument)
    {
        final Map<Integer, EnsoniqWaveSample> waveSamples = instrument.getWaveSamples ();
        final List<IGroup> groups = new ArrayList<> ();

        // Apply global range clipping
        final int instrumentKeyLow = instrument.getKeyRangeLow ();
        final int instrumentKeyHigh = instrument.getKeyRangeHigh ();

        // Apply global transposition
        final int instrumentTransposition = instrument.getTransposition ();

        // Assign trigger mode
        final int downLayers = instrument.getKeyDownLayers ();
        final int upLayers = instrument.getKeyUpLayers ();

        final Set<Integer> activeLayers = getActiveLayers (instrument);

        final List<EnsoniqLayer> layers = instrument.getLayers ();
        for (int i = 0; i < layers.size (); i++)
        {
            EnsoniqLayer layer = layers.get (i);

            if (!activeLayers.contains (Integer.valueOf (i)))
                continue;

            final IGroup group = new DefaultGroup ("Layer " + layer.getIndex ());
            groups.add (group);

            for (final KeyboardRange keyboardRange: this.createKeyboardRanges (i, layer, waveSamples))
            {
                EnsoniqWaveSample waveSample = keyboardRange.waveSample;

                InMemorySampleData sampleData = waveSample.getSampleData ();
                final int waveSampleCopy = waveSample.getWaveSampleCopy ();
                if (waveSampleCopy != 0)
                    sampleData = waveSamples.get (Integer.valueOf (waveSampleCopy)).getSampleData ();

                final ISampleZone sampleZone = new DefaultSampleZone (createSampleZoneName (waveSample, multiSampleName), sampleData);

                sampleZone.setKeyLow (Math.max (instrumentKeyLow, waveSample.getKeyRangeLow ()));
                sampleZone.setKeyHigh (Math.min (instrumentKeyHigh, waveSample.getKeyRangeHigh ()));
                sampleZone.setVelocityLow (layer.getVelocityLow ());
                sampleZone.setVelocityHigh (layer.getVelocityHigh ());

                if (instrument.getInstrumentID () == 0)
                {
                    final int panPositionEPS = waveSample.getPanPositionEPS () - 1;
                    if (panPositionEPS >= 0 && panPositionEPS <= 7)
                        sampleZone.setPanning (panPositionEPS / 7.0 * 2.0 - 1.0);
                }
                else
                    sampleZone.setPanning (waveSample.getPanPositionARS () / 127.0);

                final int pitchBendRange = waveSample.getPitchBendRange ();
                if (pitchBendRange < 13)
                {
                    sampleZone.setBendUp (pitchBendRange);
                    sampleZone.setBendDown (-pitchBendRange);
                }
                final IEnvelopeModulator pitchEnvelopeModulator = sampleZone.getPitchEnvelopeModulator ();
                pitchEnvelopeModulator.setSource (createEnvelope (waveSample.getPitchEnvelope ()));
                pitchEnvelopeModulator.setDepth (waveSample.getPitchEnvelopeAmount () / 127.0);

                sampleZone.setGain (MathUtils.valueToDb (Math.clamp (waveSample.getVolume () / 127.0, 0, 1)));

                sampleZone.setKeyRoot (waveSample.getRootNote ());
                sampleZone.setTuning (instrumentTransposition + waveSample.getFineTune () / 100.0);

                final IEnvelope ampEnvelope = createEnvelope (waveSample.getAmplitudeEnvelope ());
                final IEnvelopeModulator amplitudeEnvelopeModulator = sampleZone.getAmplitudeEnvelopeModulator ();
                amplitudeEnvelopeModulator.setSource (ampEnvelope);

                // 13=Pressure
                if (waveSample.getAmplitudeModulationSource () == 13)
                    sampleZone.getAmplitudeVelocityModulator ().setDepth (waveSample.getAmplitudeModulationAmount () / 127.0);

                // Filter
                final Integer filterMode = Integer.valueOf (waveSample.getFilterMode ());
                final FilterType filterType = FILTER_TYPES.getOrDefault (filterMode, FilterType.LOW_PASS);
                final int poles = FILTER_POLES.getOrDefault (filterMode, Integer.valueOf (2)).intValue ();

                final IFilter filter = new DefaultFilter (filterType, poles, MathUtils.denormalizeFrequency (waveSample.getFilter1Cutoff () / 150.0, 15000.0), 0);
                final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
                final IEnvelope filterEnvelope = createEnvelope (waveSample.getFilterEnvelope ());
                cutoffEnvelopeModulator.setDepth (waveSample.getFilter1EnvelopeAmount () / 127.0);
                cutoffEnvelopeModulator.setSource (filterEnvelope);
                // Pressure
                if (waveSample.getFilter1ModulationSource () == 13)
                    filter.getCutoffVelocityModulator ().setDepth (waveSample.getFilter1ModulationAmount () / 127.0);
                sampleZone.setFilter (filter);

                final int start = waveSample.getSampleStart ();
                final int end = waveSample.getSampleEnd ();
                sampleZone.setStart (start);
                sampleZone.setStop (end);

                // Loop
                final int loopMode = waveSample.getLoopMode ();
                if (loopMode > 0)
                {
                    final ISampleLoop loop = new DefaultSampleLoop ();
                    if (loopMode == 1)
                        loop.setType (LoopType.BACKWARDS);
                    else if (loopMode == 3)
                        loop.setType (LoopType.ALTERNATING);
                    loop.setStart (waveSample.getLoopStart ());
                    loop.setEnd (waveSample.getLoopEnd ());
                    sampleZone.getLoops ().add (loop);
                }

                group.addSampleZone (sampleZone);
            }

            assignToUpOrDownKey (i, group, downLayers, upLayers);

            // Hard pan left/right when stereo link is enabled
            if (layer.isStereoLink ())
            {
                for (final ISampleZone sampleZone: group.getSampleZones ())
                    sampleZone.setPanning (i % 2 == 0 ? -1.0 : 1.0);
            }
        }

        return groups;
    }


    private static void assignToUpOrDownKey (final int layerIndex, final IGroup group, final int downLayers, final int upLayers)
    {
        if (MathUtils.isBitSet (downLayers, layerIndex))
            return;

        final List<ISampleZone> sampleZones = group.getSampleZones ();
        if (MathUtils.isBitSet (upLayers, layerIndex))
        {
            for (final ISampleZone sampleZone: sampleZones)
                sampleZone.setTrigger (TriggerType.RELEASE);
            return;
        }

        // It is not part of down or up layer
        sampleZones.clear ();
        return;
    }


    private static String createSampleZoneName (final EnsoniqWaveSample waveSample, final String multiSampleName)
    {
        final String layerName = waveSample.getName ();
        final StringBuilder sb = new StringBuilder ();
        sb.append ("UNNAMED WS".equals (layerName) ? multiSampleName : layerName);
        sb.append (" #").append (waveSample.getIndex ()).append (' ');
        return sb.append (NoteParser.formatNoteAndOctave (waveSample.getRootNote (), 0)).toString ();
    }


    private static IEnvelope createEnvelope (final EnsoniqEnvelope amplitudeEnvelope)
    {
        final IEnvelope envelope = new DefaultEnvelope ();

        envelope.setAttackTime (parseTime (amplitudeEnvelope.getTime1 ()));
        envelope.setHoldTime (parseTime (amplitudeEnvelope.getTime2 ()));
        envelope.setDecayTime (parseTime (amplitudeEnvelope.getTime3 ()) + parseTime (amplitudeEnvelope.getTime4 ()));
        envelope.setReleaseTime (parseTime (amplitudeEnvelope.getTime5 ()) + parseTime (amplitudeEnvelope.getTime6 ()));

        envelope.setStartLevel (parseVolume (amplitudeEnvelope.getHardLevel0 ()));
        envelope.setHoldLevel (parseVolume (amplitudeEnvelope.getHardLevel1 ()));
        envelope.setSustainLevel (parseVolume (amplitudeEnvelope.getHardLevel4 ()));

        return envelope;
    }


    /**
     * Convert the time to seconds.
     *
     * @param time At value 0 it is instant and at value 99 the amplitude will take 49 seconds
     * @return The time in seconds
     */
    private static double parseTime (final int time)
    {
        return time > 99 ? 50 : TIMES[time];
    }


    /**
     * Convert the volume in the range of [0..31] to normalized range [0..1].
     *
     * @param volume At value 0 there will be no output and at VALUE 31 it will be at the maximum
     *            level
     * @return The normalized value
     */
    private static double parseVolume (final int volume)
    {
        return Math.clamp (volume / 99.0, 0, 1);
    }


    private List<KeyboardRange> createKeyboardRanges (final int layerIndex, final EnsoniqLayer layer, final Map<Integer, EnsoniqWaveSample> waveSamples)
    {
        final List<KeyboardRange> keyboardRanges = this.clusterRanges (layer.getWaveSamples ());
        final List<KeyboardRange> validatedKeyboardRanges = new ArrayList<> ();
        for (final KeyboardRange keyboardRange: keyboardRanges)
        {
            if (keyboardRange.refID == 0)
                continue;

            keyboardRange.waveSample = waveSamples.get (Integer.valueOf (keyboardRange.refID));
            if (keyboardRange.waveSample == null)
            {
                this.notifier.logError ("IDS_EPS_REFERENCED_WS_DOES_NOT_EXIST", Integer.toString (keyboardRange.refID), Integer.toString (layerIndex));
                continue;
            }
            keyboardRange.keyLow += LOWEST_NOTE;
            keyboardRange.keyHigh += LOWEST_NOTE;

            // Clip against covered range of the sample
            if (keyboardRange.keyLow < keyboardRange.waveSample.getKeyRangeLow ())
                keyboardRange.keyLow = keyboardRange.waveSample.getKeyRangeLow ();
            if (keyboardRange.keyHigh > keyboardRange.waveSample.getKeyRangeHigh ())
                keyboardRange.keyHigh = keyboardRange.waveSample.getKeyRangeHigh ();

            // Prevent fully clipped ranges
            if (keyboardRange.keyHigh >= keyboardRange.keyLow)
                validatedKeyboardRanges.add (keyboardRange);
        }

        return validatedKeyboardRanges;
    }


    private List<KeyboardRange> clusterRanges (final int [] referencedWaveSamples)
    {
        final List<KeyboardRange> keyboardRanges = new ArrayList<> ();
        KeyboardRange keyboardRange = new KeyboardRange ();
        keyboardRanges.add (keyboardRange);
        keyboardRange.refID = referencedWaveSamples[0];
        for (int i = 1; i < referencedWaveSamples.length; i++)
        {
            if (keyboardRange.refID != referencedWaveSamples[i])
            {
                keyboardRange.keyHigh = i - 1;

                keyboardRange = new KeyboardRange ();
                keyboardRange.keyLow = i;
                keyboardRanges.add (keyboardRange);
                keyboardRange.refID = referencedWaveSamples[i];
            }
        }
        return keyboardRanges;
    }


    private static Set<Integer> getActiveLayers (final EnsoniqInstrument instrument)
    {
        final Set<Integer> activeLayers = new TreeSet<> ();
        final int [] patches = instrument.getPatches ();
        for (int i = 0; i < patches.length; i++)
        {
            final int patch = patches[i];

            for (int p = 0; p < 8; p++)
            {
                if (MathUtils.isBitSet (patch, p))
                    activeLayers.add (Integer.valueOf (p));
            }
        }
        return activeLayers;
    }


    private class KeyboardRange
    {
        int               keyLow     = 0;
        int               keyHigh    = 127;
        int               refID      = -1;
        EnsoniqWaveSample waveSample = null;
    }
}
