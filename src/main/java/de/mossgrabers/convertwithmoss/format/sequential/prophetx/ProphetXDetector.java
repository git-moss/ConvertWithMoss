// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sequential.prophetx;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects Sequential Prophet X / XL sample instruments. An instrument is a folder which contains
 * its samples as WAV files and a group file (ending <i>.grp</i>) with the key, velocity and loop
 * mapping. The archives (ending <i>.zip</i>) which the device imports from a USB drive hold such a
 * folder and are read as well. The format was reverse-engineered from the OS 2.2.2 firmware, see
 * <i>documentation/design/PROPHET_X_GRP_FORMAT.md</i>.
 *
 * @author Jürgen Moßgraber
 */
public class ProphetXDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String  MACOS_RESOURCE_FOLDER = "__MACOSX/";
    /** The firmware reads digits and dots only. */
    private static final Pattern VOLUME_NUMBER         = Pattern.compile ("\\d+(\\.\\d*)?|\\.\\d+");
    private static final int     NUM_VELOCITIES        = 128;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public ProphetXDetector (final INotifier notifier)
    {
        super ("Sequential Prophet X", "ProphetX", notifier, new MetadataSettingsUI ("ProphetX"), ProphetXTag.GROUP_FILE_ENDING, ProphetXTag.ARCHIVE_ENDING);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            if (file.getName ().toLowerCase (Locale.US).endsWith (ProphetXTag.ARCHIVE_ENDING))
                return this.readArchive (file);
            return this.readGroupFile (file);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Read an instrument from its group file. The samples are located relative to the folder of the
     * group file.
     *
     * @param file The group file
     * @return The multi-sample source
     * @throws IOException Could not read the group file
     */
    private List<IMultisampleSource> readGroupFile (final File file) throws IOException
    {
        final File instrumentFolder = file.getAbsoluteFile ().getParentFile ();
        // On the device the instrument folder lies in one of the fixed category folders
        final File categoryFolder = instrumentFolder.getParentFile ();
        final int folderCategory = categoryFolder == null ? -1 : ProphetXTag.getCategoryIndex (categoryFolder.getName ());

        final IInstrumentFolder folder = new IInstrumentFolder ()
        {
            /** {@inheritDoc} */
            @Override
            public ISampleData loadSample (final String filePath) throws IOException
            {
                return ProphetXDetector.this.loadSampleFromGroup (instrumentFolder, filePath);
            }


            /** {@inheritDoc} */
            @Override
            public String loadVelocityVolumes () throws IOException
            {
                return ProphetXDetector.this.loadVelocityVolumesFromGroups (instrumentFolder);
            }
        };

        return this.parseGroupFile (file, FileUtils.getNameWithoutType (file), this.loadTextFile (file), folderCategory, folder);
    }


    private ISampleData loadSampleFromGroup (final File instrumentFolder, final String filePath) throws IOException
    {
        final File sampleFile = this.createCanonicalFile (instrumentFolder, filePath);
        if (!sampleFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", sampleFile.getAbsolutePath ());
            return null;
        }
        return createSampleData (sampleFile, this.notifier);
    }


    private String loadVelocityVolumesFromGroups (final File instrumentFolder) throws IOException
    {
        final File volumeFile = findFileIgnoreCase (instrumentFolder, ProphetXTag.VOLUME_FILE);
        return volumeFile.exists () ? this.loadTextFile (volumeFile) : null;
    }


    /**
     * Read an instrument from an import archive, which contains the instrument folder with its
     * group file and samples.
     *
     * @param file The archive
     * @return The multi-sample source
     * @throws IOException Could not read the archive
     */
    private List<IMultisampleSource> readArchive (final File file) throws IOException
    {
        try (final ZipFile zipFile = new ZipFile (file))
        {
            final ZipEntry groupEntry = findGroupEntry (zipFile);
            if (groupEntry == null)
            {
                this.notifier.log ("IDS_PROPHETX_NOT_AN_ARCHIVE", file.getName ());
                return Collections.emptyList ();
            }

            final String groupEntryName = groupEntry.getName ();
            final int slashIndex = groupEntryName.lastIndexOf ('/');
            final String entryFolder = slashIndex < 0 ? "" : groupEntryName.substring (0, slashIndex + 1);
            final String name = FileUtils.getNameWithoutType (new File (groupEntryName));

            // On the USB drive the archive lies in one of the fixed category folders
            final File categoryFolder = file.getAbsoluteFile ().getParentFile ();
            final int folderCategory = categoryFolder == null ? -1 : ProphetXTag.getCategoryIndex (categoryFolder.getName ());

            final IInstrumentFolder folder = new IInstrumentFolder ()
            {
                /** {@inheritDoc} */
                @Override
                public ISampleData loadSample (final String filePath) throws IOException
                {
                    return ProphetXDetector.this.loadSampleFromArchive (file, zipFile, entryFolder, filePath);
                }


                /** {@inheritDoc} */
                @Override
                public String loadVelocityVolumes () throws IOException
                {
                    final ZipEntry volumeEntry = findEntry (zipFile, entryFolder + ProphetXTag.VOLUME_FILE);
                    return volumeEntry == null ? null : readText (zipFile, volumeEntry);
                }
            };

            return this.parseGroupFile (file, name, readText (zipFile, groupEntry), folderCategory, folder);
        }
    }


    private ISampleData loadSampleFromArchive (final File file, final ZipFile zipFile, final String entryFolder, final String filePath) throws IOException
    {
        final String entryName = entryFolder + filePath.replace ('\\', '/');
        final ZipEntry sampleEntry = findEntry (zipFile, entryName);
        if (sampleEntry == null)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", file.getName () + ": " + entryName);
            return null;
        }
        return this.createSampleData (file, new File (sampleEntry.getName ()));
    }


    /**
     * Parse the content of a group file into a multi-sample source.
     *
     * @param sourceFile The group file or the archive
     * @param fileName The name of the group file, which the device shows for the instrument
     * @param content The text of the group file
     * @param folderCategory The index of the category folder in which the instrument lies or -1 if
     *            it does not lie in one
     * @param folder Reads the files of the instrument folder
     * @return The multi-sample source
     * @throws IOException Could not read a file of the instrument
     */
    private List<IMultisampleSource> parseGroupFile (final File sourceFile, final String fileName, final String content, final int folderCategory, final IInstrumentFolder folder) throws IOException
    {
        final List<Map<String, String>> rows = parseTable (content);
        if (rows == null)
        {
            this.notifier.logError ("IDS_PROPHETX_NO_HEADER", sourceFile.getAbsolutePath ());
            return Collections.emptyList ();
        }

        // The samples of a round robin set carry the same number, so each number becomes a group
        // which is played in turn; the samples without a number form the first group
        final Map<Integer, IGroup> groups = new TreeMap<> ();
        int columnCategory = -1;
        int emptySamples = 0;
        String instrumentName = null;
        for (final Map<String, String> row: rows)
        {
            if (this.waitForDelivery ())
                return Collections.emptyList ();

            final String filePath = row.get (ProphetXTag.COLUMN_FILE_PATH);
            if (filePath == null || filePath.isBlank ())
                continue;

            // The firmware keeps the category and the name of the first row which states them
            if (columnCategory < 0)
                columnCategory = ProphetXTag.getCategoryIndex (row.get (ProphetXTag.COLUMN_CATEGORY));
            if (instrumentName == null)
                instrumentName = row.get (ProphetXTag.COLUMN_INSTRUMENT);

            final ISampleZone zone;
            try
            {
                final ISampleData sampleData = folder.loadSample (filePath);
                if (sampleData == null)
                    continue;
                // PXToolkit maps the keys which no zone covers to a sample without a single frame,
                // so that the device plays nothing on them
                if (sampleData.getAudioMetadata ().getNumberOfSamples () == 0)
                {
                    emptySamples++;
                    continue;
                }
                zone = createZone (filePath, sampleData, row);
            }
            catch (final IOException ex)
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
                continue;
            }

            // The firmware reads 0 as the first number of a set
            int roundRobin = getInt (row, ProphetXTag.COLUMN_ROUND_ROBIN, ProphetXTag.NOT_SET);
            if (roundRobin == 0)
                roundRobin = 1;
            if (roundRobin > 0)
            {
                zone.setPlayLogic (PlayLogic.ROUND_ROBIN);
                zone.setSequencePosition (roundRobin);
            }
            final Integer groupNumber = Integer.valueOf (Math.max (roundRobin, 0));
            groups.computeIfAbsent (groupNumber, number -> new DefaultGroup (number.intValue () == 0 ? "Group 1" : "Round Robin " + number)).addSampleZone (zone);
        }

        if (emptySamples > 0)
            this.notifier.log ("IDS_PROPHETX_EMPTY_SAMPLES", Integer.toString (emptySamples));
        if (groups.isEmpty ())
        {
            this.notifier.logError ("IDS_PROPHETX_NO_SAMPLES", sourceFile.getAbsolutePath ());
            return Collections.emptyList ();
        }

        final List<IGroup> groupList = new ArrayList<> (groups.values ());
        this.applyVelocityVolumes (sourceFile, groupList, folder);

        final String name = instrumentName == null || instrumentName.isBlank () ? fileName : instrumentName;
        final IMultisampleSource multisampleSource = this.createMultisampleSource (sourceFile, name, groupList);
        // The folder decides on the device in which category the instrument shows up, the column
        // is only stored
        final int category = folderCategory >= 0 ? folderCategory : columnCategory;
        if (category >= 0)
            multisampleSource.getMetadata ().setCategory (ProphetXTag.toModelCategory (category));
        return Collections.singletonList (multisampleSource);
    }


    /**
     * Create a zone from one row of the group file.
     *
     * @param filePath The file path of the sample
     * @param sampleData The sample data
     * @param row The values of the row by column name
     * @return The zone
     * @throws IOException Could not read the metadata of the sample
     */
    private static ISampleZone createZone (final String filePath, final ISampleData sampleData, final Map<String, String> row) throws IOException
    {
        final ISampleZone zone = new DefaultSampleZone (FileUtils.getNameWithoutType (new File (filePath)), sampleData);

        final int keyLow = Math.clamp (getInt (row, ProphetXTag.COLUMN_LOW_NOTE, 0), 0, 127);
        zone.setKeyLow (keyLow);
        zone.setKeyHigh (Math.clamp (getInt (row, ProphetXTag.COLUMN_HIGH_NOTE, 127), keyLow, 127));
        final int velocityLow = Math.clamp (getInt (row, ProphetXTag.COLUMN_LOW_VELOCITY, 0), 0, 127);
        zone.setVelocityLow (velocityLow);
        zone.setVelocityHigh (Math.clamp (getInt (row, ProphetXTag.COLUMN_HIGH_VELOCITY, 127), velocityLow, 127));

        // The device plays a key at the ratio of the frequency of the key and the frequency at
        // which the file sounds, so the root key is the closest key and the rest is the tuning
        final double pitchInHertz = getDouble (row, ProphetXTag.COLUMN_PITCH, ProphetXTag.DEFAULT_PITCH);
        if (pitchInHertz > 0)
        {
            final double note = 69 + 12 * Math.log (pitchInHertz / ProphetXTag.DEFAULT_PITCH) / Math.log (2);
            final int root = Math.clamp (Math.round (note), 0, 127);
            zone.setKeyRoot (root);
            zone.setTuning (root - note);
        }

        // A sample with a mono pitch plays untransposed on every key
        if (ProphetXTag.YES.equalsIgnoreCase (row.get (ProphetXTag.COLUMN_MONO_PITCH)))
            zone.setKeyTracking (0);

        final int loopStart = getInt (row, ProphetXTag.COLUMN_LOOP_START, ProphetXTag.NOT_SET);
        final int loopEnd = getInt (row, ProphetXTag.COLUMN_LOOP_END, ProphetXTag.NOT_SET);
        if (loopStart >= 0 || loopEnd >= 0)
        {
            // The device loops from the start of the file if only the end is given and to its end
            // if only the start is given; both frames are played
            final int lastFrame = sampleData.getAudioMetadata ().getNumberOfSamples () - 1;
            final int start = Math.max (loopStart, 0);
            final int end = loopEnd < 0 ? lastFrame : Math.min (loopEnd, lastFrame);
            if (start < end)
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                loop.setType (LoopType.FORWARDS);
                loop.setStart (start);
                loop.setEnd (end);
                zone.addLoop (loop);
            }
        }

        return zone;
    }


    /**
     * Apply the optional velocity volumes of the instrument, one gain factor per velocity, to the
     * amplitude velocity modulators of all zones. The device uses its built-in curve instead if the
     * instrument has no such file, which is left to the defaults of the model.
     *
     * @param sourceFile The group file or the archive, for the error message
     * @param groups The groups with the zones
     * @param folder Reads the files of the instrument folder
     */
    private void applyVelocityVolumes (final File sourceFile, final List<IGroup> groups, final IInstrumentFolder folder)
    {
        final String content;
        try
        {
            content = folder.loadVelocityVolumes ();
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_PROPHETX_VOLUME_FILE", sourceFile.getAbsolutePath (), ex.getMessage ());
            return;
        }
        if (content == null)
            return;

        // The firmware reads up to 128 numbers, the rest stays at 1
        final double [] volumes = new double [NUM_VELOCITIES];
        Arrays.fill (volumes, 1);
        final Matcher matcher = VOLUME_NUMBER.matcher (content);
        for (int i = 0; i < NUM_VELOCITIES && matcher.find (); i++)
            volumes[i] = Double.parseDouble (matcher.group ());

        // The response of the model is 1 - depth + depth * (velocity / 127) ^ (3 ^ curve),
        // therefore the depth is the swing from the lowest velocity to the highest one and the
        // curve is the power law which fits the shape in between
        final double maximum = volumes[NUM_VELOCITIES - 1];
        if (maximum <= 0)
            return;
        final double depth = Math.clamp (1 - volumes[0] / maximum, 0, 1);
        double curve = 0;
        if (depth > 0)
        {
            double sumXY = 0;
            double sumXX = 0;
            for (int i = 1; i < NUM_VELOCITIES - 1; i++)
            {
                final double response = (volumes[i] / maximum - (1 - depth)) / depth;
                if (response <= 0 || response >= 1)
                    continue;
                final double x = Math.log (i / (double) (NUM_VELOCITIES - 1));
                sumXY += x * Math.log (response);
                sumXX += x * x;
            }
            if (sumXX > 0)
                curve = Math.clamp (Math.log (sumXY / sumXX) / Math.log (3), -1, 1);
        }

        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final IModulator modulator = zone.getAmplitudeVelocityModulator ();
                modulator.setDepth (depth);
                modulator.setCurve (curve);
            }
    }


    /**
     * Parse the table of a group file. The first line names the columns in any order, each further
     * line is one sample. Like the firmware, only tabs separate the values and empty values are
     * dropped, so the values of a line are matched to the columns by their position among the
     * present values.
     *
     * @param content The text of the group file
     * @return The rows with their values by column name or null if there is no header line
     */
    private static List<Map<String, String>> parseTable (final String content)
    {
        final List<Map<String, String>> rows = new ArrayList<> ();
        String [] columns = null;
        for (final String line: content.split ("\r\n|\r|\n"))
        {
            final String [] values = splitValues (line);
            if (values.length == 0)
                continue;
            if (columns == null)
            {
                columns = values;
                continue;
            }
            final Map<String, String> row = new HashMap<> ();
            for (int i = 0; i < values.length && i < columns.length; i++)
                row.putIfAbsent (columns[i], values[i]);
            rows.add (row);
        }
        return columns == null ? null : rows;
    }


    /**
     * Split a line into its values.
     *
     * @param line The line
     * @return The values, without empty ones
     */
    private static String [] splitValues (final String line)
    {
        final List<String> values = new ArrayList<> ();
        for (final String value: line.split ("\t"))
        {
            final String stripped = value.strip ();
            if (!stripped.isEmpty ())
                values.add (stripped);
        }
        return values.toArray (new String [values.size ()]);
    }


    /**
     * Find the group file in an import archive. The device expects it as
     * <i>&lt;name&gt;/&lt;name&gt;.grp</i>; if there is no such entry the first group file wins.
     *
     * @param zipFile The archive
     * @return The entry of the group file or null if there is none
     */
    private static ZipEntry findGroupEntry (final ZipFile zipFile)
    {
        ZipEntry firstGroupEntry = null;
        final Enumeration<? extends ZipEntry> entries = zipFile.entries ();
        while (entries.hasMoreElements ())
        {
            final ZipEntry entry = entries.nextElement ();
            final String entryName = entry.getName ();
            if (entry.isDirectory () || entryName.startsWith (MACOS_RESOURCE_FOLDER) || !entryName.toLowerCase (Locale.US).endsWith (ProphetXTag.GROUP_FILE_ENDING))
                continue;
            final File entryFile = new File (entryName);
            final File parent = entryFile.getParentFile ();
            if (parent != null && parent.getName ().equals (FileUtils.getNameWithoutType (entryFile)))
                return entry;
            if (firstGroupEntry == null)
                firstGroupEntry = entry;
        }
        return firstGroupEntry;
    }


    /**
     * Find an entry in an archive. The device matches the names exactly, the search here also
     * accepts a different case.
     *
     * @param zipFile The archive
     * @param entryName The name of the entry
     * @return The entry or null if it does not exist
     */
    private static ZipEntry findEntry (final ZipFile zipFile, final String entryName)
    {
        final ZipEntry entry = zipFile.getEntry (entryName);
        if (entry != null)
            return entry;

        final Enumeration<? extends ZipEntry> entries = zipFile.entries ();
        while (entries.hasMoreElements ())
        {
            final ZipEntry candidate = entries.nextElement ();
            if (candidate.getName ().equalsIgnoreCase (entryName))
                return candidate;
        }
        return null;
    }


    /**
     * Read a text entry of an archive.
     *
     * @param zipFile The archive
     * @param entry The entry
     * @return The text
     * @throws IOException Could not read the entry
     */
    private static String readText (final ZipFile zipFile, final ZipEntry entry) throws IOException
    {
        try (final InputStream inputStream = zipFile.getInputStream (entry))
        {
            return new String (inputStream.readAllBytes (), StandardCharsets.UTF_8);
        }
    }


    /**
     * Get an integer value of a row. Like the firmware, which uses atoi, the fraction of a decimal
     * number is dropped.
     *
     * @param row The row
     * @param column The name of the column
     * @param defaultValue The value to return if the column is absent or not a number
     * @return The value
     */
    private static int getInt (final Map<String, String> row, final String column, final int defaultValue)
    {
        final double value = getDouble (row, column, defaultValue);
        return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE ? defaultValue : (int) value;
    }


    /**
     * Get a decimal value of a row.
     *
     * @param row The row
     * @param column The name of the column
     * @param defaultValue The value to return if the column is absent or not a number
     * @return The value
     */
    private static double getDouble (final Map<String, String> row, final String column, final double defaultValue)
    {
        final String text = row.get (column);
        if (text == null)
            return defaultValue;
        try
        {
            return Double.parseDouble (text);
        }
        catch (final NumberFormatException _)
        {
            return defaultValue;
        }
    }
}
