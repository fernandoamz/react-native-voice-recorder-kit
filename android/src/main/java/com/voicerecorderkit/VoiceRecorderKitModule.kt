package com.voicerecorderkit

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ReactModule(name = VoiceRecorderKitModule.NAME)
class VoiceRecorderKitModule(reactContext: ReactApplicationContext) :
  NativeVoiceRecorderKitSpec(reactContext) {

  private enum class RecState { IDLE, RECORDING }

  private enum class PlayState {
    IDLE,
    PREPARING,
    STARTED,
    PAUSED,
    COMPLETED,
    STOPPED,
  }

  companion object {
    const val NAME = "VoiceRecorderKit"
    private const val TAG = "VoiceRecorderKit"
  }

  private val audioThread = HandlerThread("VoiceRecorderKitAudio").apply { start() }
  private val audioHandler = Handler(audioThread.looper)

  private var recorder: MediaRecorder? = null
  private var player: MediaPlayer? = null
  private var recordingFilePath: String? = null
  private var loopPlayback: Boolean = true
  private var recState = RecState.IDLE
  private var playState = PlayState.IDLE
  private var startedAtElapsed = 0L
  private var playbackPromise: Promise? = null
  private var focusHandle: Any? = null

  private val legacyFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
    audioHandler.post { onFocusChange(focusChange) }
  }

  override fun getName() = NAME

  override fun invalidate() {
    if (Looper.myLooper() == audioThread.looper) {
      releaseRecorder()
      releasePlayer()
      abandonFocus()
    } else if (audioThread.isAlive) {
      val latch = CountDownLatch(1)
      audioHandler.post {
        try {
          releaseRecorder()
          releasePlayer()
          abandonFocus()
        } finally {
          latch.countDown()
        }
      }
      try {
        latch.await(2, TimeUnit.SECONDS)
      } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
      }
    }
    audioThread.quitSafely()
    super.invalidate()
  }

  override fun startRecording(promise: Promise) {
    val hasPermission = reactApplicationContext.checkSelfPermission(
      android.Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasPermission) {
      promise.reject("PERMISSION_DENIED", "RECORD_AUDIO permission not granted.")
      return
    }

    audioHandler.post {
      if (recState == RecState.RECORDING) {
        promise.reject("ERR_ALREADY_RECORDING", "Stop the current recording first.")
        return@post
      }

      var next: MediaRecorder? = null
      try {
        val outputFile = File(reactApplicationContext.cacheDir, "${UUID.randomUUID()}.m4a")
        next = createRecorder()
        next.setOnErrorListener { failed, what, extra ->
          Log.e(TAG, "Recorder error what=$what extra=$extra")
          audioHandler.post {
            if (recorder === failed) {
              releaseRecorder()
            }
          }
        }
        next.apply {
          setAudioSource(MediaRecorder.AudioSource.MIC)
          setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
          setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
          setAudioChannels(1)
          setAudioEncodingBitRate(128000)
          setAudioSamplingRate(44100)
          setOutputFile(outputFile.absolutePath)
          prepare()
          start()
        }
        recorder = next
        recordingFilePath = outputFile.absolutePath
        startedAtElapsed = SystemClock.elapsedRealtime()
        recState = RecState.RECORDING
        promise.resolve(outputFile.absolutePath)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start recording", e)
        next?.let { failed ->
          if (recorder === failed) {
            recorder = null
          }
          releaseMediaRecorder(failed)
        }
        recState = RecState.IDLE
        promise.reject("ERR_RECORDING", "Failed to start recording: ${e.message}", e)
      }
    }
  }

  override fun startRecordingWithMusic(
    @Suppress("UNUSED_PARAMETER") musicPath: String,
    promise: Promise,
  ) {
    promise.reject(
      "ERR_NOT_IMPLEMENTED",
      "Recording over background music is not implemented on Android.",
    )
  }

  override fun stopRecording(promise: Promise) {
    audioHandler.post {
      val active = recorder
      if (active == null || recState != RecState.RECORDING) {
        promise.reject("ERR_NOT_RECORDING", "Recording was not in progress.")
        return@post
      }

      val durationSec = (SystemClock.elapsedRealtime() - startedAtElapsed) / 1000.0
      val path = recordingFilePath
      var stopped = false
      try {
        active.stop()
        stopped = true
        promise.resolve(Arguments.createMap().apply {
          putString("path", path)
          putDouble("duration", durationSec)
        })
      } catch (e: RuntimeException) {
        Log.e(TAG, "Failed to stop recording", e)
        promise.reject("ERR_STOP", "Failed to stop recording: ${e.message}", e)
      } finally {
        if (stopped) {
          releaseStoppedRecorder()
        } else {
          releaseRecorder()
        }
      }
    }
  }

  override fun startPlayback(path: String, promise: Promise) {
    audioHandler.post {
      val file = File(path)
      if (!file.exists()) {
        promise.reject("ERR_PLAYBACK", "File does not exist at path: $path")
        return@post
      }
      if (file.length() == 0L) {
        promise.reject("ERR_PLAYBACK", "Recording file is empty.")
        return@post
      }

      releasePlayer()
      if (!requestFocus()) {
        promise.reject("ERR_PLAYBACK", "Failed to gain audio focus")
        return@post
      }

      var next: MediaPlayer? = null
      try {
        next = MediaPlayer().apply {
          setAudioAttributes(mediaAttributes())
          FileInputStream(file).use { input ->
            setDataSource(input.fd)
          }
          isLooping = loopPlayback
          setOnPreparedListener { mp -> audioHandler.post { onPrepared(mp) } }
          setOnCompletionListener { mp -> audioHandler.post { onCompleted(mp) } }
          setOnErrorListener { mp, what, extra ->
            audioHandler.post { onPlayerError(mp, what, extra) }
            true
          }
        }
        player = next
        playState = PlayState.PREPARING
        playbackPromise = promise
        next.prepareAsync()
      } catch (e: Exception) {
        Log.e(TAG, "Playback error", e)
        playbackPromise = null
        if (player === next) {
          player = null
        }
        playState = PlayState.IDLE
        next?.let { releaseMediaPlayer(it) }
        abandonFocus()
        promise.reject("ERR_PLAYBACK", "Failed to play audio: ${e.message}", e)
      }
    }
  }

  override fun stopPlayback(promise: Promise) {
    audioHandler.post {
      try {
        val active = player
        if (active != null && canStop()) {
          active.stop()
          playState = PlayState.STOPPED
        }
        releasePlayer()
        abandonFocus()
        promise.resolve(null)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to stop playback", e)
        releasePlayer()
        abandonFocus()
        promise.reject("ERR_STOP_PLAYBACK", "Failed to stop playback: ${e.message}", e)
      }
    }
  }

  override fun pausePlayingAudio(promise: Promise) {
    audioHandler.post {
      val active = player
      if (active == null) {
        promise.reject("ERR_PAUSE", "Audio player is not initialized")
        return@post
      }
      try {
        if (active.isPlaying) {
          active.pause()
          playState = PlayState.PAUSED
          promise.resolve("paused")
        } else {
          promise.resolve("alreadyPaused")
        }
      } catch (e: IllegalStateException) {
        Log.e(TAG, "Pause failed", e)
        promise.reject("ERR_PAUSE", e.message, e)
      }
    }
  }

  override fun resumePlayingAudio(promise: Promise) {
    audioHandler.post {
      val active = player
      if (active == null) {
        promise.reject("ERR_RESUME", "Audio player is not initialized")
        return@post
      }
      try {
        if (active.isPlaying) {
          promise.resolve("alreadyPlaying")
          return@post
        }
        if (!requestFocus()) {
          promise.reject("ERR_RESUME", "Failed to gain audio focus")
          return@post
        }
        active.setVolume(1f, 1f)
        active.start()
        playState = PlayState.STARTED
        promise.resolve("resumed")
      } catch (e: IllegalStateException) {
        Log.e(TAG, "Resume failed", e)
        promise.reject("ERR_RESUME", e.message, e)
      }
    }
  }

  override fun seekToPosition(position: Double, promise: Promise) {
    audioHandler.post {
      val active = player
      if (active == null) {
        promise.reject("ERR_SEEK", "Audio player is not initialized")
        return@post
      }
      try {
        val targetMs = (position * 1000).toInt()
        val durationMs = active.duration
        if (targetMs < 0 || targetMs > durationMs) {
          promise.reject("ERR_SEEK", "Seek time out of bounds")
          return@post
        }
        active.seekTo(targetMs)
        promise.resolve(null)
      } catch (e: IllegalStateException) {
        Log.e(TAG, "Seek failed", e)
        promise.reject("ERR_SEEK", "Seek failed: ${e.message}", e)
      }
    }
  }

  override fun setLoopPlayback(shouldLoop: Boolean, promise: Promise) {
    audioHandler.post {
      loopPlayback = shouldLoop
      try {
        player?.isLooping = shouldLoop
      } catch (e: IllegalStateException) {
        Log.e(TAG, "Failed to update loop flag", e)
      }
      promise.resolve("loop set")
    }
  }

  private fun createRecorder(): MediaRecorder {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      MediaRecorder(reactApplicationContext)
    } else {
      @Suppress("DEPRECATION")
      MediaRecorder()
    }
  }

  private fun releaseStoppedRecorder() {
    recState = RecState.IDLE
    val active = recorder ?: return
    recorder = null
    active.release()
  }

  private fun releaseRecorder() {
    recState = RecState.IDLE
    val active = recorder ?: return
    recorder = null
    releaseMediaRecorder(active)
  }

  private fun releaseMediaRecorder(active: MediaRecorder) {
    try {
      active.reset()
    } catch (_: Exception) {
    }
    active.release()
  }

  private fun releasePlayer() {
    val pending = playbackPromise
    playbackPromise = null
    val active = player
    player = null
    playState = PlayState.IDLE
    if (active != null) {
      releaseMediaPlayer(active)
    }
    pending?.reject("ERR_PLAYBACK", "Playback was released before it started.")
  }

  private fun releaseMediaPlayer(active: MediaPlayer) {
    try {
      active.reset()
    } catch (_: Exception) {
    }
    active.release()
  }

  private fun canStop(): Boolean {
    return when (playState) {
      PlayState.STARTED,
      PlayState.PAUSED,
      PlayState.COMPLETED,
      PlayState.STOPPED -> true
      else -> false
    }
  }

  private fun onPrepared(mp: MediaPlayer) {
    if (player !== mp) {
      return
    }
    try {
      mp.setVolume(1f, 1f)
      mp.start()
      playState = PlayState.STARTED
      val pending = playbackPromise
      playbackPromise = null
      pending?.resolve("Playback started")
    } catch (e: Exception) {
      Log.e(TAG, "Playback start failed", e)
      val pending = playbackPromise
      playbackPromise = null
      releasePlayer()
      abandonFocus()
      pending?.reject("ERR_PLAYBACK", "Failed to play audio: ${e.message}", e)
    }
  }

  private fun onCompleted(mp: MediaPlayer) {
    if (player !== mp) {
      return
    }
    playState = PlayState.COMPLETED
    if (!mp.isLooping) {
      abandonFocus()
    }
  }

  private fun onPlayerError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
    if (player !== mp) {
      return true
    }
    Log.e(TAG, "Playback error what=$what extra=$extra")
    val pending = playbackPromise
    playbackPromise = null
    player = null
    playState = PlayState.IDLE
    releaseMediaPlayer(mp)
    abandonFocus()
    pending?.reject("ERR_PLAYBACK", "Failed to play audio (what=$what extra=$extra)")
    return true
  }

  private fun mediaAttributes(): AudioAttributes {
    return AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
      .build()
  }

  private fun audioManager(): AudioManager {
    return reactApplicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  }

  private fun requestFocus(): Boolean {
    abandonFocus()
    val manager = audioManager()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      requestFocusV26(manager)
    } else {
      @Suppress("DEPRECATION")
      manager.requestAudioFocus(
        legacyFocusListener,
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN,
      ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
  }

  @SuppressLint("NewApi")
  private fun requestFocusV26(manager: AudioManager): Boolean {
    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
      .setAudioAttributes(mediaAttributes())
      .setOnAudioFocusChangeListener({ focusChange -> onFocusChange(focusChange) }, audioHandler)
      .build()
    focusHandle = request
    return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
  }

  private fun abandonFocus() {
    val manager = audioManager()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      abandonFocusV26(manager)
    } else {
      @Suppress("DEPRECATION")
      manager.abandonAudioFocus(legacyFocusListener)
    }
  }

  @SuppressLint("NewApi")
  private fun abandonFocusV26(manager: AudioManager) {
    val request = focusHandle as? AudioFocusRequest ?: return
    manager.abandonAudioFocusRequest(request)
    focusHandle = null
  }

  private fun onFocusChange(focusChange: Int) {
    if (
      focusChange != AudioManager.AUDIOFOCUS_LOSS &&
      focusChange != AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
    ) {
      return
    }
    val active = player ?: return
    if (playState != PlayState.STARTED) {
      return
    }
    try {
      active.pause()
      playState = PlayState.PAUSED
    } catch (_: IllegalStateException) {
    }
  }
}
