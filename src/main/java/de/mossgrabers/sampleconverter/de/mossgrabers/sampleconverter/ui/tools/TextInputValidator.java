// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools;

import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TextInputControl;

import java.util.regex.Pattern;


/**
 * Can be applied to a text field to limit the text input to the given regular expression.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class TextInputValidator implements ChangeListener<String>
{

    private final Pattern pattern;


    /**
     * Constructor.
     *
     * @param regex A regular expression which should be checked
     */
    public TextInputValidator (final String regex)
    {
        this.pattern = Pattern.compile (regex);
    }


    /** {@inheritDoc} */
    @Override
    public void changed (final ObservableValue<? extends String> observable, final String oldValue, final String newValue)
    {
        if (!"".equals (newValue) && !this.pattern.matcher (newValue).matches ())
            ((StringProperty) observable).setValue (oldValue);
    }


    /**
     * Limits the text input of the given control to matches of the given regular expression.
     *
     * @param control The control to limit
     * @param regex A regular expression
     */
    public static void limit (final TextInputControl control, final String regex)
    {
        control.textProperty ().addListener (new TextInputValidator (regex));
    }


    /**
     * Limits the text input of the given control to numbers.
     *
     * @param control The control to limit
     */
    public static void limitToNumbers (final TextInputControl control)
    {
        control.textProperty ().addListener (new TextInputValidator ("\\d*"));
    }
}
