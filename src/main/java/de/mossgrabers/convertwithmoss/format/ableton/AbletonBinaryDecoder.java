// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ableton;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.mossgrabers.tools.ui.Functions;


/**
 * Decodes the binary format in which Ableton Live stores objects, e.g. the table of contents of a
 * Live Pack. The format describes itself: a record starts with the definitions of the types which
 * its object uses - the names and the types of their fields - followed by the values of the
 * object. The elements of a list are followed by an empty type name. The number of elements which
 * is stored in front of them is not used, since it can be larger than the number of elements
 * which follow.
 *
 * @author Jürgen Moßgraber
 */
public class AbletonBinaryDecoder
{
    /** The identifier at the start of each record. */
    public static final int                RECORD_ID         = 0x78561EAB;

    private static final String            ERR_BROKEN        = "IDS_ADV_PACK_BROKEN";
    private static final String            ERR_UNKNOWN_TYPE  = "IDS_ADV_PACK_UNSUPPORTED_TYPE";

    private static final int               TYPE_BOOLEAN      = 0x10;
    private static final int               TYPE_INTEGER      = 0x11;
    private static final int               TYPE_STRING       = 0x14;
    private static final int               TYPE_LONG         = 0x18;
    /** The number of fields of a generic type, e.g. a list, which holds the objects of a type. */
    private static final int               GENERIC_TYPE      = 0xFFFFFFFF;
    /** Records before this version write an additional byte into a reference to a type. */
    private static final int               VERSION_SHORT_REF = 4;

    private final ByteBuffer               buffer;
    private final Map<String, List<Field>> types             = new HashMap<> ();
    private final Set<String>              genericTypes      = new HashSet<> ();
    private int                            version;


    /**
     * The type of a value: either one of the primitive types or a type with the given name.
     *
     * @param primitive The identifier of the primitive type or 0 if it is a named type
     * @param name The name of the type, empty for a primitive type
     */
    private record Type (int primitive, String name)
    {
        boolean isPrimitive ()
        {
            return this.primitive != 0;
        }
    }


    /**
     * The definition of a field of a type.
     *
     * @param name The name of the field
     * @param type The type of the field
     */
    private record Field (String name, Type type)
    {
        // Intentionally empty
    }


    /**
     * A decoded object: the name of its type and the values of its fields. A value is a Boolean, a
     * Long, a String, a decoded object or a list of decoded objects.
     *
     * @param type The name of the type of the object
     * @param fields The values of the fields of the object by their names
     */
    public record DecodedObject (String type, Map<String, Object> fields)
    {
        /**
         * Get the value of a text field.
         *
         * @param name The name of the field
         * @return The text, empty if the field is not present or has another type
         */
        public String getString (final String name)
        {
            return this.fields.get (name) instanceof final String value ? value : "";
        }


        /**
         * Get the value of a numeric field.
         *
         * @param name The name of the field
         * @return The value, 0 if the field is not present or has another type
         */
        public long getLong (final String name)
        {
            return this.fields.get (name) instanceof final Long value ? value.longValue () : 0;
        }


        /**
         * Get the value of a boolean field.
         *
         * @param name The name of the field
         * @return The value, false if the field is not present or has another type
         */
        public boolean getBoolean (final String name)
        {
            return this.fields.get (name) instanceof final Boolean value && value.booleanValue ();
        }


        /**
         * Get the value of a field which holds an object.
         *
         * @param name The name of the field
         * @return The object if the field is present and holds an object
         */
        public Optional<DecodedObject> getObject (final String name)
        {
            return this.fields.get (name) instanceof final DecodedObject value ? Optional.of (value) : Optional.empty ();
        }


        /**
         * Get the value of a field which holds a list of objects.
         *
         * @param name The name of the field
         * @return The objects, empty if the field is not present or holds no list
         */
        public List<DecodedObject> getObjects (final String name)
        {
            if (!(this.fields.get (name) instanceof final List<?> values))
                return Collections.emptyList ();
            final List<DecodedObject> objects = new ArrayList<> (values.size ());
            for (final Object value: values)
                if (value instanceof final DecodedObject object)
                    objects.add (object);
            return objects;
        }
    }


    /**
     * Constructor.
     *
     * @param data The data to decode
     */
    public AbletonBinaryDecoder (final byte [] data)
    {
        this.buffer = ByteBuffer.wrap (data).order (ByteOrder.LITTLE_ENDIAN);
    }


    /**
     * Decode all records which follow each other in the data.
     *
     * @return The objects of the records in the order in which they are stored
     * @throws IOException The data is not in the expected format
     */
    public List<DecodedObject> readRecords () throws IOException
    {
        final List<DecodedObject> records = new ArrayList<> ();
        try
        {
            while (this.buffer.remaining () >= 4 && this.buffer.getInt (this.buffer.position ()) == RECORD_ID)
                records.add (this.readRecord ());
        }
        catch (final BufferUnderflowException | IllegalArgumentException ex)
        {
            throw new IOException (this.createBrokenMessage (), ex);
        }
        return records;
    }


    /**
     * Decode one record: the definitions of the types which it uses and the object itself.
     *
     * @return The object of the record
     * @throws IOException The data is not in the expected format
     */
    private DecodedObject readRecord () throws IOException
    {
        this.buffer.getInt ();
        this.version = this.buffer.get () & 0xFF;
        // Unknown, differs between records
        this.buffer.getInt ();

        final Type rootType = this.readType ();
        final int numberOfDefinitions = this.buffer.getInt ();
        for (int i = 0; i < numberOfDefinitions; i++)
        {
            final Type type = this.readType ();
            if (type.isPrimitive () || type.name ().isEmpty ())
                throw new IOException (this.createBrokenMessage ());
            final int numberOfFields = this.buffer.getInt ();
            if (numberOfFields == GENERIC_TYPE)
            {
                this.genericTypes.add (type.name ());
                continue;
            }
            final List<Field> fields = new ArrayList<> (numberOfFields);
            for (int f = 0; f < numberOfFields; f++)
            {
                final String fieldName = this.readText ();
                fields.add (new Field (fieldName, this.readType ()));
            }
            this.types.put (type.name (), fields);
        }

        if (rootType.isPrimitive ())
            throw new IOException (this.createBrokenMessage ());
        return this.readObject (rootType.name ());
    }


    /**
     * Read the fields of an object of the type with the given name.
     *
     * @param typeName The name of the type
     * @return The object
     * @throws IOException The type is not defined
     */
    private DecodedObject readObject (final String typeName) throws IOException
    {
        final List<Field> fields = this.types.get (typeName);
        if (fields == null)
            throw new IOException (this.createBrokenMessage ());
        final Map<String, Object> values = new LinkedHashMap<> ();
        for (final Field field: fields)
            values.put (field.name (), this.readValue (field.type ()));
        return new DecodedObject (typeName, values);
    }


    /**
     * Read one value of the given type.
     *
     * @param type The type of the value
     * @return The value
     * @throws IOException The type is not supported
     */
    private Object readValue (final Type type) throws IOException
    {
        if (type.isPrimitive ())
        {
            switch (type.primitive ())
            {
                case TYPE_BOOLEAN:
                    return Boolean.valueOf (this.buffer.get () != 0);
                case TYPE_INTEGER:
                    return Long.valueOf (this.buffer.getInt () & 0xFFFFFFFFL);
                case TYPE_STRING:
                    return this.readText ();
                case TYPE_LONG:
                    return Long.valueOf (this.buffer.getLong ());
                default:
                    throw new IOException (Functions.getMessage (ERR_UNKNOWN_TYPE, String.format ("0x%02X", Integer.valueOf (type.primitive ()))));
            }
        }

        if (!this.genericTypes.contains (type.name ()))
            return this.readObject (type.name ());

        // A list: each element names its type and carries an index, the end is an empty name
        this.buffer.getInt ();
        final List<DecodedObject> elements = new ArrayList<> ();
        while (true)
        {
            final Type elementType = this.readType ();
            if (elementType.isPrimitive ())
                throw new IOException (this.createBrokenMessage ());
            if (elementType.name ().isEmpty ())
                return elements;
            this.buffer.getInt ();
            elements.add (this.readObject (elementType.name ()));
        }
    }


    /**
     * Read a reference to a type: the identifier of a primitive type or 0 followed by the name of
     * the type.
     *
     * @return The type
     * @throws IOException The reference is not supported
     */
    private Type readType () throws IOException
    {
        final int primitive = this.buffer.get () & 0xFF;
        if (primitive != 0)
            return new Type (primitive, "");

        // Older records store another byte in front of the name, which is always 0 in the table of
        // contents of a pack but not in other objects, whose format differs
        if (this.version < VERSION_SHORT_REF && this.buffer.get () != 0)
            throw new IOException (this.createBrokenMessage ());

        final byte [] name = new byte [this.buffer.get () & 0xFF];
        this.buffer.get (name);
        return new Type (0, new String (name, StandardCharsets.US_ASCII));
    }


    /**
     * Create the message which reports that the data could not be decoded at the current position.
     *
     * @return The message
     */
    private String createBrokenMessage ()
    {
        return Functions.getMessage (ERR_BROKEN, Integer.toString (this.buffer.position ()));
    }


    /**
     * Read a text: the number of characters followed by the characters in UTF-16.
     *
     * @return The text
     */
    private String readText ()
    {
        final int length = this.buffer.getInt ();
        if (length < 0 || length > this.buffer.remaining () / 2)
            throw new IllegalArgumentException ();
        final byte [] text = new byte [length * 2];
        this.buffer.get (text);
        return new String (text, StandardCharsets.UTF_16LE);
    }
}
