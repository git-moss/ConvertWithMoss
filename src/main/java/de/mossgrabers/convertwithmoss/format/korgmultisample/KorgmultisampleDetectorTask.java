// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.korgmultisample;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.exception.FormatException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;


/**
 * Detects recursively Korgmultisample files in folders. Files must end with
 * <i>.korgmultisample</i>.
 *
 * @author Jürgen Moßgraber
 */
public class KorgmultisampleDetectorTask extends AbstractDetectorTask
{
    private static final String ENDING_KORGMULTISAMPLE = ".korgmultisample";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     */
    protected KorgmultisampleDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder)
    {
        super (notifier, consumer, sourceFolder, null, ENDING_KORGMULTISAMPLE);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final InputStream in = new BufferedInputStream (new FileInputStream (file)))
        {
            return this.parseFile (in, file);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }
        catch (final FormatException ex)
        {
            this.notifier.logError ("IDS_WS_EXPECTED_TAG", ex.getMessage ());
        }
        return Collections.emptyList ();
    }


    /**
     * Load and parse the korgmultisample file.
     *
     * @param in The input stream to read from
     * @param file The source file
     * @return The parsed multisample source
     * @throws FormatException Error in the format of the file
     * @throws IOException Could not read from the file
     */
    private List<IMultisampleSource> parseFile (final InputStream in, final File file) throws FormatException, IOException
    {
        readHeaders (in);

        // The version number, not always present
        if (in.read () == 0x1A)
        {
            readAscii (in);
            in.read ();
        }

        // Time of storage - the milliseconds from 1.1.1970: fromBytesLSB (time) * 1000L
        in.readNBytes (8);

        // Size of the content, not needed
        in.readNBytes (4);

        checkAscii (in);

        final String name = readAscii (in);

        final String [] parts = AudioFileUtils.createPathParts (file.getParentFile (), this.sourceFolder, name);
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (file, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, file));
        final List<IGroup> groups = new ArrayList<> ();
        // There is only one group (no velocity zones)
        final DefaultGroup group = new DefaultGroup ("Layer");
        groups.add (group);

        final IMetadata metadata = multisampleSource.getMetadata ();

        int id;
        while ((id = in.read ()) != -1)
        {
            switch (id)
            {
                case KorgmultisampleTag.ID_AUTHOR:
                    metadata.setCreator (readAscii (in));
                    break;

                case KorgmultisampleTag.ID_CATEGORY:
                    metadata.setCategory (readAscii (in));
                    break;

                case KorgmultisampleTag.ID_COMMENT:
                    metadata.setDescription (readAscii (in));
                    break;

                case KorgmultisampleTag.ID_SAMPLE:
                    group.addSampleMetadata (this.readSample (in));
                    break;

                case KorgmultisampleTag.ID_UUID:
                    final int size = in.readNBytes (1)[0];
                    in.readNBytes (size);
                    break;

                default:
                    throw new FormatException (Integer.toHexString (id));
            }
        }

        final String n = multisampleSource.getName ();
        if (n == null || n.isBlank () || "Empty".equals (n))
            multisampleSource.setName (FileUtils.getNameWithoutType (file));

        multisampleSource.setGroups (groups);
        return Collections.singletonList (multisampleSource);
    }


    /**
     * Reads and checks the header information preceding the actual data.
     *
     * @param in The input stream to read from
     * @throws IOException Could not read
     * @throws FormatException Found unexpected format of the file
     */
    private static void readHeaders (final InputStream in) throws IOException, FormatException
    {
        final byte [] headerTag = in.readNBytes (4);
        checkTag (KorgmultisampleTag.TAG_KORG, headerTag);

        // Ignore rest of the header
        in.readNBytes (8);

        checkAscii (in);
        final String fileInfoTag = readAscii (in);
        checkTag (KorgmultisampleTag.TAG_FILE_INFO, fileInfoTag);
        // Ignore file info data
        in.readNBytes (2);

        checkAscii (in);
        final String multiSampleTag = readAscii (in);
        checkTag (KorgmultisampleTag.TAG_MULTISAMPLE, multiSampleTag);
        // Ignore multisample header
        in.readNBytes (6);

        checkAscii (in);
        final String singleItemTag = readAscii (in);
        checkTag (KorgmultisampleTag.TAG_SINGLE_ITEM, singleItemTag);
        // Ignore single item header
        in.readNBytes (1);

        final String sampleBuilderTag = readAscii (in);
        checkTag (KorgmultisampleTag.TAG_SAMPLE_BUILDER, sampleBuilderTag);
    }


    /**
     * Read the data related to the sample configuration.
     *
     * @param in The input stream to read from
     * @return The filled sample metadata
     * @throws IOException Could not read from the file
     * @throws FormatException Found unexpected format of the file
     */
    private ISampleZone readSample (final InputStream in) throws IOException, FormatException
    {
        // Size of the sample block
        final int [] size = StreamUtils.read7bitNumberLE (in);
        final int blockLength = size[0];

        // 0x0A, Offset to key zone data
        in.readNBytes (2);

        checkAscii (in);
        final String sampleFileName = readAscii (in);
        final File sampleFile = this.createCanonicalFile (this.sourceFolder, sampleFileName);
        final ISampleData sampleData = new WavFileSampleData (sampleFile);
        final ISampleZone sample = new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), sampleData);

        int rest = blockLength - 3 - sampleFileName.length () - 1;
        rest = parseSampleParameters (sample, in, rest);
        parseKeyZoneParameters (sample, in, rest);

        return sample;
    }


    /**
     * Parses the data related to the samples' playback configuration.
     *
     * @param sample The sample metadata in which to stored the data
     * @param in The input stream to read from
     * @param rest The number of bytes available to read
     * @return The number of bytes not read
     * @throws IOException Could not read from the file
     * @throws FormatException FOund unexpected format of the file
     */
    private static int parseSampleParameters (final ISampleZone sample, final InputStream in, final int rest) throws IOException, FormatException
    {
        int lastID = 0;
        int r = rest;
        int loopStart = 0;
        boolean oneShot = false;

        while (r > 0)
        {
            in.mark (1);
            final byte [] paramID = in.readNBytes (1);

            final int currentID = paramID[0];
            if (currentID < lastID)
            {
                in.reset ();
                break;
            }

            r--;

            switch (currentID)
            {
                case KorgmultisampleTag.ID_START:
                    final int [] startNumber = StreamUtils.read7bitNumberLE (in);
                    r -= startNumber[1];
                    sample.setStart (startNumber[0]);
                    break;
                case KorgmultisampleTag.ID_LOOP_START:
                    final int [] loopStartNumber = StreamUtils.read7bitNumberLE (in);
                    r -= loopStartNumber[1];
                    loopStart = loopStartNumber[0];
                    break;
                case KorgmultisampleTag.ID_END:
                    final int [] endNumber = StreamUtils.read7bitNumberLE (in);
                    r -= endNumber[1];
                    sample.setStop (endNumber[0]);
                    break;
                case KorgmultisampleTag.ID_LOOP_TUNE:
                    r -= 4;
                    // Not used
                    in.readNBytes (4);
                    break;
                case KorgmultisampleTag.ID_ONE_SHOT:
                    r -= 1;
                    if (in.readNBytes (1)[0] > 0)
                        oneShot = true;
                    break;
                case KorgmultisampleTag.ID_BOOST_12DB:
                    r -= 1;
                    if (in.readNBytes (1)[0] > 0)
                        sample.setGain (12);
                    break;
                default:
                    throw new FormatException (Integer.toHexString (currentID));
            }

            lastID = currentID;
        }

        if (!oneShot)
        {
            final DefaultSampleLoop loop = new DefaultSampleLoop ();
            loop.setStart (loopStart);
            loop.setEnd (sample.getStop ());
            sample.addLoop (loop);
        }

        return r;
    }


    /**
     * Parses the data related to the samples' key zone configuration.
     *
     * @param sample The sample metadata in which to stored the data
     * @param in The input stream to read from
     * @param rest The number of bytes left to read
     * @throws IOException Could not read from the file
     * @throws FormatException FOund unexpected format of the file
     */
    private static void parseKeyZoneParameters (final ISampleZone sample, final InputStream in, final int rest) throws IOException, FormatException
    {
        int lastID = 0;
        int r = rest;

        while (r > 0)
        {
            in.mark (1);
            final byte [] paramID = in.readNBytes (1);

            final int currentID = paramID[0];
            if (currentID < lastID)
            {
                in.reset ();
                return;
            }

            r--;

            switch (currentID)
            {
                case KorgmultisampleTag.ID_KEY_BOTTOM:
                    r -= 1;
                    sample.setKeyLow (in.readNBytes (1)[0]);
                    break;
                case KorgmultisampleTag.ID_KEY_TOP:
                    r -= 1;
                    sample.setKeyHigh (in.readNBytes (1)[0]);
                    break;
                case KorgmultisampleTag.ID_KEY_ORIGINAL:
                    r -= 1;
                    sample.setKeyRoot (in.readNBytes (1)[0]);
                    break;
                case KorgmultisampleTag.ID_FIXED_PITCH:
                    r -= 1;
                    if (in.readNBytes (1)[0] > 0)
                        sample.setKeyTracking (0);
                    break;
                case KorgmultisampleTag.ID_TUNE:
                    r -= 4;
                    // Read value is in the range of [-999..999]
                    sample.setTune (StreamUtils.readFloatLE (in.readNBytes (4)) / 1000.0);
                    break;
                case KorgmultisampleTag.ID_LEVEL_LEFT:
                    r -= 4;
                    // Note: The left/right link button in the editor is only a UI thing!
                    final float levelLeftValue = StreamUtils.readFloatLE (in.readNBytes (4));
                    // This is not fully correct but since it is not documented what the percentages
                    // (-100..100%) mean in dB this is better than nothing...
                    sample.setGain (levelLeftValue / 100.0 * 12.0);
                    break;
                case KorgmultisampleTag.ID_LEVEL_RIGHT:
                    r -= 4;
                    // This is -100..100%. We only handle one value (left)
                    in.readNBytes (4);
                    break;
                case KorgmultisampleTag.ID_COLOR:
                    r -= 4;
                    // B, G, R, FF, ?? = 7 bit
                    in.readNBytes (5);
                    break;

                default:
                    throw new FormatException (Integer.toHexString (currentID));
            }

            lastID = currentID;
        }
    }


    /**
     * checks if the next byte in the stream indicates an ASCII string (0x0A).
     *
     * @param in The input stream to read from
     * @throws IOException Could not read
     */
    private static void checkAscii (final InputStream in) throws IOException
    {
        final int blockType = in.read ();
        if (blockType != 0x0A)
            throw new IOException ("Not an ASCII block.");
    }


    /**
     * Reads an ASCII string. The first read byte indicates the length of the string.
     *
     * @param in The input stream to read from
     * @return The read ASCII string
     * @throws IOException Could not read
     */
    private static String readAscii (final InputStream in) throws IOException
    {
        final int blocklength = in.read ();
        final byte [] blockData = in.readNBytes (blocklength);
        return new String (blockData);
    }


    /**
     * Interprets the byte array as characters and compares them with the given tag.
     *
     * @param tag The tag
     * @param bytes The byte array
     * @throws FormatException One or more characters do not match
     */
    private static void checkTag (final String tag, final byte [] bytes) throws FormatException
    {
        for (int i = 0; i < bytes.length; i++)
        {
            if ((char) bytes[i] != tag.charAt (i))
                throw new FormatException (tag);
        }
    }


    /**
     * Compares the tag with the string.
     *
     * @param tag The tag
     * @param str The text for comparison
     * @throws FormatException One or more characters do not match
     */
    private static void checkTag (final String tag, final String str) throws FormatException
    {
        if (!str.equals (tag))
            throw new FormatException (tag);
    }
}
