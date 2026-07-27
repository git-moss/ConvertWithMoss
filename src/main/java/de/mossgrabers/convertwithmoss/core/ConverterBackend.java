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

import javax.sound.sampled.UnsupportedAudioFileException;

import de.mossgrabers.convertwithmoss.core.algorithm.AudioSampleReducer;
import de.mossgrabers.convertwithmoss.core.algorithm.LoopZeroSnapper;
import de.mossgrabers.convertwithmoss.core.algorithm.MultiSampleReducer;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.ICreator;
import de.mossgrabers.convertwithmoss.core.detector.IDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.convertwithmoss.format.ableton.AbletonCreator;
import de.mossgrabers.convertwithmoss.format.ableton.AbletonDetector;
import de.mossgrabers.convertwithmoss.format.akai.akp.AkpDetector;
import de.mossgrabers.convertwithmoss.format.akai.mesa.AkaiMesaDetector;
import de.mossgrabers.convertwithmoss.format.akai.mpc.MPCKeygroupCreator;
import de.mossgrabers.convertwithmoss.format.akai.mpc.MPCModernDetector;
import de.mossgrabers.convertwithmoss.format.akai.mpc1000.AkaiMPC1000Detector;
import de.mossgrabers.convertwithmoss.format.akai.mpc2000.AkaiMPC2000Detector;
import de.mossgrabers.convertwithmoss.format.akai.mpc60.AkaiMPC60Detector;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiS1000Detector;
import de.mossgrabers.convertwithmoss.format.akai.s900.AkaiS900Detector;
import de.mossgrabers.convertwithmoss.format.bitwig.BitwigMultisampleCreator;
import de.mossgrabers.convertwithmoss.format.bitwig.BitwigMultisampleDetector;
import de.mossgrabers.convertwithmoss.format.bliss.BlissCreator;
import de.mossgrabers.convertwithmoss.format.bliss.BlissDetector;
import de.mossgrabers.convertwithmoss.format.cmi3.FairlightCmi3Detector;
import de.mossgrabers.convertwithmoss.format.decentsampler.DecentSamplerCreator;
import de.mossgrabers.convertwithmoss.format.decentsampler.DecentSamplerDetector;
import de.mossgrabers.convertwithmoss.format.disting.DistingExCreator;
import de.mossgrabers.convertwithmoss.format.disting.DistingExDetector;
import de.mossgrabers.convertwithmoss.format.dls.DlsDetector;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkMultiCreator;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkMultiDetector;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkPresetCreator;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkPresetDetector;
import de.mossgrabers.convertwithmoss.format.emu.emulator4.Emulator4Creator;
import de.mossgrabers.convertwithmoss.format.emu.emulator4.Emulator4Detector;
import de.mossgrabers.convertwithmoss.format.ensoniq.epsasr.EnsoniqEpsAsrDetector;
import de.mossgrabers.convertwithmoss.format.ensoniq.mirage.MirageDetector;
import de.mossgrabers.convertwithmoss.format.exs.EXS24Creator;
import de.mossgrabers.convertwithmoss.format.exs.EXS24Detector;
import de.mossgrabers.convertwithmoss.format.iso.IsoDetector;
import de.mossgrabers.convertwithmoss.format.kmp.KMPCreator;
import de.mossgrabers.convertwithmoss.format.kmp.KMPDetector;
import de.mossgrabers.convertwithmoss.format.korgmultisample.KorgmultisampleCreator;
import de.mossgrabers.convertwithmoss.format.korgmultisample.KorgmultisampleDetector;
import de.mossgrabers.convertwithmoss.format.kurzweil.KurzweilCreator;
import de.mossgrabers.convertwithmoss.format.kurzweil.KurzweilDetector;
import de.mossgrabers.convertwithmoss.format.music1010.bento.BentoCreator;
import de.mossgrabers.convertwithmoss.format.music1010.bento.BentoDetector;
import de.mossgrabers.convertwithmoss.format.music1010.blackbox.Music1010Creator;
import de.mossgrabers.convertwithmoss.format.music1010.blackbox.Music1010Detector;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.KontaktCreator;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.KontaktDetector;
import de.mossgrabers.convertwithmoss.format.ni.maschine.MaschineCreator;
import de.mossgrabers.convertwithmoss.format.ni.maschine.MaschineDetector;
import de.mossgrabers.convertwithmoss.format.omnisphere.OmnisphereCreator;
import de.mossgrabers.convertwithmoss.format.omnisphere.OmnisphereDetector;
import de.mossgrabers.convertwithmoss.format.polyend.PolyendTrackerCreator;
import de.mossgrabers.convertwithmoss.format.polyend.PolyendTrackerDetector;
import de.mossgrabers.convertwithmoss.format.renoise.RenoiseCreator;
import de.mossgrabers.convertwithmoss.format.renoise.RenoiseDetector;
import de.mossgrabers.convertwithmoss.format.roland.mc707.MC707Creator;
import de.mossgrabers.convertwithmoss.format.roland.mc707.MC707Detector;
import de.mossgrabers.convertwithmoss.format.roland.mv8000.MV8000Creator;
import de.mossgrabers.convertwithmoss.format.roland.mv8000.MV8000Detector;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.S5xxDetector;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770Detector;
import de.mossgrabers.convertwithmoss.format.roland.zencore.ZenCoreCreator;
import de.mossgrabers.convertwithmoss.format.roland.zencore.ZenCoreDetector;
import de.mossgrabers.convertwithmoss.format.samplefile.SampleFileDetector;
import de.mossgrabers.convertwithmoss.format.sf2.Sf2Creator;
import de.mossgrabers.convertwithmoss.format.sf2.Sf2Detector;
import de.mossgrabers.convertwithmoss.format.sfz.SfzCreator;
import de.mossgrabers.convertwithmoss.format.sfz.SfzDetector;
import de.mossgrabers.convertwithmoss.format.sxt.SxtCreator;
import de.mossgrabers.convertwithmoss.format.sxt.SxtDetector;
import de.mossgrabers.convertwithmoss.format.synclavier.SynclavierRegenCreator;
import de.mossgrabers.convertwithmoss.format.synclavier.SynclavierRegenDetector;
import de.mossgrabers.convertwithmoss.format.synthstrom.DelugeCreator;
import de.mossgrabers.convertwithmoss.format.synthstrom.DelugeDetector;
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
    private static final String            IDS_NOTIFY_SAVE_FAILED      = "IDS_NOTIFY_SAVE_FAILED";

    protected INotifier                    notifier;
    protected final List<IDetector<?>>     detectors;
    protected final List<ICreator<?>>      creators;

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

        this.detectors = new ArrayList<> ();
        this.detectors.add (new BentoDetector (notifier));
        this.detectors.add (new Music1010Detector (notifier));
        this.detectors.add (new AbletonDetector (notifier));
        this.detectors.add (new AkpDetector (notifier));
        this.detectors.add (new AkaiMesaDetector (notifier));
        this.detectors.add (new AkaiMPC60Detector (notifier));
        this.detectors.add (new AkaiMPC1000Detector (notifier));
        this.detectors.add (new AkaiMPC2000Detector (notifier));
        this.detectors.add (new MPCModernDetector (notifier));
        this.detectors.add (new AkaiS900Detector (notifier));
        this.detectors.add (new AkaiS1000Detector (notifier));
        this.detectors.add (new BitwigMultisampleDetector (notifier));
        this.detectors.add (new BlissDetector (notifier));
        this.detectors.add (new TX16WxDetector (notifier));
        this.detectors.add (new DecentSamplerDetector (notifier));
        this.detectors.add (new DlsDetector (notifier));
        this.detectors.add (new DistingExDetector (notifier));
        this.detectors.add (new Emulator4Detector (notifier));
        this.detectors.add (new TonverkMultiDetector (notifier));
        this.detectors.add (new TonverkPresetDetector (notifier));
        this.detectors.add (new EnsoniqEpsAsrDetector (notifier));
        this.detectors.add (new MirageDetector (notifier));
        this.detectors.add (new FairlightCmi3Detector (notifier));
        this.detectors.add (new IsoDetector (notifier));
        this.detectors.add (new KMPDetector (notifier));
        this.detectors.add (new KorgmultisampleDetector (notifier));
        this.detectors.add (new KurzweilDetector (notifier));
        this.detectors.add (new EXS24Detector (notifier));
        this.detectors.add (new KontaktDetector (notifier));
        this.detectors.add (new MaschineDetector (notifier));
        this.detectors.add (new PolyendTrackerDetector (notifier));
        this.detectors.add (new RenoiseDetector (notifier));
        this.detectors.add (new MC707Detector (notifier));
        this.detectors.add (new MV8000Detector (notifier));
        this.detectors.add (new S5xxDetector (notifier));
        this.detectors.add (new S770Detector (notifier));
        this.detectors.add (new ZenCoreDetector (notifier));
        this.detectors.add (new SxtDetector (notifier));
        this.detectors.add (new SampleFileDetector (notifier));
        this.detectors.add (new SfzDetector (notifier));
        this.detectors.add (new Sf2Detector (notifier));
        this.detectors.add (new OmnisphereDetector (notifier));
        this.detectors.add (new SynclavierRegenDetector (notifier));
        this.detectors.add (new DelugeDetector (notifier));
        this.detectors.add (new TALSamplerDetector (notifier));
        this.detectors.add (new WaldorfQpatDetector (notifier));
        this.detectors.add (new YamahaYsfcDetector (notifier));

        this.creators = new ArrayList<> ();
        this.creators.add (new BentoCreator (notifier));
        this.creators.add (new Music1010Creator (notifier));
        this.creators.add (new AbletonCreator (notifier));
        this.creators.add (new MPCKeygroupCreator (notifier));
        this.creators.add (new BitwigMultisampleCreator (notifier));
        this.creators.add (new BlissCreator (notifier));
        this.creators.add (new TX16WxCreator (notifier));
        this.creators.add (new DecentSamplerCreator (notifier));
        this.creators.add (new DistingExCreator (notifier));
        this.creators.add (new Emulator4Creator (notifier));
        this.creators.add (new TonverkMultiCreator (notifier));
        this.creators.add (new TonverkPresetCreator (notifier));
        this.creators.add (new KMPCreator (notifier));
        this.creators.add (new KorgmultisampleCreator (notifier));
        this.creators.add (new KurzweilCreator (notifier));
        this.creators.add (new EXS24Creator (notifier));
        this.creators.add (new KontaktCreator (notifier));
        this.creators.add (new MaschineCreator (notifier));
        this.creators.add (new PolyendTrackerCreator (notifier));
        this.creators.add (new RenoiseCreator (notifier));
        this.creators.add (new MC707Creator (notifier));
        this.creators.add (new MV8000Creator (notifier));
        this.creators.add (new ZenCoreCreator (notifier));
        this.creators.add (new SxtCreator (notifier));
        this.creators.add (new WavCreator (notifier));
        this.creators.add (new SfzCreator (notifier));
        this.creators.add (new Sf2Creator (notifier));
        this.creators.add (new OmnisphereCreator (notifier));
        this.creators.add (new SynclavierRegenCreator (notifier));
        this.creators.add (new DelugeCreator (notifier));
        this.creators.add (new TALSamplerCreator (notifier));
        this.creators.add (new WaldorfQpatCreator (notifier));
        this.creators.add (new YamahaYsfcCreator (notifier));
    }


    /**
     * Get all detectors.
     *
     * @return The detectors
     */
    public List<IDetector<? extends ICoreTaskSettings>> getDetectors ()
    {
        return this.detectors;
    }


    /**
     * Get all creators.
     *
     * @return The creators
     */
    public List<ICreator<? extends ICoreTaskSettings>> getCreators ()
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
        if (this.onlyAnalyse)
            this.notifier.log ("IDS_NOTIFY_DETECTING_NO_CONVERSION", detector.getName ());
        else
            this.notifier.log ("IDS_NOTIFY_DETECTING", detector.getName (), creator.getName ());
        this.creator.clearCancelled ();
        this.detector.detect (detectionSettings.sourceFolder, this::acceptMultisample, this::acceptPerformance, detectPerformances);
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
                this.notifier.logError (IDS_NOTIFY_SAVE_FAILED, ex);
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
            this.notifier.log ("IDS_NOTIFY_COLLECTING", multisampleSource.getName ());
            return;
        }

        if (this.onlyAnalyse)
        {
            this.notifier.log ("IDS_NOTIFY_ANALYZE_OK", multisampleSource.getName ());
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
            this.notifier.logError (IDS_NOTIFY_SAVE_FAILED, ex);
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
            this.notifier.logError (IDS_NOTIFY_SAVE_FAILED, ex);
        }
    }


    private void processSource (final IMultisampleSource multisampleSource)
    {
        ensureSafeSampleFileNames (multisampleSource);
        this.processSamples (multisampleSource);
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

            // -----------------------------------------------------------
            // Loop cross-fade

            if (this.detectionSettings.loopCrossfades > 0)
            {
                this.notifier.log ("IDS_PROCESSING_LOOP_CROSSFADE_LOG");
                final double crossfadeFactor = Math.clamp (this.detectionSettings.loopCrossfades - 1L, 0, 100) / 100.0;
                for (final IGroup group: multisampleSource.getGroups ())
                    for (final ISampleZone zone: group.getSampleZones ())
                        for (final ISampleLoop loop: zone.getLoops ())
                            loop.setCrossfade (crossfadeFactor);
            }

            // -----------------------------------------------------------
            // Transpose playback by moving the sample root keys - the key ranges are not changed

            final int transpose = this.detectionSettings.transposeSemitones;
            if (transpose != 0)
            {
                this.notifier.log ("IDS_PROCESSING_TRANSPOSE", Integer.toString (transpose));
                for (final IGroup group: multisampleSource.getGroups ())
                    for (final ISampleZone zone: group.getSampleZones ())
                    {
                        final int keyRoot = zone.getKeyRoot ();
                        if (keyRoot >= 0)
                            zone.setKeyRoot (Math.clamp (keyRoot - (long) transpose, 0, 127));
                    }
            }

            // -----------------------------------------------------------
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

            // -----------------------------------------------------------
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

            // -----------------------------------------------------------
            // Audio processing

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
            if (this.detectionSettings.alwaysResample)
                this.notifier.log ("IDS_PROCESSING_ALWAYS_RESAMPLE");
            if (this.detectionSettings.enableNormalize)
                this.notifier.log ("IDS_PROCESSING_NORMALIZING");
            this.notifier.log ("IDS_NOTIFY_LINE_FEED");
            AudioSampleReducer.reduceSamples (sampleZones, this.detectionSettings.enableMakeMono, this.detectionSettings.enableTrimSample, this.detectionSettings.reduceBitDepth, this.detectionSettings.reduceFrequency, this.detectionSettings.alwaysResample, this.detectionSettings.enableNormalize);

            // -----------------------------------------------------------
            // Snap forward loop boundaries to zero-crossings to remove loop clicks

            if (this.detectionSettings.snapLoopsToZero)
            {
                final int snapped = LoopZeroSnapper.snap (sampleZones);
                this.notifier.log ("IDS_PROCESSING_SNAP_LOOPS", Integer.toString (snapped));
            }
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
}
