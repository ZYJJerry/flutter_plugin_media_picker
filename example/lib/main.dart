
import 'dart:io';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_plugin_media_picker/media_picker.dart';
import 'package:permission_handler/permission_handler.dart';

import 'new_page.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  var takePhotoPath = "";
  var takeVideoPath = "";
  var errorMessage = "";
  int imagesCount = 4;
  int crossAxisCount = 3;
  int videosCount = 4;
  int videoDuration = 10;
  ListValueNotifier<MediumInfo> selectImages = ListValueNotifier([]);
  var selectVideos = [];

  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {

    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body:
        const Demo(),
          // _buildList(context),
      ),
    );
  }

  _getTakePhotoImage() {
    if (takePhotoPath == '') {
      return Container();
    } else {
      return Image.file(
        File(takePhotoPath),
        fit: BoxFit.cover,
      );
    }
  }

  _getImageGridView() {
    return SizedBox(
      height: selectImages.value.isNotEmpty ? 200 : 0,
      child: GridView.builder(
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 2,
            childAspectRatio: 1,
            crossAxisSpacing: 5.0,
            mainAxisSpacing: 5.0,
          ),
          itemCount: selectImages.value.length,
          scrollDirection: Axis.horizontal,
          itemBuilder: (context, index) {
            return _getImageItem(selectImages.value[index]);
          }),
    );
  }

  _getVideoGridView() {
    return SizedBox(
      height: selectVideos.isNotEmpty ? 100 : 0,
      child: GridView.builder(
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 2,
            childAspectRatio: 0.2,
            crossAxisSpacing: 5.0,
            mainAxisSpacing: 5.0,
          ),
          itemCount: selectVideos.length,
          scrollDirection: Axis.horizontal,
          itemBuilder: (context, index) {
            return _getVideoItem(selectVideos[index]);
          }),
    );
  }

  _getVideoItem(String path) {
    return Container(
      color: Colors.blue,
      height: 10,
      child: Text(path),
    );
  }

 Widget _getImageItem(MediumInfo info) {
    if(info.bytes != null){
      return GestureDetector(
        onTap: () {
          var value = selectImages.value;
          value.removeWhere((element) => element.id == info.id);
          selectImages.value = value;
          setState(() {

          });
        },
        child: Image.memory(info.bytes!, fit: BoxFit.cover,
          width: 100,
          height: 100,
        ),
      );
    }else {
      return Container();
    }

  }

  void getImages(BuildContext context) async {
    var status = await Permission.storage.request();
    if (status != PermissionStatus.granted) {
      errorMessage = "请先同意相应的权限";
      setState(() {});
      return;
    }
    if (imagesCount <= 0) {
      return;
    }
    var result = await Navigator.push<List<dynamic>>(
        context,
        MaterialPageRoute(
            builder: (BuildContext context) => MediaPickerPage(
                type: MediaPickerPage.imageType,
                maxSelectCount: imagesCount,
                lastSelectMedia: selectImages,
                crossAxisCount: crossAxisCount)));

    if (result != null && result is List<MediumInfo>) {
      selectImages.value = result;
      setState(() {});
    }
  }

  void getVideos(BuildContext context) async {
    var status = await Permission.storage.request();
    if (status != PermissionStatus.granted) {
      errorMessage = "请先同意相应的权限";
      setState(() {});
      return;
    }
    if (videosCount <= 0) {
      return;
    }
    var result = await Navigator.push<List<dynamic>>(
        context,
        MaterialPageRoute(
            builder: (BuildContext context) => MediaPickerPage(
              type: MediaPickerPage.videoType,
              maxSelectCount: videosCount,
              maxVideoDuration: 60,
              lastSelectMedia: selectImages,
              crossAxisCount: crossAxisCount,
            )));

    if (result != null) {
      selectVideos = result;
      setState(() {});
    }
  }

  void takePhoto() async {
    // var status = await [Permission.storage, Permission.photos].request();
    // if (status[Permission.storage] != PermissionStatus.granted ||
    //     status[Permission.camera] != PermissionStatus.granted) {
    //   errorMessage = "请先同意相应的权限";
    //   setState(() {});
    //   return;
    // }
    var imgPath = await MediaPicker.takePhoto(quality: 100);
    setState(() {
      takePhotoPath = imgPath;
    });
  }

  void recordVideo() async {
    // var status = await [Permission.storage, Permission.camera].request();
    // if (status[Permission.storage] != PermissionStatus.granted ||
    //     status[Permission.camera] != PermissionStatus.granted) {
    //   errorMessage = "请先同意相应的权限";
    //   setState(() {});
    //   return;
    // }
    if (videoDuration <= 0) {
      return;
    }
    var videoPath = await MediaPicker.recordVideo(maxDuration: videoDuration);
    setState(() {
      takeVideoPath = videoPath;
    });
  }

  Widget _buildList(BuildContext context){
  return  ListView(
      children: [
        const SizedBox(
          height: 10,
        ),
        SizedBox(
          // color: Colors.blue,
          height: 40,
          child: Row(
            children: [
              Builder(builder: (context) {
                return ElevatedButton(
                  onPressed: () {
                    getImages(context);
                  },
                  child: const Text("点击选择图片"),
                );
              }),
              const SizedBox(
                width: 10,
              ),
              ToggleButtons(
                isSelected: const [true, true, true],
                children: <Widget>[
                  const Icon(Icons.exposure_minus_1),
                  Text(
                    '$imagesCount',
                    style: const TextStyle(
                        fontWeight: FontWeight.bold, fontSize: 20),
                  ),
                  const Icon(Icons.exposure_plus_1),
                ],
                onPressed: (index) {
                  switch (index) {
                    case 0:
                      imagesCount -= 1;
                      if (imagesCount <= 0) {
                        imagesCount = 0;
                      }
                      setState(() {});
                      break;
                    case 2:
                      imagesCount += 1;
                      setState(() {});
                      break;
                  }
                },
              ),
            ],
          ),
        ),
        const Divider(
          height: 10,
        ),
        _getImageGridView(),
        SizedBox(
          // color: Colors.blue,
          height: 40,
          child: Row(
            children: [
              Builder(builder: (context) {
                return ElevatedButton(
                  onPressed: () {
                    getVideos(context);
                  },
                  child: const Text("点击选择视频"),
                );
              }),
              const SizedBox(
                width: 10,
              ),
              ToggleButtons(
                isSelected: [true, true, true],
                children: <Widget>[
                  const Icon(Icons.exposure_minus_1),
                  Text(
                    '$videosCount',
                    style: const TextStyle(
                        fontWeight: FontWeight.bold, fontSize: 20),
                  ),
                  const Icon(Icons.exposure_plus_1),
                ],
                onPressed: (index) {
                  switch (index) {
                    case 0:
                      videosCount -= 1;
                      if (videosCount <= 0) {
                        videosCount = 0;
                      }
                      setState(() {});
                      break;
                    case 2:
                      videosCount += 1;
                      setState(() {});
                      break;
                  }
                },
              ),
            ],
          ),
        ),
        SizedBox(
          // color: Colors.blue,
          height: 40,
          child: Row(
            children: [
              Builder(builder: (context) {
                return ElevatedButton(
                  onPressed: () {
                    getVideos(context);
                  },
                  child: const Text("每行数量"),
                );
              }),
              /*定义每行数量*/
              const SizedBox(
                width: 10,
              ),
              ToggleButtons(
                isSelected: [true, true, true],
                children: <Widget>[
                  const Icon(Icons.exposure_minus_1),
                  Text(
                    '$crossAxisCount',
                    style: const TextStyle(
                        fontWeight: FontWeight.bold, fontSize: 20),
                  ),
                  const Icon(Icons.exposure_plus_1),
                ],
                onPressed: (index) {
                  switch (index) {
                    case 0:
                      crossAxisCount -= 1;
                      if (crossAxisCount <= 0) {
                        crossAxisCount = 0;
                      }
                      setState(() {});
                      break;
                    case 2:
                      crossAxisCount += 1;
                      setState(() {});
                      break;
                  }
                },
              ),
            ],
          ),
        ),
        _getVideoGridView(),
        ElevatedButton(
          onPressed: () {
            takePhoto();
          },
          child: Text("点击拍照:$takePhotoPath"),
        ),
        _getTakePhotoImage(),
        SizedBox(
          // color: Colors.blue,
          height: 40,
          child: Row(
            children: [
              ElevatedButton(
                onPressed: () {
                  recordVideo();
                },
                child: const Text("点击录制视频"),
              ),
              const SizedBox(
                width: 10,
              ),
              ToggleButtons(
                isSelected: [true, true, true],
                children: <Widget>[
                  const Text('-10'),
                  Text(
                    '$videoDuration',
                    style: const TextStyle(
                        fontWeight: FontWeight.bold, fontSize: 20),
                  ),
                  const Text('+10'),
                ],
                onPressed: (index) {
                  switch (index) {
                    case 0:
                      videoDuration -= 10;
                      if (videoDuration <= 0) {
                        videoDuration = 0;
                      }
                      setState(() {});
                      break;
                    case 2:
                      videoDuration += 10;
                      setState(() {});
                      break;
                  }
                },
              ),
            ],
          ),
        ),
        Text("录制视频路径:$takeVideoPath"),
        const SizedBox(
          height: 30,
        ),
        Text(
          "错误信息:$errorMessage",
          style: const TextStyle(color: Colors.red),
        ),
        SizedBox(
          height: 300,
          child: MediaPickerPage(
              type: MediaPickerPage.imageType,
              maxSelectCount: imagesCount,
              lastSelectMedia: selectImages,
              crossAxisCount: 4),
        )
      ],
    );
  }
}
