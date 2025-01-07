// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

import java.io.StringReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.tools.XMLUtils;


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
     * Constructor. Creates an empty sound info document.
     *
     * @param author The author of the 'sound'
     * @param categories The category of the 'sound'
     */
    public SoundinfoDocument (final String author, final String... categories)
    {
        this.author = author;
        if (categories != null)
            Collections.addAll (this.categories, categories);
    }


    /**
     * Constructor. Parses the sound info from a XML document.
     *
     * @param content The XML document as a string
     * @throws SAXException Could not parse the document
     */
    public SoundinfoDocument (final String content) throws SAXException
    {
        final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content.trim ())));
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
            for (final Element attributeElement: XMLUtils.getChildElementsByName (attributesElement, "attribute", false))
            {
                final String value = XMLUtils.read (attributeElement, "value");
                if (value != null && !value.isBlank () && !IGNORE_TAGS.contains (value))
                    this.categories.add (value);
            }
    }


    /**
     * Create the XML structure.
     *
     * @param name The name of the sound
     * @return The XML code
     */
    public String createDocument (final String name)
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n");
        sb.append ("<soundinfo version=\"400\">\n\n");
        sb.append ("  <properties>\n");
        sb.append ("    <name>").append (name).append ("</name>\n");
        sb.append ("    <author>").append (this.author).append ("</author>\n");
        sb.append ("  </properties>\n\n");
        sb.append ("  <attributes>\n");

        for (final String category: this.categories)
        {
            sb.append ("    <attribute>\n");
            sb.append ("      <value>").append (category).append ("</value>\n");
            sb.append ("    </attribute>\n");
        }

        sb.append ("  </attributes>\n\n");
        sb.append ("</soundinfo>\n");
        return sb.toString ();
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
