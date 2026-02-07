// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.detector.DefaultInstrumentSource;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.IKontaktFormat;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt1.Kontakt1Format;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.Kontakt5Format;


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
        super ("Kontakt NKI", "Nki", notifier, new KontaktCreatorUI ("Nki"));
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

        final boolean isKontakt1 = this.settingsConfiguration.isKontakt1 ();
        final IKontaktFormat kontaktType = isKontakt1 ? new Kontakt1Format (this.notifier, false) : new Kontakt5Format (this.notifier);

        final String multisampleName = createSafeFilename (multisampleSource.getName ());
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

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
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
            final DefaultInstrumentSource instrumentSource = new DefaultInstrumentSource (multisampleSource, 0);
            instrumentSource.setName (multisampleSource.getName ());
            instrumentSources.add (instrumentSource);
        }

        final boolean isKontakt1 = this.settingsConfiguration.isKontakt1 ();
        final IKontaktFormat kontaktType = isKontakt1 ? new Kontakt1Format (this.notifier, false) : new Kontakt5Format (this.notifier);

        this.createNKM (destinationFolder, instrumentSources, libraryName, kontaktType);
    }


    /** {@inheritDoc} */
    @Override
    public void createPerformance (final File destinationFolder, final IPerformanceSource performanceSource) throws IOException
    {
        final List<IInstrumentSource> instruments = performanceSource.getInstruments ();
        if (instruments.isEmpty ())
            return;

        final String libraryName = AbstractCreator.createSafeFilename (performanceSource.getName ());
        final boolean isKontakt1 = this.settingsConfiguration.isKontakt1 ();
        final IKontaktFormat kontaktType = isKontakt1 ? new Kontakt1Format (this.notifier, false) : new Kontakt5Format (this.notifier);
        this.createNKM (destinationFolder, instruments, libraryName, kontaktType);
    }


    private void createNKM (final File destinationFolder, final List<IInstrumentSource> instrumentSources, final String libraryName, final IKontaktFormat kontaktType) throws IOException, FileNotFoundException
    {
        final File multiFile = this.createUniqueFilename (destinationFolder, libraryName, "nkm");
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
            final String multisampleName = createSafeFilename (multisampleSource.getName ());
            final String safeSampleFolderName = multisampleName + FOLDER_POSTFIX;
            sampleFilePaths.add (safeSampleFolderName);
            final File sampleFolder = new File (destinationFolder, safeSampleFolderName);
            safeCreateDirectory (sampleFolder);
            sampleFiles.addAll (this.writeSamples (sampleFolder, multisampleSource));
        }

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            kontaktType.writeNKM (out, sampleFilePaths, safeInstrumentSources, calculateSampleSize (sampleFiles));
        }

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
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