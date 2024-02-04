/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.esb.nhttp.transport;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.wso2.carbon.automation.engine.annotations.ExecutionEnvironment;
import org.wso2.carbon.automation.engine.annotations.SetEnvironment;
import org.wso2.carbon.automation.engine.context.AutomationContext;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.common.ServerConfigurationManager;

import java.io.File;
import java.nio.file.Paths;

/**
 * NhttpBaseTestCase replaces the default axis2.xml with the one at /artifacts/ESB/nhttp/transport/axis2.xml which
 * enables nhttp for the http transport at the beginning of the execution of nhttp tests.
 */
public class NhttpBaseTestCase extends ESBIntegrationTest {
    private ServerConfigurationManager serverConfigurationManager;

    @SetEnvironment(executionEnvironments = { ExecutionEnvironment.STANDALONE })
    @BeforeTest(alwaysRun = true)
    public void startJMSBrokerAndConfigureESB() throws Exception {
        super.init();
        serverConfigurationManager = new ServerConfigurationManager(
                new AutomationContext("ESB", TestUserMode.SUPER_TENANT_ADMIN));
        serverConfigurationManager
                .applyMIConfiguration(Paths.get(getESBResourceLocation(), "nhttp", "transport", "axis2.xml").toFile());
        serverConfigurationManager.applyMIConfigurationWithRestart(new File(
                getESBResourceLocation() + File.separator + "nhttp" + File.separator + "transport" + File.separator
                        + "json" + File.separator + "deployment.toml"));

    }

    @AfterTest(alwaysRun = true)
    public void close() throws Exception {
        serverConfigurationManager.restoreToLastMIConfiguration();
    }
}



