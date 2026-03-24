// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javax.sound.sampled.UnsupportedAudioFileException;

import de.mossgrabers.convertwithmoss.core.algorithm.AudioSampleReducer;
import de.mossgrabers.convertwithmoss.core.algorithm.MultiSampleReducer;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.ICreator;
import de.mossgrabers.convertwithmoss.core.detector.IDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.format.ableton.AbletonCreator;
import de.mossgrabers.convertwithmoss.format.ableton.AbletonDetector;
import de.mossgrabers.convertwithmoss.format.akai.akp.AkpDetector;
import de.mossgrabers.convertwithmoss.format.akai.mpc.xpm.MPCKeygroupCreator;
import de.mossgrabers.convertwithmoss.format.akai.mpc.xpm.MPCKeygroupDetector;
import de.mossgrabers.convertwithmoss.format.akai.mpc.xty.XtyDetector;
import de.mossgrabers.convertwithmoss.format.akai.s3p.AkaiS3pDetector;
import de.mossgrabers.convertwithmoss.format.bitwig.BitwigMultisampleCreator;
import de.mossgrabers.convertwithmoss.format.bitwig.BitwigMultisampleDetector;
import de.mossgrabers.convertwithmoss.format.bliss.BlissCreator;
import de.mossgrabers.convertwithmoss.format.bliss.BlissDetector;
import de.mossgrabers.convertwithmoss.format.decentsampler.DecentSamplerCreator;
import de.mossgrabers.convertwithmoss.format.decentsampler.DecentSamplerDetector;
import de.mossgrabers.convertwithmoss.format.disting.DistingExCreator;
import de.mossgrabers.convertwithmoss.format.disting.DistingExDetector;
import de.mossgrabers.convertwithmoss.format.exs.EXS24Creator;
import de.mossgrabers.convertwithmoss.format.exs.EXS24Detector;
import de.mossgrabers.convertwithmoss.format.iso.IsoDetector;
import de.mossgrabers.convertwithmoss.format.kmp.KMPCreator;
import de.mossgrabers.convertwithmoss.format.kmp.KMPDetector;
import de.mossgrabers.convertwithmoss.format.korgmultisample.KorgmultisampleCreator;
import de.mossgrabers.convertwithmoss.format.korgmultisample.KorgmultisampleDetector;
import de.mossgrabers.convertwithmoss.format.music1010.bento.BentoCreator;
import de.mossgrabers.convertwithmoss.format.music1010.bento.BentoDetector;
import de.mossgrabers.convertwithmoss.format.music1010.blackbox.Music1010Creator;
import de.mossgrabers.convertwithmoss.format.music1010.blackbox.Music1010Detector;
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
    private DetectSettings                 detectionSettings;
    private boolean                        onlyAnalyse;

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

        // Workaround for attribute limit of 200 which e.g. causes issues with TAL Sampler format
        System.setProperty ("jdk.xml.elementAttributeLimit", "1000");

        this.detectors = new IDetector []
        {
            new BentoDetector (notifier),
            new Music1010Detector (notifier),
            new AbletonDetector (notifier),
            new AkpDetector (notifier),
            new MPCKeygroupDetector (notifier),
            new XtyDetector (notifier),
            new AkaiS3pDetector (notifier),
            new BitwigMultisampleDetector (notifier),
            new BlissDetector (notifier),
            new TX16WxDetector (notifier),
            new DecentSamplerDetector (notifier),
            new DistingExDetector (notifier),
            new IsoDetector (notifier),
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
            new BentoCreator (notifier),
            new Music1010Creator (notifier),
            new AbletonCreator (notifier),
            new MPCKeygroupCreator (notifier),
            new BitwigMultisampleCreator (notifier),
            new BlissCreator (notifier),
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
     * @param detectionSettings The settings for the detection process
     * @param detectPerformances If true, performances are detected otherwise presets
     * @param onlyAnalyse True, if no files should be created
     */
    public void detect (final IDetector<?> detector, final ICreator<?> creator, final DetectSettings detectionSettings, final boolean detectPerformances, final boolean onlyAnalyse)
    {
        this.detector = detector;
        this.creator = creator;
        this.detectionSettings = detectionSettings;
        this.onlyAnalyse = onlyAnalyse;

        this.collectedPresetSources.clear ();
        this.collectedPerformanceSources.clear ();

        this.notifier.log ("TITLE");
        this.notifier.log ("IDS_NOTIFY_DETECTING", detector.getName (), creator.getName ());
        this.creator.clearCancelled ();
        this.detector.detect (detectionSettings.sourceFolder, new MultisampleSourceConsumer (), new PerformanceSourceConsumer (), detectPerformances);
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
            try
            {
                if (!this.collectedPresetSources.isEmpty ())
                {
                    final String name = this.getPresetLibraryName (this.collectedPresetSources, this.detectionSettings.libraryName);
                    this.creator.createPresetLibrary (this.detectionSettings.outputFolder, this.collectedPresetSources, name);
                }
                else if (!this.collectedPerformanceSources.isEmpty ())
                {
                    final String name = this.getPerformanceLibraryName (this.collectedPerformanceSources, this.detectionSettings.libraryName);
                    this.creator.createPerformanceLibrary (this.detectionSettings.outputFolder, this.collectedPerformanceSources, name);
                }
            }
            catch (final IOException | RuntimeException | OutOfMemoryError ex)
            {
                this.notifier.logError ("IDS_NOTIFY_SAVE_FAILED", ex);
            }

        this.notifier.log (cancelled ? "IDS_NOTIFY_CANCELLED" : "IDS_NOTIFY_FINISHED");
    }


    private void acceptMultisample (final IMultisampleSource multisampleSource)
    {
        if (this.detector.isCancelled ())
            return;

        this.processSource (multisampleSource);

        if (this.detectionSettings.wantsMultipleFiles)
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
            final File multisampleOutputFolder = calcOutputFolder (this.detectionSettings.outputFolder, multisampleSource.getSubPath (), this.detectionSettings.createFolderStructure);
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

        for (final IInstrumentSource instrumentSource: instrumentSources)
            this.processSource (instrumentSource.getMultisampleSource ());

        if (this.detectionSettings.wantsMultipleFiles)
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
            final File multisampleOutputFolder = calcOutputFolder (this.detectionSettings.outputFolder, instrumentSources.get (0).getMultisampleSource ().getSubPath (), this.detectionSettings.createFolderStructure);
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


    private void processSource (final IMultisampleSource multisampleSource)
    {
        ensureSafeSampleFileNames (multisampleSource);
        this.processSamples (multisampleSource);
        this.applyRenaming (multisampleSource);
        this.applyDefaultEnvelope (multisampleSource);
    }


    private void processSamples (final IMultisampleSource multisampleSource)
    {
        if (this.onlyAnalyse || !this.detectionSettings.needsProcessing ())
            return;

        this.notifier.log ("IDS_PROCESSING_PROCESS");
        try
        {
            final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);

            ////////////////////////////////////////////////////////////////////////////////////////////////
            // Combine split-mono samples to stereo samples if necessary for further processing

            final boolean hasMaximumNumberOfSamples = this.detectionSettings.maxNumberOfSamples > 0;
            if ((hasMaximumNumberOfSamples || this.detectionSettings.enableMakeMono) && ZoneChannels.detectChannelConfiguration (groups) == ZoneChannels.SPLIT_STEREO)
            {
                this.notifier.log ("IDS_PROCESSING_COMBINE_TO_STEREO");
                final Optional<IGroup> stereoGroup = ZoneChannels.combineSplitStereo (groups);
                if (stereoGroup.isPresent ())
                {
                    groups.clear ();
                    groups.add (stereoGroup.get ());
                }
                else
                    this.notifier.logError ("IDS_NOTIFY_NOT_COMBINED_TO_STEREO");
            }

            ////////////////////////////////////////////////////////////////////////////////////////////////
            // Reduce the number of samples if necessary

            if (hasMaximumNumberOfSamples && MultiSampleReducer.reduce (groups, this.detectionSettings.maxNumberOfSamples) > 0)
            {
                this.notifier.log ("IDS_PROCESSING_REDUCE_SAMPLES", Integer.toString (this.detectionSettings.maxNumberOfSamples));
                final List<IGroup> finalGroups = new ArrayList<> ();
                for (final IGroup group: groups)
                    if (!group.getSampleZones ().isEmpty ())
                        finalGroups.add (group);
                groups.clear ();
                groups.addAll (finalGroups);
                this.notifier.log ("IDS_NOTIFY_REDUCED_TO_NUM_SAMPLES", Integer.toString (this.detectionSettings.maxNumberOfSamples));
            }
            multisampleSource.setGroups (groups);

            final List<ISampleZone> sampleZones = new ArrayList<> ();
            for (final IGroup group: groups)
                sampleZones.addAll (group.getSampleZones ());

            if (this.detectionSettings.enableMakeMono)
                this.notifier.log ("IDS_PROCESSING_MAKE_MONO");
            if (this.detectionSettings.enableTrimSample)
                this.notifier.log ("IDS_PROCESSING_TRIM");
            if (this.detectionSettings.reduceBitDepth > 0)
                this.notifier.log ("IDS_PROCESSING_REDUCE_BIT_DEPTH_TO", Integer.toString (this.detectionSettings.reduceBitDepth));
            if (this.detectionSettings.reduceFrequency > 0)
                this.notifier.log ("IDS_PROCESSING_REDUCE_FREQUENCY_TO", Integer.toString (this.detectionSettings.reduceFrequency));
            if (this.detectionSettings.enableNormalize)
                this.notifier.log ("IDS_PROCESSING_NORMALIZING");
            this.notifier.log ("IDS_NOTIFY_LINE_FEED");
            AudioSampleReducer.reduceSamples (sampleZones, this.detectionSettings.enableMakeMono, this.detectionSettings.enableTrimSample, this.detectionSettings.reduceBitDepth, this.detectionSettings.reduceFrequency, this.detectionSettings.enableNormalize);
        }
        catch (final IOException | UnsupportedAudioFileException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_COULD_NOT_RESAMPLE", ex);
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
        if (this.detectionSettings.csvRenameFile == null || this.detectionSettings.csvRenameFile.isEmpty ())
            return;

        final String sourceName = multisampleSource.getName ();
        final String targetName = this.detectionSettings.csvRenameFile.getMapping (sourceName);
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
        final String name = this.detectionSettings.wantsMultipleFiles && !libraryName.isEmpty () ? libraryName : multisampleSources.get (0).getName ();
        return AbstractCreator.createSafeFilename (name);
    }


    private String getPerformanceLibraryName (final List<IPerformanceSource> performanceSources, final String libraryName)
    {
        final String name = this.detectionSettings.wantsMultipleFiles && !libraryName.isEmpty () ? libraryName : performanceSources.get (0).getName ();
        return AbstractCreator.createSafeFilename (name);
    }


    private class MultisampleSourceConsumer implements Consumer<IMultisampleSource>
    {
        @Override
        public void accept (final IMultisampleSource multisampleSource)
        {
            ConverterBackend.this.acceptMultisample (multisampleSource);
        }
    }


    private class PerformanceSourceConsumer implements Consumer<IPerformanceSource>
    {
        @Override
        public void accept (final IPerformanceSource performanceSource)
        {
            ConverterBackend.this.acceptPerformance (performanceSource);
        }
    }
}
