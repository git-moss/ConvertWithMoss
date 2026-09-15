// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sequential.prophetx;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.mossgrabers.convertwithmoss.format.TagDetector;


/**
 * Constants of the Sequential Prophet X / XL sample instrument format. An instrument is a folder
 * which contains its samples as WAV files and a group file (ending <i>.grp</i>) with the key,
 * velocity and loop mapping. The device imports such folders as ZIP archives from a USB drive:
 * <i>px/&lt;bank&gt;/&lt;category&gt;/&lt;name&gt;.zip</i>. The format was reverse-engineered from
 * the OS 2.2.2 firmware, see <i>documentation/design/PROPHET_X_GRP_FORMAT.md</i>.
 *
 * @author Jürgen Moßgraber
 */
public class ProphetXTag
{
    /** The ending of the group file which maps the samples of an instrument. */
    public static final String                GROUP_FILE_ENDING     = ".grp";
    /** The ending of an import archive. */
    public static final String                ARCHIVE_ENDING        = ".zip";
    /** The folder at the root of the USB drive below which the device looks for import archives. */
    public static final String                USB_FOLDER            = "px";
    /** The number of user banks (u00 to u31) of the device. */
    public static final int                   NUM_USER_BANKS        = 32;
    /** The optional file next to the group file with one gain factor per velocity. */
    public static final String                VOLUME_FILE           = "Volume.txt";
    /** The name of the empty sample which keeps the keys silent that no zone covers. */
    public static final String                SILENCE_NAME          = "silence";

    /** The sample rate of the audio engine. The device ignores the sample rate of a WAV file. */
    public static final int                   SAMPLE_RATE           = 48000;
    /** The only bit resolution which the device plays. */
    public static final int                   BIT_RESOLUTION        = 16;
    /** The maximum number of samples of an instrument, the limit of the mapping utility of the device. */
    public static final int                   MAX_SAMPLES           = 128;
    /** The maximum length of an instrument name; the firmware copies it into a buffer of 64 bytes. */
    public static final int                   MAX_NAME_LENGTH       = 63;
    /** The value of a column which is not set. */
    public static final int                   NOT_SET               = -1;
    /** The pitch which the firmware assumes for a sample without a pitch. */
    public static final double                DEFAULT_PITCH         = 440.0;

    /** The column with the path of the sample file, relative to the instrument folder. */
    public static final String                COLUMN_FILE_PATH      = "File Path";
    /** The column with the first key of the zone. */
    public static final String                COLUMN_LOW_NOTE       = "Low Midi Note";
    /** The column with the last key of the zone. */
    public static final String                COLUMN_HIGH_NOTE      = "High Midi Note";
    /** The column with the lowest velocity of the zone. */
    public static final String                COLUMN_LOW_VELOCITY   = "Low Velocity";
    /** The column with the highest velocity of the zone. */
    public static final String                COLUMN_HIGH_VELOCITY  = "High Velocity";
    /** The column with the first frame of the loop. */
    public static final String                COLUMN_LOOP_START     = "Loop Start";
    /** The column with the last frame of the loop, inclusive. */
    public static final String                COLUMN_LOOP_END       = "Loop End";
    /** The column with the 1-based number of the sample in its round robin set. */
    public static final String                COLUMN_ROUND_ROBIN    = "Round Robin Number";
    /** The column with the frequency at which the file sounds. */
    public static final String                COLUMN_PITCH          = "Pitch in Hertz";
    /** The column which switches the key tracking off. */
    public static final String                COLUMN_MONO_PITCH     = "Mono Pitch";
    /** The column which tells if the file is stereo or mono. */
    public static final String                COLUMN_STEREO_MONO    = "Stereo/Mono File";
    /** The column with the category of the instrument. */
    public static final String                COLUMN_CATEGORY       = "Category";
    /** The column with the name of the instrument. */
    public static final String                COLUMN_INSTRUMENT     = "Instrument Name";
    /** The column with the channel to use when a stereo file is collapsed to mono. */
    public static final String                COLUMN_MONO_COLLAPSE  = "Mono Collapse";
    /** The column with the unique identifier of the instrument. */
    public static final String                COLUMN_UUID           = "UUID";

    /** All columns in the order in which the firmware defines them. */
    public static final String []             COLUMNS               =
    {
        COLUMN_FILE_PATH,
        COLUMN_LOW_NOTE,
        COLUMN_HIGH_NOTE,
        COLUMN_LOW_VELOCITY,
        COLUMN_HIGH_VELOCITY,
        COLUMN_LOOP_START,
        COLUMN_LOOP_END,
        COLUMN_ROUND_ROBIN,
        COLUMN_PITCH,
        COLUMN_MONO_PITCH,
        COLUMN_STEREO_MONO,
        COLUMN_CATEGORY,
        COLUMN_INSTRUMENT,
        COLUMN_MONO_COLLAPSE,
        COLUMN_UUID
    };

    /** The value of a flag column which is set. */
    public static final String                YES                   = "Y";
    /** The value of a flag column which is not set. */
    public static final String                NO                    = "N";
    /** The value of the stereo/mono column for a stereo file. */
    public static final String                STEREO                = "S";
    /** The value of the stereo/mono column for a mono file. */
    public static final String                MONO                  = "M";
    /** The value of the mono collapse column which mixes both channels, the default of the firmware. */
    public static final String                COLLAPSE_BOTH         = "B";

    /** The category folders of the device in the order of their number. */
    public static final String []             CATEGORY_FOLDERS      =
    {
        "01 Ambience",
        "02 Bass",
        "03 Brass",
        "04 Choir",
        "05 Cinematic",
        "06 Drums",
        "07 Effects",
        "08 Ethnic",
        "09 Guitar",
        "10 Keyboard",
        "11 Percussion",
        "12 Tonal Perc",
        "13 Piano",
        "14 Strings",
        "15 Synth",
        "16 Vox",
        "17 Winds"
    };

    /** The category names without their number, as the firmware compares them. */
    public static final String []             CATEGORY_NAMES        =
    {
        "Ambience",
        "Bass",
        "Brass",
        "Choir",
        "Cinematic",
        "Drums",
        "Effects",
        "Ethnic",
        "Guitar",
        "Keyboard",
        "Percussion",
        "Tonal Perc",
        "Piano",
        "Strings",
        "Synth",
        "Vox",
        "Winds"
    };

    /** The category of a source whose category is unknown or has no counterpart on the device. */
    public static final int                   DEFAULT_CATEGORY      = 14;

    /** The category of the model which is the closest to each category of the device. */
    private static final String []            MODEL_CATEGORIES      =
    {
        TagDetector.CATEGORY_DRONE,
        TagDetector.CATEGORY_BASS,
        TagDetector.CATEGORY_BRASS,
        TagDetector.CATEGORY_VOCAL,
        TagDetector.CATEGORY_HITS,
        TagDetector.CATEGORY_DRUM,
        TagDetector.CATEGORY_FX,
        TagDetector.CATEGORY_WORLD,
        TagDetector.CATEGORY_GUITAR,
        TagDetector.CATEGORY_KEYBOARD,
        TagDetector.CATEGORY_PERCUSSION,
        TagDetector.CATEGORY_CHROMATIC_PERCUSSION,
        TagDetector.CATEGORY_PIANO,
        TagDetector.CATEGORY_STRINGS,
        TagDetector.CATEGORY_SYNTH,
        TagDetector.CATEGORY_VOCAL,
        TagDetector.CATEGORY_WINDS
    };

    private static final Map<String, Integer> CATEGORY_OF_MODEL     = new HashMap<> ();
    static
    {
        // The categories of the model which have a counterpart of the same meaning; a later entry
        // wins for the two which map to the same one (Choir and Vox both stand for Vocal)
        for (int i = 0; i < MODEL_CATEGORIES.length; i++)
            CATEGORY_OF_MODEL.put (MODEL_CATEGORIES[i], Integer.valueOf (i));

        // The categories of the model which the device does not know, sorted into the closest one
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_ACOUSTIC_DRUM, Integer.valueOf (5));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_KICK, Integer.valueOf (5));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_SNARE, Integer.valueOf (5));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_HI_HAT, Integer.valueOf (5));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_CLAP, Integer.valueOf (5));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_BELL, Integer.valueOf (11));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_DESTRUCTION, Integer.valueOf (6));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_LOOPS, Integer.valueOf (6));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_ENSEMBLE, Integer.valueOf (13));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_ORCHESTRAL, Integer.valueOf (13));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_ORGAN, Integer.valueOf (9));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_PIPE, Integer.valueOf (16));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_CHIP, Integer.valueOf (14));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_LEAD, Integer.valueOf (14));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_MONOSYNTH, Integer.valueOf (14));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_PAD, Integer.valueOf (14));
        CATEGORY_OF_MODEL.put (TagDetector.CATEGORY_PLUCK, Integer.valueOf (14));
    }


    /**
     * Constructor.
     */
    private ProphetXTag ()
    {
        // Intentionally empty
    }


    /**
     * Get the folder name of a user bank.
     *
     * @param index The index of the bank, 0 to {@link #NUM_USER_BANKS} - 1
     * @return The folder name, e.g. 'u00'
     */
    public static String getUserBank (final int index)
    {
        return String.format (Locale.US, "u%02d", Integer.valueOf (index));
    }


    /**
     * Get the index of a user bank from its folder name.
     *
     * @param text The folder name, e.g. 'u07', or only its number
     * @return The index of the bank or -1 if the text is not a user bank
     */
    public static int getUserBankIndex (final String text)
    {
        if (text == null)
            return -1;
        final String bank = text.strip ().toLowerCase (Locale.US);
        final String number = bank.startsWith ("u") ? bank.substring (1) : bank;
        try
        {
            final int index = Integer.parseInt (number);
            return index >= 0 && index < NUM_USER_BANKS ? index : -1;
        }
        catch (final NumberFormatException _)
        {
            return -1;
        }
    }


    /**
     * Get the index of a category from its folder name or its name.
     *
     * @param text The folder name (e.g. '15 Synth') or the name (e.g. 'Synth')
     * @return The index of the category or -1 if the text is not a category of the device
     */
    public static int getCategoryIndex (final String text)
    {
        if (text == null)
            return -1;
        final String name = text.strip ();
        for (int i = 0; i < CATEGORY_FOLDERS.length; i++)
            if (CATEGORY_FOLDERS[i].equalsIgnoreCase (name) || CATEGORY_NAMES[i].equalsIgnoreCase (name))
                return i;
        return -1;
    }


    /**
     * Get the category of the device which is the closest to a category of the model.
     *
     * @param category The category of the model, see the constants of {@link TagDetector}
     * @return The index of the category of the device
     */
    public static int fromModelCategory (final String category)
    {
        final Integer index = category == null ? null : CATEGORY_OF_MODEL.get (category);
        return index == null ? DEFAULT_CATEGORY : index.intValue ();
    }


    /**
     * Get the category of the model which is the closest to a category of the device.
     *
     * @param index The index of the category of the device
     * @return The category of the model, see the constants of {@link TagDetector}
     */
    public static String toModelCategory (final int index)
    {
        return MODEL_CATEGORIES[Math.clamp (index, 0, MODEL_CATEGORIES.length - 1)];
    }
}
