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

import com.bc.jpa.sync.Updater;
import com.bc.jpa.EntityUpdater;
import com.bc.jpa.context.PersistenceUnitContext;
import com.bc.jpa.search.QuerySearchResults;
import com.bc.jpa.search.SearchResults;
import com.bc.jpa.sync.JpaSync;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import java.util.Set;

/**
 * @author Chinomso Bassey Ikwuagwu on Mar 8, 2017 9:57:01 PM
 */
public class JpaSyncImpl implements JpaSync {
    
    private transient static final Logger logger = Logger.getLogger(JpaSyncImpl.class.getName());
    
    private volatile boolean running;
    
    private final PersistenceUnitContext master;
    
    private final PersistenceUnitContext slave;
    
    private final int pageSize;
    
    private int retrialsOnCommunicationsFailure;
    
    private final int maxRetrialsOnCommunicationsFailure = 100;
    
    private final int intervalBetweenCommunicationsFailureMillis = 5000;
    
    private final Predicate<Throwable> commsLinkFailureTest;
    
    private final Updater remoteUpdater;

    public JpaSyncImpl(
            PersistenceUnitContext master, 
            PersistenceUnitContext slave, 
            int pageSize, Predicate<Throwable> commsLinkFailureTest) {
        this.master = Objects.requireNonNull(master);
        this.slave = Objects.requireNonNull(slave);
        this.remoteUpdater = Objects.requireNonNull(new SlaveUpdater(master, slave));
        this.pageSize = pageSize;
        this.commsLinkFailureTest = commsLinkFailureTest;
    }
    
    @Override
    public boolean isRunning() {
        return running;
    }
    
    @Override
    public synchronized Map<Class, Integer> sync(Set<Class> entityTypes) {
        return this.sync(entityTypes, true);
    }
        
    public synchronized Map<Class, Integer> sync(Set<Class> entityTypes, boolean setStoppedOnComplete) {    
        try{

            running = true;

            final Map<Class, Integer> output = new HashMap();

            for(Class entityType : entityTypes) {
 
                final int entityUpdateCount = this.sync(entityType, false);
                
                output.put(entityType, entityUpdateCount);
            }

            return output.isEmpty() ? Collections.EMPTY_MAP : Collections.unmodifiableMap(output);
            
        }finally{
            if(setStoppedOnComplete) {
                running = false;
            }
        }
    }

    @Override
    public synchronized Integer sync(Class entityType) {
        return this.sync(entityType, true);
    }
        
    public synchronized Integer sync(Class entityType, boolean setStoppedOnComplete) { 
        
        logger.log(Level.FINE, "Syncing: {0}", entityType.getName());
        
        int entityUpdateCount = 0;

        EntityManager masterEm = null;
        
        try{
            
            running = true;
            
            masterEm = master.getEntityManager();
      
            final CriteriaBuilder cb = masterEm.getCriteriaBuilder();
            
            final CriteriaQuery cq = cb.createQuery(entityType);

            final String idColumnName = master.getMetaData().getIdColumnName(entityType);
            
            cq.orderBy(cb.asc(cq.from(entityType).get(idColumnName)));
            
            final TypedQuery tq = masterEm.createQuery(cq);
            
            final SearchResults sr = new QuerySearchResults(tq, pageSize, false);
            
            logger.log(Level.FINE, "Number of records to sync: {0}", sr.getSize());

            final EntityUpdater updater = master.getEntityUpdater(entityType);

            outer:
            for(int pageNumber=0; pageNumber<sr.getPageCount(); pageNumber++) {

                final List pageResults = sr.getPage(pageNumber);

                int lastIndex = -1;
                
                for(int index=0; index<pageResults.size(); index++) {
//System.out.println(entityType.getName()+". Page_"+pageNumber+"("+index+") at: "+this.getClass().getName()+" on "+new Date());
                    final boolean retrying = index == lastIndex;
                    
                    lastIndex = index;
                    
                    final Object entity = pageResults.get(index);
                    
                    if(retrying) {
                        if(logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, 
                                    "Retrying. Page: {0}, index in page: {1}, entity: {2}",
                                    new Object[]{pageNumber, index, entity});
                        }
                    }
                    
                    final Object entityId = updater.getId(entity);

                    try{

                        
                        this.remoteUpdater.update(entity, entityId);
                    
                        ++entityUpdateCount;
                        
                    }catch(Exception e) { 
                        
                        logger.log(Level.WARNING, "For entity: {0}. {1}", new Object[]{entity, e});
                        
                        if(commsLinkFailureTest != null && commsLinkFailureTest.test(e)) {
                            
                            if(++this.retrialsOnCommunicationsFailure >= this.maxRetrialsOnCommunicationsFailure) {
                                
                                break outer;
                                
                            }else{
                                try{
                                    this.wait(intervalBetweenCommunicationsFailureMillis);
                                }catch(InterruptedException ie) {
                                    logger.log(Level.WARNING, "Wait interrupted", ie);
                                }finally{
                                    
                                    this.notifyAll();
                                    
                                    --index;
                                }
                            }
                        }
                    }
                }
            }
        }finally{
            if(setStoppedOnComplete) {
                running = false;
            }
            if(masterEm != null && masterEm.isOpen()) {
                masterEm.close();
            }
        }
        
        final int count = entityUpdateCount;
        logger.fine(() -> "Entity type: "+entityType.getName()+", number of records synced: {0}"+count);

        return entityUpdateCount;
    }
}
