/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shaliu.mustache.augmentedfaces;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedFace;
import com.google.ar.core.AugmentedFace.RegionType;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Config.AugmentedFaceMode;
import com.google.ar.core.Frame;
import com.google.ar.core.PlaybackStatus;
import com.google.ar.core.Pose;
import com.google.ar.core.RecordingConfig;
import com.google.ar.core.RecordingStatus;
import com.google.ar.core.Session;
import com.google.ar.core.Track;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.PlaybackFailedException;
import com.google.ar.core.exceptions.RecordingFailedException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.shaliu.mustache.R;
import com.shaliu.mustache.beans.Video;
import com.shaliu.mustache.common.helpers.CameraPermissionHelper;
import com.shaliu.mustache.common.helpers.DisplayRotationHelper;
import com.shaliu.mustache.common.helpers.FullScreenHelper;
import com.shaliu.mustache.common.helpers.SnackbarHelper;
import com.shaliu.mustache.common.helpers.TrackingStateHelper;
import com.shaliu.mustache.common.rendering.BackgroundRenderer;
import com.shaliu.mustache.common.rendering.ObjectRenderer;

import org.joda.time.DateTime;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3d model of the Android robot.
 * <p>
 * Sha: I added some logics in AugmentedFacesActivity. In real project, I should not do it. Put logics into ViewModel or Presenter.
 */
public class AugmentedFacesActivity extends AppCompatActivity implements GLSurfaceView.Renderer, View.OnClickListener {
    private static final String TAG = AugmentedFacesActivity.class.getSimpleName();

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView;

    private boolean installRequested;

    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);

    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final AugmentedFaceRenderer augmentedFaceRenderer = new AugmentedFaceRenderer();
    private final ObjectRenderer noseObject = new ObjectRenderer();
    ;
    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] noseMatrix = new float[16];
    private final float[] rightEarMatrix = new float[16];
    private final float[] leftEarMatrix = new float[16];
    private static final float[] DEFAULT_COLOR = new float[]{0f, 0f, 0f, 0f};

    private static final String MP4_DATASET_FILENAME_TEMPLATE = "arcore-dataset-%s.mp4";
    private static final String MP4_DATASET_TIMESTAMP_FORMAT = "yyyy-MM-dd-HH-mm-ss";
    private String lastRecordingDatasetPath;
    private static final UUID ANCHOR_TRACK_ID =
            UUID.fromString("a65e59fc-2e13-4607-b514-35302121c138");
    private static final String ANCHOR_TRACK_MIME_TYPE =
            "application/hello-recording-playback-anchor";
    private final AtomicReference<AppState> currentState = new AtomicReference<>(AppState.IDLE);
    private String playbackDatasetPath;
    private Button recordingOrStop;
    private static final String[] REQUIRED_PERMISSIONS_FOR_ANDROID_S_AND_BELOW = {
            Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final String[] REQUIRED_PERMISSIONS_FOR_ANDROID_T_AND_ABOVE = {
            Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_VIDEO
    };

    private enum AppState {
        IDLE,
        RECORDING,
        PLAYBACK
    }

    private static final int PERMISSIONS_REQUEST_CODE = 0;
    private RecyclerView mustaches;
    private LinearLayoutManager mustachesRecyclerViewLM;
    private ImageAdapter mustachesRecyclerViewAdapter;
    private TextView goToHistoryRecordingsAct;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.surfaceview);
        mustaches = findViewById(R.id.mustaches_list);
        mustachesRecyclerViewAdapter = new ImageAdapter();
        mustaches.setAdapter(mustachesRecyclerViewAdapter);
        mustachesRecyclerViewLM = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        mustachesRecyclerViewLM.scrollToPosition(ArApplicatioin.positionMustache);
        mustaches.setLayoutManager(mustachesRecyclerViewLM);
        mustachesRecyclerViewAdapter.setItemClickListener(new ItemClickListener());
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        recordingOrStop = findViewById(R.id.recording_stop);
        recordingOrStop.setOnClickListener(this::onClick);

        goToHistoryRecordingsAct = findViewById(R.id.history);
        goToHistoryRecordingsAct.setOnClickListener(this::onClick);
        // Set up renderer.
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surfaceView.setWillNotDraw(false);

        installRequested = false;

        ((ArApplicatioin) getApplication()).historyItemClickedListener = new HistoryItemClickedListener();
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (requestPermissions(getPermissionsForTargetSDK())) {
            return;
        }
        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                // Create the session and configure it to use a front-facing (selfie) camera.
                session = new Session(/* context= */ this, EnumSet.noneOf(Session.Feature.class));
                CameraConfigFilter cameraConfigFilter = new CameraConfigFilter(session);
                cameraConfigFilter.setFacingDirection(CameraConfig.FacingDirection.FRONT);
                List<CameraConfig> cameraConfigs = session.getSupportedCameraConfigs(cameraConfigFilter);
                if (!cameraConfigs.isEmpty()) {
                    // Element 0 contains the camera config that best matches the session feature
                    // and filter settings.
                    session.setCameraConfig(cameraConfigs.get(0));
                } else {
                    message = "This device does not have a front-facing (selfie) camera";
                    exception = new UnavailableDeviceNotCompatibleException(message);
                }
                configureSession();

            } catch (UnavailableArcoreNotInstalledException
                     | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            session = null;
            return;
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            // Explicitly close ARCore Session to release native resources.
            // Review the API reference for important considerations before calling close() in apps with
            // more complicated lifecycle requirements:
            // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
            session.close();
            session = null;
        }
        surfaceView = null;
        displayRotationHelper = null;
        ((ArApplicatioin) getApplication()).releaseResource(getClass().getSimpleName());
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.recording_stop:
                String info = ((Button) v).getText().toString();
                if ("START".equalsIgnoreCase(info)) {
                    startRecording();
                } else {
                    stopRecording();
                }
                break;
            case R.id.history:
                Intent intent = new Intent(this, GridActivity.class);
                startActivity(intent);

                break;
            default:

        }

    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(/*context=*/ this);
            augmentedFaceRenderer.createOnGlThread(this, "models/freckles.png");
            augmentedFaceRenderer.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
//      noseObject.createOnGlThread(/*context=*/ this, "models/nose.obj", "models/nose_fur.png");
            switch (ArApplicatioin.index) {
                case 1:
                    noseObject.createOnGlThread(/*context=*/ this, "models/nose.obj", "models/1.png");
                    break;
                case 2:
                    noseObject.createOnGlThread(/*context=*/ this, "models/nose.obj", "models/2.png");
                    break;
                case 3:
                    noseObject.createOnGlThread(/*context=*/ this, "models/nose.obj", "models/3.png");
                    break;
//                case 4:
//                    noseObject.createOnGlThread(/*context=*/ this, "models/nose.obj", "models/4.png");
//                    break;
                default:
            }

            noseObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
            noseObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);

        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (session == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = session.update();
            Camera camera = frame.getCamera();

            // Get projection matrix.
            float[] projectionMatrix = new float[16];
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewMatrix = new float[16];
            camera.getViewMatrix(viewMatrix, 0);

            // Compute lighting from average intensity of the image.
            // The first three components are color scaling factors.
            // The last one is the average pixel intensity in gamma space.
            final float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            // If frame is ready, render camera preview image to the GL surface.
            backgroundRenderer.draw(frame);

            // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
            trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

            // ARCore's face detection works best on upright faces, relative to gravity.
            // If the device cannot determine a screen side aligned with gravity, face
            // detection may not work optimally.
            Collection<AugmentedFace> faces = session.getAllTrackables(AugmentedFace.class);
            for (AugmentedFace face : faces) {
                if (face.getTrackingState() != TrackingState.TRACKING) {
                    break;
                }

                float scaleFactor = 1.0f;

                // Face objects use transparency so they must be rendered back to front without depth write.
                GLES20.glDepthMask(false);

                // Each face's region poses, mesh vertices, and mesh normals are updated every frame.

                // 1. Render the face mesh first, behind any 3D objects attached to the face regions.
//        float[] modelMatrix = new float[16];
//        face.getCenterPose().toMatrix(modelMatrix, 0);
//        augmentedFaceRenderer.draw(
//            projectionMatrix, viewMatrix, modelMatrix, colorCorrectionRgba, face);

                // 2. Next, render the 3D objects attached to the forehead.
//        face.getRegionPose(RegionType.FOREHEAD_RIGHT).toMatrix(rightEarMatrix, 0);
//        rightEarObject.updateModelMatrix(rightEarMatrix, scaleFactor);
//        rightEarObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR);
//
//        face.getRegionPose(RegionType.FOREHEAD_LEFT).toMatrix(leftEarMatrix, 0);
//        leftEarObject.updateModelMatrix(leftEarMatrix, scaleFactor);
//        leftEarObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR);

                // 3. Render the nose last so that it is not occluded by face mesh or by 3D objects attached
                // to the forehead regions.
                Pose noseRegionPose = face.getRegionPose(RegionType.NOSE_TIP);
                noseRegionPose.toMatrix(noseMatrix, 0);

                // Sha: move down mustache a little bit
                float[] translationMatrix = new float[16];
                Matrix.setIdentityM(translationMatrix, 0);
                switch (ArApplicatioin.index) {
                    case 1:
                        translationMatrix[13] = -0.02f;
                        break;
                    case 2:
                        translationMatrix[13] = -0.025f;
                        break;
                    case 3:
                        translationMatrix[13] = -0.04f;
                        break;
//                    case 4:
//                        translationMatrix[13] = -0.021f;
//                        break;
                    default:
                }

                float[] modifiedMatrix = new float[16];
                Matrix.multiplyMM(modifiedMatrix, 0, noseMatrix, 0, translationMatrix, 0);

                noseObject.updateModelMatrix(modifiedMatrix, scaleFactor);
//        noseObject.updateModelMatrix(noseMatrix, scaleFactor);
                noseObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR);
            }
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        } finally {
            GLES20.glDepthMask(true);
        }
    }

    private void setPlaybackDatasetPath(long time) {
        if (session.getPlaybackStatus() == PlaybackStatus.OK) {
            setStateAndUpdateUI(AppState.PLAYBACK);
            return;
        }
        if (playbackDatasetPath != null) {
//            session.pause();
            try {
                session.setPlaybackDatasetUri(Uri.fromFile(new File(playbackDatasetPath)));
            } catch (PlaybackFailedException e) {
                throw new RuntimeException(e);
            }
            setStateAndUpdateUI(AppState.PLAYBACK);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                //Sha: session.close(); if i close session first, then create a new session. I got a crash.
                                reCreateSession();
                            }
                        }, 0);

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }, time + 500);
        }
    }

    private void reCreateSession() {
        session = null;
        displayRotationHelper.onPause();
        surfaceView.onPause();

        // Create the session and configure it to use a front-facing (selfie) camera.
        try {
            session = new Session(/* context= */ AugmentedFacesActivity.this, EnumSet.noneOf(Session.Feature.class));
        } catch (UnavailableArcoreNotInstalledException e) {
            throw new RuntimeException(e);
        } catch (UnavailableApkTooOldException e) {
            throw new RuntimeException(e);
        } catch (UnavailableSdkTooOldException e) {
            throw new RuntimeException(e);
        } catch (UnavailableDeviceNotCompatibleException e) {
            throw new RuntimeException(e);
        }
        CameraConfigFilter cameraConfigFilter = new CameraConfigFilter(session);
        cameraConfigFilter.setFacingDirection(CameraConfig.FacingDirection.FRONT);
        List<CameraConfig> cameraConfigs = session.getSupportedCameraConfigs(cameraConfigFilter);
        if (!cameraConfigs.isEmpty()) {
            // Element 0 contains the camera config that best matches the session feature
            // and filter settings.
            session.setCameraConfig(cameraConfigs.get(0));
        }
        configureSession();

        surfaceView.onResume();
        displayRotationHelper.onResume();

        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            throw new RuntimeException(e);
        }
        setStateAndUpdateUI(AppState.IDLE);
        updateUI();
    }

    class ItemClickListener {
        public void onClick(int i) {
            ArApplicatioin.index = i + 1;
            session.pause();
            restartActivityWithIntentExtras();
        }
    }

    public class HistoryItemClickedListener {
        /**
         * I should use activity + two fragment + viewmodel, then two fragment can share the same session obj.
         */
        public void historyItemClicked(String filePath, long videoDuration) {
            currentState.set(AppState.PLAYBACK);
            playbackDatasetPath = filePath;
            setPlaybackDatasetPath(videoDuration);
        }
    }

    /**
     * Sha: Restarts current activity to enter or exit playback mode.
     * Sha: Using this way to help me change mustaches is inspired by project recording_playback_java
     *
     * <p>This method simulates an app with separate activities for recording and playback by
     * restarting the current activity and passing in the desired app state via an intent with extras.
     */
    private void restartActivityWithIntentExtras() {
        ArApplicatioin.positionMustache = mustachesRecyclerViewLM.findFirstCompletelyVisibleItemPosition();

        Intent intent = this.getIntent();
        this.finish();
        this.startActivity(intent);
//        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private void configureSession() {
        Config config = new Config(session);
        config.setAugmentedFaceMode(AugmentedFaceMode.MESH3D);
        session.configure(config);
    }

    /**
     * Performs action when start_recording button is clicked.
     */
    private void startRecording() {
        try {
            lastRecordingDatasetPath = getNewDatasetPath();
            if (lastRecordingDatasetPath == null) {
                Log.e(TAG, "Failed to generate a MP4 dataset path for recording.");
                return;
            }

            Track anchorTrack =
                    new Track(session).setId(ANCHOR_TRACK_ID).setMimeType(ANCHOR_TRACK_MIME_TYPE);

            session.startRecording(
                    new RecordingConfig(session)
                            .setMp4DatasetUri(Uri.fromFile(new File(lastRecordingDatasetPath)))
                            .setAutoStopOnPause(false)
                            .addTrack(anchorTrack));
        } catch (RecordingFailedException e) {
            String errorMessage = "Failed to start recording. " + e;
            Log.e(TAG, errorMessage, e);
            messageSnackbarHelper.showError(this, errorMessage);
            return;
        }
        if (session.getRecordingStatus() != RecordingStatus.OK) {
            Log.e(TAG,
                    "Failed to start recording, recording status is " + session.getRecordingStatus());
            return;
        }
        setStateAndUpdateUI(AppState.RECORDING);
    }

    /**
     * Performs action when stop_recording button is clicked.
     */
    private void stopRecording() {
        try {
            session.stopRecording();
        } catch (RecordingFailedException e) {
            String errorMessage = "Failed to stop recording. " + e;
            Log.e(TAG, errorMessage, e);
            messageSnackbarHelper.showError(this, errorMessage);
            return;
        }
        if (session.getRecordingStatus() == RecordingStatus.OK) {
            Log.e(TAG,
                    "Failed to stop recording, recording status is " + session.getRecordingStatus());
            return;
        }
        if (new File(lastRecordingDatasetPath).exists()) {
            playbackDatasetPath = lastRecordingDatasetPath;
            Log.d(TAG, "MP4 dataset has been saved at: " + playbackDatasetPath);
        } else {
            Log.e(TAG,
                    "Recording failed. File " + lastRecordingDatasetPath + " wasn't created.");
        }

        // Sha: case1--> kotlin+coroutines, i can coroutines to call room methods。 case2--> RxJava
        new Thread(new Runnable() {
            @Override
            public void run() {
                Video video = new Video();
                video.filePath = lastRecordingDatasetPath;
                ((ArApplicatioin)getApplication()).videoDao.insert(video);
            }
        }).start();

        setStateAndUpdateUI(AppState.IDLE);
    }

    private String getNewDatasetPath() {
        File baseDir = this.getExternalFilesDir(null);
        if (baseDir == null) {
            return null;
        }
        String path = new File(this.getExternalFilesDir(null), getNewMp4DatasetFilename()).getAbsolutePath();
        System.out.println("sha path" + path);
        return path;
    }

    private static String getNewMp4DatasetFilename() {
        return String.format(
                Locale.ENGLISH,
                MP4_DATASET_FILENAME_TEMPLATE,
                DateTime.now().toString(MP4_DATASET_TIMESTAMP_FORMAT));
    }

    private void setStateAndUpdateUI(AppState state) {
        currentState.set(state);
        updateUI();
    }

    private void updateUI() {
        switch (currentState.get()) {
            case IDLE:
                recordingOrStop.setText("START");
                mustaches.setVisibility(View.VISIBLE);
                recordingOrStop.setVisibility(View.VISIBLE);
                goToHistoryRecordingsAct.setVisibility(View.VISIBLE);
                break;
            case RECORDING:
                recordingOrStop.setText("STOP");
                mustaches.setVisibility(View.INVISIBLE);
                goToHistoryRecordingsAct.setVisibility(View.INVISIBLE);
                break;
            case PLAYBACK:
                mustaches.setVisibility(View.INVISIBLE);
                recordingOrStop.setVisibility(View.INVISIBLE);
                goToHistoryRecordingsAct.setVisibility(View.INVISIBLE);
                break;
            default:
        }
    }

    private String[] getPermissionsForTargetSDK() {
        int targetSdkVersion = this.getApplicationInfo().targetSdkVersion;
        int buildSdkVersion = Build.VERSION.SDK_INT;
        return targetSdkVersion >= Build.VERSION_CODES.TIRAMISU && buildSdkVersion >= Build.VERSION_CODES.TIRAMISU
                ? REQUIRED_PERMISSIONS_FOR_ANDROID_T_AND_ABOVE
                : REQUIRED_PERMISSIONS_FOR_ANDROID_S_AND_BELOW;
    }

    private boolean requestPermissions(String[] permissions) {
        List<String> permissionsNotGranted = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNotGranted.add(permission);
            }
        }
        if (permissionsNotGranted.isEmpty()) {
            return false;
        }
        ActivityCompat.requestPermissions(
                this, permissionsNotGranted.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
        return true;
    }
}
