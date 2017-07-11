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

package com.bc.jpa.sync;

/**
 * @author Chinomso Bassey Ikwuagwu on Mar 7, 2017 7:18:33 PM
 */
public interface SlaveUpdates {
    
    SlaveUpdates NO_OP = new SlaveUpdates() {
        @Override
        public void requestStop() { }
        @Override
        public boolean isStopRequested() { return false; }
        @Override
        public boolean isPaused() { return false; }
        @Override
        public boolean pause() { return false; }
        @Override
        public boolean resume() { return false; }
        @Override
        public boolean addPersist(Object entity) { return false; }
        @Override
        public boolean addMerge(Object entity) { return false; }
        @Override
        public boolean addRemove(Object entity) { return false; }
        @Override
        public int getPendingUpdatesSize() { return 0; }
    };
    
    void requestStop();
    
    boolean isStopRequested();
    
    boolean isPaused();
    
    boolean pause();

    boolean resume();
    
    boolean addPersist(Object entity);
    
    boolean addMerge(Object entity);
    
    boolean addRemove(Object entity);
    
    int getPendingUpdatesSize();
}
