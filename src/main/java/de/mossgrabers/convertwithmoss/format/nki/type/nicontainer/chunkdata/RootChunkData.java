// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import de.mossgrabers.convertwithmoss.file.StreamUtils;

import java.io.IOException;
import java.io.InputStream;


/**
 * The root chunk of all containers.
 *
 * @author Jürgen Moßgraber
 */
public class RootChunkData extends AbstractChunkData
{
    private int majorVersion;
    private int minorVersion;
    private int patchVersion;
    private int repositoryMagic;
    private int repositoryType;


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.readVersion (in);

        final int niSoundVersion = (int) StreamUtils.readUnsigned32 (in, false);
        this.repositoryMagic = (int) StreamUtils.readUnsigned32 (in, false);
        this.repositoryType = (int) StreamUtils.readUnsigned32 (in, false);

        this.majorVersion = niSoundVersion >> 0x14 & 0xFF;
        this.minorVersion = niSoundVersion >> 0xC & 0xFF;
        this.patchVersion = niSoundVersion & 0xFFF;

        // 42 more bytes referenced repository...
    }


    /**
     * Get the major version.
     *
     * @return The major version
     */
    public int getMajorVersion ()
    {
        return this.majorVersion;
    }


    /**
     * Get the minor version.
     *
     * @return The minor version
     */
    public int getMinorVersion ()
    {
        return this.minorVersion;
    }


    /**
     * Get the patch version.
     *
     * @return The patch version
     */
    public int getPatchVersion ()
    {
        return this.patchVersion;
    }


    /**
     * Get the repository magic number.
     *
     * @return The repository magic number
     */
    public int getRepositoryMagic ()
    {
        return this.repositoryMagic;
    }


    /**
     * Get the repository type.
     *
     * @return The repository type
     */
    public int getRepositoryType ()
    {
        return this.repositoryType;
    }
}
