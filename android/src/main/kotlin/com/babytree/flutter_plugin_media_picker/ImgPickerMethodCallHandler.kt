package com.babytree.flutter_plugin_media_picker

import android.app.Activity
import android.content.Intent
import android.os.AsyncTask
import android.provider.MediaStore
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry


class ImgPickerMethodCallHandler(var binaryMessenger: BinaryMessenger) :
    MethodChannel.MethodCallHandler, PluginRegistry.ActivityResultListener, ActivityAware {

    companion object {
        const val imageType = "image"
        const val videoType = "video"
        const val allType = "all"
    }

    private var activity: Activity? = null

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (activity == null) {
            result.success(emptyList<String>())
            return
        }

        when (call.method) {
            "listMediumPath" -> {
                val mediumType = call.argument<String>("mediumType")
                BackgroundAsyncTask({
                    listMediumPath(mediumType!!)
                }, { v ->
                    result.success(v)
                })
            }
            else -> result.notImplemented()
        }
    }

    private fun listMediumPath(mediumType: String): List<Map<String, Any>> {
        return when (mediumType) {
            imageType -> {
                listImagePath()
            }
            videoType -> {
                listVideoPath()
            }
            allType -> {
                listAllPath()
            }
            else -> {
                listOf()
            }
        }
    }

    private fun listImagePath(): List<Map<String, Any>> {
        this.activity?.run {
            val imagePathList = mutableListOf<Map<String, Any>>()
            val imageCursor = this.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA),
                null,
                null,
                null
            )
            imageCursor?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idColumn)
                    val path = cursor.getString(dataColumn)
                    imagePathList.add(mapOf("id" to id, "path" to path))
                }
            }
            return imagePathList
        }
        return listOf()
    }

    private fun listVideoPath(): List<Map<String, Any>> {
        this.activity?.run {
            val videoPathList = mutableListOf<Map<String, Any>>()
            val videoCursor = this.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATA),
                null,
                null,
                null
            )

            videoCursor?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idColumn)
                    val path = cursor.getString(dataColumn)
                    videoPathList.add(mapOf("id" to id, "path" to path))
                }
            }
            return videoPathList
        }
        return listOf()
    }

    private fun listAllPath(): List<Map<String, Any>> {
        val allPathList = mutableListOf<Map<String, Any>>()
        val imagePathList = mutableListOf<Map<String, Any>>()
        val videoPathList = mutableListOf<Map<String, Any>>()

        this.activity?.run {
            val imageCursor = this.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA),
                null,
                null,
                null
            )
            imageCursor?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idColumn)
                    val path = cursor.getString(dataColumn)
                    imagePathList.add(mapOf("id" to id, "path" to path))
                }
            }

            val videoCursor = this.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATA),
                null,
                null,
                null
            )
            videoCursor?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idColumn)
                    val path = cursor.getString(dataColumn)
                    videoPathList.add(mapOf("id" to id, "path" to path))
                }
            }

            allPathList.addAll(imagePathList)
            allPathList.addAll(videoPathList)
            return allPathList;
        }
        return listOf()
    }

    /*=== ActivityAware ==== */
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        this.activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        this.activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        this.activity = null
    }

    override fun onActivityResult(p0: Int, p1: Int, p2: Intent?): Boolean {
        return true
    }

}

// this class is copied from PhotoGalleryPlugin
class BackgroundAsyncTask<T>(val handler: () -> T, val post: (result: T) -> Unit) :
    AsyncTask<Void, Void, T>() {
    init {
        execute()
    }

    override fun doInBackground(vararg params: Void?): T {
        return handler()
    }

    override fun onPostExecute(result: T) {
        super.onPostExecute(result)
        post(result)
        return
    }
}
