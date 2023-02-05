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

public class NiSSMetaDataFileParser extends AbstractNKIMetadataFileParser {

	public NiSSMetaDataFileParser(INotifier       notifier, 
			                      IMetadataConfig metadata, 
			                      final File      sourceFolder, 
			                      final File      processedFile) {
		super(notifier, metadata, sourceFolder, processedFile, new NiSSTag());
	}

	@Override
	protected Element[] findProgramElements(Element top) {
		if(tags.program().equals(top.getNodeName())) {
			Element[] programElements = new Element[1];
			programElements[0] = top;
			return programElements;
		}
		else if(tags.rootContainer().equals(top.getNodeName())) {
	        Element[] programElements = XMLUtils.getChildElementsByName (top, tags.program(), false);
	        return programElements;
		}
		else {
            this.notifier.logError (BAD_METADATA_FILE);
            return null;
		}
	}

	@Override
	protected boolean hasTarget(Element modulator, String expectedTargetValue) {
		return hasNameValuePairs(modulator, 
				tags.targetParam(), tags.targetVolValue(), 
                tags.intensityParam(), "1") ;		
	}

	@Override
	protected List<IMultisampleSource> readFile(File sourceFile) {
		// intentionally left empty (not used)
		return null;
	}

	@Override
	protected String readPitchBendIntensity(Element modulator) {
		String intensity = null;
		
        if(hasNameValuePairs(modulator, tags.targetParam(), tags.pitchValue())) {
    		Map<String, String> targetElementParams = readValueMap(modulator);
    		intensity = targetElementParams.get(tags.intensityParam());        	
        }
				
		return intensity;
	}

	@Override
	protected TriggerType getTriggerTypeFromGroupElement(Map<String, String> groupParameters) {
		return TriggerType.ATTACK;
	}

	@Override
	protected String decodeEncodedSampleFileName(String encodedSampleFileName) {
		return encodedSampleFileName.replace('\\', '/');
	}

	@Override
	protected boolean isValidTopLevelElement(Element top) {
		return (   tags.rootContainer().equals(top.getNodeName())
				|| tags.program().equals(top.getNodeName()));
	}

	
	
}
