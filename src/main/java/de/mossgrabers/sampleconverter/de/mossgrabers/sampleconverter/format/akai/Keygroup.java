// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.akai;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;


/**
 * Helper class for keeping track of created keygroups.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class Keygroup
{
    private final boolean       isSequence;
    private final Element       instrumentElement;
    private final Element       layersElement;
    private final List<Element> layerElements = new ArrayList<> ();
    private final int           velocityLow;
    private final int           velocityHigh;


    /**
     * Constructor for a velocity keygroup.
     * 
     * @param instrumentElement The instrument element
     * @param layersElement The layers element
     */
    public Keygroup (final Element instrumentElement, final Element layersElement)
    {
        this (instrumentElement, layersElement, -1, -1, false);
    }


    /**
     * Constructor for a sequence keygroup.
     * 
     * @param instrumentElement The instrument element
     * @param layersElement The layers element
     * @param velocityLow The bottom velocity
     * @param velocityHigh The upper velocity
     */
    public Keygroup (final Element instrumentElement, final Element layersElement, final int velocityLow, final int velocityHigh)
    {
        this (instrumentElement, layersElement, velocityLow, velocityHigh, true);
    }


    private Keygroup (final Element instrumentElement, final Element layersElement, final int velocityLow, final int velocityHigh, final boolean isSequence)
    {
        this.instrumentElement = instrumentElement;
        this.layersElement = layersElement;
        this.velocityLow = velocityLow;
        this.velocityHigh = velocityHigh;
        this.isSequence = isSequence;
    }


    /**
     * Check if this is a sequence keygroup.
     * 
     * @return True if it is a sequence keygroup
     */
    public boolean isSequence ()
    {
        return this.isSequence;
    }


    /**
     * Get the instrument element.
     * 
     * @return The instrument element
     */
    public Element getInstrumentElement ()
    {
        return this.instrumentElement;
    }


    /**
     * Add a layer to the keygroup.
     *
     * @param layerElement The layer to add
     */
    public void addLayer (final Element layerElement)
    {
        this.layersElement.appendChild (layerElement);
        this.layerElements.add (layerElement);
    }


    /**
     * Get the number of layers in the keygroup.
     *
     * @return The number
     */
    public int getLayerCount ()
    {
        return this.layerElements.size ();
    }


    /**
     * Get the bottom velocity of the keygroup.
     *
     * @return The bottom velocity of the keygroup
     */
    public int getVelocityLow ()
    {
        return this.velocityLow;
    }


    /**
     * Get the top velocity of the keygroup.
     *
     * @return The top velocity of the keygroup
     */
    public int getVelocityHigh ()
    {
        return this.velocityHigh;
    }
}
