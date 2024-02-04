/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.management.apis;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.impl.llom.OMTextImpl;
import org.apache.axis2.AxisFault;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.synapse.MessageContext;
import org.apache.synapse.aspects.AspectConfiguration;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.rest.RESTConstants;
import org.json.JSONArray;
import org.json.JSONObject;
import org.ops4j.pax.logging.PaxLoggingConstants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.wso2.micro.core.util.AuditLogger;
import org.wso2.micro.integrator.initializer.dashboard.ArtifactUpdateListener;
import org.wso2.micro.integrator.initializer.utils.ConfigurationHolder;
import org.wso2.micro.integrator.registry.MicroIntegratorRegistry;
import org.wso2.micro.integrator.security.MicroIntegratorSecurityUtils;
import org.wso2.micro.integrator.security.user.api.RealmConfiguration;
import org.wso2.micro.integrator.security.user.api.UserStoreException;
import org.wso2.micro.integrator.security.user.api.UserStoreManager;
import org.wso2.micro.integrator.security.user.core.UserCoreConstants;
import org.wso2.micro.integrator.security.user.core.common.AbstractUserStoreManager;
import org.wso2.micro.service.mgt.ServiceAdmin;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import static org.wso2.micro.integrator.management.apis.Constants.BAD_REQUEST;
import static org.wso2.micro.integrator.management.apis.Constants.CONFIGURATION_REGISTRY_PATH;
import static org.wso2.micro.integrator.management.apis.Constants.CONFIGURATION_REGISTRY_PREFIX;
import static org.wso2.micro.integrator.management.apis.Constants.FILE;
import static org.wso2.micro.integrator.management.apis.Constants.FORBIDDEN;
import static org.wso2.micro.integrator.management.apis.Constants.GOVERNANCE_REGISTRY_PATH;
import static org.wso2.micro.integrator.management.apis.Constants.GOVERNANCE_REGISTRY_PREFIX;
import static org.wso2.micro.integrator.management.apis.Constants.INTERNAL_SERVER_ERROR;
import static org.wso2.micro.integrator.management.apis.Constants.LOCAL_REGISTRY_PATH;
import static org.wso2.micro.integrator.management.apis.Constants.LOCAL_REGISTRY_PREFIX;
import static org.wso2.micro.integrator.management.apis.Constants.REGISTRY_ROOT_PATH;
import static org.wso2.micro.integrator.management.apis.Constants.USERNAME_PROPERTY;
import static org.wso2.micro.integrator.registry.MicroIntegratorRegistryConstants.URL_SEPARATOR;

public class Utils {

    private static final Log LOG = LogFactory.getLog(Utils.class);

    public static String getQueryParameter(MessageContext messageContext, String key) {

        if (Objects.nonNull(messageContext.getProperty(RESTConstants.REST_QUERY_PARAM_PREFIX + key))) {
            return messageContext.getProperty(RESTConstants.REST_QUERY_PARAM_PREFIX + key).toString();
        }
        return null;
    }

    /**
     * Extracts the value set for the patch parameter with the given key.
     *
     * @param messageContext message context to extract the parameter from
     * @param key            the key defined in the uri template
     * @return the resolved value from the url. Returns null if not present.
     */
    public static String getPathParameter(MessageContext messageContext, String key){
        if (Objects.nonNull(messageContext.getProperty(RESTConstants.REST_URI_VARIABLE_PREFIX + key))) {
            return messageContext.getProperty(RESTConstants.REST_URI_VARIABLE_PREFIX + key).toString();
        }
        return null;
    }

    /**
     * Returns the string representation of a property set in the message context
     *
     * @param messageContext the message context to extract the property from
     * @param key            the key of the property
     * @return the string if a non empty value has been set. Returns null, if the property is not present or if the
     * value is empty.
     */
    public static String getStringPropertyFromMessageContext(MessageContext messageContext, String key) {
        Object propertyObject = messageContext.getProperty(key);
        if (Objects.nonNull(propertyObject)) {
            String propertyString = propertyObject.toString();
            if (!StringUtils.isEmpty(propertyString)) {
                return propertyString;
            }
        }
        return null;
    }

    /**
     * Validates if the request is authenticated.
     *
     * @param messageContext the message context to extract the property from
     * @return true if the user is authenticated.
     */
    public static boolean isUserAuthenticated(MessageContext messageContext) {
        return !Objects.isNull(getStringPropertyFromMessageContext(messageContext, USERNAME_PROPERTY));
    }

    public static void setJsonPayLoad(org.apache.axis2.context.MessageContext axis2MessageContext, Object jsonObject) {

        try {
            JsonUtil.getNewJsonPayload(axis2MessageContext, jsonObject.toString(), true, true);
        } catch (AxisFault axisFault) {
            axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, Constants.INTERNAL_SERVER_ERROR);
            LOG.error("Error occurred while setting json payload", axisFault);
        }

        if (axis2MessageContext.getConfigurationContext().getAxisConfiguration().
                getMessageFormatter(Constants.MANAGEMENT_APPLICATION_JSON) != null) {
            axis2MessageContext.setProperty("messageType", Constants.MANAGEMENT_APPLICATION_JSON);
        } else {
            axis2MessageContext.setProperty("messageType", Constants.HEADER_VALUE_APPLICATION_JSON);
        }

        axis2MessageContext.setProperty("ContentType", Constants.HEADER_VALUE_APPLICATION_JSON);
        axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);
    }

    static JSONObject handleTracing(String performedBy, String type, String artifactType, JSONObject info,
                                    AspectConfiguration config, String artifactName,
                                    org.apache.axis2.context.MessageContext axisMsgCtx) {

        JSONObject payload = new JSONObject(JsonUtil.jsonPayloadToString(axisMsgCtx));
        JSONObject response = new JSONObject();
        String msg;
        if (payload.has(Constants.TRACE)) {
            String traceState = payload.get(Constants.TRACE).toString();
            if (Constants.ENABLE.equalsIgnoreCase(traceState)) {
                config.enableTracing();
                msg = "Enabled tracing for ('" + artifactName + "')";
                response.put(Constants.MESSAGE, msg);
                AuditLogger.logAuditMessage(performedBy, type, Constants.AUDIT_LOG_ACTION_ENABLE, info);
                ArtifactUpdateListener.addToUpdatedArtifactsQueue(artifactType, artifactName);
            } else if (Constants.DISABLE.equalsIgnoreCase(traceState)) {
                config.disableTracing();
                msg = "Disabled tracing for ('" + artifactName + "')";
                response.put(Constants.MESSAGE, msg);
                AuditLogger.logAuditMessage(performedBy, type, Constants.AUDIT_LOG_ACTION_DISABLED, info);
                ArtifactUpdateListener.addToUpdatedArtifactsQueue(artifactType, artifactName);
            } else {
                msg = "Invalid value for state " + Constants.TRACE;
                response = createJsonError(msg, axisMsgCtx, Constants.BAD_REQUEST);
            }
        } else {
            msg = "Missing attribute " + Constants.TRACE + " in payload";
            response = createJsonError(msg, axisMsgCtx, Constants.BAD_REQUEST);
        }
        LOG.info(msg);
        return response;
    }

    public static JSONObject createJSONList(int count) {

        JSONObject jsonBody = new JSONObject();
        JSONArray list = new JSONArray();
        jsonBody.put(Constants.COUNT, count);
        jsonBody.put(Constants.LIST, list);
        return jsonBody;
    }

    /**
     * Create a json list to list down carbon applications
     * @param activeCount active cApp count
     * @param faultyCount faulty cApp count
     * @return json object
     */
    public static JSONObject createCAppJSONList(int activeCount, int faultyCount) {

        JSONObject jsonBody = new JSONObject();
        jsonBody.put(Constants.TOTAL_COUNT, activeCount + faultyCount);
        jsonBody.put(Constants.ACTIVE_COUNT, activeCount);
        jsonBody.put(Constants.ACTIVE_LIST, new JSONArray());
        jsonBody.put(Constants.FAULTY_COUNT, faultyCount);
        jsonBody.put(Constants.FAULTY_LIST, new JSONArray());
        return jsonBody;
    }

    /**
     * Creates a json response according to the message provided and sets the provided HTTP code.
     *
     * @param message             the error response to be sent to the client
     * @param exception           the exception to be logged on the server side. The error response will be extracted
     *                            from the
     *                            exception.
     * @param axis2MessageContext message context to set the json payload to
     * @param statusCode          the HTTP status code to be returned
     * @return error response
     */
    static JSONObject createJsonError(String message, Throwable exception,
                                      org.apache.axis2.context.MessageContext axis2MessageContext, String statusCode) {
        LOG.error(message, exception);
        return createResponse(message + exception.getMessage(), axis2MessageContext, statusCode);
    }

    /**
     * Creates a json response according to the message provided and sets the provided HTTP code.
     *
     * @param message             the error response to be sent to the client
     * @param axis2MessageContext message context to set the json payload to
     * @param statusCode          the HTTP status code to be returned
     * @return error response
     */
    static JSONObject createJsonError(String message, org.apache.axis2.context.MessageContext axis2MessageContext,
                                      String statusCode) {
        LOG.error(message);
        return createResponse(message, axis2MessageContext, statusCode);
    }


    private static JSONObject createResponse(String message, org.apache.axis2.context.MessageContext axis2MessageContext,
                                             String statusCode) {
        JSONObject jsonBody = Utils.createJsonErrorObject(message);
        axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, statusCode);
        return jsonBody;
    }

    public static JSONObject createJsonErrorObject(String error) {

        JSONObject errorObject = new JSONObject();
        errorObject.put("Error", error);
        return errorObject;
    }

    public static boolean isDoingPOST(org.apache.axis2.context.MessageContext axis2MessageContext) {

        return Constants.HTTP_POST.equals(axis2MessageContext.getProperty(Constants.HTTP_METHOD_PROPERTY));
    }

    /**
     * Returns the JSON payload of a given message
     *
     * @param axis2MessageContext axis2MessageContext
     * @return JsonObject payload
     */
    public static JsonObject getJsonPayload(org.apache.axis2.context.MessageContext axis2MessageContext)
            throws IOException {

        InputStream jsonStream = JsonUtil.getJsonPayload(axis2MessageContext);
        String jsonString = IOUtils.toString(jsonStream);
        return new JsonParser().parse(jsonString).getAsJsonObject();
    }

    /**
     * Returns the ServiceAdmin based on a given message context
     *
     * @param messageContext Synapse message context
     */
    public static ServiceAdmin getServiceAdmin(MessageContext messageContext) {

        ServiceAdmin serviceAdmin = ServiceAdmin.getInstance();
        if (!serviceAdmin.isInitailized()) {
            serviceAdmin.init(messageContext.getConfiguration().getAxisConfiguration());
        }
        return serviceAdmin;
    }

    /**
     * Method to update pax-logging configuration.
     */
    public static void updateLoggingConfiguration() throws IOException {

        ConfigurationAdmin configurationAdmin = ConfigurationHolder.getInstance().getConfigAdminService();
        Configuration configuration =
                configurationAdmin.getConfiguration(Constants.PAX_LOGGING_CONFIGURATION_PID, "?");
        Dictionary properties = new Hashtable<>();
        properties.put(Constants.SERVICE_PID, PaxLoggingConstants.LOGGING_CONFIGURATION_PID);
        Hashtable paxLoggingProperties = getPaxLoggingProperties();
        paxLoggingProperties.forEach(properties::put);
        configuration.update(properties);
    }

    private static Hashtable getPaxLoggingProperties() throws IOException {
        String paxPropertiesFileLocation = System.getProperty("org.ops4j.pax.logging.property.file");
        if (StringUtils.isNotEmpty(paxPropertiesFileLocation)) {
            File file = new File(paxPropertiesFileLocation);
            if (file.exists()) {
                Properties properties = new Properties();
                properties.load(new FileInputStream(file));
                return properties;
            }
        }
        return new Hashtable();
    }

    /**
     * Util method to return the specified  property from a properties file.
     *
     * @param srcFile - The source file which needs to be looked up.
     * @param key     - Key of the property.
     * @return - Value of the property.
     */
    public static String getProperty(File srcFile, String key) throws IOException {

        String value;
        try (FileInputStream fis = new FileInputStream(srcFile)) {
            Properties properties = new Properties();
            properties.load(fis);
            value = properties.getProperty(key);
        } catch (IOException e) {
            throw new IOException("Error occurred while reading the input stream");
        }
        return value;
    }

    /**
     * This method will return the path to logs directory.
     *
     * @return path as string.
     */
    public static String getCarbonLogsPath() {

        String carbonLogsPath = System.getProperty("carbon.logs.path");
        if (carbonLogsPath == null) {
            carbonLogsPath = System.getenv("CARBON_LOGS");
            if (carbonLogsPath == null) {
                return getCarbonHome() + File.separator + "repository" + File.separator + "logs";
            }
        }
        return carbonLogsPath;
    }

    /**
     * This method will return the path to cApps directory.
     *
     * @return path as string.
     */
    public static String getCAppPath() {
        return Paths.get(getCarbonHome(), "repository", "deployment", "server", "carbonapps").toString();
    }

    /**
     * This method will return the path to CARBON_HOME.
     *
     * @return path as String.
     */
    public static String getCarbonHome() {

        String carbonHome = System.getProperty("carbon.home");
        if (carbonHome == null) {
            carbonHome = System.getenv("CARBON_HOME");
            System.setProperty("carbon.home", carbonHome);
        }
        return carbonHome;
    }

    /**
     * This method will provide information on all the files in the logs directory.
     *
     * @return list of file info objects.
     */
    public static List<LogFileInfo> getLogFileInfoList() {

        String folderPath = Utils.getCarbonLogsPath();
        List<LogFileInfo> logFilesList = new ArrayList<>();
        LogFileInfo logFileInfo;

        File folder = new File(folderPath);
        File[] listOfFiles = folder.listFiles();

        if (listOfFiles == null || listOfFiles.length == 0) {
            // folder.listFiles can return a null, in that case return a default log info
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not find any log file in " + folderPath);
            }
            return getDefaultLogInfoList();
        }
        for (File file : listOfFiles) {
            String filename = file.getName();
            if (!filename.endsWith(".lck")) {
                String filePath = Utils.getCarbonLogsPath() + File.separator + filename;
                File logfile = new File(filePath);
                logFileInfo = new LogFileInfo(filename, getFileSize(logfile));
                logFilesList.add(logFileInfo);
            }
        }
        return logFilesList;
    }

    private static String getFileSize(File file) {

        long bytes = file.length();
        int unit = 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    private static List<LogFileInfo> getDefaultLogInfoList() {

        List<LogFileInfo> defaultLogFileInfoList = new ArrayList<>();
        defaultLogFileInfoList.add(new LogFileInfo("NO_LOG_FILES", "---"));
        return defaultLogFileInfoList;
    }
    protected static void setInvalidUserStoreResponse(org.apache.axis2.context.MessageContext axis2MessageContext) {
        JSONObject response =
                Utils.createJsonError("User management is not supported with the file-based user store. " +
                                "Please plug in a user store for the correct functionality",
                        axis2MessageContext, FORBIDDEN);
        Utils.setJsonPayLoad(axis2MessageContext, response);
    }

    protected static UserStoreManager getUserStore(String domain) throws UserStoreException {
        UserStoreManager userStoreManager = MicroIntegratorSecurityUtils.getUserStoreManager();
        if (!StringUtils.isEmpty(domain) && userStoreManager instanceof AbstractUserStoreManager &&
                !UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME.equalsIgnoreCase(domain)) {
            userStoreManager = ((AbstractUserStoreManager) userStoreManager).getSecondaryUserStoreManager(domain);
            if (userStoreManager == null) {
                throw new UserStoreException("Could not find a user-store for the given domain " + domain);
            }
        }
        return userStoreManager;
    }

    protected static RealmConfiguration getRealmConfiguration() throws UserStoreException {
        return MicroIntegratorSecurityUtils.getRealmConfiguration();
    }
    public static String addDomainToName(String name, String domainName) {
        if (domainName != null && name != null && !name.contains(Constants.DOMAIN_SEPARATOR) &&
                !"PRIMARY".equalsIgnoreCase(domainName)) {
            if (!"Internal".equalsIgnoreCase(domainName) && !"Workflow".equalsIgnoreCase(domainName) &&
                    !"Application".equalsIgnoreCase(domainName)) {
                name = domainName.toUpperCase() + Constants.DOMAIN_SEPARATOR + name;
            } else {
                name = domainName.substring(0, 1).toUpperCase() + domainName.substring(1).toLowerCase() +
                        Constants.DOMAIN_SEPARATOR + name;
            }
        }
        return name;
    }

    /**
     * This method is used to check the validity of the input path.
     *
     * @param registryPath        File path
     * @param axis2MessageContext AXIS2 message context
     * @return Validated path
     */
    public static String validatePath(String registryPath,
            org.apache.axis2.context.MessageContext axis2MessageContext, MessageContext messageContext) {

        if (StringUtils.isEmpty(registryPath)) {
            JSONObject jsonBody = Utils.createJsonError("Registry path not found in the request", axis2MessageContext,
                    BAD_REQUEST);
            Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
            return null;
        }
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) messageContext.getConfiguration().getRegistry();
        String registryParentPath = formatPath(microIntegratorRegistry.getRegRoot());
        String registryRoot = formatPath(registryParentPath + REGISTRY_ROOT_PATH);
        String validatedPath;

        try {
            File validatedPathFile = new File(formatPath(registryParentPath + File.separator + registryPath));
            File registryRootFile = new File(registryRoot);
            if (!validatedPathFile.getCanonicalPath().startsWith(registryRootFile.getCanonicalPath())) {
                JSONObject jsonBody = Utils.createJsonError("The registry path  '" + registryPath
                                + "' is illegal", axis2MessageContext,
                        BAD_REQUEST);
                Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
                return null;
            } else {
                String validatedCanonicalPath = formatPath(validatedPathFile.getCanonicalPath());
                String rootCanonicalPath = formatPath(registryRootFile.getCanonicalPath());
                validatedPath = formatPath(validatedCanonicalPath.replace(rootCanonicalPath, REGISTRY_ROOT_PATH));
            }
        } catch (IOException | IllegalArgumentException e) {
            JSONObject jsonBody = Utils.createJsonError(
                    "Error while resolving the canonical path of the registry path : " + registryPath,
                    axis2MessageContext, INTERNAL_SERVER_ERROR);
            Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
            return null;
        }
        return validatedPath;
    }

    /**
     * Format the string paths to match any platform.. windows, linux etc..
     *
     * @param path  Input file path
     * @return      Formatted file path
     */
    public static String formatPath(String path) {
        // removing white spaces
        String pathFormatted = path.replaceAll("\\b\\s+\\b", "%20");
        try {
            pathFormatted = java.net.URLDecoder.decode(pathFormatted, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            LOG.error("Unsupported Encoding in the path :" + pathFormatted);
        }
        // replacing all "\" with "/"
        return pathFormatted.replace('\\', '/');
    }

    /**
     * This method checks whether a registry file exists or not.
     *
     * @param registryPath  Registry path
     * @return              Boolean output indicating the existence of the registry
     */
    public static boolean isRegistryExist(String registryPath,
                                          MessageContext messageContext) {
        MicroIntegratorRegistry microIntegratorRegistry =
                (MicroIntegratorRegistry) messageContext.getConfiguration().getRegistry();
        String regRoot = microIntegratorRegistry.getRegRoot();
        String resolvedPath = formatPath(regRoot + File.separator + registryPath + File.separator);
        try {
            File file = new File(resolvedPath);
            return file.exists();
        } catch (Exception e) {
            LOG.error("Error occurred while checking the existence of the registry", e);
            return false;
        }
    }

    /**
     * Format the path by adding the relevant registry type prefix.
     * @param path  Registry path
     * @return      Formatted path with the prefix
     */
    public static String getRegistryPathPrefix(String path) {

        String pathWithPrefix;
        if (path.startsWith(CONFIGURATION_REGISTRY_PATH)) {
            pathWithPrefix = path.replace(CONFIGURATION_REGISTRY_PATH, CONFIGURATION_REGISTRY_PREFIX);
        } else if (path.startsWith(GOVERNANCE_REGISTRY_PATH)) {
            pathWithPrefix = path.replace(GOVERNANCE_REGISTRY_PATH, GOVERNANCE_REGISTRY_PREFIX);
        } else if (path.startsWith(LOCAL_REGISTRY_PATH)) {
            pathWithPrefix = path.replace(LOCAL_REGISTRY_PATH, LOCAL_REGISTRY_PREFIX);
        } else {
            return null;
        }
        return pathWithPrefix;
    }

    /**
     * This method returns a string containing file content.
     *
     * @param axis2MessageContext Axis2 message context
     * @return A string containing file content
     */
    public static String getPayload(org.apache.axis2.context.MessageContext axis2MessageContext) {

        try {
            InputStream inputStream = getInputStream(axis2MessageContext);
            return IOUtils.toString(inputStream);
        } catch (Exception e) {
            LOG.error("Error occurred while fetching the payload", e);
            return null;
        }
    }

    /**
     * Returns input stream from the payload.
     * @param messageContext    Axis2 message context
     * @return                  InputStream of payload
     */
    private static InputStream getInputStream(org.apache.axis2.context.MessageContext messageContext) {
        if (messageContext == null) {
            return null;
        } else {
            Object o = messageContext.getProperty("bufferedInputStream");
            if (o instanceof InputStream) {
                InputStream is = (InputStream) o;
                if (is.markSupported()) {
                    try {
                        is.reset();
                    } catch (IOException e) {
                        LOG.error("Error occurred while fetching the payload", e);
                        return null;
                    }
                }
                return is;
            } else {
                return null;
            }
        }
    }

    /**
     * This method returns a byte array containing file content.
     *
     * @param messageContext    Synapse message context
     * @return                  A byte array containing file content
     */
    public static byte[] getPayloadFromMultipart(MessageContext messageContext) {

        try {
            OMNode fileOmNode = messageContext.getEnvelope().getBody().getFirstElement()
                    .getFirstChildWithName(new QName(FILE)).getFirstOMChild();
            return Base64.getDecoder().decode(((OMTextImpl) fileOmNode).getText());
        } catch (Exception e) {
            LOG.error("Error occurred while fetching the payload", e);
            return null;
        }
    }

    /**
     * Sends unauthorized fault response.
     * @param axis2MessageContext   AXIS2 message context
     */
    public static void sendForbiddenFaultResponse(org.apache.axis2.context.MessageContext axis2MessageContext) {
        axis2MessageContext.setProperty(Constants.NO_ENTITY_BODY, true);
        axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, 403);
    }

    /**
     * Fetches the registry name from the registy path.
     * @param path  Registry path
     * @return      Registry name
     */
    public static String getResourceName(String path) {
        if (path != null) {
            String correctedPath = path;
            if (path.endsWith(URL_SEPARATOR)) {
                correctedPath = path.substring(0, path.lastIndexOf(URL_SEPARATOR));
            }
            return correctedPath.substring(correctedPath.lastIndexOf(URL_SEPARATOR) + 1, correctedPath.length());
        }
        return "";
    }
}
