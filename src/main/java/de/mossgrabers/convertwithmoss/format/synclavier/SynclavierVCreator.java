// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synclavier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipOutputStream;

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
    private static final String SAMPLE_FOLDER         = "Samples";

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
        try (final ZipOutputStream zipOutputStream = new ZipOutputStream (new FileOutputStream (synxFile)))
        {
            for (final IMultisampleSource multisampleSource: multisampleSources)
            {
                String presetName = createSafeFilename (multisampleSource.getName ());
                int counter = 2;
                while (!usedPresetNames.add (presetName))
                    presetName = createSafeFilename (multisampleSource.getName ()) + " " + counter++;

                this.storePreset (zipOutputStream, libraryFolder, safeLibraryName, presetName, multisampleSource);
            }
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
    private void storePreset (final ZipOutputStream zipOutputStream, final String libraryFolder, final String libraryName, final String presetName, final IMultisampleSource multisampleSource) throws IOException
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

        final String sampleFolder = SAMPLE_FOLDER + "/" + presetName;
        final Set<String> usedSampleNames = new HashSet<> ();
        for (int index = 0; index < zones.size (); index++)
        {
            final ISampleZone zone = zones.get (index);

            // Ensure a unique sample file name inside the preset's sample folder
            String sampleName = createSafeFilename (zone.getName ());
            int counter = 2;
            while (!usedSampleNames.add (sampleName.toLowerCase ()))
                sampleName = createSafeFilename (zone.getName ()) + " " + counter++;
            zone.setName (sampleName);

            this.fillPartial (preset, index + 1, zone, sampleFolder + "/" + sampleName + ".wav");
        }

        storeDataFile (zipOutputStream, libraryFolder + presetName, preset.write (), metadata.getCreationDateTime ());
        this.storeSampleFiles (zipOutputStream, libraryFolder + sampleFolder, multisampleSource);
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

        // Split the tuning into whole semi-tones (transpose) and cents (file tuning)
        final double tuning = zone.getTuning ();
        final double transpose = Math.clamp (Math.rint (tuning), -24, 24);
        final double cents = Math.clamp ((tuning - transpose) * 100.0, -125, 125);
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


    /** {@inheritDoc} */
    @Override
    protected String createFileName (final int zoneIndex, final ISampleZone zone)
    {
        return createSafeFilename (zone.getName ()) + ".wav";
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
}
