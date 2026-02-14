// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s5000;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultInstrumentSource;
import de.mossgrabers.convertwithmoss.core.detector.DefaultPerformanceSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.convertwithmoss.format.akai.s5000.riff.AkmPart;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects Akai S5000/S6000 AKP files. Files must end with <i>.akp</i>.
 *
 * @author Jürgen Moßgraber
 */
public class AkpDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String [] ENDINGS_PRESET       =
    {
        ".akp"
    };

    private static final String [] ENDINGS_PERFORMANCES =
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

            final IMultisampleSource multisampleSource = akpFile.createMultisampleSource (this.sourceFolder, this.settingsConfiguration);

            // Check and set wave file sample data
            boolean hasError = false;
            for (final IGroup group: multisampleSource.getGroups ())
                for (final ISampleZone sampleZone: group.getSampleZones ())
                {
                    final String n = sampleZone.getName ();
                    File sampleFile = new File (file.getParentFile (), n + ".wav");
                    if (!sampleFile.exists ())
                    {
                        // Workaround for several samples seem to have 1 space before the note name
                        // but are stored with 2 or more spaces
                        final String n2 = n.replaceFirst ("\\s{2,}(?=\\S*$)", " ");
                        File sampleFile2 = new File (file.getParentFile (), n2 + ".wav");
                        if (sampleFile2.exists ())
                            sampleFile = sampleFile2;
                    }

                    if (AudioFileUtils.checkSampleFile (sampleFile, this.notifier))
                    {
                        final WavFileSampleData sampleData = new WavFileSampleData (sampleFile);
                        sampleZone.setSampleData (sampleData);
                        // If there is a loop required, read it from the sample chunk
                        final boolean addLoops = !sampleZone.getLoops ().isEmpty ();
                        sampleZone.getLoops ().clear ();
                        sampleData.addZoneData (sampleZone, true, addLoops);

                        // Several programs have set C3 as root and use pitch instead which leads to
                        // very bad sounding conversion results. Therefore, move the root key
                        // instead
                        final double tuning = sampleZone.getTuning ();
                        sampleZone.setTuning (tuning % 100);
                        sampleZone.setKeyRoot (sampleZone.getKeyRoot () - (int) Math.round (tuning / 100));
                    }
                    else
                        hasError = true;
                }
            if (hasError)
                return Collections.emptyList ();

            // Improve name
            String name = multisampleSource.getName ();
            if (name.endsWith ("-"))
                name = name.substring (0, name.length () - 1);
            multisampleSource.setName (TagDetector.toCamelCase (name));

            return Collections.singletonList (multisampleSource);
        }
        catch (final IOException | ParseException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
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

            final DefaultPerformanceSource performanceSource = new DefaultPerformanceSource ();
            performanceSource.setName (FileUtils.getNameWithoutType (file));

            for (final AkmPart part: akmFile.getParts ())
            {
                final String presetName = part.getPresetName ().trim ();
                if (presetName.isBlank ())
                    continue;
                final File presetFile = new File (file.getParentFile (), presetName + ".AKP");
                if (!presetFile.exists ())
                {
                    this.notifier.logError ("IDS_NOTIFY_ERR_PROGRAM_DOES_NOT_EXIST", presetFile.getAbsolutePath ());
                    continue;
                }

                final IInstrumentSource instrumentSource = this.readPresetFileAsInstrument (presetFile, part);
                if (instrumentSource != null)
                    performanceSource.addInstrument (instrumentSource);
            }

            return Collections.singletonList (performanceSource);
        }
        catch (final IOException | ParseException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private IInstrumentSource readPresetFileAsInstrument (final File sourceFile, final AkmPart part)
    {
        if (this.waitForDelivery ())
            return null;

        final List<IMultisampleSource> multisampleSources = this.createMultisample (sourceFile, false);
        if (multisampleSources.isEmpty ())
            return null;

        final IInstrumentSource instrumentSource = new DefaultInstrumentSource (multisampleSources.get (0), part.getMidiChannel () % 16);
        instrumentSource.setName (part.getPresetName ());
        instrumentSource.setClipKeyLow (part.getLowKey ());
        instrumentSource.setClipKeyHigh (part.getHighKey ());

        final List<IGroup> groups = instrumentSource.getMultisampleSource ().getGroups ();
        if (groups.isEmpty ())
            return null;

        final double panningFactor = part.getPanning () / 50.0 * 2.0;
        final double volumeFactor = part.getVolume () / 100.0;
        if (panningFactor != 0 || volumeFactor != 1)
            for (final ISampleZone zone: groups.get (0).getSampleZones ())
            {
                zone.setPanning (Math.clamp (zone.getPanning () + panningFactor, -1.0, 1.0));
                zone.setGain (zone.getGain () * volumeFactor);
            }

        return instrumentSource;
    }
}
