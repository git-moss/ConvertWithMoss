// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.disting;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.NoteParser;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.format.wav.WavCreator;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.BasicConfig;


/**
 * Stores WAV files with multi-sample setting encoded in the sample filename. All samples are stored
 * in a separate folder.
 *
 * @author Jürgen Moßgraber
 */
public class DistingCreator extends WavCreator
{
    private final Map<Integer, Integer> velocityLayerIndices = new HashMap<> ();
    private String                      filenamePrefix;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DistingCreator (final INotifier notifier)
    {
        super ("Expert Sleepers Disting EX", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.loadWavChunkSettings (config, "Disting");
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        this.saveWavChunkSettings (config, "Disting");
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        prepareKeyAndVelocityRanges (multisampleSource);

        final String sampleName = createSafeFilename (multisampleSource.getName ());
        this.filenamePrefix = StringUtils.fixASCII (sampleName);
        final String safeSampleFolderName = sampleName + FOLDER_POSTFIX;

        this.notifier.log ("IDS_NOTIFY_STORING", safeSampleFolderName);

        // Store all samples
        final File sampleFolder = new File (destinationFolder, safeSampleFolderName);
        safeCreateDirectory (sampleFolder);
        this.writeSamples (sampleFolder, multisampleSource);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Ensure that all zones are ordered by their upper key-range in ascending order. Create
     * velocity indices by grouping zones with the same upper velocity.
     * 
     * @param multisampleSource The multi-sample source
     */
    private void prepareKeyAndVelocityRanges (final IMultisampleSource multisampleSource)
    {
        this.velocityLayerIndices.clear ();
        final Set<Integer> highVelocities = new TreeSet<> ();
        for (final IGroup group: multisampleSource.getGroups ())
        {
            for (final ISampleZone sampleZone: group.getSampleZones ())
                highVelocities.add (Integer.valueOf (limitToDefault (sampleZone.getVelocityHigh (), 127)));
        }
        int index = 0;
        for (final Integer velocity: highVelocities)
        {
            this.velocityLayerIndices.put (velocity, Integer.valueOf (index));
            index++;
        }
    }


    /** {@inheritDoc} */
    @Override
    protected String createSampleFilename (final ISampleZone zone, final int zoneIndex, final String fileEnding)
    {
        // Format: Samplename_Note_Switch_VelocityIndex_RoundRobinIndex.wav

        final StringBuilder sb = new StringBuilder (this.filenamePrefix);
        sb.append ('_').append (NoteParser.formatNoteSharps (zone.getKeyRoot ()));

        final int keyHigh = limitToDefault (zone.getKeyHigh (), 127);
        sb.append ("_SW").append (keyHigh);

        if (this.velocityLayerIndices.size () > 1)
        {
            final int velocityHigh = limitToDefault (zone.getVelocityHigh (), 127);
            sb.append ("_V").append (this.velocityLayerIndices.get (Integer.valueOf (velocityHigh + 1)));
        }

        final PlayLogic playLogic = zone.getPlayLogic ();
        if (playLogic == PlayLogic.ROUND_ROBIN)
            sb.append ("_RR").append (zoneIndex + 1);

        return sb.append (fileEnding).toString ();
    }
}