// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.KontaktPresetChunk;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.KontaktPresetChunkID;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.NIContainerChunkType;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.NIContainerDataChunk;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.NIContainerItem;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.PresetChunkData;
import de.mossgrabers.tools.StringUtils;


/**
 * Compares the program data stored in two different containers.
 *
 * @author Jürgen Moßgraber
 */
public class CompareKontaktPrograms
{
    /**
     * Compares the program data stored in two different containers.
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

        final NIContainerDataChunk dataChunk1 = niContainerItem1.find (NIContainerChunkType.PRESET_CHUNK_ITEM);
        final NIContainerDataChunk dataChunk2 = niContainerItem2.find (NIContainerChunkType.PRESET_CHUNK_ITEM);
        if (dataChunk1.getData () instanceof final PresetChunkData preset1 && dataChunk2.getData () instanceof final PresetChunkData preset2)
            comparePresetChunkList (preset1.getChunks (), preset2.getChunks (), 0);
    }


    private static void comparePresetChunkList (final List<KontaktPresetChunk> chunks1, final List<KontaktPresetChunk> chunks2, final int level)
    {
        if (chunks1.size () != chunks2.size ())
        {
            System.out.println (StringUtils.padLeftSpaces ("Different number of Kontakt Preset chunks!", level * 4));
            return;
        }

        for (int i = 0; i < chunks1.size (); i++)
        {
            final KontaktPresetChunk kontaktPresetChunk1 = chunks1.get (i);
            final KontaktPresetChunk kontaktPresetChunk2 = chunks2.get (i);
            comparePresetChunks (kontaktPresetChunk1, kontaktPresetChunk2, level);
            comparePresetChunkList (kontaktPresetChunk1.getChildren (), kontaktPresetChunk2.getChildren (), level + 1);
        }
    }


    private static void comparePresetChunks (final KontaktPresetChunk kontaktPresetChunk1, final KontaktPresetChunk kontaktPresetChunk2, final int level)
    {
        final int depth = level * 4;

        final int id1 = kontaktPresetChunk1.getId ();
        System.out.println (StringUtils.padLeftSpaces (KontaktPresetChunkID.getName (id1) + " (" + id1 + ")", depth));
        final int id2 = kontaktPresetChunk2.getId ();
        if (id1 != id2)
            System.out.println (StringUtils.padLeftSpaces (String.format ("Different IDs: %d %d ", Integer.valueOf (id1), Integer.valueOf (id2)), depth));

        final int version1 = kontaktPresetChunk1.getVersion ();
        final int version2 = kontaktPresetChunk2.getVersion ();
        if (version1 != version2)
            System.out.println (StringUtils.padLeftSpaces (String.format ("Different versions: %d %d ", Integer.valueOf (version1), Integer.valueOf (version2)), depth));

        final byte [] privateData1 = kontaktPresetChunk1.getPrivateData ();
        final byte [] privateData2 = kontaktPresetChunk2.getPrivateData ();
        if (Arrays.compare (privateData1, privateData2) != 0)
        {
            System.out.println (StringUtils.padLeftSpaces ("Different Private Data", depth));
            System.out.println (StringUtils.padLeftSpaces ("  " + StringUtils.formatHexStr (privateData1), depth));
            System.out.println (StringUtils.padLeftSpaces ("  " + StringUtils.formatHexStr (privateData2), depth));
        }

        final byte [] publicData1 = kontaktPresetChunk1.getPublicData ();
        final byte [] publicData2 = kontaktPresetChunk2.getPublicData ();
        if (Arrays.compare (publicData1, publicData2) != 0)
        {
            System.out.println (StringUtils.padLeftSpaces ("Different Public Data", depth));
            System.out.println (StringUtils.padLeftSpaces ("  " + StringUtils.formatHexStr (publicData1), depth));
            System.out.println (StringUtils.padLeftSpaces ("  " + StringUtils.formatHexStr (publicData2), depth));
        }
    }
}
