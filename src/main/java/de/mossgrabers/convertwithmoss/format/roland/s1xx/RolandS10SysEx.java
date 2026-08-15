// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s1xx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.mossgrabers.tools.ui.Functions;


/**
 * Roland S-10 SysEx reader/writer.
 *
 * MIDI SysEx frame: F0 41 <deviceId> 10 <command> <address[3]> <data...> [checksum] F7
 *
 * Roland commands: 11 = RQ1 12 = DT1, one-way data transfer 40 = WSD, handshake write-data request
 * 41 = RQD, handshake read-data request 42 = DAT, handshake data transfer 43 = ACK 45 = EOD 4E =
 * ERR 4F = RJC
 *
 * @author Jürgen Moßgraber
 */
public final class RolandS10SysEx
{
    /** The length of the full transport array performance parameter block. */
    public static final int        PERFORMANCE_PARAMETER_BYTES  = 40;

    /** Model ID for S-10, MKS 100, S-220 */
    private static final int       MODEL_S10_ID                 = 0x10;

    private static final int       SAMPLE_PACKET_BYTES          = 128;

    private static final int       ADDRESS_SAMPLE_DUMP_MODE     = SysExMessage.address (0x00, 0x10, 0x02);

    private static final int       ADDRESS_WAVE_TRANSFER_1      = SysExMessage.address (0x01, 0x00, 0x00);
    private static final int       ADDRESS_WAVE_TRANSFER_2      = SysExMessage.address (0x01, 0x00, 0x52);
    private static final int       ADDRESS_PERFORMANCE_TRANSFER = SysExMessage.address (0x01, 0x08, 0x00);

    private static final int       ADDRESS_FIRST_SAMPLE         = SysExMessage.address (0x02, 0x00, 0x00);
    private static final int       ADDRESS_LAST_SAMPLE_PACKET   = SysExMessage.address (0x11, 0x7F, 0x00);

    private static final int       RQ1                          = 0x11;
    private static final int       DT1                          = 0x12;
    private static final int       WSD                          = 0x40;
    private static final int       RQD                          = 0x41;
    private static final int       DAT                          = 0x42;

    /** Roland device ID byte found in the SysEx header. */
    public int                     deviceId                     = 0x00;

    /**
     * One entry per S-10 edit buffer: 0=A, 1=B, 2=C, 3=D. Entries are null if the corresponding
     * wave was not supplied.
     */
    public final WaveParameters [] waveParameters               = new WaveParameters [4];

    /** Performance parameters, or null if not supplied. */
    public PerformanceParameters   performanceParameters;

    /** Decoded sample-data regions. */
    public SampleBlock             sampleBlock                  = null;


    /**
     * Creates an empty instance.
     */
    private RolandS10SysEx ()
    {
        // Intentionally empty
    }


    /**
     * Reads a complete .syx file from disk and parses it into a Roland S-10 SysEx representation.
     *
     * @param file The path to the .syx file to read
     * @return A fully parsed representation of the file's contents
     * @throws IOException If the file cannot be read or the SysEx data is invalid
     */
    public static RolandS10SysEx read (final File file) throws IOException
    {
        return parse (Files.readAllBytes (file.toPath ()));
    }


    /**
     * Parses a complete SysEx stream.
     *
     * @param bytes Several raw system exclusive message bytes
     * @return A fully parsed representation of the given SysEx stream
     * @throws IOException If no Roland S-10 message is found, a message is malformed, or the
     *             sample/parameter data is inconsistent
     */
    public static RolandS10SysEx parse (final byte [] bytes) throws IOException
    {
        final List<SysExMessage> validatedMessages = validateMessages (SysExMessage.readSysExMessages (bytes));
        if (validatedMessages.isEmpty ())
            throw new IOException (Functions.getMessage ("IDS_S1X_SYSEX_NO_MESSAGES"));

        final SysExMessage sysExMessage = validatedMessages.get (0);
        if (sysExMessage.modelId != MODEL_S10_ID)
            throw new IOException (Functions.getMessage ("IDS_S1X_SYSEX_NOT_A_S10"));

        final RolandS10SysEx result = new RolandS10SysEx ();
        result.deviceId = sysExMessage.deviceId;

        final TreeMap<Integer, int []> samplePackets = new TreeMap<> ();
        int [] waveTransferPart1 = null;
        int [] waveTransferPart2 = null;
        int [] performanceTransfer = null;

        for (final SysExMessage message: validatedMessages)
        {
            if (isSamplePacketAddress (message.address))
            {
                if (message.data.length != SAMPLE_PACKET_BYTES)
                    throw new IOException (Functions.getMessage ("IDS_S1X_SYSEX_WRONG_SAMPLE_PACKET_LENGTH", formatAddress (message.address), Integer.toString (message.data.length)));
                if (samplePackets.put (Integer.valueOf (message.address), message.data) != null)
                    throw new IOException (Functions.getMessage ("IDS_S1X_SYSEX_DUPLICATE_PACKET", formatAddress (message.address)));
                continue;
            }

            if (message.expectedLength != message.expectedLength)
                throw new IOException (Functions.getMessage ("IDS_S1X_SYSEX_UNEXPECTED_PAYLOAD", formatAddress (message.address), Integer.toString (message.data.length)));

            if (message.address == ADDRESS_WAVE_TRANSFER_1)
            {
                waveTransferPart1 = message.data;
                continue;
            }

            if (message.address == ADDRESS_WAVE_TRANSFER_2)
            {
                waveTransferPart2 = message.data;
                continue;
            }

            if (message.address == ADDRESS_PERFORMANCE_TRANSFER)
            {
                performanceTransfer = message.data;
                continue;
            }
        }

        result.decodeSamplePackets (samplePackets);
        result.decodeWaveTransfer (waveTransferPart1, waveTransferPart2);
        result.splitSampleBanks ();

        if (performanceTransfer != null)
            result.performanceParameters = new PerformanceParameters (performanceTransfer);

        return result;
    }


    private void splitSampleBanks () throws IOException
    {
        // Check if the number of wave parameter blocks matches the sampling structure

        // First wave parameter block must always be present
        if (this.waveParameters[0] == null)
            throw new IOException (Functions.getMessage ("IDS_S1X_SYSEX_UNSOUND_NUMBER_OF_WAVE_PARAM_BLOCKS"));

        WaveParameters wave = this.waveParameters[0];

        // Structures with 1, 2 or 4 layers
        if (wave.samplingStructure <= 6)
        {
            if (this.waveParameters[1] != null)
                throw new IOException (Functions.getMessage ("IDS_S1X_SYSEX_UNSOUND_NUMBER_OF_WAVE_PARAM_BLOCKS"));
        }
        else if (wave.samplingStructure <= 9)
        {
            if (this.waveParameters[2] != null)
                throw new IOException (Functions.getMessage ("IDS_S1X_SYSEX_UNSOUND_NUMBER_OF_WAVE_PARAM_BLOCKS"));
        }
        else if (this.waveParameters[1] == null || this.waveParameters[2] == null || this.waveParameters[3] == null)
            throw new IOException (Functions.getMessage ("IDS_S1X_SYSEX_UNSOUND_NUMBER_OF_WAVE_PARAM_BLOCKS"));

        for (int i = 0; i < this.waveParameters.length; i++)
        {
            wave = this.waveParameters[i];
            if (wave == null)
                break;
            // Move start of first sample start to 0
            final int offset = 65536 + (wave.destinationBank - i) * 32768;
            final int start = wave.startAddress - offset;
            final int end = wave.manualEndAddress - offset;
            wave.waveData = Arrays.copyOfRange (this.sampleBlock.sampleWords, start, end);
        }
    }


    private static List<SysExMessage> validateMessages (final List<SysExMessage> sysExMessages) throws IOException
    {
        final List<SysExMessage> cleanedMessages = new ArrayList<> ();

        for (final SysExMessage message: sysExMessages)
        {
            if (message.command != DT1 && message.command != DAT && !commandHasAddress (message.command))
                continue;

            final int expectedLength = expectedPayloadLength (message.command, message.address, message.data.length);
            if (expectedLength < 0)
                message.data = removeOptionalChecksum (message.address, message.data);
            else if (message.data.length == expectedLength + 1)
            {
                final int suppliedChecksum = message.data[message.data.length - 1];
                message.data = Arrays.copyOf (message.data, expectedLength);
                final int expectedChecksum = SysExMessage.rolandChecksum (message.address, message.data);
                if (suppliedChecksum != expectedChecksum)
                    throw new IOException ("Invalid checksum at " + formatAddress (message.address) + ": expected " + String.format ("%02X", Integer.valueOf (expectedChecksum)) + ", got " + String.format ("%02X", Integer.valueOf (suppliedChecksum)));
            }
            else if (message.data.length != expectedLength)
                message.expectedLength = expectedLength;

            cleanedMessages.add (message);
        }

        return cleanedMessages;
    }


    /**
     * Determines whether a Roland command includes an address field.
     *
     * @param command The Roland command byte
     * @return True if the command is followed by a three-byte address, false otherwise
     */
    private static boolean commandHasAddress (final int command)
    {
        return command == RQ1 || command == DT1 || command == WSD || command == RQD || command == DAT;
    }


    /**
     * Removes a trailing checksum byte from a message tail if present and valid.
     *
     * @param address The 21-bit encoded Roland address the tail belongs to
     * @param tail The message bytes following the address, possibly including a trailing checksum
     *            byte
     * @return The tail bytes without a checksum if a valid trailing checksum was found, otherwise
     *         the unmodified tail
     */
    private static int [] removeOptionalChecksum (final int address, final int [] tail)
    {
        if (tail.length == 0)
            return tail;

        final int [] possibleData = Arrays.copyOf (tail, tail.length - 1);
        final int suppliedChecksum = tail[tail.length - 1];

        if (suppliedChecksum == SysExMessage.rolandChecksum (address, possibleData))
            return possibleData;

        return tail;
    }


    /**
     * Determines the expected payload length for a given command and address.
     *
     * @param command The Roland command byte
     * @param address The 21-bit encoded Roland address of the message
     * @param tailLength The actual number of bytes following the address in the message
     * @return The expected payload length in bytes, or -1 if it cannot be determined from the
     *         address alone
     */
    private static int expectedPayloadLength (final int command, final int address, final int tailLength)
    {
        if (command == RQ1 || command == WSD || command == RQD)
            return 3;

        if (command != DT1 && command != DAT)
            return -1;

        if (isSamplePacketAddress (address))
            return SAMPLE_PACKET_BYTES;

        if (address == ADDRESS_SAMPLE_DUMP_MODE)
            return 1;

        if (address == ADDRESS_PERFORMANCE_TRANSFER)
            return PERFORMANCE_PARAMETER_BYTES;

        if (address == ADDRESS_WAVE_TRANSFER_1)
        {
            if (tailLength == 73 || tailLength == 74)
                return 73;

            if (tailLength == 146 || tailLength == 147)
                return 146;
        }

        if (address == ADDRESS_WAVE_TRANSFER_2)
            if (tailLength == 146 || tailLength == 147)
                return 146;

        return -1;
    }


    /**
     * Parses a whitespace-separated hexadecimal SysEx excerpt.
     *
     * This is convenient for validating a textual dump fragment.
     *
     * @param hex A string of space-separated hexadecimal byte values
     * @return A fully parsed representation of the hexadecimal excerpt
     * @throws IOException If no Roland S-10 message is found or a message is malformed
     */
    public static RolandS10SysEx parseHexPrefix (final String hex) throws IOException
    {
        return parse (hexToBytes (hex));
    }


    /**
     * Decodes one S-10 12-bit sample word.
     *
     * @param byte0 The first transport byte (0aaa aaaa)
     * @param byte1 The second transport byte (0bbb bb00)
     * @return The decoded unsigned 12-bit sample word, in the range 0..4095
     */
    public static int decodeSampleWord (final int byte0, final int byte1)
    {
        SysExMessage.require7Bit (byte0, "sample byte 0");
        SysExMessage.require7Bit (byte1, "sample byte 1");

        if ((byte1 & 0x03) != 0)
            throw new IllegalArgumentException ("The low two bits of the second sample byte must be zero.");

        return (byte0 << 5) | (byte1 >>> 2);
    }


    /**
     * Decodes all collected sample packets into contiguous sample blocks.
     *
     * @param packets The sample packets, keyed by their start address and ordered by address
     * @throws IOException If a packet's decoded byte count is odd or otherwise inconsistent
     */
    private void decodeSamplePackets (final TreeMap<Integer, int []> packets) throws IOException
    {
        if (packets.isEmpty ())
            return;

        int blockStart = -1;
        int expectedAddress = -1;

        final List<Integer> transportBytes = new ArrayList<> ();
        for (final Map.Entry<Integer, int []> entry: packets.entrySet ())
        {
            final int address = entry.getKey ().intValue ();
            final int [] packet = entry.getValue ();

            if (blockStart < 0 || address != expectedAddress)
            {
                this.addSampleBlock (blockStart, transportBytes);

                blockStart = address;
                transportBytes.clear ();
            }

            for (final int value: packet)
                transportBytes.add (Integer.valueOf (value));

            expectedAddress = address + SAMPLE_PACKET_BYTES;
        }

        this.addSampleBlock (blockStart, transportBytes);
    }


    /**
     * Decodes buffered transport bytes into a sample block and adds it to the result.
     *
     * @param startAddress The address of the first packet in the block, or a negative value if
     *            there is nothing to add
     * @param transportBytesdata The buffered two-byte-per-word transport bytes for the block
     * @throws IOException If the number of buffered bytes is odd
     */
    private void addSampleBlock (final int startAddress, final List<Integer> transportBytesdata) throws IOException
    {
        if (this.sampleBlock != null)
            throw new IOException ("Found 2nd sample block. Only 1 is supported.");

        if (startAddress < 0 || transportBytesdata.isEmpty ())
            return;

        final int length = transportBytesdata.size ();
        if ((length & 1) != 0)
            throw new IOException ("Odd sample byte count at address " + formatAddress (startAddress));

        final int [] words = new int [length / 2];
        for (int i = 0; i < words.length; i++)
        {
            final int pos = i * 2;
            words[i] = decodeSampleWord (transportBytesdata.get (pos).intValue (), transportBytesdata.get (pos + 1).intValue ());
        }

        this.sampleBlock = new SampleBlock (startAddress, words);
    }


    /**
     * Decodes the wave parameter transfer messages into individual edit buffers.
     *
     * @param part1 The bytes of the first wave transfer message (address 01 00 00), or null if not
     *            present
     * @param part2 The bytes of the second wave transfer message (address 01 00 52), or null if not
     *            present
     * @throws IOException If either part has an invalid length or too many wave blocks are supplied
     */
    private void decodeWaveTransfer (final int [] part1, final int [] part2) throws IOException
    {
        int editBuffer = 0;
        if (part1 != null)
            editBuffer = WaveParameters.decodeWaveTransferPart (this.waveParameters, part1, editBuffer);
        if (part2 != null)
            editBuffer = WaveParameters.decodeWaveTransferPart (this.waveParameters, part2, editBuffer);
    }


    /**
     * Determines whether an address falls within the sample data range.
     *
     * @param address The 21-bit encoded Roland address to check
     * @return True if the address lies within the sample packet address range, false otherwise
     */
    private static boolean isSamplePacketAddress (final int address)
    {
        return address >= ADDRESS_FIRST_SAMPLE && address <= ADDRESS_LAST_SAMPLE_PACKET;
    }


    /**
     * Converts a whitespace-separated hexadecimal string into a byte array.
     *
     * @param hex The string of space-separated hexadecimal byte values
     * @return The decoded byte array
     */
    private static byte [] hexToBytes (final String hex)
    {
        final String trimmed = hex.trim ();

        if (trimmed.isEmpty ())
            return new byte [0];

        final String [] tokens = trimmed.split ("\\s+");
        final byte [] result = new byte [tokens.length];

        for (int i = 0; i < tokens.length; i++)
        {
            if (!tokens[i].matches ("[0-9A-Fa-f]{2}"))
                throw new IllegalArgumentException ("Invalid hexadecimal byte: " + tokens[i]);

            result[i] = (byte) Integer.parseInt (tokens[i], 16);
        }

        return result;
    }


    /**
     * Formats an encoded address as a human-readable three-byte hexadecimal string.
     *
     * @param address The 21-bit encoded Roland address
     * @return The address formatted as three space-separated hexadecimal byte values
     */
    private static String formatAddress (final int address)
    {
        return String.format ("%02X %02X %02X", Integer.valueOf (SysExMessage.addressByte0 (address)), Integer.valueOf (SysExMessage.addressByte1 (address)), Integer.valueOf (SysExMessage.addressByte2 (address)));
    }
}