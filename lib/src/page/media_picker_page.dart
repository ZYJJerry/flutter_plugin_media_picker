import 'dart:async';
import 'dart:typed_data';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_plugin_media_picker/media_picker.dart';
import 'package:flutter_plugin_media_picker/src/page/album_list_widget.dart';
import 'package:flutter_plugin_media_picker/src/widget/bottom_drag_widget.dart';
import 'package:flutter_plugin_media_picker/src/widget/media_item_widget.dart';
import 'package:photo_gallery/photo_gallery.dart';
import 'package:permission_handler/permission_handler.dart';

enum MediaPickerActionType {
  ///回调打开相册设置权限事件
  album,
  ///回调点击完成事件
  finish,
  ///回调打开相机设置权限事件
  camera,
  ///无相册权限点击取消
  cancel,
}

typedef CustomItemBuilder = Widget Function(
    int index, String mediaPath, int selectedIndex);

typedef MediaPickerActionCallBack = Function(MediaPickerActionType actionType);

///点击相册媒体列表里的某个item的回调事件
typedef MediaPickerItemClickCallBack = Function(
    List<MediumInfo> mediumInfoList, int clickPosition);

///获取到相册文件列表文件后的callBack
typedef MediaPickerMediumInfoListCreatedCallBack = Function(
    List<MediumInfo> mediumInfoList);

class MediaPickerPage extends StatefulWidget {
  static const String imageType = 'image';
  static const String videoType = 'video';
  static const String allType = 'all';

  final String? type;
  final int maxSelectCount; // 最大选择数量
  final int? crossAxisCount; // 每行显示多少项目
  final ListValueNotifier<MediumInfo> lastSelectMedia; // 上次选择的媒体项目
  final int? maxVideoDuration; // 筛选视频最大长度
  final double? defaultShowHeight; // 默认高度
  final double? maxHeight; // 最大高度
  final ValueNotifier? showBigViewNotifier; //显示大小窗的通知
  final ValueNotifier? canSelectedVideoNotifier; //视频能否选择通知
  final MediaPickerActionCallBack? actionCallBack; //自定义操作事件回调

  //点击相册媒体列表里的某个item的回调事件
  final MediaPickerItemClickCallBack? mediaPickerItemClickCallBack;

  //获取到相册文件列表文件后的callBack
  final MediaPickerMediumInfoListCreatedCallBack?
      mediaPickerMediumInfoListCreatedCallBack;

  const MediaPickerPage(
      {Key? key,
      this.type = allType,
      required this.maxSelectCount,
      this.crossAxisCount = 4,
      required this.lastSelectMedia,
      this.defaultShowHeight = 260,
      this.maxHeight = 600,
      this.maxVideoDuration,
      this.showBigViewNotifier,
      this.canSelectedVideoNotifier,
      this.actionCallBack,
      this.mediaPickerItemClickCallBack,
      this.mediaPickerMediumInfoListCreatedCallBack})
      : super(key: key);

  @override
  State<StatefulWidget> createState() => _MediaPickerPageState();
}

class _MediaPickerPageState extends State<MediaPickerPage> {
  static const int defaultMaxVideoDuration = 60 * 15 * 1000; // 单位毫秒
  final List<MediumInfo> _mediumInfoList = [];
  late ListValueNotifier<MediumInfo> _selectedMediaNotifier;
  Size? _thumbnailSize; //缩略图的大小
  late int _maxSelectCount;
  String albumName = '最近选项';
  bool? albumPermission;

  ValueNotifier<bool> endScrollValueNotifier = ValueNotifier(false);
  ValueNotifier<bool> cameraNotifier = ValueNotifier(true);
  ValueNotifier<bool> showAlbumListNotifier = ValueNotifier(false);

  bool isAddingItem = false; //是否在处理添加item

  final GlobalKey<DragContainerState> _dragKey =
      GlobalKey<DragContainerState>();

  UniqueKey _uniqueKey = UniqueKey();

  @override
  void initState() {
    super.initState();
    _obtainMediaInfo();
    _selectedMediaNotifier = widget.lastSelectMedia;
    _maxSelectCount = widget.maxSelectCount;
    _handleCameraStatus();
    widget.showBigViewNotifier?.addListener(() {
      if (widget.showBigViewNotifier!.value == true) {
        _dragKey.currentState?.handleStatus(true);
      } else {
        _dragKey.currentState?.handleStatus(false);
      }
    });
    endScrollValueNotifier.addListener(() {
      widget.showBigViewNotifier?.value = endScrollValueNotifier.value;
    });
    _selectedMediaNotifier.addListener(() {
      _maxSelectCount = widget.maxSelectCount;
      _selectedMediaNotifier.value.forEach((element) {
        if (element.type == MediumType.video) {
          _maxSelectCount = 1;
        }
      });
      _handleCameraStatus();
    });
    PaintingBinding.instance?.imageCache?.maximumSize = 2000; //图片缓存数量上限改成4000张
    PaintingBinding.instance?.imageCache?.maximumSizeBytes =
        500 * 1024 * 1024; //图片缓存大小上限改成1000M
  }

  @override
  void dispose() {
    super.dispose();
    PaintingBinding.instance?.imageCache?.clear();
    PaintingBinding.instance?.imageCache?.maximumSize =
        1000; //图片缓存数量上限改成系统默认1000张
    PaintingBinding.instance?.imageCache?.maximumSizeBytes =
        100 * 1024 * 1024; //图片缓存大小上限改成系统默认100M
    PhotoGallery.cleanCache();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _thumbnailSize = PhotoGalleryExtension.thumbnailSize(
        Size(MediaQuery.of(context).size.width / widget.crossAxisCount!,
            MediaQuery.of(context).size.width / widget.crossAxisCount!),
        context);
  }

  void _obtainMediaInfo({bool isAdd = false}) async {
    if (await Permission.photos.request().isGranted) {
      MediumType? type = jsonToMediumType(widget.type);
      if (type != null) {
        List<MediumInfo> list = await FlutterPluginMediaPicker.listMedium(
            _thumbnailSize, type,
            maxVideoDuration: widget.maxVideoDuration ?? defaultMaxVideoDuration);
        setState(() {
          _mediumInfoList.clear();
          _mediumInfoList.addAll(list);
          ///相册列表数据获取完成后回调
          if (widget.mediaPickerMediumInfoListCreatedCallBack != null) {
            widget.mediaPickerMediumInfoListCreatedCallBack!(_mediumInfoList);
          }
          if (isAdd == true) {
            ///拍完照后,重置key,确保列表刷新
            _uniqueKey = UniqueKey();
            _handleTakePhotoResult();
          }
        });
      }
    }
  }

  void _handleTakePhotoResult() async {
    if (_mediumInfoList.isEmpty) return;
    final bytes = await PhotoGallery.getThumbnail(
        mediumId: _mediumInfoList.first.id,
        mediumType: _mediumInfoList.first.medium.mediumType,
        width: _thumbnailSize!.width.toInt() * 2,
        height: _thumbnailSize!.height.toInt() * 2,
        highQuality: true);
    //添加文件路径
    final file = await PhotoGallery.getFile(mediumId: _mediumInfoList.first.id);
    _selectedMediaNotifier.add(MediumInfo(_mediumInfoList.first.medium,
        bytes: Uint8List.fromList(bytes), file: file.file));
  }

  void _loadAlbumData(Album album) async {
    MediumType? type = jsonToMediumType(widget.type);
    if (type != null) {
      List<MediumInfo> list = await FlutterPluginMediaPicker.listMedia(
          _thumbnailSize, album,
          mediumType: type,
          maxVideoDuration: widget.maxVideoDuration ?? defaultMaxVideoDuration);
      setState(() {
        _uniqueKey = UniqueKey();
        _mediumInfoList.clear();
        _mediumInfoList.addAll(list);

        ///相册列表数据获取完成后回调
        if (widget.mediaPickerMediumInfoListCreatedCallBack != null) {
          widget.mediaPickerMediumInfoListCreatedCallBack!(_mediumInfoList);
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        Align(
          alignment: Alignment.bottomCenter,
          child: DragContainer(
            key: _dragKey,
            drawer: getListView(),
            defaultShowHeight: widget.defaultShowHeight,
            height: widget.maxHeight,
            endScrollNotifier: endScrollValueNotifier,
          ),
        )
      ],
    );
  }

  Widget getListView() {
    return SizedBox(
      child: Column(
        children: <Widget>[_buildTopBar(), _buildContent()],
      ),
    );
  }

  Widget _buildTopBar() {
    return ValueListenableBuilder(
        valueListenable: endScrollValueNotifier,
        builder: (context, bool isLarge, child) {
          return GestureDetector(
            onTap: () {
              if (widget.showBigViewNotifier!.value == false) {
                _dragKey.currentState?.handleStatus(true);
              }
            },
            child: Container(
                width: double.infinity,
                height: isLarge ? 56 : 16,
                padding: EdgeInsets.only(top: isLarge ? 6 : 5),
                decoration: BoxDecoration(
                  color: Colors.white,
                  border: Border.all(width: 0, color: Colors.white),
                  borderRadius: BorderRadius.only(
                      topLeft: Radius.circular(isLarge ? 12 : 0),
                      topRight: Radius.circular(isLarge ? 12 : 0)),
                ),
                child: Column(
                  children: [
                    _buildDragIcon(isLarge),
                    _buildActionWidgets(isLarge)
                  ],
                )),
          );
        });
  }

  Widget _buildActionWidgets(bool isLarge) {
    if (isLarge == true &&
        albumPermission != null &&
        albumPermission == false) {
      return Expanded(
          child: Container(
        width: double.infinity,
        // color: Colors.red,
        padding: const EdgeInsets.only(left: 16),
        alignment: Alignment.centerLeft,
        child: GestureDetector(
          onTap: () {
            _dragKey.currentState!.handleStatus(false);
            _actionCallBack(MediaPickerActionType.cancel);
          },
          child: const Text(
            '取消',
            style: TextStyle(fontSize: 16, color: Color(0xFF222222)),
          ),
        ),
      ));
    } else {
      return isLarge == true
          ? Stack(
              children: [
                _buildRecentWidget(isLarge),
                _buildFinishWidget(isLarge)
              ],
            )
          : const SizedBox();
    }
  }

  Widget _buildDragIcon(bool isLarge) {
    return Image.asset(
      isLarge
          ? 'assets/images/picker_large.png'
          : 'assets/images/picker_normal.png',
      width: 24,
      height: isLarge ? 3 : 5,
      fit: BoxFit.cover,
      package: 'flutter_plugin_media_picker', /*使用这种方式 加载asset 下的文件报错*/
    );
  }

  Widget _buildRecentWidget(bool isLarge) {
    return Container(
      margin: const EdgeInsets.only(top: 12),
      child: GestureDetector(
        onTap: () {
          showAlbumListNotifier.value = !showAlbumListNotifier.value;
        },
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              albumName,
              style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w500,
                  color: Color(0xFF1F1F1F)),
            ),
            ValueListenableBuilder(
              valueListenable: showAlbumListNotifier,
              builder: (context, bool show, child) {
                return Image.asset(
                  show
                      ? 'assets/images/picker_arrow_up.png'
                      : 'assets/images/picker_arrow_down.png',
                  fit: BoxFit.cover,
                  width: 12,
                  height: 12,
                  package:
                      'flutter_plugin_media_picker', /*使用这种方式 加载asset 下的文件报错*/
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFinishWidget(bool isLarge) {
    return ValueListenableBuilder(
        valueListenable: showAlbumListNotifier,
        builder: (context, bool show, child) {
          return !show
              ? Positioned(
                  bottom: 0,
                  right: 10,
                  child: GestureDetector(
                    onTap: () {
                      _dragKey.currentState!.handleStatus(false);
                      _actionCallBack(MediaPickerActionType.finish);
                    },
                    child: ValueListenableBuilder(
                      valueListenable: _selectedMediaNotifier,
                      builder: (context, List<MediumInfo> value, child) {
                        if (value.isEmpty) {
                          _maxSelectCount = widget.maxSelectCount;
                        }
                        return Text(
                          '完成 ${value.length.toString()}/${_maxSelectCount.toString()}',
                          style: const TextStyle(
                              fontWeight: FontWeight.w500,
                              fontSize: 16,
                              color: Color(0xFF4D84C9)),
                        );
                      },
                    ),
                  ))
              : const SizedBox();
        });
  }

  Widget _buildContent() {
    return OverscrollNotificationWidget(
      child: _getGridView(),
    );
  }

  Widget _getGridView() {
    return Expanded(
      child: Container(
        color: Colors.white,
        child: Stack(
          children: [
            GridView.builder(
                addAutomaticKeepAlives: false,
                addRepaintBoundaries: false,
                key: _uniqueKey,
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: widget.crossAxisCount!,
                  crossAxisSpacing: 3,
                  mainAxisSpacing: 3,
                  childAspectRatio: 1,
                ),
                cacheExtent: 1,
                padding: const EdgeInsets.only(left: 3, right: 3),
                itemCount: _mediumInfoList.length + 1 + _getPlaceHolderCount(),
                scrollDirection: Axis.vertical,
                itemBuilder: (context, index) {
                  return _buildItem(index);
                }),
            AlbumListWidget(
              type: jsonToMediumType(widget.type) ?? MediumType.all,
              maxHeight: widget.maxHeight!,
              showValueNotifier: showAlbumListNotifier,
              onAlbumClickedCallBack: (Album album) {
                albumName = album.name ?? albumName;
                _loadAlbumData(album);
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildItem(int index) {
    if (index == 0) {
      return _buildCameraWidget();
    } else if (index > _mediumInfoList.length) {
      return const SizedBox();
    } else {
      return MediaItemWidget(
        index: index,
        mediumInfo: _mediumInfoList[index - 1],
        maxSelectCount: widget.maxSelectCount,
        thumbnailSize: _thumbnailSize ?? const Size(0, 0),
        selectedMediaNotifier: _selectedMediaNotifier,
        onMediaItemClickListener: _onMediaItemClick,
        canSelectedVideoNotifier: widget.canSelectedVideoNotifier,
        itemAddCallBack: _onItemAddCallBack,
      );
    }
  }

  Widget _buildCameraWidget() {
    return ValueListenableBuilder(
        valueListenable: cameraNotifier,
        builder: (context, value, child) {
          return GestureDetector(
            onTap: () {
              if (value == true) _takePhoto();
            },
            child: Container(
              alignment: Alignment.center,
              decoration: const BoxDecoration(
                color: Color(0xFF98999A),
                borderRadius: BorderRadius.all(Radius.circular(4.0)),
              ),
              child: Stack(
                alignment: Alignment.center,
                children: [
                  Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Image.asset(
                        'assets/images/camera_icon.png',
                        width: 32,
                        height: 32,
                        fit: BoxFit.cover,
                        package:
                            'flutter_plugin_media_picker', /*使用这种方式 加载asset 下的文件报错*/
                      ),
                      const SizedBox(
                        height: 2,
                      ),
                      const Text(
                        '拍摄',
                        style: TextStyle(color: Colors.white, fontSize: 12),
                      ),
                    ],
                  ),
                  value == false
                      ? Container(
                          color: const Color(0xB3FFFFFF),
                        )
                      : const SizedBox()
                ],
              ),
            ),
          );
        });
  }

  void _takePhoto() async {
    if (await Permission.camera.request().isGranted) {
      var imgPath = await MediaPicker.takePhoto(quality: 100);
      if (imgPath.isNotEmpty) {
        ///Android平台存入数据库,读取相册数据,有一定的延迟
        if (Platform.isAndroid) {
          Future.delayed(const Duration(milliseconds: 600), () {
            _obtainMediaInfo(isAdd: true);
          });
        } else {
          _obtainMediaInfo(isAdd: true);
        }
      }
    } else {
      _actionCallBack(MediaPickerActionType.camera);
    }
  }

  void _onMediaItemClick(
      MediumInfo mediumInfo, bool isSelect, int realPositionInAlbumList) async {
    _maxSelectCount = widget.maxSelectCount;
    if (widget.mediaPickerItemClickCallBack != null) {
      widget.mediaPickerItemClickCallBack!(
          _mediumInfoList, realPositionInAlbumList);
    }
  }

  void _onItemAddCallBack(MediumInfo mediumInfo) async {
    var values = widget.lastSelectMedia.value;
    bool isContains = false;
    late MediumInfo tempMedium;
    values.forEach((element) {
      if (element.id == mediumInfo.id) {
        isContains = true;
        tempMedium = element;
      }
    });
    if (isContains) {
      widget.lastSelectMedia.remove(tempMedium);
    } else {
      if (!isAddingItem) {
        isAddingItem = true;
        final bytes = await PhotoGallery.getThumbnail(
            mediumId: mediumInfo.id,
            mediumType: mediumInfo.medium.mediumType,
            width: _thumbnailSize!.width.toInt() * 2,
            height: _thumbnailSize!.height.toInt() * 2,
            highQuality: true);
        if (mediumInfo.type == MediumType.video) {
          //添加文件路径
          final file = await PhotoGallery.getFile(mediumId: mediumInfo.id);
          widget.lastSelectMedia.add(MediumInfo(mediumInfo.medium,
              bytes: Uint8List.fromList(bytes),
              file: file.file,
              orientation: file.orientation,
              metaWidth: file.metaWidth,
              metaHeight: file.metaHeight));
          isAddingItem = false;
        } else {
          widget.lastSelectMedia.add(
              MediumInfo(mediumInfo.medium, bytes: Uint8List.fromList(bytes)));
          isAddingItem = false;
        }
      }
    }
  }

  void _handleCameraStatus() {
    bool isEnable = true;
    if (_selectedMediaNotifier.value.length >= _maxSelectCount) {
      isEnable = false;
    } else {
      _selectedMediaNotifier.value.forEach((element) {
        if (element.type == MediumType.video) {
          _maxSelectCount = 1;
          isEnable = false;
          return;
        }
      });
    }
    cameraNotifier.value = isEnable;
  }

  void _actionCallBack(MediaPickerActionType type) {
    if (widget.actionCallBack != null) {
      widget.actionCallBack!(type);
    }
  }

  //由于半屏导致部分item展示不全，这里做处理
  int _getPlaceHolderCount() {
    double height = widget.maxHeight! - widget.defaultShowHeight!;
    if (height <= 0) return 0;
    double screenHeight = MediaQuery.of(context).size.width;
    double itemHeight = (screenHeight - (widget.crossAxisCount! + 1) * 3) /
        widget.crossAxisCount!;
    int count = (height / itemHeight).ceil() * widget.crossAxisCount!;
    return count;
  }
}
