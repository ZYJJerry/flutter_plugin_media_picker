package com.babytree.flutter_plugin_media_picker

import androidx.annotation.NonNull
import com.babytree.flutter_plugin_media_picker.ImgPickerMethodCallHandler
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodChannel

/** IvydadImgPickerPlugin */
class FlutterPluginMediaPickerPlugin: FlutterPlugin,ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  var callHandler : ImgPickerMethodCallHandler? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_plugin_media_picker")
    callHandler = ImgPickerMethodCallHandler(flutterPluginBinding.binaryMessenger);
    channel.setMethodCallHandler(callHandler)
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    binding.addActivityResultListener(callHandler!!)
    callHandler?.onAttachedToActivity(binding)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    callHandler?.onDetachedFromActivityForConfigChanges()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    callHandler?.onReattachedToActivityForConfigChanges(binding)
  }

  override fun onDetachedFromActivity() {
    callHandler?.onDetachedFromActivity()
  }

}
