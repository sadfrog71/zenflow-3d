package com.deepdimen.matrix.zenflow.admin;

import com.deepdimen.matrix.MatrixBootApplication;
import org.springframework.boot.SpringApplication;

/** Runs the test-only controlled transport beside the dedicated MySQL server, without a public port. */
public class InvelleCoreIntegrationMain {
    public static void main(String[] args) {
        if(!"1".equals(System.getenv("INVELLE_CORE_INTEGRATION")))throw new IllegalStateException("INTEGRATION_ENV_REQUIRED");
        var app=new SpringApplication(MatrixBootApplication.class,InvelleCoreIntegrationTest.Controlled.class);
        app.setDefaultProperties(java.util.Map.of("server.port",0,"zenflow.core.cleanup-enabled",false,"spring.data.redis.database",2));
        int exit=1;
        try(var context=app.run(args)) {
            var test=new InvelleCoreIntegrationTest();context.getAutowireCapableBeanFactory().autowireBean(test);
            test.realManagedSkillAndEnginePersistCompleteWhileDeniedPrivateContextNeverInvokesTransport();
            System.out.println("INVELLE_CONTROLLED_CHAIN_PASS: native stream, durable Skill SUCCEEDED, Engine COMPLETED, one invocation, retry, private-egress refusal");exit=0;
        }catch(Throwable failure){System.out.println("INVELLE_CONTROLLED_CHAIN_FAIL: "+failure.getClass().getSimpleName());
            for(var frame:failure.getStackTrace())if(frame.getClassName().startsWith("com.deepdimen"))System.out.println("QA_FAILURE_FRAME "+frame.getClassName()+"."+frame.getMethodName()+":"+frame.getLineNumber());
        }
        System.exit(exit);
    }
}
