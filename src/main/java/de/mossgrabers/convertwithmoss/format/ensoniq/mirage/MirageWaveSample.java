// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ensoniq.mirage;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * The parameters of one wave-sample.
 *
 * @author Jürgen Moßgraber
 */
public class MirageWaveSample
{
    private static final int PAGE_SIZE = 256;

    /**
     * Parameter #60. This parameter will select the first wave-sample number used by the lowest key
     * of a keyboard half. Except in certain advanced sampling situations, this parameter is usually
     * set to "1". It can be used to make each program of a keyboard half play an entirely different
     * sound. This value is calculated to one offset into the PCM sample block.
     */
    public final int         sampleStart;

    /**
     * Parameter #61. Sampling parameter used to select the ending page in the Mirage memory for the
     * current wave-sample. The value for each wave-sample will be determined by the number of
     * wave-samples in the wave-table. This value is calculated to one offset into the PCM sample
     * block.
     */
    public final int         sampleEnd;

    /**
     * Parameter #62. A sampling parameter used to select the page in memory at which the looping
     * process will begin. To create an effective loop, the LOOP START should be a point sometime
     * after the initial attack portion of the wave-sample has been completed. This value is
     * calculated to one offset into the PCM sample block.
     */
    public final int         loopStart;

    /**
     * Parameter #63 / #64. #63 is a sampling parameter used to select the page where a loop will
     * end and recycle to connect with the LOOP START. To optimize the use of memory, the LOOP END
     * should be the last page before the WAVESAMPLE END. Parameter #64 will allow to adjust the
     * LOOP END in individual samples within the chosen loop end page. This fine adjustment will
     * help to match the loop end to the loop start as smoothly as possible. Both values are
     * calculated to one offset into the PCM sample block.
     */
    public final int         loopEnd;

    /**
     * Parameter #65. Turns the looping function on and off. When OFF, the wave-sample will play
     * back from beginning to end and then stop. When ON, the wave-sample will play from beginning
     * to LOOP END and then continue playing immediately from LOOP START, playing the loop segment
     * over and over as long as the key is depressed. Note that amplitude envelope parameters will
     * control the decay, sustain and release functions of the note. 0 = no loop 1 = forward loop.
     */
    public final int         loopMode;

    /**
     * Parameter #67. Alters the pitch of the current wave-sample in octave increments. Range: 0..7.
     * Default: 4
     */
    public final int         coarseTune;

    /**
     * Parameter #68. Alters the pitch of the current wave-sample in 1/20 semi-tone increments.
     * Range: 0-255. Default 0x80 (128).
     */
    public final int         fineTune;

    /**
     * Parameter #69. Adjusts the amplitude of the current wave-sample only. This allows to match
     * the volume levels of different wave-samples. Range: 0-63
     */
    public final int         relativeAmplitude;

    /**
     * Parameter #70. Adjusts the cutoff frequency of the filter for the current wave-sample,
     * relative to the other wave-samples in the wave-table. Range: 0..198
     */
    public final int         relativeFilterFreq;

    /**
     * Parameter #72. Selects the highest key that will use the current wave-sample. The TOP KEY
     * values will determine how many keys will use the wave-sample. The highest TOP KEY of the
     * lower keyboard will determine the split point. Range: 0-60.
     */
    public final int         topKey;


    /**
     * Constructor.
     * 
     * @param input The input from which to read the parameters
     * @throws IOException Could not read the parameters
     */
    public MirageWaveSample (final InputStream input) throws IOException
    {
        // These 4 values are only relevant for internal Mirage handling
        @SuppressWarnings("unused")
        int sampleStartPointer = StreamUtils.readUnsigned16 (input, false);
        @SuppressWarnings("unused")
        int sampleEndPointer = StreamUtils.readUnsigned16 (input, false);
        @SuppressWarnings("unused")
        int loopStartPointer = StreamUtils.readUnsigned16 (input, false);
        @SuppressWarnings("unused")
        int loopEndPointer = StreamUtils.readUnsigned16 (input, false);

        this.loopMode = input.read ();
        this.coarseTune = input.read ();
        this.fineTune = input.read ();

        this.relativeAmplitude = input.read ();
        this.relativeFilterFreq = input.read ();
        // 0..99 - Sets the upper limit of the cutoff frequency for the current wave-sample,
        // regardless of any other filter value (program, envelope, velocity).
        @SuppressWarnings("unused")
        int maximumFilterFreq = input.read ();

        this.topKey = input.read ();
        this.sampleStart = input.read () * PAGE_SIZE;
        this.sampleEnd = (input.read () + 1) * PAGE_SIZE;
        this.loopStart = input.read () * PAGE_SIZE;
        this.loopEnd = (input.read () + 1) * PAGE_SIZE + (0xFF - input.read ());
        @SuppressWarnings("unused")
        int freeRunFlag = input.read ();

        // Reserved
        input.skipNBytes (3);
    }
}
