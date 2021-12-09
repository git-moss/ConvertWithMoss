// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.model;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;


/**
 * Metadata for a sample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public interface ISampleMetadata extends IEnvelopeAccess
{
    /**
     * Get the file where the sample is stored.
     *
     * @return The file where the sample is stored
     */
    File getFile ();


    /**
     * Get the filename.
     *
     * @return The name of the file
     */
    String getFilename ();


    /**
     * Get the name without the layer text.
     *
     * @return The name without the layer text
     */
    String getFilenameWithoutLayer ();


    /**
     * Set the name without the layer text.
     *
     * @param nameWithoutLayer The name without the layer text
     */
    void setFilenameWithoutLayer (String nameWithoutLayer);


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
    void addLoop (SampleLoop loop);


    /**
     * Get all loops of the sample.
     *
     * @return The loops, if any
     */
    List<SampleLoop> getLoops ();


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
     * Get the key tracking of the sample.
     *
     * @return The tuning in the range of [-1 .. 1] representing [-100 .. 100] cent
     */
    double getTune ();


    /**
     * Set the fine tuning of the sample.
     *
     * @param tune The tuning in the range of [-1 .. 1] representing [-100..100] cent
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
     * If the encapsulated wave file got combined with another one, it is not stored yet. This sets
     * the new name and indicates that it is not stored yet.
     *
     * @param combinedName The name to use for the new combined file
     */
    void setCombinedName (String combinedName);


    /**
     * Get the combined file name.
     *
     * @return The name
     */
    Optional<String> getCombinedName ();


    /**
     * Get the filename resulting from combining two mono file or the original filename if not
     * combination did happen.
     *
     * @return The updated name
     */
    Optional<String> getUpdatedFilename ();


    /**
     * Returns true if the sample is mono (otherwise stereo).
     *
     * @return True if mono
     */
    boolean isMono ();


    /**
     * The number of sample slices per second. This value is unaffected by the number of channels.
     *
     * @return The four bytes converted to an integer
     */
    int getSampleRate ();


    /**
     * Write the data of the sample to the given output stream. The implementation must write a
     * fully well-formed WAV file.
     *
     * @param outputStream The stream to where to write the data
     * @throws IOException Could not write the data
     */
    void writeSample (OutputStream outputStream) throws IOException;
}
