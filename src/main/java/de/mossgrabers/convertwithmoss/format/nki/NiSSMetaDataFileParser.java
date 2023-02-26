package de.mossgrabers.convertwithmoss.format.nki;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.XMLUtils;

import org.w3c.dom.Element;

import java.io.File;
import java.util.List;
import java.util.Map;


public class NiSSMetaDataFileParser extends AbstractNKIMetadataFileParser
{

    public NiSSMetaDataFileParser (final INotifier notifier, final IMetadataConfig metadata, final File sourceFolder, final File processedFile)
    {
        super (notifier, metadata, sourceFolder, processedFile, new NiSSTag ());
    }


    @Override
    protected Element [] findProgramElements (final Element top)
    {
        if (this.tags.program ().equals (top.getNodeName ()))
        {
            final Element [] programElements = new Element [1];
            programElements[0] = top;
            return programElements;
        }
        else if (this.tags.rootContainer ().equals (top.getNodeName ()))
        {
            final Element [] programElements = XMLUtils.getChildElementsByName (top, this.tags.program (), false);
            return programElements;
        }
        else
        {
            this.notifier.logError (BAD_METADATA_FILE);
            return null;
        }
    }


    @Override
    protected boolean hasTarget (final Element modulator, final String expectedTargetValue)
    {
        return this.hasNameValuePairs (modulator, this.tags.targetParam (), this.tags.targetVolValue (), this.tags.intensityParam (), "1");
    }


    @Override
    protected List<IMultisampleSource> readFile (final File sourceFile)
    {
        // intentionally left empty (not used)
        return null;
    }


    @Override
    protected String readPitchBendIntensity (final Element modulator)
    {
        String intensity = null;

        if (this.hasNameValuePairs (modulator, this.tags.targetParam (), this.tags.pitchValue ()))
        {
            final Map<String, String> targetElementParams = this.readValueMap (modulator);
            intensity = targetElementParams.get (this.tags.intensityParam ());
        }

        return intensity;
    }


    @Override
    protected TriggerType getTriggerTypeFromGroupElement (final Map<String, String> groupParameters)
    {
        return TriggerType.ATTACK;
    }


    @Override
    protected String decodeEncodedSampleFileName (final String encodedSampleFileName)
    {
        return encodedSampleFileName.replace ('\\', '/');
    }


    @Override
    protected boolean isValidTopLevelElement (final Element top)
    {
        return this.tags.rootContainer ().equals (top.getNodeName ()) || this.tags.program ().equals (top.getNodeName ());
    }


    @Override
    protected double normalizePanning (final double panningValue)
    {
        return (panningValue - 0.5d) / 0.5d;
    }

}
