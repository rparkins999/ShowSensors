/*
 * Copyright © 2021. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 *
 * This is a simple app to identify all the sensors on the device and
 * monitor their values.
 */

package uk.co.yahoo.p1rpp.showsensors;

import static android.content.pm.PackageManager.*;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log; // used for debugging
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity
    implements SensorEventListener, OnNmeaMessageListener, LocationListener {

    private class SensorView extends LinearLayout {

        public String[] coordinates = null;
        public long lastNanos = 0;
        public String name;
        public int other = -1;
        public int type;
        public String units = "";

        public SensorView(Context context) {
            super(context);
        }

        public void cancel() {
            removeAllViews();
            TextView tv = new TextView(ac);
            tv.setText(getString(R.string.notdetected, name));
            addView(tv);
        }

        public void lineBreak(String s1, String s2) {
            removeAllViews();
            TextView tv1 = new TextView(ac);
            TextView tv2 = new TextView(ac);
            float w = tv1.getPaint().measureText(s1)
                + tv2.getPaint().measureText(s2);
            if (m_displayWidth > (int) w) {
                setOrientation(LinearLayout.HORIZONTAL);
            } else {
                setOrientation(LinearLayout.VERTICAL);
                int numberWidth = (int) (tv2.getPaint().measureText("000"));
                tv2.setPadding(numberWidth, 0, 0, 0);
            }
            tv1.setText(s1);
            tv2.setText(s2);
            addView(tv1);
            addView(tv2);
        }

        public void update(float[] values) {
            String s1 = getString(R.string.units, name, units);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.length; ++i) {
                float f = values[i];
                if (coordinates != null) {
                    sb.append(coordinates[i]);
                } else if (i > 0) {
                    sb.append(", ");
                }
                sb.append(f);
            }
            lineBreak(s1, sb.toString());
            if (other != -1) {
                for (SensorView sv : m_sensorViews) {
                    if (sv.type == other) {
                        sv.cancel();
                    }
                }
            }
        }
    }

    private Activity ac;
    private final Runnable m_cancel_significant_motion = new Runnable() {
        public void run() {
            for (SensorView sv : m_sensorViews) {
                if (sv.type == Sensor.TYPE_SIGNIFICANT_MOTION) {
                    sv.cancel();
                }
            }
        }
    };
    private int m_displayWidth;
    private SensorView m_gpsSensor;
    private SensorView m_gpsNmea;
    private final Handler m_handler = new Handler();
    private LocationManager m_locationManager;
    private SensorManager m_sensorManager;
    private ArrayList<SensorView> m_sensorViews;
    private final static int UPDATE_MILLIS = 1000;
    private final static int UPDATE_MICROS = UPDATE_MILLIS * 1000;
    private final static long UPDATE_NANOS = UPDATE_MICROS * (long)1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ac = this;
        m_locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
    }

    @SuppressLint({"SetTextI18n", "MissingPermission"})
    @SuppressWarnings("deprecation")
    @Override
    protected void onResume() {
        super.onResume();
        LinearLayout topLayout = findViewById(R.id.genericlayout);
        topLayout.removeAllViews();
        m_sensorViews = new ArrayList<>();
        Resources resources = getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        m_displayWidth = metrics.widthPixels;
        TextView tv;
        try
        {
            PackageManager pm = getPackageManager();
            PackageInfo pi = pm.getPackageInfo(getPackageName (), 0);
            String s = getString(R.string.app_name) + " " + pi.versionName
                + " built " + getString(R.string.build_time);
            tv = new TextView(this);
            tv.setText(s);
            topLayout.addView(tv);
        } catch (NameNotFoundException ignore) {}
        m_sensorManager =
            (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (m_sensorManager == null) {
            tv = new TextView(this);
            tv.setText(R.string.nosensormanager);
            topLayout.addView(tv);
            return;
        }
        List<Sensor> sensors = m_sensorManager.getSensorList(Sensor.TYPE_ALL);
        SensorView sv;
        for (Sensor sensor : sensors) {
            sv = new SensorView(this);
            sv.name = sensor.getName();
            /* debugging
            String tag = "ShowSensors";
            Log.d(tag, sensor.toString());
            switch (sensor.getReportingMode()) {
                case Sensor.REPORTING_MODE_CONTINUOUS:
                    Log.d(tag, "REPORTING_MODE_CONTINUOUS");
                    break;
                case Sensor.REPORTING_MODE_ON_CHANGE:
                    Log.d(tag, "REPORTING_MODE_ON_CHANGE");
                    break;
                case Sensor.REPORTING_MODE_ONE_SHOT:
                    Log.d(tag, "REPORTING_MODE_ONE_SHOT");
                    break;
                case Sensor.REPORTING_MODE_SPECIAL_TRIGGER:
                    Log.d(tag, "REPORTING_MODE_SPECIAL_TRIGGER");
                    break;
            }
            Log.d(tag, "TypeString=" + sensor.getStringType());
            Log.d(tag, "RequiredPermission=");
            // */
            switch (sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                case Sensor.TYPE_GRAVITY:
                case Sensor.TYPE_LINEAR_ACCELERATION:
                    sv.units = getString(R.string.ms2);
                    sv.coordinates = resources.getStringArray(R.array.xyz);
                    break;
                case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
                    continue; // we monitor TYPE_ACCELEROMETER instead
                case Sensor.TYPE_AMBIENT_TEMPERATURE:
                    sv.units = "°C";
                    break;
                case Sensor.TYPE_GAME_ROTATION_VECTOR:
                    continue; // we monitor TYPE_ROTATION_VECTOR instead
                case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR:
                case Sensor.TYPE_ROTATION_VECTOR:
                    sv.units = getString(R.string.radians);
                    sv.coordinates = resources.getStringArray(R.array.rotation);
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    sv.units = getString(R.string.rps);
                    sv.coordinates = resources.getStringArray(R.array.xyz);
                    break;
                case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                    continue; // we monitor TYPE_GYROSCOPE instead
                case Sensor.TYPE_HEART_BEAT:
                    sv.units = getString(R.string.heartbeat);
                    break;
                case Sensor.TYPE_HEART_RATE:
                    sv.units = getString(R.string.heartrate);
                    break;
                case Sensor.TYPE_HINGE_ANGLE:
                    sv.units = getString(R.string.angle);
                    break;
                case Sensor.TYPE_LIGHT:
                case 65601: // Samsung private light sensor
                case 65578: // Samsung private light IR sensor
                case 65587: // Samsung private light CCT sensor
                    sv.units = getString(R.string.lux);
                    break;
                case Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT:
                    sv.units = getString(R.string.onoffbody);
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    sv.units = getString(R.string.magnetic);
                    sv.coordinates = resources.getStringArray(R.array.xyz);
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                    continue; // we monitor TYPE_MAGNETIC_FIELD instead
                case Sensor.TYPE_MOTION_DETECT:
                    sv.coordinates = resources.getStringArray(R.array.detected);
                    sv.other = Sensor.TYPE_STATIONARY_DETECT;
                    break;
                case Sensor.TYPE_ORIENTATION:
                    sv.units = getString(R.string.degrees);
                    sv.coordinates = resources.getStringArray(R.array.orientation);
                    break;
                case Sensor.TYPE_POSE_6DOF:
                    continue; // not monitored for now
                case Sensor.TYPE_PRESSURE:
                    sv.units = getString(R.string.millibar);
                    break;
                case Sensor.TYPE_PROXIMITY:
                    sv.units = "cm";
                    break;
                case Sensor.TYPE_RELATIVE_HUMIDITY:
                    sv.units = "%";
                    break;
                case Sensor.TYPE_SIGNIFICANT_MOTION:
                    sv.coordinates = resources.getStringArray(R.array.detected);
                    break;
                case Sensor.TYPE_STATIONARY_DETECT:
                    sv.coordinates = resources.getStringArray(R.array.detected);
                    sv.other = Sensor.TYPE_MOTION_DETECT;
                    break;
                case Sensor.TYPE_STEP_COUNTER:
                    sv.units = getString(R.string.steps);
                    break;
                case Sensor.TYPE_STEP_DETECTOR:
                    continue; // we monitor TYPE_STEP_COUNTER instead
            }
            switch (sensor.getReportingMode()) {
                case Sensor.REPORTING_MODE_CONTINUOUS:
                case Sensor.REPORTING_MODE_ON_CHANGE:
                    if (!m_sensorManager.registerListener(
                        this, sensor, UPDATE_MICROS, UPDATE_MICROS))
                    {
                        sv.lineBreak(sv.name + ": ", getString(R.string.permissiondenied));
                    }
                    break;
                case Sensor.REPORTING_MODE_ONE_SHOT:
                    {
                        SensorView sv1 = sv;
                        if (!m_sensorManager.requestTriggerSensor(
                            new TriggerEventListener() {
                                @Override
                                public void onTrigger(TriggerEvent event) {
                                    m_handler.removeCallbacks(m_cancel_significant_motion);
                                    m_handler.postDelayed(m_cancel_significant_motion,
                                        5 * UPDATE_MILLIS);
                                    sv1.update(event.values);
                                    m_sensorManager.requestTriggerSensor(
                                        this, sensor);
                                }
                            }, sensor)) {
                            sv.lineBreak(sv.name + ": ",
                                getString(R.string.permissiondenied));
                        }
                    }
                    break;
                case Sensor.REPORTING_MODE_SPECIAL_TRIGGER:
                    // This is the step detector, which we ignore because
                    // we watch the step counter
                    continue;
            }
            topLayout.addView(sv);
            m_sensorViews.add(sv);
        }
        if (   (m_locationManager != null)
            && (m_locationManager.getProviders(true).contains("gps")))
        {
            m_gpsSensor = new SensorView(this);
            try {
                m_locationManager.requestLocationUpdates(
                    "gps", UPDATE_MILLIS, 0F, this);
                m_gpsSensor.lineBreak("gps: ", getString(R.string.nomessages));
            } catch (SecurityException ignored) {
                m_gpsSensor.lineBreak("gps: ", getString(R.string.permissiondenied));
            }
            topLayout.addView(m_gpsSensor);
            m_gpsNmea = new SensorView(this);
            try {
                m_locationManager.addNmeaListener(this, m_handler);
                m_gpsNmea.lineBreak("gps-NMEA: ", getString(R.string.nomessages));
            } catch (Exception ignore) {
                m_gpsNmea.lineBreak("gps-NMEA: ", getString(R.string.permissiondenied));
            }
            topLayout.addView(m_gpsNmea);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        for (SensorView sv : m_sensorViews) {
            if (sv.name.equals(event.sensor.getName())) {
                if (event.timestamp > sv.lastNanos + UPDATE_NANOS) {
                    sv.lastNanos = event.timestamp;
                    sv.update(event.values);
                }
                break;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onLocationChanged(@NonNull Location location) {
        String degrees = " " + getString(R.string.degrees);
        StringBuilder sb = new StringBuilder();
        float lat = (float)location.getLatitude();
        if (lat >= 0) {
            sb.append(lat).append(degrees).append(" N,");
        } else {
            sb.append(-lat).append(degrees).append(" S,");
        }
        float lon = (float)location.getLongitude();
        if (lon >= 0) {
            sb.append(lon).append(degrees).append(" E,");
        } else {
            sb.append(-lon).append(degrees).append(" W,");
        }
        if (location.hasAltitude()) {
            float alt = (float)location.getAltitude();
            sb.append(", ").append(alt).append(getString(R.string.altitude));
        }
        m_gpsSensor.lineBreak("gps: ", sb.toString());
    }

    @Override
    public void onNmeaMessage(String message, long timestamp) {
        long nanos = timestamp * 1000000;
        if (nanos > m_gpsNmea.lastNanos + UPDATE_NANOS) {
            m_gpsNmea.lastNanos = nanos;
            m_gpsNmea.lineBreak("gps-NMEA: ", message);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        m_handler.removeCallbacks(m_cancel_significant_motion);
        if (m_sensorManager != null) {
            m_sensorManager.unregisterListener(this);
        }
        if (m_locationManager != null) {
            m_locationManager.removeUpdates(this);
            m_locationManager.removeNmeaListener(this);
        }
    }
}
