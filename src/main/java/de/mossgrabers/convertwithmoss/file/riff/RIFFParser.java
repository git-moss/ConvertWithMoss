// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.imageio.stream.ImageInputStream;

import de.mossgrabers.convertwithmoss.exception.ParseException;


/**
 * Parses Resource Interchange File Format (RIFF) data. See the RIFF specification for more info.
 *
 * @author Jürgen Moßgraber
 */
public class RIFFParser
{
    private final Map<Integer, RiffChunkId> riffChunkIds        = new HashMap<> ();
    private final RiffChunkId               allBytesChunk;
    private boolean                         ignoreUnknownChunks = true;
    private boolean                         ignoreChunkErrors   = true;

    /** The visitor traverses the parse tree. */
    private RIFFVisitor                     visitor;

    /** List of data chunks the visitor is interested in. */
    private final HashSet<RawRIFFChunk>     dataChunks          = new HashSet<> ();
    /** List of property chunks the visitor is interested in. */
    private HashSet<RawRIFFChunk>           propertyChunks;
    /** List of collection chunks the visitor is interested in. */
    private HashSet<RawRIFFChunk>           collectionChunks;
    /** List of stop chunks the visitor is interested in. */
    private final HashSet<Integer>          stopChunkTypes      = new HashSet<> ();
    /** List of group chunks the visitor is interested in. */
    private final HashSet<RawRIFFChunk>     groupChunks         = new HashSet<> ();

    /** Reference to the input stream. */
    private RIFFPrimitivesInputStream       in;
    /** Reference to the image input stream. */
    private ImageInputStream                iin;
    /** Whether we stop at all chunks. */
    private boolean                         isStopChunks;
    /** Stream offset. */
    private long                            streamOffset;
    private boolean                         hasEmptyTopSize     = false;


    /**
     * Constructor.
     *
     * @param riffChunkIdEnums The RIFF chunk IDs which might occur in a specific RIFF based file
     *            format, must contain RiffChunkId
     */
    public RIFFParser (final Collection<Class<? extends RiffChunkId>> riffChunkIdEnums)
    {
        this (riffChunkIdEnums, null);
    }


    /**
     * Constructor.
     *
     * @param riffChunkIdEnums The RIFF chunk IDs which might occur in a specific RIFF based file
     *            format, must contain RiffChunkId
     * @param allBytesChunk The ID of the chunk which has a size of 0 and needs to capture the full
     *            available data of the rest of the file
     */
    public RIFFParser (final Collection<Class<? extends RiffChunkId>> riffChunkIdEnums, final RiffChunkId allBytesChunk)
    {
        for (final Class<? extends RiffChunkId> e: riffChunkIdEnums)
            for (final RiffChunkId id: e.getEnumConstants ())
            {
                final RiffChunkId old = this.riffChunkIds.put (Integer.valueOf (id.getFourCC ()), id);
                if (old != null)
                    throw new IllegalStateException ("Duplicate FourCC: " + id.getDescription ());
            }

        this.allBytesChunk = allBytesChunk;
    }


    /**
     * Set if unknown chunks should be ignored.
     *
     * @param ignoreUnknownChunks Ignores unknown chunks if true otherwise raises an exception
     */
    public void setIgnoreUnknownChunks (final boolean ignoreUnknownChunks)
    {
        this.ignoreUnknownChunks = ignoreUnknownChunks;
    }


    /**
     * Set if errors in chunks should be ignored.
     *
     * @param ignoreChunkErrors Ignores chunk errors like incorrect size of data
     */
    public void setIgnoreChunkErrors (final boolean ignoreChunkErrors)
    {
        this.ignoreChunkErrors = ignoreChunkErrors;
    }


    /**
     * Check if this RIFF file has no size set for the top RIFF chunk (workaround for AKP/AKM
     * format).
     *
     * @return True if it has an empty size
     */
    public boolean hasEmptyTopSize ()
    {
        return this.hasEmptyTopSize;
    }


    /**
     * Interprets the RIFFFile expression located at the current position of the indicated
     * InputStream. Lets the visitor traverse the RIFF parse tree during interpretation.
     *
     * @param in The input stream for getting the data to parse
     * @param callback The callback interface
     * @return The number of parsed bytes
     * @throws ParseException Indicates a parsing error
     * @throws IOException Could not read data from the stream
     */
    public long parse (final InputStream in, final RIFFVisitor callback) throws ParseException, IOException
    {
        this.in = new RIFFPrimitivesInputStream (in);
        this.visitor = callback;
        this.parseFile ();
        return this.getPosition ();
    }


    /**
     * Parses a RIFF file.
     *
     * @throws ParseException Indicates a parsing error
     * @throws IOException Could not read data from the stream
     */
    private void parseFile () throws ParseException, IOException
    {
        final int id = this.in.readFourCC ();

        if (id == CommonRiffChunkId.RIFF_ID.getFourCC ())
        {
            this.parseFORM (null);
            return;
        }

        if (id == CommonRiffChunkId.JUNK_ID.getFourCC ())
        {
            this.parseLocalChunk (null, this.getRiffChunkId (id));
            return;
        }

        if (id != CommonRiffChunkId.NULL_NUL_ID.getFourCC () && id != CommonRiffChunkId.NULL_ID.getFourCC () && !this.ignoreUnknownChunks)
            this.raiseRiffParseError (id);
    }


    /**
     * Get the current parsing position in the stream.
     *
     * @return The position
     */
    public long getPosition ()
    {
        return this.in.getPosition () + this.streamOffset;
    }


    /**
     * Parses a FORM group.
     *
     * @param props The property chunks
     * @throws ParseException Indicates a parsing error
     * @throws IOException Could not read data from the stream
     */
    private void parseFORM (final Map<Integer, RawRIFFChunk> props) throws ParseException, IOException
    {
        long size = this.in.readUDWORD ();
        final long offset = this.getPosition ();
        final int type = this.in.readFourCC ();
        if (!isGroupType (type))
            throw new ParseException ("Invalid FORM Type: \"" + RiffChunkId.toASCII (type) + "\"");

        if (size == 0 && type == this.allBytesChunk.getFourCC ())
        {
            this.hasEmptyTopSize = true;
            size = this.in.available ();
        }

        final RawRIFFChunk propGroup = props == null ? null : props.get (Integer.valueOf (type));
        final RawRIFFChunk chunk = new RawRIFFChunk (type, CommonRiffChunkId.RIFF_ID, size, propGroup);

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
            while (this.getPosition () < finish)
            {
                final long idscan = this.getPosition ();
                final int id = this.in.readFourCC ();

                if (id == CommonRiffChunkId.RIFF_ID.getFourCC ())
                    this.parseFORM (props);
                else if (id == CommonRiffChunkId.LIST_ID.getFourCC ())
                    this.parseLIST (props);
                else if (isLocalChunkID (id))
                    this.parseLocalChunk (chunk, this.getRiffChunkId (id));
                else if (this.ignoreUnknownChunks)
                {
                    final long longSize = this.in.readUDWORD ();
                    this.in.skipFully (longSize);
                }
                else
                {
                    final ParseException pex = new ParseException ("Invalid Chunk: \"" + RiffChunkId.toASCII (id) + "\" (" + id + ") at offset:" + idscan);
                    chunk.setParserMessage (pex.getMessage ());
                    throw pex;
                }

                this.in.align ();
            }
        }
        catch (final EOFException e)
        {
            chunk.setParserMessage ("Unexpected EOF after " + NumberFormat.getInstance ().format (this.getPosition () - offset) + " bytes");
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
    private void parseLIST (final Map<Integer, RawRIFFChunk> props) throws ParseException, IOException
    {
        final long size = this.in.readUDWORD ();
        final long scan = this.getPosition ();
        final int type = this.in.readFourCC ();

        if (!isGroupType (type))
            throw new ParseException ("Invalid LIST Type: \"" + type + "\"");

        final RawRIFFChunk propGroup = props == null ? null : props.get (Integer.valueOf (type));
        final RawRIFFChunk chunk = new RawRIFFChunk (type, CommonRiffChunkId.LIST_ID, size, propGroup);

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
                while (this.getPosition () < finish)
                {
                    final long idscan = this.getPosition ();
                    final int id = this.in.readFourCC ();
                    if (id == CommonRiffChunkId.LIST_ID.getFourCC ())
                        this.parseLIST (props);
                    else if (isLocalChunkID (id))
                        this.parseLocalChunk (chunk, this.getRiffChunkId (id));
                    else
                    {
                        this.parseGarbage (chunk, this.getRiffChunkId (id), finish - this.getPosition ());
                        final ParseException pex = new ParseException ("Invalid Chunk: \"" + RiffChunkId.toASCII (id) + "\" (" + id + ") at offset:" + idscan);
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
    private void parseLocalChunk (final RawRIFFChunk parent, final RiffChunkId id) throws ParseException, IOException
    {
        final long longSize = this.in.readUDWORD ();
        final RawRIFFChunk chunk = new RawRIFFChunk (parent == null ? 0 : parent.getType (), id, longSize);

        if (longSize < Integer.MAX_VALUE)
        {
            final int size = (int) longSize;
            if (size < 0)
                throw new ParseException ("Found negative chunk length. File is broken?!");

            if (this.handleChunks (parent, chunk, size))
                return;
        }
        else
            chunk.markTooLarge ();

        this.in.skipFully (longSize);
        if (this.isStopChunks)
            this.visitor.visitChunk (parent, chunk);
    }


    /**
     * Handles the given chunk.
     *
     * @param parent The parent chunk
     * @param chunk The chunk to handle
     * @param size The size of the chunk
     * @return True if handled
     * @throws ParseException Indicates a parsing error
     * @throws IOException Could not read data from the stream
     */
    private boolean handleChunks (final RawRIFFChunk parent, final RawRIFFChunk chunk, final int size) throws IOException, ParseException
    {
        if (this.isDataChunk (chunk))
        {
            // Note: this can only read up to 2GB!
            final byte [] chunkData = this.in.readNBytes (size);
            if (chunkData.length != size && this.ignoreChunkErrors)
                return true;
            chunk.setData (chunkData);
            this.visitor.visitChunk (parent, chunk);
            return true;
        }

        if (this.isPropertyChunk (chunk))
        {
            final byte [] chunkData = this.in.readNBytes (size);
            if (chunkData.length != size && this.ignoreChunkErrors)
                return true;
            chunk.setData (chunkData);
            if (parent != null)
                parent.putPropertyChunk (chunk);
            return true;
        }

        if (this.isCollectionChunk (chunk))
        {
            final byte [] chunkData = this.in.readNBytes (size);
            if (chunkData.length != size && this.ignoreChunkErrors)
                return true;
            chunk.setData (chunkData);
            if (parent != null)
                parent.addCollectionChunk (chunk);
            return true;
        }

        return false;
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
    private void parseGarbage (final RawRIFFChunk parent, final RiffChunkId id, final long longSize) throws ParseException, IOException
    {
        final RawRIFFChunk chunk = new RawRIFFChunk (parent.getType (), id, longSize);

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
            chunk.markTooLarge ();

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
    protected boolean isDataChunk (final RawRIFFChunk chunk)
    {
        if (this.dataChunks.isEmpty ())
            return this.collectionChunks == null && this.propertyChunks == null && !this.stopChunkTypes.contains (Integer.valueOf (chunk.getType ()));
        return this.dataChunks.contains (chunk);
    }


    /**
     * Checks whether the ID of the chunk has been declared as a group chunk.
     *
     * @param chunk Chunk to be verified
     * @return True when the visitor is interested in this
     */
    protected boolean isGroupChunk (final RawRIFFChunk chunk)
    {
        return this.groupChunks.contains (chunk);
    }


    /**
     * Checks whether the ID of the chunk has been declared as a property chunk.
     *
     * @param chunk The chunk to test
     * @return True if it is a property chunk
     */
    protected boolean isPropertyChunk (final RawRIFFChunk chunk)
    {
        return this.propertyChunks != null && this.propertyChunks.contains (chunk);
    }


    /**
     * Checks whether the ID of the chunk has been declared as a collection chunk.
     *
     * @param chunk Chunk to be tested
     * @return True when the parameter is a collection chunk
     */
    protected boolean isCollectionChunk (final RawRIFFChunk chunk)
    {
        return this.collectionChunks != null && this.collectionChunks.contains (chunk);
    }


    /**
     * Declares a data chunk.
     *
     * @param type Type of the chunk
     * @param id ID of the chunk
     */
    public void declareDataChunk (final int type, final RiffChunkId id)
    {
        this.dataChunks.add (new RawRIFFChunk (type, id));
    }


    /**
     * Declares a FORM group chunk.
     *
     * @param type Type of the chunk
     * @param id ID of the chunk
     */
    public void declareGroupChunk (final int type, final RiffChunkId id)
    {
        this.groupChunks.add (new RawRIFFChunk (type, id));
    }


    /**
     * Declares a property chunk.
     *
     * @param type Type of the chunk
     * @param id ID of the chunk
     */
    public void declarePropertyChunk (final int type, final RiffChunkId id)
    {
        final RawRIFFChunk chunk = new RawRIFFChunk (type, id);
        if (this.propertyChunks == null)
            this.propertyChunks = new HashSet<> ();
        this.propertyChunks.add (chunk);
    }


    /**
     * Declares a collection chunk.
     *
     * @param type Type of the chunk
     * @param id ID of the chunk
     */
    public void declareCollectionChunk (final int type, final RiffChunkId id)
    {
        final RawRIFFChunk chunk = new RawRIFFChunk (type, id);
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


    private boolean isStopChunk (final RawRIFFChunk chunk)
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
        return CommonRiffChunkId.isValidId (id) && !CommonRiffChunkId.isGroupId (id) && CommonRiffChunkId.NULL_ID.getFourCC () != id;
    }


    /**
     * Returns whether the argument is a valid Local Chunk ID.
     *
     * @param id Chunk ID to be checked
     * @return True when the chunk ID is a Local Chunk ID
     */
    public static boolean isLocalChunkID (final int id)
    {
        return CommonRiffChunkId.isValidId (id) && !CommonRiffChunkId.isGroupId (id) && CommonRiffChunkId.NULL_ID.getFourCC () != id;
    }


    private void raiseRiffParseError (final int id) throws ParseException
    {
        final StringBuilder sb = new StringBuilder ();
        if (id == CommonRiffChunkId.UNSUPPORTED.getFourCC ())
            sb.append ("Unknown top level RIFF file ID: \"").append (RiffChunkId.toASCII (id));
        else
            sb.append ("Unexpected top level RIFF file ID: \"").append (RiffChunkId.toASCII (id));

        sb.append (" 0x").append (Integer.toHexString (id));

        if (this.iin != null)
            try
            {
                sb.append (" near ").append (this.iin.getStreamPosition ()).append (" 0x").append (Long.toHexString (this.iin.getStreamPosition ()));
            }
            catch (final IOException ex)
            {
                sb.append (", no further information available.");
            }
        throw new ParseException (sb.toString ());
    }


    private RiffChunkId getRiffChunkId (final int id)
    {
        final RiffChunkId riffChunkId = this.riffChunkIds.get (Integer.valueOf (id));
        return riffChunkId == null ? CommonRiffChunkId.UNSUPPORTED : riffChunkId;
    }
}
