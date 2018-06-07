package com.brunogtavares.turista;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.brunogtavares.turista.GCVisionAPIUtils.PackageManagerUtils;
import com.brunogtavares.turista.GCVisionAPIUtils.PermissionUtils;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int GALLERY_PERMISSIONS_REQUEST = 100;
    private static final int GALLERY_IMAGE_REQUEST = 101;
    private static final int CAMERA_PERMISSIONS_REQUEST = 102;
    private static final int CAMERA_IMAGE_REQUEST = 103;

    private static final String CLOUD_API_KEY = BuildConfig.GCP_VISION_API_KEY;

    private static final String ANDROID_CERT_HEADER = "X-Android-Cert";
    private static final String ANDROID_PACKAGE_HEADER = "X-Android-Package";
    private static final String LANDMARK_DETECTION = "LANDMARK_DETECTION";

    public static final String FILE_NAME = "temp.jpg";
    private static final int MAX_DIMENSION = 1200;

    private ImageView mImage;
    private TextView mInfoResults;
    private Button mSelectImageButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImage = (ImageView) findViewById(R.id.iv_selected_image);
        mInfoResults = (TextView) findViewById(R.id.tv_info_results);
        mSelectImageButton = (Button) findViewById(R.id.bt_select_image_button);

        mSelectImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage(R.string.dialog_select_prompt)
                        .setPositiveButton(R.string.dialog_select_gallery,
                                new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startGalleryChooser();
                            }
                        })
                        .setNegativeButton(R.string.dialog_select_camera,
                                new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startCamera();
                            }
                        });
                builder.create().show();
            }
        });
    }


    private void startGalleryChooser() {
        if (PermissionUtils.requestPermission(this, GALLERY_PERMISSIONS_REQUEST,
                Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select a photo"),
                    GALLERY_IMAGE_REQUEST);
        }
    }

    private void startCamera() {
        if (PermissionUtils.requestPermission(
                this,
                CAMERA_PERMISSIONS_REQUEST,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA)) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            Uri photoUri = FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".provider", getCameraFile());
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }

    private File getCameraFile() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return new File(dir, FILE_NAME);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GALLERY_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            uploadImage(data.getData());
        }
        else if (requestCode == CAMERA_IMAGE_REQUEST && resultCode == RESULT_OK) {
            Uri photoUri = FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".provider", getCameraFile());
            uploadImage(photoUri);
        }
    }


    public void uploadImage(Uri uri) {
        try {
            // scale the image to save on bandwidth
            Bitmap bitmap = scaleBitmapDown(
                    MediaStore.Images.Media.getBitmap(getContentResolver(), uri),
                    MAX_DIMENSION);
            callGCVision(bitmap);
            mImage.setImageBitmap(bitmap);
        } catch (FileNotFoundException e) {
            Log.d(TAG, "Image selected was null...");
            Toast.makeText(this, R.string.image_selected_error, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.d(TAG, "Image picking failed because " + e.getMessage());
            Toast.makeText(this, R.string.image_selected_error, Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = maxDimension;
        int resizedHeight = maxDimension;

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension;
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = maxDimension;
        }

        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }

    private Vision.Images.Annotate prepareAnnotationRequest(final Bitmap bitmap) throws IOException {
        HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        VisionRequestInitializer requestInitializer = new VisionRequestInitializer(CLOUD_API_KEY) {

            /**
             * We override this so we can inject important identifying fields into the HTTP
             * headers. This enables use of a restricted cloud platform API key.
             */
            @Override
            protected void initializeVisionRequest(VisionRequest<?> visionRequest) throws IOException {
                super.initializeVisionRequest(visionRequest);

                String packageName = getPackageName();
                visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, packageName);
                String signature = PackageManagerUtils.getSignature(getPackageManager(), packageName);
                visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, signature);

            }
        };

        Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
        builder.setVisionRequestInitializer(requestInitializer);

        Vision vision = builder.build();

        BatchAnnotateImagesRequest batchAnnotateImagesRequest = new BatchAnnotateImagesRequest();
        batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>(){{
            AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

            // Add Image
            Image base64EncodeImage = new Image();
            // Convert the bitmap to a JPEG
            // Just in case it's a format that Android understands but Cloud Vision
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
            byte[] imageBytes = byteArrayOutputStream.toByteArray();

            // Base64 encode the JPEG
            base64EncodeImage.encodeContent(imageBytes);
            annotateImageRequest.setImage(base64EncodeImage);

            // add LANDMARk_DETECTION FEATURE
            annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                Feature feature = new Feature();
                feature.setType(LANDMARK_DETECTION);
                // feature.getMaxResults(10);
                add(feature);
            }});

            // the list of one thing to the request
            add(annotateImageRequest);
        }});

        Vision.Images.Annotate annotateRequest = vision.images().annotate(batchAnnotateImagesRequest);

        // Due to a bug: requests to Vision API containing large images fail when GZipped
        annotateRequest.setDisableGZipContent(true);

        Log.d(TAG, "created Cloud Vision request object, sending request");

        return annotateRequest;

    }

    private static class LandmarkDetectionTask extends AsyncTask<Object, Void, String> {
        private WeakReference<MainActivity> mMainActivityWeakReference;
        private Vision.Images.Annotate mRequest;

        private LandmarkDetectionTask(MainActivity activity, Vision.Images.Annotate annotate) {
            this.mMainActivityWeakReference = new WeakReference<>(activity);
            this.mRequest = annotate;
        }

        @Override
        protected String doInBackground(Object... objects) {

            try {
                Log.d(TAG, "Created Google Cloud Vision request object, sending request");
                BatchAnnotateImagesResponse response = mRequest.execute();
                return convertResponseToString(response);

            } catch (GoogleJsonResponseException e) {
                Log.d(TAG, "failed to make API request because " + e.getContent());
            } catch (IOException e) {
                Log.d(TAG, "failed to make API request because of other IOException " +
                        e.getMessage());
            }
            return "Cloud Vision API request failed. Check logs for details.";
        }
    }

    private static String convertResponseToString(BatchAnnotateImagesResponse response) {
        // this is the response:
//        {
//            "responses": [
//            {
//                "landmarkAnnotations": [
//                {
//                    "mid": "/g/1hg4vfsw1",
//                        "description": "Palace of Fine Arts",
//                        "score": 0.47093904,
//                        "boundingPoly": {
        if (response == null) {
            return "Unrecognized Location";
        }

        return response.getResponses().get(0).getLandmarkAnnotations().get(0).getDescription();
    }

    private void callGCVision(final Bitmap bitmap) {
        // Switch text to loading
        mInfoResults.setText(R.string.loading_message);

        // Do the real work in an async task, because we need to use the network anyway
//        try {
//            // TODO Check for network status and create an async method
//
//        }
    }


}
