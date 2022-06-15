import 'dart:io';
import 'dart:typed_data';

import 'package:photo_gallery/photo_gallery.dart';

class MediumInfo {
  final Medium medium;
  final Uint8List? bytes;
  final File? file;
  final int? orientation;
  final int? metaWidth;
  final int? metaHeight;

  MediumInfo(this.medium,{this.bytes,this.file, this.orientation, this.metaWidth, this.metaHeight});

  @override
  String toString() {
    return medium.toString();
  }

  String get id => medium.id;

  int get duration => medium.duration;

  MediumType? get type => medium.mediumType;

  int displayHeight = 1;

  int displayWidth = 1;
}
