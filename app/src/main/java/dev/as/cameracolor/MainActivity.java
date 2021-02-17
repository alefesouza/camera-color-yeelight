package dev.as.cameracolor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.palette.graphics.Palette;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Debug;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.otaliastudios.cameraview.BitmapCallback;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;
import com.otaliastudios.cameraview.size.Size;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    TelnetClient connection = null;
    SharedPreferences preferences;
    SharedPreferences.Editor editor;
    CameraView camera = null;
    int currentFrame = 0;
    int changeFrame = 15;

    boolean isRunning;

    public Menu optionsMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        editor = preferences.edit();

        String bulbIp = preferences.getString("bulbIp", null);

        changeFrame = preferences.getInt("changeFrame", 15);

        if (bulbIp != null) {
            this.init(bulbIp);
            return;
        }

        askBulbIp();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        this.optionsMenu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.play_pause:
                currentFrame = 0;

                if (isRunning) {
                    item.setIcon(R.drawable.baseline_play_arrow_white_24);

                    isRunning = false;
                } else {
                    item.setIcon(R.drawable.baseline_pause_white_24);

                    String bulbIp = preferences.getString("bulbIp", null);

                    this.init(bulbIp);
                }

                supportInvalidateOptionsMenu();
                return true;
            case R.id.change_camera:
                if (camera != null) {
                    if (camera.getFacing() == Facing.BACK) {
                        camera.setFacing(Facing.FRONT);
                    } else {
                        camera.setFacing(Facing.BACK);
                    }
                }
                return true;
            case R.id.change_bulb:
                askBulbIp();
                return true;
            case R.id.max_frame:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("How many camera frames to change the color.");

                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                input.setSingleLine();

                input.setText(String.valueOf(preferences.getInt("changeFrame", 15)));

                builder.setView(input);

                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Integer mText = Integer.parseInt(input.getText().toString());

                        editor.putInt("changeFrame", mText);
                        editor.commit();
                    }
                });

                builder.show();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void askBulbIp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Type the Bulb IP");

        final EditText input = new EditText(this);
        input.setSingleLine();

        if (preferences.contains("bulbIp")) {
            input.setText(preferences.getString("bulbIp", null));
        }

        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String mText = input.getText().toString();

                editor.putString("bulbIp", mText);
                editor.commit();
            }
        });

        builder.show();
    }

    public void init(String bulbIp) {
        isRunning = true;

        camera = findViewById(R.id.camera);

        camera.addFrameProcessor(new FrameProcessor() {
            @Override
            @WorkerThread
            public void process(@NonNull Frame frame) {
                if (!isRunning) {
                    return;
                }

                currentFrame++;

                if (currentFrame < changeFrame) {
                    return;
                }

                if (optionsMenu != null) {
                    optionsMenu.findItem(R.id.play_pause).setIcon(R.drawable.baseline_pause_white_24);
                }

                currentFrame = 0;

                Bitmap bitmap = null;

                if (frame.getDataClass() == byte[].class) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    YuvImage yuvImage = new YuvImage(
                            frame.getData(),
                            ImageFormat.NV21,
                            frame.getSize().getWidth(),
                            frame.getSize().getHeight(),
                            null
                    );
                    yuvImage.compressToJpeg(
                            new Rect(0, 0, frame.getSize().getWidth(), frame.getSize().getHeight()),
                            90,
                            out
                    );
                    byte[] imageBytes = out.toByteArray();
                    bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                    int color = getDominantColor(bitmap);

//                        Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
//                            @Override
//                            public void onGenerated(@Nullable Palette palette) {
//                                runOnUiThread(new Runnable() { public void run() {
//                                    int color = getDominantColor(bitmap);
//                                    Toast.makeText(MainActivity.this, Integer.toHexString(color), Toast.LENGTH_SHORT).show();
//
//                                    ColorDrawable cd = new ColorDrawable(Color.parseColor("#" + Integer.toHexString(color)));
//                                    getSupportActionBar().setBackgroundDrawable(cd);
//                                }});
//                            }
//                        });

                    runOnUiThread(new Runnable() {
                        public void run() {
                            setColor(color);
                        }
                    });
                }
            }
        });

        camera.setLifecycleOwner(this);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try  {
                    try {
                        connection = new TelnetClient(bulbIp, 55443);

                        JSONObject onJsonObject = new JSONObject();
                        JSONArray onArray = new JSONArray();

                        onArray.put("on");
                        onArray.put("smooth");
                        onArray.put(500);

                        onJsonObject.put("id", 1);
                        onJsonObject.put("method", "set_power");
                        onJsonObject.put("params", onArray);

                        connection.sendCommand(onJsonObject.toString());

                        JSONObject jsonObject = new JSONObject();
                        JSONArray array = new JSONArray();

                        array.put(100);
                        array.put("smooth");
                        array.put(500);

                        jsonObject.put("id", 2);
                        jsonObject.put("method", "set_bright");
                        jsonObject.put("params", array);

                        connection.sendCommand(jsonObject.toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();
    }

    public void setColor(int color) {
        String hexColor = String.format("%06X", (0xFFFFFF & color));

        ColorDrawable cd = new ColorDrawable(Color.parseColor("#" + hexColor));
        getSupportActionBar().setBackgroundDrawable(cd);

        if (connection == null) {
            return;
        }

        try {
            JSONObject jsonObject = new JSONObject();
            JSONArray array = new JSONArray();

            array.put(Integer.parseInt(hexColor, 16));
            array.put("smooth");
            array.put(500);

            jsonObject.put("id", 3);
            jsonObject.put("method", "set_rgb");
            jsonObject.put("params", array);

            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        connection.sendCommand(jsonObject.toString());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }}
            });

            thread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getDominantColor(Bitmap bitmap) {
        Bitmap newBitmap = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
        final int color = newBitmap.getPixel(0, 0);
        newBitmap.recycle();
        return color;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}