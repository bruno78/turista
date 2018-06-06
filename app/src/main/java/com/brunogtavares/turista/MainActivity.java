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

import com.brunogtavares.turista.GCVisionAPIUtils.PermissionUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int GALLERY_PERMISSIONS_REQUEST = 100;
    private static final int GALLERY_IMAGE_REQUEST = 101;
    private static final int CAMERA_PERMISSIONS_REQUEST = 102;
    private static final int CAMERA_IMAGE_REQUEST = 103;

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

    private void callGCVision(final Bitmap bitmap) {
        // Switch text to loading
        mInfoResults.setText(R.string.loading_message);

        // Do the real work in an async task, because we need to use the network anyway
        try {
            // TODO Create an async method
        }
    }


}
