// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ensoniq.mirage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.tools.ui.Functions;


/**
 * Access to an Ensoniq Mirage 440 KB floppy disk image.
 *
 * <pre>
 * Tracks:            80
 * Sectors per track: 6 (5 sectors with 1024 bytes (large sectors) and 1 sector with 512 bytes (small sector))
 * Bytes per track:   5632
 * Total size:        450,560 bytes = 440kB
 * </pre>
 *
 * The disk may contain the Operating System, six sounds configured as 3 lower-half keyboard sounds
 * and 3 upper-half keyboard sounds and either eight short sequences or three long sequences. The
 * first 11K of the Operating System is stored on both small and large sectors from Track 0, Sector
 * 0, to Track 1, Sector 5. The remaining 5k of the Operating System is stored only on small sectors
 * (Sector 5) from Track 2 to Track 10. The configuration parameters are stored on Track 11, Sector
 * 5. The directory and the sequences are only stored on the small sectors (Sector 5) and the sound
 * files are only stored on the large sectors (Sectors 0-4). Each sound is 64kB.
 *
 * <pre>
 * TK SC
 * 2  0 Sound # 1, Lower Half, Parameters (1 Sector)
 * 2  1 Sound # 1, Lower Half, Data (64 Sectors)
 * 15 0 Sound # 1, Upper Half, Parameters (1 Sector)
 * 15 1 Sound # 1, Upper Half, Data (64 Sectors)
 * 28 0 Sound # 2, Lower Half, Parameters (1 Sector)
 * 28 1 Sound # 2, Lower Half, Data (64 Sectors)
 * 41 0 Sound # 2, Upper Half, Parameters (1 Sector)
 * 41 1 Sound # 2, Upper Half, Data (64 Sectors)
 * 54 0 Sound # 3, Lower Half, Parameters (1 Sector)
 * 54 1 Sound # 3, Lower Half, Data (64 Sectors)
 * 67 0 Sound # 3, Upper Half, Parameters (1 Sector)
 * 67 1 Sound # 3, Upper Half, Data (64 Sectors)
 * 20 5 Short Sequence # 1 (4 Sectors)
 * 35 5 Short Sequence # 2 (4 Sectors)
 * 55 5 Short Sequence # 3 (4 Sectors)
 * 24 5 Short Sequence # 4 (4 Sectors)
 * 28 5 Short Sequence # 5 (4 Sectors)
 * 39 5 Short Sequence # 6 (4 Sectors)
 * 43 5 Short Sequence # 7 (4 Sectors)
 * 59 5 Short Sequence # 8 (4 Sectors)
 * 12 5 Long Sequence # 1 (16 Sectors)
 * 35 5 Long Sequence # 2 (16 Sectors)
 * 55 5 Long Sequence # 3 (16 Sectors)
 * </pre>
 *
 * @author Jürgen Moßgraber
 */
public class MirageFile
{
    /** Size of full disk image. */
    public static final int                   FILE_LENGTH            = 450560;

    // 5 Sectors of 1024 byte + 1 Sector of 512 byte
    private static final int                  TRACK_LENGTH           = 5632;
    // 5 Sectors of 1024 byte
    private static final int                  SOUND_SIZE_ON_TRACK    = 5120;
    /** 65 large sectors (1 sector parameters + 64 sector sample data). */
    private static final int                  SOUND_SIZE             = 66560;

    private static final byte []              MAGIC_MIRAGE_OS        = new byte []
    {
        0x00,
        0x00,
        0x00,
        0x00
    };

    private static final byte []              MAGIC_SOUND_PROCESS_OS = new byte []
    {
        (byte) 0x88,
        0x09,
        0x0B,
        0x05
    };

    private static final Map<String, Integer> NAMED_OFFSETS          = new LinkedHashMap<> ();
    private static final double []            SAMPLE_RATES           = new double [100];
    static
    {
        NAMED_OFFSETS.put ("-01-Lower-", Integer.valueOf (2 * TRACK_LENGTH));
        NAMED_OFFSETS.put ("-01-Upper-", Integer.valueOf (15 * TRACK_LENGTH));
        NAMED_OFFSETS.put ("-02-Lower-", Integer.valueOf (28 * TRACK_LENGTH));
        NAMED_OFFSETS.put ("-02-Upper-", Integer.valueOf (41 * TRACK_LENGTH));
        NAMED_OFFSETS.put ("-03-Lower-", Integer.valueOf (54 * TRACK_LENGTH));
        NAMED_OFFSETS.put ("-03-Upper-", Integer.valueOf (67 * TRACK_LENGTH));

        SAMPLE_RATES[20] = 50000.00;
        SAMPLE_RATES[21] = 47619.05;
        SAMPLE_RATES[22] = 45454.55;
        SAMPLE_RATES[23] = 43478.26;
        SAMPLE_RATES[24] = 41666.67;
        SAMPLE_RATES[25] = 40000.00;
        SAMPLE_RATES[26] = 38461.54;
        SAMPLE_RATES[27] = 37037.04;
        SAMPLE_RATES[28] = 35714.29;
        SAMPLE_RATES[29] = 34482.76;
        SAMPLE_RATES[30] = 33333.33;
        SAMPLE_RATES[31] = 32258.07;
        SAMPLE_RATES[32] = 31250.00;
        SAMPLE_RATES[33] = 30303.03;
        SAMPLE_RATES[34] = 29411.77;
        SAMPLE_RATES[35] = 28571.43;
        SAMPLE_RATES[36] = 27777.78;
        SAMPLE_RATES[37] = 27027.03;
        SAMPLE_RATES[38] = 26315.79;
        SAMPLE_RATES[39] = 25641.03;
        SAMPLE_RATES[40] = 25000.00;
        SAMPLE_RATES[41] = 24390.25;
        SAMPLE_RATES[42] = 23809.52;
        SAMPLE_RATES[43] = 23255.82;
        SAMPLE_RATES[44] = 22727.27;
        SAMPLE_RATES[45] = 22222.22;
        SAMPLE_RATES[46] = 21739.13;
        SAMPLE_RATES[47] = 21276.60;
        SAMPLE_RATES[48] = 20833.33;
        SAMPLE_RATES[49] = 20408.16;
        SAMPLE_RATES[50] = 20000.00;
        SAMPLE_RATES[51] = 19607.84;
        SAMPLE_RATES[52] = 19230.77;
        SAMPLE_RATES[53] = 18867.92;
        SAMPLE_RATES[54] = 18518.52;
        SAMPLE_RATES[55] = 18181.82;
        SAMPLE_RATES[56] = 17857.14;
        SAMPLE_RATES[57] = 17543.86;
        SAMPLE_RATES[58] = 17241.38;
        SAMPLE_RATES[59] = 16949.15;
        SAMPLE_RATES[60] = 16666.67;
        SAMPLE_RATES[61] = 16393.44;
        SAMPLE_RATES[62] = 16129.03;
        SAMPLE_RATES[63] = 15873.02;
        SAMPLE_RATES[64] = 15625.00;
        SAMPLE_RATES[65] = 15384.62;
        SAMPLE_RATES[66] = 15151.52;
        SAMPLE_RATES[67] = 14925.37;
        SAMPLE_RATES[68] = 14705.88;
        SAMPLE_RATES[69] = 14492.75;
        SAMPLE_RATES[70] = 14285.71;
        SAMPLE_RATES[71] = 14084.51;
        SAMPLE_RATES[72] = 13888.89;
        SAMPLE_RATES[73] = 13698.63;
        SAMPLE_RATES[74] = 13513.51;
        SAMPLE_RATES[75] = 13333.33;
        SAMPLE_RATES[76] = 13157.90;
        SAMPLE_RATES[77] = 12987.01;
        SAMPLE_RATES[78] = 12820.51;
        SAMPLE_RATES[79] = 12658.23;
        SAMPLE_RATES[80] = 12500.00;
        SAMPLE_RATES[81] = 12345.68;
        SAMPLE_RATES[82] = 12195.12;
        SAMPLE_RATES[83] = 12048.19;
        SAMPLE_RATES[84] = 11904.76;
        SAMPLE_RATES[85] = 11764.71;
        SAMPLE_RATES[86] = 11627.91;
        SAMPLE_RATES[87] = 11494.25;
        SAMPLE_RATES[88] = 11363.64;
        SAMPLE_RATES[89] = 11235.96;
        SAMPLE_RATES[90] = 11111.11;
        SAMPLE_RATES[91] = 10989.01;
        SAMPLE_RATES[92] = 10869.57;
        SAMPLE_RATES[93] = 10752.69;
        SAMPLE_RATES[94] = 10638.30;
        SAMPLE_RATES[95] = 10526.32;
        SAMPLE_RATES[96] = 10416.67;
        SAMPLE_RATES[97] = 10309.28;
        SAMPLE_RATES[98] = 10204.08;
        SAMPLE_RATES[99] = 10101.01;
    }

    /** The 6 layers (3 x lower/upper). */
    final List<MirageLayer> layers = new ArrayList<> (6);

    /** Parameter #22. Pitch bend range: range 0..12, default: 2. **/
    int                     pitchBendRange;
    /** Parameter #73. Sample time adjust. Range: 20..99. Default 34 (0x22). **/
    int                     sampleTimeAdjust;


    /**
     * Read the 6 sound layers (3 lower & 3 upper) from the disk. Each layer contains a set of four
     * Programs and eight wave-samples. There is always an upper and a lower layer present per
     * sound. For each layer:
     *
     * <ol>
     * <li>Seek to track start offset</li>
     * <li>Read 13 consecutive tracks (73,216 bytes)</li>
     * <li>Remove last sector (512 bytes) of each track</li>
     * <li>Concatenate remaining 5120-byte segments</li>
     * <li>Discard first 1024 bytes (parameter block)</li>
     * <li>Result = 65,536 bytes waveform PCM</li>
     * </ol>
     * 
     * @param sourceFile The file to read
     * @throws IOException Could not read the file
     */
    public MirageFile (final File sourceFile) throws IOException
    {
        this (sourceFile.getName (), Files.readAllBytes (sourceFile.toPath ()));
    }


    /**
     * Constructor.
     * 
     * @param nameStub The name of the source file
     * @param mirageData The data from the file
     * @throws IOException Could not read the data
     */
    public MirageFile (final String nameStub, final byte [] mirageData) throws IOException
    {
        if (mirageData.length != FILE_LENGTH)
            throw new IOException (Functions.getMessage ("IDS_MIRAGE_NOT_A_MIRAGE_DISK1", Integer.toString (mirageData.length)));

        if (Arrays.compare (mirageData, 0, 4, MAGIC_MIRAGE_OS, 0, 4) != 0)
        {
            if (Arrays.compare (mirageData, 0, 4, MAGIC_SOUND_PROCESS_OS, 0, 4) == 0)
                throw new IOException (Functions.getMessage ("IDS_MIRAGE_SOUND_PROCESS_OS_NOT_SUPPORTED", Integer.toString (mirageData.length)));
            throw new IOException (Functions.getMessage ("IDS_MIRAGE_NOT_A_MIRAGE_DISK2"));
        }

        for (final Map.Entry<String, Integer> entry: NAMED_OFFSETS.entrySet ())
        {
            final int offset = entry.getValue ().intValue ();
            final byte [] soundData = new byte [SOUND_SIZE];
            for (int start = 0; start < 13; start++)
                System.arraycopy (mirageData, offset + start * TRACK_LENGTH, soundData, start * SOUND_SIZE_ON_TRACK, SOUND_SIZE_ON_TRACK);
            this.layers.add (new MirageLayer (nameStub + entry.getKey (), soundData));
        }

        this.readConfigurationBlock (mirageData);
    }


    /**
     * Get the global sample rate.
     * 
     * @return The sample rate in Hertz
     */
    public int getSampleRate ()
    {
        final int index = this.sampleTimeAdjust < 20 || this.sampleTimeAdjust > 99 ? 34 : this.sampleTimeAdjust;
        return (int) Math.round (SAMPLE_RATES[index]);
    }


    /**
     * Reads the global settings which are stored in Track 12 Sector 5.
     * 
     * @param mirageData The full disk image data.
     * @throws IOException Could not read the settings block
     */
    private void readConfigurationBlock (final byte [] mirageData) throws IOException
    {
        // Track 12 Sector 5: 11 * 5632 + 5120 = 0x10600
        try (final ByteArrayInputStream input = new ByteArrayInputStream (mirageData, 0x10600, 256))
        {
            input.skipNBytes (1);

            //
            // General settings

            // Parameter #21. Master tune. Range: 0..99, 50 (0x32) nominal — a440
            @SuppressWarnings("unused")
            int masterTune = input.read ();
            // Parameter #22. Pitch bend range: range 0..12, default: 2
            this.pitchBendRange = input.read ();
            // Parameter #23. Keyboard velocity sensitivity. Default 30 (0x1E)
            @SuppressWarnings("unused")
            int keyboardVelocitySensitivity = input.read ();
            // Parameter #24. Keyboard balance - counts by twos. Default 32 (0x40)
            @SuppressWarnings("unused")
            int keyboardBalance = input.read ();
            // Parameter #25. Upper/lower link (on/off)
            @SuppressWarnings("unused")
            int upperLowerLink = input.read ();

            //
            // Sampling configuration settings

            // Parameter #73. Sample time adjust. Range: 20..99. Default 34 (0x22)
            this.sampleTimeAdjust = input.read ();
            // Parameter #74. Input filter frequency. Range: 00..99. Default 80. Hex is double the
            // decimal value, e.g. 80 = 0xA0
            @SuppressWarnings("unused")
            int inputFilterFrequency = input.read ();
            // Parameter #75. Line/microphone level input: on/off
            @SuppressWarnings("unused")
            int lineMicLevelInput = input.read ();
            // Parameter #76. Sampling threshold: Range: 00..63. Default 48 (0x30)
            @SuppressWarnings("unused")
            int samplingThreshold = input.read ();
            // Parameter #77. User multi-sampling: on/off
            @SuppressWarnings("unused")
            int userMultisampling = input.read ();

            input.skipNBytes (4);

            //
            // MIDI settings

            // Parameter #81. MIDI OMNI (on/off)
            @SuppressWarnings("unused")
            int midiOmni = input.read ();
            // Parameter #82. MIDI channel (1-16) [hex is $00~$0f, offset by 1]
            @SuppressWarnings("unused")
            int midiChannel = input.read ();
            // Parameter #83. MIDI thru (on/off)
            @SuppressWarnings("unused")
            int midiThru = input.read ();
            // Parameter #84. MIDI function enable (0-3)
            @SuppressWarnings("unused")
            int midiFunctionEnable = input.read ();
            // Parameter #85. MIDI external clock (on/off)
            @SuppressWarnings("unused")
            int midiExternalClock = input.read ();
            // Parameter #86. External clock jack (on/off)
            @SuppressWarnings("unused")
            int externalClockJack = input.read ();
            // Parameter #87. Internal clock rate (default 96) [counts by twos]
            @SuppressWarnings("unused")
            int internalClockRate = input.read ();
        }
    }
}
