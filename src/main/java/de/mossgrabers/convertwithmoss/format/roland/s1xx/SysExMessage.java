// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s1xx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.mossgrabers.tools.ui.Functions;


/**
 * Immutable holder for one parsed SysEx message's header and payload.
 *
 * @author Jürgen Moßgraber
 */
public final class SysExMessage
{
    /** Roland MIDI system exclusive ID. */
    public static final int ROLAND_ID = 0x41;

    final int               deviceId;
    final int               modelId;
    final int               command;
    int                     address;
    int []                  data;
    int                     expectedLength;


    /**
     * Parses a single F0..F7 delimited SysEx frame into its header and payload components.
     *
     * @param frame The complete frame, including the leading F0 and trailing F7 bytes
     * @throws IOException If the frame is too short, malformed, contains a non-7-bit data byte, has
     *             an unexpected payload length, or has an invalid checksum
     */
    public SysExMessage (final byte [] frame) throws IOException
    {
        if (frame.length < 9)
            throw new IOException (Functions.getMessage ("IDS_S1X_SYSEX_TOO_SHORT"));

        if (Byte.toUnsignedInt (frame[0]) != 0xF0 || Byte.toUnsignedInt (frame[frame.length - 1]) != 0xF7)
            throw new IOException (Functions.getMessage ("IDS_S1X_SYSEX_INVALID_FRAME"));

        if (Byte.toUnsignedInt (frame[1]) != ROLAND_ID)
            throw new IOException (Functions.getMessage ("IDS_S1X_SYSEX_NOT_ROLAND"));

        for (int i = 1; i < frame.length - 1; i++)
            if (Byte.toUnsignedInt (frame[i]) > 0x7F)
                throw new IOException (Functions.getMessage ("IDS_S1X_SYSEX_NOT_7_BIT"));

        this.deviceId = Byte.toUnsignedInt (frame[2]);
        this.modelId = Byte.toUnsignedInt (frame[3]);
        this.command = Byte.toUnsignedInt (frame[4]);
        this.address = address (Byte.toUnsignedInt (frame[5]), Byte.toUnsignedInt (frame[6]), Byte.toUnsignedInt (frame[7]));
        this.data = getTail (frame);
    }


    private static int [] getTail (final byte [] frame)
    {
        final int tailLength = frame.length - 9;
        final int [] tail = new int [tailLength];
        for (int i = 0; i < tail.length; i++)
            tail[i] = Byte.toUnsignedInt (frame[8 + i]);
        return tail;
    }


    /**
     * Split and read all MIDI system exclusive messages.
     * 
     * @param bytes Several raw system exclusive message bytes
     * @return The read system exclusive messages
     * @throws IOException Could not read the messages
     */
    public static List<SysExMessage> readSysExMessages (final byte [] bytes) throws IOException
    {
        final List<SysExMessage> messages = new ArrayList<> ();

        int position = 0;
        while (position < bytes.length)
        {
            if (Byte.toUnsignedInt (bytes[position]) != 0xF0)
            {
                position++;
                continue;
            }

            int end = position + 1;
            while (end < bytes.length && Byte.toUnsignedInt (bytes[end]) != 0xF7)
                end++;

            if (end == bytes.length)
                throw new IOException ("Incomplete SysEx message beginning at byte offset " + position);

            final byte [] frame = Arrays.copyOfRange (bytes, position, end + 1);
            position = end + 1;

            messages.add (new SysExMessage (frame));
        }

        return messages;
    }


    /**
     * Roland checksum:
     *
     * checksum = (128 - ((address bytes + data bytes) mod 128)) mod 128
     *
     * @param address The 21-bit encoded Roland address covered by the checksum
     * @param data The 7-bit data bytes covered by the checksum
     * @return The resulting 7-bit Roland checksum value
     */
    public static int rolandChecksum (final int address, final int [] data)
    {
        int sum = addressByte0 (address) + addressByte1 (address) + addressByte2 (address);

        for (final int value: data)
        {
            require7Bit (value, "data");
            sum += value;
        }

        return (128 - (sum & 0x7F)) & 0x7F;
    }


    /**
     * Converts three 7-bit Roland address bytes to an integer.
     *
     * @param msb The most significant 7-bit address byte
     * @param middle The middle 7-bit address byte
     * @param lsb The least significant 7-bit address byte
     * @return The combined 21-bit address value
     */
    public static int address (final int msb, final int middle, final int lsb)
    {
        require7Bit (msb, "address MSB");
        require7Bit (middle, "address middle");
        require7Bit (lsb, "address LSB");

        return (msb << 14) | (middle << 7) | lsb;
    }


    /**
     * Validates that a value is a legal 7-bit MIDI data byte.
     *
     * @param value The value to validate
     * @param name The name of the value, used in the exception message if validation fails
     * @throws IllegalArgumentException If the value is outside the range 0..127
     */
    public static void require7Bit (final int value, final String name)
    {
        if (value < 0 || value > 0x7F)
            throw new IllegalArgumentException (name + " must be a 7-bit value, but was " + value);
    }


    /**
     * Validates and returns a 7-bit MIDI data byte.
     *
     * @param value The value to validate
     * @param name The name of the value, used in the exception message if validation fails
     * @return The validated value, unchanged
     * @throws IllegalArgumentException If the value is outside the range 0..127
     */
    public static int checked7Bit (final int value, final String name)
    {
        require7Bit (value, name);
        return value;
    }


    /**
     * Extracts the most significant 7-bit byte of an encoded address.
     *
     * @param address The 21-bit encoded Roland address
     * @return The most significant address byte
     */
    public static int addressByte0 (final int address)
    {
        return (address >>> 14) & 0x7F;
    }


    /**
     * Extracts the middle 7-bit byte of an encoded address.
     *
     * @param address The 21-bit encoded Roland address
     * @return The middle address byte
     */
    public static int addressByte1 (final int address)
    {
        return (address >>> 7) & 0x7F;
    }


    /**
     * Extracts the least significant 7-bit byte of an encoded address.
     *
     * @param address The 21-bit encoded Roland address
     * @return The least significant address byte
     */
    public static int addressByte2 (final int address)
    {
        return address & 0x7F;
    }


    /**
     * Decodes 1 nibble value.
     *
     * @param bytes The array containing the encoded nibble byte
     * @param offset The index of the byte within the array
     * @return The decoded value, in the range 0..0xF
     */
    public static int decodeNibble (final int [] bytes, final int offset)
    {
        return bytes[offset] & 0x0F;
    }


    /**
     * Decodes a two-nibble value from two consecutive bytes.
     *
     * @param bytes The array containing the two encoded nibble bytes
     * @param offset The index of the first (LSB) byte within the array
     * @return The decoded value, in the range 0..0xFFFFF
     */
    public static int decodeTwoNibbles (final int [] bytes, final int offset)
    {
        return (bytes[offset] & 0x0F) | ((bytes[offset + 1] & 0x0F) << 4);
    }


    /**
     * Decodes a four-nibble Start/End/Loop-style value from five consecutive bytes.
     *
     * @param bytes The array containing the four encoded nibble bytes
     * @param offset The index of the first (LSB) byte within the array
     * @return The decoded value, in the range 0..0xFFFFF
     */
    public static int decodeFourNibbles (final int [] bytes, final int offset)
    {
        return ((bytes[offset] & 0x0F) << 8) | ((bytes[offset + 1] & 0x0F) << 12) | (bytes[offset + 2] & 0x0F | ((bytes[offset + 3] & 0x0F) << 4));
    }


    /**
     * Format the address as 3 hex bytes.
     * 
     * @return The formatted string
     */
    public String formatAddress ()
    {
        return String.format ("%02X %02X %02X", Integer.valueOf (addressByte0 (this.address)), Integer.valueOf (addressByte1 (this.address)), Integer.valueOf (addressByte2 (this.address)));
    }
}
