// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.elektron;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Reads/write Elektron elmulti files. Also reads eldrum files. Velocity layers can only be assigned
 * to key ranges and not freely configured.
 *
 * @author Jürgen Moßgraber
 */
public class ElektronMultiFile
{
    private static final Pattern       KV         = Pattern.compile ("^([A-Za-z0-9-]+)\\s*=\\s*(.+)$");
    private static final String []     NOTE_NAMES =
    {
        "c",
        "c#",
        "d",
        "d#",
        "e",
        "f",
        "f#",
        "g",
        "g#",
        "a",
        "a#",
        "b"
    };

    /** Format version. */
    public int                         version    = 0;
    /** Instrument display name. */
    public String                      name;
    /** The key-zones. */
    public final List<ElektronKeyZone> keyZones   = new ArrayList<> ();
    /** Errors happening during the parsing. */
    public final List<String>          errors     = new ArrayList<> ();


    /** A key zone. */
    public static class ElektronKeyZone
    {
        /** MIDI note number (0-127). Defines the root note for this zone. */
        public int                               pitch;
        /** Pitch center for transposition. Usually equals pitch as float. */
        public double                            keyCenter;
        /** Each key zone contains one or more velocity layers. */
        public final List<ElektronVelocityLayer> velocityLayers = new ArrayList<> ();
    }


    /** A velocity layer. */
    public static class ElektronVelocityLayer
    {
        /**
         * Velocity threshold. Sample plays when input velocity >= this value. 0.0 - 1.0 (= MIDI
         * velocity / 127.0).
         */
        public double                         velocity;
        /**
         * Round-robin play-back strategy. 'Forward'. Applied if multiple sample slots are assigned
         * to a velocity layer.
         */
        public String                         strategy    = "Forward";
        /** Each velocity layer contains one or more sample slots. */
        public final List<ElektronSampleSlot> sampleSlots = new ArrayList<> ();
    }


    /**
     * A sample slot. Multiple sample slots under the same velocity layer create round-robin
     * variations.
     */
    public static class ElektronSampleSlot
    {
        /** Filename (relative path, same directory). */
        public String  sample;
        /** Loop behavior: 'Forward' / 'Off' (No looping (one-shot), default if omitted)). */
        public String  loopMode;
        /** Loop start point in samples. */
        public Integer loopStart;
        /** Loop end point in samples. */
        public Integer loopEnd;
        /** Loop cross-fade length in samples */
        public Integer loopCrossfade;
        /** Continue looping after key release (used for waveforms). */
        public Boolean keepLoopingOnRelease = Boolean.TRUE;
        /** Sample start position within WAV file (for single-file multi-sample). */
        public Integer trimStart;
        /** Sample end position within WAV file (for single-file multi-sample). */
        public Integer trimEnd;
    }


    /**
     * Creates a sample name.
     *
     * @param instrumentName The instrument name to add to the sample name
     * @param velocityLayer The velocity layer index to add to the sample name
     * @param midiNote The MIDI note to add to the sample name
     * @return The formatted sample name
     */
    public static String createSampleFileName (final String instrumentName, final int velocityLayer, final int midiNote)
    {
        if (midiNote < 0 || midiNote > 127)
            throw new IllegalArgumentException ("midiNote out of range");
        if (velocityLayer < 0)
            throw new IllegalArgumentException ("velocityLayer must be >= 0");
        final int note = midiNote % 12;
        final int octave = midiNote / 12 - 2;
        return String.format ("%s-%03d-%03d-%s%d.wav", instrumentName, Integer.valueOf (velocityLayer), Integer.valueOf (midiNote), NOTE_NAMES[note], Integer.valueOf (octave));
    }


    /**
     * Parses an Elektron Multi file.
     *
     * @param path The path to the file to parse
     * @throws IOException Could not read/parse the file
     */
    public void parse (final Path path) throws IOException
    {
        this.errors.clear ();

        ElektronKeyZone currentZone = null;
        ElektronVelocityLayer currentLayer = null;
        ElektronSampleSlot currentSlot = null;

        for (final String raw: Files.readAllLines (path))
        {
            final String line = raw.trim ();
            if (line.isEmpty () || line.startsWith ("#"))
                continue;

            if (line.equals ("[[key-zones]]"))
            {
                currentZone = new ElektronKeyZone ();
                this.keyZones.add (currentZone);
                currentLayer = null;
                currentSlot = null;
                continue;
            }

            if (line.equals ("[[key-zones.velocity-layers]]"))
            {
                if (currentZone == null)
                    throw new IllegalStateException ("velocity-layer without key-zone");

                currentLayer = new ElektronVelocityLayer ();
                currentZone.velocityLayers.add (currentLayer);
                currentSlot = null;
                continue;
            }

            if (line.equals ("[[key-zones.velocity-layers.sample-slots]]"))
            {
                if (currentLayer == null)
                    throw new IllegalStateException ("sample-slot without velocity-layer");
                currentSlot = new ElektronSampleSlot ();
                currentLayer.sampleSlots.add (currentSlot);
                continue;
            }

            final Matcher m = KV.matcher (line);
            if (!m.matches ())
                throw new IllegalArgumentException ("Invalid line: " + line);
            final String key = m.group (1);
            final String value = stripQuotes (m.group (2).trim ());

            if (currentSlot != null)
                this.assignSampleSlot (currentSlot, key, value);
            else if (currentLayer != null)
                this.assignVelocityLayer (currentLayer, key, value);
            else if (currentZone != null)
                this.assignKeyZone (currentZone, key, value);
            else
                this.assignRoot (this, key, value);
        }
    }


    /**
     * Writes an Elektron Multi file.
     *
     * @param path The path to write to
     * @throws IOException Could not write the file
     */
    public void write (final Path path) throws IOException
    {
        final List<String> out = new ArrayList<> ();
        out.add ("# ELEKTRON MULTI-SAMPLE MAPPING FORMAT");
        out.add ("version = " + this.version);
        out.add ("name = '" + escape (this.name) + "'");
        out.add ("");

        for (final ElektronKeyZone keyZone: this.keyZones)
        {
            out.add ("[[key-zones]]");
            out.add ("pitch = " + keyZone.pitch);
            out.add ("key-center = " + keyZone.keyCenter);
            out.add ("");

            for (final ElektronVelocityLayer velocityLayer: keyZone.velocityLayers)
            {
                out.add ("[[key-zones.velocity-layers]]");
                out.add ("velocity = " + velocityLayer.velocity);
                if (velocityLayer.strategy != null)
                    out.add ("strategy = '" + escape (velocityLayer.strategy) + "'");
                out.add ("");

                for (final ElektronSampleSlot sampleSlot: velocityLayer.sampleSlots)
                {
                    out.add ("[[key-zones.velocity-layers.sample-slots]]");
                    out.add ("sample = '" + escape (sampleSlot.sample) + "'");
                    if (sampleSlot.loopMode != null)
                        out.add ("loop-mode = '" + escape (sampleSlot.loopMode) + "'");
                    if (sampleSlot.loopStart != null && sampleSlot.loopStart.intValue () >= 0)
                        out.add ("loop-start = " + sampleSlot.loopStart);
                    if (sampleSlot.loopEnd != null && sampleSlot.loopEnd.intValue () >= 0)
                        out.add ("loop-end = " + sampleSlot.loopEnd);
                    if (sampleSlot.loopCrossfade != null && sampleSlot.loopCrossfade.intValue () >= 0)
                        out.add ("loop-crossfade = " + sampleSlot.loopCrossfade);
                    if (sampleSlot.keepLoopingOnRelease != null && !sampleSlot.keepLoopingOnRelease.booleanValue ())
                        out.add ("keep-looping-on-release = " + sampleSlot.keepLoopingOnRelease);
                    if (sampleSlot.trimStart != null && sampleSlot.trimStart.intValue () >= 0)
                        out.add ("trim-start = " + sampleSlot.trimStart);
                    if (sampleSlot.trimEnd != null && sampleSlot.trimEnd.intValue () >= 0)
                        out.add ("trim-end = " + sampleSlot.trimEnd);
                    out.add ("");
                }
            }
        }
        if (out.get (out.size () - 1).isBlank ())
            out.remove (out.size () - 1);
        Files.write (path, out);
    }


    private void assignRoot (final ElektronMultiFile multi, final String tag, final String value)
    {
        switch (tag)
        {
            case "version" -> multi.version = Integer.parseInt (value);
            case "name" -> multi.name = value;
            default -> this.errors.add ("Unknown root tag: " + tag);
        }
    }


    private void assignKeyZone (final ElektronKeyZone keyZone, final String tag, final String value)
    {
        switch (tag)
        {
            case "pitch" -> keyZone.pitch = Integer.parseInt (value);
            case "key-center" -> keyZone.keyCenter = Double.parseDouble (value);
            default -> this.errors.add ("Unknown key-zone tag: " + tag);
        }
    }


    private void assignVelocityLayer (final ElektronVelocityLayer velocityLayer, final String tag, final String value)
    {
        switch (tag)
        {
            case "velocity" -> velocityLayer.velocity = Double.parseDouble (value);
            case "strategy" -> velocityLayer.strategy = value;
            default -> this.errors.add ("Unknown velocity-layer tag: " + tag);
        }
    }


    private void assignSampleSlot (final ElektronSampleSlot sampleSlot, final String tag, final String value)
    {
        switch (tag)
        {
            case "sample" -> sampleSlot.sample = value;
            case "loop-mode" -> sampleSlot.loopMode = value;
            case "loop-start" -> sampleSlot.loopStart = Integer.valueOf (value);
            case "loop-end" -> sampleSlot.loopEnd = Integer.valueOf (value);
            case "loop-crossfade" -> sampleSlot.loopCrossfade = Integer.valueOf (value);
            case "keep-looping-on-release" -> sampleSlot.keepLoopingOnRelease = Boolean.valueOf (value);
            case "trim-start" -> sampleSlot.trimStart = Integer.valueOf (value);
            case "trim-end" -> sampleSlot.trimEnd = Integer.valueOf (value);
            default -> this.errors.add ("Unknown sample-slot tag: " + tag);
        }
    }


    private static String stripQuotes (final String s)
    {
        if (s.startsWith ("\"") && s.endsWith ("\"") || s.startsWith ("'") && s.endsWith ("'"))
            return s.substring (1, s.length () - 1);
        return s;
    }


    private static String escape (final String s)
    {
        return s == null ? "" : s.replace ("'", "\'");
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "ElektronMultiFile{name='" + this.name + "', version=" + this.version + ", keyZones=" + this.keyZones.size () + "}";
    }
}
