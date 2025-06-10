// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.korgmultisample;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

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
import de.mossgrabers.tools.ui.Functions;


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
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     */
    protected KorgmultisampleDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder)
    {
        super (notifier, consumer, sourceFolder, null, ENDING_KORGMULTISAMPLE);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
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


    private List<IMultisampleSource> parseFile (final InputStream in, final File file) throws FormatException, IOException
    {
        final byte [] headerTag = in.readNBytes (4);
        StreamUtils.checkTag (KorgmultisampleConstants.TAG_KORG, headerTag);

        /////////////////////////////////////////////////
        // Read all 3 chunks and check first chunk

        final List<byte []> chunks = parseChunks (in);
        if (chunks.size () != 3)
            throw new IOException (Functions.getMessage ("IDS_WS_WRONG_NUMBER_OF_CHUNKS", Integer.toString (chunks.size ())));

        if (Arrays.compare (KorgmultisampleConstants.HEADER_CHUNK, chunks.get (0)) != 0)
            throw new IOException (Functions.getMessage ("IDS_WS_NO_MULTISAMPLE_HEADER"));

        /////////////////////////////////////////////////
        // Read the 2nd chunk

        final InputStream secondIn = new ByteArrayInputStream (chunks.get (1));
        checkAscii (secondIn);
        final String singleItemTag = StreamUtils.readWith1ByteLengthAscii (secondIn);
        StreamUtils.checkTag (KorgmultisampleConstants.TAG_SINGLE_ITEM, singleItemTag);
        // Ignore single item header
        secondIn.skipNBytes (1);
        final String sampleBuilderTag = StreamUtils.readWith1ByteLengthAscii (secondIn);
        StreamUtils.checkTag (KorgmultisampleConstants.TAG_SAMPLE_BUILDER, sampleBuilderTag);

        // The version number, not always present and not needed
        Date creationDateTime = new Date ();
        String application = KorgmultisampleConstants.TAG_SAMPLE_BUILDER;
        String applicationVersion = "";
        while (secondIn.available () > 0)
        {
            final int chunk2ID = secondIn.read ();
            switch (chunk2ID)
            {
                case KorgmultisampleConstants.ID_VERSION:
                    StreamUtils.readWith1ByteLengthAscii (secondIn);
                    break;
                case KorgmultisampleConstants.ID_TIME:
                    // Time of storage - the seconds from 1.1.1970
                    creationDateTime = new Date (StreamUtils.fromBytesLE (secondIn.readNBytes (8)) * 1000L);
                    break;
                case KorgmultisampleConstants.ID_APPLICATION:
                    application = StreamUtils.readWith1ByteLengthAscii (secondIn);
                    break;
                case KorgmultisampleConstants.ID_APPLICATION_VERSION:
                    applicationVersion = StreamUtils.readWith1ByteLengthAscii (secondIn);
                    break;
                default:
                    throw new IOException (Functions.getMessage ("IDS_WS_UNKNOWN_CHUNK2_ID", Integer.toString (chunk2ID)));
            }
        }

        this.notifier.log ("IDS_WS_DETECTED_APPLICATION", application, applicationVersion);

        return this.parseMultisample (new ByteArrayInputStream (chunks.get (2)), file, creationDateTime);
    }


    private static List<byte []> parseChunks (final InputStream in) throws IOException
    {
        final List<byte []> chunks = new ArrayList<> ();
        while (in.available () > 0)
        {
            final int size = (int) StreamUtils.readUnsigned32 (in, false);
            chunks.add (in.readNBytes (size));
        }
        return chunks;
    }


    /**
     * Parse the korgmultisample file.
     *
     * @param in The input stream to read from
     * @param file The source file
     * @param creationDateTime The creation date and time
     * @return The parsed multi-sample source
     * @throws FormatException Error in the format of the file
     * @throws IOException Could not read from the file
     */
    private List<IMultisampleSource> parseMultisample (final InputStream in, final File file, final Date creationDateTime) throws FormatException, IOException
    {
        checkAscii (in);

        final String name = StreamUtils.readWith1ByteLengthAscii (in);

        final String [] parts = AudioFileUtils.createPathParts (file.getParentFile (), this.sourceFolder, name);
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (file, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, file));
        final List<IGroup> groups = new ArrayList<> ();
        // There is only one group (no velocity zones)
        final DefaultGroup group = new DefaultGroup ("Layer");
        groups.add (group);

        final IMetadata metadata = multisampleSource.getMetadata ();
        metadata.setCreationDateTime (creationDateTime);

        int id;
        while ((id = in.read ()) != -1)
            switch (id)
            {
                case KorgmultisampleConstants.ID_AUTHOR:
                    metadata.setCreator (StreamUtils.readWith1ByteLengthAscii (in));
                    break;

                case KorgmultisampleConstants.ID_CATEGORY:
                    metadata.setCategory (StreamUtils.readWith1ByteLengthAscii (in));
                    break;

                case KorgmultisampleConstants.ID_COMMENT:
                    metadata.setDescription (StreamUtils.readWith1ByteLengthAscii (in));
                    break;

                case KorgmultisampleConstants.ID_SAMPLE:
                    group.addSampleZone (this.readSample (in, file.getParentFile ()));
                    break;

                case KorgmultisampleConstants.ID_UUID:
                    final int size = in.readNBytes (1)[0];
                    in.readNBytes (size);
                    break;

                default:
                    throw new FormatException (Integer.toHexString (id));
            }

        final String n = multisampleSource.getName ();
        if (n == null || n.isBlank () || "Empty".equals (n))
            multisampleSource.setName (FileUtils.getNameWithoutType (file));

        multisampleSource.setGroups (groups);
        return Collections.singletonList (multisampleSource);
    }


    /**
     * Read the data related to the sample configuration.
     *
     * @param in The input stream to read from
     * @param parentPath The path of the parent
     * @return The filled sample metadata
     * @throws IOException Could not read from the file
     * @throws FormatException Found unexpected format of the file
     */
    private ISampleZone readSample (final InputStream in, final File parentPath) throws IOException, FormatException
    {
        // Size of the sample block
        final int [] size = StreamUtils.read7bitNumberLE (in);
        final int blockLength = size[0];

        // 0x0A, Offset to key zone data
        in.readNBytes (2);

        checkAscii (in);
        final String sampleFileName = StreamUtils.readWith1ByteLengthAscii (in);
        final File sampleFile = this.createCanonicalFile (parentPath, sampleFileName);
        final ISampleData sampleData = new WavFileSampleData (sampleFile);
        final ISampleZone zone = new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), sampleData);

        int rest = blockLength - 3 - sampleFileName.length () - 1;
        rest = parseSampleParameters (zone, in, rest);
        parseKeyZoneParameters (zone, in, rest);

        return zone;
    }


    /**
     * Parses the data related to the samples' playback configuration.
     *
     * @param zone The sample metadata in which to stored the data
     * @param in The input stream to read from
     * @param rest The number of bytes available to read
     * @return The number of bytes not read
     * @throws IOException Could not read from the file
     * @throws FormatException FOund unexpected format of the file
     */
    private static int parseSampleParameters (final ISampleZone zone, final InputStream in, final int rest) throws IOException, FormatException
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
                case KorgmultisampleConstants.ID_START:
                    final int [] startNumber = StreamUtils.read7bitNumberLE (in);
                    r -= startNumber[1];
                    zone.setStart (startNumber[0]);
                    break;
                case KorgmultisampleConstants.ID_LOOP_START:
                    final int [] loopStartNumber = StreamUtils.read7bitNumberLE (in);
                    r -= loopStartNumber[1];
                    loopStart = loopStartNumber[0];
                    break;
                case KorgmultisampleConstants.ID_END:
                    final int [] endNumber = StreamUtils.read7bitNumberLE (in);
                    r -= endNumber[1];
                    zone.setStop (endNumber[0]);
                    break;
                case KorgmultisampleConstants.ID_LOOP_TUNE:
                    r -= 4;
                    // Not used
                    in.readNBytes (4);
                    break;
                case KorgmultisampleConstants.ID_ONE_SHOT:
                    r -= 1;
                    if (in.readNBytes (1)[0] > 0)
                        oneShot = true;
                    break;
                case KorgmultisampleConstants.ID_BOOST_12DB:
                    r -= 1;
                    if (in.readNBytes (1)[0] > 0)
                        zone.setGain (12);
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
            loop.setEnd (zone.getStop ());
            zone.addLoop (loop);
        }

        return r;
    }


    /**
     * Parses the data related to the samples' key zone configuration.
     *
     * @param zone The sample zone in which to store the data
     * @param in The input stream to read from
     * @param rest The number of bytes left to read
     * @throws IOException Could not read from the file
     * @throws FormatException FOund unexpected format of the file
     */
    private static void parseKeyZoneParameters (final ISampleZone zone, final InputStream in, final int rest) throws IOException, FormatException
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
                case KorgmultisampleConstants.ID_KEY_BOTTOM:
                    r -= 1;
                    zone.setKeyLow (in.readNBytes (1)[0]);
                    break;
                case KorgmultisampleConstants.ID_KEY_TOP:
                    r -= 1;
                    zone.setKeyHigh (in.readNBytes (1)[0]);
                    break;
                case KorgmultisampleConstants.ID_KEY_ORIGINAL:
                    r -= 1;
                    zone.setKeyRoot (in.readNBytes (1)[0]);
                    break;
                case KorgmultisampleConstants.ID_FIXED_PITCH:
                    r -= 1;
                    if (in.readNBytes (1)[0] > 0)
                        zone.setKeyTracking (0);
                    break;
                case KorgmultisampleConstants.ID_TUNE:
                    r -= 4;
                    // Read value is in the range of [-999..999]
                    zone.setTune (StreamUtils.readFloatLE (in.readNBytes (4)) / 1000.0);
                    break;
                case KorgmultisampleConstants.ID_LEVEL_LEFT:
                    r -= 4;
                    // Note: The left/right link button in the editor is only a UI thing!
                    final float levelLeftValue = StreamUtils.readFloatLE (in.readNBytes (4));
                    // This is not fully correct but since it is not documented what the percentages
                    // (-100..100%) mean in dB this is better than nothing...
                    zone.setGain (levelLeftValue / 100.0 * 12.0);
                    break;
                case KorgmultisampleConstants.ID_LEVEL_RIGHT:
                    r -= 4;
                    // This is -100..100%. We only handle one value (left)
                    in.readNBytes (4);
                    break;
                case KorgmultisampleConstants.ID_COLOR:
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
     * Checks if the next byte in the stream indicates an ASCII string (0x0A).
     *
     * @param in The input stream to read from
     * @throws IOException Could not read
     */
    private static void checkAscii (final InputStream in) throws IOException
    {
        final int blockType = in.read ();
        if (blockType != 0x0A)
            throw new IOException (Functions.getMessage ("IDS_WS_NO_ASCII"));
    }
}
