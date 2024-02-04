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
package org.wso2.ei.dataservice.integration.test.services;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.test.utils.axis2client.AxisServiceClient;
import org.wso2.ei.dataservice.integration.test.DSSIntegrationTest;
import org.wso2.ei.dataservice.integration.test.odata.ODataTestUtils;

import java.util.HashMap;
import java.util.Map;
import javax.xml.xpath.XPathExpressionException;

import static org.wso2.ei.dataservice.integration.test.odata.ODataTestUtils.sendGET;

public class NestedQueryTestCase extends DSSIntegrationTest {
    private static final Log log = LogFactory.getLog(NestedQueryTestCase.class);

    private final OMFactory fac = OMAbstractFactory.getOMFactory();
    private final OMNamespace omNs = fac.createOMNamespace("http://ws.wso2.org/dataservice/samples", "ns1");
    private final String serviceName = "NestedQueryTest";

    @BeforeClass(alwaysRun = true)
    public void serviceDeployment() throws Exception {
        super.init();
    }

    @Test(groups = { "wso2.dss" })
    public void getCustomerOrdersNestedQuery() throws AxisFault, XPathExpressionException {
        for (int i = 1; i < 6; i++) {
            getCustomerOrders();
        }
        log.info("Customer Orders Nested Query verified");
    }

    @Test(groups = { "wso2.dss" })
    public void listOfficeNestedQueryOperation() throws AxisFault, XPathExpressionException {
        for (int i = 1; i < 6; i++) {
            getOffices();
        }
        log.info("List Office Nested Query verified");
    }

    @Test(groups = { "wso2.dss" }, description = "nested query empty result test")
    public void testNestedQueryEmptyResultJson() throws Exception {
        String endpoint = getServiceUrlHttp(serviceName) + "/listOffices";
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        Object[] response = sendGET(endpoint, headers);
        Assert.assertEquals(response[0], ODataTestUtils.OK, "Invalid status received");
        Assert.assertTrue(response[1].toString().contains("\"Employees\":{}"), "Invalid result received");
    }

    private void getCustomerOrders() throws AxisFault, XPathExpressionException {
        OMElement payload = fac.createOMElement("customerOrders", omNs);

        OMElement result = new AxisServiceClient()
                .sendReceive(payload, getServiceUrlHttp(serviceName), "customerOrders");
        Assert.assertNotNull(result, "Response message null ");
        log.debug(result);
        Assert.assertTrue(result.toString().contains("<Order><Order-number>"), "Expected not same");
        Assert.assertTrue(result.toString().contains("<Customer><Name>"), "Expected not same");
        Assert.assertTrue(result.toString().contains("</Customer>"), "Expected not same");
    }

    private void getOffices() throws AxisFault, XPathExpressionException {
        OMElement payload = fac.createOMElement("listOffices", omNs);

        OMElement result = new AxisServiceClient().sendReceive(payload, getServiceUrlHttp(serviceName), "listOffices");
        Assert.assertNotNull(result, "Response message null ");
        log.debug(result);
        Assert.assertTrue(result.toString().contains("<Office><officeCode>1</officeCode>"), "Expected not same");
        Assert.assertTrue(result.toString().contains("<Employees><Employee><employeeNumber>"), "Expected not same");
    }

}
