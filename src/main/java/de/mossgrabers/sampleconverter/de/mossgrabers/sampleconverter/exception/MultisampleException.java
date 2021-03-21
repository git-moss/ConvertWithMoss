// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.exception;

import de.mossgrabers.sampleconverter.core.ISampleMetadata;

import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;


/**
 * Exception that indicates that there is a problem with creating a multisample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
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
    public MultisampleException (final String message, final Entry<Integer, List<ISampleMetadata>> keySamples)
    {
        super (format (message, keySamples));
    }


    private static String format (final String message, final Entry<Integer, List<ISampleMetadata>> keySamples)
    {
        final StringBuilder sb = new StringBuilder (message).append ("\nKey: ").append (keySamples.getKey ()).append ('\n');
        final List<ISampleMetadata> samples = keySamples.getValue ();
        for (int i = 0; i < samples.size (); i++)
        {
            final Optional<String> filename = samples.get (i).getUpdatedFilename ();
            sb.append ("* Sample ").append (i + 1).append (": ").append (filename.isPresent () ? filename.get () : "").append ('\n');
        }
        return sb.toString ();
    }
}
