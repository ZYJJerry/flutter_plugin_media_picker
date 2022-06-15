

import 'dart:io';

class PhotoGalleryFile {
  final File file;
  final int? orientation;
  final int? metaWidth;
  final int? metaHeight;

  PhotoGalleryFile(this.file, {this.orientation,this.metaWidth,this.metaHeight});

}