// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * A Kontakt 5+ external modulator like velocity or pitch-bend.
 *
 * @author Jürgen Moßgraber
 */
public class ExternalModulator
{
    /** Velocity as the source. */
    public static final int SOURCE_VELOCITY  = 0x00;
    /** Pitch-bend as the source. */
    public static final int SOURCE_PITCHBEND = 0x01;
    /** MIDI CC as the source. */
    public static final int SOURCE_MIDI_CC   = 0x04;
    /** Velocity as the source. */
    public static final int SOURCE_VELOCITY2 = 0x06;

    /** Pitch 2 as the destination. */
    public static final int DEST_PITCH2      = 0x38;
    /** Volume as the destination. */
    public static final int DEST_VOLUME2     = 0x3B;
    /** Pitch as the destination. */
    public static final int DEST_PITCH       = 0x3D;
    /** Volume as the destination. */
    public static final int DEST_VOLUME      = 0x3E;
    /** Volume as the destination. */
    public static final int DEST_VOLUME3     = 0x41;

    private long            destType;
    private long            sourceType;
    private float           intensity        = 1.0f;


    /**
     * Constructor.
     */
    public ExternalModulator ()
    {
        // Intentionally empty
    }


    /**
     * Get the destination type.
     *
     * @return The destination type
     */
    public long getDestType ()
    {
        return this.destType;
    }


    /**
     * Get the source type.
     *
     * @return The source type
     */
    public long getSourceType ()
    {
        return this.sourceType;
    }


    /**
     * Get the modulation intensity.
     *
     * @return The intensity [0..1], needs to be rounded after the 2nd fraction!
     */
    public float getIntensity ()
    {
        return this.intensity;
    }


    /**
     * Read the internal modulator data.
     *
     * @param chunk The chunk from which to read the bank data
     * @throws IOException Could not read the bank
     */
    public void read (final KontaktPresetChunk chunk) throws IOException
    {
        final int id = chunk.getId ();
        if (id != KontaktPresetChunkID.PAR_EXTERNAL_MOD)
            throw new IOException (Functions.getMessage ("IDS_NKI_NOT_EXTERNAL_MODULATION"));

        final InputStream in = new ByteArrayInputStream (chunk.getPublicData ());

        @SuppressWarnings("unused")
        final int u1 = in.read (); // Always 0x01 ?
        @SuppressWarnings("unused")
        final int u2 = in.read (); // Always 0x02 ?
        @SuppressWarnings("unused")
        final int u3 = in.read (); // Always 0x01 ?
        // System.out.println ("U1-U3: " + u1 + ":" + u2 + ":" + u3);

        this.destType = StreamUtils.readUnsigned32 (in, false);
        // System.out.println ("Dest Type: " + this.destType);

        @SuppressWarnings("unused")
        final long unknown2 = StreamUtils.readUnsigned32 (in, false); // 01 00 00 00
        // System.out.println ("Unknown 2: " + unknown2);

        try
        {
            @SuppressWarnings("unused")
            final String destinationDesc = StreamUtils.readWith4ByteLengthAscii (in);
            // System.out.println ("Destination Desc:" + destinationDesc);
        }
        catch (final IOException _)
        {
            // System.out.println ("Destination Desc: Could not read text");
            return;
        }

        this.intensity = StreamUtils.readFloatLE (in);
        // System.out.println ("Intensity: " + intensity);

        @SuppressWarnings("unused")
        final int a = in.read (); // 0xFF ?
        @SuppressWarnings("unused")
        final int b = in.read (); // 0xFF ?
        @SuppressWarnings("unused")
        final int c = in.read (); // 0x14 ?
        // System.out.println ("A-C: " + a + ":" + b + ":" + c);

        @SuppressWarnings("unused")
        final int unknown3 = StreamUtils.readUnsigned16 (in, false);
        // System.out.println ("Unknown 3: " + unknown3);

        // TODO There can be multiple descriptions, maybe similar to internal modulator

        final String modDescription;
        try
        {
            modDescription = StreamUtils.readWith4ByteLengthAscii (in);
            // System.out.println ("Mod. Description: " + modDescription);
        }
        catch (final IOException _)
        {
            // System.out.println ("Mod. Description: Could not read text");
            return;
        }

        // TODO not correct
        if (!modDescription.isEmpty ())
        {
            @SuppressWarnings("unused")
            final int unknown4 = in.read ();
            // System.out.println ("Unknown 4: " + unknown4);
        }

        @SuppressWarnings("unused")
        final long unknown5 = StreamUtils.readUnsigned16 (in, false);
        // System.out.println ("Unknown 5: " + unknown5);

        try
        {
            @SuppressWarnings("unused")
            final String description = StreamUtils.readWith4ByteLengthAscii (in);
            // System.out.println ("Description: " + description);
        }
        catch (final IOException _)
        {
            // System.out.println ("Description: Could not read text");
            return;
        }

        @SuppressWarnings("unused")
        final long unknown6 = StreamUtils.readUnsigned32 (in, false);
        // System.out.println ("Unknown 6: " + unknown6);

        this.sourceType = StreamUtils.readUnsigned32 (in, false);
        // System.out.println ("Source Type: " + this.sourceType);

        @SuppressWarnings("unused")
        final long unknown8 = StreamUtils.readUnsigned32 (in, false);
        // System.out.println ("Unknown 8: " + unknown8);

        @SuppressWarnings("unused")
        final long arraySlot = StreamUtils.readUnsigned32 (in, false);
        // System.out.println ("Array Slot: " + arraySlot);

        @SuppressWarnings("unused")
        final byte [] padding = in.readNBytes (7);

        // System.out.println ("-----------------------");
    }
}