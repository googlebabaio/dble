/*
 * Copyright (C) 2016-2020 ActionTech.
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher.
 */

package com.actiontech.dble.route.parser.druid.impl;

import com.actiontech.dble.DbleServer;
import com.actiontech.dble.backend.mysql.nio.handler.FetchStoreNodeOfChildTableHandler;
import com.actiontech.dble.config.ErrorCode;
import com.actiontech.dble.config.model.SchemaConfig;
import com.actiontech.dble.config.model.TableConfig;
import com.actiontech.dble.meta.TableMeta;
import com.actiontech.dble.net.ConnectionException;
import com.actiontech.dble.route.RouteResultset;
import com.actiontech.dble.route.util.RouterUtil;
import com.actiontech.dble.server.ServerConnection;
import com.actiontech.dble.server.handler.ExplainHandler;
import com.actiontech.dble.singleton.ProxyMeta;
import com.actiontech.dble.sqlengine.SQLJob;
import com.actiontech.dble.sqlengine.mpp.ColumnRoute;
import com.actiontech.dble.util.StringUtil;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.expr.SQLIntegerExpr;
import com.alibaba.druid.sql.ast.expr.SQLNullExpr;

import java.sql.SQLNonTransientException;
import java.util.List;
import java.util.Set;

import static com.actiontech.dble.server.util.SchemaUtil.SchemaInfo;

abstract class DruidInsertReplaceParser extends DefaultDruidParser {
    static RouteResultset routeByERParentColumn(RouteResultset rrs, TableConfig tc, String joinColumnVal, SchemaInfo schemaInfo)
            throws SQLNonTransientException {
        if (tc.getDirectRouteTC() != null) {
            ColumnRoute columnRoute = new ColumnRoute(joinColumnVal);
            checkDefaultValues(joinColumnVal, tc, schemaInfo.getSchema(), tc.getJoinColumn());
            Set<String> shardingNodeSet = RouterUtil.ruleCalculate(rrs, tc.getDirectRouteTC(), columnRoute, false);
            if (shardingNodeSet.size() != 1) {
                throw new SQLNonTransientException("parent key can't find  valid data node ,expect 1 but found: " + shardingNodeSet.size());
            }
            String dn = shardingNodeSet.iterator().next();
            if (SQLJob.LOGGER.isDebugEnabled()) {
                SQLJob.LOGGER.debug("found partion node (using parent partition rule directly) for child table to insert  " + dn + " sql :" + rrs.getStatement());
            }
            return RouterUtil.routeToSingleNode(rrs, dn);
        }
        return null;
    }


    /**
     * check if the column is not null and the
     */
    static void checkDefaultValues(String columnValue, TableConfig tableConfig, String schema, String partitionColumn) throws SQLNonTransientException {
        if (columnValue == null || "null".equalsIgnoreCase(columnValue)) {
            TableMeta meta = ProxyMeta.getInstance().getTmManager().getSyncTableMeta(schema, tableConfig.getName());
            for (TableMeta.ColumnMeta columnMeta : meta.getColumns()) {
                if (!columnMeta.isCanNull()) {
                    if (columnMeta.getName().equalsIgnoreCase(partitionColumn)) {
                        String msg = "Sharding column can't be null when the table in MySQL column is not null";
                        LOGGER.info(msg);
                        throw new SQLNonTransientException(msg);
                    }
                }
            }
        }
    }

    static String shardingValueToSting(SQLExpr valueExpr) throws SQLNonTransientException {
        String shardingValue = null;
        if (valueExpr instanceof SQLIntegerExpr) {
            SQLIntegerExpr intExpr = (SQLIntegerExpr) valueExpr;
            shardingValue = intExpr.getNumber() + "";
        } else if (valueExpr instanceof SQLCharExpr) {
            SQLCharExpr charExpr = (SQLCharExpr) valueExpr;
            shardingValue = charExpr.getText();
        }

        if (shardingValue == null && !(valueExpr instanceof SQLNullExpr)) {
            throw new SQLNonTransientException("Not Supported of Sharding Value EXPR :" + valueExpr.toString());
        }
        return shardingValue;
    }


    int getIncrementKeyIndex(SchemaInfo schemaInfo, String incrementColumn) throws SQLNonTransientException {
        if (incrementColumn == null) {
            throw new SQLNonTransientException("please make sure the incrementColumn's config is not null in schemal.xml");
        }
        TableMeta tbMeta = ProxyMeta.getInstance().getTmManager().getSyncTableMeta(schemaInfo.getSchema(),
                schemaInfo.getTable());
        if (tbMeta != null) {
            for (int i = 0; i < tbMeta.getColumns().size(); i++) {
                if (incrementColumn.equalsIgnoreCase(tbMeta.getColumns().get(i).getName())) {
                    return i;
                }
            }
            String msg = "please make sure your table structure has incrementColumn";
            LOGGER.info(msg);
            throw new SQLNonTransientException(msg);
        }
        return -1;
    }

    int getTableColumns(SchemaInfo schemaInfo, List<SQLExpr> columnExprList)
            throws SQLNonTransientException {
        if (columnExprList == null || columnExprList.size() == 0) {
            TableMeta tbMeta = ProxyMeta.getInstance().getTmManager().getSyncTableMeta(schemaInfo.getSchema(), schemaInfo.getTable());
            if (tbMeta == null) {
                String msg = "Meta data of table '" + schemaInfo.getSchema() + "." + schemaInfo.getTable() + "' doesn't exist";
                LOGGER.info(msg);
                throw new SQLNonTransientException(msg);
            }
            return tbMeta.getColumns().size();
        } else {
            return columnExprList.size();
        }
    }

    int getShardingColIndex(SchemaInfo schemaInfo, List<SQLExpr> columnExprList, String partitionColumn) throws SQLNonTransientException {
        int shardingColIndex = -1;
        if (columnExprList == null || columnExprList.size() == 0) {
            TableMeta tbMeta = ProxyMeta.getInstance().getTmManager().getSyncTableMeta(schemaInfo.getSchema(), schemaInfo.getTable());
            if (tbMeta != null) {
                for (int i = 0; i < tbMeta.getColumns().size(); i++) {
                    if (partitionColumn.equalsIgnoreCase(tbMeta.getColumns().get(i).getName())) {
                        return i;
                    }
                }
            }
            return shardingColIndex;
        }
        for (int i = 0; i < columnExprList.size(); i++) {
            if (partitionColumn.equalsIgnoreCase(StringUtil.removeBackQuote(columnExprList.get(i).toString()))) {
                return i;
            }
        }
        return shardingColIndex;
    }


    void fetchChildTableToRoute(TableConfig tc, String joinColumnVal, ServerConnection sc, SchemaConfig schema, String sql, RouteResultset rrs, boolean isExplain) {
        DbleServer.getInstance().getComplexQueryExecutor().execute(new Runnable() {
            //get child result will be blocked, so use ComplexQueryExecutor
            @Override
            public void run() {
                // route by sql query root parent's data node
                String findRootTBSql = tc.getLocateRTableKeySql() + joinColumnVal;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("to find root parent's node sql :" + findRootTBSql);
                }
                FetchStoreNodeOfChildTableHandler fetchHandler = new FetchStoreNodeOfChildTableHandler(findRootTBSql, sc.getSession2());
                try {
                    String dn = fetchHandler.execute(schema.getName(), tc.getRootParent().getShardingNodes());
                    if (dn == null) {
                        sc.writeErrMessage(ErrorCode.ER_UNKNOWN_ERROR, "can't find (root) parent sharding node for sql:" + sql);
                        return;
                    }
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("found partition node for child table to insert " + dn + " sql :" + sql);
                    }
                    RouterUtil.routeToSingleNode(rrs, dn);
                    if (isExplain) {
                        ExplainHandler.writeOutHeadAndEof(sc, rrs);
                    } else {
                        sc.getSession2().execute(rrs);
                    }
                } catch (ConnectionException e) {
                    sc.setTxInterrupt(e.toString());
                    sc.writeErrMessage(ErrorCode.ER_UNKNOWN_ERROR, e.toString());
                }
            }
        });
    }
}
