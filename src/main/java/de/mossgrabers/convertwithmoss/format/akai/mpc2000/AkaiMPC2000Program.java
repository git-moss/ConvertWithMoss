// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc2000;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * An Akai MPC2000/MPC2000XL/MPC3000 program.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMPC2000Program
{
    private final List<String>         sampleNames = new ArrayList<> ();
    private final String               programName;
    @SuppressWarnings("unused")
    private final SliderParams         sliderParams;
    @SuppressWarnings("unused")
    private final int                  midiChannel;
    private final List<AkaiMPC2000Pad> pads        = new ArrayList<> ();
    private final byte []              midiNotes;


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read
     */
    public AkaiMPC2000Program (final InputStream input) throws IOException
    {
        final int byte1 = input.read ();
        if (byte1 != 0x07)
            throw new IOException (Functions.getMessage ("IDS_MPC2000_UNKNOWN_FILE"));
        final int byte2 = input.read ();
        final boolean isMPC3000 = byte2 == 0x00;
        if (!isMPC3000 && byte2 != 0x04)
            throw new IOException (Functions.getMessage ("IDS_MPC2000_UNKNOWN_FILE"));

        final int numSamples = isMPC3000 ? 64 : StreamUtils.readUnsigned16 (input, false);
        for (int i = 0; i < numSamples; i++)
        {
            final String ascii = StreamUtils.readASCII (input, 16);
            this.sampleNames.add (ascii.charAt (0) == 0 ? "" : ascii.trim ());
            input.skipNBytes (1);
        }

        if (isMPC3000)
        {
            // 1090 bytes have been read so far (2 + 17 * 64)
            // Name starts at position 2690 therefore skip 1600 bytes
            input.skipNBytes (1600);
        }
        else
        {
            checkBytes (input, 0x1E);
            expectPadding (input);
        }

        this.programName = StreamUtils.readASCII (input, 16).trim ();

        expectPadding (input);

        this.sliderParams = new SliderParams (input);
        this.midiChannel = input.read ();

        if (isMPC3000)
            input.skipNBytes (35);
        else
            checkBytes (input, 0x23, 0x40, 0x00, 0x19, 0x00);

        for (int i = 0; i < 64; i++)
            this.pads.add (new AkaiMPC2000Pad (input, isMPC3000));
        for (final AkaiMPC2000Pad pad: this.pads)
            pad.readMixer (input);

        if (!isMPC3000)
            checkBytes (input, 0x00, 0x00, 0x40, 0x00);

        this.midiNotes = input.readNBytes (64);

        // Ignore the rest of MPC2000/XL bytes, MPC3000 is finished
    }


    /**
     * Get the name of the program.
     *
     * @return The name
     */
    public String getProgramName ()
    {
        return this.programName;
    }


    /**
     * Get the names of the samples.
     *
     * @return All sample names
     */
    public List<String> getSampleNames ()
    {
        return this.sampleNames;
    }


    /**
     * Get the pads.
     *
     * @return The pads
     */
    public List<AkaiMPC2000Pad> getPads ()
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


    private static class SliderParams
    {
        @SuppressWarnings("unused")
        public int note;
        @SuppressWarnings("unused")
        public int tuningLow;
        @SuppressWarnings("unused")
        public int tuningHigh;
        @SuppressWarnings("unused")
        public int decayLow;
        @SuppressWarnings("unused")
        public int decayHigh;
        @SuppressWarnings("unused")
        public int attackLow;
        @SuppressWarnings("unused")
        public int attackHigh;
        @SuppressWarnings("unused")
        public int filterLow;
        @SuppressWarnings("unused")
        public int filterHigh;


        SliderParams (final InputStream in) throws IOException
        {
            this.note = in.read ();
            this.tuningLow = in.read ();
            this.tuningHigh = in.read ();
            this.decayLow = in.read ();
            this.decayHigh = in.read ();
            this.attackLow = in.read ();
            this.attackHigh = in.read ();
            this.filterLow = in.read ();
            this.filterHigh = in.read ();
        }
    }


    private static void expectPadding (final InputStream in) throws IOException
    {
        final int value = in.read ();
        if (value != 0)
            throw new IOException (Functions.getMessage ("IDS_MPC2000_UNKNOWN_FILE"));
    }


    private static void checkBytes (final InputStream in, final int... expected) throws IOException
    {
        for (final int b: expected)
        {
            final int actual = in.read ();
            if (actual != b)
                throw new IOException (Functions.getMessage ("IDS_MPC2000_UNKNOWN_FILE"));
        }
    }
}
