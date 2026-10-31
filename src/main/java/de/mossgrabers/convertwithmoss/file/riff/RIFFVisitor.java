// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import de.mossgrabers.convertwithmoss.exception.ParseException;


/**
 * Callback interface for the RIFF parser.
 *
 * @author Jürgen Moßgraber
 */
public interface RIFFVisitor
{
    /**
     * This method is invoked when the parser attempts to enter a group. The visitor can return
     * false, if the parse shall skip the group contents.
     *
     * @param group The group to enter
     * @return True to enter the group, false to skip over the group.
     */
    boolean enteringGroup (RawRIFFChunk group);


    /**
     * This method is invoked when the parser enters a group chunk.
     *
     * @param group The group
     * @throws ParseException If a parsing error occurs in the group
     */
    void enterGroup (RawRIFFChunk group) throws ParseException;


    /**
     * This method is invoked when the parser leaves a group chunk.
     *
     * @param group The group
     * @throws ParseException If a parsing error occurs
     */
    void leaveGroup (RawRIFFChunk group) throws ParseException;


    /**
     * This method is invoked when the parser has read a data chunk or has skipped a stop chunk.
     *
     * @param group The group that contains the chunk
     * @param chunk The chunk
     * @throws ParseException Parsing error in the chunk
     */
    void visitChunk (RawRIFFChunk group, RawRIFFChunk chunk) throws ParseException;


    /**
     * This method is invoked when the file ends before the data of a data chunk does. Such a chunk
     * is skipped, unless the visitor can use the part of the data which is present: it then visits
     * the chunk shortened to that part, see {@link RawRIFFChunk#isTruncated()}.
     *
     * @param chunk The chunk which is cut off, its size is still the one which its header states
     * @return True to visit the chunk with the data which is present
     */
    default boolean acceptsTruncatedChunk (final RawRIFFChunk chunk)
    {
        return false;
    }


    /**
     * Check if the top chunk is acceptable.
     *
     * @param type The type of the top chunk
     * @throws ParseException Thrown if the top chunk is not accepted
     */
    void checkTopChunk (int type) throws ParseException;
}
