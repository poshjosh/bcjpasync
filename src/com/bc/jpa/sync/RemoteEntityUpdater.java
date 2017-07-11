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
 * @author Chinomso Bassey Ikwuagwu on Mar 9, 2017 10:52:29 PM
 */
public interface RemoteEntityUpdater {
    
    Object update(Object entity, Object entityId);
    
    Object merge(Object entity);
            
    void persist(Object entity); 
    
    void remove(Object entity);
}