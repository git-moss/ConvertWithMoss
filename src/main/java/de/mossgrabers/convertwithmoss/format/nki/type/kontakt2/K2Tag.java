// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt2;

import de.mossgrabers.convertwithmoss.format.nki.AbstractTagsAndAttributes;


/**
 * Tags used in the NKI Kontakt 2 XML structure.
 *
 * @author Jürgen Moßgraber
 * @author Philip Stolz
 */
public class K2Tag extends AbstractTagsAndAttributes
{
    /** The bank element. */
    public static final String K2_BANK_ELEMENT             = "K2_Bank";

    /** The root element. */
    public static final String K2_ROOT_CONTAINER           = "K2_Container";

    /** The programs element. */
    public static final String K2_PROGRAMS                 = "Programs";

    /** The program element. */
    public static final String K2_PROGRAM                  = "K2_Program";

    /** A K2 Group element of a program. */
    public static final String K2_GROUP                    = "K2_Group";

    /** A K2 Zone element of a program. */
    public static final String K2_ZONE                     = "K2_Zone";

    /** The release trigger parameter */
    public static final String K2_RELEASE_TRIGGER_PARAM    = "releaseTrigger";

    /** The sample file attribute. */
    public static final String K2_SAMPLE_FILE_ATTRIBUTE    = "file_ex2";

    /** The extended sample file attribute. */
    public static final String K2_SAMPLE_FILE_EX_ATTRIBUTE = "file_ex2";

    /** The program volume parameter. */
    public static final String K2_PROG_VOL_PARAM           = "volume";

    /** The program pan parameter. */
    public static final String K2_PROG_PAN_PARAM           = "pan";

    /** The program tune parameter. */
    public static final String K2_PROG_TUNE_PARAM          = "tune";

    /** The internal modulator element. */
    public static final String K2_INT_MODULATOR_ELEMENT    = "K2_IntMod";

    /** The external modulator element. */
    public static final String K2_EXT_MODULATOR_ELEMENT    = "K2_ExtMod";

    /** The targets element. */
    public static final String K2_TARGETS_ELEMENT          = "Targets";

    /** The target element. */
    public static final String K2_TARGET_ELEMENT           = "Target";


    /** {@inheritDoc} */
    @Override
    public String program ()
    {
        return K2_PROGRAM;
    }


    /** {@inheritDoc} */
    @Override
    public String group ()
    {
        return K2_GROUP;
    }


    /** {@inheritDoc} */
    @Override
    public String zone ()
    {
        return K2_ZONE;
    }


    /** {@inheritDoc} */
    @Override
    public String intModulatorElement ()
    {
        return K2_INT_MODULATOR_ELEMENT;
    }


    /** {@inheritDoc} */
    @Override
    public String extModulatorElement ()
    {
        return K2_EXT_MODULATOR_ELEMENT;
    }


    /** {@inheritDoc} */
    @Override
    public String sampleFileAttribute ()
    {
        return K2_SAMPLE_FILE_ATTRIBUTE;
    }


    /** {@inheritDoc} */
    @Override
    public String sampleFileExAttribute ()
    {
        return K2_SAMPLE_FILE_EX_ATTRIBUTE;
    }


    /** {@inheritDoc} */
    @Override
    public String progVolParam ()
    {
        return K2_PROG_VOL_PARAM;
    }


    /** {@inheritDoc} */
    @Override
    public String progTuneParam ()
    {
        return K2_PROG_TUNE_PARAM;
    }


    /** {@inheritDoc} */
    @Override
    public String progPanParam ()
    {
        return K2_PROG_PAN_PARAM;
    }


    /** {@inheritDoc} */
    @Override
    public String rootContainer ()
    {
        return K2_ROOT_CONTAINER;
    }


    /** {@inheritDoc} */
    @Override
    public double calculateTune (final double zoneTune, final double groupTune, final double progTune)
    {
        // Only the zone tune is stored logarithmically. Group and program tune are simply in the
        // range of [-3..3], 1 equals a full octave.
        final double value = 12.0 * (Math.log (zoneTune) / Math.log (2) + groupTune + progTune);
        return Math.round (value * 100000) / 100000.0;
    }
}
