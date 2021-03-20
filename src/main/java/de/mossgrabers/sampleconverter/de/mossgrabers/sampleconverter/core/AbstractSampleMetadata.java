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

    protected int              start;
    protected int              stop;
    protected boolean          hasLoop;
    protected int              loopStart;
    protected int              loopEnd;
    protected int              keyRoot;
    protected int              keyLow;
    protected int              keyHigh;
    protected int              crossfadeNotesLow;
    protected int              crossfadeNotesHigh;
    protected int              velocityLow;
    protected int              velocityHigh;
    protected int              crossfadeVelocitiesLow;
    protected int              crossfadeVelocitiesHigh;
    protected boolean          isMonoFile;
    protected Optional<String> combinedName;
    protected Optional<String> nameWithoutLayer;


    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     */
    protected AbstractSampleMetadata (final File file)
    {
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
        return this.file.getName ();
    }


    /** {@inheritDoc} */
    @Override
    public int getStart ()
    {
        return this.start;
    }


    /** {@inheritDoc} */
    @Override
    public int getStop ()
    {
        return this.stop;
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasLoop ()
    {
        return this.hasLoop;
    }


    /** {@inheritDoc} */
    @Override
    public int getLoopStart ()
    {
        return this.loopStart;
    }


    /** {@inheritDoc} */
    @Override
    public int getLoopEnd ()
    {
        return this.loopEnd;
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
