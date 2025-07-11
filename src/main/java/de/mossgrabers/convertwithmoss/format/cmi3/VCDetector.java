// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.cmi3;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Descriptor for Fairlight CMI3 Voice (VC) files detector.
 *
 * @author Jürgen Moßgraber
 */
public class VCDetector extends AbstractDetector<VCDetectorUI>
{
    private static final String [] VC_ENDINGS =
    {
        ".vc",
        ".VC"
    };

    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public VCDetector (final INotifier notifier)
    {
        super ("Fairlight CMI3 Voice", "VC", notifier, new VCDetectorUI ("VC"));
    }


    /** {@inheritDoc} */
    @Override
    protected void configureFileEndings (final boolean detectPerformances)
    {
        this.fileEndings = VC_ENDINGS;
    }

    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        final List<IMultisampleSource> multiSampleSources = new ArrayList<> ();
        multiSampleSources.addAll(this.readVCFile(sourceFile));

        return multiSampleSources;
    }


    /**
     * Reads a VC file and creates a multi-sample source from it.
     * 
     * @param sourceFile The VC file
     * @return The multi-sample source if found
     */
    private List<IMultisampleSource> readVCFile (final File sourceFile)
    {
        	final List<IMultisampleSource> multiSampleSources = new ArrayList<> ();
			try (final FileInputStream stream = new FileInputStream (sourceFile))
			{
            	final VCFile vcFile = new VCFile (this.notifier, sourceFile);
				multiSampleSources.addAll(vcFile.read (stream, sourceFile));
			}
        	catch (final IOException | ParseException ex)
			{
            	this.notifier.logError ("IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED", ex);
            	return Collections.emptyList ();
			}
			return multiSampleSources;
    }
	
}
