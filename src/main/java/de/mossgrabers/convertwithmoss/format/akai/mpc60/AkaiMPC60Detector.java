// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc60;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.hfe.DiskImageBuilder;
import de.mossgrabers.convertwithmoss.file.hfe.HfeFile;
import de.mossgrabers.convertwithmoss.file.hfe.HfeFile.HfeVersion;
import de.mossgrabers.convertwithmoss.file.hfe.Sector;
import de.mossgrabers.convertwithmoss.format.akai.mpc2000.diskformat.AkaiMPC2000DirectoryEntry;
import de.mossgrabers.convertwithmoss.format.akai.mpc2000.diskformat.AkaiMPC2000DiskImage;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects Akai MPC60 SET files and disk containers. Files must end with <i>.set</i>, <i>.img</i> or
 * <i>.hfe</i>. A HFE file contains one IMG file. A IMG file contains one SET file.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMPC60Detector extends AbstractDetector<MetadataSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public AkaiMPC60Detector (final INotifier notifier)
    {
        super ("Akai MPC60", "MPC60", notifier, new MetadataSettingsUI ("MPC60"), ".set", ".img", ".hfe");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final String lowerCaseName = sourceFile.getName ().toLowerCase ();

        if (lowerCaseName.endsWith (".img"))
            return this.readImgFile (sourceFile);

        if (lowerCaseName.endsWith (".hfe"))
            return this.readHfeFile (sourceFile);

        return this.readSetFile (sourceFile);
    }


    private List<IMultisampleSource> readHfeFile (final File sourceFile)
    {
        try
        {
            // Decode all sectors
            final HfeFile hfeFile = new HfeFile (sourceFile);
            final HfeVersion hfeVersion = hfeFile.getHfeVersion ();
            if (hfeVersion != HfeVersion.VERSION_1)
            {
                this.notifier.logError ("IDS_HFE_VERSION_NOT_SUPPORTED", hfeVersion == HfeVersion.VERSION_2 ? "v2" : "v3");
                return Collections.emptyList ();
            }
            if (hfeFile.getFloppyInterfaceMode () != HfeFile.FLOPPYMODE_GENERIC_SHUGGART_DD)
            {
                this.notifier.logError ("IDS_HFE_CAN_ONLY_DECODE_FLOPPY_MODE", "Generic Shuggart");
                return Collections.emptyList ();
            }

            final List<Sector> allSectors = hfeFile.decodeMfmSectors ();
            return this.readImgFile (sourceFile, DiskImageBuilder.buildImage (allSectors, 80, 2, 10, 512));
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private List<IMultisampleSource> readImgFile (final File sourceFile)
    {
        try
        {
            return readImgFile (sourceFile, Files.readAllBytes (sourceFile.toPath ()));
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private List<IMultisampleSource> readImgFile (final File sourceFile, final byte [] containerContent) throws IOException
    {
        final AkaiMPC2000DiskImage diskImage = new AkaiMPC2000DiskImage (containerContent);
        final List<byte []> sets = new ArrayList<> ();
        for (final AkaiMPC2000DirectoryEntry entry: diskImage.getEntries ())
        {
            switch (entry.getExtension ().toLowerCase ())
            {
                case "set":
                    sets.add (diskImage.readFile (entry, this.notifier));
                    break;

                // Ignore sequence files
                case "all":
                case "par":
                    break;

                default:
                    this.notifier.logError ("IDS_MPC2000_UNSUPPORTED_FILE_FORMAT", entry.getExtension ());
                    break;
            }
        }

        final List<IMultisampleSource> multiSampleSources = new ArrayList<> ();
        for (final byte [] fileContent: sets)
            multiSampleSources.addAll (this.readSetFile (sourceFile, fileContent));

        return multiSampleSources;
    }


    private List<IMultisampleSource> readSetFile (final File sourceFile)
    {
        try
        {
            return readSetFile (sourceFile, Files.readAllBytes (sourceFile.toPath ()));
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private List<IMultisampleSource> readSetFile (final File sourceFile, final byte [] fileContent)
    {
        final String programName = FileUtils.getNameWithoutType (sourceFile).trim ();
        final File parentFolder = sourceFile.getParentFile ();
        final String [] parts = AudioFileUtils.createPathParts (parentFolder, this.sourceFolder, programName);

        try
        {
            final AkaiMPC60Set setFile = new AkaiMPC60Set (fileContent);
            final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, programName, programName);
            multisampleSource.setGroups (createSampleZones (setFile));

            // Detect metadata
            final String [] tokens = java.util.Arrays.copyOf (parts, parts.length + 1);
            tokens[tokens.length - 1] = programName;
            multisampleSource.getMetadata ().detectMetadata (this.settingsConfiguration, tokens);

            return Collections.singletonList (multisampleSource);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_MPC60_COULD_NOT_READ", ex);
        }

        return Collections.emptyList ();
    }


    private static List<IGroup> createSampleZones (final AkaiMPC60Set setFile) throws IOException
    {
        final IGroup velocityGroup1 = new DefaultGroup ("VelLayer 1");
        final List<IGroup> groups = new ArrayList<> ();
        groups.add (velocityGroup1);

        // Simply assign MIDI notes in ascending order since MIDI notes can be assigned but the
        // default is that they are off and in that case they cannot be assigned
        int note = 36;

        for (final AkaiMPC60Pad pad: setFile.getPads ())
        {
            if (pad.name == null)
                continue;
            final String sampleName = pad.name.trim ();
            if (sampleName.isBlank ())
                continue;

            final ISampleZone sampleZone = createSampleZone (pad, sampleName);
            sampleZone.setKeyLow (note);
            sampleZone.setKeyHigh (note);
            velocityGroup1.addSampleZone (sampleZone);

            note++;
        }

        return groups;
    }


    private static ISampleZone createSampleZone (final AkaiMPC60Pad pad, final String sampleName) throws IOException
    {
        final ISampleZone sampleZone = new DefaultSampleZone (sampleName, pad.sampleData);

        // Pitch
        sampleZone.setTuning (pad.tuning / 100.0);
        sampleZone.setKeyTracking (0);

        // Mixing
        sampleZone.setGain (MathUtils.valueToDb (pad.volume / 127.0));
        sampleZone.setPanning (pad.panning / 127.0 * 2.0 - 1.0);

        final IAudioMetadata audioMetadata = pad.sampleData.getAudioMetadata ();
        final int sampleLength = audioMetadata.getNumberOfSamples ();
        final IEnvelopeModulator amplitudeEnvelopeModulator = sampleZone.getAmplitudeEnvelopeModulator ();
        amplitudeEnvelopeModulator.setSource (convertEnvelope (pad));

        // Play-back
        sampleZone.setStart (0);
        sampleZone.setStop (sampleLength);

        return sampleZone;
    }


    private static IEnvelope convertEnvelope (final AkaiMPC60Pad pad)
    {
        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (toSeconds (pad.attack, false));
        envelope.setDecayTime (toSeconds (pad.decay, true));
        envelope.setSustainLevel (0);
        // This is not fully correct but cannot be mapped otherwise...
        envelope.setReleaseTime (toSeconds (pad.decay, false));
        return envelope;
    }


    private static double toSeconds (final int value, final boolean isLong)
    {
        // No real idea, assume 2 seconds max
        return value / 255.0 * (isLong ? 6.0 : 2.0);
    }
}
