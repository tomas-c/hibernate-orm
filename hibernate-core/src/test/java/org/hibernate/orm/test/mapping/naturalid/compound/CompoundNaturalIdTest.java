/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.mapping.naturalid.compound;

import org.hibernate.annotations.NaturalId;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.spi.NaturalIdResolutions;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaRoot;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Sylvain Dusart
 */
@TestForIssue(jiraKey = "HHH-16218")
@DomainModel(
		annotatedClasses = {
				CompoundNaturalIdTest.EntityWithSimpleNaturalId.class,
				CompoundNaturalIdTest.EntityWithCompoundNaturalId.class
		}
)
@ServiceRegistry(
		settings = {
				@Setting(name = AvailableSettings.STATEMENT_BATCH_SIZE, value = "500"),
				@Setting(name = AvailableSettings.SHOW_SQL, value = "false"),
		}
)
@SessionFactory
public class CompoundNaturalIdTest {

	private static final int OBJECT_NUMBER = 2000;
	private static final int MAX_RESULTS = 2000;

	@BeforeAll
	public void setUp(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					for ( int i = 0; i < OBJECT_NUMBER; i++ ) {
						EntityWithCompoundNaturalId compoundNaturalIdEntity = new EntityWithCompoundNaturalId();
						final String str = String.valueOf( i );
						compoundNaturalIdEntity.setFirstname( str );
						compoundNaturalIdEntity.setLastname( str );
						session.persist( compoundNaturalIdEntity );

						EntityWithSimpleNaturalId withSimpleNaturalIdEntity = new EntityWithSimpleNaturalId();
						withSimpleNaturalIdEntity.setName( str );
						session.persist( withSimpleNaturalIdEntity );
					}
				}
		);
	}

	@Test
	public void createThenLoadTest(SessionFactoryScope scope) {
		long loadDurationForCompoundNaturalId = loadEntities( EntityWithCompoundNaturalId.class, MAX_RESULTS, scope );
		long loadDurationForSimpleNaturalId = loadEntities( EntityWithSimpleNaturalId.class, MAX_RESULTS, scope );

		assertTrue(
				loadDurationForCompoundNaturalId <= 10 * loadDurationForSimpleNaturalId,
				"it should not be soo long to load entities with compound naturalId"
		);
	}

	private long loadEntities(final Class<?> clazz, final int maxResults, SessionFactoryScope scope) {
		long start = System.currentTimeMillis();
		scope.inSession(
				session -> {
					final HibernateCriteriaBuilder cb = session.getCriteriaBuilder();
					JpaCriteriaQuery<?> query = cb.createQuery( clazz );
					query.from( clazz );
					session.createQuery( query ).setMaxResults( maxResults ).list();
				}
		);
		long duration = System.currentTimeMillis() - start;
		return duration;
	}

	@Entity(name = "SimpleNaturalId")
	public static class EntityWithSimpleNaturalId {

		@Id
		@GeneratedValue
		private Long id;

		@NaturalId
		private String name;

		public Long getId() {
			return id;
		}

		public void setId(final Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(final String name) {
			this.name = name;
		}
	}

	@Entity(name = "CompoundNaturalId")
	public static class EntityWithCompoundNaturalId {

		@Id
		@GeneratedValue
		private Long id;

		@NaturalId
		private String firstname;

		@NaturalId
		private String lastname;

		public Long getId() {
			return id;
		}

		public void setId(final Long id) {
			this.id = id;
		}

		public String getFirstname() {
			return firstname;
		}

		public void setFirstname(final String firstname) {
			this.firstname = firstname;
		}

		public String getLastname() {
			return lastname;
		}

		public void setLastname(final String lastname) {
			this.lastname = lastname;
		}
	}
}
