// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;

import java.util.List;
import java.util.Optional;


/**
 * The settings of a zone (key and velocity range) including the reference to the sample data.
 *
 * @author Jürgen Moßgraber
 */
public interface ISampleZone
{
    /**
     * Get the name of the zone or the referenced sample if there is no zone name.
     *
     * @return The name
     */
    String getName ();


    /**
     * Set the name of the zone.
     *
     * @param name The name
     */
    void setName (String name);


    /**
     * Get the referenced sample data.
     *
     * @return The referenced sample data
     */
    ISampleData getSampleData ();


    /**
     * Set the referenced sample data.
     *
     * @param sampleData The sample data to reference
     */
    void setSampleData (final ISampleData sampleData);


    /**
     * When is the sample played back?
     *
     * @return The play logic to apply
     */
    PlayLogic getPlayLogic ();


    /**
     * Set when is the sample should be played back.
     *
     * @param playLogic The play logic to apply
     */
    void setPlayLogic (PlayLogic playLogic);


    /**
     * Get the start of the playback.
     *
     * @return The start of the playback
     */
    int getStart ();


    /**
     * Get the start of the playback.
     *
     * @param start The start of the playback
     */
    void setStart (int start);


    /**
     * Get the end of the playback.
     *
     * @return The end of the playback
     */
    int getStop ();


    /**
     * Get the stop of the playback.
     *
     * @param stop The stop of the playback
     */
    void setStop (int stop);


    /**
     * Add a loop.
     *
     * @param loop The loop to add
     */
    void addLoop (ISampleLoop loop);


    /**
     * Get all loops of the sample.
     *
     * @return The loops, if any
     */
    List<ISampleLoop> getLoops ();


    /**
     * Get the event that triggers the playback of the sample.
     *
     * @return The trigger type
     */
    TriggerType getTrigger ();


    /**
     * Set the event that triggers the playback of the sample.
     *
     * @param trigger The trigger type
     */
    void setTrigger (TriggerType trigger);


    /**
     * Get the lowest key of the key-range to which the sample is mapped.
     *
     * @return The lowest key of the key-range to which the sample is mapped
     */
    int getKeyLow ();


    /**
     * Set the lowest key of the key-range to which the sample is mapped.
     *
     * @param keyLow The lowest key of the key-range to which the sample is mapped
     */
    void setKeyLow (int keyLow);


    /**
     * Get the highest key of the key-range to which the sample is mapped.
     *
     * @return The highest key of the key-range to which the sample is mapped
     */
    int getKeyHigh ();


    /**
     * Set the highest key of the key-range to which the sample is mapped.
     *
     * @param keyHigh The highest key of the key-range to which the sample is mapped
     */
    void setKeyHigh (int keyHigh);


    /**
     * Get the MIDI root note of the sample.
     *
     * @return The MIDI root note of the sample
     */
    int getKeyRoot ();


    /**
     * Set the MIDI root note of the sample.
     *
     * @param keyRoot The MIDI root note of the sample
     */
    void setKeyRoot (int keyRoot);


    /**
     * Get the@Override number of notes to crossfade on the lower end of the range.
     *
     * @return The number of notes to crossfade (0-127)
     */
    int getNoteCrossfadeLow ();


    /**
     * Set the number of notes to crossfade on the lower end of the range.
     *
     * @param crossfadeLow The number of notes to crossfade (0-127)
     */
    void setNoteCrossfadeLow (int crossfadeLow);


    /**
     * Get the number of notes to crossfade on the higher end of the range.
     *
     * @return The number of notes to crossfade (0-127)
     */
    int getNoteCrossfadeHigh ();


    /**
     * Set the number of notes to crossfade on the higher end of the range.
     *
     * @param crossfadeHigh The number of notes to crossfade (0-127)
     */
    void setNoteCrossfadeHigh (int crossfadeHigh);


    /**
     * Get the lowest velocity of the velocity range to which the sample is mapped.
     *
     * @return The lowest velocity of the velocity range to which the sample is mapped
     */
    int getVelocityLow ();


    /**
     * Set the lowest velocity of the velocity range to which the sample is mapped.
     *
     * @param velocityLow The lowest velocity of the velocity range to which the sample is mapped
     */
    void setVelocityLow (int velocityLow);


    /**
     * Get the highest velocity of the velocity range to which the sample is mapped.
     *
     * @return The highest velocity of the velocity range to which the sample is mapped
     */
    int getVelocityHigh ();


    /**
     * Set the highest velocity of the velocity range to which the sample is mapped.
     *
     * @param velocityHigh The highest velocity of the velocity range to which the sample is mapped
     */
    void setVelocityHigh (final int velocityHigh);


    /**
     * Get the number of velocity values to crossfade on the lower end of the range.
     *
     * @return The number of of velocity values to crossfade (0-127)
     */
    int getVelocityCrossfadeLow ();


    /**
     * Set the number of of velocity values to crossfade on the lower end of the range.
     *
     * @param crossfadeLow The number of velocity values to crossfade (0-127)
     */
    void setVelocityCrossfadeLow (int crossfadeLow);


    /**
     * Get the number of notes to crossfade on the higher end of the range.
     *
     * @return The number of of velocity values to crossfade (0-127)
     */
    int getVelocityCrossfadeHigh ();


    /**
     * Set the number of notes to crossfade on the higher end of the range.
     *
     * @param crossfadeHigh The number of velocity values to crossfade (0-127)
     */
    void setVelocityCrossfadeHigh (int crossfadeHigh);


    /**
     * Get the gain of the sample.
     *
     * @return The gain in the range of [-12 .. 12] dB
     */
    double getGain ();


    /**
     * Set the gain of the sample.
     *
     * @param gain The gain in the range of [-12 .. 12] dB
     */
    void setGain (double gain);


    /**
     * Get the panorama.
     *
     * @return The panorama in the range of [-1..1], -1 is full left, 0 centered and 1 full right
     */
    double getPanorama ();


    /**
     * Set the panorama in the range of [-1..1], -1 is full left, 0 centered and 1 full right.
     *
     * @param panorama The panorama
     */
    void setPanorama (double panorama);


    /**
     * Get the key tracking of the sample.
     *
     * @return The tuning positive or negative semitones, which means that 0.01 represents 1 cent (1
     *         semitone is 100 cent)
     */
    double getTune ();


    /**
     * Set the fine tuning of the sample.
     *
     * @param tune The tuning positive or negative semitones, which means that 0.01 represents 1
     *            cent (1 semitone is 100 cent)
     */
    void setTune (double tune);


    /**
     * Get the key tracking of the sample.
     *
     * @return The key tracking in the range of [0..1] representing [0..100] %. 100% is full
     *         tracking, 0% is no tracking
     */
    double getKeyTracking ();


    /**
     * Set the key tracking of the sample.
     *
     * @param keyTracking The key tracking in the range of [0..1] representing [0..100] %. 100% is
     *            full tracking, 0% is no tracking
     */
    void setKeyTracking (double keyTracking);


    /**
     * Get the sample playback direction.
     *
     * @return True if reversed
     */
    boolean isReversed ();


    /**
     * Set the sample playback direction.
     *
     * @param isReversed True to playback the sample reversed
     */
    void setReversed (boolean isReversed);


    /**
     * Get the amplitude modulator.
     *
     * @return The modulator, never null
     */
    IModulator getAmplitudeModulator ();


    /**
     * Get the pitch modulator.
     *
     * @return The modulator, never null
     */
    IModulator getPitchModulator ();


    /**
     * Get pitch bend up value.
     *
     * @return The cents to bend down (if negative) or up in cents (-9600 to 9600)
     */
    int getBendUp ();


    /**
     * Set pitch bend up value.
     *
     * @param cents The cents to bend down (if negative) or up in cents (-9600 to 9600)
     */
    void setBendUp (int cents);


    /**
     * Get pitch bend down value.
     *
     * @return The cents to bend down (if negative) or up in cents (-9600 to 9600)
     */
    int getBendDown ();


    /**
     * Set pitch bend down value.
     *
     * @param cents The cents to bend down (if negative) or up in cents (-9600 to 9600)
     */
    void setBendDown (int cents);


    /**
     * Get a filter.
     *
     * @return The filter
     */
    Optional<IFilter> getFilter ();


    /**
     * Set a filter.
     *
     * @param filter The filter to set
     */
    void setFilter (IFilter filter);


    /**
     * Fill in the data from another sample metadata object. The includes all data except the file
     * names and file data.
     *
     * @param sampleMetadata The data source
     */
    void fillMetadata (ISampleZone sampleMetadata);
}
