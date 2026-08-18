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
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.FileUtils;


/**
 * Creator for Roland SP-404MK2 projects. A source's zones are laid onto the pads of a bank (16 per
 * bank, overflowing into the next bank); a library of sources fills consecutive banks. The output
 * is a project folder with a <i>PADCONF.BIN</i> pad configuration and a <i>SMPL</i> folder of RFWV
 * <i>BANK&lt;bank&gt;-&lt;pad&gt;.SMP</i> samples (48 kHz / 16-bit). Copy the folder into the
 * <i>IMPORT</i> folder of an SD card and load it with the device's <i>IMPORT PROJECT</i> function.
 * Written projects have not been verified on hardware yet. Files round-trip through
 * {@link SP404Mk2Detector}.
 *
 * @author Jürgen Moßgraber
 */
public class SP404Mk2Creator extends AbstractCreator<EmptySettingsUI>
{
    private static final DestinationAudioFormat DESTINATION_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, SP404Mk2Constants.SAMPLE_RATE, true);

    /** The creator always writes the export PADCONF.BIN form. */
    private static final int                    META_START         = SP404Mk2Constants.HEADER_SIZE_EXPORT;
    private static final int                    NAME_START         = SP404Mk2Constants.nameBlockStart (SP404Mk2Constants.HEADER_SIZE_EXPORT);

    /** Default project / pad tempo (120.00 BPM) in the BPM * 100 unit. */
    private static final int                    DEFAULT_PAD_BPM    = 12000;
    /** Default per-bank tempo (120.00 BPM) in the BPM * 200 unit. */
    private static final int                    DEFAULT_BANK_BPM   = 24000;
    /** Playback speed for 100%. */
    private static final int                    SPEED_100          = 10000;


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
        final File projectFolder = new File (destinationFolder, FileUtils.createSafeFilename (name));
        safeCreateDirectory (projectFolder);
        final File smplFolder = new File (projectFolder, "SMPL");
        safeCreateDirectory (smplFolder);
        this.notifier.log ("IDS_SP404MK2_WRITING_PROJECT", projectFolder.getAbsolutePath ());

        final byte [] padconf = new byte [SP404Mk2Constants.PADCONF_SIZE_EXPORT];
        writeHeader (padconf);

        int bank = 0;
        int pad = 0;
        int written = 0;
        boolean full = false;

        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            if (full)
                break;

            // The device's fixed sample rate - scale all loop/start/end positions to it.
            recalculateSamplePositions (multisampleSource, SP404Mk2Constants.SAMPLE_RATE);

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
        final int fileSize = SP404Mk2Constants.RFWV_HEADER_SIZE + pcmLength;

        final File smpFile = new File (smplFolder, String.format ("BANK%d-%02d.SMP", Integer.valueOf (bank + 1), Integer.valueOf (pad + 1)));
        try (final OutputStream out = new BufferedOutputStream (Files.newOutputStream (smpFile.toPath ())))
        {
            out.write (buildRfwvHeader (pcmLength, channels));
            out.write (pcm);
        }

        final int padIndex = bank * SP404Mk2Constants.PADS_PER_BANK + pad;
        writePadRecord (padconf, padIndex, zone, fileSize);
        writePadName (padconf, padIndex, zone.getName ());
        return true;
    }


    /**
     * Build the 32-byte RFWV header for a sample.
     *
     * @param pcmLength The number of PCM bytes that follow
     * @param channels The number of channels
     * @return The header
     */
    private static byte [] buildRfwvHeader (final int pcmLength, final int channels)
    {
        final byte [] header = new byte [SP404Mk2Constants.RFWV_HEADER_SIZE];
        System.arraycopy (SP404Mk2Constants.RFWV_MAGIC, 0, header, 0, 4);
        SP404Mk2Constants.putU32 (header, 4, (long) SP404Mk2Constants.RFWV_HEADER_SIZE + pcmLength - 8);
        SP404Mk2Constants.putU32 (header, SP404Mk2Constants.RFWV_RATE, SP404Mk2Constants.SAMPLE_RATE);
        SP404Mk2Constants.putU32 (header, SP404Mk2Constants.RFWV_CHANNELS, channels);
        SP404Mk2Constants.putU32 (header, SP404Mk2Constants.RFWV_BITS, 16);
        return header;
    }


    /**
     * Write the header and lay down the fixed "empty pad" template into all 160 records, so unused
     * pads match the device's own layout.
     *
     * @param padconf The pad configuration to fill
     */
    private static void writeHeader (final byte [] padconf)
    {
        System.arraycopy (SP404Mk2Constants.RFPD_MAGIC, 0, padconf, 0, 4);
        SP404Mk2Constants.putU32 (padconf, SP404Mk2Constants.HDR_PAD_COUNT, SP404Mk2Constants.PAD_COUNT);
        SP404Mk2Constants.putU32 (padconf, SP404Mk2Constants.HDR_VERSION, 0x02000000);
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
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_LOOP, 1);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_USED, 1);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_BPM, DEFAULT_PAD_BPM);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_SPEED, SPEED_100);
        SP404Mk2Constants.putU32 (padconf, o + 0x44, 1);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_PAN, SP404Mk2Constants.PAN_CENTER);
        SP404Mk2Constants.putU32 (padconf, o + 0x50, 1);
        SP404Mk2Constants.putU32 (padconf, o + 0x54, 2);
        SP404Mk2Constants.putU32 (padconf, o + 0x5C, 100);
    }


    /**
     * Overlay the pad's parameters onto its (already scaffolded) record. The full sample is played
     * (start at the beginning, end at the sample end).
     *
     * @param padconf The pad configuration
     * @param padIndex The global pad index
     * @param zone The zone
     * @param fileSize The size of the pad's .SMP file (the sample-end value)
     */
    private static void writePadRecord (final byte [] padconf, final int padIndex, final ISampleZone zone, final int fileSize)
    {
        final int o = META_START + padIndex * SP404Mk2Constants.PAD_RECORD_SIZE;
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_END, fileSize);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_END2, fileSize);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_START, SP404Mk2Constants.START_SENTINEL);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_LOOP_START, SP404Mk2Constants.START_SENTINEL);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_VOLUME, levelFromGain (zone.getGain ()));
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_LOOP, zone.getLoops ().isEmpty () ? 0 : 1);

        final double tuning = zone.getTuning ();
        final int semitones = (int) tuning;
        final int cents = (int) Math.round ((tuning - semitones) * 100.0);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_PITCH, semitones);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_FINE, cents);

        final int pan = Math.clamp (SP404Mk2Constants.PAN_CENTER + Math.round (zone.getPanning () * 63.0), 0, 127);
        SP404Mk2Constants.putU32 (padconf, o + SP404Mk2Constants.PAD_PAN, pan);
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
