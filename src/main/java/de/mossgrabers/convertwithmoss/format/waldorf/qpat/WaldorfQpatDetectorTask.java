// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.exception.FormatException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects recursively Waldorf Quantum/Iridium files in folders. Files must end with <i>.qpat</i>.
 *
 * @author Jürgen Moßgraber
 */
public class WaldorfQpatDetectorTask extends AbstractDetectorTask
{
    private static final String         ENDING_QPAT = ".qpat";

    private static Map<Integer, String> SYNTH_CODES = new HashMap<> (3);
    static
    {
        SYNTH_CODES.put (Integer.valueOf (0), "Quantum");
        SYNTH_CODES.put (Integer.valueOf (1), "Iridium");
        SYNTH_CODES.put (Integer.valueOf (2), "IridiumCore");
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     */
    protected WaldorfQpatDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder)
    {
        super (notifier, consumer, sourceFolder, null, ENDING_QPAT);
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
     * Load and parse the QPAT file.
     *
     * @param in The input stream to read from
     * @param file The source file
     * @return The parsed multi-sample source
     * @throws FormatException Error in the format of the file
     * @throws IOException Could not read from the file
     */
    private List<IMultisampleSource> parseFile (final InputStream in, final File file) throws FormatException, IOException
    {
        final String name = FileUtils.getNameWithoutType (file);
        final String [] parts = AudioFileUtils.createPathParts (file.getParentFile (), this.sourceFolder, name);
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (file, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, file));

        final List<IGroup> groups = new ArrayList<> ();

        final long version = readHeader (in, multisampleSource);

        final long numParams = StreamUtils.readUnsigned16 (in, false);
        // Skip padding
        in.skipNBytes (2);

        final List<WaldorfQpatResourceHeader> resources = new ArrayList<> (WaldorfQpatConstants.MAX_RESOURCES);
        for (int i = 0; i < WaldorfQpatConstants.MAX_RESOURCES; i++)
        {
            final WaldorfQpatResourceHeader resourceHeader = new WaldorfQpatResourceHeader ();
            resourceHeader.read (in);
            if (resourceHeader.type == WaldorfQpatResourceType.USER_SAMPLE_MAP1 || resourceHeader.type == WaldorfQpatResourceType.USER_SAMPLE_MAP2 || resourceHeader.type == WaldorfQpatResourceType.USER_SAMPLE_MAP3)
                resources.add (resourceHeader);
        }

        final int flags = StreamUtils.readUnsigned16 (in, false);

        // TODO
        final boolean multiFlag = (flags & 1) > 0;
        // Single / Split / Layered
        final int timbreMode = StreamUtils.readUnsigned16 (in, false);
        final long altTimbreOffset = StreamUtils.readUnsigned32 (in, false);

        // Available from version 9 onwards. Instrument type on which the patch was saved last.
        final int synthCode = in.read ();
        this.notifier.log ("IDS_QPAT_VERSION", Long.toString (version), SYNTH_CODES.get (Integer.valueOf (synthCode)));

        // Padding up to 512 bytes.
        in.skipNBytes (75);

        final List<WaldorfQpatParameter> parameters = new ArrayList<> ();
        for (int i = 0; i < numParams; i++)
        {
            final WaldorfQpatParameter param = new WaldorfQpatParameter ();
            param.read (in);
            parameters.add (param);
        }

        final byte [] resourcesData = in.readAllBytes ();
        for (final WaldorfQpatResourceHeader resourceHeader: resources)
        {
            final String sampleMap = new String (resourcesData, resourceHeader.offset, resourceHeader.length, StandardCharsets.US_ASCII);
            final IGroup group = this.parseSampleMap (sampleMap, file.getParentFile ());
            if (resourceHeader.type == WaldorfQpatResourceType.USER_SAMPLE_MAP1)
                group.setName ("Sample Map 1");
            else if (resourceHeader.type == WaldorfQpatResourceType.USER_SAMPLE_MAP2)
                group.setName ("Sample Map 2");
            else if (resourceHeader.type == WaldorfQpatResourceType.USER_SAMPLE_MAP3)
                group.setName ("Sample Map 3");
            groups.add (group);
        }

        multisampleSource.setGroups (groups);
        return Collections.singletonList (multisampleSource);
    }


    /**
     * Reads and checks the header information preceding the actual data.
     *
     * @param in The input stream to read from
     * @param multisampleSource The multi-sample source
     * @throws IOException Could not read
     * @throws FormatException Found unexpected format of the file
     */
    private static long readHeader (final InputStream in, final DefaultMultisampleSource multisampleSource) throws IOException, FormatException
    {
        final long magic = StreamUtils.readUnsigned32 (in, false);
        if (magic != WaldorfQpatConstants.MAGIC)
            throw new IOException (Functions.getMessage ("IDS_QPAT_UNKNOWN_TYPE"));

        final long version = StreamUtils.readUnsigned32 (in, false);

        final String name = StreamUtils.readASCII (in, WaldorfQpatConstants.MAX_STRING_LENGTH).trim ();
        final String author = StreamUtils.readASCII (in, WaldorfQpatConstants.MAX_STRING_LENGTH).trim ();
        final String bank = StreamUtils.readASCII (in, WaldorfQpatConstants.MAX_STRING_LENGTH).trim ();

        final List<String> categories = new ArrayList<> ();
        for (int i = 0; i < 4; i++)
        {
            final String category = StreamUtils.readASCII (in, WaldorfQpatConstants.MAX_STRING_LENGTH).trim ();
            if (!category.isBlank ())
                categories.add (category);
        }

        multisampleSource.setName (name);
        final IMetadata metadata = multisampleSource.getMetadata ();
        metadata.setCreator (author);
        metadata.setDescription (bank);
        metadata.setCategory (TagDetector.detectCategory (categories));
        metadata.setKeywords (TagDetector.detectKeywords (categories));

        return version;
    }


    private IGroup parseSampleMap (final String sampleMap, final File parentFolder) throws IOException
    {
        final IGroup group = new DefaultGroup ();
        for (final String line: sampleMap.split ("\n"))
        {
            final String [] params = line.trim ().split ("\t");
            if (params.length == 0 || params[0].isBlank ())
                break;

            // Sample Path
            String samplePath = params[0];
            if (samplePath.startsWith ("\""))
                samplePath = samplePath.substring (1);
            if (samplePath.endsWith ("\""))
                samplePath = samplePath.substring (0, samplePath.length () - 1);
            if (samplePath.startsWith ("3:"))
                samplePath = samplePath.substring (2);
            if (samplePath.length () == 0)
                break;
            final File sampleFile = new File (parentFolder, samplePath);
            final ISampleData sampleData = this.createSampleData (sampleFile);

            final ISampleZone zone = new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), sampleData);
            group.addSampleZone (zone);
            final int numSampleFrames = sampleData.getAudioMetadata ().getNumberOfSamples ();

            // Pitch
            if (params.length <= 1)
                continue;
            final double pitch = Double.parseDouble (params[1]);
            final int coarse = (int) Math.round (pitch);
            final double fineTune = pitch - coarse;
            zone.setKeyRoot (coarse);
            zone.setTune (fineTune);

            // FromNote
            if (params.length <= 2)
                continue;
            zone.setKeyLow (MathUtils.clamp (Integer.parseInt (params[2]), 0, 127));

            // ToNote
            if (params.length <= 3)
                continue;
            zone.setKeyHigh (MathUtils.clamp (Integer.parseInt (params[3]), 0, 127));

            // Gain
            if (params.length <= 4)
                continue;
            final double gain = Double.parseDouble (params[4]);
            final double gaindB = gain == 0 ? -12.0 : MathUtils.clamp (Math.floor (20.0 * Math.log10 (gain) * 100.0 + 0.5) * 0.01, -12.0, 12.0);
            zone.setGain (gaindB);

            // FromVelo
            if (params.length <= 5)
                continue;
            zone.setVelocityLow (MathUtils.clamp (Integer.parseInt (params[5]), 1, 127));

            // ToVelo
            if (params.length <= 6)
                continue;
            zone.setVelocityHigh (MathUtils.clamp (Integer.parseInt (params[6]), 1, 127));

            // Pan
            if (params.length <= 7)
                continue;
            zone.setPanorama (MathUtils.clamp (Double.parseDouble (params[7]) * 2.0 - 1.0, -1.0, 1.0));

            // Start
            if (params.length <= 8)
                continue;
            zone.setStart ((int) (Double.parseDouble (params[8]) * numSampleFrames));

            // End
            if (params.length <= 9)
                continue;
            zone.setStop ((int) (Double.parseDouble (params[9]) * numSampleFrames));

            // LoopMode
            if (params.length <= 10)
                continue;
            final int loopMode = Integer.parseInt (params[10]);
            if (loopMode == 1 || loopMode == 2)
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                zone.getLoops ().add (loop);
                loop.setType (loopMode == 1 ? LoopType.FORWARDS : LoopType.BACKWARDS);

                // LoopStart
                if (params.length > 11)
                    loop.setStart ((int) (Double.parseDouble (params[11]) * numSampleFrames));

                // LoopEnd
                if (params.length > 12)
                    loop.setEnd ((int) (Double.parseDouble (params[12]) * numSampleFrames));

                // CrossFade
                if (params.length > 14)
                    loop.setCrossfade (Double.parseDouble (params[14]));
            }

            // Direction
            if (params.length <= 13)
                continue;
            if (Integer.parseInt (params[13]) == 1)
                zone.setReversed (true);

            // TrackPitch
            if (params.length > 15)
                zone.setKeyTracking (Double.parseDouble (params[15]));
        }

        return group;
    }
}
