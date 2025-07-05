// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import de.mossgrabers.convertwithmoss.core.CLIBackend;
import de.mossgrabers.tools.ui.DefaultApplication;
import javafx.application.Application;


/**
 * The sample converter application.
 *
 * @author Jürgen Moßgraber
 */
public class ConvertWithMossApp
{
    /**
     * Main-method.
     *
     * @param arguments The startup arguments
     */
    public static void main (final String [] arguments)
    {
        // Handle the command line (CLI) or start the user interface
        if (arguments.length > 0)
            new CLIBackend ().parseCommandLine (arguments);
        else
            Application.launch (DefaultApplication.class, MainFrame.class.getName ());
    }
}
