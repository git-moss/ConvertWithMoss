// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools;

import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;


/**
 * Provides some useful static functions. Needs to be initialized with
 * {@link #init(ResourceBundle, Stage, double, double)} before use.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public final class Functions
{
    private static final String   COPYRIGHT_SYMBOL = "\u00A9";

    private static ResourceBundle messages;
    private static Stage          defaultOwner;
    private static double         openSaveDlgWidth;
    private static double         openSaveDlgHeight;


    /**
     * Private constructor because this is a utility class.
     */
    private Functions ()
    {
        // Empty by intention
    }


    /**
     * Initialize the Singleton object.
     *
     * @param messages A resource bundle which contains message texts
     * @param defaultOwner The default owner stage to use for dialogs
     * @param openSaveDlgWidth The width of the open and save dialog used for the getFileFromUser
     *            functions.
     * @param openSaveDlgHeight The width of the open and save dialog used for the getFileFromUser
     *            functions.
     */
    public static void init (final ResourceBundle messages, final Stage defaultOwner, final double openSaveDlgWidth, final double openSaveDlgHeight)
    {
        Functions.messages = messages;
        Functions.defaultOwner = defaultOwner;
        setOpenSaveDlgWidth (openSaveDlgWidth);
        setOpenSaveDlgHeight (openSaveDlgHeight);
    }


    /**
     * Get the width of the open and save dialog used for the getFileFromUser functions.
     *
     * @return The width of the open and save dialog used for the getFileFromUser functions
     */
    static double getOpenSaveDlgWidth ()
    {
        return openSaveDlgWidth;
    }


    /**
     * Get the height of the open and save dialog used for the getFileFromUser functions.
     *
     * @return The height of the open and save dialog used for the getFileFromUser functions
     */
    static double getOpenSaveDlgHeight ()
    {
        return openSaveDlgHeight;
    }


    /**
     * Set the width of the open and save dialog used for the getFileFromUser functions.
     *
     * @param openSaveDlgWidth The width of the open and save dialog used for the getFileFromUser
     *            functions.
     */
    static void setOpenSaveDlgWidth (final double openSaveDlgWidth)
    {
        Functions.openSaveDlgWidth = openSaveDlgWidth;
    }


    /**
     * Set the height of the open and save dialog used for the getFileFromUser functions.
     *
     * @param openSaveDlgHeight The height of the open and save dialog used for the getFileFromUser
     *            functions.
     */
    static void setOpenSaveDlgHeight (final double openSaveDlgHeight)
    {
        Functions.openSaveDlgHeight = openSaveDlgHeight;
    }


    /**
     * Get a message.
     *
     * @param messageID The ID of the message to get
     * @param replaceStrings Replaces the %1..%n in the message with the strings
     * @return The message
     * @see ResourceBundle#getString
     */
    public static String getMessage (final String messageID, final String... replaceStrings)
    {
        String message = messages == null ? "" : messages.getString (messageID);
        if (replaceStrings != null)
            message = replacePercentNWithStrings (message, replaceStrings);
        return message;
    }


    /**
     * Get a message which contains a %1. Replaces the %1 with the message text of the exception. If
     * the exception has no message the class name of the Exception is inserted.
     *
     * @param messageID The ID of the message to get
     * @param throwable A throwable
     * @return The message
     * @see ResourceBundle#getString
     */
    public static String getMessage (final String messageID, final Throwable throwable)
    {
        final String msg = throwable.getLocalizedMessage ();
        return getMessage (messageID, msg == null ? throwable.getClass ().getName () : msg);
    }


    /**
     * Reads the text from the functions-resource bundle if text starts with '@' otherwise the text
     * itself is returned.
     *
     * @param text The text or a message id starting with '@'
     * @return The loaded text
     */
    public static String getText (final String text)
    {
        if (noEmptyString (text) == null)
            return "";
        return text.charAt (0) == '@' ? getMessage (text.substring (1)) : text;
    }


    /**
     * Shows a message dialog with the message of an exception. If the exception does not contain a
     * message the exceptions class name is shown.
     *
     * @param ex An exception
     */
    public static void error (final Throwable ex)
    {
        error (defaultOwner, ex);
    }


    /**
     * Shows a message dialog with the message of an exception inserted into a message. If the
     * exception does not contain a message the exceptions class name is shown.
     *
     * @param message The message to display or a resource key
     * @param ex An exception
     */
    public static void error (final String message, final Throwable ex)
    {
        error (defaultOwner, message, ex);
    }


    /**
     * Shows a message dialog with the message of an exception. If the exception does not contain a
     * message the exceptions class name is shown.
     *
     * @param owner The owner of the dialog
     * @param ex An exception
     */
    public static void error (final Window owner, final Throwable ex)
    {
        error (owner, null, ex);
    }


    /**
     * Shows a message dialog with the message of an exception. If the exception does not contain a
     * message the exceptions class name is shown.
     *
     * @param owner The owner of the dialog
     * @param message The message to display or a resource key
     * @param ex An exception
     */
    public static void error (final Window owner, final String message, final Throwable ex)
    {
        final Alert alert = new Alert (AlertType.ERROR);
        alert.setTitle ("Exception Dialog");

        final String text;
        if (message == null)
        {
            final String msg = ex.getLocalizedMessage ();
            text = msg == null ? ex.getClass ().getName () : msg;
        }
        else
        {
            text = getText (message);
        }
        alert.setHeaderText (text);

        alert.setContentText (null);

        // Create expandable Exception.
        final StringWriter sw = new StringWriter ();
        final PrintWriter pw = new PrintWriter (sw);
        ex.printStackTrace (pw);
        final String exceptionText = sw.toString ();

        final Label label = new Label ("The exception stacktrace was:");

        final TextArea textArea = new TextArea (exceptionText);
        textArea.setEditable (false);
        textArea.setWrapText (true);

        textArea.setMaxWidth (Double.MAX_VALUE);
        textArea.setMaxHeight (Double.MAX_VALUE);
        GridPane.setVgrow (textArea, Priority.ALWAYS);
        GridPane.setHgrow (textArea, Priority.ALWAYS);

        final GridPane expContent = new GridPane ();
        expContent.setMaxWidth (Double.MAX_VALUE);
        expContent.add (label, 0, 0);
        expContent.add (textArea, 0, 1);

        // Set expandable Exception into the dialog pane.
        if (owner != null)
            alert.initOwner (owner);
        alert.getDialogPane ().setExpandableContent (expContent);
        alert.showAndWait ();
    }


    /**
     * Shows a message dialog. If the message starts with a '@' the message is interpreted as a
     * identifier for a string located in the resource file.
     *
     * @param message The message to display or a resource key
     * @param replaceStrings Replaces the %1..%n in the message with the strings
     * @see ResourceBundle#getString
     */
    public static void message (final String message, final String... replaceStrings)
    {
        message (defaultOwner, message, replaceStrings);
    }


    /**
     * Shows a message dialog. If the message starts with a '@' the message is interpreted as a
     * identifier for a string located in the resource file.
     *
     * @param owner The owner of the dialog
     * @param message The message to display or a resource key
     * @param replaceStrings Replaces the %1..%n in the message with the strings
     * @see ResourceBundle#getString
     */
    public static void message (final Window owner, final String message, final String... replaceStrings)
    {
        final String t = getText (message);
        final Alert alert = new Alert (AlertType.INFORMATION);
        alert.setTitle (null);
        alert.setHeaderText (null);
        alert.setContentText (replaceStrings == null ? t : replacePercentNWithStrings (t, replaceStrings));
        if (owner != null)
            alert.initOwner (owner);
        alert.showAndWait ();
    }


    /**
     * Shows an about dialog.
     *
     * @param owner The owner window
     * @param title The dialogs title
     * @param applicationName The name of the application
     * @param copyYear The copyright year
     * @param company The company who owns the copyright
     * @param icon The icon to display
     */
    public static void about (final Window owner, final String title, final String applicationName, final int copyYear, final String company, final Image icon)
    {
        final Alert alert = new Alert (AlertType.WARNING);
        alert.initOwner (owner);
        alert.setTitle (getText (title));
        alert.setGraphic (new ImageView (icon));
        alert.setHeaderText (getText (applicationName));
        alert.setContentText (new StringBuilder (COPYRIGHT_SYMBOL).append (' ').append (copyYear).append (" by ").append (company).toString ());
        alert.showAndWait ();
    }


    /**
     * Shows a yes or no choice dialog.
     *
     * @param message The text of the message
     * @return true if yes is selected
     */
    public static boolean yesOrNo (final String message)
    {
        return yesOrNo (message, (String) null);
    }


    /**
     * Shows a yes or no choice dialog.
     *
     * @param owner The owner window
     * @param message The text of the message
     * @return true if yes is selected
     */
    public static boolean yesOrNo (final Window owner, final String message)
    {
        return yesOrNo (owner, message, (String) null);
    }


    /**
     * Shows a yes or no choice dialog.
     *
     * @param message The text of the message
     * @param replaceStrings Replaces the %1..%n in the message with the strings
     * @return True if yes is selected
     */
    public static boolean yesOrNo (final String message, final String... replaceStrings)
    {
        return yesOrNo (defaultOwner, message, replaceStrings);
    }


    /**
     * Shows a yes or no choice dialog.
     *
     * @param owner The owner window
     * @param message The text of the message
     * @param replaceStrings Replaces the %1..%n in the message with the strings
     * @return True if yes is selected
     */
    public static boolean yesOrNo (final Window owner, final String message, final String... replaceStrings)
    {
        final String t = getText (message);
        final Alert alert = new Alert (AlertType.CONFIRMATION, replaceStrings == null ? t : replacePercentNWithStrings (t, replaceStrings), ButtonType.YES, ButtonType.NO);
        alert.setTitle (owner instanceof final Stage stage ? stage.getTitle () : null);
        alert.setHeaderText (null);
        if (owner != null)
            alert.initOwner (owner);
        final Optional<ButtonType> result = alert.showAndWait ();
        return result.isPresent () && result.get ().equals (ButtonType.YES);
    }


    /**
     * Shows a yes, no or cancel choice dialog.
     *
     * @param owner The owner window
     * @param message The text of the message
     * @param replaceStrings Replaces the %1..%n in the message with the strings
     * @return The selected button type
     */
    public static ButtonType yesOrNoOrCancel (final Window owner, final String message, final String... replaceStrings)
    {
        final String t = getText (message);
        final Alert alert = new Alert (AlertType.CONFIRMATION, replaceStrings == null ? t : replacePercentNWithStrings (t, replaceStrings), ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
        alert.setTitle (owner instanceof final Stage stage ? stage.getTitle () : null);
        alert.setHeaderText (null);
        if (owner != null)
            alert.initOwner (owner);
        final Optional<ButtonType> result = alert.showAndWait ();
        return result.isPresent () ? result.get () : ButtonType.NO;
    }


    /**
     * Choose 1 from 2 options.
     *
     * @param message The text of the message
     * @param title The title of the dialog
     * @param options The texts for the options
     * @return True if the first option was selected
     */
    public static boolean chooseFromTwo (final String message, final String title, final String... options)
    {
        return chooseFromTwo (defaultOwner, message, title, options);
    }


    /**
     * Choose 1 from 2 options.
     *
     * @param owner The owning window
     * @param message The text of the message
     * @param title The title of the dialog
     * @param options The texts for the options
     * @return True if the first option was selected
     */
    public static boolean chooseFromTwo (final Window owner, final String message, final String title, final String... options)
    {
        return choose (owner, message, title, options) == ButtonData.YES;
    }


    /**
     * Choose from N options.
     *
     * @param message The text of the message
     * @param title The title of the dialog
     * @param options The texts for the options
     * @return The id of the selected option
     */
    public static ButtonData choose (final String message, final String title, final String... options)
    {
        return choose (defaultOwner, message, title, options);
    }


    /**
     * Choose from N options.
     *
     * @param owner The owning window
     * @param message The text of the message
     * @param title The title of the dialog
     * @param options The texts for the options
     * @return The id of the selected option
     */
    public static ButtonData choose (final Window owner, final String message, final String title, final String... options)
    {
        final Alert alert = new Alert (AlertType.CONFIRMATION);
        alert.setTitle (owner instanceof final Stage stage ? stage.getTitle () : null);
        if (owner != null)
            alert.initOwner (owner);
        alert.setHeaderText (getText (title));
        alert.setContentText (getText (message));

        final ButtonType [] types = new ButtonType [options.length];
        types[0] = new ButtonType (getText (options[0]), ButtonData.YES);
        types[1] = new ButtonType (getText (options[1]), ButtonData.NO);
        if (options.length > 2)
            types[2] = new ButtonType (getText (options[2]), ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes ().setAll (types);
        final Optional<ButtonType> result = alert.showAndWait ();
        return result.isPresent () ? result.get ().getButtonData () : ButtonData.NO;
    }


    /**
     * Input a line of text.
     *
     * @param owner The owning window
     * @param title The title of the dialog
     * @param header The text in the header
     * @param message The label text for the input field
     * @return The input text or null if canceled
     */
    public static String inputText (final Window owner, final String title, final String header, final String message)
    {
        return inputText (owner, title, header, message, null);
    }


    /**
     * Input a line of text.
     *
     * @param owner The owning window
     * @param title The title of the dialog
     * @param header The text in the header
     * @param message The label text for the input field
     * @param defaultValue A value to display as the text
     * @return The input text or null if canceled
     */
    public static String inputText (final Window owner, final String title, final String header, final String message, final String defaultValue)
    {
        final TextInputDialog dialog = new TextInputDialog (defaultValue);
        if (owner != null)
        {
            dialog.initOwner (owner);
            if (owner instanceof final Stage stage)
                dialog.setTitle (stage.getTitle ());
        }
        if (title != null)
            dialog.setTitle (getText (title));
        if (header == null)
        {
            dialog.setGraphic (null);
            dialog.setHeaderText (null);
        }
        else
            dialog.setHeaderText (getText (header));
        dialog.setContentText (getText (message));
        final Optional<String> result = dialog.showAndWait ();
        return result.isPresent () ? result.get () : null;
    }


    /**
     * Requests a filename from the user.
     *
     * @param owner The owner Window
     * @param open True: Open-file False: Save-file
     * @param title The title of the dialog. If it starts with '@' the matching string is loaded
     *            from the properties file
     * @param config Read/Writes the current path and the size of the dialog from this config file
     * @param filter The file filter applied to the dialog
     * @return The file or null
     */
    public static Optional<File> getFileFromUser (final Window owner, final boolean open, final String title, final BasicConfig config, final FileChooser.ExtensionFilter filter)
    {
        return getFileFromUser (owner, open, title, config, filter == null ? null : new FileChooser.ExtensionFilter []
        {
            filter
        });
    }


    /**
     * Requests a filename from the user.
     *
     * @param owner The owner Window
     * @param open True: Open-file False: Save-file
     * @param title The title of the dialog. If it starts with '@' the matching string is loaded
     *            from the properties file. May be null.
     * @param config Read/Writes the current path and the size of the dialog from this configuration
     *            file. May be null.
     * @param filters The file filters applied to the dialog
     * @return The file or null
     */
    public static Optional<File> getFileFromUser (final Window owner, final boolean open, final String title, final BasicConfig config, final FileChooser.ExtensionFilter [] filters)
    {
        final FileChooser chooser = new FileChooser ();
        if (title != null && title.length () > 0)
            chooser.setTitle (getText (title));
        final String currentPath = config == null ? null : config.getActivePath ();
        if (currentPath != null)
        {
            final File directory = new File (currentPath);
            if (directory.exists ())
                chooser.setInitialDirectory (directory);
        }

        if (filters != null)
            chooser.getExtensionFilters ().addAll (filters);

        final File file = open ? chooser.showOpenDialog (owner) : chooser.showSaveDialog (owner);
        if (file == null)
            return Optional.empty ();

        // Store the current path
        final String parent = file.getParent ();
        if (config != null && parent != null)
            config.setActivePath (parent);

        return Optional.of (file);
    }


    /**
     * Requests one or more filenames from the user.
     *
     * @param owner The owner Window
     * @param title The title of the dialog. If it starts with '@' the matching string is loaded
     *            from the properties file
     * @param config Read/Writes the current path and the size of the dialog from this configuration
     *            file
     * @param filter The file filter applied to the dialog
     * @return The files or null
     */
    public static List<File> getFilesFromUser (final Window owner, final String title, final BasicConfig config, final FileChooser.ExtensionFilter filter)
    {
        return getFilesFromUser (owner, title, config, new FileChooser.ExtensionFilter []
        {
            filter
        });
    }


    /**
     * Requests one or more filenames from the user.
     *
     * @param owner The owner window
     * @param title The title of the dialog. If it starts with '@' the matching string is loaded
     *            from the properties file. May be null.
     * @param config Read/Writes the current path and the size of the dialog from this configuration
     *            file. May be null.
     * @param filters The file filters applied to the dialog
     * @return The files or null if canceled
     */
    public static List<File> getFilesFromUser (final Window owner, final String title, final BasicConfig config, final FileChooser.ExtensionFilter [] filters)
    {
        final String currentPath = config == null ? null : config.getActivePath ();

        final FileChooser chooser = new FileChooser ();
        if (title != null && title.length () > 0)
            chooser.setTitle (getText (title));
        if (currentPath != null)
        {
            final File directory = new File (currentPath);
            if (directory.exists ())
                chooser.setInitialDirectory (directory);
        }

        if (filters != null)
            chooser.getExtensionFilters ().addAll (filters);

        final List<File> files = chooser.showOpenMultipleDialog (owner);
        if (files == null)
            return Collections.emptyList ();

        // Store the current path
        final String parent = files.get (0).getParent ();
        if (config != null && parent != null)
            config.setActivePath (parent);

        return files;
    }


    /**
     * Requests a folder from the user.
     *
     * @param owner The owner window
     * @param config Read/Writes the current path and the size of the dialog from this configuration
     *            file. May be null.
     * @param title The title of the dialog. If it starts with '@' the matching string is loaded
     *            from the properties file. May be null.
     * @return The folder or null
     */
    public static Optional<File> getFolderFromUser (final Window owner, final BasicConfig config, final String title)
    {
        final String currentPath = config == null ? null : config.getActivePath ();

        final DirectoryChooser chooser = new DirectoryChooser ();
        if (title != null && title.length () > 0)
            chooser.setTitle (getText (title));
        if (currentPath != null)
        {
            final File directory = new File (currentPath);
            if (directory.exists ())
                chooser.setInitialDirectory (directory);
        }

        final File file = chooser.showDialog (owner);
        if (file == null)
            return Optional.empty ();

        // Store the current path
        if (config != null)
            config.setActivePath (file);

        return Optional.of (file);
    }


    /**
     * Create a progress dialog.
     *
     * @param <T> The result type of the task
     * @param task The task to monitor and display in the dialog.
     * @param owner The owner window.
     * @param title The title of the dialog. If it starts with '@' the matching string is loaded
     *            from the properties file. May be null.
     */
    public static <T> void createProgressDialog (final Task<T> task, final Window owner, final String title)
    {
        final Alert alert = new Alert (AlertType.INFORMATION);
        alert.initOwner (owner);
        alert.setTitle (getText (title));
        alert.headerTextProperty ().bind (task.messageProperty ());

        // Set expandable Exception into the dialog pane.
        final ProgressBar bar = new ProgressBar ();
        bar.progressProperty ().bind (task.progressProperty ());
        final DialogPane dialogPane = alert.getDialogPane ();
        dialogPane.setMinSize (800, 200);
        dialogPane.setContent (bar);

        final ObservableList<ButtonType> buttonTypes = dialogPane.getButtonTypes ();
        buttonTypes.setAll (ButtonType.CANCEL);

        // If task stops running exchange the buttons
        task.runningProperty ().addListener ((ChangeListener<Boolean>) (observable, oldValue, newValue) -> {
            if (!newValue.booleanValue ())
                buttonTypes.setAll (ButtonType.CLOSE);
        });

        // Cancel the task if cancel buttons is pressed
        final Button cancelButton = (Button) dialogPane.lookupButton (ButtonType.CANCEL);
        cancelButton.addEventFilter (ActionEvent.ACTION, event -> {
            event.consume ();
            task.cancel ();
        });

        alert.show ();
        new Thread (task).start ();
    }


    /**
     * If value contains an empty string the function returns null otherwise the unmodified string.
     *
     * @param value The value to check/modify
     * @return Null or the string
     */
    public static String noEmptyString (final String value)
    {
        return value == null || value.length () == 0 ? null : value;
    }


    /**
     * If value is null the function returns an empty string otherwise the unmodified string.
     *
     * @param value The value to check/modify
     * @return An empty string or the unmodified string
     */
    public static String noNullString (final String value)
    {
        return value == null ? "" : value;
    }


    /**
     * Helper function to compare to objects which may be null. If both are null they are considered
     * as equal.
     *
     * @param o1 One element to compare
     * @param o2 The other element to compare
     * @return True if the elements are equal
     */
    public static boolean compare (final Object o1, final Object o2)
    {
        return o1 == o2 || o1 != null && o1.equals (o2);
    }


    /**
     * Replaces '%X' X=[0, replaceStrings.length-1] in a message with the replaceStrings.
     *
     * @param message The message to modify
     * @param replaceStrings The strings to insert
     * @return The modified string
     */
    public static String replacePercentNWithStrings (final String message, final String [] replaceStrings)
    {
        String m = message;
        for (int i = 0; i < replaceStrings.length; i++)
        {
            final int pos = m.indexOf ("%" + (i + 1));
            if (pos != -1 && replaceStrings[i] != null)
                m = new StringBuilder (m.substring (0, pos)).append (replaceStrings[i]).append (m.substring (pos + 2)).toString ();
        }
        return m;
    }


    /**
     * Get an icon from a jar file.
     *
     * @param iconName The name (and path) of the icon
     * @return The retrieved icon
     */
    public static Image iconFor (final String iconName)
    {
        return iconName == null ? null : new Image (ClassLoader.getSystemResourceAsStream (iconName));
    }


    /**
     * Reads an integer from a TextField.
     *
     * @param field The field from which to read the integer
     * @return The integer or -1 if none could be parsed
     */
    public static int readInt (final TextField field)
    {
        try
        {
            return Integer.parseInt (field.getText ());
        }
        catch (final NumberFormatException ex)
        {
            return -1;
        }
    }
}
