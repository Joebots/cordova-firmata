package com.joebotics.cordova.firmata;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.firmata4j.I2CDevice;
import org.firmata4j.I2CEvent;
import org.firmata4j.I2CListener;
import org.firmata4j.IOEvent;
import org.firmata4j.Pin;
import org.firmata4j.PinEventListener;
import org.firmata4j.firmata.FirmataDevice;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Firmata extends CordovaPlugin {
    private static final String TAG = "CordovaFirmata";
    private static final String ACTION_USB_PERMISSION = TAG + ".USB_PERMISSION";

    private static FirmataDevice device;

    private UsbManager usbManager;
    private PendingIntent permissionIntent;
    private BroadcastReceiver usbReceiver;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (usbManager == null) {
            usbManager = (UsbManager) webView.getContext().getSystemService(Context.USB_SERVICE);
            permissionIntent = PendingIntent.getBroadcast(webView.getContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
        }
/*
        if (device == null) {
            device = new FirmataDevice(usbManager, port);
            device.setEventHandler(new ArduinoFirmataEventHandler() {
                public void onError(String errorMessage) {
                    Log.e(TAG, errorMessage);
                }
                public void onClose() {
                    Log.v(TAG, "connection closed");
                }
            });
        }
*/

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
            this.pinMode(pin, Pin.Mode.resolve(mode), callbackContext);
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
        } else if (action.equals("onPinChanged")) {
            int pin = args.getInt(0);
            this.onPinChanged(pin, callbackContext);
            return true;
        } else if (action.equals("onI2CEvent")) {
            int address = args.getInt(0);
            int register = args.getInt(1);
            int messageLength = args.getInt(2);
            this.onI2CEvent((byte)address, register, (byte)messageLength, callbackContext);
        }/*else if (action.equals("sendMessage")) {
            int command = args.getInt(0);
            JSONArray data = args.getJSONArray(1);
            this.sendMessage(command, data, callbackContext);
            return true;
        }*/
        return false;
    }

    private List<UsbSerialPort> getDeviceList() {
        UsbManager usbManager = (UsbManager) cordova.getActivity().getApplicationContext().getSystemService(Context.USB_SERVICE);
        final List<UsbSerialDriver> drivers =
                UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        final List<UsbSerialPort> result = new ArrayList<UsbSerialPort>();
        for (final UsbSerialDriver driver : drivers) {
            final List<UsbSerialPort> ports = driver.getPorts();
            Log.d(TAG, String.format("+ %s: %s port%s",
                    driver, Integer.valueOf(ports.size()), ports.size() == 1 ? "" : "s"));
            result.addAll(ports);
        }
        return result;
    }

    private void getBoardVersion(final CallbackContext callbackContext) {
        String version = device.getProtocol();
        callbackContext.success(version);
    }

    private void connect(final CallbackContext callbackContext) {
        List<UsbSerialPort> ports = getDeviceList();
        if (ports.size() > 0) {
            if (usbReceiver == null) {
                usbReceiver = new UsbBroadcastReceiver(callbackContext);
                IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                webView.getContext().registerReceiver(usbReceiver, filter);
            }

            final UsbSerialPort port = ports.get(0);
            if (usbManager.hasPermission(port.getDriver().getDevice())) {
                if (usbReceiver != null) {
                    webView.getContext().unregisterReceiver(usbReceiver);
                    usbReceiver = null;
                }
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            device = new FirmataDevice(usbManager, port);
                            device.start();
                            device.ensureInitializationIsDone();
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
        boolean value = device.isReady();
        callbackContext.success(boolToInt(value));
    }

    private void close(final CallbackContext callbackContext) {
        try {
            device.stop();
            callbackContext.success();
        } catch (IOException e) {
            callbackContext.error(e.getMessage());
        }
    }

    private void reset(final CallbackContext callbackContext) {
        for (Pin pin : device.getPins()) {
            pin.removeAllEventListeners();
        }
        callbackContext.success();
    }

    private void digitalRead(final int pin, final CallbackContext callbackContext) {
        long value = device.getPin(pin).getValue();
        callbackContext.success((int)value);
    }

    private void analogRead(final int pin, final CallbackContext callbackContext) {
        Pin devicePin = device.getPin(pin);
        if (devicePin.getMode() == Pin.Mode.OUTPUT) {
            pinMode(pin, Pin.Mode.INPUT, callbackContext);
        }
        long value = device.getPin(pin).getValue();
        callbackContext.success((int)value);
    }

    private void pinMode(final int pin, final Pin.Mode mode, final CallbackContext callbackContext) {
        try {
            device.getPin(pin).setMode(mode);
            callbackContext.success();
        } catch (IOException e) {
            callbackContext.error(e.getMessage());
        }
    }

    private void digitalWrite(final int pin, final boolean value, final CallbackContext callbackContext) {
        try {
            device.getPin(pin).setValue(value? 1L: 0L);
            callbackContext.success();
        } catch (IOException e) {
            callbackContext.error(e.getMessage());
        }
    }

    private void analogWrite(final int pin, final int value, final CallbackContext callbackContext) {
        try {
            device.getPin(pin).setValue(value);
            callbackContext.success();
        } catch (IOException e) {
            callbackContext.error(e.getMessage());
        }
    }

    private void servoWrite(final int pin, final int angle, final CallbackContext callbackContext) {
        try {
            Pin devicePin = device.getPin(pin);
            if (devicePin.getMode() != Pin.Mode.SERVO) {
                devicePin.setMode(Pin.Mode.SERVO);
                devicePin.setServoMode(0, 180);
            }
            device.getPin(pin).setValue(angle);
            callbackContext.success();
        } catch (IOException e) {
            callbackContext.error(e.getMessage());
        }
    }

    private void onPinChanged(final int pin, final CallbackContext callbackContext) {
        try {
            Pin devicePin = device.getPin(pin);
            if (devicePin.getMode() == Pin.Mode.OUTPUT) {
                devicePin.setMode(Pin.Mode.INPUT);
            }
            devicePin.addEventListener(new PinEventListener() {
                @Override
                public void onModeChange(IOEvent ioEvent) {

                }

                @Override
                public void onValueChange(IOEvent ioEvent) {
                    PluginResult result = new PluginResult(PluginResult.Status.OK, ioEvent.getValue());
                    result.setKeepCallback(true);
                    callbackContext.sendPluginResult(result);
                }
            });
        } catch (IOException e) {
            callbackContext.error(e.getMessage());
        }
    }

    private int boolToInt(boolean b) {
        return b ? 1 : 0;
    }
    /*
        private void sendMessage(int command, JSONArray data, final CallbackContext callbackContext) {
            try {
                byte[] bytes = new byte[data.length()];
                for (int i = 0; i < data.length(); i++) {
                    bytes[i] = (byte) data.getInt(i);
                }
                device.sendMessage((byte) command, bytes);
                callbackContext.success();
            } catch (JSONException e) {
                callbackContext.error(e.getMessage());
            }
        }
    */

    private void onI2CEvent(final byte address, final int register, final byte messageLength, final CallbackContext callbackContext) {
        try {
            I2CDevice i2cDevice = device.getI2CDevice(address);
            i2cDevice.subscribe(new I2CListener() {
				@Override
                public int compareTo(I2CListener o) {
                    return hashCode() - o.hashCode();
                }
                
                @Override
                public void onReceive(I2CEvent i2CEvent) {
                    try {
                        JSONObject event = new JSONObject();
                        event.put("device", i2CEvent.getDevice().getAddress());
                        event.put("register", i2CEvent.getRegister());
                        event.put("data", i2CEvent.getData());
                        PluginResult result = new PluginResult(PluginResult.Status.OK, event);
                        result.setKeepCallback(true);
                        callbackContext.sendPluginResult(result);
                    } catch(JSONException e) {
                        callbackContext.error(e.getMessage());
                    }
                }
            });
            i2cDevice.startReceivingUpdates(register, messageLength);
        } catch (IOException e) {
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
