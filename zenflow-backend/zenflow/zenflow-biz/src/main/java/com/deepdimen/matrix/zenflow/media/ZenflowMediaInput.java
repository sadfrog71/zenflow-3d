package com.deepdimen.matrix.zenflow.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Bounded disk spool: no whole-file byte[] and no user filename is ever used as a path. */
public final class ZenflowMediaInput implements AutoCloseable {
    private final Path file;
    private final long bytes;
    private final String sha256;
    private ZenflowMediaInput(Path file,long bytes,String sha256){this.file=file;this.bytes=bytes;this.sha256=sha256;}
    public Path file(){return file;}
    public long bytes(){return bytes;}
    public String sha256(){return sha256;}
    public static ZenflowMediaInput receive(InputStream input,Path privateDirectory,long declaredBytes,long maxBytes,String expectedHash)throws IOException{
        if(maxBytes<1||maxBytes>64L*1024*1024||declaredBytes<1||declaredBytes>maxBytes||expectedHash==null||!expectedHash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("INVALID_MEDIA_DECLARATION");
        if(!Files.isDirectory(privateDirectory)||Files.isSymbolicLink(privateDirectory))throw new IOException("PRIVATE_MEDIA_SPOOL_REQUIRED");
        if(Files.getPosixFilePermissions(privateDirectory).stream().anyMatch(permission->permission.name().startsWith("GROUP_")||permission.name().startsWith("OTHERS_")))throw new IOException("PRIVATE_MEDIA_SPOOL_REQUIRED");
        Path file=Files.createTempFile(privateDirectory,"invelle-upload-",".bin",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        boolean success=false;
        try{
            MessageDigest hash;
            try{hash=MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
            long total=0;byte[] buffer=new byte[16384];
            try(var output=Files.newOutputStream(file)){
                while(true){
                    // Read at most one extra byte, so an endless upload stops at the declared limit.
                    int count=input.read(buffer,0,(int)Math.min(buffer.length,declaredBytes-total+1));
                    if(count<0)break;if(count==0)throw new IOException("MEDIA_STREAM_STALLED");
                    total+=count;if(total>declaredBytes||total>maxBytes)throw new IOException("MEDIA_SIZE_EXCEEDED");
                    hash.update(buffer,0,count);output.write(buffer,0,count);
                }
            }
            String actual=HexFormat.of().formatHex(hash.digest());
            if(total!=declaredBytes)throw new IOException("MEDIA_SIZE_MISMATCH");
            if(!MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII),expectedHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new IOException("MEDIA_HASH_MISMATCH");
            success=true;return new ZenflowMediaInput(file,total,actual);
        }finally{if(!success)Files.deleteIfExists(file);}
    }
    @Override public void close()throws IOException{Files.deleteIfExists(file);}
}
