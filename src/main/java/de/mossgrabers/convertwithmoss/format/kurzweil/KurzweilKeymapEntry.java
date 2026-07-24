// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil;

/**
 * One entry of a Kurzweil keymap. Assigns a sample recording (sample object + 1-based sub-sample
 * number) and a tuning offset to one key position. Which of the fields are actually stored in the
 * file is defined by the method bits of the keymap.
 *
 * @author Jürgen Moßgraber
 */
public class KurzweilKeymapEntry
{
    private int tuning;
    private int volumeAdjust;
    private int sampleID;
    private int subSampleNumber;


    /**
     * Is the entry in use? Unused entries have a sub-sample number of 0.
     *
     * @return True if the entry references a sample
     */
    public boolean isUsed ()
    {
        return this.subSampleNumber > 0;
    }


    /**
     * Check if another entry references the same sample recording with identical settings.
     *
     * @param entry The other entry
     * @return True if identical
     */
    public boolean isIdentical (final KurzweilKeymapEntry entry)
    {
        return this.sampleID == entry.sampleID && this.subSampleNumber == entry.subSampleNumber && this.tuning == entry.tuning && this.volumeAdjust == entry.volumeAdjust;
    }


    /**
     * Get the tuning offset which is added to the chromatic key tracking.
     *
     * @return The tuning in cents
     */
    public int getTuning ()
    {
        return this.tuning;
    }


    /**
     * Set the tuning offset which is added to the chromatic key tracking.
     *
     * @param tuning The tuning in cents
     */
    public void setTuning (final int tuning)
    {
        this.tuning = tuning;
    }


    /**
     * Get the volume adjustment (unit not confirmed, therefore not interpreted).
     *
     * @return The volume adjustment
     */
    public int getVolumeAdjust ()
    {
        return this.volumeAdjust;
    }


    /**
     * Set the volume adjustment.
     *
     * @param volumeAdjust The volume adjustment
     */
    public void setVolumeAdjust (final int volumeAdjust)
    {
        this.volumeAdjust = volumeAdjust;
    }


    /**
     * Get the ID of the referenced sample object.
     *
     * @return The sample object ID
     */
    public int getSampleID ()
    {
        return this.sampleID;
    }


    /**
     * Set the ID of the referenced sample object.
     *
     * @param sampleID The sample object ID
     */
    public void setSampleID (final int sampleID)
    {
        this.sampleID = sampleID;
    }


    /**
     * Get the 1-based index of the referenced header in the sample object. 0 marks an unused entry.
     * For stereo samples this references the left channel header (1, 3, 5, ...).
     *
     * @return The sub-sample number
     */
    public int getSubSampleNumber ()
    {
        return this.subSampleNumber;
    }


    /**
     * Set the 1-based index of the referenced header in the sample object.
     *
     * @param subSampleNumber The sub-sample number
     */
    public void setSubSampleNumber (final int subSampleNumber)
    {
        this.subSampleNumber = subSampleNumber;
    }
}
