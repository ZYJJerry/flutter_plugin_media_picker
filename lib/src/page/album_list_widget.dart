import 'package:flutter/material.dart';
import 'package:photo_gallery/photo_gallery.dart';
import 'package:transparent_image/transparent_image.dart';


typedef OnAlbumClickedCallBack = void Function(Album album);

class AlbumListWidget extends StatefulWidget {

  final MediumType type;
  final OnAlbumClickedCallBack onAlbumClickedCallBack;
  final ValueNotifier<bool> showValueNotifier;
  final double maxHeight;

  const AlbumListWidget({Key? key, required this.type, required this.maxHeight, required this.showValueNotifier, required this.onAlbumClickedCallBack,})
      : super(key: key);

  @override
  State<StatefulWidget> createState() => _AlbumListWidgetState();
}

class _AlbumListWidgetState extends State<AlbumListWidget> {
  final ValueNotifier<List<Album>> albumNotifier = ValueNotifier([]);

  @override
  void initState() {
    super.initState();
    _listData();
  }

  void _listData() async {
    var data = await PhotoGallery.listAlbums(mediumType: widget.type);
    albumNotifier.value = data;
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder(
        valueListenable: widget.showValueNotifier,
        builder: (context, value, child) {
          return Container(
              width: double.infinity,
              height: value == true ? widget.maxHeight : 0,
              decoration: BoxDecoration(
                color: Colors.white,
                border: Border.all(width: 0, color: Colors.white),
              ),
              child: ValueListenableBuilder(
                valueListenable: albumNotifier,
                builder: (context, List<Album> albums, child) {
                  return MediaQuery.removePadding(
                    removeTop: true,
                    context: context,
                    child: ListView.builder(
                      itemCount: albums.length,
                      itemExtent: 84.0,
                      itemBuilder: (BuildContext context, int index) {
                        return _buildItem(albums[index]);
                      },
                    ),
                  );
                },
              ));
        });
  }
  
  Widget _buildItem(Album album) {
    return GestureDetector(
      onTap: () {
        widget.onAlbumClickedCallBack(album);
        widget.showValueNotifier.value = false;
      },
      child: Container(
        color: Colors.white,
          padding: const EdgeInsets.only(left: 16),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              _buildAlumCover(album),
              const SizedBox(width: 12,),
              Text(
                  "${album.name}",
                  style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                      color: Color(0xFF1F1F1F)),
                ),
              const SizedBox(width: 3,),
              Text(
                '${album.count}',
                style: const TextStyle(fontSize: 12, color: Color(0xFF9F9F9F)),
              ),
            ],
          )
      ),
    );
  }

  Widget _buildAlumCover(Album album) {
    return Container(
      clipBehavior: Clip.hardEdge,
      decoration: const BoxDecoration(
        color: Color(0xFFD8D8D8),
        borderRadius: BorderRadius.all(Radius.circular(6)),
      ),
      child: FadeInImage(
        placeholder: MemoryImage(kTransparentImage),
        fadeOutDuration: const Duration(milliseconds: 100),
        fadeInDuration: const Duration(milliseconds: 100),
        image: AlbumThumbnailProvider(
          albumId: album.id,
          width: 68,
          height: 68,
          highQuality: true,
        ),
        fit: BoxFit.cover,
        width: 68,
        height: 68,
      ),
    );
  }
}
