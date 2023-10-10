// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type;

/**
 * A decoded path to a sample.
 *
 * @author Jürgen Moßgraber
 */
public class DecodedPath
{
    private boolean isAbsolute = false;
    private String  path;
    private String  library;


    /**
     * Default constructor.
     */
    public DecodedPath ()
    {
        // Intentionally empty
    }


    /**
     * Constructor.
     *
     * @param relativePath The relative path of the sample
     */
    public DecodedPath (final String relativePath)
    {
        this.path = relativePath;
    }


    /**
     * Get the path to the sample. The path might also be point to a sample in a NKS library.
     *
     * @return The path
     */
    public String getPath ()
    {
        return this.path;
    }


    /**
     * Set the path to the sample.
     *
     * @param path The path to set
     */
    public void setPath (final String path)
    {
        this.path = path;
    }


    /**
     * Get the library which contains the sample, if any.
     *
     * @return The relative path to the library file or null if it is not inside a library
     */
    public String getLibrary ()
    {
        return this.library;
    }


    /**
     * Set the relative library path.
     *
     * @param library the library to set
     */
    public void setLibrary (final String library)
    {
        this.library = library;
    }


    /**
     * Check if the path is an absolute path.
     *
     * @return True if it is an absolute path
     */
    public boolean isAbsolute ()
    {
        return this.isAbsolute;
    }


    /**
     * Set if it is an absolute path.
     *
     * @param isAbsolute True to set to absolute otherwise relative
     */
    public void setAbsolute (final boolean isAbsolute)
    {
        this.isAbsolute = isAbsolute;
    }
}
