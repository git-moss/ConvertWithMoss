// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.casio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;


/**
 * Creator for Casio FZ-1/FZ-10M/FZ-20M floppy disk images. One image with a full dump file is
 * written per multi-sample: the zones become voices which are distributed by a bank, the samples
 * are stored as 16-bit mono waveforms. The image can be written to a floppy disk or used with a
 * floppy emulator (e.g. Gotek/HxC).
 *
 * @author Jürgen Moßgraber
 */
public class CasioFZCreator extends AbstractCreator<ICoreTaskSettings>
{
    private static final DestinationAudioFormat DESTINATION_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, -1, false);

    /** The maximum number of wave blocks which fit into the data sectors of a disk. */
    private static final int                    MAX_CONTENT_BLOCKS = CasioFZDisk.NUM_SECTORS - 3;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public CasioFZCreator (final INotifier notifier)
    {
        super ("Casio FZ-1/10M/20M", "CasioFZ", notifier, EmptySettingsUI.INSTANCE);
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String name = createSafeFilename (multisampleSource.getName ());
        final File multiFile = this.createUniqueFilename (destinationFolder, name, "img");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        // Collect all zones; the FZ supports up to 64 voices
        final List<ISampleZone> zones = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
                if (zone.getSampleData ().isPresent ())
                    zones.add (zone);
        if (zones.size () > CasioFZBank.MAX_AREAS)
        {
            this.notifier.logError ("IDS_FZ_TOO_MANY_ZONES", Integer.toString (zones.size ()));
            zones.subList (CasioFZBank.MAX_AREAS, zones.size ()).clear ();
        }

        // Convert all samples to 16-bit mono and check that they fit onto the disk
        final List<byte []> samples = new ArrayList<> ();
        final List<Integer> sampleRates = new ArrayList<> ();
        long totalWords = 0;
        for (int i = 0; i < zones.size (); i++)
        {
            final ISampleZone zone = zones.get (i);
            final WaveFile waveFile = AudioFileUtils.convertToWav (zone.getSampleData ().get (), DESTINATION_FORMAT);
            final FormatChunk formatChunk = waveFile.getFormatChunk ();
            final byte [] mono = convertToMono (waveFile.getDataChunk ().getData (), formatChunk.getNumberOfChannels ());
            samples.add (mono);
            sampleRates.add (Integer.valueOf (formatChunk.getSampleRate ()));
            totalWords += mono.length / 2;
        }

        final int voiceBlocks = (zones.size () + 3) / 4;
        long maxWaveWords = (long) (MAX_CONTENT_BLOCKS - 1 - voiceBlocks) * 512;
        while (totalWords > maxWaveWords && !zones.isEmpty ())
        {
            // Drop zones from the end until the samples fit onto the disk
            this.notifier.logError ("IDS_FZ_DISK_FULL_ZONE_DROPPED", zones.get (zones.size () - 1).getName ());
            totalWords -= samples.remove (samples.size () - 1).length / 2;
            sampleRates.remove (sampleRates.size () - 1);
            zones.remove (zones.size () - 1);
            maxWaveWords = (long) (MAX_CONTENT_BLOCKS - 1 - (zones.size () + 3) / 4) * 512;
        }
        if (zones.isEmpty ())
            throw new IOException ("The multi-sample does not contain any samples.");

        // Build the voices and the wave pool
        final CasioFZBank bank = new CasioFZBank ();
        bank.numberOfAreas = zones.size ();
        bank.name = toFZName (multisampleSource.getName ());

        final List<CasioFZVoice> voices = new ArrayList<> ();
        long waveAddress = 0;
        for (int i = 0; i < zones.size (); i++)
        {
            final ISampleZone zone = zones.get (i);
            final int numFrames = samples.get (i).length / 2;
            final int sampleRate = sampleRates.get (i).intValue ();
            final CasioFZVoice voice = createVoice (zone, waveAddress, numFrames, sampleRate);
            voices.add (voice);

            bank.lowKey[i] = Math.clamp (zone.getKeyLow (), 0, 127);
            bank.highKey[i] = Math.clamp (limitToDefault (zone.getKeyHigh (), 127), 0, 127);
            bank.centerKey[i] = Math.clamp (limitToDefault (zone.getKeyRoot (), 60), 0, 127);
            bank.lowVelocity[i] = Math.clamp (zone.getVelocityLow (), 1, 127);
            bank.highVelocity[i] = Math.clamp (limitToDefault (zone.getVelocityHigh (), 127), 1, 127);
            bank.midiChannel[i] = 0;
            // Enable all 8 sound generators for the area
            bank.generators[i] = 0xFF;
            bank.volume[i] = (int) Math.clamp (Math.round (127.0 * Math.pow (10, Math.min (0, zone.getGain ()) / 20.0)), 1, 127);
            bank.voicePointer[i] = i;

            waveAddress += numFrames;
        }

        // Assemble the content: the bank block with the effect parameters, the voice blocks and
        // the wave blocks
        final int waveBlocks = (int) ((totalWords * 2 + CasioFZDisk.SECTOR_SIZE - 1) / CasioFZDisk.SECTOR_SIZE);
        final byte [] content = new byte [(1 + voiceBlocks + waveBlocks) * CasioFZDisk.SECTOR_SIZE];
        bank.write (content, 0);
        // The pitch bend range of the effect parameters is stored in 1/8 semitone steps
        final int bendUpCents = zones.get (0).getBendUp ();
        content[960] = (byte) Math.clamp (bendUpCents <= 0 ? 16 : Math.round (bendUpCents * 8 / 100.0), 0, 127);

        for (int i = 0; i < voices.size (); i++)
            voices.get (i).write (content, (1 + i / 4) * CasioFZDisk.SECTOR_SIZE + i % 4 * 256);

        int waveOffset = (1 + voiceBlocks) * CasioFZDisk.SECTOR_SIZE;
        for (final byte [] sample: samples)
        {
            System.arraycopy (sample, 0, content, waveOffset, sample.length);
            waveOffset += sample.length;
        }

        // The file head with the voice, bank and wave block counters
        final byte [] head = new byte [CasioFZDisk.SECTOR_SIZE];
        CasioFZVoice.writeUnsigned16 (head, 1018, voices.size ());
        CasioFZVoice.writeUnsigned16 (head, 1020, 1);
        CasioFZVoice.writeUnsigned16 (head, 1022, waveBlocks);

        final byte [] image = CasioFZDisk.createDiskImage (toFZName (name), head, content);
        Files.write (multiFile.toPath (), image);

        this.progress.notifyDone ();
    }


    /**
     * Create a voice from a zone.
     *
     * @param zone The zone
     * @param waveAddress The word address of the sample in the wave pool
     * @param numFrames The number of sample frames
     * @param sampleRate The sample rate of the sample in Hz
     * @return The voice
     */
    private static CasioFZVoice createVoice (final ISampleZone zone, final long waveAddress, final int numFrames, final int sampleRate)
    {
        final CasioFZVoice voice = new CasioFZVoice ();
        voice.name = toFZName (zone.getName ());
        voice.mode = zone.isReversed () ? CasioFZVoice.MODE_REVERSED : CasioFZVoice.MODE_NORMAL;
        voice.waveStart = waveAddress;
        voice.waveEnd = waveAddress + numFrames;
        final int start = Math.clamp (zone.getStart (), 0, numFrames);
        final int stop = zone.getStop () <= 0 ? numFrames : Math.clamp (zone.getStop (), start, numFrames);
        voice.generatorStart = waveAddress + start;
        voice.generatorEnd = waveAddress + stop;

        // The FZ plays the sample at one of its fixed sample rates; the difference to the actual
        // rate of the source sample and the fine tuning are corrected with the pitch parameter
        // (1/256 semitone steps)
        voice.sampleRateIndex = sampleRate >= 27000 ? 0 : sampleRate >= 13500 ? 1 : 2;
        final double rateCompensation = 12.0 * Math.log (sampleRate / (double) voice.getSampleRate ()) / Math.log (2);
        voice.pitch = (int) Math.clamp (Math.round ((zone.getTuning () + rateCompensation) * 256.0), Short.MIN_VALUE, Short.MAX_VALUE);

        voice.lowKey = Math.clamp (zone.getKeyLow (), 0, 127);
        voice.highKey = Math.clamp (limitToDefault (zone.getKeyHigh (), 127), 0, 127);
        voice.centerKey = Math.clamp (limitToDefault (zone.getKeyRoot (), 60), 0, 127);

        // The sustain loop; all unused loops must keep valid addresses
        final List<ISampleLoop> loops = zone.getLoops ();
        for (int i = 0; i < CasioFZVoice.NUM_LOOPS; i++)
        {
            voice.loopStart[i] = voice.generatorStart;
            voice.loopEndAddress[i] = voice.generatorEnd;
            voice.loopTime[i] = 1;
        }
        if (loops.isEmpty ())
            voice.loopSustain = 8;
        else
        {
            final ISampleLoop loop = loops.get (0);
            voice.loopSustain = 0;
            voice.loopEnd = 0;
            voice.loopStart[0] = waveAddress + Math.clamp (loop.getStart (), 0, numFrames);
            voice.loopEndAddress[0] = waveAddress + Math.clamp (loop.getEnd (), 0, numFrames);
            voice.loopCrossfade[0] = (int) Math.clamp (Math.round (loop.getCrossfade () * 1023.0), 0, 1023);
        }

        // The velocity to amplitude modulation and the key follow
        voice.velocityAmpDepth = (int) Math.clamp (Math.round (zone.getAmplitudeVelocityModulator ().getDepth () * 127.0), -127, 127);
        voice.ampKeyFollow = (int) Math.clamp (Math.round (zone.getAmplitudeKeyTracking () * 127.0), -127, 127);

        // The amplitude envelope
        final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        convertEnvelope (voice.ampRate, voice.ampStop, envelope);
        voice.ampSustainPoint = 1;
        voice.ampEndPoint = 2;

        // The filter with its envelope
        final Optional<IFilter> filterOpt = zone.getFilter ();
        if (filterOpt.isPresent () && filterOpt.get ().getType () == FilterType.LOW_PASS)
        {
            final IFilter filter = filterOpt.get ();
            voice.cutoff = frequencyToCutoff (filter.getCutoff ());
            voice.resonance = (int) Math.clamp (Math.round (filter.getResonance () * 15.0), 0, 15) << 3;

            final double filterDepth = filter.getCutoffEnvelopeModulator ().getDepth ();
            if (filterDepth > 0)
            {
                final IEnvelope filterEnvelope = filter.getCutoffEnvelopeModulator ().getSource ();
                convertEnvelope (voice.filterRate, voice.filterStop, filterEnvelope);
                final int maxLevel = (int) Math.clamp (Math.round (filterDepth * 255.0), 0, 255);
                for (int i = 0; i < CasioFZVoice.NUM_ENVELOPE_STAGES; i++)
                    voice.filterStop[i] = Math.min (voice.filterStop[i], maxLevel);
                voice.filterSustainPoint = 1;
                voice.filterEndPoint = 2;
            }
        }

        return voice;
    }


    /**
     * Convert the model envelope into the first three stages of an 8-stage rate/level envelope:
     * attack to full level, decay to the sustain level and the release to zero.
     *
     * @param rates The rates of the 8 stages to fill
     * @param stops The stop levels of the 8 stages to fill
     * @param envelope The envelope to convert
     */
    private static void convertEnvelope (final int [] rates, final int [] stops, final IEnvelope envelope)
    {
        final double attackTime = Math.max (0, envelope.getAttackTime ());
        final double sustainLevel = envelope.getSustainLevel () < 0 ? 1 : Math.clamp (envelope.getSustainLevel (), 0, 1);
        final double decayTime = Math.max (0, envelope.getDecayTime ());
        final double releaseTime = Math.max (0, envelope.getReleaseTime ());

        final int sustainStop = (int) Math.round (sustainLevel * 255.0);
        rates[0] = CasioFZVoice.secondsToRate (attackTime, 255);
        stops[0] = 255;
        rates[1] = CasioFZVoice.secondsToRate (decayTime, 255 - sustainStop);
        stops[1] = sustainStop;
        rates[2] = CasioFZVoice.secondsToRate (releaseTime, sustainStop);
        stops[2] = 0;
        for (int i = 3; i < CasioFZVoice.NUM_ENVELOPE_STAGES; i++)
        {
            rates[i] = 127;
            stops[i] = 0;
        }
    }


    /**
     * Mix the channels of interleaved 16-bit samples into one mono channel.
     *
     * @param data The interleaved sample data in little-endian order
     * @param numberOfChannels The number of channels
     * @return The mono sample data, the input data if it is already mono
     */
    private static byte [] convertToMono (final byte [] data, final int numberOfChannels)
    {
        if (numberOfChannels <= 1)
            return data;

        final int numFrames = data.length / (2 * numberOfChannels);
        final byte [] result = new byte [numFrames * 2];
        for (int frame = 0; frame < numFrames; frame++)
        {
            int sum = 0;
            for (int channel = 0; channel < numberOfChannels; channel++)
            {
                final int offset = (frame * numberOfChannels + channel) * 2;
                sum += (short) (data[offset] & 0xFF | data[offset + 1] << 8);
            }
            final int value = sum / numberOfChannels;
            result[frame * 2] = (byte) value;
            result[frame * 2 + 1] = (byte) (value >> 8);
        }
        return result;
    }


    /**
     * Map a frequency to a FZ cutoff value. This is the inverse of
     * {@link CasioFZDetector#cutoffToFrequency(int)}.
     *
     * @param frequency The frequency in Hertz
     * @return The cutoff value (0-127)
     */
    private static int frequencyToCutoff (final double frequency)
    {
        if (frequency <= 20)
            return 0;
        final int cutoff = (int) Math.round (Math.log (frequency / 20.0) / Math.log (2) * 127.0 / 9.8);
        return Math.clamp (cutoff, 0, 127);
    }


    /**
     * Convert a name into up to 12 upper case ASCII characters.
     *
     * @param name The name to convert
     * @return The converted name
     */
    private static String toFZName (final String name)
    {
        final StringBuilder sb = new StringBuilder ();
        for (final char c: name.toUpperCase (Locale.US).toCharArray ())
        {
            if (c >= 0x20 && c < 0x7F)
                sb.append (c);
            if (sb.length () == 12)
                break;
        }
        return sb.toString ();
    }
}
