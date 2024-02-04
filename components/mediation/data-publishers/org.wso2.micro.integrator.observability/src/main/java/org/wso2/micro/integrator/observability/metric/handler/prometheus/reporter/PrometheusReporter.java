/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.micro.integrator.observability.metric.handler.prometheus.reporter;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.hotspot.DefaultExports;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseConstants;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.integrator.observability.metric.handler.MetricReporter;
import org.wso2.micro.integrator.observability.util.MetricConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for instrumenting Prometheus Metrics.
 */
public class PrometheusReporter implements MetricReporter {
    private Counter TOTAL_REQUESTS_RECEIVED_PROXY_SERVICE;
    private Counter TOTAL_REQUESTS_RECEIVED_API;
    private Counter TOTAL_REQUESTS_RECEIVED_INBOUND_ENDPOINT;
    private Counter ERROR_REQUESTS_RECEIVED_PROXY_SERVICE;
    private Counter ERROR_REQUESTS_RECEIVED_API;
    private Counter ERROR_REQUESTS_RECEIVED_INBOUND_ENDPOINT;

    private Histogram PROXY_LATENCY_HISTOGRAM;
    private Histogram API_LATENCY_HISTOGRAM;
    private Histogram INBOUND_ENDPOINT_LATENCY_HISTOGRAM;

    private Gauge SERVER_UP;
    private Gauge SERVICE_UP;
    private Gauge SERVER_VERSION;

    private static Log log = LogFactory.getLog(PrometheusReporter.class);

    private double[] proxyLatencyBuckets;
    private double[] apiLatencyBuckets;
    private double[] inboundEndpointLatencyBuckets;

    private Map<String, Object> metricMap = new HashMap();

    @Override
    public void initMetrics() {
        this.initializeServeMetrics();
        this.initializeServerVersionMetrics();
        this.initializeArtifactDeploymentMetrics();

        this.initializeProxyMetrics();
        this.initializeApiMetrics();
        this.initializeInboundEndpointMetrics();
    }

    @Override
    public void createMetrics(String serviceType, String type, String metricName, String metricHelp,
                              String[] properties) {

        proxyLatencyBuckets = new double[]{0.19, 0.20, 0.25, 0.30, 0.35, 0.40, 0.50, 0.60, 1, 5};
        apiLatencyBuckets = new double[]{0.19, 0.20, 0.25, 0.30, 0.35, 0.40, 0.50, 0.60, 1, 5};
        inboundEndpointLatencyBuckets = new double[]{0.19, 0.20, 0.25, 0.30, 0.35, 0.40, 0.50, 0.60, 1, 5};

        Map<String, Object> configs = ConfigParser.getParsedConfigs();
        createBuckets(configs);
        DefaultExports.initialize();

        //Read the label names from the map
        String[] labels = properties;

        if (serviceType.equalsIgnoreCase(SERVICE.PROXY.name())) {
            if (type.equals(MetricConstants.COUNTER)) {
                TOTAL_REQUESTS_RECEIVED_PROXY_SERVICE = Counter.build(MetricConstants.PROXY_REQUEST_COUNT_TOTAL,
                        metricHelp).
                        labelNames(labels).register();
                metricMap.put(metricName, TOTAL_REQUESTS_RECEIVED_PROXY_SERVICE);

            } else if (type.equals(MetricConstants.HISTOGRAM)) {
                PROXY_LATENCY_HISTOGRAM = Histogram.build()
                        .name(MetricConstants.PROXY_LATENCY_SECONDS)
                        .help(metricHelp)
                        .labelNames(labels)
                        .buckets(proxyLatencyBuckets)
                        .register();
                metricMap.put(metricName, PROXY_LATENCY_HISTOGRAM);
            }
        } else if (serviceType.equalsIgnoreCase(SERVICE.API.name())) {
            if (type.equals(MetricConstants.COUNTER)) {
                TOTAL_REQUESTS_RECEIVED_API = Counter.build(MetricConstants.API_REQUEST_COUNT_TOTAL, metricHelp).
                        labelNames(labels).register();
                metricMap.put(metricName, TOTAL_REQUESTS_RECEIVED_API);
            } else if (type.equals(MetricConstants.HISTOGRAM)) {
                API_LATENCY_HISTOGRAM = Histogram.build()
                        .name(MetricConstants.API_LATENCY_SECONDS)
                        .help(metricHelp)
                        .labelNames(labels)
                        .buckets(apiLatencyBuckets)
                        .register();
                metricMap.put(metricName, API_LATENCY_HISTOGRAM);
            }
        } else if (serviceType.equalsIgnoreCase(SERVICE.INBOUND_ENDPOINT.name())) {
            if (type.equals(MetricConstants.COUNTER)) {
                TOTAL_REQUESTS_RECEIVED_INBOUND_ENDPOINT = Counter.build
                        (MetricConstants.INBOUND_ENDPOINT_REQUEST_COUNT_TOTAL, metricHelp).
                        labelNames(labels).register();
                metricMap.put(metricName, TOTAL_REQUESTS_RECEIVED_INBOUND_ENDPOINT);
            } else if (type.equals(MetricConstants.HISTOGRAM)) {
                INBOUND_ENDPOINT_LATENCY_HISTOGRAM = Histogram.build()
                        .name(MetricConstants.INBOUND_ENDPOINT_LATENCY_SECONDS)
                        .help(metricHelp)
                        .labelNames(labels)
                        .buckets(inboundEndpointLatencyBuckets)
                        .register();
                metricMap.put(metricName, INBOUND_ENDPOINT_LATENCY_HISTOGRAM);
            }
        } else if (serviceType.equals(MetricConstants.SERVER)) {
            SERVER_UP = Gauge.build(MetricConstants.SERVER_UP, "Server status").
                    labelNames(labels).register();
            metricMap.put(MetricConstants.SERVER_UP, SERVER_UP);
        } else if (serviceType.equals(MetricConstants.VERSION)) {
            SERVER_VERSION = Gauge.build(MetricConstants.SERVER_VERSION, metricHelp).
                    labelNames(labels).register();
            metricMap.put(MetricConstants.SERVER_VERSION, SERVER_VERSION);
        } else {
            SERVICE_UP = Gauge.build(MetricConstants.SERVICE_UP, "Service status").
                    labelNames(labels).register();
            metricMap.put(MetricConstants.SERVICE_UP, SERVICE_UP);
        }
    }

    @Override
    public void initErrorMetrics(String serviceType, String type, String metricName, String metricHelp,
            String[] properties) {

        String[] labels = properties;

        if (serviceType.equals(SERVICE.PROXY.name())) {
            ERROR_REQUESTS_RECEIVED_PROXY_SERVICE = Counter.build(MetricConstants.PROXY_REQUEST_COUNT_ERROR_TOTAL,
                    metricHelp).
                    labelNames(labels).register();
            metricMap.put(metricName, ERROR_REQUESTS_RECEIVED_PROXY_SERVICE);
        } else if (serviceType.equals(SERVICE.API.name())) {
            ERROR_REQUESTS_RECEIVED_API = Counter.build(MetricConstants.API_REQUEST_COUNT_ERROR_TOTAL, metricHelp).
                    labelNames(labels).register();
            metricMap.put(metricName, ERROR_REQUESTS_RECEIVED_API);
        } else if (serviceType.equals(SERVICE.INBOUND_ENDPOINT.name())) {
            ERROR_REQUESTS_RECEIVED_INBOUND_ENDPOINT = Counter.
                    build(MetricConstants.INBOUND_ENDPOINT_REQUEST_COUNT_ERROR_TOTAL, metricHelp).labelNames(labels).
                    register();
            metricMap.put(metricName, ERROR_REQUESTS_RECEIVED_INBOUND_ENDPOINT);

        }
    }

    @Override
    public void incrementCount(String metricName, String[] properties) {
        Counter counter = (Counter) metricMap.get(metricName);
        counter.labels(properties).inc();
    }

    @Override
    public void decrementCount(String metricName, String[] properties) {
        // decrementCount() is not necessary to be implemented for the Prometheus Reporter
        // as Gauge is used in Prometheus for the metrics that can both increment and decrement in value.
    }

    @Override
    public Object getTimer(String metricName,  String[] properties) {
        Histogram timer = (Histogram) metricMap.get(metricName);
        return timer.labels(properties).startTimer();
    }

    @Override
    public void observeTime(Object timer) {
        try {
            ((Histogram.Timer) timer).observeDuration();
        } catch (ClassCastException e) {
            log.error("Error in casting timer object to Prometheus Histogram timer", e);
        }
    }

    @Override
    public void serverUp(String host, String port, String javaHome, String javaVersion) {
        Gauge gauge = (Gauge) metricMap.get(MetricConstants.SERVER_UP);
        gauge.labels(host, port, javaHome, javaVersion).setToCurrentTime();
    }

    @Override
    public void serverVersion(String version, String updateLevel) {
        Gauge gauge = (Gauge) metricMap.get(MetricConstants.SERVER_VERSION);
        gauge.labels(version, updateLevel).setToCurrentTime();
    }

    @Override
    public void serverDown(String host, String port, String javaHome, String javaVersion) {
        Gauge gauge = (Gauge) metricMap.get(MetricConstants.SERVER_UP);
        gauge.labels(host, port, javaHome, javaVersion).set(0);
    }

    @Override
    public void serviceUp(String serviceName, String serviceType) {
        Gauge gauge = (Gauge) metricMap.get(MetricConstants.SERVICE_UP);
        gauge.labels(serviceName, serviceType).setToCurrentTime();

        if (serviceType.equals(SynapseConstants.PROXY_SERVICE_TYPE)) {
            setCounterValue(TOTAL_REQUESTS_RECEIVED_PROXY_SERVICE, serviceName, serviceType);
            setCounterValue(ERROR_REQUESTS_RECEIVED_PROXY_SERVICE, serviceName, serviceType);
        } else if (serviceType.equals(SynapseConstants.FAIL_SAFE_MODE_API)) {
            setCounterValue(TOTAL_REQUESTS_RECEIVED_API, serviceName, serviceType);
            setCounterValue(ERROR_REQUESTS_RECEIVED_API, serviceName, serviceType);
        } else {
            setCounterValue(TOTAL_REQUESTS_RECEIVED_INBOUND_ENDPOINT, serviceName, serviceType);
            setCounterValue(ERROR_REQUESTS_RECEIVED_INBOUND_ENDPOINT, serviceName, serviceType);
        }
    }

    @Override
    public void serviceDown(String serviceName, String serviceType) {
        Gauge gauge = (Gauge) metricMap.get(MetricConstants.SERVICE_UP);
        gauge.labels(serviceName, serviceType).set(0);
    }

    enum SERVICE {
        PROXY,
        API,
        INBOUND_ENDPOINT
    }

    /**
     * Load the user defined Histogram bucket upper limits configurations from the
     * deployment.toml file else assign the default bucket configuration values.  .
     *
     * @param configs The map of configs defined for Histogram bucket upper limits
     *                in the deployment.toml file
     */
    private void createBuckets(Map<String, Object> configs) {
        Object proxyConfigBuckets = configs.get(MetricConstants.METRIC_HANDLER+ "." +
                MetricConstants.PROXY_LATENCY_BUCKETS);
        Object apiConfigBuckets = configs.get(MetricConstants.METRIC_HANDLER + "." +
                MetricConstants.API_LATENCY_BUCKETS);
        Object inboundEndpointConfigBuckets = configs.get(MetricConstants.METRIC_HANDLER + "." +
                MetricConstants.INBOUND_ENDPOINT_LATENCY_BUCKETS);

        if (null != proxyConfigBuckets) {
            List<Object> list = Arrays.asList(proxyConfigBuckets);
            int size = list.size();
            List<Object> bucketList =  (ArrayList) list.get(0);
            for (int i = 0; i < size; i++) {
                proxyLatencyBuckets[i] = (double) bucketList.get(i);
            }
        }
        if (null != apiConfigBuckets) {
            List<Object> list = Arrays.asList(apiConfigBuckets);
            int size = list.size();
            List<Object> bucketList =  (ArrayList) list.get(0);
            for (int i = 0; i < size; i++) {
                apiLatencyBuckets[i] = (double) bucketList.get(i);
            }
        }
        if (null != inboundEndpointConfigBuckets) {
            List<Object> list = Arrays.asList(inboundEndpointConfigBuckets);
            int size = list.size();
            List<Object> bucketList =  (ArrayList) list.get(0);
            for (int i = 0; i < size; i++) {
                inboundEndpointLatencyBuckets[i] = (double) bucketList.get(i);
            }
        }
    }

    /**
     * Create the proxy services related metrics.
     */
    public void initializeProxyMetrics() {
        String[] labels = {MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE};

        createMetrics(SynapseConstants.PROXY_SERVICE_TYPE, MetricConstants.COUNTER,
                MetricConstants.PROXY_REQUEST_COUNT_TOTAL,
                "Total number of requests to a proxy service", labels);
        createMetrics(SynapseConstants.PROXY_SERVICE_TYPE, MetricConstants.HISTOGRAM,
                MetricConstants.PROXY_LATENCY_SECONDS,
                "Latency of requests to a proxy service", labels);

        initializeProxyErrorMetrics();
    }

    /**
     * Create the api related metrics.
     */
    public void initializeApiMetrics() {
        String[] labels = {MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE, MetricConstants.INVOCATION_URL};
        createMetrics(SynapseConstants.FAIL_SAFE_MODE_API, MetricConstants.COUNTER,
                MetricConstants.API_REQUEST_COUNT_TOTAL,
                "Total number of requests to an api", labels);
        createMetrics(SynapseConstants.FAIL_SAFE_MODE_API, MetricConstants.HISTOGRAM,
                MetricConstants.API_LATENCY_SECONDS,
                "Latency of requests to an api", labels);

        initializeApiErrorMetrics();
    }

    /**
     * Create the inbound endpoint related metrics.
     */
    public void initializeInboundEndpointMetrics() {
        String[] labels = {MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE};

        createMetrics("INBOUND_ENDPOINT", MetricConstants.COUNTER,
                MetricConstants.INBOUND_ENDPOINT_REQUEST_COUNT_TOTAL,
                "Total number of requests to an inbound endpoint.", labels);
        createMetrics("INBOUND_ENDPOINT", MetricConstants.HISTOGRAM,
                MetricConstants.INBOUND_ENDPOINT_LATENCY_SECONDS,
                "Latency of requests to an inbound endpoint.", labels);

        initializeInboundEndpointErrorMetrics();
    }

    /**
     * Create the metrics related to failed proxy services.
     */
    public void initializeProxyErrorMetrics() {
        initErrorMetrics("PROXY", MetricConstants.COUNTER,
                MetricConstants.PROXY_REQUEST_COUNT_ERROR_TOTAL,
                "Total number of error requests to a proxy service", new String[]
                        {MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE});
    }

    /**
     * Create the metrics related to failed apis.
     */
    public void initializeApiErrorMetrics() {
        initErrorMetrics("API", MetricConstants.COUNTER, MetricConstants.API_REQUEST_COUNT_ERROR_TOTAL,
                "Total number of error requests to an api", new String[]{MetricConstants.SERVICE_NAME,
                        MetricConstants.SERVICE_TYPE, MetricConstants.INVOCATION_URL});
    }

    /**
     * Create the metrics related to failed inbound endpoints.
     */
    public void initializeInboundEndpointErrorMetrics() {
        initErrorMetrics("INBOUND_ENDPOINT", MetricConstants.COUNTER,
                MetricConstants.INBOUND_ENDPOINT_REQUEST_COUNT_ERROR_TOTAL, "Total number of error" +
                        " requests when receiving the message by an inbound endpoint.",
                new String[]{MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE});
    }

    /**
     * Create the metrics related to server startup.
     */
    public void initializeServeMetrics() {
        createMetrics(MetricConstants.SERVER, MetricConstants.GAUGE, MetricConstants.SERVER_UP,
                "Server Status", new String[]{MetricConstants.HOST, MetricConstants.PORT,
                        MetricConstants.JAVA_HOME_LABEL, MetricConstants.JAVA_VERSION_LABEL});
    }

    /**
     * Create the metrics related to server version.
     */
    public void initializeServerVersionMetrics() {
        createMetrics(MetricConstants.VERSION, MetricConstants.GAUGE, MetricConstants.SERVER_VERSION,
                "Version and Update Level of Server",
                new String[]{MetricConstants.VERSION_LABEL, MetricConstants.UPDATE_LEVEL_LABEL});
    }

    /**
     * Create the metrics related to service deployment.
     */
    public void initializeArtifactDeploymentMetrics() {
        createMetrics(MetricConstants.SERVICE, MetricConstants.GAUGE, MetricConstants.SERVICE_UP,
                "Service Status", new String[]{MetricConstants.SERVICE_NAME, MetricConstants.SERVICE_TYPE});

    }

    public void setCounterValue(Counter counter, String serviceName, String serviceType) {
        if (serviceType.equals(SynapseConstants.FAIL_SAFE_MODE_API)) {
            counter.labels(serviceName, serviceType, "");
        } else {
            counter.labels(serviceName, serviceType);
        }
    }
}
