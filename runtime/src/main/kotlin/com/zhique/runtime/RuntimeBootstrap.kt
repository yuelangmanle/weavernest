package com.zhique.runtime

import com.zhique.core.project.RuntimeApiCatalog

object RuntimeBootstrap {
    fun documentStartScript(sessionId: String): String {
        val escapedSessionId = sessionId.asJavaScriptString()
        return """
            (function () {
              if (window.weaver && window.weaver.apiVersion) return;
              const apiVersion = '${RuntimeApiCatalog.apiVersion}';
              const sessionId = '$escapedSessionId';
              const pending = new Map();
              const sensorListeners = new Map();
              const subscriptionListeners = new Map();
              let requestSequence = 0;

              const runtimeError = function (code, message, capability, recoverable, action) {
                const error = new Error(message);
                error.code = code;
                error.capability = capability || null;
                error.recoverable = recoverable !== false;
                error.action = action || null;
                return error;
              };
              const nextRequestId = function () {
                requestSequence += 1;
                return 'wr_' + Date.now().toString(36) + '_' + requestSequence.toString(36) + '_' + Math.random().toString(36).slice(2, 10);
              };
              const invoke = function (method, params) {
                return new Promise(function (resolve, reject) {
                  const requestId = nextRequestId();
                  const timeout = window.setTimeout(function () {
                    if (pending.delete(requestId)) {
                      reject(runtimeError('TIMEOUT', 'Native request timed out.', method.split('.')[0], true, 'retry'));
                    }
                  }, 30000);
                  pending.set(requestId, { resolve: resolve, reject: reject, timeout: timeout });
                  if (!window.ZhiqueRuntime || typeof window.ZhiqueRuntime.postMessage !== 'function') {
                    window.clearTimeout(timeout);
                    pending.delete(requestId);
                    reject(runtimeError('RUNTIME_NOT_READY', 'Zhique Runtime bridge is not ready.', null, true, 'retry'));
                    return;
                  }
                  try {
                    window.ZhiqueRuntime.postMessage(JSON.stringify({
                      protocolVersion: apiVersion,
                      sessionId: sessionId,
                      requestId: requestId,
                      method: method,
                      params: params || {}
                    }));
                  } catch (error) {
                    window.clearTimeout(timeout);
                    pending.delete(requestId);
                    reject(runtimeError('NATIVE_FAILURE', error && error.message ? error.message : String(error), method.split('.')[0], true, 'retry'));
                  }
                });
              };
              window.__weaverResolve = function (rawResponse) {
                let response;
                try {
                  response = typeof rawResponse === 'string' ? JSON.parse(rawResponse) : rawResponse;
                } catch (_) {
                  return;
                }
                if (!response || !response.requestId) return;
                const entry = pending.get(response.requestId);
                if (!entry) return;
                window.clearTimeout(entry.timeout);
                pending.delete(response.requestId);
                if (response.error) {
                  entry.reject(runtimeError(
                    response.error.code || 'NATIVE_FAILURE',
                    response.error.message || 'Native request failed.',
                    response.error.capability,
                    response.error.recoverable,
                    response.error.action
                  ));
                } else {
                  entry.resolve(response.result);
                }
              };
              window.__weaverEvent = function (rawEvent) {
                let event;
                try {
                  event = typeof rawEvent === 'string' ? JSON.parse(rawEvent) : rawEvent;
                } catch (_) {
                  return;
                }
                if (!event || !event.subscriptionId) return;
                const listener = sensorListeners.get(event.subscriptionId) || subscriptionListeners.get(event.subscriptionId);
                if (listener) listener(event.payload);
              };
              const sensorSubscribe = function (type, listener) {
                if (typeof listener !== 'function') {
                  return Promise.reject(runtimeError('INVALID_ARGUMENT', 'sensor.subscribe requires a listener function.', 'sensors', false));
                }
                let subscriptionId = null;
                let unsubscribeRequested = false;
                const unsubscribe = function () {
                  if (!subscriptionId) {
                    unsubscribeRequested = true;
                    return Promise.resolve();
                  }
                  sensorListeners.delete(subscriptionId);
                  return invoke('sensor.unsubscribe', { subscriptionId: subscriptionId });
                };
                const subscriptionPromise = invoke('sensor.subscribe', { type: type }).then(function (result) {
                  subscriptionId = result && result.subscriptionId;
                  if (!subscriptionId) throw runtimeError('NATIVE_FAILURE', 'Native sensor subscription did not return an id.', 'sensors', true, 'retry');
                  sensorListeners.set(subscriptionId, listener);
                  if (unsubscribeRequested) return unsubscribe().then(function () { return { id: subscriptionId, unsubscribe: unsubscribe }; });
                  return {
                    id: subscriptionId,
                    unsubscribe: unsubscribe
                  };
                });
                // Compatibility for older pages that called unsubscribe before awaiting subscribe().
                subscriptionPromise.unsubscribe = unsubscribe;
                return subscriptionPromise;
              };
              const callbackSubscription = function (method, unsubscribeMethod, options, listener) {
                if (typeof listener !== 'function') {
                  return Promise.reject(runtimeError('INVALID_ARGUMENT', method + ' requires a listener function.', method.split('.')[0], false));
                }
                let subscriptionId = null;
                let unsubscribeRequested = false;
                const unsubscribe = function () {
                  if (!subscriptionId) {
                    unsubscribeRequested = true;
                    return Promise.resolve();
                  }
                  subscriptionListeners.delete(subscriptionId);
                  return invoke(unsubscribeMethod, { subscriptionId: subscriptionId });
                };
                const subscriptionPromise = invoke(method, options || {}).then(function (result) {
                  subscriptionId = result && result.subscriptionId;
                  if (!subscriptionId) throw runtimeError('NATIVE_FAILURE', 'Native subscription did not return an id.', method.split('.')[0], true, 'retry');
                  subscriptionListeners.set(subscriptionId, listener);
                  if (unsubscribeRequested) return unsubscribe().then(function () { return { id: subscriptionId, unsubscribe: unsubscribe }; });
                  return { id: subscriptionId, unsubscribe: unsubscribe };
                });
                subscriptionPromise.unsubscribe = unsubscribe;
                return subscriptionPromise;
              };
              const capabilities = function () { return invoke('capabilities.list', {}); };
              capabilities.list = function () { return invoke('capabilities.list', {}); };
              capabilities.status = function (id) { return invoke('capabilities.status', { id: id }); };
              capabilities.request = function (id) { return invoke('capabilities.request', { id: id }); };
              capabilities.openSettings = function (id) { return invoke('capabilities.openSettings', { id: id }); };
              const reserved = function (method) {
                return function (options) { return invoke(method, options || {}); };
              };
              const oneString = function (method, key) {
                return function (value, options) {
                  const params = options || {};
                  params[key] = String(value);
                  return invoke(method, params);
                };
              };

              window.weaver = {
                apiVersion: apiVersion,
                ready: function () { return invoke('runtime.ready', {}); },
                runtime: { info: reserved('runtime.info') },
                capabilities: capabilities,
                data: {
                  get: function (key) { return invoke('data.get', { key: String(key) }); },
                  set: function (key, value) { return invoke('data.set', { key: String(key), value: String(value) }); },
                  remove: function (key) { return invoke('data.remove', { key: String(key) }); },
                  clear: reserved('data.clear')
                },
                camera: {
                  capture: reserved('camera.capture'),
                  recordVideo: reserved('camera.recordVideo'),
                  scanCode: reserved('camera.scanCode')
                },
                media: {
                  pickImages: reserved('media.pickImages'),
                  pickVideo: reserved('media.pickVideo'),
                  pickAudio: reserved('media.pickAudio'),
                  save: function (uri, collection) { return invoke('media.save', { uri: String(uri), collection: String(collection) }); }
                },
                audio: {
                  play: function (source, options) { return oneString('audio.play', 'source')(source, options); },
                  pause: reserved('audio.pause'), stop: reserved('audio.stop'), state: reserved('audio.state')
                },
                geolocation: {
                  getCurrentPosition: reserved('geolocation.getCurrentPosition'),
                  watchPosition: function (options, listener) { return callbackSubscription('geolocation.watchPosition', 'geolocation.clearWatch', options || {}, listener); },
                  clearWatch: oneString('geolocation.clearWatch', 'id')
                },
                storage: {
                  readFile: function (path) { return invoke('storage.readFile', { path: String(path) }); },
                  writeFile: function (path, text) { return invoke('storage.writeFile', { path: String(path), text: String(text) }); },
                  deleteFile: oneString('storage.deleteFile', 'path'),
                  list: reserved('storage.list'), pickFile: reserved('storage.pickFile'), createFile: reserved('storage.createFile')
                },
                notification: {
                  requestPermission: function () { return invoke('notification.requestPermission', {}); },
                  show: function (title, body, options) { return invoke('notification.show', { title: String(title), body: String(body), options: options || {} }); },
                  cancel: oneString('notification.cancel', 'id'), schedule: reserved('notification.schedule')
                },
                contacts: { pick: function () { return invoke('contacts.pick', {}); }, list: reserved('contacts.list') },
                calendar: { addEvent: reserved('calendar.addEvent'), pickEvent: reserved('calendar.pickEvent'), list: reserved('calendar.list') },
                microphone: {
                  requestPermission: function () { return invoke('microphone.requestPermission', {}); },
                  record: reserved('microphone.record'), stop: reserved('microphone.stop')
                },
                clipboard: {
                  read: function () { return invoke('clipboard.read', {}); },
                  write: function (text) { return invoke('clipboard.write', { text: String(text) }); }
                },
                haptics: { vibrate: function (durationMs) { return invoke('haptics.vibrate', { durationMs: Number(durationMs) }); }, impact: oneString('haptics.impact', 'style') },
                vibrate: function (durationMs) { return invoke('haptics.vibrate', { durationMs: Number(durationMs) }); },
                sensor: { subscribe: sensorSubscribe },
                bluetooth: {
                  scan: function (options, listener) { return callbackSubscription('bluetooth.scan', 'bluetooth.stopScan', options || {}, listener); }, stopScan: reserved('bluetooth.stopScan'), connect: oneString('bluetooth.connect', 'id'), disconnect: oneString('bluetooth.disconnect', 'id'),
                  discover: oneString('bluetooth.discover', 'id'), read: reserved('bluetooth.read'), write: reserved('bluetooth.write'), subscribe: function (options, listener) { return callbackSubscription('bluetooth.subscribe', 'bluetooth.unsubscribe', options || {}, listener); }, unsubscribe: oneString('bluetooth.unsubscribe', 'subscriptionId'),
                  classic: { listPaired: reserved('bluetooth.classic.listPaired'), openSettings: reserved('bluetooth.classic.openSettings') }
                },
                wifi: { state: reserved('wifi.state'), scan: reserved('wifi.scan'), requestConnection: reserved('wifi.requestConnection'), openSettings: reserved('wifi.openSettings') },
                hotspot: { startLocalOnly: reserved('hotspot.startLocalOnly'), stop: reserved('hotspot.stop'), state: reserved('hotspot.state') },
                nfc: { isAvailable: reserved('nfc.isAvailable'), read: reserved('nfc.read'), write: function (message) { return invoke('nfc.write', { message: message }); } },
                network: { status: reserved('network.status'), request: reserved('network.request'), download: reserved('network.download') },
                share: { open: reserved('share.open') },
                system: {
                  openUrl: oneString('system.openUrl', 'url'), dial: oneString('system.dial', 'number'), openMap: reserved('system.openMap'),
                  appInfo: reserved('system.appInfo'), deviceInfo: reserved('system.deviceInfo')
                },
                biometric: { authenticate: reserved('biometric.authenticate') },
                speech: { recognize: reserved('speech.recognize'), speak: oneString('speech.speak', 'text'), stopSpeaking: reserved('speech.stopSpeaking') },
                screenCapture: { request: reserved('screenCapture.request'), start: reserved('screenCapture.start'), stop: reserved('screenCapture.stop') },
                usb: { list: reserved('usb.list'), requestPermission: oneString('usb.requestPermission', 'deviceId'), open: oneString('usb.open', 'deviceId'), close: oneString('usb.close', 'deviceId') },
                background: {
                  schedule: function (task) { return invoke('background.schedule', { task: task || {} }); },
                  cancel: oneString('background.cancel', 'id'),
                  list: reserved('background.list')
                },
                config: {
                  get: function (key) { return invoke('config.get', { key: String(key) }); },
                  set: function (key, value) { return invoke('config.set', { key: String(key), value: String(value) }); },
                  remove: oneString('config.remove', 'key')
                }
              };
              window.weaver.location = { getCurrent: window.weaver.geolocation.getCurrentPosition };
              window.weaver.files = { read: window.weaver.storage.readFile, write: window.weaver.storage.writeFile };
              window.weaver.notifications = window.weaver.notification;
            })();
        """.trimIndent()
    }

    fun injectIntoHtml(source: String, sessionId: String): String {
        val bootstrap = "<script>${documentStartScript(sessionId)}</script>"
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

private fun String.asJavaScriptString(): String = buildString(length) {
    this@asJavaScriptString.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '\'' -> append("\\'")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(character)
        }
    }
}
