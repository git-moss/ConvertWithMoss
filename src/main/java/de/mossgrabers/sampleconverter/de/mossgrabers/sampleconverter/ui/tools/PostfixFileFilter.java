// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools;

import java.io.File;


/**
 * A convenience implementation of FileFilter that filters out all files except for those type
 * extensions that it knows about.
 * <p>
 * Extensions are of the type ".foo", which is typically found on Windows and Unix boxes, but not on
 * Macintosh. Case is ignored.
 * <p>
 * Example - create a new filter that filers out all files but GIF and JPG image files: <pre>
 *            JFileChooser chooser = new JFileChooser ();
 *            FileFilter filter = new FileFilter (
 *                          new String [] {&quot;gif&quot;, &quot;jpg&quot;}, &quot;JPEG &amp; GIF Images&quot;)
 *            chooser.addChoosableFileFilter(filter);
 *            chooser.showOpenDialog(this);
 * </pre>
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class PostfixFileFilter extends javax.swing.filechooser.FileFilter
{
    private String [] postfixes;
    private String    description;


    /**
     * Creates a file filter from the given string array and description. The endings will be
     * included in the description. Example: new FileFilter(String {"gif", "jpg"}, "GIF and JPG
     * Images");
     * <p>
     * Note that the "." before the extension must not be given!
     *
     * @param description The description of all extensions. If it starts with '@' the matching
     *            string is loaded from the properties file
     * @param endings The file extensions to accept
     */
    public PostfixFileFilter (final String description, final String... endings)
    {
        this (description, true, endings);
    }


    /**
     * Creates a file filter from the given string array and description. Example: new
     * FileFilter(String {"gif", "jpg"}, "GIF and JPG Images");
     * <p>
     * Note that the "." before the extension must not be given!
     *
     * @param description The description of all extensions. If it starts with '@' the matching
     *            string is loaded from the properties file
     * @param showEndings If true the endings are included in the description
     * @param endings The file extensions to accept
     */
    public PostfixFileFilter (final String description, final boolean showEndings, final String... endings)
    {
        this.postfixes = new String [endings.length];
        for (int i = 0; i < endings.length; i++)
            this.postfixes[i] = "(?i).*\\." + endings[i];

        final StringBuilder desc = new StringBuilder (Functions.getText (description));
        if (showEndings)
        {
            desc.append (" (");
            for (int i = 0; i < endings.length; i++)
            {
                if (i > 0)
                    desc.append (", ");
                desc.append ('.').append (endings[i]);
            }
            desc.append (')');
        }
        this.description = desc.toString ();
    }


    /**
     * Return true if this file should be shown in the directory pane, false if it shouldn't.
     * <p>
     * Files that begin with "." are ignored.
     *
     * @param f The file to check
     * @return True if accepted
     * @see PostfixFileFilter#accept
     */
    @Override
    public boolean accept (final File f)
    {
        if (f.isDirectory ())
            return true;

        final String name = f.getName ();
        for (final String element: this.postfixes)
        {
            if (name.matches (element))
                return true;
        }
        return false;
    }


    /**
     * Returns the human readable description of this filter. For example: "JPEG and GIF Image Files
     * (*.jpg, *.gif)"
     *
     * @return The human readable description of this filter
     * @see PostfixFileFilter#getDescription
     */
    @Override
    public String getDescription ()
    {
        return this.description;
    }
}
