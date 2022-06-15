part of photogallery;

/// A medium type.
enum MediumType {
  all,
  image,
  video,
}

String? mediumTypeToJson(MediumType? value) {
  switch (value) {
    case MediumType.image:
      return 'image';
    case MediumType.video:
      return 'video';
    case MediumType.all:
      return 'all';
    default:
      return null;
  }
}

MediumType? jsonToMediumType(String? value) {
  switch (value) {
    case 'image':
      return MediumType.image;
    case 'video':
      return MediumType.video;
    case 'all':
      return MediumType.all;
    default:
      return null;
  }
}
