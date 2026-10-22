package com.deepdimen.matrix.zenflow.garden;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ZenflowGardenControllerTest {
    @Test void httpStatusAndReceiptRemainConsistent()throws Exception{
        var facade=mock(ZenflowGardenFacade.class);
        var mvc=MockMvcBuilders.standaloneSetup(new ZenflowGardenController(facade)).build();
        var json=new ObjectMapper();
        for(String state:new String[]{"SUCCEEDED","CONFLICT","INVALID"}){
            String error="CONFLICT".equals(state)?"GARDEN_VERSION_CONFLICT":"INVALID".equals(state)?"INVALID_GARDEN_OPERATION":null;
            when(facade.patch(any())).thenReturn(new com.deepdimen.matrix.zenflow.operation.ZenflowOperationReceipts.Receipt("request_123","GARDEN_PATCH",error==null?"SUCCEEDED":"REJECTED",error==null?"GARDEN":null,error==null?"1":null,error==null?2:null,error));
            var response=mvc.perform(patch("/zenflow/v1/garden/state").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse();
            assertEquals("no-store",response.getHeader("Cache-Control"));
            assertEquals(error==null?200:"CONFLICT".equals(state)?409:400,response.getStatus());
            assertEquals("request_123",json.readTree(response.getContentAsString()).at("/data/clientRequestId").asText());
        }
    }
}
