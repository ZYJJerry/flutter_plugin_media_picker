import Flutter
import UIKit
import Photos
import MobileCoreServices

var listMediumPath = "listMediumPath"

var normalAlbums: Set = ["Recents" , "All Photos", "Camera Roll"]

var filterAlbums: Set = ["Hidden", "Animated", "Live Photos", "Recently Deleted", "Long Exposure", "Panoramas", "Slo-mo"]


public class SwiftFlutterPluginMediaPickerPlugin: NSObject, FlutterPlugin, UIImagePickerControllerDelegate & UINavigationControllerDelegate {

    private var pickerModel: PickerModel!
    var regis: FlutterPluginRegistrar!
    private var pickerResult: FlutterResult?
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "flutter_plugin_media_picker", binaryMessenger: registrar.messenger())
        let instance = SwiftFlutterPluginMediaPickerPlugin()
        instance.regis = registrar
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        pickerResult = result

        if call.method == listMediumPath {
            if let arguements = call.arguments as? [String : Any] {
                if arguements["mediumType"] as! String == "image" {
                    getMedia(type: .image, result: result)
                }
                if arguements["mediumType"] as! String == "video" {
                    pickerModel = PickerModel(dic: call.arguments as! [String : Any])
                    guard let picker = pickerModel else { return }
                    getMedia(type: .video, maxDuration: picker.maxDuration, result: result)
                }
            }
        }
    }

    private func getAssetInfo(asset: PHAsset) -> Dictionary<String, Any> {
        var info = Dictionary<String, Any>()
        info["id"] = asset.localIdentifier
        info["mediumType"] = asset.mediaType == .image ? "image" : "video"
        info["width"] = asset.pixelWidth
        info["height"] = asset.pixelHeight
        info["path"] = "/var/mobile/Media/\(asset.value(forKey: "directory") as! String)/\(asset.value(forKey: "filename") as! String)"
        if asset.mediaType == .video {
            info["duration"] = Int(asset.duration * 1000)
        }
        return info
    }

    private func getMedia(type: PHAssetMediaType, maxDuration: Int = 0, result: @escaping FlutterResult) {
        if type == .image {
            getAllPHAsset(type: .image, successBlock: { [weak self] (assets) in
                self?.getResult(assets: assets, result: result)
            })
        }

        if type == .video {
            getAllPHAsset(type: .video, maxDuration: pickerModel.maxDuration, successBlock: { [weak self] (assets) in
                self?.getResult(assets: assets, result: result)
            })
        }


    }

    private func getResult(assets: [PHAsset], result: @escaping FlutterResult) {
        var items = [[String: Any?]]()
        for item in assets {
            items.append(getAssetInfo(asset: item))
        }
        result(items)
    }

}

extension SwiftFlutterPluginMediaPickerPlugin {

    private func getAllPHAsset(type: PHAssetMediaType, maxDuration: Int = 0, successBlock: @escaping (([PHAsset]) -> ())) {
        let options = PHFetchOptions()
        let smartAlbums:PHFetchResult = PHAssetCollection.fetchAssetCollections(with: PHAssetCollectionType.smartAlbum, subtype: PHAssetCollectionSubtype.albumRegular, options: options)
        var isExistNormalAlbum = false
        for i in 0..<smartAlbums.count {
            let collection:PHCollection  = smartAlbums[i];//得到一个相册,一个集合就是一个相册
            if collection.isKind(of: PHAssetCollection.self) {
                if normalAlbums.contains(collection.localizedTitle ?? "") {
                    isExistNormalAlbum = true
                    let assetCollection = collection as! PHAssetCollection
                    let fetchResult:PHFetchResult = PHAsset.fetchAssets(in: assetCollection, options: nil)
                    if fetchResult.count > 0 {
                        getAllPHAssetFromOneAlbum(type: type, maxDuration: maxDuration, assetCollection: assetCollection) { (assets) in
                            successBlock(assets)
                            return
                        }
                    }
                }
            }
        }
        if !isExistNormalAlbum {
            let group = DispatchGroup()
            var allAsset:[PHAsset] = []
            for i in 0..<smartAlbums.count {
                let collection:PHCollection  = smartAlbums[i];
                if collection.isKind(of: PHAssetCollection.self) {
                    if !filterAlbums.contains(collection.localizedTitle ?? "") {
                        group.enter()
                        setAlbums(collection: collection, type: type, maxDuration: maxDuration) { (assets) in
                            allAsset += assets
                            group.leave()
                        }
                    }
                }
            }
            group.notify(queue: DispatchQueue.main) {
                successBlock(allAsset)
            }
        }

    }

    func setAlbums(collection: PHCollection, type: PHAssetMediaType, maxDuration: Int = 0, successBlock: @escaping (([PHAsset]) -> ())) {
        let assetCollection = collection as! PHAssetCollection
        let fetchResult:PHFetchResult = PHAsset.fetchAssets(in: assetCollection, options: nil)
        if fetchResult.count > 0 {
            getAllPHAssetFromOneAlbum(type: type, maxDuration: maxDuration, assetCollection: assetCollection) { (assets) in
                successBlock(assets)
            }
        } else {
            successBlock([])
        }
    }

    private func getAllPHAssetFromOneAlbum(type: PHAssetMediaType, maxDuration: Int = 0, assetCollection:PHAssetCollection, successBlock: @escaping (([PHAsset]) -> ())) {
        var assets:[PHAsset] = []
        let options = PHFetchOptions.init()
        options.sortDescriptors = [NSSortDescriptor(key: "creationDate",
                                                    ascending: false)]
        options.predicate = NSPredicate.init(format: "mediaType == %ld", type.rawValue)
        let results:PHFetchResult = PHAsset.fetchAssets(in: assetCollection, options: options)
        if type == .image {
            results.enumerateObjects { (asset, index, stop) in
                if type == .image {
                    assets.append(asset)
                }
            }
            successBlock(assets)
        }
        if type == .video {
            let group = DispatchGroup()
            results.enumerateObjects { (asset, index, stop) in
                group.enter()
                PHImageManager.default().requestAVAsset(forVideo: asset, options: nil) { (avAsset, avAudioMix, info) in
                    if let tempAsset = avAsset as? AVURLAsset {
                        //限制时长
                        let duration = CMTimeGetSeconds(tempAsset.duration)
                        if duration <= TimeInterval(maxDuration) {
                            assets.append(asset)
                        }
                        group.leave()
                    }
                }
            }
            group.notify(queue: DispatchQueue.main) {
                successBlock(assets)
            }
        }
    }
}


@objcMembers class PickerModel: NSObject {

    var maxDuration = 0

    init(dic:[String: Any]) {
        super.init()
        setValuesForKeys(dic)
    }

    override func setValue(_ value: Any?, forUndefinedKey key: String) {}

    override func setValue(_ value: Any?, forKey key: String) {
        super.setValue(value, forKey: key)
    }

}

