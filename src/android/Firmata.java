package com.github.warp.cordova.firmata;

import java.io.*;
import java.lang.*;
import java.util.HashMap;
import java.util.List;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.*;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.shokai.firmata.ArduinoFirmata;
import org.shokai.firmata.ArduinoFirmataEventHandler;

public class Firmata extends CordovaPlugin {
    private static final String TAG = "CordovaFirmata";
    private static final String ACTION_USB_PERMISSION = TAG + ".USB_PERMISSION";

    private static ArduinoFirmata arduino;

    private UsbManager usbManager;
    private PendingIntent permissionIntent;
    private BroadcastReceiver usbReceiver;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (usbManager == null) {
            usbManager = (UsbManager) webView.getContext().getSystemService(Context.USB_SERVICE);
            permissionIntent = PendingIntent.getBroadcast(webView.getContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
        }

        if (arduino == null) {
            arduino = new ArduinoFirmata(this.cordova.getActivity());
            arduino.setEventHandler(new ArduinoFirmataEventHandler() {
                public void onError(String errorMessage) {
                    Log.e(TAG, errorMessage);
                }
                public void onClose() {
                    Log.v(TAG, "connection closed");
                }
            });

        }

        if ("hasUsbHostFeature".equals(action)) {
            boolean usbHostFeature = cordova.getActivity().getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, usbHostFeature));
            return true;
        } else if (action.equals("getBoardVersion")) {
            this.getBoardVersion(callbackContext);
            return true;
        } else if (action.equals("connect")) {
            this.connect(callbackContext);
            return true;
        } else if (action.equals("isOpen")) {
            this.isOpen(callbackContext);
            return true;
        } else if (action.equals("close")) {
            this.close(callbackContext);
            return true;
        } else if (action.equals("reset")) {
            this.reset(callbackContext);
            return true;
        } else if (action.equals("digitalRead")) {
            int pin = args.getInt(0);
            this.digitalRead(pin, callbackContext);
            return true;
        } else if (action.equals("analogRead")) {
            int pin = args.getInt(0);
            this.analogRead(pin, callbackContext);
            return true;
        } else if (action.equals("pinMode")) {
            int pin = args.getInt(0);
            byte mode = (byte) args.getInt(1);
            this.pinMode(pin, mode, callbackContext);
            return true;
        } else if (action.equals("digitalWrite")) {
            int pin = args.getInt(0);
            boolean value = args.getBoolean(1);
            this.digitalWrite(pin, value, callbackContext);
            return true;
        } else if (action.equals("analogWrite")) {
            int pin = args.getInt(0);
            int value = args.getInt(1);
            this.analogWrite(pin, value, callbackContext);
            return true;
        } else if (action.equals("servoWrite")) {
            int pin = args.getInt(0);
            int angle = args.getInt(1);
            this.servoWrite(pin, angle, callbackContext);
            return true;
        } else if (action.equals("sendMessage")) {
            int command = args.getInt(0);
            JSONArray data = args.getJSONArray(1);
            this.sendMessage(command, data, callbackContext);
            return true;
        }
        return false;
    }

    private void getBoardVersion(final CallbackContext callbackContext) {
        String version = arduino.getBoardVersion();
        callbackContext.success(version);
    }

    private void connect(final CallbackContext callbackContext) {
        List<UsbSerialPort> ports = arduino.getDeviceList();
        if (ports.size() > 0) {
            if (usbReceiver == null) {
                usbReceiver = new UsbBroadcastReceiver(callbackContext);
                IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                webView.getContext().registerReceiver(usbReceiver, filter);
            }

            final UsbSerialPort port = ports.get(0);
            if (usbManager.hasPermission(port.getDriver().getDevice())) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            arduino.connect(port);
                            callbackContext.success();
                        } catch (IOException e) {
                            callbackContext.error(e.getMessage());
                        } catch (InterruptedException e) {
                            callbackContext.error(e.getMessage());
                        }
                    }
                });
            } else {
                usbManager.requestPermission(port.getDriver().getDevice(), permissionIntent);
            }
        } else {
            callbackContext.error("No devices are connected");
        }
    }

    private void isOpen(final CallbackContext callbackContext) {
        boolean value = arduino.isOpen();
        callbackContext.success(boolToInt(value));
    }

    private void close(final CallbackContext callbackContext) {
        boolean value = arduino.close();
        callbackContext.success(boolToInt(value));
    }

    private void reset(final CallbackContext callbackContext) {
        arduino.reset();
        callbackContext.success();
    }

    private void digitalRead(final int pin, final CallbackContext callbackContext) {
        boolean value = arduino.digitalRead(pin);
        callbackContext.success(boolToInt(value));
    }

    private void analogRead(final int pin, final CallbackContext callbackContext) {
        int value = arduino.analogRead(pin);
        callbackContext.success(value);
    }

    private void pinMode(final int pin, final byte mode, final CallbackContext callbackContext) {
        arduino.pinMode(pin, mode);
        callbackContext.success();
    }

    private void digitalWrite(final int pin, final boolean value, final CallbackContext callbackContext) {
        arduino.digitalWrite(pin, value);
        callbackContext.success();
    }

    private void analogWrite(final int pin, final int value, final CallbackContext callbackContext) {
        arduino.analogWrite(pin, value);
        callbackContext.success();
    }

    private void servoWrite(final int pin, final int angle, final CallbackContext callbackContext) {
        arduino.servoWrite(pin, angle);
        callbackContext.success();
    }

    private int boolToInt(boolean b) {
        return b ? 1 : 0;
    }

    private void sendMessage(int command, JSONArray data, final CallbackContext callbackContext) {
        try {
            byte[] bytes = new byte[data.length()];
            for (int i = 0; i < data.length(); i++) {
                bytes[i] = (byte) data.getInt(i);
            }
            arduino.sysex((byte) command, bytes);
            callbackContext.success();
        } catch (JSONException e) {
            callbackContext.error(e.getMessage());
        }
    }

    private class UsbBroadcastReceiver extends BroadcastReceiver {
        private final CallbackContext callbackContext;

        public UsbBroadcastReceiver(CallbackContext callbackContext) {
            this.callbackContext = callbackContext;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            connect(callbackContext);
                        }
                    } else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    }
}
