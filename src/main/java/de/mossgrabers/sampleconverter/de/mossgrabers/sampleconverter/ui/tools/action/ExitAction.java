// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools.action;

import javafx.application.Platform;
import javafx.event.ActionEvent;


/**
 * Exit the program.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class ExitAction extends Action
{
    /**
     * Exit the application.
     *
     * @param e Information about the action that occurred.
     */
    @Override
    public void handle (final ActionEvent e)
    {
        Platform.exit ();
    }
}
