package com.deepdimen.matrix.zenflow.media;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.common.file.core.FileTemplate;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class ZenflowMediaConfigurationTest {
    @TempDir Path directory;
    private AnnotationConfigApplicationContext context(Map<String,Object> settings,JdbcTemplate jdbc){
        var context=new AnnotationConfigApplicationContext();context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",settings));
        context.registerBean(JdbcTemplate.class,()->jdbc);context.registerBean(ZenflowConsumerAccess.class,()->mock(ZenflowConsumerAccess.class));context.registerBean(PlatformTransactionManager.class,()->mock(PlatformTransactionManager.class));context.registerBean(ObjectMapper.class,()->new ObjectMapper());context.registerBean("existingFiles",FileTemplate.class,()->mock(FileTemplate.class));
        context.register(ZenflowMediaConfiguration.class,ZenflowMediaController.class);return context;
    }
    private Map<String,Object> settings()throws Exception{
        String probe=System.getProperty("invelle.test.ffprobe"),decoder=System.getProperty("invelle.test.ffmpeg");org.junit.jupiter.api.Assumptions.assumeTrue(probe!=null&&decoder!=null);
        Path root=Files.createDirectory(directory.resolve("private")),spool=Files.createDirectory(directory.resolve("spool"));
        Files.setPosixFilePermissions(root,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));Files.setPosixFilePermissions(spool,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        var p=new HashMap<String,Object>();p.put("zenflow.media.enabled","true");p.put("zenflow.media.private-root",root.toString());p.put("zenflow.media.spool-directory",spool.toString());p.put("zenflow.media.bucket","invelle-private-test");p.put("zenflow.media.prefix","voice");p.put("zenflow.media.storage-ref","test-private");p.put("zenflow.media.max-bytes","5242880");p.put("zenflow.media.max-duration-seconds","60");p.put("zenflow.media.max-attachments","4");p.put("zenflow.media.upload-lifetime-seconds","3600");p.put("zenflow.media.lease-seconds","120");p.put("zenflow.media.ffprobe",probe);p.put("zenflow.media.ffmpeg",decoder);p.put("zenflow.media.inspection-timeout-seconds","10");return p;
    }
    @Test void defaultConfigurationExposesNoMediaBeansAndDoesNotProbeDatabase(){
        var jdbc=mock(JdbcTemplate.class);try(var context=context(Map.of(),jdbc)){context.refresh();assertTrue(context.getBeansOfType(ZenflowMediaController.class).isEmpty());assertTrue(context.getBeansOfType(ZenflowNoteMedia.class).isEmpty());verify(jdbc).afterPropertiesSet();verifyNoMoreInteractions(jdbc);}
    }
    @Test void enabledWithoutIndependentStorageFailsInsteadOfBorrowingGlobalFiles(){
        try(var context=context(Map.of("zenflow.media.enabled","true"),mock(JdbcTemplate.class))){assertThrows(org.springframework.beans.BeansException.class,context::refresh);}
    }
    @Test void explicitConfigurationProbesSchemaAndDoesNotAddAnotherFileTemplateBean()throws Exception{
        var jdbc=mock(JdbcTemplate.class);try(var context=context(settings(),jdbc)){
            context.refresh();assertTrue(context.getBean(ZenflowMediaConfiguration.SchemaVerified.class).ready());assertNotNull(context.getBean(ZenflowMediaController.class));assertEquals(1,context.getBeansOfType(FileTemplate.class).size());verify(jdbc,atLeastOnce()).queryForList(contains("FROM zf_media_asset WHERE 1=0"));
            try(var paths=Files.walk(directory)){assertEquals(0,paths.filter(Files::isRegularFile).count());}
        }
    }
    @Test void missingSchemaRefusesToStartEnabledMediaService()throws Exception{
        var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString())).thenThrow(new org.springframework.jdbc.BadSqlGrammarException("schema probe","SELECT",new java.sql.SQLException("missing media table")));
        try(var context=context(settings(),jdbc)){assertThrows(org.springframework.beans.BeansException.class,context::refresh);}
    }
}
