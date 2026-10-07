import { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Alert,
  PermissionsAndroid,
  Platform,
  Pressable,
  ScrollView,
  StatusBar,
  type PressableStateCallbackType,
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

type Tone = 'record' | 'play' | 'neutral';

type ControlButtonProps = {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  tone?: Tone;
};

const SEEK_SECONDS = 5;
const MUSIC_PATH = 'path/to/your/music/file.mp3';

function isActivePress(state: PressableStateCallbackType): boolean {
  const hovered = Boolean(
    (state as PressableStateCallbackType & { hovered?: boolean }).hovered
  );
  return state.pressed || hovered;
}

function messageFrom(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function ControlButton({
  label,
  onPress,
  disabled = false,
  tone = 'neutral',
}: ControlButtonProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={(state) => [
        styles.button,
        tone === 'record' ? styles.buttonRecord : null,
        tone === 'play' ? styles.buttonPlay : null,
        !disabled && isActivePress(state) ? styles.buttonPressed : null,
        disabled ? styles.buttonDisabled : null,
      ]}
    >
      {(state: PressableStateCallbackType) => (
        <Text
          style={[
            styles.buttonLabel,
            tone === 'record' ? styles.buttonLabelRecord : null,
            tone === 'play' ? styles.buttonLabelPlay : null,
            !disabled && isActivePress(state)
              ? styles.buttonLabelPressed
              : null,
            disabled ? styles.buttonLabelDisabled : null,
          ]}
        >
          {label}
        </Text>
      )}
    </Pressable>
  );
}

const AudioControls = () => {
  const [recording, setRecording] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [paused, setPaused] = useState(false);
  const [loop, setLoop] = useState(false);
  const [recordingPath, setRecordingPath] = useState<string | null>(null);
  const [durationSeconds, setDurationSeconds] = useState<number | null>(null);
  const [notice, setNotice] = useState(
    'Record a take, then play it back. Looping is off until you turn it on.'
  );

  const ensureMicrophonePermission = async () => {
    if (Platform.OS !== 'android') {
      return true;
    }

    const granted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
      {
        title: 'Microphone permission',
        message: 'This app needs the microphone to record audio.',
        buttonPositive: 'OK',
        buttonNegative: 'Cancel',
      }
    );

    return granted === PermissionsAndroid.RESULTS.GRANTED;
  };

  const handleStartRecording = async () => {
    try {
      const allowed = await ensureMicrophonePermission();
      if (!allowed) {
        setNotice('Microphone permission is required before recording.');
        Alert.alert(
          'Permission denied',
          'Cannot record without microphone permission.'
        );
        return;
      }

      const path = await startRecording();
      setRecording(true);
      setPlaying(false);
      setPaused(false);
      setRecordingPath(path);
      setDurationSeconds(null);
      setNotice('Recording.');
    } catch (error) {
      const message = messageFrom(error);
      setNotice(message);
      Alert.alert('Recording error', message);
    }
  };

  const handleStopRecording = async () => {
    try {
      const result = await stopRecording();
      setRecording(false);
      setRecordingPath(result.path);
      setDurationSeconds(result.duration);
      setNotice(`Saved ${result.duration.toFixed(1)} seconds.`);
    } catch (error) {
      const message = messageFrom(error);
      setNotice(message);
      Alert.alert('Stop recording error', message);
    }
  };

  const handleStartRecordingWithMusic = async () => {
    try {
      if (Platform.OS !== 'android') {
        const allowed = await ensureMicrophonePermission();
        if (!allowed) {
          setNotice('Microphone permission is required before recording.');
          return;
        }
      }

      await startRecordingWithMusic(MUSIC_PATH);
      setRecording(true);
      setNotice('Recording with music.');
    } catch (error) {
      const message = messageFrom(error);
      setRecording(false);
      setNotice(
        Platform.OS === 'android'
          ? 'Recording over music is not implemented on Android.'
          : `Could not open ${MUSIC_PATH}. ${message}`
      );
      Alert.alert('Record with music', message);
    }
  };

  const handleStartPlayback = async () => {
    if (!recordingPath) {
      setNotice('Record audio before playback.');
      Alert.alert('No recording', 'Please record audio first.');
      return;
    }

    try {
      await setLoopPlayback(loop);
      await startPlayback(recordingPath);
      setPlaying(true);
      setPaused(false);
      setNotice(loop ? 'Playing on a loop.' : 'Playing once.');
    } catch (error) {
      const message = messageFrom(error);
      setPlaying(false);
      setNotice(message);
      Alert.alert('Playback error', message);
    }
  };

  const handleStopPlayback = async () => {
    try {
      await stopPlayback();
      setPlaying(false);
      setPaused(false);
      setNotice('Playback stopped.');
    } catch (error) {
      const message = messageFrom(error);
      setNotice(message);
      Alert.alert('Stop playback error', message);
    }
  };

  const handlePause = async () => {
    try {
      await pausePlayingAudio();
      setPlaying(false);
      setPaused(true);
      setNotice('Playback paused.');
    } catch (error) {
      const message = messageFrom(error);
      setNotice(message);
      Alert.alert('Pause error', message);
    }
  };

  const handleResume = async () => {
    try {
      await resumePlayingAudio();
      setPlaying(true);
      setPaused(false);
      setNotice('Playback resumed.');
    } catch (error) {
      const message = messageFrom(error);
      setNotice(message);
      Alert.alert('Resume error', message);
    }
  };

  const handleSeek = async () => {
    try {
      await seekToPosition(SEEK_SECONDS);
      setNotice(`Seeked to ${SEEK_SECONDS} seconds.`);
    } catch (error) {
      const message = messageFrom(error);
      setNotice(message);
      Alert.alert('Seek error', message);
    }
  };

  const handleSetLoop = async (shouldLoop: boolean) => {
    try {
      await setLoopPlayback(shouldLoop);
      setLoop(shouldLoop);
      setNotice(shouldLoop ? 'Loop is on.' : 'Loop is off.');
    } catch (error) {
      const message = messageFrom(error);
      setNotice(message);
      Alert.alert('Loop error', message);
    }
  };

  const durationLabel =
    durationSeconds === null
      ? 'Not saved yet'
      : `${durationSeconds.toFixed(1)} s`;

  return (
    <ScrollView
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={styles.screen}
    >
      <StatusBar barStyle="light-content" />
      <View style={styles.header}>
        <Text accessibilityRole="header" style={styles.title}>
          Voice Recorder Kit
        </Text>
        <Text style={styles.subtitle}>Example controls</Text>
      </View>

      <View accessibilityLiveRegion="polite" style={styles.statusCard}>
        <Text style={styles.statusLine}>
          {recording ? 'Recording' : 'Not recording'}
        </Text>
        <Text style={styles.statusLine}>
          {playing ? 'Playing' : paused ? 'Paused' : 'Not playing'}
        </Text>
        <Text style={styles.statusLine}>Duration {durationLabel}</Text>
        <Text style={styles.path}>
          {recordingPath ? recordingPath : 'No file yet'}
        </Text>
        <Text style={styles.notice}>{notice}</Text>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Record</Text>
        <View style={styles.row}>
          <ControlButton
            label="Start recording"
            tone="record"
            disabled={recording}
            onPress={handleStartRecording}
          />
          <ControlButton
            label="Stop recording"
            disabled={!recording}
            onPress={handleStopRecording}
          />
        </View>
        <ControlButton
          label={
            Platform.OS === 'android'
              ? 'Record with music (Android)'
              : 'Record with music'
          }
          disabled={recording}
          onPress={handleStartRecordingWithMusic}
        />
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Play</Text>
        <View style={styles.row}>
          <ControlButton
            label="Play"
            tone="play"
            disabled={!recordingPath || recording || playing}
            onPress={handleStartPlayback}
          />
          <ControlButton
            label="Stop"
            disabled={!playing && !paused}
            onPress={handleStopPlayback}
          />
        </View>
        <View style={styles.row}>
          <ControlButton
            label="Pause"
            disabled={!playing}
            onPress={handlePause}
          />
          <ControlButton
            label="Resume"
            disabled={!paused}
            onPress={handleResume}
          />
        </View>
        <ControlButton
          label="Seek to 5 seconds"
          disabled={!playing && !paused}
          onPress={handleSeek}
        />
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Loop</Text>
        <View style={styles.row}>
          <ControlButton
            label="Turn loop on"
            disabled={loop}
            onPress={() => {
              handleSetLoop(true).catch(() => undefined);
            }}
          />
          <ControlButton
            label="Turn loop off"
            disabled={!loop}
            onPress={() => {
              handleSetLoop(false).catch(() => undefined);
            }}
          />
        </View>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  screen: {
    flexGrow: 1,
    justifyContent: 'center',
    alignItems: 'stretch',
    backgroundColor: '#0E1420',
    paddingTop: 24,
    paddingBottom: 24,
    paddingLeft: 24,
    paddingRight: 24,
    gap: 16,
  },
  header: {
    alignItems: 'center',
    gap: 8,
  },
  title: {
    color: '#F7F8FA',
    fontSize: 16,
    fontWeight: '700',
    textAlign: 'center',
  },
  subtitle: {
    color: '#C5CEDB',
    fontSize: 16,
    fontWeight: '400',
    textAlign: 'center',
  },
  statusCard: {
    backgroundColor: '#1A2332',
    borderRadius: 12,
    borderCurve: 'continuous',
    padding: 16,
    gap: 8,
  },
  statusLine: {
    color: '#F7F8FA',
    fontSize: 16,
    fontWeight: '600',
  },
  path: {
    color: '#C5CEDB',
    fontSize: 16,
    fontWeight: '400',
  },
  notice: {
    color: '#F7F8FA',
    fontSize: 16,
    fontWeight: '400',
  },
  section: {
    gap: 12,
  },
  sectionTitle: {
    color: '#C5CEDB',
    fontSize: 16,
    fontWeight: '600',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'stretch',
    gap: 12,
  },
  button: {
    flexGrow: 1,
    flexBasis: 0,
    minHeight: 48,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#243044',
    borderRadius: 12,
    borderCurve: 'continuous',
    paddingTop: 12,
    paddingBottom: 12,
    paddingLeft: 16,
    paddingRight: 16,
  },
  buttonRecord: {
    backgroundColor: '#9F1239',
  },
  buttonPlay: {
    backgroundColor: '#047857',
  },
  buttonPressed: {
    opacity: 0.72,
  },
  buttonDisabled: {
    backgroundColor: '#1A2332',
    opacity: 1,
  },
  buttonLabel: {
    color: '#F7F8FA',
    fontSize: 16,
    fontWeight: '600',
    textAlign: 'center',
  },
  buttonLabelRecord: {
    color: '#FFFFFF',
  },
  buttonLabelPlay: {
    color: '#FFFFFF',
  },
  buttonLabelPressed: {
    color: '#FFFFFF',
  },
  buttonLabelDisabled: {
    color: '#8B97AB',
  },
});

export default AudioControls;
