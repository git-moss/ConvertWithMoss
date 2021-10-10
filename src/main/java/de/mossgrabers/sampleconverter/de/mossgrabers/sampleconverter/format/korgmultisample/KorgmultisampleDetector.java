package de.mossgrabers.sampleconverter.format.korgmultisample;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;


public class KorgmultisampleDetector
{
    private static final String TAG_KORG        = "Korg";
    private static final String TAG_FILE_INFO   = "ExtendedFileInfo";
    private static final String TAG_MULTISAMPLE = "MultiSample";
    private static final String TAG_SINGLE_ITEM = "SingleItem";

    private static final int    ID_AUTHOR       = 0x12;
    private static final int    ID_CATEGORY     = 0x1A;
    private static final int    ID_COMMENT      = 0x22;
    private static final int    ID_SAMPLE       = 0x2A;


    public static void main (String [] args)
    {
        try (FileInputStream in = new FileInputStream ("C:\\Privat\\Sounds\\Wavestate\\01W a realD50.korgmultisample"))
        {
            final byte [] headerTag = in.readNBytes (4);
            System.out.println ("Header Tag      : " + new String (headerTag));

            // TODO What are the 8 bytes? - 27 00 00 00 08 01 12 12
            final byte [] header = in.readNBytes (8);
            dumpArray ("Header Data     : ", header);

            checkAscii (in);
            final String fileInfoTag = readAscii (in);
            System.out.println ("File Info Tag   : " + fileInfoTag);
            final byte [] fileInfoData = in.readNBytes (2);
            // TODO What are the 2 bytes? - 12 0F
            dumpArray ("File Info Data  : ", fileInfoData);

            checkAscii (in);
            final String multiSampleTag = readAscii (in);
            System.out.println ("Multi Sample Tag: " + multiSampleTag);
            // What are the 6 bytes? - 18 01 25 00 00 00
            final byte [] multiSampleHeader = in.readNBytes (6);
            dumpArray ("Multi Sample Hed: ", multiSampleHeader);

            checkAscii (in);
            final String singleItemTag = readAscii (in);
            System.out.println ("Single Item Tag : " + singleItemTag);
            // 12 ?
            final byte [] itemData = in.readNBytes (1);
            dumpArray ("Item Data       : ", itemData);

            final String creatorToolTag = readAscii (in);
            System.out.println ("Creator Tool    : " + creatorToolTag);

            int next = in.read ();
            final byte [] creatorToolData;
            if (next == 0x1A)
            {
                final String version = readAscii (in);
                System.out.println ("Version         : " + version);
                creatorToolData = in.readNBytes (13);
            }
            else
            {
                System.out.println ("Version         : <missing>");
                creatorToolData = in.readNBytes (12);
            }

            // B7 68 5F 61 00 00 00 00 B1 0D 00 00 ?
            dumpArray ("CreatorTool Data: ", creatorToolData);

            checkAscii (in);
            final String multisampleName = readAscii (in);
            System.out.println ("Multisample Name: " + multisampleName);

            int id;
            while ((id = in.read ()) != -1)
            {
                switch (id)
                {
                    case ID_AUTHOR:
                        final String author = readAscii (in);
                        System.out.println ("Author          : " + author);
                        break;

                    case ID_CATEGORY:
                        final String category = readAscii (in);
                        System.out.println ("Category        : " + category);
                        break;

                    case ID_COMMENT:
                        final String comment = readAscii (in);
                        System.out.println ("Comment         : " + comment);
                        break;

                    case ID_SAMPLE:
                        readSample (in);
                        break;

                    default:
                        final byte [] postfix = in.readNBytes (3);
                        dumpArray ("\nPost fix        : ", postfix);

                        final byte [] uuid = in.readNBytes (16);
                        System.out.println ("UUID            : " + UUID.nameUUIDFromBytes (uuid));

                        final byte [] rest = in.readAllBytes ();
                        System.out.println ("Rest            : " + rest.length);
                        break;
                }
            }
        }
        catch (final Exception ex)
        {
            ex.printStackTrace ();
        }
    }


    private static void readSample (FileInputStream in) throws IOException
    {
        // Key Zone
        // - Color
        // - Original / Bottom key / Top key
        // - Fixed Pitch
        // - Right / Left Level
        // - Tune
        // Sample
        // - One Shot (on/off)
        // - Boost 12dB (on/off)
        // - Loop Tune (-99.9 ... 99.9)
        // - Play start
        // - Loop start / end

        final byte [] sampleFileHeader = in.readNBytes (3);

        final int blockLength = sampleFileHeader[0];

        dumpArray ("\nSampleFileHeader: ", sampleFileHeader);
        checkAscii (in);
        final String sampleFile = readAscii (in);
        System.out.println ("Sample File     : " + sampleFile);

        final int rest = blockLength - 3 - sampleFile.length () - 1;

        final byte [] sampleFileData = in.readNBytes (rest);
        dumpArray ("Sample File Data: ", sampleFileData);
    }


    private static void checkAscii (final InputStream in) throws IOException
    {
        final int blockType = in.read ();
        if (blockType != 0x0A)
            throw new IOException ("Not an ASCII block.");
    }


    private static String readAscii (final InputStream in) throws IOException
    {
        final int blocklength = in.read ();
        final byte [] blockData = in.readNBytes (blocklength);
        return new String (blockData);
    }


    private static void dumpArray (final String name, final byte [] data)
    {
        System.out.print (name);
        for (byte b: data)
            System.out.print (String.format ("%02X ", b));
        System.out.println ("");
    }
}
