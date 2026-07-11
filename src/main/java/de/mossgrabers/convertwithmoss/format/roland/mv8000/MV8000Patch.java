// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.mv8000;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A Roland MV-8000 patch file (.MV0). The file is an IFF-like big-endian container ('MVFF' magic,
 * form type 'PAT ') with a fixed size bit-packed parameter block ('PRM ') and the embedded sample
 * parameters and wave data ('SMPL').
 *
 * The format was reverse-engineered from the MV-8000 factory patches, see
 * documentation/design/MV8000_FORMAT.md for the details.
 *
 * @author Jürgen Moßgraber
 */
public class MV8000Patch
{
    /** The lowest MIDI note of the 96 entry note table (A0, inherited from the S-770 lineage). */
    public static final int          NOTE_BASE         = 21;

    /** The number of note table entries / pads. */
    public static final int          NUM_NOTES         = 96;

    /** The number of partials. */
    public static final int          NUM_PARTIALS      = 96;

    /** The Roland category names (identical to the XV-5080 category list). */
    public static final String []    CATEGORIES        =
    {
        "NO ASSIGN",
        "AC.PIANO",
        "EL.PIANO",
        "KEYBOARDS",
        "BELL",
        "MALLET",
        "ORGAN",
        "ACCORDION",
        "HARMONICA",
        "AC.GUITAR",
        "EL.GUITAR",
        "DIST.GUITAR",
        "BASS",
        "SYNTH BASS",
        "STRINGS",
        "ORCHESTRA",
        "HIT&STAB",
        "WIND",
        "FLUTE",
        "AC.BRASS",
        "SYNTH BRASS",
        "SAX",
        "HARD LEAD",
        "SOFT LEAD",
        "TECHNO SYNTH",
        "PULSATING",
        "SYNTH FX",
        "OTHER SYNTH",
        "BRIGHT PAD",
        "SOFT PAD",
        "VOX",
        "PLUCKED",
        "ETHNIC",
        "FRETTED",
        "PERCUSSION",
        "SOUND FX",
        "BEAT&GROOVE",
        "DRUMS",
        "COMBINATION"
    };

    private static final String      MAGIC             = "MVFF";
    private static final String      FORM_PATCH        = "PAT ";
    private static final String      CHUNK_FORMAT      = "FMT ";
    private static final String      CHUNK_PARAMETERS  = "PRM ";
    private static final String      CHUNK_SAMPLES     = "SMPL";
    private static final String      CHUNK_WAVE        = "WAVE";

    private static final int         VERSION           = 0x75;
    private static final int         PRM_SIZE          = 15862;
    private static final int         OFFSET_NAME_BITS  = 64;
    private static final int         OFFSET_CATEGORY   = 148;
    private static final int         OFFSET_NOTE_TABLE = 52;
    private static final int         OFFSET_PARTIALS   = 148;
    private static final int         OFFSET_TAIL       = 15796;
    private static final int         NAME_LENGTH       = 12;

    /** Patch common defaults (bits 155-416) from the factory patches, name/category zeroed. */
    private static final byte []     COMMON_TEMPLATE   = HexFormat.of ().parseHex ("00000000000000000000001fe02204000000408102040100104fa04081020409d02040970204081020408100");

    /** The constant 66 byte tail of the parameter block (identical in all factory patches). */
    private static final byte []     TAIL_TEMPLATE     = HexFormat.of ().parseHex ("858776b40818a040810200858776b408192040810200858776b40819a040810200858776b4081a2040810200858776b4081aa040810200858776b4081b2040810200");

    private final MV8000BitArray     parameters;
    private final MV8000Partial []   partials          = new MV8000Partial [NUM_PARTIALS];
    private final int []             noteTable         = new int [NUM_NOTES];
    private final List<MV8000Sample> samples           = new ArrayList<> ();


    /**
     * Constructor. Reads the patch from a stream.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read the patch or it is not a patch file
     */
    public MV8000Patch (final InputStream input) throws IOException
    {
        final String magic = StreamUtils.readAscii (input, 4);
        if (!MAGIC.equals (magic))
            throw new IOException ("Not a MV-8000 file. Found magic: " + magic);
        StreamUtils.readUnsigned32 (input, true);
        StreamUtils.readUnsigned32 (input, true);
        final String form = StreamUtils.readAscii (input, 4);
        if (!FORM_PATCH.equals (form))
            throw new IOException ("Not a MV-8000 patch. Found form type: " + form.trim ());

        byte [] prmData = null;
        byte [] samplesData = null;
        while (true)
        {
            final byte [] idBytes = input.readNBytes (4);
            if (idBytes.length < 4)
                break;
            final String chunkID = new String (idBytes);
            final int size = (int) StreamUtils.readUnsigned32 (input, true);
            final byte [] chunkData = input.readNBytes (size);
            if (chunkData.length < size)
                throw new IOException ("Unexpected end of file in chunk: " + chunkID);
            switch (chunkID)
            {
                case CHUNK_PARAMETERS:
                    prmData = chunkData;
                    break;
                case CHUNK_SAMPLES:
                    samplesData = chunkData;
                    break;
                default:
                    // FMT and unknown chunks are not needed
                    break;
            }
        }

        if (prmData == null || prmData.length != PRM_SIZE)
            throw new IOException ("MV-8000 patch has no or a broken parameter chunk.");
        this.parameters = new MV8000BitArray (prmData);

        for (int i = 0; i < NUM_NOTES; i++)
        {
            final int value = prmData[OFFSET_NOTE_TABLE + i] & 0xFF;
            this.noteTable[i] = value >= 0x80 ? value - 0x80 : -1;
        }

        for (int i = 0; i < NUM_PARTIALS; i++)
        {
            final byte [] partialRecord = new byte [MV8000Partial.SIZE];
            System.arraycopy (prmData, OFFSET_PARTIALS + i * MV8000Partial.SIZE, partialRecord, 0, MV8000Partial.SIZE);
            this.partials[i] = new MV8000Partial (partialRecord);
        }

        if (samplesData != null)
            this.parseSamples (samplesData);
    }


    /**
     * Constructor for creating a new empty patch.
     */
    public MV8000Patch ()
    {
        final byte [] prmData = new byte [PRM_SIZE];
        this.parameters = new MV8000BitArray (prmData);

        prmData[3] = 3;
        System.arraycopy (COMMON_TEMPLATE, 0, prmData, 8, COMMON_TEMPLATE.length);
        for (int i = 0; i < NUM_NOTES; i++)
        {
            this.noteTable[i] = -1;
            prmData[OFFSET_NOTE_TABLE + i] = 0x7F;
        }
        for (int i = 0; i < NUM_PARTIALS; i++)
            this.partials[i] = new MV8000Partial ();
        System.arraycopy (TAIL_TEMPLATE, 0, prmData, OFFSET_TAIL, TAIL_TEMPLATE.length);
    }


    private void parseSamples (final byte [] samplesData) throws IOException
    {
        int offset = 4;
        MV8000Sample sample = null;
        while (offset + 8 <= samplesData.length)
        {
            final String chunkID = new String (samplesData, offset, 4);
            final int size = (samplesData[offset + 4] & 0xFF) << 24 | (samplesData[offset + 5] & 0xFF) << 16 | (samplesData[offset + 6] & 0xFF) << 8 | samplesData[offset + 7] & 0xFF;
            offset += 8;
            if (offset + size > samplesData.length)
                throw new IOException ("Unexpected end of sample data in chunk: " + chunkID);

            if (CHUNK_PARAMETERS.equals (chunkID))
                try (final InputStream in = new ByteArrayInputStream (samplesData, offset, size))
                {
                    sample = new MV8000Sample (in);
                }
            else if (CHUNK_WAVE.equals (chunkID) && sample != null)
            {
                final byte [] waveData = new byte [size];
                System.arraycopy (samplesData, offset, waveData, 0, size);
                sample.setWaveData (waveData);
                this.samples.add (sample);
                sample = null;
            }
            offset += size;
        }
    }


    /**
     * Write the patch file.
     *
     * @param output The output stream to write to
     * @throws IOException Could not write
     */
    public void write (final OutputStream output) throws IOException
    {
        // Update the note table and partial records in the parameter block
        final byte [] prmData = this.parameters.getData ();
        for (int i = 0; i < NUM_NOTES; i++)
            prmData[OFFSET_NOTE_TABLE + i] = (byte) (this.noteTable[i] < 0 ? 0x7F : 0x80 + this.noteTable[i]);
        for (int i = 0; i < NUM_PARTIALS; i++)
            System.arraycopy (this.partials[i].getData (), 0, prmData, OFFSET_PARTIALS + i * MV8000Partial.SIZE, MV8000Partial.SIZE);

        final ByteArrayOutputStream sampleChunks = new ByteArrayOutputStream ();
        StreamUtils.writeUnsigned32 (sampleChunks, 0, true);
        for (final MV8000Sample sample: this.samples)
        {
            final ByteArrayOutputStream samplePrm = new ByteArrayOutputStream ();
            sample.write (samplePrm);
            writeChunk (sampleChunks, CHUNK_PARAMETERS, samplePrm.toByteArray ());
            writeChunk (sampleChunks, CHUNK_WAVE, sample.getWaveData ());
        }

        final ByteArrayOutputStream fmtData = new ByteArrayOutputStream ();
        StreamUtils.writeUnsigned32 (fmtData, 0, true);
        StreamUtils.writeUnsigned32 (fmtData, 1, true);
        StreamUtils.writeUnsigned32 (fmtData, this.samples.size (), true);

        final ByteArrayOutputStream chunks = new ByteArrayOutputStream ();
        writeChunk (chunks, CHUNK_FORMAT, fmtData.toByteArray ());
        writeChunk (chunks, CHUNK_PARAMETERS, prmData);
        writeChunk (chunks, CHUNK_SAMPLES, sampleChunks.toByteArray ());
        final byte [] chunkBytes = chunks.toByteArray ();

        output.write (MAGIC.getBytes ());
        // The size covers the version, the form type and all chunks
        StreamUtils.writeUnsigned32 (output, 8L + chunkBytes.length, true);
        StreamUtils.writeUnsigned32 (output, VERSION, true);
        output.write (FORM_PATCH.getBytes ());
        output.write (chunkBytes);
    }


    private static void writeChunk (final OutputStream output, final String chunkID, final byte [] data) throws IOException
    {
        output.write (chunkID.getBytes ());
        StreamUtils.writeUnsigned32 (output, data.length, true);
        output.write (data);
    }


    /**
     * Get the name of the patch.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.parameters.getText (OFFSET_NAME_BITS, NAME_LENGTH).trim ();
    }


    /**
     * Set the name of the patch.
     *
     * @param name The name, shortened/padded to 12 characters
     */
    public void setName (final String name)
    {
        this.parameters.setText (OFFSET_NAME_BITS, NAME_LENGTH, name);
    }


    /**
     * Get the category index.
     *
     * @return The index into CATEGORIES
     */
    public int getCategory ()
    {
        return this.parameters.getBits (OFFSET_CATEGORY, 7);
    }


    /**
     * Set the category index.
     *
     * @param category The index into CATEGORIES
     */
    public void setCategory (final int category)
    {
        this.parameters.setBits (OFFSET_CATEGORY, 7, category);
    }


    /**
     * Get the note table. Entry i belongs to MIDI note (NOTE_BASE + i) and contains the index of
     * the assigned partial or -1 if the note is not assigned.
     *
     * @return The 96 entries
     */
    public int [] getNoteTable ()
    {
        return this.noteTable;
    }


    /**
     * Get a partial.
     *
     * @param index The index of the partial (0-95)
     * @return The partial
     */
    public MV8000Partial getPartial (final int index)
    {
        return this.partials[index];
    }


    /**
     * Get the samples.
     *
     * @return The samples
     */
    public List<MV8000Sample> getSamples ()
    {
        return this.samples;
    }
}
