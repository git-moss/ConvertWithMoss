// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core;

import java.io.File;
import java.util.Optional;


/**
 * Base class for a samples' metadata.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public abstract class AbstractSampleMetadata implements ISampleMetadata
{
    protected final File       file;
    protected final String     filename;

    protected int              start                   = -1;
    protected int              stop                    = -1;
    // TODO replace with enum
    protected boolean          hasLoop                 = false;
    protected int              loopStart               = -1;
    protected int              loopEnd                 = -1;
    protected int              keyRoot                 = 60;
    protected int              keyLow                  = 0;
    protected int              keyHigh                 = 127;
    protected int              crossfadeNotesLow       = 0;
    protected int              crossfadeNotesHigh      = 0;
    protected int              velocityLow             = 0;
    protected int              velocityHigh            = 127;
    protected int              crossfadeVelocitiesLow  = 0;
    protected int              crossfadeVelocitiesHigh = 0;
    protected boolean          isMonoFile              = false;
    protected Optional<String> combinedName            = Optional.empty ();
    protected Optional<String> nameWithoutLayer        = Optional.empty ();


    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     */
    protected AbstractSampleMetadata (final File file)
    {
        this (file.getName (), file);
    }


    /**
     * Constructor.
     *
     * @param filename The name of the file where the sample is stored
     */
    protected AbstractSampleMetadata (final String filename)
    {
        this (filename, null);
    }


    /**
     * Constructor.
     *
     * @param filename The name of the file where the sample is stored
     * @param file The file where the sample is stored
     */
    private AbstractSampleMetadata (final String filename, final File file)
    {
        this.filename = filename;
        this.file = file;
    }


    /** {@inheritDoc} */
    @Override
    public File getFile ()
    {
        return this.file;
    }


    /** {@inheritDoc} */
    @Override
    public String getFilename ()
    {
        return this.filename;
    }


    /** {@inheritDoc} */
    @Override
    public int getStart ()
    {
        return this.start;
    }


    /** {@inheritDoc} */
    @Override
    public void setStart (int start)
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
    public void setStop (int stop)
    {
        this.stop = stop;
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasLoop ()
    {
        return this.hasLoop;
    }


    /** {@inheritDoc} */
    @Override
    public void setHasLoop (boolean hasLoop)
    {
        this.hasLoop = hasLoop;
    }


    /** {@inheritDoc} */
    @Override
    public int getLoopStart ()
    {
        return this.loopStart;
    }


    /** {@inheritDoc} */
    @Override
    public void setLoopStart (int loopStart)
    {
        this.loopStart = loopStart;
    }


    /** {@inheritDoc} */
    @Override
    public int getLoopEnd ()
    {
        return this.loopEnd;
    }


    /** {@inheritDoc} */
    @Override
    public void setLoopEnd (int loopEnd)
    {
        this.loopEnd = loopEnd;
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
    public void setCombinedName (final String combinedName)
    {
        this.combinedName = Optional.ofNullable (combinedName);
    }


    /** {@inheritDoc} */
    @Override
    public Optional<String> getCombinedName ()
    {
        return this.combinedName;
    }


    /** {@inheritDoc} */
    @Override
    public Optional<String> getUpdatedFilename ()
    {
        return this.combinedName.isEmpty () ? Optional.ofNullable (this.getFilename ()) : this.combinedName;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isMono ()
    {
        return this.isMonoFile;
    }


    /** {@inheritDoc} */
    @Override
    public void setNameWithoutLayer (final String nameWithoutLayer)
    {
        this.nameWithoutLayer = Optional.ofNullable (nameWithoutLayer);
    }


    /** {@inheritDoc} */
    @Override
    public String getNameWithoutLayer ()
    {
        return this.nameWithoutLayer.isEmpty () ? this.getFilename () : this.nameWithoutLayer.get ();
    }
}
