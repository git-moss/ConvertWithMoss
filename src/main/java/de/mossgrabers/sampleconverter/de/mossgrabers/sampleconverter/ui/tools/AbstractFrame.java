// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;


/**
 * Encapsulates a stage and adds a scene to it which provides several features like menus, toolbar,
 * statusbar, etc. Use as top level windows ("frames").
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public abstract class AbstractFrame
{
    private Stage                      stage;
    private Scene                      scene;
    private final BorderPane           rootNode = new BorderPane ();
    private final VBox                 barNode  = new VBox ();

    private String                     baseTitle;
    private final SimpleStringProperty title    = new SimpleStringProperty ();
    private final double               minWidth;
    private final double               minHeight;

    private final String               startPath;
    protected final BasicConfig        config;


    /**
     * Constructor.
     *
     * @param path The start path for settings and resources
     * @param minWidth The minimum width of the stage
     * @param minHeight The minimum height of the stage
     */
    public AbstractFrame (final String path, final double minWidth, final double minHeight)
    {
        this.minWidth = minWidth;
        this.minHeight = minHeight;

        this.startPath = path;
        this.config = new BasicConfig (path);
    }


    /**
     * @return The stage
     */
    public Stage getStage ()
    {
        return this.stage;
    }


    /**
     * Initialise the application.
     *
     * @param stage The stage where to add widgets
     * @param baseTitle The title to use for window
     * @exception EndApplicationException Oops, something crashed...
     */
    public abstract void initialise (final Stage stage, final String baseTitle) throws EndApplicationException;


    /**
     * Initialise the application.
     *
     * @param stage The stage where to add widgets
     * @param baseTitle The title to use for window
     * @param hasMenuBar True if the frame has a menu bar
     * @param hasToolBar True if the frame has a tool bar
     * @param hasStatusBar True if the frame has a status bar
     * @exception EndApplicationException Oops, something crashed...
     */
    protected void initialise (final Stage stage, final String baseTitle, final boolean hasMenuBar, final boolean hasToolBar, final boolean hasStatusBar) throws EndApplicationException
    {
        this.stage = stage;
        this.baseTitle = baseTitle;
        this.stage.minWidthProperty ().set (this.minWidth);
        this.stage.minHeightProperty ().set (this.minHeight);
        this.stage.titleProperty ().bind (this.title);

        this.scene = this.initScene ();
        this.scene.getStylesheets ().add (this.startPath + "/css/DefaultStyles.css");

        this.initConfig ();
        this.initStrings ();
        this.initTitleBar ();

        if (this.baseTitle == null)
            this.baseTitle = Functions.getMessage ("TITLE");

        this.stage.setScene (this.scene);
        this.stage.setOnCloseRequest (this::exit);

        this.stage.show ();
    }


    /**
     * Initialise the configuration file.
     *
     * @throws EndApplicationException The application should end because of an startup error
     */
    protected void initConfig () throws EndApplicationException
    {
        this.restoreStagePlacement ();
    }


    /**
     * Exits the program and consumes the event.
     *
     * @param event The event to consume
     */
    private void exit (final WindowEvent event)
    {
        event.consume ();
        this.exit ();
    }


    /**
     * Exits the application.
     */
    public void exit ()
    {
        try
        {
            this.storeStagePlacement ();

            this.config.save ();
        }
        catch (final IOException ex)
        {
            Functions.error (ex);
        }
    }


    /**
     * Sets the window title.
     *
     * @param newFileMessage The message if a new file is opened
     */
    public void updateTitle (final String newFileMessage)
    {
        final StringBuilder titleText = new StringBuilder (this.baseTitle);

        this.title.set (titleText.toString ());
    }


    /**
     * Show the WAIT-Cursor for the mouse.
     *
     * @param busy True if the WAIT-Curor should be shown
     */
    public void setBusy (final boolean busy)
    {
        this.scene.setCursor (busy ? Cursor.WAIT : Cursor.DEFAULT);
    }


    /**
     * Initialise the scene.
     *
     * @return The created scene
     */
    protected Scene initScene ()
    {
        this.rootNode.setTop (this.barNode);

        final Scene aScene = new Scene (this.rootNode);
        aScene.setFill (javafx.scene.paint.Color.TRANSPARENT);
        return aScene;
    }


    /**
     * Initialise the string resources.
     *
     * @throws EndApplicationException Could not read the string resources
     */
    protected void initStrings () throws EndApplicationException
    {
        try
        {
            Functions.init (ResourceBundle.getBundle ("Strings", Locale.getDefault ()), this.getStage (), 400, 500);
        }
        catch (final MissingResourceException mre)
        {
            throw new EndApplicationException ("Strings.properties not found", mre);
        }
    }


    /**
     * Set the app image and title.
     */
    protected void initTitleBar ()
    {
        final InputStream rs = ClassLoader.getSystemResourceAsStream (this.startPath + "/images/AppIcon.png");
        if (rs != null)
            this.stage.getIcons ().add (new Image (rs));
    }


    /**
     * Sets the node to be centered.
     *
     * @param node The node to set
     */
    protected void setCenterNode (final Node node)
    {
        this.rootNode.setCenter (node);
    }


    /**
     * Stores the placement of the main stage.
     *
     * @throws IOException Could not store the stages placement
     */
    protected void storeStagePlacement () throws IOException
    {
        this.config.storeStagePlacement (this.stage);
    }


    /**
     * Restores the main stage placement.
     */
    protected void restoreStagePlacement ()
    {
        this.config.restoreStagePlacement (this.stage);
    }
}
