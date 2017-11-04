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
import java.io.Serializable;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;

/**
 * @author Chinomso Bassey Ikwuagwu on Oct 30, 2017 10:05:02 PM
 */
public class UpdaterImpl implements Updater, Serializable {
    
    private transient static final Logger logger = Logger.getLogger(UpdaterImpl.class.getName());
    
    private final int merge = 1;
    private final int persist = 2;
    private final int remove = 3;
    
    private final PersistenceUnitContext context;
    
    private final Function formatter;

    public UpdaterImpl(PersistenceUnitContext context, Function formatter) {
        this.context = Objects.requireNonNull(context);
        this.formatter = Objects.requireNonNull(formatter);
    }
    
    @Override
    public final Object merge(Object entity) {
        return this.update(entity, merge);
    }

    @Override
    public final void persist(Object entity) {
        this.update(entity, persist);
    }
    
    @Override
    public final void remove(Object entity) {
        this.update(entity, remove);
    }

    @Override
    public Object update(Object entity, Object entityId) {
        
        final Object other = this.formatter.apply(entity);
        
        final EntityManager em = this.context.getEntityManager();
        
        try{
            
            final EntityUpdater updater = this.context.getEntityUpdater(other.getClass());
            
            final int type = em.find(other.getClass(), entityId) == null ? persist : merge;
            
            return this.beginUpdateCommitAndClose(em, updater, other, entityId, type);
        }finally{
            if(em.isOpen()) {
                em.close();
            }
        }
    }

    private Object update(Object entity, int type) {

        final Object other = this.formatter.apply(entity);
        
        final EntityManager em = this.context.getEntityManager();
        
        try{
            
            final EntityUpdater updater = context.getEntityUpdater(other.getClass());
            
            return this.beginUpdateCommitAndClose(em, updater, other, null, type);

        }finally{
            if(em.isOpen()) {
                em.close();
            }
        }
    } 
    
    private Object beginUpdateCommitAndClose(
            EntityManager em, EntityUpdater updater, 
            Object entity, Object entityId, int type) {
        Object output;
        try{

            em.getTransaction().begin();
            
            switch(type) {
                case merge: 
                    logger.log(Level.FINE, "Merging: {0}", entity);
                    try{
                        output = em.merge(entity);
                    }catch(Exception e) {
System.err.println(this.getClass().getName()+"\n---------------- CAUGHT EXCEPTION WHILE MERGING ---------------\n" + e);                        
                        final Class entityType = entity.getClass();
                        final Object idToFind = entityId == null ? updater.getId(entity) : entityId;
                        final Object found = em.find(entityType, idToFind);
                        updater.update(entity, found, false);
                        output = em.merge(found);
System.out.println(this.getClass().getName()+"\n----------------   SUCCESSFULLY MERGED ENTITY ---------------");                                                
//                        em.persist(remote);
//                        output = em.find(remote.getClass(), this.getId(remote));
                    }
                    break;
                case persist: 
                    logger.log(Level.FINE, "Persisting: {0}", entity);
                    em.persist(entity); 
                    output = entity; 
                    break;
                case remove: 
                    logger.log(Level.FINE, "Removing: {0}", entity);
                    em.remove(entity); 
                    output = entity; 
                    break;
                default: 
                    throw new UnsupportedOperationException();
            }

            em.getTransaction().commit();

        }finally{
            em.close();
        }
        
        return output;
    }
}
