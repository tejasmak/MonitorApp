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

package com.bia.monitor.service;

import com.bia.monitor.dao.JobRepository;
import com.bia.monitor.data.Job;
import java.util.Date;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Main service class for url CRUD
 * 
 * @author Intesar Mohammed
 */
@Component
public class MonitorService {

	static final String HTTP = "http://";
	static final String HTTPS = "https://";
	
	protected static final Logger logger = Logger
			.getLogger(MonitorService.class);
	
	private JobRepository jobRepository;
        private EmailService emailService;

	// private static final MonitorService instance = new MonitorService();
        @Autowired
	public MonitorService(JobRepository jobRepository, EmailService emailService) {
            this.jobRepository = jobRepository;
            this.emailService = emailService;
            logger.info("instantiated!");
	}

	/**
	 * 
	 * @param url
	 * @param email
	 * @return
	 */
	public String add(String url, String email) {

		if ((!isValidEmail(email)) || (url == null || url.trim().length() <= 4)) {
			return "Invalid data!";
		}

		String url_ = url.toLowerCase();
		if (!url_.startsWith(HTTP) && !url_.startsWith(HTTPS)) {
			url = HTTP + url;
		}

		String id = monitor(url, email);
		
		logger.info(url + ", " + email + ", " + id
					+ " is added to the queue!");
		
		return id;
	}

	/**
	 * 
	 * @param id
	 * @return
	 */
	public boolean remove(String id, String email) {
		
		Job job = jobRepository.findOne(id);

		if (job.getEmail().size() > 1) {
			job.getEmail().remove(email);
			jobRepository.save(job);
		} else {
			jobRepository.delete(job);
			logger.info("id=" + id + ", email=" + email + " is removed!");
		}

		StringBuilder body = new StringBuilder();
		body.append("Your submitted data! <br/>").append("<p>Url : ")
				.append(job.getUrl()).append("<br/>").append("Email : ")
				.append(job.getEmail()).append("<br/><br/>")
				.append("Thanks for using http://www.zytoon.me/monitor/");

		emailService.sendEmail(email, "Your site is no longer monitored!",
				body.toString());

		return true;
	}

	/**
	 * 
	 * @param id
	 * @return
	 */
	public String status(String id) {
		Job j = jobRepository.findOne(id);
		if (j != null) {

			int mins = (int) ((new Date().getTime() / 60000) - (j.getUpSince()
					.getTime() / 60000));

			StringBuilder reply = new StringBuilder();
			reply.append("{url : ").append(j.getUrl()).append(", Status : ")
					.append(j.getStatus()).append(", Since : ").append(mins)
					.append(" minutes}");
			return reply.toString();

		}

		return "No data found, please add your site at http://www.zytoon.me/monitor";
	}

	// public static MonitorService getInstance() {
	// return instance;
	// }
	/**
	 * 
	 * @param emails
	 * @return
	 */
	private boolean isValidEmail(String email) {
		if (!EmailValidator.getInstance().isValid(email)) {
			return false;
		}
		return true;
	}

	private String monitor(String url, String email) {

		Job job = null;

		try {
			job = jobRepository.findByUrl(url);
		} catch (RuntimeException re) {
		}

		// add
		if (job != null) {
			job.getEmail().add(email);
			jobRepository.save(job);
		} else {
			job = new Job();
			job.setUrl(url);
			job.getEmail().add(email);
			job.setUpSince(new Date());
			jobRepository.save(job);
		}

		StringBuilder body = new StringBuilder();
		body.append("Your submitted data! <br/>")
				.append("<p>Url : ")
				.append(url)
				.append("<br/>")
				.append("Email : ")
				.append(email)
				.append("<br/><br/>")
				.append("Free Features!")
				.append("<br/>")
                                .append("1. Free Site/Web Service Monitor <br/>")
				.append("2. Free Alerts <br/>")
				.append("3. Free weekly reports")
				.append("<br/><br/>")
                                .append("More Upcoming Features!")
                                .append("<br/>")
				.append("1. Free Social Media coverage on Twitter, G+, FB, Pinterest, Blogger, Reddit for 95+ uptime!")
				.append("<br/><br/>")
				.append("If you like this service please refer to your friends --  ")
				.append("http://www.zytoon.me/monitor/ <br/><br/>")
				.append("Important link ... <br/>")
				.append("<a href=\"http://www.zytoon.me/monitor/rest/monitor/status/")
				.append(job.getId())
				.append("\"> Show me site status </a> <br/>")
				.append(" <a href=\"http://www.zytoon.me/monitor/rest/monitor/delete/")
				.append(job.getId()).append("/").append(email)
				.append("\" > Stop monitoring my site! </a> ");

		emailService
				.sendEmail(email,
						"Congratulations we are monitoring your site!",
						body.toString());

		return job.getId();
	}
}
