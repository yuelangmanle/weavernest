package com.zhique.core.template

import com.zhique.core.project.CapabilityRegistry

/** A visible diagnostic template; unimplemented capabilities are reported, never passed. */
data class BuiltInProjectTemplate(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val capabilities: Set<String>,
    val html: String,
    val minimumApi: Int = 29,
    val verificationScenario: String = "Run the primary action, then test denial and unsupported-device states."
)

object DeviceCapabilityDiagnosticTemplate {
    val definition = BuiltInProjectTemplate(
        id = "device-capability-diagnostic",
        title = "设备能力诊断",
        category = "设备与权限",
        description = "逐项检查设备、系统限制与已接入的原生能力；测试记录按项目保存。",
        capabilities = CapabilityRegistry.all().mapTo(linkedSetOf()) { it.id },
        html = """
            <!-- weaver-required: camera, media_images, media_video, media_audio, microphone, storage, config, manage_external_storage, geolocation, background_location, sensors, haptics, notification, contacts, clipboard, calendar, phone_dial, share, system_intents, biometric, speech, screen_capture, usb, background_tasks, bluetooth_le, bluetooth_classic, nfc, wifi_scan, wifi_connect, local_hotspot, network -->
            <!doctype html>
            <html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>织雀设备能力诊断</title>
            <style>
              :root{color-scheme:dark;--bg:#111827;--card:#1f2937;--line:#374151;--text:#f3f4f6;--muted:#aeb9cb;--blue:#55c7ff;--ok:#42d39a;--warn:#ffca5c;--bad:#ff8282}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:15px system-ui,-apple-system,"Microsoft YaHei",sans-serif}header{position:sticky;top:0;padding:16px;background:#111827ee;border-bottom:1px solid var(--line);backdrop-filter:blur(12px)}h1{margin:0;font-size:20px}header p,.detail,#summary{color:var(--muted);line-height:1.45;font-size:13px}main{max-width:760px;margin:auto;padding:14px 14px 30px}#summary,.card{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:12px}.card{margin-top:8px}.row{display:flex;gap:10px;align-items:center}.name{font-weight:700;flex:1}.state{font-size:12px;color:var(--muted)}.detail{margin-top:8px;word-break:break-word}.ok{color:var(--ok)}.warn{color:var(--warn)}.bad{color:var(--bad)}h2{margin:23px 0 8px;color:var(--muted);font-size:12px}button{min-height:42px;padding:0 12px;border:0;border-radius:6px;background:var(--blue);color:#0b1722;font:inherit;font-weight:700}button:disabled{background:#4b5563;color:#d1d5db}.tools{display:flex;gap:8px;margin-top:18px}.tools button{flex:1}.secondary{background:transparent;border:1px solid var(--line);color:var(--text)}
            </style></head><body><header><h1>设备能力诊断</h1><p>记录只属于当前项目。关闭项目的“预览数据持久化”后，下一次运行前会自动清除测试记录和预览数据。</p></header><main><div id="summary">正在连接织雀运行时…</div><div id="cards"></div><div class="tools"><button id="refresh" class="secondary">刷新状态</button><button id="clear">清除测试记录</button></div></main>
            <script>
              const RESULTS_KEY = 'device-capability-diagnostic.results.v1';
              const weaver = window.weaver;
              const checks = [
                ['相机与媒体','camera','相机拍照','camera.capture'],['相机与媒体','media_images','图片/相册','media.pickImages'],['相机与媒体','media_video','视频','media.pickVideo'],['相机与媒体','media_audio','音乐播放与扬声器','audio.play'],['相机与媒体','microphone','麦克风录音','microphone.record'],
                ['文件与数据','storage','项目文件读写','storage.writeFile'],['文件与数据','config','私密运行时配置','config.set'],['文件与数据','manage_external_storage','所有文件访问','storage.pickFile'],
                ['定位与传感器','geolocation','前台定位','geolocation.getCurrentPosition'],['定位与传感器','background_location','后台定位','geolocation.watchPosition'],['定位与传感器','sensors','加速度计','sensor.subscribe'],['定位与传感器','haptics','振动与触觉','haptics.vibrate'],
                ['系统与辅助功能','notification','通知','notification.show'],['系统与辅助功能','contacts','联系人','contacts.pick'],['系统与辅助功能','clipboard','剪贴板','clipboard.write'],['系统与辅助功能','calendar','日历','calendar.addEvent'],['系统与辅助功能','phone_dial','拨号','system.dial'],['系统与辅助功能','share','系统分享','share.open'],['系统与辅助功能','system_intents','系统 Intent / 地图 / URL','system.openUrl'],['系统与辅助功能','biometric','生物识别','biometric.authenticate'],['系统与辅助功能','speech','语音识别与朗读','speech.speak'],['系统与辅助功能','screen_capture','屏幕捕获','screenCapture.request'],['系统与辅助功能','usb','USB Host','usb.list'],['系统与辅助功能','background_tasks','后台任务','background.schedule'],
                ['蓝牙与近场','bluetooth_le','低功耗蓝牙 BLE','bluetooth.scan'],['蓝牙与近场','bluetooth_classic','经典蓝牙已配对设备','bluetooth.classic.listPaired'],['蓝牙与近场','nfc','NFC 硬件与开关','nfc.isAvailable'],
                ['Wi-Fi 与网络','wifi_scan','Wi-Fi 扫描','wifi.scan'],['Wi-Fi 与网络','wifi_connect','Wi-Fi 系统确认连接','wifi.requestConnection'],['Wi-Fi 与网络','local_hotspot','局部热点','hotspot.startLocalOnly'],['Wi-Fi 与网络','network','网络与 API','network.status']
              ].map(function(item){return {family:item[0],capability:item[1],title:item[2],method:item[3]};});
              const executableCapabilities = new Set(['camera','media_images','media_video','media_audio','microphone','storage','geolocation','notification','contacts','clipboard','haptics','sensors','config','bluetooth_le','bluetooth_classic','wifi_scan','local_hotspot','network','system_intents','biometric','speech','screen_capture','usb','nfc']);
              let results = {};
              function label(state){return {not_requested:'可测试',not_implemented:'当前版本未接入',special_flow_required:'需要系统设置',not_selected:'未选择',unsupported_os:'系统不支持',unsupported_device:'设备不支持'}[state]||state;}
              async function restore(){const raw = await weaver.data.get(RESULTS_KEY);results=raw?JSON.parse(raw):{};}
              async function persist(){await weaver.data.set(RESULTS_KEY,JSON.stringify(results));}
              async function statuses(){const value={};for(const check of checks)value[check.capability]=await weaver.capabilities.status(check.capability);return value;}
              function render(state){const root=document.getElementById('cards');root.innerHTML='';let family='';checks.forEach(function(check){if(family!==check.family){family=check.family;const heading=document.createElement('h2');heading.textContent=family;root.appendChild(heading);}const capability=state[check.capability];const record=results[check.capability];const card=document.createElement('div');card.className='card';const canTest=capability.state==='not_requested'&&executableCapabilities.has(check.capability);const tone=record?(record.ok?'ok':'bad'):(capability.state==='not_implemented'||capability.state==='special_flow_required'?'warn':'');const detail=record?record.message:(executableCapabilities.has(check.capability)?'API：'+check.method:'需要目标设备、网络或项目参数后再测试。');card.innerHTML='<div class="row"><div class="name">'+check.title+'</div><span class="state '+tone+'">'+label(capability.state)+'</span><button '+(canTest?'':'disabled')+'>'+ (executableCapabilities.has(check.capability)?'测试':'需配置') +'</button></div><div class="detail">'+detail+'</div>';card.querySelector('button').onclick=function(){run(check);};root.appendChild(card);});const passed=Object.values(results).filter(function(item){return item.ok;}).length;document.getElementById('summary').textContent='已保存 '+Object.keys(results).length+' 项测试记录，其中 '+passed+' 项成功。灰色项目等待对应 Runtime 模块实现，黄色项目受 Android 系统流程限制，标记“需配置”的项目需要对应设备或参数。';}
              async function execute(check){switch(check.capability){case 'camera':return weaver.camera.capture({quality:0.6});case 'media_images':return weaver.media.pickImages({multiple:true});case 'media_video':return weaver.media.pickVideo();case 'media_audio':{const selected=await weaver.media.pickAudio();await weaver.audio.play(selected.uri);await new Promise(function(resolve){setTimeout(resolve,1200);});return weaver.audio.stop();}case 'microphone':return weaver.microphone.record({durationMs:1000});case 'storage':{const text=String(Date.now());await weaver.storage.writeFile('diagnostic/last-check.txt',text);return (await weaver.storage.readFile('diagnostic/last-check.txt'))===text;}case 'geolocation':return weaver.geolocation.getCurrentPosition({accuracy:'balanced',timeoutMs:15000});case 'notification':{const permission=await weaver.notification.requestPermission();if(permission!=='granted'&&permission!=='not_required')throw new Error(permission);return weaver.notification.show('织雀设备诊断','通知能力已测试');}case 'contacts':return weaver.contacts.pick();case 'clipboard':{const text='织雀诊断 '+Date.now();await weaver.clipboard.write(text);return (await weaver.clipboard.read())===text;}case 'haptics':return weaver.haptics.vibrate(80);case 'sensors':return new Promise(async function(resolve,reject){try{const subscription=await weaver.sensor.subscribe('accelerometer',async function(sample){await subscription.unsubscribe();resolve(sample);});setTimeout(function(){reject(new Error('SENSOR_TIMEOUT'));},4000);}catch(error){reject(error);}});case 'config':{const value='checked-'+Date.now();await weaver.config.set('diagnostic.lastCheck',value);return (await weaver.config.get('diagnostic.lastCheck'))===value;}case 'bluetooth_le':{const scan=await weaver.bluetooth.scan({},function(){});await new Promise(function(resolve){setTimeout(resolve,2000);});return scan.unsubscribe();}case 'bluetooth_classic':return weaver.bluetooth.classic.listPaired();case 'wifi_scan':return weaver.wifi.scan();case 'local_hotspot':{const hotspot=await weaver.hotspot.startLocalOnly();return weaver.hotspot.stop(hotspot);}case 'nfc':return weaver.nfc.isAvailable();case 'system_intents':return weaver.system.deviceInfo();case 'biometric':return weaver.biometric.authenticate({title:'织雀设备诊断'});case 'speech':return weaver.speech.speak('织雀语音能力测试');case 'screen_capture':return weaver.screenCapture.request();case 'usb':return weaver.usb.list();case 'network':return weaver.network.status();default:throw new Error('NOT_IMPLEMENTED');}}
              async function load(){await weaver.ready();await restore();render(await statuses());}
              async function run(check){try{await execute(check);results[check.capability]={ok:true,message:'测试成功 · '+new Date().toLocaleString()};}catch(error){results[check.capability]={ok:false,message:'测试未通过：'+(error&&error.message?error.message:String(error))};}await persist();await load();}
              document.getElementById('refresh').onclick=function(){load().catch(showFailure);};document.getElementById('clear').onclick=async function(){results={};await persist();await load();};function showFailure(error){document.getElementById('summary').textContent='无法连接织雀运行时：'+(error&&error.message?error.message:String(error));}load().catch(showFailure);
            </script></body></html>
        """.trimIndent()
    )
}
