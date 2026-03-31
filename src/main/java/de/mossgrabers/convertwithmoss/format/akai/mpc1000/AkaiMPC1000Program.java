// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc1000;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * An Akai MPC500/MPC1000/MPC2500 program. The MPC 1000 PGM file format has five sections: Header,
 * Sample and Pad, MIDI, Slider, and Footer. Up to four samples can be assigned to each of the
 * sixty-four pads.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMPC1000Program
{
    private static final String        MAGIC = "MPC1000 PGM 1.00";

    private final List<AkaiMPC1000Pad> pads  = new ArrayList<> ();
    private byte []                    midiNotes;
    private byte []                    assignedPads;
    @SuppressWarnings("unused")
    private int                        midiProgramChange;


    /**
     * Constructor.
     * 
     * @param input The input stream to read from
     * @throws IOException Could not read
     */
    public AkaiMPC1000Program (final InputStream input) throws IOException
    {
        // 2 bytes file size and 2 bytes padding
        input.skipNBytes (4);

        final String magic = StreamUtils.readASCII (input, 16);
        if (!MAGIC.equals (magic))
            throw new IOException (Functions.getMessage ("IDS_MPC1000_UNKNOWN_FILE"));

        // 4 bytes padding
        input.skipNBytes (4);

        for (int i = 0; i < 64; i++)
        {
            final List<AkaiMPC1000Sample> samples = new ArrayList<> ();
            for (int sampleIndex = 0; sampleIndex < 4; sampleIndex++)
                samples.add (new AkaiMPC1000Sample (input));
            this.pads.add (new AkaiMPC1000Pad (input, samples));
        }

        this.midiNotes = input.readNBytes (64);
        this.assignedPads = input.readNBytes (128);
        this.midiProgramChange = input.read ();

        // Parameters of slider 1 + 2
        input.skipNBytes (26);

        // Padding
        input.skipNBytes (17);

        int available = input.available ();
        System.out.println (available);
    }


    /**
     * Get all pads.
     * 
     * @return The 64 pads
     */
    public List<AkaiMPC1000Pad> getPads ()
    {
        return this.pads;
    }


    /**
     * Get the MIDI notes which can be assigned to pads.
     * 
     * @return The array of 64 MIDI notes
     */
    public byte [] getMidiNotes ()
    {
        return this.midiNotes;
    }


    /**
     * Get the pads which have an assigned MIDI notes.
     * 
     * @return An array with 64 entries, the index refers to the MIDI note array, the value
     *         represents the index of the pad to which the note is assigned. A value of 64
     *         indicates no assignment
     */
    public byte [] getAssignedPads ()
    {
        return this.assignedPads;
    }
}
