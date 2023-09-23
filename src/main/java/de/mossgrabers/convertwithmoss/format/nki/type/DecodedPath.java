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
    private String relativePath;
    private String library;


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
        this.relativePath = relativePath;
    }


    /**
     * Get the relative path to the sample. The path might also be point to a sample in a NKS
     * library.
     *
     * @return The relative path
     */
    public String getRelativePath ()
    {
        return this.relativePath;
    }


    /**
     * Set the relative path to the sample.
     *
     * @param relativePath The relative path to set
     */
    public void setRelativePath (final String relativePath)
    {
        this.relativePath = relativePath;
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
}
