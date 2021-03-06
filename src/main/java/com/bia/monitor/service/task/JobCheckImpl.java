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

import com.bia.monitor.dao.JobDownRepository;
import com.bia.monitor.dao.JobRepository;
import com.bia.monitor.data.Job;
import com.bia.monitor.data.JobDown;
import com.bia.monitor.data.JobStatus;
import com.bia.monitor.service.EmailService;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Date;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runnable checks status of url and alerts emails if down
 *
 * @author Intesar Mohammed
 */
@Service
@Transactional
public class JobCheckImpl implements JobCheck  {

    protected Logger logger = Logger.getLogger(getClass());
    private EmailService emailService;
    private JobRepository jobRepository;
    private JobDownRepository jobDownRepository;

    @Autowired
    public JobCheckImpl(JobRepository jobRepository, JobDownRepository jobDownRepository, EmailService emailService) {
        this.jobRepository = jobRepository;
        this.jobDownRepository = jobDownRepository;
        this.emailService = emailService;
    }

    /**
     *
     * <p> responseCode </p> <p> < 100 is undertermined. 1xx is informal
     * (shouldn't happen on a GET/HEAD) 2xx is success 3xx is redirect 4xx is
     * client error 5xx is server error </p>
     */
    @Async
    @Override
    public void run(Job job) {

        // check status
        String responseCodeStr = getStatus(job);
        int responseCode = getIntegerVal(responseCodeStr);
        if (responseCode == 200) {
            if (logger.isInfoEnabled()) {
                logger.info(" ping " + job.getUrl() + " successful!");
            }
            // site is up
            handleSiteUp(job);

        } else if (responseCode == 413) {
            if (logger.isInfoEnabled()) {
                logger.info(" ping in filter code " + job.getUrl() + " response code : " + responseCodeStr);
            }
            //do nothing
            // all other non-action codes will come here
            notifyAdmin(job, responseCodeStr);
        } else {

            handleSiteDown(job, responseCodeStr);

            if (logger.isInfoEnabled()) {
                int mins = (int) ((new Date().getTime() / 60000) - (job.getDownSince().getTime() / 60000));
                logger.info(" ping failed " + job.getUrl() + " response code : " + responseCodeStr + " Down since : " + mins);
            }
        }

    }

    /**
     * Get url connection status
     *
     * @return
     */
    private String getStatus(Job job) {
        String responseCode;
        try {
            if (logger.isInfoEnabled()) {
                logger.info(" pinging " + job.getUrl());
            }

            HttpURLConnection connection = (HttpURLConnection) new URL(job.getUrl()).openConnection();
            connection.setRequestMethod("GET");
            responseCode = String.valueOf(connection.getResponseCode());
            return responseCode;

        } catch (ProtocolException pe) {
            responseCode = pe.getMessage();
        } catch (IOException io) {
            responseCode = io.getMessage();
        } catch (RuntimeException re) {
            responseCode = re.getMessage();
        }
        return responseCode;

    }

    /**
     * Converts String response-code to number
     *
     * @param responseCodeStr
     * @return
     */
    private int getIntegerVal(String responseCodeStr) {
        int code = 0;
        try {
            code = Integer.parseInt(responseCodeStr);
        } catch (RuntimeException re) {
            // do nothing
        }
        return code;
    }

    /**
     * logic when site is up
     */
    private void handleSiteUp(Job job) {
        // handle only if site was down earlier
        if (!job.isLastUp()) {
            updateJobOnSiteUp(job);
            updateJobDownOnSiteUp(job);
            sendUpNotify(job);
        }
    }

    /**
     * Site is up after being down, update job objects
     */
    private void updateJobOnSiteUp(Job job) {
        job.setLastUp(true);
        job.setNotified(false);
        job.setUpSince(new Date());
        job.setStatus(JobStatus.UP.toString());
        jobRepository.save(job);
    }

    /**
     * Site is up after being down, update jobdown object
     */
    private void updateJobDownOnSiteUp(Job job) {
        JobDown jobDown = jobDownRepository.findByJobIdAndActive(job.getId(), Boolean.TRUE);
        jobDown.setDownTill(new Date());
        jobDown.setActive(Boolean.FALSE);
        jobDownRepository.save(jobDown);
    }

    /**
     * Send email on site up
     */
    private void sendUpNotify(Job job) {
        // send site up notification
        int mins = (int) ((new Date().getTime() / 60000) - (job.getDownSince().getTime() / 60000));
        StringBuilder body = new StringBuilder();
        body.append(job.getUrl()).append(" is Up after ").append(mins).append(" mins of downtime!");

        for (String email : job.getEmail()) {
            emailService.sendEmail(email, job.getUrl() + " is Up!", body.toString());
        }
    }

    /**
     * Handle site down
     */
    private void handleSiteDown(Job job, String responseCodeStr) {
        if (logger.isTraceEnabled()) {
            logger.trace(" ping failed " + job.getUrl() + " status code : " + responseCodeStr);
        }
        // notify after second attempt
        if (job.isLastUp()) {
            updateJobOnSiteDown(job);
            updateJobDownOnSiteDown(job);
        } else if (!job.isNotified()) {
            sendDownNotify(job, responseCodeStr);
        }
    }

    /**
     * Site went down for first time, update job object
     */
    private void updateJobOnSiteDown(Job job) {
        job.setLastUp(false);
        job.setDownSince(new Date());
        job.setStatus(JobStatus.DOWN.toString());
        job.setNotified(false);
        jobRepository.save(job);
    }

    /**
     * Site went down for the first time, update job-down object
     */
    private void updateJobDownOnSiteDown(Job job) {
        JobDown jobDown = new JobDown(job.getId(), Boolean.TRUE, new Date());
        jobDownRepository.save(jobDown);
    }

    /**
     *
     * @param responseCodeStr
     */
    private void sendDownNotify(Job job, String responseCodeStr) {
        job.setNotified(true);
        jobRepository.save(job);
        // send alert email
        StringBuilder body = new StringBuilder();
        body.append(job.getUrl()).append(" <br/> Status : Down! ").append("<br/>Response Code : ").append(responseCodeStr).append("<br/>Detection Time: ").append(job.getDownSince());
        for (String email : job.getEmail()) {
            emailService.sendEmail(email, job.getUrl() + " is Down!", body.toString());
        }
    }

    /**
     * Notify admin
     *
     * @param responseCodeStr
     */
    private void notifyAdmin(Job job, String responseCodeStr) {
        // send alert email
        Date time = new Date();
        StringBuilder body = new StringBuilder();
        body.append(job.getUrl()).append(" is Down! ").append("<br/>Response Code : ").append(responseCodeStr).append("<br/>Detection Time : ").append(time).append("<br/>Owner : ").append(job.getEmail());
        emailService.sendEmail("mdshannan@gmail.com", job.getUrl() + " is Down!", "Detected down on : " + time);
    }
}
