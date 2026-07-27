// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;


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
    Optional<ISampleData> getSampleData ();


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
     * Get the number indicating this zone’s position in the round robin queue.
     *
     * @return The number starting with 1
     */
    int getSequencePosition ();


    /**
     * Set the number indicating this zone’s position in the round robin queue.
     *
     * @param sequencePosition The number starting with 1
     */
    void setSequencePosition (int sequencePosition);


    /**
     * Get the start of the playback.
     *
     * @return The start of the playback
     */
    int getStart ();


    /**
     * Get the start of the play-back.
     *
     * @param start The start of the play-back
     */
    void setStart (int start);


    /**
     * Get the end of the play-back.
     *
     * @return The end of the play-back, inclusive
     */
    int getStop ();


    /**
     * Get the stop of the play-back.
     *
     * @param stop The stop of the play-back, inclusive
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
     * Check if the sample is played back as a one-shot, which means that a note-off event is
     * ignored and the sample is always played back to its end (or until the amplitude envelope has
     * finished). This is different to the trigger type, which describes what starts the playback,
     * and different to a loop which is only played until the key is released
     * ({@link ISampleLoop#isLoopUntilRelease ()}).
     * <p>
     * Known encodings of this attribute are: Akai AKP loop type 1, Akai MPC <code>OneShot</code>,
     * Akai S900 flag 0x08, SFZ <code>loop_mode=one_shot</code>, Logic EXS24 zone flag
     * <code>oneshot</code>, Renoise <code>OneShotTrigger</code>, 1010music
     * <code>samtrigtype</code>, Roland ZenCore USPa loop mode 1, Roland S7xx loop mode 2, Yamaha
     * YSFC <code>receiveNoteOff=off</code> and Synclavier loop bits 4 and 5.
     *
     * @return True if the sample is played back as a one-shot
     */
    boolean isOneShot ();


    /**
     * Set the sample to be played back as a one-shot, which means that a note-off event is ignored
     * and the sample is always played back to its end.
     *
     * @param isOneShot True to play back the sample as a one-shot
     */
    void setOneShot (boolean isOneShot);


    /**
     * Get the exclusive group of the sample. All currently sounding notes which are assigned to the
     * same exclusive group are stopped when a note of that group is started. This is typically used
     * to model a closed hi-hat cutting off an open one.
     * <p>
     * Known encodings of this attribute are: SoundFont 2 generator 57 <code>exclusiveClass</code>,
     * DLS <code>keyGroup</code>, Akai MPC1000 <code>muteGroup</code>, Akai MPC60
     * <code>exclusive</code>, Yamaha YSFC <code>alternateGroup</code>, Kontakt
     * <code>voiceGroupIdx</code>, Logic EXS24 group <code>exclusive</code>, Renoise
     * <code>MuteGroupIndex</code>, TAL Sampler <code>mutegroup</code> and 1010music
     * <code>chokegrp</code>.
     *
     * @return The exclusive group or 0 if the sample is not assigned to any exclusive group
     */
    int getExclusiveGroup ();


    /**
     * Set the exclusive group of the sample.
     *
     * @param exclusiveGroup The exclusive group or 0 if the sample should not be assigned to any
     *            exclusive group
     */
    void setExclusiveGroup (int exclusiveGroup);


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
     * Get the number of notes to cross-fade on the lower end of the range.
     *
     * @return The number of notes to cross-fade (0-127)
     */
    int getNoteCrossfadeLow ();


    /**
     * Set the number of notes to cross-fade on the lower end of the range.
     *
     * @param crossfadeLow The number of notes to cross-fade (0-127)
     */
    void setNoteCrossfadeLow (int crossfadeLow);


    /**
     * Get the number of notes to cross-fade on the higher end of the range.
     *
     * @return The number of notes to cross-fade (0-127)
     */
    int getNoteCrossfadeHigh ();


    /**
     * Set the number of notes to cross-fade on the higher end of the range.
     *
     * @param crossfadeHigh The number of notes to cross-fade (0-127)
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
     * Get the number of velocity values to cross-fade on the lower end of the range.
     *
     * @return The number of of velocity values to cross-fade (0-127)
     */
    int getVelocityCrossfadeLow ();


    /**
     * Set the number of of velocity values to cross-fade on the lower end of the range.
     *
     * @param crossfadeLow The number of velocity values to cross-fade (0-127)
     */
    void setVelocityCrossfadeLow (int crossfadeLow);


    /**
     * Get the number of notes to cross-fade on the higher end of the range.
     *
     * @return The number of of velocity values to cross-fade (0-127)
     */
    int getVelocityCrossfadeHigh ();


    /**
     * Set the number of notes to cross-fade on the higher end of the range.
     *
     * @param crossfadeHigh The number of velocity values to cross-fade (0-127)
     */
    void setVelocityCrossfadeHigh (int crossfadeHigh);


    /**
     * Get the gain of the sample.
     *
     * @return The gain in dB, assume the range to be -Inf to 24dB
     */
    double getGain ();


    /**
     * Set the gain of the sample.
     *
     * @param gain The gain in dB, assume the range to be -Inf to 24dB
     */
    void setGain (double gain);


    /**
     * Get the panning.
     *
     * @return The panning in the range of [-1..1], -1 is full left, 0 centered and 1 full right
     */
    double getPanning ();


    /**
     * Set the panning in the range of [-1..1], -1 is full left, 0 centered and 1 full right.
     *
     * @param panning The panning
     */
    void setPanning (double panning);


    /**
     * Get the coarse and fine tuning of the sample.
     *
     * @return The tuning positive or negative semi-tones, which means that 0.01 represents 1 cent
     *         (1 semi-tone is 100 cent)
     */
    double getTuning ();


    /**
     * Set the coarse and fine tuning of the sample.
     *
     * @param tune The tuning positive or negative semi-tones, which means that 0.01 represents 1
     *            cent (1 semi-tone is 100 cent)
     */
    void setTuning (double tune);


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
     * Get the key tracking of the amplitude, which means how much the volume of the sample changes
     * depending on the played key relative to the root key. This is the amplitude counterpart of
     * {@link IFilter#getCutoffKeyTracking ()}.
     * <p>
     * Known encodings of this attribute are: Akai S1000 <code>keyToVolume</code>, Yamaha YSFC
     * <code>levelKeyFollowSensitivity</code>, Roland S7xx <code>levelKf</code>, Logic EXS24
     * <code>VOLUME_KEYSCALE</code> and DLS <code>CONN_SRC_KEYNUMBER</code> to
     * <code>CONN_DST_GAIN</code>.
     *
     * The unit is fixed to <b>1 decibel per key</b> relative to the root key, which means that 1.0
     * raises the volume by 1 dB for each key above the root key (12 dB per octave) and -1.0 lowers
     * it by the same amount. Fixing the unit is necessary because the value has no natural 1:1
     * reference like pitch or filter key tracking have; without it every format would pick its own
     * anchor and the values would not survive a conversion. Formats whose native law is not known
     * approximate their range linearly and say so at the conversion site.
     *
     * @return The key tracking in the range of [-1..1] representing -1 to +1 decibel per key. 0 is
     *         no tracking, positive values increase the volume for higher keys, negative values
     *         decrease it
     */
    double getAmplitudeKeyTracking ();


    /**
     * Set the key tracking of the amplitude.
     *
     * @param amplitudeKeyTracking The key tracking in the range of [-1..1] representing -1 to +1
     *            decibel per key. 0 is no tracking, positive values increase the volume for higher
     *            keys, negative values decrease it
     */
    void setAmplitudeKeyTracking (double amplitudeKeyTracking);


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
     * Get the velocity modulator for the amplitude.
     *
     * @return The modulator
     */
    IModulator getAmplitudeVelocityModulator ();


    /**
     * Get the envelope modulator for the amplitude.
     *
     * @return The modulator, never null
     */
    IEnvelopeModulator getAmplitudeEnvelopeModulator ();


    /**
     * Get the pitch modulator.
     *
     * @return The modulator, never null
     */
    IEnvelopeModulator getPitchEnvelopeModulator ();


    /**
     * Get the low frequency oscillator modulator for the pitch, which is commonly called vibrato. A
     * depth of zero means that there is no vibrato.
     *
     * @return The modulator, never null
     */
    ILfoModulator getPitchLfoModulator ();


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
     * @param sampleZone The source zone
     */
    void fillMetadata (ISampleZone sampleZone);
}
