#import "FlutterPluginMediaPickerPlugin.h"
#if __has_include(<flutter_plugin_media_picker/flutter_plugin_media_picker-Swift.h>)
#import <flutter_plugin_media_picker/flutter_plugin_media_picker-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "flutter_plugin_media_picker-Swift.h"
#endif

@implementation FlutterPluginMediaPickerPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFlutterPluginMediaPickerPlugin registerWithRegistrar:registrar];
}
@end
