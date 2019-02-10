package com.cmp491.samples.samplebeacons;

/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import android.Manifest;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends ListActivity {
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothScanner;
    private boolean mScanning;
    private Handler mHandler;
    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 20000;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 456;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setTitle(R.string.title_devices);
        mHandler = new Handler();
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBluetoothScanner = mBluetoothAdapter.getBluetoothLeScanner();
        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, yay! Start the Bluetooth device scan.
                } else {
                    // Alert the user that this application requires the location permission to perform the scan.
                    Toast.makeText(this, "Try again!", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.activity_device_scan);
        }
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
        }
        return true;
    }
    @Override
    protected void onResume() {
        super.onResume();
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
        scanLeDevice(true);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) return;
        final Intent intent = new Intent(this, ControlActivity.class);
        intent.putExtra(ControlActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(ControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
        if (mScanning) {
            mBluetoothScanner.stopScan(mLeScanCallback);
            mScanning = false;
        }
        startActivity(intent);
    }
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothScanner.stopScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);
            mScanning = true;
            mBluetoothScanner.startScan(mLeScanCallback);

        } else {
            mScanning = false;
            mBluetoothScanner.stopScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }
    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private ArrayList<Integer> mLeRssis;
        private ArrayList<String> mLeIds;
        private LayoutInflater mInflator;
        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mLeRssis = new ArrayList<Integer>();
            mLeIds = new ArrayList<String>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }
        public void addDevice(BluetoothDevice device, String id, int rssi) {
            if(!mLeIds.contains(id)) {
                mLeDevices.add(device);
                mLeIds.add(id);
                mLeRssis.add(rssi);
            }
        }
        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }
        public int getRssi (int position) {return mLeRssis.get(position); }
        public void clear() {
            mLeDevices.clear();
        }
        @Override
        public int getCount() {
            return mLeDevices.size();
        }
        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }
        @Override
        public long getItemId(int i) {
            return i;
        }
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceRssi = (TextView) view.findViewById(R.id.device_rssi);
                viewHolder.deviceId = (TextView) view.findViewById(R.id.device_id);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }
            BluetoothDevice device = mLeDevices.get(i);
            int rssi = mLeRssis.get(i);
            String id = mLeIds.get(i);

//            final String deviceName = device.getAddress();
//            if (deviceName != null && deviceName.length() > 0)
//                viewHolder.deviceId.setText(deviceName);
//            else
//                viewHolder.deviceId.setText(R.string.unknown_device);
            viewHolder.deviceRssi.setText(Integer.toString(rssi));
            viewHolder.deviceId.setText(id);
            return view;
        }
    }
    // Device scan callback.
    private ScanCallback mLeScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, final ScanResult result) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ArrayList<String> beacons = new ArrayList<String>();
//                            beacons.add("59bfdda585767280f8"); //Icy B
//                            beacons.add("283acdcf5be28c0f71"); //Icy A
//                            beacons.add("5812ca89ff64bf3565"); //Mint B
//                            beacons.add("6a811095d963f29290"); //Mint A
//                            beacons.add("3c52a5930c34db2294"); //Coconut B
//                            beacons.add("4454649ebee76a8e5f"); //Coconut A
//                            beacons.add("e158516ea666f214c3"); //Blueberry B
//                            beacons.add("d9b0b6f879088d8f76"); //Blueberry A

                            beacons.add("59bfdda585767280f886db284653ee35"); //Icy B
                            beacons.add("283acdcf5be28c0f71dc4b6a84219d29"); //Icy A
                            beacons.add("5812ca89ff64bf356564f5ee641f6f1b"); //Mint B
                            beacons.add("6a811095d963f29290ea5371b4177020"); //Mint A
                            beacons.add("3c52a5930c34db229451868164d7fc13"); //Coconut B
                            beacons.add("4454649ebee76a8e5f23a202825c8401"); //Coconut A
                            beacons.add("e158516ea666f214c38d5464c5440d1f"); //Blueberry B
                            beacons.add("d9b0b6f879088d8f767576e07841e43a"); //Blueberry A

                            byte[] bytes = result.getScanRecord().getServiceData(ParcelUuid.fromString("0000fe9a-0000-1000-8000-00805f9b34fb"));
                            if(bytes != null) {
                                StringBuilder sb = new StringBuilder();
                                for (int i = 1; i < bytes.length; i++) {
                                    if (i <= 16)
                                        sb.append(String.format("%02x", bytes[i]));
                                }
                                if(beacons.contains(sb.toString())) {
//                                    Log.d("GEHAD", sb.toString());
                                    BluetoothDevice device = result.getDevice();
                                    Log.d("GEHAD", "identifier: " + sb.toString() + " address: " + device.getAddress() +  " name: " + device.getName());
                                    mLeDeviceListAdapter.addDevice(device, sb.toString(), result.getRssi());
                                    mLeDeviceListAdapter.notifyDataSetChanged();
                                }
                            }
                        }
                    });
                }
            };
    static class ViewHolder {
        TextView deviceId;
        TextView deviceRssi;
    }

//    @Override
//    public void onScanResult(int callbackType, final ScanResult result) {
//        super.onScanResult(callbackType, result);
//
//        // Get the ScanRecord and check if it is defined (is nullable)
//        final ScanRecord scanRecord = result.getScanRecord();
//        if (scanRecord != null) {
//            // Check if the Service UUIDs are defined (is nullable) and contain the discovery
//            // UUID
//            final List<ParcelUuid> serviceUuids = scanRecord.getServiceUuids();
//            if (serviceUuids != null && serviceUuids.contains(DISCOVERY_UUID)) {
//                // We have found our device, so update the GUI, stop scanning and start
//                // connecting
//                final BluetoothDevice device = result.getDevice();
//
//                // We'll make sure the GUI is updated on the UI thread
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        // At this point we have the device address and RSSI, so update those
//                        deviceAddressTextView.setText(device.getAddress());
//                        rssiTextView.setText(getString(R.string.rssi, result.getRssi()));
//                    }
//                });
//
//                stopDiscovery();
//
//                bluetoothGatt = device.connectGatt(
//                        MainActivity.this,
//                        // False here, as we want to directly connect to the device
//                        false,
//                        bluetoothGattCallback
//                );
//            }
//        }
//    }

}