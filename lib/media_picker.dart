library media_picker;

import 'dart:typed_data';

import 'package:flutter/widgets.dart';
import 'package:image_gallery_saver/image_gallery_saver.dart';
import 'package:image_picker/image_picker.dart';
export 'src/page/media_picker_page.dart';
export 'src/entity/medium_info.dart';
export 'src/extension/change_notifier_extension.dart';
export 'src/flutter_plugin_media_picker.dart';


class MediaPicker {
  static Future<String> takePhoto(
      {double? maxWidth, double? maxHeight, int? quality}) async {
    final ImagePicker _picker = ImagePicker();
    final PickedFile? pickedFile = await _picker.getImage(
      source: ImageSource.camera,
      maxWidth: maxWidth,
      maxHeight: maxHeight,
      imageQuality: quality,
    );
    if (pickedFile != null) {
      var data = await pickedFile.readAsBytes();
        //写入系统相册
      final result = await ImageGallerySaver.saveImage(Uint8List.fromList(data),
          quality: 60, name: "image${DateTime.now().microsecond.toString()}",path:pickedFile.path);
      debugPrint('保存的文件路径为${pickedFile.path.toString()}');
      return pickedFile.path;
    } else {
      return '';
    }
  }

  static Future<String> recordVideo({int? maxDuration}) async {
    final ImagePicker _picker = ImagePicker();
    final PickedFile? pickedFile = await _picker.getVideo(
        source: ImageSource.camera,
        maxDuration: maxDuration == null
            ? const Duration(seconds: 10)
            : Duration(seconds: maxDuration));
    return pickedFile?.path ?? '';
  }
}
