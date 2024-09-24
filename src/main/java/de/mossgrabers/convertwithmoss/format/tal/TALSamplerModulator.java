// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tal;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.tools.XMLUtils;


/**
 * A TAL Sampler modulator entry.
 *
 * @author Jürgen Moßgraber
 */
public class TALSamplerModulator
{
    /** Parameter ID for Cutoff. */
    public static final int DEST_ID_CUTOFF      = 7;
    /** Parameter ID for Volume of Layer A. */
    public static final int DEST_ID_VOLUME_A    = 62;
    /** Parameter ID for Tune of Layer A. */
    public static final int DEST_ID_TUNE_A      = 71;
    /** Parameter ID for Master Tune. */
    public static final int DEST_ID_MASTER_TUNE = 164;

    /** Source ID for Velocity. */
    public static final int SOURCE_ID_VELOCITY  = 6;
    /** Source ID for Envelope 3 (Modulation Envelope). */
    public static final int SOURCE_ID_ENV3      = 2;

    private final int       source;
    private final int       destination;
    private final double    amount;


    /**
     * Default constructor.
     */
    public TALSamplerModulator ()
    {
        this (-1, 0, 0);
    }


    /**
     * Constructor.
     *
     * @param source The source ID
     * @param destination The destination ID
     * @param amount The amount in the range of [-1..1]
     */
    public TALSamplerModulator (final int source, final int destination, final double amount)
    {
        this.source = source;
        this.destination = destination;
        this.amount = (amount + 1.0) / 2.0;
    }


    /**
     * Constructor.
     *
     * @param modulationEntryElement The modulator entry element
     */
    public TALSamplerModulator (final Element modulationEntryElement)
    {
        this.source = XMLUtils.getIntegerAttribute (modulationEntryElement, TALSamplerTag.MOD_MATRIX_SOURCE_ID, -1);
        this.destination = XMLUtils.getIntegerAttribute (modulationEntryElement, TALSamplerTag.MOD_MATRIX_PARAMETER_ID, 0);
        this.amount = XMLUtils.getDoubleAttribute (modulationEntryElement, TALSamplerTag.MOD_MATRIX_AMOUNT, 0.5);
    }


    /**
     * Creates a modulation entry from this modulator.
     *
     * @param document The document to which the element belongs
     * @param modulationElement The modulation element to which to add the entry
     */
    public void createModElements (final Document document, final Element modulationElement)
    {
        final Element entryElement = XMLUtils.addElement (document, modulationElement, "entry");
        XMLUtils.setIntegerAttribute (entryElement, TALSamplerTag.MOD_MATRIX_SOURCE_ID, this.source);
        XMLUtils.setIntegerAttribute (entryElement, TALSamplerTag.MOD_MATRIX_PARAMETER_ID, this.destination);
        XMLUtils.setDoubleAttribute (entryElement, TALSamplerTag.MOD_MATRIX_AMOUNT, this.amount, 6);
    }


    /**
     * Test if one of the given IDs matches the source ID of this modulator.
     *
     * @param sourceIDs The source IDs to match
     * @return True if one of the source IDs matches the parsed source ID
     */
    public boolean isSource (final int... sourceIDs)
    {
        return matchTags (this.source, sourceIDs);
    }


    /**
     * Test if one of the given IDs matches the destination ID of this modulator.
     *
     * @param destinationIDs The destination IDs to match
     * @return True if one of the destination IDs matches the parsed destination ID
     */
    public boolean isDestination (final int... destinationIDs)
    {
        return matchTags (this.destination, destinationIDs);
    }


    /**
     * Get the modulation amount.
     *
     * @return The modulation amount in the range of [-1..1]
     */
    public double getModAmount ()
    {
        return this.amount * 2.0 - 1.0;
    }


    private static boolean matchTags (final int id, final int... ids)
    {
        for (final int t: ids)
            if (t == id)
                return true;
        return false;
    }
}
