// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.omnisphere;

import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 * Replicates the fake XML format used by Omnisphere. E.g. fixed order of attributes, a non-existing
 * DTD and weird spacings.
 *
 * @author Jürgen Moßgraber
 */
public class OmnisphereXmlUtil
{
    private final static String XML_HEADER = "<?xml version=\"1.0\"  encoding=\"ISO-8859-1\"  standalone=\"no\"?>\n<!DOCTYPE s1 SYSTEM \"sbk:/style/dtd/document.dtd\">\n";


    /**
     * Creates the "XML" code.
     * 
     * @param document The document for which to create the code
     * @param isDirectoryDocument True if it is the main DB directory, which omits the header,
     *            spacing and closing tags
     * @param order Helper for enforcing the order of attributes
     * @return The generated "XML" code
     */
    public static String documentToXmlString (final Document document, final boolean isDirectoryDocument, final Map<String, List<String>> order)
    {
        if (document == null || document.getDocumentElement () == null)
            return "";

        final StringBuilder sb = new StringBuilder ();
        if (!isDirectoryDocument)
            sb.append (XML_HEADER);
        appendNode (document.getDocumentElement (), sb, !isDirectoryDocument, !isDirectoryDocument, order);
        return sb.toString () + " ";
    }


    private static void appendNode (final Node node, final StringBuilder sb, final boolean addSpaces, final boolean addExplicitClosingTag, final Map<String, List<String>> order)
    {
        if (!(node instanceof final Element element))
            return;

        // Opening tag
        sb.append ("<").append (element.getTagName ());
        createAttributes (sb, addSpaces, element, order);
        if (addSpaces)
            sb.append (" ");

        final NodeList children = element.getChildNodes ();
        final boolean hasChildren = hasChildren (children);
        if (hasChildren || addExplicitClosingTag)
            sb.append (">\n");
        else
            sb.append ("/>\n");

        // Content
        for (int i = 0; i < children.getLength (); i++)
        {
            final Node child = children.item (i);
            switch (child.getNodeType ())
            {
                case Node.ELEMENT_NODE:
                    appendNode (child, sb, addSpaces, addExplicitClosingTag, order);
                    break;

                case Node.TEXT_NODE:
                    final String text = child.getTextContent ().trim ();
                    if (!text.isEmpty ())
                        sb.append (escapeXml (text));
                    break;

                default:
                    // Ignore
                    break;
            }
        }

        // Closing tag
        if (hasChildren || addExplicitClosingTag)
            sb.append ("</").append (element.getTagName ()).append (">\n");
    }


    private static void createAttributes (final StringBuilder sb, final boolean addSpaces, final Element element, final Map<String, List<String>> order)
    {
        final NamedNodeMap attributes = element.getAttributes ();

        List<String> orderList = null;
        if (order != null)
            orderList = order.get (element.getTagName ());

        if (orderList == null)
            for (int i = 0; i < attributes.getLength (); i++)
                addAttribute (sb, addSpaces, i, attributes.item (i));
        else
            for (int i = 0; i < orderList.size (); i++)
                addAttribute (sb, addSpaces, i, attributes.getNamedItem (orderList.get (i)));
    }


    private static void addAttribute (final StringBuilder sb, final boolean addSpaces, final int pos, final Node attr)
    {
        sb.append (pos == 0 || !addSpaces ? " " : "  ");
        sb.append (attr.getNodeName ()).append ("=\"").append (escapeXml (attr.getNodeValue ())).append ("\"");
    }


    private static boolean hasChildren (final NodeList children)
    {
        for (int i = 0; i < children.getLength (); i++)
        {
            final Node child = children.item (i);
            final short nodeType = child.getNodeType ();
            if ((nodeType == Node.ELEMENT_NODE) || (nodeType == Node.TEXT_NODE && !child.getTextContent ().isBlank ()))
                return true;
        }
        return false;
    }


    private static String escapeXml (final String value)
    {
        return value.replace ("&", "&amp;").replace ("<", "&lt;").replace (">", "&gt;").replace ("\"", "&quot;").replace ("'", "&apos;");
    }
}