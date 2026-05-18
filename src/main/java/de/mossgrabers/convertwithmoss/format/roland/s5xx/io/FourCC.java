package de.mossgrabers.convertwithmoss.format.roland.s5xx.io;

public class FourCC
{
    public static String fourCC (int fourCC)
    {
        char [] chars = new char [4];
        chars[0] = (char) ((fourCC >> 24) & 0xFF);
        chars[1] = (char) ((fourCC >> 16) & 0xFF);
        chars[2] = (char) ((fourCC >> 8) & 0xFF);
        chars[3] = (char) (fourCC & 0xFF);
        return new String (chars);
    }


    public static int fourCC (String fourCC)
    {
        int result = 0;
        for (char c: fourCC.toCharArray ())
        {
            result = (result << 8) | (c & 0xFF);
        }
        return result;
    }


    public static String ascii (int fourCC)
    {
        String text = FourCC.fourCC (fourCC);
        int last = text.indexOf (0);
        if (last != -1)
        {
            text = text.substring (0, last);
        }
        return text;
    }


    public static int ascii (String text)
    {
        char [] c = text.toCharArray ();
        char [] chars = new char [4];
        for (int i = 0; i < c.length; i++)
        {
            chars[i] = c[i];
        }
        return fourCC (new String (chars));
    }
}
