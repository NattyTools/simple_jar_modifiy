package org.nattytools.jarmodifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.*;

public abstract class JarClassWriter {

    public abstract boolean visitClass(final InputStream input, final OutputStream output,
                                      String jarFileName,String zipEntryName) throws IOException ;

    
    private ZipEntry nextEntry(final ZipInputStream input,
                               final String location) throws IOException {
        try {
            return input.getNextEntry();
        } catch (final IOException e) {
            throw modificationError(location, e);
        }
    }

    private IOException modificationError(final String name,
                                        final Exception cause) {
        final IOException ex = new IOException(
                String.format("Error while modifying %s.", name));
        ex.initCause(cause);
        return ex;
    }




    public int modifyZip(final InputStream input,
                         final OutputStream output, String jarFileName) throws IOException {
        if(input==null)
            return -1;
        final ZipInputStream zipin = new ZipInputStream(input);
        final ZipOutputStream zipout = new ZipOutputStream(output);
        ZipEntry entry;
        int count = 0;
        while ((entry = nextEntry(zipin, jarFileName)) != null) {
            final String entryName = entry.getName();
            //TODO put filters
            /*if (signatureRemover.removeEntry(entryName)) {
                continue;
            }*/

            final ZipEntry newEntry = new ZipEntry(entryName);
            newEntry.setMethod(entry.getMethod());
            switch (entry.getMethod()) {
                case ZipEntry.DEFLATED:
                    zipout.putNextEntry(newEntry);
                    count += filterOrModify(zipin, zipout, jarFileName, entryName);
                    break;
                case ZipEntry.STORED:
                    // Uncompressed entries must be processed in-memory to calculate
                    // mandatory entry size and CRC
                    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    count += filterOrModify(zipin, buffer, jarFileName, entryName);
                    final byte[] bytes = buffer.toByteArray();
                    newEntry.setSize(bytes.length);
                    newEntry.setCompressedSize(bytes.length);
                    newEntry.setCrc(crc(bytes));
                    zipout.putNextEntry(newEntry);
                    zipout.write(bytes);
                    break;
                default:
                    throw new AssertionError(entry.getMethod());
            }
            zipout.closeEntry();
        }
        zipout.finish();
        return count;
    }
    private static long crc(final byte[] data) {
        final CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    public int modifyAll(final InputStream input, final OutputStream output,String jarFileName,String entryName
                             ) throws IOException {
        final ContentTypeDetector detector;
        try {
            detector = new ContentTypeDetector(input);
        } catch (final IOException e) {
            throw modificationError(jarFileName+"@"+entryName, e);
        }

        switch (detector.getType()) {

            case ContentTypeDetector.ZIPFILE:
                return modifyZip(detector.getInputStream(), output, jarFileName);
            case ContentTypeDetector.GZFILE:
                return modifyGzip(detector.getInputStream(), output, jarFileName);
            case ContentTypeDetector.PACK200FILE:
                return modifyPack200(detector.getInputStream(), output, jarFileName);
            case ContentTypeDetector.CLASSFILE:
                if(visitClass(detector.getInputStream(), output, jarFileName,entryName))
                    return 1;
                //if visited class else copy normally
            default:
                copy(detector.getInputStream(), output, jarFileName);
                return 0;
        }
    }


    private int modifyGzip(final InputStream input,
                               final OutputStream output, final String jarFileName) throws IOException {
        final GZIPInputStream gzipInputStream;
        try {
            gzipInputStream = new GZIPInputStream(input);
        } catch (final IOException e) {
            throw modificationError(jarFileName, e);
        }
        final GZIPOutputStream gzout = new GZIPOutputStream(output);
        final int count = modifyAll(gzipInputStream, gzout,jarFileName, "");
        gzout.finish();
        return count;
    }


    private int modifyPack200(final InputStream input,
                                  final OutputStream output, final String name) throws IOException {
        final InputStream unpackedInput;
        try {
            unpackedInput = Pack200Streams.unpack(input);
        } catch (final IOException e) {
            throw modificationError(name, e);
        }
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final int count = modifyAll(unpackedInput, buffer, name,"");
        Pack200Streams.pack(buffer.toByteArray(), output);
        return count;
    }


    private int filterOrModify(final InputStream in, final OutputStream out,
                               final String name, final String entryName) throws IOException{
        //TODO filter

        return modifyAll(in, out, name ,entryName);
    }






    private void copy(final InputStream input, final OutputStream output,
                      final String name) throws IOException {
        final byte[] buffer = new byte[1024];
        int len;
        while ((len = read(input, buffer, name)) != -1) {
            output.write(buffer, 0, len);
        }
    }

    private int read(final InputStream input, final byte[] buffer,
                     final String name) throws IOException {
        try {
            return input.read(buffer);
        } catch (final IOException e) {
            throw modificationError(name, e);
        }
    }

}
