package com.example.myapplication;

import static android.hardware.Sensor.TYPE_GYROSCOPE;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;


import mywebsocket.WebSocketServerSingle;
import mywebsocket.WebsocketInterface;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import android.media.ExifInterface;

import org.json.JSONObject;

public class SixDOF extends AppCompatActivity implements SensorEventListener{
    SensorManager mSensorManager=null;
    Sensor mAccelerometer;
    Sensor mGyroscope;

    String[] data= {"hello", "world"};

    private long lastTimestamp = 0;
    private float[] rotationMatrix = new float[9];
    private float[] orientationAngles = new float[3];
    private float[] translation = {0, 0, 0};

    // Complementary filter alpha value (adjust as needed for filtering)
    private static final float ALPHA = 0.1f;
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == mAccelerometer) {
            // Get the accelerometer values and update the rotation matrix
            System.arraycopy(event.values, 0, orientationAngles, 0, 3);
        } else if (event.sensor == mGyroscope) {
            if (lastTimestamp != 0) {
                // Calculate the rotation angles using gyroscope data
                float dt = (event.timestamp - lastTimestamp) * 1.0e-9f; // Convert timestamp to seconds
                float axisX = event.values[0];
                float axisY = event.values[1];
                float axisZ = event.values[2];

                float omegaMagnitude = (float) Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
                if (omegaMagnitude > 1e-5) {
                    axisX /= omegaMagnitude;
                    axisY /= omegaMagnitude;
                    axisZ /= omegaMagnitude;
                }

                float thetaOverTwo = omegaMagnitude * dt / 2.0f;
                float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
                float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);
                float deltaRotationX = sinThetaOverTwo * axisX;
                float deltaRotationY = sinThetaOverTwo * axisY;
                float deltaRotationZ = sinThetaOverTwo * axisZ;
                float deltaRotationW = cosThetaOverTwo;

                float[] deltaRotationMatrix = new float[9];
                deltaRotationMatrix[0] = 1 - 2 * deltaRotationY * deltaRotationY - 2 * deltaRotationZ * deltaRotationZ;
                deltaRotationMatrix[1] = 2 * deltaRotationX * deltaRotationY - 2 * deltaRotationW * deltaRotationZ;
                deltaRotationMatrix[2] = 2 * deltaRotationX * deltaRotationZ + 2 * deltaRotationW * deltaRotationY;
                deltaRotationMatrix[3] = 2 * deltaRotationX * deltaRotationY + 2 * deltaRotationW * deltaRotationZ;
                deltaRotationMatrix[4] = 1 - 2 * deltaRotationX * deltaRotationX - 2 * deltaRotationZ * deltaRotationZ;
                deltaRotationMatrix[5] = 2 * deltaRotationY * deltaRotationZ - 2 * deltaRotationW * deltaRotationX;
                deltaRotationMatrix[6] = 2 * deltaRotationX * deltaRotationZ - 2 * deltaRotationW * deltaRotationY;
                deltaRotationMatrix[7] = 2 * deltaRotationY * deltaRotationZ + 2 * deltaRotationW * deltaRotationX;
                deltaRotationMatrix[8] = 1 - 2 * deltaRotationX * deltaRotationX - 2 * deltaRotationY * deltaRotationY;

                // Update the rotation matrix using a complementary filter
                for (int i = 0; i < 9; i++) {
                    rotationMatrix[i] = ALPHA * deltaRotationMatrix[i] + (1 - ALPHA) * rotationMatrix[i];
                }

                // Update the translation using the rotation matrix and accelerometer data
                float x = rotationMatrix[0] * orientationAngles[0] + rotationMatrix[1] * orientationAngles[1] + rotationMatrix[2] * orientationAngles[2];
                float y = rotationMatrix[3] * orientationAngles[0] + rotationMatrix[4] * orientationAngles[1] + rotationMatrix[5] * orientationAngles[2];
                float z = rotationMatrix[6] * orientationAngles[0] + rotationMatrix[7] * orientationAngles[1] + rotationMatrix[8] * orientationAngles[2];

                // Update the translation values
                translation[0] += x * dt;
                translation[1] += y * dt;
                translation[2] += z * dt;

//                float x=translation[0], y=translation[1], z=translation[2];
                data[0]="X: " + String.format("%.2f", x)  + "; Y: " +String.format("%.2f", y)+ "; Z: " + String.format("%.2f", z);
                String txt=data[0]+"\n"+data[1];
                show_txt(txt);
                if(cbg.isChecked()){
//                    doSend("xy="+x+","+y);
                    String resp="xy="+x+","+y;
                    try {
                        JSONObject jsr = new JSONObject();
                        jsr.put("type", "gyro");
                        jsr.put("x", x);
                        jsr.put("y", y);
                        resp=jsr.toString();
                    }catch(Exception e) {
                    }
                    sendJSON(resp);

                }
            }
//            float x=translation[0], y=translation[1], z=translation[2];
//            data[1]="X: " + String.format("%.4f", x)  + "; Y: " +String.format("%.4f", y)+ "; Z: " + String.format("%.4f", z);
//            String txt=data[0]+"\n"+data[1];
//            show_txt(txt);

            lastTimestamp = event.timestamp;
        }
    }




    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }


    public static String TAG=SixDOF.class.getSimpleName();

    String connection_mode=null;  //server or client
    String serverIP=null;
    String localIP=null;

    TextView tv=null;



    TextView display;
    TextView tv_ip;

    Button bxp, bxm, byp, bym;

    CheckBox cb, cbg;

    SocketClient socketClient;
    WebSocketServerSingle ws;
    private final int PORT=8080;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_six_dof);
        if(getSupportActionBar()!=null){
            getSupportActionBar().hide();
        }
        connection_mode=getIntent().getStringExtra("mode");
        serverIP=getIntent().getStringExtra("serverIP");
//        Toast.makeText(this, "connecting ...\n"+serverIP, Toast.LENGTH_SHORT).show();


        tv=(TextView) findViewById(R.id.textView);
        display=(TextView) findViewById(R.id.textView2);
        tv_ip=(TextView) findViewById(R.id.textView3);

        localIP=getIP();
        tv_ip.setText("local ip="+localIP+":"+PORT);

        if(connection_mode.equals("client")) {
            socketClient = new SocketClient(serverIP, PORT);
        }else{
            createServer();
        }

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscope = mSensorManager.getDefaultSensor(TYPE_GYROSCOPE);

        bxp=(Button) findViewById(R.id.button8);
        bxm=(Button) findViewById(R.id.button9);
        byp=(Button) findViewById(R.id.button6);
        bym=(Button) findViewById(R.id.button7);

        cb = (CheckBox) findViewById(R.id.checkBox);
        cbg=(CheckBox) findViewById(R.id.checkBox3);

        View.OnTouchListener touchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {

                Button b=(Button)view;
                String name=b.getText().toString();
                doSend(name);

                return false;
            }
        };

        bxp.setOnTouchListener(touchListener);
        bxm.setOnTouchListener(touchListener);
        byp.setOnTouchListener(touchListener);
        bym.setOnTouchListener(touchListener);


    }
    private void createServer(){
        try {
            new Thread() {
                public void run() {
                    try {
                        System.out.println("Local IP="+localIP);
                        ws = new WebSocketServerSingle(new websocketlistener(), PORT);
                        ws.start();
                        System.out.println("websocket started");
                    }catch(Exception e) {
                        show_connection_status(false);
                        e.printStackTrace();
                    }
                }
            }.start();
        }catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    private String getIP(){
        Context context = this.getApplicationContext();
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        return ip;
    }


    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        if(socketClient!=null){
            socketClient.close();
        }
        if(ws!=null){
            ws.close();
        }
        super.onDestroy();
    }



    public void show_connection_status(boolean status){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tv.setText("connection="+status);
            }
        });
    }

    public void show_txt(final String txt){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                display.setText(txt);
            }
        });
    }


    public void clicked_up(View v){
        doSend("up");
    }
    public void clicked_down(View v){
        doSend("down");
    }
    public void clicked_open(View v){
        if(!cb.isChecked()){
            Toast.makeText(SixDOF.this, "click enable ctrl", Toast.LENGTH_SHORT).show();
        }

        doSend("open");
    }
    public void clicked_close(View v){
        if(!cb.isChecked()){
            Toast.makeText(SixDOF.this, "click enable ctrl", Toast.LENGTH_SHORT).show();
        }
        doSend("close");
    }

    public void clicked_xplus(View v){
//        doSend("x+");
    }
    public void clicked_xminus(View v){
//        doSend("x-");
    }
    public void clicked_yplus(View v){
//        doSend("y+");
    }
    public void clicked_yminus(View v){
//        doSend("y-");
    }

    public void doSend(final String msg){

        String resp=msg;
        try {
            JSONObject jsr = new JSONObject();
            jsr.put("type", "click");
            jsr.put("data",msg);
            resp=jsr.toString();
        }catch(Exception e) {
        }

        sendJSON(resp);
    }

    public void sendJSON(final String jsonmsg){

        if(!cb.isChecked()){
            return;
        }

        if(connection_mode.equals("client") && socketClient != null) {
            socketClient.send(jsonmsg);
        }else if(connection_mode.equals("server") && ws!=null) {
            Log.i(TAG, "sending msg ="+jsonmsg);

            new Thread() {
                public void run() {
                    try {
                        ws.send_msg(jsonmsg);
                    }catch (Exception ex){
                        show_connection_status(false);
                    }
                }
            }.start();
        }else {
            Log.i(TAG, "sending failed. __not connected__");
        }
    }


    class SocketClient{
        Socket client;
        PrintWriter writer;
        boolean isconnected=false;
        public SocketClient(String serverIP, int PORT){
            new Thread(){
                public void run(){
                    Log.i(TAG, "connecting ="+serverIP);
                    try{
                        client=new Socket(serverIP, PORT);
                        writer=new PrintWriter(new OutputStreamWriter(client.getOutputStream()));
                        isconnected=true;
                    }catch (Exception ex){
                        ex.printStackTrace();
                        isconnected=false;
                    }
//                Toast.makeText(SixDOF.this, "connecting status="+isconnected, Toast.LENGTH_SHORT).show();
                    show_connection_status(isconnected);
                }
            }.start();
        }
        public void send(String msg){
            if(!isconnected){
                return;
            }

            new Thread(){
                public void run(){
                    try{
//                    Log.i(TAG, "sending="+msg);
                        writer.println(msg);
                        writer.flush();
                    }catch (Exception ex){
                        isconnected=false;
                        show_connection_status(isconnected);
                        ex.printStackTrace();
                    }
                }
            }.start();
        }

        public void close(){
            try{
                client.close();
            }catch (Exception ex){

            }
        }


    }

    class websocketlistener implements WebsocketInterface {
        @Override
        public void onopen(String remote) {
            System.out.println("onopen=" + remote);
            show_connection_status(true);
        }


        @Override
        public void onmessage(String msg) {
            System.out.println("onmessage=" + msg);
            if(msg.startsWith("echo=")){
               try{
                   msg=msg.replace("echo=","from server=");
                    boolean s=ws.send_msg(msg);
                    System.out.println("sent="+s);
                }catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

        }

        @Override
        public void onclose() {
            System.out.println("connection closed");
            show_connection_status(false);
        }
    }

}