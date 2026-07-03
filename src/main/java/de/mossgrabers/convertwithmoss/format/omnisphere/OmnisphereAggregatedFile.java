// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.omnisphere;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.tools.XMLUtils;


/**
 * Reads/writes a container file that starts with a FileSystem XML header followed by binary file
 * pay-loads. XML entries are parsed into Document objects. WAV entries are stored as byte[].
 */
public class OmnisphereAggregatedFile
{
    private static final String        FILE_SYSTEM_END_TAG = "</FileSystem>\n";
    private static final Charset       DEFAULT_ENCODING    = StandardCharsets.ISO_8859_1;
    private static final String        NONSENSE_DOCTYPE    = "<!DOCTYPE s1 SYSTEM \"sbk:/style/dtd/document.dtd\">";

    private final Map<String, byte []> files               = new LinkedHashMap<> ();


    /**
     * Reads the aggregate file and returns all contained files.
     *
     * @param file The aggregate file to read
     * @throws IOException Could not read the file
     */
    public void read (final File file) throws IOException
    {
        try (final RandomAccessFile raf = new RandomAccessFile (file, "r"))
        {
            // Read the FileSystem XML header first
            final Document fileSystemDoc = readFileSystemXml (raf);

            // Current file pointer is directly after the XML header
            final long dataStart = raf.getFilePointer ();
            final Element root = fileSystemDoc.getDocumentElement ();
            this.processNodes (root, raf, dataStart);
        }
    }


    /**
     * Creates a bundle file from a map.
     *
     * @param file The aggregate file to write
     * @return The size of the DB file without the directory header
     * @throws IOException Could not write the aggregated file
     */
    public int write (final File file) throws IOException
    {
        byte [] fileSystemXml;
        try
        {
            fileSystemXml = this.createFileSystemXml ();
        }
        catch (final ParserConfigurationException ex)
        {
            throw new IOException (ex);
        }

        try (final ByteArrayOutputStream out = new ByteArrayOutputStream ())
        {
            out.write (fileSystemXml);
            out.write (0x0A);

            for (final byte [] data: this.files.values ())
                out.write (data);

            final byte [] data = out.toByteArray ();
            Files.write (file.toPath (), data);

            return data.length - fileSystemXml.length;
        }
    }


    /**
     * Adds an XML Document to the aggregate file.
     *
     * @param fileName The name of the file
     * @param document The XML document to add
     * @throws TransformerException Could not transform the document to XML code
     */
    public void addXmlFile (final String fileName, final Document document) throws TransformerException
    {
        this.addXmlFile (fileName, document, null);
    }


    /**
     * Adds an XML Document to the aggregate file.
     *
     * @param fileName The name of the file
     * @param document The XML document to add
     * @param order The order of the attributes
     * @throws TransformerException Could not transform the document to XML code
     */
    public void addXmlFile (final String fileName, final Document document, final Map<String, List<String>> order) throws TransformerException
    {
        final String documentToXmlString = OmnisphereXmlUtil.documentToXmlString (document, false, order);
        this.addFile (fileName, documentToXmlString.getBytes (StandardCharsets.ISO_8859_1));
    }


    /**
     * Adds a file to the aggregate file.
     *
     * @param fileName The name of the file
     * @param data The data of the file to add
     */
    public void addFile (final String fileName, final byte [] data)
    {
        this.files.put (fileName, data);
    }


    /**
     * Get a XML file and parse it as a document.
     *
     * @param fileName The name of the XML file
     * @return The parsed document or empty if it does not exist
     * @throws IOException Could not parse the file
     */
    public Optional<Document> getXmlFile (final String fileName) throws IOException
    {
        final byte [] data = this.files.get (fileName);
        return data == null ? Optional.empty () : Optional.of (parseXml (new String (data, DEFAULT_ENCODING)));
    }


    /**
     * Get a all XML files with a specific root tag parsed as a documents.
     *
     * @param rootTag The root tag to look for
     * @return The parsed documents, the key is their filename
     * @throws IOException Could not parse the file
     */
    public Map<String, Document> getXmlFiles (final String rootTag) throws IOException
    {
        final Map<String, Document> results = new HashMap<> ();
        for (final Map.Entry<String, byte []> e: this.files.entrySet ())
        {
            final String filename = e.getKey ();
            if (!filename.toLowerCase ().endsWith (".xml"))
                continue;
            final byte [] data = this.files.get (filename);
            if (data == null)
                continue;
            final String xmlCode = new String (data, DEFAULT_ENCODING).trim ();
            final Document document = parseXml (xmlCode);

            final Element top = document.getDocumentElement ();
            if (top.getNodeName ().equals (rootTag))
                results.put (filename, document);
        }
        return results;
    }


    /**
     * Get all WAV files.
     *
     * @return The WAV files
     */
    public Map<String, byte []> getWavFiles ()
    {
        final Map<String, byte []> results = new HashMap<> ();
        for (final Map.Entry<String, byte []> e: this.files.entrySet ())
        {
            final String filename = e.getKey ();
            if (!filename.toLowerCase ().endsWith (".wav"))
                continue;
            final byte [] data = this.files.get (filename);
            if (data != null)
                results.put (filename, data);
        }
        return results;
    }


    /**
     * Builds the FileSystem XML header.
     *
     * @return The XML document as bytes
     * @throws ParserConfigurationException Cold not configure the XML parser
     */
    private byte [] createFileSystemXml () throws ParserConfigurationException
    {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance ();
        final DocumentBuilder builder = factory.newDocumentBuilder ();

        final Document doc = builder.newDocument ();

        final Element root = doc.createElement ("FileSystem");
        doc.appendChild (root);

        final Element directoryElement = doc.createElement ("DIR");

        directoryElement.setAttribute ("name", "Pitch 0-127");

        long offset = 0;

        for (final Map.Entry<String, byte []> entry: this.files.entrySet ())
        {
            final Element file = doc.createElement ("FILE");
            final String filename = entry.getKey ();
            file.setAttribute ("name", filename);
            file.setAttribute ("offset", String.valueOf (offset));
            file.setAttribute ("size", String.valueOf (entry.getValue ().length));

            if ("HitBundle.xml".equals (filename) || "Layer.xml".equals (filename))
            {
                if ("HitBundle.xml".equals (filename))
                    root.appendChild (directoryElement);
                directoryElement.appendChild (file);
            }
            else
                root.appendChild (file);

            offset += entry.getValue ().length;
        }

        return OmnisphereXmlUtil.documentToXmlString (doc, true, null).trim ().getBytes (StandardCharsets.ISO_8859_1);
    }


    /**
     * Reads the XML header from the beginning of the file until </FileSystem>.
     *
     * @param raf The random access file
     * @return The parsed XML document
     * @throws IOException Could not read the document
     */
    private static Document readFileSystemXml (final RandomAccessFile raf) throws IOException
    {
        final StringBuilder sb = new StringBuilder ();
        while (true)
        {
            final int b = raf.read ();
            if (b == -1)
                throw new EOFException ("Unexpected end of file while reading FileSystem XML.");

            sb.append ((char) b);
            if (sb.indexOf (FILE_SYSTEM_END_TAG) >= 0)
                break;
        }

        return parseXml (sb.toString ());
    }


    /**
     * Recursively processes FILE and DIR nodes.
     *
     * @param parent The parent element of the FILE/DIR
     * @param raf The random access file
     * @param dataStart The start of the files section
     * @throws IOException Could not read the files
     */
    private void processNodes (final Element parent, final RandomAccessFile raf, final long dataStart) throws IOException
    {
        final NodeList children = parent.getChildNodes ();

        for (int i = 0; i < children.getLength (); i++)
        {
            final Node node = children.item (i);

            if (node.getNodeType () != Node.ELEMENT_NODE)
                continue;

            final Element element = (Element) node;
            final String tag = element.getTagName ();

            if ("FILE".equals (tag))
                this.readFileEntry (element, raf, dataStart);
            else if ("DIR".equals (tag))
                this.processNodes (element, raf, dataStart);
        }
    }


    /**
     * Reads a FILE entry and stores it in the result map.
     *
     * @param fileElement The FILE element to read
     * @param raf The random access file
     * @param dataStart The start of the files section
     * @throws IOException Could not read the file
     */
    private void readFileEntry (final Element fileElement, final RandomAccessFile raf, final long dataStart) throws IOException
    {
        final String name = fileElement.getAttribute ("name");
        final long offset = Long.parseLong (fileElement.getAttribute ("offset"));
        final int size = Integer.parseInt (fileElement.getAttribute ("size"));

        raf.seek (dataStart + offset);
        final byte [] data = new byte [size];
        raf.readFully (data);
        this.files.put (name, data);
    }


    /**
     * Parses XML bytes into a DOM Document.
     *
     * @param xmlCode The XML code to parse
     * @return The parsed document
     *
     * @throws IOException Could not parse the document
     */
    public static Document parseXml (final String xmlCode) throws IOException
    {
        final String cleanedUpXmlCode = xmlCode.replace (NONSENSE_DOCTYPE, "");

        try
        {
            return XMLUtils.parseDocument (new InputSource (new StringReader (cleanedUpXmlCode)));
        }
        catch (final SAXException ex)
        {
            throw new IOException (ex);
        }
    }
}