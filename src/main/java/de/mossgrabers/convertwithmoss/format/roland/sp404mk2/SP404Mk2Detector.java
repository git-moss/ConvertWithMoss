// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.sp404mk2;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;


/**
 * Detects Roland SP-404MK2 projects. A project is a folder holding a <i>PADCONF.BIN</i> pad
 * configuration and a <i>SMPL</i> sub-folder of <i>BANK&lt;bank&gt;-&lt;pad&gt;.SMP</i> sample
 * files. Since the SP-404MK2 is a pad sampler (each of its 160 pads plays one sample, there are no
 * key-ranges), every populated bank is converted into one multi-sample whose pads become single-key
 * zones - the same mapping used for the other pad devices. Projects written by
 * {@link SP404Mk2Creator} round-trip through this detector.
 *
 * @author Jürgen Moßgraber
 */
public class SP404Mk2Detector extends AbstractDetector<MetadataSettingsUI>
{
    /** The MIDI key of the first pad of a bank; the following pads use ascending keys. */
    private static final int BASE_NOTE = 36;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SP404Mk2Detector (final INotifier notifier)
    {
        super ("Roland SP-404MK2", "SP404MK2", notifier, new MetadataSettingsUI ("SP404MK2"), SP404Mk2Constants.PADCONF_ENDING);
    }


    /** {@inheritDoc} */
    @Override
    public Set<String> getFileEndings ()
    {
        // The detector scans for the pad configuration (PADCONF.BIN, see the constructor) since it
        // holds the pad mapping. For the format list the SMP sample ending is shown instead: it is
        // the extension unique to the SP-404MK2 and the one users recognize.
        return Set.of (SP404Mk2Constants.SAMPLE_ENDING);
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final byte [] data = Files.readAllBytes (sourceFile.toPath ());
            final int headerSize = SP404Mk2Constants.headerSizeForLength (data.length);
            if (headerSize < 0 || !SP404Mk2Constants.hasMagic (data, SP404Mk2Constants.RFPD_MAGIC))
                return Collections.emptyList ();
            return this.parseProject (sourceFile, data, headerSize);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Parse the pad configuration and build one multi-sample source per populated bank.
     *
     * @param sourceFile The PADCONF.BIN file
     * @param data The content of the file
     * @param headerSize The header size, which also determines the pad-metadata and pad-name block
     *            offsets (differs between the export and the internal PADCONF.BIN form)
     * @return The multi-sample sources
     * @throws IOException Could not read a sample file
     */
    private List<IMultisampleSource> parseProject (final File sourceFile, final byte [] data, final int headerSize) throws IOException
    {
        final int metaStart = headerSize;
        final int nameStart = SP404Mk2Constants.nameBlockStart (headerSize);
        final File smplFolder = new File (sourceFile.getParentFile (), "SMPL");
        final String projectName = cleanProjectName (sourceFile.getParentFile ().getName ());
        final List<IGroup> bankGroups = new ArrayList<> ();
        final List<Integer> bankIndices = new ArrayList<> ();
        int totalPads = 0;

        for (int bank = 0; bank < SP404Mk2Constants.BANK_COUNT; bank++)
        {
            final IGroup group = new DefaultGroup ("Pads");
            for (int pad = 0; pad < SP404Mk2Constants.PADS_PER_BANK; pad++)
            {
                final int padIndex = bank * SP404Mk2Constants.PADS_PER_BANK + pad;
                final String padName = readPadName (data, nameStart, padIndex);
                if (padName.isEmpty ())
                    continue;

                final File smpFile = new File (smplFolder, String.format ("BANK%d-%02d.SMP", Integer.valueOf (bank + 1), Integer.valueOf (pad + 1)));
                if (!smpFile.exists ())
                {
                    this.notifier.logError ("IDS_SP404MK2_SAMPLE_MISSING", smpFile.getName ());
                    continue;
                }

                final ISampleZone zone = this.createZone (data, metaStart, padIndex, pad, padName, smpFile);
                if (zone != null)
                {
                    group.addSampleZone (zone);
                    totalPads++;
                }
            }

            if (!group.getSampleZones ().isEmpty ())
            {
                bankGroups.add (group);
                bankIndices.add (Integer.valueOf (bank));
            }
        }

        if (bankGroups.isEmpty ())
        {
            this.notifier.logError ("IDS_SP404MK2_NO_PADS", projectName);
            return Collections.emptyList ();
        }

        // Only distinguish the banks by name if there is more than one.
        final boolean singleBank = bankGroups.size () == 1;
        final List<IMultisampleSource> results = new ArrayList<> ();
        for (int i = 0; i < bankGroups.size (); i++)
        {
            final String name = singleBank ? projectName : projectName + " - Bank " + (char) ('A' + bankIndices.get (i).intValue ());
            results.add (this.createMultisampleSource (sourceFile, name, List.of (bankGroups.get (i))));
        }
        this.notifier.log ("IDS_SP404MK2_READING_PROJECT", projectName, Integer.toString (totalPads));
        return results;
    }


    /**
     * Create a single-key zone from a pad. The full sample is extracted, the start/end trim and the
     * loop start of the pad are set as the play range and loop of the zone.
     *
     * @param data The pad configuration
     * @param metaStart The offset of the pad-metadata block
     * @param padIndex The global pad index (0-159)
     * @param padInBank The pad index within its bank (0-15), which selects the MIDI key
     * @param name The pad name
     * @param smpFile The RFWV sample file
     * @return The zone or null if the sample could not be read
     * @throws IOException Could not read the sample file
     */
    private ISampleZone createZone (final byte [] data, final int metaStart, final int padIndex, final int padInBank, final String name, final File smpFile) throws IOException
    {
        final byte [] smp = Files.readAllBytes (smpFile.toPath ());
        if (smp.length < SP404Mk2Constants.SAMPLE_DATA_OFFSET || !SP404Mk2Constants.hasMagic (smp, SP404Mk2Constants.RFWV_MAGIC))
        {
            this.notifier.logError ("IDS_SP404MK2_SAMPLE_INVALID", smpFile.getName ());
            return null;
        }

        final int rate = (int) SP404Mk2Constants.getU32 (smp, SP404Mk2Constants.RFWV_RATE);
        final int channels = (int) SP404Mk2Constants.getU32 (smp, SP404Mk2Constants.RFWV_CHANNELS);
        final int bits = (int) SP404Mk2Constants.getU32 (smp, SP404Mk2Constants.RFWV_BITS);
        if (channels < 1 || channels > 2 || bits != 16)
        {
            this.notifier.logError ("IDS_SP404MK2_SAMPLE_INVALID", smpFile.getName ());
            return null;
        }

        final int bytesPerFrame = channels * 2;
        // The PCM data starts after the 480 byte block which follows the header fields
        final int pcmLength = (smp.length - SP404Mk2Constants.SAMPLE_DATA_OFFSET) / bytesPerFrame * bytesPerFrame;
        final int frames = pcmLength / bytesPerFrame;
        if (frames <= 0)
            return null;
        final byte [] pcm = new byte [pcmLength];
        System.arraycopy (smp, SP404Mk2Constants.SAMPLE_DATA_OFFSET, pcm, 0, pcmLength);
        SP404Mk2Constants.swap16 (pcm);

        final int key = BASE_NOTE + padInBank;
        final ISampleZone zone = new DefaultSampleZone (name, key, key);
        zone.setKeyRoot (key);
        zone.setSampleData (new InMemorySampleData (new DefaultAudioMetadata (channels, rate, 16, frames), pcm));

        final int recordOffset = metaStart + padIndex * SP404Mk2Constants.PAD_RECORD_SIZE;
        final int start = toFrame (data, recordOffset + SP404Mk2Constants.PAD_START, bytesPerFrame, frames);
        int stop = toFrame (data, recordOffset + SP404Mk2Constants.PAD_END2, bytesPerFrame, frames);
        if (stop <= start)
            stop = frames;

        // HOLD plays only the given percentage of the play range
        final int hold = Math.clamp (SP404Mk2Constants.getS32 (data, recordOffset + SP404Mk2Constants.PAD_HOLD), 1, 100);
        if (hold < 100)
            stop = start + Math.max (1, (int) Math.round ((stop - start) * hold / 100.0));
        zone.setStart (start);
        zone.setStop (stop);

        final int volume = (int) SP404Mk2Constants.getU32 (data, recordOffset + SP404Mk2Constants.PAD_VOLUME);
        zone.setGain (MathUtils.valueToDb (Math.clamp (volume, 1, 127) / 127.0));
        final int pan = (int) SP404Mk2Constants.getU32 (data, recordOffset + SP404Mk2Constants.PAD_PAN);
        zone.setPanning (Math.clamp ((pan - SP404Mk2Constants.PAN_CENTER) / 63.0, -1.0, 1.0));

        // With VINYL on the pitch follows the speed and the pitch fields are not used
        if (SP404Mk2Constants.getU32 (data, recordOffset + SP404Mk2Constants.PAD_VINYL) == 0)
        {
            final int pitch = SP404Mk2Constants.getS32 (data, recordOffset + SP404Mk2Constants.PAD_PITCH);
            final int fine = SP404Mk2Constants.getS32 (data, recordOffset + SP404Mk2Constants.PAD_FINE);
            zone.setTuning (pitch + fine / 100.0);
        }

        // Without GATE the sample plays to its end
        zone.setOneShot (SP404Mk2Constants.getU32 (data, recordOffset + SP404Mk2Constants.PAD_GATE) == 0);
        final int playMode = SP404Mk2Constants.getS32 (data, recordOffset + SP404Mk2Constants.PAD_PLAY_MODE);
        zone.setReversed ((playMode & SP404Mk2Constants.PLAY_MODE_REVERSE) != 0);

        zone.setExclusiveGroup (Math.clamp (SP404Mk2Constants.getS32 (data, recordOffset + SP404Mk2Constants.PAD_MUTE_GROUP), 0, SP404Mk2Constants.MUTE_GROUP_COUNT));

        final long flags = SP404Mk2Constants.getU32 (data, recordOffset + SP404Mk2Constants.PAD_FLAGS);
        if ((flags & SP404Mk2Constants.FLAG_FIXED_VELOCITY) != 0)
            zone.getAmplitudeVelocityModulator ().setDepth (0);

        final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        envelope.setAttackTime (toEnvelopeTime (SP404Mk2Constants.getS32 (data, recordOffset + SP404Mk2Constants.PAD_ATTACK)));
        envelope.setReleaseTime (toEnvelopeTime (SP404Mk2Constants.getS32 (data, recordOffset + SP404Mk2Constants.PAD_RELEASE)));

        if (SP404Mk2Constants.getU32 (data, recordOffset + SP404Mk2Constants.PAD_LOOP) != 0)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType ((playMode & SP404Mk2Constants.PLAY_MODE_PING_PONG) == 0 ? LoopType.FORWARDS : LoopType.ALTERNATING);
            final int loopStart = toFrame (data, recordOffset + SP404Mk2Constants.PAD_LOOP_START, bytesPerFrame, frames);
            loop.setStart (loopStart >= start && loopStart < stop ? loopStart : start);
            // The loop runs to the end of the play range, the end of the model is its last frame
            loop.setEnd (stop - 1);
            zone.addLoop (loop);
        }

        return zone;
    }


    /**
     * Convert a sample position of a pad record (a byte offset into the .SMP file) into a frame.
     *
     * @param data The pad configuration
     * @param offset The offset of the position field
     * @param bytesPerFrame The number of bytes of one frame
     * @param frames The number of frames of the sample
     * @return The frame, clamped to the sample
     */
    private static int toFrame (final byte [] data, final int offset, final int bytesPerFrame, final int frames)
    {
        final long position = SP404Mk2Constants.getU32 (data, offset);
        return Math.clamp ((position - SP404Mk2Constants.SAMPLE_DATA_OFFSET) / bytesPerFrame, 0, frames);
    }


    /**
     * Convert an envelope attack or release value into seconds. The manual specifies 3 seconds for
     * the maximum value 127, a linear scale is assumed.
     *
     * @param value The value, 0-127
     * @return The time in seconds
     */
    private static double toEnvelopeTime (final int value)
    {
        return Math.clamp (value, 0, SP404Mk2Constants.ENVELOPE_MAX) * SP404Mk2Constants.ENVELOPE_MAX_TIME / SP404Mk2Constants.ENVELOPE_MAX;
    }


    /**
     * Read a pad name from the name block. Empty (all-space) names indicate an unused pad.
     *
     * @param data The pad configuration
     * @param nameStart The offset of the pad-name block
     * @param padIndex The global pad index (0-159)
     * @return The trimmed name, or an empty string for an unused pad
     */
    private static String readPadName (final byte [] data, final int nameStart, final int padIndex)
    {
        final int offset = nameStart + padIndex * SP404Mk2Constants.NAME_SIZE;
        int length = 0;
        while (length < SP404Mk2Constants.NAME_MAX_CHARS && data[offset + length] != 0)
            length++;
        return new String (data, offset, length, StandardCharsets.US_ASCII).trim ();
    }


    /**
     * Turn a project folder name into a display name, stripping a leading "PROJECT_XX" or
     * "PROJECT_XX-" slot prefix used by the device's project export.
     *
     * @param folderName The name of the project folder
     * @return The cleaned name
     */
    private static String cleanProjectName (final String folderName)
    {
        String name = folderName;
        if (name.toUpperCase (Locale.US).startsWith ("PROJECT_") && name.length () > 10)
        {
            name = name.substring (10);
            if (!name.isEmpty () && (name.charAt (0) == '-' || name.charAt (0) == '_' || name.charAt (0) == ' '))
                name = name.substring (1);
        }
        return name.isBlank () ? folderName : name.trim ();
    }
}
