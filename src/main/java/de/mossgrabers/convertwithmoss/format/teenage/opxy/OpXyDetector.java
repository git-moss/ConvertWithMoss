// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.teenage.opxy;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects Teenage Engineering OP-XY presets. A preset is a folder with the ending <i>.preset</i>
 * which contains the description file <i>patch.json</i> and all samples as WAV files.
 *
 * @author Jürgen Moßgraber
 */
public class OpXyDetector extends AbstractDetector<MetadataSettingsUI>
{
    private final ObjectMapper mapper = new ObjectMapper ();


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public OpXyDetector (final INotifier notifier)
    {
        super ("Teenage Engineering OP-XY", "OPXY", notifier, new MetadataSettingsUI ("OPXY"), ".json");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        // Only the description file of a preset is of interest
        if (!OpXyTag.PATCH_FILE.equalsIgnoreCase (file.getName ()))
            return Collections.emptyList ();

        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final JsonNode root = this.mapper.readTree (file);
            final JsonNode typeNode = root.get (OpXyTag.TAG_TYPE);
            if (typeNode == null || !OpXyTag.TYPE_MULTISAMPLER.equals (typeNode.asText ()))
            {
                this.notifier.logError ("IDS_OPXY_NOT_A_MULTISAMPLE", file.getAbsolutePath ());
                return Collections.emptyList ();
            }
            return this.parsePatch (file, root);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Parse the description file of a preset.
     *
     * @param file The description file
     * @param root The parsed JSON document
     * @return The multi-sample source
     * @throws IOException Could not read a sample
     */
    private List<IMultisampleSource> parsePatch (final File file, final JsonNode root) throws IOException
    {
        final File presetFolder = file.getParentFile ();
        final IGroup group = new DefaultGroup ("Group #1");

        final JsonNode regionsNode = root.get (OpXyTag.TAG_REGIONS);
        if (regionsNode != null)
            for (final JsonNode regionNode: regionsNode)
            {
                final ISampleZone zone = this.parseRegion (presetFolder, regionNode);
                if (zone != null)
                    group.addSampleZone (zone);
            }

        if (group.getSampleZones ().isEmpty ())
        {
            this.notifier.logError ("IDS_OPXY_NO_REGIONS", file.getAbsolutePath ());
            return Collections.emptyList ();
        }

        applyGlobals (root, group);

        final IMultisampleSource multisampleSource = this.createMultisampleSource (file, getPresetName (presetFolder));
        multisampleSource.setGroups (Collections.singletonList (group));
        return Collections.singletonList (multisampleSource);
    }


    /**
     * Parse one region into a sample zone.
     *
     * @param presetFolder The folder which contains the samples
     * @param regionNode The region to parse
     * @return The zone or null if the sample could not be found
     * @throws IOException Could not read the sample
     */
    private ISampleZone parseRegion (final File presetFolder, final JsonNode regionNode) throws IOException
    {
        final String sampleName = getText (regionNode, OpXyTag.TAG_SAMPLE, null);
        if (sampleName == null || sampleName.isBlank ())
            return null;
        final File sampleFile = new File (presetFolder, sampleName);
        if (!sampleFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", sampleFile.getAbsolutePath ());
            return null;
        }

        final ISampleData sampleData = createSampleData (sampleFile, this.notifier);
        final ISampleZone zone = new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), sampleData);

        zone.setKeyLow (Math.clamp (getInt (regionNode, OpXyTag.TAG_LOW_KEY, 0), 0, 127));
        zone.setKeyHigh (Math.clamp (getInt (regionNode, OpXyTag.TAG_HIGH_KEY, 127), 0, 127));
        zone.setKeyRoot (Math.clamp (getInt (regionNode, OpXyTag.TAG_KEY_CENTER, 60), 0, 127));
        zone.setTuning (getInt (regionNode, OpXyTag.TAG_TUNE, 0));
        zone.setGain (getInt (regionNode, OpXyTag.TAG_GAIN, 0));
        zone.setReversed (getBoolean (regionNode, OpXyTag.TAG_REVERSE));
        zone.setStart (getInt (regionNode, OpXyTag.TAG_SAMPLE_START, 0));
        zone.setStop (getInt (regionNode, OpXyTag.TAG_SAMPLE_END, -1));

        if (getBoolean (regionNode, OpXyTag.TAG_LOOP_ENABLED))
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType (LoopType.FORWARDS);
            loop.setStart (getInt (regionNode, OpXyTag.TAG_LOOP_START, 0));
            loop.setEnd (getInt (regionNode, OpXyTag.TAG_LOOP_END, zone.getStop ()));
            // The device relates its cross-fade percentage to the whole sample, not to the loop
            final int crossfade = getInt (regionNode, OpXyTag.TAG_LOOP_CROSSFADE, 0);
            final int frameCount = getInt (regionNode, OpXyTag.TAG_FRAME_COUNT, 0);
            if (frameCount > 0)
                loop.setCrossfade (crossfade / (double) frameCount);
            else
                loop.setCrossfadeInSamples (crossfade);
            // True keeps the loop running after note-off, false plays the part behind the loop
            loop.setLoopUntilRelease (!getBoolean (regionNode, OpXyTag.TAG_LOOP_ON_RELEASE));
            zone.getLoops ().add (loop);
        }

        return zone;
    }


    /**
     * Apply the settings which the device stores for the whole preset to all zones.
     *
     * @param root The parsed JSON document
     * @param group The group which contains the zones
     */
    private static void applyGlobals (final JsonNode root, final IGroup group)
    {
        final JsonNode engineNode = root.get (OpXyTag.TAG_ENGINE);
        final int bendRange = engineNode == null ? -1 : (int) Math.round (OpXyTag.toFactor (getInt (engineNode, OpXyTag.TAG_BEND_RANGE, -1)) * OpXyTag.MAX_BEND_RANGE);
        final int velocitySensitivity = engineNode == null ? -1 : getInt (engineNode, OpXyTag.TAG_VELOCITY_SENSITIVITY, -1);

        final JsonNode envelopeNode = root.get (OpXyTag.TAG_ENVELOPE);
        final JsonNode ampNode = envelopeNode == null ? null : envelopeNode.get (OpXyTag.TAG_AMP);

        for (final ISampleZone zone: group.getSampleZones ())
        {
            if (bendRange >= 0)
            {
                zone.setBendUp (bendRange * 100);
                zone.setBendDown (-bendRange * 100);
            }

            if (velocitySensitivity >= 0)
                zone.getAmplitudeVelocityModulator ().setDepth (OpXyTag.toFactor (velocitySensitivity));

            if (ampNode != null)
            {
                final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
                envelope.setAttackTime (OpXyTag.toEnvelopeTime (getInt (ampNode, OpXyTag.TAG_ATTACK, 0)));
                envelope.setDecayTime (OpXyTag.toEnvelopeTime (getInt (ampNode, OpXyTag.TAG_DECAY, 0)));
                envelope.setSustainLevel (OpXyTag.toFactor (getInt (ampNode, OpXyTag.TAG_SUSTAIN, OpXyTag.MAX_VALUE)));
                envelope.setReleaseTime (OpXyTag.toEnvelopeTime (getInt (ampNode, OpXyTag.TAG_RELEASE, 0)));
            }
        }
    }


    /**
     * Get the name of the preset from the name of its folder, which ends with '.preset'.
     *
     * @param presetFolder The folder of the preset
     * @return The name
     */
    private static String getPresetName (final File presetFolder)
    {
        final String folderName = presetFolder.getName ();
        return folderName.toLowerCase ().endsWith (OpXyTag.PRESET_ENDING) ? folderName.substring (0, folderName.length () - OpXyTag.PRESET_ENDING.length ()) : folderName;
    }


    private static int getInt (final JsonNode node, final String name, final int defaultValue)
    {
        final JsonNode valueNode = node.get (name);
        return valueNode == null ? defaultValue : valueNode.asInt (defaultValue);
    }


    private static boolean getBoolean (final JsonNode node, final String name)
    {
        final JsonNode valueNode = node.get (name);
        return valueNode != null && valueNode.asBoolean (false);
    }


    private static String getText (final JsonNode node, final String name, final String defaultValue)
    {
        final JsonNode valueNode = node.get (name);
        return valueNode == null ? defaultValue : valueNode.asText (defaultValue);
    }
}
