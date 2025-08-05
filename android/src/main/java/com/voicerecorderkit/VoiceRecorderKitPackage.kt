package com.voicerecorderkit

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class VoiceRecorderKitPackage : BaseReactPackage() {
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
    return if (name == VoiceRecorderKitModule.NAME) {
      VoiceRecorderKitModule(reactContext)
    } else null
  }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
    return ReactModuleInfoProvider {
      mapOf(
        VoiceRecorderKitModule.NAME to ReactModuleInfo(
          VoiceRecorderKitModule.NAME,
          VoiceRecorderKitModule.NAME,
          false, 
          false,
          false,
          true 
        )
      )
    }
  }
}
