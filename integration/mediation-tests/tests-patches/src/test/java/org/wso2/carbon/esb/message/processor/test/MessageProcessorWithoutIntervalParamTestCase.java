/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.esb.message.processor.test;

import org.awaitility.Awaitility;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.annotations.ExecutionEnvironment;
import org.wso2.carbon.automation.engine.annotations.SetEnvironment;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.servers.WireMonitorServer;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class MessageProcessorWithoutIntervalParamTestCase extends ESBIntegrationTest {

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {

        super.init();
    }

    /**
     * Create a message processor which processes messages that are in a In memory message store without the interval parameter
     */
    @SetEnvironment(executionEnvironments = {ExecutionEnvironment.STANDALONE})
    @Test(groups = "wso2.esb")
    public void testForwardingWithCronExpression() {

        //Setting up Wire Monitor Server
        WireMonitorServer wireServer = new WireMonitorServer(9500);
        wireServer.start();

        try {
            axis2Client.sendSimpleStockQuoteRequest(
                    getProxyServiceURLHttp("MessageProcessorWithoutIntervalParamTestProxy"), null, "WSO2");
            Assert.fail("Unexpected reply received !!!");
        } catch (Exception e) {
            // Axis Fault Expected
        }

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(isMessageReceivedByEndpoint(wireServer.getCapturedMessage()));
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {

        super.cleanup();
    }

    public static Callable<Boolean> isMessageReceivedByEndpoint(String serverResponse) {

        return new Callable<Boolean>() {
            @Override
            public Boolean call() {

                return serverResponse.contains("WSO2");
            }
        };
    }
}
