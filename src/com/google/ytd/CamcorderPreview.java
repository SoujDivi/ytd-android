/* Copyright (c) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ytd;

import java.io.File;
import java.io.IOException;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CamcorderPreview extends SurfaceView implements SurfaceHolder.Callback {
  private static final String TAG = "CamcorderPreview";
  
  private MediaRecorder recorder;
  private SurfaceHolder holder;
  private String outputFilePath;
  
  public CamcorderPreview(Context context) throws IOException { 
    super(context);
    
    holder = this.getHolder();
    holder.addCallback(this);
    holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    
    recorder = new MediaRecorder();
    recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
    recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
    recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
    recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H263);
    // Set max duration to 10 minutes.
    recorder.setMaxDuration(10 * 60 * 1000);
    
    File videoFile = File.createTempFile("video", ".3gp",
        Environment.getExternalStorageDirectory());
    outputFilePath = videoFile.getAbsolutePath();
    recorder.setOutputFile(outputFilePath);
  }

  @Override 
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    // no-op
  }

  @Override 
  public void surfaceCreated(SurfaceHolder holder) { 
    recorder.setPreviewDisplay(holder.getSurface());
    
    try {
      recorder.prepare();
      startRecording();
    } catch (IllegalStateException e) {
      Log.e(TAG, "", e);
      
      recorder.release();
      recorder = null;
      outputFilePath = null;
    } catch (IOException e) {
      Log.e(TAG, "", e);
      
      recorder.release();
      recorder = null;
      outputFilePath = null;
    }
  }

  @Override 
  public void surfaceDestroyed(SurfaceHolder holder) {
    stopRecording();
  }

  public void startRecording() {
    if(recorder != null) {
      recorder.start();
    }
  }

  public void stopRecording() {
    if(recorder != null) { 
      recorder.stop(); 
      recorder.release(); 
      recorder = null;
    }
  }
  
  public String getOutputFilePath() {
    return outputFilePath;
  }
}