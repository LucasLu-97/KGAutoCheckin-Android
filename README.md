# KGAutoCheckin-Android

酷狗概念版 VIP 自动签到 Android 客户端。

原生 Kotlin 实现，界面基于 Jetpack Compose。支持打开即签与每日定时后台签到，签到记录可在应用内查看。

## 功能

- 多账号管理，扫码 / 手机验证码两种登录方式
- 一键手动签到，实时过程日志（终端风格）
- 每日定时自动签到（精确闹钟 + 前台服务），通知栏显示进度、可取消
- 打开即签：启动 APP 自动补签，可配合系统自动任务使用
- 签到日历：月历视图、打卡统计、会员到期信息，支持从服务端同步历史记录
- 账号凭据经 AndroidKeyStore 密钥加密后存储在本地
- Material 3 界面，壁纸取色 + 内置配色，日间 / 夜间 / 跟随系统

## 架构

单模块原生应用，分层清晰，无动态脚本或插件机制：

```
UI（Compose M3）      签到页 / 签到日历 / 更多设置
ViewModel（MVVM）     StateFlow 驱动 UI 状态
Repository            登录流程、签到流程、历史同步
KgApi                 酷狗接口客户端（签名 / 加密 / 请求重试）
本地存储              Room（签到记录）/ DataStore（设置）/ AndroidKeyStore（账号加密）
调度                  AlarmManager 精确定时 + 前台服务（dataSync）
```

签到流程：校验登录 → 听歌领取 → 广告领取（最多 8 次，随机间隔防风控）→ 查询 VIP 状态 → 领取一天概念 VIP → 升级超级 VIP → 刷新会员信息。各步骤幂等，重复执行会快速返回“今日已领取”。

## 技术栈

| 层 | 技术 |
|---|---|
| 语言 | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM（StateFlow）+ Repository |
| 存储 | Room 2.7 / DataStore / AndroidKeyStore AES-256-GCM |
| 网络 | OkHttp 4.12 |
| 后台 | AlarmManager 精确闹钟 + 前台服务（dataSync） |
| 兼容 | Android 8.0 ~ 17（minSdk 26 / targetSdk 36） |

## 下载

到 [Releases](../../releases) 页面下载 APK 安装，要求 Android 8.0 及以上。

APP 内「设置 → 关于 → 检查更新」会请求 GitHub API 获取最新版本号并给出下载链接。

部分机型（MIUI 等）需在系统设置中将本应用省电策略设为「无限制」并允许自启动，以保证息屏时准点执行。

## 构建

```bash
git clone https://github.com/LucasLu-97/KGAutoCheckin-Android.git
cd KGAutoCheckin-Android
gradlew assembleRelease
```

需要 JDK 17+ 与 Android SDK（platforms;android-36），产物在 `app/build/outputs/apk/release/`。

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