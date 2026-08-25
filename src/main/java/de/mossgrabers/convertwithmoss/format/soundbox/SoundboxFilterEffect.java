// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.soundbox;

import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.tools.XMLUtils;


/**
 * Reads and writes the filter effect of a Soundbox effects section. The effects element carries the
 * loaded effect type of each of its 4 slots in the attributes s0fx..s3fx (4 = the filter), a
 * bypassed slot is marked with s0..s3 = 1 and the S child elements hold the parameters of a slot
 * which differ from the defaults: FREQUENCY (in Hertz, up to 15001 = fully open), RESONANCE
 * (quality factor, up to about 3.14) and TYPE (0 = low-pass; the other indices are assumed to
 * follow the usual order high-pass, band-pass, band-rejection - only 0 has been observed).
 *
 * @author Jürgen Moßgraber
 */
public class SoundboxFilterEffect
{
    private static final int    FILTER_TYPE_ID    = 4;
    private static final double DEFAULT_FREQUENCY = 15001;
    private static final double MAX_FREQUENCY     = 15001;
    private static final double MIN_FREQUENCY     = 20;
    /** JUCE filters default to a quality of 1/sqrt(2) which is a flat response. */
    private static final double DEFAULT_QUALITY   = 0.7071;
    private static final double MAX_QUALITY       = 3.145;
    private static final double MIN_QUALITY       = 0.1;


    /**
     * Private constructor since this is a utility class.
     */
    private SoundboxFilterEffect ()
    {
        // Intentionally empty
    }


    /**
     * Reads the first active filter effect from an effects element.
     *
     * @param effectsElement The effects element of a layer or the master, may be null
     * @return The filter or null if there is none
     */
    public static IFilter readFilterEffect (final Element effectsElement)
    {
        if (effectsElement == null || "0".equals (effectsElement.getAttribute ("active")))
            return null;

        for (int slot = 0; slot < 4; slot++)
        {
            // A bypassed slot is marked with s<n>="1"
            if ((XMLUtils.getIntegerAttribute (effectsElement, "s" + slot + "fx", 0) != FILTER_TYPE_ID) || (XMLUtils.getIntegerAttribute (effectsElement, "s" + slot, 0) == 1))
                continue;

            double frequency = DEFAULT_FREQUENCY;
            double quality = DEFAULT_QUALITY;
            int typeIndex = 0;
            for (final Element slotElement: XMLUtils.getChildElementsByName (effectsElement, SoundboxTag.SOUND, false))
                if (XMLUtils.getIntegerAttribute (slotElement, "SlotNum", -1) == slot)
                {
                    frequency = XMLUtils.getDoubleAttribute (slotElement, "FREQUENCY", DEFAULT_FREQUENCY);
                    quality = XMLUtils.getDoubleAttribute (slotElement, "RESONANCE", DEFAULT_QUALITY);
                    typeIndex = (int) XMLUtils.getDoubleAttribute (slotElement, "TYPE", 0);
                    break;
                }

            final FilterType [] filterTypes = FilterType.values ();
            final FilterType filterType = typeIndex >= 0 && typeIndex < filterTypes.length ? filterTypes[typeIndex] : FilterType.LOW_PASS;
            // Map the quality factor to the normalized resonance (1 = 40 dB)
            final double resonance = Math.clamp (20.0 * Math.log10 (Math.max (0.001, quality)) / IFilter.MAX_RESONANCE, 0, 1);
            return new DefaultFilter (filterType, 2, Math.min (frequency, IFilter.MAX_FREQUENCY), resonance);
        }
        return null;
    }


    /**
     * Writes a filter effect into the first slot of an effects element.
     *
     * @param effectsElement The effects element of a layer
     * @param filter The filter to write
     */
    public static void writeFilterEffect (final Element effectsElement, final IFilter filter)
    {
        effectsElement.setAttribute ("s0fx", Integer.toString (FILTER_TYPE_ID));

        final Element slotElement = XMLUtils.addElement (effectsElement.getOwnerDocument (), effectsElement, SoundboxTag.SOUND);
        slotElement.setAttribute ("SlotNum", "0");
        slotElement.setAttribute ("type", Integer.toString (FILTER_TYPE_ID));
        // Like the plug-in, write only the parameters which differ from the defaults
        XMLUtils.setDoubleAttribute (slotElement, "FREQUENCY", Math.clamp (filter.getCutoff (), MIN_FREQUENCY, MAX_FREQUENCY), 2);
        if (filter.getResonance () > 0)
        {
            final double quality = Math.pow (10, filter.getResonance () * IFilter.MAX_RESONANCE / 20.0);
            XMLUtils.setDoubleAttribute (slotElement, "RESONANCE", Math.clamp (quality, MIN_QUALITY, MAX_QUALITY), 4);
        }
        if (filter.getType () != FilterType.LOW_PASS)
            XMLUtils.setDoubleAttribute (slotElement, "TYPE", filter.getType ().ordinal (), 1);
    }
}
