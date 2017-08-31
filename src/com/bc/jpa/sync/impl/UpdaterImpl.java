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

import com.bc.jpa.EntityUpdater;
import com.bc.jpa.JpaContext;
import com.bc.jpa.JpaUtil;
import com.bc.jpa.sync.MasterSlaveTypes;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import java.util.List;
import com.bc.jpa.sync.Updater;

/**
 * @author Chinomso Bassey Ikwuagwu on Mar 9, 2017 10:53:46 PM
 */
public class UpdaterImpl implements Updater {
    
    private transient static final Logger logger = Logger.getLogger(UpdaterImpl.class.getName());
    
    private final int merge = 1;
    private final int persist = 2;
    private final int remove = 3;
    
    private final JpaContext jpaContext;

    private final MasterSlaveTypes masterSlaveTypes;

    public UpdaterImpl(JpaContext jpaContext) {
        this(jpaContext, (persistencUnit) -> true);
    }
    
    public UpdaterImpl(JpaContext jpaContext, Predicate<String> persistenceUnitTest) {
        this(jpaContext, new MasterSlaveTypesImpl(jpaContext.getMetaData(), persistenceUnitTest));
    }
    
    public UpdaterImpl(
            JpaContext jpaContext, 
            Predicate<String> masterPersistenceUnitFilter,
            Predicate<String> slavePersistenceUnitFilter) {
        this(jpaContext, new MasterSlaveTypesImpl(jpaContext.getMetaData(), masterPersistenceUnitFilter, slavePersistenceUnitFilter));
    }

    public UpdaterImpl(JpaContext jpaContext, MasterSlaveTypes masterSlaveTypes) {
        this.jpaContext = Objects.requireNonNull(jpaContext);
        this.masterSlaveTypes = Objects.requireNonNull(masterSlaveTypes);
    }
    
    @Override
    public Object update(Object entity, Object entityId) {
        
        entity = this.getSlave(entity);
        
        final EntityManager em = jpaContext.getEntityManager(entity.getClass());
        
        try{
            
            if(em.find(entity.getClass(), entityId) == null) {

                return this.beginUpdateCommitAndClose(em, entity, persist);

            }else{

                return this.beginUpdateCommitAndClose(em, entity, merge);
            }
        }finally{
            if(em.isOpen()) {
                em.close();
            }
        }
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

    private Object update(Object entity, int type) {

        entity = this.getSlave(entity);

        final EntityManager em = jpaContext.getEntityManager(entity.getClass());
        
        try{
        
            return this.beginUpdateCommitAndClose(em, entity, type);

        }finally{
            if(em.isOpen()) {
                em.close();
            }
        }
    }    

    private Object beginUpdateCommitAndClose(EntityManager em, Object entity, int type) {
        Object output;
        try{

            em.getTransaction().begin();
            
            switch(type) {
                case merge: 
                    logger.log(Level.FINE, "Merging: {0}", entity);
                    try{
                        output = em.merge(entity);
                    }catch(Exception e) {
System.err.println("---------------- CAUGHT EXCEPTION WHILE MERGING ---------------\n" + e);                        
                        final Class entityType = entity.getClass();
                        final EntityUpdater updater = jpaContext.getEntityUpdater(entityType);
                        final Object found = em.find(entityType, updater.getId(entity));
                        updater.update(entity, found, false);
                        output = em.merge(found);
System.out.println("----------------   SUCCESSFULLY MERGED ENTITY ---------------");                                                
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
    
    private Object getId(Object entity) {
        final EntityUpdater remoteUpdater = jpaContext.getEntityUpdater(entity.getClass());
        return remoteUpdater.getId(entity);
    }
    
    public Object getSlave(Object master) {
        
        if(!this.masterSlaveTypes.getMasterTypes().contains(master.getClass())) {
            throw new UnsupportedOperationException("Not a master type: "+master.getClass()+", instance: "+master);
        }
        
        final Object slave = this.toSlave(master, null);
        
        if(logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "#getSlave(..) Master: {0}, slave: {1}", 
                    new Object[]{master, slave});
        }
        
        Objects.requireNonNull(slave);
        
        return slave;
    }
    
    public Object toSlave(Object master, Object outputIfNone) {
        final List<Class> slaveTypes = masterSlaveTypes.getSlaveTypes();
        Object output = outputIfNone;
        final Class masterType = master.getClass();
        if(slaveTypes.contains(masterType)) {
            output = master;
        }else{
            final Class slaveType = masterSlaveTypes.getSlaveType(masterType, null);
            if(slaveType != null) {
                output = this.createSlave(master, masterType, slaveType);
            }
        }
        return output;
    }
    
    public Object createSlave(Object master, Class masterType, Class slaveType) {
        
        final Object slave = this.newInstance(slaveType);
        
        logger.finer(() -> "Source: " + master + ", target: " + slave);

        final EntityUpdater masterUpdater = jpaContext.getEntityUpdater(masterType);
        final EntityUpdater slaveUpdater = jpaContext.getEntityUpdater(slaveType);
        final String [] columnNames = jpaContext.getMetaData().getColumnNames(slaveType);
        
        this.removeManyToOnes(master);
        
        for(String columnName : columnNames) {
            final Object masterValue = masterUpdater.getValue(master, columnName);
            final boolean selfReference = master.equals(masterValue);
            final Object slaveValue;
            if(masterValue == null) {
                slaveValue = masterValue;
            }else if(selfReference) {
                slaveValue = slave;
            }else if(masterValue instanceof Collection) {
                final Collection masterCollection = (Collection)masterValue;
                final Collection slaveCollection = (Collection)this.newInstance(masterCollection.getClass());
                for(Object e : masterCollection) {
                    final Object slaveE = this.toSlave(e, e);
                    slaveCollection.add(slaveE);
                }
                slaveValue = slaveCollection;
            }else{
                slaveValue = this.toSlave(masterValue, masterValue);
            }
//System.out.println("#setRemoteValue( " + remote + ", " + columnName + " ): "+remoteValue);                    
            slaveUpdater.setValue(slave, columnName, slaveValue);
        }
        
        return slave;
    }
    
    private void removeManyToOnes(Object entity) {
        final Class refClass = entity.getClass();
        final Class [] refingClasses = jpaContext.getMetaData().getReferencingClasses(refClass);
        if(refingClasses != null && refingClasses.length != 0) {
            for(Class refingClass : refingClasses) {
                final Method setter = JpaUtil.getMethod(true, refClass, refingClass);
                if(setter == null) {
                    continue;
                }
                try{
                    setter.invoke(entity, new Object[]{null});
                }catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    logger.log(Level.WARNING, "Error invoking "+setter.getName()+" with argument: null", e);
                }
            }
        }
    }

    private Object newInstance(Class aClass) {
        try{
            final Object output = aClass.getConstructor().newInstance();
            return output;
        }catch(NoSuchMethodException | SecurityException | InstantiationException | 
                IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
/**
 * 
        try{
            return em.merge(entity);
        }catch(Exception e) {
            try{
                em.persist(entity);
                return entity;
            }catch(Exception ignored) {
                throw e;
            }
        }

        if(entity instanceof Taskresponse) {
            final Taskresponse tr = (Taskresponse)entity;
            if(tr.getAuthor() != null) {
                this.persist(em, tr.getAuthor());
            }
            if(tr.getTask() != null) {
                this.persist(em, tr.getTask());
            }
        }else if(entity instanceof Task){
            final Task t = (Task)entity;
            if(t.getAuthor() != null) {
                this.persist(em, t.getAuthor());
            }
            if(t.getReponsibility() == null) {
                this.persist(em, t.getReponsibility());
            }
            t.setTaskresponseList(Collections.EMPTY_LIST);
        }else if(entity instanceof Doc) {
            final Doc d = (Doc)entity;
            d.setTaskList(Collections.EMPTY_LIST);
        }else if(entity instanceof Appointment) {
            final Appointment a = (Appointment)entity;
            if(a.getParentappointment() != null && !a.getParentappointment().equals(a)) {
                this.persist(em, a.getParentappointment());
            }
            if(a.getUnit() != null) {
                this.persist(em, a.getUnit());
            }
            a.setAppointmentList(Collections.EMPTY_LIST);
            a.setTaskList(Collections.EMPTY_LIST);
            a.setTaskList1(Collections.EMPTY_LIST);
            a.setTaskresponseList(Collections.EMPTY_LIST);
        }else if(entity instanceof Unit) {
            final Unit u = (Unit)entity;
            if(u.getParentunit() != null && !u.getParentunit().equals(u)) {
                this.persist(em, u.getParentunit());
            }
            u.setAppointmentList(Collections.EMPTY_LIST);
            u.setUnitList(Collections.EMPTY_LIST);
        }else{
            throw new UnsupportedOperationException();
        }
 * 
 */
/**
 * 
    private void removeManyToOnes(Object local) {
        if(local instanceof Taskresponse) {
        }else if(local instanceof Task){
            final Task t = (Task)local;
            t.setTaskresponseList(Collections.EMPTY_LIST);
        }else if(local instanceof Doc) {
            final Doc d = (Doc)local;
            d.setTaskList(Collections.EMPTY_LIST);
        }else if(local instanceof Appointment) {
            final Appointment a = (Appointment)local;
            a.setAppointmentList(Collections.EMPTY_LIST);
            a.setTaskList(Collections.EMPTY_LIST);
            a.setTaskList1(Collections.EMPTY_LIST);
            a.setTaskresponseList(Collections.EMPTY_LIST);
        }else if(local instanceof Unit) {
            final Unit u = (Unit)local;
            u.setAppointmentList(Collections.EMPTY_LIST);
            u.setUnitList(Collections.EMPTY_LIST);
        }else{
            throw new UnsupportedOperationException("Unexpected local/master entity: "+local);
        }
    }
 * 
 */