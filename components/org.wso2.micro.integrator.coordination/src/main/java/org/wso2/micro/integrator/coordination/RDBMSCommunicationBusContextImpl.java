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
import org.wso2.micro.integrator.coordination.query.QueryManager;
import org.wso2.micro.integrator.coordination.query.QueryManager.DBQueries;
import org.wso2.micro.integrator.coordination.util.CommunicationBusContext;
import org.wso2.micro.integrator.coordination.util.MemberEvent;
import org.wso2.micro.integrator.coordination.util.MemberEventType;
import org.wso2.micro.integrator.coordination.util.RDBMSConstantUtils;
import org.wso2.micro.integrator.coordination.util.StringUtil;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * The RDBMS based communication bus layer for the nodes. This layer handles the database level calls.
 */
public class RDBMSCommunicationBusContextImpl implements CommunicationBusContext {
    /**
     * The log class
     */
    private static final Log log = LogFactory.getLog(RDBMSCommunicationBusContextImpl.class);

    /**
     * The datasource which is used to be connected to the database.
     */
    private DataSource datasource;
    private String databaseType = null;
    private QueryManager queryManager;

    public RDBMSCommunicationBusContextImpl(DataSource dataSource) {
        this.datasource = dataSource;

        Connection connection = null;
        try {
            connection = getConnection();
            DatabaseMetaData metaData = connection.getMetaData();
            this.databaseType = metaData.getDatabaseProductName();
            this.queryManager = new QueryManager(this.databaseType);
        } catch (SQLException e) {
            throw new ClusterCoordinationException("Database communication failed", e);
        } finally {
            close(connection, "Getting coordination database information");
        }
    }

    public RDBMSCommunicationBusContextImpl() {

    }

    @Override
    public void storeMembershipEvent(String changedMember, String groupId, List<String> clusterNodes,
                                     int membershipEventType) throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement storeMembershipEventPreparedStatement = null;
        String task = "Storing membership event: " + membershipEventType + " for member: " + changedMember
                      + " in group " + groupId;
        try {
            connection = getConnection();
            storeMembershipEventPreparedStatement = connection
                    .prepareStatement(queryManager.getQuery(DBQueries.INSERT_MEMBERSHIP_EVENT));
            for (String clusterNode : clusterNodes) {
                storeMembershipEventPreparedStatement.setString(1, clusterNode);
                storeMembershipEventPreparedStatement.setString(2, groupId);
                storeMembershipEventPreparedStatement.setInt(3, membershipEventType);
                storeMembershipEventPreparedStatement.setString(4, changedMember);
                storeMembershipEventPreparedStatement.addBatch();
            }
            storeMembershipEventPreparedStatement.executeBatch();
            connection.commit();
            if (log.isDebugEnabled()) {
                log.debug(StringUtil.removeCRLFCharacters(task) + " executed successfully");
            }
        } catch (SQLException e) {
            rollback(connection, task);
            throw new ClusterCoordinationException("Error storing membership change: " + membershipEventType +
                                                   " for member: " + changedMember + " in group " + groupId, e);
        } finally {
            close(storeMembershipEventPreparedStatement, task);
            close(connection, task);
        }
    }

    @Override
    public List<MemberEvent> readMemberShipEvents(String nodeID) throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        PreparedStatement clearMembershipEvents = null;
        ResultSet resultSet = null;
        List<MemberEvent> membershipEvents = new ArrayList<MemberEvent>();
        String task = "retrieving membership events destined to: " + nodeID;
        try {
            connection = getConnection();
            preparedStatement = connection.prepareStatement(queryManager.getQuery(
                    DBQueries.SELECT_MEMBERSHIP_EVENT));
            preparedStatement.setString(1, nodeID);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                MemberEvent membershipEvent = new MemberEvent(MemberEventType.getTypeFromInt(
                        resultSet.getInt(RDBMSConstantUtils.MEMBERSHIP_CHANGE_TYPE)),
                                                              resultSet.getString(RDBMSConstantUtils.MEMBERSHIP_CHANGED_MEMBER_ID),
                                                              resultSet.getString(RDBMSConstantUtils.GROUP_ID));
                membershipEvents.add(membershipEvent);
            }
            clearMembershipEvents = connection.prepareStatement(queryManager.getQuery(
                    DBQueries.CLEAN_MEMBERSHIP_EVENTS_FOR_NODE));
            clearMembershipEvents.setString(1, nodeID);
            clearMembershipEvents.executeUpdate();
            connection.commit();
            if (log.isDebugEnabled()) {
                log.debug(task + " executed successfully");
            }
            return membershipEvents;
        } catch (SQLException e) {
            throw new ClusterCoordinationException("Error occurred while " + task, e);
        } finally {
            close(resultSet, task);
            close(preparedStatement, task);
            close(clearMembershipEvents, task);
            close(connection, task);
        }
    }

    @Override
    public String getCoordinatorNodeId(String groupId) throws ClusterCoordinationException {

        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            connection = getConnection();
            preparedStatement = connection.prepareStatement(queryManager.getQuery(
                    DBQueries.GET_COORDINATOR_NODE_ID));
            preparedStatement.setString(1, groupId);
            resultSet = preparedStatement.executeQuery();
            String coordinatorNodeId;
            if (resultSet.next()) {
                coordinatorNodeId = resultSet.getString(1);
                if (log.isDebugEnabled()) {
                    log.debug("Coordinator node ID: " + StringUtil.removeCRLFCharacters(coordinatorNodeId) +
                              " for group : " + StringUtil.removeCRLFCharacters(groupId));
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("No coordinator present in database for group "
                              + StringUtil.removeCRLFCharacters(groupId));
                }
                coordinatorNodeId = null;
            }
            if (log.isDebugEnabled()) {
                log.debug(RDBMSConstantUtils.TASK_GET_COORDINATOR_INFORMATION + " executed successfully");
            }
            return coordinatorNodeId;
        } catch (SQLException e) {
            String errMsg = RDBMSConstantUtils.TASK_GET_COORDINATOR_INFORMATION;
            throw new ClusterCoordinationException("Error occurred while " + errMsg, e);
        } finally {
            close(resultSet, RDBMSConstantUtils.TASK_GET_COORDINATOR_INFORMATION);
            close(preparedStatement, RDBMSConstantUtils.TASK_GET_COORDINATOR_INFORMATION);
            close(connection, RDBMSConstantUtils.TASK_GET_COORDINATOR_INFORMATION);
        }

    }

    @Override
    public boolean createCoordinatorEntry(String nodeId, String groupId) throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        try {
            connection = getConnection();
            preparedStatement = connection.prepareStatement(queryManager.getQuery(
                    DBQueries.INSERT_COORDINATOR_ROW));
            preparedStatement.setString(1, groupId);
            preparedStatement.setString(2, nodeId);
            preparedStatement.setLong(3, System.currentTimeMillis());
            int updateCount = preparedStatement.executeUpdate();
            connection.commit();
            if (log.isDebugEnabled()) {
                log.debug(RDBMSConstantUtils.TASK_ADD_MESSAGE_ID + " " + nodeId + " executed successfully");
            }
            return updateCount != 0;
        } catch (SQLException e) {
            rollback(connection, RDBMSConstantUtils.TASK_ADD_MESSAGE_ID);
            return false;
        } finally {
            close(preparedStatement, RDBMSConstantUtils.TASK_ADD_MESSAGE_ID);
            close(connection, RDBMSConstantUtils.TASK_ADD_MESSAGE_ID);
        }
    }

    @Override
    public boolean checkIsCoordinator(String nodeId, String groupId) throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            connection = getConnection();
            preparedStatement = connection.prepareStatement(queryManager.getQuery(
                    DBQueries.GET_COORDINATOR_ROW_FOR_NODE_ID));
            preparedStatement.setString(1, nodeId);
            preparedStatement.setString(2, groupId);
            resultSet = preparedStatement.executeQuery();
            boolean isCoordinator;
            isCoordinator = resultSet.next();
            if (log.isDebugEnabled()) {
                log.debug(RDBMSConstantUtils.TASK_CHECK_IS_COORDINATOR + " instance id: " + nodeId
                          + " group ID: " + groupId + " executed successfully");
            }
            return isCoordinator;
        } catch (SQLException e) {
            String errMsg = RDBMSConstantUtils.TASK_CHECK_IS_COORDINATOR + " instance id: " + nodeId
                            + " group ID: " + groupId;
            throw new ClusterCoordinationException("Error occurred while " + errMsg, e);
        } finally {
            close(resultSet, RDBMSConstantUtils.TASK_CHECK_IS_COORDINATOR);
            close(preparedStatement, RDBMSConstantUtils.TASK_CHECK_IS_COORDINATOR);
            close(connection, RDBMSConstantUtils.TASK_CHECK_IS_COORDINATOR);
        }
    }

    @Override
    public boolean updateCoordinatorHeartbeat(String nodeId, String groupId, long currentHeartbeatTime)
            throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement preparedStatementForCoordinatorUpdate = null;
        try {
            connection = getConnection();
            preparedStatementForCoordinatorUpdate = connection
                    .prepareStatement(queryManager.getQuery(DBQueries.UPDATE_COORDINATOR_HEARTBEAT));
            preparedStatementForCoordinatorUpdate.setLong(1, currentHeartbeatTime);
            preparedStatementForCoordinatorUpdate.setString(2, nodeId);
            preparedStatementForCoordinatorUpdate.setString(3, groupId);
            int updateCount = preparedStatementForCoordinatorUpdate.executeUpdate();
            connection.commit();
            if (log.isDebugEnabled()) {
                log.debug(RDBMSConstantUtils.TASK_UPDATE_COORDINATOR_HEARTBEAT + "node id " + nodeId
                          + " executed successfully");
            }
            return updateCount != 0;
        } catch (SQLException e) {
            rollback(connection, RDBMSConstantUtils.TASK_UPDATE_COORDINATOR_HEARTBEAT);
            throw new ClusterCoordinationException("Error occurred while "
                                                   + RDBMSConstantUtils.TASK_UPDATE_COORDINATOR_HEARTBEAT
                                                   + ". instance ID: " + nodeId + " group ID: " + groupId, e);
        } finally {
            close(preparedStatementForCoordinatorUpdate, RDBMSConstantUtils.TASK_UPDATE_COORDINATOR_HEARTBEAT);
            close(connection, RDBMSConstantUtils.TASK_UPDATE_COORDINATOR_HEARTBEAT);
        }
    }

    @Override
    public boolean checkIfCoordinatorValid(String groupId, String nodeId, int heartbeatMaxAge,
                                           long currentHeartbeatTime) throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            connection = getConnection();
            preparedStatement = connection.prepareStatement(queryManager.getQuery(
                    DBQueries.GET_COORDINATOR_HEARTBEAT));
            preparedStatement.setString(1, groupId);
            resultSet = preparedStatement.executeQuery();
            boolean isCoordinatorValid;
            if (resultSet.next()) {
                long coordinatorHeartbeat = resultSet.getLong(1);
                long heartbeatAge = currentHeartbeatTime - coordinatorHeartbeat;
                isCoordinatorValid = heartbeatAge <= heartbeatMaxAge;
                if (!isCoordinatorValid) {
                    log.info("Coordinator is invalid, because there is no heartbeat for " + heartbeatAge
                             + " millis when checked by nodeId: " + nodeId +
                             ". The heartbeat should have happened in " + heartbeatMaxAge);
                }
            } else {
                log.info("No valid coordinator present in database for group " + groupId +
                         " when checked by nodeId: " + nodeId);
                isCoordinatorValid = false;
            }
            return isCoordinatorValid;
        } catch (SQLException e) {
            String errMsg = RDBMSConstantUtils.TASK_CHECK_COORDINATOR_VALIDITY;
            throw new ClusterCoordinationException("Error occurred while " + errMsg, e);
        } finally {
            close(resultSet, RDBMSConstantUtils.TASK_CHECK_COORDINATOR_VALIDITY);
            close(preparedStatement, RDBMSConstantUtils.TASK_CHECK_COORDINATOR_VALIDITY);
            close(connection, RDBMSConstantUtils.TASK_CHECK_COORDINATOR_VALIDITY);
        }
    }

    @Override
    public void removeCoordinator(String groupId, int heartbeatMaxAge, long currentHeartbeatTime)
            throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        try {
            connection = getConnection();
            long thresholdTimeLimit = currentHeartbeatTime - heartbeatMaxAge;
            preparedStatement = connection.prepareStatement(queryManager.getQuery(DBQueries.DELETE_COORDINATOR));
            preparedStatement.setString(1, groupId);
            preparedStatement.setLong(2, thresholdTimeLimit);
            preparedStatement.executeUpdate();
            if (log.isDebugEnabled()) {
                log.debug(RDBMSConstantUtils.TASK_REMOVE_COORDINATOR + " of group " + groupId + " executed successfully");
            }
            connection.commit();
        } catch (SQLException e) {
            rollback(connection, RDBMSConstantUtils.TASK_REMOVE_COORDINATOR);
            throw new ClusterCoordinationException("Error occurred while " + RDBMSConstantUtils.TASK_REMOVE_COORDINATOR, e);
        } finally {
            close(preparedStatement, RDBMSConstantUtils.TASK_REMOVE_COORDINATOR);
            close(connection, RDBMSConstantUtils.TASK_REMOVE_COORDINATOR);
        }
    }

    @Override
    public boolean updateNodeHeartbeat(String nodeId, String groupId, long currentHeartbeatTime)
            throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement preparedStatementForNodeUpdate = null;

        try {
            connection = getConnection();
            preparedStatementForNodeUpdate = connection.prepareStatement(queryManager.getQuery(
                    DBQueries.UPDATE_NODE_HEARTBEAT));
            preparedStatementForNodeUpdate.setLong(1, currentHeartbeatTime);
            preparedStatementForNodeUpdate.setString(2, nodeId);
            preparedStatementForNodeUpdate.setString(3, groupId);
            int updateCount = preparedStatementForNodeUpdate.executeUpdate();
            connection.commit();
            if (log.isDebugEnabled()) {
                log.debug(RDBMSConstantUtils.TASK_UPDATE_NODE_HEARTBEAT + " of node " + nodeId + " executed successfully");
            }
            return updateCount != 0;
        } catch (SQLException e) {
            rollback(connection, RDBMSConstantUtils.TASK_UPDATE_NODE_HEARTBEAT);
            throw new ClusterCoordinationException("Error occurred while " + RDBMSConstantUtils.TASK_UPDATE_NODE_HEARTBEAT
                                                   + ". Node ID: " + nodeId + "and Group ID : " + groupId, e);
        } finally {
            close(preparedStatementForNodeUpdate, RDBMSConstantUtils.TASK_UPDATE_NODE_HEARTBEAT);
            close(connection, RDBMSConstantUtils.TASK_UPDATE_NODE_HEARTBEAT);
        }
    }

    @Override
    public void createNodeHeartbeatEntry(String nodeId, String groupId) throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        try {
            connection = getConnection();
            preparedStatement = connection.prepareStatement(queryManager.getQuery(
                    DBQueries.INSERT_NODE_HEARTBEAT_ROW));
            preparedStatement.setString(1, nodeId);
            preparedStatement.setLong(2, System.currentTimeMillis());
            preparedStatement.setString(3, groupId);
            preparedStatement.executeUpdate();
            connection.commit();
            if (log.isDebugEnabled()) {
                log.debug(RDBMSConstantUtils.TASK_CREATE_NODE_HEARTBEAT + " of node " + nodeId + " executed successfully");
            }
        } catch (SQLException e) {
            rollback(connection, RDBMSConstantUtils.TASK_CREATE_NODE_HEARTBEAT);
            throw new ClusterCoordinationException("Error occurred while " + RDBMSConstantUtils.TASK_CREATE_NODE_HEARTBEAT
                                                   + ". Node ID: " + nodeId + " group ID " + groupId, e);
        } finally {
            close(preparedStatement, RDBMSConstantUtils.TASK_UPDATE_COORDINATOR_HEARTBEAT);
            close(connection, RDBMSConstantUtils.TASK_CREATE_NODE_HEARTBEAT);
        }
    }

    @Override
    public List<NodeDetail> getAllNodeData(String groupId) throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String coordinatorNodeId = getCoordinatorNodeId(groupId);
        if (coordinatorNodeId == null) {
            coordinatorNodeId = getCoordinatorNodeId(groupId);
        }
        ArrayList<NodeDetail> nodeDataList = new ArrayList<NodeDetail>();

        try {
            connection = getConnection();
            preparedStatement = connection.prepareStatement(queryManager.getQuery(
                    DBQueries.GET_ALL_NODE_HEARTBEAT));
            preparedStatement.setString(1, groupId);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String nodeId = resultSet.getString(2);
                boolean isCoordinatorNode = false;
                if (coordinatorNodeId != null) {
                    isCoordinatorNode = coordinatorNodeId.equals(nodeId);
                }
                long lastHeartbeat = resultSet.getLong(3);
                boolean isNewNode = convertIntToBoolean(resultSet.getInt(4));
                NodeDetail heartBeatData = new NodeDetail(nodeId, groupId, isCoordinatorNode,
                                                          lastHeartbeat, isNewNode);
                nodeDataList.add(heartBeatData);
            }

        } catch (SQLException e) {
            String errMsg = RDBMSConstantUtils.TASK_GET_ALL_QUEUES;
            throw new ClusterCoordinationException("Error occurred while " + errMsg, e);
        } finally {
            close(resultSet, RDBMSConstantUtils.TASK_GET_ALL_QUEUES);
            close(preparedStatement, RDBMSConstantUtils.TASK_GET_ALL_QUEUES);
            close(connection, RDBMSConstantUtils.TASK_GET_ALL_QUEUES);
        }
        if (log.isDebugEnabled()) {
            log.debug(RDBMSConstantUtils.TASK_GET_ALL_QUEUES + " of group " + groupId + " executed successfully");
        }
        return nodeDataList;
    }

    @Override
    public NodeDetail getRemovedNodeData(String nodeId, String groupId,
                                         String removedMemberId) throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        PreparedStatement clearMembershipEvents = null;
        ResultSet resultSet = null;
        NodeDetail nodeDetail = null;
        try {
            connection = getConnection();
            preparedStatement = connection.prepareStatement(queryManager.getQuery(
                    DBQueries.SELECT_REMOVED_MEMBER_DETAILS));
            preparedStatement.setString(1, nodeId);
            preparedStatement.setString(2, removedMemberId);
            preparedStatement.setString(3, groupId);
            resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                nodeDetail = new NodeDetail(removedMemberId, groupId, false, 0, false);
            }
            clearMembershipEvents = connection
                    .prepareStatement(queryManager.getQuery(DBQueries.DELETE_REMOVED_MEMBER_DETAIL_FOR_NODE));
            clearMembershipEvents.setString(1, nodeId);
            clearMembershipEvents.setString(2, removedMemberId);
            clearMembershipEvents.setString(3, groupId);
            clearMembershipEvents.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            String errMsg = RDBMSConstantUtils.TASK_GET_ALL_QUEUES;
            throw new ClusterCoordinationException("Error occurred while " + errMsg, e);
        } finally {
            close(resultSet, RDBMSConstantUtils.TASK_GET_ALL_QUEUES);
            close(preparedStatement, RDBMSConstantUtils.TASK_GET_ALL_QUEUES);
            close(clearMembershipEvents, RDBMSConstantUtils.TASK_GET_ALL_QUEUES);
            close(connection, RDBMSConstantUtils.TASK_GET_ALL_QUEUES);
        }
        if (log.isDebugEnabled()) {
            log.debug(RDBMSConstantUtils.TASK_GET_ALL_QUEUES + " of removed nodes in group "
                      + StringUtil.removeCRLFCharacters(groupId) + " executed successfully");
        }
        return nodeDetail;
    }

    @Override
    public NodeDetail getNodeData(String nodeId, String groupId) throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String coordinatorNodeId = getCoordinatorNodeId(groupId);
        if (coordinatorNodeId == null) {
            coordinatorNodeId = getCoordinatorNodeId(groupId);
        }
        NodeDetail nodeDetail = null;

        try {
            connection = getConnection();
            preparedStatement = connection.prepareStatement(queryManager.getQuery(DBQueries.GET_NODE_DATA));
            preparedStatement.setString(1, groupId);
            preparedStatement.setString(2, nodeId);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                boolean isCoordinatorNode = nodeId.equals(coordinatorNodeId);
                long lastHeartbeat = resultSet.getLong(3);
                boolean isNewNode = convertIntToBoolean(resultSet.getInt(4));
                nodeDetail = new NodeDetail(nodeId, groupId, isCoordinatorNode, lastHeartbeat, isNewNode);
            }
        } catch (SQLException e) {
            String errMsg = RDBMSConstantUtils.TASK_GET_ALL_QUEUES;
            throw new ClusterCoordinationException("Error occurred while " + errMsg, e);
        } finally {
            close(resultSet, RDBMSConstantUtils.TASK_GET_ALL_QUEUES);
            close(preparedStatement, RDBMSConstantUtils.TASK_GET_ALL_QUEUES);
            close(connection, RDBMSConstantUtils.TASK_GET_ALL_QUEUES);
        }
        if (log.isDebugEnabled()) {
            log.debug("getting node data of node " + StringUtil.removeCRLFCharacters(nodeId) +
                      " executed successfully");
        }
        return nodeDetail;
    }

    @Override
    public void removeNode(String nodeId, String groupId) throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        try {
            connection = getConnection();
            preparedStatement = connection.prepareStatement(queryManager.getQuery(
                    DBQueries.DELETE_NODE_HEARTBEAT));
            preparedStatement.setString(1, nodeId);
            preparedStatement.setString(2, groupId);
            preparedStatement.executeUpdate();
            connection.commit();
            if (log.isDebugEnabled()) {
                log.debug(RDBMSConstantUtils.TASK_REMOVE_NODE_HEARTBEAT + " of node "
                          + StringUtil.removeCRLFCharacters(nodeId) + " executed successfully");
            }
        } catch (SQLException e) {
            rollback(connection, RDBMSConstantUtils.TASK_REMOVE_NODE_HEARTBEAT);
            throw new ClusterCoordinationException("error occurred while "
                                                   + RDBMSConstantUtils.TASK_REMOVE_NODE_HEARTBEAT, e);
        } finally {
            close(preparedStatement, RDBMSConstantUtils.TASK_REMOVE_NODE_HEARTBEAT);
            close(connection, RDBMSConstantUtils.TASK_REMOVE_NODE_HEARTBEAT);
        }
    }

    @Override
    public void insertRemovedNodeDetails(String removedMember, String groupId, List<String> clusterNodes)
            throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement storeRemovedMembersPreparedStatement = null;
        String task = "Storing removed member: " + removedMember + " in group " + groupId;
        try {
            connection = getConnection();
            storeRemovedMembersPreparedStatement = connection
                    .prepareStatement(queryManager.getQuery(DBQueries.INSERT_REMOVED_MEMBER_DETAILS));

            for (String clusterNode : clusterNodes) {
                storeRemovedMembersPreparedStatement.setString(1, clusterNode);
                storeRemovedMembersPreparedStatement.setString(2, groupId);
                storeRemovedMembersPreparedStatement.setString(3, removedMember);
                storeRemovedMembersPreparedStatement.addBatch();
            }
            storeRemovedMembersPreparedStatement.executeBatch();
            connection.commit();
            if (log.isDebugEnabled()) {
                log.debug(StringUtil.removeCRLFCharacters(task) + " executed successfully");
            }
        } catch (SQLException e) {
            rollback(connection, task);
            throw new ClusterCoordinationException(
                    "Error storing removed member: " + removedMember + " in group " + groupId, e);
        } finally {
            close(storeRemovedMembersPreparedStatement, task);
            close(connection, task);
        }
    }

    @Override
    public void markNodeAsNotNew(String nodeId, String groupId) throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        try {
            connection = getConnection();
            preparedStatement = connection.prepareStatement(queryManager.getQuery(DBQueries.MARK_NODE_NOT_NEW));
            preparedStatement.setString(1, nodeId);
            preparedStatement.setString(2, groupId);
            int updateCount = preparedStatement.executeUpdate();
            if (updateCount == 0) {
                log.warn("No record was updated while marking node as not new");
            }
            connection.commit();
            if (log.isDebugEnabled()) {
                log.debug(RDBMSConstantUtils.TASK_MARK_NODE_NOT_NEW + " of node "
                          + StringUtil.removeCRLFCharacters(nodeId) + " executed successfully");
            }
        } catch (SQLException e) {
            rollback(connection, RDBMSConstantUtils.TASK_MARK_NODE_NOT_NEW);
            throw new ClusterCoordinationException("Error occurred while " + RDBMSConstantUtils.TASK_MARK_NODE_NOT_NEW, e);
        } finally {
            close(preparedStatement, RDBMSConstantUtils.TASK_MARK_NODE_NOT_NEW);
            close(connection, RDBMSConstantUtils.TASK_MARK_NODE_NOT_NEW);
        }
    }


    @Override
    public void clearHeartBeatData() throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement clearNodeHeartbeatData = null;
        PreparedStatement clearCoordinatorHeartbeatData = null;
        String task = "Clearing all heartbeat data";
        try {
            connection = getConnection();
            clearNodeHeartbeatData = connection.prepareStatement(queryManager.getQuery(
                    DBQueries.CLEAR_NODE_HEARTBEATS));
            clearNodeHeartbeatData.executeUpdate();

            clearCoordinatorHeartbeatData = connection.prepareStatement(queryManager.getQuery(
                    DBQueries.CLEAR_COORDINATOR_HEARTBEAT));
            clearCoordinatorHeartbeatData.executeUpdate();
            connection.commit();
            if (log.isDebugEnabled()) {
                log.debug(task + " executed successfully");
            }
        } catch (SQLException e) {
            rollback(connection, task);
            throw new ClusterCoordinationException("Error occurred while " + task, e);
        } finally {
            close(clearNodeHeartbeatData, task);
            close(clearCoordinatorHeartbeatData, task);
            close(connection, task);
        }
    }

    @Override
    public void clearMembershipEvents(String nodeID, String groupID) throws ClusterCoordinationException {
        Connection connection = null;
        PreparedStatement clearMembershipEvents = null;
        String task = "Clearing all membership events for node: " + nodeID;
        try {
            connection = getConnection();
            clearMembershipEvents = connection.prepareStatement(queryManager.getQuery(
                    DBQueries.CLEAN_MEMBERSHIP_EVENTS_FOR_NODE));
            clearMembershipEvents.setString(1, nodeID);
            clearMembershipEvents.executeUpdate();
            connection.commit();
            if (log.isDebugEnabled()) {
                log.debug(task + " executed successfully");
            }
        } catch (SQLException e) {
            rollback(connection, task);
            throw new ClusterCoordinationException("Error occurred while " + task, e);
        } finally {
            close(clearMembershipEvents, task);
            close(connection, task);
        }
    }

    /**
     * Get the connection to the database.
     */
    private Connection getConnection() throws SQLException {
        Connection connection = datasource.getConnection();
        connection.setAutoCommit(false);
        return connection;
    }

    /**
     * Close the resultset.
     *
     * @param resultSet The resultset which should be closed
     * @param task      The task which was running
     */
    private void close(ResultSet resultSet, String task) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                log.error("Closing result set failed after " + task, e);
            }
        }
    }

    /**
     * close the connection.
     *
     * @param connection The connection to be closed
     * @param task       The task which was running
     */
    private void close(Connection connection, String task) {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.rollback(); // to prevent inconsistencies in select statements
                connection.close();
            }
        } catch (SQLException e) {
            log.error("Failed to close connection after " + StringUtil.removeCRLFCharacters(task), e);
        }
    }

    /**
     * Close the prepared statement.
     *
     * @param preparedStatement The statement to be closed
     * @param task              The task which was running
     */
    private void close(Statement preparedStatement, String task) {
        if (preparedStatement != null) {
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                log.error("Closing prepared statement failed after " + StringUtil.removeCRLFCharacters(task), e);
            }
        }
    }

    /**
     * The rollback method.
     *
     * @param connection The connection object which the rollback should be applied to
     * @param task       The task which was running
     */
    private void rollback(Connection connection, String task) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException e) {
                log.warn("Rollback failed on " + StringUtil.removeCRLFCharacters(task), e);
            }
        }
    }

    /**
     * Convert a value to boolean.
     *
     * @param value the value to be converted to boolean
     * @return the converted boolean
     */
    private boolean convertIntToBoolean(int value) {
        return value != 0;
    }

}
