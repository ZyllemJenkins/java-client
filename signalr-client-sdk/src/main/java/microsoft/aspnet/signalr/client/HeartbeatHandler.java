/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
See License.txt in the project root for license information.
*/

package microsoft.aspnet.signalr.client;

import com.google.gson.JsonElement;

/**
 * Interface to define a handler for a "Empty Message received to keep the connection alive" event
 */
public interface HeartbeatHandler {
    /**
     * Handles an incoming message
     */
    void onHeartbeatReceived();
}
