package de.mossgrabers.convertwithmoss.format.nki;

public class NiSSTag extends AbstractTagsAndAttributes {

     /** The optional root element. */
    public static final String ROOT_CONTAINER            = "NiSS_Bank";			
	
    /** The program element. */
    public static final String PROGRAM                   = "NiSS_Program";
    
	/** A NiSS_Group element of a program. */
	public static final String GROUP                     = "NiSS_Group";

	/** A NiSS_Zone element of a program. */
	public static final String ZONE                      = "NiSS_Zone";	
	
	/** The internal modulator element.*/
	public static final String INT_MODULATOR_ELEMENT     = "NiSS_IntMod";	
	
	/** The external modulator element.*/
	public static final String EXT_MODULATOR_ELEMENT     = "NiSS_ExtMod";	
	
	/** The sample file attribute.*/
	public static final String SAMPLE_FILE_ATTRIBUTE     = "file";
	
	/** The program volume parameter.*/
	public static final String PROG_VOL_PARAM            = "masterVolume";
	
	/** The program tune parameter.*/
	public static final String PROG_TUNE_PARAM          = "masterTune";
	
	/** The program pan parameter.*/
	public static final String PROG_PAN_PARAM          = "masterPan";	
	
	@Override
	public String program() {
		return PROGRAM;
	}

	@Override
	public String group() {
		return GROUP;
	}	
	
	@Override
	public String zone() {
		return ZONE;
	}

	@Override
	public String intModulatorElement() {
		return INT_MODULATOR_ELEMENT;
	}

	@Override
	public String extModulatorElement() {
		return EXT_MODULATOR_ELEMENT;
	}

	@Override
	public String sampleFileAttribute() {
		return SAMPLE_FILE_ATTRIBUTE;
	}

	@Override
	public String progVolParam() {
		return PROG_VOL_PARAM;
	}

	@Override
	public String progTuneParam() {
		return PROG_TUNE_PARAM;
	}
	
	@Override
	public String progPanParam() {
		return PROG_PAN_PARAM;
	}
	
	@Override
	public String rootContainer() {
		return ROOT_CONTAINER;
	}		
	
}
