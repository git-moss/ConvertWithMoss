// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
}
