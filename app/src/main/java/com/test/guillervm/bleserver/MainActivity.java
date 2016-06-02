package com.test.guillervm.bleserver;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.ParcelUuid;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;


public class MainActivity extends ActionBarActivity {
    /*
        For advertisement publishing is necessary API level 21.
     */

    private final int REQUEST_ENABLE_BT = 123;
    private static final UUID UUID_SERVICE = java.util.UUID.fromString("0000181C-0000-1000-8000-00805F9B34FB");
    private static final UUID UUID_CHARACTERISTIC = java.util.UUID.fromString("00002a19-0000-1000-8000-00805F9B34FB"); //BatteryLevel

    private static final String TAG = "BluetoothInfo";

    private boolean ready = false;
    private int connections = 0;

    private TextView textNumberConnections;
    private TextView textLastDevice;
    private Switch serviceEnableSwitch;
    private ScrollView logScroll;
    private TextView logText;

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private AdvertiseCallback callback;
    private BluetoothGattServer bluetoothGattServer;
    private BluetoothGattServerCallback serverCallback;
    private BluetoothGattService service;
    private BluetoothGattCharacteristic characteristic;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set interface.
        setContentView(R.layout.activity_main);

        serviceEnableSwitch = (Switch) findViewById(R.id.service_enable_switch);

        textNumberConnections = (TextView)findViewById(R.id.text_number_connections);
        textNumberConnections.setText(String.valueOf(connections));

        textLastDevice = (TextView)findViewById(R.id.text_last_device);

        logScroll = (ScrollView)findViewById(R.id.log_scroll);

        logText = (TextView)findViewById(R.id.log_text);

        // Initialize Bluetooth adapter.
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        checkStateBluetooth();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            checkStateBluetooth();
        }
    }

    // Check if bluetooth is on. If it is connected, prepare the server, otherwise prompt a
    // dialog to on it.
    private void checkStateBluetooth() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            Toast.makeText(MainActivity.this, R.string.server_prepared, Toast.LENGTH_SHORT).show();
            ready = true;

            try {
                bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
                if (bluetoothLeAdvertiser == null) {
                    throw new NullPointerException("Cannot get an advertiser.");
                }

                serverCallback = new BluetoothGattServerCallback() {
                    @Override
                    public void onConnectionStateChange(final BluetoothDevice device, int status, int newState) {
                        if (newState == BluetoothGatt.STATE_CONNECTED) {
                            Log.i(TAG, "Device connected (" + device.getAddress() + ")");

                            // Update displayed data.
                            connections++;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    textNumberConnections.setText(String.valueOf(connections));
                                    textLastDevice.setText(device.getAddress());
                                }
                            });
                        }

                        super.onConnectionStateChange(device, status, newState);
                    }

                    @Override
                    public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                        Log.i(TAG, "Characteristic write request");

                        // Update log.
                        if (characteristic.getUuid().equals(MainActivity.this.characteristic.getUuid())) {
                            final String output = "\n[" + device.getAddress() + "] " + new String(value);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    logText.append(output);
                                    logScroll.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            logScroll.fullScroll(View.FOCUS_DOWN);
                                        }
                                    });
                                }
                            });
                        }

                        super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
                    }
                };

                // Initialize server and add my service and characteristic.
                bluetoothGattServer = bluetoothManager.openGattServer(MainActivity.this.getApplicationContext(), serverCallback);
                service = new BluetoothGattService(UUID_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY);
                characteristic = new BluetoothGattCharacteristic(UUID_CHARACTERISTIC, BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
                characteristic.setValue("ffffff");
                service.addCharacteristic(characteristic);
                bluetoothGattServer.addService(service);
            } catch (Exception e) {
                serviceEnableSwitch.setEnabled(false);
                Toast.makeText(getApplicationContext(), R.string.unable_to_start_server, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Method called when the switch for turning on/off the server changes.
    public void onServiceEnableSwitchChanged(View v) {
        if (ready) {
            if (serviceEnableSwitch.isChecked()) {
                startAdvertising();

                getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.green)));
            } else {
                stopAdvertising();

                getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.grey900)));
            }
        } else {
            serviceEnableSwitch.setChecked(false);
            Toast.makeText(MainActivity.this, R.string.unable_to_start_server, Toast.LENGTH_SHORT).show();
        }
    }

    // Start advertising
    public void startAdvertising() {
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();

        dataBuilder.setIncludeTxPowerLevel(true);
        dataBuilder.addServiceUuid(ParcelUuid.fromString("0000181C-0000-1000-8000-00805F9B34FB"));

        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
        settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        settingsBuilder.setConnectable(true);

        callback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);

                Toast.makeText(MainActivity.this, R.string.success_start_advertising, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStartFailure(int errorCode) {
                super.onStartFailure(errorCode);

                serviceEnableSwitch.setChecked(false);
                getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.grey900)));
                Toast.makeText(MainActivity.this, R.string.failure_start_advertising, Toast.LENGTH_SHORT).show();
            }
        };

        bluetoothLeAdvertiser.startAdvertising(settingsBuilder.build(), dataBuilder.build(), callback);
    }

    // Stop advertising
    private void stopAdvertising() {
        Toast.makeText(MainActivity.this, R.string.stop_advertising, Toast.LENGTH_SHORT).show();

        bluetoothLeAdvertiser.stopAdvertising(callback);
    }
}
