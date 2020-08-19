package com.example.activitydetection;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.speech.tts.TextToSpeech;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.cardview.widget.CardView;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements SensorEventListener, TextToSpeech.OnInitListener {

    private static final int N_SAMPLES = 100;
    private static int prevIdx = -1;

    private static List<Float> ax;
    private static List<Float> ay;
    private static List<Float> az;

    private static List<Float> lx;
    private static List<Float> ly;
    private static List<Float> lz;

    private static List<Float> gx;
    private static List<Float> gy;
    private static List<Float> gz;

    private static List<Float> ma;
    private static List<Float> ml;
    private static List<Float> mg;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private Sensor mLinearAcceleration;


    private TextView downstairsTextView;
    private TextView joggingTextView;
    private TextView sittingTextView;
    private TextView standingTextView;
    private TextView upstairsTextView;
    private TextView walkingTextView;
    private TextView Status;
    private TableRow downstairsTableRow;
    private TableRow joggingTableRow;
    private TableRow sittingTableRow;
    private TableRow standingTableRow;
    private TableRow upstairsTableRow;
    private TableRow walkingTableRow;

    private TextToSpeech textToSpeech;
    private float[] results;
    private TFClassifier classifier;

//    private String[] labels = {"Downstairs", "Jogging", "Sitting", "Standing", "Upstairs", "Walking"};
    private String[] labels = {"Sitting", "Standing", "Walking"};

    private TextView ipText, portText;
    private float[] eventData;

    private Button cb;
    public Socket client;
    public boolean connected;
    public static final int SENSOR_FREQ = 15;
    public static final int DATA_SIZE = 3; //3+1+1+4+4
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        ax = new ArrayList<>(); ay = new ArrayList<>(); az = new ArrayList<>();
        lx = new ArrayList<>(); ly = new ArrayList<>(); lz = new ArrayList<>();
        gx = new ArrayList<>(); gy = new ArrayList<>(); gz = new ArrayList<>();
        ma = new ArrayList<>(); ml = new ArrayList<>(); mg = new ArrayList<>();

        ipText = findViewById(R.id.ipAddr);
        portText = findViewById(R.id.portText);
        cb = findViewById(R.id.connect_button);
//        downstairsTextView = (TextView) findViewById(R.id.downstairs_prob);
        joggingTextView = (TextView) findViewById(R.id.jogging_prob);
        sittingTextView = (TextView) findViewById(R.id.sitting_prob);
        standingTextView = (TextView) findViewById(R.id.standing_prob);
//        upstairsTextView = (TextView) findViewById(R.id.upstairs_prob);
        walkingTextView = (TextView) findViewById(R.id.walking_prob);
        Status = (TextView) findViewById(R.id.status);
//        downstairsTableRow = (TableRow) findViewById(R.id.downstairs_row);
        joggingTableRow = (TableRow) findViewById(R.id.jogging_row);
        sittingTableRow = (TableRow) findViewById(R.id.sitting_row);
        standingTableRow = (TableRow) findViewById(R.id.standing_row);
//        upstairsTableRow = (TableRow) findViewById(R.id.upstairs_row);
        walkingTableRow = (TableRow) findViewById(R.id.walking_row);

        eventData = new float[DATA_SIZE];
        connected = false;

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer , SensorManager.SENSOR_DELAY_FASTEST);

        mLinearAcceleration = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mSensorManager.registerListener(this, mLinearAcceleration , SensorManager.SENSOR_DELAY_FASTEST);

        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorManager.registerListener(this, mGyroscope , SensorManager.SENSOR_DELAY_FASTEST);

        classifier = new TFClassifier(getApplicationContext());

        textToSpeech = new TextToSpeech(this, this);
        textToSpeech.setLanguage(Locale.US);

    }

    @Override
    public void onInit(int status) {
        Timer timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (results == null || results.length == 0) {
                    return;
                }
                float max = -1;
                int idx = -1;
                for (int i = 0; i < results.length; i++) {
                    if (results[i] > max) {
                        idx = i;
                        max = results[i];
                    }
                }

                if(max > 0.50 && idx != prevIdx) {
                    textToSpeech.speak(labels[idx], TextToSpeech.QUEUE_ADD, null,
                            Integer.toString(new Random().nextInt()));
                    Status.setText(labels[idx]);
                    prevIdx = idx;
                }

            }
        }, 1000, 3000);

    }

    protected void onResume() {
        super.onResume();
        getSensorManager().registerListener(this, getSensorManager().getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
        getSensorManager().registerListener(this, getSensorManager().getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_FASTEST);
        getSensorManager().registerListener(this, getSensorManager().getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        activityPrediction();

        Sensor sensor = event.sensor;
        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            ax.add(event.values[0]);
            ay.add(event.values[1]);
            az.add(event.values[2]);

        } else if (sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            lx.add(event.values[0]);
            ly.add(event.values[1]);
            lz.add(event.values[2]);

        } else if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gx.add(event.values[0]);
            gy.add(event.values[1]);
            gz.add(event.values[2]);

        }
    }
    class StartClient implements Runnable {

        @Override
        public void run() {
            String ip_addr = ipText.getText().toString();
            int Port = Integer.parseInt(portText.getText().toString());

            try {
                client = new Socket(ip_addr, Port);
                //client = new Socket("192.168.2.19", 31007);
                connected = true;
            } catch (IOException e) {
                //cb.setText("Failed!");
                e.printStackTrace();
            }
        }
    }

    class SendData implements Runnable {
        @Override
        public void run() {

//            sendAsString();
            sendAsBytes();
        }

        void sendAsString() {
            try {
                PrintWriter writer = new PrintWriter(client.getOutputStream());
                for (int i = 0; i < DATA_SIZE; i++) {
                    writer.print(eventData[i]);
                }
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
                connected = false;
            }
        }

        void sendAsBytes() {
            try {
                byte byteArray[] = new byte[4 * DATA_SIZE];
                ByteBuffer bbuffer = ByteBuffer.wrap(byteArray);

                FloatBuffer buffer = bbuffer.asFloatBuffer();
                buffer.put(eventData);

                client.getOutputStream().write(byteArray, 0, 4 * DATA_SIZE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Connect to a TCP server
     */
    public void connectToSever(View view) {
        Thread start_client = new Thread(new StartClient());
        start_client.start();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void activityPrediction() {

        List<Float> data = new ArrayList<>();

        if (ax.size() >= N_SAMPLES && ay.size() >= N_SAMPLES && az.size() >= N_SAMPLES
                && lx.size() >= N_SAMPLES && ly.size() >= N_SAMPLES && lz.size() >= N_SAMPLES
                && gx.size() >= N_SAMPLES && gy.size() >= N_SAMPLES && gz.size() >= N_SAMPLES
        ) {
            double maValue; double mgValue; double mlValue;

            for( int i = 0; i < N_SAMPLES ; i++ ) {
                maValue = Math.sqrt(Math.pow(ax.get(i), 2) + Math.pow(ay.get(i), 2) + Math.pow(az.get(i), 2));
                mlValue = Math.sqrt(Math.pow(lx.get(i), 2) + Math.pow(ly.get(i), 2) + Math.pow(lz.get(i), 2));
                mgValue = Math.sqrt(Math.pow(gx.get(i), 2) + Math.pow(gy.get(i), 2) + Math.pow(gz.get(i), 2));

                ma.add((float)maValue);
                ml.add((float)mlValue);
                mg.add((float)mgValue);
            }

            data.addAll(ax.subList(0, N_SAMPLES));
            data.addAll(ay.subList(0, N_SAMPLES));
            data.addAll(az.subList(0, N_SAMPLES));

            data.addAll(gx.subList(0, N_SAMPLES));
            data.addAll(gy.subList(0, N_SAMPLES));
            data.addAll(gz.subList(0, N_SAMPLES));

            data.addAll(lx.subList(0, N_SAMPLES));
            data.addAll(ly.subList(0, N_SAMPLES));
            data.addAll(lz.subList(0, N_SAMPLES));


            data.addAll(ma.subList(0, N_SAMPLES));
            data.addAll(ml.subList(0, N_SAMPLES));
            data.addAll(mg.subList(0, N_SAMPLES));

            results = classifier.predictProbabilities(toFloatArray(data));

            float max = -1;
            int idx = -1;
            for (int i = 0; i < results.length; i++) {
                if (results[i] > max) {
                    idx = i;
                    max = results[i];
                }
            }
            eventData[0] = results[0];
            eventData[1] = results[1];
            eventData[2] = results[2];

            if (connected) {
                cb.setText("Connected!");
                Thread send_data = new Thread(new SendData());
                send_data.start();
            }
            else {
                cb.setText("Connect");
            }
            setProbabilities();
            setRowsColor(idx);

            ax.clear(); ay.clear(); az.clear();
            lx.clear(); ly.clear(); lz.clear();
            gx.clear(); gy.clear(); gz.clear();
            ma.clear(); ml.clear(); mg.clear();

        }
    }

    private void setProbabilities() {
//        downstairsTextView.setText(Float.toString(round(results[0], 2)));
//        joggingTextView.setText(Float.toString(round(results[0], 2)));
        sittingTextView.setText(Float.toString(round(results[0], 2)));
        standingTextView.setText(Float.toString(round(results[1], 2)));
//        upstairsTextView.setText(Float.toString(round(results[4], 2)));
        walkingTextView.setText(Float.toString(round(results[2], 2)));
    }

    private void setRowsColor(int idx) {
//        bikingTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorTransparent, null));
//        downstairsTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorTransparent, null));
//        joggingTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorTransparent, null));
        sittingTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorTransparent, null));
        standingTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorTransparent, null));
//        upstairsTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorTransparent, null));
        walkingTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorTransparent, null));

        if(idx == 0)
//            downstairsTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorBlue, null));
//        else if (idx == 1)
//            joggingTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorBlue, null));
//        else if (idx == 1)
            sittingTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorBlue, null));
        else if (idx == 1)
            standingTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorBlue, null));
//        else if (idx == 4)
//            upstairsTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorBlue, null));
        else if (idx == 2)
            walkingTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorBlue, null));
    }

    private float[] toFloatArray(List<Float> list) {
        int i = 0;
        float[] array = new float[list.size()];

        for (Float f : list) {
            array[i++] = (f != null ? f : Float.NaN);
        }
        return array;
    }

    private static float round(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

    private SensorManager getSensorManager() {
        return (SensorManager) getSystemService(SENSOR_SERVICE);
    }

}
