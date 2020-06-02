package com.example.scrolldemo;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import android.app.Activity;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class MainActivity extends Activity {

    private int lastX = 0;
    private GraphView graph;
    private LineGraphSeries<DataPoint> series;
    public double v = 0.0;

    public final String ACTION_USB_PERMISSION = "com.hariharan.arduinousb.USB_PERMISSION";
    Button ButtonStart, ButtonSend, ButtonClear, ButtonStop;
    TextView terminal;
    EditText EditText;
    UsbManager usbManager;
    UsbDevice device;
    UsbSerialDevice serialPort;
    UsbDeviceConnection connection;


    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted =
                        intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    connection = usbManager.openDevice(device);
                    serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                    if (serialPort != null) {
                        if (serialPort.open()) { //Set Serial Connection Parameters.
                            setUiEnabled(true); //Enable Buttons in UI
                            serialPort.setBaudRate(115200);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF); // original
                            serialPort.read(mCallback); //
                            tvAppend(terminal,"Serial Connection Opened!\n");

                        } else {
                            Log.d("SERIAL", "PORT NOT OPEN");
                        }
                    } else {
                        Log.d("SERIAL", "PORT IS NULL");
                    }
                } else {
                    Log.d("SERIAL", "PERM NOT GRANTED");
                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                onClickStart(ButtonStart);
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                onClickStop(ButtonStop);
            }
        };
    };

    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
        //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            String data = null;
            try {
                data = new String(arg0, "UTF-8");

                try {
                    Integer.parseInt(data); // tratar problemas caso não int recebifo
                    int p = Integer.parseInt(data);
                    //parseIntFast(data);
                    //int p = parseIntFast(data);

                    if (lastX > 200) { // clear por thread gerava conflito de resetar dados da serie
                        lastX = 0; //enquanto ele estava tentando ser acrescentado pelo OnReceiveData, suportou 6min
                        series.resetData(new DataPoint[] { //dentro do try 9min+
                                new DataPoint(0, 0) // last lib GraphView 4.2.1 solve problem
                        });
                        //terminal.setText(""); // clear terminal
                    } else {
                        //resetDataPoints()
                        double x = p/1000.0;
                        series.appendData(new DataPoint(lastX++, x), true, 200);
                    }

                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    tvAppend(terminal, "error passando pra int"); //nenhum problema
                }

                    /*data.concat("/n");
                    tvAppend(terminal, data);
                    tvAppend(terminal, "  ");*/
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // we get graph view instance
        graph = (GraphView) findViewById(R.id.graph);
        // data
        series = new LineGraphSeries<DataPoint>();
        graph.addSeries(series);

        // customize a little bit viewport
        Viewport viewport = graph.getViewport();
        viewport.setYAxisBoundsManual(true);
        viewport.setMinY(0);
        viewport.setMaxY(40);
        //sample period
        viewport.setXAxisBoundsManual(true);
        viewport.setMinX(0);
        viewport.setMaxX(200);
        //viewport.setScrollable(true);

        graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);     //retirar numeros abaixo do grafico
        //graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);  // retirar grid

        // usb communication
        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);
        ButtonStart = (Button) findViewById(R.id.ButtonStart);
        ButtonSend = (Button) findViewById(R.id.ButtonSend);
        ButtonClear = (Button) findViewById(R.id.ButtonClear);
        ButtonStop = (Button) findViewById(R.id.ButtonStop);
        EditText = (EditText) findViewById(R.id.EditText);
        terminal = (TextView) findViewById(R.id.terminal);
        setUiEnabled(false);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);

    }

    public void setUiEnabled(boolean bool) {
        ButtonStart.setEnabled(!bool);
        ButtonSend.setEnabled(bool);
        ButtonStop.setEnabled(bool);
        terminal.setEnabled(bool);

    }

    public void onClickStart(View view) {

        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                if (deviceVID == 0x2341)//Arduino Vendor ID
                {
                    PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(device, pi);
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }

                if (!keep)
                    break;
            }
        }
    }

    public void onClickSend(View view) {
        String string = terminal.getText().toString();
        serialPort.write(string.getBytes());
        tvAppend(terminal, "\nData Sent : " + string + "\n");

    }

    public void onClickStop(View view) {
        setUiEnabled(false);
        serialPort.close();
        tvAppend(terminal,"\nSerial Connection Closed! \n");

    }

    public void onClickClear(View view) {
        terminal.setText(" ");
    }

    private void tvAppend(TextView tv, CharSequence text) {
        final TextView ftv = tv;
        final CharSequence ftext = text;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ftv.append(ftext);
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
    }


    //way faster to get int from string
    /*public static int parseIntFast(String value) {
        // Loop over the string, adjusting ASCII value to integer value.
        // ... Multiply previous result by 10 as we progress.
        int result = 0;
        for (int i = 0; i < value.length(); i++) {
            result = 10*result + (value.charAt(i) - 48);
        }
        return result;
    }*/


    // simulate real time with thread that append data to the graph
            /*new Thread(new Runnable() {
                @Override
                public void run() {
                    // we add 500 new entries
                    // implementar forma que deixe o gráfico continuo
                    for (int i = 0; i < 350; i++) {
                        runOnUiThread(new Runnable() {

                        //@Override
                        //public void run () {
                            //addEntry();
                        //}
                        //});

                    // sleep to slow down the add of entries
                    /*try {
                        Thread.sleep(40); // sem o runOnUiThread acima se baixarmos osleep pode ocorrer erro
                    } catch (InterruptedException e) {
                        // manage error ...
                    }
                    }// FOR or WHILE
                    System.out.println("thread foi terminada");
                }
            }).start();

            // reset graph
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(12000);
                    } catch (InterruptedException e) {
                        // manage error ...
                    }
                    System.out.println("thread foi terminada");

                    // clear graph
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            //graph.redrawAll();
                            //graphView.removeAllSeries(); //not working
                            recreate(); // check later if this function will gerate issue
                            //series.resetData(ClearData());
                        }
                    });
                }
            }).start();*/


}
