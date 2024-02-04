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
package org.wso2.micro.integrator.observability.metric.handler;

/**
 * The Metric Reporter allows one to access the relevant Metric Reporter implementation.
 */
public interface MetricReporter {

    /**
     * Creates a metric of the provided type with the given metric name,
     * the metric description and the set of properties that can be used
     * to identify a metric uniquely and for metric aggregation, metric filtering.
     *
     * @param serviceType The service type (Proxy Service, API, Inbound endpoint )
     *                    for which the metric is going to be instrumented.
     * @param type        The type of metric (e.g.Counter) that is used in instrumentation.
     * @param metricName  Metric name (The proxy/api/inbound endpoint name)
     * @param metricHelp  Metric description
     * @param properties  Metric labels used to uniquely identify a metric.
     */
    void createMetrics(String serviceType, String type, String metricName, String metricHelp,
                       String[] properties);

    /**
     * This is called in the handleInit() method of the AbstractExtendedSynapseHandler.
     * There by the metrics created in the createMetric() method are invoked.
     */
    void initMetrics();

    /**
     * Creates a metric of the provided type when a request invocation fails
     * with the given metric name, the metric description and the set of
     * properties that can be used to identify a metric uniquely and for
     * metric aggregation, metric filtering.
     *
     * @param serviceType Service type
     * @param type        Metric type
     * @param metricHelp  Metric description
     * @param properties  Metric labels
     */
    void initErrorMetrics(String serviceType, String type, String metricName, String metricHelp, String[] properties);

    /**
     * Increment the metric value when a request is received/server/service is deployed.
     *
     * @param metricName Metric name
     * @param properties Metric labels where the Metric and the labels are defined as key-value pairs of the properties
     *                   Map
     */
    void incrementCount(String metricName, String[] properties);

    /**
     * Decrement the metric value when a request is received/server/service is un-deployed.
     *
     * @param metricName Metric name
     * @param properties Metric and the labels as key-value pairs of the properties Map
     */
    void decrementCount(String metricName, String[] properties);

    /**
     * Return a timer object used to observe the round trip time from the moment a
     * request enters the Synapse Engine until the response goes out of the Synapse
     * Engine.
     * A Timer Object contains the metric name, it's respective set of labels
     * and time the request reached the Synapse Engine.
     *
     * @param metricName Metric Name
     * @param properties Metric and the labels as key-value pairs
     */
    Object getTimer(String metricName, String[] properties);

    /**
     * Stops the timer once the response leaves the Synapse Engine, so that the
     * latency of a request entering the Synapse Engine is calculated by the difference
     * between the time the timer was started in the getTimer() method and the
     * observeTime() method.
     *
     * @param timer Timer Object returned from the getTimer() method
     */
    public void observeTime(Object timer);

    /**
     * Instrument metrics related to server deployment where a metric can be uniquely
     * identify by the IP Address of the host and port the MI server is running and the
     * java home and java version used for running the MI server.
     *
     * @param host        The IP Address of the host the MI server is running
     * @param port        The port the MI server is running
     * @param javaHome    Java Home
     * @param javaVersion Java Version
     */
    void serverUp(String host, String port, String javaHome, String javaVersion);

    /**
     * Instrument metrics related to MI server version is and the
     * specific update 2 level of the MI server.
     *
     * @param version        The MI server version
     * @param updateLevel    The specific update 2 level of the MI server
     */
    void serverVersion(String version, String updateLevel);

    /**
     * Instrument metrics on server undeployment.
     *
     * @param host        The IP Address of the host the MI server is running
     * @param port        The port the MI server is running
     * @param javaHome    Java Home
     * @param javaVersion Java Version
     */
    void serverDown(String host, String port, String javaVersion, String javaHome);

    /**
     * Instrument metrics on service deployment where a metric can be uniquely
     * identify by service name and the service type.
     *
     * @param serviceName Service Name
     * @param serviceType Service Type
     */
    void serviceUp(String serviceName, String serviceType);

    /**
     * Instrument metrics on service undeployment.
     *
     * @param serviceName Service Name
     * @param serviceType Service Type
     */
    void serviceDown(String serviceName, String serviceType);
}
