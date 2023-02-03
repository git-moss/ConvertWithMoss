package de.mossgrabers.convertwithmoss.format.nki;

public class K2Tag {
    //////////////////////////////////////////////////////
    // Elements

    /** The root element. */
    public static final String ROOT                     = "K2_Container";	
    	
    /** The programs element. */
    public static final String PROGRAMS                  = "Programs";

    /** The program element. */
    public static final String PROGRAM                   = "K2_Program";
    
    /** The program element name attribute. */
    public static final String PROGRAM_NAME              = "name";

    /** The parameters element. */
	public static final String PARAMETERS                = "Parameters";
	
	/** The value element. */
	public static final String VALUE                     = "V";

	/** The value name attribute. */
	public static final String VALUE_NAME_ATTRIBUTE      = "name";

	/** The value name attribute. */
	public static final String VALUE_VAlUE_ATTRIBUTE     = "value";

	/** The groups element of a program. */
	public static final String GROUPS                    = "Groups";
	
	/** A K2_Group element of a program. */
	public static final String GROUP                     = "K2_Group";

	/** The zones element of a program. */
	public static final String ZONES                     = "Zones";
	
	/** A K2_Zone element of a program. */
	public static final String ZONE                      = "K2_Zone";
	
	/** The group index attribute. */
	public static final String GROUP_INDEX_ATTRIBUTE    = "groupIdx";
	
	/** The group name attribute. */
	public static final String GROUP_NAME_ATTRIBUTE     = "name";	
	
	/** The index attribute. */
	public static final String INDEX_ATTRIBUTE          = "index";
	
	/** The release trigger parameter */
	public static final String RELEASE_TRIGGER_PARAM    = "releaseTrigger";

	/** The yes value. */
	public static final String YES                      = "yes";

	/** The sample element of a program.*/
	public static final String ZONE_SAMPLE              = "Sample";
	
	/** The sample file attribute.*/
	public static final String SAMPLE_FILE_ATTRIBUTE    = "file_ex2";

	/** The root key parameter.*/
	public static final String ROOT_KEY_PARAM           = "rootKey";

	/** The low key parameter.*/
	public static final String LOW_KEY_PARAM            = "lowKey";

	/** The high key parameter.*/
	public static final String HIGH_KEY_PARAM           = "highKey";

	/** The low key parameter.*/
	public static final String LOW_VELOCITY_PARAM       = "lowVelocity";

	/** The high key parameter.*/
	public static final String HIGH_VELOCITY_PARAM      = "highVelocity";	

	/** The fade low parameter.*/
	public static final String FADE_LOW_PARAM           = "fadeLowKey";	

	/** The fade high parameter.*/
	public static final String FADE_HIGH_PARAM           = "fadeHighKey";	

	/** The fade low velocity parameter.*/
	public static final String FADE_LOW_VEL_PARAM        = "fadeLowVelo";		
	
	/** The fade high velocity parameter.*/
	public static final String FADE_HIGH_VEL_PARAM       = "fadeHighVelo";	
	
	/** The sample start parameter.*/
	public static final String SAMPLE_START_PARAM        = "sampleStart";		

	/** The sample end parameter.*/
	public static final String SAMPLE_END_PARAM          = "sampleEnd";		
	
	/** The zone volume parameter.*/
	public static final String ZONE_VOL_PARAM            = "zoneVolume";
	
	/** The volume parameter.*/
	public static final String VOL_PARAM                 = "volume";
	
	/** The zone pan parameter.*/
	public static final String ZONE_PAN_PARAM            = "zonePan";
	
	/** The pan parameter.*/
	public static final String PAN_PARAM                 = "pan";
	
	/** The zone tune parameter.*/
	public static final String ZONE_TUNE_PARAM           = "zoneTune";
	
	/** The tune parameter.*/
	public static final String TUNE_PARAM                = "tune";

	/** The key tracking parameter.*/
	public static final String KEY_TRACKING_PARAM        = "keyTracking";	
	
	/** The internal modulators element.*/
	public static final String INT_MODULATORS_ELEMENT    = "IntModulators";
	
	/** The internal modulator element.*/
	public static final String INT_MODULATOR_ELEMENT     = "K2_IntMod";

	/** The external modulators element.*/
	public static final String EXT_MODULATORS_ELEMENT    = "ExtModulators";
	
	/** The external modulator element.*/
	public static final String EXT_MODULATOR_ELEMENT     = "K2_ExtMod";	
	
	/** The targets element.*/
	public static final String TARGETS_ELEMENT           = "Targets";
	
	/** The target element.*/
	public static final String TARGET_ELEMENT            = "Target";
	
	/** The target parameter.*/
	public static final String TARGET_PARAM              = "target";
	
	/** The target volume value.*/
	public static final String TARGET_VOL_VALUE          = "volume";
	
	/** The intensity parameter.*/
	public static final String INTENSITY_PARAM           = "intensity";
	
	/** The bypass parameter.*/
	public static final String BYPASS_PARAM              = "bypass";

	/** The envelope element.*/
	public static final String ENVELOPE_ELEMENT          = "Envelope";
	
	/** The envelope type attribute.*/
	public static final String ENV_TYPE_ATTRIBETE        = "type";
	
	/** The AHD envelope type value.*/
	public static final String AHD_ENV_TYPE_VALUE        = "ahd";

	/** The AHDSR envelope type value.*/
	public static final String AHDSR_ENV_TYPE_VALUE        = "ahdsr";
	
	/** The attack parameter.*/
	public static final String ATTACK_PARAM                = "attack";
	
	/** The hold parameter.*/
	public static final String HOLD_PARAM                  = "hold";
	
	/** The decay parameter.*/
	public static final String DECAY_PARAM                 = "decay";
	
	/** The hold parameter.*/
	public static final String SUSTAIN_PARAM               = "sustain";
	
	/** The release parameter.*/
	public static final String RELEASE_PARAM               = "release";

	/** The reverse parameter.*/
	public static final String REVERSE_PARAM               = "reverse";

	/** The loops element.*/	
	public static final String LOOPS_ELEMENT               = "Loops";	

	/** The loop element.*/	
	public static final String LOOP_ELEMENT                = "Loop";	
	
	/** The loop start parameter.*/
	public static final String LOOP_START_PARAM            = "loopStart";

	/** The loop length parameter.*/
	public static final String LOOP_LENGTH_PARAM           = "loopLength";
	
	/** The mode parameter.*/
	public static final String MODE_PARAM                  = "mode";

	/** The crossfade length parameter.*/
    public static final String XFADE_LENGTH_PARAM          = "xfadeLength";
	
    /** The until end value.*/                             
    public static final String UNTIL_END_VALUE             = "until_end";
    
	/** The until release value.*/
    public static final String UNTIL_RELEASE_VALUE         = "until_release";
    
    /** The oneshot value.*/
    public static final String ONESHOT_VALUE               = "oneshot";
    
    /** The alternating loop parameter.*/
    public static final String ALTERNATING_LOOP_PARAM      = "alternatingLoop";

    /** The source parameter.*/
    public static final String SOURCE_PARAM                = "source";   
    
    /** The pitch bend value.*/
    public static final String PITCH_BEND_VALUE            = "pitchBend";   
    
    /** The pitch value.*/
    public static final String PITCH_VALUE                 = "pitch";     

    /** The intensity value.*/
    public static final String INTENSITY_VALUE             = "intensity";     
    
	/**
     * Private constructor for utility class.
     */
    private K2Tag ()
    {
        // Intentionally empty
    }
}
