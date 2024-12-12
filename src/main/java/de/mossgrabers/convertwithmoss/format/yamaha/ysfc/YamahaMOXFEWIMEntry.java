package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import de.mossgrabers.convertwithmoss.file.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class YamahaMOXFEWIMEntry extends YamahaYsfcEntry {

    @Override
    protected byte[] createContent() throws IOException {
        final ByteArrayOutputStream contentStream = new ByteArrayOutputStream ();

        StreamUtils.padBytes(contentStream, 4);

        // Size of the item corresponding to this entry
        StreamUtils.writeUnsigned32 (contentStream, this.correspondingDataSize, true);

        StreamUtils.padBytes(contentStream, 4);

        // Offset of the item chunk within the data block
        StreamUtils.writeUnsigned32 (contentStream, this.correspondingDataOffset, true);
        // Type specific - e.g. Program number
        StreamUtils.writeUnsigned32 (contentStream, this.specificValue, true);

        // Flags - type specific
//        contentStream.write (this.flags);

        // ID of the entry object for ordering
//        StreamUtils.writeUnsigned32 (contentStream, this.entryID, true);

        StreamUtils.padBytes(contentStream, 2);

        StreamUtils.writeNullTerminatedASCII (contentStream, this.itemName);
        StreamUtils.writeNullTerminatedASCII (contentStream, this.itemTitle);

        // Optional additional data - type specific, only used by EPFM
        contentStream.write (this.additionalData);

        // Finally, write the chunk
        final byte [] content = contentStream.toByteArray ();
        this.length = content.length;
        return content;
    }
}
