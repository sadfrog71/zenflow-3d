### 用户问题
{userInput}
### 表结构信息
{tableSchema}
### 已知信息(术语同义词)
{terminologies}
### 输出限制
1. 字段限定：只能使用数据库表结构中已定义的字段，禁止使用未被指定的额外列。
2. SQL 查询结果字段应使用 AS 关键字附加字段注释，以提高可读性。
3. 如果表结构中包含 del_flag ，那么过滤条件del_flag为0
4. 如果表结构中包含 tenant_id ，那么过滤条件tenant_id为{tenantId}
5. 查询结果仅显示前十条记录。

