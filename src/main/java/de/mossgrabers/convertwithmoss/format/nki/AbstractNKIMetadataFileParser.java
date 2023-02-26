package de.mossgrabers.convertwithmoss.format.nki;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.MultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.IVelocityLayer;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultVelocityLayer;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public abstract class AbstractNKIMetadataFileParser extends AbstractDetectorTask
{

    protected static final String             BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";
    protected final AbstractTagsAndAttributes tags;
    protected final File                      processedFile;


    /**
     * Constructor.
     *
     * @param notifier the notifier (needed for logging)
     * @param metadata the metadata (needed for considering the user configuration details)
     * @param sourceFolder the source folder
     * @param processedFile the file that is currently being processed
     * @param tags the format specific tags
     */
    protected AbstractNKIMetadataFileParser (final INotifier notifier, final IMetadataConfig metadata, final File sourceFolder, final File processedFile, final AbstractTagsAndAttributes tags)
    {
        super (notifier, null, sourceFolder, metadata, (String []) null);
        this.processedFile = processedFile;
        this.tags = tags;
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param file the file
     * @param content the content to parse
     * @return The parsed multisample source
     */
    public List<IMultisampleSource> parseMetadataFile (final File file, final String content)
    {
        try
        {
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseDescription (document);
        }
        catch (final SAXException ex)
        {
            this.notifier.logError (BAD_METADATA_FILE, ex);
        }
        catch (final FileNotFoundException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Process the multisample metadata file and the related wave files.
     *
     * @param document The metadata XML document
     * @return The parsed multisample source
     * @throws FileNotFoundException The WAV file could not be found
     */
    private List<IMultisampleSource> parseDescription (final Document document) throws FileNotFoundException
    {
        final Element top = document.getDocumentElement ();

        if (!this.isValidTopLevelElement (top))
        {
            this.notifier.logError (BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        final Element [] programElements = this.findProgramElements (top);

        if (programElements == null)
        {
            this.notifier.logError (BAD_METADATA_FILE);
            return Collections.emptyList ();
        }
        else if (programElements.length == 0)
        {
            this.notifier.logError (BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        final LinkedList<IMultisampleSource> multisampleSources = new LinkedList<> ();
        for (final Element programElement: programElements)
        {
            final IMultisampleSource multisampleSource = this.parseProgram (programElement);
            if (multisampleSource != null)
                multisampleSources.add (multisampleSource);
        }

        return multisampleSources;
    }


    /**
     * Checks whether a given element is a valid top-level element.
     *
     * @param top the top-level element.
     * @return true if top is a valid top-level element, false else
     */
    protected abstract boolean isValidTopLevelElement (Element top);


    /**
     * Returns the program elements in the metadata file.
     *
     * @param top the top element of the document
     * @return an array of program elements, null or an empty array if nothing was found
     */
    protected abstract Element [] findProgramElements (Element top);


    /**
     * Parses a program element and retrieves a IMultisampleSource object representing the program.
     *
     * @param programElement the program element to be parsed
     * @return the IMultisampleSource that could be read from the program or null if program
     *         couldn't be read successfully
     */
    private IMultisampleSource parseProgram (final Element programElement)
    {
        if (!programElement.hasAttribute (this.tags.programName ()))
        {
            this.notifier.logError (BAD_METADATA_FILE);
            return null;
        }

        final String name = programElement.getAttribute (this.tags.programName ());

        final String n = this.metadata.isPreferFolderName () ? this.sourceFolder.getName () : name;
        final String [] parts = createPathParts (this.processedFile.getParentFile (), this.sourceFolder, n);
        final MultisampleSource multisampleSource = new MultisampleSource (this.processedFile, parts, name, this.subtractPaths (this.sourceFolder, this.processedFile));

        // Use same guessing on the filename...
        multisampleSource.setCreator (TagDetector.detect (parts, this.metadata.getCreatorTags (), this.metadata.getCreatorName ()));
        multisampleSource.setCategory (TagDetector.detectCategory (parts));
        multisampleSource.setKeywords (TagDetector.detectKeywords (parts));

        final Map<String, String> programParameters = this.readParameters (programElement);

        final Element [] groupElements = this.getGroupElements (programElement);
        final Element [] zoneElements = this.getZoneElements (programElement);

        final List<IVelocityLayer> velocityLayers = this.getVelocityLayers (programParameters, groupElements, zoneElements);

        multisampleSource.setVelocityLayers (velocityLayers);

        return multisampleSource;
    }


    /**
     * Reads the parameters of a program under an xml element.
     *
     * @param element the xml element
     * @return a map with the parameters and their values, an empty map if no parameters are found
     */
    private Map<String, String> readParameters (final Element element)
    {

        final Element parametersElement = XMLUtils.getChildElementByName (element, this.tags.parameters ());

        return this.readValueMap (parametersElement);
    }


    /**
     * Reads a value map from a given xml element.
     *
     * @param element the xml element
     * @return the value map. If nothing can be read, an empty map is returned.
     */
    protected Map<String, String> readValueMap (final Element element)
    {
        final HashMap<String, String> result = new HashMap<> ();

        if (element == null)
            return result;

        final Element [] valueElements = XMLUtils.getChildElementsByName (element, this.tags.value (), false);

        if (valueElements == null)
            return result;

        for (final Element valueElement: valueElements)
        {
            final String valueName = valueElement.getAttribute (this.tags.valueNameAttribute ());
            final String valueValue = valueElement.getAttribute (this.tags.valueValueAttribute ());

            if (valueName == null || valueValue == null)
                continue;

            result.put (valueName, valueValue);
        }

        return result;
    }


    /**
     * Retrieves a program's group elements as an array.
     *
     * @param programElement the xml program element
     * @return the array of group elements
     */
    private Element [] getGroupElements (final Element programElement)
    {
        final Element groupsElement = XMLUtils.getChildElementByName (programElement, this.tags.groups ());

        if (groupsElement == null)
            return null;

        return XMLUtils.getChildElementsByName (groupsElement, this.tags.group (), false);
    }


    /**
     * Retrieves a program's zone elements as an array.
     *
     * @param programElement the xml program element
     * @return the array of zone elements
     */
    private Element [] getZoneElements (final Element programElement)
    {
        final Element zoneElement = XMLUtils.getChildElementByName (programElement, this.tags.zones ());

        if (zoneElement == null)
            return null;

        return XMLUtils.getChildElementsByName (zoneElement, this.tags.zone (), false);
    }


    /**
     * Creates velocity layers from a program's parameters and its group and zone elements.
     *
     * @param programParameters the program parameters
     * @param groupElements the group elements
     * @param zoneElements the zone elements
     *
     * @return the velocity layers created (empty list is returned if nothing was created)
     */
    private List<IVelocityLayer> getVelocityLayers (final Map<String, String> programParameters, final Element [] groupElements, final Element [] zoneElements)
    {
        if (groupElements == null || zoneElements == null)
        {
            this.notifier.logError (BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        final LinkedList<IVelocityLayer> velocityLayers = new LinkedList<> ();
        for (final Element groupElement: groupElements)
        {
            final IVelocityLayer velocityLayer = this.getVelocityLayer (programParameters, groupElement, zoneElements);

            if (velocityLayer != null)
                velocityLayers.add (velocityLayer);
        }

        return velocityLayers;
    }


    /**
     * Creates one velocity layer from a program's parameters, a given group element and the
     * program's zone elements.
     *
     * @param programParameters the program parameters
     * @param groupElement the group element from which the velocity layer is created
     * @param zoneElements the program's zone elements
     * @return the velocity layer created from the zone element (null, if no velocity layer could be
     *         created)
     */
    private IVelocityLayer getVelocityLayer (final Map<String, String> programParameters, final Element groupElement, final Element [] zoneElements)
    {
        if (groupElement == null)
            return null;

        final DefaultVelocityLayer velocityLayer = new DefaultVelocityLayer ();

        velocityLayer.setName (this.getGroupName (groupElement));

        final Map<String, String> groupParameters = this.readParameters (groupElement);

        final IEnvelope groupAmpEnv = this.readGroupAmpEnv (groupElement);

        final int pitchBend = this.readGroupPitchBend (groupElement);

        velocityLayer.setTrigger (this.getTriggerTypeFromGroupElement (groupParameters));

        final Element [] groupZones = this.findGroupZones (groupElement, zoneElements);

        velocityLayer.setSampleMetadata (this.getSampleMetadataFromZones (programParameters, groupParameters, groupAmpEnv, groupElement, groupZones, pitchBend));

        return velocityLayer;
    }


    /**
     * Creates sample metadata from zones.
     *
     * @param programParameters the program's parameters
     * @param groupParameters the group's parameters
     * @param groupAmpEnv the group's amp envelope
     * @param groupElement the group element from which the metadata is created
     * @param groupZones the zone elements belonging to the group
     * @param pitchBend
     * @return a list of sample metadata object. If nothing can be created, an empty list is
     *         returned.
     */
    private List<ISampleMetadata> getSampleMetadataFromZones (final Map<String, String> programParameters, final Map<String, String> groupParameters, final IEnvelope groupAmpEnv, final Element groupElement, final Element [] groupZones, final int pitchBend)
    {

        if (groupElement == null || groupZones == null)
            return Collections.emptyList ();

        final LinkedList<ISampleMetadata> sampleMetadataList = new LinkedList<> ();

        for (final Element zoneElement: groupZones)
        {
            final File sampleFile = this.getZoneSampleFile (zoneElement);

            if (sampleFile == null || !this.checkSampleFile (sampleFile))
                continue;

            final DefaultSampleMetadata sampleMetadata = new DefaultSampleMetadata (sampleFile);

            try
            {
                sampleMetadata.addMissingInfoFromWaveFile (true, true);
            }
            catch (final IOException e1)
            {
                continue;
            }

            final Map<String, String> zoneParameters = this.readParameters (zoneElement);

            try
            {
                sampleMetadata.setKeyRoot (this.getInt (zoneParameters, this.tags.rootKeyParam ()));
                sampleMetadata.setKeyLow (this.getInt (zoneParameters, this.tags.lowKeyParam ()));
                sampleMetadata.setKeyHigh (this.getInt (zoneParameters, this.tags.highKeyParam ()));
                sampleMetadata.setVelocityLow (this.getInt (zoneParameters, this.tags.lowVelocityParam ()));
                sampleMetadata.setVelocityHigh (this.getInt (zoneParameters, this.tags.highVelocityParam ()));
                sampleMetadata.setNoteCrossfadeLow (this.getInt (zoneParameters, this.tags.fadeLowParam ()));
                sampleMetadata.setNoteCrossfadeHigh (this.getInt (zoneParameters, this.tags.fadeHighParam ()));
                sampleMetadata.setVelocityCrossfadeLow (this.getInt (zoneParameters, this.tags.fadeLowVelParam ()));
                sampleMetadata.setVelocityCrossfadeLow (this.getInt (zoneParameters, this.tags.fadeHighVelParam ()));

                final int sampleStart = this.getInt (zoneParameters, this.tags.sampleStartParam ());
                final int sampleEnd = this.getInt (zoneParameters, this.tags.sampleEndParam ());

                if (sampleEnd > sampleStart)
                {
                    sampleMetadata.setStart (sampleStart);
                    sampleMetadata.setStop (sampleEnd);
                }

                final String keyTracking = this.getString (groupParameters, this.tags.keyTrackingParam ());
                final double keyTrackingValue = keyTracking.equals (this.tags.yes ()) ? 1.0d : 0.0d;
                sampleMetadata.setKeyTracking (keyTrackingValue);

                final double zoneVol = this.getDouble (zoneParameters, this.tags.zoneVolParam ());
                final double groupVol = this.getDouble (groupParameters, this.tags.groupVolParam ());
                final double progVol = this.getDouble (programParameters, this.tags.progVolParam ());
                sampleMetadata.setGain (20.0d * Math.log10 (zoneVol * groupVol * progVol));

                final double zoneTune = this.getDouble (zoneParameters, this.tags.zoneTuneParam ());
                final double groupTune = this.getDouble (groupParameters, this.tags.groupTuneParam ());
                final double progTune = this.getDouble (programParameters, this.tags.progTuneParam ());
                sampleMetadata.setTune (12.0d * Math.log (zoneTune * groupTune * progTune) / Math.log (2));

                final double zonePan = this.getDouble (zoneParameters, this.tags.zonePanParam ());
                final double groupPan = this.getDouble (groupParameters, this.tags.groupPanParam ());
                final double progPan = this.getDouble (programParameters, this.tags.progPanParam ());

                double totalPan = this.normalizePanning (zonePan) + this.normalizePanning (groupPan) + this.normalizePanning (progPan);
                if (totalPan < -1.0d)
                    totalPan = -1.0d;
                else if (totalPan > 1.0d)
                    totalPan = 1.0d;
                sampleMetadata.setPanorama (totalPan);

            }
            catch (final ValueNotAvailableException e)
            {
                continue;
            }

            if (groupAmpEnv != null)
            {
                sampleMetadata.getAmplitudeEnvelope ().setAttack (groupAmpEnv.getAttack ());
                sampleMetadata.getAmplitudeEnvelope ().setHold (groupAmpEnv.getHold ());
                sampleMetadata.getAmplitudeEnvelope ().setDecay (groupAmpEnv.getDecay ());
                sampleMetadata.getAmplitudeEnvelope ().setSustain (groupAmpEnv.getSustain ());
                sampleMetadata.getAmplitudeEnvelope ().setRelease (groupAmpEnv.getRelease ());
            }

            if (groupParameters.containsKey (this.tags.reverseParam ()))
            {
                final String reversed = groupParameters.get (this.tags.reverseParam ());
                boolean isReversed = false;
                if (reversed.equals (this.tags.yes ()))
                {
                    isReversed = true;
                }
                sampleMetadata.setReversed (isReversed);
            }

            this.readLoopInformation (zoneElement, sampleMetadata);

            sampleMetadataList.add (sampleMetadata);

            if (pitchBend >= 0)
            {
                sampleMetadata.setBendUp (pitchBend);
                sampleMetadata.setBendDown (pitchBend);
            }
        }

        return sampleMetadataList;
    }


    /**
     * Normalizes a panning value to a range from -1 to 1 where 0 is center and -1 is left
     *
     * @param panningValue
     * @return the normalizes panning value
     */
    protected abstract double normalizePanning (double panningValue);


    /**
     * Reads the loop information from a zone element and writes it to a ISampleMetadata object.
     *
     * @param zoneElement the zome element
     * @param sampleMetadata the ISampleMetadata object
     */
    private void readLoopInformation (final Element zoneElement, final DefaultSampleMetadata sampleMetadata)
    {
        final Element loopsElement = XMLUtils.getChildElementByName (zoneElement, this.tags.loopsElement ());

        if (loopsElement == null)
            return;

        final Element [] loopElements = XMLUtils.getChildElementsByName (loopsElement, this.tags.loopElement (), false);

        if (loopElements == null)
            return;

        for (final Element loopElement: loopElements)
        {
            final Map<String, String> loopParams = this.readValueMap (loopElement);

            int loopStart;
            int loopLength;
            String loopMode;
            int xFadeLength;
            String alternatingLoop;
            try
            {
                loopStart = this.getInt (loopParams, this.tags.loopStartParam ());
                loopLength = this.getInt (loopParams, this.tags.loopLengthParam ());
                loopMode = this.getString (loopParams, this.tags.loopModeParam ());
                xFadeLength = this.getInt (loopParams, this.tags.xfadeLengthParam ());
                alternatingLoop = this.getString (loopParams, this.tags.alternatingLoopParam ());
            }
            catch (final ValueNotAvailableException e)
            {
                return;
            }

            if (loopMode.equals (this.tags.oneshotValue ()))
                continue; // oneshot means no loop

            LoopType loopType = LoopType.FORWARD;

            if (loopMode.equals (this.tags.untilEndValue ()) || loopMode.equals (this.tags.untilReleaseValue ()))
            {
                if (alternatingLoop.equals (this.tags.yes ()))
                    loopType = LoopType.ALTERNATING;
            }

            final DefaultSampleLoop sampleLoop = new DefaultSampleLoop ();
            sampleLoop.setStart (loopStart);
            sampleLoop.setEnd (loopLength + loopStart);

            if (xFadeLength > 0)
            {
                double xFadeFactor = (double) loopLength / (double) xFadeLength;
                if (xFadeFactor > 1.0d)
                    xFadeFactor = 1.0d;
                sampleLoop.setCrossfade (xFadeFactor);
            }

            sampleLoop.setType (loopType);

            sampleMetadata.addLoop (sampleLoop);
        }
    }


    /**
     * Returns a String value from a value map.
     *
     * @param valueMap the value
     * @param valueName the value's name
     * @return the String value
     * @throws ValueNotAvailableException indicates that the valueName is not in the valueMap
     */
    private String getString (final Map<String, String> valueMap, final String valueName) throws ValueNotAvailableException
    {
        final String valueStr = valueMap.get (valueName);
        if (valueStr == null)
            throw new ValueNotAvailableException ();
        return valueStr;
    }


    /**
     * Returns an int value from a value map.
     *
     * @param valueMap the value
     * @param valueName the value's name
     * @return the int value
     * @throws ValueNotAvailableException indicates that the valueName is not in the valueMap
     */
    private int getInt (final Map<String, String> valueMap, final String valueName) throws ValueNotAvailableException
    {
        final String valueStr = valueMap.get (valueName);
        if (valueStr == null)
            throw new ValueNotAvailableException ();
        return Integer.valueOf (valueStr);
    }


    /**
     * Retrieves a sample file from a zone element.
     *
     * @param zoneElement the zone element
     * @return the sample file. Null is returned if file cannot be retrieved successfully.
     */
    private File getZoneSampleFile (final Element zoneElement)
    {
        final Element sampleElement = XMLUtils.getChildElementByName (zoneElement, this.tags.zoneSample ());

        if (sampleElement == null)
            return null;

        final Map<String, String> sampleParameters = this.readValueMap (sampleElement);

        final String encodedSampleFileName = sampleParameters.get (this.tags.sampleFileAttribute ());
        if (encodedSampleFileName == null)
            return null;

        return this.getFileFromEncodedSampleFileName (encodedSampleFileName);
    }


    /**
     * Decodes an encoded sample file name and returns the respective File if it can be found.
     *
     * @param encodedSampleFileName the encoded sample file name
     * @return the File if it can be found, null else
     */
    protected File getFileFromEncodedSampleFileName (final String encodedSampleFileName)
    {
        final String relativePath = this.decodeEncodedSampleFileName (encodedSampleFileName);
        final StringBuilder path = new StringBuilder ();
        try
        {
            path.append (this.processedFile.getParentFile ().getCanonicalPath ());
        }
        catch (final IOException e)
        {
            return null;
        }
        path.append ('/');
        path.append (relativePath);
        final File sampleFile = new File (path.toString ());
        if (sampleFile.canRead ())
            return sampleFile;

        return null;
    }


    /**
     * Decodes an encoded sample file name as used in the NKI's respectife sample file attribute.
     *
     * @param encodedSampleFileName the encoded sample file name
     * @return the decoded path
     */
    protected abstract String decodeEncodedSampleFileName (String encodedSampleFileName);


    /**
     * Retrieves a TriggerType from a group's parameters.
     *
     * @see readParameters
     *
     * @param groupParameters the group element to retrieve the TriggerType from
     * @return the TriggerType that could be retrieved. In doubt, TriggerType.ATTACK is returned.
     */
    abstract protected TriggerType getTriggerTypeFromGroupElement (Map<String, String> groupParameters);


    /**
     * Retrieves a group element's name.
     *
     * @param groupElement the group element
     * @return the name as a String. If no name was found, "" is returned.
     */
    private String getGroupName (final Element groupElement)
    {
        String groupName = groupElement.getAttribute (this.tags.groupNameAttribute ());

        if (groupName == null)
            groupName = "";

        return groupName;
    }


    /**
     * Reads an amplitude envelope from a group element.
     *
     * @param groupElement the group element
     * @return the amp envelope, if one could be found, otherwise null
     */
    private IEnvelope readGroupAmpEnv (final Element groupElement)
    {
        IEnvelope env = null;

        final Element modulatorsElement = XMLUtils.getChildElementByName (groupElement, this.tags.intModulatorsElement ());
        if (modulatorsElement == null)
            return env;

        final Element [] modulators = XMLUtils.getChildElementsByName (modulatorsElement, this.tags.intModulatorElement (), false);
        if (modulators == null)
            return env;

        for (final Element modulator: modulators)
        {
            env = this.getAmpEnvFromModulator (modulator);
            if (env != null)
                break;
        }

        return env;
    }


    /**
     * Reads an amplitude envelope from a modulator Element.
     *
     * @param modulator the modulator Element
     * @return the amp envelop. If none could be found, null is returned.
     */
    private IEnvelope getAmpEnvFromModulator (final Element modulator)
    {
        IEnvelope env = null;

        final Element envElement = XMLUtils.getChildElementByName (modulator, this.tags.envelopeElement ());
        if (envElement == null || this.hasNameValuePairs (modulator, this.tags.bypassParam (), this.tags.yes ()))
            return env;

        if (this.hasTarget (modulator, this.tags.targetVolValue ()))
            env = this.readEnvelopeFromEnvelopeElement (envElement);

        return env;
    }


    /**
     * Reads an IEnvelope from an envelope xml element.
     *
     * @param envElement the envelope xml element.
     * @return the IEnvelope, if envelope could be read successfully, null else.
     */
    private IEnvelope readEnvelopeFromEnvelopeElement (final Element envElement)
    {
        final Map<String, String> envParams = this.readValueMap (envElement);

        final String envType = envElement.getAttribute (this.tags.envTypeAttribute ());
        if (envType == null)
            return null;

        final DefaultEnvelope env = new DefaultEnvelope ();

        double attackTimeMs;
        try
        {
            attackTimeMs = this.getDouble (envParams, this.tags.attackParam ());

            env.setAttack (attackTimeMs / 1000.0d);

            final double holdTimeMs = this.getDouble (envParams, this.tags.holdParam ());
            env.setHold (holdTimeMs / 1000.0d);

            final double decayTimeMs = this.getDouble (envParams, this.tags.decayParam ());
            env.setDecay (decayTimeMs / 1000.0d);

            if (!envType.equals (this.tags.ahdEnvTypeValue ()))
            {
                final double sustainLinear = this.getDouble (envParams, this.tags.sustainParam ());
                env.setSustain (sustainLinear);

                final double releaseTimeMs = this.getDouble (envParams, this.tags.releaseParam ());
                env.setRelease (releaseTimeMs / 1000.0d);
            }
        }
        catch (final ValueNotAvailableException e)
        {
            return null;
        }

        return env;
    }


    /**
     * Indicates that a value is not available.
     */
    private class ValueNotAvailableException extends Exception
    {

        private static final long serialVersionUID = 7547848923691939612L;


        /**
         * Standard constructor.
         */
        public ValueNotAvailableException ()
        {
            super ();
        }
    }


    /**
     * Returns a double value from a value map.
     *
     * @param valueMap the value
     * @param valueName the value's name
     * @return the double value
     * @throws ValueNotAvailableException indicates that the valueName is not in the valueMap
     */
    private double getDouble (final Map<String, String> valueMap, final String valueName) throws ValueNotAvailableException
    {
        final String valueStr = valueMap.get (valueName);
        if (valueStr == null)
            throw new ValueNotAvailableException ();
        return Double.valueOf (valueStr);
    }


    /**
     * Retrieves whether a given modulator element has a specific target.
     *
     * @param modulator the modulator
     * @param expectedTargetValue the target value that is expected
     * @return true if the modulator has the expected target value, false else
     */
    protected abstract boolean hasTarget (Element modulator, String expectedTargetValue);


    /**
     * Checks if an element has a value map containing a required set of (name, value) pairs.
     *
     * @param element the element
     * @param nameValuePairs a number of name, value pairs, e.g. "volume", "1", "bypass", "no"
     * @return true if the element has all name value pairs in its value map, false else.
     */
    protected boolean hasNameValuePairs (final Element element, final String... nameValuePairs)
    {
        if (nameValuePairs == null)
            return true;

        final Map<String, String> parameters = this.readValueMap (element);

        for (int idx = 0; idx < nameValuePairs.length; idx += 2)
        {
            final String name = nameValuePairs[idx];
            if (parameters.containsKey (name))
            {
                final int valueIdx = idx + 1;
                if (valueIdx < nameValuePairs.length)
                {
                    final String value = nameValuePairs[valueIdx];
                    if (!parameters.get (name).equals (value))
                        return false;
                }
            }
            else
                return false;
        }

        return true;
    }


    /**
     * Finds a child element of a given parent element that has a value map containing a required
     * set of (name, value) pairs.
     *
     * @param parentElement the parent element
     * @param elementNameToBeFound the name of the element to be found
     * @param nameValuePairs a number of name, value pairs, e.g. "volume", "1", "bypass", "no"
     * @return the element with the value name pairs. If none could be found, null is returned.
     */
    protected Element findElementWithParameters (final Element parentElement, final String elementNameToBeFound, final String... nameValuePairs)
    {
        final Element [] elementsOfInterest = XMLUtils.getChildElementsByName (parentElement, elementNameToBeFound, false);

        if (elementsOfInterest == null)
            return null;

        for (final Element elementOfInterest: elementsOfInterest)
        {
            final boolean doesMatch = this.hasNameValuePairs (elementOfInterest, nameValuePairs);

            if (doesMatch)
                return elementOfInterest;
        }

        return null;
    }


    /**
     * Reads the pitch bend configuration from a group element.
     *
     * @param groupElement the group element
     * @return the number of semitones (up=down)
     */
    private int readGroupPitchBend (final Element groupElement)
    {
        int pitchBend = -1;

        final Element modulatorsElement = XMLUtils.getChildElementByName (groupElement, this.tags.extModulatorsElement ());
        if (modulatorsElement == null)
            return pitchBend;

        final Element [] modulators = XMLUtils.getChildElementsByName (modulatorsElement, this.tags.extModulatorElement (), false);
        if (modulators == null)
            return pitchBend;

        for (final Element modulator: modulators)
        {
            pitchBend = this.getPitchBendFromModulator (modulator);
            if (pitchBend >= 0)
                break;
        }

        return pitchBend;
    }


    /**
     * Reads a pitch bend value from a modulator Element.
     *
     * @param modulator the modulator Element
     * @return the pitch bend value. If none could be found, a -1 is returned.
     */
    private int getPitchBendFromModulator (final Element modulator)
    {
        int pitchBend = -1;

        final Map<String, String> modulatorParams = this.readValueMap (modulator);
        if (modulatorParams == null || !modulatorParams.containsKey (this.tags.sourceParam ()))
            return pitchBend;

        final String source = modulatorParams.get (this.tags.sourceParam ());

        if (!source.equals (this.tags.pitchBendValue ()))
            return pitchBend;

        final String intensity = this.readPitchBendIntensity (modulator);

        if (intensity == null)
            return pitchBend;

        final double pitchOctaves = Double.valueOf (intensity);
        final double pitchCents = pitchOctaves * 1200;
        pitchBend = (int) Math.round (pitchCents);
        if (pitchBend < 0)
            pitchBend = -pitchBend;

        if (pitchBend > 9600)
            pitchBend = 9600;

        return pitchBend;
    }


    /**
     * Finds a group's zone elements.
     *
     * @param groupElement the group element
     * @param zoneElements the zone elements from which to find the zones belonging to the group
     * @return an array of zone elements belonging to the group element
     */
    private Element [] findGroupZones (final Element groupElement, final Element [] zoneElements)
    {
        final LinkedList<Element> matchingZoneElements = new LinkedList<> ();

        final int index = XMLUtils.getIntegerAttribute (groupElement, this.tags.indexAttribute (), -1);

        if (index == -1 || zoneElements == null)
        {
            this.notifier.logError (BAD_METADATA_FILE);
            return createElementArray (matchingZoneElements);
        }

        for (final Element zoneElement: zoneElements)
        {
            final int groupIndex = XMLUtils.getIntegerAttribute (zoneElement, this.tags.groupIndexAttribute (), -1);

            if (groupIndex == index)
                matchingZoneElements.add (zoneElement);
        }

        return createElementArray (matchingZoneElements);
    }


    /**
     * Creates an Element Array from a List of elements
     *
     * @param list the list of elements
     * @return the Element Array
     */
    private static Element [] createElementArray (final List<Element> list)
    {
        final Element [] result = new Element [list.size ()];
        int idx = 0;
        for (final Element el: list)
            result[idx++] = el;
        return result;
    }


    /**
     * Reads the pitch bend intensity value from a modulator element.
     *
     * @param modulator the modulator element
     * @return the pitch bend intensity value or null, if intensity couldn't be read.
     */
    protected abstract String readPitchBendIntensity (Element modulator);

}
