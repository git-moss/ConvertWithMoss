// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.format.wav.WavSampleMetadata;
import de.mossgrabers.tools.ui.Functions;

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
public class DefaultSampleMetadata extends AbstractEnvelope implements ISampleMetadata
{
    protected String            filename;
    protected File              sampleFile;
    protected final File        zipFile;
    protected final File        zipEntry;

    protected boolean           isMonoFile              = false;
    protected int               sampleRate              = 44100;

    protected Optional<String>  combinedFilename        = Optional.empty ();
    protected Optional<String>  filenameWithoutLayer    = Optional.empty ();

    protected PlayLogic         playLogic               = PlayLogic.ALWAYS;
    protected int               start                   = -1;
    protected int               stop                    = -1;
    protected int               keyRoot                 = -1;
    protected int               keyLow                  = 0;
    protected int               keyHigh                 = 127;
    protected int               crossfadeNotesLow       = 0;
    protected int               crossfadeNotesHigh      = 0;
    protected int               velocityLow             = 1;
    protected int               velocityHigh            = 127;
    protected int               crossfadeVelocitiesLow  = 0;
    protected int               crossfadeVelocitiesHigh = 0;

    protected double            gain                    = 0;
    protected double            panorama                = 0;
    protected double            tune                    = 0;
    protected double            keyTracking             = 1.0;
    protected int               bendUp                  = 0;
    protected int               bendDown                = 0;
    protected boolean           isReversed              = false;
    protected IFilter           filter                  = null;

    protected List<ISampleLoop> loops                   = new ArrayList<> (1);


    /**
     * Constructor for a sample stored in the file system.
     *
     * @param sampleFile The file where the sample is stored
     */
    public DefaultSampleMetadata (final File sampleFile)
    {
        this (sampleFile.getName (), sampleFile, null, null);
    }


    /**
     * Constructor for a sample stored in a ZIP file.
     *
     * @param zipFile The ZIP file which contains the WAV files
     * @param zipEntry The relative path in the ZIP where the file is stored
     */
    public DefaultSampleMetadata (final File zipFile, final File zipEntry)
    {
        this (zipEntry.getName (), null, zipFile, zipEntry);
    }


    /**
     * Constructor for a sample stored in the file system.
     */
    protected DefaultSampleMetadata ()
    {
        this (null, null, null, null);
    }


    /**
     * Constructor.
     *
     * @param filename The name of the file where the sample is stored (must not contain any paths!)
     * @param sampleFile The file where the sample is stored
     * @param zipFile The ZIP file which contains the WAV files
     * @param zipEntry The relative path in the ZIP where the file is stored
     */
    protected DefaultSampleMetadata (final String filename, final File sampleFile, final File zipFile, final File zipEntry)
    {
        this.filename = filename;
        this.sampleFile = sampleFile;
        this.zipFile = zipFile;
        this.zipEntry = zipEntry;
    }


    /** {@inheritDoc} */
    @Override
    public File getFile ()
    {
        return this.sampleFile;
    }


    /**
     * Set the filename.
     *
     * @param filename The filename
     */
    public void setFilename (final String filename)
    {
        this.filename = filename;
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
    public void addLoop (final ISampleLoop loop)
    {
        this.loops.add (loop);
    }


    /** {@inheritDoc} */
    @Override
    public List<ISampleLoop> getLoops ()
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
    public void setPanorama (final double panorama)
    {
        this.panorama = panorama;
    }


    /** {@inheritDoc} */
    @Override
    public double getPanorama ()
    {
        return this.panorama;
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
    public int getBendUp ()
    {
        return this.bendUp;
    }


    /** {@inheritDoc} */
    @Override
    public void setBendUp (final int cents)
    {
        this.bendUp = cents;
    }


    /** {@inheritDoc} */
    @Override
    public int getBendDown ()
    {
        return this.bendDown;
    }


    /** {@inheritDoc} */
    @Override
    public void setBendDown (final int cents)
    {
        this.bendDown = cents;
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
    public Optional<IFilter> getFilter ()
    {
        return Optional.ofNullable (this.filter);
    }


    /** {@inheritDoc} */
    @Override
    public void setFilter (final IFilter filter)
    {
        this.filter = filter;
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
            final String path = this.zipEntry.getPath ().replace ('\\', '/');
            final ZipEntry entry = zf.getEntry (path);
            if (entry == null)
                throw new FileNotFoundException (Functions.getMessage ("IDS_NOTIFY_ERR_FILE_NOT_FOUND_IN_ZIP", path));

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
     * @param addRootKey If true, set the root key
     * @param addLoops If true, found loops are added
     * @throws IOException Could not read or parse the wave file
     */
    public void addMissingInfoFromWaveFile (final boolean addRootKey, final boolean addLoops) throws IOException
    {
        final WavSampleMetadata wavSampleMetadata;
        if (this.sampleFile != null)
            wavSampleMetadata = new WavSampleMetadata (this.sampleFile);
        else
            wavSampleMetadata = new WavSampleMetadata (this.zipFile, this.zipEntry);

        if (this.start < 0)
            this.start = 0;
        if (this.stop <= 0)
            this.stop = wavSampleMetadata.getStop ();

        // Read the this.keyRoot if not set...
        if (addRootKey && this.keyRoot == -1)
            this.keyRoot = wavSampleMetadata.getKeyRoot ();

        // Check for loops if not already present
        if (addLoops && this.loops.isEmpty ())
            this.loops.addAll (wavSampleMetadata.getLoops ());
    }
}