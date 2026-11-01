// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractRIFFFile;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.format.akai.akp.AkmFile;
import de.mossgrabers.convertwithmoss.format.akai.akp.AkpFile;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpGeneration;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpKeygroupLocation;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpZone;


/**
 * Developer tool which checks reading and writing of AKP and AKM files. For each file found in the
 * given folders (recursively):
 * <ul>
 * <li>The file is read, written unchanged and the result must be byte identical to the
 * original.</li>
 * <li>AKP files are additionally written in all 3 generations. Reading these files again must
 * result in the same parameters as the original file and writing them again in the same generation
 * must result in identical bytes.</li>
 * <li>AKP files are converted to sample zones, from which a new AKP file is created in all 3
 * generations (like the creator does it). Reading that file must result in the same sample
 * zones.</li>
 * </ul>
 * Call with the folders to check as parameters.
 *
 * @author Jürgen Moßgraber
 */
public class AkpRoundTrip
{
    /** Fields which are not compared since they differ by intention between the generations. */
    private static final Set<String> IGNORED_FIELDS  = Set.of ("version", "originalData", "writeGeneration", "lfoCounter", "subChunks", "ignoredChunks", "minimumSize");
    /** The package of the classes which are compared field by field. */
    private static final String      AKP_PACKAGE     = "de.mossgrabers.convertwithmoss.format.akai.akp";
    /** The maximum number of reported parameter differences of a file. */
    private static final int         MAX_DIFFERENCES = 5;

    private final List<String>       failures        = new ArrayList<> ();
    private int                      numberOfFiles   = 0;
    private int                      readErrors      = 0;


    /**
     * Main method.
     *
     * @param args The folders to check
     */
    public static void main (final String [] args)
    {
        if (args.length == 0)
        {
            System.out.println ("Usage: AkpRoundTrip folder1 [folder2 ...]");
            return;
        }

        final AkpRoundTrip roundTrip = new AkpRoundTrip ();
        roundTrip.checkNewFile ();
        for (final String folder: args)
            roundTrip.checkFolder (new File (folder));
        roundTrip.printSummary ();
        System.exit (roundTrip.failures.isEmpty () ? 0 : 1);
    }


    /**
     * Checks all AKP and AKM files in a folder and its sub-folders.
     *
     * @param folder The folder
     */
    private void checkFolder (final File folder)
    {
        final List<Path> files;
        try (final Stream<Path> stream = Files.walk (folder.toPath ()))
        {
            files = stream.filter (path -> {
                final String name = path.getFileName ().toString ().toLowerCase ();
                return Files.isRegularFile (path) && (name.endsWith (".akp") || name.endsWith (".akm"));
            }).sorted ().toList ();
        }
        catch (final IOException ex)
        {
            this.failures.add (folder + ": Could not read folder: " + ex.getMessage ());
            return;
        }

        for (final Path path: files)
            this.checkFile (path.toFile ());
    }


    /**
     * Checks one AKP or AKM file.
     *
     * @param file The file
     */
    private void checkFile (final File file)
    {
        this.numberOfFiles++;
        final boolean isAkp = file.getName ().toLowerCase ().endsWith (".akp");

        final byte [] original;
        final AbstractRIFFFile riffFile;
        try
        {
            original = Files.readAllBytes (file.toPath ());
            riffFile = isAkp ? new AkpFile (file) : new AkmFile (file);
        }
        catch (final IOException | ParseException | RuntimeException ex)
        {
            // These are problems of the reader and not of the round-trip
            this.readErrors++;
            System.out.println ("Could not read: " + file + " - " + ex.getMessage ());
            return;
        }

        try
        {
            final Map<String, String> originalParameters = dumpParameters (riffFile);

            // 1. Unchanged
            final byte [] written = write (riffFile, Optional.empty ());
            this.compareBytes (file, "unchanged", original, written);
            final AbstractRIFFFile reread = read (written, isAkp);
            this.compareParameters (file, "unchanged", originalParameters, dumpParameters (reread));

            // 2. All generations
            if (isAkp)
                for (final AkpGeneration generation: AkpGeneration.values ())
                {
                    final String step = generation.name ();
                    final byte [] converted = write (riffFile, Optional.of (generation));
                    final AbstractRIFFFile convertedFile = read (converted, true);
                    this.compareParameters (file, step, originalParameters, dumpParameters (convertedFile));
                    this.compareBytes (file, step + " written twice", converted, write (convertedFile, Optional.of (generation)));
                }

            // 3. Via the sample zones
            if (isAkp)
                this.checkSampleZones (file, (AkpFile) riffFile);
        }
        catch (final IOException | ParseException | RuntimeException ex)
        {
            this.failures.add (file + ": " + ex.getClass ().getSimpleName () + " - " + ex.getMessage ());
        }
    }


    /**
     * Checks that the sample zones of an AKP file are the same after creating a new AKP file from
     * them.
     *
     * @param file The checked file
     * @param akpFile The read AKP file
     * @throws IOException Could not write or read the file
     * @throws ParseException Could not parse the file
     */
    private void checkSampleZones (final File file, final AkpFile akpFile) throws IOException, ParseException
    {
        final List<ISampleZone> sampleZones = akpFile.createGroup (null).getSampleZones ();
        final List<String> expected = dumpSampleZones (sampleZones);

        final AkpFile createdFile = new AkpFile ();
        createdFile.setSampleZones (AkpFile.createKeygroupZones (sampleZones));
        for (final AkpGeneration generation: AkpGeneration.values ())
        {
            final AkpFile readFile = (AkpFile) read (write (createdFile, Optional.of (generation)), true);
            final List<String> actual = dumpSampleZones (readFile.createGroup (null).getSampleZones ());
            final StringBuilder sb = new StringBuilder ();
            int count = 0;
            for (int i = 0; i < Math.max (expected.size (), actual.size ()); i++)
            {
                final String expectedZone = i < expected.size () ? expected.get (i) : "-";
                final String actualZone = i < actual.size () ? actual.get (i) : "-";
                if (expectedZone.equals (actualZone))
                    continue;
                count++;
                if (count <= MAX_DIFFERENCES)
                    sb.append ("\n    expected ").append (expectedZone).append ("\n    found    ").append (actualZone);
            }
            if (count > 0)
                this.failures.add (file + " [zones " + generation.name () + "]: " + count + " zone(s) differ" + sb);
        }
    }


    /**
     * Create a sorted text representation of sample zones with all parameters which are written to
     * an AKP file.
     *
     * @param sampleZones The sample zones
     * @return One text per zone, sorted
     */
    private static List<String> dumpSampleZones (final List<ISampleZone> sampleZones)
    {
        final List<String> result = new ArrayList<> ();
        for (final ISampleZone zone: sampleZones)
        {
            final StringBuilder sb = new StringBuilder ();
            // The creator limits the key range to the lowest note of a key-group
            sb.append (zone.getName ()).append (" key=").append (Math.max (zone.getKeyLow (), AkpKeygroupLocation.LOWEST_NOTE)).append ('-').append (Math.max (zone.getKeyHigh (), AkpKeygroupLocation.LOWEST_NOTE));
            sb.append (" vel=").append (zone.getVelocityLow ()).append ('-').append (zone.getVelocityHigh ());
            sb.append (" tune=").append (format (zone.getTuning ())).append (" pan=").append (format (zone.getPanning ())).append (" gain=").append (format (zone.getGain ()));
            sb.append (" keyTrack=").append (format (zone.getKeyTracking ())).append (" oneShot=").append (zone.isOneShot ()).append (" mute=").append (zone.getExclusiveGroup ());
            sb.append (" loops=").append (zone.getLoops ().size ());
            if (!zone.getLoops ().isEmpty ())
                sb.append (zone.getLoops ().get (0).isLoopUntilRelease () ? " untilRelease" : " inRelease");
            sb.append (" bend=").append (zone.getBendUp ()).append ('/').append (zone.getBendDown ());
            sb.append (" velAmp=").append (format (zone.getAmplitudeVelocityModulator ().getDepth ()));
            sb.append (" ampEnv=").append (dumpEnvelope (zone.getAmplitudeEnvelopeModulator ().getDepth (), zone.getAmplitudeEnvelopeModulator ().getSource ()));
            sb.append (" pitchEnv=").append (dumpEnvelope (zone.getPitchEnvelopeModulator ().getDepth (), zone.getPitchEnvelopeModulator ().getSource ()));
            sb.append (" ampLfo=").append (dumpLfo (zone.getAmplitudeLfoModulator ()));
            sb.append (" pitchLfo=").append (dumpLfo (zone.getPitchLfoModulator ()));
            final Optional<IFilter> optionalFilter = zone.getFilter ();
            if (optionalFilter.isPresent ())
            {
                final IFilter filter = optionalFilter.get ();
                sb.append (" filter=").append (filter.getType ()).append ('/').append (filter.getPoles ()).append (" cutoff=").append (format (filter.getCutoff ())).append (" res=").append (format (filter.getResonance ()));
                sb.append (" filterKeyTrack=").append (format (filter.getCutoffKeyTracking ()));
                sb.append (" filterEnv=").append (dumpEnvelope (filter.getCutoffEnvelopeModulator ().getDepth (), filter.getCutoffEnvelopeModulator ().getSource ()));
                sb.append (" filterLfo=").append (dumpLfo (filter.getCutoffLfoModulator ()));
            }
            result.add (sb.toString ());
        }
        Collections.sort (result);
        return result;
    }


    /**
     * Create a text representation of an envelope modulation.
     *
     * @param depth The depth of the modulation
     * @param envelope The envelope
     * @return The text
     */
    private static String dumpEnvelope (final double depth, final IEnvelope envelope)
    {
        if (depth == 0)
            return "0";
        return format (depth) + "[" + format (envelope.getAttackTime ()) + " " + format (envelope.getHoldLevel ()) + " " + format (envelope.getDecayTime ()) + " " + format (envelope.getSustainLevel ()) + " " + format (envelope.getReleaseTime ()) + " " + format (envelope.getEndLevel ()) + " " + format (envelope.getTimeVelocityTracking ()) + " " + format (envelope.getTimeKeyTracking ()) + "]";
    }


    /**
     * Create a text representation of an LFO modulation.
     *
     * @param modulator The LFO modulator
     * @return The text
     */
    private static String dumpLfo (final ILfoModulator modulator)
    {
        if (modulator.getDepth () == 0)
            return "0";
        final ILfo lfo = modulator.getSource ();
        return format (modulator.getDepth ()) + "[" + lfo.getWaveform () + " " + format (lfo.getRate ()) + " " + format (lfo.getDelay ()) + " " + lfo.isKeySync () + "]";
    }


    /**
     * Format a value with 4 fractional digits.
     *
     * @param value The value
     * @return The formatted value
     */
    private static String format (final double value)
    {
        return String.format (Locale.US, "%.4f", Double.valueOf (value));
    }


    /**
     * Checks that a new AKP file can be written and read again in all generations.
     */
    private void checkNewFile ()
    {
        try
        {
            final AkpFile akpFile = new AkpFile ();
            final Map<String, String> parameters = dumpParameters (akpFile);
            this.compareParameters (new File ("New AKP file"), "default", parameters, dumpParameters (read (write (akpFile, Optional.empty ()), true)));
            for (final AkpGeneration generation: AkpGeneration.values ())
                this.compareParameters (new File ("New AKP file"), generation.name (), parameters, dumpParameters (read (write (akpFile, Optional.of (generation)), true)));
        }
        catch (final IOException | ParseException | RuntimeException ex)
        {
            this.failures.add ("New AKP file: " + ex.getClass ().getSimpleName () + " - " + ex.getMessage ());
        }
    }


    /**
     * Print the result.
     */
    private void printSummary ()
    {
        System.out.println ();
        for (final String failure: this.failures)
            System.out.println ("FAILED: " + failure);
        System.out.println ();
        System.out.println ("Files: " + this.numberOfFiles + ", read errors: " + this.readErrors + ", failures: " + this.failures.size ());
    }


    /**
     * Compare the bytes of two files.
     *
     * @param file The checked file
     * @param step The description of the check
     * @param expected The expected bytes
     * @param actual The written bytes
     */
    private void compareBytes (final File file, final String step, final byte [] expected, final byte [] actual)
    {
        final int length = Math.min (expected.length, actual.length);
        for (int i = 0; i < length; i++)
            if (expected[i] != actual[i])
            {
                this.failures.add (String.format ("%s [%s]: Bytes differ at 0x%X (%s): expected %d, found %d", file, step, Integer.valueOf (i), describeOffset (expected, i), Integer.valueOf (expected[i]), Integer.valueOf (actual[i])));
                return;
            }
        if (expected.length != actual.length)
            this.failures.add (String.format ("%s [%s]: Size differs: expected %d, found %d", file, step, Integer.valueOf (expected.length), Integer.valueOf (actual.length)));
    }


    /**
     * Compare the parameters of two files.
     *
     * @param file The checked file
     * @param step The description of the check
     * @param expected The expected parameters
     * @param actual The parameters of the written file
     */
    private void compareParameters (final File file, final String step, final Map<String, String> expected, final Map<String, String> actual)
    {
        final StringBuilder sb = new StringBuilder ();
        int count = 0;
        for (final Map.Entry<String, String> entry: expected.entrySet ())
        {
            final String actualValue = actual.get (entry.getKey ());
            if (entry.getValue ().equals (actualValue))
                continue;
            count++;
            if (count <= MAX_DIFFERENCES)
                sb.append ("\n    ").append (entry.getKey ()).append (": expected ").append (entry.getValue ()).append (", found ").append (actualValue);
        }
        if (expected.size () != actual.size ())
        {
            count++;
            sb.append ("\n    Number of parameters: expected ").append (expected.size ()).append (", found ").append (actual.size ());
        }
        if (count > 0)
            this.failures.add (file + " [" + step + "]: " + count + " parameter(s) differ" + sb);
    }


    /**
     * Write a file to memory.
     *
     * @param riffFile The AKP or AKM file
     * @param generation The generation to write, empty to keep the read versions
     * @return The bytes of the file
     * @throws IOException Could not write the file
     */
    private static byte [] write (final AbstractRIFFFile riffFile, final Optional<AkpGeneration> generation) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        if (generation.isPresent ())
            ((AkpFile) riffFile).write (out, generation.get ());
        else
            riffFile.write (out);
        return out.toByteArray ();
    }


    /**
     * Read a file from memory.
     *
     * @param data The bytes of the file
     * @param isAkp True to read an AKP file, false to read an AKM file
     * @return The read file
     * @throws IOException Could not read the file
     * @throws ParseException Could not parse the file
     */
    private static AbstractRIFFFile read (final byte [] data, final boolean isAkp) throws IOException, ParseException
    {
        final File tempFile = File.createTempFile ("AkpRoundTrip", isAkp ? ".akp" : ".akm");
        try
        {
            Files.write (tempFile.toPath (), data);
            return isAkp ? new AkpFile (tempFile) : new AkmFile (tempFile);
        }
        finally
        {
            Files.delete (tempFile.toPath ());
        }
    }


    /**
     * Get all parameters of a file by recursively collecting the fields of the AKP classes.
     *
     * @param riffFile The file
     * @return The parameters mapped to their path
     */
    private static Map<String, String> dumpParameters (final Object riffFile)
    {
        final Map<String, String> parameters = new LinkedHashMap<> ();
        dumpObject (riffFile, "", parameters);
        return parameters;
    }


    /**
     * Collect the parameters of an object.
     *
     * @param object The object
     * @param path The path of the object
     * @param parameters Where to add the parameters
     */
    private static void dumpObject (final Object object, final String path, final Map<String, String> parameters)
    {
        Class<?> clazz = object.getClass ();
        while (clazz != null && clazz.getPackageName ().startsWith (AKP_PACKAGE))
        {
            for (final Field field: clazz.getDeclaredFields ())
            {
                if (Modifier.isStatic (field.getModifiers ()) || IGNORED_FIELDS.contains (field.getName ()))
                    continue;
                try
                {
                    field.setAccessible (true);
                    Object value = field.get (object);
                    // The value of AS SAMPLE differs between the versions of the zone chunk
                    if (object instanceof final AkpZone zone && "playback".equals (field.getName ()) && zone.isPlaybackAsSample ())
                        value = "AS SAMPLE";
                    dumpValue (value, path + "/" + field.getName (), parameters);
                }
                catch (final IllegalAccessException ex)
                {
                    parameters.put (path + "/" + field.getName (), ex.getMessage ());
                }
            }
            clazz = clazz.getSuperclass ();
        }
    }


    /**
     * Collect the parameters of a value.
     *
     * @param value The value
     * @param path The path of the value
     * @param parameters Where to add the parameters
     */
    private static void dumpValue (final Object value, final String path, final Map<String, String> parameters)
    {
        switch (value)
        {
            case null -> parameters.put (path, "null");
            case final int [] array -> parameters.put (path, Arrays.toString (array));
            case final Optional<?> optional -> dumpValue (optional.orElse (null), path, parameters);
            case final RawRIFFChunk chunk -> parameters.put (path, Arrays.toString (chunk.getData ()));
            case final Collection<?> collection -> {
                int index = 0;
                for (final Object element: collection)
                    dumpValue (element, path + "[" + index++ + "]", parameters);
                parameters.put (path + ".size", Integer.toString (collection.size ()));
            }
            default -> {
                if (value.getClass ().getPackageName ().startsWith (AKP_PACKAGE))
                    dumpObject (value, path, parameters);
                else
                    parameters.put (path, value.toString ());
            }
        }
    }


    /**
     * Describe the chunk which contains the given offset.
     *
     * @param data The bytes of the file
     * @param offset The offset in the file
     * @return The description, e.g. "kgrp[2]/zone[1] + 0x28"
     */
    private static String describeOffset (final byte [] data, final int offset)
    {
        if (offset < 12)
            return "RIFF header";
        return describeOffset (data, 12, data.length, offset, "");
    }


    /**
     * Describe the chunk which contains the given offset.
     *
     * @param data The bytes of the file
     * @param start The start of the chunk list
     * @param end The end of the chunk list
     * @param offset The offset in the file
     * @param prefix The path of the parent chunk
     * @return The description
     */
    private static String describeOffset (final byte [] data, final int start, final int end, final int offset, final String prefix)
    {
        final Map<String, Integer> counters = new LinkedHashMap<> ();
        int position = start;
        while (position + 8 <= end)
        {
            final String id = new String (data, position, 4).trim ();
            final int size = Byte.toUnsignedInt (data[position + 4]) | Byte.toUnsignedInt (data[position + 5]) << 8 | Byte.toUnsignedInt (data[position + 6]) << 16 | Byte.toUnsignedInt (data[position + 7]) << 24;
            final int index = counters.merge (id, Integer.valueOf (1), Integer::sum).intValue () - 1;
            final String name = prefix + id + "[" + index + "]";
            final int dataStart = position + 8;
            if (offset < dataStart)
                return name + " header";
            if (offset < dataStart + size)
            {
                if ("kgrp".equals (id))
                    return describeOffset (data, dataStart, dataStart + size, offset, name + "/");
                return String.format ("%s + 0x%X", name, Integer.valueOf (offset - dataStart));
            }
            position = dataStart + size + size % 2;
        }
        return prefix + "after last chunk";
    }
}
