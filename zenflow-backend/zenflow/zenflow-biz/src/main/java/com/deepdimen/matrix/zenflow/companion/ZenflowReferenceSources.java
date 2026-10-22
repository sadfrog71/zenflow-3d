package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Internal source reader. Product endpoints must separately require ADMIN and a mutable owned draft. */
@Component
public class ZenflowReferenceSources {
    private final JdbcTemplate jdbc;
    private final ZenflowProperties product;
    private final ObjectMapper json = new ObjectMapper();
    public record Fragment(String sliceId, String content, String hash) {}
    public record Source(String datasetId, String documentId, String title, String sourceVersion,
                         String sourceHash, String sourceLocator, List<Fragment> fragments) {
        public Source { fragments = List.copyOf(fragments); }
    }
    public ZenflowReferenceSources(JdbcTemplate jdbc, ZenflowProperties product) {
        this.jdbc=jdbc; this.product=product;
    }
    public Source load(ZenflowReferencePolicy.Reference selection) {return load(selection,false);}
    public Source loadForFreeze(ZenflowReferencePolicy.Reference selection) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("REFERENCE_TRANSACTION_REQUIRED");
        return load(selection,true);
    }
    private Source load(ZenflowReferencePolicy.Reference selection,boolean lock) {
        // One SQL snapshot prevents source approval/version and body from different reads being mixed.
        // Entity declarations omit tenant fields, but the deployed tables have them; check all three owners.
        return jdbc.query("SELECT d.dataset_id,d.name,d.document_config,s.id AS slice_id,s.content "
                + "FROM ai_document d JOIN ai_dataset k ON k.id=d.dataset_id "
                + "JOIN ai_slice s ON s.document_id=d.id WHERE k.tenant_id=? AND k.collection_name=? "
                + "AND d.tenant_id=k.tenant_id AND s.tenant_id=k.tenant_id AND k.del_flag='0' AND d.del_flag='0' AND d.file_status='1' AND d.id=? "
                + "AND s.del_flag='0' ORDER BY s.id LIMIT 1001"+(lock?" FOR UPDATE":""), result -> {
            if(!result.next()) throw unavailable();
            var metadata=readMetadata(result.getString("document_config"));
            verifySelection(metadata,selection);
            String version=approvedVersion(metadata);
            String title=Objects.toString(result.getString("name"),"");
            String datasetId=result.getString("dataset_id");
            if(title.isBlank() || title.length()>200) throw unavailable();
            var fragments=new ArrayList<Fragment>();
            int characters=0;
            do {
                if(fragments.size()==1000) throw unavailable();
                String content=Objects.toString(result.getString("content"),"");
                characters+=content.length();
                if(content.isBlank() || content.length()>16000 || characters>400000) throw unavailable();
                fragments.add(new Fragment(result.getString("slice_id"),content,hash(content)));
            } while(result.next());
            String digestInput=fragments.stream().map(f->f.sliceId()+":"+f.hash())
                    .collect(java.util.stream.Collectors.joining("\n", "", "\n"));
            String locator=metadata.path("sourceLocator").asText("");if(locator.length()>512)throw unavailable();
            return new Source(datasetId,selection.documentId(),title,version,hash(digestInput),locator,fragments);
        }, product.getTenantId(),collection(product.getTenantId()),selection.documentId());
    }
    private void verifySelection(com.fasterxml.jackson.databind.JsonNode metadata,ZenflowReferencePolicy.Reference selection) {
        try {
            if(!selection.kind().equals(metadata.path("kind").asText()) || !selection.locale().equals(metadata.path("locale").asText())
                    || !selection.citationVisibility().equals(metadata.path("citationVisibility").asText()))throw unavailable();
        }catch(ResponseStatusException error){throw error;}catch(Exception error){throw unavailable();}
    }
    private com.fasterxml.jackson.databind.JsonNode readMetadata(String config) {
        try{return json.readTree(config);}catch(Exception invalid){throw unavailable();}
    }
    public static String collection(long tenant) { return "invelle-public-"+tenant; }
    private String approvedVersion(com.fasterxml.jackson.databind.JsonNode metadata) {
        try {
            if(!"PUBLIC".equals(metadata.path("invelleAudience").asText())
                    || !metadata.path("invelleCompanionApproved").isBoolean()
                    || !metadata.path("invelleCompanionApproved").booleanValue()) throw unavailable();
            var version=metadata.path("invelleSourceVersion");
            if(!version.isTextual() || !version.textValue().matches("[A-Za-z0-9._-]{1,64}")) throw unavailable();
            return version.textValue();
        } catch(ResponseStatusException error) { throw error; }
        catch(Exception error) { throw unavailable(); }
    }
    private static String hash(String content) {
        try { return ZenflowRunAuthorization.hash(content); }
        catch(IllegalStateException error) { throw new IllegalStateException("REFERENCE_HASH_UNAVAILABLE"); }
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.CONFLICT,"REFERENCE_SOURCE_NOT_APPROVED_OR_READY");
    }
}
