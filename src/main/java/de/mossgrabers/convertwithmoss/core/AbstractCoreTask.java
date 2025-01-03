// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import de.mossgrabers.tools.ui.BasicConfig;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;


/**
 * Base class for creator and detector classes.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractCoreTask implements ICoreTask
{
    protected final String    name;
    protected final INotifier notifier;


    /**
     * Constructor.
     *
     * @param name The name of the object.
     * @param notifier The notifier
     */
    protected AbstractCoreTask (final String name, final INotifier notifier)
    {
        this.name = name;
        this.notifier = notifier;
    }


    /** {@inheritDoc} */
    @Override
    public String getName ()
    {
        return this.name;
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        return new BorderPane ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettings ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public void shutdown ()
    {
        // Intentionally empty
    }


    protected void notifyProgress ()
    {
        this.notifier.log ("IDS_NOTIFY_PROGRESS");
    }


    protected void notifyNewline ()
    {
        this.notifier.log ("IDS_NOTIFY_LINE_FEED");
    }
}
