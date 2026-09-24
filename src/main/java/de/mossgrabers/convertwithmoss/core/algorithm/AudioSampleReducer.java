// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.algorithm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;


/**
 * Helper class to reduce the size of samples in different ways.
 *
 * @author Jürgen Moßgraber
 */
public class AudioSampleReducer
{
    // Full scale used to map 32-bit float samples (-1.0..1.0) onto the 32-bit signed integer
    // domain used internally by readSample/writeSample for all other bit depths.
    private static final double FLOAT_SCALE = 2147483648.0; // 2^31


    /**
     * Constructor. Private due to helper class.
     */
    private AudioSampleReducer ()
    {
        // Intentionally empty
    }


    /**
     * Reduces the size of audio samples based on the provided parameters
     *
     * @param sampleZones The sample zones to reduce
     * @param enableMakeMono True if samples should be reduced to mono
     * @param enableTrimSample True if samples should be trimmed
     * @param reduceBitDepth Maximum bit-depth to reduce to, negative to ignore
     * @param reduceFrequency Maximum sample rate to reduce to, negative to ignore
     * @param alwaysResample If true, do up-sample as well
     * @param enableNormalize True to normalize all samples (across all samples)
     * @throws IOException Could not read a sample
     * @throws UnsupportedAudioFileException Can't happen since only WAV files are supported
     */
    public static void reduceSamples (final List<ISampleZone> sampleZones, final boolean enableMakeMono, final boolean enableTrimSample, final int reduceBitDepth, final int reduceFrequency, final boolean alwaysResample, final boolean enableNormalize) throws IOException, UnsupportedAudioFileException
    {
        final List<byte []> sampleCache = loadSampleData (sampleZones);

        final int size = sampleCache.size ();
        final int [] sourceSampleRates = new int [size];
        final ISampleLoop [] resampledLoops = new ISampleLoop [size];

        final List<byte []> newSampleCache = new ArrayList<> ();
        for (int i = 0; i < size; i++)
        {
            byte [] data = sampleCache.get (i);
            final ISampleZone sampleZone = sampleZones.get (i);
            final Optional<ISampleData> sampleData = sampleZone.getSampleData ();
            if (sampleData.isEmpty ())
                throw new IOException ("Empty sample data in zone: " + sampleZone.getName ());
            final IAudioMetadata audioMetadata = sampleData.get ().getAudioMetadata ();
            sourceSampleRates[i] = audioMetadata.getSampleRate ();

            // Trim start/end
            if (enableTrimSample)
                data = trimSample (data, sampleZone);

            // Make mono if it is not already mono
            if (enableMakeMono && !audioMetadata.isMono ())
                data = convertToMono (data);

            // Reduce bit depth & sample rate if needed
            if (reduceBitDepth > 0 || reduceFrequency > 0)
            {
                resampledLoops[i] = getResampledLoop (sampleZone.getLoops (), data).orElse (null);
                final ConvertedSample converted = resample (data, reduceBitDepth, reduceFrequency, alwaysResample, resampledLoops[i]);
                data = converted.data ();
                // A sample which would clip after the conversion is lowered, the zone plays it
                // louder by the same amount
                if (converted.attenuation () > 0)
                    sampleZone.setGain (sampleZone.getGain () + converted.attenuation ());
            }

            newSampleCache.add (data);
        }

        if (enableNormalize)
            normalizeSample (newSampleCache);

        adjustPositions (sampleZones, sourceSampleRates, resampledLoops, newSampleCache);
    }


    /**
     * Get the loop which is kept intact when the sample rate of a sample is converted, see
     * {@link SincResampler#resampleLoop(double[], int, int, int, int)}: the first forward or
     * backward loop which lies inside of the audio. An alternating loop does not repeat, therefore
     * it is converted like the rest of the audio.
     *
     * @param loops The loops of the zone
     * @param numberOfFrames The number of frames of the sample
     * @return The loop, if any
     */
    public static Optional<ISampleLoop> getResampledLoop (final List<ISampleLoop> loops, final long numberOfFrames)
    {
        for (final ISampleLoop loop: loops)
            if (loop.getType () != LoopType.ALTERNATING && loop.getStart () >= 0 && loop.getEnd () > loop.getStart () && loop.getEnd () < numberOfFrames)
                return Optional.of (loop);
        return Optional.empty ();
    }


    private static Optional<ISampleLoop> getResampledLoop (final List<ISampleLoop> loops, final byte [] wavData) throws IOException, UnsupportedAudioFileException
    {
        if (loops.isEmpty ())
            return Optional.empty ();
        try (final AudioInputStream ais = AudioSystem.getAudioInputStream (new ByteArrayInputStream (wavData)))
        {
            return getResampledLoop (loops, ais.getFrameLength ());
        }
    }


    private static void adjustPositions (final List<ISampleZone> sampleZones, final int [] sourceSampleRates, final ISampleLoop [] resampledLoops, final List<byte []> newSampleCache) throws IOException
    {
        for (int i = 0; i < newSampleCache.size (); i++)
        {
            final WavFileSampleData sampleData = new WavFileSampleData (new ByteArrayInputStream (newSampleCache.get (i)));
            final ISampleZone sampleZone = sampleZones.get (i);
            sampleZone.setSampleData (sampleData);

            // Adjust all positions if sample rate did change!

            final int newSampleRate = sampleData.getAudioMetadata ().getSampleRate ();
            if (sourceSampleRates[i] == newSampleRate)
                continue;

            final double sampleRateRatio = newSampleRate / (double) sourceSampleRates[i];
            final int start = sampleZone.getStart ();
            if (start > 0)
                sampleZone.setStart ((int) Math.round (start * sampleRateRatio));
            final int stop = sampleZone.getStop ();
            if (stop > 0)
                sampleZone.setStop (Math.min ((int) Math.round (stop * sampleRateRatio), sampleData.getAudioMetadata ().getNumberOfSamples ()));

            for (final ISampleLoop loop: sampleZone.getLoops ())
            {
                // The loop which was converted as a loop has to be placed exactly where the
                // conversion put it, its length is not simply the scaled length
                if (loop == resampledLoops[i])
                {
                    final int [] positions = SincResampler.mapLoop (loop.getStart (), loop.getEnd (), sourceSampleRates[i], newSampleRate);
                    loop.setStart (positions[0]);
                    loop.setEnd (positions[1]);
                    continue;
                }

                final int loopStart = loop.getStart ();
                if (loopStart > 0)
                    loop.setStart ((int) Math.round (loopStart * sampleRateRatio));
                final int loopEnd = loop.getEnd ();
                if (loopEnd > 0)
                    loop.setEnd ((int) Math.round (loopEnd * sampleRateRatio));
            }
        }
    }


    private static void normalizeSample (final List<byte []> newSampleCache) throws IOException, UnsupportedAudioFileException
    {
        // Find the loudest peak across all samples. The peaks are normalized to the range of 0..1,
        // otherwise samples with different bit depths could not be compared with each other.
        double maximumPeak = 0;
        for (final byte [] data: newSampleCache)
            maximumPeak = Math.max (maximumPeak, findMaxAmplitude (data));
        // Normalize if needed
        if (maximumPeak > 0)
            for (int i = 0; i < newSampleCache.size (); i++)
                newSampleCache.set (i, normalize (newSampleCache.get (i), maximumPeak));
    }


    private static ConvertedSample resample (final byte [] wavData, final int reduceBitDepth, final int reduceFrequency, final boolean alwaysResample, final ISampleLoop loop) throws IOException, UnsupportedAudioFileException
    {
        final boolean shouldResampleBitDepth = reduceBitDepth > 0;
        final boolean shouldResampleFrequency = reduceFrequency > 0;
        if (!shouldResampleBitDepth && !shouldResampleFrequency)
            return new ConvertedSample (wavData, 0);

        boolean needsBitDepthResampling = false;
        boolean needsFrequencyResampling = false;
        try (final AudioInputStream ais = AudioSystem.getAudioInputStream (new ByteArrayInputStream (wavData)))
        {
            final AudioFormat format = ais.getFormat ();
            needsBitDepthResampling = shouldResampleBitDepth && (alwaysResample || format.getSampleSizeInBits () > reduceBitDepth);
            needsFrequencyResampling = shouldResampleFrequency && (alwaysResample || format.getSampleRate () > reduceFrequency);
        }
        byte [] data = wavData;
        if (needsBitDepthResampling)
            data = reduceBitDepth (data, reduceBitDepth, alwaysResample);
        if (needsFrequencyResampling)
            return convertFrequency (data, reduceFrequency, alwaysResample, loop, true);
        return new ConvertedSample (data, 0);
    }


    private static byte [] trimSample (final byte [] data, final ISampleZone sampleZone) throws IOException, UnsupportedAudioFileException
    {
        final int start = Math.max (0, sampleZone.getStart ());
        int end = sampleZone.getStop ();
        final List<ISampleLoop> loops = sampleZone.getLoops ();
        if (!loops.isEmpty ())
        {
            int maxLoopEnd = -1;
            for (final ISampleLoop loop: loops)
            {
                final int loopEnd = loop.getEnd ();
                if (loopEnd > 0 && loopEnd < end && loopEnd > maxLoopEnd)
                    maxLoopEnd = loopEnd;
            }
            // The loop end is inclusive, the end of the trimmed audio is not
            if (maxLoopEnd > 0)
                end = maxLoopEnd + 1;
        }
        sampleZone.setStart (0);

        // The audio was cut at the zone start - move the loop points with it
        if (start > 0)
            for (final ISampleLoop loop: loops)
            {
                loop.setStart (Math.max (0, loop.getStart () - start));
                if (loop.getEnd () > 0)
                    loop.setEnd (Math.max (0, loop.getEnd () - start));
            }

        return trimSampleData (data, start, end, sampleZone);
    }


    /**
     * Trim sample at beginning and end
     *
     * @param wavData The WAV data structure
     * @param startFrame The start frame from which to trim to the beginning of the sample
     * @param stopFrame All frames after this will be trimmed from the end
     * @param sampleZone The sample zone
     * @return The updated sample as a WAV audio structure
     * @throws IOException Could not read the sample
     * @throws UnsupportedAudioFileException Could not parse the WAV file
     */
    private static byte [] trimSampleData (final byte [] wavData, final int startFrame, final int stopFrame, final ISampleZone sampleZone) throws IOException, UnsupportedAudioFileException
    {
        try (final AudioInputStream ais = AudioSystem.getAudioInputStream (new ByteArrayInputStream (wavData)))
        {
            final AudioFormat format = ais.getFormat ();
            final int frameSize = format.getFrameSize ();
            final byte [] allData = ais.readAllBytes ();

            // Calculate total frames, if not provided by the API
            long totalFrames = ais.getFrameLength ();
            if (totalFrames == AudioSystem.NOT_SPECIFIED)
                totalFrames = allData.length / frameSize;

            // Calculate the trimmed region
            final int actualStart = Math.max (0, startFrame);
            final int actualStop = stopFrame > 0 && stopFrame < totalFrames ? stopFrame : (int) totalFrames;
            sampleZone.setStop (actualStop - startFrame);
            if (actualStart >= actualStop)
                return audioStreamToWavBytes (new AudioInputStream (new ByteArrayInputStream (new byte [0]), format, 0));
            final int startByte = actualStart * frameSize;
            final int endByte = actualStop * frameSize;
            final int newLength = endByte - startByte;

            // Copy trimmed region
            final byte [] trimmedData = new byte [newLength];
            System.arraycopy (allData, startByte, trimmedData, 0, newLength);
            final long newFrameLength = newLength / frameSize;
            return audioStreamToWavBytes (new AudioInputStream (new ByteArrayInputStream (trimmedData), format, newFrameLength));
        }
    }


    /**
     * Convert stereo to mono.
     *
     * @param wavData The WAV data structure
     * @return The updated sample as a WAV audio structure
     * @throws IOException Could not read the sample
     * @throws UnsupportedAudioFileException Could not parse the WAV file
     */
    private static byte [] convertToMono (final byte [] wavData) throws IOException, UnsupportedAudioFileException
    {
        try (final AudioInputStream ais = AudioSystem.getAudioInputStream (new ByteArrayInputStream (wavData)))
        {
            final byte [] sourceData = ais.readAllBytes ();
            final AudioFormat sourceFormat = ais.getFormat ();
            final AudioFormat.Encoding encoding = sourceFormat.getEncoding ();
            final int channels = sourceFormat.getChannels ();
            final int sampleSizeInBits = sourceFormat.getSampleSizeInBits ();
            final int bytesPerSample = toFullBytes (sampleSizeInBits);
            final int frameSize = channels * bytesPerSample;
            final int numFrames = sourceData.length / frameSize;
            final byte [] monoData = new byte [numFrames * bytesPerSample];
            final boolean bigEndian = sourceFormat.isBigEndian ();

            for (int frame = 0; frame < numFrames; frame++)
            {
                long sum = 0;
                for (int ch = 0; ch < channels; ch++)
                {
                    final int offset = frame * frameSize + ch * bytesPerSample;
                    final int sample = readSample (sourceData, offset, sampleSizeInBits, bigEndian, encoding);
                    sum += sample;
                }
                final int average = (int) (sum / channels);
                writeSample (monoData, frame * bytesPerSample, average, sampleSizeInBits, bigEndian, encoding);
            }

            final AudioFormat monoFormat = new AudioFormat (encoding, sourceFormat.getSampleRate (), sampleSizeInBits, 1, bytesPerSample, sourceFormat.getFrameRate (), bigEndian);
            return audioStreamToWavBytes (new AudioInputStream (new ByteArrayInputStream (monoData), monoFormat, numFrames));
        }
    }


    /**
     * Reduce bit depth.
     *
     * @param wavData The WAV data structure
     * @param targetBits The maximum bit-depth
     * @param alwaysResample If true, do up-sample as well
     * @return The updated sample as a WAV audio structure
     * @throws IOException Could not read the sample
     * @throws UnsupportedAudioFileException Could not parse the WAV file
     */
    private static byte [] reduceBitDepth (final byte [] wavData, final int targetBits, final boolean alwaysResample) throws IOException, UnsupportedAudioFileException
    {
        try (final AudioInputStream ais = AudioSystem.getAudioInputStream (new ByteArrayInputStream (wavData)))
        {
            final AudioFormat sourceFormat = ais.getFormat ();
            final AudioFormat.Encoding sourceEncoding = sourceFormat.getEncoding ();
            final int sourceBits = sourceFormat.getSampleSizeInBits ();

            // WAV stores 8 bit samples as unsigned, which writeSample already applied. The format
            // must say so as well, otherwise the WAV writer adds the bias a second time.
            // Note: reduceBitDepth always converts towards fixed-point PCM, never float, so a
            // float source is never "already" at the target encoding, even if the bit counts
            // happen to match numerically (e.g. 32-bit float -> 32-bit signed integer).
            final AudioFormat.Encoding targetEncoding = targetBits == 8 ? AudioFormat.Encoding.PCM_UNSIGNED : AudioFormat.Encoding.PCM_SIGNED;

            if (sourceEncoding == targetEncoding && (sourceBits == targetBits || sourceBits < targetBits && !alwaysResample))
                return wavData;

            final byte [] sourceData = ais.readAllBytes ();

            final int channels = sourceFormat.getChannels ();
            final int sourceBytesPerSample = toFullBytes (sourceBits); // Support e.g. 12-bit
            // Note: this works only for bit-depths which are aligned to 8! But other sizes don't
            // safe any space since they need to be aligned to 8 as well!
            final int targetBytesPerSample = targetBits / 8;
            final int sourceFrameSize = channels * sourceBytesPerSample;
            final int targetFrameSize = channels * targetBytesPerSample;
            final int numFrames = sourceData.length / sourceFrameSize;

            final byte [] targetData = new byte [numFrames * targetFrameSize];
            final boolean bigEndian = sourceFormat.isBigEndian ();
            final int shiftBits = sourceBits - targetBits;

            for (int frame = 0; frame < numFrames; frame++)
                for (int ch = 0; ch < channels; ch++)
                {
                    final int sourceOffset = frame * sourceFrameSize + ch * sourceBytesPerSample;
                    final int targetOffset = frame * targetFrameSize + ch * targetBytesPerSample;

                    final int sample = readSample (sourceData, sourceOffset, sourceBits, bigEndian, sourceEncoding);

                    final int reduced;
                    if (shiftBits > 0)
                        reduced = sample >> shiftBits; // down-sampling
                    else if (shiftBits < 0)
                        reduced = sample << -shiftBits; // up-sampling
                    else
                        reduced = sample;

                    writeSample (targetData, targetOffset, reduced, targetBits, bigEndian, targetEncoding);
                }

            final AudioFormat targetFormat = new AudioFormat (targetEncoding, sourceFormat.getSampleRate (), targetBits, channels, targetFrameSize, sourceFormat.getFrameRate (), bigEndian);
            return audioStreamToWavBytes (new AudioInputStream (new ByteArrayInputStream (targetData), targetFormat, numFrames));
        }
    }


    /**
     * Re-sample frequency with a band-limited interpolation, see {@link SincResampler}. Linear
     * interpolation was used before, which folds the frequencies above the new Nyquist frequency
     * back into the audible range when down-sampling and leaves images of the source spectrum above
     * the source Nyquist frequency when up-sampling.
     *
     * @param wavData The WAV data structure
     * @param targetRate The maximum sample rate
     * @param alwaysResample If true, do up-sample as well
     * @return The updated sample as a WAV audio structure
     * @throws IOException Could not read the sample
     * @throws UnsupportedAudioFileException Could not parse the WAV file
     */
    public static byte [] resampleFrequency (final byte [] wavData, final int targetRate, final boolean alwaysResample) throws IOException, UnsupportedAudioFileException
    {
        return resampleFrequency (wavData, targetRate, alwaysResample, null);
    }


    /**
     * Re-sample frequency with a band-limited interpolation and keep a loop intact, see
     * {@link SincResampler#resampleLoop(double[], int, int, int, int)}. Audio which exceeds the
     * range of the bit depth after the conversion is clipped.
     *
     * @param wavData The WAV data structure
     * @param targetRate The maximum sample rate
     * @param alwaysResample If true, do up-sample as well
     * @param loop The loop to keep intact, it has to lie inside of the audio; null to convert all
     *            of the audio in the same way. The positions of the loop in the result are given by
     *            {@link SincResampler#mapLoop(int, int, int, int)}
     * @return The updated sample as a WAV audio structure
     * @throws IOException Could not read the sample
     * @throws UnsupportedAudioFileException Could not parse the WAV file
     */
    public static byte [] resampleFrequency (final byte [] wavData, final int targetRate, final boolean alwaysResample, final ISampleLoop loop) throws IOException, UnsupportedAudioFileException
    {
        return convertFrequency (wavData, targetRate, alwaysResample, loop, false).data ();
    }


    /**
     * Re-sample frequency with a band-limited interpolation and keep a loop intact, see
     * {@link #resampleFrequency(byte[], int, boolean, ISampleLoop)}. The interpolation overshoots at
     * steep transients, e.g. the edges of a square wave, therefore the audio of a sample which peaks
     * close to full scale can exceed the range of the bit depth after the conversion. It is either
     * lowered to fit, if the caller compensates the attenuation, or clipped.
     *
     * @param wavData The WAV data structure
     * @param targetRate The maximum sample rate
     * @param alwaysResample If true, do up-sample as well
     * @param loop The loop to keep intact, null to convert all of the audio in the same way
     * @param attenuate True to lower audio which would clip, false to clip it
     * @return The updated sample as a WAV audio structure and the attenuation which was applied
     * @throws IOException Could not read the sample
     * @throws UnsupportedAudioFileException Could not parse the WAV file
     */
    private static ConvertedSample convertFrequency (final byte [] wavData, final int targetRate, final boolean alwaysResample, final ISampleLoop loop, final boolean attenuate) throws IOException, UnsupportedAudioFileException
    {
        try (final AudioInputStream ais = AudioSystem.getAudioInputStream (new ByteArrayInputStream (wavData)))
        {
            final AudioFormat sourceFormat = ais.getFormat ();
            final float sourceRate = sourceFormat.getSampleRate ();
            if (sourceRate == targetRate || sourceRate < targetRate && !alwaysResample)
                return new ConvertedSample (wavData, 0);

            final byte [] sourceData = ais.readAllBytes ();
            final AudioFormat.Encoding encoding = sourceFormat.getEncoding ();
            final int channels = sourceFormat.getChannels ();
            final int sampleSizeInBits = sourceFormat.getSampleSizeInBits ();
            final int bytesPerSample = toFullBytes (sampleSizeInBits);
            final int frameSize = channels * bytesPerSample;
            final int sourceFrames = sourceData.length / frameSize;

            final boolean bigEndian = sourceFormat.isBigEndian ();

            // De-interleave, convert each channel on its own and interleave again
            final double [] [] converted = new double [channels] [];
            for (int channel = 0; channel < channels; channel++)
            {
                final double [] channelData = new double [sourceFrames];
                for (int frame = 0; frame < sourceFrames; frame++)
                    channelData[frame] = readSample (sourceData, frame * frameSize + channel * bytesPerSample, sampleSizeInBits, bigEndian, encoding);
                converted[channel] = loop == null ? SincResampler.resample (channelData, (int) sourceRate, targetRate) : SincResampler.resampleLoop (channelData, (int) sourceRate, targetRate, loop.getStart (), loop.getEnd ());
            }

            final int convertedFrames = converted[0].length;
            final byte [] targetData = new byte [convertedFrames * frameSize];
            final int maximum = (1 << sampleSizeInBits - 1) - 1;
            final int minimum = -(1 << sampleSizeInBits - 1);

            // The kernel overshoots at steep transients: lower the audio to fit into the range or
            // clip it
            double scale = 1;
            if (attenuate)
            {
                double highest = 0;
                double lowest = 0;
                for (final double [] channelData: converted)
                    for (final double value: channelData)
                    {
                        highest = Math.max (highest, value);
                        lowest = Math.min (lowest, value);
                    }
                if (highest > maximum + 0.5)
                    scale = maximum / highest;
                if (lowest < minimum - 0.5)
                    scale = Math.min (scale, minimum / lowest);
            }

            for (int frame = 0; frame < convertedFrames; frame++)
                for (int channel = 0; channel < channels; channel++)
                {
                    final int sample = Math.clamp (Math.round (converted[channel][frame] * scale), minimum, maximum);
                    writeSample (targetData, frame * frameSize + channel * bytesPerSample, sample, sampleSizeInBits, bigEndian, encoding);
                }

            final AudioFormat targetFormat = new AudioFormat (encoding, targetRate, sampleSizeInBits, channels, frameSize, targetRate, bigEndian);
            final byte [] result = audioStreamToWavBytes (new AudioInputStream (new ByteArrayInputStream (targetData), targetFormat, convertedFrames));
            return new ConvertedSample (result, scale < 1 ? -20.0 * Math.log10 (scale) : 0);
        }
    }


    /**
     * The audio of a sample after a conversion.
     *
     * @param data The sample as a WAV audio structure
     * @param attenuation The attenuation in dB which was applied to the audio to prevent clipping
     */
    private record ConvertedSample (byte [] data, double attenuation)
    {
    }


    /**
     * Find maximum amplitude in audio stream.
     *
     * @param wavData The WAV data structure
     * @return The maximum value in the stream, normalized to the range of 0..1 which makes it
     *         comparable across different bit depths
     * @throws IOException Could not read the stream
     * @throws UnsupportedAudioFileException Could not parse the WAV file
     */
    private static double findMaxAmplitude (final byte [] wavData) throws IOException, UnsupportedAudioFileException
    {
        try (final AudioInputStream ais = AudioSystem.getAudioInputStream (new ByteArrayInputStream (wavData)))
        {
            final AudioFormat format = ais.getFormat ();
            final byte [] data = ais.readAllBytes ();

            final AudioFormat.Encoding encoding = format.getEncoding ();
            final int sampleSizeInBits = format.getSampleSizeInBits ();
            final int bytesPerSample = toFullBytes (sampleSizeInBits);
            final int numSamples = data.length / bytesPerSample;
            final boolean bigEndian = format.isBigEndian ();

            double max = 0.0;
            for (int i = 0; i < numSamples; i++)
            {
                final int offset = i * bytesPerSample;
                final int sample = readSample (data, offset, sampleSizeInBits, bigEndian, encoding);
                max = Math.max (max, Math.abs (sample));
            }
            return max / maximumPositiveValue (sampleSizeInBits);
        }
    }


    /**
     * Get the largest positive value which can be stored with the given bit depth. Note that the
     * negative range extends one step further, e.g. 16 bit covers -32768 to 32767.
     *
     * @param sampleSizeInBits The number of bits of one sample
     * @return The largest positive value
     */
    private static int maximumPositiveValue (final int sampleSizeInBits)
    {
        return (1 << sampleSizeInBits - 1) - 1;
    }


    /**
     * Normalize audio to maximum amplitude.
     *
     * @param wavData The WAV data structure
     * @param maximumPeak The loudest peak of all samples, normalized to the range of 0..1
     * @return The updated sample as a WAV audio structure
     * @throws IOException Could not read the sample
     * @throws UnsupportedAudioFileException Could not parse the WAV file
     */
    private static byte [] normalize (final byte [] wavData, final double maximumPeak) throws IOException, UnsupportedAudioFileException
    {
        try (final AudioInputStream ais = AudioSystem.getAudioInputStream (new ByteArrayInputStream (wavData)))
        {
            if (maximumPeak == 0)
                return wavData;

            final AudioFormat format = ais.getFormat ();
            final byte [] data = ais.readAllBytes ();
            final AudioFormat.Encoding encoding = format.getEncoding ();
            final int sampleSizeInBits = format.getSampleSizeInBits ();
            final int bytesPerSample = toFullBytes (sampleSizeInBits);
            final int numSamples = data.length / bytesPerSample;
            final boolean bigEndian = format.isBigEndian ();

            // Calculate the range of values which can be stored with this bit depth
            final int positiveLimit = maximumPositiveValue (sampleSizeInBits);
            final int negativeLimit = -positiveLimit - 1;
            // The peak is normalized to 0..1, therefore the factor which maps it exactly onto the
            // positive full scale is the same for all bit depths
            final double scale = 1.0 / maximumPeak;
            if (scale == 1.0)
                return wavData;

            final byte [] normalized = new byte [data.length];
            for (int i = 0; i < numSamples; i++)
            {
                final int offset = i * bytesPerSample;
                final int sample = readSample (data, offset, sampleSizeInBits, bigEndian, encoding);
                int scaled = (int) Math.round (sample * scale);
                scaled = Math.clamp (scaled, negativeLimit, positiveLimit);
                writeSample (normalized, offset, scaled, sampleSizeInBits, bigEndian, encoding);
            }

            return audioStreamToWavBytes (new AudioInputStream (new ByteArrayInputStream (normalized), format, ais.getFrameLength ()));
        }
    }


    private static int readSample (final byte [] data, final int offset, final int sampleSizeInBits, final boolean bigEndian, final AudioFormat.Encoding encoding)
    {
        // 32-bit IEEE float samples, normalized (-1.0..1.0) -> mapped onto the same 32-bit signed
        // integer domain used for all other bit depths
        if (encoding == AudioFormat.Encoding.PCM_FLOAT && sampleSizeInBits == 32)
            return readFloatSample (data, offset, bigEndian);

        // 8-bit WAV samples are unsigned with a bias of 128
        if (sampleSizeInBits == 8)
            return (data[offset] & 0xFF) - 128;

        int sample = 0;
        final int bytesPerSample = toFullBytes (sampleSizeInBits);
        final int containerBits = bytesPerSample * 8;

        // Read full container
        if (bigEndian)
            for (int i = 0; i < bytesPerSample; i++)
                sample = sample << 8 | data[offset + i] & 0xFF;
        else
            for (int i = bytesPerSample - 1; i >= 0; i--)
                sample = sample << 8 | data[offset + i] & 0xFF;

        // WAV PCM non-byte-aligned samples are left-justified
        final int paddingBits = containerBits - sampleSizeInBits;

        if (paddingBits > 0)
            sample >>= paddingBits;

        // Mask to valid sample width
        if (sampleSizeInBits < 32)
        {
            final int mask = (1 << sampleSizeInBits) - 1;
            sample &= mask;
        }

        // Sign extend
        if (sampleSizeInBits < 32)
        {
            final int signBit = 1 << sampleSizeInBits - 1;

            if ((sample & signBit) != 0)
                sample |= ~((1 << sampleSizeInBits) - 1);
        }

        return sample;
    }


    private static void writeSample (final byte [] data, final int offset, final int sample, final int sampleSizeInBits, final boolean bigEndian, final AudioFormat.Encoding encoding)
    {
        // 32-bit IEEE float samples, mapped back from the normalized 32-bit signed integer domain
        if (encoding == AudioFormat.Encoding.PCM_FLOAT && sampleSizeInBits == 32)
        {
            writeFloatSample (data, offset, sample, bigEndian);
            return;
        }

        // 8-bit WAV samples are unsigned with a bias of 128
        if (sampleSizeInBits == 8)
        {
            data[offset] = (byte) (sample + 128);
            return;
        }

        final int bytesPerSample = toFullBytes (sampleSizeInBits);
        final int containerBits = bytesPerSample * 8;

        // Keep only valid sample bits
        int newSample = sample;

        if (sampleSizeInBits < 32)
        {
            final int mask = (1 << sampleSizeInBits) - 1;
            newSample &= mask;
        }

        // WAV PCM non-byte-aligned samples are left-justified
        final int paddingBits = containerBits - sampleSizeInBits;

        if (paddingBits > 0)
            newSample <<= paddingBits;

        // Write container
        if (bigEndian)
            for (int i = bytesPerSample - 1; i >= 0; i--)
            {
                data[offset + i] = (byte) (newSample & 0xFF);
                newSample >>= 8;
            }
        else
            for (int i = 0; i < bytesPerSample; i++)
            {
                data[offset + i] = (byte) (newSample & 0xFF);
                newSample >>= 8;
            }
    }


    /**
     * Read a 32-bit IEEE float sample and map it onto the 32-bit signed integer domain used
     * throughout the rest of this class (nominal float range -1.0..1.0).
     *
     * @param data The byte buffer
     * @param offset The offset of the sample in the buffer
     * @param bigEndian True if the sample is stored big endian
     * @return The scaled integer value
     */
    private static int readFloatSample (final byte [] data, final int offset, final boolean bigEndian)
    {
        final ByteOrder order = bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        final float value = ByteBuffer.wrap (data, offset, 4).order (order).getFloat ();
        final long scaled = Math.round (value * FLOAT_SCALE);
        return Math.clamp (scaled, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }


    /**
     * Write an integer sample (from the 32-bit signed integer domain) back as a 32-bit IEEE float
     * sample (nominal range -1.0..1.0).
     *
     * @param data The byte buffer
     * @param offset The offset of the sample in the buffer
     * @param sample The scaled integer value
     * @param bigEndian True if the sample should be stored big endian
     */
    private static void writeFloatSample (final byte [] data, final int offset, final int sample, final boolean bigEndian)
    {
        final ByteOrder order = bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        final float value = (float) (sample / FLOAT_SCALE);
        final ByteBuffer buffer = ByteBuffer.allocate (4).order (order);
        buffer.putFloat (value);
        System.arraycopy (buffer.array (), 0, data, offset, 4);
    }


    private static List<byte []> loadSampleData (final List<ISampleZone> sampleZones) throws IOException
    {
        final List<byte []> sampleCache = new ArrayList<> ();
        for (final ISampleZone zone: sampleZones)
        {
            final ByteArrayOutputStream arrayOut = new ByteArrayOutputStream ();
            final Optional<ISampleData> sampleData = zone.getSampleData ();
            if (sampleData.isEmpty ())
                throw new IOException ("Empty sample data in zone: " + zone.getName ());
            sampleData.get ().writeSample (arrayOut);
            sampleCache.add (arrayOut.toByteArray ());
        }
        return sampleCache;
    }


    /**
     * Convert AudioInputStream to WAV byte array.
     *
     * @param ais The audio input stream
     * @return The WAV data structure
     * @throws IOException Could not create the data
     */
    private static byte [] audioStreamToWavBytes (final AudioInputStream ais) throws IOException
    {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream ();
        AudioSystem.write (ais, AudioFileFormat.Type.WAVE, baos);
        return baos.toByteArray ();
    }


    /**
     * Correctly handle non-byte-aligned depths (e.g. 12-, 20-bit).
     *
     * @param sampleSizeInBits The sample size, e.g. 12
     * @return The number of necessary bytes to store the sample size, e.g. 2 for 12-bit
     */
    private static int toFullBytes (final int sampleSizeInBits)
    {
        return (sampleSizeInBits + 7) / 8;
    }
}