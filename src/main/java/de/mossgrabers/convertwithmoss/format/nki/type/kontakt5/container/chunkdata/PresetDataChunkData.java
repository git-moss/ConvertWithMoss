package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.container.chunkdata;

import de.mossgrabers.convertwithmoss.file.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;


/**
 * A chunk which contains the data of a preset.
 *
 * @author Jürgen Moßgraber
 */
public class PresetDataChunkData extends AbstractChunkData
{
    private String name;
    private String author;
    private int    icon;
    private String description;
    private String weblink;


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        super.read (in);

        // TODO TBC
        final int dictionaryID = StreamUtils.readUnsigned32 (in, false);
        // TODO TBC
        final int numberOfItems = StreamUtils.readUnsigned32 (in, false);

        final int sizeOfItem = StreamUtils.readUnsigned32 (in, false);
        // TODO TBC
        final int reference = StreamUtils.readUnsigned32 (in, false);

        final byte [] data = in.readNBytes (sizeOfItem);

        final int endPadding = StreamUtils.readUnsigned32 (in, false);
        if (endPadding != 0)
            throw new IOException ("No end padding");

        // TODO TBC
        final int checksum = StreamUtils.readUnsigned32 (in, false);

        if (in.available () != 0) // TODO
            throw new IOException ("Not all bytes read!");

        this.parseKontakt5Preset (data);

        // TODO remove
        // final byte [] data = in.readAllBytes ();
        // Files.write (new File ("C:\\Users\\mos\\Desktop\\preset-data.bin").toPath (), data);
    }


    public void parseKontakt5Preset (final byte [] data) throws IOException
    {
        final ByteArrayInputStream in = new ByteArrayInputStream (data);

        final int presetID = StreamUtils.readUnsigned16 (in, false);
        if (presetID != 0x28)
            throw new IOException ("Not a Kontakt preset");

        // TODO remove
        // final String fileID = UUID.randomUUID ().toString ();
        // Files.write (new File ("C:\\Users\\mos\\Desktop\\Parts\\" + fileID + "-preset-item-data-"
        // + index + ".bin").toPath (), itemData);

        final int block1Size = StreamUtils.readUnsigned32 (in, false);
        final byte [] block1Data = in.readNBytes (block1Size);
        this.readBlock1 (block1Data);

        final int block2Size = StreamUtils.readUnsigned32 (in, false);
        final byte [] block2Data = in.readNBytes (block2Size);
        // TODO parse 2nd block

        if (in.available () != 0) // TODO
            throw new IOException ("Not all bytes read!");
    }


    private void readBlock1 (final byte [] data) throws IOException
    {
        final ByteArrayInputStream in = new ByteArrayInputStream (data);

        final boolean readChunked = in.read () > 0;

        // Item version: 0x80
        final int itemVersion = StreamUtils.readUnsigned16 (in, false);

        switch (itemVersion)
        {
            case 0x80:
                // TODO ProgramDataV80::read(&mut reader)?;

                final String fileID = UUID.randomUUID ().toString ();
                Files.write (new File ("C:\\Users\\mos\\Desktop\\" + fileID + "-preset-item-data.bin").toPath (), data);
                return;
            // break;

            case 0xA5:
                // TODO ProgramDataVA5::read(&mut reader)?;
                break;

            // Found 0xA8 - 0xAF
            default:
                throw new IOException ("ProgramData not supported: " + Integer.toHexString (itemVersion));
        }

        // Unknown content
        final int blockLength = StreamUtils.readUnsigned32 (in, false);
        in.skipNBytes (blockLength);

        // Size of metadata section
        final int metadataSize = StreamUtils.readUnsigned32 (in, false);
        this.readMetadata (in.readNBytes (metadataSize));

        // Size of the patch block
        final int patchSize = StreamUtils.readUnsigned32 (in, false);
        this.readPatch (in.readNBytes (patchSize));

        if (in.available () != 0) // TODO
            throw new IOException ("Not all bytes read!");
    }


    private void readMetadata (final byte [] data) throws IOException
    {
        final ByteArrayInputStream in = new ByteArrayInputStream (data);

        // The name of the preset
        this.name = StreamUtils.readWithLengthUTF16 (in);

        // Unknown content
        in.skipNBytes (44);

        // The index of the icon of the preset
        this.icon = StreamUtils.readUnsigned32 (in, false);

        // The description of the preset
        this.description = StreamUtils.readWithLengthUTF16 (in);

        // The author of the preset
        this.author = StreamUtils.readWithLengthUTF16 (in);

        // The web link of the preset
        this.weblink = StreamUtils.readWithLengthUTF16 (in);
    }


    private void readPatch (final byte [] data)
    {
        // TODO Auto-generated method stub

        // TODO Files.write (new File ("C:\\Users\\mos\\Desktop\\preset-inner-data.bin").toPath (),
        // data);

    }
}
