package com.ck.bletemp;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Vector;

@TargetApi(23)
public class MainActivity extends Activity {

    private final static int REQUEST_ENABLE_BT = 1;

    EditText editTextFilter;
    ListView listViewDevices;

    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;

    Vector<BluetoothDevice> bluetoothDeviceVector = new Vector<>();

    ScanCallback scanCallBack;
    BluetoothAdapter.LeScanCallback leScanCallBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init() {
        Log.i("init running", "Thread : " + Thread.currentThread().getId());
        bluetoothCheck();
        UIInit();
    }

    private void bluetoothCheck() {

//----------------------------------------------------------------------------检查设备是否支持蓝牙---------------------------------------------------------------------//
        // 检查当前手机是否支持ble 蓝牙,如果不支持退出程序
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "设备不支持BLE！", Toast.LENGTH_SHORT).show();
            finish();
        }

        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // 检查设备上是否支持蓝牙
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "设备出错！", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            scanCallBack = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, final ScanResult result) {
                    super.onScanResult(callbackType, result);
                    Log.i("scanCallBack running", "Thread : " + Thread.currentThread().getId());
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            if (!bluetoothDeviceVector.contains(result.getDevice())) {
                                if (editTextFilter.getText().toString().isEmpty()) {
                                    bluetoothDeviceVector.add(result.getDevice());
                                    baseAdapterDevices.notifyDataSetChanged();
                                } else {
                                    if (result.getDevice().getAddress().toLowerCase().contains(editTextFilter.getText().toString().toLowerCase())) {
                                        bluetoothDeviceVector.add(result.getDevice());
                                        baseAdapterDevices.notifyDataSetChanged();
                                    }
                                }
                            }
                        }
                    });
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Toast.makeText(MainActivity.this, "Scan Failed！", Toast.LENGTH_SHORT).show();
                }
            };
        } else {
            leScanCallBack = new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice bluetoothDevice, final int rssi, final byte[] values) {
                    Log.i("leScanCallBack running", "Thread : " + Thread.currentThread().getId());
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            if (!bluetoothDeviceVector.contains(bluetoothDevice)) {
                                if (editTextFilter.getText().toString().isEmpty()) {
                                    bluetoothDeviceVector.add(bluetoothDevice);
                                    baseAdapterDevices.notifyDataSetChanged();
                                } else {
                                    if (bluetoothDevice.getAddress().toLowerCase().contains(editTextFilter.getText().toString().toLowerCase())) {
                                        bluetoothDeviceVector.add(bluetoothDevice);
                                        baseAdapterDevices.notifyDataSetChanged();
                                    }
                                }
                            }
                        }
                    });
                }
            };
        }
    }

    private void UIInit() {
        editTextFilter = (EditText) findViewById(R.id.editTextFilter);
        listViewDevices = (ListView) findViewById(R.id.listViewDevices);

        boolean isError = (null == editTextFilter || null == listViewDevices);
        if (isError) {
            Toast.makeText(this, "UI初始化出错！", Toast.LENGTH_LONG).show();
            finish();
        }
        listViewDevices.setAdapter(baseAdapterDevices);
        editTextFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                stopScan();
                startScan();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan:
                startScan();
                break;

            case R.id.action_stop:
                stopScan();
                break;

            case R.id.action_info:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Version 0.8 by CK");
                builder.setTitle("信息:");
                builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                builder.create().show();
                break;
            
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startScan() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }
        bluetoothDeviceVector.clear();
        baseAdapterDevices.notifyDataSetChanged();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothAdapter.getBluetoothLeScanner().startScan(scanCallBack);
        } else {
            bluetoothAdapter.startLeScan(leScanCallBack);
        }
    }


    private void stopScan() {
        Log.i("stopScan running", "begin");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallBack);
        } else {
            bluetoothAdapter.stopLeScan(leScanCallBack);
        }
        bluetoothDeviceVector.clear();
        baseAdapterDevices.notifyDataSetChanged();
        Log.i("stopScan running", "end");
    }

    private BaseAdapter baseAdapterDevices = new BaseAdapter() {
        @Override
        public int getCount() {
            return bluetoothDeviceVector.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            DeviceListViewHolder deviceListViewHolder;
            if (null == convertView) {
                convertView = getLayoutInflater().inflate(
                        R.layout.devicelistitem, parent, false);
                deviceListViewHolder = new DeviceListViewHolder();
                deviceListViewHolder.deviceName = (TextView) convertView
                        .findViewById(R.id.textViewDeviceName);
                deviceListViewHolder.deviceAddress = (TextView) convertView
                        .findViewById(R.id.textViewDeviceAddress);
                deviceListViewHolder.deviceInfo = (TextView) convertView
                        .findViewById(R.id.textViewDeviceInfo);
                convertView.setTag(deviceListViewHolder);
            } else {
                deviceListViewHolder = (DeviceListViewHolder) convertView.getTag();
            }
            if (bluetoothDeviceVector.get(position).getName() != null) {
                deviceListViewHolder.deviceName.setText(bluetoothDeviceVector.get(position).getName());
            } else {
                deviceListViewHolder.deviceName.setText("No Name");
            }
            if (bluetoothDeviceVector.get(position).getAddress() != null) {
                deviceListViewHolder.deviceAddress.setText(bluetoothDeviceVector.get(position).getAddress());
            } else {
                deviceListViewHolder.deviceAddress.setText("--:--:--:--:--:--");
            }
            //// TODO:  deviceInfo
            return convertView;
        }

        class DeviceListViewHolder {
            TextView deviceName;
            TextView deviceAddress;
            TextView deviceInfo;
        }
    };
}
