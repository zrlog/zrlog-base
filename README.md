# ZrLog-Base

`zrlog-base` 是 ZrLog 的共享基础库，提供通用响应、异常、工具、数据模型、公共服务、模板对象、插件宿主接口和后台 token 能力。

## 模块

- `zrlog-common`：通用能力与宿主适配。
- `zrlog-data`：数据模型、服务和缓存。
- `zrlog-service`：公共业务服务。
- `zrlog-template`：模板对象与辅助能力。
- `zrlog-admin-token`：后台 token 服务。
- `zrlog-test-support`：测试隔离工具，仅以 test scope 使用。

[CLI 调用来源](docs/user-agent.md)介绍 HTTP User-Agent 的接入格式。仓库职责和最小验证命令见 [AGENTS.md](AGENTS.md)。
