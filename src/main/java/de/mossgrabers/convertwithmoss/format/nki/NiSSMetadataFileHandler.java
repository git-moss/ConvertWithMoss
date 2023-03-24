// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.format.nki.tag.NiSSTag;
import de.mossgrabers.tools.XMLUtils;

import org.w3c.dom.Element;

import java.util.Map;


/**
 * Parses a NKI XML file in NiSS format.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 * @author Philip Stolz
 */
public class NiSSMetadataFileHandler extends AbstractNKIMetadataFileHandler
{
    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     */
    public NiSSMetadataFileHandler (final INotifier notifier)
    {
        super (new NiSSTag (), notifier);
    }


    /** {@inheritDoc} */
    @Override
    protected Element [] findProgramElements (final Element top)
    {
        if (this.tags.program ().equals (top.getNodeName ()))
        {
            return new Element []
            {
                top
            };
        }

        if (this.tags.rootContainer ().equals (top.getNodeName ()))
            return XMLUtils.getChildElementsByName (top, this.tags.program (), false);

        return new Element [0];
    }


    /** {@inheritDoc} */
    @Override
    protected boolean hasTarget (final Element modulator, final String expectedTargetValue)
    {
        return this.hasNameValuePairs (modulator, this.tags.targetParam (), this.tags.targetVolValue (), this.tags.intensityParam (), "1");
    }


    /** {@inheritDoc} */
    @Override
    protected String readPitchBendIntensity (final Element modulator)
    {
        if (this.hasNameValuePairs (modulator, this.tags.targetParam (), this.tags.pitchValue ()))
        {
            final Map<String, String> targetElementParams = this.readValueMap (modulator);
            return targetElementParams.get (this.tags.intensityParam ());
        }
        return null;
    }


    /** {@inheritDoc} */
    @Override
    protected TriggerType getTriggerTypeFromGroupElement (final Map<String, String> groupParameters)
    {
        return TriggerType.ATTACK;
    }


    /** {@inheritDoc} */
    @Override
    protected String decodeEncodedSampleFileName (final String encodedSampleFileName)
    {
        return encodedSampleFileName.replace ('\\', '/');
    }


    /** {@inheritDoc} */
    @Override
    protected boolean isValidTopLevelElement (final Element top)
    {
        return this.tags.rootContainer ().equals (top.getNodeName ()) || this.tags.program ().equals (top.getNodeName ());
    }


    /** {@inheritDoc} */
    @Override
    protected double normalizePanning (final double panningValue)
    {
        return (panningValue - 0.5d) / 0.5d;
    }
}
