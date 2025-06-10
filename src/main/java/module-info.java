/**
 * The ConvertWithMoss module.
 *
 * @author Jürgen Moßgraber
 */
module de.mossgrabers.convertwithmoss
{
    requires transitive java.desktop;
    requires java.logging;
    requires transitive java.prefs;
    requires transitive javafx.controls;
    requires transitive java.xml;
    requires transitive de.mossgrabers.uitools;
    requires javafx.graphics;
    requires com.github.trilarion.sound;


    exports de.mossgrabers.convertwithmoss.ui;
    exports de.mossgrabers.convertwithmoss.core;
    exports de.mossgrabers.convertwithmoss.core.model;
    exports de.mossgrabers.convertwithmoss.core.model.enumeration;
    exports de.mossgrabers.convertwithmoss.core.creator;
    exports de.mossgrabers.convertwithmoss.format.bitwig;
    exports de.mossgrabers.convertwithmoss.format.sfz;
    exports de.mossgrabers.convertwithmoss.format.wav;
    exports de.mossgrabers.convertwithmoss.exception;
    exports de.mossgrabers.convertwithmoss.file.riff;
    exports de.mossgrabers.convertwithmoss.file.wav;
    exports de.mossgrabers.convertwithmoss.file;


    opens de.mossgrabers.convertwithmoss.css;
    opens de.mossgrabers.convertwithmoss.images;
    opens de.mossgrabers.convertwithmoss.templates.nki;
    opens de.mossgrabers.convertwithmoss.templates.adv;
    opens de.mossgrabers.convertwithmoss.templates.dspreset;
    opens de.mossgrabers.convertwithmoss.templates.ysfc;
}
