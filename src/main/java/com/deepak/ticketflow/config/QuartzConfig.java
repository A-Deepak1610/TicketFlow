package com.deepak.ticketflow.config;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.quartz.spi.TriggerFiredBundle;

@Configuration
public class QuartzConfig {

    @Bean
    public SpringBeanJobFactory springBeanJobFactory(
            ApplicationContext applicationContext) {

        AutowireCapableBeanFactory beanFactory =
                applicationContext.getAutowireCapableBeanFactory();

        SpringBeanJobFactory jobFactory =
                new SpringBeanJobFactory() {

                    @Override
                    protected Object createJobInstance(
                            TriggerFiredBundle bundle)
                            throws Exception {

                        Object job =
                                super.createJobInstance(bundle);

                        beanFactory.autowireBean(job);

                        return job;
                    }
                };

        return jobFactory;
    }
}