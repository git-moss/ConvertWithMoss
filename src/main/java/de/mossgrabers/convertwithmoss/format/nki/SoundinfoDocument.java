// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

import de.mossgrabers.tools.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;


/**
 * Manages a Soundinfo XML document containing some metadata information about a NKI instrument.
 *
 * @author Jürgen Moßgraber
 */
public class SoundinfoDocument
{
    private static final Set<String> IGNORE_TAGS = new HashSet<> ();
    static
    {
        IGNORE_TAGS.add ("KontaktInstrument");
    }

    private String            name;
    private String            author;
    private final Set<String> categories = new HashSet<> ();


    /**
     * Constructor.
     *
     * @param author The author of the 'sound'
     * @param category The category of the 'sound'
     */
    public SoundinfoDocument (final String author, final String category)
    {
        this.author = author;
        if (category != null)
            this.categories.add (category);
    }


    /**
     * Constructor.
     *
     * @param content The XML document as a string
     * @throws SAXException Could not parse the document
     */
    public SoundinfoDocument (final String content) throws SAXException
    {
        final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
        final Element top = document.getDocumentElement ();

        // Read the properties
        final Element propertiesElement = XMLUtils.getChildElementByName (top, "properties");
        if (propertiesElement != null)
        {
            this.name = XMLUtils.read (propertiesElement, "name");
            this.author = XMLUtils.read (propertiesElement, "author");
        }

        // Read the attribute tags
        final Element attributesElement = XMLUtils.getChildElementByName (top, "attributes");
        if (attributesElement != null)
        {
            for (final Element attributeElement: XMLUtils.getChildElementsByName (attributesElement, "attribute", false))
            {
                final String value = XMLUtils.read (attributeElement, "value");
                if (value != null && !value.isBlank () && !IGNORE_TAGS.contains (value))
                    this.categories.add (value);
            }
        }
    }


    /**
     * Get the name.
     *
     * @return The name, might be null
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the author.
     *
     * @return The author, might be null
     */
    public String getAuthor ()
    {
        return this.author;
    }


    /**
     * Get the categories.
     *
     * @return The categories, never null but might be empty
     */
    public Set<String> getCategories ()
    {
        return this.categories;
    }
}
