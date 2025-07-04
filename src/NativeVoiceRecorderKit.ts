import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  // Recording
  startRecording(): Promise<string>;
  stopRecording(): Promise<{ path: string; duration: number }>;
  startRecordingWithMusic(musicPath: string): Promise<string>;

  // Playback
  startPlayback(path: string): Promise<string>;
  stopPlayback(): Promise<void>;
  pausePlayingAudio(): Promise<string>;
  resumePlayingAudio(): Promise<string>;
  setLoopPlayback(shouldLoop: boolean): Promise<string>;
  seekToPosition(position: number): Promise<void>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('VoiceRecorderKit');
