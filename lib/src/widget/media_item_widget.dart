import 'package:common_utils/common_utils.dart';
import 'package:flutter/material.dart';
import 'package:flutter_plugin_media_picker/media_picker.dart';
import 'package:photo_gallery/photo_gallery.dart';

typedef OnMediaItemClickListener = void Function(
    MediumInfo mediumInfo, bool isSelect, int realPositionInAlbumList);
typedef OnMediaItemAddCallBack = void Function(MediumInfo mediumInfo);

class MediaItemWidget extends StatefulWidget {
  final int index;
  final MediumInfo mediumInfo;
  final int maxSelectCount;
  final Size thumbnailSize;
  final ListValueNotifier<MediumInfo> selectedMediaNotifier;
  final OnMediaItemClickListener? onMediaItemClickListener;
  final ValueNotifier? canSelectedVideoNotifier;
  final OnMediaItemAddCallBack itemAddCallBack;

  const MediaItemWidget(
      {Key? key,
      required this.index,
      required this.mediumInfo,
      required this.maxSelectCount,
      required this.thumbnailSize,
      required this.selectedMediaNotifier,
      this.onMediaItemClickListener,
      this.canSelectedVideoNotifier,
      required this.itemAddCallBack})
      : super(key: key);

  @override
  State<StatefulWidget> createState() => _MediaItemWidgetState();
}

class _MediaItemWidgetState extends State<MediaItemWidget> {
  ValueNotifier<bool> isSelectedNotifier = ValueNotifier(false);
  late int _maxSelectCount;
  ValueNotifier<bool> isCanBeSelectedNotifier = ValueNotifier(true);

  @override
  void initState() {
    super.initState();
    _maxSelectCount = widget.maxSelectCount;
    _handleCanBeSelected();
    _handleIsSelected();
    widget.selectedMediaNotifier.addListener(() {
      _maxSelectCount = widget.maxSelectCount;
      _handleCanBeSelected();
      _handleIsSelected();
    });
    widget.canSelectedVideoNotifier?.addListener(() {
      if (widget.mediumInfo.type == MediumType.video) {
        _handleCanBeSelected();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      clipBehavior: Clip.hardEdge,
      decoration: const BoxDecoration(
        borderRadius: BorderRadius.all(Radius.circular(4.0)),
      ),
      child: Stack(
        children: [
          createNormalItemWidget(widget.index, widget.mediumInfo),
          createSelectedIndexTagWidget(widget.index, widget.mediumInfo),
          _buildCoverWidget(),
        ],
      ),
    );
  }

  Widget _buildCoverWidget() {
    return ValueListenableBuilder(
        valueListenable: isCanBeSelectedNotifier,
        builder: (context, value, child) {
          if (value == false && isSelectedNotifier.value == false) {
            return GestureDetector(
              onTap: () {},
              child: Container(
                color: const Color(0xB3FFFFFF),
              ),
            );
          }
          return const SizedBox();
        });
  }

  void _onClickMediaItem() async {
    var values = widget.selectedMediaNotifier.value;
    bool isContains = false;
    values.forEach((element) {
      if (element.id == widget.mediumInfo.id) {
        isContains = true;
      }
    });
    widget.onMediaItemClickListener
        ?.call(widget.mediumInfo, !isContains, widget.index - 1);
  }

  void _handleItemSelectedStatus() {
    if (widget.mediumInfo.type == MediumType.video &&
        widget.mediumInfo.duration > 15 * 60 * 1000) {
      return;
    }
    widget.itemAddCallBack(widget.mediumInfo);
  }

  Widget createNormalItemWidget(int index, MediumInfo mediumInfo) {
    return defaultNormalItemWidget(mediumInfo);
  }

  Widget defaultNormalItemWidget(MediumInfo mediumInfo) {
    return GestureDetector(
      onTap: _onClickMediaItem,
      child: Stack(
        alignment: Alignment.center,
        children: [
          FadeInImage(
            placeholder: const AssetImage(
              'assets/images/default_square.png',
              package: 'flutter_plugin_media_picker',
              /*使用这种方式 加载asset 下的文件报错*/
            ),
            fadeOutDuration: const Duration(milliseconds: 100),
            fadeInDuration: const Duration(milliseconds: 100),
            image: _buildImage(mediumInfo),
            fit: BoxFit.cover,
            width: double.infinity,
            height: double.infinity,
          ),
          Positioned(
              top: 0,
              right: 0,
              child: GestureDetector(
                onTap: _handleItemSelectedStatus,
                child: Container(
                  width: 45,
                  height: 45,
                  color: Colors.transparent,
                  alignment: Alignment.topRight,
                  padding: const EdgeInsets.only(top: 4, right: 4),
                  child: Image.asset(
                    'assets/images/unselected_icon.png',
                    width: 24,
                    height: 24,
                    fit: BoxFit.cover,
                    package: 'flutter_plugin_media_picker', /*使用这种方式 加载asset 下的文件报错*/
                  ),
                ),
              )),
          _buildVideoDuration()
        ],
      ),
    );
  }

  ImageProvider _buildImage(MediumInfo mediumInfo) {
      return ThumbnailProvider(
        mediumId: mediumInfo.id,
        mediumType: mediumInfo.type,
        width: widget.thumbnailSize.width.toInt(),
        height: widget.thumbnailSize.width.toInt(),
        highQuality: true,
      );
    // }
  }

  Widget _buildVideoDuration() {
    return widget.mediumInfo.medium.mediumType == MediumType.video
        ? Positioned(
            right: 0,
            bottom: 0,
            left: 0,
            child: Container(
              height: 32,
              padding: const EdgeInsets.only(right: 4, bottom: 4),
              alignment: Alignment.bottomRight,
              decoration: BoxDecoration(
                  gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                    Colors.black.withAlpha(0),
                    Colors.black.withAlpha(30)
                  ])),
              child: Text(
                DateUtil.formatDateMs(widget.mediumInfo.medium.duration,
                    format: 'HH:mm:ss', isUtc: true),
                style: const TextStyle(color: Colors.white, fontSize: 12),
              ),
            ))
        : const SizedBox();
  }

  /*被选中时选中顺序widget实现*/
  Widget createSelectedIndexTagWidget(
    int index,
    MediumInfo mediumInfo,
  ) {
    return ValueListenableBuilder(
        valueListenable: isSelectedNotifier,
        builder: (context, value, child) {
          return value == true
              ? defaultSelectedIndexTagWidget()
              : const SizedBox();
        });
  }

  Widget defaultSelectedIndexTagWidget() {
    return Positioned(
      right: 0,
      top: 0,
      child: GestureDetector(
        onTap: _handleItemSelectedStatus,
        child: Container(
          width: 45,
          height: 45,
          color: Colors.transparent,
          alignment: Alignment.topRight,
          padding: const EdgeInsets.only(top: 4, right: 4),
          child: Image.asset(
            'assets/images/selected_icon.png',
            width: 24,
            height: 24,
            fit: BoxFit.cover,
            package: 'flutter_plugin_media_picker', /*使用这种方式 加载asset 下的文件报错*/
          ),
        ),
      ),
    );
  }

  //处理是否可被选中
  void _handleCanBeSelected() {
    bool canBeSelected = true;
    bool isContainsVideo = false;
    bool isContainsImage = false;
    bool hasContained = false;

    if (widget.mediumInfo.type == MediumType.video &&
        widget.canSelectedVideoNotifier?.value == false) {
      isCanBeSelectedNotifier.value = false;
      return;
    }

    widget.selectedMediaNotifier.value.forEach((element) {
      if (element.medium.mediumType == MediumType.video) {
        _maxSelectCount = 1;
        isContainsVideo = true;
      }
      if (element.medium.mediumType == MediumType.image) {
        isContainsImage = true;
      }
      if (element.id == widget.mediumInfo.id) {
        hasContained = true;
      }
    });
    bool isSelectedMax =
        widget.selectedMediaNotifier.value.length >= _maxSelectCount;
    if (isSelectedMax ||
        isContainsVideo ||
        hasContained ||
        (isContainsImage && widget.mediumInfo.type == MediumType.video)) {
      canBeSelected = false;
    } else {
      canBeSelected = true;
    }
    isCanBeSelectedNotifier.value = canBeSelected;
  }

  //处理是否被选中
  void _handleIsSelected() {
    bool isMediaSelected = false;
    widget.selectedMediaNotifier.value.forEach((element) {
      if (element.id == widget.mediumInfo.id) {
        isMediaSelected = true;
        return;
      }
    });
    isSelectedNotifier.value = isMediaSelected;
  }
}
