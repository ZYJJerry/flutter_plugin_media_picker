import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_plugin_media_picker/src/entity/medium_info.dart';
import 'package:photo_gallery/photo_gallery.dart';

class FlutterPluginMediaPicker {
  static const MethodChannel _channel =
      MethodChannel('flutter_plugin_media_picker');

  static const int defaultMaxVideoDuration = 60 * 15;

  static Future<Map<String, String>> _obtainMediumPathMap(
      MediumType mediumType) async {
    Map<String, String> mediumPathMap = {};
    final json = await _channel.invokeMethod("listMediumPath", {
      'mediumType': mediumTypeToJson(mediumType),
    });
    json.forEach((x) => mediumPathMap[x["id"]] = x["path"]);
    return mediumPathMap;
  }

  static Future<List<MediumInfo>> listMedium(Size? thumbnailSize, MediumType mediumType,
      {int maxVideoDuration = defaultMaxVideoDuration}) async {
    List<Medium> mediumList = await PhotoGalleryExtension.listMedium(mediumType: mediumType);
    return mediumList
        .where((e) => (((e.width ?? 0) > 0 && (e.height ?? 0) > 0) ||(e.duration >0))) // 过滤不合法的图片和视频
        // .where((e) => e.duration <= maxVideoDuration * 1000) // 时间单位转换
        .map<MediumInfo>((e) => getMediumInfo(e, thumbnailSize))
        .toList();
  }

  static Future<List<MediumInfo>> listMedia(Size? thumbnailSize, Album album,
      {MediumType mediumType = MediumType.all,
      int maxVideoDuration = defaultMaxVideoDuration}) async {
    List<Medium> mediumList = await PhotoGalleryExtension.listMedia(
      album,
      mediumType: mediumType
    );
    var list = mediumList
        .where((e) => (((e.width ?? 0) > 0 && (e.height ?? 0) > 0) ||(e.duration >0))) // 过滤不合法的图片和视频
        // .where((e) => e.duration <= maxVideoDuration * 1000) // 时间单位转换
        .map<MediumInfo>((e) => getMediumInfo(e, thumbnailSize))
        .toList();
    return list;
  }

  static MediumInfo getMediumInfo(Medium e, Size? thumbnailSize) {
    var mediumInfo = MediumInfo(e);
    var width = e.width??0;
    var height = e.height??0;
    if (thumbnailSize != null) {
      mediumInfo.displayHeight = thumbnailSize.height.toInt();
      mediumInfo.displayWidth = thumbnailSize.width.toInt();

      if(width > 0 && height > 0) {
        var ration = width / height.toDouble();
        if(Platform.isAndroid) {
          // 处理Android旋转角度 90 270
          if(e.orientation == 6 || e.orientation == 8) {
            ration = 1 / ration;
          }
        }
        bool isLong = ration >= 5 || ration <= 0.2;
        if(ration >= 0) {
          mediumInfo.displayHeight = isLong ? thumbnailSize.height.toInt() * 8 : thumbnailSize.height.toInt();
          mediumInfo.displayWidth = (mediumInfo.displayHeight * ration).toInt();
        } else {
          mediumInfo.displayWidth = isLong ? thumbnailSize.width.toInt() * 8 : thumbnailSize.width.toInt();
          mediumInfo.displayHeight = mediumInfo.displayWidth ~/ ration;
        }
      }
    }
    return mediumInfo;
  }
}

class PhotoGalleryExtension extends PhotoGallery {
  static const MethodChannel _channel = MethodChannel('photo_gallery');
  static const String _allAlbumId = "__ALL__";
  static const int _defaultTotal = 1 << 16; // 给一个极大值

  static Future<List<Medium>> listMedium(
      {MediumType mediumType = MediumType.all}) async {
    final json = await _channel.invokeMethod('listMedia', {
      'albumId': _allAlbumId,
      'mediumType': mediumTypeToJson(mediumType),
      'newest': true,
      'total': _defaultTotal,
    });
    return json['items'].map<Medium>((x) => Medium.fromJson(x)).toList();
  }

  static Future<List<Medium>> listMedia(Album album,
      {MediumType mediumType = MediumType.all}) async {
    final json = await _channel.invokeMethod('listMedia', {
      'albumId': album.id,
      'mediumType': mediumTypeToJson(mediumType),
      'newest': true,
      'total': _defaultTotal,
    });
    return json['items'].map<Medium>((x) => Medium.fromJson(x)).toList();
  }

  // 由于photo_gallery未对安卓缩略图做像素比放大处理，这里做了处理
  static Size thumbnailSize(Size originalSize, BuildContext context) {
    if (Platform.isAndroid) {
      return Size(originalSize.width * MediaQuery.of(context).devicePixelRatio * 0.4,
          originalSize.height * MediaQuery.of(context).devicePixelRatio * 0.4);
    } else {
      return Size(originalSize.width, originalSize.height);
    }
  }
}
