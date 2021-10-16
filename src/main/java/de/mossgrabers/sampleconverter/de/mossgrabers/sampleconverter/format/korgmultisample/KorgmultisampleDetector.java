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

    // Multisample
    private static final int    ID_AUTHOR       = 0x12;
    private static final int    ID_CATEGORY     = 0x1A;
    private static final int    ID_COMMENT      = 0x22;
    private static final int    ID_SAMPLE       = 0x2A;

    // Sample
    private static final int    ID_START        = 0x10;              // flexible Bytes
    private static final int    ID_LOOP_START   = 0x18;              // flexible Bytes
    private static final int    ID_END          = 0x20;              // flexible Bytes
    private static final int    ID_LOOP_TUNE    = 0x45;              // 4 Byte
    private static final int    ID_ONE_SHOT     = 0x48;              // 1 Byte
    private static final int    ID_BOOST_12DB   = 0x50;              // 1 Byte

    // Key Zone
    private static final int    ID_KEY_BOTTOM   = 0x10;              // 1 Byte
    private static final int    ID_KEY_TOP      = 0x18;              // 1 Byte
    private static final int    ID_KEY_ORIGINAL = 0x20;              // 1 Byte
    private static final int    ID_FIXED_PITCH  = 0x28;              // 1 Byte
    private static final int    ID_TUNE         = 0x35;              // 4 Byte
    private static final int    ID_LEVEL_LEFT   = 0x3D;              // 4 Byte
    private static final int    ID_LEVEL_RIGHT  = 0x45;              // 4 Byte
    private static final int    ID_COLOR        = 0x50;              // 4 Byte: FF FF FF FF = White


    public static void main (final String [] args)
    {
        final String name = "C:\\Privat\\Sounds\\Wavestate\\One.korgmultisample";
        // final String name = "C:\\Privat\\Sounds\\Wavestate\\01W a realD50.korgmultisample";
        try (FileInputStream in = new FileInputStream (name))
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
            // TODO What are the 6 bytes? - 18 01 25 00 00 00
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
                next = in.read ();
            }

            // TODO First 3 bytes are always different when stored, is this a checksum or time?
            // B7 68 5F 61 00 00 00 00
            creatorToolData = in.readNBytes (8);

            System.out.println ("Size Content: " + from4BytesLSB (in.readNBytes (4)));

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


    private static void readSample (final FileInputStream in) throws IOException
    {
        // 1 = blockSize, 2 = 0x0A, 3 = Offset to key zone data
        final byte [] sampleFileHeader = in.readNBytes (3);

        final int blockLength = sampleFileHeader[0];

        dumpArray ("\nSampleFileHeader: ", sampleFileHeader);
        checkAscii (in);
        final String sampleFile = readAscii (in);
        System.out.println ("Sample File     : " + sampleFile);

        final int rest = blockLength - 3 - sampleFile.length () - 1;

        // 18 A7 F4 04 20 F9 E7 0D 18 7F 20 18 28 01 50 88 B3 84
        // 28 01 = Fixed Pitch

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
        for (final byte b: data)
            System.out.print (String.format ("%02X ", b));
        System.out.println ("");
    }


    private static int from4BytesLSB (final byte [] data)
    {
        return (data[3] & 0xFF) << 24 | (data[2] & 0xFF) << 16 | (data[1] & 0xFF) << 8 | data[0] & 0xFF;
    }
}
