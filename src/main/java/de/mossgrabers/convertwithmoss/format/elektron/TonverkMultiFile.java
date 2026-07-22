// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.elektron;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.mossgrabers.tools.Pair;


/**
 * Reads/write Elektron elmulti files. Also reads eldrum files. Velocity layers can only be assigned
 * to key ranges and not freely configured.
 *
 * @author Jürgen Moßgraber
 */
public class TonverkMultiFile
{
    private static final Pattern      KV         = Pattern.compile ("^([A-Za-z0-9-]+)\\s*=\\s*(.+)$");
    private static final String []    NOTE_NAMES =
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
    public int                        version    = 0;
    /** Instrument display name. */
    public String                     name;
    /** The key-zones. */
    public final List<TonverkKeyZone> keyZones   = new ArrayList<> ();
    /** Errors happening during the parsing. */
    public final List<String>         errors     = new ArrayList<> ();


    /** Helper class for parsing the zone, layer, slot hierarchy. */
    public static class ParseHierarchy
    {
        /** The current zone. */
        public TonverkKeyZone       currentZone  = null;
        /** The current layer. */
        public TonverkVelocityLayer currentLayer = null;
        /** The current slot. */
        public TonverkSampleSlot    currentSlot  = null;
    }


    /** A key zone. */
    public static class TonverkKeyZone
    {
        /** MIDI note number (0-127). Defines the root note for this zone. */
        public int                              pitch;
        /** Pitch center for transposition. Usually equals pitch as float. */
        public double                           keyCenter;
        /** Each key zone contains one or more velocity layers. */
        public final List<TonverkVelocityLayer> velocityLayers = new ArrayList<> ();
    }


    /** A velocity layer. */
    public static class TonverkVelocityLayer
    {
        /**
         * Velocity threshold. Sample plays when input velocity >= this value. 0.0 - 1.0 (= MIDI
         * velocity / 127.0).
         */
        public double                        velocity;
        /**
         * Round-robin play-back strategy. 'Forward'. Applied if multiple sample slots are assigned
         * to a velocity layer.
         */
        public String                        strategy    = "Forward";
        /** Each velocity layer contains one or more sample slots. */
        public final List<TonverkSampleSlot> sampleSlots = new ArrayList<> ();
    }


    /**
     * A sample slot. Multiple sample slots under the same velocity layer create round-robin
     * variations.
     */
    public static class TonverkSampleSlot
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
        /**
         * Continue looping after key release. If omitted, the Tonverk stops looping on key release
         * (sustain loop).
         */
        public Boolean keepLoopingOnRelease = Boolean.FALSE;
        /** Sample start position within WAV file (for single-file multi-sample). */
        public Integer trimStart;
        /** Sample end position within WAV file (for single-file multi-sample). */
        public Integer trimEnd;
    }


    /**
     * Creates a sample file name including the '.wav' file ending.
     *
     * @param instrumentName The instrument name to add to the sample name
     * @param velocityLayer The velocity layer index to add to the sample name
     * @param midiNote The MIDI note to add to the sample name
     * @return The formatted sample file name
     */
    public static String createSampleFileName (final String instrumentName, final int velocityLayer, final int midiNote)
    {
        return createSampleName (instrumentName, velocityLayer, midiNote, 0) + ".wav";
    }


    /**
     * Creates a sample name (without the file ending) following the Elektron factory naming
     * convention: 'instrumentName-VVV-NNN-note' with an additional '-rrN' suffix for round-robin
     * samples.
     *
     * @param instrumentName The instrument name to add to the sample name
     * @param velocityLayer The velocity layer index to add to the sample name
     * @param midiNote The MIDI note to add to the sample name
     * @param roundRobinIndex The index of the sample in a round-robin chain, no suffix is added for
     *            values less than 1
     * @return The formatted sample name
     */
    public static String createSampleName (final String instrumentName, final int velocityLayer, final int midiNote, final int roundRobinIndex)
    {
        if (midiNote < 0 || midiNote > 127)
            throw new IllegalArgumentException ("midiNote out of range");
        if (velocityLayer < 0)
            throw new IllegalArgumentException ("velocityLayer must be >= 0");
        final int note = midiNote % 12;
        final int octave = midiNote / 12 - 2;
        final String name = String.format ("%s-%03d-%03d-%s%d", instrumentName, Integer.valueOf (velocityLayer), Integer.valueOf (midiNote), NOTE_NAMES[note], Integer.valueOf (octave));
        return roundRobinIndex > 0 ? name + "-rr" + roundRobinIndex : name;
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

        final ParseHierarchy hierarchy = new ParseHierarchy ();
        for (final String raw: Files.readAllLines (path))
        {
            final String line = raw.trim ();
            if (line.isEmpty () || line.startsWith ("#"))
                continue;

            if (line.equals ("[[key-zones]]"))
            {
                hierarchy.currentZone = new TonverkKeyZone ();
                this.keyZones.add (hierarchy.currentZone);
                hierarchy.currentLayer = null;
                hierarchy.currentSlot = null;
                continue;
            }

            if (line.equals ("[[key-zones.velocity-layers]]"))
            {
                if (hierarchy.currentZone == null)
                    throw new IllegalStateException ("velocity-layer without key-zone");

                hierarchy.currentLayer = new TonverkVelocityLayer ();
                hierarchy.currentZone.velocityLayers.add (hierarchy.currentLayer);
                hierarchy.currentSlot = null;
                continue;
            }

            if (line.equals ("[[key-zones.velocity-layers.sample-slots]]"))
            {
                if (hierarchy.currentLayer == null)
                    throw new IllegalStateException ("sample-slot without velocity-layer");
                hierarchy.currentSlot = new TonverkSampleSlot ();
                hierarchy.currentLayer.sampleSlots.add (hierarchy.currentSlot);
                continue;
            }

            final Pair<String, String> keyValue = getKeyValue (line);
            if (hierarchy.currentSlot != null)
                this.assignSampleSlot (hierarchy.currentSlot, keyValue);
            else if (hierarchy.currentLayer != null)
                this.assignVelocityLayer (hierarchy.currentLayer, keyValue);
            else if (hierarchy.currentZone != null)
                this.assignKeyZone (hierarchy.currentZone, keyValue);
            else
                this.assignRoot (this, keyValue);
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
        out.add ("name = " + quote (this.name));

        for (final TonverkKeyZone keyZone: this.keyZones)
        {
            out.add ("");
            out.add ("[[key-zones]]");
            out.add ("pitch = " + keyZone.pitch);
            out.add ("key-center = " + formatNumber (keyZone.keyCenter));

            for (final TonverkVelocityLayer velocityLayer: keyZone.velocityLayers)
            {
                out.add ("");
                out.add ("[[key-zones.velocity-layers]]");
                out.add ("velocity = " + formatNumber (velocityLayer.velocity));
                if (velocityLayer.strategy != null)
                    out.add ("strategy = " + quote (velocityLayer.strategy));

                for (final TonverkSampleSlot sampleSlot: velocityLayer.sampleSlots)
                {
                    out.add ("");
                    out.add ("[[key-zones.velocity-layers.sample-slots]]");
                    out.add ("sample = " + quote (sampleSlot.sample));
                    if (sampleSlot.trimStart != null && sampleSlot.trimStart.intValue () >= 0)
                        out.add ("trim-start = " + sampleSlot.trimStart);
                    if (sampleSlot.trimEnd != null && sampleSlot.trimEnd.intValue () >= 0)
                        out.add ("trim-end = " + sampleSlot.trimEnd);
                    if (sampleSlot.loopMode != null)
                        out.add ("loop-mode = " + quote (sampleSlot.loopMode));
                    if (TonverkValues.LOOP_MODE_FORWARD.equals (sampleSlot.loopMode))
                    {
                        if (sampleSlot.loopStart != null && sampleSlot.loopStart.intValue () >= 0)
                            out.add ("loop-start = " + sampleSlot.loopStart);
                        if (sampleSlot.loopEnd != null && sampleSlot.loopEnd.intValue () >= 0)
                            out.add ("loop-end = " + sampleSlot.loopEnd);
                        if (sampleSlot.loopCrossfade != null && sampleSlot.loopCrossfade.intValue () >= 0)
                            out.add ("loop-crossfade = " + sampleSlot.loopCrossfade);
                        if (sampleSlot.keepLoopingOnRelease != null && sampleSlot.keepLoopingOnRelease.booleanValue ())
                            out.add ("keep-looping-on-release = true");
                    }
                }
            }
        }
        Files.write (path, out);
    }


    private void assignRoot (final TonverkMultiFile multi, final Pair<String, String> keyValue)
    {
        switch (keyValue.getKey ())
        {
            case "version" -> multi.version = Integer.parseInt (keyValue.getValue ());
            case "name" -> multi.name = keyValue.getValue ();
            default -> this.errors.add ("Unknown root tag: " + keyValue.getKey ());
        }
    }


    private void assignKeyZone (final TonverkKeyZone keyZone, final Pair<String, String> keyValue)
    {
        switch (keyValue.getKey ())
        {
            case "pitch" -> keyZone.pitch = Integer.parseInt (keyValue.getValue ());
            case "key-center" -> keyZone.keyCenter = Double.parseDouble (keyValue.getValue ());
            default -> this.errors.add ("Unknown key-zone tag: " + keyValue.getKey ());
        }
    }


    private void assignVelocityLayer (final TonverkVelocityLayer velocityLayer, final Pair<String, String> keyValue)
    {
        switch (keyValue.getKey ())
        {
            case "velocity" -> velocityLayer.velocity = Double.parseDouble (keyValue.getValue ());
            case "strategy" -> velocityLayer.strategy = keyValue.getValue ();
            default -> this.errors.add ("Unknown velocity-layer tag: " + keyValue.getKey ());
        }
    }


    private void assignSampleSlot (final TonverkSampleSlot sampleSlot, final Pair<String, String> keyValue)
    {
        switch (keyValue.getKey ())
        {
            case "sample" -> sampleSlot.sample = keyValue.getValue ();
            case "loop-mode" -> sampleSlot.loopMode = keyValue.getValue ();
            case "loop-start" -> sampleSlot.loopStart = Integer.valueOf (keyValue.getValue ());
            case "loop-end" -> sampleSlot.loopEnd = Integer.valueOf (keyValue.getValue ());
            case "loop-crossfade" -> sampleSlot.loopCrossfade = Integer.valueOf (keyValue.getValue ());
            case "keep-looping-on-release" -> sampleSlot.keepLoopingOnRelease = Boolean.valueOf (keyValue.getValue ());
            case "trim-start" -> sampleSlot.trimStart = Integer.valueOf (keyValue.getValue ());
            case "trim-end" -> sampleSlot.trimEnd = Integer.valueOf (keyValue.getValue ());
            default -> this.errors.add ("Unknown sample-slot tag: " + keyValue.getKey ());
        }
    }


    private static String stripQuotes (final String s)
    {
        if (s.startsWith ("\"") && s.endsWith ("\"") || s.startsWith ("'") && s.endsWith ("'"))
            return s.substring (1, s.length () - 1);
        return s;
    }


    /**
     * Quotes a string value. Uses single quotes except when the value contains a single quote, in
     * that case double quotes are used and contained double quotes are escaped.
     *
     * @param value The value to quote
     * @return The quoted value
     */
    private static String quote (final String value)
    {
        final String text = value == null ? "" : value;
        if (!text.contains ("'"))
            return "'" + text + "'";
        return '"' + text.replace ("\"", "\\\"") + '"';
    }


    /**
     * Formats a floating point number like the Tonverk factory files: integral values get one
     * decimal place (e.g. '60.0'), all others a maximum of 8 significant digits (e.g.
     * '0.49411765').
     *
     * @param value The value to format
     * @return The formatted value
     */
    private static String formatNumber (final double value)
    {
        if (value == Math.rint (value) && Math.abs (value) < 1e15)
            return String.format (Locale.US, "%.1f", Double.valueOf (value));

        String text = String.format (Locale.US, "%.8g", Double.valueOf (value));
        if (text.indexOf ('e') < 0 && text.indexOf ('E') < 0 && text.indexOf ('.') >= 0)
        {
            // Remove trailing zeros but keep at least one decimal digit
            text = text.replaceAll ("0+$", "");
            if (text.endsWith ("."))
                text += "0";
        }
        return text;
    }


    private static Pair<String, String> getKeyValue (final String line)
    {
        final Matcher m = KV.matcher (line);
        if (!m.matches ())
            throw new IllegalArgumentException ("Invalid line: " + line);
        final String key = m.group (1);
        final String value = stripQuotes (m.group (2).trim ());
        return new Pair<> (key, value);
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "TonverkMultiFile{name='" + this.name + "', version=" + this.version + ", keyZones=" + this.keyZones.size () + "}";
    }
}
