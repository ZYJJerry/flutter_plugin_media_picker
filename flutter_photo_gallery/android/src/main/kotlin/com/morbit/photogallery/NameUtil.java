package com.morbit.photogallery;


public class NameUtil {
    public static String changeName(String englishName){
        if(englishName!=null){
            if(englishName.equals("all")||englishName.equals("All")||englishName.equals("ALL")){
                return "全部";
            }
            if(englishName.equals("Camera")||englishName.equals("camera")||englishName.equals("CAMERA")){
                return "相机";
            }
            if(englishName.equals("WeiXin")||englishName.equals("weixin")||englishName.equals("WEIXIN")){
                return "微信";
            }
            if(englishName.equals("Screenshots")||englishName.equals("screenshots")||englishName.equals("SCREENSHOTS")){
                return "截屏";
            }
            if(englishName.equals("downloads")||englishName.equals("DOWNLOADS")||englishName.equals("Downloads")){
                return "下载";
            }
            if(englishName.equals("Pictures")||englishName.equals("pictures")||englishName.equals("PICTURES")){
                return "图片";
            }
        }
        return  englishName;
    }
}
