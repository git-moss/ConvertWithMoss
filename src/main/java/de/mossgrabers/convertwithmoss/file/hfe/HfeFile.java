// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.hfe;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * The HFE file format is a simple floppy bit-stream tracks container originally designed for the SD
 * HxC Floppy Emulator hardwares. It stores the floppy media content at the bit-cell level. HFE is
 * the acronym of HxC Floppy Emulator. The HxC Floppy emulators are a series of universal floppy
 * emulators. This format was originally designed for these emulators. See https://hxc2001.com/. The
 * HFE file format contains a file header with metadata like the number of tracks in the file, track
 * format ID, floppy interface configuration… The second part is a tracks offsets and sizes array.
 * And finally all the tracks bit-stream buffers.
 *
 * @author Jürgen Moßgraber
 */
public class HfeFile
{
    /** IBM PC Double Density floppy mode. */
    public static final int     FLOPPYMODE_IBM_PC_DD           = 0x00;
    /** IBM PC High Density floppy mode. */
    public static final int     FLOPPYMODE_IBM_PC_HD           = 0x01;
    /** Atari ST Double Density floppy mode. */
    public static final int     FLOPPYMODE_ATARI_ST_DD         = 0x02;
    /** Atari ST High Density floppy mode. */
    public static final int     FLOPPYMODE_ATARI_ST_HD         = 0x03;
    /** Commodore Amiga Double Density floppy mode. */
    public static final int     FLOPPYMODE_AMIGA_DD            = 0x04;
    /** Commodore Amiga High Density floppy mode. */
    public static final int     FLOPPYMODE_AMIGA_HD            = 0x05;
    /** Schneider CPC Double Density floppy mode. */
    public static final int     FLOPPYMODE_CPC_DD              = 0x06;
    /** Generic Shuggart Double Density floppy mode. */
    public static final int     FLOPPYMODE_GENERIC_SHUGGART_DD = 0x07;
    /** IBM PC ED floppy mode. */
    public static final int     FLOPPYMODE_IBM_PC_ED           = 0x08;
    /** MSX2 double density floppy mode. */
    public static final int     FLOPPYMODE_MSX2_DD             = 0x09;
    /** Commodore C64 double density floppy mode. */
    public static final int     FLOPPYMODE_C64_DD              = 0x0A;
    /** E-MU floppy mode. */
    public static final int     FLOPPYMODE_EMU_SHUGART         = 0x0B;
    /** Akai S950 double density floppy mode. */
    public static final int     FLOPPYMODE_S950_DD             = 0x0C;
    /** Akai S950 high density floppy mode. */
    public static final int     FLOPPYMODE_S950_HD             = 0x0D;
    /** Disabled floppy mode. */
    public static final int     FLOPPYMODE_DISABLE             = 0xFE;

    /** ISO IBM MFM Encoding. */
    public static final int     ENCODING_ISOIBM_MFM            = 0x00;
    /** Commodore Amiga MFM Encoding. */
    public static final int     ENCODING_AMIGA_MFM             = 0x01;
    /** ISO IBM FM Encoding. */
    public static final int     ENCODING_ISOIBM_FM             = 0x02;
    /** EMU FM Encoding. */
    public static final int     ENCODING_EMU_FM                = 0x03;
    /** Unknown Encoding. */
    public static final int     ENCODING_UNKNOWN               = 0xFF;

    private static final String SIGNATURE_V1_V2                = "HXCPICFE";
    private static final String SIGNATURE_V3                   = "HXCHFEV3";

    private static final int    BLOCK_SIZE                     = 512;
    private static final int    TRACK_BLOCK_SIZE               = 256;


    /** The HFE versions. */
    public enum HfeVersion
    {
        /** HFE 1. */
        VERSION_1,
        /** HFE 2. */
        VERSION_2,
        /** HFE 3. */
        VERSION_3
    }


    private int             formatRevision;
    private int             numTracks;
    private int             numSides;
    private int             trackEncoding;
    private int             bitrate;
    private int             floppyRotationPerMinute;
    private int             floppyInterfaceMode;
    private int             trackListOffset;

    // v1.1 parameters

    @SuppressWarnings("unused")
    private int             writeAllowed;
    @SuppressWarnings("unused")
    private int             singleStep;
    @SuppressWarnings("unused")
    private int             track0s0Altencoding;
    @SuppressWarnings("unused")
    private int             track0s0Encoding;
    @SuppressWarnings("unused")
    private int             track0s1Altencoding;
    @SuppressWarnings("unused")
    private int             track0s1Encoding;

    private HfeVersion      hfeVersion;
    private TrackData [] [] tracks;


    /**
     * Constructor.
     *
     * @param file The file to load
     * @throws IOException Could not read the file
     */
    public HfeFile (final File file) throws IOException
    {
        try (final RandomAccessFile randomAccessFile = new RandomAccessFile (file, "r"))
        {
            this.readHeader (randomAccessFile);
            this.readTracks (randomAccessFile);
        }
    }


    /**
     * Decodes all sectors from all tracks which are MFM encoded.
     * 
     * @return The decoded sectors
     */
    public List<Sector> decodeMfmSectors ()
    {
        final MfmDecoder decoder = new MfmDecoder ();
        final List<Sector> allSectors = new ArrayList<> ();
        for (int track = 0; track < this.getNumTracks (); track++)
            for (int side = 0; side < this.getNumSides (); side++)
                allSectors.addAll (decoder.decodeSectors (this.getTrack (side, track), track, side));
        return allSectors;
    }


    /**
     * Read the 512 byte header.
     *
     * @param randomAccessFile The file to read from
     * @throws IOException Could not read
     */
    private void readHeader (final RandomAccessFile randomAccessFile) throws IOException
    {
        final byte [] headerBytes = new byte [BLOCK_SIZE];
        randomAccessFile.seek (0);
        randomAccessFile.readFully (headerBytes);

        final ByteBuffer bb = ByteBuffer.wrap (headerBytes);
        bb.order (ByteOrder.LITTLE_ENDIAN);

        final byte [] sig = new byte [8];
        bb.get (sig);

        final String signature = new String (sig);
        if (!SIGNATURE_V1_V2.equals (signature) && !SIGNATURE_V3.equals (signature))
            throw new IOException ("Invalid HFE signature (this is not a HFE file or unknown signature): " + signature);

        this.formatRevision = Byte.toUnsignedInt (bb.get ());
        this.numTracks = Byte.toUnsignedInt (bb.get ());
        this.numSides = Byte.toUnsignedInt (bb.get ());
        this.trackEncoding = Byte.toUnsignedInt (bb.get ());
        this.bitrate = Short.toUnsignedInt (bb.getShort ());
        this.floppyRotationPerMinute = Short.toUnsignedInt (bb.getShort ());
        this.floppyInterfaceMode = Byte.toUnsignedInt (bb.get ());

        // Reserved
        bb.get ();

        // Offset of the track list LUT (= Lookup Tables) in block of 512 bytes (= 0x200), e.g. 1
        // means offset 0x200, 2 means 0x400, ...
        this.trackListOffset = Short.toUnsignedInt (bb.getShort ());

        // 0x00 : Write protected, 0xFF: Unprotected
        this.writeAllowed = Byte.toUnsignedInt (bb.get ());

        // v1.1 addition – Set to 0xFF if unused

        // 0xFF Single Step – 0x00 Double Step mode
        this.singleStep = Byte.toUnsignedInt (bb.get ());

        // If track0s0 alternate encoding is set to 0xFF, track0s0_encoding is ignored and
        // track_encoding is used for track 0 side 0.
        // If track0s1 alternate encoding is set to 0xFF, track0s1_encoding is ignored and
        // track_encoding is used for track 0 side 1.

        // 0x00 : Use an alternate track_encoding for track 0 Side 0
        this.track0s0Altencoding = Byte.toUnsignedInt (bb.get ());
        // Alternate track_encoding for track 0 Side 0
        this.track0s0Encoding = Byte.toUnsignedInt (bb.get ());
        // 0x00 : Use an alternate track_encoding for track 0 Side 1
        this.track0s1Altencoding = Byte.toUnsignedInt (bb.get ());
        // Alternate track_encoding for track 0 Side 1
        this.track0s1Encoding = Byte.toUnsignedInt (bb.get ());

        if (SIGNATURE_V3.equals (signature))
            this.hfeVersion = HfeVersion.VERSION_3;
        else
            this.hfeVersion = this.formatRevision == 1 ? HfeVersion.VERSION_2 : HfeVersion.VERSION_1;
    }


    /**
     * Read the track list LUT (= Lookup Tables). Up to 256 tracks. Size is 512 or 1024 bytes.
     *
     * @param randomAccessFile The file to read from
     * @throws IOException Could not read the list
     */
    private void readTracks (final RandomAccessFile randomAccessFile) throws IOException
    {
        randomAccessFile.seek (this.trackListOffset * BLOCK_SIZE);
        this.tracks = new TrackData [this.numSides] [this.numTracks];
        final int [] trackOffsets = new int [this.numTracks];
        final int [] trackLengths = new int [this.numTracks];
        for (int trackIndex = 0; trackIndex < this.numTracks; trackIndex++)
        {
            trackOffsets[trackIndex] = StreamUtils.readUnsigned16 (randomAccessFile, false) * BLOCK_SIZE;
            trackLengths[trackIndex] = StreamUtils.readUnsigned16 (randomAccessFile, false);
        }

        for (int trackIndex = 0; trackIndex < this.numTracks; trackIndex++)
            this.readTrack (randomAccessFile, trackIndex, trackOffsets[trackIndex], trackLengths[trackIndex]);
    }


    /**
     * Read the track data into the data field.
     *
     * @param randomAccessFile The file to read from
     * @param trackIndex The index of the track
     * @param trackOffset The offset to the track in bytes
     * @param trackLength The length of the track in bytes
     * @throws IOException Could not read
     */
    public void readTrack (final RandomAccessFile randomAccessFile, final int trackIndex, final int trackOffset, final int trackLength) throws IOException
    {
        if (trackOffset < 0 || trackLength <= 0 || trackOffset + trackLength > randomAccessFile.length ())
            return;

        randomAccessFile.seek (trackOffset);
        final byte [] data = new byte [trackLength];
        randomAccessFile.readFully (data);

        this.deinterleave (trackIndex, data);
    }


    private void deinterleave (final int trackIndex, final byte [] data)
    {
        final int numFullBlocks = data.length / BLOCK_SIZE;
        final int remainingBytes = data.length % BLOCK_SIZE;

        // For side 0: take first 256 bytes from each 512-byte block
        // Plus any remaining bytes from partial block (up to 256)
        final int side0RemainderBytes = Math.min (remainingBytes, TRACK_BLOCK_SIZE);
        final int side0Size = numFullBlocks * TRACK_BLOCK_SIZE + side0RemainderBytes;

        final byte [] side0 = new byte [side0Size];

        int destPos = 0;
        int srcPos = 0;

        // Process full blocks
        for (int block = 0; block < numFullBlocks; block++)
        {
            System.arraycopy (data, srcPos, side0, destPos, TRACK_BLOCK_SIZE);
            srcPos += BLOCK_SIZE;
            destPos += TRACK_BLOCK_SIZE;
        }

        // Process partial block for side 0
        if (side0RemainderBytes > 0)
            System.arraycopy (data, srcPos, side0, destPos, side0RemainderBytes);

        this.tracks[0][trackIndex] = new TrackData (side0);

        if (this.numSides == 2)
        {
            // For side 1: take second 256 bytes from each 512-byte block
            // Plus any remaining bytes from partial block beyond first 256
            final int side1RemainderBytes = Math.max (0, remainingBytes - TRACK_BLOCK_SIZE);
            final int side1Size = numFullBlocks * TRACK_BLOCK_SIZE + side1RemainderBytes;

            final byte [] side1 = new byte [side1Size];

            destPos = 0;
            srcPos = TRACK_BLOCK_SIZE; // Start at side 1 portion of first block

            // Process full blocks
            for (int block = 0; block < numFullBlocks; block++)
            {
                System.arraycopy (data, srcPos, side1, destPos, TRACK_BLOCK_SIZE);
                srcPos += BLOCK_SIZE;
                destPos += TRACK_BLOCK_SIZE;
            }

            // Process partial block for side 1
            if (side1RemainderBytes > 0)
            {
                // Skip to side 1 portion of partial block
                srcPos = numFullBlocks * BLOCK_SIZE + TRACK_BLOCK_SIZE;
                System.arraycopy (data, srcPos, side1, destPos, side1RemainderBytes);
            }

            this.tracks[1][trackIndex] = new TrackData (side1);
        }
    }


    /**
     * Get the HFE version used in the file.
     *
     * @return The version
     */
    public HfeVersion getHfeVersion ()
    {
        return this.hfeVersion;
    }


    /**
     * Get the format revision.
     *
     * @return 0 for the HFEv1, 1 for the HFEv2. Reset to 0 for HFEv3
     */
    public int getFormatRevision ()
    {
        return this.formatRevision;
    }


    /**
     * Get the number of tracks of the disk.
     *
     * @return The number of track(s) in the file
     */
    public int getNumTracks ()
    {
        return this.numTracks;
    }


    /**
     * Get the number of sides.
     *
     * @return The number of valid side(s)
     */
    public int getNumSides ()
    {
        return this.numSides;
    }


    /**
     * Get the Track Encoding mode.
     *
     * @return See the ENCODING_* constants
     */
    public int getTrackEncoding ()
    {
        return this.trackEncoding;
    }


    /**
     * Get the bit-rate.
     *
     * @return The Bit-rate in Kbit/s. e.g. 250=250000bits/s, Max value : 1000
     */
    public int getBitrate ()
    {
        return this.bitrate;
    }


    /**
     * Get the floppy rotation per minute.
     *
     * @return The rotation per minute
     */
    public int getFloppyRotationPerMinute ()
    {
        return this.floppyRotationPerMinute;
    }


    /**
     * Get the floppy interface mode.
     *
     * @return See FLOPPYMODE_* constants
     */
    public int getFloppyInterfaceMode ()
    {
        return this.floppyInterfaceMode;
    }


    /**
     * Get a track.
     *
     * @param sideIndex The index of the side [0..numSides-1]
     * @param trackIndex The index of the track [0..numTracks-1]
     * @return The track
     */
    public TrackData getTrack (final int sideIndex, final int trackIndex)
    {
        return this.tracks[sideIndex][trackIndex];
    }
}