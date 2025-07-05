// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import de.mossgrabers.convertwithmoss.core.creator.ICreator;
import de.mossgrabers.convertwithmoss.core.detector.IDetector;
import de.mossgrabers.convertwithmoss.file.CSVRenameFile;
import de.mossgrabers.tools.ui.EndApplicationException;
import de.mossgrabers.tools.ui.Functions;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.ParseResult;


/**
 * The back-end for the conversion process started from a Command Line Interface (CLI).
 *
 * @author Jürgen Moßgraber
 */
public class CLIBackend implements INotifier
{
    private final Map<String, IDetector<?>> detectorsByName = new HashMap<> ();
    private final Map<String, ICreator<?>>  creatorsByName  = new HashMap<> ();
    private boolean                         hasFinished     = false;
    private final ConverterBackend          backend;


    /**
     * Constructor.
     */
    public CLIBackend ()
    {
        this.backend = new ConverterBackend (this);

        for (final IDetector<?> detector: this.backend.getDetectors ())
            this.detectorsByName.put (detector.getPrefix ().toLowerCase (), detector);
        for (final ICreator<?> creator: this.backend.getCreators ())
            this.creatorsByName.put (creator.getPrefix ().toLowerCase (), creator);
    }


    /**
     * Parse the given command line arguments and executes a conversion.
     * 
     * @param arguments The arguments
     */
    public void parseCommandLine (final String [] arguments)
    {
        try
        {
            initStrings ();

            final CommandSpec spec = CommandSpec.create ().name ("ConvertWithMoss");
            spec.mixinStandardHelpOptions (true).version (Functions.getMessage ("TITLE"));

            spec.addOption (OptionSpec.builder ("-s", "--source").paramLabel ("SOURCE").type (String.class).description ("The source format.").required (true).build ());
            spec.addOption (OptionSpec.builder ("-d", "--destination").paramLabel ("DESTINATION").type (String.class).description ("The destination format.").required (true).build ());
            spec.addOption (OptionSpec.builder ("-t", "--type").paramLabel ("TYPE").type (String.class).description ("Set to either 'preset' (the default if absent) or 'performance' (without the quotes).").build ());
            spec.addOption (OptionSpec.builder ("-a", "--analyze").paramLabel ("ANALYZE").description ("If present, only analyzes the potential source files.").build ());
            spec.addOption (OptionSpec.builder ("-f", "--flat").paramLabel ("FLAT").description ("If present, the folder structure is not recreated in the output folder.").build ());
            spec.addOption (OptionSpec.builder ("-l", "--library").paramLabel ("LIBRARY").type (String.class).description ("Name for the library. Set to create a library.").build ());
            spec.addOption (OptionSpec.builder ("-p").paramLabel ("KEY=VALUE").description ("Key-value pairs in the form -pkey1=value1,key2=value2,...").required (false).arity ("0..*").type (Map.class).auxiliaryTypes (String.class, String.class).defaultValue (null).build ());
            spec.addOption (OptionSpec.builder ("-r", "--rename").paramLabel ("RENAME").type (File.class).description ("Configuration file for automatic file renaming.").build ());
            spec.addPositional (PositionalParamSpec.builder ().paramLabel ("SOURCE_FOLDER").type (File.class).description ("The source folder to process.").required (true).build ());
            spec.addPositional (PositionalParamSpec.builder ().paramLabel ("DESTINATION_FOLDER").type (File.class).description ("The destination folder to write to.").required (true).build ());

            final CommandLine commandLine = new CommandLine (spec);

            commandLine.setExecutionStrategy (this::run);
            System.exit (commandLine.execute (arguments));
        }
        catch (final Exception ex)
        {
            ex.printStackTrace ();
            System.exit (0);
        }
    }


    private int run (final ParseResult parseResult)
    {
        // Handle help or version information
        final Integer helpExitCode = CommandLine.executeHelpRequest (parseResult);
        if (helpExitCode != null)
            return helpExitCode.intValue ();

        // Basic setup
        final String sourceFormat = parseResult.matchedOptionValue ('s', "");
        final String destinationFormat = parseResult.matchedOptionValue ('d', "");

        final IDetector<?> detector = this.detectorsByName.get (sourceFormat.toLowerCase ());
        if (detector == null)
        {
            System.err.println (Functions.getMessage ("IDS_CLI_UNKNOWN_SOURCE_FORMAT", sourceFormat, this.detectorsByName.keySet ().toString ()));
            return 0;
        }
        final ICreator<?> creator = this.creatorsByName.get (destinationFormat.toLowerCase ());
        if (creator == null)
        {
            System.err.println (Functions.getMessage ("IDS_CLI_UNKNOWN_DESTINATION_FORMAT", destinationFormat, this.creatorsByName.keySet ().toString ()));
            return 0;
        }
        // Parameter options for the specific detector and creator
        final Map<String, String> parameters = parseResult.matchedOptionValue ('p', Collections.emptyMap ());
        if (!detector.getSettings ().checkSettingsCLI (this, parameters) || !creator.getSettings ().checkSettingsCLI (this, parameters))
            return 0;
        if (!parameters.isEmpty ())
        {
            final String [] detectorCliParameterNames = detector.getSettings ().getCLIParameterNames ();
            final String [] creatorCliParameterNames = creator.getSettings ().getCLIParameterNames ();
            System.err.println (Functions.getMessage ("IDS_CLI_UNKNOWN_PARAMETER", parameters.keySet ().iterator ().next (), Arrays.toString (detectorCliParameterNames), Arrays.toString (creatorCliParameterNames)));
            return 0;
        }

        // Renaming option & folder check
        final File renamingCSVFile = parseResult.matchedOptionValue ('r', null);
        final File sourceFolder = parseResult.matchedPositionalValue (0, null);
        final File destinationFolder = parseResult.matchedPositionalValue (1, null);
        CSVRenameFile renamer = null;
        try
        {
            verifyFolders (sourceFolder, destinationFolder);
            renamer = verifyRenameFile (renamingCSVFile);
        }
        catch (final IllegalArgumentException ex)
        {
            System.err.println (ex.getMessage ());
            return 0;
        }

        final String type = parseResult.matchedOptionValue ('t', null);
        final boolean detectPerformances = "performance".equalsIgnoreCase (type);
        final String libraryName = parseResult.matchedOptionValue ('l', null);
        final boolean wantsMultipleFiles = libraryName != null;
        final boolean createFolderStructure = parseResult.matchedOptionValue ('f', null) == null;
        final boolean onlyAnalyse = parseResult.matchedOptionValue ('a', null) != null;

        this.backend.detect (detector, creator, sourceFolder, destinationFolder, renamer, libraryName, detectPerformances, wantsMultipleFiles, createFolderStructure, onlyAnalyse);

        while (!this.hasFinished)
        {
            try
            {
                Thread.sleep (10);
            }
            catch (final InterruptedException ex)
            {
                Thread.currentThread ().interrupt ();
            }
        }

        return 0;
    }


    /** {@inheritDoc} */
    @Override
    public void log (final String messageID, final String... replaceStrings)
    {
        System.out.print (Functions.getMessage (messageID, replaceStrings));
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final String messageID, final String... replaceStrings)
    {
        System.err.print (Functions.getMessage (messageID, replaceStrings));
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final String messageID, final Throwable throwable)
    {
        System.err.print (Functions.getMessage (messageID, throwable));
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final Throwable throwable)
    {
        System.err.print (throwable.getMessage ());
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
        System.err.println (message);
    }


    /** {@inheritDoc} */
    @Override
    public void logText (final String text)
    {
        System.out.print (text);
    }


    /** {@inheritDoc} */
    @Override
    public void updateButtonStates (final boolean canClose)
    {
        // Not used
    }


    /** {@inheritDoc} */
    @Override
    public void finished (final boolean cancelled)
    {
        // Creates libraries if requested
        this.backend.finish (cancelled);

        this.hasFinished = true;
    }


    /**
     * Initialize the string resources.
     *
     * @throws EndApplicationException Could not read the string resources
     */
    private static void initStrings () throws EndApplicationException
    {
        try
        {
            Functions.init (ResourceBundle.getBundle ("Strings", Locale.getDefault ()), null, 400, 500);
        }
        catch (final MissingResourceException mre)
        {
            throw new EndApplicationException ("Strings.properties not found", mre);
        }
    }


    /**
     * Set and check folder for existence.
     * 
     * @param sourceFolder The source folder to check
     * @param destinationFolder The destination folder to check
     * @throws IllegalArgumentException Source of destination folder has a problem
     */
    private static void verifyFolders (final File sourceFolder, final File destinationFolder) throws IllegalArgumentException
    {
        // Check source folder
        if (!sourceFolder.exists () || !sourceFolder.isDirectory ())
            throw new IllegalArgumentException (Functions.getMessage ("IDS_NOTIFY_FOLDER_DOES_NOT_EXIST", sourceFolder.getAbsolutePath ()));

        // Check output folder
        if (!destinationFolder.exists () && !destinationFolder.mkdirs ())
            throw new IllegalArgumentException (Functions.getMessage ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", destinationFolder.getAbsolutePath ()));
        if (!destinationFolder.isDirectory ())
            throw new IllegalArgumentException (Functions.getMessage ("IDS_NOTIFY_FOLDER_DESTINATION_NOT_A_FOLDER", destinationFolder.getAbsolutePath ()));
    }


    /**
     * Set and check folder for existence.
     * 
     * @param renamingCSVFile The renaming file
     * @return The parsed rename file
     */
    private static CSVRenameFile verifyRenameFile (final File renamingCSVFile)
    {
        if (renamingCSVFile == null)
            return null;

        final CSVRenameFile csvRenameFile = new CSVRenameFile ();

        if (!renamingCSVFile.exists ())
            throw new IllegalArgumentException (Functions.getMessage ("IDS_NOTIFY_RENAMING_CSV_DOES_NOT_EXIST", renamingCSVFile.getAbsolutePath ()));

        if (!renamingCSVFile.canRead ())
            throw new IllegalArgumentException (Functions.getMessage ("IDS_NOTIFY_RENAMING_CSV_NOT_READABLE", renamingCSVFile.getAbsolutePath ()));

        csvRenameFile.setRenameFile (renamingCSVFile);
        return csvRenameFile;
    }
}
