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
package org.wso2.micro.integrator.websocket.transport.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.micro.core.transports.TransportService;
import org.wso2.micro.integrator.core.services.Axis2ConfigurationContextService;
import org.wso2.micro.integrator.websocket.transport.service.SecureWebsocketTransportService;
import org.wso2.micro.integrator.websocket.transport.service.ServiceReferenceHolder;
import org.wso2.micro.integrator.websocket.transport.service.WebsocketTransportService;

@Component(name = "org.wso2.micro.integrator.websocket.transport.internal.WebsocketTransportServiceComponent",
        immediate = true)
public class WebsocketTransportServiceComponent {

    private static Log log = LogFactory.getLog(WebsocketTransportServiceComponent.class);

    private Axis2ConfigurationContextService contextService;

    public WebsocketTransportServiceComponent() {

    }

    @Activate
    protected void activate(ComponentContext ctxt) {

        WebsocketTransportService WebsocketTransport;
        SecureWebsocketTransportService SecureWebsocketTransport;
        if (log.isDebugEnabled()) {
            log.debug("Websocket Transport bundle is activated");
        }
        try {
            if (contextService == null) {
                throw new Exception("ConfigurationContext is not found while loading websocket transport bundle");
            }

            WebsocketTransport = new WebsocketTransportService();
            SecureWebsocketTransport = new SecureWebsocketTransportService();
            ctxt.getBundleContext().registerService(TransportService.class.getName(), WebsocketTransport, null);
            ctxt.getBundleContext().registerService(TransportService.class.getName(), SecureWebsocketTransport, null);
            if (log.isDebugEnabled()) {
                log.debug("Successfully registered the ws(s) transport service");
            }
        } catch (Throwable e) {
            log.error("Error while activating Websocket transport management bundle", e);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext ctxt) {

        log.debug("Websocket Transport bundle is deactivated ");
    }

    @Reference(name = "config.context.service",
            service = Axis2ConfigurationContextService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetConfigurationContextService")
    protected void setConfigurationContextService(Axis2ConfigurationContextService contextService) {

        this.contextService = contextService;
        ServiceReferenceHolder.getInstance().setAxis2ConfigurationContextService(contextService);
    }

    protected void unsetConfigurationContextService(Axis2ConfigurationContextService contextService) {

        this.contextService = null;
        ServiceReferenceHolder.getInstance().setAxis2ConfigurationContextService(null);
    }
}
