/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
See License.txt in the project root for license information.
*/

package microsoft.aspnet.signalr.client.transport;


import com.google.gson.Gson;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.util.Charsetfunctions;

import java.net.URI;

import microsoft.aspnet.signalr.client.ConnectionBase;
import microsoft.aspnet.signalr.client.LogLevel;
import microsoft.aspnet.signalr.client.Logger;
import microsoft.aspnet.signalr.client.SignalRFuture;
import microsoft.aspnet.signalr.client.UpdateableCancellableFuture;
import microsoft.aspnet.signalr.client.http.HttpConnection;

/**
 * Implements the WebsocketTransport for the Java SignalR library
 * Created by stas on 07/07/14.
 */
public class WebsocketTransport extends HttpClientTransport {

    private String mPrefix;
    private static final Gson gson = new Gson();
    WebSocketClient mWebSocketClient;
    private UpdateableCancellableFuture<Void> mConnectionFuture;

    public WebsocketTransport(Logger logger) {
        super(logger);
    }

    public WebsocketTransport(Logger logger, HttpConnection httpConnection) {
        super(logger, httpConnection);
    }

    @Override
    public String getName() {
        return "webSockets";
    }

    @Override
    public boolean supportKeepAlive() {
        return true;
    }

    @Override
    public SignalRFuture<Void> start(ConnectionBase connection, ConnectionType connectionType, final DataResultCallback callback){

        String url = connection.getUrl() + (connectionType == ConnectionType.InitialConnection ? "connect" : "reconnect")
                + TransportHelper.getReceiveQueryString(this, connection);;

        mConnectionFuture = new UpdateableCancellableFuture<Void>(null);

        url = url.replace("http://","ws://");
        url = url.replace("https://","wss://");

        URI uri = URI.create(url);

        mWebSocketClient = new WebSocketClient(uri, connection.getHeaders()) {

            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                mConnectionFuture.setResult(null);
            }

            @Override
            public void onMessage(String s) {
                callback.onData(s);
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                log("onClose: "+s, LogLevel.Information);
                if (!mConnectionFuture.isCancelled() && !mConnectionFuture.errorWasTriggered()) {
                    mConnectionFuture.triggerError(new RuntimeException(s));
                }
            }

            @Override
            public void onError(Exception e) {
                log("onError: "+e.getMessage(), LogLevel.Critical);
                mWebSocketClient.close();
                if (!mConnectionFuture.isCancelled() && !mConnectionFuture.errorWasTriggered()) {
                    mConnectionFuture.triggerError(e);
                }
            }

            @Override
            public void onFragment(Framedata frame) {
                try {
                    String decodedString = Charsetfunctions.stringUtf8(frame.getPayloadData());

                    if(decodedString.equals("]}")){
                        return;
                    }

                    if(decodedString.endsWith(":[") || null == mPrefix){
                        mPrefix = decodedString;
                        return;
                    }

                    String simpleConcatenate = mPrefix + decodedString;

                    if(isJSONValid(simpleConcatenate)){
                        onMessage(simpleConcatenate);
                    }else{
                        String extendedConcatenate = simpleConcatenate + "]}";
                        if (isJSONValid(extendedConcatenate)) {
                            onMessage(extendedConcatenate);
                        } else {
                            log("invalid json received:" + decodedString, LogLevel.Critical);
                        }
                    }
                } catch (InvalidDataException e) {
                    e.printStackTrace();
                    if (!mConnectionFuture.isCancelled() && !mConnectionFuture.errorWasTriggered()) {
                        mConnectionFuture.triggerError(e);
                    }
                }
            }
        };

        log("Initiating connect request", LogLevel.Information);
        mWebSocketClient.connect();

        connection.closed(new Runnable() {
            @Override
            public void run() {
                mWebSocketClient.close();
            }
        });

        return mConnectionFuture;
    }

    @Override
    public SignalRFuture<Void> send(ConnectionBase connection, String data, DataResultCallback callback) {
        mWebSocketClient.send(data);
        return new UpdateableCancellableFuture<Void>(null);
    }

    private boolean isJSONValid(String test){
        try {
            gson.fromJson(test, Object.class);
            return true;
        } catch(com.google.gson.JsonSyntaxException ex) {
            return false;
        }
    }

}