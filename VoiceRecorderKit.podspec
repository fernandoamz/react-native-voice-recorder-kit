require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "VoiceRecorderKit"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/fernandoamz/react-native-voice-recorder-kit.git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,cpp,swift}" # 👈 Asegura incluir Swift
  s.private_header_files = "ios/**/*.h"

  s.requires_arc = true
  s.swift_version = '5.0'
  s.static_framework = true

  install_modules_dependencies(s)
end
