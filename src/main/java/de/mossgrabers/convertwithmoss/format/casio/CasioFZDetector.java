// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.casio;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.format.casio.CasioFZDisk.CasioFZFile;


/**
 * Detector for Casio FZ-1/FZ-10M/FZ-20M files: floppy disk images (img, hfe) as well as bare full
 * dump (fzf), voice (fzv) and bank (fzb) files. Each bank becomes one multi-sample, dumps without
 * banks become one multi-sample with the key ranges of the voices.
 *
 * @author Jürgen Moßgraber
 */
public class CasioFZDetector extends AbstractDetector<MetadataSettingsUI>
{
    /** What the sampler adds to a cut-off value before it reaches the filter chips. */
    private static final int FILTER_CODE_OFFSET = 32;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public CasioFZDetector (final INotifier notifier)
    {
        super ("Casio FZ-1/10M/20M", "CasioFZ", notifier, new MetadataSettingsUI ("CasioFZ"), ".fzf", ".fzv", ".fzb", ".img", ".hfe");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        try
        {
            final CasioFZDisk disk = new CasioFZDisk (sourceFile);
            final List<IMultisampleSource> results = new ArrayList<> ();
            for (final CasioFZFile file: disk.getFiles ())
                results.addAll (this.readFile (sourceFile, file));
            if (results.isEmpty ())
                this.notifier.logError ("IDS_FZ_NO_INSTRUMENTS_FOUND", sourceFile.getAbsolutePath ());
            return results;
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Read all banks and voices from one FZ file.
     *
     * @param sourceFile The source file which contains the FZ file
     * @param file The FZ file
     * @return The multi-samples
     * @throws IOException The file is malformed
     */
    private List<IMultisampleSource> readFile (final File sourceFile, final CasioFZFile file) throws IOException
    {
        final int numberOfVoices;
        final int numberOfBanks;
        switch (file.type ())
        {
            case CasioFZDisk.TYPE_FULL_DUMP:
                numberOfVoices = file.getCounter (0);
                numberOfBanks = file.getCounter (1);
                break;
            case CasioFZDisk.TYPE_VOICE:
                numberOfVoices = 1;
                numberOfBanks = 0;
                break;
            case CasioFZDisk.TYPE_BANK:
                numberOfVoices = file.getCounter (0);
                numberOfBanks = 1;
                break;
            default:
                // Effect, sequence and program files do not contain any samples
                return Collections.emptyList ();
        }
        final int numberOfWaveBlocks = file.getCounter (2);

        final byte [] content = file.content ();
        final int voiceBlocks = (numberOfVoices + 3) / 4;
        final int parameterLength = (numberOfBanks + voiceBlocks) * CasioFZDisk.SECTOR_SIZE;
        if (numberOfVoices <= 0 || numberOfVoices > 64 || numberOfBanks > 8 || content.length < parameterLength)
            throw new IOException ("Malformed FZ file: " + file.name ());
        // Some circulating dump files are truncated; the missing wave data plays as silence
        final int expectedLength = parameterLength + numberOfWaveBlocks * CasioFZDisk.SECTOR_SIZE;
        if (content.length < expectedLength)
            this.notifier.logError ("IDS_FZ_WAVE_TRUNCATED", file.name (), Integer.toString (expectedLength - content.length));

        // Read the banks
        final List<CasioFZBank> banks = new ArrayList<> ();
        for (int i = 0; i < numberOfBanks; i++)
        {
            final CasioFZBank bank = new CasioFZBank ();
            bank.read (content, i * CasioFZDisk.SECTOR_SIZE);
            banks.add (bank);
        }

        // The effect parameters are located in the first bank block; the pitch bend range is
        // stored in 1/8 semitone steps (up to 12 semitones = 96). Only full dumps carry them,
        // bare voice and bank dumps have stale memory in that area
        int bendRangeCents = -1;
        if (file.type () == CasioFZDisk.TYPE_FULL_DUMP && numberOfBanks > 0)
        {
            final int bendRange = content[960] & 0xFF;
            if (bendRange <= 96)
                bendRangeCents = bendRange * 100 / 8;
        }

        // Read the voices
        final List<CasioFZVoice> voices = new ArrayList<> ();
        final int voicesOffset = numberOfBanks * CasioFZDisk.SECTOR_SIZE;
        for (int i = 0; i < numberOfVoices; i++)
        {
            final CasioFZVoice voice = new CasioFZVoice ();
            voice.read (content, voicesOffset + i * 256);
            voices.add (voice);
        }

        // The wave pool with all 16-bit samples; the addresses of the voices are word addresses.
        // Full dumps store the wave memory from address zero. Should the addresses not fit into
        // the pool (observed convention for bare voice dumps), they are relative to the lowest
        // wave start instead.
        final int wavePoolOffset = voicesOffset + voiceBlocks * CasioFZDisk.SECTOR_SIZE;
        final byte [] wavePool = new byte [numberOfWaveBlocks * CasioFZDisk.SECTOR_SIZE];
        System.arraycopy (content, wavePoolOffset, wavePool, 0, Math.min (wavePool.length, content.length - wavePoolOffset));
        long waveBase = 0;
        for (final CasioFZVoice voice: voices)
            if (voice.mode != CasioFZVoice.MODE_NO_SOUND && voice.waveEnd * 2 > wavePool.length)
            {
                waveBase = Long.MAX_VALUE;
                for (final CasioFZVoice v: voices)
                    if (v.mode != CasioFZVoice.MODE_NO_SOUND)
                        waveBase = Math.min (waveBase, v.waveStart);
                break;
            }

        // Create one multi-sample for each defined bank; if there are no banks, the voices with
        // their own key ranges make up one multi-sample
        final List<IMultisampleSource> results = new ArrayList<> ();
        if (banks.isEmpty ())
        {
            final IGroup group = new DefaultGroup ("Group 1");
            for (final CasioFZVoice voice: voices)
            {
                final ISampleZone zone = createZone (voice, wavePool, waveBase, bendRangeCents);
                if (zone != null)
                {
                    zone.setKeyLow (Math.clamp (voice.lowKey, 0, 127));
                    zone.setKeyHigh (Math.clamp (voice.highKey, 0, 127));
                    zone.setKeyRoot (Math.clamp (voice.centerKey, 0, 127));
                    group.addSampleZone (zone);
                }
            }
            if (!group.getSampleZones ().isEmpty ())
            {
                final String name = file.name ().isBlank () ? voices.get (0).name : file.name ();
                results.add (this.createMultisampleSource (sourceFile, name, Collections.singletonList (group)));
            }
            return results;
        }

        for (int bankIndex = 0; bankIndex < banks.size (); bankIndex++)
        {
            final CasioFZBank bank = banks.get (bankIndex);
            if (bank.numberOfAreas <= 0)
                continue;

            final IGroup group = new DefaultGroup ("Group 1");
            for (int area = 0; area < Math.min (bank.numberOfAreas, CasioFZBank.MAX_AREAS); area++)
            {
                final int voiceIndex = bank.voicePointer[area];
                // Bit 15 marks an area without an assigned voice (e.g. the placeholder areas
                // of the 'YOUR TURN' banks of the Soundwaves library)
                if ((voiceIndex & 0x8000) != 0)
                    continue;
                if (voiceIndex >= voices.size ())
                {
                    this.notifier.logError ("IDS_FZ_VOICE_OUT_OF_BOUNDS", Integer.toString (voiceIndex), bank.name);
                    continue;
                }
                final ISampleZone zone = createZone (voices.get (voiceIndex), wavePool, waveBase, bendRangeCents);
                if (zone == null)
                    continue;

                zone.setKeyLow (Math.clamp (bank.lowKey[area], 0, 127));
                zone.setKeyHigh (Math.clamp (bank.highKey[area], 0, 127));
                zone.setKeyRoot (Math.clamp (bank.centerKey[area], 0, 127));
                zone.setVelocityLow (Math.clamp (bank.lowVelocity[area], 1, 127));
                zone.setVelocityHigh (Math.clamp (bank.highVelocity[area], 1, 127));
                // The area volume balances the voices of a bank (127 = full volume)
                final int areaVolume = bank.volume[area];
                if (areaVolume > 0 && areaVolume < 127)
                    zone.setGain (Math.max (-40, 20 * Math.log10 (areaVolume / 127.0)));
                group.addSampleZone (zone);
            }

            if (group.getSampleZones ().isEmpty ())
                continue;

            String name = bank.name.isBlank () ? file.name () + " Bank " + (bankIndex + 1) : bank.name;
            if (name.isBlank ())
                name = "Bank " + (bankIndex + 1);
            results.add (this.createMultisampleSource (sourceFile, name, Collections.singletonList (group)));
        }
        return results;
    }


    /**
     * Create a sample zone from a voice.
     *
     * @param voice The voice
     * @param wavePool The wave data of the file
     * @param waveBase The word address at which the wave pool starts
     * @param bendRangeCents The pitch bend range in cents, -1 if not present
     * @return The zone or null if the voice has no sound
     */
    private static ISampleZone createZone (final CasioFZVoice voice, final byte [] wavePool, final long waveBase, final int bendRangeCents)
    {
        if (voice.mode == CasioFZVoice.MODE_NO_SOUND)
            return null;

        final int poolWords = wavePool.length / 2;
        final int waveStart = Math.clamp (voice.waveStart - waveBase, 0, poolWords);
        final int waveEnd = Math.clamp (voice.waveEnd - waveBase, waveStart, poolWords);
        final int numFrames = waveEnd - waveStart;
        if (numFrames <= 0)
            return null;

        // Extract the 16-bit little-endian sample of the voice from the wave pool
        final byte [] pcm = new byte [numFrames * 2];
        System.arraycopy (wavePool, waveStart * 2, pcm, 0, pcm.length);
        final InMemorySampleData sampleData = new InMemorySampleData (new DefaultAudioMetadata (1, voice.getSampleRate (), 16, numFrames), pcm);

        final ISampleZone zone = new DefaultSampleZone (voice.name.isBlank () ? "Voice" : voice.name, sampleData);
        zone.setStart (Math.clamp (voice.generatorStart - waveBase - waveStart, 0, numFrames));
        zone.setStop (Math.clamp (voice.generatorEnd - waveBase - waveStart, 0, numFrames));
        zone.setReversed (voice.mode == CasioFZVoice.MODE_REVERSED);
        // The pitch can be corrected in 1/256 semitone steps
        zone.setTuning (voice.pitch / 256.0);
        if (bendRangeCents >= 0)
        {
            zone.setBendUp (bendRangeCents);
            zone.setBendDown (-bendRangeCents);
        }

        // The sustain loop; the multi-loops before it have no equivalent in the model
        if (voice.loopSustain < CasioFZVoice.NUM_LOOPS)
        {
            final int loopStart = (int) (voice.loopStart[voice.loopSustain] - waveBase - waveStart);
            final int loopEnd = (int) (voice.loopEndAddress[voice.loopSustain] - waveBase - waveStart);
            if (loopStart >= 0 && loopStart < loopEnd && loopEnd <= numFrames)
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                loop.setType (LoopType.FORWARDS);
                loop.setStart (loopStart);
                loop.setEnd (loopEnd);
                loop.setCrossfade (voice.loopCrossfade[voice.loopSustain] / 1023.0);
                zone.getLoops ().add (loop);
            }
        }

        // The velocity to amplitude modulation
        zone.getAmplitudeVelocityModulator ().setDepth (Math.clamp (voice.velocityAmpDepth / 127.0, -1, 1));
        zone.setAmplitudeKeyTracking (Math.clamp (voice.ampKeyFollow / 127.0, -1, 1));

        // The amplitude envelope
        final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        zone.getAmplitudeEnvelopeModulator ().setDepth (1);
        convertEnvelope (amplitudeEnvelope, voice.ampRate, voice.ampStop, voice.ampSustainPoint, voice.ampEndPoint);

        // The filter with its envelope; a cutoff of 127 means the filter is open
        if (voice.cutoff < 127)
        {
            final double frequency = cutoffToFrequency (voice.cutoff);
            // Only the upper 4 bits of the resonance are effective
            final IFilter filter = new DefaultFilter (FilterType.LOW_PASS, 2, frequency, (voice.resonance >> 3) / 15.0);
            zone.setFilter (filter);

            int maxStop = 0;
            for (int i = 0; i <= Math.min (voice.filterEndPoint, CasioFZVoice.NUM_ENVELOPE_STAGES - 1); i++)
                maxStop = Math.max (maxStop, voice.filterStop[i]);
            if (maxStop > 0)
            {
                filter.getCutoffEnvelopeModulator ().setDepth (maxStop / 255.0);
                convertEnvelope (filter.getCutoffEnvelopeModulator ().getSource (), voice.filterRate, voice.filterStop, voice.filterSustainPoint, voice.filterEndPoint);
            }
        }

        return zone;
    }


    /**
     * Convert an 8-stage rate/level envelope into the model envelope. The stage times use the
     * approximated rate law of {@link CasioFZVoice#stageSeconds(int, int)}.
     *
     * @param envelope The envelope to fill
     * @param rates The rates of the 8 stages
     * @param stops The stop levels of the 8 stages
     * @param sustainPoint The index of the stage at which the envelope sustains
     * @param endPoint The index of the last stage
     */
    private static void convertEnvelope (final IEnvelope envelope, final int [] rates, final int [] stops, final int sustainPoint, final int endPoint)
    {
        final int sustainIndex = Math.clamp (sustainPoint, 0, CasioFZVoice.NUM_ENVELOPE_STAGES - 1);
        final int endIndex = Math.clamp (endPoint, sustainIndex, CasioFZVoice.NUM_ENVELOPE_STAGES - 1);

        envelope.setAttackTime (CasioFZVoice.stageSeconds (rates[0], stops[0]));

        double decayTime = 0;
        int previousStop = stops[0];
        for (int i = 1; i <= sustainIndex; i++)
        {
            decayTime += CasioFZVoice.stageSeconds (rates[i], Math.abs (stops[i] - previousStop));
            previousStop = stops[i];
        }
        envelope.setDecayTime (decayTime);
        envelope.setSustainLevel (stops[sustainIndex] / 255.0);

        double releaseTime = 0;
        previousStop = stops[sustainIndex];
        for (int i = sustainIndex + 1; i <= endIndex; i++)
        {
            releaseTime += CasioFZVoice.stageSeconds (rates[i], Math.abs (stops[i] - previousStop));
            previousStop = stops[i];
        }
        if (releaseTime > 0)
            envelope.setReleaseTime (releaseTime);
    }


    /**
     * Map a FZ cut-off value to a frequency. The value is not what the filter receives: the
     * sampler adds 32 to it - and the modulation of the filter envelope, the LFO and the key
     * follow - before it writes the result as an 8 bit quantity to its filter chips. The range of
     * the value therefore covers exactly the codes 32 to 159 of those chips, and 159 is both the
     * value the sampler initializes them with and the point at which its own programming
     * documentation calls the filter fully open.
     *
     * The frequencies which those codes produce are not exponential but follow three linear
     * sections: the service manual of the sampler gives 20 Hz for code 0 and 20 kHz for code 159,
     * and the operating system picks code 108 for a 4.5 kHz and code 124 for a 9 kHz anti-aliasing
     * filter when it samples, which fixes the two points in between.
     *
     * @param cutoff The cut-off value
     * @return The frequency in Hertz
     */
    static double cutoffToFrequency (final int cutoff)
    {
        final int code = Math.clamp (cutoff, 0, 255) + FILTER_CODE_OFFSET;
        if (code <= 108)
            return 20.0 + code * 41.48;
        if (code <= 124)
            return 4500.0 + (code - 108) * 281.25;
        if (code <= 159)
            return 9000.0 + (code - 124) * 314.29;
        return 20000.0;
    }
}
