/*
 * Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.esb.integration.common.utils.servers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.TimeUnit;

/**
 * This class can be used to capture wire messages
 */
public class WireMonitorServer {

    private static final int TIMEOUT_VALUE = 30000;
    private static Log log = LogFactory.getLog(WireMonitorServer.class);
    boolean isFinished = false;
    String response;
    int port;

    /**
     * Start listening to a port
     *
     * @param port to be listened
     */
    public WireMonitorServer(int port) {
        this.port = port;
    }

    public void start() {
        response = "";
        WireMonitor wireMonitor = new WireMonitor(port, this);
        wireMonitor.start();
        long startTime = System.currentTimeMillis();

        while (!wireMonitor.isStarted() && (System.currentTimeMillis() - startTime) < TIMEOUT_VALUE) {
            try {
                //wait until the wire monitor is started
                TimeUnit.MILLISECONDS.sleep(100);
                log.info("Waiting for wire monitor to start...");
            } catch (InterruptedException e) {
                //Continue looping until the server is started
            }
        }
    }

    /**
     * Wait until response is received and returns
     *
     * @return will return null if response is not received
     */
    public String getCapturedMessage() {
        Long time = System.currentTimeMillis();
        while (!isFinished) {
            // If wire monitor is not responding than 2min this will continue
            if (System.currentTimeMillis() > (time + TIMEOUT_VALUE)) {
                break;
            }
            try {
                //wait to avoid busy waiting
                Thread.sleep(500);
            } catch (InterruptedException e) {
                //ignore
                log.warn("InterruptedException while waiting", e);
            }
        }
        return response;
    }
}
