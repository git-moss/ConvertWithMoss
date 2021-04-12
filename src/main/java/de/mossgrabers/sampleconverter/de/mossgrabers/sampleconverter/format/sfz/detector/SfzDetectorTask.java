// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.sfz.detector;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.ISampleMetadata;
import de.mossgrabers.sampleconverter.core.IVelocityLayer;
import de.mossgrabers.sampleconverter.core.LoopType;
import de.mossgrabers.sampleconverter.core.PlayLogic;
import de.mossgrabers.sampleconverter.core.SampleLoop;
import de.mossgrabers.sampleconverter.core.VelocityLayer;
import de.mossgrabers.sampleconverter.core.detector.AbstractDetectorTask;
import de.mossgrabers.sampleconverter.core.detector.MultisampleSource;
import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.wav.FormatChunk;
import de.mossgrabers.sampleconverter.file.wav.WaveFile;
import de.mossgrabers.sampleconverter.format.sfz.SfzHeader;
import de.mossgrabers.sampleconverter.format.sfz.SfzOpcode;
import de.mossgrabers.sampleconverter.format.sfz.SfzSampleMetadata;
import de.mossgrabers.sampleconverter.ui.tools.Functions;
import de.mossgrabers.sampleconverter.util.Pair;
import de.mossgrabers.sampleconverter.util.TagDetector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Detects recursively SFZ multisample files in folders. Files must end with <i>.sfz</i>.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class SfzDetectorTask extends AbstractDetectorTask
{
    private static final Pattern               HEADER_PATTERN    = Pattern.compile ("<([a-z]+)>([^<]*)", Pattern.DOTALL);
    private static final Pattern               ATTRIBUTE_PATTERN = Pattern.compile ("(\\b\\w+)=(.*?(?=\\s\\w+=|$))", Pattern.DOTALL);

    private static final Map<String, LoopType> LOOP_TYPE_MAPPER  = new HashMap<> (3);
    static
    {
        LOOP_TYPE_MAPPER.put ("forward", LoopType.FORWARD);
        LOOP_TYPE_MAPPER.put ("backward", LoopType.BACKWARDS);
        LOOP_TYPE_MAPPER.put ("alternate", LoopType.ALTERNATING);
    }

    private Map<String, String> globalAttributes = Collections.emptyMap ();
    private Map<String, String> masterAttributes = Collections.emptyMap ();
    private Map<String, String> groupAttributes  = Collections.emptyMap ();
    private Map<String, String> regionAttributes = Collections.emptyMap ();
    private final Set<String>   processedOpcodes = new HashSet<> ();
    private final Set<String>   allOpcodes       = new HashSet<> ();


    /** {@inheritDoc} */
    @Override
    protected void detect (final File folder)
    {
        // Detect all SFZ files in the folder
        this.notifier.get ().notify (Functions.getMessage ("IDS_NOTIFY_ANALYZING", folder.getAbsolutePath ()));
        if (this.waitForDelivery ())
            return;

        final File [] sfzFiles = folder.listFiles ( (parent, name) -> {

            if (this.isCancelled ())
                return false;

            final File f = new File (parent, name);
            if (f.isDirectory ())
            {
                this.detect (f);
                return false;
            }
            return name.toLowerCase (Locale.US).endsWith (".sfz");
        });
        if (sfzFiles.length == 0)
            return;

        for (final File sfzSampleFile: sfzFiles)
        {
            this.notifier.get ().notify (Functions.getMessage ("IDS_NOTIFY_ANALYZING", sfzSampleFile.getAbsolutePath ()));

            if (this.waitForDelivery ())
                break;

            final Optional<IMultisampleSource> multisample = this.readFile (sfzSampleFile);
            if (multisample.isPresent () && !this.isCancelled ())
                this.consumer.get ().accept (multisample.get ());
        }
    }


    /**
     * Read and parse the given SFZ file.
     *
     * @param sfzFile The file to process
     * @return The parse file information
     */
    private Optional<IMultisampleSource> readFile (final File sfzFile)
    {
        if (this.waitForDelivery ())
            return Optional.empty ();

        try
        {
            final String content = Files.readString (sfzFile.toPath ());
            return this.parseMetadataFile (sfzFile, content);
        }
        catch (final IOException ex)
        {
            this.notifier.get ().notifyError (Functions.getMessage ("IDS_NOTIFY_ERR_LOAD_FILE"), ex);
            return Optional.empty ();
        }
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param multiSampleFile The file
     * @param content The content to parse
     * @return The parsed multisample source
     */
    private Optional<IMultisampleSource> parseMetadataFile (final File multiSampleFile, final String content)
    {
        String name = getNameWithoutType (multiSampleFile);
        final String [] parts = createPathParts (multiSampleFile.getParentFile (), this.sourceFolder.get (), name);

        final Optional<String> globalName = this.getAttribute (SfzOpcode.GLOBAL_LABEL);
        if (globalName.isPresent ())
            name = globalName.get ();

        final MultisampleSource multisampleSource = new MultisampleSource (multiSampleFile, parts, name, this.subtractPaths (this.sourceFolder, multiSampleFile));

        // Use same guessing on the filename...
        multisampleSource.setCategory (TagDetector.detectCategory (parts));
        multisampleSource.setKeywords (TagDetector.detectKeywords (parts));

        final List<Pair<String, Map<String, String>>> result = parseSfz (content);
        if (result.isEmpty ())
            return Optional.empty ();

        final List<IVelocityLayer> velocityLayers = this.parseVelocityLayers (multiSampleFile.getParentFile (), result);
        multisampleSource.setVelocityLayers (velocityLayers);

        return Optional.of (multisampleSource);
    }


    /**
     * Parses all velocity layers (= regions in SFZ).
     *
     * @param basePath The path where the SFZ file is located, this is the base path for samples
     * @param headers All parsed headers with their key/value pairs
     * @return The parsed velocity layers
     */
    private List<IVelocityLayer> parseVelocityLayers (final File basePath, final List<Pair<String, Map<String, String>>> headers)
    {
        File sampleBaseFolder = basePath;

        final List<IVelocityLayer> velocityLayers = new ArrayList<> ();
        IVelocityLayer layer = new VelocityLayer ();
        for (final Pair<String, Map<String, String>> pair: headers)
        {
            final Map<String, String> attributes = pair.getValue ();
            this.allOpcodes.addAll (attributes.keySet ());

            switch (pair.getKey ())
            {
                case SfzHeader.CONTROL:
                    this.processedOpcodes.add (SfzOpcode.DEFAULT_PATH);
                    final String defaultPath = attributes.get (SfzOpcode.DEFAULT_PATH);
                    if (defaultPath != null)
                    {
                        sampleBaseFolder = new File (basePath, defaultPath.replace ('\\', '/'));
                        if (!sampleBaseFolder.exists ())
                        {
                            this.notifier.get ().notifyError (Functions.getMessage ("IDS_NOTIFY_ERR_SAMPLE_FOLDER_DOES_NOT_EXIST", sampleBaseFolder.getAbsolutePath ()));
                            return velocityLayers;
                        }
                    }
                    break;

                case SfzHeader.GLOBAL:
                    this.globalAttributes = attributes;
                    break;

                case SfzHeader.MASTER:
                    this.masterAttributes = attributes;
                    break;

                case SfzHeader.GROUP:
                    if (!layer.getSampleMetadata ().isEmpty ())
                        velocityLayers.add (layer);
                    layer = new VelocityLayer ();

                    this.groupAttributes = attributes;

                    final Optional<String> groupLabel = this.getAttribute (SfzOpcode.GROUP_LABEL);
                    if (groupLabel.isPresent ())
                        layer.setName (groupLabel.get ());

                    // We do not need the value but mark it as processed
                    this.getIntegerValue (SfzOpcode.SEQ_LENGTH);

                    break;

                case SfzHeader.REGION:
                    this.regionAttributes = attributes;

                    final Optional<String> sampleName = this.getAttribute (SfzOpcode.SAMPLE);
                    if (sampleName.isEmpty ())
                        continue;

                    final File sampleFile = new File (sampleBaseFolder, sampleName.get ());
                    if (this.checkSampleFile (sampleFile))
                    {
                        final SfzSampleMetadata sampleMetadata = new SfzSampleMetadata (sampleFile);
                        this.parseRegion (sampleMetadata);
                        try
                        {
                            sampleMetadata.addMissingInfoFromWaveFile ();
                        }
                        catch (final IOException ex)
                        {
                            this.notifier.get ().notifyError (Functions.getMessage ("IDS_NOTIFY_ERR_NO_SAMPLE_LENGTH"), ex);
                        }
                        layer.addSampleMetadata (sampleMetadata);
                    }
                    break;

                default:
                    // Other headers are not supported
                    break;
            }
        }

        // Don't forget to add the last layer
        if (!layer.getSampleMetadata ().isEmpty ())
            velocityLayers.add (layer);

        this.printUnsupportedOpcodes (this.diffOpcodes ());

        return velocityLayers;
    }


    /**
     * Parse all SFZ header with their key/value pairs.
     *
     * @param content The text to parse
     * @return The parse headers and key/value pairs
     */
    private static List<Pair<String, Map<String, String>>> parseSfz (final String content)
    {
        final List<Pair<String, Map<String, String>>> headersWithOpCodes = new ArrayList<> ();

        final Matcher blockMatcher = HEADER_PATTERN.matcher (content);
        while (blockMatcher.find ())
        {
            final String headerName = blockMatcher.group (1);
            if (headerName == null || headerName.isBlank ())
                continue;

            final String attributeBlock = blockMatcher.group (2);
            final Map<String, String> attributes = new HashMap<> ();
            if (attributeBlock != null && !attributeBlock.isBlank ())
            {
                final Matcher keyValueMatcher = ATTRIBUTE_PATTERN.matcher (attributeBlock);
                while (keyValueMatcher.find ())
                {
                    final String key = keyValueMatcher.group (1);
                    final String value = keyValueMatcher.group (2);
                    if (key != null && !key.isBlank () && value != null)
                        attributes.put (key.trim (), value.trim ());
                }
            }

            headersWithOpCodes.add (new Pair<> (headerName, attributes));
        }

        return headersWithOpCodes;
    }


    /**
     * Parses the key/values of a region header.
     *
     * @param sampleMetadata Where to store the parsed information
     */
    private void parseRegion (final ISampleMetadata sampleMetadata)
    {
        final Optional<String> direction = this.getAttribute (SfzOpcode.DIRECTION);
        if (direction.isPresent ())
            sampleMetadata.setReversed ("reverse".equals (direction.get ()));
        if (this.getIntegerValue (SfzOpcode.SEQ_POSITION) > 0)
            sampleMetadata.setPlayLogic (PlayLogic.ROUND_ROBIN);

        final int offset = this.getIntegerValue (SfzOpcode.OFFSET);
        if (offset >= 0)
            sampleMetadata.setStart (offset);
        final int end = this.getIntegerValue (SfzOpcode.END);
        if (end >= 0)
            sampleMetadata.setStop (end);

        ////////////////////////////////////////////////////////////
        // Key range

        final int key = this.getIntegerValue (SfzOpcode.KEY);
        if (key >= 0)
        {
            sampleMetadata.setKeyRoot (key);
            sampleMetadata.setKeyLow (key);
            sampleMetadata.setKeyHigh (key);
        }

        // Lower bounds including crossfade
        int lowKey = this.getIntegerValue (SfzOpcode.XF_IN_LO_KEY);
        if (lowKey < 0)
            lowKey = this.getIntegerValue (SfzOpcode.LO_KEY);
        else
        {
            final int xfInHighKey = this.getIntegerValue (SfzOpcode.XF_IN_HI_KEY);
            if (xfInHighKey >= 0)
                sampleMetadata.setNoteCrossfadeLow (xfInHighKey - lowKey);
        }
        if (lowKey >= 0)
            sampleMetadata.setKeyLow (lowKey);

        // Upper bounds including crossfade
        int highKey = this.getIntegerValue (SfzOpcode.XF_OUT_HI_KEY);
        if (highKey < 0)
            highKey = this.getIntegerValue (SfzOpcode.HI_KEY);
        else
        {
            final int xfOutLowKey = this.getIntegerValue (SfzOpcode.XF_OUT_LO_KEY);
            if (xfOutLowKey >= 0)
                sampleMetadata.setNoteCrossfadeHigh (highKey - xfOutLowKey);
        }
        if (highKey >= 0)
            sampleMetadata.setKeyHigh (highKey);

        // The center key
        final int pitchKeyCenter = this.getIntegerValue (SfzOpcode.PITCH_KEY_CENTER);
        if (pitchKeyCenter >= 0)
            sampleMetadata.setKeyRoot (pitchKeyCenter);

        ////////////////////////////////////////////////////////////
        // Velocity

        // Lower bounds including crossfade
        int lowVel = this.getIntegerValue (SfzOpcode.XF_IN_LO_VEL);
        if (lowVel < 0)
            lowVel = this.getIntegerValue (SfzOpcode.LO_VEL);
        else
        {
            final int xfInHighVel = this.getIntegerValue (SfzOpcode.XF_IN_HI_VEL);
            if (xfInHighVel >= 0)
                sampleMetadata.setNoteCrossfadeLow (xfInHighVel - lowVel);
        }
        if (lowVel >= 0)
            sampleMetadata.setVelocityLow (lowVel);

        // Upper bounds including crossfade
        int highVel = this.getIntegerValue (SfzOpcode.XF_OUT_HI_VEL);
        if (highVel < 0)
            highVel = this.getIntegerValue (SfzOpcode.HI_VEL);
        else
        {
            final int xfOutLowVel = this.getIntegerValue (SfzOpcode.XF_OUT_LO_VEL);
            if (xfOutLowVel >= 0)
                sampleMetadata.setNoteCrossfadeHigh (highVel - xfOutLowVel);
        }
        if (highVel >= 0)
            sampleMetadata.setVelocityHigh (highVel);

        ////////////////////////////////////////////////////////////
        // Sample Loop

        final Optional<String> loopMode = this.getAttribute (SfzOpcode.LOOP_MODE);
        if (loopMode.isPresent ())
        {
            switch (loopMode.get ())
            {
                default:
                case "no_loop":
                case "one_shot":
                    // No looping
                    break;

                case "loop_continuous":
                case "loop_sustain":
                    final SampleLoop loop = new SampleLoop ();
                    final Optional<String> loopType = this.getAttribute (SfzOpcode.LOOP_TYPE);
                    if (loopType.isPresent ())
                    {
                        final LoopType type = LOOP_TYPE_MAPPER.get (loopType.get ());
                        if (type != null)
                            loop.setType (type);
                    }

                    final int loopStart = this.getIntegerValue (SfzOpcode.LOOP_START);
                    if (loopStart >= 0)
                        loop.setStart (loopStart);
                    final int loopEnd = this.getIntegerValue (SfzOpcode.LOOP_END);
                    if (loopEnd >= 0)
                        loop.setEnd (loopEnd);

                    final double crossfadeInSeconds = this.getDoubleValue (SfzOpcode.LOOP_CROSSFADE, 0);
                    if (crossfadeInSeconds >= 0)
                    {
                        // Calculate seconds in percent of the loop length
                        final int loopLength = loop.getStart () - loop.getEnd ();
                        if (loopLength > 0)
                        {
                            final double loopLengthInSeconds = loopLength / (double) sampleMetadata.getSampleRate ();
                            final double crossfade = crossfadeInSeconds / loopLengthInSeconds;
                            if (crossfade > 0 && crossfade <= 1)
                                loop.setCrossfade (crossfade);
                        }
                    }

                    sampleMetadata.addLoop (loop);
                    break;
            }
        }

        ////////////////////////////////////////////////////////////
        // Tune

        double tune = this.getDoubleValue (SfzOpcode.TUNE, 0);
        if (tune == 0)
            tune = this.getDoubleValue (SfzOpcode.PITCH, 0);
        sampleMetadata.setTune (Math.min (100, Math.max (-100, tune)) / 100.0);

        final double pitchKeytrack = this.getDoubleValue (SfzOpcode.PITCH_KEYTRACK, 100);
        sampleMetadata.setKeyTracking (Math.min (100, Math.max (0, pitchKeytrack)) / 100.0);

        ////////////////////////////////////////////////////////////
        // Volume

        final double volume = this.getDoubleValue (SfzOpcode.VOLUME, 0);
        sampleMetadata.setGain (Math.min (12, Math.max (-12, volume)));
    }


    /**
     * Calculate the difference between the supported and present opcodes.
     *
     * @return The unsupported opcodes which are present in the parsed SFZ file
     */
    private Set<String> diffOpcodes ()
    {
        final Set<String> unsupported = new HashSet<> ();
        this.allOpcodes.forEach (attribute -> {
            if (!this.processedOpcodes.contains (attribute))
                unsupported.add (attribute);
        });
        return unsupported;
    }


    /**
     * Formats and reports all unsupported opcodes.
     *
     * @param unsupportedOpcodes The unsupported opcodes
     */
    private void printUnsupportedOpcodes (final Set<String> unsupportedOpcodes)
    {
        final StringBuilder sb = new StringBuilder ();

        unsupportedOpcodes.forEach (attribute -> {
            if (!sb.isEmpty ())
                sb.append (", ");
            sb.append (attribute);
        });

        if (!sb.isEmpty ())
            this.notifier.get ().notifyError (Functions.getMessage ("IDS_NOTIFY_UNSUPPORTED_OPCODES", sb.toString ()));
    }


    /**
     * Get the attribute integer value for the given key. The value is search starting from region
     * upwards to group, master and finally global.
     *
     * @param key The key of the value to lookup
     * @return The value or -1 if not found or is not an integer
     */
    private int getIntegerValue (final String key)
    {
        final Optional<String> value = this.getAttribute (key);
        if (value.isEmpty ())
            return -1;
        try
        {
            return Integer.parseInt (value.get ());
        }
        catch (final NumberFormatException ex)
        {
            return -1;
        }
    }


    /**
     * Get the attribute double value for the given key. The value is search starting from region
     * upwards to group, master and finally global.
     *
     * @param key The key of the value to lookup
     * @param defaultValue The value to return if the key is not present or cannot be read
     * @return The value or 0 if not found or is not a double
     */
    private double getDoubleValue (final String key, final double defaultValue)
    {
        final Optional<String> value = this.getAttribute (key);
        if (value.isEmpty ())
            return defaultValue;
        try
        {
            return Double.parseDouble (value.get ());
        }
        catch (final NumberFormatException ex)
        {
            return defaultValue;
        }
    }


    /**
     * Get the attribute value for the given key. The value is search starting from region upwards
     * to group, master and finally global.
     *
     * @param key The key of the value to lookup
     * @return The optional value or empty if not found
     */
    private Optional<String> getAttribute (final String key)
    {
        String value = this.regionAttributes.get (key);
        if (value == null)
        {
            value = this.groupAttributes.get (key);
            if (value == null)
            {
                value = this.masterAttributes.get (key);
                if (value == null)
                    value = this.globalAttributes.get (key);
            }
        }

        if (value != null)
            this.processedOpcodes.add (key);
        return Optional.ofNullable (value);
    }


    /**
     * Test the sample file for compatibility.
     *
     * @param sampleFile The sample file to check
     * @return True if OK
     */
    private boolean checkSampleFile (final File sampleFile)
    {
        if (!sampleFile.exists ())
        {
            this.notifier.get ().notifyError (Functions.getMessage ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", sampleFile.getAbsolutePath ()));
            return false;
        }

        try
        {
            final FormatChunk formatChunk = new WaveFile (sampleFile, true).getFormatChunk ();
            if (formatChunk == null)
            {
                this.notifier.get ().notifyError (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_WAV", sampleFile.getAbsolutePath ()));
                return false;
            }

            final int numberOfChannels = formatChunk.getNumberOfChannels ();
            if (numberOfChannels > 2)
            {
                this.notifier.get ().notifyError (Functions.getMessage ("IDS_NOTIFY_ERR_MONO", Integer.toString (numberOfChannels), sampleFile.getAbsolutePath ()));
                return false;
            }
        }
        catch (final IOException | ParseException ex)
        {
            this.notifier.get ().notifyError (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_WAV"), ex);
            return false;
        }

        return true;
    }
}
