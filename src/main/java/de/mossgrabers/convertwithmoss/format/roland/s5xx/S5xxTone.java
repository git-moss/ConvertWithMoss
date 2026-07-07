// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * One 128-byte tone from the floppy disk tone area.
 *
 * @author Jürgen Moßgraber
 */
public class S5xxTone
{
    private final S5xxToneList listEntry;
    private final String       name;

    // Wave Data
    private final int          sourceTone;
    private final int          orgSubTone;
    private final int          samplingFrequency;
    private final int          origKeyNumber;
    private final int          waveBank;
    private final int          waveSegmentTop;
    private final int          waveSegmentLength;

    // Play-back
    private final int          startPoint;
    private final int          endPoint;
    private final int          loopMode;
    private final int          loopPoint;
    private final int          loopLength;
    private final int          loopTune;

    // LFO
    @SuppressWarnings("unused")
    private final int          tvaLfoDepth;
    @SuppressWarnings("unused")
    private final int          lfoRate;
    @SuppressWarnings("unused")
    private final int          lfoSync;
    @SuppressWarnings("unused")
    private final int          lfoDelay;
    @SuppressWarnings("unused")
    private final int          lfoMode;
    @SuppressWarnings("unused")
    private final int          oscLfoDepth;
    @SuppressWarnings("unused")
    private final int          lfoPolarity;
    @SuppressWarnings("unused")
    private final int          lfoOffset;

    // Amplifier
    private final int          level;
    private final int          outputAssign;
    private final int          tvaEnvSustainPoint;
    private final int          tvaEnvEndPoint;
    private final int []       tvaEnvLevels = new int [8];
    private final int []       tvaEnvRates  = new int [8];
    @SuppressWarnings("unused")
    private final int          tvaEnvKeyRate;
    @SuppressWarnings("unused")
    private final int          tvaZoom;
    @SuppressWarnings("unused")
    private final int          tvaLevelCurve;
    @SuppressWarnings("unused")
    private final int          envVelRate;

    // Pitch
    private final int          transpose;
    private final int          fineTune;
    private final int          pitchFollow;

    // Modulation
    @SuppressWarnings("unused")
    private final int          benderSwitch;
    @SuppressWarnings("unused")
    private final int          afterTouchSwitch;

    // Filter
    private final int          tvfCutOff;
    private final int          tvfResonance;
    private final int          tvfEgDepth;
    private final int          tvfEgPolarity;
    private final int          tvfSwitch;
    private final int          tvfEnvSustainPoint;
    private final int          tvfEnvEndPoint;
    private final int []       tvfEnvLevels = new int [8];
    private final int []       tvfEnvRates  = new int [8];
    @SuppressWarnings("unused")
    private final int          tvfVelocityRateFollow;
    @SuppressWarnings("unused")
    private final int          tvfKeyRateFollow;
    @SuppressWarnings("unused")
    private final int          tvfLevelCurve;
    private final int          tvfKeyFollow;
    @SuppressWarnings("unused")
    private final int          tvfLfoDepth;
    @SuppressWarnings("unused")
    private final int          tvfZoom;

    // Recording configuration
    @SuppressWarnings("unused")
    private final int          recThreshold;
    @SuppressWarnings("unused")
    private final int          recPreTrigger;
    @SuppressWarnings("unused")
    private final int          recSamplingFrequency;
    @SuppressWarnings("unused")
    private final int          recStartPoint;
    @SuppressWarnings("unused")
    private final int          recEndPoint;
    @SuppressWarnings("unused")
    private final int          recLoopPoint;
    @SuppressWarnings("unused")
    private final int          zoomT;
    @SuppressWarnings("unused")
    private final int          zoomL;
    @SuppressWarnings("unused")
    private final int          copySource;


    /**
     * Constructor.
     *
     * @param listEntry The list entry of the tone
     * @param input The input stream to read from
     * @throws IOException Could not read the tone
     */
    public S5xxTone (final S5xxToneList listEntry, final InputStream input) throws IOException
    {
        this.listEntry = listEntry;

        this.name = StreamUtils.readAscii (input, 8).replace ((char) 0, ' ').replace ((char) 16, '.').trim ();

        // Dummy on S-50
        this.outputAssign = StreamUtils.readUnsigned8 (input);
        this.sourceTone = StreamUtils.readUnsigned8 (input);
        this.orgSubTone = StreamUtils.readUnsigned8 (input);
        this.samplingFrequency = StreamUtils.readUnsigned8 (input);
        this.origKeyNumber = StreamUtils.readUnsigned8 (input);

        this.waveBank = StreamUtils.readUnsigned8 (input);
        this.waveSegmentTop = StreamUtils.readUnsigned8 (input);
        this.waveSegmentLength = StreamUtils.readUnsigned8 (input);

        this.startPoint = StreamUtils.readUnsigned24 (input, true);
        this.endPoint = StreamUtils.readUnsigned24 (input, true);
        this.loopPoint = StreamUtils.readUnsigned24 (input, true);
        this.loopMode = StreamUtils.readUnsigned8 (input);

        // Dummy on S-50
        this.tvaLfoDepth = StreamUtils.readUnsigned8 (input);
        input.skipNBytes (1);
        this.lfoRate = StreamUtils.readUnsigned8 (input);
        // Dummy on S-50
        this.lfoSync = StreamUtils.readUnsigned8 (input);
        this.lfoDelay = StreamUtils.readUnsigned8 (input);
        input.skipNBytes (1);
        this.lfoMode = StreamUtils.readUnsigned8 (input);
        this.oscLfoDepth = StreamUtils.readUnsigned8 (input);
        // Dummy on S-50
        this.lfoPolarity = StreamUtils.readUnsigned8 (input);
        // Dummy on S-50
        this.lfoOffset = StreamUtils.readUnsigned8 (input);

        // Dummy on S-50
        this.transpose = StreamUtils.readSigned8 (input);
        this.fineTune = StreamUtils.readSigned8 (input);

        // Dummy on S-50 start ->
        this.tvfCutOff = StreamUtils.readUnsigned8 (input);
        this.tvfResonance = StreamUtils.readUnsigned8 (input);
        this.tvfKeyFollow = StreamUtils.readUnsigned8 (input);
        input.skipNBytes (1);
        this.tvfLfoDepth = StreamUtils.readUnsigned8 (input);
        this.tvfEgDepth = StreamUtils.readUnsigned8 (input);
        this.tvfEgPolarity = StreamUtils.readUnsigned8 (input);
        this.tvfLevelCurve = StreamUtils.readUnsigned8 (input);
        this.tvfKeyRateFollow = StreamUtils.readUnsigned8 (input);
        this.tvfVelocityRateFollow = StreamUtils.readUnsigned8 (input);
        this.tvfZoom = StreamUtils.readSigned8 (input);
        this.tvfSwitch = StreamUtils.readUnsigned8 (input);

        this.benderSwitch = StreamUtils.readUnsigned8 (input);
        // <- Dummy on S-50 end

        this.tvaEnvSustainPoint = StreamUtils.readUnsigned8 (input);
        this.tvaEnvEndPoint = StreamUtils.readUnsigned8 (input);
        for (int i = 0; i < 8; i++)
        {
            this.tvaEnvLevels[i] = StreamUtils.readUnsigned8 (input);
            this.tvaEnvRates[i] = StreamUtils.readUnsigned8 (input);
        }

        input.skipNBytes (1);
        this.tvaEnvKeyRate = StreamUtils.readUnsigned8 (input);
        this.level = StreamUtils.readUnsigned8 (input);

        this.envVelRate = StreamUtils.readUnsigned8 (input);
        this.recThreshold = StreamUtils.readUnsigned8 (input);
        this.recPreTrigger = StreamUtils.readUnsigned8 (input);
        this.recSamplingFrequency = StreamUtils.readUnsigned8 (input);

        this.recStartPoint = StreamUtils.readUnsigned24 (input, true);
        this.recEndPoint = StreamUtils.readUnsigned24 (input, true);
        this.recLoopPoint = StreamUtils.readUnsigned24 (input, true);

        this.zoomT = StreamUtils.readUnsigned8 (input);
        this.zoomL = StreamUtils.readUnsigned8 (input);
        this.copySource = StreamUtils.readUnsigned8 (input);
        this.loopTune = StreamUtils.readSigned8 (input);
        this.tvaLevelCurve = StreamUtils.readUnsigned8 (input);

        input.skipNBytes (12);

        this.loopLength = StreamUtils.readUnsigned24 (input, true);

        this.pitchFollow = StreamUtils.readUnsigned8 (input);
        this.tvaZoom = StreamUtils.readUnsigned8 (input);

        // Dummy on S-50 start ->
        this.tvfEnvSustainPoint = StreamUtils.readUnsigned8 (input);
        this.tvfEnvEndPoint = StreamUtils.readUnsigned8 (input);

        for (int i = 0; i < 8; i++)
        {
            this.tvfEnvLevels[i] = StreamUtils.readUnsigned8 (input);
            this.tvfEnvRates[i] = StreamUtils.readUnsigned8 (input);
        }

        this.afterTouchSwitch = StreamUtils.readUnsigned8 (input);
        input.skipNBytes (2);
        // <- Dummy on S-50 end
    }


    /**
     * Get the name of the tone.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the list entry.
     *
     * @return The list entry
     */
    public S5xxToneList getListEntry ()
    {
        return this.listEntry;
    }


    /**
     * Get the source tone, if any. Valid if #getOrigSubTone() returns 1.
     *
     * @return The source tone in the range of 0..31
     */
    public int getSourceTone ()
    {
        return this.sourceTone;
    }


    /**
     * Is this an original tone or is it referencing another one (sub-tone)?
     *
     * @return 0 = original, 1 = sub-tone
     */
    public int getOrigSubTone ()
    {
        return this.orgSubTone;
    }


    /**
     * Get the sample frequency.
     *
     * @return 0 = 30kHz, 1 = 15kHz
     */
    public int getSamplingFrequency ()
    {
        return this.samplingFrequency;
    }


    /**
     * Get the original (root) key.
     *
     * @return The key in the range of 11..120
     */
    public int getOrigKeyNumber ()
    {
        return this.origKeyNumber;
    }


    /**
     * Get the wave bank A or B that contains the referenced sample.
     *
     * @return 0 = A, 1 = B, 2 = Unused
     */
    public int getWaveBank ()
    {
        return this.waveBank;
    }


    /**
     * Get the segment which contains the sample.
     *
     * @return The segment in the range of 0..17
     */
    public int getWaveSegmentTop ()
    {
        return this.waveSegmentTop;
    }


    /**
     * Get the number of segments which the sample spans across.
     *
     * @return The number of segments in the range of 0..18
     */
    public int getWaveSegmentLength ()
    {
        return this.waveSegmentLength;
    }


    /**
     * Get the start-point of the sample.
     *
     * @return The start-point in sample frames in the range of 0..221180
     */
    public int getStartPoint ()
    {
        return this.startPoint;
    }


    /**
     * Get the end-point of the sample.
     *
     * @return The end-point in sample frames in the range of 0..221184
     */
    public int getEndPoint ()
    {
        return this.endPoint;
    }


    /**
     * Get the loop-point of the sample.
     *
     * @return The loop-point in sample frames in the range of 0..221184
     */
    public int getLoopPoint ()
    {
        return this.loopPoint;
    }


    /**
     * Get the loop mode. 0 = Forward, 1 = Alternating, 2 = One-Shot, 3 = Reverse.
     *
     * @return The loop mode in the range of 0..3
     */
    public int getLoopMode ()
    {
        return this.loopMode;
    }


    /**
     * Get the length of the loop.
     *
     * @return The loop length
     */
    public int getLoopLength ()
    {
        return this.loopLength;
    }


    /**
     * Get the loop tuning.
     *
     * @return The loop tuning
     */
    public int getLoopTune ()
    {
        return this.loopTune;
    }


    /**
     * Get the transpose (this might be actually "pitch shift").
     *
     * @return The transposition value in the range of -24..24 semi-tones, not on S-50
     */
    public int getTranspose ()
    {
        return this.transpose;
    }


    /**
     * Get the fine tuning.
     *
     * @return The fine tuning in the range of -64..63
     */
    public int getFineTune ()
    {
        return this.fineTune;
    }


    /**
     * Get the pitch follow.
     *
     * @return 0 = Off, 1 = On
     */
    public int getPitchFollow ()
    {
        return this.pitchFollow;
    }


    /**
     * Get the output assignment. Not available on S-50 and always 0.
     *
     * @return The output assignment in the range of 0..7
     */
    public int getOutputAssign ()
    {
        return this.outputAssign;
    }


    /**
     * Get the level
     *
     * @return The level in the range of 0..127
     */
    public int getLevel ()
    {
        return this.level;
    }


    /**
     * Get the index of the level which is used for the sustain level.
     *
     * @return The TVA envelope sustain point in the range of 1..7
     */
    public int getTvaEnvSustainPoint ()
    {
        return this.tvaEnvSustainPoint;
    }


    /**
     * Get the index of the breakpoint which is used for the end point.
     *
     * @return The TVA envelope end point in the range of 2..8
     */
    public int getTvaEnvEndPoint ()
    {
        return this.tvaEnvEndPoint;
    }


    /**
     * Get the envelope levels.
     *
     * @return The levels in the range of 0..127
     */
    public int [] getTvaEnvelopeLevels ()
    {
        return this.tvaEnvLevels;
    }


    /**
     * Get the envelope rates.
     *
     * @return The rates in the range of 1..127
     */
    public int [] getTvaEnvelopeRates ()
    {
        return this.tvaEnvRates;
    }


    /**
     * Get the state of the TVF.
     *
     * @return 0 = Off, 1 = On
     */
    public int getTvfSwitch ()
    {
        return this.tvfSwitch;
    }


    /**
     * Get the TVF cutoff.
     *
     * @return The cutoff in the range of 0..127
     */
    public int getTvfCutoff ()
    {
        return this.tvfCutOff;
    }


    /**
     * Get the TVF resonance.
     *
     * @return The resonance in the range of 0..127
     */
    public int getTvfResonance ()
    {
        return this.tvfResonance;
    }


    /**
     * Get the TVF key-follow value.
     * 
     * @return The value
     */
    public int getTvfKeyFollow ()
    {
        return this.tvfKeyFollow;
    }


    /**
     * Get the index of the TVF envelope point which represents the sustain level.
     *
     * @return The index in the range of 1..7
     */
    public int getTvfEnvSustainPoint ()
    {
        return this.tvfEnvSustainPoint;
    }


    /**
     * Get the index of the TVF envelope point which represents the end point.
     *
     * @return The index in the range of 2..8
     */
    public int getTvfEnvEndPoint ()
    {
        return this.tvfEnvEndPoint;
    }


    /**
     * Get the TVF envelope levels.
     *
     * @return The 8 levels in the range of 0..127
     */
    public int [] getTvfEnvLevels ()
    {
        return this.tvfEnvLevels;
    }


    /**
     * Get the TVF envelope rates.
     *
     * @return The 8 rates in the range of 1..127
     */
    public int [] getTvfEnvRates ()
    {
        return this.tvfEnvRates;
    }


    /**
     * Get the depth of the envelope control on the cutoff point.
     *
     * @return The depth in the range of 0..127
     */
    public int getTvfEgDepth ()
    {
        return this.tvfEgDepth;
    }


    /**
     * Get the polarity of the TVF envelope.
     *
     * @return 0 = normal, 1 = reversed (negative)
     */
    public int getTvfEgPolarity ()
    {
        return this.tvfEgPolarity;
    }
}