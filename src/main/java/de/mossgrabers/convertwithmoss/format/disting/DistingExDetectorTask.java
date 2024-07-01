// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.disting;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.NoteParser;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFileBasedSampleData;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.exception.FormatException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects recursively DistingEX preset files in folders. Files must end with <i>.dexpreset</i>.
 *
 * @author Jürgen Moßgraber
 */
public class DistingExDetectorTask extends AbstractDetectorTask
{
    private static final String  ENDING_DISTING_EX = ".dexpreset";
    private static final Pattern FILE_NAME_QUERY   = Pattern.compile ("(.*)_(?<note>[A-Ga-g]#?\\d)((_SW(?<switch>\\d+))?(_V(?<velocity>\\d+))?(_R(?<roundrobin>\\d+))?)?");


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadataConfig Additional metadata configuration parameters
     */
    protected DistingExDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadataConfig)
    {
        super (notifier, consumer, sourceFolder, metadataConfig, ENDING_DISTING_EX);
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
     * Load and parse the DistingEX file.
     *
     * @param in The input stream to read from
     * @param file The source file
     * @return The parsed multi-sample source
     * @throws FormatException Error in the format of the file
     * @throws IOException Could not read from the file
     */
    private List<IMultisampleSource> parseFile (final InputStream in, final File file) throws FormatException, IOException
    {
        String name = this.readHeader (in);
        if (name.isBlank ())
            name = FileUtils.getNameWithoutType (file);

        final File parentFile = file.getParentFile ();
        final String [] parts = AudioFileUtils.createPathParts (parentFile, this.sourceFolder, name);
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (file, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, file));

        final int [] parameters = readParameters (in);

        final List<IGroup> groups = this.readSamples (parentFile, in);
        multisampleSource.setGroups (groups);

        applyParameters (groups, parameters);

        this.createMetadata (multisampleSource.getMetadata (), this.getFirstSample (groups), parts);

        return Collections.singletonList (multisampleSource);
    }


    /**
     * Reads and checks the header information preceding the actual data.
     *
     * @param in The input stream to read from
     * @return The preset name
     * @throws IOException Could not read
     * @throws FormatException Found unexpected format of the file
     */
    private String readHeader (final InputStream in) throws IOException, FormatException
    {
        final byte [] headerTag = in.readNBytes (8);
        StreamUtils.checkTag ("DEXBPRST", headerTag);

        final int fileVersion = StreamUtils.readSigned32 (in, false);
        if (fileVersion != 1)
            throw new IOException (Functions.getMessage ("IDS_DEX_UNKNOWN_FILE_VERSION", Integer.toString (fileVersion)));

        // All empty
        in.skipNBytes (500);

        final int presetVersion = StreamUtils.readSigned32 (in, false);
        if (presetVersion != 0x14)
            this.notifier.logError ("IDS_DEX_UNKNOWN_PRESET_VERSION", Integer.toString (presetVersion));

        final String name = StreamUtils.readASCII (in, 16).trim ();

        // Unknown
        StreamUtils.readSigned32 (in, false);

        // Skip dual mode settings (all 0 in single mode)
        in.skipNBytes (32);

        final int algorithm = StreamUtils.readSigned32 (in, false);
        if (algorithm != 2)
            throw new IOException (Functions.getMessage ("IDS_DEX_NO_SD_PRESET"));

        return name;
    }


    private List<IGroup> readSamples (final File parentFile, final InputStream in) throws IOException
    {
        final Map<Integer, IGroup> groups = new TreeMap<> ();

        for (final File file: getWavFiles (parentFile, in))
        {
            final IFileBasedSampleData sampleData = this.createSampleData (file);
            final String nameWithoutType = FileUtils.getNameWithoutType (file);
            final ISampleZone zone = new DefaultSampleZone (nameWithoutType, sampleData);

            int velocity = 0;

            // Try to get the configuration information from the file name
            final Matcher matcher = FILE_NAME_QUERY.matcher (nameWithoutType);
            if (matcher.matches ())
            {
                final String note = matcher.group ("note");
                final String keySwitch = matcher.group ("switch");
                final String velocityIndex = matcher.group ("velocity");
                final String roundrobinIndex = matcher.group ("roundrobin");
                if (note != null)
                    zone.setKeyRoot (NoteParser.parseNote (note));
                if (keySwitch != null)
                    zone.setKeyHigh (Integer.parseInt (keySwitch) + 12);
                if (velocityIndex != null)
                    velocity = Integer.parseInt (velocityIndex);
                if (roundrobinIndex != null)
                    zone.setPlayLogic (PlayLogic.ROUND_ROBIN);
            }
            if (zone.getKeyRoot () < 0)
                zone.setKeyTracking (0);

            // Add missing information from the WAV file
            zone.getSampleData ().addZoneData (zone, true, true);

            groups.computeIfAbsent (Integer.valueOf (velocity), v -> new DefaultGroup ("Group " + v)).addSampleZone (zone);
        }

        final List<IGroup> groupsList = new ArrayList<> (groups.values ());
        calculateKeyAndVelocityRanges (groupsList);

        return groupsList;
    }


    /**
     * Calculates the velocity range for each group depending on the number of detected velocity
     * indexes.
     *
     * @param groupsList All velocity groups
     */
    private static void calculateKeyAndVelocityRanges (final List<IGroup> groupsList)
    {
        final int size = groupsList.size ();
        final int velocitySteps = (int) Math.round (127.0 / size);

        for (int i = 0; i < size; i++)
        {
            final IGroup group = groupsList.get (i);
            final int velocityLow = 1 + i * velocitySteps;
            int velocityHigh = Math.clamp ((i + 1) * velocitySteps, 1, 127);
            // Ensure that the last step reaches till the highest velocity
            if (i == size - 1)
                velocityHigh = 127;

            final List<ISampleZone> sampleZones = group.getSampleZones ();
            for (final ISampleZone zone: sampleZones)
            {
                zone.setVelocityLow (velocityLow);
                zone.setVelocityHigh (velocityHigh);
            }
            calculateKeyRanges (sampleZones);
        }
    }


    private static void calculateKeyRanges (final List<ISampleZone> zones)
    {
        Collections.sort (zones, Comparator.comparing (ISampleZone::getKeyHigh));

        int lowKey = 0;
        for (final ISampleZone zone: zones)
        {
            zone.setKeyLow (lowKey);
            lowKey = zone.getKeyHigh () + 1;
        }
    }


    private static void applyParameters (final List<IGroup> groups, final int [] parameters)
    {
        // Range 1ms-15s
        final double attack = 0.001 * Math.exp (0.0757 * parameters[7]);
        // Range 20ms-15s
        final double decay = 0.02 * Math.exp (0.0521 * parameters[8]);
        // Range 10ms-30s
        final double release = 0.01 * Math.exp (0.0630 * parameters[10]);
        final double sustain = parameters[9] / 127.0;

        // Octave + Transpose + fine tune
        final double tune = parameters[11] * 12 + parameters[12] + parameters[13] / 100.0;

        // Gain in the range of -40..24 dB
        final int gain = parameters[14];

        // Pitch bend 0..48 semi-tones
        final int pitchBendRange = parameters[18];

        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                zone.setTune (tune);
                zone.setGain (gain);
                zone.setBendUp (pitchBendRange);
                zone.setBendDown (-pitchBendRange);

                final IEnvelopeModulator amplitudeEnvelopeModulator = zone.getAmplitudeEnvelopeModulator ();
                amplitudeEnvelopeModulator.setDepth (1);
                final IEnvelope envelope = amplitudeEnvelopeModulator.getSource ();
                envelope.setAttackTime (attack);
                envelope.setDecayTime (decay);
                envelope.setSustainLevel (sustain);
                envelope.setReleaseTime (release);
            }
    }


    /**
     * Print all parameter values to the console. Only for testing.
     *
     * @param parameters The 80 parameters
     */
    public static void printParameters (final int [] parameters)
    {
        System.out.println ("Attenuverter 1: " + parameters[0]);
        System.out.println ("Attenuverter 2: " + parameters[1]);
        System.out.println ("Attenuverter 3: " + parameters[2]);
        System.out.println ("Attenuverter 4: " + parameters[3]);
        System.out.println ("Attenuverter 5: " + parameters[4]);
        System.out.println ("Attenuverter 6: " + parameters[5]);
        System.out.println ("Folder: " + parameters[6]);
        System.out.println ("Attack time: " + parameters[7]);
        System.out.println ("Decay time: " + parameters[8]);
        System.out.println ("Sustain level: " + parameters[9]);
        System.out.println ("Release time: " + parameters[10]);
        System.out.println ("Octave: " + parameters[11]);
        System.out.println ("Transpose: " + parameters[12]);
        System.out.println ("Fine tune: " + parameters[13]);
        System.out.println ("Gain: " + parameters[14]);
        System.out.println ("Saturation: " + parameters[15]);
        System.out.println ("Sustain: " + parameters[16]);
        System.out.println ("Max voices: " + parameters[17]);
        System.out.println ("Bend range: " + parameters[18]);
        System.out.println ("Pitch bend input: " + parameters[19]);
        System.out.println ("Voice 1 detune: " + parameters[20]);
        System.out.println ("Voice 2 detune: " + parameters[21]);
        System.out.println ("Voice 3 detune: " + parameters[22]);
        System.out.println ("Voice 4 detune: " + parameters[23]);
        System.out.println ("Voice 5 detune: " + parameters[24]);
        System.out.println ("Voice 6 detune: " + parameters[25]);
        System.out.println ("Voice 7 detune: " + parameters[26]);
        System.out.println ("Voice 8 detune: " + parameters[27]);
        System.out.println ("Chord enable: " + parameters[28]);
        System.out.println ("Chord key: " + parameters[29]);
        System.out.println ("Chord scale: " + parameters[30]);
        System.out.println ("Chord shape: " + parameters[31]);
        System.out.println ("Chord inversion: " + parameters[32]);
        System.out.println ("Arpeggio 1 mode: " + parameters[33]);
        System.out.println ("Arpeggio 2 mode: " + parameters[34]);
        System.out.println ("Arpeggio 3 mode: " + parameters[35]);
        System.out.println ("Arpeggio 1 range: " + parameters[36]);
        System.out.println ("Arpeggio 2 range: " + parameters[37]);
        System.out.println ("Arpeggio 3 range: " + parameters[38]);
        System.out.println ("Scala/MTS: " + parameters[39]);
        System.out.println ("Scala KBM: " + parameters[40]);
        System.out.println ("Folder 2: " + parameters[41]);
        System.out.println ("Folder 3: " + parameters[42]);
        System.out.println ("Min note 1: " + parameters[43]);
        System.out.println ("Max note 1: " + parameters[44]);
        System.out.println ("Min note 2: " + parameters[45]);
        System.out.println ("Max note 2: " + parameters[46]);
        System.out.println ("Min note 3: " + parameters[47]);
        System.out.println ("Max note 3: " + parameters[48]);
        System.out.println ("Output spread: " + parameters[49]);
        System.out.println ("Delay mode: " + parameters[50]);
        System.out.println ("Delay level: " + parameters[51]);
        System.out.println ("Delay time: " + parameters[52]);
        System.out.println ("Delay feedback: " + parameters[53]);
        System.out.println ("Tone bass: " + parameters[54]);
        System.out.println ("Tone treble: " + parameters[55]);
        System.out.println ("Break time: " + parameters[56]);
        System.out.println ("Break direction: " + parameters[57]);
        System.out.println ("Voice 1 bend input: " + parameters[58]);
        System.out.println ("Voice 2 bend input: " + parameters[59]);
        System.out.println ("Voice 3 bend input: " + parameters[60]);
        System.out.println ("Voice 4 bend input: " + parameters[61]);
        System.out.println ("Voice 5 bend input: " + parameters[62]);
        System.out.println ("Voice 6 bend input: " + parameters[63]);
        System.out.println ("Voice 7 bend input: " + parameters[64]);
        System.out.println ("Voice 8 bend input: " + parameters[65]);
        System.out.println ("Output mode: " + parameters[66]);
        System.out.println ("Input mode: " + parameters[67]);
        System.out.println ("Sustain mode: " + parameters[68]);
        System.out.println ("MIDI vel curve: " + parameters[69]);
        System.out.println ("Arp reset input: " + parameters[70]);
        System.out.println ("Gate offset: " + parameters[71]);
        System.out.println ("Round robin mode: " + parameters[72]);
        System.out.println (": " + parameters[73]);
        System.out.println (": " + parameters[74]);
        System.out.println (": " + parameters[75]);
        System.out.println (": " + parameters[76]);
        System.out.println (": " + parameters[77]);
        System.out.println (": " + parameters[78]);
        System.out.println (": " + parameters[79]);
    }


    /**
     * Read the short values of all 80 parameters from the input stream
     *
     * @param in The in put stream to read from
     * @return The parameter values
     * @throws IOException Could not read the values
     */
    private static int [] readParameters (final InputStream in) throws IOException
    {
        final int [] parameters = new int [80];
        for (int i = 0; i < 80; i++)
            parameters[i] = MathUtils.fromSignedComplement (StreamUtils.readUnsigned16 (in, false));
        return parameters;
    }


    /**
     * Reads the sub-path from the input stream which contains the samples.
     *
     * @param parentPath The parent path which contains the sub-path
     * @param in The input stream
     * @return All WAV files found in the sub-path
     * @throws IOException Could not read the sub-path or it does not exist
     */
    private static File [] getWavFiles (final File parentPath, final InputStream in) throws IOException
    {
        final String subPath = StreamUtils.readNullTerminatedASCIIMax (in, 21).trim ();
        final File sampleFolder = new File (parentPath, subPath);
        if (!sampleFolder.exists ())
            throw new IOException (Functions.getMessage ("IDS_DEX_NO_SAMPLE_FOLDER", sampleFolder.getCanonicalPath ()));

        final File [] wavFiles = sampleFolder.listFiles ( (parent, name) -> {
            final File f = new File (parent, name);
            return f.isFile () && name.toLowerCase (Locale.US).endsWith (".wav");
        });
        return wavFiles;
    }
}
