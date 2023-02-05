package de.mossgrabers.convertwithmoss.format.nki;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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

public abstract class AbstractNKIMetadataFileParser extends AbstractDetectorTask {


	protected static final String                BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";
    protected final AbstractTagsAndAttributes    tags;
    protected final File                         processedFile;
    

	/**
	 * Constructor.
	 * 
	 * @param notifier the notifier (needed for logging)
	 * @param metadata the metadata (needed for considering the user configuration details)
	 * @param sourceFolder the source folder
	 * @param processedFile the file that is currently being processed
	 * @param tags the format specific tags
	 */
	protected AbstractNKIMetadataFileParser(final INotifier notifier, 
			                                final IMetadataConfig metadata, 
			                                final File sourceFolder,
			                                final File processedFile,
			                                AbstractTagsAndAttributes tags) {
		super(notifier, null, sourceFolder, metadata, (String[]) null);
		this.processedFile = processedFile;
		this.tags          = tags;
	}
	
	/**
     * Load and parse the metadata description file.
     *
     * @param file the file
     * @param content the content to parse
     * @return The parsed multisample source
     */
    public List<IMultisampleSource> parseMetadataFile (final File file, final String content) {        
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

        if (! isValidTopLevelElement(top))
        {
            this.notifier.logError (BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        Element[] programElements = findProgramElements(top);
        
        if (programElements == null)
        {
            this.notifier.logError (BAD_METADATA_FILE);
            return Collections.emptyList ();
        }
        else if(programElements.length == 0) {       	
            this.notifier.logError (BAD_METADATA_FILE);
            return Collections.emptyList ();
        }
       
        LinkedList<IMultisampleSource> multisampleSources = new LinkedList<>();
        for(Element programElement : programElements) {
        	IMultisampleSource multisampleSource = parseProgram(programElement);
        	if(multisampleSource != null)
        		multisampleSources.add(multisampleSource);
        }
        
        return multisampleSources;
    }
    
    /**
     * Checks whether a given element is a valid top-level element.
     * 
     * @param top the top-level element.
     * @return true if top is a valid top-level element, false else
     */
    protected abstract boolean isValidTopLevelElement(Element top);

	/**
     * Returns the program elements in the metadata file.
     * 
     * @param top the top element of the document
     * @return an array of program elements, null or an empty array if nothing was found
     */
    protected abstract Element[] findProgramElements(Element top);
    
    /**
     * Parses a program element and retrieves a IMultisampleSource object representing the program.
     * 
     * @param programElement the program element to be parsed
     * @return the IMultisampleSource that could be read from the program 
     *                                or null if program couldn't be read successfully
     */
	private IMultisampleSource parseProgram(Element programElement) {
        if(!programElement.hasAttribute(tags.programName())) {
        	this.notifier.logError(BAD_METADATA_FILE);
        	return null;
        }
        
        final String name = programElement.getAttribute(tags.programName());

        final String n = this.metadata.isPreferFolderName () ? this.sourceFolder.getName () : name;
        final String [] parts = createPathParts (processedFile.getParentFile (), this.sourceFolder, n);
        final MultisampleSource multisampleSource = new MultisampleSource (processedFile, parts, name, this.subtractPaths (this.sourceFolder, processedFile));
        
        // Use same guessing on the filename...
        multisampleSource.setCreator (TagDetector.detect (parts, this.metadata.getCreatorTags (), this.metadata.getCreatorName ()));
        multisampleSource.setCategory (TagDetector.detectCategory (parts));
        multisampleSource.setKeywords (TagDetector.detectKeywords (parts));
       
        final Map<String, String> programParameters = readParameters(programElement);
        
        final Element[] groupElements = getGroupElements(programElement);
        final Element[] zoneElements  = getZoneElements(programElement);
        
        
        List<IVelocityLayer> velocityLayers = getVelocityLayers(programParameters, groupElements, zoneElements);
               
        multisampleSource.setVelocityLayers(velocityLayers);

        return multisampleSource;	
    }
    
	/**
     * Reads the parameters of a program under an xml element.
     * @param element the xml element
     * @return a map with the parameters and their values, an empty map if no parameters are found
     */
	private Map<String, String> readParameters(Element element) {

        Element parametersElement = XMLUtils.getChildElementByName(element, tags.parameters());
        
        return readValueMap(parametersElement);
	}	
	
	/**
	 * Reads a value map from a given xml element.
	 * 
	 * @param element the xml element
	 * @return the value map. If nothing can be read, an empty map is returned.
	 */
	protected Map<String, String> readValueMap(Element element) {
		HashMap<String, String> result = new HashMap<>();	
		
        if(element == null)
        	return result;

        Element[] valueElements = XMLUtils.getChildElementsByName(element, tags.value(), false);
        
        if(valueElements == null)
        	return result;
        
        for(Element valueElement : valueElements) {
            final String valueName  = valueElement.getAttribute(tags.valueNameAttribute());
            final String valueValue = valueElement.getAttribute(tags.valueValueAttribute());

            if((valueName == null) || (valueValue == null))
            	continue;
            
            result.put(valueName, valueValue);
        }
        
        return result;	
	}
    	
	/**
	 * Retrieves a program's group elements as an array.
	 * 
	 * @param programElement the xml program element
	 * @return the array of group elements
	 */
    private Element[] getGroupElements(Element programElement) {
        final Element groupsElement = XMLUtils.getChildElementByName(programElement, tags.groups());

        if(groupsElement == null)
        	return null;
        
        return XMLUtils.getChildElementsByName(groupsElement, tags.group(), false);
	}
    
	/**
	 * Retrieves a program's zone elements as an array.
	 * 
	 * @param programElement the xml program element
	 * @return the array of zone elements
	 */	
	private Element[] getZoneElements(Element programElement) {
        final Element zoneElement = XMLUtils.getChildElementByName(programElement, tags.zones());

        if(zoneElement == null)
        	return null;
        
        return XMLUtils.getChildElementsByName(zoneElement, tags.zone(), false);
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
	private List<IVelocityLayer> getVelocityLayers(Map<String, String> programParameters, 
			                                       Element[]           groupElements,
			                                       Element[]           zoneElements) {	
		if( (groupElements == null)
		 || (zoneElements  == null) ) {
            this.notifier.logError (BAD_METADATA_FILE);
            return Collections.emptyList();
		}

		LinkedList<IVelocityLayer> velocityLayers = new LinkedList<>();
		for(Element groupElement : groupElements) {
			IVelocityLayer velocityLayer
			        = getVelocityLayer(programParameters, 
			        		           groupElement, 
			        		           zoneElements);
			
			if(velocityLayer != null)
				velocityLayers.add(velocityLayer);
		}
		
		return velocityLayers;
	}    

    /**
     * Creates one velocity layer from a program's parameters, a given group element 
     * and the program's zone elements.
     * 
     * @param programParameters the program parameters
     * @param groupElement the group element from which the velocity layer is created
     * @param zoneElements the program's zone elements
     * @return the velocity layer created from the zone element (null, if no velocity
     *         layer could be created)
     */
	private IVelocityLayer getVelocityLayer(Map<String, String> programParameters, 
			                                Element             groupElement,
			                                Element[]           zoneElements) {	    
		if(groupElement == null)
			return null;

		DefaultVelocityLayer velocityLayer = new DefaultVelocityLayer();   
		
		velocityLayer.setName(getGroupName(groupElement));
        
		Map<String, String> groupParameters = readParameters(groupElement);
		
		IEnvelope groupAmpEnv = readGroupAmpEnv(groupElement);
		
		int pitchBend = readGroupPitchBend(groupElement);
        
		
		velocityLayer.setTrigger(
				getTriggerTypeFromGroupElement(groupParameters));
		
		final Element[] groupZones = findGroupZones(groupElement, zoneElements);
		
		velocityLayer.setSampleMetadata(
				getSampleMetadataFromZones(
						programParameters, 
						groupParameters,
						groupAmpEnv,
						groupElement, 
						groupZones,
						pitchBend)
				);
		
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
	private List<ISampleMetadata> getSampleMetadataFromZones(
			Map<String, String> programParameters,
			Map<String, String> groupParameters, 
			IEnvelope groupAmpEnv, Element groupElement, 
			Element[] groupZones, int pitchBend) {
		
		if( (groupElement == null)
		 || (groupZones   == null))
			return Collections.emptyList();
		
		LinkedList<ISampleMetadata> sampleMetadataList = new LinkedList<>();
		
		for(Element zoneElement : groupZones) {
			File sampleFile = getZoneSampleFile(zoneElement);
			
			if(sampleFile == null)
				continue;
			
            if (!this.checkSampleFile (sampleFile))
            	continue;
            
            final DefaultSampleMetadata sampleMetadata = new DefaultSampleMetadata (sampleFile);
            
            try {
				sampleMetadata.addMissingInfoFromWaveFile(true, true);
			} catch (IOException e1) {
				continue;
			}
			
			Map<String, String> zoneParameters = readParameters(zoneElement);
			
			try {
				sampleMetadata.setKeyRoot              (getInt(zoneParameters, tags.rootKeyParam()));
				sampleMetadata.setKeyLow               (getInt(zoneParameters, tags.lowKeyParam()));
				sampleMetadata.setKeyHigh              (getInt(zoneParameters, tags.highKeyParam()));	
				sampleMetadata.setVelocityLow          (getInt(zoneParameters, tags.lowVelocityParam()));
				sampleMetadata.setVelocityHigh         (getInt(zoneParameters, tags.highVelocityParam()));
				sampleMetadata.setNoteCrossfadeLow     (getInt(zoneParameters, tags.fadeLowParam()));
				sampleMetadata.setNoteCrossfadeHigh    (getInt(zoneParameters, tags.fadeHighParam()));
				sampleMetadata.setVelocityCrossfadeLow (getInt(zoneParameters, tags.fadeLowVelParam()));
				sampleMetadata.setVelocityCrossfadeLow (getInt(zoneParameters, tags.fadeHighVelParam()));
				
				
				int sampleStart = getInt(zoneParameters, tags.sampleStartParam());
				int sampleEnd   = getInt(zoneParameters, tags.sampleEndParam());
				
				if(sampleEnd > sampleStart) {
					sampleMetadata.setStart(sampleStart);
					sampleMetadata.setStop (sampleEnd);
				}
				
				
				String keyTracking = getString(groupParameters, tags.keyTrackingParam());
				double keyTrackingValue = keyTracking.equals(tags.yes()) ? 1.0d : 0.0d;
				sampleMetadata.setKeyTracking(keyTrackingValue);
				
				double zoneVol  = getDouble(zoneParameters,    tags.zoneVolParam());
				double groupVol = getDouble(groupParameters,   tags.groupVolParam());
				double progVol  = getDouble(programParameters, tags.progVolParam());
				sampleMetadata.setGain(20.0d * Math.log10(zoneVol * groupVol * progVol));
				
				double zoneTune  = getDouble(zoneParameters,    tags.zoneTuneParam());
				double groupTune = getDouble(groupParameters,   tags.groupTuneParam());
				double progTune  = getDouble(programParameters, tags.progTuneParam());
				sampleMetadata.setTune(12.0d * Math.log(zoneTune * groupTune * progTune) / Math.log(2));
				
				double zonePan  = getDouble(zoneParameters,     tags.zonePanParam());
				double groupPan = getDouble(groupParameters,    tags.groupPanParam());
				double progPan  = getDouble(programParameters,  tags.progPanParam());
				double totalPan = zonePan + groupPan + progPan;
				if(totalPan < -1.0d)
					totalPan = -1.0d;
				else if(totalPan > 1.0d)
					totalPan = 1.0d;
				sampleMetadata.setPanorama(totalPan);
				
			} catch (ValueNotAvailableException e) {
				continue;
			}
			
			if(groupAmpEnv != null) {
				sampleMetadata.getAmplitudeEnvelope().setAttack(groupAmpEnv.getAttack());
				sampleMetadata.getAmplitudeEnvelope().setHold(groupAmpEnv.getHold());
				sampleMetadata.getAmplitudeEnvelope().setDecay(groupAmpEnv.getDecay());
				sampleMetadata.getAmplitudeEnvelope().setSustain(groupAmpEnv.getSustain());
				sampleMetadata.getAmplitudeEnvelope().setRelease(groupAmpEnv.getRelease());
			}
				
			if(groupParameters.containsKey(tags.reverseParam())) {
			    String reversed = groupParameters.get(tags.reverseParam());
			    boolean isReversed = false;
			    if(reversed.equals(tags.yes())) {
			    	isReversed = true;
			    }
			    sampleMetadata.setReversed(isReversed);
			}
			
			readLoopInformation(zoneElement, sampleMetadata);
			
			sampleMetadataList.add(sampleMetadata);
			
			if(pitchBend >= 0) {
				sampleMetadata.setBendUp(pitchBend);
				sampleMetadata.setBendDown(pitchBend);
			}
		}
		
		return sampleMetadataList;
	}	
	
	/**
	 * Reads the loop information from a zone element and writes it to a ISampleMetadata object.
	 * 
	 * @param zoneElement the zome element
	 * @param sampleMetadata the ISampleMetadata object
	 */
	private void readLoopInformation(Element zoneElement, DefaultSampleMetadata sampleMetadata) {
	    Element loopsElement = XMLUtils.getChildElementByName(zoneElement, tags.loopsElement());
	    
	    if(loopsElement == null)
	    	return;
	    
	    Element[] loopElements = XMLUtils.getChildElementsByName(loopsElement, tags.loopElement(), false);
	    
	    if(loopElements == null)
	    	return;
	    
	    for(Element loopElement : loopElements) {
	    	Map<String, String> loopParams = readValueMap(loopElement);
	    	
	    	int loopStart;
	    	int loopLength;
	    	String loopMode;
	    	int xFadeLength;
	    	String alternatingLoop;
			try {
				loopStart = getInt(loopParams, tags.loopStartParam());
				loopLength= getInt(loopParams, tags.loopLengthParam());	    	
				loopMode = getString(loopParams, tags.loopModeParam());
				xFadeLength = getInt(loopParams, tags.xfadeLengthParam());
				alternatingLoop = getString(loopParams, tags.alternatingLoopParam());
			} catch (ValueNotAvailableException e) {
				return;
			}
						
	    	if(loopMode.equals(tags.oneshotValue()))
	    		continue; // oneshot means no loop

	    	LoopType loopType = LoopType.FORWARD;
	    	
	    	if(      loopMode.equals(tags.untilEndValue())
	    	      || loopMode.equals(tags.untilReleaseValue())) {
	    		if(alternatingLoop.equals(tags.yes()))
	    			loopType = LoopType.ALTERNATING;
	    	}
						
	    	DefaultSampleLoop sampleLoop = new DefaultSampleLoop();
	    	sampleLoop.setStart(loopStart);
	    	sampleLoop.setEnd(loopLength + loopStart);
	    	
	    	if(xFadeLength > 0) {
	    		double xFadeFactor = (((double)loopLength) / ((double) xFadeLength));
	    		if(xFadeFactor > 1.0d)
	    			xFadeFactor = 1.0d;
	    		sampleLoop.setCrossfade(xFadeFactor);
	    	}
	    	
	    	sampleLoop.setType(loopType);
	    	
	    	sampleMetadata.addLoop(sampleLoop);
	    }
	}
		
	/**
	 * Returns a String value from a value map.
	 * @param valueMap the value
	 * @param valueName the value's name
	 * @return the String value
	 * @throws ValueNotAvailableException indicates that the valueName is not in the valueMap
	 */
	private String getString(Map<String, String> valueMap, String valueName) throws ValueNotAvailableException {
		String valueStr = valueMap.get(valueName);
		if(valueStr == null)
			throw new ValueNotAvailableException();
		return valueStr;
	}		
	
	/**
	 * Returns an int value from a value map.
	 * @param valueMap the value
	 * @param valueName the value's name
	 * @return the int value
	 * @throws ValueNotAvailableException indicates that the valueName is not in the valueMap
	 */
	private int getInt(Map<String, String> valueMap, String valueName) throws ValueNotAvailableException {
		String valueStr = valueMap.get(valueName);
		if(valueStr == null)
			throw new ValueNotAvailableException();
		return Integer.valueOf(valueStr);
	}	
	
	/**
	 * Retrieves a sample file from a zone element.
	 * 
	 * @param zoneElement the zone element
	 * @return the sample file. Null is returned if file cannot be retrieved successfully.
	 */
	private File getZoneSampleFile(Element zoneElement) {
		Element sampleElement = XMLUtils.getChildElementByName(zoneElement, tags.zoneSample());
		
		if(sampleElement == null)
			return null;
	
		Map<String, String> sampleParameters = readValueMap(sampleElement);
		
		String encodedSampleFileName = sampleParameters.get(tags.sampleFileAttribute());
		if(encodedSampleFileName == null)
			return null;
		
		return getFileFromEncodedSampleFileName(encodedSampleFileName);
	}	
	
    /**
     * Decodes an encoded sample file name and returns the respective File if it can be found.
     * 
     * @param encodedSampleFileName the encoded sample file name
     * @return the File if it can be found, null else
     */
	protected File getFileFromEncodedSampleFileName(String encodedSampleFileName) {
		String relativePath = decodeEncodedSampleFileName(encodedSampleFileName);
		StringBuilder path = new StringBuilder();
		try {
			path.append(processedFile.getParentFile().getCanonicalPath());
		} catch (IOException e) {
			return null;
		}
		path.append('/');
		path.append(relativePath);
		File sampleFile = new File(path.toString());
		if(sampleFile.canRead())
			return sampleFile;
		
		return null;
	}

    /**
     * Decodes an encoded sample file name as used in the NKI's respectife sample file attribute.
     * 
     * @param encodedSampleFileName the encoded sample file name
     * @return the decoded path
     */
	protected abstract String decodeEncodedSampleFileName(String encodedSampleFileName);

	/**
	 * Retrieves a TriggerType from a group's parameters.
	 * @see readParameters 
	 * 
	 * @param groupParameters the group element to retrieve the TriggerType from
	 * @return the TriggerType that could be retrieved. In doubt, TriggerType.ATTACK
	 *         is returned.
	 */
	abstract protected TriggerType getTriggerTypeFromGroupElement(Map<String, String> groupParameters);
	
	/**
	 * Retrieves a group element's name.
	 * 
	 * @param groupElement the group element
	 * @return the name as a String. If no name was found, "" is returned.
	 */
	private String getGroupName(Element groupElement) {
		String groupName = groupElement.getAttribute(tags.groupNameAttribute());
		
		if(groupName == null)
			groupName = "";
		
		return groupName;
	}
	
	/**
	 * Reads an amplitude envelope from a group element.
	 * 
	 * @param groupElement the group element
	 * @return the amp envelope, if one could be found, otherwise null
	 */
	private IEnvelope readGroupAmpEnv(Element groupElement) {
		IEnvelope env = null;
		
		Element modulatorsElement = XMLUtils.getChildElementByName(groupElement, tags.intModulatorsElement());
		if(modulatorsElement == null)
			return env;
		
		Element[] modulators = XMLUtils.getChildElementsByName(modulatorsElement, tags.intModulatorElement(), false);
		if(modulators == null)
			return env;
		
		for(Element modulator : modulators) {
			env = getAmpEnvFromModulator(modulator);
			if(env != null)
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
	private IEnvelope getAmpEnvFromModulator(Element modulator) {
		IEnvelope env = null;
		
		Element envElement = XMLUtils.getChildElementByName(modulator, tags.envelopeElement());
		if(envElement == null)
			return env;
		
		if(hasNameValuePairs(modulator, tags.bypassParam(), tags.yes()))
			return env;

		if(hasTarget(modulator, tags.targetVolValue()))
			env = readEnvelopeFromEnvelopeElement(envElement);
		
		return env;
	}	
	
	/**
	 * Reads an IEnvelope from an envelope xml element.
	 * 
	 * @param envElement the envelope xml element.
	 * @return the IEnvelope, if  envelope could be read successfully, null else.
	 */
    private IEnvelope readEnvelopeFromEnvelopeElement(Element envElement) {
    	Map<String, String> envParams = readValueMap(envElement);
    	
    	String envType = envElement.getAttribute(tags.envTypeAttribute());
    	if(envType == null)
    		return null;
    	
        DefaultEnvelope env = new DefaultEnvelope();
        
        double attackTimeMs;
		try {
			attackTimeMs = getDouble(envParams, tags.attackParam());

			env.setAttack(attackTimeMs / 1000.0d);

			double holdTimeMs   = getDouble(envParams, tags.holdParam());
			env.setHold(holdTimeMs / 1000.0d);

			double decayTimeMs  = getDouble(envParams, tags.decayParam());
			env.setDecay(decayTimeMs / 1000.0d);

			if(!envType.equals(tags.ahdEnvTypeValue())) {
				double sustainLinear = getDouble(envParams, tags.sustainParam());
				env.setSustain(sustainLinear);

				double releaseTimeMs = getDouble(envParams, tags.releaseParam());
				env.setRelease(releaseTimeMs / 1000.0d);
			}
		} catch (ValueNotAvailableException e) {
			return null;
		}
			
		return env;
	}	
	
	/**
	 * Indicates that a value is not available.
	 */
	private class ValueNotAvailableException extends Exception {
		
		private static final long serialVersionUID = 7547848923691939612L;

		/**
		 * Standard constructor.
		 */
		public ValueNotAvailableException() {
			super();
		}
	}    
    
	/**
	 * Returns a double value from a value map.
	 * @param valueMap the value
	 * @param valueName the value's name
	 * @return the double value
	 * @throws ValueNotAvailableException indicates that the valueName is not in the valueMap
	 */
	private double getDouble(Map<String, String> valueMap, String valueName) throws ValueNotAvailableException {
		String valueStr = valueMap.get(valueName);
		if(valueStr == null)
			throw new ValueNotAvailableException();
		return Double.valueOf(valueStr);
	}	    
    
	/**
	 * Retrieves whether a given modulator element has a specific target.
	 * 
	 * @param modulator the modulator
	 * @param expectedTargetValue the target value that is expected
	 * @return true if the modulator has the expected target value, false else
	 */
    protected abstract boolean hasTarget(Element modulator, String expectedTargetValue);

	/**
     * Checks if an element has a value map containing a required set
     * of (name, value) pairs.
     * 
     * @param element the element
     * @param nameValuePairs a number of name, value pairs, e.g. "volume", "1", "bypass", "no"
     * @return true if the element has all name value pairs in its value map, false else.
     */	
	protected boolean hasNameValuePairs(Element element, String... nameValuePairs) {		
			if(nameValuePairs == null)
			return true;
		
		Map<String, String> parameters = readValueMap(element);

		for(int idx = 0; idx < nameValuePairs.length; idx += 2) {
			String name = nameValuePairs[idx];
			if(parameters.containsKey(name)) {
				int valueIdx = idx + 1;
				if(valueIdx < nameValuePairs.length) {
					String value = nameValuePairs[valueIdx];
					if(!parameters.get(name).equals(value))
						return false;
				}
			}
			else
				return false;
		}

	    return true;
	}	
	
	
	/**
     * Finds a child element of a given parent element that has a value map containing a required set
     * of (name, value) pairs.
     * 
     * @param parentElement the parent element
     * @param elementNameToBeFound the name of the element to be found
     * @param nameValuePairs a number of name, value pairs, e.g. "volume", "1", "bypass", "no"
     * @return the element with the value name pairs. If none could be found, null is returned.
     */
	protected Element findElementWithParameters(Element   parentElement, 
			                                  String    elementNameToBeFound,
			                                  String... nameValuePairs) {
		Element[] elementsOfInterest = XMLUtils.getChildElementsByName(parentElement, elementNameToBeFound, false);

		if(elementsOfInterest == null)
			return null;
		

		for(Element elementOfInterest : elementsOfInterest) {
			boolean doesMatch = hasNameValuePairs(elementOfInterest, nameValuePairs);
			
			if(doesMatch)
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
	private int readGroupPitchBend(Element groupElement) {
		int pitchBend = -1;
		
		Element modulatorsElement = XMLUtils.getChildElementByName(groupElement, tags.extModulatorsElement());
		if(modulatorsElement == null)
			return pitchBend;

		Element[] modulators = XMLUtils.getChildElementsByName(modulatorsElement, tags.extModulatorElement(), false);
		if(modulators == null)
			return pitchBend;		

		for(Element modulator : modulators) {
			pitchBend = getPitchBendFromModulator(modulator);
			if(pitchBend >= 0)
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
	private int getPitchBendFromModulator(Element modulator) {
		int pitchBend = -1;

		Map<String, String> modulatorParams = readValueMap(modulator);
		if(modulatorParams == null)
			return pitchBend;
		
		if(!modulatorParams.containsKey(tags.sourceParam()))
			return pitchBend;
		
		String source = modulatorParams.get(tags.sourceParam());

		if(!source.equals(tags.pitchBendValue()))
			return pitchBend;
		
		String intensity = readPitchBendIntensity(modulator);
				
		if(intensity == null)
			return pitchBend;
		
		double pitchOctaves = Double.valueOf(intensity);
		double pitchCents = pitchOctaves * 1200;
		pitchBend = (int)  Math.round(pitchCents);
		if(pitchBend < 0)
			pitchBend = -pitchBend;
		
		if(pitchBend > 9600)
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
	private Element[] findGroupZones(Element groupElement, Element[] zoneElements) {
		LinkedList<Element> matchingZoneElements = new LinkedList<>();
		
		int index = XMLUtils.getIntegerAttribute(groupElement, tags.indexAttribute(), -1);

		if( (index == -1)
		 || (zoneElements == null)) {
            this.notifier.logError (BAD_METADATA_FILE);
            return createElementArray(matchingZoneElements);
		}
	
		for(Element zoneElement : zoneElements) {
		    int groupIndex 
		        = XMLUtils.getIntegerAttribute(zoneElement, 
		        		                       tags.groupIndexAttribute(), -1);
		    
		    if(groupIndex == index)
		    	matchingZoneElements.add(zoneElement);
		}
		
	    return createElementArray(matchingZoneElements);	
	}
	
	/**
	 * Creates an Element Array from a List of elements
	 * 
	 * @param list the list of elements
	 * @return the Element Array
	 */
    private static Element[] createElementArray(List<Element> list) {
        Element[] result = new Element[list.size()];
        int idx = 0;
        for(Element el : list)
        	result[idx ++] = el;
        return result;    	
    }
	
	
	
	/**
	 * Reads the pitch bend intensity value from a modulator element.
	 * 
	 * @param modulator the modulator element
	 * @return the pitch bend intensity value or null, if intensity couldn't be read.
	 */
	protected abstract String readPitchBendIntensity(Element modulator);
	
}
