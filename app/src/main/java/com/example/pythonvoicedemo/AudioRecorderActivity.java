package com.example.pythonvoicedemo;

import androidx.appcompat.app.AppCompatActivity;

import android.net.Uri;
import android.os.Bundle;
import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arthenica.mobileffmpeg.FFmpeg;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AudioRecorderActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_RECORD_AUDIO = 100;
    private MediaRecorder mediaRecorder;
    private String outputFile;
    private Uri audioUri;
    private Button startButton, stopButton, convertButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_recorder);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        convertButton = findViewById(R.id.convert_button);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new
                    String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_RECORD_AUDIO);
        }

        startButton.setOnClickListener(v -> startRecording());
        stopButton.setOnClickListener(v -> stopRecording());
        convertButton.setOnClickListener(v -> convertToWav());
    }
    public void startRecording() {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "recorded_audio_" + System.currentTimeMillis() + ".mp4");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/RecordedAudio/"); // Scoped storage location

        audioUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);

        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(resolver.openFileDescriptor(audioUri, "w").getFileDescriptor());
            mediaRecorder.prepare();
            mediaRecorder.start();
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
            Log.d("AudioRecorder", "Recording started");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopRecording() {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
            Log.d("AudioRecorder", "Recording stopped");
        }
    }

    public void convertToWav() {
        if (audioUri == null) {
            Toast.makeText(this, "No recording to convert", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Create a new file in app-specific storage
            File wavFile = new File(getExternalFilesDir("ConvertedAudio"), "converted_audio1.wav");
            if (!wavFile.getParentFile().exists()) wavFile.getParentFile().mkdirs();

            // Copy the content from the recorded Uri to a local MP4 file for FFmpeg
            File mp4File = new File(getExternalFilesDir("TempAudio"), "temp_audio.mp4");
            copyUriToFile(audioUri, mp4File);

            // Convert using FFmpeg
            String[] command = {"-i", mp4File.getAbsolutePath(), wavFile.getAbsolutePath()};
            FFmpeg.executeAsync(command, (executionId, returnCode) -> {
                if (returnCode == 0) {
                    Toast.makeText(this, "Conversion successful! Saved at: " + wavFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    Log.d("AudioConverter", "Conversion successful: " + wavFile.getAbsolutePath());
                } else {
                    Toast.makeText(this, "Conversion failed", Toast.LENGTH_SHORT).show();
                    Log.d("AudioConverter", "Conversion failed with code: " + returnCode);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyUriToFile(Uri uri, File file) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(file)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        }
    }

}