package cn.edu.hdu.artalk2;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.renderscript.Sampler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;

import cn.edu.hdu.artalk2.utils.OkHttpManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public class LeaveMessageActivity extends AppCompatActivity {

    private static final String TAG = "message";
    private TextView lm_date, lm_time, bt_submit;
    private ImageView lm_start,bt_return;
    private ImageButton lm_mic;
    private EditText lm_et;

    //录音功能相关
    private boolean isStart = false;
    private boolean isRecorded = false;
    private MediaRecorder mr = null;
    private File dir;
    private File soundFile;
    boolean isRecording; // 录音状态
    Thread timeThread; // 记录录音时长的线程
    int timeCount; // 录音时长 计数
    final int TIME_COUNT = 0x101;

    //播放功能
    private MediaPlayer mp = null;
    private SeekBar seekBar;
    private TextView tv_CTIME;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leave_message);


        lm_date = findViewById(R.id.lm_tv1);
        lm_time = findViewById(R.id.lm_tv3);
        lm_mic = findViewById(R.id.lm_mic);
        lm_et = findViewById(R.id.lm_et);
        lm_start = findViewById(R.id.lm_start);
        bt_submit = findViewById(R.id.bt_submit);
        seekBar=findViewById(R.id.play);
        tv_CTIME=findViewById(R.id.tv_cur_time);
        bt_return=findViewById(R.id.btn_return);


        //显示时间
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd   HH:mm:ss");
        Date curDate = new Date(System.currentTimeMillis());
        lm_date.setText(simpleDateFormat.format(curDate));

//        if ("".equals(lm_et.getText().toString()))
//        Toast.makeText(getApplicationContext(), "lm_et.getText().toString()",
//                Toast.LENGTH_SHORT).show();


        //缓存声音
        dir = new File(Environment.getExternalStorageDirectory(), "sounds");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        soundFile = new File(dir, System.currentTimeMillis() + ".amr");
        if (!soundFile.exists()) {
            try {
                soundFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
//长按录音功能的实现
        lm_mic.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startRecord();
                        updateMicStatus();
                        lm_time.setText("松开停止录制");
                        isStart = true;

                        isRecording = true;
                        // 初始化录音时长记录
                        timeThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                countTime();
                            }
                        });
                        timeThread.start();

                        break;
                    case MotionEvent.ACTION_UP:
                        stopRecord();
                        //lm_time.setText("录音完毕");
                        lm_mic.setImageDrawable(getResources().getDrawable(R.drawable.leavemessage_mic_finitsh));
                        isStart = false;

                        lm_start.setVisibility(View.VISIBLE);
                        seekBar.setVisibility(View.VISIBLE);
                        tv_CTIME.setVisibility(View.VISIBLE);

                        isRecording = false;
                        isRecorded = true;
                        break;
                    default:
                        break;
                }
                return false;
            }
        });

        //进度条监听
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                //参数b表示是否为用户手动操作
                if(b){
                    mp.seekTo(i);   //用户手动调进度
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        //试听
        lm_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isStart) {
                    mp = new MediaPlayer();
                    try {
                        mp.setDataSource(soundFile.getPath());
                        mp.prepare();
                        isStart = true;

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (isStart)
                    mp.start();
                // 设置seekbar的最大值
                seekBar.setMax(mp.getDuration());
                // 创建一个线程
                Thread thread = new Thread(new MuiscThread());
                // 启动线程
                thread.start();
            }
        });
        //提交
        bt_submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if ((!isRecorded) && "".equals(lm_et.getText().toString()))
                    Toast.makeText(getApplicationContext(), "录音或文字留言不能为空", Toast.LENGTH_SHORT).show();
                else {
                    //Toast.makeText(getApplicationContext(), lm_et.getText(), Toast.LENGTH_SHORT).show();
                    //获取经纬度信息
                    //获取系统的LocationManager对象
                    LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

                    //从GPS获取最新的定位信息
                    if (ActivityCompat.checkSelfPermission(LeaveMessageActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(LeaveMessageActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            //return;
                            Toast.makeText(getApplicationContext(), "请打开应用的定位权限", Toast.LENGTH_SHORT).show();
                        }
                        else Toast.makeText(getApplicationContext(), "请打开应用的定位权限", Toast.LENGTH_SHORT).show();
                    }
                    Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    //locationUpdates(location);    //将最新的定位信息传递给创建的locationUpdates()方法中

                    if (location==null)
                        Toast.makeText(getApplicationContext(), "无法从GPS中获取当前定位信息", Toast.LENGTH_SHORT).show();

                    int l_lat= (int) location.getLatitude();
                    int l_long= (int) location.getLongitude();
                    String postUrl = "http://47.112.174.246:3389/createMessage/";

                 //  if (isRecorded){
                    RequestBody requestBody=RequestBody.create(MediaType.parse("application/octet-stream"),soundFile);//创建requestBody对象
                    MultipartBody multipartBody=new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("userId","1")
                            .addFormDataPart("Voice",String.valueOf(soundFile),requestBody)
                            .addFormDataPart("text", String.valueOf(lm_et.getText()))
                            .addFormDataPart("cx", String.valueOf(l_lat))
                            .addFormDataPart("cy", String.valueOf(l_long))
                            .build();
//
//                    Request request= new Request.Builder()
//                            .url(postUrl)//TODO
//                            .post(multipartBody)
//                            .build();

                    Callback callback=new Callback(){
                                @Override
                                public void onFailure(Call call, IOException e) {
                                    Log.i(TAG, "onFailure: 失败");
                                  //  Toast.makeText(getApplicationContext(), "失败", Toast.LENGTH_SHORT).show();
                                    Log.e("onFailure",e.toString());
                                    Log.e("onFailure",call.toString());
                                    e.printStackTrace();
                                }

                                @Override
                                public void onResponse(Call call, Response response) throws IOException {
                                    Message message=Message.obtain();
                                    message.what=1;
                                    message.obj=response.body().string();
                                    String msId = null;
                                    String jsonData = (String)message.obj;
                                    JSONObject jsonObject= null;
                                    try {
                                        jsonObject = new JSONObject(jsonData);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                    try {
                                        msId = jsonObject.get("msId").toString();
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                    Log.i(TAG, "onResponse: 成功 " + msId+" "+l_lat+" "+l_long);
                                    Intent intent=new Intent(LeaveMessageActivity.this,MapActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);//关掉所要到的界面中间的activity
                                    startActivity(intent);
//                                    Intent intent=new Intent(LeaveMessageActivity.this,ReadActivity.class);
////括号内的形式为（当前活动.this，传递给的活动.class）;
//                                    intent.putExtra("msgid",msId);
//                                    intent.putExtra("cx",l_lat);
//                                    intent.putExtra("cy",l_long);
//                                    intent.putExtra("username","1");
//
////这里调用intent的putExtra()方法，括号内的内容是（你传递数据的名称，数据）
//                                    startActivity(intent);
////启动传递目标的活动。7
                                }
                            };
                    OkHttpManager.getInstance().sendOkHttpResponse(postUrl,multipartBody, callback);


                }
            }
        });
        //返回
        bt_return.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent=new Intent(LeaveMessageActivity.this,ArScanActivity.class);
                startActivity(intent);
            }
        });


    }
    //开始录制
    private void startRecord(){

        if(mr == null){

            mr = new MediaRecorder();
            mr.setAudioSource(MediaRecorder.AudioSource.MIC);  //音频输入源
            mr.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);   //设置输出格式
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);   //设置编码格式
            mr.setOutputFile(soundFile.getAbsolutePath());
            try {
                mr.prepare();
                mr.start();  //开始录制

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        else mr.reset();
    }


    private void stopRecord(){
        if(mr != null){
            mr.stop();//停止录制
            mr.release();//资源释放
            mr = null;
        }
    }

    private final Handler mHandler = new Handler();
    private Runnable mUpdateMicStatusTimer = new Runnable() {
        public void run() {
            updateMicStatus();
        }
    };
    /**
     * 更新话筒状态
     *
     */
    private int BASE = 1;
    private int SPACE = 100;// 间隔取样时间

    private void updateMicStatus() {
        if (mr != null) {
            double ratio = (double)mr.getMaxAmplitude() /BASE;
            double db = 0;// 分贝
            if (ratio > 1)
                db = 20 * Math.log10(ratio);
            Log.d(TAG,"分贝值："+db);
            lm_mic.getDrawable().setLevel((int) (3000 + 6000 * db / 100));
            mHandler.postDelayed(mUpdateMicStatusTimer, SPACE);
        }
    }
    //释放资源
    private void releaseRecord(){
    }
    // 记录录音时长
    private void countTime() {

        while (isRecording) {

            Log.d(TAG,"正在录音");
            timeCount++;
            Message msg = Message.obtain();
            msg.what = TIME_COUNT;
            msg.obj = timeCount;
            RecordHandler.handleMessage(msg);
            try {
                timeThread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG,"结束录音");
        //timeCount = 0;
        Message msg = Message.obtain();
        msg.what = TIME_COUNT;
        msg.obj = timeCount;
        RecordHandler.sendMessage(msg);

    }
    // 格式化 录音时长为 时:分:秒
    public static String FormatMiss(int miss) {
        String hh = miss / 3600 > 9 ? miss / 3600 + "" : "0" + miss / 3600;
        String mm = (miss % 3600) / 60 > 9 ? (miss % 3600) / 60 + "" : "0" + (miss % 3600) / 60;
        String ss = (miss % 3600) % 60 > 9 ? (miss % 3600) % 60 + "" : "0" + (miss % 3600) % 60;
        return mm + ":" + ss;
    }


    private Handler RecordHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TIME_COUNT:
                    int count = (int) msg.obj;
                    Log.d(TAG,"count == " + count);
                    lm_time.setText(FormatMiss(count));
                    break;
            }
        }
    };
    private Handler PlayHandler= new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            // 将SeekBar位置设置到当前播放位置
            seekBar.setProgress(msg.what);
            //获得音乐的当前播放时间
            tv_CTIME.setText(FormatMiss(msg.what/1000+1));
        }
    };
    class MuiscThread implements Runnable {
        @Override
        //实现run方法
        public void run() {
            //判断音乐的状态，在不停止与不暂停的情况下向总线程发出信息
            while (mp != null) {
                try {
                    // 每100毫秒更新一次位置
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //发出的信息
                PlayHandler.sendEmptyMessage(mp.getCurrentPosition());
            }
        }
    }

}
