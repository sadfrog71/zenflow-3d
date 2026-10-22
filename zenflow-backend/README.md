# 此间服务端

这是从锁定的 MATRIX 参考提交派生的独立产品目录，随上层此间仓库连接私有 GitHub 远端。复用认证、账号与 RBAC，以及助手运行引擎、托管 Skill 和胶囊记录能力；产品身份、授权和业务接口放在 `zenflow/zenflow-biz`。知识资料继续使用原存储结构。服务进程按产品需要装配能力，并用路径白名单阻止旧企业接口对外提供服务。详细来源见 `SOURCE_BASELINE.md`；当前部署与功能缺项见 `../docs/Invelle核心功能开发状态-20261006.md`。

## 本地配置

启动前需准备独立的 MySQL 8 数据库和 Redis，不得连接衍智云环境。配置项见 `matrix-boot/src/main/resources/application-zenflow.yml`：`ZENFLOW_DB_URL`、`ZENFLOW_DB_USER`、`ZENFLOW_DB_PASSWORD`、`ZENFLOW_REDIS_HOST`、`ZENFLOW_REDIS_PASSWORD`、`ZENFLOW_TENANT_ID`、`ZENFLOW_OAUTH_CLIENT_ID`、`ZENFLOW_AUTH_HMAC_KEY`、`ZENFLOW_PUBLIC_BASE_URL`、`ZENFLOW_OAUTH_ISSUER`。邮件通道就绪后另设 `spring.mail.*` 与 `ZENFLOW_EMAIL_FROM`；缺失时发送验证码明确返回 `MAIL_UNAVAILABLE`。

`db/zenflow/B00_schema_review.sql` → `B01_identity_p0.sql` → `B00_product_seed_review.sql` → `B02_external_identity.sql` 已于 2026-10-06 在独立本地 MySQL 空库依序验证通过；尚未在生产执行。B00 只抽取参考表结构，不含旧账号、密钥和演示数据。重新生成需通过 `generate-b00.py --source-root <参考数据库脚本目录>` 指定参考结构。

## 本地检查

```sh
mvn -pl zenflow/zenflow-biz,matrix-boot -am test-compile -Pboot -DskipTests -Dspring-javaformat.skip=true
mvn -pl zenflow/zenflow-biz -am test -Dtest=EmailIdentityTest,ZenflowUserDetailsServiceTest,ZenflowEndpointBoundaryFilterTest -Dsurefire.failIfNoSpecifiedTests=false -Dspring-javaformat.skip=true
```

App 与网关的路径映射遵循 `docs/前后端实施设计-v0.3.md`；正式域名未定时，不能把本地直连当成网关联调。

## 海外登录（2026-10-06）

Google、Facebook Limited Login、Apple 登录已接入消费者账号中心，公开 ID 与私有密钥配置见 `.env.example`。Google 私有 Web client secret 只在后台使用。缺配置时明确返回 PROVIDER_NOT_CONFIGURED。真实提供方联调尚未完成，Apple 撤销/销号仍待补齐；状态与本地运行限制见 `../docs/海外登录开发与本地验收-20261006.md`。

## ubuntu-sg 测试部署

独立测试服务已部署。部署文件与操作说明见 `../deploy/ubuntu-sg/README.md`，远端验证见 `../docs/服务端测试部署-20261006.md`。入口通过 SSH 隧道访问，邮件仅进入测试收件箱；未作为生产上线。
