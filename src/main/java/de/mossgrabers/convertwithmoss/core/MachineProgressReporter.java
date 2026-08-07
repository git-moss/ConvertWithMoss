// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.io.File;


/**
 * Reports the progress of a conversion in a machine-readable form on the standard error stream, so
 * that an application which runs ConvertWithMoss as a child process can drive a progress bar with
 * it. The human readable output on the standard output stream is not changed by this - a conversion
 * started from a console still writes only its progress dots there.
 * <p>
 * The protocol is off by default and must be requested, either with the '-P' option of the command
 * line interface or by setting the environment variable CWM_MACHINE_PROGRESS to '1' or 'true' (for
 * hosts which cannot add options to the command line). As long as it is off, all of the calls below
 * do nothing but test one flag.
 * <p>
 * Each event is one line which is terminated with a line separator and flushed immediately:
 *
 * <pre>
 * CWM_PROGRESS pct=&lt;0..100&gt; phase=&lt;token&gt; detail=&lt;text&gt;
 * </pre>
 *
 * The phase is one of 'start' (the run begins), 'convert' (a source file was started or finished),
 * 'sample' (a sample file of the current source file was loaded) and 'done' (the run ended; if it
 * was cancelled the percentage is the one which was reached and not 100). The detail is the rest of
 * the line and may contain spaces since it is a file name or a path. It is reduced to printable
 * ASCII characters, so that the two processes do not need to agree on a character encoding. Any
 * line which is not understood should be ignored.
 * <p>
 * All progress calls happen on the thread which executes the detection; only the activation happens
 * before that thread is started.
 *
 * @author Jürgen Moßgraber
 */
public final class MachineProgressReporter
{
    /** The prefix which starts every line of the protocol. */
    private static final String     PREFIX                = "CWM_PROGRESS";
    /** The environment variable which activates the protocol instead of the command line option. */
    private static final String     ENVIRONMENT_VARIABLE  = "CWM_MACHINE_PROGRESS";

    private static final String     PHASE_START           = "start";
    private static final String     PHASE_CONVERT         = "convert";
    private static final String     PHASE_SAMPLE          = "sample";
    private static final String     PHASE_DONE            = "done";

    /**
     * The number of loaded samples after which about 63% of the percentage range of the current
     * source file is covered. How many samples a source file contains is not known before it was
     * read, therefore the progress inside of a file can only approach the end of its range instead
     * of walking towards it in known steps.
     */
    private static final double     SAMPLE_CURVE_SCALE    = 25.0;
    /** The maximum length of the detail text. A longer text keeps its end, e.g. the file name. */
    private static final int        MAX_DETAIL_LENGTH     = 180;

    private static volatile boolean isActive              = isActivatedByEnvironment ();

    private static boolean          isRunning             = false;
    private static int              numberOfFiles         = 0;
    private static int              numberOfFinishedFiles = 0;
    private static int              numberOfSamplesOfFile = 0;


    /**
     * Constructor. Private due to utility class.
     */
    private MachineProgressReporter ()
    {
        // Intentionally empty
    }


    /**
     * Activate the protocol. Called for the option of the command line interface; the environment
     * variable is checked on its own.
     */
    public static void activate ()
    {
        isActive = true;
    }


    /**
     * Is the protocol active?
     *
     * @return True if progress lines are written
     */
    public static boolean isActive ()
    {
        return isActive;
    }


    /**
     * Report the start of a detection run.
     *
     * @param sourceFolder The folder which is processed
     * @param numberOfSourceFiles The number of source files which will be processed. Set to 0 if
     *            that number is unknown, the progress then only moves with the loaded samples.
     */
    public static void start (final File sourceFolder, final int numberOfSourceFiles)
    {
        if (!isActive)
            return;

        numberOfFiles = Math.max (0, numberOfSourceFiles);
        numberOfFinishedFiles = 0;
        numberOfSamplesOfFile = 0;
        isRunning = true;

        report (0, PHASE_START, sourceFolder == null ? "" : sourceFolder.getAbsolutePath ());
    }


    /**
     * Report that the given source file is now read.
     *
     * @param sourceFile The source file
     */
    public static void startFile (final File sourceFile)
    {
        if (!isRunning)
            return;

        numberOfSamplesOfFile = 0;
        report (calcPercent (), PHASE_CONVERT, sourceFile.getName ());
    }


    /**
     * Report that the given source file was fully processed.
     *
     * @param sourceFile The source file
     */
    public static void finishFile (final File sourceFile)
    {
        if (!isRunning)
            return;

        numberOfFinishedFiles++;
        numberOfSamplesOfFile = 0;
        report (calcPercent (), PHASE_CONVERT, sourceFile.getName ());
    }


    /**
     * Report that a sample file of the current source file was loaded.
     *
     * @param sampleFile The sample file
     */
    public static void reportSample (final File sampleFile)
    {
        if (!isRunning)
            return;

        numberOfSamplesOfFile++;
        report (calcPercent (), PHASE_SAMPLE, sampleFile.getName ());
    }


    /**
     * Report the end of the detection run.
     *
     * @param cancelled True if the run was cancelled; the reported percentage is then the one which
     *            was reached instead of 100
     */
    public static void finish (final boolean cancelled)
    {
        if (!isRunning)
            return;

        isRunning = false;
        report (cancelled ? calcPercent () : 100, PHASE_DONE, "");
    }


    /**
     * Calculate the percentage which is currently reached. The full range is split into one slice
     * per source file. Inside of a slice the progress approaches the end of the slice with the
     * number of loaded samples but never reaches it, since the number of samples of a source file
     * is only known once it was read - the end of a slice is therefore only reached by finishing
     * the file. If more files are finished than were counted, the total is raised accordingly, so
     * that the percentage never runs backwards or above 100.
     *
     * @return The percentage, 0-100
     */
    private static int calcPercent ()
    {
        final int total = Math.max (1, Math.max (numberOfFiles, numberOfFinishedFiles));
        final int lowerBound = (int) (numberOfFinishedFiles * 100L / total);
        final int upperBound = (int) Math.min (100L, (numberOfFinishedFiles + 1L) * 100L / total);
        // The upper bound belongs to the next file, therefore stay 1 percent below it
        final int span = Math.max (0, upperBound - 1 - lowerBound);
        final double factor = 1.0 - Math.exp (-numberOfSamplesOfFile / SAMPLE_CURVE_SCALE);
        return Math.min (100, lowerBound + (int) Math.round (span * factor));
    }


    /**
     * Write one line of the protocol.
     *
     * @param percent The percentage, clipped to 0-100
     * @param phase The phase
     * @param detail The detail text
     */
    private static void report (final int percent, final String phase, final String detail)
    {
        final StringBuilder sb = new StringBuilder (PREFIX);
        sb.append (" pct=").append (Math.max (0, Math.min (100, percent)));
        sb.append (" phase=").append (phase);
        sb.append (" detail=").append (sanitize (detail));
        System.err.println (sb.toString ());
        System.err.flush ();
    }


    /**
     * Reduce the given text to one line of printable ASCII characters, so that a line of the
     * protocol can always be parsed and no character encoding has to be agreed upon between the
     * processes. All other characters are replaced by a question mark. A text which is too long
     * keeps its end, which is the interesting part of a path.
     *
     * @param detail The text to sanitize
     * @return The sanitized text
     */
    private static String sanitize (final String detail)
    {
        if (detail == null)
            return "";

        final StringBuilder sb = new StringBuilder (detail.length ());
        for (int i = 0; i < detail.length (); i++)
        {
            final char c = detail.charAt (i);
            sb.append (c >= 0x20 && c < 0x7F ? c : '?');
        }

        final String text = sb.toString ().trim ();
        return text.length () > MAX_DETAIL_LENGTH ? text.substring (text.length () - MAX_DETAIL_LENGTH) : text;
    }


    /**
     * Test if the protocol was activated by the environment variable.
     *
     * @return True if the variable is set to '1' or 'true'
     */
    private static boolean isActivatedByEnvironment ()
    {
        final String value = System.getenv (ENVIRONMENT_VARIABLE);
        return "1".equals (value) || "true".equalsIgnoreCase (value);
    }
}
