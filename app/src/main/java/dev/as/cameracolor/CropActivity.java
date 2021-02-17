package dev.as.cameracolor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.littlecheesecake.croplayout.EditPhotoView;
import me.littlecheesecake.croplayout.EditableImage;
import me.littlecheesecake.croplayout.handler.OnBoxChangedListener;
import me.littlecheesecake.croplayout.model.ScalableBox;

public class CropActivity extends AppCompatActivity {
    private int cropX;
    private int cropY;
    private int cropWidth;
    private int cropHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        final EditPhotoView imageView = (EditPhotoView) findViewById(R.id.editable_image);

        String path = Environment.getExternalStorageDirectory().toString();
        File file = new File(path, "cameracolor_crop.jpg");

        final EditableImage image = new EditableImage(file.getAbsolutePath());

        ScalableBox box1 = new ScalableBox(25,180,640,880);
        List<ScalableBox> boxes = new ArrayList<>();
        boxes.add(box1);
        image.setBoxes(boxes);
        imageView.initView(this, image);

        imageView.setOnBoxChangedListener(new OnBoxChangedListener() {
            @Override
            public void onChanged(int x1, int y1, int x2, int y2) {
                cropX = x1;
                cropY = y1;
                cropWidth = x2;
                cropHeight = y2;

                Toast.makeText(CropActivity.this, "box: [" + x1 + "," + y1 +"],[" + x2 + "," + y2 + "]", Toast.LENGTH_SHORT).show();
            }
        });

        Button button = findViewById(R.id.save);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(CropActivity.this);
                SharedPreferences.Editor editor = preferences.edit();

                editor.putInt("cropX", cropX);
                editor.putInt("cropY", cropY);
                editor.putInt("cropHeight", cropHeight);
                editor.putInt("cropWidth", cropWidth);

                editor.commit();

                finish();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }

        return super.onOptionsItemSelected(item);
    }
}