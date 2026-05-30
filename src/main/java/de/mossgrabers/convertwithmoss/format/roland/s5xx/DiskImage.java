// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.util.Collections;
import java.util.List;


/**
 * Top-level parsed result of a Roland S-50 / S-550 disk image.
 *
 * @author Jürgen Moßgraber
 */
public class DiskImage
{
    private final RolandDiskImageHeader header;
    private final List<Patch>     patches;
    private final List<Tone>      tones;
    private final DiskLabel       diskLabel;
    private final List<WaveData>  waveData;


    /**
     * Constructor.
     * 
     * @param header The image header
     * @param patches The patch blocks
     * @param tones The tones
     * @param diskLabel The label of the disk
     * @param waveData The wave data on the disk
     */
    public DiskImage (final RolandDiskImageHeader header, final List<Patch> patches, final List<Tone> tones, final DiskLabel diskLabel, final List<WaveData> waveData)
    {
        this.header = header;
        this.patches = Collections.unmodifiableList (patches);
        this.tones = Collections.unmodifiableList (tones);
        this.diskLabel = diskLabel;
        this.waveData = waveData;
    }


    /**
     * Get the disk header.
     * 
     * @return The header
     */
    public RolandDiskImageHeader getHeader ()
    {
        return this.header;
    }


    /**
     * Get the patches.
     * 
     * @return The patches
     */
    public List<Patch> getPatches ()
    {
        return this.patches;
    }


    /**
     * Get the tones.
     * 
     * @return The tones
     */
    public List<Tone> getTones ()
    {
        return this.tones;
    }


    /**
     * Get the disk label.
     * 
     * @return The label, null for LAND type
     */
    public DiskLabel getDiskLabel ()
    {
        return this.diskLabel;
    }


    /**
     * {@code true} when this is a LAND-type hard-drive / CD-ROM container.
     * 
     * @return True if it is a HD / CD
     */
    public boolean isLandType ()
    {
        return this.header.getSamplerType () == SamplerType.LAND;
    }


    /**
     * Get the wave data.
     * 
     * @return The wave data
     */
    public List<WaveData> getWaveData ()
    {
        return this.waveData;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("=== Roland Disk Image ===\n").append (this.header).append ('\n');

        if (this.diskLabel != null)
            sb.append ("\n--- Disk Label ---\n").append (this.diskLabel.getFullText ()).append ('\n');
        if (!this.patches.isEmpty ())
        {
            sb.append ("\n--- Patches (").append (this.patches.size ()).append (") ---\n");
            this.patches.forEach (p -> sb.append ("  ").append (p).append ('\n'));
        }
        if (!this.tones.isEmpty ())
        {
            sb.append ("\n--- Tones (").append (this.tones.size ()).append (") ---\n");
            this.tones.forEach (t -> sb.append ("  ").append (t).append ('\n'));
        }
        return sb.toString ();
    }
}