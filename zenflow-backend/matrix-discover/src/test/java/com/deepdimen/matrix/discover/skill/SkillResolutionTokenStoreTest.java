package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class SkillResolutionTokenStoreTest {

	@Test
	@SuppressWarnings("unchecked")
	void tokenBindsSnapshotIdentityConsumerAndExpires() throws Exception {
		var redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		var backing = new HashMap<String, String>();
		when(values.setIfAbsent(any(), any(), any(Duration.class))).thenAnswer(call -> {
			assertEquals(Duration.ofMinutes(5), call.getArgument(2));
			backing.put(call.getArgument(0), call.getArgument(1));
			return true;
		});
		when(values.get(any())).thenAnswer(call -> backing.get(call.getArgument(0)));
		var json = new ObjectMapper();
        var httpModule=new com.fasterxml.jackson.databind.module.SimpleModule();
        httpModule.addDeserializer(String.class,new com.fasterxml.jackson.databind.JsonDeserializer<String>() {
            @Override public String deserialize(com.fasterxml.jackson.core.JsonParser parser,com.fasterxml.jackson.databind.DeserializationContext context) throws java.io.IOException{return parser.getValueAsString().trim();}
        });
        json.registerModule(httpModule);
		var store = new SkillResolutionTokenStore(redis, json);
		var identity = new Identity("42", "3", "3", "run");
		var consumer = new Consumer("WORK_BRIEF", "7", "WORK_BRIEF", "default");
		var snapshot = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, null, null), "{}", "{}", "  frozen\n", List.of(), "{}", List.of());
		var resolved = store.issue(identity, consumer, "business", "{}", snapshot);
		assertEquals(43, resolved.resolvedSnapshotToken().length());
		assertEquals(snapshot, store.require(resolved.resolvedSnapshotToken(), identity, consumer).snapshot());
		assertThrows(IllegalArgumentException.class,
				() -> store.require(resolved.resolvedSnapshotToken(), new Identity("42", "4", "3", "run"), consumer));
		assertThrows(IllegalArgumentException.class, () -> store.require(resolved.resolvedSnapshotToken(), identity,
				new Consumer("WORK_BRIEF", "8", "WORK_BRIEF", "default")));
		String key = backing.keySet().iterator().next();
		var stale = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(backing.get(key));
		stale.put("expiresAtEpochMillis", 0);
		backing.put(key, stale.toString());
		assertThrows(IllegalArgumentException.class,
				() -> store.require(resolved.resolvedSnapshotToken(), identity, consumer));
	}

}
