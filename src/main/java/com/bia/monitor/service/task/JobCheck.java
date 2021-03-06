/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bia.monitor.service.task;

import com.bia.monitor.data.Job;
import org.springframework.scheduling.annotation.Async;

/**
 *
 * @author Intesar Mohammed
 */
public interface JobCheck {

    /**
     *
     * <p> responseCode </p> <p> < 100 is undertermined. 1xx is informal
     * (shouldn't happen on a GET/HEAD) 2xx is success 3xx is redirect 4xx is
     * client error 5xx is server error </p>
     */
    @Async
    void run(Job job);
    
}
