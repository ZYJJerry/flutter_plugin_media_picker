part of photogallery;

/// A medium in the gallery.
///
/// It can be of image or video [mediumType].
@immutable
class Medium {
  /// A unique identifier for the medium.
  final String id;

  /// The medium filename.
  final String? filename;

  /// The medium title
  final String? title;

  /// The medium path
  final String? path;

  /// The medium type.
  final MediumType? mediumType;

  /// The medium width.
  final int? width;

  /// The medium height.
  final int? height;

  /// The medium orientation.
  final int? orientation;

  /// The medium mimeType.
  final String? mimeType;

  /// The duration of video
  final int duration;

  /// The date at which the photo or video was taken.
  final DateTime? creationDate;

  /// The date at which the photo or video was modified.
  final DateTime? modifiedDate;

  const Medium({
    required this.id,
    this.filename,
    this.title,
    this.path,
    this.mediumType,
    this.width,
    this.height,
    this.orientation = 0,
    this.mimeType,
    this.duration = 0,
    this.creationDate,
    this.modifiedDate,
  });

  /// Creates a medium from platform channel protocol.
  Medium.fromJson(dynamic json)
      : id = json["id"],
        filename = json["filename"],
        title = json["title"],
        path = json["path"],
        mediumType = jsonToMediumType(json["mediumType"]),
        width = json["width"],
        height = json["height"],
        orientation = json["orientation"],
        mimeType = json["mimeType"],
        duration = json['duration'] ?? 0,
        creationDate = json['creationDate'] != null
            ? DateTime.fromMillisecondsSinceEpoch(json['creationDate'])
            : null,
        modifiedDate = json['modifiedDate'] != null
            ? DateTime.fromMillisecondsSinceEpoch(json['modifiedDate'])
            : null;

  static Medium fromMap(Map map) {
    return Medium(
      id: map['id'],
      filename: map['filename'],
      title: map['title'],
      path: map['path'],
      mediumType: jsonToMediumType(map['mediumType']),
      width: map['width'],
      height: map['height'],
      orientation: map['orientation'],
      mimeType: map["mimeType"],
      creationDate: map['creationDate'],
      modifiedDate: map['modifiedDate'],
    );
  }

  Map toMap() {
    return {
      "id": id,
      "filename": filename,
      "title": title,
      "path": path,
      "mediumType": mediumTypeToJson(mediumType),
      "height": height,
      "orientation": orientation,
      "mimeType": mimeType,
      "width": width,
      "creationDate": creationDate,
      "modifiedDate": modifiedDate,
    };
  }

  /// Get a JPEG thumbnail's data for this medium.
  Future<List<int>> getThumbnail({
    int? width,
    int? height,
    bool? highQuality = false,
  }) {
    return PhotoGallery.getThumbnail(
      mediumId: id,
      width: width,
      height: height,
      mediumType: mediumType,
      highQuality: highQuality,
    );
  }

  /// Get the original file.
  Future<PhotoGalleryFile> getFile() {
    return PhotoGallery.getFile(
      mediumId: id,
      mediumType: mediumType,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Medium &&
          runtimeType == other.runtimeType &&
          id == other.id &&
          filename == other.filename &&
          title == other.title &&
          path == other.path &&
          mediumType == other.mediumType &&
          width == other.width &&
          height == other.height &&
          orientation == other.orientation &&
          mimeType == other.mimeType &&
          creationDate == other.creationDate &&
          modifiedDate == other.modifiedDate;

  @override
  int get hashCode =>
      id.hashCode ^
      filename.hashCode ^
      title.hashCode ^
      path.hashCode ^
      mediumType.hashCode ^
      width.hashCode ^
      height.hashCode ^
      orientation.hashCode ^
      mimeType.hashCode ^
      creationDate.hashCode ^
      modifiedDate.hashCode;

  @override
  String toString() {
    return 'Medium{id: $id, '
        'filename: $filename, '
        'title: $title, '
        'path: $path, '
        'mediumType: $mediumType, '
        'width: $width, '
        'height: $height, '
        'orientation: $orientation, '
        'mimeType: $mimeType, '
        'creationDate: $creationDate, '
        'modifiedDate: $modifiedDate}';
  }
}
