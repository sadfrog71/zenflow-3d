package com.deepdimen.matrix.zenflow.media;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZenflowAudioInspectorTest {
    @TempDir Path directory;
    private ZenflowAudioInspector inspector(){
        String probe=System.getProperty("invelle.test.ffprobe"),decoder=System.getProperty("invelle.test.ffmpeg");
        org.junit.jupiter.api.Assumptions.assumeTrue(probe!=null&&decoder!=null,"Explicit local decoder paths required");
        return new ZenflowAudioInspector(Path.of(probe),Path.of(decoder),10);
    }
    static byte[] wave(){
        int samples=2000;var bytes=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("RIFF".getBytes()).putInt(36+samples*2).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(samples*2);
        for(int i=0;i<samples;i++)bytes.putShort((short)(Math.sin(i*.2)*1000));return bytes.array();
    }
    @Test void realProbeAndFullDecoderAcceptSyntheticWaveAndMeasureActualDuration()throws Exception{
        var inspector=inspector();byte[] bytes=wave();
        try(var input=ZenflowMediaInput.receive(new ByteArrayInputStream(bytes),directory,bytes.length,10000,ZenflowMediaInputTest.hash(bytes))){
            var result=inspector.inspect(input,"audio/wav",60);assertEquals("audio/wav",result.mime());assertEquals(.25,result.durationSeconds(),.001);assertEquals("pcm_s16le",result.codec());
            assertThrows(IOException.class,()->inspector.inspect(input,"audio/mp4",60));
            assertThrows(IOException.class,()->inspector.inspect(input,"audio/wav",.1));
        }
    }
    @Test void actualM4aAacRecordingIsRecognizedAndFullyDecoded()throws Exception{
        var inspector=inspector();Path wav=directory.resolve("synthetic.wav"),m4a=directory.resolve("synthetic.m4a");
        java.nio.file.Files.write(wav,wave());
        var conversion=new ProcessBuilder(System.getProperty("invelle.test.ffmpeg"),"-nostdin","-v","error","-i",wav.toString(),"-c:a","aac","-y",m4a.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(conversion.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,conversion.exitValue());
        byte[] bytes=java.nio.file.Files.readAllBytes(m4a);
        try(var input=ZenflowMediaInput.receive(new ByteArrayInputStream(bytes),directory,bytes.length,10000,ZenflowMediaInputTest.hash(bytes))){
            var result=inspector.inspect(input,"audio/mp4",60);assertEquals("aac",result.codec());assertTrue(result.durationSeconds()>0&&result.durationSeconds()<1);
            assertThrows(IOException.class,()->inspector.inspect(input,"audio/ogg",60));
        }
    }
    @Test void aContainerHeaderDoesNotMakeCorruptContentDecodable()throws Exception{
        var inspector=inspector();byte[] bytes=java.util.Arrays.copyOf(wave(),44);
        try(var input=ZenflowMediaInput.receive(new ByteArrayInputStream(bytes),directory,bytes.length,10000,ZenflowMediaInputTest.hash(bytes))){assertThrows(IOException.class,()->inspector.inspect(input,"audio/wav",60));}
    }
}
