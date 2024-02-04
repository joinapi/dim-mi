/*
 *Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *WSO2 Inc. licenses this file to you under the Apache License,
 *Version 2.0 (the "License"); you may not use this file except
 *in compliance with the License.
 *You may obtain a copy of the License at
 *
 *http://www.apache.org/licenses/LICENSE-2.0
 *
 *Unless required by applicable law or agreed to in writing,
 *software distributed under the License is distributed on an
 *"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *KIND, either express or implied.  See the License for the
 *specific language governing permissions and limitations
 *under the License.
 */
package org.wso2.carbon.esb.mediator.test.sequence;

import org.apache.axis2.AxisFault;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.ESBTestConstant;

import static org.testng.Assert.assertEquals;

public class SequenceIntegrationNonExistingSequenceNegativeTestCase extends ESBIntegrationTest {

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {
        super.init();
    }

    // give a non existing sequence and handle the exception..
    @Test(groups = "wso2.esb", description = "Give a non-existing sequence name as the '" + "Referring"
            + " sequence' when using Static key.")
    public void testEnrichMediator() throws Exception {
        try {
            axis2Client
                    .sendSimpleStockQuoteRequest(getProxyServiceURLHttp("sequenceMediatorNonExistingSequenceTestProxy"),
                            null, "WSO2");
        } catch (AxisFault e) {

            assertEquals(e.getMessage(), ESBTestConstant.INCOMING_MESSAGE_IS_NULL, "Invalid exception");

        }

    }

    @AfterClass(alwaysRun = true)
    public void close() throws Exception {
        super.cleanup();
    }
}

