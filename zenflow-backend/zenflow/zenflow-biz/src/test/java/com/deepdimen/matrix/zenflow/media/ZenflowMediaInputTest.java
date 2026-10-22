package com.deepdimen.matrix.zenflow.media;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZenflowMediaInputTest {
    @TempDir Path directory;
    static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @Test void receivesExactBytesInPrivateFileAndClosesWithoutLeavingContent()throws Exception{
        byte[] data="synthetic audio bytes".getBytes();Path file;
        try(var input=ZenflowMediaInput.receive(new ByteArrayInputStream(data),directory,data.length,1024,hash(data))){
            assertEquals(data.length,input.bytes());assertEquals(hash(data),input.sha256());assertArrayEquals(data,Files.readAllBytes(input.file()));
            assertEquals("rw-------",java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(input.file())));file=input.file();
        }
        assertFalse(Files.exists(file));
    }
    @Test void overLimitInfiniteStreamStopsAfterOneExtraByteAndCleansSpool()throws Exception{
        int[] read={0};
        InputStream endless=new InputStream(){public int read(){read[0]++;return 1;}public int read(byte[] b,int off,int len){read[0]+=len;java.util.Arrays.fill(b,off,off+len,(byte)1);return len;}};
        assertThrows(IOException.class,()->ZenflowMediaInput.receive(endless,directory,32,32,"0".repeat(64)));
        assertEquals(33,read[0]);try(var files=Files.list(directory)){assertEquals(0,files.count());}
    }
    @Test void truncationHashMismatchAndStreamFailureNeverLeaveAValidUpload()throws Exception{
        byte[] data={1,2,3};
        assertThrows(IOException.class,()->ZenflowMediaInput.receive(new ByteArrayInputStream(data),directory,4,8,hash(data)));
        assertThrows(IOException.class,()->ZenflowMediaInput.receive(new ByteArrayInputStream(data),directory,3,8,"0".repeat(64)));
        assertThrows(IOException.class,()->ZenflowMediaInput.receive(new InputStream(){public int read()throws IOException{throw new IOException("interrupted");}},directory,3,8,hash(data)));
        try(var files=Files.list(directory)){assertEquals(0,files.count());}
    }
    @Test void rejectsPublicSpoolBeforeAcceptingPrivateBytes()throws Exception{
        Files.setPosixFilePermissions(directory,java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        assertThrows(IOException.class,()->ZenflowMediaInput.receive(new ByteArrayInputStream(new byte[]{1}),directory,1,8,"0".repeat(64)));
    }
}
