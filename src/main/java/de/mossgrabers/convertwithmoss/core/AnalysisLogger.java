// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.IModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;


/**
 * Logs the details of a detected source during an analysis run: the mapping of its zones, the
 * sample format, loops, envelopes, LFOs and the filter. Only attributes which the source actually
 * uses are logged, so searching the log finds the sources which use a specific feature - e.g. a
 * test file for development. The details are logged as they were read from the source, before any
 * processing is applied.
 * <p>
 * The attribute names are deliberately not translated, so that a search pattern does not depend on
 * the language of the user interface.
 *
 * @author Jürgen Moßgraber
 */
public class AnalysisLogger
{
    private static final String    TAG_TUNING                  = ", tuning ";
    private static final String    IDS_NOTIFY_ANALYSIS_DETAILS = "IDS_NOTIFY_ANALYSIS_DETAILS";

    private static final String [] NOTE_NAMES                  =
    {
        "C",
        "C#",
        "D",
        "D#",
        "E",
        "F",
        "F#",
        "G",
        "G#",
        "A",
        "A#",
        "B"
    };


    /**
     * Private due to utility class.
     */
    private AnalysisLogger ()
    {
        // Intentionally empty
    }


    /**
     * Log the details of a multi-sample source.
     *
     * @param notifier Where to log to
     * @param multisampleSource The multi-sample source to log
     */
    public static void log (final INotifier notifier, final IMultisampleSource multisampleSource)
    {
        notifier.log (IDS_NOTIFY_ANALYSIS_DETAILS, multisampleSource.getName ());

        final StringBuilder sb = new StringBuilder ();
        appendMultisample (sb, multisampleSource, "  ");
        notifier.logText (sb.toString ());
    }


    /**
     * Log the details of a performance source with all of its instruments.
     *
     * @param notifier Where to log to
     * @param performanceSource The performance source to log
     */
    public static void log (final INotifier notifier, final IPerformanceSource performanceSource)
    {
        notifier.log (IDS_NOTIFY_ANALYSIS_DETAILS, performanceSource.getName ());

        final StringBuilder sb = new StringBuilder ();
        final List<IInstrumentSource> instrumentSources = performanceSource.getInstruments ();
        for (int i = 0; i < instrumentSources.size (); i++)
        {
            final IInstrumentSource instrumentSource = instrumentSources.get (i);
            final IMultisampleSource multisampleSource = instrumentSource.getMultisampleSource ();

            sb.append ("  Instrument ").append (i + 1).append (" '").append (multisampleSource.getName ()).append ("': MIDI channel ");
            final int midiChannel = instrumentSource.getMidiChannel ();
            if (midiChannel >= 0 && midiChannel <= 15)
                sb.append (midiChannel + 1);
            else if (midiChannel == IInstrumentSource.MIDI_CHANNEL_OMNI)
                sb.append ("omni");
            else
                sb.append ("off");
            final int clipKeyLow = instrumentSource.getClipKeyLow ();
            final int clipKeyHigh = instrumentSource.getClipKeyHigh ();
            if (clipKeyLow > 0 || clipKeyHigh < 127)
                sb.append (", keys ").append (formatNote (clipKeyLow)).append ("..").append (formatNote (clipKeyHigh));
            sb.append ('\n');

            appendMultisample (sb, multisampleSource, "    ");
        }
        notifier.logText (sb.toString ());
    }


    private static void appendMultisample (final StringBuilder sb, final IMultisampleSource multisampleSource, final String indent)
    {
        final File sourceFile = multisampleSource.getSourceFile ();
        if (sourceFile != null)
            sb.append (indent).append ("File: ").append (sourceFile.getAbsolutePath ()).append ('\n');

        appendMetadata (sb, multisampleSource.getMetadata (), indent);

        final int polyphony = multisampleSource.getPolyphony ();
        if (polyphony > 0)
            sb.append (indent).append ("Polyphony: ").append (polyphony).append (polyphony == 1 ? " voice" : " voices").append ('\n');
        final double portamentoTime = multisampleSource.getPortamentoTime ();
        if (portamentoTime > 0)
            sb.append (indent).append ("Portamento: ").append (formatSeconds (portamentoTime)).append ('\n');

        final List<IGroup> groups = multisampleSource.getGroups ();
        for (int i = 0; i < groups.size (); i++)
            appendGroup (sb, groups.get (i), i + 1, indent);
    }


    private static void appendMetadata (final StringBuilder sb, final IMetadata metadata, final String indent)
    {
        final List<String> tokens = new ArrayList<> ();

        final String category = metadata.getCategory ();
        if (category != null && !category.isBlank ())
            tokens.add ("category '" + category + "'");
        final String creator = metadata.getCreator ();
        if (creator != null && !creator.isBlank ())
            tokens.add ("creator '" + creator + "'");
        final String [] keywords = metadata.getKeywords ();
        if (keywords != null && keywords.length > 0)
        {
            final StringBuilder keywordText = new StringBuilder ("keywords ");
            for (int i = 0; i < keywords.length; i++)
            {
                if (i > 0)
                    keywordText.append (", ");
                keywordText.append ('\'').append (keywords[i]).append ('\'');
            }
            tokens.add (keywordText.toString ());
        }
        final String description = metadata.getDescription ();
        if (description != null && !description.isBlank ())
            tokens.add ("description (" + description.length () + " characters)");

        if (!tokens.isEmpty ())
            sb.append (indent).append ("Metadata: ").append (String.join (", ", tokens)).append ('\n');
    }


    private static void appendGroup (final StringBuilder sb, final IGroup group, final int groupNumber, final String indent)
    {
        final List<ISampleZone> zones = group.getSampleZones ();

        sb.append (indent).append ("Group ").append (groupNumber);
        final String name = group.getName ();
        if (name != null && !name.isBlank ())
            sb.append (" '").append (name).append ('\'');
        sb.append (": ").append (zones.size ()).append (zones.size () == 1 ? " zone" : " zones");

        final TriggerType trigger = group.getTrigger ();
        if (trigger != null && trigger != TriggerType.ATTACK)
            sb.append (", trigger ").append (trigger.name ().toLowerCase (Locale.US));
        if (group.getGain () != 0)
            sb.append (", gain ").append (formatDecibels (group.getGain ()));
        if (group.getPanning () != 0)
            sb.append (", panning ").append (formatSignedPercent (group.getPanning ()));
        if (group.getTuning () != 0)
            sb.append (TAG_TUNING).append (formatSemitones (group.getTuning ()));
        sb.append ('\n');

        final String zoneIndent = indent + "  ";
        for (final ISampleZone zone: zones)
            appendZone (sb, zone, zoneIndent);
    }


    private static void appendZone (final StringBuilder sb, final ISampleZone zone, final String indent)
    {
        sb.append (indent).append ("Zone '").append (zone.getName ()).append ("': ").append (formatZoneMapping (zone));

        if (zone.getGain () != 0)
            sb.append (", gain ").append (formatDecibels (zone.getGain ()));
        if (zone.getPanning () != 0)
            sb.append (", panning ").append (formatSignedPercent (zone.getPanning ()));
        if (zone.getTuning () != 0)
            sb.append (TAG_TUNING).append (formatSemitones (zone.getTuning ()));
        sb.append ('\n');

        final String featureIndent = indent + "  ";
        appendZoneFeatures (sb, zone, featureIndent);
    }


    private static String formatZoneMapping (final ISampleZone zone)
    {
        final List<String> tokens = new ArrayList<> ();

        final int keyLow = Math.max (0, zone.getKeyLow ());
        final int keyHigh = zone.getKeyHigh () < 0 ? 127 : zone.getKeyHigh ();
        tokens.add ("keys " + formatNote (keyLow) + ".." + formatNote (keyHigh));
        final int keyRoot = zone.getKeyRoot ();
        tokens.add ("root " + (keyRoot < 0 ? "-" : formatNote (keyRoot)));
        tokens.add ("velocity " + zone.getVelocityLow () + ".." + zone.getVelocityHigh ());

        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
            tokens.add ("no audio");
        else
            try
            {
                final IAudioMetadata audioMetadata = sampleData.get ().getAudioMetadata ();
                tokens.add (audioMetadata.getSampleRate () + " Hz");
                tokens.add (audioMetadata.getBitResolution () + " bits");
                final int channels = audioMetadata.getChannels ();
                switch (channels)
                {
                    case 1 -> tokens.add ("mono");
                    case 2 -> tokens.add ("stereo");
                    default -> tokens.add (channels + " channels");
                }
                tokens.add (audioMetadata.getNumberOfSamples () + " frames");
            }
            catch (final IOException _)
            {
                tokens.add ("audio not readable");
            }

        return String.join (", ", tokens);
    }


    private static void appendZoneFeatures (final StringBuilder sb, final ISampleZone zone, final String indent)
    {
        final int start = zone.getStart ();
        final int stop = zone.getStop ();
        if (start > 0)
            sb.append (indent).append ("Play range: ").append (start).append ("..").append (stop).append (" frames\n");

        if (zone.getNoteCrossfadeLow () > 0 || zone.getNoteCrossfadeHigh () > 0)
            sb.append (indent).append ("Note cross-fade: low ").append (zone.getNoteCrossfadeLow ()).append (", high ").append (zone.getNoteCrossfadeHigh ()).append ('\n');
        if (zone.getVelocityCrossfadeLow () > 0 || zone.getVelocityCrossfadeHigh () > 0)
            sb.append (indent).append ("Velocity cross-fade: low ").append (zone.getVelocityCrossfadeLow ()).append (", high ").append (zone.getVelocityCrossfadeHigh ()).append ('\n');

        if (zone.getPlayLogic () == PlayLogic.ROUND_ROBIN)
        {
            sb.append (indent).append ("Round robin");
            if (zone.getSequencePosition () > 0)
                sb.append (" #").append (zone.getSequencePosition ());
            sb.append ('\n');
        }

        final TriggerType trigger = zone.getTrigger ();
        if (trigger != null && trigger != TriggerType.ATTACK)
            sb.append (indent).append ("Trigger: ").append (trigger.name ().toLowerCase (Locale.US)).append ('\n');
        if (zone.isOneShot ())
            sb.append (indent).append ("One-shot\n");
        if (zone.isReversed ())
            sb.append (indent).append ("Reversed\n");
        if (zone.getExclusiveGroup () != 0)
            sb.append (indent).append ("Exclusive group: ").append (zone.getExclusiveGroup ()).append ('\n');

        final double keyTracking = zone.getKeyTracking ();
        if (keyTracking != 1)
            sb.append (indent).append ("Key tracking: ").append (formatPercent (keyTracking)).append (keyTracking == 0 ? " (fixed pitch)" : "").append ('\n');
        if (zone.getAmplitudeKeyTracking () != 0)
            sb.append (indent).append ("Amplitude key tracking: ").append (String.format (Locale.US, "%+.2f dB/key", Double.valueOf (zone.getAmplitudeKeyTracking ()))).append ('\n');

        if (zone.getBendUp () != 200 || zone.getBendDown () != -200)
            sb.append (indent).append ("Pitch bend: up ").append (zone.getBendUp ()).append (" cents, down ").append (zone.getBendDown ()).append (" cents\n");

        for (final ISampleLoop loop: zone.getLoops ())
            appendLoop (sb, loop, indent);

        final IModulator amplitudeVelocityModulator = zone.getAmplitudeVelocityModulator ();
        if (amplitudeVelocityModulator.getDepth () != 1 || amplitudeVelocityModulator.getCurve () != 0)
            appendModulator (sb, "Amplitude velocity", amplitudeVelocityModulator, formatSignedPercent (amplitudeVelocityModulator.getDepth ()), indent);

        final IEnvelopeModulator amplitudeEnvelopeModulator = zone.getAmplitudeEnvelopeModulator ();
        if (amplitudeEnvelopeModulator.getSource ().isSet ())
            appendEnvelope (sb, "Amplitude envelope", amplitudeEnvelopeModulator, amplitudeEnvelopeModulator.getDepth () == 1 ? null : formatPercent (amplitudeEnvelopeModulator.getDepth ()), indent);

        final ILfoModulator amplitudeLfoModulator = zone.getAmplitudeLfoModulator ();
        if (amplitudeLfoModulator.getDepth () != 0 && amplitudeLfoModulator.getSource ().isSet ())
            appendLfo (sb, "Tremolo", amplitudeLfoModulator, formatLfoVolumeDepth (amplitudeLfoModulator.getDepth ()), indent);

        final IEnvelopeModulator pitchEnvelopeModulator = zone.getPitchEnvelopeModulator ();
        if (pitchEnvelopeModulator.getDepth () != 0 && pitchEnvelopeModulator.getSource ().isSet ())
            appendEnvelope (sb, "Pitch envelope", pitchEnvelopeModulator, formatCents (pitchEnvelopeModulator.getDepth ()), indent);

        final ILfoModulator pitchLfoModulator = zone.getPitchLfoModulator ();
        if (pitchLfoModulator.getDepth () != 0 && pitchLfoModulator.getSource ().isSet ())
            appendLfo (sb, "Vibrato", pitchLfoModulator, formatCents (pitchLfoModulator.getDepth ()), indent);

        final Optional<IFilter> filter = zone.getFilter ();
        if (filter.isPresent ())
            appendFilter (sb, filter.get (), indent);
    }


    private static void appendLoop (final StringBuilder sb, final ISampleLoop loop, final String indent)
    {
        sb.append (indent).append ("Loop: ").append (loop.getType ().name ().toLowerCase (Locale.US)).append (' ').append (loop.getStart ()).append ("..").append (loop.getEnd ());
        if (loop.isLoopUntilRelease ())
            sb.append (", until release");
        final int crossfadeInSamples = loop.getCrossfadeInSamples ();
        if (crossfadeInSamples > 0)
            sb.append (", cross-fade ").append (crossfadeInSamples).append (" frames");
        if (loop.getTuning () != 0)
            sb.append (TAG_TUNING).append (formatSemitones (loop.getTuning ()));
        sb.append ('\n');
    }


    private static void appendFilter (final StringBuilder sb, final IFilter filter, final String indent)
    {
        sb.append (indent).append ("Filter: ").append (filter.getType ().name ().toLowerCase (Locale.US).replace ('_', '-'));
        if (filter.getPoles () > 0)
            sb.append (", ").append (filter.getPoles ()).append (filter.getPoles () == 1 ? " pole" : " poles");
        sb.append (", cutoff ").append (String.format (Locale.US, "%.0f Hz", Double.valueOf (filter.getCutoff ())));
        if (filter.getResonance () != 0)
            sb.append (", resonance ").append (String.format (Locale.US, "%.1f dB", Double.valueOf (filter.getResonance () * IFilter.MAX_RESONANCE)));
        if (filter.getCutoffKeyTracking () != 0)
            sb.append (", key tracking ").append (formatSignedPercent (filter.getCutoffKeyTracking ()));
        sb.append ('\n');

        final String modulatorIndent = indent + "  ";

        final IModulator cutoffVelocityModulator = filter.getCutoffVelocityModulator ();
        if (cutoffVelocityModulator.getDepth () != 0 || cutoffVelocityModulator.getCurve () != 0)
            appendModulator (sb, "Filter velocity", cutoffVelocityModulator, formatFilterVelocityCents (cutoffVelocityModulator.getDepth ()), modulatorIndent);

        final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
        if (cutoffEnvelopeModulator.getDepth () != 0 && cutoffEnvelopeModulator.getSource ().isSet ())
            appendEnvelope (sb, "Filter envelope", cutoffEnvelopeModulator, formatCents (cutoffEnvelopeModulator.getDepth ()), modulatorIndent);

        final ILfoModulator cutoffLfoModulator = filter.getCutoffLfoModulator ();
        if (cutoffLfoModulator.getDepth () != 0 && cutoffLfoModulator.getSource ().isSet ())
            appendLfo (sb, "Filter LFO", cutoffLfoModulator, formatCents (cutoffLfoModulator.getDepth ()), modulatorIndent);
    }


    private static void appendModulator (final StringBuilder sb, final String label, final IModulator modulator, final String depthText, final String indent)
    {
        sb.append (indent).append (label).append (": depth ").append (depthText);
        if (modulator.getCurve () != 0)
            sb.append (", curve ").append (String.format (Locale.US, "%+.2f", Double.valueOf (modulator.getCurve ())));
        sb.append ('\n');
    }


    private static void appendEnvelope (final StringBuilder sb, final String label, final IEnvelopeModulator modulator, final String depthText, final String indent)
    {
        final List<String> tokens = new ArrayList<> ();
        if (depthText != null)
            tokens.add ("depth " + depthText);

        final IEnvelope envelope = modulator.getSource ();
        if (envelope.getDelayTime () >= 0)
            tokens.add ("delay " + formatSeconds (envelope.getDelayTime ()));
        if (envelope.getStartLevel () >= 0)
            tokens.add ("start level " + formatPercent (envelope.getStartLevel ()));
        if (envelope.getAttackTime () >= 0)
            tokens.add ("attack " + formatSeconds (envelope.getAttackTime ()) + formatSlope (envelope.getAttackSlope ()));
        if (envelope.getHoldLevel () >= 0)
            tokens.add ("hold level " + formatPercent (envelope.getHoldLevel ()));
        if (envelope.getHoldTime () >= 0)
            tokens.add ("hold " + formatSeconds (envelope.getHoldTime ()));
        if (envelope.getDecayTime () >= 0)
            tokens.add ("decay " + formatSeconds (envelope.getDecayTime ()) + formatSlope (envelope.getDecaySlope ()));
        if (envelope.getSustainLevel () >= 0)
            tokens.add ("sustain " + formatPercent (envelope.getSustainLevel ()));
        if (envelope.getReleaseTime () >= 0)
            tokens.add ("release " + formatSeconds (envelope.getReleaseTime ()) + formatSlope (envelope.getReleaseSlope ()));
        if (envelope.getEndLevel () >= 0)
            tokens.add ("end level " + formatPercent (envelope.getEndLevel ()));
        if (envelope.getTimeKeyTracking () != 0)
            tokens.add ("time key tracking " + formatSignedPercent (envelope.getTimeKeyTracking ()));
        if (envelope.getTimeVelocityTracking () != 0)
            tokens.add ("time velocity tracking " + formatSignedPercent (envelope.getTimeVelocityTracking ()));

        sb.append (indent).append (label).append (": ").append (String.join (", ", tokens)).append ('\n');
    }


    private static void appendLfo (final StringBuilder sb, final String label, final ILfoModulator modulator, final String depthText, final String indent)
    {
        final List<String> tokens = new ArrayList<> ();

        final ILfo lfo = modulator.getSource ();
        if (lfo.getRate () >= 0)
            tokens.add (String.format (Locale.US, "%.2f Hz", Double.valueOf (lfo.getRate ())));
        tokens.add ("depth " + depthText);
        if (lfo.getDelay () >= 0)
            tokens.add ("delay " + formatSeconds (lfo.getDelay ()));
        if (lfo.getFadeIn () >= 0)
            tokens.add ("fade-in " + formatSeconds (lfo.getFadeIn ()));
        if (lfo.getStartPhase () > 0)
            tokens.add ("start phase " + formatPercent (lfo.getStartPhase ()));
        if (lfo.isKeySync ())
            tokens.add ("key sync");

        sb.append (indent).append (label).append (": ").append (String.join (", ", tokens)).append ('\n');
    }


    private static String formatNote (final int midiNote)
    {
        return NOTE_NAMES[midiNote % 12] + (midiNote / 12 - 2) + "/" + midiNote;
    }


    private static String formatSeconds (final double seconds)
    {
        return String.format (Locale.US, "%.3f s", Double.valueOf (seconds));
    }


    private static String formatDecibels (final double decibels)
    {
        return String.format (Locale.US, "%+.1f dB", Double.valueOf (decibels));
    }


    private static String formatSemitones (final double semitones)
    {
        return String.format (Locale.US, "%+.2f semi-tones", Double.valueOf (semitones));
    }


    private static String formatPercent (final double value)
    {
        return Math.round (value * 100) + " %";
    }


    private static String formatSignedPercent (final double value)
    {
        return String.format (Locale.US, "%+d %%", Long.valueOf (Math.round (value * 100)));
    }


    private static String formatCents (final double depth)
    {
        return String.format (Locale.US, "%+d cents", Long.valueOf (Math.round (depth * IEnvelope.MAX_ENVELOPE_DEPTH)));
    }


    private static String formatFilterVelocityCents (final double depth)
    {
        // The full scale of the cutoff velocity modulator are 8 octaves, see e.g. its SFZ
        // fil_veltrack round-trip
        return String.format (Locale.US, "%+d cents", Long.valueOf (Math.round (depth * 9600)));
    }


    private static String formatLfoVolumeDepth (final double depth)
    {
        return String.format (Locale.US, "%.1f dB", Double.valueOf (Math.abs (depth) * ILfoModulator.MAX_VOLUME_DEPTH));
    }


    private static String formatSlope (final double slope)
    {
        return slope == 0 ? "" : String.format (Locale.US, " (slope %+.2f)", Double.valueOf (slope));
    }
}
