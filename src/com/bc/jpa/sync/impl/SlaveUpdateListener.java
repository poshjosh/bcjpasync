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

import com.bc.jpa.JpaMetaData;
import com.bc.jpa.sync.predicates.GetEntityTypes;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.PostLoad;
import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;
import javax.persistence.PrePersist;
import javax.persistence.PreRemove;
import javax.persistence.PreUpdate;
import com.bc.jpa.sync.PendingUpdatesManager;

/**
 * @author Chinomso Bassey Ikwuagwu on Jul 29, 2017 8:59:07 AM
 */
public class SlaveUpdateListener {

    private static final Logger logger = Logger.getLogger(SlaveUpdateListener.class.getName());
    
    private final PendingUpdatesManager pendingUpdatesManager;

    private final List<Class> masterTypes;
    
    public SlaveUpdateListener(PendingUpdatesManager updateManager, 
            JpaMetaData metaData, Predicate<String> masterPersistenceUnitTest) {
        this(updateManager, 
                metaData == null ? Collections.EMPTY_LIST : 
                Collections.unmodifiableList(new GetEntityTypes(masterPersistenceUnitTest).apply(metaData)));
    }
    
    public SlaveUpdateListener(PendingUpdatesManager slaveUpdates, List<Class> masterTypes) {
        this.pendingUpdatesManager = Objects.requireNonNull(slaveUpdates);
        this.masterTypes = Objects.requireNonNull(masterTypes);
    }
    
    @PrePersist public void onPrePersist(Object o) {}
    @PostPersist public void onPostPersist(Object o) {
        if(this.mayUpdateSlave(o)) {
            logger.log(Level.FINE, "Requesting persist; slave of: {0}", o);
            pendingUpdatesManager.addPersist(o);
        }
    }
    @PostLoad public void onPostLoad(Object o) { }
    @PreUpdate public void onPreUpdate(Object o) {}
    @PostUpdate public void onPostUpdate(Object o) {
        if(this.mayUpdateSlave(o)) {
            logger.log(Level.FINE, "Requesting merge; slave of: {0}", o);
            pendingUpdatesManager.addMerge(o);
        }
    }
    @PreRemove public void onPreRemove(Object o) {}
    @PostRemove public void onPostRemove(Object o) {
        if(this.mayUpdateSlave(o)) {
            logger.log(Level.FINE, "Requesting remove; slave of: {0}", o);
            pendingUpdatesManager.addRemove(o);
        }
    }
    
    public boolean mayUpdateSlave(Object entity) {
        return pendingUpdatesManager != null && this.isMasterType(entity);
    }
    
    public boolean isMasterType(Object entity) {
        for(Class cls : masterTypes) {
            if(cls.isAssignableFrom(entity.getClass())) {
                return true;
            }
        }
        return false;
    }
}
