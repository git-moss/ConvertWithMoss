// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.sp404mk2;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;


/**
 * Creator for Roland SP-404MK2 projects. A source's zones are laid onto the pads of a bank (16 per
 * bank, overflowing into the next bank); a library of sources fills consecutive banks. The output
 * is a project folder with a <i>PADCONF.BIN</i> pad configuration and a <i>SMPL</i> folder of RFWV
 * <i>BANK&lt;bank&gt;-&lt;pad&gt;.SMP</i> samples (48 kHz / 16-bit). Copy the folder into the
 * <i>IMPORT</i> folder of an SD card and load it with the device's <i>IMPORT PROJECT</i> function.
 * Written projects have not been verified on hardware yet. The SP-404MKII App does not import the
 * samples since the 480 byte block of the sample files (which looks encrypted) cannot be created
 * and is written zeroed. Files round-trip through {@link SP404Mk2Detector}.
 *
 * @author Jürgen Moßgraber
 */
public class SP404Mk2Creator extends AbstractCreator<EmptySettingsUI>
{
    private static final DestinationAudioFormat DESTINATION_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, SP404Mk2Constants.SAMPLE_RATE, true);

    /** The creator writes the PADCONF.BIN form which the device and the app store and export. */
    private static final int                    META_START         = SP404Mk2Constants.HEADER_SIZE_INTERNAL;
    private static final int                    NAME_START         = SP404Mk2Constants.nameBlockStart (SP404Mk2Constants.HEADER_SIZE_INTERNAL);
    private static final int                    MARKS_START        = NAME_START + SP404Mk2Constants.PAD_COUNT * SP404Mk2Constants.NAME_SIZE;

    /** Default project / pad tempo (120.00 BPM) in the BPM * 100 unit. */
    private static final int                    DEFAULT_PAD_BPM    = 12000;
    /** Default per-bank tempo (120.00 BPM) in the BPM * 200 unit. */
    private static final int                    DEFAULT_BANK_BPM   = 24000;
    /** Playback speed for 100%. */
    private static final int                    SPEED_100          = 10000;

    /** Maps the exclusive groups of the sources to the mute groups (1-10) of the project. */
    private final Map<Integer, Integer>         muteGroups         = new HashMap<> ();


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SP404Mk2Creator (final INotifier notifier)
    {
        super ("Roland SP-404MK2", "SP404MK2", notifier, EmptySettingsUI.INSTANCE);
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.writeProject (destinationFolder, List.of (multisampleSource), multisampleSource.getName ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPresetLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        if (!multisampleSources.isEmpty ())
            this.writeProject (destinationFolder, multisampleSources, libraryName);
    }


    /**
     * Write a project folder for the given sources.
     *
     * @param destinationFolder Where to create the project folder
     * @param multisampleSources The sources to convert
     * @param name The project name
     * @throws IOException Could not write the project
     */
    private void writeProject (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String name) throws IOException
    {
        final File projectFolder = new File (destinationFolder, SafeFileNames.create (name));
        safeCreateDirectory (projectFolder);
        final File smplFolder = new File (projectFolder, "SMPL");
        safeCreateDirectory (smplFolder);
        this.notifier.log ("IDS_SP404MK2_WRITING_PROJECT", projectFolder.getAbsolutePath ());

        final byte [] padconf = new byte [SP404Mk2Constants.PADCONF_SIZE_INTERNAL];
        writeHeader (padconf, name);
        this.muteGroups.clear ();

        int bank = 0;
        int pad = 0;
        int written = 0;
        boolean full = false;

        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            if (full)
                break;

            // The device's fixed sample rate - scale all loop/start/end positions to it.
            this.recalculateSamplePositions (multisampleSource, SP404Mk2Constants.SAMPLE_RATE);

            final List<ISampleZone> zones = new ArrayList<> ();
            for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
                zones.addAll (group.getSampleZones ());
            if (zones.isEmpty ())
                continue;

            for (final ISampleZone zone: zones)
            {
                if (pad >= SP404Mk2Constants.PADS_PER_BANK)
                {
                    bank++;
                    pad = 0;
                }
                if (bank >= SP404Mk2Constants.BANK_COUNT)
                {
                    this.notifier.logError ("IDS_SP404MK2_TOO_MANY_PADS", zone.getName ());
                    full = true;
                    break;
                }
                if (this.writePad (padconf, smplFolder, bank, pad, zone))
                    pad++;
            }

            // Each source starts a fresh bank.
            bank++;
            pad = 0;
        }

        // Count the written pads from the name block for the log.
        for (int i = 0; i < SP404Mk2Constants.PAD_COUNT; i++)
            if (padconf[NAME_START + i * SP404Mk2Constants.NAME_SIZE] != ' ')
                written++;

        final File padconfFile = new File (projectFolder, SP404Mk2Constants.PADCONF_FILE_NAME);
        try (final OutputStream out = new BufferedOutputStream (Files.newOutputStream (padconfFile.toPath ())))
        {
            out.write (padconf);
        }

        this.notifier.log ("IDS_SP404MK2_WRITTEN_PADS", Integer.toString (written));
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Write one pad: its RFWV sample file and its metadata + name record in the pad configuration.
     *
     * @param padconf The pad configuration to patch
     * @param smplFolder The SMPL folder
     * @param bank The bank index (0-9)
     * @param pad The pad index within the bank (0-15)
     * @param zone The zone to write
     * @return True if the pad was written
     * @throws IOException Could not write the sample file
     */
    private boolean writePad (final byte [] padconf, final File smplFolder, final int bank, final int pad, final ISampleZone zone) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
            return false;

        this.logResampling (zone, DESTINATION_FORMAT);
        final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData.get (), DESTINATION_FORMAT);
        final int channels = waveFile.getFormatChunk ().getNumberOfChannels ();
        if (channels < 1 || channels > 2)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MONO", Integer.toString (channels), zone.getName ());
            return false;
        }

        final byte [] littleEndian = waveFile.getDataChunk ().getData ();
        final int bytesPerFrame = channels * 2;
        final int pcmLength = littleEndian.length / bytesPerFrame * bytesPerFrame;
        if (pcmLength <= 0)
            return false;
        final byte [] pcm = new byte [pcmLength];
        System.arraycopy (littleEndian, 0, pcm, 0, pcmLength);
        SP404Mk2Constants.swap16 (pcm);
        final int fileSize = SP404Mk2Constants.SAMPLE_DATA_OFFSET + pcmLength;

        final File smpFile = new File (smplFolder, String.format ("BANK%d-%02d.SMP", Integer.valueOf (bank + 1), Integer.valueOf (pad + 1)));
        try (final OutputStream out = new BufferedOutputStream (Files.newOutputStream (smpFile.toPath ())))
        {
            out.write (buildRfwvHeader (pcmLength, channels));
            out.write (pcm);
        }

        final int padIndex = bank * SP404Mk2Constants.PADS_PER_BANK + pad;
        writePadRecord (padconf, padIndex, zone, fileSize, bytesPerFrame, this.getMuteGroup (zone.getExclusiveGroup ()));
        writePadName (padconf, padIndex, zone.getName ());
        return true;
    }


    /**
     * Get the mute group for an exclusive group. The exclusive groups are numbered in the order in
     * which they appear in the project, since the device has only the 10 mute groups A-J.
     *
     * @param exclusiveGroup The exclusive group, 0 for none
     * @return The mute group (1-10) or 0 if there is none or all 10 mute groups are in use
     */
    private int getMuteGroup (final int exclusiveGroup)
    {
        if (exclusiveGroup <= 0)
            return 0;
        final Integer key = Integer.valueOf (exclusiveGroup);
        final Integer muteGroup = this.muteGroups.get (key);
        if (muteGroup != null)
            return muteGroup.intValue ();
        if (this.muteGroups.size () >= SP404Mk2Constants.MUTE_GROUP_COUNT)
            return 0;
        final int newMuteGroup = this.muteGroups.size () + 1;
        this.muteGroups.put (key, Integer.valueOf (newMuteGroup));
        return newMuteGroup;
    }


    /**
     * Build the 512-byte RFWV header for a sample: the 32 bytes of header fields followed by the
     * 480 byte block, which is written zeroed since its encoding is unknown.
     *
     * @param pcmLength The number of PCM bytes that follow
     * @param channels The number of channels
     * @return The header
     */
    private static byte [] buildRfwvHeader (final int pcmLength, final int channels)
    {
        final byte [] header = new byte [SP404Mk2Constants.SAMPLE_DATA_OFFSET];
        System.arraycopy (SP404Mk2Constants.RFWV_MAGIC, 0, header, 0, 4);
        SP404Mk2Constants.putU32 (header, 4, (long) SP404Mk2Constants.SAMPLE_DATA_OFFSET + pcmLength - 8);
        SP404Mk2Constants.putU32 (header, SP404Mk2Constants.RFWV_RATE, SP404Mk2Constants.SAMPLE_RATE);
        SP404Mk2Constants.putU32 (header, SP404Mk2Constants.RFWV_CHANNELS, channels);
        SP404Mk2Constants.putU32 (header, SP404Mk2Constants.RFWV_BITS, 16);
        return header;
    }


    /**
     * Write the header, lay down the fixed "empty pad" template into all 160 records and fill the
     * marks section, so unused pads match the device's own layout.
     *
     * @param padconf The pad configuration to fill
     * @param projectName The name of the project
     */
    private static void writeHeader (final byte [] padconf, final String projectName)
    {
        System.arraycopy (SP404Mk2Constants.RFPD_MAGIC, 0, padconf, 0, 4);
        SP404Mk2Constants.putU32 (padconf, SP404Mk2Constants.HDR_PAD_COUNT, SP404Mk2Constants.PAD_COUNT);
        SP404Mk2Constants.putU32 (padconf, SP404Mk2Constants.HDR_VERSION, 0x03000000);
        SP404Mk2Constants.putU32 (padconf, SP404Mk2Constants.HDR_DATA_SIZE, (long) SP404Mk2Constants.PAD_COUNT * SP404Mk2Constants.PAD_RECORD_SIZE + (long) SP404Mk2Constants.PAD_COUNT * SP404Mk2Constants.NAME_SIZE);
        // Observed constant header fields of unknown purpose.
        SP404Mk2Constants.putU32 (padconf, 0x10, 0x000124B8);
        SP404Mk2Constants.putU32 (padconf, 0x1C, 0x40);
        SP404Mk2Constants.putU32 (padconf, 0x20, 0x40);
        SP404Mk2Constants.putU32 (padconf, 0x24, 0x40);
        for (int i = 0; i < 5; i++)
            SP404Mk2Constants.putU32 (padconf, 0x2C + i * 4, 0x20);
        for (int bank = 0; bank < SP404Mk2Constants.BANK_COUNT; bank++)
            SP404Mk2Constants.putU32 (padconf, SP404Mk2Constants.HDR_BANK_BPM + bank * 4, DEFAULT_BANK_BPM);
        final byte [] name = projectName.getBytes (StandardCharsets.US_ASCII);
        System.arraycopy (name, 0, padconf, SP404Mk2Constants.HDR_PROJECT_NAME, Math.min (name.length, SP404Mk2Constants.HEADER_SIZE_INTERNAL - SP404Mk2Constants.HDR_PROJECT_NAME - 1));

        // The marks section of each pad: 4 zero bytes followed by 0xFF
        for (int i = 0; i < SP404Mk2Constants.PAD_COUNT; i++)
            for (int j = 4; j < SP404Mk2Constants.MARKS_RECORD_SIZE; j++)
                padconf[MARKS_START + i * SP404Mk2Constants.MARKS_RECORD_SIZE + j] = (byte) 0xFF;

        // Fill the name block with spaces (an all-space name marks an unused pad).
        for (int i = NAME_START; i < NAME_START + SP404Mk2Constants.PAD_COUNT * SP404Mk2Constants.NAME_SIZE; i++)
            padconf[i] = ' ';

        // The fixed scaffold of an unused pad record.
        for (int i = 0; i < SP404Mk2Constants.PAD_COUNT; i++)
            writeEmptyRecord (padconf, i);
    }


    /**
     * Write the fixed constants of an empty pad record.
     *
     * @param padconf The pad configuration
     * @param padIndex The global pad index
     */
    private static void writeEmptyRecord (final byte [] padconf, final int padIndex)
    {
        final int o = META_START + padIndex * SP404Mk2Constants.PAD_RECORD_SIZE;
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_VOLUME, 127);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_GATE, 1);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_USED, 1);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_BPM, DEFAULT_PAD_BPM);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_SPEED, SPEED_100);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_VINYL, 1);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_PAN, SP404Mk2Constants.PAN_CENTER);
        SP404Mk2Constants.putU32 (padconf, o + 0x50, 1);
        SP404Mk2Constants.putU32 (padconf, o + 0x54, 2);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_HOLD, 100);
    }


    /**
     * Overlay the pad's parameters onto its (already scaffolded) record.
     *
     * @param padconf The pad configuration
     * @param padIndex The global pad index
     * @param zone The zone
     * @param fileSize The size of the pad's .SMP file (the sample-end value)
     * @param bytesPerFrame The number of bytes of one frame
     * @param muteGroup The mute group, 0 for none
     */
    private static void writePadRecord (final byte [] padconf, final int padIndex, final ISampleZone zone, final int fileSize, final int bytesPerFrame, final int muteGroup)
    {
        final int o = META_START + padIndex * SP404Mk2Constants.PAD_RECORD_SIZE;
        final int frames = (fileSize - SP404Mk2Constants.SAMPLE_DATA_OFFSET) / bytesPerFrame;
        final int start = Math.clamp (zone.getStart (), 0, frames - 1);
        int stop = zone.getStop ();
        if (stop <= start || stop > frames)
            stop = frames;
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_END, fileSize);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_END2, toPosition (stop, bytesPerFrame));
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_START, toPosition (start, bytesPerFrame));
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_VOLUME, levelFromGain (zone.getGain ()));
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_GATE, zone.isOneShot () ? 0 : 1);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_MUTE_GROUP, muteGroup);

        final List<ISampleLoop> loops = zone.getLoops ();
        int playMode = zone.isReversed () ? SP404Mk2Constants.PLAY_MODE_REVERSE : 0;
        if (loops.isEmpty ())
            SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_LOOP_START, toPosition (start, bytesPerFrame));
        else
        {
            // The loop always runs to the end of the play range
            final ISampleLoop loop = loops.get (0);
            final int loopStart = loop.getStart () >= start && loop.getStart () < stop ? loop.getStart () : start;
            SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_LOOP_START, toPosition (loopStart, bytesPerFrame));
            SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_LOOP, 1);
            SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_LOOP_COUNT, SP404Mk2Constants.LOOP_COUNT_INFINITE);
            if (loop.getType () == LoopType.ALTERNATING)
                playMode |= SP404Mk2Constants.PLAY_MODE_PING_PONG;
        }
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_PLAY_MODE, playMode);

        if (Math.abs (zone.getAmplitudeVelocityModulator ().getDepth ()) < 0.01)
            SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_FLAGS, SP404Mk2Constants.FLAG_FIXED_VELOCITY);

        final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_ATTACK, toEnvelopeValue (envelope.getAttackTime ()));
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_RELEASE, toEnvelopeValue (envelope.getReleaseTime ()));

        // The pitch fields are only used with VINYL off
        final double tuning = zone.getTuning ();
        final int semitones = (int) tuning;
        final int cents = (int) Math.round ((tuning - semitones) * 100.0);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_VINYL, 0);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_PITCH, semitones);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_FINE, cents);

        final int pan = Math.clamp (SP404Mk2Constants.PAN_CENTER + Math.round (zone.getPanning () * 63.0), 0, 127);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_PAN, pan);
    }


    /**
     * Convert a frame into a sample position of a pad record (a byte offset into the .SMP file).
     *
     * @param frame The frame
     * @param bytesPerFrame The number of bytes of one frame
     * @return The position
     */
    private static long toPosition (final int frame, final int bytesPerFrame)
    {
        return SP404Mk2Constants.SAMPLE_DATA_OFFSET + (long) frame * bytesPerFrame;
    }


    /**
     * Convert an envelope attack or release time into the 0-127 value (127 = 3 seconds, a linear
     * scale is assumed).
     *
     * @param time The time in seconds, a negative value is not set
     * @return The value
     */
    private static int toEnvelopeValue (final double time)
    {
        if (time <= 0)
            return 0;
        return Math.clamp (Math.round (time * SP404Mk2Constants.ENVELOPE_MAX / SP404Mk2Constants.ENVELOPE_MAX_TIME), 0, SP404Mk2Constants.ENVELOPE_MAX);
    }


    /**
     * Write a pad name into the name block (up to 23 ASCII characters, NUL-terminated).
     *
     * @param padconf The pad configuration
     * @param padIndex The global pad index
     * @param name The name
     */
    private static void writePadName (final byte [] padconf, final int padIndex, final String name)
    {
        final int offset = NAME_START + padIndex * SP404Mk2Constants.NAME_SIZE;
        final byte [] ascii = name.getBytes (StandardCharsets.US_ASCII);
        final int length = Math.min (ascii.length, SP404Mk2Constants.NAME_MAX_CHARS);
        System.arraycopy (ascii, 0, padconf, offset, length);
        padconf[offset + length] = 0;
    }


    /**
     * Convert a gain in dB to the device's 0-127 level.
     *
     * @param gainDb The gain in dB
     * @return The level, 0-127
     */
    private static int levelFromGain (final double gainDb)
    {
        return Math.clamp ((int) Math.round (Math.pow (10, gainDb / 20.0) * 127.0), 0, 127);
    }
}
