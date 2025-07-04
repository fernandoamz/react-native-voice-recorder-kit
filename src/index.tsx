import VoiceRecorderKit from './NativeVoiceRecorderKit';

// Recording
export const startRecording = () => VoiceRecorderKit.startRecording();
export const stopRecording = () => VoiceRecorderKit.stopRecording();
export const startRecordingWithMusic = (musicPath: string) =>
  VoiceRecorderKit.startRecordingWithMusic(musicPath);

// Playback
export const startPlayback = (path: string) => VoiceRecorderKit.startPlayback(path);
export const stopPlayback = () => VoiceRecorderKit.stopPlayback();
export const pausePlayingAudio = () => VoiceRecorderKit.pausePlayingAudio();
export const resumePlayingAudio = () => VoiceRecorderKit.resumePlayingAudio();
export const setLoopPlayback = (shouldLoop: boolean) =>
  VoiceRecorderKit.setLoopPlayback(shouldLoop);
export const seekToPosition = (position: number) =>
  VoiceRecorderKit.seekToPosition(position);
