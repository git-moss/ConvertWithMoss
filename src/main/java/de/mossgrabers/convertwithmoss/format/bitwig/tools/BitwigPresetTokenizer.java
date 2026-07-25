// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.bitwig.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


/**
 * Heuristic reader for Bitwig device preset (<i>.bwpreset</i>) files. This is a
 * <b>reverse-engineering aid</b>, not a validating parser: the serialized object graph is
 * undocumented, so the tokenizer recognizes only the constructs that have been identified so far
 * and falls back to raw byte runs for everything else.
 * <p>
 * Recognized structure:
 * <ul>
 * <li>Header: the ASCII magic {@code BtWg} followed by an ASCII-hex run (format version plus
 * section length fields) up to the first binary byte.</li>
 * <li>Tagged string atom: {@code 0x08}, a 4-byte big-endian length, then that many printable
 * bytes. Used for parameter ids, the device name, sample names and library paths.</li>
 * <li>Scalar parameter atom: a small 4-byte big-endian class id (e.g. {@code 0x00000136})
 * followed by {@code 0x07} and an 8-byte big-endian IEEE-754 double. This carries the envelope,
 * filter, gain and pan values.</li>
 * <li>Meta key: {@code 0x00000001}, a 4-byte length, then a printable key (the textual
 * {@code meta} block uses untagged keys).</li>
 * <li>Nested object marker: {@code 0x09} followed by two 4-byte length fields.</li>
 * </ul>
 *
 * @author Jürgen Moßgraber
 */
public final class BitwigPresetTokenizer
{
    /** The 4-byte file magic of a Bitwig preset. */
    public static final String MAGIC               = "BtWg";

    private static final int   STRING_TAG          = 0x08;
    private static final int   DOUBLE_TAG          = 0x07;
    private static final int   OBJECT_TAG          = 0x09;
    private static final int   META_KEY_MARKER     = 0x00000001;
    private static final int   MAX_STRING_LENGTH   = 4096;
    private static final int   MAX_META_KEY_LENGTH = 256;
    private static final int   MAX_PARAM_CLASS_ID  = 0xFFFF;
    private static final int   RAW_PREVIEW_BYTES   = 16;


    /** The kind of a recognized token. */
    public enum TokenType
    {
        /** The {@code BtWg} magic plus the ASCII-hex header run. */
        HEADER,
        /** An untagged key of the textual meta block. */
        META_KEY,
        /** A tagged string atom (parameter id, device name, sample path, ...). */
        STRING,
        /** A scalar parameter atom: class id plus an 8-byte double value. */
        PARAM,
        /** A nested object marker plus its two following length fields. */
        OBJECT_START,
        /** A run of bytes that did not match any known construct. */
        RAW
    }


    /** One recognized token of a preset byte stream. */
    public static final class Token
    {
        /** The byte offset of the token in the file. */
        public final int       offset;
        /** The number of bytes the token spans. */
        public final int       length;
        /** The kind of token. */
        public final TokenType type;
        /** The text value for {@link TokenType#HEADER}, {@link TokenType#META_KEY},
         *  {@link TokenType#STRING} and the hex preview of {@link TokenType#RAW}. */
        public final String    text;
        /** The class id for {@link TokenType#PARAM} / the first length for
         *  {@link TokenType#OBJECT_START}. */
        public final long      number;
        /** The double value for {@link TokenType#PARAM} / the second length for
         *  {@link TokenType#OBJECT_START}. */
        public final double    value;


        /**
         * Constructor.
         *
         * @param offset The byte offset
         * @param length The number of bytes spanned
         * @param type The token kind
         * @param text The text or hex preview
         * @param number The class id / first length
         * @param value The double value / second length
         */
        public Token (final int offset, final int length, final TokenType type, final String text, final long number, final double value)
        {
            this.offset = offset;
            this.length = length;
            this.type = type;
            this.text = text;
            this.number = number;
            this.value = value;
        }
    }


    /**
     * Private constructor for utility class.
     */
    private BitwigPresetTokenizer ()
    {
        // Intentionally empty
    }


    /**
     * Read and tokenize a Bitwig preset file.
     *
     * @param path The path of the <i>.bwpreset</i> file
     * @return The recognized tokens in file order
     * @throws IOException Could not read the file or it is not a Bitwig preset
     */
    public static List<Token> tokenize (final Path path) throws IOException
    {
        return tokenize (Files.readAllBytes (path));
    }


    /**
     * Tokenize the given Bitwig preset bytes.
     *
     * @param data The raw file content
     * @return The recognized tokens in file order
     * @throws IOException The data does not start with the {@code BtWg} magic
     */
    public static List<Token> tokenize (final byte [] data) throws IOException
    {
        if (data.length < 4 || data[0] != 'B' || data[1] != 't' || data[2] != 'W' || data[3] != 'g')
            throw new IOException ("Not a Bitwig preset (missing BtWg magic).");

        final List<Token> tokens = new ArrayList<> ();

        // Header: ASCII-hex run after the magic up to the first binary byte
        int index = 4;
        final StringBuilder header = new StringBuilder ();
        while (index < data.length && isHexAscii (data[index]))
        {
            header.append ((char) data[index]);
            index++;
        }
        tokens.add (new Token (0, index, TokenType.HEADER, header.toString (), 0, 0));

        int rawStart = -1;
        while (index < data.length)
        {
            final Token token = recognize (data, index);
            if (token == null)
            {
                if (rawStart < 0)
                    rawStart = index;
                index++;
                continue;
            }

            if (rawStart >= 0)
            {
                tokens.add (rawToken (data, rawStart, index));
                rawStart = -1;
            }
            tokens.add (token);
            index += token.length;
        }
        if (rawStart >= 0)
            tokens.add (rawToken (data, rawStart, data.length));

        return tokens;
    }


    /**
     * Try to recognize a known construct at the given offset.
     *
     * @param data The file content
     * @param index The offset to test
     * @return The recognized token or null if nothing matched at this offset
     */
    private static Token recognize (final byte [] data, final int index)
    {
        // Tagged string atom: 08 <len:4> <printable len bytes>
        if ((data[index] & 0xFF) == STRING_TAG && index + 5 <= data.length)
        {
            final long length = readUnsigned32 (data, index + 1);
            if (length >= 1 && length <= MAX_STRING_LENGTH && index + 5 + length <= data.length && isPrintable (data, index + 5, (int) length))
            {
                final String text = new String (data, index + 5, (int) length, java.nio.charset.StandardCharsets.US_ASCII);
                return new Token (index, 5 + (int) length, TokenType.STRING, text, 0, 0);
            }
        }

        // Scalar parameter atom: <classId:4> 07 <double:8>
        if (index + 13 <= data.length && (data[index + 4] & 0xFF) == DOUBLE_TAG)
        {
            final long classId = readUnsigned32 (data, index);
            if (classId > 0 && classId <= MAX_PARAM_CLASS_ID)
            {
                final double value = Double.longBitsToDouble (readUnsigned64 (data, index + 5));
                return new Token (index, 13, TokenType.PARAM, null, classId, value);
            }
        }

        // Meta key: 00000001 <len:4> <printable key>
        if (index + 8 <= data.length && readUnsigned32 (data, index) == META_KEY_MARKER)
        {
            final long length = readUnsigned32 (data, index + 4);
            if (length >= 1 && length <= MAX_META_KEY_LENGTH && index + 8 + length <= data.length && isPrintable (data, index + 8, (int) length))
            {
                final String text = new String (data, index + 8, (int) length, java.nio.charset.StandardCharsets.US_ASCII);
                return new Token (index, 8 + (int) length, TokenType.META_KEY, text, 0, 0);
            }
        }

        // Nested object marker: 09 <len:4> <len:4>
        if ((data[index] & 0xFF) == OBJECT_TAG && index + 9 <= data.length)
        {
            final long length1 = readUnsigned32 (data, index + 1);
            final long length2 = readUnsigned32 (data, index + 5);
            return new Token (index, 9, TokenType.OBJECT_START, null, length1, length2);
        }

        return null;
    }


    /**
     * Build a RAW token for an unrecognized byte run.
     *
     * @param data The file content
     * @param start The first offset of the run
     * @param end The offset after the run
     * @return The RAW token (text is a short hex preview)
     */
    private static Token rawToken (final byte [] data, final int start, final int end)
    {
        final int length = end - start;
        final int previewLength = Math.min (RAW_PREVIEW_BYTES, length);
        final StringBuilder preview = new StringBuilder ();
        for (int i = 0; i < previewLength; i++)
            preview.append (String.format ("%02x", Integer.valueOf (data[start + i] & 0xFF)));
        if (length > previewLength)
            preview.append ("...");
        return new Token (start, length, TokenType.RAW, preview.toString (), 0, 0);
    }


    private static boolean isHexAscii (final byte b)
    {
        final int c = b & 0xFF;
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }


    private static boolean isPrintable (final byte [] data, final int offset, final int length)
    {
        for (int i = 0; i < length; i++)
        {
            final int c = data[offset + i] & 0xFF;
            if (c < 0x20 || c > 0x7E)
                return false;
        }
        return true;
    }


    private static long readUnsigned32 (final byte [] data, final int offset)
    {
        return (data[offset] & 0xFFL) << 24 | (data[offset + 1] & 0xFFL) << 16 | (data[offset + 2] & 0xFFL) << 8 | data[offset + 3] & 0xFFL;
    }


    private static long readUnsigned64 (final byte [] data, final int offset)
    {
        long result = 0;
        for (int i = 0; i < 8; i++)
            result = result << 8 | data[offset + i] & 0xFFL;
        return result;
    }
}
