// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.ICreator;
import de.mossgrabers.convertwithmoss.core.detector.IDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.file.CSVRenameFile;
import de.mossgrabers.convertwithmoss.format.ableton.AbletonCreator;
import de.mossgrabers.convertwithmoss.format.ableton.AbletonDetector;
import de.mossgrabers.convertwithmoss.format.akai.MPCKeygroupCreator;
import de.mossgrabers.convertwithmoss.format.akai.MPCKeygroupDetector;
import de.mossgrabers.convertwithmoss.format.bitwig.BitwigMultisampleCreator;
import de.mossgrabers.convertwithmoss.format.bitwig.BitwigMultisampleDetector;
import de.mossgrabers.convertwithmoss.format.decentsampler.DecentSamplerCreator;
import de.mossgrabers.convertwithmoss.format.decentsampler.DecentSamplerDetector;
import de.mossgrabers.convertwithmoss.format.disting.DistingExCreator;
import de.mossgrabers.convertwithmoss.format.disting.DistingExDetector;
import de.mossgrabers.convertwithmoss.format.exs.EXS24Creator;
import de.mossgrabers.convertwithmoss.format.exs.EXS24Detector;
import de.mossgrabers.convertwithmoss.format.kmp.KMPCreator;
import de.mossgrabers.convertwithmoss.format.kmp.KMPDetector;
import de.mossgrabers.convertwithmoss.format.korgmultisample.KorgmultisampleCreator;
import de.mossgrabers.convertwithmoss.format.korgmultisample.KorgmultisampleDetector;
import de.mossgrabers.convertwithmoss.format.music1010.Music1010Creator;
import de.mossgrabers.convertwithmoss.format.music1010.Music1010Detector;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.KontaktCreator;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.KontaktDetector;
import de.mossgrabers.convertwithmoss.format.ni.maschine.MaschineCreator;
import de.mossgrabers.convertwithmoss.format.ni.maschine.MaschineDetector;
import de.mossgrabers.convertwithmoss.format.samplefile.SampleFileDetector;
import de.mossgrabers.convertwithmoss.format.sf2.Sf2Creator;
import de.mossgrabers.convertwithmoss.format.sf2.Sf2Detector;
import de.mossgrabers.convertwithmoss.format.sfz.SfzCreator;
import de.mossgrabers.convertwithmoss.format.sfz.SfzDetector;
import de.mossgrabers.convertwithmoss.format.sxt.SxtCreator;
import de.mossgrabers.convertwithmoss.format.sxt.SxtDetector;
import de.mossgrabers.convertwithmoss.format.tal.TALSamplerCreator;
import de.mossgrabers.convertwithmoss.format.tal.TALSamplerDetector;
import de.mossgrabers.convertwithmoss.format.tx16wx.TX16WxCreator;
import de.mossgrabers.convertwithmoss.format.tx16wx.TX16WxDetector;
import de.mossgrabers.convertwithmoss.format.waldorf.qpat.WaldorfQpatCreator;
import de.mossgrabers.convertwithmoss.format.waldorf.qpat.WaldorfQpatDetector;
import de.mossgrabers.convertwithmoss.format.wav.WavCreator;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.YamahaYsfcCreator;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.YamahaYsfcDetector;
import de.mossgrabers.tools.ui.Functions;


/**
 * The back-end for the conversion process.
 *
 * @author Jürgen Moßgraber
 */
public class ConverterBackend
{
    protected INotifier                    notifier;
    protected final IDetector<?> []        detectors;
    protected final ICreator<?> []         creators;

    private IDetector<?>                   detector;
    private ICreator<?>                    creator;
    private File                           outputFolder;
    private CSVRenameFile                  csvRenameFile;
    private String                         libraryName;
    private boolean                        wantsMultipleFiles;
    private boolean                        onlyAnalyse;
    private boolean                        createFolderStructure;

    private final List<IMultisampleSource> collectedPresetSources      = new ArrayList<> ();
    private final List<IPerformanceSource> collectedPerformanceSources = new ArrayList<> ();


    /**
     * Constructor.
     * 
     * @param notifier The notifier for log-feedback
     */
    public ConverterBackend (final INotifier notifier)
    {
        this.notifier = notifier;

        this.detectors = new IDetector []
        {
            new Music1010Detector (notifier),
            new AbletonDetector (notifier),
            new MPCKeygroupDetector (notifier),
            new BitwigMultisampleDetector (notifier),
            new TX16WxDetector (notifier),
            new DecentSamplerDetector (notifier),
            new DistingExDetector (notifier),
            new KontaktDetector (notifier),
            new KMPDetector (notifier),
            new KorgmultisampleDetector (notifier),
            new EXS24Detector (notifier),
            new MaschineDetector (notifier),
            new SxtDetector (notifier),
            new SampleFileDetector (notifier),
            new SfzDetector (notifier),
            new Sf2Detector (notifier),
            new TALSamplerDetector (notifier),
            new WaldorfQpatDetector (notifier),
            new YamahaYsfcDetector (notifier)
        };

        this.creators = new ICreator []
        {
            new Music1010Creator (notifier),
            new AbletonCreator (notifier),
            new MPCKeygroupCreator (notifier),
            new BitwigMultisampleCreator (notifier),
            new TX16WxCreator (notifier),
            new DecentSamplerCreator (notifier),
            new DistingExCreator (notifier),
            new KontaktCreator (notifier),
            new KMPCreator (notifier),
            new KorgmultisampleCreator (notifier),
            new EXS24Creator (notifier),
            new MaschineCreator (notifier),
            new SxtCreator (notifier),
            new WavCreator (notifier),
            new SfzCreator (notifier),
            new Sf2Creator (notifier),
            new TALSamplerCreator (notifier),
            new WaldorfQpatCreator (notifier),
            new YamahaYsfcCreator (notifier)
        };
    }


    /**
     * Get all detectors.
     * 
     * @return The detectors
     */
    public IDetector<?> [] getDetectors ()
    {
        return this.detectors;
    }


    /**
     * Get all creators.
     * 
     * @return The creators
     */
    public ICreator<?> [] getCreators ()
    {
        return this.creators;
    }


    /**
     * Start the detection.
     * 
     * @param detector The file detector
     * @param creator The file creator
     * @param sourceFolder The folder where to start the detection process
     * @param outputFolder Where to write the result to
     * @param csvRenameFile If renaming is required
     * @param libraryName The name to use in case that a library will be created
     * @param detectPerformances If true, performances are detected otherwise presets
     * @param wantsMultipleFiles True, if all files should be returned at once
     * @param createFolderStructure True, if the source folder structure should be replicated in the
     *            output folder
     * @param onlyAnalyse True, if no files should be created
     */
    public void detect (final IDetector<?> detector, final ICreator<?> creator, final File sourceFolder, final File outputFolder, final CSVRenameFile csvRenameFile, final String libraryName, final boolean detectPerformances, final boolean wantsMultipleFiles, final boolean createFolderStructure, final boolean onlyAnalyse)
    {
        this.detector = detector;
        this.creator = creator;
        this.outputFolder = outputFolder;
        this.csvRenameFile = csvRenameFile;
        this.libraryName = libraryName;
        this.wantsMultipleFiles = wantsMultipleFiles;
        this.onlyAnalyse = onlyAnalyse;
        this.createFolderStructure = createFolderStructure;

        this.collectedPresetSources.clear ();
        this.collectedPerformanceSources.clear ();

        this.notifier.log ("TITLE");
        this.notifier.log ("IDS_NOTIFY_DETECTING", detector.getName (), creator.getName ());
        this.creator.clearCancelled ();
        this.detector.detect (sourceFolder, new MultisampleSourceConsumer (), new PerformanceSourceConsumer (), detectPerformances);
    }


    /**
     * Cancel the execution process.
     */
    public void cancelExecution ()
    {
        this.detector.cancel ();
        this.creator.cancel ();
    }


    /**
     * If multiple files for a library were collected, create the library.
     * 
     * @param cancelled True if the process was cancelled
     */
    public void finish (final boolean cancelled)
    {
        if (!cancelled && !this.onlyAnalyse)
        {
            try
            {
                if (!this.collectedPresetSources.isEmpty ())
                {
                    final String name = this.getPresetLibraryName (this.collectedPresetSources, this.libraryName);
                    this.creator.createPresetLibrary (this.outputFolder, this.collectedPresetSources, name);
                }
                else if (!this.collectedPerformanceSources.isEmpty ())
                {
                    final String name = this.getPerformanceLibraryName (this.collectedPerformanceSources, this.libraryName);
                    this.creator.createPerformanceLibrary (this.outputFolder, this.collectedPerformanceSources, name);
                }
            }
            catch (final IOException | RuntimeException | OutOfMemoryError ex)
            {
                this.notifier.logError ("IDS_NOTIFY_SAVE_FAILED", ex);
            }
        }

        this.notifier.log (cancelled ? "IDS_NOTIFY_CANCELLED" : "IDS_NOTIFY_FINISHED");
    }


    private void acceptMultisample (final IMultisampleSource multisampleSource)
    {
        if (this.detector.isCancelled ())
            return;

        ensureSafeSampleFileNames (multisampleSource);
        this.applyRenaming (multisampleSource);
        this.applyDefaultEnvelope (multisampleSource);

        if (this.wantsMultipleFiles)
        {
            if (!this.onlyAnalyse)
                this.collectedPresetSources.add (multisampleSource);
            this.notifier.log ("IDS_NOTIFY_COLLECTING", multisampleSource.getMappingName ());
            return;
        }

        this.notifier.log ("IDS_NOTIFY_MAPPING", multisampleSource.getMappingName ());

        if (this.onlyAnalyse)
        {
            this.notifier.log ("IDS_NOTIFY_OK");
            return;
        }

        try
        {
            final File multisampleOutputFolder = calcOutputFolder (this.outputFolder, multisampleSource.getSubPath (), this.createFolderStructure);
            this.creator.createPreset (multisampleOutputFolder, multisampleSource);
        }
        catch (final NoSuchFileException | FileNotFoundException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_FILE_NOT_FOUND", ex);
        }
        catch (final IOException | RuntimeException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_SAVE_FAILED", ex);
        }
    }


    private void acceptPerformance (final IPerformanceSource performanceSource)
    {
        if (this.detector.isCancelled ())
            return;

        final List<IInstrumentSource> instrumentSources = performanceSource.getInstruments ();
        if (instrumentSources.isEmpty ())
            return;

        IMultisampleSource multisampleSource;
        for (final IInstrumentSource instrumentSource: instrumentSources)
        {
            multisampleSource = instrumentSource.getMultisampleSource ();
            ensureSafeSampleFileNames (multisampleSource);
            this.applyRenaming (multisampleSource);
            this.applyDefaultEnvelope (multisampleSource);
        }

        if (this.wantsMultipleFiles)
        {
            if (!this.onlyAnalyse)
                this.collectedPerformanceSources.add (performanceSource);
            this.notifier.log ("IDS_NOTIFY_COLLECTING", performanceSource.getName ());
            return;
        }

        if (this.onlyAnalyse)
        {
            this.notifier.log ("IDS_NOTIFY_OK");
            return;
        }

        try
        {
            final File multisampleOutputFolder = calcOutputFolder (this.outputFolder, instrumentSources.get (0).getMultisampleSource ().getSubPath (), this.createFolderStructure);
            this.creator.createPerformance (multisampleOutputFolder, performanceSource);
        }
        catch (final NoSuchFileException | FileNotFoundException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_FILE_NOT_FOUND", ex);
        }
        catch (final IOException | RuntimeException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_SAVE_FAILED", ex);
        }
    }


    /**
     * Remove critical characters from all zone names.
     *
     * @param multisampleSource The multi-sample source
     */
    private static void ensureSafeSampleFileNames (final IMultisampleSource multisampleSource)
    {
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
                zone.setName (AbstractCreator.createSafeFilename (zone.getName ()));
    }


    /**
     * Apply a volume envelopes if none are set based on the category of the multi-sample source.
     *
     * @param multisampleSource The multi-sample source
     */
    private void applyDefaultEnvelope (final IMultisampleSource multisampleSource)
    {
        final String category = multisampleSource.getMetadata ().getCategory ();
        boolean wasSet = false;
        final IEnvelope defaultEnvelope = DefaultEnvelope.getDefaultEnvelope (category);
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final IEnvelope volumeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
                if (!volumeEnvelope.isSet ())
                {
                    volumeEnvelope.set (defaultEnvelope);
                    wasSet = true;
                }
            }
        if (wasSet)
            this.notifier.log ("IDS_NOTIFY_APPLY_DEFAULT_ENVELOPE", category.isBlank () ? "Unknown" : category);
    }


    /**
     * Applies the renaming of a IMultisampleSource according to the renaming table.
     *
     * @param multisampleSource the multi-sample source to be renamed.
     */
    private void applyRenaming (final IMultisampleSource multisampleSource)
    {
        if (this.csvRenameFile == null || this.csvRenameFile.isEmpty ())
            return;

        final String sourceName = multisampleSource.getName ();
        final String targetName = this.csvRenameFile.getMapping (sourceName);
        if (targetName != null)
        {
            this.notifier.log ("IDS_NOTIFY_RENAMING_SOURCE_TO", sourceName, targetName);
            multisampleSource.setName (targetName);
        }
        else
            this.notifier.log ("IDS_NOTIFY_RENAMING_NOT_DEFINED", sourceName);
    }


    /**
     * Creates the file object for the output folder.
     *
     * @param out The top output folder
     * @param parts The sub-folder parts
     * @param createSubFolders If true the sub-folders are created inside of the top output folder
     * @return The destination folder
     * @throws IOException Could not create sub-folders
     */
    private static File calcOutputFolder (final File out, final String [] parts, final boolean createSubFolders) throws IOException
    {
        if (!createSubFolders)
            return out;

        File result = out;
        for (int i = parts.length - 2; i >= 1; i--)
        {
            result = new File (result, parts[i]);
            if (!result.exists () && !result.mkdirs ())
                throw new IOException (Functions.getMessage ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", result.getAbsolutePath ()));
        }
        return result;
    }


    private String getPresetLibraryName (final List<IMultisampleSource> multisampleSources, final String libraryName)
    {
        final String name = this.wantsMultipleFiles && !libraryName.isEmpty () ? libraryName : multisampleSources.get (0).getName ();
        return AbstractCreator.createSafeFilename (name);
    }


    private String getPerformanceLibraryName (final List<IPerformanceSource> performanceSources, final String libraryName)
    {
        final String name = this.wantsMultipleFiles && !libraryName.isEmpty () ? libraryName : performanceSources.get (0).getName ();
        return AbstractCreator.createSafeFilename (name);
    }


    private class MultisampleSourceConsumer implements Consumer<IMultisampleSource>
    {
        @Override
        public void accept (final IMultisampleSource multisampleSource)
        {
            acceptMultisample (multisampleSource);
        }
    }


    private class PerformanceSourceConsumer implements Consumer<IPerformanceSource>
    {
        @Override
        public void accept (final IPerformanceSource performanceSource)
        {
            acceptPerformance (performanceSource);
        }
    }
}
