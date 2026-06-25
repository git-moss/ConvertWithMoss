// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synthstrom;

/**
 * The XML tags and attributes used in a Synthstrom Deluge instrument file (case sensitive).
 *
 * @author Jürgen Moßgraber
 */
public class DelugeTag
{
    /** The root tag of a synth (sound) preset. */
    public static final String SOUND                         = "sound";
    /** The root tag of a kit preset. */
    public static final String KIT                           = "kit";

    /** The firmware version which created the file. */
    public static final String FIRMWARE_VERSION              = "firmwareVersion";
    /** The earliest firmware version which can load the file. */
    public static final String EARLIEST_COMPATIBLE_FIRMWARE  = "earliestCompatibleFirmware";

    /** The firmware version to write (modern XML structure, avoids legacy read paths). */
    public static final String FIRMWARE_VERSION_VALUE        = "4.1.0-alpha";
    /** The earliest compatible firmware version to write. */
    public static final String EARLIEST_COMPATIBLE_VALUE     = "4.1.0-alpha";

    // Sound attributes
    /** The polyphony mode. */
    public static final String POLYPHONIC                    = "polyphonic";
    /** The voice priority. */
    public static final String VOICE_PRIORITY                = "voicePriority";
    /** The synthesis mode. */
    public static final String MODE                          = "mode";
    /** The sound transpose. */
    public static final String TRANSPOSE                     = "transpose";
    /** The maximum number of voices. */
    public static final String MAX_VOICES                    = "maxVoices";
    /** The path attribute (community firmware, used for kit drums). */
    public static final String PATH                          = "path";
    /** The name attribute. */
    public static final String NAME                          = "name";

    // Oscillator tags / attributes
    /** The first oscillator. */
    public static final String OSC1                          = "osc1";
    /** The second oscillator. */
    public static final String OSC2                          = "osc2";
    /** The oscillator type. */
    public static final String TYPE                          = "type";
    /** The oscillator type value for a sample. */
    public static final String TYPE_SAMPLE                   = "sample";
    /** The loop/repeat mode of a sample oscillator. */
    public static final String LOOP_MODE                     = "loopMode";
    /** Whether the sample is reversed. */
    public static final String REVERSED                      = "reversed";
    /** Whether time-stretch is enabled. */
    public static final String TIME_STRETCH_ENABLE           = "timeStretchEnable";
    /** The time-stretch amount. */
    public static final String TIME_STRETCH_AMOUNT           = "timeStretchAmount";
    /** The file name of a sample. */
    public static final String FILE_NAME                     = "fileName";
    /** The per-zone cents fine tune. */
    public static final String CENTS                         = "cents";

    // Multi-sample tags / attributes
    /** The container of all sample ranges. */
    public static final String SAMPLE_RANGES                 = "sampleRanges";
    /** One sample range (zone). */
    public static final String SAMPLE_RANGE                  = "sampleRange";
    /** The highest note of a sample range. */
    public static final String RANGE_TOP_NOTE                = "rangeTopNote";

    /** The oscillator transpose (single sample). */
    public static final String TRANSPOSE_OSC                 = "transpose";
    /** The retrigger phase of an oscillator. */
    public static final String RETRIG_PHASE                  = "retrigPhase";

    // Zone tag / attributes
    /** The zone tag, which contains the sample positions. */
    public static final String ZONE                          = "zone";
    /** The start of the playback in samples. */
    public static final String START_SAMPLE_POS              = "startSamplePos";
    /** The end of the playback in samples. */
    public static final String END_SAMPLE_POS                = "endSamplePos";
    /** The start of the loop in samples. */
    public static final String START_LOOP_POS                = "startLoopPos";
    /** The end of the loop in samples. */
    public static final String END_LOOP_POS                  = "endLoopPos";
    /** The start of the playback in milli-seconds (old format). */
    public static final String START_MILLISECONDS            = "startMilliseconds";
    /** The end of the playback in milli-seconds (old format). */
    public static final String END_MILLISECONDS              = "endMilliseconds";
    /** The start of the playback in seconds (old format). */
    public static final String START_SECONDS                 = "startSeconds";
    /** The end of the playback in seconds (old format). */
    public static final String END_SECONDS                   = "endSeconds";

    // Default parameters tag / attributes
    /** The container of the default parameter values. */
    public static final String DEFAULT_PARAMS                = "defaultParams";
    /** The volume of oscillator A. */
    public static final String OSC_A_VOLUME                  = "oscAVolume";
    /** The volume of oscillator B. */
    public static final String OSC_B_VOLUME                  = "oscBVolume";
    /** The post-effects volume. */
    public static final String VOLUME                        = "volume";
    /** The low-pass filter frequency. */
    public static final String LPF_FREQUENCY                 = "lpfFrequency";
    /** The low-pass filter resonance. */
    public static final String LPF_RESONANCE                 = "lpfResonance";
    /** The high-pass filter frequency. */
    public static final String HPF_FREQUENCY                 = "hpfFrequency";
    /** The high-pass filter resonance. */
    public static final String HPF_RESONANCE                 = "hpfResonance";
    /** The low-pass filter mode (slope/type). */
    public static final String LPF_MODE                      = "lpfMode";
    /** The high-pass filter mode (slope/type). */
    public static final String HPF_MODE                      = "hpfMode";
    /** The nested low-pass filter element (old format). */
    public static final String LPF                           = "lpf";
    /** The nested high-pass filter element (old format). */
    public static final String HPF                           = "hpf";
    /** The frequency child element of a nested filter (old format). */
    public static final String FREQUENCY                     = "frequency";
    /** The resonance child element of a nested filter (old format). */
    public static final String RESONANCE                     = "resonance";

    // Unison child elements
    /** The number of unison voices. */
    public static final String UNISON_NUM                    = "num";
    /** The unison detune. */
    public static final String UNISON_DETUNE                 = "detune";

    // Envelope tags / attributes
    /** The amplitude envelope. */
    public static final String ENVELOPE1                     = "envelope1";
    /** The modulation envelope. */
    public static final String ENVELOPE2                     = "envelope2";
    /** The attack time. */
    public static final String ATTACK                        = "attack";
    /** The decay time. */
    public static final String DECAY                         = "decay";
    /** The sustain level. */
    public static final String SUSTAIN                       = "sustain";
    /** The release time. */
    public static final String RELEASE                       = "release";

    // Patch cable tags / attributes
    /** The container of all patch cables. */
    public static final String PATCH_CABLES                  = "patchCables";
    /** One patch cable. */
    public static final String PATCH_CABLE                   = "patchCable";
    /** The modulation source of a patch cable. */
    public static final String SOURCE                        = "source";
    /** The modulation destination of a patch cable. */
    public static final String DESTINATION                   = "destination";
    /** The amount of a patch cable. */
    public static final String AMOUNT                        = "amount";
    /** The patch source velocity. */
    public static final String SOURCE_VELOCITY               = "velocity";

    // Kit tags / attributes
    /** The container of all drum sounds of a kit. */
    public static final String SOUND_SOURCES                 = "soundSources";

    // Oscillator unison
    /** The unison tag. */
    public static final String UNISON                        = "unison";

    /** The Deluge loop mode: play once and stop (no release tail). */
    public static final int    LOOP_MODE_CUT                 = 0;
    /** The Deluge loop mode: play once with release tail. */
    public static final int    LOOP_MODE_ONCE                = 1;
    /** The Deluge loop mode: loop. */
    public static final int    LOOP_MODE_LOOP                = 2;
    /** The Deluge loop mode: time-stretch. */
    public static final int    LOOP_MODE_STRETCH             = 3;

    /** The synthesis mode value for a subtractive (sample) synth. */
    public static final String MODE_SUBTRACTIVE              = "subtractive";
    /** The polyphony mode value to write. */
    public static final String POLYPHONIC_POLY               = "poly";
    /** The oscillator type value for a silent second oscillator. */
    public static final String TYPE_SQUARE                   = "square";
    /** The default low-pass filter mode value to write. */
    public static final String LPF_MODE_24DB                 = "24dB";
    /** The patch cable destination for the volume. */
    public static final String DESTINATION_VOLUME            = "volume";


    /**
     * Constructor.
     */
    protected DelugeTag ()
    {
        // Intentionally empty
    }
}
