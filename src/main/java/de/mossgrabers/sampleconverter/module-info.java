/**
 * The SampleConverter module.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
module de.mossgrabers.sampleconverter
{
    requires java.desktop;
    requires java.logging;
    requires transitive java.prefs;
    requires transitive javafx.controls;
    requires transitive java.xml;


    exports de.mossgrabers.sampleconverter.ui;
    exports de.mossgrabers.sampleconverter.ui.tools;
    exports de.mossgrabers.sampleconverter.core;
    exports de.mossgrabers.sampleconverter.util;
    exports de.mossgrabers.sampleconverter.detector;
    exports de.mossgrabers.sampleconverter.creator;
    exports de.mossgrabers.sampleconverter.exception;
    exports de.mossgrabers.sampleconverter.file.riff;
    exports de.mossgrabers.sampleconverter.file.wav;


    opens de.mossgrabers.sampleconverter.css;
    opens de.mossgrabers.sampleconverter.images;
}