// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synclavier;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.sound.sampled.UnsupportedAudioFileException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;


/**
 * Creator for Synclavier Regen timbres. A timbre is written as a library folder which contains the
 * timbre text file (<i>NN-Entry.txt</i>), the referenced samples as obfuscated FLAC files
 * (<i>.sflc</i>, see {@link SynclavierRegenCodec}) and the two bank index files. A partial (=
 * group) becomes a Synclavier partial, a sample zone becomes a patch list entry.
 *
 * @author Jürgen Moßgraber
 */
public class SynclavierRegenCreator extends AbstractCreator<EmptySettingsUI>
{
    private static final String              STRINGS                = "strings";
    private static final String              PERCUSSION             = "percussion";

    private static final String              DOUBLE_ZERO            = " 0 0 ";
    private static final String              LINE_FEED              = "\n";
    private static final String              FILTER_PREFIX          = "SynclavierTBPINoteFilter";
    private static final String              AMP_ENVELOPE_PREFIX    = "SynclavierPTPIVEnv";
    private static final String              PAN_PARAM              = "SynclavierPTPIPan";
    /** The partial pan is stored as an integer in the range [-63..63], 0 is centered. */
    private static final double              PAN_RANGE              = 63.0;
    private static final String              TIMBRE_VERSION         = "SynclavierVirtualInstrumentTimbreVersion000 7 12 24 4063 99 14";
    private static final String              SAMPLE_INDEX           = "mFilenameNoExt\tmTitle\tmComment\tmSampleRate\tmFrameLength\tmChannels\tmMIDIKey\tmFileHz\tmPitchTrack\tmMarkStart\tmTotalLen\tmLoopLen\tmLoopXfade\tmLoopBits\tmMedia\tmExtension\tmDLMLSB";
    private static final String              TIMBRE_INDEX           = "bankEntry\ttimbreName\ttimbreInfo\ttimbrePartials\ttimbreDLMLSB";
    /**
     * The Regen addresses the timbres of a library as 8 banks of 8, so a library holds 64 timbres.
     */
    private static final int                 MAX_TIMBRES            = 64;
    /** A Synclavier timbre has at most 12 partials, so at most 12 velocity layers. */
    private static final int                 MAX_PARTIALS           = 12;
    /** The Regen displays at most 220 characters of a library's Description.txt. */
    private static final int                 MAX_DESCRIPTION_LENGTH = 220;

    /**
     * Maps the ConvertWithMoss categories to the Regen's canonical category tags (see the Regen
     * manual, section 3.2 - Tags). Categories without a clear Regen equivalent are not listed and
     * fall back to the sanitized category name, so a category is always written as at least a
     * custom tag.
     */
    private static final Map<String, String> REGEN_CATEGORY_TAGS    = new HashMap<> ();
    static
    {
        REGEN_CATEGORY_TAGS.put ("bass", "bass");
        REGEN_CATEGORY_TAGS.put ("bell", "chime");
        REGEN_CATEGORY_TAGS.put ("brass", "brass");
        REGEN_CATEGORY_TAGS.put ("vocal", "vox");
        REGEN_CATEGORY_TAGS.put ("chromatic percussion", "chime");
        REGEN_CATEGORY_TAGS.put ("drone", "drone");
        REGEN_CATEGORY_TAGS.put ("drum", PERCUSSION);
        REGEN_CATEGORY_TAGS.put ("acoustic drum", PERCUSSION);
        REGEN_CATEGORY_TAGS.put ("clap", PERCUSSION);
        REGEN_CATEGORY_TAGS.put ("hi-hat", PERCUSSION);
        REGEN_CATEGORY_TAGS.put ("kick", PERCUSSION);
        REGEN_CATEGORY_TAGS.put ("snare", PERCUSSION);
        REGEN_CATEGORY_TAGS.put (PERCUSSION, PERCUSSION);
        REGEN_CATEGORY_TAGS.put ("ensemble", STRINGS);
        REGEN_CATEGORY_TAGS.put (STRINGS, STRINGS);
        REGEN_CATEGORY_TAGS.put ("fx", "sfx");
        REGEN_CATEGORY_TAGS.put ("destruction", "sfx");
        REGEN_CATEGORY_TAGS.put ("keyboard", "keys");
        REGEN_CATEGORY_TAGS.put ("piano", "keys");
        REGEN_CATEGORY_TAGS.put ("lead", "lead");
        REGEN_CATEGORY_TAGS.put ("monosynth", "lead");
        REGEN_CATEGORY_TAGS.put ("orchestral", "orchestral");
        REGEN_CATEGORY_TAGS.put ("organ", "organ");
        REGEN_CATEGORY_TAGS.put ("pad", "pad");
        REGEN_CATEGORY_TAGS.put ("pipe", "wind");
        REGEN_CATEGORY_TAGS.put ("winds", "wind");
        REGEN_CATEGORY_TAGS.put ("loops", "beatloop");
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SynclavierRegenCreator (final INotifier notifier)
    {
        super ("Synclavier Regen", "SynclavierRegen", notifier, EmptySettingsUI.INSTANCE);
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPresetLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.writeLibrary (destinationFolder, multisampleSource.getName (), Collections.singletonList (multisampleSource));
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        final int total = multisampleSources.size ();
        if (total <= MAX_TIMBRES)
        {
            this.writeLibrary (destinationFolder, libraryName, multisampleSources);
            return;
        }

        // A Regen library holds at most 64 timbres (8 banks of 8), so a larger set is written as
        // several numbered libraries.
        final int parts = (total + MAX_TIMBRES - 1) / MAX_TIMBRES;
        this.notifier.log ("IDS_SYNCLAVIER_LIBRARY_SPLIT", Integer.toString (total), Integer.toString (parts));
        for (int part = 0; part < parts; part++)
        {
            final int from = part * MAX_TIMBRES;
            final int to = Math.min (from + MAX_TIMBRES, total);
            this.writeLibrary (destinationFolder, libraryName + " " + (part + 1), multisampleSources.subList (from, to));
        }
    }


    /**
     * Writes a Synclavier Regen library folder for the given multi-samples.
     *
     * @param destinationFolder The folder into which the library folder is created
     * @param libraryName The name of the library
     * @param multisampleSources The multi-samples to write (each becomes one timbre)
     * @throws IOException Could not write a file
     */
    private void writeLibrary (final File destinationFolder, final String libraryName, final List<IMultisampleSource> multisampleSources) throws IOException
    {
        final String safeLibraryName = createSafeFilename (libraryName);
        final File libraryFolder = createUniqueFolder (destinationFolder, safeLibraryName);
        this.notifier.log ("IDS_NOTIFY_STORING", libraryFolder.getAbsolutePath ());

        final Set<String> usedSampleNames = new HashSet<> ();
        // Maps a sample identity (name plus audio format) to the already written file base name, so
        // a sample which is used by several partials or timbres is stored only once - just like the
        // original banks share their samples
        final Map<String, String> writtenSamples = new HashMap<> ();
        final StringBuilder sampleIndex = new StringBuilder (SAMPLE_INDEX).append (LINE_FEED);
        final StringBuilder timbreIndex = new StringBuilder (TIMBRE_INDEX).append (LINE_FEED);

        for (int bankEntry = 0; bankEntry < multisampleSources.size (); bankEntry++)
        {
            final IMultisampleSource source = multisampleSources.get (bankEntry);
            final StringBuilder timbre = new StringBuilder ();

            final IMetadata metadata = source.getMetadata ();
            final String comment = flattenComment (metadata);
            timbre.append (source.getName ()).append (LINE_FEED);
            timbre.append (comment).append (LINE_FEED);
            timbre.append (TIMBRE_VERSION).append (LINE_FEED);

            int partialMask = 0;
            final List<IGroup> allGroups = source.getNonEmptyGroups (false);
            // A timbre has at most 12 partials (= 12 velocity layers)
            if (allGroups.size () > MAX_PARTIALS)
                this.notifier.logError ("IDS_SYNCLAVIER_PARTIAL_CAP", source.getName (), Integer.toString (allGroups.size ()));
            final List<IGroup> groups = allGroups.size () > MAX_PARTIALS ? allGroups.subList (0, MAX_PARTIALS) : allGroups;
            appendFilter (timbre, source, groups);
            // Velocity layers are put on separate partials selected by the crossfade (dynamic) axis
            final boolean velocityLayered = isVelocityLayered (groups);
            if (velocityLayered)
                timbre.append ("SynclavierTBPIDynEnvSrc 0 0 0 10").append (LINE_FEED);
            for (int partial = 0; partial < groups.size (); partial++)
            {
                final List<ISampleZone> zones = groups.get (partial).getSampleZones ();
                // Volume and tuning are split into a shared per-partial part and a per-zone
                // remainder:
                // the patch entry gain field only reaches down to -12 dB and the tuning field only
                // spans
                // +-125 cents, so a deeper attenuation goes into the per-partial volume and whole
                // semitones go into the per-partial transpose (Tran), leaving the fine rest in the
                // entry.
                final double partialVolume = partialVolume (zones);
                final int partialTranspose = partialTranspose (zones);
                for (int entry = 0; entry < zones.size (); entry++)
                {
                    final ISampleZone zone = zones.get (entry);
                    final Optional<ISampleData> sampleData = zone.getSampleData ();
                    if (sampleData.isEmpty ())
                        throw new IOException ("Empty sample data in zone: " + zone.getName ());
                    final IAudioMetadata audioMetadata = sampleData.get ().getAudioMetadata ();

                    final String identity = zone.getName () + "|" + audioMetadata.getNumberOfSamples () + "|" + audioMetadata.getSampleRate () + "|" + audioMetadata.getChannels ();
                    String sampleName = writtenSamples.get (identity);
                    if (sampleName == null)
                    {
                        sampleName = uniqueSampleName (usedSampleNames, zone.getName ());
                        writeSample (libraryFolder, sampleName, zone);
                        sampleIndex.append (sampleRow (sampleName, audioMetadata)).append (LINE_FEED);
                        writtenSamples.put (identity, sampleName);
                    }
                    timbre.append (patchEntry (partial, entry, zone, audioMetadata, safeLibraryName, sampleName, partialVolume, partialTranspose)).append (LINE_FEED);
                }
                // The volume line also marks the partial as active (required for the partial
                // bit-mask)
                timbre.append ("SynclavierPTPIVolume ").append (partial).append (DOUBLE_ZERO).append (number (partialVolume)).append (LINE_FEED);
                timbre.append ("SynclavierPTPISynthMode ").append (partial).append (" 0 0 2").append (LINE_FEED);
                if (partialTranspose != 0)
                    timbre.append ("SynclavierPTPITran ").append (partial).append (DOUBLE_ZERO).append (number (partialTranspose)).append (LINE_FEED);
                // The pan is a per-partial setting, so use the average pan of the partial's zones
                final int pan = partialPan (zones);
                if (pan != 0)
                    timbre.append (PAN_PARAM).append (' ').append (partial).append (DOUBLE_ZERO).append (number (pan)).append (LINE_FEED);
                if (!zones.isEmpty ())
                    appendAmplitudeEnvelope (timbre, partial, zones.get (0));
                if (velocityLayered)
                    appendVelocityWindow (timbre, partial, zones);
                partialMask |= 1 << partial;
            }

            final String fileName = timbreFileName (bankEntry);
            try (final FileWriter writer = new FileWriter (new File (libraryFolder, fileName), StandardCharsets.UTF_8))
            {
                writer.write (timbre.toString ());
            }

            timbreIndex.append (bankEntry).append ('\t').append (source.getName ()).append ('\t').append (comment).append ('\t').append (partialMask).append ("\t0").append (LINE_FEED);
        }

        writeTextFile (new File (libraryFolder, "_" + safeLibraryName + ".tsv"), sampleIndex.toString ());
        writeTextFile (new File (libraryFolder, "_TimbreIndex.tsv"), timbreIndex.toString ());
        // The library description is the combined (unique) descriptions of its timbres, exactly as
        // the
        // SF2 creator forms its library comment, but capped to the Regen's 220 character display
        // limit.
        // When none of the timbres has a description, no Description.txt is written and the device
        // shows
        // its own default.
        final Set<String> descriptions = new LinkedHashSet<> ();
        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            final String description = multisampleSource.getMetadata ().getDescription ();
            if (description != null && !description.isBlank ())
                descriptions.add (description.strip ());
        }
        final String libraryDescription = combineDescriptions (descriptions);
        if (!libraryDescription.isEmpty ())
            writeTextFile (new File (libraryFolder, "Description.txt"), libraryDescription + LINE_FEED);

        this.progress.notifyDone ();
    }


    /**
     * Appends the timbre-global note filter. The filter is taken from the source's global filter
     * (as set by e.g. the EXS24 detector) or, if there is none, from the first zone that carries a
     * filter (as set by e.g. the SFZ detector). This is the inverse of the filter mapping in
     * {@link SynclavierRegenDetector}.
     *
     * @param timbre The timbre text to append to
     * @param source The multi-sample source (checked for a global filter)
     * @param groups The partials (groups) of the timbre (checked for a per-zone filter)
     */
    private static void appendFilter (final StringBuilder timbre, final IMultisampleSource source, final List<IGroup> groups)
    {
        IFilter filter = source.getGlobalFilter ().orElse (null);
        if (filter == null)
            for (final IGroup group: groups)
            {
                for (final ISampleZone zone: group.getSampleZones ())
                {
                    final Optional<IFilter> optFilter = zone.getFilter ();
                    if (optFilter.isPresent ())
                    {
                        filter = optFilter.get ();
                        break;
                    }
                }
                if (filter != null)
                    break;
            }
        if (filter == null)
            return;

        appendGlobal (timbre, "Type", noteFilterTypeIndex (filter) / 255.0);
        appendGlobal (timbre, "Cutoff", MathUtils.normalizeCutoff (filter.getCutoff ()));
        appendGlobal (timbre, "Resonance", Math.clamp (filter.getResonance (), 0, 1));
        appendGlobal (timbre, "PitchTrack", filter.getCutoffKeyTracking ());

        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
        final double depth = cutoffModulator.getDepth ();
        if (!Double.isNaN (depth) && depth != 0)
            appendGlobal (timbre, "PeakDelta", Math.clamp (depth * 10.0, -10, 10));
        final IEnvelope filterEnvelope = cutoffModulator.getSource ();
        appendGlobalTime (timbre, "Attack", filterEnvelope.getAttackTime ());
        appendGlobalTime (timbre, "Decay", filterEnvelope.getDecayTime ());
        appendGlobalTime (timbre, "Release", filterEnvelope.getReleaseTime ());
    }


    /**
     * Appends the per-partial amplitude envelope (Delay, Attack to Peak, Initial Decay to Sustain,
     * Final Decay = release), the inverse of the amplitude envelope mapping in the detector.
     *
     * @param timbre The timbre text to append to
     * @param partial The partial index
     * @param zone The first zone of the partial (its envelope is used for the partial)
     */
    private static void appendAmplitudeEnvelope (final StringBuilder timbre, final int partial, final ISampleZone zone)
    {
        final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        appendPartialTime (timbre, partial, "Delay", envelope.getDelayTime ());
        appendPartialTime (timbre, partial, "Attack", envelope.getAttackTime ());
        appendPartialTime (timbre, partial, "IDecay", envelope.getDecayTime ());
        appendPartialTime (timbre, partial, "FDecay", envelope.getReleaseTime ());
        final double peak = envelope.getHoldLevel ();
        appendPartial (timbre, partial, "Peak", peak < 0 ? 100.0 : Math.clamp (peak * 100.0, 0, 100));
        final double sustain = envelope.getSustainLevel ();
        appendPartial (timbre, partial, "Sust", sustain < 0 ? 100.0 : Math.clamp (sustain * 100.0, 0, 100));
    }


    /**
     * Checks whether the groups form velocity layers, i.e. at least one zone is restricted to a
     * velocity sub-range.
     *
     * @param groups The partials (groups)
     * @return True if a velocity split is present
     */
    private static boolean isVelocityLayered (final List<IGroup> groups)
    {
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
                if (zone.getVelocityLow () > 0 || zone.getVelocityHigh () < 127)
                    return true;
        return false;
    }


    /**
     * Appends the per-partial crossfade window (XFStart/In/Out/End) that selects the partial by
     * velocity (the dynamic axis source is set to velocity for the whole timbre). The window comes
     * from the velocity range of the partial's zones, the fade regions from the velocity
     * cross-fades.
     *
     * @param timbre The timbre text to append to
     * @param partial The partial index
     * @param zones The zones of the partial
     */
    private static void appendVelocityWindow (final StringBuilder timbre, final int partial, final List<ISampleZone> zones)
    {
        int low = 127;
        int high = 0;
        int fadeIn = 0;
        int fadeOut = 0;
        for (final ISampleZone zone: zones)
        {
            low = Math.min (low, zone.getVelocityLow ());
            high = Math.max (high, zone.getVelocityHigh ());
            fadeIn = Math.max (fadeIn, zone.getVelocityCrossfadeLow ());
            fadeOut = Math.max (fadeOut, zone.getVelocityCrossfadeHigh ());
        }
        if (low > high)
        {
            low = 0;
            high = 127;
        }
        final int xfIn = Math.min (high, low + fadeIn);
        final int xfOut = Math.max (xfIn, high - fadeOut);
        timbre.append ("SynclavierPTPIXFStart ").append (partial).append (DOUBLE_ZERO).append (low).append (LINE_FEED);
        timbre.append ("SynclavierPTPIXFIn ").append (partial).append (DOUBLE_ZERO).append (xfIn).append (LINE_FEED);
        timbre.append ("SynclavierPTPIXFOut ").append (partial).append (DOUBLE_ZERO).append (xfOut).append (LINE_FEED);
        timbre.append ("SynclavierPTPIXFEnd ").append (partial).append (DOUBLE_ZERO).append (high).append (LINE_FEED);
    }


    /**
     * The Synclavier note-filter type index for a filter. The Regen note filter is multi-mode; the
     * type field selects one of six variants (value = index / 255): 1 = Low Pass 12 dB, 2 = High
     * Pass 12 dB, 3 = Band Pass 12 dB, 4 = Low Pass 24 dB, 5 = High Pass 24 dB, 6 = Band Pass 24 dB
     * (12 dB = 2-pole, 24 dB = 4-pole; recovered from the device firmware). A notch/band-rejection
     * filter has no Regen equivalent and falls back to low-pass.
     *
     * @param filter The filter
     * @return The type index 1..6
     */
    private static int noteFilterTypeIndex (final IFilter filter)
    {
        final int mode = switch (filter.getType ())
        {
            case HIGH_PASS -> 2;
            case BAND_PASS -> 3;
            default -> 1;
        };
        return mode + (filter.getPoles () >= 4 ? 3 : 0);
    }


    private static void appendGlobal (final StringBuilder timbre, final String key, final double value)
    {
        timbre.append (FILTER_PREFIX).append (key).append (" 0 0 0 ").append (number (value)).append (LINE_FEED);
    }


    private static void appendGlobalTime (final StringBuilder timbre, final String key, final double time)
    {
        if (time >= 0)
            appendGlobal (timbre, key, Math.clamp (time, 0, 30));
    }


    private static void appendPartial (final StringBuilder timbre, final int partial, final String key, final double value)
    {
        timbre.append (AMP_ENVELOPE_PREFIX).append (key).append (' ').append (partial).append (DOUBLE_ZERO).append (number (value)).append (LINE_FEED);
    }


    private static void appendPartialTime (final StringBuilder timbre, final int partial, final String key, final double time)
    {
        if (time >= 0)
            appendPartial (timbre, partial, key, Math.clamp (time, 0, 30));
    }


    /**
     * Computes the representative pan of a partial from the average panning of its zones. The
     * generic model stores panning per zone while a Synclavier partial has a single pan setting, so
     * the average of the partial's zones is used.
     *
     * @param zones The zones of the partial
     * @return The pan in the range [-63..63], 0 if centered
     */
    private static int partialPan (final List<ISampleZone> zones)
    {
        if (zones.isEmpty ())
            return 0;
        double sum = 0;
        for (final ISampleZone zone: zones)
            sum += zone.getPanning ();
        return (int) Math.round (Math.clamp (sum / zones.size (), -1, 1) * PAN_RANGE);
    }


    /**
     * Computes the shared per-partial volume (a dB attenuation) so that each zone's remaining gain
     * fits the patch entry gain field, whose floor is -12 dB. It is how far the quietest zone would
     * underflow that floor, clamped to the Regen partial-volume range of -50..0 dB. 0 for the
     * common case where no zone is below -12 dB.
     *
     * @param zones The zones of the partial
     * @return The per-partial volume in dB
     */
    private static double partialVolume (final List<ISampleZone> zones)
    {
        if (zones.isEmpty ())
            return 0;
        double minGain = Double.MAX_VALUE;
        for (final ISampleZone zone: zones)
            minGain = Math.min (minGain, zone.getGain ());
        return Math.clamp (Math.min (0, minGain + 12.0), -50, 0);
    }


    /**
     * Computes the shared per-partial transpose (whole semitones) from the average tuning of the
     * partial's zones. Only used when the tuning exceeds what the per-zone patch entry tuning field
     * can hold (+-125 cents); the per-zone fine remainder stays in the entry. Clamped to the Regen
     * range of -24..+24 semitones. 0 for the common case of a fine tuning.
     *
     * @param zones The zones of the partial
     * @return The per-partial transpose in semitones
     */
    private static int partialTranspose (final List<ISampleZone> zones)
    {
        if (zones.isEmpty ())
            return 0;
        double sum = 0;
        for (final ISampleZone zone: zones)
            sum += zone.getTuning ();
        final double average = sum / zones.size ();
        if (Math.abs (average) <= 1.25)
            return 0;
        return Math.clamp ((int) Math.round (average), -24, 24);
    }


    private static String number (final double value)
    {
        if (value == Math.rint (value) && !Double.isInfinite (value))
            return String.format (Locale.US, "%.1f", Double.valueOf (value));
        return String.format (Locale.US, "%.4f", Double.valueOf (value));
    }


    /**
     * Builds a patch list entry line for a zone.
     *
     * @param partial The partial (group) index
     * @param entry The entry index within the partial
     * @param zone The sample zone
     * @param audioMetadata The audio meta data of the sample
     * @param libraryName The (safe) library name, used as the virtual folder prefix
     * @param sampleName The (safe) sample base name
     * @param partialVolume The shared per-partial volume (dB) that is written separately and
     *            therefore removed from this entry's gain
     * @param partialTranspose The shared per-partial transpose (semitones) that is written
     *            separately and therefore removed from this entry's tuning
     * @return The line
     */
    private static String patchEntry (final int partial, final int entry, final ISampleZone zone, final IAudioMetadata audioMetadata, final String libraryName, final String sampleName, final double partialVolume, final int partialTranspose)
    {
        final int frames = audioMetadata.getNumberOfSamples ();
        final int channels = Math.max (1, audioMetadata.getChannels ());
        final int sampleRate = audioMetadata.getSampleRate ();

        // Reverse the hardware laws: volume fraction from gain (dB) and tuning fraction from cents.
        // The
        // shared per-partial volume and transpose are removed first as they are written as their
        // own
        // per-partial lines (SynclavierPTPIVolume / SynclavierPTPITran)
        final double volumeFraction = Math.clamp ((zone.getGain () - partialVolume + 12.0) / 36.0, 0, 1);
        final double tuneFraction = Math.clamp (((zone.getTuning () - partialTranspose) * 100.0 + 125.0) / 250.0, 0, 1);

        final double startFraction = frames <= 0 ? 0 : Math.clamp (zone.getStart () / (double) frames, 0, 1);
        double endFraction = frames <= 0 || zone.getStop () <= 0 ? 1 : Math.clamp (zone.getStop () / (double) frames, 0, 1);
        if (endFraction <= startFraction)
            endFraction = 1;

        int loopBits = 0;
        double loopStartFraction = 0;
        double loopEndFraction = 1;
        final List<ISampleLoop> loops = zone.getLoops ();
        if (!loops.isEmpty () && frames > 0)
        {
            final ISampleLoop loop = loops.get (0);
            loopBits = loop.getCrossfade () > 0 ? 3 : 1;
            loopStartFraction = Math.clamp (loop.getStart () / (double) frames, 0, 1);
            loopEndFraction = Math.clamp (loop.getEnd () / (double) frames, 0, 1);
        }

        final StringBuilder sb = new StringBuilder ("SynclavierPTPatchListEntry ");
        sb.append (partial).append (' ').append (entry).append (' ');
        sb.append (zone.getKeyLow ()).append (' ').append (zone.getKeyHigh ()).append (' ').append (zone.getKeyRoot ()).append (' ');
        sb.append (fraction (volumeFraction)).append (' ').append (fraction (tuneFraction)).append (' ');
        sb.append (fraction (startFraction)).append (' ').append (fraction (endFraction)).append (' ');
        sb.append (fraction (loopStartFraction)).append (' ').append (fraction (loopEndFraction)).append (' ');
        sb.append (loopBits).append (" 0 ").append (channels).append (' ').append (frames).append (' ').append (sampleRate).append (' ');
        sb.append (libraryName).append ('/').append (sampleName).append (".wav");
        return sb.toString ();
    }


    /**
     * Builds a row for the sample index TSV.
     *
     * @param sampleName The (safe) sample base name
     * @param audioMetadata The audio meta data of the sample
     * @return The row
     */
    private static String sampleRow (final String sampleName, final IAudioMetadata audioMetadata)
    {
        final StringBuilder sb = new StringBuilder (sampleName);
        // mTitle, mComment
        sb.append ("\t\t");
        sb.append ('\t').append (audioMetadata.getSampleRate ());
        sb.append ('\t').append (audioMetadata.getNumberOfSamples ());
        sb.append ('\t').append (Math.max (1, audioMetadata.getChannels ()));
        // mMIDIKey, mFileHz, mPitchTrack, mMarkStart, mTotalLen, mLoopLen, mLoopXfade, mLoopBits.
        // The loop is carried by the timbre's patch list entry, so the index loop columns stay 0.
        sb.append ("\t0\t0\t0\t0\t0\t0\t0\t0");
        // mMedia (3 = internal), mExtension, mDLMLSB
        sb.append ("\t3\t.sflc\t0");
        return sb.toString ();
    }


    /**
     * Encodes the sample of a zone as FLAC, obfuscates it and writes it as an SFLC file.
     *
     * @param libraryFolder The destination folder
     * @param sampleName The (safe) sample base name (also the obfuscation key)
     * @param zone The zone whose sample is written
     * @throws IOException Could not write the file
     */
    private static void writeSample (final File libraryFolder, final String sampleName, final ISampleZone zone) throws IOException
    {
        final Path tempFile = Files.createTempFile ("CWM-", ".flac");
        try
        {
            final Optional<ISampleData> sampleData = zone.getSampleData ();
            if (sampleData.isEmpty ())
                throw new IOException ("Empty sample data in zone: " + zone.getName ());
            AudioFileUtils.compressToFLAC (sampleData.get (), FLAC_TARGET_FORMAT, tempFile.toFile ());
            final byte [] flacData = Files.readAllBytes (tempFile);
            final byte [] obfuscated = SynclavierRegenCodec.transform (flacData, sampleName);
            Files.write (new File (libraryFolder, sampleName + ".sflc").toPath (), obfuscated);
        }
        catch (final UnsupportedAudioFileException ex)
        {
            throw new IOException (ex);
        }
        finally
        {
            Files.deleteIfExists (tempFile);
        }
    }


    /**
     * Maps a bank entry index to the timbre file name. The mapping is
     * <code>NN = 10 * (be / 8 + 1) + be % 8 + 1</code>.
     *
     * @param bankEntry The bank entry index (starting at 0)
     * @return The file name (e.g. <i>11-Entry.txt</i>)
     */
    private static String timbreFileName (final int bankEntry)
    {
        final int nn = 10 * (bankEntry / 8 + 1) + bankEntry % 8 + 1;
        return nn + "-Entry.txt";
    }


    private static String uniqueSampleName (final Set<String> usedNames, final String name)
    {
        final String base = createSafeFilename (name);
        String candidate = base;
        int counter = 2;
        while (!usedNames.add (candidate.toLowerCase (Locale.US)))
        {
            candidate = base + "-" + counter;
            counter++;
        }
        return candidate;
    }


    /**
     * Combines several descriptions into one library description, newline-separated, respecting the
     * Regen's {@link #MAX_DESCRIPTION_LENGTH} character display limit. Whole descriptions are added
     * until the next one would exceed the limit; if the first description alone is too long it is
     * truncated.
     *
     * @param descriptions The individual (already stripped, non-blank) descriptions
     * @return The combined description, never longer than {@link #MAX_DESCRIPTION_LENGTH}
     *         characters
     */
    private static String combineDescriptions (final Set<String> descriptions)
    {
        final StringBuilder sb = new StringBuilder ();
        for (final String description: descriptions)
        {
            final int separator = sb.isEmpty () ? 0 : 1;
            if (sb.length () + separator + description.length () > MAX_DESCRIPTION_LENGTH)
            {
                // The first description alone exceeds the limit, so truncate it; otherwise stop
                // adding
                if (sb.isEmpty ())
                    sb.append (description, 0, MAX_DESCRIPTION_LENGTH);
                break;
            }
            if (separator > 0)
                sb.append ('\n');
            sb.append (description);
        }
        return sb.toString ();
    }


    /**
     * Builds the timbre comment from the meta data. The Regen stores the tags as #hash-tags inside
     * the comment (see the Regen manual, section 3.2): the category becomes the primary category
     * tag, the keywords become the property tags. On reading, the first tag is taken as the
     * category again, so the category is written first. Tags are lower-cased and reduced to a
     * single token; duplicates are removed.
     *
     * @param metadata The meta data
     * @return The comment line (description followed by the #hash-tags)
     */
    private static String flattenComment (final IMetadata metadata)
    {
        final StringBuilder sb = new StringBuilder ();
        final String description = metadata.getDescription ();
        if (description != null && !description.isBlank ())
            sb.append (description.replace ("\r", "").replace ("\n", "¶"));

        final Set<String> tags = new LinkedHashSet<> ();
        final Optional<String> categoryTag = regenCategoryTag (metadata.getCategory ());
        if (categoryTag.isPresent ())
            tags.add (categoryTag.get ());
        final String [] keywords = metadata.getKeywords ();
        if (keywords != null)
            for (final String keyword: keywords)
            {
                final Optional<String> tag = sanitizeTag (keyword);
                if (tag.isPresent ())
                    tags.add (tag.get ());
            }
        for (final String tag: tags)
            sb.append (" #").append (tag);
        return sb.toString ();
    }


    /**
     * Maps a ConvertWithMoss category to a Regen category tag. Categories with a clear Regen
     * equivalent are mapped to the canonical tag; the rest fall back to the sanitized category name
     * so the category is still written as a (custom) tag.
     *
     * @param category The category (may be null or "Unknown")
     * @return The Regen category tag or null if there is no usable category
     */
    private static Optional<String> regenCategoryTag (final String category)
    {
        if (category == null || category.isBlank () || "Unknown".equalsIgnoreCase (category))
            return Optional.empty ();
        final String mapped = REGEN_CATEGORY_TAGS.get (category.toLowerCase (Locale.US));
        return mapped != null ? Optional.of (mapped) : sanitizeTag (category);
    }


    /**
     * Sanitizes a tag to the Regen convention: lower-case and reduced to letters and digits (tags
     * are always shown in lower case and are a single token).
     *
     * @param tag The tag text
     * @return The sanitized tag or null if nothing remains
     */
    private static Optional<String> sanitizeTag (final String tag)
    {
        if (tag == null)
            return Optional.empty ();
        final String sanitized = tag.toLowerCase (Locale.US).replaceAll ("[^a-z0-9]", "");
        return Optional.ofNullable (sanitized.isBlank () ? null : sanitized);
    }


    private static String fraction (final double value)
    {
        return String.format (Locale.US, "%.6f", Double.valueOf (value));
    }


    private static File createUniqueFolder (final File destinationFolder, final String name) throws IOException
    {
        File folder = new File (destinationFolder, name);
        int counter = 2;
        while (folder.exists ())
        {
            folder = new File (destinationFolder, name + " " + counter);
            counter++;
        }
        safeCreateDirectory (folder);
        return folder;
    }


    private static void writeTextFile (final File file, final String content) throws IOException
    {
        try (final FileWriter writer = new FileWriter (file, StandardCharsets.UTF_8))
        {
            writer.write (content);
        }
    }
}
