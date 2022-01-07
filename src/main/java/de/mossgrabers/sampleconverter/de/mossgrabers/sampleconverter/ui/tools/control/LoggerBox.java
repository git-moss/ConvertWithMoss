// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools.control;

import de.mossgrabers.sampleconverter.util.StringUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import javafx.application.Platform;
import javafx.scene.effect.BlendMode;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.PrintWriter;
import java.io.StringWriter;


/**
 * A window for logging information and error messages. Uses a web view component.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class LoggerBox
{
    private static final String EMPTY_DOCUMENT = """
            <html>
                <head>
                    <meta charset=utf-8>
                    <style>
                    DIV { font-family: "Lucida Console", "Courier New", monospace; font-size: 16pt; }
                    .error { color: red; }
                    </style>
                </head>
                <body>
                    <div id="content"></div>
                </body>
            </html>
            """;

    private final WebView       webView        = new WebView ();
    private final WebEngine     engine;


    /**
     * Constructor.
     */
    public LoggerBox ()
    {
        this.engine = this.webView.getEngine ();
        this.engine.loadContent (EMPTY_DOCUMENT);
    }


    /**
     * Set the background light or dark.
     *
     * @param enable Enable dark
     */
    public void setDarkmode (final boolean enable)
    {
        this.webView.setBlendMode (enable ? BlendMode.OVERLAY : BlendMode.LIGHTEN);
    }


    /**
     * Display a notification.
     *
     * @param message The message to display
     */
    public void notify (final String message)
    {
        this.append (message, false);
    }


    /**
     * Display an error message.
     *
     * @param message The message to display
     */
    public void notifyError (final String message)
    {
        this.append (message, true);
    }


    /**
     * Display an error notification.
     *
     * @param message The message to display
     * @param throwable The throwable to log
     */
    public void notifyError (final String message, final Throwable throwable)
    {
        final StringBuilder sb = new StringBuilder (message).append ('\n');
        final StringWriter sw = new StringWriter ();
        final PrintWriter pw = new PrintWriter (sw);
        throwable.printStackTrace (pw);
        sb.append (sw.toString ()).append ('\n');
        this.append (sb.toString (), true);
    }


    /**
     * Appends the text to the result text area.
     *
     * @param text The text to append
     * @param isError If true the text is highlighted in red
     */
    private void append (final String text, final boolean isError)
    {
        Platform.runLater ( () -> {
            synchronized (this.engine)
            {
                final Document doc = this.engine.getDocument ();
                final Element el = doc.getElementById ("content");
                final String [] lines = StringUtils.split (text, "\n");
                for (int i = 0; i < lines.length; i++)
                {
                    final Text lineText = doc.createTextNode (lines[i]);
                    if (isError)
                    {
                        final Element errSpan = doc.createElement ("span");
                        errSpan.setAttribute ("class", "error");
                        errSpan.appendChild (lineText);
                        el.appendChild (errSpan);
                    }
                    else
                        el.appendChild (lineText);

                    if (i + 1 < lines.length || text.endsWith ("\n"))
                        el.appendChild (doc.createElement ("br"));
                }
                this.engine.executeScript ("window.scrollTo(0, document.body.scrollHeight);");
            }
        });
    }


    /**
     * Clears the logged text.
     */
    public final void clear ()
    {
        synchronized (this.engine)
        {
            final Document doc = this.engine.getDocument ();
            final Element el = doc.getElementById ("content");
            while (el.hasChildNodes ())
                el.removeChild (el.getFirstChild ());
        }
    }


    /**
     * Get the web view.
     *
     * @return THe web view
     */
    public WebView getWebView ()
    {
        return this.webView;
    }
}
