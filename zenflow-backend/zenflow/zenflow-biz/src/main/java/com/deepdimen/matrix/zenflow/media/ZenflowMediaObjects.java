package com.deepdimen.matrix.zenflow.media;

import com.deepdimen.matrix.common.file.core.FileTemplate;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;

/** Product-private object adapter. It never creates public URLs or returns provider keys to consumers. */
public final class ZenflowMediaObjects {
    public record Stage(UUID mediaId,UUID token,String sha256,long bytes){
        public Stage{if(mediaId==null||token==null||sha256==null||!sha256.matches("[a-f0-9]{64}")||bytes<1||bytes>64L*1024*1024)throw new IllegalArgumentException("INVALID_MEDIA_STAGE");}
    }
    public record Pointer(UUID mediaId,String sha256,long bytes,String mime,UUID stageToken) {
        public Pointer(UUID mediaId,String sha256,long bytes,String mime){this(mediaId,sha256,bytes,mime,null);}
        public Pointer {
            if(mediaId==null||sha256==null||!sha256.matches("[a-f0-9]{64}")||bytes<1||bytes>64L*1024*1024||mime==null||!Set.of("audio/mp4","audio/wav","audio/ogg").contains(mime))throw new IllegalArgumentException("INVALID_MEDIA_POINTER");
        }
    }
    @FunctionalInterface public interface ReadAuthorization {void verify(UUID mediaId);}
    private final FileTemplate files;
    private final String bucket,prefix;
    /** Configuration must refer to independently provisioned private storage; no fallback to global bucket. */
    public ZenflowMediaObjects(FileTemplate files,String bucket,String prefix){
        if(files==null||bucket==null||!bucket.matches("[a-z0-9][a-z0-9-]{2,62}")||prefix==null||!prefix.matches("[a-zA-Z0-9_-]{1,64}"))throw new IllegalArgumentException("PRIVATE_MEDIA_STORAGE_REQUIRED");
        this.files=files;this.bucket=bucket;this.prefix=prefix;
    }
    public Pointer put(UUID id,ZenflowMediaInput input,ZenflowAudioInspector.Audio audio)throws Exception{
        var pointer=new Pointer(id,input.sha256(),input.bytes(),audio.mime());
        try(var stream=Files.newInputStream(input.file())){files.putObjectKnownLength(bucket,key(pointer),stream,pointer.bytes(),pointer.mime());}
        return pointer;
    }
    /** Quarantine is a separate namespace; staged content has no playback entry point. */
    public Stage stage(UUID id,UUID token,ZenflowMediaInput input)throws Exception{
        var stage=new Stage(id,token,input.sha256(),input.bytes());
        try(var stream=Files.newInputStream(input.file())){files.putObjectKnownLength(bucket,stageKey(stage),stream,stage.bytes(),"application/octet-stream");}
        return stage;
    }
    public ZenflowMediaInput inspectStage(Stage stage,java.nio.file.Path privateDirectory)throws IOException{
        try(var object=files.getObject(bucket,stageKey(stage))){return ZenflowMediaInput.receive(object.getObjectContent(),privateDirectory,stage.bytes(),stage.bytes(),stage.sha256());}
    }
    public void removeStage(Stage stage)throws Exception{files.removeObject(bucket,stageKey(stage));}
    /** Must be called only after the service has verified the owner and cleanup lifecycle. */
    public void remove(Pointer pointer)throws Exception{files.removeObject(bucket,key(pointer));}
    /** Every playback request invokes its parent-resource guard before opening storage. */
    public ZenflowMediaRange stream(Pointer pointer,String rangeHeader,ReadAuthorization authorization,OutputStream output)throws IOException{
        if(authorization==null)throw new IllegalArgumentException("MEDIA_AUTHORIZATION_REQUIRED");
        authorization.verify(pointer.mediaId());
        var range=ZenflowMediaRange.parse(rangeHeader,pointer.bytes());
        try(var object=files.getObject(bucket,key(pointer));var stream=object.getObjectContent()){
            stream.skipNBytes(range.start());long remaining=range.length();byte[] buffer=new byte[16384];
            while(remaining>0){
                if(Thread.currentThread().isInterrupted())throw new IOException("MEDIA_STREAM_INTERRUPTED");
                int count=stream.read(buffer,0,(int)Math.min(buffer.length,remaining));
                if(count<0)throw new EOFException("MEDIA_OBJECT_TRUNCATED");
                if(count==0)throw new IOException("MEDIA_STREAM_STALLED");
                output.write(buffer,0,count);remaining-=count;
            }
        }
        return range;
    }
    private String key(Pointer pointer){if(pointer.stageToken()!=null)return stageKey(new Stage(pointer.mediaId(),pointer.stageToken(),pointer.sha256(),pointer.bytes()));return prefix+"/"+pointer.mediaId()+"/"+pointer.sha256();}
    private String stageKey(Stage stage){return prefix+"/quarantine/"+stage.mediaId()+"/"+stage.token()+"/"+stage.sha256();}
}
