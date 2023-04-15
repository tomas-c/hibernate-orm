/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.annotations.generics;

import org.hibernate.query.criteria.JpaPath;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.Jira;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Marco Belladelli
 */
@SessionFactory
@DomainModel( annotatedClasses = {
		GenericBasicValuedPathTest.MySuper.class,
		GenericBasicValuedPathTest.MyEntity.class
} )
@Jira( "https://hibernate.atlassian.net/browse/HHH-16378" )
public class GenericBasicValuedPathTest {
	@BeforeAll
	public void setUp(SessionFactoryScope scope) {
		scope.inTransaction( session -> session.persist( new MyEntity( 1, "my_entity" ) ) );
	}

	@AfterAll
	public void tearDown(SessionFactoryScope scope) {
		scope.inTransaction( session -> session.createMutationQuery( "delete from MyEntity" ).executeUpdate() );
	}

	@Test
	public void testId(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final CriteriaBuilder cb = session.getCriteriaBuilder();
			final CriteriaQuery<Object> query = cb.createQuery();
			final Root<MyEntity> root = query.from( MyEntity.class );
			final Path<String> idPath = root.get( "id" );
			// generic attributes are always reported as Object java type
			assertThat( idPath.getJavaType() ).isEqualTo( Object.class );
			assertThat( idPath.getModel() ).isSameAs( root.getModel().getAttribute( "id" ) );
			assertThat( ( (JpaPath<?>) idPath ).getResolvedModel().getBindableJavaType() ).isEqualTo( Integer.class );
			final Object result = session.createQuery( query.select( idPath ) ).getSingleResult();
			assertThat( result ).isEqualTo( 1 );
		} );
	}

	@Test
	public void testProperty(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final CriteriaBuilder cb = session.getCriteriaBuilder();
			final CriteriaQuery<Object> query = cb.createQuery();
			final Root<MyEntity> root = query.from( MyEntity.class );
			final Path<String> dataPath = root.get( "data" );
			// generic attributes are always reported as Object java type
			assertThat( dataPath.getJavaType() ).isEqualTo( Object.class );
			assertThat( dataPath.getModel() ).isSameAs( root.getModel().getAttribute( "data" ) );
			assertThat( ( (JpaPath<?>) dataPath ).getResolvedModel().getBindableJavaType() ).isEqualTo( String.class );
			final Object result = session.createQuery( query.select( dataPath ) ).getSingleResult();
			assertThat( result ).isEqualTo( "my_entity" );
		} );
	}

	@MappedSuperclass
	public abstract static class MySuper<T, S> {
		@Id
		private T id;
		private S data;

		public MySuper() {
		}

		public MySuper(T id, S data) {
			this.id = id;
			this.data = data;
		}
	}

	@Entity( name = "MyEntity" )
	public static class MyEntity extends MySuper<Integer, String> {
		public MyEntity() {
		}

		public MyEntity(Integer id, String data) {
			super( id, data );
		}
	}
}
