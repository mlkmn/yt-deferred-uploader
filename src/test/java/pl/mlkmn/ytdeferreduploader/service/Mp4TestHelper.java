package pl.mlkmn.ytdeferreduploader.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Builds a minimal valid MP4 file with an embedded creation date in the mvhd atom.
 * MP4 uses the QuickTime epoch (1904-01-01T00:00:00Z) for timestamps.
 */
class Mp4TestHelper {

    private static final Instant QUICKTIME_EPOCH = Instant.parse("1904-01-01T00:00:00Z");

    static byte[] createMp4WithCreationDate(Instant creationTime) throws IOException {
        long qtTimestamp = QUICKTIME_EPOCH.until(creationTime, ChronoUnit.SECONDS);

        byte[] mvhd = buildMvhdAtom(qtTimestamp);
        byte[] moov = wrapInAtom("moov", mvhd);
        byte[] ftyp = buildFtypAtom();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ftyp);
        out.write(moov);
        return out.toByteArray();
    }

    private static byte[] buildFtypAtom() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(body);
        dos.writeBytes("isom");    // major brand
        dos.writeInt(0x200);       // minor version
        dos.writeBytes("isom");    // compatible brand
        dos.writeBytes("iso2");    // compatible brand
        dos.writeBytes("mp41");    // compatible brand
        return wrapInAtom("ftyp", body.toByteArray());
    }

    private static byte[] buildMvhdAtom(long qtTimestamp) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(body);
        dos.writeInt(0);                   // version (0) + flags
        dos.writeInt((int) qtTimestamp);    // creation time
        dos.writeInt((int) qtTimestamp);    // modification time
        dos.writeInt(1000);                // timescale
        dos.writeInt(0);                   // duration
        dos.writeInt(0x00010000);           // preferred rate (1.0)
        dos.writeShort(0x0100);            // preferred volume (1.0)
        dos.write(new byte[10]);           // reserved
        // identity matrix (3x3, 4 bytes each = 36 bytes)
        dos.writeInt(0x00010000); dos.writeInt(0); dos.writeInt(0);
        dos.writeInt(0); dos.writeInt(0x00010000); dos.writeInt(0);
        dos.writeInt(0); dos.writeInt(0); dos.writeInt(0x40000000);
        dos.write(new byte[24]);           // pre-defined
        dos.writeInt(2);                   // next track ID
        return wrapInAtom("mvhd", body.toByteArray());
    }

    private static byte[] wrapInAtom(String type, byte[] body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(body.length + 8);     // atom size = header (8) + body
        dos.writeBytes(type);              // atom type (4 chars)
        dos.write(body);
        return out.toByteArray();
    }
}
