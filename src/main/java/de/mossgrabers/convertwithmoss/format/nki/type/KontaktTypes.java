// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt1.Kontakt1Type;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt2.Kontakt2Type;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;

import java.util.HashMap;
import java.util.Map;


/**
 * Manages readers/writers for the different Kontakt versions.
 *
 * @author Jürgen Moßgraber
 */
public class KontaktTypes
{
    /** ID for Kontakt 1 NKI files. */
    public static final Integer              ID_KONTAKT1               = Integer.valueOf (0x5EE56EB3);
    /** ID for Kontakt 2 NKI files (little-endian). */
    public static final Integer              ID_KONTAKT2_LITTLE_ENDIAN = Integer.valueOf (0x1290A87F);
    /** ID for Kontakt 2 NKI files (big-endian). */
    public static final Integer              ID_KONTAKT2_BIG_ENDIAN    = Integer.valueOf (0x7FA89012);

    /** ID for a Kontakt 5 NKI monolith file. */
    public static final Integer              ID_KONTAKT5_MONOLITH      = Integer.valueOf (0x2F5C204E);

    private final Map<Integer, IKontaktType> typeHandlers              = new HashMap<> (3);


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param metadata Additional metadata configuration parameters
     */
    public KontaktTypes (final INotifier notifier, final IMetadataConfig metadata)
    {
        this.typeHandlers.put (ID_KONTAKT1, new Kontakt1Type (metadata, notifier));
        this.typeHandlers.put (ID_KONTAKT2_LITTLE_ENDIAN, new Kontakt2Type (metadata, notifier, false));
        this.typeHandlers.put (ID_KONTAKT2_BIG_ENDIAN, new Kontakt2Type (metadata, notifier, true));
    }


    /**
     * Get a handler instance for the given file ID.
     *
     * @param typeID The file ID
     * @return The handler or null if none is registered for the given ID
     */
    public IKontaktType getType (final int typeID)
    {
        return this.getType (Integer.valueOf (typeID));
    }


    /**
     * Get a handler instance for the given file ID.
     *
     * @param typeID The file ID
     * @return The handler or null if none is registered for the given ID
     */
    public IKontaktType getType (final Integer typeID)
    {
        return this.typeHandlers.get (typeID);
    }
}
