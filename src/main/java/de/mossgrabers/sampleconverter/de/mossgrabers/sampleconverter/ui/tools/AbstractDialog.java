// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools;

import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.Optional;


/**
 * An abstract dialog which has some common needed functionality Every subclass must call
 * basicInit() in her constructor! Additional features:
 * <ul>
 * <li>Centers the dialog relative to the main window</li>
 * <li>Pressing the "ESCAPE" key closes or hides the dialog</li>
 * <li>Pressing "ENTER"/"RETURN" in a dialog when the focus is in a JTextField activates the default
 * key.</li>
 * </ul>
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public abstract class AbstractDialog extends Dialog<Boolean>
{
    protected ButtonType ok;
    protected ButtonType cancel;


    /**
     * Constructor for a modal dialog.
     *
     * @param owner The owner of the dialog
     * @param title The title of the dialog
     */
    protected AbstractDialog (final Window owner, final String title)
    {
        this (owner, title, true, false, -1, -1);
    }


    /**
     * Constructor.
     *
     * @param owner The owner of the dialog
     * @param title The title of the dialog
     * @param isModal Should the dialog be modal?
     */
    protected AbstractDialog (final Window owner, final String title, final boolean isModal)
    {
        this (owner, title, isModal, false, -1, -1);
    }


    /**
     * Constructor.
     *
     * @param owner The owner of the dialog
     * @param title The title of the dialog
     * @param isModal Should the dialog be modal?
     * @param isResizable True if the dialog should be resizable
     */
    protected AbstractDialog (final Window owner, final String title, final boolean isModal, final boolean isResizable)
    {
        this (owner, title, isModal, isResizable, -1, -1);
    }


    /**
     * Constructor.
     *
     * @param owner The owner of the dialog
     * @param title The title of the dialog
     * @param isModal Should the dialog be modal?
     * @param isResizable True if the dialog should be resizable
     * @param minWidth The minimum width of the dialog, ignored if resizable is false or value &lt;=
     *            0
     * @param minHeight The minimum height of the dialog, ignored if resizable is false or value
     *            &lt;= 0
     */
    protected AbstractDialog (final Window owner, final String title, final boolean isModal, final boolean isResizable, final int minWidth, final int minHeight)
    {
        this.initModality (isModal ? Modality.APPLICATION_MODAL : Modality.NONE);
        this.setTitle (Functions.getText (title));
        this.initOwner (owner);

        if (!isResizable)
            return;

        this.setResizable (true);

        final Window window = this.getWindow ();
        if (minWidth > 0)
            window.widthProperty ().addListener ((ChangeListener<Number>) (observable, oldValue, newValue) -> {
                if (newValue.intValue () < minWidth)
                    window.setWidth (minWidth);
            });
        if (minHeight > 0)
            window.heightProperty ().addListener ((ChangeListener<Number>) (observable, oldValue, newValue) -> {
                if (newValue.intValue () < minHeight)
                    window.setHeight (minHeight);
            });
    }


    /**
     * Sets the OK button.
     *
     * @param okString The button to set as the OK (default) button
     */
    public void setButtons (final String okString)
    {
        this.setButtons (okString, null);
    }


    /**
     * Sets the OK and cancel button.
     *
     * @param okString The button to set as the OK (default) button
     * @param cancelString The button to set as the CANCEL (Esc) button
     */
    public void setButtons (final String okString, final String cancelString)
    {
        final ObservableList<ButtonType> types = this.getDialogPane ().getButtonTypes ();
        if (okString != null)
        {
            this.ok = new ButtonType (Functions.getText (okString), ButtonData.OK_DONE);
            types.add (this.ok);
            final Button btOk = this.getOKButton ();
            btOk.addEventFilter (ActionEvent.ACTION, event -> {
                if (!this.onOk ())
                    event.consume ();
            });
        }
        if (cancelString != null)
        {
            this.cancel = new ButtonType (Functions.getText (cancelString), ButtonData.CANCEL_CLOSE);
            types.add (this.cancel);
            final Button btCancel = this.getCancelButton ();
            btCancel.addEventFilter (ActionEvent.ACTION, event -> {
                if (!this.onCancel ())
                    event.consume ();
            });
        }
    }


    /**
     * Get the OK button.
     *
     * @return The OK button or null if not set
     */
    protected final Button getOKButton ()
    {
        return (Button) this.getDialogPane ().lookupButton (this.ok);
    }


    /**
     * Get the Cancel button.
     *
     * @return The Cancel button or null if not set
     */
    protected Button getCancelButton ()
    {
        return (Button) this.getDialogPane ().lookupButton (this.cancel);
    }


    /**
     * Get the window of the dialog.
     *
     * @return The window
     */
    public final Window getWindow ()
    {
        return this.getScene ().getWindow ();
    }


    /**
     * Get the scene (owner).
     *
     * @return The scene
     */
    protected Scene getScene ()
    {
        return this.getDialogPane ().getScene ();
    }


    /**
     * Show the WAIT-Cursor for the mouse.
     *
     * @param busy True if the WAIT-Cursor should be shown
     */
    public void setBusy (final boolean busy)
    {
        this.getScene ().setCursor (busy ? Cursor.WAIT : Cursor.DEFAULT);
    }


    /**
     * Start the initialization for the dialog. Every subclass must call this function in the
     * constructor!
     */
    protected final void basicInit ()
    {
        final Pane panel = this.init ();
        this.getDialogPane ().setContent (panel);

        this.set ();

        this.getWindow ().setOnCloseRequest (event -> {
            if (!this.onCancel ())
                event.consume ();
        });

        this.getScene ().setOnKeyPressed (evt -> {
            if (evt.getCode () == KeyCode.ESCAPE && !this.onCancel ())
            {
                // The dialog is closed even if the escape key is consumed (hard
                // coded in Dialog implementation)
                // See https://bugs.openjdk.java.net/browse/JDK-8130377
                evt.consume ();
            }
        });

        this.setResultConverter (dialogButton -> dialogButton != null && dialogButton.equals (this.ok) ? Boolean.TRUE : null);
    }


    /**
     * Shows the display and wait for the answer.
     *
     * @return True if the dialog was confirmed
     */
    public boolean display ()
    {
        final Optional<Boolean> result = this.showAndWait ();
        return result.isPresent () && result.get ().booleanValue ();
    }


    /**
     * Overwrite this function to create and add the widgets of the dialog.
     *
     * @return The panel that should be set as the content-pane
     */
    protected abstract Pane init ();


    /**
     * Overwrite this function to set the widgets of the dialog to the correct values.
     */
    protected void set ()
    {
        // Intentionally empty
    }


    /**
     * Overwrite this function to read the data from the widgets.
     *
     * @return If true the dialog is closed
     */
    protected boolean onOk ()
    {
        return true;
    }


    /**
     * Overwrite this function to do additional things if dialog is aborted.
     *
     * @return If true the dialog is closed
     */
    protected boolean onCancel ()
    {
        return true;
    }
}
