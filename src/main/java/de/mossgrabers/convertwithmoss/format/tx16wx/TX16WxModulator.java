// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tx16wx;

import java.util.Optional;

import org.w3c.dom.Element;


/**
 * A TX16Wx modulator entry.
 *
 * @author Jürgen Moßgraber
 */
public class TX16WxModulator
{
    private final String source;
    private final String destination;
    private final String amount;


    /**
     * Constructor.
     *
     * @param modulationEntryElement The modulator entry element
     */
    public TX16WxModulator (final Element modulationEntryElement)
    {
        this.source = modulationEntryElement.getAttribute (TX16WxTag.MODULATION_SOURCE);
        this.destination = modulationEntryElement.getAttribute (TX16WxTag.MODULATION_DESTINATION);
        this.amount = modulationEntryElement.getAttribute (TX16WxTag.MODULATION_AMOUNT);
    }


    /**
     * Test if one of the given tags matches the source tag of this modulator.
     *
     * @param sourceTags The source tags to match
     * @return True if one of the source tags matches the parsed source tag
     */
    public boolean isSource (final String... sourceTags)
    {
        return matchTags (this.source, sourceTags);
    }


    /**
     * Test if one of the given tags matches the destination tag of this modulator.
     *
     * @param destinationTags The destination tags to match
     * @return True if one of the destination tags matches the parsed destination tag
     */
    public boolean isDestination (final String... destinationTags)
    {
        return matchTags (this.destination, destinationTags);
    }


    /**
     * Get the modulation amount if its unit is cents ('Ct').
     *
     * @return The cents if present and the unit is cents
     */
    public Optional<Integer> getModAmountAsCent ()
    {
        if (this.amount != null && this.amount.endsWith ("Ct"))
            return Optional.of (Integer.valueOf (this.amount.substring (0, this.amount.length () - 2).trim ()));
        return Optional.empty ();
    }


    private static boolean matchTags (final String tag, final String... tags)
    {
        for (final String t: tags)
            if (t.equals (tag))
                return true;
        return false;
    }
}
