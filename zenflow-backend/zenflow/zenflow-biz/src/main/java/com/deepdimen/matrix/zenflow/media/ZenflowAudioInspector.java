package com.deepdimen.matrix.zenflow.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Local-file-only subprocesses; no shell, remote protocol, client-supplied path or metadata logging. */
public final class ZenflowAudioInspector {
    public record Audio(String mime,double durationSeconds,String codec) {}
    private final Path probe,decoder;
    private final int timeoutSeconds;
    private final java.util.concurrent.Semaphore processing=new java.util.concurrent.Semaphore(1);
    public ZenflowAudioInspector(Path probe,Path decoder,int timeoutSeconds){
        if(probe==null||decoder==null||!probe.isAbsolute()||!decoder.isAbsolute()||!Files.isExecutable(probe)||!Files.isExecutable(decoder)||timeoutSeconds<1||timeoutSeconds>60)throw new IllegalArgumentException("AUDIO_INSPECTOR_CONFIGURATION_REQUIRED");
        this.probe=probe;this.decoder=decoder;this.timeoutSeconds=timeoutSeconds;
    }
    public Audio inspect(ZenflowMediaInput input,String declaredMime,double maxSeconds)throws IOException{
        if(!processing.tryAcquire())throw new IOException("AUDIO_INSPECTION_BUSY");
        try{return inspectContent(input,declaredMime,maxSeconds);}finally{processing.release();}
    }
    private Audio inspectContent(ZenflowMediaInput input,String declaredMime,double maxSeconds)throws IOException{
        if(declaredMime==null||!Double.isFinite(maxSeconds)||maxSeconds<=0||maxSeconds>600||!Map.of("audio/mp4","mov","audio/wav","wav","audio/ogg","ogg").containsKey(declaredMime))throw invalid();
        byte[] header;
        try(var stream=Files.newInputStream(input.file())){header=stream.readNBytes(12);}
        if(header.length<12)throw invalid();
        String signature=new String(header,java.nio.charset.StandardCharsets.ISO_8859_1);
        boolean matches=switch(declaredMime){case "audio/mp4"->signature.substring(4,8).equals("ftyp");case "audio/wav"->signature.startsWith("RIFF")&&signature.substring(8,12).equals("WAVE");case "audio/ogg"->signature.startsWith("OggS");default->false;};
        if(!matches)throw invalid();
        var options=List.of("-v","error","-protocol_whitelist","file,pipe","-format_whitelist","mov,wav,ogg","-probesize","5242880","-analyzeduration","2000000");
        var command=new ArrayList<String>();command.add(probe.toString());command.addAll(options);
        command.addAll(List.of("-show_entries","format=duration,format_name:stream=codec_type,codec_name","-of","json",input.file().toString()));
        var metadata=new ObjectMapper().readTree(run(command,input.file().getParent()));
        var streams=metadata.path("streams");
        if(!streams.isArray()||streams.size()!=1||!"audio".equals(streams.get(0).path("codec_type").asText()))throw invalid();
        String format=metadata.path("format").path("format_name").asText();
        String expected=Map.of("audio/mp4","mov","audio/wav","wav","audio/ogg","ogg").get(declaredMime);
        if(!List.of(format.split(",")).contains(expected))throw invalid();
        double declaredDuration=metadata.path("format").path("duration").asDouble(Double.NaN);
        if(!Double.isFinite(declaredDuration)||declaredDuration<=0||declaredDuration>maxSeconds)throw invalid();
        command=new ArrayList<>();command.add(decoder.toString());command.addAll(List.of("-nostdin","-xerror"));command.addAll(options);
        command.addAll(List.of("-i",input.file().toString(),"-map","0:a:0","-progress","pipe:1","-nostats","-f","null","-"));
        String progress=run(command,input.file().getParent());double duration=0;
        for(String line:progress.split("\\R"))if(line.startsWith("out_time_us=")){
            try{duration=Math.max(duration,Long.parseLong(line.substring(12))/1000000d);}catch(NumberFormatException ignored){throw invalid();}
        }
        if(!Double.isFinite(duration)||duration<=0||duration>maxSeconds)throw invalid();
        return new Audio(declaredMime,duration,streams.get(0).path("codec_name").asText());
    }
    private String run(List<String> command,Path directory)throws IOException{
        Path output=Files.createTempFile(directory,"invelle-probe-",".txt",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Process process=null;
        try{
            process=new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!process.waitFor(timeoutSeconds,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("AUDIO_INSPECTION_TIMEOUT");}
            if(process.exitValue()!=0||Files.size(output)>65536)throw invalid();
            return Files.readString(output);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("AUDIO_INSPECTION_INTERRUPTED");}
        finally{if(process!=null&&process.isAlive())process.destroyForcibly();Files.deleteIfExists(output);}
    }
    private static IOException invalid(){return new IOException("INVALID_AUDIO_CONTENT");}
}
