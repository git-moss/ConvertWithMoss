// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.detector;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.ISampleMetadata;
import de.mossgrabers.sampleconverter.util.KeyMapping;

import java.io.File;
import java.util.List;


/**
 * A detected multi-sample source consisting of a couple of wave files in a folder.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class WavMultisampleSource implements IMultisampleSource
{
    private final File                        folder;
    private final List<List<ISampleMetadata>> sampleMetadata;
    private final KeyMapping                  keyMapping;
    private final String                      creator;
    private final String                      category;
    private final String []                   keywords;
    private final String []                   subPath;
    private final String                      name;


    /**
     * Constructor.
     *
     * @param folder The folder
     * @param subPath The names of the sub folders which contain the samples
     * @param sampleMetadata The sample file information in an ordered map
     * @param keyMapping
     * @param name The name of the multi-sample
     * @param creator The creator (author) of the multi sample
     * @param category The sound category of the multi-sample
     * @param keywords The keywords of the multi-sample
     */
    public WavMultisampleSource (final File folder, final String [] subPath, final List<List<ISampleMetadata>> sampleMetadata, final KeyMapping keyMapping, final String name, final String creator, final String category, final String [] keywords)
    {
        this.folder = folder;
        this.subPath = subPath;
        this.sampleMetadata = sampleMetadata;
        this.keyMapping = keyMapping;
        this.name = name;
        this.creator = creator;
        this.category = category;
        this.keywords = keywords;
    }


    /** {@inheritDoc} */
    @Override
    public File getFolder ()
    {
        return this.folder;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getSubPath ()
    {
        return this.subPath;
    }


    /** {@inheritDoc} */
    @Override
    public List<List<ISampleMetadata>> getSampleMetadata ()
    {
        return this.sampleMetadata;
    }


    /** {@inheritDoc} */
    @Override
    public KeyMapping getKeyMapping ()
    {
        return this.keyMapping;
    }


    /** {@inheritDoc} */
    @Override
    public String getName ()
    {
        return this.name;
    }


    /** {@inheritDoc} */
    @Override
    public String getCreator ()
    {
        return this.creator;
    }


    /** {@inheritDoc} */
    @Override
    public String getCategory ()
    {
        return this.category;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getKeywords ()
    {
        return this.keywords;
    }
}
