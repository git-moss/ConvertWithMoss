// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.zencore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects Roland ZEN-Core <i>.SVZ</i> tone/sample packs: it reads the user samples of the shared
 * <i>USPa</i>/<i>USDa</i> pool and, when present, the <i>MSPa</i> multi-sample key map (turning it
 * into key-ranged zones). The <i>.SVZ</i> container is shared across the whole FANTOM range and the
 * wider ZEN-Core hardware line, so a file written by {@link ZenCoreCreator} round-trips back
 * through this detector.
 *
 * @author Jürgen Moßgraber
 */
public class ZenCoreDetector extends AbstractDetector<MetadataSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public ZenCoreDetector (final INotifier notifier)
    {
        super ("Roland ZEN-Core", "ZenCore", notifier, new MetadataSettingsUI ("ZenCore"), ".svz");
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            return this.readSvz (sourceFile);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private List<IMultisampleSource> readSvz (final File svzFile) throws IOException
    {
        final ZenCoreContainer container = new ZenCoreContainer (Files.readAllBytes (svzFile.toPath ()));
        final List<ZenCoreSample> samples = ZenCoreSvz.readSamples (container);
        if (samples.isEmpty ())
        {
            this.notifier.logError ("IDS_ZENCORE_NO_USER_SAMPLES", svzFile.getName ());
            return Collections.emptyList ();
        }

        final ZenCoreKeyMap keyMap = ZenCoreSvz.readKeyMap (container);
        final IGroup group = new DefaultGroup ("Samples");
        if (keyMap == null)
        {
            for (final ZenCoreSample sample: samples)
                if (sample.getSampleData () != null)
                    group.addSampleZone (createZone (sample, sample.getOriginalKey (), sample.getOriginalKey ()));
        }
        else
            buildZonesFromKeyMap (group, samples, keyMap);

        if (group.getSampleZones ().isEmpty ())
            return Collections.emptyList ();
        final String name = FileUtils.getNameWithoutType (svzFile);
        this.notifier.log ("IDS_ZENCORE_READING_SVZ", name, Integer.toString (group.getSampleZones ().size ()));
        return Collections.singletonList (this.createMultisampleSource (svzFile, name, List.of (group)));
    }


    private static void buildZonesFromKeyMap (final IGroup group, final List<ZenCoreSample> samples, final ZenCoreKeyMap keyMap)
    {
        // Turn the flat 128-key table into contiguous zones (a zone per run of one sample index)
        int runStart = -1;
        int runIndex = -1;
        for (int key = 0; key <= ZenCoreKeyMap.NUM_KEYS; key++)
        {
            final boolean assigned = key < ZenCoreKeyMap.NUM_KEYS && keyMap.isAssigned (key);
            final int index = assigned ? keyMap.getSampleIndex (key) : -1;
            if (index != runIndex)
            {
                // The key map stores a 1-based index into the sample pool (0 = unassigned).
                final int sampleListIndex = runIndex - 1;
                if (sampleListIndex >= 0 && sampleListIndex < samples.size ())
                {
                    final ZenCoreSample sample = samples.get (sampleListIndex);
                    if (sample.getSampleData () != null)
                        group.addSampleZone (createZone (sample, runStart, key - 1));
                }
                runStart = key;
                runIndex = index;
            }
        }
    }


    private static ISampleZone createZone (final ZenCoreSample sample, final int keyLow, final int keyHigh)
    {
        final ISampleZone zone = new DefaultSampleZone (sample.getName (), keyLow, keyHigh);
        zone.setSampleData (sample.getSampleData ());
        zone.setKeyRoot (sample.getOriginalKey ());
        zone.setStart (sample.getStartPoint ());
        if (sample.getEndPoint () > 0)
            zone.setStop (sample.getEndPoint ());
        zone.setGain (MathUtils.valueToDb (Math.max (sample.getLevel (), 1) / 127.0) + sample.getGain ());
        zone.setTuning (sample.getFineTune () / 100.0);

        if (sample.getLoopMode () != ZenCoreSample.LOOP_MODE_ONE_SHOT && sample.getEndPoint () > sample.getLoopStart ())
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType (LoopType.FORWARDS);
            loop.setStart (sample.getLoopStart ());
            loop.setEnd (sample.getEndPoint ());
            zone.getLoops ().add (loop);
        }
        return zone;
    }
}
