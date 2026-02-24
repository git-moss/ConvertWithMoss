// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.iso;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.akai.s3000.AkaiDiskImage;
import de.mossgrabers.convertwithmoss.format.akai.s3000.AkaiKeygroup;
import de.mossgrabers.convertwithmoss.format.akai.s3000.AkaiKeygroupSample;
import de.mossgrabers.convertwithmoss.format.akai.s3000.AkaiPartition;
import de.mossgrabers.convertwithmoss.format.akai.s3000.AkaiProgram;
import de.mossgrabers.convertwithmoss.format.akai.s3000.AkaiSample;
import de.mossgrabers.convertwithmoss.format.akai.s3000.AkaiVolume;


/**
 * Detects recursively ISO files in folders. Files must end with <i>.ISO</i>.
 *
 * @author Jürgen Moßgraber
 */
public class IsoDetector extends AbstractDetector<MetadataSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public IsoDetector (final INotifier notifier)
    {
        super ("ISO file", "ISO", notifier, new MetadataSettingsUI ("ISO"), ".iso");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        final IsoFormat isoFormat = identifyIso (sourceFile);
        switch (isoFormat)
        {
            case AKAI_S1000_S1100:
                this.notifier.log ("IDS_ISO_PROCESSING_FORMAT", IsoFormat.getName (isoFormat));
                return this.processAkaiS1000 (sourceFile);

            case AKAI_S3000:
            case ROLAND_S550_W30_DJ70:
            case ROLAND_S7XX:
            case UNKNOWN:
            default:
                this.notifier.logError ("IDS_ISO_UNSUPPORTED_FORMAT", IsoFormat.getName (isoFormat));
                return Collections.emptyList ();
        }
    }


    /**
     * Process an ISO file which was detected as Akai S1000 format.
     * 
     * @param sourceFile The ISO file to process
     * @return The converted multi-samples
     */
    private List<IMultisampleSource> processAkaiS1000 (final File sourceFile)
    {
        final List<IMultisampleSource> multiSampleSources = new ArrayList<> ();

        try (final AkaiDiskImage disk = new AkaiDiskImage (sourceFile))
        {
            final int partitionCount = disk.getPartitionCount ();

            final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, sourceFile.getName ());
            for (int partitionIndex = 0; partitionIndex < partitionCount; partitionIndex++)
            {
                final AkaiPartition partition = disk.getPartition (partitionIndex);
                this.notifier.log ("IDS_ISO_PROCESSING_PARTITION", partition.getName ());

                for (final AkaiVolume volume: partition.getVolumes ())
                {
                    final List<AkaiSample> samples = volume.getSamples ();
                    for (final AkaiProgram program: volume.getPrograms ())
                    {
                        final DefaultMultisampleSource multisampleSource = createMultiSample (sourceFile, parts, samples, program);
                        multiSampleSources.add (multisampleSource);
                    }
                }
            }

            this.notifier.log ("IDS_NOTIFY_LINE_FEED");
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_ISO_COULD_NOT_PROCESS", ex);
        }

        return multiSampleSources;
    }


    private DefaultMultisampleSource createMultiSample (final File sourceFile, final String [] parts, final List<AkaiSample> samples, final AkaiProgram program)
    {
        final String programName = program.getName ();
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, programName, programName);

        final IGroup group = new DefaultGroup ();
        multisampleSource.setGroups (Collections.singletonList (group));
        this.createSampleZones (group, program.getKeygroups (), samples);

        // Set global values
        final int pitchBendRange = program.getBendToPitch () & 0xFF;
        final double gain = (program.getVolume () & 0xFF) / 99.0;
        final double velocityToVolume = Math.clamp (program.getVelocityToVolume () / 50.0, -1.0, -1.0);
        for (final ISampleZone zone: group.getSampleZones ())
        {
            zone.setBendUp (pitchBendRange);
            zone.setBendDown (-pitchBendRange);
            zone.setGain (gain);
            zone.getAmplitudeVelocityModulator ().setDepth (velocityToVolume);
        }

        // Detect metadata
        final String [] tokens = java.util.Arrays.copyOf (parts, parts.length + 1);
        tokens[tokens.length - 1] = programName;
        multisampleSource.getMetadata ().detectMetadata (this.settingsConfiguration, tokens);

        return multisampleSource;
    }


    private void createSampleZones (final IGroup group, final AkaiKeygroup [] keygroups, final List<AkaiSample> samples)
    {
        for (final AkaiKeygroup keygroup: keygroups)
        {
            // Each key-group can have up to 4 velocity layers, therefore an individual ISampleZone
            // needs to be created for each layer

            final int lowKey = keygroup.getLowKey ();
            final int highKey = keygroup.getHighKey ();

            final boolean [] sampleKeyTracking = keygroup.getSampleKeyTracking ();

            final double keygroupTuning = calculateTuning (keygroup.getTuneSemitones (), keygroup.getTuneCents ());

            // TODO implement Filter with envelope
            // keygroup.getEnvelope2ToFilter ()
            // keygroup.getFilter ()
            // keygroup.getVelocityToFilter ()

            // TODO implement pitch envelope
            // keygroup.getEnvelope2ToPitch ()

            for (final AkaiKeygroupSample keygroupSample: keygroup.getSamples ())
            {
                // Is the layer used?
                final String sampleName = keygroupSample.getName ();
                if (sampleName == null || sampleName.isBlank ())
                    continue;

                final AkaiSample sample = lookupSample (samples, sampleName);
                if (sample == null)
                {
                    this.notifier.logError ("IDS_ISO_SAMPLE_NOT_FOUND", sampleName);
                    continue;
                }

                final ISampleZone sampleZone = new DefaultSampleZone (sampleName, lowKey, highKey);
                final IAudioMetadata audioMetadata = new DefaultAudioMetadata (1, sample.getSamplingFrequency (), 16, sample.getNumberOfSamples ());
                final short [] samples16bit = sample.getSamples ();
                if (samples16bit == null)
                {
                    this.notifier.logError ("IDS_ISO_SAMPLE_NOT_FOUND", sampleName);
                    continue;
                }
                final ISampleData sampleData = new InMemorySampleData (audioMetadata, samples16bit);
                sampleZone.setSampleData (sampleData);

                sampleZone.setVelocityLow (keygroupSample.getLowVelocity ());
                sampleZone.setVelocityHigh (keygroupSample.getHighVelocity ());

                // TODO use filter offset
                // keygroupSample.getFilter ()

                // TODO implement
                keygroupSample.getLoopMode ();
                keygroupSample.getLoudness ();
                keygroupSample.getPan ();

                final double sampleTuning = calculateTuning (keygroupSample.getTuneSemitones (), keygroupSample.getTuneCents ());
                sampleZone.setTuning (keygroupTuning + sampleTuning);

                group.addSampleZone (sampleZone);
            }
        }
    }


    private static AkaiSample lookupSample (final List<AkaiSample> samples, final String sampleName)
    {
        if (sampleName == null)
            return null;
        for (final AkaiSample sample: samples)
        {
            if (sampleName.equals (sample.getName ()))
                return sample;
        }
        return null;
    }


    private static IsoFormat identifyIso (final File sourceFile)
    {
        try (final FileInputStream in = new FileInputStream (sourceFile))
        {
            return IsoFormatIdentifier.identifyIso (in.readNBytes (IsoFormatIdentifier.MINIMUM_NUMBER_OF_REQUIRED_BYTES));
        }
        catch (final IOException ex)
        {
            return IsoFormat.UNKNOWN;
        }
    }


    /**
     * Combine the semi-tone and fine tuning.
     * 
     * @param tuneSemitones The semi-tones in the range of [-50..50]
     * @param tuneCents The tuning by in the range of [-128..127], needs to be scaled to [-50..+50]
     * @return The semi-tones with cents as fractions
     */
    private static double calculateTuning (final int tuneSemitones, final int tuneCents)
    {
        double cents = 0;
        if (tuneCents < 0)
            cents = tuneCents / 128.0 * 0.5;
        else if (tuneCents > 0)
            cents = tuneCents / 127.0 * 0.5;
        return tuneSemitones + cents;
    }
}
