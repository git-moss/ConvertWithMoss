// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc2000.diskformat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * Boot Sector structure.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMPC2000BootSector
{
    String oemName;
    int    bytesPerSector;
    int    sectorsPerCluster;
    int    reservedSectors;
    int    numberOfFATs;
    int    rootEntries;
    int    totalSectors;
    int    sectorsPerFAT;
    String volumeLabel;
    String fileSystemType;


    /**
     * Read the boot sector.
     * 
     * @param buffer The buffer to read from
     * @return The boot sector
     */
    static AkaiMPC2000BootSector parse (final ByteBuffer buffer)
    {
        final AkaiMPC2000BootSector bs = new AkaiMPC2000BootSector ();
        buffer.order (ByteOrder.LITTLE_ENDIAN);

        // Jump instruction (3 bytes)
        buffer.position (3);

        // OEM Name (8 bytes) at 0x03
        final byte [] oemBytes = new byte [8];
        buffer.get (oemBytes);
        bs.oemName = new String (oemBytes).trim ();

        // BPB starts at 0x0B
        bs.bytesPerSector = buffer.getShort () & 0xFFFF; // 0x0B
        bs.sectorsPerCluster = buffer.get () & 0xFF; // 0x0D
        bs.reservedSectors = buffer.getShort () & 0xFFFF; // 0x0E
        bs.numberOfFATs = buffer.get () & 0xFF; // 0x10
        bs.rootEntries = buffer.getShort () & 0xFFFF; // 0x11

        final int totalSectors16 = buffer.getShort () & 0xFFFF; // 0x13
        @SuppressWarnings("unused")
        final byte mediaDescriptor = buffer.get (); // 0x15
        bs.sectorsPerFAT = buffer.getShort () & 0xFFFF; // 0x16

        @SuppressWarnings("unused")
        final int sectorsPerTrack = buffer.getShort () & 0xFFFF; // 0x18
        @SuppressWarnings("unused")
        final int numberOfHeads = buffer.getShort () & 0xFFFF; // 0x1A
        @SuppressWarnings("unused")
        final int hiddenSectors = buffer.getInt (); // 0x1C
        final int totalSectors32 = buffer.getInt (); // 0x20

        bs.totalSectors = totalSectors16 != 0 ? totalSectors16 : totalSectors32;

        // Extended Boot Record
        buffer.position (0x24);
        @SuppressWarnings("unused")
        final int driveNumber = buffer.get () & 0xFF;
        buffer.get (); // Reserved
        @SuppressWarnings("unused")
        final int bootSignature = buffer.get () & 0xFF; // 0x26
        @SuppressWarnings("unused")
        final int volumeSerial = buffer.getInt (); // 0x27

        // Volume Label at 0x2B (11 bytes)
        buffer.position (0x2B);
        final byte [] volLabel = new byte [11];
        buffer.get (volLabel);
        bs.volumeLabel = new String (volLabel).trim ();

        // File System Type at 0x36 (8 bytes)
        final byte [] fsType = new byte [8];
        buffer.get (fsType);
        bs.fileSystemType = new String (fsType).trim ();

        return bs;
    }


    /** Dump the name and type. */
    @Override
    public String toString ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("=== MPC2000 Disk Info ===\n");
        sb.append ("OEM Name: ").append (this.oemName).append ("\n");
        sb.append ("Volume Label: ").append ((this.volumeLabel.isEmpty () ? "(none)" : this.volumeLabel)).append ("\n");
        sb.append ("File System: ").append (this.fileSystemType).append ("\n");
        sb.append ("Bytes per sector: ").append (this.bytesPerSector).append ("\n");
        sb.append ("Sectors per cluster: ").append (this.sectorsPerCluster).append ("\n");
        sb.append ("Reserved sectors: ").append (this.reservedSectors).append ("\n");
        sb.append ("Number of FATs: ").append (this.numberOfFATs).append ("\n");
        sb.append ("Root entries: ").append (this.rootEntries).append ("\n");
        sb.append ("Sectors per FAT: ").append (this.sectorsPerFAT).append ("\n");
        sb.append ("Total sectors: ").append (this.totalSectors).append ("\n");
        sb.append ("Cluster size: ").append (this.sectorsPerCluster * this.bytesPerSector).append (" bytes\n");
        return sb.toString ();
    }
}