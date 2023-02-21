// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.ICreator;
import de.mossgrabers.convertwithmoss.core.detector.IDetector;
import de.mossgrabers.convertwithmoss.format.akai.MPCKeygroupCreator;
import de.mossgrabers.convertwithmoss.format.akai.MPCKeygroupDetector;
import de.mossgrabers.convertwithmoss.format.bitwig.BitwigMultisampleCreator;
import de.mossgrabers.convertwithmoss.format.bitwig.BitwigMultisampleDetector;
import de.mossgrabers.convertwithmoss.format.decentsampler.DecentSamplerCreator;
import de.mossgrabers.convertwithmoss.format.decentsampler.DecentSamplerDetector;
import de.mossgrabers.convertwithmoss.format.kmp.KMPCreator;
import de.mossgrabers.convertwithmoss.format.kmp.KMPDetector;
import de.mossgrabers.convertwithmoss.format.korgmultisample.KorgmultisampleCreator;
import de.mossgrabers.convertwithmoss.format.korgmultisample.KorgmultisampleDetector;
import de.mossgrabers.convertwithmoss.format.nki.NkiDetector;
import de.mossgrabers.convertwithmoss.format.sf2.Sf2Detector;
import de.mossgrabers.convertwithmoss.format.sfz.SfzCreator;
import de.mossgrabers.convertwithmoss.format.sfz.SfzDetector;
import de.mossgrabers.convertwithmoss.format.wav.WavCreator;
import de.mossgrabers.convertwithmoss.format.wav.WavDetector;
import de.mossgrabers.tools.ui.AbstractFrame;
import de.mossgrabers.tools.ui.DefaultApplication;
import de.mossgrabers.tools.ui.EndApplicationException;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.control.LoggerBox;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BasePanel;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import de.mossgrabers.tools.ui.panel.ButtonPanel;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;


/**
 * The sample converter application.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class ConvertWithMossApp extends AbstractFrame implements INotifier, Consumer<IMultisampleSource>
{
    private static final String ENABLE_DARK_MODE                    = "EnableDarkMode";
    private static final String DESTINATION_CREATE_FOLDER_STRUCTURE = "DestinationCreateFolderStructure";
    private static final String DESTINATION_ADD_NEW_FILES           = "DestinationAddNewFiles";
    private static final String DESTINATION_PATH                    = "DestinationPath";
    private static final String DESTINATION_TYPE                    = "DestinationType";
    private static final String SOURCE_PATH                         = "SourcePath";
    private static final String SOURCE_TYPE                         = "SourceType";
    private static final String RENAMING_CSV_FILE                   = "RenamingCSVFile";
    private static final String RENAMING_SOURCE_ENABLED             = "EnableRenaming";

    private static final char   COMMA_DELIMITER                     = ',';
    private static final char   QUOTATION_MARK                      = '"';
    
    private final IDetector []  detectors;
    private final ICreator []   creators;

    private BorderPane          mainPane;
    private BorderPane          executePane;
    private TextField           sourcePathField;
    private TextField           destinationPathField;
    private TextField           renamingCSVFileField;
    private File                sourceFolder;
    private File                outputFolder;
    private CheckBox            createFolderStructure;
    private CheckBox            renameSource;
    private CheckBox            addNewFiles;
    private CheckBox            enableDarkMode;

    private TabPane             sourceTabPane;
    private TabPane             destinationTabPane;

    private boolean             onlyAnalyse                         = true;
    private Button              closeButton;
    private Button              cancelButton;
    private Button              renamingFileSelectButton;
    private final LoggerBox     loggingArea                         = new LoggerBox ();
    
    private Map<String, String> renamingTable = new HashMap<>();

    /**
     * Main-method.
     *
     * @param args The startup arguments
     */
    public static void main (final String [] args)
    {
        Application.launch (DefaultApplication.class, ConvertWithMossApp.class.getName ());
    }


    /**
     * Constructor.
     *
     * @throws EndApplicationException Startup crash
     */
    public ConvertWithMossApp () throws EndApplicationException
    {
        super ("de/mossgrabers/convertwithmoss", 1000, 800);

        this.detectors = new IDetector []
        {
            new WavDetector (this),
            new BitwigMultisampleDetector (this),
            new SfzDetector (this),
            new Sf2Detector (this),
            new DecentSamplerDetector (this),
            new NkiDetector (this),
            new MPCKeygroupDetector (this),
            new KorgmultisampleDetector (this),
            new KMPDetector (this)
        };

        this.creators = new ICreator []
        {
            new WavCreator (this),
            new BitwigMultisampleCreator (this),
            new SfzCreator (this),
            new DecentSamplerCreator (this),
            new MPCKeygroupCreator (this),
            new KorgmultisampleCreator (this),
            new KMPCreator (this)
        };
    }


    /**
     * Enables/disables the renaming controls depending on the selection status of the renaming checkbox.
     */
    private void updateRenamingControls() {
        this.renamingCSVFileField.setDisable (!this.renameSource.isSelected());
        this.renamingFileSelectButton.setDisable (!this.renameSource.isSelected());
    }
    
    /** {@inheritDoc} */
    @Override
    public void initialise (final Stage stage, final Optional<String> baseTitleOptional) throws EndApplicationException
    {
        super.initialise (stage, baseTitleOptional, true, true, true);

        // The main button panel
        final ButtonPanel buttonPanel = new ButtonPanel (Orientation.VERTICAL);

        final Button convertButton = setupButton (buttonPanel, "Convert", "@IDS_MAIN_CONVERT");
        convertButton.setOnAction (event -> this.execute (false));
        final Button analyseButton = setupButton (buttonPanel, "Analyse", "@IDS_MAIN_ANALYSE");
        analyseButton.setOnAction (event -> this.execute (true));

        // Source pane
        final BorderPane sourcePane = new BorderPane ();

        this.sourcePathField = new TextField ();
        final BorderPane sourceFolderPanel = new BorderPane (this.sourcePathField);

        final Button sourceFolderSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_SOURCE"));
        sourceFolderSelectButton.setOnAction (event -> {

            final Optional<File> file = Functions.getFolderFromUser (this.getStage (), this.config, "@IDS_MAIN_SELECT_SOURCE_HEADER");
            if (file.isPresent ())
                this.sourcePathField.setText (file.get ().getAbsolutePath ());

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
        for (final IDetector detector: this.detectors)
        {
            final Tab tab = new Tab (detector.getName (), detector.getEditPane ());
            tab.setClosable (false);
            tabs.add (tab);
        }
        
        

        final BoxPanel srcRenamingCheckboxPanel = new BoxPanel (Orientation.HORIZONTAL);
        this.renameSource = srcRenamingCheckboxPanel.createCheckBox ("@IDS_MAIN_RENAMING", "@IDS_MAIN_RENAMING_TOOLTIP");
        this.renameSource.setSelected (false);

        this.renamingCSVFileField = new TextField ();
        this.renamingCSVFileField.setDisable(true);
        final BorderPane sourceFolderRenamingPathPanel = new BorderPane (this.renamingCSVFileField);
        renamingFileSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_RENAMING_FILE"));
        final BoxPanel srcRenamingPanel = new BoxPanel(Orientation.HORIZONTAL);
        srcRenamingPanel.addComponent(renamingFileSelectButton);
        renamingFileSelectButton.setDisable(true);
        this.renameSource.setSelected(false);
       
        renamingFileSelectButton.setOnAction (event -> {        	
            final Optional<File> file = Functions.getFileFromUser (this.getStage ().getOwner(), true, Functions.getText("@IDS_MAIN_SELECT_RENAMING_FILE_HEADER"), this.config, new FileChooser.ExtensionFilter(Functions.getText("@IDS_MAIN_SELECT_RENAMING_FILE_DESCRIPTION"), Functions.getText("@IDS_MAIN_SELECT_RENAMING_FILE_FILTER")));
            if (file.isPresent ()) {
            	this.renamingCSVFileField.setText (file.get ().getAbsolutePath ());
            }
        });

        renameSource.setOnAction(event -> {
        	updateRenamingControls();
        });

        updateRenamingControls();
        
        sourcePane.setBottom (new BorderPane (sourceFolderRenamingPathPanel, null, srcRenamingPanel.getPane(), null, srcRenamingCheckboxPanel.getPane ()));
        
        // Destination pane
        final BorderPane destinationPane = new BorderPane ();

        this.destinationPathField = new TextField ();
        final BorderPane destinationFolderPanel = new BorderPane (this.destinationPathField);

        final Button destinationFolderSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_DESTINATION"));
        destinationFolderSelectButton.setOnAction (event -> {

            final Optional<File> file = Functions.getFolderFromUser (this.getStage (), this.config, "@IDS_MAIN_SELECT_DESTINATION_HEADER");
            if (file.isPresent ())
                this.destinationPathField.setText (file.get ().getAbsolutePath ());

        });
        destinationFolderPanel.setRight (destinationFolderSelectButton);

        final BoxPanel destinationUpperPart = new BoxPanel (Orientation.VERTICAL);
        destinationUpperPart.addComponent (new TitledSeparator ("@IDS_MAIN_DESTINATION_HEADER"));
        destinationUpperPart.addComponent (destinationFolderPanel);

        destinationPane.setTop (destinationUpperPart.getPane ());
        this.destinationTabPane = new TabPane ();
        this.destinationTabPane.getStyleClass ().add ("paddingLeftBottomRight");
        destinationPane.setCenter (this.destinationTabPane);

        final BoxPanel bottomLeft = new BoxPanel (Orientation.HORIZONTAL);
        this.createFolderStructure = bottomLeft.createCheckBox ("@IDS_MAIN_CREATE_FOLDERS", "@IDS_MAIN_CREATE_FOLDERS_TOOLTIP");
        this.createFolderStructure.setSelected (true);
        this.addNewFiles = bottomLeft.createCheckBox ("@IDS_MAIN_ADD_NEW", "@IDS_MAIN_ADD_NEW_TOOLTIP");

        final BoxPanel bottomRight = new BoxPanel (Orientation.HORIZONTAL);
        this.enableDarkMode = bottomRight.createCheckBox ("@IDS_MAIN_ENABLE_DARK_MODE", "@IDS_MAIN_ENABLE_DARK_MODE_TOOLTIP");
        this.enableDarkMode.selectedProperty ().addListener ( (obs, wasSelected, isSelected) -> {
            final ObservableList<String> stylesheets = this.scene.getStylesheets ();
            final String stylesheet = this.startPath + "/css/Darkmode.css";
            this.loggingArea.setDarkmode (isSelected.booleanValue ());
            if (isSelected.booleanValue ())
                stylesheets.add (stylesheet);
            else
                stylesheets.remove (stylesheet);
        });

        destinationPane.setBottom (new BorderPane (null, null, bottomRight.getPane (), null, bottomLeft.getPane ()));

        final ObservableList<Tab> destinationTabs = this.destinationTabPane.getTabs ();
        for (final ICreator creator: this.creators)
        {
            final Tab tab = new Tab (creator.getName (), creator.getEditPane ());
            tab.setClosable (false);
            destinationTabs.add (tab);
        }

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

        this.cancelButton = setupButton (exButtonPanel, "Cancel", "@IDS_EXEC_CANCEL");
        this.cancelButton.setOnAction (event -> this.cancelExecution ());
        this.closeButton = setupButton (exButtonPanel, "Close", "@IDS_EXEC_CLOSE");
        this.closeButton.setOnAction (event -> this.closeExecution ());

        this.executePane.setCenter (this.loggingArea.getWebView ());
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
        
        final String renamingFilePath = this.config.getProperty (RENAMING_CSV_FILE);
        if (renamingFilePath != null)
            this.renamingCSVFileField.setText (renamingFilePath);        

        this.createFolderStructure.setSelected (this.config.getBoolean (DESTINATION_CREATE_FOLDER_STRUCTURE, true));
        this.addNewFiles.setSelected (this.config.getBoolean (DESTINATION_ADD_NEW_FILES, false));
        this.enableDarkMode.setSelected (this.config.getBoolean (ENABLE_DARK_MODE, false));
        this.renameSource.setSelected(this.config.getBoolean (RENAMING_SOURCE_ENABLED, false));
        
        updateRenamingControls();
        
        for (final IDetector detector: this.detectors)
            detector.loadSettings (this.config);
        for (final ICreator creator: this.creators)
            creator.loadSettings (this.config);

        final int sourceType = this.config.getInteger (SOURCE_TYPE, 0);
        this.sourceTabPane.getSelectionModel ().select (sourceType);
        final int destinationType = this.config.getInteger (DESTINATION_TYPE, 0);
        this.destinationTabPane.getSelectionModel ().select (destinationType);
    }


    /** {@inheritDoc} */
    @Override
    public void exit ()
    {
        for (final IDetector detector: this.detectors)
            detector.shutdown ();

        this.config.setProperty (SOURCE_PATH, this.sourcePathField.getText ());
        this.config.setProperty (DESTINATION_PATH, this.destinationPathField.getText ());
        this.config.setProperty (RENAMING_CSV_FILE, this.renamingCSVFileField.getText());
        this.config.setBoolean (DESTINATION_CREATE_FOLDER_STRUCTURE, this.createFolderStructure.isSelected ());
        this.config.setBoolean (DESTINATION_ADD_NEW_FILES, this.addNewFiles.isSelected ());
        this.config.setBoolean (ENABLE_DARK_MODE, this.enableDarkMode.isSelected ());
        this.config.setBoolean (RENAMING_SOURCE_ENABLED, this.renameSource.isSelected());

        for (final IDetector detector: this.detectors)
            detector.saveSettings (this.config);
        for (final ICreator creator: this.creators)
            creator.saveSettings (this.config);

        final int sourceSelectedIndex = this.sourceTabPane.getSelectionModel ().getSelectedIndex ();
        this.config.setInteger (SOURCE_TYPE, sourceSelectedIndex);
        final int destinationSelectedIndex = this.destinationTabPane.getSelectionModel ().getSelectedIndex ();
        this.config.setInteger (DESTINATION_TYPE, destinationSelectedIndex);

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
        this.onlyAnalyse = onlyAnalyse;

        if (!this.verifyFolders ())
            return;

        if (!this.initFileRenaming ()) 
        	return;
        
        final int selectedDetector = this.sourceTabPane.getSelectionModel ().getSelectedIndex ();
        if (selectedDetector < 0)
            return;

        this.loggingArea.clear ();

        this.mainPane.setVisible (false);
        this.executePane.setVisible (true);

        Platform.runLater ( () -> {
            this.log ("IDS_NOTIFY_DETECTING");
            this.detectors[selectedDetector].detect (this.sourceFolder, this);
        });
    }


	/**
     * Cancel button was pressed.
     */
    private void cancelExecution ()
    {
        final int selectedDetector = this.sourceTabPane.getSelectionModel ().getSelectedIndex ();
        if (selectedDetector >= 0)
            this.detectors[selectedDetector].cancel ();
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
            Functions.message ("@IDS_NOTIFY_FOLDER_DOES_NOT_EXIST", this.sourceFolder.getAbsolutePath ());
            this.sourcePathField.requestFocus ();
            return false;
        }

        if (this.onlyAnalyse)
            return true;

        // Check output folder
        this.outputFolder = new File (this.destinationPathField.getText ());
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

        // Output folder must be empty or add new must be active
        if (!this.addNewFiles.isSelected ())
        {
            final String [] content = this.outputFolder.list ();
            if (content == null || content.length > 0)
            {
                Functions.message ("@IDS_NOTIFY_FOLDER_MUST_BE_EMPTY");
                this.destinationPathField.requestFocus ();
                return false;
            }
        }

        return true;
    }

    /**
     * Parses one line from a csv file consisting of exactly two columns. Writes the first value as a key and the second
     * value as the value of a map entry to the renamingTable.
     * @param line the line read from the CSV file
     * @param lineNumber the line number of the line in the csv file (needed for logging)
     * @param csvFileName the name of the csv file (needed for logging
     * @return true if line could be processed correctly, false else
     */
    private boolean readColumnsFromRenamingCSVLineAndWriteThemToRenamingTable(String line, int lineNumber, String csvFileName) {
        int columnNumber = 0;
        boolean isQuoted = false;

        StringBuilder sb = new StringBuilder();
        String sourceName = null;
        boolean quoteWasJustEnded = false;
        for(int charIdx = 0; charIdx < line.length(); charIdx ++) {
        	char ch = line.charAt(charIdx);
        	switch(ch) {
        	case COMMA_DELIMITER:
        		if(!isQuoted) {
        		    if(columnNumber == 0) {
        		    	sourceName = sb.toString();
        		    	sb.setLength(0);
        		    	columnNumber ++;
        		    }
        		    else {
        	            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_MORE_THAN_TWO_COLUMNS", Integer.toString(lineNumber), csvFileName);
        	            return false;
        		    }
        		}
        		else {
        			sb.append(ch);
        		}
        		quoteWasJustEnded = false;
        		break;
        	case QUOTATION_MARK:
        		if(isQuoted) {
        			isQuoted = false;
        			quoteWasJustEnded = true;
        		}
        		else {
        			if(quoteWasJustEnded)
        				sb.append(ch);
    
        			isQuoted = true;
        			quoteWasJustEnded = false;
        		}
        		break;
        	
        	default:
        	    sb.append(ch);	
        	    quoteWasJustEnded = false;
        	}
        }
        
        if(columnNumber < 1) {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_LESS_THAN_TWO_COLUMNS", Integer.toString(lineNumber), csvFileName);
            return false;		        	
        }
        else {
        	String targetName = sb.toString();
        	sb.setLength(0);
        	renamingTable.put(sourceName, targetName);
        }   
        
        return true;
    }
    
    
    /**
     * Initializes the file renaming by loading the provided csv file if
     * renaming is active.
     * 
     * @return true if renaming is not active or the provided csv file could be loaded successfully, false else.
     */
    private boolean initFileRenaming () {
    	
		if(!this.renameSource.isSelected())
			return true;

		String renamingCSVFile = this.renamingCSVFileField.getText();
		
		if(renamingCSVFile == null) {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_NO_FILE_SPECIFIED");
			return false;
		}
		
		File renamingCSV = new File(renamingCSVFile);

		if(!renamingCSV.exists()) {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_DOES_NOT_EXIST", renamingCSVFile);
            return false;
		}
		
		if(!renamingCSV.canRead()) {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_NOT_READABLE", renamingCSVFile);
            return false;
		}
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(renamingCSV));
			this.renamingTable.clear();
		    String line;
		    int lineNumber = 0;

		    while ((line = br.readLine()) != null) {
		        lineNumber ++;
		        if(!readColumnsFromRenamingCSVLineAndWriteThemToRenamingTable(line, lineNumber, renamingCSVFile)) {
		        	br.close();
		        	return false;
		        }
		    }
		  
		    br.close();
		} catch (FileNotFoundException e) {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_DOES_NOT_EXIST", renamingCSVFile);
            return false;
		}
		catch (IOException e) {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_IO_EXCEPTION", e.getMessage());
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

        this.log ("IDS_NOTIFY_MAPPING", multisampleSource.getMappingName ());

        if(this.renameSource.isSelected())
        	applyRenaming(multisampleSource);
        
        try
        {
            if (this.onlyAnalyse)
                return;

            final boolean createStructure = this.createFolderStructure.isSelected ();
            final File multisampleOutputFolder = calcOutputFolder (this.outputFolder, multisampleSource.getSubPath (), createStructure);
            this.creators[selectedCreator].create (multisampleOutputFolder, multisampleSource);
        }
        catch (final IOException | RuntimeException ex)
        {
            this.logError ("IDS_NOTIFY_SAVE_FAILED", ex.getMessage ());
        }
    }

    /**
     * Applies the renaming of a IMultisampleSource according to the renaming table.
     * @param multisampleSource the multisample source to be renamed.
     */
    private void applyRenaming(IMultisampleSource multisampleSource) {
		String sourceName = multisampleSource.getName();
		
		if(this.renamingTable.containsKey(sourceName)) {
			String targetName = renamingTable.get(sourceName);
			this.log ("IDS_NOTIFY_RENAMING_SOURCE_TO", sourceName, targetName);
			multisampleSource.setName(targetName);
		}
		else {
			this.log ("IDS_NOTIFY_RENAMING_NOT_DEFINED", sourceName);
		}
	}


	/** {@inheritDoc} */
    @Override
    public void log (final String messageID, final String... replaceStrings)
    {
        this.loggingArea.notify (Functions.getMessage (messageID, replaceStrings));
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final String messageID, final String... replaceStrings)
    {
        this.loggingArea.notifyError (Functions.getMessage (messageID, replaceStrings));
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final String messageID, final Throwable throwable)
    {
        this.loggingArea.notifyError (Functions.getMessage (messageID, throwable));
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final Throwable throwable)
    {
        this.loggingArea.notifyError (throwable.getMessage (), throwable);
    }


    /** {@inheritDoc} */
    @Override
    public void logText (final String text)
    {
        this.loggingArea.notify (text);
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
        for (int i = parts.length - 2; i >= 1; i--)
        {
            result = new File (result, parts[i]);
            if (!result.exists () && !result.mkdirs ())
                throw new IOException (Functions.getMessage ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", result.getAbsolutePath ()));
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


    private static Button setupButton (final BasePanel panel, final String iconName, final String labelName)
    {
        final Image icon = Functions.iconFor ("de/mossgrabers/convertwithmoss/images/" + iconName + ".png");
        final Button button = panel.createButton (icon, labelName);
        button.alignmentProperty ().set (Pos.CENTER_LEFT);
        button.graphicTextGapProperty ().set (12);
        return button;
    }
}
