// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.soundbox;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipOutputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.XMLUtils;


/**
 * Creator for Audiomodern Soundbox packs. A pack is a ZIP file (entries are stored uncompressed)
 * which contains a description file (pack.amx), one .sbset file per preset and all samples as WAV
 * files without a file ending. Each multi-sample source becomes one preset; up to 4 groups of the
 * source are mapped to the 4 layers of the preset (which play simultaneously - the zones keep their
 * velocity ranges), more groups are merged into the first layer.
 *
 * @author Jürgen Moßgraber
 */
public class SoundboxCreator extends AbstractWavCreator<WavChunkSettingsUI>
{
    private static final String PLUGIN_VERSION  = "1.0.0b19";
    private static final int    MAX_LAYERS      = 4;

    // The default engine state and sequence blobs as written by the Soundbox plug-in
    private static final String ENGINE_STATE    = "13..DPyLybOF....D.HA.";
    private static final String ENGINE_SEQUENCE = "128.MyLS9....7iYlY1OMyLy8LyLy7ilYloOMyLy9zLyL8ilYlwO.........7SyLyjOMyLy8nYlY5yLyLyOMyLS+zLyL6iYlY1OZlYF+.....PyLybOMyLS9nYlY5SyLyrO....+nYlY7yLyLyOMyLS+XlYl8C.....MyLy8zLyL4C";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SoundboxCreator (final INotifier notifier)
    {
        super ("Audiomodern Soundbox", "Soundbox", notifier, new WavChunkSettingsUI ("Soundbox"));
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPresetLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.createPresetLibrary (destinationFolder, Collections.singletonList (multisampleSource), multisampleSource.getName ());
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        if (multisampleSources.isEmpty ())
            return;

        final String fileName = StringUtils.consolidateWhitespace (FileUtils.createSafeFilename (libraryName).replace ('_', ' '), "");

        final File packFile = this.createUniqueFilename (destinationFolder, fileName, "sbpack");
        this.notifier.log ("IDS_NOTIFY_STORING", packFile.getAbsolutePath ());

        final Optional<Document> optionalPackDocument = this.createXMLDocument ();
        if (optionalPackDocument.isEmpty ())
            return;
        final Document packDocument = optionalPackDocument.get ();
        packDocument.setXmlStandalone (true);

        final IMetadata firstMetadata = multisampleSources.get (0).getMetadata ();
        final Element packElement = packDocument.createElement (SoundboxTag.PACK);
        packDocument.appendChild (packElement);
        packElement.setAttribute (SoundboxTag.ATTR_NAME, fileName);
        final String creator = firstMetadata.getCreator ();
        packElement.setAttribute (SoundboxTag.ATTR_AUTHOR, creator == null ? "" : creator);
        final String description = firstMetadata.getDescription ();
        packElement.setAttribute (SoundboxTag.ATTR_DESCRIPTION, description == null ? "" : description);
        packElement.setAttribute ("locked", "0");
        packElement.setAttribute ("sfolder", "");
        packElement.setAttribute ("icon", "");
        packElement.setAttribute ("cover", "");
        packElement.setAttribute ("c0", "ffff6370");
        packElement.setAttribute ("c1", "ffffb46b");
        packElement.setAttribute ("c2", "ff70cf97");
        packElement.setAttribute ("c3", "ff6561fc");
        packElement.setAttribute ("c_gradtop", "ff434153");
        packElement.setAttribute ("c_gradbtm", "ff32313e");
        packElement.setAttribute ("c_btnback", "ff25252f");
        packElement.setAttribute ("panel_alpha", "1.0");

        final Element presetsElement = XMLUtils.addElement (packDocument, packElement, SoundboxTag.PRESETS);
        final Element groupsElement = XMLUtils.addElement (packDocument, packElement, SoundboxTag.GROUPS);
        final Element soundsElement = XMLUtils.addElement (packDocument, packElement, SoundboxTag.SOUNDS);

        final List<String> presetContents = new ArrayList<> ();
        final Set<String> usedGroupNames = new HashSet<> ();
        final Set<String> usedPresetNames = new HashSet<> ();

        // The samples are written to the ZIP while the sound entries are created, so that
        // duplicated sample data can be detected by its content and stored only once. The
        // original packs store the pack description behind the samples as well.
        final Date dateTime = firstMetadata.getCreationDateTime ();
        boolean success = false;
        try (final ZipOutputStream zipOutputStream = new ZipOutputStream (new FileOutputStream (packFile)))
        {
            zipOutputStream.setMethod (ZipOutputStream.STORED);
            final SamplePool samplePool = new SamplePool (zipOutputStream, dateTime);

            for (final IMultisampleSource multisampleSource: multisampleSources)
            {
                // The import in Soundbox creates files named after the preset and its groups,
                // therefore the names must not contain characters which are illegal in file names
                final String presetName = createUniqueName (StringUtils.consolidateWhitespace (FileUtils.createSafeFilename (multisampleSource.getName ()).replace ('_', ' '), "Preset"), usedPresetNames);
                final Element presetNameElement = XMLUtils.addElement (packDocument, presetsElement, SoundboxTag.PRESET_NAME);
                presetNameElement.setAttribute (SoundboxTag.ATTR_NAME, presetName);

                final List<LayerPlan> layerPlans = createLayerPlans (multisampleSource, presetName, usedGroupNames);
                for (final LayerPlan layerPlan: layerPlans)
                    this.createGroup (packDocument, groupsElement, soundsElement, layerPlan, multisampleSource, samplePool);

                final Optional<String> presetContent = this.createPresetDocument (layerPlans);
                if (presetContent.isEmpty ())
                    return;
                presetContents.add (presetContent.get ());
            }

            final Optional<String> packContent = this.createXMLString (packDocument);
            if (packContent.isEmpty ())
                return;

            for (int i = 0; i < presetContents.size (); i++)
                AbstractCreator.storeTextFile (zipOutputStream, SoundboxTag.PRESETS_FOLDER + "p" + i + ".sbset", presetContents.get (i), dateTime);
            AbstractCreator.storeTextFile (zipOutputStream, SoundboxTag.PACK_FILE, packContent.get (), dateTime);
            success = true;
        }
        finally
        {
            if (!success)
                Files.deleteIfExists (packFile.toPath ());
        }

        this.progress.notifyDone ();
    }


    /**
     * Distributes the groups of the multi-sample source to the up to 4 layers of a Soundbox preset.
     * If there are more than 4 groups all zones are merged into one layer. Since all active layers
     * play simultaneously and the zones keep their velocity ranges this only makes a difference for
     * round robin groups which are not supported by the format.
     *
     * @param multisampleSource The multi-sample source
     * @param presetName The name of the preset
     * @param usedGroupNames All already used group names to prevent duplicates
     * @return The layer plans, 1..4 entries
     */
    private static List<LayerPlan> createLayerPlans (final IMultisampleSource multisampleSource, final String presetName, final Set<String> usedGroupNames)
    {
        final List<List<ISampleZone>> roundRobinLists = new ArrayList<> ();
        final List<List<ISampleZone>> normalLists = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
        {
            final List<ISampleZone> zones = new ArrayList<> ();
            for (final ISampleZone zone: group.getSampleZones ())
                if (zone.getTrigger () != TriggerType.RELEASE)
                    zones.add (zone);
            if (zones.isEmpty ())
                continue;
            if (zones.get (0).getPlayLogic () == PlayLogic.ROUND_ROBIN)
                roundRobinLists.add (zones);
            else
                normalLists.add (zones);
        }

        // All round robin groups form the alternating layers of one Soundbox group
        final List<List<List<ISampleZone>>> layerRobins = new ArrayList<> ();
        if (roundRobinLists.size () == 1)
            normalLists.add (0, roundRobinLists.get (0));
        else if (!roundRobinLists.isEmpty ())
        {
            roundRobinLists.sort ((zones1, zones2) -> Integer.compare (getMinimumSequencePosition (zones1), getMinimumSequencePosition (zones2)));
            layerRobins.add (roundRobinLists);
        }

        // Merge all remaining zones into one layer if there are more groups than layers
        final int layersLeft = MAX_LAYERS - layerRobins.size ();
        if (normalLists.size () > layersLeft)
        {
            final List<ISampleZone> allZones = new ArrayList<> ();
            for (final List<ISampleZone> zones: normalLists)
                allZones.addAll (zones);
            normalLists.clear ();
            normalLists.add (allZones);
        }
        for (final List<ISampleZone> zones: normalLists)
            layerRobins.add (Collections.singletonList (zones));

        final List<LayerPlan> layerPlans = new ArrayList<> ();
        for (int layerIndex = 0; layerIndex < layerRobins.size (); layerIndex++)
        {
            final List<List<ISampleZone>> robins = layerRobins.get (layerIndex);
            final LayerPlan layerPlan = new LayerPlan ();
            layerPlan.groupName = createUniqueName ("G" + presetName + (layerIndex == 0 ? "" : " L" + (layerIndex + 1)), usedGroupNames);
            layerPlan.robins = robins;

            final List<ISampleZone> zones = new ArrayList<> ();
            for (final List<ISampleZone> robinZones: robins)
                zones.addAll (robinZones);

            layerPlan.state.active = true;
            layerPlan.state.panning = calculateCommonPanning (zones);
            layerPlan.state.volume = Math.clamp (SoundboxLayerState.DEFAULT_VOLUME * calculateCommonGainRatio (zones), 0, 1);
            layerPlan.settings.fineTuneCents = Math.clamp (calculateCommonFineTuneCents (zones), -100, 100);

            final IEnvelope envelope = zones.get (0).getAmplitudeEnvelopeModulator ().getSource ();
            if (envelope.getAttackTime () >= 0)
                layerPlan.settings.attack = Math.clamp (envelope.getAttackTime () / SoundboxEngineSettings.MAX_ATTACK_SECONDS, 0, 1);
            if (envelope.getDecayTime () >= 0)
                layerPlan.settings.decay = Math.clamp (envelope.getDecayTime () / SoundboxEngineSettings.MAX_RELEASE_SECONDS, 0, 1);
            if (envelope.getSustainLevel () >= 0)
                layerPlan.settings.sustain = envelope.getSustainLevel ();
            if (envelope.getReleaseTime () >= 0)
                layerPlan.settings.release = Math.clamp (envelope.getReleaseTime () / SoundboxEngineSettings.MAX_RELEASE_SECONDS, 0, 1);

            layerPlan.filter = calculateCommonFilter (zones);

            // The voice mode and glide are per instrument in the model and per layer here
            if (multisampleSource.getPolyphony () == 1)
                layerPlan.settings.voiceMode = multisampleSource.isMonophonicLegato () ? SoundboxEngineSettings.VOICE_MODE_LEGATO : SoundboxEngineSettings.VOICE_MODE_MONO;
            layerPlan.settings.glide = Math.clamp (multisampleSource.getPortamentoTime (), 0, 1);

            layerPlans.add (layerPlan);
        }
        return layerPlans;
    }


    private static int getMinimumSequencePosition (final List<ISampleZone> zones)
    {
        int minimum = Integer.MAX_VALUE;
        for (final ISampleZone zone: zones)
            if (zone.getSequencePosition () >= 0)
                minimum = Math.min (minimum, zone.getSequencePosition ());
        return minimum;
    }


    /**
     * Calculates the fractional part of the zone tuning in cents if it is identical for all zones.
     * The sound structures only store full semi-tones, the fraction is stored in the fine tune
     * parameter of the layer engine instead.
     *
     * @param zones The zones of the layer
     * @return The common fine tune in cents or 0 if the zones have different fractions
     */
    private static double calculateCommonFineTuneCents (final List<ISampleZone> zones)
    {
        Double commonFraction = null;
        for (final ISampleZone zone: zones)
        {
            final double tuning = zone.getTuning ();
            final double fraction = tuning - Math.round (tuning);
            if (commonFraction == null)
                commonFraction = Double.valueOf (fraction);
            else if (Math.abs (commonFraction.doubleValue () - fraction) > 0.005)
                return 0;
        }
        return commonFraction == null ? 0 : commonFraction.doubleValue () * 100.0;
    }


    /**
     * Calculates the panning if it is identical for all zones. A common panning is stored in the
     * layer, different values in the sounds.
     *
     * @param zones The zones of the layer
     * @return The common panning or 0 if the zones have different values
     */
    private static double calculateCommonPanning (final List<ISampleZone> zones)
    {
        final double commonPanning = zones.get (0).getPanning ();
        for (final ISampleZone zone: zones)
            if (Math.abs (zone.getPanning () - commonPanning) > 0.001)
                return 0;
        return Math.clamp (commonPanning, -1, 1);
    }


    /**
     * Returns the filter if all zones have one with identical settings. A common filter is stored
     * as a filter effect in the FX slots of the layer.
     *
     * @param zones The zones of the layer
     * @return The common filter or null
     */
    private static IFilter calculateCommonFilter (final List<ISampleZone> zones)
    {
        final Optional<IFilter> optFilter = zones.get (0).getFilter ();
        if (optFilter.isEmpty ())
            return null;
        final IFilter filter = optFilter.get ();
        for (final ISampleZone zone: zones)
        {
            final Optional<IFilter> other = zone.getFilter ();
            if (other.isEmpty ())
                return null;
            final IFilter otherFilter = other.get ();
            if (otherFilter.getType () != filter.getType () || Math.abs (otherFilter.getCutoff () - filter.getCutoff ()) > 1 || Math.abs (otherFilter.getResonance () - filter.getResonance ()) > 0.001)
                return null;
        }
        return filter;
    }


    /**
     * Calculates the gain as a ratio if it is identical for all zones. A common gain is stored
     * loss-less in the layer volume, different values are quantized to the volume percent of the
     * sounds.
     *
     * @param zones The zones of the layer
     * @return The common gain ratio or 1 if the zones have different values
     */
    private static double calculateCommonGainRatio (final List<ISampleZone> zones)
    {
        final double commonGain = zones.get (0).getGain ();
        for (final ISampleZone zone: zones)
            if (Math.abs (zone.getGain () - commonGain) > 0.001)
                return 1;
        return Math.pow (10, commonGain / 20.0);
    }


    /**
     * Creates the group element with one sound entry for each zone of the layer and adds the sample
     * file names to the sounds list.
     *
     * @param packDocument The pack XML document
     * @param groupsElement The element to which to add the group
     * @param soundsElement The element to which to add the sample file names
     * @param layerPlan The layer to create the group for
     * @param multisampleSource The multi-sample source
     * @param samplePool The pool which stores the sample files in the ZIP
     * @throws IOException Could not store a sample
     */
    private void createGroup (final Document packDocument, final Element groupsElement, final Element soundsElement, final LayerPlan layerPlan, final IMultisampleSource multisampleSource, final SamplePool samplePool) throws IOException
    {
        final Element groupElement = XMLUtils.addElement (packDocument, groupsElement, SoundboxTag.GROUP);
        groupElement.setAttribute ("gv", "1");
        groupElement.setAttribute ("pv", PLUGIN_VERSION);
        groupElement.setAttribute (SoundboxTag.ATTR_NAME, layerPlan.groupName);

        final int numberOfRobins = layerPlan.robins.size ();
        if (numberOfRobins > 1)
        {
            groupElement.setAttribute ("rrLayers", Integer.toString (numberOfRobins));
            groupElement.setAttribute ("rrMode", "0");
        }

        for (int robinIndex = 0; robinIndex < numberOfRobins; robinIndex++)
            for (final ISampleZone zone: layerPlan.robins.get (robinIndex))
            {
                final int sampleIndex = this.storeZoneSample (packDocument, soundsElement, multisampleSource, zone, samplePool);
                if (sampleIndex < 0)
                    continue;

                final Element soundElement = XMLUtils.addElement (packDocument, groupElement, SoundboxTag.SOUND);
                soundElement.setAttribute ("f", Integer.toString (sampleIndex));
                if (numberOfRobins > 1)
                    soundElement.setAttribute ("rrLayer", Integer.toString (robinIndex));
                soundElement.setTextContent (SoundboxJuceBase64.encode (createSound (zone, layerPlan).write ()));
            }
    }


    /**
     * Stores the sample data of a zone in the ZIP and adds its file name to the sounds list. Sample
     * data which is shared between zones or is identical to already stored data is stored only once
     * (unless the WAV chunk options require zone specific chunks).
     *
     * @param packDocument The pack XML document
     * @param soundsElement The element to which to add the sample file names
     * @param multisampleSource The multi-sample source
     * @param zone The zone
     * @param samplePool The pool which stores the sample files in the ZIP
     * @return The index of the sample file in the pool or -1 if the zone has no sample data
     * @throws IOException Could not store the sample
     */
    private int storeZoneSample (final Document packDocument, final Element soundsElement, final IMultisampleSource multisampleSource, final ISampleZone zone, final SamplePool samplePool) throws IOException
    {
        final Optional<ISampleData> optSampleData = zone.getSampleData ();
        if (optSampleData.isEmpty ())
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, multisampleSource.getName (), zone.getName ());
            return -1;
        }
        final ISampleData sampleData = optSampleData.get ();

        // With chunk updates enabled the written file depends on the zone, do not de-duplicate
        final boolean requiresRewrite = this.requiresRewrite (DESTINATION_FORMAT);
        if (!requiresRewrite)
        {
            final Integer knownIndex = samplePool.identityIndices.get (sampleData);
            if (knownIndex != null)
                return knownIndex.intValue ();
        }

        this.progress.notifyProgress ();
        final byte [] content;
        try (final ByteArrayOutputStream sampleOutput = new ByteArrayOutputStream ())
        {
            if (requiresRewrite)
                this.rewriteFile (multisampleSource, zone, sampleOutput, DESTINATION_FORMAT, false);
            else
                sampleData.writeSample (sampleOutput);
            content = sampleOutput.toByteArray ();
        }

        Integer sampleIndex = null;
        String contentHash = null;
        if (!requiresRewrite)
        {
            contentHash = hash (content);
            sampleIndex = samplePool.contentIndices.get (contentHash);
        }

        if (sampleIndex == null)
        {
            sampleIndex = Integer.valueOf (samplePool.numberOfSamples);
            samplePool.numberOfSamples++;
            AbstractCreator.storeDataFile (samplePool.zipOutputStream, SoundboxTag.SAMPLES_FOLDER + "s" + sampleIndex, content, samplePool.dateTime);

            final String sampleName = createUniqueName (FileUtils.createSafeFilename (zone.getName ()), samplePool.usedSampleNames);
            XMLUtils.addTextElement (packDocument, soundsElement, SoundboxTag.SOUND, sampleName + ".wav");

            if (contentHash != null)
                samplePool.contentIndices.put (contentHash, sampleIndex);
        }

        if (!requiresRewrite)
            samplePool.identityIndices.put (sampleData, sampleIndex);
        return sampleIndex.intValue ();
    }


    private static String hash (final byte [] content)
    {
        try
        {
            return HexFormat.of ().formatHex (MessageDigest.getInstance ("SHA-256").digest (content));
        }
        catch (final NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException (ex);
        }
    }


    /**
     * Fills the sound structure from a sample zone.
     *
     * @param zone The sample zone
     * @param layerPlan The layer which plays the zone
     * @return The sound structure
     */
    private static SoundboxSound createSound (final ISampleZone zone, final LayerPlan layerPlan)
    {
        final SoundboxSound sound = new SoundboxSound ();

        sound.keyLow = limitToDefault (zone.getKeyLow (), 0);
        sound.keyRoot = limitToDefault (zone.getKeyRoot (), sound.keyLow);
        // The upper bound of the mapping is stored exclusive but 127 is the highest value found
        // in the factory packs
        sound.keyHighExcl = Math.min (limitToDefault (zone.getKeyHigh (), 127) + 1, 127);
        sound.velocityLow = limitToDefault (zone.getVelocityLow (), 0);
        sound.velocityHigh = limitToDefault (zone.getVelocityHigh (), 127);
        sound.reverse = zone.isReversed ();
        sound.panning = Math.clamp (zone.getPanning () - layerPlan.state.panning, -1, 1);

        // The fractional part of the tuning is stored in the fine tune of the layer
        sound.tuneSemitones = (int) Math.round (zone.getTuning () - layerPlan.settings.fineTuneCents / 100.0);

        // The volume can only be lowered (100% is the maximum), a gain which is common to all
        // zones is stored lossless in the layer volume instead
        final double layerRatio = layerPlan.state.volume / SoundboxLayerState.DEFAULT_VOLUME;
        final double volumeRatio = Math.pow (10, zone.getGain () / 20.0) / (layerRatio > 0 ? layerRatio : 1);
        sound.volumePercent = Math.clamp (Math.round (volumeRatio * 100.0), 0, 100);

        final double numFrames = getNumberOfFrames (zone);
        if (numFrames > 0)
        {
            sound.sampleStart = Math.clamp (limitToDefault (zone.getStart (), 0) / numFrames, 0, 1);
            sound.sampleEnd = Math.clamp (limitToDefault (zone.getStop (), (int) numFrames) / numFrames, 0, 1);

            final List<ISampleLoop> loops = zone.getLoops ();
            if (!loops.isEmpty ())
            {
                final ISampleLoop loop = loops.get (0);
                sound.loopActive = true;
                sound.pingPong = loop.getType () == LoopType.ALTERNATING;
                sound.loopStart = Math.clamp (limitToDefault (loop.getStart (), 0) / numFrames, 0, 1);
                sound.loopEnd = Math.clamp (limitToDefault (loop.getEnd (), (int) numFrames) / numFrames, 0, 1);
                sound.loopCrossfade = Math.clamp (loop.getCrossfade (), 0, 0.5);
            }
            else
            {
                sound.loopStart = sound.sampleStart;
                sound.loopEnd = sound.sampleEnd;
            }
        }

        return sound;
    }


    private static double getNumberOfFrames (final ISampleZone zone)
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isPresent ())
            try
            {
                return sampleData.get ().getAudioMetadata ().getNumberOfSamples ();
            }
            catch (final IOException _)
            {
                // Fall through to the last resort
            }

        // Last resort: the zone and loop must at least cover the sample region
        double numberOfFrames = Math.max (0, zone.getStop ());
        for (final ISampleLoop loop: zone.getLoops ())
            numberOfFrames = Math.max (numberOfFrames, loop.getEnd ());
        return numberOfFrames;
    }


    /**
     * Creates the XML document of one preset.
     *
     * @param layerPlans The layers of the preset, 1..4 entries
     * @return The formatted XML document
     */
    private Optional<String> createPresetDocument (final List<LayerPlan> layerPlans)
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element presetElement = document.createElement (SoundboxTag.PRESET);
        document.appendChild (presetElement);
        presetElement.setAttribute ("v", "1");
        presetElement.setAttribute ("pv", PLUGIN_VERSION);
        for (int layerIndex = 0; layerIndex < MAX_LAYERS; layerIndex++)
            presetElement.setAttribute ("g" + layerIndex, layerIndex < layerPlans.size () ? layerPlans.get (layerIndex).groupName : "");
        presetElement.setAttribute ("t0", "120");
        presetElement.setAttribute ("t1", "0");
        presetElement.setAttribute ("t2", "0");

        final Element layersElement = XMLUtils.addElement (document, presetElement, SoundboxTag.LAYERS);
        for (int layerIndex = 0; layerIndex < MAX_LAYERS; layerIndex++)
        {
            final Element layerElement = XMLUtils.addElement (document, layersElement, SoundboxTag.LAYER);

            final LayerPlan layerPlan = layerIndex < layerPlans.size () ? layerPlans.get (layerIndex) : new LayerPlan ();
            layerElement.setAttribute (SoundboxTag.ATTR_STATE, SoundboxJuceBase64.encode (layerPlan.state.write ()));
            layerElement.setAttribute (SoundboxTag.ATTR_SETTINGS, SoundboxJuceBase64.encode (layerPlan.settings.write ()));

            final Element arpElement = XMLUtils.addElement (document, layerElement, "Arp");
            arpElement.setAttribute ("active", "0");
            arpElement.setAttribute ("sequencerActive", "0");
            arpElement.setAttribute ("sequencerLength", "16");
            arpElement.setAttribute ("swing", "0");
            arpElement.setAttribute ("mode", "2");
            arpElement.setAttribute ("noteLength", "10");
            XMLUtils.addElement (document, arpElement, "sequencerSteps");
            for (int step = 0; step < 32; step++)
            {
                final Element stepElement = XMLUtils.addElement (document, arpElement, "step" + step);
                stepElement.setAttribute ("volume", "1.0");
                stepElement.setAttribute ("pan", "0.0");
                stepElement.setAttribute ("octaveIndex", "0");
                stepElement.setAttribute ("density", "0");
            }

            final Element effectsElement = addEffectsElement (document, layerElement);
            if (layerPlan.filter != null)
                SoundboxFilterEffect.writeFilterEffect (effectsElement, layerPlan.filter);
        }

        final Element enginesElement = XMLUtils.addElement (document, presetElement, "Engines");
        for (int engineIndex = 0; engineIndex < MAX_LAYERS; engineIndex++)
        {
            final Element engineElement = XMLUtils.addElement (document, enginesElement, "E");
            engineElement.setAttribute ("state", ENGINE_STATE);
            engineElement.setAttribute ("sequence", ENGINE_SEQUENCE);
        }

        XMLUtils.addElement (document, presetElement, "ArpsManager");

        final Element masterElement = XMLUtils.addElement (document, presetElement, "Master");
        masterElement.setAttribute ("volume", "0.8500000238418579");
        masterElement.setAttribute ("pan", "0.0");
        addEffectsElement (document, masterElement);

        final Element midiElement = XMLUtils.addElement (document, presetElement, "Midi");
        XMLUtils.addElement (document, midiElement, "ModWheel");
        XMLUtils.addElement (document, midiElement, "Aftertouch");
        XMLUtils.addElement (document, midiElement, "Timbre");

        final Element xyPadElement = XMLUtils.addElement (document, presetElement, "XYPAD");
        xyPadElement.setAttribute ("active", "0");
        xyPadElement.setAttribute ("movementActive", "0");
        xyPadElement.setAttribute ("movementSyncActive", "0");
        xyPadElement.setAttribute ("movementShape", "0");
        xyPadElement.setAttribute ("movementFrequency", "1.0");
        xyPadElement.setAttribute ("movementLoopLength", "8");
        xyPadElement.setAttribute ("shapeAmplitud", "0.5");
        xyPadElement.setAttribute ("x", "0.5");
        xyPadElement.setAttribute ("y", "0.5");

        return this.createXMLString (document);
    }


    private static Element addEffectsElement (final Document document, final Element parentElement)
    {
        final Element effectsElement = XMLUtils.addElement (document, parentElement, "Effects");
        effectsElement.setAttribute ("active", "1");
        effectsElement.setAttribute ("slotSelected", "0");
        for (int slot = 0; slot < 4; slot++)
        {
            effectsElement.setAttribute ("s" + slot, "0");
            effectsElement.setAttribute ("s" + slot + "fx", "0");
        }
        return effectsElement;
    }


    private static String createUniqueName (final String name, final Set<String> usedNames)
    {
        String uniqueName = name;
        int counter = 2;
        while (!usedNames.add (uniqueName))
        {
            uniqueName = name + " " + counter;
            counter++;
        }
        return uniqueName;
    }


    /** The mapping of one layer of a preset to its group, state and engine settings. */
    private static class LayerPlan
    {
        String                       groupName = "";
        /** The zones of the layer, one list per round robin (one list = no round robin). */
        List<List<ISampleZone>>      robins    = Collections.emptyList ();
        final SoundboxLayerState     state     = new SoundboxLayerState ();
        final SoundboxEngineSettings settings  = new SoundboxEngineSettings ();
        /** The filter which is common to all zones of the layer, if any. */
        IFilter                      filter    = null;
    }


    /** The state for storing the sample files of a pack with de-duplication. */
    private static class SamplePool
    {
        final ZipOutputStream           zipOutputStream;
        final Date                      dateTime;
        final Map<ISampleData, Integer> identityIndices = new IdentityHashMap<> ();
        final Map<String, Integer>      contentIndices  = new HashMap<> ();
        final Set<String>               usedSampleNames = new HashSet<> ();
        int                             numberOfSamples = 0;


        SamplePool (final ZipOutputStream zipOutputStream, final Date dateTime)
        {
            this.zipOutputStream = zipOutputStream;
            this.dateTime = dateTime;
        }
    }
}
