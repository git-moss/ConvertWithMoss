// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import java.io.IOException;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.algorithm.PresetRenderer;


/**
 * Plays one note of a multi-sample, so that a preset can be listened to before it is converted.
 * Rendering and playing happen on a thread of their own, and starting a new note stops the one
 * which is currently playing.
 *
 * @author Jürgen Moßgraber
 */
public class AuditionPlayer
{
    /** How many frames are written to the audio device at a time. */
    private static final int FRAMES_PER_WRITE = 2048;

    private Thread           playThread;
    private SourceDataLine   line;
    private volatile boolean isCancelled      = false;


    /**
     * Play one note of the given multi-sample. A note which is currently playing is stopped first.
     *
     * @param source The multi-sample to play
     * @throws IOException Could not read the audio data of the multi-sample
     */
    public synchronized void play (final IMultisampleSource source) throws IOException
    {
        this.stop ();

        final byte [] pcm = PresetRenderer.render (source);
        if (pcm.length == 0)
            return;

        this.isCancelled = false;
        this.playThread = new Thread (() -> this.stream (pcm), "Audition");
        this.playThread.setDaemon (true);
        this.playThread.start ();
    }


    /**
     * Stop the note which is currently playing, if there is one.
     */
    public synchronized void stop ()
    {
        this.isCancelled = true;
        final SourceDataLine currentLine = this.line;
        if (currentLine != null)
            currentLine.stop ();
        final Thread currentThread = this.playThread;
        if (currentThread != null)
            try
            {
                currentThread.join (500);
            }
            catch (final InterruptedException _)
            {
                Thread.currentThread ().interrupt ();
            }
        this.playThread = null;
    }


    /**
     * Test whether a note is currently playing.
     *
     * @return True if it is
     */
    public boolean isPlaying ()
    {
        final Thread currentThread = this.playThread;
        return currentThread != null && currentThread.isAlive ();
    }


    /**
     * Write the rendered signal to the audio device.
     *
     * @param pcm The signal to play
     */
    private void stream (final byte [] pcm)
    {
        final DataLine.Info info = new DataLine.Info (SourceDataLine.class, PresetRenderer.PREVIEW_FORMAT);
        try (final SourceDataLine sourceLine = (SourceDataLine) AudioSystem.getLine (info))
        {
            sourceLine.open (PresetRenderer.PREVIEW_FORMAT);
            sourceLine.start ();
            this.line = sourceLine;

            final int blockSize = FRAMES_PER_WRITE * PresetRenderer.PREVIEW_FORMAT.getFrameSize ();
            for (int offset = 0; offset < pcm.length && !this.isCancelled; offset += blockSize)
                sourceLine.write (pcm, offset, Math.min (blockSize, pcm.length - offset));
            if (!this.isCancelled)
                sourceLine.drain ();
        }
        catch (final LineUnavailableException | IllegalArgumentException _)
        {
            // No audio device: auditioning is simply not available, which needs no error dialog
        }
        finally
        {
            this.line = null;
        }
    }
}
