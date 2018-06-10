package com.example.liuli.openfiles;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.media.ThumbnailUtils;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ArrayAdapter<String> adapter;

    private String tfpath;
    //private ListView mShowPahtLv;
    private TextView mShowResult;
    private TextView mShownum;

    private TextView mShowcarlessacc;
    private TextView mShowcarlessrec;
    private TextView mShowcarlessnum;

    private TextView mShowcarnormacc;
    private TextView mShowcarnormrec;
    private TextView mShowcarnormnum;

    private TextView mShowcarmoreacc;
    private TextView mShowcarmorerec;
    private TextView mShowcarmorenum;

    private ImageClassifier classifier;

    private List<String> wrongFrames  = new ArrayList<String>();

    private static final int REQUEST_EXTERNAL_STORAGE=1;
    private static String[] PERMISSIONS_STORGE={"android.permission.READ_EXTERNAL_STORAGE",
                                               "android.permission.WRITE_EXTERNAL_STORAGE",
                                                  "android.permission.WRITE_MEDIA_STORAGE"};
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        verifyStoragePermissions(this);

        mShowResult = (TextView) findViewById(R.id.acc_text);
        mShownum = (TextView) findViewById(R.id.num_text) ;

        mShowcarlessacc = (TextView) findViewById(R.id.carless_acc);
        mShowcarlessrec = (TextView) findViewById(R.id.carless_rec);
        mShowcarlessnum = (TextView) findViewById(R.id.carless_num);

        mShowcarnormacc = (TextView) findViewById(R.id.carnorm_acc);
        mShowcarnormrec = (TextView) findViewById(R.id.carnorm_rec);
        mShowcarnormnum = (TextView) findViewById(R.id.carnorm_num);

        mShowcarmoreacc = (TextView) findViewById(R.id.carmore_acc);
        mShowcarmorerec = (TextView) findViewById(R.id.carmore_rec);
        mShowcarmorenum = (TextView) findViewById(R.id.carmore_num);


        try {
            //classifier = new ImageClassifierQuantizedMobileNet(this);
            classifier = new ImageClassifierFloatInception(this);
        } catch(IOException e){
            Log.e( "MainActivity","Failed to initialize an image classifier.", e);
        }
    }

    @Override
    protected void onStart(){
        super.onStart();

    }

    @Override
    protected void onResume(){
        super.onResume();
        classifyFrame(getImagePath());
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onStop(){
        super.onStop();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
    }

    @Override
    protected void onRestart(){
        super.onRestart();
    }
   // 动态申请权限
    public static void verifyStoragePermissions(Activity activity){
        try{
            int permission= ActivityCompat.checkSelfPermission(activity,"android.permission.WRITE_EXTERNAL_STORAGE");
            if(permission!= PackageManager.PERMISSION_DENIED){
                ActivityCompat.requestPermissions(activity,PERMISSIONS_STORGE,REQUEST_EXTERNAL_STORAGE);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
   }

    public static List<String> getExtSDCardPathList() {
        List<String> paths = new ArrayList<String>();
        String extFileStatus = Environment.getExternalStorageState();
        File extFile = Environment.getExternalStorageDirectory();
        //首先判断一下外置SD卡的状态，处于挂载状态才能获取的到
        if (extFileStatus.equals(Environment.MEDIA_MOUNTED)
                && extFile.exists() && extFile.isDirectory()
                && extFile.canWrite()) {
            //外置SD卡的路径
            paths.add(extFile.getAbsolutePath());
        }
        try {
            // obtain executed result of command line code of 'mount', to judge
            // whether tfCard exists by the result
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec("mount");
            InputStream is = process.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            int mountPathIndex = 1;
            while ((line = br.readLine()) != null) {
                // format of sdcard file system: vfat/fuse
                if ((!line.contains("fat") && !line.contains("fuse") && !line
                        .contains("storage"))
                        || line.contains("secure")
                        || line.contains("asec")
                        || line.contains("firmware")
                        || line.contains("shell")
                        || line.contains("obb")
                        || line.contains("legacy") || line.contains("data")) {
                    continue;
                }
                String[] parts = line.split(" ");
                int length = parts.length;
                if (mountPathIndex >= length) {
                    continue;
                }
                String mountPath = parts[mountPathIndex];
                if (!mountPath.contains("/") || mountPath.contains("data")
                        || mountPath.contains("Data")) {
                    continue;
                }
                File mountRoot = new File(mountPath);
                if (!mountRoot.exists() || !mountRoot.isDirectory()
                        || !mountRoot.canWrite()) {
                    continue;
                }
                boolean equalsToPrimarySD = mountPath.equals(extFile
                        .getAbsolutePath());
                if (equalsToPrimarySD) {
                    continue;
                }
                //扩展存储卡即TF卡或者SD卡路径
                paths.add(mountPath);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return paths;
    }
    //从sd卡获取资源
    private List<String> getImagePath(){
        List<String> dirpath = getExtSDCardPathList();
        Log.d("sd_path",dirpath.get(0));
        Log.d("tf_path",dirpath.get(1));
        tfpath = dirpath.get(1);
        List<String> imagePathList = new ArrayList<String>();
        String filepath = tfpath+ File.separator+"DCIM"+File.separator+"TEST";
        //String filepath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
        //Context context = getApplicationContext(); //获取当前上下文
        //String filepath = context.getExternalFilesDir("DCIM")+File.separator;
        //得到该路径文件夹下的所有文件
        Log.d("filepath",filepath);
        File fileAll = new File(filepath);
        boolean result = fileAll.exists();
        File[] files = fileAll.listFiles();
        for(int i = 0;i<files.length;i++){
            File file = files[i];
            if(checkIsImageFile(file.getPath())){
                imagePathList.add(file.getPath());
            }
        }
        return imagePathList;
    }

    private void classifyFrame(List<String> Frames){
        int num = 0;

        int carlessnum = 0,carlessTP = 0,carlessFP = 0;
        int carnormalnum = 0,carnormalTP = 0,carnormalFP = 0;
        int carmorenum = 0,carmoreTP = 0,carmoreFP = 0;
        mShownum.setText(Integer.toString(Frames.size()));
        Log.d("mShownum",Integer.toString(Frames.size()));
        String resultfilepath = tfpath+ File.separator+"DCIM"+File.separator+"TESTRESULT"+File.separator;
        for(int i = 0;i<Frames.size();i++){
            String imagepath = Frames.get(i);
            Bitmap bitmap = createImageThumbnail(imagepath,classifier.getImageSizeX(),classifier.getImageSizeY());
            String result = classifier.classifyFrame(bitmap);
            Log.d("Predict_result"+Integer.toString(i),result);
            String imagename = imagepath.split("/")[imagepath.split("/").length-1];

            //将数据保存到本地
            String resultname = imagename.replace(".jpg",".txt");
            Log.d("resultname",resultname);

            writeTxtToFile(result,resultfilepath,resultname);
            String label = imagename.split("_")[0];
            Log.d("label"+Integer.toString(i),label);

            switch (label){
                case "0":
                    carlessnum++;
                    Log.d("carlessnum",Integer.toString(carlessnum));
                    if(result == classifier.labelList.get(Integer.parseInt(label))){
                        carlessTP++;
                        Log.d("carlessTP",Integer.toString(carlessTP));
                    }
                    break;

                case "1":
                    carnormalnum++;
                    Log.d("carnormalnum",Integer.toString(carnormalnum));
                    if(result == classifier.labelList.get(Integer.parseInt(label))){
                        carnormalTP++;
                        Log.d("carnormalTP",Integer.toString(carnormalTP));
                    }
                    break;
                case "2":
                    carmorenum++;
                    Log.d("carmorenum",Integer.toString(carmorenum));
                    if(result == classifier.labelList.get(Integer.parseInt(label))){
                        carmoreTP++;
                        Log.d("carmoreTP",Integer.toString(carmoreTP));
                    }
                    break;
            }

            if(result != classifier.labelList.get(Integer.parseInt(label))){
                switch (result){
                    case "类别一":
                        carlessFP++;
                        break;

                    case "类别二":
                        carnormalFP++;
                        break;
                    case "类别三":
                        carmoreFP++;
                        break;
                }
            }

            if(result == classifier.labelList.get(Integer.parseInt(label))){
                num++;
            } else{
                wrongFrames.add(imagepath+"predict:"+result);
            }
            Log.d("图片数：", Integer.toString(i+1));
            Log.d("正确数：", Integer.toString(num));
        }
        float result  = (float)num/(float)Frames.size();
        mShowResult.setText(Float.toString(result));

        float carlessrec = (float)Math.round((float)carlessTP/(float)carlessnum*10000)/10000;
        float carlessacc = (float) Math.round((float)carlessTP/(float)(carlessTP+carlessFP)*10000)/10000;
        float carnormalrec = (float) Math.round((float)carnormalTP/(float)carnormalnum*10000)/10000;
        float carnormalacc = (float) Math.round((float)carnormalTP /(float)(carnormalTP+carnormalFP)*10000)/10000;
        float carmorerec = (float) Math.round((float) carmoreTP/(float)carmorenum*10000)/10000;
        float carmoreacc = (float) Math.round((float)carmoreTP/(float)(carmoreTP+carmoreFP)*10000)/10000;

        mShowcarlessacc.setText(Float.toString(carlessacc));
        mShowcarlessrec.setText(Float.toString(carlessrec));
        mShowcarlessnum.setText(Integer.toString(carlessnum));


        mShowcarnormacc.setText(Float.toString(carnormalacc));
        mShowcarnormrec.setText(Float.toString(carnormalrec));
        mShowcarnormnum.setText(Integer.toString(carnormalnum));

        mShowcarmoreacc.setText(Float.toString(carmoreacc));
        mShowcarmorerec.setText(Float.toString(carmorerec));
        mShowcarmorenum.setText(Integer.toString(carmorenum));
    }

    private Bitmap createImageThumbnail(String filePath,int newHeight,int newWidth){

        Bitmap bm = BitmapFactory.decodeFile(filePath);


        float width = bm.getWidth();
        float height = bm.getHeight();
        Log.i("old_size:","宽度是"+width+",高度是"+height);

        Matrix matrix = new Matrix();

        //计算宽高缩放率
        float scaleWidth = ((float) newWidth)/width;
        float scaleHeight = ((float) newHeight)/height;

        //缩放图片动作
        matrix.postScale(scaleWidth,scaleHeight);
        Bitmap bitmap = Bitmap.createBitmap(bm,0,0,(int)width,(int)height,matrix,true);
        Log.i("new_size:","宽度是"+bitmap.getHeight()+",高度是"+bitmap.getWidth());
        return bitmap;

    }

    private boolean checkIsImageFile(String fName){
        boolean isImageFile = false;
        //获取扩展名
        String FileEnd = fName.substring(fName.lastIndexOf(".")+1,fName.length()).toLowerCase();

        if(FileEnd.equals("jpg")||FileEnd.equals("png")||FileEnd.equals("gif")||FileEnd.equals("jpeg")||FileEnd.equals("bmp")){
            isImageFile = true;
        }
        return isImageFile;
    }

    public void save(String data,String Filepath){
        FileOutputStream out = null;
        BufferedWriter writer = null;
        try{
            out = openFileOutput(Filepath,Context.MODE_PRIVATE);
            writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(data);
        } catch(IOException e){
            e.printStackTrace();
        } finally {
            try {
                if (writer !=null){
                    writer.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }

    }




    public void writeTxtToFile(String strcontent,String filepath,String filename){
        makeFilePath(filepath,filename);

        String strFilePath = filepath+filename;
        Log.d("strFilePath",strFilePath);
        String strContent = strcontent+"\r\n";
        try{
            File file = new File(strFilePath);
            if(!file.exists()){
                Log.d("TestFile","create the file"+strFilePath);
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            RandomAccessFile raf = new RandomAccessFile(file,"rw");
            raf.seek(file.length());
            raf.write(strContent.getBytes());
            raf.close();
        }catch (Exception e){
            Log.e("TestFile","Error on write File:"+e);
        }
    }

    public File makeFilePath(String filepath,String filename){
        File file = null;
        makeRootDiretory(filepath);
        try {
            file = new File(filepath+filename);
            if(!file.exists()){
                file.createNewFile();
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        return file;

    }

    public static void makeRootDiretory(String filepath){
        File file = null;
        try{
            file = new File(filepath);
            if(!file.exists()){
                file.mkdir();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}