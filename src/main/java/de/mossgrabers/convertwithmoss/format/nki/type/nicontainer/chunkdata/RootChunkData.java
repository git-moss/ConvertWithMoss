// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;


/**
 * The root chunk of all containers.
 *
 * @author Jürgen Moßgraber
 */
public class RootChunkData extends AbstractChunkData
{
    private int     majorVersion;
    private int     minorVersion;
    private int     patchVersion;
    private int     repositoryMagic;
    private int     repositoryType;
    private byte [] rest;


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
        this.rest = in.readAllBytes ();
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        this.writeVersion (out);

        final int niSoundVersion = this.majorVersion << 0x14 | this.minorVersion << 0xC | this.patchVersion;
        StreamUtils.writeUnsigned32 (out, niSoundVersion, false);
        StreamUtils.writeUnsigned32 (out, this.repositoryMagic, false);
        StreamUtils.writeUnsigned32 (out, this.repositoryType, false);

        out.write (this.rest);
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


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode (this.rest);
        return prime * result + Objects.hash (Integer.valueOf (this.majorVersion), Integer.valueOf (this.minorVersion), Integer.valueOf (this.patchVersion), Integer.valueOf (this.repositoryMagic), Integer.valueOf (this.repositoryType));
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null || this.getClass () != obj.getClass ())
            return false;
        final RootChunkData other = (RootChunkData) obj;
        return this.majorVersion == other.majorVersion && this.minorVersion == other.minorVersion && this.patchVersion == other.patchVersion && this.repositoryMagic == other.repositoryMagic && this.repositoryType == other.repositoryType && Arrays.equals (this.rest, other.rest);
    }


    /** {@inheritDoc} */
    @Override
    public String dump (final int level)
    {
        final int padding = level * 4;
        final StringBuilder sb = new StringBuilder ();
        sb.append (StringUtils.padLeftSpaces ("* Repository Magic: ", padding)).append (this.repositoryMagic).append ('\n');
        sb.append (StringUtils.padLeftSpaces ("* Repository Type: ", padding)).append (this.repositoryType).append ('\n');
        sb.append (StringUtils.padLeftSpaces ("* Version: ", padding)).append (this.majorVersion).append ('.').append (this.minorVersion).append ('.').append (this.patchVersion).append ('\n');
        sb.append (StringUtils.padLeftSpaces ("* Unknown Data: ", padding)).append (StringUtils.formatArray (this.rest)).append ('\n');
        return sb.toString ();
    }
}
