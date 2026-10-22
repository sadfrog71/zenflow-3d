package com.deepdimen.matrix.zenflow.media;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.deepdimen.matrix.common.file.core.FileProperties;
import com.deepdimen.matrix.common.file.core.FileTemplate;
import com.deepdimen.matrix.common.file.local.LocalFileProperties;
import com.deepdimen.matrix.common.file.local.LocalFileTemplate;
import com.deepdimen.matrix.common.file.oss.service.OssTemplate;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ZenflowMediaObjectsTest {
    @TempDir Path directory;
    @Test void localTemplateWritesAndStreamsExactRangeAfterPerRequestAuthorization()throws Exception{
        var properties=new FileProperties();var local=new LocalFileProperties();local.setBasePath(directory.resolve("objects").toString());properties.setLocal(local);
        var storage=new ZenflowMediaObjects(new LocalFileTemplate(properties),"invelle-private-test","voice");
        byte[] data="0123456789".getBytes();var id=UUID.randomUUID();ZenflowMediaObjects.Pointer pointer;
        try(var input=ZenflowMediaInput.receive(new ByteArrayInputStream(data),directory,data.length,100,ZenflowMediaInputTest.hash(data))){
            pointer=storage.put(id,input,new ZenflowAudioInspector.Audio("audio/wav",1,"test"));
        }
        int[] authorizations={0};var output=new ByteArrayOutputStream();
        var range=storage.stream(pointer,"bytes=2-5",media->{assertEquals(id,media);authorizations[0]++;},output);
        assertEquals("2345",output.toString());assertEquals(4,range.length());
        storage.stream(pointer,"bytes=-2",media->{authorizations[0]++;},new ByteArrayOutputStream());assertEquals(2,authorizations[0]);
        assertThrows(SecurityException.class,()->storage.stream(pointer,null,media->{throw new SecurityException("parent withdrawn");},new ByteArrayOutputStream()));
        storage.remove(pointer);assertFalse(Files.exists(directory.resolve("objects/invelle-private-test/voice/"+id+"/"+pointer.sha256())));
    }
    @Test void deniedOrMissingGuardNeverOpensObjectStorage(){
        var files=mock(FileTemplate.class);var storage=new ZenflowMediaObjects(files,"invelle-private-test","voice");
        var pointer=new ZenflowMediaObjects.Pointer(UUID.randomUUID(),"a".repeat(64),10,"audio/wav");
        assertThrows(SecurityException.class,()->storage.stream(pointer,null,id->{throw new SecurityException();},new ByteArrayOutputStream()));
        assertThrows(IllegalArgumentException.class,()->storage.stream(pointer,null,null,new ByteArrayOutputStream()));
        verifyZeroInteractions(files);
    }
    @Test void knownLengthOssUploadPassesOriginalStreamAndMetadataWithoutReadingOrBuffering()throws Exception{
        var client=mock(AmazonS3.class);var storage=new OssTemplate(new FileProperties());ReflectionTestUtils.setField(storage,"amazonS3",client);
        InputStream input=new InputStream(){public int read(){throw new AssertionError("Adapter must not buffer stream");}};
        storage.putObjectKnownLength("invelle-private-test","voice/test",input,1234,"audio/mp4");
        var metadata=ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(client).putObject(eq("invelle-private-test"),eq("voice/test"),same(input),metadata.capture());
        assertEquals(1234,metadata.getValue().getContentLength());assertEquals("audio/mp4",metadata.getValue().getContentType());
    }
    @Test void missingStorageAndPathLikeConfigurationFailClosed(){
        var files=mock(FileTemplate.class);
        assertThrows(IllegalArgumentException.class,()->new ZenflowMediaObjects(files,null,"voice"));
        assertThrows(IllegalArgumentException.class,()->new ZenflowMediaObjects(files,"invelle-private-test","../public"));
        verifyZeroInteractions(files);
    }
}
