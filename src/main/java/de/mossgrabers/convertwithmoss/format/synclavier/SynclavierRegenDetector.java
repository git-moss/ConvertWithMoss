// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synclavier;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.DoubleConsumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects recursively Synclavier Regen timbre files in folders. A timbre is stored as a text file
 * whose name ends with <i>-Entry.txt</i> and which contains the tag
 * <i>SynclavierVirtualInstrumentTimbreVersion</i>. The referenced samples are stored in the same
 * folder as obfuscated FLAC files with the ending <i>.sflc</i> (see {@link SynclavierRegenCodec}).
 *
 * @author Jürgen Moßgraber
 */
public class SynclavierRegenDetector extends AbstractDetector<EmptySettingsUI>
{
    private static final String    TIMBRE_MAGIC        = "SynclavierVirtualInstrumentTimbreVersion";
    private static final String    ENTRY               = "SynclavierPTPatchListEntry";
    private static final String    AMP_ENVELOPE_PREFIX = "SynclavierPTPIVEnv";
    private static final String    FILTER_PREFIX       = "SynclavierTBPINoteFilter";
    private static final String    CROSSFADE_PREFIX    = "SynclavierPTPIXF";
    private static final String    DYN_ENV_SOURCE      = "SynclavierTBPIDynEnvSrc";

    private static final int       DYN_SOURCE_VELOCITY = 10;

    private static final String    PAN_PARAM           = "SynclavierPTPIPan";
    // The partial pan is stored as an integer in the range [-63..63], 0 is centered
    private static final double    PAN_RANGE           = 63.0;

    // Per-partial gain and pitch (see the firmware parameter descriptor table). All add on top of
    // the per-patch-entry gain and tuning. Volume is a dB attenuation (-50..0), Tune is cents
    // (+-125), 'Tran' is semi-tones (+-24), Octave is a reference frequency (a coarse octave
    // transpose, 440 Hz = neutral)
    private static final String    VOLUME_PARAM        = "SynclavierPTPIVolume";
    private static final String    TUNE_PARAM          = "SynclavierPTPITune";
    private static final String    TRAN_PARAM          = "SynclavierPTPITran";
    private static final String    OCTAVE_PARAM        = "SynclavierPTPIOctave";
    private static final double    OCTAVE_REFERENCE_HZ = 440.0;
    private static final String    PARAGRAPH           = "¶";
    private static final String    SAMPLE_ENDING       = ".sflc";
    // The audio extensions to probe, the obfuscated SFLC first
    private static final String [] SAMPLE_ENDINGS      = new String []
    {
        SAMPLE_ENDING,
        ".flac",
        ".wav",
        ".aif",
        ".aiff"
    };


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SynclavierRegenDetector (final INotifier notifier)
    {
        super ("Synclavier Regen", "SynclavierRegen", notifier, EmptySettingsUI.INSTANCE, "-entry.txt");
    }


    /** {@inheritDoc} */
    @Override
    public Set<String> getFileEndings ()
    {
        // The detector scans for the timbre files (*-entry.txt, see the constructor) since these
        // hold the multi-sample mapping. For the format list the SFLC sample extension is shown
        // instead: it is the extension unique to the Synclavier and the one users recognize.
        return Set.of (SAMPLE_ENDING);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final List<String> lines = this.loadTextFile (file).lines ().toList ();
            final int magicIndex = findMagic (lines);
            if (magicIndex < 0)
                // Not a Synclavier timbre file, ignore it silently
                return Collections.emptyList ();

            return this.parseTimbre (file, lines, magicIndex);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Find the line which contains the timbre version tag.
     *
     * @param lines All lines of the file
     * @return The index of the version line or -1 if not present
     */
    private static int findMagic (final List<String> lines)
    {
        for (int i = 0; i < lines.size (); i++)
            if (lines.get (i).startsWith (TIMBRE_MAGIC))
                return i;
        return -1;
    }


    /**
     * Parses a timbre text file and creates a multi-sample from it. Each Synclavier <i>partial</i>
     * becomes a group (layer), each patch list entry becomes a sample zone.
     *
     * @param file The timbre file
     * @param lines All lines of the file
     * @param magicIndex The index of the version line; the title and comment are stored before it
     * @return The multi-sample source (a list with a single element) or an empty list
     * @throws IOException Could not read a sample file
     */
    private List<IMultisampleSource> parseTimbre (final File file, final List<String> lines, final int magicIndex) throws IOException
    {
        final File folder = file.getParentFile ();
        final Map<String, String> sampleIndex = readSampleIndex (folder);

        // The title is the first line, everything up to the version tag is the comment
        String title = magicIndex > 0 ? lines.get (0).trim () : "";
        if (title.isBlank ())
            title = FileUtils.getNameWithoutType (file);
        final StringBuilder commentBuilder = new StringBuilder ();
        for (int i = 1; i < magicIndex; i++)
        {
            if (!commentBuilder.isEmpty ())
                commentBuilder.append ('\n');
            commentBuilder.append (lines.get (i));
        }

        // Collect the patch list entries per partial. Note: the PTPIFile* scalar lines only mirror
        // the currently selected patch line in the editor and may be stale, so all zone data is
        // read exclusively from the patch list entries. In addition collect the per-partial
        // amplitude envelope (PTPIVEnv*) and the timbre-global note filter (TBPINoteFilter*).
        final Map<Integer, List<String []>> partials = new TreeMap<> ();
        final Map<Integer, Map<String, Double>> partialEnvelopes = new TreeMap<> ();
        final Map<Integer, Map<String, Double>> partialCrossfades = new TreeMap<> ();
        final Map<Integer, Double> partialPans = new TreeMap<> ();
        final Map<Integer, Double> partialVolumes = new TreeMap<> ();
        final Map<Integer, Double> partialTunes = new TreeMap<> ();
        final Map<Integer, Double> partialTrans = new TreeMap<> ();
        final Map<Integer, Double> partialOctaves = new TreeMap<> ();
        final Map<String, Double> filterParameters = new HashMap<> ();
        int dynamicSource = -1;
        for (int i = magicIndex + 1; i < lines.size (); i++)
        {
            final String line = lines.get (i).trim ();
            if (line.startsWith (ENTRY))
            {
                final String [] tokens = splitEntry (line);
                if (tokens.length > 0)
                {
                    final int partial = parseInt (tokens[0], -1);
                    if (partial >= 0)
                        partials.computeIfAbsent (Integer.valueOf (partial), _ -> new ArrayList<> ()).add (tokens);
                }
            }
            else if (line.startsWith (AMP_ENVELOPE_PREFIX))
                collectPartialParameter (partialEnvelopes, line, AMP_ENVELOPE_PREFIX);
            else if (line.startsWith (CROSSFADE_PREFIX))
                collectPartialParameter (partialCrossfades, line, CROSSFADE_PREFIX);
            else if (line.startsWith (PAN_PARAM))
                collectPartialScalar (partialPans, line);
            else if (line.startsWith (VOLUME_PARAM))
                collectPartialScalar (partialVolumes, line);
            // Note: the Tune / Tran checks must not swallow the stale FileTune editor mirror, which
            // is why the exact keywords (not a shared prefix) are matched
            else if (line.startsWith (TUNE_PARAM))
                collectPartialScalar (partialTunes, line);
            else if (line.startsWith (TRAN_PARAM))
                collectPartialScalar (partialTrans, line);
            else if (line.startsWith (OCTAVE_PARAM))
                collectPartialScalar (partialOctaves, line);
            else if (line.startsWith (DYN_ENV_SOURCE))
            {
                final String [] tokens = line.split ("\\s+");
                dynamicSource = parseInt (tokens[tokens.length - 1], -1);
            }
            else if (line.startsWith (FILTER_PREFIX))
                collectGlobalParameter (filterParameters, line, FILTER_PREFIX);
        }

        if (partials.isEmpty ())
        {
            this.notifier.logError ("IDS_SYNCLAVIER_NO_SAMPLES", file.getName ());
            return Collections.emptyList ();
        }

        final List<IGroup> groups = new ArrayList<> ();
        for (final Map.Entry<Integer, List<String []>> partialEntry: partials.entrySet ())
        {
            final DefaultGroup group = new DefaultGroup ("Partial " + (partialEntry.getKey ().intValue () + 1));
            final Map<String, Double> envelope = partialEnvelopes.get (partialEntry.getKey ());
            // The crossfade window is a velocity split only when the dynamic axis source is
            // velocity
            final Map<String, Double> crossfade = dynamicSource == DYN_SOURCE_VELOCITY ? partialCrossfades.get (partialEntry.getKey ()) : null;
            // Pan, volume and the pitch offset are per-partial settings and apply to all the zones.
            // The partial volume is a dB attenuation added to the zone gain; the partial Tune
            // (cents), Tran (semi-tones) and Octave (a reference frequency) add up to a semi-tone
            // offset on the tuning.
            final Double partialPan = partialPans.get (partialEntry.getKey ());
            final Double partialVolume = partialVolumes.get (partialEntry.getKey ());
            final double pitchOffset = partialPitchOffset (partialEntry.getKey (), partialTunes, partialTrans, partialOctaves);
            for (final String [] tokens: partialEntry.getValue ())
            {
                final Optional<ISampleZone> zoneOpt = this.createZone (folder, sampleIndex, tokens);
                if (zoneOpt.isPresent ())
                {
                    final ISampleZone zone = zoneOpt.get ();
                    applyAmplitudeEnvelope (zone, envelope);
                    if (crossfade != null)
                        applyVelocityWindow (zone, crossfade);
                    if (partialPan != null)
                        zone.setPanning (Math.clamp (partialPan.doubleValue () / PAN_RANGE, -1, 1));
                    if (partialVolume != null)
                        zone.setGain (zone.getGain () + partialVolume.doubleValue ());
                    if (pitchOffset != 0)
                        zone.setTuning (zone.getTuning () + pitchOffset);
                    final Optional<IFilter> zoneFilter = buildFilter (filterParameters);
                    if (zoneFilter.isPresent ())
                        zone.setFilter (zoneFilter.get ());
                    group.addSampleZone (zone);
                }
            }
            if (!group.getSampleZones ().isEmpty ())
                groups.add (group);
        }

        if (groups.isEmpty ())
            return Collections.emptyList ();

        final IMultisampleSource multisampleSource = this.createMultisampleSource (file, title, groups);
        // The note filter is timbre-global; some creators read it per-zone (set above), others read
        // the global filter of the multi-sample - so set it there as well
        final Optional<IFilter> globalFilter = buildFilter (filterParameters);
        if (globalFilter.isPresent ())
            multisampleSource.setGlobalFilter (globalFilter.get ());
        final IMetadata metadata = multisampleSource.getMetadata ();
        applyComment (metadata, commentBuilder.toString ());
        // The name of the containing folder is a good guess for the bank/library (= creator)
        if (folder != null)
            metadata.setCreator (folder.getName ());
        return Collections.singletonList (multisampleSource);
    }


    /**
     * Creates a sample zone from a patch list entry.
     *
     * @param folder The folder which contains the timbre and its samples
     * @param sampleIndex Maps a sample base name to its file name (from the bank index), may be
     *            empty
     * @param tokens The tokens of the patch list entry (see {@link #splitEntry(String)})
     * @return The created zone or null if the referenced sample could not be found
     * @throws IOException Could not read the sample file
     */
    private Optional<ISampleZone> createZone (final File folder, final Map<String, String> sampleIndex, final String [] tokens) throws IOException
    {
        final Optional<File> sampleFileOpt = resolveSample (folder, sampleIndex, tokens[16]);
        if (sampleFileOpt.isEmpty ())
        {
            this.notifier.logError ("IDS_SYNCLAVIER_SAMPLE_NOT_FOUND", tokens[16]);
            return Optional.empty ();
        }

        final File sampleFile = sampleFileOpt.get ();
        final ISampleData sampleData = this.openSampleData (sampleFile);
        final ISampleZone zone = new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), sampleData);

        zone.setKeyLow (parseInt (tokens[2], 0));
        zone.setKeyHigh (parseInt (tokens[3], 127));
        zone.setKeyRoot (parseInt (tokens[4], 60));

        // Field 5 is the volume as a fraction (0.5 is the neutral default): gain[dB] = 36 * f - 12.
        // Field 6 is the tuning as a fraction (0.5 is centered): tune[cents] = 250 * f - 125. The
        // patch list entry has no panning; panning is a per-partial setting (SynclavierPTPIPan) and
        // is applied to all zones of the partial by the caller.
        final double volumeFraction = parseDouble (tokens[5], 0.5);
        zone.setGain (36.0 * volumeFraction - 12.0);
        final double tuneFraction = parseDouble (tokens[6], 0.5);
        zone.setTuning ((250.0 * tuneFraction - 125.0) / 100.0);

        // Fields 7 to 10 are the start, end, loop start and loop end as a fraction of the total
        // length. Field 14 is the number of sample frames
        final int frames = parseInt (tokens[14], 0);
        zone.setStart ((int) Math.round (parseDouble (tokens[7], 0) * frames));
        zone.setStop ((int) Math.round (parseDouble (tokens[8], 1) * frames));

        // Field 11 are the loop bits: 0 = off, 1 = loop, 3 = loop + cross-fade, 4 = one-shot (no
        // loop), 5 = one-shot + loop
        final int loopBits = parseInt (tokens[11], 0);
        if (loopBits == 1 || loopBits == 3 || loopBits == 5)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType (LoopType.FORWARDS);
            loop.setStart ((int) Math.round (parseDouble (tokens[9], 0) * frames));
            loop.setEnd ((int) Math.round (parseDouble (tokens[10], 1) * frames));
            zone.addLoop (loop);
        }

        return Optional.of (zone);
    }


    /**
     * Creates the sample data for the resolved file. Obfuscated <i>.sflc</i> files are handled by
     * {@link SynclavierRegenSampleData}, plain WAV/FLAC/AIFF files (used by user libraries) are
     * handled by the standard sample data factory.
     *
     * @param sampleFile The resolved sample file
     * @return The sample data
     * @throws IOException Could not read the file
     */
    private ISampleData openSampleData (final File sampleFile) throws IOException
    {
        if (sampleFile.getName ().toLowerCase (Locale.US).endsWith (SAMPLE_ENDING))
            return new SynclavierRegenSampleData (sampleFile);
        return createSampleData (sampleFile, this.notifier);
    }


    /**
     * Resolve the referenced sample to a file on disk. The reference may contain a virtual folder
     * prefix. Commercial banks store the audio as an obfuscated <i>.sflc</i> file which uses the
     * referenced base name but ignores the referenced extension; user libraries reference plain
     * <i>.wav</i>/<i>.flac</i> files directly. Truncated names (ending with <i>~</i>) are resolved
     * via the bank index if present.
     *
     * @param folder The folder to search
     * @param sampleIndex The bank sample index (base name to file name)
     * @param reference The referenced sample path
     * @return The resolved file or null if it does not exist
     */
    private static Optional<File> resolveSample (final File folder, final Map<String, String> sampleIndex, final String reference)
    {
        String name = reference.replace ('\\', '/');
        final int slash = name.lastIndexOf ('/');
        if (slash >= 0)
            name = name.substring (slash + 1);

        // The exact referenced file (a plain WAV/FLAC in a user library)
        final File exact = new File (folder, name);
        if (exact.exists ())
            return Optional.of (exact);

        String base = name;
        final int dot = base.lastIndexOf ('.');
        if (dot >= 0)
            base = base.substring (0, dot);
        final boolean truncated = base.endsWith ("~");
        if (truncated)
            base = base.substring (0, base.length () - 1);

        // Try the known audio extensions, the obfuscated SFLC first
        for (final String ending: SAMPLE_ENDINGS)
        {
            final File candidate = new File (folder, base + ending);
            if (candidate.exists ())
                return Optional.of (candidate);
        }

        // Consult the bank index (handles truncated names and alternative extensions)
        final String indexed = sampleIndex.get (base.toLowerCase (Locale.US));
        if (indexed != null)
        {
            final File indexedFile = new File (folder, indexed);
            if (indexedFile.exists ())
                return Optional.of (indexedFile);
        }
        if (truncated)
            for (final Map.Entry<String, String> e: sampleIndex.entrySet ())
                if (e.getKey ().startsWith (base.toLowerCase (Locale.US)))
                {
                    final File candidate = new File (folder, e.getValue ());
                    if (candidate.exists ())
                        return Optional.of (candidate);
                }

        // Case-insensitive search in the folder
        final File [] children = folder.listFiles ();
        if (children != null)
            for (final File child: children)
            {
                final String childLower = child.getName ().toLowerCase (Locale.US);
                for (final String ending: SAMPLE_ENDINGS)
                    if (childLower.equals ((base + ending).toLowerCase (Locale.US)))
                        return Optional.of (child);
            }
        return Optional.empty ();
    }


    /**
     * Reads the bank sample index TSV file (a file starting with an underscore and having a
     * <i>mFilenameNoExt</i> column) if present. It maps the lower-cased base name to the full file
     * name (base name plus the extension from the <i>mExtension</i> column).
     *
     * @param folder The folder to look in
     * @return The map, empty if there is no index
     */
    private static Map<String, String> readSampleIndex (final File folder)
    {
        final Map<String, String> index = new java.util.HashMap<> ();
        final File [] children = folder == null ? null : folder.listFiles ();
        if (children == null)
            return index;
        for (final File child: children)
        {
            final String name = child.getName ();
            if (!name.startsWith ("_") || !name.toLowerCase (Locale.US).endsWith (".tsv"))
                continue;
            try
            {
                final List<String> lines = java.nio.file.Files.readAllLines (child.toPath ());
                if (lines.isEmpty ())
                    continue;
                final String [] header = lines.get (0).split ("\t", -1);
                int nameColumn = -1;
                int extColumn = -1;
                for (int c = 0; c < header.length; c++)
                    if ("mFilenameNoExt".equals (header[c]))
                        nameColumn = c;
                    else if ("mExtension".equals (header[c]))
                        extColumn = c;
                if (nameColumn < 0)
                    continue;
                for (int r = 1; r < lines.size (); r++)
                {
                    final String [] columns = lines.get (r).split ("\t", -1);
                    if (columns.length <= nameColumn || columns[nameColumn].isBlank ())
                        continue;
                    final String ext = extColumn >= 0 && extColumn < columns.length && !columns[extColumn].isBlank () ? columns[extColumn] : SAMPLE_ENDING;
                    index.put (columns[nameColumn].toLowerCase (Locale.US), columns[nameColumn] + ext);
                }
            }
            catch (final IOException _)
            {
                // Ignore an unreadable index
            }
        }
        return index;
    }


    /**
     * Splits a patch list entry line into its 16 leading tokens plus the sample path. The path may
     * contain spaces, therefore it is treated as the remainder of the line after the sample rate.
     *
     * @param line The line to split
     * @return The 17 tokens (0-15 are the numeric fields, 16 is the path) or null if the line is
     *         malformed
     */
    private static String [] splitEntry (final String line)
    {
        // Keyword partial entry keyLow keyHigh root gain pan start end loopStart loopEnd loopMode
        // flag channels frames sampleRate path...
        final String rest = line.substring (ENTRY.length ()).trim ();
        final String [] parts = rest.split ("\\s+", 17);
        if (parts.length < 17)
            return new String [0];
        return parts;
    }


    private static void applyComment (final IMetadata metadata, final String comment)
    {
        if (comment == null || comment.isBlank ())
            return;

        final List<String> keywords = new ArrayList<> ();
        final StringBuilder description = new StringBuilder ();
        for (final String token: comment.replace (PARAGRAPH, "\n").split ("\\s+"))
            if (token.startsWith ("#") && token.length () > 1)
                keywords.add (token.substring (1));
            else
            {
                if (!description.isEmpty ())
                    description.append (' ');
                description.append (token);
            }

        metadata.setDescription (description.toString ().trim ());
        if (!keywords.isEmpty ())
        {
            metadata.setKeywords (keywords.toArray (new String [keywords.size ()]));
            metadata.setCategory (keywords.get (0));
        }
    }


    /**
     * Collects a per-partial parameter line (e.g. an amplitude envelope stage) into a map keyed by
     * the partial index and the parameter name (the keyword suffix after the prefix).
     *
     * @param map The target map
     * @param line The line to parse
     * @param prefix The keyword prefix which is stripped to form the parameter name
     */
    private static void collectPartialParameter (final Map<Integer, Map<String, Double>> map, final String line, final String prefix)
    {
        final String [] tokens = line.split ("\\s+");
        if (tokens.length < 5)
            return;
        final int partial = parseInt (tokens[1], -1);
        if (partial < 0)
            return;
        final String key = tokens[0].substring (prefix.length ());
        map.computeIfAbsent (Integer.valueOf (partial), _ -> new HashMap<> ()).put (key, Double.valueOf (parseDouble (tokens[tokens.length - 1], 0)));
    }


    /**
     * Collects a per-partial scalar parameter line (e.g. pan or volume) into a map keyed by the
     * partial index. The line format is "&lt;keyword&gt; &lt;partial&gt; 0 0 &lt;value&gt;".
     *
     * @param map The target map
     * @param line The line to parse
     */
    private static void collectPartialScalar (final Map<Integer, Double> map, final String line)
    {
        final String [] tokens = line.split ("\\s+");
        if (tokens.length < 5)
            return;
        final int partial = parseInt (tokens[1], -1);
        if (partial >= 0)
            map.put (Integer.valueOf (partial), Double.valueOf (parseDouble (tokens[tokens.length - 1], 0)));
    }


    /**
     * Computes the additional tuning of a partial in semitones from its Tune (cents), Tran
     * (semitones) and Octave (a reference frequency where 440 Hz is neutral) parameters. All three
     * add up on top of the note and per-patch-entry tuning (the firmware pitch engine computes
     * frequency = 440 * 2^(sum of semitones / 12)).
     *
     * @param partial The partial index
     * @param tunes The collected Tune parameters (cents)
     * @param trans The collected Tran parameters (semitones)
     * @param octaves The collected Octave parameters (reference frequency in Hz)
     * @return The additional tuning in semitones (0 if the partial has none)
     */
    private static double partialPitchOffset (final Integer partial, final Map<Integer, Double> tunes, final Map<Integer, Double> trans, final Map<Integer, Double> octaves)
    {
        double semitones = 0;
        final Double tran = trans.get (partial);
        if (tran != null)
            semitones += tran.doubleValue ();
        final Double tune = tunes.get (partial);
        if (tune != null)
            semitones += tune.doubleValue () / 100.0;
        final Double octave = octaves.get (partial);
        if (octave != null && octave.doubleValue () > 0)
            semitones += 12.0 * Math.log (octave.doubleValue () / OCTAVE_REFERENCE_HZ) / Math.log (2);
        return semitones;
    }


    /**
     * Collects a timbre-global parameter line (e.g. a note filter setting) into a map keyed by the
     * parameter name (the keyword suffix after the prefix).
     *
     * @param map The target map
     * @param line The line to parse
     * @param prefix The keyword prefix which is stripped to form the parameter name
     */
    private static void collectGlobalParameter (final Map<String, Double> map, final String line, final String prefix)
    {
        final String [] tokens = line.split ("\\s+");
        if (tokens.length < 5)
            return;
        map.put (tokens[0].substring (prefix.length ()), Double.valueOf (parseDouble (tokens[tokens.length - 1], 0)));
    }


    /**
     * Applies the Synclavier amplitude envelope of a partial to a zone. The Synclavier envelope has
     * a Delay, an Attack to the Peak level, an Initial Decay to the Sustain level and a Final Decay
     * (= release), all times in seconds and the levels in percent.
     *
     * @param zone The zone to update
     * @param envelope The envelope parameters (may be null)
     */
    private static void applyAmplitudeEnvelope (final ISampleZone zone, final Map<String, Double> envelope)
    {
        if (envelope == null || envelope.isEmpty ())
            return;

        final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        setTime (envelope, "Delay", amplitudeEnvelope::setDelayTime);
        setTime (envelope, "Attack", amplitudeEnvelope::setAttackTime);
        setTime (envelope, "IDecay", amplitudeEnvelope::setDecayTime);
        setTime (envelope, "FDecay", amplitudeEnvelope::setReleaseTime);
        final Double peak = envelope.get ("Peak");
        if (peak != null)
            amplitudeEnvelope.setHoldLevel (Math.clamp (peak.doubleValue () / 100.0, 0, 1));
        final Double sustain = envelope.get ("Sust");
        if (sustain != null)
            amplitudeEnvelope.setSustainLevel (Math.clamp (sustain.doubleValue () / 100.0, 0, 1));
    }


    /**
     * Builds an {@link IFilter} from the timbre-global note filter parameters. The Synclavier note
     * filter is a low-pass filter (with a 2- or 4-pole slope) with a cutoff (a fraction), a
     * resonance (0..1), a filter envelope (attack, decay, release with a peak depth) and keyboard
     * tracking. A fresh instance is returned on every call so it can be assigned to several zones.
     *
     * @param filterParameters The filter parameters (empty if the timbre has no filter)
     * @return The filter or null if the timbre has no note filter
     */
    private static Optional<IFilter> buildFilter (final Map<String, Double> filterParameters)
    {
        final Double cutoff = filterParameters.get ("Cutoff");
        if (cutoff == null)
            return Optional.empty ();

        // The note filter is multi-mode; the type index selects mode and slope (value = index /
        // 255):
        // 1 = LP 12dB, 2 = HP 12dB, 3 = BP 12dB, 4 = LP 24dB, 5 = HP 24dB, 6 = BP 24dB
        final Double type = filterParameters.get ("Type");
        final int typeIndex = type == null ? 1 : (int) Math.round (type.doubleValue () * 255.0);
        final int poles = typeIndex >= 4 ? 4 : 2;
        final FilterType filterType = switch (typeIndex)
        {
            case 2, 5 -> FilterType.HIGH_PASS;
            case 3, 6 -> FilterType.BAND_PASS;
            default -> FilterType.LOW_PASS;
        };
        final double resonance = filterParameters.getOrDefault ("Resonance", Double.valueOf (0)).doubleValue ();
        final IFilter filter = new DefaultFilter (filterType, poles, MathUtils.denormalizeCutoff (Math.clamp (cutoff.doubleValue (), 0, 1)), Math.clamp (resonance, 0, 1));

        // Filter envelope: the times are in seconds, the depth is approximated from the peak delta
        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
        final IEnvelope filterEnvelope = cutoffModulator.getSource ();
        setTime (filterParameters, "Attack", filterEnvelope::setAttackTime);
        setTime (filterParameters, "Decay", filterEnvelope::setDecayTime);
        setTime (filterParameters, "Release", filterEnvelope::setReleaseTime);
        final Double peakDelta = filterParameters.get ("PeakDelta");
        if (peakDelta != null)
            cutoffModulator.setDepth (Math.clamp (peakDelta.doubleValue () / 10.0, -1, 1));

        final Double pitchTrack = filterParameters.get ("PitchTrack");
        if (pitchTrack != null)
            filter.setCutoffKeyTracking (Math.clamp (pitchTrack.doubleValue (), 0, 1));

        return Optional.of (filter);
    }


    /**
     * Applies a per-partial crossfade window (XFStart/In/Out/End) to a zone as its velocity range
     * and cross-fades. Only called when the dynamic axis source is velocity, so the partials form
     * velocity layers.
     *
     * @param zone The zone to update
     * @param crossfade The crossfade window parameters (may be null)
     */
    private static void applyVelocityWindow (final ISampleZone zone, final Map<String, Double> crossfade)
    {
        if (crossfade == null)
            return;
        final Double start = crossfade.get ("Start");
        final Double end = crossfade.get ("End");
        if (start == null && end == null)
            return;

        final int low = start == null ? 0 : (int) Math.round (start.doubleValue ());
        final int high = end == null ? 127 : (int) Math.round (end.doubleValue ());
        zone.setVelocityLow (Math.clamp (low, 0, 127));
        zone.setVelocityHigh (Math.clamp (high, 0, 127));
        final Double in = crossfade.get ("In");
        if (in != null)
            zone.setVelocityCrossfadeLow (Math.max (0, (int) Math.round (in.doubleValue ()) - low));
        final Double out = crossfade.get ("Out");
        if (out != null)
            zone.setVelocityCrossfadeHigh (Math.max (0, high - (int) Math.round (out.doubleValue ())));
    }


    private static void setTime (final Map<String, Double> map, final String key, final DoubleConsumer setter)
    {
        final Double value = map.get (key);
        if (value != null)
            setter.accept (Math.max (0, value.doubleValue ()));
    }


    private static int parseInt (final String text, final int defaultValue)
    {
        final String trimmed = text.trim ();
        try
        {
            return Integer.parseInt (trimmed);
        }
        catch (final NumberFormatException _)
        {
            // Older timbre generations write the integer fields as floats (e.g. "24.000000")
            try
            {
                return (int) Math.round (Double.parseDouble (trimmed));
            }
            catch (final NumberFormatException _)
            {
                return defaultValue;
            }
        }
    }


    private static double parseDouble (final String text, final double defaultValue)
    {
        try
        {
            return Double.parseDouble (text.trim ());
        }
        catch (final NumberFormatException _)
        {
            return defaultValue;
        }
    }
}
