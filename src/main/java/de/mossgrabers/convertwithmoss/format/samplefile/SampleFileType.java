package de.mossgrabers.convertwithmoss.format.samplefile;

import java.io.IOException;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.IFileBasedSampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;


/**
 * Plug-in interface for detecting sample files of a specific format, e.g. WAV.
 */
public interface SampleFileType
{
    /**
     * Get the name to display for this type.
     * 
     * @return The name, e.g. "WAV"
     */
    String getName ();


    /**
     * Get the endings to look for.
     * 
     * @return The endings, e.g. '.wav'
     */
    String [] getFileEndings ();


    /**
     * Checks if the file has data about an instrument zone (e.g. key/velocity range and loops).
     *
     * @param sampleData The sample files to check
     * @return True if instrument data is available
     */
    boolean hasInstrumentData (final List<IFileBasedSampleData> sampleData);


    /**
     * Set the zone data (e.g. key/velocity range and loops) from the given file based sample data.
     *
     * @param zone The zone to fill
     * @param sampleData The source data
     * @throws IOException Could not fill the data
     */
    void fillInstrumentData (final ISampleZone zone, final IFileBasedSampleData sampleData) throws IOException;
}
