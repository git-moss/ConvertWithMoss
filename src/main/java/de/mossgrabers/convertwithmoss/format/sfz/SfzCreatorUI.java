// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sfz;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.SampleFileFormat;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;


/**
 * Settings for the SFZ creator.
 *
 * @author Jürgen Moßgraber
 */
public class SfzCreatorUI extends WavChunkSettingsUI
{
    /** The setting of older versions which only allowed to choose between WAV and FLAC. */
    private static final String SFZ_CONVERT_TO_FLAC = "SFZConvertToFlac";


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public SfzCreatorUI (final String prefix)
    {
        super (prefix, false, false, false, false, SampleFileFormat.WAV, SampleFileFormat.AIFF, SampleFileFormat.FLAC);
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        // Take over the setting of older versions
        if (config.getProperty (this.getSampleFileFormatKey ()) == null && config.getBoolean (SFZ_CONVERT_TO_FLAC, false))
            config.setProperty (this.getSampleFileFormatKey (), SampleFileFormat.FLAC.getName ());

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        // Still support the parameter of older versions, the new one has precedence
        final String convertToFlac = parameters.remove (SFZ_CONVERT_TO_FLAC);
        final boolean hasOutputFormat = parameters.containsKey (this.getSampleFileFormatKey ());

        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        if (!hasOutputFormat && "1".equals (convertToFlac))
            this.setSampleFileFormat (SampleFileFormat.FLAC);
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (SFZ_CONVERT_TO_FLAC);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }
}
