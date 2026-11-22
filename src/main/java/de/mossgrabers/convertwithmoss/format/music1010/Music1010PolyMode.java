// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.music1010;

import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.tools.XMLUtils;


/**
 * Conversion helpers for the poly mode of the 1010music multi-sample engine (Bento and Blackbox).
 * The poly mode limits the number of notes which can sound simultaneously.
 *
 * @author Jürgen Moßgraber
 */
public class Music1010PolyMode
{
    /** The poly mode 'Poly X' which uses all available voices. */
    public static final int     POLY_X      = 5;

    // The number of voices of the poly modes Mono, Poly 2, Poly 4, Poly 6 and Poly 8
    private static final int [] POLY_VOICES =
    {
        1,
        2,
        4,
        6,
        8
    };


    /**
     * Private due to utility class.
     */
    private Music1010PolyMode ()
    {
        // Intentionally empty
    }


    /**
     * Read the poly mode and set it as the polyphony of the multi-sample source. 'Poly X' has no
     * fixed number of voices, therefore the polyphony stays unspecified in that case.
     *
     * @param paramsElement The parameters element of the instrument
     * @param multisampleSource The multi-sample source to update
     */
    public static void readPolyphony (final Element paramsElement, final IMultisampleSource multisampleSource)
    {
        final int polyMode = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_POLY_MODE, POLY_X);
        if (polyMode >= 0 && polyMode < POLY_VOICES.length)
            multisampleSource.setPolyphony (POLY_VOICES[polyMode]);
    }


    /**
     * Write the polyphony of the multi-sample source as the poly mode.
     *
     * @param paramsElement The parameters element of the instrument
     * @param multisampleSource The multi-sample source from which to get the polyphony
     */
    public static void writePolyphony (final Element paramsElement, final IMultisampleSource multisampleSource)
    {
        paramsElement.setAttribute (Music1010Tag.ATTR_POLY_MODE, Integer.toString (getPolyMode (multisampleSource.getPolyphony ())));
    }


    /**
     * Get the smallest poly mode which can play the given number of voices. If the polyphony is not
     * specified or exceeds 8 voices, 'Poly X' is used.
     *
     * @param polyphony The maximum number of simultaneous voices, 0 if not specified
     * @return The poly mode
     */
    private static int getPolyMode (final int polyphony)
    {
        if (polyphony > 0)
            for (int i = 0; i < POLY_VOICES.length; i++)
                if (polyphony <= POLY_VOICES[i])
                    return i;
        return POLY_X;
    }
}
