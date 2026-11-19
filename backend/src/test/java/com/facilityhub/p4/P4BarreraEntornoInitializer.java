package com.facilityhub.p4;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.ConfigurableEnvironment;

import javax.sql.DataSource;

/**
 * FH-005A1 / P4 - Barrera fail-closed.
 *
 * Orden de comprobaciones:
 *  1. initialize(): exige -Dp4.autorizado antes de cualquier otra cosa.
 *  2. Post-procesador de definiciones registrado en el contexto: se ejecuta al empezar refresh(),
 *     cuando ya se han aplicado TODOS los inicializadores y personalizadores de test (incluidas las
 *     propiedades de @DynamicPropertySource), y antes de instanciar ningun bean (DataSource, JPA, Flyway).
 *     Asi no depende del orden relativo entre este inicializador y el de @DynamicPropertySource.
 *  3. BeanPostProcessor: al crear el DataSource comprueba su URL real; si aparece un bean de Flyway, aborta.
 */
public class P4BarreraEntornoInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext contexto) {
        P4Entorno.exigirAutorizacionExplicita();
        contexto.addBeanFactoryPostProcessor(new Barrera(contexto.getEnvironment()));
    }

    static final class Barrera implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {
        private final ConfigurableEnvironment entorno;

        Barrera(ConfigurableEnvironment entorno) {
            this.entorno = entorno;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registro) throws BeansException {
            P4Entorno.validarEntornoEfectivo(entorno);
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory fabrica) throws BeansException {
            P4Entorno.validarEntornoEfectivo(entorno);
            fabrica.addBeanPostProcessor(new GuardaDataSourceYFlyway());
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }

    static final class GuardaDataSourceYFlyway implements BeanPostProcessor {
        @Override
        public Object postProcessBeforeInitialization(Object bean, String nombre) throws BeansException {
            if (bean instanceof Flyway) {
                throw new IllegalStateException("P4 abortada: se intento crear Flyway en el contexto de pruebas");
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String nombre) throws BeansException {
            if (bean instanceof Flyway) {
                throw new IllegalStateException("P4 abortada: se intento crear Flyway en el contexto de pruebas");
            }
            if (bean instanceof DataSource) {
                // Hikari no abre conexiones hasta el primer getConnection(): la URL se comprueba antes.
                if (!(bean instanceof HikariDataSource hikari)) {
                    throw new IllegalStateException("P4 abortada: tipo de DataSource no reconocido");
                }
                P4Entorno.validarUrl(hikari.getJdbcUrl());
                if (!P4Entorno.USUARIO.equals(hikari.getUsername())) {
                    throw new IllegalStateException("P4 abortada: usuario de BD no autorizado");
                }
            }
            return bean;
        }
    }
}
