# Invelle 管理端来源

来源：references/matrix/matrix-ui，锁定提交15cfb67c（完整值见references/matrix/baseline.json）。沿用Vue、Element Plus、Pinia、Axios及Vite的Composition API模式，裁剪企业菜单与旧域名，新增产品门面。没有复制旧凭据或业务数据。现有企业模型页依赖旧租户与菜单API，故按本产品受限接口做最小独立页面，而非开放旧管理路由。框架版权与许可适用范围沿用参考工程。

新后台自身的Vue采用3.5.43、Axios采用1.20.0安全补丁版本（npm audit明确通告），没有变更参考工程或其他项目。Element Plus/Pinia和构建工具暂沿用基线；剩余开发依赖风险在本轮部署前审查。移除未使用的Vitest依赖，页面验收使用真实浏览器。
