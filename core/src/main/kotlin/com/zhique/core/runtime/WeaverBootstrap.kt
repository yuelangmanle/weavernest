package com.zhique.core.runtime

object WeaverBootstrap {
    const val apiVersion = "0.3.1"

    fun documentStartScript(): String = """
        (function () {
          if (window.weaver && window.weaver.apiVersion) return;
          const unsupported = function (capability) {
            const error = new Error(capability + ' is not available in this runtime build.');
            error.code = 'UNSUPPORTED';
            error.capability = capability;
            return Promise.reject(error);
          };
          const unavailable = function (capability) {
            return function () { return unsupported(capability); };
          };
          const nativeCall = function (method, args) {
            if (!window.ZhiqueNative || typeof window.ZhiqueNative.call !== 'function') {
              return unsupported(method);
            }
            try {
              return Promise.resolve(JSON.parse(window.ZhiqueNative.call(method, JSON.stringify(args || {}))));
            } catch (error) {
              return Promise.reject(error);
            }
          };
          const dataGet = function (key) {
            return window.ZhiqueNative && typeof window.ZhiqueNative.dataGet === 'function'
              ? Promise.resolve(window.ZhiqueNative.dataGet(String(key)))
              : unsupported('data.get');
          };
          const dataSet = function (key, value) {
            return window.ZhiqueNative && typeof window.ZhiqueNative.dataSet === 'function'
              ? Promise.resolve(window.ZhiqueNative.dataSet(String(key), String(value)))
              : unsupported('data.set');
          };
          const capabilities = function () { return nativeCall('capabilities.list'); };
          capabilities.list = function () { return nativeCall('capabilities.list'); };
          capabilities.status = function (id) { return nativeCall('capabilities.status', { id: id }); };
          capabilities.request = function (id) { return nativeCall('capabilities.request', { id: id }); };
          window.weaver = {
            apiVersion: '$apiVersion',
            ready: function () { return Promise.resolve({ apiVersion: '$apiVersion', runtime: 'preview' }); },
            capabilities: capabilities,
            data: { get: dataGet, set: dataSet },
            camera: { capture: unavailable('camera.capture'), recordVideo: unavailable('camera.recordVideo'), scanCode: unavailable('camera.scanCode') },
            geolocation: { getCurrentPosition: unavailable('geolocation.getCurrentPosition'), watchPosition: unavailable('geolocation.watchPosition'), clearWatch: unavailable('geolocation.clearWatch') },
            storage: { readFile: unavailable('storage.readFile'), writeFile: unavailable('storage.writeFile'), pickFile: unavailable('storage.pickFile'), createFile: unavailable('storage.createFile') },
            notification: { requestPermission: unavailable('notification.requestPermission'), show: unavailable('notification.show'), cancel: unavailable('notification.cancel') },
            contacts: { pick: unavailable('contacts.pick'), list: unavailable('contacts.list') },
            microphone: { requestPermission: unavailable('microphone.requestPermission'), record: unavailable('microphone.record'), stop: unavailable('microphone.stop') },
            clipboard: { read: unavailable('clipboard.read'), write: unavailable('clipboard.write') },
            haptics: { vibrate: unavailable('haptics.vibrate'), impact: unavailable('haptics.impact') },
            vibrate: unavailable('haptics.vibrate'),
            sensor: { subscribe: unavailable('sensor.subscribe') },
            config: { get: unavailable('config.get'), set: unavailable('config.set'), remove: unavailable('config.remove') },
            bluetooth: { scan: unavailable('bluetooth.scan'), connect: unavailable('bluetooth.connect') },
            wifi: { state: unavailable('wifi.state'), scan: unavailable('wifi.scan'), requestConnection: unavailable('wifi.requestConnection') },
            hotspot: { startLocalOnly: unavailable('hotspot.startLocalOnly'), stop: unavailable('hotspot.stop') },
            network: { status: unavailable('network.status'), request: unavailable('network.request') },
            system: { openUrl: unavailable('system.openUrl'), appInfo: unavailable('system.appInfo') }
          };
          window.weaver.location = { getCurrent: window.weaver.geolocation.getCurrentPosition };
          window.weaver.files = { pick: window.weaver.storage.pickFile };
          window.weaver.notifications = window.weaver.notification;
        })();
    """.trimIndent()

    fun injectIntoHtml(source: String): String {
        val bootstrap = "<script>${documentStartScript()}</script>"
        val head = Regex("<head\\b[^>]*>", RegexOption.IGNORE_CASE)
        head.find(source)?.let { match ->
            return source.replaceRange(match.range.last + 1, match.range.last + 1, bootstrap)
        }
        val html = Regex("<html\\b[^>]*>", RegexOption.IGNORE_CASE)
        html.find(source)?.let { match ->
            return source.replaceRange(match.range.last + 1, match.range.last + 1, "<head>$bootstrap</head>")
        }
        return "$bootstrap$source"
    }
}
