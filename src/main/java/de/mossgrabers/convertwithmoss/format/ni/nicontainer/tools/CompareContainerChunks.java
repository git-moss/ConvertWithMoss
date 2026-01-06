// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.nicontainer.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import de.mossgrabers.convertwithmoss.format.ni.nicontainer.NIContainerChildItem;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.NIContainerChunkType;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.NIContainerDataChunk;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.NIContainerItem;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.IChunkData;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.SubTreeItemChunkData;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.TerminatorChunkData;
import de.mossgrabers.tools.StringUtils;


/**
 * Compares all chunks of a container.
 *
 * @author Jürgen Moßgraber
 */
public class CompareContainerChunks
{
    /**
     * Compares all chunks of a container.
     *
     * @param args The absolute path to the file as the first argument
     */
    public static void main (final String [] args)
    {
        if (args.length != 2)
        {
            System.out.println ("Two absolute paths of the files to compare need to be given as the first and second parameter.");
            return;
        }

        final NIContainerItem niContainerItem1 = new NIContainerItem ();
        final NIContainerItem niContainerItem2 = new NIContainerItem ();

        try (final InputStream inputStream1 = new FileInputStream (new File (args[0])); final InputStream inputStream2 = new FileInputStream (new File (args[1])))
        {
            niContainerItem1.read (inputStream1);
            niContainerItem2.read (inputStream2);
        }
        catch (final IOException ex)
        {
            ex.printStackTrace ();
            return;
        }

        compare (niContainerItem1, niContainerItem2, 0);
    }


    private static void compare (final NIContainerItem niContainerItem1, final NIContainerItem niContainerItem2, final int level)
    {
        final int depth = level * 4;

        NIContainerDataChunk dataChunk1 = niContainerItem1.getDataChunk ();
        NIContainerDataChunk dataChunk2 = niContainerItem2.getDataChunk ();
        while (dataChunk1 != null && dataChunk2 != null)
        {
            final IChunkData data1 = dataChunk1.getData ();
            final IChunkData data2 = dataChunk2.getData ();
            if (data1.getClass () != data2.getClass ())
            {
                System.out.println (StringUtils.padLeftSpaces ("Different Item Content: ", depth) + data1.getClass ().getName () + data2.getClass ().getName ());
                return;
            }
            System.out.println (StringUtils.padLeftSpaces ("Item Content: ", depth) + data1.getClass ().getSimpleName ());
            compareData (data1, data2, level + 1);

            dataChunk1 = dataChunk1.getNextChunk ();
            dataChunk2 = dataChunk2.getNextChunk ();
        }

        if (dataChunk1 != dataChunk2)
        {
            System.out.println (StringUtils.padLeftSpaces ("Different number of data chunks!", depth));
            return;
        }

        if (Arrays.compare (niContainerItem1.getUUID (), niContainerItem2.getUUID ()) != 0)
        {
            System.out.println (StringUtils.padLeftSpaces ("Different UUID:", depth));
            System.out.println (StringUtils.padLeftSpaces ("  " + StringUtils.formatHexStr (niContainerItem1.getUUID ()), depth));
            System.out.println (StringUtils.padLeftSpaces ("  " + StringUtils.formatHexStr (niContainerItem2.getUUID ()), depth));
        }
        if (niContainerItem1.getVersion () != niContainerItem2.getVersion ())
            System.out.println (StringUtils.padLeftSpaces (String.format ("Different versions: %d %d ", Integer.toString (niContainerItem1.getVersion ()), Integer.toString (niContainerItem2.getVersion ())), depth));

        final List<NIContainerChildItem> children1 = niContainerItem1.getChildren ();
        final List<NIContainerChildItem> children2 = niContainerItem2.getChildren ();
        if (children1.size () != children2.size ())
        {
            System.out.println (StringUtils.padLeftSpaces ("Different number of children!", depth));
            return;
        }

        if (children1.isEmpty ())
            return;

        System.out.println (StringUtils.padLeftSpaces ("Children:", depth));
        for (int i = 0; i < children1.size (); i++)
        {
            final NIContainerChildItem childItem1 = children1.get (i);
            final NIContainerChildItem childItem2 = children2.get (i);

            final String domainID1 = childItem1.getDomainID ();
            final String domainID2 = childItem2.getDomainID ();
            if (!domainID1.equals (domainID2))
                System.out.println (StringUtils.padLeftSpaces (String.format ("Different domains: %s %s ", domainID1, domainID2), depth));
            else
                System.out.println (StringUtils.padLeftSpaces ("* Domain: " + domainID1, depth));

            final int chunkTypeID1 = childItem1.getChunkTypeID ();
            final int chunkTypeID2 = childItem2.getChunkTypeID ();
            if (chunkTypeID1 != chunkTypeID2)
                System.out.println (StringUtils.padLeftSpaces (String.format ("Different types: %s %s ", NIContainerChunkType.get (chunkTypeID1), NIContainerChunkType.get (chunkTypeID2)), depth));
            else
                System.out.println (StringUtils.padLeftSpaces ("* Type: " + NIContainerChunkType.get (chunkTypeID1), depth));

            compare (childItem1.getItem (), childItem2.getItem (), level + 1);
        }
    }


    private static void compareData (final IChunkData data1, final IChunkData data2, final int level)
    {
        if (data1 instanceof final SubTreeItemChunkData subTree1 && data2 instanceof final SubTreeItemChunkData subTree2)
        {
            compare (subTree1.getSubTree (), subTree2.getSubTree (), level + 1);
            return;
        }

        if (data1 instanceof TerminatorChunkData || data1.equals (data2))
            return;

        System.out.println (StringUtils.padLeftSpaces ("Data 1:", level * 4));
        System.out.print (data1.dump (level + 1));
        System.out.println (StringUtils.padLeftSpaces ("Data 2:", level * 4));
        System.out.print (data2.dump (level + 1));
    }
}
