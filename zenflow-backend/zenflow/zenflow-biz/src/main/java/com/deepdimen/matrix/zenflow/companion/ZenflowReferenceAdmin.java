package com.deepdimen.matrix.zenflow.companion;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.zenflow.admin.ZenflowAdminAccess;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Product-only maintenance facade over the original knowledge tables. No consumer content is imported. */
@Service
public class ZenflowReferenceAdmin {
    private final JdbcTemplate jdbc;
    private final ZenflowProperties product;
    private final ZenflowAdminAccess access;
    private final TransactionTemplate tx;
    private final ObjectMapper json=new ObjectMapper();
    public ZenflowReferenceAdmin(JdbcTemplate jdbc,ZenflowProperties product,ZenflowAdminAccess access,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.product=product;this.access=access;this.tx=new TransactionTemplate(manager);
    }
    public Map<String,Object> list(String before) {
        access.require();long cursor=before==null?Long.MAX_VALUE:id(before);
        var rows=jdbc.queryForList("SELECT d.id,d.name,d.document_config,d.slice_count,d.update_time FROM ai_document d "
                +"JOIN ai_dataset k ON k.id=d.dataset_id AND k.tenant_id=d.tenant_id WHERE k.tenant_id=? "
                +"AND k.collection_name=? AND k.del_flag='0' AND d.del_flag='0' AND d.id<? ORDER BY d.id DESC LIMIT 51",tenant(),collection(),cursor);
        boolean more=rows.size()>50;var page=rows.subList(0,Math.min(rows.size(),50));
        var result=new LinkedHashMap<String,Object>();result.put("items",page.stream().map(this::projection).toList());
        result.put("nextCursor",more?page.get(page.size()-1).get("id").toString():null);return result;
    }
    public Map<String,Object> detail(String documentId) {
        access.require();return tx.execute(ignored -> detailInTransaction(documentId));
    }
    private Map<String,Object> detailInTransaction(String documentId) {
        var row=owned(id(documentId),true);var result=projection(row);var content=new StringBuilder();
        jdbc.query("SELECT s.content FROM ai_slice s JOIN ai_document d ON d.id=s.document_id AND d.tenant_id=s.tenant_id "
                +"WHERE s.document_id=? AND s.tenant_id=? AND s.del_flag='0' ORDER BY s.id LIMIT 1001",rs->{
            int count=0;while(rs.next()) {if(++count>1000)throw error(HttpStatus.CONFLICT,"REFERENCE_SOURCE_TOO_LARGE");
                content.append(Objects.toString(rs.getString("content"),""));if(content.length()>400000)throw error(HttpStatus.CONFLICT,"REFERENCE_SOURCE_TOO_LARGE");}
            return null;
        },documentId,tenant());
        result.put("content",content.toString());return result;
    }
    public Map<String,Object> create(Map<String,Object> body) {
        var actor=access.require().getId().toString();var fields=normalize(body,false);
        return tx.execute(ignored->{
            jdbc.queryForList("SELECT id FROM sys_tenant WHERE id=? FOR UPDATE",tenant());
            var pools=jdbc.queryForList("SELECT id FROM ai_dataset WHERE tenant_id=? AND collection_name=? AND del_flag='0' FOR UPDATE",tenant(),collection());
            if(pools.size()>1)throw error(HttpStatus.CONFLICT,"REFERENCE_DATASET_AMBIGUOUS");
            long dataset;
            if(pools.isEmpty()) {
                dataset=IdWorker.getId();jdbc.update("INSERT INTO ai_dataset(id,tenant_id,name,description,collection_name,public_flag,del_flag,create_by,update_by,create_time,update_time) VALUES(?,?,?, ?,?,'0','0',?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                        dataset,tenant(),"Invelle 公共陪伴参考","经后台确认供陪伴使用的公共资料；不包含私人记录",collection(),actor,actor);
            }else dataset=((Number)pools.get(0).get("id")).longValue();
            long document=IdWorker.getId();String version=Long.toString(IdWorker.getId());
            String content=fields.get("content").toString();var fragments=fragments(content);
            jdbc.update("INSERT INTO ai_document(id,tenant_id,dataset_id,name,file_type,source_type,file_hash,file_status,slice_status,slice_count,document_config,del_flag,create_by,update_by,create_time,update_time) VALUES(?,?,?,?,'txt','0',?,'1','0',?,?, '0',?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                    document,tenant(),dataset,fields.get("title"),ZenflowRunAuthorization.hash(content),fragments.size(),metadata(fields,version),actor,actor);
            slices(document,fragments,actor);return detailInTransaction(Long.toString(document));
        });
    }
    public Map<String,Object> update(String documentId,Map<String,Object> body) {
        String actor=access.require().getId().toString();var fields=normalize(body,true);
        return tx.execute(ignored->{var row=owned(id(documentId),true);expected(row,body);
            String content=fields.get("content").toString(),version=Long.toString(IdWorker.getId());var fragments=fragments(content);
            jdbc.update("UPDATE ai_document SET name=?,file_hash=?,slice_count=?,document_config=?,update_by=?,update_time=UTC_TIMESTAMP(3) WHERE id=? AND tenant_id=?",
                    fields.get("title"),ZenflowRunAuthorization.hash(content),fragments.size(),metadata(fields,version),actor,documentId,tenant());
            jdbc.update("DELETE FROM ai_slice WHERE document_id=? AND tenant_id=?",documentId,tenant());slices(id(documentId),fragments,actor);
            if(Boolean.FALSE.equals(fields.get("approved")))invalidate(id(documentId));
            return detailInTransaction(documentId);
        });
    }
    public Map<String,Object> revoke(String documentId,Map<String,Object> body) {
        String actor=access.require().getId().toString();
        if(body==null || !body.keySet().equals(Set.of("expectedSourceVersion")))throw error(HttpStatus.BAD_REQUEST,"REFERENCE_INPUT_INVALID");
        return tx.execute(ignored->{var row=owned(id(documentId),true);expected(row,body);var metadata=readMetadata(row.get("document_config"));
            metadata.put("invelleCompanionApproved",false);metadata.put("invelleSourceVersion",Long.toString(IdWorker.getId()));
            jdbc.update("UPDATE ai_document SET document_config=?,update_by=?,update_time=UTC_TIMESTAMP(3) WHERE id=? AND tenant_id=?",serialize(metadata),actor,documentId,tenant());
            invalidate(id(documentId));return projection(owned(id(documentId),false));
        });
    }
    public Map<String,Object> chunks(String documentId) {
        access.require();return tx.execute(ignored->{var document=owned(id(documentId),true);
            return Map.of("sourceVersion",readMetadata(document.get("document_config")).get("invelleSourceVersion"),"items",jdbc.queryForList("SELECT id,content,char_count FROM ai_slice WHERE document_id=? AND tenant_id=? AND del_flag='0' ORDER BY id",documentId,tenant()).stream().map(row->Map.of("id",row.get("id").toString(),"content",row.get("content"),"characters",row.get("char_count"))).toList());});
    }
    public Map<String,Object> editChunk(String documentId,String chunkId,Map<String,Object> body) {return mutateChunk(documentId,chunkId,body,false);}
    public Map<String,Object> addChunk(String documentId,Map<String,Object> body) {return mutateChunk(documentId,null,body,false);}
    public Map<String,Object> deleteChunk(String documentId,String chunkId,Map<String,Object> body) {return mutateChunk(documentId,chunkId,body,true);}
    private Map<String,Object> mutateChunk(String documentId,String chunkId,Map<String,Object> body,boolean deleting) {
        String actor=access.require().getId().toString();
        if(body==null || !body.keySet().equals(deleting?Set.of("expectedSourceVersion"):Set.of("expectedSourceVersion","content")))throw error(HttpStatus.BAD_REQUEST,"REFERENCE_INPUT_INVALID");
        String content=deleting?"":text(body,"content",16000,true);
        return tx.execute(ignored->{var document=owned(id(documentId),true);expected(document,body);
            if(chunkId==null)slices(id(documentId),List.of(content),actor);
            else {id(chunkId);int changed=deleting?jdbc.update("DELETE FROM ai_slice WHERE id=? AND document_id=? AND tenant_id=?",chunkId,documentId,tenant()):jdbc.update("UPDATE ai_slice SET content=?,char_count=?,update_by=?,update_time=UTC_TIMESTAMP(3) WHERE id=? AND document_id=? AND tenant_id=? AND del_flag='0'",content,content.length(),actor,chunkId,documentId,tenant());if(changed!=1)throw error(HttpStatus.NOT_FOUND,"REFERENCE_CHUNK_NOT_FOUND");}
            var bodies=jdbc.queryForList("SELECT content FROM ai_slice WHERE document_id=? AND tenant_id=? AND del_flag='0' ORDER BY id",documentId,tenant());
            String joined=bodies.stream().map(row->row.get("content").toString()).collect(java.util.stream.Collectors.joining());
            if(bodies.isEmpty() || bodies.size()>1000 || joined.length()>400000)throw error(HttpStatus.CONFLICT,"REFERENCE_SOURCE_TOO_LARGE_OR_EMPTY");
            var metadata=readMetadata(document.get("document_config"));metadata.put("invelleSourceVersion",Long.toString(IdWorker.getId()));
            jdbc.update("UPDATE ai_document SET document_config=?,slice_count=?,file_hash=?,update_by=?,update_time=UTC_TIMESTAMP(3) WHERE id=? AND tenant_id=?",serialize(metadata),bodies.size(),ZenflowRunAuthorization.hash(joined),actor,documentId,tenant());
            return detailInTransaction(documentId);
        });
    }
    public void delete(String documentId,String sourceVersion) {
        access.require();tx.executeWithoutResult(ignored->{var row=owned(id(documentId),true);expected(row,Map.of("expectedSourceVersion",sourceVersion));invalidate(id(documentId));jdbc.update("UPDATE ai_document SET del_flag='1',update_time=UTC_TIMESTAMP(3) WHERE id=? AND tenant_id=?",documentId,tenant());jdbc.update("DELETE FROM ai_slice WHERE document_id=? AND tenant_id=?",documentId,tenant());});
    }
    public Map<String,Object> extract(Map<String,Object> body) {
        access.require();String filename=text(body,"filename",200,true),encoded=text(body,"base64",41943040,true);
        if(!filename.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(txt|md|pdf|docx)$"))throw error(HttpStatus.BAD_REQUEST,"REFERENCE_FILE_TYPE_INVALID");
        try {byte[] data=Base64.getDecoder().decode(encoded);if(data.length>30*1024*1024)throw new IllegalArgumentException();
            var tika=new org.apache.tika.Tika();tika.setMaxStringLength(400000);String content=tika.parseToString(new java.io.ByteArrayInputStream(data));
            if(content.isBlank())throw error(HttpStatus.BAD_REQUEST,"REFERENCE_FILE_NO_TEXT");return Map.of("title",filename,"content",content,"characters",content.length());
        }catch(ResponseStatusException rejected){throw rejected;}catch(Exception invalid){throw error(HttpStatus.BAD_REQUEST,"REFERENCE_FILE_UNREADABLE_OR_TOO_LARGE");}
    }
    private void invalidate(long document) {
        jdbc.update("UPDATE zf_reference_snapshot SET status='REVOKED',updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND document_id=?",tenant(),document);
        jdbc.update("UPDATE zf_run_source SET status='INVALIDATED',updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND reference_snapshot_id IN (SELECT id FROM zf_reference_snapshot WHERE tenant_id=? AND document_id=?)",tenant(),tenant(),document);
        jdbc.update("UPDATE zf_companion_run_context SET lineage_state='INVALIDATED',updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND run_id IN (SELECT run_id FROM zf_run_source WHERE tenant_id=? AND reference_snapshot_id IN (SELECT id FROM zf_reference_snapshot WHERE tenant_id=? AND document_id=?))",tenant(),tenant(),tenant(),document);
    }
    private Map<String,Object> owned(long document,boolean lock) {
        var rows=jdbc.queryForList("SELECT d.* FROM ai_document d JOIN ai_dataset k ON k.id=d.dataset_id AND k.tenant_id=d.tenant_id "
                +"WHERE d.id=? AND k.tenant_id=? AND k.collection_name=? AND d.del_flag='0' AND k.del_flag='0'"+(lock?" FOR UPDATE":""),document,tenant(),collection());
        if(rows.size()!=1)throw error(HttpStatus.NOT_FOUND,"REFERENCE_NOT_FOUND");return rows.get(0);
    }
    private Map<String,Object> projection(Map<String,Object> row) {
        var metadata=readMetadata(row.get("document_config"));var result=new LinkedHashMap<String,Object>();
        result.put("id",row.get("id").toString());result.put("title",row.get("name"));result.put("sourceVersion",metadata.get("invelleSourceVersion"));
        result.put("approved",Boolean.TRUE.equals(metadata.get("invelleCompanionApproved")));
        for(String field:List.of("kind","locale","citationVisibility","topic","sourceLocator"))result.put(field,metadata.get(field));
        result.put("sliceCount",row.get("slice_count"));result.put("updatedAt",row.get("update_time"));return result;
    }
    private Map<String,Object> normalize(Map<String,Object> body,boolean updating) {
        var allowed=new HashSet<>(Set.of("title","content","kind","locale","citationVisibility","topic","sourceLocator","approved"));
        if(updating)allowed.add("expectedSourceVersion");
        if(body==null || !allowed.containsAll(body.keySet()) || !(body.get("approved") instanceof Boolean))throw error(HttpStatus.BAD_REQUEST,"REFERENCE_INPUT_INVALID");
        var result=new LinkedHashMap<String,Object>();result.put("title",text(body,"title",200,true));result.put("content",text(body,"content",400000,true));
        for(var field:List.of("kind","locale","citationVisibility"))result.put(field,text(body,field,24,true));
        result.put("topic",text(body,"topic",200,false));result.put("sourceLocator",text(body,"sourceLocator",512,false));result.put("approved",body.get("approved"));
        try { ZenflowReferencePolicy.requireMetadata(result.get("kind").toString(),result.get("locale").toString(),result.get("citationVisibility").toString()); }
        catch(IllegalArgumentException invalid){throw error(HttpStatus.BAD_REQUEST,"REFERENCE_INPUT_INVALID");}
        return result;
    }
    private String metadata(Map<String,Object> fields,String version) {
        var result=new LinkedHashMap<>(fields);result.remove("title");result.remove("content");result.remove("approved");
        result.put("invelleAudience","PUBLIC");result.put("invelleCompanionApproved",fields.get("approved"));result.put("invelleSourceVersion",version);return serialize(result);
    }
    private void slices(long document,List<String> fragments,String actor) {
        jdbc.batchUpdate("INSERT INTO ai_slice(id,tenant_id,document_id,content,char_count,del_flag,slice_status,create_by,update_by,create_time,update_time) VALUES(?,?,?,?,?,'0','0',?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                fragments,50,(statement,fragment)-> {
                    statement.setLong(1,IdWorker.getId());statement.setLong(2,tenant());statement.setLong(3,document);
                    statement.setString(4,fragment);statement.setInt(5,fragment.length());statement.setString(6,actor);statement.setString(7,actor);
                });
    }
    private List<String> fragments(String content) {
        var fragments=new ArrayList<String>();
        for(int start=0;start<content.length();) {
            int end=Math.min(start+2000,content.length());
            if(end<content.length() && Character.isHighSurrogate(content.charAt(end-1)) && Character.isLowSurrogate(content.charAt(end)))end--;
            fragments.add(content.substring(start,end));start=end;
        }
        return List.copyOf(fragments);
    }
    private void expected(Map<String,Object> row,Map<String,Object> body) {
        if(!(body.get("expectedSourceVersion") instanceof String version) || !version.equals(readMetadata(row.get("document_config")).get("invelleSourceVersion")))throw error(HttpStatus.CONFLICT,"REFERENCE_VERSION_CONFLICT");
    }
    private Map<String,Object> readMetadata(Object value) {
        try{return json.readValue(Objects.toString(value,"{}"),new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String,Object>>(){});}
        catch(Exception invalid){throw error(HttpStatus.CONFLICT,"REFERENCE_METADATA_INVALID");}
    }
    private String serialize(Object value) {try{return json.writeValueAsString(value);}catch(Exception invalid){throw error(HttpStatus.BAD_REQUEST,"REFERENCE_INPUT_INVALID");}}
    private static String text(Map<String,Object> body,String field,int limit,boolean required) {
        Object value=body.get(field);if(value==null && !required)return "";
        if(!(value instanceof String s) || s.length()>limit || (required && s.isBlank()))throw error(HttpStatus.BAD_REQUEST,"REFERENCE_INPUT_INVALID");return (String)value;
    }
    private static long id(String value) {try {if(value==null || !value.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException();return Long.parseLong(value);}catch(Exception invalid){throw error(HttpStatus.BAD_REQUEST,"INVALID_ID");}}
    private long tenant(){return product.getTenantId();}
    private String collection(){return ZenflowReferenceSources.collection(tenant());}
    private static ResponseStatusException error(HttpStatus status,String code){return new ResponseStatusException(status,code);}
}
