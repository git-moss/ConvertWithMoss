// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Helper functions for dealing with XML files.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class XMLUtils
{
    private static final DocumentBuilderFactory NON_VALIDATING_FACTORY = DocumentBuilderFactory.newInstance ();

    static
    {
        NON_VALIDATING_FACTORY.setValidating (false);
        NON_VALIDATING_FACTORY.setNamespaceAware (true);
    }


    /**
     * Private due helper class.
     */
    private XMLUtils ()
    {
        // Intentionally empty
    }


    /**
     * Parses the given input source. Does not validate against the XML schema.
     *
     * @param isource The input source from which to parse the XML document
     * @return The parsed document
     * @throws SAXException Could not read the document or not parse the XML
     */
    public static Document parseDocument (final InputSource isource) throws SAXException
    {
        try
        {
            // Prevent external resource access from XML document
            NON_VALIDATING_FACTORY.setAttribute (XMLConstants.ACCESS_EXTERNAL_DTD, "");
            NON_VALIDATING_FACTORY.setAttribute (XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            final DocumentBuilder builder = NON_VALIDATING_FACTORY.newDocumentBuilder ();
            return builder.parse (isource);
        }
        catch (final IOException | ParserConfigurationException exc)
        {
            throw new SAXException (exc);
        }
    }


    /**
     * Returns the subnode of a node with the name 'name' or null if not found.
     *
     * @param parent The parent node of the subnode to lookup
     * @param name The tag-name of the subnode
     * @return The subnode or null
     */
    public static Node getChildByName (final Node parent, final String name)
    {
        NodeList list = null;
        if (parent instanceof Element)
            list = ((Element) parent).getElementsByTagName (name);
        else if (parent instanceof Document)
            list = ((Document) parent).getElementsByTagName (name);
        return list == null || list.getLength () == 0 ? null : list.item (0);
    }


    /**
     * Returns the subelement of a node with the name 'name' or null if not found.
     *
     * @param parent The parent node of the subelement to lookup
     * @param name The tag-name of the subelement
     * @return The subelement or null
     */
    public static Element getChildElementByName (final Node parent, final String name)
    {
        final Node child = getChildByName (parent, name);
        return child instanceof Element ? (Element) child : null;
    }


    /**
     * Returns the text content of a subelement of a node with the name 'name' or null if not found.
     *
     * @param parent The parent node of the subelement to lookup
     * @param name The tag-name of the subelement
     * @return The subelements' content or null
     */
    public static String getChildElementContent (final Node parent, final String name)
    {
        final Element contentElement = XMLUtils.getChildElementByName (parent, name);
        return contentElement == null ? "" : XMLUtils.readTextContent (contentElement);
    }


    /**
     * Returns the subnodes of a node with the name 'name'.
     *
     * @param parent The parent node of the subnode to lookup
     * @param name The tag-name of the subnodes
     * @return The subnodes or an empty array
     */
    public static Node [] getChildrenByName (final Node parent, final String name)
    {
        if (!(parent instanceof Element))
            return new Node [0];
        final NodeList list = ((Element) parent).getElementsByTagName (name);
        final int size = list.getLength ();
        final Node [] children = new Node [size];
        for (int i = 0; i < size; i++)
            children[i] = list.item (i);
        return children;
    }


    /**
     * Returns the subelements of a node.
     *
     * @param parent The parent node of the subelement to lookup
     * @return The subelements or an empty array if none is found
     */
    public static Element [] getChildElements (final Node parent)
    {
        final NodeList list = parent.getChildNodes ();
        final int size = list.getLength ();
        final List<Element> children = new ArrayList<> (size);
        for (int i = 0; i < size; i++)
        {
            final Node item = list.item (i);
            if (item instanceof Element)
                children.add ((Element) item);
        }
        return children.toArray (new Element [children.size ()]);
    }


    /**
     * Returns the subelements of a node with the name 'name'.
     *
     * @param parent The parent node of the subelement to lookup
     * @param name The tag-name of the subelements
     * @return The subelements or an empty array if none is found
     */
    public static Element [] getChildElementsByName (final Node parent, final String name)
    {
        if (!(parent instanceof Element))
            return new Element [0];
        final NodeList list = ((Element) parent).getElementsByTagName (name);
        final int size = list.getLength ();
        final List<Element> children = new ArrayList<> (size);
        for (int i = 0; i < size; i++)
        {
            final Node item = list.item (i);
            if (item instanceof Element)
                children.add ((Element) item);
        }
        return children.toArray (new Element [children.size ()]);
    }


    /**
     * Reads a string value from a XML attribute.
     *
     * @param parent The XML node to which the attribute belongs
     * @param name The name of the XML attribute
     * @return The attributes value or null if not found
     */
    public static String read (final Node parent, final String name)
    {
        final Node node = getChildByName (parent, name);
        return node == null ? null : readTextContent (node);
    }


    /**
     * Reads the text content of a node. If the node has no children null is returned. If it
     * contains several CDATA sections the content of all sections are concatenated and returned. If
     * it contains only one text the trimmed result is returned.
     *
     * @param node The node from which to get the text content
     * @return The text or an empty string
     */
    public static String readTextContent (final Node node)
    {
        final Node first = node.getFirstChild ();
        if (first == null)
            return "";

        String content = first.getNodeValue ();
        if (content == null)
            content = "";
        final NodeList list = node.getChildNodes ();
        // If there is more than one node, take the data of the node
        // which is a CDATA section. If there is no CDATA section use
        // the first text node.
        final StringBuilder builder = new StringBuilder ();
        for (int i = 0; i < list.getLength (); i++)
        {
            if (list.item (i).getNodeType () == Node.CDATA_SECTION_NODE)
                builder.append (list.item (i).getNodeValue ());
        }

        // !!! This is not nice, but the JAXP parser seems to add also
        // the returns that surround the CDATA section to the data
        // in the CDATA section. !!!
        return builder.length () > 0 ? builder.toString () : content.trim ();
    }


    /**
     * Get an integer attribute from an element.
     *
     * @param element The element
     * @param attributeName The name of the attribute from which to get the value
     * @param defaultValue If the attribute is not present or it does not contain a valid integer
     *            this default value is returned
     * @return The value
     */
    public static int getIntegerAttribute (final Element element, final String attributeName, final int defaultValue)
    {
        final String attribute = element.getAttribute (attributeName);
        if (attribute == null)
            return defaultValue;
        try
        {
            return Integer.parseInt (attribute);
        }
        catch (final NumberFormatException ex)
        {
            return defaultValue;
        }
    }


    /**
     * Get a double attribute from an element.
     *
     * @param element The element
     * @param attributeName The name of the attribute from which to get the value
     * @param defaultValue If the attribute is not present or it does not contain a valid double
     *            this default value is returned
     * @return The value
     */
    public static double getDoubleAttribute (final Element element, final String attributeName, final double defaultValue)
    {
        final String attribute = element.getAttribute (attributeName);
        if (attribute == null)
            return defaultValue;
        try
        {
            return Double.parseDouble (attribute);
        }
        catch (final NumberFormatException ex)
        {
            return defaultValue;
        }
    }
}
