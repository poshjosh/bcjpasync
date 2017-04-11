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

package com.bc.jpa.sync.predicates;

import java.util.function.Predicate;
import org.eclipse.persistence.exceptions.DatabaseException;

/**
 * @author Chinomso Bassey Ikwuagwu on Mar 9, 2017 3:11:54 PM
 */
public class PersistenceCommunicationsLinkFailureTest implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable t) {
        boolean success = false;
        do{
            success = (t instanceof DatabaseException && ((DatabaseException)t).isCommunicationFailure());
            if(success) {
                break;
            }
            t = t.getCause();
            if(t == null) {
                break;
            }
        }while(true);
        return success;
    }
}
