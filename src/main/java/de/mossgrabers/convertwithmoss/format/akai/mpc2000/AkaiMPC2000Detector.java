// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc2000;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.settings.IMetadataConfig;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.hfe.DiskImageBuilder;
import de.mossgrabers.convertwithmoss.file.hfe.HfeFile;
import de.mossgrabers.convertwithmoss.file.hfe.HfeFile.HfeVersion;
import de.mossgrabers.convertwithmoss.file.hfe.Sector;
import de.mossgrabers.convertwithmoss.format.akai.mpc2000.diskformat.AkaiMPC2000DirectoryEntry;
import de.mossgrabers.convertwithmoss.format.akai.mpc2000.diskformat.AkaiMPC2000DiskImage;
import de.mossgrabers.convertwithmoss.format.iso.IsoFormat;
import de.mossgrabers.convertwithmoss.format.iso.IsoFormatIdentifier;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects Akai MPC2000 program files. Files must end with <i>.pgm</i>.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMPC2000Detector extends AbstractDetector<MetadataSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public AkaiMPC2000Detector (final INotifier notifier)
    {
        super ("Akai MPC2000(XL)/3000", "MPC2000", notifier, new MetadataSettingsUI ("MPC2000"), ".pgm", ".iso", ".img", ".hfe");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final String lowerCaseName = sourceFile.getName ().toLowerCase ();

        if (lowerCaseName.endsWith (".iso") || lowerCaseName.endsWith (".img"))
            return this.readIsoFile (sourceFile);

        if (lowerCaseName.endsWith (".hfe"))
            return this.readHfeFile (sourceFile);

        return this.readPgmFile (sourceFile);
    }


    private List<IMultisampleSource> readIsoFile (final File sourceFile)
    {
        final IsoFormat isoFormat = IsoFormatIdentifier.identifyIso (sourceFile);
        if (isoFormat == IsoFormat.AKAI_MPC2000 || isoFormat == IsoFormat.AKAI_MPC2000XL)
        {
            this.notifier.log ("IDS_ISO_PROCESSING_FORMAT", IsoFormat.getName (isoFormat));
            return processAkaiMPC2000Disk (sourceFile, this.sourceFolder, this.notifier, this.settingsConfiguration);
        }
        this.notifier.logError ("IDS_ISO_WRONG_FORMAT", IsoFormat.getName (IsoFormat.AKAI_MPC2000));
        return Collections.emptyList ();
    }


    private List<IMultisampleSource> readHfeFile (final File sourceFile)
    {
        try
        {
            final HfeFile hfeFile = new HfeFile (sourceFile);
            final HfeVersion hfeVersion = hfeFile.getHfeVersion ();
            if (hfeVersion != HfeVersion.VERSION_1)
            {
                this.notifier.logError ("IDS_HFE_VERSION_NOT_SUPPORTED", hfeVersion == HfeVersion.VERSION_2 ? "v2" : "v3");
                return Collections.emptyList ();
            }
            if (hfeFile.getFloppyInterfaceMode () != HfeFile.FLOPPYMODE_IBM_PC_HD)
            {
                this.notifier.logError ("IDS_HFE_CAN_ONLY_DECODE_FLOPPY_MODE", "IBM PC HD");
                return Collections.emptyList ();
            }

            final List<Sector> allSectors = hfeFile.decodeMfmSectors ();
            final byte [] imgData = DiskImageBuilder.buildImage (allSectors, 80, 2, 18, 512);

            final IsoFormat isoFormat = IsoFormatIdentifier.identifyIso (imgData);
            if (isoFormat == IsoFormat.AKAI_MPC2000 || isoFormat == IsoFormat.AKAI_MPC2000XL)
            {
                this.notifier.log ("IDS_ISO_PROCESSING_FORMAT", IsoFormat.getName (isoFormat));
                return processAkaiMPC2000Disk (imgData, this.sourceFolder, sourceFile, this.notifier, this.settingsConfiguration);
            }

            this.notifier.logError ("IDS_ISO_WRONG_FORMAT", IsoFormat.getName (IsoFormat.AKAI_MPC2000));
            return Collections.emptyList ();
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private List<IMultisampleSource> readPgmFile (final File sourceFile)
    {
        try (final FileInputStream input = new FileInputStream (sourceFile))
        {
            final AkaiMPC2000Program program = new AkaiMPC2000Program (input);
            final String programName = FileUtils.getNameWithoutType (sourceFile).trim ();
            final File parentFolder = sourceFile.getParentFile ();
            final String [] parts = AudioFileUtils.createPathParts (parentFolder, this.sourceFolder, programName);

            final Map<String, ISampleData> samples = detectSamples (parentFolder, program.getSampleNames ());
            final AkaMPC2000ProgramConverter converter = new AkaMPC2000ProgramConverter (this.notifier, this.settingsConfiguration);
            return Collections.singletonList (converter.createMultiSample (sourceFile, parts, program, samples, programName));
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private Map<String, ISampleData> detectSamples (final File parentFolder, final List<String> sampleNames) throws IOException
    {
        final Map<String, ISampleData> samples = new HashMap<> ();
        for (String sampleName: sampleNames)
        {
            if (sampleName == null || sampleName.isBlank ())
                continue;

            File sampleFile = findSample (parentFolder, sampleName);
            if (sampleFile == null)
            {
                this.notifier.logError ("IDS_ISO_SAMPLE_NOT_FOUND", sampleName);
                continue;
            }

            if (sampleFile.getName ().toLowerCase ().endsWith (".wav"))
                samples.put (sampleName, new WavFileSampleData (sampleFile));
            else
                samples.put (sampleName, new AkaiMPC2000SampleData (sampleFile));
        }
        return samples;
    }


    private static File findSample (final File parentFolder, final String sampleName)
    {
        File sampleFile = new File (parentFolder, sampleName + ".wav");
        if (sampleFile.exists ())
            return sampleFile;
        sampleFile = new File (parentFolder, sampleName + ".WAV");
        if (sampleFile.exists ())
            return sampleFile;
        sampleFile = new File (parentFolder, sampleName + ".snd");
        if (sampleFile.exists ())
            return sampleFile;
        sampleFile = new File (parentFolder, sampleName + ".SND");
        if (sampleFile.exists ())
            return sampleFile;
        return null;
    }


    /**
     * Process an MPC2000 ISO file.
     * 
     * @param sourceFile The ISO file
     * @param sourceFolder The source folder
     * @param notifier The notifier
     * @param configuration The metadata configuration
     * @return The detected multi-sample sources
     */
    public static List<IMultisampleSource> processAkaiMPC2000Disk (final File sourceFile, final File sourceFolder, final INotifier notifier, final IMetadataConfig configuration)
    {
        try
        {
            final byte [] diskImageData = Files.readAllBytes (sourceFile.toPath ());
            return processAkaiMPC2000Disk (diskImageData, sourceFolder, sourceFile, notifier, configuration);
        }
        catch (final IOException ex)
        {
            notifier.logError ("IDS_ISO_COULD_NOT_PROCESS", ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Process an MPC2000 ISO file.
     * 
     * @param diskImageData The data of an ISO file
     * @param sourceFolder The source folder
     * @param sourceFile The source file
     * @param notifier The notifier
     * @param configuration The metadata configuration
     * @return The detected multi-sample sources
     */
    public static List<IMultisampleSource> processAkaiMPC2000Disk (final byte [] diskImageData, final File sourceFolder, final File sourceFile, final INotifier notifier, final IMetadataConfig configuration)
    {
        final List<IMultisampleSource> multiSampleSources = new ArrayList<> ();
        try
        {
            final AkaiMPC2000DiskImage diskImage = new AkaiMPC2000DiskImage (diskImageData);
            final List<AkaiMPC2000Program> programs = new ArrayList<> ();
            final Map<String, ISampleData> samples = new HashMap<> ();

            final List<AkaiMPC2000DirectoryEntry> entries = diskImage.getEntries ();
            for (final AkaiMPC2000DirectoryEntry entry: entries)
            {
                final byte [] file = diskImage.readFile (entry, notifier);
                try (final ByteArrayInputStream input = new ByteArrayInputStream (file))
                {
                    switch (entry.getExtension ().toLowerCase ())
                    {
                        case "pgm":
                            programs.add (new AkaiMPC2000Program (input));
                            break;

                        case "snd":
                            final AkaiMPC2000SampleData sndSampleData = new AkaiMPC2000SampleData (input);
                            samples.put (sndSampleData.getSndFile ().getName (), sndSampleData);
                            break;

                        case "wav":
                            samples.put (entry.getName (), new WavFileSampleData (input));
                            break;

                        default:
                            notifier.logError ("IDS_MPC2000_UNSUPPORTED_FILE_FORMAT", entry.getExtension ());
                            break;
                    }
                }
            }

            final AkaMPC2000ProgramConverter converter = new AkaMPC2000ProgramConverter (notifier, configuration);
            final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), sourceFolder, sourceFile.getName ());
            for (final AkaiMPC2000Program program: programs)
                multiSampleSources.add (converter.createMultiSample (sourceFile, parts, program, samples, program.getProgramName ()));

            notifier.log ("IDS_NOTIFY_LINE_FEED");
        }
        catch (final IOException ex)
        {
            notifier.logError ("IDS_ISO_COULD_NOT_PROCESS", ex);
        }

        return multiSampleSources;
    }
}
