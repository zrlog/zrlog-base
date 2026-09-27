# 共享测试支持

`zrlog-test-support` 是 base 仓库中的测试构件，消费者必须使用 `test` scope。它不引用 admin/blog/install 的业务实现；生产 JAR、starter 和 ZIP/WAR 均不得携带它。

## 实施范围

- 合并 base/data、base/service、admin、blog、主工程的数据库夹具，统一 H2、SQLite、DAO 恢复和临时文件清理。
- 共享 SQL 使用 install-web 的真实 schema 资源，不复制表定义，不通过补列掩盖版本不匹配。
- admin 的身份、资源和业务种子数据保留在 admin-common 的测试源码，通过只包含 support 包的 tests classifier 供后台测试复用。
- MFA 专用夹具归 admin-common 测试，日志捕获归共享构件。移除 admin-test-support 模块。
- admin/blog 的 MemoryApplication 保持在测试源码，复用 MemoryRuntime 的端口校验、项目定位和安全目录重置；安装仍调用 InstallService，模块装配和主题评审内容留在消费者。
- 安装工程本身使用 common-dao 的内存数据库原语和真实 InstallService 测试；不反向依赖消费安装 SQL 的 base 测试构件，保持 install → base 的构建顺序。

## 使用约定

数据库 service/model 测试使用 `try (ZrLogTestDatabase db = ZrLogTestDatabase.open())`；需要数据库兼容性时参数化 `DatabaseType.H2/SQLITE`。`openWebApi()` 仅模拟 D1 的单语句语义，不代表真实远端 D1 验收。

共享数据库只加载 schema，不预填业务数据；业务断言所需记录由各工程的测试夹具准备。它会替换 DAO 的进程级数据源，必须顺序关闭，不能在同一 JVM 并行运行修改该全局状态的用例。工程自己的 Constants、ThreadLocal 和静态配置也必须在 finally 中恢复。

安装/启动测试必须走真实 InstallService 并断言生成的配置、锁和种子数据。不得用数据库 schema 夹具手写 install.lock 冒充安装成功。普通配置读取测试可准备特定文件输入，但不能作为安装验收证据。

MemoryRuntime 只重置指定项目的 `.zrlog-memory` 子目录，拒绝符号链接根目录，不覆盖项目原有 `conf/db.properties` 和 `conf/install.lock`。使用 MemoryApplication 的自动化测试应在临时项目目录运行，以免重置开发者正在使用的预览环境。

跨仓库验证顺序：base `./mvnw test install`，然后 admin、blog、主工程测试与产物检查。安装工程的原有安装测试作为 schema 生产者验证。发布构件清单同步维护于 ops。

## 2026-09-27 验证记录

- base `./mvnw -q test install`：468 项通过，包含新增的隔离、DAO 恢复、SQLite 清理、D1 adapter 和内存目录安全测试。
- admin `./mvnw -q clean install`：601 项通过，清理旧 class 后验证测试源码入口及 tests classifier；API 无 UI classpath 与功能禁用组合仍通过。
- blog `./mvnw -q clean verify`：128 项通过，保留原覆盖率门槛；主工程 `./mvnw -q -pl zrlog-web -am test`：44 项通过。
- install-web `./mvnw -q test`：175 项中 171 项通过、4 项按原参数化规则跳过（仅适用 SQLite 的测试在 H2 参数下不执行）。安装源码和 SQL 未改动。
- ops 发布执行器 26 项测试及 `scripts/check-repository-structure.sh` 通过；新增 tests classifier 的 Central 核验，保留原发布顺序，未执行发布。
- admin/blog 的 test classpath 内存入口在临时项目目录真实启动成功：安装配置和锁均由 InstallService 生成，后台登录/文章接口、博客页面通过。
- 隔离构建的 admin/blog starter、主工程 ZIP/WAR 均确认不含共享测试构件、tests JAR 或 MemoryApplication。主工程包契约还继续验证普通 JDK 不携带 Polyglot/Hexo。
- 博客和主工程采用 `/tmp` 中经官方 SHA-256 校验的 Temurin JDK 21，避开本机 JDK 25 与既有 JaCoCo/Mockito 的兼容问题；未改全局 JDK 或项目 Java 11 源码目标。

本地包日志在 `/tmp/zrlog-test-support-packages-7bbiejjk/`，内存启动日志在 `/tmp/zrlog-test-support-memory-i6as324p/`。本次未修改生产 API、数据库 schema、Native 注册或前端页面；测试构件不进入产品运行时。
