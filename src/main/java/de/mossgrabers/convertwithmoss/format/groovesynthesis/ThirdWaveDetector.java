// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.groovesynthesis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects the sample slots of the Groove Synthesis 3rd Wave: multi-sample slot files (*.bin) and
 * the sample slots in unified program files (*.pgdata). Each slot becomes a multi-sample with one
 * group. The settings of a program (oscillators, parts, envelopes, filters, effects, ...) are not
 * converted.
 *
 * @author Jürgen Moßgraber
 */
public class ThirdWaveDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String [] BIT_DEPTHS =
    {
        "8",
        "12",
        "16"
    };


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public ThirdWaveDetector (final INotifier notifier)
    {
        super ("Groove Synthesis 3rd Wave", "ThirdWave", notifier, new MetadataSettingsUI ("ThirdWave"), ".bin", ".pgdata");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final ThirdWaveFile.Content content;
        try
        {
            content = ThirdWaveFile.read (file);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
        // Not a file of the 3rd Wave, other devices use the same ending
        if (content == null)
            return Collections.emptyList ();

        final String fileName = FileUtils.getNameWithoutType (file);
        final String programName = content.name ().isBlank () ? fileName : content.name ();
        final List<ThirdWaveFile.Slot> slots = new ArrayList<> ();
        for (final ThirdWaveFile.Slot slot: content.slots ())
            if (!slot.samples ().isEmpty ())
                slots.add (slot);

        if (content.isProgram ())
        {
            this.notifier.log ("IDS_THIRD_WAVE_ONLY_SAMPLE_SLOTS", programName);
            if (slots.isEmpty ())
                this.notifier.log ("IDS_THIRD_WAVE_NO_SAMPLE_SLOTS", programName);
        }

        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        for (final ThirdWaveFile.Slot slot: slots)
        {
            final String slotName = getSlotName (slot, fileName);
            String name = slotName;
            // A program which uses only one slot is named like the program
            if (content.isProgram ())
                name = slots.size () == 1 ? programName : programName + " - " + slotName;

            final List<ThirdWaveFile.Sample> samples = slot.samples ();
            final DefaultGroup group = new DefaultGroup (slotName);
            for (int i = 0; i < samples.size (); i++)
                group.addSampleZone (this.createSampleZone (samples.get (i), slotName + " " + (i + 1)));
            final List<IGroup> groups = new ArrayList<> ();
            groups.add (group);
            multisampleSources.add (this.createMultisampleSource (file, name, groups));
        }
        return multisampleSources;
    }


    /**
     * Get the name of a slot. If it has none it is named like the file or its slot on the device.
     *
     * @param slot The slot
     * @param fileName The name of the file without the ending
     * @return The name
     */
    private static String getSlotName (final ThirdWaveFile.Slot slot, final String fileName)
    {
        if (!slot.name ().isBlank ())
            return slot.name ();
        if (slot.index () < 0)
            return fileName;
        return String.format (Locale.US, "S%02d", Integer.valueOf (slot.index ()));
    }


    /**
     * Create a sample zone from a sample of a slot.
     *
     * @param sample The sample
     * @param fallbackName The name to use if the sample has none
     * @return The sample zone
     */
    private ISampleZone createSampleZone (final ThirdWaveFile.Sample sample, final String fallbackName)
    {
        final String name = sample.name ().isBlank () ? fallbackName : sample.name ();

        final int sampleRate = Math.round (sample.sampleRate ());
        final int secondSampleRate = Math.round (sample.secondSampleRate ());
        if (sampleRate != secondSampleRate)
            this.notifier.log ("IDS_THIRD_WAVE_TWO_SAMPLE_RATES", name, Integer.toString (sampleRate), Integer.toString (secondSampleRate));
        if (sample.bitDepth () != ThirdWaveFile.BIT_DEPTH_16)
            this.notifier.log ("IDS_THIRD_WAVE_BIT_DEPTH", name, BIT_DEPTHS[sample.bitDepth ()]);

        final DefaultAudioMetadata audioMetadata = new DefaultAudioMetadata (1, sampleRate, 16, sample.getFrames ());
        final DefaultSampleZone zone = new DefaultSampleZone (name, new InMemorySampleData (audioMetadata, sample.audio ()));
        zone.setKeyRoot (sample.root ());
        zone.setKeyLow (sample.low ());
        zone.setKeyHigh (sample.high ());
        zone.setStart (sample.start ());
        zone.setStop (sample.end ());
        zone.setTuning (sample.tune ());
        zone.setGain (MathUtils.valueToDb (sample.volume ()));

        // A sample without a loop still stops at the release of the note, it is not a one-shot
        if (sample.loopMode () != ThirdWaveFile.LOOP_OFF && sample.loopStart () < sample.loopEnd ())
        {
            final DefaultSampleLoop loop = new DefaultSampleLoop ();
            loop.setStart (sample.loopStart ());
            loop.setEnd (sample.loopEnd ());
            // 'Looping on when note on' plays the rest of the sample after the release, 'looping
            // always on' continues looping
            loop.setLoopUntilRelease (sample.loopMode () == ThirdWaveFile.LOOP_WHILE_HELD);
            if (sample.crossfadeType () != ThirdWaveFile.CROSSFADE_OFF)
                loop.setCrossfadeInSamples (sample.crossfade ());
            zone.addLoop (loop);
        }
        return zone;
    }
}
