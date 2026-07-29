// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synclavier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects Arturia Synclavier V preset and bank exports (*.synx). A synx file is a ZIP archive
 * which contains one preset file per exported preset (a bank export contains several). A partial
 * whose carrier is in <i>Audition</i> mode plays a referenced sound file and becomes a sample
 * zone; synthesis-only presets (FM / resynthesis frames) contain no audio to convert and are
 * skipped.
 *
 * @author Jürgen Moßgraber
 */
public class SynclavierVDetector extends AbstractDetector<EmptySettingsUI>
{
    private static final String MACOS_METADATA_FOLDER = "__MACOSX/";
    private static final String SAMPLE_OBJECT_PREFIX  = "AudioSampleObject";
    /** The Arturia sample pool of Synclavier V on macOS. */
    private static final File   MACOS_SAMPLE_POOL     = new File ("/Library/Arturia/Samples/Synclavier V");
    /** The Arturia sample pool of Synclavier V on Windows. */
    private static final File   WINDOWS_SAMPLE_POOL   = new File ("C:/ProgramData/Arturia/Samples/Synclavier V");

    private static final int    MAX_PARTIALS          = 12;
    private static final int    CARRIER_MODE_AUDITION = 1;
    private static final int    DYN_SOURCE_VELOCITY   = 1;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SynclavierVDetector (final INotifier notifier)
    {
        super ("Arturia Synclavier V", "SynclavierV", notifier, EmptySettingsUI.INSTANCE, ".synx");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final List<IMultisampleSource> results = new ArrayList<> ();
        try (final ZipFile zipFile = new ZipFile (file))
        {
            for (final ZipEntry entry: Collections.list (zipFile.entries ()))
            {
                if (entry.isDirectory () || entry.getName ().startsWith (MACOS_METADATA_FOLDER))
                    continue;
                final String baseName = fileName (entry.getName ());
                if (baseName.startsWith ("._") || ".DS_Store".equals (baseName))
                    continue;

                final byte [] content;
                try (final InputStream in = zipFile.getInputStream (entry))
                {
                    content = in.readAllBytes ();
                }
                if (!SynclavierVFile.isArchive (content))
                    continue;

                try
                {
                    final SynclavierVFile preset = SynclavierVFile.parse (content);
                    final Optional<IMultisampleSource> source = this.parsePreset (file, zipFile, entry, preset);
                    if (source.isPresent ())
                        results.add (source.get ());
                }
                catch (final IOException ex)
                {
                    this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
                }
            }
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }
        return results;
    }


    /**
     * Creates a multi-sample source from a preset. Each partial in Audition mode with a resolvable
     * sound file becomes a group with one sample zone.
     *
     * @param synxFile The synx file which contains the preset
     * @param zipFile The opened synx ZIP file
     * @param entry The ZIP entry of the preset
     * @param preset The parsed preset
     * @return The multi-sample source or empty if the preset contains no samples
     * @throws IOException Could not read a sample
     */
    private Optional<IMultisampleSource> parsePreset (final File synxFile, final ZipFile zipFile, final ZipEntry entry, final SynclavierVFile preset) throws IOException
    {
        final String presetName = preset.name.isBlank () ? fileName (entry.getName ()) : preset.name;
        final boolean hasVelocitySource = Math.round (preset.getParameter ("Dynamic Envelope Source", 0) * 2) == DYN_SOURCE_VELOCITY;

        int sampleReferences = 0;
        final List<IGroup> groups = new ArrayList<> ();
        for (int partial = 1; partial <= MAX_PARTIALS; partial++)
        {
            final String samplePath = SynclavierVFile.getSamplePath (preset.blobs.get (SAMPLE_OBJECT_PREFIX + partial));
            if (samplePath.isBlank ())
                continue;
            sampleReferences++;

            // Only partials whose carrier plays the sound file ('Audition') provide audio; in
            // 'Synthesis' mode the file was only the source of the resynthesis frames
            final int carrierMode = (int) Math.round (preset.getParameter ("Partial " + partial + " Carrier Mode", 0) * 2);
            if (carrierMode != CARRIER_MODE_AUDITION)
                continue;

            // A partial with its volume at the 'Off' position is inaudible
            final double volumeNormalized = preset.getParameter ("Partial Volume " + partial, 0);
            if (volumeNormalized <= 0)
                continue;

            final ISampleZone zone;
            try
            {
                final Optional<ISampleData> sampleData = this.resolveSample (preset, synxFile, zipFile, entry, samplePath);
                if (sampleData.isEmpty ())
                {
                    this.notifier.logError ("IDS_SYNCLAVIER_SAMPLE_NOT_FOUND", samplePath);
                    continue;
                }
                zone = this.createZone (preset, partial, samplePath, sampleData.get (), volumeNormalized, hasVelocitySource);
            }
            catch (final IOException ex)
            {
                this.notifier.logError ("IDS_SYNCLAVIER_SAMPLE_NOT_FOUND", samplePath);
                continue;
            }
            final DefaultGroup group = new DefaultGroup ("Partial " + partial);
            // Record the per-partial settings on the group as well; they are already applied to
            // the zone, therefore a creator must use either the group or the zone value
            group.setPanning (zone.getPanning ());
            group.setTuning (partialTuning (preset, partial));
            group.addSampleZone (zone);
            groups.add (group);
        }

        if (groups.isEmpty ())
        {
            if (sampleReferences > 0)
                this.notifier.log ("IDS_SYNCLAVIER_V_ONLY_SYNTHESIS", presetName);
            else
                this.notifier.log ("IDS_SYNCLAVIER_V_NO_SAMPLES", presetName);
            return Optional.empty ();
        }

        final IMultisampleSource multisampleSource = this.createMultisampleSource (synxFile, presetName, groups);
        final IMetadata metadata = multisampleSource.getMetadata ();
        if (!preset.author.isBlank ())
            metadata.setCreator (preset.author);
        if (!preset.description.isBlank ())
            metadata.setDescription (preset.description);
        if (!preset.type.isBlank ())
            metadata.setCategory (preset.type);
        if (!preset.tags.isEmpty ())
            metadata.setKeywords (preset.tags.toArray (new String [preset.tags.size ()]));
        return Optional.of (multisampleSource);
    }


    /**
     * Creates a sample zone from the partial parameters.
     *
     * @param preset The preset
     * @param partial The partial index (1..12)
     * @param samplePath The referenced sample path
     * @param sampleData The resolved sample data
     * @param volumeNormalized The normalized partial volume
     * @param hasVelocitySource True if the dynamic envelope source is velocity (the crossfade
     *            window then forms a velocity range)
     * @return The zone
     * @throws IOException Could not read the audio metadata
     */
    private ISampleZone createZone (final SynclavierVFile preset, final int partial, final String samplePath, final ISampleData sampleData, final double volumeNormalized, final boolean hasVelocitySource) throws IOException
    {
        final ISampleZone zone = new DefaultSampleZone (FileUtils.getNameWithoutType (new File (fileName (samplePath))), sampleData);

        zone.setKeyRoot ((int) Math.round (preset.getParameter ("Partial File MIDI Key " + partial, 60.0 / 127.0) * 127.0));
        zone.setKeyLow ((int) Math.round (preset.getParameter ("Keyboard Envelope Start " + partial, 0) * 127.0));
        zone.setKeyHigh ((int) Math.round (preset.getParameter ("Keyboard Envelope End " + partial, 1) * 127.0));
        final int fadeIn = (int) Math.round (preset.getParameter ("Keyboard Envelope In " + partial, 0) * 127.0);
        final int fadeOut = (int) Math.round (preset.getParameter ("Keyboard Envelope Out " + partial, 1) * 127.0);
        zone.setNoteCrossfadeLow (Math.max (0, fadeIn - zone.getKeyLow ()));
        zone.setNoteCrossfadeHigh (Math.max (0, zone.getKeyHigh () - fadeOut));

        if (hasVelocitySource)
        {
            final int low = (int) Math.round (preset.getParameter ("Crossfade Envelope Start " + partial, 0) * 127.0);
            final int high = (int) Math.round (preset.getParameter ("Crossfade Envelope End " + partial, 1) * 127.0);
            zone.setVelocityLow (Math.clamp (low, 0, 127));
            zone.setVelocityHigh (Math.clamp (high, 0, 127));
            final int velocityIn = (int) Math.round (preset.getParameter ("Crossfade Envelope In " + partial, 0) * 127.0);
            final int velocityOut = (int) Math.round (preset.getParameter ("Crossfade Envelope Out " + partial, 1) * 127.0);
            zone.setVelocityCrossfadeLow (Math.max (0, velocityIn - low));
            zone.setVelocityCrossfadeHigh (Math.max (0, high - velocityOut));
        }

        // The partial volume is an attenuation of -50..0 dB, the file volume an offset of
        // -12..+24 dB
        final double fileVolumeNormalized = preset.getParameter ("Partial File Volume " + partial, 1.0 / 3.0);
        zone.setGain (volumeNormalized * 50.0 - 50.0 + fileVolumeNormalized * 36.0 - 12.0);
        zone.setPanning (Math.clamp (preset.getParameter ("Partial Pan " + partial, 0.5) * 126.0 - 63.0, -63, 63) / 63.0);

        // The file tuning is in cents, on top of the partial tuning
        final double fileTuneCents = preset.getParameter ("Partial File Tuning " + partial, 0.5) * 250.0 - 125.0;
        zone.setTuning (fileTuneCents / 100.0 + partialTuning (preset, partial));

        final int frames = sampleData.getAudioMetadata ().getNumberOfSamples ();
        zone.setStart ((int) Math.round (preset.getParameter ("Partial " + partial + " File Start", 0) * frames));
        zone.setStop ((int) Math.round (preset.getParameter ("Partial " + partial + " File End", 1) * frames));

        if (preset.getParameter ("Partial " + partial + " File Loop Mode", 0) >= 0.5)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType (LoopType.FORWARDS);
            loop.setStart ((int) Math.round (preset.getParameter ("Partial " + partial + " File Loop Start", 0) * frames));
            loop.setEnd ((int) Math.round (preset.getParameter ("Partial " + partial + " File Loop End", 1) * frames));
            zone.addLoop (loop);
        }
        else if (preset.getParameter ("Partial " + partial + " Intrinsic File Loop", 1) >= 0.5 && sampleData instanceof final WavFileSampleData wavData)
            // Without an explicit loop the file's own loop applies
            wavData.addZoneData (zone, false, true);

        applyAmplitudeEnvelope (preset, partial, zone);
        return zone;
    }


    /**
     * Computes the additional tuning of a partial in semitones from its Tuning (cents), Transpose
     * (semitones) and Octave (a reference frequency, 440 Hz is neutral) parameters.
     *
     * @param preset The preset
     * @param partial The partial index (1..12)
     * @return The tuning in semitones
     */
    private static double partialTuning (final SynclavierVFile preset, final int partial)
    {
        final double cents = preset.getParameter ("Partial Tuning " + partial, 0.5) * 250.0 - 125.0;
        final double semitones = preset.getParameter ("Partial Transpose " + partial, 0.5) * 48.0 - 24.0;
        // The octave parameter is geometric: 6.875 Hz * 2^(8 * normalized), 440 Hz (0.75) is
        // neutral
        final double octaves = preset.getParameter ("Partial Octave " + partial, 0.75) * 96.0 - 72.0;
        return cents / 100.0 + semitones + octaves;
    }


    /**
     * Applies the volume envelope parameters of a partial to the amplitude envelope of a zone. The
     * times run through the Synclavier time table, the levels are percentages.
     *
     * @param preset The preset
     * @param partial The partial index (1..12)
     * @param zone The zone to update
     */
    private static void applyAmplitudeEnvelope (final SynclavierVFile preset, final int partial, final ISampleZone zone)
    {
        final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        envelope.setDelayTime (SynclavierVFile.normalizedToSeconds (preset.getParameter ("Volume Envelope Delay " + partial, 0)));
        envelope.setAttackTime (SynclavierVFile.normalizedToSeconds (preset.getParameter ("Volume Envelope Attack " + partial, 0.012)));
        envelope.setDecayTime (SynclavierVFile.normalizedToSeconds (preset.getParameter ("Volume Envelope Initial Decay " + partial, 0)));
        envelope.setReleaseTime (SynclavierVFile.normalizedToSeconds (preset.getParameter ("Volume Envelope Final Decay " + partial, 0.3)));
        envelope.setHoldLevel (Math.clamp (preset.getParameter ("Volume Envelope Peak " + partial, 1), 0, 1));
        envelope.setSustainLevel (Math.clamp (preset.getParameter ("Volume Envelope Sustain " + partial, 1), 0, 1));
    }


    /**
     * Resolves a referenced sound file. The reference is first looked up in the sound files
     * embedded in the preset (exports are self-contained), then inside the synx ZIP (relative to
     * the preset entry and by its base name), as an absolute path, relative to the folder of the
     * synx file and finally in the Arturia sample pool.
     *
     * @param preset The preset which references the sound file
     * @param synxFile The synx file
     * @param zipFile The opened ZIP file
     * @param entry The preset entry
     * @param samplePath The referenced path
     * @return The sample data or empty if the file could not be found
     * @throws IOException Could not read the sample
     */
    private Optional<ISampleData> resolveSample (final SynclavierVFile preset, final File synxFile, final ZipFile zipFile, final ZipEntry entry, final String samplePath) throws IOException
    {
        final String normalizedPath = samplePath.replace ('\\', '/');

        // Embedded in the preset itself?
        byte [] embedded = preset.samples.get (samplePath);
        if (embedded == null)
            embedded = preset.samples.get (normalizedPath);
        if (embedded == null)
        {
            final String base = fileName (normalizedPath).toLowerCase (Locale.US);
            for (final Map.Entry<String, byte []> sampleEntry: preset.samples.entrySet ())
                if (fileName (sampleEntry.getKey ()).toLowerCase (Locale.US).equals (base))
                {
                    embedded = sampleEntry.getValue ();
                    break;
                }
        }
        if (embedded != null)
            return Optional.of (new WavFileSampleData (new ByteArrayInputStream (embedded)));

        // Bundled in the synx file, relative to the preset entry?
        final String entryFolder = folderName (entry.getName ());
        ZipEntry sampleEntry = zipFile.getEntry (entryFolder + normalizedPath);
        if (sampleEntry == null)
            sampleEntry = zipFile.getEntry (normalizedPath);
        if (sampleEntry == null)
        {
            // Anywhere in the ZIP by its base name
            final String base = fileName (normalizedPath).toLowerCase (Locale.US);
            for (final ZipEntry candidate: Collections.list (zipFile.entries ()))
                if (!candidate.isDirectory () && fileName (candidate.getName ()).toLowerCase (Locale.US).equals (base) && !candidate.getName ().startsWith (MACOS_METADATA_FOLDER))
                {
                    sampleEntry = candidate;
                    break;
                }
        }
        if (sampleEntry != null)
            try (final InputStream in = zipFile.getInputStream (sampleEntry))
            {
                return Optional.of (new WavFileSampleData (in));
            }

        // An absolute path (user imports keep the full original path)
        final File absolute = new File (normalizedPath);
        if (absolute.isAbsolute () && absolute.exists ())
            return Optional.of (createSampleData (absolute, this.notifier));

        // Relative to the folder of the synx file
        final File relative = new File (synxFile.getParentFile (), normalizedPath);
        if (relative.exists ())
            return Optional.of (createSampleData (relative, this.notifier));

        // The Arturia sample pool
        for (final File pool: new File []
        {
            MACOS_SAMPLE_POOL,
            WINDOWS_SAMPLE_POOL
        })
        {
            final File pooled = new File (pool, normalizedPath);
            if (pooled.exists ())
                return Optional.of (createSampleData (pooled, this.notifier));
        }

        return Optional.empty ();
    }


    private static String fileName (final String path)
    {
        final String normalized = path.replace ('\\', '/');
        final int slash = normalized.lastIndexOf ('/');
        return slash < 0 ? normalized : normalized.substring (slash + 1);
    }


    private static String folderName (final String path)
    {
        final String normalized = path.replace ('\\', '/');
        final int slash = normalized.lastIndexOf ('/');
        return slash < 0 ? "" : normalized.substring (0, slash + 1);
    }
}
