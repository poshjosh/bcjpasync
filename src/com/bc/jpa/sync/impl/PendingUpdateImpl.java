/*
 * Copyright 2017 NUROX Ltd.
 *
 * Licensed under the NUROX Ltd Software License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.looseboxes.com/legal/licenses/software.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bc.jpa.sync.impl;

import com.bc.jpa.sync.PendingUpdate;
import java.io.Serializable;
import java.util.Objects;

/**
 * @author Chinomso Bassey Ikwuagwu on Aug 12, 2017 9:09:28 AM
 */
public class PendingUpdateImpl implements PendingUpdate, Serializable {

    private final long timeCreated;
    
    private final UpdateType updateType;
    
    private final Object entity;

    public PendingUpdateImpl(UpdateType updateType, Object entity) {
        this.timeCreated = System.currentTimeMillis();
        this.updateType = updateType;
        this.entity = Objects.requireNonNull(entity);
    }

    @Override
    public long getTimeCreated() {
        return timeCreated;
    }

    @Override
    public UpdateType getUpdateType() {
        return updateType;
    }

    @Override
    public Object getEntity() {
        return entity;
    }
}
