package de.mossgrabers.convertwithmoss.format.nki;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.InflaterInputStream;

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

public class NkiDetectorTask extends AbstractDetectorTask {

    private static final String BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";
    
    /** stores the currently processed file */
	private File file;
    
    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadata Additional metadata configuration parameters
     */	
	public NkiDetectorTask(final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata) {
        super (notifier, consumer, sourceFolder, metadata, ".nki");
	}

	
	
	@Override
	protected List<IMultisampleSource> readFile(File sourceFile) {
        if (this.waitForDelivery ())
            return Collections.emptyList ();
        
        long offset = determineCompressedDataOffset(sourceFile);

        try
        {
            final String content = this.loadCompressedTextFile (sourceFile, offset);
            return this.parseMetadataFile (sourceFile, content);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
	}
	
	/**
	 * Determines the offset of the compressed xml in the source file.
	 * 
	 * @param sourceFile the source file
	 * @return the offset, if zlib signature was found, -1 else
	 */
	private long determineCompressedDataOffset(File sourceFile) {
		long knownOffsets[] = {0x24, 0xAA};
    	    	
    	byte buffer[] = new byte[2];
        for(long offset : knownOffsets) {
        	FileInputStream in = null;        	
        	
        	try {
    			in = new FileInputStream(sourceFile);
    		} catch (FileNotFoundException e2) {
    			return -1;
    		}
        	
        	try {
				in.skip(offset);
	            int numBytesRead = in.read(buffer);

	            if(numBytesRead != 2) {
	            	in.close();
	            	continue;
	            }
	                
	            
	            if(isZlibSignature(buffer)) {
	            	in.close();
	            	return offset;
	            }				
			} catch (IOException e) {
				// intentionally left empty
			}
        	
            try {
    			in.close();
    		} catch (IOException e) {
    			// intentionally left empty
    		}
        }
        	
        return -1;
	}


    /**
     * Test whether a byte array starts with a zlib signature.
     * 
     * @param byteArr the byte array
     * @return true if byte array starts with a zlib signature, false else
     */
	private boolean isZlibSignature(byte[] byteArr) {
		final byte firstSignatureByte = 0x78;
		final byte secondSignatureBytes[] = {0x01, 0x5E, (byte) 0x9C, (byte) 0xDA, 0x20, 0x7D, (byte) 0xBB, (byte) 0xF9};
		
		if(byteArr == null)
			return false;
		
		if(byteArr.length < 2)
			return false;
		
		if(firstSignatureByte != byteArr[0])
			return false;
		
		for(byte expected : secondSignatureBytes) {
			if(expected == byteArr[1])
				return true;
		}
		
		return false;
	}



	/**
     * Loads a zip-compressed file in UTF-8 encoding. If UTF-8 fails a string is created anyway but with
     * unspecified behavior.s
     *
     * @param file The file to load
     * @param offset the offset where the zip-compressed part begins
     * @return The loaded text
     * @throws IOException Could not load the file
	 */
    private String loadCompressedTextFile(File file, long offset) throws IOException {
    	this.file = file;
		InputStream inputStream = null;  
		String result = "";
        FileInputStream fileInputStream = new FileInputStream(file);
        fileInputStream.skip(offset);
		inputStream = new InflaterInputStream(fileInputStream);      	   
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (int length; (length = inputStream.read(buffer)) != -1; ) {
        	outputStream.write(buffer, 0, length);
        }
        
        try {
			result = outputStream.toString("UTF-8");
		} catch (UnsupportedEncodingException ex) {
            result = new String (outputStream.toString());
            this.notifier.logError ("IDS_NOTIFY_ERR_ILLEGAL_CHARACTER", ex);
		}
        
        inputStream.close();
        
        return result;
	}

	/**
     * Load and parse the metadata description file.
     *
     * @param file The file
     * @param content The content to parse
     * @return The parsed multisample source
     */
    private List<IMultisampleSource> parseMetadataFile (final File file, final String content)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();
        
        try
        {
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseDescription (file, document);
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
     * @param file The file which contained the XML document
     * @param document The metadata XML document
     * @return The parsed multisample source
     * @throws FileNotFoundException The WAV file could not be found
     */
    private List<IMultisampleSource> parseDescription (final File file, final Document document) throws FileNotFoundException
    {
        final Element top = document.getDocumentElement ();

        
        if (!K2Tag.ROOT.equals (top.getNodeName ()))
        {
            this.notifier.logError (BAD_METADATA_FILE);
            return Collections.emptyList ();
        }
                
        final Element programsElement = XMLUtils.getChildElementByName (top, K2Tag.PROGRAMS);
        if (programsElement == null)
        {
            this.notifier.logError (BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        final Element[] programElements = XMLUtils.getChildElementsByName (programsElement, K2Tag.PROGRAM, false);
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
        	IMultisampleSource multisampleSource = parseK2Program(programElement);
        	if(multisampleSource != null)
        		multisampleSources.add(multisampleSource);
        }
        
        return multisampleSources;
    }


    /**
     * Retrieves a IMultisampleSource object from a K2_Program element.
     * 
     * @param programElement the program element to retrieve the IMultisampleSource object from
     * @return the IMultisampleSource object that was retrieved from the program element or null, if no IMultisampleSource object could be retrieved
     */
	private IMultisampleSource parseK2Program(Element programElement) {
        if(!programElement.hasAttribute(K2Tag.PROGRAM_NAME)) {
        	this.notifier.logError(BAD_METADATA_FILE);
        	return null;
        }
        
        final String name = programElement.getAttribute(K2Tag.PROGRAM_NAME);

        final String n = this.metadata.isPreferFolderName () ? this.sourceFolder.getName () : name;
        final String [] parts = createPathParts (file.getParentFile (), this.sourceFolder, n);
        final MultisampleSource multisampleSource = new MultisampleSource (file, parts, name, this.subtractPaths (this.sourceFolder, file));
        
        // Use same guessing on the filename...
        multisampleSource.setCreator (TagDetector.detect (parts, this.metadata.getCreatorTags (), this.metadata.getCreatorName ()));
        multisampleSource.setCategory (TagDetector.detectCategory (parts));
        multisampleSource.setKeywords (TagDetector.detectKeywords (parts));
       
        final Map<String, String> programParameters = readK2Parameters(programElement);
        
        final Element[] groupElements = getGroupElements(programElement);
        final Element[] zoneElements  = getZoneElements(programElement);
        
        
        List<IVelocityLayer> velocityLayers = getVelocityLayers(programParameters, groupElements, zoneElements);
               
        multisampleSource.setVelocityLayers(velocityLayers);

        return multisampleSource;	
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
        
		Map<String, String> groupParameters = readK2Parameters(groupElement);
		
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
	 * Reads the pitch bend configuration from a group element.
	 * 
	 * @param groupElement the group element
	 * @return the number of semitones (up=down)
	 */
	private int readGroupPitchBend(Element groupElement) {
		int pitchBend = -1;
		
		Element modulatorsElement = XMLUtils.getChildElementByName(groupElement, K2Tag.EXT_MODULATORS_ELEMENT);
		if(modulatorsElement == null)
			return pitchBend;

		Element[] modulators = XMLUtils.getChildElementsByName(modulatorsElement, K2Tag.EXT_MODULATOR_ELEMENT, false);
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
		
		if(!modulatorParams.containsKey(K2Tag.SOURCE_PARAM))
			return pitchBend;
		
		String source = modulatorParams.get(K2Tag.SOURCE_PARAM);

		if(!source.equals(K2Tag.PITCH_BEND_VALUE))
			return pitchBend;
		
		Element targetsElement = XMLUtils.getChildElementByName(modulator, K2Tag.TARGETS_ELEMENT);
		
		if(targetsElement == null)
			return pitchBend;
		
		Element[] targetElements = XMLUtils.getChildElementsByName(targetsElement, K2Tag.TARGET_ELEMENT, false);
		
		if(targetElements == null)
			return pitchBend;

		Element targetElement 
		        = findElementWithParameters(targetsElement, 
		        		                    K2Tag.TARGET_ELEMENT, 
		        		                    K2Tag.TARGET_PARAM, K2Tag.PITCH_VALUE);
		
		if(targetElement == null)
			return pitchBend;
		
		Map<String, String> targetElementParams = readValueMap(targetElement);
		String intensity = targetElementParams.get(K2Tag.INTENSITY_PARAM);
		
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
	 * Reads an amplitude envelope from a group element.
	 * 
	 * @param groupElement the group element
	 * @return the amp envelope, if one could be found, otherwise null
	 */
	private IEnvelope readGroupAmpEnv(Element groupElement) {
		IEnvelope env = null;
		
		Element modulatorsElement = XMLUtils.getChildElementByName(groupElement, K2Tag.INT_MODULATORS_ELEMENT);
		if(modulatorsElement == null)
			return env;
		
		Element[] modulators = XMLUtils.getChildElementsByName(modulatorsElement, K2Tag.INT_MODULATOR_ELEMENT, false);
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
		
		Element envElement = XMLUtils.getChildElementByName(modulator, K2Tag.ENVELOPE_ELEMENT);
		if(envElement == null)
			return env;
		
		if(hasNameValuePairs(modulator, K2Tag.BYPASS_PARAM, K2Tag.YES))
			return env;

		Element targetsElement = XMLUtils.getChildElementByName(modulator, K2Tag.TARGETS_ELEMENT);
		if(targetsElement == null)
			return env;
		
		if(findElementWithParameters(targetsElement, K2Tag.TARGET_ELEMENT, 
				                     K2Tag.TARGET_PARAM,    K2Tag.TARGET_VOL_VALUE, 
				                     K2Tag.INTENSITY_PARAM, "1") != null) {
			
			env = readEnvelopeFromEnvelopeElement(envElement);
		}
		
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
    	
    	String envType = envElement.getAttribute(K2Tag.ENV_TYPE_ATTRIBETE);
    	if(envType == null)
    		return null;
    	
        DefaultEnvelope env = new DefaultEnvelope();
        
        double attackTimeMs;
		try {
			attackTimeMs = getDouble(envParams, K2Tag.ATTACK_PARAM);

			env.setAttack(attackTimeMs / 1000.0d);

			double holdTimeMs   = getDouble(envParams, K2Tag.HOLD_PARAM);
			env.setHold(holdTimeMs / 1000.0d);

			double decayTimeMs  = getDouble(envParams, K2Tag.DECAY_PARAM);
			env.setDecay(decayTimeMs / 1000.0d);

			if(!envType.equals(K2Tag.AHD_ENV_TYPE_VALUE)) {
				double sustainLinear = getDouble(envParams, K2Tag.SUSTAIN_PARAM);
				env.setSustain(sustainLinear);

				double releaseTimeMs = getDouble(envParams, K2Tag.RELEASE_PARAM);
				env.setRelease(releaseTimeMs / 1000.0d);
			}
		} catch (ValueNotAvailableException e) {
			return null;
		}
			
		return env;
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
	private Element findElementWithParameters(Element   parentElement, 
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
     * Checks if an element has a value map containing a required set
     * of (name, value) pairs.
     * 
     * @param element the element
     * @param nameValuePairs a number of name, value pairs, e.g. "volume", "1", "bypass", "no"
     * @return true if the element has all name value pairs in its value map, false else.
     */	
	private boolean hasNameValuePairs(Element element, String... nameValuePairs) {		
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
			
			Map<String, String> zoneParameters = readK2Parameters(zoneElement);
			
			try {
				sampleMetadata.setKeyRoot              (getInt(zoneParameters, K2Tag.ROOT_KEY_PARAM));
				sampleMetadata.setKeyLow               (getInt(zoneParameters, K2Tag.LOW_KEY_PARAM));
				sampleMetadata.setKeyHigh              (getInt(zoneParameters, K2Tag.HIGH_KEY_PARAM));	
				sampleMetadata.setVelocityLow          (getInt(zoneParameters, K2Tag.LOW_VELOCITY_PARAM));
				sampleMetadata.setVelocityHigh         (getInt(zoneParameters, K2Tag.HIGH_VELOCITY_PARAM));
				sampleMetadata.setNoteCrossfadeLow     (getInt(zoneParameters, K2Tag.FADE_LOW_PARAM));
				sampleMetadata.setNoteCrossfadeHigh    (getInt(zoneParameters, K2Tag.FADE_HIGH_PARAM));
				sampleMetadata.setVelocityCrossfadeLow (getInt(zoneParameters, K2Tag.FADE_LOW_VEL_PARAM));
				sampleMetadata.setVelocityCrossfadeLow (getInt(zoneParameters, K2Tag.FADE_HIGH_VEL_PARAM));
				
				
				int sampleStart = getInt(zoneParameters, K2Tag.SAMPLE_START_PARAM);
				int sampleEnd   = getInt(zoneParameters, K2Tag.SAMPLE_END_PARAM);
				
				if(sampleEnd > sampleStart) {
					sampleMetadata.setStart(sampleStart);
					sampleMetadata.setStop (sampleEnd);
				}
				
				
				String keyTracking = getString(groupParameters, K2Tag.KEY_TRACKING_PARAM);
				double keyTrackingValue = keyTracking.equals(K2Tag.YES) ? 1.0d : 0.0d;
				sampleMetadata.setKeyTracking(keyTrackingValue);
				
				double zoneVol  = getDouble(zoneParameters,    K2Tag.ZONE_VOL_PARAM);
				double groupVol = getDouble(groupParameters,   K2Tag.VOL_PARAM);
				double progVol  = getDouble(programParameters, K2Tag.VOL_PARAM);
				sampleMetadata.setGain(20.0d * Math.log10(zoneVol * groupVol * progVol));
				
				double zoneTune  = getDouble(zoneParameters,    K2Tag.ZONE_TUNE_PARAM);
				double groupTune = getDouble(groupParameters,   K2Tag.TUNE_PARAM);
				double progTune  = getDouble(programParameters, K2Tag.TUNE_PARAM);
				sampleMetadata.setTune(12.0d * Math.log(zoneTune * groupTune * progTune) / Math.log(2));
				
				double zonePan  = getDouble(zoneParameters,     K2Tag.ZONE_PAN_PARAM);
				double groupPan = getDouble(groupParameters,    K2Tag.PAN_PARAM);
				double progPan  = getDouble(programParameters,  K2Tag.PAN_PARAM);
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
				
			if(groupParameters.containsKey(K2Tag.REVERSE_PARAM)) {
			    String reversed = groupParameters.get(K2Tag.REVERSE_PARAM);
			    boolean isReversed = false;
			    if(reversed.equals(K2Tag.YES)) {
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
	    Element loopsElement = XMLUtils.getChildElementByName(zoneElement, K2Tag.LOOPS_ELEMENT);
	    
	    if(loopsElement == null)
	    	return;
	    
	    Element[] loopElements = XMLUtils.getChildElementsByName(loopsElement, K2Tag.LOOP_ELEMENT, false);
	    
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
				loopStart = getInt(loopParams, K2Tag.LOOP_START_PARAM);
				loopLength     = getInt(loopParams, K2Tag.LOOP_LENGTH_PARAM);	    	
				loopMode = getString(loopParams, K2Tag.MODE_PARAM);
				xFadeLength = getInt(loopParams, K2Tag.XFADE_LENGTH_PARAM);
				alternatingLoop = getString(loopParams, K2Tag.ALTERNATING_LOOP_PARAM);
			} catch (ValueNotAvailableException e) {
				return;
			}
						
	    	if(loopMode.equals(K2Tag.ONESHOT_VALUE))
	    		continue; // oneshot means no loop

	    	LoopType loopType = LoopType.FORWARD;
	    	
	    	if(      loopMode.equals(K2Tag.UNTIL_END_VALUE)
	    	      || loopMode.equals(K2Tag.UNTIL_RELEASE_VALUE)) {
	    		if(alternatingLoop.equals(K2Tag.YES))
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
	 * Retrieves a sample file from a zone element.
	 * 
	 * @param zoneElement the zone element
	 * @return the sample file. Null is returned if file cannot be retrieved successfully.
	 */
	private File getZoneSampleFile(Element zoneElement) {
		Element sampleElement = XMLUtils.getChildElementByName(zoneElement, K2Tag.ZONE_SAMPLE);
		
		if(sampleElement == null)
			return null;
	
		Map<String, String> sampleParameters = readValueMap(sampleElement);
		
		String encodedSampleFileName = sampleParameters.get(K2Tag.SAMPLE_FILE_ATTRIBUTE);
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
	private File getFileFromEncodedSampleFileName(String encodedSampleFileName) {
		String relativePath = decodeEncodedSampleFileName(encodedSampleFileName);
		StringBuilder path = new StringBuilder();
		try {
			path.append(file.getParentFile().getCanonicalPath());
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


	private enum SmpFNameParsingState{NEUTRAL, DIR_UP, DIR_SUB_LEN, DIR_SUB, UNKNOWN_FRACTION, FILENAME};	
	
    /**
     * Decodes an encoded sample file name as used in the NKI's file_ex attribute.
     * 
     * @param encodedSampleFileName the encoded sample file name
     * @return the decoded path
     */
	private String decodeEncodedSampleFileName(String encodedSampleFileName) {
		StringBuilder decodedPath = new StringBuilder();
		
		SmpFNameParsingState state = SmpFNameParsingState.NEUTRAL;
		int counter = 0;
		
		StringBuilder lenSB = new StringBuilder();;
		for(int index = 0; index < encodedSampleFileName.length(); index ++) {
			char ch =  encodedSampleFileName.charAt(index);
			switch(state) {
			case NEUTRAL:
				switch(ch) {
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
				}
				break;
			case DIR_SUB_LEN:
				counter --;
				lenSB.append(ch);
				if(counter == 0) {
					counter = Integer.valueOf(lenSB.toString());
					lenSB = new StringBuilder();
					state = SmpFNameParsingState.DIR_SUB;
				}
				break;
			case DIR_SUB:
				counter --;
				decodedPath.append(ch);
				if(counter == 0) {
					state = SmpFNameParsingState.NEUTRAL;
					decodedPath.append('/');
				}
				break;
			case DIR_UP:
				switch(ch) {
				case 'b':
					decodedPath.append("../");
					break;
				case 'd':
			        state = SmpFNameParsingState.DIR_SUB_LEN;
			        counter = 3;
					break;
				}
				break;
			case UNKNOWN_FRACTION:
				counter --;
				if(counter == 0) {
					state = SmpFNameParsingState.FILENAME;
				}
				break;
			case FILENAME:
				decodedPath.append(ch);
			}
		}
		return decodedPath.toString();
	}



	/**
	 * Retrieves a group element's name.
	 * 
	 * @param groupElement the group element
	 * @return the name as a String. If no name was found, "" is returned.
	 */
	private String getGroupName(Element groupElement) {
		String groupName = groupElement.getAttribute(K2Tag.GROUP_NAME_ATTRIBUTE);
		
		if(groupName == null)
			groupName = "";
		
		return groupName;
	}



	/**
	 * Retrieves a TriggerType from a group's parameters.
	 * @see readK2Parameters 
	 * 
	 * @param groupParameters the group element to retrieve the TriggerType from
	 * @return the TriggerType that could be retrieved. In doubt, TriggerType.ATTACK
	 *         is returned.
	 */
	private TriggerType getTriggerTypeFromGroupElement(Map<String, String> groupParameters) {
		TriggerType triggerType = TriggerType.ATTACK;
		
		String releaseTrigParam = groupParameters.get(K2Tag.RELEASE_TRIGGER_PARAM);
		
		if(releaseTrigParam != null)
			if(releaseTrigParam.equals(K2Tag.YES))
				triggerType = TriggerType.RELEASE;
		
		return triggerType;
	}

	/**
	 * Creates an Element Array from a List of elements
	 * 
	 * @param list the list of elements
	 * @return the Element Array
	 */
    Element[] createElementArray(List<Element> list) {
        Element[] result = new Element[list.size()];
        int idx = 0;
        for(Element el : list)
        	result[idx ++] = el;
        return result;    	
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
		
		int index = XMLUtils.getIntegerAttribute(groupElement, K2Tag.INDEX_ATTRIBUTE, -1);

		if( (index == -1)
		 || (zoneElements == null)) {
            this.notifier.logError (BAD_METADATA_FILE);
            return createElementArray(matchingZoneElements);
		}
	
		for(Element zoneElement : zoneElements) {
		    int groupIndex 
		        = XMLUtils.getIntegerAttribute(zoneElement, 
		        		                       K2Tag.GROUP_INDEX_ATTRIBUTE, -1);
		    
		    if(groupIndex == index)
		    	matchingZoneElements.add(zoneElement);
		}
		
	    return createElementArray(matchingZoneElements);	
	}

	/**
	 * Retrieves a program's zone elements as an array.
	 * 
	 * @param programElement the xml program element
	 * @return the array of zone elements
	 */	
	private Element[] getZoneElements(Element programElement) {
        final Element zoneElement = XMLUtils.getChildElementByName(programElement, K2Tag.ZONES);

        if(zoneElement == null)
        	return null;
        
        return XMLUtils.getChildElementsByName(zoneElement, K2Tag.ZONE, false);
	}

	/**
	 * Retrieves a program's group elements as an array.
	 * 
	 * @param programElement the xml program element
	 * @return the array of group elements
	 */
    private Element[] getGroupElements(Element programElement) {
        final Element groupsElement = XMLUtils.getChildElementByName(programElement, K2Tag.GROUPS);

        if(groupsElement == null)
        	return null;
        
        return XMLUtils.getChildElementsByName(groupsElement, K2Tag.GROUP, false);
	}

	/**
     * Reads the K2 Parameters of a K2 under an xml element.
     * @param element the K2 xml element
     * @return a map with the parameters and their values, an empty map if no parameters are found
     */
	private Map<String, String> readK2Parameters(Element element) {

        Element parametersElement = XMLUtils.getChildElementByName(element, K2Tag.PARAMETERS);
        
        return readValueMap(parametersElement);
	}


	/**
	 * Reads a value map from a given xml element.
	 * 
	 * @param element the xml element
	 * @return the value map. If nothing can be read, an empty map is returned.
	 */
	private Map<String, String> readValueMap(Element element) {
		HashMap<String, String> result = new HashMap<>();	
		
        if(element == null)
        	return result;

        Element[] valueElements = XMLUtils.getChildElementsByName(element, K2Tag.VALUE, false);
        
        if(valueElements == null)
        	return result;
        
        for(Element valueElement : valueElements) {
            final String valueName  = valueElement.getAttribute(K2Tag.VALUE_NAME_ATTRIBUTE);
            final String valueValue = valueElement.getAttribute(K2Tag.VALUE_VAlUE_ATTRIBUTE);

            if((valueName == null) || (valueValue == null))
            	continue;
            
            result.put(valueName, valueValue);
        }
        
        return result;	}
    

}
