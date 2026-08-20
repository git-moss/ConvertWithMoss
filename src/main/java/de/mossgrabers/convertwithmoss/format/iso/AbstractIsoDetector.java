// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.iso;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.akai.diskformat.AkaiDiskImage;
import de.mossgrabers.convertwithmoss.format.akai.diskformat.AkaiPartition;
import de.mossgrabers.convertwithmoss.format.akai.diskformat.IAkaiVolume;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiS1000Program;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiS1000ProgramConverter;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiS1000Sample;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiS1000Volume;


/**
 * Detects recursively ISO files in folders. Files must end with <i>.ISO</i>.
 *
 * @param <T> The type of the settings
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractIsoDetector<T extends MetadataSettingsUI> extends AbstractDetector<T>
{
    /**
     * Constructor.
     *
     * @param name The name of the object.
     * @param prefix The prefix to use for the metadata properties tags
     * @param notifier The notifier
     * @param userInterface The user interface
     * @param fileEndings The file endings to search for
     */
    protected AbstractIsoDetector (final String name, final String prefix, final INotifier notifier, final T userInterface, final String... fileEndings)
    {
        super (name, prefix, notifier, userInterface, fileEndings);
    }


    /** {@inheritDoc} */
    @Override
    protected abstract List<IMultisampleSource> readPresetFile (final File sourceFile);


    /**
     * Process an ISO file which was detected as Akai S1000 format.
     *
     * @param sourceFile The ISO file to process
     * @return The converted multi-samples
     */
    protected List<IMultisampleSource> processAkaiS1000Disk (final File sourceFile)
    {
        final List<IMultisampleSource> multiSampleSources = new ArrayList<> ();
        final AkaiS1000ProgramConverter converter = new AkaiS1000ProgramConverter (this.notifier);

        try (final AkaiDiskImage disk = new AkaiDiskImage (sourceFile))
        {
            final int partitionCount = disk.getPartitionCount ();

            final String fileName = sourceFile.getName ();
            final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, fileName);
            for (int partitionIndex = 0; partitionIndex < partitionCount; partitionIndex++)
            {
                final AkaiPartition partition = disk.getPartition (partitionIndex);
                this.notifier.log ("IDS_ISO_PROCESSING_PARTITION", partition.getName ());

                for (final IAkaiVolume volume: partition.getVolumes ())
                    if (volume instanceof final AkaiS1000Volume s1000Volume)
                    {
                        final List<String> errors = s1000Volume.getErrors ();
                        if (!errors.isEmpty ())
                            for (final String error: errors)
                                this.notifier.logError ("IDS_S1000_PARTITIONA_ERROR", error);

                        final List<AkaiS1000Sample> samples = s1000Volume.getSamples ();
                        final String volumeName = s1000Volume.getName ();
                        for (final List<AkaiS1000Program> layeredPrograms: groupLayeredPrograms (s1000Volume.getPrograms ()))
                        {
                            // The combination is documented by the multi-sample itself - it is
                            // named after the programs it combines and every one of them becomes a
                            // group which carries its name - so that it can be seen where the
                            // source is shown or converted and not for the presets of a disk which
                            // a run leaves out
                            String programName = createLayeredName (layeredPrograms);
                            if (volumeName != null && !volumeName.isBlank ())
                                programName = volumeName.trim () + " " + programName;

                            final List<IGroup> groups = new ArrayList<> ();
                            for (final AkaiS1000Program program: layeredPrograms)
                            {
                                final IGroup group = converter.createGroup (program, samples);
                                if (layeredPrograms.size () > 1)
                                    group.setName (program.getName ());
                                groups.add (group);
                            }

                            final IMultisampleSource multisampleSource = this.createMultisampleSource (sourceFile, parts, programName, groups);
                            AkaiS1000ProgramConverter.applyVoiceSettings (multisampleSource, layeredPrograms);
                            multisampleSource.extendSubPath (fileName);
                            multisampleSource.extendSubPath (volumeName);
                            multiSampleSources.add (multisampleSource);
                        }
                    }
            }

            this.notifier.log ("IDS_NOTIFY_LINE_FEED");
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_ISO_COULD_NOT_PROCESS", ex);
        }

        return multiSampleSources;
    }


    /**
     * Group the programs of a volume which the hardware plays layered. All programs which share
     * their MIDI program number (on the same MIDI channel) are selected together by that program
     * number and always sound at once - this is how the sound designers of many CD-ROMs build
     * their final patches from 2 or 3 component programs.
     *
     * @param programs The programs of a volume
     * @return The programs grouped into the stacks which play together, in the order of the volume
     */
    private static Collection<List<AkaiS1000Program>> groupLayeredPrograms (final List<AkaiS1000Program> programs)
    {
        final Map<String, List<AkaiS1000Program>> layeredPrograms = new LinkedHashMap<> ();
        for (final AkaiS1000Program program: programs)
        {
            final String selectionKey = program.getMidiProgramNumber () + ":" + (program.getMidiChannel () & 0xFF);
            layeredPrograms.computeIfAbsent (selectionKey, key -> new ArrayList<> ()).add (program);
        }
        return layeredPrograms.values ();
    }


    /**
     * Get the name for a multi-sample which combines the given layered programs, which is the
     * common prefix of their names (e.g. 'DIGIJAZZ' for 'DIGIJAZZ A' + 'DIGIJAZZ B'). If the
     * names share no reasonable prefix, the name of the first program is used.
     *
     * @param layeredPrograms The programs which play layered
     * @return The name
     */
    private static String createLayeredName (final List<AkaiS1000Program> layeredPrograms)
    {
        final String firstName = layeredPrograms.get (0).getName ();
        if (layeredPrograms.size () == 1)
            return firstName;

        String prefix = firstName;
        for (final AkaiS1000Program program: layeredPrograms)
        {
            final String name = program.getName ();
            final int length = Math.min (prefix.length (), name.length ());
            int position = 0;
            while (position < length && prefix.charAt (position) == name.charAt (position))
                position++;
            prefix = prefix.substring (0, position);
        }

        final String trimmedPrefix = prefix.trim ();
        return trimmedPrefix.length () < 2 ? firstName : trimmedPrefix;
    }
}
