package de.mossgrabers.convertwithmoss.format.nki;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.XMLUtils;

public class K2MetadataFileParser extends AbstractNKIMetadataFileParser {


	public K2MetadataFileParser(INotifier             notifier, 
			                    final IMetadataConfig metadata, 
			                    final File            sourceFolder, 
			                    final File            processedFile) {
		super(notifier, metadata, sourceFolder, processedFile, new K2Tag());
	}

	
	@Override
	protected Element[] findProgramElements(Element top) {
    	Element programsElement = XMLUtils.getChildElementByName (top, K2Tag.PROGRAMS);
    	
        if (programsElement == null)
        {
            this.notifier.logError (BAD_METADATA_FILE);
            return null;
        }

        Element[] programElements = XMLUtils.getChildElementsByName (programsElement, tags.program(), false);
        return programElements;
	}


	@Override
	protected boolean hasTarget(Element modulator, String expectedTargetValue) {
		Element targetsElement = XMLUtils.getChildElementByName(modulator, K2Tag.TARGETS_ELEMENT);
		if(targetsElement == null)
			return false;
		
		return (findElementWithParameters(targetsElement, K2Tag.TARGET_ELEMENT, 
				                     tags.targetParam(),    tags.targetVolValue(), 
				                     tags.intensityParam(), "1") != null);
	}


	@Override
	protected List<IMultisampleSource> readFile(File sourceFile) {
        // intentionally left empty (not used)
		return null;
	}


	@Override
	protected String readPitchBendIntensity(Element modulator) {
		String intensity = null;
		
		Element targetsElement = XMLUtils.getChildElementByName(modulator, K2Tag.TARGETS_ELEMENT);
		
		if(targetsElement == null)
			return intensity;
		
		Element[] targetElements = XMLUtils.getChildElementsByName(targetsElement, K2Tag.TARGET_ELEMENT, false);
		
		if(targetElements == null)
			return intensity;

		Element targetElement 
		        = findElementWithParameters(targetsElement, 
		        		                    K2Tag.TARGET_ELEMENT, 
		        		                    tags.targetParam(), tags.pitchValue());
		
		if(targetElement == null)
			return intensity;
		
		Map<String, String> targetElementParams = readValueMap(targetElement);
		intensity = targetElementParams.get(tags.intensityParam());
		
		return intensity;
	}


	@Override
	protected TriggerType getTriggerTypeFromGroupElement(Map<String, String> groupParameters) {
		TriggerType triggerType = TriggerType.ATTACK;
		
		String releaseTrigParam = groupParameters.get(K2Tag.RELEASE_TRIGGER_PARAM);
		
		if(releaseTrigParam != null)
			if(releaseTrigParam.equals(tags.yes()))
				triggerType = TriggerType.RELEASE;
		
		return triggerType;
	}

	private enum SmpFNameParsingState{NEUTRAL, DIR_UP, DIR_SUB_LEN, DIR_SUB, UNKNOWN_FRACTION, FILENAME};	

	@Override
	protected String decodeEncodedSampleFileName(String encodedSampleFileName) {
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


	@Override
	protected boolean isValidTopLevelElement(Element top) {
		return tags.rootContainer().equals(top.getNodeName());
	}



}
