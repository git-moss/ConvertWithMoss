// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import de.mossgrabers.convertwithmoss.exception.ParseException;

import javax.imageio.stream.ImageInputStream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Map;


/**
 * Parses Resource Interchange File Format (RIFF) data. See the RIFF specification for more info.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class RIFFParser
{
    /** The visitor traverses the parse tree. */
    private RIFFVisitor               visitor;

    /** List of data chunks the visitor is interested in. */
    private final HashSet<RIFFChunk>  dataChunks     = new HashSet<> ();
    /** List of property chunks the visitor is interested in. */
    private HashSet<RIFFChunk>        propertyChunks;
    /** List of collection chunks the visitor is interested in. */
    private HashSet<RIFFChunk>        collectionChunks;
    /** List of stop chunks the visitor is interested in. */
    private final HashSet<Integer>    stopChunkTypes = new HashSet<> ();
    /** List of group chunks the visitor is interested in. */
    private HashSet<RIFFChunk>        groupChunks    = new HashSet<> ();

    /** Reference to the input stream. */
    private RIFFPrimitivesInputStream in;
    /** Reference to the image input stream. */
    private ImageInputStream          iin;
    /** Whether we stop at all chunks. */
    private boolean                   isStopChunks;
    /** Stream offset. */
    private long                      streamOffset;


    /**
     * Interprets the RIFFFile expression located at the current position of the indicated
     * InputStream. Lets the visitor traverse the RIFF parse tree during interpretation.
     *
     * @param in The input stream for getting the data to parse
     * @param callback The callback interface
     * @param ignoreUnknownChunks Ignores unknown chunks if true otherwise raises an exception
     * @return The number of parsed bytes
     * @throws ParseException Indicates a parsing error
     * @throws IOException Could not read data from the stream
     */
    public long parse (final InputStream in, final RIFFVisitor callback, final boolean ignoreUnknownChunks) throws ParseException, IOException
    {
        this.in = new RIFFPrimitivesInputStream (in);
        this.visitor = callback;
        this.parseFile (ignoreUnknownChunks);
        return this.getPosition (this.in);
    }


    /**
     * Parses a RIFF file.
     *
     * @param ignoreUnknownChunks Ignores unknown chunks if true otherwise raises an exception
     * @throws ParseException Indicates a parsing error
     * @throws IOException Could not read data from the stream
     */
    private void parseFile (final boolean ignoreUnknownChunks) throws ParseException, IOException
    {
        final int id = this.in.readFourCC ();

        final RiffID riffID = RiffID.fromId (id);
        switch (riffID)
        {
            case RIFF_ID:
                this.parseFORM (null);
                break;

            case JUNK_ID:
                this.parseLocalChunk (null, id);
                break;

            case NULL_NUL_ID:
            case NULL_ID:
                // Ignore
                break;

            case UNKNOWN:
            default:
                if (!ignoreUnknownChunks)
                    this.raiseRiffParseError (id, riffID);
                break;
        }
    }


    /**
     * Get the current parsing position in the stream.
     *
     * @param in The input stream
     * @return The position
     */
    private long getPosition (final RIFFPrimitivesInputStream in)
    {
        return in.getPosition () + this.streamOffset;
    }


    /**
     * Parses a FORM group.
     *
     * @param props
     * @throws ParseException Indicates a parsing error
     * @throws IOException Could not read data from the stream
     */
    private void parseFORM (final Map<Integer, RIFFChunk> props) throws ParseException, IOException
    {
        final long size = this.in.readUDWORD ();
        final long offset = this.getPosition (this.in);
        final int type = this.in.readFourCC ();
        if (!isGroupType (type))
            throw new ParseException ("Invalid FORM Type: \"" + RiffID.toASCII (type) + "\"");

        final RIFFChunk propGroup = props == null ? null : props.get (Integer.valueOf (type));
        final RIFFChunk chunk = new RIFFChunk (type, RiffID.RIFF_ID.getId (), size, propGroup);

        boolean visitorWantsToEnterGroup = false;
        if (this.isGroupChunk (chunk))
        {
            visitorWantsToEnterGroup = this.visitor.enteringGroup (chunk);
            if (visitorWantsToEnterGroup)
                this.visitor.enterGroup (chunk);
        }

        try
        {
            final long finish = offset + size;
            while (this.getPosition (this.in) < finish)
            {
                final long idscan = this.getPosition (this.in);
                final int id = this.in.readFourCC ();

                if (id == RiffID.RIFF_ID.getId ())
                    this.parseFORM (props);
                else if (id == RiffID.LIST_ID.getId ())
                    this.parseLIST (props);
                else if (isLocalChunkID (id))
                    this.parseLocalChunk (chunk, id);
                else
                {
                    final ParseException pex = new ParseException ("Invalid Chunk: \"" + id + "\" at offset:" + idscan);
                    chunk.setParserMessage (pex.getMessage ());
                    throw pex;
                }

                this.in.align ();
            }
        }
        catch (final EOFException e)
        {
            chunk.setParserMessage ("Unexpected EOF after " + NumberFormat.getInstance ().format (this.getPosition (this.in) - offset) + " bytes");
        }
        finally
        {
            if (visitorWantsToEnterGroup)
                this.visitor.leaveGroup (chunk);
        }
    }


    /**
     * Parses a LIST group.
     *
     * @param props The property chunks
     * @throws ParseException Indicates a parsing error
     * @throws IOException Could not read data from the stream
     */
    private void parseLIST (final Map<Integer, RIFFChunk> props) throws ParseException, IOException
    {
        final long size = this.in.readUDWORD ();
        final long scan = this.getPosition (this.in);
        final int type = this.in.readFourCC ();

        if (!isGroupType (type))
            throw new ParseException ("Invalid LIST Type: \"" + type + "\"");

        final RIFFChunk propGroup = props == null ? null : props.get (Integer.valueOf (type));
        final RIFFChunk chunk = new RIFFChunk (type, RiffID.LIST_ID.getId (), size, propGroup);

        boolean visitorWantsToEnterGroup = false;
        if (this.isGroupChunk (chunk))
        {
            visitorWantsToEnterGroup = this.visitor.enteringGroup (chunk);
            if (visitorWantsToEnterGroup)
                this.visitor.enterGroup (chunk);
        }

        try
        {
            if (visitorWantsToEnterGroup)
            {
                final long finish = scan + size;
                while (this.getPosition (this.in) < finish)
                {
                    final long idscan = this.getPosition (this.in);
                    final int id = this.in.readFourCC ();
                    if (id == RiffID.LIST_ID.getId ())
                        this.parseLIST (props);
                    else if (isLocalChunkID (id))
                        this.parseLocalChunk (chunk, id);
                    else
                    {
                        this.parseGarbage (chunk, id, finish - this.getPosition (this.in));
                        final ParseException pex = new ParseException ("Invalid Chunk: \"" + id + "\" at offset:" + idscan);
                        chunk.setParserMessage (pex.getMessage ());
                    }

                    this.in.align ();
                }
            }
            else
            {
                this.in.skipFully (size - 4);
                this.in.align ();
            }
        }
        finally
        {
            if (visitorWantsToEnterGroup)
                this.visitor.leaveGroup (chunk);
        }
    }


    /**
     * Parses a local chunk.
     *
     * @param parent The parent chunk
     * @param id The chunk ID
     * @throws ParseException Indicates a parsing error
     * @throws IOException Could not read data from the stream
     */
    private void parseLocalChunk (final RIFFChunk parent, final int id) throws ParseException, IOException
    {
        final long longSize = this.in.readUDWORD ();
        this.getPosition (this.in);
        final RIFFChunk chunk = new RIFFChunk (parent == null ? 0 : parent.getType (), id, longSize);

        if (longSize < Integer.MAX_VALUE)
        {
            final int size = (int) longSize;
            if (size < 0)
                throw new ParseException ("Found negative chunk length. File is broken?!");

            if (this.isDataChunk (chunk))
            {
                final byte [] data = new byte [size];
                this.in.read (data, 0, size);
                chunk.setData (data);
                this.visitor.visitChunk (parent, chunk);
                return;
            }

            if (this.isPropertyChunk (chunk))
            {
                final byte [] data = new byte [size];
                this.in.read (data, 0, size);
                chunk.setData (data);
                if (parent != null)
                    parent.putPropertyChunk (chunk);
                return;
            }

            if (this.isCollectionChunk (chunk))
            {
                final byte [] data = new byte [size];
                this.in.read (data, 0, size);
                chunk.setData (data);
                if (parent != null)
                    parent.addCollectionChunk (chunk);
                return;
            }
        }
        else
        {
            chunk.markTooLarge ();
        }

        this.in.skipFully (longSize);
        if (this.isStopChunks)
            this.visitor.visitChunk (parent, chunk);
    }


    /**
     * This method is invoked when we encounter a parsing problem.
     *
     * @param parent The parent chunk
     * @param id The chunk id
     * @param longSize The size to read
     * @throws ParseException Indicates a parsing error
     * @throws IOException Could not read data from the stream
     */
    private void parseGarbage (final RIFFChunk parent, final int id, final long longSize) throws ParseException, IOException
    {
        final RIFFChunk chunk = new RIFFChunk (parent.getType (), id, longSize);

        if (longSize < Integer.MAX_VALUE)
        {
            final int size = (int) longSize;
            if (size < 0)
                throw new ParseException ("Found negative chunk length. File is broken?!");

            if (this.isDataChunk (chunk))
            {
                final byte [] data = new byte [size];
                this.in.read (data, 0, size);
                chunk.setData (data);
                this.visitor.visitChunk (parent, chunk);
                return;
            }

            if (this.isPropertyChunk (chunk))
            {
                final byte [] data = new byte [size];
                this.in.read (data, 0, size);
                chunk.setData (data);
                parent.putPropertyChunk (chunk);
                return;
            }

            if (this.isCollectionChunk (chunk))
            {
                final byte [] data = new byte [size];
                this.in.read (data, 0, size);
                chunk.setData (data);
                parent.addCollectionChunk (chunk);
                return;
            }
        }
        else
        {
            chunk.markTooLarge ();
        }

        this.in.skipFully (longSize);
        if (this.isStopChunk (chunk))
            this.visitor.visitChunk (parent, chunk);
    }


    /**
     * Checks whether the ID of the chunk has been declared as a data chunk.
     *
     * @param chunk Chunk to be verified.
     * @return True when the parameter is a data chunk.
     */
    protected boolean isDataChunk (final RIFFChunk chunk)
    {
        if (this.dataChunks.isEmpty ())
            return this.collectionChunks == null && this.propertyChunks == null && !this.stopChunkTypes.contains (Integer.valueOf (chunk.getType ()));
        return this.dataChunks.contains (chunk);
    }


    /**
     * Checks whether the ID of the chunk has been declared as a group chunk.
     *
     * @param chunk Chunk to be verified.
     * @return True when the visitor is interested in this is a group chunk.
     */
    protected boolean isGroupChunk (final RIFFChunk chunk)
    {
        return this.groupChunks.contains (chunk);
    }


    /**
     * Checks whether the ID of the chunk has been declared as a property chunk.
     *
     * @param chunk The chunk to test
     * @return True if it is a property chunk
     */
    protected boolean isPropertyChunk (final RIFFChunk chunk)
    {
        return this.propertyChunks != null && this.propertyChunks.contains (chunk);
    }


    /**
     * Checks whether the ID of the chunk has been declared as a collection chunk.
     *
     * @param chunk Chunk to be tested
     * @return True when the parameter is a collection chunk
     */
    protected boolean isCollectionChunk (final RIFFChunk chunk)
    {
        return this.collectionChunks != null && this.collectionChunks.contains (chunk);
    }


    /**
     * Declares a data chunk.
     *
     * @param type Type of the chunk. Must be formulated as a TypeID conforming to the method
     *            #isFormType.
     * @param id ID of the chunk. Must be formulated as a ChunkID conforming to the method
     *            #isLocalChunkID.
     */
    public void declareDataChunk (final int type, final int id)
    {
        this.dataChunks.add (new RIFFChunk (type, id));
    }


    /**
     * Declares a FORM group chunk.
     *
     * @param type Type of the chunk. Must be formulated as a TypeID conforming to the method
     *            #isFormType.
     * @param id ID of the chunk. Must be formulated as a ChunkID conforming to the method
     *            #isContentsType.
     */
    public void declareGroupChunk (final int type, final int id)
    {
        this.groupChunks.add (new RIFFChunk (type, id));
    }


    /**
     * Declares a property chunk.
     *
     * @param type Type of the chunk. Must be formulated as a TypeID conforming to the method
     *            #isFormType.
     * @param id ID of the chunk. Must be formulated as a ChunkID conforming to the method
     *            #isLocalChunkID.
     */
    public void declarePropertyChunk (final int type, final int id)
    {
        final RIFFChunk chunk = new RIFFChunk (type, id);
        if (this.propertyChunks == null)
            this.propertyChunks = new HashSet<> ();
        this.propertyChunks.add (chunk);
    }


    /**
     * Declares a collection chunk.
     *
     * @param type Type of the chunk. Must be formulated as a TypeID conforming to the method
     *            #isFormType.
     * @param id ID of the chunk. Must be formulated as a ChunkID conforming to the method
     *            #isLocalChunkID.
     */
    public void declareCollectionChunk (final int type, final int id)
    {
        final RIFFChunk chunk = new RIFFChunk (type, id);
        if (this.collectionChunks == null)
            this.collectionChunks = new HashSet<> ();

        this.collectionChunks.add (chunk);
    }


    /**
     * Declares a stop chunk.
     *
     * @param type Type of the chunk. Must be formulated as a TypeID conforming to the method
     *            #isFormType.
     */
    public void declareStopChunkType (final int type)
    {
        this.stopChunkTypes.add (Integer.valueOf (type));
    }


    /**
     * Whether the parse should stop at all chunks.
     * <p>
     * The parser does not read the data body of stop chunks.
     * <p>
     * By declaring stop chunks, and not declaring any data, group or property chunks, the file
     * structure of a RIFF file can be quickly scanned through.
     */
    public void declareStopChunks ()
    {
        this.isStopChunks = true;
    }


    private boolean isStopChunk (final RIFFChunk chunk)
    {
        return this.isStopChunks || this.stopChunkTypes.contains (Integer.valueOf (chunk.getType ()));
    }


    /**
     * Checks whether the argument represents a valid RIFF Group Type.
     *
     * @param id Chunk ID to be checked.
     * @return True when the chunk ID is a valid Group ID
     */
    public static boolean isGroupType (final int id)
    {
        return RiffID.isValidId (id) && !RiffID.isGroupId (id) && !RiffID.NULL_ID.matches (id);
    }


    /**
     * Returns whether the argument is a valid Local Chunk ID.
     *
     * @param id Chunk ID to be checked
     * @return True when the chunk ID is a Local Chunk ID
     */
    public static boolean isLocalChunkID (final int id)
    {
        return RiffID.isValidId (id) && !RiffID.isGroupId (id) && !RiffID.NULL_ID.matches (id);
    }


    private void raiseRiffParseError (final int id, final RiffID riffID) throws ParseException
    {
        final StringBuilder sb = new StringBuilder ();
        if (riffID == RiffID.UNKNOWN)
            sb.append ("Unknown top level RIFF file ID: \"").append (RiffID.toASCII (id));
        else
            sb.append ("Unexpected top level RIFF file ID: \"").append (riffID.asASCII ());

        sb.append (" 0x").append (Integer.toHexString (id));

        if (this.iin != null)
        {
            try
            {
                sb.append (" near ").append (this.iin.getStreamPosition ()).append (" 0x").append (Long.toHexString (this.iin.getStreamPosition ()));
            }
            catch (final IOException ex)
            {
                sb.append (", no further information available.");
            }
        }
        throw new ParseException (sb.toString ());
    }
}
