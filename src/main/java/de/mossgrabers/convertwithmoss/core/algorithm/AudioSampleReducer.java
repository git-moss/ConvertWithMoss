// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.algorithm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;


/**
 * Helper class to reduce the size of samples in different ways.
 *
 * @author Jürgen Moßgraber
 */
public class AudioSampleReducer
{
    /**
     * Reduces the size of audio samples based on the provided parameters
     *
     * @param sampleZones The sample zones to reduce
     * @param enableMakeMono True if samples should be reduced to mono
     * @param enableTrimSample True if samples should be trimmed
     * @param reduceBitDepth Maximum bit-depth to reduce to, negative to ignore
     * @param reduceFrequency Maximum sample rate to reduce to, negative to ignore
     * @param enableNormalize True to normalize all samples (across all samples)
     * @throws IOException Could not read a sample
     * @throws UnsupportedAudioFileException Can't happen since only WAV files are supported
     */
    public static void reduceSamples (final List<ISampleZone> sampleZones, final boolean enableMakeMono, final boolean enableTrimSample, final int reduceBitDepth, final int reduceFrequency, final boolean enableNormalize) throws IOException, UnsupportedAudioFileException
    {
        final List<byte []> sampleCache = loadSampleData (sampleZones);
        final List<byte []> newSampleCache = new ArrayList<> ();
        for (int i = 0; i < sampleCache.size (); i++)
        {
            byte [] data = sampleCache.get (i);
            final ISampleZone sampleZone = sampleZones.get (i);

            // Trim start/end
            if (enableTrimSample)
            {
                final int start = sampleZone.getStart ();
                int end = sampleZone.getStop ();
                final List<ISampleLoop> loops = sampleZone.getLoops ();
                if (!loops.isEmpty ())
                {
                    final int loopEnd = loops.get (0).getEnd ();
                    if (loopEnd < end)
                        end = loopEnd;
                }
                data = trimSample (data, start, end);
                sampleZone.setStart (0);
                sampleZone.setStop (end - start);
            }

            // Make mono if it is not already mono
            if (enableMakeMono && !sampleZone.getSampleData ().getAudioMetadata ().isMono ())
                data = convertToMono (data);

            // Reduce bit depth & sample rate if needed
            if (reduceBitDepth > 0 || reduceFrequency > 0)
                data = resample (data, reduceBitDepth, reduceFrequency);

            newSampleCache.add (data);
        }

        // Find max amplitude if normalization is enabled
        if (!enableNormalize)
            return;
        double maxAmplitude = 0;
        for (final byte [] data: newSampleCache)
            maxAmplitude = Math.max (maxAmplitude, findMaxAmplitude (data));
        // Normalize if needed
        if (maxAmplitude > 0)
        {
            for (int i = 0; i < newSampleCache.size (); i++)
            {
                final byte [] data = normalize (newSampleCache.get (i), maxAmplitude);
                final ISampleData sampleData = new WavFileSampleData (new ByteArrayInputStream (data));
                sampleZones.get (i).setSampleData (sampleData);
            }
        }
    }


    private static byte [] resample (final byte [] wavData, final int reduceBitDepth, final int reduceFrequency) throws IOException, UnsupportedAudioFileException
    {
        final boolean shouldResampleBitDepth = reduceBitDepth > 0;
        final boolean shouldResampleFrequency = reduceFrequency > 0;
        if (!shouldResampleBitDepth && !shouldResampleFrequency)
            return wavData;

        boolean needsBitDepthResampling = false;
        boolean needsFrequencyResampling = false;
        try (final AudioInputStream ais = AudioSystem.getAudioInputStream (new ByteArrayInputStream (wavData)))
        {
            final AudioFormat format = ais.getFormat ();
            needsBitDepthResampling = shouldResampleBitDepth && format.getSampleSizeInBits () > reduceBitDepth;
            needsFrequencyResampling = shouldResampleFrequency && format.getSampleRate () > reduceFrequency;
        }
        byte [] data = wavData;
        if (needsFrequencyResampling)
            data = resampleFrequency (data, reduceFrequency);
        if (needsBitDepthResampling)
            data = reduceBitDepth (data, reduceBitDepth);
        return data;
    }


    /**
     * Trim sample at beginning and end
     *
     * @param wavData The WAV data structure
     * @param startFrame The start frame from which to trim to the beginning of the sample
     * @param stopFrame All frames after this will be trimmed from the end
     * @return The updated sample as a WAV audio structure
     * @throws IOException Could not read the sample
     * @throws UnsupportedAudioFileException Could not parse the WAV file
     */
    private static byte [] trimSample (final byte [] wavData, final int startFrame, final int stopFrame) throws IOException, UnsupportedAudioFileException
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
            final int channels = sourceFormat.getChannels ();
            final int sampleSizeInBits = sourceFormat.getSampleSizeInBits ();
            final int bytesPerSample = sampleSizeInBits / 8;
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
                    final int sample = readSample (sourceData, offset, sampleSizeInBits, bigEndian);
                    sum += sample;
                }
                final int average = (int) (sum / channels);
                writeSample (monoData, frame * bytesPerSample, average, sampleSizeInBits, bigEndian);
            }

            final AudioFormat monoFormat = new AudioFormat (sourceFormat.getEncoding (), sourceFormat.getSampleRate (), sampleSizeInBits, 1, bytesPerSample, sourceFormat.getFrameRate (), bigEndian);
            return audioStreamToWavBytes (new AudioInputStream (new ByteArrayInputStream (monoData), monoFormat, numFrames));
        }
    }


    /**
     * Reduce bit depth.
     *
     * @param wavData The WAV data structure
     * @param targetBits The maximum bit-depth
     * @return The updated sample as a WAV audio structure
     * @throws IOException Could not read the sample
     * @throws UnsupportedAudioFileException Could not parse the WAV file
     */
    private static byte [] reduceBitDepth (final byte [] wavData, final int targetBits) throws IOException, UnsupportedAudioFileException
    {
        try (final AudioInputStream ais = AudioSystem.getAudioInputStream (new ByteArrayInputStream (wavData)))
        {
            final AudioFormat sourceFormat = ais.getFormat ();
            final int sourceBits = sourceFormat.getSampleSizeInBits ();
            if (sourceBits <= targetBits)
                return wavData;

            final byte [] sourceData = ais.readAllBytes ();

            final int channels = sourceFormat.getChannels ();
            final int sourceBytesPerSample = sourceBits / 8;
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

                    final int sample = readSample (sourceData, sourceOffset, sourceBits, bigEndian);
                    final int reduced = sample >> shiftBits;
                    writeSample (targetData, targetOffset, reduced, targetBits, bigEndian);
                }

            final AudioFormat targetFormat = new AudioFormat (sourceFormat.getEncoding (), sourceFormat.getSampleRate (), targetBits, channels, targetFrameSize, sourceFormat.getFrameRate (), bigEndian);
            return audioStreamToWavBytes (new AudioInputStream (new ByteArrayInputStream (targetData), targetFormat, numFrames));
        }
    }


    /**
     * Re-sample frequency using linear interpolation.
     *
     * @param wavData The WAV data structure
     * @param targetRate The maximum sample rate
     * @return The updated sample as a WAV audio structure
     * @throws IOException Could not read the sample
     * @throws UnsupportedAudioFileException Could not parse the WAV file
     */
    private static byte [] resampleFrequency (final byte [] wavData, final int targetRate) throws IOException, UnsupportedAudioFileException
    {
        try (final AudioInputStream ais = AudioSystem.getAudioInputStream (new ByteArrayInputStream (wavData)))
        {
            final AudioFormat sourceFormat = ais.getFormat ();
            final float sourceRate = sourceFormat.getSampleRate ();
            if (sourceRate <= targetRate)
                return wavData;

            final byte [] sourceData = ais.readAllBytes ();

            final double ratio = targetRate / sourceRate;
            final int channels = sourceFormat.getChannels ();
            final int sampleSizeInBits = sourceFormat.getSampleSizeInBits ();
            final int bytesPerSample = sampleSizeInBits / 8;
            final int frameSize = channels * bytesPerSample;
            final int sourceFrames = sourceData.length / frameSize;
            final int targetFrames = (int) (sourceFrames * ratio);

            final byte [] targetData = new byte [targetFrames * frameSize];
            final boolean bigEndian = sourceFormat.isBigEndian ();

            for (int targetFrame = 0; targetFrame < targetFrames; targetFrame++)
            {
                final double sourcePos = targetFrame / ratio;
                final int sourceFrame1 = (int) sourcePos;
                final int sourceFrame2 = Math.min (sourceFrame1 + 1, sourceFrames - 1);
                final double frac = sourcePos - sourceFrame1;

                for (int ch = 0; ch < channels; ch++)
                {
                    final int offset1 = sourceFrame1 * frameSize + ch * bytesPerSample;
                    final int offset2 = sourceFrame2 * frameSize + ch * bytesPerSample;

                    final int sample1 = readSample (sourceData, offset1, sampleSizeInBits, bigEndian);
                    final int sample2 = readSample (sourceData, offset2, sampleSizeInBits, bigEndian);

                    final int interpolated = (int) (sample1 * (1 - frac) + sample2 * frac);

                    final int targetOffset = targetFrame * frameSize + ch * bytesPerSample;
                    writeSample (targetData, targetOffset, interpolated, sampleSizeInBits, bigEndian);
                }
            }

            final AudioFormat targetFormat = new AudioFormat (sourceFormat.getEncoding (), targetRate, sampleSizeInBits, channels, frameSize, targetRate, bigEndian);
            return audioStreamToWavBytes (new AudioInputStream (new ByteArrayInputStream (targetData), targetFormat, targetFrames));
        }
    }


    /**
     * Find maximum amplitude in audio stream.
     *
     * @param wavData The WAV data structure
     * @return The maximum value in the stream
     * @throws IOException Could not read the stream
     * @throws UnsupportedAudioFileException Could not parse the WAV file
     */
    private static double findMaxAmplitude (final byte [] wavData) throws IOException, UnsupportedAudioFileException
    {
        try (final AudioInputStream ais = AudioSystem.getAudioInputStream (new ByteArrayInputStream (wavData)))
        {
            final AudioFormat format = ais.getFormat ();
            final byte [] data = ais.readAllBytes ();

            final int sampleSizeInBits = format.getSampleSizeInBits ();
            final int bytesPerSample = sampleSizeInBits / 8;
            final int numSamples = data.length / bytesPerSample;
            final boolean bigEndian = format.isBigEndian ();

            double max = 0.0;
            for (int i = 0; i < numSamples; i++)
            {
                final int offset = i * bytesPerSample;
                final int sample = readSample (data, offset, sampleSizeInBits, bigEndian);
                max = Math.max (max, Math.abs (sample));
            }
            return max;
        }
    }


    /**
     * Normalize audio to maximum amplitude.
     *
     * @param wavData The WAV data structure
     * @param maxAmplitude The maximum amplitude to normalize to
     * @return The updated sample as a WAV audio structure
     * @throws IOException Could not read the sample
     * @throws UnsupportedAudioFileException Could not parse the WAV file
     */
    private static byte [] normalize (final byte [] wavData, final double maxAmplitude) throws IOException, UnsupportedAudioFileException
    {
        try (final AudioInputStream ais = AudioSystem.getAudioInputStream (new ByteArrayInputStream (wavData)))
        {
            if (maxAmplitude == 0)
                return wavData;

            final AudioFormat format = ais.getFormat ();
            final byte [] data = ais.readAllBytes ();
            final int sampleSizeInBits = format.getSampleSizeInBits ();
            final int bytesPerSample = sampleSizeInBits / 8;
            final int numSamples = data.length / bytesPerSample;
            final boolean bigEndian = format.isBigEndian ();

            // Calculate max possible value for this bit depth
            final int maxValue = 1 << sampleSizeInBits - 1;
            final double scale = maxValue / maxAmplitude;
            if (scale == 1.0)
                return wavData;

            final byte [] normalized = new byte [data.length];
            for (int i = 0; i < numSamples; i++)
            {
                final int offset = i * bytesPerSample;
                final int sample = readSample (data, offset, sampleSizeInBits, bigEndian);
                int scaled = (int) Math.round (sample * scale);
                // Clamp to valid range
                scaled = Math.max (-maxValue - 1, Math.min (maxValue, scaled));
                writeSample (normalized, offset, scaled, sampleSizeInBits, bigEndian);
            }

            return audioStreamToWavBytes (new AudioInputStream (new ByteArrayInputStream (normalized), format, ais.getFrameLength ()));
        }
    }


    private static int readSample (final byte [] data, final int offset, final int sampleSizeInBits, final boolean bigEndian)
    {
        // Missing handling of 32 bit float values

        int sample = 0;
        final int bytesPerSample = sampleSizeInBits / 8;

        if (bigEndian)
            for (int i = 0; i < bytesPerSample; i++)
                sample = sample << 8 | data[offset + i] & 0xFF;
        else
            for (int i = bytesPerSample - 1; i >= 0; i--)
                sample = sample << 8 | data[offset + i] & 0xFF;

        // Sign extend if necessary
        if (sampleSizeInBits < 32)
        {
            final int signBit = 1 << sampleSizeInBits - 1;
            if ((sample & signBit) != 0)
                sample |= -1 << sampleSizeInBits;
        }

        return sample;
    }


    private static void writeSample (final byte [] data, final int offset, final int sample, final int sampleSizeInBits, final boolean bigEndian)
    {
        final int bytesPerSample = sampleSizeInBits / 8;

        int newSample = sample;
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


    private static List<byte []> loadSampleData (final List<ISampleZone> sampleZones) throws IOException
    {
        final List<byte []> sampleCache = new ArrayList<> ();
        for (final ISampleZone zone: sampleZones)
        {
            final ByteArrayOutputStream arrayOut = new ByteArrayOutputStream ();
            zone.getSampleData ().writeSample (arrayOut);
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
}