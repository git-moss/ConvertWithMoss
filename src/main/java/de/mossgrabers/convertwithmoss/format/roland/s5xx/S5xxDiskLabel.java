// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s5xx;

/**
 * The 5 × 12 character disk label stored near absolute offset 68 659.
 *
 * <p>
 * <b>Storage layout</b>
 * <ul>
 * <li>Row 1 — contiguous 12 bytes at 68 659–68 670.</li>
 * <li>Rows 2–5 — interleaved in 12 groups of 4 bytes starting at 68 671: {@code [+0]} = row-2 char
 * N, {@code [+1]} = row-3 char N, {@code [+2]} = row-4 char N, {@code [+3]} = row-5 char N.</li>
 * </ul>
 * Total label area: 60 bytes (68 659–68 718).
 *
 * @author Jürgen Moßgraber
 */
public class S5xxDiskLabel
{
    /** The number of label rows. */
    public static final int ROWS          = 5;

    /** Characters per row. */
    public static final int CHARS_PER_ROW = 12;

    private final String [] rows;


    /**
     * Constructor.
     * 
     * @param rows The label rows
     */
    public S5xxDiskLabel (final String [] rows)
    {
        if (rows.length != ROWS)
            throw new IllegalArgumentException ("DiskLabel requires exactly " + ROWS + " rows");
        this.rows = rows.clone ();
    }


    /**
     * Returns the label row at the given 0-based index.
     * 
     * @param rowIndex The index of the row (0 = row 1, 4 = row 5)
     * @return The text of the row
     */
    public String getRow (final int rowIndex)
    {
        if (rowIndex < 0 || rowIndex >= ROWS)
            throw new IndexOutOfBoundsException ("Row index must be 0–" + (ROWS - 1));
        return this.rows[rowIndex];
    }


    /**
     * Returns a defensive copy of all five rows.
     * 
     * @return The rows
     */
    public String [] getRows ()
    {
        return this.rows.clone ();
    }


    /**
     * All rows joined with {@code '\n'}.
     * 
     * @return The flattened text
     */
    public String getFullText ()
    {
        return String.join ("\n", this.rows);
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "DiskLabel{\n" + this.getFullText () + "\n}";
    }
}