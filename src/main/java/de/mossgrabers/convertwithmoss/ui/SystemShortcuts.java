// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import de.mossgrabers.tools.OperatingSystem;
import javafx.event.EventDispatcher;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;


/**
 * Keeps the shortcuts of the macOS application menu working while an editable combo box has the
 * focus.
 *
 * <p>
 * An editable combo box hands every key stroke to its internal editor and consumes the original
 * event. macOS offers a Command shortcut to the application menu only if nothing inside the window
 * handled it, therefore Command-H and Command-Q do nothing at all as long as such a combo box has
 * the focus - and the application starts with the focus in the source path combo box, which makes
 * both shortcuts look permanently dead.
 * </p>
 *
 * @author Jürgen Moßgraber
 */
public final class SystemShortcuts
{
    /**
     * Constructor. Private due to utility class.
     */
    private SystemShortcuts ()
    {
        // Intentionally empty
    }


    /**
     * Route the shortcuts of the macOS application menu around the given editable combo box, so
     * that the combo box cannot consume them. All other key strokes - among them the editing
     * shortcuts like Command-C and Command-V - keep their normal way into the editor of the combo
     * box. Does nothing on other operating systems, which have their menus inside of the window.
     *
     * @param comboBox The editable combo box
     */
    public static void keepWorkingIn (final ComboBox<?> comboBox)
    {
        if (!OperatingSystem.isMacOS ())
            return;

        final EventDispatcher comboBoxDispatcher = comboBox.getEventDispatcher ();
        comboBox.setEventDispatcher ((event, tail) -> {

            if (event instanceof final KeyEvent keyEvent && isApplicationMenuShortcut (keyEvent))
                return tail.dispatchEvent (event);
            return comboBoxDispatcher.dispatchEvent (event, tail);
        });
    }


    /**
     * Test if the given key event is one of the shortcuts of the macOS application menu: Command-H
     * hides the application, Alt-Command-H hides all others and Command-Q quits it.
     *
     * @param keyEvent The key event to test
     * @return True if it is a shortcut of the application menu
     */
    private static boolean isApplicationMenuShortcut (final KeyEvent keyEvent)
    {
        if (!keyEvent.isShortcutDown ())
            return false;
        final KeyCode keyCode = keyEvent.getCode ();
        return keyCode == KeyCode.H || keyCode == KeyCode.Q;
    }
}
