// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mesa;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiKeygroup;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiProgram;
import de.mossgrabers.tools.ui.Functions;


/**
 * The Akai MESA S3P format is a computer-side representation of Akai S-series program data used by
 * the original MESA (Mac/PC Multi-Editor and Sample Accelerator) librarian/editor software for the
 * S-3000 family. In practice the .S3P extension contains a classic S3000-style Program (instrument)
 * encoded in a format akin to MIDI SysEx dumps, with the sample waveforms stored externally as
 * accompanying WAV files on a computer. Internally the Program’s structure—keygroups, sample
 * references, mapping, filters and loop parameters—is essentially the same as an Akai S-series
 * Program on disk; MESA simply encapsulates the Akai program data in its own file container for
 * editing and transfer.
 *
 * @author Jürgen Moßgraber
 */
public class S3pFile
{
    private final static String MAGIC   = "PSYSSS30";

    private AkaiProgram         program = null;


    /**
     * Constructor.
     *
     * @param file The S3P file to read
     * @throws IOException Could not read the file
     */
    public S3pFile (final File file) throws IOException
    {
        final List<byte []> sysexMessages = readSysexMessages (file);
        this.parseSysexMessages (sysexMessages);
    }


    /**
     * Get the program.
     *
     * @return The program
     */
    public AkaiProgram getProgram ()
    {
        return this.program;
    }


    private void parseSysexMessages (final List<byte []> sysexMessages) throws IOException
    {
        final List<AkaiKeygroup> keygroups = new ArrayList<> ();

        for (final byte [] sysexMessage: sysexMessages)
        {
            if (sysexMessage[0] != (byte) 0xF0 || sysexMessage[1] != 0x47 || sysexMessage[4] != 0x48 || sysexMessage[sysexMessage.length - 1] != (byte) 0xF7)
                throw new IOException (Functions.getMessage ("IDS_S3P_INVALID_MIDI_MESSAGE"));

            final int command = sysexMessage[3];
            switch (command)
            {
                case 7:
                    this.program = parseProgram (extractContent (sysexMessage, 7));
                    break;
                case 9:
                    final AkaiKeygroup keygroup = parseKeygroup (extractContent (sysexMessage, 8));
                    if (keygroup != null)
                        keygroups.add (keygroup);
                    break;
                default:
                    // Not used
                    break;
            }
        }

        this.program.setKeygroups (keygroups);
    }


    private static AkaiProgram parseProgram (final byte [] content) throws IOException
    {
        try (final AkaiSysexImage akaiSysexImage = new AkaiSysexImage (content))
        {
            return new AkaiProgram (akaiSysexImage);
        }
    }


    private static AkaiKeygroup parseKeygroup (final byte [] content) throws IOException
    {
        try (final AkaiSysexImage akaiSysexImage = new AkaiSysexImage (content))
        {
            return new AkaiKeygroup (akaiSysexImage);
        }
    }


    /**
     * Removes the Sysex-header and the final F7 byte.
     *
     * @param data The sysex-message from which to extract the content
     * @param dropHeader The number of prefix bytes to drop
     * @return The extracted content data
     */
    private static byte [] extractContent (final byte [] data, final int dropHeader)
    {
        final int newLength = data.length - (dropHeader + 1);
        final byte [] trimmed = new byte [newLength];
        System.arraycopy (data, dropHeader, trimmed, 0, newLength);
        return trimmed;
    }


    /**
     * Read all sysex-messages from the file.
     *
     * @param file The file to read from
     * @return The read sysex-messages
     * @throws IOException Error reading from the file
     */
    private static final List<byte []> readSysexMessages (final File file) throws IOException
    {
        try (final FileInputStream in = new FileInputStream (file))
        {
            if (!MAGIC.equals (StreamUtils.readASCII (in, 8)))
                throw new IOException (Functions.getMessage ("IDS_S3P_NOT_A_S3P_FILE"));

            // Most likely the number of key-groups
            StreamUtils.readUnsigned32 (in, true);

            final List<byte []> sysexMessages = new ArrayList<> ();
            while (in.available () > 0)
            {
                final int sizeOfMessage = (int) StreamUtils.readUnsigned32 (in, true);
                final byte [] message = in.readNBytes (sizeOfMessage);
                sysexMessages.add (message);
            }

            return sysexMessages;
        }
    }
}
