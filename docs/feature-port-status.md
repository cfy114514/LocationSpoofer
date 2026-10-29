# LocationSpoofer 功能对照与实现状态

本次对照的样本为 `LocationSpoofer-v3.1.2-global-64bit-arm64-v8a.apk`，SHA-256：`20185e30c3fcc588461c29b90ee28f470cc214a5eda5584a37498b63b224c4ee`。下列状态依据样本分析和本仓库源码，功能是本仓库自行实现或集成；不表示与样本所有行为完全一致，也不表示已经通过真机验收。

## 已实现

- **OSM 地图**：普通地图、选点及路线绘制，显示 OpenStreetMap 署名；使用 osmdroid 和 OSM 瓦片。仅支持普通地图，不提供卫星图或 3D。见 [OsmMapEngineAdapter.kt](../app-ui/src/main/java/com/vincenthzr/locationspoofer/ui/components/map/OsmMapEngineAdapter.kt) 和 [MapTypeDialog.kt](../app-ui/src/main/java/com/vincenthzr/locationspoofer/ui/components/MapTypeDialog.kt)。
- **地点搜索**：选择 OSM 时使用设备配置的 Android Geocoder，结果和可用性取决于设备及其服务，不等同于样本的搜索服务。见 [SpoofingSearchBar.kt](../app-ui/src/main/java/com/vincenthzr/locationspoofer/ui/screen/SpoofingSearchBar.kt)。
- **路线规划方式**：步行、骑行、驾车独立于模拟速度，默认为驾车；分别使用现有高德 SDK 的规划接口，而非样本的服务。返回结果校验坐标及路线连续性，允许最多 150 米的道路吸附和分段间距；规划失败提示后使用直线，非法或过近的输入点则拒绝启动。支持取消规划并防止旧结果启动。见 [MainViewModelRoute.kt](../app-ui/src/main/java/com/vincenthzr/locationspoofer/viewmodel/MainViewModelRoute.kt)、[RoadRouteSegments.kt](../app-ui/src/main/java/com/vincenthzr/locationspoofer/viewmodel/RoadRouteSegments.kt)。
- **路线横向偏移**：根据样本中观察到的行为独立实现，开关默认关闭；启用后按沿路线距离产生平滑偏移，接近端点时衰减。偏移和运动控制见 [RoutePath.kt](../core-geo/src/main/kotlin/com/vincenthzr/locationspoofer/utils/RoutePath.kt) 和 [MotionController.kt](../core-data/src/main/java/com/vincenthzr/locationspoofer/data/motion/MotionController.kt)。
- **气压模拟**：开关默认关闭；模拟期间根据模拟海拔和标准大气近似值调整气压传感器事件，并加入缓慢的小幅变化。需要设备有实体气压计、目标进程通过 Java 监听器收到传感器事件且 Hook 生效；海拔超出模型范围时不改写。该模型为独立近似实现，波动参数与样本不同。见 [PressureModel.kt](../xposed/src/main/java/com/vincenthzr/locationspoofer/xposed/hooks/PressureModel.kt) 和 [SensorStepHooker.kt](../xposed/src/main/java/com/vincenthzr/locationspoofer/xposed/hooks/SensorStepHooker.kt)。
- **GNSS 状态**：已有模拟卫星状态及 NMEA 回调。旧 `GpsStatus` 按 [AOSP 映射规则](https://android.googlesource.com/platform/frameworks/base/+/bb83b89/location/java/android/location/GpsStatus.java) 编号，旧接口不显示没有对应编号的 Galileo；现代 `GnssStatus` 保留多星座。模拟激活时原始 GNSS 测量和导航电文回调被抑制，并未合成此类数据。具体设备及框架上的交付仍需验证。见 [GnssFastMockEngine.kt](../xposed/src/main/java/com/vincenthzr/locationspoofer/xposed/hooks/GnssFastMockEngine.kt) 和 [GnssStatusHooker.kt](../xposed/src/main/java/com/vincenthzr/locationspoofer/xposed/hooks/GnssStatusHooker.kt)。
- **摇杆方向锁定**：悬浮摇杆稳定推向一个方向约 1.5 秒后锁定，松手仍保持方向；可点击解锁并停止，再次拖动也会解锁，收起悬浮窗或摇杆不可用时自动解除。需要悬浮窗和摇杆正常工作，实际触控体验待设备验证。见 [FloatingJoystickService.kt](../service/src/main/java/com/vincenthzr/locationspoofer/service/FloatingJoystickService.kt) 和 [JoystickDirectionLatch.kt](../service/src/main/java/com/vincenthzr/locationspoofer/service/JoystickDirectionLatch.kt)。
- **真实路线录制**：使用系统 GPS、前台服务和本地草稿，提供录制点数、距离、时长及保存；信号中断会暂停，恢复需回到断点附近，启动模拟时暂停录制。时长仅累计当前进程中实际录制的时间，暂停期间不计，进程重启后时长重置；距离按保存轨迹相邻点的直线距离累计，包括暂停前后的连接。见 [RealRouteRecorder.kt](../app-ui/src/main/java/com/vincenthzr/locationspoofer/ui/screen/tabs/route/RealRouteRecorder.kt)、[RealRouteStatistics.kt](../app-ui/src/main/java/com/vincenthzr/locationspoofer/ui/screen/tabs/route/RealRouteStatistics.kt) 和 [RealRouteRecordingService.kt](../app-ui/src/main/java/com/vincenthzr/locationspoofer/ui/screen/tabs/route/RealRouteRecordingService.kt)。

## 范围与限制

- 原作者账号、包名审核、会员及其云端 Wi-Fi、基站数据和其他私有服务不在本次实现范围；未尝试绕过账号体系。仓库已有的第三方服务不因此变成原作者服务。
- OSM 地图和 Android Geocoder 依赖网络或设备服务；高德道路规划依赖高德服务、网络及其配置。高德同步请求取消后可能仍在后台等待返回，但已使旧结果失效。服务启动已执行后的停止时序仍需实机检查。
- GPS 录制需要精确定位权限、开启 GPS 和可运行的前台服务；系统省电策略、通知权限和信号质量会影响实际表现。草稿、断点恢复、坐标转换、后台行为和路线模拟仍需在目标设备验证。
- 传感器模拟目前依靠目标进程的 Java 回调 Hook；本仓库不包含样本的 system_server 原生传感器库与云端适配器。NDK、传感器直通通道及系统原生服务上的表现未验证，不等同于样本的原生路径。
- 本记录不表示已实现样本中的全部功能，或其未公开的服务端行为。

## 构建、测试与共存

验证入口见 [validation.yml](../.github/workflows/validation.yml)：`./gradlew :app:assembleScopedCoexist` 构建共存测试包，`./gradlew :core-geo:test :xposed:test :app-ui:testScopedDebugUnitTest :core-data:testScopedDebugUnitTest :service:testDebugUnitTest` 运行对应单元测试。道路规划、录制统计及草稿等测试位于 `app-ui/src/test`，横向偏移测试位于 `core-geo/src/test`；测试结果以具体运行记录为准，不等同于设备验证。

共存测试构建使用包名 `com.vincenthzr.locationspoofer.cfytest`、调试签名并禁用在线更新，不能覆盖原包；仅提供 scoped 共存变体，全局共存变体已禁用。不同安装的应用数据隔离，但系统级 Hook 和模拟状态可能共享或相互影响；请勿同时运行两个版本的模拟，并在目标设备核对框架作用域。见 [app/build.gradle.kts](../app/build.gradle.kts) 和 [UpdateViewModel.kt](../app-ui/src/main/java/com/vincenthzr/locationspoofer/viewmodel/UpdateViewModel.kt)。

后续分发给用户的 APK 计划使用本地固定测试签名，以便相同签名的后续版本覆盖升级；云端验证工作流默认使用运行环境生成的临时调试签名。两者即使包名相同，签名不同也不能相互覆盖安装，云端产物适合构建验证，不应与固定签名分发包混作升级包。具体 APK 应在分发前核对签名证书；若设备上已有不同签名的同包名版本，卸载会删除该版本的应用数据，操作前应先备份。
