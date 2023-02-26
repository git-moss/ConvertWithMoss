// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import de.mossgrabers.convertwithmoss.exception.ParseException;


/**
 * Callback interface for the RIFF parser.
 *
 * @author J&uuml;rgen Mo&szlig;graber
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
    public boolean enteringGroup (RIFFChunk group);


    /**
     * This method is invoked when the parser enters a group chunk.
     *
     * @param group The group
     * @throws ParseException If a parsing error occurs in the group
     */
    public void enterGroup (RIFFChunk group) throws ParseException;


    /**
     * This method is invoked when the parser leaves a group chunk.
     *
     * @param group The group
     * @throws ParseException If a parsing error occurs
     */
    public void leaveGroup (RIFFChunk group) throws ParseException;


    /**
     * This method is invoked when the parser has read a data chunk or has skipped a stop chunk.
     *
     * @param group The group that contains the chunk
     * @param chunk The chunk
     * @throws ParseException Parsing error in the chunk
     */
    public void visitChunk (RIFFChunk group, RIFFChunk chunk) throws ParseException;
}
