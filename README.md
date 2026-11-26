# Gizwits Android SDK Demo

独立的 Android 示例 App，展示应用配置、产品管理、账号登录、蓝牙发现、设备配网和控制。
通过 Maven Central 使用 `io.github.gizwits` 的四个 SDK 模块，版本统一配置在
`gradle.properties` 的 `gizwitsSdkVersion` 中。

## 运行示例

1. 使用 Android Studio 打开本目录。Gradle 使用 JDK 17，项目编译使用 JDK 11 toolchain；安装 Android SDK 36，最低运行版本 Android 8.0（API 26）。
2. 将 `demo-secrets.properties.example` 复制为 `demo-secrets.properties`，填写开发者中心的 AppID 和 AppSecret。
3. 运行 `app`，或执行 `./gradlew :app:assembleDebug`。
4. 产品 PK、PS 可在 App 中录入或扫描产品二维码添加。

```properties
# demo-secrets.properties（不要提交，不要给值加引号）
GIZWITS_APP_ID=你的32位AppID
GIZWITS_APP_SECRET=你的32位AppSecret
```

也可以通过同名环境变量传入（优先于文件），适用于 CI Secrets。
没有有效配置时允许 Gradle Sync，但构建会给出明确提示，不生成带无效默认凭据的 APK。
真实配置、签名密钥、`sentry.properties`、`.env`、构建产物都被 Git 忽略；只提交 `.example` 模板。
这些配置最终会被打包到 APK；不提交 Git 不等于能防止 APK 反编译。

## 签名和发布

Debug 自动使用 Android 调试签名。需要正式签名时，复制
`app/release-signing.properties.example` 为 `app/release-signing.properties`，
填写密钥位置和密码。密钥和真实配置都不要提交。没有签名配置时 Release 输出未签名 APK。
产物位于 `app/build/outputs/apk/`。

可选 App 更新发布脚本见 [发布说明](docs/app-updates.md)。更新服务地址默认留空，
不使用更新服务也能运行示例。`scripts/release/.env` 仅保存在本地。
可选离线产品协议 JSON 放在 `local-assets/productConfig/`，该目录不提交；
普通示例通过 SDK 在线获取产品协议。

## 检查

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 -m unittest discover -s tests/release
git status --short --ignored
```

提交前检查 `git ls-files`，不要使用 `git add -f` 强制加入忽略文件。
