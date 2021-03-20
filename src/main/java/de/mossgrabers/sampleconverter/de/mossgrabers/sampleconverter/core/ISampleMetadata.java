// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core;

import de.mossgrabers.sampleconverter.exception.CombinationNotPossibleException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;


/**
 * Metadata for a sample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public interface ISampleMetadata
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
    String getNameWithoutLayer ();


    /**
     * Set the name without the layer text.
     *
     * @param nameWithoutLayer The name without the layer text
     */
    void setNameWithoutLayer (String nameWithoutLayer);


    /**
     * Get the start of the playback.
     *
     * @return The start of the playback
     */
    int getStart ();


    /**
     * Get the end of the playback.
     *
     * @return The end of the playback
     */
    int getStop ();


    /**
     * Is there a loop section.
     *
     * @return True if there is a loop.
     */
    boolean hasLoop ();


    /**
     * Get the start of the loop.
     *
     * @return The start of the loop
     */
    int getLoopStart ();


    /**
     * Get the end of the loop.
     *
     * @return The end of the loop
     */
    int getLoopEnd ();


    /**
     * Get the lowest key of the keyrange to which the sample is mapped.
     *
     * @return The lowest key of the keyrange to which the sample is mapped
     */
    int getKeyLow ();


    /**
     * Set the lowest key of the keyrange to which the sample is mapped.
     *
     * @param keyLow The lowest key of the keyrange to which the sample is mapped
     */
    void setKeyLow (int keyLow);


    /**
     * Get the highest key of the keyrange to which the sample is mapped.
     *
     * @return The highest key of the keyrange to which the sample is mapped
     */
    int getKeyHigh ();


    /**
     * Set the highest key of the keyrange to which the sample is mapped.
     *
     * @param keyHigh The highest key of the keyrange to which the sample is mapped
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
     * Get the number of notes to crossfade on the lower end of the range.
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
     * Combines two mono files into a stereo file. Format and sample chunks must be identical.
     *
     * @param sample The other sample to include
     * @throws CombinationNotPossibleException Could not combine the wave files
     */
    void combine (ISampleMetadata sample) throws CombinationNotPossibleException;


    /**
     * If the encapsulated wavefile got combined with another one, it is not stored yet. This sets
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
     * Write the data of the sample to the given output stream.
     *
     * @param outputStream The stream to where to write the data
     * @throws IOException Could not write the data
     */
    void writeSample (OutputStream outputStream) throws IOException;
}
