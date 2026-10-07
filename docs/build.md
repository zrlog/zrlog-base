# 构建与发布

日常验证使用 `./mvnw test`；构建多版本 JAR 需要 JDK 21 或更新版本，CI 使用 JDK 21。需要供下游验证时运行 `./mvnw install`。

## 开发快照

`main` 的发布工作流使用 `./mvnw -B -ntp -U -Psnapshot clean deploy`，执行完整测试后发布 7 个 Maven 坐标。`-U` 保留，用于刷新安装工程等上游快照。

`snapshot` profile 使用 Maven Deploy 3.2.0 的 `deployAtEnd`，全部模块成功后才开始上传。父 POM、6 个模块 JAR 和 6 个源码包均保留，包括消费者测试需要的 `zrlog-test-support`；快照不生成 Javadoc 或 GPG 签名。每个坐标只更新一次版本 metadata，各附件使用同一时间戳。

该 profile 需要显式启用，发布版号会被 Maven Deploy 跳过。它的配置可被子工程继承，子工程若重新声明插件执行阶段，必须核对合并后的有效 POM，避免重新启用快照 Javadoc 或 Central 发布扩展。

Maven 3.10 起会校验发布凭证适用的仓库 origin（协议、主机和端口）。工作流生成的 `settings.xml` 使用 1.3.0 schema，并在 `central` server 的 `repositoryOrigins` 中显式声明 `https://central.sonatype.com`。否则，`central` 会只关联默认下载仓库 `https://repo.maven.apache.org`，快照上传不会携带凭证并返回 HTTP 401。此时日志会出现 `Not using credentials of server 'central'`，应先检查 origin 配置；仅向文件仓库部署无法覆盖该认证行为。详见 [Maven 3.10.0 发布说明](https://maven.apache.org/docs/3.10.0/release-notes.html#repository-credentials-are-scoped)。

## 正式版

`v*` tag 使用不带 `snapshot` profile 的 `clean deploy`，保留 Javadoc、源码、GPG 签名和 Central Publishing bundle 流程。仅 tag 构建导入 GPG 私钥。`main` 分支仍只在测试和部署成功后通知预览构建。

## 本地部署验证

使用临时文件仓库检查实际发布内容，无需发布凭证：

```bash
SNAPSHOT_CHECK_DIR=$(mktemp -d)
./mvnw -B -Psnapshot clean deploy \
  "-DaltSnapshotDeploymentRepository=snapshot-check::file://${SNAPSHOT_CHECK_DIR}"
```

检查全部 7 个坐标的 POM、模块 JAR、sources 和版本 metadata；最后一个模块测试失败时，目标仓库应为空。共享父 POM 有变化时，同时检查 blog 和 admin 的发布配置。

2026-09-30 验证：471 项测试通过，本地完整快照构建与部署耗时 19.0 秒；发布附件由 50 个减少到 19 个，另有 14 次 metadata 上传。消费者从清除相关坐标后的独立缓存重新解析，并验证末模块失败不上传、正式版仍绑定 Javadoc/GPG/Central。此前 CI Maven 阶段为 182 秒，其中末尾发布等待约 116 秒；本地文件仓库不包含 Sonatype 网络耗时，实际提速需由后续 CI 确认。
