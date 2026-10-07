
# VoiceRecorderKit 🎙️

A native React Native module for recording and playing audio on **iOS** and **Android**, with loop playback. Music-backed recording is implemented on iOS.

---

## ✨ Features

- Start/stop voice recording
- Record over background music on iOS (Android rejects this call)
- Playback with seek, pause/resume
- Looping playback, on by default
- iOS and Android

---

## 📦 Installation

> Requires React Native >= 0.76

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

Add the microphone permission to `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
```

Recordings are written to the app cache directory. Request `RECORD_AUDIO` at runtime before `startRecording()`:

```ts
import { PermissionsAndroid, Platform } from 'react-native';

if (Platform.OS === 'android') {
  const granted = await PermissionsAndroid.request(
    PermissionsAndroid.PERMISSIONS.RECORD_AUDIO
  );
  if (granted !== PermissionsAndroid.RESULTS.GRANTED) {
    throw new Error('RECORD_AUDIO permission not granted');
  }
}
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
await startRecording();

// duration is seconds
const { path, duration } = await stopRecording();

// Looping is on by default. Turn it off before playback when looping is unwanted.
await setLoopPlayback(false);
await startPlayback(path);

await pausePlayingAudio();
await resumePlayingAudio();
await seekToPosition(1.5); // seconds
await stopPlayback();

// iOS mixes the microphone with the music file.
// Android rejects with ERR_NOT_IMPLEMENTED.
const musicPath = '/path/to/music.m4a';
await startRecordingWithMusic(musicPath);
```

---

## 📚 API Reference

| Method | Returns | Notes |
|---|---|---|
| `startRecording()` | `Promise<string>` | Cache `.m4a` path. Requires a granted microphone permission. Rejects `ERR_ALREADY_RECORDING` if a take is already active. |
| `stopRecording()` | `Promise<{ path: string; duration: number }>` | `duration` is seconds. Rejects `ERR_NOT_RECORDING` if nothing is recording. |
| `startRecordingWithMusic(path)` | `Promise<string>` | iOS records over the music file. Android rejects `ERR_NOT_IMPLEMENTED`. |
| `startPlayback(path)` | `Promise<string>` | Resolves `"Playback started"`. Loops unless `setLoopPlayback(false)` ran first. |
| `pausePlayingAudio()` | `Promise<string>` | `"paused"` or `"alreadyPaused"`. Rejects `ERR_PAUSE` when no player exists. |
| `resumePlayingAudio()` | `Promise<string>` | `"resumed"` or `"alreadyPlaying"`. Rejects `ERR_RESUME` when no player exists. |
| `seekToPosition(seconds)` | `Promise<void>` | Seconds, not milliseconds. `1.5` seeks to 1.5 seconds. Rejects `ERR_SEEK` when the player is missing or the time is outside the file. |
| `stopPlayback()` | `Promise<void>` | |
| `setLoopPlayback(shouldLoop)` | `Promise<string>` | Resolves `"loop set"`. Default is `true`. |

---

## 🚧 Troubleshooting

- Request the microphone permission at runtime before `startRecording()`.
- Use physical devices to test recording; simulators may not support audio input.
- If `startPlayback` fails, check the file path and format.
- Logs can help trace path issues or audio playback errors.

---

## 📂 Contributing

Contributions are welcome!  
If you'd like to improve the module or report bugs, please open an issue or PR.

---

## 🚀 Coming Soon!

- Add chunk function on android to read the real time audio wave  
- Add mix two audio files in android  
- Example about draw the wave and show in the app as svg

---
