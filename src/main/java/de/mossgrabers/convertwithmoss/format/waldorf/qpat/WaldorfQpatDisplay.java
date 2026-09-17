// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

import de.mossgrabers.tools.StringUtils;


/**
 * The space which a text takes on the display of the Waldorf Quantum/Iridium. The device uses the
 * proportional font DIN, therefore how many characters fit depends on the characters: the patch
 * information of the 'Load Patch' page shows 24 characters of the bank 'Digital Collection Vintage'
 * ('Digital Collection Vinta') but only 20 of the name 'DW-8000 Breathy Breakup Pad' ('DW-8000
 * Breathy Brea'). The device leaves out every character which does not fit completely.
 *
 * @author Jürgen Moßgraber
 */
public class WaldorfQpatDisplay
{
    /**
     * The widths of the printable ASCII characters 32 to 126 in 1/1000 of the font size, taken from
     * the bold DIN font which is embedded into the manual of the Iridium MK2. The characters which
     * the manual does not use are estimated from DIN Alternate.
     */
    private static final int [] CHARACTER_WIDTHS =
    {
        232,
        280,
        362,
        521,
        521,
        564,
        721,
        261,
        336,
        336,
        382,
        652,
        279,
        426,
        285,
        403,                                             // ! " # $ % & ' ( ) * + , - . /
        543,
        543,
        543,
        543,
        543,
        543,
        543,
        543,
        543,
        543,
        309,
        261,
        652,
        652,
        652,
        523,                                             // 0 1 2 3 4 5 6 7 8 9 : ; < = > ?
        869,
        638,
        658,
        620,
        659,
        605,
        590,
        648,
        685,
        305,
        511,
        665,
        575,
        811,
        711,
        644,                                             // @ A B C D E F G H I J K L M N O
        628,
        643,
        654,
        591,
        571,
        668,
        581,
        887,
        603,
        572,
        548,
        302,
        362,
        302,
        652,
        543,                                             // P Q R S T U V W X Y Z [ \ ] ^ _
        261,
        534,
        560,
        480,
        561,
        548,
        342,
        555,
        569,
        275,
        280,
        559,
        325,
        876,
        573,
        543,                                             // ` a b c d e f g h i j k l m n o
        560,
        561,
        455,
        501,
        352,
        573,
        493,
        759,
        527,
        494,
        478,
        302,
        241,
        302,
        652                                              // p q r s t u v w x y z { | } ~
    };

    /**
     * The width of the patch information of the 'Load Patch' page, which shows the name, the bank
     * and the author of the selected patch: the width of the widest text which was seen completely
     * ('DW-8000 Breathy Brea'; with the next character it becomes 10972, which did not fit).
     */
    public static final int     PATCH_INFO_WIDTH = 10413;


    /**
     * Private due to utility class.
     */
    private WaldorfQpatDisplay ()
    {
        // Intentionally empty
    }


    /**
     * Get the width of a text on the display.
     *
     * @param text The text, the characters are converted to ASCII like when they are written
     * @return The width in 1/1000 of the font size
     */
    public static int getWidth (final String text)
    {
        int width = 0;
        for (final char c: StringUtils.fixASCII (text).toCharArray ())
            width += getWidth (c);
        return width;
    }


    /**
     * Test if a text is shown completely in the patch information of the 'Load Patch' page.
     *
     * @param text The text
     * @return True if it fits
     */
    public static boolean fitsIntoPatchInfo (final String text)
    {
        return getWidth (text) <= PATCH_INFO_WIDTH;
    }


    /**
     * Get the part of a text which is shown in the patch information of the 'Load Patch' page.
     *
     * @param text The text
     * @return The characters from the start of the text which fit completely
     */
    public static String getVisiblePart (final String text)
    {
        final String asciiText = StringUtils.fixASCII (text);
        int width = 0;
        for (int i = 0; i < asciiText.length (); i++)
        {
            width += getWidth (asciiText.charAt (i));
            if (width > PATCH_INFO_WIDTH)
                return asciiText.substring (0, i);
        }
        return asciiText;
    }


    private static int getWidth (final char c)
    {
        // Control characters take no space, all others are replaced before they are written
        if (c < 32)
            return 0;
        return CHARACTER_WIDTHS[c < 127 ? c - 32 : '?' - 32];
    }
}
