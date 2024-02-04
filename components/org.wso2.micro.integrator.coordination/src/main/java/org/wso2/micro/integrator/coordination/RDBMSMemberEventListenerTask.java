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

package org.wso2.micro.integrator.coordination;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.coordination.exception.ClusterCoordinationException;
import org.wso2.micro.integrator.coordination.node.NodeDetail;
import org.wso2.micro.integrator.coordination.util.MemberEvent;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

/**
 * The task that runs periodically to detect membership change events.
 */
public class RDBMSMemberEventListenerTask implements Runnable {
    /**
     * Class log.
     */
    private static final Log log = LogFactory.getLog(RDBMSMemberEventListenerTask.class);

    /**
     * Node id of the node for which the reader reads member changes.
     */
    private String nodeID;

    /**
     * Group id of the node for which the reader reads member changes.
     */
    private String localGroupId;

    /**
     * Communication bus object to communicate with the database for the context store.
     */
    private RDBMSCommunicationBusContextImpl communicationBusContext;

    /**
     * List used to hold all the registered subscribers.
     */
    private List<MemberEventListener> listeners;

    /**
     * Using heart beat warning margin to make sure other nodes in the cluster
     * are not starting tasks until this node completes stops the tasks
     */
    private int heartbeatWarningMargin;

    private boolean wasMemberUnresponsive = false;

    ExecutorService executor = Executors.newSingleThreadExecutor();

    public RDBMSMemberEventListenerTask(String nodeId, String localGroupId,
                                        RDBMSCommunicationBusContextImpl communicationBusContext, 
                                        int heartbeatWarningMargin) {
        this.nodeID = nodeId;
        this.localGroupId = localGroupId;
        this.listeners = new ArrayList<>();
        this.communicationBusContext = communicationBusContext;
        this.heartbeatWarningMargin = heartbeatWarningMargin;
    }

    /**
     * The task that is periodically run to read membership events and to notify the listeners.
     */
    @Override public void run() {

        try {
            List<MemberEvent> membershipEvents = getMembershipEvents();
            if (!membershipEvents.isEmpty()) {
                for (MemberEvent event : membershipEvents) {
                    switch (event.getMembershipEventType()) {
                        case MEMBER_ADDED:
                            notifyMemberAdditionEvent(event.getTargetNodeId(), event.getTargetGroupId());
                            break;
                        case MEMBER_REMOVED:
                            notifyMemberRemovalEvent(event.getTargetNodeId(), event.getTargetGroupId());
                            break;
                        case COORDINATOR_CHANGED:
                            notifyCoordinatorChangeEvent(event.getTargetNodeId(), event.getTargetGroupId());
                            break;
                        default:
                            log.error("Unknown cluster event type: " + event.getMembershipEventType());
                            break;
                    }
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("No membership events to sync");
                }
            }
            if (wasMemberUnresponsive) {
                notifyRejoin(nodeID, localGroupId);
                wasMemberUnresponsive = false;
            }
        } catch (Throwable e) {
            log.error("Error occurred while reading membership events. ", e);
            if (!wasMemberUnresponsive) {
                notifyUnresponsiveness(nodeID, localGroupId);
                wasMemberUnresponsive = true;
            }
        }
    }

    private List<MemberEvent> getMembershipEvents() throws ClusterCoordinationException  {
        try {
            Future<List<MemberEvent>> listFuture = executor.submit(this::readMembershipEvents);
            return listFuture.get(heartbeatWarningMargin, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            throw new ClusterCoordinationException("Reading membership events took more time than max timeout retry interval", e);
        }
    }

    /**
     * Notifies the coordinator change event to the registered listeners.
     *
     * @param member The node ID of the event occured
     */
    private void notifyCoordinatorChangeEvent(String member, String groupId) {
        for (MemberEventListener listener : listeners) {
            if (listener.getGroupId().equals(groupId)) {
                NodeDetail nodeDetail = communicationBusContext.getNodeData(member, groupId);
                if (nodeDetail != null) {
                    listener.coordinatorChanged(nodeDetail);
                }
            }
        }
    }

    /**
     * Notifies the member removal  event to the registered listeners.
     *
     * @param member The node ID of the event occured
     */
    private void notifyMemberRemovalEvent(String member, String groupId) {
        NodeDetail nodeDetail = communicationBusContext.getRemovedNodeData(nodeID, groupId, member);
        if (nodeDetail != null) {
            for (MemberEventListener listener : listeners) {
                if (listener.getGroupId().equals(groupId)) {
                    listener.memberRemoved(nodeDetail);
                }
            }
        }
    }

    /**
     * Notifies the member added  event to the registered listeners.
     *
     * @param member The node ID of the event occured
     */
    private void notifyMemberAdditionEvent(String member, String groupId) {
        for (MemberEventListener listener : listeners) {
            if (listener.getGroupId().equals(groupId)) {
                NodeDetail nodeDetail = communicationBusContext.getNodeData(member, groupId);
                if (nodeDetail != null) {
                    listener.memberAdded(nodeDetail);
                }
            }
        }
    }

    /**
     * Notifies the responsiveness to registered listeners when the node becomes responsive being after un responsive.
     *
     * @param member The node ID of the event occurred
     */
    private void notifyRejoin(String member, String groupId) {

        listeners.forEach(listener -> {
            if (listener.getGroupId().equals(groupId)) {
                listener.reJoined(member);
            }
        });
    }

    /**
     * Notifies the unresponsiveness to registered listeners.
     *
     * @param member The node ID of the event occurred
     */
    private void notifyUnresponsiveness(String member, String groupId) {
        for (MemberEventListener listener : listeners) {
            if (listener.getGroupId().equals(groupId)) {
                listener.becameUnresponsive(member);
            }
        }
    }

    /**
     * Method to read membership events.
     * <p>This will read all membership events that are recorded for a particular node and clear all of those once
     * read.
     *
     * @return list membership events
     * @throws ClusterCoordinationException
     */
    private List<MemberEvent> readMembershipEvents() throws ClusterCoordinationException {
        return communicationBusContext.readMemberShipEvents(nodeID);
    }

    /**
     * Add a listener to be notified of the cluster membership events.
     *
     * @param membershipListener membership listener object
     */
    public void addEventListener(MemberEventListener membershipListener) {
        listeners.add(membershipListener);
    }

    /**
     * Remove a previously added listener.
     *
     * @param membershipListener membership listener object
     */
    public void removeEventListener(MemberEventListener membershipListener) {
        listeners.remove(membershipListener);
    }
}
