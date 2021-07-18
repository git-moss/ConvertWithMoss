// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core;

import de.mossgrabers.sampleconverter.exception.CompressionNotSupportedException;
import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.wav.DataChunk;
import de.mossgrabers.sampleconverter.file.wav.FormatChunk;
import de.mossgrabers.sampleconverter.file.wav.WaveFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * Base class for a samples' metadata.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class DefaultSampleMetadata implements ISampleMetadata
{
    protected final File       sampleFile;
    protected final File       zipFile;

    protected final String     filename;
    protected boolean          isMonoFile              = false;
    protected int              sampleRate              = 44100;

    protected Optional<String> combinedFilename        = Optional.empty ();
    protected Optional<String> filenameWithoutLayer    = Optional.empty ();

    protected PlayLogic        playLogic               = PlayLogic.ALWAYS;
    protected int              start                   = -1;
    protected int              stop                    = -1;
    protected int              keyRoot                 = 60;
    protected int              keyLow                  = 0;
    protected int              keyHigh                 = 127;
    protected int              crossfadeNotesLow       = 0;
    protected int              crossfadeNotesHigh      = 0;
    protected int              velocityLow             = 1;
    protected int              velocityHigh            = 127;
    protected int              crossfadeVelocitiesLow  = 0;
    protected int              crossfadeVelocitiesHigh = 0;

    protected double           gain                    = 0;
    protected double           tune                    = 0;
    protected double           keyTracking             = 1.0;
    protected boolean          isReversed              = false;

    protected List<SampleLoop> loops                   = new ArrayList<> (1);


    /**
     * Constructor.
     *
     * @param sampleFile The file where the sample is stored
     */
    public DefaultSampleMetadata (final File sampleFile)
    {
        this (sampleFile.getName (), sampleFile, null);
    }


    /**
     * Constructor for a sample stored in a ZIP file.
     *
     * @param zipFile The ZIP file which contains the WAV files
     * @param filename The name of the samples' file in the ZIP file
     */
    public DefaultSampleMetadata (final File zipFile, final String filename)
    {
        this (filename, null, zipFile);
    }


    /**
     * Constructor.
     *
     * @param filename The name of the file where the sample is stored
     */
    public DefaultSampleMetadata (final String filename)
    {
        this (filename, null, null);
    }


    /**
     * Constructor.
     *
     * @param filename The name of the file where the sample is stored
     * @param sampleFile The file where the sample is stored
     * @param zipFile The ZIP file which contains the WAV files
     */
    private DefaultSampleMetadata (final String filename, final File sampleFile, final File zipFile)
    {
        this.filename = filename;
        this.sampleFile = sampleFile;
        this.zipFile = zipFile;
    }


    /** {@inheritDoc} */
    @Override
    public File getFile ()
    {
        return this.sampleFile;
    }


    /** {@inheritDoc} */
    @Override
    public String getFilename ()
    {
        return this.filename;
    }


    /** {@inheritDoc} */
    @Override
    public int getSampleRate ()
    {
        return this.sampleRate;
    }


    /** {@inheritDoc} */
    @Override
    public PlayLogic getPlayLogic ()
    {
        return this.playLogic;
    }


    /** {@inheritDoc} */
    @Override
    public void setPlayLogic (final PlayLogic playLogic)
    {
        this.playLogic = playLogic;
    }


    /** {@inheritDoc} */
    @Override
    public int getStart ()
    {
        return this.start;
    }


    /** {@inheritDoc} */
    @Override
    public void setStart (final int start)
    {
        this.start = start;
    }


    /** {@inheritDoc} */
    @Override
    public int getStop ()
    {
        return this.stop;
    }


    /** {@inheritDoc} */
    @Override
    public void setStop (final int stop)
    {
        this.stop = stop;
    }


    /** {@inheritDoc} */
    @Override
    public void addLoop (final SampleLoop loop)
    {
        this.loops.add (loop);
    }


    /** {@inheritDoc} */
    @Override
    public List<SampleLoop> getLoops ()
    {
        return this.loops;
    }


    /** {@inheritDoc} */
    @Override
    public int getKeyLow ()
    {
        return this.keyLow;
    }


    /** {@inheritDoc} */
    @Override
    public void setKeyLow (final int keyLow)
    {
        this.keyLow = keyLow;
    }


    /** {@inheritDoc} */
    @Override
    public int getKeyHigh ()
    {
        return this.keyHigh;
    }


    /** {@inheritDoc} */
    @Override
    public void setKeyHigh (final int keyHigh)
    {
        this.keyHigh = keyHigh;
    }


    /** {@inheritDoc} */
    @Override
    public int getKeyRoot ()
    {
        return this.keyRoot;
    }


    /** {@inheritDoc} */
    @Override
    public void setKeyRoot (final int keyRoot)
    {
        this.keyRoot = keyRoot;
    }


    /** {@inheritDoc} */
    @Override
    public int getNoteCrossfadeLow ()
    {
        return this.crossfadeNotesLow;
    }


    /** {@inheritDoc} */
    @Override
    public void setNoteCrossfadeLow (final int crossfadeLow)
    {
        this.crossfadeNotesLow = crossfadeLow;
    }


    /** {@inheritDoc} */
    @Override
    public int getNoteCrossfadeHigh ()
    {
        return this.crossfadeNotesHigh;
    }


    /** {@inheritDoc} */
    @Override
    public void setNoteCrossfadeHigh (final int crossfadeHigh)
    {
        this.crossfadeNotesHigh = crossfadeHigh;
    }


    /** {@inheritDoc} */
    @Override
    public int getVelocityLow ()
    {
        return this.velocityLow;
    }


    /** {@inheritDoc} */
    @Override
    public void setVelocityLow (final int velocityLow)
    {
        this.velocityLow = velocityLow;
    }


    /** {@inheritDoc} */
    @Override
    public int getVelocityHigh ()
    {
        return this.velocityHigh;
    }


    /** {@inheritDoc} */
    @Override
    public void setVelocityHigh (final int velocityHigh)
    {
        this.velocityHigh = velocityHigh;
    }


    /** {@inheritDoc} */
    @Override
    public int getVelocityCrossfadeLow ()
    {
        return this.crossfadeVelocitiesLow;
    }


    /** {@inheritDoc} */
    @Override
    public void setVelocityCrossfadeLow (final int crossfadeLow)
    {
        this.crossfadeVelocitiesLow = crossfadeLow;
    }


    /** {@inheritDoc} */
    @Override
    public int getVelocityCrossfadeHigh ()
    {
        return this.crossfadeVelocitiesHigh;
    }


    /** {@inheritDoc} */
    @Override
    public void setVelocityCrossfadeHigh (final int crossfadeHigh)
    {
        this.crossfadeVelocitiesHigh = crossfadeHigh;
    }


    /** {@inheritDoc} */
    @Override
    public void setGain (final double gain)
    {
        this.gain = gain;
    }


    /** {@inheritDoc} */
    @Override
    public double getGain ()
    {
        return this.gain;
    }


    /** {@inheritDoc} */
    @Override
    public void setTune (final double tune)
    {
        this.tune = tune;
    }


    /** {@inheritDoc} */
    @Override
    public double getTune ()
    {
        return this.tune;
    }


    /** {@inheritDoc} */
    @Override
    public double getKeyTracking ()
    {
        return this.keyTracking;
    }


    /** {@inheritDoc} */
    @Override
    public void setKeyTracking (final double keyTracking)
    {
        this.keyTracking = keyTracking;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isReversed ()
    {
        return this.isReversed;
    }


    /** {@inheritDoc} */
    @Override
    public void setReversed (final boolean isReversed)
    {
        this.isReversed = isReversed;
    }


    /** {@inheritDoc} */
    @Override
    public void setCombinedName (final String combinedName)
    {
        this.combinedFilename = Optional.ofNullable (combinedName);
    }


    /** {@inheritDoc} */
    @Override
    public Optional<String> getCombinedName ()
    {
        return this.combinedFilename;
    }


    /** {@inheritDoc} */
    @Override
    public Optional<String> getUpdatedFilename ()
    {
        return this.combinedFilename.isEmpty () ? Optional.ofNullable (this.getFilename ()) : this.combinedFilename;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isMono ()
    {
        return this.isMonoFile;
    }


    /** {@inheritDoc} */
    @Override
    public void setFilenameWithoutLayer (final String nameWithoutLayer)
    {
        this.filenameWithoutLayer = Optional.ofNullable (nameWithoutLayer);
    }


    /** {@inheritDoc} */
    @Override
    public String getFilenameWithoutLayer ()
    {
        return this.filenameWithoutLayer.isEmpty () ? this.getFilename () : this.filenameWithoutLayer.get ();
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        if (this.sampleFile != null)
        {
            try (final InputStream in = new FileInputStream (this.sampleFile))
            {
                in.transferTo (outputStream);
            }
            return;
        }

        if (this.zipFile == null)
            return;

        try (final ZipFile zf = new ZipFile (this.zipFile))
        {
            final ZipEntry entry = zf.getEntry (this.filename);
            if (entry == null)
                throw new FileNotFoundException (String.format ("The sample '%s' was not found in the ZIP file.", this.filename));

            try (final InputStream in = zf.getInputStream (entry))
            {
                in.transferTo (outputStream);
            }
        }
    }


    /**
     * Check if the sample start / stop and the sample rate is set, if not read them from the sample
     * file.
     *
     * @throws IOException Could not read or parse the wave file
     */
    public void addMissingInfoFromWaveFile () throws IOException
    {
        final WaveFile waveFile;
        try
        {
            if (this.sampleFile != null)
                waveFile = new WaveFile (this.sampleFile, true);
            else
            {
                if (this.zipFile == null)
                    return;
                try (final ZipFile zf = new ZipFile (this.zipFile))
                {
                    final ZipEntry entry = zf.getEntry (this.filename);
                    if (entry == null)
                        return;
                    try (final InputStream in = zf.getInputStream (entry))
                    {
                        waveFile = new WaveFile (in, true);
                    }
                }
            }
        }
        catch (final IOException | ParseException ex)
        {
            throw new IOException (ex);
        }

        final FormatChunk formatChunk = waveFile.getFormatChunk ();
        final DataChunk dataChunk = waveFile.getDataChunk ();
        if (formatChunk == null || dataChunk == null)
            return;

        try
        {
            this.sampleRate = formatChunk.getSampleRate ();
            if (this.stop < 0)
            {
                if (this.start < 0)
                    this.start = 0;
                this.stop = dataChunk.calculateLength (formatChunk);
            }
        }
        catch (final CompressionNotSupportedException ex)
        {
            throw new IOException (ex);
        }
    }
}