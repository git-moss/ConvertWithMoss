// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.format.nki.tag.K2Tag;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;

import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


/**
 * Parses a NKI XML file in K2 format.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 * @author Philip Stolz
 */
public class K2MetadataFileParser extends AbstractNKIMetadataFileParser
{
    private enum SmpFNameParsingState
    {
        NEUTRAL,
        DIR_UP,
        DIR_SUB_LEN,
        DIR_SUB,
        UNKNOWN_FRACTION,
        FILENAME
    }


    /**
     * Constructor.
     *
     * @param notifier the notifier (needed for logging)
     * @param metadata the metadata (needed for considering the user configuration details)
     * @param sourceFolder the source folder
     * @param processedFile the file that is currently being processed
     */
    public K2MetadataFileParser (final INotifier notifier, final IMetadataConfig metadata, final File sourceFolder, final File processedFile)
    {
        super (notifier, metadata, sourceFolder, processedFile, new K2Tag ());
    }


    /** {@inheritDoc} */
    @Override
    protected Element [] findProgramElements (final Element top)
    {
        final Element [] rootContainers;
        if (this.tags.rootContainer ().equals (top.getNodeName ()))
        {
            rootContainers = new Element [1];
            rootContainers[0] = top;
        }
        else
        {
            rootContainers = XMLUtils.getChildElementsByName (top, this.tags.rootContainer (), true);
            if (rootContainers == null)
            {
                this.notifier.logError (BAD_METADATA_FILE);
                return new Element [0];
            }
        }

        final List<Element> programElements = new ArrayList<> ();

        for (final Element rootContainer: rootContainers)
        {
            final Element programsElement = XMLUtils.getChildElementByName (rootContainer, K2Tag.K2_PROGRAMS);
            if (programsElement == null)
            {
                this.notifier.logError (BAD_METADATA_FILE);
                return new Element [0];
            }
            programElements.addAll (Arrays.asList (XMLUtils.getChildElementsByName (programsElement, this.tags.program (), false)));
        }

        return programElements.toArray (new Element [programElements.size ()]);
    }


    /** {@inheritDoc} */
    @Override
    protected boolean hasTarget (final Element modulator, final String expectedTargetValue)
    {
        final Element targetsElement = XMLUtils.getChildElementByName (modulator, K2Tag.K2_TARGETS_ELEMENT);
        if (targetsElement == null)
            return false;

        return this.findElementWithParameters (targetsElement, K2Tag.K2_TARGET_ELEMENT, this.tags.targetParam (), this.tags.targetVolValue (), this.tags.intensityParam (), "1") != null;
    }


    /** {@inheritDoc} */
    @Override
    protected String readPitchBendIntensity (final Element modulator)
    {
        final Element targetsElement = XMLUtils.getChildElementByName (modulator, K2Tag.K2_TARGETS_ELEMENT);
        if (targetsElement == null)
            return null;

        final Element [] targetElements = XMLUtils.getChildElementsByName (targetsElement, K2Tag.K2_TARGET_ELEMENT, false);
        if (targetElements == null)
            return null;

        final Element targetElement = this.findElementWithParameters (targetsElement, K2Tag.K2_TARGET_ELEMENT, this.tags.targetParam (), this.tags.pitchValue ());
        if (targetElement == null)
            return null;

        final Map<String, String> targetElementParams = this.readValueMap (targetElement);
        return targetElementParams.get (this.tags.intensityParam ());
    }


    /** {@inheritDoc} */
    @Override
    protected TriggerType getTriggerTypeFromGroupElement (final Map<String, String> groupParameters)
    {
        final String releaseTrigParam = groupParameters.get (K2Tag.K2_RELEASE_TRIGGER_PARAM);
        return releaseTrigParam != null && releaseTrigParam.equals (this.tags.yes ()) ? TriggerType.RELEASE : TriggerType.ATTACK;
    }


    /** {@inheritDoc} */
    @Override
    protected String decodeEncodedSampleFileName (final String encodedSampleFileName) throws IOException
    {
        SmpFNameParsingState state = SmpFNameParsingState.NEUTRAL;
        int counter = 0;

        final StringBuilder decodedPath = new StringBuilder ();
        StringBuilder lenSB = new StringBuilder ();
        for (int index = 0; index < encodedSampleFileName.length (); index++)
        {
            final char ch = encodedSampleFileName.charAt (index);
            switch (state)
            {
                case NEUTRAL:
                    switch (ch)
                    {
                        case '@':
                            state = SmpFNameParsingState.DIR_UP;
                            break;

                        case 'd':
                            state = SmpFNameParsingState.DIR_SUB_LEN;
                            counter = 3;
                            break;

                        case 'F':
                            counter = 11;
                            state = SmpFNameParsingState.UNKNOWN_FRACTION;
                            break;

                        default:
                            throw new IOException (Functions.getMessage ("IDS_NKI_UNEXPECTED_STATE", encodedSampleFileName));
                    }
                    break;

                case DIR_SUB_LEN:
                    counter--;
                    lenSB.append (ch);
                    if (counter == 0)
                    {
                        counter = Integer.parseInt (lenSB.toString ());
                        lenSB = new StringBuilder ();
                        state = SmpFNameParsingState.DIR_SUB;
                    }
                    break;

                case DIR_SUB:
                    counter--;
                    decodedPath.append (ch);
                    if (counter == 0)
                    {
                        state = SmpFNameParsingState.NEUTRAL;
                        decodedPath.append ('/');
                    }
                    break;

                case DIR_UP:
                    switch (ch)
                    {
                        case 'b':
                            decodedPath.append ("../");
                            break;

                        case 'd':
                            state = SmpFNameParsingState.DIR_SUB_LEN;
                            counter = 3;
                            break;

                        default:
                            throw new IOException (Functions.getMessage ("IDS_NKI_UNEXPECTED_STATE", encodedSampleFileName));
                    }
                    break;

                case UNKNOWN_FRACTION:
                    counter--;
                    if (counter == 0)
                        state = SmpFNameParsingState.FILENAME;
                    break;

                case FILENAME:
                    decodedPath.append (ch);
                    break;
            }
        }
        return decodedPath.toString ();
    }


    /** {@inheritDoc} */
    @Override
    protected boolean isValidTopLevelElement (final Element top)
    {
        return this.tags.rootContainer ().equals (top.getNodeName ()) || K2Tag.K2_BANK_ELEMENT.equals (top.getNodeName ());
    }


    /** {@inheritDoc} */
    @Override
    protected double normalizePanning (final double panningValue)
    {
        // Doesn't need to be normalizes for K2 format
        return panningValue;
    }
}
