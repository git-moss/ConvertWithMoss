// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mesa;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.exception.CompressionNotSupportedException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.SampleChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiKeygroup;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiKeygroupSample;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiProgram;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiProgramConverter;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiSample;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;


/**
 * Detects recursively MESA II files in folders. Files must end with <i>.S3P</i>.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMesaDetector extends AbstractDetector<MetadataSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public AkaiMesaDetector (final INotifier notifier)
    {
        super ("Akai MESA", "MESA", notifier, new MetadataSettingsUI ("MESA"), ".s3p");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        try
        {
            final S3pFile s3pFile = new S3pFile (sourceFile);
            final AkaiProgram program = s3pFile.getProgram ();
            if (program == null)
                return Collections.emptyList ();

            final AkaiProgramConverter converter = new AkaiProgramConverter (this.notifier, this.settingsConfiguration);
            final File parentFolder = sourceFile.getParentFile ();
            final List<AkaiSample> samples = detectSamples (parentFolder, program);
            final String [] parts = AudioFileUtils.createPathParts (parentFolder, this.sourceFolder, sourceFile.getName ());
            return Collections.singletonList (converter.createMultiSample (sourceFile, parts, samples, program, ""));
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ex);
            return Collections.emptyList ();
        }
    }


    private List<AkaiSample> detectSamples (final File parentFolder, final AkaiProgram program)
    {
        final List<AkaiSample> samples = new ArrayList<> ();
        for (final AkaiKeygroup keygroup: program.getKeygroups ())
        {
            for (final AkaiKeygroupSample keygroupSample: keygroup.getSamples ())
            {
                final String sampleName = keygroupSample.getName ();
                if (sampleName == null || sampleName.isBlank ())
                    continue;
                final File sampleFile = new File (parentFolder, sampleName + ".wav");
                if (!sampleFile.exists ())
                {
                    this.notifier.logError ("IDS_ISO_SAMPLE_NOT_FOUND", sampleFile.getName ());
                    continue;
                }

                try
                {
                    final WavFileSampleData wavFileSampleData = new WavFileSampleData (sampleFile);
                    final WaveFile waveFile = wavFileSampleData.getWaveFile ();
                    final SampleChunk sampleChunk = waveFile.getSampleChunk ();
                    if (sampleChunk == null)
                    {
                        this.notifier.logError ("IDS_S3P_SAMPLE_INFO_MISSING", sampleFile.getName ());
                        continue;
                    }
                    samples.add (new AkaiSample (sampleName, wavFileSampleData));
                }
                catch (final IOException | CompressionNotSupportedException ex)
                {
                    this.notifier.logError ("IDS_S3P_BROKEN_WAV", sampleFile.getName ());
                }
            }
        }
        return samples;
    }
}
