# VoiceRecorderKit 🎙️

A native React Native module for recording and playing audio on **iOS** and **Android**, with support for music-backed recordings and loop playback.

---

## ✨ Features

- Start/stop voice recording  
- Record over background music  
- Playback with seek, pause/resume  
- Looping playback toggle  
- Cross-platform support (iOS & Android)

---

## 📦 Installation

> Requires React Native 0.65+

### 1. Install the package

```bash
npm install react-native-voice-recorder-kit
```

or

```bash
yarn add react-native-voice-recorder-kit
```

### 2. iOS Setup

Install CocoaPods dependencies:

```bash
cd ios && pod install && cd ..
```

Then add the following permissions to your `ios/YourApp/Info.plist`:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>We need access to your microphone for audio recording</string>
<key>NSAppleMusicUsageDescription</key>
<string>We need access to your music library</string>
```

### 3. Android Setup

Add the following permissions to your `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
```

For Android 11+ (API 30+), inside your `<application>` tag:

```xml
<application
  android:requestLegacyExternalStorage="true"
  ... >
```

---

## 📲 Usage

```ts
import {
  startRecording,
  stopRecording,
  startPlayback,
  stopPlayback,
  pausePlayingAudio,
  resumePlayingAudio,
  seekToPosition,
  startRecordingWithMusic,
  setLoopPlayback,
} from 'react-native-voice-recorder-kit';
```

### ✅ Example

```ts
// Start recording
await startRecording();

// Stop and get file path
const filePath = await stopRecording();

// Playback
await startPlayback(filePath);

// Optional controls
await pausePlayingAudio();
await resumePlayingAudio();
await seekToPosition(1500); // 1500 ms
await stopPlayback();

// Enable looping
await setLoopPlayback(true);
```

---

## 📚 API Reference

| Method                               | Description                                         |
|-------------------------------------|-----------------------------------------------------|
| `startRecording()`                  | Starts voice recording                              |
| `stopRecording()`                   | Stops recording and returns file path               |
| `startRecordingWithMusic(path)`    | Records voice while playing music from given path   |
| `startPlayback(path)`              | Plays audio at the given file path                  |
| `pausePlayingAudio()`              | Pauses playback                                     |
| `resumePlayingAudio()`             | Resumes playback                                    |
| `seekToPosition(ms)`               | Seeks to a specific position in milliseconds        |
| `stopPlayback()`                   | Stops playback                                      |
| `setLoopPlayback(true/false)`      | Enables or disables loop playback                   |

---

## 🚧 Troubleshooting

- Ensure microphone and file storage permissions are granted.
- Use physical devices to test recording; simulators may not support audio input.
- If `startPlayback` fails, check the file path and format.
- Logs can help trace path issues or audio playback errors.

---

## 📂 Contributing

Contributions are welcome!  
If you'd like to improve the module or report bugs, please open an issue or PR.

---

## 🪪 Coming Soon!

- Add chunk function on android to read the real time audio wave
- Add mix two audio files in android
- Example about draw the wave and show in the app as svg
