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
import com.bc.jpa.sync.PendingUpdate.UpdateType;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.bc.jpa.sync.Updater;
import com.bc.jpa.sync.PendingUpdatesManager;
import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author Chinomso Bassey Ikwuagwu on Mar 7, 2017 7:28:37 PM
 */
public class PendingUpdatesManagerImpl implements PendingUpdatesManager {
    
    private transient static final Logger logger = Logger.getLogger(PendingUpdatesManagerImpl.class.getName());
    
    private volatile boolean stopRequested;
    private volatile boolean paused;
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    private final File file;
    
    private final Updater updater;
    
    private final Predicate<Throwable> retryOnExceptionTest;
    
    private final List<PendingUpdate> pendingUpdates;
    
    private int mark = -1;
    
    public PendingUpdatesManagerImpl(File file, Updater updater, Predicate<Throwable> retryOnExceptionTest) {
        this.file = Objects.requireNonNull(file);
        this.updater = Objects.requireNonNull(updater);
        this.retryOnExceptionTest = retryOnExceptionTest;
        this.pendingUpdates = (List<PendingUpdate>)this.readSilently(file, new LinkedList<>());
        logger.log(Level.INFO, "Pending updates count: {0}", this.pendingUpdates.size());
        
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
                    if(pendingUpdates.isEmpty()) {
                        continue;
                    }
                    
                    try{
                        final PendingUpdate pendingUpdate;
                        try{
                            lock.readLock().lock();
                            pendingUpdate = pendingUpdates.get(0);
                        }finally{
                            lock.readLock().unlock();
                        }

                        if(pendingUpdate == null) {
                            continue;
                        }

                        try{

                            lock.writeLock().lock();
                            
                            final UpdateType updateType = pendingUpdate.getUpdateType();
                            switch(updateType) {
                                case PERSIST: 
                                    updater.persist(pendingUpdate.getEntity()); break;
                                case MERGE:
                                    updater.merge(pendingUpdate.getEntity()); break;
                                case REMOVE:
                                    updater.remove(pendingUpdate.getEntity()); break;
                                default:
                                    throw new UnsupportedOperationException();
                            }

                            pendingUpdates.remove(0);

                        }catch(Exception e) {
                            
                            if(retryOnExceptionTest != null && retryOnExceptionTest.test(e)) {
                                
//                                logger.log(Level.INFO, "Communications exception updating remote entity: " + entity, e);
                                
                            }else{
                                
                                logger.log(Level.WARNING, "Failed to update remote entity: " + pendingUpdate.getEntity(), e);
                                
                                pendingUpdates.remove(0);
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
    public int getMark() {
        return this.mark;
    }
    
    @Override
    public int mark(int n) {
        this.mark = n;
        return this.mark;
    }
    
    @Override
    public synchronized void rollbackToMarkedPosition() {
        if(this.isMarked()) {
            final int size;
            try{
                lock.readLock().lock();
                size = this.pendingUpdates.size();
            }finally{
                lock.readLock().unlock();
            }
            if(this.mark < size) {
                try{
                    lock.writeLock().lock();
                    this.pendingUpdates.subList(this.mark, size).clear();
                }finally{
                    lock.writeLock().unlock();
                }
            }
            this.unmark();
        }
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
    
    private void save() {
        logger.log(Level.FINE, "Saving {0} slave updates", this.getPendingUpdatesSize());
        try{
            writeSilently(pendingUpdates, file);
        }catch(RuntimeException e) { 
            logger.log(Level.WARNING, "Error saving to: " + file, e);
        }
    }
    
    @Override
    public boolean addPersist(Object entity) {
        return this.add(UpdateType.PERSIST, entity);
    }

    @Override
    public boolean addMerge(Object entity) {
        return this.add(UpdateType.MERGE, entity);
    }

    @Override
    public boolean addRemove(Object entity) {
        return this.add(UpdateType.REMOVE, entity);
    }

    public boolean add(UpdateType updateType, Object entity) {
        
        if(stopRequested) { throw new IllegalStateException(); }
        
        try{
            
            lock.writeLock().lock(); 
            
            return this.pendingUpdates.add(new PendingUpdateImpl(updateType, entity));
            
        }finally{
            
            lock.writeLock().unlock();
            
            logger.log(Level.FINER, "Pending slave updates: {0}", this.getPendingUpdatesSize());
        }
    }

    @Override
    public int getPendingUpdatesSize() {
        return this.pendingUpdates.size();
    }
    
    public Object readSilently(File f, Object outputIfNone) {
        try{
            return this.readObject(f);
        }catch(FileNotFoundException e) {
            logger.warning(e.toString());
            return outputIfNone;
        }catch(ClassNotFoundException | IOException e) {
            logger.log(Level.WARNING, "Error from: " + f, e);
            return outputIfNone;
        }
    }

    public Object readObject(File f) throws ClassNotFoundException, IOException {
        
        Object result = null;
        
        FileInputStream     fis = null;
        BufferedInputStream bis = null;
        ObjectInputStream   ois = null;
        
        try {

            fis = new FileInputStream(f);
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

    public void writeSilently(Object obj, File f) {
        try{
            this.writeObject(obj, f);
        }catch(Exception e) {
            logger.log(Level.WARNING, "Error writing to: " + f, e);
        }
    }
    
    public void writeObject(Object obj, File f) throws FileNotFoundException, IOException {
        
        FileOutputStream     fos = null;
        BufferedOutputStream bos = null;
        ObjectOutputStream oos = null;
        
        try{
            
            fos = new FileOutputStream(f);
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

    public File getFile() {
        return file;
    }

    public Updater getUpdater() {
        return updater;
    }

    public Predicate<Throwable> getRetryOnExceptionTest() {
        return retryOnExceptionTest;
    }

    @Override
    public List<PendingUpdate> getPendingUpdates() {
        return Collections.unmodifiableList(this.pendingUpdates);
    }
}
