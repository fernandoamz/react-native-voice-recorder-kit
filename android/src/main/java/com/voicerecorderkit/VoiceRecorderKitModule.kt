package com.voicerecorderkit

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.module.annotations.ReactModule
import java.io.File
import java.util.UUID

@ReactModule(name = VoiceRecorderKitModule.NAME)
class VoiceRecorderKitModule(reactContext: ReactApplicationContext) :
  NativeVoiceRecorderKitSpec(reactContext) {

  companion object {
    const val NAME = "VoiceRecorderKit"
    private const val TAG = "VoiceRecorderKit"
  }

  private var recorder: MediaRecorder? = null
  private var player: MediaPlayer? = null
  private var recordingFilePath: String? = null
  private var loopPlayback: Boolean = true

  override fun getName() = NAME

  // Start recording audio
  override fun startRecording(promise: Promise) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val hasPermission = reactApplicationContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
      if (!hasPermission) {
        promise.reject("PERMISSION_DENIED", "RECORD_AUDIO permission not granted.")
        return
      }
    }

    try {
      val fileName = "${UUID.randomUUID()}.m4a"
      val outputFile = File(reactApplicationContext.cacheDir, fileName)
      recordingFilePath = outputFile.absolutePath

      recorder = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(128000)
        setAudioSamplingRate(44100)
        setOutputFile(recordingFilePath)
        prepare()
        start()
      }

      promise.resolve(recordingFilePath)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start recording", e)
      promise.reject("ERR_RECORDING", "Failed to start recording: ${e.message}", e)
    }
  }

  // Start recording with music (simplified, just records mic)
  override fun startRecordingWithMusic(musicPath: String, promise: Promise) {
    startRecording(promise)
  }

  // Stop recording audio
  override fun stopRecording(promise: Promise) {
    try {
      recorder?.apply {
        stop()
        release()
      }
      recorder = null

      // TODO: calculate duration if needed
      promise.resolve(Arguments.createMap().apply {
        putString("path", recordingFilePath)
        putDouble("duration", 0.0)
      })
    } catch (e: Exception) {
      Log.e(TAG, "Failed to stop recording", e)
      promise.reject("ERR_STOP", "Failed to stop recording: ${e.message}", e)
    }
  }

  // Start audio playback
  override fun startPlayback(path: String, promise: Promise) {
    try {
      val file = File(path)
      if (!file.exists()) {
        promise.reject("ERR_PLAYBACK", "File does not exist at path: $path")
        return
      }

      // Request audio focus
      val audioManager = reactApplicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val result = audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
      if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        promise.reject("ERR_PLAYBACK", "Failed to gain audio focus")
        return
      }

      // Release existing player if any
      player?.release()
      player = null

      player = MediaPlayer().apply {
        setDataSource(path)
        isLooping = loopPlayback
        prepare()
        start()
      }

      Log.d(TAG, "Playback started for $path")
      promise.resolve("Playback started")
    } catch (e: Exception) {
      Log.e(TAG, "Playback error", e)
      promise.reject("ERR_PLAYBACK", "Failed to play audio: ${e.message}", e)
    }
  }

  // Stop audio playback
  override fun stopPlayback(promise: Promise) {
    try {
      player?.stop()
      player?.release()
      player = null
      promise.resolve(null)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to stop playback", e)
      promise.reject("ERR_STOP_PLAYBACK", "Failed to stop playback: ${e.message}", e)
    }
  }

  // Pause playback
  override fun pausePlayingAudio(promise: Promise) {
    if (player?.isPlaying == true) {
      player?.pause()
      promise.resolve("paused")
    } else {
      promise.resolve("alreadyPaused")
    }
  }

  // Resume playback
  override fun resumePlayingAudio(promise: Promise) {
    if (player != null && !player!!.isPlaying) {
      player?.start()
      promise.resolve("resumed")
    } else {
      promise.resolve("alreadyPlaying")
    }
  }

  // Seek to position (in seconds)
  override fun seekToPosition(position: Double, promise: Promise) {
    try {
      val target = (position * 1000).toInt()
      player?.seekTo(target)
      promise.resolve(null)
    } catch (e: Exception) {
      Log.e(TAG, "Seek failed", e)
      promise.reject("ERR_SEEK", "Seek failed: ${e.message}", e)
    }
  }

  // Enable or disable loop playback
  override fun setLoopPlayback(shouldLoop: Boolean, promise: Promise) {
    loopPlayback = shouldLoop
    player?.isLooping = shouldLoop
    promise.resolve("loop set")
  }
}
