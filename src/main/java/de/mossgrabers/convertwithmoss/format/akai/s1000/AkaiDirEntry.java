// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

/**
 * An entry in the Akai directory.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiDirEntry
{
    private String name;
    private int    type;
    private int    size;
    private int    start;
    private int    index;


    /**
     * Get the name of the entry.
     * 
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Set the name of the entry.
     * 
     * @param name The name
     */
    public void setName (final String name)
    {
        this.name = name;
    }


    /**
     * Get the type of the entry.
     * 
     * @return The type, see AKAI_VOLUME_* in AkaiDiskElement.
     */
    public int getType ()
    {
        return this.type;
    }


    /**
     * Set the type of the entry.
     * 
     * @param type The type, see AKAI_VOLUME_* in AkaiDiskElement.
     */
    public void setType (final int type)
    {
        this.type = type;
    }


    /**
     * Get the size of the content.
     * 
     * @return The size
     */
    public int getSize ()
    {
        return this.size;
    }


    /**
     * Set the size of the content.
     * 
     * @param size The size
     */
    public void setSize (final int size)
    {
        this.size = size;
    }


    /**
     * Get the value of the start block in the structure.
     * 
     * @return The start value
     */
    public int getStart ()
    {
        return this.start;
    }


    /**
     * Set the value of the start block in the structure.
     * 
     * @param start The start value
     */
    public void setStart (final int start)
    {
        this.start = start;
    }


    /**
     * Get the index of the entry.
     * 
     * @return The index
     */
    public int getIndex ()
    {
        return this.index;
    }


    /**
     * Set the index.
     * 
     * @param index The index
     */
    public void setIndex (final int index)
    {
        this.index = index;
    }
}