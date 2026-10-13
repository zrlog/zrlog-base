# CLI 调用来源

`UserAgentUtils.parse` 支持标准 HTTP product 形式的 CLI 调用来源：

```text
User-Agent: zrlogctl/0.1.42 dot/1.2-beta.1
User-Agent: zrlogctl/0.1.42 muse
```

`zrlogctl/<版本>` 后紧接的第一个 product 表示调用 CLI 的 agent。名称和版本各限 1–128 个 ASCII HTTP token 字符；版本可省略。不枚举厂商或 agent 名称，不从操作系统注释、后续 product 或缺失信息中推测调用者。

解析结果保留 `browser=ZrLogCtl` 和 CLI 的 `browserVersion`，另提供可空的 `agentName`、`agentVersion`。原有构造函数与 getter 保持可用；未配置 agent 的 CLI 和浏览器请求返回空的 agent 字段。CLI 调用不因自定义 agent 名称包含 `bot` 等字样被判为网络爬虫。

基础库只负责解析协议。配置来自 `zrlog-client-java`；持久化和后台展示由 `zrlog-admin-web` 负责。后台操作日志增加可选字段，不涉及数据库表结构变更；读取和再次写入历史日志时保留这些字段，旧日志无需迁移。

User-Agent 为调用方自报信息，不能作为认证、账号归属或权限依据。调用方未声明 agent 时，只能确定使用了 zrlogctl，不能确定是哪一个 agent 或人工操作。
