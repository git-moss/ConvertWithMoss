// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ensoniq.epsasr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A Ensoniq wave-sample.
 *
 * @author Jürgen Moßgraber
 */
public class EnsoniqWaveSample
{
    private static final int   HEADER_SIZE = 0x120;

    private final int          index;
    private final String       name;
    private final int          waveSampleCopy;
    private final int          waveLayerCopy;
    private final int          rootNote;
    private final int          pitchEnvelopeAmount;
    private final int          pitchBendRange;
    private final int          fineTune;
    private final int          filterMode;
    private final int          filter1Cutoff;
    private final int          filter1KeyAmount;
    private final int          filter1EnvelopeAmount;
    private final int          filter1ModulationSource;
    private final int          filter1ModulationAmount;
    private final int          volume;
    private final int          amplitudeModulationSource;
    private final int          panPositionEPS;
    private final int          panPositionARS;
    private final int          amplitudeModulationAmount;
    private final int          keyRangeLow;
    private final int          keyRangeHigh;
    private final int          sampleRate;
    private final int          loopMode;
    private final int          sampleStart;
    private final int          sampleEnd;
    private final int          loopStart;
    private final int          loopEnd;
    private final byte []      pcmData;

    private EnsoniqEnvelope    pitchEnvelope;
    private EnsoniqEnvelope    filterEnvelope;
    private EnsoniqEnvelope    amplitudeEnvelope;

    private InMemorySampleData sampleData;


    /**
     * Constructor.
     *
     * @param index The index of the wave-sample
     * @param data The wave-sample data block
     * @throws IOException Could not read the sample
     */
    public EnsoniqWaveSample (final int index, final byte [] data) throws IOException
    {
        this.index = index;

        final ByteArrayInputStream input = new ByteArrayInputStream (data);

        // Not used
        input.skipNBytes (6);

        this.name = StreamUtils.readAsciiLoByte (input, 12).trim ();
        this.waveSampleCopy = StreamUtils.readUnsigned8FromWord (input);
        this.waveLayerCopy = StreamUtils.readUnsigned8FromWord (input);

        this.pitchEnvelope = new EnsoniqEnvelope (input);
        this.filterEnvelope = new EnsoniqEnvelope (input);
        this.amplitudeEnvelope = new EnsoniqEnvelope (input);

        this.rootNote = input.read ();

        @SuppressWarnings("unused")
        final int volumeModulatorCrossfadeFadecurve = input.read ();

        this.pitchEnvelopeAmount = StreamUtils.readSigned8FromWord (input);

        @SuppressWarnings("unused")
        final int lfoAmount = StreamUtils.readSigned8FromWord (input);
        @SuppressWarnings("unused")
        final int randomModulationAmount = StreamUtils.readSigned8FromWord (input);

        this.pitchBendRange = StreamUtils.readUnsigned8FromWord (input);

        @SuppressWarnings("unused")
        final int modulationSource = StreamUtils.readUnsigned8FromWord (input);

        // Signed 7 bit fraction in hi-byte
        this.fineTune = StreamUtils.readSigned8FromWord (input);

        @SuppressWarnings("unused")
        final int modulationAmount = StreamUtils.readSigned8FromWord (input);

        this.filterMode = StreamUtils.readUnsigned8FromWord (input);

        this.filter1Cutoff = StreamUtils.readUnsigned8FromWord (input);
        @SuppressWarnings("unused")
        final int filter2Cutoff = StreamUtils.readUnsigned8FromWord (input);

        this.filter1KeyAmount = StreamUtils.readSigned8FromWord (input);
        @SuppressWarnings("unused")
        final int filter2KeyAmount = StreamUtils.readSigned8FromWord (input);

        this.filter1EnvelopeAmount = StreamUtils.readSigned8FromWord (input);
        @SuppressWarnings("unused")
        final int filter2EnvelopeAmount = StreamUtils.readSigned8FromWord (input);

        this.filter1ModulationSource = StreamUtils.readUnsigned8FromWord (input);
        @SuppressWarnings("unused")
        final int filter2ModulationSource = StreamUtils.readUnsigned8FromWord (input);

        this.filter1ModulationAmount = StreamUtils.readSigned8FromWord (input);
        @SuppressWarnings("unused")
        final int filter2ModulationAmount = StreamUtils.readSigned8FromWord (input);

        this.volume = input.read ();
        @SuppressWarnings("unused")
        final int outputBus = input.read ();

        this.amplitudeModulationSource = input.read ();
        @SuppressWarnings("unused")
        final int panningModulationSource = input.read ();

        @SuppressWarnings("unused")
        final int amplitudeCrossfadeCurvePointA = StreamUtils.readSigned8FromWord (input);
        @SuppressWarnings("unused")
        final int amplitudeCrossfadeCurvePointB = StreamUtils.readSigned8FromWord (input);
        @SuppressWarnings("unused")
        final int amplitudeCrossfadeCurvePointC = StreamUtils.readSigned8FromWord (input);
        @SuppressWarnings("unused")
        final int amplitudeCrossfadeCurvePointD = StreamUtils.readSigned8FromWord (input);

        this.panPositionEPS = input.read ();
        this.panPositionARS = StreamUtils.readSigned8 (input);

        this.amplitudeModulationAmount = input.read ();
        @SuppressWarnings("unused")
        final int panningModulationAmount = input.read ();

        // Skip LFO parameters
        input.skipNBytes (14);

        this.loopMode = StreamUtils.readUnsigned8FromWord (input);
        this.sampleStart = calculateOffset (input);
        this.sampleEnd = calculateOffset (input);
        this.loopStart = calculateOffset (input);

        final int loopStop = calculatePreOffset (input);
        final int loopEndFine = (loopStop >>> 5) & 0xF;
        this.loopEnd = (loopStop >>> 9) + loopEndFine;

        final int sampleRateIndex = StreamUtils.readUnsigned8FromWord (input);
        if (sampleRateIndex == 0)
            this.sampleRate = 48000;
        else if (sampleRateIndex == 1)
            this.sampleRate = 44100;
        else
            this.sampleRate = (int) Math.round (625000.0 / sampleRateIndex);

        this.keyRangeLow = StreamUtils.readUnsigned8FromWord (input);
        this.keyRangeHigh = StreamUtils.readUnsigned8FromWord (input);

        // Skip start/loop modulations / unused
        input.skipNBytes (10);

        final int sampleSize = 4 + data.length - HEADER_SIZE;
        if (sampleSize <= 0)
        {
            this.pcmData = null;
            return;
        }
        // Add 1 byte for uneven length to be able to swap the bytes below
        this.pcmData = new byte [sampleSize + sampleSize % 2];
        input.read (this.pcmData, 0, sampleSize);

        // Swap the high/low-bytes
        for (int i = 0; i < this.pcmData.length; i += 2)
        {
            final byte store = this.pcmData[i];
            this.pcmData[i] = this.pcmData[i + 1];
            this.pcmData[i + 1] = store;
        }

        this.sampleData = new InMemorySampleData (new DefaultAudioMetadata (1, this.sampleRate, 16, this.pcmData.length), this.pcmData);
    }


    /**
     * Get the index of the sample.
     *
     * @return The index
     */
    public int getIndex ()
    {
        return this.index;
    }


    /**
     * Get the name.
     *
     * @return the name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the sample copy index.
     *
     * @return The wave-sample copy index
     */
    public int getWaveSampleCopy ()
    {
        return this.waveSampleCopy;
    }


    /**
     * Get the layer copy index.
     *
     * @return The layer copy index
     */
    public int getWaveLayerCopy ()
    {
        return this.waveLayerCopy;
    }


    /**
     * Get the sample data
     *
     * @return The sample data or null if not present
     */
    public InMemorySampleData getSampleData ()
    {
        return this.sampleData;
    }


    /**
     * Get the root note.
     *
     * @return The root note
     */
    public int getRootNote ()
    {
        return this.rootNote;
    }


    /**
     * Get the low note of the key-range.
     *
     * @return The low note
     */
    public int getKeyRangeLow ()
    {
        return this.keyRangeLow;
    }


    /**
     * Get the high note of the key-range.
     *
     * @return The high note
     */
    public int getKeyRangeHigh ()
    {
        return this.keyRangeHigh;
    }


    /**
     * Get the pitch envelope amount. -15.7 to +15.7 (in 0.1 increments).
     *
     * @return The pitch envelope amount
     */
    public int getPitchEnvelopeAmount ()
    {
        return this.pitchEnvelopeAmount;
    }


    /**
     * Get the pitch-bend range.
     *
     * @return The pitch bend range, 0-13 (13=global)
     */
    public int getPitchBendRange ()
    {
        return this.pitchBendRange;
    }


    /**
     * Get the fine tune.
     *
     * @return The fine tune, -99 to +99
     */
    public int getFineTune ()
    {
        return this.fineTune;
    }


    /**
     * Get the filter mode.
     *
     * @return 0 = F1=LP2 F2=HP2, 1 = F1=LP3 F2=HP1, 2 = F1=LP2 F2=LP2, 3 = F1=LP3 F2=LP1
     */
    public int getFilterMode ()
    {
        return this.filterMode;
    }


    /**
     * Get the cutoff of filter 1.
     *
     * @return The filter 1 cutoff, 0-127
     */
    public int getFilter1Cutoff ()
    {
        return this.filter1Cutoff;
    }


    /**
     * Get the key-amount of filter 1.
     *
     * @return The filter 1 key-amount, -127..127
     */
    public int getFilter1KeyAmount ()
    {
        return this.filter1KeyAmount;
    }


    /**
     * Get the filter 1 envelope amount.
     *
     * @return The filter 1 envelope amount, -127..127
     */
    public int getFilter1EnvelopeAmount ()
    {
        return this.filter1EnvelopeAmount;
    }


    /**
     * Get the filter 1 modulation source.
     *
     * @return The filter 1 modulation source, 0-18
     */
    public int getFilter1ModulationSource ()
    {
        return this.filter1ModulationSource;
    }


    /**
     * Get the filter 1 modulation amount.
     *
     * @return The filter 1 modulation amount, -127..127
     */
    public int getFilter1ModulationAmount ()
    {
        return this.filter1ModulationAmount;
    }


    /**
     * Get the volume.
     *
     * @return The volume, 0-127
     */
    public int getVolume ()
    {
        return this.volume;
    }


    /**
     * Get the amplitude modulation source. 0=LFO l=RANDM 2=ENVl 3=ENV2 4=PR+VL 5=VEL 6=VEL 1 7=VEL2
     * 8=KBD 9=PITCH l0=WHEEL 11=PEDAL 12=XCTRL 13=PRESS 14=WL+PR 15=OFF.
     *
     * @return The amplitude modulation source, 0-18
     */
    public int getAmplitudeModulationSource ()
    {
        return this.amplitudeModulationSource;
    }


    /**
     * Get the amplitude modulation amount.
     *
     * @return The amplitude modulation amount, 0-127
     */
    public int getAmplitudeModulationAmount ()
    {
        return this.amplitudeModulationAmount;
    }


    /**
     * Get the EPS panning position. 0=Center, 1-8 from L to R. Ignore other values which are output
     * configurations.
     *
     * @return The panning position used on the EPS
     */
    public int getPanPositionEPS ()
    {
        return this.panPositionEPS;
    }


    /**
     * Get the ARS panning position.
     *
     * @return The panning position used on the ARS, -99 to +99
     */
    public int getPanPositionARS ()
    {
        return this.panPositionARS;
    }


    /**
     * Get the sample play-back start.
     *
     * @return The sample start
     */
    public int getSampleStart ()
    {
        return this.sampleStart;
    }


    /**
     * Get the sample play-back end.
     *
     * @return The sample end
     */
    public int getSampleEnd ()
    {
        return this.sampleEnd;
    }


    /**
     * Get the sample rate.
     *
     * @return The sample rate in Hertz
     */
    public int getSampleRate ()
    {
        return this.sampleRate;
    }


    /**
     * Loop start point in sample frames.
     *
     * @return The loop start
     */
    public int getLoopStart ()
    {
        return this.loopStart;
    }


    /**
     * Loop end point in sample frames.
     *
     * @return The loop end
     */
    public int getLoopEnd ()
    {
        return this.loopEnd;
    }


    /**
     * The loop mode.
     *
     * @return 0 = FORWARD-NO LOOP, 1 = BACKWARD-NO LOOP, 2 = LOOP FORWARD, 3 = LOOP BIDIRECTION, 4
     *         = LOOP AND RELEASE.
     */
    public int getLoopMode ()
    {
        return this.loopMode;
    }


    /**
     * Get the pitch envelope.
     * 
     * @return The pitch envelope
     */
    public EnsoniqEnvelope getPitchEnvelope ()
    {
        return this.pitchEnvelope;
    }


    /**
     * Get the filter envelope.
     * 
     * @return The filter envelope
     */
    public EnsoniqEnvelope getFilterEnvelope ()
    {
        return this.filterEnvelope;
    }


    /**
     * Get the amplitude envelope.
     * 
     * @return The amplitude envelope
     */
    public EnsoniqEnvelope getAmplitudeEnvelope ()
    {
        return this.amplitudeEnvelope;
    }


    /**
     * Get the PCM data.
     *
     * @return The PCM data
     */
    public byte [] getPcmData ()
    {
        return this.pcmData;
    }


    /**
     * Left justified 32 bit field using hi-bytes of each word - shift right 9 for word offset.
     *
     * @param input The input to read 4 words as 8 bytes
     * @return The offset value
     * @throws IOException Could not read the required bytes
     */
    private static int calculateOffset (final InputStream input) throws IOException
    {
        final int value = calculatePreOffset (input);

        // Shift right by 9 bits (use unsigned shift to avoid sign issues)
        return value >>> 9;
    }


    private static int calculatePreOffset (final InputStream input) throws IOException
    {
        final byte [] data = input.readNBytes (8);

        int value = 0;

        // Take high byte from each 2-byte word
        value |= (data[0] & 0xFF) << 24; // high byte of word 0
        value |= (data[2] & 0xFF) << 16; // high byte of word 1
        value |= (data[4] & 0xFF) << 8; // high byte of word 2
        value |= data[6] & 0xFF; // high byte of word 3

        return value;
    }
}