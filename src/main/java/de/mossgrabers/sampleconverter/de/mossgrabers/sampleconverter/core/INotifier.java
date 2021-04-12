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
     * Display a notification.
     *
     * @param message The message to display
     */
    void notify (String message);


    /**
     * Display an error message.
     *
     * @param message The message to display
     */
    void notifyError (final String message);


    /**
     * Display an error notification.
     *
     * @param message The message to display
     * @param throwable The throwable to log
     */
    void notifyError (String message, Throwable throwable);


    /**
     * Update the button execution states.
     *
     * @param canClose Execution can be closed
     */
    void updateButtonStates (boolean canClose);
}
