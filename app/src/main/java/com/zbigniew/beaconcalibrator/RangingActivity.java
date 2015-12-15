package com.zbigniew.beaconcalibrator;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.Map;

public class RangingActivity extends AppCompatActivity implements BeaconConsumer, RangeNotifier, View.OnClickListener {

    private BeaconManager mBeaconManager;
    final private int FINE_LOCATION_REQUEST = 123, WRITE_SDCARD_REQUEST = 123;
    private int counter2 = 0, pointCounter = 0;

    private int[][] dane = new int[6][150];
    private Map<Beacon, Integer> beacony;
    MediaPlayer mp;

    private EditText pointValueET;
    private TextView rssi, iteracje, log;

    private static final int ITERACJE = 149;//value=149
    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranging);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        log = (TextView)findViewById(R.id.logTextView);
        log.setMovementMethod(new ScrollingMovementMethod());

        pointValueET = (EditText) findViewById(R.id.pointValueEditText);
        rssi = (TextView) findViewById(R.id.message);
        iteracje = (TextView) findViewById(R.id.iteracjeValueTextView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_ranging, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_save:
                pointCounter = Integer.parseInt(pointValueET.getText().toString());
                return true;
            case R.id.action_start:
                pointCounter = Integer.parseInt(pointValueET.getText().toString());
                if (isBlEnabled()) {
                    initializeBeaconManager();
                } else {
                    enableBluetooth();
                    initializeBeaconManager();
                }
                return true;
            case R.id.action_pause:
                stopScanning();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBeaconServiceConnect() {
        Region region = new Region("all-beacons-region", null, null, null);
        try {
            mBeaconManager.startRangingBeaconsInRegion(region);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        mBeaconManager.setRangeNotifier(this);
    }

    @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
        runOnUiThread(new Runnable() {
            public void run() {
                iteracje.setText(String.valueOf(counter2));
                log.setText("Iteracja: "+String.valueOf(counter2)+"\n");
            }
        });
        for (final Beacon beacon : beacons) {
            if (beacon.getServiceUuid() == 0xfeaa && beacon.getBeaconTypeCode() == 0x00) {
                // This is a Eddystone-UID frame
                final Identifier namespaceId = beacon.getId1();
                final Identifier instanceId = beacon.getId2();


                switch (instanceId.toString()) {
                    case "0x506b444b4c48":
                        dane[0][counter2] = beacon.getRssi();
                        break;
                    case "0x30636169506c":
                        dane[1][counter2] = beacon.getRssi();
                        break;
                    case "0x6d767674636e":
                        dane[2][counter2] = beacon.getRssi();
                        break;
                }

                runOnUiThread(new Runnable() {
                    public void run() {
                        log.append("I see a beacon transmitting namespace id: " + namespaceId +
                                " and instance id: " + instanceId +
                                " power: " + beacon.getRssi()+"\n\n");
                        log.append("Distance: " + beacon.getDistance() +"\n\n");
                        //alertDialog.show();
                        rssi.setText(String.valueOf(beacon.getRssi()));
                        //((TextView)RangingActivity.this.findViewById(R.id.message)).setText(String.valueOf(beacon.getRssi()));
                    }
                });
            }
        }
        if(beacons.size()>0){
            if (counter2 < ITERACJE) {
                counter2++;
            } else {
                counter2 = 0;
                pointCounter++;
                runOnUiThread(new Runnable() {
                    public void run() {
                        pointValueET.setText(String.valueOf(pointCounter));
                        mp = MediaPlayer.create(getApplicationContext(), R.raw.helium);
                        mp.start();
                    }
                });

            /*// Build notification
            Notification.Builder notifBuilder = new Notification.Builder(this);
            notifBuilder.setContentTitle(getString(R.string.app_name));
            notifBuilder.setSmallIcon(R.drawable.ic_pause_white_18dp);
            notifBuilder.setContentText("Zakończono skanowanie");
            notifBuilder.setDefaults(R.raw.helium);
            notifBuilder.setAutoCancel(true);
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(0,notifBuilder.build());*/

                showEndScanningAlert(region);
            }
        }
    }

    private void showEndScanningAlert(final Region region) {
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(RangingActivity.this);

        // Setting Dialog Title
        alertDialog.setTitle("Kalibracja w tym punkcie zakończona.");

        // Setting Dialog Message
        alertDialog.setMessage("Czy kontynuować kalibrację?");

        // Setting Positive "Yes" Button
        alertDialog.setPositiveButton("YES", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                saveArrayToFile();
                try {
                    mBeaconManager.startRangingBeaconsInRegion(region);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                mp.stop();
            }
        });

        // Setting Negative "NO" Button
        alertDialog.setNegativeButton("NO", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // Write your code here to invoke NO event
                stopScanning();
                saveArrayToFile();
                dialog.cancel();
                mp.stop();
            }
        });
        try {
            mBeaconManager.stopRangingBeaconsInRegion(region);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        Handler mHandler = new Handler(getMainLooper());
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                alertDialog.show();
            }
        });
        Log.w("TAG", "Skonczono skanowanie w tym punkcie");
    }

    private void saveArrayToFile() {
        File file = new File("/sdcard/Beacon_Kalibrator/Beacon_Kalibracja_" + pointCounter + ".txt");
        File json = new File("/sdcard/Beacon_Kalibrator/Beacon_Kalibracja_" + pointCounter + ".json");



        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            FileOutputStream fos = new FileOutputStream(json);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(dane);
        } catch (Exception e) {

        }

        FileWriter writer = null;

        try {
            writer = new FileWriter(file.getAbsolutePath());
            writer.write("Punkt: " + pointCounter + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            for (int[] i : dane) {
                //writer.append("Beacon: "+i);
                for (int j = 0; j < 150; j++) {
                    if (j < 149)
                        writer.append(i[j] + ", ");
                    else
                        writer.append(i[j] + "\n\n");
                }
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(RangingActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(RangingActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(RangingActivity.this, "Musisz przyznać pozwolenie lokalizacji!", Toast.LENGTH_LONG).show();
                    }
                });
                ActivityCompat.requestPermissions(RangingActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        FINE_LOCATION_REQUEST);

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(RangingActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        FINE_LOCATION_REQUEST);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }

        if (ContextCompat.checkSelfPermission(RangingActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(RangingActivity.this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(RangingActivity.this, "Musisz przyznać pozwolenie zapisu na karcie!", Toast.LENGTH_LONG).show();
                    }
                });
                ActivityCompat.requestPermissions(RangingActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        WRITE_SDCARD_REQUEST);

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(RangingActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        WRITE_SDCARD_REQUEST);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
        enableBluetooth();

    }

    private void enableBluetooth() {

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(RangingActivity.this);

        // Setting Dialog Title
        alertDialog.setTitle("Bluetooth wyłączony...");

        // Setting Dialog Message
        alertDialog.setMessage("Czy włączyć?");

        // Setting Positive "Yes" Button
        alertDialog.setPositiveButton("YES", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {

                mBluetoothAdapter.enable();
                //initializeBeaconManager();
            }
        });

        // Setting Negative "NO" Button
        alertDialog.setNegativeButton("NO", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // Write your code here to invoke NO event
                Toast.makeText(getApplicationContext(), "Dla poprawnego działania aplikacji Bluetooth musi być włączony!", Toast.LENGTH_LONG).show();
                dialog.cancel();
            }
        });

        if (!isBlEnabled()) {
            alertDialog.show();
        }

    }

    private boolean isBlEnabled() {
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(RangingActivity.this, "Device does not support Bluetooth", Toast.LENGTH_LONG).show();
            this.finish();
            System.exit(0);
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                // Bluetooth is not enable :)
                return false;
            }
        }
        return true;
    }

    private void initializeBeaconManager() {
        mBeaconManager = BeaconManager.getInstanceForApplication(this.getApplicationContext());
        // Detect the main Eddystone-UID frame:
        //mBeaconManager.getBeaconParsers().add(new BeaconParser()
          //      .setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
        mBeaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19"));
        mBeaconManager.bind(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        stopScanning();
    }

    private void stopScanning() {
        if (mBeaconManager != null)
            mBeaconManager.unbind(this);

        runOnUiThread(new Runnable() {
            public void run() {
                //alertDialog.show();
                rssi.setText("");
                //((TextView)RangingActivity.this.findViewById(R.id.message)).setText(String.valueOf(beacon.getRssi()));
            }
        });
    }

    @Override
    public void onClick(View v) {

    }
}