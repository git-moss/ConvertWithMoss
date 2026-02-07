// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

/**
 * Enumeration for the RIFF IDs of several info chunks which are used by different formats.
 *
 * @author Jürgen Moßgraber
 */
@SuppressWarnings("javadoc")
public enum InfoRiffChunkId implements RiffChunkId
{
    /** ID for INFO chunk. */
    INFO_ID("Info", "INFO"),

    /** Info chunks. */
    INFO_IARL("Archival Location", "IARL"),
    INFO_IART("Artist", "IART"),
    INFO_IBSU("BaseURL", "IBSU"),
    INFO_ICNM("Cinematographer", "ICNM"),
    INFO_CMNT("Comment", "CMNT"),
    INFO_ICMT("Comment2", "ICMT"),
    INFO_COMM("Comments", "COMM"),
    INFO_ICMS("Commissioned", "ICMS"),
    INFO_ICOP("Copyright", "ICOP"),
    INFO_ICDS("Costume Designer", "ICDS"),
    INFO_ICNT("Country", "ICNT"),
    INFO_ICRP("Cropped", "ICRP"),
    INFO_ICRD("Date Created", "ICRD"),
    INFO_IDIT("Date TimeOriginal", "IDIT"),
    INFO_ICAS("Default AudioStream", "ICAS"),
    INFO_IDIM("Dimension", "IDIM"),
    INFO_DIRC("Directory", "DIRC"),
    INFO_IDST("Distributed By", "IDST"),
    INFO_IDPI("Dots Per Inch", "IDPI"),
    INFO_IEDT("Edited By", "IEDT"),
    INFO_IAS8("Eighth Language", "IAS8"),
    INFO_CODE("Encoded By", "CODE"),
    INFO_TCDO("End Timecode", "TCDO"),
    INFO_IENG("Engineer", "IENG"),
    INFO_IAS5("Fifth Language", "IAS5"),
    INFO_IAS1("First Language", "IAS1"),
    INFO_IAS4("Fourth Language", "IAS4"),
    INFO_GENR("Genre", "GENR"),
    INFO_IKEY("Keywords", "IKEY"),
    INFO_LANG("Language", "LANG"),
    INFO_TLEN("Length", "TLEN"),
    INFO_ILGT("Lightness", "ILGT"),
    INFO_LOCA("Location", "LOCA"),
    INFO_ILIU("Logo Icon URL", "ILIU"),
    INFO_ILGU("Logo URL", "ILGU"),
    INFO_IMED("Medium", "IMED"),
    INFO_IMBI("More Info Banner Image", "IMBI"),
    INFO_IMBU("More Info Banner URL", "IMBU"),
    INFO_IMIT("More Info Text", "IMIT"),
    INFO_IMIU("More Info URL", "IMIU"),
    INFO_IMUS("Music By", "IMUS"),
    INFO_IAS9("Ninth Language", "IAS9"),
    INFO_PRT2("Number Of Parts", "PRT2"),
    INFO_TORG("Organisation", "TORG"),
    INFO_PRT1("Part", "PRT1"),
    INFO_IPRO("Produced By", "IPRO"),
    INFO_IPRD("Product Name", "IPRD"),
    INFO_IPDS("Production Designer", "IPDS"),
    INFO_ISDT("Production Studio", "ISDT"),
    INFO_RATE("Rate", "RATE"),
    INFO_AGES("Rated", "AGES"),
    INFO_IRTD("Rating", "IRTD"),
    INFO_IRIP("Ripped By", "IRIP"),
    INFO_ISGN("Secondary Genre", "ISGN"),
    INFO_IAS2("Second Language", "IAS2"),
    INFO_IAS7("Seventh Language", "IAS7"),
    INFO_ISHP("Sharpness", "ISHP"),
    INFO_IAS6("Sixth Language", "IAS6"),
    INFO_ISFT("Software", "ISFT"),
    INFO_DISP("Sound Scheme Title", "DISP"),
    INFO_ISRC("Source", "ISRC"),
    INFO_ISRF("Source From", "ISRF"),
    INFO_ISTR("Starring", "ISTR"),
    INFO_STAR("Starring2", "STAR"),
    INFO_TCOD("Start Timecode", "TCOD"),
    INFO_STAT("Statistics", "STAT"),
    INFO_ISBJ("Subject", "ISBJ"),
    INFO_TAPE("Tape Name", "TAPE"),
    INFO_ITCH("Technician", "ITCH"),
    INFO_IAS3("Third Language", "IAS3"),
    INFO_ISMP("Time Code", "ISMP"),
    INFO_INAM("Title", "INAM"),
    INFO_IPRT("Track No", "IPRT"),
    INFO_TRCK("Track Number", "TRCK"),
    INFO_TURL("URL", "TURL"),
    INFO_VMAJ("Vegas Version Major", "VMAJ"),
    INFO_VMIN("Vegas Version Minor", "VMIN"),
    INFO_TVER("Version", "TVER"),
    INFO_IWMU("Watermark URL", "IWMU"),
    INFO_IWRI("Written By", "IWRI"),
    INFO_YEAR("Year", "YEAR");


    private final int    fourCC;
    private final String description;


    /**
     * Constructor.
     *
     * @param description A descriptive text for the chunk
     * @param asciiID The ASCII representation of the ID
     */
    private InfoRiffChunkId (final String description, final String asciiID)
    {
        this.description = description;
        this.fourCC = asciiID == null ? -1 : RiffChunkId.toFourCC (asciiID);
    }


    /** {@inheritDoc} */
    @Override
    public int getFourCC ()
    {
        return this.fourCC;
    }


    /** {@inheritDoc} */
    @Override
    public String getDescription ()
    {
        return this.description;
    }
}