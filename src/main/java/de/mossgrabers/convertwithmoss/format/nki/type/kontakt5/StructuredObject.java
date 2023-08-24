// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import de.mossgrabers.convertwithmoss.file.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * A structured object.
 *
 * @author Jürgen Moßgraber
 */
public class StructuredObject
{
    private byte []                publicData;
    private byte []                privateData;
    private List<StructuredObject> children = new ArrayList<> ();


    public void parse (final InputStream in) throws IOException
    {
        final int id = StreamUtils.readUnsigned16 (in, false);
        final int objectSize = StreamUtils.readUnsigned32 (in, false);

        int read = in.read ();
        final boolean isDataStructured = read > 0;
        if (!isDataStructured)
        {
            // TODO why -1?
            if (objectSize > 0)
                this.publicData = in.readNBytes (objectSize - 1);

            // if (in.available () > 0)
            // throw new IOException ("not fully read: " + in.available ());

            return;
        }

        final int publicDataVersion = StreamUtils.readUnsigned16 (in, false);

        final int privateDataSize = StreamUtils.readUnsigned32 (in, false);
        this.privateData = in.readNBytes (privateDataSize);

        final int publicDataSize = StreamUtils.readUnsigned32 (in, false);
        this.publicData = in.readNBytes (publicDataSize);

        final int sizeChildren = StreamUtils.readUnsigned32 (in, false);
        final byte [] childrenData = in.readNBytes (sizeChildren);

        final ByteArrayInputStream inChildren = new ByteArrayInputStream (childrenData);
        int available = inChildren.available ();
        while (available > 0)
        {
            final StructuredObject object = new StructuredObject ();
            object.parse (inChildren);
            this.children.add (object);

            available = inChildren.available ();
        }
    }
}
