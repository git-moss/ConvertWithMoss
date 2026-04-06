// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import de.mossgrabers.convertwithmoss.core.INotifier;


/**
 * Helper class for notifying about a progress, e.g. copying a sample.
 *
 * @author Jürgen Moßgraber
 */
public class ProgressLogger
{
    private final INotifier notifier;
    private int             counter = 0;


    /**
     * Constructor.
     *
     * @param notifier Where to write the notifications to
     */
    public ProgressLogger (final INotifier notifier)
    {
        this.notifier = notifier;
    }


    /**
     * Print out a notification progress (a dot).
     */
    public void notifyProgress ()
    {
        this.notifier.log ("IDS_NOTIFY_PROGRESS");
        this.counter++;
        if (this.counter % 80 == 0)
            this.notifyNewline ();
    }


    /**
     * Prints a new line.
     */
    public void notifyNewline ()
    {
        this.notifier.log ("IDS_NOTIFY_LINE_FEED");
    }


    /**
     * Notifies about the end of the progress.
     */
    public void notifyDone ()
    {
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
        this.counter = 0;
    }
}
