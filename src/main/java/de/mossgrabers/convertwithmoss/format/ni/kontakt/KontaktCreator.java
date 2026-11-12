// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.detector.DefaultInstrumentSource;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.IKontaktFormat;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt1.Kontakt1Format;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt2.Kontakt2Format;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.Kontakt5Format;
import de.mossgrabers.tools.StringUtils;


/**
 * Creator for Native Instruments Kontakt NKI files.
 *
 * @author Jürgen Moßgraber
 */
public class KontaktCreator extends AbstractWavCreator<KontaktCreatorUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public KontaktCreator (final INotifier notifier)
    {
        super ("NI Kontakt", "Nki", notifier, new KontaktCreatorUI ("Nki"));
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPresetLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPerformances ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        if (multisampleSource.getNonEmptyGroups (false).isEmpty ())
        {
            this.notifier.logError ("IDS_ERR_NO_GROUPS_IN_SOURCE");
            return;
        }

        final IKontaktFormat kontaktType = this.createKontaktFormat ();

        final String multisampleName = this.createSafeFilename (multisampleSource.getName ());
        final File multiFile = this.createUniqueFilename (destinationFolder, multisampleName, "nki");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        // First, store all samples
        final String safeSampleFolderName = multisampleName + FOLDER_POSTFIX;
        final File sampleFolder = new File (destinationFolder, safeSampleFolderName);
        safeCreateDirectory (sampleFolder);
        final List<File> sampleFiles = this.writeSamples (sampleFolder, multisampleSource);

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            kontaktType.writeNKI (out, safeSampleFolderName, multisampleSource, calculateSampleSize (sampleFiles));
        }

        this.progress.notifyDone ();
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        if (multisampleSources.isEmpty ())
            return;

        final List<IInstrumentSource> instrumentSources = new ArrayList<> ();
        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            final IInstrumentSource instrumentSource = new DefaultInstrumentSource (multisampleSource, 0);
            instrumentSource.setName (multisampleSource.getName ());
            instrumentSources.add (instrumentSource);
        }

        this.createNKM (destinationFolder, instrumentSources, libraryName);
    }


    /** {@inheritDoc} */
    @Override
    public void createPerformance (final File destinationFolder, final IPerformanceSource performanceSource) throws IOException
    {
        final List<IInstrumentSource> instruments = performanceSource.getInstruments ();
        if (instruments.isEmpty ())
            return;

        final String libraryName = SafeFileNames.create (performanceSource.getName ());
        this.createNKM (destinationFolder, instruments, libraryName);
    }


    private void createNKM (final File destinationFolder, final List<IInstrumentSource> instrumentSources, final String libraryName) throws IOException
    {
        final IKontaktFormat kontaktType = this.createKontaktFormat ();

        final File multiFile = this.createUniqueFilename (destinationFolder, this.createSafeFilename (libraryName), "nkm");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        final List<IInstrumentSource> safeInstrumentSources = new ArrayList<> (instrumentSources.size ());
        for (final IInstrumentSource instrumentSource: instrumentSources)
        {
            if (instrumentSource.getMultisampleSource ().getNonEmptyGroups (false).isEmpty ())
            {
                this.notifier.logError ("IDS_ERR_NO_GROUPS_IN_INST_SOURCE_SKIPPED", instrumentSource.getName ());
                continue;
            }
            safeInstrumentSources.add (instrumentSource);

            final int numInstruments = safeInstrumentSources.size ();
            if (numInstruments == 64)
            {
                this.notifier.logError ("IDS_ERR_LIMITED_INSTRUMENTS", Integer.toString (64), Integer.toString (numInstruments));
                break;
            }
        }

        final List<File> sampleFiles = new ArrayList<> ();
        final List<String> sampleFilePaths = new ArrayList<> ();
        for (final IInstrumentSource instrumentSource: safeInstrumentSources)
        {
            final IMultisampleSource multisampleSource = instrumentSource.getMultisampleSource ();

            // First, store all samples
            final String multisampleName = this.createSafeFilename (multisampleSource.getName ());
            final String safeSampleFolderName = multisampleName + FOLDER_POSTFIX;
            sampleFilePaths.add (safeSampleFolderName);
            final File sampleFolder = new File (destinationFolder, safeSampleFolderName);
            safeCreateDirectory (sampleFolder);
            sampleFiles.addAll (this.writeSamples (sampleFolder, multisampleSource));
        }

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            kontaktType.writeNKM (out, StringUtils.fixASCII (libraryName), sampleFilePaths, safeInstrumentSources, calculateSampleSize (sampleFiles));
        }

        this.progress.notifyDone ();
    }


    /** {@inheritDoc} */
    @Override
    protected String createSampleFilename (final ISampleZone zone, final int zoneIndex, final String fileEnding)
    {
        return this.createSafeFilename (zone.getName ()) + fileEnding;
    }


    /**
     * Create a safe file name. Kontakt 1 cannot find files with non-ASCII characters in their
     * path, therefore these are replaced if Kontakt 1 is the output format.
     *
     * @param name The name
     * @return The safe file name
     */
    private String createSafeFilename (final String name)
    {
        if (this.settingsConfiguration.getOutputFormat () == KontaktCreatorUI.OutputFormat.KONTAKT_1)
            return SafeFileNames.createAscii (name);
        return SafeFileNames.create (name);
    }


    /**
     * Create the Kontakt format which is selected in the settings.
     *
     * @return The format
     */
    private IKontaktFormat createKontaktFormat ()
    {
        return switch (this.settingsConfiguration.getOutputFormat ())
        {
            case KONTAKT_1 -> new Kontakt1Format (this.notifier, false);
            case KONTAKT_2 -> new Kontakt2Format (this.notifier, false);
            default -> new Kontakt5Format (this.notifier);
        };
    }


    /**
     * Calculates the size of all samples.
     *
     * @param sampleFiles The sample files (must be all WAV files).
     * @return The summed size
     * @throws IOException Could not read a file
     */
    private static int calculateSampleSize (final List<File> sampleFiles) throws IOException
    {
        int size = 0;
        try
        {
            for (final File file: sampleFiles)
                size += new WaveFile (file, true).getDataChunk ().getData ().length;
        }
        catch (final ParseException ex)
        {
            throw new IOException (ex);
        }
        return size;
    }
}