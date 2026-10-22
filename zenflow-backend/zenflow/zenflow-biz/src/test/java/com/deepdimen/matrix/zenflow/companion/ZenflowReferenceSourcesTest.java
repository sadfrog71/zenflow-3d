package com.deepdimen.matrix.zenflow.companion;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class ZenflowReferenceSourcesTest {
    private JdbcTemplate jdbc;
    private JdbcDataSource data;
    private ZenflowReferenceSources sources;
    private final ZenflowReferencePolicy.Reference selected=new ZenflowReferencePolicy.Reference("10","MATERIAL","en","INTERNAL");
    @BeforeEach void setup() {
        data=new JdbcDataSource();data.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(data);
        jdbc.execute("CREATE TABLE ai_dataset(id BIGINT,tenant_id BIGINT,collection_name VARCHAR(100),del_flag VARCHAR(1))");
        jdbc.execute("CREATE TABLE ai_document(id BIGINT,dataset_id BIGINT,name VARCHAR(200),document_config VARCHAR(4000),del_flag VARCHAR(1),file_status VARCHAR(1),tenant_id BIGINT)");
        jdbc.execute("CREATE TABLE ai_slice(id BIGINT,document_id BIGINT,content VARCHAR(20000),del_flag VARCHAR(1),tenant_id BIGINT)");
        var product=new ZenflowProperties();product.setTenantId(1L);sources=new ZenflowReferenceSources(jdbc,product);
        jdbc.update("INSERT INTO ai_dataset VALUES(1,1,'invelle-public-1','0')");
        jdbc.update("INSERT INTO ai_document VALUES(10,1,'Reference',?,'0','1',1)","{\"invelleAudience\":\"PUBLIC\",\"kind\":\"MATERIAL\",\"locale\":\"en\",\"citationVisibility\":\"INTERNAL\",\"invelleCompanionApproved\":true,\"invelleSourceVersion\":\"v1\"}");
        jdbc.update("INSERT INTO ai_slice VALUES(100,10,?,'0',1)","first line\n second line");
    }
    @Test void preservesOriginalTextAndProducesImmutableContentSensitiveSnapshot() {
        var first=sources.load(selected);
        assertEquals("first line\n second line",first.fragments().get(0).content());
        assertEquals(64,first.sourceHash().length());
        assertThrows(UnsupportedOperationException.class,()->first.fragments().clear());
        jdbc.update("UPDATE ai_slice SET content='new revision' WHERE id=100");
        assertNotEquals(first.sourceHash(),sources.load(selected).sourceHash());
        assertEquals("first line\n second line",first.fragments().get(0).content());
    }
    @Test void rejectsOversizedSlicesAndExcessiveFragmentCounts() {
        jdbc.update("UPDATE ai_slice SET content=?", "x".repeat(16001));
        assertThrows(ResponseStatusException.class,()->sources.load(selected));
        jdbc.update("UPDATE ai_slice SET content='small'");
        for(int id=101;id<=1100;id++) jdbc.update("INSERT INTO ai_slice VALUES(?,10,'small','0',1)",id);
        assertThrows(ResponseStatusException.class,()->sources.load(selected));
    }
    @Test void concurrentSourceEditCannotMixApprovalVersionAndNewBody() {
        var concurrentData=new JdbcDataSource();concurrentData.setURL(data.getURL());
        var concurrentJdbc=new JdbcTemplate(concurrentData);
        var interleaved=new JdbcTemplate(data) {
            @Override public <T> T query(String sql,org.springframework.jdbc.core.ResultSetExtractor<T> extractor,Object... args) {
                return super.query(sql,result -> {
                    // Commit a separate source change after query execution, before projection.
                    concurrentJdbc.update("UPDATE ai_slice SET content='changed concurrently' WHERE id=100");
                    concurrentJdbc.update("UPDATE ai_document SET document_config='{}' WHERE id=10");
                    return extractor.extractData(result);
                },args);
            }
        };
        var product=new ZenflowProperties();product.setTenantId(1L);
        var reader=new ZenflowReferenceSources(interleaved,product);
        var snapshot=reader.load(selected);
        assertEquals("v1",snapshot.sourceVersion());
        assertEquals("first line\n second line",snapshot.fragments().get(0).content());
        assertThrows(ResponseStatusException.class,()->sources.load(selected));
    }
    @Test void publicVisibilityAndHiddenCitationNeverSubstituteForCompanionApproval() {
        for(String config:List.of("{}","{\"invelleCompanionApproved\":\"true\",\"invelleSourceVersion\":\"v1\"}",
                "{\"invelleCompanionApproved\":true}","invalid")) {
            jdbc.update("UPDATE ai_document SET document_config=?",config);
            assertThrows(ResponseStatusException.class,()->sources.load(selected));
        }
    }
    @Test void callersCannotUpgradeApprovedCitationVisibilityOrRelabelTheSource() {
        assertThrows(ResponseStatusException.class,()->sources.load(new ZenflowReferencePolicy.Reference("10","MATERIAL","en","PUBLIC")));
        assertThrows(ResponseStatusException.class,()->sources.load(new ZenflowReferencePolicy.Reference("10","EXAMPLE","en","INTERNAL")));
    }
    @Test void rejectsForeignTenantPrivateDeletedAndUnprocessedSources() {
        for(String change:List.of("UPDATE ai_dataset SET tenant_id=2","UPDATE ai_dataset SET collection_name='private'",
                "UPDATE ai_dataset SET del_flag='1'","UPDATE ai_document SET tenant_id=2","UPDATE ai_document SET del_flag='1'","UPDATE ai_document SET file_status='0'")) {
            jdbc.update(change);assertThrows(ResponseStatusException.class,()->sources.load(selected));
            jdbc.update("UPDATE ai_dataset SET tenant_id=1,collection_name='invelle-public-1',del_flag='0'");
            jdbc.update("UPDATE ai_document SET del_flag='0',file_status='1',tenant_id=1");
        }
        jdbc.update("UPDATE ai_slice SET tenant_id=2");
        assertThrows(ResponseStatusException.class,()->sources.load(selected));
        jdbc.update("UPDATE ai_slice SET tenant_id=1,del_flag='1'");
        assertThrows(ResponseStatusException.class,()->sources.load(selected));
    }
}
