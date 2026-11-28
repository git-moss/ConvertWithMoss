// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.wav;

import de.mossgrabers.convertwithmoss.core.creator.SampleFileFormat;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;


/**
 * Settings for the sample file creator: all audio file formats can be selected.
 *
 * @author Jürgen Moßgraber
 */
public class WavCreatorUI extends WavChunkSettingsUI
{
    private static final String OUTPUT_FORMAT = "OutputFormat";


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public WavCreatorUI (final String prefix)
    {
        super (prefix, true, true, true, true, SampleFileFormat.values ());
    }


    /** {@inheritDoc} */
    @Override
    protected String getSampleFileFormatKey ()
    {
        // Keep the identifier of older versions for the settings and the CLI parameter
        return this.prefix + OUTPUT_FORMAT;
    }
}
