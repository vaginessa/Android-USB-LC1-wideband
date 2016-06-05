package com.example.delux.testusbserial1;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.*;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

interface StatusChangeListener {
    public void onStatusChange(Object o, String status);
}

class LC1Parser {
    int pos = 0;
    int cnt = 0;
    byte[] pkt = new byte[4];
    StatusChangeListener listener;

    public boolean feedData(byte b) {
        cnt++;
        pkt[pos] = b;
        String status = "";
        switch( pos ) {
            case 0:
                if ((b & 0xE2) == 0x42) pos++;
                break;
            case 1:
                if ((b & 0x80) == 0x00) pos++;
                else pos = 0;
                break;
            case 2:
                if ((b & 0xC0) == 0x00) pos++;
                else pos = 0;
                break;
            case 3:
                if ((b & 0x80) == 0x00) pos++;
                else pos = 0;
                break;
            default:
                pos = 0;
        }
        if( pos == 4 ) {
            int func = (pkt[0] >> 2) & 7;
            int af = pkt[1] | ((pkt[0] & 0x01) << 7);
            int l = pkt[3] | (pkt[2] << 7);
            switch(func) {
                case 1: status = String.format("AFR=%.1f",l/10.0); break;
                default: status = String.format("FUNC=%d",func);
            }
            if(listener != null) listener.onStatusChange(this,String.format("Rcv %6d %s", cnt, status));
            pos = 0;
        }
        return true;
    };
}

public class MainActivity extends AppCompatActivity implements StatusChangeListener {

    boolean isOpen = false;
    boolean canOpen = false;
    UsbSerialPort port = null;
    private Timer tmr;
    private TimerTask ttask;
    LC1Parser lc1parser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Timer t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        EnumerateSerialPorts();
                    }
                });
            }
        }, 500);
    }

    @Override
    public void onStatusChange(Object o, final String status) {
        final String sts = status;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv = (TextView) findViewById(R.id.textView);
                if (tv != null) {
                    tv.setText(status);
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    class SerialReceiver extends TimerTask {
        public void run() {
            try {
                byte[] rd = new byte[256];
                int cnt = port.read(rd, 10);
                for (int i = 0; i < cnt; i++) {
                    byte b = rd[i];
                    lc1parser.feedData(b);
                }
                //port.write(new byte[] {1,2,3,4,5,6},250);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void EnumerateSerialPorts() {
        TextView tv = (TextView) findViewById(R.id.textView);
        Button btOpen = (Button) findViewById(R.id.btOpenClose);
        btOpen.setEnabled(false);
        tv.setText("GabbaGabbaHey - you should not see this if USB port is available.");
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        } else {
            StringBuilder sb = new StringBuilder();
            for (UsbSerialDriver s :
                    availableDrivers) {
                List<UsbSerialPort> p = s.getPorts();
                sb.append(s.toString() + "\n");
                for (UsbSerialPort pp : p) {
                    canOpen = true;
                    port = pp;
                    btOpen.setEnabled(true);
                    sb.append(">> " + pp.toString() + "\n");
                }
            }
            tv.setText(sb.toString());
        }
    }

    protected void onBtnClicked(View v) {
        TextView tv = (TextView) findViewById(R.id.textView);
        Button btOpen = (Button) findViewById(R.id.btOpenClose);
        if (v.getId() == R.id.btOpenClose) {
            if (isOpen) {
                //close
                try {
                    tmr.cancel();
                    Thread.sleep(50);
                    port.close();
                    tv.setText("Closed. Easy :)");
                } catch (Exception e) {
                    e.printStackTrace();
                    tv.setText("Error closing.");
                }
                btOpen.setText("Open");
                isOpen = false;
            } else {
                //open
                UsbManager um = (UsbManager) getSystemService(Context.USB_SERVICE);
                UsbDeviceConnection udc = um.openDevice(port.getDriver().getDevice());
                try {
                    port.open(udc);
                    // innovate LC-1 runs at 19200 8n1
                    port.setParameters(19200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                    tmr = new Timer();
                    ttask = new SerialReceiver();
                    lc1parser.listener = this;
                    tmr.schedule(ttask, 100, 500);
                    btOpen.setText("Close");
                    tv.setText("Port open. Easy :)");
                    isOpen = true;
                } catch (IOException e) {
                    tv.setText("Error opening.");
                    e.printStackTrace();
                }
            }
        }
        if (v.getId() == R.id.btEnum) {
            EnumerateSerialPorts();
        }
    }

}
