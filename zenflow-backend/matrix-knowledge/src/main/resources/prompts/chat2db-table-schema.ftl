<#list tables as table>
    ### 数据库类型: ${table.dbType!}
    ### 表名：**${table.tableName}**
    注释：${table.tableComment!}
    ### 数据列：
    | 名称 | 数据类型 | 注释 |
    |------|------|------|
    <#list table.fields as field>
        |${field.fieldName} | ${field.fieldType} | ${field.fieldComment!} |
    </#list>
</#list>
