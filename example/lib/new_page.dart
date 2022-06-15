import 'package:flutter/material.dart';
import 'package:flutter_plugin_media_picker/media_picker.dart';

class Demo extends StatefulWidget {
  const Demo({Key? key}) : super(key: key);

  @override
  _DemoState createState() => _DemoState();
}

class _DemoState extends State<Demo> {
  ListValueNotifier<MediumInfo> selectImages = ListValueNotifier([]);
  ValueNotifier<bool> showBigValueNotifier = ValueNotifier(false);
  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.grey,
      child: Stack(
        children: [
          _getImageGridView(),
          MediaPickerPage(
            maxSelectCount: 9,
            lastSelectMedia: selectImages,
            showBigViewNotifier: showBigValueNotifier,
            actionCallBack: ( action) {},
          ),
          ElevatedButton(onPressed: () {
            showBigValueNotifier.value = !showBigValueNotifier.value;
          }, child: const Text('切换大小')),
        ],
      ),
    );
  }

  _getImageGridView() {
    return SizedBox(
      height: 200,
      child: ValueListenableBuilder(
        valueListenable: selectImages,
        builder: (context, value, child) {
          List<MediumInfo> values = value as List<MediumInfo>;
          return GridView.builder(
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                childAspectRatio: 1,
                crossAxisSpacing: 5.0,
                mainAxisSpacing: 5.0,
              ),
              itemCount: values.length,
              scrollDirection: Axis.horizontal,
              itemBuilder: (context, index) {
                return _getImageItem(values[index]);
              });
        },
      ),
    );
  }

  Widget _getImageItem(MediumInfo info) {
    return GestureDetector(
        onTap: () {
          var value = selectImages.value.toList();
          for (var element in value) {
            if (element.id == info.id) {
              selectImages.remove(element);
            }
          }
        },
        child: Container(
          color: Colors.blue,
          width: 100,
          height: 100,
          child: info.bytes != null ? Image.memory(
            info.bytes!,
            fit: BoxFit.cover,
            width: 100,
            height: 100,
          ) : Container(),
        ));
  }
}
