package com.ibnux.launcher;

import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.ACTION_PACKAGE_ADDED;
import static android.content.Intent.ACTION_PACKAGE_REMOVED;
import static android.content.Intent.ACTION_PACKAGE_REPLACED;
import static android.content.Intent.CATEGORY_LAUNCHER;

import android.Manifest;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class Activity extends android.app.Activity implements
        Comparator<Model>,
        AdapterView.OnItemClickListener,
        AdapterView.OnItemLongClickListener{

    private final Adapter adapter = new Adapter();

    private BroadcastReceiver broadcastReceiver;
    ListView list;
    ImageView bg;

    SharedPreferences prefs;

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String TAG = "PermissionHandler";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity);

        prefs = getSharedPreferences("launcher", MODE_PRIVATE);

        list = findViewById(R.id.list);
        bg = findViewById(R.id.background);

        adapter.setPrefs(prefs);

        list.setAdapter(adapter);
        list.setOnItemClickListener(this);
        list.setOnItemLongClickListener(this);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_PACKAGE_ADDED);
        intentFilter.addAction(ACTION_PACKAGE_REMOVED);
        intentFilter.addAction(ACTION_PACKAGE_REPLACED);
        intentFilter.addDataScheme("package");

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                update();
            }
        };

        registerReceiver(broadcastReceiver, intentFilter);

        update();

    }

    private void checkPermission() {
        // Memeriksa apakah versi Android >= Marshmallow (API 23)
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 1. Cek status izin saat ini
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                // 2. Jika belum diberikan, minta izin
                requestStoragePermission();
            } else {
                // Izin sudah diberikan
                Log.d(TAG, "Izin sudah diberikan.");
                // Lanjutkan dengan operasi file Anda
                askFile();
            }
        } else {
            // Android di bawah Marshmallow (izin diberikan saat instalasi)
            Log.d(TAG, "Versi Android lama. Izin dianggap diberikan.");
            askFile();
        }
    }

    /**
     * Menampilkan dialog permintaan izin ke pengguna.
     */
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // 1. Cek status izin saat ini
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                // Izin belum diberikan, minta dari pengguna

                // Opsional: Periksa apakah perlu menampilkan justifikasi
                if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    Toast.makeText(this,
                            "Kami memerlukan izin penyimpanan untuk memuat gambar Anda.",
                            Toast.LENGTH_LONG).show();
                }

                // 2. Minta izin. Argumen pertama adalah array izin, kedua adalah kode permintaan.
                requestPermissions(
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE
                );
            } else {
                // Izin sudah diberikan
                askFile();
            }
        } else {
            // Android di bawah M: Izin diberikan saat instalasi
            askFile();
        }
    }

    /**
     * Dipanggil setelah pengguna merespons dialog permintaan izin.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Pastikan hasilnya adalah untuk permintaan yang kita kirim

            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                askFile();
            } else {
                Toast.makeText(this, "Akses penyimpanan ditolak. Tidak bisa setting wallpaper", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void askFile(){
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Pilih gambar"), 321);
        }catch (Exception e){
            setWallpaper("/sdcard/wallpaper.jpg");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 321 && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            String filePath = getPathFromUri(uri);
            Log.d("nux", filePath);
            setWallpaper(filePath);
        }
    }

    private String getPathFromUri(Uri uri) {
        String path = null;
        String[] projection = {MediaStore.Files.FileColumns.DATA};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA);
            cursor.moveToFirst();
            path = cursor.getString(columnIndex);
            cursor.close();
        }
        return path;
    }



    private void setWallpaper(String path){
        try {
            File imageFile = new File(path);
            if (imageFile.exists()) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bitmap = null;
                FileInputStream fileInputStream = null;
                fileInputStream = new FileInputStream(imageFile);
                bitmap = BitmapFactory.decodeStream(fileInputStream, null, options);
                if (bitmap != null) {
                    final WallpaperManager wm = WallpaperManager.getInstance(getApplicationContext());
                    DisplayMetrics displayMetrics = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                    int height = displayMetrics.heightPixels;
                    int width = displayMetrics.widthPixels;
//                    int dx = wm.getDesiredMinimumWidth();
//                    int dy = wm.getDesiredMinimumHeight();
//                    int w = bitmap.getWidth();
//                    int h = bitmap.getHeight();
//                    float aspectRatio = (float) w / h;
//                    int newWidth, newHeight;
//
//                    if (dx / aspectRatio > dy) {
//                        newHeight = dy;
//                        newWidth = (int) (dy * aspectRatio);
//                    } else {
//                        newWidth = dx;
//                        newHeight = (int) (dx / aspectRatio);
//                    }
                    Bitmap scaledBitmap = scaleBitmapProportionally(bitmap, width, height);
                    wm.setBitmap(scaledBitmap);
                    bg.setImageBitmap(bitmap);
                }else{
                    Log.d("NUX", "Image Null");
                }
            }

        } catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Menyesuaikan ukuran Bitmap agar menutupi seluruh area target sambil
     * mempertahankan rasio aspek. Bitmap yang dihasilkan akan memiliki dimensi
     * setidaknya sama dengan targetWidth dan targetHeight.
     */
    private Bitmap scaleBitmapProportionally(Bitmap originalImage, int targetWidth, int targetHeight) {
        // 1. Buat Bitmap kosong (Kanvas) dengan ukuran target
        Bitmap background = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);

        float originalWidth = originalImage.getWidth();
        float originalHeight = originalImage.getHeight();

        if(originalWidth == targetWidth && originalHeight == targetHeight){
            return originalImage;
        }

        Canvas canvas = new Canvas(background);

        // =========================================================
        // MODIFIKASI LOGIKA SCALING (Scale to Cover/Fill)
        // =========================================================

        // Hitung faktor skala yang diperlukan untuk mengisi lebar
        float scaleX = targetWidth / originalWidth;

        // Hitung faktor skala yang diperlukan untuk mengisi tinggi
        float scaleY = targetHeight / originalHeight;

        // Ambil faktor skala yang LEBIH BESAR (MAX) dari kedua faktor tersebut.
        // Ini memastikan bahwa sisi terpendek gambar asli akan diskala minimal
        // sama dengan batas target, sehingga menutupi seluruh kanvas.
        float scale = Math.max(scaleX, scaleY);

        // =========================================================

        // Hitung dimensi baru setelah scaling
        float scaledWidth = originalWidth * scale;
        float scaledHeight = originalHeight * scale;

        // Hitung translasi (pergeseran) untuk memposisikan di tengah (center crop)
        // Jika lebar diskala lebih besar dari target, hitung pergeseran x negatif untuk memotong tepi
        float xTranslation = (targetWidth - scaledWidth) / 2.0f;

        // Jika tinggi diskala lebih besar dari target, hitung pergeseran y negatif untuk memotong tepi
        float yTranslation = (targetHeight - scaledHeight) / 2.0f;

        Matrix transformation = new Matrix();

        // 2. Terapkan scaling (sebelum translasi agar titik 0,0 tetap di pojok kiri atas gambar asli)
        transformation.preScale(scale, scale);

        // 3. Terapkan translasi untuk memposisikan di tengah (center crop)
        transformation.postTranslate(xTranslation, yTranslation);

        // 4. Gambar Bitmap yang telah ditransformasi ke Kanvas Target
        Paint paint = new Paint();
        paint.setFilterBitmap(true); // Aktifkan filtering untuk kualitas yang lebih baik
        canvas.drawBitmap(originalImage, transformation, paint);
        Log.d("NUX", originalWidth+","+originalHeight+" | "+scale +" | "+targetWidth+","+targetHeight+" | "+xTranslation+","+yTranslation);

        return background;
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }

    @Override
    public void onBackPressed() {
    }

    @Override
    public int compare(Model lhs, Model rhs) {
        return lhs.label.compareToIgnoreCase(rhs.label);
    }


    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int index, long id) {
        Log.d("NUX", "onItemClick");
        if(Objects.equals(adapter.getItem(index).packageName, "com.mak.erot.besar")){
            besarinFont();
        }else if(Objects.equals(adapter.getItem(index).packageName, "com.mak.erot.kecil")){
            kecilinFont();
        }else if(Objects.equals(adapter.getItem(index).packageName, "com.refresh.wallpaper")){
            checkPermission();
        }else {
            try {
                startActivity(getPackageManager().getLaunchIntentForPackage(adapter.getItem(index).packageName));
            } catch (Exception e) {
                Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void besarinFont(){
        prefs.edit().putFloat("fontsize", prefs.getFloat("fontsize", 20f)+1).apply();
        adapter.notifyDataSetChanged();
    }

    private void kecilinFont(){
        prefs.edit().putFloat("fontsize", prefs.getFloat("fontsize", 20f)-1).apply();
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int index, long id) {
        Log.d("NUX", "onItemLongClick");
        if(Objects.equals(adapter.getItem(index).packageName, "com.mak.erot.besar")
        || Objects.equals(adapter.getItem(index).packageName, "com.mak.erot.kecil")
        || Objects.equals(adapter.getItem(index).packageName, "com.refresh.wallpaper")){
            return true;
        }else {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", adapter.getItem(index).packageName, null));
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, adapter.getItem(index).packageName, Toast.LENGTH_SHORT).show();
            }
        }
        return true;
    }

    private void update() {

        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent(ACTION_MAIN, null);
        intent.addCategory(CATEGORY_LAUNCHER);
        List<ResolveInfo> availableActivities = packageManager.queryIntentActivities(intent, 0);
        ArrayList<Model> models = new ArrayList<>();
        long id = 0;
        Drawable deficon = getResources().getDrawable(R.drawable.ic_app);
        for (ResolveInfo resolveInfo : availableActivities) {
            if ("com.ibnux.launcher".equalsIgnoreCase(resolveInfo.activityInfo.packageName)) continue;
            Drawable icon = deficon;
            try {
                icon = getPackageManager().getApplicationIcon(resolveInfo.activityInfo.packageName);
            } catch (PackageManager.NameNotFoundException e) {
                //ignore
            }
            models.add(new Model(
                    ++id,
                    resolveInfo.loadLabel(packageManager).toString(),
                    resolveInfo.activityInfo.packageName,
                    icon));
        }
        Collections.sort(models, this);
        models.add(new Model(
                ++id,
                "Besarin teks",
                "com.mak.erot.besar",
                getResources().getDrawable(R.drawable.ic_up)));
        models.add(new Model(
                ++id,
                "Kecilin teks",
                "com.mak.erot.kecil",
                getResources().getDrawable(R.drawable.ic_down)));
        models.add(new Model(
                ++id,
                "Set Wallpaper",
                "com.refresh.wallpaper",
                getResources().getDrawable(R.drawable.ic_wallpaper)));

        adapter.update(models);
    }

}
