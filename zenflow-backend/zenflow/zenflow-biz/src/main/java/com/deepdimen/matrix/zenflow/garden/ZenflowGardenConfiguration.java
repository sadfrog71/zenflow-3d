package com.deepdimen.matrix.zenflow.garden;

import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Disabled until the migration and server-approved catalogue have both been supplied. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="zenflow.garden",name="enabled",havingValue="true")
@EnableConfigurationProperties(ZenflowGardenConfiguration.Properties.class)
public class ZenflowGardenConfiguration {
    @ConfigurationProperties(prefix="zenflow.garden")
    public record Properties(boolean enabled,String sceneVersion,String initialFishType,Set<String> fishTypes,
            Map<String,ZenflowGardenOperations.Area> placements,Set<String> presets,int maxPlacements) {}
    @Bean ZenflowGardenFacade zenflowGardenFacade(JdbcTemplate jdbc,ZenflowConsumerAccess access,
            PlatformTransactionManager manager,ObjectMapper json,Properties properties){
        if(properties.fishTypes()==null||properties.placements()==null||properties.presets()==null)throw new IllegalArgumentException("GARDEN_CATALOGUE_REQUIRED");
        var catalogue=new ZenflowGardenOperations.Catalogue(properties.fishTypes(),properties.placements(),properties.presets(),properties.maxPlacements());
        return new ZenflowGardenFacade(jdbc,access,manager,json,catalogue,properties.sceneVersion(),properties.initialFishType());
    }
}
