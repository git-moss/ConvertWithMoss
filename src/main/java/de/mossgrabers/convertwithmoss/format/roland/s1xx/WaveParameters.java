// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s1xx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import de.mossgrabers.tools.ui.Functions;


/**
 * One 73-byte S-10 wave parameter block.
 *
 * The first 56 bytes are documented by the S-10 specification. The remaining 17 bytes are retained
 * unchanged for round-tripping.
 *
 * @author Jürgen Moßgraber
 */
public class WaveParameters
{
    /** Total size in bytes of one wave parameter block. */
    public static final int WAVE_PARAMETER_BYTES = 73;

    /** Voice Name. 9 ASCII characters. */
    String                  voiceName            = "         ";

    /**
     * The sampling structure.
     * 
     * <ul>
     * <li>0 = A
     * <li>1 = B
     * <li>2 = C
     * <li>3 = D
     * <li>4 = AB
     * <li>5 = CD
     * <li>6 = ABCD = One long sample
     * <li>7 = A/B = Double sample length as 2 layers
     * <li>8 = C/D
     * <li>9 = AB/CD = Double length in lower/upper with split-point
     * <li>A = A/B/C/D = 4 ranges with 3 split-points
     * <li>F = All Structures (Not separated)
     * </ul>
     */
    int                     samplingStructure;
    /** The destination bank. */
    int                     destinationBank;

    /** Is the bender enabled? */
    boolean                 enableBender;
    /** Is key-follow enabled? */
    boolean                 enableKeyFollow;
    /** Is vibrato enabled (both manual and auto)? */
    boolean                 enableVibrato;
    /** The sampling rate: 0 = 30kHz, 1 = 15kHz */
    int                     samplingRate;

    /** Loop Type. 0 (1 Shot), 1 (Man), 2 (Auto). */
    int                     loopMode;
    /** Scan (play) Mode. 0 (Forward), 1 (Alternate), 2 (Backward). */
    int                     scanMode;

    /** Record Key. Range 24 (c1) to 103 (g7). */
    int                     recordKey;

    /** Bank Tune. Range -50 to +50. */
    int                     bankTune;

    /** Loop Tune. Range -50 to +50. */
    int                     loopTune;
    /** Only on S-220 */
    int                     loopTune2;

    /** Start point, range 0..0xFFFFF. */
    int                     startAddress;
    /** End point, range 0..0xFFFFF. */
    int                     manualEndAddress;
    /** Loop point, range 0..0xFFFFF. */
    int                     manualLoopLength;
    /** Auto End point, range 0..0xFFFFF. */
    int                     autoLoopLength;
    /** Auto Loop point, range 0..0xFFFFF. */
    int                     autoEndAddress;

    /** Env V-Sens (envelope velocity sensitivity). Range 0-255. */
    int                     envelopeVelocitySensitivity;
    /** Env Rate 1. Range 0-255. */
    int                     envelopeRate1;
    /** Env Level 1. Range 0-255. */
    int                     envelopeLevel1;
    /** Env Rate 2 - ignored when L1==L2! Range 0-255. */
    int                     envelopeRate2;
    /** Env Level 2. Range 0-255. */
    int                     envelopeLevel2;
    /** Env Rate 3 - ignored when L2==L3! Range 0-255. */
    int                     envelopeRate3;
    /** Env Level 3. Range 0-255. */
    int                     envelopeLevel3;
    /** Env Rate 4. Range 0-255. */
    int                     envelopeRate4;

    /** Dynamic Sense (= velocity). Range 0-255. */
    int                     dynamicSensitivity;
    /** Auto Bend Rate. Range 0-255. */
    int                     autoBendRate;
    /** Auto Bend Depth. Range 0-255. */
    int                     autoBendDepth;

    /** Split 1. Range 24 (c1) to 103 (g7). */
    int                     split1;
    /** Split 2. Range 24 (c1) to 103 (g7). */
    int                     split2;
    /** Split 3. Range 24 (c1) to 103 (g7). */
    int                     split3;

    /** True if it is S-220 data otherwise S-10/MKS-100 */
    boolean                 isS220               = false;

    /** The extracted wave data. */
    int []                  waveData             = null;


    /**
     * Creates a wave parameter block from a full 73-byte transport array.
     *
     * @param bytes The 73 raw wave parameter bytes
     */
    public WaveParameters (final int [] bytes)
    {
        if (bytes.length != WAVE_PARAMETER_BYTES)
            throw new IllegalArgumentException (Functions.getMessage ("IDS_S1X_SYSEX_WRONG_WAVE_PARAMETER_LENGTH", Integer.toString (WAVE_PARAMETER_BYTES)));
        this.decodeFields (bytes);
    }


    /**
     * Decodes one wave transfer message into one or more edit buffers.
     *
     * @param waveParameters The array of edit buffers to populate; its length limits the number of
     *            blocks that can be decoded
     * @param bytes The raw bytes of the wave transfer message; its length must be a multiple of 73
     *            bytes
     * @param editBuffer The index of the next edit buffer to populate
     * @return The index of the next unpopulated edit buffer after this part
     * @throws IOException If the byte count is not a multiple of 73 or too many wave blocks are
     *             supplied
     */
    public static int decodeWaveTransferPart (final WaveParameters [] waveParameters, final int [] bytes, final int editBuffer) throws IOException
    {
        if (bytes.length % WAVE_PARAMETER_BYTES != 0)
            throw new IOException (Functions.getMessage ("IDS_S1X_SYSEX_WAVE_TRANSFER_WRONG_LENGTH", Integer.toString (bytes.length)));

        int pos = editBuffer;
        for (int offset = 0; offset < bytes.length; offset += WAVE_PARAMETER_BYTES)
        {
            if (pos >= waveParameters.length)
                throw new IOException (Functions.getMessage ("IDS_S1X_SYSEX_TOO_MANY_WAVE_BLOCKS"));
            waveParameters[pos] = new WaveParameters (Arrays.copyOfRange (bytes, offset, offset + WAVE_PARAMETER_BYTES));
            pos++;
        }
        return pos;
    }


    private void decodeFields (final int [] bytes)
    {
        final byte [] name = new byte [9];
        for (int i = 0; i < name.length; i++)
            name[i] = (byte) bytes[i];
        this.voiceName = new String (name, StandardCharsets.US_ASCII).trim ();

        this.samplingStructure = SysExMessage.decodeNibble (bytes, 9);
        this.destinationBank = SysExMessage.decodeNibble (bytes, 10);

        this.enableBender = (bytes[11] & 8) > 0;
        this.enableKeyFollow = (bytes[11] & 4) > 0;
        this.enableVibrato = (bytes[11] & 2) > 0;
        this.samplingRate = bytes[11] & 1;

        this.loopMode = (bytes[12] >> 2) & 3;
        this.scanMode = bytes[12] & 3;

        this.recordKey = SysExMessage.decodeTwoNibbles (bytes, 13);

        // Only on S-220
        this.loopTune2 = SysExMessage.decodeTwoNibbles (bytes, 15);

        this.startAddress = SysExMessage.decodeFourNibbles (bytes, 17);
        this.manualLoopLength = SysExMessage.decodeFourNibbles (bytes, 21);
        this.manualEndAddress = SysExMessage.decodeFourNibbles (bytes, 25);
        this.autoLoopLength = SysExMessage.decodeFourNibbles (bytes, 29);
        this.autoEndAddress = SysExMessage.decodeFourNibbles (bytes, 33);

        final int uu = (bytes[37] >> 2) & 3;
        final int vv = bytes[37] & 3;
        final int ww = (bytes[38] >> 2) & 3;

        this.isS220 = (bytes[38] & 2) > 0;

        // Note: bytes[39] contains a velocity switch value on S-220

        final int xx = (bytes[40] >> 2) & 3;
        final int yy = bytes[40] & 3;
        this.startAddress = (ww << 16) + this.startAddress;
        this.manualLoopLength = (uu << 16) + this.manualLoopLength;
        this.manualEndAddress = (vv << 16) + this.manualEndAddress;
        this.autoLoopLength = (xx << 16) + this.autoLoopLength;
        this.autoEndAddress = (yy << 16) + this.autoEndAddress;

        // Convert both to signed
        this.bankTune = (byte) SysExMessage.decodeTwoNibbles (bytes, 41);
        this.loopTune = (byte) SysExMessage.decodeTwoNibbles (bytes, 43);

        this.envelopeVelocitySensitivity = SysExMessage.decodeTwoNibbles (bytes, 45);

        this.envelopeRate1 = SysExMessage.decodeTwoNibbles (bytes, 47);
        this.envelopeRate2 = SysExMessage.decodeTwoNibbles (bytes, 49);
        this.envelopeRate3 = SysExMessage.decodeTwoNibbles (bytes, 51);
        this.envelopeRate4 = SysExMessage.decodeTwoNibbles (bytes, 53);

        this.envelopeLevel1 = SysExMessage.decodeTwoNibbles (bytes, 55);
        this.envelopeLevel2 = SysExMessage.decodeTwoNibbles (bytes, 57);
        this.envelopeLevel3 = SysExMessage.decodeTwoNibbles (bytes, 59);

        this.split1 = SysExMessage.decodeTwoNibbles (bytes, 61);
        this.split2 = SysExMessage.decodeTwoNibbles (bytes, 63);
        this.split3 = SysExMessage.decodeTwoNibbles (bytes, 65);

        this.dynamicSensitivity = SysExMessage.decodeTwoNibbles (bytes, 67);
        this.autoBendRate = SysExMessage.decodeTwoNibbles (bytes, 69);
        this.autoBendDepth = SysExMessage.decodeTwoNibbles (bytes, 71);
    }


    /**
     * Converts the unsigned 12-bit sample words of one sample block into signed, little-endian PCM
     * bytes, ready to be written into the data chunk of a WAV file. The original 12-bit resolution
     * is preserved (range -2048..2047); the values are only sign-extended into 16-bit containers
     * for WAV compatibility, not scaled up.
     *
     * @return The PCM bytes, two bytes (little-endian) per sample word
     */
    public byte [] toWavBytes ()
    {
        final ByteBuffer buffer = ByteBuffer.allocate (this.waveData.length * 2).order (ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < this.waveData.length; i++)
        {
            // 0..4095
            int raw = this.waveData[i];
            // 12-bit two's complement
            int signed12 = (raw < 2048) ? raw : raw - 4096;
            // Scale to 16-bit
            int signed16 = signed12 << 4;
            buffer.putShort ((short) signed16);
        }

        return buffer.array ();
    }
}