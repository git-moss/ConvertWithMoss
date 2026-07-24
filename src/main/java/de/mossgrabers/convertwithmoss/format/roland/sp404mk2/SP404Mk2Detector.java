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

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
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
 * configuration and a <i>SMPL</i> sub-folder of <i>BANK&lt;bank&gt;-&lt;pad&gt;.SMP</i> sample files.
 * Since the SP-404MK2 is a pad sampler (each of its 160 pads plays one sample, there are no
 * key-ranges), every populated bank is converted into one multi-sample whose pads become
 * single-key zones - the same mapping used for the other pad devices. Projects written by
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
        super ("Roland SP-404MK2", "SP404MK2", notifier, new MetadataSettingsUI ("SP404MK2"), "padconf.bin");
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
     * Create a single-key zone from a pad. The full sample is extracted; the on-device start/end
     * trim is not applied (its byte-offset encoding is not fully decoded).
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
        if (smp.length < SP404Mk2Constants.RFWV_HEADER_SIZE || !SP404Mk2Constants.hasMagic (smp, SP404Mk2Constants.RFWV_MAGIC))
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
        final int pcmLength = (smp.length - SP404Mk2Constants.RFWV_HEADER_SIZE) / bytesPerFrame * bytesPerFrame;
        final int frames = pcmLength / bytesPerFrame;
        if (frames <= 0)
            return null;
        final byte [] pcm = new byte [pcmLength];
        System.arraycopy (smp, SP404Mk2Constants.RFWV_HEADER_SIZE, pcm, 0, pcmLength);
        SP404Mk2Constants.swap16 (pcm);

        final int key = BASE_NOTE + padInBank;
        final ISampleZone zone = new DefaultSampleZone (name, key, key);
        zone.setKeyRoot (key);
        zone.setSampleData (new InMemorySampleData (new DefaultAudioMetadata (channels, rate, 16, frames), pcm));
        zone.setStart (0);
        zone.setStop (frames);

        final int recordOffset = metaStart + padIndex * SP404Mk2Constants.PAD_RECORD_SIZE;
        final int volume = (int) SP404Mk2Constants.getU32 (data, recordOffset + SP404Mk2Constants.PAD_VOLUME);
        zone.setGain (MathUtils.valueToDb (Math.max (Math.min (volume, 127), 1) / 127.0));
        final int pan = (int) SP404Mk2Constants.getU32 (data, recordOffset + SP404Mk2Constants.PAD_PAN);
        zone.setPanning (Math.clamp ((pan - SP404Mk2Constants.PAN_CENTER) / 63.0, -1.0, 1.0));
        final int pitch = SP404Mk2Constants.getS32 (data, recordOffset + SP404Mk2Constants.PAD_PITCH);
        final int fine = SP404Mk2Constants.getS32 (data, recordOffset + SP404Mk2Constants.PAD_FINE);
        zone.setTuning (pitch + fine / 100.0);

        if (SP404Mk2Constants.getU32 (data, recordOffset + SP404Mk2Constants.PAD_LOOP) != 0)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType (LoopType.FORWARDS);
            loop.setStart (0);
            loop.setEnd (frames);
            zone.addLoop (loop);
        }

        return zone;
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
