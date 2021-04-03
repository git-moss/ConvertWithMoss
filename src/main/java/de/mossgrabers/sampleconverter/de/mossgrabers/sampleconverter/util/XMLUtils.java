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
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;


/**
 * Helper functions for dealing with XML files.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class XMLUtils
{
    private static final DocumentBuilderFactory NON_VALIDATING_FACTORY = DocumentBuilderFactory.newInstance ();
    private static final Properties             TRANSFORM_PROPERTIES   = new Properties ();
    private static DocumentBuilder              documentBuilder;
    private static ParserConfigurationException parseConfException;

    static
    {
        TRANSFORM_PROPERTIES.setProperty (OutputKeys.METHOD, "xml");
        TRANSFORM_PROPERTIES.setProperty (OutputKeys.ENCODING, "UTF-8");
        TRANSFORM_PROPERTIES.setProperty (OutputKeys.INDENT, "yes");
        // Forces newline, if standalone attribute is omitted
        TRANSFORM_PROPERTIES.setProperty (OutputKeys.DOCTYPE_PUBLIC, "");

        try
        {
            NON_VALIDATING_FACTORY.setValidating (false);
            NON_VALIDATING_FACTORY.setNamespaceAware (true);
            // Prevent external resource access from XML document
            NON_VALIDATING_FACTORY.setAttribute (XMLConstants.ACCESS_EXTERNAL_DTD, "");
            NON_VALIDATING_FACTORY.setAttribute (XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            documentBuilder = NON_VALIDATING_FACTORY.newDocumentBuilder ();
        }
        catch (final ParserConfigurationException ex)
        {
            parseConfException = ex;
            documentBuilder = null;
        }
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
        if (parseConfException != null)
            throw new SAXException (parseConfException);
        try
        {
            return documentBuilder.parse (isource);
        }
        catch (final IOException exception)
        {
            throw new SAXException (exception);
        }
    }


    /**
     * Creates a new XML document. Does not validate against the XML schema.
     *
     * @throws ParserConfigurationException Could not instantiate the XML parser/writer
     * @return The new XML document
     */
    public static Document newDocument () throws ParserConfigurationException
    {
        if (parseConfException != null)
            throw parseConfException;
        return documentBuilder.newDocument ();
    }


    /**
     * Returns the sub-node of a node with the name 'name' or null if not found.
     *
     * @param parent The parent node of the sub-node to lookup
     * @param name The tag-name of the sub-node
     * @return The sub-node or null
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
     * Returns the sub-element of a node with the name 'name' or null if not found.
     *
     * @param parent The parent node of the sub-element to lookup
     * @param name The tag-name of the sub-element
     * @return The sub-element or null
     */
    public static Element getChildElementByName (final Node parent, final String name)
    {
        final Node child = getChildByName (parent, name);
        return child instanceof Element ? (Element) child : null;
    }


    /**
     * Returns the text content of a sub-element of a node with the name 'name' or null if not
     * found.
     *
     * @param parent The parent node of the sub-element to lookup
     * @param name The tag-name of the sub-element
     * @return The sub-elements' content or null
     */
    public static String getChildElementContent (final Node parent, final String name)
    {
        final Element contentElement = XMLUtils.getChildElementByName (parent, name);
        return contentElement == null ? "" : XMLUtils.readTextContent (contentElement);
    }


    /**
     * Returns the sub-nodes of a node with the name 'name'.
     *
     * @param parent The parent node of the sub-node to lookup
     * @param name The tag-name of the sub-nodes
     * @return The sub-nodes or an empty array
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
     * Returns the sub-elements of a node.
     *
     * @param parent The parent node of the sub-element to lookup
     * @return The sub-elements or an empty array if none is found
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
     * Returns the sub-elements of a node with the name 'name'.
     *
     * @param parent The parent node of the sub-element to lookup
     * @param name The tag-name of the sub-elements
     * @return The sub-elements or an empty array if none is found
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


    /**
     * Get a boolean attribute (false/true) from an element.
     *
     * @param element The element
     * @param attributeName The name of the attribute from which to get the value
     * @param defaultValue If the attribute is not present or it does not contain a valid boolean
     *            this default value is returned
     * @return The value
     */
    public static boolean getBooleanAttribute (final Element element, final String attributeName, final boolean defaultValue)
    {
        final String attribute = element.getAttribute (attributeName);
        return attribute == null ? defaultValue : Boolean.parseBoolean (attribute);
    }


    /**
     * Adds a child element with a text content.
     *
     * @param document The document to create the element
     * @param parentElement The parent element where to add the new text element
     * @param elementName The name of the text element
     * @param text The text content of the element
     */
    public static void addTextElement (final Document document, final Element parentElement, final String elementName, final String text)
    {
        addElement (document, parentElement, elementName).setTextContent (text);
    }


    /**
     * Adds a child element.
     *
     * @param document The document to create the element
     * @param parentElement The parent element where to add the new element
     * @param elementName The name of the element
     * @return The added element
     */
    public static Element addElement (final Document document, final Element parentElement, final String elementName)
    {
        final Element childElement = document.createElement (elementName);
        parentElement.appendChild (childElement);
        return childElement;
    }


    /**
     * Set an integer attribute on an element.
     *
     * @param element The element
     * @param attributeName The name of the attribute
     * @param value The value to set
     */
    public static void setIntegerAttribute (final Element element, final String attributeName, final int value)
    {
        element.setAttribute (attributeName, Integer.toString (value));
    }


    /**
     * Set a double attribute on an element.
     *
     * @param element The element
     * @param attributeName The name of the attribute
     * @param value The value to set
     * @param fractions The number of fractions to format
     */
    public static void setDoubleAttribute (final Element element, final String attributeName, final double value, final int fractions)
    {
        final String formatPattern = "%." + fractions + "f";
        element.setAttribute (attributeName, String.format (Locale.US, formatPattern, Double.valueOf (value)));
    }


    /**
     * Set a boolean attribute on an element.
     *
     * @param element The element
     * @param attributeName The name of the attribute
     * @param value The value to set
     */
    public static void setBooleanAttribute (final Element element, final String attributeName, final boolean value)
    {
        element.setAttribute (attributeName, Boolean.toString (value));
    }


    /**
     * Formats the XMl document into a string.
     *
     * @param document The XML document
     * @return The created text
     * @throws TransformerException Could not transform the document
     */
    public static String toString (final Document document) throws TransformerException
    {
        final TransformerFactory factory = TransformerFactory.newInstance ();
        factory.setAttribute (XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute (XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        final Transformer transformer = factory.newTransformer ();
        transformer.setOutputProperties (TRANSFORM_PROPERTIES);
        final Writer writer = new StringWriter ();
        transformer.transform (new DOMSource (document), new StreamResult (writer));
        return writer.toString ();
    }
}
