package dev.as.cameracolor;

import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.Locale;

import android.util.Log;

import org.apache.commons.net.telnet.TelnetClient;

public class TelnetConnection {
    private TelnetClient client = null;
    private final String SERVER_IP;
    private final int SERVERPORT;

    public TelnetConnection(String ip, int port) {
        SERVER_IP = ip;
        SERVERPORT = port;
        client = new TelnetClient();
    }

    public void connect() throws IOException{
        try {
            client.connect(SERVER_IP, SERVERPORT);
        } catch (SocketException ex) {
            throw new SocketException("Connection error...");
        }
    }

    public BufferedInputStream getReader(){
        return new BufferedInputStream(client.getInputStream());
    }

    public OutputStream getOutput(){
        return client.getOutputStream();
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    //exits telnet session and cleans up the telnet console
    public boolean disconnect() {
        try {
            client.disconnect();
        } catch (IOException e) {
            Log.e("Couldn't disconnect",e.getMessage());
            return false;
        }
        return true;
    }

    public TelnetClient getConnection(){
        return client;
    }
}
