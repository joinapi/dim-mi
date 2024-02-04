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

package org.wso2.micro.integrator.dataservices.core.script;

import org.wso2.micro.core.util.Pageable;

import java.util.List;

public class PaginatedTableInfo implements Pageable {

    private String[] tableList;
    
    private int numberOfPages;

    public int getNumberOfPages() {
        return numberOfPages;
    }

    public String[] getTableInfo() {
        return tableList;
    }

    public void setTableInfo(String[] tableList) {
        this.tableList = tableList;
    }


    public <T> void set(List<T> items) {
        this.tableList = items.toArray(new String[items.size()]);
    }

    public void setNumberOfPages(int numberOfPages) {
        this.numberOfPages = numberOfPages;
    }
}
