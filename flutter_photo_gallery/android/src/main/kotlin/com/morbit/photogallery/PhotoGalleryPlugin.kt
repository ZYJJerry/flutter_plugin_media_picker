package com.morbit.photogallery

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.database.Cursor
import android.database.Cursor.FIELD_TYPE_INTEGER
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import androidx.annotation.NonNull
import androidx.collection.ArrayMap
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SimpleTarget
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap


/** PhotoGalleryPlugin */
class PhotoGalleryPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        val channel = MethodChannel(flutterPluginBinding.binaryMessenger, "photo_gallery")
        val plugin = PhotoGalleryPlugin()
        plugin.context = flutterPluginBinding.applicationContext
        channel.setMethodCallHandler(plugin)
    }

    companion object {
        // This static function is optional and equivalent to onAttachedToEngine. It supports the old
        // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
        // plugin registration via this function while apps migrate to use the new Android APIs
        // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
        //
        // It is encouraged to share LogUtilic between onAttachedToEngine and registerWith to keep
        // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
        // depending on the user's project. onAttachedToEngine or registerWith must both be defined
        // in the same class.
        private val QUERY_URI = MediaStore.Files.getContentUri("external")

        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "photo_gallery")
            val plugin = PhotoGalleryPlugin()
            plugin.context = registrar.activeContext()
            channel.setMethodCallHandler(plugin)
        }

        const val TAG = "PhotoGalleryPlugin"
        const val compressByteRate = 90
        const val imageType = "image"
        const val videoType = "video"
        const val allType = "all"
        const val pathParamKey = "path"
        const val orientationParamKey = "orientation"
        const val metaWidthParamKey = "metaWidth"
        const val metaHeightParamKey = "metaHeight"

        const val allAlbumId = "__ALL__"
        const val allAlbumName = "All"

        val imageMetadataProjection = arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.TITLE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED
        )

        val videoMetadataProjection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Images.Media.DATA,
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.ORIENTATION,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_TAKEN,
                MediaStore.Video.Media.DATE_MODIFIED
            )
        } else {
            arrayOf(
                MediaStore.Images.Media.DATA,
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_TAKEN,
                MediaStore.Video.Media.DATE_MODIFIED
            )
        }
    }

    private var context: Context? = null

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "listAlbums" -> {
                val mediumType = call.argument<String>("mediumType")
                BackgroundAsyncTask({
                    listAlbums(mediumType!!)
                }, { v ->
                    result.success(v)
                })
            }
            "listMedia" -> {
                val albumId = call.argument<String>("albumId")
                val mediumType = call.argument<String>("mediumType")
                val newest = call.argument<Boolean>("newest")
                val total = call.argument<Int>("total")
                val skip = call.argument<Int>("skip")
                val take = call.argument<Int>("take")
                BackgroundAsyncTask({
                    when (mediumType) {
                        imageType -> listImages(albumId!!, newest!!, total!!, skip, take)
                        videoType -> listVideos(albumId!!, newest!!, total!!, skip, take)
                        allType -> listImageAndVideos(albumId!!, newest!!, total!!, skip, take)
                        else -> null
                    }
                }, { v ->
                    result.success(v)
                })
            }
            "getMedium" -> {
                val mediumId = call.argument<String>("mediumId")
                val mediumType = call.argument<String>("mediumType")
                BackgroundAsyncTask({
                    getMedium(mediumId!!, mediumType)
                }, { v ->
                    result.success(v)
                })
            }

            "getOrigin" -> {
            val mediumId = call.argument<String>("mediumId")
            val mediumType = call.argument<String>("mediumType")
            val width = call.argument<Int>("width")
            val height = call.argument<Int>("height")
            val highQuality = call.argument<Boolean>("highQuality")

            getThumbnail(mediumId!!, mediumType, width, height, highQuality) {
                result.success(it)
            }
        }
            "getThumbnail" -> {
                val mediumId = call.argument<String>("mediumId")
                val mediumType = call.argument<String>("mediumType")
                val width = call.argument<Int>("width")
                val height = call.argument<Int>("height")
                val highQuality = call.argument<Boolean>("highQuality")

                getThumbnail(mediumId!!, mediumType, width, height, highQuality) {
                    result.success(it)
                }
            }
            "clearThumbnail" -> {
                val mediumId = call.argument<String>("mediumId")
                val mediumType = call.argument<String>("mediumType")
                mediumId?.let {
                    // TODO
                    clearThumbnail(mediumId, mediumType)
                }
            }
            "getAlbumThumbnail" -> {
                val albumId = call.argument<String>("albumId")
                val mediumType = call.argument<String>("mediumType")
                val width = call.argument<Int>("width")
                val height = call.argument<Int>("height")
                val highQuality = call.argument<Boolean>("highQuality")

                getAlbumThumbnail(albumId!!, mediumType, width, height, highQuality) {
                    result.success(it)
                }
            }
            "getFile" -> {
                val mediumId = call.argument<String>("mediumId")
                val mediumType = call.argument<String>("mediumType")
                val mimeType = call.argument<String>("mimeType")
                BackgroundAsyncTask({
                    getFile(mediumId!!, mediumType, mimeType)
                }, { v ->
                    result.success(v)
                })
            }
            "cleanCache" -> {
                cleanCache()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {

    }

    private fun listAlbums(mediumType: String): List<Map<String, Any>> {
        return when (mediumType) {
            imageType -> {
                listImageAlbums()
            }
            videoType -> {
                listVideoAlbums()
            }
            allType -> {
                ///相册区分image和video感觉没什么卵用,此处虽然是all类型,走的方法和listImageAlbums基本一致
                listAllAlbums()
            }
            else -> {
                listOf()
            }
        }
    }

    private fun listImageAlbums(): List<Map<String, Any>> {
        this.context?.run {
            var total = 0
            val albumHashMap = mutableMapOf<String, MutableMap<String, Any>>()

            val imageProjection = arrayOf(
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_ID
            )

            val imageCursor = this.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null,
                null,
                null
            )

            imageCursor?.use { cursor ->
                val bucketColumn =
                    cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val bucketColumnId = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)

                while (cursor.moveToNext()) {
                    val bucketId = cursor.getString(bucketColumnId)
                    val album = albumHashMap[bucketId]
                    if (album == null) {

                        val folderName = cursor.getString(bucketColumn)
                        LogUtil.i(TAG, "相册名字folderName为===" + folderName)

                        albumHashMap[bucketId] = mutableMapOf(
                            "id" to bucketId,
                            "mediumType" to imageType,
                            "name" to NameUtil.changeName(folderName),
                            "count" to 1
                        )
                    } else {
                        val count = album["count"] as Int
                        album["count"] = count + 1
                    }
                    total++
                }
            }

            val albumList = mutableListOf<Map<String, Any>>()
            LogUtil.i(TAG, "相册名字allAlbumName为===" + allAlbumName)

            albumList.add(
                mapOf(
                    "id" to allAlbumId,
                    "mediumType" to imageType,
                    "name" to NameUtil.changeName(allAlbumName),
                    "count" to total
                )
            )
            albumList.addAll(albumHashMap.values)
            return albumList
        }
        return listOf()
    }

    private fun listVideoAlbums(): List<Map<String, Any>> {
        this.context?.run {
            var total = 0
            val albumHashMap = mutableMapOf<String, MutableMap<String, Any>>()

            val videoProjection = arrayOf(
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.BUCKET_ID
            )

            val videoCursor = this.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                null
            )

            videoCursor?.use { cursor ->
                val bucketColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val bucketColumnId = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)

                while (cursor.moveToNext()) {
                    val bucketId = cursor.getString(bucketColumnId)
                    val album = albumHashMap[bucketId]
                    if (album == null) {
                        val folderName = cursor.getString(bucketColumn)
                        albumHashMap[bucketId] = mutableMapOf(
                            "id" to bucketId,
                            "mediumType" to videoType,
                            "name" to NameUtil.changeName(folderName),
                            "count" to 1
                        )
                    } else {
                        val count = album["count"] as Int
                        album["count"] = count + 1
                    }
                    total++
                }
            }

            val albumList = mutableListOf<Map<String, Any>>()
            albumList.add(
                mapOf(
                    "id" to allAlbumId,
                    "mediumType" to videoType,
                    "name" to NameUtil.changeName(allAlbumName),
                    "count" to total
                )
            )
            albumList.addAll(albumHashMap.values)
            return albumList
        }
        return listOf()
    }

    private fun listAllAlbums(): List<Map<String, Any>> {
        this.context?.run {
            var totalAll = 0


//            视频相册
            var videoTotal = 0
            val videoAlbumHashMap = mutableMapOf<String, MutableMap<String, Any>>()

            val videoProjection = arrayOf(
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.BUCKET_ID
            )

            val videoCursor = this.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                null
            )

            videoCursor?.use { cursor ->
                val bucketColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val bucketColumnId = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)

                while (cursor.moveToNext()) {
                    val bucketId = cursor.getString(bucketColumnId)
                    val videoAlbum = videoAlbumHashMap[bucketId]
                    if (videoAlbum == null) {
                        val folderName = cursor.getString(bucketColumn)
                        LogUtil.i(TAG, "video相册名字folderName为===" + folderName)
                        LogUtil.i(TAG, "video相册id" + bucketId.toString())

                        videoAlbumHashMap[bucketId] = mutableMapOf(
                            "id" to bucketId,
                            "mediumType" to videoType,
                            "name" to NameUtil.changeName(folderName),
                            "count" to 1
                        )
                    } else {
                        val count = videoAlbum["count"] as Int
                        videoAlbum["count"] = count + 1
                    }
                    videoTotal++
                }
            }

            val videoAlbumList = mutableListOf<Map<String, Any>>()
            videoAlbumList.add(
                mapOf(
                    "id" to allAlbumId,
                    "mediumType" to videoType,
                    "name" to NameUtil.changeName(allAlbumName),
                    "count" to videoTotal
                )
            )
            videoAlbumList.addAll(videoAlbumHashMap.values)
//图片相册
            var imageTotal = 0
            val imageAlbumHashMap = mutableMapOf<String, MutableMap<String, Any>>()

            val imageProjection = arrayOf(
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_ID
            )

            val imageCursor = this.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null,
                null,
                null
            )

            imageCursor?.use { cursor ->
                val bucketColumn =
                    cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val bucketColumnId = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)

                while (cursor.moveToNext()) {
                    val bucketId = cursor.getString(bucketColumnId)
                    val imageAlbum = imageAlbumHashMap[bucketId]
                    if (imageAlbum == null) {

                        val folderName = cursor.getString(bucketColumn)
                        LogUtil.i(TAG, "image相册名字folderName为===" + folderName)
                        LogUtil.i(TAG, "image相册id" + bucketId.toString())

                        imageAlbumHashMap[bucketId] = mutableMapOf(
                            "id" to bucketId,
                            "mediumType" to imageType,
                            "name" to NameUtil.changeName(folderName),
                            "count" to 1
                        )
                    } else {
                        val count = imageAlbum["count"] as Int
                        imageAlbum["count"] = count + 1
                    }

                    imageTotal++
                }
            }

            val imageAlbumList = mutableListOf<Map<String, Any>>()

            imageAlbumList.add(
                mapOf(
                    "id" to allAlbumId,
                    "mediumType" to imageType,
                    "name" to NameUtil.changeName(allAlbumName),
                    "count" to imageTotal
                )
            )
            imageAlbumList.addAll(imageAlbumHashMap.values)

//图片+视频相册数据
            val allAlbumList = mutableListOf<Map<String, Any>>()
            allAlbumList.add(
                mapOf(
                    "id" to allAlbumId,
                    "mediumType" to allType,
                    "name" to NameUtil.changeName(allAlbumName),
                    "count" to videoTotal + imageTotal
                )
            )
            val allAlbumHashMap = mutableMapOf<String, MutableMap<String, Any>>()
            val tempVideoMap = mutableMapOf<String, MutableMap<String, Any>>()
            allAlbumHashMap.putAll(imageAlbumHashMap)
            tempVideoMap.putAll(videoAlbumHashMap)

            imageAlbumHashMap.keys.forEach { keyInImage: String ->
                videoAlbumHashMap.keys.forEach { keyInVideo: String ->
                    //相册id相同
                    if (keyInImage == keyInVideo) {
                        val imageCount = imageAlbumHashMap[keyInImage]?.get("count") as Int
                        val videoCount = videoAlbumHashMap[keyInImage]?.get("count") as Int
                      //将图片+视频合在一起
                        allAlbumHashMap[keyInImage] = mutableMapOf(
                            "id" to keyInImage,
                            "mediumType" to allType,
                            "name" to NameUtil.changeName(imageAlbumHashMap[keyInImage]?.get("name") as String),
                            "count" to imageCount + videoCount
                        )
                        tempVideoMap.remove(keyInImage)
                    }
                }
            }
            allAlbumHashMap.putAll(tempVideoMap)
            allAlbumList.addAll(allAlbumHashMap.values)
            return allAlbumList
        }
        return listOf()
    }

    private fun listImages(
        albumId: String,
        newest: Boolean,
        total: Int,
        skip: Int?,
        take: Int?
    ): Map<String, Any> {
        val media = mutableListOf<Map<String, Any?>>()
        val offset = skip ?: 0
        val limit = take ?: (total - offset)

        this.context?.run {
            val imageCursor: Cursor?

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                imageCursor = this.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    imageMetadataProjection,
                    android.os.Bundle().apply {
                        // Limit & Offset
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
                        putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
                        // Sort
                        putStringArray(
                            android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                            arrayOf(
                                MediaStore.Images.Media.DATE_TAKEN,
                                MediaStore.Images.Media.DATE_MODIFIED
                            )
                        )
                        putInt(
                            android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                            if (newest) {
                                android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                            } else {
                                android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
                            }
                        )
                        // Selection
                        if (albumId != allAlbumId) {
                            putString(
                                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                                "${MediaStore.Images.Media.BUCKET_ID} = ?"
                            )
                            putStringArray(
                                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                                arrayOf(albumId)
                            )
                        }
                    },
                    null
                )
            } else {
                val orderBy = if (newest) {
                    "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                } else {
                    "${MediaStore.Images.Media.DATE_TAKEN} ASC, ${MediaStore.Images.Media.DATE_MODIFIED} ASC"
                }
                imageCursor = this.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    imageMetadataProjection,
                    if (albumId == allAlbumId) null else "${MediaStore.Images.Media.BUCKET_ID} = $albumId",
                    null,
                    "$orderBy LIMIT $limit OFFSET $offset"
                )
            }

            imageCursor?.use { cursor ->
                while (cursor.moveToNext()) {
                    media.add(getImageMetadata(cursor))
                }
            }
        }
        media.sortByDescending { mediaMap -> mediaMap["modifiedDate"] as Long }

        return mapOf(
            "newest" to newest,
            "start" to offset,
            "total" to total,
            "items" to media
        )
    }

    private fun listVideos(
        albumId: String,
        newest: Boolean,
        total: Int,
        skip: Int?,
        take: Int?
    ): Map<String, Any> {
        val media = mutableListOf<Map<String, Any?>>()
        val offset = skip ?: 0
        val limit = take ?: (total - offset)

        this.context?.run {
            val videoCursor: Cursor?

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                videoCursor = this.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    videoMetadataProjection,
                    android.os.Bundle().apply {
                        // Limit & Offset
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
                        putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
                        // Sort
                        putStringArray(
                            android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                            arrayOf(
                                MediaStore.Video.Media.DATE_TAKEN,
                                MediaStore.Video.Media.DATE_MODIFIED
                            )
                        )
                        putInt(
                            android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                            if (newest) {
                                android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                            } else {
                                android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
                            }
                        )
                        // Selection
                        if (albumId != allAlbumId) {
                            putString(
                                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                                "${MediaStore.Video.Media.BUCKET_ID} = ?"
                            )
                            putStringArray(
                                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                                arrayOf(albumId)
                            )
                        }
                    },
                    null
                )
            } else {
                val orderBy = if (newest) {
                    "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                } else {
                    "${MediaStore.Images.Media.DATE_TAKEN} ASC, ${MediaStore.Images.Media.DATE_MODIFIED} ASC"
                }
                videoCursor = this.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    videoMetadataProjection,
                    if (albumId == allAlbumId) null else "${MediaStore.Video.Media.BUCKET_ID} = $albumId",
                    null,
                    "$orderBy LIMIT $limit OFFSET $offset"
                )
            }

            videoCursor?.use { cursor ->
                while (cursor.moveToNext()) {
                    media.add(getVideoMetadata(cursor))
                }
            }
        }
        media.sortByDescending { mediaMap -> mediaMap["modifiedDate"] as Long }

        return mapOf(
            "newest" to newest,
            "start" to offset,
            "total" to total,
            "items" to media
        )
    }

    private fun getMedium(mediumId: String, mediumType: String?): Map<String, Any?>? {
        return when (mediumType) {
            imageType -> {
                getImageMedia(mediumId)
            }
            videoType -> {
                getVideoMedia(mediumId)
            }
            else -> {
                getImageMedia(mediumId) ?: getVideoMedia(mediumId)
            }
        }
    }

    private fun getImageMedia(mediumId: String): Map<String, Any?>? {
        var imageMetadata: Map<String, Any?>? = null

        this.context?.run {
            val imageCursor = this.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageMetadataProjection,
                "${MediaStore.Images.Media._ID} = $mediumId",
                null,
                null
            )

            imageCursor?.use { cursor ->
                if (cursor.moveToFirst()) {
                    imageMetadata = getImageMetadata(cursor)
                }
            }
        }

        return imageMetadata
    }

    private fun getVideoMedia(mediumId: String): Map<String, Any?>? {
        var videoMetadata: Map<String, Any?>? = null

        this.context?.run {
            val videoCursor = this.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoMetadataProjection,
                "${MediaStore.Images.Media._ID} = $mediumId",
                null,
                null
            )

            videoCursor?.use { cursor ->
                if (cursor.moveToFirst()) {
                    videoMetadata = getVideoMetadata(cursor)
                }
            }
        }

        return videoMetadata
    }

    private fun getThumbnail(
        mediumId: String,
        mediumType: String?,
        width: Int?,
        height: Int?,
        highQuality: Boolean?,
        loadListener: ((byte: ByteArray?) -> Unit)? = null
    ) {
        val widthSize = width ?: if (highQuality == true) 140 else 80
        val heightSize = height ?: if (highQuality == true) 140 else 80
        val contentUri = getContentUri(mediumId, mediumType)

        // TODO
//        if (activity == null || activity?.isFinishing == true || activity?.isDestroyed == true) {
//            return
//        }

        val builder = Glide.with(context)
            .loadFromMediaStore(contentUri)
            .asBitmap()
            .dontAnimate()
            .format(DecodeFormat.PREFER_RGB_565)
            .override(widthSize, heightSize)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.RESULT)
        if (mediumType == videoType && isHarmonyOSa()) {
            builder.videoDecoder(VideoThumbnailDecoder(context, mediumId.toLong()))
        }
        val target = builder
            .into(object : SimpleTarget<Bitmap?>(widthSize, heightSize) {
                override fun onResourceReady(
                    bitmap: Bitmap?,
                    glideAnimation: GlideAnimation<in Bitmap?>?
                ) {
                    try {
                        BackgroundAsyncTask({
                            var byteArray: ByteArray? = null
                            try {
                                bitmap?.run {
                                    ByteArrayOutputStream().use { stream ->
                                        // bitmap内存大小无法影响，但会影响传递给flutter的bytearray大小
                                        this.compress(Bitmap.CompressFormat.JPEG, compressByteRate, stream)
                                        byteArray = stream.toByteArray()
                                    }
                                }
                            } catch (t : Throwable) {
                                t.printStackTrace()
                            }
                            return@BackgroundAsyncTask byteArray
                        }, { byteArray ->
                            loadListener?.invoke(byteArray)
                            clearThumbnail(mediumId, mediumType, false)
                        })
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            })
        targetMap[contentUri] = target
    }

    private val targetMap = ConcurrentHashMap<Uri?, SimpleTarget<Bitmap?>>()

    private fun clearThumbnail(mediumId: String, mediumType: String?, clear: Boolean = true) {
        try {
            val contentUri = getContentUri(mediumId, mediumType)
            val target = targetMap[contentUri]
            targetMap.remove(contentUri)
            if (clear) {
                Glide.clear(target)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun clearAllThumbnailRequest() {
        try {
            val values = targetMap.values
            for (target in values) {
                Glide.clear(target)
            }
            targetMap.clear()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun getContentUri(mediumId: String, mediumType: String?): Uri {
        return when (mediumType) {
            imageType -> ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                mediumId.toLong()
            )
            videoType -> ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                mediumId.toLong()
            )
            else -> ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                mediumId.toLong()
            )
        }
    }

    private fun getAlbumThumbnail(
        albumId: String,
        mediumType: String?,
        width: Int?,
        height: Int?,
        highQuality: Boolean?,
        loadListener: ((byte: ByteArray?) -> Unit)? = null
    ) {

        val imageCursor = when (mediumType) {
            imageType -> getImageAlbumThumbnailCursor(albumId)
            videoType -> getVideoAlbumThumbnailCursor(albumId)
            else -> getImageAlbumThumbnailCursor(albumId)
        }
        imageCursor?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idColumn)
                getThumbnail(id.toString(), mediumType, width, height, highQuality, loadListener)
            }
        }
    }

    private fun getImageAlbumThumbnailCursor(
        albumId: String
    ): Cursor? {
        return this.context?.run {
            val imageCursor: Cursor?

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                imageCursor = this.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    android.os.Bundle().apply {
                        // Limit
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 1)
                        // Sort
                        putStringArray(
                            android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                            arrayOf(
                                MediaStore.Images.Media.DATE_TAKEN,
                                MediaStore.Images.Media.DATE_MODIFIED
                            )
                        )
                        putInt(
                            android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                            android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                        )
                        // Selection
                        if (albumId != allAlbumId) {
                            putString(
                                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                                "${MediaStore.Images.Media.BUCKET_ID} = ?"
                            )
                            putStringArray(
                                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                                arrayOf(albumId)
                            )
                        }
                    },
                    null
                )
            } else {
                imageCursor = this.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    if (albumId == allAlbumId) null else "${MediaStore.Images.Media.BUCKET_ID} = $albumId",
                    null,
                    MediaStore.Images.Media.DATE_TAKEN + " DESC LIMIT 1"
                )
            }
            return imageCursor
        }
    }

    private fun getVideoAlbumThumbnailCursor(
        albumId: String
    ): Cursor? {
        return this.context?.run {
            val videoCursor: Cursor?

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                videoCursor = this.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media._ID),
                    android.os.Bundle().apply {
                        // Limit
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 1)
                        // Sort
                        putStringArray(
                            android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                            arrayOf(
                                MediaStore.Video.Media.DATE_TAKEN,
                                MediaStore.Video.Media.DATE_MODIFIED
                            )
                        )
                        putInt(
                            android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                            android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                        )
                        // Selection
                        if (albumId != allAlbumId) {
                            putString(
                                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                                "${MediaStore.Video.Media.BUCKET_ID} = ?"
                            )
                            putStringArray(
                                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                                arrayOf(albumId)
                            )
                        }
                    },
                    null
                )
            } else {
                videoCursor = this.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media._ID),
                    if (albumId == allAlbumId) null else "${MediaStore.Video.Media.BUCKET_ID} = $albumId",
                    null,
                    MediaStore.Video.Media.DATE_TAKEN + " DESC LIMIT 1"
                )
            }
            return videoCursor
        }
    }

    private fun getFile(
        mediumId: String,
        mediumType: String?,
        mimeType: String?
    ): HashMap<String?, Any?>? {
        return when (mediumType) {
            imageType -> {
                getImageFile(mediumId, mimeType = mimeType)
            }
            videoType -> {
                getVideoFile(mediumId)
            }
            else -> {
                getImageFile(mediumId, mimeType = mimeType) ?: getVideoFile(mediumId)
            }
        }
    }

    private fun getImageFile(mediumId: String, mimeType: String? = null): HashMap<String?, Any?>? {
        this.context?.run {
            mimeType?.let {
                val type = this.contentResolver.getType(
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        mediumId.toLong()
                    )
                )
                if (it != type) {
                    return cacheImage(mediumId, it)
                }
            }

            val imageCursor = this.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media.DATA),
                "${MediaStore.Images.Media._ID} = $mediumId",
                null,
                null
            )

            imageCursor?.use { cursor ->
                if (cursor.moveToNext()) {
                    val returnMap: HashMap<String?, Any?> = HashMap()
                    val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    returnMap[pathParamKey] = cursor.getString(dataColumn)
                    var exif = ExifInterface(cursor.getString(dataColumn))
                    val ori: Int = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED
                    )
//                    LogUtil.i(TAG, "图片路径为===" + cursor.getString(dataColumn))
//                    LogUtil.i(TAG, "图片旋转方向为===" + ori)
                    returnMap[orientationParamKey] = ori

                    return returnMap
                }
            }
        }

        return null
    }

    private fun getVideoFile(mediumId: String): HashMap<String?, Any?> {
        var pathAndroidQ: String? = null
        var path: String? = null

        this.context?.run {
            val videoCursor = this.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media.DATA), "${MediaStore.Images.Media._ID} = $mediumId", null, null)

            videoCursor?.use { cursor ->
                if (cursor.moveToNext()) {
                    val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    path = cursor.getString(dataColumn)
                    pathAndroidQ = getRealPathAndroidQ(mediumId)
                    Log.d("VideoSelectTag", "mediumId=$mediumId;path=$path;pathAndroidQ=$pathAndroidQ;")
                }
            }
        }

        val metaDataKeys = arrayListOf(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION,
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH,
            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,
        )
        val resultMap = if (this.context != null && !TextUtils.isEmpty(pathAndroidQ)) {
            getVideoInfoForUri(this.context, Uri.parse(pathAndroidQ), metaDataKeys)
        } else {
            getVideoInfoForUrl(path, metaDataKeys)
        }
        var rotation = resultMap[MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION]
        rotation = if (rotation.isNullOrEmpty()) "0" else rotation

        var videoWidth = resultMap[MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH]
        videoWidth = if (videoWidth.isNullOrEmpty()) "0" else videoWidth

        var videoHeight = resultMap[MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT]
        videoHeight = if (videoHeight.isNullOrEmpty()) "0" else videoHeight

        Log.d("VideoSelectTag", "path=$path;pathAndroidQ=$pathAndroidQ;rotation=$rotation;context=${this.context};")

        val returnMap: HashMap<String?, Any?> = HashMap()
        returnMap[pathParamKey] = path
        returnMap[orientationParamKey] = rotation.toLong()
        returnMap[metaWidthParamKey] = videoWidth.toInt()
        returnMap[metaHeightParamKey] = videoHeight.toInt()
        return returnMap
    }

    private fun getVideoInfoForUrl(path: String?, keyList: List<Int>): Map<Int, String?> {
        val resultMap = ArrayMap<Int, String?>()
        var mmr : MediaMetadataRetriever? =null
        try {
            mmr = MediaMetadataRetriever()
            mmr?.setDataSource(path)
            keyList.forEach { key->
                try {
                    resultMap[key] = mmr?.extractMetadata(key)
                } catch (t : Throwable) {
                    t.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                mmr?.release()
            } catch (t : Throwable) {
                t.printStackTrace()
            }
        }
        return resultMap
    }

    private fun getVideoInfoForUri(context: Context?, uri: Uri?, keyList: List<Int>): Map<Int, String?> {
        val resultMap = ArrayMap<Int, String?>()
        var mmr : MediaMetadataRetriever? =null
        try {
            mmr = MediaMetadataRetriever()
            mmr?.setDataSource(context, uri)
            keyList.forEach { key->
                try {
                    resultMap[key] = mmr?.extractMetadata(key)
                } catch (t : Throwable) {
                    t.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                mmr?.release()
            } catch (t : Throwable) {
                t.printStackTrace()
            }
        }
        return resultMap
    }

    private fun getRealPathAndroidQ(mediumId: String): String? {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return QUERY_URI.buildUpon().appendPath(mediumId).build().toString()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun cacheImage(mediumId: String, mimeType: String): HashMap<String?, Any?>? {
        val bitmap: Bitmap? = this.context?.run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(
                            this.contentResolver,
                            ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                mediumId.toLong()
                            )
                        )
                    )
                } catch (e: Exception) {
                    null
                }
            } else {
                MediaStore.Images.Media.getBitmap(
                    this.contentResolver,
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        mediumId.toLong()
                    )
                )
            }
        }

        bitmap?.let {
            val returnMap: HashMap<String?, Any?> = HashMap()
            val compressFormat: Bitmap.CompressFormat
            if (mimeType == "image/jpeg") {
                val path = File(getCachePath(), "$mediumId.jpeg")
                val out = FileOutputStream(path)
                compressFormat = Bitmap.CompressFormat.JPEG
                it.compress(compressFormat, compressByteRate, out)
                returnMap[pathParamKey] = path.absolutePath
                var exif = ExifInterface(path.absolutePath)
                val ori: Int = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
//                LogUtil.i(TAG, "图片路径为===" + path.absolutePath)
//                LogUtil.i(TAG, "图片旋转方向为===" + ori)
                returnMap[orientationParamKey] = ori

                return returnMap
            } else if (mimeType == "image/png") {
                val path = File(getCachePath(), "$mediumId.png")
                val out = FileOutputStream(path)
                compressFormat = Bitmap.CompressFormat.PNG
                it.compress(compressFormat, compressByteRate, out)
                returnMap[pathParamKey] = path.absolutePath;
                var exif = ExifInterface(path.absolutePath)
                val ori: Int = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
//                LogUtil.i(TAG, "图片路径为===" + path.absolutePath)
//                LogUtil.i(TAG, "图片旋转方向为===" + ori)
                returnMap[orientationParamKey] = ori
                return returnMap
            } else if (mimeType == "image/webp") {
                val path = File(getCachePath(), "$mediumId.webp")
                val out = FileOutputStream(path)
                compressFormat = Bitmap.CompressFormat.WEBP
                it.compress(compressFormat, compressByteRate, out)
                returnMap[pathParamKey] = path.absolutePath;
                var exif = ExifInterface(path.absolutePath)
                val ori: Int = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
//                LogUtil.i(TAG, "图片路径为===" + path.absolutePath)
//                LogUtil.i(TAG, "图片旋转方向为===" + ori)
                returnMap[orientationParamKey] = ori
                return returnMap
            }
        }

        return null
    }

    private fun getImageMetadata(cursor: Cursor): Map<String, Any?> {
        val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
        val filenameColumn = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
        val titleColumn = cursor.getColumnIndex(MediaStore.Images.Media.TITLE)
        val widthColumn = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
        val heightColumn = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
        val orientationColumn = cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION)
        val mimeColumn = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
        val dateTakenColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
        val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
//        val RELATIVE_PATH = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
//        LogUtil.i(TAG, "图片RELATIVE_PATHSUO==${RELATIVE_PATH.toString()}")
//        val relativePathColumn = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
        //获取文件路径
        val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
        val dataPath = cursor.getString(dataColumn)
//        val relativePath = cursor.getString(relativePathColumn)
//        LogUtil.i(TAG, "图片路径为===" + dataPath)
        val id = cursor.getLong(idColumn)
//        LogUtil.i(TAG, "图片idColumn==${id.toString()}")
        val filename = cursor.getString(filenameColumn)
//        LogUtil.i(TAG, "图片filename==${filename.toString()}")

        val title = cursor.getString(titleColumn)
//        LogUtil.i(TAG, "图片title==${title.toString()}")

        val width = cursor.getLong(widthColumn)
//        LogUtil.i(TAG, "图片width==${width.toString()}")

        val height = cursor.getLong(heightColumn)
//        LogUtil.i(TAG, "图片height==${height.toString()}")

        val orientation = cursor.getLong(orientationColumn)
//        LogUtil.i(TAG, "图片orientation==${orientation.toString()}")

        val mimeType = cursor.getString(mimeColumn)
//        LogUtil.i(TAG, "图片mimeType==${mimeType.toString()}")

        var dateTaken: Long = 0
        if (cursor.getType(dateTakenColumn) == FIELD_TYPE_INTEGER) {
            dateTaken = cursor.getLong(dateTakenColumn)
//            LogUtil.i(TAG, "图片dateTaken==${dateTaken.toString()}")

        }
        var dateModified: Long = 0
        if (cursor.getType(dateModifiedColumn) == FIELD_TYPE_INTEGER) {
            dateModified = cursor.getLong(dateModifiedColumn) * 1000
//            LogUtil.i(TAG, "dateModified==${dateModified.toString()}")

        }
////todo 获取原生图片路径
        return mapOf(
            "path" to dataPath.toString(),
//            "relativePath" to relativePath.toString(),
            "id" to id.toString(),
            "filename" to filename,
            "title" to title,
            "mediumType" to imageType,
            "width" to width,
            "height" to height,
            "orientation" to orientationDegree2Value(orientation),
            "mimeType" to mimeType,
            "creationDate" to dateTaken,
            "modifiedDate" to dateModified
        )
    }

    private fun getVideoMetadata(cursor: Cursor): Map<String, Any?> {
        val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
        val filenameColumn = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
        val titleColumn = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
        val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
        val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
        val mimeColumn = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
        val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
        val dateTakenColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
        val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
        //获取文件路径
        val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
        val dataPath = cursor.getString(dataColumn)


        val id = cursor.getLong(idColumn)
//        LogUtil.i(TAG, "视频id==${id.toString()}")
        val filename = cursor.getString(filenameColumn)
//        LogUtil.i(TAG, "视频filename==${filename.toString()}")

        val title = cursor.getString(titleColumn)
//        LogUtil.i(TAG, "视频title==${title.toString()}")

        val width = cursor.getLong(widthColumn)
//        LogUtil.i(TAG, "视频width==${width.toString()}")

        val height = cursor.getLong(heightColumn)
//        LogUtil.i(TAG, "视频height==${height.toString()}")


        val mimeType = cursor.getString(mimeColumn)
//        LogUtil.i(TAG, "视频mimeType==${mimeType.toString()}")

        val duration = cursor.getLong(durationColumn)
//        LogUtil.i(TAG, "视频duration==${duration.toString()}")

        var dateTaken: Long = 0
        if (cursor.getType(dateTakenColumn) == FIELD_TYPE_INTEGER) {
            dateTaken = cursor.getLong(dateTakenColumn)
//            LogUtil.i(TAG, "视频dateTaken==${dateTaken.toString()}")

        }
        var dateModified: Long = 0
        if (cursor.getType(dateModifiedColumn) == FIELD_TYPE_INTEGER) {
            dateModified = cursor.getLong(dateModifiedColumn) * 1000
//            LogUtil.i(TAG, "视频dateModified==${dateModified.toString()}")

        }
        var orientation = 0L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val orientationColumn = cursor.getColumnIndex(MediaStore.Video.Media.ORIENTATION)
            orientation = cursor.getLong(orientationColumn)
//            LogUtil.i(TAG, "视频orientation==${orientation.toString()}")

        }
        return mapOf(
            "path" to dataPath.toString(),
            "id" to id.toString(),
            "filename" to filename,
            "title" to title,
            "mediumType" to videoType,
            "width" to width,
            "orientation" to orientationDegree2Value(orientation),
            "height" to height,
            "mimeType" to mimeType,
            "duration" to duration,
            "creationDate" to dateTaken,
            "modifiedDate" to dateModified
        )
    }

    private fun orientationDegree2Value(degree: Long): Int {
        return when (degree) {
            0L -> 1
            90L -> 8
            180L -> 3
            270L -> 6
            else -> 0
        }
    }

    private fun getCachePath(): File? {
        return this.context?.run {
            val cachePath = File(this.cacheDir, "photo_gallery")
            if (!cachePath.exists()) {
                cachePath.mkdirs()
            }
            return@run cachePath
        }
    }

    private fun cleanCache() {
        val cachePath = getCachePath()
        cachePath?.deleteRecursively()
    }

    /**
     *新增加的方法,获取到图片和视频资源
     * 将这两个方法合并而成 listVideos  listImage
     * newest:是否是最新的
     * total:取多少数据(默认最多)
     * skip,take默认都是空,从而取出所有数据
     *
     */
    private fun listImageAndVideos(
        albumId: String,
        newest: Boolean,
        total: Int,
        skip: Int?,
        take: Int?
    ): Map<String, Any> {
        val allMedia = mutableListOf<Map<String, Any?>>()

        ///图片资源获取
        val mediaImage = mutableListOf<Map<String, Any?>>()
        val offsetImage = skip ?: 0
        val limitImage = take ?: (total - offsetImage)
        this.context?.run {
            val imageCursor: Cursor?

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                imageCursor = this.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    imageMetadataProjection,
                    android.os.Bundle().apply {
                        // Limit & Offset
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limitImage)
                        putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offsetImage)
                        // Sort
                        putStringArray(
                            android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                            arrayOf(
                                MediaStore.Images.Media.DATE_TAKEN,
                                MediaStore.Images.Media.DATE_MODIFIED
                            )
                        )
                        putInt(
                            android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                            if (newest) {
                                android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                            } else {
                                android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
                            }
                        )
                        // Selection
                        if (albumId != allAlbumId) {
                            putString(
                                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                                "${MediaStore.Images.Media.BUCKET_ID} = ?"
                            )
                            putStringArray(
                                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                                arrayOf(albumId)
                            )
                        }
                    },
                    null
                )
            } else {
                val orderBy = if (newest) {
                    "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                } else {
                    "${MediaStore.Images.Media.DATE_TAKEN} ASC, ${MediaStore.Images.Media.DATE_MODIFIED} ASC"
                }
                imageCursor = this.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    imageMetadataProjection,
                    if (albumId == allAlbumId) null else "${MediaStore.Images.Media.BUCKET_ID} = $albumId",
                    null,
                    "$orderBy LIMIT $limitImage OFFSET $offsetImage"
                )
            }

            imageCursor?.use { imageCursor ->
                while (imageCursor.moveToNext()) {
                    mediaImage.add(getImageMetadata(imageCursor))
                }
            }
        }


        ///视频资源
        val mediaVideo = mutableListOf<Map<String, Any?>>()
        val offsetVideo = skip ?: 0
        val limitVideo = take ?: (total - offsetVideo)

        this.context?.run {
            val videoCursor: Cursor?

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                videoCursor = this.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    videoMetadataProjection,
                    android.os.Bundle().apply {
                        // Limit & Offset
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limitVideo)
                        putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offsetVideo)
                        // Sort
                        putStringArray(
                            android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                            arrayOf(
                                MediaStore.Video.Media.DATE_TAKEN,
                                MediaStore.Video.Media.DATE_MODIFIED
                            )
                        )
                        putInt(
                            android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                            if (newest) {
                                android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                            } else {
                                android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
                            }
                        )
                        // Selection
                        if (albumId != allAlbumId) {
                            putString(
                                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                                "${MediaStore.Video.Media.BUCKET_ID} = ?"
                            )
                            putStringArray(
                                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                                arrayOf(albumId)
                            )
                        }
                    },
                    null
                )
            } else {
                val orderBy = if (newest) {
                    "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                } else {
                    "${MediaStore.Images.Media.DATE_TAKEN} ASC, ${MediaStore.Images.Media.DATE_MODIFIED} ASC"
                }
                videoCursor = this.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    videoMetadataProjection,
                    if (albumId == allAlbumId) null else "${MediaStore.Video.Media.BUCKET_ID} = $albumId",
                    null,
                    "$orderBy LIMIT $limitVideo OFFSET $offsetVideo"
                )
            }

            videoCursor?.use { videoCursor ->
                while (videoCursor.moveToNext()) {
                    mediaVideo.add(getVideoMetadata(videoCursor))
                }
            }
        }
        allMedia.addAll(mediaImage)
        allMedia.addAll(mediaVideo)
        ///排序
//        allMedia.sortBy { mediaMap -> mediaMap["modifiedDate"] as Long }
        allMedia.sortByDescending { mediaMap -> mediaMap["modifiedDate"] as Long }
//        allMedia.sortByDescending{ mediaMap -> mediaMap["creationDate"] as Long }
//        allMedia.sortBy{ mediaMap -> mediaMap["creationDate"] as Long }

        return mapOf(
            "newest" to newest,
            "start" to offsetVideo,
            "total" to total,
            "items" to allMedia
        )
    }
///获取相册图片的绝对路径
    /**
     * 根据图片的Uri获取图片的绝对路径(已经适配多种API)
     * @return 如果Uri对应的图片存在,那么返回该图片的绝对路径,否则返回null
     */
//    fun getRealPathFromCursor(context: Context, uri: Cursor): String? {
//        val sdkVersion: Int = Build.VERSION.SDK_INT
//        if (sdkVersion < 11) {
//            // SDK < Api11
//            return getRealPathFromUri_BelowApi11(context, uri)
//        }
//        return if (sdkVersion < 19) {
//            // SDK > 11 && SDK < 19
//            getRealPathFromUri_Api11To18(context, uri)
//        } else getRealFilePath(context, uri)
//        // SDK > 19
//    }

    /**
     * 根据图片的Uri获取图片的绝对路径(已经适配多种API)
     * @return 如果Uri对应的图片存在,那么返回该图片的绝对路径,否则返回null
     */
    fun getRealPathFromUri(context: Context, uri: Uri): String? {
        val sdkVersion: Int = Build.VERSION.SDK_INT
        if (sdkVersion < 11) {
            // SDK < Api11
            return getRealPathFromUri_BelowApi11(context, uri)
        }
        return if (sdkVersion < 19) {
            // SDK > 11 && SDK < 19
            getRealPathFromUri_Api11To18(context, uri)
        } else getRealFilePath(context, uri)
        // SDK > 19
    }


    /**
     * 适配api11-api18,根据uri获取图片的绝对路径
     */
    @SuppressLint("Range")
    private fun getRealPathFromUri_Api11To18(context: Context, uri: Uri): String? {
        var filePath: String? = null
        val projection = arrayOf<String>(MediaStore.Images.Media.DATA)
        val loader = CursorLoader(
            context, uri, projection, null,
            null, null
        )
        val cursor: Cursor = loader.loadInBackground()
        if (cursor != null) {
            cursor.moveToFirst()
            filePath = cursor.getString(cursor.getColumnIndex(projection[0]))
            cursor.close()
        }
        return filePath
    }

    /**
     * 适配api11以下(不包括api11),根据uri获取图片的绝对路径
     */
    private fun getRealPathFromUri_BelowApi11(context: Context, uri: Uri): String? {
        var filePath: String? = null
        val projection = arrayOf<String>(MediaStore.Images.Media.DATA)
        val cursor: Cursor? = context.getContentResolver().query(
            uri, projection,
            null, null, null
        )
        if (cursor != null) {
            cursor.moveToFirst()
            if (projection.size >= 0) {
                val columnIndex = cursor.getColumnIndex(projection[0])
                if (columnIndex >= 0) {
                    filePath = cursor.getString(columnIndex)
                }
            }
            cursor.close()
        }
        return filePath
    }
//    /**
//     * 适配api11以下(不包括api11),根据uri获取图片的绝对路径
//     */
//    private fun getRealPathFromCursor_BelowApi11(context: Context, cursor: Cursor): String? {
//        var filePath: String? = null
//        val projection = arrayOf<String>(MediaStore.Images.Media.DATA)
//        val cursor: Cursor? = context.getContentResolver().query(
//            uri, projection,
//            null, null, null
//        )
//        if (cursor != null) {
//            cursor.moveToFirst()
//            if (projection.size >= 0) {
//                val columnIndex = cursor.getColumnIndex(projection[0])
//                if (columnIndex >= 0) {
//                    filePath = cursor.getString(columnIndex)
//                }
//            }
//            cursor.close()
//        }
//        return filePath
//    }
    /**
     * @param context
     * @param uri
     * @return 文件绝对路径或者null
     */
    private fun getRealFilePath(context: Context, uri: Uri?): String? {
        if (null == uri) return null
        var scheme: String? = uri.scheme
        var data: String? = null
        if (scheme == null) data =
            uri.getPath() else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            data = uri.getPath()
        } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            val cursor: Cursor? = context.getContentResolver()
                .query(uri, arrayOf<String>(MediaStore.Images.ImageColumns.DATA), null, null, null)
            if (null != cursor) {
                if (cursor.moveToFirst()) {
                    val index: Int = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
                    if (index > -1) {
                        data = cursor.getString(index)
                    }
                }
                cursor.close()
            }
        }
        return data
    }

    private var activity: Activity? = null

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        this.activity = null
        clearAllThumbnailRequest()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        this.activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        this.activity = null
        clearAllThumbnailRequest()
    }

    private var isHarmony :Boolean? = null;

    //判断是否为鸿蒙
    fun isHarmonyOSa(): Boolean {
        val harmony = isHarmony
        if (harmony != null) {
            return harmony
        }
        try {
            val clz = Class.forName("com.huawei.system.BuildEx")
            val method = clz.getMethod("getOsBrand")
            val classLoader = clz.classLoader
            if (classLoader != null && classLoader.parent == null) {
                isHarmony = "harmony" == method.invoke(clz)
            }
        } catch (t: Throwable) {
            isHarmony = false
        }
        return isHarmony?:false
    }
}

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

class VideoThumbnailDecoder(private val context: Context?, private val localId: Long) : ResourceDecoder<ParcelFileDescriptor, Bitmap?> {

    override fun decode(source: ParcelFileDescriptor?, width: Int, height: Int): Resource<Bitmap?>? {
        try {
            val bitmap = MediaStore.Video.Thumbnails.getThumbnail(context?.contentResolver, localId, MediaStore.Video.Thumbnails.MINI_KIND, null)
            source?.close()
            return BitmapResource.obtain(bitmap, Glide.get(context).bitmapPool)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return null
    }

    override fun getId(): String {
        return ""
    }

}
