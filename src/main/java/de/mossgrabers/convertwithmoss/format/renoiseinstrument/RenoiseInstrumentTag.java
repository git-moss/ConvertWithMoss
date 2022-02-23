package de.mossgrabers.convertwithmoss.format.renoiseinstrument;

import java.util.Set;

public class RenoiseInstrumentTag {
    public static final String RENOISEINSTRUMENT = "RenoiseInstrument";
    public static final String NAME = "Name";
    public static final String GLOBALPROPERTIES = "GlobalProperties";
    public static final String SAMPLEGENERATOR = "SampleGenerator";
    public static final String SAMPLES = "Samples";
    public static final String SAMPLE = "Sample";
    public static final String FILENAME = "FileName";
    public static final String VOLUME = "Volume";
    public static final String PANNING = "Panning";
    
    public static final String MAPPING = "Mapping";
    public static final String LAYER = "Layer";
    public static final String BASENOTE = "BaseNote";
    public static final String NOTESTART = "NoteStart";
    public static final String NOTEEND = "NoteEnd";
    public static final String MAPKEYTOPITCH = "MapKeyToPitch";
    public static final String VELOCITYSTART = "VelocityStart";
    public static final String VELOCITYEND = "VelocityEnd";
    public static final String LOOPMODE = "LoopMode";
    public static final String LOOPSTART = "LoopStart";
    public static final String LOOPEND = "LoopEnd";

    public static final Set<String>  TOP_LEVEL_TAGS = Set.of (GLOBALPROPERTIES, SAMPLEGENERATOR);
    public static final Set<String>  GLOBALPROPERTIES_TAGS = Set.of (VOLUME);
    public static final Set<String>  SAMPLE_TAGS = Set.of (NAME, VOLUME, FILENAME);
    public static final Set<String>  MAPPING_TAGS = Set.of (LAYER, BASENOTE, NOTESTART, NOTEEND, MAPKEYTOPITCH, VELOCITYSTART, VELOCITYEND);

    private RenoiseInstrumentTag ()
    {

    }
}
