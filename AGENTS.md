# AGENTS.md

这份文档是 AI Agent 在 `zrlog-base` 工程内工作的入口规则。进入本仓库后，先读本文件，再按任务打开具体模块源码、测试和 `zrlog-ops/docs/repository-structure-guide.md`。

## 工程定位

`zrlog-base` 是 ZrLog 多仓库体系里的共享基础库，主要负责：

- 通用配置、异常、响应结构、i18n、工具类和 Web 基础拦截器。
- 数据模型、DAO 相关服务、缓存基础能力和站点公共数据处理。
- 插件宿主侧基础接口、静态站点能力、升级流程和公共业务服务。
- 模板页面对象、模板渲染辅助和后台 token 基础能力。

这里的 public API 会被 `zrlog-admin-web`、`zrlog-blog-web`、`zrlog-install-web`、`zrlog` 和插件仓库消费。修改前必须先确认影响面，不要把某个上层工程的临时需求直接塞进共享层。

## 模块职责

| 路径 | 职责 |
| --- | --- |
| `zrlog-test-support/` | 仅供测试使用的数据库隔离、日志捕获和内存运行目录工具；不承载功能夹具。 |
| `zrlog-common/` | 通用工具、常量、响应结构、Web 基础设施、升级处理、插件基础接口和 native-image 注册入口。 |
| `zrlog-data/` | 数据模型、数据服务、缓存实现、站点数据工具和数据库相关 DTO。 |
| `zrlog-service/` | 公共业务服务、插件宿主进程/状态、升级业务和跨模块业务 DTO。 |
| `zrlog-template/` | 前台模板页面对象、模板渲染工具、静态站点模板服务和模板资源辅助。 |
| `zrlog-admin-token/` | 后台 token 服务和 token 线程上下文。 |
| `shell/` | 发布、版本或辅助脚本，执行前先读脚本内容。 |

## 构建与验证

常用命令：

```bash
mvn -q -DskipTests compile
mvn -q test
mvn -q -DskipTests install
```

修改共享 API、DTO、模型、插件宿主或模板对象时，至少运行 `mvn -q test`。如果下游工程需要消费本地改动，先在本仓库运行 `mvn -q -DskipTests install`，再到对应消费者仓库验证。

## 边界规则

- 共享层只沉淀稳定公共能力，不承载 admin/blog/install 的页面级临时逻辑。
- 固定结构请求、响应、缓存对象和页面对象优先使用 typed DTO，不要用 `Map` 扩散协议。
- 修改 JSON 响应、DTO 字段、异常码或页面对象时，先检查下游引用和兼容语义。
- 新增会被 Gson/native-image 访问的类型时，检查 `ZrLogBaseNativeImageUtils` 等注册入口。
- 插件宿主只负责协议、进程、通道、状态和公共能力；插件业务仍归插件仓库。
- URL、IP、context path、安全校验和路径归一化属于高风险公共能力，必须补异常输入测试。
- 不提交 `target/`、本地日志、临时配置或构建输出。

## AI 修改流程

1. 先判断变更是否真的属于共享基础库；如果只服务单个上层页面，优先回到对应仓库。
2. 读取真实调用链，包括上游 service/util、下游消费者、测试和发布版本关系。
3. 保留工作区已有用户改动，不 reset、restore 或覆盖无关文件。
4. 修改 public API 时记录受影响消费者，并在消费者仓库做最小验证。
5. 修改 DTO/native/插件协议时同步检查 `zrlog-ops/docs/ai-stability-guardrails.md` 和稳定性矩阵。
6. 最终回复说明共享边界、兼容性判断、验证命令和需要下游继续处理的点。

## 常见任务入口

| 任务 | 起点 |
| --- | --- |
| 通用响应或异常 | `zrlog-common/src/main/java/com/zrlog/common` |
| IP、URL、i18n、context path 工具 | `zrlog-common/src/main/java/com/zrlog/util` 和 `com/zrlog/blog/web/util` |
| 数据模型或站点数据服务 | `zrlog-data/src/main/java/com/zrlog/model`、`com/zrlog/data` |
| 公共业务服务或升级流程 | `zrlog-service/src/main/java/com/zrlog/business` |
| 插件宿主侧协议 | `zrlog-service/src/main/java/com/zrlog/business/plugin` 和 `zrlog-common/src/main/java/com/zrlog/plugin` |
| 模板页面对象 | `zrlog-template/src/main/java/com/zrlog/blog/web/template/vo` |
| native-image 注册 | `zrlog-common/src/main/java/com/zrlog/util/ZrLogBaseNativeImageUtils.java` |

## 共享测试标准

遵守 [测试支持约定](docs/test-support.md)。data/service 使用 `com.zrlog.test.support.ZrLogTestDatabase`，不要再复制 H2/SQLite 建库、schema 加载和清理代码。共享测试构件必须以 test scope 消费，不得进入生产依赖；schema 来自 install-web，业务种子数据归消费者，安装链路仍由真实 InstallService 验证。
