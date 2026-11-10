# KGAutoCheckin-Android

酷狗概念版 VIP 自动签到 Android 客户端（KGAC）。

原生 Kotlin + Jetpack Compose 构建，直连酷狗客户端接口完成每日签到：听歌领取、广告领取、一天概念 VIP 领取与超级 VIP 升级。签名与加密算法内置，安装即用，无需任何配置。

## 功能

- **多账号管理**：扫码 / 手机验证码两种登录方式，账号凭据加密存储
- **手动签到**：一键执行，终端风格实时日志，随时可取消
- **每日定时**：精确闹钟 + 前台服务，息屏准点执行，通知栏实时显示进度
- **打开即签**：启动 APP 自动补签当日未签账号
- **签到日历**：月历视图、打卡统计、会员到期展示，支持从服务端同步历史记录
- **界面主题**：Material 3，壁纸取色 + 内置配色，日间 / 夜间 / 跟随系统
- **应用内更新**：检查 GitHub Release 新版本并给出下载链接

## 可靠性设计

定时场景做了完整的健壮性处理：

- **精确闹钟**：`USE_EXACT_ALARM` + `SCHEDULE_EXACT_ALARM` 双声明，Android 14+ 无需手动授权即可息屏准点触发
- **冷启动账号就绪**：APP 被杀后闹钟冷启动进程时，先同步等待加密账号仓库加载完成再执行，杜绝“暂无已登录账号”的启动竞态
- **网络就绪等待**：触发后等待网络恢复（最多 60 秒）再执行，避免 Wi-Fi 尚未重连导致请求全部失败
- **当日幂等**：全部账号已签到时自动跳过，重复触发不会产生多余执行
- **跳过留痕**：定时触发但无需执行（当日已签过/暂无账号/避让手动执行）时，通知栏留下静音低优先级提醒，被划掉后台的场景下触发同样可见
- **可靠的续排**：闹钟续排在进程级作用域完成，服务销毁不丢失次日调度
- **全流程日志持久化**：无论手动还是定时执行，重进 APP 都能回看完整过程

## 架构

单模块原生应用，分层清晰：

```
UI（Compose M3）      签到页 / 签到日历 / 更多设置
ViewModel（MVVM）     StateFlow 驱动 UI 状态
Repository            登录流程、签到流程、历史同步
KgApi                 酷狗接口客户端（签名 / 加密 / 请求重试）
本地存储              Room（签到记录）/ DataStore（设置）/ AndroidKeyStore（账号加密）
调度                  AlarmManager 精确定时 + 前台服务（dataSync）
```

| 层 | 技术 |
|---|---|
| 语言 | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM（StateFlow）+ Repository |
| 存储 | Room 2.7 / DataStore / AndroidKeyStore AES-256-GCM |
| 网络 | OkHttp 4.12 |
| 后台 | AlarmManager 精确闹钟 + 前台服务 |
| 兼容 | Android 8.0 ~ 17（minSdk 26 / targetSdk 36） |

## 下载

到 [Releases](../../releases) 页面下载 APK 安装，要求 Android 8.0 及以上。

部分机型（MIUI 等）建议在系统设置中将本应用省电策略设为「无限制」并允许自启动，以保证息屏时准点执行。

## 构建

```bash
git clone https://github.com/LucasLu-97/KGAutoCheckin-Android.git
cd KGAutoCheckin-Android
gradlew assembleRelease
```

需要 JDK 17+ 与 Android SDK（platforms;android-36），产物在 `app/build/outputs/apk/release/`。

## 自动发布

推送 `v*` 形式的 tag（如 `v1.0.8`）即触发 GitHub Actions 自动构建 release APK 并发布到 [Releases](../../releases)：

```bash
git tag v1.0.8 && git push origin v1.0.8
```

流水线签名需要两个仓库 Secret：`DEBUG_KEYSTORE_BASE64`（固定签名密钥的 base64）与 `KGAC_KEYSTORE_PASS`（密钥库密码，运行时经 Secret 注入，源码与日志零明文）。构建脚本通过 `KGAC_KEYSTORE_PATH` 环境变量显式引用密钥文件，并在构建后用 apksigner **自动校验产物签名证书指纹，不一致直接构建失败**，保证所有版本签名一致、App 内更新可直接覆盖安装。已配置密钥库但缺少密码 Secret 时**直接构建失败**（fail-fast，绝不静默回落默认密码）；完全未配置密钥库 Secret 时才回退 CI 临时 debug 签名（版本间不可覆盖升级）。

## 隐私说明

所有数据只保存在手机本地：账号凭据加密后存入应用私有目录，不经过任何第三方服务器。定时签到由本地闹钟触发，检查更新只访问 GitHub API。

## 免责声明

- 本项目仅供个人学习与技术交流使用，严禁用于商业用途，不接受任何商业合作与捐赠。
- 本项目与酷狗音乐官方无任何关联。自动签到可能违反酷狗用户协议，由此产生的一切后果由使用者自行承担。
- 仅供使用者本人账号日常签到使用，请勿对接口做滥用、攻击或批量操作。
- 若酷狗音乐官方认为本项目不妥，可通过 [Issues](../../issues) 联系，本人将第一时间处理或下架。

## 致谢

- [zhl20101118/kgcheckin2](https://github.com/zhl20101118/kgcheckin2) — 签到流程与签名算法参考此项目
- [MakcRe/KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi) — 部分接口定义参考此项目

## 开源协议

[GPL-3.0 License](LICENSE)。衍生作品必须同样以 GPL-3.0 开源并提供源码，禁止闭源分发。

致谢项目的上游均为 MIT 许可证，依 MIT 条款允许并入本 GPL-3.0 项目，其版权归属原作者所有。