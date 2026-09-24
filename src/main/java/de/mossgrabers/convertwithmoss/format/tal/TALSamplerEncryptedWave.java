// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;

import de.mossgrabers.convertwithmoss.file.Blowfish;
import de.mossgrabers.tools.ui.Functions;


/**
 * An encrypted sample of TAL-Sampler (*.talwav), in which the factory library of the plug-in and
 * many sample packs deliver their samples. The plug-in creates it from a WAV file with 'Convert wav
 * Samples to talwav': the WAV file encrypted with Blowfish in the electronic code-book mode with a
 * fixed key of 72 bytes. The two halves of a block of 8 bytes are 32-bit little-endian words, and
 * the up to 7 bytes behind the last whole block are not encrypted.
 * <p>
 * The plug-in writes the encrypted data behind the content of a file which already exists, so each
 * further conversion of the same samples appends another copy of the WAV file, encrypted on its
 * own. The plug-in decrypts the whole file and reads the WAV file which the RIFF chunk at its start
 * describes, the first copy, which is therefore the only one that is decrypted here.
 *
 * @author Jürgen Moßgraber
 */
public final class TALSamplerEncryptedWave
{
    private static final String  FILE_ENDING = ".talwav";

    // The 'RIFF' chunk header, the 'WAVE' form type and the chunk header of the format chunk fill
    // the first two blocks
    private static final int     HEADER_SIZE = 16;

    private static final byte [] KEY         =
    {
        (byte) 0x75,
        (byte) 0x7F,
        (byte) 0x31,
        (byte) 0x15,
        (byte) 0x03,
        (byte) 0x6C,
        (byte) 0xF5,
        (byte) 0x71,
        (byte) 0x6E,
        (byte) 0xEF,
        (byte) 0xAD,
        (byte) 0xAE,
        (byte) 0x11,
        (byte) 0x9E,
        (byte) 0x0E,
        (byte) 0x74,
        (byte) 0x27,
        (byte) 0x78,
        (byte) 0x22,
        (byte) 0xF2,
        (byte) 0x4A,
        (byte) 0x2B,
        (byte) 0xC8,
        (byte) 0x0B,
        (byte) 0xB3,
        (byte) 0xD4,
        (byte) 0xDA,
        (byte) 0xCB,
        (byte) 0x91,
        (byte) 0x01,
        (byte) 0x3F,
        (byte) 0x18,
        (byte) 0x38,
        (byte) 0x44,
        (byte) 0x43,
        (byte) 0xFD,
        (byte) 0x72,
        (byte) 0x03,
        (byte) 0x03,
        (byte) 0xD5,
        (byte) 0xEE,
        (byte) 0x43,
        (byte) 0x14,
        (byte) 0x90,
        (byte) 0x0B,
        (byte) 0xD0,
        (byte) 0x73,
        (byte) 0x31,
        (byte) 0xEA,
        (byte) 0xE7,
        (byte) 0xBA,
        (byte) 0xE4,
        (byte) 0x2C,
        (byte) 0xF6,
        (byte) 0x77,
        (byte) 0xD5,
        (byte) 0xD5,
        (byte) 0x4B,
        (byte) 0x70,
        (byte) 0x7E,
        (byte) 0xD8,
        (byte) 0xB2,
        (byte) 0xD8,
        (byte) 0x0B,
        (byte) 0x8B,
        (byte) 0x55,
        (byte) 0x03,
        (byte) 0xFB,
        (byte) 0x0D,
        (byte) 0x0B,
        (byte) 0x67,
        (byte) 0xDE
    };


    /**
     * Helper class.
     */
    private TALSamplerEncryptedWave ()
    {
        // Intentionally empty
    }


    /**
     * Check if a file is an encrypted sample. As the plug-in, the ending of the file name is
     * compared ignoring the case.
     *
     * @param file The file to check
     * @return True if the file is an encrypted sample
     */
    public static boolean isEncrypted (final File file)
    {
        return file.getName ().toLowerCase (Locale.ROOT).endsWith (FILE_ENDING);
    }


    /**
     * Decrypt an encrypted sample.
     *
     * @param file The encrypted sample
     * @return The content of the WAV file
     * @throws IOException Could not read the file or it does not contain an encrypted WAV file
     */
    public static byte [] decrypt (final File file) throws IOException
    {
        final Blowfish blowfish = new Blowfish (KEY);
        try (final InputStream in = Files.newInputStream (file.toPath ()))
        {
            final byte [] header = in.readNBytes (HEADER_SIZE);
            blowfish.decrypt (header, 0, header.length, ByteOrder.LITTLE_ENDIAN);
            if (header.length < HEADER_SIZE || !"RIFF".equals (new String (header, 0, 4, StandardCharsets.US_ASCII)) || !"WAVE".equals (new String (header, 8, 4, StandardCharsets.US_ASCII)))
                throw new IOException (Functions.getMessage ("IDS_TAL_NOT_AN_ENCRYPTED_WAV", file.getName ()));

            // The size of the RIFF chunk ends the WAV file, a further copy might follow
            final long size = Math.min ((ByteBuffer.wrap (header).order (ByteOrder.LITTLE_ENDIAN).getInt (4) & 0xFFFFFFFFL) + 8, file.length ());
            if (size < HEADER_SIZE || size > Integer.MAX_VALUE)
                throw new IOException (Functions.getMessage ("IDS_TAL_NOT_AN_ENCRYPTED_WAV", file.getName ()));
            final byte [] content = Arrays.copyOf (header, (int) size);
            final int length = in.readNBytes (content, HEADER_SIZE, content.length - HEADER_SIZE);
            blowfish.decrypt (content, HEADER_SIZE, length, ByteOrder.LITTLE_ENDIAN);
            return content;
        }
    }
}
