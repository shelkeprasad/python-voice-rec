package com.example.pythonvoicedemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import com.arthenica.mobileffmpeg.FFmpeg;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int REQUEST_CODE_RECORD_AUDIO = 100;
    private MediaRecorder mediaRecorder;
    private String filePath;
    private ApiService apiService;
    private EditText editText, editTextid,editTextIp;
    private boolean isRecording = false;
    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    private Uri audioUri;
    private Boolean isConversion = false;
    private static final String PREF_NAME = "MyAppPrefs";
    private static final String KEY_BASE_URL = "base_url";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions();
        apiService = ApiClient.getClient(this).create(ApiService.class);

        editText = findViewById(R.id.edittext);
        editTextid = findViewById(R.id.edit_text_id);
        editTextid = findViewById(R.id.edit_text_id);
        editTextIp = findViewById(R.id.edt_ip);


        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new
                    String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_RECORD_AUDIO);
        }
        SharedPreferences sharedPreference = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String baseUrl = sharedPreference.getString(KEY_BASE_URL, "");
        if (baseUrl == null || baseUrl.isEmpty()) {
            Toast.makeText(this, "please set ip address .", Toast.LENGTH_LONG).show();
        }

        findViewById(R.id.btnStartRecording).setOnClickListener(v -> {
            String id = editTextid.getText().toString();
            String name = editText.getText().toString();

            if (!id.isEmpty() && !name.isEmpty()) {
                startRecording();
            } else {
                Toast.makeText(this, "please enter speaker id & name", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnStopRecording).setOnClickListener(v -> {
            String id = editTextid.getText().toString();
            String name = editText.getText().toString();
            if (mediaRecorder != null) {
                stopRecordingAndRegister(id, name);
            } else {
                Toast.makeText(this, "Please start recording first", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnRecognize).setOnClickListener(v -> {
            String name = editText.getText().toString();
            if (!name.isEmpty()) {
                startAutoRecording();
            } else {
                Toast.makeText(this, "please enter speaker name", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btnRetrain).setOnClickListener(v -> {
           retrainModel();
        });
        findViewById(R.id.btnSubmit).setOnClickListener(v -> {
            String ipAddress = editTextIp.getText().toString().trim();
            if (ipAddress.isEmpty()) {
                editTextIp.setError("Please enter an IP address");
                return;
            }
            SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(KEY_BASE_URL, ipAddress);
            editor.apply();
            Toast.makeText(this, "IP Address saved successfully.", Toast.LENGTH_SHORT).show();

        });
    }

    private void requestPermissions() {
        String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE};
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions = new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissions are required for recording", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
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

    public void stopRecording(String speakername) {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
            Log.d("AudioRecorder", "Recording stopped");
            convertToWav(speakername);
        }
    }

    public void convertToWav(String speakerName) {
        if (audioUri == null) {
            Toast.makeText(this, "No recording to convert", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String speakerNames = speakerName.replace(" ", "");
            String uniqueFileName = speakerNames + "_" + System.currentTimeMillis() + ".wav";

            File wavFile = new File(getExternalFilesDir("ConvertedAudio"), speakerNames.toString() + ".wav");
            if (!wavFile.getParentFile().exists()) wavFile.getParentFile().mkdirs();

            // Copy the content from the recorded Uri to a local MP4 file for FFmpeg
            File mp4File = new File(getExternalFilesDir("TempAudio"), "temp_audio.mp4");
            copyUriToFile(audioUri, mp4File);

            // Convert using FFmpeg
            String[] command = {"-y", "-i", mp4File.getAbsolutePath(), wavFile.getAbsolutePath()};
            // String[] command = {"-i", mp4File.getAbsolutePath(), wavFile.getAbsolutePath()};

            FFmpeg.executeAsync(command, (executionId, returnCode) -> {
                if (returnCode == 0) {
                    Toast.makeText(this, "Conversion successful! Saved at: " + wavFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    Log.d("AudioConverter", "Conversion successful: " + wavFile.getAbsolutePath());
                    isConversion = true;
                    filePath = wavFile.getAbsolutePath();
                } else {
                    filePath = null;
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

    private void registerSpeaker(String speakerId, String filePath) {
        if (isConversion) {
            isConversion = false;
            File file = new File(filePath);
            RequestBody requestFile = RequestBody.create(MediaType.parse("audio/wav"), file);
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);
            RequestBody speakerIdBody = RequestBody.create(MediaType.parse("text/plain"), speakerId);

            apiService.registerSpeaker(body, speakerIdBody).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        //   retrainModel();
                        Toast.makeText(MainActivity.this, "Speaker Registered Successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Failed to Register Speaker", Toast.LENGTH_SHORT).show();
                    }
                    MainActivity.this.filePath = null;
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Toast.makeText(MainActivity.this, "fail register", Toast.LENGTH_SHORT).show();
                    t.printStackTrace();
                    MainActivity.this.filePath = null;
                }
            });
        } else {
            Toast.makeText(this, "failed conversion", Toast.LENGTH_SHORT).show();
            MainActivity.this.filePath = null;
        }
    }

    private void recognizeSpeaker(String filePath) {
        if (isConversion) {
            isConversion = false;
            File file = new File(filePath);

            if (!file.exists() || file.length() == 0) {
                Log.e("RecognizeSpeaker", "Invalid file: " + filePath);
                Toast.makeText(MainActivity.this, "Invalid file: " + filePath, Toast.LENGTH_SHORT).show();
                return;
            }

            RequestBody requestFile = RequestBody.create(MediaType.parse("audio/wav"), file);
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);

            apiService.recognizeSpeaker(body).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonObject = new JSONObject(response.body().string());
                            String label = jsonObject.getString("predicted_label");
                            String confidence = jsonObject.getString("confidence");
                            Toast.makeText(MainActivity.this,
                                    "Speaker: " + label + ", Confidence: " + confidence,
                                    Toast.LENGTH_SHORT).show();
                            MainActivity.this.filePath = null;
                        } catch (Exception e) {
                            Log.e("RecognizeSpeaker", "Error parsing JSON", e);
                            Toast.makeText(MainActivity.this, "Error parsing server response", Toast.LENGTH_SHORT).show();
                            MainActivity.this.filePath = null;
                        }
                    } else {
                        try {
                            String errorBody = response.errorBody().string();
                            Log.e("RecognizeSpeaker", "Server Error: " + errorBody);
                            ;
                        } catch (Exception e) {
                            Log.e("RecognizeSpeaker", "Error reading error response body", e);
                        }
                        MainActivity.this.filePath = null;
                        Toast.makeText(MainActivity.this, "Recognition Failed: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    MainActivity.this.filePath = null;
                    Log.e("RecognizeSpeaker", "API call failed", t);
                    Toast.makeText(MainActivity.this, "API Call Failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

        } else {
            MainActivity.this.filePath = null;
            Toast.makeText(this, "failed conversion", Toast.LENGTH_SHORT).show();
        }
    }

    private void retrainModel() {

        apiService.retrainModel().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    try {
                        String result = response.body().string();
                        Toast.makeText(MainActivity.this, "Model retrained successfully: " + result, Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "Error parsing response", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Retrain failed: " + response.message(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                Toast.makeText(MainActivity.this, "Error calling retrain API", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void stopRecordingAndRegister(String speakerId, String speakername) {
        stopRecording(speakername);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (filePath != null) {
                registerSpeaker(speakerId, filePath);
            } else {
                Toast.makeText(MainActivity.this, "File path is null. Registration failed.", Toast.LENGTH_SHORT).show();
            }
        }, 5000);

    }

    private void startAutoRecording() {
        startRecording();
        new Handler().postDelayed(this::stopRecordingAndRecognize, 8000);
    }

    private void stopRecordingAndRecognize() {
        stopRecording(editText.getText().toString());
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (filePath != null) {
                recognizeSpeaker(filePath);
            } else {
                Toast.makeText(MainActivity.this, "File path is null. Recognition failed.", Toast.LENGTH_SHORT).show();
            }
        }, 8000);
    }

}