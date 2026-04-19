// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ensoniq.mirage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.hfe.DiskImageBuilder;
import de.mossgrabers.convertwithmoss.file.hfe.HfeFile;
import de.mossgrabers.convertwithmoss.file.hfe.HfeFile.HfeVersion;
import de.mossgrabers.convertwithmoss.file.hfe.Sector;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects Ensoniq Mirage disk files. Files must end with <i>.edm</i>, <i>.img</i> or <i>.hfe</i>.
 *
 * @author Jürgen Moßgraber
 */
public class MirageDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final double FILTER_MINIMUM_FREQUENCY    = 50.0;
    private static final double FILTER_MAXIMUM_CUTOFF_VALUE = 99.0;
    private static final double FILTER_FREQUENCY_RATIO      = Math.pow (15000.0 / FILTER_MINIMUM_FREQUENCY, 1.0 / FILTER_MAXIMUM_CUTOFF_VALUE);
    private static final int    LOWEST_NOTE                 = 36;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public MirageDetector (final INotifier notifier)
    {
        super ("Ensoniq Mirage", "Mirage", notifier, new MetadataSettingsUI ("Mirage"), ".edm", ".img", ".hfe");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        return sourceFile.getName ().toLowerCase ().endsWith (".hfe") ? this.readHfeFile (sourceFile) : this.readEdmFile (sourceFile);
    }


    private List<IMultisampleSource> readHfeFile (final File sourceFile)
    {
        try
        {
            final HfeFile hfeFile = new HfeFile (sourceFile);
            final HfeVersion hfeVersion = hfeFile.getHfeVersion ();
            if (hfeVersion != HfeVersion.VERSION_1)
            {
                this.notifier.logError ("IDS_HFE_VERSION_NOT_SUPPORTED", hfeVersion == HfeVersion.VERSION_2 ? "v2" : "v3");
                return Collections.emptyList ();
            }
            if (hfeFile.getFloppyInterfaceMode () != HfeFile.FLOPPYMODE_GENERIC_SHUGGART_DD)
            {
                this.notifier.logError ("IDS_HFE_CAN_ONLY_DECODE_FLOPPY_MODE", "Generic Shuggart");
                return Collections.emptyList ();
            }

            return this.readEdmFile (sourceFile, buildImage (hfeFile));
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private List<IMultisampleSource> readEdmFile (final File sourceFile)
    {
        try
        {
            return this.readEdmFile (sourceFile, Files.readAllBytes (sourceFile.toPath ()));
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private List<IMultisampleSource> readEdmFile (final File sourceFile, final byte [] diskImageData)
    {
        final String programName = FileUtils.getNameWithoutType (sourceFile.getName ()).trim ();
        final String [] programNames = createMultiSampleNames (programName);

        final File parentFolder = sourceFile.getParentFile ();
        final String [] parts = AudioFileUtils.createPathParts (parentFolder, this.sourceFolder, programName);

        try
        {
            final List<IMultisampleSource> multiSampleSources = new ArrayList<> ();
            final MirageFile mirageFile = new MirageFile (programName, diskImageData);
            final int sampleRate = mirageFile.getSampleRate ();
            for (int layerIndex = 0; layerIndex < 6; layerIndex += 2)
            {
                final MirageLayer mirageLayerLower = mirageFile.layers.get (layerIndex);
                final MirageLayer mirageLayerUpper = mirageFile.layers.get (layerIndex + 1);
                final List<ISampleData> sampleDataLower = new ArrayList<> ();
                final List<ISampleData> sampleDataUpper = new ArrayList<> ();
                for (int waveIndex = 0; waveIndex < 8; waveIndex++)
                {
                    final byte [] waveSamplePcmLower = mirageLayerLower.getWaveSampleData (waveIndex);
                    sampleDataLower.add (new InMemorySampleData (new DefaultAudioMetadata (1, sampleRate, 8, waveSamplePcmLower.length), waveSamplePcmLower));
                    final byte [] waveSamplePcmUpper = mirageLayerUpper.getWaveSampleData (waveIndex);
                    sampleDataUpper.add (new InMemorySampleData (new DefaultAudioMetadata (1, sampleRate, 8, waveSamplePcmUpper.length), waveSamplePcmUpper));
                }

                for (int programIndex = 0; programIndex < 4; programIndex++)
                {
                    final MirageProgram mirageProgramLower = mirageLayerLower.programs.get (programIndex);
                    final MirageProgram mirageProgramUpper = mirageLayerUpper.programs.get (programIndex);
                    final String multiSampleName = programNames[layerIndex / 2] + " Program " + (programIndex + 1);
                    multiSampleSources.add (this.createMultiSample (sourceFile, parts, multiSampleName, mirageProgramLower, mirageLayerLower.waveSamples, sampleDataLower, mirageProgramUpper, mirageLayerUpper.waveSamples, sampleDataUpper));
                }
            }

            // Apply global pitch-bend
            final int pitchBend = mirageFile.pitchBendRange * 100;
            for (final IMultisampleSource source: multiSampleSources)
                for (final IGroup group: source.getGroups ())
                    for (final ISampleZone sampleZone: group.getSampleZones ())
                    {
                        sampleZone.setBendUp (pitchBend);
                        sampleZone.setBendDown (-pitchBend);
                    }

            return multiSampleSources;
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Check if the filename contains a pattern lime Name1-Name2-Name3. If yes, these individual
     * names are returned. If not, 3 names are generated.
     * 
     * @param programName The basic program name
     * @return The 3 sound names
     */
    private static String [] createMultiSampleNames (final String programName)
    {
        final String [] nameParts = programName.split ("-");
        if (nameParts.length == 3)
            return nameParts;

        final String [] programNames = new String [3];
        for (int i = 0; i < programNames.length; i++)
            programNames[i] = programName + " Sound " + (i + 1);
        return programNames;
    }


    /**
     * Convert a lower and upper program into a multi-sample source.
     *
     * @param sourceFile The source file
     * @param parts The folder parts for metadata lookup
     * @param mirageProgramLower The lower program
     * @param waveSamplesLower The lower wave-samples
     * @param sampleDataLower The 8 lower wave-samples
     * @param mirageProgramUpper The upper program
     * @param waveSamplesUpper The upper wave-samples
     * @param sampleDataUpper The 8 upper wave-samples
     * @param multiSampleName The name of the multi-sample
     * @return The converted multi-sample source
     */
    private IMultisampleSource createMultiSample (final File sourceFile, final String [] parts, final String multiSampleName, final MirageProgram mirageProgramLower, final List<MirageWaveSample> waveSamplesLower, final List<ISampleData> sampleDataLower, final MirageProgram mirageProgramUpper, final List<MirageWaveSample> waveSamplesUpper, final List<ISampleData> sampleDataUpper)
    {
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, multiSampleName, multiSampleName);
        multisampleSource.setGroups (createSampleZones (multiSampleName, mirageProgramLower, waveSamplesLower, sampleDataLower, mirageProgramUpper, waveSamplesUpper, sampleDataUpper));

        // Detect metadata
        final String [] tokens = java.util.Arrays.copyOf (parts, parts.length + 1);
        tokens[tokens.length - 1] = multiSampleName;
        multisampleSource.getMetadata ().detectMetadata (this.settingsConfiguration, tokens);

        return multisampleSource;
    }


    private static List<IGroup> createSampleZones (final String multiSampleName, final MirageProgram mirageProgramLower, final List<MirageWaveSample> waveSamplesLower, final List<ISampleData> sampleDataLower, final MirageProgram mirageProgramUpper, final List<MirageWaveSample> waveSamplesUpper, final List<ISampleData> sampleDataUpper)
    {
        final IGroup osc1Group = new DefaultGroup ("OSC1");
        final IGroup osc2Group = new DefaultGroup ("OSC2");

        final List<ISampleData> allSampleData = new ArrayList<> (16);
        allSampleData.addAll (sampleDataLower);
        allSampleData.addAll (sampleDataUpper);
        final List<MirageWaveSample> waveSamples = new ArrayList<> (16);
        waveSamples.addAll (waveSamplesLower);
        waveSamples.addAll (waveSamplesUpper);

        int lowerKey = 0;
        for (int i = mirageProgramLower.initialWavesample; i < allSampleData.size (); i++)
        {
            MirageWaveSample mirageWaveSample = waveSamples.get (i);
            if (mirageWaveSample.topKey < lowerKey)
            {
                // Already in the upper sound, we are done!
                if (i >= 8)
                    break;
                // Skip to the upper sound samples!
                if (i < 8 + mirageProgramUpper.initialWavesample)
                    i = 8 + mirageProgramUpper.initialWavesample;
                mirageWaveSample = waveSamples.get (i);
                if (mirageWaveSample.topKey < lowerKey)
                    break;
            }

            final ISampleZone osc1SampleZone = createSampleZone (multiSampleName, allSampleData, i);

            osc1SampleZone.setKeyLow (LOWEST_NOTE + lowerKey);
            osc1SampleZone.setKeyHigh (LOWEST_NOTE + mirageWaveSample.topKey);

            osc1SampleZone.setGain (MathUtils.valueToDb (Math.clamp (mirageWaveSample.relativeAmplitude / 63.0, 0, 1)));

            // Root Key is calculated based on tuning away from MIDI note 57 (A3 when Middle C =
            // 60), which is always the unity note on the Mirage.
            final double tuning = (mirageWaveSample.coarseTune - 4 + mirageWaveSample.fineTune / 256.0) * 12.0 - 7.0;
            int semitones = (int) tuning;
            final double fine = tuning - semitones;
            if (i >= 8)
                semitones -= 24;
            osc1SampleZone.setKeyRoot (45 - semitones);
            osc1SampleZone.setTuning (fine);

            final MirageProgram program = i < 8 ? mirageProgramLower : mirageProgramUpper;

            final IEnvelopeModulator ampEnvelopeModulation = createEnvelopeModulation (program.ampEnvelopeAttack, program.ampEnvelopeDecay, program.ampEnvelopeSustain, program.ampEnvelopeRelease, program.ampEnvelopeSustainVelocity);
            final IEnvelopeModulator amplitudeEnvelopeModulator = osc1SampleZone.getAmplitudeEnvelopeModulator ();
            amplitudeEnvelopeModulator.setDepth (ampEnvelopeModulation.getDepth ());
            amplitudeEnvelopeModulator.setSource (ampEnvelopeModulation.getSource ());

            final double resonance = Math.clamp (program.resonance, 0, 160) / 160.0;
            final IFilter filter = new DefaultFilter (FilterType.LOW_PASS, 4, valueToFrequency (program.filterCutoffFreq + mirageWaveSample.relativeFilterFreq), resonance);
            final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
            final IEnvelopeModulator filterEnvelopeModulation = createEnvelopeModulation (program.filterEnvelopeAttack, program.filterEnvelopeDecay, program.filterEnvelopeSustain, program.filterEnvelopeRelease, program.filterEnvelopeSustainVelocity);
            cutoffEnvelopeModulator.setDepth (filterEnvelopeModulation.getDepth ());
            cutoffEnvelopeModulator.setSource (filterEnvelopeModulation.getSource ());

            // Not sure about the filter values, therefore, prevent totally closed filter
            if (cutoffEnvelopeModulator.getDepth () != 0 || filter.getCutoff () > 8000)
                osc1SampleZone.setFilter (filter);

            // Ignore since results do not work...
            // final double mixVelocity = program.mixVelocitySensitivity / 124.0;
            // osc1SampleZone.getAmplitudeVelocityModulator ().setDepth (mixVelocity);
            // osc2SampleZone.getAmplitudeVelocityModulator ().setDepth (-mixVelocity);

            if (mirageWaveSample.loopMode > 0)
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                loop.setStart (mirageWaveSample.loopStart - mirageWaveSample.sampleStart);
                loop.setEnd (mirageWaveSample.loopEnd - mirageWaveSample.sampleStart);
                osc1SampleZone.getLoops ().add (loop);
            }

            // If OSC Mix is not fully set to one OSC (0 = OSC1, 252 = OSC2) create 2 layers!
            final ISampleZone osc2SampleZone = new DefaultSampleZone (osc1SampleZone);
            osc2SampleZone.setSampleData (osc1SampleZone.getSampleData ());
            // Tune the 2nd oscillator upwards, interpret as 1 cent
            osc2SampleZone.setTuning (osc2SampleZone.getTuning () + program.oscDetune / 100.0);
            osc2Group.addSampleZone (osc2SampleZone);

            // Adjust the volume between OSC1 and OSC2 with an equal-power cross-fade
            final double mix = program.oscMix / 252.0;
            osc1SampleZone.setGain (Math.pow (10.0, osc1SampleZone.getGain () / 20.0) * Math.cos (mix * Math.PI * 0.5));
            osc2SampleZone.setGain (Math.pow (10.0, osc2SampleZone.getGain () / 20.0) * Math.sin (mix * Math.PI * 0.5));

            if (program.oscMix < 252)
                osc1Group.addSampleZone (osc1SampleZone);
            if (program.oscMix > 0)
                osc1Group.addSampleZone (osc2SampleZone);

            // Assign even wave-samples to OSC1 & assign un-even wave-samples to OSC2 -> but
            // apply parameters of uneven wave-sample!
            if (program.mixModeSwitch > 0)
            {
                i++;
                osc2SampleZone.setSampleData (allSampleData.get (i));
            }

            lowerKey = mirageWaveSample.topKey + 1;
            if (lowerKey > 60)
                break;
        }

        final List<IGroup> groups = new ArrayList<> ();
        groups.add (osc1Group);
        if (!osc2Group.getSampleZones ().isEmpty ())
            groups.add (osc2Group);
        return groups;
    }


    private static ISampleZone createSampleZone (final String multiSampleName, final List<ISampleData> allSampleData, final int pos)
    {
        final String sampleName = multiSampleName + " WS " + (pos + 1);
        return new DefaultSampleZone (sampleName, allSampleData.get (pos));
    }


    private static byte [] buildImage (final HfeFile hfeFile)
    {
        final List<Sector> sectors = hfeFile.decodeMfmSectors ();
        // Sort sectors by cylinder, head, sector number
        Collections.sort (sectors);

        final int [] sectorLengths = new int []
        {
            1024,
            1024,
            1024,
            1024,
            1024,
            512
        };

        final byte [] imageData = DiskImageBuilder.createEmptyImage (MirageFile.FILE_LENGTH);
        int offset = 0;
        for (int i = 0; i < sectors.size (); i++)
        {
            final Sector sector = sectors.get (i);
            final int length = sectorLengths[i % 6];
            System.arraycopy (sector.getData (), 0, imageData, offset, length);
            offset += length;
        }

        return imageData;
    }


    private static IEnvelopeModulator createEnvelopeModulation (final int attack, final int decay, final int sustain, final int release, final int sustainVelocity)
    {
        final IEnvelopeModulator modulator = new DefaultEnvelopeModulator (sustainVelocity / 124.0);
        final IEnvelope envelope = modulator.getSource ();
        envelope.setAttackTime (parseTime (attack));
        envelope.setDecayTime (parseTime (decay));
        envelope.setSustainLevel (parseVolume (sustain));
        envelope.setReleaseTime (parseTime (release));
        return modulator;
    }


    /**
     * Convert the time in the range [0..31] to seconds.
     *
     * @param time At value 0 it is instant and at value 31 the amplitude will take 30 seconds
     * @return The time in seconds
     */
    private static double parseTime (final int time)
    {
        return MathUtils.denormalizeTime (time / 31.0, 30.0);
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
        return volume / 31.0;
    }


    private static double valueToFrequency (final int value)
    {
        return FILTER_MINIMUM_FREQUENCY * Math.pow (FILTER_FREQUENCY_RATIO, Math.clamp (value, 0, FILTER_MAXIMUM_CUTOFF_VALUE));
    }
}
