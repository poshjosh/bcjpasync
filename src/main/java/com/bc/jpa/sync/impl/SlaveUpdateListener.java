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

import com.bc.jpa.sync.MasterSlavePersistenceContext;
import java.util.logging.Logger;
import javax.persistence.PostLoad;
import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;
import javax.persistence.PrePersist;
import javax.persistence.PreRemove;
import javax.persistence.PreUpdate;
import java.util.function.Predicate;
import com.bc.jpa.DatabaseUpdater;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Chinomso Bassey Ikwuagwu on Jul 29, 2017 8:59:07 AM
 */
public class SlaveUpdateListener implements Serializable {

    private static transient final Logger logger = Logger.getLogger(SlaveUpdateListener.class.getName());
    
    private static transient final AtomicBoolean disabled = new AtomicBoolean(false);
    
    private final DatabaseUpdater updater;
    
    private final Predicate entityFilter;
    
    public SlaveUpdateListener(
            Predicate entityFilter, MasterSlavePersistenceContext masterSlaveContext) {
        this(
                entityFilter, 
                masterSlaveContext == null || !masterSlaveContext.getSlaveOptional().isPresent() ? 
                        null : new SlaveUpdater(masterSlaveContext)
        );
    }
    
    public SlaveUpdateListener(Predicate entityFilter, DatabaseUpdater updater) {
        this.entityFilter = entityFilter;
        this.updater = updater;
    }

    @PrePersist public void onPrePersist(Object o) {}
    @PostPersist public void onPostPersist(Object o) {
        if(!disabled.get() && this.mayUpdateSlave(o)) {
            logger.fine(() -> "PERSISTING: " + o);
            try{
                disabled.set(true);
                this.updater.persistIfNotFound(o);
            }finally{
                disabled.set(false);
            }
        }
    }
    @PostLoad public void onPostLoad(Object o) { }
    @PreUpdate public void onPreUpdate(Object o) {}
    @PostUpdate public void onPostUpdate(Object o) {
        if(!disabled.get() && this.mayUpdateSlave(o)) {
            logger.fine(() -> "UPDATING: " + o);
            try{
                disabled.set(true);
                this.updater.updateOrPersistIfNotFound(o);
            }finally{
                disabled.set(false);
            }
        }
    }
    @PreRemove public void onPreRemove(Object o) {}
    @PostRemove public void onPostRemove(Object o) {
        if(!disabled.get() && this.mayUpdateSlave(o)) {
            logger.fine(() -> "REMOVING: " + o);
            try{
                disabled.set(true);
                this.updater.removeIfFound(o);
            }finally{
                disabled.set(false);
            }
        }
    }
    
    public boolean mayUpdateSlave(Object entity) {
        final boolean output = this.updater != null && this.accept(entity);
        return output;
    }
    
    public boolean accept(Object entity) {
        return this.entityFilter != null && this.entityFilter.test(entity);
    }
}
