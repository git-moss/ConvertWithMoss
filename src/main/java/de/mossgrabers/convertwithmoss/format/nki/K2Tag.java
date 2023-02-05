package de.mossgrabers.convertwithmoss.format.nki;

public class K2Tag extends AbstractTagsAndAttributes {
    //////////////////////////////////////////////////////
    // Elements

    /** The root element. */
    public static final String ROOT_CONTAINER            = "K2_Container";	
    	
    /** The programs element. */
    public static final String PROGRAMS                  = "Programs";

    /** The program element. */
    public static final String PROGRAM                   = "K2_Program";

	/** A K2_Group element of a program. */
	public static final String GROUP                     = "K2_Group";
	
	/** A K2_Zone element of a program. */
	public static final String ZONE                      = "K2_Zone";

	
	/** The release trigger parameter */
	public static final String RELEASE_TRIGGER_PARAM    = "releaseTrigger";
	
	/** The sample file attribute.*/
	public static final String SAMPLE_FILE_ATTRIBUTE    = "file_ex2";

	/** The program volume parameter.*/
	public static final String PROG_VOL_PARAM            = "volume";
	
	/** The program pan parameter.*/
	public static final String PROG_PAN_PARAM            = "pan";
		
	/** The program tune parameter.*/
	public static final String PROG_TUNE_PARAM           = "tune";
	
	/** The internal modulator element.*/
	public static final String INT_MODULATOR_ELEMENT     = "K2_IntMod";
	
	/** The external modulator element.*/
	public static final String EXT_MODULATOR_ELEMENT     = "K2_ExtMod";	
	
	/** The targets element.*/
	public static final String TARGETS_ELEMENT           = "Targets";
	
	/** The target element.*/
	public static final String TARGET_ELEMENT            = "Target";


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
