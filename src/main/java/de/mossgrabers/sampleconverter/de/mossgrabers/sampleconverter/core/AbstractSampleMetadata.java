// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core;

import java.io.File;


/**
 * Base class for a samples' metadata.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public abstract class AbstractSampleMetadata implements ISampleMetadata
{
    protected final File file;

    protected int        start;
    protected int        stop;
    protected boolean    hasLoop;
    protected int        loopStart;
    protected int        loopEnd;
    protected int        keyLow;
    protected int        keyRoot;
    protected int        keyHigh;
    protected int        crossfadeLow;
    protected int        crossfadeHigh;
    protected String     combinedName;
    protected boolean    isMonoFile;
    protected String     nameWithoutLayer;


    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     */
    public AbstractSampleMetadata (final File file)
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
    public int getCrossfadeLow ()
    {
        return this.crossfadeLow;
    }


    /** {@inheritDoc} */
    @Override
    public void setCrossfadeLow (final int crossfadeLow)
    {
        this.crossfadeLow = crossfadeLow;
    }


    /** {@inheritDoc} */
    @Override
    public int getCrossfadeHigh ()
    {
        return this.crossfadeHigh;
    }


    /** {@inheritDoc} */
    @Override
    public void setCrossfadeHigh (final int crossfadeHigh)
    {
        this.crossfadeHigh = crossfadeHigh;
    }


    /** {@inheritDoc} */
    @Override
    public void setCombinedName (final String combinedName)
    {
        this.combinedName = combinedName;
    }


    /** {@inheritDoc} */
    @Override
    public String getCombinedName ()
    {
        return this.combinedName;
    }


    /** {@inheritDoc} */
    @Override
    public String getUpdatedFilename ()
    {
        return this.combinedName == null ? this.getFilename () : this.combinedName;
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
        this.nameWithoutLayer = nameWithoutLayer;
    }


    /** {@inheritDoc} */
    @Override
    public String getNameWithoutLayer ()
    {
        return this.nameWithoutLayer == null ? this.getFilename () : this.nameWithoutLayer;
    }
}
