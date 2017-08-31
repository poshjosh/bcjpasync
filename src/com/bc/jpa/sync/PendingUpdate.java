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
 * @author Chinomso Bassey Ikwuagwu on Aug 12, 2017 9:08:11 AM
 */
public interface PendingUpdate {
    
    enum UpdateType{PERSIST, MERGE, REMOVE}
    
    UpdateType getUpdateType();
    
    long getTimeCreated();
    
    Object getEntity();
}
