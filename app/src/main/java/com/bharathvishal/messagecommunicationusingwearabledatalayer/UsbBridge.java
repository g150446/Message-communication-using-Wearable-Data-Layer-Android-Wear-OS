package com.bharathvishal.messagecommunicationusingwearabledatalayer;

import android.content.Context;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import androidx.lifecycle.ViewModel;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class UsbBridge {
    private static final int READ_WAIT_MILLIS = 100;

    String tag="usbbridge";
    public String logtxt="default";
    UsbSerialPort port;
    UsbBridge(UsbManager man) throws IOException {
        UsbManager manager = man;
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            Log.d(tag,"empty");
            return;
        }else{
            Log.d(tag,"not emp");
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
            Log.d(tag,"con null");
            return;
        }else{
            Log.d(tag,"con not null");
        }

        port = driver.getPorts().get(0); // Most devices have just one port (port 0)
        port.open(connection);
        port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        logtxt="connect ok";
    }

    public String readUsb(){
        String ret="";
        try {
            byte[] buffer = new byte[8192];
            int len = port.read(buffer, READ_WAIT_MILLIS);
            if(len>0) {
                ret = new String(buffer);
            }else {
                ret = "" + len;
            }
        } catch (IOException e) {
            // when using read with timeout, USB bulkTransfer returns -1 on timeout _and_ errors
            // like connection loss, so there is typically no exception thrown here on error
            ret=e.toString();

        }

        return ret;
    }
}
