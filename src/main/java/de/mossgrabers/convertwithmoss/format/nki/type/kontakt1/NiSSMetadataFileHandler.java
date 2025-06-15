// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt1;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.DefaultInstrumentSource;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.format.nki.AbstractNKIMetadataFileHandler;
import de.mossgrabers.tools.XMLUtils;


/**
 * Parses a NKI XML file in NiSS format.
 *
 * @author Jürgen Moßgraber
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
    protected List<Element> findProgramElements (final Element top)
    {
        if (this.tags.program ().equals (top.getNodeName ()))
            return Collections.singletonList (top);

        if (this.tags.rootContainer ().equals (top.getNodeName ()))
            return XMLUtils.getChildElementsByName (top, this.tags.program ());

        return Collections.emptyList ();
    }


    /** {@inheritDoc} */
    @Override
    protected void readInstrumentParameters (final DefaultInstrumentSource instrumentSource, final Map<String, String> programParameters)
    {
        final String midiChannel = programParameters.get ("midiChannel");
        if (midiChannel != null)
            instrumentSource.setMidiChannel (Integer.parseInt (midiChannel) - 1);

        super.readInstrumentParameters (instrumentSource, programParameters);
    }


    /** {@inheritDoc} */
    @Override
    protected String getModulationTarget (final Element modulator)
    {
        for (final Element valueElement: XMLUtils.getChildElementsByName (modulator, this.tags.value ()))
            // We only support 1 target!
            if (this.tags.targetParam ().equals (valueElement.getAttribute (this.tags.valueNameAttribute ())))
                return valueElement.getAttribute (this.tags.valueValueAttribute ());
        return null;
    }


    /** {@inheritDoc} */
    @Override
    protected double getModulationIntensity (final Element modulator)
    {
        for (final Element valueElement: XMLUtils.getChildElementsByName (modulator, this.tags.value ()))
            // We only support 1 target!
            if (this.tags.intensityParam ().equals (valueElement.getAttribute (this.tags.valueNameAttribute ())))
            {
                final String attribute = valueElement.getAttribute (this.tags.valueValueAttribute ());
                try
                {
                    return attribute == null ? 0 : Double.parseDouble (attribute);
                }
                catch (final NumberFormatException ex)
                {
                    return 0;
                }
            }
        return 0;
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
    protected String readAmplitudeVelocityIntensity (final Element modulator)
    {
        if (this.hasNameValuePairs (modulator, this.tags.targetParam (), this.tags.volumeValue ()))
        {
            final Map<String, String> targetElementParams = this.readValueMap (modulator);
            return targetElementParams.get (this.tags.intensityParam ());
        }
        return null;
    }


    /** {@inheritDoc} */
    @Override
    protected void parseRoundRobin (final Element groupElement, final List<ISampleZone> zones)
    {
        // No round robin information in Kontakt 1
    }


    /** {@inheritDoc} */
    @Override
    protected TriggerType getTriggerTypeFromGroupElement (final Map<String, String> groupParameters)
    {
        return TriggerType.ATTACK;
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


    /** {@inheritDoc} */
    @Override
    protected double denormalizePanning (final double panningValue)
    {
        return 0.5d + panningValue * 0.5d;
    }


    /** {@inheritDoc} */
    @Override
    protected String getTemplatePrefix ()
    {
        return "Kontakt1";
    }
}
