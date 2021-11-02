// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.sfz;

import de.mossgrabers.sampleconverter.core.DefaultSampleMetadata;
import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.ISampleMetadata;
import de.mossgrabers.sampleconverter.core.IVelocityLayer;
import de.mossgrabers.sampleconverter.core.LoopType;
import de.mossgrabers.sampleconverter.core.PlayLogic;
import de.mossgrabers.sampleconverter.core.SampleLoop;
import de.mossgrabers.sampleconverter.core.VelocityLayer;
import de.mossgrabers.sampleconverter.core.detector.AbstractDetectorTask;
import de.mossgrabers.sampleconverter.core.detector.MultisampleSource;
import de.mossgrabers.sampleconverter.file.Utils;
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
import java.util.function.Consumer;
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
    private static final Pattern               ATTRIBUTE_PATTERN = Pattern.compile ("(\\b\\w+)=(.*?(?=\\s\\w+=|//|$))", Pattern.DOTALL);

    private static final Map<String, LoopType> LOOP_TYPE_MAPPER  = new HashMap<> (3);
    static
    {
        LOOP_TYPE_MAPPER.put ("forward", LoopType.FORWARD);
        LOOP_TYPE_MAPPER.put ("backward", LoopType.BACKWARDS);
        LOOP_TYPE_MAPPER.put ("alternate", LoopType.ALTERNATING);
    }

    /** The names of notes. */
    private static final String []            NOTE_NAMES_FLAT         =
    {
        "C",
        "Db",
        "D",
        "Eb",
        "E",
        "F",
        "Gb",
        "G",
        "Ab",
        "A",
        "Bb",
        "B"
    };
    private static final String []            NOTE_NAMES_SHARP        =
    {
        "C",
        "C#",
        "D",
        "D#",
        "E",
        "F",
        "F#",
        "G",
        "G#",
        "A",
        "A#",
        "B"
    };
    /** The names of notes. */
    private static final String []            NOTE_NAMES_FLAT_GERMAN  =
    {
        "C",
        "Db",
        "D",
        "Eb",
        "E",
        "F",
        "Gb",
        "G",
        "Ab",
        "A",
        "Bb",
        "H"
    };
    private static final String []            NOTE_NAMES_SHARP_GERMAN =
    {
        "C",
        "C#",
        "D",
        "D#",
        "E",
        "F",
        "F#",
        "G",
        "G#",
        "A",
        "A#",
        "H"
    };

    private static final Map<String, Integer> KEY_MAP                 = new HashMap<> ();

    static
    {
        // Create note map
        for (int note = 0; note < 128; note++)
        {
            final int n = Math.abs (note % 12);
            final String octave = Integer.toString (note / 12 - 2);
            final Integer ni = Integer.valueOf (note);
            KEY_MAP.put (NOTE_NAMES_FLAT[n] + octave, ni);
            KEY_MAP.put (NOTE_NAMES_SHARP[n] + octave, ni);
            KEY_MAP.put (NOTE_NAMES_FLAT_GERMAN[n] + octave, ni);
            KEY_MAP.put (NOTE_NAMES_SHARP_GERMAN[n] + octave, ni);
            KEY_MAP.put (String.format ("%d", ni), ni);
        }
    }

    private Map<String, String> globalAttributes = Collections.emptyMap ();
    private Map<String, String> masterAttributes = Collections.emptyMap ();
    private Map<String, String> groupAttributes  = Collections.emptyMap ();
    private Map<String, String> regionAttributes = Collections.emptyMap ();
    private final Set<String>   processedOpcodes = new HashSet<> ();
    private final Set<String>   allOpcodes       = new HashSet<> ();


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     */
    public SfzDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder)
    {
        super (notifier, consumer, sourceFolder, ".sfz");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final String content = Files.readString (file.toPath ());
            return this.parseMetadataFile (file, content);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param multiSampleFile The file
     * @param content The content to parse
     * @return The parsed multisample source
     */
    private List<IMultisampleSource> parseMetadataFile (final File multiSampleFile, final String content)
    {
        final List<Pair<String, Map<String, String>>> result = parseSfz (content);
        if (result.isEmpty ())
            return Collections.emptyList ();

        String name = getNameWithoutType (multiSampleFile);
        final String [] parts = createPathParts (multiSampleFile.getParentFile (), this.sourceFolder, name);

        this.processedOpcodes.add (SfzOpcode.GLOBAL_LABEL);
        final List<IVelocityLayer> velocityLayers = this.parseVelocityLayers (multiSampleFile.getParentFile (), result);

        final Optional<String> globalName = this.getAttribute (SfzOpcode.GLOBAL_LABEL);
        if (globalName.isPresent ())
            name = globalName.get ();

        final MultisampleSource multisampleSource = new MultisampleSource (multiSampleFile, parts, name, this.subtractPaths (this.sourceFolder, multiSampleFile));

        // Use same guessing on the filename...
        multisampleSource.setCategory (TagDetector.detectCategory (parts));
        multisampleSource.setKeywords (TagDetector.detectKeywords (parts));

        multisampleSource.setVelocityLayers (velocityLayers);

        return Collections.singletonList (multisampleSource);
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
                        // The default path might be relative, so make sure it is fully
                        // canonical otherwise samples will not be found
                        sampleBaseFolder = Utils.makeCanonical (new File (basePath, defaultPath.replace ('\\', '/')));
                        if (!sampleBaseFolder.exists ())
                        {
                            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_FOLDER_DOES_NOT_EXIST", sampleBaseFolder.getAbsolutePath ());
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

                    final File sampleFile = this.createCanonicalFile (sampleBaseFolder, sampleName.get ());
                    if (this.checkSampleFile (sampleFile))
                    {
                        final DefaultSampleMetadata sampleMetadata = new DefaultSampleMetadata (sampleFile);
                        this.parseRegion (sampleMetadata);
                        this.loadMissingValues (sampleMetadata);
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

        // Fix empty names
        for (int i = 0; i < velocityLayers.size (); i++)
        {
            final IVelocityLayer velocityLayer = velocityLayers.get (i);
            final String name = velocityLayer.getName ();
            if (name == null || name.isBlank ())
                velocityLayer.setName ("Group " + (i + 1));
        }

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

        final int key = this.getKeyValue (SfzOpcode.KEY);
        if (key >= 0)
        {
            sampleMetadata.setKeyRoot (key);
            sampleMetadata.setKeyLow (key);
            sampleMetadata.setKeyHigh (key);
        }

        // Lower bounds including crossfade
        int lowKey = this.getKeyValue (SfzOpcode.XF_IN_LO_KEY);
        if (lowKey < 0)
            lowKey = this.getKeyValue (SfzOpcode.LO_KEY);
        else
        {
            final int xfInHighKey = this.getKeyValue (SfzOpcode.XF_IN_HI_KEY);
            if (xfInHighKey >= 0)
                sampleMetadata.setNoteCrossfadeLow (xfInHighKey - lowKey);
        }
        if (lowKey >= 0)
            sampleMetadata.setKeyLow (lowKey);

        // Upper bounds including crossfade
        int highKey = this.getKeyValue (SfzOpcode.XF_OUT_HI_KEY);
        if (highKey < 0)
            highKey = this.getKeyValue (SfzOpcode.HI_KEY);
        else
        {
            final int xfOutLowKey = this.getKeyValue (SfzOpcode.XF_OUT_LO_KEY);
            if (xfOutLowKey >= 0)
                sampleMetadata.setNoteCrossfadeHigh (highKey - xfOutLowKey);
        }
        if (highKey >= 0)
            sampleMetadata.setKeyHigh (highKey);

        // The center key
        final int pitchKeyCenter = this.getKeyValue (SfzOpcode.PITCH_KEY_CENTER);
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
            this.notifier.logError ("IDS_NOTIFY_UNSUPPORTED_OPCODES", sb.toString ());
    }


    /**
     * Get the attribute key value for the given key. The value might be a MIDI note or a The value
     * is searched starting from region upwards to group, master and finally global.
     *
     * @param key The key of the value to lookup
     * @return The value or -1 if not found or is not an integer
     */
    private int getKeyValue (final String key)
    {
        final Optional<String> value = this.getAttribute (key);
        if (value.isEmpty ())
            return -1;
        final String noteValue = value.get ().toUpperCase (Locale.US);
        return KEY_MAP.getOrDefault (noteValue, Integer.valueOf (-1)).intValue ();
    }


    /**
     * Get the attribute integer value for the given key. The value is searched starting from region
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
     * Get the attribute double value for the given key. The value is searched starting from region
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
}
