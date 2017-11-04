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
import java.util.function.Predicate;

/**
 * @author Chinomso Bassey Ikwuagwu on Jul 29, 2017 8:59:07 AM
 */
public class SlaveUpdateListener {

    private static final Logger logger = Logger.getLogger(SlaveUpdateListener.class.getName());
    
    private final PendingUpdatesManager pendingUpdatesManager;
    
    private final Predicate entityFilter;

    public SlaveUpdateListener(
            PendingUpdatesManager pendingUpdatesManager, Predicate entityFilter) {
        this.pendingUpdatesManager = pendingUpdatesManager;
        this.entityFilter = entityFilter;
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
        final boolean output = pendingUpdatesManager != null && this.accept(entity);
        return output;
    }
    
    public boolean accept(Object entity) {
        return this.entityFilter != null && this.entityFilter.test(entity);
    }
}
