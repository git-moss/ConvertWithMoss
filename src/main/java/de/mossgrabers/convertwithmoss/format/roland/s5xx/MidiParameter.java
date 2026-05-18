package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


public class MidiParameter extends Struct
{
    // S-50 only stuff
    private byte          txChannel;
    private byte          txProgramChange;
    private byte          txBender;
    private byte          txModulation;
    private byte          txHold;
    private byte          txAfterTouch;
    private byte          txVolume;
    private final byte [] rxProgramNumber       = new byte [8];
    private final byte [] txProgramNumber       = new byte [8];
    private byte          txBendRange;

    // S-550 stuff
    private final byte [] rxChannel             = new byte [8];
    private final byte [] rxProgramChange       = new byte [8];
    private final byte [] rxBender              = new byte [8];
    private final byte [] rxModulation          = new byte [8];
    private final byte [] rxHold                = new byte [8];
    private final byte [] rxAfterTouch          = new byte [8];
    private final byte [] rxVolume              = new byte [8];
    private final byte [] rxBendRange           = new byte [8];
    private byte          systemExclusive;
    private byte          deviceId;
    private final byte [] rxProgramChangeNumber = new byte [32];


    public byte [] getRxChannel ()
    {
        return getArray (this.rxChannel);
    }


    public void setRxChannel (byte [] rxChannel)
    {
        setArray (rxChannel, this.rxChannel);
    }


    public byte [] getRxProgramChange ()
    {
        return getArray (this.rxProgramChange);
    }


    public void setRxProgramChange (byte [] rxProgramChange)
    {
        setArray (rxProgramChange, this.rxProgramChange);
    }


    public byte [] getRxBender ()
    {
        return getArray (this.rxBender);
    }


    public void setRxBender (byte [] rxBender)
    {
        setArray (rxBender, this.rxBender);
    }


    public byte [] getRxModulation ()
    {
        return getArray (this.rxModulation);
    }


    public void setRxModulation (byte [] rxModulation)
    {
        setArray (rxModulation, this.rxModulation);
    }


    public byte [] getRxHold ()
    {
        return getArray (this.rxHold);
    }


    public void setRxHold (byte [] rxHold)
    {
        setArray (rxHold, this.rxHold);
    }


    public byte [] getRxAfterTouch ()
    {
        return getArray (this.rxAfterTouch);
    }


    public void setRxAfterTouch (byte [] rxAfterTouch)
    {
        setArray (rxAfterTouch, this.rxAfterTouch);
    }


    public byte [] getRxVolume ()
    {
        return getArray (this.rxVolume);
    }


    public void setRxVolume (byte [] rxVolume)
    {
        setArray (rxVolume, this.rxVolume);
    }


    public byte [] getRxBendRange ()
    {
        return getArray (this.rxBendRange);
    }


    public void setRxBendRange (byte [] rxBendRange)
    {
        setArray (rxBendRange, this.rxBendRange);
    }


    public byte getSystemExclusive ()
    {
        return this.systemExclusive;
    }


    public void setSystemExclusive (byte systemExclusive)
    {
        this.systemExclusive = systemExclusive;
    }


    public byte getDeviceId ()
    {
        return this.deviceId;
    }


    public void setDeviceId (byte deviceId)
    {
        this.deviceId = deviceId;
    }


    public byte [] getRxProgramChangeNumber ()
    {
        return getArray (this.rxProgramChangeNumber);
    }


    public void setRxProgramChangeNumber (byte [] rxProgramChangeNumber)
    {
        setArray (rxProgramChangeNumber, this.rxProgramChangeNumber);
    }


    @Override
    public void read (WordInputStream in) throws IOException
    {
        // S-50 leftover
        in.skip (8);
        this.txChannel = in.read8bit ();
        this.txProgramChange = in.read8bit ();
        this.txBender = in.read8bit ();
        this.txModulation = in.read8bit ();
        this.txHold = in.read8bit ();
        this.txAfterTouch = in.read8bit ();
        this.txVolume = in.read8bit ();
        in.skip (1);
        in.read (this.rxProgramNumber);
        in.read (this.txProgramNumber);

        // S-550 stuff
        in.read (this.rxChannel);
        in.read (this.rxProgramChange);
        in.read (this.rxBender);
        in.read (this.rxModulation);
        in.read (this.rxHold);
        in.read (this.rxAfterTouch);
        in.read (this.rxVolume);
        in.read (this.rxBendRange);
        this.txBendRange = in.read8bit ();
        this.systemExclusive = in.read8bit ();
        this.deviceId = in.read8bit ();
        in.skip (1);
        in.read (this.rxProgramChangeNumber);
        in.skip (124);
    }


    @Override
    public void write (WordOutputStream out) throws IOException
    {
        out.write ((byte) 0, 8);
        out.write8bit (this.txChannel);
        out.write8bit (this.txProgramChange);
        out.write8bit (this.txBender);
        out.write8bit (this.txModulation);
        out.write8bit (this.txHold);
        out.write8bit (this.txAfterTouch);
        out.write8bit (this.txVolume);
        out.write8bit ((byte) 0);
        out.write (this.rxProgramNumber);
        out.write (this.txProgramNumber);

        out.write (this.rxChannel);
        out.write (this.rxProgramChange);
        out.write (this.rxBender);
        out.write (this.rxModulation);
        out.write (this.rxHold);
        out.write (this.rxAfterTouch);
        out.write (this.rxVolume);
        out.write (this.rxBendRange);
        out.write8bit (this.txBendRange);
        out.write8bit (this.systemExclusive);
        out.write8bit (this.deviceId);
        out.write8bit ((byte) 0);
        out.write (this.rxProgramChangeNumber);
        out.write ((byte) 0, 124);
    }
}
