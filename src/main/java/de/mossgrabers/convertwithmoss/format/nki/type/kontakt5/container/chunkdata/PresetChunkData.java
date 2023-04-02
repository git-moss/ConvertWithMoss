package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.container.chunkdata;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;


/**
 * The root chunk of all containers.
 *
 * @author Jürgen Moßgraber
 */
public class PresetChunkData extends AbstractChunkData
{
    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        super.read (in);

        final byte [] data = in.readAllBytes ();

        // TODO remove
        Files.write (new File ("C:/Users/mos/Desktop/PresetData.bin").toPath (), data);

        // sound-info-sound-info-version
        // sound-info-major-version
        // sound-info-minor-version
        // sound-info-sound-name
        // sound-info-author
        // sound-info-vendor
        // sound-info-comment
        // sound-info-icon
        // sound-info-tempo
        // sound-info-cpu-usage
        // sound-info-mem-usage
        // sound-info-load-time
        // sound-info-num-inputs
        // sound-info-num-outputs

    }
}
