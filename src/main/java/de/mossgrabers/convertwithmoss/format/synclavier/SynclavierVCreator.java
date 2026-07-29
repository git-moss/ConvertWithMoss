// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synclavier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.tools.ui.Functions;


/**
 * Creates Arturia Synclavier V preset exports (*.synx). A synx file is a ZIP archive with the
 * layout <i>Synclavier/User/&lt;library&gt;/&lt;preset&gt;</i> which Synclavier V creates with its
 * 'Export Preset'/'Export Bank' functions. Each sample zone becomes a partial whose carrier is set
 * to <i>Audition</i> mode (raw sound file playback); the samples are bundled in the archive and
 * referenced relative to the preset file. Since a Synclavier timbre has 12 partials, at most 12
 * zones can be stored.
 *
 * @author Jürgen Moßgraber
 */
public class SynclavierVCreator extends AbstractCreator<EmptySettingsUI>
{
    private static final String SYNX_ENDING           = "synx";
    private static final String TEMPLATE_NAME         = "SynclavierVTemplate.preset.gz";
    private static final String ZIP_ROOT              = "Synclavier/User/";
    /** The embedded samples are referenced like pool samples under a 'User' pool folder. */
    private static final String SAMPLE_FOLDER         = "User";

    private static final int    MAX_PARTIALS          = 12;
    private static final double CARRIER_MODE_AUDITION = 0.5;
    private static final double DYN_SOURCE_VELOCITY   = 0.5;

    private static byte []      templateData;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SynclavierVCreator (final INotifier notifier)
    {
        super ("Arturia Synclavier V", "SynclavierV", notifier, EmptySettingsUI.INSTANCE);
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
        this.storeSynxFile (destinationFolder, List.of (multisampleSource), multisampleSource.getName ());
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        this.storeSynxFile (destinationFolder, multisampleSources, libraryName);
    }


    /**
     * Writes one synx file which contains all the given presets.
     *
     * @param destinationFolder The folder in which to create the synx file
     * @param multisampleSources The presets to store
     * @param libraryName The name of the library (used for the file and the ZIP folder)
     * @throws IOException Could not store the file
     */
    private void storeSynxFile (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        final String safeLibraryName = createSafeFilename (libraryName);
        final File synxFile = this.createUniqueFilename (destinationFolder, safeLibraryName, SYNX_ENDING);
        this.notifier.log ("IDS_NOTIFY_STORING", synxFile.getAbsolutePath ());

        final String libraryFolder = ZIP_ROOT + safeLibraryName + "/";
        final Set<String> usedPresetNames = new HashSet<> ();
        final MinimalZipWriter zipWriter = new MinimalZipWriter ();
        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            String presetName = createSafeFilename (multisampleSource.getName ());
            int counter = 2;
            while (!usedPresetNames.add (presetName))
                presetName = createSafeFilename (multisampleSource.getName ()) + " " + counter++;

            this.storePreset (zipWriter, libraryFolder, safeLibraryName, presetName, multisampleSource);
        }
        try (final FileOutputStream out = new FileOutputStream (synxFile))
        {
            zipWriter.writeTo (out);
        }
    }


    /**
     * Stores one preset file and its samples in the ZIP output stream.
     *
     * @param zipOutputStream The ZIP output stream
     * @param libraryFolder The ZIP folder of the library including the trailing slash
     * @param libraryName The library name
     * @param presetName The unique name of the preset
     * @param multisampleSource The preset content
     * @throws IOException Could not store the preset
     */
    private void storePreset (final MinimalZipWriter zipWriter, final String libraryFolder, final String libraryName, final String presetName, final IMultisampleSource multisampleSource) throws IOException
    {
        final SynclavierVFile preset = SynclavierVFile.parse (getTemplate ());

        final IMetadata metadata = multisampleSource.getMetadata ();
        preset.name = presetName;
        preset.library = libraryName;
        final String creator = metadata.getCreator ();
        preset.author = creator == null || creator.isBlank () ? "ConvertWithMoss" : creator;
        final String category = metadata.getCategory ();
        preset.type = category == null || "Unknown".equals (category) ? "" : category;
        preset.description = metadata.getDescription () == null ? "" : metadata.getDescription ();
        preset.tags.clear ();
        if (metadata.getKeywords () != null)
            for (final String keyword: metadata.getKeywords ())
                if (!keyword.isBlank ())
                    preset.tags.add (keyword);
        preset.timestamp = System.currentTimeMillis () / 1000;
        preset.metadata.put ("Type", preset.type.isBlank () ? "Custom" : preset.type);

        // Collect at most 12 zones - a Synclavier timbre has 12 partials
        final List<ISampleZone> zones = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
                zones.add (zone);
        if (zones.size () > MAX_PARTIALS)
        {
            this.notifier.logError ("IDS_SYNCLAVIER_PARTIAL_CAP", multisampleSource.getName (), Integer.toString (zones.size ()));
            while (zones.size () > MAX_PARTIALS)
                zones.remove (zones.size () - 1);
        }

        // Velocity layers require the dynamic envelope source to be velocity
        boolean hasVelocityRange = false;
        for (final ISampleZone zone: zones)
            if (zone.getVelocityLow () > 1 || zone.getVelocityHigh () < 127)
                hasVelocityRange = true;
        if (hasVelocityRange)
            preset.setParameter ("Dynamic Envelope Source", DYN_SOURCE_VELOCITY);

        // The samples are embedded in the preset file itself, as the application's own exports do.
        // They are referenced like pool samples, relative to the Arturia sample pool.
        final String sampleFolder = SAMPLE_FOLDER + "/" + presetName;
        final Set<String> usedSampleNames = new HashSet<> ();
        for (int index = 0; index < zones.size (); index++)
        {
            final ISampleZone zone = zones.get (index);

            // Ensure a unique sample file name
            String sampleName = createSafeFilename (zone.getName ());
            int counter = 2;
            while (!usedSampleNames.add (sampleName.toLowerCase ()))
                sampleName = createSafeFilename (zone.getName ()) + " " + counter++;
            zone.setName (sampleName);

            final String samplePath = sampleFolder + "/" + sampleName + ".wav";
            preset.samples.put (samplePath, this.createSampleFileContent (multisampleSource, zone));
            this.fillPartial (preset, index + 1, zone, samplePath);
        }

        zipWriter.addEntry (libraryFolder + presetName, preset.write ());
    }


    /**
     * Creates the WAV file content of a zone, applying the configured destination format handling.
     *
     * @param multisampleSource The multi-sample source of the zone
     * @param zone The zone
     * @return The WAV file bytes
     * @throws IOException Could not create the audio data
     */
    private byte [] createSampleFileContent (final IMultisampleSource multisampleSource, final ISampleZone zone) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA", zone.getName (), zone.getName () + ".wav"));
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream ())
        {
            if (this.requiresRewrite (DESTINATION_FORMAT))
                this.rewriteFile (multisampleSource, zone, out, DESTINATION_FORMAT, false);
            else
                sampleData.get ().writeSample (out);
            return out.toByteArray ();
        }
    }


    /**
     * Fills the parameters and the sample reference of one partial from a sample zone.
     *
     * @param preset The preset to fill
     * @param partial The partial index (1..12)
     * @param zone The zone
     * @param samplePath The path of the sample file relative to the preset file
     * @throws IOException Could not create the sample reference
     */
    private void fillPartial (final SynclavierVFile preset, final int partial, final ISampleZone zone, final String samplePath) throws IOException
    {
        preset.blobs.put ("AudioSampleObject" + partial, SynclavierVFile.createSampleBlob (samplePath));
        preset.setParameter ("Partial " + partial + " Carrier Mode", CARRIER_MODE_AUDITION);

        // Split the zone gain into the partial volume (-50..0 dB) and the file volume
        // (-12..+24 dB)
        final double gain = zone.getGain ();
        final double fileVolume = Math.clamp (gain, -12, 24);
        final double partialVolume = Math.clamp (gain - fileVolume, -49.9, 0);
        preset.setParameter ("Partial Volume " + partial, (partialVolume + 50.0) / 50.0);
        preset.setParameter ("Partial File Volume " + partial, (fileVolume + 12.0) / 36.0);

        preset.setParameter ("Partial Pan " + partial, (Math.clamp (zone.getPanning (), -1, 1) * 63.0 + 63.0) / 126.0);

        // Split the tuning into octaves (the partial octave covers -72..+24 semi-tones in octave
        // steps), whole semi-tones (transpose, +-24) and cents (file tuning, +-125)
        final double tuning = zone.getTuning ();
        double octaveSemitones = 0;
        if (tuning > 24)
            octaveSemitones = Math.min (24, 12 * Math.ceil ((tuning - 24) / 12.0));
        else if (tuning < -24)
            octaveSemitones = Math.max (-72, 12 * Math.floor ((tuning + 24) / 12.0));
        final double remainder = tuning - octaveSemitones;
        final double transpose = Math.clamp (Math.rint (remainder), -24, 24);
        final double cents = Math.clamp ((remainder - transpose) * 100.0, -125, 125);
        preset.setParameter ("Partial Octave " + partial, (octaveSemitones + 72.0) / 96.0);
        preset.setParameter ("Partial Transpose " + partial, (transpose + 24.0) / 48.0);
        preset.setParameter ("Partial File Tuning " + partial, (cents + 125.0) / 250.0);

        preset.setParameter ("Partial File MIDI Key " + partial, Math.clamp (zone.getKeyRoot () < 0 ? 60 : zone.getKeyRoot (), 0, 127) / 127.0);

        // The keyboard envelope window forms the key range with its cross-fades
        final int keyLow = Math.clamp (zone.getKeyLow () < 0 ? 0 : zone.getKeyLow (), 0, 127);
        final int keyHigh = Math.clamp (zone.getKeyHigh () < 0 ? 127 : zone.getKeyHigh (), 0, 127);
        preset.setParameter ("Keyboard Envelope Start " + partial, keyLow / 127.0);
        preset.setParameter ("Keyboard Envelope In " + partial, Math.clamp (keyLow + Math.max (0, zone.getNoteCrossfadeLow ()), 0, 127) / 127.0);
        preset.setParameter ("Keyboard Envelope Out " + partial, Math.clamp (keyHigh - Math.max (0, zone.getNoteCrossfadeHigh ()), 0, 127) / 127.0);
        preset.setParameter ("Keyboard Envelope End " + partial, keyHigh / 127.0);

        // The cross-fade envelope window forms the velocity range
        final int velocityLow = Math.clamp (zone.getVelocityLow () < 0 ? 0 : zone.getVelocityLow (), 0, 127);
        final int velocityHigh = Math.clamp (zone.getVelocityHigh () < 0 ? 127 : zone.getVelocityHigh (), 0, 127);
        preset.setParameter ("Crossfade Envelope Start " + partial, velocityLow / 127.0);
        preset.setParameter ("Crossfade Envelope In " + partial, Math.clamp (velocityLow + Math.max (0, zone.getVelocityCrossfadeLow ()), 0, 127) / 127.0);
        preset.setParameter ("Crossfade Envelope Out " + partial, Math.clamp (velocityHigh - Math.max (0, zone.getVelocityCrossfadeHigh ()), 0, 127) / 127.0);
        preset.setParameter ("Crossfade Envelope End " + partial, velocityHigh / 127.0);

        // Sample start / end and the loop as fractions of the sample length
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        int frames = 0;
        if (sampleData.isPresent ())
            frames = sampleData.get ().getAudioMetadata ().getNumberOfSamples ();
        if (frames > 0)
        {
            if (zone.getStart () > 0)
                preset.setParameter ("Partial " + partial + " File Start", Math.clamp (zone.getStart () / (double) frames, 0, 1));
            if (zone.getStop () > 0)
                preset.setParameter ("Partial " + partial + " File End", Math.clamp (zone.getStop () / (double) frames, 0, 1));

            final List<ISampleLoop> loops = zone.getLoops ();
            if (!loops.isEmpty ())
            {
                final ISampleLoop loop = loops.get (0);
                preset.setParameter ("Partial " + partial + " File Loop Mode", 1);
                preset.setParameter ("Partial " + partial + " File Loop Start", Math.clamp (loop.getStart () / (double) frames, 0, 1));
                preset.setParameter ("Partial " + partial + " File Loop End", Math.clamp (loop.getEnd () / (double) frames, 0, 1));
                if (loop.getCrossfade () > 0)
                    preset.setParameter ("Partial " + partial + " File Loop Decay", 1);
            }
        }

        // The volume envelope: times through the Synclavier time table, levels in percent
        final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        if (envelope.getDelayTime () >= 0)
            preset.setParameter ("Volume Envelope Delay " + partial, SynclavierVFile.secondsToNormalized (envelope.getDelayTime ()));
        if (envelope.getAttackTime () >= 0)
            preset.setParameter ("Volume Envelope Attack " + partial, SynclavierVFile.secondsToNormalized (envelope.getAttackTime ()));
        if (envelope.getDecayTime () >= 0)
            preset.setParameter ("Volume Envelope Initial Decay " + partial, SynclavierVFile.secondsToNormalized (envelope.getDecayTime ()));
        if (envelope.getReleaseTime () >= 0)
            preset.setParameter ("Volume Envelope Final Decay " + partial, SynclavierVFile.secondsToNormalized (envelope.getReleaseTime ()));
        if (envelope.getHoldLevel () >= 0)
            preset.setParameter ("Volume Envelope Peak " + partial, Math.clamp (envelope.getHoldLevel (), 0, 1));
        if (envelope.getSustainLevel () >= 0)
            preset.setParameter ("Volume Envelope Sustain " + partial, Math.clamp (envelope.getSustainLevel (), 0, 1));
    }


    /**
     * Loads the neutral preset template.
     *
     * @return The template data
     * @throws IOException Could not load the template
     */
    private static synchronized byte [] getTemplate () throws IOException
    {
        if (templateData == null)
            try (final InputStream in = new GZIPInputStream (SynclavierVCreator.class.getResourceAsStream (TEMPLATE_NAME)))
            {
                templateData = in.readAllBytes ();
            }
        return templateData;
    }


    /**
     * Writes a ZIP file the way the Synclavier V export does: all entries stored (no compression),
     * no general purpose flags and no extra fields. The application's import is picky about the
     * container - archives written by {@link java.util.zip.ZipOutputStream} (which flags its
     * entries as UTF-8 and adds extended timestamp fields) are rejected.
     */
    private static class MinimalZipWriter
    {
        private record Entry (byte [] name, byte [] data, long crc, int offset)
        {
            // Intentionally empty
        }

        private final List<Entry> entries = new ArrayList<> ();
        private int               position = 0;


        /**
         * Adds a stored entry.
         *
         * @param name The entry name (forward slashes as separators)
         * @param data The entry content
         */
        void addEntry (final String name, final byte [] data)
        {
            final CRC32 checksum = new CRC32 ();
            checksum.update (data);
            final byte [] nameBytes = name.getBytes (StandardCharsets.UTF_8);
            this.entries.add (new Entry (nameBytes, data, checksum.getValue (), this.position));
            this.position += 30 + nameBytes.length + data.length;
        }


        /**
         * Writes the ZIP file.
         *
         * @param out Where to write to
         * @throws IOException Could not write
         */
        void writeTo (final OutputStream out) throws IOException
        {
            for (final Entry entry: this.entries)
            {
                writeInt (out, 0x04034B50);
                writeShort (out, 20);
                writeShort (out, 0);
                writeShort (out, 0);
                writeShort (out, 0);
                writeShort (out, 0x21);
                writeInt (out, (int) entry.crc);
                writeInt (out, entry.data.length);
                writeInt (out, entry.data.length);
                writeShort (out, entry.name.length);
                writeShort (out, 0);
                out.write (entry.name);
                out.write (entry.data);
            }

            int centralSize = 0;
            for (final Entry entry: this.entries)
            {
                writeInt (out, 0x02014B50);
                writeShort (out, 20);
                writeShort (out, 20);
                writeShort (out, 0);
                writeShort (out, 0);
                writeShort (out, 0);
                writeShort (out, 0x21);
                writeInt (out, (int) entry.crc);
                writeInt (out, entry.data.length);
                writeInt (out, entry.data.length);
                writeShort (out, entry.name.length);
                writeShort (out, 0);
                writeShort (out, 0);
                writeShort (out, 0);
                writeShort (out, 0);
                writeInt (out, 0);
                writeInt (out, entry.offset);
                out.write (entry.name);
                centralSize += 46 + entry.name.length;
            }

            writeInt (out, 0x06054B50);
            writeShort (out, 0);
            writeShort (out, 0);
            writeShort (out, this.entries.size ());
            writeShort (out, this.entries.size ());
            writeInt (out, centralSize);
            writeInt (out, this.position);
            writeShort (out, 0);
        }


        private static void writeShort (final OutputStream out, final int value) throws IOException
        {
            out.write (value & 0xFF);
            out.write (value >> 8 & 0xFF);
        }


        private static void writeInt (final OutputStream out, final int value) throws IOException
        {
            out.write (value & 0xFF);
            out.write (value >> 8 & 0xFF);
            out.write (value >> 16 & 0xFF);
            out.write (value >> 24 & 0xFF);
        }
    }
}
