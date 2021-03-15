// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core;

/**
 * Interface to notify the user about notification messages.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public interface INotifier
{
    /**
     * Display a notification and log it to the console.
     *
     * @param message The message to display
     */
    void notify (String message);


    /**
     * Display an error notification and log it to the console.
     *
     * @param message The message to display
     * @param ex The exception to log
     */
    void notifyError (String message, Exception ex);


    /**
     * Update the button execution states.
     *
     * @param canClose Execution can be closed
     */
    void updateButtonStates (boolean canClose);
}
