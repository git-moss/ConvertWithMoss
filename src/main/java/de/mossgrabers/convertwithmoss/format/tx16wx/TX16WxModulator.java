// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
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
    private final String  source;
    private final String  destination;
    private final String  amount;
    private final String  via;
    private final String  amountVia;
    private final boolean isEnabled;


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
        this.via = modulationEntryElement.getAttribute (TX16WxTag.MODULATION_VIA);
        this.amountVia = modulationEntryElement.getAttribute (TX16WxTag.MODULATION_AMOUNT_VIA);
        this.isEnabled = parseEnabled (modulationEntryElement.getAttribute (TX16WxTag.MODULATION_ENABLED));
    }


    /**
     * Check if the modulation slot is enabled. A disabled slot is not applied by the sampler.
     *
     * @return True if it is enabled
     */
    public boolean isEnabled ()
    {
        return this.isEnabled;
    }


    /**
     * Parse the enabled attribute. It is optional, a missing one means that the slot is enabled.
     * The value is a XML schema boolean, which is written as either 'false' or '0' when the slot is
     * switched off.
     *
     * @param value The attribute value, might be empty
     * @return True if the slot is enabled
     */
    private static boolean parseEnabled (final String value)
    {
        if (value == null || value.isBlank ())
            return true;
        final String trimmed = value.trim ();
        return !("false".equalsIgnoreCase (trimmed) || "0".equals (trimmed));
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


    /**
     * Test if the via source of this modulator matches the given tag. The via source scales the
     * modulation between the amount and the via amount.
     *
     * @param viaTag The via tag to match
     * @return True if it matches
     */
    public boolean isVia (final String viaTag)
    {
        return viaTag.equals (this.via);
    }


    /**
     * Get the modulation amount without its unit, e.g. 57 for '57Ct' or 0.5 for '0.5 dB'.
     *
     * @return The amount if present and parsable
     */
    public Optional<Double> getModAmountValue ()
    {
        return parseValue (this.amount);
    }


    /**
     * Get the modulation amount which is applied when the via source is at its maximum, without its
     * unit.
     *
     * @return The amount if present and parsable
     */
    public Optional<Double> getModAmountViaValue ()
    {
        return parseValue (this.amountVia);
    }


    /**
     * Parse a value and remove its unit (Ct, dB or %).
     *
     * @param value The value to parse
     * @return The value if present and parsable
     */
    private static Optional<Double> parseValue (final String value)
    {
        if (value == null || value.isBlank ())
            return Optional.empty ();
        final String number = value.trim ().replaceAll ("(?i)\\s*(ct|db|%)$", "");
        try
        {
            return Optional.of (Double.valueOf (number));
        }
        catch (final NumberFormatException _)
        {
            return Optional.empty ();
        }
    }


    private static boolean matchTags (final String tag, final String... tags)
    {
        for (final String t: tags)
            if (t.equals (tag))
                return true;
        return false;
    }
}
