/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.map.tx;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.PutBackupOperation;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.spi.BackupAwareOperation;
import com.hazelcast.spi.Operation;
import com.hazelcast.util.Clock;

public abstract class BaseTxPutOperation extends TransactionalMapOperation implements BackupAwareOperation {

    protected transient Data dataOldValue;

    public BaseTxPutOperation() {
    }

    protected BaseTxPutOperation(String name, Data dataKey, Data dataValue) {
        super(name, dataKey, dataValue);
    }

    public void innerAfterRun() {
        // TODO: tx map interceptor
//        mapService.interceptAfterProcess(name, MapOperationType.PUT, dataKey, dataValue, dataOldValue);
        int eventType = dataOldValue == null ? EntryEvent.TYPE_ADDED : EntryEvent.TYPE_UPDATED;
        mapService.publishEvent(getCallerAddress(), name, eventType, dataKey, dataOldValue, dataValue);
        invalidateNearCaches();
        if (mapContainer.getMapConfig().isStatisticsEnabled()) {
            mapContainer.getMapOperationCounter().incrementPuts(Clock.currentTimeMillis() - getStartTime());
        }
    }

    public final Operation getBackupOperation() {
        return new PutBackupOperation(name, dataKey, dataValue, ttl);
    }
}