// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui;

import de.mossgrabers.sampleconverter.core.ICreator;
import de.mossgrabers.sampleconverter.core.ICreatorDescriptor;
import de.mossgrabers.sampleconverter.core.IDetectorDescriptor;
import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.creator.BitwigMultisampleCreatorDescriptor;
import de.mossgrabers.sampleconverter.creator.SfzCreatorDescriptor;
import de.mossgrabers.sampleconverter.detector.WaveDetectorDescriptor;
import de.mossgrabers.sampleconverter.ui.tools.AbstractFrame;
import de.mossgrabers.sampleconverter.ui.tools.DefaultApplication;
import de.mossgrabers.sampleconverter.ui.tools.EndApplicationException;
import de.mossgrabers.sampleconverter.ui.tools.Functions;
import de.mossgrabers.sampleconverter.ui.tools.control.TitledSeparator;
import de.mossgrabers.sampleconverter.ui.tools.panel.BoxPanel;
import de.mossgrabers.sampleconverter.ui.tools.panel.ButtonPanel;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;


/**
 * The sample converter application.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class SampleConverterApp extends AbstractFrame implements INotifier, Consumer<IMultisampleSource>
{
    private static final String  DESTINATION_CREATE_FOLDER_STRUCTURE = "DestinationCreateFolderStructure";
    private static final String  DESTINATION_PATH                    = "DestinationPath";
    private static final String  SOURCE_PATH                         = "SourcePath";

    final ICreatorDescriptor []  creatorDescriptors                  =
    {
        new BitwigMultisampleCreatorDescriptor (),
        new SfzCreatorDescriptor ()
    };

    final IDetectorDescriptor [] detectorDescriptors                 =
    {
        new WaveDetectorDescriptor ()
    };

    private BorderPane           mainPane;
    private BorderPane           executePane;
    private TextField            sourcePathField;
    private TextField            destinationPathField;
    private File                 sourceFolder;
    private File                 outputFolder;
    private CheckBox             createFolderStructure;

    private TabPane              sourceTabPane;
    private TabPane              destinationTabPane;
    private TextArea             loggingArea;

    private boolean              onlyAnalyse                         = true;
    private Button               closeButton;
    private Button               cancelButton;


    /**
     * Main-method.
     *
     * @param args The startup arguments
     */
    public static void main (final String [] args)
    {
        Application.launch (DefaultApplication.class, SampleConverterApp.class.getName ());
    }


    /**
     * Constructor.
     *
     * @throws EndApplicationException Startup crash
     */
    public SampleConverterApp () throws EndApplicationException
    {
        super ("de/mossgrabers/sampleconverter", 400, 400);
    }


    /** {@inheritDoc} */
    @Override
    public void initialise (final Stage stage, final String baseTitle) throws EndApplicationException
    {
        super.initialise (stage, baseTitle, true, true, true);

        // The main button panel
        final ButtonPanel buttonPanel = new ButtonPanel (Orientation.VERTICAL);
        buttonPanel.createButton ("@IDS_MAIN_CONVERT").setOnAction (event -> this.execute (false));
        buttonPanel.createButton ("@IDS_MAIN_ANALYSE").setOnAction (event -> this.execute (true));

        // Source pane
        final BorderPane sourcePane = new BorderPane ();

        this.sourcePathField = new TextField ();
        final BorderPane sourceFolderPanel = new BorderPane (this.sourcePathField);

        final Button sourceFolderSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_SOURCE"));
        sourceFolderSelectButton.setOnAction (event -> {

            final File file = Functions.getFolderFromUser (this.getStage (), this.config, Functions.getText ("@IDS_MAIN_SELECT_SOURCE_HEADER"));
            if (file != null)
                this.sourcePathField.setText (file.getAbsolutePath ());

        });
        sourceFolderPanel.setRight (sourceFolderSelectButton);

        final BoxPanel sourceUpperPart = new BoxPanel (Orientation.VERTICAL);
        sourceUpperPart.addComponent (new TitledSeparator (Functions.getText ("@IDS_MAIN_SOURCE_HEADER")));
        sourceUpperPart.addComponent (sourceFolderPanel);

        sourcePane.setTop (sourceUpperPart.getPane ());
        this.sourceTabPane = new TabPane ();
        this.sourceTabPane.getStyleClass ().add ("paddingLeftBottomRight");
        sourcePane.setCenter (this.sourceTabPane);

        final ObservableList<Tab> tabs = this.sourceTabPane.getTabs ();
        for (final IDetectorDescriptor detectorDescriptor: this.detectorDescriptors)
            tabs.add (new Tab (detectorDescriptor.getName (), detectorDescriptor.getEditPane ()));

        // Destination pane
        final BorderPane destinationPane = new BorderPane ();

        this.destinationPathField = new TextField ();
        final BorderPane destinationFolderPanel = new BorderPane (this.destinationPathField);

        final Button destinationFolderSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_DESTINATION"));
        destinationFolderSelectButton.setOnAction (event -> {

            final File file = Functions.getFolderFromUser (this.getStage (), this.config, Functions.getText ("@IDS_MAIN_SELECT_DESTINATION_HEADER"));
            if (file != null)
                this.destinationPathField.setText (file.getAbsolutePath ());

        });
        destinationFolderPanel.setRight (destinationFolderSelectButton);

        final BoxPanel destinationUpperPart = new BoxPanel (Orientation.VERTICAL);
        destinationUpperPart.addComponent (new TitledSeparator ("@IDS_MAIN_DESTINATION_HEADER"));
        destinationUpperPart.addComponent (destinationFolderPanel);

        destinationPane.setTop (destinationUpperPart.getPane ());
        this.destinationTabPane = new TabPane ();
        this.destinationTabPane.getStyleClass ().add ("paddingLeftBottomRight");
        destinationPane.setCenter (this.destinationTabPane);

        final BoxPanel bottom = new BoxPanel (Orientation.HORIZONTAL);
        this.createFolderStructure = bottom.createCheckBox ("Create folder structure");
        this.createFolderStructure.setTooltip (new Tooltip ("Recreates the folder structure which is found below the source folder in the destination folder."));
        this.createFolderStructure.setSelected (true);
        destinationPane.setBottom (bottom.getPane ());

        final ObservableList<Tab> destinationTabs = this.destinationTabPane.getTabs ();
        for (final ICreatorDescriptor creatorDescriptor: this.creatorDescriptors)
            destinationTabs.add (new Tab (creatorDescriptor.getName (), creatorDescriptor.getEditPane ()));

        // Tie it all together ...
        final HBox grid = new HBox ();
        grid.setFillHeight (true);
        grid.getChildren ().addAll (sourcePane, destinationPane);
        HBox.setHgrow (sourcePane, Priority.ALWAYS);
        HBox.setHgrow (destinationPane, Priority.ALWAYS);

        this.mainPane = new BorderPane ();
        this.mainPane.setCenter (grid);
        this.mainPane.setRight (buttonPanel.getPane ());

        // Execution pane
        this.executePane = new BorderPane ();
        this.loggingArea = new TextArea ();
        this.loggingArea.getStyleClass ().add ("logging");
        this.loggingArea.setEditable (false);

        // The execution button panel
        final ButtonPanel exButtonPanel = new ButtonPanel (Orientation.VERTICAL);
        this.cancelButton = exButtonPanel.createButton ("@IDS_EXEC_CANCEL");
        this.cancelButton.setOnAction (event -> this.cancelExecution ());
        this.closeButton = exButtonPanel.createButton ("@IDS_EXEC_CLOSE");
        this.closeButton.setOnAction (event -> this.closeExecution ());

        final ScrollPane scrollPane = new ScrollPane (this.loggingArea);
        scrollPane.fitToWidthProperty ().set (true);
        scrollPane.fitToHeightProperty ().set (true);
        this.executePane.setCenter (scrollPane);
        this.executePane.setRight (exButtonPanel.getPane ());
        this.executePane.setVisible (false);

        final StackPane stackPane = new StackPane (this.mainPane, this.executePane);
        this.setCenterNode (stackPane);

        this.loadConfig ();

        this.updateTitle (null);
    }


    /**
     * Load configuration settings.
     */
    private void loadConfig ()
    {
        final String sourcePath = this.config.getProperty (SOURCE_PATH);
        if (sourcePath != null)
            this.sourcePathField.setText (sourcePath);

        final String destinationPath = this.config.getProperty (DESTINATION_PATH);
        if (destinationPath != null)
            this.destinationPathField.setText (destinationPath);

        this.createFolderStructure.setSelected (this.config.getBoolean (DESTINATION_CREATE_FOLDER_STRUCTURE, true));

        for (final IDetectorDescriptor detectorDescriptor: this.detectorDescriptors)
            detectorDescriptor.loadSettings (this.config);
        for (final ICreatorDescriptor creatorDescriptor: this.creatorDescriptors)
            creatorDescriptor.loadSettings (this.config);
    }


    /** {@inheritDoc} */
    @Override
    public void exit ()
    {
        for (final IDetectorDescriptor detectorDescriptor: this.detectorDescriptors)
            detectorDescriptor.shutdown ();

        this.config.setProperty (SOURCE_PATH, this.sourcePathField.getText ());
        this.config.setProperty (DESTINATION_PATH, this.destinationPathField.getText ());
        this.config.setProperty (DESTINATION_CREATE_FOLDER_STRUCTURE, Boolean.toString (this.createFolderStructure.isSelected ()));

        for (final IDetectorDescriptor detectorDescriptor: this.detectorDescriptors)
            detectorDescriptor.saveSettings (this.config);
        for (final ICreatorDescriptor creatorDescriptor: this.creatorDescriptors)
            creatorDescriptor.saveSettings (this.config);

        // Store configuration
        super.exit ();

        Platform.exit ();
    }


    private void execute (final boolean onlyAnalyse)
    {
        this.onlyAnalyse = onlyAnalyse;

        if (!this.verifyFolders ())
            return;

        final int selectedDetector = this.sourceTabPane.getSelectionModel ().getSelectedIndex ();
        if (selectedDetector < 0)
            return;

        this.loggingArea.clear ();

        this.mainPane.setVisible (false);
        this.executePane.setVisible (true);

        Platform.runLater ( () -> {
            this.notify ("Detecting and converting multisamples...\n");
            this.detectorDescriptors[selectedDetector].detect (this, this.sourceFolder, this);
        });
    }


    /**
     * Cancel button was pressed.
     */
    private void cancelExecution ()
    {
        final int selectedDetector = this.sourceTabPane.getSelectionModel ().getSelectedIndex ();
        if (selectedDetector >= 0)
            this.detectorDescriptors[selectedDetector].cancel ();
    }


    /**
     * Close button was pressed.
     */
    private void closeExecution ()
    {
        this.mainPane.setVisible (true);
        this.executePane.setVisible (false);
    }


    /**
     * Set and check folder for existence.
     *
     * @return True if OK
     */
    private boolean verifyFolders ()
    {
        // Check source folder
        this.sourceFolder = new File (this.sourcePathField.getText ());
        if (!this.sourceFolder.exists () || !this.sourceFolder.isDirectory ())
        {
            Functions.message ("The source folder does not exist: %1", this.sourceFolder.getAbsolutePath ());
            return false;
        }

        if (this.onlyAnalyse)
            return true;

        // Check output folder
        this.outputFolder = new File (this.destinationPathField.getText ());
        if (!this.outputFolder.exists () && !this.outputFolder.mkdirs ())
        {
            Functions.message ("Could not create output folder: %1", this.outputFolder.getAbsolutePath ());
            return false;
        }
        if (!this.outputFolder.isDirectory ())
        {
            Functions.message ("The selected output folder is not a folder: %1", this.outputFolder.getAbsolutePath ());
            return false;
        }

        // Output folder must be empty
        final String [] content = this.outputFolder.list ();
        if (content == null || content.length > 0)
        {
            Functions.message ("The output folder is not empty. Please select an empty folder.");
            return false;
        }

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void accept (final IMultisampleSource multisampleSource)
    {
        final int selectedCreator = this.destinationTabPane.getSelectionModel ().getSelectedIndex ();
        if (selectedCreator < 0)
            return;

        final ICreator creator = this.creatorDescriptors[selectedCreator].getCreator ();
        this.notify ("Mapping: " + multisampleSource.getFolder () + "\n");

        try
        {
            if (this.onlyAnalyse)
                return;

            final boolean createStructure = this.createFolderStructure.isSelected ();
            final File multisampleOutputFolder = calcOutputFolder (this.outputFolder, multisampleSource.getSubPath (), createStructure);
            creator.create (multisampleOutputFolder, multisampleSource, this);
        }
        catch (final IOException | RuntimeException ex)
        {
            this.notifyError (" -> Could not create multisample.\n", ex);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void notify (final String message)
    {
        Platform.runLater ( () -> {
            this.loggingArea.appendText (message);
            this.loggingArea.end ();
        });
    }


    /** {@inheritDoc} */
    @Override
    public void notifyError (final String message, final Exception ex)
    {
        Platform.runLater ( () -> {
            this.loggingArea.appendText (message);
            this.loggingArea.appendText ("\n");

            final StringWriter sw = new StringWriter ();
            final PrintWriter pw = new PrintWriter (sw);
            ex.printStackTrace (pw);
            this.loggingArea.appendText (sw.toString ());
            this.loggingArea.appendText ("\n");
            this.loggingArea.end ();
        });
    }


    /**
     * Creates the file object for the output folder.
     *
     * @param out The top output folder
     * @param parts The sub-folder parts
     * @param createSubFolders If true the sub-folders are created inside of the top output folder
     * @return The destination folder
     * @throws IOException Could not create sub-folders
     */
    private static File calcOutputFolder (final File out, final String [] parts, final boolean createSubFolders) throws IOException
    {
        if (!createSubFolders)
            return out;

        File result = out;
        for (int i = parts.length - 1; i >= 2; i--)
        {
            result = new File (result, parts[i]);
            if (!result.exists () && !result.mkdirs ())
                throw new IOException ("Could not create folder: " + result.getAbsolutePath ());
        }
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public void updateButtonStates (final boolean canClose)
    {
        Platform.runLater ( () -> {
            this.cancelButton.setDisable (canClose);
            this.closeButton.setDisable (!canClose);
        });
    }
}
