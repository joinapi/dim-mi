/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.PropertiesConfigurationLayout;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.Level;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.core.axis2.Axis2MessageContext;

import org.json.JSONObject;
import org.wso2.carbon.inbound.endpoint.internal.http.api.APIResource;
import org.wso2.carbon.utils.ServerConstants;
import org.wso2.micro.core.util.AuditLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.wso2.micro.integrator.management.apis.Constants.NO_ENTITY_BODY;
import static org.wso2.micro.integrator.management.apis.Constants.ROOT_LOGGER;
import static org.wso2.micro.integrator.management.apis.Constants.SEARCH_KEY;

public class LoggingResource extends APIResource {

    private static final Log log = LogFactory.getLog(LoggingResource.class);

    private static final Level[] logLevels =
            new Level[] { Level.OFF, Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR, Level.FATAL };

    private JSONObject jsonBody;
    private static final String FILE_PATH = System.getProperty(ServerConstants.CARBON_CONFIG_DIR_PATH) + File.separator
            + "log4j2.properties";
    private static final File LOG_PROP_FILE = new File(FILE_PATH);

    private PropertiesConfiguration config;
    private PropertiesConfigurationLayout layout;

    private static final String EXCEPTION_MSG = "Exception while getting logger data ";
    private static final String LOGGER_PREFIX = "logger.";
    private static final String LOGGER_LEVEL_SUFFIX = ".level";
    private static final String LOGGER_NAME_SUFFIX = ".name";
    private static final String LOGGER_CLASS = "loggerClass";
    private static final String LOGGERS_PROPERTY = "loggers";

    public LoggingResource(String urlTemplate) {
        super(urlTemplate);
    }

    @Override
    public Set<String> getMethods() {

        Set<String> methods = new HashSet<>();
        methods.add(Constants.HTTP_GET);
        methods.add(Constants.HTTP_METHOD_PATCH);
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext) {

        buildMessage(messageContext);

        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);

        JSONObject jsonPayload = new JSONObject(JsonUtil.jsonPayloadToString(axis2MessageContext));
        String httpMethod = axis2MessageContext.getProperty("HTTP_METHOD").toString();

        if (httpMethod.equals(Constants.HTTP_GET)) {
            String param = Utils.getQueryParameter(messageContext, Constants.LOGGER_NAME);
            String searchKey = Utils.getQueryParameter(messageContext, SEARCH_KEY);

            if (Objects.nonNull(param)) {
                try {
                    if (isLoggerExist(param)) {
                        jsonBody = getLoggerData(axis2MessageContext, param);
                    } else {
                        jsonBody = createJsonError("Logger name ('" + param + "') not found", "", axis2MessageContext);
                    }
                } catch (IOException exception) {
                    jsonBody = createJsonError(EXCEPTION_MSG, exception, axis2MessageContext);
                }
            } else {
                try {
                    if (Objects.nonNull(searchKey) && !searchKey.trim().isEmpty()) {
                        getAllLoggerDetails(messageContext, searchKey.toLowerCase());
                    } else {
                        getAllLoggerDetails(messageContext);
                    }
                } catch (IOException e) {
                    jsonBody = createJsonError(EXCEPTION_MSG, e, axis2MessageContext);
                    Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
                }
                return true;
            }
        } else {
            if (jsonPayload.has(Constants.LOGGING_LEVEL)) {
                String logLevel = jsonPayload.getString(Constants.LOGGING_LEVEL);
                if (!isValidLogLevel(logLevel)) {
                    // 400-Bad Request Invalid loggingLevel
                    jsonBody = createJsonError("Invalid log level " + logLevel, "", axis2MessageContext);
                } else {
                    if (jsonPayload.has(Constants.LOGGER_NAME)) {
                        String loggerName = jsonPayload.getString(Constants.LOGGER_NAME);
                        boolean isRootLogger = Constants.ROOT_LOGGER.equals(loggerName);
                        boolean hasLoggerClass = jsonPayload.has(LOGGER_CLASS);
                        String performedBy = Constants.ANONYMOUS_USER;
                        if (messageContext.getProperty(Constants.USERNAME_PROPERTY) !=  null) {
                            performedBy = messageContext.getProperty(Constants.USERNAME_PROPERTY).toString();
                        }
                        JSONObject info = new JSONObject();
                        info.put(Constants.LOGGER_NAME, loggerName);
                        info.put(Constants.LOGGING_LEVEL, logLevel);
                        if (isRootLogger || !hasLoggerClass) {
                            // update existing logger
                            jsonBody = updateLoggerData(performedBy, info, axis2MessageContext, loggerName, logLevel);
                        } else {
                            try {
                                if (isLoggerExist(loggerName)) {
                                    String errorMsg = "Specified logger name ('" + loggerName
                                            + "') already exists, try updating the level instead";
                                    jsonBody = createJsonError(errorMsg, "", axis2MessageContext);
                                } else {
                                    String loggerClass = jsonPayload.getString(LOGGER_CLASS);
                                    jsonBody = updateLoggerData(performedBy, info,
                                                                axis2MessageContext, loggerName, loggerClass, logLevel);
                                }
                            } catch (IOException exception) {
                                jsonBody = createJsonError(EXCEPTION_MSG, exception, axis2MessageContext);
                            }
                        }
                    } else {
                        // 400-Bad Request logger name is missing
                        jsonBody = createJsonError("Logger name is missing", "", axis2MessageContext);
                    }
                }
            } else {
                // 400-Bad Request logLevel is missing
                jsonBody = createJsonError("Log level is missing", "", axis2MessageContext);
            }
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        return true;
    }

    private JSONObject updateLoggerData(String performedBy, JSONObject info,
                                        org.apache.axis2.context.MessageContext axis2MessageContext, String loggerName,
                                        String loggerClass, String logLevel) {

        try {
            loadConfigs();
            String modifiedLogger = getLoggers().concat(", ").concat(loggerName);
            config.setProperty(LOGGERS_PROPERTY, modifiedLogger);
            config.setProperty(LOGGER_PREFIX + loggerName + LOGGER_NAME_SUFFIX, loggerClass);
            config.setProperty(LOGGER_PREFIX + loggerName + LOGGER_LEVEL_SUFFIX, logLevel);
            applyConfigs();
            jsonBody.put(Constants.MESSAGE, getSuccessMsg(loggerClass, loggerName, logLevel));
            AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_LOG_LEVEL,
                                        Constants.AUDIT_LOG_ACTION_CREATED, info);

        } catch (ConfigurationException | IOException exception) {
            jsonBody = createJsonError("Exception while updating logger data ", exception, axis2MessageContext);
        }
        return jsonBody;
    }

    private JSONObject updateLoggerData(String performedBy, JSONObject info,
                                        org.apache.axis2.context.MessageContext axis2MessageContext, String loggerName,
                                        String logLevel) {

        try {
            loadConfigs();
            if (loggerName.equals(Constants.ROOT_LOGGER)) {
                config.setProperty(loggerName + LOGGER_LEVEL_SUFFIX, logLevel);
                applyConfigs();
                jsonBody.put(Constants.MESSAGE, getSuccessMsg("", loggerName, logLevel));
                AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_ROOT_LOG_LEVEL,
                                            Constants.AUDIT_LOG_ACTION_UPDATED, info);

            } else {
                if (isLoggerExist(loggerName)) {
                    config.setProperty(LOGGER_PREFIX + loggerName + LOGGER_LEVEL_SUFFIX, logLevel);
                    AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_LOG_LEVEL,
                                                Constants.AUDIT_LOG_ACTION_UPDATED, info);
                    applyConfigs();
                    jsonBody.put(Constants.MESSAGE, getSuccessMsg("", loggerName, logLevel));
                } else {
                    jsonBody = createJsonError("Specified logger ('" + loggerName + "') not found", "",
                                               axis2MessageContext);
                }
            }
        } catch (ConfigurationException | IOException exception) {
            jsonBody = createJsonError("Exception while updating logger data ", exception, axis2MessageContext);
        }
        return jsonBody;
    }

    private String getSuccessMsg(String loggerClass, String loggerName, String logLevel) {

        return "Successfully added logger for ('" + loggerName + "') with level " + logLevel + (loggerClass.isEmpty() ?
                "" :
                " for class " + loggerClass);
    }

    private void loadConfigs() throws FileNotFoundException, ConfigurationException {

        jsonBody = new JSONObject();
        config = new PropertiesConfiguration();
        layout = new PropertiesConfigurationLayout(config);
        layout.load(new InputStreamReader(new FileInputStream(LOG_PROP_FILE)));
    }

    private boolean isLoggerExist(String loggerName) throws IOException {

        String logger = getLoggers();
        String[] loggers = logger.split(",");
        return Arrays.stream(loggers).anyMatch(loggerValue -> loggerValue.trim().equals(loggerName));
    }

    private static String getLoggers() throws IOException {
        return Utils.getProperty(LOG_PROP_FILE, LOGGERS_PROPERTY);
    }

    private JSONObject getLoggerData(org.apache.axis2.context.MessageContext axis2MessageContext, String loggerName) {

        String logLevel;
        String componentName = "";
        jsonBody = new JSONObject();
        try {
            if (loggerName.equals(Constants.ROOT_LOGGER)) {
                logLevel = Utils.getProperty(LOG_PROP_FILE, loggerName + LOGGER_LEVEL_SUFFIX);
            } else {
                componentName = Utils.getProperty(LOG_PROP_FILE, LOGGER_PREFIX + loggerName + LOGGER_NAME_SUFFIX);
                logLevel = Utils.getProperty(LOG_PROP_FILE, LOGGER_PREFIX + loggerName + LOGGER_LEVEL_SUFFIX);
            }
        } catch (IOException exception) {
            jsonBody = createJsonError("Error while obtaining logger data ", exception, axis2MessageContext);
            return jsonBody;
        }
        jsonBody.put(Constants.LOGGER_NAME, loggerName);
        jsonBody.put(Constants.COMPONENT_NAME, componentName);
        jsonBody.put(Constants.LEVEL, logLevel);
        return jsonBody;
    }

    private String[] getAllLoggers() throws IOException {
        //along with root logger
        String[] loggers = getLoggers().split(",");
        // add root logger
        int fullLength = loggers.length + 1;
        String[] allLoggers = new String[fullLength];
        allLoggers[0] = ROOT_LOGGER;
        for (int i = 1; i < fullLength; i++) {
            allLoggers[i] = loggers[i - 1];
        }
        return allLoggers;
    }

    private void getAllLoggerDetails(MessageContext messageContext) throws IOException {
        String[] loggers = getAllLoggers();
        setResponseBody(Arrays.asList(loggers), messageContext);
    }

    private List<String> getSearchResults(String searchKey) throws IOException {
        String[] allLoggers = getAllLoggers();
        List<String> filteredLoggers = new ArrayList<>();

        for (String logger : allLoggers) {
            if (logger.toLowerCase().contains(searchKey)) {
                filteredLoggers.add(logger);
            }
        }
        return filteredLoggers;
    }

    private void getAllLoggerDetails(MessageContext messageContext, String searchKey) throws IOException {
        List<String> resultsList = getSearchResults(searchKey);
        setResponseBody(resultsList, messageContext);
    }


    private void setResponseBody(List<String> logConfigsList, MessageContext messageContext) {
        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        JSONObject jsonBody = Utils.createJSONList(logConfigsList.size());

        JSONObject data;
        for (String logger : logConfigsList) {
            data = getLoggerData(axis2MessageContext, logger.trim());
            jsonBody.getJSONArray(Constants.LIST).put(data);
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        axis2MessageContext.removeProperty(NO_ENTITY_BODY);
    }

    private boolean isValidLogLevel(String logLevelToTest) {

        return Arrays.stream(logLevels).anyMatch(logLevel -> logLevel.toString().equalsIgnoreCase(logLevelToTest));
    }

    private JSONObject createJsonError(String message, Object exception,
                                       org.apache.axis2.context.MessageContext axis2MessageContext) {
        log.error(message + exception);
        JSONObject jsonBody = Utils.createJsonErrorObject(message);
        axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, Constants.BAD_REQUEST);
        return jsonBody;
    }

    private void applyConfigs() throws IOException, ConfigurationException {
        layout.save(new FileWriter(FILE_PATH, false));
        Utils.updateLoggingConfiguration();
    }
}
