-- 仅用于新的此间独立空库。假定 sys_tenant / app_role / OAuth client 主键 1 未占用；执行前核查。
-- 无管理员、演示消费者、密码或旧产品配置。域名和 SMTP 不在数据库种子中。
INSERT INTO sys_tenant(id,name,code,website_name,status,del_flag,menu_id,start_time,end_time)
VALUES (1,'此间','zenflow','此间','0','0','',UTC_TIMESTAMP(),DATE_ADD(UTC_TIMESTAMP(),INTERVAL 100 YEAR));
INSERT INTO app_role(role_id,role_name,role_code,role_desc,tenant_id,menu_id,del_flag)
VALUES (1,'此间使用者','ZF_CONSUMER','此间 App 消费者',1,'','0');
INSERT INTO sys_oauth_client_details(id,client_id,client_secret,scope,authorized_grant_types,
    web_server_redirect_uri,access_token_validity,refresh_token_validity,additional_information,
    autoapprove,del_flag,tenant_id)
VALUES (1,'zenflow-ios',NULL,'zenflow','authorization_code,refresh_token',
    'zenflow://auth/callback',3600,2592000,'{"enc_flag":"0","captcha_flag":"0"}',
    'true','0',1);
