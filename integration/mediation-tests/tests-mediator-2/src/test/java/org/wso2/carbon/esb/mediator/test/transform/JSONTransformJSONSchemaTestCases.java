/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.esb.mediator.test.transform;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.test.utils.http.client.HttpRequestUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Testcases to test schema functionality of JSON Transform Mediator
 */
public class JSONTransformJSONSchemaTestCases extends ESBIntegrationTest {
    private JsonParser parser;
    private CarbonLogReader carbonLogReader;

    @BeforeClass(alwaysRun = true)
    public void deployArtifacts() throws Exception {
        super.init();
        carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();
        parser = new JsonParser();
    }

    @Test(groups = "wso2.esb", description = "Changing the JSON payload against a simple JSON Schema " +
            "stored in registry")
    public void testSimpleJsonSchema() throws Exception {
        String payload = "{\n" +
                "  \"fruit\"           : \"12345\",\n" +
                "  \"price\"           : \"7.5\",\n" +
                "  \"nestedObject\"    : {\"Lahiru\" :{\"age\":\"27\"},\"Nimal\" :{\"married\" :\"true\"}, \"Kamal\" " +
                ": {\"scores\": [\"24\",45,\"67\"]}},\n" +
                "  \"nestedArray\"     : [[12,\"23\",34],[\"true\",false],[\"Linking Park\",\"Coldplay\"]]\n" +
                "}";
        Map<String, String> httpHeaders = new HashMap<>();
        httpHeaders.put("Content-Type", "application/json");
        String expectedOutput = "{\"fruit\":\"12345\",\"price\":7.5,\"nestedObject\":{\"Lahiru\":{\"age\":\"27\"}," +
                "\"Nimal\":{\"married\":\"true\"},\"Kamal\":{\"scores\":[\"24\",45,\"67\"]}},\"nestedArray\":[[12," +
                "\"23\",34],[\"true\",false],[\"Linking Park\",\"Coldplay\"]]}";
        HttpResponse response = HttpRequestUtil.doPost(
                new URL(getProxyServiceURLHttp("transformMediatorSimple")), payload, httpHeaders);
        assertEqualJsonObjects(response.getData(), expectedOutput,
                "JSON to JSON transformation with a simple schema did not happen properly");
    }

    @Test(groups = "wso2.esb", description = "Changing XML payload to JSON payload with simple Schema " +
            "stored in registry")
    public void testSimpleXMLToJsonWithSchema() throws Exception {
        String payload = "<jsonObject>\n" +
                "    <fruit>12345</fruit>\n" +
                "    <price>7.5</price>\n" +
                "</jsonObject>";
        Map<String, String> httpHeaders = new HashMap<>();
        httpHeaders.put("Content-Type", "application/xml");
        String expectedOutput = "{\n" +
                "    \"fruit\": \"12345\",\n" +
                "    \"price\": 7.5\n" +
                "}";
        HttpResponse response = HttpRequestUtil.doPost(
                new URL(getProxyServiceURLHttp("transformMediatorSimpleXMLtoJSONWithSchema")),
                payload, httpHeaders);
        assertEqualJsonObjects(response.getData(), expectedOutput,
                "Simple XML to JSON transformation with a simple schema did not happen properly");
    }

    @Test(groups = "wso2.esb", description = "Changing XML payload to JSON payload with simple Schema " +
            "stored in Local Entry")
    public void testSimpleXMLToJsonWithSchemaLocalEntry() throws Exception {
        String payload = "<jsonObject>\n" +
                "    <fruit>12345</fruit>\n" +
                "    <price>7.5</price>\n" +
                "</jsonObject>";
        Map<String, String> httpHeaders = new HashMap<>();
        httpHeaders.put("Content-Type", "application/xml");
        String expectedOutput = "{\n" +
                "    \"fruit\": \"12345\",\n" +
                "    \"price\": 7.5\n" +
                "}";
        HttpResponse response = HttpRequestUtil.doPost(
                new URL(getProxyServiceURLHttp("transformMediatorSimpleXMLtoJSONWithSchemaLocalEntry")),
                payload, httpHeaders);
        assertEqualJsonObjects(response.getData(), expectedOutput,
                "Simple XML to JSON transformation with a simple schema did not happen properly");
    }

    @Test(groups = "wso2.esb", description = "Changing the JSON payload against a not existing registry schema")
    public void testNotExistingJsonSchema() throws Exception {
        carbonLogReader.clearLogs();
        String payload = "{\n" +
                "  \"fruit\"           : \"12345\",\n" +
                "  \"price\"           : \"7.5\",\n" +
                "  \"nestedObject\"    : {\"Lahiru\" :{\"age\":\"27\"},\"Nimal\" :{\"married\" :\"true\"}, \"Kamal\" " +
                ": {\"scores\": [\"24\",45,\"67\"]}},\n" +
                "  \"nestedArray\"     : [[12,\"23\",34],[\"true\",false],[\"Linking Park\",\"Coldplay\"]]\n" +
                "}";
        Map<String, String> httpHeaders = new HashMap<>();
        httpHeaders.put("Content-Type", "application/json");
        String expected_error = "Schema does not exist in the specified location : conf:/jsontransform_schema/simpleSchemaNotexisting.json";
        HttpRequestUtil.doPost(
                new URL(getProxyServiceURLHttp("transformMediatorNotExistingSchema")), payload, httpHeaders);
        assertTrue(carbonLogReader.checkForLog(expected_error, DEFAULT_TIMEOUT),
                "Expected error message not received when not existing schema is provided");
    }

    @Test(groups = "wso2.esb", description = "Changing the JSON payload against a empty JSON schema")
    public void testEmptyJsonSchema() throws Exception {
        carbonLogReader.clearLogs();
        String payload = "{\n" +
                "  \"fruit\"           : \"12345\",\n" +
                "  \"price\"           : \"7.5\",\n" +
                "  \"nestedObject\"    : {\"Lahiru\" :{\"age\":\"27\"},\"Nimal\" :{\"married\" :\"true\"}, \"Kamal\" " +
                ": {\"scores\": [\"24\",45,\"67\"]}},\n" +
                "  \"nestedArray\"     : [[12,\"23\",34],[\"true\",false],[\"Linking Park\",\"Coldplay\"]]\n" +
                "}";
        Map<String, String> httpHeaders = new HashMap<>();
        httpHeaders.put("Content-Type", "application/json");
        String expected_error = "Input json and schema should not be null";
        HttpRequestUtil.doPost(
                new URL(getProxyServiceURLHttp("transformMediatorEmptySchema")), payload, httpHeaders);
        assertTrue(carbonLogReader.checkForLog(expected_error, DEFAULT_TIMEOUT),
                "Expected error message not received when empty schema is passed");
    }

    @Test(groups = "wso2.esb", description = "Changing the JSON payload against a malformed JSON schema")
    public void testMalformedJsonSchema() throws Exception {
        carbonLogReader.clearLogs();
        String payload = "{\n" +
                "  \"fruit\"           : \"12345\",\n" +
                "  \"price\"           : \"7.5\",\n" +
                "  \"nestedObject\"    : {\"Lahiru\" :{\"age\":\"27\"},\"Nimal\" :{\"married\" :\"true\"}, \"Kamal\" " +
                ": {\"scores\": [\"24\",45,\"67\"]}},\n" +
                "  \"nestedArray\"     : [[12,\"23\",34],[\"true\",false],[\"Linking Park\",\"Coldplay\"]]\n" +
                "}";
        Map<String, String> httpHeaders = new HashMap<>();
        httpHeaders.put("Content-Type", "application/json");
        String expected_error = "Invalid JSON schema";
        HttpRequestUtil.doPost(
                new URL(getProxyServiceURLHttp("transformMediatorMalformedSchema")), payload, httpHeaders);
        assertTrue(carbonLogReader.checkForLog(expected_error, DEFAULT_TIMEOUT),
                "Expected error message not received when malformed schema is passed");
    }

    @Test(groups = "wso2.esb", description = "Changing the JSON payload against JSON schema without type attribute")
    public void testJsonSchemaWithoutType() throws Exception {
        carbonLogReader.clearLogs();
        String payload = "{\n" +
                "  \"fruit\"           : \"12345\",\n" +
                "  \"price\"           : \"7.5\",\n" +
                "  \"nestedObject\"    : {\"Lahiru\" :{\"age\":\"27\"},\"Nimal\" :{\"married\" :\"true\"}, \"Kamal\" " +
                ": {\"scores\": [\"24\",45,\"67\"]}},\n" +
                "  \"nestedArray\"     : [[12,\"23\",34],[\"true\",false],[\"Linking Park\",\"Coldplay\"]]\n" +
                "}";
        Map<String, String> httpHeaders = new HashMap<>();
        httpHeaders.put("Content-Type", "application/json");
        String expected_error = "JSON schema should contain a type declaration";
        HttpRequestUtil.doPost(
                new URL(getProxyServiceURLHttp("transformMediatorSchemaWithoutType")), payload, httpHeaders);
        assertTrue(carbonLogReader.checkForLog(expected_error, DEFAULT_TIMEOUT),
                "Expected error message not received when type is not present in schema");
    }

    private void assertEqualJsonObjects(String json1, String json2, String errorMessage) {
        JsonElement element1 = parser.parse(json1);
        JsonElement element2 = parser.parse(json2);
        assertEquals(element1, element2, errorMessage);
    }
}
