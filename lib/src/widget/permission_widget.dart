import 'package:flutter/material.dart';

typedef AlbumPermissionCallBack = Function();

class PermissionWidget extends StatelessWidget {

  final AlbumPermissionCallBack albumPermissionCallBack;

  const PermissionWidget({Key? key, required this.albumPermissionCallBack}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      color: Colors.white,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          const SizedBox(
            height: 80,
          ),
          const Text(
            '开启相册权限才能在宝宝树孕育中\n分享和保存图片或视频哦～',
            textAlign: TextAlign.center,
            style: TextStyle(
                color: Color(0xFF878787),
                fontSize: 14,
                fontWeight: FontWeight.w500),
          ),
          GestureDetector(
            onTap: () {
              albumPermissionCallBack();
            },
            child: Container(
              width: 140,
              height: 40,
              margin: const EdgeInsets.only(top: 24),
              alignment: Alignment.center,
              decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(20.0), //圆角
                  gradient: const LinearGradient(colors: <Color>[
                    //背景渐变
                    Color(0xFFFF8558),
                    Color(0xFFFF4298),
                  ])),
              child: const Text('开启权限',
                  style: TextStyle(
                      color: Colors.white,
                      fontSize: 16,
                      fontWeight: FontWeight.w500)),
            ),
          )
        ],
      ),
    );
  }
}
