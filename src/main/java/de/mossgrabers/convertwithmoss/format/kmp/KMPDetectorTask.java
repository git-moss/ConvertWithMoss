// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kmp;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;


/**
 * Detects recursively KMP (KORG Multisample Parameter) files in folders. Files must end with
 * <i>.kmp</i>.
 *
 * @author Jürgen Moßgraber
 */
public class KMPDetectorTask extends AbstractDetectorTask
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadata Additional metadata configuration parameters
     */
    public KMPDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata)
    {
        super (notifier, consumer, sourceFolder, metadata, ".kmp", ".KMP");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File sourceFile)
    {
        try
        {
            final KMPFile kmpFile = new KMPFile (this.notifier, sourceFile);
            final String name = FileUtils.getNameWithoutType (sourceFile);
            final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, name);
            return this.parseKMPFile (sourceFile, kmpFile, parts);
        }
        catch (final IOException | ParseException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Create multisample sources for all presets found in the SF2 file.
     *
     * @param sourceFile The SF2 source file
     * @param kmpFile The parsed SF2 file
     * @param parts The path parts
     * @return The multisample sources
     */
    private List<IMultisampleSource> parseKMPFile (final File sourceFile, final KMPFile kmpFile, final String [] parts)
    {
        final String name = kmpFile.getName ();
        final DefaultMultisampleSource source = new DefaultMultisampleSource (sourceFile, parts, name, sourceFile.getName ());
        // Use same guessing on the filename...
        source.getMetadata ().detectMetadata (this.metadataConfig, parts);

        // Create the groups
        final DefaultGroup group = new DefaultGroup (name);

        kmpFile.getKsfFiles ().forEach (group::addSampleMetadata);

        source.setGroups (Collections.singletonList (group));
        return Collections.singletonList (source);
    }
}
