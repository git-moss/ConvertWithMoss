package de.mossgrabers.convertwithmoss.format.roland.s5xx.io;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;


public class ResourceLoader
{
    public static InputStream loadResource (Class<?> caller, String resourceName)
    {
        if (caller != null)
        {
            InputStream in = null;
            if (caller.getClassLoader () != null)
            {
                in = caller.getClassLoader ().getResourceAsStream (resourceName);
                if (in != null)
                {
                    return in;
                }
            }
            in = caller.getResourceAsStream (resourceName);
            if (in != null)
            {
                return in;
            }
            if (caller.getSuperclass () != null)
            {
                return loadResource (caller.getSuperclass (), resourceName);
            }
        }
        return null;
    }


    public static byte [] load (Class<?> clazz, String name) throws IOException
    {
        ByteArrayOutputStream buf = new ByteArrayOutputStream ();
        try (InputStream in = clazz.getResourceAsStream (name))
        {
            if (in == null)
            {
                throw new FileNotFoundException ("Resource \"" + name + "\" not found");
            }
            byte [] b = new byte [512];
            int n;
            while ((n = in.read (b)) != -1)
            {
                buf.write (b, 0, n);
            }
            return buf.toByteArray ();
        }
    }


    public static String getClassBasePath (Class<?> javaClass)
    {
        return javaClass.getPackage ().getName ().replace (".", System.getProperty ("file.separator"));
    }


    public static String getResourceBasePath (String resourceName)
    {
        String fileSeparator = System.getProperty ("file.separator");
        String result = null;
        if (resourceName != null)
        {
            if (resourceName.indexOf (fileSeparator) != -1)
            {
                result = resourceName.substring (0, resourceName.lastIndexOf (fileSeparator));
            }
            else
            {
                result = "." + fileSeparator;
            }
        }
        return result;
    }


    public static String getClassName (Class<?> javaClass)
    {
        return javaClass.getCanonicalName ();
    }
}
