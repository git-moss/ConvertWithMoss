// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import de.mossgrabers.convertwithmoss.core.ConverterBackend;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.ICreator;
import de.mossgrabers.convertwithmoss.core.detector.IDetector;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.convertwithmoss.file.CSVRenameFile;
import de.mossgrabers.tools.ui.AbstractFrame;
import de.mossgrabers.tools.ui.EndApplicationException;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.TraversalManager;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.control.loggerbox.LoggerBox;
import de.mossgrabers.tools.ui.control.loggerbox.LoggerBoxLogger;
import de.mossgrabers.tools.ui.panel.BasePanel;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import de.mossgrabers.tools.ui.panel.ButtonPanel;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;


/**
 * The sample converter application.
 *
 * @author Jürgen Moßgraber
 */
public class MainFrame extends AbstractFrame implements INotifier
{
    private static final String          PADDING_LEFT_BOTTOM_RIGHT           = "paddingLeftBottomRight";

    private static final int             NUMBER_OF_DIRECTORIES               = 20;
    private static final int             MAXIMUM_NUMBER_OF_LOG_ENTRIES       = 100000;

    private static final String          ENABLE_DARK_MODE                    = "EnableDarkMode";
    private static final String          DESTINATION_CREATE_FOLDER_STRUCTURE = "DestinationCreateFolderStructure";
    private static final String          DESTINATION_ADD_NEW_FILES           = "DestinationAddNewFiles";
    private static final String          DESTINATION_PATH                    = "DestinationPath";
    private static final String          DESTINATION_FORMAT                  = "DestinationFormat";
    private static final String          DESTINATION_TYPE                    = "DestinationType";
    private static final String          SOURCE_PATH                         = "SourcePath";
    private static final String          SOURCE_TYPE                         = "SourceType";
    private static final String          RENAMING_CSV_FILE                   = "RenamingCSVFile";
    private static final String          RENAMING_SOURCE_ENABLED             = "EnableRenaming";
    private static final String          PRESET_LIBRARY_FILENAME             = "PresetLibraryFilename";
    private static final String          PERFORMANCE_LIBRARY_FILENAME        = "PerformanceLibraryFilename";

    private static final int             DEST_TYPE_PRESET                    = 0;
    private static final int             DEST_TYPE_PRESET_LIBRARY            = 1;
    private static final int             DEST_TYPE_PERFORMANCE               = 2;
    private static final int             DEST_TYPE_PERFORMANCE_LIBRARY       = 3;

    private BorderPane                   mainPane;
    private BorderPane                   executePane;
    private final ComboBox<String>       sourcePathField                     = new ComboBox<> ();
    private final ComboBox<String>       destinationPathField                = new ComboBox<> ();
    private File                         sourceFolder;
    private File                         outputFolder;
    private Button                       convertButton;
    private Button                       analyseButton;
    private Button                       sourceFolderSelectButton;
    private Button                       destinationFolderSelectButton;
    private CheckBox                     createFolderStructure;
    private CheckBox                     addNewFiles;
    private CheckBox                     enableDarkMode;

    private final TabPane                sourceTabPane                       = new TabPane ();
    private final TabPane                destinationTabPane                  = new TabPane ();
    private final TabPane                destinationTypeTabPane              = new TabPane ();

    private final List<String>           sourcePathHistory                   = new ArrayList<> ();
    private final List<String>           destinationPathHistory              = new ArrayList<> ();

    private Button                       closeButton;
    private Button                       cancelButton;

    private CheckBox                     renameCheckbox;
    private final TextField              renameFilePathField                 = new TextField ();
    private Button                       renameFilePathSelectButton;

    private final CSVRenameFile          csvRenameFile                       = new CSVRenameFile ();
    private final LoggerBoxLogger        logger                              = new LoggerBoxLogger (MAXIMUM_NUMBER_OF_LOG_ENTRIES);
    private final LoggerBox              loggingArea                         = new LoggerBox (this.logger);
    private final TraversalManager       traversalManager                    = new TraversalManager ();

    private FileWriter                   logWriter;
    private boolean                      combineWithPreviousMessage          = false;
    private TextField                    presetLibraryFilename;
    private TextField                    performanceLibraryFilename;
    private final Map<Tab, ICreator<?>>  creatorTabs                         = new HashMap<> ();
    private final Map<Tab, IDetector<?>> sourceTabs                          = new HashMap<> ();
    private final ConverterBackend       backend;


    /**
     * Constructor.
     *
     * @throws EndApplicationException Startup crash
     */
    public MainFrame () throws EndApplicationException
    {
        super ("de/mossgrabers/convertwithmoss", 1280, 840);

        this.backend = new ConverterBackend (this);
    }


    /**
     * Enables/disables the renaming controls depending on the selection status of the renaming
     * checkbox.
     */
    private void updateRenamingControls ()
    {
        this.renameFilePathField.setDisable (!this.renameCheckbox.isSelected ());
        this.renameFilePathSelectButton.setDisable (!this.renameCheckbox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void initialise (final Stage stage, final Optional<String> baseTitleOptional) throws EndApplicationException
    {
        super.initialise (stage, baseTitleOptional, true, true, true);

        // The main button panel
        final ButtonPanel buttonPanel = new ButtonPanel (Orientation.VERTICAL);
        this.convertButton = setupButton (buttonPanel, "Convert", "@IDS_MAIN_CONVERT", "@IDS_MAIN_CONVERT_TOOLTIP");
        this.convertButton.setDefaultButton (true);
        this.convertButton.setOnAction (_ -> this.execute (false));
        this.analyseButton = setupButton (buttonPanel, "Analyse", "@IDS_MAIN_ANALYSE", "@IDS_MAIN_ANALYSE_TOOLTIP");
        this.analyseButton.setOnAction (_ -> this.execute (true));

        /////////////////////////////////////////////////////////////////////////////
        // Source pane

        this.sourceFolderSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_SOURCE"));
        this.sourceFolderSelectButton.setTooltip (new Tooltip (Functions.getText ("@IDS_MAIN_SELECT_SOURCE_TOOLTIP")));
        this.sourceFolderSelectButton.setOnAction (_ -> this.selectSourcePath ());
        final BoxPanel sourceUpperPart = new BoxPanel (Orientation.VERTICAL);
        final TitledSeparator sourceTitle = new TitledSeparator (Functions.getText ("@IDS_MAIN_SOURCE_HEADER"));
        sourceTitle.setLabelFor (this.sourcePathField);
        sourceUpperPart.addComponent (sourceTitle);
        sourceUpperPart.addComponent (new BorderPane (this.sourcePathField, null, this.sourceFolderSelectButton, null, null));
        this.sourcePathField.setMaxWidth (Double.MAX_VALUE);

        this.sourceTabPane.getStyleClass ().add (PADDING_LEFT_BOTTOM_RIGHT);
        final ObservableList<Tab> tabs = this.sourceTabPane.getTabs ();
        for (final IDetector<?> detector: this.backend.getDetectors ())
        {
            final ICoreTaskSettings userInterface = detector.getSettings ();
            final Tab tab = new Tab (detector.getName (), userInterface.getEditPane ());
            tab.setClosable (false);
            tabs.add (tab);
            this.sourceTabs.put (tab, detector);
        }
        setTabPaneLeftTabsHorizontal (this.sourceTabPane);

        // Rename CSV file section
        final BoxPanel srcRenamingCheckboxPanel = new BoxPanel (Orientation.HORIZONTAL, false);
        this.renameCheckbox = srcRenamingCheckboxPanel.createCheckBox ("@IDS_MAIN_RENAMING", "@IDS_MAIN_RENAMING_TOOLTIP");
        this.renameCheckbox.getStyleClass ().add ("paddingRight");
        this.renameCheckbox.setOnAction (_ -> this.updateRenamingControls ());
        this.renameFilePathSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_RENAMING_FILE"));
        this.renameFilePathSelectButton.setOnAction (_ -> {

            final Optional<File> file = Functions.getFileFromUser (this.getStage (), true, Functions.getText ("@IDS_MAIN_SELECT_RENAMING_FILE_HEADER"), this.config, new FileChooser.ExtensionFilter (Functions.getText ("@IDS_MAIN_SELECT_RENAMING_FILE_DESCRIPTION"), Functions.getText ("@IDS_MAIN_SELECT_RENAMING_FILE_FILTER")));
            if (file.isPresent ())
                this.renameFilePathField.setText (file.get ().getAbsolutePath ());

        });
        final BorderPane sourceBottomPane = new BorderPane (this.renameFilePathField, null, this.renameFilePathSelectButton, null, srcRenamingCheckboxPanel.getPane ());
        sourceBottomPane.getStyleClass ().add ("paddingRenameBar");

        final BorderPane sourcePane = new BorderPane (this.sourceTabPane);
        sourcePane.setTop (sourceUpperPart.getPane ());
        sourcePane.setBottom (sourceBottomPane);

        /////////////////////////////////////////////////////////////////////////////
        // Destination pane

        final BorderPane destinationFolderPanel = new BorderPane (this.destinationPathField);

        this.destinationFolderSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_DESTINATION"));
        this.destinationFolderSelectButton.setTooltip (new Tooltip (Functions.getText ("@IDS_MAIN_SELECT_DESTINATION_TOOLTIP")));
        this.destinationFolderSelectButton.setOnAction (_ -> this.selectDestinationFolder ());
        destinationFolderPanel.setRight (this.destinationFolderSelectButton);

        final BoxPanel destinationUpperPart = new BoxPanel (Orientation.VERTICAL);
        final TitledSeparator destinationHeader = new TitledSeparator ("@IDS_MAIN_DESTINATION_HEADER");
        destinationHeader.setLabelFor (this.destinationPathField);
        destinationUpperPart.addComponent (destinationHeader);
        destinationUpperPart.addComponent (destinationFolderPanel);
        this.destinationPathField.setMaxWidth (Double.MAX_VALUE);

        this.destinationTabPane.getStyleClass ().add (PADDING_LEFT_BOTTOM_RIGHT);
        final ObservableList<Tab> destinationTabs = this.destinationTabPane.getTabs ();
        for (final ICreator<?> creator: this.backend.getCreators ())
        {
            final ICoreTaskSettings userInterface = creator.getSettings ();
            final Tab tab = new Tab (creator.getName (), userInterface.getEditPane ());
            tab.setClosable (false);
            destinationTabs.add (tab);
            this.creatorTabs.put (tab, creator);
        }
        setTabPaneLeftTabsHorizontal (this.destinationTabPane);

        final BoxPanel bottomLeft = new BoxPanel (Orientation.HORIZONTAL);
        this.createFolderStructure = bottomLeft.createCheckBox ("@IDS_MAIN_CREATE_FOLDERS", "@IDS_MAIN_CREATE_FOLDERS_TOOLTIP");
        this.createFolderStructure.setSelected (true);
        this.addNewFiles = bottomLeft.createCheckBox ("@IDS_MAIN_ADD_NEW", "@IDS_MAIN_ADD_NEW_TOOLTIP");

        final BoxPanel bottomRight = new BoxPanel (Orientation.HORIZONTAL);
        this.enableDarkMode = bottomRight.createCheckBox ("@IDS_MAIN_ENABLE_DARK_MODE", "@IDS_MAIN_ENABLE_DARK_MODE_TOOLTIP");
        this.enableDarkMode.selectedProperty ().addListener ( (_, _, isSelected) -> this.setDarkMode (isSelected.booleanValue ()));

        this.configureDestinationTypePane ();

        final BorderPane destinationCenterPane = new BorderPane (this.destinationTabPane);
        destinationCenterPane.setBottom (this.destinationTypeTabPane);

        final BorderPane destinationPane = new BorderPane (destinationCenterPane);
        destinationPane.setTop (destinationUpperPart.getPane ());
        destinationPane.setBottom (new BorderPane (null, null, bottomRight.getPane (), null, bottomLeft.getPane ()));

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

        // The execution button panel
        final ButtonPanel exButtonPanel = new ButtonPanel (Orientation.VERTICAL);

        this.cancelButton = setupButton (exButtonPanel, "Cancel", "@IDS_EXEC_CANCEL", "@IDS_EXEC_CANCEL_TOOLTIP");
        this.cancelButton.setOnAction (_ -> this.cancelExecution ());
        this.closeButton = setupButton (exButtonPanel, "Close", "@IDS_EXEC_CLOSE", "@IDS_EXEC_CLOSE_TOOLTIP");
        this.closeButton.setOnAction (_ -> this.closeExecution ());

        this.executePane.setCenter (this.loggingArea);
        this.executePane.setRight (exButtonPanel.getPane ());
        this.executePane.setVisible (false);

        final StackPane stackPane = new StackPane (this.mainPane, this.executePane);
        this.setCenterNode (stackPane);

        this.loadConfiguration ();

        this.updateTitle (null);
        this.sourcePathField.requestFocus ();

        this.configureTraversalManager ();
    }


    private void configureDestinationTypePane ()
    {
        final ObservableList<Tab> destinationTypeTabs = this.destinationTypeTabPane.getTabs ();
        this.destinationTypeTabPane.getStyleClass ().add (PADDING_LEFT_BOTTOM_RIGHT);

        // Add the preset destination type
        Tab tab = new Tab (Functions.getMessage ("IDS_DEST_TYPE_PRESET"), new BorderPane ());
        tab.setTooltip (new Tooltip (Functions.getMessage ("IDS_DEST_TYPE_PRESET_INFO")));
        tab.setClosable (false);
        destinationTypeTabs.add (tab);

        // Add the preset library destination type
        BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        this.presetLibraryFilename = panel.createField ("@IDS_COMBINE_LIBRARY_FILENAME");
        tab = new Tab (Functions.getMessage ("IDS_DEST_TYPE_LIBRARY"), panel.getPane ());
        tab.setTooltip (new Tooltip (Functions.getMessage ("IDS_DEST_TYPE_LIBRARY_INFO")));
        tab.setClosable (false);
        destinationTypeTabs.add (tab);

        // Add the performance destination type
        tab = new Tab (Functions.getMessage ("IDS_DEST_TYPE_PERFORMANCE"), new BorderPane ());
        tab.setTooltip (new Tooltip (Functions.getMessage ("IDS_DEST_TYPE_PERFORMANCE_INFO")));
        tab.setClosable (false);
        destinationTypeTabs.add (tab);

        // Add the performance library destination type
        panel = new BoxPanel (Orientation.VERTICAL);
        this.performanceLibraryFilename = panel.createField ("@IDS_COMBINE_LIBRARY_FILENAME");
        tab = new Tab (Functions.getMessage ("IDS_DEST_TYPE_PERFORMANCE_LIBRARY"), panel.getPane ());
        tab.setTooltip (new Tooltip (Functions.getMessage ("IDS_DEST_TYPE_PERFORMANCE_LIBRARY_INFO")));
        tab.setClosable (false);
        destinationTypeTabs.add (tab);

        setTabPaneLeftTabsHorizontal (this.destinationTypeTabPane);

        this.destinationTypeTabPane.getSelectionModel ().selectedIndexProperty ().addListener (_ -> this.updateFormats ());
    }


    private void updateFormats ()
    {
        final int selectedType = this.destinationTypeTabPane.getSelectionModel ().getSelectedIndex ();
        boolean needsSelection = false;

        // Enable only the destination formats which support the selected output type
        for (final Tab destinationTab: this.destinationTabPane.getTabs ())
        {
            final ICreator<?> creator = this.creatorTabs.get (destinationTab);
            final boolean showTab = selectedType == DEST_TYPE_PRESET || selectedType == DEST_TYPE_PRESET_LIBRARY && creator.supportsPresetLibraries () || selectedType == DEST_TYPE_PERFORMANCE && creator.supportsPerformances () || selectedType == DEST_TYPE_PERFORMANCE_LIBRARY && creator.supportsPerformanceLibraries ();
            destinationTab.setDisable (!showTab);
            if (!showTab && destinationTab.isSelected ())
                needsSelection = true;
        }
        if (needsSelection)
        {
            // Select the first enabled destination format, if required
            for (final Tab destinationTab: this.destinationTabPane.getTabs ())
                if (!destinationTab.isDisabled ())
                {
                    this.destinationTabPane.getSelectionModel ().select (destinationTab);
                    break;
                }

            needsSelection = false;
        }

        // Enable only the source formats which support the selected output type
        for (final Tab sourceTab: this.sourceTabPane.getTabs ())
        {
            final IDetector<?> detector = this.sourceTabs.get (sourceTab);
            final boolean showTab = selectedType != DEST_TYPE_PERFORMANCE && selectedType != DEST_TYPE_PERFORMANCE_LIBRARY || detector.supportsPerformances ();
            sourceTab.setDisable (!showTab);
            if (!showTab && sourceTab.isSelected ())
                needsSelection = true;
        }
        if (needsSelection)
            // Select the first enabled source format, if required
            for (final Tab sourceTab: this.sourceTabPane.getTabs ())
                if (!sourceTab.isDisabled ())
                {
                    this.sourceTabPane.getSelectionModel ().select (sourceTab);
                    break;
                }
    }


    private void configureTraversalManager ()
    {
        this.traversalManager.add (this.sourcePathField);
        this.traversalManager.add (this.sourceFolderSelectButton);
        this.traversalManager.add (this.sourceTabPane);
        for (final Tab tab: this.sourceTabPane.getTabs ())
            if (tab.getContent () instanceof final Parent content)
                this.traversalManager.addChildren (content);

        this.traversalManager.add (this.destinationPathField);
        this.traversalManager.add (this.destinationFolderSelectButton);
        this.traversalManager.add (this.destinationTabPane);
        for (final Tab tab: this.destinationTabPane.getTabs ())
            if (tab.getContent () instanceof final Parent content)
                this.traversalManager.addChildren (content);
        this.traversalManager.add (this.destinationTypeTabPane);
        for (final Tab tab: this.destinationTypeTabPane.getTabs ())
            if (tab.getContent () instanceof final Parent content)
                this.traversalManager.addChildren (content);

        this.traversalManager.add (this.convertButton);
        this.traversalManager.add (this.analyseButton);

        this.traversalManager.add (this.renameCheckbox);
        this.traversalManager.add (this.renameFilePathField);
        this.traversalManager.add (this.renameFilePathSelectButton);
        this.traversalManager.add (this.createFolderStructure);
        this.traversalManager.add (this.addNewFiles);
        this.traversalManager.add (this.enableDarkMode);

        this.traversalManager.add (this.cancelButton);
        this.traversalManager.add (this.closeButton);
        this.traversalManager.add (this.loggingArea);

        this.traversalManager.register (this.getStage ());
    }


    private void setDarkMode (final boolean isSelected)
    {
        final ObservableList<String> stylesheets = this.scene.getStylesheets ();
        final String stylesheet = this.startPath + "/css/Darkmode.css";
        if (isSelected)
        {
            if (!stylesheets.contains (stylesheet))
            {
                stylesheets.add (stylesheet);
                this.loggingArea.setBlendMode (BlendMode.OVERLAY);
            }
        }
        else
        {
            stylesheets.remove (stylesheet);
            this.loggingArea.setBlendMode (BlendMode.DARKEN);
        }
    }


    /**
     * Load configuration settings.
     */
    private void loadConfiguration ()
    {
        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
        {
            final String sourcePath = this.config.getProperty (SOURCE_PATH + i);
            if (sourcePath == null || sourcePath.isBlank ())
                break;
            if (!this.sourcePathHistory.contains (sourcePath))
                this.sourcePathHistory.add (sourcePath);
        }
        this.sourcePathField.getItems ().addAll (this.sourcePathHistory);
        this.sourcePathField.setEditable (true);
        if (!this.sourcePathHistory.isEmpty ())
            this.sourcePathField.getEditor ().setText (this.sourcePathHistory.get (0));

        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
        {
            final String destinationPath = this.config.getProperty (DESTINATION_PATH + i);
            if (destinationPath == null || destinationPath.isBlank ())
                break;
            if (!this.destinationPathHistory.contains (destinationPath))
                this.destinationPathHistory.add (destinationPath);
        }
        this.destinationPathField.getItems ().addAll (this.destinationPathHistory);
        this.destinationPathField.setEditable (true);
        if (!this.destinationPathHistory.isEmpty ())
            this.destinationPathField.getEditor ().setText (this.destinationPathHistory.get (0));

        final String renamingFilePath = this.config.getProperty (RENAMING_CSV_FILE);
        if (renamingFilePath != null)
            this.renameFilePathField.setText (renamingFilePath);

        this.createFolderStructure.setSelected (this.config.getBoolean (DESTINATION_CREATE_FOLDER_STRUCTURE, true));
        this.addNewFiles.setSelected (this.config.getBoolean (DESTINATION_ADD_NEW_FILES, false));
        final boolean isDarkmode = this.config.getBoolean (ENABLE_DARK_MODE, false);
        this.enableDarkMode.setSelected (isDarkmode);
        this.setDarkMode (isDarkmode);
        this.renameCheckbox.setSelected (this.config.getBoolean (RENAMING_SOURCE_ENABLED, false));

        this.updateRenamingControls ();

        for (final IDetector<?> detector: this.backend.getDetectors ())
            detector.getSettings ().loadSettings (this.config);
        for (final ICreator<?> creator: this.backend.getCreators ())
            creator.getSettings ().loadSettings (this.config);

        final int sourceFormat = this.config.getInteger (SOURCE_TYPE, 0);
        this.sourceTabPane.getSelectionModel ().select (sourceFormat);
        final int destinationFormat = this.config.getInteger (DESTINATION_FORMAT, 0);
        this.destinationTabPane.getSelectionModel ().select (destinationFormat);

        final int destinationType = this.config.getInteger (DESTINATION_TYPE, DEST_TYPE_PRESET);
        this.destinationTypeTabPane.getSelectionModel ().select (destinationType);

        this.presetLibraryFilename.setText (this.config.getProperty (PRESET_LIBRARY_FILENAME, ""));
        this.performanceLibraryFilename.setText (this.config.getProperty (PERFORMANCE_LIBRARY_FILENAME, ""));
    }


    /**
     * Save the configuration.
     */
    private void saveConfiguration ()
    {
        updateHistory (this.sourcePathField.getEditor ().getText (), this.sourcePathHistory);
        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
            this.config.setProperty (SOURCE_PATH + i, this.sourcePathHistory.size () > i ? this.sourcePathHistory.get (i) : "");

        updateHistory (this.destinationPathField.getEditor ().getText (), this.destinationPathHistory);
        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
            this.config.setProperty (DESTINATION_PATH + i, this.destinationPathHistory.size () > i ? this.destinationPathHistory.get (i) : "");

        this.config.setProperty (RENAMING_CSV_FILE, this.renameFilePathField.getText ());
        this.config.setBoolean (DESTINATION_CREATE_FOLDER_STRUCTURE, this.createFolderStructure.isSelected ());
        this.config.setBoolean (DESTINATION_ADD_NEW_FILES, this.addNewFiles.isSelected ());
        this.config.setBoolean (ENABLE_DARK_MODE, this.enableDarkMode.isSelected ());
        this.config.setBoolean (RENAMING_SOURCE_ENABLED, this.renameCheckbox.isSelected ());

        for (final IDetector<?> detector: this.backend.getDetectors ())
            detector.getSettings ().saveSettings (this.config);
        for (final ICreator<?> creator: this.backend.getCreators ())
            creator.getSettings ().saveSettings (this.config);

        final int sourceSelectedIndex = this.sourceTabPane.getSelectionModel ().getSelectedIndex ();
        this.config.setInteger (SOURCE_TYPE, sourceSelectedIndex);
        final int destinationSelectedIndex = this.destinationTabPane.getSelectionModel ().getSelectedIndex ();
        this.config.setInteger (DESTINATION_FORMAT, destinationSelectedIndex);

        final int destinationTypeSelectedIndex = this.destinationTypeTabPane.getSelectionModel ().getSelectedIndex ();
        this.config.setInteger (DESTINATION_TYPE, destinationTypeSelectedIndex);

        this.config.setProperty (PRESET_LIBRARY_FILENAME, this.presetLibraryFilename.getText ());
        this.config.setProperty (PERFORMANCE_LIBRARY_FILENAME, this.performanceLibraryFilename.getText ());
    }


    /** {@inheritDoc} */
    @Override
    public void exit ()
    {
        for (final IDetector<?> detector: this.backend.getDetectors ())
            detector.shutdown ();

        this.saveConfiguration ();
        // Store configuration
        super.exit ();

        Platform.exit ();
    }


    /**
     * Execute the conversion.
     *
     * @param onlyAnalyse Do not create output files if true
     */
    private void execute (final boolean onlyAnalyse)
    {
        if (!this.verifyFolders () || !this.verifyRenameFile ())
            return;

        final int selectedDetector = this.sourceTabPane.getSelectionModel ().getSelectedIndex ();
        final int selectedCreator = this.destinationTabPane.getSelectionModel ().getSelectedIndex ();
        if (selectedDetector < 0 || selectedCreator < 0)
            return;
        final IDetector<?> detector = this.backend.getDetectors ()[selectedDetector];
        final ICreator<?> creator = this.backend.getCreators ()[selectedCreator];
        if (!detector.getSettings ().checkSettingsUI (this) || !creator.getSettings ().checkSettingsUI (this))
            return;

        this.clearLog ();

        this.mainPane.setVisible (false);
        this.executePane.setVisible (true);

        final int selectedType = this.destinationTypeTabPane.getSelectionModel ().getSelectedIndex ();
        final boolean detectPerformances = selectedType == DEST_TYPE_PERFORMANCE || selectedType == DEST_TYPE_PERFORMANCE_LIBRARY;
        final boolean wantsMultipleFiles = detectPerformances ? this.wantsMultiplePerformanceFiles () : this.wantsMultiplePresetFiles ();
        final String libraryName = (detectPerformances ? this.performanceLibraryFilename : this.presetLibraryFilename).getText ().trim ();

        Platform.runLater ( () -> this.backend.detect (detector, creator, this.sourceFolder, this.outputFolder, this.csvRenameFile, libraryName, detectPerformances, wantsMultipleFiles, this.createFolderStructure.isSelected (), onlyAnalyse));
    }


    /**
     * Cancel button was pressed.
     */
    private void cancelExecution ()
    {
        this.backend.cancelExecution ();
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
        this.sourceFolder = new File (this.sourcePathField.getEditor ().getText ());
        if (!this.sourceFolder.exists () || !this.sourceFolder.isDirectory ())
        {
            Functions.message ("@IDS_NOTIFY_FOLDER_DOES_NOT_EXIST", this.sourceFolder.getAbsolutePath ());
            this.sourcePathField.requestFocus ();
            return false;
        }
        this.sourcePathHistory.add (0, this.sourceFolder.getAbsolutePath ());

        // Check output folder
        this.outputFolder = new File (this.destinationPathField.getEditor ().getText ());
        if (!this.outputFolder.exists () && !this.outputFolder.mkdirs ())
        {
            Functions.message ("@IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", this.outputFolder.getAbsolutePath ());
            this.destinationPathField.requestFocus ();
            return false;
        }
        if (!this.outputFolder.isDirectory ())
        {
            Functions.message ("@IDS_NOTIFY_FOLDER_DESTINATION_NOT_A_FOLDER", this.outputFolder.getAbsolutePath ());
            this.destinationPathField.requestFocus ();
            return false;
        }
        this.destinationPathHistory.add (0, this.outputFolder.getAbsolutePath ());

        // Output folder must be empty or add new must be active
        return this.addNewFiles.isSelected () || this.isEmptyFolder (this.outputFolder.getPath ());
    }


    /**
     * Set and check folder for existence.
     *
     * @return True if OK
     */
    private boolean verifyRenameFile ()
    {
        this.csvRenameFile.clear ();

        if (!this.renameCheckbox.isSelected ())
            return true;

        final String renamingCSVFile = this.renameFilePathField.getText ();
        if (renamingCSVFile.isBlank ())
        {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_NO_FILE_SPECIFIED");
            this.renameFilePathField.requestFocus ();
            return false;
        }

        final File renamingCSV = new File (renamingCSVFile);
        if (!renamingCSV.exists ())
        {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_DOES_NOT_EXIST", renamingCSVFile);
            this.renameFilePathField.requestFocus ();
            return false;
        }

        if (!renamingCSV.canRead ())
        {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_NOT_READABLE", renamingCSVFile);
            this.renameFilePathField.requestFocus ();
            return false;
        }

        try
        {
            this.csvRenameFile.setRenameFile (renamingCSV);
        }
        catch (final IllegalArgumentException ex)
        {
            Functions.message (ex.getMessage ());
            this.renameFilePathField.requestFocus ();
            return false;
        }
        return true;
    }


    private boolean wantsMultiplePresetFiles ()
    {
        return this.destinationTypeTabPane.getSelectionModel ().getSelectedIndex () == DEST_TYPE_PRESET_LIBRARY;
    }


    private boolean wantsMultiplePerformanceFiles ()
    {
        return this.destinationTypeTabPane.getSelectionModel ().getSelectedIndex () == DEST_TYPE_PERFORMANCE_LIBRARY;
    }


    private void clearLog ()
    {
        this.loggingArea.clear ();
        this.loggingArea.autoScrollToTailProperty ().set (true);

        try
        {
            this.logWriter = new FileWriter (new File (this.outputFolder, "ConvertWithMoss.log"));
        }
        catch (final IOException ex)
        {
            this.logger.error (Functions.getMessage ("@IDS_NOTIFY_ERR_NO_LOG_FILE", ex.getLocalizedMessage ()));
            this.logWriter = null;
        }
    }


    /** {@inheritDoc} */
    @Override
    public void log (final String messageID, final String... replaceStrings)
    {
        this.logText (Functions.getMessage (messageID, replaceStrings));
    }


    /** {@inheritDoc} */
    @Override
    public void logText (final String text)
    {
        final boolean combine = this.combineWithPreviousMessage;
        this.combineWithPreviousMessage = !text.endsWith ("\n");
        this.logger.info (text, combine);
        this.logToFile (text);
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final String messageID, final String... replaceStrings)
    {
        this.logErrorText (Functions.getMessage (messageID, replaceStrings));
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final String messageID, final Throwable throwable)
    {
        throwable.printStackTrace ();
        this.logErrorText (Functions.getMessage (messageID, throwable));
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final Throwable throwable)
    {
        this.logError (throwable, true);
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final Throwable throwable, final boolean logExceptionStack)
    {
        String message = throwable.getMessage ();
        if (message == null)
            message = throwable.getClass ().getName ();
        if (logExceptionStack)
        {
            final StringBuilder sb = new StringBuilder (message).append ('\n');
            final StringWriter sw = new StringWriter ();
            final PrintWriter pw = new PrintWriter (sw);
            throwable.printStackTrace (pw);
            sb.append (sw.toString ()).append ('\n');
            message = sb.toString ();
        }
        this.logErrorText (message);
    }


    private void logErrorText (final String message)
    {
        this.logger.error (message);
        this.logToFile (message);
    }


    private void logToFile (final String message)
    {
        if (this.logWriter != null)
            try
            {
                this.logWriter.append (message);
            }
            catch (final IOException ex)
            {
                // Ignore
            }
    }


    /** {@inheritDoc} */
    @Override
    public void updateButtonStates (final boolean canClose)
    {
        Platform.runLater ( () -> {

            this.cancelButton.setDisable (canClose);
            this.closeButton.setDisable (!canClose);
            if (!this.cancelButton.isDisabled ())
            {
                this.cancelButton.setDefaultButton (true);
                this.cancelButton.requestFocus ();
                this.loggingArea.setAccessibleText (Functions.getMessage ("IDS_NOTIFY_PROCESSING"));
            }
            else
            {
                this.closeButton.setDefaultButton (true);
                this.closeButton.requestFocus ();
                this.loggingArea.setAccessibleText (Functions.getMessage ("IDS_NOTIFY_FINISHED"));
            }

            if (canClose && this.logWriter != null)
            {
                try
                {
                    this.logWriter.close ();
                }
                catch (final IOException ex)
                {
                    // Ignore
                }
                this.logWriter = null;
            }

        });
    }


    private void selectSourcePath ()
    {
        final File currentSourcePath = new File (this.sourcePathField.getEditor ().getText ());
        if (currentSourcePath.exists () && currentSourcePath.isDirectory ())
            this.config.setActivePath (currentSourcePath);
        final Optional<File> file = Functions.getFolderFromUser (this.getStage (), this.config, "@IDS_MAIN_SELECT_SOURCE_HEADER");
        if (file.isPresent ())
            this.sourcePathField.getEditor ().setText (file.get ().getAbsolutePath ());
    }


    private void selectDestinationFolder ()
    {
        final File currentDestinationPath = new File (this.destinationPathField.getEditor ().getText ());
        if (currentDestinationPath.exists () && currentDestinationPath.isDirectory ())
            this.config.setActivePath (currentDestinationPath);
        final Optional<File> file = Functions.getFolderFromUser (this.getStage (), this.config, "@IDS_MAIN_SELECT_DESTINATION_HEADER");
        if (file.isPresent ())
            this.destinationPathField.getEditor ().setText (file.get ().getAbsolutePath ());
    }


    /** {@inheritDoc} */
    @Override
    public void finished (final boolean cancelled)
    {
        // Creates libraries if requested
        this.backend.finish (cancelled);

        // Workaround to always scroll fully to the end of the log
        this.loggingArea.autoScrollToTailProperty ().set (false);
        final PauseTransition delay = new PauseTransition (Duration.millis (1000));
        delay.setOnFinished (_ -> this.loggingArea.scrollTo (this.loggingArea.getItems ().size () - 1));
        delay.play ();

        this.updateButtonStates (true);
    }


    private static Button setupButton (final BasePanel panel, final String iconName, final String labelName, final String mnemonic) throws EndApplicationException
    {
        Image icon;
        try
        {
            icon = Functions.iconFor ("de/mossgrabers/convertwithmoss/images/" + iconName + ".png");
        }
        catch (final IOException ex)
        {
            throw new EndApplicationException (ex);
        }
        final Button button = panel.createButton (icon, labelName, mnemonic);
        button.alignmentProperty ().set (Pos.CENTER_LEFT);
        button.graphicTextGapProperty ().set (12);
        return button;
    }


    private static void setTabPaneLeftTabsHorizontal (final TabPane tabPane)
    {
        tabPane.setSide (Side.LEFT);
        tabPane.setRotateGraphic (true);
        tabPane.setTabMinHeight (160); // Determines tab width. I know, its odd.
        tabPane.setTabMaxHeight (200);
        tabPane.getStyleClass ().add ("horizontal-tab-pane");

        for (final Tab tab: tabPane.getTabs ())
        {
            final Label l = new Label ("    ");
            l.setVisible (false);
            l.setMaxHeight (0);
            l.setPrefHeight (0);
            tab.setGraphic (l);

            Platform.runLater ( () -> rotateTabLabels (tab));
        }
    }


    private static void rotateTabLabels (final Tab tab)
    {
        // Get the "tab-container" node. This is what we want to rotate/shift for easy
        // left-alignment.
        final Parent parent = tab.getGraphic ().getParent ();
        if (parent == null)
        {
            Platform.runLater ( () -> rotateTabLabels (tab));
            return;
        }
        final Parent tabContainer = parent.getParent ();
        tabContainer.setRotate (90);
        // By default the display will originate from the center.
        // Applying a negative Y transformation will move it left.
        tabContainer.setTranslateY (-80);
    }


    private static void updateHistory (final String newItem, final List<String> history)
    {
        history.remove (newItem);
        history.add (0, newItem);
    }


    /**
     * Checks if folder is empty. Ignores OS thumb-nail files like .DS_Store on MAC and Thumbs.db on
     * Windows.
     *
     * @param directoryPath Path of folder to check
     * @return True if directory is empty
     */
    private boolean isEmptyFolder (final String directoryPath)
    {
        boolean result = true;
        try (final Stream<Path> paths = Files.list (Path.of (directoryPath)))
        {
            result = paths.filter (path -> !Pattern.matches ("^(\\.(DS_Store|desktop)|Thumbs.db|ConvertWithMoss.log)$", path.getFileName ().toString ())).count () == 0;
        }
        catch (final IOException ex)
        {
            result = false;
        }

        if (!result)
        {
            Functions.message ("@IDS_NOTIFY_FOLDER_MUST_BE_EMPTY");
            this.destinationPathField.requestFocus ();
        }

        return result;
    }
}
