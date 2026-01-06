// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;


/**
 * Helper class for keeping track of created keygroups.
 *
 * @author Jürgen Moßgraber
 */
public class Keygroup
{
    private final boolean       isSequence;
    private final Element       layersElement;
    private final List<Element> layerElements = new ArrayList<> ();
    private final int           velocityLow;
    private final int           velocityHigh;


    /**
     * Constructor for a velocity keygroup.
     *
     * @param layersElement The layers element
     */
    public Keygroup (final Element layersElement)
    {
        this (layersElement, -1, -1, false);
    }


    /**
     * Constructor for a sequence keygroup.
     *
     * @param layersElement The layers element
     * @param velocityLow The bottom velocity
     * @param velocityHigh The upper velocity
     */
    public Keygroup (final Element layersElement, final int velocityLow, final int velocityHigh)
    {
        this (layersElement, velocityLow, velocityHigh, true);
    }


    private Keygroup (final Element layersElement, final int velocityLow, final int velocityHigh, final boolean isSequence)
    {
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
