# AGENTS.md

`zrlog-base` 是 ZrLog 的共享基础库。先读本文件，再按任务读取源码和测试。若本地存在 `.codex/AGENTS.md`
，同时读取其中的工作区约定；该目录不提交到仓库。

## 工程职责

- `zrlog-common/`：通用配置、响应、异常、Web 基础设施、工具和宿主适配。
- `zrlog-data/`：数据模型、数据服务和缓存。
- `zrlog-service/`：公共业务服务、插件宿主和升级调度。
- `zrlog-template/`：模板页面对象和渲染辅助。
- `zrlog-admin-token/`：后台 token 服务。
- `zrlog-test-support/`：仅供测试使用的隔离工具。

这里的公共接口由 admin、blog、install、主工程和插件消费。修改前检查调用方和兼容性；单个页面的业务留在所属工程。

## 构建与验证

```bash
./mvnw -q spotless:apply
./mvnw -q spotless:check
./mvnw -q test
./mvnw -q -DskipTests install
```

在仓库根目录使用 JDK 21 或更高版本运行格式化命令。所有模块的 Java 源码（含测试和 Java 21 多版本源码）统一使用
Google Java Format AOSP 四空格风格；提交前运行 `spotless:apply`。根工程的 Maven `validate` 阶段会自动检查格式。

涉及公共接口或依赖变化时，在受影响的消费者中验证。保留已有工作区改动，不提交构建输出和本地配置。
