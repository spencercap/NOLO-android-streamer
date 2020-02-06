package com.mainactivity.usbhost;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;
import com.caliber.JNITest;
import com.caliber.Nolo_ControllerStates;
import com.caliber.Nolo_Pose;
import com.caliber.Nolo_Vector3;

//import com.watchdata.usbhostconn.UsbCustomTransfer2;
import com.watchdata.usbhostconn.Utils;

import java.util.HashMap;
import java.util.Iterator;

public class UsbCustomTransfer2 {
    private static final String TAG = UsbCustomTransfer2.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    UsbManager manager;
    private UsbDevice device = null;
    volatile byte[] mybuffer = new byte[64];
    byte[] mytmpbyte = new byte[64];
    byte[] mytmpbyte1 = new byte[64];
    byte[] mytmpbyte2 = new byte[64];
    private int myvid2 = 1155;
    private int mypid2 = 22352;
    private volatile UsbDeviceConnection connection = null;
    private UsbInterface musbInterace;
    private UsbEndpoint musbEndpoint_in;
    private UsbEndpoint musbEndpoint_out;
    private int packetSize;
    UsbCustomTransfer2.ConnectedThread mconnectedthread = null;
    private static UsbCustomTransfer2 instance;
    boolean threadsenddata = false;
    private boolean startFrameHasRece = false;
    private PendingIntent pi;
    private UsbCustomTransfer2.DisconnectedCallback mDisconnectedCallback;
    private int LastNoloDeviceProtocol = 0;
    private int CurrentNoloDeviceProtocol = 0;
    private int USB_CONNECTED_STATE = 0;
    private int USB_DISCONNECTED_STATE = 1;
    private Context mcontext;
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i("UsbCustomTransfer2", "接收到广播的动作类型：" + action);
            if (action.equalsIgnoreCase("android.hardware.usb.action.USB_DEVICE_DETACHED")) {
                if (UsbCustomTransfer2.this.connection != null) {
                    UsbCustomTransfer2.this.connection.releaseInterface(UsbCustomTransfer2.this.musbInterace);
                    UsbCustomTransfer2.this.connection.close();
                    UsbCustomTransfer2.this.connection = null;
                    if (UsbCustomTransfer2.this.mDisconnectedCallback != null) {
                        UsbCustomTransfer2.this.mDisconnectedCallback.setUsbDeviceConnState(2);
                    }
                }
            } else if (action.equalsIgnoreCase("android.hardware.usb.action.USB_DEVICE_ATTACHED")) {
                UsbCustomTransfer2.this.get_device();
                if (UsbCustomTransfer2.this.mDisconnectedCallback != null) {
                    UsbCustomTransfer2.this.mDisconnectedCallback.setUsbDeviceConnState(1);
                }
            }

        }
    };

    private UsbCustomTransfer2(Context context) {
        this.mcontext = context;
    }

    public static UsbCustomTransfer2 getInstance(Context context) {
        if (instance == null) {
            instance = new UsbCustomTransfer2(context);
        }

        return instance;
    }

    private void get_device() {
        HashMap<String, UsbDevice> deviceList = this.manager.getDeviceList();
        Iterator deviceIterator = deviceList.values().iterator();

        while(deviceIterator.hasNext()) {
            this.device = (UsbDevice)deviceIterator.next();
            Log.i(TAG, "vid: " + this.device.getVendorId() + "\t pid: " + this.device.getProductId());
            if (this.device.getVendorId() == this.myvid2 && this.device.getProductId() == this.mypid2) {
                break;
            }
        }

        if (this.device != null && this.device.getVendorId() == this.myvid2 && this.device.getProductId() == this.mypid2) {
            Log.i(TAG, "连凌宇智控手柄设备插入手机");
            Toast.makeText(this.mcontext, "连凌宇智控手柄设备", Toast.LENGTH_SHORT).show();
            this.pi = PendingIntent.getBroadcast(this.mcontext, 0, new Intent("com.android.example.USB_PERMISSION"), 0);
            if (this.manager.hasPermission(this.device)) {
                Log.i(TAG, "获取了访问权限");
                Toast.makeText(this.mcontext, "获取了访问权限", Toast.LENGTH_SHORT).show();
            } else {
                this.manager.requestPermission(this.device, this.pi);
            }

            if (this.mconnectedthread != null) {
                this.mconnectedthread = null;
            }

        } else if (this.device == null) {
            Log.i(TAG, "没有设备接入");
            Toast.makeText(this.mcontext, "请插入设备", Toast.LENGTH_SHORT).show();
        } else {
            Log.i(TAG, "插入手机的不是凌宇智控设备");
            Toast.makeText(this.mcontext, "请插入凌宇智控设备", Toast.LENGTH_SHORT).show();
        }
    }

    public void usb_init() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.hardware.usb.action.USB_DEVICE_DETACHED");
        filter.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED");
        this.mcontext.registerReceiver(this.mUsbReceiver, filter);
        this.manager = (UsbManager)this.mcontext.getSystemService("usb");
        this.get_device();
    }

    public int usb_conn() {
        if (this.pi != null) {
            if (this.manager.hasPermission(this.device)) {
                Log.i(TAG, "获取了访问权限");
                Toast.makeText(this.mcontext, "获取了访问权限", Toast.LENGTH_SHORT).show();
                this.mconnectedthread = new UsbCustomTransfer2.ConnectedThread();
                this.mconnectedthread.start();
                return 1;
            } else {
                Toast.makeText(this.mcontext, "没有获取访问权限，不能连接设备", Toast.LENGTH_SHORT).show();
                return 0;
            }
        } else {
            Toast.makeText(this.mcontext, "请首先向系统申请获取USB访问权限", Toast.LENGTH_SHORT).show();
            return 0;
        }
    }

    public void usb_finish() {
        try {
            this.mcontext.unregisterReceiver(this.mUsbReceiver);
        } catch (IllegalArgumentException var2) {
            if (!var2.getMessage().contains("Receiver not registered")) {
                throw var2;
            }
        }

        if (this.connection != null) {
            this.connection.releaseInterface(this.musbInterace);
            this.connection.close();
            this.connection = null;
            Toast.makeText(this.mcontext, "断开USB 连接", Toast.LENGTH_SHORT).show();
        }

    }

    public void usb_sendData(byte[] mdata) {
        this.mytmpbyte = mdata;
        Log.i("UsbCustomTransfer2", "接收数据长度：" + Integer.toString(mdata.length, 10));
        this.threadsenddata = true;
    }

    public int getDeviceTrackingStatus(int type) {
        return JNITest.GetDeviceTrackingStatus(type);
    }

    public int getVersionByDeviceType(int type) {
        return JNITest.GetVersionByDeviceType(type);
    }

    public int getElectricityByDeviceType(int type) {
        return JNITest.GetElectricityByDeviceType(type);
    }

    public Nolo_Pose getPoseByDeviceType(int type) {
        return JNITest.GetPoseByDeviceType(type);
    }

    public Nolo_ControllerStates getControllerStatesByDeviceType(int type) {
        return JNITest.GetControllerStatesByDeviceType(type);
    }

    public Nolo_Vector3 getHmdInitPosition() {
        return JNITest.GetHmdInitPosition();
    }

    public int getHmdCalibration() {
        return JNITest.GetHmdCalibration();
    }

    public void setDisconnectedCallback(UsbCustomTransfer2.DisconnectedCallback mcallback) {
        this.mDisconnectedCallback = mcallback;
    }

    public interface DisconnectedCallback {
        void setUsbDeviceConnState(int var1);
    }

    class ConnectedThread extends Thread {
        public void destroy() {
        }

        public ConnectedThread() {
            if (UsbCustomTransfer2.this.connection != null) {
                UsbCustomTransfer2.this.connection.close();
            }

            UsbCustomTransfer2.this.connection = UsbCustomTransfer2.this.manager.openDevice(UsbCustomTransfer2.this.device);
            UsbCustomTransfer2.this.musbInterace = UsbCustomTransfer2.this.device.getInterface(0);
            if (UsbCustomTransfer2.this.connection != null) {
                UsbCustomTransfer2.this.connection.claimInterface(UsbCustomTransfer2.this.musbInterace, true);
            }

            if (UsbCustomTransfer2.this.musbInterace.getInterfaceClass() == 3) {
                Log.e(UsbCustomTransfer2.TAG, "外设支持HID 协议");
            }

            try {
                if (0 == UsbCustomTransfer2.this.musbInterace.getEndpoint(1).getDirection()) {
                    UsbCustomTransfer2.this.musbEndpoint_out = UsbCustomTransfer2.this.musbInterace.getEndpoint(1);
                    Log.e(UsbCustomTransfer2.TAG, "USB 输出端点");
                    Toast.makeText(UsbCustomTransfer2.this.mcontext, "USB 输出端点", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception var4) {
                Log.e("endPointWrite", "Device have no endPointWrite", var4);
            }

            try {
                if (128 == UsbCustomTransfer2.this.musbInterace.getEndpoint(0).getDirection()) {
                    UsbCustomTransfer2.this.musbEndpoint_in = UsbCustomTransfer2.this.musbInterace.getEndpoint(0);
                    UsbCustomTransfer2.this.packetSize = UsbCustomTransfer2.this.musbEndpoint_in.getMaxPacketSize();
                    Log.e(UsbCustomTransfer2.TAG, "USB 输入端点长度" + Integer.toString(UsbCustomTransfer2.this.packetSize, 10));
                }
            } catch (Exception var3) {
                Log.e("endPointWrite", "Device have no endPointRead", var3);
            }

        }

        public void run() {
            Log.i(UsbCustomTransfer2.TAG, "初始化动态库");
            JNITest.initSo();

            while(UsbCustomTransfer2.this.connection != null && UsbCustomTransfer2.this.musbEndpoint_in != null) {
                if (UsbCustomTransfer2.this.threadsenddata) {
                    UsbCustomTransfer2.this.threadsenddata = false;
                    UsbCustomTransfer2.this.connection.bulkTransfer(UsbCustomTransfer2.this.musbEndpoint_out, UsbCustomTransfer2.this.mytmpbyte, UsbCustomTransfer2.this.mytmpbyte.length, 100);
                    Log.i(UsbCustomTransfer2.TAG, "发送数据给设备成功");
                }

                int datalength = UsbCustomTransfer2.this.connection.bulkTransfer(UsbCustomTransfer2.this.musbEndpoint_in, UsbCustomTransfer2.this.mybuffer, UsbCustomTransfer2.this.packetSize, 100);
                if (datalength > 0 && datalength == 64) {
                    if (UsbCustomTransfer2.this.mybuffer[0] == -91) {
                        UsbCustomTransfer2.this.startFrameHasRece = true;
                        System.arraycopy(UsbCustomTransfer2.this.mybuffer, 0, UsbCustomTransfer2.this.mytmpbyte1, 0, 64);
                    } else if (UsbCustomTransfer2.this.startFrameHasRece && UsbCustomTransfer2.this.mybuffer[0] == -90) {
                        UsbCustomTransfer2.this.startFrameHasRece = false;
                        System.arraycopy(UsbCustomTransfer2.this.mybuffer, 0, UsbCustomTransfer2.this.mytmpbyte2, 0, 64);
                        String str1 = Utils.toHexFromBytes(UsbCustomTransfer2.this.mytmpbyte1).toLowerCase();
                        String str2 = Utils.toHexFromBytes(UsbCustomTransfer2.this.mytmpbyte2).toLowerCase();
                        JNITest.sendStringDataToUnity(str1, str2);
                    } else {
                        UsbCustomTransfer2.this.startFrameHasRece = false;
                    }
                }
            }

        }
    }

    static enum NoloDeviceType {
        Hmd,
        LeftController,
        RightController,
        BaseStationOne;

        private NoloDeviceType() {
        }
    }
}
