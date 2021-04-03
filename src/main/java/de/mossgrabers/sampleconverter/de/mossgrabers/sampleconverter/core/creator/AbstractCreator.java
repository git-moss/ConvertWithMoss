// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.creator;

import de.mossgrabers.sampleconverter.core.ICreator;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.ui.tools.Functions;
import de.mossgrabers.sampleconverter.util.XMLUtils;

import org.w3c.dom.Document;

import javax.xml.parsers.ParserConfigurationException;

import java.util.Optional;


/**
 * Base class for creator classes.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public abstract class AbstractCreator implements ICreator
{
    protected Optional<INotifier> notifier = Optional.empty ();


    /** {@inheritDoc} */
    @Override
    public void configure (final INotifier notifier)
    {
        this.notifier = Optional.of (notifier);
    }


    /**
     * Log the message to the notifier.
     *
     * @param messageID The ID of the message to get
     * @param replaceStrings Replaces the %1..%n in the message with the strings
     */
    protected void log (final String messageID, final String... replaceStrings)
    {
        if (this.notifier.isPresent ())
            this.notifier.get ().notify (Functions.getMessage (messageID, replaceStrings));
    }


    /**
     * Log the message to the notifier.
     *
     * @param messageID The ID of the message to get
     * @param throwable A throwable
     */
    protected void log (final String messageID, final Throwable throwable)
    {
        if (this.notifier.isPresent ())
            this.notifier.get ().notifyError (Functions.getMessage (messageID), throwable);
    }


    /**
     * Log the message to the notifier.
     *
     * @param throwable A throwable
     */
    protected void log (final Throwable throwable)
    {
        if (this.notifier.isPresent ())
            this.notifier.get ().notifyError (throwable.getMessage (), throwable);
    }


    /**
     * Create a new XML document.
     *
     * @return The document or not present if there is a configuration problem
     */
    protected Optional<Document> createXMLDocument ()
    {
        try
        {
            return Optional.of (XMLUtils.newDocument ());
        }
        catch (final ParserConfigurationException ex)
        {
            this.log ("IDS_NOTIFY_ERR_PARSER", ex);
            return Optional.empty ();
        }
    }
}
