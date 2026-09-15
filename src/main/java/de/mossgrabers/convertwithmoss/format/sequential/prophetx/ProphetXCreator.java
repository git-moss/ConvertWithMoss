// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sequential.prophetx;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipOutputStream;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.tools.FileUtils;


/**
 * Creator for Sequential Prophet X / XL sample instruments. An instrument is a folder which
 * contains its samples as WAV files and a group file (ending <i>.grp</i>) with the key, velocity
 * and loop mapping. By default the instrument is written as the archive which the device imports
 * from a USB drive: <i>px/&lt;bank&gt;/&lt;category&gt;/&lt;name&gt;.zip</i>. The format was
 * reverse-engineered from the OS 2.2.2 firmware, see
 * <i>documentation/design/PROPHET_X_GRP_FORMAT.md</i>.
 *
 * @author Jürgen Moßgraber
 */
public class ProphetXCreator extends AbstractWavCreator<ProphetXCreatorUI>
{
    /**
     * The device plays 16 bit and ignores the sample rate of a file: a sample at another rate than
     * the 48 kHz of its audio engine plays transposed, therefore every sample is converted.
     */
    private static final DestinationAudioFormat DESTINATION_FORMAT = new DestinationAudioFormat (new int []
    {
        ProphetXTag.BIT_RESOLUTION
    }, ProphetXTag.SAMPLE_RATE, true);

    private static final String                 WAV_ENDING         = ".wav";
    private static final String                 LINE_ENDING        = "\r\n";
    private static final String                 FALLBACK_NAME      = "Instrument";
    private static final int                    DEFAULT_ROOT_KEY   = 60;
    private static final int                    NUM_VELOCITIES     = 128;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public ProphetXCreator (final INotifier notifier)
    {
        super ("Sequential Prophet X", "ProphetX", notifier, new ProphetXCreatorUI ("ProphetX"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final List<ISampleZone> zones = this.collectZones (multisampleSource);
        if (zones.isEmpty ())
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, multisampleSource.getName (), "-");
            return;
        }
        if (zones.size () > ProphetXTag.MAX_SAMPLES)
            this.notifier.log ("IDS_PROPHETX_TOO_MANY_SAMPLES", Integer.toString (zones.size ()), Integer.toString (ProphetXTag.MAX_SAMPLES));
        this.checkLimitations (zones);

        final int category = this.getCategory (multisampleSource);
        final String bank = ProphetXTag.getUserBank (this.settingsConfiguration.getBankIndex ());

        // The device ignores the sample rate of a WAV file and plays every sample at 48 kHz, so all
        // samples are converted; the audio of a loop and its positions are converted together
        this.recalculateSamplePositions (multisampleSource, ProphetXTag.SAMPLE_RATE);

        if (this.settingsConfiguration.isWriteArchive ())
            this.writeArchive (destinationFolder, multisampleSource, zones, category, bank);
        else
            this.writeFolder (destinationFolder, multisampleSource, zones, category, bank);

        this.progress.notifyDone ();
    }


    /**
     * Write the instrument as the archive which the device imports from a USB drive:
     * <i>px/&lt;bank&gt;/&lt;category&gt;/&lt;name&gt;.zip</i>. The archive contains the
     * instrument folder, which the importer of the device extracts next to the other instruments
     * of the category.
     *
     * @param destinationFolder The folder in which to create the USB layout
     * @param multisampleSource The multi-sample
     * @param zones The zones to write
     * @param category The index of the category of the device
     * @param bank The folder name of the user bank
     * @throws IOException Could not write the archive
     */
    private void writeArchive (final File destinationFolder, final IMultisampleSource multisampleSource, final List<ISampleZone> zones, final int category, final String bank) throws IOException
    {
        final File categoryFolder = new File (new File (new File (destinationFolder, ProphetXTag.USB_FOLDER), bank), ProphetXTag.CATEGORY_FOLDERS[category]);
        safeCreateDirectory (categoryFolder);

        final File archiveFile = this.createUniqueFilename (categoryFolder, createInstrumentName (multisampleSource), "zip");
        // The importer expects the folder and the group file inside the archive to carry the name
        // of the archive, and the device lists the instrument under that name
        final String name = FileUtils.getNameWithoutType (archiveFile);
        this.notifier.log ("IDS_NOTIFY_STORING", archiveFile.getAbsolutePath ());

        try (final ZipOutputStream zipOutputStream = new ZipOutputStream (new BufferedOutputStream (new FileOutputStream (archiveFile))))
        {
            final Set<String> writtenFiles = new HashSet<> ();
            for (int zoneIndex = 0; zoneIndex < zones.size (); zoneIndex++)
            {
                if (this.isCancelled ())
                    return;

                final ISampleZone zone = zones.get (zoneIndex);
                // Zones which share a sample reference the same file
                final String fileName = this.createSampleFilename (zone, zoneIndex, WAV_ENDING);
                if (!writtenFiles.add (fileName))
                    continue;

                this.progress.notifyProgress ();
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream ();
                // The format has no play range, therefore the sample is cut to it
                this.rewriteFile (multisampleSource, zone, outputStream, DESTINATION_FORMAT, true);
                zipDataFile (zipOutputStream, name + "/" + fileName, outputStream.toByteArray ());
            }

            // Cutting the samples moved their loops, so the group file is created afterwards
            zipTextFile (zipOutputStream, name + "/" + name + ProphetXTag.GROUP_FILE_ENDING, this.createGroupFile (zones, name, category, bank));
            final Optional<String> velocityVolumes = createVelocityVolumes (zones);
            if (velocityVolumes.isPresent ())
                zipTextFile (zipOutputStream, name + "/" + ProphetXTag.VOLUME_FILE, velocityVolumes.get ());
        }
    }


    /**
     * Write the instrument as a plain folder with its group file and samples.
     *
     * @param destinationFolder The folder in which to create the instrument folder
     * @param multisampleSource The multi-sample
     * @param zones The zones to write
     * @param category The index of the category of the device
     * @param bank The folder name of the user bank
     * @throws IOException Could not write the files
     */
    private void writeFolder (final File destinationFolder, final IMultisampleSource multisampleSource, final List<ISampleZone> zones, final int category, final String bank) throws IOException
    {
        final File instrumentFolder = this.createUniqueFilename (destinationFolder, createInstrumentName (multisampleSource), "");
        if (!instrumentFolder.mkdirs ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", instrumentFolder.getAbsolutePath ());
            return;
        }

        // The device lists the instrument under the name of its group file, which is the name of
        // its folder
        final String name = instrumentFolder.getName ();
        final File groupFile = new File (instrumentFolder, name + ProphetXTag.GROUP_FILE_ENDING);
        this.notifier.log ("IDS_NOTIFY_STORING", groupFile.getAbsolutePath ());

        // The format has no play range, therefore the samples are cut to it
        this.writeSamples (instrumentFolder, multisampleSource, zones, WAV_ENDING, DESTINATION_FORMAT, true);

        // Cutting the samples moved their loops, so the group file is created afterwards
        Files.write (groupFile.toPath (), this.createGroupFile (zones, name, category, bank).getBytes (StandardCharsets.UTF_8));
        final Optional<String> velocityVolumes = createVelocityVolumes (zones);
        if (velocityVolumes.isPresent ())
            Files.write (new File (instrumentFolder, ProphetXTag.VOLUME_FILE).toPath (), velocityVolumes.get ().getBytes (StandardCharsets.UTF_8));
    }


    /**
     * Collect the zones which the device can play: those with a mono or stereo sample which play
     * on note-on.
     *
     * @param multisampleSource The multi-sample
     * @return The zones
     * @throws IOException Could not read the metadata of a sample
     */
    private List<ISampleZone> collectZones (final IMultisampleSource multisampleSource) throws IOException
    {
        final List<ISampleZone> zones = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final Optional<ISampleData> sampleData = zone.getSampleData ();
                if (sampleData.isEmpty () || zone.getTrigger () == TriggerType.RELEASE)
                    continue;
                final int channels = sampleData.get ().getAudioMetadata ().getChannels ();
                if (channels > 2)
                {
                    this.notifier.logError ("IDS_PROPHETX_UNSUPPORTED_CHANNELS", zone.getName (), Integer.toString (channels));
                    continue;
                }
                zones.add (zone);
            }
        return zones;
    }


    /**
     * Report the settings of the zones which the device cannot play.
     *
     * @param zones The zones to write
     */
    private void checkLimitations (final List<ISampleZone> zones)
    {
        int otherLoopTypes = 0;
        int reversed = 0;
        for (final ISampleZone zone: zones)
        {
            final List<ISampleLoop> loops = zone.getLoops ();
            if (!loops.isEmpty () && loops.get (0).getType () != LoopType.FORWARDS)
                otherLoopTypes++;
            if (zone.isReversed ())
                reversed++;
        }
        if (otherLoopTypes > 0)
            this.notifier.log ("IDS_PROPHETX_LOOP_TYPE", Integer.toString (otherLoopTypes));
        if (reversed > 0)
            this.notifier.log ("IDS_PROPHETX_REVERSED", Integer.toString (reversed));
    }


    /**
     * Get the category of the device in which to store the instrument: the selected one or the
     * closest one to the category of the source.
     *
     * @param multisampleSource The multi-sample
     * @return The index of the category
     */
    private int getCategory (final IMultisampleSource multisampleSource)
    {
        final int selected = this.settingsConfiguration.getCategoryIndex ();
        return selected >= 0 ? selected : ProphetXTag.fromModelCategory (multisampleSource.getMetadata ().getCategory ());
    }


    /**
     * Create the name of the instrument from the name of the multi-sample. The firmware copies the
     * name into a buffer of 64 bytes, skips names which start with a dot and passes the name
     * through a shell, so the characters which a shell expands are replaced.
     *
     * @param multisampleSource The multi-sample
     * @return The name
     */
    private static String createInstrumentName (final IMultisampleSource multisampleSource)
    {
        String name = SafeFileNames.create (multisampleSource.getName ()).strip ();
        while (name.startsWith (".") || name.endsWith ("."))
            name = (name.startsWith (".") ? name.substring (1) : name.substring (0, name.length () - 1)).strip ();
        if (name.length () > ProphetXTag.MAX_NAME_LENGTH)
            name = name.substring (0, ProphetXTag.MAX_NAME_LENGTH).strip ();
        return name.isEmpty () ? FALLBACK_NAME : name;
    }


    /**
     * Create the text of the group file: a header line with the column names and one line per
     * zone, separated by tabs.
     *
     * @param zones The zones to write
     * @param name The name of the instrument
     * @param category The index of the category of the device
     * @param bank The folder name of the user bank
     * @return The text
     * @throws IOException Could not read the metadata of a sample
     */
    private String createGroupFile (final List<ISampleZone> zones, final String name, final int category, final String bank) throws IOException
    {
        final String categoryName = ProphetXTag.CATEGORY_NAMES[category];
        // The device identifies an instrument by its UUID and falls back to its name, therefore
        // the UUID is derived from the name and the location of the instrument: a source which is
        // converted again gives an instrument with the same identity
        final String uuid = UUID.nameUUIDFromBytes ((bank + "/" + categoryName + "/" + name).getBytes (StandardCharsets.UTF_8)).toString ().replace ("-", "");

        final List<String []> rows = new ArrayList<> ();
        for (int zoneIndex = 0; zoneIndex < zones.size (); zoneIndex++)
            rows.add (this.createRow (zones.get (zoneIndex), zoneIndex, categoryName, name, uuid));
        // The device sorts the rows the same way, this only keeps the file readable
        rows.sort (Comparator.comparingInt ((final String [] row) -> Integer.parseInt (row[1])).thenComparingInt (row -> Integer.parseInt (row[3])).thenComparingInt (row -> Integer.parseInt (row[7])));

        final StringBuilder text = new StringBuilder ();
        text.append (String.join ("\t", ProphetXTag.COLUMNS)).append (LINE_ENDING);
        for (final String [] row: rows)
            text.append (String.join ("\t", row)).append (LINE_ENDING);
        return text.toString ();
    }


    /**
     * Create the values of one row of the group file from a zone.
     *
     * @param zone The zone
     * @param zoneIndex The index of the zone
     * @param categoryName The name of the category of the device
     * @param name The name of the instrument
     * @param uuid The unique identifier of the instrument
     * @return The values in the order of the columns
     * @throws IOException Could not read the metadata of the sample
     */
    private String [] createRow (final ISampleZone zone, final int zoneIndex, final String categoryName, final String name, final String uuid) throws IOException
    {
        final IAudioMetadata audioMetadata = zone.getSampleData ().get ().getAudioMetadata ();

        final int keyLow = Math.clamp (zone.getKeyLow (), 0, 127);
        final int keyHigh = Math.clamp (limitToDefault (zone.getKeyHigh (), 127), keyLow, 127);
        final int velocityLow = Math.clamp (zone.getVelocityLow (), 0, 127);
        final int velocityHigh = Math.clamp (limitToDefault (zone.getVelocityHigh (), 127), velocityLow, 127);

        // The device plays a key at the ratio of the frequency of the key and the frequency at
        // which the file sounds, so the tuning is folded into that frequency
        final int root = Math.clamp (limitToDefault (zone.getKeyRoot (), DEFAULT_ROOT_KEY), 0, 127);
        final double pitchInHertz = ProphetXTag.DEFAULT_PITCH * Math.pow (2, (root - zone.getTuning () - 69) / 12.0);

        // The device plays both frames of a loop and rejects a loop which reaches beyond the file.
        // The zone is cut to its play range when its sample is written, which moves the loop to
        // the start of the cut part, so the length of the cut part is the limit
        int loopStart = ProphetXTag.NOT_SET;
        int loopEnd = ProphetXTag.NOT_SET;
        final List<ISampleLoop> loops = zone.getLoops ();
        if (!loops.isEmpty ())
        {
            final ISampleLoop loop = loops.get (0);
            final int lastFrame = Math.max (getPlayedFrames (zone, audioMetadata) - 1, 0);
            final int start = Math.clamp (loop.getStart (), 0, lastFrame);
            final int end = loop.getEnd () < 0 ? lastFrame : Math.min (loop.getEnd (), lastFrame);
            if (start < end)
            {
                loopStart = start;
                loopEnd = end;
            }
        }

        // The samples of a round robin set share their key and velocity range and carry a number;
        // the device picks one of them at random, so a random selection needs no other number
        final int roundRobin = zone.getPlayLogic () == PlayLogic.ALWAYS ? ProphetXTag.NOT_SET : Math.max (zone.getSequencePosition (), 1);

        return new String []
        {
            this.createSampleFilename (zone, zoneIndex, WAV_ENDING),
            Integer.toString (keyLow),
            Integer.toString (keyHigh),
            Integer.toString (velocityLow),
            Integer.toString (velocityHigh),
            Integer.toString (loopStart),
            Integer.toString (loopEnd),
            Integer.toString (roundRobin),
            formatDouble (pitchInHertz, 4),
            // The device either tracks the keyboard fully or not at all
            zone.getKeyTracking () < 0.5 ? ProphetXTag.YES : ProphetXTag.NO,
            audioMetadata.getChannels () > 1 ? ProphetXTag.STEREO : ProphetXTag.MONO,
            categoryName,
            name,
            ProphetXTag.COLLAPSE_BOTH,
            uuid
        };
    }


    /**
     * Get the number of frames which the written sample has: the play range of the zone if one is
     * set, otherwise the whole sample.
     *
     * @param zone The zone
     * @param audioMetadata The metadata of the sample of the zone
     * @return The number of frames
     */
    private static int getPlayedFrames (final ISampleZone zone, final IAudioMetadata audioMetadata)
    {
        final int frames = audioMetadata.getNumberOfSamples ();
        final int start = Math.clamp (limitToDefault (zone.getStart (), 0), 0, frames);
        final int stop = Math.clamp (limitToDefault (zone.getStop (), frames), start, frames);
        return stop - start;
    }


    /**
     * Create the optional file with the velocity volumes, one gain factor per velocity, from the
     * amplitude velocity modulators of the zones. The device has a built-in curve which the file
     * replaces, therefore it is only written if all zones share a response which differs from the
     * default of the model.
     *
     * @param zones The zones to write
     * @return The text of the file or empty if the built-in curve of the device is kept
     */
    private static Optional<String> createVelocityVolumes (final List<ISampleZone> zones)
    {
        Double depth = null;
        Double curve = null;
        for (final ISampleZone zone: zones)
        {
            final IModulator modulator = zone.getAmplitudeVelocityModulator ();
            if (depth == null)
            {
                depth = Double.valueOf (modulator.getDepth ());
                curve = Double.valueOf (modulator.getCurve ());
            }
            else if (depth.doubleValue () != modulator.getDepth () || curve.doubleValue () != modulator.getCurve ())
                return Optional.empty ();
        }
        if (depth == null || depth.doubleValue () == 1 && curve.doubleValue () == 0)
            return Optional.empty ();

        // The response of the model is 1 - depth + depth * (velocity / 127) ^ (3 ^ curve)
        final double power = Math.pow (3, curve.doubleValue ());
        final StringBuilder text = new StringBuilder ();
        for (int velocity = 0; velocity < NUM_VELOCITIES; velocity++)
        {
            final double volume = 1 - depth.doubleValue () + depth.doubleValue () * Math.pow (velocity / (double) (NUM_VELOCITIES - 1), power);
            // The firmware reads neither a sign nor an exponent
            text.append (formatDouble (Math.max (volume, 0), 4)).append (LINE_ENDING);
        }
        return Optional.of (text.toString ());
    }
}
