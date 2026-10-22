package com.deepdimen.matrix.zenflow.admin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import com.fasterxml.jackson.core.StreamReadConstraints;
/** Thirty MiB files need forty MiB Base64 strings; route ingress still bounds other payloads. */
@Configuration
public class ZenflowUploadJsonConfiguration {
 @Bean public Jackson2ObjectMapperBuilderCustomizer zenflowUploadStrings(){return builder->builder.postConfigurer(mapper->mapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxStringLength(42*1024*1024).build()));}
}
