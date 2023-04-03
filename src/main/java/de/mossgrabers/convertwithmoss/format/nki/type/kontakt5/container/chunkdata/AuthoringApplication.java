// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.container.chunkdata;

import java.util.HashMap;
import java.util.Map;


/**
 * The (known) IDs and names of authoring applications.
 *
 * @author Jürgen Moßgraber
 */
public enum AuthoringApplication
{
    GUITARRIG(1, "GuitarRig"),
    KONTAKT(2, "Kontakt"),
    KORE(3, "Kore"),
    REAKTOR(4, "Reaktor"),
    MASCHINE(5, "Maschine"),
    ABSYNTHE(6, "Absynthe"),
    MASSIVE(7, "Massive"),
    FM8(8, "FM8"),
    BATTERY(9, "Battery"),
    KKONTROL(10, "KKontrol"),
    SC(11, "SC"),
    FXF_01(12, "FXF_01"),
    FXF_02(13, "FXF_02"),
    FXF_03(14, "FXF_03"),
    FXF_04(15, "FXF_04"),
    FXF_05(16, "FXF_05"),
    FXF_06(17, "FXF_06"),
    FXF_07(18, "FXF_07"),
    FXF_08(19, "FXF_08"),
    FXF_09(20, "FXF_09"),
    FXF_10(21, "FXF_10"),
    FXF_11(22, "FXF_11"),
    FXF_12(23, "FXF_12"),
    FXF_13(24, "FXF_13"),
    FXF_14(25, "FXF_14"),
    FXF_15(26, "FXF_15"),
    FXF_16(27, "FXF_16"),
    FXF_17(28, "FXF_17"),
    FXF_18(29, "FXF_18"),
    FXF_19(30, "FXF_19"),
    TRAKTOR(31, "Traktor");


    private static final Map<Integer, AuthoringApplication> LOOKUP = new HashMap<> ();
    static
    {
        for (final AuthoringApplication type: AuthoringApplication.values ())
            LOOKUP.put (Integer.valueOf (type.id), type);
    }


    /**
     * Get an authoring application for the given ID.
     *
     * @param id An ID
     * @return The authoring application or null if none exists with that ID
     */
    public static AuthoringApplication get (final int id)
    {
        return LOOKUP.get (Integer.valueOf (id));
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
