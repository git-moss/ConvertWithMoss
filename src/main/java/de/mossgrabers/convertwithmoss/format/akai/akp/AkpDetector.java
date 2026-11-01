// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.algorithm.PitchDetector;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultInstrumentSource;
import de.mossgrabers.convertwithmoss.core.detector.DefaultPerformanceSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.SampleChunk;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkmPart;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects Akai S5000/S6000 AKP files. Files must end with <i>.akp</i>.
 *
 * @author Jürgen Moßgraber
 */
public class AkpDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final Pattern   MULTIPLE_SPACES_PATTERN = Pattern.compile ("\\s{2,}(?=\\S*$)");

    /** The MMA manufacturer ID of Akai as stored in the sample chunk of a WAV file. */
    private static final int       MANUFACTURER_AKAI       = 0x01000047;
    /** The length of the Akai specific data at the end of the sample chunk. */
    private static final int       AKAI_SAMPLER_DATA_SIZE  = 18;

    private static final String [] ENDINGS_PRESET          =
    {
        ".akp"
    };

    private static final String [] ENDINGS_PERFORMANCES    =
    {
        ".akm"
    };


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public AkpDetector (final INotifier notifier)
    {
        super ("Akai AKP/AKM", "S5000", notifier, new MetadataSettingsUI ("AKP"));
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPerformances ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    protected void configureFileEndings (final boolean detectPerformances)
    {
        this.fileEndings = detectPerformances ? ENDINGS_PERFORMANCES : ENDINGS_PRESET;
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        return this.createMultisample (file, true);
    }


    private List<IMultisampleSource> createMultisample (final File file, final boolean logVersion)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final AkpFile akpFile = new AkpFile (file);
            if (logVersion)
                this.notifier.log ("IDS_AKP_VERSION", akpFile.isS5000Series () ? "S5000/S6000" : "Z4/Z8");

            final IGroup group = akpFile.createGroup (this.settingsConfiguration);
            final String name = FileUtils.getNameWithoutType (file);
            final IMultisampleSource multisampleSource = this.createMultisampleSource (file, name.endsWith ("-") ? name.substring (0, name.length () - 1) : name, Collections.singletonList (group));

            // Check and set wave file sample data
            boolean hasError = false;
            for (final ISampleZone sampleZone: group.getSampleZones ())
            {
                final String n = sampleZone.getName ();
                File sampleFile = findFileIgnoreCase (file.getParentFile (), n + ".wav");
                if (!sampleFile.exists ())
                {
                    // Workaround for several samples seem to have 1 space before the note name
                    // but are stored with 2 or more spaces
                    final String n2 = MULTIPLE_SPACES_PATTERN.matcher (n).replaceFirst (" ");
                    final File sampleFile2 = findFileIgnoreCase (file.getParentFile (), n2 + ".wav");
                    if (sampleFile2.exists ())
                        sampleFile = sampleFile2;
                }

                if (AudioFileUtils.checkSampleFile (sampleFile, this.notifier))
                {
                    final WavFileSampleData sampleData = new WavFileSampleData (sampleFile);
                    sampleZone.setSampleData (sampleData);
                    // If there is a loop required, read it from the sample chunk
                    final List<ISampleLoop> loops = sampleZone.getLoops ();
                    final boolean addLoops = !loops.isEmpty ();
                    final boolean isLoopUntilRelease = addLoops && loops.get (0).isLoopUntilRelease ();
                    loops.clear ();
                    sampleData.addZoneData (sampleZone, true, addLoops);
                    for (final ISampleLoop loop: loops)
                        loop.setLoopUntilRelease (isLoopUntilRelease);
                    applyAkaiRootKey (sampleData, sampleZone);
                    if (sampleZone.getKeyRoot () < 0)
                        this.measureRootKey (sampleZone, sampleFile);

                    // Several programs have set C3 as root and use pitch instead which leads to
                    // very bad sounding conversion results. Therefore, move the root key by the
                    // full semi-tones of the tuning and keep only the remaining fraction
                    final int keyRoot = sampleZone.getKeyRoot ();
                    if (keyRoot >= 0)
                    {
                        final double tuning = sampleZone.getTuning ();
                        final int newKeyRoot = Math.clamp (keyRoot - Math.round (tuning), 0, 127);
                        sampleZone.setKeyRoot (newKeyRoot);
                        sampleZone.setTuning (tuning - (keyRoot - newKeyRoot));
                    }
                }
                else
                    hasError = true;
            }
            if (hasError)
                return Collections.emptyList ();

            return Collections.singletonList (multisampleSource);
        }
        catch (final IOException | ParseException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Samples written by an Akai sampler contain an Akai specific block at the end of the sample
     * chunk which contains the root key (byte 2) and the semi-tone tuning (byte 3, signed) of the
     * sample. The sampler stores the root key plus the tuning as the MIDI unity note of the sample
     * chunk but the pitch of such samples matches the root key minus the tuning (e.g. a sample with
     * the pitch of C4 has a root key of C3 and a tuning of -12 semi-tones but a unity note of C2).
     * Therefore, the root key is calculated from the Akai block if present.
     *
     * @param sampleData The sample data
     * @param sampleZone The zone in which to set the root key
     * @throws IOException Could not read the WAV file
     */
    private static void applyAkaiRootKey (final WavFileSampleData sampleData, final ISampleZone sampleZone) throws IOException
    {
        final SampleChunk sampleChunk = sampleData.getWaveFile ().getSampleChunk ();
        if (sampleChunk == null || sampleChunk.getManufacturer () != MANUFACTURER_AKAI)
            return;
        final byte [] samplerData = sampleChunk.getSamplerSpecificData ();
        if (samplerData.length < AKAI_SAMPLER_DATA_SIZE)
            return;
        sampleZone.setKeyRoot (Math.clamp (Byte.toUnsignedInt (samplerData[2]) - samplerData[3], 0, 127));
    }


    /**
     * The AKP format contains no root key, it is only stored in the sample chunk of the WAV file.
     * If the WAV file has no sample chunk (e.g. since it was stored by an application which removed
     * it), the root key is measured from the pitch of the sample. If the sample is not clearly
     * pitched, the lowest key of the key-group is used.
     *
     * @param sampleZone The zone for which to set the root key
     * @param sampleFile The sample file
     */
    private void measureRootKey (final ISampleZone sampleZone, final File sampleFile)
    {
        final int expectedNote = (sampleZone.getKeyLow () + sampleZone.getKeyHigh ()) / 2;
        final Optional<Double> pitch = PitchDetector.measurePitch (sampleZone, expectedNote);
        if (pitch.isEmpty ())
        {
            sampleZone.setKeyRoot (sampleZone.getKeyLow ());
            this.notifier.logError ("IDS_AKP_ROOT_KEY_MISSING", sampleFile.getName (), Integer.toString (sampleZone.getKeyLow ()));
            return;
        }

        final double note = pitch.get ().doubleValue ();
        final int rootKey = Math.clamp (Math.round (note), 0, 127);
        sampleZone.setKeyRoot (rootKey);
        // Keep the fraction of a semitone by which the sample deviates from the root key
        sampleZone.setTuning (sampleZone.getTuning () + rootKey - note);
        this.notifier.log ("IDS_AKP_ROOT_KEY_MEASURED", sampleFile.getName (), Integer.toString (rootKey));
    }


    /** {@inheritDoc} */
    @Override
    protected List<IPerformanceSource> readPerformanceFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final AkmFile akmFile = new AkmFile (file);
            this.notifier.log ("IDS_AKM_VERSION", akmFile.getVersion (), akmFile.isS5000Series () ? "S5000/S6000" : "Z4/Z8");

            final IPerformanceSource performanceSource = new DefaultPerformanceSource ();
            performanceSource.setName (FileUtils.getNameWithoutType (file));

            for (final AkmPart part: akmFile.getParts ())
            {
                final String presetName = part.getPresetName ().trim ();
                if (presetName.isBlank ())
                    continue;
                final File presetFile = findFileIgnoreCase (file.getParentFile (), presetName + ".AKP");
                if (!presetFile.exists ())
                {
                    this.notifier.logError ("IDS_NOTIFY_ERR_PROGRAM_DOES_NOT_EXIST", presetFile.getAbsolutePath ());
                    continue;
                }

                final Optional<IInstrumentSource> instrumentSource = this.readPresetFileAsInstrument (presetFile, part);
                if (instrumentSource.isPresent ())
                    performanceSource.addInstrument (instrumentSource.get ());
            }

            return Collections.singletonList (performanceSource);
        }
        catch (final IOException | ParseException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private Optional<IInstrumentSource> readPresetFileAsInstrument (final File sourceFile, final AkmPart part)
    {
        if (this.waitForDelivery ())
            return Optional.empty ();

        final List<IMultisampleSource> multisampleSources = this.createMultisample (sourceFile, false);
        if (multisampleSources.isEmpty ())
            return Optional.empty ();

        final IInstrumentSource instrumentSource = new DefaultInstrumentSource (multisampleSources.get (0), part.getMidiChannel () % 16);
        instrumentSource.setName (part.getPresetName ());
        instrumentSource.setClipKeyLow (part.getLowKey ());
        instrumentSource.setClipKeyHigh (part.getHighKey ());

        final List<IGroup> groups = instrumentSource.getMultisampleSource ().getGroups ();
        if (groups.isEmpty ())
            return Optional.empty ();

        final double panningOffset = part.getPanning () / 50.0;
        if (panningOffset != 0)
            instrumentSource.setPanning (Math.clamp (panningOffset, -1.0, 1.0));
        final double volumeOffset = MathUtils.valueToDb (part.getVolume () / 100.0);
        if (volumeOffset != 0)
            instrumentSource.setGain (volumeOffset);

        return Optional.of (instrumentSource);
    }
}
