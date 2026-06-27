// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.elektron;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.format.elektron.TonverkMultiFile.TonverkKeyZone;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkMultiFile.TonverkSampleSlot;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkMultiFile.TonverkVelocityLayer;


/**
 * Reads and writes Elektron Tonverk preset files (*.tvpst). In contrast to the elmulti/eldrum
 * mapping files, a preset is a full sound: it adds a flat <code>[parameters]</code> block (envelopes,
 * filter, LFOs, FX, arpeggiator, ...) and embeds the sample mapping as a nested
 * <code>*_mapping_slot</code> table. The file is a small sub-set of TOML (single-quoted scalar
 * values, arrays of tables for the key-zones).
 *
 * @author Jürgen Moßgraber
 */
public class TonverkPresetFile
{
    /** The generator (sound engine) machine of a Tonverk preset. */
    public enum Machine
    {
        /** One-Shot machine: a single sample mapped across the whole keyboard. */
        ONESHOT ("gen_oneshot"),
        /** Multi machine: a multi-sample mapped to key- and velocity-ranges. */
        MULTI ("gen_multi"),
        /** Drum machine: a kit of up to several drum voices. */
        DRUM ("gen_drum"),
        /** Unknown/unsupported machine. */
        UNKNOWN ("");


        private final String parameterPrefix;


        private Machine (final String parameterPrefix)
        {
            this.parameterPrefix = parameterPrefix;
        }


        /**
         * Get the prefix of the parameter names belonging to this machine (e.g. 'gen_multi').
         *
         * @return The parameter prefix
         */
        public String getParameterPrefix ()
        {
            return this.parameterPrefix;
        }


        /**
         * Map the value of the 'gen_machine' parameter to a machine.
         *
         * @param value The 'gen_machine' value ('0', '1' or '2')
         * @return The machine, {@link #UNKNOWN} if it could not be mapped
         */
        public static Machine fromGenMachine (final String value)
        {
            if (value == null)
                return UNKNOWN;
            return switch (value.trim ())
            {
                case "0" -> ONESHOT;
                case "1" -> MULTI;
                case "2" -> DRUM;
                default -> UNKNOWN;
            };
        }
    }


    /** Format version of the preset (the top-level 'version'). */
    public int                         version            = 2;
    /** The preset category (e.g. 'KEYS', 'DRUMS'). */
    public String                      category           = "";
    /** The preset tags. */
    public final List<String>          tags               = new ArrayList<> ();
    /** All entries of the flat '[parameters]' block, in file order, values without quotes. */
    public final Map<String, String>   parameters         = new LinkedHashMap<> ();
    /** The machine derived from the 'gen_machine' parameter (set after {@link #parse(Path)}). */
    public Machine                     machine            = Machine.UNKNOWN;
    /** The display name stored in the mapping slot. */
    public String                      mappingSlotName    = "";
    /** The version of the mapping slot. */
    public int                         mappingSlotVersion = 0;
    /** The key-zones of the mapping slot (Multi and Drum machines). */
    public final List<TonverkKeyZone> keyZones           = new ArrayList<> ();
    /** Errors which occurred during parsing. */
    public final List<String>          errors             = new ArrayList<> ();


    /**
     * Parses a Tonverk preset file.
     *
     * @param path The path to the file to parse
     * @throws IOException Could not read/parse the file
     */
    public void parse (final Path path) throws IOException
    {
        this.parse (Files.readAllLines (path));
    }


    /**
     * Parses the lines of a Tonverk preset file (or a template).
     *
     * @param lines The lines to parse
     */
    public void parse (final List<String> lines)
    {
        this.errors.clear ();

        boolean inParameters = false;
        boolean inMappingSlotRoot = false;
        TonverkKeyZone currentZone = null;
        TonverkVelocityLayer currentLayer = null;
        TonverkSampleSlot currentSlot = null;

        for (int i = 0; i < lines.size (); i++)
        {
            final String line = lines.get (i).trim ();
            if (line.isEmpty () || line.startsWith ("#"))
                continue;

            // An array of tables, e.g. '[[parameters.gen_multi_mapping_slot.key-zones]]'
            if (line.startsWith ("[["))
            {
                final String section = stripSectionBrackets (line);
                if (section.endsWith (".sample-slots"))
                {
                    if (currentLayer == null)
                        this.errors.add ("sample-slot without velocity-layer");
                    else
                    {
                        currentSlot = new TonverkSampleSlot ();
                        currentLayer.sampleSlots.add (currentSlot);
                    }
                }
                else if (section.endsWith (".velocity-layers"))
                {
                    if (currentZone == null)
                        this.errors.add ("velocity-layer without key-zone");
                    else
                    {
                        currentLayer = new TonverkVelocityLayer ();
                        currentZone.velocityLayers.add (currentLayer);
                        currentSlot = null;
                    }
                }
                else if (section.endsWith (".key-zones"))
                {
                    currentZone = new TonverkKeyZone ();
                    this.keyZones.add (currentZone);
                    currentLayer = null;
                    currentSlot = null;
                }
                else
                    this.errors.add ("Unknown array section: " + section);
                inMappingSlotRoot = false;
                continue;
            }

            // A table, e.g. '[parameters]' or '[parameters.gen_multi_mapping_slot]'
            if (line.startsWith ("["))
            {
                final String section = stripSectionBrackets (line);
                if (section.equals ("parameters"))
                {
                    inParameters = true;
                    inMappingSlotRoot = false;
                }
                else if (section.startsWith ("parameters.") && section.endsWith ("_mapping_slot"))
                {
                    inMappingSlotRoot = true;
                    currentZone = null;
                    currentLayer = null;
                    currentSlot = null;
                }
                else
                    this.errors.add ("Unknown section: " + section);
                continue;
            }

            // A key/value pair
            final int eq = line.indexOf ('=');
            if (eq < 0)
            {
                this.errors.add ("Invalid line: " + line);
                continue;
            }
            final String key = line.substring (0, eq).trim ();
            final String rawValue = line.substring (eq + 1).trim ();

            // An array value, either inline ('[]' or "[ 'a', 'b' ]") or multi-line ('[' followed by
            // the items on the next lines up to a closing ']').
            if (rawValue.startsWith ("["))
            {
                final List<String> items = new ArrayList<> ();
                if (rawValue.equals ("["))
                {
                    while (i + 1 < lines.size ())
                    {
                        final String arrayLine = lines.get (++i).trim ();
                        if (arrayLine.equals ("]"))
                            break;
                        final String item = stripQuotes (arrayLine.endsWith (",") ? arrayLine.substring (0, arrayLine.length () - 1) : arrayLine);
                        if (!item.isEmpty ())
                            items.add (item);
                    }
                }
                else
                {
                    String inline = rawValue.substring (1);
                    if (inline.endsWith ("]"))
                        inline = inline.substring (0, inline.length () - 1);
                    for (final String part: inline.split (","))
                    {
                        final String item = stripQuotes (part.trim ());
                        if (!item.isEmpty ())
                            items.add (item);
                    }
                }
                if ("tags".equals (key))
                    this.tags.addAll (items);
                continue;
            }

            final String value = stripQuotes (rawValue);

            if (currentSlot != null)
                this.assignSampleSlot (currentSlot, key, value);
            else if (currentLayer != null)
                this.assignVelocityLayer (currentLayer, key, value);
            else if (currentZone != null)
                this.assignKeyZone (currentZone, key, value);
            else if (inMappingSlotRoot)
            {
                if ("name".equals (key))
                    this.mappingSlotName = value;
                else if ("version".equals (key))
                    this.mappingSlotVersion = this.parseIntSafe (value, 0);
            }
            else if (inParameters)
                this.parameters.put (key, value);
            else
                switch (key)
                {
                    case "version" -> this.version = this.parseIntSafe (value, 2);
                    case "category" -> this.category = value;
                    default -> this.errors.add ("Unknown root tag: " + key);
                }
        }

        this.machine = Machine.fromGenMachine (this.parameters.get ("gen_machine"));
    }


    /**
     * Writes a Tonverk preset file.
     *
     * @param path The path to write to
     * @throws IOException Could not write the file
     */
    public void write (final Path path) throws IOException
    {
        final List<String> out = new ArrayList<> ();
        out.add ("version = " + this.version);
        if (this.category != null && !this.category.isEmpty ())
            out.add ("category = " + quote (this.category));
        if (!this.tags.isEmpty ())
        {
            out.add ("tags = [");
            for (final String tag: this.tags)
                out.add ("    " + quote (tag) + ",");
            out.add ("]");
        }

        out.add ("");
        out.add ("[parameters]");
        for (final Map.Entry<String, String> entry: this.parameters.entrySet ())
            out.add (entry.getKey () + " = " + quote (entry.getValue ()));

        // The mapping slot is written last (matches the device's file layout). One-Shot presets
        // carry their single sample in the parameters block and have no mapping slot.
        if (!this.keyZones.isEmpty () && this.machine != Machine.UNKNOWN && this.machine != Machine.ONESHOT)
        {
            final String prefix = "parameters." + this.machine.getParameterPrefix () + "_mapping_slot";
            out.add ("");
            out.add ("[" + prefix + "]");
            out.add ("version = " + this.mappingSlotVersion);
            out.add ("name = " + quote (this.mappingSlotName));

            for (final TonverkKeyZone zone: this.keyZones)
            {
                out.add ("");
                out.add ("[[" + prefix + ".key-zones]]");
                out.add ("pitch = " + zone.pitch);
                out.add ("key-center = " + formatNumber (zone.keyCenter));

                for (final TonverkVelocityLayer layer: zone.velocityLayers)
                {
                    out.add ("");
                    out.add ("[[" + prefix + ".key-zones.velocity-layers]]");
                    out.add ("velocity = " + formatNumber (layer.velocity));
                    if (layer.strategy != null)
                        out.add ("strategy = " + quote (layer.strategy));

                    for (final TonverkSampleSlot slot: layer.sampleSlots)
                    {
                        out.add ("");
                        out.add ("[[" + prefix + ".key-zones.velocity-layers.sample-slots]]");
                        out.add ("sample = " + quote (slot.sample));
                        if (slot.loopMode != null)
                            out.add ("loop-mode = " + quote (slot.loopMode));
                        if ("Forward".equals (slot.loopMode))
                        {
                            if (slot.loopStart != null && slot.loopStart.intValue () >= 0)
                                out.add ("loop-start = " + slot.loopStart);
                            if (slot.loopEnd != null && slot.loopEnd.intValue () >= 0)
                                out.add ("loop-end = " + slot.loopEnd);
                            if (slot.loopCrossfade != null && slot.loopCrossfade.intValue () >= 0)
                                out.add ("loop-crossfade = " + slot.loopCrossfade);
                            if (slot.keepLoopingOnRelease != null && slot.keepLoopingOnRelease.booleanValue ())
                                out.add ("keep-looping-on-release = true");
                        }
                    }
                }
            }
        }

        Files.write (path, out);
    }


    /**
     * Get a parameter value as text.
     *
     * @param key The parameter name
     * @return The value or null if not present
     */
    public String param (final String key)
    {
        return this.parameters.get (key);
    }


    /**
     * Get a parameter value as a floating point number.
     *
     * @param key The parameter name
     * @param defaultValue The value to return if the parameter is missing or not a number
     * @return The value
     */
    public double paramDouble (final String key, final double defaultValue)
    {
        final String value = this.parameters.get (key);
        if (value == null || value.isBlank ())
            return defaultValue;
        try
        {
            return Double.parseDouble (value);
        }
        catch (final NumberFormatException ex)
        {
            return defaultValue;
        }
    }


    /**
     * Get a parameter value as an integer number.
     *
     * @param key The parameter name
     * @param defaultValue The value to return if the parameter is missing or not a number
     * @return The value
     */
    public int paramInt (final String key, final int defaultValue)
    {
        final String value = this.parameters.get (key);
        if (value == null || value.isBlank ())
            return defaultValue;
        try
        {
            return (int) Math.round (Double.parseDouble (value));
        }
        catch (final NumberFormatException ex)
        {
            return defaultValue;
        }
    }


    private void assignKeyZone (final TonverkKeyZone keyZone, final String key, final String value)
    {
        switch (key)
        {
            case "pitch" -> keyZone.pitch = this.parseIntSafe (value, 0);
            case "key-center" -> keyZone.keyCenter = this.parseDoubleSafe (value, 0);
            default -> this.errors.add ("Unknown key-zone tag: " + key);
        }
    }


    private void assignVelocityLayer (final TonverkVelocityLayer velocityLayer, final String key, final String value)
    {
        switch (key)
        {
            case "velocity" -> velocityLayer.velocity = this.parseDoubleSafe (value, 0);
            case "strategy" -> velocityLayer.strategy = value;
            default -> this.errors.add ("Unknown velocity-layer tag: " + key);
        }
    }


    private void assignSampleSlot (final TonverkSampleSlot sampleSlot, final String key, final String value)
    {
        switch (key)
        {
            case "sample" -> sampleSlot.sample = value;
            case "loop-mode" -> sampleSlot.loopMode = value;
            case "loop-start" -> sampleSlot.loopStart = Integer.valueOf (this.parseIntSafe (value, 0));
            case "loop-end" -> sampleSlot.loopEnd = Integer.valueOf (this.parseIntSafe (value, 0));
            case "loop-crossfade" -> sampleSlot.loopCrossfade = Integer.valueOf (this.parseIntSafe (value, 0));
            case "keep-looping-on-release" -> sampleSlot.keepLoopingOnRelease = Boolean.valueOf (value);
            case "trim-start" -> sampleSlot.trimStart = Integer.valueOf (this.parseIntSafe (value, 0));
            case "trim-end" -> sampleSlot.trimEnd = Integer.valueOf (this.parseIntSafe (value, 0));
            default -> this.errors.add ("Unknown sample-slot tag: " + key);
        }
    }


    private int parseIntSafe (final String value, final int defaultValue)
    {
        try
        {
            return (int) Math.round (Double.parseDouble (value.trim ()));
        }
        catch (final NumberFormatException ex)
        {
            this.errors.add ("Not an integer: " + value);
            return defaultValue;
        }
    }


    private double parseDoubleSafe (final String value, final double defaultValue)
    {
        try
        {
            return Double.parseDouble (value.trim ());
        }
        catch (final NumberFormatException ex)
        {
            this.errors.add ("Not a number: " + value);
            return defaultValue;
        }
    }


    private static String stripSectionBrackets (final String line)
    {
        String result = line.trim ();
        while (result.startsWith ("["))
            result = result.substring (1);
        while (result.endsWith ("]"))
            result = result.substring (0, result.length () - 1);
        return result.trim ();
    }


    private static String stripQuotes (final String value)
    {
        final String trimmed = value.trim ();
        if (trimmed.length () >= 2 && trimmed.startsWith ("'") && trimmed.endsWith ("'"))
            return trimmed.substring (1, trimmed.length () - 1);
        return trimmed;
    }


    private static String quote (final String value)
    {
        return "'" + (value == null ? "" : value) + "'";
    }


    private static String formatNumber (final double value)
    {
        if (value == Math.rint (value) && !Double.isInfinite (value))
            return Long.toString ((long) value) + ".0";
        return Double.toString (value);
    }
}
