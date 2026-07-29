package com.zhique.core.template

/**
 * Real starter projects for the template center. Each entry exercises the API it declares;
 * device-only entries remain Experimental until the Android matrix is completed.
 */
object BuiltInCapabilityTemplates {
    val all: List<BuiltInProjectTemplate> = listOf(
        BuiltInProjectTemplate(
            id = "camera",
            title = "拍照识别",
            category = "影像与媒体",
            description = "拍摄一张照片，并把图片交给项目自己的后续逻辑。",
            capabilities = setOf("camera", "network"),
            html = page("拍照识别", "camera", "拍照", """
                const result = await weaver.camera.capture({quality: 0.7});
                show(result.uri || '照片已准备好');
            """)
        ),
        BuiltInProjectTemplate(
            id = "album",
            title = "相册管理",
            category = "影像与媒体",
            description = "使用系统图片选择器选择图片，不直接扫描用户相册。",
            capabilities = setOf("media_images"),
            html = page("相册管理", "media_images", "选择图片", """
                const result = await weaver.media.pickImages({multiple: true});
                show((result.items || result).length + ' 张图片已选择');
            """)
        ),
        BuiltInProjectTemplate(
            id = "music",
            title = "音乐播放器",
            category = "影像与媒体",
            description = "选择音频并交给系统媒体播放模块，支持暂停和停止。",
            capabilities = setOf("media_audio"),
            html = page("音乐播放器", "media_audio", "选择并播放", """
                const selected = await weaver.media.pickAudio();
                await weaver.audio.play(selected.uri || selected);
                show('正在播放');
            """, extraButtons = """
                <button id="pause" class="secondary">暂停</button>
                <button id="stop" class="secondary">停止</button>
                <script>
                  pause.onclick=()=>run(()=>weaver.audio.pause());
                  stop.onclick=()=>run(()=>weaver.audio.stop());
                </script>
            """)
        ),
        BuiltInProjectTemplate(
            id = "recorder",
            title = "录音便签",
            category = "影像与媒体",
            description = "录制一段短语音并返回应用私有 URI。",
            capabilities = setOf("microphone", "storage"),
            html = page("录音便签", "microphone", "录制 3 秒", """
                const result = await weaver.microphone.record({durationMs: 3000});
                show(result.uri ? '录音已保存' : '录音已完成');
            """)
        ),
        BuiltInProjectTemplate(
            id = "files",
            title = "文件工具",
            category = "文件与数据",
            description = "在项目沙箱中读写文本，并通过系统选择器导入外部文件。",
            capabilities = setOf("storage"),
            html = page("文件工具", "storage", "写入并读取", """
                const path = 'examples/hello.txt';
                await weaver.storage.writeFile(path, '织雀文件示例');
                show(await weaver.storage.readFile(path));
            """, extraButtons = """
                <button id="pick" class="secondary">选择外部文件</button>
                <script>pick.onclick=()=>run(async()=>show(JSON.stringify(await weaver.storage.pickFile())));</script>
            """)
        ),
        BuiltInProjectTemplate(
            id = "forms",
            title = "离线表单",
            category = "文件与数据",
            description = "不请求系统权限，把表单内容保存到当前项目的数据空间。",
            capabilities = emptySet(),
            html = """
                <!doctype html><html lang="zh-CN"><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>离线表单</title>
                <style>body{font:16px system-ui;margin:24px;background:#f7f8fa;color:#17191d}main{max-width:560px;margin:auto}label{display:block;margin:16px 0 6px}input,textarea,button{width:100%;padding:12px;font:inherit;border:1px solid #dde1e6;border-radius:6px}button{margin-top:18px;background:#8a5a00;color:#fff;border:0}#status{min-height:24px;color:#0a7c68}</style></head>
                <body><main><h1>离线表单</h1><label>标题<input id="title"></label><label>内容<textarea id="body" rows="5"></textarea></label><button id="save">保存</button><p id="status"></p></main>
                <script>
                  async function load(){await weaver.ready();const saved=await weaver.data.get('offline-form');if(saved){const value=JSON.parse(saved);title.value=value.title||'';body.value=value.body||'';status.textContent='已恢复上次内容';}}
                  save.onclick=async()=>{await weaver.data.set('offline-form',JSON.stringify({title:title.value,body:body.value}));status.textContent='已保存到当前项目';};load().catch(error=>status.textContent=error.message);
                </script></body></html>
            """.trimIndent()
        ),
        BuiltInProjectTemplate(
            id = "location",
            title = "定位记录",
            category = "定位与传感器",
            description = "按用户动作获取一次前台位置，并显示精度与时间。",
            capabilities = setOf("geolocation"),
            html = page("定位记录", "geolocation", "获取当前位置", """
                const result = await weaver.geolocation.getCurrentPosition({accuracy: 'balanced', timeoutMs: 15000});
                show(JSON.stringify(result));
            """)
        ),
        BuiltInProjectTemplate(
            id = "contacts",
            title = "联系人助手",
            category = "系统能力",
            description = "使用系统联系人选择器，只返回用户主动选择的联系人。",
            capabilities = setOf("contacts"),
            html = page("联系人助手", "contacts", "选择联系人", """
                const result = await weaver.contacts.pick();
                show(result ? JSON.stringify(result) : '用户取消了选择');
            """)
        ),
        BuiltInProjectTemplate(
            id = "ble",
            title = "蓝牙 BLE 控制",
            category = "蓝牙与近场",
            description = "扫描 BLE 设备；连接、服务发现、读写和通知需要用户提供 UUID。",
            capabilities = setOf("bluetooth_le"),
            html = bleTemplate()
        ),
        BuiltInProjectTemplate(
            id = "nfc",
            title = "NFC 标签工具",
            category = "蓝牙与近场",
            description = "检查 NFC 硬件和系统开关，再进入用户确认的标签流程。",
            capabilities = setOf("nfc"),
            html = page("NFC 标签工具", "nfc", "检查 NFC", """
                const result = await weaver.nfc.isAvailable();
                show(result.available ? 'NFC 可用，请继续标签操作' : '当前设备或系统不支持 NFC');
            """)
        ),
        BuiltInProjectTemplate(
            id = "wifi",
            title = "Wi-Fi 网络诊断",
            category = "Wi-Fi 与网络",
            description = "读取连接状态和扫描结果；系统可能要求位置或附近设备授权。",
            capabilities = setOf("wifi_scan", "network"),
            html = page("Wi-Fi 网络诊断", "wifi_scan", "扫描网络", """
                const state = await weaver.wifi.state();
                const scan = await weaver.wifi.scan();
                show(JSON.stringify({state, scan}));
            """)
        ),
        BuiltInProjectTemplate(
            id = "hotspot",
            title = "局部热点",
            category = "Wi-Fi 与网络",
            description = "使用 Android LocalOnlyHotspot；不承诺互联网共享热点。",
            capabilities = setOf("local_hotspot"),
            html = page("局部热点", "local_hotspot", "启动局部热点", """
                const result = await weaver.hotspot.startLocalOnly();
                show('局部热点已请求：' + JSON.stringify(result));
            """, extraButtons = """
                <button id="stop" class="secondary">停止局部热点</button>
                <script>stop.onclick=()=>run(()=>weaver.hotspot.stop());</script>
            """)
        ),
        BuiltInProjectTemplate(
            id = "api",
            title = "API 数据面板",
            category = "Wi-Fi 与网络",
            description = "调用 HTTPS API；私密密钥由最终用户通过运行时配置填写。",
            capabilities = setOf("network", "config"),
            html = page("API 数据面板", "network", "检查网络", """
                const result = await weaver.network.status();
                show(JSON.stringify(result));
            """, extraButtons = """
                <button id="request" class="secondary">请求示例 API</button>
                <script>request.onclick=()=>run(async()=>show(JSON.stringify(await weaver.network.request({url:'https://httpbin.org/get',method:'GET'}))));</script>
            """)
        ),
        BuiltInProjectTemplate(
            id = "notifications",
            title = "通知提醒",
            category = "系统能力",
            description = "在用户触发时申请通知权限并发送一条测试通知。",
            capabilities = setOf("notification"),
            html = page("通知提醒", "notification", "发送通知", """
                const permission = await weaver.notification.requestPermission();
                if (permission !== 'granted' && permission !== 'not_required') throw new Error(permission);
                await weaver.notification.show('织雀模板', '通知能力已工作');
                show('通知已发送');
            """)
        )
    )

    private fun page(
        title: String,
        capability: String,
        button: String,
        action: String,
        extraButtons: String = ""
    ): String = """
        <!doctype html><html lang="zh-CN"><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>$title</title>
        <style>:root{color-scheme:light}body{font:16px system-ui;margin:24px;background:#f7f8fa;color:#17191d}main{max-width:600px;margin:auto}button{min-height:48px;padding:0 16px;margin:8px 8px 0 0;border:0;border-radius:6px;background:#8a5a00;color:#fff;font:inherit}.secondary{background:#fff;color:#17191d;border:1px solid #dde1e6}#status{min-height:28px;color:#0a7c68;white-space:pre-wrap;word-break:break-word}.cap{color:#5f6670;font-size:13px}</style></head>
        <body><main><h1>$title</h1><p class="cap">能力：$capability</p><button id="run">$button</button>$extraButtons<p id="status"></p></main>
        <script>
          async function run(task){status.textContent='正在运行…';try{await weaver.ready();await task();}catch(error){status.textContent=(error.code||'ERROR')+'：'+(error.message||error);} }
          function show(value){status.textContent=typeof value==='string'?value:JSON.stringify(value,null,2);}
          run.onclick=()=>run(async()=>{ $action });
        </script></body></html>
    """.trimIndent()

    private fun bleTemplate(): String = """
        <!doctype html><html lang="zh-CN"><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>蓝牙 BLE 控制</title>
        <style>body{font:16px system-ui;margin:24px;background:#f7f8fa;color:#17191d}main{max-width:600px;margin:auto}button,input{min-height:48px;padding:0 12px;margin:8px 8px 0 0;font:inherit;border:1px solid #dde1e6;border-radius:6px}button{background:#8a5a00;color:#fff;border:0}#status{white-space:pre-wrap;word-break:break-word;color:#0a7c68}</style></head>
        <body><main><h1>蓝牙 BLE 控制</h1><p>先扫描并复制设备地址，再填写服务和特征 UUID。连接后可按设备协议读写。</p><button id="scan">扫描 10 秒</button><input id="id" placeholder="设备地址 AA:BB:CC:DD:EE:FF"><input id="service" placeholder="服务 UUID"><input id="characteristic" placeholder="特征 UUID"><button id="connect">连接并发现服务</button><p id="status"></p></main>
        <script>
          let scanSubscription;
          async function ready(){await weaver.ready();}
          function show(value){status.textContent=typeof value==='string'?value:JSON.stringify(value,null,2);}
          scan.onclick=async()=>{try{await ready();scanSubscription=await weaver.bluetooth.scan({},device=>show(device));show('扫描中，请查看设备回调');setTimeout(()=>scanSubscription&&scanSubscription.unsubscribe(),10000);}catch(error){show((error.code||'ERROR')+'：'+error.message);}};
          connect.onclick=async()=>{try{await ready();const result=await weaver.bluetooth.connect(id.value.trim());const services=await weaver.bluetooth.discover(id.value.trim());show(JSON.stringify({result,services},null,2));}catch(error){show((error.code||'ERROR')+'：'+error.message);}};
        </script></body></html>
    """.trimIndent()
}
