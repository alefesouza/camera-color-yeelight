package dev.as.cameracolor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.palette.graphics.Palette;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    List<TelnetClient> connections = new ArrayList<>();
    SharedPreferences preferences;
    SharedPreferences.Editor editor;
    CameraView camera = null;

    boolean isRunning = false;
    boolean configureCrop = false;
    boolean allowChange = false;

    public Menu optionsMenu;

    Timer timer = null;
    int colorVariant = 3;

    String selectedIp = "";
    String[] bulbIps = new String[]{};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        editor = preferences.edit();

        bulbIps = preferences.getString("bulbIps", "").split(",");

        if (bulbIps[0].isEmpty()) {
            bulbIps = new String[]{};
        }

        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                1);

        colorVariant = preferences.getInt("colorVariant", 3);

        camera = findViewById(R.id.camera);

        camera.addFrameProcessor(new FrameProcessor() {
            @Override
            @WorkerThread
            public void process(@NonNull Frame frame) {
                if (!isRunning) {
                    return;
                }

                if (!allowChange) {
                    return;
                }

                allowChange = false;

                if (optionsMenu != null) {
                    optionsMenu.findItem(R.id.play_pause).setIcon(R.drawable.baseline_pause_white_24);
                }

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
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                    if (configureCrop) {
                        isRunning = false;
                        configureCrop = false;

                        String path = Environment.getExternalStorageDirectory().toString();
                        File file = new File(path, "cameracolor_crop.jpg");

                        if (file.exists()) {
                            file.delete();
                        }

                        try (FileOutputStream out2 = new FileOutputStream(file)) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out2);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        Intent intent = new Intent(MainActivity.this, CropActivity.class);
                        intent.putExtra("selectedIp", selectedIp);
                        startActivity(intent);
                    }

                    int index = 0;

                    for (String bulbIp : bulbIps) {
                        calcBulbColorForIp(bulbIp, bitmap, index);

                        index++;
                    }
                }
            }
        });

        camera.setLifecycleOwner(this);

        if (bulbIps.length > 0) {
            this.init();

            setTimer();
            return;
        }

        askBulbIp();
    }

    private void calcBulbColorForIp(String bulbIp, Bitmap bitmap, int index) {
        if (index >= connections.size()) {
            return;
        }

        int cropWidth = preferences.getInt(bulbIp + "-cropWidth", 0);

        if (cropWidth > 0) {
            int cropX = preferences.getInt(bulbIp + "-cropX", 0);
            int cropY = preferences.getInt(bulbIp + "-cropY", 0);
            int cropHeight = preferences.getInt(bulbIp + "-cropHeight", 0);

            Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight);

            bitmap = resizedBitmap;
        }

        if (index == 0) {
            ImageView imageView = findViewById(R.id.image);
            imageView.setImageBitmap(bitmap);
        }

        if (colorVariant == 6) {
            setColor(getDominantColor(bitmap), connections.get(index), index);
            return;
        }

        Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
            @Override
            public void onGenerated(@Nullable Palette palette) {
                runOnUiThread(new Runnable() { public void run() {
                    int color = 0;

                    switch (colorVariant) {
                        case 0:
                            color = palette.getLightMutedColor(0);
                            break;
                        case 1:
                            color = palette.getLightVibrantColor(0);
                            break;
                        case 2:
                            color = palette.getMutedColor(0);
                            break;
                        case 3:
                            color = palette.getDominantColor(0);
                            break;
                        case 4:
                            color = palette.getDarkMutedColor(0);
                            break;
                        case 5:
                            color = palette.getDarkVibrantColor(0);
                            break;
                    }

                    setColor(color, connections.get(index), index);
                }});
            }
        });
    }

    private void setTimer() {
        timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                allowChange = true;
            }
        },2000,60000 / preferences.getInt("connectionsPerMinute", 60));
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
                if (isRunning) {
                    item.setIcon(R.drawable.baseline_play_arrow_white_24);

                    closeConnections();
                    isRunning = false;
                } else {
                    item.setIcon(R.drawable.baseline_pause_white_24);

                    this.init();
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
            case R.id.configure_crop:
                if (bulbIps.length == 0) {
                    Toast.makeText(MainActivity.this, "Please register a Yeelight Bulb IP", Toast.LENGTH_SHORT).show();
                    return true;
                }

                if (bulbIps.length == 1) {
                    selectedIp = bulbIps[0];

                    new Handler().postDelayed(new Runnable() {
                        public void run() {
                            configureCrop = true;
                        }
                    }, 3000);

                    Toast.makeText(MainActivity.this, "3 seconds to position phone...", Toast.LENGTH_SHORT).show();
                    return true;
                }

                AlertDialog.Builder builderSingle = new AlertDialog.Builder(MainActivity.this);
                builderSingle.setTitle("Select Bulb IP:");

                final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.select_dialog_singlechoice);

                for (String bulbIp : bulbIps) {
                    arrayAdapter.add(bulbIp.trim());
                }

                builderSingle.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedIp = arrayAdapter.getItem(which);

                        new Handler().postDelayed(new Runnable() {
                            public void run() {
                                configureCrop = true;
                            }
                        }, 3000);

                        Toast.makeText(MainActivity.this, "3 seconds to position phone...", Toast.LENGTH_SHORT).show();
                    }
                });
                builderSingle.show();
                return true;
            case R.id.connections_per_minute:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("How many connections per minute (60 = recommended).");

                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                input.setSingleLine();

                input.setText(String.valueOf(preferences.getInt("connectionsPerMinute", 60)));

                builder.setView(input);

                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Integer mText = Integer.parseInt(input.getText().toString());

                        editor.putInt("connectionsPerMinute", mText);
                        editor.commit();

                        timer.cancel();
                        timer.purge();

                        setTimer();
                    }
                });

                builder.show();
                return true;
            case R.id.change_color_variant:
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                alertDialog.setTitle("AlertDialog");
                String[] items = {"Light Muted", "Light Vibrant", "Muted", "Dominant", "Dark Muted", "Dark Vibrant", "Legacy"};
                int checkedItem = preferences.getInt("colorVariant", 3);
                alertDialog.setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        editor.putInt("colorVariant", which);
                        editor.commit();

                        colorVariant = which;
                    }
                });

                alertDialog.setPositiveButton("OK", null);

                AlertDialog alert = alertDialog.create();
                alert.setCanceledOnTouchOutside(false);
                alert.show();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void askBulbIp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Type the Bulb IPs separated by comma");

        final EditText input = new EditText(this);
        input.setSingleLine();

        if (preferences.contains("bulbIps")) {
            input.setText(preferences.getString("bulbIps", ""));
        }

        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String mText = input.getText().toString();

                editor.putString("bulbIps", mText);
                editor.commit();

                bulbIps = mText.split(",");

                if (bulbIps[0].isEmpty()) {
                    bulbIps = new String[]{};
                }

                init();
                setTimer();
            }
        });

        builder.show();
    }

    public void init() {
        if (bulbIps.length == 0) {
            Toast.makeText(MainActivity.this, "Please register a Yeelight Bulb IP", Toast.LENGTH_SHORT).show();
            return;
        }

        closeConnections();

        for (String bulbIp : bulbIps) {
            this.startTelnet(bulbIp.trim());
        }

        isRunning = true;
    }

    public void startTelnet(String bulbIp) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try  {
                    try {
                        TelnetClient connection = new TelnetClient(bulbIp, 55443);

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

                        connections.add(connection);
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

    public void setColor(int color, TelnetClient connection, int index) {
        String hexColor = String.format("%06X", (0xFFFFFF & color));

        if (index == 0) {
            ColorDrawable cd = new ColorDrawable(Color.parseColor("#" + hexColor));
            getSupportActionBar().setBackgroundDrawable(cd);
        }

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

    @Override
    protected void onDestroy() {
        super.onDestroy();

        this.closeConnections();
    }

    private void closeConnections() {
        try {
            for (TelnetClient connection : connections) {
                connection.close();
            }

            connections.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStop() {
        this.isRunning = false;

        closeConnections();

        if (optionsMenu != null) {
            optionsMenu.findItem(R.id.play_pause).setIcon(R.drawable.baseline_play_arrow_white_24);
            invalidateOptionsMenu();
        }

        super.onStop();
    }

    public int getDominantColor(Bitmap bitmap) {
        Bitmap newBitmap = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
        final int color = newBitmap.getPixel(0, 0);
        newBitmap.recycle();
        return color;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (bulbIps.length > 0) {
            this.init();
        }
    }
}