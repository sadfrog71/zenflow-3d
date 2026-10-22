package com.deepdimen.matrix.zenflow.garden;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class ZenflowGardenConfigurationTest {
    private AnnotationConfigApplicationContext context(Map<String,Object> settings){
        var context=new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",settings));
        context.registerBean(JdbcTemplate.class,()->mock(JdbcTemplate.class));
        context.registerBean(ZenflowConsumerAccess.class,()->mock(ZenflowConsumerAccess.class));
        context.registerBean(PlatformTransactionManager.class,()->mock(PlatformTransactionManager.class));
        context.registerBean(ObjectMapper.class,()->new ObjectMapper());
        context.register(ZenflowGardenConfiguration.class,ZenflowGardenController.class,com.deepdimen.matrix.zenflow.operation.ZenflowOperationConfiguration.class,com.deepdimen.matrix.zenflow.operation.ZenflowOperationController.class);
        return context;
    }
    @Test void absentConfigurationDoesNotExposeUnmigratedGardenService(){
        try(var context=context(Map.of())){
            context.refresh();assertTrue(context.getBeansOfType(ZenflowGardenFacade.class).isEmpty());
            assertTrue(context.getBeansOfType(ZenflowGardenController.class).isEmpty());
            assertTrue(context.getBeansOfType(com.deepdimen.matrix.zenflow.operation.ZenflowOperationController.class).isEmpty());
        }
    }
    @Test void enabledConfigurationNeedsValidCatalogue(){
        try(var context=context(Map.of("zenflow.garden.enabled","true"))){
            assertThrows(org.springframework.beans.BeansException.class,context::refresh);
        }
    }
    @Test void explicitlyConfiguredCatalogueBindsBeforeServiceConstruction(){
        var settings=new java.util.HashMap<String,Object>();
        settings.put("zenflow.garden.enabled","true");settings.put("zenflow.garden.scene-version","test-v1");
        settings.put("zenflow.garden.initial-fish-type","test-koi");settings.put("zenflow.garden.fish-types[0]","test-koi");
        settings.put("zenflow.garden.presets[0]","day");settings.put("zenflow.garden.max-placements","1");
        settings.put("zenflow.garden.placements.stone.anchors[0]","test.anchor");
        settings.put("zenflow.garden.placements.stone.max-position","1");
        settings.put("zenflow.garden.placements.stone.min-scale","0.5");settings.put("zenflow.garden.placements.stone.max-scale","2");
        try(var context=context(settings)){
            context.refresh();assertNotNull(context.getBean(ZenflowGardenFacade.class));assertNotNull(context.getBean(ZenflowGardenController.class));assertNotNull(context.getBean(com.deepdimen.matrix.zenflow.operation.ZenflowOperationController.class));
            var jdbc=context.getBean(JdbcTemplate.class);verify(jdbc).afterPropertiesSet();verifyNoMoreInteractions(jdbc);
        }
    }
}
