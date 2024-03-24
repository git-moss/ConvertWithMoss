// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file;

import java.io.IOException;
import java.util.Arrays;


/**
 * Conversion of FastLZ to Java based on https://ariya.github.io/FastLZ/.
 *
 * @author Jürgen Moßgraber
 */
public class FastLZ
{
    private static final int FASTLZ_LEVEL0       = 0;
    private static final int FASTLZ_LEVEL1       = 1;
    private static final int FASTLZ_LEVEL2       = 2;

    private static final int HASH_LOG            = 13;
    private static final int HASH_SIZE           = 1 << HASH_LOG;
    private static final int HASH_MASK           = HASH_SIZE - 1;

    private static final int MAX_DISTANCE_LZ1    = 8192;
    private static final int MAX_DISTANCE_LZ2    = 8191;
    private static final int MAX_FARDISTANCE_LZ2 = 65535 + MAX_DISTANCE_LZ2 - 1;
    private static final int MAX_COPY            = 32;
    private static final int MAX_LEN             = 264;

    private static final int LEVEL_BARRIER       = 65536;


    /**
     * Compresses the given data.
     *
     * @param data The data to compress
     * @return The compressed data
     */
    public static byte [] compress (final byte [] data)
    {
        final byte [] compressed = new byte [calcLength (data.length)];
        final int size = compressLZ (data, compressed, data.length < LEVEL_BARRIER ? FASTLZ_LEVEL1 : FASTLZ_LEVEL2);
        return Arrays.copyOf (compressed, size);
    }


    /**
     * Uncompresses the given data.
     *
     * @param compressed The compressed data to uncompress
     * @param sizeOutput The size of the output
     * @return The compressed data
     * @throws IOException If the data could not uncompressed or the given output size was too small
     */
    public static byte [] uncompress (final byte [] compressed, final int sizeOutput) throws IOException
    {
        final byte [] out = new byte [sizeOutput];
        final int result = uncompress (compressed, out);
        return Arrays.copyOf (out, result);
    }


    private static int compressLZ (final byte [] data, final byte [] compressed, final int compressionLevel)
    {
        int ip = 0;
        int op = 0;
        int ipBound = data.length - 2;

        if (data.length < 4)
        {
            if (data.length != 0)
            {
                compressed[op++] = (byte) (data.length - 1);
                ipBound++;
                while (ip <= ipBound)
                    compressed[op++] = data[ip++];
                return data.length + 1;
            }
            return 0;
        }

        final int [] htab = new int [HASH_SIZE];
        int hslot;
        for (hslot = 0; hslot < HASH_SIZE; hslot++)
            htab[hslot] = ip;

        int copy = 2;
        compressed[op++] = MAX_COPY - 1;
        compressed[op++] = data[ip++];
        compressed[op++] = data[ip++];

        final int ipLimit = data.length - 12;
        int hval;
        while (ip < ipLimit)
        {
            int ref = 0;
            int distance = 0;
            int len = 3;
            int anchor = ip;
            boolean labelMatch = false;

            if (compressionLevel == FASTLZ_LEVEL2 && data[ip] == data[ip - 1] && readU16 (data, ip - 1) == readU16 (data, ip + 1))
            {
                distance = 1;
                ip += 3;
                ref = anchor - 1 + 3;
                labelMatch = true;
            }

            if (!labelMatch)
            {
                hval = hashFunction (data, ip);
                hslot = hval;
                ref = htab[hval];
                distance = anchor - ref;
                htab[hslot] = anchor;

                if (distance == 0 || (compressionLevel == FASTLZ_LEVEL1 ? distance >= MAX_DISTANCE_LZ1 : distance >= MAX_FARDISTANCE_LZ2) || data[ref++] != data[ip++] || data[ref++] != data[ip++] || data[ref++] != data[ip++])
                {
                    compressed[op++] = data[anchor++];
                    ip = anchor;
                    copy++;
                    if (copy == MAX_COPY)
                    {
                        copy = 0;
                        compressed[op++] = MAX_COPY - 1;
                    }
                    continue;
                }

                if (compressionLevel == FASTLZ_LEVEL2 && distance >= MAX_DISTANCE_LZ2)
                {
                    if (data[ip++] != data[ref++] || data[ip++] != data[ref++])
                    {
                        compressed[op++] = data[anchor++];
                        ip = anchor;
                        copy++;
                        if (copy == MAX_COPY)
                        {
                            copy = 0;
                            compressed[op++] = MAX_COPY - 1;
                        }
                        continue;
                    }
                    len += 2;
                }
            }

            ip = anchor + len;
            distance--;

            if (distance == 0)
            {
                final byte x = data[ip - 1];
                while (ip < ipBound)
                {
                    if (data[ref++] != x)
                        break;
                    ip++;
                }
            }
            else
                while (true)
                {
                    if (data[ref++] != data[ip++] || data[ref++] != data[ip++] || data[ref++] != data[ip++] || data[ref++] != data[ip++])
                        break;
                    if (data[ref++] != data[ip++] || data[ref++] != data[ip++] || data[ref++] != data[ip++] || data[ref++] != data[ip++])
                        break;
                    while (ip < ipBound)
                        if (data[ref++] != data[ip++])
                            break;
                    break;
                }

            if (copy != 0)
                compressed[op - copy - 1] = (byte) (copy - 1);
            else
                op--;

            copy = 0;
            ip -= 3;
            len = ip - anchor;

            if (compressionLevel == FASTLZ_LEVEL2)
            {
                if (distance < MAX_DISTANCE_LZ2)
                {
                    if (len < 7)
                    {
                        compressed[op++] = (byte) ((len << 5) + (distance >>> 8));
                        compressed[op++] = (byte) (distance & 255);
                    }
                    else
                    {
                        compressed[op++] = (byte) ((7 << 5) + (distance >>> 8));
                        for (len -= 7; len >= 255; len -= 255)
                            compressed[op++] = (byte) 255;
                        compressed[op++] = (byte) len;
                        compressed[op++] = (byte) (distance & 255);
                    }
                }
                else if (len < 7)
                {
                    distance -= MAX_DISTANCE_LZ2;
                    compressed[op++] = (byte) ((len << 5) + 31);
                    compressed[op++] = (byte) 255;
                    compressed[op++] = (byte) (distance >>> 8);
                    compressed[op++] = (byte) (distance & 255);
                }
                else
                {
                    distance -= MAX_DISTANCE_LZ2;
                    compressed[op++] = (byte) ((7 << 5) + 31);
                    for (len -= 7; len >= 255; len -= 255)
                        compressed[op++] = (byte) 255;
                    compressed[op++] = (byte) len;
                    compressed[op++] = (byte) 255;
                    compressed[op++] = (byte) (distance >>> 8);
                    compressed[op++] = (byte) (distance & 255);
                }
            }
            else
            {
                final int length = MAX_LEN - 2;
                if (len > length)
                    while (len > length)
                    {
                        compressed[op++] = (byte) ((7 << 5) + (distance >>> 8));
                        compressed[op++] = (byte) (length - 7 - 2);
                        compressed[op++] = (byte) (distance & 255);
                        len -= length;
                    }

                if (len < 7)
                {
                    compressed[op++] = (byte) ((len << 5) + (distance >>> 8));
                    compressed[op++] = (byte) (distance & 255);
                }
                else
                {
                    compressed[op++] = (byte) ((7 << 5) + (distance >>> 8));
                    compressed[op++] = (byte) (len - 7);
                    compressed[op++] = (byte) (distance & 255);
                }
            }

            hval = hashFunction (data, ip);
            htab[hval] = ip++;
            hval = hashFunction (data, ip);
            htab[hval] = ip++;

            compressed[op++] = MAX_COPY - 1;
        }

        ipBound++;
        while (ip <= ipBound)
        {
            compressed[op++] = data[ip++];
            copy++;
            if (copy == MAX_COPY)
            {
                copy = 0;
                compressed[op++] = MAX_COPY - 1;
            }
        }

        if (copy != 0)
            compressed[op - copy - 1] = (byte) (copy - 1);
        else
            op--;

        if (compressionLevel == FASTLZ_LEVEL2)
            compressed[0] |= 1 << 5;

        return op;
    }


    private static int uncompress (final byte [] compressed, final byte [] uncompressed) throws IOException
    {
        final int compressionLevel = findLevel (compressed);
        if (compressionLevel == FASTLZ_LEVEL0)
            throw new IOException ("FastLZ: Cannot uncompress level 0.");

        int ip = 0;
        int op = 0;
        long ctrl = compressed[ip++] & 31;

        boolean loop = true;
        do
        {
            int ref = op;
            long len = ctrl >>> 5;
            long ofs = (ctrl & 31) << 8;

            if (ctrl >= 32)
            {
                int code;
                len--;
                ref -= ofs;

                if (len == 6)
                    if (compressionLevel == FASTLZ_LEVEL1)
                        len += compressed[ip++] & 0xff;
                    else
                        do
                        {
                            code = compressed[ip++] & 0xff;
                            len += code;
                        } while (code == 255);
                if (compressionLevel == FASTLZ_LEVEL1)
                    ref -= compressed[ip++] & 0xff;
                else
                {
                    code = compressed[ip++] & 0xff;
                    ref -= code;

                    if (code == 255 && ofs == 31 << 8)
                    {
                        ofs = (compressed[ip++] & 0xff) << 8;
                        ofs += compressed[ip++] & 0xff;

                        ref = (int) (op - ofs - MAX_DISTANCE_LZ2);
                    }
                }

                if (op + len + 3 > uncompressed.length)
                    throw new IOException ("FastLZ: (op+len+3 > out.length)");
                if (ref - 1 < 0)
                    throw new IOException ("FastLZ: (ref-1 < 0)");

                if (ip < compressed.length)
                    ctrl = compressed[ip++] & 0xff;
                else
                    loop = false;

                if (ref == op)
                {
                    final byte b = uncompressed[ref - 1];
                    uncompressed[op++] = b;
                    uncompressed[op++] = b;
                    uncompressed[op++] = b;

                    for (; len != 0; --len)
                        uncompressed[op++] = b;
                }
                else
                {
                    ref--;

                    uncompressed[op++] = uncompressed[ref++];
                    uncompressed[op++] = uncompressed[ref++];
                    uncompressed[op++] = uncompressed[ref++];

                    for (; len != 0; --len)
                        uncompressed[op++] = uncompressed[ref++];
                }
            }
            else
            {
                ctrl++;

                if (op + ctrl > uncompressed.length)
                    throw new IOException ("FastLZ: Unsound data (op + ctrl > out.length).");
                if (ip + ctrl > compressed.length)
                    throw new IOException ("FastLZ: Unsound data (ip + ctrl > in.length).");

                uncompressed[op++] = compressed[ip++];

                for (--ctrl; ctrl != 0; ctrl--)
                    uncompressed[op++] = compressed[ip++];

                loop = ip < compressed.length;
                if (loop)
                    ctrl = compressed[ip++] & 0xff;
            }
        } while (loop);

        return op;
    }


    private static int readU16 (final byte [] in, final int offset)
    {
        if (offset + 1 >= in.length)
            return in[offset] & 0xFF;
        return (in[offset] & 0xFF) + ((in[offset + 1] & 0xFF) << 8);
    }


    private static int hashFunction (final byte [] in, final int offset)
    {
        int v = readU16 (in, offset);
        v ^= readU16 (in, offset + 1) ^ v >>> 16 - HASH_LOG;
        v &= HASH_MASK;
        return v;
    }


    private static int findLevel (final byte [] in)
    {
        return (in[0] >>> 5) + 1;
    }


    private static int calcLength (final int length)
    {
        return (int) Math.max (66, length * 1.06);
    }


    /**
     * Utility class.
     */
    private FastLZ ()
    {
        // Intentionally empty
    }
}
