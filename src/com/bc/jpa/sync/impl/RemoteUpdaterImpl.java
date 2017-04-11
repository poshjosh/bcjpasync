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
import com.bc.jpa.JpaMetaData;
import com.bc.jpa.JpaUtil;
import com.bc.jpa.sync.RemoteUpdater;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;

/**
 * @author Chinomso Bassey Ikwuagwu on Mar 9, 2017 10:53:46 PM
 */
public class RemoteUpdaterImpl implements RemoteUpdater {
    
    private transient static final Logger logger = Logger.getLogger(RemoteUpdaterImpl.class.getName());
    
    private final int merge = 1;
    private final int persist = 2;
    private final int remove = 3;
    
    private final JpaContext jpa;

    private final List<Class> masterTypes;
    private final List<Class> slaveTypes;
    
    public RemoteUpdaterImpl(
            JpaContext jpa, 
            Predicate<String> masterPersistenceUnitFilter,
            Predicate<String> slavePersistenceUnitFilter) {
        this.jpa = Objects.requireNonNull(jpa);
        this.slaveTypes = new ArrayList();
        this.masterTypes = new ArrayList();
        final JpaMetaData metaData = this.jpa.getMetaData();
        final String [] puNames = metaData.getPersistenceUnitNames();
        for(String puName : puNames) {
            if(masterPersistenceUnitFilter.test(puName)) {
                final Class [] puClasses = metaData.getEntityClasses(puName); 
                this.masterTypes.addAll(Arrays.asList(puClasses));
            }
            if(masterPersistenceUnitFilter.test(puName)) {
                final Class [] puClasses = metaData.getEntityClasses(puName); 
                this.slaveTypes.addAll(Arrays.asList(puClasses));
            }
        }
        logger.log(Level.FINE, "Master types: {0}", masterTypes);
        logger.log(Level.FINE, "Slave types: {0}", slaveTypes);
    }
    
    @Override
    public Object update(Object entity, Object entityId) {
        
        entity = this.getRemoteEntity(entity);
        
        final EntityManager em = jpa.getEntityManager(entity.getClass());
        
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

        entity = this.getRemoteEntity(entity);

        final EntityManager em = jpa.getEntityManager(entity.getClass());
        
        try{
        
            return this.beginUpdateCommitAndClose(em, entity, type);

        }finally{
            if(em.isOpen()) {
                em.close();
            }
        }
    }    

    private Object beginUpdateCommitAndClose(EntityManager em, Object remote, int type) {
        Object output;
        try{

            em.getTransaction().begin();
            
            switch(type) {
                case merge: 
                    logger.log(Level.FINE, "Merging: {0}", remote);
                    output = this.merge(em, remote); break;
                case persist: 
                    logger.log(Level.FINE, "Persisting: {0}", remote);
                    this.persist(em, remote); output = remote; break;
                case remove: 
                    logger.log(Level.FINE, "Removing: {0}", remote);
                    this.remove(em, remote); output = remote; break;
                default: 
                    throw new UnsupportedOperationException();
            }

            em.getTransaction().commit();

        }finally{
            em.close();
        }
        
        return output;
    }
    
    public Object merge(EntityManager em, Object remote) {
        try{
            return em.merge(remote);
        }catch(Exception e) {
            this.persist(em, remote);
            final EntityUpdater remoteUpdater = jpa.getEntityUpdater(remote.getClass());
            return em.find(remote.getClass(), remoteUpdater.getId(remote));
        }
    }
    
    public void persist(EntityManager em, Object remote) {
        em.persist(remote);
    }

    public void remove(EntityManager em, Object remote) {
        em.remove(remote);
    }
    
    public Object getRemoteEntity(Object local) {
        
        if(!this.masterTypes.contains(local.getClass())) {
            throw new UnsupportedOperationException("Not a master type: "+local.getClass()+", instance: "+local);
        }
        
        final Object remote = this.getRemote(local, null);
        
        if(logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "#getRemote(..) Local: {0}, remote: {1}", 
                    new Object[]{local, remote});
        }
        
        Objects.requireNonNull(remote);
        
        return remote;
    }
    
    public Object getRemote(Object local, Object outputIfNone) {
        
        Object output = outputIfNone;
        final Class localType = local.getClass();
        if(slaveTypes.contains(localType)) {
            output = local;
        }else{
            for(Class remoteType : slaveTypes) {
                if(remoteType.getSimpleName().equals(localType.getSimpleName())) {
                    final Object remoteEntity = this.newInstance(remoteType);
                    this.updateRemote(local, localType, remoteEntity, remoteType);
                    output = remoteEntity;
                    break;
                }
            }
        }
        return output;
    }
    
    public void updateRemote(Object local, Class localType, Object remote, Class remoteType) {
        
//System.out.println("#updateRemote( " + local + ", " + remote + " )");        
        final EntityUpdater masterUpdater = jpa.getEntityUpdater(localType);
        final EntityUpdater slaveUpdater = jpa.getEntityUpdater(remoteType);
        final String [] columnNames = jpa.getMetaData().getColumnNames(remoteType);
        
        this.removeManyToOnes(local);
        
        for(String columnName : columnNames) {
            final Object localValue = masterUpdater.getValue(local, columnName);
            final boolean selfReference = local.equals(localValue);
            final Object remoteValue;
            if(localValue == null) {
                remoteValue = localValue;
            }else if(selfReference) {
                remoteValue = remote;
            }else if(localValue instanceof Collection) {
                final Collection localCollection = (Collection)localValue;
                final Collection remoteCollection = (Collection)this.newInstance(localCollection.getClass());
                for(Object e : localCollection) {
                    final Object remoteE = this.getRemote(e, e);
                    remoteCollection.add(remoteE);
                }
                remoteValue = remoteCollection;
            }else{
                remoteValue = this.getRemote(localValue, localValue);
            }
//System.out.println("#setRemoteValue( " + remote + ", " + columnName + " ): "+remoteValue);                    
            slaveUpdater.setValue(remote, columnName, remoteValue);
        }
    }
    
    private void removeManyToOnes(Object local) {
        final Class refClass = local.getClass();
        final Class [] refingClasses = jpa.getMetaData().getReferencingClasses(refClass);
        if(refingClasses != null && refingClasses.length != 0) {
            for(Class refingClass : refingClasses) {
                final Method setter = JpaUtil.getMethod(true, refClass, refingClass);
                if(setter == null) {
                    continue;
                }
                try{
                    setter.invoke(local, new Object[]{null});
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