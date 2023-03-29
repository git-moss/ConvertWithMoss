// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.exception;

import de.mossgrabers.convertwithmoss.format.wav.WavSampleMetadata;

import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;


/**
 * Exception that indicates that there is a problem with creating a multisample.
 *
 * @author Jürgen Moßgraber
 */
public class MultisampleException extends Exception
{
    private static final long serialVersionUID = -5118446410115552844L;


    /**
     * Constructor.
     *
     * @param message The message
     */
    public MultisampleException (final String message)
    {
        super (message);
    }


    /**
     * Constructor.
     *
     * @param message The message
     * @param keySamples The samples assigned to a key which caused the problem
     */
    public MultisampleException (final String message, final Entry<Integer, List<WavSampleMetadata>> keySamples)
    {
        super (format (message, keySamples));
    }


    private static String format (final String message, final Entry<Integer, List<WavSampleMetadata>> keySamples)
    {
        final StringBuilder sb = new StringBuilder (message).append ("\nKey: ").append (keySamples.getKey ()).append ('\n');
        final List<WavSampleMetadata> samples = keySamples.getValue ();
        for (int i = 0; i < samples.size (); i++)
        {
            final Optional<String> filename = samples.get (i).getUpdatedFilename ();
            sb.append ("* Sample ").append (i + 1).append (": ").append (filename.isPresent () ? filename.get () : "").append ('\n');
        }
        return sb.toString ();
    }
}
