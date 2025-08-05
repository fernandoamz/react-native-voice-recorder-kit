import { useState, useRef } from 'react';
import {
  View,
  Button,
  Text,
  StyleSheet,
  Alert,
  PermissionsAndroid,
  Platform,
} from 'react-native';
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

const AudioControls = () => {
  const [recording, setRecording] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [recordingPath, setRecordingPath] = useState<string | null>(null);
  const recordingResultRef = useRef<{ path: string; duration: number } | null>(
    null
  );

  const handleStartRecording = async () => {
    try {
      if (Platform.OS === 'android') {
        const granted = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
          {
            title: 'Microphone Permission',
            message:
              'This app needs access to your microphone to record audio.',
            buttonPositive: 'OK',
            buttonNegative: 'Cancel',
          }
        );

        if (granted !== PermissionsAndroid.RESULTS.GRANTED) {
          Alert.alert(
            'Permission denied',
            'Cannot record without microphone permission.'
          );
          return;
        }
      }

      console.log('🎙️ Starting recording...');
      const path = await startRecording();
      setRecording(true);
      setRecordingPath(path);
      recordingResultRef.current = null;
    } catch (error) {
      console.error('❌ Error starting recording:', error);
      Alert.alert('Recording Error', String(error));
    }
  };

  const handleStopRecording = async () => {
    try {
      console.log('🛑 Stopping recording...');
      const result = await stopRecording();
      setRecording(false);
      setRecordingPath(result.path);
      recordingResultRef.current = result;
      console.log('✅ Recording saved:', result);
    } catch (error) {
      console.error('❌ Error stopping recording:', error);
      Alert.alert('Stop Recording Error', String(error));
    }
  };

  const handleStartRecordingWithMusic = async () => {
    try {
      const musicPath = 'path/to/your/music/file.mp3'; // Replace with real path
      await startRecordingWithMusic(musicPath);
      setRecording(true);
      recordingResultRef.current = null;
    } catch (error) {
      console.error('❌ Error recording with music:', error);
      Alert.alert('Record with Music Error', String(error));
    }
  };

  const handleStartPlayback = async () => {
    try {
      const path = recordingResultRef.current?.path || recordingPath;
      if (!path) {
        Alert.alert('No Recording', 'Please record audio first.');
        return;
      }

      console.log('▶️ Playing file at:', path);
      await startPlayback(path);
      setPlaying(true);
    } catch (error) {
      console.error('❌ Error starting playback:', error);
      Alert.alert('Playback Error', String(error));
    }
  };

  const handleStopPlayback = async () => {
    try {
      await stopPlayback();
      setPlaying(false);
    } catch (error) {
      console.error('❌ Error stopping playback:', error);
      Alert.alert('Stop Playback Error', String(error));
    }
  };

  const handlePause = async () => {
    try {
      await pausePlayingAudio();
    } catch (error) {
      console.error('❌ Error pausing playback:', error);
      Alert.alert('Pause Error', String(error));
    }
  };

  const handleResume = async () => {
    try {
      await resumePlayingAudio();
    } catch (error) {
      console.error('❌ Error resuming playback:', error);
      Alert.alert('Resume Error', String(error));
    }
  };

  const handleSeek = async (positionMs: number) => {
    try {
      await seekToPosition(positionMs);
    } catch (error) {
      console.error('❌ Error seeking:', error);
      Alert.alert('Seek Error', String(error));
    }
  };

  const handleSetLoop = async (shouldLoop: boolean) => {
    try {
      await setLoopPlayback(shouldLoop);
    } catch (error) {
      console.error('❌ Error setting loop:', error);
      Alert.alert('Loop Error', String(error));
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.status}>
        🎙️ Recording: {recording ? 'Yes' : 'No'}
      </Text>
      <Text style={styles.status}>▶️ Playing: {playing ? 'Yes' : 'No'}</Text>
      <Text style={styles.path}>📁 File: {recordingPath || 'None yet'}</Text>

      <Button title="Start Recording" onPress={handleStartRecording} />
      <Button title="Stop Recording" onPress={handleStopRecording} />
      <Button
        title="Record with Music"
        onPress={handleStartRecordingWithMusic}
      />

      <Button title="Play" onPress={handleStartPlayback} />
      <Button title="Stop" onPress={handleStopPlayback} />
      <Button title="Pause" onPress={handlePause} />
      <Button title="Resume" onPress={handleResume} />
      <Button title="Seek to 5s" onPress={() => handleSeek(5)} />

      <Button title="Enable Loop" onPress={() => handleSetLoop(true)} />
      <Button title="Disable Loop" onPress={() => handleSetLoop(false)} />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    padding: 20,
    gap: 12,
  },
  status: {
    fontSize: 16,
    marginBottom: 4,
  },
  path: {
    fontSize: 12,
    fontStyle: 'italic',
    marginBottom: 10,
    color: '#666',
  },
});

export default AudioControls;
