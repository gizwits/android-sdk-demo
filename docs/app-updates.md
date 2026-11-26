# Android App 更新与发布

仅更新 demo App（`com.xpg.wifidemo`），不改变 SDK Maven 发布流程。

## 客户端行为

- 启动时检查一次；接入设置 → 支持与维护 → 检查更新可主动检查。
- 普通更新同一构建号每天最多自动提示一次；后台配置的最低构建号高于本机时要求更新。
- 网络错误不影响正常启动，手动检查显示错误。未配置更新服务时不联网，手动检查显示“暂未配置更新服务”。
- 下载至应用私有缓存，校验大小、SHA256、包名、版本号、构建号和签名，再用 FileProvider 打开系统安装器。
- 首次需要用户允许“安装未知应用”；返回后继续安装。取消授权可以重试，安装器取消后仍可再次安装。
- 只接受相同签名的覆盖更新。当前实现不支持签名轮换；Debug 签名与正式签名不同，不能通过此流程互相覆盖。
- 下载随 Activity 的 ViewModel 保留，进程被结束后重新检查和下载；没有后台下载通知或断点续传。

构建配置支持环境变量 `APP_UPDATE_BASE_URL`、`APP_UPDATE_CHANNEL=stable|beta`；也支持 Gradle 参数 `-PappUpdateBaseUrl=… -PappUpdateChannel=…`。地址是后端根路径，包含代理前缀，例如 `https://logs.example.com/api`，客户端自动追加 `/updates/mobile`。

默认服务地址为空。仅本地/受控测试环境可通过 `APP_UPDATE_ALLOW_HTTP=true` 允许 HTTP 检查接口，APK 下载仍强制 HTTPS。不允许更新地址重定向，请配置最终地址。

## 准备发布

复制 `scripts/release/.env.example` 为同目录 `.env` 并填写：

- `APP_UPDATE_BASE_URL`：客户端检查地址；`SDK_LOG_API_URL` 可覆盖发布 API 地址。
- `MOBILE_RELEASE_TOKEN`：至少 32 字符，与后端一致。
- `ANDROID_KEYSTORE`、`ANDROID_KEY_ALIAS`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_PASSWORD`：与既有正式版相同的签名。
- COS 配置沿用项目的 `COS_SECRET_ID`、`COS_SECRET_KEY`、`COS_BUCKET_NAME`、`APP_ID`、`COS_REGION`。安装包对象需要可以匿名通过 HTTPS 下载；脚本不会修改桶权限。

`.env` 和发布产物已忽略，签名密码通过环境传给 apksigner，不放入命令行参数。Python 需要 3.9+，上传依赖可安装到独立虚拟环境：

```sh
python3 -m venv /tmp/mobile-release-venv
/tmp/mobile-release-venv/bin/pip install -r scripts/release/requirements.txt
```

直接执行 Gradle Release 构建时可使用本地 `app/release-signing.properties`，其中填写 `MYAPP_RELEASE_STORE_FILE`、`MYAPP_RELEASE_STORE_PASSWORD`、`MYAPP_RELEASE_KEY_ALIAS`、`MYAPP_RELEASE_KEY_PASSWORD`。该文件与本地密钥均被忽略。发布脚本使用 `-PskipLocalReleaseSigning=true` 生成未签名 APK，再按 `.env` 指定的正式密钥签名。

## 构建、上传、发布

构建号必须递增，脚本用 Gradle 参数写入真实 APK，不改仓库中的默认版本。

```sh
python3 scripts/release/release.py build --version 1.1.2 --build 4 --notes-file release-notes.txt
/tmp/mobile-release-venv/bin/python scripts/release/release.py upload release/mobile/1.1.2-4/stable --register
```

构建默认使用 stable；测试渠道加 `--channel beta`。需要强制旧版升级时加 `--min-build 4`（默认 0，不限制）。`--offline` 使用已有 Gradle 缓存，`--output-directory` 可指定全新产物目录。

脚本执行 `assembleRelease`、16 KB 对齐、apksigner 签名及验证，输出 `app.apk` 和 `manifest.json`。输出目录已存在会拒绝覆盖。上传使用包含 APK SHA256 的 COS 对象路径，完成匿名下载校验后才能登记后台草稿；同一构建重复登记仅在内容完全一致时成功。

在后台“嵌入式 / 移动应用更新”确认信息后发布，或使用返回的版本 ID：

```sh
python3 scripts/release/release.py publish 123
python3 scripts/release/release.py withdraw 123
```

已有上传产物可以执行 `register <产物目录>` 重试登记。下架停止后续更新提示，不会降级已安装应用。

## 验证

```sh
python3 -m unittest discover -s tests/release -v
./gradlew :app:assembleDebug :app:testDebugUnitTest --offline -Pkotlin.compiler.execution.strategy=in-process
```

当前 Kotlin/KAPT 在此机器使用独立编译 daemon 会遇到 JDK 模块访问错误；发布脚本已使用 `in-process` 模式，沿用项目现有 JVM 参数。

上线验收需要两个使用同一正式签名的 APK：安装低版本 → 后台发布高版本 → 检查 → 下载 → 授权 → 覆盖安装，并核对已有 SDK 配置和登录数据保留。另需验证取消权限、下载失败、错误哈希/签名和强制更新。

iOS 客户端和 iOS 发布脚本暂缓，等待 UI 重构完成；后端已经支持 iOS App Store 版本草稿和检查协议。
