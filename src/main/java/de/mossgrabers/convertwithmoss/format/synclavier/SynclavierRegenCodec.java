// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synclavier;

import java.nio.charset.StandardCharsets;


/**
 * The obfuscation layer of a Synclavier Regen SFLC sample file. An SFLC file is a normal FLAC file
 * whose bytes are XORed with a key-stream. The key-stream is produced by a full-period linear
 * congruential generator (LCG) modulo 2^64 which is seeded from the file's base name (the file name
 * without its extension). Since the operation is a plain XOR, the same method both encrypts and
 * decrypts. Note that the key-stream depends on the base name, therefore renaming an SFLC file
 * corrupts it.
 *
 * @author Jürgen Moßgraber
 */
public final class SynclavierRegenCodec
{
    /** The LCG multiplier (congruent 1 modulo 4). */
    private static final long MULTIPLIER = 0x3357C6A7C5DCC7F5L;
    /** The LCG increment (odd, therefore the generator has the full period of 2^64). */
    private static final long INCREMENT  = 0x8D14B6503262BD01L;
    /** The start value for the seed derivation. */
    private static final long SEED_BASE  = 0xAEAEE9B5E0E46745L;


    /**
     * Helper class.
     */
    private SynclavierRegenCodec ()
    {
        // Intentionally empty
    }


    /**
     * Derive the initial LCG state from the base name of the file.
     *
     * @param baseName The base name (file name without the extension)
     * @return The seed
     */
    private static long seed (final String baseName)
    {
        long state = SEED_BASE;
        for (final byte b: baseName.getBytes (StandardCharsets.UTF_8))
            state = (((b & 0xFFL) ^ state) * MULTIPLIER) + INCREMENT;
        return state;
    }


    /**
     * De-/en-crypts the given data. Since the transformation is a symmetric XOR with a key-stream,
     * the same method is used to convert a FLAC file into an SFLC file and vice versa.
     *
     * @param data The data to transform (encrypted or decrypted)
     * @param baseName The base name of the file (file name without the extension) which is used as
     *            the key
     * @return The transformed data (a new array)
     */
    public static byte [] transform (final byte [] data, final String baseName)
    {
        final byte [] result = new byte [data.length];
        long state = seed (baseName);
        for (int i = 0; i < data.length; i++)
        {
            state = state * MULTIPLIER + INCREMENT;
            result[i] = (byte) (data[i] ^ (int) (state & 0xFF));
        }
        return result;
    }


    /**
     * Removes the extension from a file name to form the key for the {@link #transform(byte[], String)}
     * method.
     *
     * @param fileName The file name (must not contain any path)
     * @return The file name without its extension
     */
    public static String baseName (final String fileName)
    {
        final int pos = fileName.lastIndexOf ('.');
        return pos < 0 ? fileName : fileName.substring (0, pos);
    }
}
