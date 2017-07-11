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

import com.bc.jpa.sync.SlaveUpdates;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.bc.jpa.sync.RemoteEntityUpdater;

/**
 * @author Chinomso Bassey Ikwuagwu on Mar 7, 2017 7:28:37 PM
 */
public class SlaveUpdatesImpl implements SlaveUpdates {
    
    private transient static final Logger logger = Logger.getLogger(SlaveUpdatesImpl.class.getName());
    
    private volatile boolean stopRequested;
    private volatile boolean paused;
    
    private final int persist = 1;
    private final int merge = 2;
    private final int remove = 3;
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    private final Path backupDir;
    
    private final RemoteEntityUpdater slaveUpdater;
    
    private final Predicate<Throwable> commsLinkFailureTest;
    
    private final Queue<Integer> updateTypes;
    private final Queue entities;
    
    public SlaveUpdatesImpl(Path backupDir, RemoteEntityUpdater slaveUpdater, Predicate<Throwable> commsLinkFailureTest) {
        this.backupDir = Objects.requireNonNull(backupDir);
        this.slaveUpdater = Objects.requireNonNull(slaveUpdater);
        this.commsLinkFailureTest = commsLinkFailureTest;
        final Object [] loaded = this.loadOrCreate();
        
        this.updateTypes = (Queue<Integer>)loaded[0];
        logger.log(Level.INFO, "Pending updates count: {0}", this.updateTypes.size());
        
        this.entities = (Queue)loaded[1];
        logger.log(Level.FINE, "Pending entities: {0}", this.entities);
        
        this.init();
    }
    
    private void init() {
        
        final Thread thread = new Thread(this.getClass().getName()+"_LooperThread") {
            
            @Override
            public void run() {
                
                while(true) {
                    
                    if(stopRequested) {
                        break;
                    }
                    if(paused) {
                        continue;
                    }
                    
                    try{
                        final Integer updateType;
                        final Object entity;
                        try{
                            lock.readLock().lock();
                            updateType = updateTypes.peek();
                            entity = entities.peek();
                        }finally{
                            lock.readLock().unlock();
                        }

                        if(updateType == null) {
                            continue;
                        }

                        try{

                            lock.writeLock().lock();
                            
                            switch(updateType) {
                                case persist: 
                                    slaveUpdater.persist(entity); break;
                                case merge:
                                    slaveUpdater.merge(entity); break;
                                case remove:
                                    slaveUpdater.remove(entity); break;
                                default:
                                    throw new UnsupportedOperationException();
                            }

                            updateTypes.poll();
                            entities.poll();

                        }catch(Exception e) {
                            
                            if(commsLinkFailureTest != null && commsLinkFailureTest.test(e)) {
                                
//                                logger.log(Level.INFO, "Communications exception updating remote entity: " + entity, e);
                                
                            }else{
                                
                                logger.log(Level.WARNING, "Failed to update remote entity: "+entity, e);
                                
                                updateTypes.poll();
                                entities.poll();
                            }
                        }finally{
                            lock.writeLock().unlock();
                        }
                    }catch(RuntimeException e) {
                        
                        logger.log(Level.WARNING, "Unexpected error", e);
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }
    
    @Override
    public boolean isStopRequested() {
        return this.stopRequested;
    }
    
    @Override
    public void requestStop() {
        if(!stopRequested) {
            stopRequested = true;
            this.save();
        }
    }
    
    @Override
    public synchronized boolean isPaused() {
        
        if(stopRequested) { throw new IllegalStateException(); }
        
        return paused;
    }
    
    @Override
    public synchronized boolean pause() {
        
        if(stopRequested) { throw new IllegalStateException(); }
        
        logger.fine("Pausing slave updates");
        return this.isPaused() ? false : (paused = true);
    }

    @Override
    public synchronized boolean resume() {
        
        if(stopRequested) { throw new IllegalStateException(); }
        
        logger.fine("Resuming slave updates");
        return !this.isPaused() ? false : (paused = false);
    }
    
    private Object [] loadOrCreate() {
        final Object a = this.readNamedObject("updateTypes.pending", new LinkedList<>());
        final Object b = this.readNamedObject("entities.pending", new LinkedList());
        return new Object[]{a, b};
    }
    
    private void save() {
        
        logger.log(Level.FINE, "Saving {0} slave updates", this.getPendingUpdatesSize());
        
        new Thread() {
            @Override
            public void run() {
                try{
                    writeNamedObject("updateTypes.pending", updateTypes);
                }catch(RuntimeException e) { 
                    logger.log(Level.WARNING, "Error saving updateTypes.pending", e);
                }
            }
        }.start();
        new Thread() {
            @Override
            public void run() {
                try{
                    writeNamedObject("entities.pending", entities);
                }catch(RuntimeException e) { 
                    logger.log(Level.WARNING, "Error saving entities.pending", e);
                }
            }
        }.start();
    }
    
    @Override
    public boolean addPersist(Object entity) {
        return this.add(persist, entity);
    }

    @Override
    public boolean addMerge(Object entity) {
        return this.add(merge, entity);
    }

    @Override
    public boolean addRemove(Object entity) {
        return this.add(remove, entity);
    }

    public boolean add(int updateType, Object entity) {
        
        if(stopRequested) { throw new IllegalStateException(); }
        
        try{
            
            lock.writeLock().lock();
            
            updateTypes.add(updateType);
            return entities.add(entity);
            
        }catch(RuntimeException e) {
            while(updateTypes.size() > entities.size()) {
                updateTypes.remove(updateTypes.size()-1);
            }
            throw e;
        }finally{
            
            lock.writeLock().unlock();
            
            logger.log(Level.FINER, "Pending slave updates: {0}", this.getPendingUpdatesSize());
        }
    }

    @Override
    public int getPendingUpdatesSize() {
        return Math.min(this.updateTypes.size(), this.entities.size());
    }

    public Object readNamedObject(String name, Object outputIfNone) {
        final String path = Paths.get(backupDir.toString(), name).toString();
        try{
            return this.readObject(path);
        }catch(FileNotFoundException e) {
            logger.warning(e.toString());
            return outputIfNone;
        }catch(ClassNotFoundException | IOException e) {
            logger.log(Level.WARNING, "Error reading: "+name+" from: "+path, e);
            return outputIfNone;
        }
    }

    public Object readObject(String source) throws ClassNotFoundException, IOException {
        
        Object result = null;
        
        FileInputStream     fis = null;
        BufferedInputStream bis = null;
        ObjectInputStream   ois = null;
        
        try {

            fis = new FileInputStream(source);
            bis = new BufferedInputStream(fis);
            ois = new ObjectInputStream(bis);

            result = ois.readObject();
        
        }catch(IOException e) {
            
            throw e;
        
        }finally {
        
            if (ois != null) try { ois.close(); }catch(IOException e) {}
            if (bis != null) try { bis.close(); }catch(IOException e) {}
            if (fis != null) try { fis.close(); }catch(IOException e) {}
        }
        
        return result;
    }

    public void writeNamedObject(String name, Object obj) {
        final String path = Paths.get(backupDir.toString(), name).toString();
        try{
            this.writeObject(path, obj);
        }catch(Exception e) {
            logger.log(Level.WARNING, "Error writing: "+name+" to: "+path, e);
        }
    }
    
    public void writeObject(String destination, Object obj) throws FileNotFoundException, IOException {
        
        FileOutputStream     fos = null;
        BufferedOutputStream bos = null;
        ObjectOutputStream oos = null;
        
        try{
            
            fos = new FileOutputStream(destination);
            bos = new BufferedOutputStream(fos);
            oos = new ObjectOutputStream(bos);

            oos.writeObject(obj);
        
        }catch(IOException e) {
            
            throw e;
        
        }finally {
        
            if (oos != null) try { oos.close(); }catch(IOException e) { logger.log(Level.WARNING, "", e); }
            if (bos != null) try { bos.close(); }catch(IOException e) { logger.log(Level.WARNING, "", e); }
            if (fos != null) try { fos.close(); }catch(IOException e) { logger.log(Level.WARNING, "", e); }
        }
    }
}
