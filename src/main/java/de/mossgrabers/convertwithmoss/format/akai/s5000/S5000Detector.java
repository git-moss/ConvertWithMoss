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
public class S5000Detector extends AbstractDetector<MetadataSettingsUI>
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
    public S5000Detector (final INotifier notifier)
    {
        super ("Akai S5000/S6000", "S5000", notifier, new MetadataSettingsUI ("AKP"));
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
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final AkpFile akpFile = new AkpFile (file);
            final IMultisampleSource multisampleSource = akpFile.createMultisampleSource (this.sourceFolder, this.settingsConfiguration);

            // Check and set wave file sample data
            boolean hasError = false;
            for (final IGroup group: multisampleSource.getGroups ())
            {
                for (final ISampleZone sampleZone: group.getSampleZones ())
                {
                    final File sampleFile = new File (file.getParentFile (), sampleZone.getName () + ".wav");
                    if (AudioFileUtils.checkSampleFile (sampleFile, this.notifier))
                    {
                        final WavFileSampleData sampleData = new WavFileSampleData (sampleFile);
                        sampleZone.setSampleData (sampleData);
                        // If there is a loop required, read it from the sample chunk
                        final boolean addLoops = !sampleZone.getLoops ().isEmpty ();
                        sampleZone.getLoops ().clear ();
                        sampleData.addZoneData (sampleZone, true, addLoops);
                        // Root key is off by 1 octave
                        sampleZone.setKeyRoot (sampleZone.getKeyRoot () + 12);
                    }
                    else
                        hasError = true;
                }
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

            // this.settingsConfiguration

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

        final List<IMultisampleSource> multisampleSources = readPresetFile (sourceFile);
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
        {
            for (final ISampleZone zone: groups.get (0).getSampleZones ())
            {
                zone.setPanning (Math.clamp (zone.getPanning () + panningFactor, -1.0, 1.0));
                zone.setGain (zone.getGain () * volumeFactor);
            }
        }

        return instrumentSource;
    }
}
