// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;


/**
 * Default implementation of a sample zone.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultSampleZone implements ISampleZone
{
    protected String             name;
    protected PlayLogic          playLogic                  = PlayLogic.ALWAYS;
    protected int                sequencePosition           = -1;
    protected TriggerType        triggerType                = TriggerType.ATTACK;
    protected int                start                      = -1;
    protected int                stop                       = -1;
    protected int                keyRoot                    = -1;
    protected int                keyLow                     = 0;
    protected int                keyHigh                    = 127;
    protected int                noteCrossfadeLow           = 0;
    protected int                noteCrossfadeHigh          = 0;
    protected int                velocityLow                = 1;
    protected int                velocityHigh               = 127;
    protected int                velocityCrossfadeLow       = 0;
    protected int                velocityCrossfadeHigh      = 0;

    protected double             gain                       = 0;
    protected double             panorama                   = 0;
    protected double             tune                       = 0;
    protected double             keyTracking                = 1.0;
    protected int                bendUp                     = 0;
    protected int                bendDown                   = 0;
    protected boolean            isReversed                 = false;
    protected IModulator         amplitudeVelocityModulator = new DefaultModulator (1);
    protected IEnvelopeModulator amplitudeEnvelopeModulator = new DefaultEnvelopeModulator (1);
    protected IEnvelopeModulator pitchModulator             = new DefaultEnvelopeModulator (0);
    protected IFilter            filter                     = null;

    protected List<ISampleLoop>  loops                      = new ArrayList<> (1);

    private ISampleData          sampleData;


    /**
     * Constructor.
     *
     * @param name The name of the zone
     * @param sampleData The sample data which is referenced by the zone
     */
    public DefaultSampleZone (final String name, final ISampleData sampleData)
    {
        this.name = name;
        this.setSampleData (sampleData);
    }


    /**
     * Constructor. Copies all metadata from the given source zone.
     *
     * @param zone The zone from which to copy the metadata
     */
    public DefaultSampleZone (final ISampleZone zone)
    {
        this.name = zone.getName ();
        this.fillMetadata (zone);
    }


    /**
     * Constructor for setting the sample data later.
     */
    public DefaultSampleZone ()
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public String getName ()
    {
        return this.name;
    }


    /** {@inheritDoc} */
    @Override
    public void setName (final String name)
    {
        this.name = name;
    }


    /** {@inheritDoc} */
    @Override
    public ISampleData getSampleData ()
    {
        return this.sampleData;
    }


    /** {@inheritDoc} */
    @Override
    public void setSampleData (final ISampleData sampleData)
    {
        this.sampleData = sampleData;
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
    public int getSequencePosition ()
    {
        return this.sequencePosition;
    }


    /** {@inheritDoc} */
    @Override
    public void setSequencePosition (final int sequencePosition)
    {
        this.sequencePosition = sequencePosition;
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
    public TriggerType getTrigger ()
    {
        return this.triggerType;
    }


    /** {@inheritDoc} */
    @Override
    public void setTrigger (final TriggerType trigger)
    {
        this.triggerType = trigger;
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
        return this.noteCrossfadeLow;
    }


    /** {@inheritDoc} */
    @Override
    public void setNoteCrossfadeLow (final int crossfadeLow)
    {
        this.noteCrossfadeLow = crossfadeLow;
    }


    /** {@inheritDoc} */
    @Override
    public int getNoteCrossfadeHigh ()
    {
        return this.noteCrossfadeHigh;
    }


    /** {@inheritDoc} */
    @Override
    public void setNoteCrossfadeHigh (final int crossfadeHigh)
    {
        this.noteCrossfadeHigh = crossfadeHigh;
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
        return this.velocityCrossfadeLow;
    }


    /** {@inheritDoc} */
    @Override
    public void setVelocityCrossfadeLow (final int crossfadeLow)
    {
        this.velocityCrossfadeLow = crossfadeLow;
    }


    /** {@inheritDoc} */
    @Override
    public int getVelocityCrossfadeHigh ()
    {
        return this.velocityCrossfadeHigh;
    }


    /** {@inheritDoc} */
    @Override
    public void setVelocityCrossfadeHigh (final int crossfadeHigh)
    {
        this.velocityCrossfadeHigh = crossfadeHigh;
    }


    /** {@inheritDoc} */
    @Override
    public void setGain (final double gain)
    {
        this.gain = Math.clamp (gain, 0.125, 24.0);
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
        this.panorama = Math.clamp (panorama, -1, 1);
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
    public IModulator getAmplitudeVelocityModulator ()
    {
        return this.amplitudeVelocityModulator;
    }


    /** {@inheritDoc} */
    @Override
    public IEnvelopeModulator getAmplitudeEnvelopeModulator ()
    {
        return this.amplitudeEnvelopeModulator;
    }


    /** {@inheritDoc} */
    @Override
    public IEnvelopeModulator getPitchModulator ()
    {
        return this.pitchModulator;
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
    public void fillMetadata (final ISampleZone other)
    {
        this.playLogic = other.getPlayLogic ();
        this.triggerType = other.getTrigger ();
        this.start = other.getStart ();
        this.stop = other.getStop ();
        this.keyRoot = other.getKeyRoot ();
        this.keyLow = other.getKeyLow ();
        this.keyHigh = other.getKeyHigh ();
        this.noteCrossfadeLow = other.getNoteCrossfadeLow ();
        this.noteCrossfadeHigh = other.getNoteCrossfadeHigh ();
        this.velocityLow = other.getVelocityLow ();
        this.velocityHigh = other.getVelocityHigh ();
        this.velocityCrossfadeLow = other.getVelocityCrossfadeLow ();
        this.velocityCrossfadeHigh = other.getVelocityCrossfadeHigh ();
        this.gain = other.getGain ();
        this.panorama = other.getPanorama ();
        this.tune = other.getTune ();
        this.keyTracking = other.getKeyTracking ();
        this.bendUp = other.getBendUp ();
        this.bendDown = other.getBendDown ();
        this.isReversed = other.isReversed ();
        this.amplitudeEnvelopeModulator = other.getAmplitudeEnvelopeModulator ();
        this.pitchModulator = other.getPitchModulator ();
        final Optional<IFilter> filterOpt = other.getFilter ();
        this.filter = filterOpt.isPresent () ? filterOpt.get () : null;

        this.loops.clear ();
        this.loops.addAll (other.getLoops ());
    }
}