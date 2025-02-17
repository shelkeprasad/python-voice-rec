package com.example.pythonvoicedemo;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ApiService {
    @POST("/retrain")
    Call<ResponseBody> retrainModel();

    @Multipart
    @POST("/register")
    Call<ResponseBody> registerSpeaker(
            @Part MultipartBody.Part file,
            @Part("speaker_id") RequestBody speakerId
    );

    @Multipart
    @POST("/recognize")
    Call<ResponseBody> recognizeSpeaker(
            @Part MultipartBody.Part file
    );

}
