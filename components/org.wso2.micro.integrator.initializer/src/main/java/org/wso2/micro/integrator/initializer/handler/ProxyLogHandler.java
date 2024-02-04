/**
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.micro.integrator.initializer.handler;

import org.apache.synapse.AbstractSynapseHandler;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.core.axis2.ProxyService;

public class ProxyLogHandler extends AbstractSynapseHandler {
    @Override
    public boolean handleRequestInFlow(MessageContext messageContext) {
        handleLogAppenderSetter(messageContext);
        return true;
    }

    @Override
    public boolean handleRequestOutFlow(MessageContext messageContext) {
        handleLogAppenderSetter(messageContext);
        return true;
    }

    @Override
    public boolean handleResponseInFlow(MessageContext messageContext) {
        handleLogAppenderSetter(messageContext);
        return true;
    }

    @Override
    public boolean handleResponseOutFlow(MessageContext messageContext) {
        handleLogAppenderSetter(messageContext);
        return true;
    }

    public void handleLogAppenderSetter (MessageContext synCtx) {
        String proxyName = (String) synCtx.getProperty(SynapseConstants.PROXY_SERVICE);

        ProxyService proxyService;
        if (proxyName != null && (proxyService = synCtx.getConfiguration().getProxyService(proxyName)) != null ) {
            proxyService.setLogSetterValue();
        }
    }
}
