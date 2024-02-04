// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import java.util.HashMap;
import java.util.Map;


/**
 * The (known) IDs and names of authoring applications.
 *
 * @author Jürgen Moßgraber
 */
public enum AuthoringApplication
{
    /** Guitar Rig. */
    GUITARRIG(1, "GuitarRig"),
    /** Kontakt. */
    KONTAKT(2, "Kontakt"),
    /** KORE. */
    KORE(3, "KORE"),
    /** Reaktor. */
    REAKTOR(4, "Reaktor"),
    /** Maschine. */
    MASCHINE(5, "Maschine"),
    /** Absynth. */
    ABSYNTH(6, "Absynth"),
    /** Massive. */
    MASSIVE(7, "Massive"),
    /** FM 8. */
    FM8(8, "FM8"),
    /** Battery. */
    BATTERY(9, "Battery"),
    /** Komplete Kontrol. */
    KKONTROL(10, "Komplete Kontrol"),
    /** Unknown. */
    SC(11, "SC"),
    /** Unknown. */
    FXF_01(12, "FXF_01"),
    /** Unknown. */
    FXF_02(13, "FXF_02"),
    /** Unknown. */
    FXF_03(14, "FXF_03"),
    /** Unknown. */
    FXF_04(15, "FXF_04"),
    /** Unknown. */
    FXF_05(16, "FXF_05"),
    /** Unknown. */
    FXF_06(17, "FXF_06"),
    /** Unknown. */
    FXF_07(18, "FXF_07"),
    /** Unknown. */
    FXF_08(19, "FXF_08"),
    /** Unknown. */
    FXF_09(20, "FXF_09"),
    /** Unknown. */
    FXF_10(21, "FXF_10"),
    /** Unknown. */
    FXF_11(22, "FXF_11"),
    /** Unknown. */
    /** Unknown. */
    FXF_12(23, "FXF_12"),
    /** Unknown. */
    FXF_13(24, "FXF_13"),
    /** Unknown. */
    FXF_14(25, "FXF_14"),
    /** Unknown. */
    FXF_15(26, "FXF_15"),
    /** Unknown. */
    FXF_16(27, "FXF_16"),
    /** Unknown. */
    FXF_17(28, "FXF_17"),
    /** Unknown. */
    FXF_18(29, "FXF_18"),
    /** Unknown. */
    FXF_19(30, "FXF_19"),
    /** Traktor. */
    TRAKTOR(31, "Traktor");


    private static final Map<Long, AuthoringApplication> LOOKUP = new HashMap<> ();
    static
    {
        for (final AuthoringApplication type: AuthoringApplication.values ())
            LOOKUP.put (Long.valueOf (type.id), type);
    }


    /**
     * Get an authoring application for the given ID.
     *
     * @param id An ID
     * @return The authoring application or null if none exists with that ID
     */
    public static AuthoringApplication get (final long id)
    {
        return LOOKUP.get (Long.valueOf (id));
    }


    private final int    id;
    private final String name;


    /**
     * Constructor.
     *
     * @param id The ID of the authoring application
     * @param name The name of the authoring application
     */
    private AuthoringApplication (final int id, final String name)
    {
        this.id = id;
        this.name = name;
    }


    /**
     * Get the name of the authoring application.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }
}
