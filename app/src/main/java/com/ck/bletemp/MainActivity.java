package com.ck.bletemp;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Vector;

@TargetApi(23)
public class MainActivity extends Activity {

    private final static int REQUEST_ENABLE_BT = 1;
    private final static int REQUEST_PERMISSION_COARSE_LOCATION = 2;
    private final static int REQUEST_SET_LOCATION = 3;

    private final static long SCAN_PERIOD = 1000;

    //define the normal temperature
    private final static double TEMP_NORMAL_LOW = 36.0;
    private final static double TEMP_NORMAL_HIGH = 37.0;
    private final static double TEMP_HIGHER_FEVER = 38.0;
    private final static double TEMP_HIGHER_BOILED = 39.0;

    private volatile boolean allowScan = false;
    private volatile boolean isScanLooping = false;
    private volatile boolean isHideIrrelevantDevices = false;

    EditText editTextFilter;
    Button buttonFilter;
    Button buttonClearFilter;
    Button buttonHideIrrelevantDevices;
    TextView textViewUsingFilter;
    ListView listViewDevices;
    TextView textViewLog;

    String stringFilter = "";

    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;

    MyVector bluetoothDeviceContainerVector = new MyVector();

    ScanCallback scanCallBack;
    BluetoothAdapter.LeScanCallback leScanCallBack;

    Handler handler = new Handler();

    class MyVector extends Vector<BluetoothDeviceContainer> {
        @Override
        public boolean contains(Object o) {
            BluetoothDeviceContainer target = (BluetoothDeviceContainer) o;
            for (int i = 0; i < this.size(); i++) {
                BluetoothDeviceContainer bluetoothDeviceContainer = this.get(i);
                if (bluetoothDeviceContainer.bluetoothDevice.equals(target.bluetoothDevice)) {
                    return true;
                }
            }
            return false;
        }

        void replace(BluetoothDeviceContainer src) {
            for (int i = 0; i < this.size(); i++) {
                BluetoothDeviceContainer bluetoothDeviceContainer = this.get(i);
                if (bluetoothDeviceContainer.bluetoothDevice.equals(src.bluetoothDevice)) {
                    this.set(i, src);
                }
            }
        }
    }

    class BluetoothDeviceContainer {
        BluetoothDevice bluetoothDevice;
        byte[] record;

        BluetoothDeviceContainer(BluetoothDevice bluetoothDevice, byte[] record) {
            this.bluetoothDevice = bluetoothDevice;
            this.record = record;
        }
    }

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
        textViewLog.setText("Ready");
    }

    private void bluetoothCheck() {

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
                    //Log.i("scanCallBack running", "Thread : " + Thread.currentThread().getId());
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            if (null != result.getScanRecord()) {
                                byte[] values = result.getScanRecord().getBytes();
                                Log.i("device", result.getDevice().getAddress() + ": byte[" + values.length + "] : " + bytesToHexString(values));
                            }
                            if (!result.getDevice().getAddress().toLowerCase().contains(stringFilter.toLowerCase())) {
                                return;
                            }
                            BluetoothDeviceContainer bluetoothDeviceContainer;
                            if (null == result.getScanRecord()) {
                                bluetoothDeviceContainer = new BluetoothDeviceContainer(result.getDevice(), null);
                            } else {
                                bluetoothDeviceContainer = new BluetoothDeviceContainer(result.getDevice(), result.getScanRecord().getBytes());
                            }

                            if (isHideIrrelevantDevices && getIndex(bluetoothDeviceContainer.record) < 0) {
                                return;
                            }

                            if (bluetoothDeviceContainerVector.contains(bluetoothDeviceContainer)) {
                                bluetoothDeviceContainerVector.replace(bluetoothDeviceContainer);
                            } else {
                                bluetoothDeviceContainerVector.add(bluetoothDeviceContainer);
                            }
                            baseAdapterDevices.notifyDataSetChanged();
                        }
                    });
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    super.onBatchScanResults(results);
                    Log.i("scanCallback","onBatchScanResults has been called");
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
                    //Log.i("leScanCallBack running", "Thread : " + Thread.currentThread().getId());
                    Log.i("device", bluetoothDevice.getAddress() + ": byte[" + values.length + "] : " + bytesToHexString(values));
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            if (!bluetoothDevice.getAddress().toLowerCase().contains(stringFilter.toLowerCase())) {
                                return;
                            }

                            BluetoothDeviceContainer bluetoothDeviceContainer = new BluetoothDeviceContainer(bluetoothDevice, values);

                            if (isHideIrrelevantDevices && getIndex(bluetoothDeviceContainer.record) < 0) {
                                return;
                            }

                            if (bluetoothDeviceContainerVector.contains(bluetoothDeviceContainer)) {
                                bluetoothDeviceContainerVector.replace(bluetoothDeviceContainer);
                            } else {
                                bluetoothDeviceContainerVector.add(bluetoothDeviceContainer);
                            }

                            baseAdapterDevices.notifyDataSetChanged();
                        }
                    });
                }
            };
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //判断是否有权限
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                //判断是否需要 向用户解释，为什么要申请该权限
                if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
                    Toast.makeText(this, "Please grant the permission this time", Toast.LENGTH_SHORT).show();
                }
                //请求权限
                Log.i("android 6.0","requesting ACCESS_COARSE_LOCATION");
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSION_COARSE_LOCATION);
            } else {
                Log.i("android 6.0","ACCESS_COARSE_LOCATION is granted");
            }

            if (!isLocationEnable(this)) {
                Toast.makeText(this,"请开启位置服务，否则无法搜索到设备！",Toast.LENGTH_LONG).show();
                Intent locationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(locationIntent, REQUEST_SET_LOCATION);
            }
        }
    }

    public static boolean isLocationEnable(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean networkProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        boolean gpsProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        return networkProvider || gpsProvider;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SET_LOCATION:
                if (!isLocationEnable(this)) {
                    Toast.makeText(this,"位置服务没有开启，应用即将退出！",Toast.LENGTH_LONG).show();
                    finish();
                }
                break;

            default:
                break;
        }
    }

    public static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    private void UIInit() {
        editTextFilter = (EditText) findViewById(R.id.editTextFilter);
        listViewDevices = (ListView) findViewById(R.id.listViewDevices);
        buttonFilter = (Button) findViewById(R.id.buttonFilter);
        buttonClearFilter = (Button)findViewById(R.id.buttonClearFilter);
        buttonHideIrrelevantDevices = (Button)findViewById(R.id.buttonHideIrrelevantDevices);
        textViewUsingFilter = (TextView) findViewById(R.id.textViewUsingFilter);
        textViewLog = (TextView) findViewById(R.id.textViewLog);

        boolean isError = (null == editTextFilter || null == listViewDevices || null == buttonFilter || null == textViewLog || null == buttonHideIrrelevantDevices || null == buttonClearFilter ||null == textViewUsingFilter);
        if (isError) {
            Toast.makeText(this, "UI初始化出错！", Toast.LENGTH_LONG).show();
            finish();
        }
        listViewDevices.setAdapter(baseAdapterDevices);
        buttonFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateFilter();
            }
        });

        buttonClearFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editTextFilter.setText("");
                updateFilter();
            }
        });

        buttonHideIrrelevantDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isHideIrrelevantDevices = !isHideIrrelevantDevices;
                if (isHideIrrelevantDevices) {
                    buttonHideIrrelevantDevices.setText("Show Irrelevant Devices");
                } else {
                    buttonHideIrrelevantDevices.setText("Hide Irrelevant Devices");
                }
                bluetoothDeviceContainerVector.clear();
                baseAdapterDevices.notifyDataSetChanged();
                stopScan();
                startScan();
            }
        });

    }

    private void updateFilter() {
        stringFilter = editTextFilter.getText().toString();
        textViewUsingFilter.setText("Using Filter:" + stringFilter);
        bluetoothDeviceContainerVector.clear();
        baseAdapterDevices.notifyDataSetChanged();
        stopScan();
        startScan();
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
                startLoopScan();
                break;

            case R.id.action_stop:
                if (allowScan = true) {
                    textViewLog.setText("Stopping Scan...");
                    stopScan();
                    allowScan = false;
                }
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

    private void startLoopScan() {
        if (isScanLooping) {
            textViewLog.setText("Already Scanning");
            return;
        }
        textViewLog.setText("Start Scanning...");

        allowScan = true;
        bluetoothDeviceContainerVector.clear();
        baseAdapterDevices.notifyDataSetChanged();
        if (!bluetoothAdapter.isEnabled()) {
            textViewLog.setText("Please enable bluetooth and try again");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                stopScan();
                startScan();
                if (allowScan) {
                    isScanLooping = true;
                    textViewLog.setText("Scanning...");
                    handler.postDelayed(this, SCAN_PERIOD);
                } else {
                    isScanLooping = false;
                    textViewLog.setText("Scan Stopped");
                }
            }
        };
        handler.post(runnable);
    }

    private void startScan() {
        if (!allowScan) {
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            allowScan = false;
            return;
        }
        if (bluetoothAdapter.isDiscovering()) {
            stopScan();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (null!=scanCallBack) {
                bluetoothAdapter.getBluetoothLeScanner().startScan(scanCallBack);
            }
        } else {
            if (null!=leScanCallBack) {
                bluetoothAdapter.startLeScan(leScanCallBack);
            }
        }
    }


    private void stopScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (null!=bluetoothAdapter.getBluetoothLeScanner()) {
                bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallBack);
            }
        } else {
            bluetoothAdapter.stopLeScan(leScanCallBack);
        }
    }

    @Override
    public void finish() {
        stopScan();
        allowScan = false;
        super.finish();
    }

    private BaseAdapter baseAdapterDevices = new BaseAdapter() {
        @Override
        public int getCount() {
            return bluetoothDeviceContainerVector.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
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
            if (null != bluetoothDeviceContainerVector.get(position).bluetoothDevice.getName()) {
                deviceListViewHolder.deviceName.setText(bluetoothDeviceContainerVector.get(position).bluetoothDevice.getName());
            } else {
                deviceListViewHolder.deviceName.setText("No Name");
            }
            if (null != bluetoothDeviceContainerVector.get(position).bluetoothDevice.getAddress()) {
                final String address = bluetoothDeviceContainerVector.get(position).bluetoothDevice.getAddress();
                deviceListViewHolder.deviceAddress.setText(address);
                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        editTextFilter.setText(address);
                        updateFilter();
                    }
                });
            } else {
                deviceListViewHolder.deviceAddress.setText("--:--:--:--:--:--");
            }

            byte[] bytes;
            if (null != (bytes = bluetoothDeviceContainerVector.get(position).record)) {
                int index = getIndex(bytes);
                if (index >= 0) {
                    byte upper = bytes[index], lower = bytes[index + 1];
                    double temperature = (upper & 0x7f) + (lower & 0xff) / 256.0;

                    byte[] raw = new byte[2];
                    raw[0] = upper;
                    raw[1] = lower;
                    DecimalFormat df = new DecimalFormat("#0.00");
                    String stringTemperature;
                    if ((upper&0x80) == 0) {
                        stringTemperature = df.format(temperature);
                    } else {
                        stringTemperature = "Error";
                    }

                    SpannableString spannableString = new SpannableString("Temperature:" + stringTemperature + "℃    raw:0x" + bytesToHexString(raw));

                    spannableString.setSpan(new AbsoluteSizeSpan(25,true),12,12 + stringTemperature.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    if (temperature < TEMP_NORMAL_LOW ) {
                        spannableString.setSpan(new ForegroundColorSpan(Color.BLUE),12,12 + stringTemperature.length(),Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    } else if (temperature <= TEMP_NORMAL_HIGH){
                        spannableString.setSpan(new ForegroundColorSpan(Color.GREEN),12,12 + stringTemperature.length(),Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    } else if (temperature <= TEMP_HIGHER_FEVER) {
                        spannableString.setSpan(new ForegroundColorSpan(Color.YELLOW),12,12 + stringTemperature.length(),Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    } else{
                        spannableString.setSpan(new ForegroundColorSpan(Color.RED),12,12 + stringTemperature.length(),Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    }
                    if (temperature >= TEMP_HIGHER_BOILED) {
                        deviceListViewHolder.deviceInfo.setBackgroundColor(Color.GRAY);
                    }else {
                        deviceListViewHolder.deviceInfo.setBackgroundColor(Color.TRANSPARENT);
                    }

                    deviceListViewHolder.deviceInfo.setText(spannableString);
                } else {
                    deviceListViewHolder.deviceInfo.setText("No temperature data");
                    deviceListViewHolder.deviceInfo.setTextSize(15);
                }
            } else {
                deviceListViewHolder.deviceInfo.setText("No temperature data");
                deviceListViewHolder.deviceInfo.setTextSize(15);
            }

            return convertView;
        }

        class DeviceListViewHolder {
            TextView deviceName;
            TextView deviceAddress;
            TextView deviceInfo;
        }
    };

    private int getIndex(byte[] bytes) {
        if (null == bytes) {
            return -1;
        }

        int len = bytes.length;

        if (len < 22 ) {
            return -1;
        }

        for (int i = 0; i < len - 1; ) {
            int packetLen = bytes[i];
            byte packetType = bytes[i + 1];
            if ((0x15 == packetLen) && (0xFF == (packetType & 0xFF))) {
                return i + 20;
            } else {
                i += (packetLen + 1);
            }
        }
        return -1;
    }
}
