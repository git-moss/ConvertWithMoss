// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools;

import javafx.application.Application;
import javafx.stage.Stage;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;


/**
 * An application extension providing several features like menus, toolbar, statusbar, etc. The
 * first parameter for the Application must be the class name of a sub class of TopLevelFrame.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class DefaultApplication extends Application
{
    private Class<?> frameClass;


    /** {@inheritDoc} */
    @Override
    public void start (final Stage stage) throws Exception
    {
        final List<String> parameters = this.getParameters ().getRaw ();
        this.frameClass = Class.forName (parameters.get (0));
        final Constructor<?> constructor = this.frameClass.getConstructor ();
        ((AbstractFrame) constructor.newInstance ()).initialise (stage, this.getConfiguredTitle ());
    }


    /**
     * Gets the title and the version number from the manifest. If it is not found it is read from
     * the TITLE-property from String.properties.
     *
     * @return The configured title
     */
    public Optional<String> getConfiguredTitle ()
    {
        if (this.frameClass == null)
            return Optional.empty ();
        final Package p = this.frameClass.getPackage ();
        if (p != null)
        {
            final String implTitle = p.getImplementationTitle ();
            final String version = p.getImplementationVersion ();
            if (implTitle != null && version != null)
                return Optional.of (new StringBuilder (implTitle).append (' ').append (version).toString ());
        }
        return Optional.empty ();
    }
}
