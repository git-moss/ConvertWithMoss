// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.decentsampler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.file.Blowfish;
import de.mossgrabers.tools.ui.Functions;


/**
 * Reads the preset of a DecentSampler product file (dsproduct) of version 001. The file contains
 * the same XML document as a dspreset file in a binary container: a header followed by the
 * document which is encrypted with Blowfish in ECB mode and padded to whole 8-byte blocks. The
 * paths of the samples in the document are relative to the location of the product file.
 *
 * @author Jürgen Moßgraber
 */
final class DecentSamplerProduct
{
    private static final byte []   HEADER           = "DECENTSAMPLER001".getBytes (StandardCharsets.US_ASCII);
    /** The key of the format. */
    private static final Blowfish  CIPHER           = new Blowfish ("DNrTWJQUUEhCTyhsnEJU54TkAcRx4hfL3RfnuFSMEBwz8fR9kFSYZyFjJu7VPH7QqPdcVQ95".getBytes (StandardCharsets.US_ASCII));
    /** A preset contains only the description of the instrument, the samples are separate files. */
    private static final int       MAX_PAYLOAD_SIZE = 64 * 1024 * 1024;
    private static final int       BLOCK_SIZE       = 8;


    /**
     * Private constructor for utility class.
     */
    private DecentSamplerProduct ()
    {
        // Intentionally empty
    }


    /**
     * Read the preset of a product file. The stream is not closed.
     *
     * @param input The stream of the product file or of the entry of a library
     * @return The XML document of the preset
     * @throws IOException The file has an unknown version, is truncated or damaged
     */
    static String read (final InputStream input) throws IOException
    {
        final byte [] header = input.readNBytes (HEADER.length);
        if (!Arrays.equals (header, HEADER))
            throw new IOException (Functions.getMessage ("IDS_DS_PRODUCT_UNKNOWN_VERSION"));

        final byte [] data = input.readNBytes (MAX_PAYLOAD_SIZE + 1);
        if (data.length > MAX_PAYLOAD_SIZE)
            throw new IOException (Functions.getMessage ("IDS_DS_PRODUCT_TOO_LARGE"));
        if (data.length == 0 || data.length % BLOCK_SIZE != 0)
            throw new IOException (Functions.getMessage ("IDS_DS_PRODUCT_TRUNCATED"));

        // The two 32-bit halves of each block are stored in little-endian order
        CIPHER.decrypt (data, 0, data.length, ByteOrder.LITTLE_ENDIAN);

        // Each of the 1 to 8 padding bytes contains the number of padding bytes
        final int padding = Byte.toUnsignedInt (data[data.length - 1]);
        if (padding < 1 || padding > BLOCK_SIZE)
            throw new IOException (Functions.getMessage ("IDS_DS_PRODUCT_DAMAGED"));
        for (int i = data.length - padding; i < data.length; i++)
            if (Byte.toUnsignedInt (data[i]) != padding)
                throw new IOException (Functions.getMessage ("IDS_DS_PRODUCT_DAMAGED"));

        // Report damaged text instead of silently replacing characters, e.g. in sample paths
        try
        {
            return StandardCharsets.UTF_8.newDecoder ().decode (ByteBuffer.wrap (data, 0, data.length - padding)).toString ();
        }
        catch (final CharacterCodingException _)
        {
            throw new IOException (Functions.getMessage ("IDS_DS_PRODUCT_DAMAGED"));
        }
    }
}
